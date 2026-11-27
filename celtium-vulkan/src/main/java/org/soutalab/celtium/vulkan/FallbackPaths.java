package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-decision-point counters for the A-to-B overhaul's fast paths and their fallbacks: for every
 * named decision point, how many times the fast path was attempted, how many times it fired, and
 * how many times the frame (or allocation, or pass) fell back to the slow path instead.
 * <p>
 * This exists because of the failure mode the overhaul guardrails name the "chain of small deaths"
 * (OVERHAUL-GUARDRAILS-NEON-EVAL-2026-11-25, watch item 1): every stage's safe fallback is a sound
 * local decision - Stage 1's composed blit when the swapchain format cannot be borrowed, a
 * dedicated allocation when a slab cannot serve, the legacy terrain route when the new engine
 * refuses a section, a full sync when the GLSM front end meets a state it cannot defer - but if
 * real hardware trips <em>all</em> of them, the result is a renderer whose tests are green and
 * whose P50 never moves from the old engine's 4.920&nbsp;ms. A fallback nobody counts is a
 * fallback nobody fixes, so the counts live beside the Stage 0 census in the same diagnostics
 * line ({@link #summary()}, spliced into {@code BackendDiagnostics} next to
 * {@link VulkanContext#memoryAllocationSummary()}), where a leg's report shows not just that the
 * run was fast or slow but which path its frames actually took.
 * <p>
 * Counting semantics: an <em>attempt</em> is one decision event at the named point, not one frame -
 * a caller that retries a failed decision inside the same frame counts each retry, because each
 * retry is a real trip through the decision and hiding it would understate the fallback rate.
 * Every attempt ends in exactly one verdict, {@link Handle#fast()} or {@link Handle#fallback()},
 * so {@code fast + fallback == attempts} holds for every path whose callers keep the contract;
 * the summary prints all three numbers precisely so a caller that breaks the contract is visible
 * instead of silently absorbed.
 * <p>
 * Registration is deliberately trivial so later stages wire their own decision points without
 * touching this class: {@code FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE)} once (or the
 * one-shot statics {@link #attempt(String)}, {@link #fast(String)}, {@link #fallback(String)} at
 * the decision site - they resolve the same shared handle, so after the first call they cost one
 * map lookup and no allocation). Counts are cumulative for the process's life and are never
 * reset outside tests: a leg reads them from the diagnostics line at the end of its run.
 * <p>
 * Only decision points that are actually wired get a named constant here. The Stage 1 / Stage 3 /
 * Stage 5 names (present.direct, slab.alloc, glsm.async) were declared in advance and never wired,
 * so the reader-wiring pass (drawpath audit X-1) removed them: an unwired constant reads as a
 * promise the summary cannot keep, and {@link #register(String)} accepts any name, so a stage that
 * wires its point later declares its constant in the same change.
 */
public final class FallbackPaths {

    /** Stage 4: a section is drawn by the new Tier-A terrain engine (fast) or by the legacy route (fallback). */
    public static final String TERRAIN_ENGINE = "terrain.engine";
    /** Stage 4 (R-55): a section offered to Tier-A stays off the legacy upload (fast) or is uploaded to the legacy arena anyway (fallback). */
    public static final String TERRAIN_UPLOAD = "terrain.upload";
    /** Stage 2: a frame slot's completion is confirmed by one wait on the timeline semaphore (fast) or by a fence wait (fallback). */
    public static final String SYNC_TIMELINE = "sync.timeline";
    /** Stage 2: a frame ring slice reservation is served by the frame linear ring (fast) or the mesh promotes to buffers it owns (fallback). */
    public static final String RING_ALLOC = "ring.alloc";

    /** The shared handle per path; a handle owns its counts, so registration is the only map. */
    private static final Map<String, Handle> HANDLES = new ConcurrentHashMap<String, Handle>();

    private FallbackPaths() {
    }

    /**
     * One decision point's counters: attempts, fast-path verdicts and fallback verdicts, each
     * guarded by synchronizing on the counts array itself - the same discipline as the per-site
     * allocation census in {@link VulkanContext}. A handle is safe to share across threads and is
     * meant to be held in a {@code static final} field by the wiring site.
     */
    public static final class Handle {
        private final String path;
        /** {attempts, fast, fallback}. */
        private final long[] counts = new long[3];

        private Handle(String path) {
            this.path = path;
        }

        /** {@return the decision point's registered name}. */
        public String path() {
            return this.path;
        }

        /** Records one decision event at this point; pair it with exactly one verdict. */
        public void attempt() {
            synchronized (this.counts) {
                this.counts[0]++;
            }
        }

        /** Records that this attempt took the fast path. */
        public void fast() {
            synchronized (this.counts) {
                this.counts[1]++;
            }
        }

        /** Records that this attempt fell back to the slow path. */
        public void fallback() {
            synchronized (this.counts) {
                this.counts[2]++;
            }
        }

        /** Records the verdict of an attempt in one call: {@code true} for the fast path. */
        public void verdict(boolean fastPath) {
            if (fastPath) {
                fast();
            } else {
                fallback();
            }
        }

        /** {@return how many decisions this point has seen}. */
        public long attempts() {
            synchronized (this.counts) {
                return this.counts[0];
            }
        }

        /** {@return how many of those decisions took the fast path}. */
        public long fastHits() {
            synchronized (this.counts) {
                return this.counts[1];
            }
        }

        /** {@return how many of those decisions fell back}. */
        public long fallbacks() {
            synchronized (this.counts) {
                return this.counts[2];
            }
        }
    }

    /**
     * {@return the shared handle for {@code path}}, creating it on first use. Registering the same
     * path twice returns handles over the same counts, so a stage's wiring sites can each register
     * the path they use without coordinating.
     *
     * @throws IllegalArgumentException when {@code path} is null or empty - an unnamed decision
     *         point would be invisible in the summary, which is the failure this class exists to end
     */
    public static Handle register(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("fallback path name must be non-empty");
        }
        return HANDLES.computeIfAbsent(path, Handle::new);
    }

    /** One-shot {@link Handle#attempt()} for a path, for decision sites too cold to hold a handle. */
    public static void attempt(String path) {
        register(path).attempt();
    }

    /** One-shot {@link Handle#fast()} for a path. */
    public static void fast(String path) {
        register(path).fast();
    }

    /** One-shot {@link Handle#fallback()} for a path. */
    public static void fallback(String path) {
        register(path).fallback();
    }

    /**
     * {@return the per-path census} in the diagnostics line's {@code key=value} grammar, shaped
     * like {@link VulkanContext#memoryAllocationSummary()}'s per-site census:
     * {@code fallbackPaths=[terrain.engine:attempts=1494,fast=12,fallback=1482,fastRate=0.008 ...]},
     * paths sorted by attempts descending so the busiest decision point reads first. {@code fastRate}
     * is the fast share of attempts with three decimals ({@code 0.000} when nothing was attempted),
     * printed because the rate - not the raw count - is what the guardrail watches.
     */
    public static String summary() {
        List<Handle> handles = new ArrayList<Handle>(HANDLES.values());
        handles.sort((left, right) -> Long.compare(right.attempts(), left.attempts()));
        StringBuilder summary = new StringBuilder();
        summary.append("fallbackPaths=[");
        boolean first = true;
        for (Handle handle : handles) {
            if (!first) {
                summary.append(' ');
            }
            first = false;
            long attempts = handle.attempts();
            long fast = handle.fastHits();
            double fastRate = attempts == 0L ? 0.0d : (double) fast / (double) attempts;
            summary.append(handle.path())
                    .append(":attempts=").append(attempts)
                    .append(",fast=").append(fast)
                    .append(",fallback=").append(handle.fallbacks())
                    .append(",fastRate=").append(String.format(Locale.ROOT, "%.3f", fastRate));
        }
        summary.append(']');
        return summary.toString();
    }

    /** Drops every registered path and count. For tests only; the renderer never resets. */
    static void clearForTests() {
        HANDLES.clear();
    }
}
