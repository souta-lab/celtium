package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageFormatProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

/**
 * The depth format contract: which depth format this renderer will use, chosen from what the device can actually do.
 * <p>
 * A single fixed format cannot be the contract, and the two GPUs this project verifies on prove it: AMD's RADV
 * exposes no 24-bit depth format at all ({@code X8_D24_UNORM_PACK32} and {@code D24_UNORM_S8_UINT} both fail for a
 * sampled depth attachment) while Intel's UHD 630 exposes no {@code D16_UNORM_S8_UINT}. Minecraft itself asks for
 * 24-bit depth ({@code Framebuffer.func_147605_a} requests {@code GL_DEPTH_COMPONENT24}, and no file under
 * {@code net/minecraft/} calls a stencil entry point), so the order below prefers the format that meets that
 * requirement on the devices that have it and falls back to the closest thing that exists where it does not.
 * <p>
 * Every candidate is admitted only if it can be a single-sample 2D optimal-tiling image that is both a depth-stencil
 * attachment and sampled - not merely "the device lists the format" - because the renderer attaches it and reads it
 * back. If none passes, the run refuses with the list and the reason rather than creating a target that cannot work.
 */
public final class VulkanDepthFormat {

    /** The requirement Minecraft states, first; then the closest equivalents, most similar first. */
    private static final int[] CANDIDATES = {
            VK_FORMAT_D32_SFLOAT,
            VK_FORMAT_D24_UNORM_S8_UINT,
            VK_FORMAT_D32_SFLOAT_S8_UINT,
            VK_FORMAT_X8_D24_UNORM_PACK32,
            VK_FORMAT_D16_UNORM_S8_UINT,
            VK_FORMAT_D16_UNORM,
    };

    private static final String[] NAMES = {
            "D32_SFLOAT",
            "D24_UNORM_S8_UINT",
            "D32_SFLOAT_S8_UINT",
            "X8_D24_UNORM_PACK32",
            "D16_UNORM_S8_UINT",
            "D16_UNORM",
    };

    /** The override property, so an unusual device can be pinned without a rebuild. */
    public static final String OVERRIDE_PROPERTY = "celtium.vulkan.depthFormat";

    private static final AtomicInteger CHOSEN = new AtomicInteger(VK_FORMAT_UNDEFINED);
    /** The device the choice was made on, so a static caller (the render-pass builder) asks about the same one. */
    private static volatile VkPhysicalDevice chosenDevice;
    private static volatile String chosenName;
    private static volatile String decision;

    private VulkanDepthFormat() {
    }

    /** {@return the chosen format}, choosing it on first use. Throws when nothing is usable. */
    public static int chosen(VkPhysicalDevice device) {
        int existing = CHOSEN.get();
        if (existing != VK_FORMAT_UNDEFINED) {
            return existing;
        }
        synchronized (VulkanDepthFormat.class) {
            if (CHOSEN.get() != VK_FORMAT_UNDEFINED) {
                return CHOSEN.get();
            }
            int format = choose(device);
            chosenDevice = device;
            CHOSEN.set(format);
            return format;
        }
    }

    /**
     * {@return the chosen format} for a caller that has no device at hand - the render-pass builder is static and runs
     * after the frame target has already asked. Throws if nothing has chosen yet, because guessing here would produce a
     * render pass over an image of a different format.
     */
    public static int chosen() {
        int existing = CHOSEN.get();
        if (existing == VK_FORMAT_UNDEFINED) {
            VkPhysicalDevice device = chosenDevice;
            if (device == null) {
                throw new IllegalStateException("the depth format was asked for before anything chose one");
            }
            return chosen(device);
        }
        return existing;
    }

    /** {@return the chosen format's name}, for a report line. */
    public static String chosenName() {
        String name = chosenName;
        return name == null ? "unresolved" : name;
    }

    /** {@return how the choice was made}, for a report line. */
    public static String decisionSummary() {
        String summary = decision;
        return summary == null ? "not decided yet" : summary;
    }

    private static int choose(VkPhysicalDevice device) {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isEmpty()) {
            int wanted = formatNamed(override.trim());
            if (wanted == VK_FORMAT_UNDEFINED) {
                throw new IllegalStateException("celtium.vulkan.depthFormat=" + override
                        + " names no format this renderer knows; the candidates are " + String.join(", ", NAMES));
            }
            if (!usable(device, wanted)) {
                throw new IllegalStateException("celtium.vulkan.depthFormat=" + override
                        + " was asked for, but this device cannot use it as a sampled single-sample 2D depth-stencil"
                        + " attachment" + capabilityNote(device));
            }
            chosenName = override;
            decision = "celtium.vulkan.depthFormat=" + override + " (override, verified usable)";
            return wanted;
        }
        StringBuilder why = new StringBuilder();
        for (int i = 0; i < CANDIDATES.length; i++) {
            if (usable(device, CANDIDATES[i])) {
                chosenName = NAMES[i];
                decision = "chose " + NAMES[i] + " (candidate " + (i + 1) + " of " + CANDIDATES.length + ")"
                        + capabilityNote(device);
                return CANDIDATES[i];
            }
            why.append(why.length() == 0 ? "" : ", ").append(NAMES[i]).append(": rejected");
        }
        throw new IllegalStateException("no sampled depth format: none of " + String.join(", ", NAMES)
                + " supports DEPTH_STENCIL_ATTACHMENT|SAMPLED_IMAGE as a single-sample 2D optimal-tiling attachment"
                + " (" + OVERRIDE_PROPERTY + "=<format> overrides this). Per candidate: " + why);
    }

    /**
     * The predicate the contract names: the format must carry both the depth-stencil attachment and the sampled usage
     * bit in its optimal-tiling features, and the device must accept a 2D, optimal-tiling, single-sample image of it
     * that is both. The first check is the property; the second is the question the renderer actually asks when it
     * creates the image, and a device can answer them differently.
     */
    private static boolean usable(VkPhysicalDevice device, int format) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(device, format, properties);
            int features = properties.optimalTilingFeatures();
            if ((features & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) == 0
                    || (features & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) == 0) {
                return false;
            }
            VkImageFormatProperties out = VkImageFormatProperties.calloc(stack);
            int result = vkGetPhysicalDeviceImageFormatProperties(device, format, VK_IMAGE_TYPE_2D,
                    VK_IMAGE_TILING_OPTIMAL,
                    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, 0, out);
            return result == VK_SUCCESS;
        }
    }

    /** {@return the device name}, for the refusal text, or an empty string when it cannot be read. */
    private static String capabilityNote(VkPhysicalDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(device, properties);
            return " on " + properties.deviceNameString();
        } catch (RuntimeException | LinkageError error) {
            return "";
        }
    }

    private static int formatNamed(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(name)) {
                return CANDIDATES[i];
            }
        }
        return VK_FORMAT_UNDEFINED;
    }
}
