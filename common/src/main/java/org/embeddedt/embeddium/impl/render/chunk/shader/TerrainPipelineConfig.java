package org.embeddedt.embeddium.impl.render.chunk.shader;

import org.embeddedt.embeddium.impl.render.ShaderModBridge;

/**
 * Single core-owned request point for native terrain shader pipeline options (Phase 0).
 * <p>
 * The explicit request is disabled by default, so {@code VintageRenderSectionManager} constructs identically to the
 * baseline until a phase opts in or a shader pack is active (see {@link #isShadowPassEnabled()}).
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

    /**
     * {@return whether a section manager built now gets a shadow terrain pass}.
     * <p>
     * Two sources, either sufficient. The explicit request above is the core-owned opt-in Phase 0 defined; nothing in
     * production sets it yet, so on its own it never fires. The second source is the shader bridge:
     * {@link ShaderModBridge#areShadersEnabled()} is what upstream's modern loaders pass as {@code hasShadowPass},
     * and a shader mod's own answer to the same question ("a pack is active, even one without shadows") is defined in
     * exactly those terms, so the two agree by construction.
     * <p>
     * The Phase 0 terrain program provider cannot be the source: it is an instance field of the chunk renderer, which
     * is constructed inside the section manager's own constructor - at the moment this is asked, no renderer instance
     * exists yet, and no production code installs a provider. The bridge is asked instead because it is decidable at
     * construction time and is actually traversed at runtime (any shader mod exposing the Iris API answers it).
     * <p>
     * Timing: a pack loads on the first rendered frame, after the world, so a manager built at world load can read
     * {@code false} here and be rebuilt moments later - the shader side reloads the renderer when the pack state and
     * the pass configuration diverge, and the pipeline for the frame is prepared before that reload runs, so the
     * rebuilt manager reads {@code true}. The answer therefore changes exactly when the pass configuration does,
     * which is the property the shadow construction has to share.
     */
    public static boolean isShadowPassEnabled() {
        return shadowPassRequested || ShaderModBridge.areShadersEnabled();
    }
}
