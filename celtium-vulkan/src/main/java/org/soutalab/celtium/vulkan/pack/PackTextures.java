/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.soutalab.celtium.shader.model.TextureType;
import org.soutalab.celtium.shader.shaderpack.texture.CustomTextureData;
import org.soutalab.celtium.shader.shaderpack.texture.TextureFilteringData;
import org.soutalab.celtium.shader.texture.mipmap.CustomMipmapGenerator;
import org.soutalab.celtium.shader.texture.mipmap.PixelImage;
import org.soutalab.celtium.vulkan.VulkanContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK13.*;

/**
 * The executor's own texture model: the upload path {@link TextureType} deliberately does not
 * carry. Pack custom textures (PNG and raw) become Vulkan images here at activation time,
 * through a staging buffer and one-shot submissions; mip chains come from the pack model's
 * {@link CustomMipmapGenerator} contract when a generator applies, never from a GL call.
 * Also owns the samplers the descriptor manager binds and the 1x1 fallback texture used for
 * names the frame environment cannot resolve (atlas, lightmap, noise).
 */
final class PackTextures implements AutoCloseable {

    /** A resolved texture: everything a combined-image-sampler descriptor write needs. */
    record TextureImage(long image, long imageView, long sampler) {
    }

    private static final class OwnedImage {
        long image;
        long view;
        long memory;
    }

    private final VulkanContext context;
    private final PackSubmitter submitter;
    private final List<OwnedImage> ownedImages = new ArrayList<>();
    private final Map<String, Long> samplers = new LinkedHashMap<>();
    private final Map<String, TextureImage> customTextures = new LinkedHashMap<>();
    private TextureImage fallback;

    PackTextures(VulkanContext context, PackSubmitter submitter) {
        this.context = context;
        this.submitter = submitter;
    }

    /** The 1x1 opaque-white texture bound for unresolved texture names. */
    TextureImage fallback() {
        if (fallback == null) {
            fallback = uploadRgba(TextureType.TEXTURE_2D, 1, 1, 1,
                    new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                    VK_FORMAT_R8G8B8A8_UNORM, new TextureFilteringData(false, true), null);
        }
        return fallback;
    }

