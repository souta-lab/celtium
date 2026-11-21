package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The failure cleanup of {@code PassExecutor.recordPass}, pinned as a source contract
 * (audit-vulkan-core E1).
 * <p>
 * A pass is recorded into the frame's own command buffer, between everything else the frame
 * records. If the draw (or anything else between {@code vkCmdBeginRenderPass} and
 * {@code vkCmdEndRenderPass}) threw, the old catch recorded the refusal and returned with the
 * buffer still inside the render pass - every command recorded after it, and the buffer's own
 * end, were invalid for the rest of the frame. The cleanup is structural (the path needs a device
 * and a recording buffer to run), so what is pinned here is the shape: the open-pass flag is set
 * only after begin and cleared only after end, the failure path closes an open pass before it
 * notes the refusal, and the output's layout note - owed because its transition barrier was
 * recorded before the pass began - is paid on the failure path exactly as on the success path.
 */
class PassExecutorFailureCleanupTest {

    /** The whole of {@code recordPass}, from its signature. */
    private static String recordPassBody() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PassExecutor.java"));
        return methodSlice(source, "private boolean recordPass(", 9000);
    }

    // The flag brackets the pass: set after begin returns, cleared after end returns, so the
    // failure path can tell an open pass from a failure that happened before begin.
    @Test
    void theOpenFlagBracketsThePass() throws IOException {
        String body = recordPassBody();
        int begun = body.indexOf("vkCmdBeginRenderPass(commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);");
        int opened = body.indexOf("passOpen = true;");
        int drawn = body.indexOf("pipeline.draw(commandBuffer, this.quadMesh, uniforms, samplerSet, activeBindCache);");
        int ended = body.indexOf("vkCmdEndRenderPass(commandBuffer);", drawn);
        int closed = body.indexOf("passOpen = false;", drawn);
        assertTrue(begun >= 0 && opened > begun, "the flag is set once the pass is open");
        assertTrue(drawn > opened, "the draw happens inside the bracket");
        assertTrue(ended > drawn && closed > ended, "the flag is cleared once the pass is ended");
    }

    // The output's transition is recorded before the pass begins, and the flag that says so is set
    // at the recording - so a failure anywhere later still owes the layout note.
    @Test
    void theOutputTransitionIsMarkedWhereItIsRecorded() throws IOException {
        String body = recordPassBody();
        int transition = body.indexOf(
                "transitionImageLayout(commandBuffer, output.image(), output.layout(), attachmentLayout, output.depth(),");
        int marked = body.indexOf("outputTransitionRecorded = true;");
        assertTrue(transition >= 0 && marked > transition,
                "recording the output's barrier marks its layout note as owed");
    }

    // The failure path: an open pass is closed (behind its own guard), the owed layout note is
    // paid (behind its own guard), and only then is the refusal noted - the order the success
    // path's invariants need.
    @Test
    void failureClosesThePassPaysTheNoteAndThenRefuses() throws IOException {
        String body = recordPassBody();
        int caught = body.indexOf("} catch (RuntimeException | LinkageError failure) {");
        assertTrue(caught >= 0, "recordPass has a failure path");
        String failurePath = body.substring(caught);
        int passGuard = failurePath.indexOf("if (passOpen) {");
        int passClosed = failurePath.indexOf("vkCmdEndRenderPass(commandBuffer);");
        int noteGuard = failurePath.indexOf("if (outputTransitionRecorded) {");
        int notePaid = failurePath.indexOf(
                "this.targetRegistry.noteNamedTargetLayout(output.name(), attachmentLayout);");
        int refused = failurePath.indexOf("this.passRegistry.noteExecutionRefusal(");
        assertTrue(passGuard >= 0 && passClosed > passGuard,
                "an open pass is closed on the failure path");
        assertTrue(noteGuard > passClosed && notePaid > noteGuard,
                "the owed output layout note is paid on the failure path");
        assertTrue(refused > notePaid, "the refusal is noted after the cleanup");
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
