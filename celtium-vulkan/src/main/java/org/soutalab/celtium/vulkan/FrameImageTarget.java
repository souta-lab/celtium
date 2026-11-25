package org.soutalab.celtium.vulkan;

import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * The image a frame's content lands in and the synchronization its submissions take. The one implementation,
 * {@link GllessFrameTarget}, owns the images outright: no GL context exists to hand them to, so there is no
 * second producer and the semaphore side of this contract is empty for it - the defaults below are the safe
 * side for an implementation that has no such pair.
 */
public interface FrameImageTarget {
    /**
     * {@return whether the bridge's done semaphore already has a signal in flight}.
     * <p>
     * A binary semaphore may only be signalled again after its previous signal has been waited on. The present path
     * can present twice before an {@code endFrame} consumed the first signal, and the second signal is
     * {@code VUID-vkQueueSubmit-pSignalSemaphores-00067} - measured as ten messages in one leg on the present path,
     * with the semaphore traced to this pair rather than the swapchain's. Defaults are the safe side for an
     * implementation that has no such pair.
     */
    default boolean doneSemaphoreSignalOutstanding() {
        return false;
    }

    /** Records that a submission has signalled the done semaphore and nothing has waited on it yet. */
    default void noteDoneSemaphoreSignalled() {
    }

    /**
     * {@return the present-wait semaphore for a swapchain image}.
     * <p>
     * The present-wait semaphore must be sized by the swapchain image count and indexed by the acquired image,
     * because the presentation engine knows only that index. Defaults fall back to the frame-close semaphore for an
     * implementation with no per-image array.
     */
    default long presentDoneSemaphore(int imageIndex) {
        return doneSemaphore();
    }

    /** Records which done semaphore a producer signalled, so the wait-only submission waits on that one. */
    default void noteDoneSignalled(long semaphore) {
    }

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

    /**
     * {@return whether this target's frames take part in the wait/done semaphore contract}.
     * <p>
     * The pair exists so two producers meet on the queue: under the shared target, GL's signal is what the frame's
     * first submission waits on. Under the owned target there is no second producer - every signal and every wait
     * is one of our own submissions on one queue, where submission order already gives the same ordering - so the
     * three queue packets the contract costs per stage (beginFrame's signal, the work submission's semaphore pair,
     * endFrame's consume) buy nothing. {@link GllessFrameTarget} reports false: the pair and its
     * {@code celtium.vulkan.frameSemaphores} opt-in were removed with the new architecture.
     */
    default boolean needsFrameSemaphores() {
        return true;
    }


    /**
     * Marks the frame open: under the shared target the game framebuffer's content is copied in and the
     * GL-to-Vulkan semaphore is signaled; under the owned target the same semaphore is signaled by a submission.
     */
    void beginFrame();

    /** {@link #beginFrame()} variant for the composite moment, when the game framebuffer is not the bound one. */
    void beginFrameForPresent();

    /** Closes the frame: under the shared target GL is made to wait on the Vulkan-to-GL semaphore. */
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

    /** The binary semaphore a frame's first submission must wait on (GL's content arriving / beginFrame's signal). */
    long waitSemaphore();

    /** The binary semaphore a frame's last submission must signal (handover to the consumer). */
    long doneSemaphore();

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
