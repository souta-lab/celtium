package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

/** Records registered native fullscreen passes into the frame command stream. */
final class PassExecutor {

    private record PipelineKey(String passName, byte[] vertexSpirv, byte[] fragmentSpirv, int format, boolean depth) {
        @Override
        public boolean equals(Object other) {
            return other instanceof PipelineKey k
                    && this.passName.equals(k.passName)
                    && this.vertexSpirv == k.vertexSpirv
                    && this.fragmentSpirv == k.fragmentSpirv
                    && this.format == k.format && this.depth == k.depth;
        }

        @Override
        public int hashCode() {
            int h = this.passName.hashCode();
            h = 31 * h + System.identityHashCode(this.vertexSpirv);
            h = 31 * h + System.identityHashCode(this.fragmentSpirv);
            return 31 * (31 * h + this.format) + Boolean.hashCode(this.depth);
        }
    }

    private static final VulkanDynamicMesh.Layout FULLSCREEN_QUAD_LAYOUT =
            new VulkanDynamicMesh.Layout(VulkanImmediateTextureRecordsPipeline.POSITION_TEX_BYTES, 0, 12, -1, -1, -1);

    private final VulkanContext context;
    private final RenderTargetRegistry targetRegistry;
    private final PublishedFrameTextures publishedTextures;
    private final NativePassRegistry passRegistry;
    private final NamedRenderTargetAllocator targetAllocator;

    private final Map<PipelineKey, FullscreenPassPipeline> pipelines = new HashMap<>();
    private VulkanDynamicMesh quadMesh;
    private ByteBuffer scratchZeroUniforms;

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
        int attachmentLayout = output.depth()
                ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        // Failure bookkeeping for the catch below: whether the output's transition barrier has been
        // recorded (its layout note is still owed) and whether the render pass is open
        // (audit-vulkan-core E1).
        boolean outputTransitionRecorded = false;
        boolean passOpen = false;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FullscreenPassPipeline pipeline = ensurePipeline(pass, inputs.length, output.vkFormat(), output.depth());
            var images = new FullscreenPassPipeline.Image[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                var input = inputs[i];
                if (input.image() == output.image()) {
                    this.passRegistry.noteExecutionRefusal(pass.spec().name(), "input aliases output");
                    return false;
                }
                int sampledLayout = input.depth() ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
                        : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                images[i] = new FullscreenPassPipeline.Image(input.view(), input.sampler(), sampledLayout);
            }
            long samplerSet = pipeline.descriptorSet(stack, images, 0, 0, 0);
            ensureQuadMesh();
            int pushBytes = pass.spec().state().pushBytes();
            ByteBuffer uniforms = pushBytes > 0 ? zeroUniforms(pushBytes) : null;
            for (int i = 0; i < inputs.length; i++) {
                var input = inputs[i];
                int sampledLayout = images[i].layout();
                FullscreenPassPipeline.transition(commandBuffer, stack, input.image(),
                        input.depth() ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT,
                        input.layout(), sampledLayout,
                        VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_SHADER_SAMPLED_READ_BIT);
                this.targetRegistry.noteNamedTargetLayout(commandBuffer.address(), input.name(), sampledLayout);
            }
            FullscreenPassPipeline.transition(commandBuffer, stack, output.image(),
                    output.depth() ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT,
                    output.layout(), attachmentLayout,
                    output.depth() ? VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT
                            : VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    output.depth() ? VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                            : VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT);
            outputTransitionRecorded = true;

            var attachments = VkRenderingAttachmentInfo.calloc(1, stack);
            var attachment = attachments.get(0).sType$Default().imageView(output.view()).imageLayout(attachmentLayout)
                    .loadOp(shouldClear ? VK_ATTACHMENT_LOAD_OP_CLEAR
                            : (output.depth() ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_DONT_CARE))
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            if (shouldClear && outputSpec != null) {
                if (output.depth()) attachment.clearValue().depthStencil().set(outputSpec.clearDepth(), 0);
                else attachment.clearValue().color().float32(stack.floats(outputSpec.clearRed(),
                        outputSpec.clearGreen(), outputSpec.clearBlue(), outputSpec.clearAlpha()));
            }
            var rendering = VkRenderingInfo.calloc(stack).sType$Default().layerCount(1);
            rendering.renderArea().extent().set(output.width(), output.height());
            if (output.depth()) rendering.pDepthAttachment(attachment);
            else rendering.pColorAttachments(attachments);

