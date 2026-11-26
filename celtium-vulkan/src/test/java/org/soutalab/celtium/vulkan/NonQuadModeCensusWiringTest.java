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
 * pair. Pinned in the house source-contract style; whether a scene actually produces strip or
 * fan groups is the census leg's evidence, not this gate's.
 */
class NonQuadModeCensusWiringTest {

    private static final String ROOT = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";
    private static final String BACKEND = ROOT + "VulkanTerrainGpuBackend.java";
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
    }

    @Test
    @DisplayName("offered counts at the drain's non-quad branch, before the emitter call")
    void offeredCountsAtTheDrainBranch() throws IOException {
        String backend = read(BACKEND);
        assertEquals(1, occurrences(backend, OFFERED_INCREMENT),
                "the offered increment has exactly one site");
        int drain = backend.indexOf("private void drawPendingImmediate(Long onlyTarget)");
        assertTrue(drain >= 0, "the drain exists");
        String body = backend.substring(drain, drain + 120000);
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
        int start = backend.indexOf("private boolean celtium$emitNonQuadGroup(");
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
        assertTrue(body.indexOf("return celtium$drawGroupDirect(group, mesh, prepared, push);") >= 0
                        && body.indexOf("return celtium$drawGroupDirect(group, mesh, prepared, push);") < increment,
                "the direct-draw delegations return before the count: they are offered, not emitted");
    }

    @Test
    @DisplayName("the verification summary prints the pair as celtium/nonQuadByMode")
    void summaryPrintsThePair() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("celtium/nonQuadByMode["),
                "the summary carries the census field");
        assertTrue(diagnostics.contains("b.nonQuadOfferedByMode"),
                "the summary reads the offered counter");
        assertTrue(diagnostics.contains("b.nonQuadEmittedByMode"),
                "the summary reads the emitted counter");
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
