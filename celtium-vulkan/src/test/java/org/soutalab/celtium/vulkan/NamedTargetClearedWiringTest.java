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
 * The wiring of the named-target cleared discipline, pinned as a source contract (lanechat #1596).
 * <p>
 * The discipline is only real if the phases are connected: {@code PassExecutor} notes a clear into the
 * recording of the command buffer it is recording into (never straight into the confirmed cleared
 * frame), its clear decision reads the recorded head so a clear is visible inside its own recording
 * and invisible after that recording is abandoned, and the recording lifecycle the backend and the
 * frame coordinator already drive for layouts settles the clears with it - one recording per command
 * buffer, committed and abandoned as a unit. The semantics themselves are pinned behaviourally in
 * {@link NamedTargetClearedRecordingTest}; this contract pins that the production paths use them.
 */
class NamedTargetClearedWiringTest {

    private static final String MAIN = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";

    @Test
    void theRegistryHasNoImmediateClearedNoteLeft() throws IOException {
        String registry = Files.readString(findRepoFile(MAIN + "RenderTargetRegistry.java"));
        assertFalse(registry.contains("public void noteNamedTargetCleared(String name, long frameSerial)"),
                "the recording-time immediate note is gone");
        assertTrue(registry.contains(
                        "public void noteNamedTargetCleared(long commandBuffer, String name, long frameSerial)"),
                "the note is keyed by the recording it belongs to");
        assertTrue(registry.contains("public long recordedClearedFrame()"),
                "the recorded cleared head is readable apart from the confirmed cleared frame");
    }

    @Test
    void everyPassExecutorClearedNoteNamesItsRecording() throws IOException {
        String executor = Files.readString(findRepoFile(MAIN + "PassExecutor.java"));
        int notes = countOccurrences(executor, "noteNamedTargetCleared(");
        int keyed = countOccurrences(executor, "noteNamedTargetCleared(commandBuffer.address(),");
        assertTrue(notes > 0, "PassExecutor still notes named-target clears");
        assertEquals(notes, keyed, "every note is keyed by the command buffer being recorded into");
    }

    @Test
    void theClearDecisionReadsTheRecordedHead() throws IOException {
        String executor = Files.readString(findRepoFile(MAIN + "PassExecutor.java"));
        String decision = methodSlice(executor, "private static boolean shouldClearOutput(", 1000);
        assertTrue(decision.contains("state.recordedClearedFrame()"),
                "the CLEAR_ON_CREATE decision reads the recorded head");
        assertFalse(decision.contains("state.clearedFrame()"),
                "the decision no longer reads the confirmed cleared frame directly");
    }

    @Test
    void theRecordingLifecycleSettlesTheClearedNotes() throws IOException {
        String registry = Files.readString(findRepoFile(MAIN + "RenderTargetRegistry.java"));
        String begin = methodSlice(registry, "public void beginNamedTargetLayoutRecording(", 900);
        String commit = methodSlice(registry, "public void commitNamedTargetLayoutRecording(", 1600);
        String abandon = methodSlice(registry, "public void abandonNamedTargetLayoutRecording(", 900);
        assertTrue(begin.contains("namedClearedRecordings"),
                "opening a recording drops the previous recording's clears on the handle");
        assertTrue(commit.contains("namedClearedRecordings"),
                "committing a recording makes its clears fact");
        assertTrue(abandon.contains("namedClearedRecordings"),
                "abandoning a recording drops its clears");
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
