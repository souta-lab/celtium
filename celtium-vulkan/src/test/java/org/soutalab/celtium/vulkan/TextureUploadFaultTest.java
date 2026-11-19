package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 432: the texture-upload fault injector must cover the mirror uploads, not only the immediate path.
 * <p>
 * The injector ({@link VulkanTextureFault}) existed before this item, but its only consultation was the
 * immediate path's white-texture substitution, its switches are JVM arguments read once at class load (so no
 * test could arm it), and the mirror's whole-image entries - {@code VulkanGlTexture.uploadFromGlTexture} and
 * {@code uploadFromImage} (the entry {@code uploadFromNamedImage} delegates to) - never asked it. These tests
 * pin the three parts of the fix, in the shape {@code TerrainPipelineFailureTest} established: the injector's
 * own counting and one-shot rules, the entries' refusal contract driven on a device-free instance (the refusal
 * precedes every device touch, which is what makes it drivable here), and source contracts for the ordering
 * the device-free drive cannot reach.
 * <p>
 * The stage question is pinned too, as the item's decision record states it: texture failure is a per-texture
 * event, so no {@code StageGate} stage is added for it - the refusal is per entry, is not remembered, and the
 * next ask retries (the terrain pipelines' own arrangement, whose per-pass remembered failure is the finer
 * instrument there; here the un-moved pixel hash and upload serial are the retry guarantee).
 * <p>
 * The suite assumes the test JVM carries no {@code celtium.vulkan.failTextureUpload*} properties, which is the
 * shipped test configuration; every test that arms the override disarms it again, so no armed state can leak
 * into another test in the same JVM.
 */
class TextureUploadFaultTest {

    @AfterEach
    void disarmTheInjector() {
        VulkanTextureFault.disarmForTest();
    }

    @Test
    void anUnarmedInjectorCountsNothingAndRefusesNothing() {
        VulkanTextureFault.disarmForTest();

        assertFalse(VulkanTextureFault.shouldFail("mirror-gl:1"));
        assertFalse(VulkanTextureFault.shouldFail("mirror-image:-1"));
        assertEquals(0L, VulkanTextureFault.requests(),
                "unarmed means uncounted: requests is the uploads the injector looked at, and it looked at none");
        assertEquals(0, VulkanTextureFault.injections());
    }

    @Test
    void aCountArmFailsExactlyTheNthUpload() {
        VulkanTextureFault.armForTest(2L, "");

        assertFalse(VulkanTextureFault.shouldFail("mirror-gl:1"), "the first upload passes");
        assertTrue(VulkanTextureFault.shouldFail("mirror-gl:2"), "the armed upload fails");
        assertFalse(VulkanTextureFault.shouldFail("mirror-gl:3"), "the arm is spent after its one failure");
        assertEquals(3L, VulkanTextureFault.requests());
        assertEquals(1, VulkanTextureFault.injections(), "exactly one injection is recorded");
    }

    @Test
    void aSiteArmFailsTheFirstMatchingUploadOnly() {
        VulkanTextureFault.armForTest(-1L, "mirror-gl:7");

        assertFalse(VulkanTextureFault.shouldFail("mirror-gl:8"), "a non-matching label never fails");
        assertTrue(VulkanTextureFault.shouldFail("mirror-gl:7"), "the first matching upload fails");
        assertFalse(VulkanTextureFault.shouldFail("mirror-gl:7"),
                "the site arm is one-shot: a later upload of the same texture is not failed again");
        assertEquals(1, VulkanTextureFault.injections());
    }

    @Test
    void aRefusedAnnouncedUploadWritesNothingAndClaimsNoContent() {
        VulkanTextureFault.armForTest(-1L, "mirror-image");
        VulkanGlTexture texture = VulkanGlTexture.uncreatedForTest();

        boolean written = texture.uploadFromImage(2, 2, ByteBuffer.allocate(2 * 2 * 4));

        assertFalse(written, "a refused upload answers false, the same answer a hash skip gives");
        assertFalse(texture.hasFullContent(),
                "the entry is left with no content rather than claiming the upload that never happened");
        assertEquals(1, VulkanTextureFault.injections());
    }

