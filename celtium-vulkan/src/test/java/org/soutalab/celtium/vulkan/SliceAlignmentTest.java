package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The immediate mesh's slice-start alignment, checked without a device.
 * <p>
 * A group's vertex slice starts at an address the draw call binds at, and the cheap draw form carries that start as a
 * vertex offset in the command instead of re-binding the buffer - which only works when the start is a whole number of
 * vertices. The rule is therefore {@code lcm(16, stride)}: 16 for the 4-byte attribute rule, rounded up to the layout's
 * own stride.
 * <p>
 * The value used to be computed inline in {@code vertexWriteAddress} on every append - a Euclid loop of two long
 * remainders on each of the ~220 offers a frame - although it depends only on the stride. It is now cached per stride;
 * these tests pin the arithmetic the cache returns, not the cache's effect on a frame.
 */
class SliceAlignmentTest {

    @Test
    void theStridesTheImmediateLayoutsUse() {
        // POSITION (12), POSITION_TEX (20), POSITION_TEX_COLOR (24), POSITION_TEX_NORMAL (28),
        // POSITION_TEX_COLOR_NORMAL (32), the 32-byte mod layout (36).
        assertEquals(48L, VulkanDynamicMesh.celtium$sliceAlignmentFor(12), "lcm(16,12) = 48");
        assertEquals(80L, VulkanDynamicMesh.celtium$sliceAlignmentFor(20), "lcm(16,20) = 80");
        assertEquals(48L, VulkanDynamicMesh.celtium$sliceAlignmentFor(24), "lcm(16,24) = 48");
        assertEquals(112L, VulkanDynamicMesh.celtium$sliceAlignmentFor(28), "lcm(16,28) = 112");
        assertEquals(32L, VulkanDynamicMesh.celtium$sliceAlignmentFor(32), "lcm(16,32) = 32");
        assertEquals(144L, VulkanDynamicMesh.celtium$sliceAlignmentFor(36), "lcm(16,36) = 144");
    }

    @Test
    void aStrideThatIsAMultipleOfSixteenNeedsNoRounding() {
        assertEquals(16L, VulkanDynamicMesh.celtium$sliceAlignmentFor(16), "16 is already the base alignment");
        assertEquals(64L, VulkanDynamicMesh.celtium$sliceAlignmentFor(64), "a 64-byte vertex starts on 64");
    }

    @Test
    void aStrideThatSaysNothingKeepsTheBaseAlignment() {
        // The layout is null before the first begin(), and the field's own default is a 20-byte layout; neither may
        // produce a zero alignment, which would make the arena's alignTo divide by zero.
        assertEquals(16L, VulkanDynamicMesh.celtium$sliceAlignmentFor(0), "no stride means the 4-byte rule only");
        assertEquals(16L, VulkanDynamicMesh.celtium$sliceAlignmentFor(-1), "a negative stride is not a stride");
    }

    @Test
    void everyAlignmentIsAWholeNumberOfVerticesAndSixteenAligned() {
        for (int stride = 1; stride <= 256; stride++) {
            long alignment = VulkanDynamicMesh.celtium$sliceAlignmentFor(stride);
            assertTrue(alignment % 16L == 0L, "stride " + stride + " gives " + alignment + ", not 16-aligned");
            assertTrue(alignment % stride == 0L, "stride " + stride + " gives " + alignment + ", not a whole vertex");
            assertTrue(alignment >= 16L, "stride " + stride + " gives " + alignment + ", below the base alignment");
            assertTrue(alignment <= 16L * stride, "stride " + stride + " gives " + alignment + ", above lcm's bound");
        }
    }
}
