package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;

/**
 * The synchronous present path of the new architecture: the frame's final submit and its present, run directly
 * on the calling (render) thread at the end of the frame.
 * <p>
 * This replaces the asynchronous presenter worker. Present runs inline at the end of the render thread's frame,
 * so the verdict - the {@code VkResult} of {@code vkQueuePresentKHR} - is final in the return value of
 * {@link #submitAndPresent} the moment the call returns. There is no worker thread whose stored result a later
 * frame re-reads, and therefore no stale verdict from an earlier present being acted on as if it belonged to
 * this one: a caller that sees {@code VK_ERROR_OUT_OF_DATE_KHR} here knows it was this frame's present that
 * found the swapchain out of date.
 * <p>
 * No fence is created or waited on here. Completion of the submitted work is the caller's business, tracked on
 * the shared {@link FrameTimeline}: this class only orders the present behind the submit's signal semaphore and
 * reports what the presentation engine answered.
 * <p>
 * The static policy methods are pure and carry no Vulkan state, so the swapchain decisions they encode can be
 * unit-tested without a device.
 */
public final class PresentEngine {
    private final VkDevice device;
    private final VkQueue queue;
    private long submits;
    private long presents;

    /**
     * The swapchain generation this engine currently owns, and the handles it may lend out. Ownership of the
     * swapchain images lives here and nowhere else (coord ruling, lanechat #8830): a frame target pair never
     * owns a swapchain image, it only borrows one through {@link BorrowedImage} for the span between
     * {@link #acquireNextImage} and {@link #present(BorrowedImage, long)}.
     */
    private long swapchain = VK_NULL_HANDLE;
    private long[] swapchainImages = new long[0];
    private long[] swapchainViews = new long[0];
    /** Bumped by every {@link #adoptSwapchain} / {@link #retireSwapchain}: handles from an older generation are stale. */
    private long generation;
    /** The handles currently lent out and not yet presented or invalidated. */
    private final List<BorrowedImage> liveHandles = new ArrayList<BorrowedImage>();
    /** The result of the most recent {@link #acquireNextImage} call, successful or not. */
    private int lastAcquireResult = VK_SUCCESS;

    public PresentEngine(VkQueue queue) {
        this(null, queue);
    }

    public PresentEngine(VkDevice device, VkQueue queue) {
        this.device = device;
        this.queue = queue;
    }

    /**
     * A swapchain image lent by this engine for one frame: the image, its view, its index and the frame slot
     * it was acquired for. The handle is valid only between its acquire and its present, and only inside the
     * swapchain generation that issued it - a present or a swapchain recreation (resize) invalidates it, and
     * using an invalidated handle is a contract violation, not a stale read. The image and view stay owned by
     * the engine's swapchain generation; nothing that borrows a handle may destroy them.
     */
    public static final class BorrowedImage {
        private final PresentEngine owner;
        private final long image;
        private final long view;
        private final int index;
        private final int frameSlot;
        private final long generation;
        private boolean valid = true;

        /** Package-private so the lending contract can be pinned in tests without a device. */
        BorrowedImage(PresentEngine owner, long image, long view, int index, int frameSlot,
                long generation) {
            this.owner = owner;
            this.image = image;
            this.view = view;
            this.index = index;
            this.frameSlot = frameSlot;
            this.generation = generation;
        }

        /** {@return the swapchain image handle} (owned by the issuing engine, never by the borrower). */
        public long image() {
            return this.image;
        }

        /** {@return the image's view}, or {@code VK_NULL_HANDLE} when the generation created none. */
        public long view() {
            return this.view;
        }

        /** {@return the swapchain image index this handle was acquired as}. */
        public int index() {
            return this.index;
        }

        /** {@return the frame slot the caller acquired this image for}. */
        public int frameSlot() {
            return this.frameSlot;
        }

        /** {@return the swapchain generation that issued this handle}. */
        public long generation() {
            return this.generation;
        }

        /** {@return whether this handle may still be drawn into and presented}. */
        public boolean isValid() {
            return this.valid && this.owner.generation == this.generation;
        }

        /** Ends the borrow: after this the handle names nothing its holder may use. */
        private void invalidate() {
            this.valid = false;
        }
    }

    /**
     * Takes ownership of a swapchain generation: {@code swapchain} and its {@code images} (with {@code views},
     * which may be shorter or empty when the generation created no views). Every handle lent from the previous
     * generation is invalidated - a resize retires the images those handles name, so lending them further
     * would hand a borrower a destroyed image. The arrays are copied; the caller keeps no alias into the
     * registry.
     */
    public void adoptSwapchain(long swapchain, long[] images, long[] views) {
        invalidateLiveHandles();
        this.generation++;
        this.swapchain = swapchain;
        this.swapchainImages = images == null ? new long[0] : images.clone();
        this.swapchainViews = views == null ? new long[0] : views.clone();
    }

