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
 * The wiring of the named-target layout discipline, pinned as a source contract
 * (audit-vulkan-core E2).
 * <p>
 * The discipline is only real if the three phases are connected: {@code PassExecutor} notes into the
 * recording of the command buffer it is recording into (never straight into the confirmed layout),
 * the backend opens the recording where it opens the foreign-image one, and the frame coordinator
 * commits it where it commits the foreign-image one - the one point both submission shapes pass
 * after the queue accepted. The semantics themselves are pinned behaviourally in
 * {@link NamedTargetLayoutRecordingTest}; this contract pins that the production paths use them.
 */
class NamedTargetLayoutWiringTest {

    private static final String MAIN = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";

    @Test
    void theRegistryHasNoImmediateLayoutNoteLeft() throws IOException {
        String registry = Files.readString(findRepoFile(MAIN + "RenderTargetRegistry.java"));
        assertFalse(registry.contains("public void noteNamedTargetLayout(String name, int layout)"),
                "the recording-time immediate note is gone");
        assertTrue(registry.contains("public void noteNamedTargetLayout(long commandBuffer, String name, int layout)"),
                "the note is keyed by the recording it belongs to");
        assertTrue(registry.contains("public void beginNamedTargetLayoutRecording(long commandBuffer)"),
                "the recording can be opened (and the previous one on the handle dropped)");
        assertTrue(registry.contains("public void commitNamedTargetLayoutRecording(long commandBuffer)"),
                "the recording can be committed");
        assertTrue(registry.contains("public void abandonNamedTargetLayoutRecording(long commandBuffer)"),
                "the recording can be abandoned");
    }

    @Test
    void everyPassExecutorNoteNamesItsRecording() throws IOException {
        String executor = Files.readString(findRepoFile(MAIN + "PassExecutor.java"));
        int notes = countOccurrences(executor, "noteNamedTargetLayout(");
        int keyed = countOccurrences(executor, "noteNamedTargetLayout(commandBuffer.address(),");
        assertTrue(notes > 0, "PassExecutor still notes named-target layouts");
        assertEquals(notes, keyed, "every note is keyed by the command buffer being recorded into");
    }

    @Test
    void theBackendOpensTheRecordingWithTheForeignImageOne() throws IOException {
        String backend = Files.readString(findRepoFile(MAIN + "VulkanTerrainGpuBackend.java"));
        int foreign = backend.indexOf("VulkanGlTexture.beginLayoutRecording(this.commandBuffer);");
        int named = backend.indexOf(
                "this.targetRegistry.beginNamedTargetLayoutRecording(this.commandBuffer.address());");
        assertTrue(foreign >= 0, "the foreign-image recording is opened at beginCommands");
        assertTrue(named > foreign, "the named-target recording opens at the same point");
    }

    @Test
    void theCoordinatorCommitsTheRecordingWithTheForeignImageOne() throws IOException {
        String coordinator = Files.readString(findRepoFile(MAIN + "FrameCoordinator.java"));
        String bookkeeping = methodSlice(coordinator, "private void celtium$noteSubmissionBookkeeping(", 4000);
        int foreign = bookkeeping.indexOf("VulkanGlTexture.commitLayoutRecording(this.host.commandBuffer());");
        int named = bookkeeping.indexOf("commitNamedTargetLayoutRecording(this.host.commandBuffer().address());");
        assertTrue(foreign >= 0, "the foreign-image recording is committed in the submission bookkeeping");
        assertTrue(named > foreign,
                "the named-target recording is committed at the same acceptance point");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
    }

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
