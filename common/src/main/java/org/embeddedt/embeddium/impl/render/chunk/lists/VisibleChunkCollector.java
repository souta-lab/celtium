package org.embeddedt.embeddium.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongHeaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AccessLevel;
import lombok.Getter;
import org.embeddedt.embeddium.impl.render.chunk.ChunkUpdateType;
import org.embeddedt.embeddium.impl.render.chunk.PackedSectionMetadata;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumMap;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionLattice;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.joml.Vector3ic;

public class VisibleChunkCollector implements OcclusionCuller.Visitor {
    @Getter(AccessLevel.PACKAGE)
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;
    private final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> sortedRebuildLists;
    private final int[] rebuildQueueOverflowCounts;
    private final ChunkRenderList[] renderListsByRegion;

    private final LongArrayList[] rebuildCandidates;

    private final int frame;

    private final int targetQueueSize;

    private final SectionLattice lattice;

    // Camera position in blocks, used as the origin for the rebuild ordering
    private final int cameraX, cameraY, cameraZ;

    private boolean hasAdditionalUpdates;
    private boolean rebuildListsFinished;

    /*
     * Present census (item 683): an exclusive decomposition of the visible set by build state, so a
     * "visible count is normal but the screen shows fragments" report can be attributed instead of
     * argued. CELTIUM_VISIBLE counts traversed cells whether or not they hold built geometry; this
     * census separates the cells that can draw (visuals set) from the ones that cannot, and splits
     * the latter by why not: still queued, build in flight, in flight with the cancellation token
     * already gone (the wedge signature), or terminal with no pending update - split by whether the
     * section was ever built. A mirror/body pending disagreement is counted alongside.
     *
     * Enabled only when -Dceltium.debug.presentCensus is set; the value bounds the session's printed
     * lines (default 24 when set without a usable value). A line is printed when the anomaly sum
     * (wedged + terminalBuilt + terminalUnbuilt + mirrorMismatch) changes, so a settled scene prints
     * once and a stuck one prints as its composition shifts. The classification of the in-flight and
     * terminal cells reads the section body (token, built state) on the search thread; those reads
     * are advisory in the same way the existing in-flight check in visit() is - a concurrent commit
     * can move a cell between categories, but cannot invent a wedge that persists across searches.
     */
    private static int presentCensusPrintedLines;
    private static long presentCensusLastPrintedAnomaly = -1L;

    private static int presentCensusLimit() {
        if (System.getProperty("celtium.debug.presentCensus") == null) {
            return 0;
        }

        return Math.max(0, Integer.getInteger("celtium.debug.presentCensus", 24));
    }

    private final int presentCensusLimit;
    private final boolean presentCensusEnabled;
    private int censusVisible;
    private int censusDrawn;
    private int censusQueued;
    private int censusBuilding;
    private int censusWedged;
    private int censusTerminalBuilt;
    private int censusTerminalUnbuilt;
    private int censusMirrorMismatch;

    /** The build-state categories a visible cell falls into; see the census note above. */
    enum PresentCensusCategory {
        DRAWN,
        QUEUED,
        BUILDING,
        WEDGED,
        TERMINAL_BUILT,
        TERMINAL_UNBUILT
    }

    /**
     * Classifies one visible cell from its compact mirror metadata. The body is consulted only for
     * the categories the mirror cannot decide (in-flight token presence, ever-built state); a null
     * body - a cell uninstalled between traversal and classification - reads as still building /
     * never built, never as wedged or terminal-built, so a teardown race cannot fabricate the
     * anomaly categories.
     */
    static PresentCensusCategory classifyPresent(int meta, RenderSection section) {
        if (PackedSectionMetadata.getCompactVisualsFlags(meta) != 0) {
            return PresentCensusCategory.DRAWN;
        }

        boolean inFlight = PackedSectionMetadata.isCompactBuildInFlight(meta);
        ChunkUpdateType pending = PackedSectionMetadata.getCompactPendingUpdate(meta);

        if (!inFlight && pending != null) {
            return PresentCensusCategory.QUEUED;
        }

        if (inFlight) {
            return section != null && section.getBuildCancellationToken() == null
                    ? PresentCensusCategory.WEDGED
                    : PresentCensusCategory.BUILDING;
        }

        return section != null && section.isBuilt()
                ? PresentCensusCategory.TERMINAL_BUILT
                : PresentCensusCategory.TERMINAL_UNBUILT;
    }

    /** One search's census counts, in the printed line's field order. */
    public record PresentCensus(int visible, int drawn, int queued, int building, int wedged,
                                int terminalBuilt, int terminalUnbuilt, int mirrorMismatch) {
        long anomaly() {
            return (long) wedged + terminalBuilt + terminalUnbuilt + mirrorMismatch;
        }
    }

