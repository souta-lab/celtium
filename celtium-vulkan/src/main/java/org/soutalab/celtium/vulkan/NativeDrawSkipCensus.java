package org.soutalab.celtium.vulkan;

/**
 * Native {@code VertexBuffer} draws the Vulkan backend refused, counted at the entry that decided to skip
 * them (drawpath audit DP-02/DP-03): {@code CeltiumNativeDraw.vertexBuffer}.
 * <p>
 * Two causes, kept apart because they mean different things:
 * <ul>
 * <li>{@link #noteStalledSkip()} - the stall latch is up (issue #425): the device is gone or stopped and the
 * renderer is rebuilt by the reload path. This population is <em>not</em> {@code stalledGeometryDrops}:
 * that counter sits in {@code SubmissionStatistics} and counts batches that were accepted into the
 * immediate groups and dropped at flush time. A native draw refused at submission never enters a group,
 * so before this census it was counted nowhere.</li>
 * <li>{@link #noteRefusedSkip()} - a live, unstalled backend refused the retained geometry. Before DP-02
 * this case threw out of the draw entry into whichever renderer called it, with no catch around it - the
 * same propagation shape that killed the client in the stall case's history. It now degrades to the
 * skipped draw the stall case already promised, and this counter is its record.</li>
 * </ul>
 * Always on; read through {@link #summary()} in the verification summary beside
 * {@code stalledGeometryDrops}.
 */
public final class NativeDrawSkipCensus {
    private static long stalledSkips;
    private static long refusedSkips;

    private NativeDrawSkipCensus() {
    }

    /** One native draw skipped because the stall latch is up. */
    public static void noteStalledSkip() {
        stalledSkips++;
    }

    /** One native draw skipped because a live backend refused the retained geometry. */
    public static void noteRefusedSkip() {
        refusedSkips++;
    }

    public static long stalledSkips() {
        return stalledSkips;
    }

    public static long refusedSkips() {
        return refusedSkips;
    }

    /** {@return the one-line reading for the verification summary}. */
    public static String summary() {
        return "stalled=" + stalledSkips + " refused=" + refusedSkips;
    }
}
