package org.embeddedt.embeddium.impl.render.terrain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * QUEUE 635: the per-frame rebuild dedup set in {@code SimpleWorldRenderer}
 * ({@code celtium$scheduledSections}, a plain fastutil {@code LongOpenHashSet}) was mutated without
 * any thread guard, while its entry point accepts off-thread callers.
 * <p>
 * The chain is {@code RenderGlobalMixin.markBlocksForUpdate} (the vanilla overwrite) ->
 * {@code scheduleRebuildForBlockArea} -> {@code scheduleRebuildForChunks} ->
 * {@code scheduleRebuildForChunk}, and one level down {@code RenderSectionManager.scheduleRebuild}
 * explicitly branches on the render thread and defers off-thread callers - so off-thread arrivals
 * are part of the design, and the unguarded {@code add} raced the render thread's {@code add} and
 * the {@code clear} in {@code setupTerrain} on a non-thread-safe hash table (corruption, AIOOBE,
 * infinite loops are the known failure shapes of that race).
 * <p>
 * The fix confines the set to the render thread: the dedup add only runs when
 * {@code RenderSectionManager.isOnRenderThread()} holds, and off-thread requests go straight to
 * {@code scheduleRebuild}, whose pending-update merge collapses duplicates when the deferred task
 * runs on the render thread. A live behavioral test would need a full RenderSectionManager, whose
 * constructor builds the Vulkan terrain backend, so - as with the other source-contract tests in
 * this codebase - the confinement is pinned structurally here: if the guard is removed, if a
 * second mutation site appears, or if the accessor stops reporting the manager's own render
 * thread, this test fails.
 */
class ScheduleRebuildDedupThreadContractTest {
    private static final String RENDERER_SOURCE =
            "common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java";
    private static final String MANAGER_SOURCE =
            "common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.java";

    private static String readSource(String repoRelative) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path fromRoot = dir.resolve(repoRelative);
            if (Files.isRegularFile(fromRoot)) {
                return Files.readString(fromRoot);
            }
            // When the test working directory is the common module itself, the path is module-relative.
            Path moduleRelative = dir.resolve(repoRelative.substring("common/".length()));
            if (Files.isRegularFile(moduleRelative)) {
                return Files.readString(moduleRelative);
            }
            dir = dir.getParent();
        }
        return fail("source not found from " + System.getProperty("user.dir") + ": " + repoRelative);
    }

    /** Extracts a method body by brace matching from the first '{' at or after {@code fromIndex}. */
    private static String methodBody(String source, int fromIndex) {
        int open = source.indexOf('{', fromIndex);
        assertTrue(open >= 0, "method opening brace not found");
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        return fail("method body never closes");
    }

    @Test
    void theDedupAddIsGatedOnTheRenderThread() throws IOException {
        String source = readSource(RENDERER_SOURCE);
        int methodStart = source.indexOf("public void scheduleRebuildForChunk(");
        assertTrue(methodStart >= 0, "scheduleRebuildForChunk not found");
        String body = methodBody(source, methodStart);
        assertTrue(body.length() > 200, "extracted body is suspiciously small - the test is not seeing the method");

        int guard = body.indexOf("this.renderSectionManager.isOnRenderThread()");
        int add = body.indexOf("celtium$scheduledSections.add(");
        assertTrue(guard >= 0, "scheduleRebuildForChunk no longer gates the dedup on isOnRenderThread()");
        assertTrue(add > guard, "the dedup add must sit behind the render-thread gate, not before it");
        // The gate must be the condition of the block that performs the add, in the full form that
        // also keeps the important-request bypass and the not-yet-initialised manager intact.
        String flat = body.replaceAll("\\s+", " ");
        assertTrue(flat.contains("if (!important && this.renderSectionManager != null "
                        + "&& this.renderSectionManager.isOnRenderThread()) {"),
                "the dedup block must be conditioned on !important, a live manager, and the render thread");
    }

    @Test
    void theSetHasExactlyTwoMutationSitesAndTheClearIsInSetupTerrain() throws IOException {
        String source = readSource(RENDERER_SOURCE);
        Matcher calls = Pattern.compile("celtium\\$scheduledSections\\.(\\w+)\\(").matcher(source);
        Set<String> seen = new HashSet<>();
        int mutations = 0;
        while (calls.find()) {
            seen.add(calls.group(1));
            mutations++;
        }
        // Exactly the gated add and the setupTerrain clear. Any new mutation site (remove, addAll,
        // a second add, ...) reopens the confinement question and must update this contract on purpose.
        assertEquals(Set.of("add", "clear"), seen, "unexpected mutation of the dedup set: " + seen);
        assertEquals(2, mutations, "expected exactly one add and one clear of the dedup set");

        int setupStart = source.indexOf("public void setupTerrain(");
        assertTrue(setupStart >= 0, "setupTerrain not found");
        String setupBody = methodBody(source, setupStart);
        assertTrue(setupBody.contains("celtium$scheduledSections.clear();"),
                "the frame clear of the dedup set must stay inside setupTerrain (a render-thread entry point)");
    }

    @Test
    void theAccessorReportsTheManagersOwnRenderThread() throws IOException {
        String source = readSource(MANAGER_SOURCE);
        int methodStart = source.indexOf("public boolean isOnRenderThread()");
        assertTrue(methodStart >= 0, "RenderSectionManager.isOnRenderThread() not found");
        String body = methodBody(source, methodStart);
        assertTrue(body.contains("Thread.currentThread() == this.renderThread"),
                "isOnRenderThread() must report the manager's own renderThread field, not a second thread fact");
    }
}
