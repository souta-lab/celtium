package org.soutalab.celtium.vulkan.terrain;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * CPU-side resident directory for GPU {@link SectionDesc} records and fixed-slab geometry
 * sub-allocations ({@code CELTIUM-NEW-ARCH-2026-11-25.md} §2.2–2.3,
 * {@code SECTION-DESC-SPEC-2026-11-25.md} Step 3).
 *
 * <p>Design guarantees:
 * <ul>
 *   <li>Zero reverse dependency on legacy {@code VulkanTerrainGpuBackend} or {@code VulkanArena}.</li>
 *   <li>Plugs directly into {@code arch-hal}'s fixed 64 MiB {@code SlabAllocator} via the minimal
 *       {@link SlabPool} interface; ships with {@link FreeListSlabPool} (64 MiB slabs, 4 KiB page
 *       free-list, zero grow-and-copy, timeline-gated deferred free) for immediate integration and tests.</li>
 *   <li>Maintains a dense packed {@code SectionDesc[]} array with O(1) swap-remove on unload and
 *       tracks the dirty slot range {@code [dirtyMinSlot, dirtyMaxSlot]} for minimal SSBO uploads.</li>
 * </ul>
 */
public final class TerrainSectionStore {
    /** Fixed terrain geometry slab size: 64 MiB (spec §2.3). */
    public static final int DEFAULT_SLAB_BYTES = 64 * 1024 * 1024;
    /** Page granularity within each terrain slab: 4 KiB (spec §2.3). */
    public static final int DEFAULT_PAGE_BYTES = 4 * 1024;
    /** Maximum terrain geometry slabs (spec §2.3: 4..8 slabs). */
    public static final int DEFAULT_MAX_SLABS = 8;

    /**
     * Sub-allocation handle inside a fixed-size terrain geometry slab.
     * {@code byteOffset} is always a multiple of {@link AsyncSectionMesher#QUAD_BYTES} (64 B).
     */
    public record SlabSlice(int slabId, int byteOffset, int byteLength) {
        public int vertexOffset() {
            return byteOffset / TerrainVertex16.STRIDE;
        }

        public int quadStart() {
            return byteOffset / AsyncSectionMesher.QUAD_BYTES;
        }
    }

    /**
     * Minimal slab sub-allocation contract so {@code arch-hal}'s {@code SlabAllocator}
     * can back {@link TerrainSectionStore} without coupling terrain code to Vulkan handles.
     */
    public interface SlabPool {
        SlabSlice allocate(int byteSize);

        void retire(SlabSlice slice, long retireAfterTimeline);

        int reclaimCompleted(long completedTimeline);

        int liveSlabCount();
    }

    /**
     * Packs {@code (sectionX, sectionY, sectionZ, passOrdinal)} into a unique 64-bit key.
     * Supports sectionX/Z in [-2^23, 2^23-1] (world coords ±134M blocks), sectionY in [-128, 127],
     * passOrdinal in [0, 255].
     */
    public static long sectionPassKey(int sectionX, int sectionY, int sectionZ, int passOrdinal) {
        long x = ((long) sectionX) & 0xFFFFFFL;
        long z = ((long) sectionZ) & 0xFFFFFFL;
        long y = ((long) sectionY) & 0xFFL;
        long p = ((long) passOrdinal) & 0xFFL;
        return x | (z << 24) | (y << 48) | (p << 56);
    }

    private static final class ResidentEntry {
        final long key;
        int slot;
        SlabSlice slice;
        SectionDesc.Record desc;

        ResidentEntry(long key, int slot, SlabSlice slice, SectionDesc.Record desc) {
            this.key = key;
            this.slot = slot;
            this.slice = slice;
            this.desc = desc;
        }
    }

    private final SlabPool slabPool;
    private final HashMap<Long, ResidentEntry> entriesByKey = new HashMap<>();
    private final ArrayList<ResidentEntry> denseEntries = new ArrayList<>();
    private int[] packedDescs;
    private int dirtyMinSlot = Integer.MAX_VALUE;
    private int dirtyMaxSlot = -1;
    private int nextGeneration = 1;

