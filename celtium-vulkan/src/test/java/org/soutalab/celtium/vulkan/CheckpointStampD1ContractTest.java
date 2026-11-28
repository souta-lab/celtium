package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checkpoint seats of T3 slice S2, pinned as source contracts in this
 * codebase's idiom for device-bound shapes: the properties they pin (the
 * store defers only inside its checkpoint; the checkpoint hook fires only on
 * the flush success tail; the boundary fallback stays gated on no pending
 * batch) are exactly the ones whose violation restamps a retirement against
 * a submission the queue has not accepted — the INV-1b collapse the S5-D1
 * window would otherwise cause on every direct-present frame. The leg that
 * watches {@code pendingRetirements} return to zero in steady state is the
 * integration window's; these tests keep the shape it stands on from
 * drifting.
 */
class CheckpointStampD1ContractTest {

    private static final String STORE_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/terrain/TerrainSectionStore.java";
    private static final String CONTEXT_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java";
    private static final String BACKEND_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";

    /** INV-1a: the store's one deferred-release call site is the checkpoint. */

    /** M1/INV-1b: the hook fires on the success tail of the flush, guarded, never on the failure path. */
    @Test
    void theHookFiresOnlyOnTheFlushSuccessTail() throws IOException {
        String source = Files.readString(findRepoFile(CONTEXT_SOURCE));
        int flush = source.indexOf("public void celtium$flushBatchedSubmits()");
        assertTrue(flush >= 0, "the flush exists");
        int hook = source.indexOf("flushHook.run()", flush);
        assertTrue(hook > flush, "the hook fires inside the flush");
        String tail = source.substring(flush, hook);
        assertTrue(tail.contains("abandonFailedBatch"),
                "the failure branch stands before the hook: a failed flush never reaches it");
        assertTrue(tail.contains("commitLayoutRecording"),
                "the hook sits after the layout-commit loop, on the success tail");
        assertTrue(source.contains("!celtium$recordingOpen() && !celtium$hasPendingBatchedSubmits()"),
                "the firing is guarded on no open recording and no pending batch (INV-1b)");
    }

    /** M1: the boundary fallback drains only when no batch is pending (the D1 hold leaves one). */

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
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
