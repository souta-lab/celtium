package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Tier-A draw-side space contract (arch/regionoffset-fix, 2026-11-25).
 * <p>
 * {@code terrain_a.vert} composes a vertex' camera-relative position as
 * {@code local + section * 16 + u_RegionOffset}, where the SectionDesc record
 * carries the section translation, the TerrainVertex16 positions are
 * section-local, and {@code u_RegionOffset} — written by
 * {@link TerrainRenderer#writeTierAWorldOffset} — is the only camera term and
 * must be world-origin-minus-camera. The culling side
 * ({@code terrain_cull.glsl}'s {@code celtiumBoxVisible}, mirrored by
 * {@link GpuTerrainDrawTable#isVisible}) tests the box
 * {@code section * 16 - camera} in the same camera-relative space. The two
 * sides were audited independently (the draw side in leg r7, the culling side
 * by arch/projection-diag) but nothing pinned them to each other: the r10
 * leg's "RegionOffset" first judgement could not be separated from the
 * culling space bug until both were read together. These tests hold the
 * composition to the culling side with the camera away from the origin, the
 * one condition the earlier tests all missed.
 */
class TierARegionOffsetContractTest {

    /** {@code terrain_a.vert}'s translation for one axis: {@code section * 16 + u_RegionOffset}. */
    private static float drawTranslation(int section, CameraTransform camera, int axis) {
        return switch (axis) {
            case 0 -> section * 16.0f + TerrainRenderer.tierAWorldOffsetComponent(camera.intX, camera.fracX);
            case 1 -> section * 16.0f + TerrainRenderer.tierAWorldOffsetComponent(camera.intY, camera.fracY);
            default -> section * 16.0f + TerrainRenderer.tierAWorldOffsetComponent(camera.intZ, camera.fracZ);
        };
    }

    private static double cameraAxis(CameraTransform camera, int axis) {
        return switch (axis) {
            case 0 -> camera.x;
            case 1 -> camera.y;
            default -> camera.z;
        };
    }

    private static int sectionAxis(int[] section, int axis) {
        return section[axis];
    }

    @Test
    void worldOffsetComponentIsWorldOriginMinusCamera() {
        // The component must be exactly -(int + frac) in float arithmetic: the
        // integer subtraction happens first (exact), and negating the sum is
        // the same rounding by sign symmetry. Any drift here shifts every
        // Tier-A section by the error.
        double[][] cameras = {
                {1000.25, 100.0, -250.5},
                {-33.75, 64.5, 8192.125},
                {0.5, 100.0, 0.5},
        };
        for (double[] c : cameras) {
            CameraTransform camera = new CameraTransform(c[0], c[1], c[2]);
            assertEquals(-(camera.intX + camera.fracX),
                    TerrainRenderer.tierAWorldOffsetComponent(camera.intX, camera.fracX), 0.0f);
            assertEquals(-(camera.intY + camera.fracY),
                    TerrainRenderer.tierAWorldOffsetComponent(camera.intY, camera.fracY), 0.0f);
            assertEquals(-(camera.intZ + camera.fracZ),
                    TerrainRenderer.tierAWorldOffsetComponent(camera.intZ, camera.fracZ), 0.0f);
        }
    }

    @Test
    void drawnSectionLandsAtWorldMinusCamera() {
        // The r10 scene's shape, at a camera away from the origin: the section
        // containing the camera must draw around the relative origin, not at
        // the world origin and not displaced by the camera position.
        CameraTransform camera = new CameraTransform(1000.25, 100.0, -250.5);
        int[] section = {62, 6, -16};
        float[] locals = {0.0f, 16.0f};
        for (int axis = 0; axis < 3; axis++) {
            for (float local : locals) {
                float drawn = local + drawTranslation(sectionAxis(section, axis), camera, axis);
                double truth = sectionAxis(section, axis) * 16.0 + local - cameraAxis(camera, axis);
                assertEquals(truth, drawn, 1.0e-3,
                        "axis " + axis + " local " + local);
            }
        }
    }

    @Test
    void drawAndCullBoxesCoincide() {
        // For the same section and camera, the box the vertex stage draws
        // (translation .. translation + 16) must be the box the culling stage
        // tests (section * 16 - camera .. + 16). If these ever diverge, the
        // culling keeps sections the draw misplaces — the r10 signature.
        double[][] cameras = {
                {1000.25, 100.0, -250.5},
                {-33.75, 64.5, 8192.125},
                {0.5, 100.0, 0.5},
        };
        int[][] sections = {
                {62, 6, -16}, {0, 6, 0}, {-3, 4, 512}, {125000, 8, -125000},
        };
        for (double[] c : cameras) {
            CameraTransform camera = new CameraTransform(c[0], c[1], c[2]);
            for (int[] section : sections) {
                for (int axis = 0; axis < 3; axis++) {
                    float drawMin = drawTranslation(sectionAxis(section, axis), camera, axis);
                    float cullMin = (float) (sectionAxis(section, axis) * 16.0 - cameraAxis(camera, axis));
                    assertEquals(cullMin, drawMin, 1.0e-3,
                            "section " + java.util.Arrays.toString(section) + " axis " + axis);
                }
            }
        }
    }

    @Test
    void cullKeepsTheDrawnSectionAroundTheCamera() {
        // End-to-end on the culling mirror: with the camera at x=1000.25, the
        // section-box entry for the camera's own section is kept and the one
        // at the world origin is rejected by the identity frustum — the same
        // arrangement the projection-diag regression pins for region boxes,
        // here for the section boxes terrain_cull_a.comp tests.
        float[] m = new float[16];
        m[0] = 1;
        m[5] = 1;
        m[10] = 1;
        m[15] = 1;
        float[] planes = GpuTerrainDrawTable.extractPlanes(m);
        GpuTerrainDrawTable.Entry aroundCamera = new GpuTerrainDrawTable.Entry(
                36, 0, 100, 0, 62, 6, -16, GpuTerrainDrawTable.FLAG_SECTION_BOX);
        GpuTerrainDrawTable.Entry atOrigin = new GpuTerrainDrawTable.Entry(
                36, 0, 100, 0, 0, 6, 0, GpuTerrainDrawTable.FLAG_SECTION_BOX);
        assertTrue(GpuTerrainDrawTable.isVisible(planes, 1000.25f, 100.0f, -250.5f, 4096, aroundCamera));
        assertFalse(GpuTerrainDrawTable.isVisible(planes, 1000.25f, 100.0f, -250.5f, 4096, atOrigin));
    }

    @Test
    void farFromOriginErrorStaysWithinTheFloatBound() {
        // Characterisation, not a contract violation: past 2^24 the integer
        // camera component itself rounds in float, so the drawn position can
        // drift by up to half an ulp of the camera magnitude (1 block at
        // x=20,000,001). The compact path avoids this by keeping vertices
        // region-relative; Tier-A's desc translation cannot. Recorded here so
        // the bound is measured, and Stage 4's far-coordinate leg knows what
        // to look for.
        CameraTransform camera = new CameraTransform(20_000_001.0, 100.0, 0.0);
        int section = 1_250_000;
        float drawn = 0.0f + drawTranslation(section, camera, 0);
        double truth = section * 16.0 - camera.x;
        assertTrue(Math.abs(drawn - truth) <= 1.01,
                "drawn " + drawn + " vs truth " + truth);
    }
}
