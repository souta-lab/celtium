package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.glcompat.ComputeProgramHandle;
import org.soutalab.celtium.core.glcompat.DirectGlDrawState;
import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink;
import org.soutalab.celtium.core.glcompat.DrawCommand;
import org.soutalab.celtium.core.glcompat.IndexSpan;
import org.soutalab.celtium.core.glcompat.PrimitiveTopology;
import org.soutalab.celtium.core.glcompat.VertexBindingSpan;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.core.state.RenderStateKey;
import org.soutalab.celtium.vulkan.hal.TransientAttachmentSlab;
import org.soutalab.celtium.vulkan.hal.VulkanDynamicRendering;

/**
 * Celtium's own Vulkan 1.3 sink for the direct-GL converter lane (v0.4.0 plan section 2.3,
 * interface 3): the implementation behind {@link DirectGlVulkanSink}. It replaces the supplier's
 * 276-declaration per-function backend SPI with this one class, which owns:
 * <ul>
 *   <li>a pipeline cache keyed by the full draw state (the core {@link DirectGlDrawState} plus
 *   the converter's extended fixed state and the program's vertex layout), so equal states share
 *   one {@code VkPipeline};</li>
 *   <li>the converter's texture seat on the HAL's single texture registry - converter textures
 *   are registry textures, and a texture a mod renders into is published into the same registry
 *   as an external image, so sampling later sees the very image the draw wrote (the blackness
 *   fixes' {@code SAMPLED} invariant is carried in every attachment image's usage flags);</li>
 *   <li>custom-framebuffer attachments backed by the {@link TransientAttachmentSlab};</li>
 *   <li>buffers as persistently mapped host-visible allocations (the yardstick profiles are UMA
 *   or ReBAR; a staged profile still exposes host-visible memory for the small buffers mods
 *   use);</li>
 *   <li>per-program uniform blocks: the executor packs a program's uniform values into one block
 *   and hands it over through {@link DirectGlSinkExtensions}; the sink keeps it in a per-program
 *   uniform buffer bound at set 0, binding 0 of every converter pipeline.</li>
 * </ul>
 * Draws record into the frame's own command buffer, published per frame through
 * {@link DirectGlFrameBridge}: a draw whose target is the frame's main target continues the
 * active rendering in place (the P-5 structural-identity shortcut - no begin/end pair at all);
 * a draw into a mod framebuffer ends the main rendering, renders into the side target, and
 * resumes the main rendering with a load, so a custom FBO never hijacks the frame structure.
 * When no frame is bound the draw is counted as dropped and nothing is recorded.
 * <p>
 * Render-thread only, like everything it touches.
 */
public final class VulkanDirectGlSink implements DirectGlVulkanSink, DirectGlSinkExtensions {

    /** The converter's uniform-block convention: set 0, binding 0, one block per program. */
    static final int UNIFORM_SET = 0;
    static final int UNIFORM_BINDING = 0;

    private final VulkanContext context;
    private final VkDevice device;
    private final VulkanTextureRegistry textures;
    private final TransientAttachmentSlab attachmentSlab;

    private final Map<Long, BufferRec> buffers = new HashMap<>();
    private final Map<Long, TextureRec> textureRecs = new HashMap<>();
    private final Map<Long, FboRec> framebuffers = new HashMap<>();
    private final Map<Long, ProgramRec> programs = new HashMap<>();
    private final Map<PipelineKey, Long> pipelineCache = new HashMap<>();
    private long nextBufferId = 1L;
    private long nextFramebufferId = 1L;
    private long nextProgramHandle = 1L;

    private long uniformSetLayout;
    private long pipelineLayout;
    private long descriptorPool;
    private int descriptorPoolCapacity;

    private ExtendedFixedState extendedState = ExtendedFixedState.DEFAULT;

    public VulkanDirectGlSink(VulkanContext context, VulkanTextureRegistry textures,
                              TransientAttachmentSlab attachmentSlab) {
        this.context = context;
        this.device = context.device();
        this.textures = textures;
        this.attachmentSlab = attachmentSlab;
    }

    // -- draws ---------------------------------------------------------------

