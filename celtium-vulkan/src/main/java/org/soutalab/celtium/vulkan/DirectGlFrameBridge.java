package org.soutalab.celtium.vulkan;

import org.lwjgl.vulkan.VkCommandBuffer;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;

/**
 * The frame seat the Lane 3 sink records into: while a frame's unified command buffer is open and
 * its main rendering is active, the frame owner publishes the buffer and the main (swapchain,
 * registry id 0) targets here, and the {@link VulkanDirectGlSink} records converter draws into
 * that same buffer - the "same FrameCoordinator" the plan's interface-3 contract (clause 3)
 * requires, so a mod's direct-GL draw lands inside the frame it was issued in, ordered with
 * everything else the frame records.
 * <p>
 * Ownership: exactly one writer (the frame owner, today the terrain backend's frame begin/finish;
 * the fixed four-stage core takes the seat over at S3-b), any number of readers on the render
 * thread. When nothing is bound, sink draws are counted and dropped by the sink - never recorded
 * into a stale buffer.
 */
public final class DirectGlFrameBridge {

    /**
     * Images of the active main rendering. Color is slot zero. Both images are single-sampled and
     * support transfer source and destination usage. The layouts must match the owner's rendering.
     * The owner stores attachments when rendering ends so the sink can resume with LOAD.
     */
    public record MainImages(long colorImage, int colorLayout, long depthImage, int depthLayout,
                             boolean stencilAttached) {
    }

    private record Frame(VkCommandBuffer commandBuffer, ActiveRenderTargets targets, MainImages images) {
    }

    private static volatile Frame frame;

    private DirectGlFrameBridge() {
    }

    /** Publishes the open frame for drawing and in-place clears. */
    public static void bindFrame(VkCommandBuffer buffer, ActiveRenderTargets targets) {
        bindFrame(buffer, targets, null);
    }

    /** Publishes the open frame and the images needed for transfers. */
    public static void bindFrame(VkCommandBuffer buffer, ActiveRenderTargets targets, MainImages images) {
        frame = new Frame(buffer, targets, images);
    }

    /** Withdraws the frame at its boundary. */
    public static void unbindFrame() {
        frame = null;
    }

    public static boolean bound() {
        return commandBuffer() != null;
    }

    public static VkCommandBuffer commandBuffer() {
        Frame current = frame;
        return current == null ? null : current.commandBuffer();
    }

    public static ActiveRenderTargets mainTargets() {
        Frame current = frame;
        return current == null ? null : current.targets();
    }

    static MainImages mainImages() {
        Frame current = frame;
        return current == null ? null : current.images();
    }
}
