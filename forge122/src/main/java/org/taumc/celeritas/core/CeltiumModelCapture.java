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
        try {
            VulkanModelQuadCapture.listCompileStarted(list);
        } catch (RuntimeException | LinkageError ignored) {
            // A capture that cannot start must never stop the game from compiling: the replay path still works.
        }
        CeltiumGllessHint.nativeNewList(list, mode);
        // The stub records the list's commands, so a replay can draw them. A list compiled through
        // {@code GlStateManager.glNewList} - ModelRenderer, RenderGlobal's sky, a mod's skybox - never reached the
        // stub's own {@code glNewList}, so nothing was recorded and every {@code glCallList} of it resolved to
        // nothing. Measured on ATM3 (leg r124-atm3-amd): Astral Sorcery's skybox lists replayed 937 times with
        // {@code unresolved=937} and the upper sky stayed the fog colour. The GL body is cancelled below, so this is
        // the only place the recording can start.
        CeltiumGlStub.compatibilityNewList(list, mode);
        return true;
    }

    /**
     * Handles {@code glEndList} from Minecraft itself.
     *
     * @return whether the native renderer took it, in which case the caller must cancel the GL body
     */
    public static boolean onEndList() {
        // Close the stub's recording before the hint clears the open list: the stub keys the compiled list by its own
        // {@code compilingList}, and {@code nativeEndList} resets it to zero. The raw-GL path
        // ({@code CeltiumGllessHint.glEndList}) has the same order for the same reason.
        CeltiumGlStub.compatibilityEndList();
        CeltiumGllessHint.nativeEndList();
        return true;
    }
}