    public TerrainSectionStore(SlabPool slabPool, int initialRecordCapacity) {
        this.slabPool = Objects.requireNonNull(slabPool, "slabPool");
        int cap = Math.max(16, initialRecordCapacity);
        this.packedDescs = new int[cap * SectionDesc.STRIDE_INTS];
    }

    public int activeRecordCount() {
        return denseEntries.size();
    }

    /** Upper bound on VkDrawIndexedIndirectCommand entries required for all active records (spec: records * 7). */
    public int requiredIndirectCommandCapacity() {
        return denseEntries.size() * SectionDesc.MAX_INDIRECT_COMMANDS_PER_RECORD;
    }

    public int liveSlabCount() {
        return slabPool.liveSlabCount();
    }

    public boolean hasDirtyRange() {
        return dirtyMaxSlot >= dirtyMinSlot && dirtyMinSlot < denseEntries.size();
    }

    public int dirtyMinSlot() {
        return dirtyMinSlot;
    }

    public int dirtyMaxSlot() {
        return Math.min(dirtyMaxSlot, denseEntries.size() - 1);
    }

    /**
     * Installs or replaces a resident section+pass record, allocating a new slab slice and
     * timeline-retiring any previously held slice for the same key.
     */
    public SlabSlice upsert(
            int sectionX, int sectionY, int sectionZ,
            int passOrdinal,
            int[] bucketCounts,
            int byteSize,
            boolean animated,
            int sortedIndexBase,
            long retireTimelineForReplaced
    ) {
        if (byteSize <= 0) {
            throw new IllegalArgumentException("Empty section must be removed, not upserted: byteSize=" + byteSize);
        }
        long key = sectionPassKey(sectionX, sectionY, sectionZ, passOrdinal);
        SlabSlice newSlice = slabPool.allocate(byteSize);
        int generation = nextGeneration++;
        SectionDesc.Record desc = new SectionDesc.Builder()
                .section(sectionX, sectionY, sectionZ)
                .allocation(newSlice.slabId(), newSlice.vertexOffset(), newSlice.quadStart())
                .bucketCounts(bucketCounts)
                .passOrdinal(passOrdinal)
                .animated(animated)
                .generation(generation)
                .sortedIndexBase(sortedIndexBase)
                .build();

        ResidentEntry existing = entriesByKey.get(key);
        if (existing != null) {
            slabPool.retire(existing.slice, retireTimelineForReplaced);
            existing.slice = newSlice;
            existing.desc = desc;
            writeSlot(existing.slot, desc);
        } else {
            int slot = denseEntries.size();
            ensureRecordCapacity(slot + 1);
            ResidentEntry created = new ResidentEntry(key, slot, newSlice, desc);
            denseEntries.add(created);
            entriesByKey.put(key, created);
            writeSlot(slot, desc);
        }
        return newSlice;
    }

    /**
     * Removes a section+pass record in O(1) via dense swap-remove and retires its slab slice.
     */
    public boolean remove(
            int sectionX, int sectionY, int sectionZ,
            int passOrdinal,
            long retireTimeline
    ) {
        long key = sectionPassKey(sectionX, sectionY, sectionZ, passOrdinal);
        ResidentEntry removed = entriesByKey.remove(key);
        if (removed == null) {
            return false;
        }
        slabPool.retire(removed.slice, retireTimeline);

        int removedSlot = removed.slot;
        int lastSlot = denseEntries.size() - 1;
        if (removedSlot != lastSlot) {
            ResidentEntry moved = denseEntries.get(lastSlot);
            moved.slot = removedSlot;
            denseEntries.set(removedSlot, moved);
            writeSlot(removedSlot, moved.desc);
        }
        denseEntries.remove(lastSlot);
        if (denseEntries.isEmpty()) {
            clearDirtyRange();
        }
        return true;
    }

    public SectionDesc.Record getRecordAtSlot(int slot) {
        return denseEntries.get(slot).desc;
    }

