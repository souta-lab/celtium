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
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;

import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.hal.PresentEngine;
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
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

/**
 * The frame image when no OpenGL context exists: the colour and depth images are created, not imported, and there
 * is no GL side to hand them to.
 * <p>
 * There is no semaphore pair to cycle (Stage 2 submit unification): the wait/done binary semaphores this
 * target used to signal at {@link #beginFrame()} and consume at {@link #endFrame()} synchronized the renderer
 * with itself on one queue, where submission order already gives the same ordering, so the pair - and the
 * signal-only and wait-only queue packets that carried it - were removed. {@code beginFrame} and
 * {@code endFrame} now only keep this class's own frame-open bookkeeping.
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

    static final int COLOR_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;
    /**
     * Depth rests in the attachment layout, not GENERAL: on Intel gfx9 a writable GENERAL depth image has no HiZ
     * (no hierarchical depth culling, no fast depth clears), and nothing outside this renderer reads it - the GL-less
     * target is never handed to OpenGL. Clears and readbacks move it to their transfer layouts and back.
     */
    static final int DEPTH_RENDER_LAYOUT = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
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
    /**
     * The format this target's frame colour image is allocated in, and its class (Stage 1 BGRA ignition,
     * QUEUE 404 rule R1 revised): {@code VK_FORMAT_R8G8B8A8_UNORM} on a classic surface, {@code
     * VK_FORMAT_B8G8R8A8_UNORM} when the frame followed a BGRA surface so its generations can be borrowed.
     * Fixed at construction - a target never changes format; a recreation builds a new target, and the
     * context's noted decision gives it the same format.
     */
    private final int colorFormat;
    private final TargetFormatClass frameFormatClass;

    private long colorImage;
    private long colorMemory;
    private long colorView;
    private long depthImage;
    private long depthMemory;
    private long depthView;
    /**
     * The default-framebuffer pair: created by {@link #ensureOffTarget(int, int)} on first use, replaced when the
     * requested size changes, and released by {@link #delete()} like every other resource here. It is never handed
     * to GL and never leaves this queue family, so its only layout bookkeeping is the one-time UNDEFINED to GENERAL
     * move {@link #clearOffTarget} records before the first clear.
     * <p>
     * Its one writer is the game's draw stream with no framebuffer bound (identity 0).
     */
    private final Pair defaultPair;
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
    /**
     * The tracked layout of the image {@link #colorImage()} currently answers for - the borrowed
     * swapchain image while a borrow is live, the owned image otherwise. Every barrier and clear in
     * this class that moves or uses the frame colour image acts on that same image: the two must
     * never diverge, because a barrier recorded against the owned image while the ledger describes
     * the borrowed one (or the reverse) leaves the image the frame actually draws into in a layout
     * nobody recorded - the VUID-vkCmdDraw-None-09600 family the borrow legs reported around
     * swapchain images, and a present that transitions the borrowed image from a layout it is not in.
     */
    private int colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    private int depthImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    /**
     * The swapchain image the current frame borrows as its colour target (direct present path, A→B
     * overhaul Stage 1; lanechat #8830 (3)(4)), or null while the frame renders into the owned image.
     * While a borrow is live, {@link #colorImage()} / {@link #colorView()} answer the borrowed handles
     * and {@link #colorImageLayout} tracks the borrowed image; the owned image's resting layout is kept
     * in {@link #ownedColorImageLayout} and restored when the borrow ends. The borrowed image and its
     * view are the lender's (the context's {@code PresentEngine}): this class never destroys them.
     */
    private PresentEngine.BorrowedImage presentBorrow;
    /** The owned colour image's tracked layout, saved at borrow time and restored when the borrow ends. */
    private int ownedColorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    /**
     * The per-image acquire semaphore pool for borrowed images, handed out under the ownership
     * discipline the swapchain's own acquire pool uses: a semaphore is owned by the image its acquire
     * returned until that image is acquired again - that acquire returning proves the image's previous
     * present was consumed, and the submission that waited on the old semaphore was ordered before
     * that present. Plain cursor cycling reused a semaphore whose waiting submission the GPU had not
     * executed yet whenever the frames ran behind, which is
     * VUID-vkAcquireNextImageKHR-semaphore-01779 (the owned pool's l8switch-amd measurement, and ten
     * messages inside one second of the run-s2borrow legs for this pool). Sized to the lender's image
     * count at first; grows by one when every semaphore is owned.
     */
    private long[] presentAcquireSemaphores = new long[0];
    private long[] presentAcquireOwnerImage = new long[0];
    private int presentAcquireCursor;
    /** The semaphore the live borrow's acquire signals; handed out once by {@link #presentAcquireSemaphore()}. */
    private long presentAcquireSemaphoreUsed = VK_NULL_HANDLE;
    private boolean presentAcquireWaitPending;
    private int abandonedFrames;
    private String lastAbandonReason = "none";

    private GllessFrameTarget(VulkanContext context, int width, int height) {
        this.context = context;
        this.width = width;
        this.height = height;
        // The frame's own format (Stage 1 BGRA ignition, QUEUE 404 rule R1 revised): decided by the
        // context from the surface's adopted format - the caller (CopyReadSide.ensureFrameTarget) gives
        // the present path its chance to create a generation first, so this reads the informed decision.
        // Assigned before the default pair exists because a pair's initial class is the frame's class:
        // the default pair is identity 0's pair, and identity 0 is pinned to the frame.
        this.frameFormatClass = context.celtium$frameFormatClass();
        this.colorFormat = this.frameFormatClass.vkFormat();
        this.defaultPair = new Pair("default");
        context.celtium$noteFrameColorFormat(this.colorFormat);
    }

    public static GllessFrameTarget create(VulkanContext context, int width, int height) {
        if (context == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("context, width and height must be set");
        }
        GllessFrameTarget target = new GllessFrameTarget(context, width, height);
        try {
            // The frame's own colour is the frame's class (QUEUE 404 rule R1, revised by Stage 1's BGRA
            // ignition): the image the finished frame is composed into and presented from follows the
            // surface's format, whatever a mod's own framebuffer asked for.
            long[] color = target.allocateImage(true, width, height, target.frameFormatClass);
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
            // the frame's class (RGBA8_UNORM, or BGRA8_UNORM when the frame followed a BGRA surface), a
            // chain pair passes the class its identity resolved. The class was device-checked by the
            // resolver (VulkanColorFormat.usable) before it reached a pair, so creation does not re-decide it.
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
        return hasLivePresentBorrow() ? this.presentBorrow.image() : this.colorImage;
    }

    /** {@return the VkFormat this target's frame colour image is allocated in}. */
    public int colorFormat() {
        return this.colorFormat;
    }

    /** {@return the format class of this target's frame colour image}: the class the frame pin serves. */
    public TargetFormatClass frameFormatClass() {
        return this.frameFormatClass;
    }

    /**
     * {@return the format class of a colour image this target owns} - the frame image (owned or, while a
     * borrow is live, the borrowed swapchain image, which the borrow gate only accepts in this target's
     * own format), the default pair's images, or a chain pair's images - or {@code null} when the image
     * is none of those (a texture, another target's image). The readback path asks it to unpack in the
     * image's own byte order and to refuse classes its 4-byte expansion cannot read.
     */
    public TargetFormatClass celtium$imageFormatClass(long image) {
        if (image == 0L) {
            return null;
        }
        if (hasLivePresentBorrow() && image == this.presentBorrow.image()) {
            return this.frameFormatClass;
        }
        if (image == this.colorImage) {
            return this.frameFormatClass;
        }
        if (this.defaultPair.ownsColorImage(image)) {
            return this.defaultPair.formatClass;
        }
        long identity = chainPairIdentityForImage(image);
        return identity == Long.MIN_VALUE ? null : chainPairFormatClass(identity);
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
        return hasLivePresentBorrow() ? this.presentBorrow.view() : this.colorView;
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

    /**
     * Borrows the next swapchain image from {@code engine} as this frame's colour target.
     * <p>
     * On success the borrowed image becomes what {@link #colorImage()} / {@link #colorView()} answer, the
     * tracked colour layout is switched to the layout the lender reports the image in
     * ({@link PresentEngine.BorrowedImage#initialLayout()}), and the acquire semaphore the borrow signals
     * is held for the frame's first submission ({@link #presentAcquireSemaphore()}). The owned image's
     * tracked layout is saved for {@link #releasePresentBorrow()} to restore. A borrow already live is
     * kept (one frame borrows one image); a failed acquire leaves the frame on the owned image and
     * returns false, with the acquire verdict readable from the engine.
     *
     * @return whether a borrow is live after the call
     */
    public boolean borrowNextPresentImage(PresentEngine engine, int frameSlot) {
        checkAlive();
        if (this.presentBorrow != null && !this.presentBorrow.isValid()) {
            // The lender retired the generation under this borrow (a swapchain rebuild): forget the stale
            // handle and fall through to a fresh borrow from the live generation.
            releasePresentBorrow();
        }
        if (this.presentBorrow != null) {
            return true;
        }
        // The format gate (Stage 1, overhaul): the frame's render passes are built for the frame colour
        // format (R1 revised: the surface's format, followed at target creation - see the constructor),
        // so a swapchain generation in any other format can never be borrowed as the render target; its
        // frames take the composed present instead. Borrowing through the mismatch built framebuffers the
        // validation layer rejected (VUID-VkFramebufferCreateInfo-pAttachments-00880) and ANV crashed on.
        if (engine == null
                || !PresentEngine.formatAllowsBorrow(engine.swapchainFormat(), this.colorFormat)) {
            return false;
        }
        if (!ensurePresentAcquirePool(engine.imageCount())) {
            return false;
        }
        int slot = VulkanSwapchain.freeAcquireSlot(this.presentAcquireOwnerImage,
                this.presentAcquireCursor);
        if (slot < 0 && growPresentAcquirePool()) {
            slot = this.presentAcquireSemaphores.length - 1;
        }
        if (slot < 0) {
            return false;
        }
        this.presentAcquireCursor = slot + 1;
        long acquireSemaphore = this.presentAcquireSemaphores[slot];
        PresentEngine.BorrowedImage borrowed = engine.acquireNextImage(acquireSemaphore, frameSlot);
        if (borrowed == null) {
            // A failed acquire may leave its semaphore signaled (the spec allows it), and the slot
            // would hand that pending signal to a later acquire: replace the semaphore, the owned
            // pool's replaceAcquireSemaphore discipline. The slot stays unowned.
            replacePresentAcquireSemaphore(slot);
            return false;
        }
        VulkanSwapchain.assignAcquireOwner(this.presentAcquireOwnerImage, slot, borrowed.index());
        this.ownedColorImageLayout = this.colorImageLayout;
        this.presentBorrow = borrowed;
        this.colorImageLayout = borrowed.initialLayout();
        this.presentAcquireSemaphoreUsed = acquireSemaphore;
        this.presentAcquireWaitPending = true;
        return true;
    }

    /** {@return whether a swapchain borrow is live and still valid}. */
    public boolean hasLivePresentBorrow() {
        return this.presentBorrow != null && this.presentBorrow.isValid();
    }

    @Override
    public PresentEngine.BorrowedImage borrowedPresentImage() {
        return this.presentBorrow;
    }

    /**
     * Consumes one orphaned borrow acquire signal with a submission that only waits on it, the shape
     * {@code consumeDone} uses for the bridge's done semaphore. The signal has already fired (the image
     * was acquired), so the wait completes without touching any image.
     */
    private void drainUnconsumedAcquire(long semaphore) {
        try (MemoryStack stack = stackPush()) {
            LongBuffer wait = stack.mallocLong(1);
            wait.put(0, semaphore);
            this.context.celtium$flushBatchedSubmits();
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                    .waitSemaphoreCount(wait.remaining()).pWaitSemaphores(wait)
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT));
            VulkanContext.queueSubmitCalls++;
            check(vkQueueSubmit(this.context.queue(), submit, VK_NULL_HANDLE),
                    "vkQueueSubmit (borrow acquire drain)");
        }
    }

    @Override
    public void releasePresentBorrow() {
        if (this.presentBorrow == null) {
            return;
        }
        this.presentBorrow = null;
        if (this.presentAcquireWaitPending) {
            // The borrow ends with its acquire never consumed: no submission waited the signal and no
            // present took it (a dropped frame, or a borrow the present dropped when its generation
            // retired). Clearing the bookkeeping without consuming the signal orphans it: ownership
            // alone cannot free this slot, because the ownership release rule - the image being
            // acquired again - proves a previous present was consumed, and a dropped borrow has no
            // present. Drain the signal here, at the one point every borrow release passes, and give
            // the slot back: the drain is the wait that makes the semaphore reusable.
            drainUnconsumedAcquire(this.presentAcquireSemaphoreUsed);
            for (int slot = 0; slot < this.presentAcquireSemaphores.length; slot++) {
                if (this.presentAcquireSemaphores[slot] == this.presentAcquireSemaphoreUsed) {
                    this.presentAcquireOwnerImage[slot] = -1L;
                }
            }
        }
        this.presentAcquireWaitPending = false;
        this.presentAcquireSemaphoreUsed = VK_NULL_HANDLE;
        // The tracked layout goes back to describing the owned image, which nothing touched while the
        // borrow was live.
        this.colorImageLayout = this.ownedColorImageLayout;
    }

    @Override
    public long presentAcquireSemaphore() {
        if (!this.presentAcquireWaitPending) {
            return VK_NULL_HANDLE;
        }
        this.presentAcquireWaitPending = false;
        return this.presentAcquireSemaphoreUsed;
    }

    /**
     * Sizes the acquire semaphore pool to the lender's image count. A pool of the previous size is retired
     * through the retire queue rather than destroyed in place: a replaced semaphore can still be the one a
     * queued submission waits on, the same hazard the swapchain's own pool replacement guards against.
     *
     * @return whether a usable pool exists
     */
    private boolean ensurePresentAcquirePool(int imageCount) {
        if (imageCount <= 0) {
            return false;
        }
        if (this.presentAcquireSemaphores.length == imageCount) {
            return true;
        }
        VkDevice device = this.context.device();
        long[] created = new long[imageCount];
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkSemaphoreCreateInfo semaphoreInfo = org.lwjgl.vulkan.VkSemaphoreCreateInfo
                    .calloc(stack).sType$Default();
            LongBuffer pointer = stack.mallocLong(1);
            for (int i = 0; i < imageCount; i++) {
                int result = org.lwjgl.vulkan.VK10.vkCreateSemaphore(device, semaphoreInfo, null, pointer);
                if (result != VK_SUCCESS) {
                    for (int j = 0; j < i; j++) {
                        org.lwjgl.vulkan.VK10.vkDestroySemaphore(device, created[j], null);
                    }
                    return false;
                }
                created[i] = pointer.get(0);
            }
        }
        long[] replaced = this.presentAcquireSemaphores;
        this.presentAcquireSemaphores = created;
        this.presentAcquireOwnerImage = new long[imageCount];
        java.util.Arrays.fill(this.presentAcquireOwnerImage, -1L);
        this.presentAcquireCursor = 0;
        if (replaced.length > 0) {
            final long[] retired = replaced;
            VulkanRetireQueue.retire(() -> {
                for (long semaphore : retired) {
                    org.lwjgl.vulkan.VK10.vkDestroySemaphore(device, semaphore, null);
                }
            });
        }
        return true;
    }

    /** Grows the acquire pool by one semaphore when every slot is owned, with its ownership slot. */
    private boolean growPresentAcquirePool() {
        VkDevice device = this.context.device();
        long created;
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkSemaphoreCreateInfo semaphoreInfo = org.lwjgl.vulkan.VkSemaphoreCreateInfo
                    .calloc(stack).sType$Default();
            LongBuffer pointer = stack.mallocLong(1);
            if (org.lwjgl.vulkan.VK10.vkCreateSemaphore(device, semaphoreInfo, null, pointer)
                    != VK_SUCCESS) {
                return false;
            }
            created = pointer.get(0);
        }
        long[] pool = new long[this.presentAcquireSemaphores.length + 1];
        System.arraycopy(this.presentAcquireSemaphores, 0, pool, 0,
                this.presentAcquireSemaphores.length);
        pool[pool.length - 1] = created;
        this.presentAcquireSemaphores = pool;
        long[] owners = new long[pool.length];
        java.util.Arrays.fill(owners, -1L);
        System.arraycopy(this.presentAcquireOwnerImage, 0, owners, 0,
                this.presentAcquireOwnerImage.length);
        this.presentAcquireOwnerImage = owners;
        return true;
    }

    /**
     * Replaces one pool slot's semaphore with a fresh one, retiring the old through the retire queue:
     * a pending wait may still name it. Used after a failed acquire, which may leave its semaphore
     * signaled. If the replacement cannot be created the old semaphore stays - a degraded pool beats
     * an empty slot.
     */
    private void replacePresentAcquireSemaphore(int slot) {
        VkDevice device = this.context.device();
        long created;
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkSemaphoreCreateInfo semaphoreInfo = org.lwjgl.vulkan.VkSemaphoreCreateInfo
                    .calloc(stack).sType$Default();
            LongBuffer pointer = stack.mallocLong(1);
            if (org.lwjgl.vulkan.VK10.vkCreateSemaphore(device, semaphoreInfo, null, pointer)
                    != VK_SUCCESS) {
                return;
            }
            created = pointer.get(0);
        }
        final long retired = this.presentAcquireSemaphores[slot];
        this.presentAcquireSemaphores[slot] = created;
        VulkanRetireQueue.retire(() -> org.lwjgl.vulkan.VK10.vkDestroySemaphore(device, retired, null));
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
        snapshot.gameClearLatched = this.gameClearLatched;
        snapshot.gameClearLatchedMask = this.gameClearLatchedMask;
        snapshot.latchedRed = this.gameClearLatchedRed;
        snapshot.latchedGreen = this.gameClearLatchedGreen;
        snapshot.latchedBlue = this.gameClearLatchedBlue;
        snapshot.latchedAlpha = this.gameClearLatchedAlpha;
        snapshot.latchedDepth = this.gameClearLatchedDepth;
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
        it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<Pair>> it =
                this.chainPairs.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<Pair> entry = it.next();
            Pair pair = entry.getValue();
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
        this.gameClearLatched = snapshot.gameClearLatched;
        this.gameClearLatchedMask = snapshot.gameClearLatchedMask;
        this.gameClearLatchedRed = snapshot.latchedRed;
        this.gameClearLatchedGreen = snapshot.latchedGreen;
        this.gameClearLatchedBlue = snapshot.latchedBlue;
        this.gameClearLatchedAlpha = snapshot.latchedAlpha;
        this.gameClearLatchedDepth = snapshot.latchedDepth;
        if (snapshot.glsmSnapshot && this.glsmFrameState != null) {
            this.glsmFrameState.clearState().restore(this.glsmFrameIdentity.getAsLong(),
                    snapshot.glsmLatched);
            this.glsmFrameState.clearState().restoreAppliedTotal(snapshot.glsmAppliedTotal);
        }
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
        boolean latched = stateLatched != null || this.gameClearLatched;
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
                clears = this.gameClearLatchedMask;
                latchedRed = this.gameClearLatchedRed;
                latchedGreen = this.gameClearLatchedGreen;
                latchedBlue = this.gameClearLatchedBlue;
                latchedAlpha = this.gameClearLatchedAlpha;
                latchedDepth = this.gameClearLatchedDepth;
                this.gameClearLatched = false;
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
                org.lwjgl.vulkan.VK10.vkCmdClearColorImage(command, colorImage(),
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
     * images of one frame, the same shape {@code Pair.toTransfer} already fuses for the pair clear. They used to be
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
                barrier.sType$Default().image(colorImage())
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
            DrawCounters.notePipelineBarrier();
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
     * The pure half of {@code Pair.toTransfer}, package-private and static so the rule is pinned without a device. The
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
                barrier.sType$Default().image(colorImage())
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
            DrawCounters.notePipelineBarrier();
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
                DrawCounters.notePipelineBarrier();
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
                    .image(colorImage())
                    // The colour image's true layout is whatever the last phase left it in - acquire, release or the
                    // present's own transition - so it is tracked rather than assumed, the way the present path
                    // reports the layout it restored through noteColorImageLayout. The image named is the one
                    // colorImage() answers for: while a borrow is live that is the borrowed swapchain image,
                    // and the tracked layout describes it too (see the field's note). Naming the owned image
                    // here instead moved an image nothing was drawing into while the borrowed image kept the
                    // layout the lender handed it in, which the sampling draws then reported as 09600.
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

            DrawCounters.notePipelineBarrier();
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

    /**
     * One depth image shared by every chain pair whose framebuffer attaches the same depth texture.
     * <p>
     * GL keys depth storage by the texture: Iris builds its whole shadow framebuffer family over one
     * mainDepth texture, and the pre-translucent depth copy moves that storage into a second depth texture.
     * A pair's depth is private to its framebuffer identity, so the shadow depth used to fragment across the
     * family's pairs and no single image was "the shadow depth" — the copy out of it was refused as not owned
     * and the depth samplers resolved to empty mirrors. The store is the texture-keyed storage GL describes:
     * one image per depth-texture identity ({@code RenderTargetRegistry.rawColorTextureIdentity}), allocated
     * with the pair depth's own allocation (device-chosen format, attachment | transfer | sampled usage), its
     * layout recorded here because every pair that borrows it must agree on where it is.
     */
    static final class SharedDepth {
        private long image;
        private long memory;
        private long view;
        private int width;
        private int height;
        private int layout = VK_IMAGE_LAYOUT_UNDEFINED;
        /**
         * The frame serial this image was last cleared in, or -1 when it has not been cleared yet.
         * <p>
         * A GL shadow family is several framebuffers attaching one depth texture, so a clear of one identity's pair
         * is a clear of every family member's depth. The identity-keyed once-per-frame clear rule cannot see that -
         * it asks about the pair, and each family member has its own - so a second identity whose pair has just been
         * created (not acquired yet) clears the family's shadow depth in the middle of the frame. This record is the
         * depth-keyed half of that rule; see {@link #celtium$sharedDepthClearNeeded(long, long, long)}.
         */
        private long clearedFrameSerial = -1L;
    }

    /** Shared depth images by depth-texture identity; see {@link SharedDepth}. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<SharedDepth> sharedDepths =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    /**
     * Makes the shared depth image for {@code depthKey} exist at the requested extent and returns its
     * {@code {image, memory, view}} triple. A live entry at the same extent is returned as it stands; a
     * different extent retires the old image (through the retire queue — a pair may still be recorded against
     * it) and retires every pair that borrowed it, so no framebuffer keeps a view of the replaced image.
     */
    private long[] ensureSharedDepth(long depthKey, int width, int height) {
        checkAlive();
        SharedDepth existing = this.sharedDepths.get(depthKey);
        if (existing != null && existing.width == width && existing.height == height) {
            return new long[] {existing.image, existing.memory, existing.view};
        }
        if (existing != null) {
            retireSharedDepthHandles(existing);
            this.sharedDepths.remove(depthKey);
            retirePairsBorrowing(depthKey);
        }
        long[] allocated = allocateImage(false, width, height, TargetFormatClass.RGBA8_UNORM);
        SharedDepth created = new SharedDepth();
        created.image = allocated[0];
        created.memory = allocated[1];
        created.view = allocated[2];
        created.width = width;
        created.height = height;
        this.sharedDepths.put(depthKey, created);
        return allocated;
    }

    /** Hands a shared depth entry's handles to the retire queue and forgets them in the entry. */
    private void retireSharedDepthHandles(SharedDepth entry) {
        VkDevice device = this.context.device();
        final long view = entry.view;
        final long image = entry.image;
        final long memory = entry.memory;
        entry.view = VK_NULL_HANDLE;
        entry.image = VK_NULL_HANDLE;
        entry.memory = VK_NULL_HANDLE;
        entry.layout = VK_IMAGE_LAYOUT_UNDEFINED;
        if (view != VK_NULL_HANDLE) {
            VulkanRetireQueue.retire(device.address(), () -> vkDestroyImageView(device, view, null));
        }
        if (image != VK_NULL_HANDLE) {
            VulkanRetireQueue.retire(device.address(), () -> vkDestroyImage(device, image, null));
        }
        if (memory != VK_NULL_HANDLE) {
            VulkanRetireQueue.retire(device.address(), () -> VulkanContext.freeMemory(device, memory));
        }
    }

    /** Retires every chain pair that borrowed the depth under {@code depthKey}; they re-ensure on next use. */
    private void retirePairsBorrowing(long depthKey) {
        java.util.List<Long> borrowing = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, Pair> entry : this.chainPairs.entrySet()) {
            if (entry.getValue().sharedDepthKey == depthKey) {
                borrowing.add(entry.getKey());
            }
        }
        for (long identity : borrowing) {
            retireChainPair(identity);
        }
    }

    /**
     * Forgets the shared depth image for {@code depthKey} (its GL texture was deleted): the entry's handles
     * retire and every pair that borrowed it retires with it.
     */
    public void forgetSharedDepth(long depthKey) {
        SharedDepth existing = this.sharedDepths.remove(depthKey);
        if (existing != null) {
            retireSharedDepthHandles(existing);
        }
        retirePairsBorrowing(depthKey);
    }

    /** The shared depth image for {@code depthKey}, or 0 when none exists. */
    public long sharedDepthImage(long depthKey) {
        SharedDepth entry = this.sharedDepths.get(depthKey);
        return entry == null ? 0L : entry.image;
    }

    /** The shared depth image view for {@code depthKey}, or 0 when none exists. */
    public long sharedDepthView(long depthKey) {
        SharedDepth entry = this.sharedDepths.get(depthKey);
        return entry == null ? 0L : entry.view;
    }

    /** {@return the shared depth image's extent as {width, height}}, or null when none exists. */
    public int[] sharedDepthExtent(long depthKey) {
        SharedDepth entry = this.sharedDepths.get(depthKey);
        return entry == null ? null : new int[] {entry.width, entry.height};
    }

    /** {@return where the shared depth image for {@code depthKey} is}, UNDEFINED when none exists. */
    public int sharedDepthLayout(long depthKey) {
        SharedDepth entry = this.sharedDepths.get(depthKey);
        return entry == null ? VK_IMAGE_LAYOUT_UNDEFINED : entry.layout;
    }

    /** Records where the shared depth image for {@code depthKey} has been moved to, by whoever moved it. */
    public void noteSharedDepthLayout(long depthKey, int layout) {
        SharedDepth entry = this.sharedDepths.get(depthKey);
        if (entry != null) {
            entry.layout = layout;
        }
    }

    /**
     * The same record as {@link #noteSharedDepthLayout}, skipped when the store already names {@code layout}.
     *
     * @return whether the store's record changed
     */
    public boolean noteSharedDepthLayoutIfChanged(long depthKey, int layout) {
        SharedDepth entry = this.sharedDepths.get(depthKey);
        if (entry == null || entry.layout == layout) {
            return false;
        }
        entry.layout = layout;
        return true;
    }

    /** {@return the frame serial the shared depth image for {@code depthKey} was last cleared in}, -1 when never. */
    public long sharedDepthClearedFrameSerial(long depthKey) {
        SharedDepth entry = this.sharedDepths.get(depthKey);
        return entry == null ? -1L : entry.clearedFrameSerial;
    }

    /** Records that the shared depth image for {@code depthKey} was cleared in {@code frameSerial}. */
    public void noteSharedDepthCleared(long depthKey, long frameSerial) {
        SharedDepth entry = this.sharedDepths.get(depthKey);
        if (entry != null) {
            entry.clearedFrameSerial = frameSerial;
        }
    }

    /**
     * {@return whether a pair that renders into the shared depth image for {@code depthKey} may clear its depth in
     * {@code frameSerial}}.
     * <p>
     * Once per frame per depth image, not once per pair: the shadow family's framebuffers share one depth texture
     * (GL's own semantics - the game's {@code glClear} on any of them clears that texture), and every member
     * borrowing it here means the first member's clear is enough for the frame. Without this a member whose pair was
     * created later in the same frame clears the depth the earlier member's pass already wrote, and the copy that
     * follows reads a cleared image.
     * <p>
     * Package-private and static so the rule is pinned without a device. A {@code depthKey} of 0 means "no shared
     * depth" and answers {@code false}: there is nothing to guard, and the caller only asks when the pair borrows one.
     */
    static boolean celtium$sharedDepthClearNeeded(long depthKey, long clearedFrameSerial, long frameSerial) {
        return depthKey != 0L && clearedFrameSerial != frameSerial;
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

    /** {@return how many shared depth images the store holds}. */
    public int sharedDepthCount() {
        return this.sharedDepths.size();
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
        Pair pair = existingChainPair(identity);
        if (pair == null || !pair.exists() || depthWidth <= 0 || depthHeight <= 0) {
            return false;
        }
        if (pair.width != depthWidth || pair.height != depthHeight) {
            return false;
        }
        long[] shared = ensureSharedDepth(depthKey, depthWidth, depthHeight);
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
        Pair pair = existingChainPair(identity);
        return pair != null && pair.sharedDepthKey == depthKey && pair.depthImage != 0L
                && pair.depthImage == sharedDepthImage(depthKey);
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
     * Makes the identity's chain pair exist at the requested size in {@code formatClass} (QUEUE 404, S3): the
     * pair is rebuilt when its live class differs, through the same retire path a resize takes. This is the
     * one ensure that sets a pair's class, and a pair's class is written in exactly one of two places: the
     * registration junction, which resolves a raw pair's class from its GL request, and the off-target
     * window path, which passes the resolved class for pairs no junction resolves (a ShaderGroup group
     * pair has no GL request; see {@code VulkanTerrainGpuBackend.celtium$pairClassForWindow}). Every
     * consumer only follows the pair's recorded class and never re-reads a request (rule R5).
     */
    public void ensureChainPair(long identity, int width, int height, TargetFormatClass formatClass) {
        Pair pair = chainPairFor(identity);
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
        Pair pair = existingChainPair(identity);
        return pair == null ? TargetFormatClass.RGBA8_UNORM : pair.formatClass;
    }

    /**
     * {@return whether {@code image} is a colour image of a chain pair allocated in a format class the
     * 4-byte readback cannot expand}. The readback path asks this because it expands 4 bytes per pixel: a
     * 16F pair's image read that way is half read and wrong, not merely imprecise (QUEUE 404 rule R10).
     * Both 8-bit UNORM classes are readable - RGBA8 and, since Stage 1's BGRA ignition, BGRA8, whose byte
     * order the readback answers for - so "non-default" is no longer the question; readability is.
     */
    public boolean chainPairImageHasUnreadableFormat(long image) {
        long identity = chainPairIdentityForImage(image);
        if (identity == Long.MIN_VALUE) {
            return false;
        }
        TargetFormatClass formatClass = chainPairFormatClass(identity);
        return formatClass != TargetFormatClass.RGBA8_UNORM
                && formatClass != TargetFormatClass.BGRA8_UNORM;
    }

    /**
     * Makes the identity's chain pair exist at the requested size, holding colour images for {@code attachments}
     * colour targets: the pair itself first (its ensure retires any extras at the old size), then the tuple.
     * The owners map learns the extra images here, so the sampling side's membership questions
     * ({@link #isChainPairColorImage}, {@link #chainPairIdentityForImage}) cover every slot, not only slot 0.
     */
    public void ensureChainPairColors(long identity, int attachments, int width, int height) {
        // The class-preserving form: the pair keeps the class the registration junction resolved for it
        // (rule R5). A pair nobody resolved a class for is ensured in RGBA8_UNORM explicitly: the frame's
        // BGRA class enters through the pin (identity 0 / main) only, never through an unresolved pair.
        Pair existing = existingChainPair(identity);
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
        Pair pair = chainPairFor(identity);
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
        Pair pair = existingChainPair(identity);
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
        Pair pair = existingChainPair(identity);
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
        Pair pair = existingChainPair(identity);
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
                DrawCounters.notePipelineBarrier();
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
                DrawCounters.notePipelineBarrier();
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
        Pair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.colorImageAt(slot);
    }

    /** The colour view at a slot of the identity's chain pair (0 = the pair's own), or 0 when it holds none. */
    public long chainPairColorViewAt(long identity, int slot) {
        checkAlive();
        Pair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.colorViewAt(slot);
    }

    /**
     * {@return which tuple images the identity's chain pair holds now}, 0 before any are built. Changes whenever
     * the extras are built or dropped, at any count: a framebuffer over the previous views names dead images.
     */
    public long chainPairColorTupleGeneration(long identity) {
        Pair pair = existingChainPair(identity);
        return pair == null ? 0L : pair.extraGeneration;
    }

    /**
     * Records the acquire the identity's chain pair needs before a render pass attaches it for draws, outside any
     * clear: the colour moves from wherever it is to {@code GENERAL} and the depth - borrowed from the shared store or
     * the pair's own - moves to the attachment layout the frame pass declares.
     * <p>
     * Distinct from {@link #clearChainPair} and {@link Pair#deferClear}: those are the clear's own entry points and
     * leave the depth in the clear's transfer layout, because a {@code vkCmdClearDepthStencilImage} needs it there. A
     * pass that draws into the pair without clearing it first (item 332's routed terrain pass) has to arrive at the
     * attachment layout directly, and must not discard what another pair sharing the same depth image already wrote -
     * so the barrier's old layout is the store's record rather than {@code UNDEFINED}.
     *
     * @param depthKey the shared depth store key this pair borrows, 0 when its depth is private
     */
    public void recordChainPairAttachAcquire(long identity, long depthKey, VkCommandBuffer command) {
        checkAlive();
        Pair pair = existingChainPair(identity);
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
        Pair pair = existingChainPair(identity);
        if (pair != null && pair.exists()) {
            pair.noteColorLayout(COLOR_RENDER_LAYOUT);
        }
        if (depthKey != 0L) {
            noteSharedDepthLayout(depthKey, DEPTH_RENDER_LAYOUT);
        }
    }

    /** Records the acquire barriers the identity's tuple images need before a pass attaches them. */
    public void recordChainPairColorAcquire(long identity, VkCommandBuffer command) {
        checkAlive();
        Pair pair = existingChainPair(identity);
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
        Pair src = existingChainPair(srcIdentity);
        Pair dst = existingChainPair(dstIdentity);
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
            DrawCounters.notePipelineBarrier();
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
            DrawCounters.notePipelineBarrier();
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
     * record - held by the shared store, see {@code Pair.depthLayout()} - is restored by the post barrier,
     * so the copy leaves the sharing pairs' beliefs untouched.
     *
     * @return whether the copy was recorded
     */
    public boolean copyChainPairDepth(long srcIdentity, long dstIdentity, VkCommandBuffer command) {
        checkAlive();
        if (command == null || command.address() == 0L || srcIdentity == dstIdentity) {
            return false;
        }
        Pair src = existingChainPair(srcIdentity);
        Pair dst = existingChainPair(dstIdentity);
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
            DrawCounters.notePipelineBarrier();
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
            DrawCounters.notePipelineBarrier();
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
        Pair pair = existingChainPair(identity);
        if (pair != null) {
            pair.noteExtraColorLayout(slot, layout);
        }
    }

    /** {@return the slot an image fills in the identity's chain pair}, or -1 when it fills none. */
    public int chainPairColorSlotForImage(long identity, long image) {
        Pair pair = existingChainPair(identity);
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
        Pair pair = existingChainPair(identity);
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
     * {@return whether a pair clear is recorded inside the pass that follows it}.
     * <p>
     * The rule lives on {@code Pair} because that is the class whose clear it changes; this accessor is what the
     * backend and the test read, because {@code Pair} is private to this class.
     */
    static boolean celtium$pairClearInPass() {
        return Pair.PAIR_CLEAR_IN_PASS;
    }

    /**
     * {@return whether a deferred clear has to move the depth to the attachment layout first}.
     * <p>
     * Package-private and static so the rule is pinned without a device; see
     * {@link Pair#celtium$deferredClearNeedsDepthBarrier(int)}.
     */
    static boolean celtium$deferredClearNeedsDepthBarrier(int depthLayout) {
        return Pair.celtium$deferredClearNeedsDepthBarrier(depthLayout);
    }

    /**
     * {@return the deferred clear's extent along one axis, clamped to the attachment}; see
     * {@link Pair#celtium$deferredClearExtent(int, int)}.
     */
    static int celtium$deferredClearExtent(int passExtent, int pairExtent) {
        return Pair.celtium$deferredClearExtent(passExtent, pairExtent);
    }

    /**
     * {@return whether a pair's colour tuple must be rebuilt for a draw into {@code attachments} targets};
     * package-private and static so the rule is pinned without a device; see
     * {@link Pair#celtium$extrasNeedRebuild(int, int)}.
     */
    static boolean celtium$extrasNeedRebuild(int liveExtraCount, int attachments) {
        return Pair.celtium$extrasNeedRebuild(liveExtraCount, attachments);
    }

    /**
     * {@return whether a pair must be rebuilt for an ensure}; package-private and static so the rule is pinned
     * without a device; see {@link Pair#celtium$pairRebuildNeeded(boolean, boolean, TargetFormatClass,
     * TargetFormatClass)}.
     */
    static boolean celtium$pairRebuildNeeded(boolean sizeMatches, boolean sharedMatches,
            TargetFormatClass liveClass, TargetFormatClass wantedClass) {
        return Pair.celtium$pairRebuildNeeded(sizeMatches, sharedMatches, liveClass, wantedClass);
    }

    /**
     * Defers the identity's chain-pair clear to the pass that follows it; see {@link Pair#PAIR_CLEAR_IN_PASS}.
     *
     * @return whether a pair existed and the clear was deferred
     */
    public boolean deferChainPairClear(long identity, VkCommandBuffer command) {
        Pair pair = existingChainPair(identity);
        if (pair == null) {
            return false;
        }
        pair.deferClear(command);
        return true;
    }

    /** {@return whether the identity's chain pair is waiting for a deferred clear}. */
    public boolean chainPairHasDeferredClear(long identity) {
        Pair pair = existingChainPair(identity);
        return pair != null && pair.hasDeferredClear();
    }

    /**
     * Records the identity's deferred chain-pair clear inside the pass that has just begun.
     *
     * @return whether a clear was waiting
     */
    public boolean recordChainPairDeferredClear(long identity, VkCommandBuffer command, int width, int height) {
        Pair pair = existingChainPair(identity);
        return pair != null && pair.recordDeferredClearAttachments(command, width, height);
    }

    /**
     * The deferred clear for a pass that attaches {@code colorAttachments} colour targets; see
     * {@link Pair#recordDeferredClearAttachments(VkCommandBuffer, int, int, int)}.
     *
     * @return whether a clear was waiting
     */
    public boolean recordChainPairDeferredClear(long identity, VkCommandBuffer command, int width, int height,
            int colorAttachments) {
        Pair pair = existingChainPair(identity);
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

    /** {@return how many chain-pair image sets have been created}, the cache key for name-to-image resolutions. */
    public long chainPairGenerationTotal() {
        return this.chainPairGenerations;
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

    /** Zero-allocation width getter for hot-path off-target windows; returns 0 before the pair exists. */
    public int chainPairWidth(long identity) {
        Pair pair = existingChainPair(identity);
        return pair == null || !pair.exists() ? 0 : pair.width;
    }

    /** Zero-allocation height getter for hot-path off-target windows; returns 0 before the pair exists. */
    public int chainPairHeight(long identity) {
        Pair pair = existingChainPair(identity);
        return pair == null || !pair.exists() ? 0 : pair.height;
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
        /**
         * Extra colour images for attachment slots 1.. of a multi-target framebuffer (index = slot - 1), with
         * their memories, views and recorded layouts. Null until the pair is asked to hold more than one colour
         * target; the pair's own colour image above is always slot 0. The extras live and die with the pair's
         * current size: {@link #ensure} retires them with the rest, and the caller re-asks for the count it needs
         * after every ensure, which is what keeps a resized pair from holding images at the old extent.
         */
        private long[] extraColorImages;
        private long[] extraColorMemories;
        private long[] extraColorViews;
        private int[] extraColorLayouts;
        private int extraColorCount;
        /** Bumps whenever the extras are built or dropped, so a framebuffer cache over their views can key on it. */
        private long extraGeneration;
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
        /**
         * The format class this pair's colour images are allocated in (QUEUE 404, S3). An attribute of the
         * pair, not of its slot: it is fixed for the pair's current images, and a different wanted class is a
         * rebuild, exactly like a different size (rule R3). A pair starts in the frame's own class - the
         * default pair is identity 0's pair and identity 0 is pinned to the frame (rule R1) - and a chain
         * pair's first ensure replaces it with the class its identity resolved, which is never the frame's
         * BGRA class: BGRA enters only through the pin.
         */
        private TargetFormatClass formatClass;
        private int creations;
        /** Unique across pairs and evictions: which images a framebuffer built over this pair's views names. */
        private long generation;
        private int clears;
        private int selfTests;

        private Pair(String label) {
            this.label = label;
            this.formatClass = GllessFrameTarget.this.frameFormatClass;
        }

        /** {@return whether {@code image} is one of this pair's colour images (slot 0 or an extra)}. */
        private boolean ownsColorImage(long image) {
            if (image == 0L) {
                return false;
            }
            if (image == this.colorImage) {
                return true;
            }
            if (this.extraColorImages != null) {
                for (int i = 0; i < this.extraColorCount; i++) {
                    if (this.extraColorImages[i] == image) {
                        return true;
                    }
                }
            }
            return false;
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

        /** {@return whether this pair's colour images are allocated in {@code wanted}} (QUEUE 404's named rule). */
        private boolean matchesFormat(TargetFormatClass wanted) {
            return this.formatClass == wanted;
        }

        /**
         * {@return whether a pair must be rebuilt for an ensure}: the pure form of {@link #ensure}'s early
         * return, so the rule is pinned without a device. A live pair is kept only when the size matches, the
         * shared-depth borrow is the one asked for, and the format class is the one asked for - a class change
         * retires and recreates the pair, as a size change does (rule R3), because the existing framebuffers
         * and render passes name the old format.
         */
        static boolean celtium$pairRebuildNeeded(boolean sizeMatches, boolean sharedMatches,
                TargetFormatClass liveClass, TargetFormatClass wantedClass) {
            return !(sizeMatches && sharedMatches && liveClass == wantedClass);
        }

        /** {@return whether the images this pair holds now have not had their one legal-use self-test yet}. */
        private boolean needsSelfTest() {
            return exists() && this.selfTests < this.creations;
        }

        /**
         * {@return whether the live extras are not the ones a draw into {@code attachments} colour targets needs}.
         * Pure and static so the rule is pinned without a device: the extras are exactly slots 1..attachments-1,
         * and their extent is the pair's own (a resize retires them, so a live set is never at a stale size).
         */
        static boolean celtium$extrasNeedRebuild(int liveExtraCount, int attachments) {
            return liveExtraCount != Math.max(0, attachments - 1);
        }

        /** {@return how many colour targets this pair currently holds images for} (1 = the pair's own only). */
        private int colorCount() {
            return exists() ? 1 + this.extraColorCount : 0;
        }

        /** The colour image at a slot of this pair (0 = its own), or 0 when that slot holds none. */
        private long colorImageAt(int slot) {
            if (slot == 0) {
                return this.colorImage;
            }
            return slot > 0 && slot - 1 < this.extraColorCount ? this.extraColorImages[slot - 1] : 0L;
        }

        /** The colour view at a slot of this pair (0 = its own), or 0 when that slot holds none. */
        private long colorViewAt(int slot) {
            if (slot == 0) {
                return this.colorView;
            }
            return slot > 0 && slot - 1 < this.extraColorCount ? this.extraColorViews[slot - 1] : 0L;
        }

        /** {@return the slot an image fills in this pair} (0 = its own colour image), or -1 when it fills none. */
        private int colorSlotForImage(long image) {
            if (image == 0L) {
                return -1;
            }
            if (image == this.colorImage) {
                return 0;
            }
            for (int i = 0; i < this.extraColorCount; i++) {
                if (this.extraColorImages[i] == image) {
                    return i + 1;
                }
            }
            return -1;
        }

        /** {@return a copy of the live extra colour images} (slots 1..), or null when there are none. */
        private long[] extraColorImageSnapshot() {
            if (this.extraColorCount == 0) {
                return null;
            }
            long[] snapshot = new long[this.extraColorCount];
            System.arraycopy(this.extraColorImages, 0, snapshot, 0, this.extraColorCount);
            return snapshot;
        }

        /**
         * Makes this pair hold colour images for {@code attachments} colour targets at its current size, building
         * the extras on first ask and rebuilding them when the count changes. Asking for 1 drops the extras.
         * A pair that does not exist yet is left alone - the extras belong to a pair, and {@link #ensure} is what
         * creates one; the window ensures the pair first and the colours second.
         */
        private void ensureExtras(int attachments) {
            checkAlive();
            if (!exists()) {
                return;
            }
            if (!celtium$extrasNeedRebuild(this.extraColorCount, attachments)) {
                return;
            }
            retireExtras();
            if (attachments <= 1) {
                return;
            }
            try {
                int extras = attachments - 1;
                long[] images = new long[extras];
                long[] memories = new long[extras];
                long[] views = new long[extras];
                int[] layouts = new int[extras];
                for (int i = 0; i < extras; i++) {
                    // The tuple's images are the pair's own class (rule R4): one class per tuple, because the
                    // render pass declares one format for every colour attachment.
                    long[] allocated = allocateImage(true, this.width, this.height, this.formatClass);
                    images[i] = allocated[0];
                    memories[i] = allocated[1];
                    views[i] = allocated[2];
                    layouts[i] = VK_IMAGE_LAYOUT_UNDEFINED;
                }
                this.extraColorImages = images;
                this.extraColorMemories = memories;
                this.extraColorViews = views;
                this.extraColorLayouts = layouts;
                this.extraColorCount = extras;
                this.extraGeneration++;
            } catch (RuntimeException | LinkageError error) {
                // No half tuple is left behind, as in ensure(): whatever was adopted is released in place because
                // nothing has recorded against it yet.
                releaseExtras();
                throw error;
            }
        }

        /**
         * Hands the extras to {@link VulkanRetireQueue}, like {@link #retire()} does for the pair's own images:
         * a rebuild runs mid-session, while a command buffer that recorded against these views may still be open.
         */
        private void retireExtras() {
            if (this.extraColorCount == 0) {
                return;
            }
            VkDevice device = context.device();
            final long[] images = this.extraColorImages;
            final long[] memories = this.extraColorMemories;
            final long[] views = this.extraColorViews;
            final int count = this.extraColorCount;
            this.extraColorImages = null;
            this.extraColorMemories = null;
            this.extraColorViews = null;
            this.extraColorLayouts = null;
            this.extraColorCount = 0;
            this.extraGeneration++;
            for (int i = 0; i < count; i++) {
                final long view = views[i];
                final long image = images[i];
                final long memory = memories[i];
                if (view != VK_NULL_HANDLE) {
                    VulkanRetireQueue.retire(device.address(), () -> vkDestroyImageView(device, view, null));
                }
                if (image != VK_NULL_HANDLE) {
                    VulkanRetireQueue.retire(device.address(), () -> vkDestroyImage(device, image, null));
                }
                if (memory != VK_NULL_HANDLE) {
                    VulkanRetireQueue.retire(device.address(), () -> VulkanContext.freeMemory(device, memory));
                }
            }
        }

        /** Destroys the extras in place; only for images no command buffer can still name (see {@link #release()}). */
        private void releaseExtras() {
            if (this.extraColorImages == null) {
                this.extraColorCount = 0;
                return;
            }
            VkDevice device = context.device();
            for (int i = 0; i < this.extraColorCount; i++) {
                if (this.extraColorViews[i] != VK_NULL_HANDLE) {
                    vkDestroyImageView(device, this.extraColorViews[i], null);
                }
                if (this.extraColorImages[i] != VK_NULL_HANDLE) {
                    vkDestroyImage(device, this.extraColorImages[i], null);
                }
                if (this.extraColorMemories[i] != VK_NULL_HANDLE) {
                    VulkanContext.freeMemory(device, this.extraColorMemories[i]);
                }
            }
            this.extraColorImages = null;
            this.extraColorMemories = null;
            this.extraColorViews = null;
            this.extraColorLayouts = null;
            this.extraColorCount = 0;
        }

        /** The recorded layout of an extra colour slot, UNDEFINED when the slot holds none. */
        private int extraColorLayoutAt(int slot) {
            return slot > 0 && slot - 1 < this.extraColorCount && this.extraColorLayouts != null
                    ? this.extraColorLayouts[slot - 1] : VK_IMAGE_LAYOUT_UNDEFINED;
        }

        /** Records a layout an extra colour image has been moved to, by whoever moved it (the sampling side). */
        private void noteExtraColorLayout(int slot, int layout) {
            if (slot > 0 && slot - 1 < this.extraColorCount) {
                this.extraColorLayouts[slot - 1] = layout;
            }
        }

        /**
         * Moves every extra colour image that is not in {@code GENERAL} there, in one barrier call: the move a
         * pass that attaches them requires first, because the pass declares {@code GENERAL} as their initial
         * layout. An extra the sampling side left read-only comes back the same way - its recorded layout is the
         * barrier's source, which is why the sampling side reports its moves through
         * {@link #noteExtraColorLayout}.
         */
        private void recordExtrasAcquire(VkCommandBuffer command) {
            if (this.extraColorCount == 0) {
                return;
            }
            int pending = 0;
            for (int i = 0; i < this.extraColorCount; i++) {
                if (this.extraColorLayouts[i] != VK_IMAGE_LAYOUT_GENERAL) {
                    pending++;
                }
            }
            if (pending == 0) {
                return;
            }
            int queueFamily = context.queueFamilyIndex();
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(pending, stack);
                int index = 0;
                for (int i = 0; i < this.extraColorCount; i++) {
                    int from = this.extraColorLayouts[i];
                    if (from == VK_IMAGE_LAYOUT_GENERAL) {
                        continue;
                    }
                    VkImageMemoryBarrier barrier = barriers.get(index++);
                    barrier.sType$Default()
                            .image(this.extraColorImages[i])
                            .oldLayout(from).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                            .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                            .srcAccessMask(from == VK_IMAGE_LAYOUT_UNDEFINED ? 0
                                    : VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                    | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                    barrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                    this.extraColorLayouts[i] = VK_IMAGE_LAYOUT_GENERAL;
                }
                DrawCounters.notePipelineBarrier();
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
            }
        }

        /**
         * Makes the extras' out-of-pass clears visible to the pass that follows, layouts unchanged: the extras are
         * cleared in {@code GENERAL} (not the transfer layout the pair's own colour may use), so this is the
         * transfer-write to attachment dependency {@link #recordClearToAttachment} records for the pair's images,
         * for the extras alone.
         */
        private void recordExtrasClearToAttachment(VkCommandBuffer command) {
            if (this.extraColorCount == 0) {
                return;
            }
            int queueFamily = context.queueFamilyIndex();
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(this.extraColorCount, stack);
                for (int i = 0; i < this.extraColorCount; i++) {
                    VkImageMemoryBarrier barrier = barriers.get(i);
                    barrier.sType$Default()
                            .image(this.extraColorImages[i])
                            .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                            .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                    | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                    barrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                }
                DrawCounters.notePipelineBarrier();
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
            }
        }

        /** Clears every extra colour image with the pair's current clear colour, in {@code GENERAL}. */
        private void clearExtras(VkCommandBuffer command, VkClearColorValue color,
                VkImageSubresourceRange range) {
            if (this.extraColorCount == 0) {
                return;
            }
            recordExtrasAcquire(command);
            range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            for (int i = 0; i < this.extraColorCount; i++) {
                vkCmdClearColorImage(command, this.extraColorImages[i], VK_IMAGE_LAYOUT_GENERAL, color, range);
            }
            recordExtrasClearToAttachment(command);
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
            ensure(requestedWidth, requestedHeight, 0L, null, this.formatClass);
        }

        /**
         * {@link #ensure(int, int)} with a borrowed depth: when {@code sharedDepth} is non-null it is the
         * shared store's {@code {image, memory, view}} triple for {@code sharedKey}, and the pair renders
         * into that depth instead of allocating its own. The store keeps ownership (see {@link #sharedDepthKey});
         * the pair's colour is rebuilt exactly as a resize rebuilds it, so engaging sharing costs one pair
         * recreation, on the first window that asks for it.
         */
        private void ensure(int requestedWidth, int requestedHeight, long sharedKey, long[] sharedDepth) {
            ensure(requestedWidth, requestedHeight, sharedKey, sharedDepth, this.formatClass);
        }

        /**
         * {@link #ensure(int, int, long, long[])} in a wanted format class: the pair is rebuilt when the class
         * differs from the live one, through the same retire path a resize takes (rule R3) - the framebuffers
         * and render passes built over the old views name the old format, and the generation bump in the
         * rebuild is what retires them.
         */
        private void ensure(int requestedWidth, int requestedHeight, long sharedKey, long[] sharedDepth,
                TargetFormatClass wantedClass) {
            checkAlive();
            if (requestedWidth <= 0 || requestedHeight <= 0) {
                throw new IllegalArgumentException(this.label + " pair size must be positive, got "
                        + requestedWidth + "x" + requestedHeight);
            }
            boolean sharedMatches = this.sharedDepthKey == sharedKey
                    && (sharedDepth == null || this.depthImage == sharedDepth[0]);
            if (!celtium$pairRebuildNeeded(matches(requestedWidth, requestedHeight), sharedMatches,
                    this.formatClass, wantedClass)) {
                return;
            }
            retire();
            try {
                long[] color = allocateImage(true, requestedWidth, requestedHeight, wantedClass);
                this.colorImage = color[0];
                this.colorMemory = color[1];
                this.colorView = color[2];
                if (sharedDepth != null) {
                    this.depthImage = sharedDepth[0];
                    this.depthMemory = VK_NULL_HANDLE;
                    this.depthView = sharedDepth[2];
                } else {
                    long[] depth = allocateImage(false, requestedWidth, requestedHeight,
                            TargetFormatClass.RGBA8_UNORM);
                    this.depthImage = depth[0];
                    this.depthMemory = depth[1];
                    this.depthView = depth[2];
                }
                this.width = requestedWidth;
                this.height = requestedHeight;
                this.acquired = false;
                // A pair that was just created holds images nothing has transitioned yet.
                this.colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
                this.creations++;
                this.generation = GllessFrameTarget.this.nextChainPairGeneration();
                // Set with the depth ownership, last: until here the pair must still read as the pair it was,
                // so a failure above leaves release() freeing the old images under the old class.
                this.formatClass = wantedClass;
                // Set last: until here the pair must still read as owning whatever depth release() would free.
                this.sharedDepthKey = sharedDepth != null ? sharedKey : 0L;
            } catch (RuntimeException | LinkageError error) {
                // No half pair is left behind: a retry starts from nothing and delete() still frees whatever exists.
                release();
                throw error;
            }
        }

        /** Reused clear structures; see clear(). */
        private VkImageSubresourceRange clearRange;
        private VkClearColorValue clearColour;
        private VkClearDepthStencilValue clearDepthValue;
        private float[] clearColourFloats;

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
            // A recorded clear supersedes a deferred one: the two are the same clear, and leaving the flag set would
            // make the next pass clear an image it does not attach.
            this.deferredClearPending = false;
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
                // The clear's structures are the same every frame: one range, one colour, one depth value. Building them
                // on the stack per clear put three allocations and a float[4] on a path that runs once per pair per
                // frame; the values still come from GL every time, because a mod may change its clear colour.
                if (this.clearRange == null) {
                    this.clearRange = VkImageSubresourceRange.malloc();
                    // All four fields, not two: malloc memory is not zeroed, and the clear read a garbage baseArrayLayer
                    // (1163873110 in the validation message) because only the counts were set. The canonical gate caught
                    // it as VUID-vkCmdClearColorImage-baseArrayLayer-01472 on a plain leg.
                    this.clearRange.baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
                    this.clearColour = VkClearColorValue.malloc();
                    this.clearDepthValue = VkClearDepthStencilValue.malloc();
                    this.clearColourFloats = new float[4];
                }
                VkImageSubresourceRange range = this.clearRange;

                // The game's own clear colour and depth, not a renderer-chosen one: this pair is the framebuffer's
                // image, and glClear on a framebuffer clears with the current glClearColor - a mod's 256x256 target
                // keeps its own background (measured: the fixture's 0x081020 read back as 0 under the fixed pair
                // clear, every untouched pixel a mismatch).
                float[] clearColor = this.clearColourFloats;
                org.soutalab.celtium.vulkan.gl.GlsmFrontend.clearColor(clearColor);
                VkClearColorValue color = this.clearColour;
                color.float32(0, clearColor[0]);
                color.float32(1, clearColor[1]);
                color.float32(2, clearColor[2]);
                color.float32(3, clearColor[3]);
                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                // One barrier for both images, not two: the colour and depth transitions are the same
                // ALL_COMMANDS -> TRANSFER dependency on two images of one pair, and recording them separately put two
                // vkCmdPipelineBarrier calls on a path that runs once per pair per frame (12 a frame on the measured
                // world scene, where GregTech's bloom chain binds a mip pyramid of framebuffers). The clears below are
                // both TRANSFER-stage writes, so moving the depth transition ahead of the colour clear changes no
                // ordering the GPU has to honour.
                toTransfer(command, true, true);
                vkCmdClearColorImage(command, this.colorImage, colorClearLayout(), color, range);

                VkClearDepthStencilValue depth = this.clearDepthValue;
                depth.depth((float) org.soutalab.celtium.vulkan.gl.GlsmFrontend.clearDepth()).stencil(0);
                range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                vkCmdClearDepthStencilImage(command, this.depthImage, DEPTH_TRANSFER_LAYOUT, depth, range);

                // The extra targets of a multi-target framebuffer clear with the same colour: glClear clears
                // every draw buffer, and a tuple whose later slots kept undefined content would hand the
                // composite exactly the garbage GL says cannot be there.
                clearExtras(command, color, range);

                recordClearToAttachment(command);
                this.clears++;
            }
        }

        /**
         * The pair's once-per-frame clear is recorded as one {@code vkCmdClearAttachments} inside the pass that
         * follows it, instead of two {@code vkCmdClear*Image} calls and the two barriers that make them legal.
         * <p>
         * Why: the window split put the pair clear at 19 us a window against 12 us for the command-buffer begin and
         * 10 us for the acquire, and the world scene clears 12 pairs a frame (GregTech's bloom chain binds a mip
         * pyramid of framebuffers). The clear is outside the pass because {@code vkCmdClear*Image} requires it, and
         * that is what costs the barriers: the image has to be moved to a transfer layout and back, and the transfer
         * write has to be made visible to the attachment reads that follow. Inside the pass none of that exists - the
         * attachment is already in the layout the pass declares, and the clear is ordered by the pass itself.
         * <p>
         * Adopted in round 54: the paired measurement (legs r53a/r53b, AMD) put {@code winHead} at 0.1 ms a frame
         * with the clear outside the pass and 0.0 with it inside, and the acceptance legs on both GPUs found no
         * validation message and no picture change. The colour is cleared in the attachment layout rather than in
         * {@code TRANSFER_DST_OPTIMAL}, the layout the colour clear uses for its fast clear on Intel, so the Intel
         * leg is the one that says the fast clear survives the move - see
         * {@code docs/evidence/pair-clear-default-2026-11-04.md}.
         */
        static final boolean PAIR_CLEAR_IN_PASS = true;

        /** Whether a clear is waiting for the pass that follows it; see {@link #PAIR_CLEAR_IN_PASS}. */
        private boolean deferredClearPending;
        private final float[] deferredClearColour = new float[4];
        private float deferredClearDepth;

        /** {@return whether a clear is waiting for the pass that follows it}. */
        boolean hasDeferredClear() {
            return this.deferredClearPending;
        }

        /**
         * Records everything the deferred clear needs before the pass begins, and defers the clear itself.
         * <p>
         * The acquire is the same one {@link #clear} records. The depth's layout is the one thing that can still need
         * a barrier: a pair that was just created is in {@code TRANSFER_DST_OPTIMAL} after its acquire, and the pass
         * declares the attachment layout, so the move has to happen before {@code vkCmdBeginRenderPass}. In the steady
         * state the depth is already in the attachment layout (the pass's own finalLayout leaves it there), so this
         * records nothing at all.
         */
        void deferClear(VkCommandBuffer command) {
            checkAlive();
            if (command == null || command.address() == 0L) {
                throw new IllegalArgumentException(this.label + " pair clear needs a command buffer");
            }
            if (!exists()) {
                throw new IllegalStateException("ensure must run before the " + this.label + " pair is cleared");
            }
            if (!this.acquired) {
                recordAcquire(command);
                this.acquired = true;
            }
            if (celtium$deferredClearNeedsDepthBarrier(depthLayout())) {
                recordDepthToRender(command);
            }
            org.soutalab.celtium.vulkan.gl.GlsmFrontend.clearColor(this.deferredClearColour);
            this.deferredClearDepth = (float) org.soutalab.celtium.vulkan.gl.GlsmFrontend.clearDepth();
            this.deferredClearPending = true;
            this.clears++;
        }

        /**
         * {@return whether a deferred clear has to move the depth to the attachment layout first}.
         * <p>
         * Package-private and static so the rule is pinned without a device: the pass declares the attachment layout
         * as its initialLayout, and a clear recorded inside it cannot change that.
         */
        static boolean celtium$deferredClearNeedsDepthBarrier(int depthLayout) {
            return depthLayout != DEPTH_RENDER_LAYOUT;
        }

        /**
         * {@return the clear rect's extent along one axis, clamped to the attachment}.
         * <p>
         * {@code vkCmdClearAttachments} requires every rect to lie inside the render area, and the render area is the
         * framebuffer's - which is built from the pair's views. The two agree in the ordinary case, but the caller
         * passes the pass's render area, and a pass whose area was taken from the frame rather than from the pair
         * would ask for a rect larger than the attachment. Clamping is the same clear over the pixels that exist;
         * an unknown pair extent ({@code <= 0}) leaves the caller's value alone rather than clearing nothing.
         */
        static int celtium$deferredClearExtent(int passExtent, int pairExtent) {
            if (passExtent <= 0) {
                return 0;
            }
            return pairExtent > 0 ? Math.min(passExtent, pairExtent) : passExtent;
        }

        /** Moves the depth image to the attachment layout the pass declares, for a deferred clear. */
        private void recordDepthToRender(VkCommandBuffer command) {
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default().image(this.depthImage)
                        .oldLayout(depthLayout()).newLayout(DEPTH_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                                | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
                DrawCounters.notePipelineBarrier();
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier);
                depthLayout(command, DEPTH_RENDER_LAYOUT);
            }
        }

        /**
         * Records the deferred clear inside the pass that has just begun, and returns whether one was waiting.
         * <p>
         * The rect is the pass's render area, which is the pair's own extent: the framebuffer this pass attaches is
         * built from the pair's views, so the clear covers exactly the attachment. {@code vkCmdClearAttachments} is
         * not affected by the scissor, so a state the game left behind cannot shrink it.
         * <p>
         * The rect is clamped to the pair's own extent ({@link #celtium$deferredClearExtent}): the caller passes the
         * pass's render area, and a pass whose area came from the frame rather than from the pair would otherwise ask
         * for a rect larger than the attachment - VUID-vkCmdClearAttachments-pRects-00016. The clamp is the same clear
         * over the pixels that exist, so it cannot change the picture.
         */
        boolean recordDeferredClearAttachments(VkCommandBuffer command, int width, int height) {
            return recordDeferredClearAttachments(command, width, height, 1);
        }

        /**
         * The deferred clear for a pass with {@code colorAttachments} colour attachments: glClear clears every
         * draw buffer with the one latched colour, so a multi-target pass gets one clear attachment per colour
         * slot it attaches, then the depth. The count is clamped to the tuple this pair actually holds - a clear
         * attachment naming a slot the pass does not attach is a validation error, and the caller passes the
         * count of the pass it began, which the window sized from this same tuple.
         */
        boolean recordDeferredClearAttachments(VkCommandBuffer command, int width, int height,
                int colorAttachments) {
            if (!this.deferredClearPending) {
                return false;
            }
            this.deferredClearPending = false;
            if (command == null || command.address() == 0L || width <= 0 || height <= 0) {
                return false;
            }
            int clearWidth = celtium$deferredClearExtent(width, this.width);
            int clearHeight = celtium$deferredClearExtent(height, this.height);
            if (clearWidth <= 0 || clearHeight <= 0) {
                return false;
            }
            int colours = Math.max(1, Math.min(colorAttachments, 1 + this.extraColorCount));
            try (MemoryStack stack = stackPush()) {
                org.lwjgl.vulkan.VkClearAttachment.Buffer attachments =
                        org.lwjgl.vulkan.VkClearAttachment.calloc(colours + 1, stack);
                for (int slot = 0; slot < colours; slot++) {
                    org.lwjgl.vulkan.VkClearAttachment colour = attachments.get(slot);
                    colour.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                    colour.colorAttachment(slot);
                    colour.clearValue().color()
                            .float32(0, this.deferredClearColour[0])
                            .float32(1, this.deferredClearColour[1])
                            .float32(2, this.deferredClearColour[2])
                            .float32(3, this.deferredClearColour[3]);
                }
                org.lwjgl.vulkan.VkClearAttachment depth = attachments.get(colours);
                depth.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                depth.colorAttachment(0);
                depth.clearValue().depthStencil().depth(this.deferredClearDepth).stencil(0);
                org.lwjgl.vulkan.VkClearRect.Buffer rect = org.lwjgl.vulkan.VkClearRect.calloc(1, stack);
                rect.get(0).rect().offset().set(0, 0);
                rect.get(0).rect().extent().set(clearWidth, clearHeight);
                rect.get(0).baseArrayLayer(0).layerCount(1);
                vkCmdClearAttachments(command, attachments, rect);
            }
            return true;
        }

        /**
         * {@link #clear} with explicit values and a mask, for a game-issued {@code glClear} replayed mid-recording.
         * <p>
         * Same barriers, same acquire-before-clear order; the colour and depth are the ones latched when the game
         * issued the clear, and the mask says which buffers it names (0x4000 colour, 0x0100 depth). Unlike the
         * once-per-frame clear this may run several times in one frame - the game's own ordering, not a policy.
         */
        private void clearWith(VkCommandBuffer command, int mask, float[] rgba, double depthValue) {
            this.deferredClearPending = false;
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
                // One barrier for whichever images this clear names; see the note in clear().
                toTransfer(command, (mask & 0x4000) != 0, (mask & 0x0100) != 0);
                if ((mask & 0x4000) != 0) {
                    VkClearColorValue color = VkClearColorValue.calloc(stack);
                    color.float32(0, rgba[0]);
                    color.float32(1, rgba[1]);
                    color.float32(2, rgba[2]);
                    color.float32(3, rgba[3]);
                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                vkCmdClearColorImage(command, this.colorImage, colorClearLayout(), color, range);
                }
                if ((mask & 0x0100) != 0) {
                    VkClearDepthStencilValue depth = VkClearDepthStencilValue.calloc(stack);
                    depth.depth((float) depthValue).stencil(0);
                    range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                    vkCmdClearDepthStencilImage(command, this.depthImage, DEPTH_TRANSFER_LAYOUT, depth, range);
                }
                if ((mask & 0x4000) != 0 && this.extraColorCount > 0) {
                    VkClearColorValue extraColor = VkClearColorValue.calloc(stack);
                    extraColor.float32(0, rgba[0]);
                    extraColor.float32(1, rgba[1]);
                    extraColor.float32(2, rgba[2]);
                    extraColor.float32(3, rgba[3]);
                    clearExtras(command, extraColor, range);
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
            this.deferredClearPending = false;
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
                // One barrier for whichever images this clear names; see the note in clear(). The colour is only
                // transferred on the first use, which is the only time this path clears it.
                toTransfer(command, first, true);
                if (first) {
                    // First-use initialisation keeps the same rule as {@link #clear}: the game's own clear colour,
                    // because this image is a framebuffer's content and GL's framebuffer starts from the game's
                    // state, not a renderer-chosen one.
                    float[] clearColor = new float[4];
                    org.soutalab.celtium.vulkan.gl.GlsmFrontend.clearColor(clearColor);
                    VkClearColorValue color = VkClearColorValue.calloc(stack);
                    color.float32(0, clearColor[0]);
                    color.float32(1, clearColor[1]);
                    color.float32(2, clearColor[2]);
                    color.float32(3, clearColor[3]);
                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                    vkCmdClearColorImage(command, this.colorImage, colorClearLayout(), color, range);
                } else {
                    recordColourHandBack(command);
                }

                // The game's glClearDepth, because this clear is the game's own "clear just the depth" - the real
                // default framebuffer answers it with the recorded value, not with 1.0.
                VkClearDepthStencilValue depth = VkClearDepthStencilValue.calloc(stack);
                depth.depth((float) org.soutalab.celtium.vulkan.gl.GlsmFrontend.clearDepth()).stencil(0);
                range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
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
                DrawCounters.notePipelineBarrier();
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

        /** Where this pair's private depth image is: UNDEFINED, the clear's transfer layout, or the attachment layout. */
        private int depthLayout = VK_IMAGE_LAYOUT_UNDEFINED;

        /**
         * The shared depth store key this pair's depth image is borrowed from, or 0 when the depth is the
         * pair's own. GL keys depth storage by the texture — every Iris shadow framebuffer attaches the same
         * mainDepth texture — while a pair's depth is its own, so without this the shadow depth fragments
         * across the pairs of whichever family members were bound and there is no single image to copy from
         * or sample. A borrowed image is owned by the store: {@link #retire()} and {@link #release()} hand
         * back the reference, never the handles, and the layout record lives in the store entry, because two
         * pairs sharing one image cannot each keep a private belief about where it is.
         */
        private long sharedDepthKey;

        /** {@return where this pair's depth image actually is}, the store's record for a borrowed image. */
        private int depthLayout() {
            if (this.sharedDepthKey != 0L) {
                SharedDepth shared = sharedDepths.get(this.sharedDepthKey);
                if (shared != null) {
                    return shared.layout;
                }
            }
            return this.depthLayout;
        }

        /** Records where this pair's depth image has been moved to, in the store for a borrowed image. */
        private void depthLayout(int layout) {
            if (this.sharedDepthKey != 0L) {
                SharedDepth shared = sharedDepths.get(this.sharedDepthKey);
                if (shared != null) {
                    shared.layout = layout;
                    return;
                }
            }
            this.depthLayout = layout;
        }

        /**
         * Records where this pair's depth image has been moved to by a barrier in {@code command}, keeping both
         * the shared depth store and any published {@link VulkanGlTexture} foreign-layout ledger in step.
         */
        private void depthLayout(VkCommandBuffer command, int layout) {
            depthLayout(layout);
            if (this.sharedDepthKey != 0L && this.depthImage != 0L) {
                VulkanGlTexture.noteExternalForeignLayout(command, this.depthImage, layout);
            }
        }

        /** Whether the pair's colour sits in the clear's transfer layout. */
        private boolean colorInTransfer;

        /**
         * Moves whichever of the pair's images a clear is about to write into the transfer layout, in one barrier.
         * <p>
         * The colour and depth transitions are the same dependency - {@code ALL_COMMANDS} to {@code TRANSFER} - on two
         * images of one pair, so they belong in one {@code vkCmdPipelineBarrier}. They used to be two calls, one per
         * image, and the pair clear runs once per pair per frame: 12 a frame on the measured world scene, where
         * GregTech's bloom chain binds a mip pyramid of framebuffers and each level's pair is cleared. The window split
         * put that clear at 19 us a window against 12 us for the command-buffer begin and 10 us for the acquire, which
         * made it the largest named per-window item; one of its three barriers is what this removes.
         * <p>
         * Both clears that follow are {@code TRANSFER}-stage writes, so recording the depth transition before the
         * colour clear changes no ordering the GPU has to honour - and it waits for less, because the barrier's source
         * stage is {@code ALL_COMMANDS} and the colour clear has not been recorded yet.
         *
         * @param wantColor whether the caller is about to clear the colour image
         * @param wantDepth whether the caller is about to clear the depth image
         */
        private void toTransfer(VkCommandBuffer command, boolean wantColor, boolean wantDepth) {
            int images = GllessFrameTarget.celtium$transferImages(wantColor, wantDepth,
                    this.colorInTransfer, depthLayout() == DEPTH_TRANSFER_LAYOUT);
            if (images == 0) {
                return;
            }
            boolean color = (images & TRANSFER_COLOR) != 0;
            boolean depth = (images & TRANSFER_DEPTH) != 0;
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc((color ? 1 : 0) + (depth ? 1 : 0), stack);
                int index = 0;
                if (color) {
                    VkImageMemoryBarrier barrier = barriers.get(index++);
                    barrier.sType$Default().image(this.colorImage)
                            .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(DEPTH_TRANSFER_LAYOUT)
                            .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                            .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    barrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                    this.colorInTransfer = true;
                }
                if (depth) {
                    int from = depthLayout();
                    VkImageMemoryBarrier barrier = barriers.get(index);
                    barrier.sType$Default().image(this.depthImage)
                            .oldLayout(from).newLayout(DEPTH_TRANSFER_LAYOUT)
                            .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                            .srcAccessMask(from == VK_IMAGE_LAYOUT_UNDEFINED ? 0
                                    : VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    barrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
                    depthLayout(command, DEPTH_TRANSFER_LAYOUT);
                }
                DrawCounters.notePipelineBarrier();
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, null, barriers);
            }
        }

        private int colorClearLayout() {
            return this.colorInTransfer ? DEPTH_TRANSFER_LAYOUT : VK_IMAGE_LAYOUT_GENERAL;
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
            // A private depth is UNDEFINED here, as the colour is. A borrowed (shared) depth may already
            // hold another pair's pass: the barrier then starts from the store's recorded layout and waits
            // for those writes instead of discarding them - a layout transition preserves content unless
            // the old layout is UNDEFINED, which for a shared image it is only on its first pair's acquire.
            int depthFrom = depthLayout();
            try (MemoryStack stack = stackPush()) {
                if (depthFrom == VK_IMAGE_LAYOUT_UNDEFINED) {
                    // Both images arrive from UNDEFINED with a zero source access mask under the same
                    // TOP_OF_PIPE to TRANSFER dependency, so the two moves belong in one barrier call -
                    // the same fusion Pair.toTransfer already makes for the pair clear.
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
                            .oldLayout(depthFrom).newLayout(DEPTH_TRANSFER_LAYOUT)
                            .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                            .srcAccessMask(0)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

                    DrawCounters.notePipelineBarrier();
                    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barriers);
                } else {
                    VkImageMemoryBarrier.Buffer colorBarriers = VkImageMemoryBarrier.calloc(1, stack);
                    VkImageMemoryBarrier colorBarrier = colorBarriers.get(0);
                    colorBarrier.sType$Default()
                            .image(this.colorImage)
                            .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                            .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                            .srcAccessMask(0).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

                    VkImageMemoryBarrier.Buffer depthBarriers = VkImageMemoryBarrier.calloc(1, stack);
                    VkImageMemoryBarrier depthBarrier = depthBarriers.get(0);
                    depthBarrier.sType$Default()
                            .image(this.depthImage)
                            .oldLayout(depthFrom).newLayout(DEPTH_TRANSFER_LAYOUT)
                            .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                            .srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                                    | VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

                    DrawCounters.notePipelineBarrier();
                    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, colorBarriers);
                    // The depth barrier rides in its own call when the depth already holds content: TOP_OF_PIPE
                    // supports no access flags at all, so a barrier that names the previous writes (the shared
                    // case above) under it is exactly VUID-vkCmdPipelineBarrier-pImageMemoryBarriers-02819 -
                    // measured on the first leg whose pairs borrowed a written shared depth (leg 16, item 333).
                    // ALL_COMMANDS as the source stage covers every stage those writes could have come from.
                    DrawCounters.notePipelineBarrier();
                    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, depthBarriers);
                }
                // The barrier above is the move, so this is where the pair's own record of it belongs.
                this.colorImageLayout = VK_IMAGE_LAYOUT_GENERAL;
                depthLayout(command, DEPTH_TRANSFER_LAYOUT);
            }
        }

        /**
         * The acquire for a pass that attaches this pair to draw, without clearing it first.
         * <p>
         * Same two images, same two moves as {@link #recordAcquire}, except for where the depth arrives: the
         * attachment layout the frame pass declares rather than the clear's transfer layout. A borrowed depth starts
         * from the store's record, so a member of a shadow family arriving second does not discard what the first
         * member's pass wrote - a layout transition preserves content unless its old layout is {@code UNDEFINED}, which
         * for a shared image is only the first pair's acquire.
         * <p>
         * Both barriers use {@code ALL_COMMANDS} on both sides. {@code TOP_OF_PIPE} supports no access flags at all,
         * and a barrier that names the previous attachment or transfer writes under it is
         * {@code VUID-vkCmdPipelineBarrier-pImageMemoryBarriers-02819} - measured in item 333's first shared-depth leg.
         */
        private void recordAttachAcquire(VkCommandBuffer command) {
            checkAlive();
            if (command == null || command.address() == 0L) {
                throw new IllegalArgumentException(this.label + " pair acquire needs a command buffer");
            }
            if (!exists()) {
                throw new IllegalStateException("ensure must run before the " + this.label + " pair is acquired");
            }
            int queueFamily = context.queueFamilyIndex();
            try (MemoryStack stack = stackPush()) {
                VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);
                int colorFrom = colorLayout();
                VkImageMemoryBarrier colorBarrier = barriers.get(0);
                colorBarrier.sType$Default()
                        .image(this.colorImage)
                        .oldLayout(colorFrom).newLayout(COLOR_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(GllessFrameTarget.celtium$layoutSourceAccess(colorFrom))
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

                int depthFrom = depthLayout();
                VkImageMemoryBarrier depthBarrier = barriers.get(1);
                depthBarrier.sType$Default()
                        .image(this.depthImage)
                        .oldLayout(depthFrom).newLayout(DEPTH_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(GllessFrameTarget.celtium$layoutSourceAccess(depthFrom))
                        .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                                | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

                DrawCounters.notePipelineBarrier();
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
            }
            this.colorImageLayout = COLOR_RENDER_LAYOUT;
            depthLayout(command, DEPTH_RENDER_LAYOUT);
            this.acquired = true;
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
                        .oldLayout(depthLayout()).newLayout(DEPTH_RENDER_LAYOUT)
                        .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(depthAccess);
                depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

                DrawCounters.notePipelineBarrier();
                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        0, null, null, barriers);
                depthLayout(command, DEPTH_RENDER_LAYOUT);
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
            // A borrowed depth is the store's, not this pair's: retiring the pair hands back the reference
            // and leaves the handles (and the layout record) with the store.
            final boolean borrowedDepth = this.sharedDepthKey != 0L;
            final long depthView = borrowedDepth ? VK_NULL_HANDLE : this.depthView;
            final long depthImage = borrowedDepth ? VK_NULL_HANDLE : this.depthImage;
            final long depthMemory = borrowedDepth ? VK_NULL_HANDLE : this.depthMemory;
            this.colorView = VK_NULL_HANDLE;
            this.colorImage = VK_NULL_HANDLE;
            this.colorMemory = VK_NULL_HANDLE;
            this.depthView = VK_NULL_HANDLE;
            this.depthImage = VK_NULL_HANDLE;
            this.depthMemory = VK_NULL_HANDLE;
            if (view != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(device.address(), () -> vkDestroyImageView(device, view, null));
            }
            if (image != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(device.address(), () -> vkDestroyImage(device, image, null));
            }
            if (memory != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(device.address(), () -> VulkanContext.freeMemory(device, memory));
            }
            if (depthView != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(device.address(), () -> vkDestroyImageView(device, depthView, null));
            }
            if (depthImage != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(device.address(), () -> vkDestroyImage(device, depthImage, null));
            }
            if (depthMemory != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(device.address(), () -> VulkanContext.freeMemory(device, depthMemory));
            }
            retireExtras();
            // The generation moves with the images: a framebuffer built over the views just handed to the retire
            // queue must read as stale even though this pair is asked for them again only when it is re-ensured.
            this.generation = GllessFrameTarget.this.nextChainPairGeneration();
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
            releaseExtras();
            this.colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            // A borrowed depth's layout record belongs to the store and survives the pair; only the pair's
            // own depth forgets where it was.
            if (this.sharedDepthKey == 0L) {
                this.depthLayout = VK_IMAGE_LAYOUT_UNDEFINED;
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
            if (this.sharedDepthKey == 0L) {
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
            } else {
                this.depthView = VK_NULL_HANDLE;
                this.depthImage = VK_NULL_HANDLE;
                this.depthMemory = VK_NULL_HANDLE;
            }
            this.sharedDepthKey = 0L;
            this.width = 0;
            this.height = 0;
            this.acquired = false;
        }

        /** {@return this pair's own counters, labelled with the identity stream that feeds it}. */
        private String summary() {
            return this.label + "(size=" + (exists() ? this.width + "x" + this.height : "none")
                    + " creations=" + this.creations + " clears=" + this.clears
                    + " selfTests=" + this.selfTests + " acquired=" + this.acquired
                    + " colors=" + colorCount() + ")";
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
        if (this.presentBorrow != null) {
            // The target dies holding an unpresented borrow. The image itself is the lender's and stays
            // acquired until the swapchain generation is retired; abandoning tells the lender, whose
            // borrowAbandoned flag makes the present path rebuild the generation before borrowing again.
            this.context.presentEngine().abandon(this.presentBorrow);
            this.presentBorrow = null;
            this.presentAcquireWaitPending = false;
        }
        for (long semaphore : this.presentAcquireSemaphores) {
            org.lwjgl.vulkan.VK10.vkDestroySemaphore(device, semaphore, null);
        }
        this.presentAcquireSemaphores = new long[0];
        this.defaultPair.release();
        for (Pair pair : this.chainPairs.values()) {
            pair.release();
        }
        this.chainPairs.clear();
        this.chainPairOwners.clear();
        // The shared depth images are the store's own: pairs only borrowed them, so they are destroyed here,
        // after the pairs have handed their references back. delete() runs with the GPU settled, in place.
        for (SharedDepth entry : this.sharedDepths.values()) {
            if (entry.view != VK_NULL_HANDLE) {
                vkDestroyImageView(device, entry.view, null);
            }
            if (entry.image != VK_NULL_HANDLE) {
                vkDestroyImage(device, entry.image, null);
            }
            if (entry.memory != VK_NULL_HANDLE) {
                VulkanContext.freeMemory(device, entry.memory);
            }
        }
        this.sharedDepths.clear();
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
