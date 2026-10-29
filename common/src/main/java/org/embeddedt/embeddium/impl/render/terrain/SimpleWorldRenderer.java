package org.embeddedt.embeddium.impl.render.terrain;

import lombok.Getter;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.SortedRenderLists;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Provides an extension to a game's regular world renderer.
 */
public abstract class SimpleWorldRenderer<WORLD, SECTIONMANAGER extends RenderSectionManager, LAYER, BLOCKENTITY, BLOCKENTITY_RENDER_CONTEXT> {
    protected WORLD world;
    protected int renderDistance;

    public record CameraState(double x, double y, double z, double pitch, double yaw, float fogDistance) {}

    protected CameraState lastCameraState;

    protected Viewport currentViewport;

    @Getter
    protected SECTIONMANAGER renderSectionManager;

    public void setWorld(WORLD world) {
        // Temporary probe (2026-10-15): the client stalls inside Minecraft.loadWorld, after the world field is
        // assigned and before the player is created, and this is the project's own entry into that window. Entry and
        // exit lines say whether this method is left at all.
        System.out.println("[Celtium] CELTIUM_WORLD_LOAD setWorld enter world=" + (world != null));
        // Check that the world is actually changing
        if (this.world == world) {
            System.out.println("[Celtium] CELTIUM_WORLD_LOAD setWorld leave (same world)");
            return;
        }

        // If we have a world is already loaded, unload the renderer
        if (this.world != null) {
            this.unloadWorld();
        }

        // If we're loading a new world, load the renderer
        if (world != null) {
            this.loadWorld(world);
        }
    }

