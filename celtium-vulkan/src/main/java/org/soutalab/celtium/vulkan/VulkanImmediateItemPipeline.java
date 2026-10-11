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
public final class VulkanImmediateItemPipeline {
    /**
     * The ITEM layout: 3 floats position, 4 unsigned bytes colour, 2 floats texture coordinates, 3 signed bytes
     * normal and one padding byte - colour at offset 12, texture at 16, normal at 24. The normal is part of the
     * stride but not of the vertex input description, because the shader does not read it - Vulkan only requires the
     * attributes the shader consumes.
     */
    public static final int VERTEX_BYTES = 28;
    // 116, not 132: projection 64 + the model view's three affine columns 48 + alpha reference 4, all
    // inside the 128 bytes Vulkan guarantees (audit S19).
    // BH-30: the unlit variant pairs the shared immediate_tex_color pair, whose block now declares the fog region up
    // to byte 207. A 128-byte range would make the shader's block exceed the declared range (VUID on the push range),
    // so the unlit range grows to the 208 the shared layout needs; the lit range is unchanged at 216.
    private static final int PUSH_CONSTANT_BYTES = 208;
    /**
     * The lit variant's block: the shared 128 plus five vec4 - two model-space light directions, the scene ambient,
     * and each light's diffuse - plus the light-map coordinate pair at 208, matching the layout
     * {@code immediate_tex_color_lit.vert} declares. The block is the one place the world light level can travel for
     * this batch: the ITEM layout's vertices carry position, texture, colour and normal, and no light coordinate.
     */
    private static final int PUSH_CONSTANT_BYTES_LIT = 216;

    private final VulkanContext context;
    private final long renderPass;
    /** When the vertex stage runs the fixed-function lighting eval and the normal attribute is real input. */
    private final boolean lit;

    /**
     * Descriptor sets this family has allocated, process-wide.
     * <p>
     * Its own counter rather than the textured family's: the summary prints one census entry per family (audit B11),
     * and incrementing the textured pipeline's static from here made "texture" a name for two families and hid which
     * one the sets belonged to.
     */
    public static final java.util.concurrent.atomic.AtomicLong DESCRIPTOR_SETS_ALLOCATED =
            new java.util.concurrent.atomic.AtomicLong();

    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;
    private long descriptorSetLayout = VK_NULL_HANDLE;
    /**
     * Every descriptor pool this pipeline has allocated sets from, oldest first; all are destroyed in {@link #delete()}.
     * <p>
     * The item family had {@code maxSets(8192)} with {@code descriptorCount(64)} per set: the descriptor count is the
     * real bound, so it could allocate 64 sets and no more - the same audit-B03 lifetime as the textured pipeline,
     * with a larger-looking constant hiding it. The pools now grow on demand like that one, and the growth is counted
     * in the shared {@link VulkanImmediateTexturePipeline#descriptorPoolSummary()} line.
     */
    private final java.util.List<Long> descriptorPools = new java.util.ArrayList<Long>();
    private long descriptorSet = VK_NULL_HANDLE;
    private final java.util.Map<Long, Long> setsByTextureView = new java.util.HashMap<Long, Long>();

    private final VulkanColorBlendState colorBlend;
    private final boolean depthTest;
    private final boolean depthWrite;
    private final boolean cull;
    /** The Vulkan compare op the mirrored {@code glDepthFunc} mapped to. */
    private final int depthCompareOp;

    private VulkanImmediateItemPipeline(VulkanContext context, long renderPass, VulkanColorBlendState colorBlend,
                                           boolean depthTest, boolean depthWrite, boolean cull,
                                           int depthCompareOp, boolean lit) {
        this.context = context;
        this.renderPass = renderPass;
        this.colorBlend = colorBlend;
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        this.cull = cull;
        this.depthCompareOp = depthCompareOp;
        this.lit = lit;
    }

    /** Creates a pipeline for straight-alpha blending, which is what most of vanilla's immediate content uses. */
    public static VulkanImmediateItemPipeline create(VulkanContext context, long renderPass) {
        return create(context, renderPass, VulkanColorBlendState.legacy(), true, false, false,
                VK_COMPARE_OP_LESS_OR_EQUAL);
    }

    /**
     * Creates a pipeline for one drawing state: the blend factors, blend equations and colour write mask the batch was
     * captured under (BH-07, BH-10), the depth test and its write mask, and face culling. These bake into the pipeline
     * object, which is why the immediate path caches one per state instead of forcing every batch through a single
     * hardcoded combination.
     */
    public static VulkanImmediateItemPipeline create(VulkanContext context, long renderPass,
                            VulkanColorBlendState colorBlend, boolean depthTest, boolean depthWrite, boolean cull,
                            int depthCompareOp) {
        return create(context, renderPass, colorBlend, depthTest, depthWrite, cull, depthCompareOp, false);
    }

