package org.soutalab.celtium.vulkan;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Terrain GPU backend implemented with Vulkan.
 * <p>
 * It consumes the same meshing output, the same per-section metadata, the same culling and batch assembly and the
 * same render lists as the OpenGL backend; only the GPU resources and the draw calls differ. Renders into the
 * game's framebuffer through {@link VulkanFrameTarget}, which shares the colour and depth images with OpenGL.
 * <p>
 * Runs on the client/render thread with a current GL context, like the OpenGL backend it replaces.
 */
public final class VulkanTerrainGpuBackend implements org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend {
    /** Alignment required for dynamic uniform buffer offsets. Tightened at construction from the device limits. */
    private static final int MIN_UNIFORM_ALIGNMENT = 256;

    /**
     * Bytes per dynamic uniform block. Mirrors {@code CeltiumSolidUniforms} in
     * {@code celtium-vulkan/src/main/shaders/terrain_solid.vert}: region offset, fog colour and parameters,
     * material flags and the (currently unused) chunk fade parameters.
     */
    private static final int UNIFORM_BYTES = 80;

    private final VulkanContext context;
    private final RenderPassConfiguration<?> configuration;
    private final RenderRegionManager regionManager;
    private final FogService fogService;
    private final int commonVertexStride;
    private final int uniformAlignment;

    private final Reference2ReferenceMap<RenderRegion, RegionResources> regionResources = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceMap<TerrainRenderPass, VulkanTerrainPipeline> pipelines = new Reference2ReferenceOpenHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    private final Reference2ReferenceMap<ChunkPrimitiveType, VulkanBuffer> sharedIndexBuffers = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceMap<ChunkPrimitiveType, Integer> sharedIndexPrimitives = new Reference2ReferenceOpenHashMap<>();

    private VulkanUniformBuffer uniforms;
    private long uniformStride;
    private int uniformBlocks;
    private long uniformWriteCursor;

    private VulkanFrameTarget frameTarget;
    private int targetWidth;
    private int targetHeight;
    private long framebuffer;
    private long framebufferRenderPass;

    private VulkanGlTexture atlasTexture;
    private VulkanGlTexture lightmapTexture;
    private int atlasGlId = -1;
    private int lightmapGlId = -1;
    private long atlasRefreshCountdown;
    private long lightmapRefreshCountdown;

    private long commandPool;
    private VkCommandBuffer commandBuffer;
    private long fence;
    private long frameIndex;

    /**
     * The OpenGL projection remapped from the OpenGL clip-depth range [-1, 1] to Vulkan's [0, 1]. Without it every
     * fragment below the near plane would clamp to depth 0 and the terrain would fight the depth buffer that
     * OpenGL wrote. Reused instead of allocated per frame.
     */
    private final Matrix4f vulkanProjection = new Matrix4f();

    private long lastDrawCommands;
    private int command0Logged;
    private int verticesLogged;
    private boolean uniformsLogged;
    private long readbackBuffer;
    private long readbackMemory;
    private long readbackMapped;
    private boolean submissionPending;

    /**
     * Whether the shared images are currently handed to Vulkan. The round trip is per *frame*, not per pass: the
     * terrain is drawn by three passes in a row and handing the images back between them costs a full colour copy
     */
    /** CPU time spent inside the GL/Vulkan handoff, so the debug overlay can show what the round trip costs. */
    private long handoffNanos;
    private long handoffSamples;
    private long handoffWorstNanos;
    private int frameOpens;
    private int frameCloses;
    private int renderCalls;
    private TerrainRenderPass lastUiPass;
    private boolean swapchainProbeDone;
    private VulkanSwapchain swapchain;
    private int passSubmissions;
    private int passSkips;
    private int passErrors;
    private String lastSkipReason;

    /** Timestamp queries: two per render pass, so the debug overlay can show real terrain times under Vulkan. */
    /** Passes a single frame can have; used to reserve uniform blocks before recording. */
    private static final int MAX_UNIFORM_PASSES = 6;

    private static final int TIMESTAMP_SLOTS = 16;
    private long timestampPool;
    private float timestampPeriod = 1.0f;
    private boolean timestampsUsable;
    private final Reference2ReferenceMap<TerrainRenderPass, Long> passTimings = new Reference2ReferenceOpenHashMap<>();
    private boolean uploadVerified;
    private int beforeAfterLogged;
    private int readbackSlots;
    private long readbackOffset;
    private int timestampSlots;
    private final Reference2ReferenceMap<Integer, TerrainRenderPass> timestampPasses = new Reference2ReferenceOpenHashMap<>();
    private RegionResources readbackRegion;
    private boolean readbackPending;
    private boolean readbackDone;
    private long lastSectionsDrawn;
    private long lastUploadedBytes;

    public VulkanTerrainGpuBackend(VulkanContext context,
                                   RenderPassConfiguration<?> configuration,
                                   RenderRegionManager regionManager,
                                   FogService fogService) {
        this.context = context;
        this.configuration = configuration;
        this.regionManager = regionManager;
        this.fogService = fogService;
        this.commonVertexStride = configuration.getAllKnownRenderPasses()
                .mapToInt(pass -> pass.vertexType().getVertexFormat().getStride())
                .reduce(1, MathUtil::lcm);
        this.uniformAlignment = Math.max(MIN_UNIFORM_ALIGNMENT,
                Integer.highestOneBit(Math.max(MIN_UNIFORM_ALIGNMENT, this.alignmentFromDevice())));
        this.uniformStride = align(UNIFORM_BYTES, this.uniformAlignment);
        createCommandResources();
    }

    /** Records the Vulkan backend in place of the OpenGL one for the batch assembly path. */
    public void install() {
        BatchAssembler.setBatchFactory(VulkanMultiDrawBatch::new);
    }

    /** Restores the OpenGL batch factory when this backend is torn down. */
    private void uninstall() {
        BatchAssembler.setBatchFactory(org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch::new);
    }

    /** Human-readable state for logs and debug overlays; never null. */
    public String describe() {
        StringBuilder builder = new StringBuilder("vulkan");
        builder.append(" [").append(this.context.deviceName()).append(']');
        builder.append(" regions=").append(this.regionResources.size());
        builder.append(" draws=").append(this.lastDrawCommands);
        builder.append(" sections=").append(this.lastSectionsDrawn);
        builder.append(" uploaded=").append(this.lastUploadedBytes).append("B");
        builder.append(" index=").append(this.sharedIndexBuffers.size());
        if (this.frameTarget != null) {
            builder.append(" target=").append(this.targetWidth).append('x').append(this.targetHeight);
        }
        for (String diagnostic : this.diagnostics) {
            builder.append(" | ").append(diagnostic);
        }
        return builder.toString();
    }

    public List<String> diagnostics() {
        return this.diagnostics;
    }

    // ------------------------------------------------------------------ TerrainGpuBackend

    @Override
    public String getName() {
        return "vulkan";
    }

