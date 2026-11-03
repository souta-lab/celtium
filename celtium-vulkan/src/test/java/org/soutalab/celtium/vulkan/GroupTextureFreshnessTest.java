package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fast merge's texture-freshness decision, checked without a device.
 * <p>
 * The merge used to gate on the stub's <em>global</em> write epoch, so a write to any texture anywhere refused the
 * merge of a group that samples a different one. In a world scene that is every frame, and the cost is the whole slow
 * path - the supply, the preflight and the group open - for a batch whose material had not changed at all. Measured on
 * leg {@code r39a-dist}: 894,800 of 2,005,142 offers merged (44.6%), and the slow path's supply and prepare ran on 51%
 * of offers.
 * <p>
 * What these tests can prove: the table the decision implements, including the case the old form got wrong. They prove
 * nothing about a frame - the serials come from the stub, and a green test here is not evidence that any merge happened.
 */
class GroupTextureFreshnessTest {

    @Test
    void anUnmovedEpochIsFreshWithoutAskingTheTexture() {
        // The cheap gate: no texture anywhere was written, so the group's mirror cannot be stale.
        assertTrue(VulkanTerrainGpuBackend.celtium$groupTextureUnchanged(true, 42, 7L, 99L),
                "an unchanged global epoch answers yes without a per-texture lookup");
        assertTrue(VulkanTerrainGpuBackend.celtium$groupTextureUnchanged(true, 0, Long.MIN_VALUE, Long.MIN_VALUE),
                "and it answers yes for a textureless group too");
    }

    @Test
    void aWriteToAnotherTextureNoLongerRefusesTheMerge() {
        // The defect: the epoch moved (some other texture was written) but this group's serial is unchanged.
        assertTrue(VulkanTerrainGpuBackend.celtium$groupTextureUnchanged(false, 42, 7L, 7L),
                "a write to a different texture must not stale this group's mirror");
    }

    @Test
    void aWriteToThisGroupsTextureStillRefusesTheMerge() {
        // The rule the gate exists for: a write between two batches of one texture must not be merged past.
        assertFalse(VulkanTerrainGpuBackend.celtium$groupTextureUnchanged(false, 42, 7L, 8L),
                "the group's own texture moved, so the merge would draw content the game had not written yet");
    }

    @Test
    void aTexturelessGroupKeepsTheGlobalAnswer() {
        // Nothing to compare, so the epoch is the only fact - and it moved, which is what the slow path recorded.
        assertFalse(VulkanTerrainGpuBackend.celtium$groupTextureUnchanged(false, 0, Long.MIN_VALUE, Long.MIN_VALUE),
                "a group with no texture has no serial to clear it");
    }

    @Test
    void aTextureThatGainedItsFirstCaptureIsNotFresh() {
        // The group opened before the texture had any capture (serial MIN_VALUE); the offer arrives after one exists.
        assertFalse(VulkanTerrainGpuBackend.celtium$groupTextureUnchanged(false, 42, Long.MIN_VALUE, 3L),
                "a capture that appeared after the group opened is a write the group has not seen");
    }
}
