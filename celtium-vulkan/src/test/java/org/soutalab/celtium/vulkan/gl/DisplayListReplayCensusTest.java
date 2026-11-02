package org.soutalab.celtium.vulkan.gl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The display-list census must be able to count the lists a run actually replays.
 *
 * <p>The census lives on the verification line as {@code celtium/lists[…]}, and it is the only measurement of the
 * drawing path the "custom rendering is invisible" report is about: geometry compiled into a list never passes through
 * the uploader the immediate path hooks, so a replay that draws nothing leaves no other trace.
 *
 * <p>Its first form was an {@code int[512]} indexed by the list's GL name, and it recorded a replay only when the id was
 * below 512. Vanilla's model renderers compile their lists after the GUI and the Tessellator have taken thousands of
 * names, so every measured run replays ids above 110000 - measured on the calibrated world leg {@code r40b-gate}, whose
 * line read {@code celtium/lists[none]} on all four prints while the same log lists
 * {@code CELTIUM_DISPLAY_LIST_ORIGIN list=110244 … 110338} for the chest, sign, banner, bed, skull and biped models.
 * A census that cannot count the busiest lists reads exactly like a run that replayed nothing, which is the failure mode
 * these tests exist to keep out.
 */
class DisplayListReplayCensusTest {

    /** A model list id: above the old 512-entry array, and the range every measured run uses. */
    private static final int MODEL_LIST = 110278;

    @Test
    @DisplayName("a list id above the old array bound is counted and ranked")
    void aHighListIdIsCounted() {
        CeltiumGlStub.celtium$noteListReplayForCheck(MODEL_LIST, true);
        String summary = CeltiumGlStub.listCensus();
        assertTrue(summary.contains(MODEL_LIST + ":x"),
                "a replay of list " + MODEL_LIST + " has to appear in the census; got: " + summary);
        assertTrue(summary.contains("top=["),
                "the census has to carry a ranked list once something was replayed; got: " + summary);
    }

    @Test
    @DisplayName("the summary always names its totals, never a bare 'none'")
    void theSummaryNamesItsTotals() {
        String summary = CeltiumGlStub.listCensus();
        assertFalse(summary.equals("none"),
                "'none' is what a dead census prints as well as a quiet run; the totals have to be readable");
        for (String field : new String[] {"compiled=", "replayed=", "unresolved=", "nestingOverflow=", "held=",
                "distinct="}) {
            assertTrue(summary.contains(field), "the census has to report " + field + "; got: " + summary);
        }
    }

    @Test
    @DisplayName("a call to a list that was never compiled is named by id, not only counted")
    void anUnresolvedCallIsNamed() {
        int missing = 123457;
        CeltiumGlStub.celtium$noteListReplayForCheck(missing, false);
        String summary = CeltiumGlStub.listCensus();
        assertTrue(summary.contains("unresolvedTop=["),
                "an unresolved glCallList loses geometry silently in GL, so the census has to name it; got: " + summary);
        assertTrue(summary.contains(missing + ":x"),
                "the id that missed has to be in the list; got: " + summary);
    }

    @Test
    @DisplayName("the -1 sentinel is not reported as a lost draw")
    void theSentinelIsNotALostDraw() {
        // Measured on the entity leg: ModelRenderer passes -1 for a part whose list was never compiled, GL defines the
        // call as a no-op, and the single counter reported all 1250 of them as "unresolved" - which reads as 1250 draws
        // that found nothing. They have to be two fields, and the sentinel must never appear in the unresolved ranking.
        CeltiumGlStub.celtium$noteListReplayForCheck(-1, false);
        CeltiumGlStub.celtium$noteListReplayForCheck(0, false);
        String summary = CeltiumGlStub.listCensus();
        assertTrue(summary.contains("noList="), "the sentinel has to have its own field; got: " + summary);
        assertFalse(summary.contains("unresolvedTop=[-1"),
                "a non-positive name is the caller saying 'no list', not a draw that was lost; got: " + summary);
        assertFalse(summary.contains("unresolvedTop=[0"),
                "list 0 is not a name either; got: " + summary);
    }

    @Test
    @DisplayName("the nesting overflow is reported")
    void nestingOverflowIsReported() {
        // Not driven here: reaching the limit needs a stub and a recursive list. What is pinned is that the field is on
        // the line, because a counter that exists but is never printed cannot be read from a leg.
        assertTrue(CeltiumGlStub.listCensus().contains("nestingOverflow="),
                "a replay refused for nesting has to be visible: " + CeltiumGlStub.listCensus());
        assertTrue(CeltiumGlStub.listReplayNestingOverflows() >= 0L);
    }
}
