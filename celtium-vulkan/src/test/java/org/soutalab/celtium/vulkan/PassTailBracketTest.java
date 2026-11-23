package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The E5 pass-tail brackets (e5-passtail-design section 2.1): the E5 census put recPassTail at
 * p50 623.1 us - 77.4% of the newly named terrainRecord residual - with no name inside it. The five
 * new names split that span at its real seams: tailChain (chain close plus the pass-fog finally),
 * tailStats (statistics end), tailImm (the immediate tail's condition plus drawPendingImmediate),
 * tailEndPass (vkCmdEndRenderPass through the readback copies) and tailRelease (the routed
 * hand-back plus the release barriers). The testable discipline is the item 691 one: each name is
 * inclusive and report-only, so it must resolve in FramePassTimings' INSIDE table and stay out of
 * every set an instrument subtracts with - the pass table's TOP_LEVEL union and FrameBudgetTimings'
 * other-split buckets and world-pass members. They sit inside recPassTail, so the other-split needs
 * no new member for them; a name that entered one of those sets would double-count its containment.
 * The tailImm fire counter is the census' positive control: percentiles alone cannot say whether
 * most passes fired the immediate tail or a few fired it heavily.
 */
class PassTailBracketTest {
    private static final String[] TAIL = {"tailChain", "tailStats", "tailImm", "tailEndPass", "tailRelease"};

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

    @Test void tailNamesResolveInsideOnly() throws Exception {
        String[] topLevel = (String[]) field(FramePassTimings.class, "TOP_LEVEL");
        String[] inside = (String[]) field(FramePassTimings.class, "INSIDE");
        for (String name : TAIL) {
            int index = FramePassTimings.indexOfName(name);
            assertTrue(index >= topLevel.length, name + " must resolve in the INSIDE table, got index " + index);
            assertEquals(name, inside[index - topLevel.length]);
            for (String top : topLevel) {
                assertNotEquals(top, name, name + " must not be a top-level (subtracted) bracket");
            }
        }
    }

    @Test void tailNamesStayOutOfEverySubtractionSet() throws Exception {
        String[][] buckets = (String[][]) field(FrameBudgetTimings.class, "OTHER_BUCKETS");
        String[] worldPassMembers = (String[]) field(FrameBudgetTimings.class, "WORLD_PASS_MEMBERS");
        for (String name : TAIL) {
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

    @Test void tailNamesAppearInThePrintedBracketList() {
        String summary = FramePassTimings.passBeginCallsSummary();
        for (String name : TAIL) {
            assertTrue(summary.contains(name + "="), name + " missing from the bracket list: " + summary);
        }
    }

    @Test void tailImmediateFireCounterCounts() {
        long before = DrawCounters.tailImmediateFires();
        DrawCounters.noteTailImmediateFire();
        assertEquals(before + 1, DrawCounters.tailImmediateFires());
    }

    private static Object field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
