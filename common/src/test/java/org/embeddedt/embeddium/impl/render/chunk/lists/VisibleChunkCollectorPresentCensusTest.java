package org.embeddedt.embeddium.impl.render.chunk.lists;

import org.embeddedt.embeddium.impl.render.chunk.ChunkUpdateType;
import org.embeddedt.embeddium.impl.render.chunk.PackedSectionMetadata;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionLattice;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.TestRegions;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3i;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibleChunkCollectorPresentCensusTest {
    @BeforeAll
    static void enableCensus() {
        System.setProperty("celtium.debug.presentCensus", "24");
    }

    @AfterAll
    static void disableCensus() {
        System.clearProperty("celtium.debug.presentCensus");
    }

    private static int meta(int visuals, ChunkUpdateType pending, boolean inFlight) {
        long packed = PackedSectionMetadata.withVisualsFlags(0L, visuals);
        packed = PackedSectionMetadata.withPendingUpdate(packed, pending);
        packed = PackedSectionMetadata.withBuildInFlight(packed, inFlight);
        return PackedSectionMetadata.toCompactMeta(packed);
    }

    private static CancellationToken token() {
        return new CancellationToken() {
            private boolean cancelled;

            @Override
            public boolean isCancelled() {
                return this.cancelled;
            }

            @Override
            public void setCancelled() {
                this.cancelled = true;
            }
        };
    }

    private static RenderSection bareSection() {
        return new RenderSection(TestRegions.create(0, 0, 0, 0), 0, 0, 0);
    }

    @Test
    void classifyPresentCoversEveryCategory() {
        var unbuilt = bareSection();
        var built = bareSection();
        built.setInfo(RenderSection.EMPTY_DATA);
        var building = bareSection();
        building.setBuildCancellationToken(token());

        // Visuals win over every other state: a drawn cell is drawn.
        assertEquals(VisibleChunkCollector.PresentCensusCategory.DRAWN,
                VisibleChunkCollector.classifyPresent(meta(1, ChunkUpdateType.INITIAL_BUILD, true), unbuilt));
        // Pending with no build in flight is queued for the collector's rebuild lists.
        assertEquals(VisibleChunkCollector.PresentCensusCategory.QUEUED,
                VisibleChunkCollector.classifyPresent(meta(0, ChunkUpdateType.INITIAL_BUILD, false), unbuilt));
        // In flight with its token still attached is an ordinary build in progress, even when a
        // further update is already pending behind it.
        assertEquals(VisibleChunkCollector.PresentCensusCategory.BUILDING,
                VisibleChunkCollector.classifyPresent(meta(0, ChunkUpdateType.REBUILD, true), building));
        // In flight with the token gone is the wedge signature: the mirror still believes a build
        // is running, so the collector will skip this cell forever.
        assertEquals(VisibleChunkCollector.PresentCensusCategory.WEDGED,
                VisibleChunkCollector.classifyPresent(meta(0, null, true), unbuilt));
        // Terminal cells split on whether a build ever committed.
        assertEquals(VisibleChunkCollector.PresentCensusCategory.TERMINAL_BUILT,
                VisibleChunkCollector.classifyPresent(meta(0, null, false), built));
        assertEquals(VisibleChunkCollector.PresentCensusCategory.TERMINAL_UNBUILT,
                VisibleChunkCollector.classifyPresent(meta(0, null, false), unbuilt));
    }

    @Test
    void classifyPresentNeverFabricatesAnomaliesFromAMissingBody() {
        // A cell uninstalled between traversal and classification must not read as wedged or
        // terminal-built: those are the categories a verdict is built on.
        assertEquals(VisibleChunkCollector.PresentCensusCategory.BUILDING,
                VisibleChunkCollector.classifyPresent(meta(0, null, true), null));
        assertEquals(VisibleChunkCollector.PresentCensusCategory.TERMINAL_UNBUILT,
                VisibleChunkCollector.classifyPresent(meta(0, null, false), null));
    }

    @Test
    void visitDecomposesTheVisibleSetExclusively() {
        var lattice = new SectionLattice(0, 16, false, false);
        Map<Long, RenderRegion> regions = new HashMap<>();

        RenderSection drawn = attach(lattice, regions, 8, 8, 7);
        RenderSection queued = attach(lattice, regions, 9, 8, 7);
        // In production the mirror meta is derived from the body, so the two agree except where
        // the test deliberately desyncs them (terminalUnbuilt below).
        queued.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        RenderSection building = attach(lattice, regions, 10, 8, 7);
        building.setBuildCancellationToken(token());
        RenderSection wedged = attach(lattice, regions, 11, 8, 7);
        RenderSection terminalBuilt = attach(lattice, regions, 12, 8, 7);
        terminalBuilt.setInfo(RenderSection.EMPTY_DATA);
        RenderSection terminalUnbuilt = attach(lattice, regions, 13, 8, 7);
        // The body carries a pending update the mirror meta below does not: one mirror mismatch,
        // and the cell still classifies from the mirror as terminal-unbuilt.
        terminalUnbuilt.setPendingUpdate(ChunkUpdateType.REBUILD);

        lattice.ensureWindowCovers(new Vector3i(10, 8, 7), 32.0f);

        var collector = new VisibleChunkCollector(lattice, 1, 64, 4096, new Vector3i(160, 128, 112), true);

        visit(collector, lattice, drawn, meta(1, null, false));
        visit(collector, lattice, queued, meta(0, ChunkUpdateType.INITIAL_BUILD, false));
        visit(collector, lattice, building, meta(0, null, true));
        visit(collector, lattice, wedged, meta(0, null, true));
        visit(collector, lattice, terminalBuilt, meta(0, null, false));
        visit(collector, lattice, terminalUnbuilt, meta(0, null, false));
        // A non-visible visit is not part of the visible set at all.
        collector.visit(indexOf(lattice, drawn), drawn.getRegion().getId(), drawn.getSectionIndex(),
                drawn.getChunkX(), drawn.getChunkY(), drawn.getChunkZ(), meta(0, null, false), false);

        var census = collector.presentCensus();
        assertEquals(6, census.visible());
        assertEquals(1, census.drawn());
        assertEquals(1, census.queued());
        assertEquals(1, census.building());
        assertEquals(1, census.wedged());
        assertEquals(1, census.terminalBuilt());
        assertEquals(1, census.terminalUnbuilt());
        assertEquals(1, census.mirrorMismatch());
        assertEquals(census.visible(), census.drawn() + census.queued() + census.building()
                + census.wedged() + census.terminalBuilt() + census.terminalUnbuilt());

        collector.finishRebuildLists();
    }

    @Test
    void censusStaysOffWithoutTheProperty() {
        System.clearProperty("celtium.debug.presentCensus");
        try {
            var lattice = new SectionLattice(0, 16, false, false);
            Map<Long, RenderRegion> regions = new HashMap<>();
            RenderSection section = attach(lattice, regions, 8, 8, 7);
            lattice.ensureWindowCovers(new Vector3i(8, 8, 7), 32.0f);

            var collector = new VisibleChunkCollector(lattice, 1, 64, 4096, new Vector3i(128, 128, 112), true);
            visit(collector, lattice, section, meta(0, null, false));

            assertEquals(0, collector.presentCensus().visible());
        } finally {
            System.setProperty("celtium.debug.presentCensus", "24");
        }
    }

    private static RenderSection attach(SectionLattice lattice, Map<Long, RenderRegion> regions, int x, int y, int z) {
        int rx = x >> RenderRegion.REGION_WIDTH_SH;
        int ry = y >> RenderRegion.REGION_HEIGHT_SH;
        int rz = z >> RenderRegion.REGION_LENGTH_SH;
        var region = regions.computeIfAbsent(RenderRegion.key(rx, ry, rz),
                k -> TestRegions.create(rx, ry, rz, regions.size()));
        var section = new RenderSection(region, x, y, z);
        lattice.attach(section);
        return section;
    }

    private static int indexOf(SectionLattice lattice, RenderSection section) {
        // sectionAt is the public way to resolve an installed cell; scan for identity.
        for (int i = 0; ; i++) {
            if (lattice.sectionAt(i) == section) {
                return i;
            }
        }
    }

    private static void visit(VisibleChunkCollector collector, SectionLattice lattice, RenderSection section, int meta) {
        collector.visit(indexOf(lattice, section), section.getRegion().getId(), section.getSectionIndex(),
                section.getChunkX(), section.getChunkY(), section.getChunkZ(), meta, true);
    }
}
