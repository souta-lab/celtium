/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3: uses NamespacedId + TerrainPassKind with zero
 * Minecraft / Celeritas / GLSM dependencies.
 */
package org.soutalab.celtium.shader.block_rendering;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.shader.shaderpack.IdMap;
import org.soutalab.celtium.shader.shaderpack.materialmap.BlockEntry;
import org.soutalab.celtium.shader.shaderpack.materialmap.BlockRenderType;
import org.soutalab.celtium.shader.shaderpack.materialmap.NamespacedId;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolved block-id and render-layer mapping built from a shader pack's {@link IdMap}, keyed purely
 * by {@link NamespacedId} and metadata (or block-state properties).
 */
public final class BlockRenderingSettings {

    public static final int UNMAPPED_ID = -1;

    private final Map<NamespacedId, Int2IntMap> blockMetaIds;
    private final Object2IntMap<NamespacedId> defaultBlockIds;
    private final Map<NamespacedId, TerrainPassKind> blockPassOverrides;
    private final Object2IntMap<NamespacedId> entityIds;
    private final Object2IntMap<NamespacedId> itemIds;
    private final float ambientOcclusionLevel;
    private final boolean disableDirectionalShading;
    private final boolean useSeparateAo;
    private final boolean writeStainedGlassDepth;

    public BlockRenderingSettings(
            Map<NamespacedId, Int2IntMap> blockMetaIds,
            Object2IntMap<NamespacedId> defaultBlockIds,
            Map<NamespacedId, TerrainPassKind> blockPassOverrides,
            Object2IntMap<NamespacedId> entityIds,
            Object2IntMap<NamespacedId> itemIds,
            float ambientOcclusionLevel,
            boolean disableDirectionalShading,
            boolean useSeparateAo,
            boolean writeStainedGlassDepth) {
        this.blockMetaIds = Collections.unmodifiableMap(blockMetaIds);
        this.defaultBlockIds = defaultBlockIds;
        this.blockPassOverrides = Collections.unmodifiableMap(blockPassOverrides);
        this.entityIds = entityIds;
        this.itemIds = itemIds;
        this.ambientOcclusionLevel = ambientOcclusionLevel;
        this.disableDirectionalShading = disableDirectionalShading;
        this.useSeparateAo = useSeparateAo;
        this.writeStainedGlassDepth = writeStainedGlassDepth;
    }

    public static BlockRenderingSettings fromIdMap(
            IdMap idMap,
            float ambientOcclusionLevel,
            boolean disableDirectionalShading,
            boolean useSeparateAo,
            boolean writeStainedGlassDepth) {
        Map<NamespacedId, Int2IntMap> metaMap = new Object2ObjectOpenHashMap<>();
        Object2IntOpenHashMap<NamespacedId> defaultIds = new Object2IntOpenHashMap<>();
        defaultIds.defaultReturnValue(UNMAPPED_ID);

        for (var entry : idMap.getBlockProperties().int2ObjectEntrySet()) {
            int shaderId = entry.getIntKey();
            List<BlockEntry> entries = entry.getValue();
            if (entries == null) {
                continue;
            }
            for (BlockEntry be : entries) {
                NamespacedId id = be.getId();
                if (be.getMetas().isEmpty()) {
                    defaultIds.putIfAbsent(id, shaderId);
                } else {
                    Int2IntMap m = metaMap.computeIfAbsent(id, k -> {
                        Int2IntOpenHashMap map = new Int2IntOpenHashMap();
                        map.defaultReturnValue(UNMAPPED_ID);
                        return map;
                    });
                    for (int meta : be.getMetas()) {
                        m.putIfAbsent(meta, shaderId);
                    }
                }
            }
        }

        Map<NamespacedId, TerrainPassKind> passOverrides = new Object2ObjectOpenHashMap<>();
        for (var entry : idMap.getBlockRenderTypeMap().entrySet()) {
            TerrainPassKind kind = mapRenderType(entry.getValue());
            if (kind != null) {
                passOverrides.put(entry.getKey(), kind);
            }
        }

        Object2IntOpenHashMap<NamespacedId> entityMap = new Object2IntOpenHashMap<>();
        entityMap.defaultReturnValue(UNMAPPED_ID);
        idMap.getEntityIdMap().object2IntEntrySet().forEach(e -> entityMap.put(e.getKey(), e.getIntValue()));

        Object2IntOpenHashMap<NamespacedId> itemMap = new Object2IntOpenHashMap<>();
        itemMap.defaultReturnValue(UNMAPPED_ID);
        idMap.getItemIdMap().object2IntEntrySet().forEach(e -> itemMap.put(e.getKey(), e.getIntValue()));

        return new BlockRenderingSettings(
                metaMap, defaultIds, passOverrides, entityMap, itemMap,
                ambientOcclusionLevel, disableDirectionalShading, useSeparateAo, writeStainedGlassDepth);
    }

    @Nullable
    private static TerrainPassKind mapRenderType(@Nullable BlockRenderType renderType) {
        if (renderType == null) {
            return null;
        }
        return switch (renderType) {
            case SOLID -> TerrainPassKind.MAIN_SOLID;
            case CUTOUT, CUTOUT_MIPPED -> TerrainPassKind.MAIN_CUTOUT;
            case TRANSLUCENT -> TerrainPassKind.MAIN_TRANSLUCENT;
        };
    }

    public int resolveBlockId(NamespacedId blockId, int metadata) {
        Int2IntMap meta = blockMetaIds.get(blockId);
        if (meta != null) {
            int found = meta.get(metadata);
            if (found != UNMAPPED_ID) {
                return found;
            }
        }
        return defaultBlockIds.getInt(blockId);
    }

    /** Packs a resolved block ID into the 16-bit {@code mc_Entity} auxiliary column value. */
    public short packMcEntityShort(NamespacedId blockId, int metadata, boolean isFluid) {
        int id = resolveBlockId(blockId, metadata);
        int packed = ((id + 1) << 1) | (isFluid ? 1 : 0);
        return (short) (packed & 0xFFFF);
    }

    @Nullable
    public TerrainPassKind resolvePassOverride(NamespacedId blockId) {
        return blockPassOverrides.get(blockId);
    }

    public int resolveEntityId(NamespacedId entityId) {
        return entityIds.getInt(entityId);
    }

    public int resolveItemId(NamespacedId itemId) {
        return itemIds.getInt(itemId);
    }

    public float ambientOcclusionLevel() { return ambientOcclusionLevel; }
    public boolean disableDirectionalShading() { return disableDirectionalShading; }
    public boolean useSeparateAo() { return useSeparateAo; }
    public boolean writeStainedGlassDepth() { return writeStainedGlassDepth; }
}
