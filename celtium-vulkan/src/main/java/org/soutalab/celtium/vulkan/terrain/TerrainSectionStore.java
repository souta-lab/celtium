package org.soutalab.celtium.vulkan.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The resident SectionDesc table of the new architecture: every loaded, non-empty
 * (section, pass) owns one record here, with its geometry placed in the fixed slabs.
 * The store is pure CPU state — it packs the desc SSBO image and a parallel
 * factor-base image, and the GPU side ({@link TerrainEngineA}) uploads them and the
 * geometry bytes. Four arenas are coordinated:
 *
 * <ul>
 *   <li><b>geometry</b>: 64 MiB slabs of 16-byte vertices, 4 KiB pages.</li>
 *   <li><b>quad space</b>: per-slab quad indices (the shared static quad index buffer
 *       is addressed by slab-local quad), 4096-quad pages. Geometry and quad space are
 *       always allocated in the same slab; the desc's command vertex offset absorbs
 *       the difference between the two coordinate systems.</li>
 *   <li><b>factor</b>: the per-quad correction stream, 4 bytes per quad; the desc has
 *       no field for it, so a parallel uint-per-record image carries each record's
 *       factor base (in uints) for the vertex shader.</li>
 *   <li><b>sorted index</b>: one 32 MiB arena of uint32 indices for sorted passes;
 *       the desc's {@code sortedIndexBase} is in indices.</li>
 * </ul>
 *
 * Removal is swap-with-last in the desc image, and arena ranges are freed deferred
 * against the frame timeline (see {@link TerrainSlabAllocator}).
 */
public final class TerrainSectionStore {
    /** Quad-space capacity of one slab: a full 64 MiB geometry slab of 64-byte quads. */
    public static final int MAX_QUADS_PER_SLAB = 1_048_576;
    private static final long SORTED_ARENA_BYTES = 32L * 1024 * 1024;
    /** One arena (not slabbed): the vertex shader binds a single factor buffer. */
    private static final long FACTOR_ARENA_BYTES = 32L * 1024 * 1024;

    public record Key(int sectionX, int sectionY, int sectionZ, int passOrdinal) {
    }

    /** A live record: its desc (slab placement assigned) plus its arena allocations. */
    public static final class Registration {
        public final Key key;
        public final SectionDesc desc;
        /** Index of this record in the packed desc image; changes on swap-removals. */
        public int recordIndex;
        public final TerrainSlabAllocator.Allocation geometry;
        public final TerrainSlabAllocator.Allocation quadSpace;
        public final TerrainSlabAllocator.Allocation factor;
        /**
         * Pack-space placement (R-17 candidate 2-MIN), non-null only for
         * records whose presented format is foreign: the geometry lives in a
         * per-stride byte arena instead of the 16-byte slabs, and no factor
         * area is allocated ({@link #factor} stays null — the foreign stream
         * is self-contained). {@link #geometry}/{@link #quadSpace} are null
         * for these records.
         */
        public final TerrainSlabAllocator.Allocation packGeometry;
        public final TerrainSlabAllocator.Allocation packQuadSpace;
        /** Mutable: a translucent re-sort may replace the range (see {@link #updateSorted}). */
        public TerrainSlabAllocator.Allocation sorted;
        public final AsyncSectionMesher.BuiltSectionA built;

        private Registration(Key key, SectionDesc desc, int recordIndex,
                             TerrainSlabAllocator.Allocation geometry,
                             TerrainSlabAllocator.Allocation quadSpace,
                             TerrainSlabAllocator.Allocation factor,
                             TerrainSlabAllocator.Allocation sorted,
                             AsyncSectionMesher.BuiltSectionA built,
                             TerrainSlabAllocator.Allocation packGeometry,
                             TerrainSlabAllocator.Allocation packQuadSpace) {
            this.key = key;
            this.desc = desc;
            this.recordIndex = recordIndex;
            this.geometry = geometry;
            this.quadSpace = quadSpace;
            this.factor = factor;
            this.sorted = sorted;
            this.built = built;
            this.packGeometry = packGeometry;
            this.packQuadSpace = packQuadSpace;
        }

        /** True when this record's geometry lives in a pack (foreign-format) arena. */
        public boolean pack() {
            return this.packGeometry != null;
        }

