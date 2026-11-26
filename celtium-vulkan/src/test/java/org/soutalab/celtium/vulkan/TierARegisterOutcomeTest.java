package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-55, Step A: the Tier-A registration outcome is three-valued — accepted,
 * deferred, rejected — and the renderer's upload branch stands on it. The
 * executor needs a Vulkan device, so the classification sites are pinned as
 * source contracts (the repo's idiom for device-bound decisions); the pure
 * branch predicate over the outcome is covered behaviourally by
 * {@code TierAUploadBranchTest}.
 */
class TierARegisterOutcomeTest {

    private static String engineSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java"));
    }

    private static String rendererSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java"));
    }

    /** register() and registerClassified() are two views of one attempt, so the classification cannot drift from the work. */
    @Test
    void registerAndRegisterClassifiedShareOneAttemptCore() throws IOException {
        String source = engineSource();
        assertTrue(source.contains("return attemptRegister(built, generation).registration();"),
                "register() delegates to the shared attempt core");
        assertTrue(source.contains("return attemptRegister(built, generation).outcome();"),
                "registerClassified() delegates to the same attempt core");
    }

    /** Each outcome is produced at its own site: disabled rejects, a budget refusal defers after queueing, the upload result decides the rest. */
    @Test
    void theThreeOutcomesAreProducedAtTheirSites() throws IOException {
        String source = engineSource();
        assertTrue(source.contains("new RegisterAttempt(null, RegisterOutcome.REJECTED)"),
                "a disabled executor (and a failed attempt) classifies rejected");
        int queued = source.indexOf("this.deferred.addLast(built);");
        int deferred = source.indexOf("RegisterOutcome.DEFERRED");
        assertTrue(queued >= 0 && deferred > queued,
                "the deferred outcome is returned only after the section is queued");
        assertTrue(source.contains(
                        "registration != null ? RegisterOutcome.ACCEPTED : RegisterOutcome.REJECTED"),
                "a completed upload classifies accepted, a refused one rejected");
    }

    /** The renderer-facing registration returns the classification; an empty section is a vacuous accept and a build failure rejects. */
    @Test
    void registerTierAReturnsTheClassification() throws IOException {
        String source = rendererSource();
        assertTrue(source.contains("private TerrainEngineA.RegisterOutcome registerTierA("),
                "registerTierA returns the classified outcome");
        assertTrue(source.contains("return TerrainEngineA.RegisterOutcome.ACCEPTED;"),
                "an empty section (no built form) classifies accepted: nothing to draw on either route");
        assertTrue(source.contains("return TerrainEngineA.RegisterOutcome.REJECTED;"),
                "a build exception classifies rejected, keeping the legacy upload");
        assertTrue(source.contains(".registerClassified(built, this.terrainAHost.nextGeneration())"),
                "a built section is classified by the executor");
    }

    /** Stride-16 build meshes become offers decided after the loop; sort outputs never enter the branch (design section 3.5). */
    @Test
    void theUploadBranchOffersBuildMeshesAndLeavesSortOutputsAlone() throws IOException {
        String source = rendererSource();
        assertTrue(source.contains("tierAOffers.add(new TierAOffer("),
                "a stride-16 build mesh is recorded as an offer instead of an unconditional legacy upload");
        assertTrue(source.contains("offer.outcome() == TerrainEngineA.RegisterOutcome.REJECTED"),
                "only a rejected offer falls back to the legacy upload");
        int sortBlock = source.indexOf("output instanceof ChunkSortOutput");
        int sortBlockEnd = source.indexOf("Unexpected result type", sortBlock);
        assertTrue(sortBlock >= 0 && sortBlockEnd > sortBlock, "the sort-output block is located");
        String sortSection = source.substring(sortBlock, sortBlockEnd);
        assertFalse(sortSection.contains("TierAOffer") || sortSection.contains("registerTierA"),
                "sort outputs keep their existing handling: no Tier-A offer, no branch");
    }

    /** An upload whose offers were all skipped still runs the tail (empty-storage removal, graph update), so the early return is guarded. */
    @Test
    void anAllSkippedUploadStillRunsTheTail() throws IOException {
        String source = rendererSource();
        assertTrue(source.contains("if (pending.isEmpty() && !tierAOffersProcessed)"),
                "the empty-pending early return does not fire when offers were processed");
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
