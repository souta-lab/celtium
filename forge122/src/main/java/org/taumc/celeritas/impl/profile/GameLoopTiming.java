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
    /** The render section ({@code updateCameraAndRender}) inside the loop - the frame brackets live inside it. */
    private static final java.util.concurrent.atomic.AtomicLong renderNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /** The scheduled-task drain inside the loop - the queue the game's own work joins, chunk uploads included. */
    private static final java.util.concurrent.atomic.AtomicLong scheduledNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong scheduledCount =
            new java.util.concurrent.atomic.AtomicLong();
    /**
     * The render section's non-frame pieces: the framebuffer bind + clear wrapper around
     * {@code updateCameraAndRender} ("renderWrapper"), the toast draw ("toasts") and Forge's render-tick hooks
     * ("fmlRenderTicks") - together they name the share the frame bracket cannot see.
     */
    private static final java.util.concurrent.atomic.AtomicLong renderWrapperNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong toastNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong fmlTickNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /** The start of the tick currently executing, or 0 when none is. */
    private static volatile long openNanos;
    /** The start of the loop currently executing, or 0 when none is. */
    private static volatile long openLoopNanos;
    /** The start of the updateDisplay currently executing inside a loop, or 0 when none is. */
    private static volatile long openDisplayNanos;
    /** The start of the blit / fps-sync / render section currently executing inside a loop, or 0 when none is. */
    private static volatile long openBlitNanos, openSyncNanos, openRenderNanos;
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
    /** Render section (updateCameraAndRender) time accumulated inside the open loop, in nanoseconds. */
    private static long renderInsideLoop;
    /** Scheduled-task time accumulated inside the open loop, in nanoseconds. */
    private static long scheduledInsideLoop;
    /** Scheduled-task count inside the open loop. */
    private static int scheduledTasksInsideLoop;
    /** The render-section's wrapper/toast/FML pieces inside the open loop, in nanoseconds. */
    private static long renderWrapperInsideLoop, toastInsideLoop, fmlTickInsideLoop;
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
        renderInsideLoop = 0L;
        scheduledInsideLoop = 0L;
        scheduledTasksInsideLoop = 0;
        renderWrapperInsideLoop = 0L;
        toastInsideLoop = 0L;
        fmlTickInsideLoop = 0L;
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
        renderNanos.addAndGet(renderInsideLoop);
        scheduledNanos.addAndGet(scheduledInsideLoop);
        scheduledCount.addAndGet(scheduledTasksInsideLoop);
        renderWrapperNanos.addAndGet(renderWrapperInsideLoop);
        toastNanos.addAndGet(toastInsideLoop);
        fmlTickNanos.addAndGet(fmlTickInsideLoop);
        displayInsideLoop = 0L;
        blitInsideLoop = 0L;
        syncInsideLoop = 0L;
        tickInsideLoop = 0L;
        renderInsideLoop = 0L;
        scheduledInsideLoop = 0L;
        scheduledTasksInsideLoop = 0;
        renderWrapperInsideLoop = 0L;
        toastInsideLoop = 0L;
        fmlTickInsideLoop = 0L;
    }

    /**
     * Called around {@code entityRenderer.updateCameraAndRender} inside {@code runGameLoop}; the call site is a
     * redirect wrapper in {@code MinecraftMixin}, so the span is inside a loop by construction. The frame brackets
     * are nested inside this call, so "loop minus render" separates render-side work from the tick/display/other-loop
     * share the frame bracket cannot name.
     */
    public static void renderStarted() {
        openRenderNanos = openLoopNanos != 0L ? System.nanoTime() : 0L;
    }

    /** Called at the return of {@code entityRenderer.updateCameraAndRender}. */
    public static void renderEnded() {
        long started = openRenderNanos;
        if (started == 0L) {
            return;
        }
        openRenderNanos = 0L;
        renderInsideLoop += System.nanoTime() - started;
    }

    /**
     * Called around one {@code Util.runTask} in the {@code scheduledExecutables} drain: chunk-mesh uploads, world
     * updates and mod tasks all queue there, so their share of the loop is named rather than folded into
     * {@code loopOther}. The redirect wrapper runs one task; the elapsed time is that task's whole cost.
     */
    public static long scheduledTaskStarted() {
        return openLoopNanos != 0L ? System.nanoTime() : 0L;
    }

    /** Called after a {@code Util.runTask} returns inside the drain. */
    public static void scheduledTaskEnded(long started) {
        if (started == 0L) {
            return;
        }
        scheduledInsideLoop += System.nanoTime() - started;
        scheduledTasksInsideLoop++;
    }

    /**
     * The render-section pieces that sit outside the frame bracket: the framebuffer bind+clear wrapper, the toast
     * draw and Forge's render-tick hooks. Each named piece accumulates its own span, so "render minus frame" stops
     * being one unmeasured residue.
     */
    public static long renderPieceStarted() {
        return openLoopNanos != 0L ? System.nanoTime() : 0L;
    }

    /** @param which 0 = framebuffer bind/clear wrapper, 1 = toast draw, 2 = FML render-tick hook */
    public static void renderPieceEnded(int which, long started) {
        if (started == 0L) {
            return;
        }
        long elapsed = System.nanoTime() - started;
        if (which == 0) renderWrapperInsideLoop += elapsed;
        else if (which == 1) toastInsideLoop += elapsed;
        else fmlTickInsideLoop += elapsed;
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

    // Snapshots for summaryDelta: the readings the previous call ended at, so the next one reports the frames in
    // between rather than the run's average - which startup loading dominates and later measurements cannot use.
    private static long deltaTickCount, deltaTickMillis, deltaLoopCount, deltaLoopMillis, deltaDisplayMillis,
            deltaBlitMillis, deltaSyncMillis, deltaRenderMillis, deltaSchedMillis, deltaWrapperMillis,
            deltaToastMillis, deltaFmlMillis;

    /**
     * {@return the loop's shares for the frames since the previous call}, which is the steady state: the run-wide
     * summary is an average over the loading phase too, and that phase's frames are long enough to hide the frame the
     * measurements are about.
     */
    public static synchronized String summaryDelta() {
        long tickCountNow = tickCount.get();
        long loopCountNow = loopCount.get();
        long tickMillisNow = totalMillis();
        long loopMillisNow = loopTotalMillis();
        long displayNow = displayUpdateTotalMillis();
        long blitNow = blitTotalMillis();
        long syncNow = syncTotalMillis();
        long renderNow = renderNanos.get() / 1_000_000L;
        long schedNow = scheduledNanos.get() / 1_000_000L;
        long wrapperNow = renderWrapperNanos.get() / 1_000_000L;
        long toastNow = toastNanos.get() / 1_000_000L;
        long fmlNow = fmlTickNanos.get() / 1_000_000L;
        long frames = loopCountNow - deltaLoopCount;
        long tickFrames = tickCountNow - deltaTickCount;
        String line = "frames=" + frames
                + " loopMean=" + mean(loopMillisNow - deltaLoopMillis, frames)
                + " tickMean=" + mean(tickMillisNow - deltaTickMillis, tickFrames)
                + " displayMean=" + mean(displayNow - deltaDisplayMillis, frames)
                + " blitMean=" + mean(blitNow - deltaBlitMillis, frames)
                + " syncMean=" + mean(syncNow - deltaSyncMillis, frames)
                + " renderMean=" + mean(renderNow - deltaRenderMillis, frames)
                + " schedMean=" + mean(schedNow - deltaSchedMillis, frames)
                + " wrapperMean=" + mean(wrapperNow - deltaWrapperMillis, frames)
                + " toastMean=" + mean(toastNow - deltaToastMillis, frames)
                + " fmlMean=" + mean(fmlNow - deltaFmlMillis, frames);
        deltaTickCount = tickCountNow;
        deltaLoopCount = loopCountNow;
        deltaTickMillis = tickMillisNow;
        deltaLoopMillis = loopMillisNow;
        deltaDisplayMillis = displayNow;
        deltaBlitMillis = blitNow;
        deltaSyncMillis = syncNow;
        deltaRenderMillis = renderNow;
        deltaSchedMillis = schedNow;
        deltaWrapperMillis = wrapperNow;
        deltaToastMillis = toastNow;
        deltaFmlMillis = fmlNow;
        return line;
    }

    private static long mean(long total, long count) {
        return count <= 0L ? 0L : total / count;
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
        long renderTotal = renderNanos.get() / 1_000_000L;
        long renderMean = loops == 0L ? 0L : renderTotal / loops;
        long schedTotal = scheduledNanos.get() / 1_000_000L;
        long schedMean = loops == 0L ? 0L : schedTotal / loops;
        long schedCount = scheduledCount.get();
        long wrapperTotal = renderWrapperNanos.get() / 1_000_000L;
        long wrapperMean = loops == 0L ? 0L : wrapperTotal / loops;
        long toastTotal = toastNanos.get() / 1_000_000L;
        long toastMean = loops == 0L ? 0L : toastTotal / loops;
        long fmlTotal = fmlTickNanos.get() / 1_000_000L;
        long fmlMean = loops == 0L ? 0L : fmlTotal / loops;
        // The loop remainder after every named section: profiler sections, resize checks, the FPS counter,
        // FML render-tick hooks and everything else runGameLoop pays outside tick, render, the task drain and
        // the display path - the bucket a "frame gets faster but the interval does not" report needs named.
        long otherTotal = Math.max(0L, loopTotal - (total + displayTotal + blitTotal + syncTotal + renderTotal + schedTotal));
        long otherMean = loops == 0L ? 0L : otherTotal / loops;
        return "ticks=" + count + " tickMillisTotal=" + total + " tickMillisMean=" + mean
                + " tickMillisWorst=" + worstMillis()
                + " loops=" + loops + " loopMillisTotal=" + loopTotal + " loopMillisMean=" + loopMean
                + " loopMillisWorst=" + worstLoopMillis()
                + " displayUpdateMillisTotal=" + displayTotal + " displayUpdateMillisMean=" + displayMean
                + " blitMillisTotal=" + blitTotal + " blitMillisMean=" + blitMean
                + " syncMillisTotal=" + syncTotal + " syncMillisMean=" + syncMean
                + " renderMillisTotal=" + renderTotal + " renderMillisMean=" + renderMean
                + " scheduledTasks=" + schedCount + " scheduledMillisTotal=" + schedTotal
                + " scheduledMillisMean=" + schedMean
                + " renderWrapperMillisTotal=" + wrapperTotal + " renderWrapperMillisMean=" + wrapperMean
                + " toastMillisTotal=" + toastTotal + " toastMillisMean=" + toastMean
                + " fmlTickMillisTotal=" + fmlTotal + " fmlTickMillisMean=" + fmlMean
                + " loopOtherMillisTotal=" + otherTotal + " loopOtherMillisMean=" + otherMean;
    }
}