        /** Factor-stream base in uints, as the vertex shader consumes it. */
        public long factorBaseUints() {
            return this.factor == null ? 0 : this.factor.byteOffset / 4;
        }
    }

    private final TerrainSlabAllocator geometryAllocator =
            new TerrainSlabAllocator(TerrainSlabAllocator.DEFAULT_SLAB_BYTES, TerrainSlabAllocator.DEFAULT_MAX_SLABS);
    private final TerrainSlabAllocator quadSpaceAllocator =
            new TerrainSlabAllocator(MAX_QUADS_PER_SLAB, TerrainSlabAllocator.DEFAULT_MAX_SLABS, 4096);
    private final TerrainSlabAllocator factorAllocator =
            new TerrainSlabAllocator(FACTOR_ARENA_BYTES, 1);
    private final TerrainSlabAllocator sortedAllocator =
            new TerrainSlabAllocator(SORTED_ARENA_BYTES, 1);

    /**
     * Desc slab ids at and above this base belong to the pack space, so the
     * vanilla engines' per-slab dispatch never matches a pack record. Pack
     * slab id = {@code PACK_SLAB_BASE + arenaIndex * DEFAULT_MAX_SLABS + slab}.
     */
    public static final int PACK_SLAB_BASE = TerrainSlabAllocator.DEFAULT_MAX_SLABS;
    /** Distinct presented strides the pack space will open arenas for. */
    public static final int MAX_PACK_ARENAS = 4;

    /**
     * One pack arena pair for a single presented stride: a byte-unit geometry
     * arena whose page is a whole number of vertices ({@code stride * 256}),
     * so every allocation offset is an exact vertex offset at this stride, and
     * a quad-unit space paired with it slab by slab, exactly like the vanilla
     * pairing. Capacity management closes inside the arena: no other format's
     * records ever land here.
     */
    public static final class PackArena {
        public final int index;
        public final int stride;
        public final TerrainSlabAllocator geometry;
        public final TerrainSlabAllocator quadSpace;

        private PackArena(int index, int stride) {
            this.index = index;
            this.stride = stride;
            int page = stride * 256;
            long slabBytes = TerrainSlabAllocator.DEFAULT_SLAB_BYTES / page * page;
            this.geometry = new TerrainSlabAllocator(slabBytes,
                    TerrainSlabAllocator.DEFAULT_MAX_SLABS, page);
            this.quadSpace = new TerrainSlabAllocator(MAX_QUADS_PER_SLAB,
                    TerrainSlabAllocator.DEFAULT_MAX_SLABS, 4096);
        }

        /** Byte size of one geometry slab in this arena (the GPU buffer size). */
        public long slabBytes() {
            return this.geometry.slabBytes();
        }
    }

    private final java.util.Map<Integer, PackArena> packArenasByStride = new HashMap<>();
    private final List<PackArena> packArenas = new ArrayList<>();

    private final List<Registration> records = new ArrayList<>();
    private final HashMap<Key, Registration> byKey = new HashMap<>();
    /** Keys with an allocated-but-unpublished record (the two-phase form, {@link #allocateDiagnosed}). */
    private final Set<Key> pendingKeys = new HashSet<>();
    private int[] descImage = new int[0];
    private int[] factorBaseImage = new int[0];
    private boolean imageDirty = true;
    private long imageGeneration;

    /** Why a registration was refused: the distinction the residency gate counts by. */
    public enum RegisterFailure { OVERSIZE, ARENA_FULL }

    /** The outcome of a diagnosed registration: exactly one of the two fields is non-null. */
    public record RegisterResult(Registration registration, RegisterFailure failure) {
    }

    /**
     * An allocated-but-unpublished record (the two-phase registration form,
     * T3 design section 4.2): its arena ranges are reserved and its desc
     * placement is assigned, but it is in neither the record list nor the
     * packed image, so no snapshot can name it. Exactly one of
     * {@link #publish} / {@link #discardUnpublished} settles it.
     */
    public static final class PendingRegistration {
        private final Registration registration;
        /** The pack arena holding the ranges, or {@code null} for a vanilla record. */
        private final PackArena packArena;
        private boolean settled;

        private PendingRegistration(Registration registration, PackArena packArena) {
            this.registration = registration;
            this.packArena = packArena;
        }

        /** The record being placed; its {@code recordIndex} is assigned at publish. */
        public Registration registration() {
            return this.registration;
        }
    }

