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
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightAccumulator;
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;

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
 * exactly as vanilla does, which is also the A/B control the fixture proof leg uses.
 */
public final class MinecraftDynamicLightService implements DynamicLightService {

    public static final MinecraftDynamicLightService INSTANCE = new MinecraftDynamicLightService();

    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("celtium.dynamicLights", "true"));

    private final DynamicLightAccumulator accumulator = new DynamicLightAccumulator();

    private MinecraftDynamicLightService() {
    }

    @Override
    public int gatherLights(float[] out, double cameraX, double cameraY, double cameraZ) {
        if (!ENABLED) {
            return 0;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        WorldClient world = minecraft.world;
        if (world == null) {
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
        return this.accumulator.finish(out);
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
