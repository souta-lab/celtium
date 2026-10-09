package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * A sampled Vulkan image with its own sampler: the foundation for roadmap stage S5, where every Minecraft texture
 * (block atlas, item atlas, font pages, GUI sheets, entity skins and the light map) has to live on the device
 * instead of in a GL texture.
 * <p>
 * The class owns creation, one upload path and deletion. Uploads go through a staging buffer and a one-shot command
 * buffer from {@link VulkanContext}, which already waits for the queue to drain - the same mechanism the terrain
 * texture mirror uses. Mip chains are generated with blits when requested, and the sampler is created with the
 * filter pair and address mode the caller asks for, so pixel-art atlases and smooth GUI textures can differ.
 * <p>
 * Threading: render thread only.
 */
public final class VulkanTexture2D {
    private final VulkanContext context;
    private final int format;
    private final int width;
    private final int height;

    private long image = VK_NULL_HANDLE;
    private long memory = VK_NULL_HANDLE;
    private long view = VK_NULL_HANDLE;
    private long sampler = VK_NULL_HANDLE;
    private int mipLevels = 1;

    private VulkanTexture2D(VulkanContext context, int format, int width, int height) {
        this.context = context;
        this.format = format;
        this.width = width;
        this.height = height;
    }

    /**
     * Creates a 2D sampled image and uploads {@code pixels} (tightly packed RGBA8, row 0 first) into level 0.
     *
     * @param mipmaps whether a full mip chain should be generated from the uploaded level
     * @param nearest whether magnification and minification use NEAREST (pixel-art atlases) instead of LINEAR
     */
    public static VulkanTexture2D create(VulkanContext context, int width, int height, java.nio.ByteBuffer pixels,
                                         boolean mipmaps, boolean nearest) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("texture size must be positive, was " + width + "x" + height);
        }
        if (pixels == null || pixels.remaining() < width * height * 4) {
            throw new IllegalArgumentException("pixels must hold at least " + (width * height * 4) + " bytes of RGBA8");
        }
        VulkanTexture2D texture = new VulkanTexture2D(context, VK_FORMAT_R8G8B8A8_UNORM, width, height);
        texture.mipLevels = 1;
        if (mipmaps) {
            while ((1 << texture.mipLevels) <= Math.max(width, height)) {
                texture.mipLevels++;
            }
        }
        texture.createImage();
        texture.createView();
        texture.createSampler(nearest);
        texture.upload(pixels, mipmaps);
        return texture;
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

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int mipLevels() {
        return this.mipLevels;
    }

    public int format() {
        return this.format;
    }

    public void delete() {
        if (this.sampler != VK_NULL_HANDLE) {
            vkDestroySampler(this.context.device(), this.sampler, null);
            this.sampler = VK_NULL_HANDLE;
        }
        if (this.view != VK_NULL_HANDLE) {
            vkDestroyImageView(this.context.device(), this.view, null);
            this.view = VK_NULL_HANDLE;
        }
        if (this.image != VK_NULL_HANDLE) {
            vkDestroyImage(this.context.device(), this.image, null);
            this.image = VK_NULL_HANDLE;
        }
        if (this.memory != VK_NULL_HANDLE) {
            vkFreeMemory(this.context.device(), this.memory, null);
            this.memory = VK_NULL_HANDLE;
        }
    }

    private void createImage() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo create = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(this.format)
                    .mipLevels(this.mipLevels)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            create.extent().set(this.width, this.height, 1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateImage(this.context.device(), create, null, handle), "vkCreateImage");
            this.image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(this.context.device(), this.image, requirements);
            int type = this.context.memoryTypeIndex(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    "a " + this.width + "x" + this.height + " texture");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(type);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanTexture2D:150")) {
                throw new IllegalStateException("injected allocation failure: VulkanTexture2D:150");
            }
            check(vkAllocateMemory(this.context.device(), allocate, null, handle), "vkAllocateMemory(texture)");
            this.memory = handle.get(0);
            check(vkBindImageMemory(this.context.device(), this.image, this.memory, 0L), "vkBindImageMemory(texture)");
        }
    }

    private void createView() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo create = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(this.image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(this.format);
            create.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, this.mipLevels, 0, 1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateImageView(this.context.device(), create, null, handle), "vkCreateImageView(texture)");
            this.view = handle.get(0);
        }
    }

    private void createSampler(boolean nearest) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo create = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(nearest ? VK_FILTER_NEAREST : VK_FILTER_LINEAR)
                    .minFilter(nearest ? VK_FILTER_NEAREST : VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .minLod(0.0f)
                    .maxLod((float) this.mipLevels)
                    .compareEnable(false)
                    .anisotropyEnable(false);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateSampler(this.context.device(), create, null, handle), "vkCreateSampler(texture)");
            this.sampler = handle.get(0);
        }
    }

    /** Copies the pixels into a staging buffer, then into level 0, and optionally builds the mip chain. */
    private void upload(java.nio.ByteBuffer pixels, boolean mipmaps) {
        int bytes = this.width * this.height * 4;
        VulkanHostBuffer staging = new VulkanHostBuffer(this.context, bytes, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        try {
            org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(pixels),
                    staging.mappedAddress(), bytes);
            VkCommandBuffer commands = this.context.beginCommands();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                transition(commands, stack, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, 0, VK_ACCESS_TRANSFER_WRITE_BIT);

                VkBufferImageCopy.Buffer copy = VkBufferImageCopy.calloc(1, stack);
                copy.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
                copy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                        .layerCount(1);
                copy.get(0).imageOffset().set(0, 0, 0);
                copy.get(0).imageExtent().set(this.width, this.height, 1);
                vkCmdCopyBufferToImage(commands, staging.buffer(), this.image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy);

                if (mipmaps) {
                    buildMipChain(commands, stack);
                }

                transition(commands, stack, mipmaps ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
                                : VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
            } finally {
                this.context.endCommands(commands);
            }
        } finally {
            staging.delete();
        }
    }

    private void buildMipChain(VkCommandBuffer commands, MemoryStack stack) {
        int levelWidth = this.width;
        int levelHeight = this.height;
        for (int level = 1; level < this.mipLevels; level++) {
            int nextWidth = Math.max(1, levelWidth / 2);
            int nextHeight = Math.max(1, levelHeight / 2);
            if (level == 1) {
                transitionRange(commands, stack, 0, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT,
                        VK_ACCESS_TRANSFER_READ_BIT);
            }
            org.lwjgl.vulkan.VkImageBlit.Buffer blit = org.lwjgl.vulkan.VkImageBlit.calloc(1, stack);
            blit.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level - 1)
                    .baseArrayLayer(0).layerCount(1);
            blit.get(0).srcOffsets().get(0).set(0, 0, 0);
            blit.get(0).srcOffsets().get(1).set(levelWidth, levelHeight, 1);
            blit.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level)
                    .baseArrayLayer(0).layerCount(1);
            blit.get(0).dstOffsets().get(0).set(0, 0, 0);
            blit.get(0).dstOffsets().get(1).set(nextWidth, nextHeight, 1);
            vkCmdBlitImage(commands, this.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, this.image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK_FILTER_LINEAR);
            transitionRange(commands, stack, level, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT);
            levelWidth = nextWidth;
            levelHeight = nextHeight;
        }
    }

    private void transition(VkCommandBuffer commands, MemoryStack stack, int oldLayout, int newLayout,
                            int srcStage, int srcAccess, int dstAccess) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0).sType$Default()
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(this.image);
        barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, this.mipLevels, 0, 1);
        vkCmdPipelineBarrier(commands, srcStage, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
    }

    private void transitionRange(VkCommandBuffer commands, MemoryStack stack, int level, int oldLayout, int newLayout,
                                 int srcAccess, int dstAccess) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0).sType$Default()
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(this.image);
        barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level, 1, 0, 1);
        vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null,
                barrier);
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