    /** The outcome of a diagnosed allocation: exactly one of the two fields is non-null. */
    public record AllocationResult(PendingRegistration pending, RegisterFailure failure) {
    }

    /**
     * Registers one built section, assigning its slab placement.
     *
     * @return the registration, or {@code null} when the section is Tier-A ineligible
     *         (oversize) or an arena is exhausted; on failure nothing is allocated
     */
    public synchronized Registration register(AsyncSectionMesher.BuiltSectionA built, long generation) {
        return registerDiagnosed(built, generation).registration();
    }

    /**
     * {@link #register} with the refusal reason named, for the residency gate's
     * leak counters (order (i)): oversize and arena exhaustion used to be the
     * same silent {@code null}.
     */
    public synchronized RegisterResult registerDiagnosed(AsyncSectionMesher.BuiltSectionA built, long generation) {
        AllocationResult allocated = allocateDiagnosed(built, generation);
        if (allocated.pending() == null) {
            return new RegisterResult(null, allocated.failure());
        }
        return new RegisterResult(publish(allocated.pending()), null);
    }

    /**
     * Phase one of the two-phase registration: reserves the record's arena
     * ranges and assigns its desc placement, without publishing it — the
     * record enters neither the record list nor the packed image, so a
     * snapshot taken before {@link #publish} cannot name ranges whose bytes
     * are not yet written. The caller writes the bytes, then publishes; on
     * any failure in between it discards instead (see
     * {@link #discardUnpublished}).
     */
    public synchronized AllocationResult allocateDiagnosed(AsyncSectionMesher.BuiltSectionA built, long generation) {
        Key key = new Key(built.sectionX, built.sectionY, built.sectionZ, built.passOrdinal);
        if (this.byKey.containsKey(key) || this.pendingKeys.contains(key)) {
            throw new IllegalStateException("section already registered: " + key);
        }
        if (built.oversize || built.descDraft.oversize()) {
            return new AllocationResult(null, RegisterFailure.OVERSIZE);
        }
        AllocationResult result = built.stride != 16
                ? allocatePack(built, key, generation)
                : allocateVanilla(built, key, generation);
        if (result.pending() != null) {
            this.pendingKeys.add(key);
        }
        return result;
    }

    /**
     * Phase two: makes an allocated record visible — it joins the record
     * list and the packed image (the image dirties and its generation
     * advances). Called only after the record's bytes are fully written.
     */
    public synchronized Registration publish(PendingRegistration pending) {
        if (pending.settled) {
            throw new IllegalStateException("pending registration already settled: "
                    + pending.registration.key);
        }
        pending.settled = true;
        Registration registration = pending.registration;
        registration.recordIndex = this.records.size();
        this.records.add(registration);
        this.byKey.put(registration.key, registration);
        this.pendingKeys.remove(registration.key);
        this.imageDirty = true;
        this.imageGeneration++;
        return registration;
    }

    /**
     * Rolls an unpublished allocation back: every range returns to its
     * arena <em>immediately</em>, not deferred against the timeline. That
     * is safe for exactly this case — the record was never published, so
     * no snapshot names its ranges and no submission can read them — and
     * it is the one immediate-free the two-phase form licenses (the
     * INV-1a exception of the T3 design, section 4.2).
     */
    public synchronized void discardUnpublished(PendingRegistration pending) {
        if (pending.settled) {
            throw new IllegalStateException("pending registration already settled: "
                    + pending.registration.key);
        }
        pending.settled = true;
        Registration registration = pending.registration;
        this.pendingKeys.remove(registration.key);
        if (registration.pack()) {
            pending.packArena.geometry.free(registration.packGeometry);
            pending.packArena.quadSpace.free(registration.packQuadSpace);
        } else {
            this.geometryAllocator.free(registration.geometry);
            this.quadSpaceAllocator.free(registration.quadSpace);
            if (registration.factor != null) {
                this.factorAllocator.free(registration.factor);
            }
        }
        if (registration.sorted != null) {
            this.sortedAllocator.free(registration.sorted);
        }
    }

