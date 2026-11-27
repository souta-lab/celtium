/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.hal.TransientAttachmentSlab;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK13.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK13.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK13.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK13.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK13.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_USAGE_STORAGE_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK13.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK13.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK13.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK13.VK_SUCCESS;
import static org.lwjgl.vulkan.VK13.vkBindImageMemory;
import static org.lwjgl.vulkan.VK13.vkCmdCopyImage;
import static org.lwjgl.vulkan.VK13.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK13.vkCreateImage;
import static org.lwjgl.vulkan.VK13.vkCreateImageView;
import static org.lwjgl.vulkan.VK13.vkDestroyImage;
import static org.lwjgl.vulkan.VK13.vkDestroyImageView;
import static org.lwjgl.vulkan.VK13.vkGetImageMemoryRequirements;

/**
 * The pack's render targets as real Vulkan images, all bound into the shared
 * {@link TransientAttachmentSlab} (no per-target device memory). Ping-pong targets own two
 * images and a read/write index pair; the executor flips them after a writing composite pass.
 * Layouts are tracked per image and transitioned explicitly at pass boundaries.
 */
final class PackAttachments implements AutoCloseable {

    /** One physical image of a target. */
    private static final class TargetImage {
        long image;
        long view;
        int layout = VK_IMAGE_LAYOUT_UNDEFINED;
        TransientAttachmentSlab.Allocation allocation;
        long requirementSize;
        long requirementAlignment;
    }

    /** A plan target: one image, or two for ping-pong. */
    private static final class Target {
        final PackExecutionPlan.RenderTargetSpec spec;
        final TargetImage[] images;
        int readIndex;
        int writeIndex;
        int width;
        int height;

        Target(PackExecutionPlan.RenderTargetSpec spec) {
            this.spec = spec;
            this.images = new TargetImage[spec.pingPong() ? 2 : 1];
            for (int i = 0; i < images.length; i++) {
                images[i] = new TargetImage();
            }
        }

        TargetImage read() {
            return images[readIndex];
        }

        TargetImage write() {
            return images[writeIndex];
        }

        void flip() {
            readIndex = writeIndex;
            writeIndex = 1 - writeIndex;
        }
    }

    private final VulkanContext context;
    private final TransientAttachmentSlab slab;
    private final Map<String, Target> targets = new LinkedHashMap<>();
    private final Set<String> storageTargets;
    private final int shadowExtent;
    private int viewWidth;
    private int viewHeight;

    PackAttachments(VulkanContext context, PackExecutionPlan plan, int shadowExtent,
                    Set<String> storageTargets) {
        this.context = context;
        this.shadowExtent = shadowExtent;
        this.storageTargets = storageTargets;
        int memoryType = context.memoryTypeIndex(0xFFFFFFFF,
                org.lwjgl.vulkan.VK13.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                "shader pack attachments");
        this.slab = new TransientAttachmentSlab(context.device(), memoryType);
        for (PackExecutionPlan.RenderTargetSpec spec : plan.renderTargets()) {
            targets.put(spec.name(), new Target(spec));
        }
    }

    /** (Re)creates every image for the given view extent. Call only while no pack frame is in flight. */
    void resize(int newViewWidth, int newViewHeight) {
        if (newViewWidth == viewWidth && newViewHeight == viewHeight && viewWidth > 0) {
            return;
        }
        destroyImages();
        viewWidth = newViewWidth;
        viewHeight = newViewHeight;
        for (Target target : targets.values()) {
            boolean shadowSized = target.spec.kind() == PackExecutionPlan.RenderTargetSpec.Kind.SHADOW_MAP
                    || target.spec.kind() == PackExecutionPlan.RenderTargetSpec.Kind.SHADOW_COLOR;
            target.width = shadowSized ? shadowExtent
                    : Math.max(1, Math.round(viewWidth * target.spec.sizeScale()));
            target.height = shadowSized ? shadowExtent
                    : Math.max(1, Math.round(viewHeight * target.spec.sizeScale()));
            for (TargetImage image : target.images) {
                createImage(target, image);
            }
        }
        // The slab grows only while nothing is live, so the whole set's footprint is
        // claimed and released once up front: that single claim grows the slab to fit
        // every image this resize will bind, and the per-image claims below then fit
        // without further growth.
        long footprint = 0L;
        for (Target target : targets.values()) {
            for (TargetImage image : target.images) {
                footprint += image.requirementSize + image.requirementAlignment;
            }
        }
        if (footprint > 0L) {
            TransientAttachmentSlab.Allocation sizing = slab.acquire(footprint, 1L);
            slab.release(sizing);
        }
        for (Target target : targets.values()) {
            for (TargetImage image : target.images) {
                bindImage(target, image);
            }
        }
    }

