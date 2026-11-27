package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D1 L4 follow-up: the reader wiring for two present counters. The L4 leg's pass shape asked for
 * two readings the run could not produce: that the pre-recorded transition set is re-recorded
 * across a swapchain generation change (the {@code preRecordedTransitionPresents} counter, whose
 * increments mark exactly that) and that abandoned acquisitions move in step with the control arm
 * ({@code abandonedAcquisitions}, whose getter named the verification line but had no caller).
 * Neither counter reached any printed line - the transition counter was never wired in any
 * generation, and the loss aggregate's key set does not carry either. The smallest wiring is the
 * CELTIUM_PRESENT_TIMING line the L4 leg already read its blit census from: both counters join
 * {@code presentTimingSummary()}, the line's one source. These contracts pin the printed form so
 * the wiring cannot silently fall off again.
 */
class PresentTimingReadersContractTest {

    /** The summary prints the transition re-record count in key=value form. */
    @Test
    void theTimingSummaryPrintsTheTransitionReRecordCount() throws IOException {
        String summary = summaryBody();
        assertTrue(summary.contains(
                "\" preRecordedTransitionPresents=\" + this.preRecordedTransitionPresents"),
                "the re-record census rides the timing summary, so a leg can read the delta"
                        + " across a generation change");
    }

    /** The summary prints the abandoned acquisition count in key=value form. */
    @Test
    void theTimingSummaryPrintsTheAbandonedAcquisitionCount() throws IOException {
        String summary = summaryBody();
        assertTrue(summary.contains("\" abandonedAcquisitions=\" + this.abandonedAcquisitions"),
                "the abandoned census rides the timing summary, so both arms of a leg can be"
                        + " compared as counts rather than by event lines");
    }

    /** The CELTIUM_PRESENT_TIMING line carries the summary, so the counters reach the run's log. */
    @Test
    void theTimingLineCarriesTheSummary() throws IOException {
        String source = swapchainSource();
        assertTrue(source.contains("CELTIUM_PRESENT_TIMING \" + presentTimingSummary()"),
                "the printed line is built from the summary the counters were added to");
    }

    private static String summaryBody() throws IOException {
        String source = swapchainSource();
        int start = source.indexOf("public String presentTimingSummary()");
        int end = source.indexOf("private void celtium$notePresentTime", start);
        assertTrue(start >= 0 && end > start, "the timing summary is present");
        return source.substring(start, end);
    }

    private static String swapchainSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java"));
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
