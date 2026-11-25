package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelinePrewarmCompilerTest {

    @Test
    void registeredCreatorIsInvokedWithItsKey() throws Exception {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        List<Long> seen = new ArrayList<Long>();
        compiler.register(7L, key -> {
            seen.add(key);
            return true;
        });
        assertTrue(compiler.compile(7L));
        assertEquals(List.of(7L), seen);
        assertEquals(1, compiler.registeredCount());
    }

    @Test
    void unknownKeyIsAnHonestFailure() throws Exception {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        assertFalse(compiler.compile(99L), "no registered action: not ready, and not a fake success");
        assertTrue(compiler.summary().contains("prewarmUnknownKeys=1"));
    }

    @Test
    void fallbackCreatorHandlesUnregisteredKeys() throws Exception {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        compiler.setFallbackCreator(key -> key == 5L);
        assertTrue(compiler.compile(5L));
        assertFalse(compiler.compile(6L));
    }

    @Test
    void specificRegistrationWinsOverTheFallback() throws Exception {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        compiler.setFallbackCreator(key -> false);
        compiler.register(5L, key -> true);
        assertTrue(compiler.compile(5L));
    }

    @Test
    void creatorFailureAndExceptionPropagate() throws Exception {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        compiler.register(1L, key -> false);
        compiler.register(2L, key -> {
            throw new IllegalStateException("vkCreateGraphicsPipelines refused");
        });
        assertFalse(compiler.compile(1L));
        assertThrows(IllegalStateException.class, () -> compiler.compile(2L));
    }

    @Test
    void drivesPipelinePrewarmWarmUpAndBackgroundRequests() throws Exception {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        compiler.setFallbackCreator(key -> true);
        PipelinePrewarm prewarm = new PipelinePrewarm(compiler);
        prewarm.registerStartup(10L);
        prewarm.registerStartup(11L);
        assertEquals(0, prewarm.warmUp(10));
        assertEquals(2, prewarm.readyCount());
        prewarm.request(12L);
        assertTrue(prewarm.awaitIdle(5000));
        assertTrue(prewarm.isReady(12L));
        prewarm.close();
    }

    @Test
    void unregisteredStartupKeysStayNotReady() {
        PipelinePrewarmCompiler compiler = new PipelinePrewarmCompiler();
        PipelinePrewarm prewarm = new PipelinePrewarm(compiler);
        prewarm.registerStartup(20L);
        assertEquals(1, prewarm.warmUp(10), "a key nobody registered a creator for cannot warm up");
        assertTrue(prewarm.needsFallback(20L));
        prewarm.close();
    }
}
