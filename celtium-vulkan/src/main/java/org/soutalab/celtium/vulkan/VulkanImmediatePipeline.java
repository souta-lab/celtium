package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
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
 * The first pipeline for Minecraft's own immediate-mode geometry: untextured {@code POSITION_COLOR} vertices, the
 * format a mod's {@code BufferBuilder.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR)} produces.
 * <p>
 * It is deliberately a single fixed state combination (depth test on with LEQUAL, depth writes off, straight alpha
 * blending on) rather than the full deferred selection from the state model. That keeps the first slice honest and
 * measurable: the batches it can draw are the ones whose state matches, everything else keeps being refused with a
 * reason. The state-driven pipeline cache replaces it once the draw path is proven end to end.
 * <p>
 * No texture is bound - the format has no texture coordinates - so the pipeline has no descriptor sets at all and
 * nothing but the 128 bytes of push constants the terrain and UI pipelines also use.
 * <p>
 * Threading: render thread only.
 */
public final class VulkanImmediatePipeline {
    /** {@code POSITION_COLOR}: 3 floats position and 4 unsigned bytes colour. */
    public static final int VERTEX_BYTES = 16;
    // 116, not 132: projection 64 + the model view's three affine columns 48 + alpha reference 4, all
    // inside the 128 bytes Vulkan guarantees (audit S19).
    private static final int PUSH_CONSTANT_BYTES = 128;

    private final VulkanContext context;
    private final long renderPass;

    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;

    private final boolean blend;
    private final int srcFactor;
    private final int dstFactor;
    private final boolean depthTest;
    private final boolean depthWrite;
    private final boolean cull;
    /** Draw line lists instead of triangles: the selection outline and other line batches. */
    private final boolean lines;

    private VulkanImmediatePipeline(VulkanContext context, long renderPass, boolean blend, int srcFactor, int dstFactor,
               boolean depthTest, boolean depthWrite, boolean cull, boolean lines) {
        this.context = context;
        this.renderPass = renderPass;
        this.blend = blend;
        this.srcFactor = srcFactor;
        this.dstFactor = dstFactor;
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        this.cull = cull;
        this.lines = lines;
    }

    /** Creates a pipeline for straight-alpha blending, which is what most of vanilla's immediate content uses. */
    public static VulkanImmediatePipeline create(VulkanContext context, long renderPass) {
        return create(context, renderPass, true, org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA,
                org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, true, false, false);
    }

    /**
     * Creates a pipeline for one drawing state: blending with the given Vulkan factors, the depth test and its write
     * mask, and face culling. These bake into the pipeline object, which is why the immediate path caches one per
     * state instead of forcing every batch through a single hardcoded combination.
     */
    public static VulkanImmediatePipeline create(VulkanContext context, long renderPass, boolean blend, int srcFactor, int dstFactor,
                            boolean depthTest, boolean depthWrite, boolean cull) {
        return create(context, renderPass, blend, srcFactor, dstFactor, depthTest, depthWrite, cull, false);
    }

    /**
     * The line-list variant.
     * <p>
     * Minecraft draws the block-selection outline with {@code GL_LINE_STRIP}, and a line batch expanded to a line list
     * cannot be drawn by a triangle pipeline. Whether the width OpenGL was asked for can be honoured is decided by the
     * caller from the device's {@code wideLines} and line-width range before this is built at all, so this factory simply
     * builds the variant it is told to.
     */
    public static VulkanImmediatePipeline createForLines(VulkanContext context, long renderPass, boolean blend,
            int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull) {
        return create(context, renderPass, blend, srcFactor, dstFactor, depthTest, depthWrite, cull, true);
    }

    private static VulkanImmediatePipeline create(VulkanContext context, long renderPass, boolean blend, int srcFactor,
            int dstFactor, boolean depthTest, boolean depthWrite, boolean cull, boolean lines) {
        if (context == null || renderPass == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("context and renderPass must be set");
        }
        VulkanImmediatePipeline result = new VulkanImmediatePipeline(context, renderPass, blend, srcFactor, dstFactor,
                depthTest, depthWrite, cull, lines);
        result.createPipelineLayout();
        result.createPipeline();
        return result;
    }

    /** Binds the pipeline, pushes the matrices and records the mesh's batch. */
    public void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, ByteBuffer pushConstants) {
        if (this.pipeline == VK_NULL_HANDLE || mesh == null || mesh.isEmpty()) {
            return;
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        // Both stages: the vertex stage reads the matrices and the fragment stage the alpha-test reference, and the
        // command's stage flags have to cover every stage that reads the range.
        vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushConstants);
        LongBuffer vertexBuffer = MemoryStack.stackGet().longs(mesh.vertexBuffer());
        vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffer, MemoryStack.stackGet().longs(0L));
        vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer(), 0L, VK_INDEX_TYPE_UINT32);
        mesh.draw(commandBuffer);
    }

    public int vertexBytes() {
        return VERTEX_BYTES;
    }

    public void delete() {
        if (this.pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(this.context.device(), this.pipeline, null);
            this.pipeline = VK_NULL_HANDLE;
        }
        if (this.pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(this.context.device(), this.pipelineLayout, null);
            this.pipelineLayout = VK_NULL_HANDLE;
        }
    }

    private void createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
            // The fragment stage reads the alpha-test reference at offset 128, so the range must be visible to both
            // stages: declaring it for the vertex stage alone is a shader-interface mismatch.
            range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0).size(PUSH_CONSTANT_BYTES);
            VkPipelineLayoutCreateInfo create = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pPushConstantRanges(range);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreatePipelineLayout(this.context.device(), create, null, handle),
                    "vkCreatePipelineLayout(immediate)");
            this.pipelineLayout = handle.get(0);
        }
    }

    private void createPipeline() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer vertexCode = SpirvShaders.load(SpirvShaders.IMMEDIATE_COLOR_VERTEX);
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
                                .cullMode(this.cull ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE)
                                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                                .lineWidth(1.0f);
                VkPipelineMultisampleStateCreateInfo multisample =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
                                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                // Depth test on so the geometry participates in the world, writes off because Minecraft's own
                // immediate path for this state does the same, and blending on so the alpha it carries is honoured.
                // Depth test back on with the comparison Minecraft's own immediate path uses (depth writes stay off,
                // which is what the state the fixture sets asks for). The previous round measured the quad appearing
                // with the test disabled; this run answers whether the comparison itself is what hides it.
                VkPipelineDepthStencilStateCreateInfo depth =
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                                .depthTestEnable(this.depthTest)
                                .depthWriteEnable(this.depthWrite)
                                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
                VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(1, stack);
                blendAttachment.get(0)
                        .blendEnable(this.blend)
                        .srcColorBlendFactor(this.srcFactor)
                        .dstColorBlendFactor(this.dstFactor)
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .alphaBlendOp(VK_BLEND_OP_ADD)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                                | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default().logicOpEnable(false).pAttachments(blendAttachment);
                VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

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
                check(vkCreateGraphicsPipelines(this.context.device(), VK_NULL_HANDLE, create, null, pipelineHandle),
                        "vkCreateGraphicsPipelines(immediate)");
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
