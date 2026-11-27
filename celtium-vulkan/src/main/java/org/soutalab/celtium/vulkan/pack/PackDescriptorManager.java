/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.soutalab.celtium.core.shader.DescriptorBindingPlan;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Descriptor layouts, pipeline layouts, the pool and per-frame-slot sets for every program of
 * the active pack. Layouts come from the compiler's binding plans (graphics) and named
 * bindings (compute); sets are written by the executor once per frame slot, right before the
 * pass that binds them records, so a set is never rewritten while a submitted frame still
 * references it.
 */
final class PackDescriptorManager implements AutoCloseable {

    /** One layout binding of a program. */
    record LayoutBinding(int binding, DescriptorBindingPlan.Kind kind, int stageMask, int count) {
    }

    /** One resolved descriptor write. Exactly one of the buffer / image shapes is used. */
    record Write(int binding, DescriptorBindingPlan.Kind kind,
                 long buffer, long bufferOffset, long bufferRange,
                 long imageView, long sampler, int imageLayout) {

        static Write uniformBuffer(int binding, long buffer, long offset, long range) {
            return new Write(binding, DescriptorBindingPlan.Kind.UNIFORM_BUFFER,
                    buffer, offset, range, 0L, 0L, 0);
        }

        static Write storageBuffer(int binding, long buffer, long offset, long range) {
            return new Write(binding, DescriptorBindingPlan.Kind.STORAGE_BUFFER,
                    buffer, offset, range, 0L, 0L, 0);
        }

        static Write combinedImage(int binding, long imageView, long sampler) {
            return new Write(binding, DescriptorBindingPlan.Kind.COMBINED_IMAGE_SAMPLER,
                    0L, 0L, 0L, imageView, sampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }

        static Write sampledImage(int binding, long imageView) {
            return new Write(binding, DescriptorBindingPlan.Kind.SAMPLED_IMAGE,
                    0L, 0L, 0L, imageView, 0L, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }

        static Write storageImage(int binding, long imageView) {
            return new Write(binding, DescriptorBindingPlan.Kind.STORAGE_IMAGE,
                    0L, 0L, 0L, imageView, 0L, VK_IMAGE_LAYOUT_GENERAL);
        }

        static Write samplerOnly(int binding, long sampler) {
            return new Write(binding, DescriptorBindingPlan.Kind.SAMPLER,
                    0L, 0L, 0L, 0L, sampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }
    }

    private static final class ProgramDescriptors {
        long layout;
        long pipelineLayout;
        final long[] sets;

        ProgramDescriptors(int slots) {
            this.sets = new long[slots];
        }
    }

    private final VulkanContext context;
    private final int slots;
    private final Map<String, ProgramDescriptors> programs = new LinkedHashMap<>();
    private long pool;

    PackDescriptorManager(VulkanContext context, int slots) {
        this.context = context;
        this.slots = slots;
    }

    static int stageMask(EnumSet<org.soutalab.celtium.core.shader.ShaderStage> stages) {
        int mask = 0;
        for (org.soutalab.celtium.core.shader.ShaderStage stage : stages) {
            mask |= switch (stage) {
                case VERTEX -> VK_SHADER_STAGE_VERTEX_BIT;
                case FRAGMENT -> VK_SHADER_STAGE_FRAGMENT_BIT;
                case GEOMETRY -> VK_SHADER_STAGE_GEOMETRY_BIT;
                case COMPUTE -> VK_SHADER_STAGE_COMPUTE_BIT;
            };
        }
        return mask;
    }

    static int descriptorType(DescriptorBindingPlan.Kind kind) {
        return switch (kind) {
            case UNIFORM_BUFFER -> VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case SAMPLER -> VK_DESCRIPTOR_TYPE_SAMPLER;
            case SAMPLED_IMAGE -> VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
            case COMBINED_IMAGE_SAMPLER -> VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case STORAGE_IMAGE -> VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            case STORAGE_BUFFER -> VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        };
    }

    /** Creates the layout + pipeline layout for a program (idempotent per key). */
    void registerProgram(String programKey, List<LayoutBinding> bindings) {
        if (programs.containsKey(programKey)) {
            return;
        }
        VkDevice device = context.device();
        ProgramDescriptors program = new ProgramDescriptors(slots);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer layoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(bindings.size(), stack);
            for (int i = 0; i < bindings.size(); i++) {
                LayoutBinding binding = bindings.get(i);
                layoutBindings.get(i)
                        .binding(binding.binding())
                        .descriptorType(descriptorType(binding.kind()))
                        .descriptorCount(Math.max(binding.count(), 1))
                        .stageFlags(binding.stageMask());
            }
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(layoutBindings);
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(device, layoutInfo, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDescriptorSetLayout failed for " + programKey);
            }
            program.layout = handle.get(0);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(program.layout));
            if (vkCreatePipelineLayout(device, pipelineLayoutInfo, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreatePipelineLayout failed for " + programKey);
            }
            program.pipelineLayout = handle.get(0);
        }
        programs.put(programKey, program);
    }

    boolean registered(String programKey) {
        return programs.containsKey(programKey);
    }

    long pipelineLayout(String programKey) {
        return programs.get(programKey).pipelineLayout;
    }

    /** The descriptor set of a program for one frame slot, allocated on first use. */
    long set(String programKey, int slot) {
        ProgramDescriptors program = programs.get(programKey);
        int index = Math.floorMod(slot, slots);
        if (program.sets[index] == 0L) {
            ensurePool();
            VkDevice device = context.device();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                        .descriptorPool(pool)
                        .pSetLayouts(stack.longs(program.layout));
                LongBuffer handle = stack.mallocLong(1);
                if (vkAllocateDescriptorSets(device, allocInfo, handle) != VK_SUCCESS) {
                    throw new IllegalStateException("vkAllocateDescriptorSets failed for " + programKey);
                }
                program.sets[index] = handle.get(0);
            }
        }
        return program.sets[index];
    }

    private void ensurePool() {
        if (pool != 0L) {
            return;
        }
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] types = {
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
                    VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                    VK_DESCRIPTOR_TYPE_SAMPLER};
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(types.length, stack);
            for (int i = 0; i < types.length; i++) {
                sizes.get(i).type(types[i]).descriptorCount(4096);
            }
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(1024)
                    .pPoolSizes(sizes);
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateDescriptorPool(device, poolInfo, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDescriptorPool failed for pack descriptors");
            }
            pool = handle.get(0);
        }
    }

    /** Applies resolved writes to one set. Called during recording, before the set is bound. */
    void writeSet(long set, List<Write> writes) {
        if (writes.isEmpty()) {
            return;
        }
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer vkWrites = VkWriteDescriptorSet.calloc(writes.size(), stack);
            List<VkDescriptorBufferInfo.Buffer> bufferInfos = new ArrayList<>();
            List<VkDescriptorImageInfo.Buffer> imageInfos = new ArrayList<>();
            for (int i = 0; i < writes.size(); i++) {
                Write write = writes.get(i);
                VkWriteDescriptorSet vkWrite = vkWrites.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(set)
                        .dstBinding(write.binding())
                        .dstArrayElement(0)
                        .descriptorCount(1)
                        .descriptorType(descriptorType(write.kind()));
                if (write.kind() == DescriptorBindingPlan.Kind.UNIFORM_BUFFER
                        || write.kind() == DescriptorBindingPlan.Kind.STORAGE_BUFFER) {
                    VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack);
                    info.get(0).buffer(write.buffer()).offset(write.bufferOffset())
                            .range(write.bufferRange());
                    bufferInfos.add(info);
                    vkWrite.pBufferInfo(info);
                } else {
                    VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack);
                    info.get(0).imageView(write.imageView()).sampler(write.sampler())
                            .imageLayout(write.imageLayout());
                    imageInfos.add(info);
                    vkWrite.pImageInfo(info);
                }
            }
            vkUpdateDescriptorSets(device, vkWrites, null);
        }
    }

    @Override
    public void close() {
        VkDevice device = context.device();
        if (pool != 0L) {
            vkDestroyDescriptorPool(device, pool, null);
            pool = 0L;
        }
        for (ProgramDescriptors program : programs.values()) {
            if (program.pipelineLayout != 0L) {
                vkDestroyPipelineLayout(device, program.pipelineLayout, null);
            }
            if (program.layout != 0L) {
                vkDestroyDescriptorSetLayout(device, program.layout, null);
            }
        }
        programs.clear();
    }
}
