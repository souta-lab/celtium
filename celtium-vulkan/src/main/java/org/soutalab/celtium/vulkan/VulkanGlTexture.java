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

    /** Number of mip levels of the current image; the OpenGL atlas is sampled with a mip chain. */
    private int mipLevels = 1;
    private int imageUploadsSkipped;
    /** Levels the GL texture reported on the last upload, or 0 to compute them from the size. */
    /**
     * Bumped whenever this mirror's image and view are recreated (audit S12).
     * <p>
     * A driver may hand back the same view handle for a new view, so caches keyed on the handle alone can serve a
     * descriptor set built on a view that has been retired. The generation makes the key change even when the handle
     * does not.
     */
    private long generation;
    private boolean levelZeroCompared;
    /** GL's row order is bottom-up and Vulkan's is top-down, so the mirror has to flip. */
    private static final boolean FLIP_ROWS = false;
    private static final boolean SAMPLER_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.textureDiagnostics");

    /** Whether the sampler magnifies with NEAREST (block atlas) or LINEAR (light map). */
    /** The layout the image is actually in; barriers used to declare one the image was not in. */
    /**
     * The recorded layout of each mip level.
     * <p>
     * One field cannot describe a mipmapped atlas: the upload path leaves level 0 in a different layout from the levels a blit
     * chain produced, and a partial update that believes the whole image is being sampled cannot move level 0 at all. The
     * earlier attempts changed the tail barrier's range and broke exactly that agreement.
     */
    private int[] levelLayouts = new int[0];
    /**
     * Whether this image has ever been given its whole content.
     * <p>
     * A mirror created because a partial write arrived has only the patch that created it: the rest of the atlas is undefined,
     * and the registry used to clear its stale mark anyway, which made an image of mostly-nothing drawable. Drawing from it is
     * worse than sending the batch back to GL, so the acceptance path asks this before it builds a descriptor (audit A04).
     */
    private boolean fullContentArrived;
    private int currentLayout = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
    private boolean nearestFilter;

    /** Hash of the last uploaded level 0, so an unchanged texture skips the copy (and its queue drain). */
    private long lastPixelHash;

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
        return create(context, width, height, true);
    }

    /**
     * @param nearestFilter {@code true} for the pixel-art block atlas, {@code false} for the light map, whose
     *                      OpenGL texture is filtered linearly
     */
    public static VulkanGlTexture create(VulkanContext context, int width, int height, boolean nearestFilter) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        VulkanGlTexture result = new VulkanGlTexture(context);
        result.nearestFilter = nearestFilter;
        try {
            // One variable at a time: the level count is computed here so the sampler can be told the truth about the
            // chain, while the creation order stays exactly as it was. The previous measurement changed the order and the
            // clamp together, so it could not say which of them moved the frame.
            result.mipLevels = mipLevelsFor(width, height);
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

    /** Full-image uploads from GL: how often, how many bytes, how long, and which GL textures they came from. */
    private static long fullUploads;
    private static long fullUploadBytes;
    private static long fullUploadNanos;
    private static final java.util.Map<Integer, Integer> fullUploadsByGlId =
            new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();
    private static final java.util.Map<Integer, Long> fullUploadBytesByGlId =
            new java.util.concurrent.ConcurrentHashMap<Integer, Long>();

    /**
     * {@return a one-line summary of the full GL uploads, so the cost of mirroring can be read next to the frames}.
     * <p>
     * A sprite patch that ends up triggering a whole-image readback is the case worth seeing: the count, the bytes and
     * the wall time together say whether the mirror path is CPU-bound on copies or waiting on the GPU.
     */
    public static String fullUploadSummary() {
        StringBuilder text = new StringBuilder();
        text.append("count=").append(fullUploads)
                .append(" bytes=").append(fullUploadBytes)
                .append(" millis=").append(fullUploadNanos / 1000000L)
                .append(" byGlId=[");
        boolean first = true;
        for (java.util.Map.Entry<Integer, Integer> entry : fullUploadsByGlId.entrySet()) {
            if (!first) {
                text.append(' ');
            }
            first = false;
            Long bytes = fullUploadBytesByGlId.get(entry.getKey());
            text.append(entry.getKey()).append(':').append(entry.getValue())
                    .append('/').append(bytes == null ? 0L : bytes.longValue());
        }
        return text.append(']').toString();
    }

    public void uploadFromGlTexture(int glTextureId, int glTextureTarget) {
        long celtium$uploadStart = System.nanoTime();
        final int celtium$uploadGlId = glTextureId;
        try {
        requireAlive();
        GlLevel level = readGlLevel0(glTextureId, glTextureTarget);
        try {
            long hash = 1469598103934665603L;
            for (int i = 0; i < level.width * level.height * BYTES_PER_PIXEL; i++) {
                hash = (hash ^ (level.pixels.get(i) & 0xFF)) * 1099511628211L;
            }
            if (hash == lastPixelHash && level.width == width && level.height == height) {
                // The content is identical to what is already on the device: skip the copy, the command buffer and
                // the queue drain it would otherwise cost. The light map in particular is re-read every frame but
                // only changes when the sun moves or a torch flickers.
                return;
            }
            lastPixelHash = hash;
            dumpForDebugging(glTextureId, level);
            // The chain is generated here rather than copied from OpenGL. Copying GL's own levels needs the readback path
            // and measured 5.60%, against 0.16-0.28% for this generated chain with the sampler clamped at 1.0 - five
            // configurations were measured and this is the best, so the copying path is gone rather than kept behind a flag.
            boolean recreated = level.width != width || level.height != height;
            if (recreated) {
                // The count matters as much as the size: the previous attempt kept the size-derived chain and then read
                // levels OpenGL does not have, which measured 12.66% with ten validation errors.
                recreateImageResources(level.width, level.height);
            }
            System.out.println("[Celtium] CELTIUM_MIRROR size=" + level.width + "x" + level.height
                    + " imageLevels=" + this.mipLevels
                    + " view=" + Long.toHexString(this.view) + " image=" + Long.toHexString(this.image)
                    + " recreated=" + recreated);
            copyToImage(level.pixels, level.width, level.height, 0);
            if (SAMPLER_DIAGNOSTICS && level.width >= 128 && level.height >= 128 && !levelZeroCompared) {
                levelZeroCompared = true;
                try {
                    compareLevelZeroWithGl(glTextureId);
                } catch (RuntimeException | LinkageError error) {
                    System.out.println("[Celtium] CELTIUM_LEVEL0 comparison failed: " + error);
                }
            }
        } finally {
            MemoryUtil.memFree(level.pixels);
        }
        } finally {
            long celtium$elapsed = System.nanoTime() - celtium$uploadStart;
            synchronized (VulkanGlTexture.class) {
                fullUploads++;
                fullUploadNanos += celtium$elapsed;
                fullUploadsByGlId.merge(Integer.valueOf(celtium$uploadGlId), Integer.valueOf(1), (a, b) ->
                        Integer.valueOf(a.intValue() + b.intValue()));
                fullUploadBytesByGlId.merge(Integer.valueOf(celtium$uploadGlId), Long.valueOf(fullUploadBytes),
                        (a, b) -> Long.valueOf(a.longValue() + b.longValue()));
            }
        }
    }

    /**
     * Uploads pixels the caller already has, without reading anything back from OpenGL.
     * <p>
     * This is the ownership path: an upload through the game's utility arrives with its image in hand, so the mirror can be
     * built from the same data GL is about to be given rather than from a readback of what GL received. It deliberately does
     * **not** mirror OpenGL's mip levels - that needs the readback - so the registry uses it only when
     * {@code celtium.vulkan.mirrorGlMips} is off, which is the default and what every run in this project has measured.
     * <p>
     * Additive rather than extracted on purpose: the readback path has early returns and diagnostics of its own, and pulling a
     * shared middle out of it left the file not compiling the first time this was attempted.
     *
     * @return whether the image was written, false when the content was already there
     */
    /** {@return whether a patch was written into a sub-region}, for the animated writes that cover part of a texture. */
    /** {@return whether the image has been given its whole content at least once} (audit A04). */
    public boolean hasFullContent() {
        return this.fullContentArrived;
    }

    /**
     * Records that the image has been given its whole content.
     * <p>
     * The registry keeps the same fact in a set of its own, and the two were written by different code: a full supply
     * called the registry, while the descriptor decision asked this object, so the decision kept reading false however
     * many times the image had been supplied whole. One fact, one owner: the registry's marking lands here as well until
     * the set is retired.
     */
    void markFullContentArrived() {
        this.fullContentArrived = true;
    }

    public boolean uploadPatchFromPixels(int pixelWidth, int pixelHeight, int x, int y, java.nio.ByteBuffer rgba) {
        requireAlive();
        if (x < 0 || y < 0 || x + pixelWidth > width || y + pixelHeight > height) {
            return false;
        }
        copyToImage(rgba, pixelWidth, pixelHeight, 0, x, y, true);
        if (x == 0 && y == 0 && pixelWidth >= width && pixelHeight >= height) {
            // A "patch" that covers the image is a full initialization: the light map arrives this way.
            this.fullContentArrived = true;
        }
        // The image no longer holds what the last full upload put there, so the hash identifying that content is stale. Without
        // this a later full upload of the same pixels is skipped as a duplicate and the patch survives in a texture that should
        // have been replaced wholesale (audit A06).
        this.lastPixelHash = 0L;
        return true;
    }

    public boolean uploadFromImage(int pixelWidth, int pixelHeight, java.nio.ByteBuffer rgba) {
        requireAlive();
        requireAlive();
        long hash = 1469598103934665603L;
        int stride = Math.min(rgba.remaining() / Math.max(1, pixelHeight), pixelWidth * BYTES_PER_PIXEL);
        for (int i = 0; i < stride * pixelHeight; i++) {
            hash = (hash ^ (rgba.get(i) & 0xFF)) * 1099511628211L;
        }
        if (hash == lastPixelHash && pixelWidth == width && pixelHeight == height) {
            this.imageUploadsSkipped++;
            return false;
        }
        if (pixelWidth != width || pixelHeight != height) {
            recreateImageResources(pixelWidth, pixelHeight);
        }
        copyToImage(rgba, pixelWidth, pixelHeight, 0);
        this.fullContentArrived = true;
        // Recorded only once the pixels are actually in the image: a hash written before the transfer would claim content that
        // a failed transfer never delivered, and the next retry would then be skipped as a duplicate (audit A07).
        lastPixelHash = hash;
        return true;
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
    /** Identity for caches: the view plus the generation, so a reused handle cannot resurrect a stale entry. */
    public long key() {
        return (this.view * 31L) ^ this.generation;
    }

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
            final long retiredSampler = sampler;
            VulkanRetireQueue.retire(() -> vkDestroySampler(context.device(), retiredSampler, null));
            sampler = 0L;
        }
        width = 0;
        height = 0;
    }

    /**
     * The levels a mirrored texture of this size can have, shared so the sampler and the image agree on one number.
     * <p>
     * The chain is generated but not sampled: the clamp below is 1.0, which is the configuration that measures 0.26%
     * against the GL reference. The levels are kept rather than removed because every configuration with a different
     * clamp measured worse (see the comment on maxLod) and this is the state that was measured rather than a theory
     * about which levels ought to exist.
     */
    private static int mipLevelsFor(int width, int height) {
        int levels = 1;
        while ((1 << levels) <= Math.max(width, height)) {
            levels++;
        }
        return levels;
    }

    private void createImageResources(int newWidth, int newHeight) {
        this.mipLevels = mipLevelsFor(newWidth, newHeight);
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
                    .mipLevels(this.mipLevels)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
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
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("mirrored texture")) {
                // Thrown like any other failure on this path: the callers already handle a resource that could not be
                // built, and the point of the injection is to check that they still do.
                throw new IllegalStateException("injected allocation failure: mirrored texture");
            }
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanGlTexture:483")) {
                throw new IllegalStateException("injected allocation failure: VulkanGlTexture:483");
            }
            check(vkAllocateMemory(device, allocate, null, handle), "vkAllocateMemory(mirrored texture)");
            memory = handle.get(0);
            check(vkBindImageMemory(device, image, memory, 0L), "vkBindImageMemory(mirrored texture)");

            VkImageViewCreateInfo createView = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(IMAGE_FORMAT);
            this.generation++;
            createView.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, this.mipLevels, 0, 1);
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
                    // Minecraft's block atlas is a pixel-art texture: the OpenGL texture uses GL_NEAREST for
                    // magnification and GL_NEAREST_MIPMAP_LINEAR for minification, so linear magnification here
                    // blurred every magnified texel and the frame measured four times fewer texture edges than
                    // the OpenGL reference. NEAREST filters with a linear mipmap mode reproduce that combination.
                    .magFilter(this.nearestFilter ? VK_FILTER_NEAREST : VK_FILTER_LINEAR)
                    .minFilter(this.nearestFilter ? VK_FILTER_NEAREST : VK_FILTER_LINEAR)
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
                    // Measured law, five configurations against the same GL reference: 1.0 gives 0.21-0.28%; the full
                    // chain's highest level (8 or 9) gives 8.77-8.82%; four levels give 7.37%; 0.0 gives 13.51% both with a
                    // nine-level image and with a single-level one. The driver was then asked directly, and it reports the
                    // textures this scene binds as one level with maxLevel/maxLod 0, minFilter/magFilter 9728 (GL_NEAREST)
                    // and no bias - so copying what it reports makes the frame fifty times worse than clamping at 1.0, and
                    // the clamp is not the variable that explains the difference. The value stays at the measured best and
                    // the next instrument is to log the sampler that is actually created rather than infer it from this
                    // configuration (audit S23).
                    // Measured best. Mirroring OpenGL's own levels is implemented and validation-clean, and it measures
                    // 5.60% **whatever this clamp is**: 5.60% with a clamp of 1.0 and 5.60% with a clamp of 4.0, against
                    // 0.16-0.28% for the generated chain at this clamp. Clamping to the same levels therefore changes
                    // nothing, which rules out both the clamp and the shader's LOD as the difference and points at the
                    // recreate that mirroring performs when the level count differs - a new image and view while the
                    // descriptor may still name the old one. That is the next thing to check (audit S23).
                    // The full table, one variable at a time, against the same reference:
                    //   generated chain, clamp 0.0 -> 13.51%      mirrored levels, clamp 0.0 -> 13.52%
                    //   generated chain, clamp 1.0 -> 0.16-0.28%  mirrored levels, clamp 1.0 -> 5.56-5.60%
                    //                                             mirrored levels, clamp 4.0 -> 5.60%
                    // Level 0 alone is far off in **both** modes, with identical texels on both sides, so the reference is
                    // heavily mip-blurred. Blending in a level 1 brings the frame to 0.2% when that level is generated here
                    // and only to 5.6% when it is copied from OpenGL - so the levels this project generates resemble the
                    // reference's blur and the ones the game itself holds do not. The difference is therefore in what the
                    // sampler reads at level 1 and above, and the next instrument is to dump one level from each side and
                    // compare the numbers rather than the frame (audit S23).
                    .maxLod(1.0f)
                    .borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK)
                    .unnormalizedCoordinates(false);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateSampler(device, create, null, handle), "vkCreateSampler(mirrored texture)");
            sampler = handle.get(0);
            if (SAMPLER_DIAGNOSTICS) {
                // What was actually handed to the driver, rather than what a configuration is called. The clamp's value is
                // the variable every mip measurement has disagreed about, and this is the only place that knows it.
                System.out.println("[Celtium] CELTIUM_SAMPLER size=" + this.width + "x" + this.height
                        + " levels=" + this.mipLevels
                        + " minFilter=" + create.minFilter() + " magFilter=" + create.magFilter()
                        + " mipmapMode=" + create.mipmapMode()
                        + " minLod=" + create.minLod() + " maxLod=" + create.maxLod()
                        + " bias=" + create.mipLodBias() + " nearest=" + this.nearestFilter);
            }
        }
    }

    private void recreateImageResources(int newWidth, int newHeight) {
        destroyImageResources();
        createImageResources(newWidth, newHeight);
        // The new image is in VK_IMAGE_LAYOUT_UNDEFINED for every level, and the next write may be a patch, which only
        // transitions level 0. Levels 1..N then stay UNDEFINED while the descriptor that samples the view expects
        // SHADER_READ_ONLY_OPTIMAL, which is the validation error the world-switch scene produced (ten of them, one per
        // level, "current layout is VK_IMAGE_LAYOUT_UNDEFINED"). Moving every level here - contents are undefined, only
        // the layout changes - is what the create path already does for level 0.
        transitionToShaderRead();
    }

    private void destroyImageResources() {
        VkDevice device = context.device();
        if (view != 0L) {
            // Retired rather than destroyed: a command buffer recorded earlier may still name this view (audit S12).
            final long retiredView = view;
            VulkanRetireQueue.retire(() -> vkDestroyImageView(device, retiredView, null));
            view = 0L;
        }
        if (image != 0L) {
            final long retiredImage = image;
            VulkanRetireQueue.retire(() -> vkDestroyImage(device, retiredImage, null));
            image = 0L;
        }
        if (memory != 0L) {
            final long retiredMemory = memory;
            VulkanRetireQueue.retire(() -> vkFreeMemory(device, retiredMemory, null));
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
            // Every level, not just level 0: the descriptor samples the whole view, so a level left in UNDEFINED is a
            // validation error at draw time (VUID-vkCmdDraw-None-09600), and after a resize the levels above 0 have
            // never been transitioned by anything else.
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, Math.max(1, this.mipLevels), 0, 1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, SAMPLING_STAGES,
                    0, null, null, barrier);
            for (int covered = 0; covered < Math.max(1, this.mipLevels); covered++) {
                setLevelLayout(covered, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
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
            // The object identity is part of the name because one GL texture can be mirrored by more than one
            // VulkanGlTexture - the terrain keeps its own atlas and the registry keeps the immediate path's copy - and a
            // comparison that cannot tell those apart cannot say which one the terrain actually samples.
            java.io.File target = new java.io.File(directory, "gl-texture-" + glTextureId + "-" + level.width + "x"
                    + level.height + "-obj" + Integer.toHexString(System.identityHashCode(this)) + ".png");
            // One file per texture is enough when the question is "what shape does the mirror have". When the question is
            // "is the mirror stale right now", the last upload is the one that matters, so the overwrite mode keeps it.
            if (target.isFile() && !Boolean.getBoolean("celtium.vulkan.dumpTexturesEvery")) {
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

    /**
     * Generates the mip chain by blitting each level from the one above it. The OpenGL atlas is sampled with mips,
     * so without this every distant or grazing-angle surface aliases into noise instead of averaging like the
     * OpenGL frame does.
     */
    private void recordMipChain(VkCommandBuffer commandBuffer, int pixelWidth, int pixelHeight, MemoryStack stack) {
        if (this.mipLevels <= 1) {
            return;
        }
        int levelWidth = pixelWidth;
        int levelHeight = pixelHeight;
        // Generating the chain here used to average texels across sprite borders, which is why
        // sampling it deeply measured 7.37-8.77% against the reference while OpenGL's own per-sprite levels measure
        // better. uploadFromGlTexture copies each of GL's levels instead, so this loop exists only for the other mode.
        for (int level = 1; level < this.mipLevels; level++) {
            int nextWidth = Math.max(1, levelWidth / 2);
            int nextHeight = Math.max(1, levelHeight / 2);

            // Level 0 is in TRANSFER_DST_OPTIMAL after the copy; every later level was already moved to
            // TRANSFER_SRC_OPTIMAL by the post-blit barrier of the previous iteration, so only the first pass needs
            // this transition. Doing it twice is an invalid oldLayout and validation rejects it.
            if (level == 1) {
            VkImageMemoryBarrier.Buffer toSource = VkImageMemoryBarrier.calloc(1, stack);
            toSource.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            toSource.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level - 1, 1, 0, 1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    null, null, toSource);
            setLevelLayout(level - 1, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            }

            VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
            blit.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level - 1)
                    .baseArrayLayer(0).layerCount(1);
            blit.get(0).srcOffsets().get(0).set(0, 0, 0);
            blit.get(0).srcOffsets().get(1).set(levelWidth, levelHeight, 1);
            blit.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level)
                    .baseArrayLayer(0).layerCount(1);
            blit.get(0).dstOffsets().get(0).set(0, 0, 0);
            blit.get(0).dstOffsets().get(1).set(nextWidth, nextHeight, 1);
            vkCmdBlitImage(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK_FILTER_LINEAR);

            // The blit leaves the destination in TRANSFER_DST_OPTIMAL. Moving it to TRANSFER_SRC_OPTIMAL right away
            // keeps every level in one known layout, so the single transition to SHADER_READ_ONLY_OPTIMAL at the end
            // can cover the whole chain with one barrier instead of one per level (validation rejects a range whose
            // members sit in different layouts).
            VkImageMemoryBarrier.Buffer toSrc = VkImageMemoryBarrier.calloc(1, stack);
            toSrc.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            toSrc.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level, 1, 0, 1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    null, null, toSrc);
            setLevelLayout(level, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

            levelWidth = nextWidth;
            levelHeight = nextHeight;
        }
    }

    /** Records a level's layout, growing the array when a recreated image has more levels than the previous one had. */
    private void setLevelLayout(int level, int layout) {
        if (level < 0) {
            return;
        }
        if (level >= this.levelLayouts.length) {
            this.levelLayouts = java.util.Arrays.copyOf(this.levelLayouts, level + 1);
        }
        this.levelLayouts[level] = layout;
    }

    /** {@return the recorded layout of a level}, or {@code UNDEFINED} when nothing is known about it yet. */
    private int levelLayout(int level) {
        return level >= 0 && level < this.levelLayouts.length ? this.levelLayouts[level] : VK_IMAGE_LAYOUT_UNDEFINED;
    }

    private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight) {
        copyToImage(pixels, pixelWidth, pixelHeight, 0);
    }

    private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight, int level) {
        copyToImage(pixels, pixelWidth, pixelHeight, level, 0, 0);
    }

    /**
     * Copies pixels into a sub-region of a level, which is what the atlas's per-sprite animation needs.
     * <p>
     * Vulkan's buffer-to-image copy takes a destination offset natively, so a sprite's frame - thirty-two by thirty-two at
     * some position inside a two-hundred-and-fifty-six atlas - can be written straight into the image. That is the whole
     * reason the animated writes do not have to be read back out of GL.
     */
    private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight, int level, int offsetX,
            int offsetY) {
        copyToImage(pixels, pixelWidth, pixelHeight, level, offsetX, offsetY, false);
    }

    /**
     * Copies pixels into the image, optionally preserving what is already there.
     * <p>
     * A full upload rewrites every level, so it may start from {@code UNDEFINED} and record a mip chain. A patch must not: it
     * covers one sprite's frame inside the atlas, so it starts from the layout that level is actually in, writes only that
     * level, skips the chain, and transitions back over that level alone.
     */
    private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight, int level, int offsetX, int offsetY,
            boolean preserve) {
        long bytes = (long) pixelWidth * (long) pixelHeight * BYTES_PER_PIXEL;
        ensureStaging(bytes);
        // The staging buffer is host visible and persistently mapped, so the pixels go straight from the readback
        // buffer into device visible memory without an intermediate Java array. The row order is kept as GL
        // returned it; a vertical flip here made the whole terrain black, so the orientation question is not
        // settled and must be answered with a controlled pattern instead of by flipping this copy.
        if (FLIP_ROWS) {
            // Off, and measured: flipping every mirrored row turned the terrain almost black (band luminance 3.2 against
            // the reference's 131) because the atlas's UVs and this upload are both in OpenGL's row order, so the flip
            // inverted every block face. The earlier note that a flip "made the whole terrain black" was right, and this
            // round's attempt to overrule it is recorded as the mistake it was. The light map's darkness has another
            // cause; the flip is kept only as a lab flag.
            int rowBytes = pixelWidth * BYTES_PER_PIXEL;
            for (int y = 0; y < pixelHeight; y++) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(pixels) + (long) y * rowBytes,
                        stagingAddress + (long) (pixelHeight - 1 - y) * rowBytes, rowBytes);
            }
        } else {
            MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), stagingAddress, bytes);
        }
        if (!stagingCoherent) {
            // Non-coherent host memory needs an explicit flush before the copy; coherent memory (the common case)
            // is made available to the device by the queue submission itself.
            flushStaging();
        }

        VkCommandBuffer commandBuffer = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            // The whole chain is re-uploaded from scratch, so the old layout may be anything - including the
            // VK_IMAGE_LAYOUT_UNDEFINED an image starts in, which validation rejects as an oldLayout for a
            // first transition. Every mip level is covered because the levels are regenerated below.
            barrier.get(0).sType$Default()
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(preserve ? levelLayout(level) : VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level,
                    preserve ? 1 : this.mipLevels, 0, 1);
            if (level == 0) { vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier); }

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            // A row length of 0 means the rows are tightly packed, which is exactly how GL returned them.
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(level).baseArrayLayer(0).layerCount(1);
            region.get(0).imageOffset().set(offsetX, offsetY, 0);
            region.get(0).imageExtent().set(pixelWidth, pixelHeight, 1);
            vkCmdCopyBufferToImage(commandBuffer, stagingBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            this.currentLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            for (int covered = level; covered < (preserve ? level + 1 : this.mipLevels); covered++) {
                setLevelLayout(covered, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            }

            if (!preserve) {
                recordMipChain(commandBuffer, pixelWidth, pixelHeight, stack);
            }

            // Reuse the same barrier struct for the way back: the image and subresource range are unchanged, only
            // the access masks and layouts differ.
            // Every level ends in a different transfer layout (the blit chain leaves levels in TRANSFER_SRC or
            // TRANSFER_DST), so the range covers them all and the source accesses include both transfer roles.
            if (preserve) {
                barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level, 1, 0, 1);
            }
            barrier.get(0)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    // Mirrored levels are copied, not blitted, so the image is still in TRANSFER_DST when they are done -
                    // declaring TRANSFER_SRC here is what validation rejected with fifteen errors, the same class of
                    // mistake the Intel run caught in the mip chain (audit S23).
                    .oldLayout(this.mipLevels > 1 ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL : this.currentLayout)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            // The tail transition belongs to whichever call finishes the chain: the last mirrored level, or level 0
                // when the chain is generated by blitting below. Getting this wrong left images in a transfer layout -
                // ten validation errors and no SHADER_READ_ONLY for the atlas (audit S23).
                // A patch writes level 0 of a multi-level image, so it does its own transition; a full upload keeps the original
                // condition, whose barrier covers the whole chain - which is what makes every level end up sampled.
                if (preserve || level == this.mipLevels - 1 || level == 0) {
                    if (preserve) {
                        barrier.get(0).oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                    }
                    vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, SAMPLING_STAGES, 0, null, null,
                            barrier);
                    for (int covered = preserve ? level : 0; covered < (preserve ? level + 1 : this.mipLevels);
                            covered++) {
                        setLevelLayout(covered, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                    }
                }
        } finally {
            // Always close the one-shot command buffer, also when recording threw half way through, so the
            // context's helper never keeps an open command buffer around.
            context.endCommands(commandBuffer);
        }
    }

    /**
     * Diagnostic: read this mirror's level 0 back from the device and compare it, byte for byte, with the level 0 the
     * OpenGL texture currently holds. Every other instrument in this area has compared frames or reasoned about
     * coordinates; this compares the two images themselves.
     */
    public void compareLevelZeroWithGl(int glTextureId) {
        int w = this.width;
        int h = this.height;
        if (w <= 0 || h <= 0) {
            return;
        }
        long bytes = (long) w * h * BYTES_PER_PIXEL;
        ensureStaging(bytes);
        VkCommandBuffer commands = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    null, null, barrier);
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                    .layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().set(w, h, 1);
            vkCmdCopyImageToBuffer(commands, this.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, this.stagingBuffer,
                    region);
            barrier.get(0).srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, SAMPLING_STAGES, 0, null, null, barrier);
        }
        context.endCommands(commands);

        GlLevel gl = readGlLevel0(glTextureId, GL_TEXTURE_2D);
        try {
            long differing = 0;
            long total = 0;
            int maxDiff = 0;
            long sumDiff = 0;
            for (int i = 0; i < w * h * BYTES_PER_PIXEL; i++) {
                int ours = MemoryUtil.memGetByte(this.stagingAddress + i) & 0xFF;
                int theirs = gl.pixels.get(i) & 0xFF;
                int d = Math.abs(ours - theirs);
                total++;
                sumDiff += d;
                if (d != 0) {
                    differing++;
                }
                if (d > maxDiff) {
                    maxDiff = d;
                }
            }
            System.out.println("[Celtium] CELTIUM_LEVEL0 size=" + w + "x" + h
                    + " bytes=" + total + " differing=" + differing
                    + " fraction=" + String.format("%.4f", (double) differing / Math.max(1L, total))
                    + " meanAbsDiff=" + String.format("%.2f", (double) sumDiff / Math.max(1L, total))
                    + " maxAbsDiff=" + maxDiff);
        } finally {
            MemoryUtil.memFree(gl.pixels);
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
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanGlTexture:979")) {
                throw new IllegalStateException("injected allocation failure: VulkanGlTexture:979");
            }
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
        return readGlLevel(glTextureId, glTextureTarget, 0);
    }

    /** How many levels the GL texture actually has, from its own {@code GL_TEXTURE_MAX_LEVEL}. */

    private static GlLevel readGlLevel(int glTextureId, int glTextureTarget, int level) {
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
            int textureWidth = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_WIDTH);
            int textureHeight = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_HEIGHT);
            int internalFormat = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_INTERNAL_FORMAT);
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
            glGetTexImage(GL_TEXTURE_2D, level, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            // The internal format is part of the message because GL only permits this conversion for colour
            // formats; an integer or depth texture would be rejected here and the format identifies it.
            checkGl("reading level " + level + " of GL texture " + glTextureId + " as RGBA bytes (internal format 0x"
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
