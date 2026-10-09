package org.soutalab.celtium.vulkan;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glGetTexLevelParameteri;

/**
 * Mirrors the OpenGL textures Minecraft binds into Vulkan images, keyed by their GL name.
 * <p>
 * The immediate path needs whatever texture the drawing state has bound - the block atlas for terrain-adjacent
 * geometry, a particle sheet, a GUI texture, a mod's own texture - and the only thing the state can observe is the GL
 * name from {@code GlStateManager.bindTexture}. This registry turns that name into a Vulkan image on first use, using
 * the same level-0 readback the terrain atlas goes through, and reuses it afterwards.
 * <p>
 * Honest limitation, stated rather than hidden: an entry is uploaded once. A texture whose contents change afterwards
 * (an animated atlas, a dynamic framebuffer texture, a mod writing into its own texture) keeps the first mirror until
 * the registry is told to refresh it. {@link #invalidate(int)} exists for that, and the refresh policy belongs with
 * the texture-animation work in the plan's stage 4.
 * <p>
 * Threading: render thread only, which is where both the GL binds and the uploads happen.
 */
public final class VulkanTextureRegistry {
    /** One mirrored texture: the Vulkan image plus the GL name and size it was created from. */
    private static final class Entry {
        final int glId;
        final int width;
        final int height;
        final VulkanGlTexture texture;

        Entry(int glId, int width, int height, VulkanGlTexture texture) {
            this.glId = glId;
            this.width = width;
            this.height = height;
            this.texture = texture;
        }
    }

    private final VulkanContext context;
    private final Map<Integer, Entry> entries = new HashMap<Integer, Entry>();
    private int mirrorCount;
    private int imageUploads;
    private int createdByAnnouncement;
    /** The GL texture ids that reach the readback refresh, recorded once each. */
    private final java.util.Set<Integer> readbackIds = new java.util.HashSet<Integer>();
    /** The images announced by the upload hook, waiting for the mirror that will use them. */
    private final java.util.Map<Integer, java.awt.image.BufferedImage> announcedImages =
            new java.util.HashMap<Integer, java.awt.image.BufferedImage>();
    private final java.util.Set<Integer> staleIds = new java.util.HashSet<Integer>();
    private int inPlaceRefreshes;
    private int failureCount;
    private String lastFailure = "none";

