package org.taumc.celeritas.mixin.features.textures;

import com.google.common.collect.Iterators;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;
import org.embeddedt.embeddium.impl.util.collections.quadtree.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.impl.extensions.SpriteExtension;
import org.taumc.celeritas.impl.extensions.TextureMapExtension;

import java.util.Iterator;
import java.util.Map;

@Mixin(TextureMap.class)
public class TextureAtlasMixin implements TextureMapExtension {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapUploadedSprites;

    private QuadTree<TextureAtlasSprite> celeritas$quadTree;

    private int celeritas$width, celeritas$height;

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void generateQuadTree(CallbackInfo ci, @Local(ordinal = 0) Stitcher stitcher) {
        this.celeritas$width = stitcher.getCurrentWidth();
        this.celeritas$height = stitcher.getCurrentHeight();
        Rect2i treeRect = new Rect2i(0, 0, celeritas$width, celeritas$height);
        int minSize = this.mapUploadedSprites.values().stream().mapToInt(sprite -> Math.max(sprite.getIconWidth(), sprite.getIconHeight())).min().getAsInt();
        this.celeritas$quadTree = new QuadTree<>(treeRect, minSize, this.mapUploadedSprites.values(), sprite -> new Rect2i(sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight()));
    }

    @Override
    public QuadTree<TextureAtlasSprite> celeritas$getQuadTree() {
        return celeritas$quadTree;
    }

    @Override
    public TextureAtlasSprite celeritas$findFromUV(float u, float v) {
        int x = Math.round(u * this.celeritas$width), y = Math.round(v * this.celeritas$height);

        return this.celeritas$quadTree.find(x, y);
    }

    /**
     * One batch for the tick's animated sprites: each sprite's frame reaches the backend as its own patch upload, and
     * submitting them one by one put ~0.3 ms on every tick frame (r69prof). The end runs at RETURN; a throw leaves the
     * batch to the staging ring's own submit-before-wrap and the next tick's begin, which does not reopen it.
     */
    @Inject(method = "updateAnimations", at = @At("HEAD"))
    private void celtium$beginAnimationUploads(CallbackInfo ci) {
        // The whole animated-sprite pass, opened before the batch so the batch's own submission is inside it. This is
        // the tick's texture work - `renderEngine.tick()` -> `TextureMap.tick()` -> here - and the paired KPI's client
        // tick is 131 us slower than the reference's with only 33 us of it named.
        org.soutalab.celtium.vulkan.FramePassTimings.begin("anim");
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend != null) {
                backend.beginAnimationUploads(((TextureMap) (Object) this).getGlTextureId());
            }
        } catch (Throwable failure) {
            // A batching hint must never break the animation tick - but it must not fail silently either.
            celtium$reportAnimationFailure("begin", failure);
        }
    }

    @Inject(method = "updateAnimations", at = @At("RETURN"))
    private void celtium$endAnimationUploads(CallbackInfo ci) {
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend != null) {
                backend.endAnimationUploads();
            }
        } catch (Throwable failure) {
            // Same rule as the begin. Swallowed silently, an "Out of stack space" in the end's flush hid for days
            // while the half-recorded batch left the atlas in TRANSFER_DST (fix d8cb18595).
            celtium$reportAnimationFailure("end", failure);
        } finally {
            // Closed after the batch's submission, so `anim` is the pass and `animBatchEnd` is the submit inside it.
            org.soutalab.celtium.vulkan.FramePassTimings.end("anim");
        }
    }

    @Unique
    private static int celtium$animationFailures;

    @Unique
    private static void celtium$reportAnimationFailure(String half, Throwable failure) {
        if (celtium$animationFailures++ < 4) {
            System.out.println("[Celtium] CELTIUM_ANIMATION_BATCH_FAILURE half=" + half + " error=" + failure);
            failure.printStackTrace(System.out);
        }
    }

    @ModifyExpressionValue(method = "updateAnimations", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<TextureAtlasSprite> getFilteredIterator(Iterator<TextureAtlasSprite> iterator) {
        if (CeleritasVintage.options().performance.animateOnlyVisibleTextures) {
            return Iterators.filter(iterator, sprite -> ((SpriteExtension)sprite).celeritas$shouldUpdate());
        } else {
            return iterator;
        }
    }
}
