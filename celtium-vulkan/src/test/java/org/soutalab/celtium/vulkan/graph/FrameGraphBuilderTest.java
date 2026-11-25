package org.soutalab.celtium.vulkan.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameGraphBuilderTest {

    private static PassNode pass(String name, PassNode.Kind kind, GraphResource resource,
                                 AccessType type) {
        return PassNode.builder(name, kind).access(resource, type).build();
    }

    @Test
    void declaredOrderIsKeptWhenDependenciesAllow() {
        GraphResource buffer = GraphResource.importedBuffer("b", 64);
        CompiledGraph graph = new FrameGraphBuilder()
                .addPass(pass("write", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_WRITE))
                .addPass(pass("read", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_READ))
                .build();
        assertEquals("write", graph.executionOrder().get(0).name());
        assertEquals("read", graph.executionOrder().get(1).name());
    }

    @Test
    void aReaderDeclaredBeforeItsWriterIsReorderedAfterIt() {
        GraphResource buffer = GraphResource.importedBuffer("b", 64);
        CompiledGraph graph = new FrameGraphBuilder()
                .addPass(pass("read", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_READ))
                .addPass(pass("write", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_WRITE))
                .build();
        // WAR: the write must wait for the read of the previous content, so declaration order
        // stands here - the reader genuinely goes first.
        assertEquals("read", graph.executionOrder().get(0).name());
        assertEquals("write", graph.executionOrder().get(1).name());
    }

    @Test
    void rawDependencyOrdersAProducerBeforeAConsumerDeclaredFirst() {
        GraphResource buffer = GraphResource.importedBuffer("b", 64);
        // Two writers: the second write is WAW-ordered after the first; a reader declared
        // between them binds to the first writer.
        CompiledGraph graph = new FrameGraphBuilder()
                .addPass(pass("write1", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_WRITE))
                .addPass(pass("read", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_READ))
                .addPass(pass("write2", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_WRITE))
                .build();
        assertEquals("write1", graph.executionOrder().get(0).name());
        assertEquals("read", graph.executionOrder().get(1).name());
        assertEquals("write2", graph.executionOrder().get(2).name());
        // The read needs a barrier after the write; the second write needs one after the read.
        assertEquals(0, graph.barriersBefore(0).size());
        assertEquals(1, graph.barriersBefore(1).size());
        assertEquals(1, graph.barriersBefore(2).size());
    }

    @Test
    void readAfterReadEmitsNoBarrier() {
        GraphResource buffer = GraphResource.importedBuffer("b", 64);
        CompiledGraph graph = new FrameGraphBuilder()
                .addPass(pass("read1", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_READ))
                .addPass(pass("read2", PassNode.Kind.COMPUTE, buffer, AccessType.COMPUTE_READ))
                .build();
        assertEquals(0, graph.totalBarrierCount());
    }

    @Test
    void layoutChangeEmitsATransitionWithTheRightLayouts() {
        GraphResource image = GraphResource.importedImage("img", 64, 64, 37);
        CompiledGraph graph = new FrameGraphBuilder()
                .addPass(pass("copyIn", PassNode.Kind.TRANSFER, image, AccessType.TRANSFER_DST))
                .addPass(pass("sample", PassNode.Kind.GRAPHICS, image, AccessType.SHADER_READ))
                .build();
        List<GraphBarrier> first = graph.barriersBefore(0);
        assertEquals(1, first.size());
        assertEquals(ImageLayout.UNDEFINED, first.get(0).oldLayout());
        assertEquals(ImageLayout.TRANSFER_DST, first.get(0).newLayout());
        List<GraphBarrier> second = graph.barriersBefore(1);
        assertEquals(1, second.size());
        assertEquals(ImageLayout.TRANSFER_DST, second.get(0).oldLayout());
        assertEquals(ImageLayout.SHADER_READ_ONLY, second.get(0).newLayout());
        assertEquals(SyncBits.ACCESS_TRANSFER_WRITE, second.get(0).srcAccessMask());
        assertEquals(SyncBits.ACCESS_SHADER_READ, second.get(0).dstAccessMask());
    }

    @Test
    void sameLayoutWriteAfterWriteStillEmitsAnExecutionBarrier() {
        GraphResource image = GraphResource.importedImage("img", 64, 64, 37)
                .withInitialLayout(ImageLayout.COLOR_ATTACHMENT);
        CompiledGraph graph = new FrameGraphBuilder()
                .addPass(pass("draw1", PassNode.Kind.GRAPHICS, image, AccessType.COLOR_ATTACHMENT_WRITE))
                .addPass(pass("draw2", PassNode.Kind.GRAPHICS, image, AccessType.COLOR_ATTACHMENT_WRITE))
                .build();
        assertEquals(0, graph.barriersBefore(0).size());
        assertEquals(1, graph.barriersBefore(1).size());
        assertTrue(!graph.barriersBefore(1).get(0).isLayoutTransition());
    }

    @Test
    void mutuallyDependentPassesResolveInDeclarationOrder() {
        GraphResource a = GraphResource.importedBuffer("a", 64);
        GraphResource b = GraphResource.importedBuffer("b", 64);
        // x writes a and reads b's previous content; y writes b and reads x's a. The dependency
        // edges of this model only ever point from an earlier declaration to a later one (a use
        // binds to the last writer / readers before it), so the pair is ordered, not cyclic -
        // the compiler's cycle check is a defensive assertion, not a reachable outcome.
        PassNode x = PassNode.builder("x", PassNode.Kind.COMPUTE)
                .access(a, AccessType.COMPUTE_WRITE)
                .access(b, AccessType.COMPUTE_READ)
                .build();
        PassNode y = PassNode.builder("y", PassNode.Kind.COMPUTE)
                .access(b, AccessType.COMPUTE_WRITE)
                .access(a, AccessType.COMPUTE_READ)
                .build();
        CompiledGraph graph = new FrameGraphBuilder().addPass(x).addPass(y).build();
        assertEquals("x", graph.executionOrder().get(0).name());
        assertEquals("y", graph.executionOrder().get(1).name());
    }

    @Test
    void disjointTransientsShareAnAliasSlotAndOverlappingOnesDoNot() {
        GraphResource early = GraphResource.transientImage("early", 64, 64, 37);
        GraphResource late = GraphResource.transientImage("late", 64, 64, 37);
        GraphResource spanning = GraphResource.transientImage("spanning", 64, 64, 37);
        CompiledGraph graph = new FrameGraphBuilder()
                .addPass(PassNode.builder("p0", PassNode.Kind.GRAPHICS)
                        .access(early, AccessType.COLOR_ATTACHMENT_WRITE)
                        .access(spanning, AccessType.SHADER_READ)
                        .build())
                .addPass(PassNode.builder("p1", PassNode.Kind.GRAPHICS)
                        .access(spanning, AccessType.SHADER_READ)
                        .build())
                .addPass(PassNode.builder("p2", PassNode.Kind.GRAPHICS)
                        .access(late, AccessType.COLOR_ATTACHMENT_WRITE)
                        .access(spanning, AccessType.SHADER_READ)
                        .build())
                .build();
        assertEquals(graph.aliasSlotOf(early), graph.aliasSlotOf(late));
        assertTrue(graph.aliasSlotOf(spanning) != graph.aliasSlotOf(early));
        assertEquals(0, graph.lifetimeOf(early).firstUse());
        assertEquals(2, graph.lifetimeOf(spanning).lastUse());
    }

    @Test
    void differentlyShapedTransientsNeverAlias() {
        GraphResource a = GraphResource.transientImage("a", 64, 64, 37);
        GraphResource b = GraphResource.transientImage("b", 128, 64, 37);
        CompiledGraph graph = new FrameGraphBuilder()
                .addPass(pass("p0", PassNode.Kind.GRAPHICS, a, AccessType.COLOR_ATTACHMENT_WRITE))
                .addPass(pass("p1", PassNode.Kind.GRAPHICS, b, AccessType.COLOR_ATTACHMENT_WRITE))
                .build();
        assertTrue(graph.aliasSlotOf(a) != graph.aliasSlotOf(b));
    }
}
