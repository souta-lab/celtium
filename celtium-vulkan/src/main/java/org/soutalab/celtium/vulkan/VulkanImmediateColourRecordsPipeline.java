package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * The colour family's only pipeline form since Stage 5 stage 6: {@code POSITION_COLOR}
 * vertices whose per-group block comes from the draw records.
 *
 * <p>This class is the record-reading twin the family's deleted per-group class grew through
 * its {@code createRecords} factory, separated into its own class when the per-group
 * form was deleted (stage 6 slice S6-b): the records form is no longer a variant of anything
 * - it is the family. The layout declares the records set at set 0 and nothing else; the
 * vertex stage reads the block from the records buffer by {@code gl_InstanceIndex}, and the
 * fragment stage still takes the alpha reference and the current colour from the push range
 * the layout declares for it (208 bytes), which the batch draw sink and the direct fallback
 * push per entry from the record bytes themselves.
 *
 * <p>The line variant is a topology, not a family: {@link #createForLines} builds the same
 * records pipeline with a line-list assembly, the form the selection outline's batches take
 * now that a line group can also be captured or drawn directly through its twin.
 *
 * <p>Threading: render thread only (creation also runs on the pipeline prewarm thread, as
 * every immediate family's does, through {@link ImmediateRecordsPipelines}).
 */
public final class VulkanImmediateColourRecordsPipeline {
    /** {@code POSITION_COLOR}: 3 floats position and 4 unsigned bytes colour. */
    public static final int VERTEX_BYTES = 16;
    // 208 since the fixed-function fog landed: the fragment stage reads the fog block at 128..207 (colour, params and
    // the model view the fog coordinate is computed from), so the pipeline layout's range has to cover it. This is the
    // size the lit ITEM pipeline has required of the device since the lighting block arrived, so it asks for nothing
    // the tree did not already.
    private static final int PUSH_CONSTANT_BYTES = 208;

    private final VulkanContext context;
    private final long renderPass;
    /**
     * How many colour attachments the pass this pipeline is built against attaches. The blend state is
     * replicated once per attachment when the pipeline is created - the mod-program multi-target form,
     * because GL has one blend state for all draw buffers and the per-buffer form is not captured.
     */
    private final int colorAttachments;
    /** The records set layout, owned by {@link InstanceDataStore}; this pipeline only references it. */
    private final long recordsLayout;

    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;

    private final VulkanColorBlendState colorBlend;
    private final boolean depthTest;
    private final boolean depthWrite;
    private final int cull;
    /** Draw line lists instead of triangles: the selection outline and other line batches. */
    private final boolean lines;
    /** The Vulkan compare op the mirrored {@code glDepthFunc} mapped to. */
    private final int depthCompareOp;

    private VulkanImmediateColourRecordsPipeline(VulkanContext context, long renderPass,
            VulkanColorBlendState colorBlend, boolean depthTest, boolean depthWrite, int cull,
            boolean lines, int depthCompareOp, long recordsSetLayout, int colorAttachments) {
        this.context = context;
        this.renderPass = renderPass;
        this.colorBlend = colorBlend;
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        this.cull = cull;
        this.lines = lines;
        this.depthCompareOp = depthCompareOp;
        this.recordsLayout = recordsSetLayout;
        this.colorAttachments = Math.max(1, colorAttachments);
    }

    /**
     * Creates the triangle form for one drawing state: the blend factors, blend equations and
     * colour write mask the batch was captured under (BH-07, BH-10), the depth test and its
     * write mask, and face culling, read from the draw records at draw time.
     */
    public static VulkanImmediateColourRecordsPipeline create(VulkanContext context, long renderPass,
            VulkanColorBlendState colorBlend, boolean depthTest, boolean depthWrite, int cull,
            int depthCompareOp, long recordsSetLayout, int colorAttachments) {
        return build(new VulkanImmediateColourRecordsPipeline(context, renderPass, colorBlend,
                depthTest, depthWrite, cull, false, depthCompareOp, recordsSetLayout, colorAttachments));
    }

    /**
     * The line-list variant.
     * <p>
     * Minecraft draws the block-selection outline with {@code GL_LINE_STRIP}, and a line batch
     * expanded to a line list cannot be drawn by a triangle pipeline. Whether the width OpenGL
     * was asked for can be honoured is decided by the caller from the device's {@code wideLines}
     * and line-width range before this is built at all, so this factory simply builds the
     * variant it is told to.
     */
    public static VulkanImmediateColourRecordsPipeline createForLines(VulkanContext context, long renderPass,
            VulkanColorBlendState colorBlend, boolean depthTest, boolean depthWrite, int cull,
            int depthCompareOp, long recordsSetLayout, int colorAttachments) {
        return build(new VulkanImmediateColourRecordsPipeline(context, renderPass, colorBlend,
                depthTest, depthWrite, cull, true, depthCompareOp, recordsSetLayout, colorAttachments));
    }

    private static VulkanImmediateColourRecordsPipeline build(VulkanImmediateColourRecordsPipeline result) {
        if (result.context == null || result.renderPass == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("context and renderPass must be set");
        }
        if (result.colorBlend == null) {
            throw new IllegalArgumentException("colorBlend must be set");
        }
        if (result.recordsLayout == VK_NULL_HANDLE) {
            throw new IllegalStateException("the records pipeline needs the records set layout");
        }
        // BH-20: the two stages are one transaction. createPipeline() can throw after the layout exists - a shader
        // module that will not compile, a vkCreateGraphicsPipelines refusal - and the layout handles then had no owner
        // left to release them, because the object is never returned and its delete() is therefore never called. The
        // rollback releases exactly what the stages created (delete() null-checks each handle), which is what makes
        // "created equals destroyed" true when a native constructor fails partway.
        try {
            result.createPipelineLayout();
            result.createPipeline();
        } catch (RuntimeException | LinkageError error) {
            VulkanImmediateTextureRecordsPipeline.BUILD_ROLLBACKS.incrementAndGet();
            result.delete();
            throw error;
        }
        return result;
    }

    /** {@return whether this pipeline writes depth} - an opaque group can be reordered only when the answer is yes. */
    public boolean celtium$depthWrites() {
        return this.depthWrite;
    }

    /** {@return whether this pipeline tests depth} - reordering opaque groups is only equivalent when it does. */
    public boolean celtium$depthTests() {
        return this.depthTest;
    }

    /** {@return whether this pipeline blends} - a blended group keeps its arrival order. */
    public boolean celtium$blends() {
        return this.colorBlend != null && this.colorBlend.celtium$blends();
    }

    /** {@return the render pass this pipeline was built for}. */
    public long celtium$renderPass() {
        return this.renderPass;
    }

    /** {@return the blend state this pipeline was built with}. */
    public VulkanColorBlendState celtium$colorBlend() {
        return this.colorBlend;
    }

    /**
     * {@return this pipeline's Vulkan handle, or 0 when it has none}.
     * <p>
     * A silent draw for a zero handle is the failure this accessor exists to rule out: a probe
     * that rewrites a group's vertices and still sees nothing in the picture has to be able to
     * rule out "the draw never happened" before it accuses the fragment stage.
     */
    public long celtium$handle() {
        return this.pipeline;
    }

    /** {@return this pipeline's layout handle} (the batch draw sink binds the records set against it). */
    public long celtium$pipelineLayout() {
        return this.pipelineLayout;
    }

    /** {@return the push-constant block size this family's fragment stage declares}. */
    public int celtium$pushConstantBytes() {
        return PUSH_CONSTANT_BYTES;
    }

    /** {@return whether face culling is on}. */
    public boolean celtium$culls() {
        return VulkanCullState.culls(this.cull);
    }

    /** {@return the encoded cull state (enable, face and winding) this pipeline was built with}. */
    public int celtium$cullState() {
        return this.cull;
    }

    /** {@return the depth compare op this pipeline was built with}. */
    public int celtium$depthCompareOp() {
        return this.depthCompareOp;
    }

    /** {@return how many colour attachments this pipeline's blend state was replicated for}. */
    public int celtium$colorAttachments() {
        return this.colorAttachments;
    }

    /** {@return whether this is the line-list variant}. */
    public boolean celtium$lines() {
        return this.lines;
    }

    public int vertexBytes() {
        return VERTEX_BYTES;
    }

    public void delete() {
        // Capture: a deferred teardown must destroy these handles, not whatever this object is rebuilt into later.
        long pipelineToDestroy = this.pipeline;
        long pipelineLayoutToDestroy = this.pipelineLayout;
        this.pipeline = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        if (this.context.celtium$recordingOpen()) {
            // The open recording can have bound this pipeline; destroying it invalidates the recording. Deferred to
            // the frame boundary, where the frame's recording has been ended and submitted.
            this.context.celtium$retireCleanup("immediate.delete", () -> {
                if (pipelineToDestroy != VK_NULL_HANDLE) {
                    vkDestroyPipeline(this.context.device(), pipelineToDestroy, null);
                }
                if (pipelineLayoutToDestroy != VK_NULL_HANDLE) {
                    vkDestroyPipelineLayout(this.context.device(), pipelineLayoutToDestroy, null);
                }
            });
            return;
        }
        if (pipelineToDestroy != VK_NULL_HANDLE) {
            vkDestroyPipeline(this.context.device(), pipelineToDestroy, null);
        }
        if (pipelineLayoutToDestroy != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(this.context.device(), pipelineLayoutToDestroy, null);
        }
    }

    private void createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo create = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default();
            VkPushConstantRange.Buffer fragmentRange = VkPushConstantRange.calloc(1, stack);
            fragmentRange.get(0).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT).offset(0).size(PUSH_CONSTANT_BYTES);
            create.pSetLayouts(stack.longs(this.recordsLayout)).pPushConstantRanges(fragmentRange);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreatePipelineLayout(this.context.device(), create, null, handle),
                    "vkCreatePipelineLayout(immediate colour records)");
            this.pipelineLayout = handle.get(0);
        }
    }

    private void createPipeline() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer vertexCode = SpirvShaders.load(SpirvShaders.IMMEDIATE_COLOR_RECORDS_VERTEX);
            ByteBuffer fragmentCode = SpirvShaders.load(SpirvShaders.IMMEDIATE_COLOR_FRAGMENT);
            long vertexModule = VK_NULL_HANDLE;
            long fragmentModule = VK_NULL_HANDLE;
            try {
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
                LongBuffer handle = stack.mallocLong(1);
                moduleInfo.pCode(vertexCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                        "vkCreateShaderModule(immediate vertex)");
                vertexModule = handle.get(0);
                moduleInfo.pCode(fragmentCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                        "vkCreateShaderModule(immediate fragment)");
                fragmentModule = handle.get(0);

                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule)
                        .pName(stack.UTF8("main"));
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                        .pName(stack.UTF8("main"));

                VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0).binding(0).stride(VERTEX_BYTES).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(2, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R8G8B8A8_UNORM).offset(12);
                VkPipelineVertexInputStateCreateInfo vertexInput =
                        VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default()
                                .pVertexBindingDescriptions(binding)
                                .pVertexAttributeDescriptions(attributes);

                VkPipelineInputAssemblyStateCreateInfo assembly =
                        VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
                                .topology(this.lines ? VK_PRIMITIVE_TOPOLOGY_LINE_LIST
                                        : VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                VkPipelineViewportStateCreateInfo viewport =
                        VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default()
                                .viewportCount(1).scissorCount(1);
                VkPipelineRasterizationStateCreateInfo raster =
                        VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
                                .polygonMode(VK_POLYGON_MODE_FILL)
                                // BH-11: the depth bias is command state (vkCmdSetDepthBias) so one pipeline serves every
                                // polygon offset the game sets; the enable bit is what has to be declared here, and it is a
                                // rasterization field - the multisample state next door has no such member.
                                .depthBiasEnable(true)
                                // The enable, the face and the winding are one encoded state (VulkanCullState): a
                                // mod's glCullFace(GL_FRONT) or glFrontFace(GL_CW) selects its own pipeline here
                                // instead of being drawn with GL's default pair.
                                .cullMode(VulkanCullState.vkCullMode(this.cull))
                                .frontFace(VulkanCullState.vkFrontFace(this.cull))
                                .lineWidth(1.0f);
                VkPipelineMultisampleStateCreateInfo multisample =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
                                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                VkPipelineDepthStencilStateCreateInfo depth =
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                                .depthTestEnable(this.depthTest)
                                .depthWriteEnable(this.depthWrite)
                                .depthCompareOp(this.depthCompareOp);
                // One blend state per colour attachment of the pass this pipeline is built against: GL
                // has one blend state for all draw buffers unless a mod sets the per-buffer form, which
                // the capture does not hold, so the batch's state is replicated - the same form
                // VulkanModProgramPipeline's multi-target build uses. A one-colour pass keeps exactly
                // the single state this always built.
                int blendCount = Math.max(1, this.colorAttachments);
                VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(blendCount, stack);
                for (int i = 0; i < blendCount; i++) {
                    blendAttachment.get(i)
                            .blendEnable(this.colorBlend.blend())
                            .srcColorBlendFactor(this.colorBlend.srcColor())
                            .dstColorBlendFactor(this.colorBlend.dstColor())
                            .colorBlendOp(this.colorBlend.colorOp())
                            .srcAlphaBlendFactor(this.colorBlend.srcAlpha())
                            .dstAlphaBlendFactor(this.colorBlend.dstAlpha())
                            .alphaBlendOp(this.colorBlend.alphaOp())
                            .colorWriteMask(this.colorBlend.writeMask());
                }
                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default().logicOpEnable(false).pAttachments(blendAttachment);
                VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR, VK_DYNAMIC_STATE_DEPTH_BIAS));

                VkGraphicsPipelineCreateInfo.Buffer create = VkGraphicsPipelineCreateInfo.calloc(1, stack);
                create.get(0).sType$Default()
                        .pStages(stages)
                        .pVertexInputState(vertexInput)
                        .pInputAssemblyState(assembly)
                        .pViewportState(viewport)
                        .pRasterizationState(raster)
                        .pMultisampleState(multisample)
                        .pDepthStencilState(depth)
                        .pColorBlendState(blend)
                        .pDynamicState(dynamic)
                        .layout(this.pipelineLayout)
                        .renderPass(this.renderPass)
                        .subpass(0);
                LongBuffer pipelineHandle = stack.mallocLong(1);
                check(VulkanContext.createGraphicsPipelines(this.context.device(), create, pipelineHandle,
                        "immediate-colour-records"), "vkCreateGraphicsPipelines(immediate colour records)");
                this.pipeline = pipelineHandle.get(0);
            } finally {
                if (vertexModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.context.device(), vertexModule, null);
                }
                if (fragmentModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.context.device(), fragmentModule, null);
                }
                MemoryUtil.memFree(vertexCode);
                MemoryUtil.memFree(fragmentCode);
            }
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
