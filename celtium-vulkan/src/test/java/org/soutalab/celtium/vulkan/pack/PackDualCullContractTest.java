package org.soutalab.celtium.vulkan.pack;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.frame.VulkanCommandStream;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;
import org.soutalab.celtium.core.geometry.SectionDesc;
import org.soutalab.celtium.core.shader.AttachmentFormatKey;
import org.soutalab.celtium.core.shader.BlendStateSpec;
import org.soutalab.celtium.core.shader.DescriptorBindingPlan;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;
import org.soutalab.celtium.shader.uniforms.ShaderFrameState;
import org.soutalab.celtium.vulkan.terrain.lane1.HostSlabSpace;
import org.soutalab.celtium.vulkan.terrain.lane1.SectionGeometry;
import org.soutalab.celtium.vulkan.terrain.lane1.TerrainCullStage;
import org.soutalab.celtium.vulkan.terrain.lane1.TerrainDrawStage;
import org.soutalab.celtium.vulkan.terrain.lane1.TerrainSectionStore;
import org.soutalab.celtium.vulkan.terrain.lane1.TerrainWorld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DualCull contract (Neon frustum ruling section 3.3, stage 2 B; a Gate G3 requirement):
 * the pack path runs two culling views at once - the main perspective view and the shadow
 * orthographic view - over one resident world, and the shadow view also drives the G-7
 * block-entity gather. This test pins, against the seats that exist in this module:
 *
 * <ul>
 *   <li>the two views install into independent {@link TerrainWorld} slots: preparing or
 *       re-preparing either view leaves the other's pose, planes, frame id and distance
 *       bit-identical;</li>
 *   <li>{@link TerrainDrawStage#recordTerrainPass} culls main passes with the main frustum
 *       and shadow passes with the shadow frustum, and refuses a shadow pass whose view was
 *       never prepared;</li>
 *   <li>frustum planes extracted from a shadow camera's orthographic combined matrix judge
 *       section AABBs exactly as the independent clip-space reference does;</li>
 *   <li>the G-7 gather ({@link TerrainDrawStage#gatherShadowBlockEntities}) collects exactly
 *       the entities inside the shadow frustum - at world coordinates near +/-29,999,900 as
 *       exactly as near the origin - and agrees with the shadow terrain cull over the same
 *       frustum, so the gather and the shadow pass share one origin;</li>
 *   <li>that shared origin is the one the pack uniforms call {@code cameraPosition}:
 *       a shadow view built over a {@link ShaderFrameState} whose camera stands at large
 *       coordinates gathers around that camera, with zero double offset and zero offset
 *       loss (the negative control shows an origin-dropped frustum gathering the wrong
 *       set, so the large-coordinate assertions can actually catch the offset class).</li>
 * </ul>
 *
 * Conventions: the frustums consumed here are world-space plane sets, the space the CPU
 * seats ({@link TerrainCullStage}, {@link TerrainWorld#blockEntitiesIn}) evaluate in.
 * Extraction fixtures use the production producer's Gribb-Hartmann formulas
 * ({@code ShaderFrameStateFeeder.frustumFromMatrix}, forge122) but read the matrix rows
 * correctly - see {@link #extractFrustum} for the {@code Matrix4f.get(int, int)}
 * transposition this JOML build exhibits, which the feeder's own row read shares; the
 * feeder-side consequence is recorded in this slice's landing record as a finding for
 * the forge122 seat, outside this test's module. What is pinned here is the consumer
 * contract for correctly extracted frustums. The gather's one-block
 * grace (a block centre up to 1.0 outside a plane is still collected) is TerrainWorld's
 * standing behaviour; every assertion here keeps at least 1.5 blocks clear of that band.
 */
class PackDualCullContractTest {

    // -- fixtures ---------------------------------------------------------------

    private static TerrainWorld emptyWorld() {
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 16));
        return new TerrainWorld(store);
    }

    private static TerrainDrawStage stage(TerrainWorld world,
                                            List<TerrainDrawStage.DrawCommand> drawn) {
        return new TerrainDrawStage(world, new TerrainCullStage(world),
                (cmd, kind, commands) -> drawn.addAll(commands));
    }

    /** A frustum that contains everything: every plane is (0,0,0,1). */
    private static FrustumPlanes allInside() {
        float[] coefficients = new float[FrustumPlanes.FLOAT_COUNT];
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            coefficients[plane * 4 + 3] = 1f;
        }
        return FrustumPlanes.of(coefficients);
    }

    /** The axis-aligned box [min,max] as six world-space planes with unit normals. */
    private static FrustumPlanes boxFrustum(double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
        float[] c = new float[FrustumPlanes.FLOAT_COUNT];
        c[FrustumPlanes.LEFT * 4] = 1f;
        c[FrustumPlanes.LEFT * 4 + 3] = (float) -minX;
        c[FrustumPlanes.RIGHT * 4] = -1f;
        c[FrustumPlanes.RIGHT * 4 + 3] = (float) maxX;
        c[FrustumPlanes.BOTTOM * 4 + 1] = 1f;
        c[FrustumPlanes.BOTTOM * 4 + 3] = (float) -minY;
        c[FrustumPlanes.TOP * 4 + 1] = -1f;
        c[FrustumPlanes.TOP * 4 + 3] = (float) maxY;
        c[FrustumPlanes.NEAR * 4 + 2] = 1f;
        c[FrustumPlanes.NEAR * 4 + 3] = (float) -minZ;
        c[FrustumPlanes.FAR * 4 + 2] = -1f;
        c[FrustumPlanes.FAR * 4 + 3] = (float) maxZ;
        return FrustumPlanes.of(c);
    }

    /**
     * Gribb-Hartmann extraction with the production producer's formulas
     * ({@code ShaderFrameStateFeeder.frustumFromMatrix}, forge122): left/right/bottom/top
     * from rows 3 +/- rows 0/1, near/far from row 3 +/- row 2, each plane normalised.
     * The rows are read through the column-major flat array, NOT through
     * {@code Matrix4f.get(int, int)}: with the JOML 1.10.5 on this classpath that method
     * returns the element transposed relative to the logical matrix {@code transform}
     * applies (verified empirically against a known translation and a lookAt view:
     * {@code get(3, 0)} yields the x translation that lives at row 0, column 3). Reading
     * rows the feeder's way would extract the transposed matrix's planes - a frustum
     * around the world origin instead of around the camera, which is exactly the
     * offset-loss signature the large-coordinate tests below exist to catch.
     */
    private static FrustumPlanes extractFrustum(Matrix4f m) {
        float[] planes = new float[FrustumPlanes.FLOAT_COUNT];
        float[] flat = m.get(new float[16]);
        float[][] rows = new float[4][4];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                rows[row][col] = flat[col * 4 + row];
            }
        }
        writePlane(planes, FrustumPlanes.LEFT, rows[3], rows[0], 1.0f);
        writePlane(planes, FrustumPlanes.RIGHT, rows[3], rows[0], -1.0f);
        writePlane(planes, FrustumPlanes.BOTTOM, rows[3], rows[1], 1.0f);
        writePlane(planes, FrustumPlanes.TOP, rows[3], rows[1], -1.0f);
        writePlane(planes, FrustumPlanes.NEAR, rows[3], rows[2], 1.0f);
        writePlane(planes, FrustumPlanes.FAR, rows[3], rows[2], -1.0f);
        return FrustumPlanes.of(planes);
    }

    private static void writePlane(float[] out, int plane, float[] row3, float[] rowN, float sign) {
        float a = row3[0] + sign * rowN[0];
        float b = row3[1] + sign * rowN[1];
        float c = row3[2] + sign * rowN[2];
        float d = row3[3] + sign * rowN[3];
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        if (length > 0.0f) {
            a /= length;
            b /= length;
            c /= length;
            d /= length;
        }
        int offset = plane * 4;
        out[offset] = a;
        out[offset + 1] = b;
        out[offset + 2] = c;
        out[offset + 3] = d;
    }

    /** projection * view for a shadow camera, the feeder's construction (JOML, GL clip). */
    private static Matrix4f shadowCombined(Vector3f eye, Vector3f target,
                                           float halfExtent, float near, float far) {
        Matrix4f view = new Matrix4f().lookAt(eye, target, new Vector3f(0f, 1f, 0f));
        Matrix4f projection = new Matrix4f().ortho(
                -halfExtent, halfExtent, -halfExtent, halfExtent, near, far);
        return projection.mul(view);
    }

    // -- (ii) the two views never overwrite each other --------------------------

    @Test
    void preparingOneViewLeavesTheOtherViewBitIdentical() {
        TerrainWorld world = emptyWorld();
        TerrainDrawStage stage = stage(world, new ArrayList<>());
        assertFalse(world.hasShadowView());

        CameraPose mainPose = new CameraPose(100.5, 70.0, -200.25, 90f, 10f);
        FrustumPlanes mainFrustum = boxFrustum(50, 0, -300, 200, 160, -100);
        stage.prepareMainView(mainPose, mainFrustum, 7L);
        assertFalse(world.hasShadowView(), "preparing the main view must not invent a shadow view");
        float[] mainBefore = world.mainFrustum().coefficients();

        CameraPose shadowPose = new CameraPose(260.5, 230.0, -200.25, -90f, -45f);
        FrustumPlanes shadowFrustum = boxFrustum(0, 40, -360, 420, 320, -40);
        stage.prepareShadowView(shadowPose, shadowFrustum, 160.0f, 7L);

        assertTrue(world.hasShadowView());
        assertEquals(mainPose, world.mainPose());
        assertEquals(7L, world.mainFrameId());
        float[] mainAfter = world.mainFrustum().coefficients();
        assertEquals(java.util.Arrays.toString(mainBefore), java.util.Arrays.toString(mainAfter),
                "the shadow prepare must not touch a single main plane coefficient");
        assertEquals(shadowPose, world.shadowPose());
        assertEquals(shadowFrustum, world.shadowFrustum());
        assertEquals(160.0f, world.shadowDistance());
        assertEquals(7L, world.shadowFrameId());

        // Next frame: re-preparing the main view must leave the shadow slot untouched.
        float[] shadowBefore = world.shadowFrustum().coefficients();
        stage.prepareMainView(new CameraPose(101.5, 70.0, -199.25, 91f, 10f),
                boxFrustum(51, 0, -299, 201, 160, -99), 8L);
        float[] shadowAfter = world.shadowFrustum().coefficients();
        assertEquals(java.util.Arrays.toString(shadowBefore), java.util.Arrays.toString(shadowAfter),
                "the main re-prepare must not touch a single shadow plane coefficient");
        assertEquals(shadowPose, world.shadowPose());
        assertEquals(7L, world.shadowFrameId());
        assertEquals(8L, world.mainFrameId());
    }

    // -- (i)+(ii) record passes cull with their own view's frustum ---------------

    private static final class FakeStream implements VulkanCommandStream {
        @Override
        public long nativeCommandBuffer() {
            return 42L;
        }

        @Override
        public void beginRendering(ActiveRenderTargets targets) {
        }

        @Override
        public void endRendering() {
        }

        @Override
        public void bindTerrainProgram(TerrainStageProgram program) {
        }
    }

    private static TerrainStageProgram program(TerrainPassKind kind) {
        return new TerrainStageProgram(kind,
                SpirvModuleBundle.of(new int[]{0x07230203}, new int[]{0x07230203}),
                AttachmentFormatKey.of(new int[]{44}, 0, 0),
                BlendStateSpec.opaque(1), 0.0f, true, true, DescriptorBindingPlan.empty());
    }

    private static ActiveRenderTargets targets() {
        return new ActiveRenderTargets(
                List.of(new ActiveRenderTargets.RenderTargetView(7L, 44,
                        ActiveRenderTargets.LoadOp.LOAD, ActiveRenderTargets.StoreOp.STORE)),
                null, 64, 64);
    }

    /** Two one-quad sections: slot 0 at section (0,4,0), slot 1 at section (10,4,0). */
    private static TerrainWorld worldWithTwoSections() {
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 16));
        List<PackedAxisQuad> quads = List.of(
                PackedAxisQuad.of(0, 0, 0, 1, false, 1, 1, 0, 0, 0, 15, 15, 255, 0, 0, 0, 0));
        SectionGeometry geometry = new SectionGeometry(quads, List.of(), List.of(),
                List.of(), List.of(), List.of(), new short[0]);
        store.upload(0, 4, 0, geometry, 1L);
        store.upload(10, 4, 0, geometry, 1L);
        return new TerrainWorld(store);
    }

    @Test
    void mainAndShadowPassesCullWithTheirOwnFrustum() {
        TerrainWorld world = worldWithTwoSections();
        List<TerrainDrawStage.DrawCommand> drawn = new ArrayList<>();
        TerrainDrawStage stage = stage(world, drawn);
        // Main view sees only section slot 0 (blocks x 0..16); shadow view only slot 1
        // (blocks x 160..176). Both sections sit at y 64..80, z 0..16.
        // Frustum boxes are camera-relative: the production cull contract subtracts the
        // CameraPose from section AABBs, so each box below is the world box minus its pose.
        stage.prepareMainView(new CameraPose(8, 70, 8, 0, 0),
                boxFrustum(-16, -14, -16, 16, 18, 16), 1L);

        assertThrows(IllegalStateException.class,
                () -> stage.recordTerrainPass(new FakeStream(), TerrainPassKind.SHADOW_SOLID,
                        program(TerrainPassKind.SHADOW_SOLID), targets()),
                "a shadow pass before prepareShadowView must be refused, not culled by the main view");

        stage.recordTerrainPass(new FakeStream(), TerrainPassKind.MAIN_SOLID,
                program(TerrainPassKind.MAIN_SOLID), targets());
        assertEquals(1, drawn.size());
        assertEquals(0, drawn.get(0).sectionSlot(), "the main pass draws the main frustum's section");

        drawn.clear();
        stage.prepareShadowView(new CameraPose(168, 230, 8, 180, -90),
                boxFrustum(-16, -174, -16, 16, -142, 16), 160.0f, 1L);
        stage.recordTerrainPass(new FakeStream(), TerrainPassKind.SHADOW_SOLID,
                program(TerrainPassKind.SHADOW_SOLID), targets());
        assertEquals(1, drawn.size());
        assertEquals(1, drawn.get(0).sectionSlot(), "the shadow pass draws the shadow frustum's section");

        drawn.clear();
        stage.recordTerrainPass(new FakeStream(), TerrainPassKind.MAIN_SOLID,
                program(TerrainPassKind.MAIN_SOLID), targets());
        assertEquals(1, drawn.size());
        assertEquals(0, drawn.get(0).sectionSlot(),
                "preparing the shadow view must not move the main pass onto the shadow frustum");
    }

    // -- (i) orthographic extraction judges AABBs as the clip reference does -----

    /** Clip-space reference: an AABB is culled iff every corner is outside one clip plane. */
    private static boolean referenceVisible(Matrix4f combined, SectionDesc desc) {
        double minX = desc.sectionX() * 16.0;
        double minY = desc.sectionY() * 16.0;
        double minZ = desc.sectionZ() * 16.0;
        float[][] clip = new float[8][];
        int i = 0;
        for (int corner = 0; corner < 8; corner++) {
            Vector4f v = new Vector4f(
                    (float) ((corner & 1) == 0 ? minX : minX + 16.0),
                    (float) ((corner & 2) == 0 ? minY : minY + 16.0),
                    (float) ((corner & 4) == 0 ? minZ : minZ + 16.0), 1f);
            combined.transform(v);
            clip[i++] = new float[]{v.x, v.y, v.z, v.w};
        }
        // GL clip volume (the pack matrices' convention): -w <= x,y,z <= w.
        for (int axis = 0; axis < 3; axis++) {
            boolean allBelow = true;
            boolean allAbove = true;
            for (float[] c : clip) {
                if (c[axis] >= -c[3]) {
                    allBelow = false;
                }
                if (c[axis] <= c[3]) {
                    allAbove = false;
                }
            }
            if (allBelow || allAbove) {
                return false;
            }
        }
        return true;
    }

    @Test
    void orthoShadowExtractionMatchesTheClipSpaceReference() {
        // A rotated shadow camera (morning sun) over a moderate world position.
        Vector3f target = new Vector3f(1000f, 70f, -2000f);
        Vector3f sunDir = new Vector3f(0.6f, 0.8f, 0.0f).normalize();
        Vector3f eye = new Vector3f(target).add(new Vector3f(sunDir).mul(160f));
        Matrix4f combined = shadowCombined(eye, target, 160f, 0.05f, 320f);
        FrustumPlanes frustum = extractFrustum(combined);

        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 16));
        SectionGeometry empty = new SectionGeometry(List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), new short[0]);
        int centreX = Math.floorDiv(1000, 16);
        int centreZ = Math.floorDiv(-2000, 16);
        // Section offsets in blocks stay at least 16 blocks clear of the ortho box
        // faces (+/-160): a section corner exactly on a plane is a rounding coin-flip
        // between the two computation paths, not a verdict about either.
        for (int dx : new int[]{-11, -9, -4, 0, 4, 9, 11}) {
            for (int dz : new int[]{-11, -9, -4, 0, 4, 9, 11}) {
                for (int dy = -2; dy <= 2; dy += 2) {
                    store.upload(centreX + dx, 4 + dy, centreZ + dz, empty, 1L);
                }
            }
        }
        int visible = 0;
        int culled = 0;
        for (SectionDesc desc : store.descriptors()) {
            boolean expected = referenceVisible(combined, desc);
            boolean actual = TerrainCullStage.sectionVisible(frustum, desc);
            assertEquals(expected, actual,
                    "section (" + desc.sectionX() + "," + desc.sectionY() + "," + desc.sectionZ()
                            + ") judged differently by the extracted planes than by the matrix");
            if (actual) {
                visible++;
            } else {
                culled++;
            }
        }
        assertTrue(visible >= 8, "the grid must contain clearly visible sections, saw " + visible);
        assertTrue(culled >= 8, "the grid must contain clearly culled sections, saw " + culled);
    }

    // -- (iv) the G-7 gather at large coordinates --------------------------------

    private static Set<String> gather(TerrainDrawStage stage, FrustumPlanes frustum) {
        Set<String> found = new HashSet<>();
        stage.gatherShadowBlockEntities(frustum,
                (x, y, z, typeId) -> found.add(x + "," + y + "," + z + ":" + typeId));
        return found;
    }

    @Test
    void gatherCollectsExactlyTheShadowBoxAtLargeCoordinates() {
        TerrainWorld world = emptyWorld();
        TerrainDrawStage stage = stage(world, new ArrayList<>());
        int px = 29_999_900;
        int pz = -29_999_900;
        // Inside the shadow box (half extent 32 around the camera, y 30..110).
        world.putBlockEntity(px, 70, pz, 1);
        world.putBlockEntity(px + 31, 70, pz - 31, 2);
        world.putBlockEntity(px - 31, 30, pz + 31, 3);
        // Outside, each at least 7.5 blocks past a face (clear of the 1.0 grace).
        world.putBlockEntity(px + 40, 70, pz, 4);
        world.putBlockEntity(px, 70, pz + 40, 5);
        world.putBlockEntity(px - 40, 70, pz, 6);
        world.putBlockEntity(px, 130, pz, 7);
        // At the world origin: the offset-loss signature. If the shadow origin were
        // dropped anywhere on the path, this is the entity that would be gathered.
        world.putBlockEntity(0, 70, 0, 8);

        CameraPose origin = new CameraPose(px, 70, pz, 0, 0);
        FrustumPlanes shadow = boxFrustum(-32, -40, -32, 32, 40, 32);
        stage.prepareShadowView(origin, shadow, 160.0f, 1L);
        assertEquals(Set.of(px + ",70," + pz + ":1",
                        (px + 31) + ",70," + (pz - 31) + ":2",
                        (px - 31) + ",30," + (pz + 31) + ":3"),
                gather(stage, shadow),
                "the gather must return exactly the in-box entities at +/-29,999,900");
    }

    @Test
    void originDroppedFrustumGathersTheWrongSet() {
        // Negative control: the same registry, but a frustum built as if the shadow
        // origin had been dropped (the box sits at the world origin instead of at the
        // camera). It must collect the origin entity and miss the camera-side ones -
        // proving the large-coordinate assertions above can catch the offset class.
        TerrainWorld world = emptyWorld();
        TerrainDrawStage stage = stage(world, new ArrayList<>());
        int px = 29_999_900;
        int pz = -29_999_900;
        world.putBlockEntity(px, 70, pz, 1);
        world.putBlockEntity(0, 70, 0, 8);

        FrustumPlanes dropped = boxFrustum(-32, -40, -32, 32, 40, 32);
        stage.prepareShadowView(new CameraPose(0, 70, 0, 0, 0), dropped, 160.0f, 1L);
        assertEquals(Set.of("0,70,0:8"), gather(stage, dropped));
    }

    // -- (iii)+(iv) one origin for the gather and the shadow terrain cull ---------

    @Test
    void gatherAndShadowCullShareOneOriginAtLargeCoordinates() {
        int px = 29_999_900;
        int pz = -29_999_900;
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 16));
        SectionGeometry empty = new SectionGeometry(List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), new short[0]);
        int insideSectionX = Math.floorDiv(px, 16);
        int insideSectionZ = Math.floorDiv(pz, 16);
        store.upload(insideSectionX, 4, insideSectionZ, empty, 1L);
        store.upload(0, 4, 0, empty, 1L);
        TerrainWorld world = new TerrainWorld(store);
        TerrainCullStage cull = new TerrainCullStage(world);
        TerrainDrawStage stage = stage(world, new ArrayList<>());
        world.putBlockEntity(px, 70, pz, 1);
        world.putBlockEntity(0, 70, 0, 2);

        CameraPose origin = new CameraPose(px, 70, pz, 0, 0);
        FrustumPlanes shadow = boxFrustum(-32, -40, -32, 32, 40, 32);
        stage.prepareShadowView(origin, shadow, 160.0f, 1L);
        List<SectionDesc> visible = cull.visibleSections(shadow, origin);
        assertEquals(1, visible.size());
        assertEquals(insideSectionX, visible.get(0).sectionX());
        assertEquals(insideSectionZ, visible.get(0).sectionZ());
        assertEquals(Set.of(px + ",70," + pz + ":1"), gather(stage, shadow),
                "the gather over the same frustum must agree with the shadow cull's origin");
    }

    @Test
    void shadowGatherIsAnchoredAtTheFrameStateCameraPosition() {
        // The pack-side origin: ShaderFrameState.cameraPosition is the world-absolute
        // camera the uniforms publish. Build the shadow view over that state exactly
        // the way the host feeder builds the matrices: the optical eye is displaced
        // along the sun direction within model-view, relative to cameraPosition.
        ShaderFrameState state = new ShaderFrameState();
        state.cameraPosition.set(29_999_900.0, 70.0, -29_999_900.0);
        Vector3f sunDir = new Vector3f(1f, 1f, 0f).normalize();
        float shadowDistance = 160.0f;
        Vector3f eyeOffset = new Vector3f(sunDir).mul(shadowDistance);
        state.shadowModelView.set(new Matrix4f().lookAt(eyeOffset, new Vector3f(), new Vector3f(0f, 1f, 0f)));
        state.shadowProjection.set(new Matrix4f().ortho(
                -shadowDistance, shadowDistance, -shadowDistance, shadowDistance,
                0.05f, shadowDistance * 2.0f));
        Matrix4f combined = new Matrix4f(state.shadowProjection).mul(state.shadowModelView);
        FrustumPlanes shadowFrustum = extractFrustum(combined);

        TerrainWorld world = emptyWorld();
        TerrainDrawStage stage = stage(world, new ArrayList<>());
        stage.prepareShadowView(new CameraPose(state.cameraPosition.x, state.cameraPosition.y,
                state.cameraPosition.z, -135f, -45f), shadowFrustum, shadowDistance, 1L);
        int px = 29_999_900;
        int pz = -29_999_900;
        world.putBlockEntity(px, 70, pz, 1);
        world.putBlockEntity(px + 100, 70, pz, 2);
        world.putBlockEntity(px + 300, 70, pz, 3);
        world.putBlockEntity(px, 70, pz + 300, 4);
        world.putBlockEntity(0, 70, 0, 5);

        assertEquals(Set.of(px + ",70," + pz + ":1", (px + 100) + ",70," + pz + ":2"),
                gather(stage, world.shadowFrustum()),
                "the gather must collect around cameraPosition itself: no double offset, none lost");
    }
}
