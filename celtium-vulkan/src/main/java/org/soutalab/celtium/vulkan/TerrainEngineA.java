package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

import org.soutalab.celtium.vulkan.terrain.AsyncSectionMesher;
import org.soutalab.celtium.vulkan.terrain.SectionDesc;
import org.soutalab.celtium.vulkan.terrain.TerrainSectionStore;
import org.soutalab.celtium.vulkan.terrain.TerrainUploadBudget;

/**
 * The Tier-A terrain executor of the new rendering architecture (design sections
 * 2.2/2.4, {@code SECTION-DESC-SPEC-2026-11-25.md}): one instance per render pass,
 * over a shared {@link TerrainSectionStore}. Sections register once and stay resident
 * as SectionDesc records; each frame, one compute dispatch per slab culls the pass'
 * records (frustum + distance + per-bucket facing; Hi-Z joins when the frame graph
 * produces the pyramid) into per-slab indirect streams, and the pass draws each slab
 * with two {@code vkCmdDrawIndexedIndirectCount} calls (bucket stream over the shared
 * static quad index buffer, sorted stream over the sorted-index arena).
 * <p>
 * Registration uploads are metered by the {@link TerrainUploadBudget}: when a frame's
 * budget is spent, registrations queue inside the engine and complete in
 * {@link #prepareFrame} as later frames admit them. A queued section's built buffers
 * stay owned by the caller until {@link #isRegistered} reports it resident — the same
 * shape as the legacy renderer's pending meshes, which live until upload completes.
 * <p>
 * The graphics side (the Tier-A pipeline with {@code terrain_a.vert}, descriptor set 0
 * extended with bindings 6/7/8 for the desc table, factor bases and factor arena) is
 * bound by the caller inside the render pass; this class owns only the compute
 * pipeline, the resident buffers and the draw issuance, in the E10 executor's shape.
 * Nothing here is created unless the Tier-A flag is on, and a failure anywhere
 * disables the executor with one printed line rather than touching the frame.
 */
final class TerrainEngineA {

    private static final int WORKGROUP = 256;
    private static final int PUSH_BYTES = 128;
    private static final int COMMAND_BYTES = 20;
    private static final int MAX_SLABS = org.soutalab.celtium.vulkan.terrain.TerrainSlabAllocator.DEFAULT_MAX_SLABS;
    private static final int MIN_STREAM_COMMANDS = 1024;
    private static final int PRINT_EVERY_FRAMES = 60;

    private final VulkanContext context;
    private final int passOrdinal;
    private final String passName;
    private final TerrainSectionStore store;
    private final TerrainUploadBudget budget;
    private long setLayout;
    private long pipelineLayout;
    private long pipeline;
    private boolean disabled;

    // Resident GPU images of the store (host-visible, written through the mapping).
    private VulkanHostBuffer descBuffer;
    private VulkanHostBuffer factorBaseBuffer;
    private long uploadedGeneration = -1;
    private final List<VulkanHostBuffer> slabBuffers = new ArrayList<>();
    private VulkanHostBuffer factorBuffer;
    private VulkanHostBuffer sortedBuffer;
    private VulkanHostBuffer sharedIndexBuffer;

    private final Slot[] slots = new Slot[FrameSubmissionScheduler.FRAME_SLOTS];
    private final ArrayDeque<AsyncSectionMesher.BuiltSectionA> deferred = new ArrayDeque<>();
    private long deferredGeneration;

    private long framesPrepared;
    private long framesDrawn;
    private long issuedCommands;

    private static final class Slot {
        final VulkanHostBuffer[] bucketOut = new VulkanHostBuffer[MAX_SLABS];
        final VulkanHostBuffer[] sortedOut = new VulkanHostBuffer[MAX_SLABS];
        final VulkanHostBuffer[] counts = new VulkanHostBuffer[MAX_SLABS];
        final int[] streamCapacity = new int[MAX_SLABS];
        long descriptorPool;
        final long[] descriptorSets = new long[MAX_SLABS];
        final long[] describedDesc = new long[MAX_SLABS];
        final long[] describedBucket = new long[MAX_SLABS];
        final long[] describedSorted = new long[MAX_SLABS];
        final long[] describedCounts = new long[MAX_SLABS];
        final boolean[] armed = new boolean[MAX_SLABS];
    }

