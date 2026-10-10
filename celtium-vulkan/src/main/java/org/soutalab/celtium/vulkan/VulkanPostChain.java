package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

/**
 * Executes the vanilla post-process chains natively.
 * <p>
 * A {@code ShaderGroup.render} is a list of fullscreen-quad passes: each samples one framebuffer (the group's
 * {@code minecraft:main} target or a named chain target), writes another and binds the program's uniforms. When the
 * group's main framebuffer is the game's own, the chain runs here inside the same GL-to-Vulkan handoff the terrain
 * passes use - the shared image is Vulkan-owned in that window, so the first pass can sample the frame GL just
 * produced and the last pass can write the result back before the game keeps compositing (HUD included).
 * <p>
 * Chain targets are pure-Vulkan images keyed by the framebuffer object's identity ({@code identityHashCode} - the
 * ids are only ever matched for equality, and identity stays unique where a GL object name would not); the game's
 * framebuffer resolves to the shared image. A target may instead be an image the caller owns, adopted under the same
 * identity: the passes then sample and write that image directly and this class never creates or destroys it, only the
 * framebuffer a writing pass needs - the adopted image arrives already in {@code GENERAL} with its content live, so
 * nothing here moves it from {@code UNDEFINED}. That is the entity-outline chain's whole case: the image adopted under
 * the outline framebuffer's identity is the one the renderer kept for the silhouette the game drew while that
 * framebuffer was bound, so the chain's first pass reads the silhouette and its last pass puts the finished glow back
 * into the same image - the one the game's composite samples by texture name afterwards. The
 * uniform block the mixin packs in declaration order goes in whole as push constants - every program's block fits in
 * 128 bytes except color_convolve (180) and spider (140), so a device whose {@code maxPushConstantsSize} is the
 * 128-byte minimum keeps those chains on GL.
 */
public final class VulkanPostChain {

    /** One collected pass, handed over by the mixin with everything already resolved to plain values. */
    public static final class Pass {
        public String program;
        public int inFbId;
        public int inW;
        public int inH;
        public int outFbId;
        public int outW;
        public int outH;
        public boolean inIsMain;
        public boolean outIsMain;
        public int[] auxFbIds = new int[0];
        public int[] auxW = new int[0];
        public int[] auxH = new int[0];
        public float[] push = new float[0];
        public int pushBytes;
        /**
         * Nonzero when the chain's input framebuffer is not the game's own - the entity-outline case, whose chain
         * reads and writes the target the game filled with the silhouette: the identity its passes carry, the GL
         * texture name the game binds again to composite it, and its size, so the caller can adopt the image it
         * already keeps under that identity instead of letting the chain allocate an empty target for it. Only
         * pass[0] carries it; zero marks the game's own framebuffer, which resolves to the shared image.
         */
        public int groupMainTextureId;
        public int groupMainFbId;
        public int groupMainW;
        public int groupMainH;
    }

    /** program name -> (vertex module, fragment module); names are program JSON ids without path/extension. */
    private static final Map<String, String[]> PROGRAMS = new HashMap<>();
    static {
        PROGRAMS.put("invert", new String[] { "post_blit.vert", "post_invert.frag" });
        PROGRAMS.put("blit", new String[] { "post_blit.vert", "post_blit.frag" });
        PROGRAMS.put("blur", new String[] { "post_sobel.vert", "post_blur.frag" });
        PROGRAMS.put("bits", new String[] { "post_sobel.vert", "post_bits.frag" });
        PROGRAMS.put("entity_outline", new String[] { "post_sobel.vert", "post_entity_sobel.frag" });
        PROGRAMS.put("color_convolve", new String[] { "post_sobel.vert", "post_color_convolve.frag" });
        PROGRAMS.put("spider", new String[] { "post_rotscale.vert", "post_spiderclip.frag" });
    }

