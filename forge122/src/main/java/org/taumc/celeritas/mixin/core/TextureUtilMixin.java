package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells the Vulkan side when a GL texture's contents have been uploaded again.
 * <p>
 * The mirror of a GL texture is uploaded once, which is correct for a static texture and wrong for an animated one:
 * Minecraft animates water, lava, fire and every other animated sprite by re-uploading its frames through
 * {@code TextureUtil.func_110989_a}, so without this hook the immediate path would draw the first frame of every
 * animated texture forever.
 * <p>
 * This is the upload half of the same Forge API swap the state mixin belongs to: the method is Minecraft's own, its
 * signature is untouched, and the original body still runs - the backend is only told that what it mirrored is now
 * stale, so the next draw re-reads it.
 */
@Mixin(TextureUtil.class)
public class TextureUtilMixin {

    @Inject(method = "func_110989_a", at = @At("HEAD"), require = 0)
    private static void celtium$invalidateUploadedTexture(int glTextureId, java.awt.image.BufferedImage image, boolean blur,
            boolean clamp, CallbackInfoReturnable<Integer> info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.invalidateTexture(glTextureId);
            }
        } catch (Throwable ignored) {
            // A bookkeeping hint must never break a texture upload: if anything goes wrong the mirror simply keeps
            // whatever it had, which is the behaviour that shipped before this hook existed.
        }
    }
}
