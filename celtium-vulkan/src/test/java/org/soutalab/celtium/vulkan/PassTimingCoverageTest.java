package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.harness.HarnessConfig;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class PassTimingCoverageTest {
    @Test void nestedWorldPassesCountOnce() {
        PassTimingCoverage c = new PassTimingCoverage(5);
        c.begin(0, 10); // world
        c.begin(1, 20); // hand
        c.begin(2, 25); // clouds
        c.end(2, 35);
        c.end(1, 40);
        c.end(0, 90);
        assertEquals(80, c.coveredNanos(100));
    }

    @Test void disjointPassesLeaveTheirGapInTheResidual() {
        PassTimingCoverage c = new PassTimingCoverage(2);
        c.begin(0, 10); c.end(0, 40);
        c.begin(1, 60); c.end(1, 90);
        assertEquals(60, c.coveredNanos(100));
    }

    @Test void crossingIntervalsAndReentrantNamesAreBothAUnion() {
        PassTimingCoverage c = new PassTimingCoverage(2);
        assertTrue(c.begin(0, 10));
        assertFalse(c.begin(0, 20));
        c.begin(1, 30);
        assertFalse(c.end(0, 40));
        assertTrue(c.end(0, 50));
        c.end(1, 70);
        assertEquals(60, c.coveredNanos(100));
        assertFalse(c.end(0, 80));
        assertEquals(60, c.coveredNanos(100));
    }

    @Test void unfinishedBracketIsClippedAndDoesNotLeakToTheNextFrame() {
        PassTimingCoverage c = new PassTimingCoverage(1);
        c.begin(0, 10);
        assertEquals(90, c.coveredNanos(100));
        c.reset();
        assertEquals(0, c.coveredNanos(200));
        assertFalse(c.end(0, 210));
        c.begin(0, 220); c.end(0, 230);
        assertEquals(10, c.coveredNanos(250));
    }

    @Test void actualFrameResidualDoesNotSubtractHandInsideWorldTwice() throws Exception {
        String previousSplit = System.getProperty("celtium.vulkan.passSplit");
        String previousMaster = System.getProperty("celtium.validation");
        try {
            // The resolution this instrument's switch takes, pinned for its own key before the class is
            // touched: nothing set stays off; the legacy spelling alone stays off, because a stray launcher
            // argument must not wake a harness instrument; the spelling with the master switch on is the
            // leg's route. That route is what FB2 (2026-11-22) did not have: this class read the spelling
            // raw, so the harness file could not name the key at all, and the run's own summary had to be
            // read afterwards to learn the instrument had stayed off.
            assertFalse(HarnessConfig.bool("passSplit", false));
            System.setProperty("celtium.vulkan.passSplit", "true");
            assertFalse(HarnessConfig.bool("passSplit", false));
            System.setProperty("celtium.validation", "true");
            assertTrue(HarnessConfig.bool("passSplit", false));

            FramePassTimings.beginFrame();
            FramePassTimings.begin("world");
            FramePassTimings.begin("hand");
            FramePassTimings.end("hand");
            FramePassTimings.end("world");
            FramePassTimings.endFrame();
            int slot = ((Integer) field("written")) - 1;
            slot %= 512;
            long frame = ((long[]) field("frameTotals"))[slot];
            long world = ((long[][]) field("topLevel"))[0][slot];
            long hand = ((long[][]) field("topLevel"))[1][slot];
            long residual = ((long[]) field("residuals"))[slot];
            assertTrue(hand > 0);
            assertTrue(world >= hand);
            assertEquals(frame - world, residual);
            // The companion switches stay opt-in in the same run: the per-bracket call counts the FB2
            // summary read are not kept unless passCounts asks, and the split prints in milliseconds
            // unless passSplitMicros asks.
            assertFalse(FramePassTimings.countsCallsEnabled());
            assertEquals("ms", FramePassTimings.unit());

            // Thread confinement (roam-hunt-10 observation A, lanechat #2103): the light funnel opens
            // its lightCache bracket before its own gate, and its mixins also fire from the integrated
            // server thread, whose brackets share these tables. A foreign thread's still-open bracket
            // is counted, but it must not hold the name's outermost slot: unconfined, this frame's own
            // lightCache pair nests inside the foreign open (depth never returns to zero for it) and
            // its time is never filed, so the inside row below reads zero on the pre-fix code.
            FramePassTimings.beginFrame();
            long countBefore = FramePassTimings.passBeginCalls("lightCache");
            Thread foreign = new Thread(() -> FramePassTimings.begin("lightCache"));
            foreign.start();
            foreign.join();
            assertEquals(countBefore + 1, FramePassTimings.passBeginCalls("lightCache"));
            FramePassTimings.begin("lightCache");
            Thread.sleep(2);
            FramePassTimings.end("lightCache");
            FramePassTimings.endFrame();
            int frameSlot = ((Integer) field("written")) - 1;
            frameSlot %= 512;
            int lightCacheRow = FramePassTimings.indexOfName("lightCache")
                    - ((long[][]) field("topLevel")).length;
            assertTrue(((long[][]) field("inside"))[lightCacheRow][frameSlot] > 0);
            Thread foreignClose = new Thread(() -> FramePassTimings.end("lightCache"));
            foreignClose.start();
            foreignClose.join();
        } finally {
            restore("celtium.vulkan.passSplit", previousSplit);
            restore("celtium.validation", previousMaster);
        }
    }

    private static void restore(String name, String previous) {
        if (previous == null) System.clearProperty(name);
        else System.setProperty(name, previous);
    }

    private static Object field(String name) throws Exception {
        Field field = FramePassTimings.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
