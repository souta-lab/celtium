package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-function contract checks for the issue #422 barrier and frame-slot isolation fixes:
 * <ul>
 *   <li>{@link TerrainRenderer#uniformSlotIndex(int)} isolates consecutive in-flight frame slots across
 *       {@link VulkanTerrainPipeline#DESCRIPTOR_SLOTS}.</li>
 *   <li>{@link GllessFrameTarget#layoutBarrierAccessMasks(boolean, boolean, boolean)} and
 *       {@link GllessFrameTarget#colorToTransferSrcAccessMask()} cover attachment, transfer, and shader accesses on
 *       non-first acquires and releases while keeping first-acquire {@code srcAccessMask == 0} for
 *       {@code TOP_OF_PIPE_BIT}.</li>
 *   <li>{@link VulkanGlTexture#wholeUploadEntryBarrierMasks(boolean)} orders re-uploads of already-initialized
 *       mirrors behind shader sampling and prior transfer operations instead of {@code TOP_OF_PIPE_BIT}.</li>
 * </ul>
 */
class Issue422BarrierAndSlotIsolationTest {

    @Test
    void uniformSlotIndexIsolatesAllDescriptorSlotsAndWraps() {
        int slots = VulkanTerrainPipeline.DESCRIPTOR_SLOTS;
        assertEquals(4, slots);
        for (int i = 0; i < slots; i++) {
            assertEquals(i, TerrainRenderer.uniformSlotIndex(i));
            assertEquals(i, TerrainRenderer.uniformSlotIndex(i + slots));
        }
    }

    @Test
    void firstAcquireUsesZeroSrcAccessForTopOfPipeLegality() {
        int[] colorFirst = GllessFrameTarget.layoutBarrierAccessMasks(false, true, false);
        assertEquals(0, colorFirst[0]);
        assertTrue((colorFirst[1] & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((colorFirst[1] & VK10.VK_ACCESS_TRANSFER_WRITE_BIT) != 0);

        int[] depthFirst = GllessFrameTarget.layoutBarrierAccessMasks(false, true, true);
        assertEquals(0, depthFirst[0]);
        assertTrue((depthFirst[1] & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((depthFirst[1] & VK10.VK_ACCESS_TRANSFER_WRITE_BIT) != 0);
    }

    @Test
    void subsequentAcquireAndReleaseCarryFullColorAndDepthAccessMasks() {
        int[] colorAcquire = GllessFrameTarget.layoutBarrierAccessMasks(false, false, false);
        assertTrue((colorAcquire[0] & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((colorAcquire[0] & VK10.VK_ACCESS_TRANSFER_READ_BIT) != 0);
        assertTrue((colorAcquire[0] & VK10.VK_ACCESS_TRANSFER_WRITE_BIT) != 0);
        assertTrue((colorAcquire[0] & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
        assertTrue((colorAcquire[1] & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);

        int[] colorRelease = GllessFrameTarget.layoutBarrierAccessMasks(true, false, false);
        assertTrue((colorRelease[0] & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((colorRelease[1] & VK10.VK_ACCESS_TRANSFER_READ_BIT) != 0);
        assertTrue((colorRelease[1] & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);

        int[] depthRelease = GllessFrameTarget.layoutBarrierAccessMasks(true, false, true);
        assertTrue((depthRelease[0] & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((depthRelease[1] & VK10.VK_ACCESS_TRANSFER_READ_BIT) != 0);
        assertTrue((depthRelease[1] & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
    }

    @Test
    void colorToTransferCoversPriorReadsAndWrites() {
        int mask = GllessFrameTarget.colorToTransferSrcAccessMask();
        assertTrue((mask & VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT) != 0);
        assertTrue((mask & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((mask & VK10.VK_ACCESS_TRANSFER_READ_BIT) != 0);
        assertTrue((mask & VK10.VK_ACCESS_TRANSFER_WRITE_BIT) != 0);
        assertTrue((mask & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
    }

    @Test
    void wholeUploadEntryBarrierWaitsOnSamplingStagesOnceInitialized() {
        int[] first = VulkanGlTexture.wholeUploadEntryBarrierMasks(false);
        assertEquals(VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, first[0]);
        assertEquals(0, first[1]);

        int[] reupload = VulkanGlTexture.wholeUploadEntryBarrierMasks(true);
        assertNotEquals(VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, reupload[0]);
        assertTrue((reupload[0] & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT) != 0);
        assertTrue((reupload[0] & VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT) != 0);
        assertTrue((reupload[0] & VK10.VK_PIPELINE_STAGE_TRANSFER_BIT) != 0);
        assertTrue((reupload[1] & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
        assertTrue((reupload[1] & VK10.VK_ACCESS_TRANSFER_READ_BIT) != 0);
        assertTrue((reupload[1] & VK10.VK_ACCESS_TRANSFER_WRITE_BIT) != 0);
    }
}
