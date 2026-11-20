package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;

/**
 * Which reason a refused depth publication reports (item 537 wall (b1)).
 * <p>
 * The shared depth store entry for a framebuffer's depth texture exists before the pair that draws into the
 * image first declares a layout, so the first prepare that tries to publish the name is refused with image,
 * view and extent all present and only the layout record still UNDEFINED - and the next prepare, after the
 * owner declared its layout, publishes. That transient wore the standing refusal's name
 * ({@code depth-not-in-store}), so a leg's log could not tell a self-healing first-frame race from a depth
 * image that is really absent. The split names the layout-only case {@code depth-layout-unrecorded}.
 * <p>
 * What these tests prove: the decision table of {@code celtium$depthPublishRefusalReason} - the layout-only
 * tuple gets the new reason and every other incompleteness keeps the standing one. The publication decision
 * itself (both reasons refuse) is untouched by the split and is not what is pinned here.
 */
class DepthPublishRefusalReasonTest {

    private static final int[] EXTENT = {1280, 720};

    @Test
    void onlyTheLayoutUnrecordedIsNamedApart() {
        // The Tier 1 measurement: image=0x209..., view=0x20b..., extent=1280x720, layout=0 at the refusal,
        // published with layout=3 two seconds later.
        assertEquals("depth-layout-unrecorded",
                VulkanTerrainGpuBackend.celtium$depthPublishRefusalReason(0x209L, 0x20bL, EXTENT,
                        VK_IMAGE_LAYOUT_UNDEFINED),
                "image, view and extent present with only the layout unrecorded is the transient");
    }

    @Test
    void aMissingImageKeepsTheStandingReason() {
        assertEquals("depth-not-in-store",
                VulkanTerrainGpuBackend.celtium$depthPublishRefusalReason(0L, 0x20bL, EXTENT,
                        VK_IMAGE_LAYOUT_UNDEFINED),
                "an unrecorded layout over a missing image is still a missing image, not the transient");
        assertEquals("depth-not-in-store",
                VulkanTerrainGpuBackend.celtium$depthPublishRefusalReason(0L, 0x20bL, EXTENT,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL));
    }

    @Test
    void aMissingViewOrExtentKeepsTheStandingReason() {
        assertEquals("depth-not-in-store",
                VulkanTerrainGpuBackend.celtium$depthPublishRefusalReason(0x209L, 0L, EXTENT,
                        VK_IMAGE_LAYOUT_UNDEFINED));
        assertEquals("depth-not-in-store",
                VulkanTerrainGpuBackend.celtium$depthPublishRefusalReason(0x209L, 0x20bL, null,
                        VK_IMAGE_LAYOUT_UNDEFINED));
    }

    @Test
    void aRecordedLayoutIsNeverTheTransient() {
        // With a layout recorded the refusal condition does not fire at all today; the reason function must
        // still not name that tuple the transient if another part is missing.
        assertEquals("depth-not-in-store",
                VulkanTerrainGpuBackend.celtium$depthPublishRefusalReason(0x209L, 0L, EXTENT,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL));
    }
}
