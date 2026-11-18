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
 * <p>
 * In a world the cap is the player's own frame limit: pacing every capped run to the display's refresh instead
 * ignored the setting - a 120 fps limit ran at 60 on a 60 Hz display (Intel world leg, framerate 120: 16.56 ms
 * intervals against Celeritas' 8.33 ms), and a limit below the refresh rate was exceeded. Out of a world the game asks
 * for 30 fps, and there the refresh rate stays the target so the menus do not run at half the display's rate.
 * {@code -Dceltium.vulkan.paceToRefresh=false} turns it off.
 * <p>
 * The idle cap (item 388, the FPS Reducer mechanism in Celtium's own shape) lowers that target while the window is
 * unfocused or no keyboard/mouse input has arrived for a while: the effective target is
 * {@code min(player target, cap)}, so the cap can only ever slow a run, never speed one up, and input restores the
 * full rate on the next frame because the cap is recomputed from the last-input timestamp on every call. It is
 * <b>off unless the player turns it on</b> in the video settings: the headless fixture has neither focus nor input,
 * so a default-on cap would silently cap every validation and KPI leg. Under FIFO this class still does not sleep,
 * so the cap adds nothing there either. When the option is on, one {@code CELTIUM_IDLE_CAP} line is printed and the
 * validation report carries the state as its {@code idleCap} property, so a leg always names whether it ran capped.
 */
public final class CeltiumFramePacing {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.paceToRefresh", "true"));
    private static long intervalNanos = 1_000_000_000L / 60L;
    private static long nextFrameNanos;
    /** The last per-frame target and its interval, so a constant cap does not pay a division every frame. */
    private static int cachedTargetFps = Integer.MIN_VALUE;
    private static long cachedTargetIntervalNanos;
    private static boolean fifoPacing;
    private static boolean refreshQueried;
    /** Idle-cap configuration, pushed from the video settings by the call site every frame. Off by default. */
    private static volatile boolean idleCapEnabled;
    private static volatile int idleCapUnfocusedFps;
    private static volatile int idleCapIdleFps;
    private static volatile long idleCapDelayNanos;
    private static boolean idleCapAnnounced;
    private static org.lwjgl.sdl.SDL_EventFilter inputWatch;
    /** Set when the event watch could not be installed: the idle branch then never engages. */
    private static boolean inputWatchFailed;
    private static volatile long lastInputNanos;

    private CeltiumFramePacing() {
    }

    /** Tells the cap that the presentation engine paces the loop, so it must not sleep as well. */
    public static void setFifoPacing(boolean fifo) {
        fifoPacing = fifo;
    }

    /**
     * Hands the idle cap its video-settings values. Called every frame by the call site, so an option change is
     * honoured on the next frame; the one-time announcement and the input watch are installed on the first
     * enabling call only.
     */
    public static void setIdleCapConfig(boolean enabled, int unfocusedFps, int idleFps, int idleDelaySeconds) {
        idleCapEnabled = enabled;
        idleCapUnfocusedFps = unfocusedFps;
        idleCapIdleFps = idleFps;
        idleCapDelayNanos = idleDelaySeconds * 1_000_000_000L;
        if (enabled && !idleCapAnnounced) {
            idleCapAnnounced = true;
            installInputWatchOnce();
            System.out.println("[Celtium] CELTIUM_IDLE_CAP enabled unfocusedFps=" + unfocusedFps
                    + " idleFps=" + idleFps + " idleDelaySeconds=" + idleDelaySeconds
                    + " inputWatch=" + (inputWatchFailed ? "unavailable (idle branch off)" : "installed"));
        }
    }

    /** {@return the idle cap's state in one line}, for the validation report's {@code idleCap} property. */
    public static String idleCapStatus() {
        if (!idleCapEnabled) {
            return "disabled";
        }
        return "enabled unfocusedFps=" + idleCapUnfocusedFps + " idleFps=" + idleCapIdleFps
                + " idleDelaySeconds=" + (idleCapDelayNanos / 1_000_000_000L);
    }

    /**
     * Records the last keyboard/mouse input time through an SDL event watch - the same mechanism
     * {@link CeltiumSdlEventTrace} uses - so the idle branch measures input, not frame activity.
     */
    private static void installInputWatchOnce() {
        if (inputWatch != null || inputWatchFailed) {
            return;
        }
        lastInputNanos = System.nanoTime();
        try {
            inputWatch = org.lwjgl.sdl.SDL_EventFilter.create((userdata, event) -> {
                int type = org.lwjgl.system.MemoryUtil.memGetInt(event);
                if (type == org.lwjgl.sdl.SDLEvents.SDL_EVENT_KEY_DOWN
                        || type == org.lwjgl.sdl.SDLEvents.SDL_EVENT_KEY_UP
                        || type == org.lwjgl.sdl.SDLEvents.SDL_EVENT_TEXT_INPUT
                        || type == org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_MOTION
                        || type == org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN
                        || type == org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP
                        || type == org.lwjgl.sdl.SDLEvents.SDL_EVENT_MOUSE_WHEEL) {
                    lastInputNanos = System.nanoTime();
                }
                return true;
            });
            org.lwjgl.sdl.SDLEvents.SDL_AddEventWatch(inputWatch, 0L);
        } catch (RuntimeException | LinkageError failed) {
            // No SDL event watch on this build: the unfocused branch still works, the idle branch stays off
            // rather than capping a player whose input it cannot see.
            inputWatch = null;
            inputWatchFailed = true;
        }
    }

    /** {@return whether the SDL window lacks input focus or is minimized}; unknown focus never caps. */
    private static boolean windowUnfocused() {
        long window = CeltiumWindow.sdlWindowPointer();
        if (window == 0L) {
            return false;
        }
        try {
            long flags = SDLVideo.SDL_GetWindowFlags(window);
            return (flags & SDLVideo.SDL_WINDOW_INPUT_FOCUS) == 0L
                    || (flags & SDLVideo.SDL_WINDOW_MINIMIZED) != 0L;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** {@return the cap that applies right now}, or 0 when no cap applies. */
    private static int activeCapFps() {
        if (windowUnfocused()) {
            return idleCapUnfocusedFps > 0 ? idleCapUnfocusedFps : 0;
        }
        if (inputWatch != null && idleCapDelayNanos > 0L
                && System.nanoTime() - lastInputNanos >= idleCapDelayNanos) {
            return idleCapIdleFps > 0 ? idleCapIdleFps : 0;
        }
        return 0;
    }

    /** Sleeps to the display's refresh interval when nothing else paces the loop. */
    public static void pace() {
        pace(0);
    }

    /**
     * Sleeps to {@code targetFps} when it is positive, to the display's refresh interval otherwise, when nothing else
     * paces the loop.
     */
    public static void pace(int targetFps) {
        if (!ENABLED || fifoPacing) {
            return;
        }
        if (idleCapEnabled) {
            int cap = activeCapFps();
            if (cap > 0) {
                // min(player target, cap); a target of 0 asks for the refresh interval, which the cap
                // replaces outright - it is a ceiling, and the refresh rate is above any sane cap.
                targetFps = targetFps > 0 ? Math.min(targetFps, cap) : cap;
            }
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
        long interval;
        if (targetFps > 0) {
            if (targetFps != cachedTargetFps) {
                cachedTargetFps = targetFps;
                cachedTargetIntervalNanos = 1_000_000_000L / targetFps;
            }
            interval = cachedTargetIntervalNanos;
        } else {
            interval = intervalNanos;
        }
        long now = System.nanoTime();
        if (nextFrameNanos == 0L || now - nextFrameNanos > interval * 4L) {
            nextFrameNanos = now + interval;
            return;
        }
        long wait = nextFrameNanos - now;
        if (wait > 0L) {
            java.util.concurrent.locks.LockSupport.parkNanos(wait);
        }
        // Catch up rather than carry the overshoot: a coarse timer (Windows sleeps in ticks) can return late, and
        // adding the interval to a target that is already in the past leaves every later frame late too, which is what
        // makes a capped run burst to recover the lag instead of holding the rate.
        now = System.nanoTime();
        if (now > nextFrameNanos) {
            nextFrameNanos = now + interval;
        } else {
            nextFrameNanos += interval;
        }
    }
}
