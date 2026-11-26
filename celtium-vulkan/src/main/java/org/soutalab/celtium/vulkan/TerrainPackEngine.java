package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

import org.soutalab.celtium.vulkan.terrain.AsyncSectionMesher;
import org.soutalab.celtium.vulkan.terrain.SectionDesc;
import org.soutalab.celtium.vulkan.terrain.TerrainResidencyGate;
import org.soutalab.celtium.vulkan.terrain.TerrainSectionStore;
import org.soutalab.celtium.vulkan.terrain.TerrainUploadBudget;

/**
 * The pack-format variant executor of the Tier-A machinery (R-17 candidate
 * 2-MIN): one instance per render pass whose presented vertex format is
 * foreign (a shader-pack format such as Demonica's extended format), over the
 * same shared {@link TerrainSectionStore}, the same cull shader, the same
 * residency gate and the same upload budget as {@link TerrainEngineA}. Only
 * the geometry format and the program vary per pass: the presented byte
 * stream is stored unconverted in the pass format's own pack arena (see
 * {@link TerrainSectionStore.PackArena}), the cull dispatch filters the
 * shared desc table by this pass' ordinal and the pack slab ids, and the
 * draws bind the pack arena buffer under the pack route's own lowered
 * program, which the caller binds.
 * <p>
 * The arming rule is stricter than the vanilla executor's, deliberately: the
 * pack route keeps a complete legacy fallback inside the same method, so the
 * variant arms only while this pass' residency is complete — a missing
 * section falls the whole pass back to legacy batches instead of vanishing
 * (the pass-unit all-or-nothing discipline). Registration uploads are metered
 * by the shared budget exactly as on the vanilla side; while uploads are
 * still draining, residency is incomplete and the legacy route draws.
 * <p>
 * Nothing here is created unless a foreign-format pass registers a section,
 * and a failure anywhere disables the executor with one printed line rather
 * than touching the frame.
 */
final class TerrainPackEngine {

    private static final int WORKGROUP = 256;
    private static final int PUSH_BYTES = 128;
    private static final int COMMAND_BYTES = 20;
    private static final int MIN_STREAM_COMMANDS = 1024;
    private static final int PRINT_EVERY_FRAMES = 60;
    /** Desc slab ids span the vanilla range plus every pack arena's slabs. */
    private static final int MAX_SLAB_IDS = TerrainSectionStore.PACK_SLAB_BASE
            + TerrainSectionStore.MAX_PACK_ARENAS
            * org.soutalab.celtium.vulkan.terrain.TerrainSlabAllocator.DEFAULT_MAX_SLABS;

    private final VulkanContext context;
    private final int passOrdinal;
    private final String passName;
    private final TerrainSectionStore store;
    private final TerrainUploadBudget budget;
    private final TerrainResidencyGate residencyGate;
    private final TerrainEngineAHost host;
    private volatile TerrainResidencyGate.Report lastResidencyReport;
    private long setLayout;
    private long pipelineLayout;
    private long pipeline;
    private boolean disabled;

    // The resident desc image of the shared store (host-visible, own copy:
    // the compute descriptor set names it, as on the vanilla executor).
    private VulkanHostBuffer descBuffer;
    private long uploadedGeneration = -1;
    private VulkanHostBuffer sortedBuffer;

    private final Slot[] slots = new Slot[FrameSubmissionScheduler.FRAME_SLOTS];
    private final ArrayDeque<AsyncSectionMesher.BuiltSectionA> deferred = new ArrayDeque<>();
    private long deferredGeneration;

    private long framesPrepared;
    private long framesDrawn;
    private long issuedCommands;

    private static final class Slot {
        final VulkanHostBuffer[] bucketOut = new VulkanHostBuffer[MAX_SLAB_IDS];
        final VulkanHostBuffer[] sortedOut = new VulkanHostBuffer[MAX_SLAB_IDS];
        final VulkanHostBuffer[] counts = new VulkanHostBuffer[MAX_SLAB_IDS];
        final int[] streamCapacity = new int[MAX_SLAB_IDS];
        long descriptorPool;
        final long[] descriptorSets = new long[MAX_SLAB_IDS];
        final long[] describedDesc = new long[MAX_SLAB_IDS];
        final long[] describedBucket = new long[MAX_SLAB_IDS];
        final long[] describedSorted = new long[MAX_SLAB_IDS];
        final long[] describedCounts = new long[MAX_SLAB_IDS];
        final boolean[] armed = new boolean[MAX_SLAB_IDS];
    }

