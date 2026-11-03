package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decision table for {@code -Dceltium.vulkan.fontBake}: whether a glyph segment may have the live modelView baked
 * into its positions, which is what lets one font run span the per-string {@code pushMatrix/translate/popMatrix}
 * brackets.
 * <p>
 * The measurement that motivates it: on the shipped world profile (leg r52f-sticky, 2365 frames)
 * {@code fontRunsStarted=221565} against {@code fontQuadsAppended=265707} - 94 runs a frame carrying 1.2 quads each,
 * and every run is an offer through the accept path and a group of its own. The run key carries the modelView, so the
 * per-string translate is what splits them.
 * <p>
 * What these tests prove: the table. They prove nothing about a frame - a green test here is not evidence that a run
 * was coalesced, that the text is in the right place, or that the picture is unchanged.
 */
class FontBakeTest {

    private static float[] identity() {
        return new float[] {
                1.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 1.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F};
    }

    /** A GUI string's matrix: a translate and a uniform scale, which is the shape the diagonal product reproduces. */
    private static float[] translateScale(float x, float y, float scale) {
        float[] m = identity();
        m[0] = scale;
        m[5] = scale;
        m[10] = scale;
        m[12] = x;
        m[13] = y;
        return m;
    }

    /** The same matrix with a rotation about z, whose off-axis terms the diagonal product would drop. */
    private static float[] rotated(float x, float y) {
        float[] m = identity();
        float c = 0.70710678F;
        m[0] = c;
        m[4] = -c;
        m[1] = c;
        m[5] = c;
        m[12] = x;
        m[13] = y;
        return m;
    }

    @Test
    void aTranslateScaleIsBakeable() {
        assertTrue(VulkanTerrainGpuBackend.celtium$fontBakeable(true, false, translateScale(12.0F, 34.0F, 2.0F)),
                "the HUD's own matrix shape is the one the bake exists for");
    }

    @Test
    void theIdentityIsBakeable() {
        assertTrue(VulkanTerrainGpuBackend.celtium$fontBakeable(true, false, identity()),
                "an identity modelView bakes to the same bytes, so a run opened under it may still join the next");
    }

    @Test
    void aRotationIsNotBakeable() {
        assertFalse(VulkanTerrainGpuBackend.celtium$fontBakeable(true, false, rotated(4.0F, 5.0F)),
                "the diagonal product would drop the off-axis terms, so a rotated matrix keeps the matrix-keyed run");
    }

    @Test
    void fogIsNotBakeable() {
        assertFalse(VulkanTerrainGpuBackend.celtium$fontBakeable(true, true, translateScale(1.0F, 2.0F, 1.0F)),
                "fog is computed from view-space distance, which baking the modelView into the positions would move");
    }

    @Test
    void theSwitchOffNeverBakes() {
        assertFalse(VulkanTerrainGpuBackend.celtium$fontBakeable(false, false, translateScale(1.0F, 2.0F, 1.0F)),
                "a run that did not ask for the bake must record exactly the runs it recorded before");
    }

    @Test
    void aPerspectiveModelViewIsNotBakeable() {
        float[] m = identity();
        m[11] = -1.0F;
        assertFalse(VulkanTerrainGpuBackend.celtium$fontBakeable(true, false, m),
                "a non-affine matrix has no translate/scale/flip shape at all");
    }

    @Test
    void aMissingMatrixIsNotBakeable() {
        assertFalse(VulkanTerrainGpuBackend.celtium$fontBakeable(true, false, null),
                "no matrix is not an identity matrix");
        assertFalse(VulkanTerrainGpuBackend.celtium$fontBakeable(true, false, new float[4]),
                "a short array cannot be the sixteen floats the bake reads");
    }
}
