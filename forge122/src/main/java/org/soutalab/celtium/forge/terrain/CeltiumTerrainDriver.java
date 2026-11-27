package org.soutalab.celtium.forge.terrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.soutalab.celtium.vulkan.CeltiumBackend;
import org.soutalab.celtium.vulkan.CeltiumBackends;
import org.soutalab.celtium.vulkan.terrain.lane1.SectionGeometry;
import org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor;
import org.taumc.celeritas.mixin.core.terrain.ChunkProviderClientAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The Lane 1 terrain driver (S2-b): celtium's own replacement for the retired Celeritas world
 * renderer at the {@code RenderGlobal} seats. It polls the client's loaded-chunk set each frame
 * (no chunk tracker), snapshots changed sections on the render thread, meshes them on the S2-a
 * build scheduler, and uploads the finished geometry to the backend's Lane 1 store; the layer
 * draws themselves are one backend call per vanilla layer.
 * <p>
 * Residency is diffed, not event-driven: a section exists when its chunk is loaded and its
 * storage is non-empty. Block edits arrive through {@link #scheduleRebuildForBlockArea} (the
 * {@code markBlocksForUpdate} seat); everything else - chunk load, chunk unload, world change -
 * falls out of the per-frame diff.
 */
public final class CeltiumTerrainDriver {

    /** Builds allowed in flight at once; the initial load streams in instead of hitching. */
    private static final int MAX_PENDING_BUILDS = 96;
    /**
     * Snapshots taken per frame at most. The snapshot is render-thread work (a direct
     * chunk-storage read since the Gate G1 fix, ~0.3-0.5 ms per section), and filling the
     * whole pending cap every frame made tick frames cost ~46 ms in the flight scene
     * while the backlog lasted. The flight's steady-state demand is ~3 sections/frame;
     * a budget of 12 drains a load backlog over a second or two without hitching frames.
     */
    private static final int MAX_SNAPSHOTS_PER_FRAME = 12;
    /** Finished builds uploaded per frame at most. */
    private static final int MAX_UPLOADS_PER_FRAME = 160;

    /** The live driver, for the F3 panel and the validation fixture's renderer evidence reads. */
    private static volatile CeltiumTerrainDriver instance;

    private WorldClient world;
    private TerrainBuildScheduler scheduler;
    private ForgeBlockFaceData faceData;
    private AtlasSpriteIndex spriteIndex;
    private int pushedTextureCellCount = -1;
    private FluidloggedProbe fluids;

    /** Section keys (packed) whose geometry is in the backend store. */
    private final Set<Long> resident = new HashSet<>();
    /** Section keys the world currently has, recomputed by the per-frame diff. */
    private final Set<Long> present = new HashSet<>();
    /** Section keys waiting for a (re)build. */
    private final Set<Long> dirty = new HashSet<>();
    /** Sections built while their chunk's light was not yet populated; rebuilt once it is. */
    private final Set<Long> awaitingLight = new HashSet<>();
    // TEMP G1 PROBE6 counters.
    static final java.util.concurrent.atomic.AtomicLong DRAIN_AXIS_TOTAL =
            new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong DRAIN_AXIS_DARK =
            new java.util.concurrent.atomic.AtomicLong();
    private final Map<Long, CompletableFuture<MeshBuffers>> pending = new HashMap<>();

    private int visibleSectionCount;

    /** Sections whose upload the store refused, with the frame at which a retry is due. */
    private final Map<Long, Long> starvedUntil = new HashMap<>();
    /** setupTerrain calls so far; the refusal cooldown's clock. */
    private long frameCounter;

    public CeltiumTerrainDriver() {
        instance = this;
    }

    /** The live driver, or null before the world renderer exists. */
    public static CeltiumTerrainDriver instanceNullable() {
        return instance;
    }

    /** The live terrain backend description (same text as the /celtium report). */
    public String getTerrainBackendDescription() {
        return org.soutalab.celtium.forge.TerrainBackendStatus.describe();
    }

