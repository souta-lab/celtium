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
    private final Consumer<Entity> addEntity;

    @SuppressWarnings("unchecked")
    public EntityGatherer() {
        this.entityLists = new List[NUM_PASSES];
        for (int i = 0; i < NUM_PASSES; i++) {
            this.entityLists[i] = new ArrayList<>();
        }
        var entityLists = this.entityLists;
        this.addEntity = entity -> {
            for (int i = 0; i < NUM_PASSES; i++) {
                if (entity.shouldRenderInPass(i)) {
                    entityLists[i].add(entity);
                }
            }
        };
    }

    /**
     * The world tick the lists were gathered for. Entities join and leave the world on ticks, never between them, so
     * the gather - a walk of every loaded chunk's entity maps - is identical on every frame inside one tick and only
     * a tick boundary can change it. At uncapped framerates the same gather was paid once per frame: ~2% of the warm
     * main thread at 145fps, for lists that cannot have changed.
     */
    private long gatheredAtTick = -1;
    /** The world the tick belongs to: RenderGlobal survives a world reload, and a different world can carry the
     * same total time - the tick alone is not identity. */
    private WorldClient gatheredFromWorld;

    public void clear() {
        for (int i = 0; i < NUM_PASSES; i++) {
            entityLists[i].clear();
        }
        this.gatheredAtTick = -1;
        this.gatheredFromWorld = null;
    }

    public List<Entity>[] getLoadedEntityList(WorldClient world) {
        long tick = world.getTotalWorldTime();
        if (tick == this.gatheredAtTick && world == this.gatheredFromWorld) {
            return this.entityLists;
        }
        this.gatheredFromWorld = world;
        this.gatheredAtTick = tick;
        for (int i = 0; i < NUM_PASSES; i++) {
            entityLists[i].clear();
        }
        Consumer<Entity> addEntity = this.addEntity;
        // Iterate directly over chunk entity lists where possible - mods may create multipart entities that are not
        // added to the main loadedEntityList.
        if (world.getChunkProvider() instanceof ChunkProviderClientAccessor provider) {
            var loadedChunks = provider.celeritas$getLoadedChunks();
            for (Chunk chunk : loadedChunks.values()) {
                if (!((ChunkAccessor)chunk).celeritas$getHasEntities()) {
                    continue;
                }
                ClassInheritanceMultiMap<Entity>[] entityMaps = chunk.getEntityLists();
                for (ClassInheritanceMultiMap<Entity> map : entityMaps) {
                    map.forEach(addEntity);
                }
            }
        } else {
            // Best we can do is the loaded entity list - this will miss some multipart entities
            world.loadedEntityList.forEach(addEntity);
        }
        return this.entityLists;
    }
}
