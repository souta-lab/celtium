package org.taumc.celeritas.mixin.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
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
import net.minecraftforge.common.ForgeModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item mesh cache, ported from Angelica's RenderItem optimization (PR1034, LGPL-3.0): a finished
 * {@code func_191967_a(model, color, stack)} draw is, for almost every item, the identical sequence
 * of {@code begin(QUADS, ITEM)} -> {@code renderQuads(...)} -> {@code draw()} calls on the shared
 * tessellator. That work depends on (model geometry, color) - not on the frame - so the per-segment
 * vertex bytes are emitted once through the genuine code path into a scratch builder, stored, and
 * replayed later as {@code begin/addVertexData/draw} calls. Draw order, applied state, and every
 * refusal path stay byte-identical to the uncached path; only the quad-emitting loop is skipped.
 * <p>
 * Not cached, the inputs that genuinely vary per call: a stack-tinted render ({@code color == -1 &&
 * !stack.isEmpty()}) on a model with tinted quads (the bytes are then per-stack), and a model whose
 * quads carry lighting data outside the ITEM format - Forge's light-gatherer path handles those and
 * keeps handling them here.
 * <p>
 * Audit fixes (docs/audit-2026-10-12-maximum.md B01/B02/B14):
 * <ul>
 * <li>B01: the scratch {@code BufferBuilder} is closed with {@code finishDrawing()} before upload,
 * exactly as {@code Tessellator.draw()} does it, and the stored bytes are read from the resulting
 * [0, used) range. Previously {@code WorldVertexBufferUploader.draw()} was invoked without
 * {@code finishDrawing()} and {@code reset()} restored only {@code vertexCount}, so the saved array
 * was the whole scratch capacity (32768 ints for a 168-int cube) and a hit re-emitted thousands of
 * garbage vertices.</li>
 * <li>B02: the replay applies {@code renderLitItem}'s post-loop cleanup ({@code hasLighting} ->
 * lightmap restore + {@code enableLighting}) in a {@code finally}, so a hit leaves the same outgoing
 * lighting state as a miss - including on an exception.</li>
 * <li>B14: the negative set and the tint memo are bounded like the positive cache; all three plus the
 * plans live under one resource-manager epoch (identity of {@code Minecraft#getResourceManager()},
 * checked per call - a reload replaces the instance and clears every entry). A cached plan also
 * re-verifies the model's null-face quad list (size + first-quad identity) on every hit, so a model
 * whose {@code getQuads} output mutates invalidates instead of replaying stale bytes; repeated
 * invalidation demotes the key to uncacheable.</li>
 * </ul>
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
    /**
     * Probe failures tolerated before the key is demoted to uncacheable: a model mutating its quads
     * per draw would otherwise rebuild its plan every frame.
     */
    private static final int ITEM_MESH_INVALIDATIONS = 4;

    /** model+color-keyed plans; render-thread confined, like every other per-draw structure here. */
    private final Map<ItemMeshKey, Plan> celtium$itemMeshes =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ItemMeshKey, Plan> eldest) {
                    return size() > ITEM_MESH_CACHE_CAP;
                }
            };
    /**
     * (model,color) pairs the plan cannot cover - equality semantics, like the cache itself, and the
     * same bound. LinkedHashMap-as-set so the eldest unprofitable key evicts instead of growing
     * without limit.
     */
    private final Map<ItemMeshKey, Boolean> celtium$uncacheableMeshes =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ItemMeshKey, Boolean> eldest) {
                    return size() > ITEM_MESH_CACHE_CAP;
                }
            };
    /**
     * Per-model tinted check memo: the quad scan is identical work for every draw of the model.
     * Bounded with the rest and dies with the same epoch - a resource reload replaces the models it
     * keyes on.
     */
    private final Map<IBakedModel, Boolean> celtium$tintedModels =
            new java.util.LinkedHashMap<IBakedModel, Boolean>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<IBakedModel, Boolean> eldest) {
                    return size() > ITEM_MESH_CACHE_CAP;
                }
            };
    /** Probe failures per key, for demotion; bounded with the rest. */
    private final Map<ItemMeshKey, Integer> celtium$probeFailures =
            new java.util.LinkedHashMap<ItemMeshKey, Integer>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ItemMeshKey, Integer> eldest) {
                    return size() > ITEM_MESH_CACHE_CAP;
                }
            };
    /**
     * The resource-manager instance the caches belong to. A resource reload installs a new resource
     * manager and every model instance it keyed is replaced with it; the identity check turns the
     * reload into one cheap epoch bump instead of a stale-geometry leak.
     */
    private Object celtium$resourceEpoch;
    private long celtium$itemMeshHits, celtium$itemMeshMisses, celtium$itemMeshInvalidations;

    @Shadow
    public abstract void renderQuads(BufferBuilder renderer, List<BakedQuad> quads, int color, ItemStack stack);

    @Inject(method = "func_191967_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$cachedItemModel(IBakedModel model, int color, ItemStack stack, CallbackInfo info) {
        if (!ITEM_MESH_CACHE || model == null) {
            return;
        }
        Object manager = Minecraft.getMinecraft().getResourceManager();
        if (manager != this.celtium$resourceEpoch) {
            this.celtium$resourceEpoch = manager;
            this.celtium$itemMeshes.clear();
            this.celtium$uncacheableMeshes.clear();
            this.celtium$tintedModels.clear();
            this.celtium$probeFailures.clear();
        }
        ItemMeshKey key = new ItemMeshKey(model, color);
        Plan plan = this.celtium$itemMeshes.get(key);
        if (plan != null) {
            if (celtium$probe(model, plan)) {
                celtium$replay(plan, stack, color);
                this.celtium$itemMeshHits++;
                info.cancel();
                return;
            }
            // Geometry changed under the same model instance: drop the plan and demote when it
            // keeps happening, rather than replaying bytes built from quads that no longer exist.
            this.celtium$itemMeshes.remove(key);
            this.celtium$itemMeshInvalidations++;
            Integer failures = this.celtium$probeFailures.get(key);
            int count = failures == null ? 1 : failures.intValue() + 1;
            if (count >= ITEM_MESH_INVALIDATIONS) {
                this.celtium$probeFailures.remove(key);
                this.celtium$uncacheableMeshes.put(key, Boolean.TRUE);
            } else {
                this.celtium$probeFailures.put(key, Integer.valueOf(count));
            }
        }
        if (this.celtium$uncacheableMeshes.containsKey(key)) {
            return;
        }
        plan = this.celtium$buildPlan(model, color, stack);
        if (plan == null) {
            this.celtium$uncacheableMeshes.put(key, Boolean.TRUE);
            return;
        }
        this.celtium$itemMeshes.put(key, plan);
        this.celtium$itemMeshMisses++;
        if (this.celtium$itemMeshMisses == 1 || this.celtium$itemMeshMisses % 200 == 0
                || this.celtium$itemMeshInvalidations > 0) {
            System.out.println("[Celtium] CELTIUM_ITEM_MESH misses=" + this.celtium$itemMeshMisses
                    + " hits=" + this.celtium$itemMeshHits + " entries=" + this.celtium$itemMeshes.size()
                    + " uncacheable=" + this.celtium$uncacheableMeshes.size()
                    + " invalidations=" + this.celtium$itemMeshInvalidations);
        }
        info.cancel();
    }

    /**
     * The mutation probe: vanilla reads the null-face quad list on every draw, so re-reading it on a
     * hit costs one {@code getQuads} call where the original ran seven. Size plus first-quad identity
     * is the cheap stability signal - a dynamic model that rotates or rebuilds its quads changes one
     * of the two almost always, and a false negative only costs a rebuilt plan.
     */
    private static boolean celtium$probe(IBakedModel model, Plan plan) {
        List<BakedQuad> probe = model.getQuads((IBlockState) null, (EnumFacing) null, 0L);
        if (probe.size() != plan.probeCount) {
            return false;
        }
        BakedQuad first = probe.isEmpty() ? null : probe.get(0);
        return first == plan.probeQuad;
    }

    /**
     * Builds the plan by emitting the first draw through the exact code the original would have run - the vanilla
     * loop when emissive items are off, a faithful copy of {@code ForgeHooksClient.renderLitItem}'s segmentation
     * when they are on - while emitting into a scratch builder per segment so the bytes can be stored.
     *
     * @return {@code null} when the draw cannot be cached (stack-tinted quads or lighting-carrying formats)
     */
    private Plan celtium$buildPlan(IBakedModel model, int color, ItemStack stack) {
        boolean stackColored = color == -1 && !stack.isEmpty();
        if (stackColored && celtium$modelHasTintedQuads(model)) {
            return null;
        }
        // The probe records what the hit path re-verifies: the null-face list's size and first quad.
        List<BakedQuad> probe = model.getQuads((IBlockState) null, (EnumFacing) null, 0L);
        BakedQuad probeQuad = probe.isEmpty() ? null : probe.get(0);
        if (!ForgeModContainer.allowEmissiveItems) {
            BufferBuilder scratch = celtium$scratch();
            for (EnumFacing enumfacing : EnumFacing.values()) {
                this.renderQuads(scratch, model.getQuads((IBlockState) null, enumfacing, 0L), color, stack);
            }
            this.renderQuads(scratch, model.getQuads((IBlockState) null, (EnumFacing) null, 0L), color, stack);
            // Tessellator.draw()'s contract, applied to the scratch builder: finishDrawing freezes the
            // used range before the uploader runs and before the bytes are read out of the same range.
            scratch.finishDrawing();
            int[] data = celtium$bytes(scratch);
            new WorldVertexBufferUploader().draw(scratch);
            return new Plan(new Segment[] {
                    new Segment(data, false, 0, 0, false, false) }, false, probeQuad, probe.size());
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
            return new Plan(new Segment[0], false, probeQuad, probe.size());
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
            GlStateManager.enableLighting();
        }
        return new Plan(segments.toArray(new Segment[0]), hasLighting, probeQuad, probe.size());
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
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
        }
        if (updateLighting) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, Math.max(bl, lastBl), Math.max(sl, lastSl));
        }
        this.renderQuads(scratch, run, color, stack);
        // B01: finishDrawing before the uploader is the contract Tessellator.draw() applies to the shared
        // builder - without it the stored range was the buffer's capacity instead of its used range.
        scratch.finishDrawing();
        int[] data = celtium$bytes(scratch);
        uploader.draw(scratch);
        OpenGlHelper.lastBrightnessX = lastBl;
        OpenGlHelper.lastBrightnessY = lastSl;
        run.clear();
        return new Segment(data, shade, bl, sl, updateShading, updateLighting);
    }

    /**
     * Replays a plan: the identical state calls and bytes for every segment, with the lightmap read live per draw -
     * the part of {@code drawSegment} that genuinely depends on the frame. The post-loop cleanup mirrors
     * {@code renderLitItem}: when the plan recorded lighting, the lightmap coordinates captured at entry are
     * restored and lighting is re-enabled - in a {@code finally}, so a hit (or a throw) leaves the same outgoing
     * state a miss would.
     */
    private void celtium$replay(Plan plan, ItemStack stack, int color) {
        BufferBuilder shared = Tessellator.getInstance().getBuffer();
        float entryBl = OpenGlHelper.lastBrightnessX;
        float entrySl = OpenGlHelper.lastBrightnessY;
        try {
            for (Segment segment : plan.segments) {
                shared.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.ITEM);
                float lastBl = OpenGlHelper.lastBrightnessX;
                float lastSl = OpenGlHelper.lastBrightnessY;
                if (segment.updateShading) {
                    if (segment.shade) {
                        GlStateManager.enableLighting();
                    } else {
                        GlStateManager.disableLighting();
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
        } finally {
            if (plan.hasLighting) {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, entryBl, entrySl);
                GlStateManager.enableLighting();
            }
        }
    }

    private static BufferBuilder celtium$scratch() {
        BufferBuilder scratch = new BufferBuilder(1 << 17);
        scratch.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        return scratch;
    }

    /**
     * Reads the builder's used vertex range. Correct only after {@code finishDrawing()} has run: it
     * rewinds the position to 0 and sets the limit to the used size - the contract
     * {@code Tessellator.draw()} hands the uploader, and the one this method assumes.
     */
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

    /**
     * A plan: the segments plus the plan-level facts the replay cannot recompute - {@code hasLighting}
     * for {@code renderLitItem}'s post-loop cleanup, and the null-face probe
     * ({@code probeQuad}/{@code probeCount}) the hit path re-verifies before trusting the bytes.
     */
    private static final class Plan {
        final Segment[] segments;
        final boolean hasLighting;
        final BakedQuad probeQuad;
        final int probeCount;

        Plan(Segment[] segments, boolean hasLighting, BakedQuad probeQuad, int probeCount) {
            this.segments = segments;
            this.hasLighting = hasLighting;
            this.probeQuad = probeQuad;
            this.probeCount = probeCount;
        }
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
