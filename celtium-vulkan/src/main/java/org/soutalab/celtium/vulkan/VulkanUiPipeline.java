package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
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
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * The first non-terrain pipeline: Minecraft's GUI/font vertex layout drawn by Vulkan.
 * <p>
 * This is the S4/S6 foundation in use. It consumes {@link VulkanDynamicMesh} output in the
 * {@code POSITION_TEX_LIGHTMAP_COLOR} layout Minecraft uses for the GUI and font batches, samples a texture bound at
 * descriptor set 0 binding 0, multiplies it by the vertex colour with straight alpha blending, and does not write
 * depth. It is created against the frame target's render pass, so the geometry lands in the same colour image the
 * terrain is drawn into and the existing composition still applies while the full-Vulkan migration is in progress.
 * <p>
 * Projection and model-view arrive as 128 bytes of push constants, the same convention the terrain pipelines use.
 * Threading: render thread only.
 */
public final class VulkanUiPipeline {
    /** Vertex layout offsets, mirroring {@code DefaultVertexFormats.POSITION_TEX_LIGHTMAP_COLOR}. */
    private static final int VERTEX_STRIDE = 24;
    // 116, not 132: projection 64 + the model view's three affine columns 48 + alpha reference 4, all
    // inside the 128 bytes Vulkan guarantees (audit S19).
    private static final int PUSH_CONSTANT_BYTES = 128;

    private final VulkanContext context;
    private final long renderPass;

    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;

    private VulkanUiPipeline(VulkanContext context, long renderPass) {
        this.context = context;
        this.renderPass = renderPass;
    }