    /** A sampler for the given filtering shape (cached; owned for the executor's lifetime). */
    long sampler(TextureFilteringData filtering, boolean comparison) {
        String key = (filtering.shouldBlur() ? "linear" : "nearest") + "|"
                + (filtering.shouldClamp() ? "clamp" : "repeat") + "|" + comparison;
        Long existing = samplers.get(key);
        if (existing != null) {
            return existing;
        }
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(filtering.shouldBlur() ? VK_FILTER_LINEAR : VK_FILTER_NEAREST)
                    .minFilter(filtering.shouldBlur() ? VK_FILTER_LINEAR : VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(filtering.shouldClamp()
                            ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE : VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(filtering.shouldClamp()
                            ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE : VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(filtering.shouldClamp()
                            ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE : VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .compareEnable(comparison)
                    .compareOp(comparison ? VK_COMPARE_OP_LESS_OR_EQUAL : VK_COMPARE_OP_ALWAYS)
                    .maxLod(16.0f);
            LongBuffer handle = stack.mallocLong(1);
            int result = vkCreateSampler(device, samplerInfo, null, handle);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateSampler failed: " + result);
            }
            samplers.put(key, handle.get(0));
            return handle.get(0);
        }
    }

    /** Uploads every pack custom texture. Names resolve through {@link #custom(String)}. */
    void loadCustomTextures(Map<String, CustomTextureData> textures) {
        for (Map.Entry<String, CustomTextureData> entry : textures.entrySet()) {
            CustomTextureData data = entry.getValue();
            if (data instanceof CustomTextureData.PngData png) {
                customTextures.put(entry.getKey(), uploadPng(png));
            } else if (data instanceof CustomTextureData.RawData raw) {
                customTextures.put(entry.getKey(), uploadRaw(raw));
            }
            // LightmapMarker and ResourceData are host-resolved (lightmap / resource system);
            // the frame environment answers those names, not the pack model.
        }
    }

    TextureImage custom(String name) {
        return customTextures.get(name);
    }

    private TextureImage uploadPng(CustomTextureData.PngData png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png.getContent()));
            if (image == null) {
                throw new IllegalStateException("pack custom texture is not a readable PNG");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] rgba = new byte[width * height * 4];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    int i = (y * width + x) * 4;
                    rgba[i] = (byte) (argb >>> 16);
                    rgba[i + 1] = (byte) (argb >>> 8);
                    rgba[i + 2] = (byte) argb;
                    rgba[i + 3] = (byte) (argb >>> 24);
                }
            }
            return uploadRgba(TextureType.TEXTURE_2D, width, height, 1, rgba,
                    VK_FORMAT_R8G8B8A8_UNORM, png.getFilteringData(), null);
        } catch (IOException e) {
            throw new IllegalStateException("failed to decode pack custom texture", e);
        }
    }

    private TextureImage uploadRaw(CustomTextureData.RawData raw) {
        // Raw pack textures are uploaded byte-for-byte in their declared internal format; the
        // definition's dimensions live on the matching TextureDefinition, whose sizes the
        // caller folds into the data's content length for the common RGBA8 case. Only the
        // 2D RGBA-family shapes are materialised here; exotic raw shapes resolve to the
        // fallback rather than a malformed image.
        int vkFormat = raw.getInternalFormat().getVkFormat();
        byte[] content = raw.getContent();
        int bytesPerPixel = 4;
        int pixels = content.length / bytesPerPixel;
        int side = (int) Math.sqrt(pixels);
        if (side * side != pixels || vkFormat != VK_FORMAT_R8G8B8A8_UNORM
                && vkFormat != VK_FORMAT_R8G8B8A8_SRGB) {
            return fallback();
        }
        return uploadRgba(TextureType.TEXTURE_2D, side, side, 1, content, vkFormat,
                raw.getFilteringData(), null);
    }

    /**
     * The {@link TextureType} upload path: 1D/2D/3D/rectangle map to the matching Vulkan image
     * and view types (rectangle is a plain 2D image in Vulkan), pixels are staged and copied,
     * and the image is left in SHADER_READ_ONLY_OPTIMAL.
     */
    TextureImage uploadRgba(TextureType type, int width, int height, int depth, byte[] rgba,
                            int vkFormat, TextureFilteringData filtering,
                            CustomMipmapGenerator mipmapGenerator) {
        VkDevice device = context.device();
        int imageType = switch (type) {
            case TEXTURE_1D -> VK_IMAGE_TYPE_1D;
            case TEXTURE_2D, TEXTURE_RECTANGLE -> VK_IMAGE_TYPE_2D;
            case TEXTURE_3D -> VK_IMAGE_TYPE_3D;
        };
        int viewType = switch (type) {
            case TEXTURE_1D -> VK_IMAGE_VIEW_TYPE_1D;
            case TEXTURE_2D, TEXTURE_RECTANGLE -> VK_IMAGE_VIEW_TYPE_2D;
            case TEXTURE_3D -> VK_IMAGE_VIEW_TYPE_3D;
        };

        PixelImage[] levels = null;
        int mipLevels = 1;
        if (mipmapGenerator != null && width > 1 && height > 1) {
            PixelImage base = toPixelImage(rgba, width, height);
            int maxLevel = 31 - Integer.numberOfLeadingZeros(Math.min(width, height));
            levels = mipmapGenerator.generateMipLevels(base, maxLevel);
            mipLevels = levels.length;
        }

        OwnedImage owned = new OwnedImage();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(imageType)
                    .format(vkFormat)
                    .mipLevels(mipLevels)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().width(width).height(height).depth(Math.max(depth, 1));
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateImage(device, imageInfo, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImage for pack texture failed");
            }
            owned.image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, owned.image, requirements);
            int memoryType = context.memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "pack custom texture");
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            if (vkAllocateMemory(device, allocInfo, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkAllocateMemory for pack texture failed");
            }
            owned.memory = handle.get(0);
            vkBindImageMemory(device, owned.image, owned.memory, 0L);

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(owned.image)
                    .viewType(viewType)
                    .format(vkFormat);
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0).layerCount(1);
            if (vkCreateImageView(device, viewInfo, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImageView for pack texture failed");
            }
            owned.view = handle.get(0);
        }
        ownedImages.add(owned);

        long totalBytes = 0L;
        List<byte[]> levelBytes = new ArrayList<>();
        if (levels != null) {
            for (PixelImage level : levels) {
                byte[] bytes = fromPixelImage(level);
                levelBytes.add(bytes);
                totalBytes += bytes.length;
            }
        } else {
            levelBytes.add(rgba);
            totalBytes = rgba.length;
        }
        PackGpuBuffer staging = new PackGpuBuffer(context.physicalDevice(), device,
                Math.max(totalBytes, 4L), PackGpuBuffer.USAGE_TRANSFER_SRC);
        try {
            long offset = 0L;
            for (byte[] bytes : levelBytes) {
                staging.mapped().asSlice(offset, bytes.length)
                        .copyFrom(java.lang.foreign.MemorySegment.ofArray(bytes));
                offset += bytes.length;
            }
            int finalMipLevels = mipLevels;
            List<byte[]> finalLevels = levelBytes;
            submitter.submitAndWait("pack texture upload", cmd -> {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
                    barrier.get(0)
                            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                            .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                            .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .image(owned.image)
                            .srcAccessMask(0)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    barrier.get(0).subresourceRange()
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0).levelCount(finalMipLevels)
                            .baseArrayLayer(0).layerCount(1);
                    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);

                    long bufferOffset = 0L;
                    for (int level = 0; level < finalLevels.size(); level++) {
                        VkBufferImageCopy.Buffer regions = VkBufferImageCopy.calloc(1, stack);
                        regions.get(0)
                                .bufferOffset(bufferOffset)
                                .bufferRowLength(0)
                                .bufferImageHeight(0);
                        regions.get(0).imageSubresource()
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(level)
                                .baseArrayLayer(0)
                                .layerCount(1);
                        regions.get(0).imageExtent()
                                .width(Math.max(1, width >> level))
                                .height(Math.max(1, height >> level))
                                .depth(1);
                        vkCmdCopyBufferToImage(cmd, staging.bufferHandle(), owned.image,
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, regions);
                        bufferOffset += finalLevels.get(level).length;
                    }

                    barrier.get(0)
                            .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                            .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);
                }
            });
        } finally {
            staging.close();
        }
        return new TextureImage(owned.image, owned.view, sampler(filtering, false));
    }

    private static PixelImage toPixelImage(byte[] rgba, int width, int height) {
        PixelImage image = new PixelImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 4;
                int value = ((rgba[i + 3] & 0xFF) << 24) | ((rgba[i] & 0xFF) << 16)
                        | ((rgba[i + 1] & 0xFF) << 8) | (rgba[i + 2] & 0xFF);
                image.setPixelRGBA(x, y, value);
            }
        }
        return image;
    }

    private static byte[] fromPixelImage(PixelImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] rgba = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = image.getPixelRGBA(x, y);
                int i = (y * width + x) * 4;
                rgba[i] = (byte) (value >>> 16);
                rgba[i + 1] = (byte) (value >>> 8);
                rgba[i + 2] = (byte) value;
                rgba[i + 3] = (byte) (value >>> 24);
            }
        }
        return rgba;
    }

    @Override
    public void close() {
        VkDevice device = context.device();
        for (long sampler : samplers.values()) {
            vkDestroySampler(device, sampler, null);
        }
        samplers.clear();
        for (OwnedImage owned : ownedImages) {
            if (owned.view != 0L) {
                vkDestroyImageView(device, owned.view, null);
            }
            if (owned.image != 0L) {
                vkDestroyImage(device, owned.image, null);
            }
            if (owned.memory != 0L) {
                vkFreeMemory(device, owned.memory, null);
            }
        }
        ownedImages.clear();
    }
}
