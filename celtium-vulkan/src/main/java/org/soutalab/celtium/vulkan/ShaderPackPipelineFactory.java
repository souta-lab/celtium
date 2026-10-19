package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.soutalab.celtium.shaderpack.LegacyGlslLowering;
import org.soutalab.celtium.shaderpack.SpirvModule;
import org.soutalab.celtium.shaderpack.Stage;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Turns a compiled pack program into the Vulkan objects a draw needs: shader modules, a descriptor set layout for the
 * interface the lowering assigned, and a pipeline layout.
 * <p>
 * Deliberately stops short of the graphics pipeline itself. A pack's fragment stage writes however many colour
 * attachments its {@code DRAWBUFFERS} line asks for - BSL's terrain program writes several - and a {@code VkPipeline} has
 * to be created against render targets whose formats match. This renderer's pipelines are all built against explicit
 * render passes today, so the pipeline for a pack needs either a compatible render pass per output set or dynamic
 * rendering, and that decision belongs with the device features rather than with this class. Modules and layouts need
 * neither, and they are what the pipeline will be assembled from.
 */
public final class ShaderPackPipelineFactory {
    private final VulkanContext context;

    public ShaderPackPipelineFactory(VulkanContext context) {
        this.context = context;
    }

    /** A created module; {@link #close()} frees it. */
    public record ModuleHandle(VkDevice device, long module, Stage stage) implements AutoCloseable {
        @Override public void close() {
            if (module != 0L) {
                vkDestroyShaderModule(device, module, null);
            }
        }
    }

    /** What one program's interface produced, with the handles a pipeline layout is built from. */
    public record LayoutHandle(VkDevice device, long descriptorSetLayout, long pipelineLayout,
                               int uniformBlockBinding, Map<String, Integer> samplerBindings)
            implements AutoCloseable {
        @Override public void close() {
            if (pipelineLayout != 0L) {
                vkDestroyPipelineLayout(device, pipelineLayout, null);
            }
            if (descriptorSetLayout != 0L) {
                vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            }
        }
    }

