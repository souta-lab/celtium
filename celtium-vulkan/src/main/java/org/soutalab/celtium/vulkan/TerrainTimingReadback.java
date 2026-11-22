package org.soutalab.celtium.vulkan;

import java.util.ArrayDeque;
import java.util.Map;

import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

/**
 * Which submitted frame's timestamp state a readback may consume, and what happens to the rest.
 * <p>
 * The timestamp pool is shared by every frame: each recording resets the same query range and writes
 * the same slots, so a frame's results live in the pool only until the next submission executes.
 * The readback used to run at the next {@code beginCommands} against the live accumulators - which
 * by then describe the newest recorded frame, a submission that under the four-slot pipeline has
 * usually not completed yet. Its queries read back as unavailable (the submission's own reset has
 * executed, its writes have not), the sum was filed as zero, and the accumulators were cleared
 * either way, so when that submission did complete there was nothing left to read. Measured on the
 * generated-world leg (axis-perf-residual, 2026-11-22): the frame budget's terrain column was 0.0
 * at p50 and p95 across two runs while the window column beside it read normally, and no
 * CELTIUM_PASS_GPU line ever printed.
 * <p>
 * The fix is the shape the pipeline-statistics read already uses: state is captured per submission
 * and consumed only for a submission the scheduler has confirmed. At submit time the backend moves
 * the frame's timing state into a {@link Snapshot} here and clears the live accumulators; at the
 * readback point it asks this class for the newest snapshot the completed watermark covers. Older
 * confirmed snapshots are dropped and counted rather than read: the newer confirmed submission has
 * already executed over the shared pool, so their values are gone, and reading the pool against
 * their slot maps would file the newer frame's durations under the older frame's passes. A snapshot
 * the watermark has not reached stays queued - its values are still in the pool, behind a submission
 * that has not finished.
 * <p>
 * State and decision only, like the other seams in this package: the backend owns the pool read
 * itself and calls {@link #consume} only after a read lands, so a failed read leaves the snapshot
 * queued for the next begin (where a newer confirmation may legitimately drop it as overwritten).
 * Touched only from the backend's submit and timing paths, both on the client thread.
 */
final class TerrainTimingReadback {

    /** One submitted frame's timestamp state, captured at submit time and immutable afterwards. */
    static final class Snapshot {
        /** The submission sequence this state belongs to (the value the bookkeeping stamps). */
        final long sequence;
        /** The frame the state was recorded in, for the census lines that name it. */
        final long frameId;
        /** How many pass slots the frame timed (queries {@code 0..passSlots*2}). */
        final int passSlots;
        /** Which pass each timed slot belonged to, by slot index. */
        final Map<Integer, TerrainRenderPass> passes;
        /** How many window slots the frame timed, and the target each belonged to. */
        final int windowCount;
        final long[] windowTargets;
        /** Whether the frame timed its windows and its whole-frame pair. */
        final boolean immediateTimed;
        final boolean frameTimed;
        /** How many labelled marks the frame wrote, and their labels in recording order. */
        final int markCount;
        final String[] markLabels;

        Snapshot(long sequence, long frameId, int passSlots, Map<Integer, TerrainRenderPass> passes,
                int windowCount, long[] windowTargets, boolean immediateTimed, boolean frameTimed,
                int markCount, String[] markLabels) {
            this.sequence = sequence;
            this.frameId = frameId;
            this.passSlots = passSlots;
            this.passes = passes;
            this.windowCount = windowCount;
            this.windowTargets = windowTargets;
            this.immediateTimed = immediateTimed;
            this.frameTimed = frameTimed;
            this.markCount = markCount;
            this.markLabels = markLabels;
        }
    }

    /** Snapshots in submit order; sequences strictly increase along the queue. */
    private final ArrayDeque<Snapshot> pending = new ArrayDeque<>();
    /** Confirmed snapshots dropped unread because a newer confirmed submission overwrote the pool. */
    private long droppedOverwritten;

    /** Queues one submitted frame's state. Called at submit time, before the next recording opens. */
    void offer(Snapshot snapshot) {
        this.pending.addLast(snapshot);
    }

    /**
     * {@return the snapshot a readback may consume now}: the newest one whose sequence the completed
     * watermark covers, or null when no queued submission has been confirmed yet. The snapshot stays
     * queued until {@link #consume}; asking twice without consuming answers the same snapshot.
     * <p>
     * Side effect: every confirmed snapshot older than the returned one is dropped and counted. Its
     * values were overwritten when the newer confirmed submission executed, so dropping is not a
     * choice the caller gets to revisit - it is recorded here so the loss is a number on the census
     * line rather than a silent zero in the terrain column.
     */
    Snapshot peekReadable(long completedSequence) {
        Snapshot newest = null;
        for (Snapshot snapshot : this.pending) {
            if (snapshot.sequence > completedSequence) {
                break;
            }
            newest = snapshot;
        }
        if (newest == null) {
            return null;
        }
        while (this.pending.peekFirst() != null && this.pending.peekFirst() != newest) {
            this.pending.pollFirst();
            this.droppedOverwritten++;
        }
        return newest;
    }

    /** Removes a snapshot whose readback landed. Identity-based: only that snapshot goes. */
    void consume(Snapshot snapshot) {
        this.pending.remove(snapshot);
    }

    /** How many submitted frames' states are still waiting for their confirmation or their read. */
    int pendingCount() {
        return this.pending.size();
    }

    /** How many confirmed snapshots were dropped unread, cumulative over the run. */
    long droppedOverwritten() {
        return this.droppedOverwritten;
    }
}
