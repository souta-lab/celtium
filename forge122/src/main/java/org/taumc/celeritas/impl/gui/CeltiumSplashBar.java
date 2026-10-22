package org.taumc.celeritas.impl.gui;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The loading bar under the splash logo, driven by the initialisation stages this renderer can actually observe.
 * <p>
 * Forge's {@code SplashProgress} draws this bar, and the GL-less renderer stubs that class out because its splash needs
 * a shared GL context a {@code GLFW_NO_API} window cannot provide - so the logo appeared with nothing under it. The
 * bar here is not a decoration: it reports the stages the loader really reaches (texture atlas stitched, font atlas
 * built, game started) and creeps towards the next one at a fixed rate so it moves during the long stretches inside a
 * stage. It is drawn through the same immediate path as every other quad, so it costs nothing extra and needs no GL
 * context.
 * <p>
 * {@code -Dceltium.splashBar=false} turns it off.
 */
public final class CeltiumSplashBar {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("celtium.splashBar", "true"));
    /** Fractional progress per second while a stage is in flight, so a stage that takes seconds still moves. */
    private static final double CREEP_PER_SECOND = 0.08;
    private static final AtomicLong STAGE = new AtomicLong();
    private static volatile long stageChangedNanos = System.nanoTime();

    private CeltiumSplashBar() {
    }

    /** Marks one observable stage complete. Stages are 1 (texture atlas), 2 (font atlas), 3 (game started). */
    public static void stage(int stage) {
        long now = System.nanoTime();
        if (STAGE.getAndAccumulate(stage, Math::max) < stage) {
            stageChangedNanos = now;
        }
    }

    /** The launch's start, so the unobserved phases still move the bar instead of leaving it empty. */
    private static final long STARTED_NANOS = System.nanoTime();

    /**
     * {@return the fraction to draw, 0..1}.
     * <p>
     * This is a liveness bar, not a measured percentage: the stages Forge's splash reported are inside classes the
     * GL-less renderer stubs out, so what is known is that the launch is in progress. It fills on a bounded time curve
     * to 0.95 and snaps to 1.0 when the game actually starts playing, which is what a player needs - a bar that moves
     * while the atlas and models load, and is full exactly when it stops being shown.
     */
    public static double progress() {
        if (STAGE.get() >= 3L) {
            return 1.0D;
        }
        double elapsed = (System.nanoTime() - STARTED_NANOS) / 1e9;
        return Math.min(0.95D, elapsed / 30.0D);
    }

    public static boolean enabled() {
        return ENABLED;
    }
}
