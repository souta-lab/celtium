package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drawpath audit DP-14: the swap point's two mixin catches used to discard
 * {@code RuntimeException | LinkageError} without a count or a line, so a present that threw every
 * frame was indistinguishable from a present that was never attempted. The note the catches now make
 * is pinned here as the positive control: an injected exception moves the counter the verification
 * summary prints ({@code swapPointExceptions}) and is reported to stderr on the pinned cadence.
 */
class SwapPointExceptionNoteTest {

    /** An injected exception is counted, and the first one is reported to stderr with its seat. */
    @Test
    void anInjectedExceptionIsCountedAndReported() {
        int savedCount = VulkanBackendAdapter.swapPointExceptions;
        PrintStream savedErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            VulkanBackendAdapter.swapPointExceptions = 0;
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            VulkanBackendAdapter.celtium$noteSwapPointException("present",
                    new RuntimeException("injected present failure"));
            assertEquals(1, VulkanBackendAdapter.swapPointExceptions,
                    "the note counts the swallowed exception");
            String reported = captured.toString(StandardCharsets.UTF_8);
            assertTrue(reported.contains("CELTIUM_SWAPPOINT_EXCEPTION"),
                    "the first exception is reported, got: " + reported);
            assertTrue(reported.contains("seat=present"), "the report names its seat: " + reported);
            assertTrue(reported.contains("injected present failure"),
                    "the report carries the exception: " + reported);
        } finally {
            System.setErr(savedErr);
            VulkanBackendAdapter.swapPointExceptions = savedCount;
        }
    }

    /** Every note counts, including the ones the cadence does not report. */
    @Test
    void everyNoteCounts() {
        int before = VulkanBackendAdapter.swapPointExceptions;
        VulkanBackendAdapter.celtium$noteSwapPointException("swap",
                new RuntimeException("injected swap failure"));
        VulkanBackendAdapter.celtium$noteSwapPointException("swap",
                new LinkageError("injected linkage failure"));
        assertEquals(before + 2, VulkanBackendAdapter.swapPointExceptions,
                "both catches' exceptions land on the one counter");
    }

    /** The report cadence: the first three, then every 300th - a start is named, a flood is not. */
    @Test
    void theReportCadenceIsBounded() {
        assertTrue(VulkanBackendAdapter.celtium$swapPointExceptionReported(1));
        assertTrue(VulkanBackendAdapter.celtium$swapPointExceptionReported(2));
        assertTrue(VulkanBackendAdapter.celtium$swapPointExceptionReported(3));
        assertFalse(VulkanBackendAdapter.celtium$swapPointExceptionReported(4));
        assertFalse(VulkanBackendAdapter.celtium$swapPointExceptionReported(299));
        assertTrue(VulkanBackendAdapter.celtium$swapPointExceptionReported(300));
        assertFalse(VulkanBackendAdapter.celtium$swapPointExceptionReported(301));
        assertTrue(VulkanBackendAdapter.celtium$swapPointExceptionReported(600));
    }
}
