package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
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
import static org.lwjgl.vulkan.VK10.vkCmdBlitImage;
import static org.lwjgl.vulkan.VK10.vkCmdClearAttachments;
import static org.lwjgl.vulkan.VK10.vkCmdClearColorImage;
import static org.lwjgl.vulkan.VK10.vkCmdClearDepthStencilImage;
import static org.lwjgl.vulkan.VK10.vkCmdCopyImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;

import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.hal.FrameTargetPair;
import org.soutalab.celtium.vulkan.hal.PresentLagRecord;
import org.soutalab.celtium.vulkan.hal.SharedDepthStore;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

/**
 * The frame image when no OpenGL context exists: the colour and depth images are created, not imported, and there
 * is no GL side to hand them to.
 * <p>
 * The synchronization contract is the one {@link FrameImageTarget} declares, and for this target it is empty:
 * there is no second producer. The wait/done binary semaphore pair an earlier design cycled per frame -
 * {@link #beginFrame()} signalling {@link #waitSemaphore()} with a signal-only submission, {@link #endFrame()}
 * consuming {@link #doneSemaphore()} with a wait-only one - bought three queue packets per stage for ordering
 * the single queue already guarantees, so the new architecture removed the pair outright rather than leaving
 * it behind a flag: {@link #needsFrameSemaphores()} is false, both semaphore accessors return
 * {@code VK_NULL_HANDLE}, and no dummy submission is ever recorded for this target.
 * <p>
 * The images never leave our queue family: the acquire/release barriers still perform the layout transitions a
 * frame needs (GENERAL to the render layout and back) but carry no external-family transfer.
 * <p>
 * Two independent pairs of images live beside the frame's own, one per stream of draws that is not addressed to
 * the main framebuffer - the split, and the measurement that forced it, are in the section comment on {@link FrameTargetPair}.
 * Each uses the frame's formats and the same {@code VK_IMAGE_LAYOUT_GENERAL} attachments, so the frame-compatible
 * render pass built by {@code VulkanTerrainPipeline.createFrameRenderPass} accepts it without a second pass;
 * {@link #clearOffTarget} and {@link #clearChainPair} record the clears the game's own framebuffer would have
 * received, and {@link #offTargetSelfTest} and {@link #chainPairSelfTest} record a clear-and-barrier sequence the
 * validation layer can check end to end.
 */
public final class GllessFrameTarget implements FrameImageTarget {

    static final int COLOR_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;
    /**
     * Depth rests in the attachment layout, not GENERAL: on Intel gfx9 a writable GENERAL depth image has no HiZ
     * (no hierarchical depth culling, no fast depth clears), and nothing outside this renderer reads it - the GL-less
     * target is never handed to OpenGL. Clears and readbacks move it to their transfer layouts and back.
     */
    public static final int DEPTH_RENDER_LAYOUT = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
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
    // No semaphore fields: the wait/done pair and the per-image present-done array that fed its consume were
    // deleted with the pair (see the class documentation). The present path's own per-image semaphores live in
    // VulkanSwapchain, not here.

    /**
     * The default-framebuffer pair: created by {@link #ensureOffTarget(int, int)} on first use, replaced when the
     * requested size changes, and released by {@link #delete()} like every other resource here. It is never handed
     * to GL and never leaves this queue family, so its only layout bookkeeping is the one-time UNDEFINED to GENERAL
     * move {@link #clearOffTarget} records before the first clear.
     * <p>
     * Its one writer is the game's draw stream with no framebuffer bound (identity 0).
     */
    /** The pair host: the services {@link FrameTargetPair} cannot own, bound to this target. Its
     * methods dereference this target's fields at call time, because pairs are constructed before
     * the constructor body assigns them. */
    private final FrameTargetPair.Host pairHost = new FrameTargetPair.Host() {
        @Override
        public VulkanContext context() {
            return GllessFrameTarget.this.context;
        }

        @Override
        public long[] allocateImage(boolean color, int width, int height, TargetFormatClass colorClass) {
            return GllessFrameTarget.this.allocateImage(color, width, height, colorClass);
        }

        @Override
        public void checkAlive() {
            GllessFrameTarget.this.checkAlive();
        }

        @Override
        public SharedDepthStore sharedDepthStore() {
            return GllessFrameTarget.this.sharedDepthStore;
        }

        @Override
        public long nextPairGeneration() {
            return GllessFrameTarget.this.nextChainPairGeneration();
        }
    };

    private final FrameTargetPair defaultPair = new FrameTargetPair("default", this.pairHost);
    /**
     * The chain pair per framebuffer identity. Two non-main identities used to share one FrameTargetPair: the second one's
     * clear or draw then reached the first one's content between its draw and the read that sampled it - measured
     * as the world going black when the loading overlay's FBO and the frame's own passes alternated (legs
     * bisect4/5: acceptedForOtherTargets=11942 with pairClearsRefusedOtherIdentity=3), and as the fixture's second
     * framebuffer never holding its quad (leg fboalias-amd: every pixel check failed). Each identity now owns its
     * own pair, which is what the BH-14 census's "widest pair must fall to 1" acceptance meant. A live framebuffer
     * keeps its pair until its owner deletes it; an arbitrary size cap would discard images still needed by later passes.
     */
    // Primitive-keyed: existingChainPair runs per drawn group.
    private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<FrameTargetPair> chainPairs =
            new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<FrameTargetPair>();
    /** Reverse lookup for the sampling hot path; only live colour images have entries. */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap chainPairOwners =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    /**
     * Chain-pair image-set changes so far; the source of {@link #chainPairGeneration}. Created <em>and</em> retired
     * count, because the number a framebuffer is stamped with has to change for either - see
     * {@link #celtium$nextPairGeneration}.
     */
    private long chainPairGenerations;

    /**
     * {@return the next value of the chain-pair generation counter}.
     * <p>
     * One function for the two events that change which images a pair holds: its creation and its retirement. The
     * consumer's rule is only "the number moved" - {@link FramebufferCache#chainPair} keeps its stamp per identity
     * and retires the cached handles when it disagrees - so a retirement that left the number alone was invisible to
     * it, and only the callers' ordering (ensure before asking the cache) kept a framebuffer built over retired views
     * from being handed out (item 372, hole 3). Advancing on retirement makes the guarantee the pair's own rather than
     * its callers'.
     */
    static long celtium$nextPairGeneration(long counter) {
        return counter + 1;
    }

    /** Takes the next chain-pair generation, for a pair whose images were just created or just retired. */
    private long nextChainPairGeneration() {
        return this.chainPairGenerations = celtium$nextPairGeneration(this.chainPairGenerations);
    }

    private boolean frameOpen;
    private boolean deleted;
    private boolean firstAcquire = true;
    /** The first acquire clears both images whatever the game asked: their content starts undefined. */
    private boolean neverCleared = true;

    /**
     * The clear the game issued while its own main framebuffer was bound, latched for the delivered
     * image. The latch's state and rules live in {@link org.soutalab.celtium.vulkan.hal.GameClearLatch};
     * this target only holds it and applies what it holds.
     */
    private final org.soutalab.celtium.vulkan.hal.GameClearLatch gameClearLatch =
            new org.soutalab.celtium.vulkan.hal.GameClearLatch();
    /**
     * GLSM stage 2 (S2-2): the shared frame state this target's frame latch moves into on the
     * stage-2 route, attached by the backend at creation. The frame identity is resolved
     * through the supplier at call time - the registry's main identity is only established
     * after targets exist, so capturing its value here would freeze a stale key. Both stay
     * null on the off route, where the fields above remain the latch's only owner.
     */
    private GlsmFrameState glsmFrameState;
    private java.util.function.LongSupplier glsmFrameIdentity;

    /**
     * Attaches the shared GLSM frame state (stage 2). The backend calls this when it creates
     * the target, so the frame latch has exactly one home per route: the ClearState on the on
     * route, this class's fields on the off route.
     */
    public void celtium$attachGlsmFrameState(GlsmFrameState state,
                                             java.util.function.LongSupplier frameIdentity) {
        this.glsmFrameState = state;
        this.glsmFrameIdentity = frameIdentity;
    }

    /** How many clears came from the latch and how many still had to be read live, for this target's summary. */
    private int latchedGameClears;
    private int liveGameClears;
    /** Frames whose acquire found no clear request, which leave the previous frame's picture in place. */
    private int framesWithoutClear;

    /** {@return how many clears this image has been given}, read at frame boundaries to see a frame that had none. */
    public int clearsApplied() {
        return this.latchedGameClears + this.liveGameClears;
    }

    /** Colour clears only: the ones that erase what was drawn before them. See {@link #colourClearsApplied()}. */
    private int colourClears;

    /** Frames the backend saw start without any clear in between; counted at the frame boundary and reported here. */
    private long framesWithNoClear;

    /** Sets the count the summary reports. */
    public void setFramesWithNoClear(long frames) {
        this.framesWithNoClear = frames;
    }

    /** How far behind the game the presented frame was; the record lives in {@link PresentLagRecord}. */
    private final PresentLagRecord presentLag = new PresentLagRecord();

