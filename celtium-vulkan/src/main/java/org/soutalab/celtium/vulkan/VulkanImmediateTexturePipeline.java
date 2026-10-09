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
public final class VulkanImmediateTexturePipeline {
    /** {@code POSITION_TEX_COLOR}: 3 floats position, 2 floats texture coordinates, 4 unsigned bytes colour. */
    public static final int VERTEX_BYTES = 24;

    /** {@code POSITION_TEX}: 3 floats position and 2 floats texture coordinates, the shape RenderGlobal uploads. */
    public static final int POSITION_TEX_BYTES = 20;
    /** Position only: three floats and nothing else. */
    public static final int POSITION_BYTES = 12;
    /** Position, texture, colour and a normal the shaders do not read: the entity/TESR layout. */
    public static final int POSITION_TEX_COLOR_NORMAL_BYTES = 28;
    /** Position, texture and a normal: the composite batch's layout. */
    public static final int POSITION_TEX_NORMAL_BYTES = 24;
    // 116, not 132: projection 64 + the model view's three affine columns 48 + alpha reference 4, all
    // inside the 128 bytes Vulkan guarantees (audit S19).
    private static final int PUSH_CONSTANT_BYTES = 128;

    private final VulkanContext context;
    private final long renderPass;
    /** Whether this instance takes the colourless POSITION_TEX layout rather than POSITION_TEX_COLOR. */
    private boolean positionOnly;
    /** Position-and-nothing-else: twelve bytes, one attribute, and no texture coordinate to sample. */
    private boolean positionNoTexture;
    /** Non-zero when the layout is one of the wider standard formats whose extra element the shaders ignore. */
    private int extraTrailingElementBytes;

    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;
    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;
    private final java.util.Map<Long, Long> setsByTextureView = new java.util.HashMap<Long, Long>();

    private final boolean blend;
    private final int srcFactor;
    private final int dstFactor;
    private final boolean depthTest;
    private final boolean depthWrite;
    private final boolean cull;

    private VulkanImmediateTexturePipeline(VulkanContext context, long renderPass, boolean blend, int srcFactor,
                                           int dstFactor, boolean depthTest, boolean depthWrite, boolean cull) {
        this.context = context;
        this.renderPass = renderPass;
        this.blend = blend;
        this.srcFactor = srcFactor;
        this.dstFactor = dstFactor;
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        this.cull = cull;
    }

    /** Creates a pipeline for straight-alpha blending, which is what most of vanilla's immediate content uses. */
    public static VulkanImmediateTexturePipeline create(VulkanContext context, long renderPass) {
        return create(context, renderPass, true, org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA,
                org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, true, false, false);
    }

    /**
     * Creates a pipeline for one drawing state: blending with the given Vulkan factors, the depth test and its write
     * mask, and face culling. These bake into the pipeline object, which is why the immediate path caches one per
     * state instead of forcing every batch through a single hardcoded combination.
     */
    /** Creates the colourless {@code POSITION_TEX} variant, which is how the sky, the clouds and the water arrive. */
    public static VulkanImmediateTexturePipeline createForPositionTex(VulkanContext context, long renderPass, boolean blend,
            int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull) {
        VulkanImmediateTexturePipeline pipeline = create(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull);
        pipeline.positionOnly = true;
        return pipeline;
    }

    /** Creates the {@code POSITION} variant: twelve bytes, one attribute, no colour and no texture. */
    public static VulkanImmediateTexturePipeline createForPosition(VulkanContext context, long renderPass, boolean blend,
            int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull) {
        VulkanImmediateTexturePipeline pipeline = create(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull);
        pipeline.positionOnly = true;
        pipeline.positionNoTexture = true;
        return pipeline;
    }

    /**
     * Creates the {@code POSITION_TEX_COLOR_NORMAL} variant: the same shaders and attribute offsets as the textured pipeline,
     * with the stride the extra normal element needs. The normal is not read - none of these shaders light anything - but the
     * layout has to be declared honestly or the pipeline reads the next vertex's bytes as this one's colour.
     */
    public static VulkanImmediateTexturePipeline createForPositionTexColorNormal(VulkanContext context, long renderPass,
            boolean blend, int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull) {
        VulkanImmediateTexturePipeline pipeline = create(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull);
        pipeline.extraTrailingElementBytes = POSITION_TEX_COLOR_NORMAL_BYTES;
        return pipeline;
    }

    /** Creates the {@code POSITION_TEX_NORMAL} variant for the composite batch: colourless shaders, wider stride. */
    public static VulkanImmediateTexturePipeline createForPositionTexNormal(VulkanContext context, long renderPass,
            boolean blend, int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull) {
        VulkanImmediateTexturePipeline pipeline = create(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull);
        pipeline.positionOnly = true;
        pipeline.extraTrailingElementBytes = POSITION_TEX_NORMAL_BYTES;
        return pipeline;
    }

