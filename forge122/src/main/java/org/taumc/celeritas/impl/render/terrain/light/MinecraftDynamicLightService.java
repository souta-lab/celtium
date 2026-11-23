package org.taumc.celeritas.impl.render.terrain.light;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightAccumulator;
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;
import org.soutalab.celtium.vulkan.ConnectionGate;
import org.taumc.celeritas.impl.CeltiumLightCache;

/**
 * The Minecraft-backed {@link DynamicLightService}: every frame it walks the loaded entities and
 * collects the ones that carry or emit light, with the level rules of VulkanMod-Next's gather
 * (LGPL-3.0-or-later; the mechanism and its constants, reimplemented here):
 * <ul>
 *   <li>a burning entity is a level-15 source;</li>
 *   <li>an ignited creeper is level 10;</li>
 *   <li>a dropped item is a source at its stack's light;</li>
 *   <li>a living entity is a source at the brighter of its two held stacks.</li>
 * </ul>
 * A stack's light is its block's own light value, so a modded glowing block works unlisted; the two
 * non-block items that glow in vanilla are named (lava bucket 15, blaze rod 10). Positions are
 * interpolated with the frame's render partial ticks and lifted to roughly the carrier's hands
 * (eye height &times; 0.75; a dropped item 0.2).
 * <p>
 * The connection gate's adjudication ({@link #CONNECTION}) governs the gather: it runs only
 * when the gate seats this native service - never while an enabled Celeritas Dynamic Lights is
 * present, and never while the {@code celtium.dynamicLights} property is off - so an off or
 * stood-down gather leaves the terrain shading exactly as vanilla does, which is also the A/B
 * control the fixture proof leg uses. The same adjudication governs the CPU mirror (item 430):
 * the gather's surviving sources are kept as a world-space snapshot that
 * {@link CeltiumLightCache} raises entity, particle and held-item brightness from, so an empty
 * gather - stood down, disabled, or no world - mirrors nothing.
 */
public final class MinecraftDynamicLightService implements DynamicLightService {

    public static final MinecraftDynamicLightService INSTANCE = new MinecraftDynamicLightService();

    /** The mod id of Celeritas Dynamic Lights, the external side of this connection. */
    private static final String EXTERNAL_MOD_ID = "celeritasdynamiclights";

    /**
     * The native side's own term of the gate's adjudication. Built-in dynamic light
     * reimplementation is off by default: dynamic lighting belongs to mods such as Celeritas
     * Dynamic Lights running over Celtium's generalised connection surface (Ledger II). The
     * property key is preserved for compatibility and opt-in A/B testing
     * ({@code -Dceltium.dynamicLights=true}). It is an input to the gate, not the decision -
     * {@link #CONNECTION} is the single truth for whether this service gathers.
     */
    private static final boolean NATIVE_PROPERTY =
            Boolean.parseBoolean(System.getProperty("celtium.dynamicLights", "false"));

    /**
     * The gate's adjudication of the dynamic-lights connection, computed once when this class
     * loads - which is when the renderer first asks for a service, after every mod is known -
     * and reported once through the gate. The external terms are the partner's own: its
     * presence, and its enablement read through its own judgment (see {@link #externalEnabled}),
     * so a mode the partner itself calls disabled is disabled here too.
     */
    private static final ConnectionGate.DynamicLightsDecision CONNECTION = adjudicate();

    private static ConnectionGate.DynamicLightsDecision adjudicate() {
        boolean externalPresent = Loader.isModLoaded(EXTERNAL_MOD_ID);
        boolean[] enablementUnreadable = new boolean[1];
        boolean externalEnabled = externalPresent && externalEnabled(enablementUnreadable);
        ConnectionGate.DynamicLightsDecision decision =
                ConnectionGate.dynamicLights(externalPresent, externalEnabled, NATIVE_PROPERTY);
        String reason = decision.reason();
        if (enablementUnreadable[0]) {
            reason += "; its mode was unreadable, so enablement follows its own default (realtime)";
        }
        ConnectionGate.reportConnection("dynamicLights", decision.state().name(), reason);
        return decision;
    }

    /**
     * Reads Celeritas Dynamic Lights' own enablement judgment: the {@code dynamicLightsMode}
     * field of its config class, asked through the mode's own {@code isEnabled()} - the same
     * predicate the mod consults in its gather and its mixins, re-applied here rather than
     * re-derived (the gate's partner-judgment term). Reflection only, so this class loads and
     * adjudicates whether or not the mod is installed. An unreadable mode is reported through
     * {@code enablementUnreadable} and counts as enabled - the mod's own default mode is
     * realtime - so a broken read never turns into both sides managing the same sources.
     */
    private static boolean externalEnabled(boolean[] enablementUnreadable) {
        try {
            Class<?> config = Class.forName("toni.sodiumdynamiclights.config.DynamicLightsConfig",
                    true, MinecraftDynamicLightService.class.getClassLoader());
            Object mode = config.getField("dynamicLightsMode").get(null);
            if (mode == null) {
                enablementUnreadable[0] = true;
                return true;
            }
            return Boolean.TRUE.equals(mode.getClass().getMethod("isEnabled").invoke(mode));
        } catch (Throwable ignored) {
            enablementUnreadable[0] = true;
            return true;
        }
    }

    /** {@return the connection gate's adjudication for dynamic lights, decided once at class load}. */
    public static ConnectionGate.DynamicLightsDecision connectionDecision() {
        return CONNECTION;
    }

