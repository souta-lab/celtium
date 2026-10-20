package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The push cache's elision decision has to compare the bytes the caller actually handed it.
 * <p>
 * The cache keeps a duplicate of the last buffer to avoid allocating one per draw, and the reuse rule is what issue #385
 * was about: an earlier version rebuilt the duplicate only when the directness changed, so a second buffer of the same
 * kind was compared through a view of the <em>first</em> buffer's memory. Every one of these cases failed there, and the
 * consequence was not a slow path - it was a skipped {@code vkCmdPushConstants}, which drops the matrix, colour, alpha
 * reference or light coordinate the draw was about to use.
 */
class VulkanPushCacheReuseTest {

    @Test
    @DisplayName("a changed constant in a different buffer is pushed")
    void differentBufferIsPushed() {
        VulkanPushCache cache = new VulkanPushCache();
        ByteBuffer first = ByteBuffer.allocate(16);
        first.putInt(0, 1);
        ByteBuffer second = ByteBuffer.allocate(16);
        second.putInt(0, 2);

        cache.remember(1L, 2L, first);
        assertFalse(cache.shouldPush(1L, 2L, first), "the same bytes in the same buffer are elided");
        assertTrue(cache.shouldPush(1L, 2L, second), "a different buffer with different bytes must be pushed");
    }

    @Test
    @DisplayName("a wider block in a different buffer does not throw")
    void widerBlockIsPushedNotThrown() {
        VulkanPushCache cache = new VulkanPushCache();
        ByteBuffer narrow = ByteBuffer.allocateDirect(4);
        narrow.putInt(0, 7);
        ByteBuffer wide = ByteBuffer.allocateDirect(224);
        wide.putInt(0, 7);

        cache.remember(1L, 2L, narrow);
        assertDoesNotThrow(() -> cache.shouldPush(1L, 2L, wide),
                "the held duplicate cannot express a wider range, so the cache has to take a fresh one");
        assertTrue(cache.shouldPush(1L, 2L, wide), "the block is longer, so it differs");
    }

    @Test
    @DisplayName("two direct buffers of the same size are compared by content")
    void directBuffersOfTheSameSizeAreCompared() {
        VulkanPushCache cache = new VulkanPushCache();
        ByteBuffer first = ByteBuffer.allocateDirect(32);
        first.putInt(24, 11);
        ByteBuffer second = ByteBuffer.allocateDirect(32);
        second.putInt(24, 12);

        cache.remember(1L, 2L, first);
        assertTrue(cache.shouldPush(1L, 2L, second),
                "same allocation size, different content: the duplicate must not be reused across the two");
    }

    @Test
    @DisplayName("a slice is compared over its own range, not the whole backing array")
    void sliceRangeIsRespected() {
        VulkanPushCache cache = new VulkanPushCache();
        ByteBuffer backing = ByteBuffer.allocate(64);
        backing.putInt(0, 1);
        backing.putInt(32, 2);

        ByteBuffer low = backing.duplicate();
        low.position(0).limit(4);
        ByteBuffer high = backing.duplicate();
        high.position(32).limit(36);

        cache.remember(1L, 2L, low);
        assertTrue(cache.shouldPush(1L, 2L, high),
                "both slices share one backing array; the compared range is what decides");
    }

    @Test
    @DisplayName("another command buffer or layout always pushes")
    void newCommandBufferOrLayoutPushes() {
        VulkanPushCache cache = new VulkanPushCache();
        ByteBuffer block = ByteBuffer.allocate(8);
        block.putLong(0, 5L);

        cache.remember(1L, 2L, block);
        assertFalse(cache.shouldPush(1L, 2L, block), "unchanged");
        assertTrue(cache.shouldPush(9L, 2L, block), "a new command buffer starts empty");
        assertTrue(cache.shouldPush(9L, 3L, block), "a new pipeline layout starts empty");
    }

    @Test
    @DisplayName("interleaving two buffers keeps both decisions correct")
    void interleavedBuffersStayCorrect() {
        VulkanPushCache cache = new VulkanPushCache();
        ByteBuffer left = ByteBuffer.allocate(16);
        left.putInt(0, 1);
        ByteBuffer right = ByteBuffer.allocate(16);
        right.putInt(0, 2);

        cache.remember(1L, 1L, left);
        assertTrue(cache.shouldPush(1L, 1L, right), "different bytes");
        cache.remember(1L, 1L, right);
        assertTrue(cache.shouldPush(1L, 1L, left), "and back again: the duplicate followed neither by accident");
        cache.remember(1L, 1L, left);
        assertFalse(cache.shouldPush(1L, 1L, left), "the stored block is the one just pushed");
    }
}
