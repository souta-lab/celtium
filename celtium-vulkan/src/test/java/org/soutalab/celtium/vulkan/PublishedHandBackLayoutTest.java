package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.hal.PublishedHandBack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;

/**
 * Which layout the published-name walk moves a published image to - and reports to its owner - in each direction.
 * <p>
 * The published depth image's layout was spoken in three vocabularies (item 537 wall (c)): its descriptor declares
 * {@code SHADER_READ_ONLY_OPTIMAL}, the walk's hand-back and its report to the shared depth store were synthesised
 * from a {@code GENERAL}/{@code SHADER_READ_ONLY} binary, and its owner - the frame target's pair - attaches it in
 * {@code DEPTH_STENCIL_ATTACHMENT_OPTIMAL}, a value the binary never produces. The walk recorded one transition in
 * the measured Tier 1 run (attachment to read) and no hand-back at all, and the store record could name
 * {@code GENERAL} over an image sitting in the attachment layout: the two
 * {@code VUID-vkCmdDraw-None-09600} on that image, one in each direction.
 * <p>
 * What these tests prove: the decision table of {@code celtium$publishedHandBackLayout} - the depth hand-back
 * answers the owner's layout, the colour answers are the ones the walk has always used, and the sampling side is
 * the read layout for both. They prove nothing about a frame: the walk's use of the table, and a barrier actually
 * recorded, are the confirmation leg's business (the design's meter 6), not this test's.
 */
class PublishedHandBackLayoutTest {

    @Test
    void aDepthNameIsHandedBackToItsOwnersLayout() {
        // The wall (c) case: general=true over a depth publication answers the owner's attachment layout, not
        // GENERAL - GENERAL is a value the depth owner's records never use.
        assertEquals(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                PublishedHandBack.celtium$publishedHandBackLayout(true, true,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL),
                "a depth hand-back goes to the owner's attachment layout");
    }

    @Test
    void theDepthAnswerIsTheOwnerLayoutGivenNotAConstant() {
        // The function answers what it is given: an owner whose layout is the transfer layout gets that back, so
        // the walk's supply (today DEPTH_RENDER_LAYOUT) is the only place the concrete value lives.
        assertEquals(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                PublishedHandBack.celtium$publishedHandBackLayout(true, true, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL),
                "the depth hand-back passes the owner layout through");
    }

    @Test
    void aDepthNameSamplesInTheReadLayout() {
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                PublishedHandBack.celtium$publishedHandBackLayout(true, false,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL),
                "the sampling side is the read layout for depth too; the owner layout is ignored there");
    }

    @Test
    void theColourAnswersAreUnchanged() {
        // The colour lifecycle the pair was fixed into (sampled -> read-only, attached -> general) is the existing
        // behaviour, pinned so the depth split cannot move it; the owner layout is not a colour input.
        assertEquals(VK_IMAGE_LAYOUT_GENERAL,
                PublishedHandBack.celtium$publishedHandBackLayout(false, true,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL),
                "a colour hand-back stays in GENERAL");
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                PublishedHandBack.celtium$publishedHandBackLayout(false, false,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL),
                "a colour sampling transition stays in the read layout");
    }
}
