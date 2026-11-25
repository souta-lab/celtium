package org.soutalab.celtium.vulkan.graph;

import java.util.List;

/**
 * Walks a {@link CompiledGraph} in execution order: at each pass boundary it hands the
 * synthesized barriers to the {@link BarrierSink}, then invokes the pass's recorder. This is the
 * whole runtime contract of the graph - the backend's frame entry calls it once per frame with
 * the HAL's command context, and everything the old frame path sequenced by hand (barriers,
 * layout transitions, pass order) comes from the compiled graph instead.
 */
public final class GraphExecutor {

    private GraphExecutor() {
    }

    public static void execute(CompiledGraph graph, Object context, BarrierSink sink) {
        List<PassNode> order = graph.executionOrder();
        for (int index = 0; index < order.size(); index++) {
            sink.applyBarriers(graph.barriersBefore(index), context);
            PassRecorder recorder = order.get(index).recorder();
            if (recorder != null) {
                recorder.record(context);
            }
        }
    }
}
