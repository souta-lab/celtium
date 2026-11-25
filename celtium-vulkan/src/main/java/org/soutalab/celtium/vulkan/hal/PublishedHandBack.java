package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.vulkan.VkCommandBuffer;
import org.soutalab.celtium.vulkan.GllessFrameTarget;
import org.soutalab.celtium.vulkan.VulkanGlTexture;
import org.soutalab.celtium.vulkan.VulkanTextureRegistry;

/**
 * The published-frame-texture hand-back: the walk that moves every published name's image to the
 * layout its next use needs, the stale-name prune that keeps a recreated pair from being sampled
 * through a dead wrapper, and the pure decision rules both are built from.
 * <p>
 * Moved out of {@code PostChainBridge} (arch-graph-r7, PostChain deletion prerequisite (3)): the
 * hand-back is not chain work - the off-target windows, the terrain prepare and the chain run all
 * call it - so it belongs with the frame-target machinery in this package, behind the same
 * injected-{@link Host} shape {@link FrameTargetPair} uses. The publication census itself stays
 * in {@code PublishedFrameTextures} on the backend side; the host surface below is exactly the
 * slice of backend state this walk reads and writes, nothing wider.
 */
public final class PublishedHandBack {

    /** The backend state the hand-back walk operates on, supplied by the bridge that owns it. */
    public interface Host {
        int publishedCount();
        int publishedKeyAt(int index);
        VulkanGlTexture publishedValueAt(int index);
        long publishedVersion();
        boolean publishedIsEmpty();
        void noteSkippedForAttach();
        long skippedForAttach();
        void setPublishedValueAt(int index, VulkanGlTexture value);
        void notePublishedRepublished();
        void notePublishedUnpublished();
        void notePublishedStalePruned();
        long publishedStalePruned();
        void removePublishedAt(int index);
        String publishedLayoutsSummary();
        long publishedImage(int name);
        void putPublishedImage(int name, long image);
        void removePublishedImage(int name);
        long publishedImageIdentity(int name);
        void removePublishedImageIdentity(int name);
        int publishedImageSlot(int name);
        void removePublishedImageSlot(int name);
        long publishedDepthKey(int name);
        void removePublishedDepthKey(int name);
        GllessFrameTarget offTargetTarget();
        long frameTargetColorImage();
        VkCommandBuffer commandBuffer();
        long frameId();
        long passAttachedColorImage();
        long passAttachedDepthImage();
        boolean passAttachesImage(long image);
        void record(String line);
        int pairSkipLogs();
        void notePairSkipLog();
        long pairNameSamplingDraws();
        long pairNameSamplingDrawsFrame();
        long currentIdentity();
        VulkanTextureRegistry registry();
        int targetWidth();
        int targetHeight();
        int pairLayoutLogs();
        int pairLayoutLines();
        void notePairLayoutLog();
        String lastPairLayoutLine();
        void setLastPairLayoutLine(String line);
    }

    private final Host host;

    public PublishedHandBack(Host host) {
        this.host = host;
    }

    /**
     * Name-to-image resolution cached for the transition loop, rebuilt only when the publication set or a chain pair
     * changes. The loop runs once per window (about fifty a frame on a world scene), and it used to prune and rebuild
     * its map views on every one of them.
     */
    private long publishedCacheVersion = Long.MIN_VALUE;
    private long publishedCachePairs = Long.MIN_VALUE;

    /**
     * Item 673's lab switch: {@code -Dceltium.vulkan.terrainPreparePublishWalkOnce=true} lets a sampling-side
     * walk be skipped when it is provably a repeat of the walk that already ran for this frame.
     * <p>
     * The terrain prepare runs the sampling walk once per terrain pass (three or more passes a frame), and
     * each pass re-walks every published name only to find every image already in the layout the walk wants
     * (the settled fast path in {@code VulkanGlTexture}). The repeat is redundant exactly while nothing has
     * moved since the walk that settled them: the key below therefore carries the frame, the publication
     * version, the pair generation, the attach context (the images the pass attaches, which the walk skips)
     * and {@link VulkanGlTexture#foreignLayoutTransitionCount()} - the count of foreign layout barriers
     * actually recorded anywhere, which is what a hand-back between the two walks would advance. An
     * attach-side (general) walk always invalidates the memo, and a walk over an explicit attach-image array
     * (the multi-target window shape) neither uses nor fills it. Off by default: with the switch off the
     * walk runs exactly as before, every pass, every name.
     */
    private static final boolean PUBLISH_WALK_ONCE =
            Boolean.getBoolean("celtium.vulkan.terrainPreparePublishWalkOnce");
    private long samplingWalkFrame = Long.MIN_VALUE;
    private long samplingWalkPublications = Long.MIN_VALUE;
    private long samplingWalkPairs = Long.MIN_VALUE;
    private long samplingWalkTransitions = Long.MIN_VALUE;
    private long samplingWalkAttachColor;
    private long samplingWalkAttachDepth;
    private long samplingWalkPassColor;
    private long samplingWalkPassDepth;
    /** How many sampling walks the memo answered instead of walking, with the switch on. */
    private long samplingWalkSkips;

