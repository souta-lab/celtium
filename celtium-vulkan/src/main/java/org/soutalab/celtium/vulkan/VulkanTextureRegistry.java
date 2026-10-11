package org.soutalab.celtium.vulkan;

import java.util.HashMap;
import java.util.Map;

import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glGetTexLevelParameteri;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;

/**
 * Mirrors the OpenGL textures Minecraft binds into Vulkan images, keyed by their GL name.
 * <p>
 * The immediate path needs whatever texture the drawing state has bound - the block atlas for terrain-adjacent
 * geometry, a particle sheet, a GUI texture, a mod's own texture - and the only thing the state can observe is the GL
 * name from {@code GlStateManager.bindTexture}. This registry turns that name into a Vulkan image on first use, using
 * the same level-0 readback the terrain atlas goes through, and reuses it afterwards.
 * <p>
 * Under the GL stub there is no readback to take: the pixels of this process are the ones the game itself announced -
 * a whole {@code BufferedImage} through {@link #announceImage}, a patch through {@link #announceFullPixels} - and the
 * uploads the stub captured for the texture. Those are the sources every decision point below falls back to, in that
 * order, and a mirror neither of them can supply is left content-less (never filled from a readback that cannot deliver)
 * so the sampling side refuses it. {@link #filledFromAnnouncement()}, {@link #filledFromCapture()} and
 * {@link #readbacksSkippedWithoutContent()} count the three outcomes.
 * <p>
 * Honest limitation, stated rather than hidden: an entry is uploaded once. A texture whose contents change afterwards
 * (an animated atlas, a dynamic framebuffer texture, a mod writing into its own texture) keeps the first mirror until
 * the registry is told to refresh it. {@link #invalidate(int)} exists for that, and the refresh policy belongs with
 * the texture-animation work in the plan's stage 4.
 * <p>
 * Threading: render thread only, which is where both the GL binds and the uploads happen.
 */
public final class VulkanTextureRegistry {

