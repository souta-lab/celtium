package org.taumc.celeritas.core;

/**
 * The GL-less window hint, in its own class so the transformer can call it without loading the target's classes.
 * <p>
 * Measured 2026-10-15: the mixin that used to do this cannot be relied on. In the 268-mod E2E pack RandomPatches'
 * tweak initialisation defines {@code org.lwjgl.opengl.Display} at 8.975 s (leg {@code gv-e2e-268-xlog},
 * {@code -Xlog:class+load=info}), while Mixin prepares its configurations - both the JSON lists and the plugin's
 * {@code getMixins()} list - in one deferred pass that runs at 9.837 s. A mixin registered after its target class
 * is defined can never apply, so the stub was never installed and the window was created with a real GL 4.6
 * context.
 * <p>
 * The transformer chain has no such race: it is registered when the coremod loads, before any of this. The call
 * below is placed immediately before {@code GLFW.glfwCreateWindow} in {@code Display.create}, which is after
 * {@code glfwInit} (GLFW only permits the init-related calls before it, and {@code glfwInit} resets window hints to
 * their defaults) and before the window exists. It is idempotent and does nothing when GL-less is opted out.
 */
public final class CeltiumGllessHint {
    private static boolean applied;

    private CeltiumGllessHint() {
    }

    /**
     * Gives the calling thread its LWJGL3 capabilities from the stub. Called from injected instructions on the
     * threads the game calls GL from - see {@link CeltiumGllessModLoadTransformer}. Never throws into the game.
     */
    public static void ensureThreadCapabilities() {
        try {
            org.soutalab.celtium.vulkan.gl.CeltiumGlStub.forceThreadCapabilities();
        } catch (Throwable error) {
            org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.warn(
                    "CELTIUM_GLLESS per-thread capabilities install failed: {}", error.toString());
        }
    }

    /** Called from the injected instruction; never throws into the window creation path. */
    public static void beforeWindowCreation() {
        if (applied || "false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"))) {
            return;
        }
        applied = true;
        try {
            Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwWindowHint", int.class, int.class)
                    .invoke(null, Integer.valueOf(0x00022001), Integer.valueOf(0));
            org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.info(
                    "CELTIUM_GLLESS window hinted GLFW_NO_API before glfwCreateWindow (from the coremod transformer)");
        } catch (Throwable error) {
            // A failed hint must not stop the window: the run is then a hybrid, which the leg's own counters show.
            org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.warn(
                    "CELTIUM_GLLESS window hint failed in the transformer path: {}", error.toString());
        }
    }
}
