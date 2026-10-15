package org.taumc.celeritas.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.soutalab.celtium.vulkan.LightmapIdSource;
import org.taumc.celeritas.mixin.core.EntityRendererLightmapAccessor;

/**
 * Installs the light map id source for the terrain backend.
 * <p>
 * The backend cannot ask GL for the light map's texture name: 1.12.2 binds it through
 * {@code GlStateManager.bindTexture}, which skips the GL call when its cache already holds the name, so the stub
 * never sees it and the mirror refused every lightmapped batch with {@code id=-1} (leg {@code gv-cohort-vp1}).
 * The {@code DynamicTexture} that owns the name can always answer, so this installs that as the source. It is a
 * supplier rather than a value because the light map is reallocated on resource reloads.
 */
public final class CeltiumLightmapIdInstaller {
    private CeltiumLightmapIdInstaller() {
    }

    public static void install() {
        LightmapIdSource.install(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
                return -1;
            }

            EntityRenderer renderer = mc.entityRenderer;
            if (renderer == null) {
                return -1;
            }

            var texture = ((EntityRendererLightmapAccessor) renderer).celtium$getLightmapTexture();
            return texture == null ? -1 : texture.getGlTextureId();
        });
    }
}
