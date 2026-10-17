package org.taumc.celeritas.impl.profile;

/**
 * The client loop's own tick timing, which no other instrument in this project covers.
 * <p>
 * The (g) comparison left ~17.4 ms per frame unattributed: the renderer bracket, the present path, the deferred and
 * bootstrap submission waits, the frame-fence and slot waits and the swapchain all account for about 21 ms of a
 * 38.7 ms interval, and the rest is the client loop itself - this scene runs 1.11 world ticks per frame (241 ticks
 * over 217 frames), and the tick happens inside the interval and outside every renderer bracket. That gap was named
 * but not measured, which is what this class exists to fix: {@code Minecraft.runTick} is timed head to return, and
 * the fixture reports the total, the count and the worst beside its own frame count, so "renderer vs tick" can be
 * split in one leg.
 * <p>
 * The loop brackets extend the same idea one level up. {@code runTick} does not render:
 * {@code Minecraft.runGameLoop} runs the tick batch, then the render section (which the frame brackets cover) and
 * {@code updateDisplay} (the present path, where the swapchain's acquire/vsync wait lives), then the fps-limit sync -
 * and none of that loop span was measured. Measured 2026-10-17 (legs {@code loading-resize-state2-*}): the
 * warm-window interval ran about 3x the frame bracket (AMD 54.5 ms interval vs 18.0 ms frame) and the difference was
 * unmeasured. {@code updateDisplay} is counted only while a loop is open, so the loading phase's own
 * {@code updateDisplay} calls ({@code drawSplashScreen}, {@code Minecraft.resize}) cannot join the per-frame mean.
 * <p>
 * Units are milliseconds in the summary and nanoseconds in the accumulators. The limitation to state when quoting it:
 * this is the <em>client</em> side as {@code runTick}/{@code runGameLoop} execute it, so the integrated server's own
 * thread is not included; a tick that spends its time waiting for the server shows up here as tick time, not as
 * renderer time.
 */
public final class GameLoopTiming {
    private static final java.util.concurrent.atomic.AtomicLong tickNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong tickCount =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong worstTickNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong loopNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong loopCount =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong worstLoopNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong displayUpdateNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /** The framebuffer blit ({@code framebufferRender}) and the fps-limit sync, both inside the loop. */
    private static final java.util.concurrent.atomic.AtomicLong blitNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong syncNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /** The start of the tick currently executing, or 0 when none is. */
    private static volatile long openNanos;
    /** The start of the loop currently executing, or 0 when none is. */
    private static volatile long openLoopNanos;
    /** The start of the updateDisplay currently executing inside a loop, or 0 when none is. */
    private static volatile long openDisplayNanos;
    /** The start of the blit / fps-sync currently executing inside a loop, or 0 when none is. */
    private static volatile long openBlitNanos, openSyncNanos;
    /** updateDisplay re-entry depth: resize() calls it again from checkWindowResize inside the outer call. */
    private static int displayDepth;
    /** updateDisplay time accumulated inside the open loop, in nanoseconds. */
    private static long displayInsideLoop;
    /** framebufferRender time accumulated inside the open loop, in nanoseconds. */
    private static long blitInsideLoop;
    /** Display.sync time accumulated inside the open loop, in nanoseconds. */
    private static long syncInsideLoop;
    /** Tick time accumulated inside the open loop, in nanoseconds. */
    private static long tickInsideLoop;
    /** Snapshots of the most recently closed loop, for the fixture's per-slow-frame entry. */
    private static volatile long lastLoopNanos, lastLoopTickNanos, lastLoopDisplayNanos;
    private static volatile long lastLoopBlitNanos, lastLoopSyncNanos;

    private GameLoopTiming() {
    }

    /** Called at the head of the client tick. */
    public static void tickStarted() {
        openNanos = System.nanoTime();
    }

