package org.taumc.celeritas.mixin.features.textures;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.objectweb.asm.Opcodes;
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

import java.util.AbstractList;
import java.util.Iterator;
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
     * The visible-only animation gate, aimed at the read of the animated-sprite list rather than at the
     * {@code List.iterator()} call the loop makes on it. The call is not a stable target: it is unique in
     * vanilla's {@code updateAnimations}, but VanillaFix {@code @Overwrite}s that method with its own
     * visible-texture pass, whose body calls {@code List.iterator()} twice - once over the render-info list
     * to mark visible sprites, once over the sprite list to tick the marked ones. A single-expression
     * injector with two candidate expressions fails in prepare, the failure aborts the whole mixin, and the
     * game dies with {@code NoClassDefFoundError: TextureMap} (measured on the VanillaFix leg,
     * run-compat445a: InvalidInjectionException "cannot inject into func_94248_c()V merged by
     * org.dimdev.vanillafix.textures.mixins.client.MixinTextureMap"). Priority cannot order this away -
     * overwrites merge before injectors prepare. The field read is unique in both bodies (the decompiled
     * vanilla source reads it once, for the loop; the overwrite reads it once, bytecode offset 119, feeding
     * its iterator directly), and wrapping the list value composes with the overwrite instead of competing
     * with it: a sprite must pass this filter and then VanillaFix's own mark check. {@code require = 0} is
     * for shapes neither original has - a body that reads the list zero or several times loses the gate, not
     * the game. This injector is an optimization; the essential part of the textures group is the interface
     * this class carries, which does not depend on it.
     */
    @ModifyExpressionValue(method = "updateAnimations",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/renderer/texture/TextureMap;listAnimatedSprites:Ljava/util/List;"),
            require = 0)
    private List<TextureAtlasSprite> getFilteredSpriteList(List<TextureAtlasSprite> sprites) {
        if (!CeleritasVintage.options().performance.animateOnlyVisibleTextures) {
            return sprites;
        }
        return celtium$filteringView(sprites, sprite -> {
            celtium$animTotal++;
            if (((SpriteExtension) sprite).celeritas$shouldUpdate()) {
                celtium$animTicked++;
                if (celtium$animTickedNames.size() < 24) {
                    celtium$animTickedNames.add(sprite.getIconName());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * A read-through view of {@code sprites} whose iterator yields only the elements {@code predicate}
     * accepts. Every other operation delegates, so the list a caller loops over keeps its size and
     * contents; only the iteration is filtered, which is the whole of the gate above.
     *
     * <p>The helper must stay {@code private}: Mixin rejects a mixin class that declares a
     * non-private static method ({@code InvalidMixinException}: "contains non-private static
     * method"), which aborts the whole mixin and the class it targets - measured on the
     * VanillaFix leg run-compat445d, where this helper without {@code private} killed
     * {@code TextureMap} at startup exactly like the injection clash it was written to fix.
     */
    @Unique
    private static <T> List<T> celtium$filteringView(List<T> sprites, Predicate<T> predicate) {
        return new AbstractList<T>() {
            @Override
            public T get(int index) {
                return sprites.get(index);
            }

            @Override
            public int size() {
                return sprites.size();
            }

            @Override
            public Iterator<T> iterator() {
                return Iterators.filter(sprites.iterator(), predicate);
            }
        };
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
