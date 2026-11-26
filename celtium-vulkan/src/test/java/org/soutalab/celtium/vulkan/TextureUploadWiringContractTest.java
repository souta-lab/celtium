package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source contracts for the S3R-3 landing-A wiring: the shared texture upload service is owned
 * by the context, its batches ride the NORMAL deferred entry (the buffer is the context's own
 * one-shot buffer, so the D1 external entry - for buffers another owner must never see
 * recycled - must not appear), the budget gate and the ledger settlement sit in the submitter,
 * the deferred poll drives the queue, teardown destroys the backing, and the texture routes
 * its verbatim full-level uploads through the service while transform-armed textures keep the
 * legacy path. The mechanism lives inside device-bound methods, so - like
 * {@link PresentTransitionFoldContractTest} - these pins are by source shape.
 */
class TextureUploadWiringContractTest {

    private static final String CONTEXT =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java";
    private static final String TEXTURE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java";
    private static final String DIAGNOSTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static String window(String text, String anchor, int length) {
        int start = text.indexOf(anchor);
        assertTrue(start >= 0, "anchor is present: " + anchor);
        return text.substring(start, Math.min(text.length(), start + length));
    }

    @Test
    void theContextOwnsTheServiceOnASlabBacking() throws IOException {
        String body = window(read(CONTEXT), "TextureUploadService celtium$textureUploadService()", 3000);
        assertTrue(body.contains("celtium$hostVisibleSlabs("), "the backing is a slab-pool slice");
        assertTrue(body.contains("new TextureUploadService(TEXTURE_STAGING_CAPACITY_BYTES, 16L)"),
                "the service is created with the recorded capacity");
    }

    @Test
    void batchesRideTheNormalDeferredEntryNeverTheExternalOne() throws IOException {
        String body = window(read(CONTEXT), "private long celtium$submitTextureBatch(", 2000);
        assertTrue(body.contains("celtium$submitDeferred(commandBuffer, 0L)"),
                "the batch buffer is the context's own and submits through the normal entry");
        assertFalse(body.contains("celtium$submitDeferredExternal("),
                "the D1 external entry is for other-owned buffers and must not carry texture batches");
        assertTrue(body.contains("celtium$flushBatchedSubmits()"),
                "the submission is flushed before its stamp is read");
    }

    @Test
    void theGateConsultsTheBudgetLedgerAndTheSubmitterSettlesIt() throws IOException {
        String context = read(CONTEXT);
        String gate = window(context, "private boolean celtium$textureUploadMayIssue(", 1200);
        assertTrue(gate.contains("wouldExceed("), "the gate asks the ledger before issuing");
        assertTrue(gate.contains("noteDeferred("), "an over-budget request is noted as deferred");
        String submitter = window(context, "private long celtium$submitTextureBatch(", 2600);
        assertTrue(submitter.contains("noteFlushed("), "submitted texture bytes enter the window account");
        assertTrue(submitter.contains("noteDeferredIssued("),
                "a previously deferred request settles its note when it submits");
    }

    @Test
    void thePollDrivesTheQueueAndTeardownDestroysTheBacking() throws IOException {
        String context = read(CONTEXT);
        String poll = window(context, "public int celtium$pollDeferredSubmits()", 5200);
        assertTrue(poll.contains("celtium$flushTextureUploads()"),
                "the deferred poll is the frame-side drive point");
        String delete = window(context, "public void delete()", 24000);
        assertTrue(delete.contains("destroyTextureStagingBacking()"),
                "context teardown destroys the shared staging backing");
    }

    @Test
    void theTextureRoutesVerbatimUploadsAndKeepsTransformedOnesLegacy() throws IOException {
        String texture = read(TEXTURE);
        String whole = window(texture, "private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight,"
                + " int level, int offsetX, int offsetY)", 3800);
        assertTrue(whole.contains("celtium$tryServiceUploadWhole("), "level-0 uploads try the service first");
        String level = window(texture, "private void copyMipLevelToImage(", 2600);
        assertTrue(level.contains("celtium$tryServiceUploadLevel("), "single-level uploads try the service first");
        String armed = window(texture, "private boolean celtium$uploadTransformArmed()", 400);
        assertTrue(armed.contains("poisonColour") && armed.contains("encodeIds"),
                "poison and coordinate-encode keep the transforming legacy path");
        String destroy = window(texture, "private void destroyImageResources()", 600);
        assertTrue(destroy.contains("celtium$drainTextureUploads()"),
                "image destroy/recreate drains the queue before retiring the handle");
    }

    @Test
    void theSummaryLineCarriesTheThreeCounters() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        assertTrue(diagnostics.contains("textureUploadSubmits="), "submits counter is spliced in");
        assertTrue(diagnostics.contains("textureUploadBytes="), "bytes counter is spliced in");
        assertTrue(diagnostics.contains("textureUploadDeferredBytes="), "deferred counter is spliced in");
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