    /**
     * Called at the return of the client tick. A tick that never reaches its return (an exception on the way out)
     * leaves the accumulator untouched rather than recording a span that never ended.
     */
    public static void tickEnded() {
        long started = openNanos;
        if (started == 0L) {
            return;
        }
        openNanos = 0L;
        long elapsed = System.nanoTime() - started;
        tickNanos.addAndGet(elapsed);
        tickCount.incrementAndGet();
        worstTickNanos.accumulateAndGet(elapsed, Math::max);
        if (openLoopNanos != 0L) {
            tickInsideLoop += elapsed;
        }
    }

    /**
     * Called at the head of the game loop. Resets the loop-local accumulators, so an updateDisplay or tick inside this
     * loop is attributed to this loop's span.
     */
    public static void loopStarted() {
        openLoopNanos = System.nanoTime();
        displayInsideLoop = 0L;
        blitInsideLoop = 0L;
        syncInsideLoop = 0L;
        tickInsideLoop = 0L;
    }

    /**
     * Called at the return of the game loop. The same unpaired-start rule as {@link #tickEnded()} applies: a loop that
     * never returns records nothing.
     */
    public static void loopEnded() {
        long started = openLoopNanos;
        if (started == 0L) {
            return;
        }
        openLoopNanos = 0L;
        long elapsed = System.nanoTime() - started;
        loopNanos.addAndGet(elapsed);
        loopCount.incrementAndGet();
        worstLoopNanos.accumulateAndGet(elapsed, Math::max);
        lastLoopNanos = elapsed;
        lastLoopTickNanos = tickInsideLoop;
        lastLoopDisplayNanos = displayInsideLoop;
        lastLoopBlitNanos = blitInsideLoop;
        lastLoopSyncNanos = syncInsideLoop;
        displayUpdateNanos.addAndGet(displayInsideLoop);
        blitNanos.addAndGet(blitInsideLoop);
        syncNanos.addAndGet(syncInsideLoop);
        displayInsideLoop = 0L;
        blitInsideLoop = 0L;
        syncInsideLoop = 0L;
        tickInsideLoop = 0L;
    }

    /**
     * Called at the head of {@code Minecraft.updateDisplay}; only counted while a loop is open, and only the outermost
     * call is timed - {@code checkWindowResize} inside it can re-enter through {@code Minecraft.resize}, so a re-entry
     * is depth-counted rather than allowed to cut the outer span short.
     */
    public static void displayUpdateStarted() {
        if (openLoopNanos != 0L && displayDepth++ == 0) {
            openDisplayNanos = System.nanoTime();
        }
    }

    /** Called at the return of {@code Minecraft.updateDisplay}. */
    public static void displayUpdateEnded() {
        if (displayDepth > 0) {
            displayDepth--;
        }
        long started = openDisplayNanos;
        if (started == 0L || displayDepth != 0) {
            return;
        }
        openDisplayNanos = 0L;
        displayInsideLoop += System.nanoTime() - started;
    }

    /**
     * Called around the framebuffer blit. The call sites are {@code @Redirect} wrappers inside {@code runGameLoop},
     * so the span is inside a loop by construction; the loop-open check keeps a hand-placed call from being counted.
     */
    public static void blitStarted() {
        openBlitNanos = openLoopNanos != 0L ? System.nanoTime() : 0L;
    }

    /** Called at the return of the framebuffer blit. */
    public static void blitEnded() {
        long started = openBlitNanos;
        if (started == 0L) {
            return;
        }
        openBlitNanos = 0L;
        blitInsideLoop += System.nanoTime() - started;
    }

    /** Called at the head of the fps-limit {@code Display.sync}. */
    public static void syncStarted() {
        openSyncNanos = openLoopNanos != 0L ? System.nanoTime() : 0L;
    }

    /** Called at the return of the fps-limit {@code Display.sync}. */
    public static void syncEnded() {
        long started = openSyncNanos;
        if (started == 0L) {
            return;
        }
        openSyncNanos = 0L;
        syncInsideLoop += System.nanoTime() - started;
    }

