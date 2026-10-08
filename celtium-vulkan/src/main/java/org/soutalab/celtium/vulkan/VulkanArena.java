package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.data.MeshBufferSegment;

import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * Segment allocator over one device-local {@link VulkanBuffer}, mirroring the OpenGL arena
 * ({@code org.embeddedt.embeddium.impl.gl.arena.GlBufferArena}) that the shared mesh metadata already works with.
 * <p>
 * The free list is a doubly-linked list of byte ranges ordered by offset, exactly like the OpenGL one: allocation
 * uses <b>best fit</b> (an exact-size free range wins outright, otherwise the smallest free range that is large
 * enough), splitting from the end of the chosen range, and freeing marks the range free and merges it with the
 * adjacent free ranges so the list never contains two neighbours that are both free. {@code used} counts the bytes
 * handed out and {@code capacity} mirrors the buffer size, so {@link #deviceUsedMemoryL()} and
 * {@link #deviceAllocatedMemoryL()} report the same numbers the OpenGL arena reports.
 * <p>
 * Every size and offset is a byte count and a multiple of 4, because Vulkan requires 4-byte alignment for buffer
 * copies; {@link #allocate(int)} rounds a request up, which is what keeps {@link Segment#getOffset()} aligned.
 * <p>
 * <b>Growth contract.</b> When nothing fits, the arena grows its buffer (at least doubling, never below 512 KiB,
 * and always enough that the appended tail alone is a contiguous range of the requested size) and retries.
 * {@link VulkanBuffer#ensureCapacity(long)} copies the existing bytes into the larger buffer, so
 * <b>every live segment keeps its offset and length</b>: no segment is invalidated, no data has to be re-uploaded and
 * the byte ranges never move. What does change is the {@code VkBuffer} handle, so derived state that cached the
 * handle has to be rebuilt by the caller from the existing mod metadata - that is what
 * {@link #upload(Collection)}'s return value reports, and comparing {@link VulkanBuffer#buffer()} (the handle, not
 * the {@link VulkanBuffer} object, which survives a growth) across {@link #allocate(int)} calls gives the same
 * signal.
 * <p>
 * <b>Threading:</b> every method must be called on the render thread that owns the {@link VulkanContext}, and
 * {@link #delete()} must happen while the context is still alive.
 */
public final class VulkanArena {
    /** Growth never asks for less than this, so small arenas do not grow on every other allocation. */
    private static final int MINIMUM_GROWTH_BYTES = 512 * 1024;

    private final VulkanContext context;
    private VulkanBuffer buffer;
    private Segment head;

    /**
     * Bytes per allocated element. Segment offsets and lengths are reported in these units, matching the OpenGL
     * arena: the mod's per-section metadata multiplies an offset by the vertex count per arena element, so both
     * backends must express ranges the same way.
     */
    private final int stride;
    private int capacity;
    private int used;
    private boolean deleted;

    /**
     * Creates the arena together with its first buffer.
     *
     * @param context              owning context; the arena must be deleted before the context is
     * @param initialCapacityBytes initial buffer size, rounded up to a multiple of 4; must be positive
     * @param usageFlags           {@code VK_BUFFER_USAGE_*} flags for the buffer; the transfer flags are added by
     *                             {@link VulkanBuffer}
     */
    public VulkanArena(VulkanContext context, int initialCapacityBytes, int usageFlags) {
        this(context, initialCapacityBytes, usageFlags, 1);
    }

    public VulkanArena(VulkanContext context, int initialCapacityBytes, int usageFlags, int stride) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        if (stride <= 0) {
            throw new IllegalArgumentException("Stride must be positive, was " + stride);
        }
        if (initialCapacityBytes <= 0) {
            throw new IllegalArgumentException("initialCapacityBytes must be positive, was " + initialCapacityBytes);
        }
        this.context = context;
        this.stride = stride;
        this.capacity = alignUp4Int(initialCapacityBytes);
        this.buffer = new VulkanBuffer(context, this.capacity, usageFlags);
        this.head = new Segment(this, 0, this.capacity);
        this.head.free = true;
    }

    /**
     * Allocates a range of at least {@code byteSize} bytes. The returned range is a multiple of 4 bytes long and
     * starts at an offset that is a multiple of 4.
     * <p>
     * The free list is searched first (best fit). When no free range fits, the buffer is grown and the search is
     * repeated; growth preserves the contents and does not move existing segments. Render thread only.
     *
     * @throws IllegalStateException when the arena is deleted or the buffer cannot be grown
     */
    public Segment allocate(int byteSize) {
        requireLive();
        if (byteSize <= 0) {
            throw new IllegalArgumentException("byteSize must be positive, was " + byteSize);
        }
        // Ranges are rounded to whole stride elements so that a later offset stays aligned for the vertex format,
        // exactly like the OpenGL arena, whose allocation unit is the stride.
        int needed = alignUp4Int(byteSize);
        if (this.stride > 1) {
            needed = ((needed + this.stride - 1) / this.stride) * this.stride;
        }
        Segment free = findFree(needed);
        if (free == null) {
            grow(needed);
            free = findFree(needed);
            if (free == null) {
                // Unreachable: growth always appends a free tail of at least `needed` bytes.
                throw new IllegalStateException("The Vulkan arena grew to " + this.capacity
                        + " bytes but still has no contiguous free range of " + needed + " bytes.");
            }
        }
        Segment result;
        if (free.length == needed) {
            free.free = false;
            result = free;
        } else {
            // Split from the end so the chosen range keeps its offset and the two ranges stay adjacent.
            result = new Segment(this, free.end() - needed, needed);
            result.prev = free;
            result.next = free.next;
            if (result.next != null) {
                result.next.prev = result;
            }
            free.length -= needed;
            free.next = result;
        }
        this.used += result.length;
        return result;
    }

    /**
     * Uploads one native byte range into arena memory at a byte offset.
     * Render thread only.
     */
    public void write(int offsetBytes, long sourceAddress, int length) {
        requireLive();
        requireRange(offsetBytes, length);
        if (length == 0) {
            return;
        }
        this.buffer.write(offsetBytes, sourceAddress, length);
    }

    /**
     * Uploads the remaining bytes of a heap buffer into arena memory at a byte offset. The source buffer's position
     * is left untouched. Render thread only.
     */
    public void write(int offsetBytes, ByteBuffer source) {
        requireLive();
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        int length = source.remaining();
        requireRange(offsetBytes, length);
        if (length == 0) {
            return;
        }
        this.context.uploadToBuffer(this.buffer.buffer(), offsetBytes, source);
    }

    /**
     * Writes every upload into the arena.
     * <p>
     * An upload with a null {@link Upload#segment} asks the arena to place it: the arena allocates a segment, stores
     * it in the upload and may have to grow the buffer to do so. Render thread only.
     *
     * @return {@code true} when the underlying buffer was recreated (grown) during this call, so the caller has to
     *         refresh state that cached the old {@code VkBuffer} handle; the segment offsets themselves stay valid
     */
    public boolean upload(Collection<Upload> uploads) {
        requireLive();
        if (uploads == null) {
            throw new IllegalArgumentException("uploads must not be null");
        }
        // Recording the handle before any work is what detects a reallocation. The VulkanBuffer object survives a
        // growth (it recreates the VkBuffer inside itself), so the handle has to be compared, not the object.
        VulkanBuffer beforeBuffer = this.buffer;
        long beforeHandle = beforeBuffer.buffer();
        for (Upload upload : uploads) {
            if (upload == null) {
                throw new IllegalArgumentException("uploads must not contain null elements");
            }
            if (upload.length < 0) {
                throw new IllegalArgumentException("An upload length must not be negative, was " + upload.length);
            }
            if (upload.length == 0) {
                continue;
            }
            Segment segment = upload.segment;
            if (segment == null) {
                segment = allocate(upload.length);
                upload.segment = segment;
            } else if (segment.arena != this) {
                throw new IllegalArgumentException("An upload segment belongs to a different arena.");
            } else if (upload.length > segment.length) {
                throw new IllegalArgumentException("An upload of " + upload.length
                        + " bytes does not fit its segment of " + segment.length + " bytes.");
            }
            this.buffer.write(segment.offset, upload.sourceAddress, upload.length);
        }
        return this.buffer != beforeBuffer || this.buffer.buffer() != beforeHandle;
    }

    /** Bytes currently handed out to live segments. */
    public long deviceUsedMemoryL() {
        return this.used;
    }

    /** Bytes currently allocated on the GPU, which is the current buffer size. */
    public long deviceAllocatedMemoryL() {
        return this.capacity;
    }

    /** Number of buffers the arena owns: one while it is alive, zero after {@link #delete()}. */
    public int bufferCount() {
        return this.deleted ? 0 : 1;
    }

    /** Whether no live segment is allocated. */
    public boolean isEmpty() {
        return this.used <= 0;
    }

    /**
     * The buffer the segments live in. Its handle changes on every growth, so callers that cache it must compare it
     * across allocations.
     *
     * @throws IllegalStateException when the arena has been deleted
     */
    public VulkanBuffer buffer() {
        requireLive();
        return this.buffer;
    }

    /**
     * Deletes the buffer and releases every segment. Idempotent. Segments that are still held by the caller become
     * inert: their {@link Segment#delete()} is a no-op and their offsets must not be used any more. Render thread
     * only.
     */
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        Segment segment = this.head;
        while (segment != null) {
            Segment next = segment.next;
            segment.free = true;
            segment.offset = 0;
            segment.length = 0;
            segment.prev = null;
            segment.next = null;
            segment = next;
        }
        this.head = null;
        this.used = 0;
        this.capacity = 0;
        this.buffer.delete();
    }

    /**
     * Releases a segment back to the free list and merges it with its free neighbours. Called by
     * {@link Segment#delete()}; calling it twice for the same segment is a no-op.
     */
    void free(Segment segment) {
        if (segment.arena != this) {
            throw new IllegalArgumentException("The segment belongs to a different arena.");
        }
        if (this.deleted || segment.free) {
            return;
        }
        segment.free = true;
        this.used -= segment.length;
        if (segment.next != null && segment.next.free) {
            segment.merge(segment.next);
        }
        if (segment.prev != null && segment.prev.free) {
            segment.prev.merge(segment);
        }
    }

    /**
     * Grows the buffer so it can hold the used bytes plus {@code needed} more.
     * <p>
     * Doubling is the normal case. The {@code capacity + needed} term matters when a single request is larger than
     * the whole current buffer: doubling alone would then leave a tail that is too small and no free range large
     * enough, so growth would never succeed.
     */
    private void grow(int needed) {
        long target = Math.max(Math.max((long) this.capacity * 2L, (long) this.capacity + needed),
                MINIMUM_GROWTH_BYTES);
        if (target > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("The Vulkan arena cannot grow beyond " + Integer.MAX_VALUE + " bytes (used "
                    + this.used + " bytes, requested " + needed + " bytes).");
        }
        int oldCapacity = this.capacity;
        this.buffer.ensureCapacity(target);
        // Read the real size back instead of assuming the request: VulkanBuffer owns the exact allocation size.
        this.capacity = (int) this.buffer.size();
        appendFreeTail(oldCapacity, this.capacity - oldCapacity);
    }

    /**
     * Appends the newly added tail of the buffer to the free list. The old layout covers
     * {@code [0, oldCapacity)} contiguously, so the tail starts exactly at {@code oldCapacity} and live segments
     * keep their offsets.
     */
    private void appendFreeTail(int offset, int length) {
        if (length <= 0) {
            return;
        }
        Segment tail = new Segment(this, offset, length);
        tail.free = true;
        if (this.head == null) {
            this.head = tail;
            return;
        }
        Segment last = this.head;
        while (last.next != null) {
            last = last.next;
        }
        last.next = tail;
        tail.prev = last;
        if (last.free) {
            last.merge(tail);
        }
    }

    /** Best-fit search with an exact-size short circuit; returns null when no free range is large enough. */
    private Segment findFree(int size) {
        Segment entry = this.head;
        Segment best = null;
        while (entry != null) {
            if (entry.free) {
                if (entry.length == size) {
                    return entry;
                }
                if (entry.length > size && (best == null || best.length > entry.length)) {
                    best = entry;
                }
            }
            entry = entry.next;
        }
        return best;
    }

    private void requireRange(int offsetBytes, int length) {
        if (offsetBytes < 0) {
            throw new IllegalArgumentException("offsetBytes must not be negative, was " + offsetBytes);
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative, was " + length);
        }
        if (length > this.capacity - offsetBytes) {
            throw new IllegalArgumentException("A write of " + length + " bytes at offset " + offsetBytes
                    + " exceeds the arena capacity of " + this.capacity + " bytes.");
        }
    }

    private void requireLive() {
        if (this.deleted) {
            throw new IllegalStateException("This VulkanArena has been deleted.");
        }
    }

    private static int alignUp4Int(int value) {
        long aligned = VulkanContext.alignUp4(value);
        if (aligned > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A size of " + value + " bytes is too large for a Vulkan arena.");
        }
        return (int) aligned;
    }

    /**
     * One write into the arena: the range it goes into, the native bytes it copies and how many.
     * <p>
     * A null {@link #segment} asks the arena to place the upload itself, which is the only case in which
     * {@link #upload(Collection)} can grow the buffer; the arena then fills the field in with the segment it
     * allocated so the caller can store it in the shared mesh metadata.
     */
    public static final class Upload {
        /** Target range, or null to let the arena allocate one. */
        public Segment segment;
        /** Native address the bytes are copied from. */
        public final long sourceAddress;
        /** Number of bytes to copy. */
        public final int length;

        public Upload(Segment segment, long sourceAddress, int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length must not be negative, was " + length);
            }
            if (length > 0 && sourceAddress == 0L) {
                throw new IllegalArgumentException("sourceAddress must be a non-null native address");
            }
            this.segment = segment;
            this.sourceAddress = sourceAddress;
            this.length = length;
        }
    }

    /**
     * A range of the arena's buffer, shared with the mod's mesh metadata through {@link MeshBufferSegment}. Offsets
     * and lengths are reported in {@link VulkanArena#stride} units, exactly like the OpenGL arena, because the
     * mod's metadata converts them into vertex and index positions with the same arithmetic for both backends. The
     * byte range never changes while the segment is alive - the arena grows by copying data in place.
     */
    public static final class Segment implements MeshBufferSegment {
        private final VulkanArena arena;
        private int offset;
        private int length;
        private boolean free;
        private Segment prev;
        private Segment next;

        private Segment(VulkanArena arena, int offset, int length) {
            this.arena = arena;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public int getOffset() {
            return this.offset / this.arena.stride;
        }

        @Override
        public int getLength() {
            return this.length / this.arena.stride;
        }

        /** First byte of this range. Used for writing into the buffer; the metadata uses {@link #getOffset()}. */
        public int byteOffset() {
            return this.offset;
        }

        /** Length of this range in bytes. */
        public int byteLength() {
            return this.length;
        }

        /** Returns the range to the arena. Idempotent, and a no-op once the arena has been deleted. */
        @Override
        public void delete() {
            this.arena.free(this);
        }

        /** Offset one past the last byte of this range. */
        private int end() {
            return this.offset + this.length;
        }

        /** Absorbs the following range, which must be free, into this one. */
        private void merge(Segment other) {
            this.length += other.length;
            this.next = other.next;
            if (this.next != null) {
                this.next.prev = this;
            }
        }

        @Override
        public String toString() {
            return "VulkanArena.Segment[offset=" + this.offset + ", length=" + this.length
                    + (this.free ? ", free]" : ", used]");
        }
    }
}