    /** Creates a shader module from a compiled stage. */
    public ModuleHandle createModule(SpirvModule module) {
        ByteBuffer code = org.lwjgl.system.MemoryUtil.memAlloc(module.binary().length);
        try {
            code.put(module.binary()).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default()
                        .pCode(code);
                LongBuffer handle = stack.mallocLong(1);
                int result = vkCreateShaderModule(context.device(), info, null, handle);
                if (result != VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateShaderModule failed: " + result);
                }
                return new ModuleHandle(context.device(), handle.get(0), module.stage());
            }
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(code);
        }
    }

    /**
     * Creates a graphics pipeline for a pack program using dynamic rendering.
     * <p>
     * Dynamic rendering is what makes this possible at all: a pack's fragment stage writes however many colour
     * attachments its DRAWBUFFERS line asks for, and a pipeline built against a fixed render pass would have to match
     * that set exactly. The caller passes the formats of the targets it will render into; the pipeline itself comes from
     * the lowered interface, one vertex binding per attribute (each attribute in its own buffer - the interleaved layout
     * the game's own batches use is a separate decision, and this pipeline exists to prove the pack's shaders can be
     * built at all).
     */
    public long createPipeline(long pipelineLayout, ModuleHandle vertex, ModuleHandle fragment,
                               LegacyGlslLowering.Result vertexInterface, int colourFormat, int depthFormat) {
        return createPipeline(pipelineLayout, vertex, fragment, vertexInterface, colourFormat, depthFormat, true);
    }

    /**
     * @param depthTest      whether the pipeline tests and writes depth. The probe draws a triangle that would sit
     *                       behind the frame's own geometry - the depth buffer already holds the world - and with depth
     *                       testing on it is simply invisible; a real draw tests depth, so this is a parameter and not a
     *                       constant
     */
    public long createPipeline(long pipelineLayout, ModuleHandle vertex, ModuleHandle fragment,
                               LegacyGlslLowering.Result vertexInterface, int colourFormat, int depthFormat,
                               boolean depthTest) {
        if (vertex == null || fragment == null || vertex.stage() != Stage.VERTEX
                || fragment.stage() != Stage.FRAGMENT) {
            throw new IllegalArgumentException("a pack pipeline needs one vertex and one fragment module");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertex.module())
                    .pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragment.module())
                    .pName(stack.UTF8("main"));

            Map<String, Integer> attributes = vertexInterface.attributes();
            VkVertexInputAttributeDescription.Buffer attributeDescriptions =
                    VkVertexInputAttributeDescription.calloc(attributes.size(), stack);
            VkVertexInputBindingDescription.Buffer bindingDescriptions =
                    VkVertexInputBindingDescription.calloc(attributes.size(), stack);
            int index = 0;
            java.util.List<String> ordered = new java.util.ArrayList<>(attributes.keySet());
            ordered.sort(java.util.Comparator.comparingInt(attributes::get));
            for (String name : ordered) {
                // Only attributes the shader actually reads. A pack declares attributes it does not use in a given
                // program (BSL's mc_Entity is declared by the shared header, used only by some stages), and declaring a
                // vertex input for one is "Vertex attribute at location N not consumed by vertex shader" - a warning,
                // and this project's acceptance gate counts messages rather than only errors. The declaration itself is
                // one occurrence of the name in the lowered source, so more than one means a use.
                if (occurrences(vertexInterface.source(), name) <= 1) {
                    continue;
                }
                String type = vertexInterface.attributeTypes().get(name);
                int[] format = vertexFormat(type);
                if (format == null) {
                    continue;
                }
                int location = attributes.get(name).intValue();
                bindingDescriptions.get(index).binding(location).stride(format[1])
                        .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                attributeDescriptions.get(index).location(location).binding(location).format(format[0]).offset(0);
                index++;
            }
            bindingDescriptions.limit(index);
            attributeDescriptions.limit(index);

            VkPipelineVertexInputStateCreateInfo vertexInput =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default()
                            .pVertexBindingDescriptions(bindingDescriptions)
                            .pVertexAttributeDescriptions(attributeDescriptions);
            VkPipelineInputAssemblyStateCreateInfo assembly =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
                            .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default().polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default().depthTestEnable(depthTest).depthWriteEnable(depthTest)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
            VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachment.get(0).colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                    | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default().pAttachments(blendAttachment);
            org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamic =
                    org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                            .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            // Dynamic rendering: the targets are declared here instead of by a render pass.
            org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR rendering =
                    org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR.calloc(stack).sType$Default()
                            .colorAttachmentCount(1)
                            .pColorAttachmentFormats(stack.ints(colourFormat))
                            .depthAttachmentFormat(depthFormat);
            org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer info =
                    org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.calloc(1, stack);
            info.get(0).sType$Default()
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(assembly)
                    .pViewportState(viewport)
                    .pRasterizationState(raster)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depth)
                    .pColorBlendState(blend)
                    .pDynamicState(dynamic)
                    .layout(pipelineLayout)
                    .renderPass(VK_NULL_HANDLE)
                    .pNext(rendering.address());
            LongBuffer handle = stack.mallocLong(1);
            int result = vkCreateGraphicsPipelines(context.device(), VK_NULL_HANDLE, info, null, handle);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateGraphicsPipelines failed: " + result);
            }
            return handle.get(0);
        }
    }

    /** {@return how often a whole-word identifier occurs in the text}. */
    private static int occurrences(String text, String identifier) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(identifier, from);
            if (at < 0) {
                return count;
            }
            boolean leftOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            int after = at + identifier.length();
            boolean rightOk = after >= text.length() || !Character.isLetterOrDigit(text.charAt(after));
            if (leftOk && rightOk) {
                count++;
            }
            from = at + 1;
        }
    }

    /** {@return the VkFormat and byte size of a GLSL attribute type}, or null when it cannot be a vertex input. */
    private static int[] vertexFormat(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case "float": return new int[] {VK_FORMAT_R32_SFLOAT, 4};
            case "vec2": return new int[] {VK_FORMAT_R32G32_SFLOAT, 8};
            case "vec3": return new int[] {VK_FORMAT_R32G32B32_SFLOAT, 12};
            case "vec4": return new int[] {VK_FORMAT_R32G32B32A32_SFLOAT, 16};
            case "int": return new int[] {VK_FORMAT_R32_SINT, 4};
            case "ivec2": return new int[] {VK_FORMAT_R32G32_SINT, 8};
            case "ivec3": return new int[] {VK_FORMAT_R32G32B32_SINT, 12};
            case "ivec4": return new int[] {VK_FORMAT_R32G32B32A32_SINT, 16};
            default: return null;
        }
    }

    /**
     * Builds set 0 from the interface the lowering declared: binding 0 is the transform block, binding 1 the pack's own
     * uniforms, and each sampler takes the binding the lowering assigned to it.
     */
    /**
     * Builds set 0 from both stages of one program.
     * <p>
     * A binding is only legal when some shader uses it (VUID-VkGraphicsPipelineCreateInfo-layout-07990), and a pack's
     * two stages do not declare the same things - the fragment stage samples textures the vertex stage has never heard
     * of. The union is what the pipeline layout has to describe; a layout built from one stage left the other's
     * bindings out and declared ones nothing used.
     */
    public LayoutHandle createLayout(LegacyGlslLowering.Result vertex, LegacyGlslLowering.Result fragment) {
        boolean transformBlock = !vertex.usedTransformBlock().isEmpty() || !fragment.usedTransformBlock().isEmpty();
        boolean uniformBlock = !vertex.uniforms().isEmpty() || !fragment.uniforms().isEmpty();
        java.util.Set<Integer> samplerBindings = new java.util.TreeSet<>();
        samplerBindings.addAll(vertex.samplers().values());
        samplerBindings.addAll(fragment.samplers().values());
        return createLayout(transformBlock, uniformBlock, samplerBindings, vertex.samplers(), fragment.samplers());
    }

    public LayoutHandle createLayout(LegacyGlslLowering.Result lowered) {
        boolean transformBlock = !lowered.usedTransformBlock().isEmpty();
        boolean uniformBlock = !lowered.uniforms().isEmpty();
        return createLayout(transformBlock, uniformBlock,
                new java.util.TreeSet<>(lowered.samplers().values()), lowered.samplers(), java.util.Map.of());
    }

    private LayoutHandle createLayout(boolean transformBlock, boolean uniformBlock,
                                      java.util.Set<Integer> samplerBindings,
                                      Map<String, Integer> samplersA, Map<String, Integer> samplersB) {
        List<int[]> bindings = new ArrayList<>();
        // Only the bindings the program actually declares: a layout that names a binding no shader uses is what
        // VUID-VkDescriptorSetLayoutCreateInfo-binding-00279 reports, and a fragment stage that touches no
        // fixed-function matrix has no transform block at all.
        int uniformBlockBinding = uniformBlock ? 1 : -1;
        if (transformBlock) {
            bindings.add(new int[] {0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1});
        }
        if (uniformBlockBinding >= 0) {
            bindings.add(new int[] {uniformBlockBinding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1});
        }
        for (Integer binding : samplerBindings) {
            bindings.add(new int[] {binding.intValue(), VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1});
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer layoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(bindings.size(), stack);
            for (int i = 0; i < bindings.size(); i++) {
                int[] b = bindings.get(i);
                layoutBindings.get(i).binding(b[0]).descriptorType(b[1]).descriptorCount(b[2])
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            }
            LongBuffer descriptorLayout = stack.mallocLong(1);
            int result = vkCreateDescriptorSetLayout(context.device(),
                    VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(layoutBindings),
                    null, descriptorLayout);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDescriptorSetLayout failed: " + result);
            }
            LongBuffer setLayouts = stack.mallocLong(1).put(0, descriptorLayout.get(0));
            LongBuffer pipelineLayout = stack.mallocLong(1);
            result = vkCreatePipelineLayout(context.device(),
                    VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(setLayouts),
                    null, pipelineLayout);
            if (result != VK_SUCCESS) {
                vkDestroyDescriptorSetLayout(context.device(), descriptorLayout.get(0), null);
                throw new IllegalStateException("vkCreatePipelineLayout failed: " + result);
            }
            Map<String, Integer> merged = new java.util.TreeMap<>(samplersA);
            merged.putAll(samplersB);
            return new LayoutHandle(context.device(), descriptorLayout.get(0), pipelineLayout.get(0),
                    uniformBlockBinding, merged);
        }
    }
}
