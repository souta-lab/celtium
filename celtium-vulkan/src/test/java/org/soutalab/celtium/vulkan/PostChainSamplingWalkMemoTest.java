package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.hal.PublishedHandBack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 673: the published sampling walk's repeat decision, pinned as a pure key comparison.
 * <p>
 * The memo may answer a sampling-side walk only when every component of the state the walk reads
 * stands unchanged since the walk that settled it: the switch, the frame, the publication version,
 * the pair generation, the count of foreign layout barriers actually recorded anywhere (a hand-back
 * between the two walks advances it), and the attach context the walk skips names for. Each test
 * below moves exactly one component.
 */
class PostChainSamplingWalkMemoTest {

    private static boolean redundant(boolean enabled, boolean general, boolean hasAttachImages,
                                     long frame, long publications, long pairs, long transitions,
                                     long attachColor, long attachDepth, long passColor, long passDepth) {
        return PublishedHandBack.celtium$samplingWalkRedundant(enabled, general, hasAttachImages,
                frame, 42L, publications, 7L, pairs, 11L, transitions, 5L,
                attachColor, 100L, attachDepth, 200L, passColor, 300L, passDepth, 400L);
    }

    @Test
    void anIdenticalKeyIsRedundant() {
        assertTrue(redundant(true, false, false, 42L, 7L, 11L, 5L, 100L, 200L, 300L, 400L));
    }

    @Test
    void theSwitchOffNeverSkips() {
        assertFalse(redundant(false, false, false, 42L, 7L, 11L, 5L, 100L, 200L, 300L, 400L));
    }

    @Test
    void theAttachSideNeverSkips() {
        assertFalse(redundant(true, true, false, 42L, 7L, 11L, 5L, 100L, 200L, 300L, 400L));
    }

    @Test
    void theArrayAttachShapeNeverSkips() {
        assertFalse(redundant(true, false, true, 42L, 7L, 11L, 5L, 100L, 200L, 300L, 400L));
    }

    @Test
    void aNewFrameWalksAgain() {
        assertFalse(redundant(true, false, false, 43L, 7L, 11L, 5L, 100L, 200L, 300L, 400L));
    }

    @Test
    void aNewPublicationWalksAgain() {
        assertFalse(redundant(true, false, false, 42L, 8L, 11L, 5L, 100L, 200L, 300L, 400L));
    }

    @Test
    void aRecreatedPairWalksAgain() {
        assertFalse(redundant(true, false, false, 42L, 7L, 12L, 5L, 100L, 200L, 300L, 400L));
    }

    @Test
    void aRecordedForeignTransitionWalksAgain() {
        // The hand-back between the two walks: it records a barrier, the count advances, the memo dies.
        assertFalse(redundant(true, false, false, 42L, 7L, 11L, 6L, 100L, 200L, 300L, 400L));
    }

    @Test
    void aDifferentAttachContextWalksAgain() {
        assertFalse(redundant(true, false, false, 42L, 7L, 11L, 5L, 101L, 200L, 300L, 400L));
        assertFalse(redundant(true, false, false, 42L, 7L, 11L, 5L, 100L, 201L, 300L, 400L));
        assertFalse(redundant(true, false, false, 42L, 7L, 11L, 5L, 100L, 200L, 301L, 400L));
        assertFalse(redundant(true, false, false, 42L, 7L, 11L, 5L, 100L, 200L, 300L, 401L));
    }

    @Test
    void anUnfilledMemoNeverSkips() {
        assertFalse(PublishedHandBack.celtium$samplingWalkRedundant(true, false, false,
                42L, Long.MIN_VALUE, 7L, 7L, 11L, 11L, 5L, 5L,
                100L, 100L, 200L, 200L, 300L, 300L, 400L, 400L));
    }
}
