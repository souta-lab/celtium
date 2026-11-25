package org.soutalab.celtium.vulkan.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fixed-slab page allocator: no growth, no copies, deferred frees honoured. */
class TerrainSlabAllocatorTest {

    private static TerrainSlabAllocator small() {
        // 16 KiB slabs, 4 KiB pages, at most 2 slabs.
        return new TerrainSlabAllocator(16 * 1024, 2);
    }

    @Test
    void allocationsArePageRoundedAndPacked() {
        TerrainSlabAllocator allocator = small();
        var a = allocator.allocate(100);
        assertNotNull(a);
        assertEquals(0, a.slabId);
        assertEquals(0, a.byteOffset);
        assertEquals(4096, a.reservedBytes);
        var b = allocator.allocate(5000);
        assertNotNull(b);
        assertEquals(4096, b.byteOffset);
        assertEquals(8192, b.reservedBytes);
    }

    @Test
    void aFullSlabAddsAnotherInsteadOfGrowing() {
        TerrainSlabAllocator allocator = small();
        assertNotNull(allocator.allocate(16 * 1024));
        var second = allocator.allocate(4096);
        assertNotNull(second);
        assertEquals(1, second.slabId);
        assertEquals(2, allocator.slabCount());
        // Fill the second slab too: the cap is a hard stop, not a growth path.
        assertNotNull(allocator.allocate(12 * 1024));
        assertNull(allocator.allocate(4096));
    }

    @Test
    void freedPagesAreReusedAndCoalesced() {
        TerrainSlabAllocator allocator = small();
        var a = allocator.allocate(4096);
        var b = allocator.allocate(4096);
        allocator.free(a);
        var c = allocator.allocate(4096);
        assertNotNull(c);
        assertEquals(0, c.byteOffset);
        allocator.free(b);
        allocator.free(c);
        assertEquals(16 * 1024, allocator.freeBytes());
        var whole = allocator.allocate(16 * 1024);
        assertNotNull(whole, "coalesced free space must serve a whole-slab request");
    }

    @Test
    void deferredFreeWaitsForTheTimeline() {
        TerrainSlabAllocator allocator = small();
        var a = allocator.allocate(16 * 1024);
        allocator.freeDeferred(a, 10);
        assertNull(allocator.allocate(4096), "pages must not recycle before their timeline completes");
        allocator.collectReleased(9);
        assertNull(allocator.allocate(4096));
        allocator.collectReleased(10);
        assertNotNull(allocator.allocate(4096));
    }

    @Test
    void oversizeRequestIsRefused() {
        TerrainSlabAllocator allocator = small();
        assertNull(allocator.allocate(16 * 1024 + 4096));
    }
}
