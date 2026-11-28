package org.taumc.celeritas.core;

import org.soutalab.celtium.glcompat.DisplayListManager;
import org.soutalab.celtium.vulkan.VulkanModelQuadCapture;

/**
 * The single owner of what happens when Minecraft compiles or closes a display list.
 *
 * <p>This class exists because ownership, not injection points, was the defect. A display-list definition has to do two
 * things: tell the model capture that a list is starting (it files the quads that follow), and publish the open list so
 * the capture reads the right one. Those were done by two different injections on the same method - one cancelling at
 * the head, one notifying at the head - and the cancellation ran first, so the notification never happened. Measured on
 * leg gv-dlnative1: captured quads fell from 114 to 6, cancelled=1 stood against 593 replays, and every replay fell
 * back to the recorded GL replay. Moving the notification into the other injection then made the arrival count itself
 * unstable (29 on one leg, 1 on the next).
 *
 * <p>So there is one entry per vanilla call, it owns both halves, and exactly one mixin calls it.
 */
public final class CeltiumModelCapture {

    private CeltiumModelCapture() {
    }

    /**
     * Handles {@code glNewList} from Minecraft itself.
     *
     * @return whether the native renderer took it, in which case the caller must cancel the GL body
     */
    public static boolean onNewList(int list, int mode) {
        try {
            VulkanModelQuadCapture.listCompileStarted(list);
        } catch (RuntimeException | LinkageError ignored) {
            // A capture that cannot start must never stop the game from compiling: the replay path still works.
        }
        CeltiumGllessHint.nativeNewList(list, mode);
        // The converter's display-list manager records the list's commands, so a replay can draw them. A list compiled through
        // {@code GlStateManager.glNewList} - ModelRenderer, RenderGlobal's sky, a mod's skybox - never reached the
        // recording {@code glNewList}, so nothing was recorded and every {@code glCallList} of it resolved to
        // nothing. Measured on ATM3 (leg r124-atm3-amd): Astral Sorcery's skybox lists replayed 937 times with
        // {@code unresolved=937} and the upper sky stayed the fog colour. The GL body is cancelled below, so this is
        // the only place the recording can start. (E4-b2 point 3, NEON door ruling 2026-11-28: the recording
        // owner is DisplayListManager; the stub door it replaces was GlsmFrontend.compatibilityNewList.)
        DisplayListManager.glNewList(list, mode);
        return true;
    }

    /**
     * Handles {@code glEndList} from Minecraft itself.
     *
     * @return whether the native renderer took it, in which case the caller must cancel the GL body
     */
    public static boolean onEndList() {
        // Close the manager's recording before the hint clears the open list: the manager keys the compiled list by its own
        // recording id, and {@code nativeEndList} resets the hint's slot to zero. The raw-GL path
        // ({@code CeltiumGllessHint.glEndList}) has the same order for the same reason.
        // The finally keeps that order while guaranteeing the hint's close - and with it
        // the register's compiling-list restore publish - runs even if the manager's close
        // throws (G2-07, NEON ruling 2026-11-28).
        try {
            DisplayListManager.glEndList();
        } finally {
            CeltiumGllessHint.nativeEndList();
        }
        return true;
    }
}
