package org.soutalab.celtium.vulkan.compat;

/**
 * One collected post-process pass: the collector's contract shape, moved out of the legacy
 * chain executor (arch-graph milestone 2, Pass-type decision (i) in lanechat #8767). The
 * collector (ShaderGroupMixin) and the graph adapter refer to this type directly, so the
 * shape survives the executor's eventual deletion unchanged; the executor consumes it as
 * an input, it no longer owns it.
 */
public final class PostChainPass {
    public String program;
    public int inFbId;
    public int inW;
    public int inH;
    public int outFbId;
    public int outW;
    public int outH;
    public boolean inIsMain;
    public boolean outIsMain;
    public int[] auxFbIds = new int[0];
    public int[] auxW = new int[0];
    public int[] auxH = new int[0];
    public float[] push = new float[0];
    public int pushBytes;
    /**
     * The block the collector packed, as the member names it wrote and the byte offset it wrote each at, in
     * packing order. {@link #push} holds the values and {@link #pushBytes} how many of them are meaningful; this
     * is the same layout said member by member, which is the only form the shader's own declaration can be
     * compared against: two blocks of equal size whose members are permuted are indistinguishable in a length.
     * <p>
     * Null when the collector does not report it. The uniform-buffer route then refuses these passes rather than
     * write a block whose offsets nothing has checked - see the executor's block refusal.
     */
    public String[] blockNames;
    public int[] blockOffsets;
    /**
     * Nonzero when the chain's input framebuffer is not the game's own - the entity-outline case, whose chain
     * reads and writes the target the game filled with the silhouette: the identity its passes carry, the GL
     * texture name the game binds again to composite it, and its size, so the caller can adopt the image it
     * already keeps under that identity instead of letting the chain allocate an empty target for it. Only
     * pass[0] carries it; zero marks the game's own framebuffer, which resolves to the shared image.
     */
    public int groupMainTextureId;
    public int groupMainFbId;
    public int groupMainW;
    public int groupMainH;
}
