package org.embeddedt.embeddium.impl.render.chunk.shader;

/**
 * Single core-owned request point for native terrain shader pipeline options (Phase 0).
 * <p>
 * Disabled by default so {@code VintageRenderSectionManager} constructs identically to the baseline until explicitly
 * enabled in a later phase.
 */
public final class TerrainPipelineConfig {
    private static volatile boolean shadowPassRequested;

    private TerrainPipelineConfig() {
    }

    /** {@return whether shadow terrain pass construction is requested} (default {@code false}). */
    public static boolean isShadowPassRequested() {
        return shadowPassRequested;
    }

    /** Sets whether shadow terrain pass construction is requested. */
    public static void setShadowPassRequested(boolean requested) {
        shadowPassRequested = requested;
    }
}
