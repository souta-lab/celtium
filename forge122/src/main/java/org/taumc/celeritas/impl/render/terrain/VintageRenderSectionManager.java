package org.taumc.celeritas.impl.render.terrain;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.*;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.lists.SectionTicker;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.embeddedt.embeddium.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.jetbrains.annotations.Nullable;
import org.taumc.celeritas.impl.render.terrain.fog.GLStateManagerFogService;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.impl.render.terrain.compile.VintageChunkBuildContext;
import org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;
import org.taumc.celeritas.impl.render.terrain.sprite.SpriteUtil;
import org.taumc.celeritas.impl.world.WorldSlice;
import org.taumc.celeritas.impl.world.cloned.ChunkRenderContext;
import org.taumc.celeritas.impl.world.cloned.ClonedChunkSectionCache;

import java.util.List;

public class VintageRenderSectionManager extends RenderSectionManager {
    private final WorldClient world;
    @Getter
    private final ClonedChunkSectionCache sectionCache;

    public VintageRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world, int renderDistance, CommandList commandList, int minSection, int maxSection) {
        super(configuration, () -> new VintageChunkBuildContext(world, configuration), ChunkRenderer::new, renderDistance, commandList, minSection, maxSection, CeleritasVintage.options().performance.chunkBuilderThreads);
        this.world = world;
        this.sectionCache = new ClonedChunkSectionCache(world);
    }

    public static VintageRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList) {
        return new VintageRenderSectionManager(VintageRenderPassConfigurationBuilder.build(vertexType), world, renderDistance, commandList, 0, 16);
    }

    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        // -Dceltium.terrain.asyncOcclusion=NONE|ONLY_SHADOW|EVERYTHING overrides the options file, so a leg can A/B the
        // search thread against the same copied instance.
        String forced = System.getProperty("celtium.terrain.asyncOcclusion");
        if (forced != null) {
            return AsyncOcclusionMode.valueOf(forced.trim().toUpperCase(java.util.Locale.ROOT));
        }
        // The terrain search runs on the search thread unless the option says NONE. Core has no shadow pass, so the
        // option's default ONLY_SHADOW meant "nothing async" here; the inline search was 28% of the client thread
        // while moving (AMD flight leg), and taking it off moved flight p50 1.03-1.34 -> 0.74-0.89 ms, static Intel
        // KPI neutral (0.983x vs control), validation 0 and the same frames.
        AsyncOcclusionMode chosen = CeleritasVintage.options().performance.asyncOcclusionMode;
        return chosen == AsyncOcclusionMode.NONE ? AsyncOcclusionMode.NONE : AsyncOcclusionMode.EVERYTHING;
    }

    @Override
    protected boolean shouldRespectUpdateTaskQueueSizeLimit() {
        return true;
    }

    @Override
    protected boolean useRasterOcclusionCulling() {
        return false;
    }

    @Override
    public FogService getFogService() {
        return GLStateManagerFogService.INSTANCE;
    }

    @Override
    protected boolean useFogOcclusion() {
        return CeleritasVintage.options().performance.useFogOcclusion;
    }

    /** {@code -Dceltium.debug.noOcclusion=true}: frustum-only traversal, to tell a culling defect from a build one. */
    private static final boolean CELTIUM_NO_OCCLUSION = Boolean.getBoolean("celtium.debug.noOcclusion");

    @Override
    protected boolean shouldUseOcclusionCulling(Viewport positionedViewport, boolean spectator) {
        if (CELTIUM_NO_OCCLUSION) {
            return false;
        }
        final boolean useOcclusionCulling;
        var camBlockPos = positionedViewport.getBlockCoord();
        BlockPos origin = new BlockPos(camBlockPos.x(), camBlockPos.y(), camBlockPos.z());

        if (spectator && this.world.getBlockState(origin).isOpaqueCube())
        {
            useOcclusionCulling = false;
        } else {
            useOcclusionCulling = Minecraft.getMinecraft().renderChunksMany;
        }

        return useOcclusionCulling;
    }

    /** Bounded report counter for the emptiness decision. */
    private static int celtium$emptyReports;

    @Override
    protected boolean isSectionVisuallyEmpty(int x, int y, int z) {
        Chunk chunk = this.world.getChunk(x, z);
        boolean chunkEmpty = chunk.isEmpty();

        if (!chunkEmpty && celtium$emptyReports < 12) {
            celtium$emptyReports++;
            var storage = chunk.getBlockStorageArray();
            System.out.println("[Celtium] CELTIUM_EMPTY x=" + x + " y=" + y + " z=" + z
                    // The class is the point: ChunkProviderClient.provideChunk returns its blankChunk - an EmptyChunk -
                    // when the coordinates are not in its mapping, so an EmptyChunk here says the client never received
                    // that chunk, while a real Chunk with an empty slot says the data arrived but the section is air.
                    + " chunkClass=" + chunk.getClass().getSimpleName()
                    // Identity, not just class: if the manager holds a different WorldClient than the one the chunk
                    // tracker is attached to, every lookup here returns the provider's blank EmptyChunk and nothing is
                    // ever built - which looks identical to a world with no chunks from every other counter.
                    + " world=" + System.identityHashCode(this.world)
                    + " chunkEmpty=" + chunkEmpty
                    + " storageLength=" + storage.length
                    + " slot=" + (y >= 0 && y < storage.length
                            ? (storage[y] == Chunk.NULL_BLOCK_STORAGE ? "null" : (storage[y].isEmpty() ? "empty" : "hasBlocks"))
                            : "outOfRange"));
        }

        if (chunkEmpty) {
            return true;
        }
        var array = chunk.getBlockStorageArray();
        if (y < 0 || y >= array.length) {
            return true;
        }
        return array[y] == Chunk.NULL_BLOCK_STORAGE || array[y].isEmpty();
    }

    @Override
    protected @Nullable ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) {
        ChunkRenderContext context = WorldSlice.prepare(this.world, new SectionPos(render.getChunkX(), render.getChunkY(), render.getChunkZ()), this.sectionCache);

        if (context == null) {
            return null;
        }

        return new ChunkBuilderMeshingTask(render, context, frame, this.cameraPosition);
    }

    @Override
    protected boolean allowImportantRebuilds() {
        return !CeleritasVintage.options().performance.alwaysDeferChunkUpdates;
    }

    @Override
    protected void scheduleSectionForRebuild(int x, int y, int z, boolean important) {
        this.sectionCache.invalidate(x, y, z);
        super.scheduleSectionForRebuild(x, y, z, important);
    }

    @Override
    public void updateChunks(boolean updateImmediately) {
        this.sectionCache.cleanup();
        super.updateChunks(updateImmediately);
    }

    /**
     * This ridiculous workaround is needed because some mods rely on side effects of calling getRenderBoundingBox
     * to initialize tile entity state. It can be removed if we start using getRenderBoundingBox for TE rendering
     * again.
     */
    @SuppressWarnings("unchecked")
    private static void retrieveBBForList(List<?> blockEntities) {
        if (!blockEntities.isEmpty()) {
            for (var be : (List<TileEntity>)blockEntities) {
                be.getRenderBoundingBox();
            }
        }
    }

    @Override
    protected boolean updateSectionInfo(RenderSection render, @Nullable BuiltRenderSectionData info) {
        if (info instanceof MinecraftBuiltRenderSectionData<?,?> mcData) {
            retrieveBBForList(mcData.culledBlockEntities);
            retrieveBBForList(mcData.globalBlockEntities);
        }
        return super.updateSectionInfo(render, info);
    }

    @Override
    protected @Nullable SectionTicker createSectionTicker() {
        return new GenericSectionSpriteTicker<>(SpriteUtil::markSpriteActive);
    }

    private static class ChunkRenderer extends DefaultChunkRenderer {

        public ChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
            super(device, renderPassConfiguration, GLStateManagerFogService.INSTANCE);
        }

        @Override
        public boolean useBlockFaceCulling(){
            return CeleritasVintage.options().performance.useBlockFaceCulling;
        }

        @Override
        protected void configureShaderInterface(ChunkShaderInterface shader) {
            shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
            shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 1);
        }
    }
}
