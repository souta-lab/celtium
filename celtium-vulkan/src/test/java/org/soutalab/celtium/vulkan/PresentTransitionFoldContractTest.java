package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source contracts for the S5-D1 slice (audit D1 resolution): the direct present's pre-recorded
 * final transition folds into the frame's batched deferred submit as an external entry instead of
 * submitting alone, and the frame boundary holds its flush for that present while a borrow is
 * live. The mechanism lives inside device-bound methods, so - like
 * {@link LandedFixGuardContractTest} - these pins are by source shape: each names the review
 * condition it guards, and fails when the guard is removed or reverted.
 */
class PresentTransitionFoldContractTest {

    private static final String CONTEXT =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java";
    private static final String SWAPCHAIN =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java";
    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
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

    private static String window(String text, String anchor, int length) {
        int start = text.indexOf(anchor);
        assertTrue(start >= 0, "anchor is present: " + anchor);
        return text.substring(start, Math.min(text.length(), start + length));
    }

    /**
     * Change A: the direct present enqueues its transition as an external entry and flushes the
     * batch; it must not go back to the lone pre-recorded submit. {@code submitPreRecorded} itself
     * survives for the blit present, which is outside D1's scope.
     */
    @Test
    void theDirectPresentFoldsItsTransitionIntoTheBatchedSubmit() throws IOException {
        String body = window(read(SWAPCHAIN), "private String celtium$presentDirect(", 12000);
        assertTrue(body.contains("celtium$submitDeferredExternal("),
                "the transition joins the batched ledger as an external entry");
        assertTrue(body.contains("celtium$flushBatchedSubmits()"),
                "the present's own flush is the frame's one submission");
        assertEquals(0, count(body, "submitPreRecorded("),
                "the direct present must not submit its transition alone again");
        assertTrue(body.contains("transitionBatchedJoins++") && body.contains("transitionSoloFlushes++"),
                "the joined/solo census pair is counted at the enqueue");
    }

    /**
     * Review condition 3: the compact contract lives at the external entry's entrance - null
     * semaphore handles are dropped there, because submitChunk passes the arrays through untouched.
     */
    @Test
    void theExternalEntryCompactsNullHandlesAtItsEntrance() throws IOException {
        String entry = window(read(CONTEXT), "public boolean celtium$submitDeferredExternal(", 6000);
        assertTrue(entry.contains("semaphore != VK_NULL_HANDLE"),
                "null wait/signal handles are compacted at the entrance");
        assertTrue(entry.contains("entry.externalBuffer = true;"),
                "the entry is marked as borrowed, not owned");
    }

    /**
     * Review condition 2: an external entry's buffer is its owner's pool's. It is never recycled at
     * reclaim, never quarantined by the batch-failure path, and the await path quarantines only its
     * fence. The pooled-entry flag is cleared at reclaim and set explicitly at every entry point.
     */
    @Test
    void externalEntriesAreExcludedFromQuarantineAndRecycling() throws IOException {
        String context = read(CONTEXT);
        String reclaim = window(context, "private void celtium$reclaimDeferred(", 6000);
        assertTrue(reclaim.contains("else if (!entry.externalBuffer)"),
                "reclaim never recycles an external buffer into this context's pool");
        assertTrue(reclaim.contains("entry.externalBuffer = false;"),
                "the pooled entry's flag is cleared at reclaim");
        String abandon = window(context, "private void abandonFailedBatch(", 3000);
        assertTrue(abandon.contains("entry.frameSlot < 0 && !entry.externalBuffer"),
                "a failed batch never quarantines an external buffer");
        String await = window(context, "private void celtium$awaitDeferredEntry(", 9000);
        assertTrue(await.contains("quarantineFence(entry.fence)"),
                "a timed-out external entry quarantines its fence only");
        assertEquals(3, count(context, "entry.externalBuffer = false;"),
                "both owned entry points and the reclaim clear set the flag explicitly");
        assertEquals(1, count(context, "entry.externalBuffer = true;"),
                "only the external entry point marks an entry as borrowed");
        // The flush's layout-recording commit/abandon loops skip external entries: their owner
        // recorded the transitions itself, so there is no GL layout recording to settle.
        String flush = window(context, "public void celtium$flushBatchedSubmits()", 6000);
        assertEquals(2, count(flush, "entry.commandBuffer != null && !entry.externalBuffer"),
                "both flush loops skip the layout-recording settle for external entries");
    }

    /**
     * Change B with review condition 4: the boundary holds its flush only for a live present
     * borrow, and never while the present is skipped.
     */
    @Test
    void theBoundaryHoldsItsFlushOnlyForALiveBorrowOutsideSkipPresent() throws IOException {
        String body = window(read(BACKEND), "private void celtium$flushPendingAtFrameBoundaryBody(", 9000);
        assertTrue(body.contains("!SKIP_PRESENT"),
                "a skipped present never holds the batch");
        assertTrue(body.contains("hasLivePresentBorrow()"),
                "the hold is tied to the frame target's live borrow");
        assertTrue(body.contains("if (!holdBatchForPresent)"),
                "the boundary flush is conditional on the hold, not removed");
    }

    /**
     * Review condition 1: destruction flushes the held batch before it drains - vkQueueWaitIdle
     * only waits for submitted work, so a held entry would otherwise be submitted against destroyed
     * images and a destroyed transition pool.
     */
    @Test
    void destructionFlushesTheHeldBatchBeforeItDrains() throws IOException {
        String swapchain = read(SWAPCHAIN);
        String delete = window(swapchain, "public void delete() {", 4000);
        int flushAt = delete.indexOf("celtium$flushBatchedBeforeDestroy()");
        int drainAt = delete.indexOf("drainForCleanup(\"delete\")");
        assertTrue(flushAt >= 0 && drainAt > flushAt,
                "delete() flushes the batched ledger before its drain");
        String discard = window(swapchain, "private void discardPreRecordedTransitions() {", 1500);
        int discardFlushAt = discard.indexOf("celtium$flushBatchedBeforeDestroy()");
        int discardDrainAt = discard.indexOf("drainForCleanup(\"present transition pool\")");
        assertTrue(discardFlushAt >= 0 && discardDrainAt > discardFlushAt,
                "discarding the transition set flushes before its pool drain");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
