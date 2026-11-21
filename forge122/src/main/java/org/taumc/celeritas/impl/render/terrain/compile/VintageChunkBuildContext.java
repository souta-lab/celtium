package org.taumc.celeritas.impl.render.terrain.compile;

//? if 1.10.2 {
//?}
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;
import org.lwjgl.opengl.GL11;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.impl.extensions.TextureMapExtension;
import org.taumc.celeritas.impl.render.terrain.compile.light.LightDataCache;
import org.taumc.celeritas.impl.render.terrain.compile.pipeline.VintageBlockRenderer;
import org.taumc.celeritas.impl.world.WorldSlice;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

public class VintageChunkBuildContext extends ChunkBuildContext {
    /**
     * Snapshot kept for compatibility; runtime-added {@link BlockRenderLayer} constants (e.g. GTCEu's Bloom) are
     * read fresh per context via {@link #layers()} so class-load order can never hide a modded layer.
     */
    public static final BlockRenderLayer[] LAYERS = BlockRenderLayer.values();
    private final BlockRenderLayer[] layers = BlockRenderLayer.values();
    private final TextureMapExtension textureAtlas;
    private final ChunkBufferBuilder[] worldRenderers = new ChunkBufferBuilder[layers.length];
    private final boolean[] usedWorldRenderers = new boolean[layers.length];

    /** {@return the active block render layers, including any enum constants added at runtime}. */
    public BlockRenderLayer[] layers() {
        return this.layers;
    }
    @Getter
    private int offX, offY, offZ;
    @Getter
    private final WorldSlice worldSlice;
    @Getter
    private final VintageBlockRenderer blockRenderer;
    private final RenderPassConfiguration<?> renderPassConfiguration;
    private final LightDataCache lightDataCache;
    private final boolean useRenderPassOptimization;

    public VintageChunkBuildContext(WorldClient world, RenderPassConfiguration renderPassConfiguration) {
        super(renderPassConfiguration);
        this.renderPassConfiguration = renderPassConfiguration;
        this.worldSlice = new WorldSlice(world);
        this.lightDataCache = new LightDataCache(this.worldSlice);
        this.blockRenderer = new VintageBlockRenderer(this, lightDataCache);
        this.textureAtlas = (TextureMapExtension) Minecraft.getMinecraft().getTextureMapBlocks();
        this.useRenderPassOptimization = CeleritasVintage.options().performance.useRenderPassOptimization;
    }

    public void setupTranslation(int x, int y, int z) {
        this.lightDataCache.reset(x, y, z);

        this.offX = x;
        this.offY = y;
        this.offZ = z;
    }

    public net.minecraft.client.renderer.BufferBuilder getBufferForLayer(BlockRenderLayer layer) {
        var builder = this.worldRenderers[layer.ordinal()];
        if (builder == null) {
            // The one construction site for the build buffers (Tier B of the Demonica
            // compatibility surface): the buffer carries this context and its layer so
            // that wrappers around vanilla's dispatcher can route it to the fast renderer.
            builder = new ChunkBufferBuilder(131072, this, layer);
            this.worldRenderers[layer.ordinal()] = builder;
        }
        if (!this.usedWorldRenderers[layer.ordinal()]) {
            builder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            builder.setTranslation(-this.offX, -this.offY, -this.offZ);
            this.usedWorldRenderers[layer.ordinal()] = true;
        }
        return builder;
    }