    /** The vanilla-space half of {@link #allocateDiagnosed}: paired slab placement, unpublished. */
    private AllocationResult allocateVanilla(AsyncSectionMesher.BuiltSectionA built,
                                             Key key, long generation) {
        long vertexBytes = (long) built.totalQuads * 4 * 16;
        // Geometry and quad space must land in the same slab, so they are placed
        // as a pair, slab by slab. Two independent first-fit allocations diverge
        // once the quad space's 4096-quad pages fill a slab ahead of the geometry
        // arena (a 1,024-quad section reserves a whole quad page but only 64 KiB
        // of geometry): measured on the pre-fix store, registration stopped at
        // 256 small sections with 16 MiB of geometry live in a single slab —
        // ARENA_FULL at 3% of the designed quad capacity (TerrainArenaCapacityTest).
        TerrainSlabAllocator.Allocation geometry = null;
        TerrainSlabAllocator.Allocation quadSpace = null;
        for (int slab = 0; slab < this.geometryAllocator.maxSlabs(); slab++) {
            geometry = this.geometryAllocator.allocateInSlab(slab, vertexBytes);
            if (geometry == null) {
                continue;
            }
            quadSpace = this.quadSpaceAllocator.allocateInSlab(slab, built.totalQuads);
            if (quadSpace != null) {
                break;
            }
            this.geometryAllocator.free(geometry);
            geometry = null;
        }
        if (geometry == null) {
            return new AllocationResult(null, RegisterFailure.ARENA_FULL);
        }
        TerrainSlabAllocator.Allocation factor = null;
        TerrainSlabAllocator.Allocation sorted = null;
        boolean ok = true;
        if (ok) {
            factor = this.factorAllocator.allocate((long) built.totalQuads * 4);
            ok = factor != null;
        }
        if (ok && built.sortedIndices != null) {
            sorted = this.sortedAllocator.allocate(built.sortedIndices.getLength());
            ok = sorted != null;
        }
        if (!ok) {
            if (geometry != null) this.geometryAllocator.free(geometry);
            if (quadSpace != null) this.quadSpaceAllocator.free(quadSpace);
            if (factor != null) this.factorAllocator.free(factor);
            if (sorted != null) this.sortedAllocator.free(sorted);
            return new AllocationResult(null, RegisterFailure.ARENA_FULL);
        }

        SectionDesc desc = built.descDraft;
        desc.slabId = geometry.slabId;
        desc.vertexOffset = geometry.byteOffset / 16;
        desc.quadStart = quadSpace.byteOffset;
        desc.generation = generation;
        if (sorted != null) {
            desc.sortedIndexBase = sorted.byteOffset / 4;
        }
        Registration registration = new Registration(key, desc, -1,
                geometry, quadSpace, factor, sorted, built, null, null);
        return new AllocationResult(new PendingRegistration(registration, null), null);
    }

    /**
     * The pack-space half of {@link #allocateDiagnosed} (R-17 candidate
     * 2-MIN): a foreign-format section is placed in its stride's own arena
     * pair, byte-for-byte as presented. No factor area is allocated (the
     * stream is self-contained) and the 16-byte slabs are never touched, so
     * the Tier-A invariant — the main slab holds vanilla-format geometry
     * only — is kept by construction.
     */
    private AllocationResult allocatePack(AsyncSectionMesher.BuiltSectionA built,
                                          Key key, long generation) {
        PackArena arena = this.packArenasByStride.get(built.stride);
        if (arena == null) {
            if (this.packArenas.size() >= MAX_PACK_ARENAS) {
                return new AllocationResult(null, RegisterFailure.ARENA_FULL);
            }
            arena = new PackArena(this.packArenas.size(), built.stride);
            this.packArenasByStride.put(built.stride, arena);
            this.packArenas.add(arena);
        }
        long vertexBytes = (long) built.totalQuads * 4 * built.stride;
        TerrainSlabAllocator.Allocation geometry = null;
        TerrainSlabAllocator.Allocation quadSpace = null;
        for (int slab = 0; slab < arena.geometry.maxSlabs(); slab++) {
            geometry = arena.geometry.allocateInSlab(slab, vertexBytes);
            if (geometry == null) {
                continue;
            }
            quadSpace = arena.quadSpace.allocateInSlab(slab, built.totalQuads);
            if (quadSpace != null) {
                break;
            }
            arena.geometry.free(geometry);
            geometry = null;
        }
        if (geometry == null) {
            return new AllocationResult(null, RegisterFailure.ARENA_FULL);
        }
        TerrainSlabAllocator.Allocation sorted = null;
        if (built.sortedIndices != null) {
            sorted = this.sortedAllocator.allocate(built.sortedIndices.getLength());
            if (sorted == null) {
                arena.geometry.free(geometry);
                arena.quadSpace.free(quadSpace);
                return new AllocationResult(null, RegisterFailure.ARENA_FULL);
            }
        }
        SectionDesc desc = built.descDraft;
        desc.slabId = PACK_SLAB_BASE + arena.index * TerrainSlabAllocator.DEFAULT_MAX_SLABS
                + geometry.slabId;
        desc.vertexOffset = geometry.byteOffset / built.stride;
        desc.quadStart = quadSpace.byteOffset;
        desc.generation = generation;
        if (sorted != null) {
            desc.sortedIndexBase = sorted.byteOffset / 4;
        }
        Registration registration = new Registration(key, desc, -1,
                null, null, null, sorted, built, geometry, quadSpace);
        return new AllocationResult(new PendingRegistration(registration, arena), null);
    }

