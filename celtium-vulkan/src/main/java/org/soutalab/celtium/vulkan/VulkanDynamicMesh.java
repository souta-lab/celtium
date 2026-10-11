package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Host-visible vertex and index streams for the geometry Minecraft builds per frame.
 * <p>
 * This is the foundation for roadmap stage S4: everything that is not terrain - entities, tile entities, items and
 * the hand, particles, the sky, clouds, weather and the GUI - reaches the screen through {@code Tessellator} /
 * {@code BufferBuilder} today, which in OpenGL ends in an immediate-mode draw of client memory. The Vulkan
 * equivalent is a persistently mapped buffer that the caller writes while building, plus one indexed draw per
 * flushed batch; no staging copy or queue drain is involved, because the memory is host visible and the work is
 * consumed by the same submission that follows.
 * <p>
 * A mesh owns two buffers and grows them when a batch does not fit. {@link #begin(int)} starts a batch with a
 * vertex layout, the {@code put*} methods append attributes at the offsets the layout declares, and
 * {@link #draw(VkCommandBuffer)} issues the batch. The layout offsets mirror {@code DefaultVertexFormats} so a
 * {@code BufferBuilder} can write into this stream without a conversion step.
 * <p>
 * Threading: render thread only, like the rest of the backend.
 */
public final class VulkanDynamicMesh {
    /** Attributes are written at fixed offsets inside a vertex, exactly as the Minecraft formats declare them. */
    public static final class Layout {
        public final int stride;
        public final int position;
        public final int uv;
        public final int color;
        public final int normal;
        public final int light;

        public Layout(int stride, int position, int uv, int color, int normal, int light) {
            this.stride = stride;
            this.position = position;
            this.uv = uv;
            this.color = color;
            this.normal = normal;
            this.light = light;
        }

        /** {@code DefaultVertexFormats.POSITION_COLOR} plus the light map coordinate. */
        public static Layout positionColorLight() {
            return new Layout(24, 0, -1, 12, -1, 16);
        }

        /** {@code DefaultVertexFormats.POSITION_TEX_LIGHTMAP_COLOR}: the GUI and font layout. */
        public static Layout positionTexLightColor() {
            return new Layout(24, 0, 12, 20, -1, 16);
        }

        /** {@code DefaultVertexFormats.POSITION_TEX_COLOR} without lighting. */
        public static Layout positionTexColor() {
            return new Layout(24, 0, 12, 20, -1, -1);
        }

        /** {@code DefaultVertexFormats.POSITION_NORMAL_TEX_LIGHTMAP_COLOR}: world-space entities and items. */
        public static Layout positionNormalTexLightColor() {
            return new Layout(32, 0, 16, 28, 12, 24);
        }
    }

    private static final int INITIAL_VERTEX_BYTES = 64 * 1024;
    private static final int INITIAL_INDEX_BYTES = 32 * 1024;
    /** How many lifecycle lines are printed one by one before the log keeps only every hundredth. */
    private static final int LIFECYCLE_LOG_LIMIT = 96;

    private final VulkanContext context;
    private VulkanHostBuffer vertices;
    private VulkanHostBuffer indices;

    private Layout layout = Layout.positionTexLightColor();
    private long vertexBytes;
    private long indexBytes;
    private int vertexCount;
    private int indexCount;

    /**
     * The display list whose geometry this mesh holds, or -1 while it is an immediate batch's mesh.
     * <p>
     * This pair is the mesh's identity, and it is what the ownership rule is made of: the capture gives every compile of
     * a display list a generation, the mesh records the generation it was built from, and a mesh whose recorded
     * generation is no longer the list's current one is stale - it may not be drawn and its buffers may be released.
     * A re-used list id cannot inherit an older mesh because the generations are globally unique, never reused
     * (audit N02).
     */
    private int ownerList = -1;
    private int ownerGeneration;
    /** Whether the buffers were handed to the retire queue, so this mesh must no longer be drawn. */
    private boolean retired;
    /** Whether the buffers were freed, so a queued retire and a direct delete cannot free them twice. */
    private boolean destroyed;
    /** Whether the free happened on the teardown path, after a wait for device idle that succeeded. */
    private boolean releasedAfterIdle;

    /**
     * The model meshes the renderer committed, keyed the way its own table is.
     * <p>
     * This is a measurement, not a second ownership path: the renderer's table decides what is drawn, and this registry
     * exists so the invariant that table has to keep - one committed mesh per captured list, stamped with that list's
     * current generation - can be checked from outside it and reported. A mesh leaves the registry when it is retired
     * or deleted, which is exactly when it stops being drawable.
     */
    private static final java.util.Map<Integer, VulkanDynamicMesh> COMMITTED =
            new java.util.LinkedHashMap<Integer, VulkanDynamicMesh>();
    private static long modelBuilt;
    private static long modelRetired;
    private static long modelDestroyed;
    private static long modelDiscarded;
    private static long modelDirectDeleted;
    private static long modelIdleReleased;
    private static long stampedOverLive;
    private static int lifecycleLogged;

    public VulkanDynamicMesh(VulkanContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context;
        // The first buffer is released if the second allocation fails (audit ownership gap). Without this, a mesh whose
        // index buffer could not be created leaks the vertex buffer it already made - and a mesh is created per display
        // list, so the leak scales with the model count rather than staying a one-off.
        VulkanHostBuffer vertexBuffer = null;
        try {
            vertexBuffer = new VulkanHostBuffer(context, INITIAL_VERTEX_BYTES,
                    org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
            this.vertices = vertexBuffer;
            this.indices = new VulkanHostBuffer(context, INITIAL_INDEX_BYTES,
                    org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        } catch (RuntimeException | Error error) {
            if (vertexBuffer != null) {
                vertexBuffer.delete();
                this.vertices = null;
            }
            throw error;
        }
    }

    /** Starts a batch; anything previously written but not drawn is discarded. */
    public void begin(Layout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        this.layout = layout;
        this.vertexBytes = 0L;
        this.indexBytes = 0L;
        this.vertexCount = 0;
        this.indexCount = 0;
    }

    public Layout layout() {
        return this.layout;
    }

    public boolean isEmpty() {
        return this.indexCount == 0 && this.vertexCount == 0;
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    /** {@return the CPU-visible address of the first vertex}, for diagnostics only */
    public long vertexAddress() {
        return this.vertices.mappedAddress();
    }

    public int indexCount() {
        return this.indexCount;
    }

    public long vertexBuffer() {
        return this.vertices == null ? 0L : this.vertices.buffer();
    }

    public long indexBuffer() {
        return this.indices == null ? 0L : this.indices.buffer();
    }

    /** Appends a vertex and returns the index the caller should pass to {@link #index(int)}. */
    public int nextVertex() {
        long required = this.vertexBytes + this.layout.stride;
        if (required > this.vertices.size()) {
            this.vertices.ensureCapacity(Math.max(required, this.vertices.size() * 2L));
        }
        long base = this.vertices.mappedAddress() + this.vertexBytes;
        // Attributes are only written by the put* calls, so the vertex is zeroed first: a caller that skips the
        // light or normal attribute then reads a defined value instead of the previous batch's bytes.
        MemoryUtil.memSet(base, 0, this.layout.stride);
        this.vertexBytes += this.layout.stride;
        return this.vertexCount++;
    }

    /** Writes the position of the vertex most recently started by {@link #nextVertex()}. */
    public void putPosition(float x, float y, float z) {
        long base = currentVertex();
        MemoryUtil.memPutFloat(base, x);
        MemoryUtil.memPutFloat(base + 4, y);
        MemoryUtil.memPutFloat(base + 8, z);
    }

    /** Writes normalised texture coordinates as floats, the form {@code BufferBuilder} produces. */
    public void putUv(float u, float v) {
        requireAttribute(this.layout.uv, "uv");
        long base = currentVertex() + this.layout.uv;
        MemoryUtil.memPutFloat(base, u);
        MemoryUtil.memPutFloat(base + 4, v);
    }

    /** Writes a colour as four unsigned bytes, the form Minecraft's colour attribute uses. */
    public void putColor(int red, int green, int blue, int alpha) {
        requireAttribute(this.layout.color, "color");
        long base = currentVertex() + this.layout.color;
        MemoryUtil.memPutByte(base, (byte) red);
        MemoryUtil.memPutByte(base + 1, (byte) green);
        MemoryUtil.memPutByte(base + 2, (byte) blue);
        MemoryUtil.memPutByte(base + 3, (byte) alpha);
    }

    /** Writes the packed normal, four signed bytes in the range -127 to 127. */
    public void putNormal(float x, float y, float z) {
        requireAttribute(this.layout.normal, "normal");
        long base = currentVertex() + this.layout.normal;
        MemoryUtil.memPutByte(base, (byte) ((int) (x * 127.0f)));
        MemoryUtil.memPutByte(base + 1, (byte) ((int) (y * 127.0f)));
        MemoryUtil.memPutByte(base + 2, (byte) ((int) (z * 127.0f)));
        MemoryUtil.memPutByte(base + 3, (byte) 127);
    }

    /** Writes the light map coordinate as two unsigned shorts (block light, sky light), as vanilla packs it. */
    public void putLight(int blockLight, int skyLight) {
        requireAttribute(this.layout.light, "light");
        long base = currentVertex() + this.layout.light;
        MemoryUtil.memPutShort(base, (short) blockLight);
        MemoryUtil.memPutShort(base + 2, (short) skyLight);
    }

    /** Appends an index; the caller passes the value returned by {@link #nextVertex()}. */
    public void index(int value) {
        long required = this.indexBytes + 4L;
        if (required > this.indices.size()) {
            this.indices.ensureCapacity(Math.max(required, this.indices.size() * 2L));
        }
        MemoryUtil.memPutInt(this.indices.mappedAddress() + this.indexBytes, value);
        this.indexBytes += 4L;
        this.indexCount++;
    }

    /**
     * Copies {@code count} vertices of {@code stride} bytes each from a native address and appends triangle-list
     * indices for {@code indices} (the expansion {@link VulkanImmediateDrawer} produces).
     * <p>
     * This is the path Minecraft's immediate batches take: their vertices already are in the layout a pipeline
     * expects, so a raw copy plus generated indices is both the fastest and the least error-prone translation - no
     * per-attribute decoding that could disagree with the format the mod used.
     *
     * @return {@code false} when the layout was refused (a stride this mesh cannot represent, or no indices)
     */
    public boolean copyVertices(long sourceAddress, int stride, int count, int[] indices) {
        if (sourceAddress == 0L || count <= 0 || indices == null || indices.length == 0
                || stride < 4 || stride > 256) {
            return false;
        }
        int baseVertex = this.vertexCount;
        long addedVertices = (long) stride * count;
        long requiredVertices = this.vertexBytes + addedVertices;
        long requiredIndices = this.indexBytes + (long) indices.length * 4;
        if (requiredVertices > this.vertices.size()) this.vertices.ensureCapacity(Math.max(requiredVertices, this.vertices.size() * 2));
        if (requiredIndices > this.indices.size()) this.indices.ensureCapacity(Math.max(requiredIndices, this.indices.size() * 2));
        MemoryUtil.memCopy(sourceAddress, this.vertices.mappedAddress() + this.vertexBytes, addedVertices);
        for (int i = 0; i < indices.length; i++) {
            MemoryUtil.memPutInt(this.indices.mappedAddress() + this.indexBytes + (long) i * 4, indices[i] + baseVertex);
        }
        this.layout = new Layout(stride, 0, -1, -1, -1, -1);
        this.vertexBytes = requiredVertices;
        this.indexBytes = requiredIndices;
        this.vertexCount += count;
        this.indexCount += indices.length;
        return true;
    }

    /**
     * Issues the batch with 32-bit indices and no vertex offset. The caller binds the pipeline, the descriptor sets
     * and the vertex buffer first, exactly as the terrain path does.
     */
    public void draw(VkCommandBuffer commandBuffer) {
        if (this.indexCount == 0 || this.vertexCount == 0) {
            return;
        }
        org.lwjgl.vulkan.VK10.vkCmdDrawIndexed(commandBuffer, this.indexCount, 1, 0, 0, 0);
    }

    public void delete() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        if (this.ownerList > 0) {
            celtium$released(this, this.retired);
        }
        if (this.vertices != null) {
            this.vertices.delete();
            this.vertices = null;
        }
        if (this.indices != null) {
            this.indices.delete();
            this.indices = null;
        }
    }

    /**
     * Stamps this mesh as the geometry of one display list's compile generation (audit N02).
     * <p>
     * The renderer calls this once, after a mesh has been built from a {@code VulkanModelQuadCapture.Snapshot} and
     * before it is put in the table that draws it. The stamp is the mesh's whole identity: {@link #ownerGeneration()}
     * against {@code VulkanModelQuadCapture.generationOf(listId)} tells the renderer whether the mesh is still the
     * list's current geometry, so a list that was recompiled, deleted or whose id came back is rebuilt while every
     * unchanged list keeps its mesh instead of being rebuilt with it.
     *
     * @param listId     the display list the geometry was captured from; must be positive
     * @param generation that list's compile generation at capture time; must be positive
     * @throws IllegalArgumentException when either identity is missing
     * @throws IllegalStateException    when this mesh has already been destroyed
     */
    public void stamp(int listId, int generation) {
        if (listId <= 0 || generation <= 0) {
            throw new IllegalArgumentException(
                    "a model mesh needs a display list and a generation, got " + listId + "/" + generation);
        }
        if (this.destroyed) {
            throw new IllegalStateException("a destroyed mesh cannot be stamped as list " + listId);
        }
        boolean first = this.ownerList < 0;
        this.ownerList = listId;
        this.ownerGeneration = generation;
        celtium$committed(this, listId, generation, first);
    }

    /** {@return the display list this mesh was built from, or -1 while it is an immediate batch's mesh}. */
    public int ownerList() {
        return this.ownerList;
    }

    /** {@return the compile generation this mesh was built from, or 0 when it was never stamped}. */
    public int ownerGeneration() {
        return this.ownerGeneration;
    }

    /**
     * {@return whether this mesh is the geometry of that list's current compile and still usable}.
     * <p>
     * The renderer's rebuild test is this one call: a mesh that answers true is reused exactly as it is, and every other
     * answer - a different generation, a different list, a retired mesh - means rebuild.
     */
    public boolean matches(int listId, int generation) {
        return this.ownerList == listId && this.ownerGeneration == generation
                && !this.retired && !this.destroyed;
    }

    /** {@return whether the buffers were handed to the retire queue and must no longer be drawn}. */
    public boolean retired() {
        return this.retired;
    }

    /**
     * Releases this mesh's buffers after the GPU work that used them has completed.
     * <p>
     * The renderer's own contract is used rather than a direct free: {@code VulkanRetireQueue} is drained only after a
     * wait that succeeded (audit S12), so a command buffer recorded in an earlier frame - which still names these vertex
     * and index buffers - has finished by the time they are destroyed. The caller removes the mesh from whatever table
     * draws it in the same frame: after this call it is not usable any more, and it will never be reused even if the
     * same list id is compiled again, because the id's generation has moved on.
     * <p>
     * Destruction waits for <em>two</em> successful waits, not one. A model mesh is referenced by the immediate batch
     * queue when the native model draw is on ({@code ImmediateGroup.meshBorrowed}), and a group can outlive the frame
     * it was queued in when a submission was abandoned; the first drain only re-queues, and the second - one whole
     * frame and one more completed submission later - frees. The cost is one frame of extra host memory per retired
     * mesh, and it removes the window in which a group could be recorded against buffers that were already gone.
     * <p>
     * Retiring twice does nothing; deleting a retired mesh is what the queue will do, and {@link #delete()} is
     * idempotent.
     */
    public void retire() {
        if (this.retired || this.destroyed) {
            return;
        }
        this.retired = true;
        if (this.ownerList > 0) {
            celtium$uncommitted(this);
        }
        VulkanRetireQueue.retire(this::celtium$awaitSecondWait);
    }

    /** First drain: the device is idle, so one more frame of references can still be retired safely. */
    private void celtium$awaitSecondWait() {
        VulkanRetireQueue.retire(this::delete);
    }

    /**
     * Frees a committed mesh immediately, which is only legal right after a wait for device idle has succeeded.
     * <p>
     * This is the backend's teardown form: {@code settleGpu} has already completed the last submission and nothing more
     * will be recorded, so queueing the destruction would only leave the buffers alive past the device they belong to.
     * It is deliberately not {@link #delete()} called directly, so the count of meshes destroyed without a retirement
     * keeps meaning "freed while the GPU may still have been reading", which stays at zero (audit N02, N12's rule).
     */
    public void releaseAfterIdle() {
        this.releasedAfterIdle = true;
        delete();
    }

    /** {@return whether this mesh was destroyed on the teardown path, after a wait that succeeded}. */
    public boolean releasedAfterIdle() {
        return this.releasedAfterIdle;
    }

    /**
     * Frees a mesh that was built but never committed to the renderer's table, which is a partial build's leftovers.
     * <p>
     * Separate from {@link #delete()} only so the count of buffers a failed build created and released is a number the
     * acceptance can read. A build that throws must leave the previous table intact and free what it created; this is
     * the freeing half of that rule.
     */
    public void discard() {
        if (this.ownerList <= 0) {
            celtium$noteDiscarded();
        }
        delete();
    }

    private static synchronized void celtium$noteDiscarded() {
        modelDiscarded++;
    }

    /** {@return how many model meshes were stamped, which is one per successful mesh build}. */
    public static synchronized long built() {
        return modelBuilt;
    }

    /** {@return how many model meshes were handed to the retire queue}. */
    public static synchronized long retiredCount() {
        return modelRetired;
    }

    /** {@return how many model meshes have actually had their buffers freed}. */
    public static synchronized long destroyed() {
        return modelDestroyed;
    }

    /** {@return model meshes whose buffers exist, which is the live old-buffer count the acceptance asks for}. */
    public static synchronized long live() {
        return modelBuilt - modelDestroyed;
    }

    /**
     * {@return model meshes retired but not yet destroyed, waiting for a wait that succeeded}.
     * <p>
     * Every destruction is one of three kinds and the arithmetic has to subtract all of them from the retirements:
     * a queued retirement that ran, a direct free (which the renderer does not do and {@link #directDeletes()} counts),
     * and a teardown release after a wait that succeeded. Without the third the count went negative on the world-switch
     * run, where the backend's teardown released both meshes that were never retired.
     */
    public static synchronized long pendingRetirement() {
        return modelRetired - modelDestroyed + modelDirectDeleted + modelIdleReleased;
    }

    /** {@return how many committed meshes were destroyed without being retired first, which should stay zero}. */
    public static synchronized long directDeletes() {
        return modelDirectDeleted;
    }

    /** {@return how many meshes the backend released at teardown, after a wait for device idle that succeeded}. */
    public static synchronized long idleReleased() {
        return modelIdleReleased;
    }

    /** {@return how many never-committed meshes a failed build created and released}. */
    public static synchronized long discarded() {
        return modelDiscarded;
    }

    /**
     * {@return how many stamps found a live mesh still registered for their list and retired it themselves}.
     * <p>
     * The renderer's commit retires the superseded meshes in the same call, so this is zero when that order is kept and
     * one per rebuild when the stamp runs first - a measurement of the commit order, not a leak, because the stamp's
     * net frees it either way.
     */
    public static synchronized long stampedOverLive() {
        return stampedOverLive;
    }

    /** {@return the committed model meshes, the same set the renderer's own table holds}. */
    public static synchronized int committed() {
        return COMMITTED.size();
    }

    /** {@return the model-mesh lifetime counters and the derived live buffer count, for the run summary}. */
    public static synchronized String describeModel() {
        long live = modelBuilt - modelDestroyed;
        return "modelMeshBuilt=" + modelBuilt + " modelMeshRetired=" + modelRetired
                + " modelMeshDestroyed=" + modelDestroyed + " modelMeshDiscarded=" + modelDiscarded
                + " modelMeshDirectDeleted=" + modelDirectDeleted + " modelMeshIdleReleased=" + modelIdleReleased
                + " modelMeshStampedOverLive=" + stampedOverLive
                + " modelMeshLive=" + live + " modelBuffersLive=" + (live * 2L)
                + " modelMeshPendingRetire=" + (modelRetired - modelDestroyed + modelDirectDeleted + modelIdleReleased)
                + " modelMeshCommitted=" + COMMITTED.size();
    }

    /**
     * {@return one committed model mesh's identity and geometry, so an acceptance step can read the list it changed}.
     * <p>
     * The counts are the mesh's own: four vertices and six indices per captured quad. A recompiled list whose mesh
     * still held the previous compile's quads would show more, and a re-used id that inherited the deleted list's mesh
     * would show the old numbers and an old generation.
     *
     * @param listId the display list to describe
     * @return the description, or {@code "list=... committed=false"} when no mesh is committed for it
     */
    public static synchronized String describeMesh(int listId) {
        VulkanDynamicMesh mesh = COMMITTED.get(Integer.valueOf(listId));
        int captureGeneration = VulkanModelQuadCapture.generationOf(listId);
        int quads = VulkanModelQuadCapture.quadsForList(listId);
        if (mesh == null) {
            return "list=" + listId + " committed=false captureGeneration=" + captureGeneration
                    + " captureQuads=" + quads;
        }
        boolean ok = mesh.ownerGeneration == captureGeneration && mesh.vertexCount() == quads * 4
                && mesh.indexCount() == quads * 6 && mesh.vertexCount() % 4 == 0 && mesh.indexCount() % 6 == 0;
        return "list=" + listId + " committed=true meshGeneration=" + mesh.ownerGeneration
                + " captureGeneration=" + captureGeneration + " captureQuads=" + quads
                + " meshVertices=" + mesh.vertexCount() + " meshIndices=" + mesh.indexCount()
                + (ok ? " ok" : " MISMATCH");
    }

    /**
     * {@return the ownership invariant of the committed model meshes, so a run can be judged without reading the
     * renderer's private table}.
     * <p>
     * Three facts per list, all of them failing loudly rather than silently: <b>stale</b> - a committed mesh whose
     * generation is no longer its list's, which means the renderer would draw old geometry after a recompile, a delete
     * or a re-used id; <b>unmeshed</b> - a list that holds captured quads but no committed mesh, which means geometry
     * the renderer cannot draw; and <b>geometry mismatches</b> - a committed mesh whose vertex and index counts are not
     * the four vertices and six indices per captured quad the contract fixes, which is what says a recompiled list's
     * mesh really holds the new geometry rather than the old one with more quads appended.
     * <p>
     * The render thread and this check hold no lock across the renderer's own table, so a list compiled in the very
     * frame the check runs can read as unmeshed for that one frame; the caller verifies a frame after its change.
     */
    public static synchronized String verifyModelMeshes() {
        StringBuilder stale = new StringBuilder();
        StringBuilder unmeshed = new StringBuilder();
        StringBuilder mismatched = new StringBuilder();
        int consistent = 0;
        for (java.util.Map.Entry<Integer, VulkanDynamicMesh> entry : COMMITTED.entrySet()) {
            int listId = entry.getKey().intValue();
            VulkanDynamicMesh mesh = entry.getValue();
            int generation = VulkanModelQuadCapture.generationOf(listId);
            if (generation != mesh.ownerGeneration) {
                if (stale.length() > 0) {
                    stale.append(' ');
                }
                stale.append(listId).append(":meshGeneration=").append(mesh.ownerGeneration)
                        .append(" captureGeneration=").append(generation);
                continue;
            }
            consistent++;
            int quads = VulkanModelQuadCapture.quadsForList(listId);
            if (mesh.vertexCount() != quads * 4 || mesh.indexCount() != quads * 6) {
                if (mismatched.length() > 0) {
                    mismatched.append(' ');
                }
                mismatched.append(listId).append(":quads=").append(quads).append(" meshVertices=")
                        .append(mesh.vertexCount()).append(" meshIndices=").append(mesh.indexCount());
            }
        }
        for (int listId : VulkanModelQuadCapture.listIds()) {
            int quads = VulkanModelQuadCapture.quadsForList(listId);
            if (quads > 0 && !COMMITTED.containsKey(Integer.valueOf(listId))) {
                if (unmeshed.length() > 0) {
                    unmeshed.append(' ');
                }
                unmeshed.append(listId).append(':').append(quads).append("quads");
            }
        }
        return "committed=" + COMMITTED.size() + " consistent=" + consistent
                + " stale=" + (stale.length() == 0 ? "none" : stale.toString())
                + " unmeshed=" + (unmeshed.length() == 0 ? "none" : unmeshed.toString())
                + " geometryMismatches=" + (mismatched.length() == 0 ? "none" : mismatched.toString());
    }

    /**
     * Adds one mesh to the registry and prints one bounded lifecycle line.
     * <p>
     * One list has one committed mesh, and registering a new one for a list whose previous mesh is still live ends the
     * previous mesh's ownership here rather than leaving it allocated with nothing left to free it: that is exactly the
     * "rebuild replaces the table and the old mesh leaks" state the audit found (N02). The renderer's own commit retires
     * the superseded meshes in the same call, so this net should never be needed; when it is, it says so and counts
     * itself, because a silent absorption is how a leak becomes invisible.
     */
    private static synchronized void celtium$committed(VulkanDynamicMesh mesh, int listId, int generation,
            boolean counted) {
        VulkanDynamicMesh previous = COMMITTED.put(Integer.valueOf(listId), mesh);
        if (previous != null && previous != mesh && !previous.retired && !previous.destroyed) {
            stampedOverLive++;
            System.out.println("[Celtium] CELTIUM_MODEL_MESH_DISPLACED list=" + listId + " oldGeneration="
                    + previous.ownerGeneration() + " newGeneration=" + generation
                    + " - the previous mesh was still registered, so the stamp retired it");
            previous.retire();
        }
        if (counted) {
            modelBuilt++;
        }
        celtium$lifecycle("built", listId, generation);
    }

    /** Takes one mesh out of the registry and counts the retirement. */
    private static synchronized void celtium$uncommitted(VulkanDynamicMesh mesh) {
        modelRetired++;
        COMMITTED.remove(Integer.valueOf(mesh.ownerList), mesh);
        celtium$lifecycle("retired", mesh.ownerList, mesh.ownerGeneration);
    }

    /** Finishes a mesh's ownership: the registry and the counters see one destruction per mesh, never two. */
    private static synchronized void celtium$released(VulkanDynamicMesh mesh, boolean wasRetired) {
        modelDestroyed++;
        if (mesh.releasedAfterIdle) {
            // The teardown path: the caller waited for the device itself, so this is neither a retirement that ran nor
            // a free without a completed wait. Counted under its own name so the pending count stays exact.
            modelIdleReleased++;
        } else if (!wasRetired) {
            // Destroyed without being retired and without a completed wait: never the renderer's path (it retires, so
            // the GPU is finished first), but counted rather than hidden, because a direct free of a mesh the GPU may
            // still read is a defect. The teardown form sets releasedAfterIdle and is not counted here.
            modelDirectDeleted++;
        }
        COMMITTED.remove(Integer.valueOf(mesh.ownerList), mesh);
        celtium$lifecycle("destroyed", mesh.ownerList, mesh.ownerGeneration);
    }

    private static synchronized void celtium$lifecycle(String event, int listId, int generation) {
        lifecycleLogged++;
        if (lifecycleLogged > LIFECYCLE_LOG_LIMIT && lifecycleLogged % 100 != 0) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_MODEL_MESH " + event + " list=" + listId + " generation=" + generation
                + " " + describeModel() + " committed=" + COMMITTED.size());
    }

    private long currentVertex() {
        if (this.vertexCount == 0) {
            throw new IllegalStateException("nextVertex() must be called before any attribute is written");
        }
        return this.vertices.mappedAddress() + this.vertexBytes - this.layout.stride;
    }

    private void requireAttribute(int offset, String name) {
        if (offset < 0) {
            throw new IllegalStateException("the current vertex layout has no " + name + " attribute");
        }
    }
}
