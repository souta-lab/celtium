package org.soutalab.celtium.vulkan.graph;

/**
 * How a pass uses a resource: the stage that performs the access, the access mask, whether it
 * writes, and - for images - the layout the resource must be in. The frame graph compiler turns
 * consecutive accesses to one resource into barriers from these triples (design section 2.1:
 * layout transitions and barriers are synthesized by the graph, never written by hand per pass).
 *
 * <p>Stage masks are deliberately the union of the stages that can perform the access (a depth
 * texture read by a compute pass runs at compute stage; the same read in a graphics pass runs at
 * fragment test / shader stages). A superset stage mask is always correct, only less parallel;
 * narrowing per pass kind is a later refinement, not a correctness fix.
 */
public enum AccessType {

    COLOR_ATTACHMENT_WRITE(
            SyncBits.STAGE_COLOR_ATTACHMENT_OUTPUT, SyncBits.ACCESS_COLOR_ATTACHMENT_WRITE,
            true, ImageLayout.COLOR_ATTACHMENT, true),
    DEPTH_ATTACHMENT_WRITE(
            SyncBits.STAGE_EARLY_FRAGMENT_TESTS | SyncBits.STAGE_LATE_FRAGMENT_TESTS,
            SyncBits.ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE,
            true, ImageLayout.DEPTH_ATTACHMENT, true),
    DEPTH_READ(
            SyncBits.STAGE_EARLY_FRAGMENT_TESTS | SyncBits.STAGE_LATE_FRAGMENT_TESTS
                    | SyncBits.STAGE_FRAGMENT_SHADER | SyncBits.STAGE_COMPUTE_SHADER,
            SyncBits.ACCESS_DEPTH_STENCIL_ATTACHMENT_READ,
            false, ImageLayout.DEPTH_READ_ONLY, true),
    SHADER_READ(
            SyncBits.STAGE_VERTEX_SHADER | SyncBits.STAGE_FRAGMENT_SHADER
                    | SyncBits.STAGE_COMPUTE_SHADER,
            SyncBits.ACCESS_SHADER_READ,
            false, ImageLayout.SHADER_READ_ONLY, false),
    COMPUTE_READ(
            SyncBits.STAGE_COMPUTE_SHADER, SyncBits.ACCESS_SHADER_READ,
            false, ImageLayout.SHADER_READ_ONLY, false),
    COMPUTE_WRITE(
            SyncBits.STAGE_COMPUTE_SHADER, SyncBits.ACCESS_SHADER_WRITE,
            true, ImageLayout.GENERAL, false),
    TRANSFER_SRC(
            SyncBits.STAGE_TRANSFER, SyncBits.ACCESS_TRANSFER_READ,
            false, ImageLayout.TRANSFER_SRC, false),
    TRANSFER_DST(
            SyncBits.STAGE_TRANSFER, SyncBits.ACCESS_TRANSFER_WRITE,
            true, ImageLayout.TRANSFER_DST, false),
    /** Buffer-only access: the resource feeds {@code vkCmdDraw*Indirect}; it has no layout. */
    INDIRECT_READ(
            SyncBits.STAGE_DRAW_INDIRECT, SyncBits.ACCESS_INDIRECT_COMMAND_READ,
            false, null, false),
    /** The terminal use of a swapchain image: layout sink, no memory access of its own. */
    PRESENT(
            SyncBits.STAGE_BOTTOM_OF_PIPE, 0,
            false, ImageLayout.PRESENT_SRC, true);

    private final int stageMask;
    private final int accessMask;
    private final boolean write;
    private final ImageLayout layout;
    private final boolean imageOnly;

    AccessType(int stageMask, int accessMask, boolean write, ImageLayout layout,
               boolean imageOnly) {
        this.stageMask = stageMask;
        this.accessMask = accessMask;
        this.write = write;
        this.layout = layout;
        this.imageOnly = imageOnly;
    }

    public int stageMask() {
        return this.stageMask;
    }

    public int accessMask() {
        return this.accessMask;
    }

    public boolean isWrite() {
        return this.write;
    }

    /** The layout an image must be in for this access, or null for buffer-only accesses. */
    public ImageLayout layout() {
        return this.layout;
    }

    /** Whether this access only makes sense on an image (attachment, depth, present). */
    public boolean isImageOnly() {
        return this.imageOnly;
    }
}
