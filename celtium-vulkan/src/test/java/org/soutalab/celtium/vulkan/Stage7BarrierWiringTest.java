package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 5 stage 7 (S7-d), the barrier contract's final form, pinned in the house
 * source-contract style. The contract, as the shipped code already implements it and as
 * this stage fixes it: a queue-position operation (a framebuffer or texture-image copy, a
 * blit, a clear, a framebuffer bind, a readback) is confirmed by first draining the queued
 * immediate work through the backend's ordering funnel
 * ({@code celtium$flushPendingForGlDrawCause}, cause-stamped for the window census) and
 * then executing in a fresh command buffer of its own, or - for the CPU-side copy family -
 * by filing into the pending-raw-write stream whose application is gated ahead of any
 * batch that would sample it. Batch segments are confirmed at the same funnel's far side
 * by {@code celtium$flushBatchSegment} and its {@code BatchDrawSink}. The barrier targets
 * are the adjudicated set: {@code SEM_COPY_TEX_IMAGE} (52), {@code SEM_COPY_TEX_SUB_IMAGE}
 * (53, included by the stage-7 entry adjudication), {@code SEM_COPY_IMAGE_SUB_DATA} (79),
 * {@code SEM_BLIT_FRAMEBUFFER} (78), {@code SEM_READ_PIXELS} (70),
 * {@code SEM_BIND_FRAMEBUFFER} (28) and the clear family (29/30/31); the copy family's
 * static transformer entrances funnel into the same instance handlers the dispatch cases
 * call, so the coverage does not depend on which door a mod's call arrives through. The
 * pass boundary's owner is named in the stage-7 landing record: the backend's GL-draw
 * window machinery, whose operations these tests pin.
 */
class Stage7BarrierWiringTest {