    /** {@return how many sampling-side walks the item-673 memo skipped}, for a leg's census line. */
    public long celtium$samplingWalkSkips() {
        return this.samplingWalkSkips;
    }

    /**
     * {@return whether a sampling-side walk with this key is a repeat of the walk the memo holds}.
     * Pure, so the key's shape is pinned without a device: every component must match, and the switch,
     * the attach-side direction and the array-attach shape each refuse on their own.
     */
    public static boolean celtium$samplingWalkRedundant(boolean enabled, boolean general, boolean hasAttachImages,
            long frame, long memoFrame, long publications, long memoPublications,
            long pairs, long memoPairs, long transitions, long memoTransitions,
            long attachColor, long memoAttachColor, long attachDepth, long memoAttachDepth,
            long passColor, long memoPassColor, long passDepth, long memoPassDepth) {
        return enabled && !general && !hasAttachImages
                && memoFrame != Long.MIN_VALUE
                && frame == memoFrame && publications == memoPublications && pairs == memoPairs
                && transitions == memoTransitions
                && attachColor == memoAttachColor && attachDepth == memoAttachDepth
                && passColor == memoPassColor && passDepth == memoPassDepth;
    }
    private int[] cachedNames = new int[0];
    private VulkanGlTexture[] cachedWrappers = new VulkanGlTexture[0];
    private long[] cachedImages = new long[0];
    /**
     * The chain-pair identity each cached name's image stands for, resolved once per cache rebuild.
     * <p>
     * The transition loop used to ask {@code chainPairIdentityForImage} for every name on every window - a hash lookup
     * per name per window, and the loop runs once per window (35.8 a frame on the measured world scene). The answer
     * only changes when the pair generation changes, which is already part of the cache key, so it is resolved here
     * with the rest of the cache instead of 35.8 times a frame.
     */
    private long[] cachedPairIdentities = new long[0];

    /**
     * The chain-pair colour slot each cached name's image fills, resolved with the rest of the cache.
     * <p>
     * The walk used to re-resolve this per name per pass through
     * {@code chainPairColorSlotForImage} - a pair lookup plus a slot scan - although the answer only
     * changes when the pair generation changes, which is already part of the cache key (the same
     * argument as {@link #cachedPairIdentities}). -1 for a name that fills no slot.
     */
    private int[] cachedPairSlots = new int[0];

    /**
     * The shared depth store key each cached name stands for, 0 for a colour name, resolved with the rest of the
     * cache: the walk reports a depth wrapper's layout moves into the store under this key, and resolving it per
     * window would be the same per-name lookup the pair-identity cache exists to avoid.
     */
    private long[] cachedDepthKeys = new long[0];

    /**
     * Names the attach-side hand-back skipped because they stand for an image this pass does not attach.
     * <p>
     * The narrowing's own measurement: the walk is proportional to the published-name count and runs once per
     * off-target window, so the count of names it no longer visits is the size of the change.
     */
    private long attachNarrowedSkips;

    /** {@return the attach-side hand-back's skipped names}, for the window split's line. */
    public long celtium$attachNarrowedSkips() {
        return this.attachNarrowedSkips;
    }