    public SectionDesc.Record getRecord(int sectionX, int sectionY, int sectionZ, int passOrdinal) {
        ResidentEntry entry = entriesByKey.get(sectionPassKey(sectionX, sectionY, sectionZ, passOrdinal));
        return entry != null ? entry.desc : null;
    }

    /**
     * Copies the dirty slot range {@code [dirtyMinSlot, dirtyMaxSlot]} into a staging ByteBuffer
     * at {@code dstByteOffset} and resets the dirty markers. Returns the number of bytes written.
     */
    public int flushDirtyToBuffer(ByteBuffer dst, int dstByteOffset) {
        if (!hasDirtyRange()) {
            return 0;
        }
        int start = dirtyMinSlot;
        int end = Math.min(dirtyMaxSlot, denseEntries.size() - 1);
        int count = end - start + 1;
        int baseInt = start * SectionDesc.STRIDE_INTS;
        int intCount = count * SectionDesc.STRIDE_INTS;
        for (int i = 0; i < intCount; i++) {
            dst.putInt(dstByteOffset + (i * Integer.BYTES), packedDescs[baseInt + i]);
        }
        clearDirtyRange();
        return count * SectionDesc.STRIDE_BYTES;
    }

    public int reclaimCompleted(long completedTimeline) {
        return slabPool.reclaimCompleted(completedTimeline);
    }

    public void clearDirtyRange() {
        dirtyMinSlot = Integer.MAX_VALUE;
        dirtyMaxSlot = -1;
    }

    private void writeSlot(int slot, SectionDesc.Record desc) {
        desc.pack(packedDescs, slot * SectionDesc.STRIDE_INTS);
        if (slot < dirtyMinSlot) {
            dirtyMinSlot = slot;
        }
        if (slot > dirtyMaxSlot) {
            dirtyMaxSlot = slot;
        }
    }

    private void ensureRecordCapacity(int requiredRecords) {
        int requiredInts = requiredRecords * SectionDesc.STRIDE_INTS;
        if (requiredInts <= packedDescs.length) {
            return;
        }
        int newCapacityRecords = Math.max(requiredRecords, (packedDescs.length / SectionDesc.STRIDE_INTS) * 2);
        packedDescs = Arrays.copyOf(packedDescs, newCapacityRecords * SectionDesc.STRIDE_INTS);
    }

    /**
     * Pure-Java 64 MiB fixed-slab / 4 KiB page free-list allocator implementing {@link SlabPool}.
     * Never grows-and-copies an existing slab: when full, appends a new fixed-size slab up to
     * {@code maxSlabs}, matching {@code CELTIUM-NEW-ARCH-2026-11-25.md} §2.3.
     */
    public static final class FreeListSlabPool implements SlabPool {
        private record FreeRun(int startPage, int pageCount) {
        }

        private record RetiredSlice(SlabSlice slice, long retireAfterTimeline) {
        }

        private static final class Slab {
            final int id;
            final ArrayList<FreeRun> freeRuns = new ArrayList<>();

            Slab(int id, int totalPages) {
                this.id = id;
                this.freeRuns.add(new FreeRun(0, totalPages));
            }

            SlabSlice tryAllocate(int pagesNeeded, int pageBytes) {
                for (int i = 0; i < freeRuns.size(); i++) {
                    FreeRun run = freeRuns.get(i);
                    if (run.pageCount >= pagesNeeded) {
                        int allocStart = run.startPage;
                        if (run.pageCount == pagesNeeded) {
                            freeRuns.remove(i);
                        } else {
                            freeRuns.set(i, new FreeRun(allocStart + pagesNeeded, run.pageCount - pagesNeeded));
                        }
                        return new SlabSlice(id, allocStart * pageBytes, pagesNeeded * pageBytes);
                    }
                }
                return null;
            }

