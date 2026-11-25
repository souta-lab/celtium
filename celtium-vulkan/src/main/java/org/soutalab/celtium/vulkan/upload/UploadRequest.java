package org.soutalab.celtium.vulkan.upload;

/**
 * A single pending GPU transfer queued for P0 ({@code UploadBatch}).
 *
 * <p>Covers both buffer-to-buffer copies (terrain geometry slabs, indirect/instance buffers) and
 * buffer-to-image sub-rectangle copies (block/item atlases, animated sprites for E9/F2).
 */
public final class UploadRequest {

    public enum Kind {
        BUFFER_COPY,
        IMAGE_SUBRECT_COPY
    }

    public enum Priority {
        /** Player-adjacent chunk edits, active HUD/atlas uploads — drained first. */
        IMMEDIATE,
        /** Outer-ring flight streaming and non-urgent mip/atlas updates — drained after IMMEDIATE. */
        BACKGROUND
    }

    private final Kind kind;
    private final Priority priority;
    private final long stagingOffset;
    private final long dstHandle;
    private final long dstOffset;
    private final int mipLevel;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int sizeBytes;
    private final long coalescingKey;

    private UploadRequest(Kind kind,
                          Priority priority,
                          long stagingOffset,
                          long dstHandle,
                          long dstOffset,
                          int mipLevel,
                          int x,
                          int y,
                          int width,
                          int height,
                          int sizeBytes,
                          long coalescingKey) {
        this.kind = kind;
        this.priority = priority;
        this.stagingOffset = stagingOffset;
        this.dstHandle = dstHandle;
        this.dstOffset = dstOffset;
        this.mipLevel = mipLevel;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
        this.coalescingKey = coalescingKey;
    }

    public static UploadRequest bufferCopy(Priority priority,
                                           long stagingOffset,
                                           long dstBufferHandle,
                                           long dstOffset,
                                           int sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive: " + sizeBytes);
        }
        if (stagingOffset < 0 || dstOffset < 0) {
            throw new IllegalArgumentException("offsets must be non-negative");
        }
        return new UploadRequest(
                Kind.BUFFER_COPY,
                priority,
                stagingOffset,
                dstBufferHandle,
                dstOffset,
                0,
                0,
                0,
                0,
                0,
                sizeBytes,
                0L);
    }

    public static UploadRequest imageSubrectCopy(Priority priority,
                                                 long stagingOffset,
                                                 long dstImageHandle,
                                                 int mipLevel,
                                                 int x,
                                                 int y,
                                                 int width,
                                                 int height,
                                                 int sizeBytes) {
        if (sizeBytes <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image subrect dimensions and sizeBytes must be positive");
        }
        if (stagingOffset < 0 || mipLevel < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("image subrect coordinates and stagingOffset must be non-negative");
        }
        long key = computeImageCoalescingKey(dstImageHandle, mipLevel, x, y, width, height);
        return new UploadRequest(
                Kind.IMAGE_SUBRECT_COPY,
                priority,
                stagingOffset,
                dstImageHandle,
                0L,
                mipLevel,
                x,
                y,
                width,
                height,
                sizeBytes,
                key);
    }

    /**
     * Exact equality check for animated-sprite sub-rect coalescing (guards against hash collisions).
     */
    public boolean sameImageSubrect(UploadRequest other) {
        return this.kind == Kind.IMAGE_SUBRECT_COPY
                && other.kind == Kind.IMAGE_SUBRECT_COPY
                && this.dstHandle == other.dstHandle
                && this.mipLevel == other.mipLevel
                && this.x == other.x
                && this.y == other.y
                && this.width == other.width
                && this.height == other.height;
    }

    private static long computeImageCoalescingKey(long dstImageHandle,
                                                  int mipLevel,
                                                  int x,
                                                  int y,
                                                  int width,
                                                  int height) {
        long h = dstImageHandle * 0x9E3779B97F4A7C15L;
        h ^= ((long) (mipLevel & 0xFF) << 56)
                | ((long) (x & 0x3FFF) << 42)
                | ((long) (y & 0x3FFF) << 28)
                | ((long) (width & 0x3FFF) << 14)
                | (height & 0x3FFFL);
        return h;
    }

    public Kind kind() {
        return kind;
    }

    public Priority priority() {
        return priority;
    }

    public long stagingOffset() {
        return stagingOffset;
    }

    public long dstHandle() {
        return dstHandle;
    }

    public long dstOffset() {
        return dstOffset;
    }

    public int mipLevel() {
        return mipLevel;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int sizeBytes() {
        return sizeBytes;
    }

    long coalescingKey() {
        return coalescingKey;
    }
}
