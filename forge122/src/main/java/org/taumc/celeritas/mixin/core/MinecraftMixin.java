package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.render.frame.RenderAheadManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.CeleritasVintage;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Unique
    private final RenderAheadManager celeritas$renderAheadManager = new RenderAheadManager();

    @Inject(method = "runTick", at = @At("HEAD"))
    private void preRender(CallbackInfo ci) {
        celeritas$renderAheadManager.startFrame(CeleritasVintage.options().advanced.cpuRenderAheadLimit);
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void postRender(CallbackInfo ci) {
        celeritas$renderAheadManager.endFrame();
    }




    /**
     * Replaces the GL swap with nothing for a frame Vulkan already presented.
     * <p>
     * A redirect is used rather than cancelling the method at this call, so only the swap itself is replaced and the
     * profiler sections and window-resize check the same method performs still run. {@code Display.update()} is static, so
     * the handler takes no arguments; a frame that was not presented calls it through exactly as before.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "func_175601_h",
            at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/Display;update()V"), require = 0)
    private void celtium$maybeSkipGlSwap() {
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    && vulkan.celtium$consumeSwapPendingFromVulkan()) {
                // The frame is already on the swapchain. The window's events still need polling because the skipped call
                // also did that.
                vulkan.celtium$pollEvents();
                return;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A frame must still be shown if this fails, so the swap below happens.
        }
        org.lwjgl.opengl.Display.update();
    }
}
