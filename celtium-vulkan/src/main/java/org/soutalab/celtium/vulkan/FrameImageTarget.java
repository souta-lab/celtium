package org.soutalab.celtium.vulkan;

import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * The image a frame's content lands in. The one implementation is {@link GllessFrameTarget}, which owns the
 * images outright when no GL context exists.
 * <p>
 * There is no semaphore contract on this interface (Stage 2 submit unification): the wait/done binary
 * semaphore pair the frame's submissions used to carry synchronized the renderer with itself on one queue,
 * where submission order already gives the same ordering, so the pair and its signal-only / wait-only queue
 * packets were removed rather than carried. A frame's ordering is the queue's own, and its completion is the
 * frame timeline's.
 */
public interface FrameImageTarget {
    /**
     * {@return the swapchain image this frame is borrowing as its colour target}, or {@code null} when the
     * frame renders into an owned image.
     * <p>
     * The direct present path (design section 2.5, lanechat #8830 (3)(4)): the frame target borrows one
     * swapchain image per frame from the context's {@code PresentEngine} and every pass attaches it through
     * {@link #colorImage()} / {@link #colorView()} exactly as it attached the owned image, so the finished
     * frame is already in the swapchain when the frame ends and the present is a layout transition and a
     * {@code vkQueuePresentKHR}, never a blit. The handle is the lender's: it is invalidated by the present
     * that ends the borrow, and a caller must not destroy the image or its view. The default is the owned
     * shape - no borrow - for a target with no lender.
     */
    default org.soutalab.celtium.vulkan.hal.PresentEngine.BorrowedImage borrowedPresentImage() {
        return null;
    }

    /**
     * Ends the frame's borrow of a swapchain image: the target forgets the handle and destroys nothing
     * (the image and its view are the lender's). Called by the present path once the present that ends the
     * borrow has been attempted, and when a borrow is dropped unpresented.
     */
    default void releasePresentBorrow() {
    }

    /**
     * {@return the acquire semaphore the next submission touching the frame's colour image must wait on},
     * or {@code VK_NULL_HANDLE} when no wait is owed. Consume-on-read: a borrow's acquire is waited on
     * exactly once, by the first submission recorded after the borrow, so only that reader is handed the
     * semaphore; every later reader gets {@code VK_NULL_HANDLE}.
     */
    default long presentAcquireSemaphore() {
        return org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
    }
    void beginFrame();

    /** {@link #beginFrame()} variant for the composite moment, when the game framebuffer is not the bound one. */
    void beginFrameForPresent();

    /** Closes the frame. */
    void endFrame();

    /** Drops an open frame without a present; the reason is recorded for the debug report. */
    void abandonFrame();

    /** Drops an open frame without a present; the reason is recorded for the debug report. */
    void abandonFrame(String reason);

    /** The framebuffer object the game's main framebuffer may adopt, or 0 - always 0 under the owned target. */
    int adoptableFramebuffer(int width, int height);

    /** Preflight of the current bound framebuffer against the bridge contract; null means no violation. */
    String currentFramebufferViolation();

    /** The colour image this frame's passes render into, as the {@code long} Vulkan calls take. */
    long colorImage();

    /** The colour image view for framebuffer construction. */
    long colorView();

    /** The depth image. */
    long depthImage();

    /** The depth image view. */
    long depthView();

    /** {@return the layout the depth image rests in between passes}, for a readback that must name it. */
    default int depthLayout() {
        return org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
    }

    /** The layout the colour image is currently in, as far as this class's bookkeeping knows. */
    int colorImageLayout();

    /** Records the layout a caller left the colour image in. */
    void noteColorImageLayout(int layout);

    /** Records the commands that make the images usable for this frame's rendering. */
    void recordAcquireBarriers(VkCommandBuffer commandBuffer);

    /** Records the commands that hand the images over after this frame's rendering. */
    void recordReleaseBarriers(VkCommandBuffer commandBuffer);

    /** Whether the target still matches the given size. */
    boolean matches(int width, int height);

    /** The first error seen entering a frame, or null. */
    String firstEntryGlError();

    /** The shared colour GL texture name, or 0 under the owned target. */
    int sharedColorTexture();

    /** The shared depth GL texture name, or 0 under the owned target. */
    int sharedDepthTexture();

    /** Whether the framebuffer object is the shared one; always false under the owned target. */
    boolean isSharedFramebuffer(int framebufferObject);

    /** Records that the game's framebuffer has been pointed at the shared images. */
    void noteGameFramebufferAdopted();

    /** The per-phase timing summary, for the debug report. */
    String takeTimingSummary();

    /** Releases every owned resource. */
    void delete();
}
