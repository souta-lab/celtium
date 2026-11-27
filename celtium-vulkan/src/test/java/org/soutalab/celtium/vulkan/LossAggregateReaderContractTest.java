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
 * Contracts for the drawpath audit's reader wiring (X-1): the verification line carries a loss
 * aggregate a leg gate can fail on, the flush-boundary census has an output seat, the log limiter's
 * suppression counts are readable mid-run, and the counters that could never move - they had no
 * increment site anywhere - are gone instead of printing a permanent zero a reader could mistake
 * for health. The summary is built inside device-bound code, so the wiring is pinned by source
 * contracts in the shape {@code FallbackWiringContractTest} established.
 */
class LossAggregateReaderContractTest {

    private static final String DIAGNOSTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java";
    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String TARGET_STATISTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TargetStatistics.java";
    private static final String IMMEDIATE_STATISTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateStatistics.java";
    private static final String FALLBACK_PATHS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FallbackPaths.java";

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
    void verificationSummaryCarriesTheLossAggregate() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("lossAggregateSummary()"),
                "the verification summary must splice the loss aggregate");
        assertTrue(diagnostics.contains("\"lossAggregate=[postAcceptDroppedGroups=\""),
                "the aggregate opens with its first member");
        for (String member : new String[] {"orderingFlushFailures", "orderingFlushDroppedBatches",
                "stalledGeometryDrops", "stalledGeometryDroppedBatches", "unflushedTargetGroupsDropped",
                "windowFlushFailures", "staleGroupRefusals"}) {
            assertTrue(diagnostics.contains(member + "="), "aggregate member missing: " + member);
        }
        assertTrue(diagnostics.contains("\",total=\" + total + \"]\""),
                "the aggregate closes with the summed total the gates read");
    }

    @Test
    void flushBoundaryCensusHasAnOutputSeatInTheSummary() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("flushBoundarySummary()"),
                "the verification summary must splice the flush-boundary census");
        assertTrue(diagnostics.contains("FlushBoundaryCensus.current()"),
                "the census is read from its single current instance");
        assertTrue(diagnostics.contains("\"flushBoundary=[\""),
                "the census prints under its own bracket");
    }

    @Test
    void logLimiterSuppressionIsReadableFromTheSummary() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("CeltiumLogLimiter.suppressionSummary()"),
                "the verification summary must carry the limiter's suppression counts");
    }

    @Test
    void zombieCountersAreNotPrinted() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        for (String key : new String[] {"drawsWithoutCommandBuffer=", "immediateDeferredNextFrame=",
                "unflushedTargetPassFailures=", "lastUnflushedTargetFailure=",
                "windowFlushDroppedBatches=", "compositePresentRefusals=",
                "outsideWorldPassRefusals="}) {
            assertFalse(diagnostics.contains(key), "zombie key still printed: " + key);
        }
        // The key collision: offTargetRefusals= was printed twice, the live reason map first and a
        // counter that could never move second, so a last-wins reader always read zero. One print
        // remains - the live map.
        assertEquals(1, count(diagnostics, "\" offTargetRefusals=\""),
                "offTargetRefusals must be printed exactly once, by the live reason map");
    }

    @Test
    void zombieCountersAreRemovedFromTheStatistics() throws IOException {
        String target = read(TARGET_STATISTICS);
        for (String member : new String[] {"offTargetRefusals", "outsideWorldPassRefusals",
                "compositePresentRefusals", "unflushedTargetPassFailures", "windowFlushDroppedBatches"}) {
            assertFalse(target.contains(member), "zombie member still in TargetStatistics: " + member);
        }
        String immediate = read(IMMEDIATE_STATISTICS);
        for (String member : new String[] {"drawsWithoutCommandBuffer", "immediateDeferredNextFrame"}) {
            assertFalse(immediate.contains(member),
                    "zombie member still in ImmediateStatistics: " + member);
        }
        String backend = read(BACKEND);
        assertFalse(backend.contains("lastUnflushedTargetFailure"),
                "the never-written last-failure text is removed with its counter");
        assertFalse(backend.contains("immediateDeferredNextFrame()"),
                "the flush snapshot no longer reads the removed counter");
    }

    @Test
    void droppedByFormatIsWiredAtThePostAcceptDropSite() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains(
                "final int[] postAcceptDroppedByFormat = new int[VulkanImmediateFormats.COUNT];"),
                "the per-format drop row is sized from the format count, like drawnByFormat");
        assertTrue(backend.contains("this.postAcceptDroppedByFormat[group.formatId] += group.batches;"),
                "the replacement-boundary drop increments the row per group and format");
    }

    @Test
    void unwiredFallbackPathNamesAreNotDeclared() throws IOException {
        String paths = read(FALLBACK_PATHS);
        for (String constant : new String[] {"PRESENT_DIRECT", "SLAB_ALLOC", "GLSM_ASYNC"}) {
            assertFalse(paths.contains("String " + constant + " ="),
                    "unwired path constant still declared: " + constant);
        }
        for (String constant : new String[] {"TERRAIN_ENGINE", "TERRAIN_UPLOAD", "SYNC_TIMELINE",
                "RING_ALLOC"}) {
            assertTrue(paths.contains("String " + constant + " ="),
                    "wired path constant missing: " + constant);
        }
    }
}
