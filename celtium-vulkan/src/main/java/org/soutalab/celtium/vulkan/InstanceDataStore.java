package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

/**
 * The FIF-aware successor to {@link ImmediateDrawRecords} (S5-1): the same 256-byte draw
 * records in the same layout, but held in {@value #SLOTS} frame slots, one storage buffer and
 * one descriptor set per slot.
 *
 * <p>The single-buffer original is rewritten from offset 0 by every window's {@code reset()},
 * which is only safe while the previous frame's submits have completed; with two frames in
 * flight the next frame's records land on bytes the GPU may still be reading. Here the frame
 * loop selects the slot for the frame it is building ({@link #selectSlot(int)}) and writes go
 * only there, so a slot is never rewritten until its own turn comes round again - by which
 * point the frame that used it has completed under the FIF=2 discipline. Record semantics are
 * {@link ImmediateDrawRecords#record(ByteBuffer, int)}'s exactly, including the bindless array
 * index stored as float bits at offset 216, so the record-reading shaders and the indirect run
 * do not change when the backend switches stores.
 *
 * <p>In the S5-1 set layout this store's set is set 1; set 0 is the bindless texture array
 * ({@code BindlessTextureBinding}), bound once and never rewritten per texture.
 *
 * <p>The backend switch is integration work: this class is complete and tested on its own, and
 * {@link ImmediateDrawRecords} remains the live store until then.
 */
final class InstanceDataStore {

    /** Bytes per record; one stride for both immediate families, as in {@link ImmediateDrawRecords}. */
    static final int RECORD_BYTES = ImmediateDrawRecords.RECORD_BYTES;

    /** Frame slots, matching the renderer's frames-in-flight count. */
    static final int SLOTS = 2;

    private static final int INITIAL_RECORDS = 256;

    /** One frame slot's backing store; the seam that lets the store be tested without a device. */
    interface SlotBuffer {
        long mappedAddress();

        long bufferHandle();

        /** Grows the store, preserving existing contents; the buffer handle may change. */
        void ensureCapacity(long requiredBytes);

        void delete();
    }

    interface SlotBufferFactory {
        SlotBuffer create(long capacityBytes);
    }

    private static final class HostSlotBuffer implements SlotBuffer {
        private final VulkanHostBuffer host;

        HostSlotBuffer(VulkanContext context, long capacityBytes) {
            this.host = new VulkanHostBuffer(context, capacityBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }

        @Override
        public long mappedAddress() {
            return this.host.mappedAddress();
        }

        @Override
        public long bufferHandle() {
            return this.host.buffer();
        }

        @Override
        public void ensureCapacity(long requiredBytes) {
            this.host.ensureCapacity(requiredBytes);
        }

        @Override
        public void delete() {
            this.host.delete();
        }
    }

    private final SlotBuffer[] buffers = new SlotBuffer[SLOTS];
    private final int[] capacities = new int[SLOTS];
    private final int[] records = new int[SLOTS];
    private int currentSlot;

    private VulkanContext context;
    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private final long[] descriptorSets = new long[SLOTS];

    InstanceDataStore(SlotBufferFactory factory) {
        for (int slot = 0; slot < SLOTS; slot++) {
            this.buffers[slot] = factory.create(INITIAL_RECORDS * (long) RECORD_BYTES);
            this.capacities[slot] = INITIAL_RECORDS;
        }
    }

    /**
     * Builds the production store: host buffers plus one descriptor set per slot over a shared
     * layout.
     *
     * @return the store, or {@code null} when the descriptor objects could not be created - the
     *         caller then keeps the per-group path, as with {@link ImmediateDrawRecords#create()}
     */
    static InstanceDataStore create(VulkanContext context) {
        InstanceDataStore store = new InstanceDataStore(
                capacityBytes -> new HostSlotBuffer(context, capacityBytes));
        store.context = context;
        if (!store.createDescriptors()) {
            store.delete();
            return null;
        }
        return store;
    }