    protected void loadWorld(WORLD world) {
        this.world = world;

        System.out.println("[Celtium] CELTIUM_WORLD_LOAD loadWorld enter, creating a command list");
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            System.out.println("[Celtium] CELTIUM_WORLD_LOAD command list created, initialising the renderer");
            this.initRenderer(commandList);
            System.out.println("[Celtium] CELTIUM_WORLD_LOAD renderer initialised");
        }
        System.out.println("[Celtium] CELTIUM_WORLD_LOAD loadWorld leave");
    }

    protected void unloadWorld() {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.world = null;
    }

    /**
     * @return The number of chunk renders which are visible in the current camera's frustum
     */
    public int getVisibleChunkCount() {
        // Measured 2026-10-15 (leg gv-cohort-fix141): the debug HUD asks this on the frame a world arrives, before the
        // renderer has one, and the null manager took the client down -
        // NullPointerException in getVisibleChunkCount from RenderGlobal.func_184382_g through
        // EntityRenderer.func_184373_n. It is a read-only query, and with no manager nothing is visible, so zero is
        // the honest answer; the rendering-path methods below are deliberately left unguarded, because a null manager
        // there is a real fault rather than a question asked too early.
        return this.renderSectionManager != null ? this.renderSectionManager.getVisibleChunkCount() : 0;
    }

    /**
     * @return The active terrain backend and the reason it was selected, or {@code null} before terrain is initialized
     */
    public @Nullable String getTerrainBackendDescription() {
        return this.renderSectionManager != null ? this.renderSectionManager.getTerrainBackendDescription() : null;
    }

    /**
     * Notifies the chunk renderer that the graph scene has changed and should be re-computed.
     */
    public void scheduleTerrainUpdate() {
        // BUG: seems to be called before init
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    /**
     * @return True if no chunks are pending rebuilds
     */
    public boolean isTerrainRenderComplete() {
        return this.renderSectionManager.getBuilder().isBuildQueueEmpty();
    }

    public abstract int getEffectiveRenderDistance();

    /**
     * Whether terrain is currently being set up or rendered for a shadow pass.
     */
    public boolean isInShadowPass() {
        return this.renderSectionManager.isInShadowPass();
    }

    /**
     * Called prior to any chunk rendering in order to update necessary state.
     */
    public void setupTerrain(Viewport viewport,
                             CameraState cameraState,
                             @Deprecated(forRemoval = true) int frame,
                             boolean spectator,
                             boolean updateChunksImmediately) {
        this.celtium$scheduledSections.clear();
        TerrainPhaseTiming.begin("stReclaim");
        NativeBuffer.reclaim(false);
        TerrainPhaseTiming.end("stReclaim");

        // When a shadow pass preceded this one, it joined the previous frame's searches, applied the chunk events
        // and submitted this frame's searches; joining again here would stall on those, and chunk events must
        // be applied while no search is in flight. The two steps are timed apart because they have different causes:
        // a join waits on the search, an event apply walks the tracker.
        TerrainPhaseTiming.begin("stGraph");
        boolean shadowPassRan = this.renderSectionManager.didShadowPassRunThisFrame();
        if (!shadowPassRan) {
            this.renderSectionManager.finishAllGraphUpdates();
        }
        TerrainPhaseTiming.end("stGraph");
        TerrainPhaseTiming.begin("stEvents");
        if (!shadowPassRan) {
            this.processChunkEvents();
        }
        TerrainPhaseTiming.end("stEvents");

        TerrainPhaseTiming.begin("stAsync");
        this.renderSectionManager.runAsyncTasks();

        if (getEffectiveRenderDistance() != this.renderDistance) {
            this.reload();
        }
        TerrainPhaseTiming.end("stAsync");

        TerrainPhaseTiming.begin("stPrepare");
        this.prepareFrame(viewport, cameraState, updateChunksImmediately);
        TerrainPhaseTiming.end("stPrepare");

        // After a shadow pass the uploads already happened there; uploading here would join the searches it submitted,
        // which is only worth it for rebuilds the render thread just waited on.
        TerrainPhaseTiming.begin("stUpload");
        if (!this.renderSectionManager.didShadowPassRunThisFrame() || this.renderSectionManager.hasBlockingResultsPending()) {
            this.renderSectionManager.uploadChunks();
        }
        TerrainPhaseTiming.end("stUpload");

        TerrainPhaseTiming.begin("stUpdate");
        if (this.renderSectionManager.needsUpdate()) {
            this.renderSectionManager.update(viewport, frame, spectator);
        }

        if (updateChunksImmediately) {
            this.renderSectionManager.uploadChunks();
        }
        TerrainPhaseTiming.end("stUpdate");

        TerrainPhaseTiming.begin("stVisible");
        this.renderSectionManager.tickVisibleRenders();
        TerrainPhaseTiming.end("stVisible");
    }

    /**
     * Shadow-pass counterpart of {@link #setupTerrain}. The shadow pass precedes the terrain pass in a frame, so
     * this joins the previous frame's searches, applies chunk load/unload events while the lattice is quiescent,
     * and runs the terrain search for {@code playerViewport} when one is due; the terrain pass then reuses it.
     * Build results are uploaded here, while no search is in flight; render-distance reloads are left to the terrain
     * pass.
     *
     * @param playerViewport the player camera's viewport for this frame
     * @param shadowViewport the shadow frustum, centred on the player camera
     */
    public void setupShadowTerrain(Viewport playerViewport,
                                   Viewport shadowViewport,
                                   CameraState cameraState,
                                   @Deprecated(forRemoval = true) int frame,
                                   boolean spectator) {
        NativeBuffer.reclaim(false);

        this.renderSectionManager.finishAllGraphUpdates();

        this.processChunkEvents();

        this.prepareFrame(shadowViewport, cameraState, false);

        this.renderSectionManager.uploadChunks();

        this.renderSectionManager.updateForShadowPass(playerViewport, shadowViewport, frame, spectator);

        this.renderSectionManager.tickVisibleRenders();
    }

    // Steps common to both passes: camera-change detection, async task draining and chunk build scheduling.
    private void prepareFrame(Viewport viewport, CameraState cameraState, boolean updateChunksImmediately) {
        boolean dirty = this.lastCameraState == null || !this.lastCameraState.equals(cameraState)
                || !this.renderSectionManager.matchesPublishedViewport(viewport);

        if (dirty) {
            this.renderSectionManager.markGraphDirty();
            this.lastCameraState = cameraState;
        }

        this.currentViewport = viewport;

        this.renderSectionManager.runAsyncTasks();

        this.renderSectionManager.updateChunks(updateChunksImmediately);
    }

    protected void processChunkEvents() {
        var tracker = ChunkTrackerHolder.get(this.world);
        tracker.forEachEvent(this.renderSectionManager::onChunkAdded, this.renderSectionManager::onChunkRemoved);
    }

    protected abstract ChunkRenderMatrices createChunkRenderMatrices();

    public Viewport getLastViewport() {
        return this.currentViewport;
    }

    /**
     * Performs a render pass for the given {@link LAYER} and draws all visible chunks for it.
     */
    public void drawChunkLayer(LAYER renderLayer, double x, double y, double z) {
        ChunkRenderMatrices matrices = createChunkRenderMatrices();

        Collection<TerrainRenderPass> passes = this.renderSectionManager.getRenderPassConfiguration().vanillaRenderStages().get(renderLayer);

        if (passes != null && !passes.isEmpty()) {
            var occlusionCamera = this.getLastViewport().getTransform();
            var realCamera = new CameraTransform(x, y, z);
            for (var pass : passes) {
                this.renderSectionManager.renderLayer(matrices, pass, occlusionCamera, realCamera);
            }
        }
    }

    public void reload() {
        if (this.world == null) {
            return;
        }

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    protected abstract SECTIONMANAGER createRenderSectionManager(CommandList commandList);

    protected void initRenderer(CommandList commandList) {
        // Temporary probe (2026-10-15): leg gv-cohort-fix149 reached "command list created, initialising the
        // renderer" and never printed "renderer initialised", so the stall is inside this method. One line per call
        // says which one, and the constructor's own suspects - the chunk builder's worker threads and
        // TerrainBackendLoader.createVulkan - are on the other side of the middle line.
        System.out.println("[Celtium] CELTIUM_WORLD_LOAD initRenderer enter, old manager="
                + (this.renderSectionManager != null));
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }
        System.out.println("[Celtium] CELTIUM_WORLD_LOAD old manager destroyed");

        this.renderDistance = getEffectiveRenderDistance();
        System.out.println("[Celtium] CELTIUM_WORLD_LOAD creating the section manager (chunk builder + Vulkan backend)");

        this.renderSectionManager = this.createRenderSectionManager(commandList);
        System.out.println("[Celtium] CELTIUM_WORLD_LOAD section manager created");

        var tracker = ChunkTrackerHolder.get(this.world);
        // Measured 2026-10-15 (the cohort on merged source): the client reaches the world, the renderer initialises,
        // and it reports sections=0 - so the terrain pass has nothing to draw, nothing is submitted, and the present
        // has no picture. The tracker is where sections come from, so its counts at this moment separate "the chunks
        // never arrived" from "they arrived and were not accepted".
        System.out.println("[Celtium] CELTIUM_WORLD_LOAD tracker known=" + tracker.knownChunkCount()
                + " ready=" + tracker.readyChunkCount());
        // Re-evaluate readiness before seeding: a world change moves the player to chunks the tracker already knows
        // but has not accepted, and without this the new manager is seeded with whatever was ready for the old
        // position. See ChunkTracker.refreshReadyChunks and Sodium's issue 3497.
        tracker.refreshReadyChunks();
        ChunkTracker.forEachChunk(tracker.getReadyChunks(), this.renderSectionManager::onChunkAdded);
        System.out.println("[Celtium] CELTIUM_WORLD_LOAD initRenderer leave, sections="
                + this.renderSectionManager.getTotalSections());
    }

    /**
     * {@return an iterator over all visible block entities}
     */
    public Iterator<BLOCKENTITY> blockEntityIterator() {
        return MinecraftBuiltRenderSectionData.generateBlockEntityIterator(this.renderSectionManager.getRenderLists(), this.renderSectionManager.getSectionsWithGlobalEntities());
    }

    public void forEachVisibleBlockEntity(Consumer<BLOCKENTITY> consumer) {
        MinecraftBuiltRenderSectionData.forEachBlockEntity(consumer, this.renderSectionManager.getRenderLists(), this.renderSectionManager.getSectionsWithGlobalEntities());
    }

    protected abstract void renderBlockEntityList(List<BLOCKENTITY> list, BLOCKENTITY_RENDER_CONTEXT context);

    protected int renderSectionBlockEntities(RenderSection section, List<BLOCKENTITY> blockEntities, boolean culled, BLOCKENTITY_RENDER_CONTEXT renderContext) {
        this.renderBlockEntityList(blockEntities, renderContext);
        return blockEntities.size();
    }

    private int renderCulledBlockEntities(BLOCKENTITY_RENDER_CONTEXT renderContext) {
        int count = 0;
        SortedRenderLists renderLists = this.renderSectionManager.getRenderLists();
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            var renderList = renderListIterator.next();

            var renderRegion = renderList.getRegion();
            var renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                var renderSectionId = renderSectionIterator.nextByteAsInt();
                var renderSection = renderRegion.getSection(renderSectionId);

                if (renderSection == null) {
                    continue;
                }

                var context = renderSection.getBuiltContext();

                if (!(context instanceof MinecraftBuiltRenderSectionData mcData)) {
                    continue;
                }

                List<BLOCKENTITY> blockEntities = mcData.culledBlockEntities;

                if (blockEntities.isEmpty()) {
                    continue;
                }

                count += this.renderSectionBlockEntities(renderSection, blockEntities, true, renderContext);
            }
        }

        return count;
    }

    private int renderGlobalBlockEntities(BLOCKENTITY_RENDER_CONTEXT renderContext) {
        int count = 0;
        for (var renderSection : this.renderSectionManager.getSectionsWithGlobalEntities()) {
            var context = renderSection.getBuiltContext();

            if (!(context instanceof MinecraftBuiltRenderSectionData mcData)) {
                continue;
            }

            List<BLOCKENTITY> blockEntities = mcData.globalBlockEntities;

            if (blockEntities.isEmpty()) {
                continue;
            }

            count += this.renderSectionBlockEntities(renderSection, blockEntities, false, renderContext);
        }

        return count;
    }

    public int renderBlockEntities(BLOCKENTITY_RENDER_CONTEXT renderContext) {
        int count = 0;
        count += this.renderCulledBlockEntities(renderContext);
        count += this.renderGlobalBlockEntities(renderContext);
        return count;
    }

    // the volume of a section multiplied by the number of sections to be checked at most
    public static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;

    public abstract int getMinimumBuildHeight();
    public abstract int getMaximumBuildHeight();

    public boolean isPointVisible(double x, double y, double z) {
        if (y < getMinimumBuildHeight() + 0.5D || y > getMaximumBuildHeight() - 0.5D) {
            return true;
        }

        return this.renderSectionManager.isSectionVisible(
                PositionUtil.posToSectionCoord(x),
                PositionUtil.posToSectionCoord(y),
                PositionUtil.posToSectionCoord(z)
        );
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Boxes outside the valid world height will never map to a rendered chunk
        // Always render these boxes or they'll be culled incorrectly!
        if (y2 < getMinimumBuildHeight() + 0.5D || y1 > getMaximumBuildHeight() - 0.5D) {
            return true;
        }

        double entityVolume = (x2 - x1) * (y2 - y1) * (z2 - z1);
        if (entityVolume > MAX_ENTITY_CHECK_VOLUME) {
            return true;
        }

        int minX = PositionUtil.posToSectionCoord(x1 - 0.5D);
        int minY = PositionUtil.posToSectionCoord(y1 - 0.5D);
        int minZ = PositionUtil.posToSectionCoord(z1 - 0.5D);

        int maxX = PositionUtil.posToSectionCoord(x2 + 0.5D);
        int maxY = PositionUtil.posToSectionCoord(y2 + 0.5D);
        int maxZ = PositionUtil.posToSectionCoord(z2 + 0.5D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.renderSectionManager.isSectionVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getChunksDebugString() {
        // C: visible/total D: distance
        // Same window as getVisibleChunkCount above: the HUD's own string is asked on the same frames.
        if (this.renderSectionManager == null) {
            return null;
        }
        return String.format("C: %d/%d D: %d %s", this.renderSectionManager.getVisibleChunkCount(),
                this.renderSectionManager.getTotalSections(), this.renderDistance,
                this.renderSectionManager.getTickerDebugString());
    }

    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderSectionManager.getRenderPassConfiguration();
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified block region.
     */
    private static int celtium$blockAreaStacks;

    /**
     * The build-flow diagnostics switch, read once.
     * <p>
     * It used to be a {@code Boolean.getBoolean} inside the per-call check of
     * {@link #scheduleRebuildForBlockArea} - and that method is called ~8.6 thousand times per frame while vanilla
     * drains its light-update queue, so the property lookup alone was a measurable part of the storm's cost.
     */
    private static final boolean BUILD_FLOW_LOG = Boolean.getBoolean("celtium.debug.buildFlow");

    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        // Who calls this twelve thousand times a frame in a still scene? The stack names the caller instead of the
        // next round guessing between block updates, light updates and a renderer loop.
        // Classified sampling, not per-call stacks: one stack capture per 2000 calls is free and still says which
        // vanilla path owns the storm (light updates, block placement, chunk data, chunk light init).
        long areaCalls = ++org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.areaCalls;
        if (BUILD_FLOW_LOG && areaCalls % 2000 == 0) {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            boolean light = false, setBlock = false, chunkData = false, chunkLightInit = false;
            for (int i = 2; i < stack.length; i++) {
                String method = stack[i].getMethodName();
                if (method.equals("func_72975_g") || method.equals("checkLightFor") || method.equals("func_147458_c")
                        || method.equals("markBlockRangeForRenderUpdate")) {
                    light = true;
                } else if (method.equals("func_180501_a") || method.equals("setBlockState")
                        || method.equals("markAndNotifyBlock")) {
                    setBlock = true;
                } else if (method.equals("func_147263_a") || method.equals("handleChunkData")) {
                    chunkData = true;
                } else if (method.equals("func_76615_h") || method.equals("generateSkylightMap")) {
                    chunkLightInit = true;
                }
            }
            if (chunkData) {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.areaFromChunkData++;
            } else if (chunkLightInit) {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.areaFromChunkLightInit++;
            } else if (setBlock) {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.areaFromSetBlock++;
            } else if (light) {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.areaFromLight++;
            } else {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.areaFromOther++;
                if (org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.areaFromOther <= 3) {
                    StringBuilder line = new StringBuilder("[Celtium] CELTIUM_BLOCK_AREA other");
                    for (int i = 2; i < Math.min(stack.length, 9); i++) {
                        line.append(" <- ").append(stack[i].getClassName()).append('.').append(stack[i].getMethodName());
                    }
                    System.out.println(line);
                }
            }
        }
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified chunk region.
     */
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    /**
     * Schedules a chunk rebuild for the render belonging to the given chunk section position.
     */
    /**
     * Sections already scheduled for a rebuild this frame.
     * <p>
     * Vanilla's per-frame {@code RenderGlobal.updateClouds()} drains its light-update set and asks for a rebuild around
     * every position, one {@code markBlocksForUpdate(pos-1 .. pos+1)} each. A dense light-update storm therefore sends
     * the same sections over and over within one frame - measured 8.6 thousand area calls and 12 thousand section
     * schedules per frame in a still scene. A section already scheduled only needs the one rebuild (the rebuild reads
     * the world as it is when the task runs), so repeated requests inside the same frame are dropped; the
     * pending-update merge in {@code scheduleRebuild} would have collapsed them anyway, this skips the work.
     */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet celtium$scheduledSections =
            new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        // Only the unimportant requests are collapsed: those are the light-update storm's shape (a position that had a
        // light change, marked so the section is re-meshed). An important request - a block placement or break, which
        // vanilla and Forge mark important - always goes through, so a real edit is never merged away by an earlier
        // light request in the same frame.
        if (!important) {
            long key = ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF);
            if (!this.celtium$scheduledSections.add(key)) {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.scheduleDeduped++;
                return;
            }
        }
        this.renderSectionManager.scheduleRebuild(x, y, z, important);
    }

    public Collection<String> getDebugStrings() {
        var debugStrings = new ArrayList<String>();
        if (this.currentViewport != null) {
            var transform = this.currentViewport.getTransform();
            debugStrings.add("Viewport: %.02f %.02f %.02f".formatted(transform.x, transform.y, transform.z));
        }
        if (this.renderSectionManager != null) {
            debugStrings.addAll(this.renderSectionManager.getDebugStrings());
        }
        return debugStrings;
    }

    public boolean isSectionReady(int x, int y, int z) {
        return this.renderSectionManager.isSectionBuilt(x, y, z);
    }

    /**
     * This interface should be implemented on the WorldRenderer or equivalent class.
     */
    public interface Provider<T extends SimpleWorldRenderer<?, ?, ?, ?, ?>> {
        T celeritas$getWorldRenderer();

        @SuppressWarnings("unchecked")
        static <T extends SimpleWorldRenderer<?, ?, ?, ?, ?>> @Nullable T getWorldRendererNullable(Object o) {
            return ((Provider<T>)o).celeritas$getWorldRenderer();
        }

        static <T extends SimpleWorldRenderer<?, ?, ?, ?, ?>> @NotNull T getWorldRenderer(Object o) {
            T result = getWorldRendererNullable(o);
            if (result == null) {
                throw new IllegalStateException("No renderer attached to active world");
            }
            return result;
        }
    }
}
