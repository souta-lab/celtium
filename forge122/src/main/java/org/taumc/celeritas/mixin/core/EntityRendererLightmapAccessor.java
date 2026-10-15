package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the light map texture object, whose own id is the authoritative answer the mirror needs. */
@Mixin(EntityRenderer.class)
public interface EntityRendererLightmapAccessor {
    @Accessor("lightmapTexture")
    DynamicTexture celtium$getLightmapTexture();
}
