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

    /** Light-array fingerprint per resident section, taken when its mesh was built. */
    private final Map<Long, Integer> lightFingerprints = new HashMap<>();
    private int lightSweepCursor;
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
        lightFingerprints.clear();
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
        sweepLightFingerprints();
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
                lightFingerprints.remove(k);
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

    /**
     * A cheap hash of the light arrays a section's mesh was built from: the sky and
     * block NibbleArrays of the centre chunk's own storage and the two vertical
     * neighbours. The integrated server relights chunks after load (the save's arrays
     * can arrive zeroed) and the client arrays then change under a resident mesh;
     * vanilla re-meshes on those updates, so a changed fingerprint re-dirties the
     * section. Swept in a rotating window so the whole resident set is re-checked
     * about once a second without a per-frame full pass.
     */
    private int lightFingerprint(int sx, int sy, int sz) {
        if (!(world instanceof net.minecraft.world.World w)) {
            return 0;
        }
        net.minecraft.world.chunk.Chunk chunk = w.getChunk(sx, sz);
        if (chunk == null) {
            return 0;
        }
        net.minecraft.world.chunk.storage.ExtendedBlockStorage[] sections =
                chunk.getBlockStorageArray();
        int hash = 1;
        for (int dy = -1; dy <= 1; dy++) {
            int idx = sy + dy;
            if (idx < 0 || idx >= sections.length || sections[idx] == null) {
                hash = hash * 31 + 7;
                continue;
            }
            hash = hash * 31 + nibbleHash(sections[idx].getSkyLight());
            hash = hash * 31 + nibbleHash(sections[idx].getBlockLight());
        }
        return hash;
    }

    private static int nibbleHash(net.minecraft.world.chunk.NibbleArray array) {
        if (array == null) {
            return 3;
        }
        byte[] data = array.getData();
        int hash = data.length;
        for (byte b : data) {
            hash = hash * 31 + b;
        }
        return hash;
    }

    private void sweepLightFingerprints() {
        if (resident.isEmpty() || world == null) {
            return;
        }
        List<Long> keys = new ArrayList<>(resident);
        int n = keys.size();
        int batch = Math.min(n, 96);
        for (int i = 0; i < batch; i++) {
            long k = keys.get((lightSweepCursor + i) % n);
            if (pending.containsKey(k) || dirty.contains(k)) {
                continue;
            }
            Integer recorded = lightFingerprints.get(k);
            if (recorded == null) {
                continue;
            }
            int current = lightFingerprint(keyX(k), keyY(k), keyZ(k));
            if (current != recorded.intValue()) {
                lightFingerprints.put(k, current);
                dirty.add(k);
            }
        }
        lightSweepCursor = (lightSweepCursor + batch) % n;
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
            lightFingerprints.put(k, lightFingerprint(sx, sy, sz));
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
        // Celtium has a single cutout layer (the mesher does not split mipped from
        // unmipped), and vanilla issues both CUTOUT_MIPPED and CUTOUT calls every
        // frame (premise verified via the CELTIUM_BLOCKLAYER log), so the cutout
        // layer is drawn on the CUTOUT_MIPPED call only - mapping both calls to it
        // drew every cutout quad twice (NEON-G1-DEPTH-RULING section 1.4).
        int ordinal = switch (layer) {
            case SOLID -> 0;
            case CUTOUT_MIPPED -> 1;
            case CUTOUT -> -1;
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
