package org.soutalab.celtium.vulkan.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure rules of the shader pack DAG: the canonical graph a profile produces, and the refusals a
 * malformed graph gets at build time. Pinned without a device because a wrong graph here is silent in
 * game - a stage reading a target nobody wrote would sample whatever the allocator aliased there.
 */
class ShaderpackDagTest {

    @Test
    void theStandardGraphCullsBothCamerasAndEndsAtTheSwapchain() {
        ShaderpackDag dag = ShaderpackDag.standard(true, 4, 2, 1);
        assertEquals(List.of(ShaderpackStage.CullCamera.SHADOW, ShaderpackStage.CullCamera.MAIN),
                dag.cullPlan(), "shadow and main culls are both planned, shadow first");
        List<ShaderpackDag.Node> nodes = dag.nodes();
        ShaderpackDag.Node last = nodes.get(nodes.size() - 1);
        assertEquals(ShaderpackStage.FINAL, last.stage());
        assertEquals(List.of(ShaderpackDag.SWAPCHAIN), last.writes(),
                "the final node writes the swapchain directly");
        ShaderpackDag.Node gbuffers = nodes.stream()
                .filter(node -> node.stage() == ShaderpackStage.GBUFFERS
                        && node.kind() == ShaderpackDag.NodeKind.TERRAIN)
                .findFirst().orElseThrow();
        assertEquals(List.of("colortex0", "colortex1", "colortex2", "colortex3",
                ShaderpackDag.DEPTHTEX0), gbuffers.writes());
        assertTrue(gbuffers.reads().contains("shadowtex0"),
                "the gbuffers terrain shades with the shadow map");
    }

    @Test
    void aPackWithoutShadowsPlansOnlyTheMainCull() {
        ShaderpackDag dag = ShaderpackDag.standard(false, 1, 0, 0);
        assertEquals(List.of(ShaderpackStage.CullCamera.MAIN), dag.cullPlan());
        assertTrue(dag.nodes().stream().noneMatch(node -> node.stage() == ShaderpackStage.SHADOW));
    }

    @Test
    void aReadOfAnUnwrittenAttachmentIsRefused() {
        ShaderpackDag.Builder builder = new ShaderpackDag.Builder();
        builder.node("final", ShaderpackStage.FINAL, ShaderpackDag.NodeKind.FULLSCREEN,
                List.of("colortex0"), List.of(ShaderpackDag.SWAPCHAIN));
        IllegalStateException error = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(error.getMessage().contains("colortex0"), error.getMessage());
    }

    @Test
    void anImportedAttachmentMayBeRead() {
        ShaderpackDag dag = new ShaderpackDag.Builder()
                .importing("colortex0")
                .node("final", ShaderpackStage.FINAL, ShaderpackDag.NodeKind.FULLSCREEN,
                        List.of("colortex0"), List.of(ShaderpackDag.SWAPCHAIN))
                .build();
        assertEquals(1, dag.nodes().size());
    }

    @Test
    void stagesNeverRunBackwards() {
        ShaderpackDag.Builder builder = new ShaderpackDag.Builder();
        builder.node("final", ShaderpackStage.FINAL, ShaderpackDag.NodeKind.FULLSCREEN,
                List.of(), List.of(ShaderpackDag.SWAPCHAIN));
        builder.node("late-gbuffers", ShaderpackStage.GBUFFERS, ShaderpackDag.NodeKind.TERRAIN,
                List.of(), List.of("colortex0"));
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void theSwapchainIsWrittenExactlyOnce() {
        ShaderpackDag.Builder builder = new ShaderpackDag.Builder();
        builder.node("composite0", ShaderpackStage.COMPOSITE, ShaderpackDag.NodeKind.FULLSCREEN,
                List.of(), List.of("colortex0"));
        IllegalStateException error = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(error.getMessage().contains("exactly once"), error.getMessage());
    }

    @Test
    void targetCountsAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> ShaderpackDag.standard(false, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShaderpackDag.standard(false, 9, 0, 0));
    }
}
