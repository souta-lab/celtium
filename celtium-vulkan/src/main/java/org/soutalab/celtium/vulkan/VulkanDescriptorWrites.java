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

    /** Prints the layout of one descriptor write about to be issued, bounded, with the caller's class name. */
    static void note(VkWriteDescriptorSet.Buffer write, String owner) {
        if (!ENABLED || printed >= 40 || write == null || write.remaining() == 0) {
            return;
        }
        VkDescriptorImageInfo.Buffer images = write.get(0).pImageInfo();
        if (images == null || images.remaining() == 0) {
            return;
        }
        printed++;
        System.out.println("[Celtium] CELTIUM_DESC_LAYOUT_WRITE owner=" + owner + " writes=" + write.remaining()
                + " layout=" + nameOf(images.get(0).imageLayout()));
    }

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
