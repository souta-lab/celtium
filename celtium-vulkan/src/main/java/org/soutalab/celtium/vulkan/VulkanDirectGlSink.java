package org.soutalab.celtium.vulkan;

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
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearRect;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkMemoryBarrier2;
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
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkStencilOpState;
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
 *   <li>buffers as {@link VulkanHostBuffer}s - persistently mapped host-visible buffers backed
 *   by sub-allocations of the context's shared host-visible slabs, so a converter buffer never
 *   issues its own {@code vkAllocateMemory} (plan section 2.4: zero per-buffer allocations at
 *   runtime) and a delete hands the slab range back as a timeline-deferred release instead of
 *   freeing memory under a possibly in-flight submission;</li>
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
    /**
     * Every descriptor pool created for program uniform sets, oldest first. Pools are only ever
     * appended, never destroyed or replaced while the sink lives: a set allocated from an earlier
     * pool stays valid for the pool's whole life, so growing past one pool's capacity must not
     * invalidate the sets programs already hold (audit #16 F-1).
     */
    private final List<Long> descriptorPools = new ArrayList<>();
    /** How many sets the newest pool can still hand out / has handed out. */
    private int descriptorPoolCapacity;
    private int descriptorPoolUsed;

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
        if (targets == null) {
            BackendCallCounters.noteLane3DroppedDraw();
            return;
        }
        long pipeline = pipelineFor(state, program, targets, vertices);
        if (pipeline == VK_NULL_HANDLE) {
            BackendCallCounters.noteLane3DroppedDraw();
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!mainTarget) {
                suspendMain(cmd, main);
                beginSideRendering(cmd, stack, fbo);
            }
            VK10.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            applyDynamicState(cmd, stack, targets);
            if (program.descriptorSet != VK_NULL_HANDLE) {
                VK10.vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout,
                        UNIFORM_SET, stack.longs(program.descriptorSet), null);
            }
            VK10.vkCmdBindVertexBuffers(cmd, 0, stack.longs(vertexBuffer.buffer.buffer()),
                    stack.longs(vertices.offsetBytes()));
            if (draw.indexed()) {
                BufferRec indexBuffer = this.buffers.get(indices.bufferHandle());
                if (indexBuffer == null) {
                    if (!mainTarget) {
                        finishSideRendering(cmd, stack, fbo, main);
                    }
                    BackendCallCounters.noteLane3DroppedDraw();
                    return;
                }
                VK10.vkCmdBindIndexBuffer(cmd, indexBuffer.buffer.buffer(), indices.offsetBytes(),
                        indices.indexType() == IndexSpan.IndexType.UINT32
                                ? VK10.VK_INDEX_TYPE_UINT32 : VK10.VK_INDEX_TYPE_UINT16);
                VK10.vkCmdDrawIndexed(cmd, draw.indexCount(), draw.instanceCount(), draw.firstIndex(),
                        draw.vertexOffset(), draw.firstInstance());
            } else {
                VK10.vkCmdDraw(cmd, draw.vertexCount(), draw.instanceCount(), draw.firstVertex(),
                        draw.firstInstance());
            }
            if (!mainTarget) {
                finishSideRendering(cmd, stack, fbo, main);
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

    private static void suspendMain(VkCommandBuffer cmd, ActiveRenderTargets main) {
        if (main != null) {
            VulkanDynamicRendering.endRendering(cmd);
        }
    }

    private void resumeMain(VkCommandBuffer cmd, MemoryStack stack, ActiveRenderTargets main) {
        if (main == null) {
            return;
        }
        // LOAD in the next rendering instance must see the previous instance's STORE.
        long stages = VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT
                | VK13.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT;
        VkMemoryBarrier2.Buffer barriers = VkMemoryBarrier2.calloc(1, stack);
        barriers.get(0).sType$Default().srcStageMask(stages).dstStageMask(stages)
                .srcAccessMask(VK13.VK_ACCESS_2_MEMORY_WRITE_BIT)
                .dstAccessMask(VK13.VK_ACCESS_2_MEMORY_READ_BIT | VK13.VK_ACCESS_2_MEMORY_WRITE_BIT);
        VK13.vkCmdPipelineBarrier2(cmd, VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barriers));
        BackendCallCounters.noteBarrier();

        ActiveRenderTargets resumed = new ActiveRenderTargets(
                main.colorAttachments().stream().map(VulkanDirectGlSink::loadView).toList(),
                main.depthAttachment() == null ? null : loadView(main.depthAttachment()),
                main.width(), main.height());
        DirectGlFrameBridge.MainImages images = DirectGlFrameBridge.mainImages();
        VulkanDynamicRendering.beginRendering(cmd, stack, resumed, null, 1.0f, 0,
                images == null ? VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL : images.colorLayout(),
                images == null ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL : images.depthLayout(),
                images != null && images.stencilAttached());
    }

    private static ActiveRenderTargets.RenderTargetView loadView(ActiveRenderTargets.RenderTargetView view) {
        return new ActiveRenderTargets.RenderTargetView(view.imageViewHandle(), view.format(),
                ActiveRenderTargets.LoadOp.LOAD, view.storeOp());
    }

    private static void beginSideRendering(VkCommandBuffer cmd, MemoryStack stack, FboRec fbo) {
        int depthLayout = restingLayout(cmd, fbo.depth);
        transition(cmd, stack, fbo.color, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        transition(cmd, stack, fbo.depth, depthLayout, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        ActiveRenderTargets targets = fbo.targets;
        if (fbo.depth != null && depthLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
            ActiveRenderTargets.RenderTargetView depth = targets.depthAttachment();
            targets = new ActiveRenderTargets(targets.colorAttachments(),
                    new ActiveRenderTargets.RenderTargetView(depth.imageViewHandle(), depth.format(),
                            ActiveRenderTargets.LoadOp.DONT_CARE, depth.storeOp()), targets.width(), targets.height());
        }
        VulkanDynamicRendering.beginRendering(cmd, stack, targets, null, 1.0f, 0,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                hasStencil(fbo.depth));
    }

    private void finishSideRendering(VkCommandBuffer cmd, MemoryStack stack, FboRec fbo, ActiveRenderTargets main) {
        VulkanDynamicRendering.endRendering(cmd);
        transition(cmd, stack, fbo.color, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        transition(cmd, stack, fbo.depth, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        resumeMain(cmd, stack, main);
    }

    private static void transition(VkCommandBuffer cmd, MemoryStack stack, FramebufferImage image,
                                   int oldLayout, int newLayout) {
        if (image == null) {
            return;
        }
        // Include attachment LOAD/STORE, shader sampling, and transfer commands in the dependency.
        long memoryAccess = VK13.VK_ACCESS_2_MEMORY_READ_BIT | VK13.VK_ACCESS_2_MEMORY_WRITE_BIT;
        try (MemoryStack barrierStack = stack.push()) {
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, barrierStack);
            barriers.get(0).sType$Default()
                    .srcStageMask(oldLayout == VK_IMAGE_LAYOUT_UNDEFINED ? 0 : VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                    .srcAccessMask(oldLayout == VK_IMAGE_LAYOUT_UNDEFINED ? 0 : memoryAccess)
                    .dstStageMask(VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT).dstAccessMask(memoryAccess)
                    .oldLayout(oldLayout).newLayout(newLayout)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image.handle())
                    .subresourceRange().set(image.aspects(), 0, 1, 0, 1);
            VK13.vkCmdPipelineBarrier2(cmd,
                    VkDependencyInfo.calloc(barrierStack).sType$Default().pImageMemoryBarriers(barriers));
        }
        VulkanGlTexture.noteExternalForeignLayout(cmd, image.handle(), newLayout);
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
                               int colorFormat, int depthFormat, boolean stencilAttached) {
    }

    private long pipelineFor(DirectGlDrawState state, ProgramRec program, ActiveRenderTargets targets,
                             VertexBindingSpan vertices) {
        int colorFormat = targets.colorAttachments().isEmpty()
                ? VK10.VK_FORMAT_UNDEFINED
                : targets.colorAttachments().get(0).format();
        int depthFormat = targets.depthAttachment() == null
                ? VK10.VK_FORMAT_UNDEFINED : targets.depthAttachment().format();
        long layoutHash = program.vertexLayoutHash ^ (vertices == null ? 0L : vertices.strideBytes());
        FboRec fbo = this.framebuffers.get((long) state.renderTargetId());
        DirectGlFrameBridge.MainImages main = DirectGlFrameBridge.mainImages();
        boolean stencil = fbo == null ? main != null && main.stencilAttached() : hasStencil(fbo.depth);
        PipelineKey key = new PipelineKey(state, this.extendedState.hash(), layoutHash, colorFormat, depthFormat, stencil);
        Long cached = this.pipelineCache.get(key);
        if (cached != null) {
            return cached;
        }
        long created = createPipeline(state, program, targets, vertices, colorFormat, depthFormat, stencil);
        if (created != VK_NULL_HANDLE) {
            this.pipelineCache.put(key, created);
        }
        return created;
    }

    private long createPipeline(DirectGlDrawState state, ProgramRec program, ActiveRenderTargets targets,
                                VertexBindingSpan vertices, int colorFormat, int depthFormat, boolean stencilAttached) {
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
                    attributes.get(i).location(attrib.location()).binding(0)
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
            VkStencilOpState stencilFront = VkStencilOpState
                    .calloc(stack)
                    .failOp(ext.stencilFail()).passOp(ext.stencilPass())
                    .depthFailOp(ext.stencilDepthFail()).compareOp(ext.stencilFunc())
                    .compareMask(ext.stencilFuncMask()).writeMask(ext.stencilWriteMask())
                    .reference(ext.stencilRef());
            VkStencilOpState stencilBack = VkStencilOpState
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
                    .stencilTestEnable(stencilAttached && key.stencilTestEnabled())
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
                    .depthAttachmentFormat(depthFormat)
                    .stencilAttachmentFormat(stencilAttached ? depthFormat : VK10.VK_FORMAT_UNDEFINED);
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

    /**
     * Makes sure the newest descriptor pool still has a free set, appending a fresh pool when it
     * does not. The previous pool is left fully alive - sets already allocated from it stay valid -
     * so crossing a pool boundary can never leave a program holding a destroyed pool's set
     * (audit #16 F-1: the old code destroyed the pool at 65 programs and dangled every existing
     * {@code ProgramRec.descriptorSet}).
     *
     * @param setsHint how many programs exist, used only to size a newly appended pool
     */
    private void ensureDescriptorPool(int setsHint) {
        if (!this.descriptorPools.isEmpty() && this.descriptorPoolUsed < this.descriptorPoolCapacity) {
            return;
        }
        int capacity = Math.max(64, setsHint * 2);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(capacity);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(capacity).pPoolSizes(sizes);
            LongBuffer handle = stack.longs(0);
            check(VK10.vkCreateDescriptorPool(this.device, poolInfo, null, handle),
                    "vkCreateDescriptorPool(converter)");
            this.descriptorPools.add(handle.get(0));
            this.descriptorPoolCapacity = capacity;
            this.descriptorPoolUsed = 0;
        }
    }

    /** The pool new descriptor sets are allocated from: always the newest appended pool. */
    private long currentDescriptorPool() {
        return this.descriptorPools.get(this.descriptorPools.size() - 1);
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
        if (rec.uniformBuffer == null) {
            rec.uniformBuffer = new VulkanHostBuffer(this.context,
                    Math.max(256L, values.byteSize()), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
            // The program's descriptor set names this buffer, and a set written before a growth
            // could keep naming the grown-away handle: retention keeps every superseded uniform
            // buffer alive until the program itself is destroyed (see VulkanHostBuffer).
            rec.uniformBuffer.retainSupersededBuffers();
        } else {
            rec.uniformBuffer.ensureCapacity(values.byteSize());
        }
        MemorySegment.copy(values, 0L, MemorySegment.ofAddress(rec.uniformBuffer.mappedAddress())
                .reinterpret(values.byteSize()), 0L, values.byteSize());
        if (rec.descriptorSet == VK_NULL_HANDLE) {
            ensureDescriptorPool(this.programs.size());
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetAllocateInfo alloc = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default().descriptorPool(currentDescriptorPool())
                        .pSetLayouts(stack.longs(this.uniformSetLayout));
                LongBuffer set = stack.longs(0);
                check(VK10.vkAllocateDescriptorSets(this.device, alloc, set),
                        "vkAllocateDescriptorSets(converter)");
                rec.descriptorSet = set.get(0);
                this.descriptorPoolUsed++;
            }
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0).buffer(rec.uniformBuffer.buffer()).offset(0L).range(values.byteSize());
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
            TextureRec rec = new TextureRec();
            rec.width = width;
            rec.height = height;
            rec.format = attachmentFormat(format);
            int id;
            if (rec.format == VK_FORMAT_R8G8B8A8_UNORM) {
                ByteBuffer zeros = ByteBuffer.allocateDirect(Math.max(4, width * height * 4));
                id = textures.createTexture2d(width, height, false, zeros, true);
            } else {
                id = RenderObjectIds.reserve(1);
                int aspects = depthAspects(rec.format);
                rec.depth = createAttachmentImage(width, height, rec.format,
                        VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, aspects);
                if (textures.publishDirectGlDepthAttachment(id, rec.depth.image(), rec.depth.sampleView(),
                        width, height, aspects) == null) {
                    retireAttachment(rec.depth);
                    return 0;
                }
            }
            textureRecs.put((long) id, rec);
            return id;
        }

        @Override
        public void writeTexture(long textureId, int level, int x, int y, int width, int height,
                                 MemorySegment pixels) {
            BackendCallCounters.noteLane3SinkCall();
            TextureRec rec = textureRecs.get(textureId);
            VulkanGlTexture texture = textures.existing((int) textureId);
            if (level == 0 && rec != null && rec.depth == null && texture != null && pixels != null) {
                texture.uploadPatchFromPixels(width, height, x, y, pixels.asByteBuffer());
            }
        }

        @Override
        public void destroyTexture(long textureId) {
            BackendCallCounters.noteLane3SinkCall();
            TextureRec rec = textureRecs.remove(textureId);
            if (rec != null) {
                textures.forgetTexture((int) textureId);
                if (rec.depth != null) {
                    retireAttachment(rec.depth);
                }
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
            BufferRec rec = new BufferRec();
            rec.size = Math.max(16L, sizeBytes);
            rec.buffer = new VulkanHostBuffer(VulkanDirectGlSink.this.context, rec.size,
                    usageFlags | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT
                            | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
            long id = VulkanDirectGlSink.this.nextBufferId++;
            VulkanDirectGlSink.this.buffers.put(id, rec);
            return id;
        }

        @Override
        public void writeBuffer(long bufferId, long offsetBytes, MemorySegment data) {
            BackendCallCounters.noteLane3SinkCall();
            BufferRec rec = VulkanDirectGlSink.this.buffers.get(bufferId);
            if (rec == null || data == null || rec.buffer.mappedAddress() == 0L) {
                return;
            }
            long writable = Math.min(data.byteSize(), rec.size - offsetBytes);
            if (writable <= 0L) {
                return;
            }
            MemorySegment.copy(data, 0L,
                    MemorySegment.ofAddress(rec.buffer.mappedAddress()).reinterpret(rec.size),
                    offsetBytes, writable);
        }

        @Override
        public void destroyBuffer(long bufferId) {
            BackendCallCounters.noteLane3SinkCall();
            BufferRec rec = VulkanDirectGlSink.this.buffers.remove(bufferId);
            if (rec != null) {
                // VulkanHostBuffer.delete() never frees memory immediately: with a recording
                // open the handle joins the context's retired list, and the slab range itself
                // is released against the timeline completion value (audit #16 F-1).
                rec.buffer.delete();
            }
        }
    };

    @Override
    public BufferOps bufferOps() {
        return this.bufferOps;
    }

    // -- framebuffer ops ------------------------------------------------------------

    private final FramebufferOps framebufferOps = new FramebufferOps() {
        @Override
        public long createFramebuffer(long[] colorTextureIds, long depthTextureId, int width, int height) {
            BackendCallCounters.noteLane3SinkCall();
            TextureRec colorRec = colorTextureIds.length == 0 ? null : textureRecs.get(colorTextureIds[0]);
            TextureRec depthRec = textureRecs.get(depthTextureId);
            VulkanGlTexture colorTexture = colorRec == null ? null : textures.existing((int) colorTextureIds[0]);
            if (colorTexture != null && (colorRec.depth != null || colorRec.width < width || colorRec.height < height)
                    || depthRec != null && (depthRec.depth == null || depthRec.width < width || depthRec.height < height)) {
                return 0;
            }

            FboRec rec = new FboRec();
            ActiveRenderTargets.RenderTargetView colorView = null;
            if (colorTexture != null) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    rec.colorView = attachmentView(stack, colorTexture.image(), colorRec.format, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
                }
                rec.color = new FramebufferImage(colorTexture.image(), colorRec.format, VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                colorView = targetView(rec.colorView, colorRec.format);
            }
            ActiveRenderTargets.RenderTargetView depthView = null;
            if (depthRec != null) {
                rec.depth = new FramebufferImage(depthRec.depth.image(), depthRec.format, depthAspects(depthRec.format),
                        VK_IMAGE_LAYOUT_UNDEFINED);
                depthView = targetView(depthRec.depth.view(), depthRec.format);
            }
            if (colorView == null && depthView == null) {
                return 0;
            }
            rec.targets = new ActiveRenderTargets(colorView == null ? List.of() : List.of(colorView),
                    depthView, width, height);
            long id = nextFramebufferId++;
            framebuffers.put(id, rec);
            return id;
        }

        @Override
        public void destroyFramebuffer(long framebufferId) {
            BackendCallCounters.noteLane3SinkCall();
            FboRec rec = framebuffers.remove(framebufferId);
            if (rec != null && rec.colorView != VK_NULL_HANDLE) {
                VulkanRetireQueue.retire(device.address(), () -> VK10.vkDestroyImageView(device, rec.colorView, null));
            }
        }

        @Override
        public void clearFramebuffer(long framebufferId, ClearSpec spec) {
            BackendCallCounters.noteLane3SinkCall();
            recordFramebufferClear(framebufferId, spec);
        }

        @Override
        public void blitFramebuffer(long srcFramebufferId, long dstFramebufferId, BlitRegion region) {
            BackendCallCounters.noteLane3SinkCall();
            recordFramebufferBlit(srcFramebufferId, dstFramebufferId, region);
        }
    };

    @Override
    public FramebufferOps framebufferOps() {
        return this.framebufferOps;
    }

    private void recordFramebufferClear(long framebufferId, ClearSpec spec) {
        VkCommandBuffer cmd = DirectGlFrameBridge.commandBuffer();
        ActiveRenderTargets main = DirectGlFrameBridge.mainTargets();
        FboRec fbo = this.framebuffers.get(framebufferId);
        ActiveRenderTargets targets = framebufferId == 0 ? main : fbo == null ? null : fbo.targets;
        if (cmd == null || targets == null) {
            return;
        }
        DirectGlFrameBridge.MainImages mainImages = DirectGlFrameBridge.mainImages();
        boolean stencil = fbo == null ? mainImages != null && mainImages.stencilAttached() : hasStencil(fbo.depth);
        int depthStencil = targets.depthAttachment() == null ? 0
                : vkAspects(spec.clearMask()) & (VK10.VK_IMAGE_ASPECT_DEPTH_BIT
                        | (stencil ? VK10.VK_IMAGE_ASPECT_STENCIL_BIT : 0));
        int colorCount = (spec.clearMask() & COLOR_ASPECT) == 0 ? 0 : targets.colorAttachments().size();
        int count = colorCount + (depthStencil == 0 ? 0 : 1);
        if (count == 0) {
            return;
        }

        int x0 = 0, y0 = 0, x1 = targets.width(), y1 = targets.height();
        if (spec.scissorEnabled()) {
            x0 = Math.max(0, spec.scissorX());
            y0 = Math.max(0, spec.scissorY());
            x1 = (int) Math.min(x1, (long) spec.scissorX() + Math.max(0, spec.scissorWidth()));
            y1 = (int) Math.min(y1, (long) spec.scissorY() + Math.max(0, spec.scissorHeight()));
        }
        if (x0 >= x1 || y0 >= y1) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearAttachment.Buffer attachments = VkClearAttachment.calloc(count, stack);
            for (int slot = 0; slot < colorCount; slot++) {
                VkClearAttachment attachment = attachments.get(slot)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).colorAttachment(slot);
                attachment.clearValue().color()
                        .float32(0, spec.r()).float32(1, spec.g()).float32(2, spec.b()).float32(3, spec.a());
            }
            if (depthStencil != 0) {
                attachments.get(colorCount).aspectMask(depthStencil).clearValue().depthStencil()
                        .set(spec.depth(), spec.stencil());
            }
            VkClearRect.Buffer rectangles = VkClearRect.calloc(1, stack);
            VkClearRect rect = rectangles.get(0).baseArrayLayer(0).layerCount(1);
            rect.rect().offset().set(x0, y0);
            rect.rect().extent().set(x1 - x0, y1 - y0);

            if (fbo != null) {
                suspendMain(cmd, main);
                beginSideRendering(cmd, stack, fbo);
            }
            VK10.vkCmdClearAttachments(cmd, attachments, rectangles);
            if (fbo != null) {
                finishSideRendering(cmd, stack, fbo, main);
            }
        }
    }

    private static int vkAspects(int aspects) {
        int mask = 0;
        if ((aspects & COLOR_ASPECT) != 0) {
            mask |= VK10.VK_IMAGE_ASPECT_COLOR_BIT;
        }
        if ((aspects & DEPTH_ASPECT) != 0) {
            mask |= VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
        }
        if ((aspects & STENCIL_ASPECT) != 0) {
            mask |= VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
        }
        return mask;
    }

    private void recordFramebufferBlit(long srcId, long dstId, BlitRegion region) {
        VkCommandBuffer cmd = DirectGlFrameBridge.commandBuffer();
        if (cmd == null) {
            return;
        }
        ActiveRenderTargets main = DirectGlFrameBridge.mainTargets();
        FboRec src = srcId == 0 ? mainFramebuffer(main) : this.framebuffers.get(srcId);
        FboRec dst = dstId == 0 ? mainFramebuffer(main) : this.framebuffers.get(dstId);
        if (src == null || dst == null) {
            return;
        }
        region = clipBlitRegion(region, src.targets, dst.targets);
        if (region == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            BlitTransfer color = blitTransfer(cmd, stack, src.color, dst.color, region, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            BlitTransfer depthStencil = blitTransfer(cmd, stack, src.depth, dst.depth, region,
                    VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT);
            if (color == null && depthStencil == null) {
                return;
            }
            suspendMain(cmd, main);
            recordBlitTransfer(cmd, stack, color, region);
            recordBlitTransfer(cmd, stack, depthStencil, region);
            resumeMain(cmd, stack, main);
        }
    }

    private static FboRec mainFramebuffer(ActiveRenderTargets main) {
        DirectGlFrameBridge.MainImages images = DirectGlFrameBridge.mainImages();
        if (main == null || images == null) {
            return null;
        }
        FboRec target = new FboRec();
        target.targets = main;
        if (images.colorImage() != VK_NULL_HANDLE && !main.colorAttachments().isEmpty()) {
            target.color = new FramebufferImage(images.colorImage(), main.colorAttachments().getFirst().format(),
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT, images.colorLayout());
        }
        if (images.depthImage() != VK_NULL_HANDLE && main.depthAttachment() != null) {
            int format = main.depthAttachment().format();
            target.depth = new FramebufferImage(images.depthImage(), format, depthAspects(format), images.depthLayout());
        }
        return target;
    }

    private static BlitRegion clipBlitRegion(BlitRegion region, ActiveRenderTargets source, ActiveRenderTargets destination) {
        BlitAxis x = clipAxis(region.srcX0(), region.srcX1(), source.width(),
                region.dstX0(), region.dstX1(), destination.width());
        BlitAxis y = clipAxis(region.srcY0(), region.srcY1(), source.height(),
                region.dstY0(), region.dstY1(), destination.height());
        return x == null || y == null ? null : new BlitRegion(x.src0(), y.src0(), x.src1(), y.src1(),
                x.dst0(), y.dst0(), x.dst1(), y.dst1(), region.aspectMask(), region.linearFilter());
    }

    private record BlitAxis(int src0, int src1, int dst0, int dst1) {
    }

    private static BlitAxis clipAxis(int src0, int src1, int srcLimit, int dst0, int dst1, int dstLimit) {
        double srcDelta = (double) src1 - src0;
        double dstDelta = (double) dst1 - dst0;
        if (srcDelta == 0 || dstDelta == 0) {
            return null;
        }
        double srcAtZero = -src0 / srcDelta;
        double srcAtLimit = (srcLimit - (double) src0) / srcDelta;
        double dstAtZero = -dst0 / dstDelta;
        double dstAtLimit = (dstLimit - (double) dst0) / dstDelta;
        double start = Math.max(0, Math.max(Math.min(srcAtZero, srcAtLimit), Math.min(dstAtZero, dstAtLimit)));
        double end = Math.min(1, Math.min(Math.max(srcAtZero, srcAtLimit), Math.max(dstAtZero, dstAtLimit)));
        if (start >= end) {
            return null;
        }
        double startSrc = Math.fma(start, srcDelta, src0);
        double endSrc = Math.fma(end, srcDelta, src0);
        double startDst = Math.fma(start, dstDelta, dst0);
        double endDst = Math.fma(end, dstDelta, dst0);
        // VkImageBlit describes integer texel edges. Fractional intersections would change the GL scale if rounded.
        if (!isTexelEdge(startSrc) || !isTexelEdge(endSrc) || !isTexelEdge(startDst) || !isTexelEdge(endDst)) {
            return null;
        }
        return new BlitAxis((int) Math.round(startSrc), (int) Math.round(endSrc),
                (int) Math.round(startDst), (int) Math.round(endDst));
    }

    private static boolean isTexelEdge(double value) {
        return Math.abs(value - Math.rint(value)) < 0.000001;
    }

    private record BlitTransfer(FramebufferImage source, FramebufferImage destination, int aspects,
                                int sourceLayout, int destinationLayout, boolean copy, int filter) {
    }

    private BlitTransfer blitTransfer(VkCommandBuffer cmd, MemoryStack stack, FramebufferImage source,
                                      FramebufferImage destination, BlitRegion region, int requestedAspects) {
        if (source == null || destination == null || source.handle() == destination.handle()) {
            return null;
        }
        int aspects = requestedAspects & vkAspects(region.aspectMask()) & source.aspects() & destination.aspects();
        int sourceLayout = restingLayout(cmd, source);
        if (aspects == 0 || sourceLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
            return null;
        }
        boolean color = aspects == VK10.VK_IMAGE_ASPECT_COLOR_BIT;
        boolean sameFormat = source.format() == destination.format();
        if (!color && !sameFormat) {
            return null; // Vulkan requires identical depth/stencil formats.
        }
        int srcFeatures = formatFeatures(stack, source.format());
        int dstFeatures = sameFormat ? srcFeatures : formatFeatures(stack, destination.format());
        if ((srcFeatures & org.lwjgl.vulkan.VK11.VK_FORMAT_FEATURE_TRANSFER_SRC_BIT) == 0
                || (dstFeatures & org.lwjgl.vulkan.VK11.VK_FORMAT_FEATURE_TRANSFER_DST_BIT) == 0) {
            return null;
        }
        boolean copy = sameFormat && region.srcX1() - region.srcX0() == region.dstX1() - region.dstX0()
                && region.srcY1() - region.srcY0() == region.dstY1() - region.dstY0();
        boolean linear = color && region.linearFilter();
        if (!copy && ((srcFeatures & VK10.VK_FORMAT_FEATURE_BLIT_SRC_BIT) == 0
                || (dstFeatures & VK10.VK_FORMAT_FEATURE_BLIT_DST_BIT) == 0
                || linear && (srcFeatures & VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) == 0)) {
            return null;
        }
        return new BlitTransfer(source, destination, aspects, sourceLayout, restingLayout(cmd, destination), copy,
                linear ? VK10.VK_FILTER_LINEAR : VK10.VK_FILTER_NEAREST);
    }

    private int formatFeatures(MemoryStack stack, int format) {
        VkFormatProperties properties = VkFormatProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceFormatProperties(this.context.physicalDevice(), format, properties);
        return properties.optimalTilingFeatures();
    }

    private static void recordBlitTransfer(VkCommandBuffer cmd, MemoryStack stack,
                                           BlitTransfer transfer, BlitRegion region) {
        if (transfer == null) {
            return;
        }
        int srcLayout = VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        int dstLayout = VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        FramebufferImage source = transfer.source();
        FramebufferImage destination = transfer.destination();
        transition(cmd, stack, source, transfer.sourceLayout(), srcLayout);
        transition(cmd, stack, destination, transfer.destinationLayout(), dstLayout);
        for (int aspects = transfer.aspects(); aspects != 0; aspects &= aspects - 1) {
            int aspect = Integer.lowestOneBit(aspects);
            if (transfer.copy()) {
                VkImageCopy.Buffer copies = VkImageCopy.calloc(1, stack);
                VkImageCopy copy = copies.get(0);
                copy.srcSubresource().set(aspect, 0, 0, 1);
                copy.dstSubresource().set(aspect, 0, 0, 1);
                copy.srcOffset().set(Math.min(region.srcX0(), region.srcX1()), Math.min(region.srcY0(), region.srcY1()), 0);
                copy.dstOffset().set(Math.min(region.dstX0(), region.dstX1()), Math.min(region.dstY0(), region.dstY1()), 0);
                copy.extent().set(Math.abs(region.srcX1() - region.srcX0()), Math.abs(region.srcY1() - region.srcY0()), 1);
                VK10.vkCmdCopyImage(cmd, source.handle(), srcLayout, destination.handle(), dstLayout, copies);
            } else {
                VkImageBlit.Buffer blits = VkImageBlit.calloc(1, stack);
                VkImageBlit blit = blits.get(0);
                blit.srcSubresource().set(aspect, 0, 0, 1);
                blit.dstSubresource().set(aspect, 0, 0, 1);
                blit.srcOffsets(0).set(region.srcX0(), region.srcY0(), 0);
                blit.srcOffsets(1).set(region.srcX1(), region.srcY1(), 1);
                blit.dstOffsets(0).set(region.dstX0(), region.dstY0(), 0);
                blit.dstOffsets(1).set(region.dstX1(), region.dstY1(), 1);
                VK10.vkCmdBlitImage(cmd, source.handle(), srcLayout, destination.handle(), dstLayout,
                        blits, transfer.filter());
            }
        }
        transition(cmd, stack, source, srcLayout, transfer.sourceLayout());
        transition(cmd, stack, destination, dstLayout, transfer.destinationLayout() == VK_IMAGE_LAYOUT_UNDEFINED
                ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : transfer.destinationLayout());
    }

    private static ActiveRenderTargets.RenderTargetView targetView(long view, int format) {
        return new ActiveRenderTargets.RenderTargetView(view, format, ActiveRenderTargets.LoadOp.LOAD,
                ActiveRenderTargets.StoreOp.STORE);
    }

    private int attachmentFormat(int glFormat) {
        return switch (glFormat) {
            case 0x81A5 -> VK10.VK_FORMAT_D16_UNORM; // GL_DEPTH_COMPONENT16
            case 0x81A6 -> VK10.VK_FORMAT_X8_D24_UNORM_PACK32; // GL_DEPTH_COMPONENT24
            case 0x1902, 0x81A7, 0x8CAC -> VK_FORMAT_D32_SFLOAT; // GL_DEPTH_COMPONENT, 32, 32F
            case 0x84F9, 0x88F0 -> { // GL_DEPTH_STENCIL, GL_DEPTH24_STENCIL8
                int required = VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT
                        | VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    yield (formatFeatures(stack, VK10.VK_FORMAT_D24_UNORM_S8_UINT) & required) == required
                            ? VK10.VK_FORMAT_D24_UNORM_S8_UINT : VK10.VK_FORMAT_D32_SFLOAT_S8_UINT;
                }
            }
            case 0x8CAD -> VK10.VK_FORMAT_D32_SFLOAT_S8_UINT; // GL_DEPTH32F_STENCIL8
            default -> VK_FORMAT_R8G8B8A8_UNORM;
        };
    }

    private static int depthAspects(int format) {
        return switch (format) {
            case VK10.VK_FORMAT_D16_UNORM_S8_UINT, VK10.VK_FORMAT_D24_UNORM_S8_UINT,
                    VK10.VK_FORMAT_D32_SFLOAT_S8_UINT -> VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
            default -> VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
        };
    }

    private static boolean hasStencil(FramebufferImage image) {
        return image != null && (image.aspects() & VK10.VK_IMAGE_ASPECT_STENCIL_BIT) != 0;
    }

    private record FramebufferImage(long handle, int format, int aspects, int layout) {
    }

    private static int restingLayout(VkCommandBuffer cmd, FramebufferImage image) {
        if (image == null) {
            return VK_IMAGE_LAYOUT_UNDEFINED;
        }
        return image.layout() == VK_IMAGE_LAYOUT_UNDEFINED
                ? VulkanGlTexture.recordingLayoutOf(cmd.address(), image.handle()) : image.layout();
    }

    /** Slab-backed depth attachment. The sampled view selects the depth aspect alone. */
    private record AttachmentImage(long image, long view, long sampleView, TransientAttachmentSlab.Allocation allocation) {
    }

    private void retireAttachment(AttachmentImage attachment) {
        VulkanRetireQueue.retire(this.device.address(), () -> {
            if (attachment.sampleView() != attachment.view()) {
                VK10.vkDestroyImageView(this.device, attachment.sampleView(), null);
            }
            VK10.vkDestroyImageView(this.device, attachment.view(), null);
            VK10.vkDestroyImage(this.device, attachment.image(), null);
            this.attachmentSlab.release(attachment.allocation());
        });
    }

    private long attachmentView(MemoryStack stack, long image, int format, int aspects) {
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                .image(image).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D).format(format);
        viewInfo.subresourceRange().set(aspects, 0, 1, 0, 1);
        LongBuffer view = stack.longs(0);
        check(VK10.vkCreateImageView(this.device, viewInfo, null, view), "vkCreateImageView(converter fbo)");
        return view.get(0);
    }

    private AttachmentImage createAttachmentImage(int width, int height, int format, int usage, int aspects) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1).arrayLayers(1)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(width, height, 1);
            LongBuffer handle = stack.longs(0);
            check(VK10.vkCreateImage(this.device, imageInfo, null, handle), "vkCreateImage(converter fbo)");
            long image = handle.get(0);
            long view = VK_NULL_HANDLE;
            TransientAttachmentSlab.Allocation allocation = null;
            try {
                VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
                VK10.vkGetImageMemoryRequirements(this.device, image, requirements);
                allocation = this.attachmentSlab.acquire(requirements.size(), requirements.alignment());
                check(VK10.vkBindImageMemory(this.device, image, this.attachmentSlab.memoryHandle(), allocation.offset()),
                        "vkBindImageMemory(converter fbo)");
                view = attachmentView(stack, image, format, aspects);
                long sampleView = (aspects & VK10.VK_IMAGE_ASPECT_STENCIL_BIT) != 0
                        ? attachmentView(stack, image, format, VK10.VK_IMAGE_ASPECT_DEPTH_BIT) : view;
                return new AttachmentImage(image, view, sampleView, allocation);
            } catch (RuntimeException | Error failure) {
                VK10.vkDestroyImageView(this.device, view, null);
                VK10.vkDestroyImage(this.device, image, null);
                if (allocation != null) {
                    this.attachmentSlab.release(allocation);
                }
                throw failure;
            }
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
            VkDevice device = VulkanDirectGlSink.this.device;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default().stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(rec.vertexModule).pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer createInfo =
                        VkComputePipelineCreateInfo.calloc(1, stack);
                createInfo.get(0).sType$Default().stage(stage).layout(
                        VulkanDirectGlSink.this.pipelineLayout);
                // Pipeline handles are 64-bit: the LWJGL binding takes a LongBuffer here.
                // (A PointerBuffer argument is a plain type error, but JDK 25's javac
                // reports it as an internal TransTypes NPE instead of an error.)
                java.nio.LongBuffer pipelines = stack.callocLong(1);
                int result = VK10.vkCreateComputePipelines(device,
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
            if (rec.uniformBuffer != null) {
                rec.uniformBuffer.delete();
            }
        }
    };

    @Override
    public ShaderOps shaderOps() {
        return this.shaderOps;
    }

    private long createShaderModule(int[] words) {
        // The compiled SPIR-V is input-sized with no bound, so the code buffer lives on the heap:
        // the thread MemoryStack is 64 KiB and a large module copied onto it overflows the frame
        // (the crash class S5-b hit on the pack path). The small create-info structs stay on the
        // stack, as in VulkanModProgramPipeline's heap-shaped equivalent of this same work.
        ByteBuffer code = MemoryUtil.memAlloc(words.length * Integer.BYTES);
        try (MemoryStack stack = MemoryStack.stackPush()) {
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
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    private void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with Vulkan result " + result);
        }
    }

    // -- records ------------------------------------------------------------------------

    private static final class BufferRec {
        /** Slab-backed, persistently mapped; owns no device memory of its own. */
        VulkanHostBuffer buffer;
        long size;
    }

    private static final class TextureRec {
        int width;
        int height;
        int format;
        AttachmentImage depth;
    }

    private static final class FboRec {
        ActiveRenderTargets targets;
        FramebufferImage color;
        FramebufferImage depth;
        long colorView;
    }

    private static final class ProgramRec {
        long vertexModule;
        long fragmentModule;
        long computePipeline;
        AttribDesc[] attribs = new AttribDesc[0];
        long vertexLayoutHash = 0x9E3779B97F4A7C15L;
        /** Slab-backed uniform block buffer; superseded growths are retained until destroy. */
        VulkanHostBuffer uniformBuffer;
        long descriptorSet;
    }
}
