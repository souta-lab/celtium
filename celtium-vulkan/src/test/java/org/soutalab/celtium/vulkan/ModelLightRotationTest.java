package org.soutalab.celtium.vulkan;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The model replay's lit payload is now proven by its two inputs instead of being rebuilt and compared, so the
 * geometry it is built from has to be testable on its own: if the inverse were wrong, a hit would write the wrong
 * twenty floats and no guard would notice. These cases pin the rotation mapping (the modelview's upper-left 3x3, in
 * {@link ModelReplayInputCache#copyRotation}'s order) and the inverse's arithmetic - {@code R * R^-1 = I} - which is
 * the part a transcription can get wrong.
 */
class ModelLightRotationTest {

    /** The nine-float rotation {@link ModelReplayInputCache#copyRotation} takes from a full modelview. */
    private static float[] rotationOf(Matrix4f matrix) {
        float[] rotation = new float[9];
        ModelReplayInputCache.copyRotation(matrix.get(new float[16]), rotation);
        return rotation;
    }

    /** Column-major 3x3 product of {@code a} and {@code b}, the layout both the rotation and its inverse use. */
    private static float[] multiply(float[] a, float[] b) {
        float[] product = new float[9];
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 3; k++) {
                    sum += a[k * 3 + row] * b[column * 3 + k];
                }
                product[column * 3 + row] = sum;
            }
        }
        return product;
    }

    private static void assertIdentity(float[] product, float tolerance, String what) {
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                float expected = column == row ? 1.0f : 0.0f;
                assertEquals(expected, product[column * 3 + row], tolerance,
                        what + ": column " + column + " row " + row);
            }
        }
    }

    @Test void theRotationIsTheModelviewsUpperLeftThreeByThree() {
        float[] matrix = new float[16];
        for (int i = 0; i < 16; i++) {
            matrix[i] = i + 1;
        }
        float[] rotation = new float[9];
        ModelReplayInputCache.copyRotation(matrix, rotation);
        // Column-major indices 0..2, 4..6 and 8..10: what the payload's inverse reads, and nothing else.
        assertArrayEquals(new float[] {1, 2, 3, 5, 6, 7, 9, 10, 11}, rotation, 0.0f);
    }

    @Test void theInverseTimesTheRotationIsTheIdentity() {
        Matrix4f[] matrices = {
                new Matrix4f(),
                new Matrix4f().rotateX(0.5f),
                new Matrix4f().rotateYXZ(-0.8f, 0.4f, 1.1f),
                new Matrix4f().rotateXYZ(0.0f, (float) Math.PI / 2.0f, 0.0f),
        };
        for (Matrix4f matrix : matrices) {
            float[] rotation = rotationOf(matrix);
            float[] inverse = new float[9];
            VulkanTerrainGpuBackend.celtium$invertRotation(rotation, inverse);
            assertIdentity(multiply(rotation, inverse), 1.0e-4f, matrix.toString());
            // The inverse must be usable as the column-major 3x3 the payload multiplies the light directions with, so
            // the same check from the other side: this is the product that has to be the identity for either order.
            assertIdentity(multiply(inverse, rotation), 1.0e-4f, matrix + " (inverse on the left)");
        }
    }

    @Test void aSingularRotationInvertsToTheIdentity() {
        float[] singular = {1, 2, 3, 2, 4, 6, 0, 0, 0};
        float[] inverse = new float[9];
        VulkanTerrainGpuBackend.celtium$invertRotation(singular, inverse);
        assertArrayEquals(new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, inverse, 0.0f,
                "a degenerate draw keeps identity directions rather than infinities");
    }

    @Test void theInverseIsWrittenInPlaceAndLeavesNothingBehind() {
        float[] rotation = rotationOf(new Matrix4f().rotateZ(0.7f));
        float[] inverse = new float[9];
        java.util.Arrays.fill(inverse, 99.0f);
        VulkanTerrainGpuBackend.celtium$invertRotation(rotation, inverse);
        for (float value : inverse) {
            assertNotEquals(99.0f, value, "every slot is written");
        }
    }
}
