package org.soutalab.celtium.vulkan.terrain.lane1;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.geometry.SectionDesc;
import org.soutalab.celtium.vulkan.lane2.NonTerrainCuller;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.soutalab.celtium.vulkan.terrain.lane1.FrustumExtractor.DepthConvention.OPENGL_NEG_ONE_TO_ONE;
import static org.soutalab.celtium.vulkan.terrain.lane1.FrustumExtractor.DepthConvention.VULKAN_ZERO_TO_ONE;

class TerrainCullStageContractTest {

    private static final CameraPose ZERO = pose(0, 0, 0);
    private static final float FOV = radians(70);
    private static final float ASPECT = 16.0f / 9.0f;

    private record Fixture(String name, CameraPose feet, float roll, float distance, boolean front) {
        Matrix4f modelView() {
            // EntityRenderer.orientCamera and Forge CameraSetup matrix order, using the
            // same feet-relative fixture as the retired frustum_camera_offset_check.py.
            Matrix4f view = new Matrix4f();
            if (front) {
                view.rotateY(radians(180)).rotateX(radians(-180));
            }
            view.translate(0, 0, distance == 0.0f ? 0.05f : -distance);
            if (front) {
                view.rotateX(radians(180));
            }
            return view.rotateZ(radians(roll))
                    .rotateX(radians(feet.pitchDegrees()))
                    .rotateY(radians(feet.yawDegrees() + 180))
                    .translate(0, -1.62f, 0);
        }
    }

    @Test
    void clipReferenceCpuAndGpuAgreeForCameraPoses() {
        List<Fixture> fixtures = List.of(
                new Fixture("level", new CameraPose(0.5, 12.125, 0.5, 0, 0), 0, 0, false),
                new Fixture("pitch yaw roll", new CameraPose(127.125, 63.75, -255.5, -43, 74), 7, 0, false),
                new Fixture("third person back", new CameraPose(0.5, 15.125, 0.5, 37, 23), 0, 4, false),
                new Fixture("third person front", new CameraPose(-128.25, 64.5, 127.875, -117, -21), 0, 4, true),
                new Fixture("world border", new CameraPose(29_999_900.125, 253.75, -29_999_900.375,
                        141, 31), -3, 4, false));
        for (Fixture fixture : fixtures) {
            FrustumExtractor.View view = extract(fixture.feet(), projection(0.05f), fixture.modelView());
            CameraSection camera = CameraSection.of(view.origin());
            // Compute the oracle transform independently of the extractor to catch transform drift.
            float[] clip = flat(projection(0.05f).mul(fixture.modelView()));
            assertArrayEquals(clip, view.viewProj(), 1.0e-6f);
            assertEquals(fixture.feet(), view.origin());
            int visible = 0;
            int rejected = 0;
            for (int x = camera.x() - 10; x <= camera.x() + 10; x++) {
                for (int y = camera.y() - 4; y <= camera.y() + 4; y++) {
                    for (int z = camera.z() - 10; z <= camera.z() + 10; z++) {
                        SectionDesc section = section(x, y, z);
                        boolean reference = clipVisible(clip, fixture.feet(),
                                x * 16.0, y * 16.0, z * 16.0,
                                x * 16.0 + 16, y * 16.0 + 16, z * 16.0 + 16);
                        assertEquals(reference, TerrainCullStage.sectionVisible(view.planes(), view.origin(), section),
                                () -> fixture.name() + " CPU " + section);
                        assertEquals(reference, gpuVisible(view.planes(), camera, section),
                                () -> fixture.name() + " GPU equation " + section);
                        if (reference) {
                            visible++;
                        } else {
                            rejected++;
                        }
                    }
                }
            }
            assertTrue(visible > 100 && rejected > 100, fixture.name() + " counts " + visible + "/" + rejected);
        }
    }

