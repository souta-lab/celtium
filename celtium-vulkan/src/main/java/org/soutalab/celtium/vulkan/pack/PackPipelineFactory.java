/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.soutalab.celtium.core.shader.AttachmentBlendSpec;
import org.soutalab.celtium.core.shader.BlendFactor;
import org.soutalab.celtium.core.shader.BlendOp;
import org.soutalab.celtium.core.shader.BlendStateSpec;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Shader modules and pipelines for the active pack, built with dynamic rendering
 * (VkPipelineRenderingCreateInfo) and cached for the pack's lifetime. Vertex input is derived
 * from SPIR-V reflection of the vertex module: attributes in location order, tightly packed -
 * for fullscreen passes that yields exactly the executor's constant quad layout
 * (location 0 vec4 position at offset 0, location 2 vec4 texcoord at offset 16, stride 32).
 */
final class PackPipelineFactory implements AutoCloseable {

    /** Everything a graphics pipeline is built from; the executor composes the cache key. */
    record GraphicsRequest(
            String cacheKey,
            int[] vertexWords,
            int[] fragmentWords,
            int[] geometryWords,
            long pipelineLayout,
            List<Integer> colorFormats,
            int depthFormat,
            BlendStateSpec blendSpec,
            boolean depthTest,
            boolean depthWrite,
            boolean cullBack,
            int topology) {
    }

    private final VulkanContext context;
    private final Map<Integer, Long> shaderModules = new LinkedHashMap<>();
    private final Map<String, Long> pipelines = new LinkedHashMap<>();
    private PackGpuBuffer quadBuffer;

    PackPipelineFactory(VulkanContext context) {
        this.context = context;
    }

