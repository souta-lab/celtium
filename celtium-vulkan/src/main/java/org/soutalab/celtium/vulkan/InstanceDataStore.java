package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.hal.FrameLinearRing;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

/**
 * The unified set 1 every converted window binds (Stage 5 slicing design section 3.2): the draw
 * records at binding 0 and the frame's lightmap sampler at binding 1, one descriptor set per frame
 * slot. Stage 5 stage 3 lands the store in its plan B form, adjudicated in the stage 3 record
 * before implementation: the records live in slices of the stage 2 {@link FrameLinearRing}, so the
 * store owns no buffer object of its own - the +1 BO of the plan A per-slot buffers is the price
 * the BO budget could not pay at B2 = Live 26 with a variable part of 22.
 *
 * <p>The store only ever allocates from the ring; it never resets, holds or releases ring slots,
 * because the ring's completion discipline belongs to the frame scheduler alone. A slice stays
 * valid because the ring never reissues a slot's bytes until that slot's frame has completed, and
 * a new selection of the same slot allocates a fresh slice after the previous one rather than
 * reusing it, so slices recorded by earlier windows of the same frame are never overwritten.
 *
 * <p>Binding 0 is {@code STORAGE_BUFFER_DYNAMIC}, written once per slot against the slot's whole
 * ring buffer; each bind supplies the current slice's offset as the dynamic offset. That is what
 * lets a second window in the same frame take its own slice without rewriting the descriptor under
 * the first window's recorded-but-unsubmitted commands. Slices are requested at the device's
 * {@code minStorageBufferOffsetAlignment}, which is what makes their offsets legal dynamic
 * offsets.
 *
 * <p>Record semantics are {@link ImmediateDrawRecords#record(ByteBuffer, int)}'s exactly: 256-byte
 * stride, the family's push block copied verbatim with the rest of the record zeroed, and the
 * bindless array index as float bits at offset 216 when one is given. A selection whose slice
 * cannot be allocated refuses records for the whole window (the caller keeps the per-group path);
 * growth inside a selection allocates a larger slice in the same slot and copies the records
 * written so far - safe because binding happens at the window's flush, after all its records, so
 * the final offset is the one that gets bound - and a failed growth refuses only the record that
 * did not fit. Remembered demand grows with use and never shrinks; a refused selection does not
 * shrink it either, because the ring frees the whole slot at its next completion.
 */
final class InstanceDataStore {

    /** Bytes per record; one stride for both immediate families, as in {@link ImmediateDrawRecords}. */
    static final int RECORD_BYTES = ImmediateDrawRecords.RECORD_BYTES;

    /** Records a fresh slot asks room for; the slice doubles on demand from here. */
    private static final int INITIAL_RECORDS = 256;

    /** The smallest push block the two families produce; shorter blocks are refused. */
    private static final int MIN_BLOCK_BYTES = 208;

    /** Where the record shaders read the bindless array index, as float bits. */
    private static final int ARRAY_INDEX_OFFSET = 216;

    /**
     * The ring surface the store carves record slices from; the seam that lets the store be
     * tested without a device. The store only allocates through it.
     */
    interface RingSource {
        int slots();

        long slotCapacity(int slot);

        /** {@return the slot-relative offset of a fresh slice, or -1 when it does not fit} */
        long alloc(int slot, long size, long alignment);

        long mappedAddress(int slot, long offset);

        long buffer(int slot);
    }

    /** The production source: the context's stage 2 frame-linear ring. */
    private static final class FrameLinearRingSource implements RingSource {
        private final FrameLinearRing ring;

        FrameLinearRingSource(FrameLinearRing ring) {
            this.ring = ring;
        }

        @Override
        public int slots() {
            return this.ring.slots();
        }

        @Override
        public long slotCapacity(int slot) {
            return this.ring.slotCapacity(slot);
        }

        @Override
        public long alloc(int slot, long size, long alignment) {
            return this.ring.alloc(slot, size, alignment);
        }

        @Override
        public long mappedAddress(int slot, long offset) {
            return this.ring.mappedAddress(slot, offset);
        }

        @Override
        public long buffer(int slot) {
            return this.ring.buffer(slot).handle();
        }
    }

    private final RingSource ring;
    private final long alignment;
    private final int slots;
    /** The current selection's slice offset per slot; -1 when the selection has no slice. */
    private final long[] offsets;
    /** Records that fit the current slice, per slot. */
    private final int[] capacities;
    private final int[] records;
    /** Bytes the next selection of a slot asks for; grows with observed demand, never shrinks. */
    private final long[] demand;
    /** A failed growth is not retried inside one selection; the next selection asks afresh. */
    private final boolean[] growFailed;
    /** The lightmap (view, sampler) last written into each slot's set; 0 = unwritten. */
    private final long[] lightmapViews;
    private final long[] lightmapSamplers;
    private int currentSlot;

