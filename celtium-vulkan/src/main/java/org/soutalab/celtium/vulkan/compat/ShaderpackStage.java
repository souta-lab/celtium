package org.soutalab.celtium.vulkan.compat;

/**
 * One stage of a shader pack's fixed pipeline, in the order the pack's programs run. The stages are
 * Demonica's (Iris's) own: the pack does not invent new ones, it fills these in - so the host's DAG is
 * built over this closed set and a pack that omits a stage simply leaves that stage's nodes out.
 * <p>
 * Two stages draw terrain and therefore consume a GPU cull: {@link #SHADOW} culls against the light's
 * camera, {@link #GBUFFERS} against the player's. The culls are independent dispatches over the same
 * resident section data, which is what lets them run in parallel (design {@code CELTIUM-NEW-ARCH}
 * section 2.1: "Shadow/Main parallel GPU culling"). The remaining stages are fullscreen passes over
 * the gbuffer targets the terrain stages produced.
 */
public enum ShaderpackStage {

    /** The shadow map render: terrain and entities from the light's point of view. */
    SHADOW(true, CullCamera.SHADOW),
    /** The geometry render: terrain, entities and block entities into the MRT gbuffer set. */
    GBUFFERS(true, CullCamera.MAIN),
    /** Lighting and material resolution over the gbuffers, before translucency completes the scene. */
    DEFERRED(false, CullCamera.NONE),
    /** Post effects over the resolved scene (bloom, tonemapping preparation, volumetrics). */
    COMPOSITE(false, CullCamera.NONE),
    /** The last pass: writes the frame to the swapchain image directly, no composite blit after it. */
    FINAL(false, CullCamera.NONE);

    /** Which camera a terrain-consuming stage culls against; {@link #NONE} for fullscreen stages. */
    public enum CullCamera {
        NONE, MAIN, SHADOW
    }

    private final boolean drawsTerrain;
    private final CullCamera cullCamera;

    ShaderpackStage(boolean drawsTerrain, CullCamera cullCamera) {
        this.drawsTerrain = drawsTerrain;
        this.cullCamera = cullCamera;
    }

    /** {@return whether this stage draws terrain and so consumes a cull of the resident sections}. */
    public boolean drawsTerrain() {
        return this.drawsTerrain;
    }

    /** {@return the camera this stage's cull runs against}, or {@link CullCamera#NONE}. */
    public CullCamera cullCamera() {
        return this.cullCamera;
    }
}
