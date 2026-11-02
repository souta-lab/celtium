package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model-instance segment's size, checked without a device.
 * <p>
 * The instance path declines a replay whose frame segment is full and the caller then takes the per-group path. In leg
 * {@code r14e-menu-colour-fix} that fallback took two thirds of the leg's replays ({@code appended=242944},
 * {@code declinedFull=479232}) while nothing else declined ({@code declinedMissing=0}) and every run shared one
 * pipeline - a capacity that bound, not a mesh, list or state that was missing. These cases pin the resolution and the
 * arithmetic bounds the segment index and the indirect command's record index rely on; they cannot say what a device
 * draws.
 */
class ModelRecordCapacityTest {

    @Test
    void anAbsentOrUnreadableValueKeepsTheDefault() {
        assertEquals(ModelRecordCapacity.DEFAULT, ModelRecordCapacity.resolve(null), "property not set");
        assertEquals(ModelRecordCapacity.DEFAULT, ModelRecordCapacity.resolve(""), "property empty");
        assertEquals(ModelRecordCapacity.DEFAULT, ModelRecordCapacity.resolve("   "), "property blank");
        assertEquals(ModelRecordCapacity.DEFAULT, ModelRecordCapacity.resolve("many"), "not a number");
        assertEquals(ModelRecordCapacity.DEFAULT, ModelRecordCapacity.resolve("16000x"), "not a whole number");
    }

    @Test
    void aRequestedValueIsKeptInsideTheBounds() {
        assertEquals(8192, ModelRecordCapacity.resolve("8192"), "a sane request is used as given");
        assertEquals(24576, ModelRecordCapacity.resolve(" 24576 "), "surrounding spaces do not change it");
        assertEquals(ModelRecordCapacity.MIN, ModelRecordCapacity.resolve("16"),
                "a typo below the floor must not starve the segment");
        assertEquals(ModelRecordCapacity.MAX, ModelRecordCapacity.resolve("100000000"),
                "a request above the ceiling is clamped, not refused");
    }

    @Test
    void theDefaultIsLargerThanTheMeasuredPackFrame() {
        assertTrue(ModelRecordCapacity.DEFAULT > 26880,
                "the r15 world attempted 26880 replays in its fullest frame; the default must not bind there");
    }

    @Test
    void theRecordIndexFitsTheIndirectCommandField() {
        long highestIndex = (long) ModelRecordCapacity.MAX * VulkanModelInstances.SEGMENTS;
        assertTrue(highestIndex < Integer.MAX_VALUE,
                "the command carries the record index as firstInstance; " + highestIndex + " does not fit an int");
    }

    @Test
    void theDefaultBuffersStayInAModestFootprint() {
        long records = ModelRecordCapacity.bytes(ModelRecordCapacity.DEFAULT, VulkanModelInstances.SEGMENTS,
                VulkanModelInstances.RECORD_BYTES);
        long commands = ModelRecordCapacity.bytes(ModelRecordCapacity.DEFAULT, VulkanModelInstances.SEGMENTS,
                VulkanModelInstances.COMMAND_BYTES);
        assertTrue(records <= 64L * 1024L * 1024L, "records at the default: " + records + " bytes");
        assertTrue(commands <= 4L * 1024L * 1024L, "commands at the default: " + commands + " bytes");
    }
}
