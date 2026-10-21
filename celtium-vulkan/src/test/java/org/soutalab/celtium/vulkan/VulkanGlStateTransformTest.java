package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The transform path used to allocate a sixteen-float matrix per call - for glLoadIdentity and for every translate,
 * scale and rotate a tile entity renderer makes per part. These pin that the values are still what GL would produce and
 * that the reusable scratch cannot leak one call into the next.
 */
class VulkanGlStateTransformTest {

    private static VulkanGlState state() {
        return new VulkanGlState();
    }

    private static float[] topModelView(VulkanGlState state) {
        return state.peekMatrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
    }

    @Test
    void loadIdentityReplacesTheActiveMatrixWithTheIdentity() {
        VulkanGlState state = state();
        state.translate(3, 4, 5);
        state.loadIdentity();
        float[] m = topModelView(state);
        assertEquals(1.0f, m[0]);
        assertEquals(1.0f, m[5]);
        assertEquals(1.0f, m[10]);
        assertEquals(1.0f, m[15]);
        assertEquals(0.0f, m[12], "translation cleared");
        assertEquals(0.0f, m[13]);
        assertEquals(0.0f, m[14]);
    }

    @Test
    void twoTransformsInARowComposeInsteadOfReusingTheFirstOne() {
        VulkanGlState state = state();
        state.translate(1, 0, 0);
        state.translate(2, 0, 0);
        float[] m = topModelView(state);
        assertEquals(3.0f, m[12], 1.0e-6f, "translations compose");
        // The second call must not have inherited anything but the identity as its starting point.
        assertEquals(1.0f, m[0], 1.0e-6f);
        assertEquals(0.0f, m[1], 1.0e-6f);
    }

    @Test
    void scaleAndRotateStartFromTheIdentityScratchNotThePreviousMatrix() {
        VulkanGlState state = state();
        state.scale(2, 3, 4);
        state.loadIdentity();
        state.scale(1, 1, 1);
        float[] m = topModelView(state);
        assertEquals(1.0f, m[0], 1.0e-6f, "the earlier scale is gone after loadIdentity");
        assertEquals(1.0f, m[5], 1.0e-6f);
        assertEquals(1.0f, m[10], 1.0e-6f);
    }

    @Test
    void pushAndPopStillGiveEachStackEntryItsOwnArray() {
        VulkanGlState state = state();
        state.translate(5, 0, 0);
        state.pushMatrix();
        state.translate(5, 0, 0);
        assertEquals(10.0f, topModelView(state)[12], 1.0e-6f);
        state.popMatrix();
        assertEquals(5.0f, topModelView(state)[12], 1.0e-6f, "the popped entry kept its own values");
    }
}
