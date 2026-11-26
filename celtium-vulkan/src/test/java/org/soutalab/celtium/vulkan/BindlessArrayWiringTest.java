package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bindless array wiring slice's contracts, pinned by source reading in the house style
 * (see {@link Stage5Stage3WiringTest}): the connections this slice exists to make - the
 * array shader's record stride matching the writer's, the array twin derived from the
 * prepared pipeline, the capture resolving the index and flagging the entry, the frame-wide
 * pin being gone - are exactly the parts a device-less test cannot execute, so they are
 * pinned as source facts. Behaviour that CAN run headless lives in the batch package's
 * tests ({@code ImmediateBatcherFallbackTest}, {@code BatchPlanExecutorTest}).
 */
class BindlessArrayWiringTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String PIPELINE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTextureRecordsPipeline.java";
    private static final String HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateRecordsPipelines.java";
    private static final String BATCHER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/batch/ImmediateBatcher.java";
    private static final String EXECUTOR =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/batch/BatchPlanExecutor.java";
    private static final String STORE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/InstanceDataStore.java";
    private static final String ARRAY_SHADER =
            "celtium-vulkan/src/main/shaders/immediate_tex_color_array.vert";

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

    /**
     * The stride fix the whole slice stands on: the array vertex shader's DrawRecord must
     * span the writer's full 256-byte stride (the two pads past 208), or every record after
     * the first is read 32 bytes early per step. The index seat stays at 216, the writer's
     * own offset.
     */
    @Test
    void arrayShaderDeclaresTheWritersFullStride() throws IOException {
        String shader = read(ARRAY_SHADER);
        assertTrue(shader.contains("vec4 u_RecordPad2;"),
                "the first pad past the old 224-byte form is declared");
        assertTrue(shader.contains("vec4 u_RecordPad3;"),
                "the second pad completes the 256-byte stride");
        assertTrue(shader.contains("256-byte stride"),
                "the stride comment names the writer's stride, not the stale 224");
        String store = read(STORE);
        assertTrue(store.contains("RECORD_BYTES = 256"), "the writer's stride is 256");
        assertTrue(store.contains("ARRAY_INDEX_OFFSET = 216"),
                "the writer stores the array index at 216");
        assertTrue(shader.contains("216"), "the shader's index seat comment names 216");
    }

    /**
     * The array twin is derived from the prepared pipeline instance, carriers copied, so a
     * group drawn through the prewarm's standard substitute twins THAT state, not the
     * nominal one.
     */
    @Test
    void arrayTwinIsDerivedFromThePreparedPipeline() throws IOException {
        String pipeline = read(PIPELINE);
        assertTrue(pipeline.contains(
                        "createArrayLike(\n            VulkanImmediateTextureRecordsPipeline like, long arraySetLayout)"),
                "the Like factory takes the prepared pipeline itself");
        assertTrue(pipeline.contains("pipeline.colorAttachments = like.colorAttachments;"),
                "the colour attachment count is copied, not defaulted");
        assertTrue(pipeline.contains("pipeline.arrayRecords = true;"),
                "the twin is the array variant");
        assertTrue(pipeline.contains("celtium$bindlessArrayEligible()"),
                "the variant rejection matrix lives on the pipeline");
    }

    /** The host caches one array twin per records twin and releases them with the rest. */
    @Test
    void hostCachesAndReleasesArrayTwins() throws IOException {
        String host = read(HOST);
        assertTrue(host.contains("celtium$arrayPipelineForBatch("),
                "the capture's accessor exists on the host");
        assertTrue(occurrences(host, "textureArrayPipelines") >= 4,
                "the cache is declared, read, filled and released");
        int release = host.indexOf("int celtium$releaseAll()");
        assertTrue(release >= 0 && host.substring(release).contains("textureArrayPipelines"),
                "the release walk deletes the array twins");
    }

    /**
     * The capture: the index is resolved at capture (the group's own stamp is -1 by design),
     * the record write carries it, the flag reaches the batcher in both capture attempts,
     * and no per-texture binding is registered for an array capture.
     */
    @Test
    void captureResolvesTheIndexAndFlagsTheEntry() throws IOException {
        String backend = read(BACKEND);
        int capture = backend.indexOf("private boolean celtium$tryCaptureBatchGroup(");
        int intern = backend.indexOf("private int celtium$internBatchState(", capture);
        assertTrue(capture >= 0 && intern > capture, "the capture region is located");
        String body = backend.substring(capture, intern);
        assertTrue(body.contains("celtium$bindlessIndex(celtium$registry().get(textureSlot), textureSlot)"),
                "the index is resolved at capture through the pure lookup");
        assertTrue(body.contains("celtium$arrayPipelineForBatch(recordsTwin,"),
                "the array twin is resolved from the prepared twin");
        assertTrue(body.contains("this.drawRecords.record(push, arrayIndex)"),
                "the record write carries the resolved index (-1 on the per-texture form)");
        assertTrue(occurrences(body, "textureInRecord, recordIndex, ordered, vertices") == 2,
                "the flag reaches the batcher in the attempt and the mid-flush retry alike");
        assertTrue(body.contains("if (!colour && !textureInRecord) {"),
                "an array capture registers no per-texture binding");
    }

    /**
     * The deviation this slice removes: no frame-wide bindless shape remains anywhere in
     * the production wiring - the batcher holds no such field, the backend sets none, and
     * the executor's signature carries no such argument. The truth is per entry.
     */
    @Test
    void theFrameWidePinIsGone() throws IOException {
        assertFalse(read(BACKEND).contains("setBindlessEnabled"),
                "the backend no longer pins the batcher to the per-texture shape");
        assertFalse(read(BATCHER).contains("bindlessEnabled"),
                "the batcher holds no frame-wide bindless flag");
        assertTrue(read(EXECUTOR).contains("public void execute(BatchPlan plan, DrawSink sink)"),
                "the executor takes no frame-wide shape argument");
        assertTrue(read(BACKEND).contains("this.batchExecutor.execute(plan, new BatchDrawSink(stack));"),
                "the flush hands the executor the plan and the sink only");
    }

    /** The route census carries the array count, so the verification leg can read the share. */
    @Test
    void censusCarriesTheArrayCount() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("\" arrayCaptured=\" + this.batchArrayCapturedGroups"),
                "the CELTIUM_BATCH_ROUTE line prints the array-captured count");
        assertTrue(backend.contains("this.batchArrayCapturedGroups++;"),
                "the count is incremented on an array capture");
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
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }
}
