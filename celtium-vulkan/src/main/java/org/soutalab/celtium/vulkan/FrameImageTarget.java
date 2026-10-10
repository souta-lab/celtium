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
