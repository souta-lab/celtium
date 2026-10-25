package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;

/**
 * A depth-only target for a shadow map: one depth image, a sampler that compares, and a render pass that clears it,
 * renders depth into it and leaves it sampleable.
 * <p>
 * This is the first stage of the native shadow work laid out in {@code docs/plan.md}: the target exists and its pass
 * clears it, which is what the next stages need - a light matrix and a depth pipeline fill it, and the terrain shader
 * samples it. The stage is deliberately separate from the frame's own depth target because that one is sized to the
 * window and cleared per frame for the camera, while a shadow map is a fixed size, rendered from the sun and read by
 * the same pass that writes the camera image.
 */
public final class ShadowMapTarget {

    private final VulkanContext context;
    private final int size;
    private final int format;
    private final long image;
    private final long memory;
    private final long view;
    private final long sampler;
    private final long renderPass;
    private final long framebuffer;
    private boolean deleted;

    public ShadowMapTarget(VulkanContext context, int size) {
        this.context = context;
        this.size = size;
        this.format = VulkanDepthFormat.chosen(context.physicalDevice());
        VkDevice device = context.device();
        try (MemoryStack stack = stackPush()) {
            LongBuffer handle = stack.mallocLong(1);

            VkImageCreateInfo createImage = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D).format(this.format).mipLevels(1).arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            createImage.extent().set(size, size, 1);
            check(vkCreateImage(device, createImage, null, handle), "vkCreateImage(shadow map)");
            this.image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, this.image, requirements);
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits()));
            check(VulkanContext.allocateMemory(device, allocate, handle), "vkAllocateMemory(shadow map)");
            this.memory = handle.get(0);
            check(vkBindImageMemory(device, this.image, this.memory, 0L), "vkBindImageMemory(shadow map)");

            VkImageViewCreateInfo createView = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(this.image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(this.format);
            createView.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            check(vkCreateImageView(device, createView, null, handle), "vkCreateImageView(shadow map)");
            this.view = handle.get(0);

            // The comparison is the point of the sampler: the terrain fragment stage will ask "is this fragment behind
            // what the light saw", which is a depth compare, and the map's own clear value (1.0) makes every fragment
            // lit until something is drawn in front of it.
            VkSamplerCreateInfo createSampler = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK_FILTER_LINEAR).minFilter(VK_FILTER_LINEAR).mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER)
                    .compareEnable(true).compareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                    .minLod(0.0f).maxLod(0.0f).borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE);
            check(vkCreateSampler(device, createSampler, null, handle), "vkCreateSampler(shadow map)");
            this.sampler = handle.get(0);

            VkAttachmentDescription.Buffer attachment = VkAttachmentDescription.calloc(1, stack);
            attachment.get(0).format(this.format).samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            VkAttachmentReference.Buffer depth = VkAttachmentReference.calloc(1, stack);
            depth.get(0).attachment(0).layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS).colorAttachmentCount(0)
                    .pDepthStencilAttachment(depth.get(0));
            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.get(0).srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(0).dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            VkRenderPassCreateInfo createPass = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                    .pAttachments(attachment).pSubpasses(subpass).pDependencies(dependency);
            check(vkCreateRenderPass(device, createPass, null, handle), "vkCreateRenderPass(shadow map)");
            this.renderPass = handle.get(0);

            VkFramebufferCreateInfo createFramebuffer = VkFramebufferCreateInfo.calloc(stack).sType$Default()
                    .renderPass(this.renderPass).width(size).height(size).layers(1);
            createFramebuffer.pAttachments(stack.longs(this.view));
            check(vkCreateFramebuffer(device, createFramebuffer, null, handle), "vkCreateFramebuffer(shadow map)");
            this.framebuffer = handle.get(0);
        }
    }

    public long image() {
        return this.image;
    }

    public long view() {
        return this.view;
    }

    public long sampler() {
        return this.sampler;
    }

    public long renderPass() {
        return this.renderPass;
    }

    public long framebuffer() {
        return this.framebuffer;
    }

    public int size() {
        return this.size;
    }

    public int format() {
        return this.format;
    }

    /** Releases every object this target owns. The caller must have confirmed the device is idle. */
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        VkDevice device = this.context.device();
        vkDestroyFramebuffer(device, this.framebuffer, null);
        vkDestroyRenderPass(device, this.renderPass, null);
        vkDestroySampler(device, this.sampler, null);
        vkDestroyImageView(device, this.view, null);
        vkDestroyImage(device, this.image, null);
        VulkanContext.freeMemory(device, this.memory);
    }

    private int findMemoryType(int typeBits) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(this.context.physicalDevice(), properties);
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) != 0
                        && (properties.memoryTypes(i).propertyFlags() & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) {
                    return i;
                }
            }
            throw new IllegalStateException("no device-local memory type for the shadow map");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result);
        }
    }
}
