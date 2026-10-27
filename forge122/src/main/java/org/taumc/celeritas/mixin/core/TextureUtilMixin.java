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
    /** Bounded caller census for the int[] upload path; see the injection below. */
    private static int celtium$uploadCallerLogs;
    private static int celtium$firstUploads;
    private static int celtium$repeats;
    private static final java.util.Set<Integer> celtium$seen = new java.util.HashSet<Integer>();
    private static int celtium$animationHits;

    /**
     * Allocation supplies the image's real extent before any sprite patch arrives. TAIL is essential: the original
     * method first deletes the same name, so a HEAD record would be retired before the atlas can use it.
     */
    @Inject(method = "func_180600_a(IIII)V", at = @At("TAIL"), require = 1)
    private static void celtium$recordAllocatedTexture(int glTextureId, int mipmapLevels, int width, int height,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.recordTextureAllocation(glTextureId, mipmapLevels, width, height);
    }

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
                // The pixels go with the hint: this is the animation path, it has the data in hand, and a region that covers
                // the texture is a complete replacement the mirror can take without reading GL back.
                backend.announceAnimatedUpload(data, width, height, x, y);
                backend.invalidateBoundTexture();
            }
        } catch (Throwable ignored) {
            // Same rule as the registration hook: a bookkeeping hint must never break a texture upload.
        }
    }

    /**
     * The plain {@code int[]} upload, which is what {@code DynamicTexture.updateDynamicTexture} uses - the light map's
     * path. The size comes from the parameters, so nothing has to be reflected and no {@code glGetTexLevelParameteri}
     * is needed (completion condition (b): the value comes from Minecraft, not from a GL query).
     * <p>
     * The SRG name is derived rather than guessed: the mapped source's parameter names carry the SRG number
     * ({@code p_110988_1_} and friends), which is exactly how the two hooks around this one were chosen.
     */
    @Inject(method = "func_110988_a", at = @At("HEAD"), require = 0)
    private static void celtium$announceUploadedPixels(int glTextureId, int[] data, int width, int height,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            // BH-36/panorama: a bounded caller census for the int[] path, because "the game uploaded white" and "our
            // path filled white" are the same mirror line otherwise. id 8 and 20 both report
            // source=announced-int[] content=opaque=65536/65536 firstOpaque=0xffffffff with announced=0.
            boolean nameScoped = glTextureId == org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.uploadCallerId();
            boolean censusDue = nameScoped ? celtium$uploadCallerLogs < 200 : celtium$uploadCallerLogs < 16;
            // The splash only runs before a world exists, so with a world loaded the stack has nothing to say unless the
            // census wants it: the walk ran on every int[] upload - Forge's cloud colour texture every frame and the
            // light map every tick - and was the main thread's largest allocation site after the frame arena (JFR,
            // Intel world, ~1 MB/s of Throwable.fillInStackTrace with gluPerspectiveSite).
            StackTraceElement[] stack = censusDue || net.minecraft.client.Minecraft.getMinecraft().world == null
                    ? Thread.currentThread().getStackTrace() : new StackTraceElement[0];
            boolean fromSplash = false;
            for (StackTraceElement frame : stack) {
                String name = frame.getClassName();
                if (name.contains("SplashProgress") || name.contains("drawSplashScreen")
                        || "func_180510_a".equals(frame.getMethodName())) {
                    fromSplash = true;
                    break;
                }
            }
            // A name-scoped, practically unlimited mode, for the question the bounded census cannot answer: which
            // uploads of ONE GL name carry which pixels. The splash's logo is the measured case - "the game uploaded
            // white" and "our path filled white" are the same mirror line, and only the sequence of first= values for
            // that name separates them. `-Dceltium.vulkan.uploadCallerId=<gl name>`; off by default.
            if (censusDue) {
                celtium$uploadCallerLogs++;
                StackTraceElement[] frames = stack;
                StringBuilder caller = new StringBuilder();
                for (int i = 2; i < Math.min(frames.length, 7); i++) {
                    caller.append(frames[i].getClassName().replaceAll(".*\\.", "")).append('.')
                            .append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber())
                            .append(" <- ");
                }
                int first = data != null && data.length > 0 ? data[0] : 0;
                // The pixels themselves, when the caller asked for one name in particular: "the game uploaded white",
                // "our path filled white" and "the mirror never got the upload" are the same mirror line otherwise, and
                // first= alone is one texel. Unit: the game's own int[] as this hook received it (ARGB, the packing
                // TextureUtil hands to glTexSubImage2D as GL_BGRA/GL_UNSIGNED_INT_8_8_8_8_REV). Limit: a bounded sample
                // of the array - up to 4096 pixels, evenly strided - so a 1080p upload cannot cost a scan per call.
                String pixels = "";
                if (nameScoped && data != null && data.length > 0) {
                    long r = 0L;
                    long g = 0L;
                    long b = 0L;
                    long a = 0L;
                    int sampled = 0;
                    int white = 0;
                    int stride = Math.max(1, data.length / 4096);
                    for (int index = 0; index < data.length; index += stride) {
                        int value = data[index];
                        r += (value >> 16) & 0xFF;
                        g += (value >> 8) & 0xFF;
                        b += value & 0xFF;
                        a += (value >>> 24) & 0xFF;
                        if ((value & 0x00FFFFFF) == 0x00FFFFFF) {
                            white++;
                        }
                        sampled++;
                    }
                    pixels = " sampled=" + sampled + " mean=(" + (r / sampled) + "," + (g / sampled) + ","
                            + (b / sampled) + "," + (a / sampled) + ") white=" + white + "/" + sampled
                            + " px0=0x" + Integer.toHexString(data[0]) + " last=0x"
                            + Integer.toHexString(data[data.length - 1]);
                }
                System.out.println("[Celtium] CELTIUM_UPLOAD_CALLER id=" + glTextureId + " " + width + "x" + height
                        + " first=0x" + Integer.toHexString(first) + pixels + " callers=[" + caller + "]");
            }
            // A full upload allocates this id's content from scratch, which is what a resource reload, a re-stitch or a
            // reused id all look like from here. The size goes along so a same-size write is treated as the content
            // update it is - a DynamicTexture refresh - rather than a new identity, which is what keeps the light map's
            // mirror alive across its per-update uploads.
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                vulkan.onGlTextureReallocated(glTextureId, width, height);
                // The id rides along too: this method's own parameter names the texture it writes, and aiming the
                // pixels at whatever is bound at hook time is how they were landing in the wrong texture.
                vulkan.announceDynamicTexture(glTextureId, data, width, height, fromSplash);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A lifecycle notification must never break texture loading.
        }
    }

    /**
     * The delete half of the lifecycle. Every vanilla texture deletion - resource unload, framebuffer teardown,
     * {@code AbstractTexture.func_147631_c} - funnels through this one method, and until now none of them reached the
     * registry: a mirror could outlive its GL texture, and an id that came back for different content would sample the
     * old pixels until some other hook noticed. {@code onGlTextureReallocated} without a size is the always-forget path,
     * which is the right answer for a deletion.
     */
    @Inject(method = "func_147942_a", at = @At("HEAD"), require = 0)
    private static void celtium$forgetDeletedTexture(int glTextureId,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.forgetTextureAllocation(glTextureId);
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                vulkan.onGlTextureReallocated(glTextureId);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A lifecycle notification must never break a deletion.
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
            } else {
                // The menu's panorama PNGs load before the backend exists, and this guard used to drop their
                // announcements silently: measured, announceImage never fired for the panorama's ids while
                // forgetTexture did, so the reused name kept the splash logo's white fill and the panorama sampled
                // white. Kept until a backend installs, which replays it.
                TerrainGpuBackends.notePendingTextureUpload(glTextureId, image);
            }
        } catch (Throwable ignored) {
            // A bookkeeping hint must never break a texture upload: if anything goes wrong the mirror simply keeps
            // whatever it had, which is the behaviour that shipped before this hook existed.
        }
    }
}
