package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The draw-path census contracts of the L-G disposition bundle (DP-19, DP-20, DP-07, DP-08-b), pinned as
 * source contracts in this codebase's idiom for shapes that need a GPU to exercise directly: the indexed
 * decoder counts every arrival before any exit, asks the drawer for its mode rule instead of keeping its
 * own list, names every silent catch, and counts a draw as drawn only when the backend took it; the
 * multi-draw and list replays no longer truncate in silence; the no-op census prints during the run, not
 * only at an exit that may never print.
 */
class DrawPathCensusContractTest {

    private static final String FRONTEND_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlsmFrontend.java";
    private static final String CENSUS_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/GlDrawCensus.java";

    /** DP-19: the arrival is counted at the head, before the empty and refusal exits it used to hide behind. */
    @Test
    void drawElementsCountsEveryArrivalFirst() throws IOException {
        String body = drawElementsBody();
        int calls = body.indexOf("DrawElementCensus.CALLS.incrementAndGet()");
        assertTrue(calls >= 0, "the decoder counts its arrivals");
        int empty = body.indexOf("DrawElementCensus.EMPTY.incrementAndGet()");
        assertTrue(empty > calls, "an empty draw is counted as empty, after the arrival");
        int mode = body.indexOf("VulkanImmediateDrawer.refusalReason(mode)");
        assertTrue(mode > calls, "the mode refusal is recorded, after the arrival");
    }

    /** DP-19: the mode rule is the drawer's, and the old three-mode guard is gone. */
    @Test
    void drawElementsAsksTheDrawerForItsModeRule() throws IOException {
        String source = Files.readString(findRepoFile(FRONTEND_SOURCE));
        assertFalse(source.contains("(mode != 4 && mode != 5 && mode != 7)"),
                "the guard that silently dropped fans, strips and polygons must not come back");
        assertTrue(drawElementsBody().contains("VulkanImmediateDrawer.refusalReason(mode)"),
                "the decoder consults the drawer's acceptance rule");
    }

    /** DP-19: every catch in the decoder names its refusal instead of returning in silence. */
    @Test
    void drawElementsNamesEveryCatch() throws IOException {
        String body = drawElementsBody();
        assertTrue(body.contains("reading the bound vertex buffer's view threw"),
                "the vertex-buffer view catch is named");
        assertTrue(body.contains("reading the VAO attribute buffer's view threw"),
                "the VAO attribute view catch is named");
        assertTrue(body.contains("the layout decode threw"), "the format decode catch is named");
        assertTrue(body.contains("reading the bound element buffer's view threw"),
                "the element-buffer view catch is named");
    }

    /** DP-19: drawn means the backend took the batch; a refused submit is a named refusal. */
    @Test
    void drawElementsCountsDrawnOnlyWhenTheBackendTookTheBatch() throws IOException {
        String body = drawElementsBody();
        assertTrue(body.contains("boolean taken = backend.submitImmediateBatch(null, submitStride, formatId,"),
                "the submit's answer is read, not discarded");
        int taken = body.indexOf("if (taken)");
        int drawn = body.indexOf("DrawElementCensus.DRAWN.incrementAndGet()");
        assertTrue(taken >= 0 && drawn > taken, "the drawn count moves only under the taken branch");
        assertTrue(body.contains("the backend refused the decoded batch"),
                "a refused submit names itself as a refusal");
    }

    /** DP-19: the census line carries the empty count next to drawn and refused. */
    @Test
    void theElementSummaryCarriesTheEmptyCount() throws IOException {
        String census = Files.readString(findRepoFile(CENSUS_SOURCE));
        assertTrue(census.contains("AtomicLong EMPTY"), "the census owns an empty counter");
        assertTrue(census.contains("\" empty=\""), "and the summary line prints it");
    }

    /** DP-20: neither multi-draw form truncates its run. */
    @Test
    void multiDrawDoesNotTruncate() throws IOException {
        String source = Files.readString(findRepoFile(FRONTEND_SOURCE));
        assertFalse(source.contains("Math.min(drawcount, 1024)"),
                "the 1024 sub-draw truncation must not come back");
    }

    /** DP-20: the list replay has no name clamp, and an unreadable name type is counted and logged. */
    @Test
    void callListsHasNoClampAndNamesABadType() throws IOException {
        String source = Files.readString(findRepoFile(FRONTEND_SOURCE));
        assertFalse(source.contains("MAX_CALL_LIST_NAMES"), "the 64 Ki name clamp must not come back");
        int start = source.indexOf("private void callLists(int count, int type, long address)");
        int end = source.indexOf("private void replayDisplayList(int listId)", start);
        assertTrue(start >= 0 && end > start, "the list replay exists");
        assertTrue(source.substring(start, end).contains("CELTIUM_CALL_LISTS_REFUSED"),
                "an unknown name type is counted and logged, not met as a bare return");
    }

    /** DP-07: the no-op census prints at powers of two of its total, from both counting doors. */
    @Test
    void theNoopCensusPrintsPeriodically() throws IOException {
        String source = Files.readString(findRepoFile(FRONTEND_SOURCE));
        assertTrue(source.contains("CELTIUM_NOOP_CENSUS via=periodic"),
                "the periodic line exists alongside the exit line");
        assertEquals(2, countOccurrences(source, "NoopCensusState.TOTAL.incrementAndGet()"),
                "both counting doors (by id, and by name for unknown names) feed the total");
    }

    /** DP-08-b: a colour or stencil blit is counted by shape instead of returning uncounted. */
    @Test
    void unservedBlitsAreCountedByShape() throws IOException {
        String source = Files.readString(findRepoFile(FRONTEND_SOURCE));
        assertTrue(source.contains("CELTIUM_BLIT_UNSERVED"), "the unserved blit line exists");
        assertTrue(source.contains("celtium$noteUnservedBlit(srcX0, srcY0, srcX1, srcY1"),
                "the blit handler counts a colour/stencil mask before the depth-only return");
    }

    /** DP-08: the indexed enable spellings never touch the global bit for a non-zero index. */
    @Test
    void indexedEnablesAreCountedNotApplied() throws IOException {
        String source = Files.readString(findRepoFile(FRONTEND_SOURCE));
        assertEquals(3, countOccurrences(source, "celtium$noteIndexedEnable(fn.name, (int) a0, a1)"),
                "all three indexed spellings (enable, disable, is-enabled) route a non-zero index to the census");
    }

    private static String drawElementsBody() throws IOException {
        String source = Files.readString(findRepoFile(FRONTEND_SOURCE));
        int start = source.indexOf("private void drawElementsNow(int mode, int count, int type, long indices");
        int end = source.indexOf("private void drawArrays(int mode, int first, int count) {", start);
        assertTrue(start >= 0 && end > start, "the indexed decoder exists");
        return source.substring(start, end);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
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
