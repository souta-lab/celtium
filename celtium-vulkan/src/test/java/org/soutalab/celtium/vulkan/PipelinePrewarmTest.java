package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelinePrewarmTest {

    private static final class FakeCompiler implements PipelinePrewarm.Compiler {
        final Set<Long> compiled = new HashSet<Long>();
        final Set<Long> failing = new HashSet<Long>();

        @Override
        public synchronized boolean compile(long pipelineKey) {
            if (this.failing.contains(pipelineKey)) {
                return false;
            }
            this.compiled.add(pipelineKey);
            return true;
        }
    }

    @Test
    void warmUpCompilesUpToTheBudgetAndReportsTheRest() {
        FakeCompiler compiler = new FakeCompiler();
        PipelinePrewarm prewarm = new PipelinePrewarm(compiler);
        prewarm.registerStartup(1);
        prewarm.registerStartup(2);
        prewarm.registerStartup(3);
        assertEquals(2, prewarm.warmUp(1));
        assertEquals(1, prewarm.readyCount());
        assertEquals(0, prewarm.warmUp(10));
        assertEquals(3, prewarm.readyCount());
        assertFalse(prewarm.needsFallback(2));
        prewarm.close();
    }

    @Test
    void requestedKeysCompileInTheBackground() throws Exception {
        FakeCompiler compiler = new FakeCompiler();
        PipelinePrewarm prewarm = new PipelinePrewarm(compiler);
        assertTrue(prewarm.needsFallback(77));
        prewarm.request(77);
        assertTrue(prewarm.awaitIdle(5000));
        assertTrue(prewarm.isReady(77));
        assertFalse(prewarm.needsFallback(77));
        prewarm.close();
    }

    @Test
    void failedCompilesAreTerminalUntilRetried() throws Exception {
        FakeCompiler compiler = new FakeCompiler();
        compiler.failing.add(9L);
        PipelinePrewarm prewarm = new PipelinePrewarm(compiler);
        prewarm.request(9);
        assertTrue(prewarm.awaitIdle(5000));
        assertTrue(prewarm.isFailed(9));
        assertEquals(1, prewarm.failedCount());
        // A repeated request does not silently retry.
        prewarm.request(9);
        assertTrue(prewarm.awaitIdle(5000));
        assertTrue(prewarm.isFailed(9));
        compiler.failing.remove(9L);
        prewarm.retry(9);
        assertTrue(prewarm.awaitIdle(5000));
        assertTrue(prewarm.isReady(9));
        prewarm.close();
    }

    @Test
    void duplicateRequestsCompileOnce() throws Exception {
        final int[] calls = {0};
        PipelinePrewarm prewarm = new PipelinePrewarm(new PipelinePrewarm.Compiler() {
            @Override
            public boolean compile(long pipelineKey) {
                calls[0]++;
                return true;
            }
        });
        prewarm.request(5);
        prewarm.request(5);
        assertTrue(prewarm.awaitIdle(5000));
        assertEquals(1, calls[0]);
        prewarm.close();
    }
}
