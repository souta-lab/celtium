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
    /**
     * A triangle in clip space that covers the whole viewport whatever its size: (-1,-1), (3,-1), (-1,3). The earlier
     * values were (0,0), (0,1), (1,0) - a small triangle in NDC's upper-right quadrant, which mapped inside the frame
     * sized viewport and therefore outside the 64x64 render area the probe actually uses. That is why the pack program
     * drew nothing while the attribute-free constant triangle (full NDC) drew: both pipelines were fine.
     */
    private static final float[] POSITIONS = {-1.0f, -1.0f, 3.0f, -1.0f, -1.0f, 3.0f};
    private static final float[] UVS = {0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f};
    private static final int TRANSFORM_BYTES = 752;
    private static final int UNIFORM_BYTES = 4096;

    /** The name the lowering gives the plain uniform block, so its members can be found in the lowered source. */
    private static final String UNIFORM_BLOCK_NAME = LegacyGlslLowering.UNIFORM_BLOCK;

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
    private int vertexCount;

    /** Byte capacity of each vertex buffer, so a larger geometry replaces rather than overruns one. */
    private final java.util.List<Long> vertexBufferCapacities = new java.util.ArrayList<>();

    /** The pack attributes the probe's vertex buffers feed, name to location, in the order they were declared. */
    private final java.util.Map<String, Integer> packAttributeLocations = new java.util.LinkedHashMap<>();

    private long offscreenImage;

    /** The host address of the transform block, so a fitted transform can replace the identity one. */
    private long transformBlockAddress;

    /** The host address of the plain uniform block, for the environment values a pack's vertex stage needs. */
    private long uniformBlockAddress;
    private long offscreenView;
    private long offscreenMemory;
    private int offscreenSize;
    /** The delivered image's size, so a full-viewport draw can be asked for. */
    private int frameWidth = 1280;
    private int frameHeight = 720;

    /** Sets the size the in-pass draw's viewport covers. */
    void setFrameSize(int width, int height) {
        this.frameWidth = width;
        this.frameHeight = height;
    }

    private long passPipeline;
    private ShaderPackPipelineFactory.ModuleHandle passVertexHandle;
    private ShaderPackPipelineFactory.ModuleHandle passFragmentHandle;
    private LegacyGlslLowering.Result passInterface;

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
        // Samplers start at 2 so they cannot collide with the transform block (0) or the plain uniform block (1).
        // The probe draws the pack's own stages, with one line removed: the TAA jitter that rewrites gl_Position.xy from
        // uniforms the probe fills approximately (BSL divides by viewWidth/viewHeight there, so an unfilled or
        // mis-offset block makes the clip position non-finite and the triangle disappears before rasterisation - measured
        // with the patch still all clear colour). The jitter is a post-process wobble the probe has no opinion about, and
        // removing exactly that statement keeps everything else - the transforms, the varyings, the fragment lighting -
        // the pack's own.
        ShaderPackPipelineFactory.LayoutHandle layout = factory.createLayoutRebased(vertex, fragment, 2);
        // Without depth testing: the frame's depth buffer already holds the world, and a probe triangle drawn with
        // the identity transform sits behind it - measured as a recorded draw that appeared in no screenshot.
        long pipeline = factory.createPipeline(layout.pipelineLayout(), vertexHandle, fragmentHandle, vertex,
                colorFormat, depthFormat, false);
        // The modules stay alive: the render-pass pipeline is created from them on first use, and a pipeline's subpass
        // compatibility is fixed at creation, so it cannot be built before the pass that will be open is known.

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
            probe.writeEnvironmentValues(vertex.source(), 1280, 720);
            probe.prepareRenderPassPipeline(vertexHandle, fragmentHandle, vertex);
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
                this.transformBlockAddress = transforms.mappedAddress();
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
                this.uniformBlockAddress = uniforms.mappedAddress();
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
                this.packAttributeLocations.put(attribute.getKey(), attribute.getValue());
                this.vertexBufferCapacities.add(Long.valueOf(bytes));
                FloatBuffer data = MemoryUtil.memFloatBuffer(vertexBuffer.mappedAddress(), bytes / 4);
                for (int i = 0; i < 3 * components; i++) {
                    data.put(i, 0.0f);
                }
                // The position attribute is written as (x, y, 0, 1): filling only the first two components left w at
                // zero, and a clip-space vertex with w = 0 is degenerate - the triangle was being clipped away with the
                // draw recorded and no validation message saying so.
                boolean position = "a_Position".equals(attribute.getKey());
                for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                    for (int component = 0; component < components; component++) {
                        float value = 0.0f;
                        if (position) {
                            if (component == 0 || component == 1) {
                                value = POSITIONS[vertexIndex * 2 + component];
                            } else if (component == 3) {
                                value = 1.0f;
                            }
                        } else if (component < 2) {
                            value = UVS[vertexIndex * 2 + component];
                        }
                        data.put(vertexIndex * components + component, value);
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
     * Draws the triangle into a small offscreen image and reads the pixels back.
     * <p>
     * The on-screen route has resisted three rounds of attempts (full-image clears reach the presented picture, partial
     * areas and draws do not, and the reason is still unexplained), and the question that matters first is simpler: does
     * a pack's shader produce pixels at all? Rendering into an image this class owns and reading it back answers that
     * without depending on the delivery path, and the pixels are the evidence.
     *
     * @return a summary: the centre pixel's colour in hex, and how many distinct colours the image holds
     */
    String renderOffscreenAndSample(ShaderPackPipelineFactory.ModuleHandle vertexModule,
                                    ShaderPackPipelineFactory.ModuleHandle fragmentModule, int size) {
        StringBuilder summary = new StringBuilder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer image = stack.mallocLong(1);
            LongBuffer memory = stack.mallocLong(1);
            LongBuffer view = stack.mallocLong(1);
            org.lwjgl.vulkan.VkImageCreateInfo imageInfo = org.lwjgl.vulkan.VkImageCreateInfo.calloc(stack)
                    .sType$Default().imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .extent(it -> it.width(size).height(size).depth(1))
                    .mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
            int result = vkCreateImage(context.device(), imageInfo, null, image);
            if (result != VK_SUCCESS) {
                return "image=failed(" + result + ")";
            }
            org.lwjgl.vulkan.VkMemoryRequirements requirements = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(context.device(), image.get(0), requirements);
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(context.memoryTypeIndex(requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "pack probe offscreen image"));
            if (vkAllocateMemory(context.device(), allocate, null, memory) != VK_SUCCESS) {
                vkDestroyImage(context.device(), image.get(0), null);
                return "memory=failed";
            }
            vkBindImageMemory(context.device(), image.get(0), memory.get(0), 0);
            org.lwjgl.vulkan.VkImageViewCreateInfo viewInfo = org.lwjgl.vulkan.VkImageViewCreateInfo.calloc(stack)
                    .sType$Default().image(image.get(0)).viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM);
            viewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
            if (vkCreateImageView(context.device(), viewInfo, null, view) != VK_SUCCESS) {
                vkFreeMemory(context.device(), memory.get(0), null);
                vkDestroyImage(context.device(), image.get(0), null);
                return "view=failed";
            }
            VulkanHostBuffer readback = new VulkanHostBuffer(context, (long) size * size * 4,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT);
            buffers.add(readback);
            // A pool and buffer of this probe's own: the context's beginCommands() hands out a frame-slot buffer that
            // the frame coordinator also submits, and handing that buffer to a second submission is what the validation
            // layer rejected as an invalid command buffer. A diagnostic with its own lifecycle does not disturb the frame.
            LongBuffer privatePool = stack.mallocLong(1);
            org.lwjgl.vulkan.VkCommandPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                            .queueFamilyIndex(context.queueFamilyIndex())
                            .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            if (vkCreateCommandPool(context.device(), poolInfo, null, privatePool) != VK_SUCCESS) {
                return "pool=failed";
            }
            org.lwjgl.PointerBuffer privateBuffer = stack.callocPointer(1);
            org.lwjgl.vulkan.VkCommandBufferAllocateInfo allocateInfo =
                    org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                            .commandPool(privatePool.get(0)).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                            .commandBufferCount(1);
            if (vkAllocateCommandBuffers(context.device(), allocateInfo, privateBuffer) != VK_SUCCESS) {
                vkDestroyCommandPool(context.device(), privatePool.get(0), null);
                return "buffer=failed";
            }
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer =
                    new org.lwjgl.vulkan.VkCommandBuffer(privateBuffer.get(0), context.device());
            org.lwjgl.vulkan.VkCommandBufferBeginInfo begin = org.lwjgl.vulkan.VkCommandBufferBeginInfo
                    .calloc(stack).sType$Default();
            vkBeginCommandBuffer(commandBuffer, begin);
            try {
                barrier(stack, commandBuffer, image.get(0), VK_IMAGE_LAYOUT_UNDEFINED,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, 0, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                VkRenderingAttachmentInfoKHR.Buffer colour = VkRenderingAttachmentInfoKHR.calloc(1, stack);
                colour.get(0).sType$Default().imageView(view.get(0))
                        .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                colour.get(0).clearValue(clearValue(stack, 0.0f, 0.0f, 1.0f));
                org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                        .offset(it -> it.set(0, 0)).extent(it -> it.set(size, size));
                VkRenderingInfoKHR rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                        .renderArea(area).layerCount(1).pColorAttachments(colour);
                vkCmdBeginRenderingKHR(commandBuffer, rendering);
                long passPipeline = new ShaderPackPipelineFactory(context).createPipeline(this.pipelineLayout,
                        vertexModule, fragmentModule, this.passInterface, VK_FORMAT_R8G8B8A8_UNORM,
                        VK_FORMAT_UNDEFINED, false);
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, passPipeline);
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                        stack.longs(this.descriptorSet), null);
                vkCmdSetViewport(commandBuffer, 0, org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                        .x(0.0f).y(0.0f).width(size).height(size).minDepth(0.0f).maxDepth(1.0f));
                vkCmdSetScissor(commandBuffer, 0, org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                        .offset(it -> it.set(0, 0)).extent(it -> it.set(size, size)));
                for (int index = 0; index < vertexBuffers.size(); index++) {
                    vkCmdBindVertexBuffers(commandBuffer, vertexBufferLocations.get(index).intValue(),
                            stack.longs(vertexBuffers.get(index).buffer()), stack.longs(0L));
                }
                vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
                vkCmdEndRenderingKHR(commandBuffer);
                barrier(stack, commandBuffer, image.get(0), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                        VK_ACCESS_TRANSFER_READ_BIT);
                org.lwjgl.vulkan.VkBufferImageCopy.Buffer copy =
                        org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
                copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
                copy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                        .baseArrayLayer(0).layerCount(1);
                copy.get(0).imageOffset().set(0, 0, 0);
                copy.get(0).imageExtent().set(size, size, 1);
                vkCmdCopyImageToBuffer(commandBuffer, image.get(0), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        readback.buffer(), copy);
                if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
                    return "end=failed";
                }
                // A one-off synchronous submit: this is a diagnostic, not a frame, so it waits for its own work rather
                // than joining the frame's pipelined submissions.
                org.lwjgl.vulkan.VkSubmitInfo submit = org.lwjgl.vulkan.VkSubmitInfo.calloc(stack).sType$Default()
                        // The native handle, not the Java wrapper: passing the wrapper put an invalid handle in the
                        // submit (VUID-VkSubmitInfo-pCommandBuffers-parameter).
                        .pCommandBuffers(stack.pointers(commandBuffer.address()));
                LongBuffer fence = stack.mallocLong(1);
                if (vkCreateFence(context.device(), org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack)
                        .sType$Default(), null, fence) != VK_SUCCESS) {
                    return "fence=failed";
                }
                vkQueueSubmit(context.queue(), submit, fence.get(0));
                vkWaitForFences(context.device(), fence.get(0), true, Long.MAX_VALUE);
                vkDestroyFence(context.device(), fence.get(0), null);
                int centre = size / 2;
                long at = readback.mappedAddress() + ((long) centre * size + centre) * 4L;
                int b = MemoryUtil.memGetByte(at) & 0xFF;
                int g = MemoryUtil.memGetByte(at + 1) & 0xFF;
                int r = MemoryUtil.memGetByte(at + 2) & 0xFF;
                java.util.Set<Integer> colours = new java.util.LinkedHashSet<>();
                for (int y = 0; y < size; y += Math.max(1, size / 16)) {
                    for (int x = 0; x < size; x += Math.max(1, size / 16)) {
                        long pixel = readback.mappedAddress() + ((long) y * size + x) * 4L;
                        colours.add(Integer.valueOf((MemoryUtil.memGetByte(pixel + 2) & 0xFF) << 16
                                | (MemoryUtil.memGetByte(pixel + 1) & 0xFF) << 8
                                | (MemoryUtil.memGetByte(pixel) & 0xFF)));
                    }
                }
                vkDestroyPipeline(context.device(), passPipeline, null);
                summary.append("centre=#").append(String.format("%06X", Integer.valueOf((r << 16) | (g << 8) | b)))
                        .append(" sampledColours=").append(colours.size());
                // The image, view and memory stay alive: the delivery experiment blits this image into the delivered
                // frame, so the pixels the pack's shaders produced are what appears on screen.
                this.offscreenImage = image.get(0);
                this.offscreenView = view.get(0);
                this.offscreenMemory = memory.get(0);
                this.offscreenSize = size;
                return summary.toString();
            } finally {
                vkDestroyCommandPool(context.device(), privatePool.get(0), null);
            }
        } catch (Throwable failure) {
            return "offscreen=failed(" + failure + ")";
        }
    }

    private static org.lwjgl.vulkan.VkClearValue clearValue(MemoryStack stack, float r, float g, float b) {
        org.lwjgl.vulkan.VkClearValue clear = org.lwjgl.vulkan.VkClearValue.calloc(stack);
        clear.color().float32(0, r);
        clear.color().float32(1, g);
        clear.color().float32(2, b);
        clear.color().float32(3, 1.0f);
        return clear;
    }

    private static void barrier(MemoryStack stack, org.lwjgl.vulkan.VkCommandBuffer commandBuffer, long image,
                                int oldLayout, int newLayout, int srcAccess, int dstAccess) {
        org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0).sType$Default().oldLayout(oldLayout).newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image).srcAccessMask(srcAccess).dstAccessMask(dstAccess);
        barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
        vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0,
                null, null, barrier);
    }

    /**
     * Binds the pack pipeline and draws the triangle into a render pass that is already open.
     * <p>
     * The pass is the one the game's own immediate quads use, so delivery is the proven path rather than a second
     * mechanism beside it; the pipeline for this pass is created on first use, because a pipeline's subpass compatibility
     * is decided at creation.
     */
    void recordInPass(VkCommandBuffer commandBuffer, long renderPass) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long passPipeline = this.passPipeline;
            if (passPipeline == 0L) {
                ShaderPackPipelineFactory.ModuleHandle vertexHandle = this.passVertexHandle;
                ShaderPackPipelineFactory.ModuleHandle fragmentHandle = this.passFragmentHandle;
                if (vertexHandle == null || fragmentHandle == null) {
                    return;
                }
                passPipeline = new ShaderPackPipelineFactory(this.context).createPipelineForRenderPass(renderPass,
                        this.pipelineLayout, vertexHandle, fragmentHandle, this.passInterface, false);
                this.passPipeline = passPipeline;
            }
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, passPipeline);
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                    stack.longs(this.descriptorSet), null);
            // Full screen, deliberately: partial coverage has been the one property every invisible case shared, and a
            // draw that covers the whole viewport is the direct test of whether that is the rule.
            vkCmdSetViewport(commandBuffer, 0, org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                    .x(0.0f).y(0.0f).width(this.frameWidth).height(this.frameHeight).minDepth(0.0f).maxDepth(1.0f));
            vkCmdSetScissor(commandBuffer, 0, org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                    .offset(it -> it.set(0, 0)).extent(it -> it.set(this.frameWidth, this.frameHeight)));
            for (int index = 0; index < this.vertexBuffers.size(); index++) {
                vkCmdBindVertexBuffers(commandBuffer, this.vertexBufferLocations.get(index).intValue(),
                        stack.longs(this.vertexBuffers.get(index).buffer()), stack.longs(0L));
            }
            vkCmdDraw(commandBuffer, this.vertexCount, 1, 0, 0);
        }
    }

    /**
     * Blits the image the pack's shaders rendered into the delivered image.
     * <p>
     * The on-screen tests so far added a draw or a clear into the frame's own recording, and a full-image clear was the
     * only one that appeared. A blit is neither: it is a transfer of pixels that are already known to be correct, into an
     * image whose usage flags include TRANSFER_DST, at the last point before the frame is submitted.
     */
    void blitToFrame(VkCommandBuffer commandBuffer, long frameImage, int x, int y, int width, int height) {
        if (this.offscreenImage == 0L) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkImageBlit.Buffer blit = org.lwjgl.vulkan.VkImageBlit.calloc(1, stack);
            blit.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                    .layerCount(1);
            blit.get(0).srcOffsets(0).set(0, 0, 0);
            blit.get(0).srcOffsets(1).set(this.offscreenSize, this.offscreenSize, 1);
            blit.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                    .layerCount(1);
            blit.get(0).dstOffsets(0).set(x, y, 0);
            blit.get(0).dstOffsets(1).set(x + width, y + height, 1);
            vkCmdBlitImage(commandBuffer, this.offscreenImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, frameImage,
                    VK_IMAGE_LAYOUT_GENERAL, blit, VK_FILTER_NEAREST);
        }
    }

    /** {@return the offscreen render's summary}, using the modules and interface kept for the render-pass pipeline. */
    String sampleOffscreen(int size) {
        if (this.passVertexHandle == null || this.passFragmentHandle == null) {
            return "no modules";
        }
        return renderOffscreenAndSample(this.passVertexHandle, this.passFragmentHandle, size);
    }

    /**
     * Prints what the pipeline expects from its vertex input and what the bound buffers hold, once, when
     * {@code celtium.shaderpack.drawProbeDebug=true}. The constant triangle draws and the pack program does not, so the
     * difference has to be visible: locations, formats, strides and the first position's bytes.
     */
    /**
     * Replaces the probe's three-vertex triangle with real geometry converted to the pack's attributes.
     * <p>
     * This is the step from "a pack's shaders can draw" to "a pack's shaders can draw the game's terrain": the vertices come
     * from the renderer's own immediate format and {@link ShaderPackVertexAdapter} produces the attribute set the pack's
     * vertex stage declares, so what the probe then draws is the game's geometry through the pack's program.
     *
     * @return a one-line summary for the log, or null when the format is not one the adapter converts
     */
    String replaceGeometry(int formatId, java.nio.ByteBuffer vertices, int stride, int count) {
        java.util.Map<String, float[]> adapted = ShaderPackVertexAdapter.adapt(formatId, vertices, stride, count);
        if (adapted == null) {
            return null;
        }
        int index = 0;
        for (java.util.Map.Entry<String, Integer> attribute : this.packAttributeLocations.entrySet()) {
            float[] values = adapted.get(attribute.getKey());
            if (values == null) {
                index++;
                continue;
            }
            VulkanHostBuffer buffer = this.vertexBuffers.get(index);
            long bytes = (long) values.length * 4L;
            if (this.vertexBufferCapacities.size() <= index) {
                this.vertexBufferCapacities.add(Long.valueOf(0L));
            }
            if (this.vertexBufferCapacities.get(index).longValue() < bytes) {
                buffer.delete();
                buffer = new VulkanHostBuffer(this.context, bytes, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
                this.vertexBuffers.set(index, buffer);
                this.vertexBufferCapacities.set(index, Long.valueOf(bytes));
                this.buffers.add(buffer);
            }
            java.nio.FloatBuffer data = MemoryUtil.memFloatBuffer(buffer.mappedAddress(), values.length);
            for (int i = 0; i < values.length; i++) {
                data.put(i, values[i]);
            }
            index++;
        }
        this.vertexCount = count;
        // World-space captured vertices with an identity transform land outside clip space and are clipped (measured),
        // so the probe fits the geometry's own bounds into the visible area; there is no camera to reproduce here.
        String fitted = fitTransformToBounds(adapted.get("a_Position"));
        return "terrain vertices=" + count + " format=" + formatId + " attributes="
                + this.packAttributeLocations.size();
    }

    void dumpVertexContract() {
        if (!Boolean.getBoolean("celtium.shaderpack.drawProbeDebug")) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_PACK_VERTEX_DUMP attributes=" + vertexBuffers.size());
        for (int index = 0; index < vertexBuffers.size(); index++) {
            long buffer = vertexBuffers.get(index).buffer();
            long address = vertexBuffers.get(index).mappedAddress();
            StringBuilder bytes = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                bytes.append(Float.intBitsToFloat(org.lwjgl.system.MemoryUtil.memGetInt(address + i * 4L))).append(' ');
            }
            System.out.println("[Celtium] CELTIUM_PACK_VERTEX_DUMP location=" + vertexBufferLocations.get(index)
                    + " buffer=" + Long.toHexString(buffer) + " firstVertex=[" + bytes.toString().trim() + "]");
        }
    }

    /**
     * Writes the values a pack's vertex stage needs to produce a finite position, into the plain uniform block.
     * <p>
     * BSL's gbuffers_basic jitters the clip position with TAAJitter(gl_Position.xy, gl_Position.w), which divides by
     * viewWidth/viewHeight - zeroed with the rest of the block, so the jitter was 0/0 = NaN, the vertex was NaN, and the
     * triangle was clipped before rasterisation. That is why a validated pipeline and a bound vertex buffer produced
     * nothing while an attribute-free shader did. The offsets come from the block declaration in the lowered source
     * (std140 rules, which is what the emitter writes), so the right members are written rather than guessed.
     */
    void writeEnvironmentValues(String loweredSource, int width, int height) {
        int blockAt = loweredSource.indexOf("uniform " + UNIFORM_BLOCK_NAME);
        if (blockAt < 0 || this.uniformBlockAddress == 0L) {
            return;
        }
        int braceAt = loweredSource.indexOf('{', blockAt);
        int endAt = loweredSource.indexOf('}', braceAt);
        if (braceAt < 0 || endAt < 0) {
            return;
        }
        int offset = 0;
        for (String line : loweredSource.substring(braceAt + 1, endAt).split("\n")) {
            String member = line.trim();
            if (member.isEmpty() || member.startsWith("//")) {
                continue;
            }
            String[] parts = member.replace(";", "").trim().split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            String type = parts[0];
            String name = parts[1].replace(";", "");
            int count = 1;
            int bracket = name.indexOf('[');
            if (bracket >= 0) {
                count = Integer.parseInt(name.substring(bracket + 1, name.indexOf(']')));
                name = name.substring(0, bracket);
            }
            int size = std140Size(type) * count;
            if ("viewWidth".equals(name)) {
                org.lwjgl.system.MemoryUtil.memPutFloat(this.uniformBlockAddress + offset, (float) width);
            } else if ("viewHeight".equals(name)) {
                org.lwjgl.system.MemoryUtil.memPutFloat(this.uniformBlockAddress + offset, (float) height);
            } else if ("near".equals(name)) {
                org.lwjgl.system.MemoryUtil.memPutFloat(this.uniformBlockAddress + offset, 0.05f);
            } else if ("far".equals(name)) {
                org.lwjgl.system.MemoryUtil.memPutFloat(this.uniformBlockAddress + offset, 256.0f);
            } else if ("frameTimeCounter".equals(name)) {
                org.lwjgl.system.MemoryUtil.memPutFloat(this.uniformBlockAddress + offset, 0.016f);
            }
            offset += size;
        }
    }

    /**
     * {@return the lowered vertex source with the TAA jitter statement removed}.
     * <p>
     * The probe's job is to show that a pack's pipeline and shaders reach the screen; a jitter that depends on frame
     * uniforms the probe only approximates is noise for that question and, when its uniforms are not the real ones,
     * makes gl_Position non-finite. Only statements of the form {@code gl_Position.xy = ...TAAJitter...;} are dropped.
     */
    /**
     * Writes a model-view-projection that fits the given positions' bounds into clip space, and returns a one-line
     * description of the fit.
     */
    private String fitTransformToBounds(float[] positions) {
        if (positions == null || positions.length < 4 || this.transformBlockAddress == 0L) {
            return "none";
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        int vertices = positions.length / 4;
        for (int vertex = 0; vertex < vertices; vertex++) {
            float x = positions[vertex * 4];
            float y = positions[vertex * 4 + 1];
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                continue;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        if (minX > maxX || minY > maxY) {
            return "empty";
        }
        float extentX = Math.max(1.0e-3f, maxX - minX);
        float extentY = Math.max(1.0e-3f, maxY - minY);
        float extent = Math.max(extentX, extentY);
        float scale = 2.0f / extent;
        float centreX = (minX + maxX) * 0.5f;
        float centreY = (minY + maxY) * 0.5f;
        float[] fit = new float[16];
        fit[0] = scale;
        fit[5] = scale;
        fit[10] = 0.0f;
        fit[12] = -scale * centreX;
        fit[13] = -scale * centreY;
        fit[14] = 0.0f;
        fit[15] = 1.0f;
        // u_TextureMatrix[8] (128 floats), u_NormalMatrix (12), u_ModelView (16), u_Projection (16),
        // u_ModelViewProjection (16): the layout the emitter writes and the earlier identity fill already used.
        writeTransformMatrix((8 * 16 + 12) * 4, identityMatrix());
        writeTransformMatrix((8 * 16 + 28) * 4, fit);
        writeTransformMatrix((8 * 16 + 44) * 4, fit);
        return String.format(java.util.Locale.ROOT, "bounds=%.1fx%.1f scale=%.4f", extentX, extentY, scale);
    }

    private static float[] identityMatrix() {
        float[] identity = new float[16];
        identity[0] = 1.0f;
        identity[5] = 1.0f;
        identity[10] = 1.0f;
        identity[15] = 1.0f;
        return identity;
    }

    private void writeTransformMatrix(int byteOffset, float[] matrix) {
        for (int i = 0; i < 16; i++) {
            org.lwjgl.system.MemoryUtil.memPutFloat(this.transformBlockAddress + byteOffset + (long) i * 4L,
                    matrix[i]);
        }
    }

    static String sanitiseProbeVertex(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("gl_Position.") && trimmed.contains("TAAJitter(")) {
                out.append("// probe: TAA jitter statement removed\n");
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** {@return the std140 size of a declared type}, including the array stride of a vec3. */
    private static int std140Size(String type) {
        return switch (type) {
            case "int", "float", "bool" -> 4;
            case "ivec2", "vec2", "bvec2" -> 8;
            case "ivec3", "vec3", "bvec3" -> 16;
            case "ivec4", "vec4", "bvec4" -> 16;
            case "mat2", "mat3" -> 48;
            case "mat4" -> 64;
            default -> 16;
        };
    }

    /** Keeps the handles and interface the render-pass pipeline is created from on first use. */
    void prepareRenderPassPipeline(ShaderPackPipelineFactory.ModuleHandle vertex,
                                   ShaderPackPipelineFactory.ModuleHandle fragment,
                                   LegacyGlslLowering.Result vertexInterface) {
        this.passVertexHandle = vertex;
        this.passFragmentHandle = fragment;
        this.passInterface = vertexInterface;
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
        recordClear(commandBuffer, target, 0, 0, width, height, 1.0f, 0.0f, 1.0f);
    }

    /** Clears one rectangle to a colour; the diagnostic that says which areas of the delivered image actually take. */
    void recordClear(VkCommandBuffer commandBuffer, GllessFrameTarget target, int x, int y, int width, int height,
                     float red, float green, float blue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfoKHR.Buffer colour = VkRenderingAttachmentInfoKHR.calloc(1, stack);
            colour.get(0).sType$Default().imageView(target.colorView())
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            org.lwjgl.vulkan.VkClearValue clear = org.lwjgl.vulkan.VkClearValue.calloc(stack);
            clear.color().float32(0, red);
            clear.color().float32(1, green);
            clear.color().float32(2, blue);
            clear.color().float32(3, 1.0f);
            colour.get(0).clearValue(clear);
            org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                    .offset(it -> it.set(x, y)).extent(it -> it.set(width, height));
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
    /**
     * The attribute-free probe: a full-viewport triangle whose positions come from gl_VertexIndex, no vertex buffers, no
     * descriptors. It answers whether a draw through this record path rasterises at all.
     */
    static ShaderPackDrawProbe createConstant(VulkanContext context, ShadercGlslCompiler compiler, int colorFormat,
                                              int depthFormat) throws Exception {
        String vertexSource = "#version 450\n"
                + "layout(location = 0) out vec3 v_Colour;\n"
                + "void main() {\n"
                + "  vec2 p = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));\n"
                + "  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n"
                + "  v_Colour = vec3(0.0, 1.0, 0.0);\n"
                + "}\n";
        String fragmentSource = "#version 450\n"
                + "layout(location = 0) in vec3 v_Colour;\n"
                + "layout(location = 0) out vec4 o_Colour;\n"
                + "void main() { o_Colour = vec4(v_Colour, 1.0); }\n";
        SpirvModule vertexModule = compiler.compile(Stage.VERTEX, vertexSource);
        SpirvModule fragmentModule = compiler.compile(Stage.FRAGMENT, fragmentSource);
        ShaderPackPipelineFactory factory = new ShaderPackPipelineFactory(context);
        ShaderPackPipelineFactory.ModuleHandle vertexHandle = factory.createModule(vertexModule);
        ShaderPackPipelineFactory.ModuleHandle fragmentHandle = factory.createModule(fragmentModule);
        LongBuffer pipelineLayoutHandle = MemoryUtil.memAllocLong(1);
        LongBuffer pool = MemoryUtil.memAllocLong(1);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int result = vkCreatePipelineLayout(context.device(), org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
                    .calloc(stack).sType$Default(), null, pipelineLayoutHandle);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreatePipelineLayout(constant) failed: " + result);
            }
            result = vkCreateDescriptorPool(context.device(), VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(1), null, pool);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDescriptorPool(constant) failed: " + result);
            }
            long pipeline = factory.createAttributeFreeTrianglePipeline(pipelineLayoutHandle.get(0), vertexHandle,
                    fragmentHandle, colorFormat, depthFormat);
            ShaderPackDrawProbe probe = new ShaderPackDrawProbe(context, pipeline, pipelineLayoutHandle.get(0),
                    pool.get(0), 0L, 3, true);
            return probe;
        } finally {
            vertexHandle.close();
            fragmentHandle.close();
            MemoryUtil.memFree(pipelineLayoutHandle);
            MemoryUtil.memFree(pool);
        }
    }

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

    /**
     * Records the draw into the delivered image. The caller has ended any render pass already.
     * <p>
     * <b>Coordinates here are image space, which is bottom-up relative to the presented picture.</b> The present blits the
     * frame image into the swapchain with a 1:1 flip (VulkanSwapchain's pre-recorded blit says so), so a rectangle recorded
     * at image (0, 0) appears at the <i>bottom</i> left on screen. Three rounds of probes were read as silent because the
     * samples were taken from the top left: a 64x64 clear is visible at (10, height - 10) in the early capture of leg
     * flipTest1, and the same experiment looked like a delivery failure because the identical patch was sampled at (10, 10).
     * A clear recorded here therefore does reach the screen; a draw recorded here still does not, which is a draw-specific
     * question rather than a coordinate one.
     */
    void record(VkCommandBuffer commandBuffer, GllessFrameTarget target) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The area is cleared to magenta first and only then drawn: a clear through this same scope is known to
            // reach the presented picture (leg flipTest1 sampled the bottom-left corner), so whatever this triangle
            // covers shows up as non-magenta. That is the difference between "the draw writes" and "the draw is not
            // recorded", which the LOAD variant could not tell apart from a black frame.
            VkRenderingAttachmentInfoKHR.Buffer colour = VkRenderingAttachmentInfoKHR.calloc(1, stack);
            colour.get(0).sType$Default().imageView(target.colorView())
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            colour.get(0).clearValue(clearValue(stack, 1.0f, 0.0f, 1.0f));
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
        if (this.offscreenView != 0L) {
            vkDestroyImageView(context.device(), this.offscreenView, null);
        }
        if (this.offscreenImage != 0L) {
            vkDestroyImage(context.device(), this.offscreenImage, null);
        }
        if (this.offscreenMemory != 0L) {
            vkFreeMemory(context.device(), this.offscreenMemory, null);
        }
        for (VulkanHostBuffer buffer : buffers) {
            buffer.delete();
        }
    }
}
