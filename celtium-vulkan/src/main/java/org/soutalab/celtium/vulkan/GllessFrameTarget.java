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
 * Two independent pairs of images live beside the frame's own, one per stream of draws that is not addressed to
 * the main framebuffer - the split, and the measurement that forced it, are in the section comment on {@link Pair}.
 * Each uses the frame's formats and the same {@code VK_IMAGE_LAYOUT_GENERAL} attachments, so the frame-compatible
 * render pass built by {@code VulkanTerrainPipeline.createFrameRenderPass} accepts it without a second pass;
 * {@link #clearOffTarget} and {@link #clearChainPair} record the clears the game's own framebuffer would have
 * received, and {@link #offTargetSelfTest} and {@link #chainPairSelfTest} record a clear-and-barrier sequence the
 * validation layer can check end to end.
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
     * The default-framebuffer pair: created by {@link #ensureOffTarget(int, int)} on first use, replaced when the
     * requested size changes, and released by {@link #delete()} like every other resource here. It is never handed
     * to GL and never leaves this queue family, so its only layout bookkeeping is the one-time UNDEFINED to GENERAL
     * move {@link #clearOffTarget} records before the first clear.
     * <p>
     * Its one writer is the game's draw stream with no framebuffer bound (identity 0).
     */
    private final Pair defaultPair = new Pair("default");
    /**
     * The second pair, for draws the game addresses to one of <em>its own</em> framebuffers - the entity-outline
     * silhouette is the measured case. {@link #ensureChainPair(int, int)} creates it on first use at the size the
     * game's framebuffer asks for, and the post chain adopts it as its input, so the chain reads the silhouette
     * rather than the copy of the whole scene the default pair holds.
     */
    private final Pair chainPair = new Pair("chain");

    private boolean frameOpen;
    private boolean deleted;
    private boolean firstAcquire = true;
    /** The first acquire clears both images whatever the game asked: their content starts undefined. */
    private boolean neverCleared = true;

    /**
     * The clear the game issued while <em>its own main framebuffer</em> was bound, latched by
     * {@code VulkanTerrainGpuBackend.bindFramebuffer} at the binding change that ends it.
     * <p>
     * The game clears three different framebuffers and only one of them is this image: its own main framebuffer is
     * the delivered frame, the entity-outline framebuffer is {@link #chainPair} and the default framebuffer is
     * {@link #defaultPair}. The stub records a clear's buffer mask and the colour it would use, but neither says
     * <em>which</em> framebuffer was bound when the game asked for it, and the request is consumed at the next
     * frame's acquire - by which time the previous frame's last {@code glClearColor} is the outline framebuffer's
     * transparent black. Applying that request here cleared the whole delivered image black once per glow frame, and
     * the sky kept that black wherever nothing drew over it (measured: {@code presentedBlackFraction=0.18984}, run
     * {@code glow688}). The latch is taken where both facts are still true, and is the only clear this image is
     * cleared with while one is held.
     */
    private boolean gameClearLatched;
    private int gameClearLatchedMask;
    private float gameClearLatchedRed;
    private float gameClearLatchedGreen;
    private float gameClearLatchedBlue;
    private float gameClearLatchedAlpha;
    private double gameClearLatchedDepth = 1.0D;
    /** How many clears came from the latch and how many still had to be read live, for this target's summary. */
    private int latchedGameClears;
    private int liveGameClears;
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
            // The depth format is chosen from what this device can do rather than fixed: AMD's RADV has no 24-bit
            // depth format at all and Intel's UHD 630 has no D16_UNORM_S8_UINT, so a constant is wrong on one of the
            // two GPUs this project verifies on. The usage gains SAMPLED_BIT because the contract the choice is made
            // against is "a depth attachment that can also be sampled", and an image created without it would not be
            // the image that predicate was verified for.
            int format = color ? VK_FORMAT_R8G8B8A8_UNORM
                    : VulkanDepthFormat.chosen(this.context.physicalDevice());
            int usage = color
                    ? VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
                    : VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
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
     * Latches a clear the game asked for while the framebuffer it names was still bound, so it can be applied to the
     * image that framebuffer stands for rather than to whatever is bound when the request is finally read. The caller
     * is {@code VulkanTerrainGpuBackend.bindFramebuffer}, which is the only place that still knows both facts: the
     * target the clear was issued against and the colour, depth and buffer mask in effect at that moment.
     *
     * @param mask  the {@code GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT} bits the game asked for
     * @param red   the clear colour in effect when the clear was issued
     * @param green the clear colour's green channel
     * @param blue  the clear colour's blue channel
     * @param alpha the clear colour's alpha channel
     * @param depth the depth value in effect when the clear was issued
     */
    public void noteGameClear(int mask, float red, float green, float blue, float alpha, double depth) {
        this.gameClearLatched = true;
        this.gameClearLatchedMask = mask;
        this.gameClearLatchedRed = red;
        this.gameClearLatchedGreen = green;
        this.gameClearLatchedBlue = blue;
        this.gameClearLatchedAlpha = alpha;
        this.gameClearLatchedDepth = depth;
    }

    /** {@return whether a clear addressed to this image is waiting to be recorded}, consuming nothing. */
    public boolean gameClearLatched() {
        return this.gameClearLatched;
    }

    /**
     * Clears what the game asked {@code glClear} to clear while no GL framebuffer existed to clear.
     * <p>
     * This is not a nicety: a depth buffer that is never cleared holds undefined values for the whole session, and
     * every draw that tests depth against them fails, which is a frame of nothing rather than a wrong pixel. The
     * request is carried over from the stub because {@code glClear} is the game's, not ours - the colour it names is
     * the sky or the menu background, and the depth is what makes the next draw's test meaningful.
     * <p>
     * A latched clear - one the game issued while its own main framebuffer was bound - is preferred, and carries the
     * colour and depth of the moment it was asked for. The live read is the fallback for a clear that was issued with
     * no binding change after it, and it is the path that used to run always: it reads {@code glClearColor} at the
     * acquire, one frame late, which is how the entity-outline framebuffer's transparent black came to clear the
     * delivered image (the black region). Both counts are reported, so a run says which path its frames took.
     * <p>
     * The image is in {@link #COLOR_RENDER_LAYOUT} here, which is {@code VK_IMAGE_LAYOUT_GENERAL}, one of the two
     * layouts {@code vkCmdClear*Image} accepts, so no extra transition is needed.
     */
    private void applyGameClears(VkCommandBuffer command) {
        boolean first = this.neverCleared;
        boolean latched = this.gameClearLatched;
        int clears;
        float latchedRed = 0.0F;
        float latchedGreen = 0.0F;
        float latchedBlue = 0.0F;
        float latchedAlpha = 0.0F;
        double latchedDepth = 1.0D;
        if (latched) {
            clears = this.gameClearLatchedMask;
            latchedRed = this.gameClearLatchedRed;
            latchedGreen = this.gameClearLatchedGreen;
            latchedBlue = this.gameClearLatchedBlue;
            latchedAlpha = this.gameClearLatchedAlpha;
            latchedDepth = this.gameClearLatchedDepth;
            this.gameClearLatched = false;
        } else {
            clears = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.consumePendingClears();
        }
        if (clears == 0 && !first) {
            return;
        }
        this.neverCleared = false;
        if (latched) {
            this.latchedGameClears++;
        } else {
            this.liveGameClears++;
        }
        // GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT
        boolean color = first || (clears & 0x4000) != 0 || (clears & 0x1000) != 0;
        boolean depth = first || (clears & 0x0100) != 0;
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkImageSubresourceRange range =
                    org.lwjgl.vulkan.VkImageSubresourceRange.calloc(stack).levelCount(1).layerCount(1);
            if (color) {
                float[] rgba = new float[4];
                if (latched) {
                    rgba[0] = latchedRed;
                    rgba[1] = latchedGreen;
                    rgba[2] = latchedBlue;
                    rgba[3] = latchedAlpha;
                } else {
                    org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearColor(rgba);
                }
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
                double depthValue = latched ? latchedDepth
                        : org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearDepth();
                org.lwjgl.vulkan.VkClearDepthStencilValue value =
                        org.lwjgl.vulkan.VkClearDepthStencilValue.calloc(stack);
                value.depth((float) depthValue).stencil(0);
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
    // The two off-target image pairs
    //
    // Not every draw the game issues is addressed to the main framebuffer, and the draws that are not do not all
    // belong to the same producer. The measured case is the entity outline: the silhouette is drawn into the game's
    // own entityOutlineFramebuffer before the chain that turns it into a glow, while the finished-frame composite -
    // issued with no framebuffer bound at all - is drawn once per frame into the default framebuffer. Both used to
    // land in one owned pair, so the outline chain, whose input is that pair, blurred a per-frame copy of the whole
    // scene rather than the silhouette (audit N04). There are two pairs now: {@link #defaultPair} takes identity 0
    // and {@link #chainPair} takes every other non-main identity, which is what makes each pair have exactly one
    // writer and the chain's input the silhouette.
    //
    // Both repeat the frame images' formats and usages - so the frame-compatible render pass built by
    // VulkanTerrainPipeline.createFrameRenderPass accepts them unchanged, GENERAL attachments included - and both
    // are created on demand at the size the game's framebuffer asks for rather than up front, because most sessions
    // never bind a second framebuffer at all.
    // ----------------------------------------------------------------------------------------------------------

    /**
     * {@return whether the default pair has been acquired}, i.e. moved out of UNDEFINED into the layout its render
     * pass assumes.
     * <p>
     * A caller that only remembers "this frame already cleared it" cannot see a pair that was recreated after that
     * clear: the new images are UNDEFINED again while the frame flag still says otherwise, and the next pass that
     * attaches or samples them is a layout error. This asks the pair itself.
     */
    public boolean offTargetAcquired() {
        return this.defaultPair.acquired;
    }

    /** The colour image the default pair renders into, or 0 before it exists. */
    public long offTargetColorImage() {
        checkAlive();
        return this.defaultPair.colorImage;
    }

    public long offTargetColorView() {
        checkAlive();
        return this.defaultPair.colorView;
    }

    /** The depth image view the default pair tests against, or 0 before the first {@link #ensureOffTarget}. */
    public long offTargetDepthView() {
        checkAlive();
        return this.defaultPair.depthView;
    }

    /** Whether the default pair exists and is exactly the requested size. */
    public boolean offTargetMatches(int width, int height) {
        return this.defaultPair.matches(width, height);
    }

    /** Makes the default pair exist at the requested size. See {@link Pair#ensure(int, int)}. */
    public void ensureOffTarget(int width, int height) {
        this.defaultPair.ensure(width, height);
    }

    /** Records the clears a pair receives before the game's draws for it land in it. */
    public void clearOffTarget(VkCommandBuffer command) {
        this.defaultPair.clear(command);
    }

    /** Records and reports a pair's clear-and-barrier sequence. See {@link Pair#selfTest}. */
    public String offTargetSelfTest(VkCommandBuffer command) {
        return this.defaultPair.selfTest(command);
    }

    /** {@return whether the chain pair exists}, i.e. whether some non-default framebuffer has drawn into it. */
    public boolean chainPairExists() {
        return this.chainPair.exists();
    }

    /** Whether the chain pair exists and is exactly the requested size. */
    public boolean chainPairMatches(int width, int height) {
        return this.chainPair.matches(width, height);
    }

    /** {@return whether the chain pair has been acquired}, i.e. moved out of UNDEFINED into its render layout. */
    public boolean chainPairAcquired() {
        return this.chainPair.acquired;
    }

    /** {@return whether the chain pair still owes the one-shot self-test for its current images}. */
    public boolean chainPairNeedsSelfTest() {
        return this.chainPair.needsSelfTest();
    }

    /** The colour image the chain pair renders into and the post chain adopts, or 0 before it exists. */
    public long chainPairColorImage() {
        checkAlive();
        return this.chainPair.colorImage;
    }

    public long chainPairColorView() {
        checkAlive();
        return this.chainPair.colorView;
    }

    /** The depth image view the chain pair tests against, or 0 before it exists. */
    public long chainPairDepthView() {
        checkAlive();
        return this.chainPair.depthView;
    }

    /** Makes the chain pair exist at the requested size. See {@link Pair#ensure(int, int)}. */
    public void ensureChainPair(int width, int height) {
        this.chainPair.ensure(width, height);
    }

    public void clearChainPair(VkCommandBuffer command) {
        this.chainPair.clear(command);
    }

    public String chainPairSelfTest(VkCommandBuffer command) {
        return this.chainPair.selfTest(command);
    }

    /** {@return one line per pair, naming the counters that used to be one unlabelled set}. */
    public String pairSummary() {
        return this.defaultPair.summary() + " " + this.chainPair.summary();
    }

    /**
     * Which of the two pairs an off-target draw identity renders into: {@link #defaultPair} for identity 0 - the
     * default framebuffer, which under the stub carries the game's finished-frame composites - and {@link #chainPair}
     * for every other non-main identity, the game's own framebuffers, of which the entity-outline silhouette is the
     * measured case.
     * <p>
     * The rule lives here because this class owns the two pairs; the backend routes by it and labels its attribution
     * counters with it, so the counters and the drawing cannot disagree. It is public and static - and touches no
     * device - so the CPU contract test can exercise it without a GPU, which is the same reason
     * {@code ImmediateGroupQueue.retireGroupsForTarget} is written that way.
     *
     * @param identity the framebuffer identity the game bound, as the frame mixin reports it
     * @return the pair label, "default-pair" or "chain-pair"
     */
    public static String pairLabelForIdentity(long identity) {
        return identity == 0L ? "default-pair" : "chain-pair";
    }

    /**
     * One owned colour+depth pair and its whole lifecycle: created on first use at the size the caller asks for,
     * replaced when that size changes, cleared once per frame from UNDEFINED, and released by {@link #delete()}.
     * <p>
     * The two instances differ in one thing only - the identity whose draws they receive - and that is what the
     * label on every counter of theirs says. The pair itself has no idea which stream feeds it, so the lifecycle is
     * written once here rather than twice beside two sets of fields.
     */
    private final class Pair {

        /** How this pair is named in every counter that describes it. */
        private final String label;
        private long colorImage;
        private long colorMemory;
        private long colorView;
        private long depthImage;
        private long depthMemory;
        private long depthView;
        private int width;
        private int height;
        /** False until the first clear has moved the pair out of the layout it was created in (UNDEFINED). */
        private boolean acquired;
        private int creations;
        private int clears;
        private int selfTests;

        private Pair(String label) {
            this.label = label;
        }

        private boolean exists() {
            return this.colorView != VK_NULL_HANDLE && this.depthView != VK_NULL_HANDLE;
        }

        private boolean matches(int requestedWidth, int requestedHeight) {
            return exists() && this.width == requestedWidth && this.height == requestedHeight;
        }

        /** {@return whether the images this pair holds now have not had their one legal-use self-test yet}. */
        private boolean needsSelfTest() {
            return exists() && this.selfTests < this.creations;
        }

        /**
         * Makes the pair exist at the requested size, creating it on first use and replacing it when the size
         * changes. A freed pair is destroyed here, so the caller must have settled the GPU before asking for a
         * different size - the same precondition the frame target's own re-creation has, and for the same reason:
         * the validation layer has already caught one destroy of an image still in use by a command buffer.
         * <p>
         * The images are created in {@code VK_IMAGE_LAYOUT_UNDEFINED}; {@link #clear} records the one-way move to
         * the {@code GENERAL} layout the render pass declares, so it (or {@link #selfTest}) must be recorded before
         * any render pass binds these views.
         */
        private void ensure(int requestedWidth, int requestedHeight) {
            checkAlive();
            if (requestedWidth <= 0 || requestedHeight <= 0) {
                throw new IllegalArgumentException(this.label + " pair size must be positive, got "
                        + requestedWidth + "x" + requestedHeight);
            }
            if (matches(requestedWidth, requestedHeight)) {
                return;
            }
            release();
            try {
                long[] color = allocateImage(true, requestedWidth, requestedHeight);
                this.colorImage = color[0];
                this.colorMemory = color[1];
                this.colorView = color[2];
                long[] depth = allocateImage(false, requestedWidth, requestedHeight);
                this.depthImage = depth[0];
                this.depthMemory = depth[1];
                this.depthView = depth[2];
                this.width = requestedWidth;
                this.height = requestedHeight;
                this.acquired = false;
                this.creations++;
            } catch (RuntimeException | LinkageError error) {
                // No half pair is left behind: a retry starts from nothing and delete() still frees whatever exists.
                release();
                throw error;
            }
        }

        /**
         * Records the clears the game's own framebuffer receives before it draws into it: colour (0,0,0,0) and
         * depth 1.0, the transparent black and far plane {@code glClear} leaves behind, in the {@code GENERAL}
         * layout both {@code vkCmdClearColorImage} and {@code vkCmdClearDepthStencilImage} accept.
         * <p>
         * Three things are recorded, in this order, because each one is what makes the next legal:
         * <ol>
         * <li>on first use only, one barrier per image moving UNDEFINED to GENERAL - the layout these images were
         * created in has undefined content, and a clear into an image the validation layer still believes is
         * UNDEFINED is a layout mismatch rather than a clear;</li>
         * <li>the colour clear and the depth/stencil clear, both in GENERAL;</li>
         * <li>a barrier from the transfer write the clears are to the attachment reads and writes the pass that
         * follows performs, so the pass cannot load the pre-clear content.</li>
         * </ol>
         * Recorded outside a render pass instance, as both clear commands require. The commands land in the
         * caller's command buffer, which is the one the pair's pass will be recorded into.
         */
        private void clear(VkCommandBuffer command) {
            checkAlive();
            if (command == null || command.address() == 0L) {
                throw new IllegalArgumentException(this.label + " pair clear needs a command buffer");
            }
            if (!exists()) {
                throw new IllegalStateException("ensure must run before the " + this.label + " pair is cleared");
            }
            try (MemoryStack stack = stackPush()) {
                if (!this.acquired) {
                    recordAcquire(command);
                    this.acquired = true;
                }
                VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack).levelCount(1).layerCount(1);

                VkClearColorValue color = VkClearColorValue.calloc(stack);
                color.float32(0, OFF_TARGET_CLEAR_RED);
                color.float32(1, OFF_TARGET_CLEAR_GREEN);
                color.float32(2, OFF_TARGET_CLEAR_BLUE);
                color.float32(3, OFF_TARGET_CLEAR_ALPHA);
                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                vkCmdClearColorImage(command, this.colorImage, VK_IMAGE_LAYOUT_GENERAL, color, range);

                VkClearDepthStencilValue depth = VkClearDepthStencilValue.calloc(stack);
                depth.depth(OFF_TARGET_CLEAR_DEPTH).stencil(0);
                range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                vkCmdClearDepthStencilImage(command, this.depthImage, VK_IMAGE_LAYOUT_GENERAL, depth, range);

                recordClearToAttachment(command);
                this.clears++;
            }
        }

        /**
         * Proves a pair can actually be written, which nothing before this did: it records the clears a real pass
         * starts from and the barriers around them, so the validation layer checks the image handles, the image
         * layouts, the aspect masks and the queue-family use of the whole sequence, and returns what was recorded
         * as one line.
         * <p>
         * No staging buffer is involved and none is needed: the point is not to read a pixel back but to show that
         * the commands record and are legal, which is what a wrong format, usage, aspect or layout would break. The
         * line names the pair and carries the two images' and views' handles, so a report can show they exist, are
         * distinct from the frame's own images, and were cleared at the size the game asked for.
         */
        private String selfTest(VkCommandBuffer command) {
            clear(command);
            this.selfTests++;
            return "glless " + this.label + " pair self-test #" + this.selfTests
                    + ": size=" + this.width + "x" + this.height
                    + " clearColor=(" + OFF_TARGET_CLEAR_RED + "," + OFF_TARGET_CLEAR_GREEN + ","
                    + OFF_TARGET_CLEAR_BLUE + "," + OFF_TARGET_CLEAR_ALPHA + ")"
                    + " clearDepth=" + OFF_TARGET_CLEAR_DEPTH + " clearStencil=0"
                    + " layout=GENERAL->GENERAL"
                    + " barrier=clearTransferWrite->attachmentReadWrite"
                    + " colorImage=0x" + Long.toHexString(this.colorImage)
                    + " depthImage=0x" + Long.toHexString(this.depthImage)
                    + " colorView=0x" + Long.toHexString(this.colorView)
                    + " depthView=0x" + Long.toHexString(this.depthView)
                    + " readback=none";
        }

        /**
         * The one-time move from the layout the pair was created in to the {@code GENERAL} layout every later
         * command assumes, with the clears that follow execution-ordered behind it. {@code TOP_OF_PIPE} as the
         * source is the same statement the frame images' first acquire makes: nothing has touched these images yet,
         * and the move discards the undefined content rather than preserving it.
         * <p>
         * Both destination access masks are the transfer write the clear performs, not the attachment accesses the
         * pass will perform: this barrier's destination stage is {@code TRANSFER}, and an access flag not supported
         * by a stage in the destination mask is exactly the kind of mismatch the validation layer rejects. The
         * attachment visibility is the following barrier's job.
         */
        private void recordAcquire(VkCommandBuffer command) {
            int queueFamily = context.queueFamilyIndex();
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);
                VkImageMemoryBarrier colorBarrier = barriers.get(0);
                colorBarrier.sType$Default()
                        .image(this.colorImage)
                        .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(0).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

                VkImageMemoryBarrier depthBarrier = barriers.get(1);
                depthBarrier.sType$Default()
                        .image(this.depthImage)
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
         * and {@code vkCmdClearDepthStencilImage} are transfer operations, so without this the pass that follows has
         * an execution dependency but no availability or visibility for the cleared values, and its attachment loads
         * may still see the content the clear replaced. The layouts do not change - the pair is GENERAL before and
         * after.
         */
        private void recordClearToAttachment(VkCommandBuffer command) {
            int colorAccess = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            int depthAccess = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                    | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            int queueFamily = context.queueFamilyIndex();
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);
                VkImageMemoryBarrier colorBarrier = barriers.get(0);
                colorBarrier.sType$Default()
                        .image(this.colorImage)
                        .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(colorAccess);
                colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

                VkImageMemoryBarrier depthBarrier = barriers.get(1);
                depthBarrier.sType$Default()
                        .image(this.depthImage)
                        .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(depthAccess);
                depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        0, null, null, barriers);
            }
        }

        /**
         * Releases the pair and forgets its size, so the next {@link #ensure} builds it afresh. Safe to call with
         * nothing created and safe to call twice; {@link #delete()} calls it, so every handle this class ever
         * creates is freed exactly once even when a resize replaces a pair mid-session.
         */
        private void release() {
            VkDevice device = context.device();
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
            this.width = 0;
            this.height = 0;
            this.acquired = false;
        }

        /** {@return this pair's own counters, labelled with the identity stream that feeds it}. */
        private String summary() {
            return this.label + "(size=" + (exists() ? this.width + "x" + this.height : "none")
                    + " creations=" + this.creations + " clears=" + this.clears
                    + " selfTests=" + this.selfTests + " acquired=" + this.acquired + ")";
        }
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
        // Per pair and labelled: the counters this used to print unlabelled were the default pair's, and with a
        // second pair a bare "offTargetClears=" would be read as if it covered both images.
        return "glless target: no handoff, abandoned=" + this.abandonedFrames
                + " lastAbandon=[" + this.lastAbandonReason + "]"
                + " gameClears=[latched=" + this.latchedGameClears + ",live=" + this.liveGameClears + "]"
                + " offTargetPairs=[" + pairSummary() + "]";
    }

    @Override
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        VkDevice device = this.context.device();
        this.defaultPair.release();
        this.chainPair.release();
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
