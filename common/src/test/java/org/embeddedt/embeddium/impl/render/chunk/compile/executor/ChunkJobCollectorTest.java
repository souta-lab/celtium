package org.embeddedt.embeddium.impl.render.chunk.compile.executor;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completion must mean the results have been delivered, not merely produced: {@code onJobFinished}
 * hands the result to the collector before it releases the completion permit {@code awaitCompletion}
 * counts. Releasing first let a caller that consumes on completion race the delivery.
 */
class ChunkJobCollectorTest {

    @Test
    void resultIsDeliveredBeforeCompletionIsSignalled() {
        List<Integer> permitsDuringDelivery = new ArrayList<>();
        List<ChunkJobResult<? extends ChunkTaskOutput>> delivered = new ArrayList<>();
        ChunkJobCollector[] self = new ChunkJobCollector[1];
        ChunkJobCollector collector = new ChunkJobCollector(10, result -> {
            delivered.add(result);
            permitsDuringDelivery.add(self[0].semaphore.availablePermits());
        });
        self[0] = collector;

        ChunkJobResult<ChunkTaskOutput> result = new ChunkJobResult.Success<>(null, 0L);
        collector.onJobFinished(result);

        assertEquals(List.of(0), permitsDuringDelivery);
        assertSame(result, delivered.get(0));
        assertEquals(1, collector.semaphore.availablePermits());
    }

    @Test
    void nullResultStillSignalsCompletionWithoutDelivering() {
        List<ChunkJobResult<? extends ChunkTaskOutput>> delivered = new ArrayList<>();
        ChunkJobCollector collector = new ChunkJobCollector(10, delivered::add);

        collector.onJobFinished(null);

        assertTrue(delivered.isEmpty());
        assertEquals(1, collector.semaphore.availablePermits());
    }
}
