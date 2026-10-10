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
     * Presents the frame from Vulkan at the game's own swap point.
     * <p>
     * The world render is the wrong place for the GL-less window: it runs only when a world is loaded, so the menu, the
     * loading screens and every GUI-only frame reached the window with nothing to show. {@code updateDisplay} is the
     * one point every frame passes through on its way to the screen, and it is also where the GL swap below sits, so
     * the present and the swap decision happen in the same place and in the right order: this presents first, and the
     * redirect on {@code Display.update()} then consumes the fact and skips the swap it replaced.
     */
    @Unique
    private static int celtium$swapPointCalls;

    @Inject(method = "func_175601_h", at = @At("HEAD"), require = 0)
    private void celtium$presentAtSwapPoint(CallbackInfo ci) {
        celtium$swapPointCalls++;
        if (celtium$swapPointCalls <= 3 || celtium$swapPointCalls % 300 == 0) {
            System.out.println("[Celtium] CELTIUM_SWAPPOINT calls=" + celtium$swapPointCalls);
        }
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
                vulkan.celtium$presentFrame();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A failed present must not stop the frame from being shown, and the swap below still runs.
        }
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
                // The frame is already on the swapchain, so only the swap is skipped: processMessages() is
                // everything Display.update() does besides it - GLFW's events, the mouse position and the resize
                // bookkeeping Minecraft.checkWindowResize reads - and skipping all of that froze input and resizes.
                org.lwjgl.opengl.Display.processMessages();
                return;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A frame must still be shown if this fails, so the swap below happens.
        }
        org.lwjgl.opengl.Display.update();
    }
}
