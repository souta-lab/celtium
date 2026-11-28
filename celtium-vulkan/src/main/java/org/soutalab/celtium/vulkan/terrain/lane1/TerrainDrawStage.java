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
    // TEMP G1 Step 2 diagnostic (NEON-G1-DEPTH-RULING section 3.2): whether the forward
    // surface section (8,4,0) is in the main view's visible set. Strip after the G1 run.
    private boolean tempG1Section840Visible;
    private boolean tempG1Section840EverVisible;

    // The visible set and its layer ranges, computed once per (view, store generation):
    // the three main-layer passes of a frame share one frustum and one residency, and the
    // first version of this stage re-ran the full cull + a cloning range lookup for every
    // section on every layer (2,902 sections x 3 layers per frame at Gate G1).
    private FrustumPlanes cachedFrustum;
    private CameraPose cachedPose;
    private boolean cachedShadow;
    private long cachedGeneration = -1L;
    private List<SectionDesc> cachedVisible = List.of();
    private List<TerrainSectionStore.LayerRange[]> cachedRanges = List.of();
    private int[] cachedSlots = new int[0];

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
        TerrainSectionStore store = world.store();
        long generation = store.generation();
        // The published frustum planes are camera-relative, so the cull test needs the view's
        // pose to shift section AABBs by the camera origin (NEON-G1-DEPTH-RULING defect #1).
        // The pose is part of the cache key: a reused FrustumPlanes instance must not pin a
        // stale visible set after the camera moves.
        CameraPose pose = kind.isShadow() ? world.shadowPose() : world.mainPose();
        if (cachedFrustum != frustum || cachedShadow != kind.isShadow()
                || cachedGeneration != generation || !java.util.Objects.equals(pose, cachedPose)) {
            List<SectionDesc> descriptors = store.descriptors();
            List<SectionDesc> visible = new ArrayList<>();
            List<TerrainSectionStore.LayerRange[]> ranges = new ArrayList<>();
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < descriptors.size(); slot++) {
                SectionDesc desc = descriptors.get(slot);
                if (!TerrainCullStage.sectionVisible(frustum, pose, desc)) {
                    continue;
                }
                TerrainSectionStore.LayerRange[] layerRanges = store.layerRangesRef(
                        desc.sectionX(), desc.sectionY(), desc.sectionZ());
                if (layerRanges == null) {
                    continue;
                }
                visible.add(desc);
                ranges.add(layerRanges);
                slots.add(Integer.valueOf(slot));
            }
            cachedFrustum = frustum;
            cachedPose = pose;
            cachedShadow = kind.isShadow();
            cachedGeneration = generation;
            cachedVisible = visible;
            cachedRanges = ranges;
            cachedSlots = slots.stream().mapToInt(Integer::intValue).toArray();
            if (!kind.isShadow()) {
                // TEMP G1 Step 2 diagnostic: see the field declaration.
                boolean found840 = false;
                for (SectionDesc desc : visible) {
                    if (desc.sectionX() == 8 && desc.sectionY() == 4 && desc.sectionZ() == 0) {
                        found840 = true;
                        break;
                    }
                }
                tempG1Section840Visible = found840;
                tempG1Section840EverVisible |= found840;
            }
        }
        // Opaque kinds are depth-tested and order-free, so their commands are grouped by
        // stream (all axis, then all general): the sink binds a pipeline per stream switch,
        // and interleaving the streams per section made it bind ~once per draw (Gate G1
        // profile). Translucent kinds keep section order - their blending is order-bound.
        boolean groupStreams = kind != TerrainPassKind.MAIN_TRANSLUCENT
                && kind != TerrainPassKind.SHADOW_TRANSLUCENT;
        List<DrawCommand> commands = new ArrayList<>();
        List<DrawCommand> generalCommands = groupStreams ? new ArrayList<>() : commands;
        long quads = 0L;
        int visibleCount = cachedVisible.size();
        for (int i = 0; i < cachedVisible.size(); i++) {
            SectionDesc desc = cachedVisible.get(i);
            int slot = cachedSlots[i];
            TerrainSectionStore.LayerRange range = cachedRanges.get(i)[layer];
            if (range.axisQuadCount() > 0) {
                commands.add(new DrawCommand(range.axisQuadCount() * 6,
                        (int) (desc.axisSlabOffset() / 16L) + range.axisQuadOffset(), slot, layer, false));
                quads += range.axisQuadCount();
            }
            if (range.generalQuadCount() > 0) {
                generalCommands.add(new DrawCommand(range.generalQuadCount() * 6,
                        (int) (desc.generalSlabOffset() / GeneralQuad.BYTE_SIZE) + range.generalQuadOffset(), slot, layer, true));
                quads += range.generalQuadCount();
            }
        }
        if (groupStreams) {
            commands.addAll(generalCommands);
        }
        if (layer == 0 && !kind.isShadow()) {
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
                + " blockEntities=" + world.blockEntityCount()
                + " temp840now=" + tempG1Section840Visible
                + " temp840ever=" + tempG1Section840EverVisible;
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
