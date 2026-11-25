package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
