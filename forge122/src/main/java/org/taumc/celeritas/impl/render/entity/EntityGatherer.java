package org.taumc.celeritas.impl.render.entity;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.chunk.Chunk;
import org.taumc.celeritas.mixin.core.terrain.ChunkAccessor;
import org.taumc.celeritas.mixin.core.terrain.ChunkProviderClientAccessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The entity membership of the loaded chunks, gathered once per tick and filtered per render pass.
 * <p>
 * The reference implementation ({@code 01-celeritas-forge-mc12.2-2.5.0-dev}, {@code EntityGatherer}) walks every
 * loaded chunk on <em>every</em> call and fills both pass lists in the same walk, and {@code renderEntities} calls it
 * twice a frame - so the reference pays two chunk walks and four pass-predicate passes per frame. This one walks the
 * chunks once per tick (the membership cannot change without a chunk mutation, which
 * {@link EntityMembershipEpoch} counts) and re-filters the gathered membership per pass, which is what keeps the
 * predicate evaluated with Forge's actual pass active.
 * <p>
 * The membership is held in a plain array with a count rather than an {@code ArrayList} fed through a
 * {@code Consumer}: the walk is the tick-gated half of the {@code entities} bracket (54-56 % of the p99 frame's
 * excess), and per entity the list cost three things the array does not - a {@code Consumer} interface dispatch, an
 * {@code ArrayList.add} with its bounds and growth checks, and a null store for every element on the next
 * {@code clear()}. The array is grown by doubling and its stale tail is nulled only by the amount the membership
 * shrank, so an unloaded entity is still collectable and a steady-state tick pays no clearing at all.
 * <p>
 * Render thread only.
 */
public class EntityGatherer {
    public static final int NUM_PASSES = 2;

    private final List<Entity>[] entityLists;
    /** The gathered membership, valid for {@code [0, gatheredCount)}; the tail is null. */
    private Entity[] gathered = new Entity[256];
    private int gatheredCount;
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
        Arrays.fill(this.gathered, 0, this.gatheredCount, null);
        this.gatheredCount = 0;
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
        Entity[] membership = this.gathered;
        int count = this.gatheredCount;
        for (int i = 0; i < count; i++) {
            Entity entity = membership[i];
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
        int previous = this.gatheredCount;
        this.gatheredCount = 0;
        org.soutalab.celtium.vulkan.FramePassTimings.begin("entGatherWalk");
        if (provider instanceof ChunkProviderClientAccessor chunks) {
            // Chunk collections retain multipart entities that are absent from loadedEntityList.
            for (Chunk chunk : chunks.celeritas$getLoadedChunks().values()) {
                if (!((ChunkAccessor) chunk).celeritas$getHasEntities()) continue;
                for (ClassInheritanceMultiMap<Entity> map : chunk.getEntityLists()) map.forEach(this::addGathered);
            }
        } else {
            // A custom provider's loadedEntityList may change without any Chunk mutation hook.
            // Re-read this fallback on every ask rather than claim a membership epoch we cannot observe.
            world.loadedEntityList.forEach(this::addGathered);
        }
        org.soutalab.celtium.vulkan.FramePassTimings.end("entGatherWalk");
        // Publish only after the complete gather, so a failed producer is retried on the next ask.
        this.gatheredFromWorld = world;
        this.gatheredAtTick = tick;
        this.gatheredAtEpoch = epoch;
        // Release the entities the membership no longer holds, and only those: a steady-state tick shrinks by zero.
        if (this.gatheredCount < previous) {
            Arrays.fill(this.gathered, this.gatheredCount, previous, null);
        }
    }

    private void addGathered(Entity entity) {
        Entity[] membership = this.gathered;
        int count = this.gatheredCount;
        if (count == membership.length) {
            membership = Arrays.copyOf(membership, count << 1);
            this.gathered = membership;
        }
        membership[count] = entity;
        this.gatheredCount = count + 1;
    }
}
