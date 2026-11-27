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
 * Contracts for the drawpath audit's terrain/pack health wiring (DP-04, DP-05,
 * DP-36): the quantities a leg must gate on - executor disablement, head-of-line
 * blockage, slab capacity skips, latched pipeline failures, atlas backoffs, pack
 * terrain refusals and skipped regions - are counted at their seats and carried
 * into {@code CELTIUM_VERIFY}. The seats live inside device-bound methods (a
 * frame recording, an atlas re-supply, a pipeline creation), so the wiring is
 * pinned by source contracts in the shape {@code FallbackWiringContractTest}
 * established; the bridge half of DP-06 is covered behaviourally by
 * {@code DemonicaTerrainBridgeFailureTest}.
 */
class DrawpathTerrainHealthContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String DIAGNOSTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java";
    private static final String ENGINE_A =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java";
    private static final String HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";
    private static final String RENDERER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java";
    private static final String COPY_READ_SIDE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java";
    private static final String BRIDGE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DemonicaTerrainBridge.java";

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
    void verificationSummaryCarriesEveryTerrainHealthQuantity() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("\" terrainEngines=[\""),
                "the executor health summary is in CELTIUM_VERIFY");
        assertTrue(diagnostics.contains("engineHealthSummary()"),
                "the summary comes from the host that owns the executors");
        assertTrue(diagnostics.contains("\" failedPipelines=\" + b.terrain.failedPipelineCount()"),
                "latched pipeline failures are in CELTIUM_VERIFY");
        assertTrue(diagnostics.contains("\" atlasRefreshFailures=\" + b.atlasRefreshFailures"),
                "atlas backoff entries are in CELTIUM_VERIFY");
        assertTrue(diagnostics.contains("\" packTerrainRefused=\" + b.packTerrainRefused"),
                "the unbounded pack refusal total is in CELTIUM_VERIFY");
        assertTrue(diagnostics.contains(
                        "\" packTerrainSkippedRegions=\" + b.packTerrainSkippedRegions"),
                "skipped pack regions are in CELTIUM_VERIFY, not only in DRAWN lines");
        assertTrue(diagnostics.contains("DemonicaTerrainBridge.failureSummary()"),
                "bridge failures are in CELTIUM_VERIFY");
    }

    @Test
    void packRefusalsAreCountedBeforeThePrintCap() throws IOException {
        String backend = read(BACKEND);
        int seat = backend.indexOf("private void celtium$reportPackTerrainRefused");
        assertTrue(seat >= 0, "the refusal report seat is declared");
        int counted = backend.indexOf("this.packTerrainRefused++", seat);
        int cap = backend.indexOf("this.packTerrainRefusedLogs < 24", seat);
        assertTrue(counted > seat && counted < cap,
                "the total increments unconditionally, before the 24-line print cap");
        assertEquals(1, count(backend, "this.packTerrainRefused++"),
                "exactly one seat counts pack refusals");
    }

    @Test
    void slabCapacitySkipsAreCountedAtTheReceptacle() throws IOException {
        String engine = read(ENGINE_A);
        assertEquals(1, count(engine, "this.slabCapacitySkips++"),
                "exactly one seat counts slab capacity skips");
        int comment = engine.indexOf("Prescription A receptacle");
        int counted = engine.indexOf("this.slabCapacitySkips++");
        assertTrue(comment >= 0 && counted > comment,
                "the count sits in the recordDraws receptacle catch, not elsewhere");
        assertTrue(engine.contains("long slabCapacitySkips()"),
                "the executor exposes the total to the host summary");
    }

    @Test
    void hostSummaryAggregatesDisablementBlockageAndSkips() throws IOException {
        String host = read(HOST);
        int seat = host.indexOf("String engineHealthSummary()");
        assertTrue(seat >= 0, "the host aggregates executor health");
        String summary = host.substring(seat, host.indexOf("\n    }", seat));
        assertTrue(summary.contains("\" disabled=\""), summary);
        assertTrue(summary.contains("\" packDisabled=\""), summary);
        assertTrue(summary.contains("headOfLineBlocked()"),
                "the residency gate's head-of-line blockage is read from the gate itself");
        assertTrue(summary.contains("slabCapacitySkips()"),
                "per-executor slab skips are totalled");
    }

    @Test
    void atlasBackoffEntriesAreCountedAtTheFailureSeat() throws IOException {
        String copy = read(COPY_READ_SIDE);
        assertEquals(1, count(copy, "this.backend.atlasRefreshFailures++"),
                "exactly one seat counts atlas backoff entries");
        int failed = copy.indexOf("block atlas upload failed");
        int counted = copy.indexOf("this.backend.atlasRefreshFailures++");
        int backoff = copy.indexOf("this.backend.atlasRefreshCountdown = 400", counted);
        assertTrue(failed >= 0 && counted > failed && backoff > counted,
                "the count sits in the failure catch, beside the backoff it names");
    }

    @Test
    void pipelineFailureCountIsExposedByTheRenderer() throws IOException {
        String renderer = read(RENDERER);
        assertTrue(renderer.contains("int failedPipelineCount()"),
                "the renderer exposes the latched failure count");
        assertTrue(renderer.contains("return this.failedPipelines.size();"),
                "the count is the latch set itself, not a parallel counter");
    }

    @Test
    void bridgeFailuresReachCountersAtEverySeat() throws IOException {
        String bridge = read(BRIDGE);
        assertFalse(bridge.contains("catch (Throwable ignored)"),
                "no bridge seat swallows a failure silently anymore");
        assertEquals(2, count(bridge, "noteResolveFailure(") - 1,
                "both resolution seats record failures (one definition, two calls)");
        assertEquals(2, count(bridge, "noteInvokeFailure(") - 1,
                "both invocation seats record failures (one definition, two calls)");
        assertTrue(bridge.contains("error instanceof ClassNotFoundException"),
                "an absent guest is distinguished from a broken bridge");
    }
}
