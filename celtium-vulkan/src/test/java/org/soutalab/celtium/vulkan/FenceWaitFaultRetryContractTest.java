package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-56's injection redesign: the fence-wait fault ({@code celtium.vulkan.failFenceWait}) used to forge its
 * VK_TIMEOUT only once, before waitForSlot's retry loop, and the loop's first real re-await overwrote the
 * forgery with its true SUCCESS - so the stall latch seat behind the loop (S4/S6) was structurally
 * unreachable for this injection, which is why the bundle leg's 7,247 injections produced zero
 * CELTIUM_STALL lines (desk record OVERHAUL-R56-STALL-LATCH-DESK-2026-11-26.md). The redesign re-assigns
 * the forgery after every re-await inside both retry loops (the timeline branch and the fence branch), so
 * an injected wait runs its full bounded course - the initial wait plus IDLE_WAIT_RETRIES retries, each
 * nominally 5 s but answered at once by a healthy device - and reaches noteStalled().
 * <p>
 * These are source contracts, in this codebase's idiom for device-bound shapes: the properties they pin
 * (the forgery is re-applied per retry, on both branches; it stays gated on the one switch; the
 * no-fault disposition and the latch itself are untouched) are exactly the ones whose drift would either
 * re-break the injection or change production behaviour. The proof on a real device - CELTIUM_STALL
 * exactly once and a run that ends without hanging - is the post-landing leg's, not this gate's.
 */
class FenceWaitFaultRetryContractTest {

    private static final String BACKEND_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";

    private static final String FAULT_IF = "if (FENCE_WAIT_FAULT) {";
    private static final String FORGERY = "result = org.lwjgl.vulkan.VK10.VK_TIMEOUT;";
    private static final String RETRY_LOOP =
            "while (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT && retries < IDLE_WAIT_RETRIES)";
    private static final String TIMELINE_AWAIT = "timeline.awaitValueResult(timelineValue, IDLE_TIMEOUT_NANOS)";
    private static final String FENCE_AWAIT = "vkWaitForFences(this.context.device(), fences, true, IDLE_TIMEOUT_NANOS)";

    /** The forgery is re-applied after the real re-await inside both branches' retry loops. */
    @Test
    void theFaultReappliesInsideBothRetryLoops() throws IOException {
        String wait = waitForSlot(readBackend());
        List<Integer> loops = indicesOf(wait, RETRY_LOOP);
        assertEquals(2, loops.size(), "waitForSlot keeps its two bounded retry loops (timeline, fence)");

        assertReapplication(wait, loops.get(0), TIMELINE_AWAIT, "the timeline branch");
        assertReapplication(wait, loops.get(1), FENCE_AWAIT, "the fence branch");

        assertEquals(4, countOccurrences(wait, "CELTIUM_FENCE_TIMEOUT injected"),
                "each forgery (initial + retry, on both branches) reports itself the same way");
    }

    private static void assertReapplication(String wait, int loopStart, String awaitCall, String branch) {
        int seat = wait.indexOf("this.noteStalled();", loopStart);
        assertTrue(seat > loopStart, branch + ": the stall seat still stands behind the loop");
        String loop = wait.substring(loopStart, seat);
        int awaitAt = loop.indexOf(awaitCall);
        int faultAt = loop.indexOf(FAULT_IF);
        int forgeryAt = loop.indexOf(FORGERY);
        assertTrue(awaitAt >= 0, branch + ": the loop still re-awaits for real");
        assertTrue(faultAt > awaitAt, branch + ": the fault is consulted after the loop's real re-await");
        assertTrue(forgeryAt > faultAt, branch + ": the forgery is re-assigned under the fault switch");
    }

