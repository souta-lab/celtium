package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The non-quad emitter census (R-23 strip/fan occurrence census, completion-legs addendum 2
 * item D): the drain's one non-quad branch counts every group it sends to the emitter by GL
 * mode, the emitter counts the groups it drew itself, and the verification summary prints the
 * pair. The drawpath audit (DP-12) added the delegation and drop columns, so the summary now
 * prints all four and the per-mode identity closes structurally. Pinned in the house
 * source-contract style; whether a scene actually produces strip or fan groups is the census
 * leg's evidence, not this gate's.
 */
class NonQuadModeCensusWiringTest {

    private static final String ROOT = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";
    private static final String BACKEND = ROOT + "Lane2ImmediateRecorder.java";
    private static final String DIAGNOSTICS = ROOT + "BackendDiagnostics.java";

    private static final String OFFERED_INCREMENT = "this.nonQuadOfferedByMode[group.drawMode]++;";
    private static final String EMITTED_INCREMENT = "this.nonQuadEmittedByMode[group.drawMode]++;";

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

    @Test
    @DisplayName("the census counters are always-on mode arrays on the backend")
    void counterFieldsExist() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("final long[] nonQuadOfferedByMode = new long[8];"),
                "the offered counter is a long[8] indexed by GL mode");
        assertTrue(backend.contains("final long[] nonQuadEmittedByMode = new long[8];"),
                "the emitted counter is a long[8] indexed by GL mode");
        assertTrue(backend.contains("final long[] nonQuadDelegatedByMode = new long[8];"),
                "the delegated counter is a long[8] indexed by GL mode");
        assertTrue(backend.contains("final long[] nonQuadDroppedByMode = new long[8];"),
                "the dropped counter is a long[8] indexed by GL mode");
    }

    @Test
    @DisplayName("offered counts at the drain's non-quad branch, before the emitter call")
    void offeredCountsAtTheDrainBranch() throws IOException {
        String backend = read(BACKEND);
        assertEquals(1, occurrences(backend, OFFERED_INCREMENT),
                "the offered increment has exactly one site");
        int drain = backend.indexOf("void drawPendingImmediate(Long onlyTarget)");
        assertTrue(drain >= 0, "the drain exists");
        String body = backend.substring(drain, Math.min(backend.length(), drain + 120000));
        int branch = body.indexOf("group.drawMode != org.lwjgl.opengl.GL11.GL_QUADS");
        int increment = body.indexOf(OFFERED_INCREMENT);
        int call = body.indexOf("celtium$emitNonQuadGroup(group, mesh, prepared, push);");
        assertTrue(branch >= 0 && increment > branch && call > increment,
                "the count sits inside the non-quad branch, ahead of the one emitter call");
    }

    @Test
    @DisplayName("emitted counts only on the emitter's own draw path")
    void emittedCountsOnlyOnTheEmittersOwnDraw() throws IOException {
        String backend = read(BACKEND);
        assertEquals(1, occurrences(backend, EMITTED_INCREMENT),
                "the emitted increment has exactly one site");
        int start = backend.indexOf("boolean celtium$emitNonQuadGroup(");
        assertTrue(start >= 0, "the emitter exists");
        int end = backend.indexOf("return true;\n    }", start);
        String body = backend.substring(start, end);
        int refused = body.indexOf("this.batchDroppedDraws++;");
        int draw = body.indexOf("vkCmdDrawIndexed");
        int increment = body.indexOf(EMITTED_INCREMENT);
        assertTrue(refused >= 0 && increment > refused,
                "a record refusal returns before the count: refused groups are not emitted");
        assertTrue(draw >= 0 && increment > draw,
                "the count follows the emitter's own draw issuance");
        assertTrue(body.indexOf("return celtium$delegateNonQuadToDirect(group, mesh, prepared, push);") >= 0
                        && body.indexOf("return celtium$delegateNonQuadToDirect(group, mesh, prepared, push);") < increment,
                "the direct-draw delegations return before the count: they are offered, not emitted");
    }

    @Test
    @DisplayName("delegated and dropped count at the delegation helper and the emitter's refusal")
    void delegatedAndDroppedCountAtTheirSeats() throws IOException {
        String backend = read(BACKEND);
        String delegated = "this.nonQuadDelegatedByMode[group.drawMode]++;";
        String dropped = "this.nonQuadDroppedByMode[group.drawMode]++;";
        assertEquals(1, occurrences(backend, delegated),
                "the delegated increment has exactly one site");
        assertEquals(2, occurrences(backend, dropped),
                "the dropped increment has two sites: the delegation helper and the emitter's own refusal");
        int helper = backend.indexOf("boolean celtium$delegateNonQuadToDirect(");
        assertTrue(helper >= 0, "the delegation helper exists");
        String helperBody = backend.substring(helper, helper + 1600);
        assertTrue(helperBody.indexOf("celtium$drawGroupDirect(group, mesh, prepared, push)") >= 0
                        && helperBody.indexOf(delegated) > helperBody.indexOf("celtium$drawGroupDirect(group, mesh, prepared, push)")
                        && helperBody.indexOf(dropped) > helperBody.indexOf("celtium$drawGroupDirect(group, mesh, prepared, push)"),
                "the helper counts delegated or dropped from the direct draw's outcome");
        int emitter = backend.indexOf("boolean celtium$emitNonQuadGroup(");
        String emitterBody = backend.substring(emitter, backend.indexOf("return true;\n    }", emitter));
        assertTrue(emitterBody.indexOf(dropped) > emitterBody.indexOf("int recordIndex = celtium$recordForDirectDraw(push);"),
                "the emitter's own record refusal lands in the dropped column");
    }

    @Test
    @DisplayName("the verification summary prints all four columns as celtium/nonQuadByMode")
    void summaryPrintsAllFourColumns() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("celtium/nonQuadByMode["),
                "the summary carries the census field");
        assertTrue(diagnostics.contains("funnel.nonQuadOfferedByMode"),
                "the summary reads the offered counter");
        assertTrue(diagnostics.contains("funnel.nonQuadEmittedByMode"),
                "the summary reads the emitted counter");
        assertTrue(diagnostics.contains("funnel.nonQuadDelegatedByMode"),
                "the summary reads the delegated counter");
        assertTrue(diagnostics.contains("funnel.nonQuadDroppedByMode"),
                "the summary reads the dropped counter");
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
