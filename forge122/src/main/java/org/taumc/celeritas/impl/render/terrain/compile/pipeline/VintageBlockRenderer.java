package org.taumc.celeritas.impl.render.terrain.compile.pipeline;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import net.minecraftforge.registries.IRegistryDelegate;
import org.embeddedt.embeddium.api.util.ColorARGB;
import org.embeddedt.embeddium.impl.model.light.LightMode;
import org.embeddedt.embeddium.impl.model.light.LightPipeline;
import org.embeddedt.embeddium.impl.model.light.LightPipelineProvider;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadOrientation;
import org.embeddedt.embeddium.impl.render.chunk.ChunkColorWriter;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BakedQuadGroupAnalyzer;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.ModelQuadUtil;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.impl.render.terrain.compile.VintageChunkBuildContext;
import org.taumc.celeritas.impl.render.terrain.compile.light.LightDataCache;
import org.taumc.celeritas.impl.render.terrain.compile.light.VintageDiffuseProvider;
import org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess;
import org.taumc.celeritas.mixin.core.terrain.BlockColorsAccessor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class VintageBlockRenderer {

    /** Diagnostic: the brightness the colour writer receives per vertex; it is folded into the vertex colour. */
    private static final boolean BRIGHTNESS_DIAGNOSTICS =
            Boolean.getBoolean("celtium.terrain.brightnessDiagnostics");
    private static final java.util.Map<Integer, Integer> BRIGHTNESS_HISTOGRAM = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile int brightnessWrites;

    private static void recordBrightness(float value) {
        if (!BRIGHTNESS_DIAGNOSTICS) {
            return;
        }
        BRIGHTNESS_HISTOGRAM.merge(Integer.valueOf(Math.round(value * 100.0f)), Integer.valueOf(1), Integer::sum);
        brightnessWrites++;
        if (brightnessWrites == 1 || brightnessWrites % 2000 == 0) {
            StringBuilder text = new StringBuilder("[Celtium] CELTIUM_BRIGHTNESS n=" + brightnessWrites);
            int shown = 0;
            for (var entry : new java.util.TreeMap<>(BRIGHTNESS_HISTOGRAM).entrySet()) {
                if (shown++ >= 8) break;
                text.append(' ').append(entry.getKey().intValue() / 100.0f).append('x').append(entry.getValue());
            }
            System.out.println(text);
        }
    }
    private final VintageChunkBuildContext context;
    private final VertexBufferConsumer consumer;
    private final LightPipelineProvider lighters;
    private final Map<IRegistryDelegate<Block>, IBlockColor> blockColors;

    private final QuadLightData quadLightData = new QuadLightData();
    private final int[] quadColors = new int[4];
    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    private final boolean useRenderPassOptimization;

    private IBlockState currentState;
    private CeleritasBlockAccess currentBlockAccess;

    private final BakedQuadGroupAnalyzer analyzer = new BakedQuadGroupAnalyzer();

    private int currentQuadRenderingFlags;


    public VintageBlockRenderer(VintageChunkBuildContext context, LightDataCache cache) {
        this.consumer = new VertexBufferConsumer();
        this.context = context;
        // The third argument is the quad-normals shading option; it was a literal true here,
        // which left the config key (use_quad_normals_for_shading_v2) with no effect on the
        // only mesher this module has. The field lives in QualitySettings - unlike
        // useRenderPassOptimization below - and has since the options refactor that made the
        // options logic common; reading it through performance does not compile. The field
        // defaults to true, so the default shading is unchanged.
        this.lighters = new LightPipelineProvider(cache, VintageDiffuseProvider.INSTANCE,
                CeleritasVintage.options().quality.useQuadNormalsForShading);
        this.blockColors = ((BlockColorsAccessor)Minecraft.getMinecraft().getBlockColors()).getBlockColorMap();
        this.useRenderPassOptimization = CeleritasVintage.options().performance.useRenderPassOptimization;
    }

    public void renderBlock(IBlockState state, BlockPos pos, CeleritasBlockAccess blockAccess, BlockRenderLayer layer) {
        if (blockAccess.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
            // B23: vanilla tolerates a throwing getActualState (BlockRendererDispatcher.java:65-72 -
            // catches Exception and keeps the un-extended state). The fast path must be able to run
            // where vanilla could, so the same guard applies here.
            try {
                state = state.getActualState(blockAccess, pos);
            } catch (Exception ignored) {
                ;
            }
        }
        // B23: the model is fetched through the dispatcher's entry, not the shapes' own - that is the
        // call site mods intercept (BlockRendererDispatcher.getModelForState), and going straight to
        // blockModelShapes skipped every hook installed there for an identical vanilla result.
        var model = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(state);
        renderModel(model, state, pos, blockAccess, layer);
    }

    /**
     * Renders one block from a model the caller already holds: the drawing half of
     * {@link #renderBlock}, split out so a caller that arrives with a model (rather than
     * with just a state) enters at the same drawing code.
     *
     * <p>Part of celtium's Demonica compatibility surface (design: COMPAT-SURFACE-DESIGN-2026-11-17.md,
     * Tier B). Demonica's patch S13 calls this entry when it routes a block from vanilla's
     * dispatcher to the fast renderer, and its MESHING patches S10/S11 inject into it; with
     * no such entry the injections silently never applied. The state passed in is extended
     * here, as {@code renderBlock} always did before drawing.
     *
     * @return true when at least one quad list was rendered
     */
    public boolean renderModel(IBakedModel model, IBlockState state, BlockPos pos, CeleritasBlockAccess blockAccess, BlockRenderLayer layer) {
        int defaultFlags = BakedQuadGroupAnalyzer.USE_ALL_THINGS;
        if (!useRenderPassOptimization) {
            defaultFlags &= ~BakedQuadGroupAnalyzer.USE_RENDER_PASS_OPTIMIZATION;
        }
        this.analyzer.setDefaultRenderingFlags(defaultFlags);

        state = state.getBlock().getExtendedState(state, blockAccess, pos);
        this.currentState = state;
        this.currentBlockAccess = blockAccess;

        var buffers = this.context.buffers;
        var material = buffers.getRenderPassConfiguration().getMaterialForRenderType(layer);
        var buffer = buffers.get(material);

        long rand = MathHelper.getPositionRandom(pos);

        IBlockColor colorProvider = this.blockColors.get(state.getBlock().delegate);

        var offset = this.currentState.getOffset(blockAccess, pos);

        var aoEnabled = Minecraft.isAmbientOcclusionEnabled() && state.getLightValue(blockAccess, pos) == 0 && model.isAmbientOcclusion(state);

        var lighter = this.lighters.getLighter(aoEnabled ? LightMode.SMOOTH : LightMode.FLAT);

        boolean rendered = false;

        for (var dir : EnumFacing.VALUES) {
            var quads = model.getQuads(state, dir, rand);

            if (quads.isEmpty() || !state.shouldSideBeRendered(blockAccess, pos, dir)) {
                continue;
            }

            this.currentQuadRenderingFlags = this.analyzer.getFlagsForRendering(VintageDiffuseProvider.fromEnumFacing(dir), BakedQuadView.ofList(quads));
            renderQuadList(buffer, buffers, material, pos, dir, lighter, colorProvider, offset, quads);
            rendered = true;
        }

        var quads = model.getQuads(state, null, rand);

        if (!quads.isEmpty()) {
            this.currentQuadRenderingFlags = this.analyzer.getFlagsForRendering(ModelQuadFacing.UNASSIGNED, BakedQuadView.ofList(quads));
            renderQuadList(buffer, buffers, material, pos, null, lighter, colorProvider, offset, quads);
            rendered = true;
        }

        this.currentBlockAccess = null;
        return rendered;
    }

    /**
     * The orientation of one quad, decided from that quad's own light. {@link ModelQuadOrientation}'s
     * contract is per-quad - the split diagonal follows the quad's own vertex brightness pattern - so
     * a later quad on the same face of the same block must not inherit an earlier quad's answer. (A
     * per-face cache did exactly that, across render layers too; its cross-layer rationale does not
     * need it, because the light calculation is layer-independent and deterministic, so the same
     * quad already receives the same orientation in every layer.) Unassigned quads keep NORMAL.
     */
    static ModelQuadOrientation orientationFor(EnumFacing cullFace, QuadLightData light) {
        return cullFace != null ? ModelQuadOrientation.orientByBrightness(light.br, light.lm) : ModelQuadOrientation.NORMAL;
    }

    private QuadLightData getVertexLight(LightPipeline lighter, BlockPos pos, EnumFacing cullFace, BakedQuadView quad) {
        QuadLightData light = this.quadLightData;
        lighter.calculate(quad, pos.getX(), pos.getY(), pos.getZ(), light, VintageDiffuseProvider.fromEnumFacingOrUnassigned(cullFace), quad.getLightFace(), quad.hasShade(), false);

        return light;
    }

    private int[] getVertexColors(BlockPos pos, IBlockColor colorProvider, BakedQuadView quad) {
        final int[] vertexColors = this.quadColors;

        if (colorProvider != null && quad.hasColor()) {
            // Force full alpha on all colors
            Arrays.fill(vertexColors, 0xFF000000 | ColorARGB.toABGR(colorProvider.colorMultiplier(this.currentState, this.currentBlockAccess, pos, quad.getColorIndex())));
        } else {
            Arrays.fill(vertexColors, 0xFFFFFFFF);
        }

        return vertexColors;
    }

    private void renderQuadList(ChunkModelBuilder defaultBuffer, ChunkBuildBuffers buffers,
                                Material material, BlockPos pos, EnumFacing cullFace,
                                LightPipeline lighter, IBlockColor colorProvider, Vec3d offset,
                                List<BakedQuad> quads) {
        int localX = pos.getX() & 15, localY = pos.getY() & 15, localZ = pos.getZ() & 15;
        var config = buffers.getRenderPassConfiguration();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            var quad = quads.get(i);
            var format = quad.getFormat();
            if (format != DefaultVertexFormats.ITEM && format != DefaultVertexFormats.BLOCK && format.getElement(0).getUsage() != VertexFormatElement.EnumUsage.POSITION) {
                throw new IllegalStateException("Vertex format does not have POSITION as first element: " + format);
            }

            BakedQuadView quadView = (BakedQuadView)quad;

            // Run through our light pipeline
            var light = this.getVertexLight(lighter, pos, cullFace, quadView);
            var colors = this.getVertexColors(pos, colorProvider, quadView);

            ModelQuadOrientation orientation = orientationFor(cullFace, light);

            var quadMaterial = BakedQuadGroupAnalyzer.chooseOptimalMaterial(this.currentQuadRenderingFlags, material, config, BakedQuadView.of(quad));
            ChunkModelBuilder buffer = (quadMaterial == material) ? defaultBuffer : buffers.get(quadMaterial);

            this.writeGeometry(localX, localY, localZ, buffer, offset, quadMaterial,
                    quadView, colors, light, orientation);

            TextureAtlasSprite sprite = (TextureAtlasSprite)quadView.celeritas$getSprite();

            if (sprite.hasAnimationMetadata() && buffer.getSectionContextBundle() instanceof MinecraftBuiltRenderSectionData<?,?> mcData) {
                //noinspection unchecked
                ((Collection<TextureAtlasSprite>)mcData.animatedSprites).add(sprite);
            }
        }
    }

    private void writeGeometry(int localX, int localY, int localZ, ChunkModelBuilder builder,
                               Vec3d offset,
                               Material material,
                               BakedQuadView quad,
                               int[] colors,
                               QuadLightData light,
                               ModelQuadOrientation orientation)
    {
        var vertices = this.vertices;

        ModelQuadFacing normalFace = quad.getNormalFace();

        int vanillaNormal = normalFace.getPackedNormal();
        int trueNormal = quad.getComputedFaceNormal();

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = orientation.getVertexIndex(dstIndex);

            var out = vertices[dstIndex];
            out.x = localX + quad.getX(srcIndex) + (float) offset.x;
            out.y = localY + quad.getY(srcIndex) + (float) offset.y;
            out.z = localZ + quad.getZ(srcIndex) + (float) offset.z;

            out.color = ChunkColorWriter.EMBEDDIUM.writeColor(ModelQuadUtil.mixARGBColors(colors[srcIndex], quad.getColor(srcIndex)), light.br[srcIndex]);
            recordBrightness(light.br[srcIndex]);

            out.u = quad.getTexU(srcIndex);
            out.v = quad.getTexV(srcIndex);

            out.light = ModelQuadUtil.mergeBakedLight(quad.getLight(srcIndex), quad.getVanillaLightEmission(), light.lm[srcIndex]);

            out.vanillaNormal = vanillaNormal;
            out.trueNormal = trueNormal;
        }

        var vertexBuffer = builder.getVertexBuffer(normalFace);
        vertexBuffer.push(vertices, material);
    }
}
