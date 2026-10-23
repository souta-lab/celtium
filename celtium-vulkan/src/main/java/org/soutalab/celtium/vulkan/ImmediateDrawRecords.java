package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;

/**
 * The per-frame array of draw records an indirect immediate run reads, one 208-byte block per draw.
 * <p>
 * A record is byte-for-byte the push block the immediate pipelines take - matrix at 0, the texture affines at 64, the
 * alpha reference and colour at 112, the fog block at 128 - so the record-reading shader
 * ({@link SpirvShaders#IMMEDIATE_TEX_COLOR_RECORDS_VERTEX}) can be swapped in without changing what the writer produces.
 * The point of moving it out of push constants is that a push block cannot be shared by two draws: with the block in a
 * buffer, a run of groups that agree on material becomes one {@code vkCmdDrawIndexedIndirect} whose commands carry their
 * record index as {@code firstInstance}.
 * <p>
 * The descriptor set is set 1: set 0 stays the per-texture (texture, light map) pair the immediate path already creates
 * per texture, and adding a binding there would change the layout of every one of those sets.
 */
final class ImmediateDrawRecords {

    /** Bytes per record: the push block's length for the lit and textured families. */
    static final int RECORD_BYTES = 208;

    private static final int INITIAL_RECORDS = 256;

    private final VulkanContext context;
    private VulkanHostBuffer buffer;
    private long descriptorSetLayout = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
    private long descriptorPool = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
    private long descriptorSet = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
    private int capacity = INITIAL_RECORDS;
    private int records;

    ImmediateDrawRecords(VulkanContext context) {
        this.context = context;
    }

    /** {@return whether the buffer, the set layout, the pool and the set all exist}. */
    boolean create() {
        this.buffer = new VulkanHostBuffer(this.context, INITIAL_RECORDS * (long) RECORD_BYTES,
                org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer bindings =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0).binding(0)
                    .descriptorType(org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT);
            org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutInfo =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(bindings);
            java.nio.LongBuffer handle = stack.mallocLong(1);
            if (org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null,
                    handle) != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return false;
            }
            this.descriptorSetLayout = handle.get(0);
            org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer sizes =
                    org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(1, stack);
            sizes.get(0).type(org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1);
            org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                            .maxSets(1).pPoolSizes(sizes);
            if (org.lwjgl.vulkan.VK10.vkCreateDescriptorPool(this.context.device(), poolInfo, null,
                    handle) != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return false;
            }
            this.descriptorPool = handle.get(0);
            org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                    org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                            .descriptorPool(this.descriptorPool).pSetLayouts(stack.longs(this.descriptorSetLayout));
            if (org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets(this.context.device(), allocate, handle)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return false;
            }
            this.descriptorSet = handle.get(0);
            writeDescriptor(stack);
            return true;
        }
    }

    /** Points the set at the buffer as it is now; called again after a growth. */
    private void writeDescriptor(org.lwjgl.system.MemoryStack stack) {
        org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer info =
                org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(1, stack);
        info.get(0).buffer(this.buffer.buffer()).offset(0L).range(org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE);
        org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer write =
                org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(1, stack);
        write.get(0).sType$Default()
                .dstSet(this.descriptorSet)
                .dstBinding(0)
                .descriptorType(org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(info);
        org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets(this.context.device(), write, null);
    }

    /** {@return the set layout, so a record-reading pipeline can declare set 1}. */
    long descriptorSetLayout() {
        return this.descriptorSetLayout;
    }

    /** {@return the set holding the records, to bind at set 1 before a run}. */
    long descriptorSet() {
        return this.descriptorSet;
    }

    /** {@return how many records this window has written}. */
    int count() {
        return this.records;
    }

    /** Starts a window: the next record goes to slot 0. The buffer keeps its capacity. */
    void reset() {
        this.records = 0;
    }

    /**
     * Appends one record.
     *
     * @param pushBlock a block of at least {@link #RECORD_BYTES} bytes, positioned anywhere; the first 208 bytes are copied
     * @return the record's index, which is the indirect command's firstInstance, or -1 when the block is too short
     */
    int record(ByteBuffer pushBlock) {
        if (pushBlock == null || pushBlock.capacity() < RECORD_BYTES) {
            return -1;
        }
        if (this.records + 1 > this.capacity) {
            int grown = Math.max(this.capacity * 2, this.records + 1);
            this.buffer.ensureCapacity(grown * (long) RECORD_BYTES);
            this.capacity = grown;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                writeDescriptor(stack);
            }
        }
        long target = this.buffer.mappedAddress() + (long) this.records * RECORD_BYTES;
        org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(pushBlock), target, RECORD_BYTES);
        return this.records++;
    }

    void delete() {
        if (this.descriptorPool != org.lwjgl.vulkan.VK10.VK_NULL_HANDLE) {
            org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool(this.context.device(), this.descriptorPool, null);
            this.descriptorPool = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
            this.descriptorSet = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
        }
        if (this.descriptorSetLayout != org.lwjgl.vulkan.VK10.VK_NULL_HANDLE) {
            org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout(this.context.device(), this.descriptorSetLayout, null);
            this.descriptorSetLayout = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
        }
        if (this.buffer != null) {
            this.buffer.delete();
            this.buffer = null;
        }
    }
}
