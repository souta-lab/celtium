package org.soutalab.celtium.vulkan;

import java.util.HashMap;
import java.util.Map;

/**
 * Item 719(c): the rate cap ("歯止め") for the descriptor-set-cap safety net.
 * <p>
 * When a {@link VulkanModProgramPipeline}'s set population reaches the 4096 cap, its owner
 * regenerates that pipeline alone at the next resolution point, which restores service in one
 * pass. On a route whose key space is genuinely unbounded (a mod recreating textures every
 * frame), regeneration would just trade a permanent skip for a permanent rebuild hitch, so
 * regenerations are capped per owner key: at most {@link #MAX_REGENS_PER_WINDOW} inside a
 * sliding {@link #WINDOW_MS} window. Past the cap the owner falls back to the pre-net behaviour
 * (the failing lookup returns 0 and the draw/pass is skipped) until the window slides.
 * <p>
 * Keys are the owner's own identity for the pipeline (the terrain backend's pipeKey string,
 * PassExecutor's PipelineKey); two owners never share a guard instance. All state is confined
 * to the render thread by the owners' call sites, but the methods are synchronized anyway so
 * the guard stays correct if a future owner resolves pipelines from another thread.
 */
final class PipelineRegenGuard {

    static final long WINDOW_MS = 60_000L;
    static final int MAX_REGENS_PER_WINDOW = 3;

    private static final class KeyState {
        /** Timestamps of the regenerations still inside the current window, oldest first. */
        final long[] stamps = new long[MAX_REGENS_PER_WINDOW];
        int count;
        /** When this key's suppression was last logged; suppressions log at most once per window. */
        long suppressionLoggedAt = Long.MIN_VALUE;
    }

    private final Map<Object, KeyState> states = new HashMap<>();

    /**
     * Records a regeneration attempt for {@code key} at {@code nowMillis}.
     *
     * @return true if the regeneration is allowed (and has been recorded); false if the key is
     * already at the window cap, in which case nothing is recorded
     */
    synchronized boolean tryAcquire(Object key, long nowMillis) {
        KeyState state = prune(key, nowMillis);
        if (state.count >= MAX_REGENS_PER_WINDOW) {
            return false;
        }
        // count < MAX here, and prune() compacted the survivors to the front.
        state.stamps[state.count++] = nowMillis;
        return true;
    }

    /**
     * @return true exactly once per window for {@code key}, so the owner can log a suppression
     * without spamming one line per skipped pass
     */
    synchronized boolean shouldLogSuppression(Object key, long nowMillis) {
        KeyState state = prune(key, nowMillis);
        if (state.suppressionLoggedAt != Long.MIN_VALUE
                && nowMillis - state.suppressionLoggedAt < WINDOW_MS) {
            return false;
        }
        state.suppressionLoggedAt = nowMillis;
        return true;
    }

    /** @return how many regenerations for {@code key} are still inside the window at {@code nowMillis} */
    synchronized int recentCount(Object key, long nowMillis) {
        return prune(key, nowMillis).count;
    }

    /** Drops stamps at or before {@code nowMillis - WINDOW_MS} and compacts the survivors. */
    private KeyState prune(Object key, long nowMillis) {
        KeyState state = this.states.computeIfAbsent(key, k -> new KeyState());
        long cutoff = nowMillis - WINDOW_MS;
        int kept = 0;
        for (int i = 0; i < state.count; i++) {
            if (state.stamps[i] > cutoff) {
                state.stamps[kept++] = state.stamps[i];
            }
        }
        state.count = kept;
        return state;
    }
}