    private final VulkanContext context;
    private final FrameImageTarget frameTarget;
    private final long renderPass;
    private final long setLayout;
    private final long descriptorPool;
    private final long sampler;
    private final int maxPushConstants;
    /** Every chain target, allocated here or adopted from the caller, keyed by the identity the passes carry. */
    private final Map<Integer, Target> targets = new HashMap<>();
    /**
     * Targets that an adoption or a release replaced. They are freed only in {@link #delete()}, after the device
     * wait: a command buffer that already recorded one may still be in flight.
     */
    private final List<Target> retiredTargets = new ArrayList<>();
    private final Map<String, Program> programs = new HashMap<>();
    private long mainFramebuffer;
    private long mainFramebufferImage;
    private int mainFramebufferWidth;
    private int mainFramebufferHeight;
    private int passesRecorded;
    private int chainsRun;
    /**
     * Chain runs whose caller reported an image to adopt ({@link Pass#groupMainFbId}) and had not adopted one under
     * that identity: the passes then ran on an empty target this class allocated, which is the difference between
     * "the chain ran" and "the chain ran on the image the game drew into". Zero with a caller that adopts.
     */
    private int runsOnAllocatedStandIn;
    /** One-shot: the same fact printed with the identity, texture and size the caller reported. */
    private boolean standInReported;
    /** How many adopted targets have been handed to this chain over its life; reported by {@link #status()}. */
    private int adoptions;
    /**
     * Targets whose one-time {@code UNDEFINED -> GENERAL} barrier is in the recording being recorded right now.
     * <p>
     * The flag that says "this image is in {@code GENERAL}" ({@link Target#initialized}) is set for them by
     * {@link #commitPasses()} alone, which the caller calls after the submission that carried the barrier reached the
     * queue. A recording that is reset or abandoned therefore leaves every target it touched uninitialized, and the
     * next recording carries their barriers again - which is the whole invariant: an image is never sampled before
     * the barrier that moves it out of {@code UNDEFINED} is recorded into the command buffer that draws, and the flag
     * that says so belongs to a submission that really happened.
     */
    private final Set<Target> pendingInit = new LinkedHashSet<>();
    /** Recordings that carried a first-use barrier, counted by {@link #initBarriers} and {@link #commits}. */
    private int initBarriers;
    private int commits;
    /** Recordings that carried a first-use barrier and were never reported as submitted before the next one began. */
    private int uncommittedRecordings;
    /** How many targets were recorded a barrier again after an earlier recording of theirs was not submitted. */
    private int repeatInits;
    /** Bounded trace budget: {@link #trace} prints at most this many lines over the chain's whole life. */
    private static final int TRACE_LIMIT = 240;
    private int traceLines;
    /** One-shot flag: the trace says once that it stopped printing rather than going silent for no stated reason. */
    private boolean traceLimitReported;
    /** Recordings recorded so far; the trace numbers them so a barrier and the draw that follows can be paired. */
    private int recordings;

    /** Persistent descriptor sets, handed out round-robin: one set per pass, never reused inside one command
     * buffer, so updating one is legal whenever the buffer that last bound it has completed. */
    private final long[] setRing = new long[32];
    private int setCursor;

    private VulkanPostChain(VulkanContext context, FrameImageTarget frameTarget, long renderPass, long setLayout,
            long descriptorPool, long sampler, int maxPushConstants) {
        this.context = context;
        this.frameTarget = frameTarget;
        this.renderPass = renderPass;
        this.setLayout = setLayout;
        this.descriptorPool = descriptorPool;
        this.sampler = sampler;
        this.maxPushConstants = maxPushConstants;
    }

