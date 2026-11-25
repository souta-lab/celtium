package org.soutalab.celtium.vulkan;

/**
 * The swapchain's creation arguments, decided from what the surface reports (BH-18).
 * <p>
 * A pure function of the surface's numbers and the application's request: no device, no {@code VkSurfaceCapabilitiesKHR}
 * and no LWJGL type reaches it, so the decision can be driven by synthetic inputs - which is the only way its contract
 * can be checked, because a real surface never exercises the branches (measured on AMD RX 6400 and Intel UHD 630: both
 * pin {@code currentExtent} and report {@code maxImageCount == 0}, so the clamp paths never run).
 * <p>
 * {@link VulkanSwapchain#createSwapchain} reads the live capabilities into these arguments and calls
 * {@link #select}, so the checked function and the production path are the same code rather than two implementations.
 * <p>
 * The two traps from the audit are encoded here rather than remembered:
 * <ul>
 *   <li>{@code currentExtent.width == 0xFFFFFFFF} means the surface does not dictate a size, and the request is clamped
 *       into {@code minImageExtent..maxImageExtent} (a zero or negative request lands on the minimum, never on 0).</li>
 *   <li>{@code maxImageCount == 0} means unlimited, not zero: nothing is clamped.</li>
 * </ul>
 */
public final class SwapchainCreateArguments {
    /** {@code VK_FORMAT_UNDEFINED}: "no preferred format, the application chooses" in the surface's format list. */
    public static final int FORMAT_UNDEFINED = 0;
    /** The format this renderer wants, BGRA first because it is the one every desktop WSI in play offers. */
    public static final int FORMAT_B8G8R8A8_UNORM = 44;
    /** The same byte order, sRGB encoding. Named because the presented-image dump decides its channel swap from it. */
    public static final int FORMAT_B8G8R8A8_SRGB = 50;
    /** The other acceptable 8-bit format. */
    public static final int FORMAT_R8G8B8A8_UNORM = 37;

    public static final int USAGE_TRANSFER_SRC = 0x00000001;
    public static final int USAGE_TRANSFER_DST = 0x00000002;
    public static final int USAGE_COLOR_ATTACHMENT = 0x00000010;

    public static final int ALPHA_OPAQUE = 0x00000001;
    public static final int ALPHA_PRE_MULTIPLIED = 0x00000002;
    public static final int ALPHA_POST_MULTIPLIED = 0x00000004;
    public static final int ALPHA_INHERIT = 0x00000008;

    /** {@code 0xFFFFFFFF}: the surface does not dictate the extent, so the request is clamped instead. */
    public static final long EXTENT_UNSPECIFIED = 0xFFFFFFFFL;

    /** What the surface reports, as plain numbers so a synthetic input and the live one are the same shape. */
    public static final class Surface {
        public int minImageCount;
        public int maxImageCount;
        public int supportedUsage;
        public int supportedCompositeAlpha;
        public long currentExtentWidth = EXTENT_UNSPECIFIED;
        public long currentExtentHeight = EXTENT_UNSPECIFIED;
        public int minExtentWidth = 1;
        public int minExtentHeight = 1;
        public int maxExtentWidth = Integer.MAX_VALUE;
        public int maxExtentHeight = Integer.MAX_VALUE;
        /** Flattened {@code (format, colorSpace)} pairs, in the order the surface listed them. */
        public int[] formats = new int[0];
    }

    /** What goes into {@code VkSwapchainCreateInfoKHR}, with the reasoning that produced it. */
    public static final class Result {
        public int format;
        public int colorSpace;
        public int imageCount;
        public int width;
        public int height;
        public int usage;
        public int compositeAlpha;
        public boolean imageCountClamped;
        public boolean imagesUnlimited;
        public boolean extentFromSurface;
        public String formatNote = "";
        public String imageCountNote = "";
        public String droppedUsage = "";
        public String alphaNote = "OPAQUE";
        public String extentNote = "";
        /** Null when the surface is usable; otherwise the sentence that refuses it, and nothing may be created. */
        public String refusal;
        /**
         * True only for the pinned-extent refusal: the surface has not reported a size to create against
         * (a minimised window pins {@code currentExtent} at 0x0), so the attempt is a wait, not a verdict on
         * the surface. Creation is retried once a size is reported, and the caller must not count the wait
         * toward the swapchain creation escalation, which counts refused creations - a wait is a creation
         * no surface could have accepted, however long the window stays without a size.
         */
        public boolean awaitingSurfaceSize;