    long shaderModule(int[] words) {
        int key = Arrays.hashCode(words) * 31 + words.length;
        Long existing = shaderModules.get(key);
        if (existing != null) {
            return existing;
        }
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(stack.ints(words));
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateShaderModule(device, moduleInfo, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateShaderModule failed");
            }
            shaderModules.put(key, handle.get(0));
            return handle.get(0);
        }
    }

    /** The constant fullscreen quad (triangle strip): position vec4 + texcoord vec4 per vertex. */
    long quadBuffer() {
        if (quadBuffer == null) {
            float[] quad = {
                    -1f, -1f, 0f, 1f, 0f, 0f, 0f, 1f,
                    1f, -1f, 0f, 1f, 1f, 0f, 0f, 1f,
                    -1f, 1f, 0f, 1f, 0f, 1f, 0f, 1f,
                    1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f};
            quadBuffer = new PackGpuBuffer(context.physicalDevice(), context.device(),
                    quad.length * 4L, PackGpuBuffer.USAGE_VERTEX);
            quadBuffer.mapped().copyFrom(java.lang.foreign.MemorySegment.ofArray(quad));
        }
        return quadBuffer.bufferHandle();
    }

    long graphicsPipeline(GraphicsRequest request) {
        Long existing = pipelines.get(request.cacheKey());
        if (existing != null) {
            return existing;
        }
        VkDevice device = context.device();
        long vertexModule = shaderModule(request.vertexWords());
        long fragmentModule = shaderModule(request.fragmentWords());
        SpirvReflection.ModuleInfo vertexInfo = SpirvReflection.reflect(request.vertexWords());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int stageCount = request.geometryWords() != null ? 3 : 2;
            VkPipelineShaderStageCreateInfo.Buffer stages =
                    VkPipelineShaderStageCreateInfo.calloc(stageCount, stack);
            stages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertexModule)
                    .pName(stack.UTF8("main"));
            stages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragmentModule)
                    .pName(stack.UTF8("main"));
            if (request.geometryWords() != null) {
                stages.get(2)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_GEOMETRY_BIT)
                        .module(shaderModule(request.geometryWords()))
                        .pName(stack.UTF8("main"));
            }

            // Vertex input from reflection: attributes in location order, tightly packed.
            List<SpirvReflection.InterfaceVariable> attributes = vertexInfo.inputs();
            VkVertexInputAttributeDescription.Buffer attributeDescriptions =
                    VkVertexInputAttributeDescription.calloc(attributes.size(), stack);
            int stride = 0;
            for (int i = 0; i < attributes.size(); i++) {
                SpirvReflection.InterfaceVariable attribute = attributes.get(i);
                int format = vertexFormat(attribute);
                attributeDescriptions.get(i)
                        .location(attribute.location())
                        .binding(0)
                        .format(format)
                        .offset(stride);
                stride += formatSize(format);
            }
            VkVertexInputBindingDescription.Buffer bindingDescriptions =
                    VkVertexInputBindingDescription.calloc(1, stack);
            bindingDescriptions.get(0)
                    .binding(0)
                    .stride(Math.max(stride, 4))
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDescriptions)
                    .pVertexAttributeDescriptions(attributeDescriptions);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                            .topology(request.topology())
                            .primitiveRestartEnable(false);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);

            VkPipelineRasterizationStateCreateInfo rasterization =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                            .depthClampEnable(false)
                            .rasterizerDiscardEnable(false)
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .lineWidth(1.0f)
                            .cullMode(request.cullBack() ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE)
                            .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                            .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo depthStencil =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                            .depthTestEnable(request.depthTest())
                            .depthWriteEnable(request.depthWrite())
                            .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                            .depthBoundsTestEnable(false)
                            .stencilTestEnable(false);

            int colorCount = request.colorFormats().size();
            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(Math.max(colorCount, 1), stack);
            for (int i = 0; i < Math.max(colorCount, 1); i++) {
                AttachmentBlendSpec spec = request.blendSpec() != null && i < colorCount
                        ? request.blendSpec().forAttachment(i)
                        : AttachmentBlendSpec.opaque();
                blendAttachments.get(i)
                        .blendEnable(spec.blendEnabled())
                        .srcColorBlendFactor(blendFactor(spec.srcColorFactor()))
                        .dstColorBlendFactor(blendFactor(spec.dstColorFactor()))
                        .colorBlendOp(blendOp(spec.colorOp()))
                        .srcAlphaBlendFactor(blendFactor(spec.srcAlphaFactor()))
                        .dstAlphaBlendFactor(blendFactor(spec.dstAlphaFactor()))
                        .alphaBlendOp(blendOp(spec.alphaOp()))
                        .colorWriteMask(spec.colorWriteMask());
            }
            VkPipelineColorBlendStateCreateInfo colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .pAttachments(blendAttachments);

            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkPipelineRenderingCreateInfo rendering = VkPipelineRenderingCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO)
                    .colorAttachmentCount(colorCount)
                    .depthAttachmentFormat(request.depthFormat())
                    .stencilAttachmentFormat(0);
            if (colorCount > 0) {
                rendering.pColorAttachmentFormats(stack.ints(
                        request.colorFormats().stream().mapToInt(Integer::intValue).toArray()));
            }

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfos =
                    VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfos.get(0)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pNext(rendering)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterization)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlend)
                    .pDynamicState(dynamicState)
                    .layout(request.pipelineLayout());

            LongBuffer handle = stack.mallocLong(1);
            int result = VulkanContext.createGraphicsPipelines(device, pipelineInfos, handle, "pack");
            if (result != VK_SUCCESS || handle.get(0) == 0L) {
                throw new IllegalStateException("vkCreateGraphicsPipelines failed for "
                        + request.cacheKey() + ": " + result);
            }
            pipelines.put(request.cacheKey(), handle.get(0));
            return handle.get(0);
        }
    }

    long computePipeline(String cacheKey, int[] words, long pipelineLayout) {
        Long existing = pipelines.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkComputePipelineCreateInfo pipelineInfo = VkComputePipelineCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .layout(pipelineLayout);
            pipelineInfo.stage()
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule(words))
                    .pName(stack.UTF8("main"));
            LongBuffer handle = stack.mallocLong(1);
            int result = vkCreateComputePipelines(device, 0L, pipelineInfo, null, handle);
            if (result != VK_SUCCESS || handle.get(0) == 0L) {
                throw new IllegalStateException("vkCreateComputePipelines failed for "
                        + cacheKey + ": " + result);
            }
            pipelines.put(cacheKey, handle.get(0));
            return handle.get(0);
        }
    }

    /** Binds a pipeline and the dynamic viewport/scissor for an extent. */
    static void bindGraphics(VkCommandBuffer cmd, long pipeline, int width, int height) {
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkViewport.Buffer viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
            viewport.get(0).x(0f).y(0f).width(width).height(height).minDepth(0f).maxDepth(1f);
            vkCmdSetViewport(cmd, 0, viewport);
            org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
            scissor.get(0).offset().set(0, 0);
            scissor.get(0).extent().set(width, height);
            vkCmdSetScissor(cmd, 0, scissor);
        }
    }

    private static int vertexFormat(SpirvReflection.InterfaceVariable attribute) {
        int width = Math.max(1, Math.min(attribute.vectorWidth(), 4));
        if (attribute.floatingPoint()) {
            return switch (width) {
                case 1 -> VK_FORMAT_R32_SFLOAT;
                case 2 -> VK_FORMAT_R32G32_SFLOAT;
                case 3 -> VK_FORMAT_R32G32B32_SFLOAT;
                default -> VK_FORMAT_R32G32B32A32_SFLOAT;
            };
        }
        return switch (width) {
            case 1 -> VK_FORMAT_R32_SINT;
            case 2 -> VK_FORMAT_R32G32_SINT;
            case 3 -> VK_FORMAT_R32G32B32_SINT;
            default -> VK_FORMAT_R32G32B32A32_SINT;
        };
    }

    private static int formatSize(int format) {
        return switch (format) {
            case VK_FORMAT_R32_SFLOAT, VK_FORMAT_R32_SINT -> 4;
            case VK_FORMAT_R32G32_SFLOAT, VK_FORMAT_R32G32_SINT -> 8;
            case VK_FORMAT_R32G32B32_SFLOAT, VK_FORMAT_R32G32B32_SINT -> 12;
            default -> 16;
        };
    }

    static int blendFactor(BlendFactor factor) {
        return switch (factor) {
            case ZERO -> VK_BLEND_FACTOR_ZERO;
            case ONE -> VK_BLEND_FACTOR_ONE;
            case SRC_COLOR -> VK_BLEND_FACTOR_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> VK_BLEND_FACTOR_DST_COLOR;
            case ONE_MINUS_DST_COLOR -> VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> VK_BLEND_FACTOR_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> VK_BLEND_FACTOR_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case CONSTANT_COLOR -> VK_BLEND_FACTOR_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR -> VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA -> VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            case SRC_ALPHA_SATURATE -> VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
        };
    }

    static int blendOp(BlendOp op) {
        return switch (op) {
            case ADD -> VK_BLEND_OP_ADD;
            case SUBTRACT -> VK_BLEND_OP_SUBTRACT;
            case REVERSE_SUBTRACT -> VK_BLEND_OP_REVERSE_SUBTRACT;
            case MIN -> VK_BLEND_OP_MIN;
            case MAX -> VK_BLEND_OP_MAX;
        };
    }

    @Override
    public void close() {
        VkDevice device = context.device();
        for (long pipeline : pipelines.values()) {
            vkDestroyPipeline(device, pipeline, null);
        }
        pipelines.clear();
        for (long module : shaderModules.values()) {
            vkDestroyShaderModule(device, module, null);
        }
        shaderModules.clear();
        if (quadBuffer != null) {
            quadBuffer.close();
            quadBuffer = null;
        }
    }
}
