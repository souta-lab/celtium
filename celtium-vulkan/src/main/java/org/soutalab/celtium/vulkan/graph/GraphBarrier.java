package org.soutalab.celtium.vulkan.graph;

/**
 * One synthesized dependency between two consecutive uses of a resource: the executor turns it
 * into a {@code VkImageMemoryBarrier2} (images, where the layouts differ or a hazard needs an
 * execution dependency) or a {@code VkMemoryBarrier2} (buffers). Stage and access masks use the
 * {@link SyncBits} values, which mirror Vulkan's, so the translation is field-by-field.
 */
public final class GraphBarrier {

    private final GraphResource resource;
    private final int srcStageMask;
    private final int dstStageMask;
    private final int srcAccessMask;
    private final int dstAccessMask;
    private final ImageLayout oldLayout;
    private final ImageLayout newLayout;

    GraphBarrier(GraphResource resource, int srcStageMask, int dstStageMask,
                 int srcAccessMask, int dstAccessMask,
                 ImageLayout oldLayout, ImageLayout newLayout) {
        this.resource = resource;
        this.srcStageMask = srcStageMask;
        this.dstStageMask = dstStageMask;
        this.srcAccessMask = srcAccessMask;
        this.dstAccessMask = dstAccessMask;
        this.oldLayout = oldLayout;
        this.newLayout = newLayout;
    }

    public GraphResource resource() {
        return this.resource;
    }

    public int srcStageMask() {
        return this.srcStageMask;
    }

    public int dstStageMask() {
        return this.dstStageMask;
    }

    public int srcAccessMask() {
        return this.srcAccessMask;
    }

    public int dstAccessMask() {
        return this.dstAccessMask;
    }

    /** Layouts are UNDEFINED/UNDEFINED for buffer barriers. */
    public ImageLayout oldLayout() {
        return this.oldLayout;
    }

    public ImageLayout newLayout() {
        return this.newLayout;
    }

    public boolean isLayoutTransition() {
        return this.oldLayout != this.newLayout;
    }

    @Override
    public String toString() {
        return this.resource.name() + " " + this.oldLayout + "->" + this.newLayout;
    }
}
