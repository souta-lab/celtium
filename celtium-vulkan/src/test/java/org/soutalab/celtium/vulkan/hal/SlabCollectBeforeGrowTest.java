package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Collect-before-grow (BO convergence lever 2, part (a)): when a pool is about to append a slab while
 * deferred releases are pending, it first collects the releases the completion currency has already
 * passed and retries the search, so the owner's slab peak - one device memory allocation per slab,
 * and a slab is never given back - is not raised for ranges that were free in everything but
 * bookkeeping. The gate is the timeline value itself: a release whose value has not completed is
 * never collected at the growth point, and the growth proceeds exactly as it did before the hook
 * existed. These tests exercise the pure ledgers without a device; the owner's wiring of the
 * currency source is pinned by {@code SlabCollectBeforeGrowContractTest}.
 */
class SlabCollectBeforeGrowTest {

    private static final long PAGE = 4096L;

    @Test
    void ordinaryPoolCollectsCompletedReleasesBeforeGrowing() {
        SlabPool pool = new SlabPool(64 * PAGE, (int) PAGE, 4);
        AtomicLong completed = new AtomicLong(0L);
        pool.setCompletedValueSource(completed::get);
        SlabPool.Allocation[] fill = new SlabPool.Allocation[64];
        for (int i = 0; i < fill.length; i++) {
            fill[i] = pool.allocate(PAGE, 1L);
            assertNotNull(fill[i]);
        }
        assertEquals(1, pool.slabCount());
        pool.freeDeferred(fill[0], 5L);
        completed.set(10L);
        SlabPool.Allocation next = pool.allocate(PAGE, 1L);
        assertNotNull(next);
        assertEquals(1, pool.slabCount(),
                "the completed release is collected and its range reused; no slab is appended");
        assertEquals(0, next.slab());
        assertEquals(fill[0].offset(), next.offset());
        assertEquals(0, pool.pendingDeferredCount());
    }

    @Test
    void ordinaryPoolNeverCollectsAReleaseWhoseValueHasNotCompleted() {
        SlabPool pool = new SlabPool(64 * PAGE, (int) PAGE, 4);
        AtomicLong completed = new AtomicLong(10L);
        pool.setCompletedValueSource(completed::get);
        SlabPool.Allocation[] fill = new SlabPool.Allocation[64];
        for (int i = 0; i < fill.length; i++) {
            fill[i] = pool.allocate(PAGE, 1L);
            assertNotNull(fill[i]);
        }
        assertEquals(1, pool.slabCount());
        pool.freeDeferred(fill[0], 50L);
        SlabPool.Allocation next = pool.allocate(PAGE, 1L);
        assertNotNull(next);
        assertEquals(2, pool.slabCount(),
                "an uncompleted release stays pending, so the growth proceeds");
        assertEquals(1, next.slab());
        assertEquals(1, pool.pendingDeferredCount(),
                "the uncompleted release was not collected at the growth point");
    }

    @Test
    void growthConsultsTheSourceOnlyWhileReleasesArePending() {
        SlabPool pool = new SlabPool(64 * PAGE, (int) PAGE, 4);
        pool.setCompletedValueSource(() -> {
            throw new AssertionError("no deferred release is pending, so the source must not be read");
        });
        for (int i = 0; i < 64; i++) {
            assertNotNull(pool.allocate(PAGE, 1L));
        }
        assertNotNull(pool.allocate(PAGE, 1L));
        assertEquals(2, pool.slabCount(), "with nothing pending, growth proceeds without collecting");
    }

    @Test
    void fixedPoolCollectsCompletedReleasesBeforeGrowing() {
        long page = FixedPagePool.FIXED_PAGE_SIZE_BYTES;
        FixedPagePool pool = new FixedPagePool(4 * page, page, 2);
        AtomicLong completed = new AtomicLong(0L);
        pool.setCompletedValueSource(completed::get);
        FixedPagePool.Allocation[] fill = new FixedPagePool.Allocation[4];
        for (int i = 0; i < fill.length; i++) {
            fill[i] = pool.allocate(page, 64L);
            assertNotNull(fill[i]);
        }
        assertEquals(1, pool.slabCount());
        pool.freeDeferred(fill[0], 5L);
        completed.set(10L);
        FixedPagePool.Allocation next = pool.allocate(page, 64L);
        assertNotNull(next);
        assertEquals(1, pool.slabCount(),
                "the completed release is collected and its page reused; no slab is appended");
        assertEquals(0, next.slab());
        assertEquals(fill[0].offset(), next.offset());
        assertEquals(0, pool.pendingDeferredCount());
    }

    @Test
    void fixedPoolNeverCollectsAReleaseWhoseValueHasNotCompleted() {
        long page = FixedPagePool.FIXED_PAGE_SIZE_BYTES;
        FixedPagePool pool = new FixedPagePool(4 * page, page, 2);
        AtomicLong completed = new AtomicLong(10L);
        pool.setCompletedValueSource(completed::get);
        FixedPagePool.Allocation[] fill = new FixedPagePool.Allocation[4];
        for (int i = 0; i < fill.length; i++) {
            fill[i] = pool.allocate(page, 64L);
            assertNotNull(fill[i]);
        }
        assertEquals(1, pool.slabCount());
        pool.freeDeferred(fill[0], 50L);
        FixedPagePool.Allocation next = pool.allocate(page, 64L);
        assertNotNull(next);
        assertEquals(2, pool.slabCount(),
                "an uncompleted release stays pending, so the growth proceeds");
        assertEquals(1, next.slab());
        assertEquals(1, pool.pendingDeferredCount(),
                "the uncompleted release was not collected at the growth point");
    }
}
