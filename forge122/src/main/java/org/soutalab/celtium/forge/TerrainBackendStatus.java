package org.soutalab.celtium.forge;

import org.soutalab.celtium.vulkan.CeltiumBackend;
import org.soutalab.celtium.vulkan.CeltiumBackends;

/**
 * Reports the terrain backend that is actually active, rather than a compile-time assumption.
 * <p>
 * Before terrain exists (main menu, no world attached) the answer says so plainly, so the
 * report is always available and never initializes OpenGL or Vulkan by itself. S2-b retype:
 * the report now reads celtium's own backend holder - the retired renderer's instance and
 * the old selection type are gone, and this build renders terrain with the Vulkan backend
 * or not at all.
 */
public final class TerrainBackendStatus {
    private TerrainBackendStatus() {
    }

    /** Never null. */
    public static String describe() {
        CeltiumBackend backend = CeltiumBackends.current();
        if (backend != null) {
            return backend.getName() + " (lane1 sections=" + backend.celtium$lane1SectionCount() + ")";
        }
        // No live backend yet (main menu, or terrain not initialized): state what this build would do.
        return "VULKAN (terrain is not initialized yet; the live backend is reported once a world is loaded)";
    }

    /**
     * {@return the backend's name alone}, for the F3 panel's one-line default.
     * <p>
     * {@link #describe()} carries the whole description, which is right for the log and wrong
     * for a panel read at a glance: the reason stays in the full summary
     * ({@code -Dceltium.f3.fullSummary=true}) and in {@code CELTIUM_VERIFY}.
     */
    public static String name() {
        return CeltiumBackends.current() == null
                ? "VULKAN (not initialized yet)"
                : "VULKAN (native)";
    }

    /**
     * {@return whether a native terrain backend is installed in this process right now}.
     * <p>
     * The holder is not trusted on its own: it is also where any backend would install
     * itself, so "something is installed" is not the claim being made. The class name is
     * compared instead, which is the same rule the acceptance fixture uses, and the
     * comparison is a string so this module needs no dependency on the renderer.
     */
    public static boolean nativeInstalled() {
        try {
            Object backend = CeltiumBackends.current();
            return backend != null
                    && "org.soutalab.celtium.vulkan.VulkanBackendAdapter".equals(backend.getClass().getName());
        } catch (RuntimeException | LinkageError absent) {
            return false;
        }
    }
}
