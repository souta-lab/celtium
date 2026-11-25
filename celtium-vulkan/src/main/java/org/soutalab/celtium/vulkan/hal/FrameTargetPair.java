package org.soutalab.celtium.vulkan.hal;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.vkCmdBlitImage;
import static org.lwjgl.vulkan.VK10.vkCmdClearAttachments;
import static org.lwjgl.vulkan.VK10.vkCmdClearColorImage;
import static org.lwjgl.vulkan.VK10.vkCmdClearDepthStencilImage;
import static org.lwjgl.vulkan.VK10.vkCmdCopyImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.VulkanGlTexture;
import org.soutalab.celtium.vulkan.VulkanRetireQueue;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import java.nio.LongBuffer;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

/**
 * The frame target pair type, moved out of the GL-less frame target (HAL wave 3, pair group):
 * the default pair (identity 0), the off-target pair and every chain pair are this one type, so
 * extracting it moves functional groups 2, 3 and 4 of the frame-target decomposition together.
 * Owner services arrive through {@link Host}; the pair owns its images, layouts and lifecycle.
 */
/**
 * One owned colour+depth pair and its whole lifecycle: created on first use at the size the caller asks for,
 * replaced when that size changes, cleared once per frame from UNDEFINED, and released by {@code delete()} on the owning frame target.
 * <p>
 * The two instances differ in one thing only - the identity whose draws they receive - and that is what the
 * label on every counter of theirs says. The pair itself has no idea which stream feeds it, so the lifecycle is
 * written once here rather than twice beside two sets of fields.
 */
public final class FrameTargetPair {

    /** The owner services a pair cannot own: the one image allocator, the alive check, the shared
     * depth store it borrows depth from, and the pair-generation counter it advances when its
     * images are created or retired. Injected by the frame target that owns the pairs, in the
     * same shape {@link SharedDepthStore} takes its allocator and borrower callback. */
    public interface Host {
        /** {@return the context the pair creates and records against}. */
        VulkanContext context();
        /** Allocates one pair image, as {@code {image, memory, view}} (the owner's one allocator). */
        long[] allocateImage(boolean color, int width, int height, TargetFormatClass colorClass);
        /** Throws when the owning frame target has been deleted. */
        void checkAlive();
        /** {@return the shared depth store pairs borrow depth images from}. */
        SharedDepthStore sharedDepthStore();
        /** {@return the next pair-generation value}, taken when a pair's images change. */
        long nextPairGeneration();
    }

    private final Host host;

    /** {@return the owner's context}, resolved per call: pairs are constructed before the owner's
     * constructor body has assigned its context, exactly as the inner class read it lazily. */
    private VulkanContext context() {
        return this.host.context();
    }

    /** {@return the shared depth store}, resolved per call for the same reason as {@link #context()}. */
    private SharedDepthStore sharedDepthStore() {
        return this.host.sharedDepthStore();
    }

    static final int COLOR_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;
    static final int DEPTH_RENDER_LAYOUT = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
    private static final int DEPTH_TRANSFER_LAYOUT = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    private static final float OFF_TARGET_CLEAR_RED = 0.0f;
    private static final float OFF_TARGET_CLEAR_GREEN = 0.0f;
    private static final float OFF_TARGET_CLEAR_BLUE = 0.0f;
    private static final float OFF_TARGET_CLEAR_ALPHA = 0.0f;
    private static final float OFF_TARGET_CLEAR_DEPTH = 1.0f;

    static final int TRANSFER_COLOR = 1;
    static final int TRANSFER_DEPTH = 2;

    /** {@return which of a pair's images a clear still has to move into the transfer layout} (the pair's copy of the frame target's pure rule). */
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

