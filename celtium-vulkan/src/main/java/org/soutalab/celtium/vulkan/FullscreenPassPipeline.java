package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

/** Dynamic-rendering pipeline for a position/UV quad or a shader-generated fullscreen strip. */
final class FullscreenPassPipeline {
    private static final int STAGES = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    private static final int SETS = 256;

    record Image(long view, long sampler, int layout) {}

    private final VulkanContext context;
    private final VkDevice device;
    private final int samplerCount;
    private final boolean uniformBuffer;
    private long setLayout;
    private long layout;
    private long pipeline;
    private long pool;
    private boolean retired;

    FullscreenPassPipeline(VulkanContext context, byte[] vertex, byte[] fragment, int samplerCount,
                           int pushBytes, boolean uniformBuffer, boolean mesh, int colorFormat, int depthFormat,
                           VulkanColorBlendState blendState, boolean depthTest, boolean depthWrite,
                           int depthCompareOp, int cull) {
        this.context = context;
        this.device = context.device();
        this.samplerCount = samplerCount;
        this.uniformBuffer = uniformBuffer;

        long vertexModule = 0;
        long fragmentModule = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.callocLong(1);
            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default();
            int bindingCount = samplerCount + (uniformBuffer ? 1 : 0);
            if (bindingCount > 0) {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(bindingCount, stack);
                for (int i = 0; i < samplerCount; i++) {
                    bindings.get(i).binding(i).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                            .descriptorCount(1).stageFlags(STAGES);
                }
                if (uniformBuffer) {
                    bindings.get(samplerCount).binding(2).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                            .descriptorCount(1).stageFlags(STAGES);
                }
                var setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(bindings);
                check(vkCreateDescriptorSetLayout(device, setInfo, null, handle), "descriptor set layout");
                setLayout = handle.get(0);
                layoutInfo.pSetLayouts(stack.longs(setLayout));
            }
            if (pushBytes > 0) {
                var range = VkPushConstantRange.calloc(1, stack);
                range.get(0).stageFlags(STAGES).offset(0).size(pushBytes);
                layoutInfo.pPushConstantRanges(range);
            }
            check(vkCreatePipelineLayout(device, layoutInfo, null, handle), "pipeline layout");
            layout = handle.get(0);

            vertexModule = shaderModule(vertex);
            fragmentModule = shaderModule(fragment);
            var stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule).pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule).pName(stack.UTF8("main"));

            var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
            if (mesh) {
                var bindings = VkVertexInputBindingDescription.calloc(1, stack);
                bindings.get(0).binding(0).stride(VulkanImmediateTextureRecordsPipeline.POSITION_TEX_BYTES)
                        .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                var attributes = VkVertexInputAttributeDescription.calloc(2, stack);
                attributes.get(0).binding(0).location(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).binding(0).location(1).format(VK_FORMAT_R32G32_SFLOAT).offset(12);
                vertexInput.pVertexBindingDescriptions(bindings).pVertexAttributeDescriptions(attributes);
            }
            var assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
                    .topology(mesh ? VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST : VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP);
            var viewport = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default().viewportCount(1).scissorCount(1);
            var raster = VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
                    .polygonMode(VK_POLYGON_MODE_FILL).lineWidth(1.0f)
                    .cullMode(VulkanCullState.vkCullMode(cull)).frontFace(VulkanCullState.vkFrontFace(cull));
            var multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            var depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                    .depthTestEnable(depthFormat != VK_FORMAT_UNDEFINED && depthTest)
                    .depthWriteEnable(depthFormat != VK_FORMAT_UNDEFINED && depthWrite).depthCompareOp(depthCompareOp);
            var blend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default();
            var rendering = VkPipelineRenderingCreateInfo.calloc(stack).sType$Default().depthAttachmentFormat(depthFormat);
            if (colorFormat != VK_FORMAT_UNDEFINED) {
                var attachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
                attachment.get(0).blendEnable(blendState.blend()).colorWriteMask(blendState.writeMask())
                        .srcColorBlendFactor(blendState.srcColor()).dstColorBlendFactor(blendState.dstColor())
                        .colorBlendOp(blendState.colorOp()).srcAlphaBlendFactor(blendState.srcAlpha())
                        .dstAlphaBlendFactor(blendState.dstAlpha()).alphaBlendOp(blendState.alphaOp());
                blend.pAttachments(attachment);
                rendering.pColorAttachmentFormats(stack.ints(colorFormat));
            }
            var dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
            var info = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            info.get(0).sType$Default().pNext(rendering.address()).pStages(stages).pVertexInputState(vertexInput)
                    .pInputAssemblyState(assembly).pViewportState(viewport).pRasterizationState(raster)
                    .pMultisampleState(multisample).pDepthStencilState(depth).pColorBlendState(blend)
                    .pDynamicState(dynamic).layout(layout).basePipelineIndex(-1);
            handle.put(0, VK_NULL_HANDLE);
            int result = VulkanContext.createGraphicsPipelines(device, info, handle, "fullscreen-pass");
            pipeline = handle.get(0);
            check(result, "graphics pipeline");
        } catch (RuntimeException | Error failure) {
            destroyIdle();
            throw failure;
        } finally {
            if (fragmentModule != 0) vkDestroyShaderModule(device, fragmentModule, null);
            if (vertexModule != 0) vkDestroyShaderModule(device, vertexModule, null);
        }
    }

    private long shaderModule(byte[] spirv) {
        ByteBuffer code = MemoryUtil.memAlloc(spirv.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            code.put(spirv).flip();
            var info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateShaderModule(device, info, null, handle), "shader module");
            return handle.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    long descriptorSet(MemoryStack stack, Image[] images, long buffer, long offset, int bytes) {
        if (setLayout == 0) return 0;
        LongBuffer handle = stack.mallocLong(1);
        if (pool == 0) {
            var sizes = VkDescriptorPoolSize.calloc((samplerCount > 0 && uniformBuffer) ? 2 : 1, stack);
            if (samplerCount > 0) {
                sizes.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(SETS * samplerCount);
            }
            if (uniformBuffer) {
                sizes.get(sizes.capacity() - 1).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(SETS);
            }
            var info = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT).maxSets(SETS).pPoolSizes(sizes);
            check(vkCreateDescriptorPool(device, info, null, handle), "descriptor pool");
            pool = handle.get(0);
        }
        var alloc = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                .descriptorPool(pool).pSetLayouts(stack.longs(setLayout));
        check(vkAllocateDescriptorSets(device, alloc, handle), "descriptor allocation");
        long set = handle.get(0);
        // The descriptor is immutable until the recording that can reference it has completed.
        long ownerPool = pool;
        context.celtium$retireCleanup("fullscreen pass descriptor", () -> {
            if (!retired) vkFreeDescriptorSets(device, ownerPool, set);
        });
        var writes = VkWriteDescriptorSet.calloc(samplerCount + (uniformBuffer ? 1 : 0), stack);
        var infos = VkDescriptorImageInfo.calloc(samplerCount, stack);
        for (int i = 0; i < samplerCount; i++) {
            Image image = images[i];
            infos.get(i).imageView(image.view()).sampler(image.sampler()).imageLayout(image.layout());
            writes.get(i).sType$Default().dstSet(set).dstBinding(i).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(VkDescriptorImageInfo.create(infos.get(i).address(), 1));
        }
        if (uniformBuffer) {
            var info = VkDescriptorBufferInfo.calloc(1, stack);
            info.get(0).buffer(buffer).offset(offset).range(bytes);
            writes.get(samplerCount).sType$Default().dstSet(set).dstBinding(2).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).pBufferInfo(info);
        }
        vkUpdateDescriptorSets(device, writes, null);
        return set;
    }

    void draw(VkCommandBuffer cmd, MemoryStack stack, VulkanDynamicMesh mesh, ByteBuffer push, long set) {
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        if (set != 0) vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, layout, 0, stack.longs(set), null);
        if (push != null) vkCmdPushConstants(cmd, layout, STAGES, 0, push);
        VulkanPushCache.SHARED.invalidate();
        if (mesh == null) vkCmdDraw(cmd, 4, 1, 0, 0);
        else mesh.bindAndDraw(cmd);
    }

    static void transition(VkCommandBuffer cmd, MemoryStack stack, long image, int aspect, int oldLayout,
                           int newLayout, long dstStage, long dstAccess) {
        boolean undefined = oldLayout == VK_IMAGE_LAYOUT_UNDEFINED;
        var barriers = VkImageMemoryBarrier2.calloc(1, stack);
        barriers.get(0).sType$Default().image(image).oldLayout(oldLayout).newLayout(newLayout)
                .srcStageMask(undefined ? VK_PIPELINE_STAGE_2_NONE : VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                .srcAccessMask(undefined ? 0 : VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT)
                .dstStageMask(dstStage).dstAccessMask(dstAccess)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .subresourceRange().aspectMask(aspect).levelCount(1).layerCount(1);
        BackendCallCounters.noteBarrier();
        vkCmdPipelineBarrier2(cmd, VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
    }

    static void viewport(VkCommandBuffer cmd, MemoryStack stack, int width, int height) {
        var viewport = VkViewport.calloc(1, stack);
        viewport.get(0).set(0, 0, width, height, 0, 1);
        vkCmdSetViewport(cmd, 0, viewport);
        var scissor = VkRect2D.calloc(1, stack);
        scissor.get(0).extent().set(width, height);
        vkCmdSetScissor(cmd, 0, scissor);
    }

    void delete() {
        if (retired) return;
        retired = true;
        context.celtium$retireCleanup("fullscreen pass pipeline", this::destroyIdle);
        // Context teardown also drains device-owned retirements after the idle wait.
        VulkanRetireQueue.retire(device.address(), this::destroyIdle);
    }

    void destroyIdle() {
        retired = true;
        if (pool != 0) vkDestroyDescriptorPool(device, pool, null);
        if (pipeline != 0) vkDestroyPipeline(device, pipeline, null);
        if (layout != 0) vkDestroyPipelineLayout(device, layout, null);
        if (setLayout != 0) vkDestroyDescriptorSetLayout(device, setLayout, null);
        pool = pipeline = layout = setLayout = 0;
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException("Fullscreen pass " + operation + " failed with VkResult " + result);
        }
    }
}
