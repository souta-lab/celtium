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

    /**
     * {@code -Dceltium.debug.fboBind=true}: name each framebuffer the first time it is bound, with who bound it.
     * <p>
     * Once per identity, not once per bind: binds repeat every frame, so a per-bind bound was spent in the loading
     * phase and never reached the world, where the question arises - E2E-E's HUD landed in a non-main framebuffer
     * ({@code CELTIUM_HUD_GROUP ... route=chain-pair}) and only its identity said which one.
     */
    private static final boolean FBO_BIND_LOG = Boolean.getBoolean("celtium.debug.fboBind");
    private static final java.util.Set<Long> FBO_BIND_SEEN =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Native phase boundary: metadata only; the renderer owns the fullscreen geometry/material and execution. */
    @Inject(method = "func_178038_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$nativeComposite(int width, int height, boolean opaque, CallbackInfo info) {
        if (!org.taumc.celeritas.core.CeltiumNativeDraw.enabled()) return;
        TerrainGpuBackend current = TerrainGpuBackends.current();
        if (!(current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend backend)) return;
        Framebuffer self = (Framebuffer) (Object) this;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        backend.compositeFramebuffer(System.identityHashCode(self), mc.getFramebuffer() == self,
                self.framebufferTexture, self.framebufferWidth, self.framebufferHeight,
                self.framebufferTextureWidth, self.framebufferTextureHeight, width, height, opaque);
        info.cancel();
    }

    // The vanilla methods that bind raw GL framebuffers themselves (create, delete, bind, unbind). While one runs, its
    // own reports to the renderer stand; the raw bind inside it is not reported again (CeltiumGllessHint).
    @Inject(method = {"func_147610_a", "func_147609_e", "func_147605_b", "func_147608_a"}, at = @At("HEAD"),
            require = 0)
    private void celtium$enterVanillaFramebuffer(CallbackInfo info) {
        org.taumc.celeritas.core.CeltiumGllessHint.vanillaFramebufferDepth++;
    }

    @Inject(method = {"func_147610_a", "func_147609_e", "func_147605_b", "func_147608_a"}, at = @At("RETURN"),
            require = 0)
    private void celtium$leaveVanillaFramebuffer(CallbackInfo info) {
        if (org.taumc.celeritas.core.CeltiumGllessHint.vanillaFramebufferDepth > 0) {
            org.taumc.celeritas.core.CeltiumGllessHint.vanillaFramebufferDepth--;
        }
    }

    /** deleteFramebuffer also deletes its colour texture; the pair can now be retired after pending GPU work. */
    @Inject(method = "func_147608_a", at = @At("RETURN"), require = 0)
    private void celtium$retireDeletedFramebuffer(CallbackInfo info) {
        TerrainGpuBackend current = TerrainGpuBackends.current();
        if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend backend) {
            backend.celtium$forgetDeletedFramebuffer(System.identityHashCode(this));
        }
    }

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
            boolean isMainFramebuffer = mc != null && mc.getFramebuffer() == self;
            if (FBO_BIND_LOG && FBO_BIND_SEEN.size() < 64 && FBO_BIND_SEEN.add(identity)) {
                String callers = StackWalker.getInstance().walk(frames -> frames
                        .map(StackWalker.StackFrame::toStackTraceElement)
                        .filter(frame -> !frame.getClassName().startsWith("org.taumc")
                                && !frame.getClassName().startsWith("org.soutalab")
                                && !frame.getClassName().endsWith("Framebuffer"))
                        .limit(4)
                        .map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
                                + "#" + frame.getMethodName())
                        .collect(java.util.stream.Collectors.joining(" <- ")));
                System.out.println("[Celtium] CELTIUM_FBO_BIND identity=0x" + Long.toHexString(identity)
                        + " isMain=" + isMainFramebuffer + " size=" + self.framebufferTextureWidth + "x"
                        + self.framebufferTextureHeight + " class=" + self.getClass().getName()
                        + " firstBoundBy=[" + callers + "]");
            }
            backend.bindFramebuffer(identity, isMainFramebuffer);
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
            // Which object the game's own framebuffer field names, reported on every bind rather than only when that
            // object is the one being bound: during the loading phase the splash binds its own framebuffer, and
            // `mc.getFramebuffer() == self` is false for the game's own one until it is adopted - so asking that
            // question at that moment answers "not main" for the framebuffer the game itself calls its own, and its
            // white clear then lands on the chain pair the logo was drawn into (measured: CELTIUM_FBO_CENSUS
            // name=23 identity=0x512b5e2 boundAsMain=false, and the presented loading frame stayed one colour).
            // The field is the fact, and the census is the one place that answers main-ness.
            if (mc != null && backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                // The game's own framebuffer object, read through its accessor (the field itself is private): what
                // matters is that it is asked on every bind, so the answer arrives while the splash's own framebuffer
                // is the one being bound.
                net.minecraft.client.shader.Framebuffer game = mc.getFramebuffer();
                if (game != null) {
                    ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) backend).celtium$noteGameFramebuffer(
                            System.identityHashCode(game), game.framebufferTexture);
                }
            }
            // The GL name under the identity, for a readback that only has the name: the game binds the framebuffer by
            // name for glReadPixels, and which image the name's draws went into is answered by the identity. The main
            // framebuffer is the measured case - its creation routed no glFramebufferTexture2D through the stub, so
            // the colour-texture chain cannot resolve it.
            if (self.framebufferObject != 0
                    && backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) backend).celtium$noteFramebufferName(
                        identity, self.framebufferObject);
            }
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
