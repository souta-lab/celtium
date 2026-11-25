package org.soutalab.celtium.vulkan.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaDirectGraphTest {

    private static VanillaDirectGraph steadyState() {
        return VanillaDirectGraph.build(1280, 720, 37, 126, 97, ImageLayout.PRESENT_SRC);
    }

    @Test
    void passesCompileInFrameOrder() {
        List<PassNode> order = steadyState().graph().executionOrder();
        assertEquals(5, order.size());
        assertEquals("UploadBatch", order.get(0).name());
        assertEquals("TerrainCull", order.get(1).name());
        assertEquals("MainForwardPass", order.get(2).name());
        assertEquals("HiZBuild", order.get(3).name());
        assertEquals("Present", order.get(4).name());
    }

    @Test
    void barrierShapePerBoundary() {
        CompiledGraph graph = steadyState().graph();
        // UploadBatch: both buffers are first uses - nothing to synchronize against.
        assertEquals(0, graph.barriersBefore(0).size());
        // TerrainCull: the Hi-Z pyramid's first-use layout transition only.
        assertEquals(1, graph.barriersBefore(1).size());
        assertEquals(ImageLayout.SHADER_READ_ONLY,
                graph.barriersBefore(1).get(0).newLayout());
        // MainForwardPass: indirect buffer hazard, swapchain into COLOR_ATTACHMENT, depth
        // first-use transition.
        assertEquals(3, graph.barriersBefore(2).size());
        // HiZBuild: depth into read-only, Hi-Z read->write.
        assertEquals(2, graph.barriersBefore(3).size());
        // Present: swapchain into PRESENT_SRC.
        assertEquals(1, graph.barriersBefore(4).size());
        assertEquals(ImageLayout.PRESENT_SRC, graph.barriersBefore(4).get(0).newLayout());
        assertEquals(7, graph.totalBarrierCount());
    }

    @Test
    void theMainPassWritesTheSwapchainDirectly() {
        VanillaDirectGraph vanilla = steadyState();
        PassNode main = vanilla.graph().executionOrder().get(2);
        ResourceAccess access = main.accessTo(vanilla.swapchainColor());
        assertEquals(AccessType.COLOR_ATTACHMENT_WRITE, access.type());
    }

    @Test
    void executorEmitsBarriersBeforeEachPassAndRecordsInOrder() {
        VanillaDirectGraph vanilla = VanillaDirectGraph.build(64, 64, 37, 126, 97,
                ImageLayout.UNDEFINED);
        final List<String> events = new ArrayList<String>();
        FrameGraphBuilder builder = new FrameGraphBuilder();
        // Rebuild the same shape with recorders attached, through the public builder, to watch
        // the executor's sequencing (the vanilla factory intentionally carries no recorders).
        GraphResource target = GraphResource.importedImage("t", 64, 64, 37);
        CompiledGraph graph = builder
                .addPass(PassNode.builder("a", PassNode.Kind.TRANSFER)
                        .access(target, AccessType.TRANSFER_DST)
                        .recorder(ctx -> events.add("record:a"))
                        .build())
                .addPass(PassNode.builder("b", PassNode.Kind.GRAPHICS)
                        .access(target, AccessType.SHADER_READ)
                        .recorder(ctx -> events.add("record:b"))
                        .build())
                .build();
        GraphExecutor.execute(graph, null, (barriers, ctx) -> {
            if (!barriers.isEmpty()) {
                events.add("barriers:" + barriers.size());
            }
        });
        assertEquals(4, events.size());
        assertEquals("barriers:1", events.get(0));
        assertEquals("record:a", events.get(1));
        assertEquals("barriers:1", events.get(2));
        assertEquals("record:b", events.get(3));
        assertTrue(vanilla.graph().totalBarrierCount() > 0);
    }
}
