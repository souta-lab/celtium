package org.soutalab.celtium.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/**
 * One Vulkan sampled image that mirrors a Minecraft OpenGL texture (block atlas, lightmap).
 * <p>
 * <b>Why a copy.</b> The GL textures are created and owned by Minecraft, and neither OpenGL nor its
 * {@code EXT_memory_object} extensions offer a way to export an existing texture's storage: the extension can only
 * import memory that was created elsewhere, not hand out the storage of a texture GL already allocated. The only
 * route is therefore to read the pixels back from GL and upload them into a Vulkan image of our own, exactly as
 * this class does. This is a per-refresh copy, not a zero-copy import: every upload costs a GPU to CPU readback of
 * the whole level and a CPU to GPU transfer, so it must only run when the texture content actually changed
 * (resource reload / texture atlas refresh), never per frame.
 * <p>
 * <b>What is mirrored.</b> Only mip level 0 is copied. Minecraft's atlas has a mip chain, but this image has a
 * single level and the sampler clamps the LOD to 0, so terrain always samples the base level. That is a deliberate
 * simplification of the first mirroring implementation, not an accident.
 * <p>
 * <b>Layout invariant.</b> The image is in {@code VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL} at rest: it is put there
 * when it is created and again at the end of every upload, so a descriptor written by
 * {@code VulkanTerrainPipeline.bindTextures} is valid as soon as {@link #create} or {@link #uploadFromGlTexture}
 * returns.
 * <p>
 * <b>Threading.</b> All methods run on the render thread. {@link #uploadFromGlTexture} additionally requires the
 * current GL context (it issues GL calls). Because {@link #create} and a size-changing upload destroy device
 * objects, the caller must ensure no submitted frame is still reading the previous image when calling them; the
 * backend does this at texture refresh time, where the surrounding frame boundary already guarantees it.
 * <p>
 * <b>API surface used from the sibling classes in this package.</b>
 * <ul>
 *   <li>{@code VulkanContext.device()} - the {@code VkDevice} for every object created and for the memory mapping.</li>
 *   <li>{@code VulkanContext.physicalDevice()} - queried for format support, the maximum image dimension and memory
 *       types (the context does not expose memory type selection).</li>
 *   <li>{@code VulkanContext.beginCommands()} - a one-shot command buffer for the layout transitions and the copy.</li>
 *   <li>{@code VulkanContext.endCommands(VkCommandBuffer)} - submits that command buffer and waits for it. This
 *       class assumes the helper does not return before the work has completed; that is what makes the layout
 *       invariant and the staging buffer reuse safe.</li>
 * </ul>
 * {@code VulkanContext.queue()}, {@code queueFamilyIndex()} and {@code uploadToBuffer(...)} are deliberately unused:
 * the staging buffer is mapped here so the pixels can be copied without an intermediate Java array, and the image is
 * only ever used by the same queue family, so barriers use {@code VK_QUEUE_FAMILY_IGNORED} instead of an ownership
 * transfer.
 */
public final class VulkanGlTexture {
    /**
     * The mirror image format. R8G8B8A8_UNORM is what the GL readback is converted to (see
     * {@link #readGlLevel0}) and what the interop bootstrap already proved importable on both APIs.
     */
    private static final int IMAGE_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    private static final int BYTES_PER_PIXEL = 4;

    /**
     * The shader stages that may sample the mirrored image. Both are listed because the descriptor set layout
     * declares the samplers for vertex and fragment use, and an image barrier has to cover every stage that reads
     * the image; naming a stage that never samples it is harmless.
     */
    private static final int SAMPLING_STAGES =
            VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;

    private final VulkanContext context;

    private int width;
    private int height;
    private long image;
    private long memory;
    private long view;
    private long sampler;

    private long stagingBuffer;
    private long stagingMemory;
    /** Host address of the persistently mapped staging buffer, or 0 while it is not mapped. */
    private long stagingAddress;
    private long stagingCapacity;
    private boolean stagingCoherent;