    /**
     * Whether the published-name walk has to visit one name.
     * <p>
     * The sampling side always does: it is the direction that has to cover every name a draw in the pass might sample,
     * and that set is not known at the walk. The attach side only has to visit the names whose image the pass is about
     * to attach - the hand-back exists to satisfy {@code VUID-vkCmdBeginRenderPass-initialLayout-00900} for that one
     * image - so a name over another image is skipped. {@code attachImage == 0} means the caller did not name an
     * image, and the walk keeps its old, unconditional behaviour.
     * <p>
     * Static and package-private so the rule is testable without a device: the walk itself needs a GPU, but "which
     * names may be skipped" is arithmetic, and a green test here is not evidence that a transition happened.
     *
     * @param general     {@code true} for the attach-side hand-back
     * @param attachImage the image the pass attaches, or 0 when the caller did not narrow
     * @param publishedImage the image the published name stands for, 0 when it has none
     */
    public static boolean celtium$attachHandBackNeeded(boolean general, long attachImage, long publishedImage) {
        return !general || attachImage == 0L || publishedImage == attachImage;
    }

    /**
     * The same rule for a pass that attaches several images (a multi-target window's whole tuple): the name is
     * visited when any attached image is the one it stands for. A null array is the unnarrowed walk, as
     * {@code attachImage == 0} is above.
     */
    public static boolean celtium$attachHandBackNeeded(boolean general, long[] attachImages, long publishedImage) {
        if (!general || attachImages == null) {
            return true;
        }
        for (long attachImage : attachImages) {
            if (celtium$attachHandBackNeeded(true, attachImage, publishedImage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Zero-allocation attach hand-back predicate covering a pass's color target(s) AND its borrowed shared depth
     * target (shadow §3.4). When {@code general == false} (sampling side) or all attach arguments are unset
     * (unnarrowed walk), returns {@code true}; otherwise returns {@code true} iff {@code publishedImage} matches
     * {@code attachColor}, {@code attachDepth}, or any element of {@code attachColors}.
     */
    public static boolean celtium$attachHandBackNeeded(boolean general, long attachColor, long attachDepth,
            long[] attachColors, long publishedImage) {
        if (!general) {
            return true;
        }
        if (attachColor == 0L && attachDepth == 0L && attachColors == null) {
            return true;
        }
        if (attachColor != 0L && publishedImage == attachColor) {
            return true;
        }
        if (attachDepth != 0L && publishedImage == attachDepth) {
            return true;
        }
        if (attachColors != null) {
            for (long candidate : attachColors) {
                if (candidate != 0L && candidate == publishedImage) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@return the layout the published-name walk moves one name's image to, and the layout it reports to the
     * image's owner afterwards}.
     * <p>
     * One decision for both the transition and the report, so the two cannot disagree: the walk records a barrier
     * to this value and then reports this value (read back as the wrapper's recorded destination). The sampling
     * side ({@code general == false}) is {@code SHADER_READ_ONLY_OPTIMAL} for every published image - the layout
     * the sampling descriptors declare. The attach side differs by kind: a colour image's owner attaches it in
     * {@code GENERAL}, the layout its pass declares, but a depth image's owner attaches it in its own layout
     * ({@code ownerLayout}, normally {@code DEPTH_STENCIL_ATTACHMENT_OPTIMAL}) - {@code GENERAL} is a value the
     * depth owner's records never use, and handing a depth image back there left the owner's store record naming
     * a layout the next draw's descriptor did not declare (item 537 wall (c): the two
     * {@code VUID-vkCmdDraw-None-09600} on the published depth image).
     * <p>
     * Static and package-private so the rule is testable without a device, like
     * {@link #celtium$attachHandBackNeeded}: a green test here pins the decision table, not a recorded barrier.
     *
     * @param depth       {@code true} when the published name stands for a depth image (it carries a depth key)
     * @param general     {@code true} for the attach-side hand-back, {@code false} for the sampling side
     * @param ownerLayout the layout the depth image's owner attaches it in; ignored for colour names and on the
     *                    sampling side
     */
    public static int celtium$publishedHandBackLayout(boolean depth, boolean general, int ownerLayout) {
        if (!general) {
            return org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }
        return depth ? ownerLayout : org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
    }

    private void celtium$rebuildPublishedCache() {
        int size = host.publishedCount();
        GllessFrameTarget pairOwner = host.offTargetTarget();
        this.cachedNames = new int[size];
        this.cachedWrappers = new VulkanGlTexture[size];
        this.cachedImages = new long[size];
        this.cachedPairIdentities = new long[size];
        this.cachedPairSlots = new int[size];
        this.cachedDepthKeys = new long[size];
        for (int i = 0; i < size; i++) {
            int name = host.publishedKeyAt(i);
            VulkanGlTexture wrapper = host.publishedValueAt(i);
            long image = host.publishedImage(name);
            this.cachedNames[i] = name;
            this.cachedWrappers[i] = wrapper;
            this.cachedImages[i] = image;
            this.cachedPairIdentities[i] = pairOwner == null || image == 0L
                    ? Long.MIN_VALUE : pairOwner.chainPairIdentityForImage(image);
            this.cachedPairSlots[i] = pairOwner == null || this.cachedPairIdentities[i] == Long.MIN_VALUE
                    ? -1 : pairOwner.chainPairColorSlotForImage(this.cachedPairIdentities[i], image);
            this.cachedDepthKeys[i] = host.publishedDepthKey(name);
        }
    }

    /**
     * Moves every published frame texture to the layout its next use needs.
     * <p>
     * Two uses alternate on the same image: the pass that draws the game's framebuffer into the pair attaches it, which
     * declares GENERAL, and the pass that samples it through the published name has a descriptor declaring
     * SHADER_READ_ONLY_OPTIMAL. {@code general} picks which one is next; the sampling side is the transition that was
     * missing, and its absence is the VUID-vkCmdDraw-None-09600 the vsync leg reported at vkQueueSubmit.
     */
    public void celtium$transitionPublishedFrameTextures(boolean general) {
        celtium$transitionPublishedFrameTextures(general, 0L);
    }

    /**
     * Records a layout move in the pair that owns the image, at the slot the cache resolved for it:
     * slot 0's record is the pair's own, a tuple slot's record is what that slot's acquire barrier
     * reads as its source layout.
     *
     * @return whether the pair's record changed - a settled name (the record already names this
     *         layout) writes nothing, and its caller skips the layout report for it too
     */
    private boolean notePairColorLayout(GllessFrameTarget pairOwner, long identity, int slot, int layout) {
        return pairOwner.noteChainPairColorLayoutAtSlotIfChanged(identity, slot, layout);
    }

    /**
     * The same walk, narrowed on the attach side to the names that stand for the image the pass is about to attach.
     * <p>
     * The hand-back exists for one reason: the pass declares {@code GENERAL} for attachment 0, so every published name
     * whose image that pass attaches has to be moved back before {@code vkCmdBeginRenderPass}
     * ({@code VUID-vkCmdBeginRenderPass-initialLayout-00900}). A name published over a <em>different</em> image is not
     * attached by this pass and is not moved by it, so handing it back is a transition nothing asked for - and the walk
     * that records it runs once per off-target window, over every published name (38 at the end of the measured run).
     * <p>
     * The sampling side ({@code general == false}) is unchanged: it is the direction that has to cover every name a
     * draw in the pass might sample, and that set is not known here.
     *
     * @param general     {@code true} for the attach-side hand-back, {@code false} for the sampling-side transition
     * @param attachImage the image the pass is about to attach, or 0 to hand back every published name as before
     */
    public void celtium$transitionPublishedFrameTextures(boolean general, long attachImage) {
        celtium$transitionPublishedFrameTextures(general, attachImage, 0L, null);
    }

    /** Zero-allocation overload for a single-color pass that may also attach a borrowed shared depth image. */
    public void celtium$transitionPublishedFrameTextures(boolean general, long attachColor, long attachDepth) {
        celtium$transitionPublishedFrameTextures(general, attachColor, attachDepth, null);
    }

    /**
     * The same walk for a pass that attaches several images at once (a multi-target window's tuple): the
     * hand-back covers every attached slot's names, and the sampling side skips every attached slot's names.
     *
     * @param attachImages the images the pass is about to attach, or null to hand back every published name
     */
    public void celtium$transitionPublishedFrameTextures(boolean general, long[] attachImages) {
        celtium$transitionPublishedFrameTextures(general, 0L, 0L, attachImages);
    }

    public void celtium$transitionPublishedFrameTextures(boolean general, long attachColor, long attachDepth,
            long[] attachImages) {
        if (host.publishedIsEmpty() || host.commandBuffer() == null) {
            return;
        }
        long publications = host.publishedVersion();
        GllessFrameTarget cachePairsOwner = host.offTargetTarget();
        long pairs = cachePairsOwner == null ? -1L : cachePairsOwner.chainPairGenerationTotal();
        if (general) {
            // A hand-back moves images toward the attach layouts: whatever the sampling memo settled no
            // longer holds, whatever this walk itself does.
            this.samplingWalkFrame = Long.MIN_VALUE;
        } else if (celtium$samplingWalkRedundant(PUBLISH_WALK_ONCE, false, attachImages != null,
                host.frameId(), this.samplingWalkFrame, publications, this.samplingWalkPublications,
                pairs, this.samplingWalkPairs,
                VulkanGlTexture.foreignLayoutTransitionCount(), this.samplingWalkTransitions,
                attachColor, this.samplingWalkAttachColor, attachDepth, this.samplingWalkAttachDepth,
                host.passAttachedColorImage(), this.samplingWalkPassColor,
                host.passAttachedDepthImage(), this.samplingWalkPassDepth)) {
            this.samplingWalkSkips++;
            return;
        }
        if (publications != this.publishedCacheVersion || pairs != this.publishedCachePairs) {
            // The prune re-publishes a name whose pair image was recreated; it is the reason a stale resolution cannot
            // simply be cached forever, and the generation is part of the key for exactly that case.
            celtium$pruneStalePublishedNames();
            this.publishedCacheVersion = host.publishedVersion();
            this.publishedCachePairs = pairs;
            celtium$rebuildPublishedCache();
            if (host.publishedIsEmpty()) {
                return;
            }
        }
        GllessFrameTarget pairOwner = host.offTargetTarget();
        for (int cachedIndex = 0; cachedIndex < this.cachedNames.length; cachedIndex++) {
            int entryKey = this.cachedNames[cachedIndex];
            VulkanGlTexture entryValue = this.cachedWrappers[cachedIndex];
            long publishedImageHandleValue = this.cachedImages[cachedIndex];
            long publishedImageIdentity = this.cachedPairIdentities[cachedIndex];
            boolean pairImageName = publishedImageIdentity != Long.MIN_VALUE;
            if (!celtium$attachHandBackNeeded(general, attachColor, attachDepth, attachImages,
                    publishedImageHandleValue)) {
                // The hand-back is the attach's own precondition: only the names that stand for the image this pass
                // attaches have to be in GENERAL / DEPTH_STENCIL_ATTACHMENT_OPTIMAL for it. A name over another
                // image is not attached here and is not moved here, so the transition would be one nothing asked for.
                this.attachNarrowedSkips++;
                continue;
            }
            // The sampling side must not move an image (color OR depth) that this pass is about to attach: the pass
            // declares GENERAL for color attachments and DEPTH_STENCIL_ATTACHMENT_OPTIMAL for depth attachments, so
            // transitioning it to SHADER_READ_ONLY_OPTIMAL right before vkCmdBeginRenderPass causes
            // VUID-vkCmdBeginRenderPass-initialLayout-00900.
            if (!general && (host.passAttachedColorImage() != 0L || host.passAttachedDepthImage() != 0L)) {
                if (publishedImageHandleValue != 0L
                        && host.passAttachesImage(publishedImageHandleValue)) {
                    host.noteSkippedForAttach();
                    if (host.skippedForAttach() == 1) {
                        host.record("published name " + entryKey + " stands for the image this pass attaches (0x"
                                + Long.toHexString(host.passAttachedColorImage()) + "); the sampling-side transition"
                                + " would leave it in SHADER_READ_ONLY under a pass that declares GENERAL, so it is"
                                + " skipped and the attach-side hand-back keeps it in GENERAL");
                    }
                    if (host.pairSkipLogs() < 8) {
                        // Whether the refusal leaves a draw without the layout its descriptor declares: the count is
                        // of groups that sampled a published chain-pair name, this frame and cumulatively.
                        host.notePairSkipLog();
                        System.out.println("[Celtium] CELTIUM_PAIR_SKIP frame=" + host.frameId()
                                + " name=" + entryKey + " attached=0x"
                                + Long.toHexString(host.passAttachedColorImage())
                                + " identity=0x" + Long.toHexString(host.currentIdentity())
                                + " samplingDrawsThisRun=" + host.pairNameSamplingDraws()
                                + " samplingDrawsLastFrame=" + host.pairNameSamplingDrawsFrame()
                                + " (0 means nothing sampled this name before the skip)");
                    }
                    continue;
                }
            }
            // A depth publication is decided apart from the colour names (item 537 wall (c)): its owner's
            // vocabulary has no GENERAL in it, so the hand-back target comes from the same decision the report
            // below uses, with the owner's own attachment layout as the depth answer.
            long depthKey = this.cachedDepthKeys[cachedIndex];
            int pairSlot = this.cachedPairSlots[cachedIndex];
            // Whether this name's pair record actually moves below: a settled name writes nothing
            // and earns no layout report (the report's summary cannot have changed through it).
            boolean pairRecordChanged = false;
            if (general) {
                if (depthKey != 0L) {
                    entryValue.ensureHandBackLayout(host.commandBuffer(), celtium$publishedHandBackLayout(true, true,
                            GllessFrameTarget.DEPTH_RENDER_LAYOUT));
                } else {
                    entryValue.ensureGeneralLayout(host.commandBuffer());
                }
            } else if (pairImageName) {
                // The pair is never moved out of the layout its pass declares, so its sampling transition is not
                // served. Counted, so "the transition was skipped" is a measurement; and the pair's own record follows,
                // because the image is still in GENERAL and a record left at SHADER_READ_ONLY would be stale.
                host.noteSkippedForAttach();
                if (pairOwner != null && publishedImageIdentity != Long.MIN_VALUE) {
                    pairRecordChanged = notePairColorLayout(pairOwner, publishedImageIdentity, pairSlot,
                            org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
                }
            } else {
                entryValue.ensureSampledLayout(host.commandBuffer());
            }
            // The pair's own record of the image's layout follows the wrapper's barrier, but only for the names that
            // stand for the pair image on the hand-back side: on the sampling side (!general), pairImageName already
            // skipped the transition and recorded GENERAL above, so writing SHADER_READ_ONLY_OPTIMAL here would
            // overwrite the true GENERAL layout with a stale sampling layout.
            if (general && pairOwner != null && publishedImageIdentity != Long.MIN_VALUE) {
                pairRecordChanged = notePairColorLayout(pairOwner, publishedImageIdentity, pairSlot,
                        celtium$publishedHandBackLayout(false, true, 0));
                if (pairRecordChanged) {
                    celtium$reportPairLayout("hand-back");
                }
            } else if (!general && pairOwner != null && publishedImageIdentity != Long.MIN_VALUE
                    && pairRecordChanged) {
                celtium$reportPairLayout("sampling");
            }
            // A depth publication's layout record is the shared depth store, not a pair, so the wrapper's move is
            // reported into it under the name's key: the owner's next attachment acquire reads the store for its
            // barrier's source layout, and the native depth copy reads it too. Without the report the store would
            // keep naming the attachment layout while the image sits in the read layout the sampling side left it
            // in, and the next acquire would name a source layout the image is not in. This is the report
            // SHADOW-NATIVE-DESIGN section 3.4 requires of the publication.
            // The reported value is the destination the wrapper's recording actually leaves the image in, read
            // back from the wrapper - not a value synthesised from `general`. The two agreed for colour, but for
            // depth the binary synthesis wrote GENERAL on the hand-back side: a layout the depth owner never
            // uses, over an image the hand-back above moved to the owner's attachment layout (item 537 wall (c)).
            if (depthKey != 0L && pairOwner != null) {
                // The destination is the value the transition above was told to reach, on both
                // deterministic routes: the hand-back names DEPTH_RENDER_LAYOUT, the sampling side
                // names the wrapper's sampling layout. Reading it back through foreignLayoutIn -
                // a synchronized lookup over two shared maps - per depth name per pass bought
                // nothing on those routes. The pair-name sampling route is the exception: it
                // records no transition, so the image stays wherever it was, and only the read-back
                // knows that value. The store write itself is skipped when it already names the
                // destination (a settled depth name).
                int depthDestination;
                if (general) {
                    depthDestination = GllessFrameTarget.DEPTH_RENDER_LAYOUT;
                } else if (pairImageName) {
                    depthDestination = entryValue.foreignLayoutIn(host.commandBuffer());
                } else {
                    depthDestination = entryValue.samplingLayout();
                }
                pairOwner.sharedDepths().noteLayoutIfChanged(depthKey, depthDestination);
            }
        }
        if (!general && attachImages == null && PUBLISH_WALK_ONCE) {
            // The memo holds the state this walk just established, with the transition count read after
            // the walk: a name this walk itself had to move advances the count, and the next identical
            // walk must compare against the settled total, not the one from before this walk ran.
            this.samplingWalkFrame = host.frameId();
            this.samplingWalkPublications = publications;
            this.samplingWalkPairs = pairs;
            this.samplingWalkTransitions = VulkanGlTexture.foreignLayoutTransitionCount();
            this.samplingWalkAttachColor = attachColor;
            this.samplingWalkAttachDepth = attachDepth;
            this.samplingWalkPassColor = host.passAttachedColorImage();
            this.samplingWalkPassDepth = host.passAttachedDepthImage();
        }
    }

    /**
     * Prints the chain pair's three layouts when they change: what the pass declares, what the image is in, and what
     * the last sampling barrier left the published wrapper recording.
     * <p>
     * Bounded to {@code -Dceltium.vulkan.pairLayoutLines} (default 12) and printed only on a change, the same shape as
     * the splash flip trace: the question is which of the three moves first in a frame, and a per-frame repetition
     * would hide that. The declared value is the constant the pair's pass is built with
     * ({@code GllessFrameTarget.COLOR_RENDER_LAYOUT}); the actual value is the pair's own record; the wrapper column is
     * the published wrapper's recorded layout, which is what a descriptor written from it carries.
     */
    public void celtium$reportPairLayout(String site) {
        // The budget first: the summary walks every pair and builds a string, and this runs at every pass that samples
        // a published name - with Xaero's Minimap that was 26% of pass recording (JFR, leg xprof-amd) long after the
        // bounded lines were spent.
        if (host.pairLayoutLogs() >= host.pairLayoutLines()) {
            return;
        }
        GllessFrameTarget pairTarget = host.offTargetTarget();
        if (pairTarget == null) {
            return;
        }
        String line = celtium$pairLayoutSummary();
        if ("pair=absent".equals(line)) {
            return;
        }
        if (line.equals(host.lastPairLayoutLine()) || host.pairLayoutLogs() >= host.pairLayoutLines()) {
            return;
        }
        host.setLastPairLayoutLine(line);
        host.notePairLayoutLog();
        System.out.println("[Celtium] CELTIUM_PAIR_LAYOUT frame=" + host.frameId() + " site=" + site + " " + line
                + " attached=0x" + Long.toHexString(host.passAttachedColorImage()));
    }

    /**
     * {@return every chain pair's three layouts as one string}: what the pass declares, what the image is actually
     * in, and what each published wrapper records - the three values {@code VUID-vkCmdDraw-None-09600} compares.
     * With per-identity pairs there is one entry per identity. Used by the change-triggered line and by
     * {@code CELTIUM_VERIFY}, so the state at the end of a run is recorded even when nothing changed late enough to
     * print.
     */
    public String celtium$pairLayoutSummary() {
        GllessFrameTarget pairTarget = host.offTargetTarget();
        if (pairTarget == null) {
            return "pair=absent";
        }
        StringBuilder pairs = new StringBuilder();
        for (Long identity : pairTarget.chainPairIdentities()) {
            if (pairTarget.chainPairColorImage(identity.longValue()) == 0L) {
                continue;
            }
            if (pairs.length() > 0) {
                pairs.append(' ');
            }
            pairs.append("0x").append(Long.toHexString(identity.longValue())).append(":[declared=")
                    .append(celtium$layoutName(pairTarget.chainPairDeclaredLayout()))
                    .append(" actual=").append(celtium$layoutName(pairTarget.chainPairColorLayout(identity.longValue())))
                    .append(']');
        }
        if (pairs.length() == 0) {
            return "pair=absent";
        }
        return "pair=" + pairs + " wrappers=" + host.publishedLayoutsSummary();
    }

    /** {@return a Vulkan layout's name, so a line can be read without a table}. */
    public static String celtium$layoutName(int layout) {
        switch (layout) {
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED: return "UNDEFINED";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL: return "GENERAL";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL: return "SHADER_READ_ONLY";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL: return "TRANSFER_SRC";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL: return "TRANSFER_DST";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL: return "COLOR_ATTACHMENT";
            default: return "layout" + layout;
        }
    }

    /**
     * Drops every published name whose image is no longer the pair's live image.
     * <p>
     * A name is published over the renderer's own image so the game's composite can sample it by the name it binds. That
     * image is destroyed and replaced when the pair is recreated (a size change, the first real frame after the splash),
     * and the wrapper then names freed memory. Recording a barrier on it is not a validation warning: the menu leg
     * measured `vkCmdPipelineBarrier(): pImageMemoryBarriers[0].image Invalid VkImage Object 0x300000000030` and then a
     * <b>SIGSEGV inside libvulkan_radeon</b> — the driver dereferenced the freed handle. The unpublish-on-recreation rule
     * already existed ({@code celtium$syncPublishedChainOutput}), but it only ran on the chain-identity branch, so a
     * frame that samples the name before that branch runs used the stale wrapper. This is the same rule at the point of
     * use, so a recreated pair cannot be sampled through a name that still points at the old image.
     */
    public void celtium$pruneStalePublishedNames() {
        if (host.publishedIsEmpty()) {
            return;
        }
        GllessFrameTarget offTarget = host.offTargetTarget();
        if (offTarget == null) {
            // Only the owned target carries the chain pairs the published names stand for; without one there is
            // nothing here to judge.
            return;
        }
        for (int idx = host.publishedCount() - 1; idx >= 0; idx--) {
            int name = host.publishedKeyAt(idx);
            long image = host.publishedImage(name);
            long owner = host.publishedImageIdentity(name);
            long depthKey = host.publishedDepthKey(name);
            long live;
            if (depthKey != 0L) {
                // A depth publication stands for the shared depth store's image for its key, not a pair's colour
                // image: the store is its liveness record, and a recreated store entry re-points the name the
                // same way a recreated pair re-points a colour name.
                live = offTarget.sharedDepths().image(depthKey);
            } else if (owner != Long.MIN_VALUE) {
                // The name stands for the slot it was published over: a tuple slot's live image is not slot 0's
                // (comparing against slot 0 re-published colortex4/5 and shadowcolor1 over the pair's own image).
                live = offTarget.chainPairColorImageAt(owner, host.publishedImageSlot(name));
            } else if (image != 0L && (image == host.frameTargetColorImage()
                    || image == offTarget.offTargetColorImage())) {
                live = image;
            } else {
                live = 0L;
            }
            if (image != 0L && live != 0L && image == live) {
                continue;
            }
            if (live != 0L) {
                VulkanGlTexture republished;
                if (depthKey != 0L) {
                    long liveView = offTarget.sharedDepths().view(depthKey);
                    int[] liveExtent = offTarget.sharedDepths().extent(depthKey);
                    int liveLayout = offTarget.sharedDepths().layout(depthKey);
                    republished = liveView == 0L || liveExtent == null
                            || liveLayout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED ? null
                                    : host.registry().publishExternalDepthImage(name, live, liveView,
                                            liveExtent[0], liveExtent[1], liveLayout);
                } else {
                    republished = host.registry().publishExternalImage(name, live,
                            offTarget.chainPairColorViewAt(owner, host.publishedImageSlot(name)),
                            host.targetWidth(), host.targetHeight());
                    // A republished wrapper is a fresh wrapper: without the flag it declares SHADER_READ_ONLY over an
                    // image the pair keeps in GENERAL (09600/00344 colour form, 00900 on the next attach).
                    if (republished != null) {
                        republished.notePairImage(true);
                    }
                }
                if (republished != null) {
                    host.setPublishedValueAt(idx, republished);
                    host.putPublishedImage(name, live);
                    host.notePublishedRepublished();
                    continue;
                }
            }
            host.registry().unpublishExternalImage(name);
            host.removePublishedImage(name);
            host.removePublishedImageIdentity(name);
            host.removePublishedImageSlot(name);
            host.removePublishedDepthKey(name);
            host.notePublishedUnpublished();
            host.notePublishedStalePruned();
            if (host.publishedStalePruned() == 1) {
                host.record("published name " + name + " named image 0x"
                        + (image == 0L ? "unknown" : Long.toHexString(image))
                        + " which is no longer the pair's image (0x" + Long.toHexString(live)
                        + " for identity 0x" + Long.toHexString(owner)
                        + "); unpublished before any barrier could be recorded on it");
            }
            host.removePublishedAt(idx);
        }
    }

}
