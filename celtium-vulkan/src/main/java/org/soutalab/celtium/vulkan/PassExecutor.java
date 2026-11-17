package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkViewport;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCmdSetScissor;
import static org.lwjgl.vulkan.VK10.vkCmdSetViewport;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;

/**
 * Frame-owned executor for registered {@link NativePassRegistry} passes (Phase 0).
 * <p>
 * Reuses {@link VulkanModProgramPipeline} and its prepared {@link VulkanModProgramPipeline.Draw} path to record
 * fullscreen passes into the backend's frame command stream. When no passes are registered for a stage, every call
 * returns immediately with zero allocations and zero Vulkan work.
 */
final class PassExecutor {

    private record RenderPassKey(int vkFormat, boolean depth, boolean clear) {
    }

    private record FramebufferKey(String targetName, long view, long renderPass, int width, int height) {
    }

    private record PipelineKey(String passName, byte[] vertexSpirv, byte[] fragmentSpirv, long renderPass) {
        @Override
        public boolean equals(Object other) {
            return other instanceof PipelineKey k
                    && this.passName.equals(k.passName)
                    && this.vertexSpirv == k.vertexSpirv
                    && this.fragmentSpirv == k.fragmentSpirv
                    && this.renderPass == k.renderPass;
        }

        @Override
        public int hashCode() {
            int h = this.passName.hashCode();
            h = 31 * h + System.identityHashCode(this.vertexSpirv);
            h = 31 * h + System.identityHashCode(this.fragmentSpirv);
            h = 31 * h + Long.hashCode(this.renderPass);
            return h;
        }
    }

    private static final VulkanDynamicMesh.Layout FULLSCREEN_QUAD_LAYOUT =
            new VulkanDynamicMesh.Layout(VulkanImmediateTexturePipeline.POSITION_TEX_BYTES, 0, 12, -1, -1, -1);

    private final VulkanContext context;
    private final RenderTargetRegistry targetRegistry;
    private final PublishedFrameTextures publishedTextures;
    private final NativePassRegistry passRegistry;
    private final NamedRenderTargetAllocator targetAllocator;

    private final Map<RenderPassKey, Long> renderPasses = new HashMap<>();
    private final Map<FramebufferKey, Long> framebuffers = new HashMap<>();
    private final Map<PipelineKey, VulkanModProgramPipeline> pipelines = new HashMap<>();
    private VulkanDynamicMesh quadMesh;

    PassExecutor(VulkanContext context, RenderTargetRegistry targetRegistry,
                 PublishedFrameTextures publishedTextures, NativePassRegistry passRegistry) {
        this.context = context;
        this.targetRegistry = targetRegistry;
        this.publishedTextures = publishedTextures;
        this.passRegistry = passRegistry;
        this.targetAllocator = new NamedRenderTargetAllocator(context, targetRegistry, publishedTextures);
    }

    /** Records every compiled native pass scheduled at {@code stage}; returns how many passes were recorded. */
    int executeStage(NativePassRegistry.PassStage stage, VkCommandBuffer commandBuffer,
                     int frameWidth, int frameHeight, long frameSerial, long[] bindCache) {
        if (!this.passRegistry.hasPassesFor(stage)) {
            return 0;
        }
        this.targetAllocator.ensureAllocated(frameWidth, frameHeight);
        if (this.context == null || this.context.device() == null || commandBuffer == null) {
            for (NativePassRegistry.RegisteredPass pass : this.passRegistry.passesFor(stage)) {
                this.passRegistry.noteExecutionRefusal(pass.spec().name(), "Vulkan command buffer unavailable");
            }
            return 0;
        }

        int executed = 0;
        for (NativePassRegistry.RegisteredPass pass : this.passRegistry.passesFor(stage)) {
            if (!pass.compiled()) {
                this.passRegistry.noteExecutionRefusal(pass.spec().name(),
                        "shader compile failed: " + pass.compileFailure());
                continue;
            }
            if (recordPass(pass, commandBuffer, frameSerial, bindCache)) {
                executed++;
            }
        }
        return executed;
    }

