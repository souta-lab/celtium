package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixed-page ledger's guarantee, stated as the r8 crash inverted: in the ordinary pool a 64 KiB request can
 * be refused while more than 64 KiB is free in runs too small to serve it; here, while any page is free, a
 * staging-class request is served, because a page is the request size and a page is never split. The remaining
 * tests pin the shared ledger rules (cap, deferred release, double free, census) to the same behaviour as
 * {@link SlabPool}.
 */
class FixedPagePoolTest {

    private static final long PAGE = FixedPagePool.FIXED_PAGE_SIZE_BYTES;
    private static final long SLAB = 16 * PAGE;

    @Test
    void everyAllocationIsOneWholePageAtAPageOffset() {
        FixedPagePool pool = new FixedPagePool(SLAB, PAGE, 2);

        FixedPagePool.Allocation first = pool.allocate(65536L, 64L);
        assertEquals(0, first.slab());
        assertEquals(0L, first.offset());
        assertEquals(PAGE, first.size());
        assertEquals(65536L, first.requestedSize());

        FixedPagePool.Allocation second = pool.allocate(65536L, 64L);
        assertEquals(PAGE, second.offset());
        assertEquals(0L, second.offset() % 64L);
    }

    /**
     * The r8 signature, reproduced as a page pattern: free every second page, so the pool holds many free pages
     * with no two adjacent - in a byte-run pool the largest free run would be a single page at best and smaller
     * interleavings would strand it below the request size. Here every freed page serves the next staging
     * request, and {@code largestFreeRun} never reports below one page while a page is free.
     */
    @Test
    void checkerboardFreePatternStillServesEveryStagingRequest() {
        FixedPagePool pool = new FixedPagePool(SLAB, PAGE, 1);
        FixedPagePool.Allocation[] allocations = new FixedPagePool.Allocation[16];
        for (int i = 0; i < allocations.length; i++) {
            allocations[i] = pool.allocate(65536L, 64L);
            assertNotNull(allocations[i]);
        }
        assertNull(pool.allocate(65536L, 64L));

        for (int i = 0; i < allocations.length; i += 2) {
            pool.free(allocations[i]);
        }
        assertEquals(8 * PAGE, pool.freeBytes());
        assertEquals(PAGE, pool.largestFreeRun());

        for (int i = 0; i < allocations.length; i += 2) {
            FixedPagePool.Allocation reused = pool.allocate(65536L, 64L);
            assertNotNull(reused);
            assertEquals(allocations[i].offset(), reused.offset());
            allocations[i] = reused;
        }
        assertEquals(0L, pool.freeBytes());
        assertNull(pool.allocate(65536L, 64L));
    }

    @Test
    void requestsOutsideTheSizeClassAreRefusedAndConsumeNoSlab() {
        FixedPagePool pool = new FixedPagePool(SLAB, PAGE, 2);

        assertFalse(pool.serves(PAGE + 1L, 64L));
        assertNull(pool.allocate(PAGE + 1L, 64L));
        // An alignment the page size is not a multiple of cannot be honoured at a page offset.
        assertFalse(pool.serves(PAGE, 96L));
        assertNull(pool.allocate(PAGE, 96L));
        assertEquals(0, pool.slabCount());

        assertTrue(pool.serves(PAGE, 64L));
        assertTrue(pool.serves(PAGE, 4096L));
    }

    @Test
    void deferredReleaseIsNotReusedUntilCollected() {
        FixedPagePool pool = new FixedPagePool(PAGE, PAGE, 1);
        FixedPagePool.Allocation allocation = pool.allocate(65536L, 64L);

        pool.freeDeferred(allocation, 5L);
        assertNull(pool.allocate(65536L, 64L));
        assertEquals(1, pool.pendingDeferredCount());
        assertEquals(0, pool.collectCompleted(4L));
        assertNull(pool.allocate(65536L, 64L));

        assertEquals(1, pool.collectCompleted(5L));
        FixedPagePool.Allocation reused = pool.allocate(65536L, 64L);
        assertEquals(allocation.offset(), reused.offset());
    }

