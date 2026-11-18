package org.embeddedt.embeddium.impl.render.chunk.data;

import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage.BatchCacheParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch cache census's three added fields, each fixed as the definition item 397's plan (§2.5) gave it:
 * a key mismatch is a miss against a cache holding only other keys, an invalidation counts only when it dropped
 * something, and views are counted by identity across the process - not by a record's {@code equals}, and not
 * per storage.
 */
class BatchCacheCensusTest {
    private final BatchCacheCensus census = new BatchCacheCensus();

    @Test
    void aMissWithEntriesIsAKeyMismatch() {
        this.census.noteLookup(false, 1);
        assertEquals(1, this.census.keyMismatches());

        // A miss against an empty cache is a first build, not an alternation.
        this.census.noteLookup(false, 0);
        assertEquals(1, this.census.keyMismatches());
        assertEquals(2, this.census.misses());
    }

    @Test
    void aHitIsNeverAMismatch() {
        this.census.noteLookup(true, 2);
        assertEquals(0, this.census.keyMismatches());
        assertEquals(1, this.census.hits());
    }

    @Test
    void invalidateCountsOnlyNonEmpty() {
        this.census.noteInvalidate(0);
        assertEquals(0, this.census.invalidations());

        this.census.noteInvalidate(3);
        assertEquals(1, this.census.invalidations());
    }

    @Test
    void viewsCountsIdentitiesNotEquals() {
        this.census.noteStore(BatchCacheParams.DEFAULT_VIEW);
        this.census.noteStore(BatchCacheParams.DEFAULT_VIEW);
        assertEquals(1, this.census.viewCount());

        this.census.noteStore(BatchCacheParams.SHADOW_VIEW);
        assertEquals(2, this.census.viewCount());

        // A look-alike object is a third view: identity, not the params record's equality, is what counts.
        this.census.noteStore(new Object());
        assertEquals(3, this.census.viewCount());
        assertEquals(4, this.census.stores());
    }

    @Test
    void viewsStopsAtTheBound() {
        String overflow = null;
        for (int i = 0; i < 9; i++) {
            String line = this.census.noteStore(new Object());
            if (line != null && line.contains("views=overflow")) {
                overflow = line;
            }
        }
        assertEquals(BatchCacheCensus.MAX_VIEWS, this.census.viewCount());
        assertNotNull(overflow);

        // The overflow is announced once; a tenth view is neither tracked nor announced again.
        assertNull(this.census.noteStore(new Object()));
        assertEquals(BatchCacheCensus.MAX_VIEWS, this.census.viewCount());
    }

    @Test
    void theLineKeepsItsFieldOrder() {
        this.census.noteStore(BatchCacheParams.DEFAULT_VIEW);
        this.census.noteInvalidate(2);
        for (int i = 0; i < BatchCacheCensus.PRINT_EVERY_LOOKUPS; i++) {
            this.census.noteLookup(i % 2 == 0, 1);
        }

        String line = this.census.line();
        assertNotNull(line);
        assertTrue(line.startsWith("[Celtium] CELTIUM_BATCH_CACHE "), line);

        String[] fields = {"lookups=", "hits=", "misses=", "stores=", "inval=", "keyMismatch=", "views=", "entries="};
        int previous = -1;
        for (String field : fields) {
            int index = line.indexOf(field);
            assertTrue(index > previous, field + " out of order in: " + line);
            previous = index;
        }
    }

    @Test
    void printingStopsAtTwelveLinesButCountingDoesNot() {
        int lines = 0;
        for (int i = 0; i < BatchCacheCensus.PRINT_EVERY_LOOKUPS * (BatchCacheCensus.MAX_LINES + 2); i++) {
            this.census.noteLookup(false, 0);
            if (this.census.line() != null) {
                lines++;
            }
        }
        assertEquals(BatchCacheCensus.MAX_LINES, lines);

        long counted = this.census.lookups();
        this.census.noteLookup(false, 0);
        assertNull(this.census.line());
        assertEquals(counted + 1, this.census.lookups());
    }

    @Test
    void aViewTransitionIsReportedOnce() {
        String first = this.census.noteStore(BatchCacheParams.DEFAULT_VIEW);
        assertNotNull(first);
        assertTrue(first.startsWith("[Celtium] CELTIUM_BATCH_VIEWS views=1"), first);

        // Storing the same view again is not a transition.
        assertNull(this.census.noteStore(BatchCacheParams.DEFAULT_VIEW));

        String second = this.census.noteStore(BatchCacheParams.SHADOW_VIEW);
        assertNotNull(second);
        assertTrue(second.startsWith("[Celtium] CELTIUM_BATCH_VIEWS views=2"), second);
    }
}