    /**
     * CPU-only verification path: exercises compile-refusal skipping and target resolution without requiring a live
     * {@code VkCommandBuffer} or {@code VkDevice}.
     */
    int dryRunStageForTest(NativePassRegistry.PassStage stage) {
        if (!this.passRegistry.hasPassesFor(stage)) {
            return 0;
        }
        this.publishedTextures.syncNamedTargets(this.targetRegistry);
        int executed = 0;
        for (NativePassRegistry.RegisteredPass pass : this.passRegistry.passesFor(stage)) {
            if (!pass.compiled()) {
                this.passRegistry.noteExecutionRefusal(pass.spec().name(),
                        "shader compile failed: " + pass.compileFailure());
                continue;
            }
            if (resolveInputs(pass) == null || resolveOutput(pass) == null) {
                continue;
            }
            this.passRegistry.noteExecution();
            executed++;
        }
        return executed;
    }

    private PublishedFrameTextures.NamedTargetTexture[] resolveInputs(NativePassRegistry.RegisteredPass pass) {
        List<String> names = pass.spec().inputTargets();
        PublishedFrameTextures.NamedTargetTexture[] resolved =
                new PublishedFrameTextures.NamedTargetTexture[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String inputName = names.get(i);
            PublishedFrameTextures.NamedTargetTexture tex = this.publishedTextures.publishedNamed(inputName);
            if (tex == null || tex.image() == 0L || tex.view() == 0L || tex.sampler() == 0L) {
                this.passRegistry.noteExecutionRefusal(pass.spec().name(),
                        "input target '" + inputName + "' has no allocated image");
                return null;
            }
            resolved[i] = tex;
        }
        return resolved;
    }

    private PublishedFrameTextures.NamedTargetTexture resolveOutput(NativePassRegistry.RegisteredPass pass) {
        String outputName = pass.spec().outputTarget();
        PublishedFrameTextures.NamedTargetTexture out = this.publishedTextures.publishedNamed(outputName);
        if (out == null || out.image() == 0L || out.view() == 0L) {
            this.passRegistry.noteExecutionRefusal(pass.spec().name(),
                    "output target '" + outputName + "' has no allocated image");
            return null;
        }
        return out;
    }

