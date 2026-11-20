package org.embeddedt.embeddium.impl.render.chunk.lists;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionLattice;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.TestRegions;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 426 (upstream Celeritas d3946af4e): the shadow pass's lattice window update runs inside the
 * submitted shadow search, on the search thread, instead of on the calling thread before submission.
 *
 * <p>Before the move, {@code startShadowGraphUpdate} called {@code ensureWindowCovers} on the render
 * thread while the terrain search submitted just before it could still be running on the search
 * thread, so the ensure (allocation, rebase, and the item-423 root remap) raced the terrain search's
 * live reads of the lattice arrays. On the single search thread, submission order now serializes the
 * two: the shadow ensure cannot start until the terrain search has finished executing.
 */
class ShadowWindowThreadTest {
    // A frustum that claims every box is fully inside, so region classification never culls and the
    // per-section distance test is the only visibility gate left.
    private static final Frustum FRUSTUM = new Frustum() {
        @Override
        public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            return true;
        }

        @Override
        public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            return FULLY_INSIDE;
        }
    };

    // Large enough that every section the traversal can reach also passes the per-section distance
    // test, so the set of sections the terrain snapshot reports reached is exactly the root set the
    // shadow search seeds from. (A reached-but-not-visible cell is stamped by the traversal without
    // being recorded as a root; with a short search distance the two sets differ.)
    private static final float SEARCH_DISTANCE = 256.0f;

    // Terrain camera section, and a shadow camera sixteen sections east: far enough that the window
    // (dim 43 at this search distance) must slide, dropping the western roots and retaining the rest.
    private static final int[] TERRAIN_CAM = {8, 8, 7};
    private static final int[] SHADOW_CAM = {24, 8, 7};

    /** One graph with both pass managers over a fully attached section grid. */
    private static final class Fixture implements AutoCloseable {
        final SectionGraph graph;
        final SectionLattice lattice;
        final RenderListManager terrain;
        final RenderListManager shadow;
        final int regionIdsLength;

        Fixture(AsyncOcclusionMode mode) {
            this.graph = new SectionGraph(0, 16, mode, true, false);
            this.lattice = this.graph.getLattice();

            Map<Long, RenderRegion> regions = new HashMap<>();
            for (int x = 0; x <= 30; x++) {
                for (int y = 0; y <= 15; y++) {
                    for (int z = 0; z <= 14; z++) {
                        int rx = x >> RenderRegion.REGION_WIDTH_SH;
                        int ry = y >> RenderRegion.REGION_HEIGHT_SH;
                        int rz = z >> RenderRegion.REGION_LENGTH_SH;
                        var region = regions.computeIfAbsent(RenderRegion.key(rx, ry, rz),
                                k -> TestRegions.create(rx, ry, rz, regions.size()));
                        this.graph.attachRenderSection(new RenderSection(region, x, y, z));
                    }
                }
            }
            this.regionIdsLength = regions.size();

            this.terrain = new RenderListManager(this.graph, false, mode, null);
            this.shadow = new RenderListManager(this.graph, true, mode, null);
        }

        void runTerrainSearch(int frame) {
            this.terrain.startGraphUpdate(viewport(TERRAIN_CAM), frame, this.regionIdsLength,
                    SEARCH_DISTANCE, false, Integer.MAX_VALUE);
            this.terrain.finishPreviousGraphUpdate();
        }

        void runTerrainAndShadowSearch(int frame, org.joml.Vector3fc lightVector) {
            this.terrain.startGraphUpdate(viewport(TERRAIN_CAM), frame, this.regionIdsLength,
                    SEARCH_DISTANCE, false, Integer.MAX_VALUE);
            this.shadow.startShadowGraphUpdate(viewport(SHADOW_CAM), frame, this.regionIdsLength,
                    SEARCH_DISTANCE, lightVector, Integer.MAX_VALUE);
            this.terrain.finishPreviousGraphUpdate();
            this.shadow.finishPreviousGraphUpdate();
        }

        Set<Long> terrainVisibleSet() {
            return visibleSet(this.terrain);
        }

        @Override
        public void close() {
            this.graph.destroy();
        }
    }

    private static Viewport viewport(int[] section) {
        return new Viewport(FRUSTUM, new Vector3d(section[0] * 16 + 8, section[1] * 16 + 8, section[2] * 16 + 8));
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) | ((long) y << 20) | z;
    }

    private static Set<Long> visibleSet(RenderListManager manager) {
        Set<Long> visible = new HashSet<>();
        for (int x = 0; x <= 30; x++) {
            for (int y = 0; y <= 15; y++) {
                for (int z = 0; z <= 14; z++) {
                    if (manager.isSectionVisible(x, y, z)) {
                        visible.add(key(x, y, z));
                    }
                }
            }
        }
        return visible;
    }

    /** T1: the shadow ensure runs on the search thread; the terrain ensure stays on the calling thread. */
    @Test
    void shadowEnsureRunsOnSearchThread() {
        try (var fixture = new Fixture(AsyncOcclusionMode.EVERYTHING)) {
            fixture.runTerrainSearch(1);
            assertEquals(Thread.currentThread(), fixture.lattice.lastEnsureWindowCoversThread(),
                    "terrain ensure is dispatched before submission, on the calling thread");

            fixture.runTerrainAndShadowSearch(2, new Vector3f(0, 1, 0));
            Thread ensureThread = fixture.lattice.lastEnsureWindowCoversThread();
            assertNotNull(ensureThread);
            assertNotEquals(Thread.currentThread(), ensureThread,
                    "shadow ensure must not run on the calling thread");
            assertEquals("Celtium chunk graph search thread", ensureThread.getName(),
                    "shadow ensure must run inside the submitted search task, on the search thread");
        }
    }

    /**
     * T2: the terrain result is identical whether or not a shadow search follows it, and the roots the
     * window slide retains are the ones the shadow search seeds from (the remap ran, in order, before
     * the shadow search read the root set).
     */
    @Test
    void terrainResultUnchangedAndRetainedRootsSeedShadowSearch() {
        Set<Long> reference;
        try (var fixture = new Fixture(AsyncOcclusionMode.EVERYTHING)) {
            fixture.runTerrainSearch(1);
            reference = fixture.terrainVisibleSet();
        }
        assertTrue(reference.size() > 10, "terrain search should report a non-trivial visible set");

        // The slide to the shadow window (baseX -13 -> 3, interior x in [4, 44]) drops the western
        // roots and retains the eastern ones; the scenario only exercises the remap if both exist.
        assertTrue(reference.stream().anyMatch(k -> (int) (k >> 40) < 4), "expected roots the slide drops");
        assertTrue(reference.stream().anyMatch(k -> (int) (k >> 40) >= 4), "expected roots the slide retains");

        try (var fixture = new Fixture(AsyncOcclusionMode.EVERYTHING)) {
            fixture.runTerrainAndShadowSearch(1, new Vector3f(0, 1, 0));

            assertEquals(reference, fixture.terrainVisibleSet(),
                    "the shadow window update must not perturb the terrain search submitted before it");

            // Probe the retained roots from x >= 8 eastward. The remap moves every retained root's
            // lattice index (baseX shifts by 16), so a stale index would seed the wrong cell and the
            // probe would not report visible. Roots west of x = 8 are also retained, but their
            // regions fall outside the shadow search's own region distance cull from the shadow
            // camera, so the shadow search legitimately never seeds them.
            for (long k : reference) {
                int x = (int) (k >> 40), y = (int) (k >> 20 & 0xFFFFF), z = (int) (k & 0xFFFFF);
                if (x >= 8) {
                    assertTrue(fixture.shadow.isSectionVisible(x, y, z),
                            "retained root " + x + "," + y + "," + z + " must seed the shadow search");
                }
            }
        }
    }

    /**
     * T3: every async mode completes the same scenario, including the frustum-only shadow fallback
     * (null light vector), with the same terrain result. The ensure's thread follows the mode: inline
     * on the calling thread for NONE, on the search thread whenever the shadow search is async.
     */
    @ParameterizedTest
    @EnumSource(AsyncOcclusionMode.class)
    void allModesCompleteWithFrustumOnlyShadowFallback(AsyncOcclusionMode mode) {
        Set<Long> reference;
        try (var fixture = new Fixture(mode)) {
            fixture.runTerrainSearch(1);
            reference = fixture.terrainVisibleSet();
        }

        try (var fixture = new Fixture(mode)) {
            fixture.runTerrainAndShadowSearch(1, null);

            assertEquals(reference, fixture.terrainVisibleSet());
            assertTrue(fixture.shadow.isSectionVisible(SHADOW_CAM[0], SHADOW_CAM[1], SHADOW_CAM[2]),
                    "frustum-only shadow search must reach its camera section");
            assertTrue(fixture.shadow.isSectionVisible(SHADOW_CAM[0] - 1, SHADOW_CAM[1], SHADOW_CAM[2]),
                    "frustum-only shadow search must reach neighbouring sections");

            Thread ensureThread = fixture.lattice.lastEnsureWindowCoversThread();
            assertNotNull(ensureThread);
            if (mode == AsyncOcclusionMode.NONE) {
                assertEquals(Thread.currentThread(), ensureThread,
                        "with no async search, the shadow ensure runs inline on the calling thread");
            } else {
                assertEquals("Celtium chunk graph search thread", ensureThread.getName(),
                        "with an async shadow search, the shadow ensure runs on the search thread");
            }
        }
    }
}
