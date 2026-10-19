package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkRenderingAttachmentInfoKHR;
import org.lwjgl.vulkan.VkRenderingInfoKHR;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.soutalab.celtium.shaderpack.LegacyGlslLowering;
import org.soutalab.celtium.shaderpack.SpirvModule;
import org.soutalab.celtium.shaderpack.Stage;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Draws one triangle with a shaderpack's own shaders into the delivered image, through dynamic rendering.
 * <p>
 * This is the first place the pack's code runs on the GPU rather than merely being compiled: the pipeline comes from
 * {@link ShaderPackPipelineFactory}, set 0 is written with an identity transform block, a zeroed uniform block and the
 * renderer's white texture for every sampler the program declares, and the draw is a full-screen triangle. Its purpose
 * is to be the smallest thing that cannot pass without the shader working - the fragment stage samples the white
 * texture, multiplies by the interpolated colour and writes it - and to be visible in a screenshot, which is what makes
 * it evidence rather than a log line.
 */
final class ShaderPackDrawProbe implements AutoCloseable {
    /** A full-screen triangle in clip space, plus the UVs that reach the same corners. */
    private static final float[] POSITIONS = {0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f};
    private static final float[] UVS = {0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f};
    private static final int TRANSFORM_BYTES = 752;
    private static final int UNIFORM_BYTES = 4096;

    private final VulkanContext context;
    private final long pipeline;
    private final long pipelineLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final List<VulkanHostBuffer> buffers = new ArrayList<>();
    /** The vertex buffers, in binding order; the uniform buffers are in {@link #buffers} for cleanup only. */
    private final List<VulkanHostBuffer> vertexBuffers = new ArrayList<>();
    /** The attribute location each vertex buffer belongs to; the pipeline declares binding == location. */
    private final List<Integer> vertexBufferLocations = new ArrayList<>();
    private final int vertexCount;

    /** Whether a descriptor set is bound at all; the built-in triangle's layout has none. */
    private final boolean builtin;

    private ShaderPackDrawProbe(VulkanContext context, long pipeline, long pipelineLayout, long descriptorPool,
                                long descriptorSet, int vertexCount) {
        this(context, pipeline, pipelineLayout, descriptorPool, descriptorSet, vertexCount, false);
    }

    private ShaderPackDrawProbe(VulkanContext context, long pipeline, long pipelineLayout, long descriptorPool,
                                long descriptorSet, int vertexCount, boolean builtin) {
        this.builtin = builtin;
        this.context = context;
        this.pipeline = pipeline;
        this.pipelineLayout = pipelineLayout;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.vertexCount = vertexCount;
    }

