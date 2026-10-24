package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The light matrix is worth testing without a device because each property below is what a wrong shadow map looks
 * like: an off-centre box, a depth that runs the wrong way, or a light that is not vertical mapping to a degenerate
 * basis.
 */
class ShadowLightMatrixTest {

    private static final float[] A = new float[4];

    private static float[] clip(float[] matrix, float x, float y, float z) {
        return ShadowLightMatrix.transform(matrix, x, y, z);
    }

    @Test
    void theCentreOfTheBoxLandsInTheMiddleOfTheMap() {
        float[] matrix = ShadowLightMatrix.viewProjection(0, 64, 0, 0, 1, 0, 32, 96);
        float[] centre = clip(matrix, 0, 64, 0);
        float w = centre[3];
        assertTrue(w > 0.0f, "the centre must be in front of the light, w=" + w);
        assertEquals(0.0f, centre[0] / w, 1.0e-4f);
        assertEquals(0.0f, centre[1] / w, 1.0e-4f);
        // The centre sits half the depth range in front of the eye, so its depth is somewhere inside (0, 1) rather than
        // at either end.
        float depth = centre[2] / w;
        assertTrue(depth > 0.05f && depth < 0.95f, "centre depth out of range: " + depth);
    }

    @Test
    void somethingTowardsTheSunIsCloserToTheLight() {
        float[] matrix = ShadowLightMatrix.viewProjection(0, 64, 0, 0, 1, 0, 32, 96);
        float[] centre = clip(matrix, 0, 64, 0);
        float[] higher = clip(matrix, 0, 80, 0);
        float centreDepth = centre[2] / centre[3];
        float higherDepth = higher[2] / higher[3];
        assertTrue(higherDepth < centreDepth,
                "a point towards the sun must be nearer the light: " + higherDepth + " vs " + centreDepth);
    }

    @Test
    void theHalfExtentReachesTheEdgeOfTheClippedArea() {
        float[] matrix = ShadowLightMatrix.viewProjection(0, 64, 0, 0, 1, 0, 32, 96);
        // World +x is the box's "right" axis for a light straight above, so a point at half the extent in x should land
        // at half the clip range.
        float[] edge = clip(matrix, 16, 64, 0);
        float x = edge[0] / edge[3];
        assertEquals(0.5f, x, 1.0e-3f);
        float[] outside = clip(matrix, 40, 64, 0);
        assertTrue(Math.abs(outside[0] / outside[3]) > 1.0f,
                "a point past the half extent must fall outside the clipped box");
    }

    @Test
    void depthStaysInsideTheVulkanRangeNearTheCentre() {
        // The box is axis-aligned in *light* space, so a world-axis corner at the full half-extent is not necessarily
        // inside it. Points within a fraction of the extent around the centre are, and those are what the terrain near
        // the player occupies.
        float[] matrix = ShadowLightMatrix.viewProjection(100, 70, -40, 0.3f, 0.9f, 0.2f, 24, 80);
        float[] offsets = {-10.0f, 0.0f, 10.0f};
        for (float dx : offsets) {
            for (float dy : offsets) {
                for (float dz : offsets) {
                    float[] out = clip(matrix, 100 + dx, 70 + dy, -40 + dz);
                    float w = out[3];
                    assertTrue(w > 0.0f, "point behind the light: " + w);
                    float depth = out[2] / w;
                    assertTrue(depth >= 0.0f && depth <= 1.0f, "depth " + depth + " at " + dx + "," + dy + "," + dz);
                    assertTrue(Math.abs(out[0] / w) <= 1.0f + 1.0e-3f, "x at " + dx + "," + dy + "," + dz);
                    assertTrue(Math.abs(out[1] / w) <= 1.0f + 1.0e-3f, "y at " + dx + "," + dy + "," + dz);
                }
            }
        }
    }

    @Test
    void aLightStraightUpStillBuildsABasis() {
        float[] matrix = ShadowLightMatrix.viewProjection(0, 0, 0, 0, 1, 0, 16, 64);
        float[] point = clip(matrix, 8, 4, 0);
        float w = point[3];
        assertTrue(w > 0.0f);
        assertTrue(Float.isFinite(point[0]) && Float.isFinite(point[1]) && Float.isFinite(point[2]));
        assertTrue(A.length == 4);
    }

    @Test
    void degenerateInputsReturnIdentityRatherThanNaN() {
        float[] noDirection = ShadowLightMatrix.viewProjection(0, 0, 0, 0, 0, 0, 16, 64);
        assertEquals(1.0f, noDirection[0]);
        assertEquals(1.0f, noDirection[5]);
        float[] noExtent = ShadowLightMatrix.viewProjection(0, 0, 0, 0, 1, 0, 0, 64);
        assertEquals(1.0f, noExtent[0]);
    }
}
