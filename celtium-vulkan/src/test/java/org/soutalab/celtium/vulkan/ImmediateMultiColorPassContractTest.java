package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multi-target immediate fix (the pack-scene 02684 disposition, plan A): a batch offered for a
 * target attaching several colours is prepared against that count's chain pass, the colour count
 * folds into the pipeline cache key beside the format class, and each immediate pipeline class
 * replicates its blend state once per colour attachment - the mod-program form, which the immediate
 * families were missing.
 * <p>
 * The device-bound half (a pipeline actually created against a multi-target pass) cannot run on a
 * CPU-only instance, so the decisions are pinned at their two testable layers, the shape
 * {@code ChainPairFormatClassTest} and {@code HostBufferGrowthRetentionTest} established: the pure
 * key fold exercised directly, and source contracts for the wiring - the pass selection, the count's
 * source, and the blend replication in each of the five classes.
 */
class ImmediateMultiColorPassContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String INDIRECT =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java";
    private static final String[] PIPELINE_CLASSES = {
            "VulkanImmediatePipeline", "VulkanImmediateTexturePipeline", "VulkanImmediateBlockPipeline",
            "VulkanImmediateItemPipeline", "VulkanImmediateParticlePipeline"};

    @Test
    @DisplayName("the colour fold is the identity at one colour, for plain and line keys alike")
    void colorFoldIsIdentityAtOneColor() {
        long key = 0x1234_5678_9ABL;
        long lineKey = key | VulkanTerrainGpuBackend.LINE_KEY_BIT;
        assertEquals(key, VulkanTerrainGpuBackend.celtium$pipelineColorKey(key, 1));
        assertEquals(key, VulkanTerrainGpuBackend.celtium$pipelineColorKey(key, 0),
                "an unrecorded count reads as one colour, never as a distinct key");
        assertEquals(lineKey, VulkanTerrainGpuBackend.celtium$pipelineColorKey(lineKey, 1));
    }

    @Test
    @DisplayName("the colour fold separates counts and keeps the line bit on its own side of the fold")
    void colorFoldSeparatesCountsAndPreservesTheLineBit() {
        long key = 0x1234_5678_9ABL;
        long two = VulkanTerrainGpuBackend.celtium$pipelineColorKey(key, 2);
        long three = VulkanTerrainGpuBackend.celtium$pipelineColorKey(key, 3);
        assertNotEquals(key, two, "a two-colour target must not share the one-colour key");
        assertNotEquals(two, three);
        assertEquals(two, VulkanTerrainGpuBackend.celtium$pipelineColorKey(key, 2), "the fold is stable");
        assertEquals(0L, two & VulkanTerrainGpuBackend.LINE_KEY_BIT,
                "the fold must not set the line flag on a triangle batch");
        long lineKey = key | VulkanTerrainGpuBackend.LINE_KEY_BIT;
        long foldedLine = VulkanTerrainGpuBackend.celtium$pipelineColorKey(lineKey, 2);
        assertEquals(VulkanTerrainGpuBackend.LINE_KEY_BIT, foldedLine & VulkanTerrainGpuBackend.LINE_KEY_BIT,
                "the fold must not clear the line flag on a line batch");
        assertNotEquals(two, foldedLine, "a line batch and a triangle batch stay apart at any count");
    }

    @Test
    @DisplayName("the colour fold composes with the class fold instead of replacing it")
    void colorFoldComposesWithTheClassFold() {
        long key = 0x1234_5678_9ABL;
        long classOnly = VulkanTerrainGpuBackend.celtium$pipelineKey(key, 1);
        long classAndColor = VulkanTerrainGpuBackend.celtium$pipelineColorKey(classOnly, 2);
        assertNotEquals(classOnly, classAndColor);
        assertNotEquals(VulkanTerrainGpuBackend.celtium$pipelineColorKey(key, 2), classAndColor,
                "class and count are separate axes of the same key");
    }

    @Test
    @DisplayName("prepare reads the count from the mod-program source and folds it into the key")
    void prepareReadsAndFoldsTheTargetColorCount() throws IOException {
        String source = read(BACKEND);
        assertTrue(source.contains("int targetColors = this.modPrograms.celtium$modTargetColors();"),
                "the count comes from the publication record, the same source mod programs read");
        assertTrue(source.contains("celtium$pipelineColorKey(celtium$pipelineKey(key, pairClass.ordinal()),"),
                "the count folds into the effective key beside the class, before any family cache sees it");
    }

    @Test
    @DisplayName("a multi-colour target's pipelines are built against that count's chain pass")
    void multiColorTargetsBuildAgainstTheChainPassOfTheirCount() throws IOException {
        String source = read(BACKEND);
        assertTrue(source.contains("long pass = targetColors > 1"),
                "the generation pass selection branches on the count");
        assertTrue(source.contains("? chainPairRenderPass(targetColors, pairClass)"),
                "the multi-colour branch builds against the (count, class) chain pass");
    }

    @Test
    @DisplayName("every immediate pipeline class replicates its blend state per colour attachment")
    void everyImmediateClassReplicatesItsBlendState() throws IOException {
        for (String name : PIPELINE_CLASSES) {
            String source = read("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/"
                    + name + ".java");
            assertEquals(1, count(source, "int blendCount = Math.max(1, this.colorAttachments);"),
                    name + ": the blend count is the pipeline's colour attachment count");
            assertEquals(1, count(source, "VkPipelineColorBlendAttachmentState.calloc(blendCount, stack)"),
                    name + ": the blend attachment buffer is sized by that count");
            assertEquals(0, count(source, "VkPipelineColorBlendAttachmentState.calloc(1, stack)"),
                    name + ": no fixed one-attachment blend state remains");
            assertTrue(source.contains("public int celtium$colorAttachments()"),
                    name + ": the count is readable, so a record-reading twin can inherit it");
        }
    }

    @Test
    @DisplayName("record-reading twins inherit the colour count with the pass they inherit")
    void recordsTwinsInheritTheColorCount() throws IOException {
        String indirect = read(INDIRECT);
        assertEquals(2, count(indirect, "celtium$colorAttachments()"),
                "both createRecords calls (colour and item) hand the source pipeline's count down");
        String textured = read("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/"
                + "VulkanImmediateTexturePipeline.java");
        assertTrue(textured.contains("pipeline.colorAttachments = state.celtium$colorAttachments();"),
                "createRecordsLike copies the count from the pipeline it twins");
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
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
