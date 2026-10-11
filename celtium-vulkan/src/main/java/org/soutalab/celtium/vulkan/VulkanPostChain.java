package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
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
 * uniform block the collector packs in declaration order goes in whole as push constants when it fits the device's
 * {@code maxPushConstantsSize}, and into a host-visible uniform buffer bound in the same descriptor set as the two
 * samplers when it does not: color_convolve's block is 192 bytes and spider's 144, so on a device whose limit is the
 * 128-byte minimum - the Vulkan minimum, which is what both verified GPUs report - those two programs take the buffer
 * route and every other program keeps pushing. The layout is not restated per route: the member offsets each shader
 * declares are read out of its own SPIR-V module, and the layout the collector reports for the pass has to place every
 * declared member at the offset the shader declares, or the chain is refused by name rather than run on a block whose
 * members sit where the shader does not read them. The buffer route's slots are the descriptor ring's slots - one
 * block per pass, taken with the set that binds it - so it inherits that ring's refusal to wrap inside a recording
 * (audit N16) instead of overwriting a block a not-yet-submitted command buffer still reads.
 * <p>
 * <b>What a chain may read.</b> A chain is only accepted when every image it reads from <em>outside itself</em> can be
 * supplied by this class: the group's main framebuffer (the frame image the caller acquired, which holds what the
 * game drew), an image the caller adopted under the identity the pass carries (the entity-outline silhouette under
 * the GL-less backend), or a target an earlier pass of the same chain wrote. An identity that is none of those is a
 * framebuffer only GL has ever written - the bridge case, where the chain's input is a real GL framebuffer - and
 * {@link #recordPasses} refuses the whole chain instead of allocating an empty target for it and reporting a run that
 * sampled nothing. The caller turns that refusal into the GL chain it replaces; see
 * {@link #unsuppliedInput(List)}.
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
         * The block the collector packed, as the member names it wrote and the byte offset it wrote each at, in
         * packing order. {@link #push} holds the values and {@link #pushBytes} how many of them are meaningful; this
         * is the same layout said member by member, which is the only form the shader's own declaration can be
         * compared against: two blocks of equal size whose members are permuted are indistinguishable in a length.
         * <p>
         * Null when the collector does not report it. The uniform-buffer route then refuses these passes rather than
         * write a block whose offsets nothing has checked - see {@link #blockRefusal}.
         */
        public String[] blockNames;
        public int[] blockOffsets;
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

    /**
     * The programs whose block does not fit the push-constant limit on some device, and which therefore have a shader
     * variant declaring the same block as a uniform buffer: {@code <module>_ubo.<stage>} beside each push shader. The
     * route is chosen per pass from the block's size against the device limit, so a device whose limit carries
     * color_convolve's 192 bytes keeps pushing it; this set only says which programs <em>can</em> take the buffer.
     */
    private static final Set<String> UBO_PROGRAMS = new HashSet<>(Arrays.asList("color_convolve", "spider"));
    /** The set binding the uniform buffer is bound at; 0 and 1 are the two combined image samplers. */
    private static final int UNIFORM_BLOCK_BINDING = 2;

    /**
     * {@return the uniform-buffer variant of a shader module name}: {@code post_sobel.vert} becomes
     * {@code post_sobel_ubo.vert}. Derived rather than listed a second time, so a variant can only be missing, never
     * mismatched by name.
     */
    static String uboVariant(String module) {
        int dot = module.lastIndexOf('.');
        return dot < 0 ? module + "_ubo" : module.substring(0, dot) + "_ubo" + module.substring(dot);
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
     * <p>
     * With the input contract enforced in {@link #recordPasses} this counter is a proof rather than a measurement: a
     * chain that would have run on a stand-in is refused before anything is recorded, so it must stay zero. A nonzero
     * value means a caller bypassed the gate.
     */
    private int runsOnAllocatedStandIn;
    /** One-shot: the same fact printed with the identity, texture and size the caller reported. */
    private boolean standInReported;
    /** Chains refused because a real input could not be supplied natively; reported by {@link #status()}. */
    private int refusedInputs;
    /** Refusals of the specific case the old stand-in counter measured: a reported identity nothing was adopted for. */
    private int standInRefusals;
    /**
     * How many times {@link #unsuppliedInput} was asked and answered "not suppliable". Counted there as well as in
     * {@link #recordPasses} because a caller may consult it as a preflight and refuse before the executor ever
     * records - the bridge's {@code celtium$postChainRun} does exactly that - and the run's report must still say
     * that the chain was seen and refused rather than silently absent.
     */
    private int preflightRefusals;
    /** One-shot: the refusal with the identity, size and program that asked for the unsuppliable image. */
    private boolean refusalReported;
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
    /**
     * The recording {@link #pendingInit} belongs to - the token {@link #recordPasses} returned for it. A commit that
     * names a different recording is not this recording's success and commits nothing; see
     * {@link #commitPasses(int)}.
     */
    private int pendingRecording;
    /** Recordings that carried a first-use barrier, counted by {@link #initBarriers} and {@link #commits}. */
    private int initBarriers;
    private int commits;
    /** Commits that named a recording which no longer owns the pending set, so they committed nothing. */
    private int staleCommits;
    /** Recordings that carried a first-use barrier and were never reported as submitted before the next one began. */
    private int uncommittedRecordings;
    /** Recordings refused because the descriptor ring would have wrapped inside them (audit N16). */
    private int ringExhaustions;
    /** How many targets were recorded a barrier again after an earlier recording of theirs was not submitted. */
    private int repeatInits;
    /** Bounded trace budget: {@link #trace} prints at most this many lines over the chain's whole life. */
    private static final int TRACE_LIMIT = 240;
    private int traceLines;
    /** One-shot flag: the trace says once that it stopped printing rather than going silent for no stated reason. */
    private boolean traceLimitReported;
    /** Recordings recorded so far; the trace numbers them so a barrier and the draw that follows can be paired. */
    private int recordings;

    /**
     * Test-only injection, guarded by the validation flag: fail this many recordings after they are fully recorded and
     * before the caller's {@code vkQueueSubmit} - the exact point the audit's N05 asks to be exercised, where the
     * commands exist but the queue never owns them. The caller sees an ordinary recording failure, abandons the
     * frame, and the targets whose {@code UNDEFINED -> GENERAL} barrier went into the discarded buffer must get it
     * again in the next recording; a submission of anything else must not commit them.
     */
    private static final int FAIL_FIRST_USE_RECORDINGS =
            Integer.getInteger("celtium.vulkan.postChainFailFirstUse", 0).intValue();
    private static final boolean INJECTION_ALLOWED = Boolean.getBoolean("celtium.vulkan.validation");
    private int injectedFirstUseFailures;

    /**
     * Test-only: decide the route as if the device's push-constant limit were this many bytes, so the uniform-buffer
     * route can be exercised on a device whose own limit carries the block. The verified AMD device reports 256, where
     * color_convolve's 192 bytes fit and the buffer route would be dead code on that machine; with this set to 128 the
     * same chain, scene and frame run through the buffer instead, which is what makes "the two routes render the same
     * picture" a measurement on one GPU rather than a comparison across two.
     * <p>
     * It is an instrument, not the route's answer: it never widens what is accepted (a device whose real limit is
     * smaller still decides by its real limit, and a block the real limit cannot carry is still refused), it is
     * guarded by the validation flag like the failure injections above, and the default - the device's own limit -
     * is what ships.
     */
    private static final int MAX_PUSH_CONSTANTS_OVERRIDE =
            INJECTION_ALLOWED ? Integer.getInteger("celtium.vulkan.postChainMaxPushConstants", 0).intValue() : 0;

    /** Persistent descriptor sets, handed out round-robin: one set per pass, never reused inside one command
     * buffer, so updating one is legal whenever the buffer that last bound it has completed. */
    private final long[] setRing = new long[32];
    private int setCursor;
    /**
     * How many ring sets the recording in progress has taken. The ring is only safe if a set is updated after the
     * command buffer that last bound it has completed, and that stops being true the moment the cursor wraps inside a
     * single recording: the set pass 33 takes is the one pass 1 bound, and updating it changes what the pending
     * recording reads. The counter is what makes the wrap visible instead of silent (audit N16).
     * <p>
     * The uniform-buffer slots are taken with the sets - slot {@code i} of the buffer is what set {@code i} binds - so
     * this one counter bounds both, and the refusal below covers both.
     */
    private int setsThisRecording;

    /**
     * The uniform blocks of the buffer route, one host-visible slot per descriptor set, or null when this device
     * cannot run that route. Sized and bound at creation from the blocks the shader variants declare; never grown,
     * because growing it would destroy a buffer the ring's descriptors already name.
     */
    private final VulkanUniformBuffer uniformBlocks;
    /** Byte distance between two slots; a multiple of the device's {@code minUniformBufferOffsetAlignment}. */
    private final long uniformStride;
    /** The largest block any uniform-buffer variant declares: the size every slot's descriptor is bound with. */
    private final int uniformBlockBytes;
    /** The mapped view of {@link #uniformBlocks}, addressed in floats; null exactly when that buffer is. */
    private final FloatBuffer uniformView;
    /** One-shot: why the buffer route is unavailable on this device, or null when it is available. */
    private final String uniformRouteRefusal;
    /** Programs whose pipeline was built on the uniform-buffer route, by name; reported by {@link #status()}. */
    private final List<String> uboPrograms = new ArrayList<>();
    /** Programs refused a pipeline for the buffer route, with the reason; reported by {@link #status()}. */
    private final Map<String, String> programRefusals = new HashMap<>();
    /** Passes recorded through the uniform-buffer route and bytes written into it; reported by {@link #status()}. */
    private int uboPasses;
    private long uboBytesWritten;
    /** Chains refused because a pass's packed block does not match the block its shader declares. */
    private int blockLayoutRefusals;
    /** One-shot: the first such refusal, printed with the member, the two offsets and the program. */
    private boolean blockLayoutRefusalReported;
    /**
     * Programs whose packed block has been printed once, as the floats the route writes. The offsets the shader
     * declares are checked in {@link #blockRefusal}; what is <em>written</em> at those offsets is only visible in a
     * run, and a picture that leaves the window says nothing about it when the chain's output never reaches that
     * picture. One line per program, over the chain's whole life.
     */
    private final Set<String> packedReported = new HashSet<>();
    /** One-shot: the uniform-buffer route's own unavailability, printed when the first chain asks for it. */
    private boolean uboRouteReported;
    /**
     * Programs asked for a pass whose packed block exceeds the device's push-constant limit, counted per pass. Zero
     * means no chain on this device ever needed the buffer route; nonzero with {@code postUboPrograms=0} means one
     * asked and was refused (the reason is the program refusal in the trace).
     */
    private int uboRequests;

    private VulkanPostChain(VulkanContext context, FrameImageTarget frameTarget, long renderPass, long setLayout,
            long descriptorPool, long sampler, int maxPushConstants, VulkanUniformBuffer uniformBlocks,
            long uniformStride, int uniformBlockBytes, String uniformRouteRefusal) {
        this.context = context;
        this.frameTarget = frameTarget;
        this.renderPass = renderPass;
        this.setLayout = setLayout;
        this.descriptorPool = descriptorPool;
        this.sampler = sampler;
        this.maxPushConstants = maxPushConstants;
        this.uniformBlocks = uniformBlocks;
        this.uniformStride = uniformStride;
        this.uniformBlockBytes = uniformBlockBytes;
        this.uniformRouteRefusal = uniformRouteRefusal;
        if (uniformBlocks == null) {
            this.uniformView = null;
        } else {
            // One view of the mapped ring, addressed in floats: the slots are 4-byte aligned by construction (the
            // stride is a multiple of the device's uniform-buffer alignment) and never move, because nothing grows
            // this buffer after creation.
            ByteBuffer mapped = uniformBlocks.mapped().order(java.nio.ByteOrder.nativeOrder());
            this.uniformView = mapped.asFloatBuffer();
        }
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
            if (MAX_PUSH_CONSTANTS_OVERRIDE > 0 && MAX_PUSH_CONSTANTS_OVERRIDE < maxPush) {
                System.out.println("[Celtium] CELTIUM_POSTCHAIN MAX_PUSH_OVERRIDE device=" + maxPush
                        + " routeDecision=" + MAX_PUSH_CONSTANTS_OVERRIDE + " (test-only,"
                        + " -Dceltium.vulkan.postChainMaxPushConstants; the device's own limit is what ships)");
                maxPush = MAX_PUSH_CONSTANTS_OVERRIDE;
            }

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

            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
            bindings.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            bindings.get(1).binding(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            // The uniform-buffer route's binding. It is in the layout for every program - the pipeline layouts all
            // take this one set layout - but only the *_ubo shader variants declare it, and a descriptor nothing
            // statically reads may stay unwritten.
            bindings.get(2).binding(UNIFORM_BLOCK_BINDING).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                    .pBindings(bindings);
            check(vkCreateDescriptorSetLayout(device, layoutInfo, null, handle),
                    "vkCreateDescriptorSetLayout(post chain)");
            long setLayout = handle.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
            poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(64);
            poolSizes.get(1).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(32);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(32).pPoolSizes(poolSizes);
            check(vkCreateDescriptorPool(device, poolInfo, null, handle),
                    "vkCreateDescriptorPool(post chain)");
            long descriptorPool = handle.get(0);

            // The uniform-buffer route's ring, established here - before any recording exists - from what the
            // *_ubo variants declare, so a pass never has to ask whether there is room for it. A device where any of
            // that fails simply has no buffer route: the programs that would need it are refused by name, exactly as
            // they were before this route existed, and every push-route chain runs untouched.
            int uniformBlockBytes = 0;
            String uniformRouteRefusal = null;
            try {
                uniformBlockBytes = declaredBlockBytes();
            } catch (RuntimeException | LinkageError error) {
                uniformRouteRefusal = "the uniform-buffer route's shaders could not be read: " + error;
            }
            long uniformStride = 0;
            VulkanUniformBuffer uniformBlocks = null;
            // The limit is a uint32; RADV reports 0xFFFFFFFF for it (measured on this AMD device: read as a signed
            // int it is -1, which refused the route before this comparison was made unsigned).
            long maxUniformBufferRange = Integer.toUnsignedLong(properties.limits().maxUniformBufferRange());
            if (uniformRouteRefusal == null && uniformBlockBytes > maxUniformBufferRange) {
                // A block larger than the largest range a uniform-buffer descriptor may name cannot be bound at all;
                // the guaranteed minimum is 16384 bytes, so this is a device the route simply cannot serve.
                uniformRouteRefusal = "the uniform-buffer route's block is " + uniformBlockBytes
                        + "B, this device's maxUniformBufferRange is " + maxUniformBufferRange;
            }
            if (uniformRouteRefusal == null) {
                long alignment = Math.max(256L, properties.limits().minUniformBufferOffsetAlignment());
                uniformStride = (uniformBlockBytes + alignment - 1) / alignment * alignment;
                try {
                    uniformBlocks = new VulkanUniformBuffer(context, uniformStride * 32);
                } catch (RuntimeException | LinkageError error) {
                    // Includes "no host-visible, host-coherent memory type": a refusal the caller keeps, not a crash.
                    uniformRouteRefusal = "the uniform-buffer ring could not be allocated: " + error;
                }
            }
            long uniformRouteStride = uniformStride;
            int uniformRouteBytes = uniformBlockBytes;

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
                    descriptorPool, sampler, maxPush, uniformBlocks, uniformRouteStride, uniformRouteBytes,
                    uniformRouteRefusal);
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
            // Each set names its own slot of the uniform ring, once, here: slot i's block can then only change
            // through a mapping write, and the pass that takes set i is the pass that owns that write. Going through
            // vkUpdateDescriptorSets per pass instead would put a descriptor update and the command buffer that
            // bound it in the same race the ring exists to avoid.
            if (uniformBlocks != null) {
                chain.writeUniformDescriptors(uniformBlockBytes);
            }
            if (uniformRouteRefusal != null) {
                System.out.println("[Celtium] CELTIUM_POSTCHAIN UBO_ROUTE_UNAVAILABLE " + uniformRouteRefusal
                        + " - a chain whose block does not fit " + maxPush + " push-constant bytes is refused"
                        + " (post chain refused), as it was before the uniform-buffer route existed");
            } else {
                System.out.println("[Celtium] CELTIUM_POSTCHAIN UBO_ROUTE device=" + context.deviceName()
                        + " binding=" + UNIFORM_BLOCK_BINDING + " slots=" + chain.setRing.length
                        + " blockBytes=" + uniformBlockBytes + " stride=" + uniformStride
                        + " mapped=" + (chain.uniformView != null));
            }
            return chain;
        }
    }

    /**
     * {@return the largest block the uniform-buffer shader variants declare, in bytes}.
     * <p>
     * Read from the variants' own SPIR-V at creation, so the ring's size, its slots' stride and the descriptor range
     * all come from the declarations rather than from a number kept beside them - and a variant that cannot be read
     * or declares a member type this renderer does not understand refuses the whole route instead of being sized
     * approximately.
     */
    private static int declaredBlockBytes() {
        int bytes = 0;
        for (String program : UBO_PROGRAMS) {
            String[] modules = PROGRAMS.get(program);
            for (String module : modules) {
                bytes = Math.max(bytes, mergedBlock(uboVariant(module), module).bytes);
            }
        }
        return bytes;
    }

    /** Binds every ring set's uniform-block binding to its own slot of the mapped ring. */
    private void writeUniformDescriptors(int blockBytes) {
        VkDevice device = this.context.device();
        for (int i = 0; i < this.setRing.length; ++i) {
            try (MemoryStack stack = stackPush()) {
                VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(this.uniformBlocks.buffer())
                        .offset(i * this.uniformStride)
                        .range(blockBytes);
                VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
                write.get(0).sType$Default().dstSet(this.setRing[i]).dstBinding(UNIFORM_BLOCK_BINDING)
                        .descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .pBufferInfo(info);
                vkUpdateDescriptorSets(device, write, null);
            }
        }
    }

    /**
     * {@return whether every program the pass list uses has a native pipeline this device can carry}.
     * <p>
     * A pass whose packed block fits {@code maxPushConstantsSize} runs through push constants, exactly as it always
     * has; one that does not needs the program's uniform-buffer shader variant and a slot of the uniform ring. This is
     * the caller's preflight - the bridge asks it before it opens a frame - so it must answer rather than throw: a
     * program whose variant is missing, unreadable or laid out differently is recorded in
     * {@link #programRefusals} with its reason and answered "no", which leaves the caller its GL chain and, under the
     * GL-less window, the named refusal it has today.
     */
    public boolean supportsAll(List<Pass> passes) {
        if (passes.isEmpty()) {
            return false;
        }
        for (Pass pass : passes) {
            Program program = programFor(pass);
            if (program == null) {
                return false;
            }
            String refusal = blockRefusal(pass, program);
            if (refusal != null) {
                this.blockLayoutRefusals++;
                reportBlockRefusal(pass, program, refusal);
                return false;
            }
        }
        return true;
    }

    /**
     * {@return the pipeline this pass's program runs through, or null when no route can carry its block}; never
     * throws, and caches both the pipelines and the refusals, so a program that cannot run is diagnosed once per
     * chain lifetime rather than once per frame.
     */
    private Program programFor(Pass pass) {
        if (pass == null || pass.program == null || !PROGRAMS.containsKey(pass.program) || pass.pushBytes <= 0) {
            return null;
        }
        Program cached = this.programs.get(pass.program);
        if (cached != null) {
            return cached;
        }
        if (this.programRefusals.containsKey(pass.program)) {
            return null;
        }
        boolean uniformBlock = pass.pushBytes > this.maxPushConstants;
        if (uniformBlock && !UBO_PROGRAMS.contains(pass.program)) {
            // No shader variant declares this program's block as a uniform buffer, and inventing one here would mean
            // writing the block at offsets nothing declares. The caller keeps the refusal it has today.
            this.programRefusals.put(pass.program, "no uniform-buffer shader variant for " + pass.program
                    + " (block " + pass.pushBytes + "B > maxPushConstantsSize " + this.maxPushConstants + "B)");
            return null;
        }
        if (uniformBlock) {
            this.uboRequests++;
            if (this.uniformBlocks == null) {
                this.programRefusals.put(pass.program, this.uniformRouteRefusal);
                if (!this.uboRouteReported) {
                    this.uboRouteReported = true;
                    System.out.println("[Celtium] CELTIUM_POSTCHAIN UBO_REFUSED program=" + pass.program
                            + " blockBytes=" + pass.pushBytes + " maxPush=" + this.maxPushConstants
                            + " reason=" + this.uniformRouteRefusal);
                }
                return null;
            }
        }
        try {
            String[] modules = PROGRAMS.get(pass.program);
            Program program = Program.create(this.context, this.renderPass, this.setLayout, uniformBlock,
                    modules[0], modules[1], pass.pushBytes, this.uniformBlockBytes);
            this.programs.put(pass.program, program);
            if (uniformBlock) {
                this.uboPrograms.add(pass.program);
            }
            trace("ROUTE program=" + pass.program + " route=" + (uniformBlock ? "uniformBuffer" : "pushConstants")
                    + " packedBytes=" + pass.pushBytes + " blockBytes=" + program.blockBytes
                    + " pushRange=" + program.pushRange + " maxPush=" + this.maxPushConstants
                    + " members=" + program.blockMembers.size()
                    + (uniformBlock ? " binding=" + UNIFORM_BLOCK_BINDING : ""));
            if (this.packedReported.add(pass.program)) {
                trace("PACKED program=" + pass.program + " bytes=" + pass.pushBytes + " bits="
                        + packedBits(pass));
            }
            return program;
        } catch (RuntimeException | LinkageError error) {
            this.programRefusals.put(pass.program, String.valueOf(error));
            if (uniformBlock) {
                this.uboRouteReported = true;
                System.out.println("[Celtium] CELTIUM_POSTCHAIN UBO_REFUSED program=" + pass.program
                        + " blockBytes=" + pass.pushBytes + " maxPush=" + this.maxPushConstants
                        + " reason=" + error + " - the chain keeps the refusal it has today");
            } else {
                System.out.println("[Celtium] CELTIUM_POSTCHAIN PROGRAM_REFUSED program=" + pass.program
                        + " reason=" + error);
            }
            return null;
        }
    }

    /**
     * {@return the packed block's floats as raw little-endian bit patterns}, so a run's log carries the numbers the
     * shader will compute with and not only the picture that leaves the window. Read with the member offsets the
     * trace prints beside it, this is what says whether a pass's own uniform values or the program's defaults went in.
     */
    private static String packedBits(Pass pass) {
        StringBuilder bits = new StringBuilder(pass.pushBytes * 2);
        for (int i = 0; i < pass.pushBytes / 4; ++i) {
            String word = Integer.toHexString(Float.floatToRawIntBits(pass.push[i]));
            for (int pad = word.length(); pad < 8; ++pad) {
                bits.append('0');
            }
            bits.append(word);
        }
        return bits.toString();
    }

    /** {@return the reason this pass's packed block cannot feed the program's declared block, or null}. */
    private static String blockRefusal(Pass pass, Program program) {
        int limit = program.uniformBlock ? program.blockBytes : program.pushRange;
        if (pass.pushBytes > limit) {
            return "the collector packed " + pass.pushBytes + "B for " + pass.program + ", whose "
                    + (program.uniformBlock ? "declared block is " : "push range is ") + limit + "B";
        }
        if (pass.blockNames == null) {
            // The collector does not report the layout member by member, so the offsets cannot be checked. The extent
            // above is all the evidence there is; the buffer route in particular must not write a block whose member
            // placement nothing has compared with the shader's declaration.
            return program.uniformBlock ? "the collector did not report the block's member layout"
                    + " (Pass.blockNames), which the uniform-buffer route requires" : null;
        }
        if (pass.blockNames.length != pass.blockOffsets.length) {
            return "the collector reported " + pass.blockNames.length + " member names and "
                    + pass.blockOffsets.length + " offsets for " + pass.program;
        }
        for (int i = 0; i < pass.blockNames.length; ++i) {
            Integer declared = program.blockMembers.get(pass.blockNames[i]);
            if (declared != null && declared.intValue() != pass.blockOffsets[i]) {
                return "member " + pass.blockNames[i] + " of " + pass.program + " is packed at "
                        + pass.blockOffsets[i] + "B, the shader declares it at " + declared + "B";
            }
        }
        // The other direction: a member the shader declares but the collector did not place. The buffer route can
        // leave it at its GL default (zero) only when it lies beyond everything that was packed; the push route
        // cannot, because the offsets past the pushed bytes hold whatever the last push left there.
        Map<String, Integer> reported = new HashMap<>();
        for (int i = 0; i < pass.blockNames.length; ++i) {
            reported.put(pass.blockNames[i], Integer.valueOf(pass.blockOffsets[i]));
        }
        for (Map.Entry<String, Integer> member : program.blockMembers.entrySet()) {
            if (reported.containsKey(member.getKey())) {
                continue;
            }
            if (program.uniformBlock) {
                if (member.getValue().intValue() < pass.pushBytes) {
                    return "member " + member.getKey() + " of " + pass.program + " is declared at "
                            + member.getValue() + "B, inside the packed " + pass.pushBytes + "B, but the collector"
                            + " did not report it: the shader would read bytes packed for another member";
                }
            } else {
                return "member " + member.getKey() + " of " + pass.program + " is declared at " + member.getValue()
                        + "B and the collector did not report it: the push route cannot leave a declared member"
                        + " unwritten";
            }
        }
        return null;
    }

    /** Prints the first block-layout refusal with everything the diagnosis needs; the rest are counted. */
    private void reportBlockRefusal(Pass pass, Program program, String reason) {
        if (this.blockLayoutRefusalReported) {
            return;
        }
        this.blockLayoutRefusalReported = true;
        StringBuilder layout = new StringBuilder();
        if (pass.blockNames != null) {
            for (int i = 0; i < pass.blockNames.length && i < pass.blockOffsets.length; ++i) {
                if (layout.length() > 0) {
                    layout.append(',');
                }
                layout.append(pass.blockNames[i]).append('@').append(pass.blockOffsets[i]);
            }
        }
        System.out.println("[Celtium] CELTIUM_POST_BLOCK_REFUSED program=" + pass.program + " route="
                + (program == null ? "none" : (program.uniformBlock ? "uniformBuffer" : "pushConstants"))
                + " pushBytes=" + pass.pushBytes + " declaredBytes=" + (program == null ? 0 : program.blockBytes)
                + " pushRange=" + (program == null ? 0 : program.pushRange) + " reason=" + reason
                + " packed=[" + layout + "] - the chain is refused rather than run on a block whose members do not"
                + " sit where the shader reads them");
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
     * A chain that reads an image this class cannot supply is refused before anything is recorded: see
     * {@link #unsuppliedInput(List)}. Refusing here rather than running on an allocated stand-in is what keeps a run
     * that sampled nothing out of the counters and out of the picture - the caller falls back to the GL chain, which
     * has the real image.
     * <p>
     * Every target this recording uses for the first time gets its {@code UNDEFINED -> GENERAL} barrier recorded into
     * this same command buffer, immediately before the draw that samples it. The targets are also remembered in
     * {@link #pendingInit}, bound to the recording token this method returns, until {@link #commitPasses(int)} reports
     * that <em>this</em> recording's submission reached the queue: a recording that is reset, abandoned or refused
     * therefore leaves them uninitialized, and the next recording carries their barriers again instead of sampling an
     * image that is still {@code UNDEFINED}.
     *
     * @return the recording's token, to be passed to {@link #commitPasses(int)} once - and only once - the submission
     *     that carried it has been accepted by the queue
     */
    public int recordPasses(VkCommandBuffer commandBuffer, List<Pass> passes) {
        String unsupplied = unsuppliedInput(passes);
        if (unsupplied != null) {
            // Nothing has been recorded yet, so there is nothing to roll back: the caller still owns a frame it can
            // hand back to GL, and the only thing this class did was refuse to stand in for an image it does not have.
            this.refusedInputs++;
            noteReportedButUnadoptedInput(passes);
            if (!this.refusalReported) {
                this.refusalReported = true;
                Pass first = passes.get(0);
                System.out.println("[Celtium] CELTIUM_POST_REFUSED_UNSUPPLIED_INPUT run=" + this.chainsRun
                        + " reportedIdentity=" + first.groupMainFbId + " reportedTexture=" + first.groupMainTextureId
                        + " " + first.groupMainW + "x" + first.groupMainH + " adopted=" + adoptedCount()
                        + " reason=" + unsupplied + " - the chain is not run natively; the caller's GL chain keeps it");
            }
            throw new UnsuppliedInputException(unsupplied);
        }
        if (!this.pendingInit.isEmpty()) {
            // Not a second recording inside one command buffer: a non-empty set means the recording that carried
            // these targets' first-use barrier ended without commitPasses(). Either its command buffer was reset or
            // abandoned - in which case the transitions never ran and the images are still UNDEFINED - or the caller
            // does not report submissions at all. Both cases are answered the same way: the barrier goes into this
            // recording too, because nothing here may assume the image is in GENERAL.
            this.uncommittedRecordings++;
            this.repeatInits += this.pendingInit.size();
            if (this.uncommittedRecordings <= 4) {
                for (Target target : this.pendingInit) {
                    trace("UNCOMMITTED identity=" + target.identity + " image=0x" + hex(target.image)
                            + " recording=" + this.recordings + " (committedRecording=" + this.pendingRecording
                            + "): its UNDEFINED -> GENERAL barrier was recorded but commitPasses("
                            + this.pendingRecording + ") did not report that recording as submitted, so it is"
                            + " recorded again here (the caller patch: commitPasses(token) right after the submit)");
                }
            }
        }
        this.pendingInit.clear();
        this.recordings++;
        this.pendingRecording = this.recordings;
        // A recording starts here, so the ring budget is this recording's: what earlier recordings took is already
        // behind a submitted (or abandoned) command buffer and plays no part in this one's safety.
        this.setsThisRecording = 0;

        List<String> details = new ArrayList<>();
        boolean interesting = false;
        // The recording's outputs, in pass order, for the caller's chain-time image dump: cleared here so a recording
        // that throws leaves no stale half-list, and readable before commitPasses (the dump runs right after the
        // submit, and an output image that is only correct after the commit is a measurement one step too late).
        this.lastRecordedOutputs.clear();
        int passIndex = 0;
        for (Pass pass : passes) {
            Program program = programFor(pass);
            String refusal = program == null ? "no pipeline this device can carry its block on"
                    : blockRefusal(pass, program);
            if (refusal != null) {
                // supportsAll answers this before the caller opens a frame, so reaching it here means a caller
                // skipped the preflight; it is still refused rather than run on a block nothing has checked.
                this.blockLayoutRefusals++;
                reportBlockRefusal(pass, program, refusal);
                throw new IllegalStateException("post chain program without a pipeline: " + pass.program
                        + " (" + refusal + ")");
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
            // Where this pass's result can be read back, for the chain-time dump. Main is reported as image 0: the
            // caller already holds the frame image and its view, and handing out a second handle to the same image
            // would let a reader believe it is a different one. The label is the reading aid; the pass index is what
            // makes a line attributable ("pass 0 wrote nothing" and "the last pass wrote nothing" are different
            // findings).
            this.lastRecordedOutputs.add(new RecordedOutput(passIndex, pass.program,
                    pass.outIsMain ? "main" : (outTarget.owned ? "owned" : "adopted"),
                    pass.outIsMain ? 0L : outTarget.image,
                    pass.outIsMain ? 0L : outTarget.view,
                    pass.outW, pass.outH));

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

            int slot = descriptorSet(inView, auxView);
            if (program.uniformBlock) {
                // The slot the set was just updated with is the slot this pass's block lives in, taken together with
                // it: the ring's refusal to wrap inside this recording is what makes the write safe, and it is the
                // same take the counter above already bounds.
                writeUniformBlock(slot, pass, program);
            }
            long descriptorSet = this.setRing[slot];
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
                if (!program.uniformBlock) {
                    // The push route is unchanged: the collector's block goes in whole, as it always has. Its layout
                    // is the block the shader declares, which blockRefusal has just compared member by member.
                    java.nio.FloatBuffer push = stack.mallocFloat(pass.pushBytes / 4);
                    push.put(pass.push, 0, pass.pushBytes / 4).flip();
                    vkCmdPushConstants(commandBuffer, program.layout,
                            VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, push);
                }
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, program.layout, 0,
                        stack.longs(descriptorSet), null);
                vkCmdDraw(commandBuffer, 4, 1, 0, 0);
                vkCmdEndRenderPass(commandBuffer);
            }
            this.passesRecorded++;
            passIndex++;
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
        if (INJECTION_ALLOWED && this.injectedFirstUseFailures < FAIL_FIRST_USE_RECORDINGS) {
            // The recording is complete - barriers and draws are in the command buffer - and the queue has not seen
            // it. Raising here is the closest a class that does not own the submit can get to a refused
            // vkQueueSubmit: the caller's own failure path runs (frame abandoned, false returned, GL chain kept), and
            // every target whose first-use barrier went into this buffer must stay uninitialized.
            this.injectedFirstUseFailures++;
            trace("INJECTED_FAILURE recording=" + this.recordings + " pendingInit=" + this.pendingInit.size()
                    + " - recorded, never submitted; the next recording must carry these barriers again"
                    + " (injected=" + this.injectedFirstUseFailures + "/" + FAIL_FIRST_USE_RECORDINGS + ")");
            throw new IllegalStateException("injected post chain submit failure before queue ownership: recording="
                    + this.recordings + " pendingInit=" + this.pendingInit.size() + " injected="
                    + this.injectedFirstUseFailures + "/" + FAIL_FIRST_USE_RECORDINGS);
        }
        // Counted only now: a recording that was refused (unsupplied input) or thrown away (injected failure) never
        // reached the queue, and postChains is read as "chains this executor ran", not "chains it was asked to run".
        this.chainsRun++;
        return this.pendingRecording;
    }

    /**
     * Reports that the submission which carried the barriers of the recording {@code recordingToken} reached the
     * queue, so every target that needed its {@code UNDEFINED -> GENERAL} move in <em>that</em> recording is now
     * really in {@code GENERAL}.
     * <p>
     * This is the caller's half of the chain's layout invariant and the reason the flag is not set while recording:
     * a command buffer that is reset or abandoned never runs its barriers, so a target whose barrier went into one is
     * not initialized, and the next recording carries the barrier again. Calling this for a recording that did not
     * reach the queue would make the chain believe an image is in {@code GENERAL} while it is still
     * {@code UNDEFINED} - which is exactly the state the flag exists to prevent.
     * <p>
     * The token is what makes that a check rather than a promise: only the recording that owns
     * {@link #pendingInit} can commit it. A commit naming any other recording - a later buffer's success, a buffer
     * whose recording was already replaced, reset or abandoned - is counted in {@link #staleCommits} and commits
     * nothing, so no submission can make another submission's layouts fact.
     */
    public void commitPasses(int recordingToken) {
        if (recordingToken != this.pendingRecording) {
            this.staleCommits++;
            trace("STALE_COMMIT token=" + recordingToken + " pendingRecording=" + this.pendingRecording
                    + " pendingInit=" + this.pendingInit.size() + ": the submission that succeeded did not carry this"
                    + " recording's barriers, so none of them become fact here");
            return;
        }
        this.commits++;
        for (Target target : this.pendingInit) {
            target.initialized = true;
        }
        this.pendingInit.clear();
    }

    /**
     * Commits the recording whose targets are pending, without naming it.
     * <p>
     * Equivalent to {@code commitPasses(pendingRecording)} and therefore only correct when the call site is the
     * successful submission of the recording {@link #recordPasses} returned last. The token form
     * ({@link #commitPasses(int)}) is the contract: it is the one that refuses a commit arriving from another buffer's
     * success. Kept because a caller that reports submissions but does not thread the token still gets the
     * re-record-on-abandon behaviour.
     */
    public void commitPasses() {
        commitPasses(this.pendingRecording);
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
     * {@return the reason this chain cannot be run natively, or null when every image it reads from outside itself
     * can be supplied}.
     * <p>
     * "From outside itself" is the whole rule. An input resolves to one of exactly three real images:
     * <ul>
     *   <li>the group's main framebuffer ({@link Pass#inIsMain}, {@code auxFbIds[0] == 0}) - the shared frame image
     *       the caller acquired, which holds what the game drew;</li>
     *   <li>a target the caller adopted under the identity the pass carries ({@link #adoptTarget}) - the caller's own
     *       image, already in {@code GENERAL} with its content live;</li>
     *   <li>a target an <em>earlier pass of this same chain</em> wrote - a private intermediate, legitimately
     *       allocated here.</li>
     * </ul>
     * Anything else is an identity only GL has ever written: under the GL bridge the chain's input is a real GL
     * framebuffer whose pixels this renderer never copied, and no image was adopted for it. Allocating a target for it
     * would make the chain sample an empty image and its first pass would produce the deterministic result of reading
     * nothing - the failure the audit's N04 measured (19 chains, 19 stand-ins, black picture) while reporting the
     * chain as run. Refusing instead sends the caller back to the GL chain, which has the image.
     * <p>
     * The check is deliberately structural rather than a counter: it walks the pass list in order, so a chain that
     * reads its own intermediate targets is accepted even though those identities were unknown to this class a moment
     * ago, and a chain whose first read is unknown is refused whether or not the caller reported an identity for it.
     */
    public String unsuppliedInput(List<Pass> passes) {
        if (passes == null || passes.isEmpty()) {
            return "empty pass list";
        }
        Set<Integer> produced = new LinkedHashSet<>();
        for (int index = 0; index < passes.size(); ++index) {
            Pass pass = passes.get(index);
            String refusal = unsuppliedInput(index, pass, produced, pass.inIsMain, pass.inFbId, pass.inW, pass.inH);
            if (refusal != null) {
                this.preflightRefusals++;
                return refusal;
            }
            if (pass.auxFbIds.length > 0) {
                boolean auxIsMain = pass.auxFbIds[0] == 0;
                refusal = unsuppliedInput(index, pass, produced, auxIsMain, pass.auxFbIds[0], pass.auxW[0],
                        pass.auxH[0]);
                if (refusal != null) {
                    this.preflightRefusals++;
                    return refusal;
                }
            }
            if (!pass.outIsMain) {
                produced.add(Integer.valueOf(pass.outFbId));
            }
        }
        return null;
    }

    /** {@return the refusal for one pass side, or null when this recording or an adoption supplies it}. */
    private String unsuppliedInput(int index, Pass pass, Set<Integer> produced, boolean isMain, int identity,
            int width, int height) {
        if (isMain) {
            // The group's main framebuffer is the frame image: the one image the executor is handed for free.
            return null;
        }
        if (produced.contains(Integer.valueOf(identity))) {
            return null;
        }
        Target target = this.targets.get(Integer.valueOf(identity));
        if (target != null && !target.owned) {
            return null;
        }
        return "pass " + index + " (" + pass.program + ") reads framebuffer identity=" + identity + " " + width + "x"
                + height + ", which no earlier pass of this chain writes and no image was adopted under: the chain's"
                + " real input is not available to this renderer";
    }

    /** {@return how many identities currently resolve to an image the caller adopted}. */
    private int adoptedCount() {
        int adopted = 0;
        for (Target target : this.targets.values()) {
            if (!target.owned) {
                adopted++;
            }
        }
        return adopted;
    }

    /**
     * Notes - and prints once - the case that used to be a stand-in run: the caller reported an identity whose image
     * it meant to be adopted and nothing was adopted under it. The chain is refused (never run), so this counts
     * refusals, not runs, and the identity, the GL texture name and the size the caller reported are carried because
     * they are everything an adoption needs.
     */
    private void noteReportedButUnadoptedInput(List<Pass> passes) {
        if (passes.isEmpty() || passes.get(0).groupMainFbId == 0) {
            return;
        }
        Target adopted = this.targets.get(Integer.valueOf(passes.get(0).groupMainFbId));
        if (adopted != null && !adopted.owned) {
            return;
        }
        this.standInRefusals++;
        if (!this.standInReported) {
            this.standInReported = true;
            Pass first = passes.get(0);
            System.out.println("[Celtium] CELTIUM_POST_ADOPT_REFUSED identity=" + first.groupMainFbId
                    + " texture=" + first.groupMainTextureId + " " + first.groupMainW + "x" + first.groupMainH
                    + " adopted=false (no image under this identity: the chain is refused rather than run on an empty"
                    + " stand-in target)");
        }
    }

    /**
     * One pass's output image from the recording {@link #recordPasses} made last, in pass order.
     * <p>
     * This exists for the chain-time image dump: the question "did pass 0 produce anything" cannot be answered from
     * the frame image (the last pass is the one that writes there), so the caller needs each pass's output, with the
     * pass index that makes a line attributable and the label that says whether the image is one this chain
     * allocated, one the caller adopted, or the shared frame image.
     */
    public static final class RecordedOutput {
        /** Index of the pass in the recorded chain, from 0. */
        public final int passIndex;
        /** The pass's program id, as the collector named it. */
        public final String program;
        /** {@code "main"}, {@code "owned"} or {@code "adopted"}: which kind of image this output is. */
        public final String label;
        /** The output image, or 0 for {@code main} - the caller already holds the frame image and its view. */
        public final long image;
        /** The output view, or 0 for {@code main}. */
        public final long view;
        public final int width;
        public final int height;

        RecordedOutput(int passIndex, String program, String label, long image, long view, int width, int height) {
            this.passIndex = passIndex;
            this.program = program;
            this.label = label;
            this.image = image;
            this.view = view;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return "pass " + this.passIndex + " " + this.program + " out=" + this.label + " image=0x"
                    + Long.toHexString(this.image) + " " + this.width + "x" + this.height;
        }
    }

    /**
     * The output images of the recording {@link #recordPasses} made last, in pass order.
     * <p>
     * Readable as soon as {@code recordPasses} returns and before {@link #commitPasses(int)}: the caller's dump runs
     * right after the submission, and an output that is only correct after the commit would be a measurement taken a
     * step too late. The list is replaced by each recording and is empty when no recording has been made.
     */
    public List<RecordedOutput> lastRecordedOutputs() {
        return java.util.Collections.unmodifiableList(this.lastRecordedOutputs);
    }

    /** Outputs of the last {@link #recordPasses}; see {@link #lastRecordedOutputs()}. */
    private final List<RecordedOutput> lastRecordedOutputs = new ArrayList<>();

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
                + ", postUboAvailable=" + (this.uniformBlocks != null)
                + ", postUboPrograms=" + this.uboPrograms.size()
                + ", postUboProgramNames=[" + String.join(",", this.uboPrograms) + "]"
                + ", postUboPasses=" + this.uboPasses
                + ", postUboBlockBytes=" + (this.uniformBlocks == null ? 0 : this.uniformBlockBytes)
                + ", postUboBytesWritten=" + this.uboBytesWritten
                + ", postUboRequests=" + this.uboRequests
                + ", postUboRefusals=" + this.programRefusals.size()
                + ", postBlockLayoutRefusals=" + this.blockLayoutRefusals
                + ", postRunsOnStandIn=" + this.runsOnAllocatedStandIn
                + ", postRefusedUnsuppliedInput=" + this.refusedInputs
                + ", postUnsuppliedInputPreflights=" + this.preflightRefusals
                + ", postStandInRefusals=" + this.standInRefusals
                + ", postRecordingInitBarriers=" + this.initBarriers + ", postRecordingCommits=" + this.commits
                + ", postStaleCommits=" + this.staleCommits
                + ", postUncommittedRecordings=" + this.uncommittedRecordings
                + ", postRepeatInits=" + this.repeatInits
                + ", postRingExhaustions=" + this.ringExhaustions
                + ", postRingCapacity=" + this.setRing.length
                + ", postAdoptedIds=[" + adoptedIds + "]";
    }

    /**
     * The refusal {@link #recordPasses} raises when a chain's real input cannot be supplied. Distinct from a broken
     * pipeline: the caller's answer is to keep the GL chain, not to fix or disable anything.
     */
    public static final class UnsuppliedInputException extends IllegalStateException {
        UnsuppliedInputException(String reason) {
            super(reason);
        }
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
     * Updates the next ring set with this pass's views and returns the slot it took: the index of both the set and
     * the uniform-block slot bound to it. A set is updated only after the command buffer that last bound it finished -
     * each pass takes a fresh slot, so nothing here is bound in the buffer being recorded.
     */
    private int descriptorSet(long inView, long auxView) {
        VkDevice device = this.context.device();
        if (this.setsThisRecording >= this.setRing.length) {
            // Refused rather than wrapped. The alternative is to update a set that a not-yet-submitted recording
            // already bound, which changes the image a pass reads and shows up as a wrong glow rather than as an
            // error - the shape of failure this project keeps having to un-learn. Nothing has been updated here, so
            // the caller can still hand the frame back to GL, which is what the caller's catch does with the
            // UnsuppliedInputException next to it. The uniform blocks are taken with the sets, so this one refusal
            // covers both: a wrapped slot would also overwrite the block a pending recording reads.
            this.ringExhaustions++;
            throw new IllegalStateException("post chain descriptor ring exhausted: " + this.setRing.length
                    + " sets are already bound by the recording in progress (recording=" + this.pendingRecording
                    + ", passes so far=" + this.setsThisRecording + ")");
        }
        int slot = this.setCursor;
        long set = this.setRing[slot];
        this.setCursor = (this.setCursor + 1) % this.setRing.length;
        this.setsThisRecording++;
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
            // Binding 2 is not written here: it names this slot's uniform block for the chain's whole life, set once
            // at creation. Only the block's contents change per pass, through the mapping below.
            vkUpdateDescriptorSets(device, writes, null);
            return slot;
        }
    }

    /**
     * Writes this pass's packed block into {@code slot}'s uniform block.
     * <p>
     * The bytes go in at the offsets the collector packed them at, which {@link #blockRefusal} has compared with the
     * offsets the shader declares - so the copy is a straight one and no member is translated here. A member the
     * shader declares beyond everything the collector packed stays zero: that is the value GL would show for an unset
     * uniform, and this route can produce it exactly, unlike a push whose unwritten tail is whatever the last push
     * left in the driver's storage.
     * <p>
     * The slot is the one the pass's descriptor set was just updated with, and the ring refuses to hand the same slot
     * out twice inside one recording, so this write cannot reach a block a pending command buffer still reads.
     */
    private void writeUniformBlock(int slot, Pass pass, Program program) {
        int base = (int) (slot * this.uniformStride) / 4;
        int floats = pass.pushBytes / 4;
        for (int i = 0; i < floats; ++i) {
            this.uniformView.put(base + i, pass.push[i]);
        }
        for (int i = floats; i < program.blockBytes / 4; ++i) {
            this.uniformView.put(base + i, 0.0f);
        }
        this.uboPasses++;
        this.uboBytesWritten += program.blockBytes;
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
        if (this.uniformBlocks != null) {
            // After the wait above and after every set that named it is gone with the pool.
            this.uniformBlocks.delete();
        }
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

    /**
     * One program: the pipeline, the pipeline layout, and the block declaration the two shader modules of the route
     * agree on. The push range and the block's size are read from those modules rather than from a table beside them:
     * a table is a second copy of the layout that nothing compares with the shaders, which is how a program can push a
     * block whose members the shader does not read there.
     */
    private static final class Program {
        final long pipeline;
        final long layout;
        /** The push-constant range this pipeline declares; 0 on the uniform-buffer route, which pushes nothing. */
        final int pushRange;
        /** Whether this program's block travels in the ring buffer instead of push constants. */
        final boolean uniformBlock;
        /** Declared member name -> byte offset, merged across both stages (they must agree where they overlap). */
        final Map<String, Integer> blockMembers;
        /** The end of the declared block: the furthest byte any of the two modules' members occupies. */
        final int blockBytes;

        Program(long pipeline, long layout, int pushRange, boolean uniformBlock, Map<String, Integer> blockMembers,
                int blockBytes) {
            this.pipeline = pipeline;
            this.layout = layout;
            this.pushRange = pushRange;
            this.uniformBlock = uniformBlock;
            this.blockMembers = blockMembers;
            this.blockBytes = blockBytes;
        }

        /**
         * @param packedBytes the block length the collector reported for this program's first pass; the push range
         *     covers it as well as the declaration, because {@code vkCmdPushConstants} may not push bytes no range of
         *     the layout contains (VUID-vkCmdPushConstants-offset-00374)
         * @param uniformBlockBytes the size every slot of the uniform ring is bound with; a declared block larger
         *     than that could not be bound and is refused here rather than at the draw
         */
        static Program create(VulkanContext context, long renderPass, long setLayout, boolean uniformBlock,
                String vertex, String fragment, int packedBytes, int uniformBlockBytes) {
            VkDevice device = context.device();
            // The block the shader modules of this route declare. For the buffer route the variant is compared with the
            // push module it mirrors member by member: a variant that moved, renamed or retyped a member is not the
            // same block, and the comparison is the only thing that says so.
            SpirvBlock declared = mergedBlock(uniformBlock ? uboVariant(vertex) : vertex,
                    uniformBlock ? uboVariant(fragment) : fragment);
            if (uniformBlock) {
                SpirvBlock push = mergedBlock(vertex, fragment);
                if (!push.offsets.equals(declared.offsets) || push.bytes != declared.bytes) {
                    throw new IllegalStateException("the uniform-buffer variant of " + fragment + " declares "
                            + declared.offsets + " (" + declared.bytes + "B), the push variant declares "
                            + push.offsets + " (" + push.bytes + "B): the variant is not the same block");
                }
                if (declared.bytes > uniformBlockBytes) {
                    throw new IllegalStateException("the uniform-buffer variant of " + fragment + " declares "
                            + declared.bytes + "B, the ring's slots are bound with " + uniformBlockBytes + "B");
                }
            }
            int pushRange = uniformBlock ? 0 : Math.max(declared.bytes, packedBytes);
            try (MemoryStack stack = stackPush()) {
                LongBuffer handle = stack.mallocLong(1);
                LongBuffer setLayouts = stack.longs(setLayout);
                VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                        .pSetLayouts(setLayouts);
                if (!uniformBlock) {
                    VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
                    range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                            .offset(0).size(pushRange);
                    layoutInfo.pPushConstantRanges(range);
                }
                check(vkCreatePipelineLayout(device, layoutInfo, null, handle),
                        "vkCreatePipelineLayout(post " + fragment + ")");
                long layout = handle.get(0);

                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT)
                        .module(createShaderModule(stack, device, uniformBlock ? uboVariant(vertex) : vertex))
                        .pName(stack.UTF8("main"));
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                        .module(createShaderModule(stack, device, uniformBlock ? uboVariant(fragment) : fragment))
                        .pName(stack.UTF8("main"));

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
                return new Program(pipelineHandle.get(0), layout, pushRange, uniformBlock, declared.offsets,
                        declared.bytes);
            }
        }
    }

    /**
     * The uniform block shader modules declare together: every member's name, byte offset and size, read from the
     * modules' SPIR-V.
     * <p>
     * Only the types the post-chain blocks use are understood - scalars, vectors and matrices - and anything else
     * raises rather than being sized by a guess, which refuses the route using it. A member the two stages place
     * differently also raises: one binding holds one block, so two stages disagreeing about it is not a block at all.
     */
    static final class SpirvBlock {
        /** Opcodes this reader interprets; every other instruction is skipped by its own word count. */
        private static final int OP_MEMBER_NAME = 6;
        private static final int OP_TYPE_INT = 21;
        private static final int OP_TYPE_FLOAT = 22;
        private static final int OP_TYPE_VECTOR = 23;
        private static final int OP_TYPE_MATRIX = 24;
        private static final int OP_TYPE_STRUCT = 30;
        private static final int OP_TYPE_POINTER = 32;
        private static final int OP_VARIABLE = 59;
        private static final int OP_MEMBER_DECORATE = 72;
        private static final int DECORATION_MATRIX_STRIDE = 7;
        private static final int DECORATION_OFFSET = 35;
        private static final int STORAGE_UNIFORM = 2;
        private static final int STORAGE_PUSH_CONSTANT = 9;
        /** The SPIR-V header is five words; instructions start after it. */
        private static final int HEADER_WORDS = 5;

        /** Declared member name -> byte offset, in declaration order. */
        final Map<String, Integer> offsets = new LinkedHashMap<>();
        /** The end of the block: the furthest byte any member occupies. */
        final int bytes;

        private SpirvBlock(Map<String, Integer> offsets, int bytes) {
            this.offsets.putAll(offsets);
            this.bytes = bytes;
        }

        /**
         * {@return the block {@code module} declares}, read from the compiled module the driver is handed.
         */
        static SpirvBlock of(String module) {
            ByteBuffer code = SpirvShaders.load(module);
            try {
                return read(module, code);
            } finally {
                MemoryUtil.memFree(code);
            }
        }

        /**
         * {@return the block {@code code} declares}. Reads the module's words in the buffer's own byte order,
         * so the classpath loader's native-order buffer and a test's little-endian wrapper both work.
         */
        static SpirvBlock read(String module, ByteBuffer code) {
            int words = code.remaining() / 4;
            Map<Long, String> names = new HashMap<>();
            Map<Long, Integer> memberOffsets = new HashMap<>();
            Map<Long, Integer> memberStrides = new HashMap<>();
            Map<Integer, int[]> pointers = new HashMap<>();
            Map<Integer, int[]> structs = new HashMap<>();
            Map<Integer, Integer> scalars = new HashMap<>();
            Map<Integer, int[]> vectors = new HashMap<>();
            Map<Integer, int[]> matrices = new HashMap<>();
            Set<Integer> blockVariables = new LinkedHashSet<>();
            int index = HEADER_WORDS;
            while (index < words) {
                int instruction = code.getInt(index * 4);
                int wordCount = instruction >>> 16;
                int opcode = instruction & 0xFFFF;
                if (wordCount <= 0 || index + wordCount > words) {
                    throw new IllegalStateException("SPIR-V module " + module + " has a malformed instruction at word "
                            + index);
                }
                switch (opcode) {
                    case OP_MEMBER_NAME:
                        names.put(key(code.getInt((index + 1) * 4), code.getInt((index + 2) * 4)),
                                literal(code, index + 3, wordCount - 3));
                        break;
                    case OP_MEMBER_DECORATE: {
                        long member = key(code.getInt((index + 1) * 4), code.getInt((index + 2) * 4));
                        int decoration = code.getInt((index + 3) * 4);
                        if (decoration == DECORATION_OFFSET) {
                            memberOffsets.put(member, Integer.valueOf(code.getInt((index + 4) * 4)));
                        } else if (decoration == DECORATION_MATRIX_STRIDE) {
                            memberStrides.put(member, Integer.valueOf(code.getInt((index + 4) * 4)));
                        }
                        break;
                    }
                    case OP_TYPE_INT:
                    case OP_TYPE_FLOAT: {
                        int width = code.getInt((index + 2) * 4);
                        if (width != 32) {
                            throw new IllegalStateException("SPIR-V module " + module + " declares a " + width
                                    + "-bit scalar; the post chain's blocks are 32-bit");
                        }
                        scalars.put(Integer.valueOf(code.getInt((index + 1) * 4)), Integer.valueOf(width));
                        break;
                    }
                    case OP_TYPE_VECTOR:
                        vectors.put(Integer.valueOf(code.getInt((index + 1) * 4)), new int[] {
                                code.getInt((index + 2) * 4), code.getInt((index + 3) * 4) });
                        break;
                    case OP_TYPE_MATRIX:
                        matrices.put(Integer.valueOf(code.getInt((index + 1) * 4)), new int[] {
                                code.getInt((index + 2) * 4), code.getInt((index + 3) * 4) });
                        break;
                    case OP_TYPE_STRUCT: {
                        int[] members = new int[wordCount - 2];
                        for (int i = 0; i < members.length; ++i) {
                            members[i] = code.getInt((index + 2 + i) * 4);
                        }
                        structs.put(Integer.valueOf(code.getInt((index + 1) * 4)), members);
                        break;
                    }
                    case OP_TYPE_POINTER:
                        pointers.put(Integer.valueOf(code.getInt((index + 1) * 4)), new int[] {
                                code.getInt((index + 2) * 4), code.getInt((index + 3) * 4) });
                        break;
                    case OP_VARIABLE: {
                        int[] pointer = pointers.get(Integer.valueOf(code.getInt((index + 1) * 4)));
                        int storage = code.getInt((index + 3) * 4);
                        if (pointer != null && pointer[0] == storage
                                && (storage == STORAGE_PUSH_CONSTANT || storage == STORAGE_UNIFORM)
                                && structs.containsKey(Integer.valueOf(pointer[1]))) {
                            blockVariables.add(Integer.valueOf(pointer[1]));
                        }
                        break;
                    }
                    default:
                        break;
                }
                index += wordCount;
            }
            if (blockVariables.isEmpty()) {
                throw new IllegalStateException("SPIR-V module " + module
                        + " declares no push-constant or uniform block");
            }
            if (blockVariables.size() > 1) {
                throw new IllegalStateException("SPIR-V module " + module + " declares " + blockVariables.size()
                        + " blocks; the post chain's shaders declare one");
            }
            int block = blockVariables.iterator().next().intValue();
            int[] members = structs.get(Integer.valueOf(block));
            Map<String, Integer> offsets = new LinkedHashMap<>();
            int end = 0;
            for (int member = 0; member < members.length; ++member) {
                String name = names.get(key(block, member));
                Integer offset = memberOffsets.get(key(block, member));
                if (name == null || offset == null) {
                    throw new IllegalStateException("SPIR-V module " + module + " declares block member " + member
                            + " without a name or an offset decoration");
                }
                int size = sizeOf(module, members[member], memberStrides.get(key(block, member)), scalars, vectors,
                        matrices);
                offsets.put(name, offset);
                end = Math.max(end, offset.intValue() + size);
            }
            return new SpirvBlock(offsets, end);
        }

        private static int sizeOf(String module, int type, Integer matrixStride, Map<Integer, Integer> scalars,
                Map<Integer, int[]> vectors, Map<Integer, int[]> matrices) {
            Integer scalar = scalars.get(Integer.valueOf(type));
            if (scalar != null) {
                return scalar.intValue() / 8;
            }
            int[] vector = vectors.get(Integer.valueOf(type));
            if (vector != null) {
                Integer component = scalars.get(Integer.valueOf(vector[0]));
                if (component != null) {
                    return vector[1] * (component.intValue() / 8);
                }
            }
            int[] matrix = matrices.get(Integer.valueOf(type));
            if (matrix != null) {
                int[] column = vectors.get(Integer.valueOf(matrix[0]));
                if (column != null) {
                    Integer component = scalars.get(Integer.valueOf(column[0]));
                    if (component != null) {
                        // The stride between columns is decorated on the member; a matrix whose columns are not
                        // decorated is not a layout this reader will guess at.
                        if (matrixStride == null) {
                            throw new IllegalStateException("SPIR-V module " + module
                                    + " declares a matrix member without a MatrixStride decoration");
                        }
                        return matrix[1] * matrixStride.intValue();
                    }
                }
            }
            throw new IllegalStateException("SPIR-V module " + module + " declares block member type " + type
                    + ", which is neither a 32-bit scalar, a vector of them, nor a matrix of them");
        }

        /** The two-stage merge: the same member at two offsets is a disagreement, not a block. */
        static SpirvBlock merge(SpirvBlock first, SpirvBlock second) {
            Map<String, Integer> offsets = new LinkedHashMap<>(first.offsets);
            for (Map.Entry<String, Integer> member : second.offsets.entrySet()) {
                Integer previous = offsets.put(member.getKey(), member.getValue());
                if (previous != null && !previous.equals(member.getValue())) {
                    throw new IllegalStateException("the two stages declare " + member.getKey() + " at "
                            + previous + "B and " + member.getValue() + "B: one binding holds one block");
                }
            }
            return new SpirvBlock(offsets, Math.max(first.bytes, second.bytes));
        }

        private static long key(int type, int member) {
            return ((long) type << 32) | (member & 0xFFFFFFFFL);
        }

        /** The UTF-8 name a literal string's words carry. */
        private static String literal(ByteBuffer code, int firstWord, int wordCount) {
            byte[] bytes = new byte[wordCount * 4];
            for (int word = 0; word < wordCount; ++word) {
                int value = code.getInt((firstWord + word) * 4);
                bytes[word * 4] = (byte) value;
                bytes[word * 4 + 1] = (byte) (value >>> 8);
                bytes[word * 4 + 2] = (byte) (value >>> 16);
                bytes[word * 4 + 3] = (byte) (value >>> 24);
            }
            int end = 0;
            while (end < bytes.length && bytes[end] != 0) {
                ++end;
            }
            return new String(bytes, 0, end, StandardCharsets.UTF_8);
        }
    }

    /** {@return the block two shader modules declare together, or throws when they disagree}. */
    private static SpirvBlock mergedBlock(String vertex, String fragment) {
        return SpirvBlock.merge(SpirvBlock.of(vertex), SpirvBlock.of(fragment));
    }
}
