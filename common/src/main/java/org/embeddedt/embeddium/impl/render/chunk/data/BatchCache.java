package org.embeddedt.embeddium.impl.render.chunk.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.jetbrains.annotations.Nullable;

/**
 * One region's (and one pass's) cache of assembled draw batches, keyed by
 * {@link SectionRenderDataStorage.BatchCacheParams}.
 * <p>
 * This replaces the two unrolled slots {@link SectionRenderDataStorage} used to hold. Two slots were enough while the
 * key was a single culling flag, because a sorted pass never culls faces and at most one key was live per storage; the
 * view in the key turns the key space into the views times the flags, which a fixed pair of slots cannot hold. Views
 * come from a small fixed set ({@code DEFAULT_VIEW}, {@code SHADOW_VIEW}), so the map is bounded by that set and a miss
 * never has to evict anything: the entries are only ever dropped by {@link #invalidate()}. That fixed set is a bound,
 * not a usage claim: only one of its members ever keys an entry in the current tree - {@code SHADOW_VIEW} does not
 * reach a key until the shadow wiring lands (item 406) - so today every map holds a single view's entries.
 * <p>
 * Invalidation is the owner's, not the map's. Every mutation of the mesh data a cached batch refers to - the storage's
 * {@code setMeshes}, {@code removeMeshes}, {@code removeIndexBuffer}, {@code replaceIndexBuffer}, {@code onBufferResized}
 * and {@code delete} - calls {@link #invalidate()}, so no surviving batch can point at an arena range that moved or was
 * freed. Storing under a key that already holds a batch deletes only that batch: it is the entry being replaced, and
 * the other views' entries are not touched.
 */
final class BatchCache {
    private final Object2ObjectArrayMap<SectionRenderDataStorage.BatchCacheParams, CachedBatch> batches =
            new Object2ObjectArrayMap<>(2);

    /** {@return the batch stored under {@code params}, or {@code null} when this key holds none} */
    @Nullable
    CachedBatch get(SectionRenderDataStorage.BatchCacheParams params) {
        return this.batches.get(params);
    }

    /**
     * Stores {@code batch} under {@code params}, deleting the batch it replaces if the key was already held.
     * <p>
     * The rebuild paths do not rely on that replacement delete: they retire the stale batch first (see
     * {@link #retire}), so by the time the replacement is stored the key is empty and nothing is deleted here.
     * The delete stays for stores that replace without retiring first, so a batch is never leaked by a direct
     * store - but a caller that creates the replacement before storing it must retire first if the old batch's
     * resources have to be released before the new batch is created.
     */
    void store(SectionRenderDataStorage.BatchCacheParams params, CachedBatch batch) {
        CachedBatch replaced = this.batches.put(params, batch);
        if (replaced != null) {
            replaced.delete();
        }
    }

    /**
     * Removes the batch held under {@code params} and deletes it; does nothing when the key holds none.
     * Only that key's batch is touched - the other views' entries survive a retire, unlike an invalidation.
     */
    void retire(SectionRenderDataStorage.BatchCacheParams params) {
        CachedBatch retired = this.batches.remove(params);
        if (retired != null) {
            retired.delete();
        }
    }

    /** Deletes every batch held and drops every key. */
    void invalidate() {
        if (this.batches.isEmpty()) {
            return;
        }

        for (var batch : this.batches.values()) {
            batch.delete();
        }

        this.batches.clear();
    }

    /** {@return the number of distinct keys currently held} Printed as {@code entries=} by the storage's census. */
    int size() {
        return this.batches.size();
    }
}
