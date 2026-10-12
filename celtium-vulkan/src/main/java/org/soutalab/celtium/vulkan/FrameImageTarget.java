package org.soutalab.celtium.vulkan;

import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * The image a frame's content lands in and the synchronization its submissions take. Two implementations exist:
 * {@link VulkanFrameTarget}, which shares the images with OpenGL and synchronizes them through the GL↔Vulkan
 * semaphore pair, and {@link GllessFrameTarget}, which owns the images outright when no GL context exists - its
 * semaphores are signaled and consumed by Vulkan submissions alone, so this contract stays identical either way.
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
     * {@return whether this target's frames take part in the wait/done semaphore contract}.
     * <p>
     * The pair exists so two producers meet on the queue: under the shared target, GL's signal is what the frame's
     * first submission waits on. Under the owned target there is no second producer - every signal and every wait
     * is one of our own submissions on one queue, where submission order already gives the same ordering - so the
     * three queue packets the contract costs per stage (beginFrame's signal, the work submission's semaphore pair,
     * endFrame's consume) buy nothing. {@link GllessFrameTarget} reports false by default;
     * {@code -Dceltium.vulkan.gllessFrameSemaphores=true} keeps the pair anyway.
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
