package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * The first pipeline for Minecraft's own immediate-mode geometry: untextured {@code POSITION_COLOR} vertices, the
 * format a mod's {@code BufferBuilder.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR)} produces.
 * <p>
 * It is deliberately a single fixed state combination (depth test on with LEQUAL, depth writes off, straight alpha
 * blending on) rather than the full deferred selection from the state model. That keeps the first slice honest and
 * measurable: the batches it can draw are the ones whose state matches, everything else keeps being refused with a
 * reason. The state-driven pipeline cache replaces it once the draw path is proven end to end.
 * <p>
 * No texture is bound - the format has no texture coordinates - so the pipeline has no descriptor sets at all and
 * nothing but the 128 bytes of push constants the terrain and UI pipelines also use.
 * <p>
 * Threading: render thread only.
 */
public final class VulkanImmediateBlockPipeline {

    /** Bounded census of the layout a descriptor is actually written with, for VUID-vkCmdDraw-None-09600. */
    private static int descLayoutLogs;

    /** Prints the layout of the descriptor write about to be issued, bounded, so the offending write names itself. */
    static void celtium$noteDescWrite(org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer write) {
        if (descLayoutLogs >= 24 || write == null || write.remaining() == 0) {
            return;
        }
        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer images = write.get(0).pImageInfo();
        int layout = images == null ? -1 : images.get(0).imageLayout();
        descLayoutLogs++;
        String name = layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL ? "GENERAL"
                : layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL ? "SHADER_READ_ONLY"
                : layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL ? "TRANSFER_DST"
                : Integer.toString(layout);
        System.out.println("[Celtium] CELTIUM_DESC_WRITE layout=" + name + " images="
                + (images == null ? 0 : images.remaining()));
    }

    /** The BLOCK family: 3 floats position, 4 unsigned bytes colour, 2 floats uv, 2 unsigned shorts lightmap. */
    public static final int VERTEX_BYTES = 28;
    // 116, not 132: projection 64 + the model view's three affine columns 48 + alpha reference 4, all
    // inside the 128 bytes Vulkan guarantees (audit S19).
    private static final int PUSH_CONSTANT_BYTES = 128;

    /** Elides a push whose bytes are already in this command buffer (see {@link VulkanPushCache}). */
    private final VulkanPushCache pushCache = VulkanPushCache.SHARED;

    private final VulkanContext context;
    private final long renderPass;

    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;
    private long descriptorSetLayout = VK_NULL_HANDLE;

    // ---- Native shadow map, shared by every instance of this family (the same arrangement as the item family) --------
    private static org.soutalab.celtium.vulkan.VulkanHostBuffer shadowMatrixBuffer;
    private static long shadowMapView;
    private static long shadowMapSampler;
    private static long shadowPlaceholderView;
    private static long shadowPlaceholderSampler;
    private static long shadowPlaceholderImage;
    private static long shadowPlaceholderMemory;
    /** Pools whose placeholder transition did not complete in time; see #403 and {@link PendingCommandPools}. */
    private static final org.soutalab.celtium.vulkan.PendingCommandPools PENDING_TRANSITIONS =
            new org.soutalab.celtium.vulkan.PendingCommandPools();
    private static final java.util.List<VulkanImmediateBlockPipeline> SHADOW_INSTANCES =
            new java.util.ArrayList<VulkanImmediateBlockPipeline>();
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;
    // Primitive long keys, as in the texture family: one lookup per immediate batch.
    /**
     * Sets the shadow-source change displaced, plus the count: they may still be referenced by recorded or pending command
     * buffers, so they are released by the same completion-gated path that serves eviction rather than destroyed here.
     */
    private final java.util.List<Long> retiredSets = new java.util.ArrayList<Long>();
    private int retiredSetsCount;

    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap setsByTextureView =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    /** How many descriptor sets this pipeline has had to build; a rise means keys stopped matching. */
    public long setsCreated;
    /** Batches refused because the texture or light map they would sample has no view or sampler any more. */
    public long zeroViewRefusals;