    /**
     * The lit variant: the ITEM format's trailing normal stops being padding - it becomes the attribute the
     * fixed-function eval shades by, and the push block grows to carry the light directions, ambient and diffuse.
     */
    public static VulkanImmediateItemPipeline create(VulkanContext context, long renderPass,
                            VulkanColorBlendState colorBlend, boolean depthTest, boolean depthWrite, boolean cull,
                            int depthCompareOp, boolean lit) {
        if (context == null || renderPass == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("context and renderPass must be set");
        }
        if (colorBlend == null) {
            throw new IllegalArgumentException("colorBlend must be set");
        }
        VulkanImmediateItemPipeline result = new VulkanImmediateItemPipeline(context, renderPass, colorBlend,
                depthTest, depthWrite, cull, depthCompareOp, lit);
        result.createPipelineLayout();
        result.createPipeline();
        return result;
    }

    /** {@return whether this variant samples the light map and carries the light-map term}. */
    public boolean lit() {
        return this.lit;
    }

    /** {@return the push range this pipeline's layout declares}, so the draw clamps a wider shared block. */
    public int pushBytes() {
        return this.lit ? PUSH_CONSTANT_BYTES_LIT : PUSH_CONSTANT_BYTES;
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
        ByteBuffer view = pushConstants.duplicate();
        // The shared block may be wider than this layout's declared range (the lit variant's block is) - pushing past
        // the range is a validation error, so the view is clamped to what this pipeline declared.
        view.limit(Math.min(view.remaining(), pushBytes()));
        vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, view);
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
    public long descriptorSetFor(VulkanGlTexture texture) {
        if (this.descriptorPools.isEmpty() || texture == null) {
            return 0L;
        }
        if (texture.view() == 0L) {
            // Same rule as the texture pipeline: a released view is refused by name rather than written into a
            // descriptor. Measured failure of the other choice: `u_Texture is using imageView VkImageView 0x0` followed
            // by VK_ERROR_DEVICE_LOST, which ends the session rather than one batch.
            VulkanImmediateTexturePipeline.ZERO_VIEW_REFUSALS++;
            return 0L;
        }
        Long existing = this.setsByTextureView.get(Long.valueOf(texture.key()));
        if (existing != null) {
            return existing.longValue();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long set = allocateDescriptorSet(stack);
            if (set == 0L) {
                return 0L;
            }
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
            VulkanImmediateTexturePipeline.DESCRIPTOR_KEYS_CACHED.incrementAndGet();
            return set;
        } catch (RuntimeException | LinkageError error) {
            return 0L;
        }
    }

