package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

/**
 * The CPU-side plumbing of mesh stage 2 (item 677): per frame slot it owns the three
 * inputs the task shader reads beside the draw table — the {@link SectionDirectory}, the
 * region remap and the occlusion mask — and records the stage's single draw call,
 * {@code vkCmdDrawMeshTasksEXT} with one task workgroup per section, at a terrain pass
 * head. The draw table itself is not duplicated: the caller passes the E10 slot's table
 * buffer handle and it is bound into this class's descriptor set as-is (design
 * section 2.3).
 * <p>
 * The pipeline is the caller's: the stage-2 mesh pipeline (the sibling factory of
 * {@code VulkanMeshTerrain}) builds its pipeline layout with this class's set-0 layout
 * ({@link #taskSetLayout()}) as its first set and a 144-byte push range at
 * {@code VK_SHADER_STAGE_TASK_BIT_EXT}, and hands both handles to
 * {@link #tryRecord}. Nothing here is created unless the caller constructs it, which it
 * does only when {@link #available(VulkanContext)} holds — flag off or a device without
 * the mesh/task pair means no object, no allocation and a byte-identical current path.
 * <p>
 * Every refusal is a counted {@link GateReason}, never a silent fall back (the E10 house
 * form): capability, an active frame budget, a missing pipeline or table, a table the
 * directory cannot be built over, a directory containing a section the task shader must
 * not emit for, an empty directory, and the task workgroup ceiling. A Vulkan failure
 * latches the class disabled with one printed line (stage 1's form) instead of touching
 * the frame.
 */
public final class VulkanMeshTaskDraw {

    /** Stage 2's own switch, separate from stage 1's {@code celtium.vulkan.meshTerrain}. */
    public static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.meshTerrainStage2");

    /**
     * The split budget of design section 2.2: how many quads one mesh workgroup may
     * re-issue. The mesh side's stage-1 form emits 6 vertices per quad against RADV's
     * measured 256-vertex mesh output limit, so 42 (252 vertices) is the safe initial
     * value; it travels in the push block, never baked into the shader, because the
     * budget changes with the mesh side's emission form.
     */
    public static final int QUADS_PER_MESH_WORKGROUP = Math.max(1,
            Integer.getInteger("celtium.vulkan.meshTerrainStage2.quadsPerMeshWorkgroup", 42).intValue());

    /** Bytes in the stage's push block (the terrain generation family's 144-byte form). */
    public static final int PUSH_BYTES = 144;

    /**
     * The clamp on task workgroups per draw: the measured maxTaskWorkGroupTotalCount on
     * the RADV fixture (mesh-shader-652 section 1.1). Section counts in the target
     * scenes are orders of magnitude below this; the clamp is a safety net, and a frame
     * that reaches it falls back rather than truncating.
     */
    private static final int MAX_TASK_WORKGROUPS = 4194304;

    private static final int PRINT_EVERY_FRAMES = 60;

    /** Why a frame did not take the stage-2 task path; every value is counted. */
    public enum GateReason {
        /** Recorded: one vkCmdDrawMeshTasksEXT for the pass. */
        NONE,
        /** The device did not report / could not enable the mesh+task feature pair. */
        NO_MESH_SHADER,
        /** A frame budget cap is active for this frame (design section 2.3). */
        BUDGET_ACTIVE,
        /** The caller has no stage-2 pipeline to bind yet. */
        NO_PIPELINE,
        /** The caller did not supply the E10 slot's table buffer. */
        TABLE_NOT_BOUND,
        /** The frame's table is not section-granular, so no directory exists. */
        DIRECTORY_NOT_BUILT,
        /** The directory holds a section the task shader must not emit for. */
        INELIGIBLE_SECTIONS,
        /** The directory is empty: nothing to emit, nothing recorded. */
        NO_SECTIONS,
        /** The section count exceeds the task workgroup ceiling. */
        OVER_TASK_WORKGROUP_LIMIT,
        /** A Vulkan failure latched the class disabled. */
        DISABLED
    }

