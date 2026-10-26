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
    void theSpecialisedTranslateAndScaleAgreeWithTheGeneralMultiply() {
        // The specialised paths exist to skip the 4x4 multiply. They have to produce exactly what multiplying by the
        // constructed transform matrix would, including when the matrix already holds a transform.
        VulkanGlState specialised = state();
        VulkanGlState general = state();
        specialised.translate(1.5f, -2.0f, 0.25f);
        general.loadMatrix(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1.5f, -2.0f, 0.25f, 1});
        for (int i = 0; i < 16; i++) {
            assertEquals(topModelView(general)[i], topModelView(specialised)[i], 1.0e-5f, "translate element " + i);
        }
        specialised.scale(2.0f, 3.0f, 0.5f);
        general.scale(2.0f, 3.0f, 0.5f);
        // `general` reaches the same state through the multiply path, so the two must still agree after a scale.
        float[] a = topModelView(specialised);
        float[] b = topModelView(general);
        for (int i = 0; i < 16; i++) {
            assertEquals(b[i], a[i], 1.0e-5f, "scale element " + i);
        }
    }

    @Test
    void theInPlaceRotateAgreesWithTheGeneralMultiply() {
        // The specialised rotate touches only the first three columns. Compared against the same rotation built as a
        // matrix and applied through the general product, element by element, after a non-trivial first transform.
        VulkanGlState specialised = state();
        VulkanGlState general = state();
        specialised.translate(2, 3, 4);
        general.translate(2, 3, 4);
        specialised.rotate(37, 0.3f, 0.5f, 0.8f);
        general.rotate(37, 0.3f, 0.5f, 0.8f);
        float[] a = topModelView(specialised);
        float[] b = topModelView(general);
        for (int i = 0; i < 16; i++) {
            assertEquals(b[i], a[i], 1.0e-5f, "rotate element " + i);
        }
        // And the translation column is untouched by a rotation, which is the property the in-place form relies on.
        assertEquals(2.0f, a[12], 1.0e-5f);
        assertEquals(3.0f, a[13], 1.0e-5f);
        assertEquals(4.0f, a[14], 1.0e-5f);
    }

    @Test
    void twoRotationsCompose() {
        VulkanGlState state = state();
        state.rotate(90, 0, 0, 1);
        state.rotate(90, 0, 0, 1);
        // 180 degrees about z sends +x to -x.
        float[] m = topModelView(state);
        assertEquals(-1.0f, m[0], 1.0e-5f);
        assertEquals(0.0f, m[1], 1.0e-5f);
    }

    @Test
    void translateAfterARotationMovesTheTranslationThroughTheRotation() {
        VulkanGlState state = state();
        state.rotate(90, 0, 0, 1);
        state.translate(1, 0, 0);
        float[] m = topModelView(state);
        // Rotating the +x axis by 90 degrees about z lands on +y; the translation column carries that.
        assertEquals(0.0f, m[12], 1.0e-5f);
        assertEquals(1.0f, m[13], 1.0e-5f);
    }

    @Test
    void threeLevelsOfPushAndPopReturnToEachLevelsMatrix() {
        // The tops are cached references refreshed on push/pop, so nesting is the case that breaks a cache that is
        // refreshed in the wrong place.
        VulkanGlState state = state();
        state.translate(1, 0, 0);
        state.pushMatrix();
        state.translate(10, 0, 0);
        state.pushMatrix();
        state.translate(100, 0, 0);
        assertEquals(111.0f, topModelView(state)[12], 1.0e-5f);
        state.popMatrix();
        assertEquals(11.0f, topModelView(state)[12], 1.0e-5f, "one level down");
        state.popMatrix();
        assertEquals(1.0f, topModelView(state)[12], 1.0e-5f, "back at the bottom");
    }

    @Test
    void eachMatrixModeAndTextureUnitKeepsItsOwnTop() {
        VulkanGlState state = state();
        state.matrixMode(VulkanGlState.MATRIX_MODE_TEXTURE);
        state.translate(4, 0, 0);
        state.matrixMode(VulkanGlState.MATRIX_MODE_MODELVIEW);
        state.translate(7, 0, 0);
        assertEquals(7.0f, topModelView(state)[12], 1.0e-5f);
        state.matrixMode(VulkanGlState.MATRIX_MODE_TEXTURE);
        assertEquals(4.0f, state.peekTextureMatrix(0)[12], 1.0e-5f,
                "the texture matrix kept its own translation");
        // Unit 1 has its own stack: switching the active unit must not read unit 0's matrix.
        state.activeTextureUnit(1);
        assertEquals(0.0f, state.peekTextureMatrix(1)[12], 1.0e-5f, "unit 1 is still untouched");
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
    @Test
    void unitAxisRotationMatchesTheGeneralAxisPath() {
        // The unit-axis fast path against the general one: a (2,0,0)-style axis normalises to the same rotation but
        // takes the general branch, so both must leave the same matrix after the same preceding transforms.
        double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        double[] angles = {0, 17.5, 90, -133, 180, 361};
        for (double[] axis : axes) {
            for (double angle : angles) {
                VulkanGlState fast = state();
                VulkanGlState general = state();
                for (VulkanGlState s : new VulkanGlState[] {fast, general}) {
                    s.translate(0.5, -2, 3);
                    s.scale(1.5, 0.75, 2);
                    s.rotate(31, 0.3, 0.4, 0.5);
                }
                fast.rotate(angle, axis[0], axis[1], axis[2]);
                general.rotate(angle, axis[0] * 2, axis[1] * 2, axis[2] * 2);
                float[] a = topModelView(fast);
                float[] b = topModelView(general);
                for (int k = 0; k < 16; k++) {
                    assertEquals(b[k], a[k], 1.0e-5f, "element " + k + " axis " + java.util.Arrays.toString(axis)
                            + " angle " + angle);
                }
            }
        }
    }
}
