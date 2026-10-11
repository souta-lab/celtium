package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.OpenGlHelper;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the light-map coordinate the game sets before every entity, TESR, held-item and layer draw.
 * <p>
 * This is the missing half of the light-map term. Vanilla's fixed-function pipeline applies the world light level by
 * sampling a 16x16 light map on texture unit 1 with a per-draw coordinate:
 * {@code OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, x, y)} ends in
 * {@code GL13.glMultiTexCoord2f(unit, x, y)} ({@code OpenGlHelper.java:877}), and the callers are exactly the ones a
 * night scene needs - {@code RenderLiving.java:64} for every living entity (the pair comes from
 * {@code entity.getBrightnessForRender()}, i.e. the block and sky light at its position),
 * {@code TileEntityRendererDispatcher.java:129} for block entities, {@code ItemRenderer.java:105} for the held item,
 * {@code LayerSpiderEyes.java:42,50} and {@code LayerHeldBlock.java:42} for layers, and the deliberately full-bright
 * {@code LayerEndermanEyes.java:34} / {@code LayerEnderDragonEyes.java:34}, which pass {@code (61680, 0)}.
 * <p>
 * Under the GL-less window that call is a no-op: {@code glMultiTexCoord2f} sits in {@code CeltiumGlStub}'s no-op list
 * and nothing mirrors it, so the coordinate was discarded and the lit ITEM batch - the model path's batch since
 * task-12 - had no world-position term at all: entities kept their noon brightness at midnight (measured
 * 1.3-2.1x the upstream reference on the body families while the full-bright eye layer matched at 0.98). The value
 * is captured here per call, not once per frame, because it changes per entity: the batch that is enqueued while it
 * holds belongs to that entity.
 * <p>
 * {@code require = 0} and a swallowed failure, like every other mirror in this package: a build without the method
 * must degrade to "no coordinate captured", never to a client that will not start.
 */
@Mixin(OpenGlHelper.class)
public class LightmapCoordsMixin {

    @Inject(method = "setLightmapTextureCoords", at = @At("HEAD"), require = 0)
    private static void celtium$captureLightmapCoords(int target, float x, float y, CallbackInfo info) {
        try {
            if (target != OpenGlHelper.lightmapTexUnit) {
                // Unit 0's coordinates are not the light map; only the light-map unit's pair is the world light level.
                return;
            }
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.setLightmapCoords(x, y);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A capture must never break the frame the game is drawing.
        }
    }
}
