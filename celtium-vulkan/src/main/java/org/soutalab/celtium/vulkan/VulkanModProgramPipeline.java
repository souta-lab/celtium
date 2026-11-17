package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.soutalab.celtium.vulkan.gl.ModGlslTranslator;
import org.soutalab.celtium.vulkan.gl.ModProgramRegistry;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/**
 * A mod's own GL program (lowered and compiled by {@link ModProgramRegistry}) as a pipeline of the immediate path.
 * <p>
 * The batch a mod draws with its program bound used to be refused, because the immediate path's own shaders cannot
 * reproduce it (GTCEu's bloom passes are the measured case). This pipeline draws the same batch, in the same group
 * machinery, with the program's SPIR-V: the render state (render pass, blend, depth, cull) is copied from the variant
 * the path would have used for the batch, so the only difference is the shaders, their push-constant block (the
 * program's std140 uniforms, at most {@link ModGlslTranslator#MAX_PUSH_BYTES}) and their sampler set.
 * <p>
 * Vertex inputs follow GL's aliasing of generic attribute 0 with the conventional vertex position: the program's
 * first attribute takes the position, a lowered {@code celtium_MultiTexCoord0} the texture coordinate and a lowered
 * {@code celtium_Color} the colour. Any other attribute shape is refused with a reason.
 * <p>
 * One instance per (program, layout, render state); {@link Draw} is the per-batch half (uniform bytes and sampler set)
 * that a group carries as its prepared pipeline.
 */
public final class VulkanModProgramPipeline {

    /** The per-batch part a group draws: the pipeline, the packed uniforms and the sampler set. */
    public static final class Draw {
        final VulkanModProgramPipeline pipeline;
        final ByteBuffer uniforms;
        final long samplerSet;
        /** What this pass reads and writes, for the record-order trace; never read by the draw. */
        public final String label;
        /** The images the pass samples, for the mid-frame readback instrument only. */
        public final VulkanGlTexture[] inputs;
        /** Storage images whose layouts are transitioned around the fragment draw. */
        public final VulkanGlTexture[] images;

        Draw(VulkanModProgramPipeline pipeline, ByteBuffer uniforms, long samplerSet, String label,
             VulkanGlTexture[] inputs, VulkanGlTexture[] images) {
            this.pipeline = pipeline;
            this.uniforms = uniforms;
            this.samplerSet = samplerSet;
            this.label = label;
            this.inputs = inputs;
            this.images = images;
        }

