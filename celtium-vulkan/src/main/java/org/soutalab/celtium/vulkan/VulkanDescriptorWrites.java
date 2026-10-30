package org.soutalab.celtium.vulkan;

import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

/**
 * Bounded census of the image layouts descriptor writes declare.
 * <p>
 * One {@code VUID-vkCmdDraw-None-09600} survives in the menu/splash phase: a descriptor that declares
 * {@code TRANSFER_DST_OPTIMAL} for an image that is actually {@code SHADER_READ_ONLY_OPTIMAL}. The immediate pipelines
 * were measured clean (28 SHADER_READ_ONLY, 1 GENERAL, no TRANSFER_DST), so the write that trips the check is written
 * somewhere else - the post chain, the UI pipeline, the terrain pipeline or the registry's publish path. This reads
 * the layout out of the write structure itself, so wherever the offender is, it names itself on the same line as its
 * class, and the earlier call-site search cannot miss a write that builds its image info indirectly.
 * <p>
 * Off unless {@code -Dceltium.vulkan.descWriteCensus=true}: a per-write line is diagnostic, and the finding it
 * belongs to is one message at frame 0.
 */
final class VulkanDescriptorWrites {
    /** Whether the census prints; off by default because it is one line per descriptor write. */
    private static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.descWriteCensus");
    /** How many writes were printed, so a long run cannot flood the log. */
    private static int printed;

    private VulkanDescriptorWrites() {
    }

    /**
     * {@return whether a texture wrapper still holds a live image, view and sampler}.
     * <p>
     * A destroyed wrapper clears all three handles. Writing a descriptor from one hands the GPU a null or freed handle,
     * which validation reports and a driver without the layer may fault on - the measured case was a menu phase that
     * deletes and recreates its screen-sized framebuffers: a RADV GPUVM fault, device lost, and a black screen because
     * a lost device has nothing left to present. A batch whose texture is gone is refused and named instead.
     */
    static boolean celtium$isLive(VulkanGlTexture texture) {
        return texture != null && texture.image() != 0L && texture.view() != 0L && texture.sampler() != 0L;
    }

    /** How many dead-texture refusals were printed, so a long run cannot flood the log. */
    private static int deadTextureRefusals;

    /** Prints one bounded line when a descriptor is refused because its wrapper no longer has an image. */
    static void celtium$noteDeadTexture(String owner) {
        if (deadTextureRefusals >= 8) {
            return;
        }
        deadTextureRefusals++;
        System.out.println("[Celtium] CELTIUM_DEAD_TEXTURE_REFUSED owner=" + owner
                + " (the wrapper\'s image/view/sampler were released; the batch is refused instead of binding a"
                + " freed image)");
    }

    /** Writes whose image info could not be read, so a silent miss is a number rather than nothing. */
    private static int unreadable;

    /**
     * Prints the layout of one descriptor write about to be issued, with the caller's class name.
     * <p>
     * The first forty writes are printed as a sample, and beyond that only the transfer layouts are: the violation this
     * exists for declares {@code TRANSFER_DST_OPTIMAL}, which no sampling descriptor should ever declare, and a sample
     * that stops at forty writes never reaches the menu phase where it happens.
     */
    static void note(VkWriteDescriptorSet.Buffer write, String owner) {
        if (!ENABLED) {
            return;
        }
        if (write == null || write.remaining() == 0) {
            unreadable++;
            return;
        }
        VkDescriptorImageInfo.Buffer images = write.get(0).pImageInfo();
        if (images == null || images.remaining() == 0) {
            unreadable++;
            return;
        }
        int layout = images.get(0).imageLayout();
        boolean transfer = layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                || layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        if (!transfer && printed >= 40) {
            return;
        }
        if (transfer && transferPrinted >= 20) {
            return;
        }
        if (transfer) {
            transferPrinted++;
        }
        printed++;
        System.out.println("[Celtium] CELTIUM_DESC_LAYOUT_WRITE owner=" + owner + " writes=" + write.remaining()
                + " layout=" + nameOf(layout) + " image=" + Long.toHexString(images.get(0).imageView())
                + " unreadable=" + unreadable);
    }

    /** How many transfer-layout writes were printed, which are the anomaly this census is for. */
    private static int transferPrinted;

    /** {@return the layout's name}, for a line that can be read without a table. */
    private static String nameOf(int layout) {
        switch (layout) {
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL: return "GENERAL";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL: return "SHADER_READ_ONLY";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL: return "TRANSFER_DST";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL: return "TRANSFER_SRC";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL: return "COLOR_ATTACHMENT";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED: return "UNDEFINED";
            default: return Integer.toString(layout);
        }
    }
}
