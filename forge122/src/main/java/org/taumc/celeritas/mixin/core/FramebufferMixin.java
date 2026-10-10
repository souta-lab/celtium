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

    /** One-shot: whether the bind RETURN hook runs and what it sees, which decides if the adoption can happen there. */
    private static int celtium$adoptProbeLastSize = -1;

    @Inject(method = "func_147610_a", at = @At("HEAD"), require = 0)
    private void celtium$noteRenderTarget(boolean setViewport, CallbackInfo info) {
        // Announce a framebuffer replacement before the bind below, so the flush that a target change would otherwise do
        // is skipped when the framebuffer has just been replaced at a new size: that flush's wait is what stalled the
        // device on a mid-run resize. The adoption itself is not done here - at HEAD the bind has not happened yet, so the
        // size read would be the previous framebuffer's; it runs in celtium$adoptAfterBind instead.
        try {
            net.minecraft.client.shader.Framebuffer self = (net.minecraft.client.shader.Framebuffer) (Object) this;
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current)
                        .celtium$noteBindSize(self.framebufferWidth, self.framebufferHeight);
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
    /**
     * The adoption, after the bind has taken effect.
     * <p>
     * The target is prepared first, from the framebuffer that is bound now, which is the whole point of running here: a
     * resize replaces the game framebuffer's textures, and preparing the shared images before the bind would have used the
     * old size and kept them at it.
     */
    @Inject(method = "func_147610_a", at = @At("RETURN"), require = 0)
    private void celtium$adoptAfterBind(boolean setViewport, CallbackInfo info) {
        try {
            net.minecraft.client.shader.Framebuffer self = (net.minecraft.client.shader.Framebuffer) (Object) this;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc == null || mc.getFramebuffer() != self) {
                return;
            }
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (!(current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend)) {
                return;
            }
            org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan =
                    (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current;
            // The framebuffer's own size is handed down as a fallback: the bound colour attachment can be a shared
            // image that died with a previous backend instance (a world change recreates the backend but not this
            // framebuffer), which reads back as 0x0 and leaves the frame target unbuildable forever otherwise.
            boolean ensured = vulkan.celtium$ensureTargetForBoundFramebuffer(
                    self.framebufferWidth, self.framebufferHeight);
            int adopted = ensured ? vulkan.celtium$adoptableFramebuffer(self.framebufferWidth, self.framebufferHeight) : 0;
            // Printed whenever the size changes rather than a fixed number of times: the bind runs every frame, so a
            // count is used up long before a mid-run resize, which is exactly the case being looked at.
            int size = self.framebufferWidth * 100000 + self.framebufferHeight;
            if (size != celtium$adoptProbeLastSize) {
                celtium$adoptProbeLastSize = size;
                System.out.println("[Celtium] CELTIUM_ADOPT_PROBE afterBind size=" + self.framebufferWidth + "x"
                        + self.framebufferHeight + " ensured=" + ensured + " adopted=" + adopted + " currentFbo="
                        + self.framebufferObject);
            }
            if (adopted != 0 && self.framebufferObject != adopted) {
                self.framebufferObject = adopted;
                self.framebufferTexture = vulkan.celtium$adoptedColorTexture();
                self.depthBuffer = vulkan.celtium$adoptedDepthTexture();
                vulkan.celtium$noteFramebufferAdopted();
                vulkan.celtium$noteGlErrorAt("at the swap", org.lwjgl.opengl.GL11.glGetError());
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A hint must never break the frame.
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
