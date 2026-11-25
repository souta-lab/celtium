package org.soutalab.celtium.vulkan.batch;

import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.VulkanBuffer;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

/**
 * The device side of {@link SharedQuadIndex}: one index buffer holding the pattern for the full
 * {@link SharedQuadIndex#MAX_QUADS} quads, created once at startup and bound by every immediate
 * draw the {@link ImmediateBatcher} plans. The upload rides the context's deferred upload batch,
 * so this must be constructed before the first frame whose batch should carry it (the startup
 * path, alongside the pipeline prewarm).
 */
public final class SharedQuadIndexBuffer {

    private final VulkanBuffer buffer;

    public SharedQuadIndexBuffer(VulkanContext context) {
        int bytes = SharedQuadIndex.byteSizeForQuads(SharedQuadIndex.MAX_QUADS);
        this.buffer = new VulkanBuffer(context, bytes, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        ByteBuffer pattern = MemoryUtil.memAlloc(bytes);
        try {
            SharedQuadIndex.fill(pattern, SharedQuadIndex.MAX_QUADS);
            pattern.flip();
            this.buffer.write(0, MemoryUtil.memAddress(pattern), bytes);
        } finally {
            MemoryUtil.memFree(pattern);
        }
    }

    /** The raw {@code VkBuffer} handle to bind with {@code VK_INDEX_TYPE_UINT32}. */
    public long buffer() {
        return this.buffer.buffer();
    }

    public void delete() {
        this.buffer.delete();
    }
}
