package org.soutalab.celtium.vulkan;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The generation rule behind the terrain timestamp readback, checked without a GPU.
 * <p>
 * What these tests can and cannot prove: they prove a readback is only ever handed the newest
 * confirmed submission's snapshot, that an unconfirmed submission's state is never read and never
 * dropped, and that a confirmed snapshot overtaken by a newer confirmed one is dropped and counted
 * instead of read against a pool that no longer holds its values. They prove nothing about any
 * driver's query pool - the availability bits arbitrate that side, and the leg is the evidence.
 */
class TerrainTimingReadbackTest {

    private static TerrainTimingReadback.Snapshot snapshot(long sequence) {
        return new TerrainTimingReadback.Snapshot(sequence, sequence * 10L, 0,
                Collections.emptyMap(), 0, new long[0], false, false, 0, new String[0]);
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
        readback.offer(snapshot(5L));
        assertNull(readback.peekReadable(4L), "sequence 5 is not covered by a watermark of 4");
        assertEquals(1, readback.pendingCount());
        assertEquals(0L, readback.droppedOverwritten());
    }

    @Test
    void aConfirmedSnapshotIsReadableAndPeekingIsIdempotent() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        TerrainTimingReadback.Snapshot offered = snapshot(5L);
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
    void theNewestConfirmedSnapshotWinsAndTheOlderConfirmedIsDropped() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        readback.offer(snapshot(5L));
        TerrainTimingReadback.Snapshot newest = snapshot(6L);
        readback.offer(newest);
        assertSame(newest, readback.peekReadable(6L),
                "sequence 6 executed over the shared pool, so sequence 5's values are gone");
        assertEquals(1L, readback.droppedOverwritten(), "the loss is counted, not silent");
        assertEquals(1, readback.pendingCount(), "only the readable snapshot remains queued");
    }

    @Test
    void anUnconfirmedNewerSnapshotDoesNotInvalidateTheConfirmedOne() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        TerrainTimingReadback.Snapshot confirmed = snapshot(5L);
        readback.offer(confirmed);
        readback.offer(snapshot(6L));
        assertSame(confirmed, readback.peekReadable(5L),
                "sequence 6 has not completed, so sequence 5 is the newest confirmed");
        assertEquals(0L, readback.droppedOverwritten());
        readback.consume(confirmed);
        assertSame(readback.peekReadable(6L), readback.peekReadable(6L));
        assertEquals(1, readback.pendingCount());
    }

    @Test
    void severalConfirmedSnapshotsCollapseToTheNewest() {
        TerrainTimingReadback readback = new TerrainTimingReadback();
        readback.offer(snapshot(5L));
        readback.offer(snapshot(6L));
        TerrainTimingReadback.Snapshot newest = snapshot(7L);
        readback.offer(newest);
        assertSame(newest, readback.peekReadable(7L));
        assertEquals(2L, readback.droppedOverwritten());
        assertEquals(1, readback.pendingCount());
    }
}
