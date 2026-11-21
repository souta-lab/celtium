package org.embeddedt.embeddium.impl.render.chunk.lists;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.TestRegions;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BH10-10: one failed occlusion search must not wedge the section graph. Before the fix,
 * {@code finishPreviousGraphUpdate()} called {@code join()} before clearing the future and
 * returning the graph's in-flight count, so a single exceptional search rethrew the same
 * exception every frame, kept {@code inFlight} above zero forever (every attach/detach
 * throwing, deferred tasks never draining), and made {@code destroy()} abort the rest of
 * the teardown chain.
 *
 * <p>The failure is injected through {@link SectionTicker#onRenderListUpdated}, which the
 * search task calls after producing its snapshot: throwing there fails the task exactly
 * the way a defect inside the search itself would.
 */
class RenderListManagerFailureTest {
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

    private static final int[] CAM = {8, 4, 7};
    private static final float SEARCH_DISTANCE = 64.0f;

    /** A ticker that fails the next {@code failures} searches, then behaves. */
    private static final class FlakyTicker implements SectionTicker {
        private final AtomicInteger failuresLeft;

        FlakyTicker(int failures) {
            this.failuresLeft = new AtomicInteger(failures);
        }

        @Override
        public void tickVisibleRenders() {
        }

        @Override
        public void onRenderListUpdated(List<ChunkRenderList> renderLists) {
            if (this.failuresLeft.getAndDecrement() > 0) {
                throw new RuntimeException("ticker failure (test)");
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SectionGraph graph;
        final RenderListManager terrain;
        final RenderSection aSection;
        final int regionIdsLength;

        Fixture(AsyncOcclusionMode mode, int tickerFailures) {
            this.graph = new SectionGraph(0, 16, mode, true, false);

            Map<Long, RenderRegion> regions = new HashMap<>();
            RenderSection any = null;
            for (int x = 0; x <= 15; x++) {
                for (int y = 0; y <= 7; y++) {
                    for (int z = 0; z <= 14; z++) {
                        int rx = x >> RenderRegion.REGION_WIDTH_SH;
                        int ry = y >> RenderRegion.REGION_HEIGHT_SH;
                        int rz = z >> RenderRegion.REGION_LENGTH_SH;
                        var region = regions.computeIfAbsent(RenderRegion.key(rx, ry, rz),
                                k -> TestRegions.create(rx, ry, rz, regions.size()));
                        var section = new RenderSection(region, x, y, z);
                        this.graph.attachRenderSection(section);
                        any = section;
                    }
                }
            }
            this.aSection = any;
            this.regionIdsLength = regions.size();

            this.terrain = new RenderListManager(this.graph, false, mode, new FlakyTicker(tickerFailures));
        }

        void startSearch(int frame) {
            this.terrain.startGraphUpdate(viewport(CAM), frame, this.regionIdsLength,
                    SEARCH_DISTANCE, false, Integer.MAX_VALUE);
        }

        @Override
        public void close() {
            this.graph.destroy();
        }
    }

    private static Viewport viewport(int[] section) {
        return new Viewport(FRUSTUM, new Vector3d(section[0] * 16 + 8, section[1] * 16 + 8, section[2] * 16 + 8));
    }

    @Test
    void asyncSearchFailureDoesNotWedgeGraph() {
        try (var fixture = new Fixture(AsyncOcclusionMode.EVERYTHING, 1)) {
            fixture.startSearch(1);
            // The failure surfaces once, as the join's CompletionException...
            assertThrows(CompletionException.class, fixture.terrain::finishPreviousGraphUpdate);

            // ...and the graph is usable again: the in-flight count was returned, so a
            // structural mutation no longer throws "during occlusion".
            assertDoesNotThrow(() -> fixture.graph.detachRenderSection(fixture.aSection));

            // A fresh search runs to completion and publishes.
            fixture.startSearch(2);
            assertDoesNotThrow(fixture.terrain::finishPreviousGraphUpdate);
            assertEquals(2, fixture.terrain.getLastUpdatedFrame());
        }
    }

    @Test
    void syncSearchFailureDoesNotWedgeGraph() {
        try (var fixture = new Fixture(AsyncOcclusionMode.NONE, 1)) {
            // Inline searches run inside the submit; the failure propagates from the
            // start call itself, with no future left behind.
            assertThrows(RuntimeException.class, () -> fixture.startSearch(1));

            assertDoesNotThrow(() -> fixture.graph.detachRenderSection(fixture.aSection));

            assertDoesNotThrow(() -> fixture.startSearch(2));
            assertEquals(2, fixture.terrain.getLastUpdatedFrame());
        }
    }

    @Test
    void destroySwallowsFailedSearchAndReturnsInFlight() {
        try (var fixture = new Fixture(AsyncOcclusionMode.EVERYTHING, Integer.MAX_VALUE)) {
            fixture.startSearch(1);

            // Teardown must not be aborted by the failed search...
            assertDoesNotThrow(fixture.terrain::destroy);

            // ...and the in-flight count was still returned.
            assertDoesNotThrow(() -> fixture.graph.detachRenderSection(fixture.aSection));
        }
    }
}
