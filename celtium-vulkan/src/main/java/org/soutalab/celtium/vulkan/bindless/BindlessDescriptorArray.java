package org.soutalab.celtium.vulkan.bindless;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBindingFlagsCreateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO_EXT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

/**
 * The Vulkan side of the bindless texture array: one descriptor set whose single binding is a
 * {@value BindlessTextureTable#CAPACITY}-entry {@code COMBINED_IMAGE_SAMPLER} array, created with
 * {@code PARTIALLY_BOUND | UPDATE_AFTER_BIND | UPDATE_UNUSED_WHILE_PENDING} so slots can be
 * written while other slots are in use and unwritten slots may stay unbound (new-architecture
 * design section 2.2). Draws index the
 * array with the slot from {@link BindlessTextureTable}; a texture binding change never splits a
 * batch again.
 *
 * <p>Requires descriptor indexing ({@code VK_EXT_descriptor_indexing} or Vulkan 1.2) with
 * {@code shaderSampledImageArrayNonUniformIndexing} and the update-after-bind features for
 * sampled images; enabling the extension and checking the features is the context's job - this
 * class only builds the objects. Slots are written with
 * {@link #writeSlot(int, long, long)} as textures register; a slot that was never written must
 * not be sampled (partially-bound makes that legal only while the shader does not dereference
 * it, which the table's handle discipline guarantees for well-behaved draws).
 */
public final class BindlessDescriptorArray {

    private final VulkanContext context;
    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;

    public BindlessDescriptorArray(VulkanContext context) {
        this.context = context;
        createLayoutAndSet();
    }

    /**
     * The binding flags the array's single binding is created with.
     * <p>
     * {@code UPDATE_UNUSED_WHILE_PENDING} is the one S5-1 adds: without it, writing a slot no
     * in-flight command buffer references is still a validation error when the set is bound by a
     * pending submission, which is exactly the shape of a texture registering mid-frame (the
     * terrain path's {@code VulkanTextureArray} has carried the same trio from the start).
     * Package-private and static so the flag contract is testable without a device.
     */
    static int bindingFlags() {
        return VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT
                | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT
                | VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT_EXT;
    }

    private void createLayoutAndSet() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding =
                    VkDescriptorSetLayoutBinding.calloc(1, stack);
            binding.get(0).binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(BindlessTextureTable.CAPACITY)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

            IntBuffer bindingFlags = stack.ints(bindingFlags());
            VkDescriptorSetLayoutBindingFlagsCreateInfo flagsInfo =
                    VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO_EXT)
                            .pBindingFlags(bindingFlags);

            VkDescriptorSetLayoutCreateInfo layoutInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                            .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT_EXT)
                            .pBindings(binding)
                            .pNext(flagsInfo.address());
            LongBuffer layoutHandle = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null, layoutHandle),
                    "vkCreateDescriptorSetLayout(bindless textures)");
            this.descriptorSetLayout = layoutHandle.get(0);

            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(BindlessTextureTable.CAPACITY);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT_EXT)
                    .maxSets(1)
                    .pPoolSizes(poolSize);
            LongBuffer poolHandle = stack.mallocLong(1);
            check(vkCreateDescriptorPool(this.context.device(), poolInfo, null, poolHandle),
                    "vkCreateDescriptorPool(bindless textures)");
            this.descriptorPool = poolHandle.get(0);

            VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(this.descriptorPool)
                    .pSetLayouts(stack.longs(this.descriptorSetLayout));
            LongBuffer setHandle = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(this.context.device(), allocate, setHandle),
                    "vkAllocateDescriptorSets(bindless textures)");
            this.descriptorSet = setHandle.get(0);
        }
    }

    /** Points one array slot at a texture's image view and sampler. */
    public void writeSlot(int slot, long imageView, long sampler) {
        if (slot < 0 || slot >= BindlessTextureTable.CAPACITY) {
            throw new IllegalArgumentException("slot " + slot);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0).sampler(sampler)
                    .imageView(imageView)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0).sType$Default()
                    .dstSet(this.descriptorSet)
                    .dstBinding(0)
                    .dstArrayElement(slot)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(this.context.device(), write, null);
        }
    }

    public long descriptorSetLayout() {
        return this.descriptorSetLayout;
    }

    public long descriptorSet() {
        return this.descriptorSet;
    }

    public void delete() {
        if (this.descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(this.context.device(), this.descriptorPool, null);
            this.descriptorPool = VK_NULL_HANDLE;
        }
        if (this.descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(this.context.device(), this.descriptorSetLayout, null);
            this.descriptorSetLayout = VK_NULL_HANDLE;
        }
        this.descriptorSet = VK_NULL_HANDLE;
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with " + result);
        }
    }
}
