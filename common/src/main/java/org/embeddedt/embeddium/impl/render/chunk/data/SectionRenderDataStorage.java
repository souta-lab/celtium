package org.embeddedt.embeddium.impl.render.chunk.data;

import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

public class SectionRenderDataStorage {
    private final MeshBufferSegment[] allocations = new MeshBufferSegment[RenderRegion.REGION_SIZE];
    private final MeshBufferSegment[] indexAllocations = new MeshBufferSegment[RenderRegion.REGION_SIZE];

    private final long pMeshDataArray;
    private final ChunkPrimitiveType primitiveType;
    private final SectionRenderDataUnsafe.Strategy storageStrategy;

    private final int verticesPerArenaElement;

    private int numAllocations;

    /**
     * The key a cached region batch is held under: the flags the batch was assembled with, and the view it was
     * assembled for.
     * <p>
     * The view is part of the key because a view is one perspective the world is drawn from - the player camera, or a
     * shader pack's shadow camera - and two views can share every other field: a sorted pass culls no faces in
     * either, and the shadow pass draws the same passes over the same section storage as the terrain pass does. Keyed
     * by the flags alone, each view does not fit the other's cached batch, so each rebuild overwrites the other's
     * entry and both rebuild every frame. Upstream Celeritas keyed its cached region batches by the render view for
     * exactly this case (its commit message states the eviction, and its
     * {@code oneViewRebuildsOnEveryAlternation} test names the behaviour); item 337's audit brought the change here.
     * <p>
     * Views are compared by identity and must come from a small fixed set ({@link #DEFAULT_VIEW}, {@link #SHADOW_VIEW}),
     * because every distinct view adds an entry to every region's batch cache.
     * <p>
     * Do not grow {@code static final BatchCacheParams} companion constants for the key combinations: a constant
     * that reads a sentinel while it is being initialised is the declaration-order trap
     * (docs/verification-traps.md). Keys are assembled at the call site instead (item 406 kept the landed sentinel
     * shape - the two {@code Object} constants in this record - over item 397 plan §2.1's dedicated
     * {@code TerrainView} class, because no static key constant exists to observe the order and the key is built
     * once per pass, outside the region loop).
     */
    public record BatchCacheParams(boolean useBlockFaceCulling, Object view) {
        /** The player camera's view: the only perspective drawn when no shadow pass runs. */
        public static final Object DEFAULT_VIEW = new Object();

        /** A shader pack's shadow camera. Distinguishing it keeps the shadow and terrain passes out of one entry. */
        public static final Object SHADOW_VIEW = new Object();
    }

    /**
     * The batches assembled for this region storage, one per key. It replaces the two unrolled slots that were enough
     * while the key was a single culling flag: with the view in the key the key space is the views times the flags,
     * and a fixed pair of slots would thrash exactly as a pair keyed by the flag alone did.
     */
    private final BatchCache batchCache = new BatchCache();

    public SectionRenderDataStorage(ChunkPrimitiveType primitiveType, boolean sorted, int verticesPerArenaElement) {
        this.storageStrategy = sorted ? SectionRenderDataUnsafe.Strategy.FULL : SectionRenderDataUnsafe.Strategy.COMPACT;
        this.pMeshDataArray = this.storageStrategy.allocateHeap();
        if (this.pMeshDataArray == 0) {
            throw new OutOfMemoryError("Failed to allocate mesh data array");
        }
        this.primitiveType = primitiveType;
        this.verticesPerArenaElement = verticesPerArenaElement;
    }

    public boolean isEmpty() {
        return this.numAllocations == 0;
    }

    public ChunkPrimitiveType getPrimitiveType() {
        return this.primitiveType;
    }

