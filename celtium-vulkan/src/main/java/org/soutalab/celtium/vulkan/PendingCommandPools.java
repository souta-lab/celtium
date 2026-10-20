package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkGetFenceStatus;

import java.util.ArrayDeque;

/**
 * Command pools whose submission has not been confirmed complete, kept until the queue is done with them.
 * <p>
 * {@code vkDestroyCommandPool} requires that no command buffer allocated from it is pending
 * ({@code VUID-commandPool-00041}), and a fence wait that timed out is not evidence that the submission finished - it is
 * evidence that nothing was proven either way. Several one-shot submits used to destroy their pool in a {@code finally}
 * block regardless: the shadow placeholder's image transition in the item and block pipelines (#403), which is on the
 * shaderless lit path because the placeholder exists whether or not the shadow pass is enabled. A timeout there freed a
 * pool the GPU could still be reading.
 * <p>
 * The rule is the one the frame path already follows (#364): a timeout moves ownership here, and only a signalled fence -
 * or an explicit device-loss teardown, when nothing can be completed any more - releases it.
 */
public final class PendingCommandPools {

    /** A pool and the fence that will say when the queue is done with its command buffers. */
    private final ArrayDeque<long[]> pending = new ArrayDeque<long[]>();

    /** Takes ownership of a pool whose submission did not complete within the wait. */
    public void retain(long pool, long fence) {
        this.pending.add(new long[] {pool, fence});
    }

    /** {@return how many pools are still waiting for their submission}. */
    public int retained() {
        return this.pending.size();
    }

    /**
     * Destroys the pools whose fence has signalled, and no others.
     *
     * @return how many were released
     */
    public int reclaim(org.lwjgl.vulkan.VkDevice device) {
        int released = 0;
        while (!this.pending.isEmpty()) {
            long[] entry = this.pending.peek();
            if (vkGetFenceStatus(device, entry[1]) != VK_SUCCESS) {
                return released;
            }
            this.pending.poll();
            vkDestroyFence(device, entry[1], null);
            vkDestroyCommandPool(device, entry[0], null);
            released++;
        }
        return released;
    }

    /**
     * Releases everything without waiting, for a device that is being torn down.
     * <p>
     * Only correct when the device is lost or the whole context is going away: at that point no submission can complete,
     * so holding the pools buys nothing and the destruction is the one the owner is entitled to do anyway.
     */
    public void releaseAllOnDeviceTeardown(org.lwjgl.vulkan.VkDevice device) {
        while (!this.pending.isEmpty()) {
            long[] entry = this.pending.poll();
            vkDestroyFence(device, entry[1], null);
            vkDestroyCommandPool(device, entry[0], null);
        }
    }
}
