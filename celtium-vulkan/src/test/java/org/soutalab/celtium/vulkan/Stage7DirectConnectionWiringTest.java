package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 5 stage 7 (S7-c), the direct connection of the frontend's replay streams, pinned
 * in the house source-contract style. The identification this pins, made behaviourally on
 * the stage-7 input tree (the frontend holds no {@code ImmediateGroup} reference, so the
 * old direct glue can only be named by where its streams land): every geometry stream the
 * frontend originates - the {@code glBegin}/{@code glEnd} capture, {@code drawArrays} and
 * the indexed draws, and the M14 display-list replay in both its forms (compiled legacy
 * runs and compiled client arrays) - lands on the backend's one offer entry
 * {@code submitImmediateBatch} (or, for retained geometry, its sibling
 * {@code submitRetainedGeometry}, which delegates to the same append machinery), and from
 * there into group capture, the batcher's segments and the records pipelines. The frontend
 * records no Vulkan command itself.
 * <p>
 * Stage 8 (S8-g) removed the escape hatch this test used to name - the
 * {@code CeltiumBackends.current()} + {@code instanceof} arrival at those submit sites.
 * The five sites now resolve the backend through the holder's submit-role accessor
 * {@code CeltiumBackends.submitBackend()} and offer through the {@code CeltiumBackend}
 * interface alone: the retained-geometry branch is the backend's
 * {@code acceptsRetainedGeometry()} capability rather than its concrete type, and the two
 * state queries the sites used to reach through a cast ({@code celtium$texture2dEnabled},
 * {@code fixedFunctionLightingEnabled}) are interface reads. This test fails if a sixth,
 * divergent submit site ever appears beside the five, or if a submit site goes back to
 * discovering its receiver by type.
 */
class Stage7DirectConnectionWiringTest {

    private static final String ROOT = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";
    private static final String FRONTEND = ROOT + "gl/GlsmFrontend.java";
    private static final String BACKEND = ROOT + "VulkanTerrainGpuBackend.java";

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

    @Test
    @DisplayName("the frontend records no Vulkan command itself")
    void frontendRecordsNoVulkanCommands() throws IOException {
        assertEquals(0, occurrences(read(FRONTEND), "vkCmd"),
                "every draw the frontend originates is offered to the backend, never recorded here");
    }

    @Test
    @DisplayName("the frontend's five submit sites all land on the backend's offer entry")
    void frontendSubmitsOnlyThroughTheOfferEntry() throws IOException {
        String frontend = read(FRONTEND);
        assertEquals(5, occurrences(frontend, "submitImmediateBatch("),
                "exactly the five identified submit sites; a sixth is a divergent draw path");
        assertTrue(frontend.contains("backend.submitImmediateBatch(null, stride, layout, address, count, drawMode)"),
                "the glEnd sink offers the captured run through the offer entry");
        assertTrue(frontend.contains(
                        "backend.submitImmediateBatch(null, submission.stride, submission.format, submission.address,"),
                "a compiled legacy run's replay offers through the same entry");
        assertTrue(frontend.contains("backend.submitImmediateBatch(null, submitStride, formatId,"),
                "drawArrays offers through the same entry");
        assertTrue(frontend.contains("backend.submitImmediateBatch(null, asItem ? ITEM_STRIDE : decodedStride,"),
                "the indexed draws offer through the same entry");
        assertTrue(frontend.contains(
                        "backend.submitImmediateBatch(null, lit ? ITEM_STRIDE : (int) c[1], lit ? ITEM_FORMAT : (int) c[2],"),
                "compiled client arrays on a backend without retained geometry offer through the same entry");
    }

    @Test
    @DisplayName("the five submit sites resolve through the submit-role accessor, never by concrete type")
    void submitSitesUseTheDirectConnection() throws IOException {
        String frontend = read(FRONTEND);
        assertEquals(5, occurrences(frontend, "CeltiumBackends.submitBackend()"),
                "one submit-role resolution per submit site; the general door is for the other seats");
        assertTrue(frontend.contains("backend.acceptsRetainedGeometry()"),
                "compiled client arrays branch on the retained-geometry capability, not the concrete type");
        assertTrue(frontend.contains("backend.submitRetainedGeometry(geometry, (int) c[4])"),
                "retained geometry is offered through the interface's retained entry");
        assertTrue(frontend.contains("backend.texture2dEnabled()"),
                "the legacy-run replay reads the texturing state through the interface");
        assertTrue(frontend.contains("backend.fixedFunctionLightingEnabled()"),
                "the lighting query the submit sites share is an interface read");
        assertTrue(!frontend.contains("vulkan.submitImmediateBatch"),
                "no submit site addresses the concrete backend by name any more");
        assertTrue(!frontend.contains(
                        "((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) backend).submitRetainedGeometry"),
                "no submit site casts to the concrete backend for the retained offer");
        assertTrue(!frontend.contains(
                        "((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) backend).celtium$texture2dEnabled()"),
                "no submit site casts to the concrete backend for the texturing state");
    }

    @Test
    @DisplayName("the list executor routes compiled runs, compiled arrays and recorded commands")
    void listReplayRoutesEveryEntryForm() throws IOException {
        String frontend = read(FRONTEND);
        assertTrue(frontend.contains("replayLegacyRun((CompiledLegacyRun) call[1]);"),
                "a compiled legacy run replays through replayLegacyRun, in list order");
        assertTrue(frontend.contains("if (command.semantics == SEM_COMPILED_ARRAYS) replayCompiledArrays((long[]) call[1]);"),
                "compiled client arrays replay through their own route");
        assertTrue(frontend.contains("else dispatchArgs(command, (long[]) call[1]);"),
                "every other recorded command re-enters the dispatch, so its draws land on the same entry");
    }

    @Test
    @DisplayName("retained geometry converges on the same append machinery")
    void retainedGeometryConverges() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("return submitGeometryBatch(null, geometry.stride(), geometry.formatId(), geometry.address(),"),
                "submitRetainedGeometry delegates into the shared append machinery, not a second draw path");
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