    @Override
    public void submitDraw(DirectGlDrawState state, VertexBindingSpan vertices, IndexSpan indices,
                           DrawCommand draw) {
        BackendCallCounters.noteLane3SinkCall();
        VkCommandBuffer cmd = DirectGlFrameBridge.commandBuffer();
        if (cmd == null) {
            BackendCallCounters.noteLane3DroppedDraw();
            return;
        }
        ProgramRec program = this.programs.get(state.programHandle());
        BufferRec vertexBuffer = vertices == null ? null : this.buffers.get(vertices.bufferHandle());
        if (program == null || vertexBuffer == null) {
            BackendCallCounters.noteLane3DroppedDraw();
            return;
        }
        ActiveRenderTargets main = DirectGlFrameBridge.mainTargets();
        boolean mainTarget = state.renderTargetId() == 0;
        FboRec fbo = null;
        ActiveRenderTargets targets;
        if (mainTarget) {
            targets = main;
        } else {
            fbo = this.framebuffers.get((long) state.renderTargetId());
            if (fbo == null) {
                BackendCallCounters.noteLane3DroppedDraw();
                return;
            }
            targets = fbo.targets;
        }
        long pipeline = pipelineFor(state, program, targets, vertices);
        if (pipeline == VK_NULL_HANDLE) {
            BackendCallCounters.noteLane3DroppedDraw();
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!mainTarget) {
                VulkanDynamicRendering.endRendering(cmd);
                VulkanDynamicRendering.beginRendering(cmd, stack, targets, null, 1.0f, 0);
            }
            VK10.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            applyDynamicState(cmd, stack, targets);
            if (program.descriptorSet != VK_NULL_HANDLE) {
                VK10.vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout,
                        UNIFORM_SET, stack.longs(program.descriptorSet), null);
            }
            VK10.vkCmdBindVertexBuffers(cmd, 0, stack.longs(vertexBuffer.vkBuffer),
                    stack.longs(vertices.offsetBytes()));
            if (draw.indexed()) {
                BufferRec indexBuffer = this.buffers.get(indices.bufferHandle());
                if (indexBuffer == null) {
                    if (!mainTarget) {
                        resumeMain(cmd, stack, main);
                    }
                    BackendCallCounters.noteLane3DroppedDraw();
                    return;
                }
                VK10.vkCmdBindIndexBuffer(cmd, indexBuffer.vkBuffer, indices.offsetBytes(),
                        indices.indexType() == IndexSpan.IndexType.UINT32
                                ? VK10.VK_INDEX_TYPE_UINT32 : VK10.VK_INDEX_TYPE_UINT16);
                VK10.vkCmdDrawIndexed(cmd, draw.indexCount(), draw.instanceCount(), draw.firstIndex(),
                        draw.vertexOffset(), draw.firstInstance());
            } else {
                VK10.vkCmdDraw(cmd, draw.vertexCount(), draw.instanceCount(), draw.firstVertex(),
                        draw.firstInstance());
            }
            if (!mainTarget) {
                VulkanDynamicRendering.endRendering(cmd);
                transitionFboToSampled(cmd, stack, fbo);
                resumeMain(cmd, stack, main);
            }
        }
        BackendCallCounters.noteLane3Draw();
    }

    @Override
    public void dispatchCompute(ComputeProgramHandle program, int groupsX, int groupsY, int groupsZ) {
        BackendCallCounters.noteLane3SinkCall();
        VkCommandBuffer cmd = DirectGlFrameBridge.commandBuffer();
        ProgramRec rec = program == null ? null : this.programs.get(program.handle());
        if (cmd == null || rec == null || rec.computePipeline == VK_NULL_HANDLE) {
            return;
        }
        VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, rec.computePipeline);
        if (rec.descriptorSet != VK_NULL_HANDLE) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VK10.vkCmdBindDescriptorSets(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                        this.pipelineLayout, UNIFORM_SET, stack.longs(rec.descriptorSet), null);
            }
        }
        VK10.vkCmdDispatch(cmd, groupsX, groupsY, groupsZ);
        BackendCallCounters.noteLane3Dispatch();
    }

    private void resumeMain(VkCommandBuffer cmd, MemoryStack stack, ActiveRenderTargets main) {
        if (main != null) {
            VulkanDynamicRendering.beginRendering(cmd, stack, main, null, 1.0f, 0);
        }
    }

    /** After a side-target draw, the FBO's images become shader-readable for later sampling. */
    private void transitionFboToSampled(VkCommandBuffer cmd, MemoryStack stack, FboRec fbo) {
        int count = (fbo.colorImage != VK_NULL_HANDLE ? 1 : 0) + (fbo.depthImage != VK_NULL_HANDLE ? 1 : 0);
        if (count == 0) {
            return;
        }
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(count, stack);
        int index = 0;
        if (fbo.colorImage != VK_NULL_HANDLE) {
            VkImageMemoryBarrier2 barrier = barriers.get(index++);
            barrier.sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .image(fbo.colorImage);
            barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
        }
        if (fbo.depthImage != VK_NULL_HANDLE) {
            VkImageMemoryBarrier2 barrier = barriers.get(index++);
            barrier.sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .image(fbo.depthImage);
            barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
        }
        org.lwjgl.vulkan.VkDependencyInfo dependency = org.lwjgl.vulkan.VkDependencyInfo.calloc(stack)
                .sType$Default().pImageMemoryBarriers(barriers);
        VK13.vkCmdPipelineBarrier2(cmd, dependency);
        BackendCallCounters.noteBarrier();
    }

    private void applyDynamicState(VkCommandBuffer cmd, MemoryStack stack, ActiveRenderTargets targets) {
        VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
        viewport.get(0)
                .x(this.extendedState.viewportX()).y(this.extendedState.viewportY())
                .width(this.extendedState.viewportWidth() > 0
                        ? this.extendedState.viewportWidth() : targets.width())
                .height(this.extendedState.viewportHeight() > 0
                        ? this.extendedState.viewportHeight() : targets.height())
                .minDepth(0.0f).maxDepth(1.0f);
        VK10.vkCmdSetViewport(cmd, 0, viewport);
        int scissorWidth = this.extendedState.scissorWidth() > 0
                ? this.extendedState.scissorWidth() : targets.width();
        int scissorHeight = this.extendedState.scissorHeight() > 0
                ? this.extendedState.scissorHeight() : targets.height();
        VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
        scissor.get(0).offset().set(this.extendedState.scissorX(), this.extendedState.scissorY());
        scissor.get(0).extent().set(scissorWidth, scissorHeight);
        VK10.vkCmdSetScissor(cmd, 0, scissor);
        VK10.vkCmdSetLineWidth(cmd, Math.max(1.0f, this.extendedState.lineWidth()));
    }

    // -- pipeline cache --------------------------------------------------------

    private record PipelineKey(DirectGlDrawState state, long extendedHash, long vertexLayoutHash,
                               int colorFormat, int depthFormat) {
    }

    private long pipelineFor(DirectGlDrawState state, ProgramRec program, ActiveRenderTargets targets,
                             VertexBindingSpan vertices) {
        int colorFormat = targets.colorAttachments().isEmpty()
                ? VK10.VK_FORMAT_UNDEFINED
                : targets.colorAttachments().get(0).format();
        int depthFormat = targets.depthAttachment() == null
                ? VK10.VK_FORMAT_UNDEFINED : targets.depthAttachment().format();
        long layoutHash = program.vertexLayoutHash ^ (vertices == null ? 0L : vertices.strideBytes());
        PipelineKey key = new PipelineKey(state, this.extendedState.hash(), layoutHash, colorFormat, depthFormat);
        Long cached = this.pipelineCache.get(key);
        if (cached != null) {
            return cached;
        }
        long created = createPipeline(state, program, targets, vertices, colorFormat, depthFormat);
        if (created != VK_NULL_HANDLE) {
            this.pipelineCache.put(key, created);
        }
        return created;
    }

    private long createPipeline(DirectGlDrawState state, ProgramRec program, ActiveRenderTargets targets,
                                VertexBindingSpan vertices, int colorFormat, int depthFormat) {
        ensurePipelineLayout();
        RenderStateKey key = state.renderState();
        ExtendedFixedState ext = this.extendedState;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(program.vertexModule).pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(program.fragmentModule).pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer bindings = null;
            VkVertexInputAttributeDescription.Buffer attributes = null;
            if (vertices != null && program.attribs.length > 0) {
                bindings = VkVertexInputBindingDescription.calloc(1, stack);
                bindings.get(0).binding(0).stride(vertices.strideBytes())
                        .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
                attributes = VkVertexInputAttributeDescription.calloc(program.attribs.length, stack);
                int offset = 0;
                for (int i = 0; i < program.attribs.length; i++) {
                    AttribDesc attrib = program.attribs[i];
                    attributes.get(i).location(attrib.location).binding(0)
                            .format(attrib.vkFormat()).offset(offset);
                    offset += attrib.byteSize();
                }
            }
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo
                    .calloc(stack).sType$Default()
                    .pVertexBindingDescriptions(bindings).pVertexAttributeDescriptions(attributes);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo
                    .calloc(stack).sType$Default().topology(topologyOf(state.topology()));
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo
                    .calloc(stack).sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo
                    .calloc(stack).sType$Default()
                    .polygonMode(polygonOf(key.polygonMode()))
                    .cullMode(cullOf(key.cullMode()))
                    .frontFace(ext.frontFaceCcw() ? VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE
                            : VK10.VK_FRONT_FACE_CLOCKWISE)
                    .lineWidth(1.0f)
                    .depthBiasEnable(ext.polygonOffsetFactor() != 0.0f || ext.polygonOffsetUnits() != 0.0f)
                    .depthBiasConstantFactor(ext.polygonOffsetUnits())
                    .depthBiasSlopeFactor(ext.polygonOffsetFactor());
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo
                    .calloc(stack).sType$Default().rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);
            org.lwjgl.vulkan.VkStencilOpState stencilFront = org.lwjgl.vulkan.VkStencilOpState
                    .calloc(stack)
                    .failOp(ext.stencilFail()).passOp(ext.stencilPass())
                    .depthFailOp(ext.stencilDepthFail()).compareOp(ext.stencilFunc())
                    .compareMask(ext.stencilFuncMask()).writeMask(ext.stencilWriteMask())
                    .reference(ext.stencilRef());
            org.lwjgl.vulkan.VkStencilOpState stencilBack = org.lwjgl.vulkan.VkStencilOpState
                    .calloc(stack)
                    .failOp(ext.stencilFail()).passOp(ext.stencilPass())
                    .depthFailOp(ext.stencilDepthFail()).compareOp(ext.stencilFunc())
                    .compareMask(ext.stencilFuncMask()).writeMask(ext.stencilWriteMask())
                    .reference(ext.stencilRef());
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo
                    .calloc(stack).sType$Default()
                    .depthTestEnable(key.depthTestEnabled())
                    .depthWriteEnable(key.depthWriteEnabled())
                    .depthCompareOp(ext.depthFunc())
                    .stencilTestEnable(key.stencilTestEnabled())
                    .front(stencilFront)
                    .back(stencilBack);
            VkPipelineColorBlendAttachmentState.Buffer attachment = null;
            VkPipelineColorBlendStateCreateInfo blending = VkPipelineColorBlendStateCreateInfo
                    .calloc(stack).sType$Default()
                    .logicOpEnable(key.logicOpEnabled()).logicOp(ext.logicOp());
            if (colorFormat != VK10.VK_FORMAT_UNDEFINED) {
                attachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
                attachment.get(0)
                        .blendEnable(key.blendEnabled())
                        .srcColorBlendFactor(ext.srcRgb()).dstColorBlendFactor(ext.dstRgb())
                        .colorBlendOp(ext.blendEquationRgb())
                        .srcAlphaBlendFactor(ext.srcAlpha()).dstAlphaBlendFactor(ext.dstAlpha())
                        .alphaBlendOp(ext.blendEquationAlpha())
                        .colorWriteMask(key.colorWriteMask());
                blending.pAttachments(attachment);
            }
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default().pDynamicStates(stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT,
                            VK10.VK_DYNAMIC_STATE_SCISSOR, VK10.VK_DYNAMIC_STATE_LINE_WIDTH));
            VkPipelineRenderingCreateInfo rendering = VkPipelineRenderingCreateInfo.calloc(stack)
                    .sType$Default()
                    .colorAttachmentCount(colorFormat == VK10.VK_FORMAT_UNDEFINED ? 0 : 1)
                    .depthAttachmentFormat(depthFormat);
            if (colorFormat != VK10.VK_FORMAT_UNDEFINED) {
                rendering.pColorAttachmentFormats(stack.ints(colorFormat));
            }
            VkGraphicsPipelineCreateInfo.Buffer createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            createInfo.get(0).sType$Default()
                    .pNext(rendering.address())
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(raster)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(blending)
                    .pDynamicState(dynamic)
                    .layout(this.pipelineLayout);
            // VulkanContext's wrapper supplies the persistent VkPipelineCache (and the
            // CELTIUM_PIPELINE_CREATE identity line) - the converter's pipelines ride it.
            LongBuffer pipelines = stack.longs(0);
            int result = VulkanContext.createGraphicsPipelines(this.device, createInfo, pipelines);
            if (result != VK_SUCCESS) {
                return VK_NULL_HANDLE;
            }
            return pipelines.get(0);
        }
    }

    private static int topologyOf(PrimitiveTopology topology) {
        return switch (topology) {
            case POINTS -> VK10.VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
            case LINES -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            case LINE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
            case TRIANGLES -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            case TRIANGLE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            case TRIANGLE_FAN -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
        };
    }

    private static int polygonOf(int mode) {
        return switch (mode) {
            case RenderStateKey.POLYGON_LINE -> VK10.VK_POLYGON_MODE_LINE;
            case RenderStateKey.POLYGON_POINT -> VK10.VK_POLYGON_MODE_POINT;
            default -> VK10.VK_POLYGON_MODE_FILL;
        };
    }

    private static int cullOf(int mode) {
        return switch (mode) {
            case RenderStateKey.CULL_BACK -> VK10.VK_CULL_MODE_BACK_BIT;
            case RenderStateKey.CULL_FRONT -> VK10.VK_CULL_MODE_FRONT_BIT;
            case RenderStateKey.CULL_FRONT_AND_BACK -> VK10.VK_CULL_MODE_FRONT_AND_BACK;
            default -> VK10.VK_CULL_MODE_NONE;
        };
    }

    private void ensurePipelineLayout() {
        if (this.pipelineLayout != VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack);
            binding.get(0).binding(UNIFORM_BINDING).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default().pBindings(binding);
            LongBuffer layout = stack.longs(0);
            check(VK10.vkCreateDescriptorSetLayout(this.device, layoutInfo, null, layout),
                    "vkCreateDescriptorSetLayout(converter uniforms)");
            this.uniformSetLayout = layout.get(0);
            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default().pSetLayouts(stack.longs(this.uniformSetLayout));
            LongBuffer handle = stack.longs(0);
            check(VK10.vkCreatePipelineLayout(this.device, pipelineLayoutInfo, null, handle),
                    "vkCreatePipelineLayout(converter)");
            this.pipelineLayout = handle.get(0);
        }
    }

    private void ensureDescriptorPool(int sets) {
        if (this.descriptorPool != VK_NULL_HANDLE && this.descriptorPoolCapacity >= sets) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (this.descriptorPool != VK_NULL_HANDLE) {
                VK10.vkDestroyDescriptorPool(this.device, this.descriptorPool, null);
                this.descriptorPool = VK_NULL_HANDLE;
            }
            int capacity = Math.max(64, sets * 2);
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(capacity);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(capacity).pPoolSizes(sizes);
            LongBuffer handle = stack.longs(0);
            check(VK10.vkCreateDescriptorPool(this.device, poolInfo, null, handle),
                    "vkCreateDescriptorPool(converter)");
            this.descriptorPool = handle.get(0);
            this.descriptorPoolCapacity = capacity;
        }
    }

    // -- DirectGlSinkExtensions -------------------------------------------------

    @Override
    public void setExtendedFixedState(ExtendedFixedState state) {
        this.extendedState = state == null ? ExtendedFixedState.DEFAULT : state;
    }

    @Override
    public void registerProgramAttribs(long programHandle, AttribDesc[] attribs) {
        ProgramRec rec = this.programs.get(programHandle);
        if (rec != null) {
            rec.attribs = attribs == null ? new AttribDesc[0] : attribs.clone();
            long hash = 0x9E3779B97F4A7C15L;
            for (AttribDesc attrib : rec.attribs) {
                hash = (hash ^ attrib.location()) * 0xBF58476D1CE4E5B9L;
                hash = (hash ^ attrib.vkFormat()) * 0x94D049BB133111EBL;
            }
            rec.vertexLayoutHash = hash;
        }
    }

    @Override
    public void updateUniforms(long programHandle, MemorySegment values) {
        ProgramRec rec = this.programs.get(programHandle);
        if (rec == null || values == null || values.byteSize() == 0L) {
            return;
        }
        ensurePipelineLayout();
        if (rec.uniformBuffer == VK_NULL_HANDLE
                || rec.uniformBufferSize < values.byteSize()) {
            if (rec.uniformBuffer != VK_NULL_HANDLE) {
                destroyBufferRaw(rec.uniformBuffer, rec.uniformMemory);
            }
            long size = Math.max(256L, values.byteSize());
            long[] created = createBufferRaw(size, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
            rec.uniformBuffer = created[0];
            rec.uniformMemory = created[1];
            rec.uniformBufferSize = size;
            rec.uniformMapped = mapMemory(rec.uniformMemory, size);
        }
        MemorySegment.copy(values, 0L, MemorySegment.ofAddress(rec.uniformMapped)
                .reinterpret(values.byteSize()), 0L, values.byteSize());
        if (rec.descriptorSet == VK_NULL_HANDLE) {
            ensureDescriptorPool(this.programs.size());
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetAllocateInfo alloc = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default().descriptorPool(this.descriptorPool)
                        .pSetLayouts(stack.longs(this.uniformSetLayout));
                LongBuffer set = stack.longs(0);
                check(VK10.vkAllocateDescriptorSets(this.device, alloc, set),
                        "vkAllocateDescriptorSets(converter)");
                rec.descriptorSet = set.get(0);
            }
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0).buffer(rec.uniformBuffer).offset(0L).range(values.byteSize());
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0).sType$Default().dstSet(rec.descriptorSet).dstBinding(UNIFORM_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).pBufferInfo(bufferInfo);
            VK10.vkUpdateDescriptorSets(this.device, write, null);
        }
    }

    // -- texture ops -------------------------------------------------------------

    private final TextureOps textureOps = new TextureOps() {
        @Override
        public long createTexture2D(int width, int height, int format, int mipLevels) {
            BackendCallCounters.noteLane3SinkCall();
            ByteBuffer zeros = ByteBuffer.allocateDirect(Math.max(4, width * height * 4));
            int id = VulkanDirectGlSink.this.textures.createTexture2d(width, height, false, zeros);
            TextureRec rec = new TextureRec();
            rec.width = width;
            rec.height = height;
            rec.shadow = new byte[width * height * 4];
            VulkanDirectGlSink.this.textureRecs.put((long) id, rec);
            return id;
        }

        @Override
        public void writeTexture(long textureId, int level, int x, int y, int width, int height,
                                 MemorySegment pixels) {
            BackendCallCounters.noteLane3SinkCall();
            TextureRec rec = VulkanDirectGlSink.this.textureRecs.get(textureId);
            VulkanGlTexture texture = VulkanDirectGlSink.this.textures.existing((int) textureId);
            if (rec == null || texture == null || pixels == null) {
                return;
            }
            if (level == 0) {
                // Region writes land in the CPU shadow and the whole level is re-uploaded: the
                // registry's upload entry is whole-image, and converter textures are small.
                int rowBytes = width * 4;
                for (int row = 0; row < height; row++) {
                    int dst = ((y + row) * rec.width + x) * 4;
                    int src = row * rowBytes;
                    if (dst >= 0 && dst + rowBytes <= rec.shadow.length
                            && src + rowBytes <= pixels.byteSize()) {
                        pixels.asSlice(src, rowBytes).asByteBuffer().get(rec.shadow, dst, rowBytes);
                    }
                }
                ByteBuffer upload = ByteBuffer.allocateDirect(rec.shadow.length);
                upload.put(rec.shadow).flip();
                texture.uploadFromNamedImage((int) textureId, rec.width, rec.height, upload);
            }
        }

        @Override
        public void destroyTexture(long textureId) {
            BackendCallCounters.noteLane3SinkCall();
            VulkanDirectGlSink.this.textureRecs.remove(textureId);
            VulkanGlTexture texture = VulkanDirectGlSink.this.textures.existing((int) textureId);
            if (texture != null) {
                texture.delete();
            }
        }
    };

    @Override
    public TextureOps textureOps() {
        return this.textureOps;
    }

    // -- buffer ops ---------------------------------------------------------------

    private final BufferOps bufferOps = new BufferOps() {
        @Override
        public long createBuffer(long sizeBytes, int usageFlags) {
            BackendCallCounters.noteLane3SinkCall();
            long[] created = createBufferRaw(Math.max(16L, sizeBytes),
                    usageFlags | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT
                            | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
            long id = VulkanDirectGlSink.this.nextBufferId++;
            BufferRec rec = new BufferRec();
            rec.vkBuffer = created[0];
            rec.memory = created[1];
            rec.size = Math.max(16L, sizeBytes);
            rec.mapped = mapMemory(rec.memory, rec.size);
            VulkanDirectGlSink.this.buffers.put(id, rec);
            return id;
        }

        @Override
        public void writeBuffer(long bufferId, long offsetBytes, MemorySegment data) {
            BackendCallCounters.noteLane3SinkCall();
            BufferRec rec = VulkanDirectGlSink.this.buffers.get(bufferId);
            if (rec == null || data == null || rec.mapped == 0L) {
                return;
            }
            long writable = Math.min(data.byteSize(), rec.size - offsetBytes);
            if (writable <= 0L) {
                return;
            }
            MemorySegment.copy(data, 0L, MemorySegment.ofAddress(rec.mapped).reinterpret(rec.size),
                    offsetBytes, writable);
        }

        @Override
        public void destroyBuffer(long bufferId) {
            BackendCallCounters.noteLane3SinkCall();
            BufferRec rec = VulkanDirectGlSink.this.buffers.remove(bufferId);
            if (rec != null) {
                destroyBufferRaw(rec.vkBuffer, rec.memory);
            }
        }
    };

    @Override
    public BufferOps bufferOps() {
        return this.bufferOps;
    }

    private long[] createBufferRaw(long size, int usage) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(size).usage(usage).sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer buffer = stack.longs(0);
            check(VK10.vkCreateBuffer(this.device, bufferInfo, null, buffer), "vkCreateBuffer(converter)");
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            VK10.vkGetBufferMemoryRequirements(this.device, buffer.get(0), requirements);
            int type = this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    "converter buffer");
            if (type < 0) {
                type = this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, "converter buffer (host-visible)");
            }
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size()).memoryTypeIndex(type);
            LongBuffer memory = stack.longs(0);
            check(VulkanContext.allocateMemory(this.device, allocInfo, memory,
                            "direct-GL converter buffer"),
                    "vkAllocateMemory(converter buffer)");
            check(VK10.vkBindBufferMemory(this.device, buffer.get(0), memory.get(0), 0L),
                    "vkBindBufferMemory(converter)");
            return new long[] {buffer.get(0), memory.get(0)};
        }
    }

    private long mapMemory(long memory, long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer data = stack.callocPointer(1);
            check(VK10.vkMapMemory(this.device, memory, 0L, size, 0, data), "vkMapMemory(converter)");
            return data.get(0);
        }
    }

    private void destroyBufferRaw(long buffer, long memory) {
        if (buffer != VK_NULL_HANDLE) {
            VK10.vkDestroyBuffer(this.device, buffer, null);
        }
        if (memory != VK_NULL_HANDLE) {
            VulkanContext.freeMemory(this.device, memory);
        }
    }

    // -- framebuffer ops ------------------------------------------------------------

    private final FramebufferOps framebufferOps = new FramebufferOps() {
        @Override
        public long createFramebuffer(long[] colorTextureIds, long depthTextureId, int width, int height) {
            BackendCallCounters.noteLane3SinkCall();
            FboRec rec = new FboRec();
            rec.width = width;
            rec.height = height;
            ActiveRenderTargets.RenderTargetView colorView = null;
            if (colorTextureIds != null && colorTextureIds.length > 0 && colorTextureIds[0] != 0L) {
                long textureId = colorTextureIds[0];
                VulkanGlTexture existing = VulkanDirectGlSink.this.textures.existing((int) textureId);
                if (existing != null && existing.view() != VK_NULL_HANDLE) {
                    rec.colorImage = existing.image();
                    colorView = new ActiveRenderTargets.RenderTargetView(existing.view(),
                            VK_FORMAT_R8G8B8A8_UNORM, ActiveRenderTargets.LoadOp.LOAD,
                            ActiveRenderTargets.StoreOp.STORE);
                } else {
                    AttachmentImage attachment = createAttachmentImage(width, height,
                            VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT, false);
                    rec.colorImage = attachment.image;
                    rec.colorView = attachment.view;
                    rec.colorAllocation = attachment.allocation;
                    rec.colorPublished = true;
                    colorView = new ActiveRenderTargets.RenderTargetView(attachment.view,
                            VK_FORMAT_R8G8B8A8_UNORM, ActiveRenderTargets.LoadOp.LOAD,
                            ActiveRenderTargets.StoreOp.STORE);
                    VulkanDirectGlSink.this.textures.publishExternalImage((int) textureId,
                            attachment.image, attachment.view, width, height);
                }
            }
            ActiveRenderTargets.RenderTargetView depthView = null;
            if (depthTextureId != 0L) {
                VulkanGlTexture existing = VulkanDirectGlSink.this.textures.existing((int) depthTextureId);
                if (existing != null && existing.view() != VK_NULL_HANDLE) {
                    rec.depthImage = existing.image();
                    depthView = new ActiveRenderTargets.RenderTargetView(existing.view(),
                            VK_FORMAT_D32_SFLOAT, ActiveRenderTargets.LoadOp.LOAD,
                            ActiveRenderTargets.StoreOp.STORE);
                } else {
                    AttachmentImage attachment = createAttachmentImage(width, height, VK_FORMAT_D32_SFLOAT,
                            VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, true);
                    rec.depthImage = attachment.image;
                    rec.depthView = attachment.view;
                    rec.depthAllocation = attachment.allocation;
                    rec.depthPublished = true;
                    depthView = new ActiveRenderTargets.RenderTargetView(attachment.view,
                            VK_FORMAT_D32_SFLOAT, ActiveRenderTargets.LoadOp.LOAD,
                            ActiveRenderTargets.StoreOp.STORE);
                    VulkanDirectGlSink.this.textures.publishExternalDepthImage((int) depthTextureId,
                            attachment.image, attachment.view, width, height,
                            VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                }
            }
            rec.targets = new ActiveRenderTargets(
                    colorView == null ? java.util.List.of() : java.util.List.of(colorView),
                    depthView, width, height);
            long id = VulkanDirectGlSink.this.nextFramebufferId++;
            VulkanDirectGlSink.this.framebuffers.put(id, rec);
            return id;
        }

        @Override
        public void destroyFramebuffer(long framebufferId) {
            BackendCallCounters.noteLane3SinkCall();
            FboRec rec = VulkanDirectGlSink.this.framebuffers.remove(framebufferId);
            if (rec == null) {
                return;
            }
            // Attachments published into the registry are owned by the registry from the
            // publish on (its unpublish/delete path destroys image and view); the slab range
            // underneath is still this sink's, and returns either way. Unpublished attachments
            // (none today - every sink-created attachment is published) would be destroyed here.
            if (rec.colorAllocation != null) {
                if (!rec.colorPublished) {
                    VK10.vkDestroyImageView(VulkanDirectGlSink.this.device, rec.colorView, null);
                    VK10.vkDestroyImage(VulkanDirectGlSink.this.device, rec.colorImage, null);
                }
                VulkanDirectGlSink.this.attachmentSlab.release(rec.colorAllocation);
            }
            if (rec.depthAllocation != null) {
                if (!rec.depthPublished) {
                    VK10.vkDestroyImageView(VulkanDirectGlSink.this.device, rec.depthView, null);
                    VK10.vkDestroyImage(VulkanDirectGlSink.this.device, rec.depthImage, null);
                }
                VulkanDirectGlSink.this.attachmentSlab.release(rec.depthAllocation);
            }
        }
    };

    @Override
    public FramebufferOps framebufferOps() {
        return this.framebufferOps;
    }

    /** One sink-created attachment: its image, its view, and the slab range underneath. */
    private record AttachmentImage(long image, long view, TransientAttachmentSlab.Allocation allocation) {
    }

    /**
     * Creates one attachment image whose memory is an aligned range of the transient-attachment
     * slab. Usage always includes {@code SAMPLED} - the blackness fixes' invariant: an image a
     * mod renders into must stay sampleable, or the borrow that presents it goes black.
     */
    private AttachmentImage createAttachmentImage(int width, int height, int format, int usage, boolean depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1).arrayLayers(1)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().width(width).height(height).depth(1);
            LongBuffer image = stack.longs(0);
            check(VK10.vkCreateImage(this.device, imageInfo, null, image), "vkCreateImage(converter fbo)");
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            VK10.vkGetImageMemoryRequirements(this.device, image.get(0), requirements);
            TransientAttachmentSlab.Allocation allocation =
                    this.attachmentSlab.acquire(requirements.size(), requirements.alignment());
            check(VK10.vkBindImageMemory(this.device, image.get(0),
                    this.attachmentSlab.memoryHandle(), allocation.offset()),
                    "vkBindImageMemory(converter fbo)");
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image.get(0)).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D).format(format);
            viewInfo.subresourceRange()
                    .aspectMask(depth ? VK10.VK_IMAGE_ASPECT_DEPTH_BIT : VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .levelCount(1).layerCount(1);
            LongBuffer view = stack.longs(0);
            check(VK10.vkCreateImageView(this.device, viewInfo, null, view),
                    "vkCreateImageView(converter fbo)");
            return new AttachmentImage(image.get(0), view.get(0), allocation);
        }
    }

    // -- shader ops -------------------------------------------------------------------

    private final ShaderOps shaderOps = new ShaderOps() {
        @Override
        public long createProgram(SpirvModuleBundle bundle) {
            BackendCallCounters.noteLane3SinkCall();
            ProgramRec rec = new ProgramRec();
            rec.vertexModule = createShaderModule(bundle.vertexWords());
            rec.fragmentModule = createShaderModule(bundle.fragmentWords());
            long handle = VulkanDirectGlSink.this.nextProgramHandle++;
            VulkanDirectGlSink.this.programs.put(handle, rec);
            return handle;
        }

        @Override
        public ComputeProgramHandle createComputeProgram(SpirvModuleBundle bundle) {
            BackendCallCounters.noteLane3SinkCall();
            ProgramRec rec = new ProgramRec();
            rec.vertexModule = createShaderModule(bundle.vertexWords());
            ensurePipelineLayout();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default().stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(rec.vertexModule).pName(stack.UTF8("main"));
                org.lwjgl.vulkan.VkComputePipelineCreateInfo.Buffer createInfo =
                        org.lwjgl.vulkan.VkComputePipelineCreateInfo.calloc(1, stack);
                createInfo.get(0).sType$Default().stage(stage).layout(
                        VulkanDirectGlSink.this.pipelineLayout);
                PointerBuffer pipelines = stack.callocPointer(1);
                int result = VK10.vkCreateComputePipelines(VulkanDirectGlSink.this.device,
                        VK_NULL_HANDLE, createInfo, null, pipelines);
                if (result == VK_SUCCESS) {
                    rec.computePipeline = pipelines.get(0);
                }
            }
            long handle = VulkanDirectGlSink.this.nextProgramHandle++;
            VulkanDirectGlSink.this.programs.put(handle, rec);
            return new ComputeProgramHandle(handle);
        }

        @Override
        public void destroyProgram(long programHandle) {
            BackendCallCounters.noteLane3SinkCall();
            ProgramRec rec = VulkanDirectGlSink.this.programs.remove(programHandle);
            if (rec == null) {
                return;
            }
            if (rec.vertexModule != VK_NULL_HANDLE) {
                VK10.vkDestroyShaderModule(VulkanDirectGlSink.this.device, rec.vertexModule, null);
            }
            if (rec.fragmentModule != VK_NULL_HANDLE) {
                VK10.vkDestroyShaderModule(VulkanDirectGlSink.this.device, rec.fragmentModule, null);
            }
            if (rec.computePipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(VulkanDirectGlSink.this.device, rec.computePipeline, null);
            }
            if (rec.uniformBuffer != VK_NULL_HANDLE) {
                destroyBufferRaw(rec.uniformBuffer, rec.uniformMemory);
            }
        }
    };

    @Override
    public ShaderOps shaderOps() {
        return this.shaderOps;
    }

    private long createShaderModule(int[] words) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer code = stack.calloc(words.length * Integer.BYTES);
            for (int word : words) {
                code.putInt(word);
            }
            code.flip();
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default().pCode(code);
            LongBuffer module = stack.longs(0);
            check(VK10.vkCreateShaderModule(this.device, moduleInfo, null, module),
                    "vkCreateShaderModule(converter)");
            return module.get(0);
        }
    }

    private void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with Vulkan result " + result);
        }
    }

    // -- records ------------------------------------------------------------------------

    private static final class BufferRec {
        long vkBuffer;
        long memory;
        long size;
        long mapped;
    }

    private static final class TextureRec {
        int width;
        int height;
        byte[] shadow;
    }

    private static final class FboRec {
        ActiveRenderTargets targets;
        long colorImage;
        long depthImage;
        long colorView;
        long depthView;
        TransientAttachmentSlab.Allocation colorAllocation;
        TransientAttachmentSlab.Allocation depthAllocation;
        boolean colorPublished;
        boolean depthPublished;
        int width;
        int height;
    }

    private static final class ProgramRec {
        long vertexModule;
        long fragmentModule;
        long computePipeline;
        AttribDesc[] attribs = new AttribDesc[0];
        long vertexLayoutHash = 0x9E3779B97F4A7C15L;
        long uniformBuffer;
        long uniformMemory;
        long uniformBufferSize;
        long uniformMapped;
        long descriptorSet;
    }
}
