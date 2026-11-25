package org.soutalab.celtium.vulkan.compat;

import org.soutalab.celtium.vulkan.graph.AccessType;
import org.soutalab.celtium.vulkan.graph.BarrierSink;
import org.soutalab.celtium.vulkan.graph.CompiledGraph;
import org.soutalab.celtium.vulkan.graph.FrameGraphBuilder;
import org.soutalab.celtium.vulkan.graph.GraphExecutor;
import org.soutalab.celtium.vulkan.graph.GraphResource;
import org.soutalab.celtium.vulkan.graph.PassNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A collected post-process chain restated as a compiled frame graph: one graphics pass node per
 * chain pass, in chain order, over imported image resources named by the adapter's attachment
 * names ({@link PostChainGraphAdapter#FRAME} for the shared frame image,
 * {@code fb:<identity>} for chain targets and the adopted group main).
 *
 * <p>This is the graph-side recorder the migration's wiring lane (#8740 point 2) builds on: the
 * backend hands the chain's passes to {@link #build} together with a {@link ChainPassSink}, and
 * {@link #execute} walks the compiled graph through {@link GraphExecutor} - the sink is invoked
 * once per chain pass, in graph execution order, with the pass's {@link PostChainNode}; the
 * {@link BarrierSink} receives the barriers the compiler synthesized between passes (a
 * colour-attachment write followed by a shader read of the same attachment, and the first-use
 * transitions out of {@code UNDEFINED}).
 *
 * <p>What this class deliberately does not do: it holds no Vulkan handle and records nothing
 * itself. The recording of one pass (program, render pass, descriptors, target ring) stays with
 * the sink's owner until the chain executor's recording body itself moves graph-side; the
 * graph's part - pass order, resource declarations, barrier synthesis - is fully determined
 * here and unit-tested without a device.
 *
 * <p>Resource shapes: an attachment's size is the largest extent any node declares for it -
 * a node's output extent for its output attachment, the import's extent for the group main,
 * and a consuming node's output extent as the only available lower bound for an input-only
 * attachment. Imported resources are never aliased by the compiler, so the shape only feeds
 * the graph's bookkeeping, never an allocation.
 */
public final class ChainGraph {

    /** Records one chain pass at execution time, in graph order. */
    public interface ChainPassSink {

        void recordPass(PostChainNode node, Object context);
    }

    private final CompiledGraph graph;
    private final List<PostChainNode> nodes;
    private final Map<String, GraphResource> resources;

    private ChainGraph(CompiledGraph graph, List<PostChainNode> nodes,
                       Map<String, GraphResource> resources) {
        this.graph = graph;
        this.nodes = nodes;
        this.resources = resources;
    }

    /**
     * Compiles one accepted adapter result into a chain graph. The caller must have checked
     * {@link PostChainGraphAdapter.Result#accepted()}: a refused chain has no graph.
     */
    public static ChainGraph build(PostChainGraphAdapter.Result result, ChainPassSink sink) {
        if (result == null || !result.accepted()) {
            throw new IllegalArgumentException("a refused chain has no graph");
        }
        if (sink == null) {
            throw new IllegalArgumentException("a chain pass sink is required");
        }
        Map<String, int[]> extents = new LinkedHashMap<String, int[]>();
        for (PostChainGraphAdapter.Import imported : result.imports()) {
            extents.put(imported.attachment(), new int[]{imported.width(), imported.height()});
        }
        for (PostChainNode node : result.nodes()) {
            grow(extents, node.output(), node.width(), node.height());
            grow(extents, node.input(), node.width(), node.height());
            for (String aux : node.auxInputs()) {
                grow(extents, aux, node.width(), node.height());
            }
        }
        Map<String, GraphResource> resources = new LinkedHashMap<String, GraphResource>();
        for (Map.Entry<String, int[]> entry : extents.entrySet()) {
            int[] extent = entry.getValue();
            resources.put(entry.getKey(), GraphResource.importedImage(
                    entry.getKey(), extent[0], extent[1], 0));
        }
        FrameGraphBuilder builder = new FrameGraphBuilder();
        for (final PostChainNode node : result.nodes()) {
            PassNode.Builder pass = PassNode.builder(node.program(), PassNode.Kind.GRAPHICS);
            // A pass that reads and writes the same attachment (the outline pass over its
            // adopted target) declares the write only: the compiler's per-resource combine
            // would keep the write anyway, and an intra-pass read-after-write is the
            // recorder's own ordering, not a pass boundary the graph synthesizes for.
            if (!node.input().equals(node.output())) {
                pass.access(resources.get(node.input()), AccessType.SHADER_READ);
            }
            for (String aux : node.auxInputs()) {
                if (!aux.equals(node.output()) && !aux.equals(node.input())) {
                    pass.access(resources.get(aux), AccessType.SHADER_READ);
                }
            }
            pass.access(resources.get(node.output()), AccessType.COLOR_ATTACHMENT_WRITE);
            pass.recorder(context -> sink.recordPass(node, context));
            builder.addPass(pass.build());
        }
        return new ChainGraph(builder.build(),
                Collections.unmodifiableList(new ArrayList<PostChainNode>(result.nodes())),
                Collections.unmodifiableMap(resources));
    }

    private static void grow(Map<String, int[]> extents, String attachment, int width, int height) {
        int[] current = extents.get(attachment);
        if (current == null) {
            extents.put(attachment, new int[]{width, height});
        } else {
            current[0] = Math.max(current[0], width);
            current[1] = Math.max(current[1], height);
        }
    }

    /** Walks the compiled graph: synthesized barriers to {@code sink}, passes to the build-time sink. */
    public void execute(Object context, BarrierSink sink) {
        GraphExecutor.execute(this.graph, context, sink);
    }

    public CompiledGraph graph() {
        return this.graph;
    }

    /** The chain's nodes, in declaration (chain) order. */
    public List<PostChainNode> nodes() {
        return this.nodes;
    }

    /** The imported image resource an attachment name resolved to, or null when undeclared. */
    public GraphResource resource(String attachment) {
        return this.resources.get(attachment);
    }
}
