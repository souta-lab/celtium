package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The backend half of the FontBatch sink contract (Stage 5 stage 4 entry pack, section 3-7; the
 * R-52 fix lineage). The batch-side mechanics the contract names - retention, re-offer, the caps,
 * the backoff, the re-entry guard - live in {@link FontBatch} and carry behavioural tests of their
 * own ({@link FontRunRetentionTest}, {@link FontBatchRetainedOrderTest},
 * {@link FontBatchFlushOrderTest}). What those tests cannot see is the sink itself,
 * {@code Lane2ImmediateRecorder.celtium$submitFontRun}: the batch retains a run only when the
 * sink answers {@code false} or throws, so the whole retention side stands on this one method's
 * return value, and the state it borrows (texture unit, alpha test, matrices,
 * {@code matricesDirty}) has to come back exactly as it was or the next batch draws contaminated.
 * The stage-4 wiring left the sink's shape unchanged - capture moved to the drain, where a
 * font-stamped group whose capture fails falls back to the direct draw (the per-group draw
 * until stage 6 replaced it with the record-twin draw) - so the contract is
 * preserved by construction; this class is the dedicated pin the wiring commits did not carry.
 * The backend needs a Vulkan device, so the decision sites are pinned as source contracts (the
 * repo's idiom for device-bound decisions, as in {@link TierASlabCapacityTest}).
 */
class FontRunSinkContractTest {

    private static String backendSource() throws IOException {
        return readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java");
    }

    private static String groupSource() throws IOException {
        return readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateGroup.java");
    }

    /** The sink body: from its signature to the matrix-boundary delegate that follows it. */
    private static String sinkRegion(String source) {
        int start = source.indexOf("private boolean celtium$submitFontRun(FontBatch.Run run) {");
        int end = source.indexOf("private void celtium$fontMatrixBoundary()", start);
        assertTrue(start >= 0 && end > start, "the sink region is located");
        return source.substring(start, end);
    }

    /** Everything before the try: the state the sink borrows is saved here. */
    private static String saveRegion(String sink) {
        int end = sink.indexOf("try {");
        assertTrue(end > 0, "the sink's try is located");
        return sink.substring(0, end);
    }

    /** The try body: the run's state is installed and the submission happens here. */
    private static String tryRegion(String sink) {
        int start = sink.indexOf("try {");
        int end = sink.indexOf("} catch (RuntimeException | LinkageError error) {", start);
        assertTrue(start >= 0 && end > start, "the sink's try region is located");
        return sink.substring(start, end);
    }

    /** The catch body: a submission that could not be made at all. */
    private static String catchRegion(String sink) {
        int start = sink.indexOf("} catch (RuntimeException | LinkageError error) {");
        int end = sink.indexOf("} finally {", start);
        assertTrue(start >= 0 && end > start, "the sink's catch region is located");
        return sink.substring(start, end);
    }

    /** The finally body: the borrowed state is handed back here, on every exit. */
    private static String finallyRegion(String sink) {
        int start = sink.indexOf("} finally {");
        assertTrue(start >= 0, "the sink's finally is located");
        return sink.substring(start);
    }

    /** The batch-population gate: from its signature to the capture attempt that follows it. */
    private static String eligibilityRegion(String source) {
        int start = source.indexOf("private boolean celtium$batchEligible(ImmediateGroup group");
        int end = source.indexOf("private boolean celtium$tryCaptureBatchGroup(", start);
        assertTrue(start >= 0 && end > start, "the eligibility region is located");
        return source.substring(start, end);
    }

    /** The drain-time capture attempt: from its signature to the state interning that follows it. */
    private static String captureRegion(String source) {
        int start = source.indexOf("private boolean celtium$tryCaptureBatchGroup(ImmediateGroup group");
        int end = source.indexOf("private int celtium$internBatchState(ImmediateGroup group) {", start);
        assertTrue(start >= 0 && end > start, "the capture region is located");
        return source.substring(start, end);
    }

    /** Section 3-7 point 1: a refused run comes back as false and is counted, never answered true. */
    @Test
    void refusalReturnsFalseAndIsCounted() throws IOException {
        String sink = sinkRegion(backendSource());
        int submit = sink.indexOf(
                "boolean accepted = submitImmediateBatch(run.format, run.stride, run.formatId, run.address,");
        int refusal = sink.indexOf("if (!accepted) {", submit);
        assertTrue(submit >= 0 && refusal > submit,
                "the submission's own verdict drives the refusal branch");
        assertTrue(sink.substring(refusal, refusal + 200).contains("this.fontRunRefusals++;"),
                "a refused run is counted in the sink's refusal census");
        assertTrue(sink.contains("return accepted;"),
                "the sink returns the submission's verdict to the batch");
        assertFalse(sink.contains("return true"),
                "an unconditional true is the R-52 form: refused runs vanish before the retention queue");
    }

    /** Section 3-7 point 1, thrown form: a submission that throws is also a refusal, counted the same way. */
    @Test
    void thrownSubmissionReturnsFalseAndIsCounted() throws IOException {
        String caught = catchRegion(sinkRegion(backendSource()));
        assertTrue(caught.contains("this.fontRunRefusals++;"),
                "a thrown submission is counted as a refusal");
        assertTrue(caught.contains("return false;"),
                "a thrown submission answers false so the batch retains the run");
    }

    /** Section 3-7 point 5, the save half: every borrowed value is read before the try opens. */
    @Test
    void stateIsSavedBeforeTheSubmission() throws IOException {
        String saved = saveRegion(sinkRegion(backendSource()));
        assertTrue(saved.contains("int savedTexture = this.immediateState.boundTextureName();"),
                "the bound texture is saved");
        assertTrue(saved.contains("boolean savedTexture2d = this.immediateState.texture2dEnabled();"),
                "the texture-2d enable is saved");
        assertTrue(saved.contains("boolean savedAlphaTest = this.immediateState.alphaTestEnabled();"),
                "the alpha-test enable is saved");
        assertTrue(saved.contains("float savedAlphaRef = this.immediateState.alphaFuncReference();"),
                "the alpha-test reference is saved");
        assertTrue(saved.contains(
                        "System.arraycopy(this.glProjection, 0, this.fontRunProjectionScratch, 0, 16);"),
                "the projection matrix is saved");
        assertTrue(saved.contains(
                        "System.arraycopy(this.glModelView, 0, this.fontRunModelViewScratch, 0, 16);"),
                "the model-view matrix is saved");
        assertTrue(saved.contains("int savedUnit = this.immediateState.activeTextureUnit();"),
                "the active texture unit is saved");
        assertTrue(saved.contains("boolean savedMatricesDirty = this.matricesDirty;"),
                "the matrices-dirty flag is saved");
        assertTrue(saved.contains("boolean savedFontRunSubmitting = this.fontRunSubmitting;"),
                "the sink-in-flight flag is saved, so a nested submission cannot leak the mark");
    }

    /** Section 3-7 point 5, the install half: the run's own state is in place before it is submitted. */
    @Test
    void runStateIsInstalledBeforeTheSubmission() throws IOException {
        String body = tryRegion(sinkRegion(backendSource()));
        int submit = body.indexOf("boolean accepted = submitImmediateBatch(");
        int unitZero = body.indexOf("this.immediateState.activeTextureUnit(0);");
        int bind = body.indexOf("this.immediateState.bindTexture(run.texture);");
        assertTrue(unitZero >= 0 && bind > unitZero && submit > bind,
                "the run's texture is bound on unit 0 - the unit the group's shader reads - before submitting");
        int directBind = body.indexOf("org.lwjgl.opengl.GL11.GL_TEXTURE_2D, run.texture);");
        assertTrue(directBind >= 0 && submit > directBind,
                "the run's texture is also bound through the direct-GL route the submission reads");
        int alphaOn = body.indexOf("this.immediateState.alphaTestEnabled(run.alphaTest);");
        int alphaRef = body.indexOf("this.immediateState.alphaFuncReference(run.alphaRef);");
        assertTrue(alphaOn >= 0 && alphaRef >= 0 && submit > alphaOn && submit > alphaRef,
                "the run's alpha test and reference are installed before submitting");
        int projection = body.indexOf("System.arraycopy(run.projection, 0, this.glProjection, 0, 16);");
        int modelView = body.indexOf("System.arraycopy(run.modelView, 0, this.glModelView, 0, 16);");
        assertTrue(projection >= 0 && modelView >= 0 && submit > projection && submit > modelView,
                "the run is submitted under the matrices it was appended under");
        int dirty = body.indexOf("this.matricesDirty = false;");
        assertTrue(dirty >= 0 && submit > dirty,
                "the dirty flag is cleared for the submission, so the live matrices cannot be re-captured over the run's");
        int flag = body.indexOf("this.fontRunSubmitting = true;");
        assertTrue(flag >= 0 && submit > flag,
                "the provenance flag stands for the whole submission, so its groups close font-stamped");
    }

    /** Section 3-7 point 5, the restore half: the finally hands every borrowed value back. */
    @Test
    void stateIsRestoredInTheFinallyBlock() throws IOException {
        String restored = finallyRegion(sinkRegion(backendSource()));
        assertTrue(restored.contains("this.fontRunSubmitting = savedFontRunSubmitting;"),
                "the provenance flag is restored");
        int unitZero = restored.indexOf("this.immediateState.activeTextureUnit(0);");
        int bindSaved = restored.indexOf("this.immediateState.bindTexture(savedTexture);");
        int unitBack = restored.indexOf("this.immediateState.activeTextureUnit(savedUnit);");
        assertTrue(unitZero >= 0 && bindSaved > unitZero && unitBack > bindSaved,
                "the saved texture is restored on unit 0 before the active unit itself is handed back");
        assertTrue(restored.contains("this.immediateState.texture2dEnabled(savedTexture2d);"),
                "the texture-2d enable is restored");
        assertTrue(restored.contains("org.lwjgl.opengl.GL11.GL_TEXTURE_2D, savedTexture);"),
                "the saved texture is restored through the direct-GL route as well");
        assertTrue(restored.contains("this.immediateState.alphaTestEnabled(savedAlphaTest);"),
                "the alpha-test enable is restored");
        assertTrue(restored.contains("this.immediateState.alphaFuncReference(savedAlphaRef);"),
                "the alpha-test reference is restored");
        assertTrue(restored.contains("System.arraycopy(savedProjection, 0, this.glProjection, 0, 16);"),
                "the projection matrix is restored");
        assertTrue(restored.contains("System.arraycopy(savedModelView, 0, this.glModelView, 0, 16);"),
                "the model-view matrix is restored");
        assertTrue(restored.contains("this.matricesDirty = savedMatricesDirty;"),
                "the matrices-dirty flag comes back exactly as it was");
    }

    /** The batch's sink is this method: FontBatch offers its runs here, nowhere else. */
    @Test
    void fontBatchSinkDelegatesToTheBackendSink() throws IOException {
        String source = backendSource();
        int start = source.indexOf("celtium$setFontRunSink(new FontBatch.RunSink()");
        assertTrue(start >= 0, "the sink registration is located");
        assertTrue(source.substring(start, start + 500)
                        .contains("return Lane2ImmediateRecorder.this.celtium$submitFontRun(run);"),
                "the registered RunSink delegates to the backend sink");
    }

    /** The refusal census reaches the retention summary a leg reads; its disappearance is a stop condition (T5). */
    @Test
    void refusalsReachTheRetentionCensus() throws IOException {
        String source = backendSource();
        int start = source.indexOf("public String celtium$fontRetentionSummary() {");
        assertTrue(start >= 0, "the retention summary is located");
        assertTrue(source.substring(start, start + 500).contains(" fontRunRefusals=\" + this.fontRunRefusals;"),
                "the sink's refusal count is printed with the batch's retention census");
    }

    /** The drain identifies font groups by the close-time stamp, and pool reuse clears the stamp. */
    @Test
    void groupsClosedUnderTheSinkCarryTheFontStamp() throws IOException {
        assertTrue(backendSource().contains("group.fromFontRun = this.fontRunSubmitting;"),
                "the group close stamps the group from the sink-in-flight flag");
        String group = groupSource();
        int start = group.indexOf("private void clearForReuse() {");
        assertTrue(start >= 0, "the group's reuse reset is located");
        assertTrue(group.substring(start, start + 1600).contains("this.fromFontRun = false;"),
                "a recycled group does not carry a previous submission's font stamp");
    }

    /**
     * The population axis across stages: stage 4 admitted font-stamped groups through the
     * eligibility gate's stamp term; stage 6's population is the whole record-twin
     * population, so the stamps left eligibility and now act where they belong - in the
     * capture's order classification, which keeps a font-stamped group (like a
     * UI-bracket group) ordered unconditionally.
     */
    @Test
    void fontGroupsAreABatchPopulation() throws IOException {
        assertFalse(eligibilityRegion(backendSource())
                        .contains("if (!group.closedInUiBracket && !group.fromFontRun) {"),
                "stage 6 eligibility no longer gates on the stamps: the population is the twin population");
        assertTrue(captureRegion(backendSource())
                        .contains("(group.closedInUiBracket || group.fromFontRun)"),
                "a font-stamped group is captured ordered, as a UI-bracket group is");
    }

    /**
     * The drain-side form of the contract (stage-4 record, section 2-2 correction): a font group is
     * already accepted at the drain, so a capture that has no room is not a refusal - the group
     * falls back to the per-group draw, counted in the wiring's own fallback counter, and the
     * font-run refusal census is not touched.
     */
    @Test
    void captureFailureFallsBackWithoutTouchingTheRefusalCensus() throws IOException {
        String capture = captureRegion(backendSource());
        assertTrue(capture.contains(
                        "boolean captured = this.batchBatcher.captureQuads(0, pipelineKey, stateKey, 0, textureSlot,"),
                "the group is offered to the batcher at the drain");
        // The records fallback reselection (audit 11, SOL-4 finding 1) retries a refused
        // record once behind a segment flush, so the first refusal branch in the capture
        // is the retry; the counted refusal this contract names is the last one, reached
        // when the group still has no record slot after the retry.
        int noRecord = capture.lastIndexOf("if (recordIndex < 0) {");
        assertTrue(noRecord >= 0 && capture.substring(noRecord, noRecord + 200)
                        .contains("this.batchFallbackGroups++;"),
                "a group with no record slot is counted as a fallback");
        int notCaptured = capture.indexOf("if (!captured) {");
        assertTrue(notCaptured >= 0, "the capture verdict is branched on");
        String fallback = capture.substring(notCaptured, notCaptured + 500);
        assertTrue(fallback.contains("this.batchFallbackGroups++;") && fallback.contains("return false;"),
                "an uncaptured group is counted and returned, so the caller draws it per-group - text is never dropped");
        assertFalse(capture.contains("fontRunRefusals"),
                "the drain fallback never touches the font-run refusal census");
    }

    private static String readSource(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