    private final VulkanColorBlendState colorBlend;
    private final boolean depthTest;
    private final boolean depthWrite;
    private final boolean cull;
    /** The Vulkan compare op the mirrored {@code glDepthFunc} mapped to. */
    private final int depthCompareOp;

    private VulkanImmediateBlockPipeline(VulkanContext context, long renderPass, VulkanColorBlendState colorBlend,
                                           boolean depthTest, boolean depthWrite, boolean cull, int depthCompareOp) {
        this.context = context;
        this.renderPass = renderPass;
        this.colorBlend = colorBlend;
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        this.cull = cull;
        this.depthCompareOp = depthCompareOp;
    }

    /** Creates a pipeline for straight-alpha blending, which is what most of vanilla's immediate content uses. */
    public static VulkanImmediateBlockPipeline create(VulkanContext context, long renderPass) {
        return create(context, renderPass, VulkanColorBlendState.legacy(), true, false, false,
                VK_COMPARE_OP_LESS_OR_EQUAL);
    }

    /**
     * Creates a pipeline for one drawing state: the blend factors, blend equations and colour write mask the batch was
     * captured under (BH-07, BH-10), the depth test and its write mask, and face culling. These bake into the pipeline
     * object, which is why the immediate path caches one per state instead of forcing every batch through a single
     * hardcoded combination.
     */
    public static VulkanImmediateBlockPipeline create(VulkanContext context, long renderPass,
                            VulkanColorBlendState colorBlend, boolean depthTest, boolean depthWrite, boolean cull,
                            int depthCompareOp) {
        if (context == null || renderPass == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("context and renderPass must be set");
        }
        if (colorBlend == null) {
            throw new IllegalArgumentException("colorBlend must be set");
        }
        VulkanImmediateBlockPipeline result = new VulkanImmediateBlockPipeline(context, renderPass, colorBlend,
                depthTest, depthWrite, cull, depthCompareOp);
        result.createPipelineLayout();
        result.createPipeline();
        return result;
    }

