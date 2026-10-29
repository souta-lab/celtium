package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelMissingRecoveryTest {
    @Test void capturedButUncachedListGetsOneForcedReconciliation() {
        ModelGeometryCache cache = new ModelGeometryCache();
        assertTrue(cache.requestMissingCapturedListRebuild(144, 6, 17));
        assertFalse(cache.requestMissingCapturedListRebuild(144, 6, 17),
                "An unchanged missing list must not rebuild every frame");
        assertTrue(cache.requestMissingCapturedListRebuild(153, 6, 17),
                "A second list may still need recovery after a world switch");
        assertTrue(cache.requestMissingCapturedListRebuild(144, 6, 18),
                "A newly captured version must permit a fresh recovery");
    }

    @Test void neverCapturedOrInvalidListsDoNotTriggerRebuild() {
        ModelGeometryCache cache = new ModelGeometryCache();
        assertFalse(cache.requestMissingCapturedListRebuild(-1, 6, 17));
        assertFalse(cache.requestMissingCapturedListRebuild(144, 0, 17));
        assertTrue(cache.requestMissingCapturedListRebuild(144, 6, 17));
    }
}
