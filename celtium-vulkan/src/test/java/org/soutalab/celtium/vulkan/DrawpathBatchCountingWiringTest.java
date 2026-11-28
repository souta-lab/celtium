package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drawpath audit's batch-counting bundle (L-B: DP-01 aggregation side, DP-09, DP-10,
 * DP-12, DP-15, DP-33, and the X-1 offTargetRefusals key collision). The dispositions are
 * source shapes - a fallback's return value, a counter's print seat, a removed zombie - so
 * they are pinned in the house source-contract style; the behaviour on a live scene is the
 * confirmation legs' evidence (the R-79 leg doubles as the new counters' positive control).
 */
class DrawpathBatchCountingWiringTest {

    private static final String ROOT = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";
    private static final String BACKEND = ROOT + "Lane2ImmediateRecorder.java";
    private static final String DIAGNOSTICS = ROOT + "BackendDiagnostics.java";
    private static final String TARGET_STATISTICS = ROOT + "TargetStatistics.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    /** The body of one method: from its signature to the first method-closing return-true shape after it. */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "the method exists: " + signature);
        int end = source.indexOf("\n    }\n", start);
        assertTrue(end > start, "the method body closes: " + signature);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("DP-12: the twin fallbacks return false on a drop, true only on a draw")
    void fallbacksReturnFalseOnDrop() throws IOException {
        String backend = read(BACKEND);
        assertFalse(backend.contains("@return always true: the group is consumed here"),
                "no fallback still promises success on a drop");
        assertEquals(2, occurrences(backend, "this.batchDroppedDraws++;\n            return false;"),
                "the particle and direct fallbacks count the drop and return false");
        String emitter = methodBody(backend, "private boolean celtium$emitNonQuadGroup(");
        int refusal = emitter.indexOf("this.batchDroppedDraws++;");
        assertTrue(refusal >= 0 && emitter.indexOf("return false;", refusal) > refusal,
                "the emitter's record refusal returns false");
        String particle = methodBody(backend, "private boolean celtium$drawParticleGroupFallback(");
        assertTrue(particle.indexOf("return false;") >= 0
                        && particle.indexOf("return true;") > particle.indexOf("mesh.bindAndDrawInstanced"),
                "the particle fallback returns true only after its draw");
        String direct = methodBody(backend, "private boolean celtium$drawGroupDirect(");
        assertTrue(direct.indexOf("return false;") >= 0
                        && direct.indexOf("return true;") > direct.indexOf("mesh.bindAndDrawInstanced"),
                "the direct fallback returns true only after its draw");
    }

    @Test
    @DisplayName("DP-12: the drain gates the drawn accounting and the sky census on groupDrew")
    void drainGatesDrawnAccountingOnGroupDrew() throws IOException {
        String backend = read(BACKEND);
        int drain = backend.indexOf("private void drawPendingImmediate(Long onlyTarget)");
        assertTrue(drain >= 0, "the drain exists");
        String body = backend.substring(drain, drain + 120000);
        assertTrue(body.contains("boolean groupDrew = false;"),
                "the drain declares the drawn flag beside the capture flag");
        assertTrue(body.contains("groupDrew = batchCaptured || fallbackDrawn"),
                "the flag is computed from the three routes after the dispatch");
        assertTrue(body.contains("if (skyGroup && groupDrew) {"),
                "the sky census counts only groups that drew");
        int gate = body.indexOf("if (groupDrew) {");
        int recorded = body.indexOf("this.immediateStatistics.addImmediateRecordedBatches(batches);");
        int drawn = body.indexOf("this.immediateStatistics.noteImmediateGroupsDrawn();");
        int flushed = body.indexOf("this.immediateStatistics.noteImmediateGroupsFlushed();");
        assertTrue(gate >= 0 && recorded > gate && drawn > gate,
                "the recorded/drawn counters sit inside the gate");
        assertTrue(flushed > drawn,
                "the flushed counter remains, after the gate");
        String gateBody = body.substring(gate, flushed);
        assertTrue(gateBody.contains("this.drawnByFormat[group.formatId] += batches;"),
                "the per-format drawn row is gated too");
        assertFalse(gateBody.contains("noteImmediateGroupsFlushed"),
                "the flushed counter is not inside the gate: the group was flushed either way");
    }

