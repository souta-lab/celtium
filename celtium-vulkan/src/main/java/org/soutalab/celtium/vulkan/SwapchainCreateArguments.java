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
     * Chooses the creation arguments for the default present mode: the transfer-only shape of issue
     * #422 (a) - the present path writes swapchain images solely through blits and clears, so
     * colour-attachment usage is dropped and displayable DCC compression metadata on RDNA3 proprietary
     * drivers never collides with {@code UNDEFINED -> TRANSFER_DST} presents. This is the BH-18
     * contract's default for this entry point: {@link VulkanSwapchain} makes its own selection through
     * the explicit overloads below, passing the transfer-only shape only when direct present is not
     * in effect ({@code -Dceltium.present.direct=false}; direct present is the default since the
     * Stage 2 integration landing restored it under ruling lanechat #9168), and the direct path's
     * shape otherwise, whose mode requires colour-attachment usage and keeps transfer usage
     * wherever the surface offers it (the readback/dump block and the loading screen's standalone
     * present read those bits).
     *
     * @param surface         what the surface reported
     * @param requestedWidth  the window's framebuffer width
     * @param requestedHeight the window's framebuffer height
     * @param desiredImages   the application's preference for the image count (3 for triple buffering)
     * @return the arguments to request, or a {@link Result#refusal} when this surface cannot carry a frame
     */
    public static Result select(Surface surface, int requestedWidth, int requestedHeight, int desiredImages) {
        return select(surface, requestedWidth, requestedHeight, desiredImages, true);
    }

    /**
     * Chooses the creation arguments, optionally dropping {@link #USAGE_COLOR_ATTACHMENT} so swapchain images are
     * allocated as transfer surfaces rather than renderable colour attachments (issue #422 (a): avoids RDNA3
     * displayable DCC compression metadata colliding with {@code UNDEFINED -> TRANSFER_DST} blit presents).
     * The transfer-only request is the composite fallback path's shape, reached whenever direct
     * present is not in effect ({@code -Dceltium.present.direct=false}; it is on by default).
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
        return select(surface, requestedWidth, requestedHeight, desiredImages, transferOnlyPresent, false);
    }

    /**
     * As {@link #select(Surface, int, int, int, boolean)}, optionally preferring the frame colour format
     * ({@link #FORMAT_R8G8B8A8_UNORM}) when the surface offers it: with direct present enabled, a swapchain
     * in the frame's own format is the only one whose images the frame may borrow as its render target
     * (the format gate in the lending contract), so the preference is what lets the direct path engage at
     * all on surfaces that offer both channel orders. Surfaces offering only BGRA keep the historical
     * first-offered choice and their frames fall back to the composed present.
     *
     * @param preferFrameFormat true to choose {@link #FORMAT_R8G8B8A8_UNORM} ahead of the surface's order
     */
    public static Result select(Surface surface, int requestedWidth, int requestedHeight, int desiredImages,
            boolean transferOnlyPresent, boolean preferFrameFormat) {
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
        int fallbackFormat = 0;
        int fallbackColorSpace = 0;
        for (int i = 0; i < offeredCount; i++) {
            int candidate = surface.formats[i * 2];
            int colorSpace = surface.formats[i * 2 + 1];
            if (candidate == FORMAT_UNDEFINED) {
                sawUndefined = true;
                continue;
            }
            if (!chosen && (candidate == FORMAT_B8G8R8A8_UNORM || candidate == FORMAT_R8G8B8A8_UNORM)) {
                if (preferFrameFormat && candidate != FORMAT_R8G8B8A8_UNORM) {
                    // Direct present prefers the frame format: keep scanning in case the surface lists it
                    // later; the first acceptable non-frame format is remembered as the fallback choice.
                    if (fallbackFormat == 0) {
                        fallbackFormat = candidate;
                        fallbackColorSpace = colorSpace;
                    }
                    continue;
                }
                chosenFormat = candidate;
                chosenColorSpace = colorSpace;
                chosen = true;
            }
        }
        if (!chosen && fallbackFormat != 0) {
            // The frame format was preferred but not offered: the first acceptable format stands, with the
            // preference recorded so a leg's surface line says why this generation cannot be borrowed from.
            chosenFormat = fallbackFormat;
            chosenColorSpace = fallbackColorSpace;
            chosen = true;
            result.formatNote = "direct present prefers R8G8B8A8_UNORM, which the surface did not offer;"
                    + " took the first acceptable format (the frame adopts this format, so this generation"
                    + " can be borrowed - Stage 1 R1 revised)";
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
     * {@return the format the frame colour image is allocated in} (Stage 1 BGRA ignition, QUEUE 404 rule R1
     * revised): the frame follows the swapchain's format, so a generation whose format the frame can adopt
     * can be borrowed for direct present instead of composed.
     * <p>
     * The classic frame format is {@link #FORMAT_R8G8B8A8_UNORM}. It stays the answer whenever direct present
     * is off (nothing borrows, so the frame keeps the format every existing pass and readback was built
     * against), and whenever the surface's chosen format is not one the frame may adopt: the sRGB formats
     * are never adopted (an sRGB attachment would apply a linear-to-sRGB conversion on every write, and
     * Minecraft's pipeline is not linear), and {@link #FORMAT_B8G8R8A8_UNORM} is adopted only when the device
     * can attach, blend and sample it ({@code bgraUsable}, the caller's device answer) - a frame the device
     * cannot render into would be worse than a composed present. Any other answer leaves the generation
     * unborrowable: the borrow gate compares formats and refuses, which is the composed present, never a
     * mismatched framebuffer.
     * <p>
     * The alternative considered and not taken: {@code VK_KHR_swapchain_mutable_format} with a component
     * swizzle, keeping the frame at R8G8B8A8 over a BGRA swapchain image. A view's component mapping applies
     * to reads of the image, not to colour-attachment writes, so the frame's R would land in the swapchain
     * image's B byte - the presented picture would swap red and blue. The extension would also need
     * {@code VK_KHR_image_format_list} plumbing at creation for a benefit the follow-the-surface rule
     * already provides on every offer shape.
     *
     * @param swapchainFormat the format the live generation was created in, or {@link #FORMAT_UNDEFINED}
     *                        when no generation exists yet
     * @param directPresent   whether direct present is enabled ({@code -Dceltium.present.direct})
     * @param bgraUsable      whether the device can attach, blend and sample B8G8R8A8_UNORM
     */
    public static int frameColorFormat(int swapchainFormat, boolean directPresent, boolean bgraUsable) {
        if (directPresent && swapchainFormat == FORMAT_B8G8R8A8_UNORM && bgraUsable) {
            return FORMAT_B8G8R8A8_UNORM;
        }
        return FORMAT_R8G8B8A8_UNORM;
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
