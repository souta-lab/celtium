package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The build-and-close placement behind audit-vulkan-core I2, pinned as a source contract because the failure
 * needs a re-entering sink to reproduce at runtime.
 * <p>
 * {@code celtium$flushPendingFontBatch} offers the retained runs before the open one, and offering submits
 * through the sink, whose own entry flush re-enters this method (the leg-5 recursion path). The open run used
 * to be built and marked inactive only after that offer: a nested flush found it still active, offered it at
 * whatever bytes it then held, and the outer call offered and counted it a second time. The method's own
 * javadoc has always stated the run is marked inactive before the submission; these assertions pin the code
 * to it - the run is built, deactivated and its counters zeroed before the retained offer, in the flush body.
 */
class FontBatchFlushOrderTest {

    private static String fontBatchSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java"));
    }

    /** The flush body: from its declaration to the offer helper that follows it. */
    private static String flushBody(String source) {
        int method = source.indexOf("public void celtium$flushPendingFontBatch");
        assertTrue(method >= 0, "the flush body is present");
        int end = source.indexOf("private boolean offer(", method);
        assertTrue(end > method, "the offer helper follows the flush body");
        return source.substring(method, end);
    }

    @Test
    void theOpenRunIsBuiltAndClosedBeforeTheRetainedOffer() throws IOException {
        String body = flushBody(fontBatchSource());
        int built = body.indexOf("Run run = new Run(");
        int closed = body.indexOf("this.pendingActive = false;");
        int retainedOffer = body.lastIndexOf("offerRetained(view, target);");
        assertTrue(built >= 0, "the open run is built in the flush body");
        assertTrue(closed > built, "the run is marked inactive after it is built");
        assertTrue(retainedOffer > closed,
                "the retained runs are offered only after the open run is built and closed");
        assertTrue(body.indexOf("this.pendingVertexCount = 0;", closed) >= 0
                        && body.indexOf("this.pendingVertexCount = 0;", closed) < retainedOffer,
                "the pending counters are zeroed before the retained offer, with the close");
    }

    @Test
    void theFlushBodyClosesTheRunExactlyOnce() throws IOException {
        String body = flushBody(fontBatchSource());
        assertEquals(1, countOccurrences(body, "this.pendingActive = false;"),
                "one close site in the flush body; the append path owns reopening");
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
