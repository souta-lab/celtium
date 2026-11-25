package org.soutalab.celtium.vulkan;

/**
 * The offset policy of the shared texture staging pool: which slices of one fixed host-visible
 * staging space may be handed out, and when a handed-out slice may be handed out again.
 * <p>
 * This is the pure half of {@link TextureUploadService}'s staging pool (design
 * OVERHAUL-S3-REMAINDER-2026-11-26 &sect;3.2, item 1). It owns no Vulkan object and no memory;
 * the service's owner (at landing, {@code VulkanContext}) owns the backing allocation and asks
 * this class where a slice goes. Keeping the policy free of the device is what makes the pool's
 * structural claims testable without a GPU:
 * <ul>
 *   <li><b>Reuse is judged by the frame timeline, never by a fence per texture.</b> A slice that
 *   a submitted batch read is stamped with the timeline value that batch completes at. It becomes
 *   reusable only when {@link #releaseCompleted(long)} is told a completed value at or past the
 *   stamp. This replaces the per-texture fence waits {@code VulkanGlTexture} keeps today: the
 *   pool cannot hand the CPU bytes the GPU may still be reading, because a slice leaves the
 *   in-flight state only on the owner's completion evidence.</li>
 *   <li><b>Exhaustion is a value, not an event.</b> When no free run fits, {@link #allocate(long)}
 *   returns {@code -1}. The pool never grows, never waits and never wraps into an in-flight
 *   slice; the caller decides what exhaustion means (defer, await a timeline value, or fall
 *   back), exactly as the Stage 2 ring's allocator leaves that decision to its caller.</li>
 *   <li><b>Free runs coalesce.</b> Releasing merges adjacent free runs, so the fragmentation a
 *   first-fit policy could otherwise accumulate is bounded by the in-flight set: once everything
 *   in flight completes, the pool is one free run of the whole capacity again.</li>
 * </ul>
 * The backing this policy will run over at landing is the context's host-visible slab pool (the
 * S34 tree's form of {@code VulkanGlTexture}'s per-texture {@code stagingAllocation}, promoted
 * from per-texture to per-service). On the tree this preparation was written against, that slab
 * pool is not on {@code main} yet, so the binding is deliberately not expressed here: this class
 * speaks only in offsets within a capacity, and any backing that can map a byte range can serve.
 * <p>
 * Not thread-safe: the pool is written by the one thread recording uploads.
 */
public final class TextureStagingPool {

    /** A slice handed out and not yet submitted; its bytes are being written by the CPU. */
    private static final int RESERVED = 0;
    /** A slice a submitted batch reads; reusable only once its stamp has completed. */
    private static final int IN_FLIGHT = 1;
    /** A slice nobody reads; the only state {@link #allocate(long)} hands out. */
    private static final int FREE = 2;

    /** One contiguous run of the staging space. The segment list is kept sorted by offset. */
    private static final class Segment {
        long offset;
        long size;
        int state;
        /** The timeline value the reading batch completes at; meaningful only when IN_FLIGHT. */
        long stamp;

        Segment(long offset, long size, int state) {
            this.offset = offset;
            this.size = size;
            this.state = state;
        }
    }

    private final long capacity;
    private final long alignment;
    private final java.util.ArrayList<Segment> segments = new java.util.ArrayList<Segment>();

    /**
     * @param capacityBytes the staging space's fixed size; must be positive
     * @param alignment     every handed-out offset is a multiple of this; any positive value,
     *                      a power of two or not (the division form, as the Stage 2 ring states it)
     */
    public TextureStagingPool(long capacityBytes, long alignment) {
        if (capacityBytes <= 0L) {
            throw new IllegalArgumentException("staging pool capacity must be positive, was " + capacityBytes);
        }
        if (alignment < 1L) {
            throw new IllegalArgumentException("staging pool alignment must be positive, was " + alignment);
        }
        this.capacity = capacityBytes;
        this.alignment = alignment;
        this.segments.add(new Segment(0L, capacityBytes, FREE));
    }

    /** {@return the pool's fixed capacity in bytes}. */
    public long capacity() {
        return this.capacity;
    }

    /** {@return the alignment every handed-out offset satisfies}. */
    public long alignment() {
        return this.alignment;
    }

