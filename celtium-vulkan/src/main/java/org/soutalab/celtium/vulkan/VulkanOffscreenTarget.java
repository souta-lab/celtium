package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

/**
 * A Vulkan render target standing in for one of the game's own framebuffers.
 * <p>
 * The immediate path refuses content bound for anything but the main framebuffer, and the measurement says what that costs:
 * one target, 256x256, 571 batches, all of a single vertex format. This class is the resource half of taking that content - an
 * image, its memory and a view - built and validated now so that the drawing half has somewhere to render and something to
 * copy back into the game's texture.
 * <p>
 * Deliberately not used for drawing yet: until the copy-back exists, taking the batches would lose their content, so the
 * renderer keeps refusing them while this target is created, validated and reported. The risky part is the Vulkan resource,
 * and it is proved clean before any pixel depends on it - the render pass and framebuffer arrive with the drawing half, when
 * there is something to render.
 */
public final class VulkanOffscreenTarget {

    private final VulkanContext context;
    private final int width;
    private final int height;
    private final long image;
    private final long memory;
    private final long view;

    private VulkanOffscreenTarget(VulkanContext context, int width, int height, long image, long memory, long view) {
        this.context = context;
        this.width = width;
        this.height = height;
        this.image = image;
        this.memory = memory;
        this.view = view;
    }

    public static VulkanOffscreenTarget create(VulkanContext context, int width, int height) {
        if (context == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("context, width and height must be set");
        }
        VkDevice device = context.device();
        long image = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        long view = VK_NULL_HANDLE;
        try (MemoryStack stack = stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            VkImageCreateInfo createImage = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            createImage.extent().set(width, height, 1);
            if (vkCreateImage(device, createImage, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImage(offscreen target) failed");
            }
            image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, image, requirements);
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(context, requirements.memoryTypeBits()));
            if (vkAllocateMemory(device, allocate, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkAllocateMemory(offscreen target) failed");
            }
            memory = handle.get(0);
            if (vkBindImageMemory(device, image, memory, 0L) != VK_SUCCESS) {
                throw new IllegalStateException("vkBindImageMemory(offscreen target) failed");
            }

            VkImageViewCreateInfo createView = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM);
            createView.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            if (vkCreateImageView(device, createView, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImageView(offscreen target) failed");
            }
            view = handle.get(0);
            return new VulkanOffscreenTarget(context, width, height, image, memory, view);
        } catch (RuntimeException | Error failure) {
            // Nothing half-built is left behind: what exists is retired, and destroyed once the device is idle, exactly as
            // the texture resources are.
            final long imageHandle = image;
            final long memoryHandle = memory;
            final long viewHandle = view;
            VulkanRetireQueue.retire(() -> {
                if (viewHandle != VK_NULL_HANDLE) {
                    vkDestroyImageView(device, viewHandle, null);
                }
                if (imageHandle != VK_NULL_HANDLE) {
                    vkDestroyImage(device, imageHandle, null);
                }
                if (memoryHandle != VK_NULL_HANDLE) {
                    vkFreeMemory(device, memoryHandle, null);
                }
            });
            throw failure;
        }
    }

    /** The one memory type this target needs: device-local, since nothing reads it from the host yet. */
    private static int findMemoryType(VulkanContext context, int typeBits) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(context.physicalDevice(), properties);
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) != 0
                        && (properties.memoryTypes(i).propertyFlags() & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) {
                    return i;
                }
            }
            throw new IllegalStateException("no device-local memory type for the offscreen target");
        }
    }

    public long image() {
        return image;
    }

    public long view() {
        return view;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void delete() {
        VkDevice device = context.device();
        final long imageHandle = image;
        final long memoryHandle = memory;
        final long viewHandle = view;
        VulkanRetireQueue.retire(() -> {
            vkDestroyImageView(device, viewHandle, null);
            vkDestroyImage(device, imageHandle, null);
            vkFreeMemory(device, memoryHandle, null);
        });
    }
}
