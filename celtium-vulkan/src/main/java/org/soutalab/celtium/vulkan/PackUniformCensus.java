package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.gl.ModGlslTranslator;
import org.soutalab.celtium.vulkan.gl.ModProgramRegistry;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * The item-678 census of the pack terrain pass' per-region uniform glue ({@code packRecordUniform}):
 * the counters the bracket split alone cannot give, printed as {@code CELTIUM_PACK_UNIFORM} on the
 * {@link FramePassTimings} window cadence.
 * <p>
 * Why counters beside the three new brackets ({@code packUniformOffset} / {@code packUniformPack} /
 * {@code packUniformSetAlloc}): the bracket times say which seam owns the span, but the optimisation
 * the census is buying evidence for is chosen by rates, not times - how many regions a window records
 * per stage, how many ring-slot claims those regions force (a claim is what makes the pack walk and
 * the new descriptor-set key unavoidable), how often the pack actually walks versus serving its
 * cache, and how the descriptor-set lookups split into hits, misses and fresh allocations. A window
 * where claims approach regions says every region re-packs; a window where they diverge says the
 * version bump, not the region count, is the lever. {@code skippedRegions} mirrors the backend's own
 * {@code packTerrainSkippedRegions} at the same site, windowed, so a window's recorded-plus-skipped
 * total can be checked against the regions the stage was offered.
 * <p>
 * The first pack of each program also prints one {@code CELTIUM_PACK_UNIFORM_PROGRAM} line (member
 * count, block bytes, route, sampler / image counts): the per-program shape the walk's cost scales
 * with, stated once instead of inferred from a pack total.
 * <p>
 * The lane-diff census is separate and off unless asked for
 * ({@code -Dceltium.debug.packUniformLaneCensus=true}): on every pack it byte-compares the packed
 * block against the same program's previous pack and files which 16-byte lanes changed, printed per
 * window as {@code CELTIUM_PACK_UNIFORM_LANES}. It exists to size an incremental pack before one is
 * built: if the changed lanes concentrate in the region-offset lane, a member-level dirty pack wins
 * little over a lane-level one, and the histogram is the evidence either way. The comparison reads
 * the packed buffer at the pack site, before the ring copy consumes it, so a cache-served pack -
 * whose bytes by construction equal the previous block - files zero changed lanes, which is the
 * correct reading (the block did not change), not a missed sample.
 * <p>
 * Counting runs only while an instrument can read it: {@link FramePassTimings#enabled()} (the
 * brackets' own gate) or the lane census. Everywhere else each note is one boolean read, the same
 * off-cost discipline as the other censuses here. All state is render-thread confined, like
 * {@link FramePassTimings}' tables; the aggregation lives in {@link Ledger} so it is testable
 * without the switches.
 */
public final class PackUniformCensus {

    /** The lane-diff census switch; the window counters ride the pass timings' gate instead. */
    static final boolean LANE_CENSUS = Boolean.getBoolean("celtium.debug.packUniformLaneCensus");

    private static final Ledger LEDGER = new Ledger(LANE_CENSUS);

    private PackUniformCensus() {
    }

    /** {@return whether any reader of this census is on}. */
    static boolean counting() {
        return LANE_CENSUS || FramePassTimings.enabled();
    }

    /** One region reached the uniform span of the pack terrain record loop. */
    public static void noteRegion(boolean shadowStage) {
        if (counting()) {
            LEDGER.noteRegion(shadowStage);
        }
    }

    /** One region was skipped because its descriptor set could not be resolved. */
    public static void noteSkippedRegion() {
        if (counting()) {
            LEDGER.noteSkippedRegion();
        }
    }

    /** The terrain pipeline staged a fresh uniform ring slot for a program. */
    public static void noteSlotClaim() {
        if (counting()) {
            LEDGER.noteSlotClaim();
        }
    }

    /** A descriptor-set lookup on a terrain pipeline was served by an already-registered set. */
    public static void noteSetHit() {
        if (counting()) {
            LEDGER.noteSetHit();
        }
    }

    /** A descriptor-set lookup on a terrain pipeline fell through to the allocate path. */
    public static void noteSetMiss() {
        if (counting()) {
            LEDGER.noteSetMiss();
        }
    }

    /** The allocate path registered a fresh descriptor set. */
    public static void noteSetAlloc() {
        if (counting()) {
            LEDGER.noteSetAlloc();
        }
    }

    /** A descriptor-set lookup on any pipeline route was served by an already-registered set. */
    public static void notePipelineSetHit(Object pipeline, String route) {
        if (counting()) {
            LEDGER.notePipelineSetHit(pipeline, route);
        }
    }

    /** A descriptor-set lookup on any pipeline route fell through to the allocate path. */
    public static void notePipelineSetMiss(Object pipeline, String route) {
        if (counting()) {
            LEDGER.notePipelineSetMiss(pipeline, route);
        }
    }

    /** The allocate path registered a fresh descriptor set on a pipeline; population is its new set count. */
    public static void notePipelineSetAlloc(Object pipeline, String route, int population) {
        if (counting()) {
            LEDGER.notePipelineSetAlloc(pipeline, route, population);
        }
    }

    /**
     * A descriptor-set allocation on a pipeline failed: {@code capped} separates the 4096 cap guard
     * (allocation never attempted) from descriptor-pool exhaustion (allocation attempted, returned 0).
     */
    public static void notePipelineAllocFailure(Object pipeline, String route, boolean capped) {
        if (counting()) {
            LEDGER.notePipelineAllocFailure(pipeline, route, capped);
        }
    }

    /**
     * One {@code packUniforms} execution at a terrain pack site: files the walk, the program's
     * first-sight line and, when the lane census is on, the block's lane diff.
     */
    public static void notePack(ModProgramRegistry.Program program, ByteBuffer packed,
            int samplers, int images) {
        if (!counting()) {
            return;
        }
        ModGlslTranslator.Result lowered = program.lowered();
        int blockBytes = lowered == null ? 0 : lowered.uniformBlockSize();
        int members = lowered == null ? 0 : lowered.uniforms().size();
        boolean viaBuffer = lowered != null && lowered.uniformsViaBuffer();
        String first = LEDGER.notePack(program, program.id(), members, blockBytes, viaBuffer,
                samplers, images, packed);
        if (first != null) {
            System.out.println("[Celtium] " + first);
        }
    }

    /** Prints the window's {@code CELTIUM_PACK_UNIFORM} lines, on the pass timings' cadence. */
    static void reportWindow(long frames) {
        for (String line : LEDGER.windowLines(frames)) {
            System.out.println("[Celtium] " + line);
        }
    }

    /**
     * The census' counting: window totals plus, per program, the first-sight record and the
     * lane-diff state. The previous packed block survives window resets - the diff is between
     * consecutive packs, not between windows - while the histogram is windowed like the counters.
     */
    static final class Ledger {
        private final boolean laneCensus;

        private long regionsShadow;
        private long regionsGbuffers;
        private long slotClaims;
        private long packWalks;
        private long setAllocs;
        private long setHits;
        private long setMisses;
        private long skippedRegions;

        private final IdentityHashMap<Object, ProgramState> programs = new IdentityHashMap<>();

        private final IdentityHashMap<Object, PipelineState> pipelines = new IdentityHashMap<>();
        private final List<PipelineState> pipelineOrder = new ArrayList<>();
        private int pipelineSeq;

        Ledger(boolean laneCensus) {
            this.laneCensus = laneCensus;
        }

        private static final class PipelineState {
            private int seq;
            private String route;
            private int sets;
            private int maxSets;
            private long hits;
            private long misses;
            private long allocs;
            private long poolFailures;
            private long capFailures;

            private boolean active() {
                return this.hits > 0L || this.misses > 0L || this.allocs > 0L
                        || this.poolFailures > 0L || this.capFailures > 0L;
            }

            private void resetWindow() {
                this.hits = 0L;
                this.misses = 0L;
                this.allocs = 0L;
                this.poolFailures = 0L;
                this.capFailures = 0L;
                this.maxSets = this.sets;
            }
        }

        private PipelineState pipelineState(Object pipeline, String route) {
            PipelineState state = this.pipelines.get(pipeline);
            if (state == null) {
                state = new PipelineState();
                state.seq = ++this.pipelineSeq;
                state.route = route;
                this.pipelines.put(pipeline, state);
                this.pipelineOrder.add(state);
            }
            return state;
        }

        void notePipelineSetHit(Object pipeline, String route) {
            pipelineState(pipeline, route).hits++;
        }

        void notePipelineSetMiss(Object pipeline, String route) {
            pipelineState(pipeline, route).misses++;
        }

        void notePipelineSetAlloc(Object pipeline, String route, int population) {
            PipelineState state = pipelineState(pipeline, route);
            state.allocs++;
            state.sets = population;
            if (population > state.maxSets) {
                state.maxSets = population;
            }
        }

        void notePipelineAllocFailure(Object pipeline, String route, boolean capped) {
            PipelineState state = pipelineState(pipeline, route);
            if (capped) {
                state.capFailures++;
            } else {
                state.poolFailures++;
            }
        }

        private static final class ProgramState {
            private int programId;
            private int blockBytes;
            private byte[] previousBlock;
            private long[] laneHistogram;
        }

        void noteRegion(boolean shadowStage) {
            if (shadowStage) {
                this.regionsShadow++;
            } else {
                this.regionsGbuffers++;
            }
        }

        void noteSkippedRegion() {
            this.skippedRegions++;
        }

        void noteSlotClaim() {
            this.slotClaims++;
        }

        void noteSetHit() {
            this.setHits++;
        }

        void noteSetMiss() {
            this.setMisses++;
        }

        void noteSetAlloc() {
            this.setAllocs++;
        }

        /**
         * Files one pack. {@return the program's first-sight line}, or null when the program was
         * already filed. The key is the program's identity; the remaining arguments are the shape
         * the first-sight line prints, extracted by the facade so this class stays testable without
         * a lowered program.
         */
        String notePack(Object key, int programId, int members, int blockBytes, boolean viaBuffer,
                int samplers, int images, ByteBuffer packed) {
            this.packWalks++;
            ProgramState state = this.programs.get(key);
            String first = null;
            if (state == null) {
                state = new ProgramState();
                state.programId = programId;
                state.blockBytes = blockBytes;
                this.programs.put(key, state);
                first = "CELTIUM_PACK_UNIFORM_PROGRAM program=" + programId
                        + " members=" + members
                        + " blockBytes=" + blockBytes
                        + " route=" + (viaBuffer ? "ubo" : "push")
                        + " samplers=" + samplers
                        + " images=" + images;
            }
            if (this.laneCensus && packed != null && blockBytes > 0) {
                laneDiff(state, packed, blockBytes);
            }
            return first;
        }

        private void laneDiff(ProgramState state, ByteBuffer packed, int blockBytes) {
            int length = Math.min(blockBytes, packed.capacity());
            byte[] current = new byte[length];
            ByteBuffer view = packed.duplicate();
            view.clear();
            view.get(current, 0, length);
            byte[] previous = state.previousBlock;
            if (previous != null && previous.length == length) {
                int lanes = (length + 15) / 16;
                if (state.laneHistogram == null || state.laneHistogram.length != lanes) {
                    state.laneHistogram = new long[lanes];
                }
                for (int lane = 0; lane < lanes; lane++) {
                    int from = lane * 16;
                    int to = Math.min(from + 16, length);
                    for (int i = from; i < to; i++) {
                        if (current[i] != previous[i]) {
                            state.laneHistogram[lane]++;
                            break;
                        }
                    }
                }
            }
            state.previousBlock = current;
        }

        /**
         * {@return the ended window's lines} - the counter line, then one lane line per program
         * whose histogram moved - and resets the window's counters and histograms. An idle window
         * returns no lines.
         */
        List<String> windowLines(long frames) {
            List<String> lines = new ArrayList<>();
            boolean active = this.regionsShadow > 0L || this.regionsGbuffers > 0L
                    || this.slotClaims > 0L || this.packWalks > 0L || this.setAllocs > 0L
                    || this.setHits > 0L || this.setMisses > 0L || this.skippedRegions > 0L;
            List<String> pipelineLines = new ArrayList<>();
            for (PipelineState state : this.pipelineOrder) {
                if (!state.active()) {
                    continue;
                }
                pipelineLines.add("CELTIUM_PACK_UNIFORM_PIPELINE pipeline=" + state.seq
                        + " route=" + state.route
                        + " sets=" + state.sets
                        + " maxSets=" + state.maxSets
                        + " hits=" + state.hits
                        + " misses=" + state.misses
                        + " allocs=" + state.allocs
                        + " allocFailures=" + state.poolFailures
                        + " capFailures=" + state.capFailures);
            }
            if (!active && pipelineLines.isEmpty()) {
                for (PipelineState state : this.pipelineOrder) {
                    state.resetWindow();
                }
                return lines;
            }
            if (active) {
                lines.add("CELTIUM_PACK_UNIFORM frames=" + frames
                    + " regionsShadow=" + this.regionsShadow
                    + " regionsGbuffers=" + this.regionsGbuffers
                    + " slotClaims=" + this.slotClaims
                    + " packWalks=" + this.packWalks
                    + " setAllocs=" + this.setAllocs
                    + " setHits=" + this.setHits
                    + " setMisses=" + this.setMisses
                    + " skippedRegions=" + this.skippedRegions);
                lines.addAll(pipelineLines);
            } else {
                lines.addAll(pipelineLines);
            }
            if (this.laneCensus) {
                for (ProgramState state : this.programs.values()) {
                    if (state.laneHistogram == null) {
                        continue;
                    }
                    StringBuilder changed = new StringBuilder();
                    for (int lane = 0; lane < state.laneHistogram.length; lane++) {
                        if (state.laneHistogram[lane] > 0L) {
                            if (changed.length() > 0) {
                                changed.append(' ');
                            }
                            changed.append(lane).append('=').append(state.laneHistogram[lane]);
                        }
                    }
                    if (changed.length() > 0) {
                        lines.add("CELTIUM_PACK_UNIFORM_LANES program=" + state.programId
                                + " blockBytes=" + state.blockBytes
                                + " changed{" + changed + "}");
                    }
                }
            }
            this.regionsShadow = 0L;
            this.regionsGbuffers = 0L;
            this.slotClaims = 0L;
            this.packWalks = 0L;
            this.setAllocs = 0L;
            this.setHits = 0L;
            this.setMisses = 0L;
            this.skippedRegions = 0L;
            for (ProgramState state : this.programs.values()) {
                if (state.laneHistogram != null) {
                    java.util.Arrays.fill(state.laneHistogram, 0L);
                }
            }
            for (PipelineState state : this.pipelineOrder) {
                state.resetWindow();
            }
            return lines;
        }

        long packWalks() {
            return this.packWalks;
        }

        long slotClaims() {
            return this.slotClaims;
        }
    }
}
