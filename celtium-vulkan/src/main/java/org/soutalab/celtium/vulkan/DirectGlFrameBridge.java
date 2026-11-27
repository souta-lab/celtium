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

    private static volatile VkCommandBuffer commandBuffer;
    private static volatile ActiveRenderTargets mainTargets;

    private DirectGlFrameBridge() {
    }

    /** Publishes the open frame: its command buffer and its main rendering targets (id 0). */
    public static void bindFrame(VkCommandBuffer buffer, ActiveRenderTargets targets) {
        mainTargets = targets;
        commandBuffer = buffer;
    }

    /** Withdraws the frame at its boundary; later sink draws drop until the next bind. */
    public static void unbindFrame() {
        commandBuffer = null;
        mainTargets = null;
    }

    /** Whether a frame is currently bound. */
    public static boolean bound() {
        return commandBuffer != null;
    }

    /** The bound frame's command buffer, or {@code null} when unbound. */
    public static VkCommandBuffer commandBuffer() {
        return commandBuffer;
    }

    /** The bound frame's main targets (the swapchain, registry id 0), or {@code null}. */
    public static ActiveRenderTargets mainTargets() {
        return mainTargets;
    }
}
