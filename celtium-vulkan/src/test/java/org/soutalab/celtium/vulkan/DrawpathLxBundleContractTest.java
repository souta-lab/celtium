package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contracts for the drawpath audit's L-X bundle (DP-31, DP-32, DP-34, DP-35, DP-37): refusals and
 * swallowed failures on the post-chain / framebuffer / mod-program / native-pass seats gain counts
 * that the verify line actually carries, and the post chain's first-use commit waits for the
 * submission that carries its barriers.
 * <p>
 * The seats themselves live inside device-bound methods (a chain recording against a real command
 * buffer, a mod-program prepare against a real pipeline), so the wiring is pinned by source
 * contracts in the shape {@code FallbackWiringContractTest} established; the two pure seams - the
 * refusal summary's format and the stage gate's summary shape - are pinned behaviourally.
 */
class DrawpathLxBundleContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
    private static final String COORDINATOR =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java";
    private static final String ADAPTER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java";
    private static final String DIAGNOSTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java";
    private static final String COMPOSITOR =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeFramebufferCompositor.java";
    private static final String SHADER_GROUP_MIXIN =
            "forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java";
    private static final String FRAMEBUFFER_MIXIN =
            "forge122/src/main/java/org/taumc/celeritas/mixin/core/FramebufferMixin.java";

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

    // DP-32: the deferred first-use commit seat retired with the old chain (ruling step 5).
    // The vanilla receiver records into the frame's own command buffer, so there is no
    // separate commit to settle; what remains of DP-32 is the flush-failure census below.

    @Test
    void theDeferredCommitSeatIsRetiredWithTheOldChain() throws IOException {
        String backend = read(BACKEND) + read(COORDINATOR) + read(ADAPTER);
        assertEquals(0, count(backend, "celtium$settleDeferredCommit"),
                "no finish path settles a deferred chain commit any more");
        assertEquals(0, count(backend, "this.postChainBridge"),
                "the bridge seat itself is gone from the backend");
    }

    @Test
    void theChainInputFlushFailureIsCountedNotSwallowed() throws IOException {
        String mixin = read(SHADER_GROUP_MIXIN);
        assertTrue(mixin.contains("backend.celtium$noteChainInputFlushFailure(error)"),
                "the mixin's catch hands the failure to the backend census");
        String backend = read(BACKEND) + read(COORDINATOR) + read(ADAPTER);
        assertTrue(backend.contains("public void celtium$noteChainInputFlushFailure(Throwable error)"),
                "the backend counts and bounds-prints the failure");
        assertTrue(read(DIAGNOSTICS).contains("\" chainInputFlushFailures=\" + frame.chainInputFlushFailures()"),
                "the verify line carries the count");
    }

    // DP-31: the vanilla receiver and the surviving stage gate reach the verify line.

    @Test
    void theVerifyLineCarriesTheVanillaReceiverCensusAndTheStageGateSummary() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("frame.postChainCreationFailures()"));
        assertTrue(diagnostics.contains("frame.postChainUnsupportedRefusals()"));
        assertTrue(diagnostics.contains("StageGate.system().summary()"));
    }

    @Test
    void theStageGateSummaryKeepsItsBracketShape() {
        String summary = StageGate.system().summary();
        assertTrue(summary.startsWith("stageGate[") && summary.contains("terrainPipelines="),
                "legs parse the gate summary by its bracket shape: " + summary);
    }

    // DP-34: the compositor's refusals and the framebuffer hints are counted.

    @Test
    void bothCompositorRefusalSeatsCount() throws IOException {
        String compositor = read(COMPOSITOR);
        assertEquals(2, count(compositor, "this.compositeRefusals++"),
                "the no-source throw and the no-material throw both count");
        assertTrue(compositor.contains("\" nativeCompositeRefusals=\" + this.compositeRefusals"),
                "describe() carries the count into the verify line");
    }

    @Test
    void everyFramebufferHintSeatReportsItsFailure() throws IOException {
        String mixin = read(FRAMEBUFFER_MIXIN);
        assertEquals(5, count(mixin, "celtium$noteHintFailure("),
                "one helper definition and the four hint/probe catches");
        assertTrue(mixin.contains("celtium$noteHintFailure(\"bind size\", error)"));
        assertTrue(mixin.contains("celtium$noteHintFailure(\"adopt after bind\", error)"));
        String backend = read(BACKEND) + read(COORDINATOR) + read(ADAPTER);
        assertTrue(backend.contains("public void celtium$noteFramebufferHintFailure(String seat, Throwable error)"),
                "the backend counts the failure with its seat's name");
        assertTrue(read(DIAGNOSTICS).contains("\" framebufferHintFailures=\" + funnel.framebufferHintFailures"),
                "the verify line carries the count");
    }

    // DP-35: mod-program refusals aggregate per fixed reason category. The census seat moved
    // to Lane 3's DirectGlRefusalCensus with the ModPrograms retirement (ruling section 2.2);
    // the verify line's key shape is unchanged.

    @Test
    void theVerifyLineCarriesTheLane3RefusalAggregate() throws IOException {
        assertTrue(read(DIAGNOSTICS).contains("DirectGlRefusalCensus.refusalSummary()"),
                "the verify line carries the aggregate");
    }

    @Test
    void theRefusalSummaryNamesEveryCategory() {
        String summary = org.soutalab.celtium.glcompat.backend.DirectGlRefusalCensus.refusalSummary();
        assertTrue(summary.startsWith("modRefusals=") && summary.contains(" modRefusalsByReason["),
                "the total leads, in the verify line's key shape: " + summary);
        for (org.soutalab.celtium.glcompat.backend.DirectGlRefusalCensus.Reason category
                : org.soutalab.celtium.glcompat.backend.DirectGlRefusalCensus.Reason.values()) {
            assertTrue(summary.contains(category.key() + "="),
                    "every category is named, zero or not: " + category.key());
            assertTrue(category.key().matches("[a-z_]+"),
                    "category keys stay snake_case for the loss gate's parser: " + category.key());
        }
    }

    // DP-37: native-pass refusals are in the verify body, not only the census line.

    @Test
    void theVerifyLineCarriesTheNativePassRefusals() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("\" nativePassExecRefusals=\" + funnel.nativePassRegistry.executionRefusalCount()"),
                "execution refusals are in the verify body");
        assertTrue(diagnostics.contains("\" nativePassCompileRefusals=\" + funnel.nativePassRegistry.compileRefusalCount()"),
                "compile refusals are in the verify body");
    }
}