    @Test
    void usingTheOpticalOriginTwiceLosesAndAddsHundredsOfBoxes() {
        CameraPose feet = new CameraPose(40.125, 70.25, -60.375, 180, 0);
        Matrix4f modelView = new Fixture("level", feet, 0, 0, false).modelView();
        Vector3f offset = new Matrix4f(modelView).invert().transformPosition(new Vector3f());
        CameraPose optical = pose(feet.x() + offset.x, feet.y() + offset.y, feet.z() + offset.z);
        FrustumExtractor.View wrong = extract(optical, projection(0.05f), modelView);
        float[] clip = flat(projection(0.05f).mul(modelView));
        int falseNegatives = 0;
        int falsePositives = 0;
        // Sample strips lying between the correct and double-shifted top and bottom
        // boundaries. Their separation is the 1.62-block eye-height offset.
        for (int depth = 8; depth < 40; depth++) {
            double halfHeight = depth * Math.tan(FOV / 2);
            for (int column = -8; column <= 8; column++) {
                double x = feet.x() + column * 0.25;
                double z = feet.z() + 0.05 - depth;
                double bottom = feet.y() + 1.62 - halfHeight + 0.81;
                double top = feet.y() + 1.62 + halfHeight + 0.81;
                if (clipBox(clip, feet, x, bottom, z) && !planeBox(wrong, x, bottom, z)) {
                    falseNegatives++;
                }
                if (!clipBox(clip, feet, x, top, z) && planeBox(wrong, x, top, z)) {
                    falsePositives++;
                }
            }
        }
        assertTrue(falseNegatives > 100 && falsePositives > 100,
                "double offset generated " + falseNegatives + " false negatives and "
                        + falsePositives + " false positives");
    }