    /** The forgery exists only under the one switch: four gated assignments, no ungated one. */
    @Test
    void theFaultStaysGatedOnTheOneSwitch() throws IOException {
        String source = readBackend();
        assertEquals(1, countOccurrences(source,
                        "Boolean.getBoolean(\"celtium.vulkan.failFenceWait\")"),
                "the fault is read from the one documented switch, once");
        String wait = waitForSlot(source);
        assertEquals(4, countOccurrences(wait, FAULT_IF),
                "initial + retry forgery sites on both branches, each gated");
        List<Integer> forgeries = indicesOf(wait, FORGERY);
        assertEquals(4, forgeries.size(), "no forgery site beyond the four gated ones");
        for (int forgery : forgeries) {
            int gate = wait.lastIndexOf(FAULT_IF, forgery);
            assertTrue(gate >= 0, "every forgery stands under the fault switch");
            String between = wait.substring(gate, forgery);
            assertFalse(between.contains("awaitValueResult") || between.contains("vkWaitForFences"),
                    "no real await stands between a forgery and its gate (the gate is the if it sits in)");
        }
    }

    /** Ordering: each branch's initial forgery precedes its loop, and its re-application sits inside it. */
    @Test
    void eachBranchForgesBeforeAndInsideItsLoop() throws IOException {
        String wait = waitForSlot(readBackend());
        List<Integer> gates = indicesOf(wait, FAULT_IF);
        List<Integer> loops = indicesOf(wait, RETRY_LOOP);
        assertEquals(4, gates.size());
        assertEquals(2, loops.size());
        assertTrue(gates.get(0) < loops.get(0) && loops.get(0) < gates.get(1),
                "timeline branch: initial forgery before the loop, re-application inside it");
        assertTrue(gates.get(1) < gates.get(2),
                "the fence branch's initial forgery follows the timeline branch's disposition");
        assertTrue(gates.get(2) < loops.get(1) && loops.get(1) < gates.get(3),
                "fence branch: initial forgery before the loop, re-application inside it");
    }

    /** With the switch off, the disposition is the established one: bounded retries, latch, abandon. */
    @Test
    void withoutTheFaultTheDispositionIsUnchanged() throws IOException {
        String wait = waitForSlot(readBackend());
        assertEquals(4, countOccurrences(wait, "this.noteStalled();"),
                "the four latch seats inside waitForSlot (timeout + abnormal result, on both branches)");
        assertTrue(wait.contains("abandonFrame(\"frame slot timeline timed out\")"),
                "the timeline timeout seat still abandons the frame after latching");
        assertTrue(wait.contains("abandonFrame(\"frame slot fence timed out\")"),
                "the fence timeout seat still abandons the frame after latching");
        assertEquals(4, countOccurrences(wait, "this.submissionStatistics.noteIdleWaitTimeouts();"),
                "timeouts are still counted on entry to the retry disposition and again at the latch,"
                        + " on both branches");
    }

    /** The latch and the reader guards it feeds are untouched by the injection redesign. */
    @Test
    void theLatchAndItsGuardsAreUntouched() throws IOException {
        String source = readBackend();
        int latch = source.indexOf("private void noteStalled()");
        assertTrue(latch >= 0, "noteStalled exists");
        String body = source.substring(latch, latch + 1200);
        assertTrue(body.contains("this.gpuStalled = true;"), "the latch is still set in noteStalled");
        assertTrue(body.contains("CELTIUM_STALL"), "the latch still reports itself as CELTIUM_STALL");
        assertFalse(source.contains("gpuStalled = false"),
                "nothing clears the latch, by design: readers refuse work whose buffers may be in flight");
        assertTrue(countOccurrences(source, "gpuStalled") >= 30,
                "the reader-guard surface stands (33 occurrences measured on the redesign tree)");
    }

    private static String readBackend() throws IOException {
        return Files.readString(findRepoFile(BACKEND_SOURCE)).replace("\r\n", "\n");
    }

    /** waitForSlot's body: from its signature to the next member's javadoc. */
    private static String waitForSlot(String source) {
        int start = source.indexOf("private boolean waitForSlot(int slot)");
        assertTrue(start >= 0, "waitForSlot exists");
        int end = source.indexOf("\n    /**", start);
        assertTrue(end > start, "waitForSlot's end is found");
        return source.substring(start, end);
    }

    private static List<Integer> indicesOf(String text, String needle) {
        List<Integer> indices = new ArrayList<Integer>();
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            indices.add(at);
        }
        return indices;
    }

    private static int countOccurrences(String text, String needle) {
        return indicesOf(text, needle).size();
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
