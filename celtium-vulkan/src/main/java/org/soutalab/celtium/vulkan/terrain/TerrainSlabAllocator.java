package org.soutalab.celtium.vulkan.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The fixed-slab page allocator of the new architecture (design section 2.3): geometry
 * lives in a small number of fixed-size slabs (64 MiB by default), sub-allocated in
 * 4 KiB pages from a free list. A full slab never grows and is never copied — a new slab
 * is added instead — so the flight-time grow-and-copy spike of the legacy arena cannot
 * occur. Freed pages are returned against a timeline value and only re-enter the free
 * list once the caller reports that timeline complete, so a range is never reused while
 * a submitted frame may still read it.
 * <p>
 * Pure Java: the GPU side owns one buffer per slab and indexes it by
 * ({@link Allocation#slabId}, {@link Allocation#byteOffset}).
 */
public final class TerrainSlabAllocator {
    public static final long DEFAULT_SLAB_BYTES = 64L * 1024 * 1024;
    public static final int DEFAULT_MAX_SLABS = 8;
    public static final int PAGE_BYTES = 4096;

    private final long slabBytes;
    private final int maxSlabs;
    private final int pageBytes;
    private final int pagesPerSlab;
    private final List<TreeMap<Long, Long>> freeBySlab = new ArrayList<>();
    /** Deferred frees: release timeline -> (slab, offset, bytes) triples packed as longs. */
    private final TreeMap<Long, List<long[]>> deferredFrees = new TreeMap<>();
    private long liveBytes;

    public TerrainSlabAllocator() {
        this(DEFAULT_SLAB_BYTES, DEFAULT_MAX_SLABS);
    }

    public TerrainSlabAllocator(long slabBytes, int maxSlabs) {
        this(slabBytes, maxSlabs, PAGE_BYTES);
    }

    /**
     * @param pageBytes the allocation granularity; the unit is the caller's (bytes for
     *                  the geometry/factor arenas, quads for the quad-index space)
     */
    public TerrainSlabAllocator(long slabBytes, int maxSlabs, int pageBytes) {
        if (slabBytes <= 0 || slabBytes % pageBytes != 0) {
            throw new IllegalArgumentException("slabBytes must be a positive multiple of the page size: " + slabBytes);
        }
        if (maxSlabs <= 0) {
            throw new IllegalArgumentException("maxSlabs must be positive: " + maxSlabs);
        }
        this.slabBytes = slabBytes;
        this.maxSlabs = maxSlabs;
        this.pageBytes = pageBytes;
        this.pagesPerSlab = (int) (slabBytes / pageBytes);
    }

    public static final class Allocation {
        public final int slabId;
        public final long byteOffset;
        /** The requested size, before page rounding. */
        public final long byteSize;
        /** The page-rounded size actually reserved. */
        public final long reservedBytes;

        private Allocation(int slabId, long byteOffset, long byteSize, long reservedBytes) {
            this.slabId = slabId;
            this.byteOffset = byteOffset;
            this.byteSize = byteSize;
            this.reservedBytes = reservedBytes;
        }
    }

    /**
     * Allocates {@code byteSize} bytes (page-rounded) from the first slab with room,
     * adding a slab when every existing one is full.
     *
     * @return the allocation, or {@code null} when the slab cap is reached
     */
    public synchronized Allocation allocate(long byteSize) {
        if (byteSize <= 0) {
            throw new IllegalArgumentException("byteSize must be positive: " + byteSize);
        }
        long reserved = roundUpToPage(byteSize);
        if (reserved > this.slabBytes) {
            return null;
        }
        for (int slab = 0; slab < this.freeBySlab.size(); slab++) {
            Long offset = takeFromSlab(slab, reserved);
            if (offset != null) {
                this.liveBytes += reserved;
                return new Allocation(slab, offset, byteSize, reserved);
            }
        }
        if (this.freeBySlab.size() >= this.maxSlabs) {
            return null;
        }
        int slab = this.freeBySlab.size();
        TreeMap<Long, Long> free = new TreeMap<>();
        free.put(0L, this.slabBytes);
        this.freeBySlab.add(free);
        Long offset = takeFromSlab(slab, reserved);
        // A fresh slab always fits a request that passed the size check above.
        this.liveBytes += reserved;
        return new Allocation(slab, offset, byteSize, reserved);
    }

    /**
     * Allocates {@code byteSize} bytes (page-rounded) from one named slab only,
     * opening that slab when it is the next unopened one and the cap allows.
     * Paired arenas whose placements must coincide (the store's geometry and
     * quad space) allocate slab by slab through this entry point: two
     * independent first-fit scans diverge as soon as their page granularities
     * make them fill slabs at different rates, and every later placement then
     * misses its pair even with most of the arena empty.
     *
     * @return the allocation, or {@code null} when that slab cannot serve it
     */
    public synchronized Allocation allocateInSlab(int slab, long byteSize) {
        if (byteSize <= 0) {
            throw new IllegalArgumentException("byteSize must be positive: " + byteSize);
        }
        if (slab < 0 || slab > this.freeBySlab.size() || slab >= this.maxSlabs) {
            return null;
        }
        long reserved = roundUpToPage(byteSize);
        if (reserved > this.slabBytes) {
            return null;
        }
        if (slab == this.freeBySlab.size()) {
            TreeMap<Long, Long> free = new TreeMap<>();
            free.put(0L, this.slabBytes);
            this.freeBySlab.add(free);
        }
        Long offset = takeFromSlab(slab, reserved);
        if (offset == null) {
            return null;
        }
        this.liveBytes += reserved;
        return new Allocation(slab, offset, byteSize, reserved);
    }

    /** Returns an allocation's pages at once (init/teardown paths only). */
    public synchronized void free(Allocation allocation) {
        returnToSlab(allocation.slabId, allocation.byteOffset, allocation.reservedBytes);
        this.liveBytes -= allocation.reservedBytes;
    }

    /**
     * Schedules an allocation's pages to return once {@code releaseTimeline} completes;
     * {@link #collectReleased(long)} performs the actual return.
     */
    public synchronized void freeDeferred(Allocation allocation, long releaseTimeline) {
        this.deferredFrees.computeIfAbsent(releaseTimeline, key -> new ArrayList<>())
                .add(new long[]{allocation.slabId, allocation.byteOffset, allocation.reservedBytes});
    }

    /** Returns every deferred free whose timeline has completed. */
    public synchronized void collectReleased(long completedTimeline) {
        while (!this.deferredFrees.isEmpty() && this.deferredFrees.firstKey() <= completedTimeline) {
            List<long[]> frees = this.deferredFrees.pollFirstEntry().getValue();
            for (long[] free : frees) {
                returnToSlab((int) free[0], free[1], free[2]);
                this.liveBytes -= free[2];
            }
        }
    }

    private Long takeFromSlab(int slab, long reserved) {
        TreeMap<Long, Long> free = this.freeBySlab.get(slab);
        Long foundKey = null;
        long foundSize = 0;
        for (var entry : free.entrySet()) {
            if (entry.getValue() >= reserved) {
                foundKey = entry.getKey();
                foundSize = entry.getValue();
                break;
            }
        }
        if (foundKey == null) {
            return null;
        }
        free.remove(foundKey);
        if (foundSize > reserved) {
            free.put(foundKey + reserved, foundSize - reserved);
        }
        return foundKey;
    }

    private void returnToSlab(int slab, long offset, long size) {
        TreeMap<Long, Long> free = this.freeBySlab.get(slab);
        long start = offset;
        long end = offset + size;
        // Coalesce with the following range.
        Long nextSize = free.remove(end);
        if (nextSize != null) {
            end += nextSize;
        }
        // Coalesce with the preceding range.
        var previous = free.floorEntry(start);
        if (previous != null && previous.getKey() + previous.getValue() == start) {
            start = previous.getKey();
            free.remove(previous.getKey());
        }
        free.put(start, end - start);
    }

    private long roundUpToPage(long bytes) {
        return (bytes + this.pageBytes - 1) / this.pageBytes * this.pageBytes;
    }

    public int slabCount() {
        return this.freeBySlab.size();
    }

    public int maxSlabs() {
        return this.maxSlabs;
    }

    public long slabBytes() {
        return this.slabBytes;
    }

    public int pagesPerSlab() {
        return this.pagesPerSlab;
    }

    public long liveBytes() {
        return this.liveBytes;
    }

    public long freeBytes() {
        long total = 0;
        for (TreeMap<Long, Long> free : this.freeBySlab) {
            for (long size : free.values()) {
                total += size;
            }
        }
        return total;
    }
}