    public static VulkanImmediateTexturePipeline create(VulkanContext context, long renderPass, boolean blend, int srcFactor, int dstFactor,
                            boolean depthTest, boolean depthWrite, boolean cull) {
        if (context == null || renderPass == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("context and renderPass must be set");
        }
        VulkanImmediateTexturePipeline result = new VulkanImmediateTexturePipeline(context, renderPass, blend, srcFactor, dstFactor, depthTest, depthWrite, cull);
        result.createPipelineLayout();
        result.createPipeline();
        return result;
    }

    /** Binds the pipeline, pushes the matrices and records the mesh's batch. */
    public void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, ByteBuffer pushConstants,
                     long descriptorSetToBind) {
        if (this.pipeline == VK_NULL_HANDLE || mesh == null || mesh.isEmpty()) {
            return;
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                org.lwjgl.system.MemoryStack.stackGet().longs(descriptorSetToBind), null);
        // Both stages: the vertex stage reads the matrices and the fragment stage the alpha-test reference, and the
        // command's stage flags have to cover every stage that reads the range.
        vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushConstants);
        LongBuffer vertexBuffer = MemoryStack.stackGet().longs(mesh.vertexBuffer());
        vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffer, MemoryStack.stackGet().longs(0L));
        vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer(), 0L, VK_INDEX_TYPE_UINT32);
        mesh.draw(commandBuffer);
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
    /** Cache hits and misses of {@link #descriptorSetFor}, so "the cache is not hitting" is measured, not assumed. */
    public static long DESCRIPTOR_HITS;
    public static long DESCRIPTOR_MISSES;

    public long descriptorSetFor(VulkanGlTexture texture) {
        if (this.descriptorPool == VK_NULL_HANDLE || texture == null) {
            return 0L;
        }
        Long existing = this.setsByTextureView.get(Long.valueOf(texture.key()));
        if (existing != null) {
            DESCRIPTOR_HITS++;
            return existing.longValue();
        }
        // A miss allocates and writes a descriptor set. If this fires on almost every batch, the key (the texture's view)
        // is changing under us and the "cache" is really an allocation per call - which is what the 1.13 ms per
        // prepareImmediate call would be.
        DESCRIPTOR_MISSES++;
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
            image.get(0).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(texture.view())
                    .sampler(texture.sampler());
            org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer write =
                    org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(1, stack);
            write.get(0).sType$Default()
                    .dstSet(set)
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(image);
            vkUpdateDescriptorSets(this.context.device(), write, null);
            this.setsByTextureView.put(Long.valueOf(texture.key()), Long.valueOf(set));
            return set;
        } catch (RuntimeException | LinkageError error) {
            return 0L;
        }
    }

    public void delete() {
        if (this.pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(this.context.device(), this.pipeline, null);
            this.pipeline = VK_NULL_HANDLE;
        }
        this.setsByTextureView.clear();
        if (this.descriptorPool != VK_NULL_HANDLE) {
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
                    org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(1, stack);
            binding.get(0).binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutInfo =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                            .pBindings(binding);
            LongBuffer layoutHandle = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null, layoutHandle),
                    "vkCreateDescriptorSetLayout(immediate textured)");
            this.descriptorSetLayout = layoutHandle.get(0);
            org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer poolSize =
                    org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(1, stack);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(64);
            // One set per distinct texture the frame binds; the pool refuses beyond that and the caller then refuses
            // the batch rather than drawing with the wrong image.
            org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                            .maxSets(64).pPoolSizes(poolSize);
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
            ByteBuffer vertexCode = SpirvShaders.load(this.positionNoTexture ? SpirvShaders.IMMEDIATE_POSITION_VERTEX
                    : this.positionOnly ? SpirvShaders.IMMEDIATE_POSITION_TEX_VERTEX : SpirvShaders.IMMEDIATE_TEX_COLOR_VERTEX);
            ByteBuffer fragmentCode = SpirvShaders.load(this.positionNoTexture ? SpirvShaders.IMMEDIATE_POSITION_FRAGMENT
                : this.positionOnly ? SpirvShaders.IMMEDIATE_POSITION_TEX_FRAGMENT : SpirvShaders.IMMEDIATE_TEX_COLOR_FRAGMENT);
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
                stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                        .pName(stack.UTF8("main"));

                VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0).binding(0).stride(this.extraTrailingElementBytes != 0 ? this.extraTrailingElementBytes
                        : this.positionNoTexture ? POSITION_BYTES : this.positionOnly ? POSITION_TEX_BYTES : VERTEX_BYTES)
                        .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(this.positionNoTexture ? 1 : this.positionOnly ? 2 : 3, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(12);
                if (!this.positionOnly && !this.positionNoTexture) {
                    // The colourless format has nothing at offset 20 - describing an attribute there would read past
                    // the end of every vertex.
                    attributes.get(2).location(2).binding(0).format(VK_FORMAT_R8G8B8A8_UNORM).offset(20);
                }
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
                                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
                VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(1, stack);
                blendAttachment.get(0)
                        .blendEnable(this.blend)
                        .srcColorBlendFactor(this.srcFactor)
                        .dstColorBlendFactor(this.dstFactor)
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .alphaBlendOp(VK_BLEND_OP_ADD)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                                | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default().logicOpEnable(false).pAttachments(blendAttachment);
                VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

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
}
