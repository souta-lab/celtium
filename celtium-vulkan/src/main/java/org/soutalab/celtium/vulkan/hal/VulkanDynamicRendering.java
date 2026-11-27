package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;

import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.shader.PackExecutionPlan;

import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

/**
 * The thin Vulkan 1.3 dynamic-rendering surface the frame executor and the pack executor record
 * through (design section 2.2/2.4): {@code vkCmdBeginRendering} / {@code vkCmdEndRendering} built
 * from the core contract's {@link ActiveRenderTargets}, and the {@code synchronization2} memory
 * barrier built from a plan's precomputed {@link PackExecutionPlan.BarrierSpec}. There are no
 * render-pass or framebuffer objects anywhere in this HAL - this class is where that shows.
 * <p>
 * Clear values are the caller's: {@code clearColors} runs parallel to the target's colour
 * attachments (a null entry, or a short list, means that attachment is not cleared even when its
 * load op says CLEAR - the caller owns that consistency), and the depth/stencil clear applies
 * when the target has a depth attachment.
 */
public final class VulkanDynamicRendering {

    private VulkanDynamicRendering() {
    }

    public static void beginRendering(VkCommandBuffer cmd, MemoryStack stack, ActiveRenderTargets targets,
                                      float[][] clearColors, float clearDepth, int clearStencil) {
        VkRenderingAttachmentInfo.Buffer colourInfos = null;
        if (!targets.colorAttachments().isEmpty()) {
            colourInfos = VkRenderingAttachmentInfo.calloc(targets.colorAttachments().size(), stack);
            for (int i = 0; i < targets.colorAttachments().size(); i++) {
                ActiveRenderTargets.RenderTargetView view = targets.colorAttachments().get(i);
                VkRenderingAttachmentInfo info = colourInfos.get(i).sType$Default()
                        .imageView(view.imageViewHandle())
                        .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .loadOp(loadOp(view.loadOp()))
                        .storeOp(storeOp(view.storeOp()));
                if (view.loadOp() == ActiveRenderTargets.LoadOp.CLEAR
                        && clearColors != null && i < clearColors.length && clearColors[i] != null) {
                    VkClearValue clear = info.clearValue();
                    clear.color().float32(stack.floats(clearColors[i][0], clearColors[i][1],
                            clearColors[i][2], clearColors[i][3]));
                }
            }
        }
        VkRenderingInfo rendering = VkRenderingInfo.calloc(stack).sType$Default()
                .renderArea(org.lwjgl.vulkan.VkRect2D.calloc(stack)
                        .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack).set(0, 0))
                        .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack)
                                .set(targets.width(), targets.height())))
                .layerCount(1)
                .pColorAttachments(colourInfos);
        if (targets.depthAttachment() != null) {
            ActiveRenderTargets.RenderTargetView depth = targets.depthAttachment();
            VkRenderingAttachmentInfo depthInfo = VkRenderingAttachmentInfo.calloc(stack).sType$Default()
                    .imageView(depth.imageViewHandle())
                    .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(loadOp(depth.loadOp()))
                    .storeOp(storeOp(depth.storeOp()));
            if (depth.loadOp() == ActiveRenderTargets.LoadOp.CLEAR) {
                depthInfo.clearValue().depthStencil().set(clearDepth, clearStencil);
            }
            rendering.pDepthAttachment(depthInfo);
        }
        VK13.vkCmdBeginRendering(cmd, rendering);
    }

    public static void endRendering(VkCommandBuffer cmd) {
        VK13.vkCmdEndRendering(cmd);
    }

    /**
     * A global memory barrier from a plan's precomputed spec ({@code vkCmdPipelineBarrier2}):
     * the deferred/composite chain's edges are memory dependencies only - image layout transitions
     * ride on the attachment declarations of the passes themselves.
     */
    public static void pipelineBarrier2Memory(VkCommandBuffer cmd, MemoryStack stack,
                                              PackExecutionPlan.BarrierSpec spec) {
        VkMemoryBarrier2.Buffer barriers = VkMemoryBarrier2.calloc(1, stack);
        barriers.get(0).sType$Default()
                .srcStageMask(spec.srcStageMask())
                .srcAccessMask(spec.srcAccessMask())
                .dstStageMask(spec.dstStageMask())
                .dstAccessMask(spec.dstAccessMask());
        VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default()
                .pMemoryBarriers(barriers);
        VK13.vkCmdPipelineBarrier2(cmd, dependency);
    }

    private static int loadOp(ActiveRenderTargets.LoadOp op) {
        return switch (op) {
            case LOAD -> VK_ATTACHMENT_LOAD_OP_LOAD;
            case CLEAR -> VK_ATTACHMENT_LOAD_OP_CLEAR;
            case DONT_CARE -> VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        };
    }

    private static int storeOp(ActiveRenderTargets.StoreOp op) {
        return switch (op) {
            case STORE -> VK_ATTACHMENT_STORE_OP_STORE;
            case DONT_CARE -> VK_ATTACHMENT_STORE_OP_DONT_CARE;
        };
    }
}
