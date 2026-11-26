package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.hal.SlabAllocator;
import org.soutalab.celtium.vulkan.terrain.AsyncSectionMesher;
import org.soutalab.celtium.vulkan.terrain.TerrainResidencyGate;
import org.soutalab.celtium.vulkan.terrain.TerrainSectionStore;
import org.soutalab.celtium.vulkan.terrain.TerrainSlabAllocator;
import org.soutalab.celtium.vulkan.terrain.TerrainUploadBudget;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

/**
 * Owns the Tier-A terrain state for one backend: the shared
 * {@link TerrainSectionStore}, the upload budget, and one
 * {@link TerrainEngineA} executor per render pass. Pass ordinals are assigned by
 * identity in first-use order and are the same ordinals the SectionDesc records
 * carry, so registration and culling always agree on them.
 * <p>
 * The host also owns the pass-independent GPU resources every executor shares
 * (designer audit 2026-11-25, Stage 4 item 5-②): the geometry slab allocator —
 * one allocator for all passes keeps the terrain geometry within the shared
 * slab cap instead of multiplying it by the pass count — and the static quad
 * index buffer, whose contents depend only on
 * {@link TerrainSectionStore#MAX_QUADS_PER_SLAB}. Both are created lazily and
 * deleted here, after every engine, because the allocator's slabs are the
 * engines' slab buffers' memory.
 * <p>
 * The resident store images and arenas are shared the same way (R-15): the
 * SectionDesc table and the factor-base image are one store image every pass
 * reads identically, and the factor and sorted arenas are partial images of
 * the store's single cross-pass offset spaces, so one buffer per kind holds
 * the union at the store's own offsets. Until R-15 each engine kept a private
 * copy of all four — the two images byte-identical duplicates, the two arenas
 * 32 MiB each per pass — for no reader's benefit.
 * <p>
 * The two images are per-frame-slot since T3 condition (2): each frame slot
 * owns its copy, written only when that slot's snapshot is prepared — after
 * the slot's previous submission completed, by the frame-slot contract — and
 * immutable for the rest of the recording. What a recording reads is a
 * {@link FrameSnapshot}: the store's shape captured atomically at the slot's
 * first prepare of the recording, handles and counts together, so no
 * dispatch ever mixes a frozen image with live counts (INV-2a/2b).
 * <p>
 * Deferred arena releases are stamped and collected in the context's slab
 * completion currency ({@link VulkanContext#celtium$slabReleaseValue()} /
 * {@link VulkanContext#celtium$slabCompletedValue()}): on a timeline device
 * collection waits for real GPU completion, off one it waits for a later
 * drain. Until Stage 2/3 landed, the stamps were the host's own generation
 * counter with a two-generation lag — an interim rule approximating the
 * frames-in-flight depth; the counter remains, but only as the registration
 * counter written into each section desc, no longer as a completion proof.
 */
final class TerrainEngineAHost {
    // Tier-A is the default terrain route (wave-2 cutover, 2026-11-25): the property
    // remains only as a bisection switch to turn it off, not a parallel route flag.
    // The A->B overhaul's Stage 4 escape flag -Dceltium.terrain.engine=legacy turns
    // it off as well (the whole engine falls back to the legacy terrain route).
    static final boolean ENABLED =
            !"legacy".equals(System.getProperty("celtium.terrain.engine"))
                    && Boolean.parseBoolean(System.getProperty("celtium.vulkan.terrainEngineA", "true"));

