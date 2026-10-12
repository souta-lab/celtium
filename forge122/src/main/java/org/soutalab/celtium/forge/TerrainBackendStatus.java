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
}
