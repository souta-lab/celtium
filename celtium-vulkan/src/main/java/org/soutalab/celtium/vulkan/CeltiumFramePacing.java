package org.soutalab.celtium.vulkan;

import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.sdl.SDLVideo;

/**
 * The GL-less frame cap, applied at the point the game's own limiter used to sleep.
 * <p>
 * With VSync off the present mode is MAILBOX and the delivered rate is whatever a frame costs, so the interval jitters
 * with it - read as stutter at any average rate. VSync on (FIFO) already blocks in the presentation engine, so this cap
 * stands down there: sleeping on top of FIFO would only add a refresh interval of latency.
 * <p>
 * The call site matters. It used to run at the top of the present, i.e. before the frame was handed to the compositor,
 * which pushed every frame back by the sleep. The game's fps-limit hook runs right after {@code Display.update} - which
 * is after the swap - so the cap sleeps after the frame is out rather than in front of it.
 * {@code -Dceltium.vulkan.paceToRefresh=false} turns it off.
 */
public final class CeltiumFramePacing {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.paceToRefresh", "true"));
    private static long intervalNanos = 1_000_000_000L / 60L;
    private static long nextFrameNanos;
    private static boolean fifoPacing;
    private static boolean refreshQueried;

    private CeltiumFramePacing() {
    }

    /** Tells the cap that the presentation engine paces the loop, so it must not sleep as well. */
    public static void setFifoPacing(boolean fifo) {
        fifoPacing = fifo;
    }

    /** Sleeps to the display's refresh interval when nothing else paces the loop. */
    public static void pace() {
        if (!ENABLED || fifoPacing) {
            return;
        }
        if (!refreshQueried) {
            refreshQueried = true;
            try {
                SDL_DisplayMode mode = SDLVideo.SDL_GetCurrentDisplayMode(SDLVideo.SDL_GetPrimaryDisplay());
                if (mode != null && mode.refresh_rate() > 1.0F) {
                    intervalNanos = (long) (1_000_000_000D / mode.refresh_rate());
                }
            } catch (RuntimeException | LinkageError ignored) {
                // No SDL display query here: the 60 Hz default applies.
            }
        }
        long now = System.nanoTime();
        if (nextFrameNanos == 0L || now - nextFrameNanos > intervalNanos * 4L) {
            nextFrameNanos = now + intervalNanos;
            return;
        }
        long wait = nextFrameNanos - now;
        if (wait > 0L) {
            java.util.concurrent.locks.LockSupport.parkNanos(wait);
        }
        nextFrameNanos += intervalNanos;
    }
}
