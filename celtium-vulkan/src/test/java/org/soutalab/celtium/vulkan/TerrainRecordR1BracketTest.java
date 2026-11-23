package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The item 691 terrainRecord residual brackets (flight-census-leg-2 section 4, R1): the named rec*
 * children summed to 748/996 us of a 1977/2577 us terrainRecord, so the residual 1.2-1.6 ms was the
 * parent's largest single component and had no name. The five new names cover every span of
 * recordRenderPass the older names do not - recHead (pass head to recordPrepare), recPassSetup
 * (prepare's end to the region loop), recIter (the per-region iterator advance), recCharge (the
 * per-region span between recLoop and recBind) and recPassTail (the loop's end to the method's end).
 * The testable discipline is the same as the cloud R3 brackets': each name is inclusive and
 * report-only, so it must resolve in FramePassTimings' INSIDE table and stay out of every set an
 * instrument subtracts with - the pass table's TOP_LEVEL union and FrameBudgetTimings' other-split
 * buckets and world-pass members. They sit inside the terrain bucket through terrainRecord, so the
 * other-split needs no new member for them; a name that entered one of those sets would double-count
 * its containment chain.
 */
class TerrainRecordR1BracketTest {
    private static final String[] R1 = {"recHead", "recPassSetup", "recIter", "recCharge", "recPassTail"};

    /**
     * FramePassTimings latches its harness switches when the class loads, and the test JVM is shared:
     * whichever test touches the class first fixes the latch for every later one. The same properties
     * the other bracket tests set are set here, before this class's first touch, and restored after -
     * the latch then holds the same value either class would have latched on its own.
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

    @Test void r1NamesResolveInsideOnly() throws Exception {
        String[] topLevel = (String[]) field(FramePassTimings.class, "TOP_LEVEL");
        String[] inside = (String[]) field(FramePassTimings.class, "INSIDE");
        for (String name : R1) {
            int index = FramePassTimings.indexOfName(name);
            assertTrue(index >= topLevel.length, name + " must resolve in the INSIDE table, got index " + index);
            assertEquals(name, inside[index - topLevel.length]);
            for (String top : topLevel) {
                assertNotEquals(top, name, name + " must not be a top-level (subtracted) bracket");
            }
        }
    }

    @Test void r1NamesStayOutOfEverySubtractionSet() throws Exception {
        String[][] buckets = (String[][]) field(FrameBudgetTimings.class, "OTHER_BUCKETS");
        String[] worldPassMembers = (String[]) field(FrameBudgetTimings.class, "WORLD_PASS_MEMBERS");
        for (String name : R1) {
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

    @Test void r1NamesAppearInThePrintedBracketList() {
        String summary = FramePassTimings.passBeginCallsSummary();
        for (String name : R1) {
            assertTrue(summary.contains(name + "="), name + " missing from the bracket list: " + summary);
        }
    }

    private static Object field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