    @Test
    @DisplayName("DP-12/DP-01: the loss counters and the refusal total print on CELTIUM_VERIFY")
    void verifyPromotions() throws IOException {
        String backend = read(BACKEND);
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(backend.contains("    long batchDroppedDraws;"),
                "the dropped-draws counter is package-private for the summary");
        assertTrue(backend.contains("    long batchDiscardedSegments;"),
                "the discarded-segments counter is package-private for the summary");
        assertTrue(diagnostics.contains("\" batchDroppedDraws=\" + b.batchDroppedDraws"),
                "VERIFY prints batchDroppedDraws");
        assertTrue(diagnostics.contains("\" batchDiscardedSegments=\" + b.batchDiscardedSegments"),
                "VERIFY prints batchDiscardedSegments");
        assertTrue(diagnostics.contains("\" offerRefusals=\" + b.offerRefusals"),
                "VERIFY prints the aggregate offer-refusal total");
        assertEquals(1, occurrences(backend, "this.offerRefusals++;"),
                "refusals are counted once, at the submitImmediateBatch funnel");
    }


    @Test
    @DisplayName("DP-09/X-1: the zombie counters and the duplicate offTargetRefusals print are gone")
    void zombiesRemoved() throws IOException {
        String backend = read(BACKEND);
        String diagnostics = read(DIAGNOSTICS);
        String statistics = read(TARGET_STATISTICS);
        assertFalse(statistics.contains("int windowFlushDroppedBatches"),
                "the window-flush dropped counter is removed: its seats retain since task-60");
        assertFalse(statistics.contains("int unflushedTargetPassFailures"),
                "the unflushed-pass failure counter is removed: no such event exists");
        assertFalse(statistics.contains("int offTargetRefusals"),
                "the never-incremented off-target refusal counter is removed");
        assertFalse(backend.contains("String lastUnflushedTargetFailure"),
                "the never-assigned failure text is removed with its counter");
        assertFalse(diagnostics.contains("windowFlushDroppedBatches="),
                "VERIFY no longer prints the window-flush dropped key");
        assertFalse(diagnostics.contains("unflushedTargetPassFailures="),
                "VERIFY no longer prints the unflushed-pass failure key");
        assertFalse(diagnostics.contains("lastUnflushedTargetFailure"),
                "VERIFY no longer prints the unflushed failure text");
        assertEquals(1, occurrences(diagnostics, "offTargetRefusals="),
                "the offTargetRefusals key prints exactly once: the live by-reason map");
        assertTrue(diagnostics.contains("b.offTargetRefusalsByReason"),
                "the surviving print is the by-reason map");
        assertTrue(diagnostics.contains("\" windowFlushFailures=\" + b.targetStatistics.windowFlushFailures()"),
                "the live window-flush failure counter still prints");
        assertTrue(diagnostics.contains("\" unflushedTargetGroupsDropped=\" + b.targetStatistics.unflushedTargetGroupsDropped()"),
                "the live unflushed drop counter still prints");
    }

    @Test
    @DisplayName("DP-10: droppedByFormat is wired at the post-accept drop seat and the aggregate prints")
    void postAcceptDropsWiredAndAggregated() throws IOException {
        String backend = read(BACKEND);
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(backend.contains("final int[] postAcceptDroppedByFormat = new int[VulkanImmediateFormats.COUNT];"),
                "the per-format row is sized from COUNT, as its javadoc always claimed");
        assertEquals(1, occurrences(backend, "this.postAcceptDroppedByFormat[group.formatId] += group.batches;"),
                "the row is incremented once, in the replacement-drop loop beside the scalar");
        int seat = backend.indexOf("this.postAcceptDroppedByFormat[group.formatId] += group.batches;");
        int scalar = backend.indexOf("this.immediateStatistics.addPostAcceptDroppedGroups(dropped);");
        assertTrue(seat >= 0 && scalar > seat,
                "the per-format increment rides the same loop as the scalar count");
        assertTrue(diagnostics.contains("\" totalPostAcceptDrops=\""),
                "VERIFY prints the post-accept loss aggregate");
    }

    @Test
    @DisplayName("DP-33: the off-target redirect is counted at its seat and printed")
    void offTargetRedirectCounted() throws IOException {
        String backend = read(BACKEND);
        String diagnostics = read(DIAGNOSTICS);
        assertEquals(1, occurrences(backend, "this.offTargetRedirects++;"),
                "the redirect counter has exactly one increment site");
        int failure = backend.indexOf("this.lastOffTargetFailure = describe(error);");
        int increment = backend.indexOf("this.offTargetRedirects++;");
        assertTrue(failure >= 0 && increment > failure && increment - failure < 200,
                "the increment sits in the routing-failure catch, beside the failure text");
        assertTrue(diagnostics.contains("\" offTargetRedirects=\" + b.offTargetRedirects"),
                "VERIFY prints offTargetRedirects");
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