        /** {@return the offer beside the choice, one line, for a leg's console and for the diagnostics}. */
        public String describe() {
            return "images=" + this.imageCount + (this.imageCountClamped ? " (clamped to maxImageCount)" : "")
                    + (this.imagesUnlimited ? " (max unlimited)" : "") + " " + this.imageCountNote
                    + " extent=" + this.width + "x" + this.height + " (" + this.extentNote + ")"
                    + " format=" + this.format + " colourSpace=" + this.colorSpace
                    + (this.formatNote.isEmpty() ? "" : " (" + this.formatNote + ")")
                    + " usage=0x" + Integer.toHexString(this.usage)
                    + (this.droppedUsage.isEmpty() ? "" : " dropped[" + this.droppedUsage + "]")
                    + " compositeAlpha=0x" + Integer.toHexString(this.compositeAlpha) + " " + this.alphaNote;
        }
    }

    private SwapchainCreateArguments() {
    }

    /**
     * Chooses the creation arguments for the direct present path (design CELTIUM-A-TO-B Stage 1; lanechat
     * #8830 (3)(4)): the frame renders straight into the swapchain image, so colour-attachment usage is
     * required and transfer usage is kept wherever the surface offers it, for the readback/dump block and
     * the loading screen's standalone present.
     * <p>
     * The previous default was transfer-only usage, chosen for issue #422 (a): on RDNA3 proprietary
     * drivers, displayable DCC compression metadata on colour-attachment swapchain images collided with
     * {@code UNDEFINED -> TRANSFER_DST} blit presents. That trade belonged to the blit path it protected:
     * a transfer-only swapchain cannot be rendered into at all, and the direct path's own transitions -
     * the frame target's acquire/release barriers, which already carry the full attachment/transfer/shader
     * masks #422 (a) asked for - are the DCC-relevant surface now. The transfer-only shape survives only
     * behind {@code -Dceltium.present.direct=false}, through the overload below.
     *
     * @param surface         what the surface reported
     * @param requestedWidth  the window's framebuffer width
     * @param requestedHeight the window's framebuffer height
     * @param desiredImages   the application's preference for the image count (3 for triple buffering)
     * @return the arguments to request, or a {@link Result#refusal} when this surface cannot carry a frame
     */
    public static Result select(Surface surface, int requestedWidth, int requestedHeight, int desiredImages) {
        return select(surface, requestedWidth, requestedHeight, desiredImages, false);
    }

