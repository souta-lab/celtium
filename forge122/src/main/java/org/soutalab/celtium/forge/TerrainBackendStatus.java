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
        return TerrainBackendSelection.opengl(
                "terrain is not initialized yet; the live selection is reported once a world is loaded").describe();
    }
}
