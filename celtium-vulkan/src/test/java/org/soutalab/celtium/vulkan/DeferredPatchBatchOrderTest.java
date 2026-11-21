package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L06-02: a deferred patch batch names the image, the extent and the staging slices it was deferred against,
 * so it has to be submitted before anything reuses or retires them.
 * <p>
 * Two whole-write entries broke that order. {@code copyToImage} reused the staging ring first - resetting the
 * cursor, overwriting the handed-out slices from offset 0, and on the growth path destroying the staging buffer
 * outright - and submitted the pending batch only afterwards, so the batch recorded copies of pixels that were
 * no longer the deferred ones, from a buffer that might no longer be the one the offsets were handed out of.
 * {@code recreateImageResources} retired the old image without touching the pending batch at all; the submit
 * guard in {@code transitionToShaderRead} then flushed it against the NEW image, declaring a sampled layout the
 * new image never had and copying rectangles measured for the old extent. The sibling entries
 * ({@code copyMipLevelToImage}, {@code transitionToShaderRead}) and the ring's own rule in
 * {@code reserveStagingSlice} - submit before reuse - already had the order right; these tests pin all three
 * whole-write entries to it.
 * <p>
 * The orderings live behind a live device, which the device-free drive cannot reach, so they are pinned as
 * source contracts in the shape {@code TextureUploadFaultTest} established.
 */
class DeferredPatchBatchOrderTest {

    private static final String GUARD = "if (this.deferredCount > 0) submitPatchBatch();";

    @Test
    void aWholeImageUploadSubmitsThePendingBatchBeforeItReusesTheStagingRing() throws IOException {
        String source = textureSource();
        int entry = source.indexOf(
                "private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight, int level,"
                        + " int offsetX, int offsetY)");
        assertTrue(entry >= 0, "copyToImage is declared");
        int end = source.indexOf("private void ensureStaging", entry);
        assertTrue(end > entry, "ensureStaging is declared after copyToImage");
        String body = source.substring(entry, end);
        int guard = body.indexOf(GUARD);
        int staging = body.indexOf("ensureStaging(bytes);");
        int pixels = body.indexOf("putStagingPixels(stagingAddress");
        assertTrue(guard >= 0, "copyToImage submits a pending deferred batch");
        assertTrue(staging > guard,
                "the submission precedes ensureStaging, whose cursor reset and possible buffer replacement"
                        + " would otherwise strand the slices the batch still reads");
        assertTrue(pixels > guard,
                "the submission precedes the pixel write at offset 0, which overwrites the ring from its start");
    }

    @Test
    void aRecreateSubmitsThePendingBatchBeforeItRetiresTheImageItNames() throws IOException {
        String source = textureSource();
        int entry = source.indexOf("private void recreateImageResources(int newWidth, int newHeight)");
        assertTrue(entry >= 0, "recreateImageResources is declared");
        int end = source.indexOf("private void destroyImageResources()", entry);
        assertTrue(end > entry, "destroyImageResources is declared after recreateImageResources");
        String body = source.substring(entry, end);
        int guard = body.indexOf(GUARD);
        int destroy = body.indexOf("destroyImageResources();");
        int create = body.indexOf("createImageResources(newWidth, newHeight);");
        assertTrue(guard >= 0, "recreateImageResources submits a pending deferred batch");
        assertTrue(destroy > guard,
                "the submission precedes the destroy, so the batch still lands on the image it was deferred"
                        + " against instead of being flushed onto its replacement");
        assertTrue(create > guard, "the submission precedes the replacement image's creation for the same reason");
    }

    @Test
    void theMipLevelUploadKeepsTheSameOrder() throws IOException {
        String source = textureSource();
        int entry = source.indexOf("private void copyMipLevelToImage(");
        assertTrue(entry >= 0, "copyMipLevelToImage is declared");
        String body = source.substring(entry, Math.min(source.length(), entry + 6000));
        int guard = body.indexOf(GUARD);
        int staging = body.indexOf("ensureStaging(bytes);");
        assertTrue(guard >= 0, "copyMipLevelToImage submits a pending deferred batch");
        assertTrue(staging > guard, "the mip-level entry keeps the submit-before-staging order");
    }

    private static String textureSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java"));
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
