package org.soutalab.celtium.vulkan.terrain.lane1;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.geometry.SectionDesc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate G1 visibility/geometry contracts (NEON-G1-DEPTH-RULING-2026-11-28 section 2.2, G1-V2):
 * the three defects that produced the black blob and radial streaks on the Lane 1 path -
 * (1) world-space section AABBs tested against camera-relative frustum planes, (2) the
 * AXIS_VERTEX axis==0 extent swap, (3) the GENERAL_VERTEX bowtie corner split - pinned as
 * unit contracts so none of them can silently return.
 */
class G1VisibilityContractTest {

    private static SectionDesc desc(int x, int y, int z) {
        return new SectionDesc(x, y, z, 0, 0L, 0L, 0, 0, 0, SectionDesc.NO_AUXILIARY);
    }

    /**
     * The scene-A frustum in camera-relative space: camera at the origin looking +X (yaw 270),
     * pitched 20 degrees down, vertical FOV 70 degrees, aspect 16:9. Every plane passes through
     * the origin except near/far, exactly as the backend's Gribb-Hartmann extraction from the
     * translation-free view matrix produces.
     */
    private static FrustumPlanes sceneACameraRelativeFrustum() {
        double pitch = Math.toRadians(20.0);
        double halfV = Math.toRadians(35.0);
        double halfH = Math.atan(Math.tan(halfV) * (16.0 / 9.0));
        double fx = Math.cos(pitch), fy = -Math.sin(pitch);
        // Right vector is +Z, camera up is (sin20, cos20, 0); the side-plane inward normals
        // below are the cross products worked out from those axes (sign-checked against the
        // view direction in the sanity assertion of the test that uses this frustum).
        double sh = Math.sin(halfH), ch = Math.cos(halfH);
        float[] left = {(float) (fx * sh), (float) (fy * sh), (float) ch, 0.0f};
        float[] right = {(float) (fx * sh), (float) (fy * sh), (float) -ch, 0.0f};
        double topPitch = Math.toRadians(15.0);   // -20 + 35
        float[] top = {(float) Math.sin(topPitch), (float) -Math.cos(topPitch), 0.0f, 0.0f};
        double bottomPitch = Math.toRadians(55.0); // -20 - 35, magnitude
        float[] bottom = {(float) Math.sin(bottomPitch), (float) Math.cos(bottomPitch), 0.0f, 0.0f};
        float[] near = {(float) fx, (float) fy, 0.0f, -0.05f};
        float[] far = {(float) -fx, (float) -fy, 0.0f, 600.0f};
        float[][] planes = {left, right, bottom, top, near, far};
        float[] coefficients = new float[FrustumPlanes.FLOAT_COUNT];
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            System.arraycopy(planes[plane], 0, coefficients, plane * 4, 4);
        }
        return FrustumPlanes.of(coefficients);
    }

    @Test
    void frustumTestSubtractsTheCameraOrigin() {
        FrustumPlanes frustum = sceneACameraRelativeFrustum();
        // Sanity: the view centre a short step ahead is inside every plane.
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            assertTrue(frustum.signedDistance(plane, 10.0, -3.4, 0.0) >= 0.0f,
                    "view centre must be inside plane " + plane);
        }
        CameraPose pose = new CameraPose(0.0, 100.0, 0.0, 270.0f, 20.0f);
        // The sunlit surface section in front of the raised camera survives the cull.
        assertTrue(TerrainCullStage.sectionVisible(frustum, pose, desc(8, 4, 0)),
                "(8,4,0) must be visible from cameraY=100 looking +X, pitch 20 down");
        // Negative control (defect #1 itself): the same frustum tested against world
        // coordinates - the origin-relative convenience form - culls the section entirely,
        // because the top plane then sits 100 blocks too low.
        assertFalse(TerrainCullStage.sectionVisible(frustum, desc(8, 4, 0)),
                "without the camera-origin subtraction (8,4,0) is culled (defect #1)");
        // A section behind the camera stays culled with the subtraction in place.
        assertFalse(TerrainCullStage.sectionVisible(frustum, pose, desc(-8, 4, 0)),
                "sections behind the camera must stay culled");
    }

    // -- AXIS_VERTEX expansion contract -------------------------------------------------

    /** Component index (0=x, 1=y, 2=z) the shader branch adds extentU / extentV into. */
    private static int[] axisBranchTargets(String shader, String branchHead) {
        Pattern pattern = Pattern.compile(Pattern.quote(branchHead)
                + "\\s*\\{\\s*local\\.([xyz]) \\+= corner\\.x \\* float\\(extentU\\);"
                + "\\s*local\\.([xyz]) \\+= corner\\.y \\* float\\(extentV\\);\\s*\\}");
        Matcher matcher = pattern.matcher(shader);
        assertTrue(matcher.find(), "AXIS_VERTEX branch not found: " + branchHead);
        return new int[]{component(matcher.group(1)), component(matcher.group(2))};
    }

    private static int component(String name) {
        return switch (name) {
            case "x" -> 0;
            case "y" -> 1;
            default -> 2;
        };
    }

    @Test
    void axisVertexExpansionMatchesMesherAxes() {
        // TerrainDirs: axis 0 (X face) U=Z(2) V=Y(1); axis 1 (Y face) U=X(0) V=Z(2);
        // axis 2 (Z face) U=X(0) V=Y(1). The shader's expansion targets must match, or a
        // greedy quad rotates 90 degrees in its own plane (defect #2).
        int[][] expected = {{2, 1}, {0, 2}, {0, 1}};
        String[] heads = {"if (axis == 0u)", "else if (axis == 1u)", "else"};
        for (int axis = 0; axis < 3; axis++) {
            int[] targets = axisBranchTargets(Lane1Shaders.AXIS_VERTEX, heads[axis]);
            assertEquals(expected[axis][0], targets[0], "axis " + axis + " extentU target");
            assertEquals(expected[axis][1], targets[1], "axis " + axis + " extentV target");
            // Bounds simulation: a quad whose V axis already sits at local 14 with extentV 1
            // and a full-width extentU 16 must stay inside the section's [0,16] cube. With the
            // U/V targets swapped, the 16-wide extent lands on the V axis and reaches 30.
            double[] minCorner = {0.0, 0.0, 0.0};
            minCorner[targets[1]] = 14.0;
            for (int cx = 0; cx <= 1; cx++) {
                for (int cy = 0; cy <= 1; cy++) {
                    double[] local = minCorner.clone();
                    local[targets[0]] += cx * 16.0;
                    local[targets[1]] += cy * 1.0;
                    for (int c = 0; c < 3; c++) {
                        assertTrue(local[c] >= 0.0 && local[c] <= 16.0,
                                "axis " + axis + " corner escapes the section: " + local[c]);
                    }
                }
            }
        }
    }

    // -- GENERAL_VERTEX cyclic split contract --------------------------------------------

    private static double triangleArea(double[] a, double[] b, double[] c) {
        return Math.abs((b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])) / 2.0;
    }

    private static boolean insideTriangle(double[] p, double[] a, double[] b, double[] c) {
        double d1 = (p[0] - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (p[1] - b[1]);
        double d2 = (p[0] - c[0]) * (b[1] - c[1]) - (b[0] - c[0]) * (p[1] - c[1]);
        double d3 = (p[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (p[1] - a[1]);
        boolean negative = (d1 < -1e-9) || (d2 < -1e-9) || (d3 < -1e-9);
        boolean positive = (d1 > 1e-9) || (d2 > 1e-9) || (d3 > 1e-9);
        return !(negative && positive);
    }

    @Test
    void generalVertexSplitCoversTheCyclicQuadExactlyOnce() {
        Matcher matcher = Pattern.compile(
                "CORNER_INDEX\\[6\\] = int\\[\\]\\((\\d+), (\\d+), (\\d+), (\\d+), (\\d+), (\\d+)\\)")
                .matcher(Lane1Shaders.GENERAL_VERTEX);
        assertTrue(matcher.find(), "GENERAL_VERTEX CORNER_INDEX not found");
        int[] index = new int[6];
        for (int i = 0; i < 6; i++) {
            index[i] = Integer.parseInt(matcher.group(i + 1));
        }
        assertEquals(java.util.List.of(0, 1, 2, 0, 2, 3), java.util.Arrays.stream(index).boxed().toList(),
                "cyclic quads split on the 0-2 diagonal (defect #3)");
        // The cyclic corner embedding (the GEN_UV order): 0=(0,0), 1=(0,1), 2=(1,1), 3=(1,0).
        double[][] corner = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        double area = triangleArea(corner[index[0]], corner[index[1]], corner[index[2]])
                + triangleArea(corner[index[3]], corner[index[4]], corner[index[5]]);
        assertEquals(1.0, area, 1e-9, "the two triangles must cover the unit quad's area");
        // Every quadrant centre is covered exactly once: the bowtie split (0,1,2)+(2,1,3)
        // double-covers two quadrants and leaves the other two bare. The probes sit just
        // off the shared 0-2 diagonal (the y=x line): a probe exactly on the diagonal is on
        // both triangles' boundary and counts twice under any correct split.
        double[][] probes = {{0.25, 0.75}, {0.75, 0.70}, {0.25, 0.30}, {0.75, 0.25}};
        for (double[] probe : probes) {
            int cover = 0;
            if (insideTriangle(probe, corner[index[0]], corner[index[1]], corner[index[2]])) {
                cover++;
            }
            if (insideTriangle(probe, corner[index[3]], corner[index[4]], corner[index[5]])) {
                cover++;
            }
            assertEquals(1, cover, "quadrant probe must be covered exactly once");
        }
        // The local-UV table must follow the same cyclic corner order.
        assertTrue(Lane1Shaders.GENERAL_VERTEX.contains(
                        "vec2[](vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0))"),
                "GEN_UV must follow the cyclic corner order");
        assertTrue(Lane1Shaders.GENERAL_VERTEX.contains("fragLocalUv = GEN_UV[corner]"),
                "fragLocalUv must come from the cyclic GEN_UV table");
    }
}
