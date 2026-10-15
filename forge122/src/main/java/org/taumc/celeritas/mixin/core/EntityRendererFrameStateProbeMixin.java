package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports the frame's own state on the frames where the world has no camera, so the next fix is measured rather than
 * guessed.
 * <p>
 * Leg {@code gv-cohort-fix137} wrote {@code mc.skipRenderWorld} from the head of {@code updateCameraAndRender} whenever
 * there was neither a player nor a render view entity, and still died at the same 39.9 s with the same null entity in
 * {@code renderWorld} as {@code gv-cohort-fix136}. Two explanations survive: the condition was false on the frame that
 * crashed, or the guard never ran. One line of state separates them.
 * <p>
 * This is the same shape as the tracker report that settled the Nether question: print the inputs the decision is made
 * from, at the two places the decision is made. The head of {@code updateCameraAndRender} is where the flag is written
 * and the head of {@code renderWorld} is where it is acted on, so a single leg shows whether the window is entered,
 * what the fields hold when it is, and whether the flag was already set by the time the world render begins.
 * <p>
 * Bounded: the first few frames are always reported so the sequence has a start, then only frames in the window, and
 * the count is capped so a long run cannot flood the log.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererFrameStateProbeMixin {

    /** Frames always reported at the start, so the log shows the sequence before the window. */
    @Unique
    private static final int CELTIUM_PROBE_ALWAYS_FRAMES = 5;

    /** Cap on reported frames per site. */
    @Unique
    private static final int CELTIUM_PROBE_MAX_LINES = 60;

    @Unique
    private static int celtium$probeFrames;

    @Unique
    private static int celtium$probeLines;

    @Inject(method = "func_78471_a", at = @At("HEAD"), require = 0)
    private void celtium$probeCameraAndRender(float partialTicks, long nanoTime, CallbackInfo info) {
        celtium$probeFrames++;
        celtium$report("updateCameraAndRender");
    }

    @Inject(method = "func_175068_a", at = @At("HEAD"), require = 0)
    private void celtium$probeRenderWorld(int pass, float partialTicks, long finishTimeNano, CallbackInfo info) {
        celtium$report("renderWorld");
    }

    /** The last state reported, so a line is written when something changes rather than every frame. */
    @Unique
    private static String celtium$lastState;

    @Unique
    private static void celtium$report(String site) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        boolean early = celtium$probeFrames <= CELTIUM_PROBE_ALWAYS_FRAMES;
        String screen = mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
        String state = "player=" + (mc.player != null)
                + " renderViewEntity=" + (mc.getRenderViewEntity() != null)
                + " world=" + (mc.world != null)
                + " skipRenderWorld=" + mc.skipRenderWorld
                + " screen=" + screen;
        // Reported on change, not per frame: the first version filled its cap with sixty identical lines and the
        // transition it was written to catch - when the world appears, and when a player arrives - was exactly what it
        // could no longer show.
        if (!early && state.equals(celtium$lastState)) {
            return;
        }
        if (celtium$probeLines >= CELTIUM_PROBE_MAX_LINES) {
            return;
        }
        celtium$lastState = state;
        celtium$probeLines++;
        System.out.println("[Celtium] CELTIUM_FRAME_STATE site=" + site + " frame=" + celtium$probeFrames + " " + state);
    }
}
