package org.taumc.celeritas.core;

import net.minecraft.client.Minecraft;

/**
 * Remembers that a world arrived before a player did, so the frame can be skipped as a whole instead of guarding its
 * consumers one at a time.
 * <p>
 * The window: the client can have {@code mc.world} set while {@code mc.player} and the render view entity are both
 * still null, because the world arrives on the network thread part-way through a frame. Legs on 2026-10-15 walked that
 * window one dereference at a time - fog, the viewpoint lookup, the camera state, {@code renderWorld}'s own repair
 * falling through, the debug HUD, and finally Forge's HUD - and the frame-state probe on leg
 * {@code gv-cohort-fix138} showed why flag-based fixes kept missing it: the world becomes non-null <em>during</em> the
 * frame that crashes, so the head of {@code runGameLoop}, which is where vanilla's own gate reads
 * {@code skipRenderWorld}, still sees {@code world == null} and cannot set it in time.
 * <p>
 * So the fact is recorded where it happens, at world assignment, and acted on at the gate. Vanilla clears the flag in
 * {@code displayGuiScreen}, which is why the write is repeated every frame from this state rather than set once. The
 * cost of skipping is the one the platform documents: the HUD is skipped with the world, and the screen keeps its last
 * frame until a camera exists again, which is what a loading screen shows anyway.
 */
public final class CeltiumWorldEntryWatch {

    /** Set when a world is assigned while the player is still absent. */
    private static volatile boolean worldArrivedWithoutPlayer;

    private CeltiumWorldEntryWatch() {
    }

    /** Records that a world was just assigned, if the player is not there yet. */
    public static void noteWorldAssigned(Minecraft mc) {
        if (mc != null && mc.world != null && mc.player == null) {
            worldArrivedWithoutPlayer = true;
        }
    }

    /**
     * {@return whether this frame is one the world has no camera for}.
     * <p>
     * Clears the record as soon as a player exists, so ordinary rendering resumes on the first frame that has one.
     */
    public static boolean shouldSkipWorld(Minecraft mc) {
        if (mc == null || mc.world == null) {
            return false;
        }
        if (mc.player != null) {
            worldArrivedWithoutPlayer = false;
            return false;
        }
        return worldArrivedWithoutPlayer;
    }
}
