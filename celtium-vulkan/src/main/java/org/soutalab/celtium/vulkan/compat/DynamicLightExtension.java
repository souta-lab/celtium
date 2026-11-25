package org.soutalab.celtium.vulkan.compat;

import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;

import java.util.function.Supplier;

/**
 * Celtium's native dynamic lights as a frame extension: once per frame, at frame start, the active
 * {@link DynamicLightService} gathers the shading-time sources around the camera and the extension
 * hands them to the frame block through the context sink. This is the item 430 path - {@code
 * TerrainRenderer} used to run the same gather inside its first uniform write of the frame, the first
 * call that knew the camera - lifted to where the new architecture says frame data enters: before the
 * graph is compiled, through the {@link RenderExtensionRegistry}, so every consumer (terrain today,
 * the pack gbuffers under {@link ShaderpackHost} tomorrow) reads the same block.
 * <p>
 * The service is supplied, not owned: the renderer binding decides which implementation is active
 * (today {@code MinecraftDynamicLightService}, whose own {@code -Dceltium.dynamicLights} switch stays
 * the kill switch - this extension does not re-decide it), and a supplier returning {@link
 * DynamicLightService#EMPTY} or null simply contributes no sources. The gather's semantics are the
 * service's contract unchanged: camera-relative positions, nearest sources win past the cap, cheap
 * enough for every frame, and tolerant of no world being loaded.
 * <p>
 * This surface is celtium's own. The Celeritas Dynamic Lights <em>addon</em> is not this and does not
 * pass through here: it injects into vanilla's light queries, which ledger II keeps unreplaced and
 * the light funnel keeps serving (item 378). The two coexist because they answer different questions -
 * the addon raises baked light through the world queries; this gather feeds sources that exist only
 * at shading time to the shaders directly.
 */
public final class DynamicLightExtension implements FrameExtension {

    /** The registry id of this extension. */
    public static final String ID = "dynamic-lights";

    private final Supplier<DynamicLightService> serviceSource;
    private final float[] scratch = new float[DynamicLightService.MAX_LIGHTS * 4];

    public DynamicLightExtension(Supplier<DynamicLightService> serviceSource) {
        this.serviceSource = serviceSource;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void gather(FrameExtensionContext context) {
        DynamicLightService service = this.serviceSource == null ? null : this.serviceSource.get();
        int count = 0;
        if (service != null) {
            count = service.gatherLights(this.scratch, context.cameraX(), context.cameraY(),
                    context.cameraZ());
        }
        context.writeLights(this.scratch, count);
    }
}
