package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The item-722 brackets inside the pack region loop (optim-enum E6, first stage): the
 * design's discipline is the testable part - the four names are inclusive and report-only, so each
 * must resolve in FramePassTimings' INSIDE table (reported, never subtracted) and must stay out of
 * every set an instrument subtracts with: the pass table's TOP_LEVEL union and FrameBudgetTimings'
 * other-split buckets and world-pass members. A name that entered one of those sets would
 * double-count its containment chain, the kpi-8 v1 accident this design exists to avoid. The print
 * shape is pinned through the begin-call summary, which lists every bracket name whether or not the
 * timings ran.
 */
class PackRegionBracketTest {
    private static final String[] PACK_REGION = {"packRegionIter", "packRegionBind", "packRegionDraw", "packSamplerClaim"};

    /**
     * FramePassTimings latches its harness switches when the class loads, and the test JVM is shared:
     * whichever test touches the class first fixes the latch for every later one. PassTimingCoverageTest
     * sets the pass-split route's properties before its own first touch and would measure nothing if
     * this class latched the switches off first. So the same properties are set here, before this
     * class's first touch, and restored after - the latch then holds the same value either class would
     * have latched on its own. (Same guard as CloudR3BracketTest.)
     */
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

    @Test void packRegionNamesResolveInsideOnly() throws Exception {
        String[] topLevel = (String[]) field(FramePassTimings.class, "TOP_LEVEL");
        String[] inside = (String[]) field(FramePassTimings.class, "INSIDE");
        for (String name : PACK_REGION) {
            int index = FramePassTimings.indexOfName(name);
            assertTrue(index >= topLevel.length, name + " must resolve in the INSIDE table, got index " + index);
            assertEquals(name, inside[index - topLevel.length]);
            for (String top : topLevel) {
                assertNotEquals(top, name, name + " must not be a top-level (subtracted) bracket");
            }
        }
    }

    @Test void packRegionNamesStayOutOfEverySubtractionSet() throws Exception {
        String[][] buckets = (String[][]) field(FrameBudgetTimings.class, "OTHER_BUCKETS");
        String[] worldPassMembers = (String[]) field(FrameBudgetTimings.class, "WORLD_PASS_MEMBERS");
        for (String name : PACK_REGION) {
            for (String[] bucket : buckets) {
                for (String member : bucket) {
                    assertNotEquals(member, name, name + " must not join an other-split bucket");
                }
            }
            for (String member : worldPassMembers) {
                assertNotEquals(member, name, name + " must not join the world-pass members");
            }
        }
    }

    @Test void packRegionNamesAppearInThePrintedBracketList() {
        String summary = FramePassTimings.passBeginCallsSummary();
        for (String name : PACK_REGION) {
            assertTrue(summary.contains(name + "="), name + " missing from the bracket list: " + summary);
        }
    }

    private static Object field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
