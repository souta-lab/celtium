package org.soutalab.celtium.vulkan.terrain.lane1;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.frame.VulkanCommandStream;
import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;
import org.soutalab.celtium.core.shader.AttachmentFormatKey;
import org.soutalab.celtium.core.shader.BlendStateSpec;
import org.soutalab.celtium.core.shader.DescriptorBindingPlan;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainDrawStageTest {

    /** A frustum that contains everything: every plane is (0,0,0,1). */
    private static FrustumPlanes allInside() {
        float[] coefficients = new float[FrustumPlanes.FLOAT_COUNT];
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            coefficients[plane * 4 + 3] = 1f;
        }
        return FrustumPlanes.of(coefficients);
    }

    /** A frustum that only keeps x >= 0. */
    private static FrustumPlanes xPositive() {
        float[] coefficients = new float[FrustumPlanes.FLOAT_COUNT];
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            coefficients[plane * 4 + 3] = 1f;
        }
        coefficients[0] = 1f;      // left plane: x >= 0
        coefficients[3] = 0f;
        return FrustumPlanes.of(coefficients);
    }

    private static final class FakeStream implements VulkanCommandStream {
        final List<String> calls = new ArrayList<>();

        @Override
        public long nativeCommandBuffer() {
            return 42L;
        }

        @Override
        public void beginRendering(ActiveRenderTargets targets) {
            calls.add("begin");
        }

        @Override
        public void endRendering() {
            calls.add("end");
        }

        @Override
        public void bindTerrainProgram(TerrainStageProgram program) {
            calls.add("bind");
        }
    }

    private static TerrainStageProgram program() {
        return new TerrainStageProgram(TerrainPassKind.MAIN_SOLID,
                SpirvModuleBundle.of(new int[]{0x07230203}, new int[]{0x07230203}),
                AttachmentFormatKey.of(new int[]{44}, 0, 0),
                BlendStateSpec.opaque(1), 0.0f, true, true, DescriptorBindingPlan.empty());
    }

    private static TerrainWorld worldWithTwoSections() {
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 16));
        List<PackedAxisQuad> quads = List.of(
                PackedAxisQuad.of(0, 0, 0, 1, false, 1, 1, 0, 0, 0, 15, 15, 255, 0, 0, 0, 0),
                PackedAxisQuad.of(1, 0, 0, 1, false, 1, 1, 0, 0, 0, 15, 15, 255, 0, 0, 0, 0));
        SectionGeometry geometry = new SectionGeometry(quads, List.of(), List.of(),
                List.of(), List.of(), List.of(), new short[0]);
        store.upload(0, 0, 0, geometry, 1L);
        store.upload(-2, 0, 0, geometry, 1L);
        return new TerrainWorld(store);
    }

    private static final class PackedDraws implements TerrainDrawStage.DrawSink {
        TerrainPassKind kind;
        int[] packed;
        int[] copy;
        int count;

        @Override
        public void draw(VulkanCommandStream stream, TerrainPassKind kind, int[] packed, int commandCount) {
            this.kind = kind;
            this.packed = packed;
            this.copy = Arrays.copyOf(packed, commandCount * 4);
            this.count = commandCount;
        }
    }

    @Test
    void recordEmitsOneDrawPerVisibleSectionLayer() {
        TerrainWorld world = worldWithTwoSections();
        TerrainCullStage cull = new TerrainCullStage(world);
        PackedDraws drawn = new PackedDraws();
        TerrainDrawStage stage = new TerrainDrawStage(world, cull, drawn);
        stage.prepareMainView(new CameraPose(8, 70, 8, 0, 0), xPositive(), 1L);
        FakeStream stream = new FakeStream();
        ActiveRenderTargets targets = new ActiveRenderTargets(
                List.of(new ActiveRenderTargets.RenderTargetView(7L, 44,
                        ActiveRenderTargets.LoadOp.LOAD, ActiveRenderTargets.StoreOp.STORE)),
                null, 64, 64);
        stage.recordTerrainPass(stream, TerrainPassKind.MAIN_SOLID, program(), targets);
        assertEquals(List.of("begin", "bind", "end"), stream.calls);
        assertEquals(1, drawn.count, "only the x >= 0 section survives the frustum");
        assertEquals(12, drawn.copy[0], "two quads, six vertices each");
        assertEquals(0, TerrainDrawStage.layerOf(drawn.kind));
    }

    @Test
    void recordBeforePrepareThrows() {
        TerrainWorld world = worldWithTwoSections();
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world),
                (cmd, kind, packed, count) -> { });
        ActiveRenderTargets targets = new ActiveRenderTargets(List.of(), null, 64, 64);
        assertThrows(IllegalStateException.class,
                () -> stage.recordTerrainPass(new FakeStream(), TerrainPassKind.MAIN_SOLID,
                        program(), targets));
    }

    @Test
    void shadowGatherFeedsTheSinkFromTheRegistry() {
        TerrainWorld world = worldWithTwoSections();
        world.putBlockEntity(4, 64, 4, 99);
        world.putBlockEntity(-500, 64, -500, 98);
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world),
                (cmd, kind, packed, count) -> { });
        FrustumPlanes frustum = xPositive();
        stage.prepareShadowView(new CameraPose(0, 64, 0, 0, 0), frustum, 160.0f, 1L);
        AtomicInteger gathered = new AtomicInteger();
        stage.gatherShadowBlockEntities(frustum, (x, y, z, type) -> {
            gathered.incrementAndGet();
            assertEquals(99, type);
        });
        assertEquals(1, gathered.get(), "only the in-frustum entity is gathered (G-7)");
        assertTrue(stage.diagnosticsSummary().contains("sections=2"));
    }

    @Test
    void generalDrawStartsAtTheSectionsRecordIndex() {
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 16));
        GeneralQuad quad = new GeneralQuad(new float[12], 1, 0, 0, 0);
        SectionGeometry fourGeneral = new SectionGeometry(List.of(), List.of(), List.of(),
                List.of(quad, quad, quad, quad), List.of(), List.of(), new short[0]);
        SectionGeometry oneGeneral = new SectionGeometry(List.of(), List.of(), List.of(),
                List.of(quad), List.of(), List.of(), new short[0]);
        store.upload(0, 0, 0, fourGeneral, 1L);
        store.upload(1, 0, 0, oneGeneral, 1L);
        TerrainWorld world = new TerrainWorld(store);
        PackedDraws drawn = new PackedDraws();
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world), drawn);
        stage.prepareMainView(new CameraPose(8, 70, 8, 0, 0), allInside(), 1L);
        ActiveRenderTargets targets = new ActiveRenderTargets(List.of(), null, 64, 64);
        stage.recordTerrainPass(new FakeStream(), TerrainPassKind.MAIN_SOLID, program(), targets);
        assertEquals(2, drawn.count, "one general draw per section");
        assertEquals(1, drawn.copy[3]);
        assertEquals(1, drawn.copy[7]);
        assertEquals(6, drawn.copy[4], "the second section has one quad");
        assertEquals(4, drawn.copy[5],
                "the second section's records start after the first section's four 80 B records");
    }

    @Test
    void packedEmissionPreservesOpaqueGroupingAndTranslucentSectionOrder() {
        PackedAxisQuad axis = PackedAxisQuad.of(0, 0, 0, 1, false, 1, 1, 0, 0, 0,
                15, 15, 255, 0, 0, 0, 0);
        GeneralQuad general = new GeneralQuad(new float[12], 1, 0, 0, 0);
        SectionGeometry geometry = new SectionGeometry(List.of(axis), List.of(axis, axis), List.of(axis),
                List.of(general, general), List.of(), List.of(general), new short[0]);
        // Use fresh slabs. Upload in reverse descriptor order so firstQuad and slot cannot be confused.
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 16));
        store.upload(1, 0, 0, geometry, 1L);
        store.upload(0, 0, 0, geometry, 1L);
        TerrainWorld world = new TerrainWorld(store);
        PackedDraws drawn = new PackedDraws();
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world), drawn);
        stage.prepareMainView(new CameraPose(0, 0, 0, 0, 0), allInside(), 1L);
        ActiveRenderTargets targets = new ActiveRenderTargets(List.of(), null, 64, 64);
        stage.recordTerrainPass(new FakeStream(), TerrainPassKind.MAIN_SOLID, program(), targets);
        assertArrayEquals(new int[]{
                6, 4, 0, 0,
                6, 0, 1, 0,
                12, 3, 0, 1,
                12, 0, 1, 1
        }, drawn.copy);
        int[] buffer = drawn.packed;

        stage.prepareMainView(new CameraPose(1, 0, 0, 0, 0), allInside(), 2L);
        stage.recordTerrainPass(new FakeStream(), TerrainPassKind.MAIN_TRANSLUCENT, program(), targets);
        assertArrayEquals(new int[]{
                6, 7, 0, 0,
                6, 5, 0, 1,
                6, 3, 1, 0,
                6, 2, 1, 1
        }, drawn.copy);
        assertSame(buffer, drawn.packed, "a view change reuses the command buffer");
    }

    @Test
    void indirectPassUsesTheSinkWithoutPreparingACpuView() {
        TerrainWorld world = worldWithTwoSections();
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world),
                (stream, kind, packed, count) -> { throw new AssertionError("CPU draw"); });
        FakeStream commands = new FakeStream();
        ActiveRenderTargets targets = new ActiveRenderTargets(List.of(), null, 64, 64);
        assertFalse(stage.indirectArmed(0));
        stage.setIndirectDrawSink((stream, kind, layer) -> {
            assertSame(commands, stream);
            assertEquals(TerrainPassKind.MAIN_CUTOUT, kind);
            assertEquals(1, layer);
            commands.calls.add("indirect");
        });
        stage.armIndirect(1);
        assertTrue(stage.indirectArmed(1));
        assertFalse(stage.indirectArmed(2));
        stage.recordTerrainPassIndirect(commands, TerrainPassKind.MAIN_CUTOUT, program(), targets);
        assertEquals(List.of("begin", "bind", "indirect", "end"), commands.calls);

        stage.setIndirectDrawSink(null);
        assertThrows(IllegalStateException.class,
                () -> stage.recordTerrainPassIndirect(commands, TerrainPassKind.MAIN_CUTOUT, program(), targets));
    }

    @Test
    void cullStageVisibilityMatchesTheDrawStage() {
        TerrainWorld world = worldWithTwoSections();
        TerrainCullStage cull = new TerrainCullStage(world);
        assertEquals(2, cull.visibleSections(allInside()).size());
        assertEquals(1, cull.visibleSections(xPositive()).size());
        assertEquals(10, TerrainCullStage.hiZPyramidLevels(1920, 1080));
    }
}
