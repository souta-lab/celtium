package org.soutalab.celtium.vulkan;

/**
 * The frame-level budget discipline Vulcanizator's terrain snapshot carries and celtium's live draw loop did
 * not (item 328, V-03): a hard per-frame cap on the vertices and draws the terrain passes may present, an
 * adaptive rate limit on batch rebuilds, and last-good reuse while a rebuild is deferred.
 * <p>
 * The snapshot this budgets is the one celtium already has: the per-(region, pass, culling)
 * {@code CachedBatch} in {@code SectionRenderDataStorage}. That batch is immutable once built and is deleted
 * by the storage the moment any of its sections' meshes change, so a stored batch always references live
 * arena ranges; only its visibility selection (the section set and the occlusion validity box) can be stale.
 * That invariant is what makes the reuse below safe: drawing a stored batch whose rebuild was deferred can
 * show a frame-old visibility decision, never a dangling range.
 * <p>
 * Budgets are per presented frame, summed over every terrain pass of the frame (the shadow pass included -
 * it draws through the same loop). The window is keyed to the backend's frame id, which increments once per
 * presented frame; {@link #beginFrame(long)} rolls it lazily, so passes of one frame share one budget.
 * <p>
 * Vertex and draw drop caps default to disabled ({@code 0}): a hard cap shared across all terrain passes drops
 * later passes ({@code CUTOUT_MIPPED} leaves and {@code TRANSLUCENT} water, exposing the dark riverbed as black
 * water) on vanilla large render distances such as RD 32 (issue #422 (c)/(d)). Both caps remain opt-in through
 * {@code -Dceltium.vulkan.terrainMaxVertices} and {@code -Dceltium.vulkan.terrainMaxDraws} ({@code <= 0} disables).
 * The rebuild target is the celtium-scale analogue of Vulcanizator's adaptive snapshot target: celtium rebuilds
 * inline in the frame, so the target is a per-frame rebuild-time budget in microseconds, not the 250 ms a background
 * snapshot build may spend.
 */
public final class TerrainFrameBudget {
    private static final long MAX_VERTICES = Long.getLong("celtium.vulkan.terrainMaxVertices", 0L);
    private static final int MAX_DRAWS = Integer.getInteger("celtium.vulkan.terrainMaxDraws", 0);
    private static final int REBUILD_CAP = Integer.getInteger("celtium.vulkan.terrainMaxRebuildsPerFrame", 128);
    private static final long REBUILD_TARGET_NANOS =
            Long.getLong("celtium.vulkan.terrainRebuildTargetMicros", 2_000L) * 1_000L;
    private static final int MIN_ALLOWANCE = 8;

    private final long maxVertices;
    private final int maxDraws;
    private final int rebuildCap;
    private final long rebuildTargetNanos;

    /** The frame the current window belongs to; {@code Long.MIN_VALUE} before the first window. */
    private long windowFrame = Long.MIN_VALUE;
    private long drawnVertices;
    private int drawnDraws;
    private int rebuildsUsed;
    private int allowance;
    private long rebuildNanos;

    // The last finished window's snapshot, so the backend can print one complete line per eventful frame.
    private long finishedFrame = Long.MIN_VALUE;
    private boolean finishedHadEvents;
    private String finishedSummary = "";

    // The finished/current window's event tallies, read by the backend for its budget line.
    private int droppedBatches;
    private long droppedDraws;
    private long droppedVertices;
    private int deferredRebuilds;
    private int reusedSnapshots;
    private int forcedFirstBuilds;

    // Cumulative totals over the run, read by BackendDiagnostics.
    private long totalDrawnVertices;
    private long totalDrawnDraws;
    private long totalDroppedBatches;
    private long totalDroppedDraws;
    private long totalDroppedVertices;
    private long totalRebuilds;
    private long totalDeferredRebuilds;
    private long totalReusedSnapshots;
    private long totalForcedFirstBuilds;

    public TerrainFrameBudget() {
        this(MAX_VERTICES, MAX_DRAWS, REBUILD_CAP, REBUILD_TARGET_NANOS);
    }

    TerrainFrameBudget(long maxVertices, int maxDraws, int rebuildCap, long rebuildTargetNanos) {
        this.maxVertices = maxVertices;
        this.maxDraws = maxDraws;
        this.rebuildCap = Math.max(MIN_ALLOWANCE, rebuildCap);
        this.rebuildTargetNanos = rebuildTargetNanos;
        this.allowance = this.rebuildCap;
    }

    /**
     * Rolls the window when a new frame begins. The finished window's rebuild time adapts the next window's
     * allowance: over target halves it (floored at {@value #MIN_ALLOWANCE}), under a quarter of target grows
     * it by a quarter toward the cap. A window with no rebuilds carries no signal and leaves it unchanged.
     */
    public void beginFrame(long frameId) {
        if (frameId == this.windowFrame) {
            return;
        }
        if (this.windowFrame != Long.MIN_VALUE) {
            this.finishedFrame = this.windowFrame;
            this.finishedHadEvents = this.windowHasEvents();
            this.finishedSummary = "frame=" + this.windowFrame + " " + this.windowSummary();
        }
        if (this.windowFrame != Long.MIN_VALUE && this.rebuildsUsed + this.forcedFirstBuilds > 0) {
            if (this.rebuildNanos > this.rebuildTargetNanos) {
                this.allowance = Math.max(MIN_ALLOWANCE, this.allowance / 2);
            } else if (this.rebuildNanos < this.rebuildTargetNanos / 4) {
                this.allowance = Math.min(this.rebuildCap, this.allowance + Math.max(1, this.allowance / 4));
            }
        }
        this.windowFrame = frameId;
        this.drawnVertices = 0L;
        this.drawnDraws = 0;
        this.rebuildsUsed = 0;
        this.rebuildNanos = 0L;
        this.droppedBatches = 0;
        this.droppedDraws = 0L;
        this.droppedVertices = 0L;
        this.deferredRebuilds = 0;
        this.reusedSnapshots = 0;
        this.forcedFirstBuilds = 0;
    }