    private boolean createDescriptors() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0).binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default().pBindings(bindings);
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null, handle) != VK_SUCCESS) {
                return false;
            }
            this.descriptorSetLayout = handle.get(0);
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(SLOTS);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default().maxSets(SLOTS).pPoolSizes(sizes);
            if (vkCreateDescriptorPool(this.context.device(), poolInfo, null, handle) != VK_SUCCESS) {
                return false;
            }
            this.descriptorPool = handle.get(0);
            LongBuffer layouts = stack.mallocLong(SLOTS);
            for (int slot = 0; slot < SLOTS; slot++) {
                layouts.put(slot, this.descriptorSetLayout);
            }
            VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default().descriptorPool(this.descriptorPool).pSetLayouts(layouts);
            LongBuffer sets = stack.mallocLong(SLOTS);
            if (vkAllocateDescriptorSets(this.context.device(), allocate, sets) != VK_SUCCESS) {
                return false;
            }
            for (int slot = 0; slot < SLOTS; slot++) {
                this.descriptorSets[slot] = sets.get(slot);
                writeDescriptor(stack, slot);
            }
            return true;
        }
    }

    /** Points one slot's set at its buffer as it is now; called again after that slot grows. */
    private void writeDescriptor(MemoryStack stack, int slot) {
        VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack);
        info.get(0).buffer(this.buffers[slot].bufferHandle()).offset(0L).range(VK_WHOLE_SIZE);
        VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
        write.get(0).sType$Default()
                .dstSet(this.descriptorSets[slot])
                .dstBinding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(info);
        vkUpdateDescriptorSets(this.context.device(), write, null);
    }

    /**
     * Selects the frame slot this frame writes into and rewinds its cursor. Selecting a slot is
     * the frame boundary: the slot's previous contents belong to a frame two submissions ago,
     * which the FIF=2 discipline guarantees complete.
     */
    void selectSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            throw new IllegalArgumentException("slot " + slot);
        }
        this.currentSlot = slot;
        this.records[slot] = 0;
    }

    int currentSlot() {
        return this.currentSlot;
    }

    /** {@return how many records the current slot has written this frame}. */
    int count() {
        return this.records[this.currentSlot];
    }

    /** {@return how many records the given slot holds (its last selected frame's count)}. */
    int count(int slot) {
        return this.records[slot];
    }

    /**
     * Appends one record to the current slot.
     *
     * @param pushBlock a block of at least 208 bytes; as much of it as the record stride holds
     *                  is copied and the rest of the record is zeroed
     * @param arrayIndex the bindless texture index stored at offset 216, or -1 for none
     * @return the record's index in this slot, or -1 when the block is too short
     */
    int record(ByteBuffer pushBlock, int arrayIndex) {
        if (pushBlock == null || pushBlock.capacity() < 208) {
            return -1;
        }
        int slot = this.currentSlot;
        if (this.records[slot] + 1 > this.capacities[slot]) {
            int grown = Math.max(this.capacities[slot] * 2, this.records[slot] + 1);
            this.buffers[slot].ensureCapacity(grown * (long) RECORD_BYTES);
            this.capacities[slot] = grown;
            if (this.descriptorSets[slot] != VK_NULL_HANDLE) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    writeDescriptor(stack, slot);
                }
            }
        }
        long target = this.buffers[slot].mappedAddress() + (long) this.records[slot] * RECORD_BYTES;
        int copy = Math.min(pushBlock.capacity(), RECORD_BYTES);
        MemoryUtil.memCopy(MemoryUtil.memAddress(pushBlock), target, copy);
        MemoryUtil.memSet(target + copy, 0, RECORD_BYTES - copy);
        if (arrayIndex >= 0) {
            MemoryUtil.memPutFloat(target + 216, Float.intBitsToFloat(arrayIndex));
        }
        return this.records[slot]++;
    }

    /** {@return the set layout, so a record-reading pipeline can declare set 1}. */
    long descriptorSetLayout() {
        return this.descriptorSetLayout;
    }

    /** {@return the current slot's set, to bind at set 1 before a run}. */
    long descriptorSet() {
        return this.descriptorSets[this.currentSlot];
    }

    void delete() {
        if (this.descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(this.context.device(), this.descriptorPool, null);
            this.descriptorPool = VK_NULL_HANDLE;
            for (int slot = 0; slot < SLOTS; slot++) {
                this.descriptorSets[slot] = VK_NULL_HANDLE;
            }
        }
        if (this.descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(this.context.device(), this.descriptorSetLayout, null);
            this.descriptorSetLayout = VK_NULL_HANDLE;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.buffers[slot] != null) {
                this.buffers[slot].delete();
                this.buffers[slot] = null;
            }
        }
    }
}
