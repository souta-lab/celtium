package org.taumc.celeritas.mixin.core;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.CeltiumAtlasLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends each sprite's resource read, PNG decode and mip pyramid to a bounded pool while the stitch itself
 * stays sequential and ordered.
 * <p>
 * Three points inside {@code TextureMap} make this safe rather than just fast. The
 * {@code generateMipmaps} call in {@code loadTexture} becomes a pool submission, so the sprite loop keeps
 * walking and the decode tasks overlap. The matching {@code stitcher.addSprite} call only records the sprite
 * in submission order - nothing is stitched yet. Then, at the head of {@code finishLoading} - the point
 * where the original first reads stitcher state - the recorded sprites are awaited in order and stitched,
 * exactly where the original would have put them. The stitcher sees one sprite at a time in the original
 * order; the parallelism is confined to the per-sprite decode that shared nothing anyway.
 * <p>
 * Name notes: {@code loadTexture(Stitcher,...)} and {@code finishLoading} are Forge-added methods - they have
 * no SRG name, so their member references carry {@code remap = false}. Vanilla privates carry their SRG names.
 */
@Mixin(TextureMap.class)
public class TextureMapParallelMixin {
    @Shadow
    private int field_147636_j; // mipmapLevels

    @Shadow
    private String field_94254_c; // basePath

    @Shadow
    private boolean func_184397_a(IResourceManager resourceManager, TextureAtlasSprite texture) {
        throw new AssertionError();
    }

    /** The sprites submitted for stitching this load, in load order; drained at finishLoading. */
    private final List<TextureAtlasSprite> celeritas$pendingStitch = new ArrayList<>();

    @Redirect(method = "loadTexture(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)I",
            remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureMap;func_184397_a(Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)Z"))
    private boolean celeritas$parallelMipmaps(TextureMap self, IResourceManager resourceManager,
            TextureAtlasSprite sprite) {
        if (!CeltiumAtlasLoader.ENABLED) {
            return this.func_184397_a(resourceManager, sprite);
        }
        return CeltiumAtlasLoader.submit(self, this.field_94254_c, this.field_147636_j, resourceManager, sprite);
    }

    /** A custom loader may read other sprites' frames, which must have finished loading as they would in vanilla. */
    @Inject(method = "loadTexture(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)I",
            remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;load(Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/util/ResourceLocation;Ljava/util/function/Function;)Z"))
    private void celeritas$finishFramesBeforeCustomLoad(CallbackInfoReturnable<Integer> cir) {
        if (CeltiumAtlasLoader.ENABLED) {
            CeltiumAtlasLoader.awaitSubmitted();
        }
    }

    @Redirect(method = "loadTexture(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)I",
            remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/Stitcher;func_110934_a(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void celeritas$deferStitch(Stitcher stitcher, TextureAtlasSprite sprite) {
        if (!CeltiumAtlasLoader.ENABLED) {
            stitcher.addSprite(sprite);
        } else {
            this.celeritas$pendingStitch.add(sprite);
        }
    }

    @Inject(method = "finishLoading(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)V",
            remap = false,
            at = @At("HEAD"))
    private void celeritas$drainBeforeStitch(CallbackInfo ci, @Local Stitcher stitcher) {
        for (TextureAtlasSprite sprite : this.celeritas$pendingStitch) {
            try {
                if (CeltiumAtlasLoader.await(sprite)) {
                    stitcher.addSprite(sprite);
                }
            } catch (RuntimeException | Error passThrough) {
                throw passThrough;
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
        this.celeritas$pendingStitch.clear();
    }
}