    /** {@return the access mask an image last moved to {@code layout} may have pending} (the pair's copy of the frame target's table). */
    static int layoutSourceAccess(int layout) {
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


    /** How this pair is named in every counter that describes it. */
    private final String label;
    /**
     * A swapchain colour image this pair is borrowing for the current frame, lent by
     * {@code PresentEngine} under the ownership ruling (lanechat #8830): the engine owns the image and its
     * view, this pair only names them while the borrow lasts. The fields are never passed to a destroy or
     * retire call - {@link #release()} and {@link #retire()} forget them exactly as they forget a borrowed
     * depth - and the borrow ends at the frame's present or when the lender invalidates it.
     */
    private long borrowedColorImage;
    private long borrowedColorView;
    private int borrowedColorIndex = -1;
    public long colorImage;
    private long colorMemory;
    public long colorView;
    public long depthImage;
    private long depthMemory;
    public long depthView;
    public int width;
    public int height;
    /**
     * Extra colour images for attachment slots 1.. of a multi-target framebuffer (index = slot - 1), with
     * their memories, views and recorded layouts. Null until the pair is asked to hold more than one colour
     * target; the pair's own colour image above is always slot 0. The extras live and die with the pair's
     * current size: {@link #ensure} retires them with the rest, and the caller re-asks for the count it needs
     * after every ensure, which is what keeps a resized pair from holding images at the old extent.
     */
    public long[] extraColorImages;
    private long[] extraColorMemories;
    public long[] extraColorViews;
    public int[] extraColorLayouts;
    public int extraColorCount;
    /** Bumps whenever the extras are built or dropped, so a framebuffer cache over their views can key on it. */
    public long extraGeneration;
    /** False until the first clear has moved the pair out of the layout it was created in (UNDEFINED). */
    public boolean acquired;
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
    public int colorImageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    /**
     * The format class this pair's colour images are allocated in (QUEUE 404, S3). An attribute of the
     * pair, not of its slot: it is fixed for the pair's current images, and a different wanted class is a
     * rebuild, exactly like a different size (rule R3). The default pair and a pair nobody resolved a
     * class for stay {@code RGBA8_UNORM}, which is the format every pair had before classes existed.
     */
    public TargetFormatClass formatClass = TargetFormatClass.RGBA8_UNORM;
    private int creations;
    /** Unique across pairs and evictions: which images a framebuffer built over this pair's views names. */
    public long generation;
    private int clears;
    private int selfTests;

    public FrameTargetPair(String label, Host host) {
        this.label = label;
        this.host = host;
    }

    /**
     * Lends this pair a swapchain colour image for one frame: {@code image} / {@code view} are the borrowed
     * handles and {@code index} the swapchain image index they were acquired as. Borrowing replaces any
     * previous borrow (the previous image was presented or retired by the lender, never by this pair).
     */
    public void borrowColorImage(long image, long view, int index) {
        if (image == VK_NULL_HANDLE) {
            throw new IllegalArgumentException(this.label + " pair cannot borrow a null swapchain image");
        }
        this.borrowedColorImage = image;
        this.borrowedColorView = view;
        this.borrowedColorIndex = index;
    }

    /** Ends the borrow: the pair forgets the handles and destroys nothing (they are the lender's). */
    public void releaseBorrowedColorImage() {
        this.borrowedColorImage = VK_NULL_HANDLE;
        this.borrowedColorView = VK_NULL_HANDLE;
        this.borrowedColorIndex = -1;
    }

    /** {@return whether this pair currently borrows a swapchain colour image}. */
    public boolean hasBorrowedColorImage() {
        return this.borrowedColorImage != VK_NULL_HANDLE;
    }

    /** {@return the borrowed swapchain colour image}, or {@code VK_NULL_HANDLE} when no borrow is live. */
    public long borrowedColorImage() {
        return this.borrowedColorImage;
    }

    /** {@return the borrowed image's view}, or {@code VK_NULL_HANDLE} when no borrow (or no view) is live. */
    public long borrowedColorView() {
        return this.borrowedColorView;
    }

    /** {@return the swapchain index the borrowed image was acquired as}, or -1 when no borrow is live. */
    public int borrowedColorIndex() {
        return this.borrowedColorIndex;
    }

    /** {@return the layout this pair's colour image is actually in}. */
    public int colorLayout() {
        return this.colorImageLayout;
    }

    /** Records a layout this pair's colour image has been moved to, by whoever moved it. */
    public void noteColorLayout(int layout) {
        this.colorImageLayout = layout;
    }

    public boolean exists() {
        return this.colorView != VK_NULL_HANDLE && this.depthView != VK_NULL_HANDLE;
    }

    public boolean matches(int requestedWidth, int requestedHeight) {
        return exists() && this.width == requestedWidth && this.height == requestedHeight;
    }

    /** {@return whether this pair's colour images are allocated in {@code wanted}} (QUEUE 404's named rule). */
    public boolean matchesFormat(TargetFormatClass wanted) {
        return this.formatClass == wanted;
    }

    /**
     * {@return whether a pair must be rebuilt for an ensure}: the pure form of {@link #ensure}'s early
     * return, so the rule is pinned without a device. A live pair is kept only when the size matches, the
     * shared-depth borrow is the one asked for, and the format class is the one asked for - a class change
     * retires and recreates the pair, as a size change does (rule R3), because the existing framebuffers
     * and render passes name the old format.
     */
    public static boolean celtium$pairRebuildNeeded(boolean sizeMatches, boolean sharedMatches,
            TargetFormatClass liveClass, TargetFormatClass wantedClass) {
        return !(sizeMatches && sharedMatches && liveClass == wantedClass);
    }

    /** {@return whether the images this pair holds now have not had their one legal-use self-test yet}. */
    public boolean needsSelfTest() {
        return exists() && this.selfTests < this.creations;
    }

    /**
     * {@return whether the live extras are not the ones a draw into {@code attachments} colour targets needs}.
     * Pure and static so the rule is pinned without a device: the extras are exactly slots 1..attachments-1,
     * and their extent is the pair's own (a resize retires them, so a live set is never at a stale size).
     */
    public static boolean celtium$extrasNeedRebuild(int liveExtraCount, int attachments) {
        return liveExtraCount != Math.max(0, attachments - 1);
    }

    /** {@return how many colour targets this pair currently holds images for} (1 = the pair's own only). */
    public int colorCount() {
        return exists() ? 1 + this.extraColorCount : 0;
    }

    /** The colour image at a slot of this pair (0 = its own), or 0 when that slot holds none. */
    public long colorImageAt(int slot) {
        if (slot == 0) {
            return this.colorImage;
        }
        return slot > 0 && slot - 1 < this.extraColorCount ? this.extraColorImages[slot - 1] : 0L;
    }

    /** The colour view at a slot of this pair (0 = its own), or 0 when that slot holds none. */
    public long colorViewAt(int slot) {
        if (slot == 0) {
            return this.colorView;
        }
        return slot > 0 && slot - 1 < this.extraColorCount ? this.extraColorViews[slot - 1] : 0L;
    }

    /** {@return the slot an image fills in this pair} (0 = its own colour image), or -1 when it fills none. */
    public int colorSlotForImage(long image) {
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
    public long[] extraColorImageSnapshot() {
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
    public void ensureExtras(int attachments) {
        this.host.checkAlive();
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
                long[] allocated = this.host.allocateImage(true, this.width, this.height, this.formatClass);
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
        VkDevice device = context().device();
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
        VkDevice device = context().device();
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
    public int extraColorLayoutAt(int slot) {
        return slot > 0 && slot - 1 < this.extraColorCount && this.extraColorLayouts != null
                ? this.extraColorLayouts[slot - 1] : VK_IMAGE_LAYOUT_UNDEFINED;
    }

    /** Records a layout an extra colour image has been moved to, by whoever moved it (the sampling side). */
    public void noteExtraColorLayout(int slot, int layout) {
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
    public void recordExtrasAcquire(VkCommandBuffer command) {
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
        int queueFamily = context().queueFamilyIndex();
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
        int queueFamily = context().queueFamilyIndex();
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
    public void ensure(int requestedWidth, int requestedHeight) {
        ensure(requestedWidth, requestedHeight, 0L, null, this.formatClass);
    }

    /**
     * {@link #ensure(int, int)} with a borrowed depth: when {@code sharedDepth} is non-null it is the
     * shared store's {@code {image, memory, view}} triple for {@code sharedKey}, and the pair renders
     * into that depth instead of allocating its own. The store keeps ownership (see {@link #sharedDepthKey});
     * the pair's colour is rebuilt exactly as a resize rebuilds it, so engaging sharing costs one pair
     * recreation, on the first window that asks for it.
     */
    public void ensure(int requestedWidth, int requestedHeight, long sharedKey, long[] sharedDepth) {
        ensure(requestedWidth, requestedHeight, sharedKey, sharedDepth, this.formatClass);
    }

    /**
     * {@link #ensure(int, int, long, long[])} in a wanted format class: the pair is rebuilt when the class
     * differs from the live one, through the same retire path a resize takes (rule R3) - the framebuffers
     * and render passes built over the old views name the old format, and the generation bump in the
     * rebuild is what retires them.
     */
    public void ensure(int requestedWidth, int requestedHeight, long sharedKey, long[] sharedDepth,
            TargetFormatClass wantedClass) {
        this.host.checkAlive();
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
            long[] color = this.host.allocateImage(true, requestedWidth, requestedHeight, wantedClass);
            this.colorImage = color[0];
            this.colorMemory = color[1];
            this.colorView = color[2];
            if (sharedDepth != null) {
                this.depthImage = sharedDepth[0];
                this.depthMemory = VK_NULL_HANDLE;
                this.depthView = sharedDepth[2];
            } else {
                long[] depth = this.host.allocateImage(false, requestedWidth, requestedHeight,
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
            this.generation = this.host.nextPairGeneration();
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
    public void clear(VkCommandBuffer command) {
        // A recorded clear supersedes a deferred one: the two are the same clear, and leaving the flag set would
        // make the next pass clear an image it does not attach.
        this.deferredClearPending = false;
        this.host.checkAlive();
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
    public static final boolean PAIR_CLEAR_IN_PASS = true;

    /** Whether a clear is waiting for the pass that follows it; see {@link #PAIR_CLEAR_IN_PASS}. */
    private boolean deferredClearPending;
    private final float[] deferredClearColour = new float[4];
    private float deferredClearDepth;

    /** {@return whether a clear is waiting for the pass that follows it}. */
    public boolean hasDeferredClear() {
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
    public void deferClear(VkCommandBuffer command) {
        this.host.checkAlive();
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
    public static boolean celtium$deferredClearNeedsDepthBarrier(int depthLayout) {
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
    public static int celtium$deferredClearExtent(int passExtent, int pairExtent) {
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
    public boolean recordDeferredClearAttachments(VkCommandBuffer command, int width, int height) {
        return recordDeferredClearAttachments(command, width, height, 1);
    }

    /**
     * The deferred clear for a pass with {@code colorAttachments} colour attachments: glClear clears every
     * draw buffer with the one latched colour, so a multi-target pass gets one clear attachment per colour
     * slot it attaches, then the depth. The count is clamped to the tuple this pair actually holds - a clear
     * attachment naming a slot the pass does not attach is a validation error, and the caller passes the
     * count of the pass it began, which the window sized from this same tuple.
     */
    public boolean recordDeferredClearAttachments(VkCommandBuffer command, int width, int height,
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
    public void clearWith(VkCommandBuffer command, int mask, float[] rgba, double depthValue) {
        this.deferredClearPending = false;
        this.host.checkAlive();
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
     * ({@code GlStateManager.clear(256)}; see {@code GllessFrameTarget#clearOffTarget}).
     * <p>
     * The colour is cleared once, on the pair's first use, so the first frame does not sample undefined content;
     * after that it accumulates, exactly as the real default framebuffer does. The barrier the colour needs is the
     * hand-back of the previous frame's attachment writes, not a transfer-write barrier: nothing transferred into it
     * this frame.
     */
    public void clearDepthOnly(VkCommandBuffer command) {
        this.deferredClearPending = false;
        this.host.checkAlive();
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
        int queueFamily = context().queueFamilyIndex();
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
    public String selfTest(VkCommandBuffer command) {
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
    public int depthLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    /**
     * The shared depth store key this pair's depth image is borrowed from, or 0 when the depth is the
     * pair's own. GL keys depth storage by the texture — every Iris shadow framebuffer attaches the same
     * mainDepth texture — while a pair's depth is its own, so without this the shadow depth fragments
     * across the pairs of whichever family members were bound and there is no single image to copy from
     * or sample. A borrowed image is owned by the store: {@link #retire()} and {@link #release()} hand
     * back the reference, never the handles, and the layout record lives in the store entry, because two
     * pairs sharing one image cannot each keep a private belief about where it is.
     */
    public long sharedDepthKey;

    /** {@return where this pair's depth image actually is}, the store's record for a borrowed image. */
    public int depthLayout() {
        if (this.sharedDepthKey != 0L && sharedDepthStore().contains(this.sharedDepthKey)) {
            return sharedDepthStore().layout(this.sharedDepthKey);
        }
        return this.depthLayout;
    }

    /** Records where this pair's depth image has been moved to, in the store for a borrowed image. */
    public void depthLayout(int layout) {
        if (this.sharedDepthKey != 0L && sharedDepthStore().contains(this.sharedDepthKey)) {
            sharedDepthStore().noteLayout(this.sharedDepthKey, layout);
            return;
        }
        this.depthLayout = layout;
    }

    /**
     * Records where this pair's depth image has been moved to by a barrier in {@code command}, keeping both
     * the shared depth store and any published {@link VulkanGlTexture} foreign-layout ledger in step.
     */
    public void depthLayout(VkCommandBuffer command, int layout) {
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
        int images = celtium$transferImages(wantColor, wantDepth,
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
        int queueFamily = context().queueFamilyIndex();
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

                vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, colorBarriers);
                // The depth barrier rides in its own call when the depth already holds content: TOP_OF_PIPE
                // supports no access flags at all, so a barrier that names the previous writes (the shared
                // case above) under it is exactly VUID-vkCmdPipelineBarrier-pImageMemoryBarriers-02819 -
                // measured on the first leg whose pairs borrowed a written shared depth (leg 16, item 333).
                // ALL_COMMANDS as the source stage covers every stage those writes could have come from.
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
    public void recordAttachAcquire(VkCommandBuffer command) {
        this.host.checkAlive();
        if (command == null || command.address() == 0L) {
            throw new IllegalArgumentException(this.label + " pair acquire needs a command buffer");
        }
        if (!exists()) {
            throw new IllegalStateException("ensure must run before the " + this.label + " pair is acquired");
        }
        int queueFamily = context().queueFamilyIndex();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);
            int colorFrom = colorLayout();
            VkImageMemoryBarrier colorBarrier = barriers.get(0);
            colorBarrier.sType$Default()
                    .image(this.colorImage)
                    .oldLayout(colorFrom).newLayout(COLOR_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(layoutSourceAccess(colorFrom))
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                            | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
            colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

            int depthFrom = depthLayout();
            VkImageMemoryBarrier depthBarrier = barriers.get(1);
            depthBarrier.sType$Default()
                    .image(this.depthImage)
                    .oldLayout(depthFrom).newLayout(DEPTH_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(queueFamily).dstQueueFamilyIndex(queueFamily)
                    .srcAccessMask(layoutSourceAccess(depthFrom))
                    .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

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
        int queueFamily = context().queueFamilyIndex();
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
    public void retire() {
        VkDevice device = context().device();
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
        this.generation = this.host.nextPairGeneration();
        // The handles are gone from this pair; release() now only forgets the size and layouts.
        release();
    }

    /**
     * Releases the pair and forgets its size, so the next {@link #ensure} builds it afresh. Safe to call with
     * nothing created and safe to call twice. Frees in place, so it is only for images no command buffer can
     * still name: {@code delete()} on the owning frame target after the GPU settled, and a failed {@link #ensure} that never recorded.
     * A pair the session may still be drawing with goes through {@link #retire()}.
     */
    public void release() {
        VkDevice device = context().device();
        // A borrowed swapchain colour image is forgotten, never destroyed: it is the present engine's, and
        // this pair's release (which retire() also runs) must not hand it to a destroy call.
        releaseBorrowedColorImage();
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
    public String summary() {
        return this.label + "(size=" + (exists() ? this.width + "x" + this.height : "none")
                + " creations=" + this.creations + " clears=" + this.clears
                + " selfTests=" + this.selfTests + " acquired=" + this.acquired
                + " colors=" + colorCount() + ")";
    }
}
