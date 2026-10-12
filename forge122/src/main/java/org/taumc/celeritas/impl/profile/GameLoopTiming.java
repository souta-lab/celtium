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
 * Units are milliseconds in the summary and nanoseconds in the accumulators. The limitation to state when quoting it:
 * this is the <em>client</em> tick as {@code Minecraft.runTick} executes it, so the integrated server's own thread is
 * not included; a tick that spends its time waiting for the server shows up here as tick time, not as renderer time.
 */
public final class GameLoopTiming {
    private static final java.util.concurrent.atomic.AtomicLong tickNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong tickCount =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong worstTickNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /** The start of the tick currently executing, or 0 when none is. */
    private static volatile long openNanos;

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

    /** {@return one line for the fixture report: the count, the total and the mean and worst per tick}. */
    public static String summary() {
        long count = tickCount.get();
        long total = totalMillis();
        long mean = count == 0L ? 0L : total / count;
        return "ticks=" + count + " tickMillisTotal=" + total + " tickMillisMean=" + mean
                + " tickMillisWorst=" + worstMillis();
    }
}
