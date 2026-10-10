package org.taumc.celeritas.mixin.core;

import net.minecraft.client.shader.Framebuffer;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the backend which render target a batch belongs to.
 * <p>
 * The group key held matrices, format, texture and state but not the target, so batches from different passes could merge
 * into one draw. The audit lists this as S10, and the framebuffer turned out not to come through {@code GlStateManager} at
 * all - its SRG source has no {@code glBindFramebuffer} - so this hooks the Minecraft class that does the binding.
 * <p>
 * Only the identity of the framebuffer is reported, not its GL name: a pass discriminator needs to tell targets apart, and
 * taking an object identity avoids reaching for a shadowed field or any other accessor machinery.
 */
@Mixin(Framebuffer.class)
public abstract class FramebufferMixin {

    @Inject(method = "func_147610_a", at = @At("HEAD"), require = 0)
    private void celtium$noteRenderTarget(boolean setViewport, CallbackInfo info) {
        // Point the game's own framebuffer (by identity) at the shared images. Fail-open.
        try {
            net.minecraft.client.shader.Framebuffer self = (net.minecraft.client.shader.Framebuffer) (Object) this;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.getFramebuffer() == self) {
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                    org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan =
                            (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current;
                    int adopted = vulkan.celtium$adoptableFramebuffer(self.framebufferWidth, self.framebufferHeight);
                    if (adopted != 0 && self.framebufferObject != adopted) {
                        self.framebufferObject = adopted;
                        self.framebufferTexture = vulkan.celtium$adoptedColorTexture();
                        self.depthBuffer = vulkan.celtium$adoptedDepthTexture();
                        vulkan.celtium$noteFramebufferAdopted();
                        vulkan.celtium$noteGlErrorAt("at the swap", org.lwjgl.opengl.GL11.glGetError());
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A hint must never break the frame.
        }
        // Probe A: right after the game's framebuffer is bound. Reading the error clears it, so the first probe that finds
        // one names the window it was produced in; the frame's own begin path then sees a clean state.
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend probeBackend =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (probeBackend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) probeBackend)
                        .celtium$noteGlErrorAt("after bindFramebuffer", org.lwjgl.opengl.GL11.glGetError());
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A probe must never break the frame.
        }
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend != null) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            backend.bindFramebuffer(System.identityHashCode(this), mc != null && mc.getFramebuffer() == (Object) this);
        }
    }
    @Inject(method = "func_147609_e", at = @At("HEAD"), require = 0)
    private void celtium$beforeUnbind(CallbackInfo info) {
        // Probe A2: the other end of the same method, so "during the bind" and "before it" are told apart.
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend probeBackend =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (probeBackend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) probeBackend)
                        .celtium$noteGlErrorAt("before unbindFramebuffer", org.lwjgl.opengl.GL11.glGetError());
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A probe must never break the frame.
        }
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend != null) backend.bindFramebuffer(0L, false);
    }

}
