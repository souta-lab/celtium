package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.ffp.ShaderManager;
import org.soutalab.celtium.glcompat.stacks.ColorMaskStack;
import org.soutalab.celtium.glcompat.stacks.DepthStateStack;
import org.lwjgl.opengl.GL11;

import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;
import static org.soutalab.celtium.glcompat.gtnh.lwjgl2.LWJGLServiceProvider.LWJGL;

/** Selects a depth interval for fixed-function draws and defers application shaders. */
public final class DepthPeel {
    public static final int TEXTURE_UNIT = 15;
    public static final int SCENE_TEXTURE_UNIT = 14;

    private static final int PASS_COLOR = 1;
    private static final int UNBOUNDED = 4;

    public static final String GLSL = """
        uniform sampler2D u_PeelDepth;
        uniform sampler2D u_PeelScene;
        uniform int u_PeelMode;

        void peelTest() {
            const float sceneTolerance = 6.0e-8;
            const float peelWindow = 2.4e-7;
            ivec2 texel = ivec2(gl_FragCoord.xy);
            float z = gl_FragCoord.z;
            if (z > texelFetch(u_PeelScene, texel, 0).r + sceneTolerance) discard;
            if ((u_PeelMode & %d) != 0) return;
            float bound = texelFetch(u_PeelDepth, texel, 0).r;
            if (u_PeelMode == %d) {
                if (z > bound || z < bound - peelWindow) discard;
            } else if (z >= bound - peelWindow) {
                discard;
            }
        }
        """.formatted(UNBOUNDED, PASS_COLOR);

    public enum Pass { OFF, DEPTH, COLOR, REST, APPLICATION }

    private static final ThreadLocal<Scope> SCOPES = new ThreadLocal<>();

    private DepthPeel() {}

    private static final class Scope {
        private final GLContextState context = GLStateManager.ctx();
        private Pass pass;
        private boolean unbounded;
        private boolean skippedApplicationDraws;
    }

    /** Checks for the desktop FFP pipeline and the depth and query features required by the peel passes. */
    public static boolean supported() {
        return !EXECUTOR.isSDLGPU() && EXECUTOR.hasContextOnThread() && !RenderSystem.isGLES()
            && LWJGL.isOpenGLVersionSupported(3, 3) && ShaderManager.isEnabled()
            && GLStateManager.MAX_TEXTURE_UNITS > TEXTURE_UNIT;
    }

    /** Switches passes on the render thread. The caller owns the framebuffer, viewport and sampler bindings. */
    public static void setPass(Pass pass, boolean unbounded) {
        if (pass == Pass.OFF) {
            SCOPES.remove();
            return;
        }
        Scope scope = SCOPES.get();
        if (scope == null) {
            if (!supported() || DepthEcho.replaying()) {
                throw new IllegalStateException("Depth peeling requires desktop OpenGL outside depth echo replay");
            }
            scope = new Scope();
            SCOPES.set(scope);
        } else if (scope.context != GLStateManager.ctx()) {
            throw new IllegalStateException("Depth peel passes must use the same OpenGL context");
        }
        scope.pass = pass;
        scope.unbounded = unbounded;
    }

    private static Scope current() {
        final Scope scope = SCOPES.get();
        return scope != null && scope.context == GLStateManager.ctx() ? scope : null;
    }

    public static boolean active() {
        return current() != null;
    }

    public static boolean shaderActive() {
        final Scope scope = current();
        return scope != null && scope.pass != Pass.APPLICATION;
    }

    public static boolean skippedApplicationDraws() {
        final Scope scope = current();
        return scope != null && scope.skippedApplicationDraws;
    }

    public static int shaderMode() {
        final Scope scope = current();
        if (scope == null) return 0;
        return switch (scope.pass) {
            case DEPTH -> scope.unbounded ? UNBOUNDED : 0;
            case COLOR -> PASS_COLOR;
            default -> 0;
        };
    }

    /** Submits a native desktop draw with temporary driver state while preserving GLSM's cached state. */
    public static void submit(Runnable draw) {
        final Scope scope = current();
        if (scope == null) {
            DepthEcho.submit(draw);
            return;
        }
        final boolean fixedFunction = GLSMPrograms.getActiveProgram() == 0;
        if (scope.pass == Pass.APPLICATION) {
            if (!fixedFunction) DepthEcho.submit(draw);
            return;
        }
        if (!fixedFunction) {
            scope.skippedApplicationDraws = true;
            return;
        }
        switch (scope.pass) {
            case DEPTH -> drawDepth(draw);
            case COLOR -> DepthEcho.submit(() -> drawColor(draw), draw);
            case REST -> DepthEcho.submit(draw);
            default -> throw new IllegalStateException("Unexpected depth peel pass: " + scope.pass);
        }
    }

    private static void drawDepth(Runnable draw) {
        final ColorMaskStack mask = GLSMBlend.getColorMask();
        final DepthStateStack depth = GLSMDepthStencilAlpha.getDepthState();
        final boolean depthTest = GLSMDepthStencilAlpha.getDepthTest().isEnabled();
        try {
            if (!depthTest) EXECUTOR.enable(GL11.GL_DEPTH_TEST);
            EXECUTOR.colorMask(false, false, false, false);
            EXECUTOR.depthMask(true);
            EXECUTOR.depthFunc(GL11.GL_GREATER);
            draw.run();
        } finally {
            EXECUTOR.depthFunc(depth.getFunc());
            EXECUTOR.depthMask(depth.isMaskEnabled());
            EXECUTOR.colorMask(mask.red, mask.green, mask.blue, mask.alpha);
            if (!depthTest) EXECUTOR.disable(GL11.GL_DEPTH_TEST);
        }
    }

    private static void drawColor(Runnable draw) {
        final int depthFunc = GLSMDepthStencilAlpha.getDepthState().getFunc();
        try {
            EXECUTOR.depthFunc(GL11.GL_ALWAYS);
            draw.run();
        } finally {
            EXECUTOR.depthFunc(depthFunc);
        }
    }
}