    /**
     * {@return the vertices a batch charges against the frame budget}. An unsorted batch reads the shared
     * quad index, where 6 indices name exactly 4 vertices; a sorted batch reads the region's own index arena,
     * where the honest charge is the index count itself - an upper bound on the distinct vertices touched, so
     * the sorted charge can over-count but never under-count.
     */
    public long verticesOf(VulkanMultiDrawBatch batch, boolean sortedPass) {
        long vertices = 0L;
        for (int i = 0; i < batch.size(); i++) {
            int elementCount = batch.elementCount(i);
            vertices += sortedPass ? elementCount : (elementCount / 6) * 4L;
        }
        return vertices;
    }

    /**
     * Charges a batch against the frame's draw and vertex budgets.
     *
     * @return true when the batch fits and has been charged; false when either budget would be exceeded, in
     * which case nothing is charged and the caller drops the whole batch (counted via
     * {@link #noteDroppedBatch(long, int)}) - a partial batch would break the indirect path's
     * one-call-per-batch shape, so drops are whole-batch by construction.
     */
    public boolean tryConsumeDraw(long vertices, int draws) {
        if (this.maxVertices > 0 && this.drawnVertices + vertices > this.maxVertices) {
            return false;
        }
        if (this.maxDraws > 0 && this.drawnDraws + draws > this.maxDraws) {
            return false;
        }
        this.drawnVertices += vertices;
        this.drawnDraws += draws;
        this.totalDrawnVertices += vertices;
        this.totalDrawnDraws += draws;
        return true;
    }

    /** Counts a whole-batch drop: the degradation is reported, never silent. */
    public void noteDroppedBatch(long vertices, int draws) {
        this.droppedBatches++;
        this.droppedDraws += draws;
        this.droppedVertices += vertices;
        this.totalDroppedBatches++;
        this.totalDroppedDraws += draws;
        this.totalDroppedVertices += vertices;
    }

    /** {@return whether this frame still has rebuild allowance}. */
    public boolean rebuildAllowed() {
        return this.rebuildsUsed < this.allowance;
    }

    /** Records a rebuild of a stored-but-invalid batch, charged against the allowance. */
    public void noteRebuild(long nanos) {
        this.rebuildsUsed++;
        this.rebuildNanos += nanos;
        this.totalRebuilds++;
    }

    /**
     * Records a first build - a region with no stored batch has no last-good to reuse, so its build always
     * proceeds. It does not consume allowance, but its time feeds the adaptation signal like any rebuild.
     */
    public void noteForcedFirstBuild(long nanos) {
        this.forcedFirstBuilds++;
        this.rebuildNanos += nanos;
        this.totalForcedFirstBuilds++;
    }

    /** Records a deferred rebuild: the stored batch is drawn as-is for this frame (last-good reuse). */
    public void noteDeferredReuse() {
        this.deferredRebuilds++;
        this.reusedSnapshots++;
        this.totalDeferredRebuilds++;
        this.totalReusedSnapshots++;
    }

    /** {@return whether the current window saw a drop or a reuse - the events the budget line reports}. */
    public boolean windowHasEvents() {
        return this.droppedBatches > 0 || this.reusedSnapshots > 0;
    }

    /** {@return the current window's report line body, in the backend's CELTIUM_TERRAIN_BUDGET format}. */
    public String windowSummary() {
        return "drawnVertices=" + this.drawnVertices
                + " drawnDraws=" + this.drawnDraws
                + " droppedBatches=" + this.droppedBatches
                + " droppedDraws=" + this.droppedDraws
                + " droppedVertices=" + this.droppedVertices
                + " rebuilds=" + this.rebuildsUsed
                + " deferred=" + this.deferredRebuilds
                + " reused=" + this.reusedSnapshots
                + " forced=" + this.forcedFirstBuilds
                + " allowance=" + this.allowance;
    }

    /** {@return the frame the finished-window snapshot belongs to}. */
    public long finishedFrame() {
        return this.finishedFrame;
    }

    /** {@return whether the finished window saw a drop or a reuse}. */
    public boolean finishedHadEvents() {
        return this.finishedHadEvents;
    }

    /** {@return the finished window's report line body, frame id included}. */
    public String finishedSummary() {
        return this.finishedSummary;
    }

    public int allowance() {
        return this.allowance;
    }

    public int rebuildsUsed() {
        return this.rebuildsUsed;
    }

    public int droppedBatches() {
        return this.droppedBatches;
    }

    public int reusedSnapshots() {
        return this.reusedSnapshots;
    }

    public long totalDrawnVertices() {
        return this.totalDrawnVertices;
    }

    public long totalDrawnDraws() {
        return this.totalDrawnDraws;
    }

    public long totalDroppedBatches() {
        return this.totalDroppedBatches;
    }

    public long totalDroppedDraws() {
        return this.totalDroppedDraws;
    }

    public long totalDroppedVertices() {
        return this.totalDroppedVertices;
    }

    public long totalRebuilds() {
        return this.totalRebuilds;
    }

    public long totalDeferredRebuilds() {
        return this.totalDeferredRebuilds;
    }

    public long totalReusedSnapshots() {
        return this.totalReusedSnapshots;
    }

    public long totalForcedFirstBuilds() {
        return this.totalForcedFirstBuilds;
    }
}