    /**
     * Chooses the creation arguments, optionally dropping {@link #USAGE_COLOR_ATTACHMENT} so swapchain images are
     * allocated as transfer surfaces rather than renderable colour attachments (issue #422 (a): avoids RDNA3
     * displayable DCC compression metadata colliding with {@code UNDEFINED -> TRANSFER_DST} blit presents).
     * The transfer-only request is the composite fallback path's shape, reached only with
     * {@code -Dceltium.present.direct=false}.
     *
     * @param surface             what the surface reported
     * @param requestedWidth      the window's framebuffer width
     * @param requestedHeight     the window's framebuffer height
     * @param desiredImages       the application's preference for the image count (3 for triple buffering)
     * @param transferOnlyPresent true to request only transfer usage (fallback path), false to include
     *                            colour-attachment (the direct present path)
     * @return the arguments to request, or a {@link Result#refusal} when this surface cannot carry a frame
     */
    public static Result select(Surface surface, int requestedWidth, int requestedHeight, int desiredImages,
            boolean transferOnlyPresent) {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        Result result = new Result();

        // --- image count: the surface's minimum, the application's preference, then the surface's maximum.
        // maxImageCount == 0 is unlimited; a minimum of 0 is a broken answer to a question the spec answers with at
        // least 1, and is treated as 1 rather than as "no images".
        int minImages = surface.minImageCount;
        if (minImages < 1) {
            result.imageCountNote = "minImageCount=" + minImages + " treated as 1 (the spec requires >= 1)";
            minImages = 1;
        }
        int imageCount = Math.max(minImages, desiredImages);
        result.imagesUnlimited = surface.maxImageCount == 0;
        if (!result.imagesUnlimited && imageCount > surface.maxImageCount) {
            imageCount = surface.maxImageCount;
            result.imageCountClamped = true;
        }
        result.imageCount = imageCount;

        // --- extent: the surface's current extent, or the request clamped into min..max (the trap: 0xFFFFFFFF).
        int[] extent = extentOf(surface, requestedWidth, requestedHeight);
        result.width = extent[0];
        result.height = extent[1];
        result.extentFromSurface = extent[2] != 0;
        result.extentNote = result.extentFromSurface
                ? "surface currentExtent"
                : "request clamped into min..max (currentExtent unspecified)";
        if (result.width <= 0 || result.height <= 0) {
            // Only the pinned-extent path can land here (the clamp never returns below 1): a surface that pins a
            // 0x0 current extent - a minimised window on some platforms - would otherwise flow unprocessed into
            // vkCreateSwapchainKHR and fail there every frame until the window settles (bughunt LANE-05 #8).
            // Refusing by name lets the caller skip the frame and retry creation once the surface reports a size.
            // The refusal is flagged as that wait (awaitingSurfaceSize), because counting it as a refusal is
            // what killed a healthy shipped v0.3.0 session: the surface pinned 0x0 while a world loaded, the
            // loading screen's present loop retried unthrottled, and 120 waits in about two seconds reached
            // the creation escalation's limit on a surface that created its swapchain as soon as it reported
            // a size again.
            result.awaitingSurfaceSize = true;
            result.refusal = "the surface pins a current extent of " + result.width + "x" + result.height
                    + ", so no swapchain can be created until it reports a size";
            return result;
        }

        // --- usage: the present path writes swapchain images solely through vkCmdBlitImage (and first-acquire
        // vkCmdClearColorImage), never as render-pass colour attachments. Requesting transfer-only usage by default
        // avoids displayable DCC compression allocation mismatches on RDNA3 proprietary drivers (issue #422 (a)).
        if (transferOnlyPresent) {
            if ((surface.supportedUsage & USAGE_TRANSFER_DST) == 0) {
                result.refusal = "the surface does not support transfer-destination usage (supported=0x"
                        + Integer.toHexString(surface.supportedUsage) + "), which the transfer-only present blit"
                        + " writes to";
                return result;
            }
            result.usage = USAGE_TRANSFER_DST | (surface.supportedUsage & USAGE_TRANSFER_SRC);
            result.droppedUsage = ("transfer-only"
                    + ((surface.supportedUsage & USAGE_TRANSFER_SRC) == 0 ? " TRANSFER_SRC" : "")).trim();
        } else {
            if ((surface.supportedUsage & USAGE_COLOR_ATTACHMENT) == 0) {
                result.refusal = "the surface does not support colour-attachment usage (supported=0x"
                        + Integer.toHexString(surface.supportedUsage) + "), which the native present path attaches the"
                        + " frame to";
                return result;
            }
            result.usage = USAGE_COLOR_ATTACHMENT
                    | (surface.supportedUsage & USAGE_TRANSFER_DST)
                    | (surface.supportedUsage & USAGE_TRANSFER_SRC);
            result.droppedUsage = ((surface.supportedUsage & USAGE_TRANSFER_DST) == 0 ? "TRANSFER_DST " : "")
                    + ((surface.supportedUsage & USAGE_TRANSFER_SRC) == 0 ? "TRANSFER_SRC" : "");
            result.droppedUsage = result.droppedUsage.trim();
        }

        // --- composite alpha: OPAQUE when offered, else the next one the surface does offer, named.
        if (surface.supportedCompositeAlpha == 0) {
            result.refusal = "the surface advertises no composite-alpha mode at all";
            return result;
        }
        if ((surface.supportedCompositeAlpha & ALPHA_OPAQUE) != 0) {
            result.compositeAlpha = ALPHA_OPAQUE;
        } else if ((surface.supportedCompositeAlpha & ALPHA_PRE_MULTIPLIED) != 0) {
            result.compositeAlpha = ALPHA_PRE_MULTIPLIED;
            result.alphaNote = "PRE_MULTIPLIED (OPAQUE not offered)";
        } else if ((surface.supportedCompositeAlpha & ALPHA_POST_MULTIPLIED) != 0) {
            result.compositeAlpha = ALPHA_POST_MULTIPLIED;
            result.alphaNote = "POST_MULTIPLIED (OPAQUE not offered)";
        } else {
            result.compositeAlpha = ALPHA_INHERIT;
            result.alphaNote = "INHERIT (OPAQUE not offered)";
        }

        // --- format: a format this renderer can present, from the ones the surface listed. VK_FORMAT_UNDEFINED is
        // *not* a format: it is the surface saying "no preference, you choose", and passing it to
        // VkSwapchainCreateInfoKHR::imageFormat is invalid - which is exactly the hole the audit names. A list whose
        // only entry is UNDEFINED therefore falls back to the default format with the colour space the surface
        // reported for that entry.
        int offeredCount = surface.formats.length / 2;
        if (offeredCount == 0) {
            result.refusal = "the surface advertises no formats";
            return result;
        }
        int chosenFormat = 0;
        int chosenColorSpace = 0;
        boolean chosen = false;
        boolean sawUndefined = false;
        for (int i = 0; i < offeredCount; i++) {
            int candidate = surface.formats[i * 2];
            int colorSpace = surface.formats[i * 2 + 1];
            if (candidate == FORMAT_UNDEFINED) {
                sawUndefined = true;
                continue;
            }
            if (!chosen && (candidate == FORMAT_B8G8R8A8_UNORM || candidate == FORMAT_R8G8B8A8_UNORM)) {
                chosenFormat = candidate;
                chosenColorSpace = colorSpace;
                chosen = true;
            }
        }
        if (!chosen) {
            // No preferred format: take the first real one the surface listed, never the UNDEFINED marker.
            for (int i = 0; i < offeredCount; i++) {
                int candidate = surface.formats[i * 2];
                if (candidate == FORMAT_UNDEFINED) {
                    continue;
                }
                chosenFormat = candidate;
                chosenColorSpace = surface.formats[i * 2 + 1];
                chosen = true;
                result.formatNote = "no B8G8R8A8/R8G8B8A8 offered; took the surface's first real format";
                break;
            }
        }
        if (!chosen) {
            // Every entry is UNDEFINED: the surface has no preference and no usable entry, so the default is chosen
            // and said out loud. Colour space still comes from the surface's own entry.
            chosenFormat = FORMAT_B8G8R8A8_UNORM;
            chosenColorSpace = surface.formats[1];
            result.formatNote = "surface offered only VK_FORMAT_UNDEFINED; chose the default B8G8R8A8_UNORM"
                    + " with the colour space it reported";
            chosen = true;
        } else if (sawUndefined && result.formatNote.isEmpty()) {
            result.formatNote = "surface also listed VK_FORMAT_UNDEFINED, which is not a format and was not chosen";
        }
        result.format = chosenFormat;
        result.colorSpace = chosenColorSpace;
        return result;
    }

