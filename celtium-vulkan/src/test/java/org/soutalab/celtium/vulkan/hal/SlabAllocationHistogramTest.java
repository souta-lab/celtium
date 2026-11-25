package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The caller-by-size-class histogram's counting rules: page-rounded class boundaries (with the fixed-page
 * class landing exactly on raw requests in (61440, 65536]), live counts that include releases not yet
 * collected - the ledgers' own live semantics - and the summary rendering the memory census prints.
 */
class SlabAllocationHistogramTest {

    private static final long SLAB = 64L * 1024 * 1024;

    @Test
    void sizeClassesFollowThePageRoundedBounds() {
        assertEquals(0, SlabAllocationHistogram.classify(4096L, SLAB));
        assertEquals(1, SlabAllocationHistogram.classify(8192L, SLAB));
        assertEquals(1, SlabAllocationHistogram.classify(16384L, SLAB));
        assertEquals(2, SlabAllocationHistogram.classify(20480L, SLAB));
        assertEquals(2, SlabAllocationHistogram.classify(32768L, SLAB));
        assertEquals(3, SlabAllocationHistogram.classify(36864L, SLAB));
        assertEquals(3, SlabAllocationHistogram.classify(49152L, SLAB));
        assertEquals(4, SlabAllocationHistogram.classify(53248L, SLAB));
        assertEquals(4, SlabAllocationHistogram.classify(61440L, SLAB));
        assertEquals(6, SlabAllocationHistogram.classify(69632L, SLAB));
        assertEquals(6, SlabAllocationHistogram.classify(262144L, SLAB));
        assertEquals(7, SlabAllocationHistogram.classify(266240L, SLAB));
        assertEquals(7, SlabAllocationHistogram.classify(1048576L, SLAB));
        assertEquals(8, SlabAllocationHistogram.classify(16777216L, SLAB));
        assertEquals(9, SlabAllocationHistogram.classify(SLAB, SLAB));
        assertEquals(10, SlabAllocationHistogram.classify(SLAB + 4096L, SLAB));
    }

    @Test
    void fixedPageClassIsExactlyThePageRounded64kClass() {
        // Raw requests in (61440, 65536] page-round to exactly 65536, the fixed page size: the class the
        // Stage 3+4 exhaustion left unattributed (8,192 live pages) must be this one and no other.
        assertEquals(SlabAllocationHistogram.FIXED_PAGE_CLASS,
                SlabAllocationHistogram.classify(65536L, SLAB));
        assertEquals(SlabAllocationHistogram.FIXED_PAGE_CLASS - 1,
                SlabAllocationHistogram.classify(61440L, SLAB));
    }

    @Test
    void liveCountsIncludeReleasesUntilCollectionPassesTheirValue() {
        SlabAllocationHistogram histogram = new SlabAllocationHistogram(SLAB);
        histogram.noteAllocated(SlabAllocationHistogram.SITE_HOST_BUFFER, 65536L, true);
        histogram.noteAllocated(SlabAllocationHistogram.SITE_HOST_BUFFER, 65536L, true);
        histogram.noteAllocated(SlabAllocationHistogram.SITE_TEXTURE_STAGING, 32768L, false);

        histogram.noteReleased(SlabAllocationHistogram.SITE_HOST_BUFFER, 65536L, true, 10L);
        // Released but not collected: still live, matching the ledgers' liveAllocationCount semantics -
        // the state a refusal actually happens in.
        assertEquals(2, histogram.liveCount(SlabAllocationHistogram.SITE_HOST_BUFFER,
                SlabAllocationHistogram.FIXED_PAGE_CLASS));
        assertEquals(1, histogram.pendingCount());

        histogram.collectCompleted(9L);
        assertEquals(2, histogram.liveCount(SlabAllocationHistogram.SITE_HOST_BUFFER,
                SlabAllocationHistogram.FIXED_PAGE_CLASS));

        histogram.collectCompleted(10L);
        assertEquals(1, histogram.liveCount(SlabAllocationHistogram.SITE_HOST_BUFFER,
                SlabAllocationHistogram.FIXED_PAGE_CLASS));
        assertEquals(1, histogram.fixedLiveCount(SlabAllocationHistogram.SITE_HOST_BUFFER,
                SlabAllocationHistogram.FIXED_PAGE_CLASS));
        assertEquals(2, histogram.totalCount(SlabAllocationHistogram.SITE_HOST_BUFFER,
                SlabAllocationHistogram.FIXED_PAGE_CLASS));
        assertEquals(1, histogram.liveCount(SlabAllocationHistogram.SITE_TEXTURE_STAGING, 2));
        assertEquals(0, histogram.pendingCount());
    }

    @Test
    void fixedServedFallsBackAreCountedInTheClassButNotAsFixed() {
        SlabAllocationHistogram histogram = new SlabAllocationHistogram(SLAB);
        // A 64k-class request the fixed pool could not serve falls back to the ordinary ledger; it still
        // belongs to the class, but the (fixed=N) split must not claim the fixed ledger served it.
        histogram.noteAllocated(SlabAllocationHistogram.SITE_HOST_BUFFER, 65536L, false);
        assertEquals(1, histogram.liveCount(SlabAllocationHistogram.SITE_HOST_BUFFER,
                SlabAllocationHistogram.FIXED_PAGE_CLASS));
        assertEquals(0, histogram.fixedLiveCount(SlabAllocationHistogram.SITE_HOST_BUFFER,
                SlabAllocationHistogram.FIXED_PAGE_CLASS));
    }

    @Test
    void outOfRangeSitesLandInTheUnknownSite() {
        SlabAllocationHistogram histogram = new SlabAllocationHistogram(SLAB);
        histogram.noteAllocated(99, 4096L, false);
        histogram.noteAllocated(-1, 4096L, false);
        assertEquals(2, histogram.liveCount(SlabAllocationHistogram.SITE_UNKNOWN, 0));
        assertEquals(2, histogram.totalCount(SlabAllocationHistogram.SITE_UNKNOWN, 0));
    }

    @Test
    void summaryRendersOnlyCellsThatSawAnAllocation() {
        SlabAllocationHistogram histogram = new SlabAllocationHistogram(SLAB);
        assertTrue(histogram.isEmpty());
        histogram.noteAllocated(SlabAllocationHistogram.SITE_HOST_BUFFER, 65536L, true);
        histogram.noteAllocated(SlabAllocationHistogram.SITE_HOST_BUFFER, 65536L, true);
        histogram.noteReleased(SlabAllocationHistogram.SITE_HOST_BUFFER, 65536L, true, 5L);
        histogram.collectCompleted(5L);
        histogram.noteAllocated(SlabAllocationHistogram.SITE_UNIFORM_BUFFER, 16384L, false);

        StringBuilder rendered = new StringBuilder();
        histogram.appendSummary(rendered);
        assertEquals("hostBuffer:64k=1/2(fixed=1) uniformBuffer:le16k=1/1", rendered.toString());
    }
}
