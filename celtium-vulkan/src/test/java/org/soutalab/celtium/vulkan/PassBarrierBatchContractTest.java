package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-function contract checks for the C2 (item 673) pass-start barrier batching in
 * {@link PassExecutor}: transitions may share one {@code vkCmdPipelineBarrier} call only when their
 * {@code (srcStage, dstStage)} pair is identical, so the grouping must never merge across a stage pair
 * (that would widen a stage), and the batched call count must be the number of distinct pairs.
 */
class PassBarrierBatchContractTest {

    @Test
    void colorInputsShareOneGroup() {
        int[] color = PassExecutor.transitionStages(VK10.VK_IMAGE_LAYOUT_GENERAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        assertEquals(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, color[0]);
        assertEquals(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, color[1]);
        int[] src = { color[0], color[0], color[0] };
        int[] dst = { color[1], color[1], color[1] };
        assertArrayEquals(new int[] { 0, 0, 0 }, PassExecutor.transitionGroups(src, dst));
        assertEquals(1, PassExecutor.transitionBarrierCallCount(src, dst, true));
        assertEquals(3, PassExecutor.transitionBarrierCallCount(src, dst, false));
    }

    @Test
    void depthInputsFormTheirOwnGroup() {
        int[] color = PassExecutor.transitionStages(VK10.VK_IMAGE_LAYOUT_GENERAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        int[] depth = PassExecutor.transitionStages(VK10.VK_IMAGE_LAYOUT_GENERAL,
                VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL, true);
        assertEquals(VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, depth[0]);
        assertEquals(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, depth[1]);
        int[] src = { color[0], depth[0], color[0] };
        int[] dst = { color[1], depth[1], color[1] };
        assertArrayEquals(new int[] { 0, 1, 0 }, PassExecutor.transitionGroups(src, dst));
        assertEquals(2, PassExecutor.transitionBarrierCallCount(src, dst, true));
    }

    @Test
    void undefinedSourceFormsItsOwnGroup() {
        int[] undefined = PassExecutor.transitionStages(VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        int[] general = PassExecutor.transitionStages(VK10.VK_IMAGE_LAYOUT_GENERAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        assertEquals(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, undefined[0]);
        int[] src = { undefined[0], general[0] };
        int[] dst = { undefined[1], general[1] };
        assertArrayEquals(new int[] { 0, 1 }, PassExecutor.transitionGroups(src, dst));
        assertEquals(2, PassExecutor.transitionBarrierCallCount(src, dst, true));
    }

    @Test
    void attachmentBoundTransitionsNeverMergeWithSampledOnes() {
        // The output transition's stage pair (to EARLY_FRAGMENT_TESTS / COLOR_ATTACHMENT_OUTPUT) must
        // stay separate from every input pair: merging would widen the output's stages.
        int[] output = PassExecutor.transitionStages(VK10.VK_IMAGE_LAYOUT_GENERAL,
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, false);
        int[] input = PassExecutor.transitionStages(VK10.VK_IMAGE_LAYOUT_GENERAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        int[] src = { input[0], output[0] };
        int[] dst = { input[1], output[1] };
        assertArrayEquals(new int[] { 0, 1 }, PassExecutor.transitionGroups(src, dst));
    }

    @Test
    void emptyInputSetRecordsNoCalls() {
        assertEquals(0, PassExecutor.transitionBarrierCallCount(new int[0], new int[0], true));
        assertEquals(0, PassExecutor.transitionBarrierCallCount(new int[0], new int[0], false));
    }
}
