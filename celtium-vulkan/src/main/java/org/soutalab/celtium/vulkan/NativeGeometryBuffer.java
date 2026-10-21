package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Retained vertex data owned by Minecraft's VertexBuffer API, not by a GL name or client-array decoder.
 * Upload copies the caller's remaining bytes once. Device caches are invalidated on replacement; their outstanding
 * draw leases keep the old GPU generation alive. CPU bytes survive a device/backend replacement and are freed on close.
 * All operations belong to the render thread.
 */
public final class NativeGeometryBuffer implements AutoCloseable {
    private static NativeGeometryBuffer bound;
    private static int boundId;
    private static final Map<Integer, NativeGeometryBuffer> LIVE = new java.util.HashMap<>();
    private final int id = RenderObjectIds.reserve(1);
    private int vertexCount;
    private boolean mapped;
    private final Map<Object, Runnable> invalidations = new IdentityHashMap<>();
    private ByteBuffer data;
    private int stride;
    private int formatId;
    private boolean closed;

    public NativeGeometryBuffer() {
        LIVE.put(this.id, this);
    }

    /** An opaque API identity; only a mod's raw GL adapter resolves it back to this resource. */
    public int id() {
        return this.id;
    }

    public static NativeGeometryBuffer find(int id) {
        return LIVE.get(id);
    }

    public void upload(ByteBuffer source, int stride, int formatId) {
        requireOpen();
        if (source == null || stride < 4 || stride > 256 || source.remaining() % stride != 0) {
            throw new IllegalArgumentException("vertex data must contain whole vertices of a valid stride");
        }
        replaceStorage(source, source.remaining());
        this.stride = stride;
        this.formatId = formatId;
        this.vertexCount = source.remaining() / stride;
    }

    /** Raw mod storage replacement keeps VertexBuffer's API draw count, just as a raw glBufferData would. */
    public void replaceStorage(ByteBuffer source, int bytes) {
        requireOpen();
        if (bytes < 0 || source != null && source.remaining() != bytes || this.mapped) {
            throw new IllegalArgumentException("invalid or mapped native buffer storage replacement");
        }
        ByteBuffer replacement = null;
        if (bytes != 0) {
            replacement = source == null ? MemoryUtil.memCalloc(bytes) : MemoryUtil.memAlloc(bytes);
            try {
                if (source != null) replacement.put(source.duplicate()).flip();
            } catch (RuntimeException | Error error) {
                MemoryUtil.memFree(replacement);
                throw error;
            }
        }
        try {
            invalidate();
        } catch (RuntimeException | Error error) {
            MemoryUtil.memFree(replacement);
            throw error;
        }
        MemoryUtil.memFree(this.data);
        this.data = replacement;
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    public int byteSize() {
        return this.data == null ? 0 : this.data.remaining();
    }

    /** A mod's byte-range update invalidates the cached GPU generation, never the meshes held by queued draw leases. */
    public void write(long offset, ByteBuffer source) {
        requireOpen();
        int bytes = source.remaining();
        if (this.mapped || offset < 0 || offset > byteSize() - (long) bytes) {
            throw new IllegalArgumentException("native buffer update outside storage or while mapped");
        }
        invalidate();
        if (bytes != 0) this.data.duplicate().position((int) offset).put(source.duplicate());
    }

    /** Transient raw-mod mapping only. Native API draws cannot consume a still-mapped buffer. */
    public long map(long offset, long length) {
        requireOpen();
        if (this.mapped || offset < 0 || length <= 0 || offset > byteSize() - length) return 0L;
        invalidate();
        this.mapped = true;
        return MemoryUtil.memAddress(this.data) + offset;
    }

    public boolean unmap() {
        requireOpen();
        if (!this.mapped) return false;
        this.mapped = false;
        invalidate();
        return true;
    }

    public int stride() {
        return this.stride;
    }

    public int formatId() {
        return this.formatId;
    }

    public long address() {
        requireOpen();
        if (this.mapped || (long) this.vertexCount * this.stride > byteSize()) {
            throw new IllegalStateException("native draw of a mapped or undersized vertex buffer");
        }
        return this.data == null ? 0L : MemoryUtil.memAddress(this.data);
    }

    /** Only the raw-GL mod adapter needs a client-array view. Native draws never decode this view. */
    public ByteBuffer compatibilityView() {
        requireOpen();
        return this.data == null ? null : this.data.asReadOnlyBuffer();
    }

    public void bind() {
        requireOpen();
        bound = this;
        boundId = this.id;
    }

    /** Raw-mod binds and save/restore use the same identity without forcing native calls through the GL adapter. */
    public static void bindId(int id) {
        boundId = id;
        bound = find(id);
    }

    public static int boundId() {
        return boundId;
    }

    public static NativeGeometryBuffer bound() {
        return bound;
    }

    /** A raw GL array-buffer bind supersedes a native API bind, including a bind to zero. */
    public static void unbind() {
        bound = null;
        boundId = 0;
    }

    void onInvalidate(Object consumer, Runnable action) {
        requireOpen();
        this.invalidations.put(consumer, action);
    }

    void detach(Object consumer) {
        this.invalidations.remove(consumer);
    }

    private void invalidate() {
        // Detach before invoking a consumer: callbacks may detach themselves as they drop the cache entry.
        Runnable[] actions = this.invalidations.values().toArray(new Runnable[0]);
        this.invalidations.clear();
        Throwable failure = null;
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }

    private void requireOpen() {
        if (this.closed) {
            throw new IllegalStateException("native vertex buffer is deleted");
        }
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        LIVE.remove(this.id);
        this.vertexCount = 0;
        if (bound == this) {
            unbind();
        }
        try {
            invalidate();
        } finally {
            MemoryUtil.memFree(this.data);
            this.data = null;
        }
    }
}
