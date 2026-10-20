package org.taumc.celeritas.core;

/**
 * Counts the {@code Tessellator.draw()} calls the renderer still sends through the GL path.
 *
 * <p>The 2026-10-20 direction moves the main path to a CPU frontend feeding native Vulkan and reduces the GL stub to a
 * HUD/GUI fallback. Sizing that move needs one number the repository does not have: how much geometry still enters
 * through {@code Tessellator}. The batch timings measured the cost of a batch already queued (~3.5 us, ~290 a frame,
 * ~1 ms of a 3.3 ms frame, no hotspot); they do not say how many of those batches are Tessellator traffic versus models
 * and font, and a conversion has to be aimed at the larger of those.
 *
 * <p>Off unless {@code -Dceltium.tessellatorCensus=true}. The hook is a single increment on a call that already
 * allocates nothing, so a run without the switch adds no work worth measuring.
 */
public final class CeltiumTessellatorCensus {

    private static final boolean ENABLED = Boolean.getBoolean("celtium.tessellatorCensus");

    private static long draws;
    private static long framesSeen;
    private static long lastReportDraws;
    private static long maxDrawsInAFrame;
    private static long drawsThisFrame;
    private static final java.util.concurrent.atomic.AtomicBoolean HOOKED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private CeltiumTessellatorCensus() {
    }

    /** One {@code Tessellator.draw()}. */
    public static void noteDraw() {
        if (!ENABLED) {
            return;
        }
        draws++;
        drawsThisFrame++;
        if (HOOKED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // FileDescriptor.out for the same reason the glless hint's exit line uses it: Forge routes System.out
                // into Log4j, whose own shutdown hook stops the appenders, so a line printed that way competes with
                // teardown and is sometimes lost.
                try (java.io.PrintStream out =
                        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), false)) {
                    out.println("CELTIUM_TESS_CENSUS draws=" + draws + " frames=" + framesSeen
                            + " drawsPerFrameMax=" + maxDrawsInAFrame);
                } catch (Throwable ignored) {
                    // A census that cannot be printed must not take the process down on its way out.
                }
            }, "celtium-tess-census"));
        }
    }

    /** Called at the end of a frame's draw, so the per-frame figure means frames and not calls. */
    public static void noteFrameBoundary() {
        if (!ENABLED) {
            return;
        }
        framesSeen++;
        if (drawsThisFrame > maxDrawsInAFrame) {
            maxDrawsInAFrame = drawsThisFrame;
        }
        drawsThisFrame = 0;
    }

    /** {@return how many draws have been counted so far} */
    public static long draws() {
        return draws;
    }
}
