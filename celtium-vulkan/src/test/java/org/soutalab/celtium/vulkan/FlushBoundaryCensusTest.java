package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The GLSM stage-2 S2-1 census' counting, driven through its Ledger directly: a window is
 * filed under exactly the cause its entrance stamped, the window line carries the per-cause
 * split and the since-start totals, the reconciliation invariant
 * ({@code sum(cause) == actualFlushes == sum(windowsByTrigger)}, with the trigger view
 * derived from the causes) holds on a consistent window and counts a discrepancy on an
 * inconsistent one, the new causes (chain-order, frame-end) stay distinct from the families
 * they used to hide inside, and an out-of-range cause is filed as other rather than lost.
 * The {@code flushBoundary} seam's discipline is pinned the same way the other INSIDE
 * brackets pin theirs: inside-only, out of every subtraction set.
 */
class FlushBoundaryCensusTest {

    private static String previousSplit;
    private static String previousMaster;

    @BeforeAll static void latchThePassSplitRouteOpen() {
        previousSplit = System.getProperty("celtium.vulkan.passSplit");
        previousMaster = System.getProperty("celtium.validation");
        System.setProperty("celtium.vulkan.passSplit", "true");
        System.setProperty("celtium.validation", "true");
    }

    @AfterAll static void restoreProperties() {
        restore("celtium.vulkan.passSplit", previousSplit);
        restore("celtium.validation", previousMaster);
    }

    private static void restore(String name, String previous) {
        if (previous == null) System.clearProperty(name);
        else System.setProperty(name, previous);
    }

    /** Files {@code causes} as confirmed windows: cause, actual flush and hold key each. */
    private static void fileWindows(FlushBoundaryCensus.Ledger ledger, int... causes) {
        for (int cause : causes) {
            ledger.noteWindow(cause, 0);
            ledger.noteActualFlush();
            ledger.noteHoldKey(0x1234L);
        }
    }

    /** {@return the trigger sum the derivation of {@code causes} implies}. */
    private static long derivedTriggerSum(int... causes) {
        return causes.length;
    }