    private final AsyncSectionMesher mesher = new AsyncSectionMesher();
    private final TerrainSectionStore store = new TerrainSectionStore();
    private final TerrainUploadBudget budget = new TerrainUploadBudget(
            Long.getLong("celtium.vulkan.terrainUploadMiB", 4L) * 1024 * 1024);
    private final TerrainResidencyGate residencyGate = new TerrainResidencyGate();
    private final Map<TerrainRenderPass, Integer> ordinals = new IdentityHashMap<>();
    private final Map<TerrainRenderPass, TerrainEngineA> engines = new IdentityHashMap<>();
    /**
     * Pass ordinals tainted by a Tier-A rejection (R-55, Step B): a rejected
     * section lives on the legacy route alone, so while its pass is tainted the
     * pass must not arm — the resident table does not hold the section and an
     * armed pass would draw without it. Keyed by ordinal; registration and the
     * pass head run on different threads, so the set is concurrent.
     */
    private final java.util.Set<Integer> taintedPassOrdinals =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<TerrainRenderPass, TerrainPackEngine> packEngines = new IdentityHashMap<>();
    private final java.util.Map<Integer, VulkanHostBuffer> packArenaBuffers = new java.util.HashMap<>();
    private long generation;
    /**
     * The world epoch this host's state belongs to (T3 design section 5.2):
     * advanced by every successful paired reset in {@link #delete}, and
     * stamped into each executor at creation, so an executor of a dead epoch
     * can be told apart from the live ones and refuse to run.
     */
    private long worldEpoch;
    /**
     * Whether the current epoch has already been torn down by {@link #delete}:
     * a repeated delete is then a no-op, and the flag clears when the next
     * epoch's first executor is created.
     */
    private boolean worldTornDown;
    private VulkanContext context;
    private SlabAllocator geometrySlabAllocator;
    private VulkanHostBuffer sharedIndexBuffer;
    // Resident GPU images of the store (host-visible, written through the
    // mapping), one copy per frame slot since T3 condition (2) (R-15's
    // sharing stands across passes; the slot is the lifetime unit, see the
    // class javadoc).
    private final VulkanHostBuffer[] descBuffers =
            new VulkanHostBuffer[FrameSubmissionScheduler.FRAME_SLOTS];
    private final VulkanHostBuffer[] factorBaseBuffers =
            new VulkanHostBuffer[FrameSubmissionScheduler.FRAME_SLOTS];
    // Per-slot snapshot bookkeeping: the snapshot each slot's current
    // recording reads, the recording identity it was built under, and the
    // store generation last uploaded into the slot's buffers.
    private final FrameSnapshot[] slotSnapshots =
            new FrameSnapshot[FrameSubmissionScheduler.FRAME_SLOTS];
    private final long[] slotSnapshotEpochs = new long[FrameSubmissionScheduler.FRAME_SLOTS];
    private final long[] slotSnapshotFrameKeys = new long[FrameSubmissionScheduler.FRAME_SLOTS];
    private final long[] slotUploadedGenerations = new long[FrameSubmissionScheduler.FRAME_SLOTS];
    // Frame keying of the snapshot: the backend stamps the current frame at
    // its Tier-A pass heads, and the stamp is one component of the snapshot
    // identity, so a new frame never inherits the previous frame's snapshot
    // even where a recording were to span the boundary.
    private long descFrameKey = Long.MIN_VALUE;
    private VulkanHostBuffer factorBuffer;
    private VulkanHostBuffer sortedBuffer;

    AsyncSectionMesher mesher() {
        return this.mesher;
    }

    TerrainSectionStore store() {
        return this.store;
    }

    TerrainResidencyGate residencyGate() {
        return this.residencyGate;
    }

    /** The world epoch the executors created now belong to; see {@link #delete}. */
    long worldEpoch() {
        return this.worldEpoch;
    }

