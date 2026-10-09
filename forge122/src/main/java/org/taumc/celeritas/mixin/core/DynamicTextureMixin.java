package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.texture.DynamicTexture;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
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
 * the size are asked of GL by the backend, because this method writes into whatever is bound and its own size fields are not
 * part of the public surface.
 */
@Mixin(DynamicTexture.class)
public class DynamicTextureMixin {

    /** The {@code int[]} this class holds its pixels in, found once by type. */
    private static java.lang.reflect.Field celtium$pixels;
    /** The two {@code int} size fields, found by name: they are private but stable, and reading them costs no GL query. */
    private static java.lang.reflect.Field celtium$width;
    private static java.lang.reflect.Field celtium$height;

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
            if (celtium$pixels != null) {
                // The size rides along when the fields are readable, so the backend does not have to ask OpenGL for it
                // (two glGetTexLevelParameteri per update, about a thousand a run on AMD). The fields are private in
                // 1.12.2, but they are plain ints with stable names, which is the same footing as the pixel array above.
                // The size is not read here: the fields are private and, in a production (SRG-named) jar, a lookup by the
                // mapped name does not match - measured, the size-carrying path was never taken (announcedDynamicTextures
                // stayed 0). The plain announce stays as the early hint; the size now comes from the TextureUtil upload
                // hook, whose parameters carry it.
                backend.announceDynamicTexture((int[]) celtium$pixels.get(this));
            }
        } catch (Throwable ignored) {
            // Same rule as the other texture hooks: a bookkeeping hint must never break an upload. If anything goes wrong the
            // mirror keeps whatever it had, which is the behaviour that shipped before this hook existed.
        }
    }
}
