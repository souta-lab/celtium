package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The staging pool's structural claims, without a device: a slice is handed out once, is never
 * handed out again while its batch is in flight, and becomes reusable exactly when the timeline
 * completed value reaches the batch's stamp. This is the reuse judgment that replaces the
 * per-texture fence waits in {@code VulkanGlTexture}: if any of these tests failed, the pool
 * could hand the CPU bytes the GPU is still reading.
 */
class TextureStagingPoolTest {

    @Test
    void allocationsBumpAndHonourAlignment() {
        TextureStagingPool pool = new TextureStagingPool(1024L, 256L);
        assertEquals(0L, pool.allocate(100L));
        assertEquals(256L, pool.allocate(100L), "the second slice starts at the next aligned offset");
        assertEquals(200L, pool.usedBytes());
    }

    @Test
    void exhaustionIsAValueNotAnEvent() {
        TextureStagingPool pool = new TextureStagingPool(512L, 1L);
        assertEquals(0L, pool.allocate(512L));
        assertEquals(-1L, pool.allocate(1L), "a reserved slice is not handed out twice");
    }

    @Test
    void anInFlightSliceIsNotReusedBeforeItsStampCompletes() {
        TextureStagingPool pool = new TextureStagingPool(512L, 1L);
        long offset = pool.allocate(512L);
        pool.markSubmitted(offset, 5L);
        assertEquals(-1L, pool.allocate(1L));
        pool.releaseCompleted(4L);
        assertEquals(-1L, pool.allocate(1L), "stamp 5 has not completed at completed value 4");
        pool.releaseCompleted(5L);
        assertEquals(0L, pool.allocate(512L), "the slice is reusable once its stamp completes");
    }

    @Test
    void releasedNeighboursCoalesceIntoOneRun() {
        TextureStagingPool pool = new TextureStagingPool(1024L, 1L);
        long a = pool.allocate(256L);
        long b = pool.allocate(256L);
        long c = pool.allocate(512L);
        pool.markSubmitted(a, 1L);
        pool.markSubmitted(b, 2L);
        pool.markSubmitted(c, 3L);
        pool.releaseCompleted(2L);
        assertEquals(512L, pool.largestFreeRun(), "the two released neighbours form one run");
        assertEquals(0L, pool.allocate(512L));
        assertEquals(3L, pool.oldestInFlightValue(), "only the third batch is still in flight");
        pool.releaseCompleted(3L);
        assertEquals(0L, pool.oldestInFlightValue());
        assertTrue(pool.hasOutstanding(), "the re-allocated slice is still outstanding");
        assertEquals(512L, pool.freeBytes());
    }

    @Test
    void oldestInFlightValueIsTheSmallestStamp() {
        TextureStagingPool pool = new TextureStagingPool(1024L, 1L);
        long a = pool.allocate(128L);
        long b = pool.allocate(128L);
        pool.markSubmitted(a, 7L);
        pool.markSubmitted(b, 3L);
        assertEquals(3L, pool.oldestInFlightValue());
        pool.releaseCompleted(3L);
        assertEquals(7L, pool.oldestInFlightValue());
        pool.releaseCompleted(7L);
        assertEquals(0L, pool.oldestInFlightValue());
        assertFalse(pool.hasOutstanding());
    }

    @Test
    void invalidArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new TextureStagingPool(0L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new TextureStagingPool(64L, 0L));
        TextureStagingPool pool = new TextureStagingPool(64L, 1L);
        assertThrows(IllegalArgumentException.class, () -> pool.allocate(0L));
        assertThrows(IllegalArgumentException.class, () -> pool.allocate(65L),
                "a slice larger than the pool is refused, not waited for");
        assertThrows(IllegalStateException.class, () -> pool.markSubmitted(0L, 1L),
                "no reserved slice exists at offset 0 before any allocation");
        long offset = pool.allocate(64L);
        assertThrows(IllegalStateException.class, () -> pool.markSubmitted(offset + 1L, 1L));
        pool.markSubmitted(offset, 1L);
        assertTrue(pool.hasOutstanding());
    }
}
