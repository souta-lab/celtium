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

        Draw(VulkanModProgramPipeline pipeline, ByteBuffer uniforms, long samplerSet, String label,
             VulkanGlTexture[] inputs) {
            this.pipeline = pipeline;
            this.uniforms = uniforms;
            this.samplerSet = samplerSet;
            this.label = label;
            this.inputs = inputs;
        }

        public void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, long[] bindCache) {
            this.pipeline.draw(commandBuffer, mesh, this.uniforms, this.samplerSet, bindCache);
        }
    }

    private final VulkanContext context;
    private final ModGlslTranslator.Result lowered;
    private final int samplerCount;
    private final int pushBytes;
    /** Whether the pass blends (its render state as built); the readback instrument looks for the blend-off copy. */
    boolean blends;
    private long setLayout = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private final Map<String, Long> setsByTextures = new HashMap<>();
    /** Sets one pipeline may hold; a program samples a handful of framebuffer textures that rarely change. */
    private static final int MAX_SETS = 64;

    private VulkanModProgramPipeline(VulkanContext context, ModGlslTranslator.Result lowered) {
        this.context = context;
        this.lowered = lowered;
        this.samplerCount = lowered.samplers().size();
        this.pushBytes = lowered.uniformBlockSize();
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
        VulkanModProgramPipeline built = new VulkanModProgramPipeline(context, program.lowered());
        try {
            built.createLayouts();
            built.createPipeline(program.vertexSpirv(), program.fragmentSpirv(), formatId, like);
        } catch (RuntimeException | LinkageError failure) {
            built.delete();
            throw failure;
        }
        return built;
    }

    private void createLayouts() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(this.samplerCount, stack);
            for (int i = 0; i < this.samplerCount; i++) {
                bindings.get(i).binding(i).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT);
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

            if (this.samplerCount > 0) {
                VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
                sizes.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(MAX_SETS * this.samplerCount);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                        .maxSets(MAX_SETS).pPoolSizes(sizes);
                check(vkCreateDescriptorPool(this.context.device(), poolInfo, null, handle),
                        "vkCreateDescriptorPool(mod program)");
                this.descriptorPool = handle.get(0);
            }
        }
    }

    private void createPipeline(byte[] vertexSpirv, byte[] fragmentSpirv, int formatId,
                                VulkanImmediateTexturePipeline like) {
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
            List<ModGlslTranslator.Attribute> declared = this.lowered.attributes();
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
            int cull = like.celtium$cull();
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default().polygonMode(VK_POLYGON_MODE_FILL).depthBiasEnable(true)
                    .cullMode(VulkanCullState.vkCullMode(cull)).frontFace(VulkanCullState.vkFrontFace(cull))
                    .lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            // Lab switch -Dceltium.vulkan.modProgramDepth=false: no depth test or write for mod passes, to separate a
            // full-screen quad's depth from its colour when a pass misbehaves (round 8 of the GTCEu bloom evidence).
            boolean depthOn = !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modProgramDepth", "true"));
            VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default().depthTestEnable(depthOn && like.celtium$depthTest())
                    .depthWriteEnable(depthOn && like.celtium$depthWrite()).depthCompareOp(like.celtium$depthCompareOp());
            VulkanColorBlendState colorBlend = like.celtium$colorBlend();
            if (FORCE_ADDITIVE_OPAQUE && !colorBlend.blend()) {
                // Lab: a blend-off pass drawn additively instead, so a black result says "it read black" and a
                // saturated one says "it read the picture and the output was lost" (round 8f of the bloom evidence).
                colorBlend = VulkanColorBlendState.of(true, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE,
                        VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE, VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, 0xF);
            }
            this.blends = colorBlend.blend();
            VkPipelineColorBlendAttachmentState.Buffer blendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachment.get(0).blendEnable(colorBlend.blend())
                    .srcColorBlendFactor(colorBlend.srcColor()).dstColorBlendFactor(colorBlend.dstColor())
                    .colorBlendOp(colorBlend.colorOp())
                    .srcAlphaBlendFactor(colorBlend.srcAlpha()).dstAlphaBlendFactor(colorBlend.dstAlpha())
                    .alphaBlendOp(colorBlend.alphaOp()).colorWriteMask(colorBlend.writeMask());
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
                    .pDynamicState(dynamic).layout(this.pipelineLayout).renderPass(like.celtium$renderPassHandle())
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
    public long samplerSet(VulkanGlTexture[] textures) {
        if (this.samplerCount == 0) {
            return 0L;
        }
        StringBuilder key = new StringBuilder();
        for (VulkanGlTexture texture : textures) {
            key.append(texture.key()).append(',');
        }
        Long cached = this.setsByTextures.get(key.toString());
        if (cached != null) {
            return cached.longValue();
        }
        if (this.setsByTextures.size() >= MAX_SETS) {
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
            VkDescriptorImageInfo.Buffer images = VkDescriptorImageInfo.calloc(this.samplerCount, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(this.samplerCount, stack);
            for (int i = 0; i < this.samplerCount; i++) {
                images.get(i).imageLayout(textures[i].samplingLayout()).imageView(textures[i].view())
                        .sampler(textures[i].sampler());
                writes.get(i).sType$Default().dstSet(set).dstBinding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
                        .pImageInfo(VkDescriptorImageInfo.create(images.address(i), 1));
            }
            vkUpdateDescriptorSets(this.context.device(), writes, null);
            this.setsByTextures.put(key.toString(), Long.valueOf(set));
            return set;
        }
    }

    /**
     * {@return the program's uniform block packed at its std140 offsets} from the values {@code glUniform*} recorded;
     * a uniform never set is zero, as in GL.
     */
    public static ByteBuffer packUniforms(ModProgramRegistry.Program program) {
        ModGlslTranslator.Result lowered = program.lowered();
        ByteBuffer block = ByteBuffer.allocateDirect(Math.max(16, lowered.uniformBlockSize()))
                .order(java.nio.ByteOrder.nativeOrder());
        for (ModGlslTranslator.UniformMember member : lowered.uniforms()) {
            float[] floats = program.floats(member.name());
            int[] ints = program.ints(member.name());
            int components = componentsOf(member.type());
            int columns = columnsOf(member.type());
            int elements = Math.max(1, member.arrayLength());
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
                            if (member.type().startsWith("i") || member.type().startsWith("u")
                                    || member.type().equals("int") || member.type().equals("bool")) {
                                block.putInt(at, ints[value]);
                            } else {
                                block.putFloat(at, ints[value]);
                            }
                        }
                    }
                }
            }
        }
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
        if (this.pipeline == VK_NULL_HANDLE || mesh == null || mesh.isEmpty()) {
            return;
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        // The shared caches describe the immediate variants' layouts: after this pipeline they are stale. The next
        // variant rebinds its pipeline and set, and pushes its block again (push constants do not survive a layout
        // that is incompatible for them).
        bindCache[4] = this.pipeline;
        bindCache[5] = -1L;
        VulkanPushCache.SHARED.invalidate();
        if (samplerSet != 0L) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                        stack.longs(samplerSet), null);
            }
        }
        if (this.pushBytes > 0) {
            ByteBuffer view = uniforms.duplicate();
            view.position(0).limit(this.pushBytes);
            vkCmdPushConstants(commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                    0, view);
        }
        if (SKIP_DRAW_CALL) {
            // Lab: every bind of the pass without its pixels, to tell "the pass's pixels are wrong" from "the pass
            // leaves state behind that breaks what is drawn after it".
            return;
        }
        mesh.bindAndDraw(commandBuffer, bindCache);
    }

    private static final boolean FORCE_ADDITIVE_OPAQUE = Boolean.getBoolean("celtium.vulkan.modProgramAdditive");
    private static final boolean SKIP_DRAW_CALL = Boolean.getBoolean("celtium.vulkan.modProgramSkipDraw");

    public int samplerCount() {
        return this.samplerCount;
    }

    public List<ModGlslTranslator.SamplerBinding> samplers() {
        return this.lowered.samplers();
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
        this.setsByTextures.clear();
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
