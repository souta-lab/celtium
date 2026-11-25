package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;

/**
 * The frame-target and clear judgments the backend's routing consults, as pure functions of their inputs.
 * <p>
 * Split out of {@link VulkanTerrainGpuBackend} (Stage 6 receiver design, group B2): each rule answers from
 * the values the caller already holds - no device, no registry, no backend state - so the rule can be pinned
 * without standing the renderer up. The callers keep the state and pass it in.
 */
final class FrameTargetJudgments {

    /** The GL clear bits this renderer owns: colour (0x4000) and depth (0x0100). Stencil is not implemented. */
    static final int CLEAR_ORDER_MASK = 0x4100;

    /** {@return the class a {@code celtium.vulkan.targetFormatClass} value names}, or null for no forcing. */
    static TargetFormatClass celtium$parseForcedTargetFormatClass(String propertyValue) {
        if (propertyValue == null) {
            return null;
        }
        String name = propertyValue.trim();
        if (name.isEmpty() || name.equalsIgnoreCase("default")) {
            return null;
        }
        for (TargetFormatClass candidate : TargetFormatClass.values()) {
            if (!candidate.isDepth() && candidate.name().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * {@return whether a GL clear mask names an attachment this renderer owns}, the mask half of the clear-order rule.
     * <p>
     * A clear of neither colour nor depth erases nothing the renderer holds, so the accepted draws have nothing to be
     * ordered against. Stencil is the case that matters: the measured scene's dominant clear is ModularUI's
     * {@code Stencil#reset} (mask 0x400), issued between GUI widgets, and it used to submit the GUI batch it cannot
     * touch - one window per widget.
     */
    static boolean celtium$clearNamesOwnedAttachment(int mask) {
        return (mask & CLEAR_ORDER_MASK) != 0;
    }

    /**
     * Whether the pending mask is exactly the clear the queue carried.
     * <p>
     * Extracted so the rule can be pinned without a device, the same shape as the other decision tables here. It is a
     * containment test rather than an equality one because the stub ORs the mask: a clear of colour+depth that follows
     * a depth-only clear of the same frame leaves {@code 0x4100} pending, and the carried clear is still the one that
     * has to be consumed. A mask carrying a bit the reservation does not name belongs to a clear this method did not
     * carry, and consuming it would drop that clear - so it is left pending for the frame machinery.
     */
    static boolean celtium$carriedClearMatches(int carriedMask, int clearMask) {
        return carriedMask != 0 && (carriedMask & clearMask) == clearMask;
    }

    /**
     * The decision table behind {@link VulkanTerrainGpuBackend#celtium$clearNeedsOrderingFlush}, as a pure function so the rule can be pinned
     * without a device.
     * <p>
     * Each input is one of the reasons the flush is still needed, and the answer is "skip" only when none of them
     * applies:
     * <ul>
     * <li>{@code pairQueueAvailable} - the GL bridge has no pair queue, so the flush is the only ordering there is;</li>
     * <li>{@code rawWritesPending} - the flush also records the coalesced texture copies, and a copy into the texture
     *     the clear covers is an ordering the queue does not express;</li>
     * <li>{@code identityResolved} - an unresolved framebuffer cannot be shown to have nothing queued;</li>
     * <li>{@code imageHasPendingDraws} - the clear's own image has queued draws, which the clear must erase.</li>
     * </ul>
     */
    static boolean celtium$clearNeedsOrderingFlushDecision(boolean pairQueueAvailable, boolean rawWritesPending,
                                                           boolean identityResolved, boolean imageHasPendingDraws) {
        return !pairQueueAvailable || rawWritesPending || !identityResolved || imageHasPendingDraws;
    }

    /**
     * {@return the refusal reason for a depth publication whose shared depth store entry is incomplete}:
     * {@code depth-layout-unrecorded} when image, view and extent are all present and only the layout record
     * is still {@code VK_IMAGE_LAYOUT_UNDEFINED}, {@code depth-not-in-store} for every other incompleteness.
     * <p>
     * The layout-only case is a first-prepare race, not a missing image (item 537 wall (b1)): the store entry
     * exists before the pair that draws into the image first declares a layout through the borrowed pair's
     * setter, so a prepare that tries to publish in between is refused on the honest UNDEFINED record - and
     * publishing with an UNDEFINED declared layout is refused by the share itself, by design. Measured in the
     * Tier 1 leg: refused with layout=0, then published two seconds later with layout=3 once the owner had
     * declared it. Naming the case apart keeps that self-healing transient from reading as the standing
     * refusal in a leg's log. The publication decision is unchanged: both reasons refuse, and the next prepare
     * retries. Static and pure so the split is pinned without a device.
     */
    static String celtium$depthPublishRefusalReason(long image, long view, int[] extent, int layout) {
        boolean layoutOnly = image != 0L && view != 0L && extent != null
                && layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
        return layoutOnly ? "depth-layout-unrecorded" : "depth-not-in-store";
    }

    /**
     * {@return whether the terrain pass about to be recorded draws into the bound framebuffer's chain pair instead of
     * the frame image} (item 332).
     * <p>
     * The rule is "the pair renders into the depth image the framebuffer's depth texture owns", and nothing wider: the
     * pass is routed only when the image it would write is the image a depth copy or a sampler is going to read. That
     * is what separates this from {@link VulkanTerrainGpuBackend#TERRAIN_TO_BOUND_TARGET}, which routes on "a non-main framebuffer is bound and
     * a pair exists at the window's extent" and was measured for GTCEu's bloom only.
     * <p>
     * Package-private and static so the table is pinned without a device. The arguments are the facts the caller
     * already has in hand (switch, current target, pair, depth texture) and nothing else - no lookups happen here, so
     * the decision cannot race a registry mutation between two of its own conditions. {@code false} is the answer for
     * every state but the measured one, which is also the second half of "the main path is unchanged": off is off at
     * the switch, and on is still off for main, the main depth texture, identity 0, a pair-less identity, a
     * non-shared depth, an extent mismatch and a pair with three or more colour targets.
     */
    static boolean celtium$routeTerrainToDepthTarget(boolean enabled, boolean currentIsMain, boolean depthIsMainDepth,
            long identity, boolean pairExists, int pairWidth, int pairHeight, int depthWidth, int depthHeight,
            boolean depthShared, int pairColorCount) {
        return celtium$depthTargetRefusal(enabled, currentIsMain, depthIsMainDepth, identity, pairExists, pairWidth,
                pairHeight, depthWidth, depthHeight, depthShared, pairColorCount) == null;
    }

    /**
     * {@return why {@link #celtium$routeTerrainToDepthTarget} answered false, or null when it answered true}.
     * <p>
     * The one place the conditions live, so the predicate and the census line that explains a refusal cannot disagree
     * about which condition failed. The conditions, in the order they are asked:
     * <ol>
     *   <li>{@code enabled} - the lab switch; off in the default build by design (see {@link VulkanTerrainGpuBackend#TERRAIN_TO_DEPTH_TARGET}).</li>
     *   <li>{@code !currentIsMain} - a main-target pass belongs to the frame image and to no pair.</li>
     *   <li>{@code !depthIsMainDepth} - the depth texture is not the game framebuffer's own. A deferred pipeline
     *       attaches the main depth texture to every one of its gbuffer framebuffers, so those framebuffers present
     *       exactly the routed shape (a pair, a shared depth, matching extents) at the window's size; without this
     *       condition the main scene's terrain is what gets routed, and the presented frame loses it (item 408,
     *       defect 1: leg 20 routed twelve main passes and presented went one colour). {@code currentIsMain} cannot
     *       catch them: under a deferred pipeline the main scene itself arrives under raw framebuffer identities.</li>
     *   <li>{@code identity != 0} - GL name 0 is the window; its draws already land in the frame image.</li>
     *   <li>{@code pairExists} - the pair is item 333's registration's work; without one the pass stays on the frame.</li>
     *   <li>{@code pairColorCount} is 1 or 2 - the frame pass attaches the pair's colour slot 0 and its depth, which
     *       is all a terrain program writes. Two is the measured main case, not an extension: the framebuffer bound
     *       while a shadow pass draws is the shadow program's, whose pair holds two colour images (the design
     *       review's condition 7 assumed one, and leg 20's silence followed from that - item 408, defect 2's second
     *       blocker). The second image is left unwritten, exactly as a GL program with one output leaves draw
     *       buffer 1 alone. Three or more has no measured case and stays a named refusal, not a silent fallback
     *       ({@code ModProgramMrtRefusalTest} is the neighbouring rule).</li>
     *   <li>{@code depthShared} - the pair must be rendering into the shared depth image for the depth texture: that
     *       image is the GL-framebuffer's depth storage, and it is the whole point of the route.</li>
     *   <li>{@code pair extent == depth extent} - a Vulkan framebuffer's attachments share one extent, which is the
     *       same invariant {@code ensureChainPairSharedDepth} borrows under.</li>
     * </ol>
     * The extents compared are the <b>pair's</b> and the depth texture's, never the window's: the shadow pair is
     * 2048x2048 while the window is not, and that difference is the reason the older route cannot reach it.
     */
    static String celtium$depthTargetRefusal(boolean enabled, boolean currentIsMain, boolean depthIsMainDepth,
            long identity, boolean pairExists, int pairWidth, int pairHeight, int depthWidth, int depthHeight,
            boolean depthShared, int pairColorCount) {
        if (!enabled) {
            return "disabled";
        }
        if (currentIsMain) {
            return "main";
        }
        if (depthIsMainDepth) {
            return "main-depth";
        }
        if (identity == 0L) {
            return "window";
        }
        if (!pairExists) {
            return "no-pair";
        }
        if (pairColorCount != 1 && pairColorCount != 2) {
            return "mrt";
        }
        if (!depthShared) {
            return "not-shared";
        }
        if (depthWidth <= 0 || depthHeight <= 0 || pairWidth != depthWidth || pairHeight != depthHeight) {
            return "extent";
        }
        return null;
    }

    /**
     * {@return whether the depth half of a routed pass's owed clear may run in {@code frameSerial}}.
     * <p>
     * Two shapes of pair depth, two guards. A pair borrowing a shared depth image (a nonzero {@code depthKey}) is
     * guarded by the shared rule, {@link GllessFrameTarget#celtium$sharedDepthClearNeeded}: once per frame per
     * depth key, because the family of framebuffers attaching that texture shares the one image. A pair on its
     * own depth (key 0) has no family to guard against - the image is this pair's alone - and the once-per-frame
     * rule for its depth half is the per-identity one the consume has already passed
     * ({@link VulkanTerrainGpuBackend#celtium$pairClearNeeded}). The shared rule answers {@code false} for key 0 by design ("nothing to
     * guard"), so reading it directly here would strip the depth half of every private-depth pair's owed clear -
     * and refuse its self-test arm with it - leaving that clear pending for a later window to land after the
     * pass drew, which is the erasure item 384 measured on the bound-target route.
     * <p>
     * Package-private and static so the rule is pinned without a device, like the shared rule it extends.
     */
    static boolean celtium$routedDepthClearAllowed(long depthKey, long clearedFrameSerial, long frameSerial) {
        return depthKey == 0L
                || GllessFrameTarget.celtium$sharedDepthClearNeeded(depthKey, clearedFrameSerial, frameSerial);
    }

    /** {@return the leading contiguous run of non-zero GL colour attachment texture names}. */
    static int celtium$leadingColorAttachmentCount(int[] attachments) {
        if (attachments == null || attachments.length == 0 || attachments[0] == 0) {
            return 0;
        }
        int count = 1;
        while (count < attachments.length && attachments[count] != 0) {
            count++;
        }
        return count;
    }

    /**
     * {@return whether the old frame target may be released and replaced}, given whether its framebuffers could be
     * freed.
     * <p>
     * The release's outcome is the gate, not a report. A cache that could not be emptied still holds framebuffers
     * built over the old target's views, so deleting those views would leave the next window beginning a pass against
     * a framebuffer naming dead attachments - the fault the audit of issue #423 describes (item 372 hole 1). A false
     * keeps the old target and retries the recreation on a later frame, the direction {@code settleGpu}'s own failure
     * already takes.
     */
    static boolean celtium$frameTargetReplaceable(boolean framebuffersReleased) {
        return framebuffersReleased;
    }
}
