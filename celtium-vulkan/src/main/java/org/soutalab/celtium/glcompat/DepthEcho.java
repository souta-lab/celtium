package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.hooks.BatchStateGuard;
import org.soutalab.celtium.glcompat.stacks.ColorMaskStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Map;
import java.util.WeakHashMap;

import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

/** Captures depth for visible blended geometry submitted with depth writes disabled. */
public final class DepthEcho {
    private static final Map<GLContextState, Capture> CAPTURES = new WeakHashMap<>();
    private static volatile boolean hasCaptures;

    private DepthEcho() {}

    private static final class Capture {
        int target;
        int source;
        int immediate;
        boolean echoed;
        boolean tracking;
        boolean hit;
        boolean replaying;
    }

    private static Capture current() {
        if (!hasCaptures) return null;
        final GLContextState context = GLStateManager.ctx();
        synchronized (CAPTURES) {
            return CAPTURES.get(context);
        }
    }

    private static void releaseIfIdle(Capture capture) {
        if (capture.target != 0 || capture.echoed || capture.immediate != 0) return;
        final GLContextState context = GLStateManager.ctx();
        synchronized (CAPTURES) {
            CAPTURES.remove(context, capture);
            hasCaptures = !CAPTURES.isEmpty();
        }
    }

    /** Arms capture from the source draw framebuffer into the depth-only target. */
    public static void arm(int framebuffer, int source) {
        if (EXECUTOR.isSDLGPU()) return;
        if (framebuffer == 0 || framebuffer == source) {
            throw new IllegalArgumentException("Depth echo requires a distinct non-default target framebuffer");
        }
        final GLContextState context = GLStateManager.ctx();
        synchronized (CAPTURES) {
            final Capture capture = CAPTURES.computeIfAbsent(context, ignored -> new Capture());
            capture.target = framebuffer;
            capture.source = source;
            hasCaptures = true;
        }
    }

    /** Ends capture while retaining the result for the layer merge. */
    public static void disarm() {
        final Capture capture = current();
        if (capture == null) return;
        capture.target = 0;
        capture.source = 0;
        capture.tracking = false;
        capture.hit = false;
        capture.replaying = false;
        releaseIfIdle(capture);
    }

    public static boolean armed() {
        final Capture capture = current();
        return capture != null && capture.target != 0;
    }

    public static void beginTracking() {
        final Capture capture = current();
        if (capture == null || capture.target == 0) return;
        capture.tracking = true;
        capture.hit = false;
    }

    /** Returns whether the tracked renderer must be replayed by its caller. */
    public static boolean endTracking() {
        final Capture capture = current();
        if (capture == null) return false;
        capture.tracking = false;
        final boolean hit = capture.hit;
        capture.hit = false;
        return hit;
    }

    /** Marks deferred batches whose geometry can be submitted a second time immediately. */
    public static void beginImmediate() {
        final Capture capture = current();
        if (capture != null && (capture.target != 0 || capture.immediate != 0)) capture.immediate++;
    }

    public static void endImmediate() {
        final Capture capture = current();
        if (capture == null || capture.immediate == 0) return;
        capture.immediate--;
        releaseIfIdle(capture);
    }

    public static void beginReplay() {
        final Capture capture = current();
        if (capture != null && capture.target != 0) capture.replaying = true;
    }

    public static void endReplay() {
        final Capture capture = current();
        if (capture != null) capture.replaying = false;
    }

    public static boolean replaying() {
        final Capture capture = current();
        return capture != null && capture.replaying;
    }

    static boolean protectsDepth(int framebuffer) {
        final Capture capture = current();
        return capture != null && capture.replaying && capture.target == framebuffer;
    }

    /** Consumes the current context's capture result, including after disarming. */
    public static boolean takeEchoed() {
        final Capture capture = current();
        if (capture == null) return false;
        final boolean echoed = capture.echoed;
        capture.echoed = false;
        releaseIfIdle(capture);
        return echoed;
    }

    static boolean skipsCall() {
        final Capture capture = current();
        return capture != null && capture.replaying && !DisplayListManager.isRecording() && replaySkips(capture);
    }

    /** Selects coverage discard before the fixed-function shader for the draw is bound. */
    public static boolean discardsCoverage() {
        final Capture capture = current();
        return capture != null && (capture.replaying || echoes(capture));
    }

    /** Submits a native OpenGL draw and manages temporary driver state without changing GLSM caches. */
    public static void submit(Runnable draw) {
        submit(draw, draw);
    }

    static void submit(Runnable draw, Runnable echoDraw) {
        final Capture capture = current();
        if (capture == null || capture.target == 0) {
            draw.run();
        } else {
            submitCaptured(capture, draw, echoDraw);
        }
    }

    private static void submitCaptured(Capture capture, Runnable draw, Runnable echoDraw) {
        if (capture.replaying) {
            if (replaySkips(capture)) return;
            if (GLSMDepthStencilAlpha.getDepthState().isMaskEnabled()) {
                draw.run();
            } else {
                drawWithDepthWrite(draw);
            }
            return;
        }
        draw.run();
        if (!echoes(capture)) return;
        if (capture.tracking && capture.immediate == 0 && !BatchStateGuard.isSuspended()) {
            capture.hit = true;
            capture.echoed = true;
            return;
        }
        final int source = capture.source;
        try {
            EXECUTOR.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, capture.target);
            drawWithDepthWrite(() -> GLStateManager.ctx().ffp.withDepthEchoCoverage(echoDraw));
            capture.echoed = true;
        } finally {
            EXECUTOR.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, source);
        }
    }

    private static void drawWithDepthWrite(Runnable draw) {
        try {
            EXECUTOR.depthMask(true);
            draw.run();
        } finally {
            EXECUTOR.depthMask(false);
        }
    }

    private static boolean replaySkips(Capture capture) {
        return GLSMFramebuffer.getDrawFramebuffer() != capture.target || !blendsVisibly();
    }

    private static boolean echoes(Capture capture) {
        return capture.target != 0 && GLSMFramebuffer.getDrawFramebuffer() == capture.source
            && !GLSMDepthStencilAlpha.getDepthState().isMaskEnabled() && blendsVisibly();
    }

    private static boolean blendsVisibly() {
        if (!GLSMDepthStencilAlpha.getDepthTest().isEnabled() || !GLSMBlend.getBlendMode().isEnabled()
            || GLSMDepthStencilAlpha.getDepthState().getFunc() == GL11.GL_EQUAL) return false;
        final ColorMaskStack mask = GLSMBlend.getColorMask();
        return mask.red || mask.green || mask.blue || mask.alpha;
    }
}
