package org.embeddedt.embeddium.impl.render.chunk.data;

import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage.BatchCacheParams;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch cache's key and its invalidation, both of which have to be read as contracts rather than as behaviour:
 * a lookup for one view must never answer with another view's batch, and a stored batch must not survive a change to
 * the mesh data it was assembled from.
 */
class BatchCacheTest {

    /** A cached batch that counts its deletions; its payload is null, so nothing outside this test is touched. */
    private static final class CountingBatch extends CachedBatch {
        private int deletes;

        CountingBatch() {
            super(null, null, new byte[0], 0, 0, 0, 0, 0, 0, 0);
        }

        @Override
        public void delete() {
            this.deletes++;
        }
    }

    private final BatchCache cache = new BatchCache();

    private static BatchCacheParams params(boolean cull, Object view) {
        return new BatchCacheParams(cull, view);
    }

    // ---------------------------------------------------------------- keying

    @Test
    void viewsThatShareTheCullingFlagAreDifferentKeys() {
        assertNotEquals(params(false, BatchCacheParams.DEFAULT_VIEW), params(false, BatchCacheParams.SHADOW_VIEW));
        assertEquals(params(false, BatchCacheParams.DEFAULT_VIEW), params(false, BatchCacheParams.DEFAULT_VIEW));
        // The culling flag is still part of the key on its own.
        assertNotEquals(params(true, BatchCacheParams.DEFAULT_VIEW), params(false, BatchCacheParams.DEFAULT_VIEW));
        // Views are compared by identity: a key built from a look-alike object is not the player camera's key.
        assertNotEquals(params(false, BatchCacheParams.DEFAULT_VIEW), params(false, new Object()));
    }

    @Test
    void viewsKeepTheirOwnBatchWhenTheyAlternate() {
        CountingBatch terrain = new CountingBatch();
        CountingBatch shadow = new CountingBatch();

        // The caller's shape: look up, and only rebuild (store) on a miss. Two views alternating through one region
        // storage used to alternate misses because the key held only the culling flag, which the two share when the
        // pass is sorted.
        for (int round = 0; round < 4; round++) {
            if (this.cache.get(params(false, BatchCacheParams.DEFAULT_VIEW)) == null) {
                this.cache.store(params(false, BatchCacheParams.DEFAULT_VIEW), terrain);
            }
            if (this.cache.get(params(false, BatchCacheParams.SHADOW_VIEW)) == null) {
                this.cache.store(params(false, BatchCacheParams.SHADOW_VIEW), shadow);
            }

            assertSame(terrain, this.cache.get(params(false, BatchCacheParams.DEFAULT_VIEW)));
            assertSame(shadow, this.cache.get(params(false, BatchCacheParams.SHADOW_VIEW)));
        }

        // Neither view rebuilt after the first frame, and neither deleted the other's batch.
        assertEquals(0, terrain.deletes);
        assertEquals(0, shadow.deletes);
        assertEquals(2, this.cache.size());
    }

    @Test
    void oneKeyForTwoViewsLetsThemEvictEachOther() {
        // The control for the test above, in the key's own terms: if both views build the same key (which is what the
        // key was before the view was added - the pass and culling flag the two share), the second view's rebuild
        // replaces the first view's entry and the first view's batch is deleted under it. That is the symptom.
        CountingBatch terrain = new CountingBatch();
        CountingBatch shadow = new CountingBatch();
        BatchCacheParams sharedKey = params(false, BatchCacheParams.DEFAULT_VIEW);

        for (int round = 0; round < 3; round++) {
            this.cache.store(sharedKey, terrain);
            this.cache.store(sharedKey, shadow);
        }

        assertEquals(1, this.cache.size());
        assertSame(shadow, this.cache.get(sharedKey));
        assertTrue(terrain.deletes > 0, "the terrain view's batch was destroyed by the shadow view");
        assertTrue(shadow.deletes > 0, "the shadow view's batch was destroyed by the terrain view");
    }

