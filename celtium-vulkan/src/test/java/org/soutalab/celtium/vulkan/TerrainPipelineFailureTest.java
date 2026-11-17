package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A terrain pipeline that fails to create must cost the pass its draws, not the game its process.
 * <p>
 * The leg-11 Demonica run (2026-11-17) died inside the failure handler itself: {@code pipelineFor}'s catch
 * block cached the failure as {@code pipelines.put(pass, null)} in a ConcurrentHashMap, which rejects null
 * values, so the handler's NullPointerException escaped and killed the game - and the creation failure it
 * had just recorded was never seen in either log, leaving the real failure unobserved. These tests drive
 * the failure path with a renderer that has no context, which makes {@code VulkanTerrainPipeline.create}
 * refuse immediately, and pin the handler's contract: the failure is recorded with the pass's name, null
 * is returned (which every caller already handles), nothing throws, and the failure is remembered so a
 * permanently failing pass is neither retried nor re-recorded per frame.
 */
class TerrainPipelineFailureTest {

    private static TerrainRenderPass testPass(String name) {
        return TerrainRenderPass.builder()
                .name(name)
                .pipelineState(new TerrainRenderPass.PipelineState() {
                    @Override public void setup() { }
                    @Override public void clear() { }
                })
                .vertexType(ChunkMeshFormats.COMPACT)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    @Test
    void aFailedCreationIsRecordedAndAnsweredWithNullNotACrash() {
        TerrainRenderer renderer = new TerrainRenderer();
        List<String> records = new ArrayList<>();
        renderer.setDiagnostics(records::add);
        TerrainRenderPass pass = testPass("solid");

        VulkanTerrainPipeline pipeline = renderer.pipelineFor(pass);

        assertNull(pipeline, "a failed creation answers null, which is what every caller already handles");
        assertEquals(1, records.size(), "the failure is recorded exactly once");
        assertTrue(records.get(0).contains("pipeline for solid failed"),
                "the record names the pass: " + records.get(0));
        assertTrue(records.get(0).contains("NullPointerException"),
                "the record names the underlying failure (no context): " + records.get(0));
    }

    @Test
    void aFailedPassIsRememberedNotRetriedOrReRecorded() {
        TerrainRenderer renderer = new TerrainRenderer();
        List<String> records = new ArrayList<>();
        renderer.setDiagnostics(records::add);
        TerrainRenderPass pass = testPass("translucent");

        assertNull(renderer.pipelineFor(pass));
        assertNull(renderer.pipelineFor(pass));
        assertNull(renderer.pipelineFor(pass));

        assertEquals(1, records.size(),
                "a remembered failure answers later calls without another creation attempt or record");
    }

    @Test
    void releasingThePipelinesForgetsTheFailuresToo() {
        TerrainRenderer renderer = new TerrainRenderer();
        List<String> records = new ArrayList<>();
        renderer.setDiagnostics(records::add);
        TerrainRenderPass pass = testPass("cutout");

        assertNull(renderer.pipelineFor(pass));
        renderer.releasePipelines();
        assertNull(renderer.pipelineFor(pass));

        assertEquals(2, records.size(),
                "after release the failure is forgotten, so the next ask retries the creation and records anew");
    }

    @Test
    void noPassStillAnswersNull() {
        TerrainRenderer renderer = new TerrainRenderer();
        assertNull(renderer.pipelineFor(null), "the null-pass guard predates this contract and stays");
    }
}
