package org.taumc.celeritas.mixin.core.terrain;

import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.entity.ChunkEntityCache;
import org.taumc.celeritas.impl.render.entity.EntityBearingChunks;
import org.taumc.celeritas.impl.render.entity.EntityGatherCensus;
import org.taumc.celeritas.impl.render.entity.EntityMembershipEpoch;

import java.util.Arrays;

/**
 * These original Chunk methods own the entity-map mutation/load boundaries the gather cache observes, and this mixin
 * also holds the per-chunk membership cache the gatherer reads.
 * <p>
 * Two things are maintained here, both keyed by the same four methods:
 * <ul>
 *   <li>the per-chunk gathered array ({@link ChunkEntityCache}), rebuilt only when this chunk's membership changed, so
 *       a chunk that did not change costs one array copy instead of sixteen section iterators;</li>
 *   <li>the world's index of chunks that currently hold entities ({@link EntityBearingChunks}), so the gather walk is
 *       proportional to those chunks instead of to the 1 281 loaded ones - measured on {@code r8kpi-before}, visiting
 *       every loaded chunk cost 112 us of a 1 920 us tick frame with one entity in the world.</li>
 * </ul>
 * The count that decides index membership is recomputed from the section maps rather than tracked with a counter:
 * {@code removeEntity} delegates to {@code removeEntityAtIndex}, so a counter would be decremented twice, and a
 * recomputation cannot drift. Sixteen {@code size()} calls per mutation is nothing next to the walk it removes.
 * <p>
 * The cache is exact with respect to the same boundary the epoch already relies on. A mod that mutates a chunk's entity
 * maps directly would show up in {@code -Dceltium.vulkan.entityCacheAudit=true} instead of as an invisible entity.
 */
@Mixin(Chunk.class)
public abstract class ChunkEntityMembershipMixin implements ChunkEntityCache {
    @Shadow @Final private World world;

    /** Cleared by every original method that can change this chunk's entity membership. */
    @Unique private volatile boolean celtium$cacheValid;
    /** The chunk's entities, valid for {@code [0, celtium$cachedCount)}; the tail is null. */
    @Unique private Entity[] celtium$cachedEntities;
    @Unique private int celtium$cachedCount;

    @Inject(method = {"func_76612_a", "func_76608_a", "func_76631_c"}, at = @At("RETURN"))
    private void celtium$entityMembershipChanged(CallbackInfo info) {
        this.celtium$cacheValid = false;
        int count = 0;
        for (ClassInheritanceMultiMap<Entity> map : ((Chunk) (Object) this).getEntityLists()) {
            count += map.size();
        }
        EntityBearingChunks.update(this.world, (Chunk) (Object) this, count > 0);
        EntityMembershipEpoch.changed(this.world);
    }

    /**
     * {@code onUnload} does not clear the section maps - {@code World.unloadEntities} only removes the entities from
     * the world's own lists - so the count would still be positive here. The chunk is leaving the world, so it leaves
     * the index unconditionally.
     */
    @Inject(method = "func_76623_d", at = @At("RETURN"))
    private void celtium$entityMembershipUnloaded(CallbackInfo info) {
        this.celtium$cacheValid = false;
        EntityBearingChunks.update(this.world, (Chunk) (Object) this, false);
        EntityMembershipEpoch.changed(this.world);
    }

    @Override
    public Entity[] celtium$gatheredEntities() {
        if (this.celtium$cacheValid) {
            return this.celtium$cachedEntities;
        }
        Entity[] out = this.celtium$cachedEntities;
        if (out == null) {
            out = new Entity[4];
        }
        int count = 0;
        for (ClassInheritanceMultiMap<Entity> map : ((Chunk) (Object) this).getEntityLists()) {
            if (map.isEmpty()) {
                continue;
            }
            for (Entity entity : map) {
                if (count == out.length) {
                    out = Arrays.copyOf(out, out.length << 1);
                }
                out[count++] = entity;
            }
        }
        if (count < this.celtium$cachedCount) {
            Arrays.fill(out, count, this.celtium$cachedCount, null);
        }
        this.celtium$cachedEntities = out;
        this.celtium$cachedCount = count;
        this.celtium$cacheValid = true;
        EntityGatherCensus.rebuilt();
        return out;
    }

    @Override
    public int celtium$gatheredCount() {
        return this.celtium$cachedCount;
    }
}