    private VulkanContext context;
    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private final long[] descriptorSets;

    InstanceDataStore(RingSource ring, long alignment) {
        this.ring = ring;
        this.alignment = Math.max(1L, alignment);
        this.slots = ring.slots();
        this.offsets = new long[this.slots];
        this.capacities = new int[this.slots];
        this.records = new int[this.slots];
        this.demand = new long[this.slots];
        this.growFailed = new boolean[this.slots];
        this.lightmapViews = new long[this.slots];
        this.lightmapSamplers = new long[this.slots];
        this.descriptorSets = new long[this.slots];
        for (int slot = 0; slot < this.slots; slot++) {
            this.offsets[slot] = -1L;
            this.demand[slot] = (long) INITIAL_RECORDS * RECORD_BYTES;
        }
    }

    /**
     * Builds the production store over the context's frame-linear ring, with one descriptor set
     * per ring slot.
     *
     * @return the store, or {@code null} when its descriptor objects could not be created - the
     *         caller then keeps the per-group path, as with {@link ImmediateDrawRecords#create()}
     */
    static InstanceDataStore create(VulkanContext context) {
        FrameLinearRing ring = context.celtium$frameLinearRing();
        InstanceDataStore store = new InstanceDataStore(new FrameLinearRingSource(ring),
                storageBufferOffsetAlignment(context));
        store.context = context;
        if (!store.createDescriptors()) {
            store.delete();
            return null;
        }
        return store;
    }

