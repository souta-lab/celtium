package org.embeddedt.embeddium.impl.render.chunk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * BH-25's retry, second half (3ad79e263): a section whose upload failed is re-queued, not just
 * skipped.
 * <p>
 * The first half of BH-25 skipped the commit for a failed upload, on the assumption that
 * skipping left the section queued. Submission had already consumed it - the pending update was
 * cleared, the section was out of the pending mesh set, and its cancellation token pointed at
 * the submitted job - so a skipped section was stranded with no path back into a build and its
 * chunk stayed missing until some unrelated update touched it. The landed fix re-queues
 * explicitly, for the latest submission only: clear the finished job's token, request the same
 * kind of update again (REBUILD for build outputs, SORT for sort outputs), re-add the section
 * to the pending mesh set and mark the graph dirty.
 * <p>
 * Driving {@code processChunkBuildResults} needs a live terrain backend and a section graph, so
 * the re-queue is pinned structurally here, in the shape
 * {@code ScheduleRebuildDedupThreadContractTest} established: every element of the retry must be
 * present inside the failed-upload branch, in the branch's own window of the method.
 */
class FailedUploadRequeueContractTest {
    private static final String MANAGER_SOURCE =
            "common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java";

    @Test
    void aFailedUploadIsRequeuedNotJustSkipped() throws IOException {
        String source = readSource(MANAGER_SOURCE);
        int start = source.indexOf("private void processChunkBuildResults(");
        assertTrue(start >= 0, "processChunkBuildResults is declared");
        String body = source.substring(start, start + 9000);

        int branch = body.indexOf("failedUploads.contains(result.render)");
        assertTrue(branch >= 0, "the failed-upload branch is present");
        String retry = body.substring(branch, branch + 3500);

        assertTrue(retry.contains("result.buildTime >= result.render.getLastSubmittedFrame()"),
                "only the latest submission is retried - a stale result's replacement is already"
                        + " in flight and commits (or retries) on its own");
        assertTrue(retry.contains("!result.render.isDisposed()"),
                "a disposed section is not re-queued");
        assertTrue(retry.contains("setBuildCancellationToken(null)"),
                "the finished job's token is cleared, so the retry is not cancelled by it");
        assertTrue(retry.contains("ChunkUpdateType.REBUILD") && retry.contains("ChunkUpdateType.SORT"),
                "the retry requests the same kind of update the failed submission was");
        assertTrue(retry.contains("requestUpdate(retry)"),
                "the update is actually requested again");
        assertTrue(retry.contains("celtiumPendingMeshUpdates.add(result.render)"),
                "the section re-enters the pending mesh set submission removed it from");
        assertTrue(retry.contains("markGraphDirty()"),
                "the graph is dirtied so the search picks the re-queued section up");
    }

    private static String readSource(String repoRelative) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path fromRoot = dir.resolve(repoRelative);
            if (Files.isRegularFile(fromRoot)) {
                return Files.readString(fromRoot);
            }
            // When the test working directory is the common module itself, the path is module-relative.
            Path moduleRelative = dir.resolve(repoRelative.substring("common/".length()));
            if (Files.isRegularFile(moduleRelative)) {
                return Files.readString(moduleRelative);
            }
            dir = dir.getParent();
        }
        return fail("source not found from " + System.getProperty("user.dir") + ": " + repoRelative);
    }
}