    /**
     * {@return the extent to create}, as {@code {width, height, fromSurface}}: the surface's current extent when it
     * dictates one, otherwise the request clamped into {@code minImageExtent..maxImageExtent}.
     * <p>
     * Public and pure because two callers must agree: {@link #select} when a swapchain is created, and the resize
     * check that asks whether a fresh swapchain would get the same extent. Two copies of this clamp would be two
     * answers to one question.
     */
    public static int[] extentOf(Surface surface, int requestedWidth, int requestedHeight) {
        if (Long.compareUnsigned(surface.currentExtentWidth, EXTENT_UNSPECIFIED) != 0) {
            return new int[] {(int) surface.currentExtentWidth, (int) surface.currentExtentHeight, 1};
        }
        return new int[] {clampExtent(requestedWidth, surface.minExtentWidth, surface.maxExtentWidth),
                clampExtent(requestedHeight, surface.minExtentHeight, surface.maxExtentHeight), 0};
    }

    /** {@return the request clamped into the surface's limits, never below the minimum and never zero}. */
    private static int clampExtent(int requested, int minimum, int maximum) {
        int floor = Math.max(1, minimum);
        int ceiling = Math.max(floor, maximum);
        return Math.max(floor, Math.min(Math.max(1, requested), ceiling));
    }
}
