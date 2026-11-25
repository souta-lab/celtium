package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The slab ledger's placement and release rules: page rounding, alignment, first-fit reuse, run merging, the slab
 * cap, and the deferred release that must not hand bytes back before the timeline completes. Oversized requests are
 * the owning allocator's problem, so the ledger side of that rule - a plain refusal that consumes no slab - is all
 * that is covered here.
 */
class SlabPoolTest {

    private static final int PAGE = 4096;

    @Test
    void basicAllocationIsPageRoundedAndPageAligned() {
        SlabPool pool = new SlabPool(64 * PAGE, PAGE, 4);

        SlabPool.Allocation first = pool.allocate(100L, 1L);
        assertEquals(0, first.slab());
        assertEquals(0L, first.offset());
        assertEquals(PAGE, first.size());
        assertEquals(100L, first.requestedSize());

        SlabPool.Allocation second = pool.allocate(PAGE, 1L);
        assertEquals(0, second.slab());
        assertEquals(PAGE, second.offset());
        assertEquals(PAGE, second.size());
    }

    @Test
    void alignmentIsRespectedIncludingNonPowerOfTwo() {
        SlabPool pool = new SlabPool(64 * PAGE, PAGE, 4);
        pool.allocate(PAGE, 1L);

        SlabPool.Allocation aligned = pool.allocate(PAGE, 2L * PAGE);
        assertEquals(2L * PAGE, aligned.offset());

        SlabPool.Allocation oddAlignment = pool.allocate(PAGE, 3L * PAGE);
        assertEquals(0L, oddAlignment.offset() % (3L * PAGE));
        assertEquals(3L * PAGE, oddAlignment.offset());
    }

    @Test
    void firstFitReusesTheEarliestHole() {
        SlabPool pool = new SlabPool(64 * PAGE, PAGE, 4);
        SlabPool.Allocation a = pool.allocate(PAGE, 1L);
        SlabPool.Allocation b = pool.allocate(PAGE, 1L);
        pool.allocate(PAGE, 1L);

        pool.free(b);
        SlabPool.Allocation reused = pool.allocate(PAGE, 1L);
        assertEquals(b.offset(), reused.offset());

        // The earlier hole wins over the later one: first fit scans in offset order.
        pool.free(a);
        pool.free(reused);
        SlabPool.Allocation earliest = pool.allocate(PAGE, 1L);
        assertEquals(a.offset(), earliest.offset());
    }

    @Test
    void freeMergesAdjacentRuns() {
        SlabPool pool = new SlabPool(4 * PAGE, PAGE, 1);
        SlabPool.Allocation a = pool.allocate(PAGE, 1L);
        SlabPool.Allocation b = pool.allocate(PAGE, 1L);
        SlabPool.Allocation c = pool.allocate(PAGE, 1L);
        SlabPool.Allocation d = pool.allocate(PAGE, 1L);
        assertEquals(0L, pool.largestFreeRun());

        pool.free(b);
        pool.free(c);
        assertEquals(2L * PAGE, pool.largestFreeRun());

        pool.free(a);
        pool.free(d);
        assertEquals(4L * PAGE, pool.largestFreeRun());
        // The fully merged slab serves a whole-slab request again.
        SlabPool.Allocation whole = pool.allocate(4L * PAGE, 1L);
        assertEquals(0L, whole.offset());
    }

    @Test
    void doubleFreeThrows() {
        SlabPool pool = new SlabPool(4 * PAGE, PAGE, 1);
        SlabPool.Allocation allocation = pool.allocate(PAGE, 1L);
        pool.free(allocation);

        assertThrows(IllegalStateException.class, () -> pool.free(allocation));
        assertThrows(IllegalStateException.class, () -> pool.freeDeferred(allocation, 1L));
    }

    @Test
    void growthAppendsASecondSlabWithoutMovingExistingAllocations() {
        SlabPool pool = new SlabPool(2 * PAGE, PAGE, 4);
        SlabPool.Allocation a = pool.allocate(PAGE, 1L);
        SlabPool.Allocation b = pool.allocate(PAGE, 1L);
        assertEquals(1, pool.slabCount());

        SlabPool.Allocation c = pool.allocate(PAGE, 1L);
        assertEquals(2, pool.slabCount());
        assertEquals(1, c.slab());
        assertEquals(0L, c.offset());
        // Growth is append-only: the first slab's allocations keep their slab and offset.
        assertEquals(0, a.slab());
        assertEquals(0L, a.offset());
        assertEquals(0, b.slab());
        assertEquals(PAGE, b.offset());
    }

    @Test
    void maxSlabsReachedReturnsNull() {
        SlabPool pool = new SlabPool(PAGE, PAGE, 1);
        pool.allocate(PAGE, 1L);

        assertNull(pool.allocate(PAGE, 1L));
        assertEquals(1, pool.slabCount());
    }