    @Test
    void aLookupForAnotherViewIsAMissAndKeepsTheStoredBatch() {
        CountingBatch terrain = new CountingBatch();
        this.cache.store(params(false, BatchCacheParams.DEFAULT_VIEW), terrain);

        // The same pass drawn for the shadow camera does not fit the terrain camera's batch: miss.
        assertNull(this.cache.get(params(false, BatchCacheParams.SHADOW_VIEW)));
        assertNull(this.cache.get(params(true, BatchCacheParams.DEFAULT_VIEW)));
        assertNull(this.cache.get(params(false, new Object())));

        // A miss must not evict anything, or the next frame's terrain lookup would miss as well.
        assertSame(terrain, this.cache.get(params(false, BatchCacheParams.DEFAULT_VIEW)));
        assertEquals(0, terrain.deletes);
    }

    // ---------------------------------------------------------------- replacement

    @Test
    void storingUnderOneKeyDeletesOnlyTheBatchItReplaces() {
        CountingBatch oldTerrain = new CountingBatch();
        CountingBatch shadow = new CountingBatch();
        CountingBatch newTerrain = new CountingBatch();

        this.cache.store(params(false, BatchCacheParams.DEFAULT_VIEW), oldTerrain);
        this.cache.store(params(false, BatchCacheParams.SHADOW_VIEW), shadow);
        this.cache.store(params(false, BatchCacheParams.DEFAULT_VIEW), newTerrain);

        assertEquals(1, oldTerrain.deletes);
        assertEquals(0, shadow.deletes);
        assertSame(newTerrain, this.cache.get(params(false, BatchCacheParams.DEFAULT_VIEW)));
        assertSame(shadow, this.cache.get(params(false, BatchCacheParams.SHADOW_VIEW)));
        assertEquals(2, this.cache.size());
    }

    @Test
    void aReplacedBatchIsNotHandedOutAgain() {
        CountingBatch oldTerrain = new CountingBatch();
        CountingBatch newTerrain = new CountingBatch();

        this.cache.store(params(true, BatchCacheParams.DEFAULT_VIEW), oldTerrain);
        this.cache.store(params(true, BatchCacheParams.DEFAULT_VIEW), newTerrain);

        assertNotSame(oldTerrain, this.cache.get(params(true, BatchCacheParams.DEFAULT_VIEW)));
        assertSame(newTerrain, this.cache.get(params(true, BatchCacheParams.DEFAULT_VIEW)));
    }

    // ---------------------------------------------------------------- invalidation

    @Test
    void invalidateDeletesEveryBatchAndDropsEveryKey() {
        CountingBatch terrain = new CountingBatch();
        CountingBatch shadow = new CountingBatch();
        CountingBatch sortedShadow = new CountingBatch();

        this.cache.store(params(false, BatchCacheParams.DEFAULT_VIEW), terrain);
        this.cache.store(params(false, BatchCacheParams.SHADOW_VIEW), shadow);
        this.cache.store(params(true, BatchCacheParams.SHADOW_VIEW), sortedShadow);
        assertEquals(3, this.cache.size());

        this.cache.invalidate();

        assertEquals(1, terrain.deletes);
        assertEquals(1, shadow.deletes);
        assertEquals(1, sortedShadow.deletes);
        assertEquals(0, this.cache.size());
        assertNull(this.cache.get(params(false, BatchCacheParams.DEFAULT_VIEW)));
        assertNull(this.cache.get(params(false, BatchCacheParams.SHADOW_VIEW)));
        assertNull(this.cache.get(params(true, BatchCacheParams.SHADOW_VIEW)));

        // Invalidating an empty cache deletes nothing more - the storage calls this on every mesh change, and a
        // batch must never be deleted twice by a repeated invalidation.
        this.cache.invalidate();
        assertEquals(1, terrain.deletes);
        assertEquals(0, this.cache.size());
    }

    @Test
    void anEmptyCacheHoldsNothing() {
        assertEquals(0, this.cache.size());
        assertNull(this.cache.get(params(false, BatchCacheParams.DEFAULT_VIEW)));
        assertNull(this.cache.get(params(false, BatchCacheParams.SHADOW_VIEW)));
        this.cache.invalidate();
        assertEquals(0, this.cache.size());
    }
}