    private boolean recordPass(NativePassRegistry.RegisteredPass pass, VkCommandBuffer commandBuffer,
                               long frameSerial, long[] bindCache) {
        PublishedFrameTextures.NamedTargetTexture[] inputs = resolveInputs(pass);
        if (inputs == null) {
            return false;
        }
        PublishedFrameTextures.NamedTargetTexture output = resolveOutput(pass);
        if (output == null) {
            return false;
        }

        RenderTargetRegistry.NamedTargetSpec outputSpec =
                this.targetRegistry.namedTarget(pass.spec().outputTarget());
        RenderTargetRegistry.NamedTargetState outputState =
                this.targetRegistry.namedTargetState(pass.spec().outputTarget());
        boolean shouldClear = shouldClearOutput(outputSpec, outputState, frameSerial);

        try {
            long renderPass = ensureRenderPass(output.vkFormat(), output.depth(), shouldClear);
            long framebuffer = ensureFramebuffer(output.name(), output.view(), renderPass,
                    output.width(), output.height());
            VulkanModProgramPipeline pipeline = ensurePipeline(pass, inputs.length, renderPass);

            long samplerSet = 0L;
            if (inputs.length > 0) {
                long[] views = new long[inputs.length];
                long[] samplers = new long[inputs.length];
                int[] layouts = new int[inputs.length];
                long[] generations = new long[inputs.length];
                for (int i = 0; i < inputs.length; i++) {
                    int sampledLayout = inputs[i].depth()
                            ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
                            : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                    transitionImageLayout(commandBuffer, inputs[i].image(), inputs[i].layout(), sampledLayout,
                            inputs[i].depth());
                    this.targetRegistry.noteNamedTargetLayout(inputs[i].name(), sampledLayout);
                    views[i] = inputs[i].view();
                    samplers[i] = inputs[i].sampler();
                    layouts[i] = sampledLayout;
                    generations[i] = inputs[i].generation();
                }
                samplerSet = pipeline.samplerSetForHandles(views, samplers, layouts, generations);
                if (samplerSet == 0L) {
                    this.passRegistry.noteExecutionRefusal(pass.spec().name(), "failed to allocate sampler descriptor set");
                    return false;
                }
            }

            int attachmentLayout = output.depth()
                    ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                    : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            transitionImageLayout(commandBuffer, output.image(), output.layout(), attachmentLayout, output.depth());

            ensureQuadMesh();
            ByteBuffer uniforms = pass.spec().state().pushBytes() > 0
                    ? ByteBuffer.allocateDirect(pass.spec().state().pushBytes())
                            .order(java.nio.ByteOrder.nativeOrder())
                    : null;
            VulkanModProgramPipeline.Draw draw = pipeline.createDraw(uniforms, samplerSet,
                    "nativePass=" + pass.spec().name(), null);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                        .renderPass(renderPass)
                        .framebuffer(framebuffer);
                begin.renderArea().offset().set(0, 0);
                begin.renderArea().extent().set(output.width(), output.height());
                if (shouldClear && outputSpec != null) {
                    VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
                    if (output.depth()) {
                        clearValues.get(0).depthStencil().set(outputSpec.clearDepth(), 0);
                    } else {
                        clearValues.get(0).color().float32()
                                .put(0, outputSpec.clearRed())
                                .put(1, outputSpec.clearGreen())
                                .put(2, outputSpec.clearBlue())
                                .put(3, outputSpec.clearAlpha());
                    }
                    begin.pClearValues(clearValues);
                }

                vkCmdBeginRenderPass(commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
                viewport.get(0).set(0.0f, 0.0f, output.width(), output.height(), 0.0f, 1.0f);
                vkCmdSetViewport(commandBuffer, 0, viewport);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.get(0).offset().set(0, 0);
                scissor.get(0).extent().set(output.width(), output.height());
                vkCmdSetScissor(commandBuffer, 0, scissor);

                long[] activeBindCache = bindCache != null ? bindCache : new long[6];
                draw.draw(commandBuffer, this.quadMesh, activeBindCache);
                vkCmdEndRenderPass(commandBuffer);
            }

            this.targetRegistry.noteNamedTargetLayout(output.name(), attachmentLayout);
            if (shouldClear) {
                this.targetRegistry.noteNamedTargetCleared(output.name(), frameSerial);
            }
            this.publishedTextures.syncNamedTargets(this.targetRegistry);
            this.passRegistry.noteExecution();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            this.passRegistry.noteExecutionRefusal(pass.spec().name(),
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            return false;
        }
    }

    private static boolean shouldClearOutput(RenderTargetRegistry.NamedTargetSpec spec,
                                             RenderTargetRegistry.NamedTargetState state,
                                             long frameSerial) {
        if (spec == null) {
            return false;
        }
        return switch (spec.clearPolicy()) {
            case CLEAR_EACH_FRAME -> true;
            case CLEAR_ON_CREATE -> state == null || state.clearedFrame() < 0L;
            case DONT_CARE -> false;
        };
    }

    private VulkanModProgramPipeline ensurePipeline(NativePassRegistry.RegisteredPass pass, int samplerCount,
                                                    long renderPass) {
        PipelineKey key = new PipelineKey(pass.spec().name(), pass.vertexSpirv(), pass.fragmentSpirv(), renderPass);
        VulkanModProgramPipeline existing = this.pipelines.get(key);
        if (existing != null) {
            return existing;
        }
        NativePassRegistry.PassState state = pass.spec().state();
        VulkanModProgramPipeline built = VulkanModProgramPipeline.createForNativePass(
                this.context,
                pass.vertexSpirv(),
                pass.fragmentSpirv(),
                samplerCount,
                state.pushBytes(),
                state.colorBlend(),
                state.depthTest(),
                state.depthWrite(),
                state.depthCompareOp(),
                state.cull(),
                renderPass);
        this.pipelines.put(key, built);
        return built;
    }

    private long ensureRenderPass(int vkFormat, boolean depth, boolean clear) {
        RenderPassKey key = new RenderPassKey(vkFormat, depth, clear);
        Long existing = this.renderPasses.get(key);
        if (existing != null) {
            return existing.longValue();
        }
        int attachmentLayout = depth
                ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachment = VkAttachmentDescription.calloc(1, stack);
            attachment.get(0)
                    .format(vkFormat)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(clear ? VK_ATTACHMENT_LOAD_OP_CLEAR
                            : (depth ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_DONT_CARE))
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(attachmentLayout)
                    .finalLayout(attachmentLayout);

            VkAttachmentReference.Buffer ref = VkAttachmentReference.calloc(1, stack);
            ref.get(0).attachment(0).layout(attachmentLayout);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            if (depth) {
                subpass.get(0).pDepthStencilAttachment(ref.get(0));
            } else {
                subpass.get(0).colorAttachmentCount(1).pColorAttachments(ref);
            }

            VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                    .pAttachments(attachment)
                    .pSubpasses(subpass);
            LongBuffer handle = stack.mallocLong(1);
            int res = vkCreateRenderPass(this.context.device(), info, null, handle);
            if (res != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateRenderPass(native pass) failed with VkResult " + res);
            }
            long created = handle.get(0);
            this.renderPasses.put(key, Long.valueOf(created));
            return created;
        }
    }

