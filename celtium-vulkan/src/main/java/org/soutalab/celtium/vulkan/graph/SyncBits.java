package org.soutalab.celtium.vulkan.graph;

/**
 * Pipeline stage and memory access masks, mirroring the {@code VkPipelineStageFlagBits} and
 * {@code VkAccessFlagBits} values the graph's barrier synthesis produces. The executor (HAL side)
 * copies them into {@code VkImageMemoryBarrier2} / {@code VkMemoryBarrier2} unchanged.
 */
public final class SyncBits {

    private SyncBits() {
    }

    // VkPipelineStageFlagBits
    public static final int STAGE_TOP_OF_PIPE = 0x00000001;
    public static final int STAGE_DRAW_INDIRECT = 0x00000002;
    public static final int STAGE_VERTEX_INPUT = 0x00000004;
    public static final int STAGE_VERTEX_SHADER = 0x00000008;
    public static final int STAGE_FRAGMENT_SHADER = 0x00000080;
    public static final int STAGE_EARLY_FRAGMENT_TESTS = 0x00000100;
    public static final int STAGE_LATE_FRAGMENT_TESTS = 0x00000200;
    public static final int STAGE_COLOR_ATTACHMENT_OUTPUT = 0x00000400;
    public static final int STAGE_COMPUTE_SHADER = 0x00000800;
    public static final int STAGE_TRANSFER = 0x00001000;
    public static final int STAGE_BOTTOM_OF_PIPE = 0x00002000;
    public static final int STAGE_ALL_COMMANDS = 0x00010000;

    // VkAccessFlagBits
    public static final int ACCESS_INDIRECT_COMMAND_READ = 0x00000001;
    public static final int ACCESS_SHADER_READ = 0x00000020;
    public static final int ACCESS_SHADER_WRITE = 0x00000040;
    public static final int ACCESS_COLOR_ATTACHMENT_READ = 0x00000080;
    public static final int ACCESS_COLOR_ATTACHMENT_WRITE = 0x00000100;
    public static final int ACCESS_DEPTH_STENCIL_ATTACHMENT_READ = 0x00000200;
    public static final int ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE = 0x00000400;
    public static final int ACCESS_TRANSFER_READ = 0x00000800;
    public static final int ACCESS_TRANSFER_WRITE = 0x00001000;
    public static final int ACCESS_MEMORY_READ = 0x00008000;
    public static final int ACCESS_MEMORY_WRITE = 0x00010000;
}
