package org.taumc.celeritas.mixin.core;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Angelica's in-world item optimization (GTNewHorizons/Angelica PR #1034, LGPL), ported to this renderer's contract:
 * a {@code RenderItem.renderModel} call emits the same baked-quad vertex data every time it runs for the same model,
 * because the transform that distinguishes two draws lives in the matrix, not the vertices. The vanilla loop re-walks
 * {@code getQuads} per face and runs {@code renderQuads} per quad on every draw - for a held or dropped item that is
 * the identical sequence dozens of times per frame, and for an inventory grid hundreds. Forge's default
 * {@code allowEmissiveItems=true} makes it worse: every draw additionally allocates {@code allquads}, pipes every
 * quad through the light gatherer and splits it into segments, even though none of it depends on the frame.
 * <p>
 * The cache keeps a <b>segment plan</b> per {@code (model instance, color)}: the segment boundaries, each segment's
 * emitted {@code int[]}, and the light/shade flags {@code drawSegment} would apply - so a hit replays the identical
 * GL state calls and the identical bytes through the identical uploader call, reading {@code lastBrightnessX/Y} live
 * the way the original does. The model instance is the right key on its own: a resource reload produces new
 * {@code IBakedModel} objects, so an entry can never outlive the bake it captured.
 * <p>
 * Not cached, the inputs that genuinely vary per call: a stack-tinted render ({@code color == -1 && !stack.isEmpty()})
 * on a model with tinted quads (the bytes are then per-stack), and a model whose quads carry lighting data outside
 * the ITEM format - Forge's light-gatherer path handles those and keeps handling them here.
 */
@Mixin(RenderItem.class)
public abstract class RenderItemMixin {
    /**
     * {@code -Dceltium.itemMeshCache=false} disables the interception entirely - the control leg for picture
     * comparisons.
     */
    private static final boolean ITEM_MESH_CACHE =
            !"false".equals(System.getProperty("celtium.itemMeshCache", "true"));
    private static final int ITEM_MESH_CACHE_CAP = 256;

