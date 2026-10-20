package org.taumc.celeritas.core;

import org.soutalab.celtium.vulkan.VulkanModelQuadCapture;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;

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
        if (!CeltiumGlStub.isInstalled()) {
            return false;
        }
        try {
            VulkanModelQuadCapture.listCompileStarted(list);
        } catch (RuntimeException | LinkageError ignored) {
            // A capture that cannot start must never stop the game from compiling: the replay path still works.
        }
        CeltiumGllessHint.glNewList(list, mode);
        return true;
    }

    /**
     * Handles {@code glEndList} from Minecraft itself.
     *
     * @return whether the native renderer took it, in which case the caller must cancel the GL body
     */
    public static boolean onEndList() {
        if (!CeltiumGlStub.isInstalled()) {
            return false;
        }
        CeltiumGllessHint.glEndList();
        return true;
    }
}