    public PresentCensus presentCensus() {
        return new PresentCensus(this.censusVisible, this.censusDrawn, this.censusQueued,
                this.censusBuilding, this.censusWedged, this.censusTerminalBuilt,
                this.censusTerminalUnbuilt, this.censusMirrorMismatch);
    }

    public VisibleChunkCollector(SectionLattice lattice, int frame, int regionIdsLength, int targetQueueSize, Vector3ic cameraBlockPos) {
        this(lattice, frame, regionIdsLength, targetQueueSize, cameraBlockPos, false);
    }

    public VisibleChunkCollector(SectionLattice lattice, int frame, int regionIdsLength, int targetQueueSize, Vector3ic cameraBlockPos, boolean collectPresentCensus) {
        this.presentCensusLimit = collectPresentCensus ? presentCensusLimit() : 0;
        this.presentCensusEnabled = this.presentCensusLimit > 0;
        this.lattice = lattice;
        this.frame = frame;

        this.sortedRenderLists = new ObjectArrayList<>();
        this.sortedRebuildLists = new EnumMap<>(ChunkUpdateType.class);
        this.rebuildQueueOverflowCounts = new int[ChunkUpdateType.VALUES.length];
        this.rebuildCandidates = new LongArrayList[ChunkUpdateType.VALUES.length];
        this.targetQueueSize = targetQueueSize;
        this.renderListsByRegion = new ChunkRenderList[regionIdsLength];
        this.cameraX = cameraBlockPos.x();
        this.cameraY = cameraBlockPos.y();
        this.cameraZ = cameraBlockPos.z();

        for (var type : ChunkUpdateType.VALUES) {
            this.sortedRebuildLists.put(type, new ArrayDeque<>());
        }
    }

    private ChunkRenderList createRenderList(RenderRegion region) {
        ChunkRenderList renderList = new ChunkRenderList(region);
        this.sortedRenderLists.add(renderList);
        this.renderListsByRegion[region.getId()] = renderList;
        return renderList;
    }

    @Override
    public void visit(int latticeIndex, int regionId, int sectionIndex, int chunkX, int chunkY, int chunkZ, int meta, boolean visible) {
        // Note: even if a section does not have render objects, we must ensure the render list is initialized and put
        // into the sorted queue of lists, so that we maintain the correct order of draw calls.
        ChunkRenderList renderList = this.renderListsByRegion[regionId];

        if (renderList == null) {
            renderList = this.createRenderList(this.lattice.sectionAt(latticeIndex).getRegion());
        }

        if (visible) {
            int visualsFlags = PackedSectionMetadata.getCompactVisualsFlags(meta);
            if (visualsFlags != 0) {
                renderList.add(sectionIndex, visualsFlags);
            }

            ChunkUpdateType type = PackedSectionMetadata.getCompactPendingUpdate(meta);

            // Skip sections with an in-flight build to avoid redundant work. This is an advisory
            // check only: submitRebuildTasks() will validate getPendingUpdate() independently before
            // scheduling, so a stale read here cannot cause a double submission.
            if (type != null && !PackedSectionMetadata.isCompactBuildInFlight(meta)) {
                this.addRebuildCandidate(latticeIndex, chunkX, chunkY, chunkZ, type);
            }

            if (this.presentCensusEnabled) {
                this.censusVisit(latticeIndex, meta, type);
            }
        }
    }

    private void censusVisit(int latticeIndex, int meta, ChunkUpdateType mirrorPending) {
        this.censusVisible++;

        // The body is read only for cells without visuals - the minority in a healthy scene, and
        // exactly the population the census exists to explain. Drawn cells need no body read.
        RenderSection section = null;

        if (PackedSectionMetadata.getCompactVisualsFlags(meta) == 0) {
            section = this.lattice.sectionAt(latticeIndex);

            if (section != null && section.getPendingUpdate() != mirrorPending) {
                this.censusMirrorMismatch++;
            }
        }

        switch (classifyPresent(meta, section)) {
            case DRAWN -> this.censusDrawn++;
            case QUEUED -> this.censusQueued++;
            case BUILDING -> this.censusBuilding++;
            case WEDGED -> this.censusWedged++;
            case TERMINAL_BUILT -> this.censusTerminalBuilt++;
            case TERMINAL_UNBUILT -> this.censusTerminalUnbuilt++;
        }
    }

