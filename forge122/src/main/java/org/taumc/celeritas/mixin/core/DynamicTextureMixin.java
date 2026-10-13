package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.texture.DynamicTexture;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Announces dynamic-texture writes with the pixels they are about to upload.
 * <p>
 * The light map is the texture this exists for. It is a {@code DynamicTexture}, it is updated every frame the sun moves or a
 * torch flickers, and its write goes through neither of the two {@code TextureUtil} methods that are hooked - which is why it
 * was the one texture in this renderer that still reached the readback every time it changed, about a thousand times a run on
 * AMD and four hundred and seventy on Intel.
 * <p>
 * The mixin does the least it can: it reads the pixel array the method is about to upload and hands it over. The texture id and
 * the dimensions come from DynamicTexture's own private final width/height fields via mapped Mixin shadows.
 * Querying GL 4.5 for these values under GL-less Vulkan requires a GL function pointer the native context does not own.
 */
@Mixin(DynamicTexture.class)
public class DynamicTextureMixin {

    /** The {@code int[]} this class holds its pixels in, found once by type. */
    private static java.lang.reflect.Field celtium$pixels;
    /** Vanilla DynamicTexture stores the exact allocation dimensions; Mixin remaps these shadows at load time. */
    @Shadow @Final private int width;
    @Shadow @Final private int height;

    @Inject(method = "updateDynamicTexture", at = @At("HEAD"), require = 0)
    private void celtium$announceDynamicTexture(CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend == null) {
                return;
            }
            // The pixel array is read reflectively: the field's name is stable across the mapping but the accessor's is not, and
            // a mixin that fails to compile cannot announce anything at all. If the field is not there the hook does nothing,
            // which is the contract every texture hook in this project keeps.
            if (celtium$pixels == null) {
                for (java.lang.reflect.Field field : DynamicTexture.class.getDeclaredFields()) {
                    if (field.getType() == int[].class) {
                        field.setAccessible(true);
                        celtium$pixels = field;
                        break;
                    }
                }
            }
            if (celtium$pixels != null && backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                // Use the object's id AND stored dimensions. The old two-argument overload made a direct
                // GL45C.glGetTextureLevelParameteri call, which can terminate the JVM before a fixture exists
                // when the GL-less stub advertises an honest 3.0 compatibility version (PR #62/#63, BH-35).
                vulkan.announceDynamicTexture(
                        ((DynamicTexture) (Object) this).getGlTextureId(),
                        (int[]) celtium$pixels.get(this), this.width, this.height);
            }
        } catch (Throwable ignored) {
            // Same rule as the other texture hooks: a bookkeeping hint must never break an upload. If anything goes wrong the
            // mirror keeps whatever it had, which is the behaviour that shipped before this hook existed.
        }
    }
}
