package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallbackPathsTest {

    @BeforeEach
    void clearRegistry() {
        FallbackPaths.clearForTests();
    }

    @Test
    void countsAttemptsAndVerdictsPerPath() {
        FallbackPaths.Handle present = FallbackPaths.register(FallbackPaths.PRESENT_DIRECT);
        present.attempt();
        present.fast();
        present.attempt();
        present.fallback();
        present.attempt();
        present.fallback();

        assertEquals(3L, present.attempts());
        assertEquals(1L, present.fastHits());
        assertEquals(2L, present.fallbacks());
        // The contract the summary relies on: every attempt ends in exactly one verdict.
        assertEquals(present.attempts(), present.fastHits() + present.fallbacks());
    }

    @Test
    void pathsAreCountedIndependently() {
        FallbackPaths.attempt(FallbackPaths.SLAB_ALLOC);
        FallbackPaths.fast(FallbackPaths.SLAB_ALLOC);
        FallbackPaths.attempt(FallbackPaths.TERRAIN_ENGINE);
        FallbackPaths.fallback(FallbackPaths.TERRAIN_ENGINE);
        FallbackPaths.attempt(FallbackPaths.TERRAIN_ENGINE);
        FallbackPaths.fallback(FallbackPaths.TERRAIN_ENGINE);

        assertEquals(1L, FallbackPaths.register(FallbackPaths.SLAB_ALLOC).attempts());
        assertEquals(1L, FallbackPaths.register(FallbackPaths.SLAB_ALLOC).fastHits());
        assertEquals(0L, FallbackPaths.register(FallbackPaths.SLAB_ALLOC).fallbacks());
        assertEquals(2L, FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE).attempts());
        assertEquals(0L, FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE).fastHits());
        assertEquals(2L, FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE).fallbacks());
    }

    @Test
    void registeringTheSamePathTwiceSharesTheCounts() {
        FallbackPaths.Handle first = FallbackPaths.register("present.direct");
        first.attempt();
        first.fast();
        FallbackPaths.Handle second = FallbackPaths.register("present.direct");
        assertEquals(1L, second.attempts());
        assertEquals(1L, second.fastHits());
        second.attempt();
        second.fallback();
        assertEquals(2L, first.attempts());
        assertEquals(1L, first.fallbacks());
    }

    @Test
    void verdictRoutesToFastOrFallback() {
        FallbackPaths.Handle handle = FallbackPaths.register(FallbackPaths.GLSM_ASYNC);
        handle.attempt();
        handle.verdict(true);
        handle.attempt();
        handle.verdict(false);
        assertEquals(1L, handle.fastHits());
        assertEquals(1L, handle.fallbacks());
    }

    @Test
    void unnamedPathsAreRejected() {
        // An unnamed decision point would be invisible in the summary - the failure this
        // instrumentation exists to end - so registration refuses it instead of counting it.
        assertThrows(IllegalArgumentException.class, () -> FallbackPaths.register(null));
        assertThrows(IllegalArgumentException.class, () -> FallbackPaths.register(""));
    }

    @Test
    void summaryIsEmptyWhenNothingWasRegistered() {
        assertEquals("fallbackPaths=[]", FallbackPaths.summary());
    }

    @Test
    void summaryPrintsAttemptsFastFallbackAndRatePerPath() {
        FallbackPaths.Handle present = FallbackPaths.register(FallbackPaths.PRESENT_DIRECT);
        for (int i = 0; i < 4; i++) {
            present.attempt();
        }
        present.fast();
        present.fallback();
        present.fallback();
        present.fallback();

        String summary = FallbackPaths.summary();
        assertTrue(summary.startsWith("fallbackPaths=["), summary);
        assertTrue(summary.endsWith("]"), summary);
        assertTrue(summary.contains(
                "present.direct:attempts=4,fast=1,fallback=3,fastRate=0.250"), summary);
    }

    @Test
    void summarySortsTheBusiestPathFirst() {
        FallbackPaths.Handle quiet = FallbackPaths.register(FallbackPaths.GLSM_ASYNC);
        quiet.attempt();
        quiet.fallback();
        FallbackPaths.Handle busy = FallbackPaths.register(FallbackPaths.PRESENT_DIRECT);
        busy.attempt();
        busy.fast();
        busy.attempt();
        busy.fast();

        String summary = FallbackPaths.summary();
        int busyIndex = summary.indexOf("present.direct:");
        int quietIndex = summary.indexOf("glsm.async:");
        assertTrue(busyIndex >= 0 && quietIndex > busyIndex, summary);
    }

    @Test
    void summaryRateIsZeroWhenNothingWasAttempted() {
        FallbackPaths.register(FallbackPaths.SLAB_ALLOC);
        assertTrue(FallbackPaths.summary().contains(
                "slab.alloc:attempts=0,fast=0,fallback=0,fastRate=0.000"),
                FallbackPaths.summary());
    }
}
