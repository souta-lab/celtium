package org.taumc.celeritas.impl.render.entity;

import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

/** Membership generation for the chunk entity collections the renderer gathers, without retaining unloaded worlds. */
public final class EntityMembershipEpoch {
    private static final Map<World, Long> epochs = new WeakHashMap<>();

    private EntityMembershipEpoch() {
    }

    public static synchronized long current(World world) {
        return epochs.getOrDefault(world, 0L);
    }

    public static synchronized void changed(World world) {
        if (world != null && world.isRemote) {
            epochs.put(world, epochs.getOrDefault(world, 0L) + 1L);
        }
    }
}
