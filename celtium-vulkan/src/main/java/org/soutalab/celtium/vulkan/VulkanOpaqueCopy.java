package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

/** A scratch image fixes window alpha without changing the source image or pixels outside the copy. */
final class VulkanOpaqueCopy {
    private static final int FORMAT = VK_FORMAT_R8G8B8A8_UNORM;
    private static final int USAGE = VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    private final VulkanContext context;
    private long setLayout, pipelineLayout, pipeline, sampler;
    private int maxWidth, maxHeight;
    private Scratch scratch;

    private static final class Scratch {
        long image, memory, view, pool, set, sourceImage, sourceView, sourceGeneration;
        int width, height;
    }

    VulkanOpaqueCopy(VulkanContext context) {
        this.context = context;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(context.physicalDevice(), count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(context.physicalDevice(), count, families);
            int family = context.queueFamilyIndex();
            if (family < 0 || family >= count.get(0)
                    || (families.get(family).queueFlags() & VK_QUEUE_COMPUTE_BIT) == 0) {
                throw new IllegalStateException("the graphics queue does not support compute");
            }
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(context.physicalDevice(), FORMAT, properties);
            if ((properties.optimalTilingFeatures() & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) == 0) {
                throw new IllegalStateException("RGBA8 storage images are unsupported");
            }
            VkImageFormatProperties imageProperties = VkImageFormatProperties.calloc(stack);
            check(vkGetPhysicalDeviceImageFormatProperties(context.physicalDevice(), FORMAT, VK_IMAGE_TYPE_2D,
                    VK_IMAGE_TILING_OPTIMAL, USAGE, 0, imageProperties), "query opaque-copy image support");
            this.maxWidth = imageProperties.maxExtent().width();
            this.maxHeight = imageProperties.maxExtent().height();
            createPipeline(stack);
        } catch (RuntimeException | LinkageError error) {
            if (!context.celtium$isDeviceLost()) destroyPipeline();
            throw error;
        }
    }

