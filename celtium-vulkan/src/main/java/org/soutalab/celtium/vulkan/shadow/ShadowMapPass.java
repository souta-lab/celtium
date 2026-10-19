package org.soutalab.celtium.vulkan.shadow;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.LongBuffer;

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

    /** Whether the map's depth has been sampled once, and what the corner held. */
    private boolean depthSampled;
    private float sampledDepthMin = -1.0f;
    private float sampledDepthMax = -1.0f;

    private long pipeline;
    private long pipelineLayout;
    /** Draws of real terrain into the map, as opposed to the test triangle. */
    private long terrainPasses;

    private long terrainPipeline;
    private long terrainPipelineLayout;
    private long terrainModule;

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
                    // TRANSFER_SRC is what the depth readback needs: without it the transition to TRANSFER_SRC and the
                    // copy are both invalid (VUID-VkImageMemoryBarrier-oldLayout-01212 and -srcImage-00186, three
                    // messages in leg shadowDepth3).
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
                            | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
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
        if (this.passes == 2) {
            sampleDepthOnce();
        }
    }

    /**
     * Reads a small corner of the map back and records its depth range.
     * <p>
     * The counters say the pass recorded; they do not say the rasteriser wrote anything. A triangle at clip depth 0.5 over
     * the whole map must leave 0.5 everywhere in the sampled corner (and 1.0 outside it), so the range is the evidence that
     * the depth-only pipeline is writing depth, which is what the terrain draw will depend on. Run once, on the frame after
     * the first pass, so a frame's cost does not carry it.
     */
    private void sampleDepthOnce() {
        if (this.depthSampled || this.image == VK_NULL_HANDLE) {
            return;
        }
        this.depthSampled = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final int edge = 8;
            int bytes = edge * edge * 4;
            LongBuffer handle = stack.mallocLong(1);
            LongBuffer memory = stack.mallocLong(1);
            LongBuffer pool = stack.mallocLong(1);
            LongBuffer buffer = stack.mallocLong(1);
            if (vkCreateBuffer(this.context.device(), org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(bytes).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE), null, handle) != VK_SUCCESS) {
                this.lastFailure = "readback buffer creation failed";
                return;
            }
            long readback = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), readback, requirements);
            if (vkAllocateMemory(this.context.device(), org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                            "shadow map readback")), null, memory) != VK_SUCCESS) {
                vkDestroyBuffer(this.context.device(), readback, null);
                this.lastFailure = "readback memory allocation failed";
                return;
            }
            vkBindBufferMemory(this.context.device(), readback, memory.get(0), 0);
            org.lwjgl.vulkan.VkCommandPoolCreateInfo poolInfo = org.lwjgl.vulkan.VkCommandPoolCreateInfo
                    .calloc(stack).sType$Default().queueFamilyIndex(this.context.queueFamilyIndex());
            vkCreateCommandPool(this.context.device(), poolInfo, null, pool);
            org.lwjgl.vulkan.VkCommandBufferAllocateInfo allocateInfo =
                    org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                            .commandPool(pool.get(0)).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
            org.lwjgl.PointerBuffer buffers = stack.callocPointer(1);
            vkAllocateCommandBuffers(this.context.device(), allocateInfo, buffers);
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer =
                    new org.lwjgl.vulkan.VkCommandBuffer(buffers.get(0), this.context.device());
            vkBeginCommandBuffer(commandBuffer, org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default());
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().oldLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image).srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
            org.lwjgl.vulkan.VkBufferImageCopy.Buffer copy = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            copy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).mipLevel(0)
                    .baseArrayLayer(0).layerCount(1);
            copy.get(0).imageOffset().set(0, 0, 0);
            copy.get(0).imageExtent().set(edge, edge, 1);
            vkCmdCopyImageToBuffer(commandBuffer, this.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readback, copy);
            barrier.get(0).oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, 0,
                    null, null, barrier);
            vkEndCommandBuffer(commandBuffer);
            LongBuffer fence = stack.mallocLong(1);
            vkCreateFence(this.context.device(), org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack).sType$Default(),
                    null, fence);
            vkQueueSubmit(this.context.queue(),
                    org.lwjgl.vulkan.VkSubmitInfo.calloc(stack).sType$Default()
                            .pCommandBuffers(stack.pointers(commandBuffer.address())), fence.get(0));
            vkWaitForFences(this.context.device(), fence.get(0), true, Long.MAX_VALUE);
            vkDestroyFence(this.context.device(), fence.get(0), null);
            vkDestroyCommandPool(this.context.device(), pool.get(0), null);
            org.lwjgl.PointerBuffer mapped = stack.callocPointer(1);
            vkMapMemory(this.context.device(), memory.get(0), 0, bytes, 0, mapped);
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (int i = 0; i < edge * edge; i++) {
                float value = org.lwjgl.system.MemoryUtil.memGetFloat(mapped.get(0) + (long) i * 4L);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            vkUnmapMemory(this.context.device(), memory.get(0));
            this.sampledDepthMin = min;
            this.sampledDepthMax = max;
            vkFreeMemory(this.context.device(), memory.get(0), null);
            vkDestroyBuffer(this.context.device(), readback, null);
        } catch (RuntimeException | LinkageError error) {
            this.lastFailure = "depth sample: " + error;
        }
    }

    /**
     * Builds the pipeline that draws the game's terrain into the map: the terrain's own vertex module and vertex layout,
     * a push-constant range of the same size the terrain pass uses (it carries the matrices), and no fragment stage - the
     * rasteriser writes depth.
     */
    public boolean createTerrainPipeline(org.soutalab.celtium.vulkan.VulkanTerrainPipeline terrain) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            // The terrain's own pipeline layout, not a fresh one: its vertex stage reads the region uniforms through
            // descriptor set 0 (VUID-VkGraphicsPipelineCreateInfo-layout-07988 named exactly that when this created a
            // layout without them), and the descriptors the terrain pass bound remain bound in the command buffer, so the
            // shadow draw only has to bind its pipeline and push the matrices.
            this.terrainPipelineLayout = terrain.pipelineLayout();
            if (this.terrainPipelineLayout == 0L) {
                this.failures++;
                this.lastFailure = "the terrain pipeline has no layout";
                return false;
            }
            int[] formats = terrain.vertexFormats();
            int[] offsets = terrain.vertexOffsets();
            org.lwjgl.vulkan.VkVertexInputBindingDescription.Buffer bindings =
                    org.lwjgl.vulkan.VkVertexInputBindingDescription.calloc(1, stack);
            bindings.get(0).binding(0).stride(terrain.vertexStride())
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            org.lwjgl.vulkan.VkVertexInputAttributeDescription.Buffer attributes =
                    org.lwjgl.vulkan.VkVertexInputAttributeDescription.calloc(formats.length, stack);
            for (int i = 0; i < formats.length; i++) {
                attributes.get(i).location(i).binding(0).format(formats[i]).offset(offsets[i]);
            }
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(1, stack);
            // The module comes from the same checked-in SPIR-V the terrain pass loads; SpirvShaders.load hands over a
            // native buffer the caller owns, so it is freed here after the module is created.
            java.nio.ByteBuffer code = org.soutalab.celtium.vulkan.SpirvShaders.load(terrain.vertexModuleName());
            if (code == null) {
                this.failures++;
                this.lastFailure = "shader module " + terrain.vertexModuleName() + " unavailable";
                return false;
            }
            org.lwjgl.vulkan.VkShaderModuleCreateInfo moduleInfo =
                    org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            if (vkCreateShaderModule(this.context.device(), moduleInfo, null, handle) != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkCreateShaderModule(shadow terrain) failed";
                return false;
            }
            long module = handle.get(0);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(module)
                    .pName(stack.UTF8("main"));
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default().pVertexBindingDescriptions(bindings)
                    .pVertexAttributeDescriptions(attributes);
            VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default().polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE)
                    .depthBiasEnable(true).depthBiasConstantFactor(1.25f).depthBiasSlopeFactor(1.75f)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depthState = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default().depthTestEnable(true).depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
            org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamic =
                    org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                            .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
            org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR rendering =
                    org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR.calloc(stack).sType$Default()
                            .colorAttachmentCount(0).depthAttachmentFormat(VK_FORMAT_D32_SFLOAT);
            VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            info.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput)
                    .pInputAssemblyState(assembly).pViewportState(viewport).pRasterizationState(raster)
                    .pMultisampleState(multisample).pDepthStencilState(depthState).pDynamicState(dynamic)
                    .layout(this.terrainPipelineLayout).renderPass(VK_NULL_HANDLE).pNext(rendering.address());
            if (vkCreateGraphicsPipelines(this.context.device(), VK_NULL_HANDLE, info, null, handle) != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkCreateGraphicsPipelines(shadow terrain) failed";
                return false;
            }
            this.terrainPipeline = handle.get(0);
            this.terrainModule = module;
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.failures++;
            this.lastFailure = "shadow terrain pipeline: " + error;
            return false;
        }
    }

    /**
     * Records the game's terrain into the map with the light's matrices: the same vertex and index buffers the terrain pass
     * uses, the same indirect commands, and the terrain's own vertex stage, so the depth in the map is the geometry the
     * player sees rather than a test triangle.
     *
     * @param block the 32-float push block (projection then model view) the terrain vertex stage expects
     * @return whether anything was drawn
     */
    public boolean recordTerrain(VkCommandBuffer commandBuffer, MemoryStack stack, long vertexBuffer, long indexBuffer,
                                 long indirectBuffer, long indirectOffset, int indirectCount, float[] block) {
        if (this.terrainPipeline == 0L || vertexBuffer == 0L || indirectBuffer == 0L || indirectCount <= 0
                || block == null || block.length < 32) {
            return false;
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
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.terrainPipeline);
        vkCmdSetViewport(commandBuffer, 0, org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                .x(0.0f).y(0.0f).width(SIZE).height(SIZE).minDepth(0.0f).maxDepth(1.0f));
        vkCmdSetScissor(commandBuffer, 0, org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE)));
        vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(vertexBuffer), stack.longs(0L));
        if (indexBuffer != 0L) {
            vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0L, VK_INDEX_TYPE_UINT32);
        }
        java.nio.FloatBuffer push = stack.mallocFloat(32);
        for (int i = 0; i < 32; i++) {
            push.put(i, block[i]);
        }
        vkCmdPushConstants(commandBuffer, this.terrainPipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, push);
        vkCmdDrawIndexedIndirect(commandBuffer, indirectBuffer, indirectOffset, indirectCount, 20);
        vkCmdEndRenderingKHR(commandBuffer);
        this.passes++;
        this.terrainPasses++;
        return true;
    }

    /** {@return whether the terrain pipeline has been built}. */
    public boolean hasTerrainPipeline() {
        return this.terrainPipeline != 0L;
    }

    /** {@return the pass's counters for the verification line}. */
    public String statistics() {
        return "shadowPasses=" + this.passes + " terrainPasses=" + this.terrainPasses + " size=" + SIZE
                + " failures=" + this.failures + (this.lastFailure.isEmpty() ? "" : " lastFailure=[" + this.lastFailure + "]")
                + (this.sampledDepthMin < 0.0f ? "" : " depthCorner=[" + this.sampledDepthMin + ","
                        + this.sampledDepthMax + "]")
;
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
