package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The item-719(c) safety net's rate cap: a key may regenerate at most
 * {@link PipelineRegenGuard#MAX_REGENS_PER_WINDOW} times inside the sliding window, the cap is
 * per key, the window sliding restores the allowance, and a suppression is loggable only once
 * per window so a capped pipeline cannot spam the log once per skipped pass.
 */
class PipelineRegenGuardTest {

    @Test void allowsUpToTheCapThenRefuses() {
        PipelineRegenGuard guard = new PipelineRegenGuard();
        long t0 = 1_000_000L;
        for (int i = 0; i < PipelineRegenGuard.MAX_REGENS_PER_WINDOW; i++) {
            assertTrue(guard.tryAcquire("k", t0 + i), "regen " + i + " must be allowed");
        }
        assertFalse(guard.tryAcquire("k", t0 + 10), "the regen past the cap must be refused");
        assertEquals(PipelineRegenGuard.MAX_REGENS_PER_WINDOW, guard.recentCount("k", t0 + 10),
                "a refused regen must not be recorded");
    }

    @Test void capIsPerKey() {
        PipelineRegenGuard guard = new PipelineRegenGuard();
        long t0 = 2_000_000L;
        for (int i = 0; i < PipelineRegenGuard.MAX_REGENS_PER_WINDOW; i++) {
            assertTrue(guard.tryAcquire("a", t0 + i));
        }
        assertFalse(guard.tryAcquire("a", t0 + 10));
        assertTrue(guard.tryAcquire("b", t0 + 10), "another key keeps its own allowance");
    }

    @Test void windowSlidingRestoresTheAllowance() {
        PipelineRegenGuard guard = new PipelineRegenGuard();
        long t0 = 3_000_000L;
        for (int i = 0; i < PipelineRegenGuard.MAX_REGENS_PER_WINDOW; i++) {
            assertTrue(guard.tryAcquire("k", t0));
        }
        long afterWindow = t0 + PipelineRegenGuard.WINDOW_MS + 1;
        assertEquals(0, guard.recentCount("k", afterWindow));
        assertTrue(guard.tryAcquire("k", afterWindow), "a slid window must allow regeneration again");
    }

    @Test void partialSlideFreesOnlyTheExpiredStamps() {
        PipelineRegenGuard guard = new PipelineRegenGuard();
        long t0 = 4_000_000L;
        assertTrue(guard.tryAcquire("k", t0));
        assertTrue(guard.tryAcquire("k", t0 + 30_000L));
        assertTrue(guard.tryAcquire("k", t0 + 59_000L));
        // At t0+60s+1ms only the first stamp has expired: exactly one more regen fits.
        long t = t0 + PipelineRegenGuard.WINDOW_MS + 1;
        assertEquals(2, guard.recentCount("k", t));
        assertTrue(guard.tryAcquire("k", t));
        assertFalse(guard.tryAcquire("k", t));
    }

    @Test void suppressionIsLoggableOncePerWindow() {
        PipelineRegenGuard guard = new PipelineRegenGuard();
        long t0 = 5_000_000L;
        assertTrue(guard.shouldLogSuppression("k", t0));
        assertFalse(guard.shouldLogSuppression("k", t0 + 1_000L),
                "a second suppression inside the same window must not log again");
        long afterWindow = t0 + PipelineRegenGuard.WINDOW_MS + 1;
        // Drive a regen so the key has window state again, then the new window may log once.
        assertTrue(guard.tryAcquire("k", afterWindow));
        assertTrue(guard.shouldLogSuppression("k", afterWindow + 1),
                "a fresh window gets its own single suppression line");
        assertFalse(guard.shouldLogSuppression("k", afterWindow + 2));
    }
}
