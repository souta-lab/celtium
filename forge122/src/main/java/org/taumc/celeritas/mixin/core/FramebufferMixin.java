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
 * The identity reported is the object identity, not the GL name: it has to stay the same for the whole life of one
 * framebuffer, and {@code framebufferObject} does not - adoption below rewrites it in place, and a resize regenerates the
 * textures of the same object - while the shader-group hook hands the id of a group's input to the post chain and only an
 * identity that never moves can be matched against it. The colour texture name travels with the identity because the
 * GL-less path has no GL to ask for a framebuffer's attachment, and because the finished outline is composited by the
 * name the game binds again, not by the identity the renderer keyed its image under.
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
                // Which framebuffer this is decides whether a size difference means "the shared images are being
                // replaced". The game binds its own differently-sized framebuffers on purpose (the loading screen's is
                // 468x1018 against the window's 854x480), and treating those as a replacement made the next ordering
                // flush drop the pending groups - the splash's own composite among them.
                net.minecraft.client.Minecraft owner = net.minecraft.client.Minecraft.getMinecraft();
                boolean main = owner != null && owner.getFramebuffer() == self;
                ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current)
                        .celtium$noteBindSize(self.framebufferWidth, self.framebufferHeight, main);
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
            net.minecraft.client.shader.Framebuffer self = (net.minecraft.client.shader.Framebuffer) (Object) this;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            // One framebuffer is one identity for its whole life: this value is what the shader-group hook hands the
            // backend as the id of a group's input, and what the queue keys the silhouette's batches under, so it must
            // not move when the GL name behind it does (adoption rewrites framebufferObject, a resize regenerates
            // framebufferTexture in place through func_147613_a).
            long identity = System.identityHashCode(self);
            backend.bindFramebuffer(identity, mc != null && mc.getFramebuffer() == self);
            // CELTIUM-OUTLINE-WIRING (call site 1 of 2; blocked on the backend - nothing is invented here): the colour
            // texture name and size attached to the framebuffer that is being bound. The GL-less path cannot ask GL for
            // an attachment (glGetNamedFramebufferAttachmentParameteri answers nothing without a context), so this bind
            // is the only place the association identity -> texture can be made. It is what lets the renderer keep the
            // silhouette in an image of its own - keyed by this identity - instead of drawing it onto the scene, and
            // what lets the later full-screen composite, which samples the outline texture by name, find the image the
            // chain wrote. The texture size (not the logical framebuffer size the hint above carries, which differs
            // while a resize is in flight) is the size an image standing for this colour texture must have.
            //
            // The pair travels together: the identity says which target a batch belongs to, the colour texture says
            // which image stands for its pixels.
            backend.bindFramebufferColorTexture(identity, self.framebufferTexture,
                    self.framebufferTextureWidth, self.framebufferTextureHeight);
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
                // The identity does not move, only the name behind it: the bind above reported the old name for this
                // identity, and this one's own report on the next bind carries the adopted name, so the pair the backend
                // holds stays current without a second hand-off here.
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