    /**
     * Counts one section the renderer's stride gate kept out of Tier-A
     * (order (i)): the region was not built with the TerrainVertex16 format,
     * so {@code registerTierA} was never called for it — a permanent leak the
     * engine-side counters cannot see.
     */
    void noteStrideGate(int sectionX, int sectionY, int sectionZ, TerrainRenderPass pass) {
        this.residencyGate.noteMissing(new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinalFor(pass)),
                TerrainResidencyGate.Reason.STRIDE_GATE);
    }

    /** Counts one section whose Tier-A build threw before registration (order (i)). */
    void noteBuildException(int sectionX, int sectionY, int sectionZ, TerrainRenderPass pass) {
        this.residencyGate.noteMissing(new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinalFor(pass)),
                TerrainResidencyGate.Reason.BUILD_EXCEPTION);
    }

    /** Taints one pass (R-55, Step B): Tier-A rejected a section of it, so the section is legacy-only. */
    void taintPass(TerrainRenderPass pass) {
        this.taintedPassOrdinals.add(ordinalFor(pass));
    }

    /** Whether this pass is tainted. A pass with no ordinal yet was never offered a section, so it is clean. */
    boolean isPassTainted(TerrainRenderPass pass) {
        Integer ordinal = this.ordinals.get(pass);
        return ordinal != null && this.taintedPassOrdinals.contains(ordinal);
    }

    /**
     * Consumes one per-frame residency verification (R-55, Step B): a complete
     * report — every expected section of the pass resident in the store —
     * clears the pass' taint, because the fact the taint recorded (a section
     * missing from the resident table) no longer holds. An incomplete report
     * changes nothing.
     */
    void noteResidencyReport(TerrainResidencyGate.Report report) {
        if (report.complete()) {
            this.taintedPassOrdinals.remove(report.passOrdinal());
        }
    }

    long nextGeneration() {
        return ++this.generation;
    }

    int ordinalFor(TerrainRenderPass pass) {
        Integer ordinal = this.ordinals.get(pass);
        if (ordinal == null) {
            ordinal = this.ordinals.size();
            this.ordinals.put(pass, ordinal);
        }
        return ordinal;
    }

    TerrainEngineA engineFor(VulkanContext context, TerrainRenderPass pass) {
        this.context = context;
        TerrainEngineA engine = this.engines.get(pass);
        if (engine == null) {
            engine = new TerrainEngineA(context, ordinalFor(pass),
                    "pass-" + ordinalFor(pass), this.store, this.budget, this.residencyGate, this);
            this.engines.put(pass, engine);
            this.worldTornDown = false;
        }
        return engine;
    }

    /**
     * Whether an executor already exists for {@code pass} and still has live
     * work (resident records or a pending deferred queue). A peek only: the
     * terrain pass entry asks this before deciding the pass has nothing to
     * draw, and creating an engine there would manufacture the very presence
     * being tested.
     */
    boolean hasLiveWork(TerrainRenderPass pass) {
        TerrainEngineA engine = this.engines.get(pass);
        return engine != null && engine.hasLiveWork();
    }

    /**
     * Whether a pack executor already exists for {@code pass} and still has
     * live work (resident pack records or a pending deferred queue). The
     * pack half of the terrain pass entry's presence judgment: the pack
     * route's gates consult this so a pass held entirely in the pack space
     * is not refused — or short-circuited as empty — on legacy presence
     * alone. A peek only, for the same reason as {@link #hasLiveWork}:
     * creating an executor here would manufacture the presence being tested.
     */
    boolean hasPackLiveWork(TerrainRenderPass pass) {
        TerrainPackEngine engine = this.packEngines.get(pass);
        return engine != null && engine.hasLiveWork();
    }

    /**
     * The pack-format variant executor for one pass (R-17 candidate 2-MIN):
     * the same shared store, budget and residency gate as the vanilla
     * executors, drawing the pass' foreign-format records from the pack
     * arenas. A pass is served by exactly one of the two executor kinds —
     * its presented format decides at registration time.
     */
    TerrainPackEngine packEngineFor(VulkanContext context, TerrainRenderPass pass) {
        this.context = context;
        TerrainPackEngine engine = this.packEngines.get(pass);
        if (engine == null) {
            engine = new TerrainPackEngine(context, ordinalFor(pass),
                    "pack-" + ordinalFor(pass), this.store, this.budget, this.residencyGate, this);
            this.packEngines.put(pass, engine);
            this.worldTornDown = false;
        }
        return engine;
    }

    /** The pack executor already serving a pass, or null when none was created. */
    TerrainPackEngine packEngineIfPresent(TerrainRenderPass pass) {
        return this.packEngines.get(pass);
    }

    /**
     * The GPU buffer of one pack slab (keyed by desc slab id), created lazily
     * at its arena's slab size. The pack engines upload through its mapping
     * and bind it as the vertex buffer; the host owns it and deletes it after
     * every engine.
     */
    VulkanHostBuffer packArenaBuffer(VulkanContext context, int descSlabId) {
        VulkanHostBuffer buffer = this.packArenaBuffers.get(descSlabId);
        if (buffer == null) {
            TerrainSectionStore.PackArena arena = this.store.packArenaOfSlab(descSlabId);
            if (arena == null) {
                throw new IllegalStateException("no pack arena for desc slab " + descSlabId);
            }
            buffer = new VulkanHostBuffer(context, arena.slabBytes(),
                    org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
            this.packArenaBuffers.put(descSlabId, buffer);
        }
        return buffer;
    }

    /**
     * The one geometry slab allocator shared by every pass' executor, created
     * lazily from the first slab buffer's own memory-type choice. Every pass'
     * slab buffers carry the same usage over the shared store's slab size, so
     * the first buffer's memory type is every buffer's; a later call's
     * arguments are the same values by construction and are not re-read.
     */
    SlabAllocator geometrySlabAllocator(VulkanContext context, int memoryType, long slabBytes) {
        if (this.geometrySlabAllocator == null) {
            this.geometrySlabAllocator = new SlabAllocator(context, "terrainGeometry", memoryType,
                    slabBytes, org.soutalab.celtium.vulkan.terrain.TerrainSlabAllocator.DEFAULT_MAX_SLABS, true);
        }
        return this.geometrySlabAllocator;
    }

    /**
     * {@return the terrainGeometry entry of the context's slab pool summary} (BO convergence design
     * §5.1): the device side is the shared geometry allocator's live slab count and used bytes, the
     * logical side is the shared store ledger's slab count and live bytes, and every executor's created
     * slab buffer count is listed in pass-ordinal order. One census reading then sets the device count
     * against the logical count - the two sides of the cross-pass sharing lever's effect formula - and
     * shows how the per-pass device buffers multiply it. The device allocator is created lazily with the
     * first slab buffer, so before that the device side reads as zero against the ledger's own numbers.
     */
    String terrainSlabSummary() {
        SlabAllocator allocator = this.geometrySlabAllocator;
        StringBuilder summary = new StringBuilder("terrainGeometry(type=")
                .append(allocator == null ? -1 : allocator.memoryTypeIndex()).append(')');
        summary.append(":usedBytes=").append(allocator == null ? 0L : allocator.usedBytes())
                .append(",slabs=").append(allocator == null ? 0 : allocator.liveSlabCount())
                .append(",oversizedSlabs=").append(allocator == null ? 0 : allocator.oversizedSlabCount())
                .append(",oversizedSlabSizes=").append(VulkanContext.oversizedSlabSizesText(
                        allocator == null ? new long[0] : allocator.oversizedSlabSizes()));
        TerrainSlabAllocator logical = this.store.geometryAllocator();
        summary.append(",logicalSlabs=").append(logical.slabCount())
                .append(",logicalLiveBytes=").append(logical.liveBytes())
                .append(",engineSlabBuffers=[");
        List<Map.Entry<TerrainRenderPass, TerrainEngineA>> entries =
                new ArrayList<>(this.engines.entrySet());
        entries.sort((left, right) -> Integer.compare(
                this.ordinals.getOrDefault(left.getKey(), Integer.MAX_VALUE),
                this.ordinals.getOrDefault(right.getKey(), Integer.MAX_VALUE)));
        boolean first = true;
        for (Map.Entry<TerrainRenderPass, TerrainEngineA> entry : entries) {
            if (!first) {
                summary.append(',');
            }
            first = false;
            summary.append(entry.getValue().liveSlabBufferCount());
        }
        summary.append(']');
        summary.append(",pendingRetirements=").append(this.store.pendingRetirementCount());
        return summary.toString();
    }

    /**
     * The static quad index buffer shared by every pass: its contents are the
     * fixed quad triangulation for {@link TerrainSectionStore#MAX_QUADS_PER_SLAB}
     * quads and depend on nothing a pass owns. Written once, at creation.
     */
    VulkanHostBuffer sharedIndexBuffer(VulkanContext context) {
        if (this.sharedIndexBuffer == null) {
            int quads = TerrainSectionStore.MAX_QUADS_PER_SLAB;
            VulkanHostBuffer buffer = new VulkanHostBuffer(context, (long) quads * 6 * 4,
                    VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            long address = buffer.mappedAddress();
            for (int q = 0; q < quads; q++) {
                long at = address + (long) q * 6 * 4;
                int baseVertex = q * 4;
                MemoryUtil.memPutInt(at, baseVertex);
                MemoryUtil.memPutInt(at + 4, baseVertex + 1);
                MemoryUtil.memPutInt(at + 8, baseVertex + 2);
                MemoryUtil.memPutInt(at + 12, baseVertex);
                MemoryUtil.memPutInt(at + 16, baseVertex + 2);
                MemoryUtil.memPutInt(at + 20, baseVertex + 3);
            }
            this.sharedIndexBuffer = buffer;
        }
        return this.sharedIndexBuffer;
    }

    /**
     * The SectionDesc table of one frame slot, for the cull compute's
     * descriptor bookkeeping, or {@code null} before the slot's first
     * snapshot. Callers reach this only after a snapshot call for the same
     * slot in the same recording, the engine's precondition for naming it.
     */
    VulkanHostBuffer descBuffer(int slotIndex) {
        return this.descBuffers[slotIndex];
    }

    /**
     * One recording's frozen view of the store (T3 condition (2)): the image
     * generation, the record count, the slot's desc and factor-base buffer
     * handles, and the per-pass slab counts of both spaces, captured
     * atomically from the store when the snapshot was built. Every count a
     * dispatch or an arming reads comes from here, never from the live
     * store, so a registration landing mid-recording cannot split the
     * recording's passes across two generations (INV-2a).
     */
    static final class FrameSnapshot {
        private static final int[] NO_COUNTS = new int[0];

        private final long generation;
        private final int recordCount;
        private final long descBufferHandle;
        private final long factorBaseBufferHandle;
        private final int slotIndex;
        private final int[][] vanillaSlabCounts;
        private final int[][] packSlabCounts;

        private FrameSnapshot(long generation, int recordCount, long descBufferHandle,
                              long factorBaseBufferHandle, int slotIndex,
                              int[][] vanillaSlabCounts, int[][] packSlabCounts) {
            this.generation = generation;
            this.recordCount = recordCount;
            this.descBufferHandle = descBufferHandle;
            this.factorBaseBufferHandle = factorBaseBufferHandle;
            this.slotIndex = slotIndex;
            this.vanillaSlabCounts = vanillaSlabCounts;
            this.packSlabCounts = packSlabCounts;
        }

        long generation() {
            return this.generation;
        }

        /** The store's record count at capture time: every pass' dispatch width and push count. */
        int recordCount() {
            return this.recordCount;
        }

        /** Raw VkBuffer of this slot's desc table (graphics binding 6). */
        long descBufferHandle() {
            return this.descBufferHandle;
        }

        /** Raw VkBuffer of this slot's factor-base image (graphics binding 7). */
        long factorBaseBufferHandle() {
            return this.factorBaseBufferHandle;
        }

        int slotIndex() {
            return this.slotIndex;
        }

        /**
         * The frozen per-slab record counts of one vanilla pass: only that
         * pass' records are counted, so the pass arms no slab it holds
         * nothing in (prescription B's empty-copy removal, kept by M2). A
         * pass with no record at capture time reads as all zero.
         */
        int[] vanillaSlabCounts(int passOrdinal) {
            return passOrdinal >= 0 && passOrdinal < this.vanillaSlabCounts.length
                    ? this.vanillaSlabCounts[passOrdinal] : NO_COUNTS;
        }

        /** The frozen per-slab record counts of one pack pass, indexed by desc slab id. */
        int[] packSlabCounts(int passOrdinal) {
            return passOrdinal >= 0 && passOrdinal < this.packSlabCounts.length
                    ? this.packSlabCounts[passOrdinal] : NO_COUNTS;
        }
    }

    /** Whether a slot's standing snapshot already serves this recording: same epoch, same frame stamp. */
    static boolean snapshotCurrent(boolean exists, long snapshotEpoch, long snapshotFrameKey,
                                   long recordingEpoch, long frameKey) {
        return exists && snapshotEpoch == recordingEpoch && snapshotFrameKey == frameKey;
    }

    /** Whether a slot's buffers must take the captured image: not created yet, or a generation behind. */
    static boolean slotUploadDue(boolean bufferExists, long generation, long uploadedGeneration) {
        return !bufferExists || generation != uploadedGeneration;
    }

    /**
     * Whether draws armed under {@code armedRecordingEpoch} may issue in the
     * recording {@code recordingEpoch} (INV-2c): only the very recording
     * that produced a slot's indirect streams may issue them. An unarmed
     * slot carries -1, which no real epoch (counting up from 0) equals.
     */
    static boolean drawEpochCurrent(long armedRecordingEpoch, long recordingEpoch) {
        return armedRecordingEpoch == recordingEpoch;
    }

    /**
     * The snapshot {@code slotIndex}' current recording reads, building it on
     * the recording's first prepare: the store's shape is captured
     * atomically and uploaded into the slot's own desc and factor-base
     * buffers. A re-call within the same recording — the frame's other
     * passes and stages — returns the standing snapshot untouched, so the
     * whole recording reads one generation. A later recording (a new epoch,
     * or a new frame stamp) builds afresh from the store as it then stands.
     * The slot's buffers are written only here, and a slot's recording
     * starts only after its previous submission completed (the frame-slot
     * contract), so no in-flight submission reads bytes being rewritten
     * (INV-2b).
     */
    FrameSnapshot prepareSlotSnapshot(VulkanContext context, int slotIndex, long recordingEpoch) {
        FrameSnapshot current = this.slotSnapshots[slotIndex];
        if (snapshotCurrent(current != null, this.slotSnapshotEpochs[slotIndex],
                this.slotSnapshotFrameKeys[slotIndex], recordingEpoch, this.descFrameKey)) {
            return current;
        }
        TerrainSectionStore.SnapshotData data = this.store.captureSnapshot();
        uploadSlotImage(context, slotIndex, data);
        FrameSnapshot snapshot = new FrameSnapshot(data.generation(), data.recordCount(),
                this.descBuffers[slotIndex].buffer(), this.factorBaseBuffers[slotIndex].buffer(),
                slotIndex, data.vanillaSlabCounts(), data.packSlabCounts());
        this.slotSnapshots[slotIndex] = snapshot;
        this.slotSnapshotEpochs[slotIndex] = recordingEpoch;
        this.slotSnapshotFrameKeys[slotIndex] = this.descFrameKey;
        return snapshot;
    }

    /**
     * Writes one capture into a slot's desc and factor-base buffers, unless
     * the slot already holds that generation. The buffers are named by
     * Tier-A descriptor sets (bindings 6 and 7) that are only rewritten at a
     * pass head, under conditions, for the current frame slot — so a set can
     * keep naming a grown-away handle long after the growth. Retiring the
     * old handle at growth let the context's retired-buffer drain destroy a
     * buffer a live set still named (VUID-08114, diagnosed 2026-11-26). Both
     * buffers are small (sections x 48B / x 4B) and growth is geometric, so
     * keeping every superseded buffer until delete() costs a bounded,
     * negligible amount of slab space; delete() below destroys them all at
     * host teardown.
     */
    private void uploadSlotImage(VulkanContext context, int slotIndex,
                                 TerrainSectionStore.SnapshotData data) {
        if (!slotUploadDue(this.descBuffers[slotIndex] != null, data.generation(),
                this.slotUploadedGenerations[slotIndex])) {
            return;
        }
        int[] descImage = data.descImage();
        int[] factorBaseImage = data.factorBaseImage();
        long descBytes = Math.max(48L, (long) descImage.length * 4L);
        if (this.descBuffers[slotIndex] == null) {
            this.descBuffers[slotIndex] = new VulkanHostBuffer(context, descBytes,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            this.factorBaseBuffers[slotIndex] = new VulkanHostBuffer(context,
                    Math.max(4L, (long) factorBaseImage.length * 4L), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            this.descBuffers[slotIndex].retainSupersededBuffers();
            this.factorBaseBuffers[slotIndex].retainSupersededBuffers();
        } else {
            this.descBuffers[slotIndex].ensureCapacity(descBytes);
            this.factorBaseBuffers[slotIndex].ensureCapacity(
                    Math.max(4L, (long) factorBaseImage.length * 4L));
        }
        long descAddress = this.descBuffers[slotIndex].mappedAddress();
        for (int i = 0; i < descImage.length; i++) {
            MemoryUtil.memPutInt(descAddress + (long) i * 4L, descImage[i]);
        }
        long factorAddress = this.factorBaseBuffers[slotIndex].mappedAddress();
        for (int i = 0; i < factorBaseImage.length; i++) {
            MemoryUtil.memPutInt(factorAddress + (long) i * 4L, factorBaseImage[i]);
        }
        this.slotUploadedGenerations[slotIndex] = data.generation();
    }

    /**
     * Stamps the frame the backend's Tier-A pass heads are serving, at the pass
     * head, before any engine prepares. The stamp is one component of the
     * snapshot identity (see {@link #prepareSlotSnapshot}); stamping the same
     * frame again is a no-op in effect.
     */
    void noteTierAFrameHead(long frameKey) {
        this.descFrameKey = frameKey;
    }

    /**
     * The factor arena shared by every pass (graphics binding 8), created lazily
     * at its first touch, as the per-engine copies were. The store's one factor
     * allocator manages the whole arena offset space across passes and writes
     * never collide, so the shared buffer holds every pass' bytes at the same
     * offsets the private images held them.
     */
    VulkanHostBuffer factorBuffer(VulkanContext context) {
        if (this.factorBuffer == null) {
            this.factorBuffer = new VulkanHostBuffer(context, 32L * 1024 * 1024,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        return this.factorBuffer;
    }

    /** The sorted-index arena shared by every pass, the factor arena's twin. */
    VulkanHostBuffer sortedBuffer(VulkanContext context) {
        if (this.sortedBuffer == null) {
            this.sortedBuffer = new VulkanHostBuffer(context,
                    this.store.sortedAllocator().slabBytes(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        }
        return this.sortedBuffer;
    }

    /**
     * The value deferred store releases are stamped with: the context's slab
     * release value once an engine has been created against one. Before that
     * no section can be registered, so the generation counter the interim
     * rule used is a safe stand-in.
     */
    private long releaseStamp() {
        return this.context != null ? this.context.celtium$slabReleaseValue() : this.generation;
    }

    /**
     * The value store collection may proceed up to: the context's slab
     * completed value, or — before any engine exists, when nothing is
     * registered — the interim rule's two-generation lag.
     */
    private long completedStamp() {
        return this.context != null ? this.context.celtium$slabCompletedValue()
                : Math.max(0, this.generation - 2);
    }

    /**
     * The frame-boundary checkpoint for the store's pending-retirement ledger
     * (T3 design section 2.3): stamp every pended range with the release
     * currency, then collect what the completion currency already covers, in
     * that order. The backend calls this only from the seats where the stamp
     * is known to name an accepted submission — the context's post-flush hook
     * and the boundary fallback for batch-less frames (INV-1b) — never from
     * the removal paths themselves.
     */
    void drainPendingRetirements() {
        this.store.stampPendingRetirements(releaseStamp());
        this.store.collectReleased(completedStamp());
    }

    /** Re-uploads one pass' sorted indices for a section after a translucent re-sort. */
    void updateSortedSection(int sectionX, int sectionY, int sectionZ, TerrainRenderPass pass,
                             org.embeddedt.embeddium.impl.common.util.NativeBuffer indices) {
        Integer ordinal = this.ordinals.get(pass);
        if (ordinal == null) {
            return;
        }
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinal);
        TerrainPackEngine packEngine = this.packEngines.get(pass);
        if (packEngine != null) {
            packEngine.updateSorted(key, indices);
            return;
        }
        TerrainEngineA engine = this.engines.get(pass);
        if (engine != null) {
            engine.updateSorted(key, indices);
        }
    }

    /**
     * Removes every pass' record of one section. The records' ranges retire
     * onto the store's pending ledger; the boundary checkpoint
     * ({@link #drainPendingRetirements}) stamps and collects them — removal
     * itself no longer collects, so collection happens only at the two
     * licensed points (the checkpoint, and allocate's collect-before-fail).
     */
    void unregisterSection(int sectionX, int sectionY, int sectionZ) {
        for (int ordinal : this.ordinals.values()) {
            TerrainSectionStore.Key key = new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinal);
            this.store.unregister(key);
            this.residencyGate.forget(key);
            // The deferred queues are invalidated with the record (T3
            // design section 4.2): a queued build of an unregistered
            // section must never resurrect through a later drain. Keys
            // carry their pass ordinal and each executor queues only its
            // own pass' sections, so the calls into the other executors
            // are plain misses.
            for (TerrainEngineA engine : this.engines.values()) {
                engine.cancelDeferred(key);
            }
            for (TerrainPackEngine engine : this.packEngines.values()) {
                engine.cancelDeferred(key);
            }
        }
    }

    /**
     * Ends the current world epoch: the paired reset of T3 design section
     * 5.2, behind the quiescence + completion gate. The gate is verified
     * here, at the host's own door, not trusted to the caller: no recording
     * may be open, no recorded-but-unsubmitted batch may be queued, and
     * every submission must be confirmed complete. Unless all three hold,
     * nothing is destroyed and {@code false} is returned - a half-torn epoch
     * is never left for the next world to adopt (INV-4a); the caller settles
     * the device (finish, flush, wait - the backend's settle, M4) and calls
     * again. When the gate holds, the reset runs its five steps in order and
     * the post-state is total (INV-4b): no records, no live ledger bytes, no
     * pending retirements, every shared buffer - superseded handles included
     * - destroyed, the geometry slab allocator gone. A repeated call after a
     * successful reset is a harmless no-op returning {@code true}.
     *
     * @return whether the epoch now stands torn down
     */
    boolean delete(VulkanContext context) {
        if (this.worldTornDown) {
            return true;
        }
        boolean recordingOpen = context.celtium$recordingOpen();
        boolean pendingBatchedSubmits = context.celtium$hasPendingBatchedSubmits();
        boolean submissionCompleted = context.celtium$isSubmissionCompleted();
        if (recordingOpen || pendingBatchedSubmits || !submissionCompleted) {
            System.out.println("[Celtium] CELTIUM_TERRAIN_A_DELETE_REFUSED epoch=" + this.worldEpoch
                    + " recordingOpen=" + recordingOpen
                    + " pendingBatchedSubmits=" + pendingBatchedSubmits
                    + " submissionCompleted=" + submissionCompleted);
            return false;
        }
        // Step 1: every executor, vanilla and pack. Each frees the payload
        // copies its own deferred FIFO owns (condition (3)'s ownership).
        for (TerrainEngineA engine : this.engines.values()) {
            engine.delete();
        }
        this.engines.clear();
        for (TerrainPackEngine engine : this.packEngines.values()) {
            engine.delete();
        }
        this.packEngines.clear();
        // Step 2: the store's paired reset - records, unpublished allocations
        // and every ledger leave with the epoch, in one wholesale replacement.
        this.store.resetForNewEpoch();
        // Step 3: the host's accompanying state, and the epoch advance itself.
        this.residencyGate.reset();
        this.taintedPassOrdinals.clear();
        this.ordinals.clear();
        this.budget.reset();
        // The desc upload state is per frame slot since slice S1: each
        // slot's snapshot bookkeeping returns to its construction state
        // (the snapshots themselves are dropped with their buffers in
        // step 4, and a destroyed buffer makes the next upload due
        // regardless of the generation counters).
        for (int slot = 0; slot < this.slotSnapshots.length; slot++) {
            this.slotSnapshotEpochs[slot] = 0;
            this.slotSnapshotFrameKeys[slot] = 0;
            this.slotUploadedGenerations[slot] = 0;
        }
        this.descFrameKey = Long.MIN_VALUE;
        this.worldEpoch++;
        // Step 4: the shared VkBuffers. The shared resident buffers (R-15) are
        // read by every pass' draws and cull dispatches until the engines are
        // gone, so they go here, after every engine; a host buffer's delete
        // destroys its retained superseded handles with it, which is the whole
        // set the retention kept alive for this moment. The desc and
        // factor-base images are per frame slot (slice S1): every slot's
        // copy goes, and the snapshots that named them with it.
        int retiredSlabBefore = context.celtium$retiredSlabBufferCount();
        for (int slot = 0; slot < this.descBuffers.length; slot++) {
            if (this.descBuffers[slot] != null) {
                this.descBuffers[slot].delete();
                this.descBuffers[slot] = null;
            }
            if (this.factorBaseBuffers[slot] != null) {
                this.factorBaseBuffers[slot].delete();
                this.factorBaseBuffers[slot] = null;
            }
            this.slotSnapshots[slot] = null;
        }
        if (this.factorBuffer != null) {
            this.factorBuffer.delete();
            this.factorBuffer = null;
        }
        if (this.sortedBuffer != null) {
            this.sortedBuffer.delete();
            this.sortedBuffer = null;
        }
        // After every engine: the pack arena buffers are the pack engines'
        // vertex buffers, so they are freed once no pack engine can bind them.
        for (VulkanHostBuffer buffer : this.packArenaBuffers.values()) {
            buffer.delete();
        }
        this.packArenaBuffers.clear();
        // The shared index buffer is read by every pass' draws until the
        // engines are gone, so it goes with the other shared resources.
        if (this.sharedIndexBuffer != null) {
            this.sharedIndexBuffer.delete();
            this.sharedIndexBuffer = null;
        }
        // Step 5, last: the shared allocator's slabs are the engines' slab
        // buffers' memory, so they are freed after every buffer that sub-
        // allocates from them (its delete is teardown, freeing live slabs
        // outright). The gate above is what keeps step 4's deletes from
        // escaping into the context's retire list through a host buffer's
        // recording-open branch; a retire here would mean the gate itself is
        // broken, so it is recorded as one (T3 design section 5.2).
        if (this.geometrySlabAllocator != null) {
            this.geometrySlabAllocator.delete();
            this.geometrySlabAllocator = null;
        }
        int retiredSlabAfter = context.celtium$retiredSlabBufferCount();
        if (retiredSlabAfter > retiredSlabBefore) {
            System.out.println("[Celtium] CELTIUM_TERRAIN_A_GATE_VIOLATION epoch=" + (this.worldEpoch - 1)
                    + " retiredSlabBuffers " + retiredSlabBefore + " -> " + retiredSlabAfter
                    + ": a shared buffer escaped into the retire list during a gate-passed delete");
        }
        this.worldTornDown = true;
        return true;
    }
}
