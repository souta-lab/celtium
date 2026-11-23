package org.embeddedt.embeddium.impl.render.chunk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Item 730's late half of BH-25: a failure that arrives after its batch was committed must still re-queue.
 * <p>
 * The upload budget defers a burst's upload to a later frame's drain. When that drain fails, the backend names
 * the sections through the same {@code noteFailedUpload} set the unbudgeted path uses - but the sections were
 * already committed as built when the batch arrived, and the caller's consumption only ever matched the set
 * against the batch being processed on that same call. A late failure therefore matched nothing, and a section
 * that reads as built is never re-submitted: the hole was permanent. Worse, {@code uploadChunks} returned early
 * when no new results had arrived, so a quiet world never consumed the set at all.
 * <p>
 * The re-queue for late failures is deliberately not the in-batch retry: the build cancellation token no longer
 * belongs to the failed submission (a newer build may be in flight under it), so it must not be cleared. A fresh
 * REBUILD request, the pending-mesh set and a dirty graph are the whole of it. Driving the manager needs a live
 * backend and section graph, so the shape is pinned structurally, as in
 * {@link FailedUploadRequeueContractTest}.
 */
class DeferredFailureRequeueContractTest {
    private static final String MANAGER_SOURCE =
            "common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java";

    @Test
    void anEmptyResultFrameStillConsumesAndRequeuesLateFailures() throws IOException {
        String source = readSource(MANAGER_SOURCE);
        int start = source.indexOf("public void uploadChunks()");
        assertTrue(start >= 0, "uploadChunks is declared");
        String body = source.substring(start, start + 4000);

        int empty = body.indexOf("if (results.isEmpty())");
        assertTrue(empty >= 0, "the empty-results branch is present");
        String branch = body.substring(empty, empty + 1500);
        assertTrue(branch.contains("consumeFailedUploads()"),
                "an empty frame still consumes the failed set - a quiet world must not strand late failures");
        assertTrue(branch.contains("requeueLateFailedUploads(lateFailedUploads"),
                "the consumed late failures are re-queued on the empty path too");
    }

    @Test
    void aBatchRequeuesFailuresThatAreNotItsOwn() throws IOException {
        String source = readSource(MANAGER_SOURCE);
        int start = source.indexOf("private void processChunkBuildResults(");
        assertTrue(start >= 0, "processChunkBuildResults is declared");
        String body = source.substring(start, start + 12000);
        assertTrue(body.contains("requeueLateFailedUploads(failedUploads, inBatch)"),
                "failures naming sections outside the current batch are re-queued, not dropped after the loop");
    }

    @Test
    void theLateRequeueRequestsARebuildWithoutTouchingTheToken() throws IOException {
        String source = readSource(MANAGER_SOURCE);
        int start = source.indexOf("private void requeueLateFailedUploads(");
        assertTrue(start >= 0, "requeueLateFailedUploads is declared");
        String body = source.substring(start, start + 2500);

        assertTrue(body.contains("inCurrentBatch.contains(section)"),
                "sections of the current batch keep the in-batch retry, not this one");
        assertTrue(body.contains("section.isDisposed()"),
                "a disposed section is not re-queued");
        assertTrue(body.contains("requestUpdate(ChunkUpdateType.REBUILD)"),
                "the late failure requests a rebuild");
        assertTrue(body.contains("celtiumPendingMeshUpdates.add(section)"),
                "the section re-enters the pending mesh set");
        assertTrue(body.contains("markGraphDirty()"),
                "the graph is dirtied so the search picks the section up");
        assertFalse(body.contains("setBuildCancellationToken"),
                "the token may belong to a newer in-flight build - the late re-queue must never clear it");
    }

    private static String readSource(String repoRelative) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path fromRoot = dir.resolve(repoRelative);
            if (Files.isRegularFile(fromRoot)) {
                return Files.readString(fromRoot);
            }
            Path moduleRelative = dir.resolve(repoRelative.substring("common/".length()));
            if (Files.isRegularFile(moduleRelative)) {
                return Files.readString(moduleRelative);
            }
            dir = dir.getParent();
        }
        return fail("source not found from " + System.getProperty("user.dir") + ": " + repoRelative);
    }
}
