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
        // Not final: a full upload may resize the Vulkan image, and a stale size here makes every later patch's bounds and
        // whole-texture check wrong (audit A07). The texture identity stays final.
        int width;
        int height;
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
    /** Transfers the image refused because the content was already there. */
    private int imageUploadsSkipped;
    private int createdByAnnouncement;
    /** Readback refreshes by the origin that asked for them. */
    private final java.util.Map<String, Integer> readbackByOrigin = new java.util.HashMap<String, Integer>();
    private String currentOrigin = "unknown";
    /** The distinct reasons a patch was refused, each printed once. */
    private final java.util.Set<String> patchRejections = new java.util.HashSet<String>();
    /** The GL texture ids that reach the readback refresh, recorded once each. */
    private final java.util.Set<Integer> readbackIds = new java.util.HashSet<Integer>();
    /**
     * The last image announced for each texture.
     * <p>
     * Kept rather than consumed. Consuming it meant the announcement was spent by whichever refresh asked first, and every
     * later refresh of the same texture - the ones the draw path marks stale - found nothing pending and read GL back.
     * That was the whole of the remaining readback stream: about four thousand nine hundred refreshes for eight textures
     * whose announcements had already been used once. The cost of keeping them is one image per mirrored texture, and the
     * count of those has been measured at eight to eleven all along.
     */
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

    /**
     * {@return the single image for this GL texture, created if this is the first time it is asked for}.
     * <p>
     * This is the owner's door: the backend used to create its own atlas and light map images, so the announced pixels went
     * into one image while the descriptors sampled another and every animation ended in a full readback. The filter is a
     * property of the image, so it is decided here once, by the caller that knows what the texture is.
     *
     * @param nearestFilter see {@link VulkanGlTexture#create(VulkanContext, int, int, boolean)}
     */
    public VulkanGlTexture imageFor(int glTextureId, int width, int height, boolean nearestFilter) {
        Entry entry = this.entries.get(Integer.valueOf(glTextureId));
        if (entry != null) {
            return entry.texture;
        }
        VulkanGlTexture texture = VulkanGlTexture.create(this.context, width, height, nearestFilter);
        this.entries.put(Integer.valueOf(glTextureId), new Entry(glTextureId, width, height, texture));
        this.mirrorCount++;
        return texture;
    }

    /** Records that the whole image has been filled, which a partial patch alone cannot establish. */
    public void markFullyInitialized(int glTextureId) {
        if (glTextureId != 0) {
            Entry entry = this.entries.get(Integer.valueOf(glTextureId));
            if (entry != null) {
                this.fullyInitialized.add(Integer.valueOf(glTextureId));
                // And on the image itself, which is what the descriptor decision reads. Writing only the set here is what
                // kept "the mirror for texture N has never received its full content" true after a full supply.
                entry.texture.markFullContentArrived();
            }
        }
    }

    /**
     * {@return whether the invalidation can be answered without reading the texture back from OpenGL}.
     * <p>
     * True only when the image is fully initialized and a CPU application has landed in it that no readback has consumed:
     * the write that invalidated the mirror handed over its pixels, and they went into the image the descriptors sample.
     */
    public boolean canSkipReadback(int glTextureId) {
        Integer id = Integer.valueOf(glTextureId);
        if (!this.fullyInitialized.contains(id)) {
            return false;
        }
        Integer applied = this.announcedGeneration.get(id);
        if (applied == null) {
            return false;
        }
        Integer consumed = this.consumedGeneration.get(id);
        return consumed == null || applied.intValue() > consumed.intValue();
    }

    /** Consumes the CPU application that made a readback unnecessary, so the next write is judged on its own. */
    public void consumeReadback(int glTextureId) {
        Integer id = Integer.valueOf(glTextureId);
        Integer applied = this.announcedGeneration.get(id);
        if (applied != null) {
            this.consumedGeneration.put(id, applied);
        }
        this.readbacksSkippedAfterAnnouncement++;
        // The generation being consumed means the pixels the invalidation reported are already in this image, so the
        // stale mark - "the mirror may not match GL" - is false for it and must not send the next lookup into the
        // readback path. Before this was removed, the directly held refresh skipped its readback while the registry's
        // get() still re-uploaded the same texture on the next bind, which is the readback stream that was supposed
        // to be gone: it ran once per animated-sprite invalidation for every texture the immediate path samples.
        this.staleIds.remove(id);
    }

    /** {@return how many readbacks were skipped because the announced pixels were already in the image}. */
    public int readbacksSkippedAfterAnnouncement() {
        return this.readbacksSkippedAfterAnnouncement;
    }

    /** Records one CPU application into the image, whatever producer it came from. */
    private void recordApplication(int glTextureId, boolean wholeTexture) {
        Integer id = Integer.valueOf(glTextureId);
        this.announcedGeneration.put(id, Integer.valueOf(this.announceCounter = this.announceCounter + 1));
        if (wholeTexture) {
            this.fullyInitialized.add(id);
        }
    }

    /** @param nearestFilter see {@link VulkanGlTexture#create(VulkanContext, int, int, boolean)} */
    /**
     * Records the image a texture is being uploaded with, so the mirror can be built from it instead of from a readback.
     * <p>
     * The upload hook receives this image - it is the same data GL is about to be given - which is what makes ownership of these
     * textures a change at one hook rather than a subsystem.
     */
    /** FBO rendering invalidates the old CPU announcement; future sampling must read the returned game texture. */
    public void invalidateRenderedTexture(int id) {
        this.announcedImages.remove(Integer.valueOf(id));
        this.staleIds.add(Integer.valueOf(id));
    }

    public void announceImage(int glTextureId, java.awt.image.BufferedImage image) {
        if (glTextureId == 0 || image == null) {
            return;
        }
        this.announcedImages.put(Integer.valueOf(glTextureId), image);
        // A generation, not the image identity: Minecraft updates a DynamicTexture in place, so the same BufferedImage
        // object arrives with different pixels. The generation changes on every announcement and is consumed when the
        // mirror is built from it, which is what makes "already converted this one" safe to skip.
        this.announcedGeneration.put(Integer.valueOf(glTextureId),
                Integer.valueOf(this.announceCounter = this.announceCounter + 1));
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
            Entry entry = this.entries.get(glTextureId);
            VulkanGlTexture texture = entry != null ? entry.texture : null;
            if (texture == null) {
                texture = VulkanGlTexture.create(this.context, width, height, nearestFilter);
                entry = new Entry(glTextureId, width, height, texture);
                this.entries.put(glTextureId, entry);
                this.mirrorCount++;
            }
            if (texture.uploadFromImage(width, height, rgba)) {
                // Counted only when the pixels were transferred, and the metadata follows the image: a full upload may resize
                // the Vulkan image, and a stale entry size then makes every later patch bounds check wrong (audit A07).
                this.imageUploads++;
                entry.width = width;
                entry.height = height;
                // The announcement carries the whole texture - announceImage is only ever reached by a full upload -
                // so a successful conversion is an initialization, the same fact the readback path records. Without
                // this mark the image holds full content while fullyInitialized does not know it, and every later
                // partial patch is refused as "before initialization" - which is exactly where the atlas's animated
                // sprite writes were landing and being read back instead.
                markFullyInitialized(glTextureId);
            } else {
                this.imageUploadsSkipped++;
            }
            // The stale mark is cleared here as well as in the patch path. Without this the image is applied and the texture is
            // still marked stale, so the very next lookup refreshes it - from the readback - and the announcement's work is
            // thrown away. That was the whole of the remaining stream: all nine hundred and eighty refreshes came from this
            // origin, which is the one origin whose writes are announced.
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
     * Converts the animation method's pixel array into the RGBA bytes this renderer uploads.
     * <p>
     * The array is {@code int[mipLevel][y * width + x]}: the first index is the mip level and the second is a **flat** texel
     * offset, not a row. Level 0 is the patch being written. ARGB comes in and RGBA goes out, matching both the readback path
     * and the image path. This is static and device-free on purpose: it is the piece the CPU contract check exercises.
     *
     * @return the number of texels written
     */
    public static int toRgba(int[][] data, int width, int height, java.nio.ByteBuffer out) {
        int[] level0 = data != null && data.length > 0 && data[0] != null ? data[0] : new int[0];
        int written = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int texel = row * width + col;
                int argb = texel < level0.length ? level0[texel] : 0;
                out.put((byte) ((argb >> 16) & 0xFF));
                out.put((byte) ((argb >> 8) & 0xFF));
                out.put((byte) (argb & 0xFF));
                out.put((byte) ((argb >>> 24) & 0xFF));
                written++;
            }
        }
        return written;
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
            this.rejectPatch("no texture id or no data (id=" + glTextureId + ", data=" + (data == null ? "null" : "present") + ")");
            return false;
        }
        Entry entry = this.entries.get(glTextureId);
        if (entry == null) {
            // The announcement arrives before the draw path has ever asked for this texture, which is the normal order for an
            // atlas animation. Creating the mirror here needs the texture's full size and its magnification filter, and a
            // patch carries neither - so both are asked of the texture itself, not of whatever happens to be bound. The
            // bound-read this used to do asked the wrong texture and produced a nearest-filtered image for textures that
            // need linear (the light map): the draw path then had to throw the image away to get the right filter.
            int textureWidth = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(glTextureId, 0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
            int textureHeight = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(glTextureId, 0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
            if (textureWidth <= 0 || textureHeight <= 0) {
                this.failureCount++;
                this.lastFailure = "announced pixels for GL texture " + glTextureId + " whose size is "
                        + textureWidth + "x" + textureHeight;
                return false;
            }
            int magFilter = org.lwjgl.opengl.GL45C.glGetTextureParameteri(glTextureId,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER);
            try {
                VulkanGlTexture created = VulkanGlTexture.create(this.context, textureWidth, textureHeight,
                        magFilter == org.lwjgl.opengl.GL11.GL_NEAREST);
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
        if (!wholeTexture && !this.fullyInitialized.contains(Integer.valueOf(glTextureId))) {
            // A partial patch into an image that has never been filled whole would sit next to undefined texels. The draw
            // path already refuses such a mirror (hasFullContent), so applying it would only make the state dishonest:
            // the pixels are not sampled, and the bookkeeping would claim the image is up to date. Refusing here keeps
            // the readback as the one way such a texture gets established.
            this.patchesBeforeInitialization++;
            this.rejectPatch("partial patch " + width + "x" + height + " at " + x + "," + y
                    + " before the image for GL texture " + glTextureId + " was initialized");
            return false;
        }
        if (!wholeTexture && (x < 0 || y < 0 || x + width > entry.width || y + height > entry.height)) {
            this.rejectPatch("region " + width + "x" + height + " at " + x + "," + y + " outside the mirror "
                    + entry.width + "x" + entry.height);
            return false;
        }
        java.nio.ByteBuffer rgba = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
        try {
            // The array the animation method passes is int[mipLevel][y * width + x], not an array of rows. The first index is
            // the mip level and the second is the flat texel index, so level 0 is the whole patch and the indexing below is
            // one-dimensional. Treating data[row] as a row read the wrong texels for every patch of every animated sprite.
            toRgba(data, width, height, rgba);
            rgba.flip();
            long applicationStart = System.nanoTime();
            if (wholeTexture) {
                entry.texture.uploadFromImage(width, height, rgba);
            } else {
                entry.texture.uploadPatchFromPixels(width, height, x, y, rgba);
            }
            long applicationNanos = System.nanoTime() - applicationStart;
            if (wholeTexture) {
                this.fullImageCount++;
                this.fullImageBytes += (long) width * height * 4L;
                this.fullImageNanos += applicationNanos;
            } else {
                this.patchCount++;
                this.patchBytes += (long) width * height * 4L;
                this.patchNanos += applicationNanos;
            }
            this.imageUploads++;
            this.staleIds.remove(Integer.valueOf(glTextureId));
            recordApplication(glTextureId, wholeTexture);
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "announced pixels for GL texture " + glTextureId + ": " + error;
            this.rejectPatch("conversion or upload failed: " + error);
            return false;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(rgba);
        }
    }

    /**
     * Reports a refused patch once per reason.
     * <p>
     * The counter said a hundred and six patches were applied against nearly five thousand refreshes that still read back, and
     * the three refusal conditions said nothing about which one was responsible. This is the instrument that has answered every
     * question on this path for ten rounds.
     */
    private void rejectPatch(String reason) {
        if (this.patchRejections.size() < 8 && this.patchRejections.add(reason)) {
            System.out.println("[Celtium] CELTIUM_PATCH_REJECTED " + reason);
        }
    }

    /**
     * Applies an announced image to a texture this registry does not own.
     * <p>
     * The block atlas and the light map are held directly by the backend rather than through this registry, and they refreshed
     * with a readback on every animation - about eleven hundred of them a run - without ever consulting an announcement. The
     * light map is a whole-texture write, so the same pixel path serves it.
     *
     * @return whether an announced image was available and applied
     */
    public boolean applyAnnouncedTo(VulkanGlTexture target, int glTextureId) {
        java.awt.image.BufferedImage image = this.announcedImages.get(Integer.valueOf(glTextureId));
        if (image == null || target == null) {
            return false;
        }
        java.nio.ByteBuffer rgba = rgbaOf(image);
        try {
            boolean uploaded = target.uploadFromImage(image.getWidth(), image.getHeight(), rgba);
            if (!uploaded) {
                this.imageUploadsSkipped++;
                return true;
            }
            this.imageUploads++;
            // Same contract as the int[][] producer: the pixels are in the image the descriptors sample, so the invalidation
            // that follows does not need to read the texture back.
            recordApplication(glTextureId, true);
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "announced image for directly held GL texture " + glTextureId + ": " + error;
            return false;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(rgba);
        }
    }

    /** {@return the readback refreshes by origin}, for the summary line. */
    public String readbackByOrigin() {
        return this.readbackByOrigin.isEmpty() ? "none" : this.readbackByOrigin.toString();
    }

    /** Sets the origin recorded against refreshes until it changes. */
    public void origin(String origin) {
        this.currentOrigin = origin;
    }

    /** {@return how many mirrors were created by an announcement rather than by the draw path asking} */
    public int createdByAnnouncement() {
        return this.createdByAnnouncement;
    }

    /** {@return transfers the image refused because the content was already there}, which are not updates. */
    public int imageUploadsSkipped() {
        return this.imageUploadsSkipped;
    }

    /** {@return how many mirrors were filled from an announced image rather than a readback} */
    public int imageUploads() {
        return this.imageUploads;
    }

    /** Split of the registry lookup cost: announced-image conversions, stale GL re-uploads, plain hits. */
    public static long ANNOUNCED_NANOS;
    public static long ANNOUNCED_CALLS;
    public static long STALE_NANOS;
    public static long STALE_CALLS;
    public static long HIT_CALLS;
    /** Announcement generation per texture, and the generation already turned into a mirror. */
    private final java.util.Map<Integer, Integer> announcedGeneration = new java.util.HashMap<Integer, Integer>();
    /**
     * The ownership contract for a mirrored texture, kept in one place.
     * <p>
     * One GL texture id has exactly one Vulkan image, and it is the one this registry holds - the backend samples the same
     * object through {@link #imageFor}. Content arrives from two producers and the state below says which of them the image
     * has already received:
     * <ul>
     *   <li>{@code fullyInitialized} - the whole image has been filled at least once (a full readback, or an announcement
     *       that covered the whole texture). A partial patch applied before this would leave the untouched texels undefined,
     *       so a readback is still required in that case.</li>
     *   <li>{@code announcedGeneration} / {@code consumedGeneration} - every CPU application bumps the first, whatever
     *       producer it came from; a readback that is skipped because the pixels are already there, and the mirror build that
     *       converts an announced image, both consume it. One counter for both producers is what makes "the write that
     *       invalidated the mirror already handed its pixels over" a fact rather than an assumption.</li>
     * </ul>
     */
    private final java.util.Set<Integer> fullyInitialized = new java.util.HashSet<Integer>();
    /** The generation already present in the image, per texture: consumed by a readback skip or by a mirror build. */
    private final java.util.Map<Integer, Integer> consumedGeneration = new java.util.HashMap<Integer, Integer>();
    private int announceCounter;
    private int readbacksSkippedAfterAnnouncement;
    /** CPU application cost, so "the update path is cheap" is a measurement and not an assumption. */
    private long patchCount;
    private long patchBytes;
    private long patchNanos;
    private long fullImageCount;
    private long fullImageBytes;
    private long fullImageNanos;
    private int patchesBeforeInitialization;
    private int forgotten;
    /** How many announced-image conversions were skipped because the same announcement was already consumed. */
    public static long ANNOUNCED_SKIPS;
    /**
     * Forgets a GL texture, retiring the image it owned.
     * <p>
     * Minecraft deletes and re-creates textures on a resource reload, and the same numeric id can come back for different
     * content. Without this the registry would keep the old image under a reused id: patches would land in it, the size
     * would be the old size, and the sampled content would be a different texture's. The image is destroyed through the
     * retire queue because a frame that was already recorded may still reference it.
     */
    public void forgetTexture(int glTextureId) {
        Integer id = Integer.valueOf(glTextureId);
        Entry entry = this.entries.remove(id);
        this.fullyInitialized.remove(id);
        this.announcedGeneration.remove(id);
        this.consumedGeneration.remove(id);
        this.announcedImages.remove(id);
        if (entry != null) {
            this.forgotten++;
            final VulkanGlTexture image = entry.texture;
            VulkanRetireQueue.retire(new Runnable() {
                @Override
                public void run() {
                    image.delete();
                }
            });
        }
    }

    /** {@return whether a mirror already exists for this id at exactly this size} */
    public boolean hasEntry(int glTextureId, int width, int height) {
        Entry entry = this.entries.get(Integer.valueOf(glTextureId));
        return entry != null && entry.width == width && entry.height == height;
    }

    /** {@return how many GL textures were forgotten after deletion}. */
    public int forgotten() {
        return this.forgotten;
    }

    /** {@return the CPU application cost, as count/bytes/millis for patches and full images}. */
    public String patchCostSummary() {
        return "patches=" + this.patchCount + "/" + this.patchBytes + "B/" + (this.patchNanos / 1000000L)
                + "ms full=" + this.fullImageCount + "/" + this.fullImageBytes + "B/" + (this.fullImageNanos / 1000000L)
                + "ms beforeInit=" + this.patchesBeforeInitialization + " forgotten=" + this.forgotten;
    }

    /** {@return how many 1x1 mirrors the last invalidation left alone}. */


    public VulkanGlTexture get(int glTextureId, boolean nearestFilter) {
        if (glTextureId == 0) {
            return null;
        }
        // The ownership path: when the upload hook has handed over the image this texture is being filled with, and the
        // mirrors are not copying GL's mip levels (which would need the readback), the mirror is built from those pixels.
        java.awt.image.BufferedImage announced = this.announcedImages.get(Integer.valueOf(glTextureId));
        if (announced != null) {
            // The conversion is 1.17 ms per call and it ran on almost every batch: the announcement stayed pending, so the
            // same image was turned into RGBA again and again. A consumed generation means the mirror already holds these
            // pixels, and this lookup is then a plain hit.
            Integer current = this.announcedGeneration.get(Integer.valueOf(glTextureId));
            Integer consumed = this.consumedGeneration.get(Integer.valueOf(glTextureId));
            if (current != null && current.equals(consumed) && this.entries.get(Integer.valueOf(glTextureId)) != null) {
                ANNOUNCED_SKIPS++;
                HIT_CALLS++;
                return this.entries.get(Integer.valueOf(glTextureId)).texture;
            }
            long announcedStart = System.nanoTime();
            try {
                VulkanGlTexture converted = this.fromImage(glTextureId, announced, nearestFilter);
                if (converted != null) {
                    this.consumedGeneration.put(Integer.valueOf(glTextureId), current);
                }
                return converted;
            } finally {
                ANNOUNCED_NANOS += System.nanoTime() - announcedStart;
                ANNOUNCED_CALLS++;
            }
        }
        Entry existing = this.entries.get(glTextureId);
        if (existing != null) {
            if (this.staleIds.remove(Integer.valueOf(glTextureId))) {
                if (canSkipReadback(glTextureId)) {
                    // The write that made this texture stale announced its pixels, and they are already in this image:
                    // the refresh the direct path skips must be skipped here too, or registry-held textures (anything
                    // that is not the directly held atlas or light map) still re-read GL on every animated write.
                    consumeReadback(glTextureId);
                    HIT_CALLS++;
                    return existing.texture;
                }
                // In place: same image, same view, same generation - so the descriptor keys built for this mirror stay
                // valid and the cache keeps working (audit S13). Recreating it instead cost 545 sets for 546 batches.
                try {
                    long uploadStart = System.nanoTime();
                    existing.texture.uploadFromGlTexture(glTextureId, GL_TEXTURE_2D);
                    STALE_NANOS += System.nanoTime() - uploadStart;
                    STALE_CALLS++;
                    this.inPlaceRefreshes++;
                    // Which origin produced this refresh. The counters say most updates are served from announcements and a thousand
                    // are not; this says which caller the thousand belong to, rather than another name being guessed at.
                    this.readbackByOrigin.merge(this.currentOrigin, Integer.valueOf(1), Integer::sum);
                    // Which textures still come through the readback, named once each. The image path takes every refresh that
                    // arrives with an announced image pending; these are the ones that do not, and knowing their ids is the
                    // difference between guessing why and knowing why.
                    if (this.readbackIds.size() < 8 && this.readbackIds.add(Integer.valueOf(glTextureId))) {
                        System.out.println("[Celtium] CELTIUM_READBACK_REFRESH glTexture=" + glTextureId
                                + " size=" + existing.width + "x" + existing.height
                                + " view=" + Long.toHexString(existing.texture.view())
                                + " (announced images pending: " + this.announcedImages.size() + ")");
                    }
                } catch (RuntimeException | LinkageError error) {
                    this.failureCount++;
                    this.lastFailure = "re-upload of GL texture " + glTextureId + ": " + error;
                }
            }
            HIT_CALLS++;
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
            if (width >= 512 || height >= 512) {
                // Who asks for a screen-sized mirror: the stack is the evidence for whether the frame's own colour is
                // being mirrored (and read back every frame) or something else is.
                StringBuilder where = new StringBuilder("[Celtium] CELTIUM_BIG_MIRROR glTexture=" + glTextureId + " size="
                        + width + "x" + height + " from");
                StackTraceElement[] trace = new Throwable().getStackTrace();
                for (int i = 0; i < Math.min(8, trace.length); i++) {
                    String cls = trace[i].getClassName();
                    where.append(" <- ").append(cls.substring(cls.lastIndexOf('.') + 1)).append('.')
                            .append(trace[i].getMethodName());
                }
                System.out.println(where);
            }
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