    @Test void windowLineCarriesTheCauseSplitAndResetsTheWindow() {
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        fileWindows(ledger,
                FlushBoundaryCensus.CAUSE_BIND_FRAMEBUFFER_TARGET,
                FlushBoundaryCensus.CAUSE_BIND_FRAMEBUFFER_TARGET,
                FlushBoundaryCensus.CAUSE_TEXTURE_ANNOUNCE,
                FlushBoundaryCensus.CAUSE_BATCH_SUBMIT);
        List<String> lines = ledger.windowLines(200, derivedTriggerSum(1, 1, 1, 1), 4L);
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.startsWith("CELTIUM_FLUSH_CENSUS frames=200"), line);
        assertTrue(line.contains("bind-framebuffer-target=2"), line);
        assertTrue(line.contains("texture-announce=1"), line);
        assertTrue(line.contains("batch-submit=1"), line);
        assertTrue(line.contains("actualFlushes=4/4"), line);
        assertTrue(line.contains("censusDiscrepancy=0"), line);
        assertEquals(0L, ledger.censusDiscrepancy());
        assertTrue(ledger.windowLines(200, 0L, 4L).isEmpty(), "an idle consistent window prints nothing");
    }

    @Test void totalsSurviveTheWindowReset() {
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        fileWindows(ledger, FlushBoundaryCensus.CAUSE_SIZE);
        ledger.windowLines(200, 1L, 1L);
        fileWindows(ledger, FlushBoundaryCensus.CAUSE_SIZE, FlushBoundaryCensus.CAUSE_COPY_ORDER);
        List<String> lines = ledger.windowLines(200, 2L, 3L);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("total[windows=3 copy-order=1 size=2]"), lines.get(0));
        assertEquals(3L, ledger.total());
        assertEquals(2L, ledger.totalFor(FlushBoundaryCensus.CAUSE_SIZE));
    }

    @Test void everyCauseIsNamedAndCountedSeparately() {
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        int[] all = new int[FlushBoundaryCensus.CAUSE_NAMES.length];
        for (int cause = 0; cause < all.length; cause++) all[cause] = cause;
        fileWindows(ledger, all);
        assertEquals(FlushBoundaryCensus.CAUSE_NAMES.length, ledger.total());
        for (int cause = 0; cause < all.length; cause++) {
            assertEquals(1L, ledger.totalFor(cause), "cause " + cause);
        }
        String line = ledger.windowLines(512, all.length, all.length).get(0);
        for (String name : FlushBoundaryCensus.CAUSE_NAMES) {
            assertTrue(line.contains(name + "=1"), "line names " + name + ": " + line);
        }
    }

    @Test void outOfRangeCausesFileAsOther() {
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        fileWindows(ledger, -1, 9999);
        assertEquals(2L, ledger.totalFor(FlushBoundaryCensus.CAUSE_OTHER));
        assertEquals(2L, ledger.totalFor(-1), "an out-of-range read is the other bucket too");
        assertEquals(2L, ledger.total());
    }

    @Test void bindKindsStayDistinct() {
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        fileWindows(ledger,
                FlushBoundaryCensus.CAUSE_BIND_FRAMEBUFFER_MAIN,
                FlushBoundaryCensus.CAUSE_BIND_FRAMEBUFFER_TARGET,
                FlushBoundaryCensus.CAUSE_BIND_FRAMEBUFFER_RAW);
        String line = ledger.windowLines(200, 3L, 3L).get(0);
        assertTrue(line.contains("bind-framebuffer-main=1"), line);
        assertTrue(line.contains("bind-framebuffer-target=1"), line);
        assertTrue(line.contains("bind-framebuffer-raw=1"), line);
    }

    @Test void chainAndFrameEndAreTheirOwnCauses() {
        // S2-1's new classification: the chain input flush used to hide inside
        // replay-offtarget, and frame-end windows inside other.
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        fileWindows(ledger,
                FlushBoundaryCensus.CAUSE_CHAIN_ORDER,
                FlushBoundaryCensus.CAUSE_REPLAY_OFFTARGET,
                FlushBoundaryCensus.CAUSE_FRAME_END);
        assertEquals(1L, ledger.totalFor(FlushBoundaryCensus.CAUSE_CHAIN_ORDER));
        assertEquals(1L, ledger.totalFor(FlushBoundaryCensus.CAUSE_REPLAY_OFFTARGET));
        assertEquals(1L, ledger.totalFor(FlushBoundaryCensus.CAUSE_FRAME_END));
        assertEquals(0L, ledger.totalFor(FlushBoundaryCensus.CAUSE_OTHER));
    }

    @Test void triggerDerivationMatchesTheDesignTable() {
        assertEquals(FramePassTimings.WINDOW_TRIGGER_SIZE,
                FlushBoundaryCensus.triggerForCause(FlushBoundaryCensus.CAUSE_SIZE));
        for (int cause : new int[] {FlushBoundaryCensus.CAUSE_CLEAR_ORDER, FlushBoundaryCensus.CAUSE_COPY_ORDER,
                FlushBoundaryCensus.CAUSE_REFUSAL_ORDER, FlushBoundaryCensus.CAUSE_CHAIN_ORDER,
                FlushBoundaryCensus.CAUSE_TEXTURE_ANNOUNCE, FlushBoundaryCensus.CAUSE_TEXTURE_LIFETIME}) {
            assertEquals(FramePassTimings.WINDOW_TRIGGER_ORDER, FlushBoundaryCensus.triggerForCause(cause),
                    "cause " + cause);
        }
        assertEquals(FramePassTimings.WINDOW_TRIGGER_FRAME_END,
                FlushBoundaryCensus.triggerForCause(FlushBoundaryCensus.CAUSE_FRAME_END));
        for (int cause : new int[] {FlushBoundaryCensus.CAUSE_OTHER, FlushBoundaryCensus.CAUSE_BIND_FRAMEBUFFER_MAIN,
                FlushBoundaryCensus.CAUSE_BATCH_SUBMIT, FlushBoundaryCensus.CAUSE_REPLAY_OFFTARGET,
                FlushBoundaryCensus.CAUSE_RAW_WRITE_GATE, FlushBoundaryCensus.CAUSE_COMPUTE_ORDER}) {
            assertEquals(FramePassTimings.WINDOW_TRIGGER_GL_DRAW, FlushBoundaryCensus.triggerForCause(cause),
                    "cause " + cause);
        }
    }

    @Test void reconciliationCountsADiscrepancyInsteadOfDrifting() {
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        // Two causes filed but only one actual flush and a trigger sum of one:
        // both the window scope and the total scope disagree.
        ledger.noteWindow(FlushBoundaryCensus.CAUSE_SIZE, 0);
        ledger.noteWindow(FlushBoundaryCensus.CAUSE_SIZE, 0);
        ledger.noteActualFlush();
        List<String> lines = ledger.windowLines(200, 1L, 1L);
        assertEquals(1, lines.size(), "a mismatching window still prints, with the discrepancy on it");
        assertTrue(lines.get(0).contains("censusDiscrepancy=2"), lines.get(0));
        assertEquals(2L, ledger.censusDiscrepancy());
    }

    @Test void subClassificationsRideTheStamp() {
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        ledger.noteWindow(FlushBoundaryCensus.CAUSE_COPY_ORDER, FlushBoundaryCensus.SUB_COPY_READBACK);
        ledger.noteActualFlush();
        ledger.noteWindow(FlushBoundaryCensus.CAUSE_COPY_ORDER, 0);
        ledger.noteActualFlush();
        ledger.noteWindow(FlushBoundaryCensus.CAUSE_FRAME_END, FlushBoundaryCensus.SUB_FRAME_END_PRESENT);
        ledger.noteActualFlush();
        ledger.noteWindow(FlushBoundaryCensus.CAUSE_FRAME_END, 0);
        ledger.noteActualFlush();
        String line = ledger.windowLines(200, 4L, 4L).get(0);
        assertTrue(line.contains("copyReadback=1/1"), line);
        assertTrue(line.contains("frameEndPresent=1/1"), line);
    }

    @Test void groupBreakAndMidSubmitRideTheSameWindow() {
        FlushBoundaryCensus.Ledger ledger = new FlushBoundaryCensus.Ledger();
        fileWindows(ledger, FlushBoundaryCensus.CAUSE_BATCH_SUBMIT);
        ledger.noteGroupBreak(FlushBoundaryCensus.GROUP_BREAK_PIPELINE);
        ledger.noteGroupBreak(FlushBoundaryCensus.GROUP_BREAK_TARGET);
        ledger.noteGroupBreak(-1);
        ledger.noteMidFrameSubmit(FlushBoundaryCensus.SUBMIT_WINDOW);
        ledger.noteMidFrameSubmit(FlushBoundaryCensus.SUBMIT_CHAIN);
        ledger.noteMidFrameSubmit(FlushBoundaryCensus.SUBMIT_ONE_SHOT_UPLOAD);
        String line = ledger.windowLines(200, 1L, 1L).get(0);
        assertTrue(line.contains("groupBreak[pipeline=1 target=1]"), line);
        assertTrue(line.contains("midSubmit[window=1 chain=1 one-shot-upload=1]"), line);
        assertTrue(line.contains("holdKeys[1234=1]"), line);
    }

    @Test void flushBoundaryBracketResolvesInsideOnly() throws Exception {
        String[] topLevel = (String[]) field(FramePassTimings.class, "TOP_LEVEL");
        String[] inside = (String[]) field(FramePassTimings.class, "INSIDE");
        int index = FramePassTimings.indexOfName("flushBoundary");
        assertTrue(index >= topLevel.length, "flushBoundary must resolve in the INSIDE table, got " + index);
        assertEquals("flushBoundary", inside[index - topLevel.length]);
        String[][] buckets = (String[][]) field(FrameBudgetTimings.class, "OTHER_BUCKETS");
        String[] worldPassMembers = (String[]) field(FrameBudgetTimings.class, "WORLD_PASS_MEMBERS");
        for (String[] bucket : buckets) {
            for (String member : bucket) {
                assertNotEquals(member, "flushBoundary", "flushBoundary must not join an other-split bucket");
            }
        }
        for (String member : worldPassMembers) {
            assertNotEquals(member, "flushBoundary", "flushBoundary must not join the world-pass members");
        }
        assertTrue(FramePassTimings.passBeginCallsSummary().contains("flushBoundary="),
                "flushBoundary missing from the bracket list");
    }

    private static Object field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
