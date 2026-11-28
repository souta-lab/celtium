package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The records fallback reselection's wiring, pinned by source reading in the house style
 * (see {@link FallbackWiringContractTest}): the seats live inside device-bound methods a
 * device-less test cannot drive, so the wiring is what a test can hold; the store's
 * behaviour is pinned behaviourally in {@link InstanceDataStoreTest}.
 * <p>
 * The defect (overall audit 11, SOL-4 finding 1): the ring separation's landing record
 * promised that on records-pool exhaustion the caller's direct path keeps the drawing
 * alive, but every direct twin draw wrote its record into the same selection the store
 * had just refused, so an exhausted window lost its direct groups with its batched ones.
 * The fix routes the three drain fallbacks' records through a helper that takes the
 * store's fallback selection on refusal, and gives the capture seat the same retry
 * behind a segment flush.
 */
class RecordsFallbackReselectWiringTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
    private static final String STORE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/InstanceDataStore.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }

    private static int count(String text, String needle) {
        int found = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    @Test
    void theThreeDrainFallbacksRecordThroughTheReselectingHelper() throws IOException {
        String backend = read(BACKEND);
        // The particle fallback, the generalized quad draw and the non-quad emitter each
        // take their record through the one helper; no drain seat writes a record behind
        // its back in the pre-fix form, which dropped the group on the very refusal the
        // capture had just taken.
        assertEquals(3, count(backend, "celtium$recordForDirectDraw(push)"),
                "particle, quad and non-quad drain fallbacks record through the helper");
        assertEquals(0, count(backend,
                        "this.drawRecords == null ? -1 : this.drawRecords.record(push, -1)"),
                "the pre-fix drain form is gone from all three branches");
    }

    @Test
    void theCaptureRetryFlushesTheSegmentBeforeReselecting() throws IOException {
        String backend = read(BACKEND);
        // The reselection replaces the selection the open segment's entries will bind at
        // their flush, so the capture seat's retry must stand after a flush of that
        // segment - the order the method's own mid-flush retry already uses.
        int capture = backend.indexOf("boolean celtium$tryCaptureBatchGroup");
        int flush = backend.indexOf("celtium$flushBatchSegment();", capture);
        int reselect = backend.indexOf("this.drawRecords.reselectFallback()", capture);
        assertTrue(capture >= 0 && flush > capture && reselect > flush,
                "inside the capture, a segment flush precedes the fallback reselection");
    }

    @Test
    void theDropAndFallbackCountingSeatsAreUnchanged() throws IOException {
        String backend = read(BACKEND);
        // The pass shape rulings D-8/D-9 are stated over these counters (the batcher's
        // droppedCaptures against the wiring's fallback count, with droppedDraws counted
        // at its seats): the fix rescues a refused group before any counter moves - a
        // rescued group is captured or drawn, never counted - so the counters keep
        // their seats and their meanings, and the identity's terms are untouched.
        assertEquals(2, count(backend, "batchFallbackGroups++"),
                "the wiring's fallback counter still increments at its two capture seats");
        assertEquals(4, count(backend, "batchDroppedDraws++"),
                "the dropped-draw counter still increments at its four seats"
                        + " (the three drain fallbacks and the batch sink's texture drop)");
    }

    @Test
    void theStoreOffersTheFallbackSelection() throws IOException {
        String store = read(STORE);
        assertTrue(store.contains("boolean reselectFallback()"),
                "the store offers the fallback reselection the drain's seats call");
        assertTrue(store.contains("private static final int FALLBACK_RECORDS = 16;"),
                "the fallback selection asks a per-group handful of records, not a window's slice");
    }
}