    /**
     * Hands out {@code size} bytes from a free run, at the first offset satisfying the alignment.
     * The slice is RESERVED until {@link #markSubmitted(long, long)} stamps it.
     *
     * @param size bytes wanted; must be positive and no larger than the capacity
     * @return the slice's offset from the pool's base, or {@code -1} when no free run fits
     */
    public long allocate(long size) {
        if (size <= 0L) {
            throw new IllegalArgumentException("staging allocation size must be positive, was " + size);
        }
        if (size > this.capacity) {
            throw new IllegalArgumentException("staging allocation of " + size
                    + " bytes exceeds the pool capacity of " + this.capacity + " bytes");
        }
        for (int i = 0; i < this.segments.size(); i++) {
            Segment segment = this.segments.get(i);
            if (segment.state != FREE) {
                continue;
            }
            long offset = alignUp(segment.offset, this.alignment);
            long prefix = offset - segment.offset;
            if (size > segment.size - prefix) {
                continue;
            }
            long tail = segment.size - prefix - size;
            if (prefix == 0L && tail == 0L) {
                segment.state = RESERVED;
                return offset;
            }
            Segment reserved = new Segment(offset, size, RESERVED);
            if (tail > 0L) {
                this.segments.add(i + 1, new Segment(offset + size, tail, FREE));
            }
            if (prefix > 0L) {
                segment.size = prefix;
                this.segments.add(i + 1, reserved);
            } else {
                this.segments.set(i, reserved);
            }
            return offset;
        }
        return -1L;
    }

    /**
     * Stamps the RESERVED slice at {@code offset} with the timeline value the batch that reads
     * it completes at. From here the slice is IN_FLIGHT and cannot be handed out again until
     * {@link #releaseCompleted(long)} retires the stamp.
     *
     * @throws IllegalStateException when no reserved slice starts at {@code offset}
     */
    public void markSubmitted(long offset, long timelineValue) {
        for (Segment segment : this.segments) {
            if (segment.offset == offset && segment.state == RESERVED) {
                segment.state = IN_FLIGHT;
                segment.stamp = timelineValue;
                return;
            }
        }
        throw new IllegalStateException("no reserved staging slice at offset " + offset);
    }

    /**
     * Retires every in-flight slice whose stamp is at or below {@code completedValue} and merges
     * the free runs that produces. The owner calls this with the frame timeline's completed
     * value; passing a value the device has not actually reached would hand the CPU bytes the
     * GPU is still reading, so the value must be device evidence, never an allocation counter.
     */
    public void releaseCompleted(long completedValue) {
        boolean freed = false;
        for (Segment segment : this.segments) {
            if (segment.state == IN_FLIGHT && segment.stamp <= completedValue) {
                segment.state = FREE;
                freed = true;
            }
        }
        if (freed) {
            coalesce();
        }
    }

    /** {@return the bytes currently reserved or in flight}. */
    public long usedBytes() {
        long used = 0L;
        for (Segment segment : this.segments) {
            if (segment.state != FREE) {
                used += segment.size;
            }
        }
        return used;
    }

    /** {@return the bytes in free runs, including alignment-unusable prefixes}. */
    public long freeBytes() {
        return this.capacity - usedBytes();
    }

    /** {@return the size of the largest single free run}. */
    public long largestFreeRun() {
        long largest = 0L;
        for (Segment segment : this.segments) {
            if (segment.state == FREE && segment.size > largest) {
                largest = segment.size;
            }
        }
        return largest;
    }

    /**
     * {@return the smallest stamp among in-flight slices - the value whose completion frees the
     * oldest staging data - or {@code 0} when nothing is in flight}. A caller blocked on space
     * awaits this value on the timeline and then retries.
     */
    public long oldestInFlightValue() {
        long oldest = 0L;
        for (Segment segment : this.segments) {
            if (segment.state == IN_FLIGHT && (oldest == 0L || segment.stamp < oldest)) {
                oldest = segment.stamp;
            }
        }
        return oldest;
    }

    /** {@return whether any slice is reserved or in flight}. */
    public boolean hasOutstanding() {
        for (Segment segment : this.segments) {
            if (segment.state != FREE) {
                return true;
            }
        }
        return false;
    }

    private void coalesce() {
        for (int i = this.segments.size() - 2; i >= 0; i--) {
            Segment current = this.segments.get(i);
            Segment next = this.segments.get(i + 1);
            if (current.state == FREE && next.state == FREE) {
                current.size += next.size;
                this.segments.remove(i + 1);
            }
        }
    }

    /** Rounds {@code value} up to a multiple of {@code alignment}; any positive alignment. */
    private static long alignUp(long value, long alignment) {
        return (value + alignment - 1L) / alignment * alignment;
    }
}
