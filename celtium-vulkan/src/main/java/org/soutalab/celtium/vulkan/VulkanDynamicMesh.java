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

    private final VulkanContext context;
    private VulkanHostBuffer vertices;
    private VulkanHostBuffer indices;

    private Layout layout = Layout.positionTexLightColor();
    private long vertexBytes;
    private long indexBytes;
    private int vertexCount;
    private int indexCount;

    public VulkanDynamicMesh(VulkanContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context;
        this.vertices = new VulkanHostBuffer(context, INITIAL_VERTEX_BYTES, org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        this.indices = new VulkanHostBuffer(context, INITIAL_INDEX_BYTES, org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
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
        return this.vertices.buffer();
    }

    public long indexBuffer() {
        return this.indices.buffer();
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
        if (this.vertices != null) {
            this.vertices.delete();
            this.vertices = null;
        }
        if (this.indices != null) {
            this.indices.delete();
            this.indices = null;
        }
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
