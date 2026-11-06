package org.taumc.celeritas.impl;

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;

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
 * Client world only: {@code Entity.getBrightness} also runs on the integrated server's thread (mob AI), and these
 * maps are unsynchronised statics - sharing them let the server thread corrupt the client's map (measured: an
 * {@code ArrayIndexOutOfBoundsException} inside the map on the server thread) and could hand one world's light to the
 * other. A server-world lookup takes the vanilla call unchanged.
 * <p>
 * Kill switch: {@code -Dceltium.entityLightCache=false} disables the whole cache (entity and
 * particle callers degrade to the vanilla call sites unchanged).
 */
public final class CeltiumLightCache {

    private CeltiumLightCache() {
    }

    private static final boolean ENABLED =
            !"false".equals(System.getProperty("celtium.entityLightCache", "true"));
    /**
     * Entries per map. The cache is cleared on every world-time or light-map change, so this is the working set of
     * one tick's lookups, not a long-lived cache. It used to be 1,024, which a chunk rebuild exceeds (a 16^3 section
     * holds 4,096 positions): evictions sent the lookups back to World#getCombinedLight, whose block lookups are the
     * expensive part. Measured on ATM3 as 3.7% of client-thread CPU inclusive before the change.
     */
    private static final int CAP = 8192;

    private static long tick = Long.MIN_VALUE;
    /**
     * The second half of the epoch (audit B19): world time alone is not the whole invalidation
     * condition - a light write that lands mid-tick must be visible to the very next lookup, or the
     * cache would serve a stale value vanilla would not have produced. Every write to the light
     * map funnels through {@code World.setLightFor}; the mixin on that method bumps this counter,
     * so the cache flushes exactly when the light map the lookups read changes.
     */
    private static volatile long lightEpoch;

    /** Bumps the light epoch; called by the mixin on {@code World.setLightFor}. */
    public static void noteLightWrite() {
        lightEpoch++;
    }

    /**
     * LRU maps on primitive keys (fastutil 7 API only - that is the runtime's). They replace access-ordered
     * {@code LinkedHashMap<Long, ...>}s: {@code Long.hashCode} of a packed position collides enough to treeify bins
     * ({@code HashMap$TreeNode.getTreeNode} was 3.8% of the client thread with these among its callers), and every
     * lookup boxed its key.
     * <p>
     * <b>Invalidation is a generation tag, not a clear.</b> The maps used to be emptied on every world-time or
     * light-epoch change, and that is O(capacity): a {@code Long2IntLinkedOpenHashMap} of 16 384 slots clears four
     * arrays (key, value, link, order) - about 320 KB per map, 640 KB for the pair - and the day-cycle skylight sweep
     * writes light on every tick, so a <em>tick frame</em> paid it while a plain frame did not. Measured on the
     * paired KPI's scene (leg {@code r12tick2-amd}, {@code CELTIUM_FRAME_TICK_CONSTANTS}): {@code lightCache=73/1}
     * us, tick frames against plain. The value now carries the generation in its high 32 bits and a lookup compares
     * it, so an invalidation is one increment and a stale entry is a miss that is overwritten in place. The maps keep
     * their LRU bound, so the working set is unchanged.
     */
    private static final long ABSENT = 0L;
    private static final Long2LongLinkedOpenHashMap COMBINED = new Long2LongLinkedOpenHashMap(CAP * 2);
    private static final Long2LongLinkedOpenHashMap BRIGHTNESS = new Long2LongLinkedOpenHashMap(CAP * 2);

    static {
        COMBINED.defaultReturnValue(ABSENT);
        BRIGHTNESS.defaultReturnValue(ABSENT);
    }

    /** The tag every live entry carries; 0 is never valid, so a fresh map cannot serve a stale value. */
    private static int generation = 1;

    /** {@return the value tagged with the current generation, or {@link #ABSENT}}. */
    private static long tagged(int value) {
        return ((long) generation << 32) | (value & 0xFFFFFFFFL);
    }

    /** {@return the value a tagged entry carries, or {@link #ABSENT} when the entry is from an older generation}. */
    private static long untag(long entry) {
        return (int) (entry >>> 32) == generation ? entry : ABSENT;
    }

    private static void putCombined(long key, int value) {
        COMBINED.putAndMoveToLast(key, tagged(value));
        if (COMBINED.size() > CAP) {
            COMBINED.removeFirstLong();
        }
    }

