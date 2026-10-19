package org.soutalab.celtium.vulkan.shadow;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.LongBuffer;

import org.lwjgl.vulkan.VkRenderingAttachmentInfoKHR;
import org.lwjgl.vulkan.VkRenderingInfoKHR;

import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdBeginRenderingKHR;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdEndRenderingKHR;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The native shadow map's own pass: a depth-only image rendered from a light camera.
 * <p>
 * This is the first slice of the shadow work the shader plan puts after the shaderless and pack gates, and it is
 * deliberately a separate unit rather than another block inside the renderer: the owners are its image, its views and its
 * pipeline, the frame asks it to record once per frame, and nothing about the shaderless path changes while it is off
 * ({@code -Dceltium.shadow.enabled=true}).
 * <p>
 * What this slice establishes is the resource and the recording: a 2048x2048 D32 depth image in GENERAL layout with a
 * depth-optimal dynamic-rendering pass over it once per frame, with the clear counted and reported. Drawing terrain into it
 * from the light's matrix, then sampling it in the world pass, are the next slices - each one a change to this class and to
 * the one call site, not a change spread through the renderer.
 */
public final class ShadowMapPass {
    /** The edge length of the shadow map; a power of two because the depth comparison will filter across it. */
    public static final int SIZE = 2048;

    private final VulkanContext context;
    private long image = VK_NULL_HANDLE;
    private long memory = VK_NULL_HANDLE;
    private long view = VK_NULL_HANDLE;
    /** Whether the depth image has been moved out of UNDEFINED into GENERAL. */
    private boolean transitioned;

    private long pipeline;
    private long pipelineLayout;
    private long passes;
    private long failures;
    private String lastFailure = "";

    private ShadowMapPass(VulkanContext context) {
        this.context = context;
    }

    /** {@return whether the pass is requested for this process}; a JVM argument, resolved once. */
    public static boolean requested() {
        return Boolean.getBoolean("celtium.shadow.enabled");
    }

    /**
     * Creates the shadow map's depth image and view.
     *
     * @return the pass, or null when the image could not be created (with the reason recorded on the instance it returns
     *         as null and printed by the caller)
     */
    public static ShadowMapPass create(VulkanContext context) {
        ShadowMapPass pass = new ShadowMapPass(context);
        if (!pass.allocate()) {
            return null;
        }
        if (!pass.createPipeline()) {
            pass.destroy();
            return null;
        }
        return pass;
    }