    public void setMeshes(int localSectionIndex,
                          MeshBufferSegment allocation, @Nullable MeshBufferSegment indexAllocation, Map<ModelQuadFacing, VertexRange> ranges) {
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;
            this.numAllocations--;
        }

        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;
        }

        this.allocations[localSectionIndex] = allocation;
        this.indexAllocations[localSectionIndex] = indexAllocation;
        this.numAllocations++;

        int vertexOffset = allocation.getOffset() * this.verticesPerArenaElement;
        int indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        this.storageStrategy.writeMeshesAndSliceMask(this.pMeshDataArray, localSectionIndex, vertexOffset, indexOffset, ranges, this.primitiveType);

        this.invalidateCachedBatches();
    }

    public void removeMeshes(int localSectionIndex) {
        if (this.allocations[localSectionIndex] != null) {
            this.allocations[localSectionIndex].delete();
            this.allocations[localSectionIndex] = null;

            this.storageStrategy.clearRow(this.pMeshDataArray, localSectionIndex);

            this.numAllocations--;

            this.invalidateCachedBatches();
        }

        removeIndexBuffer(localSectionIndex);
    }

    public void removeIndexBuffer(int localSectionIndex) {
        if (this.indexAllocations[localSectionIndex] != null) {
            this.indexAllocations[localSectionIndex].delete();
            this.indexAllocations[localSectionIndex] = null;

            this.invalidateCachedBatches();
        }
    }

    public void replaceIndexBuffer(int localSectionIndex, MeshBufferSegment indexAllocation) {
        removeIndexBuffer(localSectionIndex);

        this.indexAllocations[localSectionIndex] = indexAllocation;

        var pMeshData = this.getDataPointer(localSectionIndex);

        int indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        this.storageStrategy.writeIndexOffsets(pMeshData, indexOffset, this.primitiveType);

        this.invalidateCachedBatches();
    }

    public void onBufferResized() {
        for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
            this.updateMeshes(sectionIndex);
        }

        this.invalidateCachedBatches();
    }

    private void updateMeshes(int sectionIndex) {
        var allocation = this.allocations[sectionIndex];

        if (allocation == null) {
            return;
        }

        var indexAllocation = this.indexAllocations[sectionIndex];

        var vertexOffset = allocation.getOffset() * this.verticesPerArenaElement;
        var indexOffset = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;

        var data = this.getDataPointer(sectionIndex);

        this.storageStrategy.rebase(data, vertexOffset, indexOffset, this.primitiveType);
    }

    public long getDataPointer(int sectionIndex) {
        return this.storageStrategy.heapPointer(this.pMeshDataArray, sectionIndex);
    }

    /**
     * {@return the base address of the mesh data array}
     * <p>
     * Row {@code i} begins {@code i * stride} bytes in. Exposed for hot loops which already know their layout
     * statically, so that they can hoist the base and stride and index the array themselves rather than dispatching
     * through {@link #getDataPointer} once per section.
     */
    public long getRowBasePointer() {
        return this.storageStrategy.getRowBasePointer(this.pMeshDataArray);
    }

    public int getSliceMask(int sectionIndex) {
        return this.storageStrategy.getSliceMask(this.pMeshDataArray, sectionIndex);
    }

    /**
     * Hits, misses and stores of this batch cache, counted because the terrain pass's own recording
     * measured 6.5 ms p50 and rebuilding a batch - with the GPU buffer it allocates - is the largest thing inside it.
     * <p>
     * Measured 2026-10-16 (leg gv-notax-r37-bcache): {@code lookups=6000 hits=1431 misses=4569}, a 24% hit rate. The
     * first reading of that was "two slots, three render passes per frame, so each pass displaces the other"; the
     * A/B on the same jar refused it - {@code -Dceltium.terrain.batchCacheSlots=4} moved the rate to 27% and the
     * terrain bracket to 9.6 ms p50 against the two-slot arm's 8.7 ms. So the entries are displaced by a changing
     * key, not by a shortage of slots, and the next question is which field of {@link BatchCacheParams} changes every
     * frame. The counter stays because that is the number any fix has to move.
     * <p>
     * The answer taken 2026-11-18 (item 397, from the item 337 sync audit) is that the missing field is the view: a
     * shader pack's shadow pass draws the same passes over the same storage as the terrain pass, and the two can share
     * the culling flag, so the two render lists alternate under one key - which is the shape that displaces an entry
     * every frame. The key now carries the view. Item 406 completed the census the 397 plan (§2.5) fixed:
     * {@code inval} counts the invalidations that dropped at least one entry, {@code keyMismatch} the misses against
     * a cache holding only other keys, and {@code views} the distinct views observed across the process, counted by
     * identity - so a leg can tell two views keeping their own entries from one view being hit twice, and can split
     * the misses into invalidation-driven and alternation-driven. The count this storage itself holds is printed as
     * {@code entries=}; that is what {@code views=} held before item 406, and legs from before the rename read it as
     * an entry count.
     * <p>
     * That <em>explains a mechanism</em>, not this reading: nothing in the tree calls the shadow-pass setup yet -
     * {@code RenderSectionManager.isInShadowPass()} is a fixed {@code false} with no override in this tree, so
     * {@code SHADOW_VIEW} never reaches a key here and {@code views} stays 1 (item 406) - so the 24% above is not
     * claimed to be accounted for. A leg that runs a shader pack's shadow pass is what would show the rate move, and
     * the number to compare is this same counter.
     * <p>
     * Printed by the storage itself, bounded, because the fixture cannot reach this class.
     */
    private static final BatchCacheCensus CENSUS = new BatchCacheCensus();

    public @Nullable CachedBatch getCachedMultiDrawBatch(BatchCacheParams params) {
        CachedBatch cached = this.batchCache.get(params);
        CENSUS.noteLookup(cached != null, this.batchCache.size());
        String line = CENSUS.line();
        if (line != null) {
            System.out.println(line);
        }
        return cached;
    }

    public void storeCachedMultiDrawBatch(BatchCacheParams params, CachedBatch batch) {
        String transition = CENSUS.noteStore(params.view());
        if (transition != null) {
            System.out.println(transition);
        }
        this.batchCache.store(params, batch);
    }

    /**
     * Discards the cached batch for {@code params} ahead of its rebuild, so the stale batch is deleted before
     * its replacement is created rather than inside {@link #storeCachedMultiDrawBatch} after the replacement
     * already exists (item 398, the ordering half of upstream's "Reuse the indirect command buffer when a
     * region batch is rebuilt", from item 337's audit). Upstream retires first so the new batch can adopt the
     * old one's GL buffer; celtium's batches own no GL buffer ({@code VulkanMultiDrawBatch.upload} is a no-op
     * and its commands stay in CPU arrays), so there is nothing to adopt - what the ordering buys here is that
     * the stale batch's own resources (a Vulkan batch's retained packet buffers, freed by its {@code delete()})
     * are released before the replacement allocates its own, instead of both being alive across the rebuild.
     * <p>
     * A retire is not an invalidation: the mesh data the batch was assembled from has not changed - the batch
     * is being replaced because its validity window lapsed - so the census does not count it as one, and the
     * other keys' batches are left alone. Callers that hold the stale batch as a fallback (the Vulkan
     * backend's deferred rebuild under the item 328 budget) must retire only once the rebuild is actually
     * going ahead, never before the deferral decision.
     */
    public void retireCachedMultiDrawBatch(BatchCacheParams params) {
        this.batchCache.retire(params);
    }

    public void invalidateCachedBatches() {
        CENSUS.noteInvalidate(this.batchCache.size());
        this.batchCache.invalidate();
    }

    public void delete() {
        this.invalidateCachedBatches();

        for (var allocation : this.allocations) {
            if (allocation != null) {
                allocation.delete();
            }
        }

        for (var allocation : this.indexAllocations) {
            if (allocation != null) {
                allocation.delete();
            }
        }

        Arrays.fill(this.allocations, null);
        Arrays.fill(this.indexAllocations, null);

        this.storageStrategy.freeHeap(this.pMeshDataArray);

        this.numAllocations = 0;
    }
}
