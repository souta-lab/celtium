package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.util.List;

import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
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
    private final VkQueue queue;
    private long submits;
    private long presents;

    public PresentEngine(VkQueue queue) {
        this.queue = queue;
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
