package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shadow E10 fix (design memo SHADOW-DESIGN-BY-NEON-2026-11-27, decision 1): the shadow
 * executor's stage head, collection and issuance are aligned to the solid pass alone -
 * the camera executor's own target predicate - so the collected population and the
 * pre-enumerated population are the same pass' by construction. Before the fix the
 * collect hook took every unsorted subpass (solid and cutout_mipped concatenated into
 * one frame collection) while the enumeration covered the single pass that fired the
 * stage head, so the exact-reproduction test at the generation head could never hold
 * once a cutout subpass had geometry, and the executor fell back on every frame. The
 * same patch closes two latent defects on the armed path: the per-region bind handed
 * the sentinel descriptor handle (1L) to vkCmdBindDescriptorSets whenever the arm was
 * live, and the generation head had no degenerate-table (zero-command) refusal. Most
 * of the wiring is device-bound, so it is pinned as source contracts in the established
 * shape; the reproduction test itself is a pure function and is driven directly.
 */
class ShadowGpuTerrainDrawContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String EXECUTOR =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java";

    /** The stage head, the collect hook and the enumeration share the one target predicate. */
    @Test
    void theStageHeadAndTheCollectHookShareTheOneTargetPredicate() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains(
                        "boolean shadowDrawTargetPass = shadowStage && GpuTerrainCulling.SHADOW_DRAW"),
                "the pack execution derives its one shadow target predicate from the stage");
        assertTrue(backend.contains("&& celtium$gpuTerrainDrawTarget(pass);"),
                "the predicate is the camera executor's own target test (the solid pass)");
        assertTrue(backend.contains("if (shadowDrawTargetPass && this.shadowGpuTerrainDraw != null) {"),
                "the collect hook gates on the predicate, not on the unsorted flag");
        assertFalse(backend.contains("&& this.shadowGpuTerrainDraw != null && !sortedPass"),
                "the unfiltered unsorted-only collect condition is gone: a second subpass'"
                        + " population can no longer be concatenated into the collection");
        int head = backend.indexOf("private void celtium$shadowGpuTerrainDrawStageHead(");
        assertTrue(head >= 0, "the shadow stage head is declared");
        String headBody = backend.substring(head, head + 1800);
        int guard = headBody.indexOf("if (!celtium$gpuTerrainDrawTarget(pass)) {");
        int stamp = headBody.indexOf("this.shadowGpuTerrainDrawFrame = this.frameId;");
        assertTrue(guard >= 0 && stamp > guard,
                "the stage head refuses a non-target pass before it can consume the frame stamp");
    }

    /** The head runs after the variant's arming is decided, before the render pass opens, and notes the hold. */
    @Test
    void theStageHeadRunsAfterTheVariantDecisionAndNotesTheHold() throws IOException {
        String backend = read(BACKEND);
        int armedBlock = backend.indexOf("boolean packVariantArmed = false;");
        assertTrue(armedBlock >= 0, "the variant arming block is present");
        int headCall = backend.indexOf(
                "celtium$shadowGpuTerrainDrawStageHead(matrices, renderLists, pass,", armedBlock);
        int beginPass = backend.indexOf(
                "vkCmdBeginRenderPass(this.commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);",
                armedBlock);
        assertTrue(headCall > armedBlock && beginPass > headCall,
                "the generation head is called after the arming decision and before the"
                        + " stage's render pass opens");
        assertTrue(backend.substring(headCall, headCall + 300).contains("packVariantArmed);"),
                "the arming outcome is handed to the stage head");
        int head = backend.indexOf("private void celtium$shadowGpuTerrainDrawStageHead(");
        String headBody = backend.substring(head, head + 4500);
        int held = headBody.indexOf("this.shadowGpuTerrainDraw.noteHeldByVariant();");
        int enumerate = headBody.indexOf("celtium$gpuTerrainVisitList(");
        assertTrue(headBody.contains("if (packVariantArmed) {"),
                "the stage head branches on the variant's arming");
        assertTrue(held >= 0 && enumerate > held,
                "a held stage notes the hold and returns before any visit enumeration");
    }

    /** Under the arm the loop's per-region bind stands down: the sentinel handle never reaches the bind. */
    @Test
    void theArmedLoopNeverBindsTheSentinelDescriptorSet() throws IOException {
        String backend = read(BACKEND);
        int sentinel = backend.indexOf("descSet = 1L;");
        assertTrue(sentinel >= 0, "the armed span's sentinel assignment is present");
        int bind = backend.indexOf(
                "modPipeline.bindTerrainUniformsAndSet(this.commandBuffer, pushUniforms, descSet, setScratch);",
                sentinel);
        assertTrue(bind > sentinel, "the per-region bind follows the sentinel assignment");
        String between = backend.substring(sentinel, bind);
        int guard = between.indexOf("if (!shadowIssuanceArmed) {");
        int bracket = between.indexOf("FramePassTimings.begin(\"packRegionBind\")");
        assertTrue(guard >= 0 && bracket > guard,
                "the packRegionBind span is guarded on the arm being off, so the sentinel"
                        + " (handle 0x1) is never handed to vkCmdBindDescriptorSets");
    }

    /** The generation head counts its stand-down causes apart, including the degenerate table. */
    @Test
    void theGenerationHeadCountsItsFallbacksApart() throws IOException {
        String executor = read(EXECUTOR);
        int head = executor.indexOf("boolean generationHead(");
        int draw = executor.indexOf("boolean recordDraw(", head);
        assertTrue(head >= 0 && draw > head, "the generation head bounds its own region");
        String body = executor.substring(head, draw);
        assertTrue(body.contains("this.fallbackEmpty++;"),
                "a finalized table with no commands is refused, as at the camera head");
        assertTrue(body.contains("this.fallbackVisitsNull++;")
                        && body.contains("this.fallbackMismatch++;"),
                "no enumeration and a failed reproduction are counted apart");
        assertTrue(body.contains("this.lastMismatchVisits = visits.size();")
                        && body.contains("this.lastMismatchCollected = this.ready.regionIds.length;"),
                "a failed reproduction records the two sizes that disagreed");
        assertFalse(body.contains("visits == null || !reproduces(visits)"),
                "the combined population fallback is split, not kept");
        assertFalse(body.contains("this.fallbackPopulation++;"),
                "the shadow head no longer folds its causes into the camera head's counter");
        assertTrue(executor.contains("void noteHeldByVariant()")
                        && executor.contains("void noteCancelReason(CancelReason reason)")
                        && executor.contains("enum CancelReason"),
                "the held note and the named cancellation reasons exist");
        assertTrue(executor.contains("mode=shadow-generation+issuance heads=")
                        && executor.contains("heldByVariant=")
                        && executor.contains("cancels=[variant=")
                        && executor.contains("lastMismatch="),
                "the periodic line carries the split fallbacks, the cancels and the last mismatch");
    }

    /** A solid-only collection reproduces the solid enumeration, element for element. */
    @Test
    void aSolidOnlyCollectionReproducesTheSolidEnumeration() {
        VulkanMultiDrawBatch solidA = new VulkanMultiDrawBatch(4);
        VulkanMultiDrawBatch solidB = new VulkanMultiDrawBatch(4);
        int[] regionIds = {11, 12};
        VulkanMultiDrawBatch[] batches = {solidA, solidB};
        List<VulkanGpuTerrainDraw.Visit> visits = List.of(
                new VulkanGpuTerrainDraw.Visit(11, solidA),
                new VulkanGpuTerrainDraw.Visit(12, solidB));
        assertTrue(VulkanGpuTerrainDraw.reproducesPopulation(visits, regionIds, batches),
                "the solid enumeration reproduces the solid collection exactly");
    }

    /** A concatenated (solid + cutout) collection cannot reproduce the solid enumeration. */
    @Test
    void aConcatenatedCollectionDoesNotReproduceTheSolidEnumeration() {
        VulkanMultiDrawBatch solidA = new VulkanMultiDrawBatch(4);
        VulkanMultiDrawBatch cutoutA = new VulkanMultiDrawBatch(4);
        VulkanMultiDrawBatch solidB = new VulkanMultiDrawBatch(4);
        // The pre-fix collection's shape: the stage's subpasses concatenated into one
        // frame collection, against the single pass' enumeration.
        int[] collectedRegionIds = {11, 11, 12};
        VulkanMultiDrawBatch[] collectedBatches = {solidA, cutoutA, solidB};
        List<VulkanGpuTerrainDraw.Visit> solidVisits = List.of(
                new VulkanGpuTerrainDraw.Visit(11, solidA),
                new VulkanGpuTerrainDraw.Visit(12, solidB));
        assertFalse(VulkanGpuTerrainDraw.reproducesPopulation(
                        solidVisits, collectedRegionIds, collectedBatches),
                "a concatenated collection is longer than the solid enumeration and cannot reproduce it");
        // Same length, one batch rebuilt since the collection: identity, not shape, decides.
        VulkanMultiDrawBatch rebuiltA = new VulkanMultiDrawBatch(4);
        int[] regionIds = {11, 12};
        VulkanMultiDrawBatch[] batches = {solidA, solidB};
        List<VulkanGpuTerrainDraw.Visit> rebuiltVisits = List.of(
                new VulkanGpuTerrainDraw.Visit(11, rebuiltA),
                new VulkanGpuTerrainDraw.Visit(12, solidB));
        assertFalse(VulkanGpuTerrainDraw.reproducesPopulation(rebuiltVisits, regionIds, batches),
                "a rebuilt batch is a different object and breaks the reproduction");
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
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
