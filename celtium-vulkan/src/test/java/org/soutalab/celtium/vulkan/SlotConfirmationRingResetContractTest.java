package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-frame linear ring's reset wiring, pinned at the layers a CPU gate can reach, in the
 * house source-contract style.
 * <p>
 * The ring (Stage 2) is bump-only: a slot's cursor is recycled solely by
 * {@code VulkanContext.celtium$resetFrameLinearSlot}, whose gate is "the slot's submission has
 * been confirmed complete". The reset was wired only into {@code waitForSlot}'s success exits,
 * but the dominant confirmation route is the non-blocking completion poll - and a slot the poll
 * already confirmed is no longer pending, so the reuse gate never enters the wait for it at all.
 * Poll-confirmed slots therefore kept their cursor across every reuse until the 32 MiB slot
 * pinned at capacity; from then on {@code InstanceDataStore.selectSlot}'s leading allocation
 * failed and every record of the window was refused - the stage-4 legs' re-run measured
 * droppedDraws VS 90,921 / HUD 241,640 / INV 68,462 on jar 278d2560d, with the settings panel
 * and the HUD elements missing on screen (separation record OVERHAUL-DROPDRAWS-SEPARATION).
 * <p>
 * The fix seats the reset in {@code celtium$noteSlotConfirmed}, the one tail every confirmation
 * route runs through - the wait, the poll, the deferred submits' reclaim callback and a
 * device-wide idle settle - and removes the wait's three private copies. These tests pin that
 * wiring: the tail resets, the wait does not, and each route still reaches the tail. The reset's
 * own semantics (bump-only reuse, a held slot deferring its reset to the last release) are
 * behavioural and are pinned by {@code LinearRingAllocatorTest}; what was missing for two months
 * was not the semantics but this wiring, which is why it is pinned here as source.
 */
class SlotConfirmationRingResetContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    /**
     * The body of the method whose declaration contains {@code signature}, from its opening
     * brace to the matching close. Strings, char literals and comments are skipped while
     * matching, so braces inside them cannot end the body early.
     */
    private static String methodBody(String source, String signature) {
        int at = source.indexOf(signature);
        assertTrue(at >= 0, "declaration not found: " + signature);
        int open = source.indexOf('{', at);
        assertTrue(open >= 0, "no body opens after: " + signature);
        int depth = 0;
        int i = open;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(source, i, c);
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                int end = source.indexOf('\n', i);
                i = end < 0 ? source.length() : end;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 2;
            } else if (c == '{') {
                depth++;
                i++;
            } else if (c == '}') {
                depth--;
                i++;
                if (depth == 0) {
                    return source.substring(open, i);
                }
            } else {
                i++;
            }
        }
        throw new IllegalStateException("unbalanced braces after: " + signature);
    }

    private static int skipLiteral(String source, int start, char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else {
                i++;
            }
        }
        return source.length();
    }

    @Test
    @DisplayName("the confirmation tail resets the confirmed slot's ring, exactly once")
    void theConfirmationTailResetsTheRing() throws IOException {
        String body = methodBody(read(BACKEND),
                "private void celtium$noteSlotConfirmed(int slot, boolean lostDevice)");
        assertEquals(1, occurrences(body, "this.context.celtium$resetFrameLinearSlot(slot);"),
                "every confirmation route runs through this tail, so the reset seated here is the"
                        + " one every confirmed slot gets - poll-confirmed slots included");
    }

    @Test
    @DisplayName("the slot wait carries no ring reset of its own")
    void theSlotWaitCarriesNoRingReset() throws IOException {
        String body = methodBody(read(BACKEND), "private boolean waitForSlot(int slot)");
        assertFalse(body.contains("resetFrameLinearSlot"),
                "the wait's three private reset copies are removed: a second seat is how the"
                        + " poll route came to be skipped, and a wait-side copy must not grow back");
    }

    @Test
    @DisplayName("the backend holds exactly one ring reset call site")
    void exactlyOneResetCallSiteInTheBackend() throws IOException {
        assertEquals(1, occurrences(read(BACKEND), "celtium$resetFrameLinearSlot("),
                "the confirmation tail is the only seat that calls the reset");
    }

    @Test
    @DisplayName("the completion poll confirms through the tail")
    void thePollConfirmsThroughTheTail() throws IOException {
        String body = methodBody(read(BACKEND), "private int celtium$pollCompletedSlots()");
        assertTrue(body.contains("celtium$noteSlotConfirmed(slot, false)"),
                "a fence the poll observes signalled confirms through the tail, ring reset included");
        assertTrue(body.contains("celtium$noteSlotConfirmed(slot, true)"),
                "a lost device observed by the poll is completion-equivalent and confirms the same way");
    }

    @Test
    @DisplayName("the deferred submits' reclaim callback is the tail")
    void theDeferredReclaimConfirmsThroughTheTail() throws IOException {
        String body = methodBody(read(BACKEND), "public void celtium$submitSlotDeferred(");
        assertTrue(body.contains("lost -> celtium$noteSlotConfirmed(slot, lost.booleanValue())"),
                "a batched slot's confirmation runs inside its batch's reclaim callback, which is"
                        + " the tail - so a reclaimed slot's ring is reset by the same seat");
    }

    @Test
    @DisplayName("the device-idle settle confirms every pending slot through the tail")
    void theSettleConfirmsThroughTheTail() throws IOException {
        String body = methodBody(read(BACKEND), "private boolean settleGpu(String reason)");
        assertTrue(body.contains("celtium$noteSlotConfirmed(slot, false)"),
                "vkDeviceWaitIdle confirms every pending slot, and it does so through the tail");
    }
}