    private static void putBrightness(long key, float value) {
        BRIGHTNESS.putAndMoveToLast(key, tagged(Float.floatToRawIntBits(value)));
        if (BRIGHTNESS.size() > CAP) {
            BRIGHTNESS.removeFirstLong();
        }
    }

    /**
     * {@code World#getCombinedLight(pos, 0)} with no loaded check - the TESR dispatcher's own call, which runs once per
     * block entity per frame.
     */
    public static int combinedLight(World world, BlockPos pos) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("lightCache");
        try {
            if (!ENABLED || !world.isRemote) {
                return world.getCombinedLight(pos, 0);
            }
            rollTick(world);
            long key = pack(pos.getX(), pos.getY(), pos.getZ());
            long cached = untag(COMBINED.getAndMoveToLast(key));
            if (cached != ABSENT) {
                return (int) cached;
            }
            int value = world.getCombinedLight(pos, 0);
            putCombined(key, value);
            return value;
        } finally {
            org.soutalab.celtium.vulkan.FramePassTimings.end("lightCache");
        }
    }

    /**
     * {@code World#getCombinedLight(pos, 0)} with the isBlockLoaded guard the callers apply - the
     * particle contract: the check runs at the position itself.
     */
    public static int combinedLightAtPos(World world, double posX, double posY, double posZ) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("lightCache");
        try {
            if (!ENABLED || world == null || !world.isRemote) {
                BlockPos pos = new BlockPos(posX, posY, posZ);
                return world != null && world.isBlockLoaded(pos) ? world.getCombinedLight(pos, 0) : 0;
            }
            rollTick(world);
            long x = MathHelper.floor(posX), y = MathHelper.floor(posY), z = MathHelper.floor(posZ);
            long key = pack(x, y, z);
            long cached = untag(COMBINED.getAndMoveToLast(key));
            if (cached != ABSENT) {
                return (int) cached;
            }
            BlockPos pos = new BlockPos(x, y, z);
            int value = world.isBlockLoaded(pos) ? world.getCombinedLight(pos, 0) : 0;
            putCombined(key, value);
            return value;
        } finally {
            org.soutalab.celtium.vulkan.FramePassTimings.end("lightCache");
        }
    }

    /**
     * {@code Entity#getBrightnessForRender}: the loaded-check runs at (x, 0, z) - the column the
     * entity stands in - and the light value is read at eye height.
     */
    public static int entityCombinedLight(World world, double posX, double posYEye, double posZ) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("lightCache");
        try {
            if (!ENABLED || world == null || !world.isRemote) {
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
            long cached = untag(COMBINED.getAndMoveToLast(key));
            if (cached != ABSENT) {
                return (int) cached;
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) x, 0, (int) z);
            int value;
            if (!world.isBlockLoaded(pos)) {
                value = 0;
            } else {
                pos.setY((int) y);
                value = world.getCombinedLight(pos, 0);
            }
            putCombined(key, value);
            return value;
        } finally {
            org.soutalab.celtium.vulkan.FramePassTimings.end("lightCache");
        }
    }

    /** {@code Entity#getBrightness}: same column check, {@code getLightBrightness} at eye height. */
    public static float entityBrightness(World world, double posX, double posYEye, double posZ) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("lightCache");
        try {
            if (!ENABLED || world == null || !world.isRemote) {
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
            long cached = untag(BRIGHTNESS.getAndMoveToLast(key));
            if (cached != ABSENT) {
                return Float.intBitsToFloat((int) cached);
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) x, 0, (int) z);
            float value;
            if (!world.isBlockLoaded(pos)) {
                value = 0.0F;
            } else {
                pos.setY((int) y);
                value = world.getLightBrightness(pos);
            }
            putBrightness(key, value);
            return value;
        } finally {
            org.soutalab.celtium.vulkan.FramePassTimings.end("lightCache");
        }
    }

    private static long lightEpochSeen = Long.MIN_VALUE;

    private static void rollTick(World world) {
        long now = world.getTotalWorldTime();
        long epoch = lightEpoch;
        if (now != tick || epoch != lightEpochSeen) {
            tick = now;
            lightEpochSeen = epoch;
            // One increment instead of two map clears: the entries stay and are read as misses by their tag. The
            // wrap is the only case that needs the old behaviour, and it is 4 billion invalidations away.
            if (++generation == 0) {
                generation = 1;
                COMBINED.clear();
                BRIGHTNESS.clear();
            }
        }
    }

    private static long pack(long x, long y, long z) {
        return (x & 0x3ffffffL) << 37 | (y & 0xfffL) << 25 | (z & 0x3ffffffL);
    }
}
