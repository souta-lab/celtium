package org.soutalab.celtium.validation;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Exercises a mod camera transform while the view entity itself remains still. */
final class CameraEventProbe {
    private final int startTick = Integer.getInteger("celtium.validation.cameraEventStartTick", 300);
    private final float yaw = Float.parseFloat(System.getProperty("celtium.validation.cameraEventYaw", "75"));
    private boolean announced;

    @SubscribeEvent
    public void camera(EntityViewRenderEvent.CameraSetup event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.player.ticksExisted < this.startTick) {
            return;
        }
        // GVCLib applies its vehicle camera transform through this same direct GL call during CameraSetup.
        GL11.glRotatef(this.yaw, 0.0F, 1.0F, 0.0F);
        if (!this.announced) {
            this.announced = true;
            System.out.println("[Celtium] CELTIUM_CAMERA_EVENT_PROBE tick=" + mc.player.ticksExisted + " yaw=" + this.yaw);
        }
    }
}