    /**
     * Creates the pipeline against the render pass the frame target owns.
     *
     * @param context    owning context
     * @param renderPass the {@code VkRenderPass} the caller records with; the UI geometry must be recorded inside
     *                   that pass, exactly like the terrain geometry
     */
    public static VulkanUiPipeline create(VulkanContext context, long renderPass) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (renderPass == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("renderPass must not be null");
        }
        VulkanUiPipeline result = new VulkanUiPipeline(context, renderPass);
        result.createDescriptorSetLayout();
        result.createPipelineLayout();
        result.createPipeline();
        result.createDescriptorPool();
        return result;
    }

    /** Binds the texture this UI batch samples; the texture must stay alive while the pipeline is used. */
    public void bindTexture(VulkanGlTexture texture) {
        if (this.descriptorSet == VK_NULL_HANDLE) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                        .descriptorPool(this.descriptorPool)
                        .pSetLayouts(stack.longs(this.descriptorSetLayout));
                LongBuffer handle = stack.mallocLong(1);
                check(vkAllocateDescriptorSets(this.context.device(), allocate, handle),
                        "vkAllocateDescriptorSets(ui)");
                this.descriptorSet = handle.get(0);
            }
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack);
            image.get(0).sampler(texture.sampler()).imageView(texture.view())
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0).sType$Default()
                    .dstSet(this.descriptorSet)
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(image);
            vkUpdateDescriptorSets(this.context.device(), write, null);
        }
    }

    /** Binds the pipeline, its descriptor set and the given projection/model-view pair, then records the draw. */
    public void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, ByteBuffer pushConstants) {
        if (this.pipeline == VK_NULL_HANDLE || mesh == null || mesh.isEmpty()) {
            return;
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                org.lwjgl.system.MemoryStack.stackGet().longs(this.descriptorSet), null);
        vkCmdPushConstants(commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);
        org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers(commandBuffer, 0,
                org.lwjgl.system.MemoryStack.stackGet().longs(mesh.vertexBuffer()),
                org.lwjgl.system.MemoryStack.stackGet().longs(0L));
        vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer(), 0L, VK_INDEX_TYPE_UINT32);
        mesh.draw(commandBuffer);
    }

    public void delete() {
        if (this.descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(this.context.device(), this.descriptorPool, null);
            this.descriptorPool = VK_NULL_HANDLE;
            this.descriptorSet = VK_NULL_HANDLE;
        }
        if (this.pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(this.context.device(), this.pipeline, null);
            this.pipeline = VK_NULL_HANDLE;
        }
        if (this.pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(this.context.device(), this.pipelineLayout, null);
            this.pipelineLayout = VK_NULL_HANDLE;
        }
        if (this.descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(this.context.device(), this.descriptorSetLayout, null);
            this.descriptorSetLayout = VK_NULL_HANDLE;
        }
    }

    private void createDescriptorSetLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack);
            binding.get(0).binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo create = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                    .pBindings(binding);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(this.context.device(), create, null, handle),
                    "vkCreateDescriptorSetLayout(ui)");
            this.descriptorSetLayout = handle.get(0);
        }
    }

    private void createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
            range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(PUSH_CONSTANT_BYTES);
            VkPipelineLayoutCreateInfo create = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(this.descriptorSetLayout))
                    .pPushConstantRanges(range);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreatePipelineLayout(this.context.device(), create, null, handle), "vkCreatePipelineLayout(ui)");
            this.pipelineLayout = handle.get(0);
        }
    }

    private void createPipeline() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer vertexCode = SpirvShaders.load(SpirvShaders.UI_VERTEX);
            ByteBuffer fragmentCode = SpirvShaders.load(SpirvShaders.UI_FRAGMENT);
            long vertexModule = VK_NULL_HANDLE;
            long fragmentModule = VK_NULL_HANDLE;
            try {
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
                LongBuffer handle = stack.mallocLong(1);
                moduleInfo.pCode(vertexCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle), "vkCreateShaderModule(ui vertex)");
                vertexModule = handle.get(0);
                moduleInfo.pCode(fragmentCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle), "vkCreateShaderModule(ui fragment)");
                fragmentModule = handle.get(0);

                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule).pName(stack.UTF8("main"));
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule).pName(stack.UTF8("main"));

                VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0).binding(0).stride(VERTEX_STRIDE).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(4, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(12);
                attributes.get(2).location(2).binding(0).format(VK_FORMAT_R16G16_UINT).offset(16);
                attributes.get(3).location(3).binding(0).format(VK_FORMAT_R8G8B8A8_UNORM).offset(20);

                VkPipelineVertexInputStateCreateInfo vertexInput =
                        VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default()
                                .pVertexBindingDescriptions(binding)
                                .pVertexAttributeDescriptions(attributes);

                VkPipelineInputAssemblyStateCreateInfo assembly =
                        VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
                                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

                VkPipelineViewportStateCreateInfo viewport =
                        VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default()
                                .viewportCount(1).scissorCount(1);

                VkPipelineRasterizationStateCreateInfo raster =
                        VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
                                .polygonMode(VK_POLYGON_MODE_FILL)
                                .cullMode(VK_CULL_MODE_NONE)
                                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                                .lineWidth(1.0f);

                VkPipelineMultisampleStateCreateInfo multisample =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
                                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

                // The GUI draws over whatever the world left behind, so depth is neither tested nor written.
                VkPipelineDepthStencilStateCreateInfo depth =
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                                .depthTestEnable(false)
                                .depthWriteEnable(false)
                                .depthCompareOp(VK_COMPARE_OP_ALWAYS);

                VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(1, stack);
                blendAttachment.get(0)
                        .blendEnable(true)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
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
                        "vkCreateGraphicsPipelines(ui)");
                this.pipeline = pipelineHandle.get(0);
            } finally {
                if (vertexModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.context.device(), vertexModule, null);
                }
                if (fragmentModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.context.device(), fragmentModule, null);
                }
                org.lwjgl.system.MemoryUtil.memFree(vertexCode);
                org.lwjgl.system.MemoryUtil.memFree(fragmentCode);
            }
        }
    }

    private void createDescriptorPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer size = VkDescriptorPoolSize.calloc(1, stack);
            size.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(4);
            VkDescriptorPoolCreateInfo create = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(4)
                    .pPoolSizes(size);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateDescriptorPool(this.context.device(), create, null, handle), "vkCreateDescriptorPool(ui)");
            this.descriptorPool = handle.get(0);
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