    /**
     * {@return the descriptor set that samples the batch's texture and the light map}, for the lit variant.
     * <p>
     * The key is the pair of {@link VulkanGlTexture#key()}s, and that is what makes it safe across a refresh: a key
     * folds in the view handle *and the generation*, which is bumped when the image view or the sampler is rebuilt
     * ({@code VulkanGlTexture} lines 1905, 1945, 2129). A set written before a rebuild therefore cannot be served
     * afterwards - the old key is simply never looked up again - and the light map, which is re-uploaded every few
     * frames, invalidates exactly the sets that named its previous view.
     * <p>
     * A wrapper whose view has been released is refused by name ({@code ZERO_VIEW_REFUSALS}) rather than written into
     * a descriptor: a zero view is the measured `imageView 0x0 is invalid or has been destroyed` ->
     * {@code VK_ERROR_DEVICE_LOST} chain, which ends the session rather than one batch. Returns 0, which the caller
     * turns into a named refusal; under the GL-less window a refused batch is drawn by nobody, and that is the honest
     * outcome while the wrapper has no view.
     */
    public long descriptorSetFor(VulkanGlTexture texture, VulkanGlTexture lightmap) {
        if (!this.lit || lightmap == null || texture == null || this.descriptorPools.isEmpty()) {
            return 0L;
        }
        if (texture.view() == 0L) {
            VulkanImmediateTexturePipeline.ZERO_VIEW_REFUSALS++;
            return 0L;
        }
        if (lightmap.view() == 0L || lightmap.sampler() == 0L) {
            // The light map is refreshed every few frames and a mirror mid-rebuild has no view yet. Binding it would be
            // the device-lost path; refusing costs the batch's world-light term for as long as the rebuild lasts, and
            // the counter says how often that happened.
            VulkanImmediateTexturePipeline.ZERO_VIEW_REFUSALS++;
            if (VulkanImmediateTexturePipeline.ZERO_VIEW_REFUSALS <= 4) {
                System.out.println("[Celtium] CELTIUM_ZERO_VIEW_REFUSAL key=0x"
                        + Long.toHexString(lightmap.key()) + " size=" + lightmap.width() + "x"
                        + lightmap.height() + " (the light map's view has been released; the lit batch is refused"
                        + " rather than binding a zero view)");
            }
            return 0L;
        }
        long descriptorKey = (texture.key() * 31L) + lightmap.key();
        Long existing = this.setsByTextureView.get(Long.valueOf(descriptorKey));
        if (existing != null) {
            return existing.longValue();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long set = allocateDescriptorSet(stack);
            if (set == 0L) {
                return 0L;
            }
            VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack);
            image.get(0).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(texture.view())
                    .sampler(texture.sampler());
            VkDescriptorImageInfo.Buffer lightImage = VkDescriptorImageInfo.calloc(1, stack);
            lightImage.get(0).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(lightmap.view())
                    .sampler(lightmap.sampler());
            org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer write =
                    org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(2, stack);
            write.get(0).sType$Default()
                    .dstSet(set)
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(image);
            write.get(1).sType$Default()
                    .dstSet(set)
                    .dstBinding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(lightImage);
            vkUpdateDescriptorSets(this.context.device(), write, null);
            this.setsByTextureView.put(Long.valueOf(descriptorKey), Long.valueOf(set));
            VulkanImmediateTexturePipeline.DESCRIPTOR_KEYS_CACHED.incrementAndGet();
            return set;
        } catch (RuntimeException | LinkageError error) {
            return 0L;
        }
    }

    /**
     * Creates one more pool and appends it to {@link #descriptorPools}; the item family shares the textured family's
     * pool sizing and counters, because the failure it guards against is the same one (audit B03).
     *
     * @return the new pool, or {@link VK_NULL_HANDLE} when the pipeline has already grown to its limit
     */
    private long createDescriptorPool() {
        if (this.descriptorPools.size() >= DESCRIPTOR_POOL_LIMIT) {
            return VK_NULL_HANDLE;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer poolSize =
                    org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(1, stack);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(DESCRIPTOR_SETS_PER_POOL);
            org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                            .maxSets(DESCRIPTOR_SETS_PER_POOL).pPoolSizes(poolSize);
            LongBuffer poolHandle = stack.mallocLong(1);
            if (vkCreateDescriptorPool(this.context.device(), poolInfo, null, poolHandle) != VK_SUCCESS) {
                return VK_NULL_HANDLE;
            }
            this.descriptorPools.add(Long.valueOf(poolHandle.get(0)));
            VulkanImmediateTexturePipeline.DESCRIPTOR_POOLS_CREATED.incrementAndGet();
            if (this.descriptorPools.size() > 1) {
                VulkanImmediateTexturePipeline.DESCRIPTOR_POOL_GROWTHS.incrementAndGet();
                System.out.println("[Celtium] CELTIUM_IMMEDIATE_DESCRIPTORS item pipeline grew to "
                        + this.descriptorPools.size() + " pools: "
                        + VulkanImmediateTexturePipeline.descriptorPoolSummary());
            }
            return poolHandle.get(0);
        }
    }

    /** {@return a descriptor set from the newest pool, growing the pool when it is full}, or 0 when it cannot. */
    private long allocateDescriptorSet(MemoryStack stack) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Long pool = this.descriptorPools.get(this.descriptorPools.size() - 1);
            org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                    org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                            .descriptorPool(pool.longValue())
                            .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer handle = stack.mallocLong(1);
            if (vkAllocateDescriptorSets(this.context.device(), allocate, handle) == VK_SUCCESS) {
                DESCRIPTOR_SETS_ALLOCATED.incrementAndGet();
                return handle.get(0);
            }
            if (createDescriptorPool() == VK_NULL_HANDLE) {
                break;
            }
        }
        VulkanImmediateTexturePipeline.DESCRIPTOR_ALLOCATION_FAILURES.incrementAndGet();
        return 0L;
    }

    /** Sets one pool holds and how many pools a pipeline may grow to, shared with the textured family. */
    private static final int DESCRIPTOR_SETS_PER_POOL = VulkanImmediateTexturePipeline.DESCRIPTOR_SETS_PER_POOL;
    private static final int DESCRIPTOR_POOL_LIMIT = VulkanImmediateTexturePipeline.DESCRIPTOR_POOL_LIMIT;

    public void delete() {
        if (this.pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(this.context.device(), this.pipeline, null);
            this.pipeline = VK_NULL_HANDLE;
        }
        this.setsByTextureView.clear();
        for (int i = 0; i < this.descriptorPools.size(); i++) {
            vkDestroyDescriptorPool(this.context.device(), this.descriptorPools.get(i).longValue(), null);
        }
        this.descriptorPools.clear();
        this.descriptorSet = VK_NULL_HANDLE;
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
            // The lit variant samples the light map on a second binding, and it samples it in the *vertex* stage: the
            // fixed-function term it stands in for is a per-draw modulate, and the coordinate arrives as a push constant
            // (see LightmapCoordsMixin). A binding the layout does not declare is a shader-interface error, and one it
            // declares but the set does not fill is a descriptor error, so both variants get exactly theirs.
            org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer binding =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(this.lit ? 2 : 1, stack);
            binding.get(0).binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(this.lit ? (VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                            : VK_SHADER_STAGE_FRAGMENT_BIT);
            if (this.lit) {
                binding.get(1).binding(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            }
            org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutInfo =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                            .pBindings(binding);
            LongBuffer layoutHandle = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null, layoutHandle),
                    "vkCreateDescriptorSetLayout(immediate textured)");
            this.descriptorSetLayout = layoutHandle.get(0);
            // The first of the pipeline's pools. maxSets was 8192 here while descriptorCount was 64, and the descriptor
            // count is the binding limit: the pool could never hold more than 64 sets. Both numbers are now
            // DESCRIPTOR_SETS_PER_POOL and allocateDescriptorSet grows a new pool when one is full (audit B03).
            if (createDescriptorPool() == VK_NULL_HANDLE) {
                throw new IllegalStateException("vkCreateDescriptorPool(immediate item) failed");
            }
            org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                    org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                            .descriptorPool(this.descriptorPools.get(0).longValue())
                            .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer setHandle = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(this.context.device(), allocate, setHandle),
                    "vkAllocateDescriptorSets(immediate item)");
            this.descriptorSet = setHandle.get(0);
            DESCRIPTOR_SETS_ALLOCATED.incrementAndGet();
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
            // The fragment stage reads the alpha-test reference at offset 128, so the range must be visible to both
            // stages: declaring it for the vertex stage alone is a shader-interface mismatch.
            range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0).size(this.lit ? PUSH_CONSTANT_BYTES_LIT : PUSH_CONSTANT_BYTES);
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
            ByteBuffer vertexCode = SpirvShaders.load(this.lit ? SpirvShaders.IMMEDIATE_TEX_COLOR_LIT_VERTEX
                    : SpirvShaders.IMMEDIATE_TEX_COLOR_VERTEX);
            // BH-30: the lit variant has its own fragment without the fog input/math - its push block spends 128..215
            // on the lighting payload, so the shared fragment's fog block cannot be declared there (and with the shared
            // fragment the pipeline failed to create at all: fragment input 2 with no matching vertex output, which
            // refused every lit ITEM/id9 batch, i.e. entities, the held item and TESR models).
            ByteBuffer fragmentCode = SpirvShaders.load(this.lit
                    ? SpirvShaders.IMMEDIATE_TEX_COLOR_LIT_FRAGMENT : SpirvShaders.IMMEDIATE_TEX_COLOR_FRAGMENT);
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
                binding.get(0).binding(0).stride(VERTEX_BYTES).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(this.lit ? 4 : 3, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                // DefaultVertexFormats.ITEM's real order is POSITION_3F, COLOR_4UB, TEX_2F, NORMAL_3B, PADDING_1B -
                // colour at 12 and the texture coordinates at 16, the reverse of POSITION_TEX_COLOR's tex/colour
                // order. Earlier offsets read the colour bytes as texture coordinates; the latent bug never showed
                // because no ITEM-format batch arrived in any measured run (acceptedByFormat has been 4:0 to 4:1).
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(16);
                attributes.get(2).location(2).binding(0).format(VK_FORMAT_R8G8B8A8_UNORM).offset(12);
                if (this.lit) {
                    // The trailing normal, packed signed bytes the way BufferBuilder.normal writes them.
                    attributes.get(3).location(3).binding(0).format(VK_FORMAT_R8G8B8_SNORM).offset(24);
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
}