    /** Renderer evidence lines for the validation fixture and the F3 panel. */
    public java.util.List<String> getDebugStrings() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Celtium terrain driver (lane1): " + getTerrainBackendDescription());
        lines.add(getChunksDebugString());
        CeltiumBackend backend = CeltiumBackends.current();
        if (backend != null) {
            String diagnostics = backend.celtium$lane1DiagnosticsSummary();
            if (!diagnostics.isEmpty()) {
                lines.add(diagnostics);
            }
        }
        if (faceData != null) {
            // TEMP G1 PROBE: ground-truth face cells for known blocks.
            StringBuilder probe = new StringBuilder("cellsProbe atlasPx=" + atlasWidthPx());
            net.minecraft.block.Block[] probeBlocks = {
                    net.minecraft.init.Blocks.GRASS, net.minecraft.init.Blocks.DIRT,
                    net.minecraft.init.Blocks.STONE, net.minecraft.init.Blocks.LEAVES,
                    net.minecraft.init.Blocks.LOG, net.minecraft.init.Blocks.SAND};
            for (net.minecraft.block.Block b : probeBlocks) {
                net.minecraft.block.state.IBlockState st = b.getDefaultState();
                int sid = net.minecraft.block.Block.BLOCK_STATE_IDS.get(st);
                probe.append(' ').append(b.getRegistryName().getPath()).append("[id=").append(sid).append(']');
                for (int d = 0; d < 6; d++) {
                    int cell = faceData.faceCell(sid, d);
                    probe.append(' ').append(cell & 0xFF).append(',').append((cell >> 8) & 0xFF);
                }
            }
            lines.add(probe.toString());
        }
        if (faceData != null && world != null && Minecraft.getMinecraft().player != null) {
            // TEMP G1 PROBE2: fresh-snapshot light histogram for the player's section.
            try {
                net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().player;
                int sx = ((int) Math.floor(player.posX)) >> 4;
                int sz = ((int) Math.floor(player.posZ)) >> 4;
                int sy = ((int) Math.floor(player.posY)) >> 4;
                WorldSectionSnapshot snap = new WorldSectionSnapshot();
                // Descend to the first section that actually holds terrain.
                for (int probeY = sy; probeY >= 0; probeY--) {
                    snap = new WorldSectionSnapshot();
                    ForgeSectionScanner.fill(snap, world, faceData, fluids, sx, probeY, sz);
                    int solid = 0;
                    for (int i = 0; i < WorldSectionSnapshot.SIZE * WorldSectionSnapshot.SIZE
                            * WorldSectionSnapshot.SIZE; i++) {
                        if (snap.stateAt(i) != 0) {
                            solid++;
                        }
                    }
                    if (solid > 200) {
                        sy = probeY;
                        break;
                    }
                }
                int sky15 = 0, skyMid = 0, sky0 = 0, blockPos = 0, nonAir = 0;
                for (int y = 1; y <= WorldSectionSnapshot.INNER; y++) {
                    for (int z = 1; z <= WorldSectionSnapshot.INNER; z++) {
                        for (int x = 1; x <= WorldSectionSnapshot.INNER; x++) {
                            int idx = WorldSectionSnapshot.index(x, y, z);
                            if (snap.stateAt(idx) != 0) {
                                nonAir++;
                            }
                            int sky = snap.skyLightAt(idx);
                            if (sky >= 15) { sky15++; } else if (sky == 0) { sky0++; } else { skyMid++; }
                            if (snap.blockLightAt(idx) > 0) { blockPos++; }
                        }
                    }
                }
                // TEMP G1 PROBE5: raw skylight arrays, player chunk vs 8 chunks west.
                try {
                    net.minecraft.world.World w = (net.minecraft.world.World) world;
                    StringBuilder raw = new StringBuilder("rawLight");
                    for (int dx : new int[]{0, -8}) {
                        net.minecraft.world.chunk.Chunk c =
                                w.getChunk(sx + dx, sz);
                        raw.append(" chunk[").append(sx + dx).append(',').append(sz)
                                .append("] populated=").append(c.isLightPopulated())
                                .append(" empty=").append(c.isEmpty());
                        net.minecraft.world.chunk.storage.ExtendedBlockStorage[] arr =
                                c.getBlockStorageArray();
                        for (int yy = 4; yy <= 5; yy++) {
                            net.minecraft.world.chunk.storage.ExtendedBlockStorage st =
                                    yy < arr.length ? arr[yy] : null;
                            raw.append(" y").append(yy).append('=');
                            if (st == null) {
                                raw.append("null");
                            } else {
                                int sum = 0;
                                for (int cell = 0; cell < 4096; cell++) {
                                    sum += st.getSkyLight(cell & 15, (cell >> 8) & 15, (cell >> 4) & 15);
                                }
                                raw.append("skySum=").append(sum);
                            }
                        }
                    }
                    lines.add(raw.toString());
                } catch (Throwable t) {
                    lines.add("rawLight failed: " + t);
                }
                lines.add("lightProbe section=" + sx + "," + sy + "," + sz
                        + " sky15=" + sky15 + " skyMid=" + skyMid + " sky0=" + sky0
                        + " blockPos=" + blockPos + " nonAir=" + nonAir);
                // TEMP G1 PROBE3: mesh the same snapshot here and count the emitted light.
                MeshBuffers buffers = new MeshBuffers();
                new SectionMesher().mesh(snap, faceData, MesherOptions.PACK_OFF, buffers);
                StringBuilder meshProbe = new StringBuilder("meshProbe");
                for (int layer = 0; layer < 3; layer++) {
                    java.util.List<org.soutalab.celtium.core.geometry.PackedAxisQuad> quads =
                            buffers.axisLayerList(layer);
                    int qSky15 = 0, qSky0 = 0;
                    for (org.soutalab.celtium.core.geometry.PackedAxisQuad q : quads) {
                        if (q.skyLight() >= 15) { qSky15++; } else if (q.skyLight() == 0) { qSky0++; }
                    }
                    meshProbe.append(" L").append(layer).append(": n=").append(quads.size())
                            .append(" sky15=").append(qSky15).append(" sky0=").append(qSky0);
                    if (!quads.isEmpty()) {
                        org.soutalab.celtium.core.geometry.PackedAxisQuad q0 = quads.get(0);
                        meshProbe.append(" first=[cell ").append(q0.cellU()).append(',')
                                .append(q0.cellV()).append(" sky ").append(q0.skyLight())
                                .append(" block ").append(q0.blockLight()).append(']');
                    }
                }
                lines.add(meshProbe.toString());
            } catch (Throwable t) {
                lines.add("lightProbe failed: " + t);
            }
        }
        lines.add("drainCensus axisTotal=" + DRAIN_AXIS_TOTAL.get()
                + " axisDark=" + DRAIN_AXIS_DARK.get());
        lines.add("fillCensus total=" + ForgeSectionScanner.FILLS_TOTAL.get()
                + " centerUnpopulated=" + ForgeSectionScanner.FILLS_CENTER_UNPOPULATED.get());
        lines.add("driver resident=" + resident.size() + " present=" + present.size()
                + " pending=" + pending.size() + " dirty=" + dirty.size()
                + " visible=" + visibleSectionCount);
        return lines;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF);
    }

    private static int keyX(long key) {
        int v = (int) (key >> 42) & 0x3FFFFF;
        return v >= 0x200000 ? v - 0x400000 : v;
    }

    private static int keyZ(long key) {
        int v = (int) (key >> 20) & 0x3FFFFF;
        return v >= 0x200000 ? v - 0x400000 : v;
    }

    private static int keyY(long key) {
        return (int) (key & 0xFFFFF);
    }

    // ------------------------------------------------------------------
    // Lifecycle (RenderGlobalMixin seats)
    // ------------------------------------------------------------------

    public void setWorld(WorldClient world) {
        if (this.world == world) {
            return;
        }
        CeltiumBackend backend = CeltiumBackends.current();
        if (backend != null) {
            for (long k : resident) {
                backend.celtium$lane1RemoveSection(keyX(k), keyY(k), keyZ(k));
            }
        }
        resident.clear();
        present.clear();
        dirty.clear();
        for (CompletableFuture<MeshBuffers> future : pending.values()) {
            future.cancel(false);
        }
        pending.clear();
        this.world = world;
    }

    /** Resource reload: the atlas changed, so every resident section is stale. */
    public void reload() {
        faceData = null;
        dirty.addAll(resident);
    }

    public void scheduleTerrainUpdate() {
        dirty.addAll(resident);
    }

    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ,
                                            int maxX, int maxY, int maxZ, boolean important) {
        // The border sections are included: a face on the boundary can change either side.
        for (int x = (minX >> 4) - 1; x <= (maxX >> 4) + 1; x++) {
            for (int y = Math.max(0, (minY >> 4) - 1); y <= Math.min(15, (maxY >> 4) + 1); y++) {
                for (int z = (minZ >> 4) - 1; z <= (maxZ >> 4) + 1; z++) {
                    long k = key(x, y, z);
                    if (present.contains(k)) {
                        dirty.add(k);
                    }
                }
            }
        }
    }

    /**
     * The entity-culling seat: an entity is culled only when its section exists in the world
     * but has no geometry yet and none is coming - residency, not frustum visibility (the draw
     * stage frustum-culls terrain itself; an entity culled on a stale visibility set would pop).
     */
    public boolean isEntityVisible(net.minecraft.entity.Entity entity) {
        int sx = net.minecraft.util.math.MathHelper.floor(entity.posX) >> 4;
        int sy = net.minecraft.util.math.MathHelper.floor(entity.posY) >> 4;
        int sz = net.minecraft.util.math.MathHelper.floor(entity.posZ) >> 4;
        long k = key(sx, sy, sz);
        return resident.contains(k) || pending.containsKey(k) || dirty.contains(k)
                || !present.contains(k);
    }

    public int getVisibleChunkCount() {
        return visibleSectionCount;
    }

    public boolean isTerrainRenderComplete() {
        return pending.isEmpty() && dirty.isEmpty();
    }

    public String getChunksDebugString() {
        CeltiumBackend backend = CeltiumBackends.current();
        int sections = backend == null ? 0 : backend.celtium$lane1SectionCount();
        return "C: " + visibleSectionCount + "/" + sections
                + " (lane1 pending=" + pending.size() + " dirty=" + dirty.size() + ")";
    }

    // ------------------------------------------------------------------
    // Per-frame work
    // ------------------------------------------------------------------

    /**
     * The setupTerrain seat: diff the loaded sections, schedule builds for what changed, and
     * upload what finished. Runs on the render thread before the layer draws.
     */
    public void setupTerrain(double cameraX, double cameraY, double cameraZ) {
        if (world == null) {
            return;
        }
        ensureBuildSide();
        diffLoadedSections();
        promoteAwaitingLight();
        drainFinishedBuilds();
        scheduleBuilds(cameraX, cameraY, cameraZ);
        visibleSectionCount = resident.size();
    }

    private void ensureBuildSide() {
        if (scheduler == null) {
            scheduler = new TerrainBuildScheduler();
            fluids = FluidloggedProbe.resolve();
        }
        if (faceData == null) {
            spriteIndex = new AtlasSpriteIndex();
            spriteIndex.setAtlasSize(atlasWidthPx(), atlasWidthPx());
            faceData = ForgeBlockFaceData.build(spriteIndex);
        }
        pushTextureCellTableIfGrown();
    }

    /**
     * The general stream's shaders resolve a quad's texture index through the backend's texture-cell
     * table; push a fresh snapshot whenever meshing has indexed new sprites since the last push.
     */
    private void pushTextureCellTableIfGrown() {
        if (spriteIndex == null || spriteIndex.size() == pushedTextureCellCount) {
            return;
        }
        var backend = org.soutalab.celtium.vulkan.CeltiumBackends.current();
        if (backend == null) {
            return;
        }
        backend.celtium$setTextureCellTable(spriteIndex.cellTable());
        pushedTextureCellCount = spriteIndex.size();
    }

    /** The block atlas' pixel width, derived from one known sprite's UV span. */
    private int atlasWidthPx() {
        try {
            var sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite("minecraft:blocks/stone");
            float span = sprite.getMaxU() - sprite.getMinU();
            if (span > 0.0001f) {
                return Math.round(sprite.getIconWidth() / span);
            }
        } catch (RuntimeException ignored) {
            // No atlas yet (menu-time driver): the default below is corrected on the next reload.
        }
        return 1024;
    }

    private final Set<Long> populatedChunks = new HashSet<>();

    private void diffLoadedSections() {
        present.clear();
        populatedChunks.clear();
        ChunkProviderClient provider = world.getChunkProvider();
        if (provider instanceof ChunkProviderClientAccessor accessor) {
            for (Chunk chunk : accessor.celeritas$getLoadedChunks().values()) {
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                if (chunk.isLightPopulated()) {
                    populatedChunks.add(net.minecraft.util.math.ChunkPos.asLong(chunk.x, chunk.z));
                }
                ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
                for (int y = 0; y < storages.length; y++) {
                    ExtendedBlockStorage storage = storages[y];
                    if (storage != null && !storage.isEmpty()) {
                        present.add(key(chunk.x, y, chunk.z));
                    }
                }
            }
        }
        CeltiumBackend backend = CeltiumBackends.current();
        // Sections that vanished: unload from the store.
        Iterator<Long> residentIt = resident.iterator();
        while (residentIt.hasNext()) {
            long k = residentIt.next();
            if (!present.contains(k)) {
                if (backend != null) {
                    backend.celtium$lane1RemoveSection(keyX(k), keyY(k), keyZ(k));
                }
                residentIt.remove();
                dirty.remove(k);
                CompletableFuture<MeshBuffers> stale = pending.remove(k);
                if (stale != null) {
                    stale.cancel(false);
                }
            }
        }
        // Sections that appeared: build.
        for (long k : present) {
            if (!resident.contains(k) && !pending.containsKey(k)) {
                dirty.add(k);
            }
        }
    }

    /** Rebuilds sections that were meshed before their chunk's light was populated. */
    private void promoteAwaitingLight() {
        if (awaitingLight.isEmpty()) {
            return;
        }
        Iterator<Long> it = awaitingLight.iterator();
        while (it.hasNext()) {
            long k = it.next();
            if (!present.contains(k)) {
                it.remove();
                continue;
            }
            if (populatedChunks.contains(net.minecraft.util.math.ChunkPos.asLong(keyX(k), keyZ(k)))) {
                dirty.add(k);
                it.remove();
            }
        }
    }

    private void scheduleBuilds(double cameraX, double cameraY, double cameraZ) {
        frameCounter++;
        if (dirty.isEmpty()) {
            return;
        }
        // Nearest first, so the view fills in around the camera instead of at the horizon.
        List<Long> queue = new ArrayList<>(dirty);
        queue.sort((a, b) -> Double.compare(distanceSq(a, cameraX, cameraY, cameraZ),
                distanceSq(b, cameraX, cameraY, cameraZ)));
        int snapshots = 0;
        for (long k : queue) {
            if (pending.size() >= MAX_PENDING_BUILDS || snapshots >= MAX_SNAPSHOTS_PER_FRAME) {
                break;
            }
            Long starved = starvedUntil.get(k);
            if (starved != null && starved.longValue() > frameCounter) {
                // Refused recently: rebuilding now would only be refused again and the
                // snapshot+mesh cost is what starves the frame. Retry on the cooldown.
                continue;
            }
            dirty.remove(k);
            snapshots++;
            int sx = keyX(k), sy = keyY(k), sz = keyZ(k);
            WorldSectionSnapshot snapshot = new WorldSectionSnapshot();
            ForgeSectionScanner.fill(snapshot, world, faceData, fluids, sx, sy, sz);
            if (!populatedChunks.contains(net.minecraft.util.math.ChunkPos.asLong(sx, sz))) {
                // The chunk's light is not populated yet: this mesh will carry zero light.
                // Queue a rebuild for when the light arrives (vanilla re-meshes on light
                // updates; without this the section stays dark forever).
                awaitingLight.add(k);
            } else {
                awaitingLight.remove(k);
            }
            pending.put(k, scheduler.build(snapshot, faceData, MesherOptions.PACK_OFF));
        }
    }

    private static double distanceSq(long k, double cameraX, double cameraY, double cameraZ) {
        double dx = keyX(k) * 16.0 + 8.0 - cameraX;
        double dy = keyY(k) * 16.0 + 8.0 - cameraY;
        double dz = keyZ(k) * 16.0 + 8.0 - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private void drainFinishedBuilds() {
        CeltiumBackend backend = CeltiumBackends.current();
        if (backend == null) {
            return;
        }
        int uploads = 0;
        Iterator<Map.Entry<Long, CompletableFuture<MeshBuffers>>> it = pending.entrySet().iterator();
        while (it.hasNext() && uploads < MAX_UPLOADS_PER_FRAME) {
            Map.Entry<Long, CompletableFuture<MeshBuffers>> entry = it.next();
            CompletableFuture<MeshBuffers> future = entry.getValue();
            if (!future.isDone()) {
                continue;
            }
            it.remove();
            if (future.isCancelled() || future.isCompletedExceptionally()) {
                continue;
            }
            MeshBuffers buffers = future.getNow(null);
            if (buffers == null) {
                continue;
            }
            // TEMP G1 PROBE6: census of the geometry as it arrives at drain time.
            for (int layer = 0; layer < 3; layer++) {
                for (org.soutalab.celtium.core.geometry.PackedAxisQuad q
                        : buffers.axisLayerList(layer)) {
                    DRAIN_AXIS_TOTAL.incrementAndGet();
                    if (q.skyLight() == 0 && q.blockLight() == 0) {
                        DRAIN_AXIS_DARK.incrementAndGet();
                    }
                }
            }
            long k = entry.getKey();
            try {
                if (present.contains(k)) {
                    backend.celtium$lane1UploadSection(keyX(k), keyY(k), keyZ(k), toGeometry(buffers));
                    if (backend.celtium$lane1HasSection(keyX(k), keyY(k), keyZ(k))) {
                        resident.add(k);
                        starvedUntil.remove(k);
                        uploads++;
                    } else {
                        // The store refused the upload (slab pressure): keep the section
                        // dirty so it is rebuilt and retried once space frees, instead of
                        // marking a residency that does not exist (a permanent hole). The
                        // retry waits out a cooldown so a full slab cannot turn into a
                        // rebuild-every-frame churn (Gate G1 measured 8,146 refusals).
                        dirty.add(k);
                        starvedUntil.put(k, frameCounter + 30L);
                    }
                }
            } finally {
                scheduler.releaseBuffers(buffers);
            }
        }
    }

    private static SectionGeometry toGeometry(MeshBuffers buffers) {
        short[] aux = buffers.auxEnabled() ? buffers.auxColumn() : new short[0];
        int axisTotal = buffers.axisTotal();
        if (aux.length != axisTotal) {
            aux = new short[0];
        }
        return new SectionGeometry(
                List.copyOf(buffers.axisLayerList(0)),
                List.copyOf(buffers.axisLayerList(1)),
                List.copyOf(buffers.axisLayerList(2)),
                List.copyOf(buffers.generalLayerList(0)),
                List.copyOf(buffers.generalLayerList(1)),
                List.copyOf(buffers.generalLayerList(2)),
                aux);
    }

    // ------------------------------------------------------------------
    // Draws (RenderGlobalMixin seats)
    // ------------------------------------------------------------------

    /** The renderBlockLayer seat: one backend draw for the vanilla layers, nothing for others. */
    public void drawChunkLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ) {
        int ordinal = switch (layer) {
            case SOLID -> 0;
            case CUTOUT, CUTOUT_MIPPED -> 1;
            case TRANSLUCENT -> 2;
            default -> -1;
        };
        if (ordinal < 0) {
            return;
        }
        CeltiumBackend backend = CeltiumBackends.current();
        if (backend == null) {
            return;
        }
        float[] projection = new float[16];
        float[] modelView = new float[16];
        java.nio.FloatBuffer projBuf = ActiveRenderInfoAccessor.getProjectionMatrix();
        java.nio.FloatBuffer mvBuf = ActiveRenderInfoAccessor.getModelViewMatrix();
        for (int i = 0; i < 16; i++) {
            projection[i] = projBuf.get(i);
            modelView[i] = mvBuf.get(i);
        }
        backend.celtium$lane1DrawLayer(ordinal, projection, modelView, cameraX, cameraY, cameraZ);
    }

    /**
     * The block-entity seat inside renderEntities: every loaded tile entity with a renderer,
     * frustum-tested against the pass camera, drawn through the vanilla dispatcher (whose draws
     * ride the backend's immediate capture like any other mod draw).
     */
    public void renderBlockEntities(ICamera camera, float partialTicks) {
        if (world == null) {
            return;
        }
        int pass = net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        TileEntityRendererDispatcher.instance.preDrawBatch();
        for (TileEntity te : world.loadedTileEntityList) {
            if (te.shouldRenderInPass(pass)
                    && TileEntityRendererDispatcher.instance.getRenderer(te) != null
                    && (camera == null || camera.isBoundingBoxInFrustum(te.getRenderBoundingBox()))) {
                TileEntityRendererDispatcher.instance.render(te, partialTicks, -1);
            }
        }
        TileEntityRendererDispatcher.instance.drawBatch(pass);
    }
}
