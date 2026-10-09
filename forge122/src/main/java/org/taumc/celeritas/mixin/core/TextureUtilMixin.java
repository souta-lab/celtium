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

    private static int celtium$hooked;
    private static int celtium$firstUploads;
    private static int celtium$repeats;
    private static final java.util.Set<Integer> celtium$seen = new java.util.HashSet<Integer>();
    private static int celtium$animationHits;

    /**
     * The animation path.
     * <p>
     * Reading the sources settled which method actually animates a sprite: {@code TextureMap.func_94248_c} binds the
     * atlas through {@code TextureUtil.func_94277_a} and then calls each sprite's {@code func_94219_l}, which uploads
     * its frame through {@code TextureUtil.func_147955_a} - a method that takes no texture id because it writes into
     * whatever is bound. That is why hooking {@code func_110989_a} saw uploads but never a repeat: it is the
     * registration path, not the animation one.
     */
    @Inject(method = "func_147955_a", at = @At("HEAD"), require = 0)
    private static void celtium$invalidateAnimatedTexture(int[][] data, int width, int height, int x, int y, boolean blur,
            boolean clamp, org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            if (celtium$animationHits < 5) {
                celtium$animationHits++;
                System.out.println("celtium texture animation upload: hit=" + celtium$animationHits + " size=" + width + "x"
                        + height + " at " + x + "," + y);
            }
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.invalidateBoundTexture();
            }
        } catch (Throwable ignored) {
            // Same rule as the registration hook: a bookkeeping hint must never break a texture upload.
        }
    }

    @Inject(method = "func_110989_a", at = @At("HEAD"), require = 0)
    private static void celtium$invalidateUploadedTexture(int glTextureId, java.awt.image.BufferedImage image, boolean blur,
            boolean clamp, CallbackInfoReturnable<Integer> info) {
        try {
            // Counted here as well as in the backend, and reported for the first few uploads only: whether the hook
            // fires at all is what was unproven, and a counter that never reaches the log cannot answer it.
            // A first upload is registration; the animation is a *second* upload of the same texture id, so the
            // interesting event is the repeat, not the hit.
            if (celtium$seen.add(Integer.valueOf(glTextureId))) {
                celtium$firstUploads++;
            } else if (celtium$repeats < 5) {
                celtium$repeats++;
                System.out.println("celtium texture re-upload (animated): id=" + glTextureId + " firstUploads="
                        + celtium$firstUploads + " repeat=" + celtium$repeats);
            }
            if (celtium$hooked < 3) {
                celtium$hooked++;
                System.out.println("celtium texture-upload hook: id=" + glTextureId + " hit=" + celtium$hooked);
            }
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                // The image goes with the announcement: it is the same data GL is about to be given, so the mirror can be
                // built from it rather than from a readback of what GL received.
                backend.announceTextureUpload(glTextureId, image);
                backend.invalidateTexture(glTextureId);
            }
        } catch (Throwable ignored) {
            // A bookkeeping hint must never break a texture upload: if anything goes wrong the mirror simply keeps
            // whatever it had, which is the behaviour that shipped before this hook existed.
        }
    }
}
