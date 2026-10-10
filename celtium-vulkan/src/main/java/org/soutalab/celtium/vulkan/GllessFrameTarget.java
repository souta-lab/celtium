package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
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
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCmdClearColorImage;
import static org.lwjgl.vulkan.VK10.vkCmdClearDepthStencilImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;

import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

/**
 * The frame image when no OpenGL context exists: the colour and depth images are created, not imported, and there
 * is no GL side to hand them to.
 * <p>
 * The synchronization contract stays the one {@link FrameImageTarget} declares, so the backend's submission logic
 * does not branch on which target it holds: {@link #beginFrame()} signals {@link #waitSemaphore()} with a
 * signal-only submission (a real binary semaphore the frame's first submit waits on, exactly as the GL signal
 * would), and {@link #endFrame()} consumes {@link #doneSemaphore()} with a wait-only submission, so the pair
 * cycles every frame the way GL's wait did. Both are cheap queue packets - the alternative, returning null
 * semaphores, would make every submission path grow a branch.
 * <p>
 * The images never leave our queue family: the acquire/release barriers still perform the layout transitions a
 * frame needs (GENERAL to the render layout and back) but carry no external-family transfer.
 * <p>
 * A second, independent pair of images lives beside the frame's own: {@link #ensureOffTarget(int, int)} creates it
 * on first use for draws the game addresses to one of <em>its</em> framebuffers rather than the main one - the
 * entity-outline silhouette is the measured case, and recorded against the frame's framebuffer it painted the
 * silhouette flat onto the scene, depth-testing against the scene's own buffer. The pair uses the frame's formats
 * and the same {@code VK_IMAGE_LAYOUT_GENERAL} attachments, so the frame-compatible render pass built by
 * {@code VulkanTerrainPipeline.createFrameRenderPass} accepts it without a second pass; {@link #clearOffTarget}
 * records the clears the game's own framebuffer would have received, and {@link #offTargetSelfTest} records a
 * clear-and-barrier sequence the validation layer can check end to end.
 */
public final class GllessFrameTarget implements FrameImageTarget {

    private static final int COLOR_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;
    private static final int DEPTH_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;

    /** What the game clears its own framebuffers to before drawing into them: transparent black, depth 1. */
    private static final float OFF_TARGET_CLEAR_RED = 0.0f;
    private static final float OFF_TARGET_CLEAR_GREEN = 0.0f;
    private static final float OFF_TARGET_CLEAR_BLUE = 0.0f;
    private static final float OFF_TARGET_CLEAR_ALPHA = 0.0f;
    private static final float OFF_TARGET_CLEAR_DEPTH = 1.0f;

    private final VulkanContext context;
    private final int width;
    private final int height;

    private long colorImage;
    private long colorMemory;
    private long colorView;
    private long depthImage;
    private long depthMemory;
    private long depthView;
    private long waitSemaphore;
    private long doneSemaphore;

    /**
     * The off-target pair: created by {@link #ensureOffTarget(int, int)} on first use, replaced when the requested
     * size changes, and released by {@link #delete()} like every other resource here. They are never handed to GL
     * and never leave this queue family, so their only layout bookkeeping is the one-time UNDEFINED to GENERAL move
     * {@link #clearOffTarget} records before the first clear.
     */
    private long offTargetColorImage;
    private long offTargetColorMemory;
    private long offTargetColorView;
    private long offTargetDepthImage;
    private long offTargetDepthMemory;
    private long offTargetDepthView;
    private int offTargetWidth;
    private int offTargetHeight;
    /** False until the first clear has moved the pair out of the layout they were created in (UNDEFINED). */
    private boolean offTargetAcquired;
    private int offTargetCreations;
    private int offTargetClears;
    private int offTargetSelfTests;

    private boolean frameOpen;
    private boolean deleted;
    private boolean firstAcquire = true;
    /** The first acquire clears both images whatever the game asked: their content starts undefined. */
    private boolean neverCleared = true;
    private int colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    private int depthImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    private int abandonedFrames;
    private String lastAbandonReason = "none";

