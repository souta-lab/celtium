package org.taumc.celeritas.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Per-world-tick cache for the light lookups the render side repeats every frame.
 * <p>
 * Two families pay the same lookup per frame: {@code Particle.getBrightnessForRender} runs
 * {@code World#getCombinedLight} per particle per frame, and {@code Entity.getBrightnessForRender}/
 * {@code getBrightness} run {@code getCombinedLight}/{@code getLightBrightness} per entity per frame.
 * World light only changes on the tick, and dozens-to-hundreds of callers share the same few blocks,
 * so each result is cached keyed by the packed block position and the whole cache is dropped on the
 * tick boundary - identical semantics to calling it fresh, minus the lookups between ticks.
 * <p>
 * The {@code BlockPos} the original allocates is only ever a key on the hit path, so the key packs
 * the same coordinates by hand ({@link BlockPos#toLong} layout: 25 bits z, 12 bits y, 25 bits x) and
 * the object is materialised only on a miss, where the world call needs it anyway. One map per
 * lookup kind; {@code isBlockLoaded} semantics are preserved inside each entry point so an unloaded
 * column still answers 0 exactly as the original would.
 * <p>
 * Kill switch: {@code -Dceltium.entityLightCache=false} disables the whole cache (entity and
 * particle callers degrade to the vanilla call sites unchanged).
 */
public final class CeltiumLightCache {

    private CeltiumLightCache() {
    }

    private static final boolean ENABLED =
            !"false".equals(System.getProperty("celtium.entityLightCache", "true"));
    private static final int CAP = 1024;

    private static long tick = Long.MIN_VALUE;

    private static final LinkedHashMap<Long, Integer> COMBINED =
            new LinkedHashMap<Long, Integer>(CAP, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
                    return size() > CAP;
                }
            };
    private static final LinkedHashMap<Long, Float> BRIGHTNESS =
            new LinkedHashMap<Long, Float>(CAP, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Float> eldest) {
                    return size() > CAP;
                }
            };

    /**
     * {@code World#getCombinedLight(pos, 0)} with the isBlockLoaded guard the callers apply - the
     * particle contract: the check runs at the position itself.
     */
    public static int combinedLightAtPos(World world, double posX, double posY, double posZ) {
        if (!ENABLED || world == null) {
            BlockPos pos = new BlockPos(posX, posY, posZ);
            return world != null && world.isBlockLoaded(pos) ? world.getCombinedLight(pos, 0) : 0;
        }
        rollTick(world);
        long x = MathHelper.floor(posX), y = MathHelper.floor(posY), z = MathHelper.floor(posZ);
        long key = pack(x, y, z);
        Integer cached = COMBINED.get(Long.valueOf(key));
        if (cached != null) {
            return cached.intValue();
        }
        BlockPos pos = new BlockPos(x, y, z);
        int value = world.isBlockLoaded(pos) ? world.getCombinedLight(pos, 0) : 0;
        COMBINED.put(Long.valueOf(key), Integer.valueOf(value));
        return value;
    }

    /**
     * {@code Entity#getBrightnessForRender}: the loaded-check runs at (x, 0, z) - the column the
     * entity stands in - and the light value is read at eye height.
     */
    public static int entityCombinedLight(World world, double posX, double posYEye, double posZ) {
        if (!ENABLED || world == null) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                    MathHelper.floor(posX), 0, MathHelper.floor(posZ));
            if (world == null || !world.isBlockLoaded(pos)) {
                return 0;
            }
            pos.setY(MathHelper.floor(posYEye));
            return world.getCombinedLight(pos, 0);
        }
        rollTick(world);
        long x = MathHelper.floor(posX), y = MathHelper.floor(posYEye), z = MathHelper.floor(posZ);
        long key = pack(x, y, z);
        Integer cached = COMBINED.get(Long.valueOf(key));
        if (cached != null) {
            return cached.intValue();
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) x, 0, (int) z);
        int value;
        if (!world.isBlockLoaded(pos)) {
            value = 0;
        } else {
            pos.setY((int) y);
            value = world.getCombinedLight(pos, 0);
        }
        COMBINED.put(Long.valueOf(key), Integer.valueOf(value));
        return value;
    }

    /** {@code Entity#getBrightness}: same column check, {@code getLightBrightness} at eye height. */
    public static float entityBrightness(World world, double posX, double posYEye, double posZ) {
        if (!ENABLED || world == null) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                    MathHelper.floor(posX), 0, MathHelper.floor(posZ));
            if (world == null || !world.isBlockLoaded(pos)) {
                return 0.0F;
            }
            pos.setY(MathHelper.floor(posYEye));
            return world.getLightBrightness(pos);
        }
        rollTick(world);
        long x = MathHelper.floor(posX), y = MathHelper.floor(posYEye), z = MathHelper.floor(posZ);
        long key = pack(x, y, z);
        Float cached = BRIGHTNESS.get(Long.valueOf(key));
        if (cached != null) {
            return cached.floatValue();
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) x, 0, (int) z);
        float value;
        if (!world.isBlockLoaded(pos)) {
            value = 0.0F;
        } else {
            pos.setY((int) y);
            value = world.getLightBrightness(pos);
        }
        BRIGHTNESS.put(Long.valueOf(key), Float.valueOf(value));
        return value;
    }

    private static void rollTick(World world) {
        long now = world.getTotalWorldTime();
        if (now != tick) {
            tick = now;
            COMBINED.clear();
            BRIGHTNESS.clear();
        }
    }

    private static long pack(long x, long y, long z) {
        return (x & 0x3ffffffL) << 37 | (y & 0xfffL) << 25 | (z & 0x3ffffffL);
    }
}