    /** model+color-keyed plans; render-thread confined, like every other per-draw structure here. */
    private final Map<ItemMeshKey, Segment[]> celtium$itemMeshes =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ItemMeshKey, Segment[]> eldest) {
                    return size() > ITEM_MESH_CACHE_CAP;
                }
            };
    /** (model,color) pairs the plan cannot cover - equality semantics, like the cache itself. */
    private final java.util.Set<ItemMeshKey> celtium$uncacheableMeshes = new java.util.HashSet<>();
    /** Per-model tinted check memo: the quad scan is identical work for every draw of the model. */
    private final IdentityHashMap<IBakedModel, Boolean> celtium$tintedModels = new IdentityHashMap<>();
    private long celtium$itemMeshHits, celtium$itemMeshMisses;

    @Shadow
    public abstract void renderQuads(BufferBuilder renderer, List<BakedQuad> quads, int color, ItemStack stack);

    @Inject(method = "func_191967_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$cachedItemModel(IBakedModel model, int color, ItemStack stack, CallbackInfo info) {
        if (!ITEM_MESH_CACHE || model == null) {
            return;
        }
        ItemMeshKey key = new ItemMeshKey(model, color);
        Segment[] plan = this.celtium$itemMeshes.get(key);
        if (plan != null) {
            celtium$replay(plan, stack, color);
            this.celtium$itemMeshHits++;
            info.cancel();
            return;
        }
        if (this.celtium$uncacheableMeshes.contains(key)) {
            return;
        }
        plan = this.celtium$buildPlan(model, color, stack);
        if (plan == null) {
            this.celtium$uncacheableMeshes.add(key);
            return;
        }
        this.celtium$itemMeshes.put(key, plan);
        this.celtium$itemMeshMisses++;
        if (this.celtium$itemMeshMisses == 1 || this.celtium$itemMeshMisses % 200 == 0) {
            System.out.println("[Celtium] CELTIUM_ITEM_MESH misses=" + this.celtium$itemMeshMisses
                    + " hits=" + this.celtium$itemMeshHits + " entries=" + this.celtium$itemMeshes.size());
        }
        info.cancel();
    }

    /**
     * Builds the plan by emitting the first draw through the exact code the original would have run - the vanilla
     * loop when emissive items are off, a faithful copy of {@code ForgeHooksClient.renderLitItem}'s segmentation
     * when they are on - while emitting into a scratch builder per segment so the bytes can be stored.
     *
     * @return {@code null} when the draw cannot be cached (stack-tinted quads or lighting-carrying formats)
     */
    private Segment[] celtium$buildPlan(IBakedModel model, int color, ItemStack stack) {
        boolean stackColored = color == -1 && !stack.isEmpty();
        if (stackColored && celtium$modelHasTintedQuads(model)) {
            return null;
        }
        if (!net.minecraftforge.common.ForgeModContainer.allowEmissiveItems) {
            BufferBuilder scratch = celtium$scratch();
            for (EnumFacing enumfacing : EnumFacing.values()) {
                this.renderQuads(scratch, model.getQuads((IBlockState) null, enumfacing, 0L), color, stack);
            }
            this.renderQuads(scratch, model.getQuads((IBlockState) null, (EnumFacing) null, 0L), color, stack);
            new WorldVertexBufferUploader().draw(scratch);
            return new Segment[] { new Segment(celtium$bytes(scratch), false, 0, 0, false, false) };
        }
        // renderLitItem, faithfully: gather all quads, walk them by (blockLight, skyLight, shade) transitions, and
        // emit a segment whenever the lighting or shading state changes - with ITEM-format quads carrying no light
        // data this usually collapses to one segment, but the structure is kept because mods' quads may differ.
        List<BakedQuad> allquads = new ArrayList<>();
        for (EnumFacing enumfacing : EnumFacing.values()) {
            allquads.addAll(model.getQuads((IBlockState) null, enumfacing, 0L));
        }
        allquads.addAll(model.getQuads((IBlockState) null, (EnumFacing) null, 0L));
        if (allquads.isEmpty()) {
            return new Segment[0];
        }
        // Any quad outside the ITEM layout that carries a light element goes back to the original code: the light
        // gatherer's pipe is the piece this port deliberately does not reimplement, and the format check is what
        // tells it apart.
        VertexFormat itemFormat = DefaultVertexFormats.ITEM;
        for (BakedQuad quad : allquads) {
            if (quad.getFormat() != itemFormat && celtium$quadHasLightElement(quad)) {
                return null;
            }
        }
        List<Segment> segments = new ArrayList<>();
        List<BakedQuad> run = new ArrayList<>();
        int runBl = 0, runSl = 0;
        boolean runShade = true;
        boolean runLightingDirty = false, runShadingDirty = false, hasLighting = false;
        WorldVertexBufferUploader uploader = new WorldVertexBufferUploader();
        for (int i = 0; i < allquads.size(); i++) {
            BakedQuad quad = allquads.get(i);
            boolean shade = quad.shouldApplyDiffuseLighting();
            // ITEM-format quads gather no lighting - established by the format loop above, so bl/sl stay zero here
            // exactly as they stay zero in the original for this case.
            boolean lightingDirty = runBl != 0 || runSl != 0;
            boolean shadeDirty = shade != runShade;
            if (lightingDirty || shadeDirty) {
                if (i > 0) {
                    segments.add(celtium$emitSegment(run, color, stack, runBl, runSl, runShade,
                            runLightingDirty && (hasLighting || run.size() < i), runShadingDirty, uploader));
                }
                runBl = 0;
                runSl = 0;
                runShade = shade;
                runLightingDirty = lightingDirty;
                runShadingDirty = shadeDirty;
                hasLighting = runBl > 0 || runSl > 0 || !runShade;
            }
            run.add(quad);
        }
        segments.add(celtium$emitSegment(run, color, stack, runBl, runSl, runShade,
                runLightingDirty && (hasLighting || run.size() < allquads.size()), runShadingDirty, uploader));
        if (hasLighting) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY);
            net.minecraft.client.renderer.GlStateManager.enableLighting();
        }
        return segments.toArray(new Segment[0]);
    }

    /**
     * One segment's emission for the miss path: the same {@code begin/renderQuads/draw} the original's
     * {@code drawSegment} performs, plus the shade and lighting state changes, recorded for the replay.
     */
    private Segment celtium$emitSegment(List<BakedQuad> run, int color, ItemStack stack, int bl, int sl,
                                        boolean shade, boolean updateLighting, boolean updateShading,
                                        WorldVertexBufferUploader uploader) {
        BufferBuilder scratch = celtium$scratch();
        float lastBl = OpenGlHelper.lastBrightnessX;
        float lastSl = OpenGlHelper.lastBrightnessY;
        if (updateShading) {
            if (shade) {
                net.minecraft.client.renderer.GlStateManager.enableLighting();
            } else {
                net.minecraft.client.renderer.GlStateManager.disableLighting();
            }
        }
        if (updateLighting) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, Math.max(bl, lastBl), Math.max(sl, lastSl));
        }
        this.renderQuads(scratch, run, color, stack);
        uploader.draw(scratch);
        OpenGlHelper.lastBrightnessX = lastBl;
        OpenGlHelper.lastBrightnessY = lastSl;
        run.clear();
        return new Segment(celtium$bytes(scratch), shade, bl, sl, updateShading, updateLighting);
    }

    /**
     * Replays a plan: the identical state calls and bytes for every segment, with the lightmap read live per draw -
     * the part of {@code drawSegment} that genuinely depends on the frame.
     */
    private void celtium$replay(Segment[] plan, ItemStack stack, int color) {
        BufferBuilder shared = Tessellator.getInstance().getBuffer();
        for (Segment segment : plan) {
            shared.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.ITEM);
            float lastBl = OpenGlHelper.lastBrightnessX;
            float lastSl = OpenGlHelper.lastBrightnessY;
            if (segment.updateShading) {
                if (segment.shade) {
                    net.minecraft.client.renderer.GlStateManager.enableLighting();
                } else {
                    net.minecraft.client.renderer.GlStateManager.disableLighting();
                }
            }
            if (segment.updateLighting) {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                        Math.max(segment.blockLight, lastBl), Math.max(segment.skyLight, lastSl));
            }
            shared.addVertexData(segment.verts);
            Tessellator.getInstance().draw();
            OpenGlHelper.lastBrightnessX = lastBl;
            OpenGlHelper.lastBrightnessY = lastSl;
        }
    }

    private static BufferBuilder celtium$scratch() {
        BufferBuilder scratch = new BufferBuilder(1 << 17);
        scratch.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        return scratch;
    }

    private static int[] celtium$bytes(BufferBuilder scratch) {
        java.nio.IntBuffer ints = scratch.getByteBuffer().asIntBuffer();
        ints.rewind();
        int[] data = new int[ints.remaining()];
        ints.get(data);
        return data;
    }

    /** The element-presence test {@code renderLitItem} runs on a quad's format - uv id 1, the light map channel. */
    private static boolean celtium$quadHasLightElement(BakedQuad quad) {
        try {
            return quad.getFormat().hasUvOffset(1);
        } catch (RuntimeException error) {
            return true; // Unanswerable means "maybe lit" - hand it back to the original path.
        }
    }

    private boolean celtium$modelHasTintedQuads(IBakedModel model) {
        Boolean tinted = this.celtium$tintedModels.get(model);
        if (tinted != null) {
            return tinted.booleanValue();
        }
        boolean found = false;
        outer:
        for (EnumFacing enumfacing : EnumFacing.values()) {
            for (BakedQuad quad : model.getQuads((IBlockState) null, enumfacing, 0L)) {
                if (quad.hasTintIndex()) {
                    found = true;
                    break outer;
                }
            }
        }
        if (!found) {
            for (BakedQuad quad : model.getQuads((IBlockState) null, (EnumFacing) null, 0L)) {
                if (quad.hasTintIndex()) {
                    found = true;
                    break;
                }
            }
        }
        this.celtium$tintedModels.put(model, Boolean.valueOf(found));
        return found;
    }

    /** One segment of a plan: the emitted bytes plus the light/shade state {@code drawSegment} applies around them. */
    private static final class Segment {
        final int[] verts;
        final boolean shade;
        final int blockLight, skyLight;
        final boolean updateShading, updateLighting;

        Segment(int[] verts, boolean shade, int bl, int sl, boolean updateShading, boolean updateLighting) {
            this.verts = verts;
            this.shade = shade;
            this.blockLight = bl;
            this.skyLight = sl;
            this.updateShading = updateShading;
            this.updateLighting = updateLighting;
        }
    }

    /**
     * The cache key: model instance identity plus the literal color the caller passed. Identity (not equals) is what
     * matches the emit path's own comparison - two model instances emitting identical bytes stay separate, which is
     * safe if slightly redundant.
     */
    private static final class ItemMeshKey {
        private final IBakedModel model;
        private final int color;

        ItemMeshKey(IBakedModel model, int color) {
            this.model = model;
            this.color = color;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ItemMeshKey)) {
                return false;
            }
            ItemMeshKey that = (ItemMeshKey) other;
            return this.model == that.model && this.color == that.color;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.model) * 31 + this.color;
        }
    }
}
