package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.FlushBoundaryCensus.Reason;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FL-1 / M-6: the single flush-boundary census. The census is a JVM singleton with no
 * reset, so every assertion is delta-based (counts captured before the action), which
 * keeps the tests independent of execution order and of each other.
 * <p>
 * The backend-side wiring points (F-1/F-4/F-5/F-6/F-8, at their funnels in
 * VulkanTerrainGpuBackend) cannot be exercised without a GPU backend instance; what
 * is pinned here is the ledger contract those funnels record into: each of the five
 * M-6 reasons counts as a real boundary, and the F-11 guest flush counts as an
 * arrival only - never as a boundary.
 */
class FlushBoundaryCensusTest {

    @Test
    void m6ReasonsEachCountAsOneBoundary() {
        FlushBoundaryCensus census = FlushBoundaryCensus.current();
        Reason[] m6 = {Reason.SIZE, Reason.DRAW_ENTRY, Reason.FRAME_DRAIN,
                Reason.POST_CHAIN, Reason.IMMEDIATE_RUN};
        long[] before = new long[m6.length];
        for (int i = 0; i < m6.length; i++) before[i] = census.count(m6[i]);
        long boundariesBefore = census.boundaries();

        for (Reason reason : m6) census.note(reason);

        for (int i = 0; i < m6.length; i++) {
            assertEquals(before[i] + 1, census.count(m6[i]), m6[i].name());
        }
        assertEquals(boundariesBefore + m6.length, census.boundaries());
    }

    @Test
    void guestFlushCountsAsArrivalButNeverAsBoundary() {
        FlushBoundaryCensus census = FlushBoundaryCensus.current();
        long arrivalsBefore = census.count(Reason.GUEST_FLUSH);
        long boundariesBefore = census.boundaries();

        census.noteGuestFlush();

        assertEquals(arrivalsBefore + 1, census.count(Reason.GUEST_FLUSH));
        assertEquals(boundariesBefore, census.boundaries());
    }

    @Test
    void previouslyWiredReasonsStillCountAsBoundaries() {
        FlushBoundaryCensus census = FlushBoundaryCensus.current();
        Reason[] wired = {Reason.FBO_BIND, Reason.ORDERING, Reason.RASTER_CHANGE,
                Reason.CLEAR_CONSUME, Reason.PROGRAM_MATERIALIZE};
        long boundariesBefore = census.boundaries();

        for (Reason reason : wired) {
            long before = census.count(reason);
            census.note(reason);
            assertEquals(before + 1, census.count(reason), reason.name());
        }
        assertEquals(boundariesBefore + wired.length, census.boundaries());
    }

    @Test
    void reasonEnumCoversTheElevenCensusTriggersInOrder() {
        // Census section 5 fixes F-1..F-11 in this order; the ordinal is the ledger key.
        assertEquals(11, Reason.values().length);
        assertEquals(Reason.SIZE, Reason.values()[0]);
        assertEquals(Reason.GUEST_FLUSH, Reason.values()[10]);
    }
}
