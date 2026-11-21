package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shadow §3.4 attach hand-back predicate in {@link PostChainBridge}: a pass that attaches both a color
 * target (or MRT tuple) and a borrowed shared depth image must hand back BOTH, while skipping unrelated published
 * images.
 */
final class ShadowDepthHandBackContractTest {

    @Test
    void handBackMatchesAttachedDepthAsWellAsColor() {
        long colorImg = 0x1001L;
        long depthImg = 0x2002L;
        long otherImg = 0x3003L;

        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(true, colorImg, depthImg, null, colorImg));
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(true, colorImg, depthImg, null, depthImg));
        assertFalse(PostChainBridge.celtium$attachHandBackNeeded(true, colorImg, depthImg, null, otherImg));
    }

    @Test
    void handBackMatchesDepthAlongsideMrtColorArray() {
        long[] mrtColors = {0x1001L, 0x1002L};
        long depthImg = 0x2002L;
        long otherImg = 0x3003L;

        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(true, 0L, depthImg, mrtColors, 0x1002L));
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(true, 0L, depthImg, mrtColors, depthImg));
        assertFalse(PostChainBridge.celtium$attachHandBackNeeded(true, 0L, depthImg, mrtColors, otherImg));
    }

    @Test
    void samplingSideAlwaysVisitsAllPublishedImages() {
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(false, 0x1001L, 0x2002L, null, 0x9999L));
    }
}