    private static final String ROOT = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";
    private static final String FRONTEND = ROOT + "gl/GlsmFrontend.java";
    private static final String BACKEND = ROOT + "VulkanTerrainGpuBackend.java";
    private static final String COPY_SIDE = ROOT + "CopyReadSide.java";
    private static final String COPY_SERVICE = ROOT + "TextureCopyService.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    /** The text from the first occurrence of {@code start} to the next occurrence of {@code end}. */
    private static String segment(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, "segment start present: " + start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, "segment end present after start: " + end);
        return source.substring(from, to);
    }

    @Test
    @DisplayName("the dispatch routes every adjudicated barrier target to its handler")
    void dispatchRoutesEveryBarrierTarget() throws IOException {
        String frontend = read(FRONTEND);
        assertTrue(segment(frontend, "case SEM_COPY_TEX_IMAGE:", "case SEM_COPY_TEX_SUB_IMAGE:")
                        .contains("celtium$noteTextureCopy("),
                "copyTexImage is served through the texture-copy handler");
        assertTrue(segment(frontend, "case SEM_COPY_TEX_SUB_IMAGE:", "case SEM_COPY_IMAGE_SUB_DATA:")
                        .contains("celtium$noteTextureCopy("),
                "copyTexSubImage (53, in the target set by adjudication) takes the same handler");
        assertTrue(segment(frontend, "case SEM_COPY_IMAGE_SUB_DATA:", "case SEM_BLIT_FRAMEBUFFER:")
                        .contains("copyImageSubData("),
                "copyImageSubData takes the CPU-side copy handler");
        assertTrue(segment(frontend, "case SEM_BLIT_FRAMEBUFFER:", "case SEM_READ_PIXELS:")
                        .contains("celtium$noteBlitFramebuffer("),
                "blitFramebuffer takes the blit handler");
        assertTrue(segment(frontend, "case SEM_READ_PIXELS:", "case SEM_TEX_PARAM_I:")
                        .contains("serveReadPixels("),
                "readPixels takes the readback handler");
        assertTrue(segment(frontend, "case SEM_BIND_FRAMEBUFFER:", "case SEM_CLEAR:")
                        .contains("bindFramebuffer((int) a0, (int) a1);"),
                "bindFramebuffer takes the bind handler");
        assertTrue(segment(frontend, "case SEM_CLEAR:", "case SEM_CLEAR_COLOR:")
                        .contains("this.recordClear((int) a0);"),
                "clear takes the clear record");
        assertTrue(segment(frontend, "case SEM_CLEAR_COLOR:", "case SEM_CLEAR_DEPTH:")
                        .contains("this.recordClearColor("),
                "clearColor takes the clear-colour record");
        assertTrue(segment(frontend, "case SEM_CLEAR_DEPTH:", "case SEM_BUFFER_SUBDATA:")
                        .contains("this.recordClearDepth("),
                "clearDepth takes the clear-depth record");
    }

    @Test
    @DisplayName("the copy family's static entrances funnel into the same handlers")
    void staticEntrancesFunnelToTheSameHandlers() throws IOException {
        String frontend = read(FRONTEND);
        assertTrue(segment(frontend, "public static void celtium$copyTexSubImage2D",
                        "public static void celtium$copyImageSubData")
                        .contains("celtium$noteTextureCopy("),
                "the copyTexSubImage2D transformer entrance reaches the texture-copy handler");
        assertTrue(segment(frontend, "public static void celtium$copyImageSubData",
                        "public static void celtium$copyTexImage2D")
                        .contains("stub.copyImageSubData("),
                "the copyImageSubData transformer entrance reaches the CPU-side copy handler");
        assertTrue(segment(frontend, "public static void celtium$copyTexImage2D",
                        "private static boolean isDepthInternalFormat")
                        .contains("celtium$noteTextureCopy("),
                "the copyTexImage2D transformer entrance reaches the texture-copy handler");
        assertTrue(segment(frontend, "public static void celtium$bindFramebuffer",
                        "private void bindFramebuffer(int target, int name)")
                        .contains("stub.bindFramebuffer(target, name);"),
                "the bindFramebuffer transformer entrance reaches the bind handler");
        assertTrue(segment(frontend, "public static void celtium$readPixels",
                        "private void serveReadPixels")
                        .contains("stub.serveReadPixels("),
                "the readPixels transformer entrance reaches the readback handler");
        assertTrue(segment(frontend, "public static void celtium$getTexImage",
                        "public static void celtium$readPixels")
                        .contains("vulkan.celtium$getTexImage("),
                "the getTexImage transformer entrance reaches the backend's readback entry");
    }

    @Test
    @DisplayName("the copy side drains queued work through the funnel before it executes")
    void copySideFlushesBeforeExecuting() throws IOException {
        String copySide = read(COPY_SIDE);
        assertTrue(occurrences(copySide, "GlDrawWindowCensus.CAUSE_COPY_ORDER") >= 2,
                "both the depth copy and the copy service's host flush stamp the copy-order cause");
        assertTrue(copySide.contains("celtium$flushPendingForGlDrawCause(GlDrawWindowCensus.CAUSE_COPY_ORDER)"),
                "the depth copy drains through the ordering funnel before recording its copy");
    }

    @Test
    @DisplayName("the texture copy service orders every serve through its host contract")
    void copyServiceOrdersThroughItsHost() throws IOException {
        String service = read(COPY_SERVICE);
        assertTrue(service.contains("void flushPendingBeforeGlDraw();"),
                "the host contract names the ordering flush");
        assertTrue(occurrences(service, "this.host.flushPendingBeforeGlDraw();") >= 2,
                "the texture-copy and readback serves both flush before executing");
    }

    @Test
    @DisplayName("the ordering funnel reaches the window flush")
    void funnelReachesTheWindowFlush() throws IOException {
        String backend = read(BACKEND);
        assertTrue(segment(backend, "void celtium$flushPendingForGlDrawCause(int cause)",
                        "void flushPendingBeforeGlDraw() {")
                        .contains("flushPendingBeforeGlDraw();"),
                "the cause-stamping funnel delegates to the window flush");
    }

    @Test
    @DisplayName("batch segments are confirmed at the funnel's far side")
    void batchSegmentsAreConfirmed() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("private boolean celtium$flushBatchSegment()"),
                "the segment confirmation receiver exists");
        assertTrue(backend.contains("private final class BatchDrawSink implements"),
                "the batch draw sink is the segment receiver's partner");
        assertTrue(occurrences(backend, "celtium$flushBatchSegment();") >= 3,
                "the drain confirms segments at its named points");
    }

    @Test
    @DisplayName("clears take an ordered position against queued work")
    void clearsTakeAnOrderedPosition() throws IOException {
        String frontend = read(FRONTEND);
        String backend = read(BACKEND);
        assertTrue(segment(frontend, "private void recordClear(int mask)",
                        "public static int celtium$effectiveClearMask(int mask)")
                        .contains("vulkan.celtium$gameClear(mask, this.drawFbo);"),
                "a non-main clear is handed to the backend at its queue position");
        assertTrue(backend.contains("public void flushBeforeClear(int mask)"),
                "the backend's clear-ordering flush exists for the main route");
    }

    @Test
    @DisplayName("framebuffer binds stamp their ordering cause at the funnel")
    void bindsStampTheOrderingCause() throws IOException {
        String backend = read(BACKEND);
        String bindFlush = segment(backend, "celtium$flushPendingForGlDrawCause(this.flushEntryHint",
                "this.pendingGlDrawLeavingMainToDefault = previousLeavingMainToDefault;");
        assertTrue(bindFlush.contains("GlDrawWindowCensus.CAUSE_BIND_FRAMEBUFFER_RAW")
                        && bindFlush.contains("GlDrawWindowCensus.CAUSE_BIND_FRAMEBUFFER_MAIN")
                        && bindFlush.contains("GlDrawWindowCensus.CAUSE_BIND_FRAMEBUFFER_TARGET"),
                "the bind route stamps raw, main and target causes at the one funnel call");
    }

    private static Path findRepoFile(String repoRelative) {
        return findRepoRoot().resolve(repoRelative);
    }

    private static Path findRepoRoot() {
        Path direct = Paths.get("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java");
        if (Files.exists(direct)) {
            return Paths.get("");
        }
        return Paths.get("..");
    }
}
