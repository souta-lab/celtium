package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pass timings' switches are re-resolved at every frame open, so a touch that initialises
 * {@link FramePassTimings} before the harness configuration exists must not fix them for the JVM.
 * <p>
 * The touch this simulates is the one that broke the suite: {@code VulkanTerrainGpuBackend.flushPendingBeforeGlDraw}
 * evaluates this class's section state as a call argument at its head, so a backend unit test initialises the class
 * with no harness properties set. While the resolution was taken only at class initialisation,
 * {@code PassTimingCoverageTest} then recorded nothing in the same JVM however it set its own properties
 * (hud-census-1 sections 9/10, 2026-11-23).
 */
class FramePassTimingsInitOrderTest {

    @Test
    void anEarlyTouchDoesNotLatchTheSwitchesOff() throws Exception {
        // The early touch, in the flush hook's shape: the class initialises here, with no harness
        // properties set, and only the section state is read.
        assertFalse(FramePassTimings.guiSectionActive());

        String previousSplit = System.getProperty("celtium.vulkan.passSplit");
        String previousMaster = System.getProperty("celtium.validation");
        try {
            System.setProperty("celtium.vulkan.passSplit", "true");
            System.setProperty("celtium.validation", "true");
            int writtenBefore = (Integer) field("written");
            FramePassTimings.beginFrame();
            assertTrue(FramePassTimings.enabled(),
                    "the frame open must take the resolution with the configuration now in place");
            FramePassTimings.begin("world");
            FramePassTimings.begin("hand");
            FramePassTimings.end("hand");
            FramePassTimings.end("world");
            FramePassTimings.endFrame();
            assertEquals(writtenBefore + 1, (Integer) field("written"),
                    "a frame opened after the early touch must still be recorded");
            int slot = ((Integer) field("written") - 1) % 512;
            assertTrue(((long[]) field("frameTotals"))[slot] > 0);
        } finally {
            restore("celtium.vulkan.passSplit", previousSplit);
            restore("celtium.validation", previousMaster);
        }
    }

    @Test
    void withoutTheSwitchAFrameOpenStillRecordsNothing() throws Exception {
        // The shipping shape: no harness properties, so the frame open resolves everything off and
        // records nothing - the re-resolution must not wake the instrument on its own.
        int writtenBefore = (Integer) field("written");
        FramePassTimings.beginFrame();
        FramePassTimings.endFrame();
        assertEquals(writtenBefore, (Integer) field("written"));
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
