package org.taumc.celeritas.impl.render.entity;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.chunk.Chunk;
import org.taumc.celeritas.mixin.core.terrain.ChunkAccessor;
import org.taumc.celeritas.mixin.core.terrain.ChunkProviderClientAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EntityGatherer {
    public static final int NUM_PASSES = 2;

    private final List<Entity>[] entityLists;
    private final List<Entity> gatheredEntities = new ArrayList<>();
    private final Consumer<Entity> gatherEntity = this.gatheredEntities::add;
    private long gatheredAtTick = -1;
    private long gatheredAtEpoch = -1;
    private WorldClient gatheredFromWorld;

    @SuppressWarnings("unchecked")
    public EntityGatherer() {
        this.entityLists = new List[NUM_PASSES];
        for (int i = 0; i < NUM_PASSES; i++) {
            this.entityLists[i] = new ArrayList<>();
        }
    }

    public void clear() {
        for (List<Entity> entities : this.entityLists) entities.clear();
        this.gatheredEntities.clear();
        this.gatheredAtTick = -1;
        this.gatheredAtEpoch = -1;
        this.gatheredFromWorld = null;
    }

    /** Preserve the existing all-pass entry point for callers outside the render loop. */
    public List<Entity>[] getLoadedEntityList(WorldClient world) {
        gatherMembership(world);
        for (int pass = 0; pass < NUM_PASSES; pass++) collectPass(pass);
        return this.entityLists;
    }

    /** Evaluate eligibility at the actual Forge pass, even when membership was already gathered this tick. */
    public List<Entity>[] getLoadedEntityList(WorldClient world, int pass) {
        if (pass < 0 || pass >= NUM_PASSES) {
            throw new IllegalArgumentException("Unsupported entity render pass " + pass);
        }
        gatherMembership(world);
        collectPass(pass);
        return this.entityLists;
    }

    private void collectPass(int pass) {
        List<Entity> entities = this.entityLists[pass];
        entities.clear();
        for (Entity entity : this.gatheredEntities) {
            if (entity.shouldRenderInPass(pass)) entities.add(entity);
        }
    }

    private void gatherMembership(WorldClient world) {
        long tick = world.getTotalWorldTime();
        long epoch = EntityMembershipEpoch.current(world);
        Object provider = world.getChunkProvider();
        boolean chunkBacked = provider instanceof ChunkProviderClientAccessor;
        if (chunkBacked && tick == this.gatheredAtTick && epoch == this.gatheredAtEpoch
                && world == this.gatheredFromWorld) {
            return;
        }
        this.gatheredEntities.clear();
        if (provider instanceof ChunkProviderClientAccessor chunks) {
            // Chunk collections retain multipart entities that are absent from loadedEntityList.
            for (Chunk chunk : chunks.celeritas$getLoadedChunks().values()) {
                if (!((ChunkAccessor) chunk).celeritas$getHasEntities()) continue;
                for (ClassInheritanceMultiMap<Entity> map : chunk.getEntityLists()) map.forEach(this.gatherEntity);
            }
        } else {
            // A custom provider's loadedEntityList may change without any Chunk mutation hook.
            // Re-read this fallback on every ask rather than claim a membership epoch we cannot observe.
            world.loadedEntityList.forEach(this.gatherEntity);
        }
        // Publish only after the complete gather, so a failed producer is retried on the next ask.
        this.gatheredFromWorld = world;
        this.gatheredAtTick = tick;
        this.gatheredAtEpoch = epoch;
    }
}