    private static long storageBufferOffsetAlignment(VulkanContext context) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            return Math.max(1L, properties.limits().minStorageBufferOffsetAlignment());
        }
    }

    private boolean createDescriptors() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
            bindings.get(0)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            bindings.get(1)
                    .binding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings);
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(this.context.device(), layoutInfo, null, handle) != VK_SUCCESS) {
                return false;
            }
            this.descriptorSetLayout = handle.get(0);
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(2, stack);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC).descriptorCount(this.slots);
            sizes.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(this.slots);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(this.slots)
                    .pPoolSizes(sizes);
            if (vkCreateDescriptorPool(this.context.device(), poolInfo, null, handle) != VK_SUCCESS) {
                return false;
            }
            this.descriptorPool = handle.get(0);
            long[] layouts = new long[this.slots];
            Arrays.fill(layouts, this.descriptorSetLayout);
            VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(this.descriptorPool)
                    .pSetLayouts(stack.longs(layouts));
            LongBuffer sets = stack.mallocLong(this.slots);
            if (vkAllocateDescriptorSets(this.context.device(), allocate, sets) != VK_SUCCESS) {
                return false;
            }
            for (int slot = 0; slot < this.slots; slot++) {
                this.descriptorSets[slot] = sets.get(slot);
                this.writeRecordsDescriptor(stack, slot);
            }
            return true;
        }
    }

    /**
     * Binding 0 is written once per slot: the slot's whole ring buffer at offset 0. The range
     * WHOLE_SIZE resolves against the dynamic offset at bind time, so this one write serves every
     * slice the slot ever hands out.
     */
    private void writeRecordsDescriptor(MemoryStack stack, int slot) {
        VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack);
        info.get(0).buffer(this.ring.buffer(slot)).offset(0L).range(VK_WHOLE_SIZE);
        VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
        write.get(0).sType$Default()
                .dstSet(this.descriptorSets[slot])
                .dstBinding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC)
                .descriptorCount(1)
                .pBufferInfo(info);
        vkUpdateDescriptorSets(this.context.device(), write, null);
    }

    /**
     * Selects the frame slot the following records belong to and carves this window's slice: the
     * slot's record cursor rewinds and a fresh slice of the remembered demand is allocated. A slot
     * whose slice does not fit refuses records until its next selection.
     */
    void selectSlot(int slot) {
        if (slot < 0 || slot >= this.slots) {
            throw new IllegalArgumentException("slot " + slot);
        }
        this.currentSlot = slot;
        this.records[slot] = 0;
        this.growFailed[slot] = false;
        long offset = this.ring.alloc(slot, this.demand[slot], this.alignment);
        if (offset < 0L) {
            this.offsets[slot] = -1L;
            this.capacities[slot] = 0;
        } else {
            this.offsets[slot] = offset;
            this.capacities[slot] = (int) (this.demand[slot] / RECORD_BYTES);
        }
    }

    int currentSlot() {
        return this.currentSlot;
    }

    /**
     * Points the current slot's set 1 binding 1 at the frame's lightmap, keyed per slot on the
     * last written (view, sampler). A slot's set is rewritten only on a selection after its
     * previous frame completed - the same completion gate that lets the slot be selected again -
     * so the write never lands under a submitted command buffer.
     */
    void bindLightmap(long imageView, long sampler, int imageLayout) {
        int slot = this.currentSlot;
        if (imageView == VK_NULL_HANDLE) {
            return;
        }
        if (this.lightmapViews[slot] == imageView && this.lightmapSamplers[slot] == sampler) {
            return;
        }
        this.lightmapViews[slot] = imageView;
        this.lightmapSamplers[slot] = sampler;
        if (this.context == null || this.descriptorSets[slot] == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(imageLayout)
                    .imageView(imageView)
                    .sampler(sampler);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0).sType$Default()
                    .dstSet(this.descriptorSets[slot])
                    .dstBinding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(this.context.device(), write, null);
        }
    }

    /** {@return the lightmap view last bound into the slot's set (0 when none), for tests} */
    long boundLightmapView(int slot) {
        return this.lightmapViews[slot];
    }

    /**
     * Appends one record to the selected slot, with {@link ImmediateDrawRecords}' semantics.
     *
     * @param pushBlock a block of at least 208 bytes; as much of it as the record stride holds
     *                  is copied and the rest of the record is zeroed
     * @param arrayIndex the bindless array index stored as float bits at offset 216, or -1 for none
     * @return the record's index in the slot, or -1 when the block is too short, the selection has
     *         no slice, or the slice cannot grow to hold the record
     */
    int record(ByteBuffer pushBlock, int arrayIndex) {
        if (pushBlock == null || pushBlock.capacity() < MIN_BLOCK_BYTES) {
            return -1;
        }
        int slot = this.currentSlot;
        if (this.offsets[slot] < 0L) {
            return -1;
        }
        if (this.records[slot] + 1 > this.capacities[slot] && !this.grow(slot)) {
            return -1;
        }
        long target = this.ring.mappedAddress(slot, this.offsets[slot])
                + (long) this.records[slot] * RECORD_BYTES;
        int copy = Math.min(pushBlock.capacity(), RECORD_BYTES);
        MemoryUtil.memCopy(MemoryUtil.memAddress(pushBlock), target, copy);
        MemoryUtil.memSet(target + copy, 0, RECORD_BYTES - copy);
        if (arrayIndex >= 0) {
            MemoryUtil.memPutFloat(target + ARRAY_INDEX_OFFSET, Float.intBitsToFloat(arrayIndex));
        }
        return this.records[slot]++;
    }

    /**
     * Doubles the current slice inside the same slot and copies the records written so far. The
     * abandoned slice is not freed by hand: it is ring bytes the slot's next reset reclaims, and
     * nothing has bound the old offset yet - binding happens at the window's flush, after all its
     * records - so this copy is the old bytes' only reader.
     */
    private boolean grow(int slot) {
        if (this.growFailed[slot]) {
            return false;
        }
        long wanted = Math.max(this.demand[slot] * 2L, (this.records[slot] + 1L) * RECORD_BYTES);
        long offset = this.ring.alloc(slot, wanted, this.alignment);
        if (offset < 0L) {
            this.growFailed[slot] = true;
            return false;
        }
        long from = this.ring.mappedAddress(slot, this.offsets[slot]);
        long to = this.ring.mappedAddress(slot, offset);
        MemoryUtil.memCopy(from, to, (long) this.records[slot] * RECORD_BYTES);
        this.offsets[slot] = offset;
        this.demand[slot] = wanted;
        this.capacities[slot] = (int) (wanted / RECORD_BYTES);
        return true;
    }

    /** {@return how many records the current slot has written since its selection} */
    int count() {
        return this.records[this.currentSlot];
    }

    /** {@return how many records the given slot has written since its last selection} */
    int count(int slot) {
        return this.records[slot];
    }

    /** {@return the set layout, so a record-reading pipeline can declare set 1} */
    long descriptorSetLayout() {
        return this.descriptorSetLayout;
    }

    /** {@return the current slot's set, to bind at set 1 before a run} */
    long descriptorSet() {
        return this.descriptorSets[this.currentSlot];
    }

    /** {@return the current slot's set} */
    long descriptorSet(int slot) {
        return this.descriptorSets[slot];
    }

    /**
     * {@return the dynamic offset a bind of the current slot's set must supply for binding 0: the
     *         current selection's slice offset}
     */
    long descriptorDynamicOffset() {
        return this.offsets[this.currentSlot];
    }

    /** Releases the descriptor objects. The ring and its buffers are not the store's to release. */
    void delete() {
        if (this.context != null && this.descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(this.context.device(), this.descriptorPool, null);
            this.descriptorPool = VK_NULL_HANDLE;
            Arrays.fill(this.descriptorSets, VK_NULL_HANDLE);
        }
        if (this.context != null && this.descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(this.context.device(), this.descriptorSetLayout, null);
            this.descriptorSetLayout = VK_NULL_HANDLE;
        }
    }
}
