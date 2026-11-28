package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
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
        int capture = backend.indexOf("boolean celtium$tryCaptureBatchGroup(");
        int intern = backend.indexOf("int celtium$internBatchState(", capture);
        assertTrue(capture >= 0 && intern > capture, "the capture region is located");
        String body = backend.substring(capture, intern);
        assertTrue(body.contains("celtium$bindlessIndex(this.owner.celtium$registry().get(textureSlot), textureSlot)"),
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

    /**
     * The texture family answers its push size per variant, in the colour family's shape:
     * the array variant's fragment stage declares the family's 208-byte push block (it is
     * the one records-pair fragment that still reads the push range), the per-texture
     * variant declares none.
     */
    @Test
    void texturePipelineAnswersPushBytesPerVariant() throws IOException {
        String pipeline = read(PIPELINE);
        assertTrue(pipeline.contains("public int celtium$pushConstantBytes()"),
                "the texture family carries the push-size answer, like the colour family's");
        assertTrue(pipeline.contains("return this.arrayRecords ? PUSH_CONSTANT_BYTES : 0;"),
                "the answer is the variant's own: the full block for the array variant, none otherwise");
        assertTrue(pipeline.contains("PUSH_CONSTANT_BYTES = 208"),
                "the block is the 208 bytes the array fragment's push range spans");
    }

    /**
     * The capture carries the array twin's push size into the entry's binding: without it
     * the sink never pushes and the array fragment draws with an unset push range (the
     * verification leg's VUID 08601). The per-texture form keeps the initial zero - beside
     * the initializer, only the item branch, the colour branch and the array adoption
     * assign the local.
     */
    @Test
    void captureCarriesTheArrayTwinsPushBytes() throws IOException {
        String backend = read(BACKEND);
        int capture = backend.indexOf("boolean celtium$tryCaptureBatchGroup(");
        int intern = backend.indexOf("int celtium$internBatchState(", capture);
        assertTrue(capture >= 0 && intern > capture, "the capture region is located");
        String body = backend.substring(capture, intern);
        int adopted = body.indexOf("captureTwin = arrayTwin;");
        int carried = body.indexOf("pushBytes = arrayTwin.celtium$pushConstantBytes();");
        assertTrue(adopted >= 0 && carried > adopted,
                "adopting the array twin carries its push size into the entry's binding");
        assertEquals(1, occurrences(body, "pushBytes = value.pushBytes();"),
                "the item branch's assignment stands unchanged, the only one of its shape");
        assertEquals(1, occurrences(body, "pushBytes = value.celtium$pushConstantBytes();"),
                "the colour branch's assignment stands unchanged, the only one of its shape");
        assertTrue(body.contains("int pushBytes = 0;"),
                "the per-texture form still starts from zero");
    }

    /**
     * The binding's premise, corrected with the fix: the array pair's fragment reads the
     * push range, so the old blanket claim that the texture pairs read everything through
     * varyings must not come back.
     */
    @Test
    void bindingPremiseNamesTheArrayPair() throws IOException {
        String backend = read(BACKEND);
        assertFalse(backend.contains("the texture, block and particle records pairs"),
                "the old premise lumped the texture family into the varying-only pairs");
        assertTrue(backend.contains("the texture family's bindless array pair"),
                "the corrected premise names the array pair as a pushing form");
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