    /** Sets the present-lag counters the summary reports. */
    public void setPresentLag(long max, long one, long more) {
        this.presentLag.set(max, one, more);
    }
    private int colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    private int depthImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    private int abandonedFrames;
    private String lastAbandonReason = "none";

    private GllessFrameTarget(VulkanContext context, int width, int height) {
        this.context = context;
        this.width = width;
        this.height = height;
        this.sharedDepthStore = new SharedDepthStore(
                () -> this.context.device(),
                (w, h) -> allocateImage(false, w, h, TargetFormatClass.RGBA8_UNORM),
                this::retirePairsBorrowing);
    }

    public static GllessFrameTarget create(VulkanContext context, int width, int height) {
        if (context == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("context, width and height must be set");
        }
        GllessFrameTarget target = new GllessFrameTarget(context, width, height);
        try {
            // The frame's own colour is always RGBA8_UNORM (QUEUE 404 rule R1): it is the image the finished
            // frame is composed into and presented from, whatever a mod's own framebuffer asked for.
            long[] color = target.allocateImage(true, width, height, TargetFormatClass.RGBA8_UNORM);
            target.colorImage = color[0];
            target.colorMemory = color[1];
            target.colorView = color[2];
            long[] depth = target.allocateImage(false, width, height, TargetFormatClass.RGBA8_UNORM);
            target.depthImage = depth[0];
            target.depthMemory = depth[1];
            target.depthView = depth[2];
            return target;
        } catch (RuntimeException | LinkageError error) {
            target.delete();
            throw error;
        }
    }

