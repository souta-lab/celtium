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

import java.util.Arrays;
import java.util.List;

/**
 * The Lane 1 draw stage: celtium's {@link PackTerrainDriver} (design V040-FINAL-PLAN section 2.3,
 * interface 1). The two {@code prepare*} calls install the frame's views in the {@link
 * TerrainWorld}; {@link #recordTerrainPass} brackets the pass in dynamic rendering, binds the
 * program, and emits the pass's draws - one indirect draw per (visible section, layer) run, six
 * vertices per quad, vertex-pulled from the shared resident slab (the main and shadow stages read
 * the same geometry; falsification condition K1's vertex-pulling MDI shape).
 * <p>
 * The CPU path emits packed commands through {@link DrawSink}. The main-view GPU path
 * delegates the prebuilt command regions to {@link IndirectDrawSink}.
 */
public final class TerrainDrawStage implements PackTerrainDriver {

    /** Receives the first commandCount records, each containing vertexCount, firstQuad, slot and stream. */
    @FunctionalInterface
    public interface DrawSink {
        void draw(VulkanCommandStream stream, TerrainPassKind kind, int[] packed, int commandCount);
    }

    @FunctionalInterface
    public interface IndirectDrawSink {
        void drawPass(VulkanCommandStream stream, TerrainPassKind kind, int layer);
    }

    /** Compatibility for callers that consume the original per-pass list. */
    @Deprecated
    public record DrawCommand(int vertexCount, int firstQuad, int sectionSlot, int layer, boolean general) {
    }

    @Deprecated
    @FunctionalInterface
    public interface LegacyDrawSink {
        void draw(VulkanCommandStream stream, TerrainPassKind kind, List<DrawCommand> commands);
    }

    private final TerrainWorld world;
    private final DrawSink drawSink;
    private long lastDrawnQuads;
    private int lastVisibleSections;

    // The visible set and its layer ranges, computed once per (view, store generation):
    // the three main-layer passes of a frame share one frustum and one residency, and the
    // first version of this stage re-ran the full cull + a cloning range lookup for every
    // section on every layer (2,902 sections x 3 layers per frame at Gate G1).
    private FrustumPlanes cachedFrustum;
    private CameraPose cachedPose;
    private boolean cachedShadow;
    private long cachedGeneration = -1L;
    private SectionDesc[] cacheDescs = new SectionDesc[0];
    private TerrainSectionStore.LayerRange[][] cacheRanges = new TerrainSectionStore.LayerRange[0][];
    private int[] cacheSlots = new int[0];
    private int cacheCount;
    private int[] packed = new int[0];
    private IndirectDrawSink indirectSink;
    private long indirectFrame;
    private boolean armed;

    public TerrainDrawStage(TerrainWorld world, TerrainCullStage cullStage, DrawSink drawSink) {
        this.world = world;
        this.drawSink = drawSink;
    }

    @Deprecated
    public TerrainDrawStage(TerrainWorld world, TerrainCullStage cullStage, LegacyDrawSink drawSink) {
        this(world, cullStage, (stream, kind, packed, count) -> {
            List<DrawCommand> commands = new java.util.ArrayList<>(count);
            int layer = layerOf(kind);
            for (int index = 0; index < count; index++) {
                int base = index * 4;
                commands.add(new DrawCommand(packed[base], packed[base + 1], packed[base + 2],
                        layer, packed[base + 3] != 0));
            }
            drawSink.draw(stream, kind, List.copyOf(commands));
        });
    }

    public void setIndirectDrawSink(IndirectDrawSink sink) {
        indirectSink = sink;
    }

    public void armIndirect(long frameId) {
        indirectFrame = frameId;
        armed = true;
    }

    void disarmIndirect() {
        armed = false;
    }

    public boolean indirectArmed(long frameId) {
        return armed && indirectFrame == frameId;
    }

    public void recordTerrainPassIndirect(VulkanCommandStream stream, TerrainPassKind kind,
                                          TerrainStageProgram program, ActiveRenderTargets targets) {
        if (indirectSink == null) {
            recordTerrainPass(stream, kind, program, targets);
            return;
        }
        stream.beginRendering(targets);
        try {
            if (program != null) {
                stream.bindTerrainProgram(program);
            }
            indirectSink.drawPass(stream, kind, layerOf(kind));
        } finally {
            stream.endRendering();
        }
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
            if (descriptors.size() > cacheSlots.length) {
                int capacity = Math.max(descriptors.size(), Math.max(16, cacheSlots.length * 2));
                cacheSlots = Arrays.copyOf(cacheSlots, capacity);
                cacheDescs = Arrays.copyOf(cacheDescs, capacity);
                cacheRanges = Arrays.copyOf(cacheRanges, capacity);
                packed = new int[capacity * 8];
            }
            int count = 0;
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
                cacheDescs[count] = desc;
                cacheRanges[count] = layerRanges;
                cacheSlots[count++] = slot;
            }
            if (count < cacheCount) {
                Arrays.fill(cacheDescs, count, cacheCount, null);
                Arrays.fill(cacheRanges, count, cacheCount, null);
            }
            cacheCount = count;
            cachedFrustum = frustum;
            cachedPose = pose;
            cachedShadow = kind.isShadow();
            cachedGeneration = generation;
        }
        // Opaque passes group streams to limit pipeline switches. Translucent passes keep
        // the section order used by the CPU blend path.
        boolean groupStreams = kind != TerrainPassKind.MAIN_TRANSLUCENT
                && kind != TerrainPassKind.SHADOW_TRANSLUCENT;
        int count = 0;
        int generalCount = 0;
        long quads = 0L;
        for (int i = 0; i < cacheCount; i++) {
            SectionDesc desc = cacheDescs[i];
            int slot = cacheSlots[i];
            TerrainSectionStore.LayerRange range = cacheRanges[i][layer];
            if (range.axisQuadCount() > 0) {
                emit(count++, range.axisQuadCount(),
                        (int) (desc.axisSlabOffset() / 16L) + range.axisQuadOffset(), slot, 0);
                quads += range.axisQuadCount();
            }
            if (range.generalQuadCount() > 0) {
                int index = groupStreams ? cacheCount + generalCount++ : count++;
                emit(index, range.generalQuadCount(),
                        (int) (desc.generalSlabOffset() / GeneralQuad.BYTE_SIZE) + range.generalQuadOffset(),
                        slot, 1);
                quads += range.generalQuadCount();
            }
        }
        if (generalCount > 0) {
            System.arraycopy(packed, cacheCount * 4, packed, count * 4, generalCount * 4);
            count += generalCount;
        }
        cmd.beginRendering(targets);
        try {
            cmd.bindTerrainProgram(program);
            drawSink.draw(cmd, kind, packed, count);
        } finally {
            cmd.endRendering();
        }
        lastVisibleSections = cacheCount;
        lastDrawnQuads = quads;
    }

    private void emit(int index, int quadCount, int firstQuad, int slot, int stream) {
        int base = index * 4;
        packed[base] = quadCount * 6;
        packed[base + 1] = firstQuad;
        packed[base + 2] = slot;
        packed[base + 3] = stream;
    }

    @Override
    public void gatherShadowBlockEntities(FrustumPlanes shadowFrustum, BlockEntitySink sink) {
        CameraPose shadowOrigin = world.shadowPose();
        if (shadowOrigin == null) {
            throw new IllegalStateException("shadow block entities gathered before the shadow view was prepared");
        }
        for (TerrainWorld.BlockEntityEntry entry : world.blockEntitiesIn(shadowFrustum, shadowOrigin)) {
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
