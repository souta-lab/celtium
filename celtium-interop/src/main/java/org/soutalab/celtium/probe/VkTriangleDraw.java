package org.soutalab.celtium.probe;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL11C.glFlush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/** One Vulkan triangle with real depth testing between external ownership transfers. */
final class VkTriangleDraw implements AutoCloseable {
    private final VkInteropDevice owner;
    private final SharedImage color, depth;
    private ExternalSemaphore glToVk, vkToGl;
    private long renderPass, framebuffer, layout, pipeline;

    VkTriangleDraw(VkInteropDevice owner, SharedImage color, SharedImage depth) throws IOException {
        this.owner = owner;
        this.color = color;
        this.depth = depth;
        try {
            createPipeline();
            glToVk = new ExternalSemaphore(owner);
            vkToGl = new ExternalSemaphore(owner);
        } catch (RuntimeException | LinkageError | IOException error) {
            close();
            throw error;
        }
    }

    private void createPipeline() throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
            for (int i = 0; i < 2; i++) attachments.get(i).format(i == 0 ? color.format : depth.format)
                    .samples(VK_SAMPLE_COUNT_1_BIT).loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_GENERAL).finalLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack);
            colorReference.get(0).attachment(0).layout(VK_IMAGE_LAYOUT_GENERAL);
            VkAttachmentReference depthReference = VkAttachmentReference.calloc(stack).attachment(1).layout(VK_IMAGE_LAYOUT_GENERAL);
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS).colorAttachmentCount(1)
                    .pColorAttachments(colorReference).pDepthStencilAttachment(depthReference);
            VkRenderPassCreateInfo passInfo = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                    .pAttachments(attachments).pSubpasses(subpass);
            LongBuffer handle = stack.mallocLong(1);
            VkInteropDevice.check(vkCreateRenderPass(owner.device, passInfo, null, handle), "create proof render pass");
            renderPass = handle.get(0);
            VkFramebufferCreateInfo target = VkFramebufferCreateInfo.calloc(stack).sType$Default().renderPass(renderPass)
                    .pAttachments(stack.longs(color.view, depth.view)).width(color.width).height(color.height).layers(1);
            VkInteropDevice.check(vkCreateFramebuffer(owner.device, target, null, handle), "create Vulkan framebuffer");
            framebuffer = handle.get(0);
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
            range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT).offset(0).size(32);
            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pPushConstantRanges(range);
            VkInteropDevice.check(vkCreatePipelineLayout(owner.device, layoutInfo, null, handle), "create proof pipeline layout");
            layout = handle.get(0);
            long vertex = 0, fragment = 0;
            try {
                vertex = shader("/fullscreen.vert.spv");
                fragment = shader("/solid.frag.spv");
                VkPipelineShaderStageCreateInfo.Buffer shaders = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                shaders.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertex).pName(stack.UTF8("main"));
                shaders.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragment).pName(stack.UTF8("main"));
                VkPipelineVertexInputStateCreateInfo vertices = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
                VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
                        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default()
                        .viewportCount(1).scissorCount(1);
                VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
                        .polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1);
                VkPipelineMultisampleStateCreateInfo samples = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
                        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                VkPipelineDepthStencilStateCreateInfo depthState = VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                        .depthTestEnable(true).depthWriteEnable(true).depthCompareOp(VK_COMPARE_OP_LESS).minDepthBounds(0).maxDepthBounds(1);
                VkPipelineColorBlendAttachmentState.Buffer blendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
                blendAttachment.get(0).colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default().pAttachments(blendAttachment);
                VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                        .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
                VkGraphicsPipelineCreateInfo.Buffer create = VkGraphicsPipelineCreateInfo.calloc(1, stack);
                create.get(0).sType$Default().pStages(shaders).pVertexInputState(vertices).pInputAssemblyState(assembly)
                        .pViewportState(viewport).pRasterizationState(raster).pMultisampleState(samples).pDepthStencilState(depthState)
                        .pColorBlendState(blend).pDynamicState(dynamic).layout(layout).renderPass(renderPass).subpass(0);
                VkInteropDevice.check(vkCreateGraphicsPipelines(owner.device, VK_NULL_HANDLE, create, null, handle), "create proof graphics pipeline");
                pipeline = handle.get(0);
            } finally {
                if (vertex != 0) vkDestroyShaderModule(owner.device, vertex, null);
                if (fragment != 0) vkDestroyShaderModule(owner.device, fragment, null);
            }
        }
    }

    private long shader(String resource) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = VkTriangleDraw.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing compiled shader " + resource);
            byte[] chunk = new byte[4096];
            int count;
            while ((count = stream.read(chunk)) >= 0) bytes.write(chunk, 0, count);
        }
        ByteBuffer code = MemoryUtil.memAlloc(bytes.size());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            code.put(bytes.toByteArray()).flip();
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer handle = stack.mallocLong(1);
            VkInteropDevice.check(vkCreateShaderModule(owner.device, info, null, handle), "create shader module");
            return handle.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    void initializeOwnership() {
        VkCommandBuffer command = owner.begin();
        barrier(command, true, true);
        owner.submit(0, vkToGl.semaphore);
        vkToGl.await(color, depth);
    }

    void roundtrip(GlDepthBridge gl) {
        roundtrip(gl, 0.5f, true);
    }

    void roundtrip(GlDepthBridge gl, float triangleDepth, boolean clearOriginalForOracle) {
        gl.toVulkan();
        glToVk.signal(color, depth);
        glFlush(); // Make the signal available before Vulkan waits on it.
        VkCommandBuffer command = owner.begin();
        barrier(command, false, false);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderPassBeginInfo pass = VkRenderPassBeginInfo.calloc(stack).sType$Default().renderPass(renderPass).framebuffer(framebuffer);
            pass.renderArea().offset().set(0, 0);
            pass.renderArea().extent().set(color.width, color.height);
            vkCmdBeginRenderPass(command, pass, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0).x(0).y(0).width(color.width).height(color.height).minDepth(0).maxDepth(1);
            vkCmdSetViewport(command, 0, viewport);
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset().set(0, 0);
            scissor.get(0).extent().set(color.width, color.height);
            vkCmdSetScissor(command, 0, scissor);
            ByteBuffer values = stack.calloc(32);
            values.putFloat(0, triangleDepth).putFloat(16, 0).putFloat(20, 1).putFloat(24, 0).putFloat(28, 1);
            vkCmdPushConstants(command, layout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, values);
            vkCmdDraw(command, 3, 1, 0, 0);
            vkCmdEndRenderPass(command);
        }
        barrier(command, true, false);
        owner.submit(glToVk.semaphore, vkToGl.semaphore);
        vkToGl.await(color, depth);
        // Destroy the original contents so a missing return copy cannot pass.
        // This is a functional-test oracle, disabled in cost measurements.
        if (clearOriginalForOracle) gl.clearOriginal();
        gl.fromVulkan();
    }

    private void barrier(VkCommandBuffer command, boolean release, boolean initialize) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);
            SharedImage[] images = { color, depth };
            for (int i = 0; i < images.length; i++) {
                int access = i == 0 ? VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        : VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                barriers.get(i).sType$Default().image(images[i].image)
                        .oldLayout(initialize ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .srcQueueFamilyIndex(release ? owner.queueFamily : VK_QUEUE_FAMILY_EXTERNAL)
                        .dstQueueFamilyIndex(release ? VK_QUEUE_FAMILY_EXTERNAL : owner.queueFamily)
                        .srcAccessMask(release && !initialize ? access : 0).dstAccessMask(release ? 0 : access)
                        .subresourceRange().set(images[i].aspect, 0, 1, 0, 1);
            }
            vkCmdPipelineBarrier(command, initialize ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
        }
    }

    @Override public void close() {
        if (glToVk != null) { glToVk.close(); glToVk = null; }
        if (vkToGl != null) { vkToGl.close(); vkToGl = null; }
        if (pipeline != 0) { vkDestroyPipeline(owner.device, pipeline, null); pipeline = 0; }
        if (layout != 0) { vkDestroyPipelineLayout(owner.device, layout, null); layout = 0; }
        if (framebuffer != 0) { vkDestroyFramebuffer(owner.device, framebuffer, null); framebuffer = 0; }
        if (renderPass != 0) { vkDestroyRenderPass(owner.device, renderPass, null); renderPass = 0; }
    }
}