    @Test
    void deferredFreeIsNotReusedUntilCollected() {
        SlabPool pool = new SlabPool(2 * PAGE, PAGE, 1);
        SlabPool.Allocation a = pool.allocate(PAGE, 1L);
        pool.allocate(PAGE, 1L);

        pool.freeDeferred(a, 5L);
        // The range is still reserved: nothing fits, and the pool is at its slab cap.
        assertNull(pool.allocate(PAGE, 1L));
        assertEquals(1, pool.pendingDeferredCount());

        assertEquals(0, pool.collectCompleted(4L));
        assertNull(pool.allocate(PAGE, 1L));

        assertEquals(1, pool.collectCompleted(5L));
        assertEquals(0, pool.pendingDeferredCount());
        SlabPool.Allocation reused = pool.allocate(PAGE, 1L);
        assertEquals(a.offset(), reused.offset());
    }

    @Test
    void oversizedRequestIsRefusedAndConsumesNoSlab() {
        SlabPool pool = new SlabPool(2 * PAGE, PAGE, 4);

        assertNull(pool.allocate(2L * PAGE + 1L, 1L));
        assertEquals(0, pool.slabCount());
    }

    /**
     * The Phase 1 leg crash pattern (r4/r6: a 65536-byte, alignment-64 staging request refused at the slab cap
     * while the pool sat full of uncollected deferred releases): long-lived small allocations stay put while a
     * per-frame-sized churn allocation is allocated, deferred-released and collected over and over. As long as
     * collection is driven, the churn must keep succeeding inside the slabs that already exist, and once every
     * allocation is back, each slab must coalesce to one whole-slab free run again.
     */
    @Test
    void deferredChurnBehindLongLivedAllocationsStaysBoundedAndCoalesces() {
        SlabPool pool = new SlabPool(256 * PAGE, PAGE, 8);
        SlabPool.Allocation[] longLived = new SlabPool.Allocation[3];
        for (int i = 0; i < longLived.length; i++) {
            longLived[i] = pool.allocate(2L * PAGE, 1L);
            assertEquals(0, longLived[i].slab());
        }
        assertEquals(1, pool.slabCount());

        for (long timeline = 1L; timeline <= 200L; timeline++) {
            SlabPool.Allocation churn = pool.allocate(65536L, 64L);
            assertNotNull(churn);
            // Band segregation: 65536 bytes is the large band in a 1 MiB slab (threshold 64 KiB), so the churn
            // recycles in its own slab (index 1) and never touches the small allocations' slab.
            assertEquals(1, churn.slab());
            pool.freeDeferred(churn, timeline);
            assertEquals(1, pool.collectCompleted(timeline));
            assertEquals(2, pool.slabCount());
        }

        for (SlabPool.Allocation allocation : longLived) {
            pool.free(allocation);
        }
        assertEquals(0L, pool.usedBytes());
        assertEquals(256L * PAGE, pool.largestFreeRun());
        // Two slabs exist now (one per band), so the free total is both slabs while each coalesces whole.
        assertEquals(2L * 256 * PAGE, pool.freeBytes());
    }

    /**
     * The other half of the crash mechanism: without collection, deferred churn is indistinguishable from live
     * memory, so the pool fills to its cap and refuses - and only a collection at the releases' timeline value
     * makes the same request succeed again. This is what the allocator's collect-on-allocate retry and the
     * per-frame collection drive exist to prevent in production.
     */
    @Test
    void uncollectedDeferredChurnExhaustsThePoolUntilCollected() {
        SlabPool pool = new SlabPool(32 * PAGE, PAGE, 2);
        for (int i = 0; i < 4; i++) {
            SlabPool.Allocation churn = pool.allocate(65536L, 64L);
            assertNotNull(churn);
            pool.freeDeferred(churn, 100L);
        }
        assertEquals(2, pool.slabCount());
        assertEquals(4, pool.pendingDeferredCount());
        // Both slabs are full of ranges the timeline has not released: the next request is refused.
        assertNull(pool.allocate(65536L, 64L));

        assertEquals(0, pool.collectCompleted(99L));
        assertNull(pool.allocate(65536L, 64L));

        assertEquals(4, pool.collectCompleted(100L));
        assertEquals(0, pool.pendingDeferredCount());
        SlabPool.Allocation reused = pool.allocate(65536L, 64L);
        assertNotNull(reused);
        // 65536 bytes is the large class in a 128 KiB slab, so the tail slab is served first.
        assertEquals(1, reused.slab());
        assertEquals(0L, reused.offset());
    }

    /**
     * Size-class placement within a band: a large request prefers the tail slab of its band even when a fitting
     * hole exists in the head slab - so large requests keep the long runs of the newest slabs. (Slab of 64 pages;
     * the large threshold is a sixteenth of it, 4 pages, so every request here is large-band.)
     */
    @Test
    void largeRequestsPreferTheTailSlabAndSmallRequestsFillHeadHoles() {
        SlabPool pool = new SlabPool(64 * PAGE, PAGE, 4);
        SlabPool.Allocation large = pool.allocate(8L * PAGE, 1L);
        assertEquals(0, large.slab());
        for (int i = 0; i < 14; i++) {
            assertEquals(0, pool.allocate(4L * PAGE, 1L).slab());
        }
        SlabPool.Allocation spill = pool.allocate(4L * PAGE, 1L);
        assertEquals(1, spill.slab());
        assertEquals(2, pool.slabCount());

        // Freeing the large head allocation leaves an 8-page hole at the start of slab 0, but the next large
        // request still prefers the tail slab while the tail has a run that serves it.
        pool.free(large);
        SlabPool.Allocation tailLarge = pool.allocate(8L * PAGE, 1L);
        assertEquals(1, tailLarge.slab());
        assertEquals(4L * PAGE, tailLarge.offset());
    }

