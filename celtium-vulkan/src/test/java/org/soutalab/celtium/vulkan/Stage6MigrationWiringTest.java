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
 * Stage 5 stage 6 (the immediate families' full migration to their record-reading forms),
 * pinned at the layers a CPU gate can reach, in the house source-contract style: the
 * per-group classes and the indirect run are deleted, the prepare resolves the twins
 * directly through the records host, the capture population is the whole twin population
 * under the widened translucent rule with its mid-flush retry, and the drain's fallback
 * chain is the particle fallback, the non-quad emitter and the generalized direct twin
 * draw. The device-bound halves (a twin actually drawing) are the leg windows' evidence;
 * the pure decisions (the emitter's issue forms, the order classification, the record
 * ABI) have their own behavioural tests beside this one.
 */
class Stage6MigrationWiringTest {

    private static final String ROOT = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";
    private static final String BACKEND = ROOT + "VulkanTerrainGpuBackend.java";
    private static final String HOST = ROOT + "ImmediateRecordsPipelines.java";
    private static final String MODEL_REPLAY = ROOT + "ModelReplay.java";

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
    @DisplayName("the per-group classes, the indirect run and the legacy record store are deleted")
    void deletedClassesAreGone() {
        String[] deleted = {
                "VulkanImmediateTexturePipeline.java", "VulkanImmediateItemPipeline.java",
                "VulkanImmediateBlockPipeline.java", "VulkanImmediatePipeline.java",
                "IndirectImmediateRuns.java", "ImmediateDrawRecords.java"};
        for (String name : deleted) {
            assertFalse(Files.exists(findRepoRoot().resolve(ROOT + name)),
                    name + " is deleted: the record-reading forms are the only forms");
        }
    }

    @Test
    @DisplayName("the backend names none of the deleted classes")
    void backendNamesNoDeletedClass() throws IOException {
        String backend = read(BACKEND);
        String[] deleted = {
                "VulkanImmediateTexturePipeline", "VulkanImmediateItemPipeline",
                "VulkanImmediateBlockPipeline", "VulkanImmediatePipeline",
                "IndirectImmediateRuns", "ImmediateDrawRecords"};
        for (String name : deleted) {
            assertFalse(backend.contains(name), "the backend names " + name + " nowhere");
        }
    }

    @Test
    @DisplayName("prepare resolves the twins directly, one branch per family")
    void prepareResolvesTwinsDirectly() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("(VulkanImmediateTextureRecordsPipeline)\n"
                        + "                    celtium$resolveImmediatePipeline(format, key, state, pass, targetColors);"),
                "the textured branches resolve the texture twin");
        assertTrue(backend.contains("(VulkanImmediateItemRecordsPipeline)\n"
                        + "                    celtium$resolveImmediatePipeline(format, key, state, pass, targetColors);"),
                "the item branch resolves the item twin, lit and unlit in one class");
        assertTrue(backend.contains("(VulkanImmediateBlockRecordsPipeline)\n"
                        + "                    celtium$resolveImmediatePipeline(format, key, state, pass, targetColors);"),
                "the block branch resolves the block twin, the family's first records form");
    }

    @Test
    @DisplayName("creation and release run through the records host")
    void creationAndReleaseRunThroughTheHost() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains(
                        "this.recordsPipelines.celtium$createPipeline(format, key, state, pass, targetColors);"),
                "the single creation path delegates to the host's dispatch");
        assertTrue(backend.contains("this.recordsPipelines.celtium$cachedPipeline(format, branchKey);"),
                "the branch cache read delegates to the host");
        assertTrue(backend.contains("released += this.recordsPipelines.celtium$releaseAll();"),
                "the world-leave release walks the host's caches");
        assertTrue(backend.contains("this.recordsPipelines.celtium$releaseAll();"),
                "the settled teardown releases through the host as well");
    }

    @Test
    @DisplayName("the host dispatches every format to its family's twin factory")
    void hostDispatchesEveryFamily() throws IOException {
        String host = read(HOST);
        assertTrue(host.contains("VulkanImmediateItemRecordsPipeline.create(this.b.context, pass,"),
                "item twins are built by the host");
        assertTrue(host.contains("VulkanImmediateBlockRecordsPipeline.create(this.b.context, pass,"),
                "block twins are built by the host");
        assertTrue(host.contains("VulkanImmediateColourRecordsPipeline.create(this.b.context, pass,"),
                "colour twins are built by the host");
        assertTrue(host.contains("VulkanImmediateTextureRecordsPipeline.createForTexGen(this.b.context, pass,"),
                "texgen twins are built by the host");
        assertTrue(host.contains("VulkanImmediateTextureRecordsPipeline.createForCombine4("),
                "combine4 twins are built by the host");
        assertTrue(host.contains("celtium$recordsParticlePipeline(int format, long key, long pass,"),
                "the particle accessor the stage-5 landing handed off lives on the host");
    }

    @Test
    @DisplayName("texgen and combine4 twins live in dedicated caches, so their pipeline keys cannot fold into the base variant's")
    void texgenAndCombine4HaveDedicatedCaches() throws IOException {
        String host = read(HOST);
        assertTrue(host.contains("texGenPipelines"), "the texgen variant has its own cache");
        assertTrue(host.contains("combine4Pipelines"), "the combine4 variant has its own cache");
        assertTrue(host.contains("case VulkanImmediateFormats.POSITION_COLOR_TEXGEN:"),
                "the cache switch names the texgen format");
        assertTrue(host.contains("case VulkanImmediateFormats.COMBINE4:"),
                "the cache switch names the combine4 format");
    }

    @Test
    @DisplayName("the capture population is the twin population with the shared-quad shape, not the stage-4 populations")
    void capturePopulationIsTheTwinPopulation() throws IOException {
        String backend = read(BACKEND);
        int eligible = backend.indexOf("private boolean celtium$batchEligible(");
        assertTrue(eligible >= 0, "the eligibility decision exists");
        int end = backend.indexOf("static boolean celtium$batchOrderFree", eligible);
        String body = backend.substring(eligible, end);
        assertTrue(body.contains("pipeline instanceof VulkanImmediateTextureRecordsPipeline"),
                "the texture twin is in the population");
        assertTrue(body.contains("pipeline instanceof VulkanImmediateColourRecordsPipeline"),
                "the colour twin is in the population");
        assertTrue(body.contains("mesh.layout().stride == VulkanImmediateItemRecordsPipeline.VERTEX_BYTES"),
                "the item family is pinned to its fixed stride");
        assertTrue(body.contains("mesh.layout().stride == VulkanImmediateBlockRecordsPipeline.VERTEX_BYTES"),
                "the block family is pinned to its fixed stride");
        assertFalse(body.contains("closedInUiBracket"),
                "the stage-4 UI-bracket population condition is gone from eligibility");
    }

    @Test
    @DisplayName("the translucent rule widens the order classification with the UI/font guard kept")
    void translucentRuleShape() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("boolean ordered = particle ? !celtium$batchOrderFree(group)"),
                "a particle group is ordered unless its order is free");
        assertTrue(backend.contains(
                        ": (group.closedInUiBracket || group.fromFontRun) || !celtium$batchOrderFree(group);"),
                "the UI bracket and font runs stay ordered unconditionally; the rest follow the classification");
    }

    @Test
    @DisplayName("a capture that fills the segment retries once after a mid-flush, with the same record")
    void captureRetriesAfterAMidFlush() throws IOException {
        String backend = read(BACKEND);
        assertEquals(2, occurrences(backend,
                        "this.batchBatcher.captureQuads(0, pipelineKey, stateKey, 0, textureSlot,"),
                "the capture call appears twice in the wiring: the attempt and the mid-flush retry");
        int capture = backend.indexOf("private boolean celtium$tryCaptureBatchGroup(");
        String body = backend.substring(capture, capture + 9000);
        int attempt = body.indexOf("boolean captured = this.batchBatcher.captureQuads(");
        int flush = body.indexOf("celtium$flushBatchSegment();", attempt);
        int retry = body.indexOf("captured = this.batchBatcher.captureQuads(", flush);
        assertTrue(attempt >= 0 && flush > attempt && retry > flush,
                "the retry follows the segment flush inside the capture");
        assertTrue(body.contains("recordIndex, ordered, vertices"),
                "both attempts hand the batcher the same record index");
    }

    @Test
    @DisplayName("the drain fallback chain is particle fallback, non-quad emitter, direct twin draw")
    void drainFallbackChain() throws IOException {
        String backend = read(BACKEND);
        int drain = backend.indexOf("private void drawPendingImmediate(Long onlyTarget)");
        assertTrue(drain >= 0, "the drain exists");
        String body = backend.substring(drain, drain + 120000);
        int particle = body.indexOf("celtium$drawParticleGroupFallback(group, mesh, prepared, push);");
        int emitter = body.indexOf("celtium$emitNonQuadGroup(group, mesh, prepared, push);");
        int direct = body.indexOf("celtium$drawGroupDirect(group, mesh, prepared, push);");
        assertTrue(particle >= 0 && emitter > particle && direct > emitter,
                "an uncaptured group reaches exactly one of the three fallbacks, in this order");
        assertFalse(backend.contains("celtium$tryEnqueueIndirect"),
                "the indirect try is deleted from the drain");
        assertFalse(backend.contains("celtium$flushIndirectRun"),
                "no indirect flush remains in the backend");
        assertFalse(read(MODEL_REPLAY).contains("celtium$flushIndirectRun"),
                "the model replay's flush call is severed with the run");
    }

    @Test
    @DisplayName("a group carries exactly one draw mode, stamped at close and split on at accept")
    void groupDrawModeStampAndSplit() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("group.drawMode = this.currentGroupDrawMode;"),
                "the close stamps the open group's mode onto the group");
        assertTrue(backend.contains("|| this.currentGroupDrawMode != drawMode"),
                "the accept split predicate compares the mode, so a group never mixes modes");
        assertTrue(read(ROOT + "ImmediateGroup.java").contains(
                        "public int drawMode = org.lwjgl.opengl.GL11.GL_QUADS;"),
                "a group built off the accept path keeps the quad default");
    }

    @Test
    @DisplayName("the block twin's layout declares no push range: its fragment reads the alpha reference from a varying")
    void blockTwinDeclaresNoPushRange() throws IOException {
        String block = read(ROOT + "VulkanImmediateBlockRecordsPipeline.java");
        assertFalse(block.contains("PushConstantRange"),
                "no push range is declared for the block records layout");
        assertTrue(block.contains("IMMEDIATE_BLOCK_RECORDS_VERTEX"),
                "the block twin is built on the dedicated records shader pair");
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

    private static Path findRepoRoot() {
        Path direct = Paths.get("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java");
        if (Files.exists(direct)) {
            return Paths.get("");
        }
        return Paths.get("..");
    }
}