    private boolean deleted;

    private VulkanGlTexture(VulkanContext context) {
        this.context = context;
    }

    /**
     * Creates an empty image of the given size, its device local memory, an image view and a sampler.
     * <p>
     * The image starts out in {@code VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL} with undefined contents, so it can be
     * bound to a descriptor before the first upload without violating the layout rules. The sampler uses linear
     * filtering with linear mipmap mode, clamps to the edge on all three axes and has anisotropy and comparison
     * sampling disabled, mirroring the sampler state Minecraft uses for terrain textures.
     *
     * @param context the live Vulkan device wrapper
     * @param width   initial image width in pixels; the first upload resizes the image if the GL texture differs
     * @param height  initial image height in pixels
     * @return a created texture; delete it with {@link #delete()}
     * @throws IllegalArgumentException if the size is not positive or exceeds the device limit
     * @throws IllegalStateException    if the device cannot sample this format with linear filtering, or if Vulkan
     *                                  object creation fails
     */
    public static VulkanGlTexture create(VulkanContext context, int width, int height) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        VulkanGlTexture result = new VulkanGlTexture(context);
        try {
            result.createSampler();
            result.createImageResources(width, height);
            return result;
        } catch (RuntimeException | LinkageError error) {
            // Release whatever was already created so a failed create() cannot leak device objects.
            result.delete();
            throw error;
        }
    }

    /**
     * Copies mip level 0 of a GL texture into this Vulkan image.
     * <p>
     * The GL texture is queried and read with the GL context current: its level-0 size and internal format come from
     * {@code glGetTexLevelParameteri}, the pixels from {@code glGetTexImage} converted to RGBA bytes (GL performs the
     * conversion, so no per-internal-format table is needed and every colour texture comes back as tightly packed
     * RGBA8). The pixels are then memcpy'd into a persistently mapped host visible staging buffer and copied into the
     * image with {@code vkCmdCopyBufferToImage}, bracketed by image memory barriers
     * ({@code SHADER_READ_ONLY_OPTIMAL} to {@code TRANSFER_DST_OPTIMAL} and back). The one-shot command buffer is
     * submitted and waited for.
     * <p>
     * If the GL texture's level-0 size differs from the current image size, the image, its memory and its view are
     * destroyed and recreated at the new size before the copy: the mirror always matches the GL texture exactly
     * instead of being stretched or partially written. A size change is a full device object replacement, so it is
     * expensive and only expected when the atlas is rebuilt - and because it also replaces the image view handle,
     * any descriptor set written with the old {@link #view()} has to be written again afterwards (call
     * {@code VulkanTerrainPipeline.bindTextures} after the upload, not before it).
     * <p>
     * Every piece of GL state this touches is restored before returning: the pack alignment, the 2D binding of the
     * texture unit that was active on entry, and the active texture unit itself. Restoring the exact previous
     * binding matters because Minecraft caches per-unit texture bindings - leaving a different texture bound here
     * would make the cache lie and Minecraft would skip a rebind it actually needs.
     *
     * @param glTextureId     the GL name of the source texture
     * @param glTextureTarget the GL target, which must be {@code GL_TEXTURE_2D}
     * @throws IllegalArgumentException if the target is not {@code GL_TEXTURE_2D} or the id is 0
     * @throws IllegalStateException    if the GL texture has no level-0 storage, a GL call fails, or this texture was
     *                                  deleted
     */
    public void uploadFromGlTexture(int glTextureId, int glTextureTarget) {
        requireAlive();
        GlLevel level = readGlLevel0(glTextureId, glTextureTarget);
        try {
            dumpForDebugging(glTextureId, level);
            if (level.width != width || level.height != height) {
                recreateImageResources(level.width, level.height);
            }
            copyToImage(level.pixels, level.width, level.height);
        } finally {
            MemoryUtil.memFree(level.pixels);
        }
    }

    /**
     * The native {@code VkImageView} handle of this texture, for {@code VkDescriptorImageInfo}.
     * <p>
     * The handle is a plain {@code long} because LWJGL 3.4 removed the non-dispatchable Vulkan handle wrapper
     * classes ({@code VkImageView}, {@code VkSampler}, ...); only dispatchable handles such as {@code VkDevice}
     * still have Java types. It stays valid until {@link #delete()} or until an upload resizes the image (a resize
     * creates a new view, so any descriptor holding the old handle must be rewritten by calling
     * {@code VulkanTerrainPipeline.bindTextures} again).
     *
     * @return the image view handle
     */
    public long view() {
        return view;
    }

    /**
     * The native {@code VkSampler} handle of this texture, for {@code VkDescriptorImageInfo}. See {@link #view()}
     * for why the handle is a {@code long}. It stays valid until {@link #delete()}.
     *
     * @return the sampler handle
     */
    public long sampler() {
        return sampler;
    }

    /**
     * The current image width. Before the first upload this is the size passed to {@link #create}; afterwards it is
     * the level-0 width of the GL texture that was uploaded.
     *
     * @return the image width in pixels
     */
    public int width() {
        return width;
    }

    /**
     * The current image height. See {@link #width()} for the value before the first upload.
     *
     * @return the image height in pixels
     */
    public int height() {
        return height;
    }

    /**
     * Destroys the image view, the image, its memory, the sampler and the staging buffer.
     * <p>
     * Idempotent: a second call, or a call after a partially failed {@link #create}, does nothing. The caller must
     * ensure the device is idle with respect to this texture before deleting it, and that no descriptor set still
     * references the handles.
     */
    public void delete() {
        if (deleted) {
            return;
        }
        deleted = true;
        destroyStaging();
        destroyImageResources();
        if (sampler != 0L) {
            vkDestroySampler(context.device(), sampler, null);
            sampler = 0L;
        }
        width = 0;
        height = 0;
    }

    private void createImageResources(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
            throw new IllegalArgumentException("A mirrored texture size of " + newWidth + "x" + newHeight
                    + " pixels is not usable");
        }
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            int maxDimension = properties.limits().maxImageDimension2D();
            if (newWidth > maxDimension || newHeight > maxDimension) {
                throw new IllegalArgumentException("A mirrored texture of " + newWidth + "x" + newHeight
                        + " pixels exceeds the device limit of " + maxDimension + " per dimension");
            }
            int requiredFeatures = VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT
                    | VK_FORMAT_FEATURE_TRANSFER_DST_BIT
                    | VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
            VkFormatProperties features = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(context.physicalDevice(), IMAGE_FORMAT, features);
            if ((features.optimalTilingFeatures() & requiredFeatures) != requiredFeatures) {
                throw new IllegalStateException("Vulkan format " + IMAGE_FORMAT + " cannot be sampled with linear"
                        + " filtering on this device, so terrain textures cannot be mirrored");
            }

            VkImageCreateInfo createImage = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(IMAGE_FORMAT)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            createImage.extent().set(newWidth, newHeight, 1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateImage(device, createImage, null, handle), "vkCreateImage(mirrored texture)");
            image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, requirements);
            int memoryType = findMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "image memory");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            check(vkAllocateMemory(device, allocate, null, handle), "vkAllocateMemory(mirrored texture)");
            memory = handle.get(0);
            check(vkBindImageMemory(device, image, memory, 0L), "vkBindImageMemory(mirrored texture)");

            VkImageViewCreateInfo createView = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(IMAGE_FORMAT);
            createView.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            check(vkCreateImageView(device, createView, null, handle), "vkCreateImageView(mirrored texture)");
            view = handle.get(0);
        }
        this.width = newWidth;
        this.height = newHeight;
        // Make the resting layout true for a brand new image too, so a descriptor bound before the first upload is
        // already valid. The contents stay undefined until the first upload, which is correct for a fresh mirror.
        transitionToShaderRead();
    }

    private void createSampler() {
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo create = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .mipLodBias(0.0f)
                    .anisotropyEnable(false)
                    // Ignored while anisotropy is disabled, but set to the neutral value so the struct never
                    // contains the zero that calloc leaves behind.
                    .maxAnisotropy(1.0f)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_NEVER)
                    .minLod(0.0f)
                    // The image has exactly one level, so clamping the LOD to 0 is what keeps implicit-derivative
                    // sampling inside the image. The GL mip chain is deliberately not mirrored.
                    .maxLod(0.0f)
                    .borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK)
                    .unnormalizedCoordinates(false);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateSampler(device, create, null, handle), "vkCreateSampler(mirrored texture)");
            sampler = handle.get(0);
        }
    }

    private void recreateImageResources(int newWidth, int newHeight) {
        destroyImageResources();
        createImageResources(newWidth, newHeight);
    }

    private void destroyImageResources() {
        VkDevice device = context.device();
        if (view != 0L) {
            vkDestroyImageView(device, view, null);
            view = 0L;
        }
        if (image != 0L) {
            vkDestroyImage(device, image, null);
            image = 0L;
        }
        if (memory != 0L) {
            vkFreeMemory(device, memory, null);
            memory = 0L;
        }
    }

    private void transitionToShaderRead() {
        VkCommandBuffer commandBuffer = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    // The contents are undefined, so nothing has to be made available: only the layout changes.
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    // The image is only used by this device and never handed to another queue family, so no
                    // ownership transfer is needed.
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, SAMPLING_STAGES,
                    0, null, null, barrier);
        } finally {
            context.endCommands(commandBuffer);
        }
    }

    /**
     * Writes the mirrored pixels to a PNG when {@code -Dceltium.vulkan.dumpTextures=<dir>} is set. Used while
     * bringing the backend up: comparing what GL hands over with what the shader sees separates an upload problem
     * from a sampling problem. One file per texture per run.
     */
    private void dumpForDebugging(int glTextureId, GlLevel level) {
        String directory = System.getProperty("celtium.vulkan.dumpTextures");
        if (directory == null || directory.isEmpty()) {
            return;
        }
        try {
            java.io.File target = new java.io.File(directory,
                    "gl-texture-" + glTextureId + "-" + level.width + "x" + level.height + ".png");
            if (target.isFile()) {
                return;
            }
            target.getParentFile().mkdirs();
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(level.width, level.height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < level.height; y++) {
                for (int x = 0; x < level.width; x++) {
                    int offset = ((y * level.width) + x) * BYTES_PER_PIXEL;
                    int r = level.pixels.get(offset) & 0xFF;
                    int g = level.pixels.get(offset + 1) & 0xFF;
                    int b = level.pixels.get(offset + 2) & 0xFF;
                    int a = level.pixels.get(offset + 3) & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            javax.imageio.ImageIO.write(image, "png", target);
        } catch (Throwable ignored) {
            // Debug-only path: never let a dump failure affect rendering.
        }
    }

    private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight) {
        long bytes = (long) pixelWidth * (long) pixelHeight * BYTES_PER_PIXEL;
        ensureStaging(bytes);
        // The staging buffer is host visible and persistently mapped, so the pixels go straight from the readback
        // buffer into device visible memory without an intermediate Java array.
        MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), stagingAddress, bytes);
        if (!stagingCoherent) {
            // Non-coherent host memory needs an explicit flush before the copy; coherent memory (the common case)
            // is made available to the device by the queue submission itself.
            flushStaging();
        }

        VkCommandBuffer commandBuffer = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(commandBuffer, SAMPLING_STAGES, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier);

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            // A row length of 0 means the rows are tightly packed, which is exactly how GL returned them.
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().set(pixelWidth, pixelHeight, 1);
            vkCmdCopyBufferToImage(commandBuffer, stagingBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            // Reuse the same barrier struct for the way back: the image and subresource range are unchanged, only
            // the access masks and layouts differ.
            barrier.get(0)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, SAMPLING_STAGES,
                    0, null, null, barrier);
        } finally {
            // Always close the one-shot command buffer, also when recording threw half way through, so the
            // context's helper never keeps an open command buffer around.
            context.endCommands(commandBuffer);
        }
    }

    private void ensureStaging(long required) {
        if (stagingBuffer != 0L && stagingCapacity >= required) {
            return;
        }
        destroyStaging();
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo create = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(required)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(device, create, null, handle), "vkCreateBuffer(staging)");
            stagingBuffer = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, requirements);
            int memoryType = findMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, "staging memory");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            check(vkAllocateMemory(device, allocate, null, handle), "vkAllocateMemory(staging)");
            stagingMemory = handle.get(0);
            check(vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0L), "vkBindBufferMemory(staging)");

            // Map once and keep the mapping: a stale mapping would be a bug, and remapping per upload is pure
            // overhead for a buffer that is reused for every refresh.
            PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(device, stagingMemory, 0L, VK_WHOLE_SIZE, 0, pointer), "vkMapMemory(staging)");
            stagingAddress = pointer.get(0);
            stagingCapacity = requirements.size();
            stagingCoherent = isHostCoherent(memoryType);
        }
    }

    private void destroyStaging() {
        VkDevice device = context.device();
        if (stagingAddress != 0L && stagingMemory != 0L) {
            vkUnmapMemory(device, stagingMemory);
            stagingAddress = 0L;
        }
        if (stagingBuffer != 0L) {
            vkDestroyBuffer(device, stagingBuffer, null);
            stagingBuffer = 0L;
        }
        if (stagingMemory != 0L) {
            vkFreeMemory(device, stagingMemory, null);
            stagingMemory = 0L;
        }
        stagingCapacity = 0L;
        stagingCoherent = false;
    }

    private void flushStaging() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMappedMemoryRange.Buffer range = VkMappedMemoryRange.calloc(1, stack);
            // VK_WHOLE_SIZE flushes the whole mapped range, which avoids having to round the written length up to
            // nonCoherentAtomSize (flushing more than was written is always allowed).
            range.get(0).sType$Default().memory(stagingMemory).offset(0L).size(VK_WHOLE_SIZE);
            check(vkFlushMappedMemoryRanges(context.device(), range), "vkFlushMappedMemoryRanges(staging)");
        }
    }

    private boolean isHostCoherent(int memoryTypeIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(context.physicalDevice(), properties);
            return (properties.memoryTypes(memoryTypeIndex).propertyFlags()
                    & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0;
        }
    }

    /**
     * Picks a memory type index for the given requirement mask.
     *
     * @param typeBits      the {@code memoryTypeBits} reported by {@code vkGetImageMemoryRequirements} or
     *                      {@code vkGetBufferMemoryRequirements}
     * @param required      properties the type must have
     * @param preferred     properties that are preferred but not required
     * @param what          a description used in the error message
     * @return the index of a suitable memory type
     * @throws IllegalStateException if no type satisfies the required properties
     */
    private int findMemoryType(int typeBits, int required, int preferred, String what) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(context.physicalDevice(), properties);
            int fallback = -1;
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) == 0) {
                    continue;
                }
                int flags = properties.memoryTypes(i).propertyFlags();
                if ((flags & required) != required) {
                    continue;
                }
                if ((flags & preferred) == preferred) {
                    return i;
                }
                if (fallback < 0) {
                    fallback = i;
                }
            }
            if (fallback < 0) {
                throw new IllegalStateException("No Vulkan memory type for " + what + " satisfies 0x"
                        + Integer.toHexString(required) + " within the allowed mask 0x"
                        + Integer.toHexString(typeBits));
            }
            return fallback;
        }
    }

    /**
     * Reads level 0 of a GL texture into a freshly allocated direct buffer, restoring all GL state it touches.
     *
     * @return the pixel buffer (the caller frees it with {@link MemoryUtil#memFree(ByteBuffer)}) plus the source size
     */
    private static GlLevel readGlLevel0(int glTextureId, int glTextureTarget) {
        if (glTextureTarget != GL_TEXTURE_2D) {
            throw new IllegalArgumentException("Only GL_TEXTURE_2D can be mirrored, but the caller passed target 0x"
                    + Integer.toHexString(glTextureTarget) + "; a cube map or array texture would need one copy"
                    + " per layer");
        }
        if (glTextureId == 0) {
            throw new IllegalArgumentException("GL texture name 0 is not a texture");
        }
        // The unit that is active on entry is reused instead of switching to unit 0, so the state that changes is
        // as small as possible. Whatever unit it is, its binding and the pack alignment are restored below.
        int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        int previousBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousPackAlignment = glGetInteger(GL_PACK_ALIGNMENT);
        // Discard errors left behind by earlier GL work so that the checks below can only report failures caused by
        // this method. Nothing is lost: Minecraft does not read the GL error state.
        while (glGetError() != GL_NO_ERROR) {
            // Drain the error queue.
        }
        ByteBuffer pixels = null;
        try {
            // The classic bind-and-query path is used instead of GL 4.5's glGetTextureLevelParameteri /
            // glGetTextureImage: direct state access needs ARB_direct_state_access, and Minecraft's context is only
            // guaranteed to be a compatibility context, so the query that works everywhere wins over the shorter one.
            glBindTexture(GL_TEXTURE_2D, glTextureId);
            int textureWidth = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
            int textureHeight = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
            int internalFormat = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT);
            checkGl("querying GL texture " + glTextureId);
            if (textureWidth <= 0 || textureHeight <= 0) {
                throw new IllegalStateException("GL texture " + glTextureId + " has no level-0 storage ("
                        + textureWidth + "x" + textureHeight + ", internal format 0x"
                        + Integer.toHexString(internalFormat) + ")");
            }
            long bytes = (long) textureWidth * (long) textureHeight * BYTES_PER_PIXEL;
            if (bytes > Integer.MAX_VALUE) {
                throw new IllegalStateException("GL texture " + glTextureId + " is too large to mirror ("
                        + bytes + " bytes)");
            }
            pixels = MemoryUtil.memAlloc((int) bytes);
            // Rows of RGBA bytes are always a multiple of 4 bytes, but a pack alignment of 1 removes the last way
            // a driver could pad the readback and is the value the tightly packed Vulkan copy assumes.
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            // The internal format is part of the message because GL only permits this conversion for colour
            // formats; an integer or depth texture would be rejected here and the format identifies it.
            checkGl("reading level 0 of GL texture " + glTextureId + " as RGBA bytes (internal format 0x"
                    + Integer.toHexString(internalFormat) + ")");
            return new GlLevel(pixels, textureWidth, textureHeight);
        } catch (RuntimeException | Error error) {
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }
            throw error;
        } finally {
            // GL state is per unit, so the binding of the unit we used has to be restored before the active unit
            // itself is put back.
            glPixelStorei(GL_PACK_ALIGNMENT, previousPackAlignment);
            glBindTexture(GL_TEXTURE_2D, previousBinding);
            glActiveTexture(previousUnit);
        }
    }

    private static void checkGl(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            throw new IllegalStateException(operation + " failed with GL error 0x" + Integer.toHexString(error));
        }
    }

    private void requireAlive() {
        if (deleted) {
            throw new IllegalStateException("The mirrored texture was deleted");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }

    /** One GL level-0 readback: the pixels are owned by the caller until it frees them. */
    private static final class GlLevel {
        final ByteBuffer pixels;
        final int width;
        final int height;

        GlLevel(ByteBuffer pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }
}