    /** {@return how many client ticks were timed}. */
    public static long ticks() {
        return tickCount.get();
    }

    /** {@return the total time spent inside the client tick, in milliseconds}. */
    public static long totalMillis() {
        return tickNanos.get() / 1_000_000L;
    }

    /** {@return the worst single client tick, in milliseconds}. */
    public static long worstMillis() {
        return worstTickNanos.get() / 1_000_000L;
    }

    /** {@return how many game loops were timed}. */
    public static long loops() {
        return loopCount.get();
    }

    /** {@return the total time spent inside the game loop, in milliseconds}. */
    public static long loopTotalMillis() {
        return loopNanos.get() / 1_000_000L;
    }

    /** {@return the worst single game loop, in milliseconds}. */
    public static long worstLoopMillis() {
        return worstLoopNanos.get() / 1_000_000L;
    }

    /** {@return the total time spent inside updateDisplay while a loop was open, in milliseconds}. */
    public static long displayUpdateTotalMillis() {
        return displayUpdateNanos.get() / 1_000_000L;
    }

    /** {@return the total time spent inside the framebuffer blit while a loop was open, in milliseconds}. */
    public static long blitTotalMillis() {
        return blitNanos.get() / 1_000_000L;
    }

    /** {@return the total time spent inside the fps-limit sync while a loop was open, in milliseconds}. */
    public static long syncTotalMillis() {
        return syncNanos.get() / 1_000_000L;
    }

    /** {@return the span of the most recently closed loop, in milliseconds}. */
    public static long lastLoopMillis() {
        return lastLoopNanos / 1_000_000L;
    }

    /** {@return the tick time inside the most recently closed loop, in milliseconds}. */
    public static long lastLoopTickMillis() {
        return lastLoopTickNanos / 1_000_000L;
    }

    /** {@return the updateDisplay time inside the most recently closed loop, in milliseconds}. */
    public static long lastLoopDisplayMillis() {
        return lastLoopDisplayNanos / 1_000_000L;
    }

    /** {@return the blit time inside the most recently closed loop, in milliseconds}. */
    public static long lastLoopBlitMillis() {
        return lastLoopBlitNanos / 1_000_000L;
    }

    /** {@return the fps-sync time inside the most recently closed loop, in milliseconds}. */
    public static long lastLoopSyncMillis() {
        return lastLoopSyncNanos / 1_000_000L;
    }

    /** {@return one line for the fixture report: counts, totals, means and worsts for tick, loop and present}. */
    public static String summary() {
        long count = tickCount.get();
        long total = totalMillis();
        long mean = count == 0L ? 0L : total / count;
        long loops = loopCount.get();
        long loopTotal = loopTotalMillis();
        long loopMean = loops == 0L ? 0L : loopTotal / loops;
        long displayTotal = displayUpdateTotalMillis();
        long displayMean = loops == 0L ? 0L : displayTotal / loops;
        long blitTotal = blitTotalMillis();
        long blitMean = loops == 0L ? 0L : blitTotal / loops;
        long syncTotal = syncTotalMillis();
        long syncMean = loops == 0L ? 0L : syncTotal / loops;
        return "ticks=" + count + " tickMillisTotal=" + total + " tickMillisMean=" + mean
                + " tickMillisWorst=" + worstMillis()
                + " loops=" + loops + " loopMillisTotal=" + loopTotal + " loopMillisMean=" + loopMean
                + " loopMillisWorst=" + worstLoopMillis()
                + " displayUpdateMillisTotal=" + displayTotal + " displayUpdateMillisMean=" + displayMean
                + " blitMillisTotal=" + blitTotal + " blitMillisMean=" + blitMean
                + " syncMillisTotal=" + syncTotal + " syncMillisMean=" + syncMean;
    }
}
