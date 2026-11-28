package org.soutalab.celtium.core.shader;

import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.BlockEntitySink;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.frame.VulkanCommandStream;

/**
 * Interface 1, the terrain lane's side (design V040-FINAL-PLAN section 2.3): the five operations
 * the pack executor drives terrain with. This replaces the twelve {@code WorldRendererCompat}
 * callbacks, which assumed a CPU chunk-graph search; here culling is a per-frame GPU operation
 * against the resident slab, so the surface is prepare-two-views, record-a-pass, gather the shadow
 * block entities, report.
 * <p>
 * Call order within a frame is the frame executor's fixed template: {@link #prepareMainView},
 * then {@link #prepareShadowView} when the pack has a shadow pass, then one
 * {@link #recordTerrainPass} per {@link TerrainPassKind} the stage draws, with
 * {@link #gatherShadowBlockEntities} feeding the non-terrain lane's shadow rendering.
 */
public interface PackTerrainDriver {

    /** Makes the main view the culling input for this frame. The pose supplies the frustum origin. */
    void prepareMainView(CameraPose cam, FrustumPlanes frustum, long frameId);

    /** Makes the shadow view the culling input for the shadow stage. The pose supplies the frustum origin. */
    void prepareShadowView(CameraPose shadowCam, FrustumPlanes shadowFrustum, float shadowDistance, long frameId);

    /** Records one terrain pass: binds the program and draws the culled sections for {@code kind}. */
    void recordTerrainPass(VulkanCommandStream cmd, TerrainPassKind kind,
                           TerrainStageProgram program, ActiveRenderTargets targets);

    /**
     * Hands every block entity inside the shadow frustum to the sink, for the non-terrain lane to
     * draw into the shadow pass (gap G-7: this collection is explicit because the predecessor
     * design dropped these entities silently). Uses the origin installed by {@link #prepareShadowView}.
     */
    void gatherShadowBlockEntities(FrustumPlanes shadowFrustum, BlockEntitySink sink);

    /** A one-line human summary of the driver's state, for the F3 panel and telemetry. */
    String diagnosticsSummary();
}
