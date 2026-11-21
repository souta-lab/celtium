package org.taumc.celeritas.mixin.features.textures;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.GlStateManager;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(TextureMap.class)
public class TextureAtlasMixin implements TextureMapExtension {
    @Shadow
    @Final
    private Map<String, TextureAtlasSprite> mapUploadedSprites;

    @Shadow
    @Final
    private List<TextureAtlasSprite> listAnimatedSprites;

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

    /**
     * The visible-only animation gate. When the option is on, this handler runs the animation pass
     * itself - bind the atlas, tick exactly the sprites the visibility predicate accepts, with the
     * CELTIUM_ANIM_TICK counting inline - and cancels the method body, whoever wrote it. With the
     * option off it returns at once and the body runs untouched.
     *
     * <p>The gate lives at the method boundary because every expression-level aim inside
     * {@code updateAnimations} has failed in measurement against VanillaFix, whose
     * {@code MixinTextureMap} {@code @Overwrite}s that method. The {@code List.iterator()} call is
     * not unique in the overwritten body - its pass calls it twice, once over the render-info list
     * and once over the sprite list - and the injection failed prepare on leg run-compat445a. The
     * read of {@code listAnimatedSprites} is unique in both bodies, but in the overwritten body the
     * field reference's owner is VanillaFix's shadow rather than {@code TextureMap}, so a FIELD
     * target naming the field on {@code TextureMap} did not resolve there either (leg
     * run-compat445f: InvalidInjectionException in INJECT_PREPARE_LEGACY, a failure
     * {@code require = 0} does not cover). The HEAD/RETURN injectors on this same method - the
     * batch pair above - applied cleanly in both shapes on both legs, so the gate uses HEAD and
     * replaces the pass outright while enabled. That also replaces VanillaFix's own
     * visible-texture pass while the option is on; the outcome that pass exists for - only visible
     * sprites tick - is this gate's own contract, kept with celtium's visibility predicate.
     */
    @Inject(method = "updateAnimations", at = @At("HEAD"), cancellable = true)
    private void celtium$animateOnlyVisible(CallbackInfo ci) {
        if (!CeleritasVintage.options().performance.animateOnlyVisibleTextures) {
            return;
        }
        ci.cancel();
        // The body's own bind is TextureUtil.bindTexture, a package-private one-liner over
        // GlStateManager.bindTexture (decompiled original) - call the public form directly.
        GlStateManager.bindTexture(((TextureMap) (Object) this).getGlTextureId());
        for (TextureAtlasSprite sprite : this.listAnimatedSprites) {
            celtium$animTotal++;
            if (((SpriteExtension) sprite).celeritas$shouldUpdate()) {
                celtium$animTicked++;
                if (celtium$animTickedNames.size() < 24) {
                    celtium$animTickedNames.add(sprite.getIconName());
                }
                sprite.updateAnimation();
            }
        }
    }

    // ------------------------------------------------------------------
    // CELTIUM_ANIM_TICK: the tick-count instrument for the visible-only
    // animation gate above (absorption item 330, 2026-11-17). The gate
    // itself is the Celeritas-lineage mechanism - per-section animated
    // sprites marked from the render lists, plus UV-query and item-render
    // marking for entity/item sprites - and this counter is its ongoing
    // proof: per window of calls it prints how many animated sprites the
    // atlas holds against how many actually ticked. With the option off
    // the two numbers are equal by construction (the RETURN half counts
    // the whole list), so an A/B leg reads directly off the log.
    // ------------------------------------------------------------------

    @Unique
    private static long celtium$animCalls;

    @Unique
    private static long celtium$animTotal;

    @Unique
    private static long celtium$animTicked;

    @Unique
    private static final Set<String> celtium$animTickedNames = new LinkedHashSet<>();

    @Unique
    private static final int CELTIUM_ANIM_TICK_WINDOW = 200;

    @Inject(method = "updateAnimations", at = @At("RETURN"))
    private void celtium$countAnimationTicks(CallbackInfo ci) {
        boolean gated = CeleritasVintage.options().performance.animateOnlyVisibleTextures;
        if (!gated) {
            int all = this.listAnimatedSprites.size();
            celtium$animTotal += all;
            celtium$animTicked += all;
        }
        if (++celtium$animCalls % CELTIUM_ANIM_TICK_WINDOW == 0) {
            System.out.println("[Celtium] CELTIUM_ANIM_TICK calls=" + CELTIUM_ANIM_TICK_WINDOW
                    + " total=" + celtium$animTotal
                    + " ticked=" + celtium$animTicked
                    + " gated=" + gated
                    + " tickedNames=" + celtium$animTickedNames);
            celtium$animTotal = 0;
            celtium$animTicked = 0;
            celtium$animTickedNames.clear();
        }
    }
}