    TerrainEngineA(VulkanContext context, int passOrdinal, String passName,
                   TerrainSectionStore store, TerrainUploadBudget budget) {
        this.context = context;
        this.passOrdinal = passOrdinal;
        this.passName = passName;
        this.store = store;
        this.budget = budget;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            createPipeline(stack);
        }
        for (int i = 0; i < this.slots.length; i++) {
            this.slots[i] = new Slot();
        }
        System.out.println("[Celtium] CELTIUM_TERRAIN_A created pass=" + passName
                + " drawIndirectCount=" + context.drawIndirectCountEnabled()
                + " maxDrawIndirectCount=" + context.maxDrawIndirectCount());
    }

    boolean disabled() {
        return this.disabled;
    }

    private void disable(String what, RuntimeException error) {
        if (!this.disabled) {
            this.disabled = true;
            System.out.println("[Celtium] CELTIUM_TERRAIN_A_DISABLED pass=" + this.passName
                    + " at=" + what + " reason=" + error);
        }
    }

    // ----------------------------------------------------------------------------------
    // Registration
    // ----------------------------------------------------------------------------------

    /**
     * Registers one built section, uploading its bytes into the slab/factor/sorted
     * buffers. Admission is metered by the upload budget: a section that does not fit
     * this frame's budget is queued and completed by a later {@link #prepareFrame}.
     *
     * @return the registration, or {@code null} when the section is Tier-A ineligible,
     *         an arena is exhausted, or admission was deferred (poll
     *         {@link #isRegistered} for the deferred case)
     */
    TerrainSectionStore.Registration register(AsyncSectionMesher.BuiltSectionA built, long generation) {
        if (this.disabled) {
            return null;
        }
        try {
            if (!this.budget.tryAdmit(built.slabBytes())) {
                this.deferred.addLast(built);
                this.deferredGeneration = generation;
                return null;
            }
            return upload(built, generation);
        } catch (RuntimeException error) {
            disable("register", error);
            return null;
        }
    }

    private TerrainSectionStore.Registration upload(AsyncSectionMesher.BuiltSectionA built, long generation) {
        TerrainSectionStore.Registration registration = this.store.register(built, generation);
        if (registration == null) {
            return null;
        }
        VulkanHostBuffer slab = slabBuffer(registration.geometry.slabId);
        long vertexAddress = slab.mappedAddress() + registration.geometry.byteOffset;
        MemoryUtil.memCopy(MemoryUtil.memAddress(built.vertexData), vertexAddress, built.vertexBytes);
        if (built.rdhFactors != null) {
            long factorAddress = factorBuffer().mappedAddress() + registration.factor.byteOffset;
            for (int i = 0; i < built.rdhFactors.length; i++) {
                MemoryUtil.memPutInt(factorAddress + (long) i * 4L, built.rdhFactors[i]);
            }
        }
        if (built.sortedIndices != null && registration.sorted != null) {
            long sortedAddress = sortedBuffer().mappedAddress() + registration.sorted.byteOffset;
            MemoryUtil.memCopy(MemoryUtil.memAddress(built.sortedIndices.getDirectBuffer()),
                    sortedAddress, built.sortedIndices.getLength());
        }
        return registration;
    }

    TerrainSectionStore.Registration unregister(TerrainSectionStore.Key key, long releaseTimeline) {
        return this.store.unregister(key, releaseTimeline);
    }

    boolean isRegistered(TerrainSectionStore.Key key) {
        return this.store.get(key) != null;
    }

    /** Raw VkBuffer of the resident desc table, for graphics binding 6. */
    long descBufferHandle() {
        uploadDescImageIfMoved();
        return this.descBuffer == null ? 0L : this.descBuffer.buffer();
    }

    /** Raw VkBuffer of the factor-base image, for graphics binding 7. */
    long factorBaseBufferHandle() {
        uploadDescImageIfMoved();
        return this.factorBaseBuffer == null ? 0L : this.factorBaseBuffer.buffer();
    }

    /** Raw VkBuffer of the factor arena, for graphics binding 8. */
    long factorBufferHandle() {
        return factorBuffer().buffer();
    }

    /**
     * Re-uploads a section's sorted indices after a translucent re-sort (wave-2
     * item 4): the store keeps or replaces the sorted allocation, and the new
     * bytes are copied into the sorted arena at the resulting offset.
     *
     * @return whether the resident record now carries these indices
     */
    boolean updateSorted(TerrainSectionStore.Key key, org.embeddedt.embeddium.impl.common.util.NativeBuffer indices,
                         long releaseTimeline) {
        if (this.disabled || indices == null) {
            return false;
        }
        try {
            TerrainSectionStore.SortedUpdate result =
                    this.store.updateSorted(key, indices.getLength(), releaseTimeline);
            if (result == TerrainSectionStore.SortedUpdate.NOT_REGISTERED
                    || result == TerrainSectionStore.SortedUpdate.ARENA_FULL) {
                return false;
            }
            TerrainSectionStore.Registration registration = this.store.get(key);
            long address = sortedBuffer().mappedAddress() + registration.sorted.byteOffset;
            MemoryUtil.memCopy(MemoryUtil.memAddress(indices.getDirectBuffer()), address, indices.getLength());
            return true;
        } catch (RuntimeException error) {
            disable("updateSorted", error);
            return false;
        }
    }

    int deferredCount() {
        return this.deferred.size();
    }

    // ----------------------------------------------------------------------------------
    // Per-frame cull and draw
    // ----------------------------------------------------------------------------------

    /**
     * The frame head, recorded outside the render pass: admits deferred registrations
     * against the new frame's budget, re-uploads the desc image when it moved, then
     * records the per-slab cull dispatches into {@code commandBuffer} for
     * {@code slotIndex}. The previous use of this slot is complete by the frame-slot
     * contract, so its counts are read back into the census here before being refilled.
     *
     * @return whether any slab was armed for {@link #recordDraws}
     */
    boolean prepareFrame(VkCommandBuffer commandBuffer, int slotIndex, float[] viewProjection,
                         double cameraX, double cameraY, double cameraZ) {
        if (this.disabled) {
            return false;
        }
        if (!this.context.drawIndirectCountEnabled()) {
            return false;
        }
        try {
            this.budget.beginFrame();
            while (!this.deferred.isEmpty()) {
                AsyncSectionMesher.BuiltSectionA head = this.deferred.peekFirst();
                if (!this.budget.tryAdmitDeferred(head.slabBytes())) {
                    break;
                }
                this.deferred.pollFirst();
                upload(head, this.deferredGeneration);
            }
            uploadDescImageIfMoved();
            if (this.store.recordCount() == 0) {
                return false;
            }
            Slot slot = this.slots[slotIndex];
            // Read back the counts this slot's last dispatch produced (host-visible).
            for (int s = 0; s < MAX_SLABS; s++) {
                if (slot.counts[s] != null && slot.armed[s]) {
                    long address = slot.counts[s].mappedAddress();
                    this.issuedCommands += MemoryUtil.memGetInt(address) + MemoryUtil.memGetInt(address + 4);
                }
                slot.armed[s] = false;
            }
            float[] planes = GpuTerrainDrawTable.extractPlanes(viewProjection);
            int[] slabCounts = this.store.slabRecordCounts();
            boolean any = false;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                for (int s = 0; s < slabCounts.length; s++) {
                    if (slabCounts[s] == 0) {
                        continue;
                    }
                    dispatchSlab(stack, commandBuffer, slot, s, slabCounts[s], planes,
                            (float) cameraX, (float) cameraY, (float) cameraZ);
                    slot.armed[s] = true;
                    any = true;
                }
            }
            this.framesPrepared++;
            if (this.framesPrepared % PRINT_EVERY_FRAMES == 0) {
                System.out.println("[Celtium] CELTIUM_TERRAIN_A pass=" + this.passName
                        + " frames=" + this.framesPrepared
                        + " drawn=" + this.framesDrawn
                        + " issuedCommands=" + this.issuedCommands
                        + " records=" + this.store.recordCount()
                        + " deferred=" + this.deferred.size());
            }
            return any;
        } catch (RuntimeException error) {
            disable("prepareFrame", error);
            return false;
        }
    }

    private void dispatchSlab(MemoryStack stack, VkCommandBuffer commandBuffer, Slot slot, int slab,
                              int slabRecords, float[] planes, float cameraX, float cameraY, float cameraZ) {
        int capacity = Math.max(MIN_STREAM_COMMANDS, slabRecords * SectionDesc.BUCKETS);
        ensureStreamBuffers(slot, slab, capacity);
        ensureDescriptors(stack, slot, slab);
        vkCmdFillBuffer(commandBuffer, slot.counts[slab].buffer(), 0L, 8L, 0);
        VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack).sType$Default()
                .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
        vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_HOST_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, barrier, null, null);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipelineLayout, 0,
                stack.longs(slot.descriptorSets[slab]), null);
        ByteBuffer push = stack.malloc(PUSH_BYTES);
        for (int i = 0; i < 24; i++) {
            push.putFloat(i * 4, planes[i]);
        }
        push.putFloat(96, cameraX);
        push.putFloat(100, cameraY);
        push.putFloat(104, cameraZ);
        push.putFloat(108, GpuTerrainCulling.MAX_DISTANCE_BLOCKS);
        push.putInt(112, this.store.recordCount());
        push.putInt(116, this.passOrdinal);
        push.putInt(120, slab);
        push.putInt(124, slot.streamCapacity[slab]);
        push.clear();
        vkCmdPushConstants(commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
        vkCmdDispatch(commandBuffer, (this.store.recordCount() + WORKGROUP - 1) / WORKGROUP, 1, 1);
        barrier.get(0).srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
        vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT, 0, barrier, null, null);
    }

    /**
     * Issues the armed slabs' draws inside the render pass. The caller has bound the
     * Tier-A graphics pipeline and descriptor set 0 (with the desc table, factor bases
     * and factor arena at bindings 6/7/8, as {@code terrain_a.vert} declares); this
     * method binds each slab's vertex buffer and the two index buffers in turn.
     *
     * @return whether at least one slab was drawn
     */
    boolean recordDraws(VkCommandBuffer commandBuffer, int slotIndex) {
        if (this.disabled) {
            return false;
        }
        Slot slot = this.slots[slotIndex];
        boolean any = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer vertexBuffers = stack.mallocLong(1);
            LongBuffer offsets = stack.mallocLong(1);
            for (int s = 0; s < MAX_SLABS; s++) {
                if (!slot.armed[s]) {
                    continue;
                }
                VulkanHostBuffer slab = slabBuffer(s);
                vertexBuffers.put(0, slab.buffer());
                offsets.put(0, 0L);
                vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets);
                int maxDraws = slot.streamCapacity[s];
                if (this.context.maxDrawIndirectCount() > 0) {
                    maxDraws = Math.min(maxDraws, this.context.maxDrawIndirectCount());
                }
                vkCmdBindIndexBuffer(commandBuffer, sharedIndexBuffer().buffer(), 0L, VK_INDEX_TYPE_UINT32);
                VK12.vkCmdDrawIndexedIndirectCount(commandBuffer, slot.bucketOut[s].buffer(), 0L,
                        slot.counts[s].buffer(), 0L, maxDraws, COMMAND_BYTES);
                vkCmdBindIndexBuffer(commandBuffer, sortedBuffer().buffer(), 0L, VK_INDEX_TYPE_UINT32);
                VK12.vkCmdDrawIndexedIndirectCount(commandBuffer, slot.sortedOut[s].buffer(), 0L,
                        slot.counts[s].buffer(), 4L, maxDraws, COMMAND_BYTES);
                any = true;
            }
        }
        if (any) {
            this.framesDrawn++;
        }
        return any;
    }

    // ----------------------------------------------------------------------------------
    // Buffers
    // ----------------------------------------------------------------------------------

    private void uploadDescImageIfMoved() {
        long generation = this.store.imageGeneration();
        if (generation == this.uploadedGeneration && this.descBuffer != null) {
            return;
        }
        int[] descImage = this.store.descImage();
        int[] factorBaseImage = this.store.factorBaseImage();
        long descBytes = Math.max(48L, (long) descImage.length * 4L);
        if (this.descBuffer == null) {
            this.descBuffer = new VulkanHostBuffer(this.context, descBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            this.factorBaseBuffer = new VulkanHostBuffer(this.context, Math.max(4L, (long) factorBaseImage.length * 4L),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        } else {
            this.descBuffer.ensureCapacity(descBytes);
            this.factorBaseBuffer.ensureCapacity(Math.max(4L, (long) factorBaseImage.length * 4L));
        }
        long descAddress = this.descBuffer.mappedAddress();
        for (int i = 0; i < descImage.length; i++) {
            MemoryUtil.memPutInt(descAddress + (long) i * 4L, descImage[i]);
        }
        long factorAddress = this.factorBaseBuffer.mappedAddress();
        for (int i = 0; i < factorBaseImage.length; i++) {
            MemoryUtil.memPutInt(factorAddress + (long) i * 4L, factorBaseImage[i]);
        }
        this.uploadedGeneration = generation;
    }

    private VulkanHostBuffer slabBuffer(int slab) {
        while (this.slabBuffers.size() <= slab) {
            this.slabBuffers.add(null);
        }
        VulkanHostBuffer buffer = this.slabBuffers.get(slab);
        if (buffer == null) {
            buffer = new VulkanHostBuffer(this.context, this.store.geometryAllocator().slabBytes(),
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            this.slabBuffers.set(slab, buffer);
        }
        return buffer;
    }

    private VulkanHostBuffer factorBuffer() {
        if (this.factorBuffer == null) {
            this.factorBuffer = new VulkanHostBuffer(this.context, 32L * 1024 * 1024,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        return this.factorBuffer;
    }

    private VulkanHostBuffer sortedBuffer() {
        if (this.sortedBuffer == null) {
            this.sortedBuffer = new VulkanHostBuffer(this.context,
                    this.store.sortedAllocator().slabBytes(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        }
        return this.sortedBuffer;
    }

    private VulkanHostBuffer sharedIndexBuffer() {
        if (this.sharedIndexBuffer == null) {
            int quads = TerrainSectionStore.MAX_QUADS_PER_SLAB;
            this.sharedIndexBuffer = new VulkanHostBuffer(this.context, (long) quads * 6 * 4,
                    VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            long address = this.sharedIndexBuffer.mappedAddress();
            for (int q = 0; q < quads; q++) {
                long at = address + (long) q * 6 * 4;
                int baseVertex = q * 4;
                MemoryUtil.memPutInt(at, baseVertex);
                MemoryUtil.memPutInt(at + 4, baseVertex + 1);
                MemoryUtil.memPutInt(at + 8, baseVertex + 2);
                MemoryUtil.memPutInt(at + 12, baseVertex);
                MemoryUtil.memPutInt(at + 16, baseVertex + 2);
                MemoryUtil.memPutInt(at + 20, baseVertex + 3);
            }
        }
        return this.sharedIndexBuffer;
    }

    private void ensureStreamBuffers(Slot slot, int slab, int capacityCommands) {
        int storage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        if (slot.bucketOut[slab] == null) {
            slot.bucketOut[slab] = new VulkanHostBuffer(this.context, (long) capacityCommands * COMMAND_BYTES,
                    storage | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
            slot.sortedOut[slab] = new VulkanHostBuffer(this.context, (long) capacityCommands * COMMAND_BYTES,
                    storage | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
            slot.counts[slab] = new VulkanHostBuffer(this.context, 8L,
                    storage | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
            slot.streamCapacity[slab] = capacityCommands;
        } else if (slot.streamCapacity[slab] < capacityCommands) {
            slot.bucketOut[slab].ensureCapacity((long) capacityCommands * COMMAND_BYTES);
            slot.sortedOut[slab].ensureCapacity((long) capacityCommands * COMMAND_BYTES);
            slot.streamCapacity[slab] = capacityCommands;
        }
    }

    private void ensureDescriptors(MemoryStack stack, Slot slot, int slab) {
        if (slot.descriptorSets[slab] != 0L
                && slot.describedDesc[slab] == this.descBuffer.buffer()
                && slot.describedBucket[slab] == slot.bucketOut[slab].buffer()
                && slot.describedSorted[slab] == slot.sortedOut[slab].buffer()
                && slot.describedCounts[slab] == slot.counts[slab].buffer()) {
            return;
        }
        VkDevice device = this.context.device();
        LongBuffer handle = stack.mallocLong(1);
        if (slot.descriptorPool == 0L) {
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(4 * MAX_SLABS);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(MAX_SLABS).pPoolSizes(poolSize);
            check(vkCreateDescriptorPool(device, poolInfo, null, handle), "create terrain-a descriptor pool");
            slot.descriptorPool = handle.get(0);
        }
        if (slot.descriptorSets[slab] == 0L) {
            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(slot.descriptorPool).pSetLayouts(stack.longs(this.setLayout));
            check(vkAllocateDescriptorSets(device, setInfo, handle), "allocate terrain-a descriptor set");
            slot.descriptorSets[slab] = handle.get(0);
        }
        VulkanHostBuffer[] buffers = {this.descBuffer, slot.bucketOut[slab], slot.sortedOut[slab], slot.counts[slab]};
        VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(4, stack);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(4, stack);
        for (int i = 0; i < 4; i++) {
            infos.get(i).buffer(buffers[i].buffer()).offset(0).range(VK_WHOLE_SIZE);
            writes.get(i).sType$Default().dstSet(slot.descriptorSets[slab]).dstBinding(i).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).pBufferInfo(infos.slice(i, 1));
        }
        vkUpdateDescriptorSets(device, writes, null);
        slot.describedDesc[slab] = this.descBuffer.buffer();
        slot.describedBucket[slab] = slot.bucketOut[slab].buffer();
        slot.describedSorted[slab] = slot.sortedOut[slab].buffer();
        slot.describedCounts[slab] = slot.counts[slab].buffer();
    }

    private void createPipeline(MemoryStack stack) {
        VkDevice device = this.context.device();
        LongBuffer handle = stack.mallocLong(1);
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(4, stack);
        for (int i = 0; i < 4; i++) {
            bindings.get(i).binding(i).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
        }
        VkDescriptorSetLayoutCreateInfo setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default().pBindings(bindings);
        check(vkCreateDescriptorSetLayout(device, setInfo, null, handle), "create terrain-a set layout");
        this.setLayout = handle.get(0);
        VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
        range.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(PUSH_BYTES);
        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                .pSetLayouts(stack.longs(this.setLayout)).pPushConstantRanges(range);
        check(vkCreatePipelineLayout(device, layoutInfo, null, handle), "create terrain-a pipeline layout");
        this.pipelineLayout = handle.get(0);
        ByteBuffer code = SpirvShaders.load("terrain_cull_a.comp");
        long module = 0L;
        try {
            VkShaderModuleCreateInfo shaderInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            check(vkCreateShaderModule(device, shaderInfo, null, handle), "create terrain-a shader module");
            module = handle.get(0);
            VkComputePipelineCreateInfo.Buffer create = VkComputePipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default().layout(this.pipelineLayout);
            create.get(0).stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module)
                    .pName(stack.UTF8("main"));
            check(PipelineCreateCensus.createComputeUncached(device, create, handle, "terrain-a-cull"),
                    "create terrain-a compute pipeline");
            this.pipeline = handle.get(0);
        } finally {
            if (module != 0L) {
                vkDestroyShaderModule(device, module, null);
            }
            MemoryUtil.memFree(code);
        }
    }

    /** Destroys every executor resource. Called from the backend's own teardown. */
    void delete() {
        VkDevice device = this.context.device();
        for (Slot slot : this.slots) {
            if (slot.descriptorPool != 0L) {
                vkDestroyDescriptorPool(device, slot.descriptorPool, null);
            }
            for (int s = 0; s < MAX_SLABS; s++) {
                if (slot.bucketOut[s] != null) slot.bucketOut[s].delete();
                if (slot.sortedOut[s] != null) slot.sortedOut[s].delete();
                if (slot.counts[s] != null) slot.counts[s].delete();
            }
        }
        if (this.descBuffer != null) this.descBuffer.delete();
        if (this.factorBaseBuffer != null) this.factorBaseBuffer.delete();
        for (VulkanHostBuffer slab : this.slabBuffers) {
            if (slab != null) slab.delete();
        }
        if (this.factorBuffer != null) this.factorBuffer.delete();
        if (this.sortedBuffer != null) this.sortedBuffer.delete();
        if (this.sharedIndexBuffer != null) this.sharedIndexBuffer.delete();
        if (this.pipeline != 0L) {
            vkDestroyPipeline(device, this.pipeline, null);
        }
        if (this.pipelineLayout != 0L) {
            vkDestroyPipelineLayout(device, this.pipelineLayout, null);
        }
        if (this.setLayout != 0L) {
            vkDestroyDescriptorSetLayout(device, this.setLayout, null);
        }
    }

    private void check(int result, String what) {
        if (result == VK_ERROR_DEVICE_LOST) {
            throw this.context.celtium$reportDeviceLost(what, result);
        }
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed: VkResult " + result);
        }
    }

}
