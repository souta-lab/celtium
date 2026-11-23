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
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightAccumulator;
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;
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
 * {@code -Dceltium.dynamicLights=false} turns the gather off entirely - the terrain then shades
 * exactly as vanilla does, which is also the A/B control the fixture proof leg uses. The same
 * switch governs the CPU mirror (item 430): the gather's surviving sources are kept as a
 * world-space snapshot that {@link CeltiumLightCache} raises entity, particle and held-item
 * brightness from, so an empty gather - disabled, or no world - mirrors nothing.
 */
public final class MinecraftDynamicLightService implements DynamicLightService {

    public static final MinecraftDynamicLightService INSTANCE = new MinecraftDynamicLightService();

    /**
     * Built-in dynamic light reimplementation is off by default: dynamic lighting belongs to mods such as
     * Celeritas Dynamic Lights running over Celtium's generalised connection surface (Ledger II). The property key
     * is preserved for compatibility and opt-in A/B testing ({@code -Dceltium.dynamicLights=true}).
     */
    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("celtium.dynamicLights", "false"));

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

    // Item 698 values print: the counters above say a gather happened and how many sources it
    // kept, but not what it actually wrote - the scratch contents (camera-relative position and
    // level per source) are the discriminator between "the values are wrong" and "the values are
    // right but light nothing visible". The v1 print (first non-empty gather only) measured the
    // values correct yet could not bracket the capture frame: a leg's final summary showed
    // gatherLast=0, so whether the source was still being returned at capture time stayed open.
    // v2 therefore prints every count transition (including the drop to zero, with the call
    // index) and re-prints the values every 256 calls while the count is non-zero, so a leg can
    // place the capture frame inside the source's lifetime from the console alone. Budgets keep
    // a long session readable and under the log limiter's per-tag cap.
    private static int gatherValuesChangePrints;
    private static int gatherValuesPeriodicPrints;
    private static int gatherValuesLastCount = -1;
    private static int gatherTransitionPrints;
    private static int gatherTrackedCount;

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
        if (!ENABLED) {
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
        if (count != gatherTrackedCount) {
            if (gatherTransitionPrints < 32) {
                gatherTransitionPrints++;
                System.out.println("[Celtium] CELTIUM_GATHER_TRANSITION calls=" + gatherCalls
                        + " count=" + gatherTrackedCount + "->" + count);
            }
            gatherTrackedCount = count;
        }
        if (count > 0) {
            boolean changed = count != gatherValuesLastCount;
            boolean periodic = (gatherCalls & 255L) == 0L;
            if ((changed && gatherValuesChangePrints < 8)
                    || (periodic && gatherValuesPeriodicPrints < 16)) {
                if (changed) {
                    gatherValuesChangePrints++;
                } else {
                    gatherValuesPeriodicPrints++;
                }
                gatherValuesLastCount = count;
                StringBuilder line = new StringBuilder("[Celtium] CELTIUM_GATHER_VALUES count=");
                line.append(count);
                line.append(" calls=").append(gatherCalls);
                line.append(" cam=").append(cameraX).append(',').append(cameraY).append(',').append(cameraZ);
                int shown = Math.min(count, 4);
                for (int i = 0; i < shown; i++) {
                    line.append(" src").append(i).append('=')
                            .append(out[i * 4]).append(',').append(out[i * 4 + 1]).append(',')
                            .append(out[i * 4 + 2]).append('@').append(out[i * 4 + 3]);
                    line.append(" world").append(i).append('=')
                            .append(this.snapshot[i * 4]).append(',').append(this.snapshot[i * 4 + 1])
                            .append(',').append(this.snapshot[i * 4 + 2]);
                }
                System.out.println(line.toString());
            }
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