    @Test
    void invalidMatricesFailOpenAndTheNextValidFrameRecovers() {
        CameraPose origin = pose(29_999_900.125, 70.25, -29_999_900.375);
        TerrainWorld world = new TerrainWorld(null);
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world), (cmd, kind, draws) -> {});
        List<SectionDesc> sections = List.of(section(0, 0, 0), section(1_874_993, 4, -1_874_993),
                section(-1_874_993, 4, 1_874_993));
        float[] identity = flat(new Matrix4f());
        List<float[]> invalid = List.of(new float[16], flat(new Matrix4f().scale(1, 0, 1)),
                flat(new Matrix4f().m00(Float.NaN)), flat(new Matrix4f().m31(Float.POSITIVE_INFINITY)),
                flat(new Matrix4f().m32(Float.NEGATIVE_INFINITY)));
        long frameId = 1L;
        for (float[] matrix : invalid) {
            for (FrustumExtractor.View view : List.of(
                    FrustumExtractor.extract(origin, matrix, identity, VULKAN_ZERO_TO_ONE),
                    FrustumExtractor.extract(origin, identity, matrix, VULKAN_ZERO_TO_ONE))) {
                stage.prepareMainView(view.origin(), view.planes(), frameId++);
                assertSame(FrustumPlanes.permissive(), world.mainFrustum());
                for (SectionDesc section : sections) {
                    assertTrue(TerrainCullStage.sectionVisible(world.mainFrustum(), world.mainPose(), section));
                    assertTrue(gpuVisible(world.mainFrustum(), CameraSection.of(origin), section));
                }
            }
        }

        FrustumExtractor.View valid = FrustumExtractor.extract(origin, identity, identity, VULKAN_ZERO_TO_ONE);
        stage.prepareMainView(valid.origin(), valid.planes(), frameId);
        assertNotSame(FrustumPlanes.permissive(), world.mainFrustum());
        assertFalse(TerrainCullStage.sectionVisible(world.mainFrustum(), world.mainPose(), sections.get(0)));
        assertTrue(TerrainCullStage.sectionVisible(world.mainFrustum(), world.mainPose(),
                section(1_874_993, 4, -1_874_994)));
    }

    @Test
    void inputsAndLaterFramesCannotMutateAPublishedSnapshot() {
        float[] projection = flat(projection(0.05f));
        float[] modelView = flat(new Matrix4f().translate(0, -1.62f, 0.05f));
        FrustumExtractor.View first = FrustumExtractor.extract(ZERO, projection, modelView, VULKAN_ZERO_TO_ONE);
        TerrainWorld world = new TerrainWorld(null);
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world), (cmd, kind, draws) -> {});
        stage.prepareMainView(first.origin(), first.planes(), 1L);
        FrustumPlanes snapshot = world.mainFrustum();
        float[] beforePlanes = snapshot.coefficients();
        float[] beforeTransform = first.viewProj();

        Arrays.fill(projection, 0);
        Arrays.fill(modelView, 0);
        Arrays.fill(first.viewProj(), 0);
        Arrays.fill(snapshot.coefficients(), 0);
        FrustumExtractor.View next = extract(pose(10, 20, 30), new Matrix4f(), new Matrix4f());
        stage.prepareMainView(next.origin(), next.planes(), 2L);

        assertSame(next.planes(), world.mainFrustum());
        assertArrayEquals(beforePlanes, snapshot.coefficients());
        assertArrayEquals(beforeTransform, first.viewProj());
        assertEquals(ZERO, first.origin());
    }

    @Test
    void integerSectionDeltaPreservesSubBlockDecisionsAtBothWorldBorders() {
        for (int sign : new int[]{-1, 1}) {
            int shift = sign * 29_999_904; // section-aligned
            double localX = -4.0 * sign + 0.25;
            CameraPose near = pose(localX, 0, 0);
            CameraPose far = pose(shift + localX, 0, 0);
            float[] coeffs = new float[FrustumPlanes.FLOAT_COUNT];
            coeffs[0] = 1.0f;
            coeffs[3] = (float) (localX - 0.125);
            FrustumPlanes planes = FrustumPlanes.of(coeffs);
            for (int sectionX : new int[]{-1, 0}) {
                SectionDesc local = section(sectionX, 0, 0);
                SectionDesc translated = section(sectionX + shift / 16, 0, 0);
                boolean visible = sectionX == 0;
                assertEquals(visible, TerrainCullStage.sectionVisible(planes, near, local));
                assertEquals(visible, TerrainCullStage.sectionVisible(planes, far, translated));
                assertEquals(visible, gpuVisible(planes, CameraSection.of(near), local));
                assertEquals(visible, gpuVisible(planes, CameraSection.of(far), translated));
            }
            float oldMax = (float) (shift / 16 - 1) * 16.0f - (float) far.x() + 16.0f;
            assertTrue(oldMax + coeffs[3] >= 0, "world-float subtraction must lose the rejecting margin");
        }
    }

    @Test
    void vulkanNearPlaneUsesRowTwoAndRejectsBoxesBeforeNear() {
        Matrix4f projection = projection(1.0f);
        CameraPose origin = pose(0, 0, 0.75);
        FrustumExtractor.View vk = extract(origin, projection, new Matrix4f());
        FrustumExtractor.View wrongRule = FrustumExtractor.extract(origin, flat(projection),
                flat(new Matrix4f()), OPENGL_NEG_ONE_TO_ONE);
        SectionDesc beforeNear = section(0, 0, 0);
        SectionDesc beyondNear = section(0, 0, -1);
        assertFalse(TerrainCullStage.sectionVisible(vk.planes(), origin, beforeNear));
        assertFalse(gpuVisible(vk.planes(), CameraSection.of(origin), beforeNear));
        assertTrue(TerrainCullStage.sectionVisible(wrongRule.planes(), origin, beforeNear));
        assertTrue(TerrainCullStage.sectionVisible(vk.planes(), origin, beyondNear));
        assertFalse(planeBox(vk, 0, 0, origin.z() - 0.9));
        assertTrue(planeBox(vk, 0, 0, origin.z() - 1.1));

        FrustumExtractor.View gl = FrustumExtractor.extract(origin,
                flat(new Matrix4f().perspective(FOV, ASPECT, 1, 256)), flat(new Matrix4f()), OPENGL_NEG_ONE_TO_ONE);
        assertFalse(TerrainCullStage.sectionVisible(gl.planes(), origin, beforeNear));
        assertTrue(TerrainCullStage.sectionVisible(gl.planes(), origin, beyondNear));
    }

    @Test
    void necKeepsAnEntityCrossingFromACulledIntoAVisibleSection() {
        int sx = -1_874_994;
        CameraPose origin = pose(sx * 16.0, 0, 0);
        FrustumExtractor.View view = extract(origin, projection(1.0f), new Matrix4f());
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1024),
                new HostSlabSpace(1024), new HostSlabSpace(1024));
        SectionGeometry empty = new SectionGeometry(List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), new short[0]);
        store.upload(sx, 0, 0, empty, 1L);
        store.upload(sx, 0, -1, empty, 1L);
        TerrainCullStage stage = new TerrainCullStage(new TerrainWorld(store));
        List<SectionDesc> visible = stage.visibleSections(view.planes(), view.origin());
        assertEquals(1, visible.size());
        assertEquals(-1, visible.getFirst().sectionZ());

        NonTerrainCuller.Kind kind = NonTerrainCuller.Kind.ENTITY;
        NonTerrainCuller.Candidate[] candidates = {
                NonTerrainCuller.Candidate.bounded(kind, origin.x(), 0, -2, origin.x() + 0.5, 0.5, 0.5),
                NonTerrainCuller.Candidate.bounded(kind, origin.x(), 0, 0.25, origin.x() + 0.5, 0.5, 0.5)
        };
        NonTerrainCuller.Result result = NonTerrainCuller.cull(candidates, (x, y, z) ->
                visible.stream().anyMatch(section -> section.sectionX() == x
                        && section.sectionY() == y && section.sectionZ() == z), null);
        assertArrayEquals(new boolean[]{true, false}, result.visible());
        assertEquals(1, result.culledBySection());
    }

    private static CameraPose pose(double x, double y, double z) {
        return new CameraPose(x, y, z, 0, 0);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static Matrix4f projection(float near) {
        return new Matrix4f().perspective(FOV, ASPECT, near, 256, true);
    }

    private static float[] flat(Matrix4f matrix) {
        return matrix.get(new float[16]);
    }

    private static FrustumExtractor.View extract(CameraPose origin, Matrix4f projection, Matrix4f view) {
        return FrustumExtractor.extract(origin, flat(projection), flat(view), VULKAN_ZERO_TO_ONE);
    }

    private static SectionDesc section(int x, int y, int z) {
        return new SectionDesc(x, y, z, 0, 0, 0, 0, 0, 0, SectionDesc.NO_AUXILIARY);
    }

    // Oracle: transform all corners, then reject only if every corner fails the same
    // homogeneous clip inequality. This is the conservative section cull contract.
    private static boolean clipVisible(float[] m, CameraPose origin, double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        int commonOutcode = 0b111111;
        for (int corner = 0; corner < 8; corner++) {
            double x = ((corner & 1) == 0 ? minX : maxX) - origin.x();
            double y = ((corner & 2) == 0 ? minY : maxY) - origin.y();
            double z = ((corner & 4) == 0 ? minZ : maxZ) - origin.z();
            double cx = m[0] * x + m[4] * y + m[8] * z + m[12];
            double cy = m[1] * x + m[5] * y + m[9] * z + m[13];
            double cz = m[2] * x + m[6] * y + m[10] * z + m[14];
            double cw = m[3] * x + m[7] * y + m[11] * z + m[15];
            int outcode = (cx < -cw ? 1 : 0) | (cx > cw ? 2 : 0)
                    | (cy < -cw ? 4 : 0) | (cy > cw ? 8 : 0)
                    | (cz < 0 ? 16 : 0) | (cz > cw ? 32 : 0);
            commonOutcode &= outcode;
        }
        return commonOutcode == 0;
    }

    private static boolean clipBox(float[] clip, CameraPose origin, double x, double y, double z) {
        double r = 0.05;
        return clipVisible(clip, origin, x - r, y - r, z - r, x + r, y + r, z + r);
    }

    private static boolean planeBox(FrustumExtractor.View view, double x, double y, double z) {
        double r = 0.05;
        CameraPose origin = view.origin();
        FrustumPlanes planes = view.planes();
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            double px = x - origin.x() + Math.copySign(r, planes.coefficient(plane, 0));
            double py = y - origin.y() + Math.copySign(r, planes.coefficient(plane, 1));
            double pz = z - origin.z() + Math.copySign(r, planes.coefficient(plane, 2));
            if (planes.signedDistance(plane, px, py, pz) < 0.0f) {
                return false;
            }
        }
        return true;
    }

    // Lane1Shaders.CULL_COMPUTE: subtract integer sections, then evaluate each
    // plane at its positive vertex using float arithmetic.
    private static boolean gpuVisible(FrustumPlanes planes, CameraSection camera, SectionDesc section) {
        float x = (section.sectionX() - camera.x()) * 16.0f - camera.fracX();
        float y = (section.sectionY() - camera.y()) * 16.0f - camera.fracY();
        float z = (section.sectionZ() - camera.z()) * 16.0f - camera.fracZ();
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            float a = planes.coefficient(plane, 0);
            float b = planes.coefficient(plane, 1);
            float c = planes.coefficient(plane, 2);
            float px = a >= 0 ? x + 16.0f : x;
            float py = b >= 0 ? y + 16.0f : y;
            float pz = c >= 0 ? z + 16.0f : z;
            if (a * px + b * py + c * pz + planes.coefficient(plane, 3) < 0.0f) {
                return false;
            }
        }
        return true;
    }
}
