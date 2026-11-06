package org.taumc.celeritas.impl.render.entity;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loaded chunks that currently hold at least one entity, per world.
 * <p>
 * The gather walk used to visit every loaded chunk and ask whether it had entities. That question is cheap per chunk
 * but it is asked 1 281 times a tick, and the answer is "no" for all but a handful: measured on {@code r8kpi-before}
 * (Supersymmetry, render distance 18, one loaded entity) the walk cost 112 us of a 1 920 us tick frame, and the
 * per-chunk membership cache added in the same round did not move it - the cost is visiting the chunk objects, not
 * iterating their sixteen section maps. This index is the answer to the same question without the visit: the four
 * original {@code Chunk} methods that add, remove or unload entities keep it current, so the walk is proportional to
 * the chunks that <em>have</em> entities.
 * <p>
 * The index is a {@link ConcurrentHashMap} key set because the entity tracker mutates it from its own thread while the
 * render thread iterates it; a weakly consistent iterator is exactly the contract wanted here (a chunk added during a
 * gather is picked up by the next one). Iteration order is not stable across mutations, which the previous walk did not
 * promise either - it followed the loaded-chunk map's order, which changes as chunks load.
 * <p>
 * {@code -Dceltium.vulkan.entityCacheAudit=true} compares the index against a full walk of the loaded chunks every
 * fiftieth gather, which is the positive control for "the index is complete".
 */
public final class EntityBearingChunks {
    private static final Map<World, Set<Chunk>> INDEX = new WeakHashMap<>();
    /** The world most recently asked about and its set, published as one object so a read cannot tear. */
    private static volatile State last;

    private EntityBearingChunks() {
    }

    /** {@return the chunks of {@code world} that currently hold at least one entity}; never null. */
    public static Set<Chunk> of(World world) {
        State state = last;
        if (state != null && state.world == world) {
            return state.chunks;
        }
        return ofSlow(world);
    }

    private static synchronized Set<Chunk> ofSlow(World world) {
        Set<Chunk> chunks = INDEX.get(world);
        if (chunks == null) {
            chunks = ConcurrentHashMap.newKeySet();
            INDEX.put(world, chunks);
        }
        last = new State(world, chunks);
        return chunks;
    }

    /** Records whether {@code chunk} holds entities now; called from the original {@code Chunk} methods. */
    public static void update(World world, Chunk chunk, boolean hasEntities) {
        if (world == null || !world.isRemote) {
            return;
        }
        Set<Chunk> chunks = of(world);
        if (hasEntities) {
            chunks.add(chunk);
        } else {
            chunks.remove(chunk);
        }
    }

    private static final class State {
        final World world;
        final Set<Chunk> chunks;

        State(World world, Set<Chunk> chunks) {
            this.world = world;
            this.chunks = chunks;
        }
    }
}