    private final DynamicLightAccumulator accumulator = new DynamicLightAccumulator();

    /**
     * The last gather's surviving sources in world space (xyz + level per source), with the world
     * they were gathered in. Written by {@link #gatherLights} on the client main thread and read
     * by {@link CeltiumLightCache} on the same thread; the world identity is part of the snapshot
     * so one world's sources can never raise another world's answers after a world switch.
     */
    private final float[] snapshot = new float[DynamicLightService.MAX_LIGHTS * 4];
    private int snapshotCount;
    private WorldClient snapshotWorld;

    // Gather/snapshot telemetry (item 430): the funnel's own counters can only see the combined-light
    // cache, so a leg that reads them cannot tell a gather that never ran from a snapshot the funnel
    // never matched. These counters split the ways snapshotSources can answer 0 - no gather yet, a
    // different world, a matching world with an empty snapshot - and whether gatherLights runs at all.
    // Written and read on the client main thread (the gather and the funnel both live there).
    private static long gatherCalls;
    private static long gatherDisabledReturns;
    private static long gatherNoWorldReturns;
    private static long gatherLastSources;
    private static long gatherMaxSources;
    private static long snapshotQueries;
    private static long snapshotNoSnapshot;
    private static long snapshotWorldMismatches;
    private static long snapshotEmpty;
    private static boolean gatherFirstPrinted;

    private MinecraftDynamicLightService() {
    }

    /**
     * Copies the last gather's world-space sources into {@code out} and returns how many there
     * are - or 0 when the snapshot belongs to a different world than {@code world} (or to no
     * gather at all), so a stale snapshot raises nothing.
     */
    public static int snapshotSources(World world, float[] out) {
        MinecraftDynamicLightService service = INSTANCE;
        snapshotQueries++;
        if (service.snapshotWorld == null) {
            snapshotNoSnapshot++;
            return 0;
        }
        if (service.snapshotWorld != world) {
            snapshotWorldMismatches++;
            return 0;
        }
        if (service.snapshotCount == 0) {
            snapshotEmpty++;
            return 0;
        }
        System.arraycopy(service.snapshot, 0, out, 0, service.snapshotCount * 4);
        return service.snapshotCount;
    }

    /** {@return a compact summary of gather and snapshot traffic for diagnostics (item 430)}. */
    public static String diagnosticSummary() {
        return "gatherCalls=" + gatherCalls + " gatherDisabled=" + gatherDisabledReturns
                + " gatherNoWorld=" + gatherNoWorldReturns + " gatherLast=" + gatherLastSources
                + " gatherMax=" + gatherMaxSources + " snapshotQueries=" + snapshotQueries
                + " snapshotNoSnapshot=" + snapshotNoSnapshot
                + " snapshotMismatch=" + snapshotWorldMismatches + " snapshotEmpty=" + snapshotEmpty;
    }

    private void clearSnapshot() {
        this.snapshotCount = 0;
        this.snapshotWorld = null;
    }

    @Override
    public int gatherLights(float[] out, double cameraX, double cameraY, double cameraZ) {
        gatherCalls++;
        if (CONNECTION.state() != ConnectionGate.DynamicLightsState.NATIVE) {
            gatherDisabledReturns++;
            clearSnapshot();
            return 0;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        WorldClient world = minecraft.world;
        if (world == null) {
            gatherNoWorldReturns++;
            clearSnapshot();
            return 0;
        }
        float partialTicks = minecraft.getRenderPartialTicks();
        this.accumulator.begin(cameraX, cameraY, cameraZ);
        for (Entity entity : world.loadedEntityList) {
            int level = lightLevel(entity);
            if (level <= 0) {
                continue;
            }
            double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
            double y = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks;
            double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;
            double lift = entity instanceof EntityItem ? 0.2D : entity.getEyeHeight() * 0.75D;
            this.accumulator.offer(x, y + lift, z, (float) level);
        }
        int count = this.accumulator.finish(out);
        this.snapshotCount = this.accumulator.copyWorldSources(this.snapshot);
        this.snapshotWorld = world;
        gatherLastSources = count;
        if (count > gatherMaxSources) {
            gatherMaxSources = count;
        }
        if (count > 0 && !gatherFirstPrinted) {
            gatherFirstPrinted = true;
            // In-run, once: a leg reads this without depending on shutdown-time log capture.
            System.out.println("[Celtium] CELTIUM_GATHER_FIRST count=" + count
                    + " snapshot=" + this.snapshotCount);
        }
        return count;
    }

    /** {@return the light level a single entity contributes as a source, or 0 when it is not one}. */
    static int lightLevel(Entity entity) {
        if (entity.isBurning()) {
            return 15;
        }
        if (entity instanceof EntityCreeper && ((EntityCreeper) entity).hasIgnited()) {
            return 10;
        }
        if (entity instanceof EntityItem) {
            return stackLight(((EntityItem) entity).getItem());
        }
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            return Math.max(stackLight(living.getHeldItemMainhand()),
                    stackLight(living.getHeldItemOffhand()));
        }
        return 0;
    }

    /** {@return the light level of a held or dropped stack, from its block where it has one}. */
    static int stackLight(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == Items.LAVA_BUCKET) {
            return 15;
        }
        if (item == Items.BLAZE_ROD) {
            return 10;
        }
        Block block = Block.getBlockFromItem(item);
        if (block == null || block == Blocks.AIR) {
            return 0;
        }
        return block.getDefaultState().getLightValue();
    }
}
