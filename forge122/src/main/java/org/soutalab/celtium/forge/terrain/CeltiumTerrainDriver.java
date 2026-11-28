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
import org.soutalab.celtium.vulkan.terrain.lane1.SectionGeometryRaw;
import org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor;
import org.taumc.celeritas.mixin.core.terrain.ChunkProviderClientAccessor;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The Lane 1 terrain driver (S2-b): celtium's own replacement for the retired Celeritas world
 * renderer at the {@code RenderGlobal} seats. It polls the client's loaded-chunk set each tick
 * (no chunk tracker), snapshots changed sections on the render thread, meshes them on the S2-a
 * build scheduler, and uploads the finished geometry to the backend's Lane 1 store; the layer
 * draws themselves are one backend call per vanilla layer.
 * <p>
 * Residency is diffed, not event-driven: a section exists when its chunk is loaded and its
 * storage is non-empty. Block edits arrive through {@link #scheduleRebuildForBlockArea} (the
 * {@code markBlocksForUpdate} seat); everything else - chunk load, chunk unload, world change -
 * falls out of the per-tick diff.
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
    private final LongOpenHashSet resident = new LongOpenHashSet();
    /** Section keys the world currently has, recomputed by the per-tick diff. */
    private final LongOpenHashSet present = new LongOpenHashSet();
    /** Section keys waiting for a (re)build. */
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    /** Sections built while their chunk's light was not yet populated; rebuilt once it is. */
    private final LongOpenHashSet awaitingLight = new LongOpenHashSet();

    /** Light-array fingerprint per resident section, taken when its mesh was built. */
    private final Long2IntOpenHashMap lightFingerprints = new Long2IntOpenHashMap();
    private int lightSweepCursor;
    /** The resident keys as one flat array for the sweep, rebuilt only when residency changed. */
    private long[] sweepKeys;
    private boolean sweepKeysDirty = true;
    /** Gates the per-tick bookkeeping (diff, light promotion, fingerprint sweep) to client ticks. */
    private final TickGate tickGate = new TickGate();
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

    /**
     * The tick gate for the driver's per-tick bookkeeping: chunk loads, unloads and relights
     * land on client ticks, so the loaded-set diff and the light sweep are due only when the
     * world time actually advances - not on every render frame (ruling section 1.3, CPU-1/2).
     * While time is frozen (pause, lag) nothing is due; a world switch resets the gate so the
     * first frame of the new world always syncs.
     */
    static final class TickGate {
        private long lastAccepted = Long.MIN_VALUE;

        /** {@return true} exactly when {@code worldTime} differs from the last accepted value. */
        boolean advance(long worldTime) {
            if (worldTime == lastAccepted) {
                return false;
            }
            lastAccepted = worldTime;
            return true;
        }

        void reset() {
            lastAccepted = Long.MIN_VALUE;
        }
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
        tickGate.reset();
        sweepKeysDirty = true;
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
        // Per-tick bookkeeping: the loaded-set diff, the light promotion and the fingerprint
        // sweep only have new information when a client tick lands, so they run on tick
        // boundaries instead of re-scanning the whole resident set every render frame.
        // Drains and build scheduling stay per-frame (they are budgeted and event-shaped).
        if (tickGate.advance(world.getTotalWorldTime())) {
            diffLoadedSections();
            promoteAwaitingLight();
            sweepLightFingerprints();
        }
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
        backend.celtium$setTextureRectTable(spriteIndex.rectTable());
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

    private final LongOpenHashSet populatedChunks = new LongOpenHashSet();

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
        LongIterator residentIt = resident.iterator();
        while (residentIt.hasNext()) {
            long k = residentIt.nextLong();
            if (!present.contains(k)) {
                if (backend != null) {
                    backend.celtium$lane1RemoveSection(keyX(k), keyY(k), keyZ(k));
                }
                residentIt.remove();
                sweepKeysDirty = true;
                lightFingerprints.remove(k);
                dirty.remove(k);
                CompletableFuture<MeshBuffers> stale = pending.remove(k);
                if (stale != null) {
                    stale.cancel(false);
                }
            }
        }
        // Sections that appeared: build.
        LongIterator presentIt = present.iterator();
        while (presentIt.hasNext()) {
            long k = presentIt.nextLong();
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
        LongIterator it = awaitingLight.iterator();
        while (it.hasNext()) {
            long k = it.nextLong();
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
     * section. Swept in a rotating window on tick boundaries so the whole resident
     * set is re-checked every second or so without a per-frame full pass.
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
        return fingerprintWords(array.getData());
    }

    /**
     * A word-parallel fingerprint of a light array's bytes. The old hash chained
     * {@code hash * 31 + b} over all 2,048 bytes: a serial dependency chain whose latency,
     * not the byte count, is what the sweep paid for (six arrays per section). Four
     * independent accumulators over 8-byte words break the chain. The value is an internal
     * change detector, recorded and compared only by this class, so the contract is just:
     * same bytes give the same value, and a changed byte changes the value (each accumulator
     * step is a bijection in the word, and the final mix is a bijection per accumulator).
     */
    static int fingerprintWords(byte[] data) {
        long h0 = 0x9E3779B97F4A7C15L;
        long h1 = 0xC2B2AE3D27D4EB4FL;
        long h2 = 0x165667B19E3779F9L;
        long h3 = 0x85EBCA77C2B2AE63L;
        int words = data.length >>> 3;
        int w = 0;
        for (; w + 4 <= words; w += 4) {
            h0 = mixWord(h0, wordAt(data, w));
            h1 = mixWord(h1, wordAt(data, w + 1));
            h2 = mixWord(h2, wordAt(data, w + 2));
            h3 = mixWord(h3, wordAt(data, w + 3));
        }
        for (; w < words; w++) {
            h0 = mixWord(h0, wordAt(data, w));
        }
        int tail = data.length & 7;
        if (tail != 0) {
            long partial = 0L;
            for (int i = 0; i < tail; i++) {
                partial |= (long) (data[(words << 3) + i] & 0xFF) << (i * 8);
            }
            h1 = mixWord(h1, partial ^ ((long) tail << 56));
        }
        long combined = splitMix64(h0) ^ splitMix64(h1) ^ splitMix64(h2) ^ splitMix64(h3)
                ^ data.length;
        return (int) (combined ^ (combined >>> 32));
    }

    private static long wordAt(byte[] data, int wordIndex) {
        int base = wordIndex << 3;
        return (long) (data[base] & 0xFF)
                | ((long) (data[base + 1] & 0xFF) << 8)
                | ((long) (data[base + 2] & 0xFF) << 16)
                | ((long) (data[base + 3] & 0xFF) << 24)
                | ((long) (data[base + 4] & 0xFF) << 32)
                | ((long) (data[base + 5] & 0xFF) << 40)
                | ((long) (data[base + 6] & 0xFF) << 48)
                | ((long) (data[base + 7] & 0xFF) << 56);
    }

    private static long mixWord(long hash, long word) {
        return Long.rotateLeft(hash ^ word, 27) * 5L + 0x52DCE729L;
    }

    private static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private void sweepLightFingerprints() {
        if (resident.isEmpty() || world == null) {
            return;
        }
        // The key array is rebuilt only when residency changed (diff removals, drain
        // uploads, world switches mark it dirty); the old code copied the whole resident
        // set into a fresh ArrayList on every frame.
        if (sweepKeysDirty || sweepKeys == null) {
            sweepKeys = resident.toLongArray();
            sweepKeysDirty = false;
        }
        int n = sweepKeys.length;
        int batch = Math.min(n, 96);
        for (int i = 0; i < batch; i++) {
            long k = sweepKeys[(lightSweepCursor + i) % n];
            if (pending.containsKey(k) || dirty.contains(k)) {
                continue;
            }
            if (!lightFingerprints.containsKey(k)) {
                continue;
            }
            int recorded = lightFingerprints.get(k);
            int current = lightFingerprint(keyX(k), keyY(k), keyZ(k));
            if (current != recorded) {
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
            WorldSectionSnapshot snapshot = scheduler.acquireSnapshot();
            // A fill that throws leaves the snapshot half-written: it is dropped, not
            // returned to the pool (the pre-pool code dropped it the same way). Once the
            // build is handed off, the future's completion - success, failure, or the
            // cancellation an unload triggers - returns the snapshot to the pool.
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
            CompletableFuture<MeshBuffers> future =
                    scheduler.build(snapshot, faceData, MesherOptions.packOffFromProperty());
            future.whenComplete((buffers, error) -> scheduler.releaseSnapshot(snapshot));
            pending.put(k, future);
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
                    backend.celtium$lane1UploadSectionRaw(keyX(k), keyY(k), keyZ(k), toRaw(buffers));
                    if (backend.celtium$lane1HasSection(keyX(k), keyY(k), keyZ(k))) {
                        resident.add(k);
                        sweepKeysDirty = true;
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

    /**
     * The upload carrier in the mesher's own storage shape (S2-b-perf CPU-4): references to
     * the pooled buffers' backing arrays, consumed synchronously by the store's bulk copy
     * before the buffers return to the pool in the caller's finally - the old form built a
     * record and two list copies per quad on this thread. The per-corner light column
     * (R-107) rides as the one concatenated array, in the store's layout.
     */
    private static SectionGeometryRaw toRaw(MeshBuffers buffers) {
        return new SectionGeometryRaw(
                new int[][]{buffers.axisWords(0), buffers.axisWords(1), buffers.axisWords(2)},
                new int[]{buffers.axisCount(0), buffers.axisCount(1), buffers.axisCount(2)},
                new short[][]{buffers.axisAuxArray(0), buffers.axisAuxArray(1), buffers.axisAuxArray(2)},
                buffers.auxEnabled(),
                new float[][]{buffers.generalPositions(0), buffers.generalPositions(1),
                        buffers.generalPositions(2)},
                new int[][]{buffers.generalMeta(0), buffers.generalMeta(1), buffers.generalMeta(2)},
                new int[]{buffers.generalCount(0), buffers.generalCount(1), buffers.generalCount(2)},
                buffers.cornerLightColumn());
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
