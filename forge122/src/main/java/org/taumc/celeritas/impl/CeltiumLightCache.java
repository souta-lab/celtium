package org.taumc.celeritas.impl;

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;
import org.taumc.celeritas.impl.render.terrain.light.MinecraftDynamicLightService;

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
 * Since item 378 the {@code getCombinedLight} half is served through one funnel inside
 * {@code World#getCombinedLight} itself ({@link #combinedLightFunnel}); there are no caller-side
 * mixins left on the entity, particle or block-entity paths. The vanilla methods run as written -
 * their own loaded checks, their own call into {@code getCombinedLight} - and the cache answers
 * inside that call, which is also the shape addons injecting on those vanilla methods or on the
 * call expect (see the addon connection surface ledger in {@code docs/mod-compatibility.md}).
 * {@code Entity#getBrightness} still enters through its mixin: {@code getLightBrightness} does not
 * pass through {@code getCombinedLight}, so it keeps its own entry below.
 * <p>
 * The {@code BlockPos} the original allocates is only ever a key on the hit path, so the key packs
 * the same coordinates by hand ({@link BlockPos#toLong} layout: 26 bits x at shift 38, 12 bits y
 * at shift 26, 26 bits z at shift 0 - the fields tile the long exactly, so no two coordinates
 * share a key) and
 * the object is materialised only on a miss, where the world call needs it anyway. One map per
 * lookup kind; {@code isBlockLoaded} semantics are preserved inside each entry point so an unloaded
 * column still answers 0 exactly as the original would.
 * <p>
 * Client world only: {@code Entity.getBrightness} also runs on the integrated server's thread (mob AI), and these
 * maps are unsynchronised statics - sharing them let the server thread corrupt the client's map (measured: an
 * {@code ArrayIndexOutOfBoundsException} inside the map on the server thread) and could hand one world's light to the
 * other. A server-world lookup takes the vanilla call unchanged.
 * <p>
 * Kill switch: {@code -Dceltium.entityLightCache=false} disables the cache inside the funnel
 * (and the brightness entry); every lookup then takes the vanilla body unchanged. Before item 378
 * the same property was the escape that restored the vanilla entity/particle methods by standing
 * their HEAD cancels down; with the cancels gone it governs the funnel only.
 * <p>
 * The dynamic-light mirror (item 430, the item 326 follow-up) rides on the funnel's answers: the
 * maps keep holding the vanilla answer only, and the block component of the value handed back is
 * raised at answer time by the current frame's gathered sources - the same {@code level -
 * distance} maximum the terrain shader applies, measured at the queried block's centre. Raising
 * at answer time (never in the stored entry) is what keeps a per-frame quantity out of a
 * per-tick cache. The raise is max-only and lives inside the funnel gate, so a call that carries
 * a {@code lightValue} floor - the shape an addon like Celeritas Dynamic Lights feeds its own
 * contribution through - is answered by the vanilla body and never raised twice; an addon that
 * max-raises the returned value composes as a maximum of maxima, as on the terrain side.
 * {@code -Dceltium.dynamicLights=false} empties the gather and with it the mirror.
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
    /**
     * Reusable mutable position for client-thread cache misses. Guarded by {@link #isClientMainThread()} so any
     * off-thread caller on a client world falls back to a fresh position and leaves these maps untouched.
     */
    private static final BlockPos.MutableBlockPos MISS_POS = new BlockPos.MutableBlockPos();

    /**
     * Scratch for the dynamic-light mirror's per-call snapshot copy (world-space xyz + level per
     * source). Only ever touched inside the funnel, which is gated to the client main thread.
     */
    private static final float[] MIRROR_SOURCES = new float[DynamicLightService.MAX_LIGHTS * 4];

    static {
        COMBINED.defaultReturnValue(ABSENT);
        BRIGHTNESS.defaultReturnValue(ABSENT);
    }

    /** The tag every live entry carries; 0 is never valid, so a fresh map cannot serve a stale value. */
    private static int generation = 1;

    private static boolean isClientMainThread() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.isCallingFromMinecraftThread();
    }

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
     * The funnel (item 378): the one entry the combined-light cache is served through, inside
     * {@code World#getCombinedLight(BlockPos, int)} itself. The mixin on that method hands every
     * call here together with the vanilla body as {@code vanillaBody}; the cache answers only the
     * calls it can answer identically - {@code lightValue == 0} (the cache key carries no
     * lightValue, and folding a non-zero one in is the vanilla body's own work), on a client world,
     * on the client main thread. Everything else takes the vanilla body unchanged, so the method
     * keeps the vanilla contract for every caller: the entity, particle and block-entity render
     * paths (whose own loaded checks run in their vanilla bodies before they call in) and any addon
     * calling or injecting on this method alike. A miss also takes the vanilla body - supplied by
     * the caller, so the computation is the original method, never a reimplementation and never a
     * recursive call into this funnel.
     */
    /** Funnel telemetry counters (client main thread only) so real-game legs can verify the funnel is live. */
    private static long funnelCalls;
    private static long funnelHits;
    private static long funnelMisses;
    private static long funnelPassthrough;

    /** {@return a compact summary of combined-light funnel traffic for diagnostics}. */
    public static String funnelSummary() {
        return "funnelCalls=" + funnelCalls + " funnelHits=" + funnelHits
                + " funnelMisses=" + funnelMisses + " funnelPassthrough=" + funnelPassthrough;
    }

    /**
     * Prints {@link #funnelSummary()} once, when the JVM exits.
     * <p>
     * The counters exist so real-game legs can verify the funnel is live (item 430 reads them), but
     * nothing called this: the traffic accumulated and no surface ever showed it. A shutdown hook is
     * the one place that runs after the last lookup of every configuration - a run killed by the
     * harness's timeout included - and it only reads these counters, never the game. The font glyph
     * capture's summary hook is the same shape, for the same reason.
     */
    static {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> System.out.println("[Celtium] CELTIUM_FUNNEL_SUMMARY " + funnelSummary()),
                    "celtium-funnel-summary"));
        } catch (IllegalStateException | SecurityException ignored) {
            // A shutdown already in progress, or a security manager: the counters are a diagnostic, not a contract.
        }
    }

    public static int combinedLightFunnel(World world, BlockPos pos, int lightValue,
            java.util.function.IntSupplier vanillaBody) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("lightCache");
        try {
            funnelCalls++;
            if (!funnelApplies(ENABLED, lightValue, world.isRemote, isClientMainThread())) {
                funnelPassthrough++;
                return vanillaBody.getAsInt();
            }
            rollTick(world);
            long key = pack(pos.getX(), pos.getY(), pos.getZ());
            long cached = untag(COMBINED.getAndMoveToLast(key));
            int value;
            if (cached != ABSENT) {
                funnelHits++;
                value = (int) cached;
            } else {
                funnelMisses++;
                value = vanillaBody.getAsInt();
                putCombined(key, value);
            }
            int sources = MinecraftDynamicLightService.snapshotSources(world, MIRROR_SOURCES);
            return sources == 0 ? value
                    : raiseCombinedLight(value, pos.getX(), pos.getY(), pos.getZ(),
                            MIRROR_SOURCES, sources);
        } finally {
            org.soutalab.celtium.vulkan.FramePassTimings.end("lightCache");
        }
    }

    /**
     * The dynamic-light mirror's raise (item 430), kept pure and free of Minecraft types so the
     * rule itself is unit-testable: the block component of a combined-light value ({@code sky <<
     * 20 | block << 4}, the vanilla packing) is raised to the strongest gathered source's
     * contribution at the block's centre - per source {@code level - distance}, the maximum over
     * sources, exactly the terrain shader's rule. The combined format carries integer levels, so
     * the contribution is floored: the mirror never shows a block brighter than the terrain raise
     * at the same centre. The raise is a maximum, never an addition and never a lowering; every
     * bit outside the block component is returned untouched.
     */
    static int raiseCombinedLight(int combined, int x, int y, int z, float[] sources, int count) {
        int block = (combined >> 4) & 0xF;
        double centreX = x + 0.5D;
        double centreY = y + 0.5D;
        double centreZ = z + 0.5D;
        double best = 0.0D;
        for (int i = 0; i < count; i++) {
            double dx = sources[i * 4] - centreX;
            double dy = sources[i * 4 + 1] - centreY;
            double dz = sources[i * 4 + 2] - centreZ;
            double contribution = sources[i * 4 + 3] - Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (contribution > best) {
                best = contribution;
            }
        }
        int raised = (int) Math.floor(best);
        if (raised <= block) {
            return combined;
        }
        if (raised > 15) {
            raised = 15;
        }
        return (combined & ~0xF0) | (raised << 4);
    }

    /**
     * The funnel's gate, kept pure and free of Minecraft types so the rule itself is unit-testable:
     * the cache may answer exactly when it is enabled, the caller asked for no lightValue floor,
     * the world is a client world and the call runs on the client main thread (the maps are
     * unsynchronised statics, and a server world's light is a different world's answer).
     */
    static boolean funnelApplies(boolean enabled, int lightValue, boolean remoteWorld,
            boolean clientMainThread) {
        return enabled && lightValue == 0 && remoteWorld && clientMainThread;
    }

    /** {@code Entity#getBrightness}: same column check, {@code getLightBrightness} at eye height. */
    public static float entityBrightness(World world, double posX, double posYEye, double posZ) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("lightCache");
        try {
            if (!ENABLED || world == null || !world.isRemote || !isClientMainThread()) {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                        MathHelper.floor(posX), 0, MathHelper.floor(posZ));
                if (world == null || !world.isBlockLoaded(pos)) {
                    return 0.0F;
                }
                pos.setY(MathHelper.floor(posYEye));
                return world.getLightBrightness(pos);
            }
            rollTick(world);
            int x = MathHelper.floor(posX), y = MathHelper.floor(posYEye), z = MathHelper.floor(posZ);
            long key = pack(x, y, z);
            long cached = untag(BRIGHTNESS.getAndMoveToLast(key));
            if (cached != ABSENT) {
                return Float.intBitsToFloat((int) cached);
            }
            MISS_POS.setPos(x, 0, z);
            float value;
            if (!world.isBlockLoaded(MISS_POS)) {
                value = 0.0F;
            } else {
                MISS_POS.setY(y);
                value = world.getLightBrightness(MISS_POS);
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

    /** Package-private so the key layout is unit-testable; see the class javadoc for the layout. */
    static long pack(long x, long y, long z) {
        return (x & 0x3ffffffL) << 38 | (y & 0xfffL) << 26 | (z & 0x3ffffffL);
    }
}
