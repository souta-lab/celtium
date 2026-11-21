package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The condition under which a failed lazy supply drops the mirror it just created.
 * <p>
 * The supply creates the registry entry before it reads the capture, and the read can refuse: the measured case is
 * an item-frame map whose data packet has not arrived yet. Its {@code DynamicTexture} allocated the level with a
 * NULL data pointer, so the stub holds an extent but no game pixels, the supply decision reads "a capture exists"
 * and proceeds, and {@code uploadFromGlTexture} then throws the capture-miss. With the empty entry left behind,
 * every later lookup returned it as a plain hit and the preflight refused the map's quad with "no complete native
 * image" for the rest of the run - the deferral a mirror-less name gets, and the announcement that would have
 * filled the mirror, both describe a state the registry no longer reported. Dropping the created entry returns the
 * name to that unmirrored state.
 * <p>
 * The two facts the rule reads are the ones that separate that case from its neighbours: a mirror that was already
 * there holds real partial state its owner may still complete (the audit A04 completion path runs through this same
 * supply), and a mirror the failed upload nevertheless filled is not the failure's to undo.
 * <p>
 * What these tests can prove: the truth table the drop rests on. They prove nothing about a frame - the registry
 * side of the drop (identity and content guards, retire-queue disposal) is exercised by the fixture legs, not here.
 */
class SupplyFailureMirrorDropTest {

    @Test
    void aMirrorCreatedByTheFailedSupplyAndStillUnfilledIsDropped() {
        assertTrue(VulkanTerrainGpuBackend.celtium$supplyFailureDropsMirror(false, false));
    }

    @Test
    void aPreExistingPartialMirrorStays() {
        // The entry was already there with partial content: completing or keeping it is its owner's business.
        assertFalse(VulkanTerrainGpuBackend.celtium$supplyFailureDropsMirror(true, false));
    }

    @Test
    void aMirrorTheUploadFilledStays() {
        // The failure came after the content landed (a later step of the supply), so there is nothing to undo.
        assertFalse(VulkanTerrainGpuBackend.celtium$supplyFailureDropsMirror(false, true));
    }

    @Test
    void aPreExistingCompleteMirrorStays() {
        assertFalse(VulkanTerrainGpuBackend.celtium$supplyFailureDropsMirror(true, true));
    }
}