    /**
     * Builds the depth-only pipeline: a vertex stage whose positions come from {@code gl_VertexIndex}, no fragment stage,
     * no descriptors and no push constants. It draws a triangle at clip depth 0.5 over the whole map, which is enough to
     * prove that the pass rasterises into the shadow map and writes depth - the quantity the next slice (terrain from the
     * light's matrix) depends on.
     */
    private boolean createPipeline() {
        try {
            String vertexSource = "#version 450\n"
                    + "void main() {\n"
                    + "  vec2 p = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));\n"
                    + "  gl_Position = vec4(p * 2.0 - 1.0, 0.5, 1.0);\n"
                    + "}\n";
            org.soutalab.celtium.shaderpack.SpirvModule module = new org.soutalab.celtium.vulkan.ShadercGlslCompiler()
                    .compile(org.soutalab.celtium.shaderpack.Stage.VERTEX, vertexSource);
            org.soutalab.celtium.vulkan.ShaderPackPipelineFactory factory =
                    new org.soutalab.celtium.vulkan.ShaderPackPipelineFactory(this.context);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                java.nio.LongBuffer handle = stack.mallocLong(1);
                if (vkCreatePipelineLayout(this.context.device(),
                        org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack).sType$Default(), null, handle)
                        != VK_SUCCESS) {
                    this.failures++;
                    this.lastFailure = "vkCreatePipelineLayout(depth-only) failed";
                    return false;
                }
                this.pipelineLayout = handle.get(0);
            }
            try (org.soutalab.celtium.vulkan.ShaderPackPipelineFactory.ModuleHandle moduleHandle =
                         factory.createModule(module)) {
                this.pipeline = factory.createDepthOnlyPipeline(this.pipelineLayout, moduleHandle,
                        VK_FORMAT_D32_SFLOAT);
            }
            return true;
        } catch (RuntimeException | LinkageError | org.soutalab.celtium.shaderpack.SpirvStageCompiler.CompileFailure
                 error) {
            this.failures++;
            this.lastFailure = "depth-only pipeline: " + error;
            return false;
        }
    }

    private boolean allocate() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkImageCreateInfo imageInfo = org.lwjgl.vulkan.VkImageCreateInfo.calloc(stack)
                    .sType$Default().imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_D32_SFLOAT)
                    .extent(it -> it.width(SIZE).height(SIZE).depth(1))
                    .mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
            LongBuffer handle = stack.mallocLong(1);
            int result = vkCreateImage(this.context.device(), imageInfo, null, handle);
            if (result != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkCreateImage failed: " + result;
                return false;
            }
            this.image = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(this.context.device(), this.image, requirements);
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocateInfo = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "the native shadow map"));
            if (vkAllocateMemory(this.context.device(), allocateInfo, null, handle) != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkAllocateMemory failed";
                destroy();
                return false;
            }
            this.memory = handle.get(0);
            vkBindImageMemory(this.context.device(), this.image, this.memory, 0);
            org.lwjgl.vulkan.VkImageViewCreateInfo viewInfo = org.lwjgl.vulkan.VkImageViewCreateInfo.calloc(stack)
                    .sType$Default().image(this.image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(VK_FORMAT_D32_SFLOAT);
            viewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
            if (vkCreateImageView(this.context.device(), viewInfo, null, handle) != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkCreateImageView failed";
                destroy();
                return false;
            }
            this.view = handle.get(0);
            return true;
        }
    }

    /**
     * Records the pass into the frame's command buffer: a depth clear over the whole map in its own dynamic-rendering
     * scope. Drawing into it is the next slice; the clear is what makes the resource and the scope verifiable now.
     */
    public void record(VkCommandBuffer commandBuffer, MemoryStack stack) {
        if (this.image == VK_NULL_HANDLE) {
            return;
        }
        if (!this.transitioned) {
            // The image is created in UNDEFINED and the pass renders into GENERAL, so the first use needs the transition:
            // declaring GENERAL without it is VUID-vkCmdBeginRendering-pRenderingInfo-09588 (measured, ten messages in one
            // leg before this line existed). Subsequent passes keep GENERAL by storing the attachment.
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image).srcAccessMask(0).dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, 0,
                    null, null, barrier);
            this.transitioned = true;
        }
        VkRenderingAttachmentInfoKHR.Buffer depth = VkRenderingAttachmentInfoKHR.calloc(1, stack);
        depth.get(0).sType$Default().imageView(this.view)
                .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        org.lwjgl.vulkan.VkClearValue clear = org.lwjgl.vulkan.VkClearValue.calloc(stack);
        clear.depthStencil().depth(1.0f);
        clear.depthStencil().stencil(0);
        depth.get(0).clearValue(clear);
        org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE));
        VkRenderingInfoKHR rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                .renderArea(area).layerCount(1).pDepthAttachment(depth.get(0));
        vkCmdBeginRenderingKHR(commandBuffer, rendering);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        vkCmdSetViewport(commandBuffer, 0, org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                .x(0.0f).y(0.0f).width(SIZE).height(SIZE).minDepth(0.0f).maxDepth(1.0f));
        vkCmdSetScissor(commandBuffer, 0, org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE)));
        vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        vkCmdEndRenderingKHR(commandBuffer);
        this.passes++;
    }

    /** {@return the pass's counters for the verification line}. */
    public String statistics() {
        return "shadowPasses=" + this.passes + " size=" + SIZE + " failures=" + this.failures
                + (this.lastFailure.isEmpty() ? "" : " lastFailure=[" + this.lastFailure + "]");
    }

    /** Frees the pass's resources. */
    public void destroy() {
        if (this.view != VK_NULL_HANDLE) {
            vkDestroyImageView(this.context.device(), this.view, null);
            this.view = VK_NULL_HANDLE;
        }
        if (this.image != VK_NULL_HANDLE) {
            vkDestroyImage(this.context.device(), this.image, null);
            this.image = VK_NULL_HANDLE;
        }
        if (this.memory != VK_NULL_HANDLE) {
            vkFreeMemory(this.context.device(), this.memory, null);
            this.memory = VK_NULL_HANDLE;
        }
    }
}
