package org.soutalab.celtium.vulkan.compat;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.graph.GraphBarrier;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chain graph's contract: chain order survives compilation, the executor invokes the sink
 * once per pass in that order, and the compiler synthesizes the write-to-read barrier between
 * a pass that writes an attachment and the next pass that samples it. Pinned without a device
 * because a wrong order here is a wrong picture in game with every counter green.
 */
class ChainGraphTest {

    private static PostChainPass pass(String program, boolean inMain, int inId,
            boolean outMain, int outId) {
        PostChainPass pass = new PostChainPass();
        pass.program = program;
        pass.inIsMain = inMain;
        pass.inFbId = inId;
        pass.inW = 854;
        pass.inH = 480;
        pass.outIsMain = outMain;
        pass.outFbId = outId;
        pass.outW = 854;
        pass.outH = 480;
        pass.push = new float[1];
        pass.pushBytes = 4;
        return pass;
    }

    @Test
    void aTwoPassChainExecutesInChainOrderWithABarrierBetween() {
        PostChainPass blur = pass("blur", true, 11, false, 22);
        PostChainPass blit = pass("blit", false, 22, true, 11);
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of(blur, blit));

        List<String> recorded = new ArrayList<>();
        ChainGraph chain = ChainGraph.build(result, (node, context) -> recorded.add(node.program()));
        List<List<GraphBarrier>> barriers = new ArrayList<>();
        chain.execute(null, (before, context) -> barriers.add(before));

        assertEquals(List.of("blur", "blit"), recorded);
        assertEquals(2, chain.graph().executionOrder().size());
        assertEquals("blur", chain.graph().executionOrder().get(0).name());
        assertEquals("blit", chain.graph().executionOrder().get(1).name());
        // The intermediate attachment is written by blur and sampled by blit: a barrier must
        // sit before blit (its first-use transition may sit before blur instead).
        boolean barrierBeforeBlit = !barriers.get(1).isEmpty();
        boolean barrierOnIntermediate = barriers.stream().flatMap(List::stream)
                .anyMatch(b -> b.resource() == chain.resource(PostChainGraphAdapter.attachment(22)));
        assertTrue(barrierBeforeBlit || barrierOnIntermediate,
                "expected a synthesized barrier on the intermediate attachment");
    }

    @Test
    void theOutlineChainCompilesToOnePassOverItsImport() {
        PostChainPass sobel = pass("entity_outline", false, 77, false, 77);
        sobel.groupMainFbId = 77;
        sobel.groupMainTextureId = 5;
        sobel.groupMainW = 854;
        sobel.groupMainH = 480;
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of(sobel));

        List<String> recorded = new ArrayList<>();
        ChainGraph chain = ChainGraph.build(result, (node, context) -> recorded.add(node.program()));
        chain.execute(null, (before, context) -> { });

        assertEquals(List.of("entity_outline"), recorded);
        assertEquals(1, chain.graph().executionOrder().size());
        assertTrue(chain.resource(PostChainGraphAdapter.attachment(77)) != null);
    }

    @Test
    void aRefusedChainHasNoGraph() {
        PostChainPass spider = pass("spider", false, 99, true, 11);
        PostChainGraphAdapter.Result result = PostChainGraphAdapter.convert(List.of(spider));
        assertThrows(IllegalArgumentException.class,
                () -> ChainGraph.build(result, (node, context) -> { }));
    }
}