    private void createPipeline(MemoryStack stack) {
        VkDevice device = this.context.device();
        LongBuffer handle = stack.mallocLong(1);
        VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(2, stack);
        binding.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
        binding.get(1).binding(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
        VkDescriptorSetLayoutCreateInfo setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                .pBindings(binding);
        check(vkCreateDescriptorSetLayout(device, setInfo, null, handle), "create opaque-copy descriptor layout");
        this.setLayout = handle.get(0);
        VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
        range.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(16);
        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                .pSetLayouts(stack.longs(this.setLayout)).pPushConstantRanges(range);
        check(vkCreatePipelineLayout(device, layoutInfo, null, handle), "create opaque-copy pipeline layout");
        this.pipelineLayout = handle.get(0);
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                .magFilter(VK_FILTER_NEAREST).minFilter(VK_FILTER_NEAREST).mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).maxAnisotropy(1.0f).maxLod(0.0f);
        check(vkCreateSampler(device, samplerInfo, null, handle), "create opaque-copy sampler");
        this.sampler = handle.get(0);
        ByteBuffer code = SpirvShaders.load("window_copy_opaque.comp");
        long module = 0L;
        try {
            VkShaderModuleCreateInfo shaderInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            check(vkCreateShaderModule(device, shaderInfo, null, handle), "create opaque-copy shader");
            module = handle.get(0);
            VkComputePipelineCreateInfo.Buffer create = VkComputePipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default().layout(this.pipelineLayout);
            create.get(0).stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module)
                    .pName(stack.UTF8("main"));
            check(vkCreateComputePipelines(device, VK_NULL_HANDLE, create, null, handle),
                    "create opaque-copy pipeline");
            this.pipeline = handle.get(0);
        } finally {
            if (module != 0L && !this.context.celtium$isDeviceLost()) vkDestroyShaderModule(device, module, null);
            MemoryUtil.memFree(code);
        }
    }

    /** Prepare before recording, so an unsupported device or allocation failure can use the existing host route. */
    void prepare(int width, int height, long sourceImage, long sourceView, long sourceGeneration) {
        // Vulkan guarantees at least 65535 workgroups in each dimension; cap at that portable limit so the
        // dispatch count and the +7 rounding below remain valid even for unusually large image limits.
        if (sourceImage == 0L || sourceView == 0L || width <= 0 || height <= 0 || width > this.maxWidth || height > this.maxHeight
                || width > 65535 * 8 || height > 65535 * 8) {
            throw new IllegalArgumentException("unsupported opaque-copy extent " + width + "x" + height);
        }
        if (this.scratch != null && this.scratch.width == width && this.scratch.height == height
                && this.scratch.sourceImage == sourceImage && this.scratch.sourceView == sourceView
                && this.scratch.sourceGeneration == sourceGeneration) return;
        Scratch next = new Scratch();
        next.width = width;
        next.height = height;
        next.sourceImage = sourceImage;
        next.sourceView = sourceView;
        next.sourceGeneration = sourceGeneration;
        VkDevice device = this.context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default().imageType(VK_IMAGE_TYPE_2D)
                    .format(FORMAT).mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).usage(USAGE).sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(width, height, 1);
            check(vkCreateImage(device, imageInfo, null, handle), "create opaque-copy scratch image");
            next.image = handle.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, next.image, requirements);
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size()).memoryTypeIndex(this.context.memoryTypeIndex(
                            requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "opaque-copy image"));
            check(VulkanContext.allocateMemory(device, allocate, handle), "allocate opaque-copy image");
            next.memory = handle.get(0);
            check(vkBindImageMemory(device, next.image, next.memory, 0), "bind opaque-copy image");
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default().image(next.image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D).format(FORMAT);
            viewInfo.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            check(vkCreateImageView(device, viewInfo, null, handle), "create opaque-copy view");
            next.view = handle.get(0);
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(2, stack);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1);
            poolSize.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(1).pPoolSizes(poolSize);
            check(vkCreateDescriptorPool(device, poolInfo, null, handle), "create opaque-copy descriptor pool");
            next.pool = handle.get(0);
            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(next.pool).pSetLayouts(stack.longs(this.setLayout));
            check(vkAllocateDescriptorSets(device, setInfo, handle), "allocate opaque-copy descriptor");
            next.set = handle.get(0);
            VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack);
            image.get(0).imageView(next.view).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer source = VkDescriptorImageInfo.calloc(1, stack);
            source.get(0).imageView(sourceView).sampler(this.sampler).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(2, stack);
            write.get(0).sType$Default().dstSet(next.set).dstBinding(0).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(image);
            write.get(1).sType$Default().dstSet(next.set).dstBinding(1).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(source);
            vkUpdateDescriptorSets(device, write, null);
        } catch (RuntimeException | LinkageError error) {
            if (!this.context.celtium$isDeviceLost()) destroyScratch(next);
            throw error;
        }
        Scratch old = this.scratch;
        this.scratch = next;
        if (old != null) VulkanRetireQueue.retire(() -> destroyScratch(old));
    }

    /** Source is GENERAL and destination is TRANSFER_DST. Ends with scratch in GENERAL. */
    void record(VkCommandBuffer command, long source, int sx, int sy, long destination, int dx, int dy,
            int width, int height, MemoryStack stack) {
        Scratch s = this.scratch;
        if (s == null || s.width != width || s.height != height || s.sourceImage != source) {
            throw new IllegalStateException("scratch or source not ready");
        }
        VkImageMemoryBarrier.Buffer b = VkImageMemoryBarrier.calloc(1, stack).sType$Default()
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(s.image);
        b.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
        // All of the scratch is overwritten. UNDEFINED discards prior content, but prior writes still need a
        // memory dependency: a layout transition can itself access memory even when the old content is discarded.
        b.get(0).oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT);
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                0, null, null, b);
        vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
        vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipelineLayout, 0, stack.longs(s.set), null);
        vkCmdPushConstants(command, this.pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, stack.ints(sx, sy, width, height));
        vkCmdDispatch(command, (width + 7) / 8, (height + 7) / 8, 1);
        b.get(0).oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                0, null, null, b);
        VkImageCopy.Buffer copy = VkImageCopy.calloc(1, stack);
        copy.get(0).srcSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
        copy.get(0).dstSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
        copy.get(0).srcOffset().set(0, 0, 0);
        copy.get(0).dstOffset().set(dx, dy, 0);
        copy.get(0).extent().set(width, height, 1);
        vkCmdCopyImage(command, s.image, VK_IMAGE_LAYOUT_GENERAL, destination, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy);
    }

    /** Called only after the backend has confirmed that its GPU work has completed. */
    void delete() {
        if (this.scratch != null) destroyScratch(this.scratch);
        this.scratch = null;
        destroyPipeline();
    }

    private void destroyScratch(Scratch s) {
        VkDevice device = this.context.device();
        if (s.pool != 0L) vkDestroyDescriptorPool(device, s.pool, null);
        if (s.view != 0L) vkDestroyImageView(device, s.view, null);
        if (s.image != 0L) vkDestroyImage(device, s.image, null);
        if (s.memory != 0L) VulkanContext.freeMemory(device, s.memory);
    }

    private void destroyPipeline() {
        VkDevice device = this.context.device();
        if (this.pipeline != 0L) vkDestroyPipeline(device, this.pipeline, null);
        if (this.sampler != 0L) vkDestroySampler(device, this.sampler, null);
        if (this.pipelineLayout != 0L) vkDestroyPipelineLayout(device, this.pipelineLayout, null);
        if (this.setLayout != 0L) vkDestroyDescriptorSetLayout(device, this.setLayout, null);
        this.pipeline = this.pipelineLayout = this.setLayout = this.sampler = 0L;
    }

    private void check(int result, String what) {
        if (result == VK_ERROR_DEVICE_LOST) throw this.context.celtium$reportDeviceLost(what, result);
        if (result != VK_SUCCESS) throw new IllegalStateException(what + " failed: VkResult " + result);
    }
}
