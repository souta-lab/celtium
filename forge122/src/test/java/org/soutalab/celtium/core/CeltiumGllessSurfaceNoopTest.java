package org.soutalab.celtium.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DP-08: the transformer's no-op set and the hint's instanced drops are counted by name, by default.
 * <p>
 * The no-op set answered with a plain return and no counter; the instanced entry points dropped any count
 * other than one in silence. Both losses were invisible in exactly the way this audit keeps finding - a
 * reader could not distinguish "never called" from "called and swallowed". These tests pin the counting
 * doors (the hint's per-id and per-name counters) and, as source contracts, the wiring that feeds them:
 * the rewrite branch that emits the count before its return, and the surface report's two lines.
 */
class CeltiumGllessSurfaceNoopTest {

    @Test
    void noteSurfaceNoopCountsPerId() {
        int before = CeltiumGllessHint.surfaceNoopCounts()[7];
        CeltiumGllessHint.noteSurfaceNoop(7);
        CeltiumGllessHint.noteSurfaceNoop(7);
        assertEquals(before + 2, CeltiumGllessHint.surfaceNoopCounts()[7],
                "each answered call moves its id's count");
    }

    @Test
    void noteSurfaceNoopIgnoresOutOfRangeIds() {
        int[] before = CeltiumGllessHint.surfaceNoopCounts().clone();
        CeltiumGllessHint.noteSurfaceNoop(-1);
        CeltiumGllessHint.noteSurfaceNoop(4096);
        for (int i = 0; i < before.length; i++) {
            assertEquals(before[i], CeltiumGllessHint.surfaceNoopCounts()[i],
                    "an untracked id must not land in another name's count");
        }
    }

    @Test
    void anInstancedDrawWithAnotherCountIsCountedByName() {
        CeltiumGllessHint.glDrawArraysInstanced(4, 0, 3, 5);
        String summary = CeltiumGllessHint.unsupportedInstancedSummary();
        assertTrue(summary.contains("glDrawArraysInstanced/count="),
                "the dropped instanced draw is named with its count, got: " + summary);
    }

    @Test
    void anEmptyInstancedDrawIsNotCounted() {
        String before = CeltiumGllessHint.unsupportedInstancedSummary();
        CeltiumGllessHint.glDrawArraysInstanced(4, 0, 3, 0);
        CeltiumGllessHint.glDrawElementsInstanced(4, 3, 0x1403, 0L, 0);
        assertEquals(before, CeltiumGllessHint.unsupportedInstancedSummary(),
                "instanceCount 0 is GL's empty draw: nothing was asked for, so nothing is a loss");
    }

    /**
     * A non-zero base instance on a forwarded (instanceCount 1) draw is counted as a narrowing under its
     * own kind. Pinned on the hint's source rather than by calling the entry point: the forward lands in
     * the stub's decoder, whose classes this module's test classpath cannot load - the same reason the
     * transformer contract below is a source contract.
     */
    @Test
    void aBaseInstanceOnAForwardedDrawIsCountedAsANarrowing() throws IOException {
        String hint = Files.readString(findRepoFile(
                "forge122/src/main/java/org/soutalab/celtium/core/CeltiumGllessHint.java"));
        assertTrue(hint.contains("noteUnsupportedInstanced(\"glDrawArraysInstancedBaseInstance\", \"baseInstance\")"),
                "the arrays form counts an ignored base instance as a narrowing");
        assertTrue(hint.contains(
                        "noteUnsupportedInstanced(\"glDrawElementsInstancedBaseVertexBaseInstance\", \"baseInstance\")"),
                "the elements form counts an ignored base instance as a narrowing");
        CeltiumGllessHint.noteUnsupportedInstanced("glDrawArraysInstancedBaseInstance", "baseInstance");
        assertTrue(CeltiumGllessHint.unsupportedInstancedSummary()
                        .contains("glDrawArraysInstancedBaseInstance/baseInstance="),
                "and the kind lands in the aggregate the surface report prints");
    }

    /** The rewrite branch counts before it returns, and the surface report carries both new lines. */
    @Test
    void theTransformerCountsTheNoopSetAndReportsIt() throws IOException {
        String source = Files.readString(findRepoFile(
                "forge122/src/main/java/org/soutalab/celtium/core/CeltiumGllessNativeTransformer.java"));
        int branch = source.indexOf("if (VOID_NOOPS.contains(name) && descriptor.endsWith(\")V\"))");
        assertTrue(branch >= 0, "the no-op branch exists");
        int counted = source.indexOf("\"noteSurfaceNoop\"", branch);
        int returned = source.indexOf("method.visitInsn(Opcodes.RETURN);", branch);
        assertTrue(counted > branch && returned > counted,
                "the count is emitted before the branch's return, so every answered call is counted");
        assertTrue(source.contains("CELTIUM_GL_SURFACE_NOOP"),
                "the surface report names the no-op set's calls on their own line");
        assertTrue(source.contains("CELTIUM_UNSUPPORTED_INSTANCED"),
                "the surface report aggregates the hint's instanced drops");
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
        Path fromForgeModule = Paths.get(repoRelative.substring("forge122/".length()));
        if (Files.exists(fromForgeModule)) {
            return fromForgeModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
