package org.soutalab.celtium.forge.terrain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The build scheduler's shape: the platform pool keeps at least two threads (design section
 * 2.4: {@code cores - 2}, minimum 2), builds complete off the calling thread, and the I/O side
 * runs on virtual threads.
 */
class TerrainBuildSchedulerTest {

    @Test
    void poolKeepsAtLeastTwoThreads() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainBuildScheduler(1));
        try (TerrainBuildScheduler scheduler = new TerrainBuildScheduler(2)) {
            assertEquals(2, scheduler.meshThreads());
        }
        try (TerrainBuildScheduler scheduler = new TerrainBuildScheduler()) {
            assertTrue(scheduler.meshThreads() >= 2);
            assertEquals(Math.max(2, Runtime.getRuntime().availableProcessors() - 2),
                    scheduler.meshThreads());
        }
    }

    @Test
    void buildRunsOnThePoolAndReturnsBuffers() throws Exception {
        WorldSectionSnapshot snap = new WorldSectionSnapshot();
        snap.fill(0, 0, 0);
        java.util.Arrays.fill(snap.skyLight(), (byte) 15);
        snap.states()[WorldSectionSnapshot.index(8, 8, 8)] = 1;
        try (TerrainBuildScheduler scheduler = new TerrainBuildScheduler(2)) {
            MeshBuffers out = scheduler.build(snap, new SectionMesherTest.Stub(),
                    MesherOptions.PACK_OFF).get(30, TimeUnit.SECONDS);
            assertEquals(6, out.axisTotal());
            scheduler.releaseBuffers(out);
            // A second build reuses the machinery and still produces the section.
            MeshBuffers again = scheduler.build(snap, new SectionMesherTest.Stub(),
                    MesherOptions.PACK_OFF).get(30, TimeUnit.SECONDS);
            assertEquals(6, again.axisTotal());
            scheduler.releaseBuffers(again);
        }
    }

    @Test
    void ioRunsOnVirtualThreads() throws Exception {
        try (TerrainBuildScheduler scheduler = new TerrainBuildScheduler(2)) {
            Boolean virtual = scheduler.io(() -> Thread.currentThread().isVirtual())
                    .get(30, TimeUnit.SECONDS);
            assertTrue(virtual);
        }
    }
}
