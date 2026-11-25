package org.soutalab.celtium.vulkan.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The declarative pass graph of one shader pack frame, in the shape the FrameGraph compiler consumes:
 * an ordered list of nodes, each naming the attachments it reads and writes, with the terrain stages
 * preceded by their GPU cull. This is the new architecture's replacement for the way a pack frame used
 * to be assembled implicitly - Demonica drove Celeritas's render loop, the loop's GL framebuffer binds
 * were intercepted one by one, and the pass structure existed nowhere as data. Here it is data: the
 * compiler can alias the transient targets (the deferred/composite ping-pong over the gbuffer set),
 * minimise barriers from the declared reads and writes, and schedule the two culls independently.
 * <p>
 * Attachment names are the pack format's own ({@code colortex0..}, {@code depthtex0}, {@code
 * shadowtex0}, {@code shadowcolor0}), so a node list can be checked against the pack that produced it.
 * Two names are the host's, not the pack's: {@link #SWAPCHAIN} (the present image the final pass writes
 * directly - the design abolishes the composite blit after it) and the indirect-draw buffers the culls
 * produce ({@link #INDIRECT_MAIN}, {@link #INDIRECT_SHADOW}), which the terrain nodes consume.
 * <p>
 * The graph is validated as it is built ({@link Builder#build()} refuses a malformed graph by name):
 * exactly one node writes the swapchain and it is the last node, stages never run backwards, and every
 * read is of an attachment an earlier node wrote or the caller declared as an import. A pack frame that
 * cannot be described is refused here, before any GPU work, in the same spirit as the post chain's
 * supply preflight.
 */
public final class ShaderpackDag {

    /** The present image; written by the final node only, and directly (no blit after it). */
    public static final String SWAPCHAIN = "swapchain";
    /** The main depth the gbuffers stage produces and the deferred/composite stages read. */
    public static final String DEPTHTEX0 = "depthtex0";
    /** The indirect draw buffer the main-camera cull produces for the gbuffers terrain node. */
    public static final String INDIRECT_MAIN = "indirect:main";
    /** The indirect draw buffer the light-camera cull produces for the shadow terrain node. */
    public static final String INDIRECT_SHADOW = "indirect:shadow";

    /** What a node does, which decides how the compiler records it. */
    public enum NodeKind {
        /** A compute dispatch that culls the resident sections into an indirect draw buffer. */
        CULL,
        /** A terrain (and entity) draw consuming one cull's indirect buffer. */
        TERRAIN,
        /** A fullscreen pass over already-rendered targets. */
        FULLSCREEN
    }

    /** One node of the graph: a stage, a kind, and the attachments it reads and writes. */
    public static final class Node {
        private final String name;
        private final ShaderpackStage stage;
        private final NodeKind kind;
        private final ShaderpackStage.CullCamera cullCamera;
        private final List<String> reads;
        private final List<String> writes;

        Node(String name, ShaderpackStage stage, NodeKind kind, ShaderpackStage.CullCamera cullCamera,
                List<String> reads, List<String> writes) {
            this.name = name;
            this.stage = stage;
            this.kind = kind;
            this.cullCamera = cullCamera;
            this.reads = Collections.unmodifiableList(new ArrayList<>(reads));
            this.writes = Collections.unmodifiableList(new ArrayList<>(writes));
        }

        public String name() {
            return this.name;
        }

        public ShaderpackStage stage() {
            return this.stage;
        }

        public NodeKind kind() {
            return this.kind;
        }

        /** {@return the camera a {@link NodeKind#CULL} node culls against}; NONE for other kinds. */
        public ShaderpackStage.CullCamera cullCamera() {
            return this.cullCamera;
        }

        public List<String> reads() {
            return this.reads;
        }

        public List<String> writes() {
            return this.writes;
        }
    }

    private final List<Node> nodes;
    private final Set<String> imports;

    private ShaderpackDag(List<Node> nodes, Set<String> imports) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.imports = Collections.unmodifiableSet(new LinkedHashSet<>(imports));
    }

    public List<Node> nodes() {
        return this.nodes;
    }

    /** {@return the attachments the caller supplies from outside the graph} (frame depth, history). */
    public Set<String> imports() {
        return this.imports;
    }

    /** {@return the cull cameras this frame needs}, in node order - the parallel cull plan. */
    public List<ShaderpackStage.CullCamera> cullPlan() {
        List<ShaderpackStage.CullCamera> plan = new ArrayList<>();
        for (Node node : this.nodes) {
            if (node.kind() == NodeKind.CULL) {
                plan.add(node.cullCamera());
            }
        }
        return plan;
    }

    /**
     * Builds the canonical pack DAG: shadow cull and shadow pass (when the pack has shadows), main
     * cull, the gbuffers terrain pass writing {@code gbufferTargets} colour targets plus {@link
     * #DEPTHTEX0}, then the pack's deferred and composite fullscreen passes over the colour set, and
     * the final pass writing {@link #SWAPCHAIN}. Deferred and composite passes declare the colour set
     * as both read and written - the ping-pong between them is the compiler's aliasing problem, not
     * the DAG's; the DAG only fixes the order.
     */
    public static ShaderpackDag standard(boolean shadowEnabled, int gbufferTargets,
            int deferredPasses, int compositePasses) {
        if (gbufferTargets < 1 || gbufferTargets > 8) {
            throw new IllegalArgumentException("gbufferTargets out of range: " + gbufferTargets);
        }
        if (deferredPasses < 0 || compositePasses < 0) {
            throw new IllegalArgumentException("pass counts must not be negative");
        }
        Builder builder = new Builder();
        List<String> colours = new ArrayList<>();
        for (int i = 0; i < gbufferTargets; i++) {
            colours.add("colortex" + i);
        }
        if (shadowEnabled) {
            builder.cull(ShaderpackStage.SHADOW);
            builder.node("shadow", ShaderpackStage.SHADOW, NodeKind.TERRAIN,
                    list(INDIRECT_SHADOW), list("shadowtex0", "shadowcolor0"));
        }
        builder.cull(ShaderpackStage.GBUFFERS);
        List<String> gbufferWrites = new ArrayList<>(colours);
        gbufferWrites.add(DEPTHTEX0);
        List<String> gbufferReads = new ArrayList<>();
        gbufferReads.add(INDIRECT_MAIN);
        if (shadowEnabled) {
            // The gbuffers terrain shades with the shadow map the shadow pass just produced.
            gbufferReads.add("shadowtex0");
            gbufferReads.add("shadowcolor0");
        }
        builder.node("gbuffers", ShaderpackStage.GBUFFERS, NodeKind.TERRAIN, gbufferReads, gbufferWrites);
        List<String> resolved = new ArrayList<>(colours);
        resolved.add(DEPTHTEX0);
        for (int i = 0; i < deferredPasses; i++) {
            builder.node("deferred" + i, ShaderpackStage.DEFERRED, NodeKind.FULLSCREEN,
                    resolved, colours);
        }
        for (int i = 0; i < compositePasses; i++) {
            builder.node("composite" + i, ShaderpackStage.COMPOSITE, NodeKind.FULLSCREEN,
                    resolved, colours);
        }
        builder.node("final", ShaderpackStage.FINAL, NodeKind.FULLSCREEN,
                list(colours.get(0)), list(SWAPCHAIN));
        return builder.build();
    }

    private static List<String> list(String... names) {
        List<String> out = new ArrayList<>(names.length);
        Collections.addAll(out, names);
        return out;
    }

    /** Accumulates nodes and validates the whole graph on {@link #build()}. */
    public static final class Builder {
        private final List<Node> nodes = new ArrayList<>();
        private final Set<String> imports = new LinkedHashSet<>();

        /** Declares an attachment the caller supplies (its content predates this graph). */
        public Builder importing(String attachment) {
            this.imports.add(attachment);
            return this;
        }

        /** Adds the cull node a terrain stage needs, named after the camera it serves. */
        public Builder cull(ShaderpackStage stage) {
            if (!stage.drawsTerrain()) {
                throw new IllegalArgumentException("stage " + stage + " draws no terrain and has no cull");
            }
            String indirect = stage.cullCamera() == ShaderpackStage.CullCamera.SHADOW
                    ? INDIRECT_SHADOW : INDIRECT_MAIN;
            this.nodes.add(new Node("cull-" + stage.cullCamera().name().toLowerCase(java.util.Locale.ROOT),
                    stage, NodeKind.CULL, stage.cullCamera(), list(), list(indirect)));
            return this;
        }

        /** Adds one pass node. Reads and writes are attachment names; order is declaration order. */
        public Builder node(String name, ShaderpackStage stage, NodeKind kind,
                List<String> reads, List<String> writes) {
            this.nodes.add(new Node(name, stage, kind, ShaderpackStage.CullCamera.NONE, reads, writes));
            return this;
        }

        /**
         * Validates and freezes the graph.
         *
         * @throws IllegalStateException naming the first rule the graph breaks
         */
        public ShaderpackDag build() {
            if (this.nodes.isEmpty()) {
                throw new IllegalStateException("shaderpack DAG has no nodes");
            }
            Set<String> supplied = new HashSet<>(this.imports);
            int swapchainWrites = 0;
            ShaderpackStage lastStage = null;
            for (int i = 0; i < this.nodes.size(); i++) {
                Node node = this.nodes.get(i);
                if (lastStage != null && node.stage().ordinal() < lastStage.ordinal()) {
                    throw new IllegalStateException("node " + node.name() + " runs stage " + node.stage()
                            + " after " + lastStage + "; pack stages never run backwards");
                }
                lastStage = node.stage();
                for (String read : node.reads()) {
                    if (!supplied.contains(read)) {
                        throw new IllegalStateException("node " + node.name() + " reads " + read
                                + ", which no earlier node wrote and the caller did not import");
                    }
                }
                for (String write : node.writes()) {
                    if (SWAPCHAIN.equals(write)) {
                        swapchainWrites++;
                        if (i != this.nodes.size() - 1) {
                            throw new IllegalStateException("node " + node.name()
                                    + " writes the swapchain before the end of the graph");
                        }
                    }
                    supplied.add(write);
                }
            }
            if (swapchainWrites != 1) {
                throw new IllegalStateException(
                        "shaderpack DAG must write the swapchain exactly once, found " + swapchainWrites);
            }
            return new ShaderpackDag(this.nodes, this.imports);
        }
    }
}
