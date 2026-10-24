package org.soutalab.celtium.vulkan;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDescriptorSetVariableDescriptorCountAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT_EXT;
import static org.lwjgl.vulkan.VK10.*;

/**
 * One descriptor set that names every texture a window draws, indexed from the shader.
 * <p>
 * The reason this exists is measured, not assumed. The frame records about 159 immediate groups and they come from only eleven pipelines
 * (architecture section 11.6), but no two neighbouring groups share pipeline and descriptor, so a run never exceeds one group and the window
 * cannot collapse. This set is the half that removes the descriptor from the comparison: every group binds the same set and picks its texture
 * by index, so once the per-draw matrices also leave the push block the window can be sorted by pipeline and recorded as about eleven commands.
 * <p>
 * The device has to allow it: non-uniform sampled-array indexing, a runtime array, update-after-bind and partially-bound, all reported by
 * {@link VulkanContext#textureArraySupported()}. Descriptors are written as textures are registered and may be rewritten while recorded
 * buffers still name the set, which is exactly what UPDATE_AFTER_BIND is for; PARTIALLY_BOUND is what lets an untouched slot stay unwritten
 * instead of needing a placeholder image per slot.
 * <p>
 * Nothing draws through this yet. It is created, filled from the batches the renderer accepts, and reported, so the mechanism is exercised and
 * its cost visible before a family is switched over; a device without the features keeps the per-group sets and this stays absent.
 */
final class VulkanTextureArray {

    /** How many textures one window may draw through the array. Linux drivers report millions; this is deliberately modest. */
    static final int CAPACITY = 4096;

    private final VulkanContext context;
    private long layout = VK_NULL_HANDLE;
    private long pool = VK_NULL_HANDLE;
    private long set = VK_NULL_HANDLE;
    private final Map<Long, Integer> indexByKey = new HashMap<Long, Integer>();
    private int nextIndex;
    private long descriptorsWritten;
    private long writesCoalesced;
    private boolean fullReported;

    VulkanTextureArray(VulkanContext context) {
        this.context = context;
    }

    /** {@return whether the set was created; false when the device lacks the features or creation failed}. */
    boolean create(MemoryStack stack) {
        if (!this.context.textureArraySupported()) {
            return false;
        }
        try {
            VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack);
            binding.get(0).binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(CAPACITY)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);

            org.lwjgl.vulkan.VkDescriptorSetLayoutBindingFlagsCreateInfo flags =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack).sType$Default();
            flags.pBindingFlags(stack.ints(VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT
                    | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT
                    | org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT_EXT));

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(flags.address())
                    .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT_EXT)
                    .pBindings(binding);
            java.nio.LongBuffer handle = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null, handle) != VK_SUCCESS) {
                return false;
            }
            this.layout = handle.get(0);

            VkDescriptorPoolSize.Buffer size = VkDescriptorPoolSize.calloc(1, stack);
            size.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(CAPACITY);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT_EXT)
                    .maxSets(1)
                    .pPoolSizes(size);
            if (vkCreateDescriptorPool(this.context.device(), poolInfo, null, handle) != VK_SUCCESS) {
                vkDestroyDescriptorSetLayout(this.context.device(), this.layout, null);
                this.layout = VK_NULL_HANDLE;
                return false;
            }
            this.pool = handle.get(0);

            VkDescriptorSetVariableDescriptorCountAllocateInfo variable =
                    VkDescriptorSetVariableDescriptorCountAllocateInfo.calloc(stack).sType$Default()
                            .pDescriptorCounts(stack.ints(CAPACITY));
            VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(this.pool)
                    .pNext(variable.address())
                    .pSetLayouts(stack.longs(this.layout));
            if (vkAllocateDescriptorSets(this.context.device(), allocate, handle) != VK_SUCCESS) {
                vkDestroyDescriptorPool(this.context.device(), this.pool, null);
                vkDestroyDescriptorSetLayout(this.context.device(), this.layout, null);
                this.pool = VK_NULL_HANDLE;
                this.layout = VK_NULL_HANDLE;
                return false;
            }
            this.set = handle.get(0);
            return true;
        } catch (RuntimeException | LinkageError error) {
            System.out.println("[Celtium] CELTIUM_TEXTURE_ARRAY create failed: " + error);
            destroy();
            return false;
        }
    }

    /** {@return the index this wrapper occupies, registering it on first use; -1 when the array is full or absent}. */
    int indexFor(MemoryStack stack, VulkanGlTexture texture, int expectedKeySlots) {
        if (this.set == VK_NULL_HANDLE || texture == null || !texture.hasFullContent()) {
            return -1;
        }
        Integer known = this.indexByKey.get(Long.valueOf(texture.key()));
        if (known != null) {
            return known.intValue();
        }
        if (this.nextIndex >= CAPACITY) {
            if (!this.fullReported) {
                this.fullReported = true;
                System.out.println("[Celtium] CELTIUM_TEXTURE_ARRAY full at " + CAPACITY
                        + " entries; further textures keep their own descriptor sets");
            }
            return -1;
        }
        int index = this.nextIndex++;
        this.indexByKey.put(Long.valueOf(texture.key()), Integer.valueOf(index));
        write(stack, index, texture);
        return index;
    }

    private void write(MemoryStack stack, int index, VulkanGlTexture texture) {
        VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack);
        image.get(0).imageLayout(texture.samplingLayout())
                .imageView(texture.view())
                .sampler(texture.sampler());
        VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
        write.get(0).sType$Default()
                .dstSet(this.set)
                .dstBinding(0)
                .dstArrayElement(index)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(image);
        vkUpdateDescriptorSets(this.context.device(), write, null);
        this.descriptorsWritten++;
    }

    /** {@return the set every converted draw binds, or {@code VK_NULL_HANDLE} when this mechanism is absent}. */
    long set() {
        return this.set;
    }

    long layout() {
        return this.layout;
    }

    int entries() {
        return this.nextIndex;
    }

    long descriptorsWritten() {
        return this.descriptorsWritten;
    }

    String report() {
        return "textureArray set=0x" + Long.toHexString(this.set) + " entries=" + this.nextIndex + "/" + CAPACITY
                + " descriptorWrites=" + this.descriptorsWritten + " coalesced=" + this.writesCoalesced;
    }

    void destroy() {
        if (this.pool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(this.context.device(), this.pool, null);
            this.pool = VK_NULL_HANDLE;
        }
        if (this.layout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(this.context.device(), this.layout, null);
            this.layout = VK_NULL_HANDLE;
        }
        this.set = VK_NULL_HANDLE;
        this.indexByKey.clear();
        this.nextIndex = 0;
    }
}
