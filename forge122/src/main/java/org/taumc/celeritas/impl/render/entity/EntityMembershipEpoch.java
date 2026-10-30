package org.taumc.celeritas.impl.render.entity;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.World;

/**
 * A per-world counter that changes whenever the loaded entity set may have changed.
 * <p>
 * {@code EntityGatherer} asks for it once per render pass to decide whether the membership it gathered this tick is
 * still valid. Both methods used to be {@code synchronized} on the class, and {@link #current} also boxed its default
 * value through {@code getOrDefault}: profiled on a Susy world leg, {@code current} was 3.0% of client-thread CPU -
 * the render thread spinning on a monitor another thread (the entity tracker calling {@link #changed}) holds, for a
 * read of one long that almost never changes.
 * <p>
 * The common case is now a volatile read compare: the world the last ask was about, and its counter. A miss falls back
 * to the map under the lock, and {@link #changed} updates the fast pair when it is the same world.
 */
public final class EntityMembershipEpoch {
    private static final Map<World, Long> epochs = new WeakHashMap<>();
    /** The world most recently asked about, and the value the fast path returns; volatile so no lock is needed. */
    private static volatile World lastWorld;
    private static volatile long lastEpoch;

    private EntityMembershipEpoch() {
    }

    public static long current(World world) {
        if (world == lastWorld) {
            return lastEpoch;
        }
        return currentSlow(world);
    }

    private static synchronized long currentSlow(World world) {
        Long known = epochs.get(world);
        long epoch = known == null ? 0L : known.longValue();
        lastEpoch = epoch;
        lastWorld = world;
        return epoch;
    }

    public static synchronized void changed(World world) {
        if (world != null && world.isRemote) {
            Long known = epochs.get(world);
            long epoch = (known == null ? 0L : known.longValue()) + 1L;
            epochs.put(world, Long.valueOf(epoch));
            if (world == lastWorld) {
                lastEpoch = epoch;
            }
        }
    }
}
