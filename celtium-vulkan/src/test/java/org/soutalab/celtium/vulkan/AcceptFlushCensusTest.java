package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The flight-c4 accept-flush census (flight-c3-desk section 4): the design's discipline is the
 * testable part. The {@code acceptFlushRetained} bracket is inclusive and report-only, so it must
 * resolve in FramePassTimings' INSIDE table and stay out of every set an instrument subtracts with -
 * a name that entered one of those sets would double-count its containment chain (the flush sits
 * inside the acceptance, which sits inside {@code clouds}), the kpi-8 v1 accident this family of
 * designs exists to avoid. The counters are gated on the census switch: with it off the note methods
 * count nothing, so a player's run and a plain census run observe exactly the old behaviour.
 */
class AcceptFlushCensusTest {
    /**
     * FramePassTimings resolves its harness switches when the class loads and again at every frame
     * open, and the test JVM is shared: the same properties the pass-split route needs are set here
     * before this class's first touch and restored after, so whichever class latches first holds the
     * same values (the CloudR3BracketTest guard, for the same reason).
     */
    private static String previousSplit;
    private static String previousMaster;
    private static String previousAcceptFlush;

    @BeforeAll static void latchThePassSplitRouteOpen() {
        previousSplit = System.getProperty("celtium.vulkan.passSplit");
        previousMaster = System.getProperty("celtium.validation");
        previousAcceptFlush = System.getProperty("celtium.vulkan.acceptFlush");
        System.setProperty("celtium.vulkan.passSplit", "true");
        System.setProperty("celtium.validation", "true");
        System.setProperty("celtium.vulkan.acceptFlush", "true");
    }

    @AfterAll static void restoreProperties() {
        restore("celtium.vulkan.passSplit", previousSplit);
        restore("celtium.validation", previousMaster);
        restore("celtium.vulkan.acceptFlush", previousAcceptFlush);
    }

    private static void restore(String name, String previous) {
        if (previous == null) System.clearProperty(name);
        else System.setProperty(name, previous);
    }

    @Test void acceptFlushRetainedResolvesInsideOnly() throws Exception {
        String[] topLevel = (String[]) field(FramePassTimings.class, "TOP_LEVEL");
        String[] inside = (String[]) field(FramePassTimings.class, "INSIDE");
        int index = FramePassTimings.indexOfName("acceptFlushRetained");
        assertTrue(index >= topLevel.length,
                "acceptFlushRetained must resolve in the INSIDE table, got index " + index);
        assertEquals("acceptFlushRetained", inside[index - topLevel.length]);
        for (String top : topLevel) {
            assertNotEquals(top, "acceptFlushRetained",
                    "acceptFlushRetained must not be a top-level (subtracted) bracket");
        }
    }

    @Test void acceptFlushRetainedStaysOutOfEverySubtractionSet() throws Exception {
        String[][] buckets = (String[][]) field(FrameBudgetTimings.class, "OTHER_BUCKETS");
        String[] worldPassMembers = (String[]) field(FrameBudgetTimings.class, "WORLD_PASS_MEMBERS");
        for (String[] bucket : buckets) {
            for (String member : bucket) {
                assertNotEquals(member, "acceptFlushRetained",
                        "acceptFlushRetained must not join an other-split bucket");
            }
        }
        for (String member : worldPassMembers) {
            assertNotEquals(member, "acceptFlushRetained",
                    "acceptFlushRetained must not join the world-pass members");
        }
    }

    @Test void acceptFlushRetainedAppearsInThePrintedBracketList() {
        String summary = FramePassTimings.passBeginCallsSummary();
        assertTrue(summary.contains("acceptFlushRetained="),
                "acceptFlushRetained missing from the bracket list: " + summary);
    }

    @Test void windowTriggerKindsAreDistinct() {
        int[] kinds = {FramePassTimings.WINDOW_TRIGGER_SIZE, FramePassTimings.WINDOW_TRIGGER_ORDER,
                FramePassTimings.WINDOW_TRIGGER_FRAME_END, FramePassTimings.WINDOW_TRIGGER_GL_DRAW};
        for (int i = 0; i < kinds.length; i++) {
            for (int j = i + 1; j < kinds.length; j++) {
                assertNotEquals(kinds[i], kinds[j], "window trigger kinds must be distinct");
            }
        }
    }

    /**
     * With the census switch resolved on (the frame open re-resolves it), the note methods count:
     * every size trip, the retained share of them, and one window per trigger note. The totals are
     * cumulative over the shared JVM, so the assertions read deltas around this test's own notes.
     * An out-of-range trigger kind is refused silently - a bad stamp must never fail a frame over a
     * census.
     */
    @Test void countersCountTripsAndWindowsWhileTheSwitchIsOn() throws Exception {
        FramePassTimings.beginFrame();
        try {
            assertTrue(FramePassTimings.acceptFlushEnabled(),
                    "the acceptFlush switch must resolve on for this test's properties");
            long tripsBefore = (Long) field(FramePassTimings.class, "sizeFlushTripsTotal");
            long retainedBefore = (Long) field(FramePassTimings.class, "sizeFlushTripsByRetainedTotal");
            long[] windowsBefore = ((long[]) field(FramePassTimings.class, "windowsByTriggerTotal")).clone();

            FramePassTimings.noteSizeFlushTrip(true, 41_000);
            FramePassTimings.noteSizeFlushTrip(false, 33_000);
            FramePassTimings.noteWindowTrigger(FramePassTimings.WINDOW_TRIGGER_SIZE);
            FramePassTimings.noteWindowTrigger(-1);
            FramePassTimings.noteWindowTrigger(Integer.MAX_VALUE);

            long tripsAfter = (Long) field(FramePassTimings.class, "sizeFlushTripsTotal");
            long retainedAfter = (Long) field(FramePassTimings.class, "sizeFlushTripsByRetainedTotal");
            long[] windowsAfter = (long[]) field(FramePassTimings.class, "windowsByTriggerTotal");
            assertEquals(2L, tripsAfter - tripsBefore, "both size trips must count");
            assertEquals(1L, retainedAfter - retainedBefore, "only the retained trip counts as retained");
            assertEquals(1L,
                    windowsAfter[FramePassTimings.WINDOW_TRIGGER_SIZE]
                            - windowsBefore[FramePassTimings.WINDOW_TRIGGER_SIZE],
                    "the size window must count once");
            long otherWindows = 0L;
            for (int i = 0; i < windowsAfter.length; i++) {
                if (i != FramePassTimings.WINDOW_TRIGGER_SIZE) {
                    otherWindows += windowsAfter[i] - windowsBefore[i];
                }
            }
            assertEquals(0L, otherWindows, "no other trigger may count, and bad kinds are refused");
        } finally {
            FramePassTimings.endFrame();
        }
    }

    private static Object field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