            void free(int startPage, int pageCount) {
                int insertAt = 0;
                while (insertAt < freeRuns.size() && freeRuns.get(insertAt).startPage < startPage) {
                    insertAt++;
                }
                int mergedStart = startPage;
                int mergedCount = pageCount;

                // Coalesce with previous run if adjacent.
                if (insertAt > 0) {
                    FreeRun prev = freeRuns.get(insertAt - 1);
                    if (prev.startPage + prev.pageCount == mergedStart) {
                        mergedStart = prev.startPage;
                        mergedCount += prev.pageCount;
                        freeRuns.remove(insertAt - 1);
                        insertAt--;
                    }
                }
                // Coalesce with next run if adjacent.
                if (insertAt < freeRuns.size()) {
                    FreeRun next = freeRuns.get(insertAt);
                    if (mergedStart + mergedCount == next.startPage) {
                        mergedCount += next.pageCount;
                        freeRuns.remove(insertAt);
                    }
                }
                freeRuns.add(insertAt, new FreeRun(mergedStart, mergedCount));
            }
        }

        private final int slabBytes;
        private final int pageBytes;
        private final int pagesPerSlab;
        private final int maxSlabs;
        private final List<Slab> slabs = new ArrayList<>();
        private final ArrayDeque<RetiredSlice> retiredQueue = new ArrayDeque<>();

        public FreeListSlabPool() {
            this(DEFAULT_SLAB_BYTES, DEFAULT_PAGE_BYTES, DEFAULT_MAX_SLABS);
        }

        public FreeListSlabPool(int slabBytes, int pageBytes, int maxSlabs) {
            if (slabBytes <= 0 || pageBytes <= 0 || (slabBytes % pageBytes) != 0) {
                throw new IllegalArgumentException("slabBytes must be a positive multiple of pageBytes");
            }
            if ((pageBytes % AsyncSectionMesher.QUAD_BYTES) != 0) {
                throw new IllegalArgumentException("pageBytes must be a multiple of QUAD_BYTES (64)");
            }
            if (maxSlabs <= 0) {
                throw new IllegalArgumentException("maxSlabs must be > 0");
            }
            this.slabBytes = slabBytes;
            this.pageBytes = pageBytes;
            this.pagesPerSlab = slabBytes / pageBytes;
            this.maxSlabs = maxSlabs;
        }

        @Override
        public SlabSlice allocate(int byteSize) {
            if (byteSize <= 0 || byteSize > slabBytes) {
                throw new IllegalArgumentException("Invalid allocation size " + byteSize + " for slabBytes=" + slabBytes);
            }
            int pagesNeeded = (byteSize + pageBytes - 1) / pageBytes;
            for (Slab slab : slabs) {
                SlabSlice slice = slab.tryAllocate(pagesNeeded, pageBytes);
                if (slice != null) {
                    return slice;
                }
            }
            if (slabs.size() >= maxSlabs) {
                throw new IllegalStateException("Terrain slab pool exhausted (" + maxSlabs + " slabs)");
            }
            Slab newSlab = new Slab(slabs.size(), pagesPerSlab);
            slabs.add(newSlab);
            SlabSlice slice = newSlab.tryAllocate(pagesNeeded, pageBytes);
            if (slice == null) {
                throw new IllegalStateException("Fresh slab failed to satisfy " + pagesNeeded + " pages");
            }
            return slice;
        }

        @Override
        public void retire(SlabSlice slice, long retireAfterTimeline) {
            retiredQueue.addLast(new RetiredSlice(Objects.requireNonNull(slice, "slice"), retireAfterTimeline));
        }

        @Override
        public int reclaimCompleted(long completedTimeline) {
            int reclaimed = 0;
            while (!retiredQueue.isEmpty() && retiredQueue.peekFirst().retireAfterTimeline <= completedTimeline) {
                RetiredSlice retired = retiredQueue.pollFirst();
                Slab slab = slabs.get(retired.slice.slabId());
                slab.free(retired.slice.byteOffset() / pageBytes, retired.slice.byteLength() / pageBytes);
                reclaimed++;
            }
            return reclaimed;
        }

        @Override
        public int liveSlabCount() {
            return slabs.size();
        }
    }
}
