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
import org.embeddedt.embeddium.impl.render.chunk.shader.TerrainPipelineConfig;
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
import org.taumc.celeritas.impl.world.PrepareNullCensus;
import org.taumc.celeritas.impl.world.WorldSlice;
import org.taumc.celeritas.impl.world.cloned.ChunkRenderContext;
import org.taumc.celeritas.impl.world.cloned.ClonedChunkSectionCache;

import java.util.List;

public class VintageRenderSectionManager extends RenderSectionManager {
    private final WorldClient world;
    @Getter
    private final ClonedChunkSectionCache sectionCache;

    public VintageRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world, int renderDistance, CommandList commandList, int minSection, int maxSection) {
        this(configuration, world, renderDistance, commandList, minSection, maxSection, resolveShadowPassEnabled());
    }

    public VintageRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world, int renderDistance, CommandList commandList, int minSection, int maxSection, boolean hasShadowPass) {
        super(configuration, () -> new VintageChunkBuildContext(world, configuration), CeltiumTerrainChunkRenderer::new, renderDistance, commandList, minSection, maxSection, CeleritasVintage.options().performance.chunkBuilderThreads, hasShadowPass);
        this.world = world;
        this.sectionCache = new ClonedChunkSectionCache(world);
        if (celtium$shadowPassReports < 8) {
            celtium$shadowPassReports++;
            // The construction-time value and its two sources, so a fixture leg can tell "the gate is still shut"
            // from "the gate opened" at the first manager a run builds, without waiting for a shadow frame
            // (item 409 diagnosis, leg requirements: the failure this fixes was invisible to every frame census
            // because it happened here, at object construction).
            System.out.println("[Celtium] CELTIUM_SECTION_MANAGER hasShadowPass=" + hasShadowPass
                    + " requested=" + TerrainPipelineConfig.isShadowPassRequested()
                    + " shadersEnabled=" + org.embeddedt.embeddium.impl.render.ShaderModBridge.areShadersEnabled());
        }
    }

    /** Bounded report counter for the construction-time shadow decision. */
    private static int celtium$shadowPassReports;

    /**
     * {@return whether a section manager built now gets a shadow terrain pass}: the core pipeline config's effective
     * decision (explicit request, or a shader pack active through the shader bridge). See
     * {@link TerrainPipelineConfig#isShadowPassEnabled()} for why the bridge, not the Phase 0 program provider, is
     * the runtime source.
     */
    public static boolean resolveShadowPassEnabled() {
        return TerrainPipelineConfig.isShadowPassEnabled();
    }

    public static VintageRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList) {
        return create(vertexType, world, renderDistance, commandList, resolveShadowPassEnabled());
    }

    public static VintageRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList, boolean hasShadowPass) {
        return new VintageRenderSectionManager(VintageRenderPassConfigurationBuilder.build(vertexType), world, renderDistance, commandList, 0, 16, hasShadowPass);
    }

    /** Read once: the section manager is asked for this mode on start-up paths, never with a changing property. */
    private static final String FORCED_ASYNC_OCCLUSION = System.getProperty("celtium.terrain.asyncOcclusion");

    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        // -Dceltium.terrain.asyncOcclusion=NONE|ONLY_SHADOW|EVERYTHING overrides the options file, so a leg can A/B the
        // search thread against the same copied instance.
        String forced = FORCED_ASYNC_OCCLUSION;
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
        // The option is the only input this seam has: the graph, lattice and culler all take
        // the flag from here, and the raster occluder is only created when it is set. Returning
        // a constant false (the shape this override had since the feature landed) left the GUI
        // toggle bound and saved but with no effect. The field defaults to false, so reading it
        // changes nothing unless the user opted in.
        return CeleritasVintage.options().performance.useRasterOcclusionCulling;
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
    protected void onSectionAddedAsEmpty(RenderSection render) {
        // The add-time EMPTY commit is where most terminal-built sections are actually born, and
        // it never reaches WorldSlice.prepare, so the prepare-null census (item 701) counted
        // nothing for it and a leg could not split the population. Classify it here with the same
        // inputs prepare would have used, so the census sees both formation paths in one set of
        // reason buckets (the add-path share is the line's addEmpty field).
        if (!PrepareNullCensus.enabled()) {
            return;
        }

        Chunk chunk = this.world.getChunk(render.getChunkX(), render.getChunkZ());
        var storage = chunk.getBlockStorageArray();
        int y = render.getChunkY();
        var section = y >= 0 && y < storage.length ? storage[y] : null;

        PrepareNullCensus.Reason reason = PrepareNullCensus.classifyForNullBranch(
                chunk.isEmpty(), storage.length, y,
                section == null, section != null && section.isEmpty());
        PrepareNullCensus.GLOBAL.recordAddEmpty(reason);

        if (reason == PrepareNullCensus.Reason.SLOT_ABSENT
                || reason == PrepareNullCensus.Reason.SLOT_EMPTY) {
            // The discrimination this census cannot make today (item 683, leg-3): a slot that
            // is absent/empty at add time is classified legitimateEmpty, but the commit is
            // final - onSectionAdded is a no-op for an existing section and the only
            // re-prepare trigger is a per-block markBlockRangeForRenderUpdate. Register the
            // section so the sweep in updateChunks can count a later bulk arrival of geometry
            // into this slot (chunk-data re-fill allocates storage without per-block marks).
            // Interior = the section top is below the column height value at the centre
            // column: geometry already exists above it, so "air above the terrain" cannot
            // explain this section later gaining blocks.
            int topY = y * 16 + 15;
            int height = chunk.getHeightValue(8, 8);
            PrepareNullCensus.GLOBAL.registerAddEmpty(
                    PrepareNullCensus.packSectionKey(render.getChunkX(), y, render.getChunkZ()),
                    topY < height);
        }
    }

    @Override
    protected boolean isSectionAddedEmptyFinal(RenderSection render) {
        // isSectionVisuallyEmpty returns true both for a section that is genuinely empty (a
        // received chunk whose storage slot is absent or empty, or a Y outside the storage
        // array) and for a chunk the client has not received yet, where ChunkProviderClient
        // answers with its blank EmptyChunk. Only the first is final: committing the second as
        // EMPTY at add time guesses at geometry that has not arrived, and the section is never
        // re-added, so the guess is permanent (item 683). Chunk.isEmpty is true only for the
        // blank placeholder, never for a received chunk (the same distinction
        // isSectionVisuallyEmpty relies on). The emptiness re-check closes the arrival race:
        // if the chunk landed between the caller's verdict and this one and the slot now has
        // blocks, the verdict is not final either and the section goes to initial build.
        Chunk chunk = this.world.getChunk(render.getChunkX(), render.getChunkZ());

        if (chunk.isEmpty()) {
            return false;
        }

        return this.isSectionVisuallyEmpty(render.getChunkX(), render.getChunkY(), render.getChunkZ());
    }

    @Override
    protected boolean isRebuildDataFinal(RenderSection render) {
        // WorldSlice.prepare returns null both for a section that is genuinely empty (a received
        // chunk whose storage slot is absent or empty) and for a chunk the client has not received
        // yet, where ChunkProviderClient answers with its blank EmptyChunk. Only the first is
        // final: committing the second as EMPTY would guess at geometry that has not arrived.
        // Chunk.isEmpty is true only for the blank placeholder, never for a received chunk (the
        // same distinction isSectionVisuallyEmpty relies on).
        return !this.world.getChunk(render.getChunkX(), render.getChunkZ()).isEmpty();
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

    /** Sweep throttle: one bounded slice every 300 updateChunks calls. */
    private int celtium$lateArrivalSweepCalls;

    @Override
    public void updateChunks(boolean updateImmediately) {
        this.sectionCache.cleanup();
        super.updateChunks(updateImmediately);

        if (PrepareNullCensus.enabled()
                && PrepareNullCensus.GLOBAL.trackedEmptyCount() > 0
                && ++this.celtium$lateArrivalSweepCalls >= 300) {
            this.celtium$lateArrivalSweepCalls = 0;
            // 512 keys per sweep: at the leg-3 population (1700 registered) the whole set is
            // re-read roughly every 1200 frames, and a single sweep is a few hundred map/slot
            // reads on the render thread - desk estimate < 0.05 ms, and only while the census
            // property is set.
            PrepareNullCensus.GLOBAL.sweepAddEmpty(512, key -> {
                int sx = PrepareNullCensus.unpackSectionX(key);
                int sy = PrepareNullCensus.unpackSectionY(key);
                int sz = PrepareNullCensus.unpackSectionZ(key);
                Chunk chunk = this.world.getChunk(sx, sz);
                if (chunk.isEmpty()) {
                    return 2; // no longer loaded: lifecycle over, drop silently
                }
                var array = chunk.getBlockStorageArray();
                if (sy < 0 || sy >= array.length) {
                    return 0;
                }
                var slot = array[sy];
                return (slot != Chunk.NULL_BLOCK_STORAGE && !slot.isEmpty()) ? 1 : 0;
            });
        }
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

    /**
     * Celtium-native terrain service carrier implementing {@link org.embeddedt.embeddium.impl.render.chunk.ChunkRenderer}
     * directly without inheriting Celeritas's OpenGL {@code DefaultChunkRenderer}/{@code ShaderChunkRenderer}
     * (no GL shader compilation, no {@code SharedQuadIndexBuffer}, no {@code GlTessellation}).
     */
    private static final class CeltiumTerrainChunkRenderer implements org.embeddedt.embeddium.impl.render.chunk.ChunkRenderer {
        private final RenderPassConfiguration<?> renderPassConfiguration;

        CeltiumTerrainChunkRenderer(RenderDevice ignoredDevice, RenderPassConfiguration<?> renderPassConfiguration) {
            this.renderPassConfiguration = renderPassConfiguration;
        }

        @Override
        public void render(ChunkRenderMatrices matrices, CommandList commandList,
                           org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable renderLists,
                           org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass pass,
                           org.embeddedt.embeddium.impl.render.viewport.CameraTransform occlusionCamera,
                           org.embeddedt.embeddium.impl.render.viewport.CameraTransform camera) {
            throw new UnsupportedOperationException("Celtium renders terrain exclusively through VulkanTerrainGpuBackend");
        }

        @Override
        public void delete(CommandList commandList) {
            // No GL resources owned.
        }

        @Override
        public RenderPassConfiguration<?> getRenderPassConfiguration() {
            return this.renderPassConfiguration;
        }

        @Override
        public FogService getFogService() {
            return GLStateManagerFogService.INSTANCE;
        }

        @Override
        public org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService getDynamicLightService() {
            return org.taumc.celeritas.impl.render.terrain.light.MinecraftDynamicLightService.INSTANCE;
        }

        @Override
        public boolean useBlockFaceCulling() {
            return CeleritasVintage.options().performance.useBlockFaceCulling;
        }
    }
}
