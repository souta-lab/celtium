package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DP-01's census: the three pre-backend offer deaths are counted unconditionally and kept apart.
 * <p>
 * The defect this pins was a counter that stopped counting: the offer hook's swallowed-failure count
 * lived inside its own print budget, so the fourth failure onwards left no trace, and the two early
 * returns (no buffer, address 0) had no counter at all. Deltas are read around the notes because the
 * census is process-wide static state shared with every other test in the JVM.
 */
class OfferFunnelCensusTest {

    @Test
    void eachDeathIsCountedApart() {
        long swallowed = OfferFunnelCensus.swallowed();
        long noData = OfferFunnelCensus.noData();
        long noAddress = OfferFunnelCensus.noAddress();
        OfferFunnelCensus.noteSwallowed();
        OfferFunnelCensus.noteSwallowed();
        OfferFunnelCensus.noteNoData();
        OfferFunnelCensus.noteNoAddress();
        assertEquals(swallowed + 2, OfferFunnelCensus.swallowed(), "swallowed");
        assertEquals(noData + 1, OfferFunnelCensus.noData(), "noData");
        assertEquals(noAddress + 1, OfferFunnelCensus.noAddress(), "noAddress");
    }

    /** Counting does not stop at the old print budget of three - the fourth and fifth notes still land. */
    @Test
    void countingContinuesPastThePrintBudget() {
        long before = OfferFunnelCensus.swallowed();
        for (int i = 0; i < 5; i++) {
            OfferFunnelCensus.noteSwallowed();
        }
        assertEquals(before + 5, OfferFunnelCensus.swallowed());
    }

    @Test
    void summaryNamesAllThree() {
        String summary = OfferFunnelCensus.summary();
        assertTrue(summary.contains("swallowed=" + OfferFunnelCensus.swallowed()), summary);
        assertTrue(summary.contains("noData=" + OfferFunnelCensus.noData()), summary);
        assertTrue(summary.contains("noAddress=" + OfferFunnelCensus.noAddress()), summary);
    }
}
