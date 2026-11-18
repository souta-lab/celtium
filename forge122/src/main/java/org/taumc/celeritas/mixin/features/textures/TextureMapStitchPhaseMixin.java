package org.taumc.celeritas.mixin.features.textures;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.model.ModelPhaseTrace;

import java.util.Map;

/**
 * Item 338a instrument: the stitch bracket on {@code TextureMap.loadTextureAtlas} (the real
 * stitch, not {@code loadSprites}). Kept in its own file so the instrument can be removed
 * without touching the existing TextureMap mixins. The trace records whether the stitch ran
 * inside the ModelManager bracket, which settles the listener-order question empirically.
 */
@Mixin(TextureMap.class)
public class TextureMapStitchPhaseMixin {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapUploadedSprites;

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"), require = 0)
    private void celtium$stitchBegin(IResourceManager resourceManager, CallbackInfo ci) {
        ModelPhaseTrace.stitchBegin();
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"), require = 0)
    private void celtium$stitchEnd(IResourceManager resourceManager, CallbackInfo ci) {
        if (!ModelPhaseTrace.ENABLED) {
            return;
        }
        ModelPhaseTrace.stitchEnd(this.mapUploadedSprites == null ? -1 : this.mapUploadedSprites.size());
    }
}
