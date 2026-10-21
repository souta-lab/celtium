package org.soutalab.celtium.vulkan.shadow;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdBeginRenderingKHR;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdEndRenderingKHR;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The native shadow map's own pass: a depth-only image rendered from a light camera.
 * <p>
 * This is the first slice of the shadow work the shader plan puts after the shaderless and pack gates, and it is
 * deliberately a separate unit rather than another block inside the renderer: the owners are its image, its views and its
 * pipeline, the frame asks it to record once per frame, and nothing about the shaderless path changes while it is off
 * ({@code -Dceltium.shadow.enabled=true}).
 * <p>
 * What this slice establishes is the resource and the recording: a 2048x2048 D32 depth image in GENERAL layout with a
 * depth-optimal dynamic-rendering pass over it once per frame, with the clear counted and reported. Drawing terrain into it
 * from the light's matrix, then sampling it in the world pass, are the next slices - each one a change to this class and to
 * the one call site, not a change spread through the renderer.
 */
public final class ShadowMapPass {
    /**
     * Command pools whose submissions have not completed yet, with the fence that will tell: a pool may only be
     * destroyed once the queue is done with its command buffers, and waiting here would serialise the frame against the
     * pass. Each frame reclaims whatever has signalled.
     */
    private final java.util.ArrayDeque<long[]> pendingOwnSubmissions = new java.util.ArrayDeque<>();

    /** Pools whose one-shot probe submission timed out; see #403. */
    private final org.soutalab.celtium.vulkan.PendingCommandPools pendingProbePools =
            new org.soutalab.celtium.vulkan.PendingCommandPools();

    /** The edge length of the shadow map; a power of two because the depth comparison will filter across it. */
    public static final int SIZE = 2048;

    private final VulkanContext context;
    private long image = VK_NULL_HANDLE;
    private long memory = VK_NULL_HANDLE;
    private long view = VK_NULL_HANDLE;
    /** The sampler and the 64-byte matrix block the terrain shader reads the map through. */
    private long sampler;
    private org.soutalab.celtium.vulkan.VulkanHostBuffer matrixBuffer;

    /** {@return the sampled view of the map}, valid while the pass exists. */
    public long view() {
        return this.view;
    }

    /** {@return the sampler the world pass reads the map with}. */
    public long sampler() {
        return this.sampler;
    }

    /**
     * Writes the eye-space light matrix, for the immediate path: its vertices arrive already transformed by the model
     * view, so the matrix it needs is lightWorld * inverse(modelView), which the caller composes.
     */
    public void writeEyeSpaceMatrix(float[] matrix) {
        if (this.matrixBuffer == null) {
            return;
        }
        java.nio.FloatBuffer data = org.lwjgl.system.MemoryUtil.memFloatBuffer(
                this.matrixBuffer.mappedAddress() + 64L, 16);
        for (int i = 0; i < 16 && i < matrix.length; i++) {
            data.put(i, matrix[i]);
        }
    }

    /** {@return the host-visible block holding the light matrix the vertex stage projects with}. */
    public org.soutalab.celtium.vulkan.VulkanHostBuffer matrixBuffer() {
        return this.matrixBuffer;
    }

    /** Whether the depth image has been moved out of UNDEFINED into GENERAL. */
    private boolean transitioned;

    /** Whether the map's depth has been sampled once, and what the corner held. */
    private boolean depthSampled;
    private float sampledDepthMin = -1.0f;
    private float sampledDepthMax = -1.0f;

    private long pipeline;
    private long pipelineLayout;
    /** Draws of real terrain into the map, as opposed to the test triangle. */
    private long terrainPasses;

    /** One shadow pipeline per terrain pipeline: a frame runs more than one variant, and a set bound under one layout is
     * not compatible with a pipeline created under the other (VUID-vkCmdDrawIndexedIndirect-None-08600, measured when a
     * single shadow pipeline was reused across variants). */
    private final java.util.Map<Object, Long> terrainPipelines = new java.util.IdentityHashMap<>();

    private long terrainPipeline;
    private long terrainPipelineLayout;
    private long terrainModule;

    private long passes;
    private long failures;
    private String lastFailure = "";

    private ShadowMapPass(VulkanContext context) {
        this.context = context;
    }

    /** {@return whether the pass is requested for this process}; a JVM argument, resolved once. */
    /**
     * Why {@link #create} last returned null, or null when it did not fail.
     * <p>
     * The caller used to log only that the map could not be created, which is the same message for three different
     * failures: no memory, no pipeline, or no transition. The reason the pass recorded on itself was discarded with
     * the object, so the log named the symptom and never the cause.
     */
    private static volatile String LAST_FAILURE;

    /** {@return why the last {@link #create} failed, or null} */
    public static String lastFailure() {
        return LAST_FAILURE;
    }

    public static boolean requested() {
        return Boolean.getBoolean("celtium.shadow.enabled");
    }

    /**
     * Creates the shadow map's depth image and view.
     *
     * @return the pass, or null when the image could not be created (with the reason recorded on the instance it returns
     *         as null and printed by the caller)
     */
    public static ShadowMapPass create(VulkanContext context) {
        ShadowMapPass pass = new ShadowMapPass(context);
        if (!pass.allocate()) {
            LAST_FAILURE = pass.lastFailure == null ? "allocate failed" : pass.lastFailure;
            return null;
        }
        if (!pass.createPipeline()) {
            LAST_FAILURE = pass.lastFailure == null ? "createPipeline failed" : pass.lastFailure;
            pass.destroy();
            return null;
        }
        // The image is moved to GENERAL once, here, before anything can sample it. The transition used to happen inside the
        // pass's own recording, which is fine while only the pass touches the image - but the receivers sample it through a
        // descriptor written with GENERAL, and publishing the sources before the pass records (which the own-submission path
        // needs, #396) made a receiver draw in this frame sample an image in its initial layout:
        // VUID-vkCmdDraw-None-09600 "expects ... GENERAL". Starting in GENERAL and staying there removes the ordering
        // dependency between publication and recording.
        if (!pass.transitionImageToGeneral()) {
            LAST_FAILURE = pass.lastFailure == null ? "transitionImageToGeneral failed" : pass.lastFailure;
            pass.destroy();
            return null;
        }
        LAST_FAILURE = null;
        return pass;
    }

