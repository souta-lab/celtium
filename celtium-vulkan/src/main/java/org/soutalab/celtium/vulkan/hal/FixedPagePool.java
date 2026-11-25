package org.soutalab.celtium.vulkan.hal;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A pure ledger for the fixed-page path: slabs divided into pages of exactly one staging-class size, where
 * every allocation occupies exactly one whole page. This class knows nothing about Vulkan, for the same reason
 * {@link SlabPool} does not: the placement rule stays testable without a device, and the owning allocator
 * ({@link SlabAllocator}) mirrors the slab count with real {@code VkDeviceMemory}.
 * <p>
 * <b>Why a separate pool:</b> the Phase 1 leg r8 crash (coord-migration ruling #8945 (A)) was a 65536-byte,
 * alignment-64 request refused at the slab cap with 151,552 bytes free in the host-visible pool but a largest
 * free run of only 28,672 bytes. Size-band segregation in {@link SlabPool} (r7 fix) did not prevent it: inside
 * a 4096-byte-page slab, any interleaving of live and freed ranges can strand every free run below the staging
 * size, and no placement order can undo fragmentation that has already happened. This pool removes the
 * mechanism instead of ordering around it: a page is exactly the staging size, a page is either wholly free or
 * wholly live, and a free page is therefore always a contiguous run of the full staging size. Fragmentation of
 * the kind that killed r8 is not unlikely here, it is unrepresentable.
 * <p>
 * <b>Scope:</b> only requests whose page-rounded size is exactly one page belong here. Smaller requests stay in
 * the ordinary pool (paying a whole staging page for a small allocation would waste memory and change the
 * ordinary pool's packing), and larger requests keep the ordinary and oversized paths. The owning allocator
 * falls back to the ordinary pool when this pool cannot serve, so the fixed path can only add capacity for its
 * size class, never remove it.
 * <p>
 * <b>Alignment:</b> every page offset is a multiple of the page size, so a request is served only when the page
 * size is a multiple of the requested alignment (65536 is a multiple of the staging alignment 64). Any other
 * alignment returns {@code null} and the caller falls back, rather than this pool silently handing out an
 * offset that does not satisfy the request.
 * <p>
 * <b>Growth and release:</b> as in {@link SlabPool}, slabs are appended lazily up to the cap and never copied,
 * and {@link #freeDeferred(Allocation, long)} keeps a page counted as used until
 * {@link #collectCompleted(long)} passes its timeline value, because the GPU may still be reading the staging
 * bytes the CPU has already finished with.
 * <p>
 * <b>Census:</b> slabs created here are ordinary slabs of the owning allocator for accounting: the allocator
 * counts them in its live slab count and used bytes through the same {@code VulkanContext.allocateMemory}
 * path as every other slab, so nothing here is an allocation outside the slab census.
 * <p>
 * <b>Threading:</b> not thread-safe; the owning allocator is confined to the render thread.
 */
public final class FixedPagePool {

    /** The fixed page size: the 64 KiB staging class the r8 crash turned on. */
    public static final long FIXED_PAGE_SIZE_BYTES = 64L * 1024;

    private final long slabSizeBytes;
    private final long pageSizeBytes;
    private final int pagesPerSlab;
    private final int maxSlabs;

    /** Live-page bitmap per slab, in slab creation order; a set bit is a live (or deferred-pending) page. */
    private final List<BitSet> livePagesBySlab = new ArrayList<BitSet>();

    /** Every allocation whose page is still counted as used, including ones pending a deferred release. */
    private final Set<Allocation> live = new HashSet<Allocation>();

    /** Allocations released through {@link #freeDeferred}, in the order they were released. */
    private final List<DeferredRelease> deferredReleases = new ArrayList<DeferredRelease>();

    /** Membership view of {@link #deferredReleases}, for rejecting a second release of the same allocation. */
    private final Set<Allocation> deferredPending = new HashSet<Allocation>();

    private long usedBytes;

    /**
     * Creates an empty pool. No slab exists until the first allocation needs one.
     *
     * @param slabSizeBytes size of every slab; must be a positive multiple of {@code pageSizeBytes}
     * @param pageSizeBytes the fixed page size; every allocation occupies exactly one page of this size
     * @param maxSlabs      the slab cap for this pool
     */
    public FixedPagePool(long slabSizeBytes, long pageSizeBytes, int maxSlabs) {
        if (slabSizeBytes <= 0L) {
            throw new IllegalArgumentException("slabSizeBytes must be positive, was " + slabSizeBytes);
        }
        if (pageSizeBytes <= 0L) {
            throw new IllegalArgumentException("pageSizeBytes must be positive, was " + pageSizeBytes);
        }
        if (maxSlabs < 1) {
            throw new IllegalArgumentException("maxSlabs must be at least 1, was " + maxSlabs);
        }
        if (slabSizeBytes % pageSizeBytes != 0L) {
            throw new IllegalArgumentException("slabSizeBytes (" + slabSizeBytes + ") must be a multiple of "
                    + "pageSizeBytes (" + pageSizeBytes + ")");
        }
        long pages = slabSizeBytes / pageSizeBytes;
        if (pages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("slabSizeBytes / pageSizeBytes is too large: " + pages);
        }
        this.slabSizeBytes = slabSizeBytes;
        this.pageSizeBytes = pageSizeBytes;
        this.pagesPerSlab = (int) pages;
        this.maxSlabs = maxSlabs;
    }

    /** Size of one slab in bytes; every slab in the pool has exactly this size. */
    public long slabSize() {
        return this.slabSizeBytes;
    }

    /** The fixed page size in bytes; every allocation reserves exactly this many bytes. */
    public long pageSize() {
        return this.pageSizeBytes;
    }

    /** The slab cap this pool was created with. */
    public int maxSlabs() {
        return this.maxSlabs;
    }

    /** Whether a request belongs to this pool's size class: its size rounds up to exactly one fixed page. */
    public boolean serves(long sizeBytes, long alignment) {
        return sizeBytes > 0L && sizeBytes <= this.pageSizeBytes && alignment >= 1L
                && this.pageSizeBytes % alignment == 0L;
    }

    /**
     * Allocates one whole page for a request of at most one page, in the first slab with a free page (slabs in
     * creation order, pages in offset order, so the layout is deterministic). When every existing slab is full
     * and the pool is below its cap, exactly one new slab is appended and its first page is handed out.
     *
     * @return the allocation, or {@code null} when the request is outside this pool's size class or alignment
     *         rule, or when every page is live and the slab cap has been reached
     */
    public Allocation allocate(long sizeBytes, long alignment) {
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("sizeBytes must be positive, was " + sizeBytes);
        }
        if (alignment < 1L) {
            throw new IllegalArgumentException("alignment must be at least 1, was " + alignment);
        }
        if (!serves(sizeBytes, alignment)) {
            return null;
        }
        for (int slab = 0; slab < this.livePagesBySlab.size(); slab++) {
            BitSet pages = this.livePagesBySlab.get(slab);
            int page = pages.nextClearBit(0);
            if (page < this.pagesPerSlab) {
                return claim(slab, page, sizeBytes);
            }
        }
        if (this.livePagesBySlab.size() >= this.maxSlabs) {
            return null;
        }
        this.livePagesBySlab.add(new BitSet(this.pagesPerSlab));
        return claim(this.livePagesBySlab.size() - 1, 0, sizeBytes);
    }

    private Allocation claim(int slab, int page, long requestedSize) {
        this.livePagesBySlab.get(slab).set(page);
        Allocation allocation = new Allocation(slab, page * this.pageSizeBytes, this.pageSizeBytes, requestedSize);
        this.live.add(allocation);
        this.usedBytes += this.pageSizeBytes;
        return allocation;
    }

    /**
     * Returns an allocation's page at once.
     *
     * @throws IllegalStateException when the allocation was already freed, is pending a deferred release, or was
     *                               never issued by this pool
     */
    public void free(Allocation allocation) {
        requireLive(allocation, "free");
        if (this.deferredPending.contains(allocation)) {
            throw new IllegalStateException("This allocation is already pending a deferred release; freeing it "
                    + "again would return the same page twice.");
        }
        releasePage(allocation);
    }

    /**
     * Records an allocation for release once the timeline reaches {@code timelineValue}; until collection, the
     * page stays counted as used and is never handed out again.
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
     * {@code completedTimelineValue}.
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
                releasePage(pending.allocation);
                released++;
            }
        }
        return released;
    }

    /** Number of slabs the pool currently holds; grows lazily up to {@link #maxSlabs()}. */
    public int slabCount() {
        return this.livePagesBySlab.size();
    }

    /** Bytes held by live allocations, including allocations pending a deferred release. */
    public long usedBytes() {
        return this.usedBytes;
    }

    /** Bytes not held by any live allocation, across all current slabs. */
    public long freeBytes() {
        return this.slabSizeBytes * this.livePagesBySlab.size() - this.usedBytes;
    }

    /**
     * Length of the longest run of consecutive free pages in any slab, in bytes. While any page is free this is
     * at least one full page - the structural guarantee the ordinary pool cannot make.
     */
    public long largestFreeRun() {
        long largest = 0L;
        for (BitSet pages : this.livePagesBySlab) {
            int run = 0;
            for (int page = 0; page < this.pagesPerSlab; page++) {
                if (pages.get(page)) {
                    run = 0;
                } else {
                    run++;
                    if (run * this.pageSizeBytes > largest) {
                        largest = run * this.pageSizeBytes;
                    }
                }
            }
        }
        return largest;
    }

    /** Allocations released through {@link #freeDeferred} that no {@link #collectCompleted} call has collected yet. */
    public int pendingDeferredCount() {
        return this.deferredReleases.size();
    }

    /** Allocations not yet returned, including ones pending a deferred release. */
    public int liveAllocationCount() {
        return this.live.size();
    }

    private void releasePage(Allocation allocation) {
        this.live.remove(allocation);
        this.usedBytes -= this.pageSizeBytes;
        this.livePagesBySlab.get(allocation.slab()).clear((int) (allocation.offset() / this.pageSizeBytes));
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
     * One fixed page handed out by this pool. Identity is meaningful, as in {@link SlabPool.Allocation}: the pool
     * tracks live allocations by the exact object it issued, which is what makes a double free detectable.
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

        /** Index of the slab holding this page, in slab creation order within this pool. */
        public int slab() {
            return this.slab;
        }

        /** Byte offset of the page inside its slab; always a multiple of the fixed page size. */
        public long offset() {
            return this.offset;
        }

        /** Size in bytes actually reserved: always exactly one fixed page. */
        public long size() {
            return this.size;
        }

        /** Size in bytes the caller asked for, before the whole-page reservation. */
        public long requestedSize() {
            return this.requestedSize;
        }
    }
}