    public void convertVanillaDataToCeleritasData(ChunkBuildBuffers buffers) {
        var renderers = this.worldRenderers;
        var used = this.usedWorldRenderers;
        for (int i = 0; i < renderers.length; i++) {
            if(!used[i]) {
                continue;
            }
            var bufferBuilder = Objects.requireNonNull(renderers[i]);
            bufferBuilder.finishDrawing();
            used[i] = false;
            ByteBuffer rawBuffer = bufferBuilder.getByteBuffer();
            var material = buffers.getRenderPassConfiguration().getMaterialForRenderType(this.layers[i]);
            copyBlockData(rawBuffer, buffers, material);
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        this.worldSlice.reset();
        for (int i = 0; i < this.layers.length; i++) {
            if (this.usedWorldRenderers[i]) {
                this.worldRenderers[i].finishDrawing();
                this.usedWorldRenderers[i] = false;
            }
        }
    }

    private Material selectMaterial(Material material, TextureAtlasSprite sprite) {
        if (sprite != null && sprite.getClass() == TextureAtlasSprite.class && !sprite.hasAnimationMetadata() && this.useRenderPassOptimization) {
            return selectOptimizedMaterial(material,
                    ((SpriteTransparencyLevel.Holder) sprite).embeddium$getTransparencyLevel(),
                    this.renderPassConfiguration.defaultSolidMaterial(),
                    this.renderPassConfiguration.defaultCutoutMippedMaterial(),
                    this.renderPassConfiguration.defaultTranslucentMaterial(),
                    this.renderPassConfiguration.getMaterialForRenderType(BlockRenderLayer.CUTOUT));
        }
        return material;
    }

    /**
     * Pure render-pass downgrade rule: only vanilla cutout/translucent materials may be downgraded based on sprite
     * transparency. Custom mod render layers (such as GTCEu's Bloom layer) MUST keep their own material even when
     * their sprite is opaque, otherwise their geometry is moved into SOLID and the custom pass draws nothing.
     */
    static Material selectOptimizedMaterial(Material material,
                                            SpriteTransparencyLevel transparencyLevel,
                                            Material solidMaterial,
                                            Material cutoutMippedMaterial,
                                            Material translucentMaterial,
                                            Material cutoutMaterial) {
        boolean isVanillaDowngradable = material == cutoutMaterial
                || material == cutoutMippedMaterial
                || material == translucentMaterial;
        if (!isVanillaDowngradable) {
            return material;
        }
        if (transparencyLevel == SpriteTransparencyLevel.OPAQUE) {
            return solidMaterial;
        } else if (material == translucentMaterial && transparencyLevel != SpriteTransparencyLevel.TRANSLUCENT) {
            return cutoutMippedMaterial;
        }
        return material;
    }

    private static final int BLOCK_VERTEX_FORMAT_SIZE;

    static {
        var format = DefaultVertexFormats.BLOCK;
        int size = 0;
        for (int i = 0; i < format.getElementCount(); i++) {
            size += format.getElement(i).getSize();
        }
        BLOCK_VERTEX_FORMAT_SIZE = size;
    }

    private void copyBlockData(ByteBuffer source, ChunkBuildBuffers buffers, Material material) {
        int vsize = BLOCK_VERTEX_FORMAT_SIZE;
        int numQuads = source.limit() / (vsize * 4);
        long ptr = LWJGL.memAddress(source);
        var quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
        var animatedSpritesList = ((MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity>)buffers.getSectionContextBundle()).animatedSprites;
        for(int q = 0; q < numQuads; q++) {
            float uSum = 0, vSum = 0;
            for(int v = 0; v < 4; v++) {
                var vertex = quad[v];
                vertex.x = LWJGL.memGetFloat(ptr);
                vertex.y = LWJGL.memGetFloat(ptr + 4);
                vertex.z = LWJGL.memGetFloat(ptr + 8);
                vertex.color = LWJGL.memGetInt(ptr + 12);
                vertex.u = LWJGL.memGetFloat(ptr + 16);
                vertex.v = LWJGL.memGetFloat(ptr + 20);
                uSum += vertex.u;
                vSum += vertex.v;
                vertex.light = LWJGL.memGetInt(ptr + 24);
                ptr += vsize;
            }
            TextureAtlasSprite sprite = this.textureAtlas.celeritas$findFromUV(uSum * 0.25f, vSum * 0.25f);
            if (sprite != null && sprite.hasAnimationMetadata()) {
                animatedSpritesList.add(sprite);
            }
            int trueNormal = QuadUtil.calculateNormal(quad);
            for (int v = 0; v < 4; v++) {
                var vertex = quad[v];
                vertex.vanillaNormal = trueNormal;
                vertex.trueNormal = trueNormal;
            }
            ModelQuadFacing facing = QuadUtil.findNormalFace(trueNormal);
            Material correctMaterial = selectMaterial(material, sprite);
            buffers.get(correctMaterial).getVertexBuffer(facing).push(quad, correctMaterial);
        }
    }
}
