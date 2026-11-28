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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void recordEmitsOneDrawPerVisibleSectionLayer() {
        TerrainWorld world = worldWithTwoSections();
        TerrainCullStage cull = new TerrainCullStage(world);
        List<TerrainDrawStage.DrawCommand> drawn = new ArrayList<>();
        TerrainDrawStage stage = new TerrainDrawStage(world, cull,
                (cmd, kind, commands) -> drawn.addAll(commands));
        stage.prepareMainView(new CameraPose(8, 70, 8, 0, 0), xPositive(), 1L);
        FakeStream stream = new FakeStream();
        ActiveRenderTargets targets = new ActiveRenderTargets(
                List.of(new ActiveRenderTargets.RenderTargetView(7L, 44,
                        ActiveRenderTargets.LoadOp.LOAD, ActiveRenderTargets.StoreOp.STORE)),
                null, 64, 64);
        stage.recordTerrainPass(stream, TerrainPassKind.MAIN_SOLID, program(), targets);
        assertEquals(List.of("begin", "bind", "end"), stream.calls);
        assertEquals(1, drawn.size(), "only the x >= 0 section survives the frustum");
        assertEquals(12, drawn.get(0).vertexCount(), "two quads, six vertices each");
        assertEquals(0, drawn.get(0).layer());
    }

    @Test
    void recordBeforePrepareThrows() {
        TerrainWorld world = worldWithTwoSections();
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world),
                (cmd, kind, commands) -> { });
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
                (cmd, kind, commands) -> { });
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
        List<TerrainDrawStage.DrawCommand> drawn = new ArrayList<>();
        TerrainDrawStage stage = new TerrainDrawStage(world, new TerrainCullStage(world),
                (cmd, kind, commands) -> drawn.addAll(commands));
        stage.prepareMainView(new CameraPose(8, 70, 8, 0, 0), allInside(), 1L);
        ActiveRenderTargets targets = new ActiveRenderTargets(List.of(), null, 64, 64);
        stage.recordTerrainPass(new FakeStream(), TerrainPassKind.MAIN_SOLID, program(), targets);
        List<TerrainDrawStage.DrawCommand> general = drawn.stream()
                .filter(TerrainDrawStage.DrawCommand::general).toList();
        assertEquals(2, general.size(), "one general draw per section");
        TerrainDrawStage.DrawCommand second = general.stream()
                .filter(command -> command.vertexCount() == 6).findFirst().orElseThrow();
        assertEquals(4, second.firstQuad(),
                "the second section's records start after the first section's four 80 B records");
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