    private final VulkanContext context;
    private long setLayout;
    private boolean disabled;
    private final Slot[] slots = new Slot[FrameSubmissionScheduler.FRAME_SLOTS];

    private final long[] reasonCounts = new long[GateReason.values().length];
    private long recordedFrames;
    private long recordedSections;

    private static final class Slot {
        VulkanHostBuffer directory;
        VulkanHostBuffer remap;
        VulkanHostBuffer mask;
        long descriptorPool;
        long descriptorSet;
        long describedDirectory;
        long describedTable;
        long describedRemap;
        long describedMask;
    }

    /** The standing gate: the stage-2 flag on AND the stage-0 capability pair enabled. */
    public static boolean available(VulkanContext context) {
        return ENABLED && context.meshShaderEnabled();
    }

    public VulkanMeshTaskDraw(VulkanContext context) {
        this.context = context;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDevice device = context.device();
            LongBuffer handle = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(4, stack);
            for (int i = 0; i < 4; i++) {
                // Binding 1 (the draw table) is also read by the mesh stage - it resolves
                // the payload's entry range there (stage 3, terrain_section.mesh) - so its
                // flags name both stages; the other three are the task stage's alone.
                int stageFlags = EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT
                        | (i == 1 ? EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT : 0);
                bindings.get(i).binding(i).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(stageFlags);
            }
            VkDescriptorSetLayoutCreateInfo setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default().pBindings(bindings);
            check(vkCreateDescriptorSetLayout(device, setInfo, null, handle),
                    "create mesh stage-2 set layout");
            this.setLayout = handle.get(0);
        }
        for (int i = 0; i < this.slots.length; i++) {
            this.slots[i] = new Slot();
        }
        System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_STAGE2_GATE enabled=" + ENABLED
                + " meshShader=" + context.meshShaderEnabled()
                + " quadsPerMeshWorkgroup=" + QUADS_PER_MESH_WORKGROUP
                + " mode=input-side (item 677 stage 2: directory + task shader + one draw call)");
    }

    /**
     * The set-0 layout the stage-2 pipeline's layout must lead with: binding 0 directory,
     * 1 draw table, 2 remap, 3 mask, all storage buffers read by the task stage.
     */
    public long taskSetLayout() {
        return this.setLayout;
    }

    public boolean disabled() {
        return this.disabled;
    }

    /**
     * Records the stage-2 task draw for one pass of one frame into {@code commandBuffer}.
     * Seat note (stage 3): the draw is a graphics-pipeline draw, so the recording must be
     * inside the pass' render pass - {@link VulkanMeshStage2Pipeline#recordPassHead},
     * the stage-3 caller, records it there; the "pass head" is the head of the pass'
     * in-render-pass recording, not a pre-pass seat. Uploads this frame's directory, remap and mask into
     * the slot first; the table is bound from {@code tableBuffer}, the E10 slot's handle,
     * whose contents the caller has already uploaded for the same frame.
     *
     * @param pipeline       the stage-2 task+mesh pipeline, or 0 when none exists yet
     * @param pipelineLayout a layout leading with {@link #taskSetLayout()} and a
     *                       {@link #PUSH_BYTES} push range at the task stage
     * @param tableEntryBase index of this pass's first entry within the table buffer
     * @param budgetActive   whether a terrain frame budget cap is in force this frame
     * @return the first gate that applied, or {@link GateReason#NONE} when recorded
     */
    public GateReason tryRecord(MemoryStack stack, VkCommandBuffer commandBuffer, int slotIndex,
                                long pipeline, long pipelineLayout, long tableBuffer,
                                GpuTerrainDrawTable.Table table, float[] planes,
                                float cameraX, float cameraY, float cameraZ, float maxDistance,
                                int passOrdinal, int tableEntryBase, boolean budgetActive) {
        if (this.disabled) {
            return count(GateReason.DISABLED);
        }
        if (!this.context.meshShaderEnabled()) {
            return count(GateReason.NO_MESH_SHADER);
        }
        if (budgetActive) {
            return count(GateReason.BUDGET_ACTIVE);
        }
        if (pipeline == VK_NULL_HANDLE || pipelineLayout == VK_NULL_HANDLE) {
            return count(GateReason.NO_PIPELINE);
        }
        if (tableBuffer == VK_NULL_HANDLE) {
            return count(GateReason.TABLE_NOT_BOUND);
        }
        SectionDirectory.Directory directory = SectionDirectory.build(table);
        if (directory == null) {
            return count(GateReason.DIRECTORY_NOT_BUILT);
        }
        if (directory.ineligibleSections > 0) {
            return count(GateReason.INELIGIBLE_SECTIONS);
        }
        int sectionCount = directory.sectionCount();
        if (sectionCount == 0) {
            return count(GateReason.NO_SECTIONS);
        }
        if (sectionCount > MAX_TASK_WORKGROUPS) {
            return count(GateReason.OVER_TASK_WORKGROUP_LIMIT);
        }
        try {
            Slot slot = this.slots[slotIndex];
            ensureBuffers(slot, directory, table);
            long directoryAddress = slot.directory.mappedAddress();
            ByteBuffer view = MemoryUtil.memByteBuffer(directoryAddress,
                    Math.max(1, directory.recordsBytes()));
            view.order(java.nio.ByteOrder.nativeOrder());
            directory.packRecords(view, 0);
            long remapAddress = slot.remap.mappedAddress();
            for (int i = 0; i < table.remap.length; i++) {
                MemoryUtil.memPutInt(remapAddress + (long) i * 4L, table.remap[i]);
            }
            long maskAddress = slot.mask.mappedAddress();
            for (int i = 0; i < table.maskWords.length; i++) {
                MemoryUtil.memPutInt(maskAddress + (long) i * 4L, table.maskWords[i]);
            }
            ensureDescriptors(stack, slot, tableBuffer);

            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack).sType$Default()
                    .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_HOST_BIT,
                    EXTMeshShader.VK_PIPELINE_STAGE_TASK_SHADER_BIT_EXT, 0, barrier, null, null);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0,
                    stack.longs(slot.descriptorSet), null);
            ByteBuffer push = stack.malloc(PUSH_BYTES);
            for (int i = 0; i < 24; i++) {
                push.putFloat(i * 4, planes[i]);
            }
            push.putFloat(96, cameraX);
            push.putFloat(100, cameraY);
            push.putFloat(104, cameraZ);
            push.putFloat(108, maxDistance);
            push.putInt(112, passOrdinal);
            push.putInt(116, sectionCount);
            push.putInt(120, QUADS_PER_MESH_WORKGROUP);
            push.putInt(124, 0); // remap base: the slot's remap buffer is dedicated
            push.putInt(128, tableEntryBase);
            push.putInt(132, 0); // mask base: the slot's mask buffer is dedicated
            push.putInt(136, 0);
            push.putInt(140, 0);
            push.clear();
            vkCmdPushConstants(commandBuffer, pipelineLayout,
                    EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT, 0, push);
            EXTMeshShader.vkCmdDrawMeshTasksEXT(commandBuffer, sectionCount, 1, 1);
        } catch (RuntimeException error) {
            this.disabled = true;
            System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_STAGE2_DISABLED at=tryRecord reason="
                    + error);
            return count(GateReason.DISABLED);
        }
        this.recordedFrames++;
        this.recordedSections += sectionCount;
        if (this.recordedFrames % PRINT_EVERY_FRAMES == 0) {
            System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_STAGE2 frames=" + this.recordedFrames
                    + " sections=" + this.recordedSections
                    + " fallbacks=[directoryNotBuilt=" + this.reasonCounts[GateReason.DIRECTORY_NOT_BUILT.ordinal()]
                    + " ineligibleSections=" + this.reasonCounts[GateReason.INELIGIBLE_SECTIONS.ordinal()]
                    + " budgetActive=" + this.reasonCounts[GateReason.BUDGET_ACTIVE.ordinal()]
                    + " noMeshShader=" + this.reasonCounts[GateReason.NO_MESH_SHADER.ordinal()]
                    + " overLimit=" + this.reasonCounts[GateReason.OVER_TASK_WORKGROUP_LIMIT.ordinal()]
                    + "]");
        }
        return count(GateReason.NONE);
    }

    private GateReason count(GateReason reason) {
        this.reasonCounts[reason.ordinal()]++;
        return reason;
    }

    private void ensureBuffers(Slot slot, SectionDirectory.Directory directory,
                               GpuTerrainDrawTable.Table table) {
        int usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        long directoryBytes = Math.max((long) SectionDirectory.RECORD_BYTES,
                (long) directory.recordsBytes());
        long remapBytes = Math.max(4L, (long) table.remap.length * 4L);
        long maskBytes = Math.max(4L, (long) table.maskWords.length * 4L);
        if (slot.directory == null) {
            slot.directory = new VulkanHostBuffer(this.context, directoryBytes, usage);
            slot.remap = new VulkanHostBuffer(this.context, remapBytes, usage);
            slot.mask = new VulkanHostBuffer(this.context, maskBytes, usage);
        } else {
            slot.directory.ensureCapacity(directoryBytes);
            slot.remap.ensureCapacity(remapBytes);
            slot.mask.ensureCapacity(maskBytes);
        }
    }

    /** (Re)writes the slot's descriptor set when any of the four handles has changed. */
    private void ensureDescriptors(MemoryStack stack, Slot slot, long tableBuffer) {
        if (slot.descriptorSet != 0L
                && slot.describedDirectory == slot.directory.buffer()
                && slot.describedTable == tableBuffer
                && slot.describedRemap == slot.remap.buffer()
                && slot.describedMask == slot.mask.buffer()) {
            return;
        }
        VkDevice device = this.context.device();
        LongBuffer handle = stack.mallocLong(1);
        if (slot.descriptorPool == 0L) {
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(4);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(1).pPoolSizes(poolSize);
            check(vkCreateDescriptorPool(device, poolInfo, null, handle),
                    "create mesh stage-2 descriptor pool");
            slot.descriptorPool = handle.get(0);
            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(slot.descriptorPool).pSetLayouts(stack.longs(this.setLayout));
            check(vkAllocateDescriptorSets(device, setInfo, handle),
                    "allocate mesh stage-2 descriptor set");
            slot.descriptorSet = handle.get(0);
        }
        long[] buffers = {slot.directory.buffer(), tableBuffer, slot.remap.buffer(), slot.mask.buffer()};
        VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(4, stack);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(4, stack);
        for (int i = 0; i < 4; i++) {
            infos.get(i).buffer(buffers[i]).offset(0).range(VK_WHOLE_SIZE);
            writes.get(i).sType$Default().dstSet(slot.descriptorSet).dstBinding(i).descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).pBufferInfo(infos.slice(i, 1));
        }
        vkUpdateDescriptorSets(device, writes, null);
        slot.describedDirectory = slot.directory.buffer();
        slot.describedTable = tableBuffer;
        slot.describedRemap = slot.remap.buffer();
        slot.describedMask = slot.mask.buffer();
    }

    /** Destroys every stage-2 resource. Called from the backend's own teardown. */
    public void delete() {
        VkDevice device = this.context.device();
        for (Slot slot : this.slots) {
            if (slot.descriptorPool != 0L) {
                vkDestroyDescriptorPool(device, slot.descriptorPool, null);
            }
            if (slot.directory != null) slot.directory.delete();
            if (slot.remap != null) slot.remap.delete();
            if (slot.mask != null) slot.mask.delete();
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