    /**
     * Removes a record, deferring its arena ranges to {@code releaseTimeline}.
     *
     * @return the removed registration, or {@code null} when the key is unknown
     */
    public synchronized Registration unregister(Key key, long releaseTimeline) {
        Registration removed = this.byKey.remove(key);
        if (removed == null) {
            return null;
        }
        int index = removed.recordIndex;
        Registration last = this.records.remove(this.records.size() - 1);
        if (last != removed) {
            this.records.set(index, last);
            last.recordIndex = index;
        }
        if (removed.pack()) {
            PackArena arena = packArenaOfSlab((int) removed.desc.slabId);
            arena.geometry.freeDeferred(removed.packGeometry, releaseTimeline);
            arena.quadSpace.freeDeferred(removed.packQuadSpace, releaseTimeline);
        } else {
            this.geometryAllocator.freeDeferred(removed.geometry, releaseTimeline);
            this.quadSpaceAllocator.freeDeferred(removed.quadSpace, releaseTimeline);
            this.factorAllocator.freeDeferred(removed.factor, releaseTimeline);
        }
        if (removed.sorted != null) {
            this.sortedAllocator.freeDeferred(removed.sorted, releaseTimeline);
        }
        this.imageDirty = true;
        this.imageGeneration++;
        return removed;
    }

    public enum SortedUpdate { NOT_REGISTERED, IN_PLACE, RESIZED, ARENA_FULL }

    /**
     * Re-points one record's sorted-index range at a re-sorted index buffer of
     * {@code byteSize} bytes (translucent re-sort, wave-2 item 4). Same size keeps
     * the existing allocation ({@link SortedUpdate#IN_PLACE}); a different size
     * frees the old range deferred and allocates a new one, updating the desc's
     * {@code sortedIndexBase} ({@link SortedUpdate#RESIZED}). The caller re-uploads
     * the bytes at the resulting allocation either way.
     */
    public synchronized SortedUpdate updateSorted(Key key, long byteSize, long releaseTimeline) {
        Registration registration = this.byKey.get(key);
        if (registration == null) {
            return SortedUpdate.NOT_REGISTERED;
        }
        if (registration.sorted != null && registration.sorted.byteSize == byteSize) {
            return SortedUpdate.IN_PLACE;
        }
        TerrainSlabAllocator.Allocation next = this.sortedAllocator.allocate(byteSize);
        if (next == null) {
            return SortedUpdate.ARENA_FULL;
        }
        if (registration.sorted != null) {
            this.sortedAllocator.freeDeferred(registration.sorted, releaseTimeline);
        }
        registration.sorted = next;
        registration.desc.sortedIndexBase = next.byteOffset / 4;
        this.imageDirty = true;
        this.imageGeneration++;
        return SortedUpdate.RESIZED;
    }

    /** Returns deferred arena ranges whose timeline has completed. */
    public synchronized void collectReleased(long completedTimeline) {
        this.geometryAllocator.collectReleased(completedTimeline);
        this.quadSpaceAllocator.collectReleased(completedTimeline);
        this.factorAllocator.collectReleased(completedTimeline);
        this.sortedAllocator.collectReleased(completedTimeline);
        for (PackArena arena : this.packArenas) {
            arena.geometry.collectReleased(completedTimeline);
            arena.quadSpace.collectReleased(completedTimeline);
        }
    }

