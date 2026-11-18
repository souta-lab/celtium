package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceFormatProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceImageFormatProperties;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageFormatProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;

/**
 * The colour-format contract for chain pairs (D18, QUEUE 404): how a GL internal format becomes the
 * {@link TargetFormatClass} a pair is allocated with, and whether this device can actually use that class as a
 * sampled colour attachment.
 * <p>
 * The mapping itself is item 394's ({@link TargetFormatClass#forGlInternalFormat}); this class owns the two
 * decisions around it that the pair generation needs. The first is the fallback: an unrecorded texture (the stub
 * answers 0), an unmapped internal format (the mapping answers {@code null}) and a depth internal format all
 * resolve to {@link TargetFormatClass#RGBA8_UNORM}, which is the renderer's standing behaviour - the caller that
 * needs to <em>count</em> how often a request was not met exactly is the census (QUEUE 405), not this resolution.
 * The second is the device check, shaped exactly like {@link VulkanDepthFormat}'s: the format must carry the
 * colour-attachment, blend and sampled bits in its optimal-tiling features, and the device must accept a 2D,
 * optimal-tiling, single-sample image of it with the usage {@code GllessFrameTarget} creates pair images with.
 * "16F is a core Vulkan format, so the check is unnecessary" is not taken: the version of the required-format
 * set cannot be read from an original source in this environment, and one query per (device, class) is cheaper
 * than a wrong assumption. A class the device refuses resolves to {@code RGBA8_UNORM} at the call site.
 */
public final class VulkanColorFormat {

    /** The usage a pair colour image is created with; the device check asks for exactly this (see allocateImage). */
    private static final int PAIR_COLOUR_USAGE = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
            | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;

    /** The device the cached answers below were taken on; a different device invalidates them. */
    private static VkPhysicalDevice checkedDevice;
    private static final boolean[] DECIDED = new boolean[TargetFormatClass.values().length];
    private static final boolean[] USABLE = new boolean[TargetFormatClass.values().length];

    private VulkanColorFormat() {
    }

    /**
     * {@return the class a recorded GL internal format asks its pair to be allocated with}: the 394 mapping's
     * answer, with every refusal (unrecorded 0, unmapped, depth) resolved to {@code RGBA8_UNORM}.
     */
    public static TargetFormatClass forRequest(int glInternalFormat) {
        TargetFormatClass mapped = TargetFormatClass.forGlInternalFormat(glInternalFormat);
        return mapped == null ? TargetFormatClass.RGBA8_UNORM : mapped;
    }

    /**
     * {@return the class for a target that may be pinned to the frame's format}: the frame image and identity 0
     * are composed into an 8-bit image, so a pinned target is {@code RGBA8_UNORM} whatever GL asked for
     * (QUEUE 404 rule R1); an unpinned target resolves its request through {@link #forRequest}.
     */
    public static TargetFormatClass pinnedOrRequested(boolean pinnedToFrame, int glInternalFormat) {
        return pinnedToFrame ? TargetFormatClass.RGBA8_UNORM : forRequest(glInternalFormat);
    }

    /**
     * {@return the wider of two classes} - the one class a multi-target tuple is allocated with (rule R4: the
     * render pass declares one format for every colour attachment, so per-slot classes would multiply the pass
     * variants by the tuple). Width is {@link TargetFormatClass#bytesPerPixel()} - not the enum ordinal and not
     * the Vk value, both of which rank {@code R11G11B10_FLOAT} (4 bytes/px) above {@code RGBA32_FLOAT}
     * (16 bytes/px). Equal widths go to the higher ordinal, which keeps the float class over the same-width
     * UNORM one ({@code R11G11B10_FLOAT} over {@code RGBA8_UNORM}).
     */
    public static TargetFormatClass widest(TargetFormatClass first, TargetFormatClass second) {
        if (first == null) {
            return second == null ? TargetFormatClass.RGBA8_UNORM : second;
        }
        if (second == null) {
            return first;
        }
        int firstBytes = first.bytesPerPixel();
        int secondBytes = second.bytesPerPixel();
        if (firstBytes != secondBytes) {
            return firstBytes > secondBytes ? first : second;
        }
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    /**
     * {@return whether this device can use the class as a pair colour image}: a sampled, blendable colour
     * attachment the renderer can create at 2D / optimal tiling / single sample with the pair's usage. Depth
     * classes are not colour attachments and answer {@code false}. The answer is asked once per (device, class)
     * and kept, in {@link VulkanDepthFormat}'s shape.
     */
    public static boolean usable(VkPhysicalDevice device, TargetFormatClass formatClass) {
        if (formatClass == null || formatClass.isDepth() || device == null) {
            return false;
        }
        synchronized (VulkanColorFormat.class) {
            if (checkedDevice != device) {
                checkedDevice = device;
                java.util.Arrays.fill(DECIDED, false);
            }
            int slot = formatClass.ordinal();
            if (!DECIDED[slot]) {
                USABLE[slot] = queryUsable(device, formatClass.vkFormat());
                DECIDED[slot] = true;
            }
            return USABLE[slot];
        }
    }

    private static boolean queryUsable(VkPhysicalDevice device, int format) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(device, format, properties);
            int features = properties.optimalTilingFeatures();
            if ((features & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT) == 0
                    || (features & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT) == 0
                    || (features & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) == 0) {
                return false;
            }
            VkImageFormatProperties out = VkImageFormatProperties.calloc(stack);
            int result = vkGetPhysicalDeviceImageFormatProperties(device, format, VK_IMAGE_TYPE_2D,
                    VK_IMAGE_TILING_OPTIMAL, PAIR_COLOUR_USAGE, 0, out);
            return result == VK_SUCCESS;
        }
    }
}