    /** {@return the probe for this program, creating the pipeline, descriptors and vertex buffers on first use}. */
    static ShaderPackDrawProbe create(VulkanContext context, LegacyGlslLowering.Result vertex,
                                      LegacyGlslLowering.Result fragment, SpirvModule vertexModule,
                                      SpirvModule fragmentModule, int colorFormat, int depthFormat,
                                      VulkanGlTexture whiteTexture) {
        ShaderPackPipelineFactory factory = new ShaderPackPipelineFactory(context);
        ShaderPackPipelineFactory.ModuleHandle vertexHandle = factory.createModule(vertexModule);
        ShaderPackPipelineFactory.ModuleHandle fragmentHandle = factory.createModule(fragmentModule);
        ShaderPackPipelineFactory.LayoutHandle layout = factory.createLayout(vertex, fragment);
        // Without depth testing: the frame's depth buffer already holds the world, and a probe triangle drawn with
        // the identity transform sits behind it - measured as a recorded draw that appeared in no screenshot.
        long pipeline = factory.createPipeline(layout.pipelineLayout(), vertexHandle, fragmentHandle, vertex,
                colorFormat, depthFormat, false);
        vertexHandle.close();
        fragmentHandle.close();

        LongBuffer pool = MemoryUtil.memAllocLong(1);
        LongBuffer set = MemoryUtil.memAllocLong(1);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(2, stack);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(2);
            sizes.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(8);
            int result = vkCreateDescriptorPool(context.device(), VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(1).pPoolSizes(sizes), null, pool);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDescriptorPool(probe) failed: " + result);
            }
            result = vkAllocateDescriptorSets(context.device(), VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default().descriptorPool(pool.get(0))
                    .pSetLayouts(stack.longs(layout.descriptorSetLayout())), set);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkAllocateDescriptorSets(probe) failed: " + result);
            }
            ShaderPackDrawProbe probe = new ShaderPackDrawProbe(context, pipeline, layout.pipelineLayout(), pool.get(0),
                    set.get(0), 3);
            probe.writeDescriptors(vertex, fragment, layout, whiteTexture);
            return probe;
        } finally {
            MemoryUtil.memFree(pool);
            MemoryUtil.memFree(set);
        }
    }

    /** Writes the identity transform block, the zeroed uniform block and the white texture for each sampler. */
    private void writeDescriptors(LegacyGlslLowering.Result vertex, LegacyGlslLowering.Result fragment,
                                  ShaderPackPipelineFactory.LayoutHandle layout, VulkanGlTexture whiteTexture) {
        List<VkWriteDescriptorSet> writes = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer buffer = VkWriteDescriptorSet.calloc(6, stack);
            int count = 0;
            VulkanHostBuffer transforms = null;
            if (!vertex.usedTransformBlock().isEmpty() || !fragment.usedTransformBlock().isEmpty()) {
                transforms = new VulkanHostBuffer(context, TRANSFORM_BYTES, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
                buffers.add(transforms);
                FloatBuffer data = MemoryUtil.memFloatBuffer(transforms.mappedAddress(), TRANSFORM_BYTES / 4);
                for (int i = 0; i < TRANSFORM_BYTES / 4; i++) {
                    data.put(i, 0.0f);
                }
                int[] matrixStarts = {0, 8 * 16, 8 * 16 + 12, 8 * 16 + 28, 8 * 16 + 44};
                for (int start : matrixStarts) {
                    for (int row = 0; row < 4; row++) {
                        data.put(start + row * 4 + row, 1.0f);
                    }
                }
                VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(transforms.buffer()).offset(0).range(TRANSFORM_BYTES);
                buffer.get(count++).sType$Default().dstSet(descriptorSet).dstBinding(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1).pBufferInfo(info);
            }
            if (layout.uniformBlockBinding() >= 0) {
                VulkanHostBuffer uniforms = new VulkanHostBuffer(context, UNIFORM_BYTES,
                        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
                buffers.add(uniforms);
                MemoryUtil.memSet(uniforms.mappedAddress(), 0, UNIFORM_BYTES);
                VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(uniforms.buffer()).offset(0).range(UNIFORM_BYTES);
                buffer.get(count++).sType$Default().dstSet(descriptorSet)
                        .dstBinding(layout.uniformBlockBinding())
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1).pBufferInfo(info);
            }
            for (Integer binding : layout.samplerBindings().values()) {
                VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack)
                        .imageView(whiteTexture.view()).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .sampler(whiteTexture.sampler());
                buffer.get(count++).sType$Default().dstSet(descriptorSet).dstBinding(binding.intValue())
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1).pImageInfo(image);
            }
            buffer.limit(count);
            vkUpdateDescriptorSets(context.device(), buffer, null);
        }
        // One vertex buffer per used attribute: the pipeline declares a binding per attribute, and the triangle's
        // positions go to the attribute the vertex shader reads as position while the rest are zeroed.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (Map.Entry<String, Integer> attribute : vertex.attributes().entrySet()) {
                String type = vertex.attributeTypes().get(attribute.getKey());
                int components = components(type);
                if (components <= 0) {
                    continue;
                }
                int bytes = 3 * components * 4;
                VulkanHostBuffer vertexBuffer = new VulkanHostBuffer(context, bytes, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
                buffers.add(vertexBuffer);
                vertexBuffers.add(vertexBuffer);
                vertexBufferLocations.add(attribute.getValue());
                FloatBuffer data = MemoryUtil.memFloatBuffer(vertexBuffer.mappedAddress(), bytes / 4);
                for (int i = 0; i < 3 * components; i++) {
                    data.put(i, 0.0f);
                }
                float[] source = "a_Position".equals(attribute.getKey()) ? POSITIONS : UVS;
                for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                    for (int component = 0; component < Math.min(components, 2); component++) {
                        int at = vertexIndex * 2 + component;
                        data.put(vertexIndex * components + component, at < source.length ? source[at] : 0.0f);
                    }
                }
            }
        }
    }

    private static int components(String type) {
        if (type == null) {
            return 0;
        }
        switch (type) {
            case "float": return 1;
            case "vec2": case "ivec2": return 2;
            case "vec3": case "ivec3": return 3;
            case "vec4": case "ivec4": return 4;
            default: return 0;
        }
    }

    /**
     * Clears the delivered image's colour attachment instead of drawing, for the question "is this command buffer the one
     * the present submits at all". If the picture turns magenta the recording is submitted and the draw is the problem;
     * if it does not, the hook is recording into a buffer nobody sends.
     */
    void recordClear(VkCommandBuffer commandBuffer, GllessFrameTarget target) {
        recordClear(commandBuffer, target, 1280, 720);
    }

    /**
     * Clears a smaller area, the same one the triangle's viewport covers: if this corner changes, the area and the
     * attachment are fine and the draw state is what fails; if it does not, the small-area path itself is broken.
     */
    void recordClear(VkCommandBuffer commandBuffer, GllessFrameTarget target, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfoKHR.Buffer colour = VkRenderingAttachmentInfoKHR.calloc(1, stack);
            colour.get(0).sType$Default().imageView(target.colorView())
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            org.lwjgl.vulkan.VkClearValue clear = org.lwjgl.vulkan.VkClearValue.calloc(stack);
            clear.color().float32(0, 1.0f);
            clear.color().float32(1, 0.0f);
            clear.color().float32(2, 1.0f);
            clear.color().float32(3, 1.0f);
            colour.get(0).clearValue(clear);
            org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                    .offset(it -> it.set(0, 0)).extent(it -> it.set(width, height));
            VkRenderingInfoKHR rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                    .renderArea(area).layerCount(1).pColorAttachments(colour);
            vkCmdBeginRenderingKHR(commandBuffer, rendering);
            vkCmdEndRenderingKHR(commandBuffer);
        }
    }

    /**
     * A triangle with hard-coded shaders and no pack at all, drawn through the same record path.
     * <p>
     * The experiment that separates the two explanations for a recorded draw nobody can see: if this appears, the
     * recording, pipeline state, descriptors and vertex setup are all sound and what fails is the pack program's own use
     * of its blocks; if it does not, the draw state is still wrong.
     */
    static ShaderPackDrawProbe createBuiltin(VulkanContext context, ShadercGlslCompiler compiler, int colorFormat,
                                             int depthFormat) throws Exception {
        String vertexSource = "#version 450\n"
                + "layout(location = 0) in vec3 a_Position;\n"
                + "layout(location = 0) out vec3 v_Colour;\n"
                + "void main() { v_Colour = vec3(1.0, 0.0, 0.0); gl_Position = vec4(a_Position, 1.0); }\n";
        String fragmentSource = "#version 450\n"
                + "layout(location = 0) in vec3 v_Colour;\n"
                + "layout(location = 0) out vec4 o_Colour;\n"
                + "void main() { o_Colour = vec4(v_Colour, 1.0); }\n";
        SpirvModule vertexModule = compiler.compile(Stage.VERTEX, vertexSource);
        SpirvModule fragmentModule = compiler.compile(Stage.FRAGMENT, fragmentSource);
        ShaderPackPipelineFactory factory = new ShaderPackPipelineFactory(context);
        ShaderPackPipelineFactory.ModuleHandle vertexHandle = factory.createModule(vertexModule);
        ShaderPackPipelineFactory.ModuleHandle fragmentHandle = factory.createModule(fragmentModule);
        LongBuffer layoutHandle = MemoryUtil.memAllocLong(1);
        LongBuffer pipelineLayoutHandle = MemoryUtil.memAllocLong(1);
        LongBuffer pipelineHandle = MemoryUtil.memAllocLong(1);
        LongBuffer pool = MemoryUtil.memAllocLong(1);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int result = vkCreatePipelineLayout(context.device(), org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
                    .calloc(stack).sType$Default(), null, pipelineLayoutHandle);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreatePipelineLayout(builtin) failed: " + result);
            }
            // The built-in shaders sample nothing, so the layout has no descriptor sets and no set is bound.
            result = vkCreateDescriptorPool(context.device(), VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(1), null, pool);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDescriptorPool(builtin) failed: " + result);
            }
            long pipeline = factory.createBuiltinTrianglePipeline(pipelineLayoutHandle.get(0), vertexHandle,
                    fragmentHandle, colorFormat, depthFormat);
            ShaderPackDrawProbe probe = new ShaderPackDrawProbe(context, pipeline, pipelineLayoutHandle.get(0),
                    pool.get(0), 0L, 3, true);
            VulkanHostBuffer vertices = new VulkanHostBuffer(context, 3 * 3 * 4, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
            probe.buffers.add(vertices);
            probe.vertexBuffers.add(vertices);
            probe.vertexBufferLocations.add(Integer.valueOf(0));
            FloatBuffer data = MemoryUtil.memFloatBuffer(vertices.mappedAddress(), 9);
            float[] triangle = {-1.0f, -1.0f, 0.0f, 3.0f, -1.0f, 0.0f, -1.0f, 3.0f, 0.0f};
            for (int i = 0; i < 9; i++) {
                data.put(i, triangle[i]);
            }
            return probe;
        } finally {
            MemoryUtil.memFree(layoutHandle);
            MemoryUtil.memFree(pipelineLayoutHandle);
            MemoryUtil.memFree(pipelineHandle);
            MemoryUtil.memFree(pool);
            vertexHandle.close();
            fragmentHandle.close();
        }
    }

    /** Records the draw into the delivered image. The caller has ended any render pass already. */
    void record(VkCommandBuffer commandBuffer, GllessFrameTarget target) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfoKHR.Buffer colour = VkRenderingAttachmentInfoKHR.calloc(1, stack);
            colour.get(0).sType$Default().imageView(target.colorView())
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingAttachmentInfoKHR.Buffer depth = VkRenderingAttachmentInfoKHR.calloc(1, stack);
            depth.get(0).sType$Default().imageView(target.depthView())
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                    .offset(it -> it.set(0, 0)).extent(it -> it.set(64, 64));
            VkRenderingInfoKHR rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                    .renderArea(area)
                    .layerCount(1)
                    .pColorAttachments(colour)
                    .pDepthAttachment(depth.get(0));
            vkCmdBeginRenderingKHR(commandBuffer, rendering);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            if (!this.builtin) {
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0,
                        stack.longs(descriptorSet), null);
            }
            vkCmdSetViewport(commandBuffer, 0, org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                    .x(0.0f).y(0.0f).width(64.0f).height(64.0f).minDepth(0.0f).maxDepth(1.0f));
            vkCmdSetScissor(commandBuffer, 0, org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                    .offset(it -> it.set(0, 0)).extent(it -> it.set(64, 64)));
            // Bound at the attribute's OWN location. The buffers are created in the attribute map's order, which is
            // alphabetical, and binding them as 0,1,2,... put position data on whichever location sorted third - the
            // pipeline asked for it at its location and read zeros, which is why a recorded draw showed nothing.
            for (int index = 0; index < vertexBuffers.size(); index++) {
                vkCmdBindVertexBuffers(commandBuffer, vertexBufferLocations.get(index).intValue(),
                        stack.longs(vertexBuffers.get(index).buffer()), stack.longs(0L));
            }
            vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
            vkCmdEndRenderingKHR(commandBuffer);
        }
    }

    @Override public void close() {
        if (descriptorPool != 0L) {
            vkDestroyDescriptorPool(context.device(), descriptorPool, null);
        }
        if (pipeline != 0L) {
            vkDestroyPipeline(context.device(), pipeline, null);
        }
        for (VulkanHostBuffer buffer : buffers) {
            buffer.delete();
        }
    }
}