    /**
     * Creates one image of a pair at the given size - the image, its device-local memory and a 2D view - and
     * returns them as {@code {image, memory, view}}. The reason this is one method rather than one per pair: the
     * off-target pair is the same pair of usages as the frame's own, only at the size the game's framebuffer
     * asks for. The colour format is {@code colorClass}'s - the frame's own format for the frame and the
     * default pair, the resolved class for a chain pair (QUEUE 404) - and is ignored for depth, whose format
     * {@link VulkanDepthFormat} chooses.
     * <p>
     * A failure after the image or the memory exists frees what was created here before it propagates, so the
     * caller only ever adopts complete handles and {@link #delete()} never has to know about a half-created image.
     */
    private long[] allocateImage(boolean color, int imageWidth, int imageHeight, TargetFormatClass colorClass) {
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
            // The colour format is the caller's class (QUEUE 404, S3): the frame and the default pair pass
            // RGBA8_UNORM, a chain pair passes the class its identity resolved. The class was device-checked by
            // the resolver (VulkanColorFormat.usable) before it reached a pair, so creation does not re-decide it.
            int format = color ? colorClass.vkFormat()
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
            if (VulkanContext.allocateMemory(device, allocate, handle, "frameTarget") != VK_SUCCESS) {
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

    @Override
    public void beginFrame() {
        checkAlive();
        if (this.frameOpen) {
            this.frameOpen = false;
            this.abandonedFrames++;
            this.lastAbandonReason = "beginFrame found the previous frame open";
        }
        // No signal accompanies the open: the wait semaphore the pair's beginFrame used to signal does not
        // exist, so an abandoned frame leaves nothing outstanding for the next frame to trip over - the
        // VUID-00067 double-signal chain the pair's consume machinery existed to prevent is impossible by
        // construction now, not merely handled.
        this.frameOpen = true;
    }

    /**
     * Whether the wait/done semaphore pair runs at all here: never.
     * <p>
     * The pair carried content between two producers on different queues; this target's frames are produced
     * and consumed on the one queue, whose submission order is the whole ordering contract, so the pair was
     * ceremony - three queue packets per stage (beginFrame's signal-only submission, the work submission's own
     * semaphore pair, endFrame's wait-only submission) for ordering the queue already guarantees. Measured on
     * a held leg, the frame pass's own submit work was the renderer's largest steady-state line item
     * ({@code terrain+flush} p50 ~7.5 ms of a ~10 ms world pass, with per-stage submit cycles of three). The
     * new architecture deleted the pair and its {@code celtium.vulkan.frameSemaphores} opt-in together, rather
     * than leaving a dormant synchronization path in the frame loop.
     */
    @Override
    public boolean needsFrameSemaphores() {
        return false;
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
        // Nothing was ever signalled for this frame, so there is nothing to consume: closing is bookkeeping.
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
        // The pair is deleted: there is nothing to wait on. Callers pass the null handle through, and the
        // submit paths that compact nulls drop it from the wait list entirely.
        return VK_NULL_HANDLE;
    }

    @Override
    public long doneSemaphore() {
        checkAlive();
        return VK_NULL_HANDLE;
    }

    /**
     * {@return the present-wait semaphore for a swapchain image}: always {@code VK_NULL_HANDLE} here.
     * <p>
     * This target used to grow a per-image array of done semaphores for the present path to signal, but the
     * only consumer of those signals was the pair's own wait-only consume submission - with the pair deleted
     * nothing waits on them, and a signalled-but-never-waited binary semaphore is a VUID-00067 the second time
     * it is signalled, so they are not produced either. The present path's own per-image semaphores (the ones
     * {@code vkQueuePresentKHR} actually waits on) are {@code VulkanSwapchain}'s, and always were.
     */
    @Override
    public long presentDoneSemaphore(int imageIndex) {
        checkAlive();
        return VK_NULL_HANDLE;
    }

    @Override
    public void recordAcquireBarriers(VkCommandBuffer commandBuffer) {
        recordAcquireBarriers(commandBuffer, true);
    }

    /**
     * The acquire for a window whose pass may not attach the frame image at all.
     * <p>
     * An off-target window (a mod's own framebuffer, the entity outline, GregTech's bloom chain) records its pass
     * against one of the owned pairs, so the frame image is neither attached nor written by it. The two layout
     * barriers this method used to record unconditionally - GENERAL to the attachment layout and back - were then a
     * matched pair around a pass that never touched the image: 26 {@code vkCmdPipelineBarrier} calls a frame on the
     * measured scene, where 13 of 23 windows are GregTech's post chain. Skipping both leaves the image in GENERAL,
     * which is exactly where the release leaves it, so the next window that does attach it finds the layout it
     * expects.
     * <p>
     * The clear path is the one thing that still needs the attachment layout, so it is asked first: a window with a
     * game clear pending (or an image that has never been cleared) keeps the full acquire. The caller passes the same
     * answer to {@link #recordReleaseBarriers(VkCommandBuffer, boolean)}, so the two are always a pair.
     *
     * @param frameImageAttached whether this window's pass attaches the frame image (or a clear needs its layout)
     */
    public void recordAcquireBarriers(VkCommandBuffer commandBuffer, boolean frameImageAttached) {
        if (frameImageAttached) {
            recordLayoutBarriers(commandBuffer, false);
            this.firstAcquire = false;
            this.colorImageLayout = COLOR_RENDER_LAYOUT;
            this.depthImageLayout = DEPTH_RENDER_LAYOUT;
        }
        applyGameClears(commandBuffer);
    }

    /**
     * {@return whether this window's acquire has to move the frame image into its attachment layout}.
     * <p>
     * True when the image has never been cleared (the first attachment loads it) or when a game clear is waiting to
     * be applied to it - {@link #applyGameClears} records its clear in the attachment layout, so a window that skips
     * the acquire must not be one that carries a clear. The answer is read before the window records, because the
     * release has to make the same decision.
     */
    public boolean frameImageAcquireNeeded() {
        return this.neverCleared || org.soutalab.celtium.vulkan.gl.GlsmFrontend.hasPendingClears();
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
        // GLSM stage 2 (S2-2): the frame latch's ClearState record, and the applied count its
        // consumption moved, captured and restored as a pair on the frame-state route - the
        // ClearState equivalent of the gameClearLatched* fields above.
        private GlsmFrameState.PendingClear glsmLatched;
        private long glsmAppliedTotal;
        private boolean glsmSnapshot;
        private boolean defaultAcquired;
        // Parallel arrays, not two boxed maps: a snapshot is taken for every window flush (~10 a frame) and covers
        // every chain pair, while a rollback that reads it back is rare.
        private int chainCount;
        private long[] chainKeys;
        private boolean[] chainAcquired;
        private int[] chainColorLayouts;
    }

    /**
     * Fills {@code snapshot} (or a new one when null) and returns it. The window flush takes one snapshot per window
     * (~10 a frame); reusing the object keeps its chain arrays too - they are grown only when more pairs appear.
     */
    public RecordingSnapshot snapshotRecording(RecordingSnapshot snapshot) {
        if (snapshot == null) {
            snapshot = new RecordingSnapshot();
        }
        snapshot.firstAcquire = this.firstAcquire;
        snapshot.neverCleared = this.neverCleared;
        snapshot.colorImageLayout = this.colorImageLayout;
        snapshot.depthImageLayout = this.depthImageLayout;
        snapshot.gameClearLatched = this.gameClearLatch.latched();
        snapshot.gameClearLatchedMask = this.gameClearLatch.mask();
        snapshot.latchedRed = this.gameClearLatch.red();
        snapshot.latchedGreen = this.gameClearLatch.green();
        snapshot.latchedBlue = this.gameClearLatch.blue();
        snapshot.latchedAlpha = this.gameClearLatch.alpha();
        snapshot.latchedDepth = this.gameClearLatch.depth();
        if (GlsmFrameState.ENABLED && this.glsmFrameState != null) {
            snapshot.glsmLatched = this.glsmFrameState.clearState()
                    .peek(this.glsmFrameIdentity.getAsLong());
            snapshot.glsmAppliedTotal = this.glsmFrameState.clearState().appliedTotal();
            snapshot.glsmSnapshot = true;
        } else {
            snapshot.glsmSnapshot = false;
            snapshot.glsmLatched = null;
        }
        snapshot.defaultAcquired = this.defaultPair.acquired;
        int size = this.chainPairs.size();
        if (snapshot.chainKeys == null || snapshot.chainKeys.length < size) {
            snapshot.chainKeys = new long[size];
            snapshot.chainAcquired = new boolean[size];
            snapshot.chainColorLayouts = new int[size];
        }
        int n = 0;
        it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<FrameTargetPair>> it =
                this.chainPairs.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<FrameTargetPair> entry = it.next();
            FrameTargetPair pair = entry.getValue();
            snapshot.chainKeys[n] = entry.getLongKey();
            snapshot.chainAcquired[n] = pair.acquired;
            snapshot.chainColorLayouts[n] = pair.colorImageLayout;
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
        this.gameClearLatch.restore(snapshot.gameClearLatched, snapshot.gameClearLatchedMask,
                snapshot.latchedRed, snapshot.latchedGreen, snapshot.latchedBlue, snapshot.latchedAlpha,
                snapshot.latchedDepth);
        if (snapshot.glsmSnapshot && this.glsmFrameState != null) {
            this.glsmFrameState.clearState().restore(this.glsmFrameIdentity.getAsLong(),
                    snapshot.glsmLatched);
            this.glsmFrameState.clearState().restoreAppliedTotal(snapshot.glsmAppliedTotal);
        }
        this.defaultPair.acquired = snapshot.defaultAcquired;
        for (int i = 0; i < snapshot.chainCount; i++) {
            FrameTargetPair pair = this.chainPairs.get(snapshot.chainKeys[i]);
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
        this.gameClearLatch.note(mask, red, green, blue, alpha, depth);
    }

    /** {@return whether a clear addressed to this image is waiting to be recorded}, consuming nothing. */
    public boolean gameClearLatched() {
        return this.gameClearLatch.latched();
    }

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

    /** Scratch for the clear colour a replay reads; refilled before every use. */
    private final float[] clearColourScratch = new float[4];

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
        // GLSM stage 2 (S2-2): on the frame-state route the latch is the ClearState's pending
        // record for the frame identity, consumed here at the point the fields were read. The
        // fields are never written on this route, so the two owners can never both hold one.
        GlsmFrameState.PendingClear stateLatched = null;
        if (GlsmFrameState.ENABLED && this.glsmFrameState != null) {
            stateLatched = this.glsmFrameState.clearState()
                    .consume(this.glsmFrameIdentity.getAsLong());
        }
        boolean latched = stateLatched != null || this.gameClearLatch.latched();
        boolean initializeOnly = false;
        int clears;
        float latchedRed = 0.0F;
        float latchedGreen = 0.0F;
        float latchedBlue = 0.0F;
        float latchedAlpha = 0.0F;
        double latchedDepth = 1.0D;
        if (latched) {
            if (stateLatched != null) {
                clears = stateLatched.mask;
                latchedRed = stateLatched.red;
                latchedGreen = stateLatched.green;
                latchedBlue = stateLatched.blue;
                latchedAlpha = stateLatched.alpha;
                latchedDepth = stateLatched.depth;
            } else {
                clears = this.gameClearLatch.mask();
                latchedRed = this.gameClearLatch.red();
                latchedGreen = this.gameClearLatch.green();
                latchedBlue = this.gameClearLatch.blue();
                latchedAlpha = this.gameClearLatch.alpha();
                latchedDepth = this.gameClearLatch.depth();
                this.gameClearLatch.consume();
            }
        } else {
            int owner = org.soutalab.celtium.vulkan.gl.GlsmFrontend.pendingClearDrawFbo();
            boolean ownerIsMain = org.soutalab.celtium.vulkan.gl.GlsmFrontend.pendingClearIsMain();
            if (owner >= 0 && !ownerIsMain) {
                // The clear names one of the game's own framebuffers. Every non-main identity shares the chain pair,
                // so applying it here wipes whatever another identity drew into that image - measured as the
                // panorama's faces being cleared to white between their draw and the copy that reads them. The clear
                // belongs to an image this path does not own, so it is dropped and counted rather than applied.
                clears = org.soutalab.celtium.vulkan.gl.GlsmFrontend.consumePendingClears();
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
                clears = org.soutalab.celtium.vulkan.gl.GlsmFrontend.consumePendingClears();
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
            // Both transitions are recorded before either clear: they share the one dependency below, and the
            // colour clear sitting between them used to split them into two barrier calls for no ordering gain.
            recordGameClearsToTransfer(command, color, depth);
            if (color) {
                float[] rgba = this.clearColourScratch;
                if (latched || initializeOnly) {
                    rgba[0] = latchedRed;
                    rgba[1] = latchedGreen;
                    rgba[2] = latchedBlue;
                    rgba[3] = latchedAlpha;
                } else {
                    // The frame image is the main framebuffer: its clear uses the values that clear was issued with.
                    org.soutalab.celtium.vulkan.gl.GlsmFrontend.mainClearValues(rgba);
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
                org.lwjgl.vulkan.VK10.vkCmdClearColorImage(command, this.colorImage,
                        DEPTH_TRANSFER_LAYOUT, value, range);
            }
            if (depth) {
                double mainDepth = org.soutalab.celtium.vulkan.gl.GlsmFrontend.mainClearValues(this.clearColourScratch);
                double depthValue = latched || initializeOnly ? latchedDepth
                        : Double.isNaN(mainDepth) ? org.soutalab.celtium.vulkan.gl.GlsmFrontend.clearDepth() : mainDepth;
                org.lwjgl.vulkan.VkClearDepthStencilValue value =
                        org.lwjgl.vulkan.VkClearDepthStencilValue.calloc(stack);
                value.depth((float) depthValue).stencil(0);
                range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                org.lwjgl.vulkan.VK10.vkCmdClearDepthStencilImage(command, this.depthImage, DEPTH_TRANSFER_LAYOUT,
                        value, range);
            }
            recordClearToAttachment(command, color, depth);
            if (color) {
                this.colourClears++;
            }
        }
    }

    /**
     * How many of this image's clears named the colour buffer.
     * <p>
     * The frame's colour clear is the one that erases whatever was drawn before it, and the sky plane is the frame's
     * first draw: a colour clear that lands after it leaves the clear colour in the sky band, which is the fog colour -
     * the same value a fully fogged plane would have. The count beside the draw is what tells those apart without a
     * second guess, and it is cumulative like {@link #clearsApplied()} so a caller compares two readings.
     */
    public int colourClearsApplied() {
        return this.colourClears;
    }

    /**
     * Moves the images {@link #applyGameClears} is about to clear into the transfer layout those clears need, in
     * one {@code vkCmdPipelineBarrier} when both are cleared.
     * <p>
     * The colour and depth transitions are the same dependency - {@code ALL_COMMANDS} to {@code TRANSFER} - on two
     * images of one frame, the same shape {@code FrameTargetPair.toTransfer} already fuses for the pair clear. They used to be
     * two calls, one per image, with the colour clear recorded between them; both clears that follow are
     * {@code TRANSFER}-stage writes, so recording the depth transition before the colour clear changes no ordering
     * the GPU has to honour, and it waits for less, because the barrier's source stage is {@code ALL_COMMANDS} and
     * the colour clear has not been recorded yet. Everything that can have touched either image before - earlier
     * windows' attachment and depth tests and writes, an earlier clear - is covered by {@code ALL_COMMANDS}.
     * <p>
     * The colour half moves a GENERAL image to the transfer layout a clear uses, after whatever touched it before.
     * Colour clears run in TRANSFER_DST_OPTIMAL instead of GENERAL, which lets Intel's driver use a CCS fast clear
     * where it otherwise writes the whole surface: under INTEL_MEASURE on the UHD 630 the three "slow color clear"s
     * a frame (263 us) became fast clears (17 us) plus partial resolves (62 us), and the draws that follow on the
     * compressed surface cost less as well. The depth half starts from {@link #DEPTH_RENDER_LAYOUT}, the layout
     * {@link #applyGameClears} always clears from, so its source mask is the non-UNDEFINED one the old per-image
     * helper computed for that caller.
     */
    private void recordGameClearsToTransfer(VkCommandBuffer command, boolean color, boolean depth) {
        if (!color && !depth) {
            // A stencil-only clear names neither image (the frame has no stencil), so nothing moves.
            return;
        }
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barriers =
                    VkImageMemoryBarrier.calloc((color ? 1 : 0) + (depth ? 1 : 0), stack);
            int index = 0;
            if (color) {
                VkImageMemoryBarrier barrier = barriers.get(index++);
                barrier.sType$Default().image(this.colorImage)
                        .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(DEPTH_TRANSFER_LAYOUT)
                        .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                        .srcAccessMask(colorToTransferSrcAccessMask())
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            }
            if (depth) {
                VkImageMemoryBarrier barrier = barriers.get(index);
                barrier.sType$Default().image(this.depthImage)
                        .oldLayout(DEPTH_RENDER_LAYOUT).newLayout(DEPTH_TRANSFER_LAYOUT)
                        .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                        .srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                                | VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            }
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barriers);
        }
    }

    /** {@link #celtium$transferImages} bit: the pair's colour image needs the transition. */
    static final int TRANSFER_COLOR = 1;
    /** {@link #celtium$transferImages} bit: the pair's depth image needs the transition. */
    static final int TRANSFER_DEPTH = 2;

    /**
     * {@return which of a pair's images a clear still has to move into the transfer layout}, as
     * {@link #TRANSFER_COLOR} and {@link #TRANSFER_DEPTH} bits.
     * <p>
     * The pure half of {@code FrameTargetPair.toTransfer}, package-private and static so the rule is pinned without a device. The
     * case that matters is the one that must record <em>no</em> barrier at all - an image already in the transfer
     * layout - because a barrier recorded for an image that is already where the clear wants it is a driver call
     * that buys nothing.
     */
    static int celtium$transferImages(boolean wantColor, boolean wantDepth,
            boolean colorInTransfer, boolean depthInTransfer) {
        int images = 0;
        if (wantColor && !colorInTransfer) {
            images |= TRANSFER_COLOR;
        }
        if (wantDepth && !depthInTransfer) {
            images |= TRANSFER_DEPTH;
        }
        return images;
    }

    /**
     * {@return the source access mask for {@link #recordGameClearsToTransfer}}, covering prior colour attachment,
     * transfer, and shader accesses so RDNA3/PAL flushes and resolves DCC/CB caches cleanly (issue #422 (a)).
     */
    static int colorToTransferSrcAccessMask() {
        return VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                | VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT;
    }

    /**
     * {@return the {@code [srcAccessMask, dstAccessMask]} pair for {@link #recordLayoutBarriers}}.
     * <p>
     * First-acquire transitions run from {@code TOP_OF_PIPE_BIT} where non-zero {@code srcAccessMask} is invalid
     * ({@code VUID-vkCmdPipelineBarrier-pImageMemoryBarriers-02819}); every subsequent acquire and every release
     * carries full attachment, transfer, and shader masks so layout transitions across {@code GENERAL} cannot skip
     * CB/DCC cache flushes or invalidations (issue #422 (a)).
     */
    static int[] layoutBarrierAccessMasks(boolean release, boolean firstAcquire, boolean depth) {
        int attachmentAccess = depth
                ? (VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                : (VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        int generalAccess = attachmentAccess | VK_ACCESS_SHADER_READ_BIT
                | VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT;
        if (release) {
            return new int[] { attachmentAccess | VK_ACCESS_TRANSFER_WRITE_BIT, generalAccess };
        }
        int src = firstAcquire ? 0 : generalAccess;
        int dst = attachmentAccess | VK_ACCESS_TRANSFER_WRITE_BIT;
        return new int[] { src, dst };
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
                        .oldLayout(DEPTH_TRANSFER_LAYOUT)
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
        recordReleaseBarriers(commandBuffer, true);
    }

    /**
     * The release that matches {@link #recordAcquireBarriers(VkCommandBuffer, boolean)}.
     * <p>
     * With {@code frameImageAttached} false the acquire recorded no layout barrier, so the image is still in GENERAL
     * and there is nothing to hand back: recording the release anyway would declare COLOR_RENDER_LAYOUT as its source
     * layout, which is the VUID-vkCmdPipelineBarrier-oldLayout-01197 the pair exists to avoid. The depth image's
     * layout is unchanged either way - both of its barriers name the attachment layout - so only the bookkeeping
     * line stays.
     */
    public void recordReleaseBarriers(VkCommandBuffer commandBuffer, boolean frameImageAttached) {
        if (frameImageAttached) {
            recordLayoutBarriers(commandBuffer, true);
            this.colorImageLayout = VK_IMAGE_LAYOUT_GENERAL;
        }
        // Depth stays in its attachment layout across windows: moving it to GENERAL and back at every window would
        // resolve HiZ each time on the hardware this layout exists for.
        this.depthImageLayout = DEPTH_RENDER_LAYOUT;
    }

    /**
     * The same layout transitions the shared target's ownership barriers perform, minus the queue-family
     * transfers: the images belong to this queue in every phase, so the barrier only moves layouts.
     */
    private void recordLayoutBarriers(VkCommandBuffer command, boolean release) {
        boolean first = this.firstAcquire && !release;
        int colorOldLayout = release ? COLOR_RENDER_LAYOUT : this.colorImageLayout;
        int colorNewLayout = release ? VK_IMAGE_LAYOUT_GENERAL : COLOR_RENDER_LAYOUT;
        int depthOldLayout = release ? DEPTH_RENDER_LAYOUT : this.depthImageLayout;
        int srcStage = first ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        if (layoutsAreSelfTransitions(colorOldLayout, colorNewLayout, depthOldLayout, DEPTH_RENDER_LAYOUT)) {
            // Steady state (every terrain pass after the first, and every release): both images already sit
            // in the layout this barrier would move them to, so the two image barriers perform no layout
            // transition at all - they are purely the execution and memory dependency the frame render pass
            // relies on (it declares no subpass dependencies; see VulkanTerrainPipeline.buildFrameRenderPass).
            // One memory barrier with the union of the two images' access masks carries a dependency that is
            // at least as strong, at the same stages, and skips the per-image transition bookkeeping the
            // driver records for each image barrier. The queue families are this queue on both sides of the
            // image form, which a memory barrier leaves untouched either way.
            int[] masks = selfTransitionBarrierMasks(release, first);
            try (MemoryStack stack = stackPush()) {
                VkMemoryBarrier.Buffer memoryBarrier = VkMemoryBarrier.calloc(1, stack);
                memoryBarrier.get(0).sType$Default()
                        .srcAccessMask(masks[0])
                        .dstAccessMask(masks[1]);
                vkCmdPipelineBarrier(command, srcStage, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        0, memoryBarrier, null, null);
            }
            return;
        }
        int[] colorMasks = layoutBarrierAccessMasks(release, first, false);
        int[] depthMasks = layoutBarrierAccessMasks(release, first, true);
        int queueFamily = this.context.queueFamilyIndex();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);

            VkImageMemoryBarrier colorBarrier = barriers.get(0);
            colorBarrier.sType$Default()
                    .image(this.colorImage)
                    // The colour image's true layout is whatever the last phase left it in - acquire, release or the
                    // present's own transition - so it is tracked rather than assumed, the way the present path
                    // reports the layout it restored through noteColorImageLayout.
                    .oldLayout(colorOldLayout)
                    .newLayout(colorNewLayout)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(colorMasks[0])
                    // Acquire can be followed by a game transfer clear before the attachment pass.
                    .dstAccessMask(colorMasks[1]);
            colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

            VkImageMemoryBarrier depthBarrier = barriers.get(1);
            depthBarrier.sType$Default()
                    .image(this.depthImage)
                    .oldLayout(depthOldLayout)
                    .newLayout(DEPTH_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(depthMasks[0])
                    .dstAccessMask(depthMasks[1]);
            depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

            vkCmdPipelineBarrier(command, srcStage,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
        }
    }

    /**
     * {@return whether both layout moves {@link #recordLayoutBarriers} is about to record leave their image in
     * the layout it already occupies}.
     * <p>
     * Only then may the pair of image barriers be recorded as one memory barrier: a memory barrier performs
     * no layout transition, so substituting it where a real transition is owed (the first acquire out of
     * {@code UNDEFINED}, or an acquire after the present path left the colour image elsewhere) would lose the
     * transition itself, not just its cost.
     */
    static boolean layoutsAreSelfTransitions(int colorOldLayout, int colorNewLayout,
            int depthOldLayout, int depthNewLayout) {
        return colorOldLayout == colorNewLayout && depthOldLayout == depthNewLayout;
    }

    /**
     * {@return the {@code [srcAccessMask, dstAccessMask]} pair for the memory-barrier form of
     * {@link #recordLayoutBarriers}}: the union of the colour and depth pairs from
     * {@link #layoutBarrierAccessMasks(boolean, boolean, boolean)}.
     * <p>
     * The union is deliberate: a memory barrier scopes its dependency to all memory rather than to one image,
     * so its masks must cover every access either image barrier covered. The result is a dependency at least
     * as strong as the pair it replaces - never a narrowing - which is what keeps the substitution inside the
     * issue #422 (a) full-mask rule.
     */
    static int[] selfTransitionBarrierMasks(boolean release, boolean firstAcquire) {
        int[] color = layoutBarrierAccessMasks(release, firstAcquire, false);
        int[] depth = layoutBarrierAccessMasks(release, firstAcquire, true);
        return new int[] { color[0] | depth[0], color[1] | depth[1] };
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
    // Both repeat the frame images' usages, and the default pair repeats its format too - so the frame-compatible
    // render pass built by VulkanTerrainPipeline.createFrameRenderPass accepts it unchanged, GENERAL attachments
    // included. A chain pair's colour format is the class its identity resolved (QUEUE 404): the default class is
    // the frame's format, and a non-default class gets its own variant pass instead. Both pairs are created on
    // demand at the size the game's framebuffer asks for rather than up front, because most sessions never bind
    // a second framebuffer at all.
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

    /** Makes the default pair exist at the requested size. See {@link FrameTargetPair#ensure(int, int)}. */
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

    /** Records and reports a pair's clear-and-barrier sequence. See {@link FrameTargetPair#selfTest}. */
    public String offTargetSelfTest(VkCommandBuffer command) {
        return this.defaultPair.selfTest(command);
    }

    /**
     * {@return the chain pair an identity owns}, creating it on first ask. Creating is what a binding's ensures and pass attaches need; callers that only look up - the
     * census, the summary, a report - use {@link #existingChainPair} so the pool does not grow on a read.
     */
    private FrameTargetPair chainPairFor(long identity) {
        FrameTargetPair pair = this.chainPairs.get(identity);
        if (pair != null) {
            return pair;
        }
        FrameTargetPair created = new FrameTargetPair("chain-" + Long.toHexString(identity), this.pairHost);
        this.chainPairs.put(identity, created);
        return created;
    }

    /** {@return the identity's chain pair without creating it}, null when none exists yet. */
    private FrameTargetPair existingChainPair(long identity) {
        return this.chainPairs.get(identity);
    }

    /** Retire the pair after its framebuffer and owned texture have been deleted. */
    public void retireChainPair(long identity) {
        FrameTargetPair pair = this.chainPairs.remove(identity);
        if (pair != null) {
            if (pair.colorImage != 0L) this.chainPairOwners.remove(pair.colorImage);
            long[] extras = pair.extraColorImageSnapshot();
            if (extras != null) {
                for (long extra : extras) {
                    this.chainPairOwners.remove(extra);
                }
            }
            pair.retire();
        }
    }

    /** {@return whether the identity's chain pair exists}, i.e. whether that framebuffer has drawn into it. */
    public boolean chainPairExists(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair != null && pair.exists();
    }

    /** Whether the identity's chain pair exists and is exactly the requested size. */
    public boolean chainPairMatches(long identity, int width, int height) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair != null && pair.matches(width, height);
    }

    /** {@return whether the identity's chain pair has been acquired}, i.e. moved out of UNDEFINED into its render layout. */
    public boolean chainPairAcquired(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair != null && pair.acquired;
    }

    /** {@return whether the identity's chain pair still owes the one-shot self-test for its current images}. */
    public boolean chainPairNeedsSelfTest(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair != null && pair.needsSelfTest();
    }

    /** The colour image the identity's chain pair renders into and its consumers sample, or 0 before it exists. */
    public long chainPairColorImage(long identity) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
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
        FrameTargetPair pair = existingChainPair(identity);
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
        FrameTargetPair pair = existingChainPair(identity);
        if (pair != null) {
            pair.noteColorLayout(layout);
        }
    }

    public long chainPairColorView(long identity) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.colorView;
    }

    /** The depth image view the identity's chain pair tests against, or 0 before it exists. */
    public long chainPairDepthView(long identity) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.depthView;
    }

    /**
     * The shared depth store (HAL wave 3: ownership moved to {@link SharedDepthStore}). This class keeps
     * only the glue the store cannot own: depth images are allocated through this class's one allocator
     * (device-chosen format, the pair depth's own allocation), and evicting an entry retires the chain
     * pairs that borrowed it, which is chain-pair business. Consumers read and record through
     * {@link #sharedDepths()} directly.
     */
    private final SharedDepthStore sharedDepthStore;

    /** {@return the shared depth store}: one depth image per depth-texture identity, borrowed by pairs. */
    public SharedDepthStore sharedDepths() {
        return this.sharedDepthStore;
    }

    /** Retires every chain pair that borrowed the depth under {@code depthKey}; they re-ensure on next use. */
    private void retirePairsBorrowing(long depthKey) {
        java.util.List<Long> borrowing = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, FrameTargetPair> entry : this.chainPairs.entrySet()) {
            if (entry.getValue().sharedDepthKey == depthKey) {
                borrowing.add(entry.getKey());
            }
        }
        for (long identity : borrowing) {
            retireChainPair(identity);
        }
    }