    @Test
    void aRefusedCapturedUploadLeavesTheMirrorContentless() {
        VulkanTextureFault.armForTest(-1L, "mirror-gl");
        VulkanGlTexture texture = VulkanGlTexture.uncreatedForTest();

        // GL_TEXTURE_2D; the refusal precedes every use of the target, so no GL state is touched here.
        texture.uploadFromGlTexture(7, 3553);

        assertFalse(texture.hasFullContent(),
                "the mirror keeps no content: old pixels are never presented as the failed upload's result");
        assertEquals(1, VulkanTextureFault.injections());
    }

    @Test
    void aRefusalIsNotRememberedSoTheNextAskRetries() {
        VulkanGlTexture texture = VulkanGlTexture.uncreatedForTest();
        ByteBuffer pixels = ByteBuffer.allocate(2 * 2 * 4);

        VulkanTextureFault.armForTest(1L, "");
        assertFalse(texture.uploadFromImage(2, 2, pixels), "the armed upload is refused");

        VulkanTextureFault.disarmForTest();
        assertFalse(VulkanTextureFault.shouldFail("mirror-image:-1"),
                "disarmed, the gate is open again: nothing about the refusal is remembered by the injector");

        boolean refusedAgain;
        try {
            refusedAgain = !texture.uploadFromImage(2, 2, pixels);
        } catch (RuntimeException | Error deviceMissing) {
            // Past the gate the entry needs a live device, which this instance has none of. What this test
            // pins is only that the answer is no longer the fault's refusal.
            refusedAgain = false;
        }
        assertFalse(refusedAgain,
                "the retry proceeds past the gate instead of being answered from a remembered refusal");

        VulkanTextureFault.armForTest(1L, "");
        assertEquals(0L, VulkanTextureFault.requests(), "re-arming restarts the counting from zero");
        assertFalse(texture.uploadFromImage(2, 2, pixels),
                "a fresh arm refuses a fresh first upload: the earlier refusal bought no immunity");
        assertEquals(1, VulkanTextureFault.injections());
    }

    @Test
    void noStageIsAddedForTextureUploads() {
        assertEquals(2, StageGate.Stage.values().length,
                "the stage registry stays terrain pipelines + post chain: texture failure is per-texture, "
                        + "and one bad texture must not switch off every upload (item 432 decision record)");
        assertNull(StageGate.stageNamed("textureUpload"), "no texture-upload stage exists to be switched off");
    }

    @Test
    void theAnnouncedEntryConsultsTheInjectorBeforeAnyContentFactMoves() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java"));
        int entry = source.indexOf("public boolean uploadFromImage(int pixelWidth, int pixelHeight");
        assertTrue(entry >= 0, "uploadFromImage is declared");
        String body = source.substring(entry, entry + 5000);
        int consult = body.indexOf("VulkanTextureFault.shouldFail");
        int invalidate = body.indexOf("invalidateUploadCache()");
        int commit = body.indexOf("commitFullUpload(hash");
        assertTrue(consult >= 0, "uploadFromImage consults the fault injector");
        assertTrue(invalidate > consult,
                "the consultation precedes the cache invalidation, so a refusal moves no content fact");
        assertTrue(commit > consult, "the consultation precedes the upload commit for the same reason");
        assertTrue(body.substring(consult, invalidate).contains("return false"),
                "the refused branch answers false before anything is written");
    }

    @Test
    void theCapturedEntryConsultsTheInjectorBeforeAnyPixelsAreRead() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java"));
        int entry = source.indexOf("public void uploadFromGlTexture(int glTextureId, int glTextureTarget)");
        assertTrue(entry >= 0, "uploadFromGlTexture is declared");
        String body = source.substring(entry, entry + 6000);
        int consult = body.indexOf("VulkanTextureFault.shouldFail");
        int read = body.indexOf("readGlLevel0(glTextureId");
        assertTrue(consult >= 0, "uploadFromGlTexture consults the fault injector");
        assertTrue(read > consult,
                "the consultation precedes the pixel read, so a refused upload reads and copies nothing");
        assertTrue(body.substring(consult, read).contains("return;"),
                "the refused branch returns before the upload machinery starts");
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