    /** Binds the pipeline, pushes the matrices and records the mesh's batch. */
    public void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, ByteBuffer pushConstants,
                     long descriptorSetToBind, long[] bindCache) {
        if (this.pipeline == VK_NULL_HANDLE || mesh == null || mesh.isEmpty()) {
            return;
        }
        // OPT-12: pipeline and descriptor binds dedup against the flush's bind cache; a pipeline rebind
        // disturbs the set bound under the old layout, so the descriptor slot resets whenever this re-emits.
        if (bindCache[4] != this.pipeline) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
            bindCache[4] = this.pipeline;
            bindCache[5] = -1L;
        }
        if (bindCache[5] != descriptorSetToBind) {
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                    org.lwjgl.system.MemoryStack.stackGet().longs(descriptorSetToBind), null);
            bindCache[5] = descriptorSetToBind;
        }
        // Both stages: the vertex stage reads the matrices and the fragment stage the alpha-test reference, and the
        // command's stage flags have to cover every stage that reads the range.
        ByteBuffer clamped = pushConstants.duplicate();
        // The caller's block can be wider than this layout's declared range (the lit variant's is 208) - pushing
        // past the range is a validation error, so only the declared bytes go out.
        clamped.limit(Math.min(clamped.remaining(), PUSH_CONSTANT_BYTES));
        if (this.pushCache.shouldPush(commandBuffer.address(), this.pipelineLayout, clamped)) {
            vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, clamped);
            this.pushCache.remember(commandBuffer.address(), this.pipelineLayout, clamped);
        }
        // The mesh binds its own streams at their own offsets and draws: an owning mesh binds at 0, a frame-arena
        // borrow at its slice offset. Keeping the three calls in one place is what stops a variant from binding a
        // slice at 0 and drawing another group's vertices (OPT-05).
        mesh.bindAndDraw(commandBuffer, bindCache);
    }

    public int vertexBytes() {
        return VERTEX_BYTES;
    }

    /**
     * {@return the descriptor set that samples the given texture}, creating and writing it on first use.
     * <p>
     * Each texture gets its own set, written **before** it is ever bound, because updating a set that a recording
     * command buffer has already bound is invalid without {@code UPDATE_AFTER_BIND} - and the validation layer said so
     * precisely: "VkDescriptorSet was destroyed or updated without UPDATE_AFTER_BIND", after which every following
     * command in that buffer failed with "commandBuffer must be in the recording state". Returns 0 when the pool is
     * exhausted, so the caller refuses the batch instead of drawing with a stale binding.
     */
    public long descriptorSetFor(VulkanGlTexture texture, VulkanGlTexture lightmap) {
        if (lightmap == null) {
            return 0L;
        }
        if (this.descriptorPool == VK_NULL_HANDLE || texture == null) {
            return 0L;
        }
        // A wrapper whose view or sampler has been released must not be written into a descriptor: the measured result is
        // `u_Texture is using imageView VkImageView 0x0 that is invalid or has been destroyed`
        // (VUID-vkCmdDrawIndexed-None-08114), and RADV answers the frame with VK_ERROR_DEVICE_LOST. The particle pipeline
        // already refuses this case and this one did not - which is why the guard on the texture and item pipelines did
        // not fire while 08114 still did.
        if (texture.view() == VK_NULL_HANDLE || texture.sampler() == VK_NULL_HANDLE
                || lightmap.view() == VK_NULL_HANDLE || lightmap.sampler() == VK_NULL_HANDLE) {
            this.zeroViewRefusals++;
            return 0L;
        }
        // The light map is part of the key this time (audit S13). The mirror is refreshed every few frames, so its
        // view and generation change while the atlas's do not - keyed on the texture alone, a set built for an old
        // light map would be served for a new one.
        long descriptorKey = (texture.key() * 31L) + (lightmap != null ? lightmap.key() : 0L);
        if (this.setsByTextureView.containsKey(descriptorKey)) {
            return this.setsByTextureView.get(descriptorKey);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                    org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                            .descriptorPool(this.descriptorPool)
                            .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer handle = stack.mallocLong(1);
            if (vkAllocateDescriptorSets(this.context.device(), allocate, handle) != VK_SUCCESS) {
                return 0L;
            }
            long set = handle.get(0);
            VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack);
            image.get(0).imageLayout(texture.samplingLayout())
                    .imageView(texture.view())
                    .sampler(texture.sampler());
            VkDescriptorImageInfo.Buffer lightImage = VkDescriptorImageInfo.calloc(1, stack);
            lightImage.get(0).imageLayout(lightmap.samplingLayout())
                    .imageView(lightmap.view())
                    .sampler(lightmap.sampler());
            org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer write =
                    org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(2, stack);
            write.get(1).sType$Default()
                    .dstSet(set)
                    .dstBinding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(lightImage);
            write.get(0).sType$Default()
                    .dstSet(set)
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(image);
            celtium$noteDescWrite(write);
            vkUpdateDescriptorSets(this.context.device(), write, null);
            ensureShadowResources();
            writeShadowBindings(stack, set);
            this.setsByTextureView.put(descriptorKey, set);
            this.setsCreated++;
            return set;
        } catch (RuntimeException | LinkageError error) {
            return 0L;
        }
    }

    public void delete() {
        // A deleted pipeline must not stay reachable from the static list that fans shadow sources out to live
        // instances: reload and variant churn left deleted pipelines registered, and a later source change wrote through
        // their descriptor sets (#400).
        SHADOW_INSTANCES.remove(this);
        if (this.pipeline != VK_NULL_HANDLE) {
            this.context.celtium$notePipelineDestroy("immediateblockpipeline");
            vkDestroyPipeline(this.context.device(), this.pipeline, null);
            this.pipeline = VK_NULL_HANDLE;
        }
        this.setsByTextureView.clear();
        // Retired sets are pools' to free: the pool teardown below releases them with every other set this pipeline
        // allocated, which is why this list only has to keep them reachable until then.
        this.retiredSets.clear();
        if (this.descriptorPool != VK_NULL_HANDLE) {
            this.context.celtium$noteDescriptorDestroy("block.delete");
vkDestroyDescriptorPool(this.context.device(), this.descriptorPool, null);
            this.descriptorPool = VK_NULL_HANDLE;
            this.descriptorSet = VK_NULL_HANDLE;
        }
        if (this.descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(this.context.device(), this.descriptorSetLayout, null);
            this.descriptorSetLayout = VK_NULL_HANDLE;
        }
        if (this.pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(this.context.device(), this.pipelineLayout, null);
            this.pipelineLayout = VK_NULL_HANDLE;
        }
    }

    private void createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer binding =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(4, stack);
            binding.get(0).binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            binding.get(1).binding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            // The native shadow map: its eye-space matrix for the vertex stage and the map for both stages (the vertex
            // shader declares the sampler too, so the flags cover it).
            binding.get(2).binding(2)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            binding.get(3).binding(3)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutInfo =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                            .pBindings(binding);
            LongBuffer layoutHandle = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null, layoutHandle),
                    "vkCreateDescriptorSetLayout(immediate textured)");
            this.descriptorSetLayout = layoutHandle.get(0);
            org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer poolSize =
                    org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(2, stack);
            poolSize.get(1).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(8192);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)// The per-type count is what actually caps the pool: maxSets alone was raised and had no effect,
                            // because 128 combined-image-sampler descriptors at 2 per set is 64 sets however large maxSets
                            // is. The two are now sized together.
                            .descriptorCount(8192 * 2);
            // One set per distinct texture the frame binds; the pool refuses beyond that and the caller then refuses
            // the batch rather than drawing with the wrong image.
            org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                            // Sized for a session rather than for a scene: the set is cached per texture view and nothing
                            // evicts it, so 64 was reached in ordinary play and every later allocation failed - which is why the
                            // particle family, with the most distinct views, was taken and then dropped. A pool this size is
                            // the smallest change that stops exhaustion; eviction by texture generation is the proper fix
                            // and is recorded as open.
                            .maxSets(8192).pPoolSizes(poolSize);
            LongBuffer poolHandle = stack.mallocLong(1);
            check(vkCreateDescriptorPool(this.context.device(), poolInfo, null, poolHandle),
                    "vkCreateDescriptorPool(immediate textured)");
            this.descriptorPool = poolHandle.get(0);
            org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                    org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                            .descriptorPool(this.descriptorPool)
                            .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer setHandle = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(this.context.device(), allocate, setHandle),
                    "vkAllocateDescriptorSets(immediate textured)");
            this.descriptorSet = setHandle.get(0);
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
            // The fragment stage reads the alpha-test reference at offset 128, so the range must be visible to both
            // stages: declaring it for the vertex stage alone is a shader-interface mismatch.
            range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0).size(PUSH_CONSTANT_BYTES);
            VkPipelineLayoutCreateInfo create = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(this.descriptorSetLayout))
                    .pPushConstantRanges(range);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreatePipelineLayout(this.context.device(), create, null, handle),
                    "vkCreatePipelineLayout(immediate)");
            this.pipelineLayout = handle.get(0);
        }
    }

    private void createPipeline() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer vertexCode = SpirvShaders.load(SpirvShaders.IMMEDIATE_BLOCK_VERTEX);
            ByteBuffer fragmentCode = SpirvShaders.load(SpirvShaders.IMMEDIATE_BLOCK_FRAGMENT);
            long vertexModule = VK_NULL_HANDLE;
            long fragmentModule = VK_NULL_HANDLE;
            try {
                VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
                LongBuffer handle = stack.mallocLong(1);
                moduleInfo.pCode(vertexCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                        "vkCreateShaderModule(immediate vertex)");
                vertexModule = handle.get(0);
                moduleInfo.pCode(fragmentCode);
                check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                        "vkCreateShaderModule(immediate fragment)");
                fragmentModule = handle.get(0);

                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule)
                        .pName(stack.UTF8("main"));
                org.lwjgl.vulkan.VkSpecializationMapEntry.Buffer shadowEntry =
                        org.lwjgl.vulkan.VkSpecializationMapEntry.calloc(1, stack);
                shadowEntry.get(0).constantID(0).offset(0).size(4);
                java.nio.ByteBuffer shadowValue = stack.calloc(4);
                shadowValue.putInt(0, Boolean.getBoolean("celtium.shadow.enabled") ? 1 : 0);
                org.lwjgl.vulkan.VkSpecializationInfo shadowSpecialization =
                        org.lwjgl.vulkan.VkSpecializationInfo.calloc(stack).set(shadowEntry, shadowValue);
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                        .pSpecializationInfo(shadowSpecialization);

                VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0).binding(0).stride(VERTEX_BYTES).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(4, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R8G8B8A8_UNORM).offset(12);
                attributes.get(2).location(2).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(16);
                attributes.get(3).location(3).binding(0).format(VK_FORMAT_R16G16_UINT).offset(24);
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
                                // BH-11: the depth bias is command state (vkCmdSetDepthBias) so one pipeline serves every
                                // polygon offset the game sets; the enable bit is what has to be declared here, and it is a
                                // rasterization field - the multisample state next door has no such member.
                                .depthBiasEnable(true)
                                .cullMode(this.cull ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE)
                                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                                .lineWidth(1.0f);
                VkPipelineMultisampleStateCreateInfo multisample =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default()
                                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                // Depth test on so the geometry participates in the world, writes off because Minecraft's own
                // immediate path for this state does the same, and blending on so the alpha it carries is honoured.
                // Depth test back on with the comparison Minecraft's own immediate path uses (depth writes stay off,
                // which is what the state the fixture sets asks for). The previous round measured the quad appearing
                // with the test disabled; this run answers whether the comparison itself is what hides it.
                VkPipelineDepthStencilStateCreateInfo depth =
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                                .depthTestEnable(this.depthTest)
                                .depthWriteEnable(this.depthWrite)
                                .depthCompareOp(this.depthCompareOp);
                VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(1, stack);
                blendAttachment.get(0)
                        .blendEnable(this.colorBlend.blend())
                        .srcColorBlendFactor(this.colorBlend.srcColor())
                        .dstColorBlendFactor(this.colorBlend.dstColor())
                        .colorBlendOp(this.colorBlend.colorOp())
                        .srcAlphaBlendFactor(this.colorBlend.srcAlpha())
                        .dstAlphaBlendFactor(this.colorBlend.dstAlpha())
                        .alphaBlendOp(this.colorBlend.alphaOp())
                        .colorWriteMask(this.colorBlend.writeMask());
                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default().logicOpEnable(false).pAttachments(blendAttachment);
                VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR, VK_DYNAMIC_STATE_DEPTH_BIAS));

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
                        "vkCreateGraphicsPipelines(immediate)");
                this.pipeline = pipelineHandle.get(0);
            } finally {
                if (vertexModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.context.device(), vertexModule, null);
                }
                if (fragmentModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.context.device(), fragmentModule, null);
                }
                MemoryUtil.memFree(vertexCode);
                MemoryUtil.memFree(fragmentCode);
            }
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
    // ---- Native shadow map -----------------------------------------------------------------------------------------

    /** Creates the matrix block and the placeholder map on first use, and records this instance for later updates. */
    private void ensureShadowResources() {
        if (shadowMatrixBuffer == null) {
            shadowMatrixBuffer = new org.soutalab.celtium.vulkan.VulkanHostBuffer(this.context, 64L,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
            // Identity with the translation far away: a finite coordinate well outside the map, so a lookup with it is
            // skipped by comparison rather than by a non-finite divide.
            java.nio.FloatBuffer matrix = org.lwjgl.system.MemoryUtil.memFloatBuffer(
                    shadowMatrixBuffer.mappedAddress(), 16);
            for (int i = 0; i < 16; i++) {
                matrix.put(i, 0.0f);
            }
            matrix.put(0, 1.0f);
            matrix.put(5, 1.0f);
            matrix.put(10, 1.0f);
            matrix.put(15, 1.0f);
            matrix.put(12, -1.0e6f);
        }
        if (shadowPlaceholderView == 0L) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                org.lwjgl.vulkan.VkImageCreateInfo imageInfo = org.lwjgl.vulkan.VkImageCreateInfo.calloc(stack)
                        .sType$Default().imageType(org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D)
                        .format(org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT)
                        .extent(it -> it.set(1, 1, 1)).mipLevels(1).arrayLayers(1)
                        .samples(org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT)
                        .tiling(org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL)
                        .usage(org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                                | org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT)
                        .sharingMode(org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                java.nio.LongBuffer handle = stack.mallocLong(1);
                if (org.lwjgl.vulkan.VK10.vkCreateImage(this.context.device(), imageInfo, null, handle)
                        == org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                    shadowPlaceholderImage = handle.get(0);
                    org.lwjgl.vulkan.VkMemoryRequirements requirements =
                            org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
                    org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements(this.context.device(),
                            shadowPlaceholderImage, requirements);
                    org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                            .sType$Default().allocationSize(requirements.size())
                            .memoryTypeIndex(this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                                    org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                                    "immediate item shadow placeholder"));
                    if (org.lwjgl.vulkan.VK10.vkAllocateMemory(this.context.device(), allocate, null, handle)
                            == org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                        shadowPlaceholderMemory = handle.get(0);
                        org.lwjgl.vulkan.VK10.vkBindImageMemory(this.context.device(), shadowPlaceholderImage,
                                shadowPlaceholderMemory, 0L);
                    }
                    org.lwjgl.vulkan.VkImageViewCreateInfo viewInfo =
                            org.lwjgl.vulkan.VkImageViewCreateInfo.calloc(stack).sType$Default()
                                    .image(shadowPlaceholderImage)
                                    .viewType(org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D)
                                    .format(org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT);
                    viewInfo.subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                            .levelCount(1).layerCount(1);
                    if (org.lwjgl.vulkan.VK10.vkCreateImageView(this.context.device(), viewInfo, null, handle)
                            == org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                        shadowPlaceholderView = handle.get(0);
                    }
                }
                org.lwjgl.vulkan.VkSamplerCreateInfo samplerInfo =
                        org.lwjgl.vulkan.VkSamplerCreateInfo.calloc(stack).sType$Default()
                                .magFilter(org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST)
                                .minFilter(org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST)
                                .addressModeU(org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                                .addressModeV(org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                                .addressModeW(org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
                if (org.lwjgl.vulkan.VK10.vkCreateSampler(this.context.device(), samplerInfo, null, handle)
                        == org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                    shadowPlaceholderSampler = handle.get(0);
                }
                transitionPlaceholderToGeneral();
            }
        }
        if (!SHADOW_INSTANCES.contains(this)) {
            SHADOW_INSTANCES.add(this);
        }
    }

    /** Writes bindings 2 and 3 of one set from whatever the current shadow sources are. */
    private void writeShadowBindings(MemoryStack stack, long set) {
        org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer buffer =
                org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(1, stack);
        buffer.get(0).buffer(shadowMatrixBuffer.buffer()).offset(0L).range(64L);
        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer image =
                org.lwjgl.vulkan.VkDescriptorImageInfo.calloc(1, stack);
        long view = shadowMapView != 0L ? shadowMapView : shadowPlaceholderView;
        long sampler = shadowMapSampler != 0L ? shadowMapSampler : shadowPlaceholderSampler;
        image.get(0).imageLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL)
                .imageView(view).sampler(sampler);
        org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer writes =
                org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(2, stack);
        writes.get(0).sType$Default().dstSet(set).dstBinding(2)
                .descriptorType(org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1).pBufferInfo(buffer);
        writes.get(1).sType$Default().dstSet(set).dstBinding(3)
                .descriptorType(org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1).pImageInfo(image);
        vkUpdateDescriptorSets(this.context.device(), writes, null);
    }

    /** Writes the eye-space light matrix the renderer composes each frame. */
    public static void writeShadowMatrix(float[] matrix) {
        if (shadowMatrixBuffer == null) {
            return;
        }
        java.nio.FloatBuffer data = org.lwjgl.system.MemoryUtil.memFloatBuffer(
                shadowMatrixBuffer.mappedAddress(), 16);
        for (int i = 0; i < 16 && i < matrix.length; i++) {
            data.put(i, matrix[i]);
        }
    }

    /**
     * Points every live instance's sets at the map that now exists.
     * <p>
     * Sets are cached per texture view and built as items are drawn - which starts before the shadow pass has an image -
     * so this is what moves a set from the placeholder to the real map.
     */
    public static void setShadowSources(long view, long sampler) {
        shadowMapView = view;
        shadowMapSampler = sampler;
        // Cached sets are retired rather than rewritten: vkUpdateDescriptorSets on a set that a recording or pending command
        // buffer already references invalidates that buffer unless the layout declares UPDATE_AFTER_BIND, which this one
        // does not (#401). A retired set is released by the completion-gated path that also serves eviction, and the next
        // draw builds a set that names the real map.
        for (int i = 0; i < SHADOW_INSTANCES.size(); i++) {
            VulkanImmediateBlockPipeline instance = SHADOW_INSTANCES.get(i);
            instance.retireCachedSets();
        }
    }

    /** Moves every cached set to the retirement list and clears the cache. */
    private void retireCachedSets() {
        for (it.unimi.dsi.fastutil.longs.Long2LongMap.Entry entry : this.setsByTextureView.long2LongEntrySet()) {
            this.retiredSets.add(Long.valueOf(entry.getLongValue()));
        }
        this.retiredSetsCount += this.setsByTextureView.size();
        this.setsByTextureView.clear();
    }

    /**
     * Moves the placeholder map to GENERAL once, so the descriptor's declared layout is the layout the image is in.
     * The draw path cannot express a layout per frame, and an image left in UNDEFINED is what the layer reported as
     * "expects ... to be in layout VK_IMAGE_LAYOUT_GENERAL ... current layout is VK_IMAGE_LAYOUT_UNDEFINED".
     */
    private void transitionPlaceholderToGeneral() {
        if (shadowPlaceholderImage == 0L) {
            return;
        }
        long pool = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            if (org.lwjgl.vulkan.VK10.vkCreateCommandPool(this.context.device(),
                    org.lwjgl.vulkan.VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                            .queueFamilyIndex(this.context.queueFamilyIndex()), null, handle)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return;
            }
            pool = handle.get(0);
            org.lwjgl.PointerBuffer buffers = stack.callocPointer(1);
            if (org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers(this.context.device(),
                    org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                            .commandPool(pool).level(org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                            .commandBufferCount(1), buffers) != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return;
            }
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer = new org.lwjgl.vulkan.VkCommandBuffer(
                    buffers.get(0), this.context.device());
            org.lwjgl.vulkan.VK10.vkBeginCommandBuffer(commandBuffer,
                    org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack).sType$Default());
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(shadowPlaceholderImage).srcAccessMask(0)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT);
            barrier.get(0).subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .levelCount(1).layerCount(1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commandBuffer,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);
            org.lwjgl.vulkan.VK10.vkEndCommandBuffer(commandBuffer);
            PENDING_TRANSITIONS.reclaim(this.context.device());
            java.nio.LongBuffer fence = stack.mallocLong(1);
            if (org.lwjgl.vulkan.VK10.vkCreateFence(this.context.device(),
                    org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack).sType$Default(), null, fence) != VK_SUCCESS) {
                return;
            }
            long transitionFence = fence.get(0);
            if (org.lwjgl.vulkan.VK10.vkQueueSubmit(this.context.queue(),
                    org.lwjgl.vulkan.VkSubmitInfo.calloc(stack).sType$Default()
                            .pCommandBuffers(stack.pointers(commandBuffer.address())), transitionFence) != VK_SUCCESS) {
                org.lwjgl.vulkan.VK10.vkDestroyFence(this.context.device(), transitionFence, null);
                return;
            }
            int waited = org.lwjgl.vulkan.VK10.vkWaitForFences(this.context.device(), transitionFence, true,
                    2_000_000_000L);
            if (waited == VK_SUCCESS) {
                org.lwjgl.vulkan.VK10.vkDestroyFence(this.context.device(), transitionFence, null);
                return;
            }
            // A timeout is not a completion (#403): keep the pool with its fence instead of freeing it in the finally.
            PENDING_TRANSITIONS.retain(pool, transitionFence);
            pool = VK_NULL_HANDLE;
        } finally {
            if (pool != VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyCommandPool(this.context.device(), pool, null);
            }
        }
    }
}
