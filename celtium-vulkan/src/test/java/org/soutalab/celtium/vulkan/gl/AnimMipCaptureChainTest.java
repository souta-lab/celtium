package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Truth-table contract for {@link GlTextureCaptures#celtium$chainRegionMatches}: a tex-sub-image call is the
 * {@code level}-th upload of an announced patch's mip chain only when its region is the announcement's
 * region scaled by that level. Every other shape - a different level's region, an unscaled repeat of the
 * level-0 region, a vanished extent - must not match, so the capture runs and no pixels are lost.
 * <p>
 * The announcement used below is a 16x16 sprite patch at (16, 32), whose producer chain is
 * (16,32,16x16), (8,16,8x8), (4,8,4x4), (2,4,2x2), (1,2,1x1).
 */
class AnimMipCaptureChainTest {

    @Test
    void levelZeroIsTheAnnouncedRegionItself() {
        assertTrue(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 0, 16, 32, 16, 16));
    }

    @Test
    void everyChainLevelMatchesAtItsScaledRegion() {
        assertTrue(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 1, 8, 16, 8, 8));
        assertTrue(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 2, 4, 8, 4, 4));
        assertTrue(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 3, 2, 4, 2, 2));
        assertTrue(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 4, 1, 2, 1, 1));
    }

    @Test
    void aRegionScaledForAnotherLevelDoesNotMatch() {
        // Level 1's region offered as level 2, and level 2's offered as level 1: the walk is ordered, and a
        // call that arrives out of order is some other upload, not this chain's next link.
        assertFalse(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 2, 8, 16, 8, 8));
        assertFalse(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 1, 4, 8, 4, 4));
    }

    @Test
    void anUnscaledRepeatOfLevelZeroDoesNotMatchAboveLevelZero() {
        assertFalse(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 1, 16, 32, 16, 16));
    }

    @Test
    void aShiftedOrResizedRegionDoesNotMatch() {
        assertFalse(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 1, 9, 16, 8, 8));
        assertFalse(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 1, 8, 16, 8, 7));
        assertFalse(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 0, 16, 32, 15, 16));
    }

    @Test
    void aVanishedExtentNeverMatches() {
        // Level 5 of a 16x16 patch scales to 0x0: the producer's walk stops before it, and a zero-size call
        // is never this chain's link.
        assertFalse(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, 5, 0, 1, 0, 0));
    }

    @Test
    void aNegativeLevelNeverMatches() {
        assertFalse(GlTextureCaptures.celtium$chainRegionMatches(16, 32, 16, 16, -1, 32, 64, 32, 32));
    }
}
