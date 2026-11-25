package org.soutalab.celtium.vulkan.graph;

/**
 * Image layouts as the frame graph tracks them, mirroring {@code VkImageLayout} values so the
 * executor's barrier translation is a copy, not a conversion table. Only the layouts the graph
 * can place a resource into are listed.
 */
public enum ImageLayout {

    UNDEFINED(0),
    GENERAL(1),
    COLOR_ATTACHMENT(2),
    DEPTH_ATTACHMENT(3),
    DEPTH_READ_ONLY(4),
    SHADER_READ_ONLY(5),
    TRANSFER_SRC(6),
    TRANSFER_DST(7),
    PRESENT_SRC(1000001002);

    private final int vkValue;

    ImageLayout(int vkValue) {
        this.vkValue = vkValue;
    }

    public int vkValue() {
        return this.vkValue;
    }
}
