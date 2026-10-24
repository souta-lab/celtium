package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
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

/**
 * The depth-only pipeline of the shadow map: chunk vertices under the light's view-projection,
 * written into a depth attachment and nothing else.
 * <p>
 * The pipeline reads the actual chunk layout (24-byte compressed or 32-byte vanilla), including the packed section
 * identifier used to translate local positions. A 64-byte light matrix and a 16-byte camera-relative region offset
 * are pushed separately; no texture or uniform descriptor is required.
 */
public final class ShadowDepthPipeline {

    private final VulkanContext context;
    private final long pipelineLayout;
    private final long pipeline;
    private boolean deleted;

    private ShadowDepthPipeline(VulkanContext context, long pipelineLayout, long pipeline) {
        this.context = context;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
    }

    /** Creates the pipeline for the chunk vertex layout used by this world. */
    public static ShadowDepthPipeline create(VulkanContext context, long renderPass, int vertexStride) {
        if (vertexStride != 24 && vertexStride != 32) {
            throw new IllegalArgumentException("unsupported shadow vertex stride " + vertexStride);
        }
        VkDevice device = context.device();
        long layout = VK_NULL_HANDLE;
        long pipeline = VK_NULL_HANDLE;
        try (MemoryStack stack = stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
            range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(80);
            VkPipelineLayoutCreateInfo createLayout = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pPushConstantRanges(range);
            check(vkCreatePipelineLayout(device, createLayout, null, handle), "vkCreatePipelineLayout(shadow depth)");
            layout = handle.get(0);

            ByteBuffer vertexCode = SpirvShaders.load(vertexStride == 24
                    ? SpirvShaders.SHADOW_DEPTH_VERTEX : SpirvShaders.SHADOW_DEPTH_VANILLA_VERTEX);
            ByteBuffer fragmentCode = SpirvShaders.load(SpirvShaders.SHADOW_DEPTH_FRAGMENT);
            long vertexModule = VK_NULL_HANDLE;
            long fragmentModule = VK_NULL_HANDLE;
            try {
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
                moduleInfo.pCode(vertexCode);
                check(vkCreateShaderModule(device, moduleInfo, null, handle), "vkCreateShaderModule(shadow vertex)");
                vertexModule = handle.get(0);
                moduleInfo.pCode(fragmentCode);
                check(vkCreateShaderModule(device, moduleInfo, null, handle), "vkCreateShaderModule(shadow fragment)");
                fragmentModule = handle.get(0);

                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule)
                        .pName(stack.UTF8("main"));
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                        .pName(stack.UTF8("main"));

                VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0).binding(0).stride(vertexStride).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(2, stack);
                attributes.get(0).location(0).binding(0).format(vertexStride == 24
                        ? VK_FORMAT_R16G16B16A16_UINT : VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32_UINT)
                        .offset(vertexStride == 24 ? 16 : 24);
                VkPipelineVertexInputStateCreateInfo vertexInput =
                        VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default()
                                .pVertexBindingDescriptions(binding)
                                .pVertexAttributeDescriptions(attributes);

                VkPipelineInputAssemblyStateCreateInfo assembly =
                        VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
                                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType$Default().viewportCount(1).scissorCount(1);
                VkPipelineRasterizationStateCreateInfo raster =
                        VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
                                .polygonMode(VK_POLYGON_MODE_FILL)
                                // Both faces are written: a depth map only needs the nearest surface, and culling here
                                // would silently drop casters whose winding the terrain pass happens to reverse.
                                .cullMode(VK_CULL_MODE_NONE)
                                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                                .lineWidth(1.0f);
                VkPipelineMultisampleStateCreateInfo multisample =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
                                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                VkPipelineDepthStencilStateCreateInfo depth =
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                                .depthTestEnable(true).depthWriteEnable(true)
                                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
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
                        .pDynamicState(dynamic)
                        // No colour attachments: a depth-only pass declares an empty blend state, which is why
                        // pColorBlendState stays null here rather than naming zero attachments.
                        .layout(layout)
                        .renderPass(renderPass)
                        .subpass(0);
                LongBuffer pipelineHandle = stack.mallocLong(1);
                check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, create, null, pipelineHandle),
                        "vkCreateGraphicsPipelines(shadow depth)");
                pipeline = pipelineHandle.get(0);
            } finally {
                if (vertexModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(device, vertexModule, null);
                }
                if (fragmentModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(device, fragmentModule, null);
                }
                MemoryUtil.memFree(vertexCode);
                MemoryUtil.memFree(fragmentCode);
            }
        }
        return new ShadowDepthPipeline(context, layout, pipeline);
    }

    /** Binds the pipeline and pushes the light's view-projection (64 bytes, column-major). */
    public void bind(VkCommandBuffer commandBuffer, ByteBuffer lightMatrix) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        vkCmdPushConstants(commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, lightMatrix);
    }

    /** The region origin relative to the camera; the vertex's section id supplies the remaining translation. */
    public void bindRegion(VkCommandBuffer commandBuffer, ByteBuffer regionOffset) {
        vkCmdPushConstants(commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 64, regionOffset);
    }

    public long pipeline() {
        return this.pipeline;
    }

    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        VkDevice device = this.context.device();
        vkDestroyPipeline(device, this.pipeline, null);
        vkDestroyPipelineLayout(device, this.pipelineLayout, null);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result);
        }
    }
}
