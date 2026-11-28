package org.soutalab.celtium.forge.terrain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void snapshotPoolRecirculatesThroughABuild() throws Exception {
        try (TerrainBuildScheduler scheduler = new TerrainBuildScheduler(2)) {
            WorldSectionSnapshot snapshot = scheduler.acquireSnapshot();
            snapshot.fill(0, 0, 0);
            // The driver's pattern: the future's completion returns the snapshot.
            java.util.concurrent.CompletableFuture<MeshBuffers> future =
                    scheduler.build(snapshot, new SectionMesherTest.Stub(), MesherOptions.PACK_OFF);
            future.whenComplete((buffers, error) -> scheduler.releaseSnapshot(snapshot));
            future.get(30, TimeUnit.SECONDS);
            assertTrue(acquireEventually(scheduler, snapshot),
                    "the completed build's snapshot comes back out of the pool");
        }
    }

    @Test
    void snapshotReturnsToThePoolOnCancel() throws Exception {
        try (TerrainBuildScheduler scheduler = new TerrainBuildScheduler(2)) {
            WorldSectionSnapshot snapshot = scheduler.acquireSnapshot();
            snapshot.fill(0, 0, 0);
            java.util.concurrent.CompletableFuture<MeshBuffers> future =
                    scheduler.build(snapshot, new SectionMesherTest.Stub(), MesherOptions.PACK_OFF);
            future.whenComplete((buffers, error) -> scheduler.releaseSnapshot(snapshot));
            // Occupy the pool so the cancel lands while queued or done - either way the
            // completion callback is what returns the snapshot (an unload cancels like this).
            future.cancel(false);
            assertTrue(acquireEventually(scheduler, snapshot),
                    "a cancelled build still returns its snapshot");
        }
    }

    @Test
    void renderLightRuleRunsInsideTheBuild() throws Exception {
        // A neighbour-brightness cell with raw sky 0 next to a sky-15 cell: after the
        // build, the snapshot itself must carry the ruled value - the rule moved from
        // the scanner's fill (render thread) to the head of the build (worker).
        WorldSectionSnapshot snap = new WorldSectionSnapshot();
        snap.fill(0, 0, 0);
        int cell = WorldSectionSnapshot.index(8, 8, 8);
        int bright = WorldSectionSnapshot.index(9, 8, 8);
        snap.setNeighborBrightness(cell, true);
        snap.skyLight()[bright] = 15;
        snap.blockLight()[bright] = 7;
        try (TerrainBuildScheduler scheduler = new TerrainBuildScheduler(2)) {
            scheduler.build(snap, new SectionMesherTest.Stub(), MesherOptions.PACK_OFF)
                    .get(30, TimeUnit.SECONDS);
        }
        assertEquals(15, snap.skyLightAt(cell), "sky takes the neighbour maximum");
        assertEquals(7, snap.blockLightAt(cell), "block light takes the neighbour maximum");
    }

    @Test
    void buildTimeLightRuleMatchesTheOldFillTimeApplication() {
        // The equivalence the move has to keep: meshing a snapshot whose rule was applied
        // by hand (the old fill-time form) and one whose rule the build applies must
        // produce identical geometry, and both must differ from no rule at all (the
        // sampled face light really does flow through the rule).
        SectionMesherTest.Stub stub = new SectionMesherTest.Stub();
        WorldSectionSnapshot ruled = ruleSensitiveSnapshot();
        ruled.applyRenderLightRule();
        MeshBuffers expected = new MeshBuffers();
        new SectionMesher().mesh(ruled, stub, MesherOptions.PACK_OFF, expected);

        WorldSectionSnapshot unruled = ruleSensitiveSnapshot();
        MeshBuffers noRule = new MeshBuffers();
        new SectionMesher().mesh(unruled, stub, MesherOptions.PACK_OFF, noRule);

        WorldSectionSnapshot viaBuild = ruleSensitiveSnapshot();
        MeshBuffers actual;
        try (TerrainBuildScheduler scheduler = new TerrainBuildScheduler(2)) {
            actual = scheduler.build(viaBuild, stub, MesherOptions.PACK_OFF)
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals(expected.axisTotal(), actual.axisTotal());
        assertEquals(expected.generalTotal(), actual.generalTotal());
        for (int layer = 0; layer < 3; layer++) {
            assertEquals(expected.axisLayerList(layer), actual.axisLayerList(layer),
                    "layer " + layer + " axis quads differ between fill-time and build-time rule");
            assertEquals(expected.generalLayerList(layer), actual.generalLayerList(layer),
                    "layer " + layer + " general quads differ");
        }
        assertFalse(java.util.Objects.equals(expected.axisLayerList(0), noRule.axisLayerList(0)),
                "the fixture must be rule-sensitive: no rule, different light on the +X face");
    }

    /**
     * One stone cube whose +X neighbour cell is a neighbour-brightness cell with raw sky 0,
     * backed by a sky-15 cell: the cube's +X face samples the middle cell, so its light is
     * 0 before the rule and 15 after it.
     */
    private static WorldSectionSnapshot ruleSensitiveSnapshot() {
        WorldSectionSnapshot snap = new WorldSectionSnapshot();
        snap.fill(0, 0, 0);
        snap.states()[WorldSectionSnapshot.index(8, 8, 8)] = 1; // STONE in the stub table
        int middle = WorldSectionSnapshot.index(9, 8, 8);
        snap.setNeighborBrightness(middle, true);
        snap.skyLight()[WorldSectionSnapshot.index(10, 8, 8)] = 15;
        return snap;
    }

    /** Polls the pool for {@code expected}, parking (and later returning) anything else. */
    private static boolean acquireEventually(TerrainBuildScheduler scheduler,
                                             WorldSectionSnapshot expected) throws Exception {
        java.util.List<WorldSectionSnapshot> parked = new java.util.ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean found = false;
        while (System.nanoTime() < deadline) {
            WorldSectionSnapshot candidate = scheduler.acquireSnapshot();
            if (candidate == expected) {
                found = true;
                break;
            }
            parked.add(candidate);
            Thread.sleep(5);
        }
        for (WorldSectionSnapshot extra : parked) {
            scheduler.releaseSnapshot(extra);
        }
        if (found) {
            scheduler.releaseSnapshot(expected);
        }
        return found;
    }
}
