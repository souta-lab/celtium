package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.hal.FrameLinearRing;
import org.soutalab.celtium.vulkan.hal.RecordsRing;

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
 * slot. Stage 5 stage 3 landed the store in its plan B form, adjudicated in the stage 3 record
 * before implementation: the records lived in slices of the stage 2 {@link FrameLinearRing}, so
 * the store owned no buffer object of its own - the +1 BO of the plan A per-slot buffers was the
 * price the BO budget could not pay at B2 = Live 26 with a variable part of 22.
 *
 * <p>The ring records separation (F-4) retires that sharing. The records now live in a dedicated
 * {@link RecordsRing} - one buffer object for all slots, one fixed region per slot - because the
 * shared ring made the records the fatal victim of geometry-side exhaustion: a window's slice is
 * taken in one allocation, so the window the ring could not serve lost every record at once (the
 * all-or-nothing freeze the bindless verification leg measured), while geometry-side exhaustion
 * merely promotes and keeps drawing. The budget position that forced plan B no longer holds
 * (Live 22 against the 24 gate after the BO convergence, D-62), so the separation pays exactly
 * the one buffer object plan A would have spent several of. Exhaustion of the dedicated pool
 * degrades the geometry side's way: the store refuses, the caller keeps the direct path, and the
 * drawing survives. Setting {@code celtium.vulkan.recordsDedicatedPool=false} restores the
 * shared-ring source, which remains a supported configuration and the separation's A/B arm.
 * The per-group path's own record is served the same way: its need is one group at a time, so
 * when the window's selection refuses it, {@link #reselectFallback} carves it a small selection
 * of its own - the refusal of a window-sized ask, or of a growth doubling one, is not a refusal
 * of a handful of records.
 *
 * <p>The store only ever allocates from its source; it never resets, holds or releases the
 * shared ring's slots, because that ring's completion discipline belongs to the frame scheduler
 * alone. A slice stays valid because the ring never reissues a slot's bytes until that slot's
 * frame has completed, and a new selection of the same slot allocates a fresh slice after the
 * previous one rather than reusing it, so slices recorded by earlier windows of the same frame
 * are never overwritten. The dedicated pool follows the same rule at the store's own seat: its
 * slot region is rewound only at {@link #beginFrame}, where the slot's previous submission is
 * known complete.
 *
 * <p>Binding 0 is {@code STORAGE_BUFFER_DYNAMIC}, written once per slot against the slot's whole
 * ring buffer; each bind supplies the current slice's offset as the dynamic offset. That is what
 * lets a second window in the same frame take its own slice without rewriting the descriptor under
 * the first window's recorded-but-unsubmitted commands. Slices are requested at the device's
 * {@code minStorageBufferOffsetAlignment}, which is what makes their offsets legal dynamic
 * offsets.
 *
 * <p>Record semantics are the legacy standalone store's exactly (its class was deleted in
 * Stage 5 stage 6 and this store is the record layout's only home now): 256-byte
 * stride, the family's push block copied verbatim with the rest of the record zeroed, and the
 * bindless array index as float bits at offset 216 when one is given. A selection whose slice
 * cannot be allocated refuses records for the whole window (the caller keeps the per-group path);
 * growth inside a selection allocates a larger slice in the same slot and copies the records
 * written so far - safe because binding happens at the window's flush, after all its records, so
 * the final offset is the one that gets bound - and a failed growth refuses only the record that
 * did not fit. Remembered demand grows with use inside a frame and decays at the frame boundary
 * to the largest window the finished frame actually served (never below the initial ask): the
 * all-time-high ratchet the bindless leg's diagnosis named is what made one busy phase tax every
 * later window of the slot, and the boundary is the one seat that knows what the frame needed.
 * A refused selection does not shrink demand either, because the slot's bytes are freed wholesale
 * at its next completion.
 */
final class InstanceDataStore {

    /**
     * Bytes per record; one stride for both immediate families. The literal was the legacy
     * standalone store's constant until Stage 5 stage 6 moved it here with the deletion.
     */
    static final int RECORD_BYTES = 256;

    /** Records a fresh slot asks room for; the slice doubles on demand from here. */
    private static final int INITIAL_RECORDS = 256;

    /** Records a fallback selection asks room for: a per-group path's handful, not a window's. */
    private static final int FALLBACK_RECORDS = 16;

    /** The smallest push block the two families produce; shorter blocks are refused. */
    private static final int MIN_BLOCK_BYTES = 208;

    /** Where the record shaders read the bindless array index, as float bits. */
    private static final int ARRAY_INDEX_OFFSET = 216;

    /**
     * The slice source the store carves record slices from; the seam that lets the store be
     * tested without a device. The store only allocates through it.
     */
    interface RingSource {
        int slots();

        long slotCapacity(int slot);

        /** {@return the buffer-relative offset of a fresh slice, or -1 when it does not fit} */
        long alloc(int slot, long size, long alignment);

        long mappedAddress(int slot, long offset);

        long buffer(int slot);

        /** {@return a short name for the source's home, for the diagnostics line} */
        String kind();

        /** {@return the bytes the source has handed out of {@code slot} since its last rewind} */
        long usedBytes(int slot);

        /**
         * Opens {@code slot}'s next frame at the store's frame boundary, where the slot's
         * previous submission is known complete. The shared ring's source does nothing: that
         * ring's rewind belongs to the frame scheduler's confirmation tail, not to the store.
         * The dedicated pool rewinds its slot region here, the one seat its discipline allows.
         */
        void beginSlot(int slot);

        /** Releases what the source owns. The shared ring is not the store's to release. */
        void delete();
    }

    /** The A/B arm's source: the context's stage 2 frame-linear ring, shared with the geometry. */
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
            return this.ring.buffer(slot);
        }

        @Override
        public String kind() {
            return "shared";
        }

        @Override
        public long usedBytes(int slot) {
            return this.ring.used(slot);
        }

        @Override
        public void beginSlot(int slot) {
            // The shared ring's rewind is the confirmation tail's, called through the context;
            // the store must not rewind a slot whose submission may still be reading it.
        }

        @Override
        public void delete() {
            // The ring and its buffers are the context's, not the store's.
        }
    }

    /** The production source since the separation: the records' own pool (see {@link RecordsRing}). */
    private static final class RecordsRingSource implements RingSource {
        private final RecordsRing pool;

        RecordsRingSource(RecordsRing pool) {
            this.pool = pool;
        }

        @Override
        public int slots() {
            return this.pool.slots();
        }

        @Override
        public long slotCapacity(int slot) {
            return this.pool.slotCapacity(slot);
        }

        @Override
        public long alloc(int slot, long size, long alignment) {
            return this.pool.alloc(slot, size, alignment);
        }

        @Override
        public long mappedAddress(int slot, long offset) {
            return this.pool.mappedAddress(slot, offset);
        }

        @Override
        public long buffer(int slot) {
            return this.pool.buffer();
        }

        @Override
        public String kind() {
            return "dedicated";
        }

        @Override
        public long usedBytes(int slot) {
            return this.pool.used(slot);
        }

        @Override
        public void beginSlot(int slot) {
            this.pool.beginSlot(slot);
        }

        @Override
        public void delete() {
            this.pool.delete();
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
    /**
     * Whether the slot's set has been bound into the open recording since the slot's frame
     * began. While this stands, a lightmap write for the slot is deferred (see
     * {@link #bindLightmap}): the set is inside a command buffer that is still recording, and
     * updating it there is the contract violation the frame-1 validation group measured.
     */
    private final boolean[] boundThisFrame;
    /** A lightmap write deferred because the slot's set was already bound; one per slot. */
    private final boolean[] lightmapWritePending;
    private final long[] pendingLightmapViews;
    private final long[] pendingLightmapSamplers;
    private final int[] pendingLightmapLayouts;
    private int currentSlot;

    /** Selections whose slice did not fit, per slot - the separation's headline counter. */
    private final long[] selectFailures;
    /** The largest window (in records) each slot has served in its current frame. */
    private final int[] servedMaxRecords;
    /** Records refused because the selection has no slice (a failed selection's fallout). */
    private long refusalsNoSlice;
    /** Records refused because the slice could not grow to hold them. */
    private long refusalsGrow;
    /** Records refused because the push block was missing or too short. */
    private long refusalsBlock;
    /** Records successfully written, over the store's life. */
    private long recordsWritten;

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
        this.boundThisFrame = new boolean[this.slots];
        this.lightmapWritePending = new boolean[this.slots];
        this.pendingLightmapViews = new long[this.slots];
        this.pendingLightmapSamplers = new long[this.slots];
        this.pendingLightmapLayouts = new int[this.slots];
        this.selectFailures = new long[this.slots];
        this.servedMaxRecords = new int[this.slots];
        this.descriptorSets = new long[this.slots];
        for (int slot = 0; slot < this.slots; slot++) {
            this.offsets[slot] = -1L;
            this.demand[slot] = (long) INITIAL_RECORDS * RECORD_BYTES;
        }
    }

    /**
     * Builds the production store over the records' slice source, with one descriptor set
     * per slot. The default source is the dedicated pool (the ring records separation, F-4);
     * {@code celtium.vulkan.recordsDedicatedPool=false} selects the shared frame ring instead -
     * the A/B arm, and the fallback when the pool's one buffer cannot be created, which is
     * reported on the diagnostics line by the source kind it prints rather than hidden.
     *
     * @return the store, or {@code null} when its descriptor objects could not be created - the
     *         caller then keeps the direct fallback path
     */
    static InstanceDataStore create(VulkanContext context) {
        RingSource source = null;
        if (Boolean.parseBoolean(System.getProperty("celtium.vulkan.recordsDedicatedPool", "true"))) {
            try {
                source = new RecordsRingSource(new RecordsRing(context));
            } catch (RuntimeException | LinkageError failure) {
                System.out.println("[Celtium] records pool could not be created (" + failure
                        + "); the records fall back to the shared frame ring");
                source = null;
            }
        }
        if (source == null) {
            source = new FrameLinearRingSource(context.celtium$frameLinearRing());
        }
        InstanceDataStore store = new InstanceDataStore(source, storageBufferOffsetAlignment(context));
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
        select(slot, this.demand[slot]);
    }

    /** Carves one selection of {@code bytes} in {@code slot}, replacing whatever it held. */
    private void select(int slot, long bytes) {
        this.records[slot] = 0;
        this.growFailed[slot] = false;
        long offset = this.ring.alloc(slot, bytes, this.alignment);
        if (offset < 0L) {
            this.offsets[slot] = -1L;
            this.capacities[slot] = 0;
            this.selectFailures[slot]++;
        } else {
            this.offsets[slot] = offset;
            this.capacities[slot] = (int) (bytes / RECORD_BYTES);
        }
    }

    /**
     * Takes a fresh, small selection of the current slot for a caller the current selection
     * refused: the per-group (direct) path, whose record need is one group at a time. The
     * window's selection is the batch capture's economy - a whole window's records in one
     * slice, asked at the remembered demand - so its refusal (no room for the demand-sized
     * ask, or for a growth doubling it) says nothing about room for a handful of records.
     * The fallback selection asks only that handful; growth from it proceeds as from any
     * selection, and the remembered demand is left alone - the frame boundary's decay, not
     * this seat, owns what the next window asks.
     *
     * <p>The caller owns this seat's precondition: nothing already recorded against the
     * current selection may still be waiting to bind it. The batch capture's entries bind
     * the selection's offset at their segment's flush, so a caller holding unflushed
     * captures must flush the segment before calling this - the drain's seats all stand
     * after a flush (the group loop flushes the open segment before the direct dispatch,
     * and the capture seat flushes before its retry).
     *
     * @return whether the current slot now holds a slice records can be written into
     */
    boolean reselectFallback() {
        int slot = this.currentSlot;
        select(slot, (long) FALLBACK_RECORDS * RECORD_BYTES);
        return this.offsets[slot] >= 0L;
    }

    int currentSlot() {
        return this.currentSlot;
    }

    /**
     * Points the current slot's set 1 binding 1 at the frame's lightmap, keyed per slot on the
     * last written (view, sampler).
     *
     * <p>The completion gate covers only half of this write's safety. A slot is selected again
     * only after its previous frame completed, so a write here never lands under a
     * <em>submitted</em> command buffer - but since Stage 5 stage 6 recorded the record
     * families directly into the frame's unified buffer, the slot's set can already be bound
     * into the command buffer that is <em>still recording</em> when a later window of the same
     * frame asks for a different lightmap (an earlier window bound the set, the lightmap was
     * reallocated mid-frame, and this call then rewrote the set under the open recording). That
     * is the frame-1 validation group the separation record fixed: the recording's back half
     * invalidated and the frame lost. The gate says nothing about that case, so this method no
     * longer relies on it alone: once the slot's set has been bound in the slot's current frame
     * (the bind seats report it through {@link #noteSetBound}), a changed lightmap is not
     * written here at all. The request is remembered and {@link #beginFrame} applies it at the
     * slot's next frame boundary, before that frame's recording begins - the one seat where
     * the slot's previous submission is known complete and nothing is recording yet. A lightmap
     * switch can therefore land one frame late; the switch points (load, resource reload) are
     * exactly the scenes where that costs nothing visible.
     */
    void bindLightmap(long imageView, long sampler, int imageLayout) {
        int slot = this.currentSlot;
        if (imageView == VK_NULL_HANDLE) {
            return;
        }
        if (this.boundThisFrame[slot]) {
            // The set is inside the open recording: remember the newest request and let the
            // frame boundary apply it. A request that returns to the written value simply
            // overwrites the remembered one and is then a no-op when applied.
            this.pendingLightmapViews[slot] = imageView;
            this.pendingLightmapSamplers[slot] = sampler;
            this.pendingLightmapLayouts[slot] = imageLayout;
            this.lightmapWritePending[slot] = true;
            return;
        }
        writeLightmap(slot, imageView, sampler, imageLayout);
    }

    /** Writes the slot's binding 1, unless the slot already holds exactly this lightmap. */
    private void writeLightmap(int slot, long imageView, long sampler, int imageLayout) {
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

    /**
     * Records that the current slot's set has just been bound into the open recording. Every
     * seat that binds the records set ({@code vkCmdBindDescriptorSets} over
     * {@link #descriptorSet()}) must call this: it is what makes {@link #bindLightmap} defer
     * instead of rewriting a bound set, so a bind seat added without it reopens the frame-1
     * invalidation this class now guards against.
     */
    void noteSetBound() {
        this.boundThisFrame[this.currentSlot] = true;
    }

    /**
     * Opens the store's side of a frame slot's new frame: the slot's previous submission has
     * completed and its next recording has not begun (the backend calls this from the recording
     * start, after the slot's completion wait). The bound mark from the slot's last frame is
     * cleared and a lightmap write deferred during that frame is applied now - before any
     * recording of the new frame can bind the set again.
     */
    void beginFrame(int slot) {
        if (slot < 0 || slot >= this.slots) {
            throw new IllegalArgumentException("slot " + slot);
        }
        // The slot's previous submission is complete at this seat, so the dedicated pool may
        // rewind its region now (the shared ring's source ignores this; its rewind is the
        // confirmation tail's). Demand then decays to the largest window the finished frame
        // actually served: inside a frame it still grows on demand, but one busy phase no
        // longer taxes every later frame of the slot at its peak size.
        this.ring.beginSlot(slot);
        long servedBytes = (long) this.servedMaxRecords[slot] * RECORD_BYTES;
        this.demand[slot] = Math.max((long) INITIAL_RECORDS * RECORD_BYTES, servedBytes);
        this.servedMaxRecords[slot] = 0;
        this.boundThisFrame[slot] = false;
        if (this.lightmapWritePending[slot]) {
            this.lightmapWritePending[slot] = false;
            writeLightmap(slot, this.pendingLightmapViews[slot], this.pendingLightmapSamplers[slot],
                    this.pendingLightmapLayouts[slot]);
        }
    }

    /** {@return the lightmap view last bound into the slot's set (0 when none), for tests} */
    long boundLightmapView(int slot) {
        return this.lightmapViews[slot];
    }

    /** {@return the lightmap view whose write is deferred for the slot (0 when none), for tests} */
    long pendingLightmapView(int slot) {
        return this.lightmapWritePending[slot] ? this.pendingLightmapViews[slot] : 0L;
    }

    /**
     * Appends one record to the selected slot, with the record semantics the class
     * documentation states.
     *
     * @param pushBlock a block of at least 208 bytes; as much of it as the record stride holds
     *                  is copied and the rest of the record is zeroed
     * @param arrayIndex the bindless array index stored as float bits at offset 216, or -1 for none
     * @return the record's index in the slot, or -1 when the block is too short, the selection has
     *         no slice, or the slice cannot grow to hold the record
     */
    int record(ByteBuffer pushBlock, int arrayIndex) {
        if (pushBlock == null || pushBlock.capacity() < MIN_BLOCK_BYTES) {
            this.refusalsBlock++;
            return -1;
        }
        int slot = this.currentSlot;
        if (this.offsets[slot] < 0L) {
            this.refusalsNoSlice++;
            return -1;
        }
        if (this.records[slot] + 1 > this.capacities[slot] && !this.grow(slot)) {
            this.refusalsGrow++;
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
        int written = this.records[slot]++;
        this.recordsWritten++;
        if (this.records[slot] > this.servedMaxRecords[slot]) {
            this.servedMaxRecords[slot] = this.records[slot];
        }
        return written;
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
        // The doubling is of the slice the selection actually holds, not of the remembered
        // demand: the two agree for a window selection (the demand is its size, and a growth
        // raises both together), but a fallback selection is deliberately smaller than the
        // demand, and doubling the demand from there would ask for room the region has
        // already refused once. The demand itself only ever rises here - a fallback
        // selection's small growth must not shrink what the next window asks.
        long wanted = Math.max((long) this.capacities[slot] * RECORD_BYTES * 2L,
                (this.records[slot] + 1L) * RECORD_BYTES);
        long offset = this.ring.alloc(slot, wanted, this.alignment);
        if (offset < 0L) {
            this.growFailed[slot] = true;
            return false;
        }
        long from = this.ring.mappedAddress(slot, this.offsets[slot]);
        long to = this.ring.mappedAddress(slot, offset);
        MemoryUtil.memCopy(from, to, (long) this.records[slot] * RECORD_BYTES);
        this.offsets[slot] = offset;
        this.demand[slot] = Math.max(this.demand[slot], wanted);
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

    /** {@return how many frame slots the store serves} */
    int slots() {
        return this.slots;
    }

    /** {@return the slice source's home: {@code dedicated} for the records pool, {@code shared} for the frame ring} */
    String sourceKind() {
        return this.ring.kind();
    }

    /** {@return the bytes the next selection of {@code slot} will ask for} */
    long demandBytes(int slot) {
        return this.demand[slot];
    }

    /** {@return how many of {@code slot}'s selections found no room for their slice} */
    long selectFailures(int slot) {
        return this.selectFailures[slot];
    }

    /** {@return the bytes the source has handed out of {@code slot} since its last rewind} */
    long sourceUsedBytes(int slot) {
        return this.ring.usedBytes(slot);
    }

    /** {@return the fixed capacity of {@code slot}'s home in the source} */
    long sourceCapacityBytes(int slot) {
        return this.ring.slotCapacity(slot);
    }

    /** {@return records refused because the selection had no slice} */
    long refusalsNoSlice() {
        return this.refusalsNoSlice;
    }

    /** {@return records refused because the slice could not grow to hold them} */
    long refusalsGrow() {
        return this.refusalsGrow;
    }

    /** {@return records refused because the push block was missing or too short} */
    long refusalsBlock() {
        return this.refusalsBlock;
    }

    /** {@return records successfully written over the store's life} */
    long recordsWritten() {
        return this.recordsWritten;
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

    /**
     * {@return the mapped host address of record {@code index} in the current selection, for a
     *         host-side reader of the record bytes (the batch sink's push read), or 0 when the
     *         selection has no slice}
     * <p>
     * The address is computed in the store's own home, through its ring source: the records
     * live wherever the source puts them (the dedicated records pool in production), and the
     * slice offset is only meaningful in that home. A reader that instead adds the offset to
     * another ring's mapping reads that ring's bytes as the record - the seat of the
     * panel-background defect, where the batch sink pushed frame-ring geometry bytes as the
     * array fragment's tint and alpha reference. The offset is read at call time, so a slice
     * growth between the record's write and the read is followed, exactly as the descriptor
     * bind follows it.
     */
    long recordAddress(int index) {
        int slot = this.currentSlot;
        if (this.offsets[slot] < 0L) {
            return 0L;
        }
        return this.ring.mappedAddress(slot,
                this.offsets[slot] + (long) index * RECORD_BYTES);
    }

    /**
     * Releases the descriptor objects and the slice source's own home. The shared ring is not
     * the store's to release (its source's delete is empty); the dedicated pool is, and is
     * released here.
     */
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
        this.ring.delete();
    }
}
