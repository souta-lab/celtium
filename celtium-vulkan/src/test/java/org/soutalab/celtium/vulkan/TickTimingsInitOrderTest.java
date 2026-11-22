package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tick timings' switches are re-resolved at every tick open, so a touch that initialises
 * {@link TickTimings} before the harness configuration exists must not fix them for the JVM.
 * <p>
 * The touch this simulates is structural, not hypothetical: {@link FramePassTimings#enabled()} asks this
 * class's switches, so any touch of the frame timings - a production path a unit test exercises, ahead of
 * the harness configuration - initialises this class with no harness properties set. While the resolution
 * was taken only at class initialisation, a test that then set {@code tickTimings} and opened ticks
 * recorded nothing in the same JVM (the latch {@code FramePassTimings} carried until its own fix, whose
 * record named this class as the remaining carrier; item 689).
 */
class TickTimingsInitOrderTest {

    @Test
    void anEarlyTouchDoesNotLatchTheSwitchesOff() throws Exception {
        // The early touch: the class initialises here, with no harness properties set, and only the
        // switch state is read - the shape of FramePassTimings.enabled() asking from its own path.
        assertFalse(TickTimings.enabled());

        String previousTick = System.getProperty("celtium.vulkan.tickTimings");
        String previousFrom = System.getProperty("celtium.vulkan.tickTimingsFrom");
        String previousTo = System.getProperty("celtium.vulkan.tickTimingsTo");
        String previousMaster = System.getProperty("celtium.validation");
        try {
            System.setProperty("celtium.validation", "true");
            System.setProperty("celtium.vulkan.tickTimings", "true");
            // A window that holds the test JVM's first ticks, so the recording below does not have to
            // replay the fixture's 400 warm-up ticks to reach it.
            System.setProperty("celtium.vulkan.tickTimingsFrom", "0");
            System.setProperty("celtium.vulkan.tickTimingsTo", "600");
            int recordedBefore = (Integer) field("recorded");
            TickTimings.beginTick(true);
            assertTrue(TickTimings.enabled(),
                    "the tick open must take the resolution with the configuration now in place");
            TickTimings.endTick();
            TickTimings.beginTick(true);
            TickTimings.endTick();
            assertEquals(recordedBefore + 2, (Integer) field("recorded"),
                    "ticks opened after the early touch must still be recorded");
        } finally {
            restore("celtium.vulkan.tickTimings", previousTick);
            restore("celtium.vulkan.tickTimingsFrom", previousFrom);
            restore("celtium.vulkan.tickTimingsTo", previousTo);
            restore("celtium.validation", previousMaster);
            // Leave the class resolved the way the shipping configuration resolves it, so a test that
            // reads the switches without opening a tick sees the default in this JVM too.
            TickTimings.beginTick(false);
        }
    }

    @Test
    void withoutTheSwitchATickOpenStillRecordsNothing() throws Exception {
        // The shipping shape: no harness properties, so the tick open resolves everything off and
        // records nothing - the re-resolution must not wake the instrument on its own.
        int recordedBefore = (Integer) field("recorded");
        TickTimings.beginTick(true);
        TickTimings.endTick();
        assertFalse(TickTimings.enabled());
        assertEquals(recordedBefore, (Integer) field("recorded"));
    }

    private static void restore(String name, String previous) {
        if (previous == null) System.clearProperty(name);
        else System.setProperty(name, previous);
    }

    private static Object field(String name) throws Exception {
        Field field = TickTimings.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
