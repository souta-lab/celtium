package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch pool behind {@link VulkanMultiDrawBatch#obtain(int)}: a cached batch's lifetime is one
 * camera-validity interval, so in flight the assembler replaces batches constantly, and each replacement
 * used to allocate the batch's three backing arrays fresh. These tests pin the reuse contract - a deleted
 * batch comes back cleared, only when its slots cover the request, and a double delete cannot put the
 * same instance into the pool twice (which would hand one batch to two owners).
 */
class VulkanMultiDrawBatchPoolTest {

    @org.junit.jupiter.api.BeforeEach
    void emptyPool() {
        VulkanMultiDrawBatch.drainPoolForTest();
    }

    @Test
    void aDeletedBatchIsReusedClearedWhenItsSlotsCoverTheRequest() {
        VulkanMultiDrawBatch first = VulkanMultiDrawBatch.obtain(16);
        first.appendDrawCommand(100, 60, 0L);
        first.appendDrawCommand(200, 30, 0L);
        assertEquals(2, first.size());
        first.delete();

        VulkanMultiDrawBatch second = VulkanMultiDrawBatch.obtain(8);

        assertSame(first, second, "the deleted batch is the one the pool hands back");
        assertEquals(0, second.size(), "a reused batch starts with an empty command list");
        assertEquals(0, second.getIndexBufferSize(), "a reused batch forgets the previous max element count");
        second.appendDrawCommand(7, 12, 0L);
        assertEquals(7, second.baseVertex(0));
        assertEquals(12, second.elementCount(0));
        second.delete();
    }

    @Test
    void aRequestWiderThanThePooledBatchGetsAFreshOne() {
        VulkanMultiDrawBatch small = VulkanMultiDrawBatch.obtain(4);
        small.delete();

        VulkanMultiDrawBatch wide = VulkanMultiDrawBatch.obtain(4096);

        assertNotSame(small, wide, "a pooled batch whose slots do not cover the request is not stretched");
        assertTrue(wide.capacity() > 4096);
        wide.delete();
        // Drain the small one back out so other tests start from an empty pool.
        assertSame(small, VulkanMultiDrawBatch.obtain(4));
    }

    @Test
    void aDoubleDeleteDoesNotPoolTheSameInstanceTwice() {
        VulkanMultiDrawBatch batch = VulkanMultiDrawBatch.obtain(16);
        batch.delete();
        batch.delete();

        VulkanMultiDrawBatch first = VulkanMultiDrawBatch.obtain(16);
        VulkanMultiDrawBatch second = VulkanMultiDrawBatch.obtain(16);

        assertSame(batch, first);
        assertNotSame(first, second,
                "the second obtain must construct, not hand out the already-owned instance again");
        first.delete();
        second.delete();
    }
}
