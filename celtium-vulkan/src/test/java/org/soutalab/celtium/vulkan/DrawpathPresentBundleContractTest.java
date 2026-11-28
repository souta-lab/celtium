package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drawpath audit, present bundle (L-P): DP-16, DP-17 and DP-18. These are source contracts, in this
 * codebase's idiom for device-bound shapes: the properties they pin (a failed borrow's fatal verdict
 * is latched on the swapchain wrapper; a present-path device loss reaches the context's loss funnel
 * and the backend's stall latch; the present body's rebuild consumption and re-borrow run on the
 * exception path as well) are exactly the ones whose drift would silently reopen the holes - each of
 * them was, before this bundle, a path where a failure left no record and no recovery. The proof on a
 * real device belongs to the confirmation legs, not this gate.
 */
class DrawpathPresentBundleContractTest {

    private static final String PRESENT_SWAPCHAIN_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java";
    private static final String SWAPCHAIN_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java";
    private static final String FRAME_TARGET_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java";
    private static final String BACKEND_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
    private static final String COORDINATOR =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java";
    private static final String ADAPTER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java";
    private static final String MIXIN_SOURCE =
            "forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java";

    // DP-16: the borrow acquire's fatal verdicts are classified, kept and latched.

    /** The fatal classification is exactly the three verdicts that name a recovery. */
    @Test
    void borrowVerdictClassificationIsExactlyTheFatalThree() {
        assertTrue(GllessFrameTarget.celtium$isFatalBorrowVerdict(
                org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR));
        assertTrue(GllessFrameTarget.celtium$isFatalBorrowVerdict(
                org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR));
        assertTrue(GllessFrameTarget.celtium$isFatalBorrowVerdict(
                org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST));
        assertFalse(GllessFrameTarget.celtium$isFatalBorrowVerdict(org.lwjgl.vulkan.VK10.VK_SUCCESS));
        assertFalse(GllessFrameTarget.celtium$isFatalBorrowVerdict(
                org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR));
        assertFalse(GllessFrameTarget.celtium$isFatalBorrowVerdict(
                org.lwjgl.vulkan.VK10.VK_ERROR_INITIALIZATION_FAILED));
        assertFalse(GllessFrameTarget.celtium$isFatalBorrowVerdict(org.lwjgl.vulkan.VK10.VK_TIMEOUT));
    }

    /** The verdict is recorded at the acquire seat, where the result is this call's own. */
    @Test
    void theBorrowVerdictIsRecordedAtTheAcquireSeat() throws IOException {
        String target = readRepoFile(FRAME_TARGET_SOURCE);
        int acquire = target.indexOf("engine.acquireNextImage(acquireSemaphore, frameSlot)");
        assertTrue(acquire >= 0, "the borrow acquire seat exists");
        String seat = target.substring(acquire, Math.min(target.length(), acquire + 1600));
        assertTrue(seat.contains("celtium$isFatalBorrowVerdict(acquireResult)"),
                "the failed acquire classifies its own result");
        assertTrue(seat.contains("this.pendingBorrowAcquireVerdict = acquireResult"),
                "a fatal verdict is kept for the caller");
        assertTrue(target.contains("public int celtium$takeBorrowAcquireVerdict()"),
                "the verdict is handed out consume-on-read");
    }

    /** The borrow's caller consumes the verdict and latches it on the swapchain wrapper. */
    @Test
    void theBorrowVerdictIsLatchedByTheCaller() throws IOException {
        String present = readRepoFile(PRESENT_SWAPCHAIN_SOURCE);
        String ensure = region(present, "void celtium$ensureBorrowedFrameImage()",
                "void runSwapchainProbe()");
        assertTrue(ensure.contains("target.celtium$takeBorrowAcquireVerdict()"),
                "the borrow ensure consumes the verdict");
        assertTrue(ensure.contains("funnel.swapchain.celtium$noteBorrowOutOfDate()"),
                "an out-of-date borrow latches the rebuild");
        assertTrue(ensure.contains("funnel.swapchain.celtium$noteBorrowSurfaceLost()"),
                "a lost-surface borrow latches the surface recreation");
        assertTrue(ensure.contains("celtium$reportDeviceLost(\"the borrow acquire\", borrowVerdict)"),
                "a device-lost borrow reaches the context's loss funnel");

        String swapchain = readRepoFile(SWAPCHAIN_SOURCE);
        assertTrue(swapchain.contains("void celtium$noteBorrowOutOfDate()"),
                "the wrapper records a borrow out-of-date");
        assertTrue(swapchain.contains(
                "celtium$noteRebuildNeeded(\"the borrow acquire reported the swapchain out of date\")"),
                "the borrow out-of-date asks for the same sticky rebuild as the composed acquire");
        assertTrue(swapchain.contains("void celtium$noteBorrowSurfaceLost()"),
                "the wrapper records a borrow surface loss");
    }

    // DP-17: a present-path device loss reaches the context funnel and the backend's stall latch.