    /**
     * Band segregation, the r7 crash pattern in miniature: medium allocations and small allocations interleaved
     * over time must not share a slab, so a small long-lived allocation cannot pin a hole pattern into the slab
     * whose runs medium requests need. The small churn fills and recycles its own slab while the medium slab's
     * freed runs stay whole and serve the next medium request.
     */
    @Test
    void smallAllocationsDoNotPinMediumSlabs() {
        // Slab of 1024 pages (4 MiB); large threshold 256 KiB, small ceiling 64 KiB.
        SlabPool pool = new SlabPool(1024 * PAGE, PAGE, 4);
        SlabPool.Allocation mediumA = pool.allocate(128L * 1024, 1L);
        assertEquals(0, mediumA.slab());
        SlabPool.Allocation smallPin = pool.allocate(PAGE, 1L);
        // The small request does not land behind the medium one; it opens a small-band slab.
        assertEquals(1, smallPin.slab());
        assertEquals(2, pool.slabCount());

        SlabPool.Allocation mediumB = pool.allocate(128L * 1024, 1L);
        assertEquals(0, mediumB.slab());
        assertEquals(128L * 1024, mediumB.offset());

        // Small churn recycles only the small slab, however often it runs.
        for (int i = 0; i < 64; i++) {
            SlabPool.Allocation churn = pool.allocate(65536L, 64L);
            assertEquals(1, churn.slab());
            pool.free(churn);
        }
        assertEquals(2, pool.slabCount());

        // A medium hole freed between live medium neighbours stays a whole medium run: the next medium
        // request takes it, undamaged by the small churn above.
        pool.free(mediumA);
        SlabPool.Allocation mediumC = pool.allocate(128L * 1024, 1L);
        assertEquals(0, mediumC.slab());
        assertEquals(0L, mediumC.offset());
    }

    /**
     * Bands guide placement only while the pool can grow: at the slab cap, a request whose band has no slab
     * falls back to whatever slab can serve it, so segregation never shrinks the pool's total capacity.
     */
    @Test
    void bandSegregationFallsBackToAnySlabAtTheCap() {
        SlabPool pool = new SlabPool(1024 * PAGE, PAGE, 1);
        SlabPool.Allocation medium = pool.allocate(128L * 1024, 1L);
        assertEquals(0, medium.slab());

        // The only slab is medium-band and the cap is reached, yet the small request is still served from it.
        SlabPool.Allocation small = pool.allocate(PAGE, 1L);
        assertNotNull(small);
        assertEquals(0, small.slab());
        assertEquals(128L * 1024, small.offset());
        assertEquals(1, pool.slabCount());
    }

    @Test
    void slabSizeMustBeAPageMultiple() {
        assertThrows(IllegalArgumentException.class, () -> new SlabPool(2L * PAGE + 1L, PAGE, 1));
    }

    @Test
    void censusIsConsistentAcrossAllocateFreeAndDefer() {
        SlabPool pool = new SlabPool(4 * PAGE, PAGE, 2);
        assertEquals(0, pool.slabCount());
        assertEquals(0L, pool.usedBytes());
        assertEquals(0L, pool.freeBytes());

        SlabPool.Allocation a = pool.allocate(PAGE, 1L);
        SlabPool.Allocation b = pool.allocate(2L * PAGE, 1L);
        assertEquals(1, pool.slabCount());
        assertEquals(3L * PAGE, pool.usedBytes());
        assertEquals(PAGE, pool.freeBytes());
        assertEquals(PAGE, pool.largestFreeRun());
        assertEquals(2, pool.liveAllocationCount());
        assertEquals(0, pool.pendingDeferredCount());

        // A deferred release still counts as used until it is collected.
        pool.freeDeferred(a, 7L);
        assertEquals(3L * PAGE, pool.usedBytes());
        assertEquals(2, pool.liveAllocationCount());
        assertEquals(1, pool.pendingDeferredCount());

        assertEquals(1, pool.collectCompleted(7L));
        assertEquals(2L * PAGE, pool.usedBytes());
        assertEquals(1, pool.liveAllocationCount());
        assertEquals(0, pool.pendingDeferredCount());

        pool.free(b);
        assertEquals(0L, pool.usedBytes());
        assertEquals(4L * PAGE, pool.freeBytes());
        assertEquals(4L * PAGE, pool.largestFreeRun());
        assertEquals(0, pool.liveAllocationCount());
    }
}