    /** Whether the skip decision above is logged; set with the mirror dump, which asks the same question of pixels. */
    private static final String MIRROR_DUMP = System.getProperty("celtium.vulkan.mirrorDump");
    private static int skipDecisionLogs;
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
    /**
     * Images this registry does not own, published under a GL name so the immediate path can sample them.
     * <p>
     * Kept apart from {@link #entries} on purpose: an entry there is a mirror this registry created and may resize,
     * write, invalidate and destroy, while a published image belongs to the renderer that handed it over - the
     * GL-less off-target colour image the outline chain writes and the game's composite samples by name. Everything
     * that treats an entry as a resource of this class (the uploads, the readback skips, {@code invalidate}, the
     * announce paths, {@code delete()}) must not see these ids, and everything that resolves a bound texture
     * ({@link #get}) must see them first. One map, checked first, is that split.
     */
    private final Map<Integer, VulkanGlTexture> externalImages = new HashMap<Integer, VulkanGlTexture>();
    private int externalPublished;
    private int externalRepublishes;
    private int externalUnpublished;
    private int externalSupersededMirrors;
    private int externalRetireLogs;
    /** Invalidations of a published name that were ignored, because its content is live. */
    private int externalInvalidatesIgnored;
    /** GL names whose publication has been named in the log, so a per-frame publish cannot flood it. */
    private final java.util.Set<Integer> externalLogged = new java.util.HashSet<Integer>();
    private int mirrorCount;
    private int imageUploads;
    /** Transfers the image refused because the content was already there. */
    private int imageUploadsSkipped;
    private int createdByAnnouncement;
    /**
     * Under the GL stub, pixels moved into a mirror from a game announcement - a whole {@link #announceImage} image or
     * the {@code int[][]} a patch announces - as one event per application.
     */
    private int filledFromAnnouncement;
    /**
     * Under the GL stub, pixels moved into a mirror from an upload the stub captured - the whole level-0 surface as GL
     * was given it, which is the only whole-image source for a texture the game uploads piecewise (the block atlas).
     */
    private int filledFromCapture;
    /**
     * Under the GL stub, captured surfaces refused because the game never wrote a pixel into the level.
     * <p>
     * A level allocated with a NULL data pointer has a surface (zeros) but no game bytes; the stub answers that through
     * {@code CeltiumGlStub.textureLevelHasGamePixels}. Filling a mirror from one would mark whole content over an image
     * no upload ever filled - measured as two 1280x720 mirrors and the light map's first read reporting
     * {@code opaque=0/… full=true}. Non-zero here is the number of times that was refused instead.
     */
    private int capturesRefusedWithoutGamePixels;
    /**
     * Under the GL stub, readbacks that were not attempted because neither an announcement nor a captured upload held
     * pixels. The mirror keeps the content it already had, which is nothing when it never received any - and a mirror
     * whose whole content never arrived is refused by the sampling side rather than drawn.
     */
    private int readbacksSkippedWithoutContent;
    /** Readback refreshes by the origin that asked for them. */
    private final java.util.Map<String, Integer> readbackByOrigin = new java.util.HashMap<String, Integer>();
    private String currentOrigin = "unknown";
    /** The distinct reasons a patch was refused, each printed once. */
    private final java.util.Set<String> patchRejections = new java.util.HashSet<String>();
    /**
     * Whether a region patch applies the producer's whole mip chain (the default) or only level 0.
     * <p>
     * The second setting is the pre-N07 behaviour and exists as a measurement control, not as a way to run the
     * renderer: the per-level census in {@link VulkanGlTexture#mirrorLevelSummary()} and the mirror dump's
     * {@code writes=}/{@code behind=} fields are what make the difference between "the producer's levels were applied"
     * and "the levels above 0 kept whatever the last whole upload put there" a pair of numbers from the same binary.
     * With the control set, {@code reachUnwritten} in the census must rise; with it unset it must stay at zero.
     */
    /**
     * Whether a mirror filled from a captured surface takes the game's own per-level data (the default) or only level
     * 0, with the chain above it generated here. The second setting is the pre-N07b behaviour and exists as a
     * measurement control: the whole-image blit averages across sprite borders, so the two settings differ in what a
     * deep sample reads - which is a claim to be measured, not assumed.
     */
    /**
     * Diagnostic: the sampler reach to force on every captured fill, whatever its content, so the reach and the
     * content can be measured apart. 0 leaves every mirror with the reach its own content justifies.
     */
    private static final int MIRROR_REACH_OVERRIDE =
            Integer.getInteger("celtium.vulkan.mirrorReachOverride", 0).intValue();
    private static final boolean MIRROR_CAPTURED_LEVELS =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.mirrorCapturedLevels", "true"));
    private static final boolean PATCH_MIP_LEVELS =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.patchMipLevels", "true"));
    /** Wrap answers that came from the GL default because the game never set that pname on that texture. */
    private static int wrapDefaultsUsed;
    /** The GL texture ids that reach the readback refresh, recorded once each. */
    private final java.util.Set<Integer> readbackIds = new java.util.HashSet<Integer>();
    /**
     * Which source filled each mirror's content, by GL name, and the census of those fills.
     * <p>
     * The defect this exists for: under the GL-less window a mirror's content can only come from an announcement or
     * from the upload the stub captured, and "the batch is submitted and accepted and produces no pixels" is what a
     * mirror filled with nothing looks like from the frame. The counters that existed said how many fills happened
     * ({@code fromAnnouncement}, {@code fromCapture}, {@code noContent}) but not <em>which texture</em> got which, so
     * a GUI panel that never appears could not be attributed to its own mirror. Each id is named once per source it
     * is filled from, with the source's own pixels summarised, so the mirror a missing batch samples is a fact in the
     * log rather than an inference.
     */
    private final java.util.Map<Integer, String> mirrorSourceById = new java.util.HashMap<Integer, String>();
    private final java.util.Map<String, Integer> mirrorFillsBySource = new java.util.LinkedHashMap<String, Integer>();
    /** Announcements made per GL name, and the ones a {@code forgetTexture} dropped before a mirror consumed them. */
    private final java.util.Map<Integer, Integer> announcementsById = new java.util.HashMap<Integer, Integer>();
    private final java.util.Map<Integer, Integer> droppedAnnouncementsById = new java.util.HashMap<Integer, Integer>();
    /** Lines printed, bounded like every other per-texture census in this class. */
    private static final int MIRROR_SOURCE_LOG_LIMIT = 32;
    private int mirrorSourceLogs;
    /** Announcements an image-less delete kept alive, because no mirror had consumed them yet. */
    private int retainedAnnouncements;
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
     * Publishes an image this registry does not own under a GL texture name, and returns the wrapper the immediate
     * path resolves that name through.
     * <p>
     * The case this exists for: under the GL-less window the entity-outline chain runs natively on the renderer's
     * own off-target colour image (see {@code GllessFrameTarget}), and the game's composite then binds that image by
     * the GL name the outline framebuffer's colour texture has - which, with no OpenGL context, has no storage of
     * its own. Without this the bound texture has no mirror, the batch is refused, and the glow never reaches the
     * screen.
     * <p>
     * <b>The contract, which the ownership split enforces.</b> This class creates nothing, resizes nothing,
     * transitions nothing on the caller's behalf at destroy time, and frees nothing: the image, its memory and its
     * view stay the caller's, and the wrapper's {@link VulkanGlTexture#delete()} releases only the sampler the
     * wrapper itself created. {@link #invalidate(int)} leaves a published image alone (its content is live - it is
     * whatever the owner rendered into it - so there is nothing a re-read could supply), {@link #forgetTexture(int)}
     * drops the entry without touching the image, and no upload or announce path may write it. The entry counts as
     * fully initialized because the owner declares the content live, which is also why the wrapper reports
     * {@link VulkanGlTexture#hasFullContent()} true immediately.
     * <p>
     * <b>The layout.</b> The published image is in {@code VK_IMAGE_LAYOUT_GENERAL} when it arrives - the off-target
     * pass attaches it in that layout - while the immediate pipelines declare
     * {@code VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL}. The owner moves it with
     * {@link VulkanGlTexture#ensureSampledLayout(VkCommandBuffer)} before the composite samples it and back with
     * {@link VulkanGlTexture#ensureGeneralLayout(VkCommandBuffer)} before the next off-target pass attaches it; the
     * wrapper records that handshake so a repeated call records no second barrier.
     * <p>
     * Idempotent for the same handles: re-publishing the same image, view and size returns the same wrapper, so a
     * per-frame call site does not churn samplers and does not invalidate the descriptor keys the immediate path
     * cached. Different handles under the same name are a replacement (the owner recreated its image): the old
     * wrapper is retired and a new one built.
     *
     * @param glTextureId the GL name the game will bind for this image
     * @param image       the caller's {@code VkImage}; never destroyed or written here
     * @param view        the caller's {@code VkImageView}; never destroyed here
     * @param width       the image width in pixels
     * @param height      the image height in pixels
     * @return the wrapper for that name, or {@code null} when the arguments are incomplete or the wrapper could not
     *         be built - the immediate path then refuses the batch exactly as it did before
     */
    public VulkanGlTexture publishExternalImage(int glTextureId, long image, long view, int width, int height) {
        if (glTextureId == 0 || image == 0L || view == 0L || width <= 0 || height <= 0) {
            this.failureCount++;
            this.lastFailure = "an external image for GL texture " + glTextureId + " (image=0x"
                    + Long.toHexString(image) + " view=0x" + Long.toHexString(view) + " " + width + "x" + height
                    + ") is incomplete";
            this.rejectPatch("external image for GL texture " + glTextureId + " was not published: "
                    + (glTextureId == 0 ? "no GL name to publish it under"
                            : image == 0L || view == 0L ? "no image or view handle" : "no usable size"));
            return null;
        }
        Integer id = Integer.valueOf(glTextureId);
        VulkanGlTexture existing = this.externalImages.get(id);
        if (existing != null && existing.standsFor(image, view, width, height)) {
            this.externalRepublishes++;
            return existing;
        }
        if (existing != null) {
            // Same name, different handles: the owner replaced its image, so this wrapper stands for a resource that
            // no longer exists. Dropped (the image itself is the owner's) rather than kept as a stale view.
            this.externalImages.remove(id);
            retireExternal(existing, "GL texture " + glTextureId + " was published with new handles");
        }
        Entry superseded = this.entries.remove(id);
        if (superseded != null) {
            // A mirror this registry created for the same name is superseded by the renderer's own live image. It is
            // this registry's object, so it is destroyed - through the retire queue, because a recorded frame may
            // still name its view. Without this the sampling side could keep resolving the empty mirror.
            final VulkanGlTexture retiredMirror = superseded.texture;
            VulkanRetireQueue.retire(new Runnable() {
                @Override
                public void run() {
                    retiredMirror.delete();
                }
            });
            this.externalSupersededMirrors++;
        }
        VulkanGlTexture shared;
        try {
            shared = VulkanGlTexture.shareExternal(this.context, image, view, width, height, VK_IMAGE_LAYOUT_GENERAL);
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "external image for GL texture " + glTextureId + ": " + error;
            this.rejectPatch("external image for GL texture " + glTextureId + " could not be published: " + error);
            return null;
        }
        this.externalImages.put(id, shared);
        this.fullyInitialized.add(id);
        this.staleIds.remove(id);
        this.externalPublished++;
        if (this.externalLogged.size() < 8 && this.externalLogged.add(id)) {
            System.out.println("[Celtium] CELTIUM_EXTERNAL_IMAGE published glTexture=" + glTextureId
                    + " image=0x" + Long.toHexString(image) + " view=0x" + Long.toHexString(view)
                    + " size=" + width + "x" + height + " layout=GENERAL owner=renderer"
                    + " (the immediate path resolves this name through the wrapper; nothing here creates,"
                    + " resizes or frees the image)");
        }
        return shared;
    }

    /**
     * Drops a published image, without touching the image.
     * <p>
     * Needed when the owner replaces or destroys the image behind the name (a resize recreates the off-target pair):
     * the wrapper's handles would name a retired image, and a batch sampling it would be a validation error rather
     * than a missing glow. The wrapper's sampler is retired; the image, its memory and its view are the owner's and
     * are left exactly as they were.
     *
     * @return whether an entry was dropped
     */
    public boolean unpublishExternalImage(int glTextureId) {
        Integer id = Integer.valueOf(glTextureId);
        VulkanGlTexture existing = this.externalImages.remove(id);
        if (existing == null) {
            return false;
        }
        this.externalUnpublished++;
        this.fullyInitialized.remove(id);
        retireExternal(existing, "GL texture " + glTextureId + " was unpublished");
        return true;
    }

    /**
     * Retires one external wrapper. Only the sampler it created is released: the image, its memory and its view
     * belong to the caller, and {@link VulkanGlTexture#delete()} knows that for a wrapper built by
     * {@code shareExternal}.
     */
    private void retireExternal(final VulkanGlTexture wrapper, String why) {
        if (this.externalRetireLogs < 8) {
            this.externalRetireLogs++;
            System.out.println("[Celtium] CELTIUM_EXTERNAL_IMAGE dropped: " + why
                    + " (the image is the owner's; only this wrapper's sampler is released)");
        }
        VulkanRetireQueue.retire(new Runnable() {
            @Override
            public void run() {
                wrapper.delete();
            }
        });
    }

    /** {@return how many external images are published right now}. */
    public int externalCount() {
        return this.externalImages.size();
    }

    /**
     * {@return the per-registry image counts plus the two process-wide texture facts}, so a run's report says whether
     * the owner's publish arrived, whether the shared layout handshake is bound to its submissions, and which mip
     * levels the animated patches reached.
     * <p>
     * Instance rather than static on purpose: {@code VulkanTerrainGpuBackend.verificationSummary()} calls this through
     * a registry reference, and the fixture's report is the one place the layout ledger ({@code unbound=0} with a
     * non-zero {@code abandons}, the N05 acceptance) and the per-level patch census ({@code reachUnwritten=0}, the
     * N07 acceptance) can be read. The two facts it adds live in {@link VulkanGlTexture} and are process-wide, but
     * changing this signature to expose them would break the caller for no gain.
     */
    public String externalSummary() {
        return "published=" + this.externalPublished + " republished=" + this.externalRepublishes
                + " unpublished=" + this.externalUnpublished + " supersededMirrors=" + this.externalSupersededMirrors
                + " invalidatesIgnored=" + this.externalInvalidatesIgnored
                + " live=" + this.externalImages.size()
                + " layout[" + VulkanGlTexture.foreignLayoutSummary() + "]"
                + " patchLevels[" + VulkanGlTexture.mirrorLevelSummary() + "]";
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
                if (CeltiumGlStub.isInstalled() && !entry.texture.hasFullContent()) {
                    // Under the stub the only thing that could have filled this image is a GL readback, and a readback
                    // delivers no pixels here: the stub has none to give and copies memory GL never filled. The callers
                    // that reach this method - every one of them after uploadFromGlTexture - would otherwise advertise
                    // a zeroed image as full, which is exactly how the atlas came to report "full" and draw black.
                    // Refusing leaves the mirror content-less, the honest state, and names the reason once.
                    this.readbacksSkippedWithoutContent++;
                    this.rejectPatch("GL-less mirror: refusing to mark GL texture " + glTextureId
                            + " full from a readback that cannot deliver pixels (image is "
                            + entry.width + "x" + entry.height + ")");
                    return;
                }
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
     * <p>
     * Under the GL stub it is also true whenever the mirror holds whole content that came from the game. Everything in
     * that image arrived through an announcement or through an upload the stub captured; a readback can only replace it
     * with what the stub does not have, so copying is never the better answer however old the last application is. The
     * generation comparison alone said "stale" as soon as a consume had caught up - once per announced write - which sent
     * the directly held atlas into a readback that overwrote a correct mirror with zeros.
     */
    public boolean canSkipReadback(int glTextureId) {
        Integer id = Integer.valueOf(glTextureId);
        VulkanGlTexture external = this.externalImages.get(id);
        if (external != null) {
            // Live content, and no readback could improve on it: this image is what its owner is rendering into
            // right now, so a GL readback (which under the stub delivers nothing at all) could only replace it with
            // something older or empty.
            skipDecisionLog(glTextureId, "the image is published by its owner and its content is live", true);
            return true;
        }
        if (!this.fullyInitialized.contains(id)) {
            skipDecisionLog(glTextureId, "not fully initialized", false);
            return false;
        }
        // The image's own fact decides in BOTH configurations, and it is asked first: the set above is bookkeeping and
        // the generation below speaks about announced pixels, but neither says that the image a descriptor samples has
        // ever received its whole content. The block atlas is the case that proves it: it is announced one sprite at a
        // time, so an announced generation exists long before any whole supply, and the directly held mirror was then
        // left with a patch (or with nothing at all) while this method answered "the readback is unnecessary" - the
        // terrain sampled undefined texels and drew them as black. A mirror whose whole content never arrived may not
        // have its supply skipped; once it has, the generation comparison below is the economy it was meant to be.
        Entry entry = this.entries.get(id);
        if (entry == null || !entry.texture.hasFullContent()) {
            skipDecisionLog(glTextureId, "the mirror holds no whole content", false);
            return false;
        }
        if (CeltiumGlStub.isInstalled()) {
            // Without a context the captured surface is the only source, and it is already in the image.
            skipDecisionLog(glTextureId, "stub mirror holds whole content", true);
            return true;
        }
        Integer applied = this.announcedGeneration.get(id);
        if (applied == null) {
            // Whole content is present and nothing was announced: kept as it was. Skipping here would also skip the
            // refresh of a texture the game writes without announcing (a rendered framebuffer), and that is a separate
            // question from the one this method answers.
            skipDecisionLog(glTextureId, "no announced application", false);
            return false;
        }
        Integer consumed = this.consumedGeneration.get(id);
        boolean skip = consumed == null || applied.intValue() > consumed.intValue();
        skipDecisionLog(glTextureId, "applied=" + applied + " consumed=" + consumed, skip);
        return skip;
    }

    /**
     * Names the decision above when {@code -Dceltium.vulkan.mirrorDump} is set.
     * <p>
     * This method is the gate every directly held texture's readback passes: a "true" here with an image whose whole
     * content never arrived is the defect that draws patches over undefined texels, and a "false" here is the defect
     * that re-reads a texture every frame. The two are one boolean apart in the log, so the boolean is logged with the
     * facts it was made from.
     */
    private void skipDecisionLog(int glTextureId, String why, boolean decision) {
        if (MIRROR_DUMP == null || skipDecisionLogs >= 200) {
            return;
        }
        skipDecisionLogs++;
        Entry entry = this.entries.get(Integer.valueOf(glTextureId));
        System.out.println("[Celtium] CELTIUM_SKIP_DECISION glTexture=" + glTextureId + " skip=" + decision
                + " because " + why + " entry=" + (entry == null ? "none"
                        : entry.width + "x" + entry.height + " full=" + entry.texture.hasFullContent())
                + " fullyInitialized=" + this.fullyInitialized.contains(Integer.valueOf(glTextureId)));
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
        if (glTextureId == 8 || glTextureId == 20) {
            // Bounded lifecycle line for the panorama/splash name: which of the three branches is true needs the
            // announce and the forget ordered against each other, not inferred.
            System.out.println("[Celtium] CELTIUM_LIFECYCLE announceImage id=" + glTextureId
                    + " size=" + (image == null ? "null" : image.getWidth() + "x" + image.getHeight())
                    + " hasEntry=" + this.entries.containsKey(Integer.valueOf(glTextureId)));
        }
        if (glTextureId == 0 || image == null) {
            return;
        }
        if (this.externalImages.containsKey(Integer.valueOf(glTextureId))) {
            // The renderer owns this name's image; a CPU-side upload of the same GL texture (the game allocating a
            // framebuffer texture, say) does not describe its content, and storing it would let a later path build a
            // mirror from pixels the image is not made of.
            this.externalInvalidatesIgnored++;
            return;
        }
        this.announcedImages.put(Integer.valueOf(glTextureId), image);
        this.announcementsById.merge(Integer.valueOf(glTextureId), Integer.valueOf(1), Integer::sum);
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

    /** {@return whether the texture's magnification filter is nearest}, asked of the texture itself, not of a binding. */
    private static boolean nearestFilterOf(int glTextureId) {
        return org.lwjgl.opengl.GL45C.glGetTextureParameteri(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER)
                == org.lwjgl.opengl.GL11.GL_NEAREST;
    }

    /**
     * The filter a mirror the registry has to create for an announcement is built with.
     * <p>
     * The texture cannot be asked under the stub - {@code glTexParameteri} is a no-op there, so every texture answers the
     * same value and the real one is gone. The announcement says it instead, by its shape: the writes that cover a whole
     * texture from the origin are the dynamic ones, and the light map - the texture that needs linear - is the one that
     * arrives that way, while a region write is sprite animation into an atlas, which Minecraft samples nearest. Under
     * the bridge the texture's own answer is the real one and is asked exactly as before.
     */
    private static boolean nearestFilterFor(int glTextureId, int x, int y, int width, int height) {
        if (CeltiumGlStub.isInstalled()
                && CeltiumGlStub.textureParameterRecorded(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER)) {
            // The stub now records glTexParameteri, so the texture's own answer is real under the stub too. The
            // record wins over the write-shape inference below: the inference is a guess about which writes are the
            // dynamic ones, and it is kept only for a texture whose MAG_FILTER the game never set.
            return nearestFilterOf(glTextureId);
        }
        if (!CeltiumGlStub.isInstalled()) {
            return nearestFilterOf(glTextureId);
        }
        return !(x == 0 && y == 0 && width > 0 && height > 0);
    }

    /**
     * The upload the GL stub captured for this texture's level 0.
     * <p>
     * Under the stub there is no driver to read a texture back from. What the stub does hold is the uploads the game
     * itself made into that texture - the bytes GL was handed, converted to RGBA8, top row first, with every sub-image
     * write accumulated into the level's surface. That is the same data the mirror would have been given, and it is a
     * read-only view of the stub's own record: it is not freed here and is only read.
     *
     * @param outWidthHeight receives the captured level-0 width and height
     * @return a read-only RGBA8 buffer positioned at 0, or null when the stub captured nothing for this name
     */
    private static java.nio.ByteBuffer capturedRgbaOf(int glTextureId, int[] outWidthHeight) {
        java.nio.ByteBuffer captured = CeltiumGlStub.textureUploadRgba8(glTextureId, 0, outWidthHeight);
        int width = outWidthHeight[0];
        int height = outWidthHeight[1];
        if (captured == null || width <= 0 || height <= 0 || captured.remaining() < width * height * 4) {
            return null;
        }
        return captured;
    }

    /**
     * The per-level surfaces the stub captured for a texture, level 0 first, each at half the extent of the one below.
     * <p>
     * This is what lets a mirror hold the game's own mip chain instead of a chain generated here. Minecraft uploads the
     * atlas level by level ({@code TextureUtil.uploadTextureMipmap}), each level generated per sprite
     * ({@code TextureAtlasSprite.generateMipmaps}), and the stub records every one of those uploads separately - so
     * level 4 can be the per-sprite average GL holds rather than a whole-image blit that averages across sprite
     * borders. The walk stops at the first level with no capture, with an extent that is not {@code base >> level}, or
     * with no game pixels (an allocation that no upload ever wrote): a level the game did not write is not content, and
     * the sampler must not be allowed to reach it.
     *
     * @return the levels found, always at least level 0 when the caller's own level-0 checks passed
     */
    private static java.nio.ByteBuffer[] celtium$capturedLevels(int glTextureId, int baseWidth, int baseHeight) {
        int maxLevels = 1;
        while ((baseWidth >> maxLevels) > 0 && (baseHeight >> maxLevels) > 0) {
            maxLevels++;
        }
        java.nio.ByteBuffer[] found = new java.nio.ByteBuffer[maxLevels];
        int count = 0;
        for (int level = 0; level < maxLevels; level++) {
            int expectedWidth = Math.max(1, baseWidth >> level);
            int expectedHeight = Math.max(1, baseHeight >> level);
            int[] extent = new int[2];
            java.nio.ByteBuffer captured = CeltiumGlStub.textureUploadRgba8(glTextureId, level, extent);
            if (captured == null || extent[0] != expectedWidth || extent[1] != expectedHeight
                    || captured.remaining() < expectedWidth * expectedHeight * 4
                    || !CeltiumGlStub.textureLevelHasGamePixels(glTextureId, level)) {
                break;
            }
            found[level] = captured;
            count++;
        }
        if (count == maxLevels) {
            return found;
        }
        java.nio.ByteBuffer[] truncated = new java.nio.ByteBuffer[Math.max(1, count)];
        System.arraycopy(found, 0, truncated, 0, count);
        return truncated;
    }

    /**
     * Fills a mirror from the upload the stub captured, without reading anything back from GL.
     * <p>
     * This is the source the announcements do not cover. The block atlas is uploaded one sprite at a time and never
     * announced whole, and a patch on its own cannot establish a mirror - the rules below refuse one before the image's
     * whole content arrived - so the captured surface, which is the whole thing as GL received it, is what makes such a
     * texture usable under the stub. Once a mirror holds a whole image it is left alone: re-copying the captured surface
     * on every invalidation would cost a full texture transfer per animated write, and the announcements keep it current.
     * <p>
     * The size comes from the capture itself, checked against every size already established for the texture: a capture
     * whose extent disagrees with a size the registry knows, or with the extent the stub recorded for the level, describes
     * a surface other than the whole one, and filling with it would mark texels that never arrived as content. A dimension
     * of 1 is not a known size - the backend's size query coerces its own 0 answer to 1 - so a placeholder mirror is still
     * established from the capture.
     * <p>
     * <b>A surface with no game pixels is not content, except when the game is announcing the write that fills it.</b>
     * {@code glTexImage2D} with a NULL data pointer lays down zeros so that the sub-image writes that follow have a
     * surface; a draw-time lookup that took those zeros for content is how two 1280x720 colour textures and the light
     * map's first read came to hold {@code opaque=0} over {@code full=true}. But the atlas and the light map are filled
     * through a hook that runs at the <em>head</em> of the upload call, one instruction before the write - the zero
     * surface is then exactly the thing the announced patch is about to land on, and refusing it would cost the atlas
     * its whole chain (measured: every later patch refused as "before initialization"). {@code gameWritesThisLevel}
     * carries which of the two a caller is: true from the announce paths, false from every lookup.
     *
     * @param nearestFilter the filter for a mirror this call has to create; see {@link VulkanGlTexture#create}
     * @param gameWritesThisLevel whether the game has just announced a write into this level, which is evidence that the
     *                            surface is about to hold game pixels even though the captured surface has none yet
     * @return whether the mirror was filled
     */
    private boolean fillFromCapture(int glTextureId, boolean nearestFilter, boolean gameWritesThisLevel) {
        Integer id = Integer.valueOf(glTextureId);
        Entry known = this.entries.get(id);
        if (known != null && known.texture.hasFullContent()) {
            return false;
        }
        int[] extent = new int[2];
        java.nio.ByteBuffer rgba = capturedRgbaOf(glTextureId, extent);
        if (rgba == null) {
            return false;
        }
        if (!gameWritesThisLevel && !CeltiumGlStub.textureLevelHasGamePixels(glTextureId, 0)) {
            // A surface exists but the game never wrote a pixel into it: it is the zero fill an allocation with a NULL
            // data pointer leaves so that later sub-image writes have somewhere to land. Copying it would mark a mirror
            // whole over an image no upload ever filled, and the sampling side would draw those zeros as content. Two
            // frame-sized colour textures and the light map's first read arrived exactly this way. Refused and counted:
            // the mirror keeps whatever it had, and the announcement carrying the real pixels fills it as before.
            this.capturesRefusedWithoutGamePixels++;
            this.rejectPatch("GL-less mirror: the captured surface for GL texture " + glTextureId + " is " + extent[0]
                    + "x" + extent[1] + " with no game pixels (an allocation with a NULL data pointer) and nothing"
                    + " announced a write into it; a mirror is not built from it");
            return false;
        }
        try {
            if (known != null && known.width > 1 && known.height > 1
                    && (known.width != extent[0] || known.height != extent[1])) {
                this.rejectPatch("GL-less mirror: the captured upload for GL texture " + glTextureId + " is "
                        + extent[0] + "x" + extent[1] + " against a mirror of " + known.width + "x" + known.height);
                return false;
            }
            Entry entry = known;
            if (entry == null) {
                // The stub records a level's extent from its allocation even when that allocation carried no pixels -
                // the atlas allocates first and uploads sprite by sprite - so a capture whose extent disagrees with
                // that record is a sub-image that arrived before the surface was known. Taking it would build a mirror
                // the size of one sprite and mark it whole, which is worse than having none. A dimension of 1 is not a
                // record: it is what the backend's own size query coerces a 0 answer to.
                int recordedWidth = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(glTextureId, 0,
                        org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
                int recordedHeight = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(glTextureId, 0,
                        org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
                if (recordedWidth > 1 && recordedHeight > 1
                        && (recordedWidth != extent[0] || recordedHeight != extent[1])) {
                    this.rejectPatch("GL-less mirror: the captured upload for GL texture " + glTextureId + " is "
                            + extent[0] + "x" + extent[1] + " against a recorded size of " + recordedWidth + "x"
                            + recordedHeight + "; a partial capture is not used");
                    return false;
                }
                VulkanGlTexture texture = VulkanGlTexture.create(this.context, extent[0], extent[1], nearestFilter,
                        wrapRepeats(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S),
                        wrapRepeats(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T));
                entry = new Entry(glTextureId, extent[0], extent[1], texture);
                this.entries.put(id, entry);
                this.mirrorCount++;
            }
            // Control: -Dceltium.vulkan.mirrorCapturedLevels=false keeps the pre-N07b behaviour (level 0 only, the
            // chain above it generated here by a whole-image blit) so the per-level content can be measured one flag
            // apart on one jar. The atlas is the texture this matters for; every other captured fill has one level.
            java.nio.ByteBuffer[] levels = MIRROR_CAPTURED_LEVELS
                    ? celtium$capturedLevels(glTextureId, extent[0], extent[1])
                    : new java.nio.ByteBuffer[] {rgba};
            int appliedLevels = entry.texture.uploadCapturedLevels(glTextureId, extent[0], extent[1], levels,
                    levels.length);
            if (appliedLevels == 0) {
                // The content was already in the image, so it needs no second transfer and no second count.
                this.imageUploadsSkipped++;
                return true;
            }
            // The metadata follows the image: a full upload may resize the Vulkan image, and a stale entry size then
            // makes every later patch's bounds and whole-texture check wrong (audit A07).
            this.imageUploads++;
            entry.width = extent[0];
            entry.height = extent[1];
            // The whole level-0 surface arrived from a CPU-side producer, so this is an initialization: the generation a
            // skipped readback reads is bumped and the registry records the image as established, exactly as a
            // whole-texture announcement does.
            recordApplication(glTextureId, true);
            this.staleIds.remove(id);
            this.filledFromCapture++;
            if (MIRROR_REACH_OVERRIDE > 0) {
                entry.texture.forceSamplerReach(MIRROR_REACH_OVERRIDE);
            }
            celtium$noteMirrorSource(glTextureId, "capture", extent[0], extent[1], rgba, appliedLevels);
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "captured upload for GL texture " + glTextureId + ": " + error;
            return false;
        }
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
                boolean wrapS = wrapRepeats(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S);
                boolean wrapT = wrapRepeats(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T);
                texture = VulkanGlTexture.create(this.context, width, height, nearestFilter, wrapS, wrapT);
                entry = new Entry(glTextureId, width, height, texture);
                this.entries.put(glTextureId, entry);
                this.mirrorCount++;
            }
            if (texture.uploadFromNamedImage(glTextureId, width, height, rgba)) {
                // Counted only when the pixels were transferred, and the metadata follows the image: a full upload may resize
                // the Vulkan image, and a stale entry size then makes every later patch bounds check wrong (audit A07).
                this.imageUploads++;
                entry.width = width;
                entry.height = height;
                celtium$noteMirrorSource(glTextureId, "announcement", width, height, rgba);
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
     * <p>
     * A producer that holds the pixels as one flat {@code int[]} - a {@code DynamicTexture}, or
     * {@code TextureUtil.func_110988_a} - passes that array as the single level-0 entry
     * ({@code new int[][] { pixels }}). Splitting it into one {@code int[]} per row instead makes {@code data[0]} a single
     * row, and every texel past it is then written as a zero: that is how the light map's mirror came to hold row 0 and
     * fifteen transparent rows, and why the terrain sampled black.
     *
     * @return the number of texels written
     */
    public static int toRgba(int[][] data, int width, int height, java.nio.ByteBuffer out) {
        return toRgba(data, 0, width, height, out);
    }

    /**
     * Converts one mip level of the animation method's array into RGBA bytes.
     * <p>
     * Same shape as {@link #toRgba(int[][], int, int, java.nio.ByteBuffer)} with the outer index selected: level 0 is
     * the patch being written, level {@code i} is the same patch at its scaled extent. A level the producer did not
     * supply - an index past the array, or a {@code null} entry - converts to zeroes and is refused by the caller
     * before it reaches the image, rather than being written as transparent texels.
     *
     * @param level the mip level to read out of {@code data}
     * @return the number of texels written
     */
    public static int toRgba(int[][] data, int level, int width, int height, java.nio.ByteBuffer out) {
        int[] texels = data != null && level >= 0 && level < data.length && data[level] != null ? data[level]
                : new int[0];
        int written = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int texel = row * width + col;
                int argb = texel < texels.length ? texels[texel] : 0;
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
     * readback, because a patch would need the mirror's previous content and that is what the readback is for - and under
     * the GL stub, where a readback delivers no pixels, the captured surface is what supplies that previous content
     * instead, before the patch lands on it.
     *
     * @return whether the mirror was updated from these pixels
     */
    public boolean announceFullPixels(int glTextureId, int[][] data, int width, int height, int x, int y) {
        if (this.externalImages.containsKey(Integer.valueOf(glTextureId))) {
            // Same rule as announceImage: the pixels of a published image are the ones its owner rendered into it,
            // and an upload the game makes to the same GL name is not that content. Answered as "nothing to do" so
            // no caller falls back to a readback that cannot deliver either.
            this.externalInvalidatesIgnored++;
            return true;
        }
        if (glTextureId == 0 || data == null) {
            this.rejectPatch("no texture id or no data (id=" + glTextureId + ", data=" + (data == null ? "null" : "present") + ")");
            return false;
        }
        Entry entry = this.entries.get(glTextureId);
        if (entry == null && CeltiumGlStub.isInstalled()) {
            // Under the stub nothing can read this texture back, so the pixels the registry already holds establish the
            // mirror before any size is asked of GL: the captured surface carries the pixels and their extent together.
            // This is what makes the block atlas work at all - it is uploaded one sprite at a time and never announced
            // whole, and a patch alone is refused below because the image's whole content never arrived.
            if (fillFromCapture(glTextureId, nearestFilterFor(glTextureId, x, y, width, height), true)) {
                entry = this.entries.get(glTextureId);
            }
        }
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
            if ((textureWidth <= 0 || textureHeight <= 0) && CeltiumGlStub.isInstalled()
                    && x == 0 && y == 0 && width > 0 && height > 0) {
                // A whole-texture write anchored at the origin, which is how a dynamic texture reaches this method - the
                // light map above all. Its dimensions ARE the texture's, so the announcement answers the size question
                // itself; asking GL instead is what refused these writes as "0x0" whenever the stub had recorded no
                // upload for the name (the stub's own texture binding record can miss a bind the state manager skipped).
                textureWidth = width;
                textureHeight = height;
            }
            if (textureWidth <= 0 || textureHeight <= 0) {
                this.failureCount++;
                this.lastFailure = "announced pixels for GL texture " + glTextureId + " whose size is "
                        + textureWidth + "x" + textureHeight;
                if (CeltiumGlStub.isInstalled()) {
                    // Nothing announced a size, nothing captured one, and the stub's record has none: there is nothing
                    // left to build an image from, and the mirror stays absent rather than being guessed at.
                    this.readbacksSkippedWithoutContent++;
                    this.rejectPatch("GL-less mirror: announced " + width + "x" + height + " at " + x + "," + y
                            + " for GL texture " + glTextureId + ", whose size no source answers");
                }
                return false;
            }
            boolean wrapS = wrapRepeats(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S);
            boolean wrapT = wrapRepeats(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T);
            try {
                VulkanGlTexture created = VulkanGlTexture.create(this.context, textureWidth, textureHeight,
                        nearestFilterFor(glTextureId, x, y, width, height), wrapS, wrapT);
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
            // the readback as the one way such a texture gets established - and under the stub, where there is no
            // readback, the captured surface above is the way that image gets established instead.
            this.patchesBeforeInitialization++;
            this.rejectPatch("partial patch " + width + "x" + height + " at " + x + "," + y
                    + " before the image for GL texture " + glTextureId + " was initialized");
            if (CeltiumGlStub.isInstalled()) {
                this.readbacksSkippedWithoutContent++;
            }
            return false;
        }
        if (!wholeTexture && (x < 0 || y < 0 || x + width > entry.width || y + height > entry.height)) {
            this.rejectPatch("region " + width + "x" + height + " at " + x + "," + y + " outside the mirror "
                    + entry.width + "x" + entry.height);
            return false;
        }
        // How many mip levels this announcement carries, using Minecraft's own rule: uploadTextureMipmap walks
        // data[level] and breaks as soon as a scaled extent reaches zero. A null entry stops the walk for the same
        // reason it would stop the GL upload - there is no data for that level and none for any level above it. The
        // walk's result is the producer's chain depth: Minecraft's TextureAtlasSprite generates a chain per animated
        // frame (TextureUtil.generateMipmapData) and uploadTextureMipmap hands every level of it over, so an animated
        // sprite normally announces five levels for a 16x16 sprite - not one.
        int producerDepth = 0;
        for (int level = 0; level < data.length; level++) {
            if ((width >> level) <= 0 || (height >> level) <= 0 || data[level] == null) {
                break;
            }
            producerDepth++;
        }
        if (producerDepth == 0) {
            this.failureCount++;
            this.lastFailure = "announced pixels for GL texture " + glTextureId + " carry no level-0 data";
            this.rejectPatch("announced " + width + "x" + height + " at " + x + "," + y + " for GL texture "
                    + glTextureId + " carry no level-0 data");
            return false;
        }
        // A region patch applies every level the producer sent, each at its own scaled region: that is what keeps the
        // levels the sampler can reach in step with level 0 (audit N07). A whole-texture write keeps the generated
        // chain instead - level 0 is copied and the levels above it are regenerated from it, which is the
        // configuration this project measured as the closest to the GL reference, and leaves no level stale. The
        // patchMipLevels property turns the per-level application off for measurement only; see PATCH_MIP_LEVELS.
        int appliedLevels = wholeTexture || !PATCH_MIP_LEVELS ? 1 : producerDepth;
        java.nio.ByteBuffer[] levelBytes = new java.nio.ByteBuffer[appliedLevels];
        try {
            for (int level = 0; level < appliedLevels; level++) {
                int levelWidth = width >> level;
                int levelHeight = height >> level;
                java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(levelWidth * levelHeight * 4);
                levelBytes[level] = buffer;
                // The array the animation method passes is int[mipLevel][y * width + x], not an array of rows. The first
                // index is the mip level and the second is the flat texel index, so level 0 is the whole patch and the
                // indexing below is one-dimensional. Treating data[row] as a row read the wrong texels for every patch
                // of every animated sprite, and a producer that splits a flat int[] that way loses every row but the
                // first - see toRgba's own note.
                toRgba(data, level, levelWidth, levelHeight, buffer);
                buffer.flip();
            }
            long applicationStart = System.nanoTime();
            if (wholeTexture) {
                entry.texture.uploadFromNamedImage(glTextureId, width, height, levelBytes[0]);
            } else {
                entry.texture.uploadPatchedLevels(width, height, x, y, levelBytes, appliedLevels, glTextureId);
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
                logPatchLevelContract(glTextureId, width, height, x, y, appliedLevels, producerDepth,
                        entry.texture.mipLevels());
            }
            this.imageUploads++;
            this.staleIds.remove(Integer.valueOf(glTextureId));
            recordApplication(glTextureId, wholeTexture);
            if (wholeTexture) {
                celtium$noteMirrorSource(glTextureId, "announced-int[]", width, height, levelBytes[0]);
            }
            if (CeltiumGlStub.isInstalled()) {
                // Which source filled the mirror under the stub: the game's pixels, not a readback.
                this.filledFromAnnouncement++;
            }
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.failureCount++;
            this.lastFailure = "announced pixels for GL texture " + glTextureId + ": " + error;
            this.rejectPatch("conversion or upload failed: " + error);
            return false;
        } finally {
            for (java.nio.ByteBuffer buffer : levelBytes) {
                if (buffer != null) {
                    org.lwjgl.system.MemoryUtil.memFree(buffer);
                }
            }
        }
    }

    /**
     * Names the mip-level contract of one animated patch, once per GL texture (audit N07).
     * <p>
     * The question this answers in a run's log is the one the audit raised: which levels did the announcement update,
     * and what does the sampler therefore read? {@code appliedLevels} is what went into the image,
     * {@code producerDepth} is how deep the producer's own chain went, and {@code mirrorLevels} is how many levels
     * this image has. A level between {@code appliedLevels} and {@code producerDepth} - the case the
     * {@code patchMipLevels=false} control produces - keeps the content the last whole upload or patch put there,
     * which is exactly what the GL texture holds for the same level after the same {@code glTexSubImage2D} call. A
     * level above {@code producerDepth} is outside the sprite's own chain and is not a staleness claim; the census
     * counts it separately and names the sampler's reach.
     */
    private void logPatchLevelContract(int glTextureId, int width, int height, int x, int y, int appliedLevels,
            int producerDepth, int mirrorLevels) {
        Integer id = Integer.valueOf(glTextureId);
        Integer seen = this.patchLevelLogged.get(id);
        int count = seen == null ? 0 : seen.intValue();
        // The first few patches of a texture, not just the first: the census is cumulative, so one sample cannot show
        // the levels catching up across an animation.
        if (count >= 3 || this.patchLevelLogged.size() >= 8 && seen == null) {
            return;
        }
        this.patchLevelLogged.put(id, Integer.valueOf(count + 1));
        System.out.println("[Celtium] CELTIUM_MIRROR_PATCH glTexture=" + glTextureId + " region=" + width + "x" + height
                + " at " + x + "," + y + " producerDepth=" + producerDepth + " appliedLevels=" + appliedLevels
                + " mirrorLevels=" + mirrorLevels + " patch#" + (count + 1)
                + " (each applied level is written at x>>level,width>>level; a level above the producer's chain is"
                + " outside this sprite and is not what the sampler reads at this LOD clamp)"
                + " census[" + VulkanGlTexture.mirrorLevelSummary() + "]");
    }

    /** GL names whose per-level patch contract has been printed, with how many times, so an animation cannot flood the log. */
    private final java.util.Map<Integer, Integer> patchLevelLogged = new java.util.HashMap<Integer, Integer>();

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
     * <p>
     * Under the GL stub, when no announced image exists, the upload the stub captured is applied instead: the block atlas
     * never announces a whole image - it is uploaded one sprite at a time - so the captured surface is the only source
     * that can establish its mirror, and the caller's fallback would be a readback that delivers nothing.
     *
     * @return whether pixels the registry already held were applied
     */
    public boolean applyAnnouncedTo(VulkanGlTexture target, int glTextureId) {
        if (target == null) {
            return false;
        }
        java.awt.image.BufferedImage image = this.announcedImages.get(Integer.valueOf(glTextureId));
        if (image != null) {
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
                if (CeltiumGlStub.isInstalled()) {
                    this.filledFromAnnouncement++;
                }
                return true;
            } catch (RuntimeException | LinkageError error) {
                this.failureCount++;
                this.lastFailure = "announced image for directly held GL texture " + glTextureId + ": " + error;
                return false;
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(rgba);
            }
        }
        if (!CeltiumGlStub.isInstalled()) {
            return false;
        }
        // The capture branch only ever touches the registry's own image: a target that is not this texture's mirror is
        // left to its owner, because filling a foreign image from these pixels would need bookkeeping the entry does not
        // have. The backend holds these textures through imageFor(), so the target is the object the entry created - and
        // because that guard rules out creating a mirror here, the filter argument below is never read.
        Entry entry = this.entries.get(Integer.valueOf(glTextureId));
        if (entry != null && entry.texture == target) {
            if (fillFromCapture(glTextureId, false, false)) {
                return true;
            }
            if (target.hasFullContent()) {
                // This mirror already holds a whole image from an earlier supply, so there is nothing to hand over and
                // nothing was withheld. Answering false here would send the caller into the readback it must not take;
                // the same "the content is already in the image" answer the announced-image branch above gives applies.
                return true;
            }
        }
        // Nothing to hand over at all, and under the stub the caller's fallback readback cannot deliver pixels either.
        // Counted here rather than silently: this is the decision that sends a directly held mirror into that readback.
        this.readbacksSkippedWithoutContent++;
        this.rejectPatch("GL-less mirror: no announced image and no usable captured upload for directly held GL texture "
                + glTextureId + " (entry=" + (entry == null ? "none"
                        : entry.width + "x" + entry.height + " full=" + entry.texture.hasFullContent()
                                + (entry.texture == target ? "" : " other-image"))
                + ")");
        return false;
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

    /** {@return under the GL stub, how many times announced pixels were moved into a mirror} */
    public int filledFromAnnouncement() {
        return this.filledFromAnnouncement;
    }

    /** {@return under the GL stub, how many times a captured upload was moved into a mirror} */
    public int filledFromCapture() {
        return this.filledFromCapture;
    }

    /** {@return under the GL stub, readbacks declined because no announced pixels and no capture existed} */
    public int readbacksSkippedWithoutContent() {
        return this.readbacksSkippedWithoutContent;
    }

    /**
     * Names the source a mirror's content came from, and what that source's pixels look like, once per name and
     * source.
     * <p>
     * The question it answers is the one the GUI defect needed and no counter could: which of the sources filled the
     * mirror for this GL name, at what size, and whether the pixels that went in were opaque, transparent, or absent.
     * {@code droppedAnnouncements} is the other half: an announcement for a name that a {@code forgetTexture} removed
     * before any mirror consumed it is pixels the game handed over and this registry threw away, and the texture that
     * loses them is left to the captured upload - or to nothing.
     * <p>
     * The pixel walk runs only when a line is actually due, so a per-frame animation cannot pay for the census.
     *
     * @param rgba the pixels about to be written, or {@code null} when this source has none to summarise
     */
    private void celtium$noteMirrorSource(int glTextureId, String source, int width, int height,
            java.nio.ByteBuffer rgba) {
        celtium$noteMirrorSource(glTextureId, source, width, height, rgba, -1);
    }

    /**
     * As the four-argument form, with the number of mip levels the fill applied.
     * <p>
     * {@code levels} is the fact the near-horizon band turned on: a mirror filled from the game's own per-level
     * surfaces carries the chain GL carries, while a mirror filled from a single surface has levels above 0 generated
     * here. {@code -1} means the caller has no level count to give (a patch, a readback, an announced image).
     */
    private void celtium$noteMirrorSource(int glTextureId, String source, int width, int height,
            java.nio.ByteBuffer rgba, int levels) {
        this.mirrorFillsBySource.merge(source, Integer.valueOf(1), Integer::sum);
        Integer id = Integer.valueOf(glTextureId);
        String previous = this.mirrorSourceById.put(id, source);
        if (source.equals(previous) || this.mirrorSourceLogs >= MIRROR_SOURCE_LOG_LIMIT) {
            return;
        }
        this.mirrorSourceLogs++;
        Entry filled = this.entries.get(id);
        // The mirror's own identity and content state are on the line because they are what the other two instruments
        // are keyed by: the mirror dump names its PNGs by the object's identity hash, and "full=" is the image's own
        // fact rather than the registry's bookkeeping - the pair that tells "this image holds whole content" from
        // "the set says initialized and the image never received anything". Without them a
        // mirror-gl-1-256x256-*.png could not be tied to the GL name whose fill put those pixels there.
        System.out.println("[Celtium] CELTIUM_MIRROR_SOURCE id=" + glTextureId + " source=" + source
                + " size=" + width + "x" + height + " content=" + contentOf(rgba, width, height)
                + " announced=" + countFor(this.announcementsById, id)
                + " droppedAnnouncements=" + countFor(this.droppedAnnouncementsById, id)
                + " hasEntry=" + this.entries.containsKey(id) + " stale=" + this.staleIds.contains(id)
                + (levels > 0 ? " levels=" + levels + " samplerMaxLod=" + filled.texture.samplerMaxLod() : "")
                + (filled == null ? "" : " obj=" + Integer.toHexString(System.identityHashCode(filled.texture))
                        + " full=" + filled.texture.hasFullContent())
                + " glLess=" + CeltiumGlStub.isInstalled());
    }

    private static int countFor(java.util.Map<Integer, Integer> counts, Integer id) {
        Integer value = counts.get(id);
        return value == null ? 0 : value.intValue();
    }

    /**
     * {@return a one-line summary of the source buffer's level 0} - how many of its texels are opaque, out of how
     * many the size declares, and the first opaque texel's ARGB.
     * <p>
     * A mirror with the right size and no pixels and a mirror with the right size and transparent pixels are the same
     * thing in a frame and different findings in the log. The first opaque texel is printed because a GUI sheet is
     * mostly transparent and its content is where the panel is - "opaque=60000" and "opaque=1" are different textures.
     */
    private static String contentOf(java.nio.ByteBuffer rgba, int width, int height) {
        if (rgba == null || width <= 0 || height <= 0) {
            return "not-carried";
        }
        int base = rgba.position();
        long declared = (long) width * (long) height;
        long available = rgba.remaining() / 4L;
        if (available <= 0) {
            return "empty-buffer";
        }
        long texels = Math.min(declared, available);
        long opaque = 0;
        int first = 0;
        boolean seen = false;
        for (long texel = 0; texel < texels; texel++) {
            int at = base + (int) texel * 4;
            int alpha = rgba.get(at + 3) & 0xFF;
            if (alpha == 0) {
                continue;
            }
            opaque++;
            if (!seen) {
                seen = true;
                first = (alpha << 24) | ((rgba.get(at) & 0xFF) << 16) | ((rgba.get(at + 1) & 0xFF) << 8)
                        | (rgba.get(at + 2) & 0xFF);
            }
        }
        return "opaque=" + opaque + "/" + declared + (seen ? " firstOpaque=0x" + Integer.toHexString(first) : "");
    }

    /** {@return the fills by source and the announcement drops, for the texture summary line}. */
    public String mirrorSourceSummary() {
        StringBuilder sources = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> entry : this.mirrorFillsBySource.entrySet()) {
            if (sources.length() > 0) {
                sources.append(',');
            }
            sources.append(entry.getKey()).append('=').append(entry.getValue());
        }
        int dropped = 0;
        for (Integer count : this.droppedAnnouncementsById.values()) {
            dropped += count.intValue();
        }
        return (sources.length() == 0 ? "none" : sources.toString()) + " announcedDropped=" + dropped;
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
        if (glTextureId == 8 || glTextureId == 20) {
            System.out.println("[Celtium] CELTIUM_LIFECYCLE forgetTexture id=" + glTextureId
                    + " hadEntry=" + this.entries.containsKey(Integer.valueOf(glTextureId))
                    + " announcedUnconsumed=" + announcementUnconsumed(Integer.valueOf(glTextureId)));
        }
        Integer id = Integer.valueOf(glTextureId);
        // A published image is dropped, never destroyed: the registry does not own it, and its owner (the renderer's
        // off-target pair, or whatever else published it) is the only thing that may free it. The GL name is going
        // away, so the wrapper goes with it - but only the wrapper's sampler is released.
        VulkanGlTexture external = this.externalImages.remove(id);
        if (external != null) {
            this.externalUnpublished++;
            retireExternal(external, "GL texture " + glTextureId + " was forgotten by its owner");
        }
        Entry entry = this.entries.remove(id);
        this.fullyInitialized.remove(id);
        if (CeltiumGlStub.isInstalled() && announcementUnconsumed(id)) {
            // The GL-less case, and the reason this is not the plain removal below: the game's own upload utility
            // deletes the id inside the very call that announced its image
            // ({@code TextureUtil.uploadTextureImageAllocate} -> {@code allocateTexture} -> {@code deleteTexture}),
            // so the announcement arrives, this method drops it microseconds later, and the mirror is then built from
            // whatever the stub captured - or from nothing when the stub could not convert that upload. An
            // announcement no mirror has consumed is the game's own pixels for this name and the newest statement
            // about its content, so it survives the delete-and-reallocate; a consumed one is dropped as before,
            // which is what keeps a name that was deleted and reused for something else from sampling old pixels.
            // Bounded to the stub on purpose: with a GL context the readback below is the better source and the
            // bridge leg's behaviour is left exactly as it was.
            this.retainedAnnouncements++;
        } else {
            this.announcedGeneration.remove(id);
            this.consumedGeneration.remove(id);
            if (this.announcedImages.remove(id) != null) {
                // A pending announcement dropped with the mirror. Counted per name because the GUI defect turns on it:
                // an announcement that has not been turned into a mirror yet is pixels the game handed over and this
                // method threw away - and the texture is then left to the captured upload, or to nothing.
                this.droppedAnnouncementsById.merge(id, Integer.valueOf(1), Integer::sum);
            }
        }
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

    /**
     * {@return whether this name has an announced image that no mirror has been built from yet}.
     * <p>
     * The generation comparison is what makes the preservation rule safe: an announcement a lookup already consumed
     * describes content the image this method is about to drop was built from, so it goes with the mirror. Only an
     * announcement that arrived and was never used is the newer statement, and that is exactly the one the game's
     * delete-and-reallocate sequence would otherwise destroy.
     */
    private boolean announcementUnconsumed(Integer id) {
        Integer announced = this.announcedGeneration.get(id);
        if (announced == null || !this.announcedImages.containsKey(id)) {
            return false;
        }
        Integer consumed = this.consumedGeneration.get(id);
        return consumed == null || announced.intValue() > consumed.intValue();
    }

    /** {@return announcements an image-less delete kept alive, for the census and the summary line} */
    public int retainedAnnouncements() {
        return this.retainedAnnouncements;
    }

    /** {@return whether a mirror already exists for this id at exactly this size} */    public boolean hasEntry(int glTextureId, int width, int height) {
        VulkanGlTexture external = this.externalImages.get(Integer.valueOf(glTextureId));
        if (external != null) {
            // "There is an image for this name at this size" is the question the callers ask, and a published image
            // answers it exactly like an entry: the size it was published with is the size it has.
            return external.width() == width && external.height() == height;
        }
        Entry entry = this.entries.get(Integer.valueOf(glTextureId));
        return entry != null && entry.width == width && entry.height == height;
    }

    /** {@return how many GL textures were forgotten after deletion}. */
    public int forgotten() {
        return this.forgotten;
    }

    /** {@return the CPU application cost, as count/bytes/millis for patches and full images}. */
    public String patchCostSummary() {
        String costs = "patches=" + this.patchCount + "/" + this.patchBytes + "B/" + (this.patchNanos / 1000000L)
                + "ms full=" + this.fullImageCount + "/" + this.fullImageBytes + "B/" + (this.fullImageNanos / 1000000L)
                + "ms beforeInit=" + this.patchesBeforeInitialization + " forgotten=" + this.forgotten;
        if (!CeltiumGlStub.isInstalled()) {
            // The bridge's line stays exactly what it was: the split below only has a meaning where no readback exists.
            return costs;
        }
        // The GL-less split: which source filled a mirror when no readback could, how often a mirror was left without
        // content because neither was there, and how often a captured surface was refused because the level held no
        // game pixels at all (an allocation with a NULL data pointer - the stub's own answer, not a pixel guess).
        return costs + " glLess[fromAnnouncement=" + this.filledFromAnnouncement + " fromCapture=" + this.filledFromCapture
                + " noContent=" + this.readbacksSkippedWithoutContent
                + " noGamePixels=" + this.capturesRefusedWithoutGamePixels
                + " wrapDefaults=" + wrapDefaultsUsed + "]";
    }

    /** {@return how many 1x1 mirrors the last invalidation left alone}. */


    public VulkanGlTexture get(int glTextureId, boolean nearestFilter) {
        if (glTextureId == 0) {
            return null;
        }
        // Published images are resolved before every other source: their content is live (the renderer is writing
        // that image right now), so no announcement and no captured upload could describe it, and a mirror built
        // from either would be a second image under a name that already stands for the frame's own.
        VulkanGlTexture external = this.externalImages.get(Integer.valueOf(glTextureId));
        if (external != null) {
            HIT_CALLS++;
            return external;
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
                    return converted;
                }
                // The announcement could not become an image - an unreadable size, a buffer that would not fit, a
                // device failure - and returning null here would refuse every batch that samples this name while the
                // captured upload below holds the same pixels. The capture is asked instead, which is the other source
                // this registry documents; the announced image is left in place for the next lookup.
            } finally {
                ANNOUNCED_NANOS += System.nanoTime() - announcedStart;
                ANNOUNCED_CALLS++;
            }
        }
        Entry existing = this.entries.get(glTextureId);
        if (existing != null) {
            // Once per mirror: the sampler is built when the mirror is created, and a texture's wrap parameters are
            // set by the game *after* the upload that created it (SimpleTexture: image first, then setTextureClamped),
            // so a sampler built at creation time can hold the default. Re-consulting the record here - the first
            // lookup, which is a draw, after the upload completed - is what makes the mode the game's rather than
            // whichever default was in force a moment earlier.
            existing.texture.confirmSamplerStateFromRecord(glTextureId);
            if (this.staleIds.remove(Integer.valueOf(glTextureId))) {
                if (canSkipReadback(glTextureId)) {
                    // The write that made this texture stale announced its pixels, and they are already in this image:
                    // the refresh the direct path skips must be skipped here too, or registry-held textures (anything
                    // that is not the directly held atlas or light map) still re-read GL on every animated write.
                    consumeReadback(glTextureId);
                    HIT_CALLS++;
                    return existing.texture;
                }
                if (CeltiumGlStub.isInstalled()) {
                    // No readback can refresh this mirror under the stub, so the pixels the game handed over are the
                    // only source - and there is no announced image left here, because the block above returns for one.
                    // The captured upload is what remains; when that is gone too the mirror keeps the content it already
                    // has, which is nothing when it never received any, and the sampling side refuses that rather than
                    // drawing texels no upload ever filled.
                    if (fillFromCapture(glTextureId, nearestFilter, false)) {
                        HIT_CALLS++;
                        return existing.texture;
                    }
                    this.readbacksSkippedWithoutContent++;
                    this.rejectPatch("GL-less mirror: stale GL texture " + glTextureId + " has no announced pixels and"
                            + " no usable captured upload; the mirror keeps its " + existing.width + "x" + existing.height
                            + " content (full=" + existing.texture.hasFullContent() + ")");
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
                    celtium$noteMirrorSource(glTextureId, "stale-readback", existing.width, existing.height, null);
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
        if (CeltiumGlStub.isInstalled()) {
            // Under the stub the readback below would copy memory GL never filled: the pixels of this process exist in
            // the game's announcements - which the block above has already handled, so there is none here - and in the
            // uploads the stub captured. The capture is asked first, and it carries the whole surface.
            if (fillFromCapture(glTextureId, nearestFilter, false)) {
                HIT_CALLS++;
                return this.entries.get(Integer.valueOf(glTextureId)).texture;
            }
            // Nothing announced, nothing captured, and no readback either. The mirror is still created at the size the
            // stub recorded - when even that is unknown there is no image to build, and no size may be invented - and it
            // is left content-less: the sampling side refuses a mirror whose whole content never arrived instead of
            // drawing texels nothing ever wrote.
            this.readbacksSkippedWithoutContent++;
            // Asked by name, not of whatever happens to be bound: the stub records an extent per texture, and the
            // binding the state manager believes in can be one bind ahead of the one the stub saw.
            int width = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(glTextureId, 0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
            int height = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(glTextureId, 0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0) {
                this.failureCount++;
                this.lastFailure = "GL texture " + glTextureId + " has no pixels and no recorded size under the GL stub";
                this.rejectPatch("GL-less mirror: no announced pixels, no captured upload and no recorded size for GL"
                        + " texture " + glTextureId + "; no mirror can be created");
                return null;
            }
            VulkanGlTexture empty = VulkanGlTexture.create(this.context, width, height, nearestFilter);
            this.entries.put(glTextureId, new Entry(glTextureId, width, height, empty));
            this.mirrorCount++;
            celtium$noteMirrorSource(glTextureId, "none", width, height, null);
            this.rejectPatch("GL-less mirror: GL texture " + glTextureId + " has no announced pixels and no captured"
                    + " upload; mirror created content-less at " + width + "x" + height);
            HIT_CALLS++;
            return empty;
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
            celtium$noteMirrorSource(glTextureId, "readback", width, height, null);
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
        if (this.externalImages.containsKey(Integer.valueOf(glTextureId))) {
            // A published image is left alone: its content is what its owner is rendering, there is no stored state
            // a re-read could supply, and dropping the entry would take the name away from the batch that samples it
            // (which is exactly the batched glow). Counted rather than silently ignored.
            this.externalInvalidatesIgnored++;
            return;
        }
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
        // Published images are dropped the same way forgetTexture drops one: the wrapper's sampler is released and
        // the image, its memory and its view are left to their owner. Skipping them would leak a sampler per
        // published name; deleting them like entries would destroy an image this class never created.
        for (VulkanGlTexture external : this.externalImages.values()) {
            external.delete();
        }
        this.externalImages.clear();
    }

    /**
     * {@return whether the named GL texture wraps by repeating on this axis}.
     * <p>
     * Read from the texture object itself rather than the currently bound unit: an announcement names its texture, so
     * a bind-side query would read whatever happened to be bound. On any failure the safe answer is clamp, which is
     * what the mirror used before wrap modes were carried over at all.
     */
    private static boolean wrapRepeats(int glTextureId, int pname) {
        try {
            if (CeltiumGlStub.isInstalled() && !CeltiumGlStub.textureParameterRecorded(glTextureId, pname)) {
                // Nothing was recorded for this pname, so the answer is GL's context default - which for WRAP_S/T is
                // GL_REPEAT (GlStateManager.Profile.DEFAULT re-asserts it), not the CLAMP the stub used to hardcode.
                // Counted so a texture that relied on the default is distinguishable from one set to REPEAT.
                wrapDefaultsUsed++;
            }
            return org.lwjgl.opengl.GL45C.glGetTextureParameteri(glTextureId, pname)
                    == org.lwjgl.opengl.GL11.GL_REPEAT;
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }
}
