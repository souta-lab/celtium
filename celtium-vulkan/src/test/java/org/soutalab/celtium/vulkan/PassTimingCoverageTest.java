package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

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
        String previous = System.getProperty("celtium.vulkan.passSplit");
        System.setProperty("celtium.vulkan.passSplit", "true");
        try {
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
        } finally {
            if (previous == null) System.clearProperty("celtium.vulkan.passSplit");
            else System.setProperty("celtium.vulkan.passSplit", previous);
        }
    }

    private static Object field(String name) throws Exception {
        Field field = FramePassTimings.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
