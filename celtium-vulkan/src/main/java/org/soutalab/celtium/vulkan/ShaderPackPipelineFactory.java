package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
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
     * Builds set 0 from the interface the lowering declared: binding 0 is the transform block, binding 1 the pack's own
     * uniforms, and each sampler takes the binding the lowering assigned to it.
     */
    public LayoutHandle createLayout(LegacyGlslLowering.Result lowered) {
        List<int[]> bindings = new ArrayList<>();
        // Only the bindings the program actually declares: a layout that names a binding no shader uses is what
        // VUID-VkDescriptorSetLayoutCreateInfo-binding-00279 reports, and a fragment stage that touches no
        // fixed-function matrix has no transform block at all.
        boolean transformBlock = !lowered.usedTransformBlock().isEmpty();
        int uniformBlockBinding = lowered.uniforms().isEmpty() ? -1 : 1;
        if (transformBlock) {
            bindings.add(new int[] {0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1});
        }
        if (uniformBlockBinding >= 0) {
            bindings.add(new int[] {uniformBlockBinding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1});
        }
        for (Integer binding : lowered.samplers().values()) {
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
            return new LayoutHandle(context.device(), descriptorLayout.get(0), pipelineLayout.get(0),
                    uniformBlockBinding, lowered.samplers());
        }
    }
}
