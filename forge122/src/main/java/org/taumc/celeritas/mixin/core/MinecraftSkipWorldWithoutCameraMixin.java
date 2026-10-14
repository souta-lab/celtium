package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the world undrawn on frames that have no camera at all, using vanilla's own switch and setting it in time.
 * <p>
 * Measured 2026-10-15 with the frame-state probe on leg {@code gv-cohort-fix138}: on the very first frame the client
 * reported
 * {@code site=updateCameraAndRender frame=1 player=false renderViewEntity=false world=true skipRenderWorld=true} and
 * {@code site=renderWorld ... skipRenderWorld=true} - the flag was written and was already set when the world render
 * began, and the world render still ran, because {@code Minecraft.runGameLoop} evaluates {@code if
 * (!this.skipRenderWorld)} (Minecraft.java:1205) <em>before</em> it calls {@code updateCameraAndRender} (1209). The
 * write was correct and one level too low, which is why leg {@code gv-cohort-fix137} changed nothing.
 * <p>
 * So the flag is written at the head of {@code runGameLoop} (SRG {@code func_71411_J}), which is before the gate it
 * has to influence. The condition is the same: no player and no render view entity means no camera, and
 * {@code renderWorld} cannot be cancelled directly because this project's HEAD/RETURN pair on it holds the backend's
 * world pass open.
 * <p>
 * Written every frame, so it clears itself as soon as a camera exists again.
 * <p>
 * The world test is part of the condition, and that was found the hard way on leg {@code gv-cohort-fix139}: without it
 * the flag is also set in the main menu, where there is no world and no camera either, so nothing that depends on
 * {@code updateCameraAndRender} runs there at all. The frame-state probe's silence was the symptom - not one line
 * from either site - and the fixture stopped at four lines with no crash and no progress.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSkipWorldWithoutCameraMixin {

    @Inject(method = "func_71411_J", at = @At("HEAD"), require = 0)
    private void celtium$skipWorldWithoutCamera(CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.skipRenderWorld = mc.world != null && mc.player == null && mc.getRenderViewEntity() == null;
        }
    }
}
