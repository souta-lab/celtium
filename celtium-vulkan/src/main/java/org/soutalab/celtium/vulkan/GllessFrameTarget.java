package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
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
    /**
     * Depth rests in the attachment layout, not GENERAL: on Intel gfx9 a writable GENERAL depth image has no HiZ
     * (no hierarchical depth culling, no fast depth clears), and nothing outside this renderer reads it - the GL-less
     * target is never handed to OpenGL. Clears and readbacks move it to their transfer layouts and back.
     */
    private static final int DEPTH_RENDER_LAYOUT = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
    private static final int DEPTH_TRANSFER_LAYOUT = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;

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
    /** Whether {@link #beginFrame}'s wait signal is outstanding (no submission has waited on it yet). */
    private boolean waitSignalled;
    private int abandonedWaitsConsumed;
    private int abandonedWaitFailures;
    private String lastAbandonedWaitFailure = "none";
    private long doneSemaphore;
    /** Whether the done semaphore has a signal in flight; cleared by the wait-only submission below. */
    private boolean doneSemaphoreOutstanding;
    /**
     * Present-wait semaphores, one per swapchain image, grown and never destroyed.
     * <p>
     * The Khronos guide states the rule this exists for: a present wait semaphore must be sized by the swapchain
     * image count and indexed by the acquired image, because the presentation engine knows only that index - a
     * semaphore sized by the frame index can be signalled again by a new frame while the presentation engine is
     * still waiting on it from a previous one. Growing rather than re-creating matters because the swapchain
     * recreation guidance defers destroying an old swapchain's semaphores until the first present of the new one
     * has completed; a grow-only array has nothing to defer.
     */
    private long[] presentDoneSemaphores;
    /** The done semaphore a producer last signalled, which is what {@link #consumeDone()} must wait on. */
    private long lastSignalledDone;

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
    /**
     * The chain pair per framebuffer identity. Two non-main identities used to share one Pair: the second one's
     * clear or draw then reached the first one's content between its draw and the read that sampled it - measured
     * as the world going black when the loading overlay's FBO and the frame's own passes alternated (legs
     * bisect4/5: acceptedForOtherTargets=11942 with pairClearsRefusedOtherIdentity=3), and as the fixture's second
     * framebuffer never holding its quad (leg fboalias-amd: every pixel check failed). Each identity now owns its
     * own pair, which is what the BH-14 census's "widest pair must fall to 1" acceptance meant. A live framebuffer
     * keeps its pair until its owner deletes it; an arbitrary size cap would discard images still needed by later passes.
     */
    // Primitive-keyed: existingChainPair runs per drawn group.
    private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<Pair> chainPairs =
            new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<Pair>();
    /** Reverse lookup for the sampling hot path; only live colour images have entries. */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap chainPairOwners =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    /** Chain-pair image sets created so far; the source of {@link #chainPairGeneration}. */
    private long chainPairGenerations;

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
     * the delivered frame, the entity-outline framebuffer is {@link #chainPairs} and the default framebuffer is
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
    /** Frames whose acquire found no clear request, which leave the previous frame's picture in place. */
    private int framesWithoutClear;

    /** {@return how many clears this image has been given}, read at frame boundaries to see a frame that had none. */
    public int clearsApplied() {
        return this.latchedGameClears + this.liveGameClears;
    }

    /** Frames the backend saw start without any clear in between; counted at the frame boundary and reported here. */
    private long framesWithNoClear;

    /** Sets the count the summary reports. */
    public void setFramesWithNoClear(long frames) {
        this.framesWithNoClear = frames;
    }

    /** How far behind the game the presented frame was, as {max, at most one, more than one}. */
    private long presentLagMax;
    private long presentLagOne;
    private long presentLagMore;

    /** Sets the present-lag counters the summary reports. */
    public void setPresentLag(long max, long one, long more) {
        this.presentLagMax = max;
        this.presentLagOne = one;
        this.presentLagMore = more;
    }
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
            if (VulkanContext.allocateMemory(device, allocate, handle) != VK_SUCCESS) {
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
                VulkanContext.freeMemory(device, memory);
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
        this.context.celtium$flushBatchedSubmits();
        try (MemoryStack stack = stackPush()) {
            LongBuffer signal = stack.mallocLong(1);
            signal.put(0, this.waitSemaphore);
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default().pSignalSemaphores(signal);
            VulkanContext.queueSubmitCalls++;
                        check(vkQueueSubmit(this.context.queue(), submit, VK_NULL_HANDLE),
                    "vkQueueSubmit (glless beginFrame signal)");
        }
    }

    /**
     * {@return whether the bridge's done semaphore already has a signal in flight}.
     * <p>
     * A binary semaphore may only be signalled again after its previous signal has been waited on, and the rule is
     * written in this file's own documentation. The present path can present twice before an {@code endFrame}
     * consumed the first signal - a re-present or a second {@code beginFrame} - and the second signal is
     * {@code VUID-vkQueueSubmit-pSignalSemaphores-00067}, measured as ten messages in one leg on the present path
     * with the semaphore traced to this pair rather than the swapchain's. The present path asks this before handing
     * the semaphore to the present submit, the same way {@code VulkanContext} gates its upload batch.
     */
    public boolean doneSemaphoreSignalOutstanding() {
        return this.doneSemaphoreOutstanding;
    }

    /** Records that a submission has signalled the done semaphore and nothing has waited on it yet. */
    public void noteDoneSemaphoreSignalled() {
        this.doneSemaphoreOutstanding = true;
    }

    /**
     * A submission that only waits on {@link #doneSemaphore()}: the frame's last submission signals it, so this
     * consumes the signal and resets the semaphore for the next frame - the job GL's wait did under the shared
     * target.
     */
    private void consumeDone() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer wait = stack.mallocLong(1);
            // The producer may have signalled a per-image present semaphore rather than the frame-close one, so the
            // wait follows whichever was recorded: waiting on the frame-close semaphore when the present signalled
            // the per-image one would block forever.
            wait.put(0, this.lastSignalledDone != 0L ? this.lastSignalledDone : this.doneSemaphore);
            // The wait below is what makes the next signal legal: this is the one place the bridge's done semaphore
            // is consumed, so clearing the flag here keeps the invariant in the target that owns the semaphore.
            this.doneSemaphoreOutstanding = false;
            this.context.celtium$flushBatchedSubmits();
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                    .waitSemaphoreCount(wait.remaining()).pWaitSemaphores(wait)
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
            VulkanContext.queueSubmitCalls++;
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
            // The previous frame was never ended, so its wait signal is still outstanding and this frame's signal would
            // be the second on the same binary semaphore.
            consumeOutstandingWait("beginFrame found the previous frame open");
        }
        if (FRAME_SEMAPHORES) {
            signalWait();
            this.waitSignalled = true;
        }
        // Without the pair there is no signal to leave outstanding: the wait-signalled flag staying false is what
        // keeps consumeOutstandingWait a no-op on abandoned frames, which is the state this path needs.
        this.frameOpen = true;
    }

    /**
     * Whether the wait/done semaphore pair runs at all here.
     * <p>
     * Under the shared target the pair carries content between two producers on different queues; under this
     * target the same queue sees every submission in order, so the pair is ceremony: three queue packets per stage
     * (beginFrame's signal-only submission, the work submission's own semaphore pair, endFrame's wait-only
     * submission) for ordering the queue already guarantees. Measured on a held leg: the frame pass's own submit
     * work is the renderer's largest steady-state line item ({@code terrain+flush} p50 ~7.5 ms of a ~10 ms world
     * pass, with per-stage submit cycles of three). The pair is skipped by default and
     * {@code -Dceltium.vulkan.gllessFrameSemaphores=true} keeps it.
     */
    private static final boolean FRAME_SEMAPHORES =
            Boolean.parseBoolean(System.getProperty("celtium.vulkan.gllessFrameSemaphores", "false"));

    @Override
    public boolean needsFrameSemaphores() {
        return FRAME_SEMAPHORES;
    }

    /**
     * The frame's own submission waited on {@link #waitSemaphore()}: the signal is consumed.
     * <p>
     * Called by the backend where it submits with the frame's semaphores, and by {@code submitSignalOnly}. Without this
     * the target cannot tell a frame that reached the queue from one that was abandoned before its first submission, and
     * the two need opposite handling.
     */
    public void noteWaitConsumed() {
        this.waitSignalled = false;
    }

    /** How many abandoned frames had their outstanding wait signal consumed. */
    public int abandonedWaitsConsumed() {
        return this.abandonedWaitsConsumed;
    }

    /**
     * Consumes the wait signal a begun frame issued when nothing has waited on it.
     * <p>
     * A binary semaphore may not be signalled twice without an intervening wait. The measured chain: a frame abandoned
     * at frame 0 (a failed window flush) left {@link #waitSemaphore()} signalled, the next {@code beginFrame} signalled it
     * again, the validation layer reported {@code VUID-vkQueueSubmit-pSignalSemaphores-00067}, and RADV answered
     * {@code VK_ERROR_DEVICE_LOST} - after which every submit failed and {@code settleGpu} latched the renderer. The
     * failure is swallowed and counted: this runs inside abandonment, where throwing would replace a dropped frame with
     * a crash.
     */
    private void consumeOutstandingWait(String reason) {
        if (!this.waitSignalled || this.waitSemaphore == VK_NULL_HANDLE) {
            return;
        }
        this.waitSignalled = false;
        try (MemoryStack stack = stackPush()) {
            LongBuffer wait = stack.mallocLong(1);
            wait.put(0, this.waitSemaphore);
            this.context.celtium$flushBatchedSubmits();
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                    .waitSemaphoreCount(wait.remaining()).pWaitSemaphores(wait)
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
            VulkanContext.queueSubmitCalls++;
                        check(vkQueueSubmit(this.context.queue(), submit, VK_NULL_HANDLE),
                    "vkQueueSubmit (glless abandoned frame wait)");
            this.abandonedWaitsConsumed++;
            if (this.abandonedWaitsConsumed <= 4) {
                System.out.println("[Celtium] CELTIUM_ABANDON_WAIT_CONSUMED reason=" + reason.replace(' ', '_')
                        + " (the frame was begun and abandoned before any submission waited on its wait semaphore;"
                        + " leaving it signalled makes the next beginFrame signal a binary semaphore twice, which the"
                        + " validation layer reports as VUID-vkQueueSubmit-pSignalSemaphores-00067 and RADV answers with"
                        + " VK_ERROR_DEVICE_LOST)");
            }
        } catch (RuntimeException | LinkageError error) {
            this.abandonedWaitFailures++;
            this.lastAbandonedWaitFailure = reason + ": " + error;
        }
    }

    /** How many attempts to consume an abandoned frame's wait signal failed (a device already lost, typically). */
    public int abandonedWaitFailures() {
        return this.abandonedWaitFailures;
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
        if (FRAME_SEMAPHORES) {
            consumeDone();
        }
        // With the pair skipped nothing was ever signalled, so there is nothing to consume: the wait-only
        // submission is the third queue packet per stage this mode exists to remove.
        this.frameOpen = false;
        this.waitSignalled = false;
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
            // A frame begun and abandoned before any submission waited on the semaphore must not leave it signalled:
            // that is the measured VUID-00067 -> VK_ERROR_DEVICE_LOST chain.
            consumeOutstandingWait(reason);
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

    /** {@return the delivered image's width}, which the probe reports instead of assuming the capture's size. */
    public int width() {
        return this.width;
    }

    /** {@return the delivered image's height}. */
    public int height() {
        return this.height;
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
        // With the pair skipped there is nothing to wait on: callers pass the null handle through, and the submit
        // paths that compact nulls drop it from the wait list entirely.
        return FRAME_SEMAPHORES ? this.waitSemaphore : VK_NULL_HANDLE;
    }

    @Override
    public long doneSemaphore() {
        checkAlive();
        return FRAME_SEMAPHORES ? this.doneSemaphore : VK_NULL_HANDLE;
    }

    /**
     * {@return the present-wait semaphore for a swapchain image}, created on first use.
     * <p>
     * The array grows and existing entries are never destroyed, so a swapchain recreation that changes the image
     * count cannot leave an in-flight present referring to a destroyed semaphore.
     */
    public long presentDoneSemaphore(int imageIndex) {
        checkAlive();
        // A signalled-but-never-waited binary semaphore is a VUID-00067 the second time it is signalled: with the
        // pair skipped nothing consumes these, so they are not produced either.
        if (!FRAME_SEMAPHORES) {
            return VK_NULL_HANDLE;
        }
        if (imageIndex < 0) {
            return this.doneSemaphore;
        }
        if (this.presentDoneSemaphores == null || imageIndex >= this.presentDoneSemaphores.length) {
            int wanted = Math.max(imageIndex + 1, this.presentDoneSemaphores == null ? 2
                    : this.presentDoneSemaphores.length * 2);
            long[] grown = java.util.Arrays.copyOf(
                    this.presentDoneSemaphores == null ? new long[0] : this.presentDoneSemaphores, wanted);
            try (MemoryStack stack = stackPush()) {
                LongBuffer handle = stack.mallocLong(1);
                VkSemaphoreCreateInfo info = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
                for (int i = 0; i < grown.length; i++) {
                    if (grown[i] == 0L) {
                        if (vkCreateSemaphore(this.context.device(), info, null, handle) != VK_SUCCESS) {
                            throw new IllegalStateException("vkCreateSemaphore(glless present done) failed");
                        }
                        grown[i] = handle.get(0);
                    }
                }
            }
            this.presentDoneSemaphores = grown;
        }
        return this.presentDoneSemaphores[imageIndex];
    }

    /** Records which done semaphore a producer signalled, so the wait-only submission waits on that one. */
    public void noteDoneSignalled(long semaphore) {
        this.lastSignalledDone = semaphore;
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
     * The owner state a recording mutates, so a recording that is thrown away can put it back (BH-26).
     * <p>
     * {@link #recordAcquireBarriers} does not only record: it also advances this object's idea of the world - the
     * first-acquire flag, the two image layouts, the "never cleared" flag, the latched clear it consumes, and both
     * pairs' acquired flags. When the command buffer is reset before any submission (the backend's abort path), the
     * recorded commands are gone but those flags stayed advanced, so the re-record skipped the first transition and
     * the clear it had already consumed. The snapshot is taken before the record and handed back on the abort.
     */
    public static final class RecordingSnapshot {
        private boolean firstAcquire;
        private boolean neverCleared;
        private int colorImageLayout;
        private int depthImageLayout;
        private boolean gameClearLatched;
        private int gameClearLatchedMask;
        private float latchedRed;
        private float latchedGreen;
        private float latchedBlue;
        private float latchedAlpha;
        private double latchedDepth;
        private boolean defaultAcquired;
        // Parallel arrays, not two boxed maps: a snapshot is taken for every window flush (~10 a frame) and covers
        // every chain pair, while a rollback that reads it back is rare.
        private int chainCount;
        private long[] chainKeys;
        private boolean[] chainAcquired;
        private int[] chainColorLayouts;
    }

    /** {@return the owner state as it is now}, to be handed to {@link #rollbackRecording} if the record is discarded. */
    public RecordingSnapshot snapshotRecording() {
        RecordingSnapshot snapshot = new RecordingSnapshot();
        snapshot.firstAcquire = this.firstAcquire;
        snapshot.neverCleared = this.neverCleared;
        snapshot.colorImageLayout = this.colorImageLayout;
        snapshot.depthImageLayout = this.depthImageLayout;
        snapshot.gameClearLatched = this.gameClearLatched;
        snapshot.gameClearLatchedMask = this.gameClearLatchedMask;
        snapshot.latchedRed = this.gameClearLatchedRed;
        snapshot.latchedGreen = this.gameClearLatchedGreen;
        snapshot.latchedBlue = this.gameClearLatchedBlue;
        snapshot.latchedAlpha = this.gameClearLatchedAlpha;
        snapshot.latchedDepth = this.gameClearLatchedDepth;
        snapshot.defaultAcquired = this.defaultPair.acquired;
        int size = this.chainPairs.size();
        snapshot.chainKeys = new long[size];
        snapshot.chainAcquired = new boolean[size];
        snapshot.chainColorLayouts = new int[size];
        int n = 0;
        for (java.util.Map.Entry<Long, Pair> entry : this.chainPairs.entrySet()) {
            snapshot.chainKeys[n] = entry.getKey().longValue();
            snapshot.chainAcquired[n] = entry.getValue().acquired;
            snapshot.chainColorLayouts[n] = entry.getValue().colorImageLayout;
            n++;
        }
        snapshot.chainCount = n;
        return snapshot;
    }

    /**
     * Puts the owner state back to what {@link #snapshotRecording} saw, because the commands that advanced it were
     * never submitted. Counted ({@code recordingRollbacks}) so a run says how often a recording was discarded.
     */
    public void rollbackRecording(RecordingSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        this.firstAcquire = snapshot.firstAcquire;
        this.neverCleared = snapshot.neverCleared;
        this.colorImageLayout = snapshot.colorImageLayout;
        this.depthImageLayout = snapshot.depthImageLayout;
        this.gameClearLatched = snapshot.gameClearLatched;
        this.gameClearLatchedMask = snapshot.gameClearLatchedMask;
        this.gameClearLatchedRed = snapshot.latchedRed;
        this.gameClearLatchedGreen = snapshot.latchedGreen;
        this.gameClearLatchedBlue = snapshot.latchedBlue;
        this.gameClearLatchedAlpha = snapshot.latchedAlpha;
        this.gameClearLatchedDepth = snapshot.latchedDepth;
        this.defaultPair.acquired = snapshot.defaultAcquired;
        for (int i = 0; i < snapshot.chainCount; i++) {
            Pair pair = this.chainPairs.get(snapshot.chainKeys[i]);
            if (pair != null) {
                pair.acquired = snapshot.chainAcquired[i];
                pair.colorImageLayout = snapshot.chainColorLayouts[i];
            }
        }
        this.recordingRollbacks++;
    }

    /** Recordings discarded before submission and rolled back (BH-26). */
    private int recordingRollbacks;

    /** {@return how many recordings were discarded and rolled back}. */
    public int recordingRollbacks() {
        return this.recordingRollbacks;
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
    /** Clears dropped because they named one of the game's own framebuffers rather than the delivered frame. */
    private int gameClearsDroppedForeignTarget;
    /** The last such clear's framebuffer name, so the drop is attributable. */
    private int lastDroppedClearOwner;

    /** {@return clears dropped because they named one of the game's own framebuffers, not the delivered frame}. */
    public int gameClearsDroppedForeignTargetCount() {
        return this.gameClearsDroppedForeignTarget;
    }

    /** {@return the framebuffer name of the last dropped clear}, or -1 when none was dropped. */
    public int lastDroppedClearOwnerName() {
        return this.lastDroppedClearOwner;
    }

    private void applyGameClears(VkCommandBuffer command) {
        boolean first = this.neverCleared;
        boolean latched = this.gameClearLatched;
        boolean initializeOnly = false;
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
            int owner = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.pendingClearDrawFbo();
            boolean ownerIsMain = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.pendingClearIsMain();
            if (owner >= 0 && !ownerIsMain) {
                // The clear names one of the game's own framebuffers. Every non-main identity shares the chain pair,
                // so applying it here wipes whatever another identity drew into that image - measured as the
                // panorama's faces being cleared to white between their draw and the copy that reads them. The clear
                // belongs to an image this path does not own, so it is dropped and counted rather than applied.
                clears = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.consumePendingClears();
                this.gameClearsDroppedForeignTarget++;
                this.lastDroppedClearOwner = owner;
                if (!first) {
                    return;
                }
                // Dropping another target's request must not leave our first attachment load undefined.
                // Initialize this image with its own safe defaults, not the foreign target's live clear values.
                initializeOnly = true;
                clears = 0;
            } else {
                clears = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.consumePendingClears();
            }
        }
        if (clears == 0 && !first) {
            // The game cleared none of its framebuffers this frame, so this image keeps the previous frame's pixels.
            // Counted rather than assumed absent: a frame that draws the world always clears, and a frame that does
            // not is exactly the shape a ghosted picture leaves behind (moving text staying behind the new frame).
            this.framesWithoutClear++;
            return;
        }
        this.neverCleared = false;
        if (latched) {
            this.latchedGameClears++;
        } else {
            this.liveGameClears++;
        }
        // GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT
        // The frame has color and D32 depth only; accum/stencil requests must not repaint its color image.
        boolean color = first || (clears & 0x4000) != 0;
        boolean depth = first || (clears & 0x0100) != 0;
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkImageSubresourceRange range =
                    org.lwjgl.vulkan.VkImageSubresourceRange.calloc(stack).levelCount(1).layerCount(1);
            if (color) {
                float[] rgba = new float[4];
                if (latched || initializeOnly) {
                    rgba[0] = latchedRed;
                    rgba[1] = latchedGreen;
                    rgba[2] = latchedBlue;
                    rgba[3] = latchedAlpha;
                } else {
                    // The frame image is the main framebuffer: its clear uses the values that clear was issued with.
                    org.soutalab.celtium.vulkan.gl.CeltiumGlStub.mainClearValues(rgba);
                }
                org.lwjgl.vulkan.VkClearColorValue value =
                        org.lwjgl.vulkan.VkClearColorValue.calloc(stack);
                value.float32(0, rgba[0]);
                value.float32(1, rgba[1]);
                value.float32(2, rgba[2]);
                // Alpha 1 whatever the game's clear colour says, because this image *is* the default framebuffer.
                // GL's default framebuffer normally has no alpha bits, so the alpha a clear writes there is not
                // observable and every read of it - a copy into a texture, a shader sampling the FBO colour texture -
                // yields 1. Measured on the main menu (round 215): the game clears with (1,1,1,0), this image kept
                // alpha 0, `glCopyTexSubImage2D` carried that 0 into the panorama's DynamicTexture, and the blur and
                // final composite quads there blend with SRC_ALPHA - so they drew nothing at all and the menu's
                // background stayed the cleared white instead of the panorama (menu dump mean=(169,168,169) with the
                // cube visible only where it was drawn directly, against the accepted (98,102,96)).
                value.float32(3, 1.0f);
                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                if (COLOR_CLEAR_TRANSFER) {
                    recordColorToTransfer(command, this.colorImage);
                }
                org.lwjgl.vulkan.VK10.vkCmdClearColorImage(command, this.colorImage,
                        COLOR_CLEAR_TRANSFER ? DEPTH_TRANSFER_LAYOUT : COLOR_RENDER_LAYOUT, value, range);
            }
            if (depth) {
                double mainDepth = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.mainClearValues(new float[4]);
                double depthValue = latched || initializeOnly ? latchedDepth
                        : Double.isNaN(mainDepth) ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearDepth() : mainDepth;
                org.lwjgl.vulkan.VkClearDepthStencilValue value =
                        org.lwjgl.vulkan.VkClearDepthStencilValue.calloc(stack);
                value.depth((float) depthValue).stencil(0);
                range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                recordDepthToTransfer(command, this.depthImage, DEPTH_RENDER_LAYOUT);
                org.lwjgl.vulkan.VK10.vkCmdClearDepthStencilImage(command, this.depthImage, DEPTH_TRANSFER_LAYOUT,
                        value, range);
            }
            recordClearToAttachment(command, color, depth);
        }
    }

    /**
     * Moves a depth image from its attachment layout to the transfer layout a clear needs. Everything that can have
     * touched it before - earlier windows' depth tests and writes, an earlier clear - is covered by ALL_COMMANDS.
     */
    private void recordDepthToTransfer(VkCommandBuffer command, long image, int from) {
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().image(image)
                    .oldLayout(from).newLayout(DEPTH_TRANSFER_LAYOUT)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .srcAccessMask(from == VK_IMAGE_LAYOUT_UNDEFINED ? 0
                            : VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier);
        }
    }

    /**
     * Colour clears run in TRANSFER_DST_OPTIMAL instead of GENERAL, which lets Intel's driver use a CCS fast clear where
     * it otherwise writes the whole surface: under INTEL_MEASURE on the UHD 630 the three "slow color clear"s a frame
     * (263 us) became fast clears (17 us) plus partial resolves (62 us), and the draws that follow on the compressed
     * surface cost less as well. {@code -Dceltium.vulkan.colorClearTransfer=false} clears in GENERAL again.
     */
    static final boolean COLOR_CLEAR_TRANSFER = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.colorClearTransfer"));

    /** Moves a GENERAL colour image to the transfer layout a clear uses, after whatever touched it before. */
    private void recordColorToTransfer(VkCommandBuffer command, long image) {
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().image(image)
                    .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(DEPTH_TRANSFER_LAYOUT)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier);
        }
    }

    /** {@return the layout the main depth image rests in}, for a readback that must name it. */
    public int depthLayout() {
        return this.depthImageLayout == VK_IMAGE_LAYOUT_UNDEFINED ? DEPTH_RENDER_LAYOUT : this.depthImageLayout;
    }

    /** Transfer clears run after acquire; their writes must be visible to the following attachment loads. */
    private void recordClearToAttachment(VkCommandBuffer command, boolean color, boolean depth) {
        if (!color && !depth) {
            return;
        }
        int queueFamily = this.context.queueFamilyIndex();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc((color ? 1 : 0) + (depth ? 1 : 0), stack);
            int index = 0;
            if (color) {
                VkImageMemoryBarrier barrier = barriers.get(index++);
                barrier.sType$Default().image(this.colorImage)
                        .oldLayout(COLOR_CLEAR_TRANSFER ? DEPTH_TRANSFER_LAYOUT : COLOR_RENDER_LAYOUT)
                        .newLayout(COLOR_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        // The readback (glReadPixels' copy) and the composite's sampling are the consumers a
                        // mid-frame clear must publish to, not only the attachment the next pass draws through.
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                                | VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_SHADER_READ_BIT);
                barrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            }
            if (depth) {
                VkImageMemoryBarrier barrier = barriers.get(index);
                barrier.sType$Default().image(this.depthImage)
                        .oldLayout(DEPTH_TRANSFER_LAYOUT).newLayout(DEPTH_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                                | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                barrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            }
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    0, null, null, barriers);
        }
    }

    @Override
    public void recordReleaseBarriers(VkCommandBuffer commandBuffer) {
        recordLayoutBarriers(commandBuffer, true);
        this.colorImageLayout = VK_IMAGE_LAYOUT_GENERAL;
        // Depth stays in its attachment layout across windows: moving it to GENERAL and back at every window would
        // resolve HiZ each time on the hardware this layout exists for.
        this.depthImageLayout = DEPTH_RENDER_LAYOUT;
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
                    // Acquire can be followed by a game transfer clear before the attachment pass.
                    .dstAccessMask(release ? 0 : (colorAccess | VK_ACCESS_TRANSFER_WRITE_BIT));
            colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

            VkImageMemoryBarrier depthBarrier = barriers.get(1);
            depthBarrier.sType$Default()
                    .image(this.depthImage)
                    .oldLayout(release ? DEPTH_RENDER_LAYOUT : this.depthImageLayout)
                    .newLayout(DEPTH_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(release && !(this.firstAcquire && !release) ? depthAccess : 0)
                    .dstAccessMask(release ? 0 : (depthAccess | VK_ACCESS_TRANSFER_WRITE_BIT));
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
    // and {@link #chainPairs} takes every other non-main identity, which is what makes each pair have exactly one
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

    /** The default pair's actual extent, or null before allocation. */
    public int[] offTargetExtent() {
        return this.defaultPair.exists() ? new int[] {this.defaultPair.width, this.defaultPair.height} : null;
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

    /**
     * Records the depth clear the game gives the default framebuffer, and leaves its colour alone.
     * <p>
     * This is the game's own clear for this framebuffer, read from the sources: with no world loaded,
     * {@code EntityRenderer.updateCameraAndRender} runs {@code GlStateManager.clear(256)} - depth only - before
     * {@code currentScreen.drawScreen} (EntityRenderer.java:1171-1174). The colour is never cleared, which is what makes
     * the main menu's panorama work: {@code GuiMainMenu.renderSkybox} draws the six faces into this framebuffer and
     * copies them into the skybox texture, so each frame accumulates over the previous frame's faces (the classic
     * feedback loop the blur passes are built on). Clearing the colour to transparent black every frame - which is what
     * this pair used to receive - left that texture 28 % opaque and nearly black, so the menu's background stayed the
     * flat gradient: measured on the GL-less menu, the served copy of the 256x256 region read
     * {@code opaque=18763/65536 firstOpaque=0xff020101}.
     */
    public void clearOffTarget(VkCommandBuffer command) {
        this.defaultPair.clearDepthOnly(command);
    }

    /** Records and reports a pair's clear-and-barrier sequence. See {@link Pair#selfTest}. */
    public String offTargetSelfTest(VkCommandBuffer command) {
        return this.defaultPair.selfTest(command);
    }

    /**
     * {@return the chain pair an identity owns}, creating it on first ask. Creating is what a binding's ensures and pass attaches need; callers that only look up - the
     * census, the summary, a report - use {@link #existingChainPair} so the pool does not grow on a read.
     */
    private Pair chainPairFor(long identity) {
        Pair pair = this.chainPairs.get(identity);
        if (pair != null) {
            return pair;
        }
        Pair created = new Pair("chain-" + Long.toHexString(identity));
        this.chainPairs.put(identity, created);
        return created;
    }

    /** {@return the identity's chain pair without creating it}, null when none exists yet. */
    private Pair existingChainPair(long identity) {
        return this.chainPairs.get(identity);
    }

    /** Retire the pair after its framebuffer and owned texture have been deleted. */
    public void retireChainPair(long identity) {
        Pair pair = this.chainPairs.remove(identity);
        if (pair != null) {
            if (pair.colorImage != 0L) this.chainPairOwners.remove(pair.colorImage);
            pair.retire();
        }
    }

    /** {@return whether the identity's chain pair exists}, i.e. whether that framebuffer has drawn into it. */
    public boolean chainPairExists(long identity) {
        Pair pair = existingChainPair(identity);
        return pair != null && pair.exists();
    }

    /** Whether the identity's chain pair exists and is exactly the requested size. */
    public boolean chainPairMatches(long identity, int width, int height) {
        Pair pair = existingChainPair(identity);
        return pair != null && pair.matches(width, height);
    }

    /** {@return whether the identity's chain pair has been acquired}, i.e. moved out of UNDEFINED into its render layout. */
    public boolean chainPairAcquired(long identity) {
        Pair pair = existingChainPair(identity);
        return pair != null && pair.acquired;
    }

    /** {@return whether the identity's chain pair still owes the one-shot self-test for its current images}. */
    public boolean chainPairNeedsSelfTest(long identity) {
        Pair pair = existingChainPair(identity);
        return pair != null && pair.needsSelfTest();
    }

    /** The colour image the identity's chain pair renders into and its consumers sample, or 0 before it exists. */
    public long chainPairColorImage(long identity) {
        checkAlive();
        Pair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.colorImage;
    }

    /**
     * {@return the layout the chain pair's colour image is actually in}.
     * <p>
     * The fact the two sides of {@code VUID-vkCmdDraw-None-09600} are compared against: the pair's pass declares the
     * constant {@code GENERAL}, and a published wrapper that samples the same image records
     * {@code SHADER_READ_ONLY_OPTIMAL}. This is what the image itself was last moved to.
     */
    public int chainPairColorLayout(long identity) {
        checkAlive();
        Pair pair = existingChainPair(identity);
        return pair == null ? VK_IMAGE_LAYOUT_UNDEFINED : pair.colorLayout();
    }

    /**
     * {@return the layout a chain pair's render pass declares for its colour attachment}.
     * <p>
     * A constant ({@code COLOR_RENDER_LAYOUT}) rather than a per-frame value: the pass is built with it, so this is the
     * declaration side of the comparison the pair's actual layout is measured against.
     */
    public int chainPairDeclaredLayout() {
        return COLOR_RENDER_LAYOUT;
    }

    /**
     * Records a layout the chain pair's colour image has been moved to by someone outside this class.
     * <p>
     * The sampling side moves it through the published wrapper's own barrier
     * ({@code VulkanGlTexture.ensureSampledLayout}/{@code ensureGeneralLayout}), which this class cannot see, so the
     * caller that records those barriers reports them here. Without it the pair's record would claim GENERAL while the
     * image was left read-only, which is the message this field exists to make readable.
     */
    public void noteChainPairColorLayout(long identity, int layout) {
        checkAlive();
        Pair pair = existingChainPair(identity);
        if (pair != null) {
            pair.noteColorLayout(layout);
        }
    }

    public long chainPairColorView(long identity) {
        checkAlive();
        Pair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.colorView;
    }

    /** The depth image view the identity's chain pair tests against, or 0 before it exists. */
    public long chainPairDepthView(long identity) {
        checkAlive();
        Pair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.depthView;
    }

    /** Makes the identity's chain pair exist at the requested size. See {@link Pair#ensure(int, int)}. */
    public void ensureChainPair(long identity, int width, int height) {
        Pair pair = chainPairFor(identity);
        if (pair.matches(width, height)) return;
        if (pair.colorImage != 0L) this.chainPairOwners.remove(pair.colorImage);
        try {
            pair.ensure(width, height);
        } finally {
            // Invalid dimensions can leave the old pair alive; allocation failure can leave no image.
            if (pair.colorImage != 0L) this.chainPairOwners.put(pair.colorImage, identity);
        }
    }

    /**
     * A framebuffer can be sampled before its first draw (vanilla's empty outline target does this every frame).
     * Initialize the owned attachment, rather than manufacturing an unwritten texture mirror under its name.
     * This one-time bootstrap completes before publication; subsequent sampling adds no submission or wait.
     */
    public void initializeChainPairForSampling(long identity) {
        Pair pair = existingChainPair(identity);
        if (pair == null || !pair.exists() || pair.colorLayout() != VK_IMAGE_LAYOUT_UNDEFINED) return;
        VkCommandBuffer command = this.context.beginCommands();
        pair.clearWith(command, 0x4100, new float[4], 1.0);
        this.context.endCommands(command, "initialize framebuffer before first sample");
    }

    public void clearChainPair(long identity, VkCommandBuffer command) {
        Pair pair = existingChainPair(identity);
        if (pair != null) {
            pair.clear(command);
        }
    }

    /**
     * A game's own {@code glClear} against the identity's pair, with the colour and depth the game latched.
     * <p>
     * Recorded at the point the clear was consumed, which keeps it between the draws before it and the draws after it
     * - the once-per-frame clear in {@link #clearChainPair} is the pair's initialisation, while this is an explicit
     * mid-frame clear: measured in the fixture's clear-order phase, where a second {@code framebufferClear} after a
     * drawn quad must erase that quad (before this route it was counted and dropped, and the quad survived).
     * The pair's {@code acquired} barrier is honoured: a pair that never recorded its acquire gets it here first,
     * exactly as {@link Pair#clear} does.
     *
     * @return whether any clear commands were recorded (false when the pair does not exist yet)
     */
    public boolean applyGameClearToChainPair(long identity, VkCommandBuffer command, int mask,
            float[] rgba, double depth) {
        checkAlive();
        Pair pair = existingChainPair(identity);
        if (pair == null || !pair.exists() || command == null || command.address() == 0L) {
            return false;
        }
        pair.clearWith(command, mask, rgba, depth);
        return true;
    }

    /**
     * {@return which images the identity's chain pair holds now}, 0 before it exists. Changes whenever the pair is
     * created or replaced, at any size: a framebuffer over the previous images names dead views.
     */
    public long chainPairGeneration(long identity) {
        Pair pair = existingChainPair(identity);
        return pair == null || !pair.exists() ? 0L : pair.generation;
    }

    /** {@return the identity's chain-pair extent as {w, h}}, or null before the pair exists. */
    public long[] chainPairExtent(long identity) {
        Pair pair = existingChainPair(identity);
        return pair == null || !pair.exists() ? null : new long[] {pair.width, pair.height};
    }

    public String chainPairSelfTest(long identity, VkCommandBuffer command) {
        Pair pair = existingChainPair(identity);
        return pair == null ? "no pair for identity" : pair.selfTest(command);
    }

    /**
     * {@return whether this image is a live chain pair's colour image}, across identities.
     * <p>
     * The checks that ask "is this published name sampling a chain image" predate the per-identity pairs and have no
     * identity in hand; their question is a membership test over the pool, not a lookup.
     */
    public boolean isChainPairColorImage(long image) {
        return image != 0L && this.chainPairOwners.containsKey(image);
    }

    /** {@return every chain pair's identity}, for iteration in the dump and summary paths. */
    public java.util.Set<Long> chainPairIdentities() {
        return this.chainPairs.keySet();
    }

    /** {@return the identity whose chain pair holds this colour image}, or {@code Long#MIN_VALUE} when none does. */
    public long chainPairIdentityForImage(long image) {
        return image == 0L ? Long.MIN_VALUE : this.chainPairOwners.getOrDefault(image, Long.MIN_VALUE);
    }

    /** {@return one line per pair}, naming the counters that used to be one unlabelled set. */
    public String pairSummary() {
        StringBuilder summary = new StringBuilder(this.defaultPair.summary());
        for (java.util.Map.Entry<Long, Pair> entry : this.chainPairs.entrySet()) {
            summary.append(' ').append(entry.getValue().summary());
        }
        return summary.toString();
    }

    /**
     * Which of the two pairs an off-target draw identity renders into: {@link #defaultPair} for identity 0 - the
     * default framebuffer, which under the stub carries the game's finished-frame composites - and {@link #chainPairs}
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


    // ---- BH-14 census: how many identities share how few images --------------------------------------------------
    //
    // The routing rule below sends identity 0 to the default pair and *every other* non-main identity to the chain
    // pair, so two non-main identities in one frame draw into the same two images: one identity's clear or draw can
    // reach the other's content between its draw and the copy that reads it. That is a fact about the ownership, not
    // a symptom, and this census turns it into numbers the report can carry: the distinct non-main identities seen,
    // the distinct main identities, and the largest number of identities sharing one pair. When the per-identity
    // image lands, "widest pair" must fall to 1 and the pair count must rise to the identity count - that is the
    // acceptance this counter exists to make readable.
    private static final java.util.Set<Long> CENSUS_NON_MAIN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Long> CENSUS_MAIN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<String, java.util.Set<Long>> CENSUS_PAIR_MEMBERS =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.Set<Long>>();

    /** Records one routed bind: the identity, whether it is main, and which pair it therefore owns. */
    public static void noteIdentityForCensus(long identity, boolean isMain) {
        (isMain ? CENSUS_MAIN : CENSUS_NON_MAIN).add(Long.valueOf(identity));
        // The per-identity chain pair landed: a non-main identity's pair key is its own name, so the census's
        // "widest pair" falls to 1 when every identity owns its image - the acceptance the counter exists to read.
        // The frame-image and default-pair routes keep their shared labels; they are single-owner by design.
        String route = routeLabelForBinding(identity, isMain);
        String pair = "chain-pair".equals(route) ? route + ":" + Long.toHexString(identity) : route;
        java.util.Set<Long> members = CENSUS_PAIR_MEMBERS.get(pair);
        if (members == null) {
            members = java.util.concurrent.ConcurrentHashMap.newKeySet();
            java.util.Set<Long> existing = CENSUS_PAIR_MEMBERS.putIfAbsent(pair, members);
            if (existing != null) {
                members = existing;
            }
        }
        members.add(Long.valueOf(identity));
    }

    /** {@return the BH-14 census as one line}: identities, pairs, and the widest pair's member count. */
    public static String identityPairCensus() {
        int widest = 0;
        String widestPair = "none";
        StringBuilder pairs = new StringBuilder();
        for (java.util.Map.Entry<String, java.util.Set<Long>> entry : CENSUS_PAIR_MEMBERS.entrySet()) {
            if (pairs.length() > 0) {
                pairs.append(',');
            }
            pairs.append(entry.getKey()).append('=').append(entry.getValue().size());
            if (entry.getValue().size() > widest) {
                widest = entry.getValue().size();
                widestPair = entry.getKey();
            }
        }
        return "mainIdentities=" + CENSUS_MAIN.size()
                + " nonMainIdentities=" + CENSUS_NON_MAIN.size()
                + " pairs=" + CENSUS_PAIR_MEMBERS.size()
                + " widestPair=" + widestPair + "(" + widest + " identities share it)"
                + " perPair=[" + pairs + "]";
    }

    /**
     * Which image a bound framebuffer's draws actually render into, as the backend routes them - not which pair an
     * identity <em>would</em> take if it were off-target. The two answers differ for the main framebuffer, and the
     * census used to print only the second: the game's main framebuffer identity is a non-zero
     * {@code System.identityHashCode}, so a bind census line read {@code main=true pair=chain-pair}, which conflates
     * "this is the delivered image" with "these draws go to the outline pair". The routing itself never had that
     * ambiguity - {@code VulkanTerrainGpuBackend} keys it on the main flag, {@code offTarget = !currentRenderTargetIsMain}
     * - so this is the label that was wrong, and this method is the one place the two questions are separated:
     * the main framebuffer renders into the frame image, identity 0 into {@link #defaultPair} and every other
     * non-main identity into {@link #chainPairs}.
     *
     * @param identity the framebuffer identity the game bound
     * @param isMain   whether that framebuffer is the game's own main one ({@code mc.getFramebuffer()})
     * @return "frame-image", "default-pair" or "chain-pair"
     */
    public static String routeLabelForBinding(long identity, boolean isMain) {
        // Identity 0 is the window (GL name 0 is the default framebuffer), so its draws go to the frame image in every
        // phase. Routing it to a pair instead made the window's own picture and the picture a name-0 copy reads two
        // different images: measured on the main menu, the panorama's faces were drawn into the frame image while the
        // copy that feeds the blur still read the pair, and the presented menu came out washed out and flat
        // (presentedDistinctColours 96k -> 21k, top mean (164,167,177) -> (203,201,198)). One image for the window in
        // both directions is vanilla's own semantics and removes the phase dependence the earlier rule had.
        return isMain || identity == 0L ? "frame-image" : pairLabelForIdentity(identity);
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
        /**
         * The layout this pair's colour image is actually in, moved by the same calls that move it.
         * <p>
         * The frame image has carried this since the present path needed it ({@code colorImageLayout} above); the pair
         * did not, and that asymmetry is what the chain pair's validation messages measure. The pair is attached by a
         * pass that declares the constant {@code GENERAL} ({@code COLOR_RENDER_LAYOUT}) while the published wrapper that
         * stands for the same image records {@code SHADER_READ_ONLY_OPTIMAL} whenever it is sampled
         * ({@code VulkanGlTexture.ensureSampledLayout}), and nothing recorded the image's own state between the two.
         * {@code VUID-vkCmdDraw-None-09600} on image 0x370000000037 then appears in both directions (legs
         * r212-world-hud18/hud24/hud26/hud27/hud28/hud29, 2 per leg): "expects GENERAL -- instead SHADER_READ_ONLY" and
         * the reverse. This field is the fact the two sides are compared against.
         */
        private int colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        private int creations;
        /** Unique across pairs and evictions: which images a framebuffer built over this pair's views names. */
        private long generation;
        private int clears;
        private int selfTests;

        private Pair(String label) {
            this.label = label;
        }

        /** {@return the layout this pair's colour image is actually in}. */
        private int colorLayout() {
            return this.colorImageLayout;
        }

        /** Records a layout this pair's colour image has been moved to, by whoever moved it. */
        private void noteColorLayout(int layout) {
            this.colorImageLayout = layout;
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
            retire();
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
                // A pair that was just created holds images nothing has transitioned yet.
                this.colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
                this.creations++;
                this.generation = ++GllessFrameTarget.this.chainPairGenerations;
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

                // The game's own clear colour and depth, not a renderer-chosen one: this pair is the framebuffer's
                // image, and glClear on a framebuffer clears with the current glClearColor - a mod's 256x256 target
                // keeps its own background (measured: the fixture's 0x081020 read back as 0 under the fixed pair
                // clear, every untouched pixel a mismatch).
                float[] clearColor = new float[4];
                org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearColor(clearColor);
                VkClearColorValue color = VkClearColorValue.calloc(stack);
                color.float32(0, clearColor[0]);
                color.float32(1, clearColor[1]);
                color.float32(2, clearColor[2]);
                color.float32(3, clearColor[3]);
                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                colorToTransfer(command);
                vkCmdClearColorImage(command, this.colorImage, colorClearLayout(), color, range);

                VkClearDepthStencilValue depth = VkClearDepthStencilValue.calloc(stack);
                depth.depth((float) org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearDepth()).stencil(0);
                range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                depthToTransfer(command);
                vkCmdClearDepthStencilImage(command, this.depthImage, DEPTH_TRANSFER_LAYOUT, depth, range);

                recordClearToAttachment(command);
                this.clears++;
            }
        }

        /**
         * {@link #clear} with explicit values and a mask, for a game-issued {@code glClear} replayed mid-recording.
         * <p>
         * Same barriers, same acquire-before-clear order; the colour and depth are the ones latched when the game
         * issued the clear, and the mask says which buffers it names (0x4000 colour, 0x0100 depth). Unlike the
         * once-per-frame clear this may run several times in one frame - the game's own ordering, not a policy.
         */
        private void clearWith(VkCommandBuffer command, int mask, float[] rgba, double depthValue) {
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
                if ((mask & 0x4000) != 0) {
                    VkClearColorValue color = VkClearColorValue.calloc(stack);
                    color.float32(0, rgba[0]);
                    color.float32(1, rgba[1]);
                    color.float32(2, rgba[2]);
                    color.float32(3, rgba[3]);
                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                    colorToTransfer(command);
                vkCmdClearColorImage(command, this.colorImage, colorClearLayout(), color, range);
                }
                if ((mask & 0x0100) != 0) {
                    VkClearDepthStencilValue depth = VkClearDepthStencilValue.calloc(stack);
                    depth.depth((float) depthValue).stencil(0);
                    range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                    depthToTransfer(command);
                    vkCmdClearDepthStencilImage(command, this.depthImage, DEPTH_TRANSFER_LAYOUT, depth, range);
                }
                recordClearToAttachment(command);
                this.clears++;
            }
        }

        /**
         * Clears the depth of the pair and keeps its colour, which is the clear the game gives the default framebuffer
         * ({@code GlStateManager.clear(256)}; see {@link GllessFrameTarget#clearOffTarget}).
         * <p>
         * The colour is cleared once, on the pair's first use, so the first frame does not sample undefined content;
         * after that it accumulates, exactly as the real default framebuffer does. The barrier the colour needs is the
         * hand-back of the previous frame's attachment writes, not a transfer-write barrier: nothing transferred into it
         * this frame.
         */
        private void clearDepthOnly(VkCommandBuffer command) {
            checkAlive();
            if (command == null || command.address() == 0L) {
                throw new IllegalArgumentException(this.label + " pair clear needs a command buffer");
            }
            if (!exists()) {
                throw new IllegalStateException("ensure must run before the " + this.label + " pair is cleared");
            }
            try (MemoryStack stack = stackPush()) {
                boolean first = !this.acquired;
                if (first) {
                    recordAcquire(command);
                    this.acquired = true;
                }
                VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack).levelCount(1).layerCount(1);
                if (first) {
                    // First-use initialisation keeps the same rule as {@link #clear}: the game's own clear colour,
                    // because this image is a framebuffer's content and GL's framebuffer starts from the game's
                    // state, not a renderer-chosen one.
                    float[] clearColor = new float[4];
                    org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearColor(clearColor);
                    VkClearColorValue color = VkClearColorValue.calloc(stack);
                    color.float32(0, clearColor[0]);
                    color.float32(1, clearColor[1]);
                    color.float32(2, clearColor[2]);
                    color.float32(3, clearColor[3]);
                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                    colorToTransfer(command);
                vkCmdClearColorImage(command, this.colorImage, colorClearLayout(), color, range);
                } else {
                    recordColourHandBack(command);
                }

                // The game's glClearDepth, because this clear is the game's own "clear just the depth" - the real
                // default framebuffer answers it with the recorded value, not with 1.0.
                VkClearDepthStencilValue depth = VkClearDepthStencilValue.calloc(stack);
                depth.depth((float) org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearDepth()).stencil(0);
                range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                depthToTransfer(command);
                vkCmdClearDepthStencilImage(command, this.depthImage, DEPTH_TRANSFER_LAYOUT, depth, range);

                recordClearToAttachment(command);
                this.clears++;
            }
        }

        /**
         * Hands the colour image's previous attachment writes back to the attachment reads and writes that follow.
         * <p>
         * The pair's colour is written by a render pass in one frame and loaded by a render pass in the next, and this
         * pair is not covered by the frame images' own acquire/release barriers, so without this the next pass's
         * attachment load has an execution dependency but no availability or visibility for the writes it is loading.
         * The layouts do not change: the pair is {@code GENERAL} before and after.
         */
        private void recordColourHandBack(VkCommandBuffer command) {
            int queueFamily = context.queueFamilyIndex();
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default()
                        .image(this.colorImage)
                        .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier);
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
        /** Where the pair's depth image is: UNDEFINED, the clear's transfer layout, or the attachment layout. */
        private int depthLayout = VK_IMAGE_LAYOUT_UNDEFINED;

        /** Whether the pair's colour sits in the clear's transfer layout (only with COLOR_CLEAR_TRANSFER). */
        private boolean colorInTransfer;

        private void colorToTransfer(VkCommandBuffer command) {
            if (COLOR_CLEAR_TRANSFER && !this.colorInTransfer) {
                recordColorToTransfer(command, this.colorImage);
                this.colorInTransfer = true;
            }
        }

        private int colorClearLayout() {
            return this.colorInTransfer ? DEPTH_TRANSFER_LAYOUT : VK_IMAGE_LAYOUT_GENERAL;
        }

        /** Moves the pair's depth to the transfer layout a clear needs, unless it is already there. */
        private void depthToTransfer(VkCommandBuffer command) {
            if (this.depthLayout == DEPTH_TRANSFER_LAYOUT) {
                return;
            }
            recordDepthToTransfer(command, this.depthImage, this.depthLayout);
            this.depthLayout = DEPTH_TRANSFER_LAYOUT;
        }

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
                        .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(DEPTH_TRANSFER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(0).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, null, barriers);
                // The barrier above is the move, so this is where the pair's own record of it belongs.
                this.colorImageLayout = VK_IMAGE_LAYOUT_GENERAL;
                this.depthLayout = DEPTH_TRANSFER_LAYOUT;
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
                        .oldLayout(this.colorInTransfer ? DEPTH_TRANSFER_LAYOUT : VK_IMAGE_LAYOUT_GENERAL)
                        .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(colorAccess);
                this.colorInTransfer = false;
                colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

                VkImageMemoryBarrier depthBarrier = barriers.get(1);
                depthBarrier.sType$Default()
                        .image(this.depthImage)
                        // From wherever the depth is - the transfer layout after a depth clear, the attachment layout
                        // when this clear named colour only - to the attachment layout the pass declares.
                        .oldLayout(this.depthLayout).newLayout(DEPTH_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(depthAccess);
                depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        0, null, null, barriers);
                this.depthLayout = DEPTH_RENDER_LAYOUT;
            }
        }

        /**
         * Like {@link #release()}, but hands the images, views and memory to {@link VulkanRetireQueue} instead of
         * destroying them: eviction and a resize run mid-session, while a command buffer that recorded against this
         * pair is still open or in flight. Destroying in place invalidated that command buffer and let the GPU read
         * freed memory - E2E-E leg e2ee-amd9, a mod's downsample chain evicting pairs mid-frame: "VkImage ... was
         * destroyed" on every later command, a GPUVM permission fault, then VK_ERROR_DEVICE_LOST.
         */
        private void retire() {
            VkDevice device = context.device();
            final long view = this.colorView;
            final long image = this.colorImage;
            final long memory = this.colorMemory;
            final long depthView = this.depthView;
            final long depthImage = this.depthImage;
            final long depthMemory = this.depthMemory;
            this.colorView = VK_NULL_HANDLE;
            this.colorImage = VK_NULL_HANDLE;
            this.colorMemory = VK_NULL_HANDLE;
            this.depthView = VK_NULL_HANDLE;
            this.depthImage = VK_NULL_HANDLE;
            this.depthMemory = VK_NULL_HANDLE;
            if (view != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(() -> vkDestroyImageView(device, view, null));
            }
            if (image != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(() -> vkDestroyImage(device, image, null));
            }
            if (memory != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(() -> VulkanContext.freeMemory(device, memory));
            }
            if (depthView != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(() -> vkDestroyImageView(device, depthView, null));
            }
            if (depthImage != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(() -> vkDestroyImage(device, depthImage, null));
            }
            if (depthMemory != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(() -> VulkanContext.freeMemory(device, depthMemory));
            }
            // The handles are gone from this pair; release() now only forgets the size and layouts.
            release();
        }

        /**
         * Releases the pair and forgets its size, so the next {@link #ensure} builds it afresh. Safe to call with
         * nothing created and safe to call twice. Frees in place, so it is only for images no command buffer can
         * still name: {@link #delete()} after the GPU settled, and a failed {@link #ensure} that never recorded.
         * A pair the session may still be drawing with goes through {@link #retire()}.
         */
        private void release() {
            VkDevice device = context.device();
            this.colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            this.depthLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            if (this.colorView != VK_NULL_HANDLE) {
                vkDestroyImageView(device, this.colorView, null);
                this.colorView = VK_NULL_HANDLE;
            }
            if (this.colorImage != VK_NULL_HANDLE) {
                vkDestroyImage(device, this.colorImage, null);
                this.colorImage = VK_NULL_HANDLE;
            }
            if (this.colorMemory != VK_NULL_HANDLE) {
                VulkanContext.freeMemory(device, this.colorMemory);
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
                VulkanContext.freeMemory(device, this.depthMemory);
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
                + " gameClears=[latched=" + this.latchedGameClears + ",live=" + this.liveGameClears
                + ",noClear=" + this.framesWithoutClear
                + ",framesNoClear=" + this.framesWithNoClear
                + " presentLag=[max=" + this.presentLagMax + ",one=" + this.presentLagOne
                + ",more=" + this.presentLagMore + "]"
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
        for (Pair pair : this.chainPairs.values()) {
            pair.release();
        }
        this.chainPairs.clear();
        this.chainPairOwners.clear();
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
            VulkanContext.freeMemory(device, this.colorMemory);
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
            VulkanContext.freeMemory(device, this.depthMemory);
            this.depthMemory = VK_NULL_HANDLE;
        }
    }
}
