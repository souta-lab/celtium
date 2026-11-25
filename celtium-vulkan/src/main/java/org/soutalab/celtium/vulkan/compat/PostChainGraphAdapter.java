package org.soutalab.celtium.vulkan.compat;

import org.soutalab.celtium.vulkan.VulkanPostChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Restates a collected vanilla post-process chain as frame graph nodes - the replacement for {@code
 * PostChainBridge}'s planning half. The bridge's work was three things, and each becomes a
 * declaration here instead of a procedure inside the frame:
 * <ul>
 *   <li><b>Supply preflight.</b> The executor refused a chain whose input no Vulkan image could
 *   supply (the N04 rule: an input that is only a real GL framebuffer keeps the whole chain on GL).
 *   The adapter runs the same walk over the pass list - an input is supplied when it is the shared
 *   frame image, the chain's imported group main, or an attachment an earlier node wrote - and
 *   reports the first unsupplied input by program name, before any node is handed over.</li>
 *   <li><b>Adoption.</b> The entity-outline chain reads and writes a target the game filled itself;
 *   the bridge adopted that image mid-frame and published it back under the GL texture name the
 *   game's composite binds. Here that is one {@link Import}: an external attachment the graph reads
 *   first, writes last, and publishes back - declared, so the compiler can order the frame around it
 *   instead of discovering it inside a handoff.</li>
 *   <li><b>Execution.</b> The bridge recorded the passes into the backend's own command buffer inside
 *   a GL-to-Vulkan handoff. The nodes this adapter produces are recorded by the frame graph's
 *   executor in graph order, in the frame's single submission; the per-pass state the chain used to
 *   invalidate by hand (viewport/scissor trackers) is the graph recorder's own bookkeeping.</li>
 * </ul>
 * The legacy executor ({@code VulkanPostChain}) and the bridge stay until the graph path passes its
 * gate; per the migration's rule they are deleted in the same integration that lands this replacement
 * for real, not before. What this class changes today is that the chain's <em>plan</em> exists as
 * data the compiler can consume, with the same refusal semantics the bridge enforced procedurally.
 */
public final class PostChainGraphAdapter {

    /** The shared frame image: the attachment every chain's "main" resolves to. Always imported. */
    public static final String FRAME = "frame";

    /** An external image the graph does not own: the chain's group main, when it is not the frame. */
    public static final class Import {
        private final String attachment;
        private final int textureId;
        private final int width;
        private final int height;

        Import(String attachment, int textureId, int width, int height) {
            this.attachment = attachment;
            this.textureId = textureId;
            this.width = width;
            this.height = height;
        }

        /** {@return the attachment name the nodes refer to this image by}. */
        public String attachment() {
            return this.attachment;
        }

        /** {@return the GL colour texture name the game binds again to composite the result}. */
        public int textureId() {
            return this.textureId;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }
    }

    /** The conversion of one chain: its nodes, its imports, and its refusal when it has one. */
    public static final class Result {
        private final List<PostChainNode> nodes;
        private final List<Import> imports;
        private final String unsupplied;

        Result(List<PostChainNode> nodes, List<Import> imports, String unsupplied) {
            this.nodes = Collections.unmodifiableList(nodes);
            this.imports = Collections.unmodifiableList(imports);
            this.unsupplied = unsupplied;
        }

        public List<PostChainNode> nodes() {
            return this.nodes;
        }

        public List<Import> imports() {
            return this.imports;
        }

        /**
         * {@return the program whose input the graph cannot supply}, or null when every input is
         * supplied. A non-null value refuses the whole chain - the caller keeps the GL chain, exactly
         * as the bridge's preflight refusal did.
         */
        public String unsupplied() {
            return this.unsupplied;
        }

        /** {@return whether the chain may run on the graph}. */
        public boolean accepted() {
            return this.unsupplied == null;
        }
    }

    private PostChainGraphAdapter() {
    }

    /** {@return the attachment name a chain target identity is referred to by} in node declarations. */
    public static String attachment(int framebufferIdentity) {
        return "fb:" + framebufferIdentity;
    }

    /**
     * Converts one collected chain. The passes are consumed in order; the walk that decides supply is
     * the executor's own, restated: the frame image is always supplied, the first pass's group main
     * (when it names an external framebuffer) is imported and supplied, and every later input must
     * have been written by an earlier pass of this chain.
     */
    public static Result convert(List<VulkanPostChain.Pass> passes) {
        List<PostChainNode> nodes = new ArrayList<>();
        List<Import> imports = new ArrayList<>();
        if (passes == null || passes.isEmpty()) {
            return new Result(nodes, imports, null);
        }
        Set<String> supplied = new HashSet<>();
        supplied.add(FRAME);
        VulkanPostChain.Pass first = passes.get(0);
        if (first.groupMainFbId != 0) {
            String groupMain = attachment(first.groupMainFbId);
            imports.add(new Import(groupMain, first.groupMainTextureId, first.groupMainW,
                    first.groupMainH));
            supplied.add(groupMain);
        }
        String unsupplied = null;
        for (VulkanPostChain.Pass pass : passes) {
            String input = pass.inIsMain ? FRAME : attachment(pass.inFbId);
            if (unsupplied == null && !supplied.contains(input)) {
                unsupplied = pass.program;
            }
            List<String> aux = new ArrayList<>();
            if (pass.auxFbIds != null) {
                for (int id : pass.auxFbIds) {
                    // 0 marks the group's main framebuffer, resolved the same way the main input is.
                    String auxAttachment = id == 0 ? FRAME : attachment(id);
                    aux.add(auxAttachment);
                    if (unsupplied == null && !supplied.contains(auxAttachment)) {
                        unsupplied = pass.program;
                    }
                }
            }
            String output = pass.outIsMain ? FRAME : attachment(pass.outFbId);
            nodes.add(new PostChainNode(pass.program, input, Collections.unmodifiableList(aux),
                    output, pass.outW, pass.outH, pass.push, pass.pushBytes, pass.blockNames,
                    pass.blockOffsets));
            supplied.add(output);
        }
        return new Result(nodes, imports, unsupplied);
    }
}
