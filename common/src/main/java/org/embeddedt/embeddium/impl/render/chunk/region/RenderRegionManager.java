package org.embeddedt.embeddium.impl.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;

/**
 * Spatial registry of {@link RenderRegion}s. GPU buffer allocation and chunk mesh uploads belong to
 * Celtium's Vulkan {@code TerrainRenderer} rather than Celeritas's OpenGL {@code StagingBuffer} / {@code GlBufferArena}.
 */
public class RenderRegionManager {
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();
    private final BitSet regionIds = new BitSet();
    private int nextFreeId = 0;

    private final int commonVertexStride;

    public RenderRegionManager(RenderPassConfiguration<?> renderPassConfiguration) {
        this.commonVertexStride = renderPassConfiguration.commonVertexStride();
    }

    /** Overload kept for binary compatibility; {@code commandList} is ignored on Celtium's Vulkan-native path. */
    public RenderRegionManager(CommandList ignoredCommandList, RenderPassConfiguration<?> renderPassConfiguration) {
        this(renderPassConfiguration);
    }

    public void update() {
        Iterator<RenderRegion> it = this.regions.values().iterator();
        while (it.hasNext()) {
            RenderRegion region = it.next();
            if (region.isEmpty()) {
                region.delete();
                it.remove();
                this.regionIds.clear(region.getId());
                this.nextFreeId = Math.min(this.nextFreeId, region.getId());
            }
        }
    }

    public void delete() {
        for (RenderRegion region : this.regions.values()) {
            region.delete();
        }
        this.regions.clear();
    }

    /** Overload kept for compatibility; {@code commandList} is unused. */
    public void delete(CommandList ignoredCommandList) {
        delete();
    }

    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }

    public RenderRegion createForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.create(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH);
    }

    private int getNextId() {
        int id = this.nextFreeId;
        this.nextFreeId = this.regionIds.nextClearBit(id + 1);
        this.regionIds.set(id);
        return id;
    }

    @NotNull
    private RenderRegion create(int x, int y, int z) {
        var key = RenderRegion.key(x, y, z);
        var instance = this.regions.get(key);

        if (instance == null) {
            this.regions.put(key, instance = new RenderRegion(x, y, z, this.getNextId(), this.commonVertexStride));
        }

        return instance;
    }

    public int getRegionIdsLength() {
        return this.regionIds.length();
    }
}
