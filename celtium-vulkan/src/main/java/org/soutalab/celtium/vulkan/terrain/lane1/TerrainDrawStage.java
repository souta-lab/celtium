package org.soutalab.celtium.vulkan.terrain.lane1;

import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.BlockEntitySink;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.frame.VulkanCommandStream;
import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.SectionDesc;
import org.soutalab.celtium.core.shader.PackTerrainDriver;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;

import java.util.ArrayList;
import java.util.List;

/**
 * The Lane 1 draw stage: celtium's {@link PackTerrainDriver} (design V040-FINAL-PLAN section 2.3,
 * interface 1). The two {@code prepare*} calls install the frame's views in the {@link
 * TerrainWorld}; {@link #recordTerrainPass} brackets the pass in dynamic rendering, binds the
 * program, and emits the pass's draws - one indirect draw per (visible section, layer) run, six
 * vertices per quad, vertex-pulled from the shared resident slab (the main and shadow stages read
 * the same geometry; falsification condition K1's vertex-pulling MDI shape).
 * <p>
 * The draw emission itself goes through a {@link DrawSink}: in production the sink writes the
 * commands into the cull stage's indirect buffer and issues {@code vkCmdDrawIndirectCount}; on
 * the CPU-cull path (and in tests) it records from the command list this stage builds. The
 * command list is pure data - {@link DrawCommand} - so the stage's decisions are unit-testable
 * without a device.
 */
public final class TerrainDrawStage implements PackTerrainDriver {

    /**
     * One indirect draw: {@code vertexCount} vertices starting at quad {@code firstQuad} of a slab.
     * {@code general} selects the stream - the 80 B general slab when true, the 16 B axis slab when
     * false; the two streams share the pass's fragment stage and differ in the vertex entry point.
     */
    public record DrawCommand(int vertexCount, int firstQuad, int sectionSlot, int layer, boolean general) {
    }

    /** Receives one pass's draw commands against the pass's command stream. */
    @FunctionalInterface
    public interface DrawSink {
        void draw(VulkanCommandStream cmd, TerrainPassKind kind, List<DrawCommand> commands);
    }

    private final TerrainWorld world;
    private final TerrainCullStage cullStage;
    private final DrawSink drawSink;
    private long lastDrawnQuads;
    private int lastVisibleSections;

    public TerrainDrawStage(TerrainWorld world, TerrainCullStage cullStage, DrawSink drawSink) {
        this.world = world;
        this.cullStage = cullStage;
        this.drawSink = drawSink;
    }

    @Override
    public void prepareMainView(CameraPose cam, FrustumPlanes frustum, long frameId) {
        world.setMainView(cam, frustum, frameId);
    }

    @Override
    public void prepareShadowView(CameraPose shadowCam, FrustumPlanes shadowFrustum,
                                  float shadowDistance, long frameId) {
        world.setShadowView(shadowCam, shadowFrustum, shadowDistance, frameId);
    }

    @Override
    public void recordTerrainPass(VulkanCommandStream cmd, TerrainPassKind kind,
                                  TerrainStageProgram program, ActiveRenderTargets targets) {
        FrustumPlanes frustum = kind.isShadow() ? world.shadowFrustum() : world.mainFrustum();
        if (frustum == null) {
            throw new IllegalStateException("terrain pass " + kind + " recorded before its view "
                    + "was prepared");
        }
        int layer = layerOf(kind);
        List<SectionDesc> descriptors = world.store().descriptors();
        List<DrawCommand> commands = new ArrayList<>();
        long quads = 0L;
        int visibleCount = 0;
        for (int slot = 0; slot < descriptors.size(); slot++) {
            SectionDesc desc = descriptors.get(slot);
            if (!TerrainCullStage.sectionVisible(frustum, desc)) {
                continue;
            }
            visibleCount++;
            TerrainSectionStore.LayerRange range = world.store()
                    .layerRanges(desc.sectionX(), desc.sectionY(), desc.sectionZ())[layer];
            if (range.axisQuadCount() > 0) {
                commands.add(new DrawCommand(range.axisQuadCount() * 6,
                        (int) (desc.axisSlabOffset() / 16L) + range.axisQuadOffset(), slot, layer, false));
                quads += range.axisQuadCount();
            }
            if (range.generalQuadCount() > 0) {
                commands.add(new DrawCommand(range.generalQuadCount() * 6,
                        (int) (desc.generalSlabOffset() / GeneralQuad.BYTE_SIZE) + range.generalQuadOffset(), slot, layer, true));
                quads += range.generalQuadCount();
            }
        }
        cmd.beginRendering(targets);
        try {
            cmd.bindTerrainProgram(program);
            drawSink.draw(cmd, kind, List.copyOf(commands));
        } finally {
            cmd.endRendering();
        }
        lastVisibleSections = visibleCount;
        lastDrawnQuads = quads;
    }

    @Override
    public void gatherShadowBlockEntities(FrustumPlanes shadowFrustum, BlockEntitySink sink) {
        for (TerrainWorld.BlockEntityEntry entry : world.blockEntitiesIn(shadowFrustum)) {
            sink.accept(entry.blockX(), entry.blockY(), entry.blockZ(), entry.typeId());
        }
    }

    @Override
    public String diagnosticsSummary() {
        return "lane1 terrain: sections=" + world.store().sectionCount()
                + " visible=" + lastVisibleSections
                + " lastPassQuads=" + lastDrawnQuads
                + " axisBytes=" + world.store().axisUsedBytes()
                + " generalBytes=" + world.store().generalUsedBytes()
                + " auxBytes=" + world.store().auxUsedBytes()
                + " blockEntities=" + world.blockEntityCount();
    }

    /** Pass kind to store layer: SOLID kinds draw layer 0, CUTOUT layer 1, TRANSLUCENT layer 2. */
    static int layerOf(TerrainPassKind kind) {
        return switch (kind) {
            case MAIN_SOLID, SHADOW_SOLID -> TerrainSectionStore.LAYER_SOLID;
            case MAIN_CUTOUT, SHADOW_CUTOUT -> TerrainSectionStore.LAYER_CUTOUT;
            case MAIN_TRANSLUCENT, SHADOW_TRANSLUCENT -> TerrainSectionStore.LAYER_TRANSLUCENT;
        };
    }
}