    private GllessFrameTarget(VulkanContext context, int width, int height) {
        this.context = context;
        this.width = width;
        this.height = height;
    }

    public static GllessFrameTarget create(VulkanContext context, int width, int height) {
        if (context == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("context, width and height must be set");
        }
        GllessFrameTarget target = new GllessFrameTarget(context, width, height);
        try {
            long[] color = target.allocateImage(true, width, height);
            target.colorImage = color[0];
            target.colorMemory = color[1];
            target.colorView = color[2];
            long[] depth = target.allocateImage(false, width, height);
            target.depthImage = depth[0];
            target.depthMemory = depth[1];
            target.depthView = depth[2];
            target.allocateSemaphores();
            return target;
        } catch (RuntimeException | LinkageError error) {
            target.delete();
            throw error;
        }
    }

    /**
     * Creates one image of a pair at the given size - the image, its device-local memory and a 2D view - and
     * returns them as {@code {image, memory, view}}. The reason this is one method rather than one per pair: the
     * off-target pair is the same pair of formats and usages as the frame's own, only at the size the game's
     * framebuffer asks for.
     * <p>
     * A failure after the image or the memory exists frees what was created here before it propagates, so the
     * caller only ever adopts complete handles and {@link #delete()} never has to know about a half-created image.
     */
    private long[] allocateImage(boolean color, int imageWidth, int imageHeight) {
        VkDevice device = this.context.device();
        long image = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        try (MemoryStack stack = stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            int format = color ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_D32_SFLOAT;
            int usage = color
                    ? VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
                    : VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            VkImageCreateInfo createImage = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D).format(format).mipLevels(1).arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL).usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            createImage.extent().set(imageWidth, imageHeight, 1);
            if (vkCreateImage(device, createImage, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImage(glless " + (color ? "colour" : "depth") + ") failed");
            }
            image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, image, requirements);
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits()));
            if (vkAllocateMemory(device, allocate, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkAllocateMemory(glless " + (color ? "colour" : "depth") + ") failed");
            }
            memory = handle.get(0);
            if (vkBindImageMemory(device, image, memory, 0L) != VK_SUCCESS) {
                throw new IllegalStateException("vkBindImageMemory(glless " + (color ? "colour" : "depth") + ") failed");
            }

            VkImageViewCreateInfo createView = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
            createView.subresourceRange().set(color ? VK_IMAGE_ASPECT_COLOR_BIT : VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            if (vkCreateImageView(device, createView, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImageView(glless " + (color ? "colour" : "depth") + ") failed");
            }
            return new long[] {image, memory, handle.get(0)};
        } catch (RuntimeException | LinkageError error) {
            if (memory != VK_NULL_HANDLE) {
                vkFreeMemory(device, memory, null);
            }
            if (image != VK_NULL_HANDLE) {
                vkDestroyImage(device, image, null);
            }
            throw error;
        }
    }

    private void allocateSemaphores() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkSemaphoreCreateInfo info = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            if (vkCreateSemaphore(this.context.device(), info, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateSemaphore(glless wait) failed");
            }
            this.waitSemaphore = handle.get(0);
            if (vkCreateSemaphore(this.context.device(), info, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateSemaphore(glless done) failed");
            }
            this.doneSemaphore = handle.get(0);
        }
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
            throw new IllegalStateException("no device-local memory type for the glless frame target");
        }
    }

    private void checkAlive() {
        if (this.deleted) {
            throw new IllegalStateException("GllessFrameTarget was deleted");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result);
        }
    }

    /**
     * A submission that only signals {@link #waitSemaphore()}: under the shared target GL's signal does this; with
     * no GL to signal, a queue packet does instead, and the frame's first submission can wait on the semaphore in
     * exactly the way it already does.
     */
    private void signalWait() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer signal = stack.mallocLong(1);
            signal.put(0, this.waitSemaphore);
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default().pSignalSemaphores(signal);
            check(vkQueueSubmit(this.context.queue(), submit, VK_NULL_HANDLE),
                    "vkQueueSubmit (glless beginFrame signal)");
        }
    }

    /**
     * A submission that only waits on {@link #doneSemaphore()}: the frame's last submission signals it, so this
     * consumes the signal and resets the semaphore for the next frame - the job GL's wait did under the shared
     * target.
     */
    private void consumeDone() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer wait = stack.mallocLong(1);
            wait.put(0, this.doneSemaphore);
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                    .waitSemaphoreCount(wait.remaining()).pWaitSemaphores(wait)
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
            check(vkQueueSubmit(this.context.queue(), submit, VK_NULL_HANDLE),
                    "vkQueueSubmit (glless endFrame wait)");
        }
    }

    @Override
    public void beginFrame() {
        checkAlive();
        if (this.frameOpen) {
            this.frameOpen = false;
            this.abandonedFrames++;
            this.lastAbandonReason = "beginFrame found the previous frame open";
        }
        signalWait();
        this.frameOpen = true;
    }

    @Override
    public void beginFrameForPresent() {
        beginFrame();
    }

    @Override
    public void endFrame() {
        checkAlive();
        if (!this.frameOpen) {
            return;
        }
        consumeDone();
        this.frameOpen = false;
    }

    @Override
    public void abandonFrame() {
        abandonFrame("unspecified");
    }

    @Override
    public void abandonFrame(String reason) {
        if (this.frameOpen) {
            this.frameOpen = false;
            this.abandonedFrames++;
            this.lastAbandonReason = reason;
        }
    }

    @Override
    public int adoptableFramebuffer(int width, int height) {
        return 0;
    }

    @Override
    public String currentFramebufferViolation() {
        return null;
    }

    @Override
    public long colorImage() {
        checkAlive();
        return this.colorImage;
    }

    @Override
    public long colorView() {
        checkAlive();
        return this.colorView;
    }

    @Override
    public long depthImage() {
        checkAlive();
        return this.depthImage;
    }

    @Override
    public long depthView() {
        checkAlive();
        return this.depthView;
    }

    @Override
    public int colorImageLayout() {
        return this.colorImageLayout;
    }

    @Override
    public void noteColorImageLayout(int layout) {
        this.colorImageLayout = layout;
    }

    @Override
    public long waitSemaphore() {
        checkAlive();
        return this.waitSemaphore;
    }

    @Override
    public long doneSemaphore() {
        checkAlive();
        return this.doneSemaphore;
    }

    @Override
    public void recordAcquireBarriers(VkCommandBuffer commandBuffer) {
        recordLayoutBarriers(commandBuffer, false);
        this.firstAcquire = false;
        this.colorImageLayout = COLOR_RENDER_LAYOUT;
        this.depthImageLayout = DEPTH_RENDER_LAYOUT;
        applyGameClears(commandBuffer);
    }

    /**
     * Clears what the game asked {@code glClear} to clear while no GL framebuffer existed to clear.
     * <p>
     * This is not a nicety: a depth buffer that is never cleared holds undefined values for the whole session, and
     * every draw that tests depth against them fails, which is a frame of nothing rather than a wrong pixel. The
     * request is carried over from the stub because {@code glClear} is the game's, not ours - the colour it names is
     * the sky or the menu background, and the depth is what makes the next draw's test meaningful.
     * <p>
     * The image is in {@link #COLOR_RENDER_LAYOUT} here, which is {@code VK_IMAGE_LAYOUT_GENERAL}, one of the two
     * layouts {@code vkCmdClear*Image} accepts, so no extra transition is needed.
     */
    private void applyGameClears(VkCommandBuffer command) {
        int clears = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.consumePendingClears();
        boolean first = this.neverCleared;
        if (clears == 0 && !first) {
            return;
        }
        this.neverCleared = false;
        // GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT
        boolean color = first || (clears & 0x4000) != 0 || (clears & 0x1000) != 0;
        boolean depth = first || (clears & 0x0100) != 0;
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkImageSubresourceRange range =
                    org.lwjgl.vulkan.VkImageSubresourceRange.calloc(stack).levelCount(1).layerCount(1);
            if (color) {
                float[] rgba = new float[4];
                org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearColor(rgba);
                org.lwjgl.vulkan.VkClearColorValue value =
                        org.lwjgl.vulkan.VkClearColorValue.calloc(stack);
                value.float32(0, rgba[0]);
                value.float32(1, rgba[1]);
                value.float32(2, rgba[2]);
                value.float32(3, rgba[3]);
                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                org.lwjgl.vulkan.VK10.vkCmdClearColorImage(command, this.colorImage, COLOR_RENDER_LAYOUT,
                        value, range);
            }
            if (depth) {
                org.lwjgl.vulkan.VkClearDepthStencilValue value =
                        org.lwjgl.vulkan.VkClearDepthStencilValue.calloc(stack);
                value.depth((float) org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearDepth()).stencil(0);
                range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                org.lwjgl.vulkan.VK10.vkCmdClearDepthStencilImage(command, this.depthImage, DEPTH_RENDER_LAYOUT,
                        value, range);
            }
        }
    }

    @Override
    public void recordReleaseBarriers(VkCommandBuffer commandBuffer) {
        recordLayoutBarriers(commandBuffer, true);
        this.colorImageLayout = VK_IMAGE_LAYOUT_GENERAL;
        this.depthImageLayout = VK_IMAGE_LAYOUT_GENERAL;
    }

    /**
     * The same layout transitions the shared target's ownership barriers perform, minus the queue-family
     * transfers: the images belong to this queue in every phase, so the barrier only moves layouts.
     */
    private void recordLayoutBarriers(VkCommandBuffer command, boolean release) {
        int colorAccess = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        int depthAccess = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        int queueFamily = this.context.queueFamilyIndex();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);

            VkImageMemoryBarrier colorBarrier = barriers.get(0);
            colorBarrier.sType$Default()
                    .image(this.colorImage)
                    // The colour image's true layout is whatever the last phase left it in - acquire, release or the
                    // present's own transition - so it is tracked rather than assumed, the way the present path
                    // reports the layout it restored through noteColorImageLayout.
                    .oldLayout(release ? COLOR_RENDER_LAYOUT : this.colorImageLayout)
                    .newLayout(release ? VK_IMAGE_LAYOUT_GENERAL : COLOR_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(release && !(this.firstAcquire && !release) ? colorAccess : 0)
                    .dstAccessMask(release ? 0 : colorAccess);
            colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

            VkImageMemoryBarrier depthBarrier = barriers.get(1);
            depthBarrier.sType$Default()
                    .image(this.depthImage)
                    .oldLayout(release ? DEPTH_RENDER_LAYOUT : this.depthImageLayout)
                    .newLayout(release ? VK_IMAGE_LAYOUT_GENERAL : DEPTH_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(release && !(this.firstAcquire && !release) ? depthAccess : 0)
                    .dstAccessMask(release ? 0 : depthAccess);
            depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

            vkCmdPipelineBarrier(command,
                    this.firstAcquire && !release ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
        }
    }

    @Override
    public boolean matches(int width, int height) {
        return this.width == width && this.height == height;
    }

    // ----------------------------------------------------------------------------------------------------------
    // The off-target image pair
    //
    // Not every draw the game issues is addressed to the main framebuffer. The entity outline is the measured
    // case: the silhouette is drawn into the game's own entityOutlineFramebuffer. Recorded against the frame's
    // framebuffer those draws overpainted the silhouette flat onto the scene, testing depth against the scene's
    // own depth buffer. This pair is where such draws land instead. It repeats the frame images' formats and
    // usages - so the frame-compatible render pass built by VulkanTerrainPipeline.createFrameRenderPass accepts
    // it unchanged, GENERAL attachments included - and is created on demand at the size the game's framebuffer
    // asks for rather than up front, because most sessions never bind a second framebuffer at all.
    // ----------------------------------------------------------------------------------------------------------

    /** The colour image view off-target draws render into, or 0 before the first {@link #ensureOffTarget}. */
    public long offTargetColorView() {
        checkAlive();
        return this.offTargetColorView;
    }

    /** The depth image view off-target draws test against, or 0 before the first {@link #ensureOffTarget}. */
    public long offTargetDepthView() {
        checkAlive();
        return this.offTargetDepthView;
    }

    /** Whether the off-target pair exists and is exactly the requested size. */
    public boolean offTargetMatches(int width, int height) {
        return this.offTargetColorView != VK_NULL_HANDLE && this.offTargetDepthView != VK_NULL_HANDLE
                && this.offTargetWidth == width && this.offTargetHeight == height;
    }

    /**
     * Makes the off-target pair exist at the requested size, creating it on first use and replacing it when the
     * size changes. A freed pair is destroyed here, so the caller must have settled the GPU before asking for a
     * different size - the same precondition the frame target's own re-creation has, and for the same reason: the
     * validation layer has already caught one destroy of an image still in use by a command buffer.
     * <p>
     * The images are created in {@code VK_IMAGE_LAYOUT_UNDEFINED}; {@link #clearOffTarget} records the one-way move
     * to the {@code GENERAL} layout the render pass declares, so it (or {@link #offTargetSelfTest}) must be
     * recorded before any render pass binds these views.
     */
    public void ensureOffTarget(int width, int height) {
        checkAlive();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("off-target size must be positive, got " + width + "x" + height);
        }
        if (offTargetMatches(width, height)) {
            return;
        }
        releaseOffTarget();
        try {
            long[] color = allocateImage(true, width, height);
            this.offTargetColorImage = color[0];
            this.offTargetColorMemory = color[1];
            this.offTargetColorView = color[2];
            long[] depth = allocateImage(false, width, height);
            this.offTargetDepthImage = depth[0];
            this.offTargetDepthMemory = depth[1];
            this.offTargetDepthView = depth[2];
            this.offTargetWidth = width;
            this.offTargetHeight = height;
            this.offTargetAcquired = false;
            this.offTargetCreations++;
        } catch (RuntimeException | LinkageError error) {
            // No half pair is left behind: a retry starts from nothing and delete() still frees whatever exists.
            releaseOffTarget();
            throw error;
        }
    }

    /**
     * Records the clears the game's own framebuffer receives before it draws into it: colour (0,0,0,0) and depth
     * 1.0, the transparent black and far plane {@code glClear} leaves behind, in the {@code GENERAL} layout both
     * {@code vkCmdClearColorImage} and {@code vkCmdClearDepthStencilImage} accept.
     * <p>
     * Three things are recorded, in this order, because each one is what makes the next legal:
     * <ol>
     * <li>on first use only, one barrier per image moving UNDEFINED to GENERAL - the layout these images were
     * created in has undefined content, and a clear into an image the validation layer still believes is UNDEFINED
     * is a layout mismatch rather than a clear;</li>
     * <li>the colour clear and the depth/stencil clear, both in GENERAL;</li>
     * <li>a barrier from the transfer write the clears are to the attachment reads and writes the pass that follows
     * performs, so the pass cannot load the pre-clear content.</li>
     * </ol>
     * Recorded outside a render pass instance, as both clear commands require. The commands land in the caller's
     * command buffer, which is the one the off-target pass will be recorded into.
     */
    public void clearOffTarget(VkCommandBuffer command) {
        checkAlive();
        if (command == null || command.address() == 0L) {
            throw new IllegalArgumentException("off-target clear needs a command buffer");
        }
        if (this.offTargetColorView == VK_NULL_HANDLE || this.offTargetDepthView == VK_NULL_HANDLE) {
            throw new IllegalStateException("ensureOffTarget must run before clearOffTarget");
        }
        try (MemoryStack stack = stackPush()) {
            if (!this.offTargetAcquired) {
                recordOffTargetAcquire(command);
                this.offTargetAcquired = true;
            }
            VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack).levelCount(1).layerCount(1);

            VkClearColorValue color = VkClearColorValue.calloc(stack);
            color.float32(0, OFF_TARGET_CLEAR_RED);
            color.float32(1, OFF_TARGET_CLEAR_GREEN);
            color.float32(2, OFF_TARGET_CLEAR_BLUE);
            color.float32(3, OFF_TARGET_CLEAR_ALPHA);
            range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            vkCmdClearColorImage(command, this.offTargetColorImage, VK_IMAGE_LAYOUT_GENERAL, color, range);

            VkClearDepthStencilValue depth = VkClearDepthStencilValue.calloc(stack);
            depth.depth(OFF_TARGET_CLEAR_DEPTH).stencil(0);
            range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
            vkCmdClearDepthStencilImage(command, this.offTargetDepthImage, VK_IMAGE_LAYOUT_GENERAL, depth, range);

            recordOffTargetClearToAttachment(command);
            this.offTargetClears++;
        }
    }

    /**
     * Proves the off-target pair can actually be written, which nothing before this did: it records the clears a
     * real off-target pass starts from and the barriers around them, so the validation layer checks the image
     * handles, the image layouts, the aspect masks and the queue-family use of the whole sequence, and returns what
     * was recorded as one line.
     * <p>
     * No staging buffer is involved and none is needed: the point is not to read a pixel back but to show that the
     * commands record and are legal, which is what a wrong format, usage, aspect or layout would break. The line
     * carries the two images' and views' handles, so a report can show they exist, are distinct from the frame's
     * own images, and were cleared at the size the game asked for.
     */
    public String offTargetSelfTest(VkCommandBuffer command) {
        checkAlive();
        if (command == null || command.address() == 0L) {
            throw new IllegalArgumentException("off-target self-test needs a command buffer");
        }
        if (this.offTargetColorView == VK_NULL_HANDLE || this.offTargetDepthView == VK_NULL_HANDLE) {
            throw new IllegalStateException("ensureOffTarget must run before offTargetSelfTest");
        }
        clearOffTarget(command);
        this.offTargetSelfTests++;
        return "glless off-target self-test #" + this.offTargetSelfTests
                + ": size=" + this.offTargetWidth + "x" + this.offTargetHeight
                + " clearColor=(" + OFF_TARGET_CLEAR_RED + "," + OFF_TARGET_CLEAR_GREEN + ","
                + OFF_TARGET_CLEAR_BLUE + "," + OFF_TARGET_CLEAR_ALPHA + ")"
                + " clearDepth=" + OFF_TARGET_CLEAR_DEPTH + " clearStencil=0"
                + " layout=GENERAL->GENERAL"
                + " barrier=clearTransferWrite->attachmentReadWrite"
                + " colorImage=0x" + Long.toHexString(this.offTargetColorImage)
                + " depthImage=0x" + Long.toHexString(this.offTargetDepthImage)
                + " colorView=0x" + Long.toHexString(this.offTargetColorView)
                + " depthView=0x" + Long.toHexString(this.offTargetDepthView)
                + " readback=none";
    }

    /**
     * The one-time move from the layout the pair was created in to the {@code GENERAL} layout every later command
     * assumes, with the clears that follow execution-ordered behind it. {@code TOP_OF_PIPE} as the source is the
     * same statement the frame images' first acquire makes: nothing has touched these images yet, and the move
     * discards the undefined content rather than preserving it.
     * <p>
     * Both destination access masks are the transfer write the clear performs, not the attachment accesses the
     * pass will perform: this barrier's destination stage is {@code TRANSFER}, and an access flag not supported by
     * a stage in the destination mask is exactly the kind of mismatch the validation layer rejects. The attachment
     * visibility is the following barrier's job.
     */
    private void recordOffTargetAcquire(VkCommandBuffer command) {
        int queueFamily = this.context.queueFamilyIndex();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);
            VkImageMemoryBarrier colorBarrier = barriers.get(0);
            colorBarrier.sType$Default()
                    .image(this.offTargetColorImage)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(0).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

            VkImageMemoryBarrier depthBarrier = barriers.get(1);
            depthBarrier.sType$Default()
                    .image(this.offTargetDepthImage)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(0).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barriers);
        }
    }

    /**
     * Makes the clears visible to what reads and writes the attachments afterwards. {@code vkCmdClearColorImage}
     * and {@code vkCmdClearDepthStencilImage} are transfer operations, so without this the pass that follows has an
     * execution dependency but no availability or visibility for the cleared values, and its attachment loads may
     * still see the content the clear replaced. The layouts do not change - the pair is GENERAL before and after.
     */
    private void recordOffTargetClearToAttachment(VkCommandBuffer command) {
        int colorAccess = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        int depthAccess = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        int queueFamily = this.context.queueFamilyIndex();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);
            VkImageMemoryBarrier colorBarrier = barriers.get(0);
            colorBarrier.sType$Default()
                    .image(this.offTargetColorImage)
                    .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(colorAccess);
            colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

            VkImageMemoryBarrier depthBarrier = barriers.get(1);
            depthBarrier.sType$Default()
                    .image(this.offTargetDepthImage)
                    .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(depthAccess);
            depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    0, null, null, barriers);
        }
    }

    /**
     * Releases the off-target pair and forgets its size, so the next {@link #ensureOffTarget} builds it afresh.
     * Safe to call with nothing created and safe to call twice; {@link #delete()} calls it, so every handle this
     * class ever creates is freed exactly once even when a resize replaces a pair mid-session.
     */
    private void releaseOffTarget() {
        VkDevice device = this.context.device();
        if (this.offTargetColorView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, this.offTargetColorView, null);
            this.offTargetColorView = VK_NULL_HANDLE;
        }
        if (this.offTargetColorImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, this.offTargetColorImage, null);
            this.offTargetColorImage = VK_NULL_HANDLE;
        }
        if (this.offTargetColorMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, this.offTargetColorMemory, null);
            this.offTargetColorMemory = VK_NULL_HANDLE;
        }
        if (this.offTargetDepthView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, this.offTargetDepthView, null);
            this.offTargetDepthView = VK_NULL_HANDLE;
        }
        if (this.offTargetDepthImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, this.offTargetDepthImage, null);
            this.offTargetDepthImage = VK_NULL_HANDLE;
        }
        if (this.offTargetDepthMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, this.offTargetDepthMemory, null);
            this.offTargetDepthMemory = VK_NULL_HANDLE;
        }
        this.offTargetWidth = 0;
        this.offTargetHeight = 0;
        this.offTargetAcquired = false;
    }

    @Override
    public String firstEntryGlError() {
        return null;
    }

    @Override
    public int sharedColorTexture() {
        return 0;
    }

    @Override
    public int sharedDepthTexture() {
        return 0;
    }

    @Override
    public boolean isSharedFramebuffer(int framebufferObject) {
        return false;
    }

    @Override
    public void noteGameFramebufferAdopted() {
        // There is no GL framebuffer to point at the images.
    }

    @Override
    public String takeTimingSummary() {
        return "glless target: no handoff, abandoned=" + this.abandonedFrames
                + " lastAbandon=[" + this.lastAbandonReason + "]"
                + " offTarget=" + (this.offTargetWidth > 0 ? this.offTargetWidth + "x" + this.offTargetHeight : "none")
                + " offTargetCreations=" + this.offTargetCreations
                + " offTargetClears=" + this.offTargetClears
                + " offTargetSelfTests=" + this.offTargetSelfTests;
    }

    @Override
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        VkDevice device = this.context.device();
        releaseOffTarget();
        if (this.waitSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device, this.waitSemaphore, null);
            this.waitSemaphore = VK_NULL_HANDLE;
        }
        if (this.doneSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device, this.doneSemaphore, null);
            this.doneSemaphore = VK_NULL_HANDLE;
        }
        if (this.colorView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, this.colorView, null);
            this.colorView = VK_NULL_HANDLE;
        }
        if (this.colorImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, this.colorImage, null);
            this.colorImage = VK_NULL_HANDLE;
        }
        if (this.colorMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, this.colorMemory, null);
            this.colorMemory = VK_NULL_HANDLE;
        }
        if (this.depthView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, this.depthView, null);
            this.depthView = VK_NULL_HANDLE;
        }
        if (this.depthImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, this.depthImage, null);
            this.depthImage = VK_NULL_HANDLE;
        }
        if (this.depthMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, this.depthMemory, null);
            this.depthMemory = VK_NULL_HANDLE;
        }
    }
}