    public VulkanTextureRegistry(VulkanContext context) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        this.context = context;
    }

    /**
     * {@return the Vulkan mirror of the given GL texture, creating it if needed}, or {@code null} when the name is 0
     * or the mirror could not be created - the caller then refuses the batch instead of drawing with a wrong image.
     */
    /** As {@link #get(int)}, with linear filtering - for the lightmap, whose GL texture is filtered linearly. */
    public VulkanGlTexture getLinear(int glTextureId) {
        return get(glTextureId, false);
    }

    public VulkanGlTexture get(int glTextureId) {
        return get(glTextureId, true);
    }

    /** @param nearestFilter see {@link VulkanGlTexture#create(VulkanContext, int, int, boolean)} */
    /**
     * Records the image a texture is being uploaded with, so the mirror can be built from it instead of from a readback.
     * <p>
     * The upload hook receives this image - it is the same data GL is about to be given - which is what makes ownership of these
     * textures a change at one hook rather than a subsystem.
     */
    public void announceImage(int glTextureId, java.awt.image.BufferedImage image) {
        if (glTextureId == 0 || image == null) {
            return;
        }
        this.announcedImages.put(Integer.valueOf(glTextureId), image);
    }

    /** {@return the announced images converted to RGBA bytes}, or null when this texture has none. */
    private java.nio.ByteBuffer rgbaOf(java.awt.image.BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        java.nio.ByteBuffer out = org.lwjgl.system.MemoryUtil.memAlloc(w * h * 4);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                out.put((byte) ((argb >> 16) & 0xFF));
                out.put((byte) ((argb >> 8) & 0xFF));
                out.put((byte) (argb & 0xFF));
                out.put((byte) ((argb >>> 24) & 0xFF));
            }
        }
        out.flip();
        return out;
    }

    /** Builds or refreshes a mirror from an image the upload hook handed over, without reading anything back from GL. */
    private VulkanGlTexture fromImage(int glTextureId, java.awt.image.BufferedImage image, boolean nearestFilter) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            this.failureCount++;
            this.lastFailure = "announced image for GL texture " + glTextureId + " is " + width + "x" + height;
            return null;
        }
        java.nio.ByteBuffer rgba = rgbaOf(image);
        try {
            Entry existing = this.entries.get(glTextureId);
            VulkanGlTexture texture = existing != null ? existing.texture : null;
            if (texture == null) {
                texture = VulkanGlTexture.create(this.context, width, height, nearestFilter);
                this.entries.put(glTextureId, new Entry(glTextureId, width, height, texture));
                this.mirrorCount++;
            }
            texture.uploadFromImage(width, height, rgba);
            this.imageUploads++;
            this.staleIds.remove(Integer.valueOf(glTextureId));
            return texture;
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "announced image for GL texture " + glTextureId + ": " + error;
            return null;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(rgba);
        }
    }

    /**
     * Replaces a mirror's content with pixels the caller already has, when the write covers the whole texture.
     * <p>
     * The animation path writes through {@code TextureUtil.func_147955_a}, which takes the pixels and the region and writes into
     * whatever texture is bound. A region that covers the texture is the light map and every small dynamic texture, and for
     * those this is a complete replacement - no readback, and the same data GL was given. A partial region is left to the
     * readback, because a patch would need the mirror's previous content and that is what the readback is for.
     *
     * @return whether the mirror was updated from these pixels
     */
    public boolean announceFullPixels(int glTextureId, int[][] data, int width, int height, int x, int y) {
        if (glTextureId == 0 || data == null) {
            return false;
        }
        Entry entry = this.entries.get(glTextureId);
        if (entry == null) {
            // The announcement arrives before the draw path has ever asked for this texture, which is the normal order for an
            // atlas animation. Creating the mirror here needs the texture's full size, and a patch does not carry it - so the
            // size is queried and the pixels are not: the expensive half of the readback is the pixels, and those are already
            // in the announcement. The wait for the draw path was the whole reason these writes were being read back.
            int textureWidth = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
            int textureHeight = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
            if (textureWidth <= 0 || textureHeight <= 0) {
                this.failureCount++;
                this.lastFailure = "announced pixels for GL texture " + glTextureId + " whose size is "
                        + textureWidth + "x" + textureHeight;
                return false;
            }
            try {
                VulkanGlTexture created = VulkanGlTexture.create(this.context, textureWidth, textureHeight, true);
                entry = new Entry(glTextureId, textureWidth, textureHeight, created);
                this.entries.put(glTextureId, entry);
                this.mirrorCount++;
                this.createdByAnnouncement++;
            } catch (RuntimeException | LinkageError error) {
                this.failureCount++;
                this.lastFailure = "mirror for GL texture " + glTextureId + " from an announcement: " + error;
                return false;
            }
        }
        boolean wholeTexture = x == 0 && y == 0 && entry.width == width && entry.height == height;
        if (!wholeTexture && (x < 0 || y < 0 || x + width > entry.width || y + height > entry.height)) {
            return false;
        }
        java.nio.ByteBuffer rgba = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
        try {
            for (int row = 0; row < height; row++) {
                int[] source = row < data.length ? data[row] : new int[width];
                for (int col = 0; col < width; col++) {
                    int argb = col < source.length ? source[col] : 0;
                    rgba.put((byte) ((argb >> 16) & 0xFF));
                    rgba.put((byte) ((argb >> 8) & 0xFF));
                    rgba.put((byte) (argb & 0xFF));
                    rgba.put((byte) ((argb >>> 24) & 0xFF));
                }
            }
            rgba.flip();
            if (wholeTexture) {
                entry.texture.uploadFromImage(width, height, rgba);
            } else {
                entry.texture.uploadPatchFromPixels(width, height, x, y, rgba);
            }
            this.imageUploads++;
            this.staleIds.remove(Integer.valueOf(glTextureId));
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "announced pixels for GL texture " + glTextureId + ": " + error;
            return false;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(rgba);
        }
    }

    /** {@return how many mirrors were created by an announcement rather than by the draw path asking} */
    public int createdByAnnouncement() {
        return this.createdByAnnouncement;
    }

    /** {@return how many mirrors were filled from an announced image rather than a readback} */
    public int imageUploads() {
        return this.imageUploads;
    }

    public VulkanGlTexture get(int glTextureId, boolean nearestFilter) {
        if (glTextureId == 0) {
            return null;
        }
        // The ownership path: when the upload hook has handed over the image this texture is being filled with, and the
        // mirrors are not copying GL's mip levels (which would need the readback), the mirror is built from those pixels.
        java.awt.image.BufferedImage announced = VulkanGlTexture.mirrorsGlMips() ? null
                : this.announcedImages.remove(Integer.valueOf(glTextureId));
        if (announced != null) {
            return this.fromImage(glTextureId, announced, nearestFilter);
        }
        Entry existing = this.entries.get(glTextureId);
        if (existing != null) {
            if (this.staleIds.remove(Integer.valueOf(glTextureId))) {
                // In place: same image, same view, same generation - so the descriptor keys built for this mirror stay
                // valid and the cache keeps working (audit S13). Recreating it instead cost 545 sets for 546 batches.
                try {
                    existing.texture.uploadFromGlTexture(glTextureId, GL_TEXTURE_2D);
                    this.inPlaceRefreshes++;
                    // Which textures still come through the readback, named once each. The image path takes every refresh that
                    // arrives with an announced image pending; these are the ones that do not, and knowing their ids is the
                    // difference between guessing why and knowing why.
                    if (this.readbackIds.size() < 8 && this.readbackIds.add(Integer.valueOf(glTextureId))) {
                        System.out.println("[Celtium] CELTIUM_READBACK_REFRESH glTexture=" + glTextureId
                                + " (announced images pending: " + this.announcedImages.size() + ")");
                    }
                } catch (RuntimeException | LinkageError error) {
                    this.failureCount++;
                    this.lastFailure = "re-upload of GL texture " + glTextureId + ": " + error;
                }
            }
            return existing.texture;
        }
        try {
            // LWJGL 3 returns the queried value directly rather than filling a buffer.
            int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
            int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0) {
                this.failureCount++;
                this.lastFailure = "GL texture " + glTextureId + " reports size " + width + "x" + height;
                return null;
            }
            // Nearest by default, because that is what Minecraft sets for its textures - block atlas, item atlas,
            // particle sprites - and it is what the create() documentation has said all along while this call passed
            // false for every texture. The lightmap is the exception and asks for the linear variant (audit S23).
            VulkanGlTexture texture = VulkanGlTexture.create(this.context, width, height, nearestFilter);
            texture.uploadFromGlTexture(glTextureId, GL_TEXTURE_2D);
            this.entries.put(glTextureId, new Entry(glTextureId, width, height, texture));
            this.mirrorCount++;
            return texture;
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "GL texture " + glTextureId + ": " + error;
            return null;
        }
    }

    /** Drops one mirror so the next use re-uploads it; used when a texture's contents are known to have changed. */
    /**
     * Drops every mirror, so the next use of each re-reads its GL texture.
     * <p>
     * Needed because Minecraft updates some textures - the lightmap above all - through a path the upload hooks cannot
     * reach: {@code DynamicTexture.func_110564_a} uploads into the texture the object owns, and two attempts to hook that
     * path crashed the client during Mixin transformation. Refreshing from the renderer costs a re-upload per mirrored
     * texture per refresh and cannot be wrong about which texture changed.
     */
    public void invalidateAll() {
        // Marked, not dropped: dropping meant the next use created a new image and view, which changed every descriptor
        // key in the immediate path and made the cache useless. The content is re-read into the same resources instead.
        this.staleIds.addAll(this.entries.keySet());
    }

    public void invalidate(int glTextureId) {
        if (this.entries.containsKey(Integer.valueOf(glTextureId))) {
            this.staleIds.add(Integer.valueOf(glTextureId));
        }
    }

    /** {@return how many textures have been mirrored so far}, for the verification line */
    /** {@return how many mirrors were refreshed in place rather than recreated} */
    public int inPlaceRefreshes() {
        return this.inPlaceRefreshes;
    }

    public int mirrorCount() {
        return this.mirrorCount;
    }

    public int failureCount() {
        return this.failureCount;
    }

    public String lastFailure() {
        return this.lastFailure;
    }

    public int size() {
        return this.entries.size();
    }

    public void delete() {
        for (Entry entry : this.entries.values()) {
            entry.texture.delete();
        }
        this.entries.clear();
    }
}