    TerrainPackEngine(VulkanContext context, int passOrdinal, String passName,
                      TerrainSectionStore store, TerrainUploadBudget budget,
                      TerrainResidencyGate residencyGate, TerrainEngineAHost host) {
        this.context = context;
        this.passOrdinal = passOrdinal;
        this.passName = passName;
        this.store = store;
        this.budget = budget;
        this.residencyGate = residencyGate;
        this.host = host;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            createPipeline(stack);
        }
        for (int i = 0; i < this.slots.length; i++) {
            this.slots[i] = new Slot();
        }
        System.out.println("[Celtium] CELTIUM_TERRAIN_A_PACK created pass=" + passName
                + " drawIndirectCount=" + context.drawIndirectCountEnabled()
                + " maxDrawIndirectCount=" + context.maxDrawIndirectCount());
    }

    boolean disabled() {
        return this.disabled;
    }

    private void disable(String what, RuntimeException error) {
        if (!this.disabled) {
            this.disabled = true;
            System.out.println("[Celtium] CELTIUM_TERRAIN_A_PACK_DISABLED pass=" + this.passName
                    + " at=" + what + " reason=" + error);
        }
    }

    // ----------------------------------------------------------------------------------
    // Registration
    // ----------------------------------------------------------------------------------

    /**
     * Registers one built foreign-format section, uploading its presented
     * bytes unconverted into the pack arena. Admission is metered by the
     * shared upload budget, exactly as on the vanilla executor.
     *
     * @return the registration, or {@code null} when the section is
     *         ineligible, an arena is exhausted, or admission was deferred
     */
    TerrainSectionStore.Registration register(AsyncSectionMesher.BuiltSectionA built, long generation) {
        if (this.disabled) {
            return null;
        }
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(
                built.sectionX, built.sectionY, built.sectionZ, built.passOrdinal);
        this.residencyGate.expect(key);
        try {
            if (!this.budget.tryAdmit(built.slabBytes())) {
                this.deferred.addLast(built);
                this.deferredGeneration = generation;
                this.residencyGate.noteMissing(key, TerrainResidencyGate.Reason.DEFERRED);
                if (built.slabBytes() > this.budget.bytesPerFrame()) {
                    this.residencyGate.noteHeadOfLine(key);
                }
                return null;
            }
            return upload(built, generation);
        } catch (RuntimeException error) {
            disable("register", error);
            return null;
        }
    }

    private TerrainSectionStore.Registration upload(AsyncSectionMesher.BuiltSectionA built, long generation) {
        TerrainSectionStore.RegisterResult result = this.store.registerDiagnosed(built, generation);
        TerrainSectionStore.Registration registration = result.registration();
        if (registration == null) {
            TerrainSectionStore.Key key = new TerrainSectionStore.Key(
                    built.sectionX, built.sectionY, built.sectionZ, built.passOrdinal);
            this.residencyGate.noteMissing(key, result.failure() == TerrainSectionStore.RegisterFailure.OVERSIZE
                    ? TerrainResidencyGate.Reason.OVERSIZE : TerrainResidencyGate.Reason.ARENA_FULL);
            return null;
        }
        this.residencyGate.noteResident(registration.key);
        VulkanHostBuffer arenaBuffer = this.host.packArenaBuffer(this.context, (int) registration.desc.slabId);
        long vertexAddress = arenaBuffer.mappedAddress() + registration.packGeometry.byteOffset;
        MemoryUtil.memCopy(MemoryUtil.memAddress(built.vertexData), vertexAddress, built.vertexBytes);
        if (built.sortedIndices != null && registration.sorted != null) {
            long sortedAddress = sortedBuffer().mappedAddress() + registration.sorted.byteOffset;
            MemoryUtil.memCopy(MemoryUtil.memAddress(built.sortedIndices.getDirectBuffer()),
                    sortedAddress, built.sortedIndices.getLength());
        }
        return registration;
    }

    boolean isRegistered(TerrainSectionStore.Key key) {
        return this.store.get(key) != null;
    }

    /** Re-uploads a section's sorted indices after a translucent re-sort. */
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

    /** The most recent per-pass residency verification, or null before the first prepared frame. */
    TerrainResidencyGate.Report lastResidencyReport() {
        return this.lastResidencyReport;
    }

    /** This pass' resident pack records, in record order (region-table fill). */
    java.util.List<TerrainSectionStore.Registration> packRecords() {
        return this.store.packRecords(this.passOrdinal);
    }

    // ----------------------------------------------------------------------------------
    // Per-frame cull and draw
    // ----------------------------------------------------------------------------------

    /**
     * The frame head, recorded outside the render pass: admits deferred
     * registrations against the new frame's budget, re-uploads the desc image
     * when it moved, then records this pass' pack-slab cull dispatches into
     * {@code commandBuffer} for {@code slotIndex}.
     *
     * @return whether the variant armed: residency for this pass is complete
     *         and at least one pack slab was dispatched. When this returns
     *         false the caller draws the pass with legacy batches, whole.
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
                    if (head.slabBytes() > this.budget.bytesPerFrame()) {
                        this.residencyGate.noteHeadOfLine(new TerrainSectionStore.Key(
                                head.sectionX, head.sectionY, head.sectionZ, head.passOrdinal));
                    }
                    break;
                }
                this.deferred.pollFirst();
                upload(head, this.deferredGeneration);
            }
            // The variant's arming rule: complete residency or nothing. The
            // pack route's legacy batches draw the whole pass whenever any
            // section of it is not resident in the pack space.
            TerrainResidencyGate.Report report = this.residencyGate.verify(this.passOrdinal, this.store);
            this.lastResidencyReport = report;
            if (!report.complete()) {
                return false;
            }
            uploadDescImageIfMoved();
            java.util.List<TerrainSectionStore.Registration> records = this.store.packRecords(this.passOrdinal);
            if (records.isEmpty()) {
                return false;
            }
            Set<Integer> slabs = new HashSet<>();
            for (TerrainSectionStore.Registration registration : records) {
                slabs.add((int) registration.desc.slabId);
            }
            Slot slot = this.slots[slotIndex];
            for (int s = 0; s < MAX_SLAB_IDS; s++) {
                if (slot.counts[s] != null && slot.armed[s]) {
                    long address = slot.counts[s].mappedAddress();
                    this.issuedCommands += MemoryUtil.memGetInt(address) + MemoryUtil.memGetInt(address + 4);
                }
                slot.armed[s] = false;
            }
            float[] planes = GpuTerrainDrawTable.extractPlanes(viewProjection);
            int[] packCounts = this.store.packSlabRecordCounts();
            boolean any = false;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                for (int slab : slabs) {
                    dispatchSlab(stack, commandBuffer, slot, slab, packCounts[slab], planes,
                            (float) cameraX, (float) cameraY, (float) cameraZ);
                    slot.armed[slab] = true;
                    any = true;
                }
            }
            this.framesPrepared++;
            if (this.framesPrepared == 1 || this.framesPrepared % PRINT_EVERY_FRAMES == 0) {
                System.out.println("[Celtium] CELTIUM_TERRAIN_A_PACK pass=" + this.passName
                        + " frames=" + this.framesPrepared
                        + " drawn=" + this.framesDrawn
                        + " issuedCommands=" + this.issuedCommands
                        + " records=" + records.size()
                        + " deferred=" + this.deferred.size()
                        + " residency[" + TerrainResidencyGate.describe(report) + "]"
                        + " leaks[deferred=" + this.residencyGate.count(TerrainResidencyGate.Reason.DEFERRED)
                        + " oversize=" + this.residencyGate.count(TerrainResidencyGate.Reason.OVERSIZE)
                        + " arenaFull=" + this.residencyGate.count(TerrainResidencyGate.Reason.ARENA_FULL)
                        + " strideGate=" + this.residencyGate.count(TerrainResidencyGate.Reason.STRIDE_GATE)
                        + " buildException=" + this.residencyGate.count(TerrainResidencyGate.Reason.BUILD_EXCEPTION)
                        + "]");
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
     * Issues the armed pack slabs' draws inside the render pass. The caller
     * has bound the pack route's lowered terrain pipeline and claimed its
     * descriptor set once for the whole pass (the region table carries the
     * per-record offsets, indexed by the cull shader's firstInstance); this
     * method binds each pack slab's arena buffer and the two index buffers in
     * turn.
     *
     * @return whether at least one pack slab was drawn
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
            for (int s = 0; s < MAX_SLAB_IDS; s++) {
                if (!slot.armed[s]) {
                    continue;
                }
                VulkanHostBuffer arena = this.host.packArenaBuffer(this.context, s);
                vertexBuffers.put(0, arena.buffer());
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
        long descBytes = Math.max(48L, (long) descImage.length * 4L);
        if (this.descBuffer == null) {
            this.descBuffer = new VulkanHostBuffer(this.context, descBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            // Same rule as the vanilla executor's desc buffer: a descriptor
            // set may keep naming a grown-away handle (VUID-08114), so
            // superseded buffers are kept until delete().
            this.descBuffer.retainSupersededBuffers();
        } else {
            this.descBuffer.ensureCapacity(descBytes);
        }
        long descAddress = this.descBuffer.mappedAddress();
        for (int i = 0; i < descImage.length; i++) {
            MemoryUtil.memPutInt(descAddress + (long) i * 4L, descImage[i]);
        }
        this.uploadedGeneration = generation;
    }

    private VulkanHostBuffer sortedBuffer() {
        if (this.sortedBuffer == null) {
            this.sortedBuffer = new VulkanHostBuffer(this.context,
                    this.store.sortedAllocator().slabBytes(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        }
        return this.sortedBuffer;
    }

    /** The static quad index buffer, owned by the host and shared by every pass. */
    private VulkanHostBuffer sharedIndexBuffer() {
        return this.host.sharedIndexBuffer(this.context);
    }

    private void ensureStreamBuffers(Slot slot, int slab, int capacityCommands) {
        int storage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        if (slot.bucketOut[slab] == null) {
            slot.bucketOut[slab] = new VulkanHostBuffer(this.context, (long) capacityCommands * COMMAND_BYTES,
                    storage | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
            slot.sortedOut[slab] = new VulkanHostBuffer(this.context, (long) capacityCommands * COMMAND_BYTES,
                    storage | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
            slot.counts[slab] = new VulkanHostBuffer(this.context, 8L,
                    storage | VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
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
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(4 * MAX_SLAB_IDS);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(MAX_SLAB_IDS).pPoolSizes(poolSize);
            check(vkCreateDescriptorPool(device, poolInfo, null, handle), "create terrain-a-pack descriptor pool");
            slot.descriptorPool = handle.get(0);
        }
        if (slot.descriptorSets[slab] == 0L) {
            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(slot.descriptorPool).pSetLayouts(stack.longs(this.setLayout));
            check(vkAllocateDescriptorSets(device, setInfo, handle), "allocate terrain-a-pack descriptor set");
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
        check(vkCreateDescriptorSetLayout(device, setInfo, null, handle), "create terrain-a-pack set layout");
        this.setLayout = handle.get(0);
        VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
        range.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(PUSH_BYTES);
        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                .pSetLayouts(stack.longs(this.setLayout)).pPushConstantRanges(range);
        check(vkCreatePipelineLayout(device, layoutInfo, null, handle), "create terrain-a-pack pipeline layout");
        this.pipelineLayout = handle.get(0);
        ByteBuffer code = SpirvShaders.load("terrain_cull_a.comp");
        long module = 0L;
        try {
            VkShaderModuleCreateInfo shaderInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            check(vkCreateShaderModule(device, shaderInfo, null, handle), "create terrain-a-pack shader module");
            module = handle.get(0);
            VkComputePipelineCreateInfo.Buffer create = VkComputePipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default().layout(this.pipelineLayout);
            create.get(0).stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module)
                    .pName(stack.UTF8("main"));
            check(PipelineCreateCensus.createComputeUncached(device, create, handle, "terrain-a-pack-cull"),
                    "create terrain-a-pack compute pipeline");
            this.pipeline = handle.get(0);
        } finally {
            if (module != 0L) {
                vkDestroyShaderModule(device, module, null);
            }
            MemoryUtil.memFree(code);
        }
    }

    /** Destroys every executor resource. Called from the host's teardown, before the arena buffers. */
    void delete() {
        VkDevice device = this.context.device();
        for (Slot slot : this.slots) {
            if (slot.descriptorPool != 0L) {
                vkDestroyDescriptorPool(device, slot.descriptorPool, null);
            }
            for (int s = 0; s < MAX_SLAB_IDS; s++) {
                if (slot.bucketOut[s] != null) slot.bucketOut[s].delete();
                if (slot.sortedOut[s] != null) slot.sortedOut[s].delete();
                if (slot.counts[s] != null) slot.counts[s].delete();
            }
        }
        if (this.descBuffer != null) this.descBuffer.delete();
        if (this.sortedBuffer != null) this.sortedBuffer.delete();
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
