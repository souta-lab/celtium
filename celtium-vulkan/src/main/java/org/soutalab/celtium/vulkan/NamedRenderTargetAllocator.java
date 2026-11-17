package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE;
import static org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;

/**
 * Allocates Vulkan images, views and samplers for named targets declared in {@link RenderTargetRegistry} and publishes
 * them into {@link PublishedFrameTextures}. Does nothing when no named targets are declared.
 */
final class NamedRenderTargetAllocator {

    private static final class Allocation {
        final RenderTargetRegistry.NamedTargetSpec spec;
        final long image;
        final long memory;
        final long view;
        final long sampler;
        final int width;
        final int height;

        Allocation(RenderTargetRegistry.NamedTargetSpec spec, long image, long memory, long view, long sampler,
                   int width, int height) {
            this.spec = spec;
            this.image = image;
            this.memory = memory;
            this.view = view;
            this.sampler = sampler;
            this.width = width;
            this.height = height;
        }
    }

    private final VulkanContext context;
    private final RenderTargetRegistry registry;
    private final PublishedFrameTextures published;
    private final Map<String, Allocation> allocations = new LinkedHashMap<>();

    NamedRenderTargetAllocator(VulkanContext context, RenderTargetRegistry registry, PublishedFrameTextures published) {
        this.context = context;
        this.registry = registry;
        this.published = published;
    }

    /** Ensures every declared named target has a Vulkan allocation matching {@code frameWidth x frameHeight}. */
    void ensureAllocated(int frameWidth, int frameHeight) {
        if (this.registry.namedTargetCount() == 0) {
            if (!this.allocations.isEmpty()) {
                delete();
            }
            return;
        }
        if (this.context == null || this.context.device() == null || frameWidth <= 0 || frameHeight <= 0) {
            return;
        }

        Iterator<Map.Entry<String, Allocation>> it = this.allocations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Allocation> entry = it.next();
            if (!this.registry.hasNamedTarget(entry.getKey())) {
                destroyAllocation(entry.getValue());
                this.registry.forgetNamedTargetAllocation(entry.getKey());
                it.remove();
            }
        }

        for (RenderTargetRegistry.NamedTargetSpec spec : this.registry.namedTargets()) {
            int targetWidth = spec.sizing().resolveWidth(frameWidth);
            int targetHeight = spec.sizing().resolveHeight(frameHeight);
            Allocation current = this.allocations.get(spec.name());
            if (current != null && current.width == targetWidth && current.height == targetHeight
                    && current.spec.equals(spec)) {
                continue;
            }
            if (current != null) {
                destroyAllocation(current);
                this.allocations.remove(spec.name());
                this.registry.forgetNamedTargetAllocation(spec.name());
            }
            Allocation created = createAllocation(spec, targetWidth, targetHeight);
            this.allocations.put(spec.name(), created);
            this.registry.noteNamedTargetAllocated(spec.name(), created.image, created.view, created.sampler,
                    created.width, created.height, VK_IMAGE_LAYOUT_UNDEFINED);
        }
        this.published.syncNamedTargets(this.registry);
    }

    private Allocation createAllocation(RenderTargetRegistry.NamedTargetSpec spec, int width, int height) {
        VkDevice device = this.context.device();
        boolean depth = spec.kind().isDepth();
        int format = spec.formatClass().vkFormat();
        int usage = (depth ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

        long image = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        long view = VK_NULL_HANDLE;
        long sampler = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(width, height, 1);
            check(vkCreateImage(device, imageInfo, null, handle), "vkCreateImage(named target " + spec.name() + ")");
            image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, requirements);
            int memoryType = this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "named target " + spec.name());
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            check(vkAllocateMemory(device, allocInfo, null, handle), "vkAllocateMemory(named target " + spec.name() + ")");
            memory = handle.get(0);
            check(vkBindImageMemory(device, image, memory, 0L), "vkBindImageMemory(named target " + spec.name() + ")");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format);
            viewInfo.subresourceRange()
                    .aspectMask(depth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            check(vkCreateImageView(device, viewInfo, null, handle),
                    "vkCreateImageView(named target " + spec.name() + ")");
            view = handle.get(0);

            boolean compare = spec.kind().usesComparisonSampler();
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .compareEnable(compare)
                    .compareOp(compare ? VK_COMPARE_OP_LESS_OR_EQUAL : VK_COMPARE_OP_LESS_OR_EQUAL)
                    .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
                    .minLod(0.0f)
                    .maxLod(0.0f);
            check(vkCreateSampler(device, samplerInfo, null, handle),
                    "vkCreateSampler(named target " + spec.name() + ")");
            sampler = handle.get(0);
            return new Allocation(spec, image, memory, view, sampler, width, height);
        } catch (RuntimeException | LinkageError failure) {
            if (sampler != VK_NULL_HANDLE) {
                vkDestroySampler(device, sampler, null);
            }
            if (view != VK_NULL_HANDLE) {
                vkDestroyImageView(device, view, null);
            }
            if (image != VK_NULL_HANDLE) {
                vkDestroyImage(device, image, null);
            }
            if (memory != VK_NULL_HANDLE) {
                vkFreeMemory(device, memory, null);
            }
            throw failure;
        }
    }

    void delete() {
        for (Allocation allocation : this.allocations.values()) {
            destroyAllocation(allocation);
            this.registry.forgetNamedTargetAllocation(allocation.spec.name());
        }
        this.allocations.clear();
        this.published.clearNamedPublished();
    }

    private void destroyAllocation(Allocation allocation) {
        if (this.context == null || this.context.device() == null) {
            return;
        }
        VkDevice device = this.context.device();
        if (allocation.sampler != VK_NULL_HANDLE) {
            vkDestroySampler(device, allocation.sampler, null);
        }
        if (allocation.view != VK_NULL_HANDLE) {
            vkDestroyImageView(device, allocation.view, null);
        }
        if (allocation.image != VK_NULL_HANDLE) {
            vkDestroyImage(device, allocation.image, null);
        }
        if (allocation.memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, allocation.memory, null);
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