        public void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, long[] bindCache) {
            this.pipeline.draw(commandBuffer, mesh, this.uniforms, this.samplerSet, this.images, bindCache);
        }
    }

    private final VulkanContext context;
    private final List<ModGlslTranslator.Attribute> attributes;
    private final List<ModGlslTranslator.SamplerBinding> samplers;
    private final int samplerCount;
    private final List<ModGlslTranslator.ImageBinding> images;
    private final int imageCount;
    private final int pushBytes;
    /** Whether the pass blends (its render state as built); the readback instrument looks for the blend-off copy. */
    boolean blends;
    private long setLayout = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private static final VulkanGlTexture[] EMPTY_IMAGES = new VulkanGlTexture[0];
    private static final long[] EMPTY_LONG = new long[0];
    private static final int[] EMPTY_INT = new int[0];
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap setsByKey =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private final long[] scratchViews;
    private final long[] scratchSamplers;
    private final int[] scratchLayouts;
    private final long[] scratchImageViews;
    private long lastSamplerKey;
    private long lastSamplerSet;
    /** Sets one pipeline may hold; a program samples a handful of framebuffer textures that rarely change. */
    private static final int MAX_SETS = 64;

    private VulkanModProgramPipeline(VulkanContext context, List<ModGlslTranslator.Attribute> attributes,
                                     List<ModGlslTranslator.SamplerBinding> samplers,
                                     List<ModGlslTranslator.ImageBinding> images, int pushBytes) {
        this.context = context;
        this.attributes = attributes;
        this.samplers = samplers;
        this.samplerCount = samplers.size();
        this.images = images;
        this.imageCount = images.size();
        this.pushBytes = pushBytes;
        this.scratchViews = this.samplerCount > 0 ? new long[this.samplerCount] : EMPTY_LONG;
        this.scratchSamplers = this.samplerCount > 0 ? new long[this.samplerCount] : EMPTY_LONG;
        this.scratchLayouts = this.samplerCount > 0 ? new int[this.samplerCount] : EMPTY_INT;
        this.scratchImageViews = this.imageCount > 0 ? new long[this.imageCount] : EMPTY_LONG;
    }

    /**
     * {@return why this program and batch layout cannot be drawn}, or null when they can. Checked before any Vulkan
     * object is created, so a refusal costs nothing.
     */
    public static String unsupported(ModProgramRegistry.Program program, int formatId) {
        if (program == null || program.lowered() == null || !program.lowered().ok()) {
            return "the program is not lowered";
        }
        if (!program.compiled()) {
            return "the program has no SPIR-V (" + program.compileFailure() + ")";
        }
        if (formatId != VulkanImmediateFormats.POSITION_TEX && formatId != VulkanImmediateFormats.POSITION_TEX_COLOR) {
            return "batch format " + formatId + " is not mapped onto program attributes";
        }
        List<ModGlslTranslator.Attribute> attributes = program.lowered().attributes();
        for (ModGlslTranslator.Attribute attribute : attributes) {
            if (attribute.location() == 0 || attribute.name().equals("celtium_MultiTexCoord0")) {
                continue;
            }
            if (attribute.name().equals("celtium_Color") && formatId == VulkanImmediateFormats.POSITION_TEX_COLOR) {
                continue;
            }
            return "attribute " + attribute.name() + " has no source in format " + formatId;
        }
        return null;
    }

    /** Builds the pipeline with the render state of {@code like}; throws on a Vulkan failure. */
    public static VulkanModProgramPipeline create(VulkanContext context, ModProgramRegistry.Program program,
                                                  int formatId, VulkanImmediateTexturePipeline like) {
        ModGlslTranslator.Result lowered = program.lowered();
        VulkanModProgramPipeline built = new VulkanModProgramPipeline(context, lowered.attributes(), lowered.samplers(),
                lowered.images(), lowered.uniformBlockSize());
        if (!lowered.images().isEmpty() && !context.celtium$storageImagesWithoutFormat()) {
            throw new IllegalStateException("storage images without format qualifiers are not supported by this device "
                    + "(shaderStorageImageRead/WriteWithoutFormat=false)");
        }
        try {
            built.createLayouts();
            built.createPipeline(program.vertexSpirv(), program.fragmentSpirv(), formatId,
                    like.celtium$renderPassHandle(), like.celtium$cull(), like.celtium$depthTest(),
                    like.celtium$depthWrite(), like.celtium$depthCompareOp(), like.celtium$colorBlend(), true);
        } catch (RuntimeException | LinkageError failure) {
            built.delete();
            throw failure;
        }
        return built;
    }

    /**
     * Builds a pipeline for a native fullscreen pass from pre-compiled SPIR-V and explicit pipeline state.
     * Reuses the same layout, descriptor pool, shader module and prepared-draw path as mod programs.
     */
    public static VulkanModProgramPipeline createForNativePass(VulkanContext context,
                                                               byte[] vertexSpirv,
                                                               byte[] fragmentSpirv,
                                                               int samplerCount,
                                                               int pushBytes,
                                                               VulkanColorBlendState colorBlend,
                                                               boolean depthTest,
                                                               boolean depthWrite,
                                                               int depthCompareOp,
                                                               int cull,
                                                               long renderPass) {
        if (vertexSpirv == null || fragmentSpirv == null) {
            throw new IllegalArgumentException("vertex and fragment SPIR-V must be non-null");
        }
        List<ModGlslTranslator.Attribute> attrs = List.of(
                new ModGlslTranslator.Attribute("position", "vec3", 0),
                new ModGlslTranslator.Attribute("celtium_MultiTexCoord0", "vec2", 1));
        java.util.ArrayList<ModGlslTranslator.SamplerBinding> bindings = new java.util.ArrayList<>(samplerCount);
        for (int i = 0; i < samplerCount; i++) {
            bindings.add(new ModGlslTranslator.SamplerBinding("input" + i, "sampler2D", i));
        }
        VulkanModProgramPipeline built = new VulkanModProgramPipeline(context, attrs,
                java.util.Collections.unmodifiableList(bindings), List.of(), Math.max(0, pushBytes));
        try {
            built.createLayouts();
            built.createPipeline(vertexSpirv, fragmentSpirv, VulkanImmediateFormats.POSITION_TEX, renderPass, cull,
                    depthTest, depthWrite, depthCompareOp,
                    colorBlend != null ? colorBlend : VulkanColorBlendState.legacy(), false);
        } catch (RuntimeException | LinkageError failure) {
            built.delete();
            throw failure;
        }
        return built;
    }

    /** Creates a prepared draw bound to this pipeline. */
    public Draw createDraw(ByteBuffer uniforms, long samplerSet, String label, VulkanGlTexture[] inputs) {
        return new Draw(this, uniforms, samplerSet, label, inputs, EMPTY_IMAGES);
    }

    public Draw createDraw(ByteBuffer uniforms, long descriptorSet, String label, VulkanGlTexture[] inputs,
                           VulkanGlTexture[] images) {
        return new Draw(this, uniforms, descriptorSet, label, inputs, images);
    }

    private void createLayouts() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(this.samplerCount + this.imageCount, stack);
            for (int i = 0; i < this.samplerCount; i++) {
                bindings.get(i).binding(i).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT);
            }
            for (int i = 0; i < this.imageCount; i++) {
                bindings.get(this.samplerCount + i).binding(this.samplerCount + i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            }
            VkDescriptorSetLayoutCreateInfo setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                    .pBindings(bindings);
            check(vkCreateDescriptorSetLayout(this.context.device(), setInfo, null, handle),
                    "vkCreateDescriptorSetLayout(mod program)");
            this.setLayout = handle.get(0);

            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(this.setLayout));
            if (this.pushBytes > 0) {
                VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
                range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT).offset(0)
                        .size(this.pushBytes);
                layoutInfo.pPushConstantRanges(range);
            }
            check(vkCreatePipelineLayout(this.context.device(), layoutInfo, null, handle),
                    "vkCreatePipelineLayout(mod program)");
            this.pipelineLayout = handle.get(0);

            if (this.samplerCount + this.imageCount > 0) {
                VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc((this.samplerCount > 0 ? 1 : 0)
                        + (this.imageCount > 0 ? 1 : 0), stack);
                int sizeIndex = 0;
                if (this.samplerCount > 0) sizes.get(sizeIndex++).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(MAX_SETS * this.samplerCount);
                if (this.imageCount > 0) sizes.get(sizeIndex).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .descriptorCount(MAX_SETS * this.imageCount);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                        .maxSets(MAX_SETS).pPoolSizes(sizes);
                check(vkCreateDescriptorPool(this.context.device(), poolInfo, null, handle),
                        "vkCreateDescriptorPool(mod program)");
                this.descriptorPool = handle.get(0);
            }
        }
    }

    private void createPipeline(byte[] vertexSpirv, byte[] fragmentSpirv, int formatId, long renderPass, int cull,
                                boolean depthTest, boolean depthWrite, int depthCompareOp,
                                VulkanColorBlendState colorBlend, boolean honourModProgramFlags) {
        ByteBuffer vertexCode = MemoryUtil.memAlloc(vertexSpirv.length).put(vertexSpirv).flip();
        ByteBuffer fragmentCode = MemoryUtil.memAlloc(fragmentSpirv.length).put(fragmentSpirv).flip();
        long vertexModule = VK_NULL_HANDLE;
        long fragmentModule = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(vertexCode);
            check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle), "vkCreateShaderModule(mod vertex)");
            vertexModule = handle.get(0);
            moduleInfo.pCode(fragmentCode);
            check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                    "vkCreateShaderModule(mod fragment)");
            fragmentModule = handle.get(0);

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule).pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                    .pName(stack.UTF8("main"));

            boolean colour = formatId == VulkanImmediateFormats.POSITION_TEX_COLOR;
            int stride = colour ? VulkanImmediateTexturePipeline.VERTEX_BYTES
                    : VulkanImmediateTexturePipeline.POSITION_TEX_BYTES;
            VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
            binding.get(0).binding(0).stride(stride).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            List<ModGlslTranslator.Attribute> declared = this.attributes;
            VkVertexInputAttributeDescription.Buffer attributes =
                    VkVertexInputAttributeDescription.calloc(declared.size(), stack);
            for (int i = 0; i < declared.size(); i++) {
                ModGlslTranslator.Attribute attribute = declared.get(i);
                VkVertexInputAttributeDescription out = attributes.get(i).location(attribute.location()).binding(0);
                if (attribute.name().equals("celtium_MultiTexCoord0")) {
                    out.format(VK_FORMAT_R32G32_SFLOAT).offset(12);
                } else if (attribute.name().equals("celtium_Color")) {
                    out.format(VK_FORMAT_R8G8B8A8_UNORM).offset(20);
                } else {
                    // Generic attribute 0 aliases the conventional vertex position (unsupported() admitted no other).
                    out.format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                }
            }
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default().pVertexBindingDescriptions(binding).pVertexAttributeDescriptions(attributes);
            VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default()
                    .viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default().polygonMode(VK_POLYGON_MODE_FILL).depthBiasEnable(true)
                    .cullMode(VulkanCullState.vkCullMode(cull)).frontFace(VulkanCullState.vkFrontFace(cull))
                    .lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            // Lab switch -Dceltium.vulkan.modProgramDepth=false: no depth test or write for mod passes, to separate a
            // full-screen quad's depth from its colour when a pass misbehaves (round 8 of the GTCEu bloom evidence).
            boolean depthOn = !honourModProgramFlags
                    || !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modProgramDepth", "true"));
            VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default().depthTestEnable(depthOn && depthTest)
                    .depthWriteEnable(depthOn && depthWrite).depthCompareOp(depthCompareOp);
            VulkanColorBlendState effectiveBlend = colorBlend;
            if (honourModProgramFlags && FORCE_ADDITIVE_OPAQUE && !effectiveBlend.blend()) {
                // Lab: a blend-off pass drawn additively instead, so a black result says "it read black" and a
                // saturated one says "it read the picture and the output was lost" (round 8f of the bloom evidence).
                effectiveBlend = VulkanColorBlendState.of(true, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE,
                        VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE, VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, 0xF);
            }
            this.blends = effectiveBlend.blend();
            VkPipelineColorBlendAttachmentState.Buffer blendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachment.get(0).blendEnable(effectiveBlend.blend())
                    .srcColorBlendFactor(effectiveBlend.srcColor()).dstColorBlendFactor(effectiveBlend.dstColor())
                    .colorBlendOp(effectiveBlend.colorOp())
                    .srcAlphaBlendFactor(effectiveBlend.srcAlpha()).dstAlphaBlendFactor(effectiveBlend.dstAlpha())
                    .alphaBlendOp(effectiveBlend.alphaOp()).colorWriteMask(effectiveBlend.writeMask());
            VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default()
                    .logicOpEnable(false).pAttachments(blendAttachment);
            // The same dynamic states as the immediate variants, so the group loop's per-group commands stay valid.
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR,
                            VK_DYNAMIC_STATE_DEPTH_BIAS));
            VkGraphicsPipelineCreateInfo.Buffer create = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput)
                    .pInputAssemblyState(assembly).pViewportState(viewport).pRasterizationState(raster)
                    .pMultisampleState(multisample).pDepthStencilState(depth).pColorBlendState(blend)
                    .pDynamicState(dynamic).layout(this.pipelineLayout).renderPass(renderPass)
                    .subpass(0);
            check(VulkanContext.createGraphicsPipelines(this.context.device(), create, handle),
                    "vkCreateGraphicsPipelines(mod program)");
            this.pipeline = handle.get(0);
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

    /**
     * {@return the sampler set for these textures (binding i = textures[i])}, cached by their keys; 0 when the pool is
     * exhausted, which the caller refuses as a missing draw.
     */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public long samplerSet(VulkanGlTexture[] textures) {
        return samplersAndImages(textures, EMPTY_IMAGES);
    }

    /** Builds the one descriptor set from sampler bindings followed by storage-image bindings. */
    public long samplersAndImages(VulkanGlTexture[] textures, VulkanGlTexture[] imageTextures) {
        if (this.samplerCount + this.imageCount == 0) {
            return 0L;
        }
        long hash = 0x9E3779B97F4A7C15L;
        long[] views = this.scratchViews;
        long[] samplers = this.scratchSamplers;
        int[] layouts = this.scratchLayouts;
        for (int i = 0; i < this.samplerCount; i++) {
            VulkanGlTexture tex = textures[i];
            long view = tex.view();
            long sampler = tex.sampler();
            int layout = tex.samplingLayout();
            views[i] = view;
            samplers[i] = sampler;
            layouts[i] = layout;
            hash = mix64(hash ^ tex.key()) ^ mix64(view ^ (sampler * 31L) ^ layout);
        }
        long[] imageViews = this.scratchImageViews;
        for (int i = 0; i < this.imageCount; i++) {
            VulkanGlTexture img = imageTextures[i];
            long view = img.view();
            imageViews[i] = view;
            hash = mix64(hash ^ img.key() ^ 0x517CC1B727220A95L) ^ mix64(view);
        }
        if (hash == 0L) {
            hash = 1L;
        }
        return allocateOrReuseDescriptorSet(hash, views, samplers, layouts, imageViews);
    }

    /** Allocates or reuses a descriptor set binding explicit image views, samplers and layouts. */
    public long samplerSetForHandles(long[] views, long[] samplers, int[] layouts, long[] generations) {
        return samplerSetForHandles(views, samplers, layouts, generations, this.samplerCount);
    }

    public long samplerSetForHandles(long[] views, long[] samplers, int[] layouts, long[] generations, int count) {
        if (this.samplerCount == 0 || count <= 0) {
            return 0L;
        }
        long hash = 0x9E3779B97F4A7C15L;
        int n = Math.min(this.samplerCount, count);
        for (int i = 0; i < n; i++) {
            hash = mix64(hash ^ views[i]) ^ mix64(samplers[i]) ^ mix64(generations[i] ^ ((long) layouts[i] << 32));
        }
        if (hash == 0L) {
            hash = 1L;
        }
        return allocateOrReuseDescriptorSet(hash, views, samplers, layouts, EMPTY_LONG);
    }

    private long allocateOrReuseDescriptorSet(long key, long[] views, long[] samplers, int[] layouts,
                                              long[] imageViews) {
        if (key == this.lastSamplerKey && this.lastSamplerSet != 0L) {
            return this.lastSamplerSet;
        }
        long cached = this.setsByKey.get(key);
        if (cached != 0L) {
            this.lastSamplerKey = key;
            this.lastSamplerSet = cached;
            return cached;
        }
        if (this.setsByKey.size() >= MAX_SETS) {
            return 0L;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(this.descriptorPool).pSetLayouts(stack.longs(this.setLayout));
            LongBuffer handle = stack.mallocLong(1);
            if (vkAllocateDescriptorSets(this.context.device(), allocate, handle) != VK_SUCCESS) {
                return 0L;
            }
            long set = handle.get(0);
            VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(this.samplerCount + this.imageCount, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(this.samplerCount + this.imageCount, stack);
            for (int i = 0; i < this.samplerCount; i++) {
                imageInfos.get(i).imageLayout(layouts[i]).imageView(views[i]).sampler(samplers[i]);
                writes.get(i).sType$Default().dstSet(set).dstBinding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
                        .pImageInfo(VkDescriptorImageInfo.create(imageInfos.address(i), 1));
            }
            for (int i = 0; i < this.imageCount; i++) {
                int index = this.samplerCount + i;
                imageInfos.get(index).imageLayout(VK_IMAGE_LAYOUT_GENERAL).imageView(imageViews[i]).sampler(VK_NULL_HANDLE);
                writes.get(index).sType$Default().dstSet(set).dstBinding(index)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1)
                        .pImageInfo(VkDescriptorImageInfo.create(imageInfos.address(index), 1));
            }
            vkUpdateDescriptorSets(this.context.device(), writes, null);
            this.setsByKey.put(key, set);
            this.lastSamplerKey = key;
            this.lastSamplerSet = set;
            return set;
        }
    }

    /**
     * {@return the program's uniform block packed at its std140 offsets} from the values {@code glUniform*} recorded;
     * a uniform never set is zero, as in GL.
     */
    public static ByteBuffer packUniforms(ModProgramRegistry.Program program) {
        ByteBuffer cached = program.cachedPackedUniforms();
        if (cached != null) {
            return cached;
        }
        long version = program.uniformVersion();
        ModGlslTranslator.Result lowered = program.lowered();
        ByteBuffer block = ByteBuffer.allocateDirect(Math.max(16, lowered.uniformBlockSize()))
                .order(java.nio.ByteOrder.nativeOrder());
        for (ModGlslTranslator.UniformMember member : lowered.uniforms()) {
            float[] floats = program.floats(member.name());
            int[] ints = program.ints(member.name());
            if (floats == null && ints == null) {
                continue;
            }
            String type = member.type();
            int components = componentsOf(type);
            int columns = columnsOf(type);
            int elements = Math.max(1, member.arrayLength());
            boolean integerType = type.startsWith("i") || type.startsWith("u")
                    || type.equals("int") || type.equals("bool");
            // std140: an array element or a matrix column starts on a 16-byte boundary; a plain vector packs tightly.
            int columnStride = (member.arrayLength() > 0 || columns > 1) ? 16 : components * 4;
            int value = 0;
            for (int e = 0; e < elements; e++) {
                for (int c = 0; c < columns; c++) {
                    int base = member.offset() + (e * columns + c) * columnStride;
                    for (int k = 0; k < components; k++, value++) {
                        int at = base + k * 4;
                        if (at + 4 > block.capacity()) {
                            continue;
                        }
                        if (floats != null && value < floats.length) {
                            block.putFloat(at, floats[value]);
                        } else if (ints != null && value < ints.length) {
                            if (integerType) {
                                block.putInt(at, ints[value]);
                            } else {
                                block.putFloat(at, ints[value]);
                            }
                        }
                    }
                }
            }
        }
        program.cachePackedUniforms(version, block);
        return block;
    }

    private static int componentsOf(String type) {
        return switch (type) {
            case "vec2", "ivec2", "uvec2", "bvec2", "mat2" -> 2;
            case "vec3", "ivec3", "uvec3", "bvec3", "mat3" -> 3;
            case "vec4", "ivec4", "uvec4", "bvec4", "mat4" -> 4;
            default -> 1;
        };
    }

    private static int columnsOf(String type) {
        return switch (type) {
            case "mat2" -> 2;
            case "mat3" -> 3;
            case "mat4" -> 4;
            default -> 1;
        };
    }

    void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, ByteBuffer uniforms, long samplerSet,
              long[] bindCache) {
        draw(commandBuffer, mesh, uniforms, samplerSet, EMPTY_IMAGES, bindCache);
    }

    void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, ByteBuffer uniforms, long samplerSet,
              VulkanGlTexture[] images, long[] bindCache) {
        if (this.pipeline == VK_NULL_HANDLE || mesh == null || mesh.isEmpty()) {
            return;
        }
        // A descriptor may only name GENERAL once it has been transitioned there in this command buffer. Keep this
        // before pipeline/set binding so the bound descriptor and its declared layout agree from first use.
        if (!SKIP_DRAW_CALL) {
            for (VulkanGlTexture image : images) image.ensureGeneralLayout(commandBuffer);
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        // The shared caches describe the immediate variants' layouts: after this pipeline they are stale. The next
        // variant rebinds its pipeline and set, and pushes its block again (push constants do not survive a layout
        // that is incompatible for them).
        bindCache[4] = this.pipeline;
        bindCache[5] = -1L;
        VulkanPushCache.SHARED.invalidate();
        if (samplerSet != 0L) {
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                    MemoryStack.stackGet().longs(samplerSet), null);
        }
        if (this.pushBytes > 0 && uniforms != null) {
            uniforms.position(0).limit(this.pushBytes);
            vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, uniforms);
        }
        if (SKIP_DRAW_CALL) {
            // Lab: every bind of the pass without its pixels, to tell "the pass's pixels are wrong" from "the pass
            // leaves state behind that breaks what is drawn after it".
            return;
        }
        mesh.bindAndDraw(commandBuffer, bindCache);
        for (VulkanGlTexture image : images) image.ensureSampledLayout(commandBuffer);
    }

    private static final boolean FORCE_ADDITIVE_OPAQUE = Boolean.getBoolean("celtium.vulkan.modProgramAdditive");
    private static final boolean SKIP_DRAW_CALL = Boolean.getBoolean("celtium.vulkan.modProgramSkipDraw");

    public int samplerCount() {
        return this.samplerCount;
    }

    public List<ModGlslTranslator.SamplerBinding> samplers() {
        return this.samplers;
    }

    /** Destroys the Vulkan objects; the caller retires it after the frames that used it completed. */
    public void delete() {
        VkDevice device = this.context.device();
        if (this.pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, this.pipeline, null);
            this.pipeline = VK_NULL_HANDLE;
        }
        if (this.descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, this.descriptorPool, null);
            this.descriptorPool = VK_NULL_HANDLE;
        }
        if (this.pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, this.pipelineLayout, null);
            this.pipelineLayout = VK_NULL_HANDLE;
        }
        if (this.setLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, this.setLayout, null);
            this.setLayout = VK_NULL_HANDLE;
        }
        this.setsByKey.clear();
        this.lastSamplerKey = 0L;
        this.lastSamplerSet = 0L;
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
