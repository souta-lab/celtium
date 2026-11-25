package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;

/**
 * The pack-terrain identity, handoff and pair judgments, as pure functions of their inputs.
 * <p>
 * Split out of {@link VulkanTerrainGpuBackend} (Stage 6 receiver design, group B2): the chain-pair identity
 * a pack pass draws into, the colour handoff that precedes it, the pair's shape and the terrain shadow route
 * are decided here from the values the caller already holds; the caller keeps the pairs and the state the
 * decisions are taken over.
 */
final class PackTerrainJudgments {

    /**
     * {@return whether the bound-target terrain route is on for the property's value}: on unless the value reads
     * "false", the mod-programs rule ({@code ModPrograms#MOD_PROGRAMS}). Package-private and static so the default
     * is pinned without a device.
     */
    static boolean celtium$terrainToBoundTargetEnabled(String property) {
        return !"false".equalsIgnoreCase(property);
    }

    /** {@return the darkness property parsed and clamped to [0, 1]}, 0.45 when absent or malformed. Pure. */
    static float celtium$shadowDarkness(String property) {
        if (property == null || property.isBlank()) {
            return 0.45f;
        }
        try {
            float value = Float.parseFloat(property.trim());
            return Float.isNaN(value) ? 0.45f : Math.max(0.0f, Math.min(1.0f, value));
        } catch (NumberFormatException malformed) {
            return 0.45f;
        }
    }

    /**
     * {@return why a non-routed terrain pass does not sample the frame's shadow capture}, or null when it does.
     * Static and pure so the rule is pinned without a device: the switch, a routed shadow pass earlier in this very
     * frame (a capture from another frame names a matrix and a depth the current camera did not draw), a depth texture
     * name, and a published native image for it.
     */
    static String celtium$terrainShadowRefusal(boolean enabled, boolean routeEnabled, long captureFrame, long frame,
            int depthTexture, boolean published) {
        if (!enabled) {
            return "switch-off";
        }
        if (!routeEnabled) {
            return "route-off";
        }
        if (captureFrame != frame) {
            return "no-capture-this-frame";
        }
        if (depthTexture <= 0) {
            return "no-depth-texture";
        }
        if (!published) {
            return "depth-not-published";
        }
        return null;
    }

    /**
     * The pure half of {@link VulkanTerrainGpuBackend#celtium$pairClassForWindow} (the device check stays with the caller, as in
     * the rest of QUEUE 404's decisions): no forcing follows the pair, a pinned identity never leaves the
     * default, anything else takes the device-checked forced class.
     */
    static TargetFormatClass celtium$windowPairClass(TargetFormatClass forcedClass, boolean pinned,
            TargetFormatClass pairClass, TargetFormatClass deviceCheckedForcedClass) {
        return celtium$windowPairClass(forcedClass, pinned, pairClass, deviceCheckedForcedClass,
                TargetFormatClass.RGBA8_UNORM);
    }

    /**
     * {@link #celtium$windowPairClass(TargetFormatClass, boolean, TargetFormatClass, TargetFormatClass)}
     * against the frame's own class: "the default" a pinned identity never leaves is the frame's class
     * (rule R1 as revised by Stage 1's BGRA ignition).
     */
    static TargetFormatClass celtium$windowPairClass(TargetFormatClass forcedClass, boolean pinned,
            TargetFormatClass pairClass, TargetFormatClass deviceCheckedForcedClass,
            TargetFormatClass frameClass) {
        if (forcedClass == null) {
            return pairClass;
        }
        if (pinned) {
            return frameClass;
        }
        return deviceCheckedForcedClass;
    }

    /**
     * {@return the chain-pair identity a pack terrain pass should draw into}: reuses an already-published
     * non-main chain-pair owner of the same GL slot-0 colour texture, extent, and shared-depth attachment
     * (so separate Iris FBOs for clear / gbuffers_terrain / gbuffers_cutout / gbuffers_water that attach
     * the same {@code colortex0} and {@code depthtex0} draw into one shared {@code VkImage} without
     * hijacking depth-free composite FBOs), or falls back to {@code boundIdentity}.
     */
    static long celtium$resolvePackTerrainDrawIdentity(long boundIdentity,
                                                       long existingPublishedOwner,
                                                       long mainIdentity,
                                                       long gameFramebufferIdentity,
                                                       boolean ownerPairExists,
                                                       boolean ownerSizeMatches,
                                                       boolean ownerDepthMatches) {
        if (existingPublishedOwner != Long.MIN_VALUE && existingPublishedOwner != 0L
                && existingPublishedOwner != boundIdentity
                && existingPublishedOwner != mainIdentity
                && existingPublishedOwner != gameFramebufferIdentity
                && ownerPairExists && ownerSizeMatches && ownerDepthMatches) {
            return existingPublishedOwner;
        }
        return boundIdentity;
    }

    /**
     * {@return the chain-pair identity whose colour contents should be handed off (copied) into {@code drawIdentity}}
     * before drawing, or {@code 0L} when no handoff applies.
     * <p>
     * Applies when {@code drawIdentity} could not directly reuse {@code existingPublishedOwner} solely because their
     * depth attachments differ (e.g. depth-free {@code deferred1} wrote {@code colortex0} ALT, and depth-attached
     * {@code gbuffers_water} / {@code gbuffers_hand} now draws onto the same {@code colortex0} ALT with
     * {@code depthtex0}). Without handing off colour, {@code drawIdentity}'s per-frame clear erases {@code deferred1}.
     */
    static long celtium$resolvePackTerrainColorHandoffSource(long boundIdentity,
                                                             long drawIdentity,
                                                             long existingPublishedOwner,
                                                             long mainIdentity,
                                                             long gameFramebufferIdentity,
                                                             boolean ownerPairExists,
                                                             boolean ownerSizeMatches,
                                                             boolean ownerDepthMatches,
                                                             boolean ownerAcquired,
                                                             boolean ownerWrittenThisFrame) {
        if (drawIdentity == boundIdentity
                && existingPublishedOwner != Long.MIN_VALUE && existingPublishedOwner != 0L
                && existingPublishedOwner != drawIdentity
                && existingPublishedOwner != mainIdentity
                && existingPublishedOwner != gameFramebufferIdentity
                && ownerPairExists && ownerSizeMatches && !ownerDepthMatches && ownerAcquired
                && ownerWrittenThisFrame) {
            return existingPublishedOwner;
        }
        return 0L;
    }

    /** {@return whether an off-target immediate window must use the load/store depth render pass}. */
    static boolean celtium$preserveWindowDepth(boolean terrainToDepthTarget, boolean hasDepthRecord) {
        return terrainToDepthTarget && hasDepthRecord;
    }

    /** {@return the chain-pair colour slot count to ensure without shrinking existing MRT slots}. */
    static int celtium$packTerrainPairColorCount(int targetColors, int existingPairColors) {
        return Math.max(Math.max(1, targetColors), existingPairColors);
    }

    /** Whether an ask for this many colour targets must be served by the multi-target cache entry. */
    static boolean celtium$chainPairMultiTarget(int colorCount) {
        return colorCount > 1;
    }
}
