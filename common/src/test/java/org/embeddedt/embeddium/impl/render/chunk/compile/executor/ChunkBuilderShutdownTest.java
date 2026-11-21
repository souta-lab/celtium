package org.embeddedt.embeddium.impl.render.chunk.compile.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * BH10-04: {@link ChunkBuilder#shutdown()} documents that an already-stopped builder is a
 * no-op, but the implementation threw {@link IllegalStateException}. The throw made
 * teardown non-retryable: a destroy() that failed partway (BH10-11) could never get past
 * this call on its second attempt, so the terrain backend behind it was never deleted.
 */
class ChunkBuilderShutdownTest {
    @Test
    void shutdownIsIdempotent() {
        // No worker threads: the queue alone carries the running state this test exercises.
        var builder = new ChunkBuilder(ChunkBuilder.ManagedBlocker.NONE, () -> null, -1);

        builder.shutdown();

        assertDoesNotThrow(builder::shutdown);
        assertDoesNotThrow(builder::shutdown);
    }
}