    @Test
    void deferredChurnRecyclesOnePageIndefinitely() {
        FixedPagePool pool = new FixedPagePool(SLAB, PAGE, 1);
        // One long-lived page stays put while staging churn recycles a second page, collected each round.
        pool.allocate(65536L, 64L);
        for (long timeline = 1L; timeline <= 200L; timeline++) {
            FixedPagePool.Allocation churn = pool.allocate(65536L, 64L);
            assertNotNull(churn);
            assertEquals(PAGE, churn.offset());
            pool.freeDeferred(churn, timeline);
            assertEquals(1, pool.collectCompleted(timeline));
            assertEquals(1, pool.slabCount());
        }
    }

    @Test
    void growthAppendsSlabsAndTheCapIsHard() {
        FixedPagePool pool = new FixedPagePool(2 * PAGE, PAGE, 2);
        pool.allocate(65536L, 64L);
        pool.allocate(65536L, 64L);
        assertEquals(1, pool.slabCount());

        FixedPagePool.Allocation spill = pool.allocate(65536L, 64L);
        assertEquals(1, spill.slab());
        assertEquals(0L, spill.offset());
        pool.allocate(65536L, 64L);

        assertNull(pool.allocate(65536L, 64L));
        assertEquals(2, pool.slabCount());
    }

    @Test
    void doubleFreeThrows() {
        FixedPagePool pool = new FixedPagePool(SLAB, PAGE, 1);
        FixedPagePool.Allocation allocation = pool.allocate(65536L, 64L);
        pool.free(allocation);

        assertThrows(IllegalStateException.class, () -> pool.free(allocation));
        assertThrows(IllegalStateException.class, () -> pool.freeDeferred(allocation, 1L));
    }

    @Test
    void largestFreeRunCoalescesOnlyAcrossAdjacentFreePages() {
        FixedPagePool pool = new FixedPagePool(SLAB, PAGE, 1);
        FixedPagePool.Allocation[] allocations = new FixedPagePool.Allocation[4];
        for (int i = 0; i < allocations.length; i++) {
            allocations[i] = pool.allocate(65536L, 64L);
        }
        pool.free(allocations[1]);
        pool.free(allocations[3]);
        // Page 3 is adjacent to the untouched free tail (pages 4-15), so the longest run is pages 3-15.
        assertEquals(13 * PAGE, pool.largestFreeRun());
        pool.free(allocations[2]);
        // Pages 1, 2 and 3 are now free and adjacent to the tail: one run of pages 1-15.
        assertEquals(15 * PAGE, pool.largestFreeRun());
        pool.free(allocations[0]);
        assertEquals(SLAB, pool.largestFreeRun());
    }

    @Test
    void slabSizeMustBeAPageMultiple() {
        assertThrows(IllegalArgumentException.class, () -> new FixedPagePool(SLAB + 1L, PAGE, 1));
    }

    @Test
    void censusIsConsistentAcrossAllocateFreeAndDefer() {
        FixedPagePool pool = new FixedPagePool(SLAB, PAGE, 2);
        assertEquals(0, pool.slabCount());
        assertEquals(0L, pool.usedBytes());
        assertEquals(0L, pool.freeBytes());

        FixedPagePool.Allocation a = pool.allocate(65536L, 64L);
        pool.allocate(65536L, 64L);
        assertEquals(1, pool.slabCount());
        assertEquals(2 * PAGE, pool.usedBytes());
        assertEquals(SLAB - 2 * PAGE, pool.freeBytes());
        assertEquals(2, pool.liveAllocationCount());

        pool.freeDeferred(a, 7L);
        assertEquals(2 * PAGE, pool.usedBytes());
        assertEquals(1, pool.pendingDeferredCount());

        assertEquals(1, pool.collectCompleted(7L));
        assertEquals(PAGE, pool.usedBytes());
        assertEquals(1, pool.liveAllocationCount());
    }
}
