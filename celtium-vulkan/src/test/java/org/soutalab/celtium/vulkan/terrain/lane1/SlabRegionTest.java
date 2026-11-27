package org.soutalab.celtium.vulkan.terrain.lane1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlabRegionTest {

    @Test
    void firstFitAndAlignment() {
        SlabRegion region = new SlabRegion(1024);
        long a = region.allocate(100, 16);
        assertEquals(0L, a);
        long b = region.allocate(100, 256);
        assertEquals(256L, b);
        assertEquals(200L, region.usedBytes());
    }

    @Test
    void deferredFreeBecomesReusableOnlyAfterCollection() {
        SlabRegion region = new SlabRegion(256);
        long a = region.allocate(256, 16);
        assertEquals(0L, a);
        assertEquals(-1L, region.allocate(16, 16));
        region.releaseDeferred(a, 256, 7L);
        assertEquals(-1L, region.allocate(16, 16), "release is deferred until the timeline passes");
        region.collectCompleted(6L);
        assertEquals(-1L, region.allocate(16, 16), "timeline 6 has not passed the release at 7");
        region.collectCompleted(7L);
        assertEquals(0L, region.allocate(256, 16));
    }

    @Test
    void freedNeighboursCoalesce() {
        SlabRegion region = new SlabRegion(512);
        long a = region.allocate(128, 16);
        long b = region.allocate(128, 16);
        long c = region.allocate(128, 16);
        region.releaseDeferred(a, 128, 1L);
        region.releaseDeferred(b, 128, 1L);
        region.releaseDeferred(c, 128, 1L);
        region.collectCompleted(1L);
        assertEquals(0L, region.allocate(512, 16), "three neighbour frees coalesce into the full run");
    }

    @Test
    void fragmentationIsHonest() {
        SlabRegion region = new SlabRegion(384);
        long a = region.allocate(128, 16);
        region.allocate(128, 16);
        region.releaseDeferred(a, 128, 1L);
        region.collectCompleted(1L);
        assertTrue(region.freeBytes() >= 128L);
        assertEquals(-1L, region.allocate(256, 16), "no single run fits 256");
    }
}