            BackendCallCounters.noteFboSwitch();
            vkCmdBeginRendering(commandBuffer, rendering);
            passOpen = true;
            FullscreenPassPipeline.viewport(commandBuffer, stack, output.width(), output.height());
            if (bindCache != null) java.util.Arrays.fill(bindCache, 0L);
            pipeline.draw(commandBuffer, stack, this.quadMesh, uniforms, samplerSet);
            vkCmdEndRendering(commandBuffer);
            passOpen = false;

            this.targetRegistry.noteNamedTargetLayout(commandBuffer.address(), output.name(), attachmentLayout);
            if (shouldClear) {
                this.targetRegistry.noteNamedTargetCleared(commandBuffer.address(), output.name(), frameSerial);
            }
            this.publishedTextures.syncNamedTargets(this.targetRegistry);
            this.passRegistry.noteExecution();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            // A failure between begin and end would otherwise leave the frame's command buffer inside
            // a render pass, poisoning everything recorded into it afterwards (audit-vulkan-core E1).
            // Close the pass so the buffer stays usable. The output's transition barrier was recorded
            // before the pass began, so note its layout as the success path does - the registry must
            // agree with what the recording says.
            if (passOpen) {
                try {
                    vkCmdEndRendering(commandBuffer);
                } catch (RuntimeException | LinkageError ignored) {
                    // Already in failure handling; a second failure here changes nothing.
                }
            }
            if (outputTransitionRecorded) {
                this.targetRegistry.noteNamedTargetLayout(commandBuffer.address(), output.name(), attachmentLayout);
            }
            if (passOpen && shouldClear) {
                // The pass began, so its clear load operation is in the recording as well; note the clear
                // as the success path does - as a pending the submission's fate settles, never as fact.
                this.targetRegistry.noteNamedTargetCleared(commandBuffer.address(), output.name(), frameSerial);
            }
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
            // The recorded head, not the confirmed frame: a pass recorded later in the same recording
            // must see an earlier pass's clear, while a clear that sat in an abandoned recording must
            // not suppress this frame's clear (lanechat #1596).
            case CLEAR_ON_CREATE -> state == null || state.recordedClearedFrame() < 0L;
            case DONT_CARE -> false;
        };
    }

    private FullscreenPassPipeline ensurePipeline(NativePassRegistry.RegisteredPass pass, int samplerCount,
                                                  int format, boolean depth) {
        PipelineKey key = new PipelineKey(pass.spec().name(), pass.vertexSpirv(), pass.fragmentSpirv(), format, depth);
        NativePassRegistry.PassState state = pass.spec().state();
        VulkanColorBlendState blend = state.colorBlend() == null ? VulkanColorBlendState.legacy() : state.colorBlend();
        return this.pipelines.computeIfAbsent(key, ignored -> new FullscreenPassPipeline(
                this.context, pass.vertexSpirv(), pass.fragmentSpirv(), samplerCount, state.pushBytes(), false, true,
                depth ? VK_FORMAT_UNDEFINED : format, depth ? format : VK_FORMAT_UNDEFINED, blend,
                state.depthTest(), state.depthWrite(), state.depthCompareOp(), state.cull()));
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

    private ByteBuffer zeroUniforms(int pushBytes) {
        ByteBuffer buf = this.scratchZeroUniforms;
        if (buf == null || buf.capacity() < pushBytes) {
            buf = ByteBuffer.allocateDirect(Math.max(128, pushBytes)).order(java.nio.ByteOrder.nativeOrder());
            this.scratchZeroUniforms = buf;
        }
        buf.position(0).limit(pushBytes);
        return buf;
    }

    void delete() {
        this.pipelines.values().forEach(FullscreenPassPipeline::delete);
        this.pipelines.clear();
        if (this.quadMesh != null) {
            this.quadMesh.delete();
            this.quadMesh = null;
        }
        this.targetAllocator.delete();
    }
}