    private void createImage(Target target, TargetImage out) {
        org.lwjgl.vulkan.VkDevice device = context.device();
        boolean depth = isDepth(target.spec);
        int usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                | VK_IMAGE_USAGE_TRANSFER_DST_BIT
                | (depth ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                         : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        if (!depth && storageTargets.contains(target.spec.name())) {
            usage |= VK_IMAGE_USAGE_STORAGE_BIT;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(target.spec.format())
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().width(target.width).height(target.height).depth(1);
            LongBuffer handle = stack.mallocLong(1);
            int result = vkCreateImage(device, imageInfo, null, handle);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImage for pack target "
                        + target.spec.name() + " failed: " + result);
            }
            out.image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, out.image, requirements);
            out.requirementSize = requirements.size();
            out.requirementAlignment = requirements.alignment();
        }
    }

    private void bindImage(Target target, TargetImage out) {
        org.lwjgl.vulkan.VkDevice device = context.device();
        boolean depth = isDepth(target.spec);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            out.allocation = slab.acquire(out.requirementSize, out.requirementAlignment);
            int result = vkBindImageMemory(device, out.image, slab.memoryHandle(), out.allocation.offset());
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkBindImageMemory for pack target "
                        + target.spec.name() + " failed: " + result);
            }

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(out.image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(target.spec.format());
            viewInfo.subresourceRange()
                    .aspectMask(depth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            result = vkCreateImageView(device, viewInfo, null, handle);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImageView for pack target "
                        + target.spec.name() + " failed: " + result);
            }
            out.view = handle.get(0);
            out.layout = VK_IMAGE_LAYOUT_UNDEFINED;
        }
    }

    static boolean isDepth(PackExecutionPlan.RenderTargetSpec spec) {
        return spec.kind() == PackExecutionPlan.RenderTargetSpec.Kind.DEPTH_TEXTURE
                || spec.kind() == PackExecutionPlan.RenderTargetSpec.Kind.SHADOW_MAP;
    }

    boolean has(String name) {
        return targets.containsKey(name);
    }

    int width(String name) {
        return targets.get(name).width;
    }

    int height(String name) {
        return targets.get(name).height;
    }

    PackExecutionPlan.RenderTargetSpec spec(String name) {
        return targets.get(name).spec;
    }

    /** The image view sampled by later passes (the read side of a ping-pong pair). */
    long readView(String name) {
        return targets.get(name).read().view;
    }

    long readImage(String name) {
        return targets.get(name).read().image;
    }

    /** Advances a ping-pong target: what was just written becomes what is read. */
    void flip(String name) {
        Target target = targets.get(name);
        if (target.images.length > 1) {
            target.flip();
        }
    }

    /**
     * Builds the attachment set for a pass's declared outputs: the first depth-kind output is
     * the depth attachment, the rest are color attachments in declared order.
     *
     * @param clear whether attachments clear on load; the first terrain kind of a stage clears
     *              (pack stages own their targets outright), later kinds and the non-terrain
     *              windows load, so what earlier kinds drew survives
     */
    ActiveRenderTargets activeTargets(List<String> outputNames, boolean clear) {
        List<ActiveRenderTargets.RenderTargetView> colors = new ArrayList<>();
        ActiveRenderTargets.RenderTargetView depth = null;
        int width = 0;
        int height = 0;
        for (String name : outputNames) {
            Target target = targets.get(name);
            if (target == null) {
                continue;
            }
            ActiveRenderTargets.RenderTargetView view = new ActiveRenderTargets.RenderTargetView(
                    target.write().view, target.spec.format(),
                    clear ? ActiveRenderTargets.LoadOp.CLEAR : ActiveRenderTargets.LoadOp.LOAD,
                    ActiveRenderTargets.StoreOp.STORE);
            if (isDepth(target.spec) && depth == null) {
                depth = view;
            } else {
                colors.add(view);
            }
            width = Math.max(width, target.width);
            height = Math.max(height, target.height);
        }
        if (width == 0) {
            width = viewWidth;
            height = viewHeight;
        }
        return new ActiveRenderTargets(colors, depth, width, height);
    }

    /** Transitions the write image of a target to an attachment layout. */
    void transitionWriteToAttachment(VkCommandBuffer cmd, String name) {
        Target target = targets.get(name);
        if (target == null) {
            return;
        }
        transition(cmd, target, target.write(),
                isDepth(target.spec) ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                        : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
    }

    /** Transitions the read image of a target to shader-read layout. */
    void transitionReadToSampled(VkCommandBuffer cmd, String name) {
        Target target = targets.get(name);
        if (target == null) {
            return;
        }
        transition(cmd, target, target.read(), VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    /** Transitions the read image of a target to GENERAL (storage image access). */
    void transitionReadToGeneral(VkCommandBuffer cmd, String name) {
        Target target = targets.get(name);
        if (target == null) {
            return;
        }
        transition(cmd, target, target.read(), VK_IMAGE_LAYOUT_GENERAL);
    }

    private void transition(VkCommandBuffer cmd, Target target, TargetImage image, int newLayout) {
        if (image.layout == newLayout) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(1, stack);
            barriers.get(0)
                    .sType(org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(image.layout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK13.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK13.VK_QUEUE_FAMILY_IGNORED)
                    .image(image.image)
                    .srcAccessMask(accessMaskFor(image.layout))
                    .dstAccessMask(accessMaskFor(newLayout));
            barriers.get(0).subresourceRange()
                    .aspectMask(isDepth(target.spec) ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            vkCmdPipelineBarrier(cmd,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    0, null, null, barriers);
        }
        image.layout = newLayout;
    }

    private static int accessMaskFor(int layout) {
        return switch (layout) {
            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> VK_ACCESS_SHADER_READ_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> VK_ACCESS_TRANSFER_READ_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> VK_ACCESS_TRANSFER_WRITE_BIT;
            default -> 0;
        };
    }

    /** Copies one target's read image into another target's write image (the depthtex copies). */
    void copyReadToWrite(VkCommandBuffer cmd, String sourceName, String destName) {
        Target source = targets.get(sourceName);
        Target dest = targets.get(destName);
        if (source == null || dest == null) {
            return;
        }
        transition(cmd, source, source.read(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        transition(cmd, dest, dest.write(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkImageCopy.Buffer regions = org.lwjgl.vulkan.VkImageCopy.calloc(1, stack);
            regions.get(0).srcSubresource()
                    .aspectMask(isDepth(source.spec) ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            regions.get(0).dstSubresource()
                    .aspectMask(isDepth(dest.spec) ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            regions.get(0).extent().width(Math.min(source.width, dest.width))
                    .height(Math.min(source.height, dest.height)).depth(1);
            vkCmdCopyImage(cmd, source.read().image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    dest.write().image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, regions);
        }
    }

    private void destroyImages() {
        org.lwjgl.vulkan.VkDevice device = context.device();
        for (Target target : targets.values()) {
            for (TargetImage image : target.images) {
                if (image.view != 0L) {
                    vkDestroyImageView(device, image.view, null);
                    image.view = 0L;
                }
                if (image.image != 0L) {
                    vkDestroyImage(device, image.image, null);
                    image.image = 0L;
                }
                if (image.allocation != null) {
                    slab.release(image.allocation);
                    image.allocation = null;
                }
                image.layout = VK_IMAGE_LAYOUT_UNDEFINED;
            }
            target.readIndex = 0;
            target.writeIndex = 0;
        }
    }

    @Override
    public void close() {
        destroyImages();
        slab.close();
    }
}
