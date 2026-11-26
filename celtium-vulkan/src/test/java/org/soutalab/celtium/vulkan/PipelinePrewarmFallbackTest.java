package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fallback instrument and the asynchronous half of the prewarm wiring (Stage 5 slice
 * S5-0): the counter that keeps a warm window with zero synchronous pipeline creations
 * honest, and the teardown wait that only a compile in flight can hold up.
 */
class PipelinePrewarmFallbackTest {

    @Test
    void fallbackCounterStartsAtZeroAndCountsEachNotedDraw() {
        PipelinePrewarm prewarm = new PipelinePrewarm(key -> true);
        assertEquals(0, prewarm.fallbackDraws());
        prewarm.noteFallbackDraw();
        prewarm.noteFallbackDraw();
        assertEquals(2, prewarm.fallbackDraws());
        prewarm.close();
    }

    @Test
    void summaryReportsFallbackReadyAndFailedCounts() throws Exception {
        PipelinePrewarm prewarm = new PipelinePrewarm(key -> key != 99L);
        prewarm.registerStartup(1);
        prewarm.registerStartup(2);
        assertEquals(0, prewarm.warmUp(Integer.MAX_VALUE));
        prewarm.request(99L);
        assertTrue(prewarm.awaitIdle(5000));
        prewarm.noteFallbackDraw();
        prewarm.noteFallbackDraw();
        prewarm.noteFallbackDraw();
        assertEquals("prewarmFallbackDraws=3 prewarmReady=2 prewarmFailed=1", prewarm.summary());
        prewarm.close();
    }

    @Test
    void unknownKeysFailThroughTheCompilerAndAreCounted() throws Exception {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        PipelinePrewarm prewarm = new PipelinePrewarm(compiler);
        prewarm.request(4242L);
        assertTrue(prewarm.awaitIdle(5000));
        assertTrue(prewarm.isFailed(4242L));
        assertTrue(prewarm.needsFallback(4242L));
        assertTrue(compiler.summary().contains("prewarmUnknownKeys=1"),
                compiler.summary());
        prewarm.close();
    }

    @Test
    void registeredCreatorsCompileThroughTheCompiler() throws Exception {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        compiler.register(5L, key -> true);
        PipelinePrewarm prewarm = new PipelinePrewarm(compiler);
        prewarm.request(5L);
        assertTrue(prewarm.awaitIdle(5000));
        assertTrue(prewarm.isReady(5L));
        assertFalse(prewarm.needsFallback(5L));
        assertTrue(compiler.summary().contains("prewarmCompiled=1"), compiler.summary());
        prewarm.close();
    }

    @Test
    void awaitCompilesIdleWaitsOnlyForTheCompileInFlight() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // A compile that cannot be interrupted, the way a native vkCreateGraphicsPipelines
        // cannot: close() interrupts the worker, and the compile is still in flight after it.
        PipelinePrewarm prewarm = new PipelinePrewarm(key -> {
            started.countDown();
            boolean done = false;
            while (!done) {
                try {
                    done = release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    // Keep waiting: the interrupt is close() stopping the worker, not the
                    // compile finishing.
                }
            }
            return true;
        });
        prewarm.request(1L);
        assertTrue(started.await(5000, TimeUnit.MILLISECONDS));
        prewarm.request(2L);
        prewarm.close();
        assertFalse(prewarm.awaitCompilesIdle(200));
        release.countDown();
        // Key 2 stays queued forever after close(); the wait must not care.
        assertTrue(prewarm.awaitCompilesIdle(5000));
    }
}
