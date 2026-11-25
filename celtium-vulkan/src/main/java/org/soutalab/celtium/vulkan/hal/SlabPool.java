package org.soutalab.celtium.vulkan.hal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A pure ledger for fixed-size slabs, sub-allocated in page-sized units. This class knows nothing about Vulkan: it
 * tracks which page runs of which slab are live, and the owning allocator (see {@link SlabAllocator}) mirrors the
 * slab count with real {@code VkDeviceMemory}. Keeping the ledger free of native calls is what makes the placement
 * rules testable without a device.
 * <p>
 * <b>Growth:</b> a slab is created lazily, the first time no existing slab can serve a request, and growth stops at
 * {@code maxSlabs}. A full pool is never grown by copying: copying a slab would move every offset inside it, and an
 * offset is exactly what a buffer or image was bound with, so a move would silently invalidate bindings that are
 * already recorded. The only growth is a new, empty slab appended after the existing ones; existing allocations
 * never change slab or offset.
 * <p>
 * <b>Placement:</b> first fit within a size class. A request whose paged size reaches a sixteenth of a slab
 * is <i>large</i>: slabs are scanned newest first, so a large
 * request lands in the tail slabs where long free runs survive, instead of failing on a hole that a long-lived
 * allocation pins open in an old slab. Every smaller request is <i>small</i>: slabs are scanned in creation order,
 * so small requests fill the earliest holes - including the holes small short-lived churn leaves behind when it is
 * collected, which are exactly small-request-sized. Within a slab, free runs are scanned in offset order for both
 * classes. First fit is not the tightest packing, but it is deterministic - the same sequence of requests always
 * produces the same layout. Requests are rounded up to whole pages and placed at an offset that satisfies the
 * requested alignment; the alignment does not have to be a power of two, and the padding skipped to reach it stays
 * a free run.
 * <p>
 * <b>Release:</b> {@link #free(Allocation)} returns a range at once, merging it with any neighbouring free runs.
 * {@link #freeDeferred(Allocation, long)} only records the release against a timeline value: the range stays counted
 * as used and cannot be handed out again until {@link #collectCompleted(long)} is told the timeline has passed that
 * value. The deferral exists because the CPU learns about a release before the GPU has finished reading the memory -
 * the submission that last used the range may still be in flight - so reuse has to wait for the completion signal,
 * not for the call that requested the release.
 * <p>
 * <b>Oversized requests:</b> a request larger than one slab is refused with {@code null} and does not consume a slab.
 * Serving it is the owning allocator's decision (it creates a dedicated slab of the request's own size), because this
 * ledger's slabs are all the same size by construction.
 * <p>
 * <b>Threading:</b> not thread-safe. The owning allocator is used on the render thread only, and this ledger inherits
 * that confinement.
 */
public final class SlabPool {

    /** The page size callers are expected to use unless they have a reason to differ: 4096 bytes. */
    public static final int DEFAULT_PAGE_SIZE_BYTES = 4096;

    /**
     * The size-class divisor: a request whose paged size is at least one {@code LARGE_REQUEST_SLAB_DIVISOR}th of a
     * slab counts as large and is placed tail-first (see the class documentation). A divisor rather than an absolute
     * byte threshold keeps the class boundary proportional when the slab size changes.
     */
    private static final int LARGE_REQUEST_SLAB_DIVISOR = 16;

    private final long slabSizeBytes;
    private final int pageSizeBytes;
    private final int maxSlabs;

    /** Paged size at or above which a request counts as large; never below one page. */
    private final long largeRequestThresholdBytes;

    /** Free runs per slab, keyed by start offset; the value is the run length in bytes. */
    private final List<TreeMap<Long, Long>> freeRunsBySlab = new ArrayList<TreeMap<Long, Long>>();

    /** Every allocation whose bytes are still counted as used, including ones pending a deferred release. */
    private final Set<Allocation> live = new HashSet<Allocation>();

    /** Allocations released through {@link #freeDeferred}, in the order they were released. */
    private final List<DeferredRelease> deferredReleases = new ArrayList<DeferredRelease>();

    /** Membership view of {@link #deferredReleases}, for rejecting a second release of the same allocation. */
    private final Set<Allocation> deferredPending = new HashSet<Allocation>();

    private long usedBytes;

    /**
     * Creates an empty pool. No slab exists until the first allocation needs one.
     *
     * @param slabSizeBytes size of every slab; must be a positive multiple of {@code pageSizeBytes}, because a slab
     *                      that ended mid-page would strand a partial page no allocation could ever claim
     * @param pageSizeBytes the allocation granularity; every allocation occupies a whole number of pages
     * @param maxSlabs      the slab cap; once reached, an allocation that fits nowhere returns {@code null} instead
     *                      of growing, so the cap is a hard bound on the memory the owner will hold
     * @throws IllegalArgumentException when a size is not positive, {@code maxSlabs} is below 1, or the slab size is
     *                                  not a multiple of the page size
     */
    public SlabPool(long slabSizeBytes, int pageSizeBytes, int maxSlabs) {
        if (slabSizeBytes <= 0L) {
            throw new IllegalArgumentException("slabSizeBytes must be positive, was " + slabSizeBytes);
        }
        if (pageSizeBytes <= 0) {
            throw new IllegalArgumentException("pageSizeBytes must be positive, was " + pageSizeBytes);
        }
        if (maxSlabs < 1) {
            throw new IllegalArgumentException("maxSlabs must be at least 1, was " + maxSlabs);
        }
        if (slabSizeBytes % pageSizeBytes != 0L) {
            throw new IllegalArgumentException("slabSizeBytes (" + slabSizeBytes + ") must be a multiple of "
                    + "pageSizeBytes (" + pageSizeBytes + ")");
        }
        this.slabSizeBytes = slabSizeBytes;
        this.pageSizeBytes = pageSizeBytes;
        this.maxSlabs = maxSlabs;
        this.largeRequestThresholdBytes = Math.max((long) pageSizeBytes,
                slabSizeBytes / LARGE_REQUEST_SLAB_DIVISOR);
    }

    /** Size of one slab in bytes; every slab in the pool has exactly this size. */
    public long slabSize() {
        return this.slabSizeBytes;
    }

    /** The allocation granularity in bytes. */
    public int pageSize() {
        return this.pageSizeBytes;
    }

    /** The slab cap this pool was created with. */
    public int maxSlabs() {
        return this.maxSlabs;
    }

    /**
     * Allocates {@code sizeBytes} rounded up to whole pages, at an offset that is a multiple of {@code alignment}.
     * <p>
     * Slabs are searched in the request's size-class order (large: newest first; small: creation order) and free
     * runs in offset order; the first run that can hold the aligned request wins. When no slab fits and the pool
     * is below its cap, exactly one new slab is appended and the search is retried against it - appended, never
     * copied into, so the offsets of everything already allocated stay put.
     *
     * @param sizeBytes the requested size; must be positive
     * @param alignment the required offset alignment; must be at least 1 and need not be a power of two
     * @return the allocation, or {@code null} when the request is larger than one slab or when every slab is too
     *         fragmented and the slab cap has been reached
     */
    public Allocation allocate(long sizeBytes, long alignment) {
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("sizeBytes must be positive, was " + sizeBytes);
        }
        if (alignment < 1L) {
            throw new IllegalArgumentException("alignment must be at least 1, was " + alignment);
        }
        long pagedSize = alignUp(sizeBytes, this.pageSizeBytes);
        if (pagedSize > this.slabSizeBytes) {
            // Larger than any slab this ledger will ever hold; the owner decides how to serve it.
            return null;
        }
        boolean large = pagedSize >= this.largeRequestThresholdBytes;
        Allocation allocation = tryAllocate(pagedSize, sizeBytes, alignment, large);
        if (allocation != null) {
            return allocation;
        }
        if (this.freeRunsBySlab.size() >= this.maxSlabs) {
            return null;
        }
        addSlab();
        return tryAllocate(pagedSize, sizeBytes, alignment, large);
    }

    /**
     * Returns an allocation's range to its slab at once, merging it with any free runs it touches.
     * <p>
     * The merge is what keeps {@link #largestFreeRun()} honest: without it, a slab released page by page would look
     * permanently fragmented even though it is entirely free.
     *
     * @throws IllegalStateException when the allocation was already freed, is already pending a deferred release, or
     *                               was never issued by this pool - a double free here would hand the same bytes to
     *                               two owners, so it is rejected rather than absorbed
     */
    public void free(Allocation allocation) {
        requireLive(allocation, "free");
        if (this.deferredPending.contains(allocation)) {
            throw new IllegalStateException("This allocation is already pending a deferred release; freeing it "
                    + "again would return the same range twice.");
        }
        releaseRange(allocation);
    }

    /**
     * Records an allocation for release once the timeline reaches {@code timelineValue}. Until
     * {@link #collectCompleted(long)} collects it, the range stays counted as used and is never handed out again:
     * the GPU may still be reading it, and the timeline value is the only evidence that it has stopped.
     *
     * @throws IllegalStateException when the allocation was already freed or is already pending a deferred release
     */
    public void freeDeferred(Allocation allocation, long timelineValue) {
        requireLive(allocation, "freeDeferred");
        if (!this.deferredPending.add(allocation)) {
            throw new IllegalStateException("This allocation is already pending a deferred release.");
        }
        this.deferredReleases.add(new DeferredRelease(allocation, timelineValue));
    }

    /**
     * Actually releases every deferred allocation whose timeline value is at or below
     * {@code completedTimelineValue}, merging each range back into its slab's free runs.
     *
     * @return how many deferred allocations this call released
     */
    public int collectCompleted(long completedTimelineValue) {
        int released = 0;
        Iterator<DeferredRelease> iterator = this.deferredReleases.iterator();
        while (iterator.hasNext()) {
            DeferredRelease pending = iterator.next();
            if (pending.timelineValue <= completedTimelineValue) {
                iterator.remove();
                this.deferredPending.remove(pending.allocation);
                releaseRange(pending.allocation);
                released++;
            }
        }
        return released;
    }

    /** Number of slabs the pool currently holds; grows lazily up to {@link #maxSlabs()}. */
    public int slabCount() {
        return this.freeRunsBySlab.size();
    }

    /** Bytes held by live allocations, including allocations pending a deferred release. */
    public long usedBytes() {
        return this.usedBytes;
    }

    /** Bytes not held by any live allocation, across all current slabs. */
    public long freeBytes() {
        return this.slabSizeBytes * this.freeRunsBySlab.size() - this.usedBytes;
    }

    /** Length of the longest single free run in any slab; 0 when there are no slabs or no free bytes. */
    public long largestFreeRun() {
        long largest = 0L;
        for (TreeMap<Long, Long> runs : this.freeRunsBySlab) {
            for (Long length : runs.values()) {
                if (length.longValue() > largest) {
                    largest = length.longValue();
                }
            }
        }
        return largest;
    }

    /** Allocations released through {@link #freeDeferred} that no {@link #collectCompleted} call has collected yet. */
    public int pendingDeferredCount() {
        return this.deferredReleases.size();
    }

    /** Allocations not yet returned to a free run, including ones pending a deferred release. */
    public int liveAllocationCount() {
        return this.live.size();
    }

    private Allocation tryAllocate(long pagedSize, long requestedSize, long alignment, boolean large) {
        int slabCount = this.freeRunsBySlab.size();
        for (int index = 0; index < slabCount; index++) {
            // Size-class scan direction: large requests walk the slabs newest first, small ones oldest first.
            int slab = large ? slabCount - 1 - index : index;
            TreeMap<Long, Long> runs = this.freeRunsBySlab.get(slab);
            // Snapshot the entries: the winning run is split in place below, and a TreeMap cannot be
            // structurally modified while its entry set is being iterated.
            for (Map.Entry<Long, Long> run : new ArrayList<Map.Entry<Long, Long>>(runs.entrySet())) {
                long runStart = run.getKey().longValue();
                long runEnd = runStart + run.getValue().longValue();
                long offset = alignUp(runStart, alignment);
                if (offset < runStart || offset + pagedSize > runEnd) {
                    continue;
                }
                runs.remove(run.getKey());
                if (offset > runStart) {
                    // The alignment padding stays free; a later, smaller request may still fit in it.
                    runs.put(Long.valueOf(runStart), Long.valueOf(offset - runStart));
                }
                long end = offset + pagedSize;
                if (end < runEnd) {
                    runs.put(Long.valueOf(end), Long.valueOf(runEnd - end));
                }
                Allocation allocation = new Allocation(slab, offset, pagedSize, requestedSize);
                this.live.add(allocation);
                this.usedBytes += pagedSize;
                return allocation;
            }
        }
        return null;
    }

    private void addSlab() {
        TreeMap<Long, Long> runs = new TreeMap<Long, Long>();
        runs.put(Long.valueOf(0L), Long.valueOf(this.slabSizeBytes));
        this.freeRunsBySlab.add(runs);
    }

    private void releaseRange(Allocation allocation) {
        this.live.remove(allocation);
        this.usedBytes -= allocation.size();
        insertFreeRun(allocation.slab(), allocation.offset(), allocation.size());
    }

    private void insertFreeRun(int slab, long offset, long length) {
        TreeMap<Long, Long> runs = this.freeRunsBySlab.get(slab);
        long start = offset;
        long end = offset + length;
        Map.Entry<Long, Long> before = runs.floorEntry(Long.valueOf(start));
        if (before != null && before.getKey().longValue() + before.getValue().longValue() == start) {
            start = before.getKey().longValue();
            runs.remove(before.getKey());
        }
        Long after = runs.get(Long.valueOf(end));
        if (after != null) {
            end += after.longValue();
            runs.remove(Long.valueOf(end - after.longValue()));
        }
        runs.put(Long.valueOf(start), Long.valueOf(end - start));
    }

    private void requireLive(Allocation allocation, String operation) {
        if (allocation == null) {
            throw new IllegalArgumentException("allocation must not be null");
        }
        if (!this.live.contains(allocation)) {
            throw new IllegalStateException("Cannot " + operation + " an allocation that is not live in this pool "
                    + "(already freed, or issued by another pool).");
        }
    }

    private static long alignUp(long value, long alignment) {
        long remainder = value % alignment;
        return remainder == 0L ? value : value + (alignment - remainder);
    }

    /** One recorded deferred release: the allocation and the timeline value that gates its actual return. */
    private static final class DeferredRelease {
        final Allocation allocation;
        final long timelineValue;

        DeferredRelease(Allocation allocation, long timelineValue) {
            this.allocation = allocation;
            this.timelineValue = timelineValue;
        }
    }

    /**
     * One allocation inside a slab. Identity is meaningful: two allocations are never {@code equals}, because the
     * pool tracks live allocations by the exact object it issued, which is what makes a double free detectable.
     */
    public static final class Allocation {
        private final int slab;
        private final long offset;
        private final long size;
        private final long requestedSize;

        Allocation(int slab, long offset, long size, long requestedSize) {
            this.slab = slab;
            this.offset = offset;
            this.size = size;
            this.requestedSize = requestedSize;
        }

        /** Index of the slab holding this allocation, in slab creation order. */
        public int slab() {
            return this.slab;
        }

        /** Byte offset of the allocation inside its slab; a multiple of the requested alignment. */
        public long offset() {
            return this.offset;
        }

        /** Size in bytes after rounding up to whole pages; the range actually reserved. */
        public long size() {
            return this.size;
        }

        /** Size in bytes the caller asked for, before page rounding. */
        public long requestedSize() {
            return this.requestedSize;
        }
    }
}
