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
    void selfTransitionDetectionRequiresBothImagesSettled() {
        int general = VK10.VK_IMAGE_LAYOUT_GENERAL;
        int depthLayout = VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        assertTrue(GllessFrameTarget.layoutsAreSelfTransitions(general, general, depthLayout, depthLayout));
        // The first acquire leaves UNDEFINED on both images: a real transition is owed.
        assertTrue(!GllessFrameTarget.layoutsAreSelfTransitions(
                VK10.VK_IMAGE_LAYOUT_UNDEFINED, general, VK10.VK_IMAGE_LAYOUT_UNDEFINED, depthLayout));
        // An acquire after an outside phase left the colour image read-only also owes its transition.
        assertTrue(!GllessFrameTarget.layoutsAreSelfTransitions(
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, general, depthLayout, depthLayout));
        assertTrue(!GllessFrameTarget.layoutsAreSelfTransitions(
                general, general, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, depthLayout));
    }

    @Test
    void selfTransitionMasksAreTheUnionOfTheImagePairs() {
        for (boolean release : new boolean[] { false, true }) {
            int[] color = GllessFrameTarget.layoutBarrierAccessMasks(release, false, false);
            int[] depth = GllessFrameTarget.layoutBarrierAccessMasks(release, false, true);
            int[] union = GllessFrameTarget.selfTransitionBarrierMasks(release, false);
            assertEquals(color[0] | depth[0], union[0]);
            assertEquals(color[1] | depth[1], union[1]);
            // The union must never narrow either side (issue #422 (a) full-mask rule).
            assertEquals(union[0], union[0] | color[0] | depth[0]);
            assertEquals(union[1], union[1] | color[1] | depth[1]);
        }
        // A first acquire keeps the VUID-02819 shape even in union form: no source accesses at TOP_OF_PIPE.
        assertEquals(0, GllessFrameTarget.selfTransitionBarrierMasks(false, true)[0]);
    }

    @Test
    void handbackMergeRequiresPendingSameBufferAndSelfTransitionAcquire() {
        // Nothing held: nothing to merge.
        assertTrue(!GllessFrameTarget.handbackCanMerge(false, 0x1000L, 0x1000L, true));
        // Held on a different command buffer: the merged barrier would execute in the wrong submission.
        assertTrue(!GllessFrameTarget.handbackCanMerge(true, 0x1000L, 0x2000L, true));
        // The acquire owes a real layout transition: it must not absorb the hand-back.
        assertTrue(!GllessFrameTarget.handbackCanMerge(true, 0x1000L, 0x1000L, false));
        assertTrue(GllessFrameTarget.handbackCanMerge(true, 0x1000L, 0x1000L, true));
    }

    @Test
    void handbackMergedMasksNeverNarrowEitherSide() {
        int[] release = GllessFrameTarget.selfTransitionBarrierMasks(true, false);
        int[] acquire = GllessFrameTarget.selfTransitionBarrierMasks(false, false);
        int[] merged = GllessFrameTarget.handbackMergedMasks(release, acquire);
        assertEquals(release[0] | acquire[0], merged[0]);
        assertEquals(release[1] | acquire[1], merged[1]);
        // Every access either barrier named is still named after the merge (issue #422 (a)).
        assertEquals(merged[0], merged[0] | release[0] | acquire[0]);
        assertEquals(merged[1], merged[1] | release[1] | acquire[1]);
        // Both steady-state pairs are non-empty, so the merged barrier is a real dependency, not a no-op.
        assertTrue(merged[0] != 0 && merged[1] != 0);
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
