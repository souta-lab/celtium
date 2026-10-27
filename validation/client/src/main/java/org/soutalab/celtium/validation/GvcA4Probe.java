package org.soutalab.celtium.validation;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Opt-in server-constructor probe. Constructs but never spawns or attacks with either entity. */
final class GvcA4Probe {
    private boolean done;

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (this.done || event.phase != TickEvent.Phase.END) return;
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world = server == null ? null : server.getWorld(0);
        if (world == null) return;
        this.done = true;
        try {
            Entity a4 = EntityList.createEntityByIDFromName(new ResourceLocation("minecraft:entitygvcr2_air_a4"), world);
            if (a4 == null) throw new IllegalStateException("A4 registry entry missing");
            String[] models = (String[]) a4.getClass().getField("bullet_model1").get(a4);
            String[] textures = (String[]) a4.getClass().getField("bullet_tex1").get(a4);
            Class<?> bulletType = Class.forName("gvclib.entity.EntityB_BulletAA");
            Object bullet = bulletType.getConstructor(World.class).newInstance(world);
            String setter;
            try {
                bulletType.getMethod("setModel", String.class).invoke(bullet, models[1]);
                setter = "OK";
            } catch (InvocationTargetException failure) {
                setter = failure.getCause().getClass().getName();
            }
            System.out.println("CELTIUM_GVC_A4_PROBE models=" + Arrays.toString(Arrays.copyOf(models, 2))
                    + " textures=" + Arrays.toString(Arrays.copyOf(textures, 2)) + " setter=" + setter);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            System.out.println("CELTIUM_GVC_A4_PROBE FAILED " + failure);
        }
    }
}