    /** Every present path classifies VK_ERROR_DEVICE_LOST through the one funnel helper. */
    @Test
    void presentDeviceLossesReachTheContextFunnel() throws IOException {
        String swapchain = readRepoFile(SWAPCHAIN_SOURCE);
        assertTrue(swapchain.contains("private void celtium$notePresentDeviceLost(String operation)"),
                "the present paths share one device-loss note");
        assertTrue(swapchain.contains("this.context.celtium$reportDeviceLost(operation, VK_ERROR_DEVICE_LOST)"),
                "the note enters the context's loss funnel");
        int notes = countOccurrences(swapchain, "celtium$notePresentDeviceLost(\"");
        assertTrue(notes >= 4,
                "the composed acquire, the composed present, the direct present and the probe each "
                        + "classify a device loss (found " + notes + ")");
    }

    /** The backend syncs its stall latch from the context's loss fact, at the present body's edges. */
    @Test
    void theStallLatchSyncsFromTheContextLossFact() throws IOException {
        String backend = readRepoFile(BACKEND_SOURCE) + readRepoFile(COORDINATOR) + readRepoFile(ADAPTER);
        assertTrue(backend.contains("void celtium$noteStalledOnDeviceLoss()"),
                "the backend has the sync door");
        String door = region(backend, "void celtium$noteStalledOnDeviceLoss()",
                "static final int FAIL_IDLE_AFTER");
        assertTrue(door.contains("this.context.celtium$isDeviceLost()"),
                "the door reads the context's loss fact");
        assertTrue(door.contains("this.frame.noteStalled()"),
                "the door latches the backend's own stall");

        String body = presentFrameBody(readRepoFile(PRESENT_SWAPCHAIN_SOURCE));
        assertEquals(2, countOccurrences(body, "funnel.celtium$noteStalledOnDeviceLoss()"),
                "the sync runs at the body's head and after its present attempt");
    }

    // DP-18: the present body's tail runs on the exception path too; the skip total is printed.
    // FP-1 succession (plan 8.3 item 8, S3-b): the tail's re-borrow moved to the frame head -
    // the backend's beginCommands ensures the borrow before the recording opens - so the
    // consumption is the tail's remaining half, and the borrow's seat is pinned here instead.

    /** The rebuild consumption sits after the catch, and the borrow sits at the frame head. */
    @Test
    void thePresentBodyTailRunsAfterTheCatch() throws IOException {
        String body = presentFrameBody(readRepoFile(PRESENT_SWAPCHAIN_SOURCE));
        int catchSeat = body.indexOf("funnel.lastPresentResult = \"present threw: \" + error;");
        assertTrue(catchSeat >= 0, "the present body's catch exists");
        String afterCatch = body.substring(catchSeat);
        int consume = afterCatch.indexOf("consumeSwapchainRebuild();");
        int returned = afterCatch.indexOf("return reached;");
        assertTrue(consume > 0, "the rebuild consumption runs after the catch");
        assertTrue(returned > consume, "the body returns the attempt's result after its tail");
        assertEquals(1, countOccurrences(body, "consumeSwapchainRebuild();"),
                "the consumption exists once - the in-try copy was moved, not duplicated");
        assertEquals(0, countOccurrences(body, "celtium$ensureBorrowedFrameImage();"),
                "the present body no longer re-borrows: FP-1 moved the borrow to the frame head");

        String backend = readRepoFile(BACKEND_SOURCE) + readRepoFile(COORDINATOR) + readRepoFile(ADAPTER);
        int begin = backend.indexOf("void beginFrame(boolean packActive");
        assertTrue(begin >= 0, "the frame begin exists (E7: beginCommands integrated into beginFrame)");
        int headBorrow = backend.indexOf("this.presentSwapchain.celtium$ensureBorrowedFrameImage();", begin);
        int reset = backend.indexOf("vkResetCommandBuffer(this.commandBuffer, 0)", begin);
        assertTrue(headBorrow > begin, "the frame head ensures the borrow (FP-1)");
        assertTrue(reset > headBorrow,
                "the borrow is ensured before the recording opens - the ensure refuses while"
                        + " a recording is open");
    }

    /** The heartbeat prints the aggregate skip count beside the per-reason split. */
    @Test
    void thePresentHeartbeatPrintsTheSkipTotal() throws IOException {
        String present = readRepoFile(PRESENT_SWAPCHAIN_SOURCE);
        assertTrue(present.contains("skips=\" + funnel.presentSkips"),
                "CELTIUM_PRESENT carries the aggregate presentSkips a leg can gate on");
        assertTrue(present.contains("skipsByReason=[noTarget/stalled="),
                "the per-reason split stays beside the total");
    }

    // DP-14: the mixin's catches report instead of discarding.

    /** Both swap-point catches name the note, one per seat. */
    @Test
    void theMixinCatchesReportTheirExceptions() throws IOException {
        String mixin = readRepoFile(MIXIN_SOURCE);
        assertTrue(mixin.contains(
                "celtium$noteSwapPointException(\"present\", error)"),
                "the present catch reports its exception");
        assertTrue(mixin.contains(
                "celtium$noteSwapPointException(\"swap\", error)"),
                "the swap-consume catch reports its exception");
    }

    private static String presentFrameBody(String present) {
        return region(present, "private boolean celtium$presentFrameBody()",
                "private void dumpFrameIfRequested");
    }

    private static String region(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        assertTrue(start >= 0, "region start exists: " + startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(end > start, "region end exists after its start: " + endMarker);
        return source.substring(start, end);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }

    private static String readRepoFile(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
}
