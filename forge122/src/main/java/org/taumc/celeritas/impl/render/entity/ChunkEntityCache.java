package org.taumc.celeritas.impl.render.entity;

import net.minecraft.entity.Entity;

/**
 * One chunk's gathered entity membership, held by the chunk itself and rebuilt only when that chunk's membership
 * changed.
 * <p>
 * The gatherer used to walk every loaded chunk on every tick and, for each chunk the sticky {@code hasEntities} flag
 * admitted, iterate all sixteen {@code ClassInheritanceMultiMap}s. {@code hasEntities} is set by
 * {@code Chunk.addEntity} and never cleared by the original code, so after a few minutes of play it admits nearly every
 * loaded chunk - and a chunk with one item entity still paid sixteen iterator constructions. Measured on the
 * {@code r7ent-amd} leg (render distance 18, 1 369 loaded chunks): {@code entGatherWalk} 146 us at the p99 rank, 100 %
 * tick-gated, which was 60 % of the settled tick frame's entity excess.
 * <p>
 * The cache is exact with respect to the same boundary the epoch already relies on: the four original {@code Chunk}
 * methods that add, remove or unload entities. {@code -Dceltium.vulkan.entityCacheAudit=true} re-counts each chunk's
 * sections on every gather and reports a mismatch, which is the positive control for that assumption - a mod that
 * mutates a chunk's entity maps directly would show up there instead of as an invisible entity.
 * <p>
 * Render thread only; the invalidation flag is volatile because the entity tracker invalidates from its own thread.
 */
public interface ChunkEntityCache {
    /**
     * {@return this chunk's entities}, rebuilt from its section maps when the membership changed since the last call.
     * The returned array is owned by the chunk and is valid for {@link #celtium$gatheredCount()} entries; its tail is
     * null so a removed entity stays collectable.
     */
    Entity[] celtium$gatheredEntities();

    /** {@return the number of valid entries in {@link #celtium$gatheredEntities()}}. */
    int celtium$gatheredCount();
}