    /**
     * {@return the access mask an image last moved to {@code layout} may have pending}.
     * <p>
     * The one table both of a pair's own acquires use, so a barrier's {@code oldLayout} and {@code srcAccessMask}
     * cannot disagree: every layout the pair's images are ever left in by this class, by the clear's transfer move, or
     * by the sampling wrapper ({@code SHADER_READ_ONLY_OPTIMAL}) is answered here. A layout transition preserves
     * content, so naming the wrong source access leaves the previous writes unavailable rather than discarding them -
     * which is a synchronisation bug that only a validation layer or a wrong picture would show.
     */
    static int celtium$layoutSourceAccess(int layout) {
        if (layout == VK_IMAGE_LAYOUT_UNDEFINED) {
            return 0;
        }
        if (layout == DEPTH_TRANSFER_LAYOUT) {
            return VK_ACCESS_TRANSFER_WRITE_BIT;
        }
        if (layout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            return VK_ACCESS_SHADER_READ_BIT;
        }
        if (layout == DEPTH_RENDER_LAYOUT) {
            return VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                    | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        }
        return VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    }

    /**
     * Points the identity's chain pair at the shared depth image for {@code depthKey}, creating the image at
     * the depth texture's own extent. Sharing engages only when the depth extent equals the pair's extent:
     * a Vulkan framebuffer's attachments share one extent, and a depth texture larger or smaller than the
     * colour target it is attached beside has no honest framebuffer here — the pair then keeps its private
     * depth and the caller counts the skip.
     *
     * @return whether the pair now renders into the shared depth image
     */
    public boolean ensureChainPairSharedDepth(long identity, long depthKey, int depthWidth, int depthHeight) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        if (pair == null || !pair.exists() || depthWidth <= 0 || depthHeight <= 0) {
            return false;
        }
        if (pair.width != depthWidth || pair.height != depthHeight) {
            return false;
        }
        long[] shared = this.sharedDepthStore.ensure(depthKey, depthWidth, depthHeight);
        if (pair.sharedDepthKey == depthKey && pair.depthImage == shared[0]) {
            return true;
        }
        int prevColorCount = pair.colorCount();
        long oldColor = pair.colorImage;
        long[] oldExtras = pair.extraColorImageSnapshot();
        if (oldColor != 0L) {
            this.chainPairOwners.remove(oldColor);
        }
        if (oldExtras != null) {
            for (long extra : oldExtras) {
                this.chainPairOwners.remove(extra);
            }
        }
        try {
            pair.ensure(pair.width, pair.height, depthKey, shared);
            if (prevColorCount > 1) {
                pair.ensureExtras(prevColorCount);
            }
        } finally {
            if (pair.colorImage != 0L) {
                this.chainPairOwners.put(pair.colorImage, identity);
            }
            long[] newExtras = pair.extraColorImageSnapshot();
            if (newExtras != null) {
                for (long extra : newExtras) {
                    this.chainPairOwners.put(extra, identity);
                }
            }
        }
        return true;
    }

    /** {@return whether the identity's chain pair renders into the shared depth image for {@code depthKey}}. */
    public boolean chainPairUsesSharedDepth(long identity, long depthKey) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair != null && pair.sharedDepthKey == depthKey && pair.depthImage != 0L
                && pair.depthImage == sharedDepthStore.image(depthKey);
    }

    /** Makes the identity's chain pair exist at the requested size. See {@link FrameTargetPair#ensure(int, int)}. */
    public void ensureChainPair(long identity, int width, int height) {
        FrameTargetPair pair = chainPairFor(identity);
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
     * Makes the identity's chain pair exist at the requested size in {@code formatClass} (QUEUE 404, S3): the
     * pair is rebuilt when its live class differs, through the same retire path a resize takes. This is the
     * one ensure that sets a pair's class, and a pair's class is written in exactly one of two places: the
     * registration junction, which resolves a raw pair's class from its GL request, and the off-target
     * window path, which passes the resolved class for pairs no junction resolves (a ShaderGroup group
     * pair has no GL request; see {@code VulkanTerrainGpuBackend.celtium$pairClassForWindow}). Every
     * consumer only follows the pair's recorded class and never re-reads a request (rule R5).
     */
    public void ensureChainPair(long identity, int width, int height, TargetFormatClass formatClass) {
        FrameTargetPair pair = chainPairFor(identity);
        if (pair.matches(width, height) && pair.matchesFormat(formatClass)) return;
        if (pair.colorImage != 0L) this.chainPairOwners.remove(pair.colorImage);
        try {
            pair.ensure(width, height, 0L, null, formatClass);
        } finally {
            if (pair.colorImage != 0L) this.chainPairOwners.put(pair.colorImage, identity);
        }
    }

    /**
     * {@return the format class the identity's chain pair is allocated in}, or {@code RGBA8_UNORM} before the
     * pair exists - the answer consumers gate on (the window's render-pass choice, the post chain's adoption
     * refusal, the readback refusal).
     */
    public TargetFormatClass chainPairFormatClass(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null ? TargetFormatClass.RGBA8_UNORM : pair.formatClass;
    }

    /**
     * {@return whether {@code image} is a colour image of a chain pair allocated in a non-default format
     * class}. The readback path asks this because it expands 4 bytes per pixel: a 16F pair's image read that
     * way is half read and wrong, not merely imprecise (QUEUE 404 rule R10).
     */
    public boolean chainPairImageHasNonDefaultFormat(long image) {
        long identity = chainPairIdentityForImage(image);
        return identity != Long.MIN_VALUE
                && chainPairFormatClass(identity) != TargetFormatClass.RGBA8_UNORM;
    }

    /**
     * Makes the identity's chain pair exist at the requested size, holding colour images for {@code attachments}
     * colour targets: the pair itself first (its ensure retires any extras at the old size), then the tuple.
     * The owners map learns the extra images here, so the sampling side's membership questions
     * ({@link #isChainPairColorImage}, {@link #chainPairIdentityForImage}) cover every slot, not only slot 0.
     */
    public void ensureChainPairColors(long identity, int attachments, int width, int height) {
        // The class-preserving form: the pair keeps the class the registration junction resolved for it
        // (rule R5). A pair nobody resolved a class for keeps RGBA8_UNORM, its field's initial value.
        FrameTargetPair existing = existingChainPair(identity);
        ensureChainPairColors(identity, attachments, width, height,
                existing == null ? TargetFormatClass.RGBA8_UNORM : existing.formatClass);
    }

    /**
     * {@link #ensureChainPairColors(long, int, int, int)} in a resolved format class (QUEUE 404, S3): the
     * pair is ensured in the class first, then the tuple - the extras are allocated in the pair's class, so
     * the whole tuple shares one format (rule R4).
     */
    public void ensureChainPairColors(long identity, int attachments, int width, int height,
            TargetFormatClass formatClass) {
        FrameTargetPair pair = chainPairFor(identity);
        long[] before = pair.extraColorImageSnapshot();
        ensureChainPair(identity, width, height, formatClass);
        pair.ensureExtras(attachments);
        long[] after = pair.extraColorImageSnapshot();
        if (before != null) {
            for (long extra : before) {
                boolean kept = false;
                if (after != null) {
                    for (long live : after) {
                        if (live == extra) {
                            kept = true;
                            break;
                        }
                    }
                }
                if (!kept) {
                    this.chainPairOwners.remove(extra);
                }
            }
        }
        if (after != null) {
            for (long extra : after) {
                this.chainPairOwners.put(extra, identity);
            }
        }
    }

    /** {@return how many colour targets the identity's chain pair holds images for}, 0 before the pair exists. */
    public int chainPairColorCount(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null ? 0 : pair.colorCount();
    }

    /**
     * Ensures extra colour slot {@code slot} (1-based) exists on {@code identity}'s chain pair and initializes any
     * newly allocated {@code UNDEFINED} slot to {@code (0, 0, 0, 1)} in {@code GENERAL} layout outside a render pass,
     * so deferred/composite mod programs sampling an unwritten MRT slot (such as {@code shadowcolor1}) resolve a
     * valid native image instead of failing with {@code slot-image-zero}.
     */
    public boolean ensureAndInitializeExtraColor(long identity, int slot, int width, int height,
            VkCommandBuffer command) {
        checkAlive();
        if (slot <= 0 || slot >= 8 || width <= 0 || height <= 0 || command == null || command.address() == 0L) {
            return false;
        }
        int needed = Math.max(chainPairColorCount(identity), slot + 1);
        ensureChainPairColors(identity, needed, width, height, chainPairFormatClass(identity));
        FrameTargetPair pair = existingChainPair(identity);
        if (pair == null || pair.extraColorImages == null || slot - 1 >= pair.extraColorCount) {
            return false;
        }
        return initializeExtraColorIfUndefined(identity, slot, command);
    }

    /**
     * Initializes extra colour slot {@code slot} (1-based) of an <em>existing</em> pair to {@code (0, 0, 0, 1)} in
     * the colour render layout when it is still {@code UNDEFINED}, without allocating or resizing anything. Extras
     * are allocated at framebuffer registration (outside any command buffer), so a slot that no pass has written yet
     * (e.g. {@code shadowcolor1}) is still {@code UNDEFINED} when a later pass samples it as {@code GENERAL}.
     * Must be called outside a render pass.
     *
     * @return whether the slot holds a live image (initialized now or before)
     */
    public boolean initializeExtraColorIfUndefined(long identity, int slot, VkCommandBuffer command) {
        checkAlive();
        if (slot <= 0 || slot >= 8 || command == null || command.address() == 0L) {
            return false;
        }
        FrameTargetPair pair = existingChainPair(identity);
        if (pair == null || pair.extraColorImages == null || slot - 1 >= pair.extraColorCount) {
            return false;
        }
        int idx = slot - 1;
        long extraImage = pair.extraColorImages[idx];
        long extraView = pair.extraColorViews[idx];
        if (extraImage == 0L || extraView == 0L) {
            return false;
        }
        if (pair.extraColorLayouts[idx] == VK_IMAGE_LAYOUT_UNDEFINED) {
            int queueFamily = this.context.queueFamilyIndex();
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer toClear = VkImageMemoryBarrier.calloc(1, stack);
                toClear.get(0).sType$Default()
                        .image(extraImage)
                        .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .newLayout(COLOR_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily)
                        .dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(0)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                toClear.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, null, toClear);

                VkClearColorValue clear = VkClearColorValue.calloc(stack);
                clear.float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1.0f);
                VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                        .set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                vkCmdClearColorImage(command, extraImage, COLOR_RENDER_LAYOUT, clear, range);

                VkImageMemoryBarrier.Buffer toRead = VkImageMemoryBarrier.calloc(1, stack);
                toRead.get(0).sType$Default()
                        .image(extraImage)
                        .oldLayout(COLOR_RENDER_LAYOUT)
                        .newLayout(COLOR_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily)
                        .dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                toRead.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        0, null, null, toRead);
            }
            pair.extraColorLayouts[idx] = COLOR_RENDER_LAYOUT;
        }
        return true;
    }

    /** The colour image at a slot of the identity's chain pair (0 = the pair's own), or 0 when it holds none. */
    public long chainPairColorImageAt(long identity, int slot) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.colorImageAt(slot);
    }

    /** The colour view at a slot of the identity's chain pair (0 = the pair's own), or 0 when it holds none. */
    public long chainPairColorViewAt(long identity, int slot) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.colorViewAt(slot);
    }

    /**
     * {@return which tuple images the identity's chain pair holds now}, 0 before any are built. Changes whenever
     * the extras are built or dropped, at any count: a framebuffer over the previous views names dead images.
     */
    public long chainPairColorTupleGeneration(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.extraGeneration;
    }

    /**
     * Records the acquire the identity's chain pair needs before a render pass attaches it for draws, outside any
     * clear: the colour moves from wherever it is to {@code GENERAL} and the depth - borrowed from the shared store or
     * the pair's own - moves to the attachment layout the frame pass declares.
     * <p>
     * Distinct from {@link #clearChainPair} and {@link FrameTargetPair#deferClear}: those are the clear's own entry points and
     * leave the depth in the clear's transfer layout, because a {@code vkCmdClearDepthStencilImage} needs it there. A
     * pass that draws into the pair without clearing it first (item 332's routed terrain pass) has to arrive at the
     * attachment layout directly, and must not discard what another pair sharing the same depth image already wrote -
     * so the barrier's old layout is the store's record rather than {@code UNDEFINED}.
     *
     * @param depthKey the shared depth store key this pair borrows, 0 when its depth is private
     */
    public void recordChainPairAttachAcquire(long identity, long depthKey, VkCommandBuffer command) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        if (pair != null && pair.exists()) {
            pair.recordAttachAcquire(command);
        }
    }

    /**
     * Records the hand-back a routed pass owes after its draws: the shared depth image is left in the attachment
     * layout and that layout is recorded in the store, which is what a later depth copy reads as its {@code oldLayout}.
     * No command is recorded - the pass' {@code finalLayout} is that same layout, so nothing has to move - but the
     * store's record is what makes the copy's source layout readable, and a copy whose record says
     * {@code UNDEFINED} discards the content it is supposed to serve.
     */
    public void recordChainPairAttachRelease(long identity, long depthKey) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        if (pair != null && pair.exists()) {
            pair.noteColorLayout(COLOR_RENDER_LAYOUT);
        }
        if (depthKey != 0L) {
            sharedDepthStore.noteLayout(depthKey, DEPTH_RENDER_LAYOUT);
        }
    }

    /** Records the acquire barriers the identity's tuple images need before a pass attaches them. */
    public void recordChainPairColorAcquire(long identity, VkCommandBuffer command) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        if (pair != null) {
            pair.recordExtrasAcquire(command);
        }
    }

    /**
     * Copies one colour slot from {@code srcIdentity}'s chain pair into {@code dstIdentity}'s chain pair outside
     * a render pass, keeping both images in {@link #COLOR_RENDER_LAYOUT} ({@code GENERAL}).
     * <p>
     * Used when a depth-attached framebuffer (such as Iris {@code gbuffers_water} / {@code gbuffers_hand} attaching
     * {@code colortex0} ALT + {@code depthtex0}) draws on top of a colour texture whose most recent writer was a
     * different chain pair (such as depth-free {@code deferred1}) and therefore cannot directly share the pair
     * without destroying its colour image. Uses {@code vkCmdCopyImage} when both pairs share a format class and
     * {@code vkCmdBlitImage} otherwise.
     *
     * @return whether the copy was recorded
     */
    public boolean copyChainPairColorSlot(long srcIdentity, int srcSlot, long dstIdentity, int dstSlot,
            VkCommandBuffer command) {
        checkAlive();
        if (command == null || command.address() == 0L || srcIdentity == dstIdentity || srcSlot < 0 || dstSlot < 0) {
            return false;
        }
        FrameTargetPair src = existingChainPair(srcIdentity);
        FrameTargetPair dst = existingChainPair(dstIdentity);
        if (src == null || dst == null || !src.exists() || !dst.exists()) {
            return false;
        }
        if (src.width <= 0 || src.height <= 0 || src.width != dst.width || src.height != dst.height) {
            return false;
        }
        long srcImage = src.colorImageAt(srcSlot);
        long dstImage = dst.colorImageAt(dstSlot);
        if (srcImage == 0L || dstImage == 0L || srcImage == dstImage) {
            return false;
        }
        int srcLayout = srcSlot == 0 ? src.colorLayout()
                : (src.extraColorLayouts != null && srcSlot - 1 < src.extraColorCount
                        ? src.extraColorLayouts[srcSlot - 1] : VK_IMAGE_LAYOUT_UNDEFINED);
        if (srcLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
            return false;
        }
        int dstLayout = dstSlot == 0 ? dst.colorLayout()
                : (dst.extraColorLayouts != null && dstSlot - 1 < dst.extraColorCount
                        ? dst.extraColorLayouts[dstSlot - 1] : VK_IMAGE_LAYOUT_UNDEFINED);
        int queueFamily = this.context.queueFamilyIndex();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer pre = VkImageMemoryBarrier.calloc(2, stack);
            pre.get(0).sType$Default()
                    .image(srcImage)
                    .oldLayout(srcLayout).newLayout(COLOR_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(celtium$layoutSourceAccess(srcLayout))
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            pre.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            pre.get(1).sType$Default()
                    .image(dstImage)
                    .oldLayout(dstLayout).newLayout(COLOR_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(dstLayout == VK_IMAGE_LAYOUT_UNDEFINED ? 0 : celtium$layoutSourceAccess(dstLayout))
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            pre.get(1).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, pre);

            if (src.formatClass == dst.formatClass) {
                org.lwjgl.vulkan.VkImageCopy.Buffer region = org.lwjgl.vulkan.VkImageCopy.calloc(1, stack);
                region.get(0).srcSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
                region.get(0).srcOffset().set(0, 0, 0);
                region.get(0).dstSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
                region.get(0).dstOffset().set(0, 0, 0);
                region.get(0).extent().set(dst.width, dst.height, 1);
                vkCmdCopyImage(command, srcImage, COLOR_RENDER_LAYOUT, dstImage, COLOR_RENDER_LAYOUT, region);
            } else {
                org.lwjgl.vulkan.VkImageBlit.Buffer region = org.lwjgl.vulkan.VkImageBlit.calloc(1, stack);
                region.get(0).srcSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
                region.get(0).srcOffsets(0).set(0, 0, 0);
                region.get(0).srcOffsets(1).set(src.width, src.height, 1);
                region.get(0).dstSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
                region.get(0).dstOffsets(0).set(0, 0, 0);
                region.get(0).dstOffsets(1).set(dst.width, dst.height, 1);
                vkCmdBlitImage(command, srcImage, COLOR_RENDER_LAYOUT, dstImage, COLOR_RENDER_LAYOUT,
                        region, VK_FILTER_NEAREST);
            }

            VkImageMemoryBarrier.Buffer post = VkImageMemoryBarrier.calloc(1, stack);
            post.get(0).sType$Default()
                    .image(dstImage)
                    .oldLayout(COLOR_RENDER_LAYOUT).newLayout(COLOR_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                            | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_SHADER_READ_BIT);
            post.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    0, null, null, post);
        }
        if (srcSlot == 0) {
            src.noteColorLayout(COLOR_RENDER_LAYOUT);
        } else {
            src.noteExtraColorLayout(srcSlot, COLOR_RENDER_LAYOUT);
        }
        if (dstSlot == 0) {
            dst.noteColorLayout(COLOR_RENDER_LAYOUT);
        } else {
            dst.noteExtraColorLayout(dstSlot, COLOR_RENDER_LAYOUT);
        }
        VulkanGlTexture.noteExternalForeignLayout(command, srcImage, COLOR_RENDER_LAYOUT);
        VulkanGlTexture.noteExternalForeignLayout(command, dstImage, COLOR_RENDER_LAYOUT);
        return true;
    }

    /**
     * Copies the depth image of {@code srcIdentity}'s chain pair into {@code dstIdentity}'s chain pair
     * outside a render pass (alpha-shadow-cache-form): the source returns to the layout it was found in and
     * the destination is left in {@link #DEPTH_RENDER_LAYOUT}, the layout a pass attaching it expects. The
     * colour twin is {@link #copyChainPairColorSlot}; depth has no slot axis and pair depths share one
     * format ({@code VK_FORMAT_D32_SFLOAT}), so a plain {@code vkCmdCopyImage} always applies.
     * <p>
     * Used by the pack shadow result cache: capture copies the just-recorded shadow pair's depth into the
     * snapshot pair, restore copies it back. A borrowed (shared) source depth is only read, and its layout
     * record - held by the shared store, see {@code FrameTargetPair.depthLayout()} - is restored by the post barrier,
     * so the copy leaves the sharing pairs' beliefs untouched.
     *
     * @return whether the copy was recorded
     */
    public boolean copyChainPairDepth(long srcIdentity, long dstIdentity, VkCommandBuffer command) {
        checkAlive();
        if (command == null || command.address() == 0L || srcIdentity == dstIdentity) {
            return false;
        }
        FrameTargetPair src = existingChainPair(srcIdentity);
        FrameTargetPair dst = existingChainPair(dstIdentity);
        if (src == null || dst == null || !src.exists() || !dst.exists()) {
            return false;
        }
        if (src.width <= 0 || src.height <= 0 || src.width != dst.width || src.height != dst.height) {
            return false;
        }
        long srcImage = src.depthImage;
        long dstImage = dst.depthImage;
        if (srcImage == 0L || dstImage == 0L || srcImage == dstImage) {
            return false;
        }
        int srcLayout = src.depthLayout();
        int dstLayout = dst.depthLayout();
        // A UNDEFINED source holds nothing worth copying; a UNDEFINED destination (a snapshot pair
        // created for this capture and never written) is fine - its barrier discards, as any
        // UNDEFINED-oldLayout transition does.
        if (srcLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
            return false;
        }
        int queueFamily = this.context.queueFamilyIndex();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer pre = VkImageMemoryBarrier.calloc(2, stack);
            pre.get(0).sType$Default()
                    .image(srcImage)
                    .oldLayout(srcLayout).newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(celtium$layoutSourceAccess(srcLayout))
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            pre.get(0).subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            pre.get(1).sType$Default()
                    .image(dstImage)
                    .oldLayout(dstLayout).newLayout(DEPTH_TRANSFER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(celtium$layoutSourceAccess(dstLayout))
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            pre.get(1).subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, pre);

            org.lwjgl.vulkan.VkImageCopy.Buffer region = org.lwjgl.vulkan.VkImageCopy.calloc(1, stack);
            region.get(0).srcSubresource().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 0, 1);
            region.get(0).srcOffset().set(0, 0, 0);
            region.get(0).dstSubresource().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 0, 1);
            region.get(0).dstOffset().set(0, 0, 0);
            region.get(0).extent().set(dst.width, dst.height, 1);
            vkCmdCopyImage(command, srcImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    dstImage, DEPTH_TRANSFER_LAYOUT, region);

            VkImageMemoryBarrier.Buffer post = VkImageMemoryBarrier.calloc(2, stack);
            post.get(0).sType$Default()
                    .image(srcImage)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL).newLayout(srcLayout)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(celtium$layoutSourceAccess(srcLayout));
            post.get(0).subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            post.get(1).sType$Default()
                    .image(dstImage)
                    .oldLayout(DEPTH_TRANSFER_LAYOUT).newLayout(DEPTH_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_SHADER_READ_BIT);
            post.get(1).subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    0, null, null, post);
        }
        src.depthLayout(command, srcLayout);
        dst.depthLayout(command, DEPTH_RENDER_LAYOUT);
        return true;
    }

    /** Records a layout an extra colour image of the identity's pair has been moved to by the sampling side. */
    public void noteChainPairColorLayoutAt(long identity, int slot, int layout) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        if (pair != null) {
            pair.noteExtraColorLayout(slot, layout);
        }
    }

    /** {@return the slot an image fills in the identity's chain pair}, or -1 when it fills none. */
    public int chainPairColorSlotForImage(long identity, long image) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null ? -1 : pair.colorSlotForImage(image);
    }

    /**
     * Records a layout at a slot the caller already resolved (the published walk resolves it once per
     * cache rebuild, when the pair generation changes), skipping the write when the pair's record
     * already names {@code layout}.
     * <p>
     * The walk used to pay two pair lookups and a slot scan per name per pass for this record
     * ({@code chainPairColorSlotForImage} plus the note's own lookup), and wrote the same value back
     * on every settled pass. One lookup remains - the record is read before it is trusted, because
     * the pair's own acquire and clear paths also write it between walks, so a caller's memo of the
     * last value it wrote would not be a sound reason to skip.
     *
     * @return whether the pair's record changed
     */
    public boolean noteChainPairColorLayoutAtSlotIfChanged(long identity, int slot, int layout) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        if (pair == null || slot < 0) {
            return false;
        }
        if (slot == 0) {
            if (pair.colorLayout() == layout) {
                return false;
            }
            pair.noteColorLayout(layout);
            return true;
        }
        if (pair.extraColorLayoutAt(slot) == layout) {
            return false;
        }
        pair.noteExtraColorLayout(slot, layout);
        return true;
    }

    /**
     * A framebuffer can be sampled before its first draw (vanilla's empty outline target does this every frame).
     * Initialize the owned attachment, rather than manufacturing an unwritten texture mirror under its name.
     * This one-time bootstrap completes before publication; subsequent sampling adds no submission or wait.
     */
    public void initializeChainPairForSampling(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        if (pair == null || !pair.exists() || pair.colorLayout() != VK_IMAGE_LAYOUT_UNDEFINED) return;
        VkCommandBuffer command = this.context.beginCommands();
        pair.clearWith(command, 0x4100, new float[4], 1.0);
        this.context.endCommands(command, "initialize framebuffer before first sample");
    }

    public void clearChainPair(long identity, VkCommandBuffer command) {
        FrameTargetPair pair = existingChainPair(identity);
        if (pair != null) {
            pair.clear(command);
        }
    }

    /**
     * {@return whether a pair clear is recorded inside the pass that follows it}.
     * <p>
     * The rule lives on {@code FrameTargetPair} because that is the class whose clear it changes; this accessor is what the
     * backend and the test read, because {@code FrameTargetPair} is private to this class.
     */
    static boolean celtium$pairClearInPass() {
        return FrameTargetPair.PAIR_CLEAR_IN_PASS;
    }

    /**
     * {@return whether a deferred clear has to move the depth to the attachment layout first}.
     * <p>
     * Package-private and static so the rule is pinned without a device; see
     * {@link FrameTargetPair#celtium$deferredClearNeedsDepthBarrier(int)}.
     */
    static boolean celtium$deferredClearNeedsDepthBarrier(int depthLayout) {
        return FrameTargetPair.celtium$deferredClearNeedsDepthBarrier(depthLayout);
    }

    /**
     * {@return the deferred clear's extent along one axis, clamped to the attachment}; see
     * {@link FrameTargetPair#celtium$deferredClearExtent(int, int)}.
     */
    static int celtium$deferredClearExtent(int passExtent, int pairExtent) {
        return FrameTargetPair.celtium$deferredClearExtent(passExtent, pairExtent);
    }

    /**
     * {@return whether a pair's colour tuple must be rebuilt for a draw into {@code attachments} targets};
     * package-private and static so the rule is pinned without a device; see
     * {@link FrameTargetPair#celtium$extrasNeedRebuild(int, int)}.
     */
    static boolean celtium$extrasNeedRebuild(int liveExtraCount, int attachments) {
        return FrameTargetPair.celtium$extrasNeedRebuild(liveExtraCount, attachments);
    }

    /**
     * {@return whether a pair must be rebuilt for an ensure}; package-private and static so the rule is pinned
     * without a device; see {@link FrameTargetPair#celtium$pairRebuildNeeded(boolean, boolean, TargetFormatClass,
     * TargetFormatClass)}.
     */
    static boolean celtium$pairRebuildNeeded(boolean sizeMatches, boolean sharedMatches,
            TargetFormatClass liveClass, TargetFormatClass wantedClass) {
        return FrameTargetPair.celtium$pairRebuildNeeded(sizeMatches, sharedMatches, liveClass, wantedClass);
    }

    /**
     * Defers the identity's chain-pair clear to the pass that follows it; see {@link FrameTargetPair#PAIR_CLEAR_IN_PASS}.
     *
     * @return whether a pair existed and the clear was deferred
     */
    public boolean deferChainPairClear(long identity, VkCommandBuffer command) {
        FrameTargetPair pair = existingChainPair(identity);
        if (pair == null) {
            return false;
        }
        pair.deferClear(command);
        return true;
    }

    /** {@return whether the identity's chain pair is waiting for a deferred clear}. */
    public boolean chainPairHasDeferredClear(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair != null && pair.hasDeferredClear();
    }

    /**
     * Records the identity's deferred chain-pair clear inside the pass that has just begun.
     *
     * @return whether a clear was waiting
     */
    public boolean recordChainPairDeferredClear(long identity, VkCommandBuffer command, int width, int height) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair != null && pair.recordDeferredClearAttachments(command, width, height);
    }

    /**
     * The deferred clear for a pass that attaches {@code colorAttachments} colour targets; see
     * {@link FrameTargetPair#recordDeferredClearAttachments(VkCommandBuffer, int, int, int)}.
     *
     * @return whether a clear was waiting
     */
    public boolean recordChainPairDeferredClear(long identity, VkCommandBuffer command, int width, int height,
            int colorAttachments) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair != null && pair.recordDeferredClearAttachments(command, width, height, colorAttachments);
    }

    /**
     * A game's own {@code glClear} against the identity's pair, with the colour and depth the game latched.
     * <p>
     * Recorded at the point the clear was consumed, which keeps it between the draws before it and the draws after it
     * - the once-per-frame clear in {@link #clearChainPair} is the pair's initialisation, while this is an explicit
     * mid-frame clear: measured in the fixture's clear-order phase, where a second {@code framebufferClear} after a
     * drawn quad must erase that quad (before this route it was counted and dropped, and the quad survived).
     * The pair's {@code acquired} barrier is honoured: a pair that never recorded its acquire gets it here first,
     * exactly as {@link FrameTargetPair#clear} does.
     *
     * @return whether any clear commands were recorded (false when the pair does not exist yet)
     */
    public boolean applyGameClearToChainPair(long identity, VkCommandBuffer command, int mask,
            float[] rgba, double depth) {
        checkAlive();
        FrameTargetPair pair = existingChainPair(identity);
        if (pair == null || !pair.exists() || command == null || command.address() == 0L) {
            return false;
        }
        pair.clearWith(command, mask, rgba, depth);
        return true;
    }

    /** {@return how many chain-pair image sets have been created}, the cache key for name-to-image resolutions. */
    public long chainPairGenerationTotal() {
        return this.chainPairGenerations;
    }

    /**
     * {@return which images the identity's chain pair holds now}, 0 before it exists. Changes whenever the pair is
     * created or replaced, at any size: a framebuffer over the previous images names dead views.
     */
    public long chainPairGeneration(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null || !pair.exists() ? 0L : pair.generation;
    }

    /** {@return the identity's chain-pair extent as {w, h}}, or null before the pair exists. */
    public long[] chainPairExtent(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null || !pair.exists() ? null : new long[] {pair.width, pair.height};
    }

    /** Zero-allocation width getter for hot-path off-target windows; returns 0 before the pair exists. */
    public int chainPairWidth(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null || !pair.exists() ? 0 : pair.width;
    }

    /** Zero-allocation height getter for hot-path off-target windows; returns 0 before the pair exists. */
    public int chainPairHeight(long identity) {
        FrameTargetPair pair = existingChainPair(identity);
        return pair == null || !pair.exists() ? 0 : pair.height;
    }

    public String chainPairSelfTest(long identity, VkCommandBuffer command) {
        FrameTargetPair pair = existingChainPair(identity);
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
        for (java.util.Map.Entry<Long, FrameTargetPair> entry : this.chainPairs.entrySet()) {
            summary.append(' ').append(entry.getValue().summary());
        }
        return summary.toString();
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
                + " " + this.presentLag.describe()
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
        for (FrameTargetPair pair : this.chainPairs.values()) {
            pair.release();
        }
        this.chainPairs.clear();
        this.chainPairOwners.clear();
        // The shared depth images are the store's own: pairs only borrowed them, so the store destroys
        // them here, after the pairs have handed their references back.
        this.sharedDepthStore.destroyAll();
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
