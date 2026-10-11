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
    /** Position, texture, colour and a normal the shaders do not read: the entity/TESR layout, 28 bytes. */
    public static final int POSITION_TEX_COLOR_NORMAL_BYTES = 28;
    /**
     * Position, texture and a normal, 24 bytes: the composite batch's layout, and {@code POSITION_TEX_NORMAL} and
     * {@code OLDMODEL_POSITION_TEX_NORMAL} with it.
     * <p>
     * 12 + 8 + 3 + 1. These widths are the runtime ones and not a rounded stride: vanilla's {@code VertexFormat} advances
     * by the element's own {@code getSize()} and pads nothing, so this layout is 24 and not 28 while the colour-carrying
     * {@code POSITION_TEX_COLOR_NORMAL} really is 28. This constant said 24 while the id it belongs to was never assigned
     * and the id it was confused with was answered instead, so nothing ever compared the two.
     */
    public static final int POSITION_TEX_NORMAL_BYTES = 24;
    /**
     * Position, a normal and a padding byte: 16 bytes, {@code DefaultVertexFormats.POSITION_NORMAL}.
     * <p>
     * 12 + 3 + 1. Nothing is written in the top byte of each vertex, and the shader reads only the position, but the
     * binding still has to step the width the format really has: a stride of 12 would read the next vertex's first
     * position byte as this vertex's normal, and the vertices are written end to end at no other stride.
     */
    public static final int POSITION_NORMAL_BYTES = 16;
    // 116, not 132: projection 64 + the model view's three affine columns 48 + alpha reference 4, all
    // inside the 128 bytes Vulkan guarantees (audit S19).
    // 208 since the fixed-function fog landed: the fragment stage reads the fog block at 128..207 (colour, params and
    // the model view the fog coordinate is computed from), so the pipeline layout's range has to cover it. This is the
    // size the lit ITEM pipeline has required of the device since the lighting block arrived, so it asks for nothing
    // the tree did not already. This class carries the POSITION (sky dome, horizon plane) and POSITION_TEX (water
    // surface, sun, moon, clouds) layouts, which is where the fog is visible.
    private static final int PUSH_CONSTANT_BYTES = 208;

    private final VulkanContext context;
    private final long renderPass;
    /** Whether this instance takes the colourless POSITION_TEX layout rather than POSITION_TEX_COLOR. */
    private boolean positionOnly;
    /** Position-and-nothing-else: twelve bytes, one attribute, and no texture coordinate to sample. */
    private boolean positionNoTexture;
    /** Non-zero when the layout is one of the wider standard formats whose extra element the shaders ignore. */
    private int extraTrailingElementBytes;
    /**
     * A stride that is neither the base layout's nor one of the trailing-element ones.
     * <p>
     * {@code POSITION_NORMAL} is 16 bytes and carries a normal rather than trailing padding, so neither of the two fields
     * above describes it. Rather than let it fall into a default that would be wrong, the stride lives in one place and the
     * default is only reached when none of the three is set.
     */
    private int explicitStride;

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
    /** The Vulkan compare op the mirrored {@code glDepthFunc} mapped to. */
    private final int depthCompareOp;

    private VulkanImmediateTexturePipeline(VulkanContext context, long renderPass, boolean blend, int srcFactor,
                                           int dstFactor, boolean depthTest, boolean depthWrite, boolean cull,
                                           int depthCompareOp) {
        this.context = context;
        this.renderPass = renderPass;
        this.blend = blend;
        this.srcFactor = srcFactor;
        this.dstFactor = dstFactor;
        this.depthTest = depthTest;
        this.depthWrite = depthWrite;
        this.cull = cull;
        this.depthCompareOp = depthCompareOp;
    }

    /** Creates a pipeline for straight-alpha blending, which is what most of vanilla's immediate content uses. */
    public static VulkanImmediateTexturePipeline create(VulkanContext context, long renderPass) {
        return create(context, renderPass, true, org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA,
                org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, true, false, false,
                VK_COMPARE_OP_LESS_OR_EQUAL);
    }

    /**
     * Creates a pipeline for one drawing state: blending with the given Vulkan factors, the depth test and its write
     * mask, and face culling. These bake into the pipeline object, which is why the immediate path caches one per
     * state instead of forcing every batch through a single hardcoded combination.
     */
    /** Creates the colourless {@code POSITION_TEX} variant, which is how the sky, the clouds and the water arrive. */
    public static VulkanImmediateTexturePipeline createForPositionTex(VulkanContext context, long renderPass, boolean blend,
            int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull, int depthCompareOp) {
        VulkanImmediateTexturePipeline pipeline = allocate(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull, depthCompareOp);
        pipeline.positionOnly = true;
        return pipeline.build();
    }

    /** Creates the {@code POSITION} variant: twelve bytes, one attribute, no colour and no texture. */
    public static VulkanImmediateTexturePipeline createForPosition(VulkanContext context, long renderPass, boolean blend,
            int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull, int depthCompareOp) {
        VulkanImmediateTexturePipeline pipeline = allocate(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull, depthCompareOp);
        pipeline.positionOnly = true;
        pipeline.positionNoTexture = true;
        return pipeline.build();
    }

    /**
     * Creates the {@code POSITION_NORMAL} variant: 16 bytes, position and a normal, no colour and no texture.
     * <p>
     * The white box {@code Render.renderOffsetAABB} draws for an entity with no renderer, and the reason this variant
     * exists: without it that box is refused by the uploader (it had no id at all before) and, under the GL-less window,
     * drawn by nobody. The shaders are the colourless position ones - this renderer evaluates no fixed-function lighting, so
     * the normal cannot change the picture, and the vertex's colour comes from the push block the way the position-only
     * layout's does. The stride is the whole of what makes it a different pipeline from {@link #createForPosition}: the
     * binding has to step 16 bytes, not the position-only layout's 12.
     */
    public static VulkanImmediateTexturePipeline createForPositionNormal(VulkanContext context, long renderPass,
            boolean blend, int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull,
            int depthCompareOp) {
        VulkanImmediateTexturePipeline pipeline = allocate(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull, depthCompareOp);
        pipeline.positionOnly = true;
        pipeline.positionNoTexture = true;
        pipeline.explicitStride = POSITION_NORMAL_BYTES;
        return pipeline.build();
    }

    /**
     * Creates the {@code POSITION_TEX_COLOR_NORMAL} variant: the same shaders and attribute offsets as the textured pipeline,
     * with the stride the extra normal element needs. The normal is not read - none of these shaders light anything - but the
     * layout has to be declared honestly or the pipeline reads the next vertex's bytes as this one's colour.
     */
    public static VulkanImmediateTexturePipeline createForPositionTexColorNormal(VulkanContext context, long renderPass,
            boolean blend, int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull,
            int depthCompareOp) {
        VulkanImmediateTexturePipeline pipeline = allocate(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull, depthCompareOp);
        pipeline.extraTrailingElementBytes = POSITION_TEX_COLOR_NORMAL_BYTES;
        return pipeline.build();
    }

    /** Creates the {@code POSITION_TEX_NORMAL} variant, 24 bytes: colourless shaders, wider stride than the 20-byte one. */
    public static VulkanImmediateTexturePipeline createForPositionTexNormal(VulkanContext context, long renderPass,
            boolean blend, int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull,
            int depthCompareOp) {
        VulkanImmediateTexturePipeline pipeline = allocate(context, renderPass, blend, srcFactor, dstFactor, depthTest,
                depthWrite, cull, depthCompareOp);
        pipeline.positionOnly = true;
        pipeline.extraTrailingElementBytes = POSITION_TEX_NORMAL_BYTES;
        return pipeline.build();
    }

    public static VulkanImmediateTexturePipeline create(VulkanContext context, long renderPass, boolean blend, int srcFactor, int dstFactor,
                            boolean depthTest, boolean depthWrite, boolean cull, int depthCompareOp) {
        return allocate(context, renderPass, blend, srcFactor, dstFactor, depthTest, depthWrite, cull, depthCompareOp).build();
    }

    /**
     * Allocates the pipeline without building it, so a variant factory can set the fields that decide its layout first.
     * <p>
     * This split is the whole fix for the defect the sky dome measured. {@link #create} builds the pipeline inside the
     * call, and every variant factory used to call it and then set {@code positionOnly} / {@code positionNoTexture} /
     * {@code extraTrailingElementBytes} / {@code explicitStride} on the finished object - so those fields were still at
     * their defaults when {@link #createPipeline} read them, and <b>every</b> variant was built as the base
     * {@code POSITION_TEX_COLOR} pipeline: three attributes, a 24-byte binding stride, and a colour attribute at byte
     * 20 read as {@code R8G8B8A8_UNORM}. Measured on the sky dome, whose real layout is 12 bytes of position: the
     * attribute at 20 landed on the vertex data's own floats, and since every coordinate of that dome is a multiple of
     * 64 or +-16 (low two bytes zero) the fragments came out with red and green exactly zero and blue in
     * {0,64,128,160,192} interpolated across the quad - the 176,633 pure-blue pixels the frame gained. The index
     * expansion stayed the 676-vertex one, so the quads sheared into the wedges and read past the end of the mesh.
     * {@code VulkanImmediatePipeline} does this correctly by passing its variant through the constructor; this class
     * now builds only after its fields are final.
     */
    private static VulkanImmediateTexturePipeline allocate(VulkanContext context, long renderPass, boolean blend,
                            int srcFactor, int dstFactor, boolean depthTest, boolean depthWrite, boolean cull,
                            int depthCompareOp) {
        if (context == null || renderPass == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("context and renderPass must be set");
        }
        return new VulkanImmediateTexturePipeline(context, renderPass, blend, srcFactor, dstFactor, depthTest, depthWrite,
                cull, depthCompareOp);
    }

    /** Builds the layout and the pipeline from the fields as they stand; call only once they are final. */
    private VulkanImmediateTexturePipeline build() {
        this.createPipelineLayout();
        this.createPipeline();
        return this;
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
        ByteBuffer clamped = pushConstants.duplicate();
        // The caller's block can be wider than this layout's declared range (the lit variant's is 208) - pushing
        // past the range is a validation error, so only the declared bytes go out.
        clamped.limit(Math.min(clamped.remaining(), PUSH_CONSTANT_BYTES));
        vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, clamped);
        LongBuffer vertexBuffer = MemoryStack.stackGet().longs(mesh.vertexBuffer());
        vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffer, MemoryStack.stackGet().longs(0L));
        vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer(), 0L, VK_INDEX_TYPE_UINT32);
        mesh.draw(commandBuffer);
    }

    public int vertexBytes() {
        return VERTEX_BYTES;
    }

    /**
     * {@return the stride this variant's vertex binding declares}.
     * <p>
     * One place, in the order the variants are built: an explicit stride wins (the position-normal layout is neither of the
     * shortages below), then a trailing element that widens the vertex, then the three base layouts. The expression used to
     * be inlined in the binding and read the three flags in an order that made {@code POSITION_NORMAL} unwritable without a
     * fourth branch.
     */
    private int vertexStride() {
        if (this.explicitStride != 0) {
            return this.explicitStride;
        }
        if (this.extraTrailingElementBytes != 0) {
            return this.extraTrailingElementBytes;
        }
        return this.positionNoTexture ? POSITION_BYTES : this.positionOnly ? POSITION_TEX_BYTES : VERTEX_BYTES;
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

    /**
     * The other side of the atlas-binding question: which images the registry is handing out.
     * <p>
     * Every texture this pipeline builds a descriptor for comes from {@code VulkanTextureRegistry} - either
     * {@code get(boundTexture)} or {@code imageFor(...)} - so the first few distinct ones are a sample of the registry's
     * current values, taken at the moment a descriptor is built from them. Printed next to
     * {@code CELTIUM_ATLAS_DESCRIPTOR}, a run says whether the image the terrain samples is the same image the registry
     * hands out for that size, or an older one the terrain is still holding. Bounded to
     * {@link #CENSUS_LIMIT} lines for the whole process; the identity comparison per call is a walk over at most that
     * many longs.
     */
    private static final int CENSUS_LIMIT = 6;
    /** Distinct census entries: {key, view, generation, width<<32|height}, plus the object identity alongside. */
    private static final long[][] CENSUS = new long[CENSUS_LIMIT][4];
    private static final int[] CENSUS_IDENTITY = new int[CENSUS_LIMIT];
    private static int censusCount;
    private static int censusLines;
    private static long censusCalls;

    /** Records one distinct image the registry supplied, and prints the first few with their full identity. */
    private static void censusTexture(VulkanGlTexture texture) {
        censusCalls++;
        long key = texture.key();
        for (int i = 0; i < censusCount; i++) {
            if (CENSUS[i][0] == key) {
                return;
            }
        }
        if (censusCount >= CENSUS_LIMIT) {
            return;
        }
        int slot = censusCount++;
        CENSUS[slot][0] = key;
        CENSUS[slot][1] = texture.view();
        CENSUS[slot][2] = key ^ (texture.view() * 31L);
        CENSUS[slot][3] = ((long) texture.width() << 32) | (texture.height() & 0xFFFFFFFFL);
        CENSUS_IDENTITY[slot] = System.identityHashCode(texture);
        if (censusLines < CENSUS_LIMIT) {
            censusLines++;
            System.out.println("[Celtium] CELTIUM_REGISTRY_TEXTURE #" + slot + " obj="
                    + Integer.toHexString(CENSUS_IDENTITY[slot]) + " view=0x" + Long.toHexString(texture.view())
                    + " gen=" + CENSUS[slot][2] + " key=0x" + Long.toHexString(key) + " size=" + texture.width()
                    + "x" + texture.height() + " full=" + texture.hasFullContent() + " sampler=0x"
                    + Long.toHexString(texture.sampler()) + " (from the registry, sampled by the immediate path)");
        }
    }

    /** {@return whether the registry has been seen supplying an image of this size}, for the terrain audit line. */
    public static String registryImageOfSize(int width, int height) {
        long packed = ((long) width << 32) | (height & 0xFFFFFFFFL);
        for (int i = 0; i < censusCount; i++) {
            if (CENSUS[i][3] == packed) {
                return "#" + i + " obj=" + Integer.toHexString(CENSUS_IDENTITY[i]) + " view=0x"
                        + Long.toHexString(CENSUS[i][1]) + " gen=" + CENSUS[i][2];
            }
        }
        return "none-seen";
    }

    public long descriptorSetFor(VulkanGlTexture texture) {
        if (this.descriptorPool == VK_NULL_HANDLE || texture == null) {
            return 0L;
        }
        censusTexture(texture);
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
                binding.get(0).binding(0).stride(vertexStride()).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(this.positionNoTexture ? 1 : this.positionOnly ? 2 : 3, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                if (!this.positionNoTexture) {
                    // Only the variants that declare a texture coordinate have anything at offset 12; writing this
                    // struct for the one-attribute layouts wrote 16 bytes past the end of a one-element array.
                    attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(12);
                }
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
                                .depthCompareOp(this.depthCompareOp);
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
