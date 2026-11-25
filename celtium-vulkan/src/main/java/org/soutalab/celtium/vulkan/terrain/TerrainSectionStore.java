package org.soutalab.celtium.vulkan.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        public final TerrainSlabAllocator.Allocation sorted;
        public final AsyncSectionMesher.BuiltSectionA built;

        private Registration(Key key, SectionDesc desc, int recordIndex,
                             TerrainSlabAllocator.Allocation geometry,
                             TerrainSlabAllocator.Allocation quadSpace,
                             TerrainSlabAllocator.Allocation factor,
                             TerrainSlabAllocator.Allocation sorted,
                             AsyncSectionMesher.BuiltSectionA built) {
            this.key = key;
            this.desc = desc;
            this.recordIndex = recordIndex;
            this.geometry = geometry;
            this.quadSpace = quadSpace;
            this.factor = factor;
            this.sorted = sorted;
            this.built = built;
        }

        /** Factor-stream base in uints, as the vertex shader consumes it. */
        public long factorBaseUints() {
            return this.factor.byteOffset / 4;
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

    private final List<Registration> records = new ArrayList<>();
    private final HashMap<Key, Registration> byKey = new HashMap<>();
    private int[] descImage = new int[0];
    private int[] factorBaseImage = new int[0];
    private boolean imageDirty = true;
    private long imageGeneration;

    /**
     * Registers one built section, assigning its slab placement.
     *
     * @return the registration, or {@code null} when the section is Tier-A ineligible
     *         (oversize) or an arena is exhausted; on failure nothing is allocated
     */
    public synchronized Registration register(AsyncSectionMesher.BuiltSectionA built, long generation) {
        Key key = new Key(built.sectionX, built.sectionY, built.sectionZ, built.passOrdinal);
        if (this.byKey.containsKey(key)) {
            throw new IllegalStateException("section already registered: " + key);
        }
        if (built.oversize || built.descDraft.oversize()) {
            return null;
        }
        long vertexBytes = (long) built.totalQuads * 4 * 16;
        TerrainSlabAllocator.Allocation geometry = this.geometryAllocator.allocate(vertexBytes);
        if (geometry == null) {
            return null;
        }
        TerrainSlabAllocator.Allocation quadSpace = this.quadSpaceAllocator.allocate(built.totalQuads);
        TerrainSlabAllocator.Allocation factor = null;
        TerrainSlabAllocator.Allocation sorted = null;
        boolean ok = quadSpace != null && quadSpace.slabId == geometry.slabId;
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
            return null;
        }

        SectionDesc desc = built.descDraft;
        desc.slabId = geometry.slabId;
        desc.vertexOffset = geometry.byteOffset / 16;
        desc.quadStart = quadSpace.byteOffset;
        desc.generation = generation;
        if (sorted != null) {
            desc.sortedIndexBase = sorted.byteOffset / 4;
        }
        Registration registration = new Registration(key, desc, this.records.size(),
                geometry, quadSpace, factor, sorted, built);
        this.records.add(registration);
        this.byKey.put(key, registration);
        this.imageDirty = true;
        this.imageGeneration++;
        return registration;
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
        this.geometryAllocator.freeDeferred(removed.geometry, releaseTimeline);
        this.quadSpaceAllocator.freeDeferred(removed.quadSpace, releaseTimeline);
        this.factorAllocator.freeDeferred(removed.factor, releaseTimeline);
        if (removed.sorted != null) {
            this.sortedAllocator.freeDeferred(removed.sorted, releaseTimeline);
        }
        this.imageDirty = true;
        this.imageGeneration++;
        return removed;
    }

    /** Returns deferred arena ranges whose timeline has completed. */
    public synchronized void collectReleased(long completedTimeline) {
        this.geometryAllocator.collectReleased(completedTimeline);
        this.quadSpaceAllocator.collectReleased(completedTimeline);
        this.factorAllocator.collectReleased(completedTimeline);
        this.sortedAllocator.collectReleased(completedTimeline);
    }

    public synchronized Registration get(Key key) {
        return this.byKey.get(key);
    }

    public synchronized int recordCount() {
        return this.records.size();
    }

    /** Live record counts per geometry slab id, sized to the slab count. */
    public synchronized int[] slabRecordCounts() {
        int[] counts = new int[Math.max(1, this.geometryAllocator.slabCount())];
        for (Registration registration : this.records) {
            int slab = (int) registration.desc.slabId;
            if (slab >= counts.length) {
                counts = java.util.Arrays.copyOf(counts, slab + 1);
            }
            counts[slab]++;
        }
        return counts;
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

    public TerrainSlabAllocator sortedAllocator() {
        return this.sortedAllocator;
    }
}
