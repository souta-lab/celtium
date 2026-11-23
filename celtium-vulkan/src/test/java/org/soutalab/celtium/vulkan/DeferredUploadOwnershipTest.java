package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Item 730: the upload budget's deferred queue must own the data it drains.
 * <p>
 * The budgeted path queued the caller's outputs themselves and returned; the caller
 * ({@code RenderSectionManager.uploadChunks}) deleted every output on that same call, so the drain - a frame or
 * more later - read freed native buffers, failed with "Buffer has been deleted", and the sections, already
 * committed as built, stayed missing permanently. The fix copies the native buffers at enqueue time, inside the
 * backend, which is what the {@code TerrainGpuBackend.uploadMeshes} contract ("consume/copy native output data
 * before the caller deletes those outputs") already required.
 * <p>
 * The decision half is pinned as a pure function here. The copy itself is driven for real in common's
 * {@code DeferredOutputCopyTest} - this module's test classpath cannot initialise {@code NativeBuffer} (no
 * log4j), common's can. The wiring (enqueue copies, drain deletes its copies and names its failures, world
 * teardown deletes what is still queued) lives behind a live device, so it is pinned as source contracts.
 */
class DeferredUploadOwnershipTest {

    @Test
    void onlyABurstWithABudgetIsDeferred() {
        assertFalse(VulkanTerrainGpuBackend.celtium$shouldDeferUpload(0, 32, 33),
                "budget 0 is the default and must never defer");
        assertFalse(VulkanTerrainGpuBackend.celtium$shouldDeferUpload(8, 32, 32),
                "a batch at the burst threshold goes straight through");
        assertTrue(VulkanTerrainGpuBackend.celtium$shouldDeferUpload(8, 32, 33),
                "budget > 0 and batch > burst defers");
        assertTrue(VulkanTerrainGpuBackend.celtium$shouldDeferUpload(8, 32, 512),
                "a large burst defers");
    }

    @Test
    void theEnqueueQueuesCopiesAndTheDrainOwnsThem() throws IOException {
        String source = backendSource();

        int enqueue = source.indexOf("celtium$shouldDeferUpload(UPLOAD_BUDGET, UPLOAD_BURST, results.size())");
        assertTrue(enqueue >= 0, "the budgeted branch is decided by the pinned decision function");
        int enqueueEnd = source.indexOf("Reference2ReferenceMap<RenderRegion, List<ChunkTaskOutput>> queues",
                enqueue);
        String enqueueBody = source.substring(enqueue, enqueueEnd);
        assertTrue(enqueueBody.contains("celtium$copyOutputForDeferral(output)"),
                "the enqueue queues a backend-owned copy, never the caller's output");
        assertTrue(enqueueBody.contains("queue.add(copy)"),
                "the deferred queue receives the copy");
        assertTrue(enqueueBody.contains("noteFailedUpload(output.render)"),
                "a failed copy names its section for the late-failure re-queue instead of committing it as built");

        int drain = source.indexOf("public void celtium$drainDeferredUploads()");
        assertTrue(drain >= 0, "the drain is declared");
        int drainEnd = source.indexOf("public void uploadMeshes(", drain);
        String drainBody = source.substring(drain, drainEnd);
        assertTrue(drainBody.contains("noteFailedUpload(output.render)"),
                "a failed drain names every section of the region (BH-25), so the failure is re-queued, "
                        + "not a permanent hole in a section already committed as built");
        assertTrue(drainBody.contains("output.delete()"),
                "the drain deletes the copies it consumed - they are backend-owned and nobody else frees them");

        int teardown = source.indexOf("public void celtium$releaseWorldResources()");
        assertTrue(teardown >= 0, "the world teardown is declared");
        String teardownBody = source.substring(teardown, teardown + 1500);
        assertTrue(teardownBody.contains("celtium$deleteDeferredOutputs()"),
                "world teardown deletes the copies still queued instead of leaking their native buffers");
    }

    private static String backendSource() throws IOException {
        String repoRelative =
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path fromRoot = dir.resolve(repoRelative);
            if (Files.isRegularFile(fromRoot)) {
                return Files.readString(fromRoot);
            }
            Path moduleRelative = dir.resolve(repoRelative.substring("celtium-vulkan/".length()));
            if (Files.isRegularFile(moduleRelative)) {
                return Files.readString(moduleRelative);
            }
            dir = dir.getParent();
        }
        return fail("source not found from " + System.getProperty("user.dir") + ": " + repoRelative);
    }
}
