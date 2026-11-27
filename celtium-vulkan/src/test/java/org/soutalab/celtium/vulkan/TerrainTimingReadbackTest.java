package org.soutalab.celtium.vulkan;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The generation rule behind the terrain timestamp readback, checked without a GPU.
 * <p>
 * What these tests can and cannot prove: they prove a readback is only ever handed the oldest
 * confirmed submission's snapshot (the pool is partitioned by frame slot, so an older confirmed
 * snapshot's values survive a newer submission's execution), that an unconfirmed submission's
 * state is never read and never dropped, and that a queued snapshot is dropped and counted only
 * when a newer submission re-records its own frame slot. They prove nothing about any driver's
 * query pool - the availability bits arbitrate that side, and the leg is the evidence.
 */
class TerrainTimingReadbackTest {

    private static TerrainTimingReadback.Snapshot snapshot(long sequence, int poolBase) {
        return new TerrainTimingReadback.Snapshot(sequence, sequence * 10L, poolBase, 0,
                Collections.emptyMap(), Collections.emptyMap(), 0, new long[0], false, false, 0,
                new String[0]);
    }

    @Test
    void nothingOfferedReadsAsNothingConfirmed() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        assertNull(readback.peekReadable(100L));
        assertEquals(0, readback.pendingCount());
        assertEquals(0L, readback.droppedOverwritten());
    }

    @Test
    void anUnconfirmedSnapshotIsNeitherReadNorDropped() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        readback.offer(snapshot(5L, 0));
        assertNull(readback.peekReadable(4L), "sequence 5 is not covered by a watermark of 4");
        assertEquals(1, readback.pendingCount());
        assertEquals(0L, readback.droppedOverwritten());
    }

    @Test
    void aConfirmedSnapshotIsReadableAndPeekingIsIdempotent() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        TerrainTimingReadback.Snapshot offered = snapshot(5L, 0);
        readback.offer(offered);
        assertSame(offered, readback.peekReadable(5L));
        assertSame(offered, readback.peekReadable(5L),
                "a failed pool read must be retryable against the same snapshot");
        assertEquals(1, readback.pendingCount(), "peeking does not consume");
        assertEquals(0L, readback.droppedOverwritten());
        readback.consume(offered);
        assertEquals(0, readback.pendingCount());
        assertNull(readback.peekReadable(5L), "a consumed snapshot is not read twice");
    }

    @Test
    void theOldestConfirmedSnapshotIsReadFirstAndTheNewerOneSurvives() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        TerrainTimingReadback.Snapshot oldest = snapshot(5L, 0);
        TerrainTimingReadback.Snapshot newer = snapshot(6L, 320);
        readback.offer(oldest);
        readback.offer(newer);
        assertSame(oldest, readback.peekReadable(6L),
                "each frame slot owns its query range, so sequence 6's execution left sequence 5's values intact");
        assertEquals(0L, readback.droppedOverwritten(), "nothing was overwritten, so nothing is dropped");
        assertEquals(2, readback.pendingCount());
        readback.consume(oldest);
        assertSame(newer, readback.peekReadable(6L), "consumption advances in submit order");
    }

    @Test
    void anUnconfirmedNewerSnapshotDoesNotInvalidateTheConfirmedOne() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        TerrainTimingReadback.Snapshot confirmed = snapshot(5L, 0);
        readback.offer(confirmed);
        readback.offer(snapshot(6L, 320));
        assertSame(confirmed, readback.peekReadable(5L),
                "sequence 6 has not completed, so sequence 5 is the oldest confirmed");
        assertEquals(0L, readback.droppedOverwritten());
        readback.consume(confirmed);
        assertSame(readback.peekReadable(6L), readback.peekReadable(6L));
        assertEquals(1, readback.pendingCount());
    }

    @Test
    void reRecordingAFrameSlotDropsItsQueuedSnapshotAndCountsIt() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        readback.offer(snapshot(5L, 0));
        readback.offer(snapshot(6L, 320));
        TerrainTimingReadback.Snapshot reRecorded = snapshot(7L, 0);
        readback.offer(reRecorded);
        assertEquals(1L, readback.droppedOverwritten(),
                "sequence 7 re-recorded slot 0's range, so sequence 5's values are gone in fact");
        assertEquals(2, readback.pendingCount(), "the other slot's snapshot is untouched");
        assertSame(readback.peekReadable(7L), readback.peekReadable(7L));
        assertEquals(320, readback.peekReadable(7L).poolBase,
                "the surviving oldest snapshot is the other slot's");
    }

    @Test
    void severalConfirmedSnapshotsQueueInSubmitOrder() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        TerrainTimingReadback.Snapshot first = snapshot(5L, 0);
        readback.offer(first);
        readback.offer(snapshot(6L, 320));
        readback.offer(snapshot(7L, 640));
        assertSame(first, readback.peekReadable(7L));
        assertEquals(0L, readback.droppedOverwritten());
        assertEquals(3, readback.pendingCount());
    }
}
