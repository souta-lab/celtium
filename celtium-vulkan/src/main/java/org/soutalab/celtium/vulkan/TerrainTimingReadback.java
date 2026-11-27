package org.soutalab.celtium.vulkan;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;

import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

/**
 * Which submitted frame's timestamp state a readback may consume, and what happens to the rest.
 * <p>
 * The timestamp pool is partitioned by frame slot: each frame slot records into its own query
 * range of {@code TIMESTAMP_SLOTS} queries (the same partition the pipeline-statistics pool
 * already uses), so a frame's results live in its slot's range until that slot is recorded
 * again - not, as in the shared-range shape this replaces, only until the next submission
 * executes. The readback therefore consumes snapshots in submit order: the oldest queued
 * snapshot the completed watermark covers is the one read, and a queued snapshot is dropped
 * only when a newer submission re-records its frame slot (its range has been reset and
 * rewritten by then, so its values are gone in fact, not by policy). Measured before this
 * partition, on the Demonica+BSL pack scene (shader-breakdown, 2026-11-27): of 247 submitted
 * frames' snapshots the readback consumed 129 and dropped 118 overwritten - on a CPU-bound
 * scene the GPU drains the whole queue between two readbacks, so under a shared range nearly
 * half the frames' timings were lost before they could be read, and the terrain column and
 * the pass report starved with them.
 * <p>
 * State and decision only, like the other seams in this package: the backend owns the pool read
 * itself and calls {@link #consume} only after a read lands, so a failed read leaves the snapshot
 * queued for the next begin (where its slot's re-recording may legitimately drop it as
 * overwritten). Touched only from the backend's submit and timing paths, both on the client thread.
 */
final class TerrainTimingReadback {

    /** One submitted frame's timestamp state, captured at submit time and immutable afterwards. */
    static final class Snapshot {
        /** The submission sequence this state belongs to (the value the bookkeeping stamps). */
        final long sequence;
        /** The frame the state was recorded in, for the census lines that name it. */
        final long frameId;
        /** The offset of this frame slot's query range within the pool. */
        final int poolBase;
        /** How many pass slots the frame timed (queries {@code poolBase..poolBase+passSlots*2}). */
        final int passSlots;
        /** Which pass each natively timed slot belonged to, by slot index. */
        final Map<Integer, TerrainRenderPass> passes;
        /**
         * The printed name of each pack-route timed slot, by slot index: the pack stage
         * ({@code shadow} / {@code gbuffers}) rather than a {@link TerrainRenderPass}, because the
         * pack route's passes are the pack pipeline's stages - the same subpass object serves the
         * shadow stage and the gbuffers stage in one frame, so the pass object cannot name them apart.
         */
        final Map<Integer, String> packPassNames;
        /** How many window slots the frame timed, and the target each belonged to. */
        final int windowCount;
        final long[] windowTargets;
        /** Whether the frame timed its windows and its whole-frame pair. */
        final boolean immediateTimed;
        final boolean frameTimed;
        /** How many labelled marks the frame wrote, and their labels in recording order. */
        final int markCount;
        final String[] markLabels;

        Snapshot(long sequence, long frameId, int poolBase, int passSlots,
                Map<Integer, TerrainRenderPass> passes, Map<Integer, String> packPassNames,
                int windowCount, long[] windowTargets, boolean immediateTimed, boolean frameTimed,
                int markCount, String[] markLabels) {
            this.sequence = sequence;
            this.frameId = frameId;
            this.poolBase = poolBase;
            this.passSlots = passSlots;
            this.passes = passes;
            this.packPassNames = packPassNames;
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
    /** Snapshots dropped unread because their frame slot was re-recorded before they were read. */
    private long droppedOverwritten;

    /**
     * Queues one submitted frame's state. Called at submit time, before the next recording opens.
     * <p>
     * A still-queued snapshot for the same frame slot is dropped and counted here: the submission
     * being queued re-recorded that slot's query range, so the older snapshot's values were
     * overwritten during that recording and no later read could land them.
     */
    void offer(Snapshot snapshot) {
        for (Iterator<Snapshot> iterator = this.pending.iterator(); iterator.hasNext();) {
            if (iterator.next().poolBase == snapshot.poolBase) {
                iterator.remove();
                this.droppedOverwritten++;
            }
        }
        this.pending.addLast(snapshot);
    }

    /**
     * {@return the snapshot a readback may consume now}: the oldest queued one whose sequence the
     * completed watermark covers, or null when the oldest queued submission has not been confirmed
     * yet (confirmation is in queue order, so no later snapshot can be confirmed either). The
     * snapshot stays queued until {@link #consume}; asking twice without consuming answers the
     * same snapshot, so a failed pool read is retryable against it.
     */
    Snapshot peekReadable(long completedSequence) {
        Snapshot head = this.pending.peekFirst();
        if (head == null || head.sequence > completedSequence) {
            return null;
        }
        return head;
    }

    /** Removes a snapshot whose readback landed. Identity-based: only that snapshot goes. */
    void consume(Snapshot snapshot) {
        this.pending.remove(snapshot);
    }

    /** How many submitted frames' states are still waiting for their confirmation or their read. */
    int pendingCount() {
        return this.pending.size();
    }

    /** How many queued snapshots were dropped unread, cumulative over the run. */
    long droppedOverwritten() {
        return this.droppedOverwritten;
    }
}