    private long ensureFramebuffer(String targetName, long view, long renderPass, int width, int height) {
        FramebufferKey key = new FramebufferKey(targetName, view, renderPass, width, height);
        Long existing = this.framebuffers.get(key);
        if (existing != null) {
            return existing.longValue();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack).sType$Default()
                    .renderPass(renderPass)
                    .pAttachments(stack.longs(view))
                    .width(width)
                    .height(height)
                    .layers(1);
            LongBuffer handle = stack.mallocLong(1);
            int res = vkCreateFramebuffer(this.context.device(), info, null, handle);
            if (res != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateFramebuffer(native pass) failed with VkResult " + res);
            }
            long created = handle.get(0);
            this.framebuffers.put(key, Long.valueOf(created));
            return created;
        }
    }

    private void ensureQuadMesh() {
        if (this.quadMesh != null) {
            return;
        }
        VulkanDynamicMesh mesh = new VulkanDynamicMesh(this.context);
        mesh.begin(FULLSCREEN_QUAD_LAYOUT);
        int v0 = putQuadVertex(mesh, -1.0f, -1.0f, 0.0f, 0.0f);
        int v1 = putQuadVertex(mesh, 1.0f, -1.0f, 1.0f, 0.0f);
        int v2 = putQuadVertex(mesh, 1.0f, 1.0f, 1.0f, 1.0f);
        int v3 = putQuadVertex(mesh, -1.0f, 1.0f, 0.0f, 1.0f);
        mesh.index(v0);
        mesh.index(v1);
        mesh.index(v2);
        mesh.index(v0);
        mesh.index(v2);
        mesh.index(v3);
        this.quadMesh = mesh;
    }

    private static int putQuadVertex(VulkanDynamicMesh mesh, float x, float y, float u, float v) {
        int index = mesh.nextVertex();
        mesh.putPosition(x, y, 0.0f);
        mesh.putUv(u, v);
        return index;
    }

    private static void transitionImageLayout(VkCommandBuffer commandBuffer, long image, int oldLayout, int newLayout,
                                              boolean depth) {
        if (oldLayout == newLayout) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(oldLayout == VK_IMAGE_LAYOUT_UNDEFINED ? 0
                            : (depth ? VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                                    : VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) | VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                            || newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
                                    ? VK_ACCESS_SHADER_READ_BIT
                                    : (depth ? VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                                            : VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                                                    | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT));
            barrier.get(0).subresourceRange()
                    .aspectMask(depth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            int srcStage = oldLayout == VK_IMAGE_LAYOUT_UNDEFINED
                    ? VK_PIPELINE_STAGE_ALL_COMMANDS_BIT
                    : (depth ? VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT
                            : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            int dstStage = (newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                    || newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                            ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                            : (depth ? VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                                    : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            vkCmdPipelineBarrier(commandBuffer, srcStage, dstStage, 0, null, null, barrier);
        }
    }

    void delete() {
        for (VulkanModProgramPipeline pipeline : this.pipelines.values()) {
            if (pipeline != null) {
                pipeline.delete();
            }
        }
        this.pipelines.clear();
        if (this.context != null && this.context.device() != null) {
            VkDevice device = this.context.device();
            for (Long fb : this.framebuffers.values()) {
                if (fb != null && fb.longValue() != VK_NULL_HANDLE) {
                    vkDestroyFramebuffer(device, fb.longValue(), null);
                }
            }
            for (Long rp : this.renderPasses.values()) {
                if (rp != null && rp.longValue() != VK_NULL_HANDLE) {
                    vkDestroyRenderPass(device, rp.longValue(), null);
                }
            }
        }
        this.framebuffers.clear();
        this.renderPasses.clear();
        if (this.quadMesh != null) {
            this.quadMesh.delete();
            this.quadMesh = null;
        }
        this.targetAllocator.delete();
    }
}
