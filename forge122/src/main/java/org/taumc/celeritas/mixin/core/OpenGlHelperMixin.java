package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attaches the shared depth as a texture instead of a renderbuffer while the shared framebuffer is bound.
 * <p>
 * Minecraft attaches its depth with {@code glFramebufferRenderbuffer}, and this binding has no external-memory renderbuffer
 * (checked: {@code EXTMemoryObject} exposes no renderbuffer entry point), so a renderbuffer cannot be backed by the
 * Vulkan-owned depth. The texture can: it is the same allocation. It only fires while the shared framebuffer itself is
 * bound, and it fails open.
 * <p>
 * The hook names its target in both the MCP and the SRG form ({@code func_153190_b}, from the build's own
 * mcp_stable 39 table - the method is vanilla's, {@code OpenGlHelper.java:786} in the decompiled sources, not a
 * Forge addition). One form alone is silently unbound in the other environment behind {@code require = 0}: the
 * mixin loads, nothing logs, and the depth attach keeps going through the renderbuffer this hook exists to
 * replace. This is the LANE-07 sweep's dual-form rule, applied to the one hook its first pass left as a
 * suspicion (QUEUE 572, second half).
 */
@Mixin(net.minecraft.client.renderer.OpenGlHelper.class)
public class OpenGlHelperMixin {
    @Inject(method = {"glFramebufferRenderbuffer", "func_153190_b"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void celtium$attachSharedDepthTexture(int target, int attachment, int renderbufferTarget,
            int renderbuffer, CallbackInfo info) {
        try {
            if (attachment != org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT) {
                return;
            }
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (!(current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend)) {
                return;
            }
            org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan =
                    (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current;
            int bound = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$getInteger(0x8CA6);
            if (bound == Integer.MIN_VALUE) {
                bound = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            }
            if (!vulkan.celtium$isSharedFramebuffer(bound)) {
                return;
            }
            int sharedDepth = vulkan.celtium$adoptedDepthTexture();
            if (sharedDepth == 0) {
                return;
            }
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(target, attachment,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, sharedDepth, 0);
            vulkan.celtium$noteGlErrorAt("at the depth attachment", org.lwjgl.opengl.GL11.glGetError());
            info.cancel();
        } catch (RuntimeException | LinkageError ignored) {
            // A redirect must never break framebuffer setup.
        }
    }
}