    /**
     * Builds the depth-only pipeline: a vertex stage whose positions come from {@code gl_VertexIndex}, no fragment stage,
     * no descriptors and no push constants. It draws a triangle at clip depth 0.5 over the whole map, which is enough to
     * prove that the pass rasterises into the shadow map and writes depth - the quantity the next slice (terrain from the
     * light's matrix) depends on.
     */
    private boolean createPipeline() {
        try {
            // Built by the build, not by a runtime compiler. This was a shaderc compile, and it made the whole pass depend
            // on a binding that is absent from most installs and version-matched on the rest: the leg environment carried
            // lwjgl-shaderc 3.4.1 against an LWJGL 3.4.3 runtime, the binding refused to load, and the map was silently
            // never created. The module is now a build input (src/main/shaders/shadow_depth_only.vert) like the terrain
            // shaders, so a missing compiler fails the build instead of the feature.
            org.soutalab.celtium.shaderpack.SpirvModule module =
                    org.soutalab.celtium.shaderpack.SpirvModule.inspect(
                            org.soutalab.celtium.shaderpack.Stage.VERTEX,
                            org.soutalab.celtium.vulkan.SpirvShaders.loadBytes(
                                    org.soutalab.celtium.vulkan.SpirvShaders.SHADOW_DEPTH_ONLY_VERTEX));
            org.soutalab.celtium.vulkan.ShaderPackPipelineFactory factory =
                    new org.soutalab.celtium.vulkan.ShaderPackPipelineFactory(this.context);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                java.nio.LongBuffer handle = stack.mallocLong(1);
                if (vkCreatePipelineLayout(this.context.device(),
                        org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack).sType$Default(), null, handle)
                        != VK_SUCCESS) {
                    this.failures++;
                    this.lastFailure = "vkCreatePipelineLayout(depth-only) failed";
                    return false;
                }
                this.pipelineLayout = handle.get(0);
            }
            try (org.soutalab.celtium.vulkan.ShaderPackPipelineFactory.ModuleHandle moduleHandle =
                         factory.createModule(module)) {
                this.pipeline = factory.createDepthOnlyPipeline(this.pipelineLayout, moduleHandle,
                        VK_FORMAT_D32_SFLOAT);
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                java.nio.LongBuffer handle = stack.mallocLong(1);
                org.lwjgl.vulkan.VkSamplerCreateInfo samplerInfo = org.lwjgl.vulkan.VkSamplerCreateInfo
                        .calloc(stack).sType$Default()
                        .magFilter(VK_FILTER_NEAREST).minFilter(VK_FILTER_NEAREST)
                        .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                        .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                        .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                        .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
                if (vkCreateSampler(this.context.device(), samplerInfo, null, handle) != VK_SUCCESS) {
                    this.failures++;
                    this.lastFailure = "vkCreateSampler(shadow) failed";
                    return false;
                }
                this.sampler = handle.get(0);
            }
            // Two matrices: the world-space one the terrain vertex stage projects with, and an eye-space one for the
            // immediate path, whose vertices are already transformed by the model view. The immediate shaders read the
            // second at offset 64.
            this.matrixBuffer = new org.soutalab.celtium.vulkan.VulkanHostBuffer(this.context, 128L,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
            org.lwjgl.system.MemoryUtil.memSet(this.matrixBuffer.mappedAddress(), 0, 128L);
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.failures++;
            this.lastFailure = "depth-only pipeline: " + error;
            return false;
        }
    }

    private boolean allocate() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkImageCreateInfo imageInfo = org.lwjgl.vulkan.VkImageCreateInfo.calloc(stack)
                    .sType$Default().imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_D32_SFLOAT)
                    .extent(it -> it.width(SIZE).height(SIZE).depth(1))
                    .mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    // TRANSFER_SRC is what the depth readback needs: without it the transition to TRANSFER_SRC and the
                    // copy are both invalid (VUID-VkImageMemoryBarrier-oldLayout-01212 and -srcImage-00186, three
                    // messages in leg shadowDepth3).
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
                            | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
            LongBuffer handle = stack.mallocLong(1);
            int result = vkCreateImage(this.context.device(), imageInfo, null, handle);
            if (result != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkCreateImage failed: " + result;
                return false;
            }
            this.image = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(this.context.device(), this.image, requirements);
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocateInfo = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "the native shadow map"));
            if (vkAllocateMemory(this.context.device(), allocateInfo, null, handle) != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkAllocateMemory failed";
                destroy();
                return false;
            }
            this.memory = handle.get(0);
            vkBindImageMemory(this.context.device(), this.image, this.memory, 0);
            org.lwjgl.vulkan.VkImageViewCreateInfo viewInfo = org.lwjgl.vulkan.VkImageViewCreateInfo.calloc(stack)
                    .sType$Default().image(this.image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(VK_FORMAT_D32_SFLOAT);
            viewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
            if (vkCreateImageView(this.context.device(), viewInfo, null, handle) != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkCreateImageView failed";
                destroy();
                return false;
            }
            this.view = handle.get(0);
            return true;
        }
    }

    /**
     * Records the pass into the frame's command buffer: a depth clear over the whole map in its own dynamic-rendering
     * scope. Drawing into it is the next slice; the clear is what makes the resource and the scope verifiable now.
     */
    public void record(VkCommandBuffer commandBuffer, MemoryStack stack) {
        if (this.image == VK_NULL_HANDLE) {
            return;
        }
        if (!this.transitioned) {
            // The image is created in UNDEFINED and the pass renders into GENERAL, so the first use needs the transition:
            // declaring GENERAL without it is VUID-vkCmdBeginRendering-pRenderingInfo-09588 (measured, ten messages in one
            // leg before this line existed). Subsequent passes keep GENERAL by storing the attachment.
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image).srcAccessMask(0).dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, 0,
                    null, null, barrier);
            this.transitioned = true;
        }
        VkRenderingAttachmentInfoKHR.Buffer depth = VkRenderingAttachmentInfoKHR.calloc(1, stack);
        depth.get(0).sType$Default().imageView(this.view)
                .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        org.lwjgl.vulkan.VkClearValue clear = org.lwjgl.vulkan.VkClearValue.calloc(stack);
        clear.depthStencil().depth(1.0f);
        clear.depthStencil().stencil(0);
        depth.get(0).clearValue(clear);
        org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE));
        VkRenderingInfoKHR rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                .renderArea(area).layerCount(1).pDepthAttachment(depth.get(0));
        vkCmdBeginRenderingKHR(commandBuffer, rendering);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        vkCmdSetViewport(commandBuffer, 0, org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                .x(0.0f).y(0.0f).width(SIZE).height(SIZE).minDepth(0.0f).maxDepth(1.0f));
        vkCmdSetScissor(commandBuffer, 0, org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE)));
        vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        vkCmdEndRenderingKHR(commandBuffer);
        this.passes++;
    }

    /**
     * Reads a small corner of the map back and records its depth range.
     * <p>
     * The counters say the pass recorded; they do not say the rasteriser wrote anything. A triangle at clip depth 0.5 over
     * the whole map must leave 0.5 everywhere in the sampled corner (and 1.0 outside it), so the range is the evidence that
     * the depth-only pipeline is writing depth, which is what the terrain draw will depend on. Run once, on the frame after
     * the first pass, so a frame's cost does not carry it.
     */
    private void sampleDepthOnce() {
        if (this.depthSampled || this.image == VK_NULL_HANDLE) {
            return;
        }
        this.depthSampled = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final int edge = 8;
            int bytes = edge * edge * 4;
            LongBuffer handle = stack.mallocLong(1);
            LongBuffer memory = stack.mallocLong(1);
            LongBuffer pool = stack.mallocLong(1);
            LongBuffer buffer = stack.mallocLong(1);
            if (vkCreateBuffer(this.context.device(), org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(bytes).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE), null, handle) != VK_SUCCESS) {
                this.lastFailure = "readback buffer creation failed";
                return;
            }
            long readback = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), readback, requirements);
            if (vkAllocateMemory(this.context.device(), org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                            "shadow map readback")), null, memory) != VK_SUCCESS) {
                vkDestroyBuffer(this.context.device(), readback, null);
                this.lastFailure = "readback memory allocation failed";
                return;
            }
            vkBindBufferMemory(this.context.device(), readback, memory.get(0), 0);
            org.lwjgl.vulkan.VkCommandPoolCreateInfo poolInfo = org.lwjgl.vulkan.VkCommandPoolCreateInfo
                    .calloc(stack).sType$Default().queueFamilyIndex(this.context.queueFamilyIndex());
            vkCreateCommandPool(this.context.device(), poolInfo, null, pool);
            org.lwjgl.vulkan.VkCommandBufferAllocateInfo allocateInfo =
                    org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                            .commandPool(pool.get(0)).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
            org.lwjgl.PointerBuffer buffers = stack.callocPointer(1);
            vkAllocateCommandBuffers(this.context.device(), allocateInfo, buffers);
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer =
                    new org.lwjgl.vulkan.VkCommandBuffer(buffers.get(0), this.context.device());
            vkBeginCommandBuffer(commandBuffer, org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default());
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().oldLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image).srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
            org.lwjgl.vulkan.VkBufferImageCopy.Buffer copy = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            copy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).mipLevel(0)
                    .baseArrayLayer(0).layerCount(1);
            copy.get(0).imageOffset().set(0, 0, 0);
            copy.get(0).imageExtent().set(edge, edge, 1);
            vkCmdCopyImageToBuffer(commandBuffer, this.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readback, copy);
            barrier.get(0).oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, 0,
                    null, null, barrier);
            vkEndCommandBuffer(commandBuffer);
            LongBuffer fence = stack.mallocLong(1);
            vkCreateFence(this.context.device(), org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack).sType$Default(),
                    null, fence);
            vkQueueSubmit(this.context.queue(),
                    org.lwjgl.vulkan.VkSubmitInfo.calloc(stack).sType$Default()
                            .pCommandBuffers(stack.pointers(commandBuffer.address())), fence.get(0));
            vkWaitForFences(this.context.device(), fence.get(0), true, Long.MAX_VALUE);
            vkDestroyFence(this.context.device(), fence.get(0), null);
            vkDestroyCommandPool(this.context.device(), pool.get(0), null);
            org.lwjgl.PointerBuffer mapped = stack.callocPointer(1);
            vkMapMemory(this.context.device(), memory.get(0), 0, bytes, 0, mapped);
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (int i = 0; i < edge * edge; i++) {
                float value = org.lwjgl.system.MemoryUtil.memGetFloat(mapped.get(0) + (long) i * 4L);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            vkUnmapMemory(this.context.device(), memory.get(0));
            this.sampledDepthMin = min;
            this.sampledDepthMax = max;
            vkFreeMemory(this.context.device(), memory.get(0), null);
            vkDestroyBuffer(this.context.device(), readback, null);
        } catch (RuntimeException | LinkageError error) {
            this.lastFailure = "depth sample: " + error;
        }
    }

    /**
     * Builds the pipeline that draws the game's terrain into the map: the terrain's own vertex module and vertex layout,
     * a push-constant range of the same size the terrain pass uses (it carries the matrices), and no fragment stage - the
     * rasteriser writes depth.
     */
    public boolean createTerrainPipeline(org.soutalab.celtium.vulkan.VulkanTerrainPipeline terrain) {
        if (this.terrainPipelines.containsKey(terrain)) {
            return true;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            // The terrain's own pipeline layout, not a fresh one: its vertex stage reads the region uniforms through
            // descriptor set 0 (VUID-VkGraphicsPipelineCreateInfo-layout-07988 named exactly that when this created a
            // layout without them), and the descriptors the terrain pass bound remain bound in the command buffer, so the
            // shadow draw only has to bind its pipeline and push the matrices.
            this.terrainPipelineLayout = terrain.pipelineLayout();
            if (this.terrainPipelineLayout == 0L) {
                this.failures++;
                this.lastFailure = "the terrain pipeline has no layout";
                return false;
            }
            int[] formats = terrain.vertexFormats();
            int[] offsets = terrain.vertexOffsets();
            org.lwjgl.vulkan.VkVertexInputBindingDescription.Buffer bindings =
                    org.lwjgl.vulkan.VkVertexInputBindingDescription.calloc(1, stack);
            bindings.get(0).binding(0).stride(terrain.vertexStride())
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            org.lwjgl.vulkan.VkVertexInputAttributeDescription.Buffer attributes =
                    org.lwjgl.vulkan.VkVertexInputAttributeDescription.calloc(formats.length, stack);
            for (int i = 0; i < formats.length; i++) {
                attributes.get(i).location(i).binding(0).format(formats[i]).offset(offsets[i]);
            }
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(1, stack);
            // The module comes from the same checked-in SPIR-V the terrain pass loads; SpirvShaders.load hands over a
            // native buffer the caller owns, so it is freed here after the module is created.
            java.nio.ByteBuffer code = org.soutalab.celtium.vulkan.SpirvShaders.load(terrain.vertexModuleName());
            if (code == null) {
                this.failures++;
                this.lastFailure = "shader module " + terrain.vertexModuleName() + " unavailable";
                return false;
            }
            org.lwjgl.vulkan.VkShaderModuleCreateInfo moduleInfo =
                    org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            if (vkCreateShaderModule(this.context.device(), moduleInfo, null, handle) != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkCreateShaderModule(shadow terrain) failed";
                return false;
            }
            long module = handle.get(0);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(module)
                    .pName(stack.UTF8("main"));
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default().pVertexBindingDescriptions(bindings)
                    .pVertexAttributeDescriptions(attributes);
            VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default().polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE)
                    .depthBiasEnable(true).depthBiasConstantFactor(1.25f).depthBiasSlopeFactor(1.75f)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depthState = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default().depthTestEnable(true).depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
            org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamic =
                    org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                            .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
            org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR rendering =
                    org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR.calloc(stack).sType$Default()
                            .colorAttachmentCount(0).depthAttachmentFormat(VK_FORMAT_D32_SFLOAT);
            VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            info.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput)
                    .pInputAssemblyState(assembly).pViewportState(viewport).pRasterizationState(raster)
                    .pMultisampleState(multisample).pDepthStencilState(depthState).pDynamicState(dynamic)
                    .layout(this.terrainPipelineLayout).renderPass(VK_NULL_HANDLE).pNext(rendering.address());
            if (vkCreateGraphicsPipelines(this.context.device(), VK_NULL_HANDLE, info, null, handle) != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkCreateGraphicsPipelines(shadow terrain) failed";
                return false;
            }
            this.terrainPipeline = handle.get(0);
            this.terrainPipelines.put(terrain, Long.valueOf(this.terrainPipeline));
            this.terrainModule = module;
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.failures++;
            this.lastFailure = "shadow terrain pipeline: " + error;
            return false;
        }
    }

    /**
     * Records the game's terrain into the map with the light's matrices: the same vertex and index buffers the terrain pass
     * uses, the same indirect commands, and the terrain's own vertex stage, so the depth in the map is the geometry the
     * player sees rather than a test triangle.
     *
     * @param block the 32-float push block (projection then model view) the terrain vertex stage expects
     * @return whether anything was drawn
     */
    public boolean recordTerrain(org.soutalab.celtium.vulkan.VulkanTerrainPipeline terrain,
                                 VkCommandBuffer commandBuffer, MemoryStack stack, long vertexBuffer,
                                 long indexBuffer, long indirectBuffer, long indirectOffset, int indirectCount,
                                 float[] block, long descriptorSet, int dynamicOffset) {
        java.util.List<Batch> one = new java.util.ArrayList<Batch>(1);
        one.add(new Batch(vertexBuffer, indexBuffer, indirectBuffer, indirectOffset, indirectCount, descriptorSet,
                dynamicOffset, block));
        return this.recordTerrainBatches(terrain, commandBuffer, stack, one);
    }

    /**
     * Records every captured terrain batch into the map in one rendering scope.
     * <p>
     * One scope and one pipeline bind for the whole frame's shadow casters; each batch binds its own descriptor set with its
     * own dynamic offset, its own vertex and index buffers and its own light-space push block. Everything is checked before
     * the scope opens, because a return between Begin and End would leave the pass unbalanced (#399).
     *
     * @param terrain       the terrain pipeline whose layout and vertex stage the map is drawn with
     * @param commandBuffer where to record
     * @param stack         the frame's stack for throwaway structures
     * @param batches       the frame's captured batches; a batch without a descriptor set is skipped, not drawn
     * @return whether anything was drawn
     */
    public boolean recordTerrainBatches(org.soutalab.celtium.vulkan.VulkanTerrainPipeline terrain,
                                        VkCommandBuffer commandBuffer, MemoryStack stack,
                                        java.util.List<Batch> batches) {
        if (batches == null || batches.isEmpty()) {
            return false;
        }
        if (!this.terrainPipelines.containsKey(terrain) && !createTerrainPipeline(terrain)) {
            return false;
        }
        long terrainPipelineForThisDraw = this.terrainPipelines.get(terrain).longValue();
        // Preconditions, all before the scope opens.
        int drawable = 0;
        for (int i = 0; i < batches.size(); i++) {
            Batch batch = batches.get(i);
            if (batch.vertexBuffer != 0L && batch.indirectBuffer != 0L && batch.indirectCount > 0
                    && batch.pushBlock != null && batch.pushBlock.length >= 32 && batch.descriptorSet != 0L
                    && !Boolean.getBoolean("celtium.shadow.noDescriptors")) {
                drawable++;
            }
        }
        if (terrainPipelineForThisDraw == 0L || drawable == 0) {
            return false;
        }
        VkRenderingAttachmentInfoKHR.Buffer depth = VkRenderingAttachmentInfoKHR.calloc(1, stack);
        depth.get(0).sType$Default().imageView(this.view)
                .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        org.lwjgl.vulkan.VkClearValue clear = org.lwjgl.vulkan.VkClearValue.calloc(stack);
        clear.depthStencil().depth(1.0f);
        clear.depthStencil().stencil(0);
        depth.get(0).clearValue(clear);
        org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE));
        VkRenderingInfoKHR rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                .renderArea(area).layerCount(1).pDepthAttachment(depth.get(0));
        boolean barriers = !Boolean.getBoolean("celtium.shadow.noBarriers");
        if (barriers) {
            depthBarrier(commandBuffer, stack, VK_ACCESS_SHADER_READ_BIT,
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
        }
        int recorded = 0;
        vkCmdBeginRenderingKHR(commandBuffer, rendering);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, terrainPipelineForThisDraw);
        vkCmdSetViewport(commandBuffer, 0, org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                .x(0.0f).y(0.0f).width(SIZE).height(SIZE).minDepth(0.0f).maxDepth(1.0f));
        vkCmdSetScissor(commandBuffer, 0, org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE)));
        int maxDraws = Integer.getInteger("celtium.shadow.maxDraws", Integer.MAX_VALUE).intValue();
        for (int i = 0; i < batches.size(); i++) {
            Batch batch = batches.get(i);
            if (batch.vertexBuffer == 0L || batch.indirectBuffer == 0L || batch.indirectCount <= 0
                    || batch.pushBlock == null || batch.pushBlock.length < 32 || batch.descriptorSet == 0L
                    || Boolean.getBoolean("celtium.shadow.noDescriptors")) {
                continue;
            }
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, terrain.pipelineLayout(), 0,
                    stack.longs(batch.descriptorSet), stack.ints(batch.dynamicOffset));
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(batch.vertexBuffer), stack.longs(0L));
            if (batch.indexBuffer != 0L) {
                vkCmdBindIndexBuffer(commandBuffer, batch.indexBuffer, 0L, VK_INDEX_TYPE_UINT32);
            }
            float[] lightBlock = lightSpaceBlock(batch.pushBlock);
            java.nio.FloatBuffer push = stack.mallocFloat(32);
            for (int k = 0; k < 32; k++) {
                push.put(k, lightBlock[k]);
            }
            vkCmdPushConstants(commandBuffer, terrain.pipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, push);
            if (!Boolean.getBoolean("celtium.shadow.noDraw")) {
                int draws = Math.min(batch.indirectCount, maxDraws);
                vkCmdDrawIndexedIndirect(commandBuffer, batch.indirectBuffer, batch.indirectOffset, draws, 20);
                recorded += draws;
            }
        }
        vkCmdEndRenderingKHR(commandBuffer);
        if (barriers) {
            depthBarrier(commandBuffer, stack, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_ACCESS_SHADER_READ_BIT);
        }
        this.passes++;
        if (recorded > 0) {
            this.terrainPasses++;
        }
        return recorded > 0;
    }

    /** One GENERAL-to-GENERAL barrier over the map with the given access masks. */
    private void depthBarrier(VkCommandBuffer commandBuffer, MemoryStack stack, int srcAccess, int dstAccess) {
        org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0).sType$Default().oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(this.image).srcAccessMask(srcAccess).dstAccessMask(dstAccess);
        barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
        vkCmdPipelineBarrier(commandBuffer,
                srcAccess == VK_ACCESS_SHADER_READ_BIT ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                        : VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                dstAccess == VK_ACCESS_SHADER_READ_BIT ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                        : VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                0, null, null, barrier);
    }

    /**
     * Records one shadow pass into a command buffer of this class's own, submits it with a fence and waits with a bound.
     * <p>
     * The frame loop stops presenting when the pass is recorded into the frame's command buffer (3 presents in 60 s against
     * 600), and six switches - draw, descriptors, sources, barriers, shader sampling, and the map's clear - have each been
     * ruled out. This separates the two remaining explanations: whether the pass never completes on the GPU, or whether
     * something about carrying it in the frame's command buffer is what stalls.
     *
     * @return whether the submission completed within the bound, with the reason when it did not
     */
    public String recordAndSubmitOnce() {
        if (this.image == VK_NULL_HANDLE) {
            return "no image";
        }
        long pool = VK_NULL_HANDLE;
        long fence = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            if (vkCreateCommandPool(this.context.device(), org.lwjgl.vulkan.VkCommandPoolCreateInfo
                    .calloc(stack).sType$Default().queueFamilyIndex(this.context.queueFamilyIndex()), null,
                    handle) != VK_SUCCESS) {
                return "pool failed";
            }
            pool = handle.get(0);
            org.lwjgl.PointerBuffer buffers = stack.callocPointer(1);
            if (vkAllocateCommandBuffers(this.context.device(), org.lwjgl.vulkan.VkCommandBufferAllocateInfo
                    .calloc(stack).sType$Default().commandPool(pool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1), buffers) != VK_SUCCESS) {
                return "buffer failed";
            }
            VkCommandBuffer commandBuffer = new VkCommandBuffer(buffers.get(0), this.context.device());
            vkBeginCommandBuffer(commandBuffer, org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default());
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image).srcAccessMask(0).dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, 0,
                    null, null, barrier);
            VkRenderingAttachmentInfoKHR.Buffer depth = VkRenderingAttachmentInfoKHR.calloc(1, stack);
            depth.get(0).sType$Default().imageView(this.view).imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            org.lwjgl.vulkan.VkClearValue clear = org.lwjgl.vulkan.VkClearValue.calloc(stack);
            clear.depthStencil().depth(1.0f);
            depth.get(0).clearValue(clear);
            org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                    .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE));
            VkRenderingInfoKHR rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                    .renderArea(area).layerCount(1).pDepthAttachment(depth.get(0));
            vkCmdBeginRenderingKHR(commandBuffer, rendering);
            vkCmdEndRenderingKHR(commandBuffer);
            vkEndCommandBuffer(commandBuffer);
            if (vkCreateFence(this.context.device(), org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack)
                    .sType$Default(), null, handle) != VK_SUCCESS) {
                return "fence failed";
            }
            fence = handle.get(0);
            vkQueueSubmit(this.context.queue(), org.lwjgl.vulkan.VkSubmitInfo.calloc(stack).sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer.address())), fence);
            int waited = vkWaitForFences(this.context.device(), fence, true, 2_000_000_000L);
            if (waited != VK_SUCCESS) {
                // The probe used to report the timeout and then free the pool in the finally anyway, which is the same
                // VUID-commandPool-00041 mistake as the placeholder transitions (#403): a timeout proves nothing about
                // completion, so ownership moves to the pending list and only a signalled fence releases it.
                this.pendingProbePools.retain(pool, fence);
                pool = VK_NULL_HANDLE;
                fence = VK_NULL_HANDLE;
            }
            return waited == VK_SUCCESS ? "completed" : "did not complete (" + waited + ")";
        } catch (RuntimeException | LinkageError error) {
            return "threw " + error;
        } finally {
            if (fence != VK_NULL_HANDLE) {
                vkDestroyFence(this.context.device(), fence, null);
            }
            if (pool != VK_NULL_HANDLE) {
                vkDestroyCommandPool(this.context.device(), pool, null);
            }
            this.pendingProbePools.reclaim(this.context.device());
        }
    }

    /**
     * Records the terrain pass into a command buffer of its own and submits it, without waiting.
     * <p>
     * The pass stalls the game when it is recorded into the frame's own command buffer (3 presents in 45 s against 600,
     * with the recording itself measured at zero and the same scope completing in under two seconds on its own buffer).
     * So it is submitted as its own queue packet instead: same queue, so ordering against the frame's submissions is the
     * queue's, and the world pass samples what the previous packet wrote.
     *
     * @return a one-line result for the log, or null when nothing was recorded
     */
    public String recordTerrainAndSubmit(org.soutalab.celtium.vulkan.VulkanTerrainPipeline terrain, long vertexBuffer,
                                         long indexBuffer, long indirectBuffer, long indirectOffset, int indirectCount,
                                         float[] block, long descriptorSet, int dynamicOffset) {
        if (this.image == VK_NULL_HANDLE || vertexBuffer == 0L || indirectBuffer == 0L
                || !this.terrainPipelines.containsKey(terrain) && !createTerrainPipeline(terrain)) {
            // Nothing was captured for this frame (the colour pass has not drawn this region yet), and drawing from a
            // null buffer is what the layer reports as vkCmdDrawIndexedIndirect ... buffer is VK_NULL_HANDLE.
            return null;
        }
        long terrainPipelineForThisDraw = this.terrainPipelines.get(terrain).longValue();
        reclaimCompletedSubmissions();
        if (this.pendingOwnSubmissions.size() >= MAX_PENDING_OWN_SUBMISSIONS) {
            // Every retained submission is memory and a fence; a GPU that has stopped signalling must not let this grow.
            this.failures++;
            this.lastFailure = "pending own submissions at the limit (" + MAX_PENDING_OWN_SUBMISSIONS + ")";
            return "pending submissions at the limit";
        }
        long pool = VK_NULL_HANDLE;
        long ownFence = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            if (vkCreateCommandPool(this.context.device(), org.lwjgl.vulkan.VkCommandPoolCreateInfo
                    .calloc(stack).sType$Default().queueFamilyIndex(this.context.queueFamilyIndex()), null,
                    handle) != VK_SUCCESS) {
                return "pool failed";
            }
            pool = handle.get(0);
            org.lwjgl.PointerBuffer buffers = stack.callocPointer(1);
            if (vkAllocateCommandBuffers(this.context.device(), org.lwjgl.vulkan.VkCommandBufferAllocateInfo
                    .calloc(stack).sType$Default().commandPool(pool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1), buffers) != VK_SUCCESS) {
                return "buffer failed";
            }
            VkCommandBuffer commandBuffer = new VkCommandBuffer(buffers.get(0), this.context.device());
            vkBeginCommandBuffer(commandBuffer, org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default());
            depthBarrier(commandBuffer, stack, VK_ACCESS_SHADER_READ_BIT,
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            VkRenderingAttachmentInfoKHR.Buffer depth = VkRenderingAttachmentInfoKHR.calloc(1, stack);
            depth.get(0).sType$Default().imageView(this.view).imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            org.lwjgl.vulkan.VkClearValue clear = org.lwjgl.vulkan.VkClearValue.calloc(stack);
            clear.depthStencil().depth(1.0f);
            depth.get(0).clearValue(clear);
            org.lwjgl.vulkan.VkRect2D area = org.lwjgl.vulkan.VkRect2D.calloc(stack)
                    .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE));
            VkRenderingInfoKHR rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                    .renderArea(area).layerCount(1).pDepthAttachment(depth.get(0));
            vkCmdBeginRenderingKHR(commandBuffer, rendering);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, terrainPipelineForThisDraw);
            vkCmdSetViewport(commandBuffer, 0, org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                    .x(0.0f).y(0.0f).width(SIZE).height(SIZE).minDepth(0.0f).maxDepth(1.0f));
            vkCmdSetScissor(commandBuffer, 0, org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                    .offset(it -> it.set(0, 0)).extent(it -> it.set(SIZE, SIZE)));
            if (descriptorSet == 0L) {
                // Same rule as recordTerrain: no set 0, no draw - and nothing to submit, so the pool this command buffer
                // came from stays with the caller and is destroyed in its finally (submitting here and destroying the
                // pool afterwards is what the layer reported as vkDestroyCommandPool ... is in use).
                return "no descriptor set";
            }
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, terrain.pipelineLayout(), 0,
                    stack.longs(descriptorSet), stack.ints(dynamicOffset));
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(vertexBuffer), stack.longs(0L));
            if (indexBuffer != 0L) {
                vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0L, VK_INDEX_TYPE_UINT32);
            }
            float[] lightBlock = lightSpaceBlock(block);
            java.nio.FloatBuffer push = stack.mallocFloat(32);
            for (int i = 0; i < 32; i++) {
                push.put(i, lightBlock[i]);
            }
            vkCmdPushConstants(commandBuffer, terrain.pipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, push);
            vkCmdDrawIndexedIndirect(commandBuffer, indirectBuffer, indirectOffset, indirectCount, 20);
            vkCmdEndRenderingKHR(commandBuffer);
            depthBarrier(commandBuffer, stack, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_ACCESS_SHADER_READ_BIT);
            vkEndCommandBuffer(commandBuffer);
            java.nio.LongBuffer fenceHandle = stack.mallocLong(1);
            if (vkCreateFence(this.context.device(), org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack)
                    .sType$Default(), null, fenceHandle) != VK_SUCCESS) {
                return "fence failed";
            }
            ownFence = fenceHandle.get(0);
            int submitResult = vkQueueSubmit(this.context.queue(), org.lwjgl.vulkan.VkSubmitInfo.calloc(stack)
                    .sType$Default().pCommandBuffers(stack.pointers(commandBuffer.address())), ownFence);
            if (submitResult != VK_SUCCESS) {
                // A submit the queue refused owns nothing: counting it as a pass and moving its pool to the pending list
                // left an unsignalled fence at the head of that list, which then blocked every later reclamation (#402).
                this.failures++;
                this.lastFailure = "vkQueueSubmit returned " + submitResult;
                vkDestroyFence(this.context.device(), ownFence, null);
                ownFence = VK_NULL_HANDLE;
                return "submit failed (" + submitResult + ")";
            }
            this.pendingOwnSubmissions.add(new long[]{pool, ownFence});
            pool = VK_NULL_HANDLE;   // ownership moves to the pending list
            this.passes++;
            this.terrainPasses++;
            return "submitted draws=" + indirectCount;
        } catch (RuntimeException | LinkageError error) {
            return "threw " + error;
        } finally {
            if (pool != VK_NULL_HANDLE) {
                vkDestroyCommandPool(this.context.device(), pool, null);
            }
        }
    }

    /**
     * {@return the terrain push block with its two matrices replaced by the light's}.
     * <p>
     * The terrain vertex shader computes {@code gl_Position = projection * modelView * worldPosition}, and the block it is
     * given was captured from the colour pass, so its matrices are the camera's: the map was drawn from the camera's
     * viewpoint while the receiver compared coordinates from the light's (#398). Replacing the two mat4 slots with the
     * light's view-projection and the identity keeps everything else - region offset, per-draw translations, fog and
     * texture fields - exactly as the colour pass had it.
     *
     * @param block the captured terrain push block
     * @return a copy whose first two mat4 slots are the light's view-projection and the identity
     */
    private float[] lightSpaceBlock(float[] block) {
        float[] light = new float[block.length];
        System.arraycopy(block, 0, light, 0, block.length);
        if (this.matrixBuffer != null) {
            java.nio.FloatBuffer matrices = org.lwjgl.system.MemoryUtil.memFloatBuffer(
                    this.matrixBuffer.mappedAddress(), 16);
            for (int i = 0; i < 16; i++) {
                light[i] = matrices.get(i);
            }
        }
        for (int i = 16; i < 32; i++) {
            light[i] = 0.0f;
        }
        light[16] = 1.0f;
        light[21] = 1.0f;
        light[26] = 1.0f;
        light[31] = 1.0f;
        return light;
    }

    /**
     * Moves the depth image from UNDEFINED to GENERAL, once, on its own command buffer.
     *
     * @return whether the transition reached the queue
     */
    private boolean transitionImageToGeneral() {
        long pool = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            if (vkCreateCommandPool(this.context.device(), org.lwjgl.vulkan.VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default().queueFamilyIndex(this.context.queueFamilyIndex()), null, handle) != VK_SUCCESS) {
                return false;
            }
            pool = handle.get(0);
            org.lwjgl.PointerBuffer buffers = stack.callocPointer(1);
            if (vkAllocateCommandBuffers(this.context.device(), org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default().commandPool(pool).level(org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1), buffers) != VK_SUCCESS) {
                return false;
            }
            VkCommandBuffer commandBuffer = new VkCommandBuffer(buffers.get(0), this.context.device());
            vkBeginCommandBuffer(commandBuffer, org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default());
            // UNDEFINED -> GENERAL explicitly: depthBarrier() declares GENERAL on both sides (it orders accesses for a
            // pass that already owns the layout), so calling it here left the image in UNDEFINED and the validation layer
            // reported the receiver's draw as "expects GENERAL ... current layout is UNDEFINED".
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer firstUse =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            firstUse.get(0).sType$Default()
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image).srcAccessMask(0).dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
            firstUse.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commandBuffer,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, firstUse);
            vkEndCommandBuffer(commandBuffer);
            java.nio.LongBuffer fence = stack.mallocLong(1);
            if (vkCreateFence(this.context.device(), org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack)
                    .sType$Default(), null, fence) != VK_SUCCESS) {
                return false;
            }
            if (vkQueueSubmit(this.context.queue(), org.lwjgl.vulkan.VkSubmitInfo.calloc(stack).sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer.address())), fence.get(0)) != VK_SUCCESS) {
                vkDestroyFence(this.context.device(), fence.get(0), null);
                return false;
            }
            int waited = vkWaitForFences(this.context.device(), fence.get(0), true, 2_000_000_000L);
            vkDestroyFence(this.context.device(), fence.get(0), null);
            if (waited != VK_SUCCESS) {
                return false;
            }
            vkDestroyCommandPool(this.context.device(), pool, null);
            pool = VK_NULL_HANDLE;
            return true;
        } catch (RuntimeException | LinkageError error) {
            return false;
        } finally {
            if (pool != VK_NULL_HANDLE) {
                vkDestroyCommandPool(this.context.device(), pool, null);
            }
        }
    }

    /**
     * One terrain draw's worth of state, captured together for the shadow pass.
     * <p>
     * A frame draws several regions, each with its own vertex buffer, index buffer, indirect range and descriptor set. The
     * pass used to draw only the first of those, so the map held whatever region happened to be recorded first - usually a
     * distant one - and the objects near the camera cast nothing (#397's coverage note).
     */
    public static final class Batch {
        public final long vertexBuffer;
        public final long indexBuffer;
        public final long indirectBuffer;
        public final long indirectOffset;
        public final int indirectCount;
        public final long descriptorSet;
        public final int dynamicOffset;
        public final float[] pushBlock;

        public Batch(long vertexBuffer, long indexBuffer, long indirectBuffer, long indirectOffset, int indirectCount,
                     long descriptorSet, int dynamicOffset, float[] pushBlock) {
            this.vertexBuffer = vertexBuffer;
            this.indexBuffer = indexBuffer;
            this.indirectBuffer = indirectBuffer;
            this.indirectOffset = indirectOffset;
            this.indirectCount = indirectCount;
            this.descriptorSet = descriptorSet;
            this.dynamicOffset = dynamicOffset;
            this.pushBlock = pushBlock;
        }
    }

    /**
     * Records every batch into the map on a command buffer of its own and submits it, without waiting.
     * <p>
     * The own-submission path needs the same coverage as the in-frame one, so both go through
     * {@link #recordTerrainBatches}: one scope, one clear, every batch's descriptor set and geometry.
     *
     * @param terrain the terrain pipeline the map is drawn with
     * @param batches the frame's captured batches
     * @return a one-line summary, or null when nothing was recorded
     */
    public String recordTerrainBatchesAndSubmit(org.soutalab.celtium.vulkan.VulkanTerrainPipeline terrain,
                                                java.util.List<Batch> batches) {
        if (this.image == VK_NULL_HANDLE || batches == null || batches.isEmpty()
                || !this.terrainPipelines.containsKey(terrain) && !createTerrainPipeline(terrain)) {
            return null;
        }
        reclaimCompletedSubmissions();
        if (this.pendingOwnSubmissions.size() >= MAX_PENDING_OWN_SUBMISSIONS) {
            this.failures++;
            this.lastFailure = "pending own submissions at the limit (" + MAX_PENDING_OWN_SUBMISSIONS + ")";
            return "pending submissions at the limit";
        }
        long pool = VK_NULL_HANDLE;
        long ownFence = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            if (vkCreateCommandPool(this.context.device(), org.lwjgl.vulkan.VkCommandPoolCreateInfo
                    .calloc(stack).sType$Default().queueFamilyIndex(this.context.queueFamilyIndex()), null,
                    handle) != VK_SUCCESS) {
                return "pool failed";
            }
            pool = handle.get(0);
            org.lwjgl.PointerBuffer buffers = stack.callocPointer(1);
            if (vkAllocateCommandBuffers(this.context.device(), org.lwjgl.vulkan.VkCommandBufferAllocateInfo
                    .calloc(stack).sType$Default().commandPool(pool).level(org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1), buffers) != VK_SUCCESS) {
                return "buffer failed";
            }
            VkCommandBuffer commandBuffer = new VkCommandBuffer(buffers.get(0), this.context.device());
            vkBeginCommandBuffer(commandBuffer, org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default());
            if (!this.recordTerrainBatches(terrain, commandBuffer, stack, batches)) {
                vkEndCommandBuffer(commandBuffer);
                return "nothing to draw";
            }
            vkEndCommandBuffer(commandBuffer);
            java.nio.LongBuffer fenceHandle = stack.mallocLong(1);
            if (vkCreateFence(this.context.device(), org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack)
                    .sType$Default(), null, fenceHandle) != VK_SUCCESS) {
                return "fence failed";
            }
            ownFence = fenceHandle.get(0);
            int submitResult = vkQueueSubmit(this.context.queue(), org.lwjgl.vulkan.VkSubmitInfo.calloc(stack)
                    .sType$Default().pCommandBuffers(stack.pointers(commandBuffer.address())), ownFence);
            if (submitResult != VK_SUCCESS) {
                this.failures++;
                this.lastFailure = "vkQueueSubmit returned " + submitResult;
                vkDestroyFence(this.context.device(), ownFence, null);
                ownFence = VK_NULL_HANDLE;
                return "submit failed (" + submitResult + ")";
            }
            this.pendingOwnSubmissions.add(new long[]{pool, ownFence});
            pool = VK_NULL_HANDLE;
            ownFence = VK_NULL_HANDLE;
            return "submitted batches=" + batches.size();
        } catch (RuntimeException | LinkageError error) {
            return "threw " + error;
        } finally {
            if (ownFence != VK_NULL_HANDLE) {
                vkDestroyFence(this.context.device(), ownFence, null);
            }
            if (pool != VK_NULL_HANDLE) {
                vkDestroyCommandPool(this.context.device(), pool, null);
            }
        }
    }

    /** {@return whether the terrain pipeline has been built}. */
    public boolean hasTerrainPipeline() {
        return this.terrainPipeline != 0L;
    }

    /** {@return the pass's counters for the verification line}. */
    public String statistics() {
        return "shadowPasses=" + this.passes + " terrainPasses=" + this.terrainPasses + " size=" + SIZE
                + " failures=" + this.failures + (this.lastFailure.isEmpty() ? "" : " lastFailure=[" + this.lastFailure + "]")
                + (this.sampledDepthMin < 0.0f ? "" : " depthCorner=[" + this.sampledDepthMin + ","
                        + this.sampledDepthMax + "]")
;
    }

    /** Frees the pass's resources. */
    public void destroy() {
        // Everything this pass owns, in one place (#400). It used to release the image, view and memory only: the probe
        // pipeline and its layout, the per-terrain pipelines, the sampler, the matrix block and the pools of the own
        // submissions stayed alive, and a context rebuild inherited handles from the old device.
        if (this.pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(this.context.device(), this.pipeline, null);
            this.pipeline = VK_NULL_HANDLE;
        }
        if (this.pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(this.context.device(), this.pipelineLayout, null);
            this.pipelineLayout = VK_NULL_HANDLE;
        }
        for (java.util.Iterator<Long> pipelines = this.terrainPipelines.values().iterator(); pipelines.hasNext();) {
            vkDestroyPipeline(this.context.device(), pipelines.next().longValue(), null);
        }
        this.terrainPipelines.clear();
        if (this.sampler != VK_NULL_HANDLE) {
            vkDestroySampler(this.context.device(), this.sampler, null);
            this.sampler = VK_NULL_HANDLE;
        }
        if (this.matrixBuffer != null) {
            this.matrixBuffer.delete();
            this.matrixBuffer = null;
        }
        while (!this.pendingOwnSubmissions.isEmpty()) {
            long[] pending = this.pendingOwnSubmissions.poll();
            vkDestroyFence(this.context.device(), pending[1], null);
            vkDestroyCommandPool(this.context.device(), pending[0], null);
        }
        this.pendingProbePools.releaseAllOnDeviceTeardown(this.context.device());
        if (this.view != VK_NULL_HANDLE) {
            vkDestroyImageView(this.context.device(), this.view, null);
            this.view = VK_NULL_HANDLE;
        }
        if (this.image != VK_NULL_HANDLE) {
            vkDestroyImage(this.context.device(), this.image, null);
            this.image = VK_NULL_HANDLE;
        }
        if (this.memory != VK_NULL_HANDLE) {
            vkFreeMemory(this.context.device(), this.memory, null);
            this.memory = VK_NULL_HANDLE;
        }
    }

    /** Destroys the pools of submissions the queue has finished, and no others. */
    /** How many own-submissions may wait before a new one is refused: each holds a command pool and a fence. */
    private static final int MAX_PENDING_OWN_SUBMISSIONS = 64;

    private void reclaimCompletedSubmissions() {
        while (!this.pendingOwnSubmissions.isEmpty()) {
            long[] pending = this.pendingOwnSubmissions.peek();
            if (vkGetFenceStatus(this.context.device(), pending[1]) != VK_SUCCESS) {
                return;
            }
            this.pendingOwnSubmissions.poll();
            vkDestroyFence(this.context.device(), pending[1], null);
            vkDestroyCommandPool(this.context.device(), pending[0], null);
        }
    }
}