    private void printPresentCensus() {
        PresentCensus census = this.presentCensus();
        long anomaly = census.anomaly();

        if (anomaly == presentCensusLastPrintedAnomaly || presentCensusPrintedLines >= this.presentCensusLimit) {
            return;
        }

        presentCensusLastPrintedAnomaly = anomaly;
        presentCensusPrintedLines++;

        System.out.println("[Celtium] CELTIUM_PRESENT_CENSUS frame=" + this.frame
                + " visible=" + census.visible()
                + " drawn=" + census.drawn()
                + " queued=" + census.queued()
                + " building=" + census.building()
                + " wedged=" + census.wedged()
                + " terminalBuilt=" + census.terminalBuilt()
                + " terminalUnbuilt=" + census.terminalUnbuilt()
                + " mirrorMismatch=" + census.mirrorMismatch()
                + " graphSections=" + this.lattice.attachedSectionCount());
    }

    private void addRebuildCandidate(int latticeIndex, int chunkX, int chunkY, int chunkZ, ChunkUpdateType type) {
        long dx = ((long) chunkX << 4) + 8 - this.cameraX;
        long dy = ((long) chunkY << 4) + 8 - this.cameraY;
        long dz = ((long) chunkZ << 4) + 8 - this.cameraZ;
        long distanceSq = Math.min(dx * dx + dy * dy + dz * dz, Integer.MAX_VALUE);

        var candidates = this.rebuildCandidates[type.ordinal()];

        if (candidates == null) {
            candidates = this.rebuildCandidates[type.ordinal()] = new LongArrayList();
        }

        candidates.add((distanceSq << 32) | (latticeIndex & 0xFFFFFFFFL));
    }

    public void finishRebuildLists() {
        if (this.rebuildListsFinished) {
            throw new IllegalStateException("Rebuild lists already finished");
        }

        this.rebuildListsFinished = true;

        var types = ChunkUpdateType.VALUES;

        for (int i = 0; i < types.length; i++) {
            var candidates = this.rebuildCandidates[i];

            if (candidates == null) {
                continue;
            }

            var type = types[i];
            long[] keys = candidates.elements();
            int count = candidates.size();

            // Do not limit the queue size for rebuilds
            int limit = type == ChunkUpdateType.INITIAL_BUILD ? Math.min(count, this.targetQueueSize) : count;

            // Only use max-heap selection when we need 1/3 or less of the full list
            if (limit * 3 < count) {
                keys = selectSmallest(keys, count, limit);
            } else {
                Arrays.sort(keys, 0, count);
            }

            var queue = this.sortedRebuildLists.get(type);

            for (int j = 0; j < limit; j++) {
                queue.add(this.lattice.sectionAt((int) keys[j]));
            }

            if (limit < count) {
                this.rebuildQueueOverflowCounts[i] += count - limit;
                this.hasAdditionalUpdates = true;
            }

            this.rebuildCandidates[i] = null;
        }

        if (this.presentCensusEnabled) {
            this.printPresentCensus();
        }
    }

    /**
     * {@return the {@code k} smallest of the first {@code count} keys, in ascending order}
     *
     * <p>Uses a bounded max-heap: once it holds {@code k} keys, a candidate larger than the heap's root is rejected
     * with a single comparison, so for the common case where most candidates are farther than the cutoff this runs
     * in roughly linear time instead of the {@code n log n} of a full sort.</p>
     */
    private static long[] selectSmallest(long[] keys, int count, int k) {
        var comparator = LongComparators.OPPOSITE_COMPARATOR;

        long[] heap = Arrays.copyOf(keys, k);
        LongHeaps.makeHeap(heap, k, comparator);

        for (int i = k; i < count; i++) {
            long key = keys[i];

            if (key < heap[0]) {
                heap[0] = key;
                LongHeaps.downHeap(heap, k, 0, comparator);
            }
        }

        Arrays.sort(heap);

        return heap;
    }

    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }

    public ChunkRebuildLists getRebuildLists() {
        if (!this.rebuildListsFinished) {
            throw new IllegalStateException("finishRebuildLists() must be called on the search thread first");
        }

        EnumMap<ChunkUpdateType, Integer> overflowCounts = new EnumMap<>(ChunkUpdateType.class);
        if (this.hasAdditionalUpdates) {
            var values = ChunkUpdateType.VALUES;
            for (int i = 0; i < values.length; i++) {
                if (this.rebuildQueueOverflowCounts[i] != 0) {
                    overflowCounts.put(values[i], this.rebuildQueueOverflowCounts[i]);
                }
            }
        }
        return new ChunkRebuildLists(this.sortedRebuildLists, this.hasAdditionalUpdates, overflowCounts);
    }
}
