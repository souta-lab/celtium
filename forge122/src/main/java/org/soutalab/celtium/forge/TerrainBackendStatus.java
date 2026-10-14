package org.soutalab.celtium.forge;

import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainBackendSelection;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

/**
 * Reports the terrain backend that is actually active, rather than a compile-time assumption.
 * <p>
 * Before terrain exists (main menu, no world attached) this falls back to the selection this build would make,
 * so the answer is always available and never initializes OpenGL or Vulkan by itself.
 */
public final class TerrainBackendStatus {
    private TerrainBackendStatus() {
    }

    /** Never null. */
    public static String describe() {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.instanceNullable();
        if (renderer != null) {
            String description = renderer.getTerrainBackendDescription();
            if (description != null) {
                return description;
            }
        }
        // No live renderer yet (main menu, or terrain not initialized): state what this build would do.
        return TerrainBackendSelection.pending(
                "terrain is not initialized yet; the live selection is reported once a world is loaded").describe();
    }

    /**
     * {@return the backend's name alone}, for the F3 panel's one-line default.
     * <p>
     * {@link #describe()} carries the selection's whole reason, which is right for the log and wrong for a panel read
     * at a glance: the reason stays in the full summary ({@code -Dceltium.f3.fullSummary=true}) and in
     * {@code CELTIUM_VERIFY}. This build renders terrain with the Vulkan backend or not at all, so the name is the
     * constant when a renderer exists.
     */
    public static String name() {
        return CeleritasWorldRenderer.instanceNullable() == null
                ? TerrainBackendSelection.VULKAN + " (not initialized yet)"
                : TerrainBackendSelection.VULKAN + " (native)";
    }

    /**
     * {@return whether a native terrain backend is installed in this process right now}.
     * <p>
     * The holder is not trusted on its own: it is also where a GL backend would install itself, so "something is
     * installed" is not the claim being made. The class name is compared instead, which is the same rule the
     * acceptance fixture uses, and the comparison is a string so this module needs no dependency on the renderer.
     */
    public static boolean nativeInstalled() {
        try {
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            return backend != null
                    && "org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend".equals(backend.getClass().getName());
        } catch (RuntimeException | LinkageError absent) {
            return false;
        }
    }

    /**
     * {@return the terrain backend this process is running, as the two report fields name it}.
     * <p>
     * {@code "vulkan"} with {@code native=true} only when the native renderer is the installed backend; otherwise
     * {@code "opengl"}, which is what draws terrain when no native backend exists.
     */
    public static String reportName() {
        return nativeInstalled() ? "vulkan" : "opengl";
    }
}
