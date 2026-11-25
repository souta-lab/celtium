package org.soutalab.celtium.vulkan.compat;

import java.util.List;

/**
 * One fullscreen pass of a vanilla post-process chain, restated as a frame graph node: the program,
 * the attachments it samples (its main input first, then the auxiliaries, in the chain's own order),
 * the attachment it writes, and the uniform block the collector packed for it. Produced by {@link
 * PostChainGraphAdapter} from the collected {@code PostChainPass} shape, so the collector in
 * {@code ShaderGroupMixin} - which resolves the chain's live uniforms and framebuffer identities -
 * does not change; only the consumer does.
 */
public final class PostChainNode {

    private final String program;
    private final String input;
    private final List<String> auxInputs;
    private final String output;
    private final int width;
    private final int height;
    private final float[] push;
    private final int pushBytes;
    private final String[] blockNames;
    private final int[] blockOffsets;

    PostChainNode(String program, String input, List<String> auxInputs, String output,
            int width, int height, float[] push, int pushBytes, String[] blockNames,
            int[] blockOffsets) {
        this.program = program;
        this.input = input;
        this.auxInputs = auxInputs;
        this.output = output;
        this.width = width;
        this.height = height;
        this.push = push;
        this.pushBytes = pushBytes;
        this.blockNames = blockNames;
        this.blockOffsets = blockOffsets;
    }

    /** {@return the chain program's id}, as the collector reduced it (for example {@code "blur"}). */
    public String program() {
        return this.program;
    }

    /** {@return the attachment this pass samples as its main input}. */
    public String input() {
        return this.input;
    }

    /** {@return the attachments this pass samples as auxiliaries}, in the chain's order. */
    public List<String> auxInputs() {
        return this.auxInputs;
    }

    /** {@return the attachment this pass writes}. */
    public String output() {
        return this.output;
    }

    /** {@return whether this pass writes the shared frame image itself}. */
    public boolean writesFrameImage() {
        return PostChainGraphAdapter.FRAME.equals(this.output);
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** {@return the packed uniform block values}, of which {@link #pushBytes()} bytes are meaningful. */
    public float[] push() {
        return this.push;
    }

    public int pushBytes() {
        return this.pushBytes;
    }

    /** {@return the block's member names in packing order}, or null when the collector did not report them. */
    public String[] blockNames() {
        return this.blockNames;
    }

    /** {@return the block's member byte offsets, parallel to {@link #blockNames()}}. */
    public int[] blockOffsets() {
        return this.blockOffsets;
    }
}