    public static VulkanPostChain create(VulkanContext context, FrameImageTarget frameTarget) {
        if (context == null || frameTarget == null) {
            throw new IllegalArgumentException("context and frame target must be set");
        }
        System.out.println("[Celtium] CELTIUM_POSTCHAIN create: a chain reached the native executor on this backend"
                + " (device=0x" + Long.toHexString(context.device().address()) + ", frameImage=0x"
                + Long.toHexString(frameTarget.colorImage()) + ")");
        VkDevice device = context.device();
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            int maxPush = properties.limits().maxPushConstantsSize();

            // One colour attachment, kept in GENERAL like every other image this renderer owns; each pass writes
            // every pixel of its target, so the load op is dont-care rather than a clear.
            VkAttachmentDescription.Buffer attachment = VkAttachmentDescription.calloc(1, stack);
            attachment.get(0).format(VK_FORMAT_R8G8B8A8_UNORM)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .finalLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack);
            colorRef.get(0).attachment(0).layout(VK_IMAGE_LAYOUT_GENERAL);
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1).pColorAttachments(colorRef);
            VkRenderPassCreateInfo passInfo = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                    .pAttachments(attachment).pSubpasses(subpass);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateRenderPass(device, passInfo, null, handle), "vkCreateRenderPass(post chain)");
            long renderPass = handle.get(0);

            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
            bindings.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            bindings.get(1).binding(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                    .pBindings(bindings);
            check(vkCreateDescriptorSetLayout(device, layoutInfo, null, handle),
                    "vkCreateDescriptorSetLayout(post chain)");
            long setLayout = handle.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(64);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(32).pPoolSizes(poolSizes);
            check(vkCreateDescriptorPool(device, poolInfo, null, handle),
                    "vkCreateDescriptorPool(post chain)");
            long descriptorPool = handle.get(0);

            // The chain's targets and the shared image are GL framebuffer textures: NEAREST, clamp to edge.
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK_FILTER_NEAREST).minFilter(VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .mipLodBias(0.0f).minLod(0.0f).maxLod(0.0f);
            check(vkCreateSampler(device, samplerInfo, null, handle), "vkCreateSampler(post chain)");
            long sampler = handle.get(0);
            VulkanPostChain chain = new VulkanPostChain(context, frameTarget, renderPass, setLayout,
                    descriptorPool, sampler, maxPush);
            // The set ring is allocated once: each pass takes the next set, so a set is only ever bound and then
            // updated again after the command buffer that bound it has long completed - the pattern the spec
            // allows without UPDATE_AFTER_BIND.
            for (int i = 0; i < chain.setRing.length; ++i) {
                LongBuffer layouts = stack.longs(setLayout);
                VkDescriptorSetAllocateInfo alloc = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                        .descriptorPool(descriptorPool).pSetLayouts(layouts);
                check(vkAllocateDescriptorSets(device, alloc, handle), "vkAllocateDescriptorSets(post ring)");
                chain.setRing[i] = handle.get(0);
            }
            return chain;
        }
    }

    /** {@return whether every program the pass list uses has a native pipeline within the push limit} */
    public boolean supportsAll(List<Pass> passes) {
        if (passes.isEmpty()) {
            return false;
        }
        for (Pass pass : passes) {
            if (!PROGRAMS.containsKey(pass.program) || pass.pushBytes <= 0
                    || pass.pushBytes > this.maxPushConstants) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adopts an image this chain does not own as the target for an identity the passes carry: later passes sample and
     * write that image instead of one allocated here. Nothing about the image, its memory or its view is created or
     * destroyed by this class - only the framebuffer a writing pass needs.
     * <p>
     * The image belongs to the caller, so its layout does too: an adopted image is in {@code GENERAL} and its content
     * is live (the entity-outline caller hands over the image the silhouette was just drawn into), and this class
     * records no {@code UNDEFINED -> GENERAL} transition for it - that transition discards content, which for an
     * image that stands for a framebuffer the game already filled is exactly the silhouette. A caller whose image is
     * still undefined must move it to {@code GENERAL} itself before adopting it.
     * <p>
     * Re-adopting the same image under the same identity is a no-op: the caller reports the association every time
     * the chain runs, and a fresh target each time would leak one framebuffer per frame.
     * <p>
     * The identity is the chain's own key - the {@code identityHashCode} of the framebuffer object, the same value the
     * passes carry - so a value outside the int range can never match a pass and is rejected here.
     */
    public void adoptTarget(long identity, long image, long view, int width, int height) {
        Integer key = adoptedKey(identity);
        if (image == VK_NULL_HANDLE || view == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("adopted post chain target " + identity
                    + " needs an image and a view");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("adopted post chain target " + identity + " needs a size, got "
                    + width + "x" + height);
        }
        Target existing = this.targets.get(key);
        if (existing != null && !existing.owned && existing.image == image && existing.view == view
                && existing.width == width && existing.height == height) {
            return;
        }
        Target adopted = Target.adopt(key.intValue(), width, height, image, view);
        Target previous = this.targets.put(key, adopted);
        if (previous != null) {
            // Whatever this identity resolved to before may already be recorded in a buffer still in flight.
            this.retiredTargets.add(previous);
        }
        this.adoptions++;
        trace("adopt identity=" + adopted.identity + " image=0x" + hex(image) + " view=0x" + hex(view) + " "
                + width + "x" + height + " replaced="
                + (previous == null ? "none" : (previous.owned ? "owned" : "adopted") + "(image=0x"
                        + hex(previous.image) + ",initialized=" + previous.initialized + ")")
                + " - the caller declares this image live in GENERAL, so no UNDEFINED -> GENERAL move is recorded"
                + " for it here");
    }

    /**
     * Stops resolving {@code identity} to an adopted image; the image itself stays the caller's. Releasing an identity
     * this chain allocated, or one it never adopted, does nothing.
     */    public void releaseAdoptedTarget(long identity) {
        Integer key = adoptedKey(identity);
        Target target = this.targets.get(key);
        if (target == null || target.owned) {
            return;
        }
        this.targets.remove(key);
        this.retiredTargets.add(target);
    }

    /** {@return the chain's key for an adopted identity}; the passes carry int keys and a wider one cannot match. */
    private static Integer adoptedKey(long identity) {
        if ((long) (int) identity != identity) {
            throw new IllegalArgumentException("adopted post chain target identity is outside the chain's key range: "
                    + identity);
        }
        return Integer.valueOf((int) identity);
    }

    /**
     * Records every pass into the caller's command buffer. A pass whose input or output is the group's main
     * framebuffer samples or writes the shared frame image, which the caller must already have acquired; every other
     * framebuffer resolves to a chain target - one allocated here, or the image the caller adopted for its identity.
     * <p>
     * Every target this recording uses for the first time gets its {@code UNDEFINED -> GENERAL} barrier recorded into
     * this same command buffer, immediately before the draw that samples it. The targets are also remembered in
     * {@link #pendingInit} until {@link #commitPasses()} reports the submission that carried them reached the queue:
     * a recording that is reset or abandoned therefore leaves them uninitialized, and the next recording carries
     * their barriers again instead of sampling an image that is still {@code UNDEFINED}.
     */
    public void recordPasses(VkCommandBuffer commandBuffer, List<Pass> passes) {
        checkStandInTarget(passes);
        if (!this.pendingInit.isEmpty()) {
            // Not a second recording inside one command buffer: a non-empty set means the recording that carried
            // these targets' first-use barrier ended without commitPasses(), so that recording never reached the
            // queue and the images it moved are still UNDEFINED. The barriers are recorded again below.
            this.uncommittedRecordings++;
            this.repeatInits += this.pendingInit.size();
            if (this.uncommittedRecordings <= 4) {
                for (Target target : this.pendingInit) {
                    trace("UNCOMMITTED identity=" + target.identity + " image=0x" + hex(target.image)
                            + " recording=" + this.recordings + ": its UNDEFINED -> GENERAL barrier was recorded"
                            + " but never submitted; it is recorded again in this recording");
                }
            }
        }
        this.pendingInit.clear();
        this.recordings++;
        List<String> details = new ArrayList<>();
        boolean interesting = false;
        for (Pass pass : passes) {
            Program program = program(pass.program);
            if (program == null || pass.pushBytes > program.pushRange) {
                throw new IllegalStateException("post chain program without a pipeline: " + pass.program);
            }
            Target inTarget = pass.inIsMain ? null : target(pass.inFbId, pass.inW, pass.inH);
            long inView = inTarget != null ? inTarget.view : this.frameTarget.colorView();
            long inImage = inTarget != null ? inTarget.image : this.frameTarget.colorImage();
            // auxFbIds[0] == 0 marks the group's main framebuffer; the same resolution as intarget applies.
            Target auxTarget = pass.auxFbIds.length > 0 && pass.auxFbIds[0] != 0
                    ? target(pass.auxFbIds[0], pass.auxW[0], pass.auxH[0]) : null;
            boolean auxIsMain = pass.auxFbIds.length > 0 && pass.auxFbIds[0] == 0;
            long auxView;
            if (auxTarget != null) {
                auxView = auxTarget.view;
            } else if (auxIsMain) {
                auxView = this.frameTarget.colorView();
            } else {
                auxView = inView;
            }
            if (auxIsMain) {
                shaderReadBarrier(commandBuffer, this.frameTarget.colorImage());
            }
            Target outTarget = pass.outIsMain ? null : target(pass.outFbId, pass.outW, pass.outH);
            // An adopted target has no framebuffer until a pass writes it; that is where its view gets one.
            long outFramebuffer = pass.outIsMain
                    ? mainFramebuffer(pass.outW, pass.outH)
                    : outTarget.framebuffer(this.context, this.renderPass);

            // A target that was just created is still UNDEFINED: bring it to GENERAL exactly once, before any use.
            // An adopted target is never in that state - its caller declares it live and already in GENERAL - so it
            // arrives here initialized and its content survives the first use.
            interesting |= beginUse(commandBuffer, inTarget) | beginUse(commandBuffer, auxTarget)
                    | beginUse(commandBuffer, outTarget);

            // The previous pass's attachment writes (or the acquired GL frame) must be visible to this sampler.
            shaderReadBarrier(commandBuffer, inImage);
            if (auxTarget != null) {
                shaderReadBarrier(commandBuffer, auxTarget.image);
            }

            if (details.size() < 12) {
                details.add("pass=" + details.size() + " program=" + pass.program
                        + " in=" + describe(inTarget, pass.inIsMain, pass.inW, pass.inH)
                        + " aux=" + describe(auxTarget, auxIsMain, pass.auxFbIds.length > 0 ? pass.auxW[0] : 0,
                                pass.auxFbIds.length > 0 ? pass.auxH[0] : 0)
                        + " out=" + describe(outTarget, pass.outIsMain, pass.outW, pass.outH));
            }

            long descriptorSet = descriptorSet(inView, auxView);
            int outW = pass.outW;
            int outH = pass.outH;
            try (MemoryStack stack = stackPush()) {
                VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                        .renderPass(this.renderPass).framebuffer(outFramebuffer)
                        .renderArea(VkRect2D.calloc(stack)
                                .offset(it -> it.x(0).y(0))
                                .extent(it -> it.width(outW).height(outH)));
                vkCmdBeginRenderPass(commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, program.pipeline);
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
                viewport.get(0).x(0).y(0).width(outW).height(outH).minDepth(0.0f).maxDepth(1.0f);
                vkCmdSetViewport(commandBuffer, 0, viewport);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.get(0).offset(it -> it.x(0).y(0)).extent(it -> it.width(outW).height(outH));
                vkCmdSetScissor(commandBuffer, 0, scissor);
                java.nio.FloatBuffer push = stack.mallocFloat(pass.pushBytes / 4);
                push.put(pass.push, 0, pass.pushBytes / 4).flip();
                vkCmdPushConstants(commandBuffer, program.layout,
                        VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, push);
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, program.layout, 0,
                        stack.longs(descriptorSet), null);
                vkCmdDraw(commandBuffer, 4, 1, 0, 0);
                vkCmdEndRenderPass(commandBuffer);
            }
            this.passesRecorded++;
        }
        // The header and the per-pass lines go out together, and the per-pass detail only for a run that touched a
        // target's first use: a run that reuses targets already in GENERAL has nothing to say about layouts, and
        // printing every pass of every run would spend the whole trace budget before the interesting frame.
        trace("run=" + this.chainsRun + " recording=" + this.recordings + " cmd=0x" + hex(commandBuffer.address())
                + " frame=0x" + hex(this.frameTarget.colorImage()) + " frameView=0x"
                + hex(this.frameTarget.colorView()) + " passes=" + passes.size()
                + " pendingInit=" + this.pendingInit.size());
        if (interesting) {
            for (String detail : details) {
                trace(detail);
            }
        }
        this.chainsRun++;
    }

    /**
     * Reports that the submission which carried the barriers {@link #recordPasses} recorded reached the queue, so
     * every target that needed its {@code UNDEFINED -> GENERAL} move in that recording is now really in
     * {@code GENERAL}.
     * <p>
     * This is the caller's half of the chain's layout invariant and the reason the flag is not set while recording:
     * a command buffer that is reset or abandoned never runs its barriers, so a target whose barrier went into one is
     * not initialized, and the next recording carries the barrier again. Calling this for a recording that did not
     * reach the queue would make the chain believe an image is in {@code GENERAL} while it is still
     * {@code UNDEFINED} - which is exactly the state the flag exists to prevent.
     */
    public void commitPasses() {
        this.commits++;
        for (Target target : this.pendingInit) {
            target.initialized = true;
        }
        this.pendingInit.clear();
    }

    /**
     * Records the one-time {@code UNDEFINED -> GENERAL} move for a target this pass samples or writes, unless the
     * target is already known to be in {@code GENERAL} (its transition was committed) or this recording already
     * carries the barrier ({@link #pendingInit}). The barrier lands in the caller's command buffer, before the draw
     * that reads or writes the image.
     *
     * {@return whether this call is what touched the target's first use in this recording}
     */
    private boolean beginUse(VkCommandBuffer commandBuffer, Target target) {
        if (target == null || target.initialized || !this.pendingInit.add(target)) {
            return false;
        }
        this.initBarriers++;
        target.barriersRecorded++;
        initBarrier(commandBuffer, target.image);
        trace("initBarrier recording=" + this.recordings + " identity=" + target.identity + " image=0x"
                + hex(target.image) + " view=0x" + hex(target.view) + " " + target.width + "x" + target.height
                + " owned=" + target.owned + " barriersForThisTarget=" + target.barriersRecorded);
        return true;
    }

    /** {@return a pass side rendered for the trace}: the shared frame image, or the target and its handles. */
    private static String describe(Target target, boolean isMain, int width, int height) {
        if (target == null) {
            return isMain ? "main(" + width + "x" + height + ")" : "none(" + width + "x" + height + ")";
        }
        return (target.owned ? "owned#" : "adopted#") + target.identity + "(image=0x" + hex(target.image)
                + ",view=0x" + hex(target.view) + "," + target.width + "x" + target.height
                + ",initialized=" + target.initialized + ")";
    }

    /** Prints one bounded trace line, with the address of the image handles the validation layer reports. */
    private void trace(String message) {
        if (this.traceLines >= TRACE_LIMIT) {
            if (!this.traceLimitReported) {
                this.traceLimitReported = true;
                System.out.println("[Celtium] CELTIUM_POSTCHAIN trace limit reached (" + TRACE_LIMIT + " lines)");
            }
            return;
        }
        this.traceLines++;
        System.out.println("[Celtium] CELTIUM_POSTCHAIN " + message);
    }

    private static String hex(long handle) {
        return Long.toHexString(handle);
    }

    /**
     * Counts - and prints once - the case where the caller reported a framebuffer whose image it means to be adopted
     * and no image was adopted under that identity, so the passes are about to run on an empty target allocated here.
     * That is a wiring gap rather than a rendering decision: the chain still runs, but it reads nothing and its output
     * goes nowhere the game can see. The line carries the identity, the GL texture name and the size the caller
     * reported, which is everything an adoption needs.
     */
    private void checkStandInTarget(List<Pass> passes) {
        if (passes.isEmpty() || passes.get(0).groupMainFbId == 0) {
            return;
        }
        Target adopted = this.targets.get(Integer.valueOf(passes.get(0).groupMainFbId));
        if (adopted != null && !adopted.owned) {
            return;
        }
        this.runsOnAllocatedStandIn++;
        if (!this.standInReported) {
            this.standInReported = true;
            Pass first = passes.get(0);
            System.out.println("[Celtium] CELTIUM_POST_ADOPT_REPORTED identity=" + first.groupMainFbId
                    + " texture=" + first.groupMainTextureId + " " + first.groupMainW + "x" + first.groupMainH
                    + " adopted=false (no image under this identity: the chain allocates an empty stand-in)");
        }
    }

    /** {@return a one-line status for the verification line}; adopted targets count separately from allocated ones. */
    public String status() {
        int adopted = 0;
        StringBuilder adoptedIds = new StringBuilder();
        for (Map.Entry<Integer, Target> entry : this.targets.entrySet()) {
            if (!entry.getValue().owned) {
                adopted++;
                // Which identities were adopted, not just how many: an adoption the caller aimed at the wrong
                // framebuffer (a chain's own target among them) is invisible in a count and obvious in the list.
                if (adoptedIds.length() > 0) {
                    adoptedIds.append(',');
                }
                adoptedIds.append(Integer.toHexString(entry.getKey().intValue()));
            }
        }
        return "postChains=" + this.chainsRun + ", postPasses=" + this.passesRecorded
                + ", postTargets=" + (this.targets.size() - adopted) + ", postAdopted=" + adopted
                + ", postAdoptions=" + this.adoptions + ", postPipelines=" + this.programs.size()
                + ", postRunsOnStandIn=" + this.runsOnAllocatedStandIn
                + ", postRecordingInitBarriers=" + this.initBarriers + ", postRecordingCommits=" + this.commits
                + ", postUncommittedRecordings=" + this.uncommittedRecordings
                + ", postRepeatInits=" + this.repeatInits
                + ", postAdoptedIds=[" + adoptedIds + "]";
    }

    private void initBarrier(VkCommandBuffer commandBuffer, long image) {
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image).subresourceRange(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1));
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier);
        }
    }

    private void shaderReadBarrier(VkCommandBuffer commandBuffer, long image) {
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_COLOR_ATTACHMENT_READ_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_GENERAL).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image).subresourceRange(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1));
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);
        }
    }

    /**
     * {@return the chain target for a framebuffer identity}, allocated on first use or the image the caller adopted
     * for that identity. An allocated target whose size changed is replaced; an adopted one whose size disagrees with
     * the pass is refused by message - the caller's image is not this class's to resize, and sampling the wrong one
     * silently is exactly what the refusal is for.
     */
    private Target target(int fbId, int width, int height) {
        Integer key = Integer.valueOf(fbId);
        Target target = this.targets.get(key);
        if (target != null && (target.width != width || target.height != height)) {
            if (!target.owned) {
                throw new IllegalStateException("adopted post chain target " + fbId + " is " + target.width + "x"
                        + target.height + ", the pass asks for " + width + "x" + height);
            }
            target.delete(this.context.device());
            trace("resize identity=" + fbId + " replaced image=0x" + hex(target.image)
                    + " (the replacement starts UNDEFINED and gets its own barrier)");
            target = null;
        }
        if (target == null) {
            target = Target.create(key.intValue(), this.context, this.renderPass, width, height);
            this.targets.put(key, target);
            trace("created identity=" + target.identity + " image=0x" + hex(target.image) + " view=0x"
                    + hex(target.view) + " framebuffer=0x" + hex(target.fb) + " " + width + "x" + height
                    + " owned=true (UNDEFINED until a pass records its barrier)");
        }
        return target;
    }

    private Program program(String name) {
        Program program = this.programs.get(name);
        if (program == null) {
            String[] shaders = PROGRAMS.get(name);
            if (shaders == null) {
                return null;
            }
            program = Program.create(this.context, this.renderPass, this.setLayout, shaders[0], shaders[1]);
            this.programs.put(name, program);
        }
        return program;
    }

    private long mainFramebuffer(int width, int height) {
        long image = this.frameTarget.colorImage();
        if (image != this.mainFramebufferImage || width != this.mainFramebufferWidth
                || height != this.mainFramebufferHeight) {
            if (this.mainFramebuffer != 0) {
                vkDestroyFramebuffer(this.context.device(), this.mainFramebuffer, null);
                this.mainFramebuffer = 0;
            }
            try (MemoryStack stack = stackPush()) {
                LongBuffer attachments = stack.longs(this.frameTarget.colorView());
                VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack).sType$Default()
                        .renderPass(this.renderPass).pAttachments(attachments)
                        .width(width).height(height).layers(1);
                LongBuffer handle = stack.mallocLong(1);
                check(vkCreateFramebuffer(this.context.device(), info, null, handle),
                        "vkCreateFramebuffer(post chain main)");
                this.mainFramebuffer = handle.get(0);
                this.mainFramebufferImage = image;
                this.mainFramebufferWidth = width;
                this.mainFramebufferHeight = height;
            }
        }
        return this.mainFramebuffer;
    }

    /**
     * Updates the next ring set with this pass's views. A set is updated only after the command buffer that last
     * bound it finished - each pass takes a fresh slot, so nothing here is bound in the buffer being recorded.
     */
    private long descriptorSet(long inView, long auxView) {
        VkDevice device = this.context.device();
        long set = this.setRing[this.setCursor];
        this.setCursor = (this.setCursor + 1) % this.setRing.length;
        try (MemoryStack stack = stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
            VkDescriptorImageInfo.Buffer inInfo = VkDescriptorImageInfo.calloc(1, stack);
            inInfo.get(0).sampler(this.sampler).imageView(inView).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer auxInfo = VkDescriptorImageInfo.calloc(1, stack);
            auxInfo.get(0).sampler(this.sampler).imageView(auxView).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            writes.get(0).sType$Default().dstSet(set).dstBinding(0).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(inInfo);
            writes.get(1).sType$Default().dstSet(set).dstBinding(1).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(auxInfo);
            vkUpdateDescriptorSets(device, writes, null);
            return set;
        }
    }

    public void delete() {
        VkDevice device = this.context.device();
        vkDeviceWaitIdle(device);
        // The census before anything is freed: the handles this chain's targets carried, so a validation message's
        // image handle can be matched against the target it belonged to after the run.
        trace("delete: targets=" + this.targets.size() + " recordings=" + this.recordings + " chains=" + this.chainsRun
                + " initBarriers=" + this.initBarriers + " commits=" + this.commits + " uncommittedRecordings="
                + this.uncommittedRecordings + " repeatInits=" + this.repeatInits + " adoptions=" + this.adoptions
                + " pendingInit=" + this.pendingInit.size());
        for (Target target : this.targets.values()) {
            trace("delete: identity=" + target.identity + " image=0x" + hex(target.image) + " view=0x"
                    + hex(target.view) + " " + target.width + "x" + target.height + " owned=" + target.owned
                    + " initialized=" + target.initialized + " barriersRecorded=" + target.barriersRecorded);
        }
        // An adopted target frees only the framebuffer this class built around it, never the caller's image.
        for (Target target : this.targets.values()) {
            target.delete(device);
        }
        this.targets.clear();
        for (Target retired : this.retiredTargets) {
            retired.delete(device);
        }
        this.retiredTargets.clear();
        for (Program program : this.programs.values()) {
            vkDestroyPipeline(device, program.pipeline, null);
            vkDestroyPipelineLayout(device, program.layout, null);
        }
        this.programs.clear();
        if (this.mainFramebuffer != 0) {
            vkDestroyFramebuffer(device, this.mainFramebuffer, null);
            this.mainFramebuffer = 0;
        }
        vkDestroySampler(device, this.sampler, null);
        vkDestroyDescriptorPool(device, this.descriptorPool, null);
        vkDestroyDescriptorSetLayout(device, this.setLayout, null);
        vkDestroyRenderPass(device, this.renderPass, null);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with " + result);
        }
    }

    private static long createShaderModule(MemoryStack stack, VkDevice device, String name) {
        ByteBuffer code = SpirvShaders.load(name);
        try {
            VkShaderModuleCreateInfo create = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateShaderModule(device, create, null, handle), "vkCreateShaderModule(" + name + ")");
            return handle.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    /**
     * A named chain target: an image the passes both write to and sample from, either one this class allocated or one
     * the caller adopted. The ownership flag decides everything but the framebuffer: an adopted target's image, its
     * memory and its view belong to the caller, so they are never created, resized or freed here.
     */
    private static final class Target {
        /** The framebuffer identity this target resolves, carried here so a trace line names it. */
        final int identity;
        final int width;
        final int height;
        final long image;
        final long memory;
        final long view;
        /** 0 until a pass writes an adopted target; an allocated target is given one at creation. */
        long fb;
        final boolean owned;

        Target(int identity, int width, int height, long image, long memory, long view, long fb, boolean owned) {
            this.identity = identity;
            this.width = width;
            this.height = height;
            this.image = image;
            this.memory = memory;
            this.view = view;
            this.fb = fb;
            this.owned = owned;
        }

        /**
         * Whether this image is in {@code GENERAL}, which is only ever true once the recording that carried its
         * {@code UNDEFINED -> GENERAL} barrier was reported as submitted ({@link #commitPasses()}), or - for an
         * adopted image - from the moment the caller guarantees a layout it established itself in the same command
         * buffer. An allocated target starts false here and stays false until {@link #commitPasses()} says otherwise.
         */
        boolean initialized;
        /** How many recordings carried this target's one-time barrier; more than one means one of them never ran. */
        int barriersRecorded;

        /** {@return the adopted view of an image the caller owns}, with nothing but the view handed over. */
        static Target adopt(int identity, int width, int height, long image, long view) {
            Target adopted = new Target(identity, width, height, image, 0L, view, 0L, false);
            // The caller's image is already in GENERAL and already holds what the passes must read - the silhouette,
            // for the outline chain - so the one-time UNDEFINED -> GENERAL move is skipped: it discards content, and
            // that content is the whole reason the image is adopted. The caller establishes that layout itself, in the
            // same command buffer the passes are recorded into (the off-target clear records the one-way move), which
            // is what makes skipping the barrier here an assertion about the caller rather than a guess.
            adopted.initialized = true;
            return adopted;
        }

        static Target create(int identity, VulkanContext context, long renderPass, int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("chain target needs a size, got " + width + "x" + height);
            }
            VkDevice device = context.device();
            try (MemoryStack stack = stackPush()) {
                LongBuffer handle = stack.mallocLong(1);
                VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default()
                        .imageType(VK_IMAGE_TYPE_2D).format(VK_FORMAT_R8G8B8A8_UNORM)
                        .mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
                                | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                imageInfo.extent(it -> it.width(width).height(height).depth(1));
                check(vkCreateImage(device, imageInfo, null, handle), "vkCreateImage(post chain)");
                long image = handle.get(0);
                VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
                vkGetImageMemoryRequirements(device, image, requirements);
                VkMemoryAllocateInfo alloc = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                        .allocationSize(requirements.size())
                        .memoryTypeIndex(context.memoryTypeIndex(requirements.memoryTypeBits(),
                                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "post chain target"));
                check(vkAllocateMemory(device, alloc, null, handle), "vkAllocateMemory(post chain)");
                long memory = handle.get(0);
                check(vkBindImageMemory(device, image, memory, 0), "vkBindImageMemory(post chain)");
                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                        .image(image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(VK_FORMAT_R8G8B8A8_UNORM);
                viewInfo.subresourceRange(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0)
                        .levelCount(1).baseArrayLayer(0).layerCount(1));
                check(vkCreateImageView(device, viewInfo, null, handle), "vkCreateImageView(post chain)");
                long view = handle.get(0);
                LongBuffer attachments = stack.longs(view);
                VkFramebufferCreateInfo fbInfo = VkFramebufferCreateInfo.calloc(stack).sType$Default()
                        .renderPass(renderPass).pAttachments(attachments).width(width).height(height).layers(1);
                check(vkCreateFramebuffer(device, fbInfo, null, handle), "vkCreateFramebuffer(post)");
                long fb = handle.get(0);
                return new Target(identity, width, height, image, memory, view, fb, true);
            }
        }

        /** {@return the framebuffer that renders into this target}, built when a pass first writes an adopted one. */
        long framebuffer(VulkanContext context, long renderPass) {
            if (this.fb == 0L) {
                try (MemoryStack stack = stackPush()) {
                    LongBuffer attachments = stack.longs(this.view);
                    VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack).sType$Default()
                            .renderPass(renderPass).pAttachments(attachments)
                            .width(this.width).height(this.height).layers(1);
                    LongBuffer handle = stack.mallocLong(1);
                    check(vkCreateFramebuffer(context.device(), info, null, handle),
                            "vkCreateFramebuffer(post adopted target)");
                    this.fb = handle.get(0);
                }
            }
            return this.fb;
        }

        void delete(VkDevice device) {
            if (this.fb != 0L) {
                vkDestroyFramebuffer(device, this.fb, null);
                this.fb = 0L;
            }
            if (!this.owned) {
                // The image, its memory and its view are the caller's: the framebuffer above was all this class owned.
                return;
            }
            vkDestroyImageView(device, view, null);
            vkDestroyImage(device, image, null);
            vkFreeMemory(device, memory, null);
        }
    }

    /** One program: pipeline + layout sized to its push block. */
    private static final class Program {
        final long pipeline;
        final long layout;
        final int pushRange;

        Program(long pipeline, long layout, int pushRange) {
            this.pipeline = pipeline;
            this.layout = layout;
            this.pushRange = pushRange;
        }

        static Program create(VulkanContext context, long renderPass, long setLayout, String vertex,
                String fragment) {
            VkDevice device = context.device();
            int pushBytes = pushBytesFor(fragment);
            try (MemoryStack stack = stackPush()) {
                LongBuffer handle = stack.mallocLong(1);
                VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
                range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0).size(pushBytes);
                LongBuffer setLayouts = stack.longs(setLayout);
                VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                        .pSetLayouts(setLayouts).pPushConstantRanges(range);
                check(vkCreatePipelineLayout(device, layoutInfo, null, handle),
                        "vkCreatePipelineLayout(post " + fragment + ")");
                long layout = handle.get(0);

                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT)
                        .module(createShaderModule(stack, device, vertex)).pName(stack.UTF8("main"));
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                        .module(createShaderModule(stack, device, fragment)).pName(stack.UTF8("main"));

                VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo
                        .calloc(stack).sType$Default();
                VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo
                        .calloc(stack).sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP);
                VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType$Default().viewportCount(1).scissorCount(1);
                VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType$Default().polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE)
                        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1.0f);
                VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo
                        .calloc(stack).sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                VkPipelineColorBlendAttachmentState.Buffer blendAttachment = VkPipelineColorBlendAttachmentState
                        .calloc(1, stack);
                blendAttachment.get(0).blendEnable(false)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                                | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default().pAttachments(blendAttachment);
                java.nio.IntBuffer dynamic = stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
                org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamicState = org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
                        .calloc(stack).sType$Default().pDynamicStates(dynamic);

                VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1, stack);
                info.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput)
                        .pInputAssemblyState(inputAssembly).pViewportState(viewport).pRasterizationState(raster)
                        .pMultisampleState(multisample).pColorBlendState(blend).pDynamicState(dynamicState)
                        .layout(layout).renderPass(renderPass).subpass(0);
                LongBuffer pipelineHandle = stack.mallocLong(1);
                check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, info, null, pipelineHandle),
                        "vkCreateGraphicsPipelines(post " + fragment + ")");
                vkDestroyShaderModule(device, stages.get(0).module(), null);
                vkDestroyShaderModule(device, stages.get(1).module(), null);
                return new Program(pipelineHandle.get(0), layout, pushBytes);
            }
        }

        /** The byte length of the program's push block, in the JSON declaration order the mixin packs. */
        private static int pushBytesFor(String fragment) {
            switch (fragment) {
                case "post_invert.frag": return 92;
                case "post_blit.frag": return 112;
                case "post_blur.frag": return 100;
                case "post_bits.frag": return 100;
                case "post_entity_sobel.frag": return 88;
                case "post_color_convolve.frag": return 196;
                case "post_spiderclip.frag": return 144;
                default: return 128;
            }
        }
    }
}
