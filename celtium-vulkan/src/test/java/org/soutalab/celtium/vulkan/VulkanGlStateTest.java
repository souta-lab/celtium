package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The state model that replaces the GlStateManager calls a mod makes. Two properties matter for the API swap: the
 * matrix stack has to behave like the one mods already use (push/pop, multiplication order), and the pipeline key has
 * to change exactly when the pipeline would have to.
 */
class VulkanGlStateTest {
    @Test
    void pushAndPopRestoreTheActiveMatrix() {
        VulkanGlState state = new VulkanGlState();
        float[] identity = state.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
        state.pushMatrix();
        state.loadMatrix(translation(2.0f, 3.0f, 4.0f));
        assertArrayEquals(translation(2.0f, 3.0f, 4.0f), state.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW));
        state.popMatrix();
        assertArrayEquals(identity, state.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW));
    }

    @Test
    void projectionAndModelViewStacksAreIndependent() {
        VulkanGlState state = new VulkanGlState();
        state.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        state.pushMatrix();
        state.loadMatrix(translation(1.0f, 0.0f, 0.0f));
        assertArrayEquals(translation(1.0f, 0.0f, 0.0f), state.matrix(VulkanGlState.MATRIX_MODE_PROJECTION));
        assertArrayEquals(identity(), state.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW));
        state.matrixMode(VulkanGlState.MATRIX_MODE_MODELVIEW);
        state.popMatrix();
        assertArrayEquals(identity(), state.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW));
    }

    @Test
    void multiplicationFollowsTheColumnMajorOrderMinecraftUses() {
        VulkanGlState state = new VulkanGlState();
        state.loadMatrix(translation(1.0f, 2.0f, 3.0f));
        state.multiplyMatrix(scale(2.0f, 2.0f, 2.0f));
        float[] product = state.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
        // translate(1,2,3) * scale(2) keeps the translation and doubles the basis, column-major.
        assertEquals(2.0f, product[0], 1e-5f);
        assertEquals(2.0f, product[5], 1e-5f);
        assertEquals(1.0f, product[12], 1e-5f);
        assertEquals(2.0f, product[13], 1e-5f);
    }

    @Test
    void thePipelineKeyTracksPipelineStateAndIgnoresPushConstantState() {
        VulkanGlState state = new VulkanGlState();
        long base = state.pipelineKey(42);
        state.color(0.5f, 0.5f, 0.5f, 0.5f);
        assertEquals(base, state.pipelineKey(42), "colour is a push constant, not pipeline state");
        state.blendEnabled(true);
        assertNotEquals(base, state.pipelineKey(42), "enabling blend needs another pipeline");
        long blended = state.pipelineKey(42);
        state.blendFunc(VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO, VulkanGlState.BLEND_ONE,
                VulkanGlState.BLEND_ZERO);
        assertNotEquals(blended, state.pipelineKey(42), "a different blend function needs another pipeline");
        state.blendEnabled(false);
        assertEquals(base, state.pipelineKey(42), "disabling blend returns to the earlier pipeline");
    }

    @Test
    void theKeyDependsOnTheVertexFormat() {
        VulkanGlState state = new VulkanGlState();
        assertNotEquals(state.pipelineKey(1), state.pipelineKey(2));
        assertTrue(state.describe().contains("depthTest"));
    }

    private static float[] identity() {
        float[] matrix = new float[16];
        matrix[0] = 1.0f;
        matrix[5] = 1.0f;
        matrix[10] = 1.0f;
        matrix[15] = 1.0f;
        return matrix;
    }

    private static float[] translation(float x, float y, float z) {
        float[] matrix = identity();
        matrix[12] = x;
        matrix[13] = y;
        matrix[14] = z;
        return matrix;
    }

    private static float[] scale(float x, float y, float z) {
        float[] matrix = identity();
        matrix[0] = x;
        matrix[5] = y;
        matrix[10] = z;
        return matrix;
    }
}