    @Override
    public void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                       CameraTransform occlusionCamera, CameraTransform camera, boolean profile) {
        if (!renderLists.hasPass(pass)) {
            // No geometry for this pass; nothing is opened, nothing has to be closed.
            return;
        }
        if (!ensureFrameTarget()) {
            this.passSkips++;
            this.lastSkipReason = pass.name() + ": no frame target";
            return;
        }
        VulkanTerrainPipeline pipeline = pipelineFor(pass);
        if (pipeline == null) {
            this.passSkips++;
            this.lastSkipReason = pass.name() + ": no pipeline";
            return;
        }
        ChunkPrimitiveType primitiveType = this.configuration.getPrimitiveTypeForPass(pass);
        ensureSharedIndexBuffer(primitiveType);
        // Reserve every uniform block this pass can possibly need *before* anything is recorded. VulkanUniformBuffer
        // growth allocates a new buffer and destroys the old one immediately, so a growth after the first region's
        // descriptors were bound would leave the already recorded draws of this pass pointing at freed memory - the
        // queue wait cannot help, because those draws have not been submitted yet. One block per region draw, times
        // the number of passes a frame can have, is a cheap upper bound (a few kilobytes).
        ensureUniformCapacity((this.regionResources.size() * MAX_UNIFORM_PASSES) + 1);

        refreshTextures();
        if (this.atlasTexture == null || this.lightmapTexture == null) {
            // Without both textures the descriptor set cannot be filled; the pass is skipped and the reason is
            // recorded instead of drawing with an unbound atlas.
            this.passSkips++;
            this.lastSkipReason = pass.name() + ": atlas or lightmap not mirrored";
            record("pass " + pass.name() + " skipped: the block atlas or the lightmap is not mirrored yet");
            return;
        }

        boolean useBlockFaceCulling = !pass.isSorted();
        this.lastDrawCommands = 0;
        this.lastSectionsDrawn = 0;
        this.frameIndex++;

        this.renderCalls++;
        this.lastUiPass = pass;

        // One complete handoff per pass. GL signals its GL->Vulkan semaphore exactly once in beginFrame() and waits
        // for the Vulkan->GL semaphore exactly once in endFrame(), so exactly one submission must consume the first
        // and signal the second. Aggregating several passes into one handoff (the previous design) made the Vulkan
        // side wait that semaphore once per pass while GL signalled it once per frame, which left the second and
        // third pass of every frame waiting for the next frame's signal - and a frame whose sorted pass had no
        // geometry never returned the images to GL at all, so the entities, hand and GUI that GL draws afterwards
        // were composed over a stale terrain. The aggregation is gone until a boundary signal exists that the
        // renderer itself provides.
        try {
            long handoffStart = System.nanoTime();
            this.frameTarget.beginFrame();
            this.frameOpens++;
            recordHandoff(System.nanoTime() - handoffStart);
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("frame begin failed: " + describe(error));
            return;
        }

        boolean submitted = false;
        try {
            beginCommands();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                recordRenderPass(stack, pipeline, matrices, renderLists, pass, occlusionCamera, camera,
                        useBlockFaceCulling, primitiveType);
            }
            submitCommands();
            submitted = true;
            this.passSubmissions++;
            if (this.readbackPending) {
                readBackAndLog();
            }
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("render failed: " + describe(error));
        }

        if (!submitted) {
            // endFrame() makes GL wait on the Vulkan->GL semaphore, and GL has no timeout. A submission must
            // therefore consume the GL->Vulkan semaphore and signal the Vulkan->GL one even when nothing was
            // recorded: an empty acquire/release round trip hands the untouched images straight back, which is what
            // the game expects. Only the first attempt is repeated - a failing recovery is reported and endFrame()
            // still runs so the frame cannot be left open.
            try {
                waitForIdle();
                beginCommands();
                this.frameTarget.recordAcquireBarriers(this.commandBuffer);
                this.frameTarget.recordReleaseBarriers(this.commandBuffer);
                submitCommands();
            } catch (RuntimeException | LinkageError error) {
                record("frame recovery failed: " + describe(error));
            }
        }

        try {
            long handoffStart = System.nanoTime();
            this.frameTarget.endFrame();
            this.frameCloses++;
            recordHandoff(System.nanoTime() - handoffStart);
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("frame end failed: " + describe(error));
        }
    }

    // ---------------------------------------------------------------- immediate-mode drawing (Forge API swap)

    /** The state the GlStateManager Mixin mirrors into; the draw entry turns it into a pipeline key. */
    private final VulkanGlState immediateState = new VulkanGlState();
    private int immediateStateCalls;
    private int immediateBatchesOffered;
    private String immediateLastRefusal = "none";
    private int immediateTaken;
    /** Per-channel counters: which mirror actually reaches the backend is measured, not assumed. */
    private int perspectiveCalls;
    private int frustumCalls;
    private int matrixModeCalls;
    private int loadIdentityCalls;
    private int immediateFlushes;
    private int pendingImmediateIndices;
    private VulkanDynamicMesh pendingImmediate;
    private VulkanImmediatePipeline immediatePipeline;
    /** The matrices in force when the pending batches were built; the flush runs a frame later. */
    private final float[] pendingProjection = new float[16];
    private final float[] pendingModelView = new float[16];
    private int pendingGroups;
    private final float[] glProjection = new float[16];
    private final float[] glModelView = new float[16];

    @Override
    public void setBlendEnabled(boolean enabled) {
        this.immediateState.blendEnabled(enabled);
        this.immediateStateCalls++;
    }

    @Override
    public void setBlendFunc(int sourceRgb, int destRgb, int sourceAlpha, int destAlpha) {
        this.immediateState.blendFunc(sourceRgb, destRgb, sourceAlpha, destAlpha);
        this.immediateStateCalls++;
    }

    @Override
    public void setDepthMask(boolean mask) {
        this.immediateState.depthMask(mask);
        this.immediateStateCalls++;
    }

    @Override
    public void setDepthTest(boolean enabled) {
        this.immediateState.depthTestEnabled(enabled);
        this.immediateStateCalls++;
    }

    @Override
    public void setColor(float red, float green, float blue, float alpha) {
        this.immediateState.color(red, green, blue, alpha);
        this.immediateStateCalls++;
    }

    @Override
    public void matrixMode(int glMode) {
        // OpenGL's constants, mapped onto the three stacks the state model keeps.
        if (glMode == 5889) {
            this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        } else if (glMode == 5890) {
            this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_TEXTURE);
        } else {
            this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_MODELVIEW);
        }
        this.immediateStateCalls++;
        this.matrixModeCalls++;
    }

    @Override
    public void loadIdentity() {
        this.immediateState.loadIdentity();
        this.immediateStateCalls++;
        this.loadIdentityCalls++;
    }

    @Override
    public void ortho(double left, double right, double bottom, double top, double near, double far) {
        float[] matrix = new float[16];
        // The OpenGL ortho matrix, written column-major, exactly as glOrtho builds it.
        matrix[0] = (float) (2.0 / (right - left));
        matrix[5] = (float) (2.0 / (top - bottom));
        matrix[10] = (float) (-2.0 / (far - near));
        matrix[12] = (float) (-(right + left) / (right - left));
        matrix[13] = (float) (-(top + bottom) / (top - bottom));
        matrix[14] = (float) (-(far + near) / (far - near));
        matrix[15] = 1.0f;
        this.immediateState.loadMatrix(matrix);
        this.immediateStateCalls++;
    }

    @Override
    public void translate(double x, double y, double z) {
        this.immediateState.translate(x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        this.immediateState.rotate(angle, x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void scale(float x, float y, float z) {
        this.immediateState.scale(x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void perspective(float fovDegrees, float aspect, float near, float far) {
        if (aspect == 0.0f || near == far) {
            return;
        }
        double f = 1.0 / Math.tan(Math.toRadians(fovDegrees) / 2.0);
        float[] frustum = new float[16];
        frustum[0] = (float) (f / aspect);
        frustum[5] = (float) f;
        frustum[10] = (far + near) / (near - far);
        frustum[11] = -1.0f;
        frustum[14] = (2.0f * far * near) / (near - far);
        // glFrustum multiplies, so the mirrored projection stack must multiply too.
        int mode = this.immediateState.matrixMode();
        this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        this.immediateState.multiplyMatrix(frustum);
        this.immediateState.matrixMode(mode);
        this.immediateStateCalls++;
    }

    @Override
    public void frustum(double left, double right, double bottom, double top, double near, double far) {
        double width = right - left;
        double height = top - bottom;
        double depth = far - near;
        if (width == 0.0 || height == 0.0 || depth == 0.0) {
            return;
        }
        float[] matrix = new float[16];
        matrix[0] = (float) ((2.0 * near) / width);
        matrix[5] = (float) ((2.0 * near) / height);
        matrix[8] = (float) ((right + left) / width);
        matrix[9] = (float) ((top + bottom) / height);
        matrix[10] = (float) (-(far + near) / depth);
        matrix[11] = -1.0f;
        matrix[14] = (float) (-(2.0 * far * near) / depth);
        int mode = this.immediateState.matrixMode();
        this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        this.immediateState.multiplyMatrix(matrix);
        this.immediateState.matrixMode(mode);
        this.immediateStateCalls++;
        this.frustumCalls++;
    }

    @Override
    public void multiplyMatrix(java.nio.FloatBuffer buffer) {
        this.immediateState.multiplyMatrixBuffer(buffer);
        this.immediateStateCalls++;
    }

    @Override
    public void pushMatrix() {
        this.immediateState.pushMatrix();
        this.immediateStateCalls++;
    }

    @Override
    public void popMatrix() {
        this.immediateState.popMatrix();
        this.immediateStateCalls++;
    }

    @Override
    public boolean submitImmediateBatch(Object vertexFormat, int vertexStride, long vertexAddress, int vertexCount,
                                        int drawMode) {
        this.immediateBatchesOffered++;
        String reason = VulkanImmediateDrawer.unsupportedReason(drawMode);
        if (reason != null) {
            this.immediateLastRefusal = reason;
            return false;
        }
        if (vertexStride != VulkanImmediatePipeline.VERTEX_BYTES) {
            // Only the POSITION_COLOR layout has a pipeline so far. Refusing by stride - not by class name - keeps
            // this structural: any mod format with those 16 bytes takes the same path.
            this.immediateLastRefusal = "no pipeline yet for a " + vertexStride + "-byte vertex";
            return false;
        }
        int[] indices = VulkanImmediateDrawer.expandIndices(drawMode, vertexCount);
        if (indices == null) {
            this.immediateLastRefusal = "topology " + drawMode + " has no expansion";
            return false;
        }
        try {
            if (this.pendingImmediate == null) {
                this.pendingImmediate = new VulkanDynamicMesh(this.context);
            }
            if (!this.pendingImmediate.copyVertices(vertexAddress, vertexStride, vertexCount, indices)) {
                this.immediateLastRefusal = "the pending batch buffer refused the vertices";
                return false;
            }
            this.pendingImmediateIndices += indices.length;
            if (this.pendingGroups == 0) {
                // The matrices are captured with the first batch of the group: by the time the flush runs, a frame
                // later, the live state has moved on (GUI ortho, world transforms) and would place the geometry
                // somewhere else entirely.
                // The matrices are read from OpenGL itself at the moment the batch is built. Mirroring them through
                // Mixins was measured to fail for the projection: the world projection is installed by LWJGL 2's GLU
                // utility class, which the transforming loader does not own, so no injector fires there
                // (`matrixChannels=persp:0`). Reading the live matrices on the render thread, with the GL context
                // current, needs no cooperation from any class and is exactly what Minecraft is drawing with.
                captureGlMatrices();
                // OpenGL's clip depth runs -1..1 and Vulkan's 0..1, the same remap the terrain projection uses.
                remapClipDepth(this.glProjection, this.pendingProjection);
                System.arraycopy(this.glModelView, 0, this.pendingModelView, 0, 16);
            }
            this.pendingGroups++;
            this.immediateTaken++;
            this.immediateLastRefusal = "none";
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.immediateLastRefusal = "copy failed: " + describe(error);
            record("immediate batch refused: " + describe(error));
            return false;
        }
    }

    /**
     * {@return a machine-readable summary of what this backend actually drew}. The fixture reads it to decide
     * whether a run really rendered terrain: selecting the backend by name is not evidence that any pass reached the
     * queue, so submissions, skips and errors are counted instead.
     */
    public String verificationSummary() {
        return "celtium/vulkan-check passes=" + this.passSubmissions + " skipped=" + this.passSkips + " errors="
                + this.passErrors + " lastDraw=" + this.lastDrawCommands + " lastSections=" + this.lastSectionsDrawn
                + " skipReason=" + (this.lastSkipReason == null ? "none" : this.lastSkipReason.replace(' ', '_'))
                + " immediateState=" + this.immediateStateCalls + " immediateOffered=" + this.immediateBatchesOffered
                + " immediateTaken=" + this.immediateTaken + " immediateFlushes=" + this.immediateFlushes
                + " immediateRefusal=" + this.immediateLastRefusal.replace(' ', '_')
                + " matrixChannels=persp:" + this.perspectiveCalls + ",frustum:" + this.frustumCalls
                + ",mode:" + this.matrixModeCalls + ",identity:" + this.loadIdentityCalls;
    }

    @Override
    public void uploadMeshes(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger) {
        Reference2ReferenceMap<RenderRegion, List<ChunkTaskOutput>> queues = new Reference2ReferenceOpenHashMap<>();
        for (ChunkJobResult.Success<? extends ChunkTaskOutput> holder : results) {
            ChunkTaskOutput output = holder.output();
            List<ChunkTaskOutput> queue = queues.get(output.render.getRegion());
            if (queue == null) {
                queue = new ArrayList<>();
                queues.put(output.render.getRegion(), queue);
            }
            queue.add(output);
        }

        for (Reference2ReferenceMap.Entry<RenderRegion, List<ChunkTaskOutput>> entry : queues.reference2ReferenceEntrySet()) {
            uploadRegion(entry.getKey(), entry.getValue(), graphUpdateTrigger);
        }
    }

    @Override
    public void updateRegions() {
        // The region manager owns region lifetime (creating regions for loaded chunks and dropping empty ones).
        // It is reused unchanged; the Vulkan arenas are released for the regions it has dropped.
        this.regionManager.update();
        if (this.regionResources.isEmpty()) {
            return;
        }
        Set<RenderRegion> live = Collections.newSetFromMap(new IdentityHashMap<RenderRegion, Boolean>());
        live.addAll(this.regionManager.getLoadedRegions());
        Iterator<Reference2ReferenceMap.Entry<RenderRegion, RegionResources>> iterator =
                this.regionResources.reference2ReferenceEntrySet().iterator();
        while (iterator.hasNext()) {
            Reference2ReferenceMap.Entry<RenderRegion, RegionResources> entry = iterator.next();
            if (!live.contains(entry.getKey())) {
                entry.getValue().delete();
                iterator.remove();
            }
        }
    }

    @Override
    public void updateTimings() {
        if (this.timestampPool == 0 || this.timestampSlots == 0 || !this.timestampsUsable) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int queries = this.timestampSlots * 2;
            LongBuffer data = stack.mallocLong(queries * 2);
            int result = vkGetQueryPoolResults(this.context.device(), this.timestampPool, 0, queries, data,
                    Long.BYTES * 2, VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
            if (result != VK_SUCCESS && result != VK_NOT_READY) {
                return;
            }
            for (int slot = 0; slot < this.timestampSlots; slot++) {
                long startAvailable = data.get((slot * 2 * 2) + 1);
                long endAvailable = data.get((slot * 2 * 2) + 3);
                if (startAvailable == 0 || endAvailable == 0) {
                    // Keep whatever was measured for this pass last time instead of reporting "?? ms".
                    continue;
                }
                long start = data.get(slot * 2 * 2);
                long end = data.get((slot * 2 * 2) + 2);
                if (end >= start) {
                    this.passTimings.put(this.timestampPasses.get(slot),
                            (long) ((end - start) * this.timestampPeriod));
                }
            }
        }
        this.timestampPasses.clear();
        this.timestampSlots = 0;
    }

    @Override
    public Object2LongMap<TerrainRenderPass> snapshotTimings() {
        return new Object2LongOpenHashMap<>(this.passTimings);
    }

    @Override
    public void collectMemoryStats(RenderSectionManager.DeviceMemoryStats destination) {
        for (RegionResources resources : this.regionResources.values()) {
            destination.deviceUsed += resources.vertexArena.deviceUsedMemoryL();
            destination.deviceAllocated += resources.vertexArena.deviceAllocatedMemoryL();
            if (resources.indexArena != null) {
                destination.indexUsed += resources.indexArena.deviceUsedMemoryL();
                destination.indexAllocated += resources.indexArena.deviceAllocatedMemoryL();
            }
            destination.bufferCount += resources.bufferCount();
        }
    }

    private String depthSharingSummary;

    /**
     * Roadmap S4/S6 wiring: when enabled, the first non-terrain geometry (a textured quad built through
     * {@link VulkanDynamicMesh} and drawn by {@link VulkanUiPipeline}) is recorded into the same render pass as the
     * terrain, so the whole path - dynamic vertex stream, second pipeline, second texture binding, blending and
     * push constants - is exercised inside the frame that the validation harness already checks.
     */
    private final boolean uiTestEnabled = Boolean.getBoolean("celtium.vulkan.uiTest");
    private VulkanUiPipeline uiPipeline;
    private VulkanDynamicMesh uiMesh;

    private void recordHandoff(long nanos) {
        this.handoffNanos += nanos;
        this.handoffSamples++;
        if (nanos > this.handoffWorstNanos) {
            this.handoffWorstNanos = nanos;
        }
    }

    /** {@return the average and worst CPU time of the GL/Vulkan handoff since the last call, or null} */
    private String takeHandoffSummary() {
        if (this.handoffSamples == 0) {
            return null;
        }
        String summary = "handoff: avg " + (this.handoffNanos / this.handoffSamples / 1000L) + " us over "
                + this.handoffSamples + " calls (" + this.frameOpens + " opens, " + this.frameCloses + " closes, "
                + this.renderCalls + " pass calls), worst " + (this.handoffWorstNanos / 1000L) + " us";
        this.frameOpens = 0;
        this.frameCloses = 0;
        this.renderCalls = 0;
        this.handoffNanos = 0L;
        this.handoffSamples = 0L;
        this.handoffWorstNanos = 0L;
        return summary;
    }

    @Override
    public String getTransferQueueDebugString() {
        // The debug overlay shows this line, so it carries the backend's own state: without it a terrain pass that
        // silently skips (missing textures, no frame target, a failed pipeline) would look like a rendering bug.
        if (this.depthSharingSummary == null) {
            try {
                this.depthSharingSummary = VulkanFrameTarget.describeDepthFormatSharing(this.context);
            } catch (RuntimeException | LinkageError error) {
                this.depthSharingSummary = "shared depth formats: probe failed";
            }
        }
        String handoff = takeHandoffSummary();
        String detail = this.frameTarget == null ? null : this.frameTarget.takeTimingSummary();
        String base = describe();
        String withHandoff = handoff == null ? base : base + " | " + handoff;
        String withDetail = detail == null ? withHandoff : withHandoff + " | " + detail;
        return withDetail + " | " + this.depthSharingSummary + " | " + verificationSummary();
    }

    @Override
    public void delete() {
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(null);
        uninstall();
        for (RegionResources resources : this.regionResources.values()) {
            resources.delete();
        }
        this.regionResources.clear();
        for (VulkanTerrainPipeline pipeline : this.pipelines.values()) {
            pipeline.delete();
        }
        this.pipelines.clear();
        for (VulkanBuffer buffer : this.sharedIndexBuffers.values()) {
            buffer.delete();
        }
        this.sharedIndexBuffers.clear();
        this.sharedIndexPrimitives.clear();
        if (this.uniforms != null) {
            this.uniforms.delete();
            this.uniforms = null;
        }
        if (this.atlasTexture != null) {
            this.atlasTexture.delete();
            this.atlasTexture = null;
        }
        if (this.lightmapTexture != null) {
            this.lightmapTexture.delete();
            this.lightmapTexture = null;
        }
        if (this.readbackMapped != 0) {
            vkUnmapMemory(this.context.device(), this.readbackMemory);
            this.readbackMapped = 0;
        }
        if (this.readbackBuffer != 0) {
            vkDestroyBuffer(this.context.device(), this.readbackBuffer, null);
            this.readbackBuffer = 0;
        }
        if (this.readbackMemory != 0) {
            vkFreeMemory(this.context.device(), this.readbackMemory, null);
            this.readbackMemory = 0;
        }
        destroyFramebuffer();
        if (this.frameTarget != null) {
            this.frameTarget.delete();
            this.frameTarget = null;
        }
        destroyCommandResources();
    }

    // ------------------------------------------------------------------ upload path

    private void uploadRegion(RenderRegion region, List<ChunkTaskOutput> outputs, Runnable graphUpdateTrigger) {
        List<PendingMesh> pending = new ArrayList<>();
        boolean needIndexBuffer = false;

        for (ChunkTaskOutput output : outputs) {
            if (output instanceof ChunkBuildOutput) {
                ChunkBuildOutput build = (ChunkBuildOutput) output;
                region.removeMeshes(build.render.getSectionIndex());
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, BuiltSectionMeshParts> meshEntry
                        : build.meshes.reference2ReferenceEntrySet()) {
                    BuiltSectionMeshParts mesh = meshEntry.getValue();
                    if (mesh == null) {
                        continue;
                    }
                    needIndexBuffer |= mesh.indexBuffer() != null;
                    pending.add(new PendingMesh(build.render.getSectionIndex(), meshEntry.getKey(), mesh, null,
                    resourcesFor(region).sectionFirstColor));
                }
            } else if (output instanceof ChunkSortOutput) {
                ChunkSortOutput sort = (ChunkSortOutput) output;
                needIndexBuffer = true;
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, ChunkSortOutput.SortedMesh> meshEntry
                        : sort.meshes.reference2ReferenceEntrySet()) {
                    SectionRenderDataStorage storage = region.getStorage(meshEntry.getKey());
                    if (storage != null) {
                        storage.removeIndexBuffer(sort.render.getSectionIndex());
                    }
                    pending.add(new PendingMesh(sort.render.getSectionIndex(), meshEntry.getKey(), null,
                            meshEntry.getValue(), null));
                }
            } else {
                throw new IllegalStateException("Unexpected result type: " + output.getClass().getName());
            }
        }

        if (pending.isEmpty()) {
            return;
        }

        RegionResources resources = resourcesFor(region);
        long uploaded = 0;

        for (PendingMesh mesh : pending) {
            if (mesh.parts != null) {
                ByteBuffer vertices = mesh.parts.vertexBuffer().getDirectBuffer();
                int vertexBytes = mesh.parts.vertexBuffer().getLength();
                if (mesh.sectionFirstColor != null) {
                    mesh.sectionFirstColor[mesh.sectionIndex] = firstVertexColor(vertices);
                }
                int beforeColour = firstVertexColor(vertices);
                long beforeAddress = MemoryUtil.memAddress(vertices);
                VulkanArena.Segment vertexSegment = resources.vertexArena.allocate(vertexBytes);
                resources.vertexArena.write(vertexSegment.byteOffset(), MemoryUtil.memAddress(vertices), vertexBytes);
                if (beforeAfterLogged < 4) {
                    beforeAfterLogged++;
                    int mid = Math.max(0, (vertexBytes / 2 / 32) * 32);
                    int midColour = firstVertexColor(vertices, mid);
                    record("copy check: before=0x" + Integer.toHexString(beforeColour)
                            + " after=0x" + Integer.toHexString(firstVertexColor(vertices))
                            + " address=" + beforeAddress + " segmentOffset=" + vertexSegment.byteOffset()
                            + " bytes=" + vertexBytes + " position=" + vertices.position()
                            + " mid=0x" + Integer.toHexString(midColour));
                }
                uploaded += vertexBytes;

                VulkanArena.Segment indexSegment = null;
                if (mesh.parts.indexBuffer() != null) {
                    ByteBuffer indices = mesh.parts.indexBuffer().getDirectBuffer();
                    int indexBytes = mesh.parts.indexBuffer().getLength();
                    indexSegment = indexArena(resources).allocate(indexBytes);
                    indexArena(resources).write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                    uploaded += indexBytes;
                }

                SectionRenderDataStorage storage = region.createStorage(mesh.pass, this.configuration);
                storage.setMeshes(mesh.sectionIndex, vertexSegment, indexSegment, mesh.parts.ranges());
            } else if (mesh.sorted != null) {
                ByteBuffer indices = mesh.sorted.indexData().getDirectBuffer();
                int indexBytes = mesh.sorted.indexData().getLength();
                VulkanArena.Segment indexSegment = indexArena(resources).allocate(indexBytes);
                indexArena(resources).write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                uploaded += indexBytes;

                SectionRenderDataStorage storage = region.createStorage(mesh.pass, this.configuration);
                storage.replaceIndexBuffer(mesh.sectionIndex, indexSegment);
            }
        }

        int previousCookie = region.getPassSetUpdateCount();
        region.removeEmptyStorages();
        if (region.getPassSetUpdateCount() != previousCookie) {
            graphUpdateTrigger.run();
        }

        this.lastUploadedBytes = uploaded;
        logFirstVertex(pending);

    }

    /**
     * Decodes the first vertex of the first uploaded mesh and records it. The values the shader receives are the
     * only way to tell an attribute-layout problem from a transform problem, and the upload path already has the
     * bytes in hand.
     */
    private final java.util.HashMap<Integer, Integer> colourCounts = new java.util.HashMap<>();
    private int colourBatches;

    private void logFirstVertex(java.util.List<PendingMesh> pending) {
        for (PendingMesh mesh : pending) {
            if (mesh.parts != null && mesh.sectionFirstColor != null) {
                int colour = mesh.sectionFirstColor[mesh.sectionIndex];
                Integer previous = colourCounts.get(colour);
                colourCounts.put(colour, previous == null ? 1 : previous + 1);
            }
        }
        this.colourBatches++;
        if (this.colourBatches == 1 || this.colourBatches % 20 == 0) {
            java.util.List<java.util.Map.Entry<Integer, Integer>> entries =
                    new java.util.ArrayList<>(colourCounts.entrySet());
            java.util.Collections.sort(entries, (a, b) -> b.getValue() - a.getValue());
            StringBuilder line = new StringBuilder("mesh colour census (batches=" + this.colourBatches + "):");
            for (int i = 0; i < Math.min(5, entries.size()); i++) {
                line.append(" 0x").append(Integer.toHexString(entries.get(i).getKey()))
                        .append(" x").append(entries.get(i).getValue());
            }
            record(line.toString());
        }
        if (verticesLogged >= 8) {
            return;
        }
        for (PendingMesh mesh : pending) {
            if (mesh.parts != null && mesh.sectionFirstColor != null) {
                // Counted for every mesh, not just the first few, so a colour that only some sections carry shows up.
            }
        }
        if (verticesLogged >= 8) {
            return;
        }
        for (PendingMesh mesh : pending) {
            if (mesh.parts == null) {
                continue;
            }
            int stride = mesh.pass.vertexType().getVertexFormat().getStride();
            ByteBuffer vertices = mesh.parts.vertexBuffer().getDirectBuffer();
            if (vertices.remaining() < stride) {
                continue;
            }
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            StringBuilder line = new StringBuilder("vertex0 stride=").append(stride);
            if (stride == 32) {
                line.append(" pos=(").append(vertices.getFloat(0)).append(',').append(vertices.getFloat(4))
                        .append(',').append(vertices.getFloat(8)).append(')');
                line.append(" colorARGB=0x").append(Integer.toHexString(vertices.getInt(12)));
                line.append(" uv=(").append(vertices.getFloat(16)).append(',').append(vertices.getFloat(20)).append(')');
                line.append(" light=0x").append(Integer.toHexString(vertices.getInt(24)));
                int packed = vertices.getInt(24) >>> 16;
                line.append(" block=").append(packed & 0xFF).append(" sky=").append((packed >> 8) & 0xFF);
                line.append(" material=").append(packed & 0xFF).append(" section=").append((packed >> 8) & 0xFF);
            } else {
                line.append(" posId=0x").append(Integer.toHexString(vertices.getInt(0)));
                line.append(" colorARGB=0x").append(Integer.toHexString(vertices.getInt(8)));
                line.append(" light=0x").append(Integer.toHexString(vertices.getInt(16)));
            }
            verticesLogged++;
            record("mesh" + verticesLogged + " pass=" + mesh.pass.name() + " " + line);
            return;
        }
    }

    /** Colour word of the first vertex of a mesh, in the little-endian byte order the GPU reads. */
    private static int firstVertexColor(ByteBuffer vertices, int at) {
        java.nio.ByteOrder order = vertices.order();
        try {
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return vertices.getInt(at + 12);
        } finally {
            vertices.order(order);
        }
    }

    private static int firstVertexColor(ByteBuffer vertices) {
        java.nio.ByteOrder order = vertices.order();
        try {
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return vertices.getInt(12);
        } finally {
            vertices.order(order);
        }
    }

    /**
     * Debug aid: copies the arena bytes for the just-uploaded range back to host memory and decodes them. This is
     * the one comparison that the draw-time readback cannot make - whether the arena holds what the upload path was
     * given.
     */
    private void verifyUpload(RegionResources resources, java.util.List<PendingMesh> pending) {
        if (uploadVerified || resources.vertexArena.isEmpty()) {
            return;
        }
        for (PendingMesh mesh : pending) {
            if (mesh.parts == null) {
                continue;
            }
            uploadVerified = true;
            ensureReadback();
            long offset = 0;
            VkCommandBuffer commands = this.context.beginCommands();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                org.lwjgl.vulkan.VkBufferCopy.Buffer region = org.lwjgl.vulkan.VkBufferCopy.calloc(1, stack);
                region.get(0).srcOffset(offset).dstOffset(0L).size(64L);
                vkCmdCopyBuffer(commands, resources.vertexArena.buffer().buffer(), this.readbackBuffer, region);
                org.lwjgl.vulkan.VkMemoryBarrier.Buffer barrier = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_HOST_READ_BIT);
                vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0,
                        barrier, null, null);
            } finally {
                this.context.endCommands(commands);
            }
            ByteBuffer view = MemoryUtil.memByteBuffer(this.readbackMapped, 64);
            view.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            record("arena after upload: pos=(" + view.getFloat(0) + "," + view.getFloat(4) + ","
                    + view.getFloat(8) + ") colour=0x" + Integer.toHexString(view.getInt(12))
                    + " uv=(" + view.getFloat(16) + "," + view.getFloat(20) + ") light=0x"
                    + Integer.toHexString(view.getInt(24))
                    + " | source meshes=" + pending.size());
            return;
        }
    }

    private RegionResources resourcesFor(RenderRegion region) {
        RegionResources resources = this.regionResources.get(region);
        if (resources == null) {
            resources = new RegionResources(this.context, this.commonVertexStride);
            this.regionResources.put(region, resources);
        }
        return resources;
    }

    private VulkanArena indexArena(RegionResources resources) {
        if (resources.indexArena == null) {
            resources.indexArena = new VulkanArena(this.context, 64 * 1024, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 4);
        }
        return resources.indexArena;
    }

    // ------------------------------------------------------------------ draw path

    private void recordRenderPass(MemoryStack stack,
                                  VulkanTerrainPipeline pipeline,
                                  ChunkRenderMatrices matrices,
                                  ChunkRenderListIterable renderLists,
                                  TerrainRenderPass pass,
                                  CameraTransform occlusionCamera,
                                  CameraTransform camera,
                                  boolean useBlockFaceCulling,
                                  ChunkPrimitiveType primitiveType) {
        ensureUniformCapacity(this.regionResources.size());
        // The descriptor set points at the atlas, the lightmap and the uniform buffer for this pass. It is rewritten
        // once per pass per frame, which is a few descriptor writes and removes any staleness after a re-upload.
        pipeline.bindTextures(this.atlasTexture, this.lightmapTexture, this.uniforms.buffer(), this.uniforms.size());

        this.frameTarget.recordAcquireBarriers(this.commandBuffer);

        long renderPass = pipeline.renderPass();
        VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                .renderPass(renderPass)
                .framebuffer(ensureFramebuffer(renderPass));
        begin.renderArea().offset().set(0, 0);
        begin.renderArea().extent().set(this.targetWidth, this.targetHeight);
        int timestampSlot = this.timestampsUsable && this.timestampSlots < (TIMESTAMP_SLOTS / 2)
                ? this.timestampSlots : -1;
        if (timestampSlot >= 0) {
            vkCmdWriteTimestamp(this.commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, this.timestampPool,
                    timestampSlot * 2);
        }
        vkCmdBeginRenderPass(this.commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);

        setViewportAndScissor(stack);
        pipeline.bindPipeline(this.commandBuffer);

        Iterator<ChunkRenderList> iterator = renderLists.iterator(pass.isReverseOrder());
        SectionRenderDataStorage.BatchCacheParams cacheParams =
                new SectionRenderDataStorage.BatchCacheParams(useBlockFaceCulling);
        boolean indexBound = false;

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();
            RenderRegion region = renderList.getRegion();
            RegionResources resources = this.regionResources.get(region);
            SectionRenderDataStorage storage = region.getStorage(pass);

            if (resources == null || storage == null) {
                continue;
            }

            CachedBatch cached = storage.getCachedMultiDrawBatch(cacheParams);
            if (cached == null || !cached.isValidFor(renderList.getSectionsWithGeometry(),
                    renderList.getSectionsWithGeometryCount(), occlusionCamera.intX, occlusionCamera.intY,
                    occlusionCamera.intZ)) {
                cached = BatchAssembler.createCachedBatch(region, storage, renderList, occlusionCamera, pass,
                        useBlockFaceCulling, null, (commands, ignored, ignoredPass) -> null);
                storage.storeCachedMultiDrawBatch(cacheParams, cached);
            }

            if (!(cached.getBatch() instanceof VulkanMultiDrawBatch) || cached.getBatch().isEmpty()) {
                continue;
            }

            VulkanMultiDrawBatch batch = (VulkanMultiDrawBatch) cached.getBatch();

            if (this.readbackSlots == 0) {
                this.readbackOffset = (long) batch.baseVertex(0) * this.commonVertexStride;
                this.readbackRegion = resources;
            }
            long uniformOffset = writeUniforms(region, pass, camera);
            pipeline.bindDescriptors(this.commandBuffer, uniformOffset);
            pipeline.pushMatrices(this.commandBuffer, vulkanProjection(matrices.projection()), matrices.modelView());

            bindVertexBuffer(stack, resources);
            if (!indexBound) {
                bindIndexBuffer(stack, this.sharedIndexBuffers.get(primitiveType));
                indexBound = true;
            }

            if (this.lastDrawCommands == 0 && command0Logged < 2) {
                command0Logged++;
                StringBuilder colors = new StringBuilder("drawn section colours:");
                byte[] sections = renderList.getSectionsWithGeometry();
                for (int i = 0; i < Math.min(4, sections.length); i++) {
                    int local = sections[i] & 0xFF;
                    colors.append(" #").append(local).append("=0x")
                            .append(Integer.toHexString(resources.sectionFirstColor[local]));
                }
                record(colors.toString());
                command0Logged++;
                record("firstDraw region=" + region.getId() + " pass=" + pass.name()
                        + " stride=" + this.commonVertexStride
                        + " vertexArenaBytes=" + resources.vertexArena.deviceAllocatedMemoryL()
                        + " baseVertex=" + batch.baseVertex(0) + " elements=" + batch.elementCount(0)
                        + " indexBytes=" + batch.indexByteOffset(0)
                        + " batchSize=" + batch.size()
                        + " sharedIndexPrimitives=" + this.sharedIndexPrimitives.get(primitiveType));
            }
            for (int command = 0; command < batch.size(); command++) {
                long byteOffset = batch.indexByteOffset(command);
                int firstIndex;
                if (byteOffset == 0) {
                    firstIndex = 0;
                } else if (resources.indexArena != null) {
                    bindIndexBuffer(stack, resources.indexArena.buffer());
                    firstIndex = (int) (byteOffset / 4);
                } else {
                    continue;
                }
                vkCmdDrawIndexed(this.commandBuffer, batch.elementCount(command), 1, firstIndex,
                        batch.baseVertex(command), 0);
                this.lastDrawCommands++;
            }

            this.lastSectionsDrawn += renderList.getSectionsWithGeometryCount();

            if (resources.indexArena != null) {
                // The next region may use the shared index buffer again.
                bindIndexBuffer(stack, this.sharedIndexBuffers.get(primitiveType));
            }
        }

        if (this.pendingImmediate != null && !this.pendingImmediate.isEmpty()) {
            drawPendingImmediate();
        }

        if (this.uiTestEnabled && pass.isSorted()) {
            drawUiTestQuad(primitiveType);
        }

        vkCmdEndRenderPass(this.commandBuffer);
        if (timestampSlot >= 0) {
            vkCmdWriteTimestamp(this.commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, this.timestampPool,
                    (timestampSlot * 2) + 1);
            this.timestampPasses.put(timestampSlot, pass);
            this.timestampSlots++;
        }
        // The debug readback copies a buffer, which is only legal outside a render pass instance.
        if (!this.readbackDone && this.readbackSlots < 8 && this.readbackRegion != null) {
            recordReadback(this.readbackRegion, this.readbackOffset, this.readbackSlots);
            this.readbackSlots++;
            this.readbackPending = true;
        }
        this.frameTarget.recordReleaseBarriers(this.commandBuffer);
    }

    /**
     * {@code M * P} where {@code M} maps clip z from [-1, 1] to [0, 1] and leaves x, y and w alone. The Y axis is
     * handled by the negative-height viewport instead, so the OpenGL matrices and culling stay untouched.
     */
    private Matrix4fc vulkanProjection(Matrix4fc projection) {
        this.vulkanProjection.set(projection);
        // z: OpenGL clip depth [-1, 1] to Vulkan [0, 1]  ->  row 2 becomes 0.5 * (row 2 + row 3)
        float c0z = 0.5f * (projection.m02() + projection.m03());
        float c1z = 0.5f * (projection.m12() + projection.m13());
        float c2z = 0.5f * (projection.m22() + projection.m23());
        float c3z = 0.5f * (projection.m32() + projection.m33());
        // No Y flip: the shared colour image already carries the orientation OpenGL expects, so flipping here (or
        // through a negative-height viewport) rendered the terrain mirror-imaged. Culling compensates instead: the
        // pipeline uses VK_FRONT_FACE_COUNTER_CLOCKWISE for the same reason.
        this.vulkanProjection.m02(c0z);
        this.vulkanProjection.m12(c1z);
        this.vulkanProjection.m22(c2z);
        this.vulkanProjection.m32(c3z);
        return this.vulkanProjection;
    }

    /**
     * The framebuffer is created here rather than in the frame target because compatibility is defined by the
     * render pass the pipeline built, and one framebuffer serves every pass that is compatible with it.
     */
    private long ensureFramebuffer(long renderPass) {
        if (this.framebuffer != 0 && this.framebufferRenderPass == renderPass) {
            return this.framebuffer;
        }
        destroyFramebuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.mallocLong(2);
            attachments.put(0, this.frameTarget.colorView());
            attachments.put(1, this.frameTarget.depthView());
            org.lwjgl.vulkan.VkFramebufferCreateInfo create = org.lwjgl.vulkan.VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .pAttachments(attachments)
                    .width(this.targetWidth)
                    .height(this.targetHeight)
                    .layers(1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateFramebuffer(this.context.device(), create, null, handle), "vkCreateFramebuffer");
            this.framebuffer = handle.get(0);
            this.framebufferRenderPass = renderPass;
            return this.framebuffer;
        }
    }

    private void destroyFramebuffer() {
        if (this.framebuffer != 0) {
            vkDestroyFramebuffer(this.context.device(), this.framebuffer, null);
            this.framebuffer = 0;
            this.framebufferRenderPass = 0;
        }
    }

    private void bindVertexBuffer(MemoryStack stack, RegionResources resources) {
        LongBuffer buffers = stack.mallocLong(1);
        buffers.put(0, resources.vertexArena.buffer().buffer());
        LongBuffer offsets = stack.mallocLong(1);
        offsets.put(0, 0L);
        vkCmdBindVertexBuffers(this.commandBuffer, 0, buffers, offsets);
    }

    private void bindIndexBuffer(MemoryStack stack, VulkanBuffer buffer) {
        vkCmdBindIndexBuffer(this.commandBuffer, buffer.buffer(), 0L, VK_INDEX_TYPE_UINT32);
    }

    private void setViewportAndScissor(MemoryStack stack) {
        org.lwjgl.vulkan.VkViewport.Buffer viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
        // Positive height: see vulkanProjection() for why the Vulkan convention is used unchanged.
        viewport.get(0).x(0.0f).y(0.0f).width(this.targetWidth).height(this.targetHeight)
                .minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(this.commandBuffer, 0, viewport);
        org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
        scissor.get(0).offset().set(0, 0);
        scissor.get(0).extent().set(this.targetWidth, this.targetHeight);
        vkCmdSetScissor(this.commandBuffer, 0, scissor);
    }

    /** Writes one dynamic uniform block and returns its byte offset. */
    private long writeUniforms(RenderRegion region, TerrainRenderPass pass, CameraTransform camera) {
        long offset = this.uniformWriteCursor;
        this.uniformWriteCursor += this.uniformStride;
        if (this.uniformWriteCursor > this.uniforms.size()) {
            // This must not happen: render() reserves an upper bound before recording, and growth replaces the
            // buffer that descriptors recorded earlier in this pass already point at. Reported loudly rather than
            // growing silently, so a missed reservation shows up as a defect instead of as wrong terrain.
            record("uniform buffer grew during recording: " + this.uniformWriteCursor + " > " + this.uniforms.size()
                    + " (descriptors bound earlier in this pass may be stale)");
            this.uniforms.ensureCapacity(this.uniformWriteCursor);
            this.uniformBlocks = (int) (this.uniforms.size() / this.uniformStride);
        }

        ByteBuffer block = this.uniforms.mapped();
        int base = (int) offset;

        block.putFloat(base + 0, (region.getOriginX() - camera.intX) - camera.fracX);
        block.putFloat(base + 4, (region.getOriginY() - camera.intY) - camera.fracY);
        block.putFloat(base + 8, (region.getOriginZ() - camera.intZ) - camera.fracZ);
        block.putFloat(base + 12, 0.0f);

        float[] fogColor = this.fogService != null ? this.fogService.getFogColor() : null;
        if (!uniformsLogged) {
            uniformsLogged = true;
            record("uniforms: fogColor=" + java.util.Arrays.toString(fogColor)
                    + " start=" + (this.fogService != null ? this.fogService.getFogStart() : 0.0f)
                    + " end=" + (this.fogService != null ? this.fogService.getFogEnd() : 0.0f)
                    + " density=" + (this.fogService != null ? this.fogService.getFogDensity() : 0.0f)
                    + " shape=" + (this.fogService != null ? this.fogService.getFogShapeIndex() : -1)
                    + " glFogMode=" + fogMode() + " cutoff=" + (this.fogService != null ? this.fogService.getFogCutoff() : 0.0f)
                    + " regionOffset=(" + ((region.getOriginX() - camera.intX) - camera.fracX) + ","
                    + ((region.getOriginY() - camera.intY) - camera.fracY) + ","
                    + ((region.getOriginZ() - camera.intZ) - camera.fracZ) + ")");
        }
        block.putFloat(base + 16, fogColor != null && fogColor.length > 0 ? fogColor[0] : 0.0f);
        block.putFloat(base + 20, fogColor != null && fogColor.length > 1 ? fogColor[1] : 0.0f);
        block.putFloat(base + 24, fogColor != null && fogColor.length > 2 ? fogColor[2] : 0.0f);
        block.putFloat(base + 28, fogColor != null && fogColor.length > 3 ? fogColor[3] : 0.0f);

        block.putFloat(base + 32, this.fogService != null ? this.fogService.getFogDensity() : 0.0f);
        block.putFloat(base + 36, this.fogService != null ? this.fogService.getFogStart() : 0.0f);
        block.putFloat(base + 40, this.fogService != null ? this.fogService.getFogEnd() : 0.0f);
        // Additive atlas LOD shift. The per-material bias already travels in the vertex, so parity needs 0 here.
        block.putFloat(base + 44, 0.0f);
        block.putInt(base + 48, fogMode());
        block.putInt(base + 52, this.fogService != null ? this.fogService.getFogShapeIndex() : FogService.FOG_SHAPE_SPHERICAL);
        block.putInt(base + 56, pass.supportsFragmentDiscard() ? 1 : 0);
        // Chunk fade is a separate feature that the Vulkan path does not port yet; 0 keeps the GL default look.
        block.putInt(base + 60, 0);
        block.putFloat(base + 64, 0.0f);
        block.putFloat(base + 68, 0.0f);
        block.putFloat(base + 72, 0.0f);
        block.putFloat(base + 76, 0.0f);

        return offset;
    }

    /**
     * The shader's fog mode selector: 0 = none, 1 = exp2, 2 = exp, 3 = smooth/linear. Derived from the GL fog
     * state, which is what the OpenGL backend's {@code GLStateManagerFogService} reads as well.
     */
    private static int fogMode() {
        if (!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_FOG)) {
            return 0;
        }
        int mode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE);
        if (mode == org.lwjgl.opengl.GL11.GL_EXP2) {
            return 1;
        }
        if (mode == org.lwjgl.opengl.GL11.GL_EXP) {
            return 2;
        }
        return 3;
    }

    private static float[] regionTranslation(RenderRegion region, CameraTransform camera) {
        return new float[] {
                (region.getOriginX() - camera.intX) - camera.fracX,
                (region.getOriginY() - camera.intY) - camera.fracY,
                (region.getOriginZ() - camera.intZ) - camera.fracZ,
        };
    }

    private VulkanTerrainPipeline pipelineFor(TerrainRenderPass pass) {
        VulkanTerrainPipeline pipeline = this.pipelines.get(pass);
        if (pipeline != null) {
            return pipeline;
        }
        try {
            pipeline = VulkanTerrainPipeline.create(this.context, pass, !pass.isSorted());
            this.pipelines.put(pass, pipeline);
            record("pipeline for " + pass.name() + " created [" + pipeline.vertexInputSummary() + "]");
            if (pipeline.limitsWarning() != null) {
                record(pipeline.limitsWarning());
            }
            return pipeline;
        } catch (RuntimeException | LinkageError error) {
            record("pipeline for " + pass.name() + " failed: " + describe(error));
            this.pipelines.put(pass, null);
            return null;
        }
    }

    // ------------------------------------------------------------------ frame target

    /**
     * S2 probe: builds the surface and swapchain for the window GLFW currently owns, reports what the surface
     * advertises, and presents one cleared image. GL still presents this window in the hybrid build, so this runs
     * once and only when {@code -Dceltium.vulkan.swapchainProbe=true} is set.
     */
    private void runSwapchainProbe() {
        if (this.swapchainProbeDone) {
            return;
        }
        this.swapchainProbeDone = true;
        String step = "window handle";
        try {
            long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            java.util.List<String> reasons = new java.util.ArrayList<String>();
            step = "surface and swapchain creation";
            this.swapchain = VulkanSwapchain.create(this.context, window, 1280, 720, reasons);
            if (this.swapchain == null) {
                record("swapchain probe: unavailable at " + step + ": " + reasons);
                return;
            }
            record(this.swapchain.describe());
            step = "clear and present";
            record(this.swapchain.clearAndPresent(0.2f, 0.4f, 0.8f, 1.0f));
        } catch (RuntimeException | LinkageError error) {
            // The failing step and the deepest frame inside this module are what make an NPE diagnosable here: the
            // probe runs once, so a bare message would leave the next attempt guessing.
            StackTraceElement[] trace = error.getStackTrace();
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < Math.min(4, trace.length); i++) {
                if (i > 0) {
                    where.append(" <- ");
                }
                where.append(trace[i].getClassName().substring(trace[i].getClassName().lastIndexOf('.') + 1))
                        .append(':').append(trace[i].getLineNumber());
            }
            String arguments = this.swapchain == null ? "" : " args[" + this.swapchain.argumentSummary() + "]";
            record("swapchain probe failed at " + step + ": " + describe(error) + " [" + where + "]" + arguments);
        }
    }

    private boolean ensureFrameTarget() {
        int width = currentDrawableWidth();
        int height = currentDrawableHeight();
        if (width <= 0 || height <= 0) {
            record("drawable size unavailable");
            return false;
        }
        if (this.frameTarget != null && this.frameTarget.matches(width, height)) {
            return true;
        }
        destroyFramebuffer();
        if (this.frameTarget != null) {
            this.frameTarget.delete();
            this.frameTarget = null;
        }
        try {
            this.frameTarget = VulkanFrameTarget.create(this.context.instance(), this.context.physicalDevice(),
                    this.context.device(), this.context.queue(), this.context.queueFamilyIndex(), width, height);
            this.targetWidth = width;
            this.targetHeight = height;
            return true;
        } catch (RuntimeException | LinkageError error) {
            record("frame target " + width + "x" + height + " failed: " + describe(error));
            return false;
        }
    }

    private static int currentDrawableWidth() {
        int[] viewport = currentViewport();
        return Math.max(0, viewport[2]);
    }

    private static int currentDrawableHeight() {
        int[] viewport = currentViewport();
        return Math.max(0, viewport[3]);
    }

    private static int[] currentViewport() {
        IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(4);
        org.lwjgl.opengl.GL11C.glGetIntegerv(org.lwjgl.opengl.GL11C.GL_VIEWPORT, viewport);
        return new int[] { viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3) };
    }

    private void refreshTextures() {
        int atlasId = boundTextureId(0);
        int lightmapId = boundTextureId(1);

        if (atlasId != 0 && (this.atlasTexture == null || atlasId != this.atlasGlId || this.atlasRefreshCountdown <= 0)) {
            try {
                if (this.atlasTexture == null) {
                    int[] size = textureSize(atlasId);
                    this.atlasTexture = VulkanGlTexture.create(this.context, size[0], size[1], true);
                }
                this.atlasTexture.uploadFromGlTexture(atlasId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                int[] atlasSize = textureSize(atlasId);
                record("block atlas: gl texture " + atlasId + " " + atlasSize[0] + "x" + atlasSize[1]);
                this.atlasGlId = atlasId;
                this.atlasRefreshCountdown = 400;
            } catch (RuntimeException | LinkageError error) {
                record("block atlas upload failed: " + describe(error));
                this.atlasRefreshCountdown = 400;
            }
        }
        this.atlasRefreshCountdown--;

        if (lightmapId != 0 && (this.lightmapTexture == null || lightmapId != this.lightmapGlId || this.lightmapRefreshCountdown <= 0)) {
            try {
                if (this.lightmapTexture == null) {
                    int[] size = textureSize(lightmapId);
                    this.lightmapTexture = VulkanGlTexture.create(this.context, size[0], size[1], false);
                }
                this.lightmapTexture.uploadFromGlTexture(lightmapId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                int[] lightmapSize = textureSize(lightmapId);
                record("lightmap: gl texture " + lightmapId + " " + lightmapSize[0] + "x" + lightmapSize[1]);
                this.lightmapGlId = lightmapId;
                // The light map changes every frame (torch flicker, sun angle, weather), and it is 16x16, so it is
                // mirrored every frame instead of on a cadence: a stale copy shows up as a small uniform colour
                // shift against the OpenGL render.
                this.lightmapRefreshCountdown = 1;
            } catch (RuntimeException | LinkageError error) {
                record("lightmap upload failed: " + describe(error));
                this.lightmapRefreshCountdown = 1;
            }
        }
        this.lightmapRefreshCountdown--;
    }

    private static int boundTextureId(int unit) {
        int previous = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE);
        org.lwjgl.opengl.GL13C.glActiveTexture(org.lwjgl.opengl.GL13C.GL_TEXTURE0 + unit);
        int texture = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
        org.lwjgl.opengl.GL13C.glActiveTexture(previous);
        return texture;
    }

    private static int[] textureSize(int texture) {
        int width = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(texture, 0, org.lwjgl.opengl.GL11C.GL_TEXTURE_WIDTH);
        int height = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(texture, 0, org.lwjgl.opengl.GL11C.GL_TEXTURE_HEIGHT);
        return new int[] { Math.max(1, width), Math.max(1, height) };
    }

    // ------------------------------------------------------------------ command resources

    private void createCommandResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(this.context.queueFamilyIndex());
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateCommandPool(this.context.device(), poolInfo, null, handle), "vkCreateCommandPool");
            this.commandPool = handle.get(0);

            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                    .commandPool(this.commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            org.lwjgl.PointerBuffer buffers = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(this.context.device(), allocate, buffers), "vkAllocateCommandBuffers");
            this.commandBuffer = new VkCommandBuffer(buffers.get(0), this.context.device());

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack).sType$Default();
            check(vkCreateFence(this.context.device(), fenceInfo, null, handle), "vkCreateFence");
            this.fence = handle.get(0);

            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
            // timestampValidBits belongs to the queue family, not to the device limits, so it is read for the
            // family this backend submits on.
            IntBuffer familyCount = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    this.context.physicalDevice(), familyCount, null);
            org.lwjgl.vulkan.VkQueueFamilyProperties.Buffer families =
                    org.lwjgl.vulkan.VkQueueFamilyProperties.calloc((int) familyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    this.context.physicalDevice(), familyCount, families);
            int family = Math.min(this.context.queueFamilyIndex(), families.capacity() - 1);
            long timestampValidBits = Integer.toUnsignedLong(families.get(Math.max(0, family)).timestampValidBits());
            this.timestampPeriod = properties.limits().timestampPeriod();
            // Writing timestamps on a queue that cannot record them, or with a zero period, produces meaningless
            // numbers; the overlay then keeps its "?? ms" instead of reporting fiction.
            this.timestampsUsable = timestampValidBits > 0L && this.timestampPeriod > 0.0f;
            org.lwjgl.vulkan.VkQueryPoolCreateInfo queryPool = org.lwjgl.vulkan.VkQueryPoolCreateInfo.calloc(stack)
                    .sType$Default().queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(TIMESTAMP_SLOTS);
            if (this.timestampsUsable) {
                check(vkCreateQueryPool(this.context.device(), queryPool, null, handle), "vkCreateQueryPool");
                this.timestampPool = handle.get(0);

            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(this);

            if (Boolean.getBoolean(VulkanContext.SWAPCHAIN_PROPERTY)) {
                runSwapchainProbe();
            }
            }
        }
    }

    private void destroyCommandResources() {
        if (this.timestampPool != 0) {
            vkDestroyQueryPool(this.context.device(), this.timestampPool, null);
            this.timestampPool = 0;
        }
        if (this.fence != 0) {
            vkDestroyFence(this.context.device(), this.fence, null);
            this.fence = 0;
        }
        if (this.commandPool != 0) {
            vkDestroyCommandPool(this.context.device(), this.commandPool, null);
            this.commandPool = 0;
            this.commandBuffer = null;
        }
    }

    private void beginCommands() {
        // The frame uses one command buffer, so the previous submission has to finish before it is reset: resetting
        // or re-recording a pending buffer is invalid and, on some drivers, makes the GPU execute a half-rewritten
        // command stream - which shows up as the wrong vertex data reaching the shader.
        if (this.submissionPending) {
            waitForIdle();
            this.submissionPending = false;
            // The previous frame's timestamps are complete now, which is the only moment they can be read reliably.
            updateTimings();
        }
        vkResetCommandBuffer(this.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(this.commandBuffer, begin), "vkBeginCommandBuffer");
            // Queries must be reset before they are written again, and a fresh command buffer is exactly where that
            // belongs; without it validation reports every timestamp write as using an unavailable query.
            if (this.timestampPool != 0) {
                vkCmdResetQueryPool(this.commandBuffer, this.timestampPool, 0, TIMESTAMP_SLOTS);
            }
        }
        this.uniformWriteCursor = 0;
    }

    /**
     * Draws the immediate-mode batches accumulated since the last flush.
     * <p>
     * Timing note: the batches arrive while the frame is already past its terrain passes (entities, particles, the
     * GUI), and the bridge only owns the shared images inside a terrain pass. They are therefore drawn with the
     * <em>next</em> frame's first terrain pass, using that pass's own projection and model view. For content that
     * does not move between frames this is invisible; for moving content it is one frame of latency, and the proper
     * fix is a frame the renderer owns outright (stage 7 of the docs/plan.md stages). It is documented rather than
     * hidden.
     */
    private void drawPendingImmediate() {
        try {
            VulkanTerrainPipeline terrain = pipelineFor(this.lastUiPass);
            if (terrain == null) {
                return;
            }
            if (this.immediatePipeline == null) {
                this.immediatePipeline = VulkanImmediatePipeline.create(this.context, terrain.renderPass());
            }
            ByteBuffer push = MemoryUtil.memAlloc(128);
            try {
                // joml writes column-major into the buffer at the given offset, which is the layout the push
                // constant block declares.
                // The mirrored matrices, not the terrain pass's: immediate geometry is drawn in the coordinate
                // system Minecraft had when it built the batch, which is what the GlStateManager Mixin mirrors.
                pushMatrixInto(this.pendingProjection, push, 0);
                pushMatrixInto(this.pendingModelView, push, 64);
                this.immediatePipeline.draw(this.commandBuffer, this.pendingImmediate, push);
                this.immediateFlushes++;
            } finally {
                MemoryUtil.memFree(push);
            }
            this.pendingImmediate.begin(VulkanDynamicMesh.Layout.positionTexLightColor());
            this.pendingImmediateIndices = 0;
            this.pendingGroups = 0;
        } catch (RuntimeException | LinkageError error) {
            record("immediate flush failed: " + describe(error));
        }
    }

    /**
     * Records the non-terrain test geometry: one textured quad in normalised device coordinates, drawn with the UI
     * pipeline into the terrain colour image. The projection and model-view are identities, so the quad lands in a
     * fixed corner regardless of the camera, which makes the pixel check in the harness unambiguous.
     */
    private void drawUiTestQuad(ChunkPrimitiveType primitiveType) {
        try {
            VulkanTerrainPipeline terrain = pipelineFor(this.lastUiPass);
            if (terrain == null) {
                return;
            }
            if (this.uiPipeline == null) {
                this.uiPipeline = VulkanUiPipeline.create(this.context, terrain.renderPass());
                this.uiMesh = new VulkanDynamicMesh(this.context);
            }
            if (this.atlasTexture == null) {
                return;
            }
            this.uiMesh.begin(VulkanDynamicMesh.Layout.positionTexLightColor());
            float u = 1.0f / 32.0f;
            float v = 1.0f / 32.0f;
            addQuadVertex(-0.95f, 0.95f, 0.0f, 0.0f);
            addQuadVertex(-0.55f, 0.95f, u, 0.0f);
            addQuadVertex(-0.55f, 0.55f, u, v);
            addQuadVertex(-0.95f, 0.55f, 0.0f, v);
            this.uiMesh.index(0);
            this.uiMesh.index(1);
            this.uiMesh.index(2);
            this.uiMesh.index(0);
            this.uiMesh.index(2);
            this.uiMesh.index(3);
            this.uiPipeline.bindTexture(this.atlasTexture);
            ByteBuffer push = MemoryUtil.memAlloc(128);
            try {
                // Identity projection and model-view: the vertices above are already in clip space.
                putIdentity(push, 0);
                putIdentity(push, 64);
                this.uiPipeline.draw(this.commandBuffer, this.uiMesh, push);
            } finally {
                MemoryUtil.memFree(push);
            }
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("ui test quad failed: " + describe(error));
        }
    }

    /**
     * Writes {@code result = clipDepthRemap * projection}, the same z remap the terrain projection goes through, so a
     * matrix mirrored from OpenGL can be used unchanged for depth in Vulkan.
     */
    private static void remapClipDepth(float[] projection, float[] result) {
        float[] remap = new float[16];
        remap[0] = 1.0f;
        remap[5] = 1.0f;
        remap[10] = 0.5f;
        remap[14] = 0.5f;
        remap[15] = 1.0f;
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += remap[k * 4 + row] * projection[column * 4 + k];
                }
                result[column * 4 + row] = sum;
            }
        }
    }

    /**
     * Reads the matrices OpenGL is currently using. Called on the render thread while the GL context is current, so
     * the values are the ones Minecraft itself would draw with - no class needs to cooperate, which matters because
     * the projection is installed by a utility class outside the transforming loader.
     */
    private void captureGlMatrices() {
        try {
            FloatBuffer projection = MemoryUtil.memAllocFloat(16);
            FloatBuffer modelView = MemoryUtil.memAllocFloat(16);
            try {
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX, projection);
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, modelView);
                projection.get(this.glProjection);
                modelView.get(this.glModelView);
            } finally {
                MemoryUtil.memFree(projection);
                MemoryUtil.memFree(modelView);
            }
        } catch (RuntimeException | LinkageError error) {
            record("could not read the OpenGL matrices for an immediate batch: " + describe(error));
        }
    }

    private static void pushMatrixInto(float[] matrix, ByteBuffer target, int offset) {
        for (int i = 0; i < 16; i++) {
            target.putFloat(offset + (i * 4), matrix[i]);
        }
    }

    private void addQuadVertex(float x, float y, float u, float v) {
        this.uiMesh.nextVertex();
        this.uiMesh.putPosition(x, y, 0.0f);
        this.uiMesh.putUv(u, v);
        this.uiMesh.putLight(0, 240);
        this.uiMesh.putColor(255, 255, 255, 255);
    }

    private static void putIdentity(ByteBuffer target, int offset) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                target.putFloat(offset + ((column * 4) + row) * 4, column == row ? 1.0f : 0.0f);
            }
        }
    }

    private void submitCommands() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(vkEndCommandBuffer(this.commandBuffer), "vkEndCommandBuffer");
            vkResetFences(this.context.device(), this.fence);
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default();
            LongBuffer waitSemaphores = stack.mallocLong(1);
            waitSemaphores.put(0, this.frameTarget.waitSemaphore());
            IntBuffer waitStages = stack.mallocInt(1);
            waitStages.put(0, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            LongBuffer signalSemaphores = stack.mallocLong(1);
            signalSemaphores.put(0, this.frameTarget.doneSemaphore());
            org.lwjgl.PointerBuffer commandBuffers = stack.mallocPointer(1);
            commandBuffers.put(0, this.commandBuffer.address());
            // LWJGL derives the semaphore counts from the buffers, so only the pointers are set here.
            submit.pWaitSemaphores(waitSemaphores).pWaitDstStageMask(waitStages)
                    .pCommandBuffers(commandBuffers)
                    .pSignalSemaphores(signalSemaphores);
            check(vkQueueSubmit(this.context.queue(), submit, this.fence), "vkQueueSubmit");
            this.submissionPending = true;
        }
    }

    private void waitForIdle() {
        this.submissionPending = false;
        if (this.fence != 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer fences = stack.mallocLong(1);
                fences.put(0, this.fence);
                vkWaitForFences(this.context.device(), fences, true, Long.MAX_VALUE);
            }
        }
        vkQueueWaitIdle(this.context.queue());
    }

    // ------------------------------------------------------------------ shared resources

    /**
     * The shared index buffer holds the repeating primitive pattern the OpenGL backend also uses for unsorted
     * passes. The pattern is generated by the pass's own {@link ChunkPrimitiveType}, so the Vulkan and OpenGL
     * paths cannot drift apart on element order or count.
     */
    private void ensureSharedIndexBuffer(ChunkPrimitiveType primitiveType) {
        int maxVertices = 0;
        for (RegionResources resources : this.regionResources.values()) {
            long bytes = resources.vertexArena.deviceAllocatedMemoryL();
            maxVertices = Math.max(maxVertices, (int) (bytes / this.commonVertexStride));
        }
        int primitives = (maxVertices / primitiveType.getVerticesPerPrimitive()) + 1;
        Integer existing = this.sharedIndexPrimitives.get(primitiveType);
        if (this.sharedIndexBuffers.containsKey(primitiveType) && existing != null && existing >= primitives) {
            return;
        }
        int capacity = Math.max(primitives, 1024);
        int bytes = primitiveType.getIndexBufferSize(capacity);
        ByteBuffer data = MemoryUtil.memAlloc(bytes);
        try {
            primitiveType.generateSimpleIndexBuffer(data, capacity);
            VulkanBuffer previous = this.sharedIndexBuffers.remove(primitiveType);
            if (previous != null) {
                previous.delete();
            }
            VulkanBuffer buffer = new VulkanBuffer(this.context, bytes, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            buffer.write(0, MemoryUtil.memAddress(data), bytes);
            this.sharedIndexBuffers.put(primitiveType, buffer);
            this.sharedIndexPrimitives.put(primitiveType, capacity);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private void ensureUniformCapacity(int regions) {
        int blocks = Math.max(16, regions + 1);
        long required = blocks * this.uniformStride;
        if (this.uniforms != null && this.uniforms.size() >= required) {
            return;
        }
        if (this.uniforms == null) {
            this.uniforms = new VulkanUniformBuffer(this.context, required);
        } else {
            this.uniforms.ensureCapacity(required);
        }
        this.uniformBlocks = blocks;
    }

    private int alignmentFromDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties = org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
            long alignment = properties.limits().minUniformBufferOffsetAlignment();
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, alignment));
        }
    }

    private static long align(long value, int alignment) {
        long mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    /**
     * Debug aid: copies the first vertices of the buffer the first draw uses into host-visible memory and decodes
     * them. It answers the one question the CPU-side logs cannot: what the GPU actually holds.
     */
    private void ensureReadback() {
        if (this.readbackBuffer != 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkBufferCreateInfo create = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(8 * 64).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(this.context.device(), create, null, handle), "vkCreateBuffer(readback)");
            this.readbackBuffer = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), this.readbackBuffer, requirements);
            org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(this.context.physicalDevice(), properties);
            int type = -1;
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                int flags = properties.memoryTypes(i).propertyFlags();
                if ((requirements.memoryTypeBits() & (1 << i)) != 0
                        && (flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0
                        && (flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0) {
                    type = i;
                    break;
                }
            }
            if (type < 0) {
                throw new IllegalStateException("No host-visible coherent memory type for the debug readback");
            }
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size()).memoryTypeIndex(type);
            check(vkAllocateMemory(this.context.device(), allocate, null, handle), "vkAllocateMemory(readback)");
            this.readbackMemory = handle.get(0);
            check(vkBindBufferMemory(this.context.device(), this.readbackBuffer, this.readbackMemory, 0),
                    "vkBindBufferMemory(readback)");
            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(this.context.device(), this.readbackMemory, 0, 8 * 64, 0, pointer), "vkMapMemory(readback)");
            this.readbackMapped = pointer.get(0);
        }
    }

    private void recordReadback(RegionResources resources, long sourceOffset, int slot) {
        ensureReadback();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkBufferCopy.Buffer region = org.lwjgl.vulkan.VkBufferCopy.calloc(1, stack);
            region.get(0).srcOffset(sourceOffset).dstOffset((long) slot * 64L).size(64L);
            vkCmdCopyBuffer(this.commandBuffer, resources.vertexArena.buffer().buffer(), this.readbackBuffer, region);
            org.lwjgl.vulkan.VkMemoryBarrier.Buffer barrier = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_HOST_READ_BIT);
            vkCmdPipelineBarrier(this.commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0,
                    barrier, null, null);
        }
        this.readbackPending = true;
    }

    private void readBackAndLog() {
        this.readbackPending = false;
        this.readbackDone = true;
        waitForIdle();
        ByteBuffer view = MemoryUtil.memByteBuffer(this.readbackMapped, Math.max(64, this.readbackSlots * 64));
        view.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        StringBuilder line = new StringBuilder("gpu draws:");
        for (int slot = 0; slot < this.readbackSlots; slot++) {
            int base = slot * 64;
            line.append(" [").append(slot).append("] colour=0x")
                    .append(Integer.toHexString(view.getInt(base + 12)))
                    .append(" light=0x").append(Integer.toHexString(view.getInt(base + 24)))
                    .append(" pos=(").append(view.getFloat(base)).append(',')
                    .append(view.getFloat(base + 4)).append(',').append(view.getFloat(base + 8)).append(')');
        }
        record(line.toString());
    }

    private void record(String diagnostic) {
        this.diagnostics.add(diagnostic);
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }

    /** Per-region device memory. */
    private static final class RegionResources {
        final VulkanArena vertexArena;
        final int[] sectionFirstColor = new int[RenderRegion.REGION_SIZE];
        VulkanArena indexArena;

        RegionResources(VulkanContext context, int vertexStride) {
            this.vertexArena = new VulkanArena(context, 512 * 1024, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, vertexStride);
        }

        int bufferCount() {
            return 1 + (this.indexArena != null ? 1 : 0);
        }

        void delete() {
            this.vertexArena.delete();
            if (this.indexArena != null) {
                this.indexArena.delete();
                this.indexArena = null;
            }
        }
    }

    private static final class PendingMesh {
        final int sectionIndex;
        final TerrainRenderPass pass;
        final BuiltSectionMeshParts parts;
        final ChunkSortOutput.SortedMesh sorted;
        final int[] sectionFirstColor;

        PendingMesh(int sectionIndex, TerrainRenderPass pass, BuiltSectionMeshParts parts,
                    ChunkSortOutput.SortedMesh sorted, int[] sectionFirstColor) {
            this.sectionIndex = sectionIndex;
            this.pass = pass;
            this.parts = parts;
            this.sorted = sorted;
            this.sectionFirstColor = sectionFirstColor;
        }
    }
}
