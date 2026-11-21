package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which names the mod-program sampler resolution may materialize a mirror for (item 537, the
 * centre-depth alt texture).
 * <p>
 * The measured case is Iris's {@code CenterDepthSampler}: its alt texture is a standalone 1x1 colour
 * texture the game sized with {@code glTexImage2D} and no framebuffer attaches, written only by a copy
 * out of the sampler's own framebuffer. No supply route fills such a name - no upload carries game
 * pixels, no framebuffer publishes it, no depth record names it - so the sampler resolution refused
 * the draw, the framebuffer's pair was never acquired, and the copy that would have filled the
 * texture was refused by the virgin-pair guard in turn. GL samples a texture from the moment its
 * storage exists, so the resolution materializes the mirror at the recorded extent instead.
 * <p>
 * What these tests prove: the decision table of {@code celtium$standaloneTextureMaterializable} - the
 * alt-texture tuple materializes, and every tuple with an owner (a usable mirror already, a colour
 * attachment the publish routes supply, a depth record whose publication heals on the next prepare,
 * or game pixels the capture supplies) or without recorded storage does not.
 */
class StandaloneTextureMaterializeTest {

    @Test
    void theAltTextureTupleMaterializes() {
        // No mirror, no attachment, no depth record, no game pixels, recorded 1x1 storage.
        assertTrue(VulkanTerrainGpuBackend.celtium$standaloneTextureMaterializable(
                false, false, false, false, 1, 1),
                "a standalone allocated texture with no supply route materializes");
    }

    @Test
    void aUsableMirrorIsLeftAlone() {
        assertFalse(VulkanTerrainGpuBackend.celtium$standaloneTextureMaterializable(
                true, false, false, false, 1, 1),
                "a name that already resolves to an image needs no materialization");
    }

    @Test
    void aColourAttachmentKeepsItsPublishRoute() {
        assertFalse(VulkanTerrainGpuBackend.celtium$standaloneTextureMaterializable(
                false, true, false, false, 1280, 720),
                "a framebuffer's colour attachment is supplied by publish/adopt, never shadowed");
    }

    @Test
    void aDepthRecordKeepsItsPublicationRetry() {
        // The depthtex1 shape (item 537 wall (b1)): publication refused only because the layout is
        // unrecorded; the next prepare publishes. Materializing would draw an empty depth instead.
        assertFalse(VulkanTerrainGpuBackend.celtium$standaloneTextureMaterializable(
                false, false, true, false, 1280, 720),
                "a name with a depth record waits for its publication, it is not materialized");
    }

    @Test
    void gamePixelsKeepTheCaptureAsSupply() {
        assertFalse(VulkanTerrainGpuBackend.celtium$standaloneTextureMaterializable(
                false, false, false, true, 256, 256),
                "a level the game wrote into is filled from the capture, not from zeros");
    }

    @Test
    void noRecordedStorageCannotBeSized() {
        assertFalse(VulkanTerrainGpuBackend.celtium$standaloneTextureMaterializable(
                false, false, false, false, 0, 0),
                "no recorded extent, no mirror: the size would be a guess");
        assertFalse(VulkanTerrainGpuBackend.celtium$standaloneTextureMaterializable(
                false, false, false, false, 1280, 0),
                "a half-recorded extent is no extent");
    }
}