    public synchronized Registration get(Key key) {
        return this.byKey.get(key);
    }

    public synchronized int recordCount() {
        return this.records.size();
    }

    /**
     * Live record counts per geometry slab id, sized to the slab count.
     * Pack-space records are excluded: their desc slab ids address the pack
     * arenas, and counting them here would make the vanilla engines dispatch
     * phantom slabs and create real slab buffers for them.
     */
    public synchronized int[] slabRecordCounts() {
        int[] counts = new int[Math.max(1, this.geometryAllocator.slabCount())];
        for (Registration registration : this.records) {
            if (registration.pack()) {
                continue;
            }
            int slab = (int) registration.desc.slabId;
            if (slab >= counts.length) {
                counts = java.util.Arrays.copyOf(counts, slab + 1);
            }
            counts[slab]++;
        }
        return counts;
    }

    /** Live pack-space record counts, indexed by desc slab id (pack range only). */
    public synchronized int[] packSlabRecordCounts() {
        int size = PACK_SLAB_BASE + Math.max(1, this.packArenas.size())
                * TerrainSlabAllocator.DEFAULT_MAX_SLABS;
        int[] counts = new int[size];
        for (Registration registration : this.records) {
            if (registration.pack()) {
                counts[(int) registration.desc.slabId]++;
            }
        }
        return counts;
    }

    /** The pack records of one pass, in record order. */
    public synchronized List<Registration> packRecords(int passOrdinal) {
        List<Registration> out = new ArrayList<>();
        for (Registration registration : this.records) {
            if (registration.pack() && registration.key.passOrdinal() == passOrdinal) {
                out.add(registration);
            }
        }
        return out;
    }

    /**
     * Whether one pass holds any live pack-space records. The lightweight
     * form of {@link #packRecords(int)} for the terrain pass entry's gates:
     * the entry asks this per pass per frame, so no list is built.
     */
    public synchronized boolean hasPackRecords(int passOrdinal) {
        for (Registration registration : this.records) {
            if (registration.pack() && registration.key.passOrdinal() == passOrdinal) {
                return true;
            }
        }
        return false;
    }

    /** The pack arena a desc slab id belongs to, or null outside the pack range. */
    public synchronized PackArena packArenaOfSlab(int descSlabId) {
        int arenaIndex = (descSlabId - PACK_SLAB_BASE) / TerrainSlabAllocator.DEFAULT_MAX_SLABS;
        if (descSlabId < PACK_SLAB_BASE || arenaIndex >= this.packArenas.size()) {
            return null;
        }
        return this.packArenas.get(arenaIndex);
    }

    /** The pack arena opened for a presented stride, or null when none was. */
    public synchronized PackArena packArenaForStride(int stride) {
        return this.packArenasByStride.get(stride);
    }

    public synchronized List<Registration> records() {
        return List.copyOf(this.records);
    }

    /** Bumped on every mutation; the GPU side re-uploads the images when it moves. */
    public synchronized long imageGeneration() {
        return this.imageGeneration;
    }

    /** The packed SectionDesc image: {@link #recordCount()} * 12 words. */
    public synchronized int[] descImage() {
        packIfDirty();
        return this.descImage;
    }

    /** The parallel factor-base image: one uint per record, same order as the descs. */
    public synchronized int[] factorBaseImage() {
        packIfDirty();
        return this.factorBaseImage;
    }

    private void packIfDirty() {
        if (!this.imageDirty) {
            return;
        }
        int count = this.records.size();
        if (this.descImage.length != count * SectionDesc.WORDS) {
            this.descImage = new int[count * SectionDesc.WORDS];
            this.factorBaseImage = new int[count];
        }
        for (int i = 0; i < count; i++) {
            Registration registration = this.records.get(i);
            registration.desc.pack(this.descImage, i * SectionDesc.WORDS);
            this.factorBaseImage[i] = (int) registration.factorBaseUints();
        }
        this.imageDirty = false;
    }

    public TerrainSlabAllocator geometryAllocator() {
        return this.geometryAllocator;
    }

    public TerrainSlabAllocator quadSpaceAllocator() {
        return this.quadSpaceAllocator;
    }

    public TerrainSlabAllocator factorAllocator() {
        return this.factorAllocator;
    }

    public TerrainSlabAllocator sortedAllocator() {
        return this.sortedAllocator;
    }
}
