package org.soutalab.celtium.vulkan;

/**
 * The game's fog state, pushed in from the side that can read it.
 * <p>
 * {@code VulkanTerrainGpuBackend.fogMode()} used to ask OpenGL for it every time
 * ({@code glIsEnabled(GL_FOG)} and {@code glGetInteger(GL_FOG_MODE)}), which is one of the per-frame GL dependencies on
 * the way to a frame that needs no GL context at all (completion condition (b)). Minecraft already tracks the same
 * values in {@code GlStateManager.fogState} - the project's own {@code GLStateManagerFogService} reads them there - so
 * the forge side pushes them here instead.
 * <p>
 * This class lives in the Vulkan module, which has no Minecraft dependency on purpose: it stores two booleans and an
 * int, and {@link #hasValue()} stays false until the game has pushed something, in which case the backend keeps using
 * the GL query. That fallback is what makes the change safe to land before every path is covered.
 */
public final class VulkanFogMirror {
    private static volatile boolean hasValue;
    private static volatile boolean enabled;
    private static volatile int mode;

    private VulkanFogMirror() {
    }

    /** Called by the game-side fog reader; {@code mode} is a {@code GL_FOG_MODE} constant. */
    public static void set(boolean fogEnabled, int fogMode) {
        enabled = fogEnabled;
        mode = fogMode;
        hasValue = true;
    }

    /** {@return whether the game has pushed a value yet}, i.e. whether the mirror can be trusted. */
    public static boolean hasValue() {
        return hasValue;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int mode() {
        return mode;
    }
}