    /**
     * Hands the current generation back: every outstanding handle is invalidated and the registry forgets the
     * images. Destroying the swapchain and its views stays the caller's work - this engine owns the lending
     * contract, and the recreation path that built the generation is the one that tears it down.
     */
    public void retireSwapchain() {
        invalidateLiveHandles();
        this.generation++;
        this.swapchain = VK_NULL_HANDLE;
        this.swapchainImages = new long[0];
        this.swapchainViews = new long[0];
    }

    private void invalidateLiveHandles() {
        for (BorrowedImage handle : this.liveHandles) {
            handle.invalidate();
        }
        this.liveHandles.clear();
    }

    /** {@return the swapchain this engine currently owns}, or {@code VK_NULL_HANDLE} between generations. */
    public long swapchain() {
        return this.swapchain;
    }

    /** {@return how many images the current generation holds}. */
    public int imageCount() {
        return this.swapchainImages.length;
    }

    /** {@return the current swapchain generation}; a handle whose generation differs is stale. */
    public long generation() {
        return this.generation;
    }

    /** {@return the result of the most recent acquire}, including a failed one that lent nothing. */
    public int lastAcquireResult() {
        return this.lastAcquireResult;
    }

    /**
     * Acquires the next image of the owned swapchain and lends it to the caller as a {@link BorrowedImage}.
     * The borrow lasts until {@link #present(BorrowedImage, long)} (or a swapchain recreation) ends it; the
     * caller records the frame's draws into the borrowed image and presents the same handle.
     *
     * @return the borrowed image, or {@code null} when the acquire itself failed - the verdict is then in
     *         {@link #lastAcquireResult()} ({@code VK_ERROR_OUT_OF_DATE_KHR} asks for a recreation, a lost
     *         surface for a surface rebuild; neither lends an image)
     */
    public BorrowedImage acquireNextImage(long acquireSemaphore, int frameSlot) {
        if (this.device == null || this.swapchain == VK_NULL_HANDLE) {
            this.lastAcquireResult = VK_ERROR_OUT_OF_DATE_KHR;
            return null;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer index = stack.mallocInt(1);
            int result = vkAcquireNextImageKHR(this.device, this.swapchain, Long.MAX_VALUE, acquireSemaphore,
                    VK_NULL_HANDLE, index);
            this.lastAcquireResult = result;
            if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                return null;
            }
            int imageIndex = index.get(0);
            if (imageIndex < 0 || imageIndex >= this.swapchainImages.length) {
                this.lastAcquireResult = VK_ERROR_OUT_OF_DATE_KHR;
                return null;
            }
            long view = imageIndex < this.swapchainViews.length ? this.swapchainViews[imageIndex]
                    : VK_NULL_HANDLE;
            BorrowedImage handle = new BorrowedImage(this, this.swapchainImages[imageIndex], view, imageIndex,
                    frameSlot, this.generation);
            this.liveHandles.add(handle);
            return handle;
        }
    }

    /**
     * Presents a borrowed image, waiting on {@code waitSemaphore} (or on nothing when it is
     * {@code VK_NULL_HANDLE}), and ends the borrow: the handle is invalidated whatever the presentation
     * engine answers, because the image returns to the engine's pool the moment it is presented.
     *
     * @return the {@code VkResult} of {@code vkQueuePresentKHR}
     * @throws IllegalStateException when {@code image} is not a live handle of this engine's current
     *         generation - presenting an image twice, or one a resize already retired, is a contract
     *         violation the caller must not paper over
     */
    public int present(BorrowedImage image, long waitSemaphore) {
        checkLiveHandle(image);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack).sType$Default()
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(this.swapchain))
                    .pImageIndices(stack.ints(image.index()));
            if (waitSemaphore != VK_NULL_HANDLE) {
                present.pWaitSemaphores(stack.longs(waitSemaphore));
            }
            this.presents++;
            int result = vkQueuePresentKHR(this.queue, present);
            releaseHandle(image);
            return result;
        }
    }

    /**
     * {@link #submitAndPresent(VkCommandBuffer, long, long, long, int)} against a borrowed image: submits the
     * frame's final command buffer and presents the image it drew into. A failed submit attempts no present
     * and leaves the borrow live (the caller still owns the acquired image and must present or abandon it);
     * once the present is attempted the borrow ends, as in {@link #present(BorrowedImage, long)}.
     *
     * @return the present's {@code VkResult}, or the submit's when the submit failed
     */
    public int submitAndPresent(VkCommandBuffer commandBuffer, long waitSemaphore, long signalSemaphore,
            BorrowedImage image) {
        checkLiveHandle(image);
        long presentsBefore = this.presents;
        int result = submitAndPresent(commandBuffer, waitSemaphore, signalSemaphore, this.swapchain,
                image.index());
        if (this.presents != presentsBefore) {
            releaseHandle(image);
        }
        return result;
    }

    private void checkLiveHandle(BorrowedImage image) {
        if (image == null || image.owner != this || !image.isValid() || !this.liveHandles.contains(image)) {
            throw new IllegalStateException("borrowed swapchain image is not a live handle of this engine's "
                    + "current generation (already presented, or retired by a swapchain recreation)");
        }
    }

    private void releaseHandle(BorrowedImage image) {
        image.invalidate();
        this.liveHandles.remove(image);
    }

    /**
     * {@return the swapchain image count to request}: one more than {@code minImageCount} (design
     * CELTIUM-NEW-ARCH-2026-11-25, section 2.5 - usually three images), clamped to {@code maxImageCount} when
     * the surface reports a finite maximum ({@code maxImageCount > 0}); a maximum of 0 means the surface sets no
     * limit and the count is not clamped.
     */
    public static int desiredImageCount(int minImageCount, int maxImageCount) {
        int desired = minImageCount + 1;
        if (maxImageCount > 0) {
            return Math.min(desired, maxImageCount);
        }
        return desired;
    }

    /**
     * {@return the present mode to request} from {@code available}. With vsync off the preference is
     * {@code VK_PRESENT_MODE_MAILBOX_KHR}, then {@code VK_PRESENT_MODE_IMMEDIATE_KHR}, then
     * {@code VK_PRESENT_MODE_FIFO_KHR}; with vsync on it is {@code VK_PRESENT_MODE_FIFO_RELAXED_KHR}, then
     * {@code VK_PRESENT_MODE_FIFO_KHR}. When none of the preferred modes is available the answer is
     * {@code VK_PRESENT_MODE_FIFO_KHR}, which the specification guarantees every surface offers.
     */
    public static int choosePresentMode(List<Integer> available, boolean vsync) {
        int[] preference = vsync
                ? new int[]{VK_PRESENT_MODE_FIFO_RELAXED_KHR, VK_PRESENT_MODE_FIFO_KHR}
                : new int[]{VK_PRESENT_MODE_MAILBOX_KHR, VK_PRESENT_MODE_IMMEDIATE_KHR, VK_PRESENT_MODE_FIFO_KHR};
        for (int mode : preference) {
            if (available.contains(Integer.valueOf(mode))) {
                return mode;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    /**
     * {@return whether a submit/present {@code result} is fatal}, in the sense that recreating the swapchain
     * cannot recover from it. Success, {@code VK_SUBOPTIMAL_KHR}, {@code VK_ERROR_OUT_OF_DATE_KHR} and
     * {@code VK_ERROR_SURFACE_LOST_KHR} are recoverable: each is answered by recreating the swapchain (and, for a
     * lost surface, the surface). A lost surface in particular is not a lost device - classifying it as fatal
     * device loss would throw away a renderer that a recreation would have restored (ruling R2). Everything
     * else, {@code VK_ERROR_DEVICE_LOST} included, is fatal.
     */
    public static boolean resultIsFatal(int result) {
        return result != VK_SUCCESS
                && result != VK_SUBOPTIMAL_KHR
                && result != VK_ERROR_OUT_OF_DATE_KHR
                && result != VK_ERROR_SURFACE_LOST_KHR;
    }

    /**
     * Submits {@code commandBuffer} - waiting on {@code waitSemaphore} at
     * {@code VK_PIPELINE_STAGE_ALL_COMMANDS_BIT} and signalling {@code signalSemaphore} - and, when the submit
     * succeeds, presents {@code imageIndex} of {@code swapchain} waiting on {@code signalSemaphore}.
     *
     * @return the {@code VkResult} of {@code vkQueuePresentKHR}; when {@code vkQueueSubmit} itself fails, its
     *         result is returned instead and no present is attempted
     */
    public int submitAndPresent(VkCommandBuffer commandBuffer, long waitSemaphore, long signalSemaphore,
            long swapchain, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer))
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(waitSemaphore))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT))
                    .pSignalSemaphores(stack.longs(signalSemaphore));
            this.submits++;
            int submitted = vkQueueSubmit(this.queue, submit, VK_NULL_HANDLE);
            if (submitted != VK_SUCCESS) {
                return submitted;
            }
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(signalSemaphore))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchain))
                    .pImageIndices(stack.ints(imageIndex));
            this.presents++;
            return vkQueuePresentKHR(this.queue, present);
        }
    }

    /** {@return how many presents this engine has attempted} (a failed submit attempts none). */
    public long presents() {
        return this.presents;
    }

    /** {@return how many queue submits this engine has issued}. */
    public long submits() {
        return this.submits;
    }
}
