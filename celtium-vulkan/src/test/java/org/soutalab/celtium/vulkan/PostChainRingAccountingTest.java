package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ring-accounting reset placement behind audit-vulkan-core F1, pinned as a source contract because the
 * failure needs a device-bound recording sequence to reproduce at runtime.
 * <p>
 * {@code recordPasses} returns an abandoned recording's descriptor-ring slots from the wrapper's catch, reading
 * {@code setsThisRecording} and {@code recordingStartCursor}. Those fields used to be reset only after the
 * unsupplied-input refusal - the one throw the prologue raises before taking anything - so the sequence
 * "recording N succeeds, recording N+1 is refused as unsupplied" handed the abandon path recording N's count
 * and start cursor: it cleared the ownership epochs of slots recording N may still have in flight and rewound
 * the cursor onto them, letting a later recording overwrite a set an in-flight submission reads
 * (VUID-vkUpdateDescriptorSets-None-03047, the shape rule B11 exists to prevent).
 * <p>
 * The fix resets the accounting at the head of the recording, before every refusal the method can raise. These
 * assertions pin that placement: both resets precede the unsupplied throw inside {@code beginChainRecording}
 * (the recording head {@code recordPasses} delegates to since the ChainRecording session move, arch-graph-r8),
 * neither reset is repeated later in the file outside the abandon path itself, and the abandon path still
 * early-returns when nothing was taken.
 */
class PostChainRingAccountingTest {

    private static String postChainSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/ChainRecorder.java"));
    }

    @Test
    void theRingAccountingIsResetBeforeTheUnsuppliedRefusal() throws IOException {
        String source = postChainSource();
        int method = source.indexOf("private ChainRecording beginChainRecording");
        assertTrue(method >= 0, "the recording body is present");
        int refusal = source.indexOf("throw new UnsuppliedInputException", method);
        assertTrue(refusal > method, "the unsupplied refusal is present in the recording body");
        String prologue = source.substring(method, refusal);
        assertTrue(prologue.contains("this.setsThisRecording = 0;"),
                "the taken count is reset before the unsupplied refusal can be raised");
        assertTrue(prologue.contains("this.recordingStartCursor = this.setCursor;"),
                "the start cursor is reset before the unsupplied refusal can be raised");
    }

    @Test
    void theResetIsNotRepeatedAfterThePrologue() throws IOException {
        String source = postChainSource();
        assertEquals(2, countOccurrences(source, "this.setsThisRecording = 0;"),
                "one reset at the recording head and one in the abandon path that consumes it; no third site");
        assertEquals(1, countOccurrences(source, "this.recordingStartCursor = this.setCursor;"),
                "the start cursor is assigned only at the recording head");
    }

    @Test
    void theAbandonPathStillReturnsNothingWhenNothingWasTaken() throws IOException {
        String source = postChainSource();
        int abandon = source.indexOf("private void celtium$returnSlotsOfAbandonedRecording");
        assertTrue(abandon >= 0, "the abandon path is present");
        String body = source.substring(abandon, source.indexOf("private void celtium$recordOnePass", abandon));
        assertTrue(body.contains("if (taken <= 0)"),
                "a recording that took nothing returns nothing");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
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
