package org.soutalab.celtium.vulkan;

/**
 * The no-camera guards' activity (drawpath audit DP-29): frames where the world render or the HUD was
 * skipped because there is no camera - no player and no render view entity, with a world loaded.
 * <p>
 * The skips themselves are designed guards (they keep the first world-entry frame from crashing the
 * renderers that read the player). The defect was invisibility: nothing counted them, so a session stuck
 * in the no-camera state - world loaded, camera never arriving - looked exactly like a session that never
 * entered it. Three readings:
 * <ul>
 * <li>{@link #noteFlagTrue()} - {@code runGameLoop} heads where the world-skip flag was written true.</li>
 * <li>{@link #noteWorldSkip()} / {@link #noteWorldRendered()} - the world-render redirect's two branches.
 * The rendered branch resets the consecutive run, so {@link #maxConsecutiveWorldSkips()} is the number
 * that separates a one-frame entry transient from a stuck session.</li>
 * <li>{@link #noteHudSkip()} - HUD renders cancelled by the no-player guard.</li>
 * </ul>
 * Always on; read through {@link #summary()} in the verification summary. Render thread only, like the
 * guards themselves.
 */
public final class NoCameraSkipCensus {
    private static long flagTrueFrames;
    private static long worldSkips;
    private static long hudSkips;
    private static long consecutiveWorldSkips;
    private static long maxConsecutiveWorldSkips;

    private NoCameraSkipCensus() {
    }

    /** One {@code runGameLoop} head that wrote the world-skip flag true. */
    public static void noteFlagTrue() {
        flagTrueFrames++;
    }

    /** One world render skipped by the no-camera redirect. */
    public static void noteWorldSkip() {
        worldSkips++;
        consecutiveWorldSkips++;
        if (consecutiveWorldSkips > maxConsecutiveWorldSkips) {
            maxConsecutiveWorldSkips = consecutiveWorldSkips;
        }
    }

    /** One world render that ran: the no-camera run, if any, is over. */
    public static void noteWorldRendered() {
        consecutiveWorldSkips = 0L;
    }

    /** One HUD render cancelled by the no-player guard. */
    public static void noteHudSkip() {
        hudSkips++;
    }

    public static long flagTrueFrames() {
        return flagTrueFrames;
    }

    public static long worldSkips() {
        return worldSkips;
    }

    public static long hudSkips() {
        return hudSkips;
    }

    public static long maxConsecutiveWorldSkips() {
        return maxConsecutiveWorldSkips;
    }

    /** {@return the one-line reading for the verification summary}. */
    public static String summary() {
        return "flagTrue=" + flagTrueFrames + " worldSkips=" + worldSkips
                + " maxConsecutiveWorldSkips=" + maxConsecutiveWorldSkips + " hudSkips=" + hudSkips;
    }
}
