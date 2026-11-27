package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransientAttachmentSlabTest {

    private static final class RecordingBacking implements TransientAttachmentSlab.Backing {
        final List<Long> allocations = new ArrayList<>();
        final List<Long> frees = new ArrayList<>();
        private long nextHandle = 100;

        @Override
        public long allocate(long bytes) {
            this.allocations.add(bytes);
            return this.nextHandle++;
        }

        @Override
        public void free(long handle) {
            this.frees.add(handle);
        }
    }

    @Test
    void allocatorAlignsAndSplitsRanges() {
        TransientAttachmentSlab.OffsetAllocator allocator =
                new TransientAttachmentSlab.OffsetAllocator(1024);
        assertEquals(0L, allocator.tryAllocate(100, 1));
        assertEquals(256L, allocator.tryAllocate(100, 256));
        assertEquals(100L, allocator.tryAllocate(50, 1));
        assertNull(allocator.tryAllocate(2048, 1));
    }

    @Test
    void allocatorCoalescesReleasedRanges() {
        TransientAttachmentSlab.OffsetAllocator allocator =
                new TransientAttachmentSlab.OffsetAllocator(512);
        Long a = allocator.tryAllocate(128, 1);
        Long b = allocator.tryAllocate(128, 1);
        allocator.release(a, 128);
        allocator.release(b, 128);
        assertEquals(0L, allocator.tryAllocate(512, 1));
    }

    @Test
    void slabStartsAtZeroBytesAndGrowsWithOneAllocation() {
        RecordingBacking backing = new RecordingBacking();
        try (TransientAttachmentSlab slab = new TransientAttachmentSlab(backing)) {
            assertEquals(0, slab.allocatedBytes());
            TransientAttachmentSlab.Allocation first = slab.acquire(1024, 256);
            assertEquals(0, first.offset());
            // One backing allocation, at the first-allocation size - not one per acquire.
            assertEquals(List.of(TransientAttachmentSlab.FIRST_ALLOCATION_BYTES), backing.allocations);
            slab.acquire(2048, 256);
            assertEquals(1, backing.allocations.size());
            assertEquals(TransientAttachmentSlab.FIRST_ALLOCATION_BYTES, slab.allocatedBytes());
        }
    }

    @Test
    void slabRefusesToGrowWhileAllocationsAreLive() {
        RecordingBacking backing = new RecordingBacking();
        try (TransientAttachmentSlab slab = new TransientAttachmentSlab(backing)) {
            slab.acquire(1024, 1);
            assertThrows(IllegalStateException.class,
                    () -> slab.acquire(TransientAttachmentSlab.FIRST_ALLOCATION_BYTES + 1, 1));
        }
    }

    @Test
    void slabRegrowsBetweenFramesAndFreesTheOldBacking() {
        RecordingBacking backing = new RecordingBacking();
        try (TransientAttachmentSlab slab = new TransientAttachmentSlab(backing)) {
            TransientAttachmentSlab.Allocation first = slab.acquire(1024, 1);
            slab.release(first);
            slab.acquire(TransientAttachmentSlab.FIRST_ALLOCATION_BYTES + 4096, 1);
            assertEquals(2, backing.allocations.size());
            assertEquals(1, backing.frees.size());
        }
    }
}
