package org.soutalab.celtium.vulkan;

/**
 * The device limits half of the mesh-shader capability (item 652): an immutable,
 * Vulkan-free snapshot of the {@code VkPhysicalDeviceMeshShaderPropertiesEXT} values
 * the two mesh terrain paths actually depend on, plus the pure judgements those paths
 * gate on. {@link VulkanContext} fills one instance at device creation (only when the
 * extension is advertised; otherwise {@link #ABSENT}); the paths never read a Vulkan
 * struct themselves, so every judgement here is unit-testable without a device.
 * <p>
 * Why this exists (alpha-mesh-next2): before this class, no code anywhere queried the
 * mesh properties struct. Stage 2 clamped its draw against a hard-coded
 * 4,194,304 task workgroups ("the measured value on the RADV fixture",
 * mesh-shader-652 section 1.1) and assumed the stage-2 mesh shader's compiled output
 * size (256 vertices / 128 primitives) fit the device; stage 1 passed its quad count
 * straight into {@code vkCmdDrawMeshTasksEXT}'s x dimension with no limit check at
 * all. On the one measured device (RX 6400 / RADV) those assumptions hold, but a
 * device reporting the feature pair with smaller limits in any of the terms below
 * (workgroup counts, invocations, payload, mesh output) would have taken an invalid
 * dispatch instead of the indirect fallback. The feature pair alone is therefore
 * not the capability; the pair AND these limits are.
 */
public final class MeshCapability {

    /** Stage 2's task workgroup size ({@code layout(local_size_x = 32)} in terrain_section.task). */
    public static final int STAGE2_TASK_WORKGROUP_SIZE = 32;
    /** Stage 2's task payload size in bytes (the 644-byte CeltiumSectionTaskPayload). */
    public static final int STAGE2_TASK_PAYLOAD_BYTES = 644;
    /** Stage 2 mesh shader's compiled output: {@code max_vertices = 256} in terrain_section.mesh. */
    public static final int STAGE2_MESH_OUTPUT_VERTICES = 256;
    /** Stage 2 mesh shader's compiled output: {@code max_primitives = 128} in terrain_section.mesh. */
    public static final int STAGE2_MESH_OUTPUT_PRIMITIVES = 128;
    /** Stage 2 mesh shader's invocation count ({@code layout(local_size_x = 64)}). */
    public static final int STAGE2_MESH_WORKGROUP_INVOCATIONS = 64;
    /** Stage 1 mesh shader's compiled output: {@code max_vertices = 6} in terrain_solid.mesh. */
    public static final int STAGE1_MESH_OUTPUT_VERTICES = 6;
    /** Stage 1 mesh shader's compiled output: {@code max_primitives = 2} in terrain_solid.mesh. */
    public static final int STAGE1_MESH_OUTPUT_PRIMITIVES = 2;

    /** No properties were queried (extension not advertised): every limit reads 0. */
    public static final MeshCapability ABSENT = new MeshCapability(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    /** maxTaskWorkGroupTotalCount: total task workgroups across all dimensions of one draw. */
    public final int maxTaskWorkGroupTotalCount;
    /** maxTaskWorkGroupCount[0]: task workgroups in the x dimension of one draw. */
    public final int maxTaskWorkGroupCountX;
    /** maxTaskWorkGroupInvocations: invocations in one task workgroup. */
    public final int maxTaskWorkGroupInvocations;
    /** maxTaskWorkGroupSize[0]: x size of one task workgroup. */
    public final int maxTaskWorkGroupSizeX;
    /** maxTaskPayloadSize: bytes of task payload one task workgroup may publish. */
    public final int maxTaskPayloadSize;
    /** maxMeshWorkGroupTotalCount: total mesh workgroups a task draw may amplify to. */
    public final int maxMeshWorkGroupTotalCount;
    /** maxMeshWorkGroupCount[0]: mesh workgroups in the x dimension of one direct draw. */
    public final int maxMeshWorkGroupCountX;
    /** maxMeshWorkGroupInvocations: invocations in one mesh workgroup. */
    public final int maxMeshWorkGroupInvocations;
    /** maxMeshOutputVertices: vertices one mesh workgroup may emit. */
    public final int maxMeshOutputVertices;
    /** maxMeshOutputPrimitives: primitives one mesh workgroup may emit. */
    public final int maxMeshOutputPrimitives;

    public MeshCapability(int maxTaskWorkGroupTotalCount, int maxTaskWorkGroupCountX,
                          int maxTaskWorkGroupInvocations, int maxTaskWorkGroupSizeX,
                          int maxTaskPayloadSize, int maxMeshWorkGroupTotalCount,
                          int maxMeshWorkGroupCountX, int maxMeshWorkGroupInvocations,
                          int maxMeshOutputVertices, int maxMeshOutputPrimitives) {
        this.maxTaskWorkGroupTotalCount = maxTaskWorkGroupTotalCount;
        this.maxTaskWorkGroupCountX = maxTaskWorkGroupCountX;
        this.maxTaskWorkGroupInvocations = maxTaskWorkGroupInvocations;
        this.maxTaskWorkGroupSizeX = maxTaskWorkGroupSizeX;
        this.maxTaskPayloadSize = maxTaskPayloadSize;
        this.maxMeshWorkGroupTotalCount = maxMeshWorkGroupTotalCount;
        this.maxMeshWorkGroupCountX = maxMeshWorkGroupCountX;
        this.maxMeshWorkGroupInvocations = maxMeshWorkGroupInvocations;
        this.maxMeshOutputVertices = maxMeshOutputVertices;
        this.maxMeshOutputPrimitives = maxMeshOutputPrimitives;
    }

    /** {@return whether any limit was actually read (all-zero means the query never ran)}. */
    public boolean queried() {
        return this.maxTaskWorkGroupTotalCount > 0 && this.maxMeshWorkGroupTotalCount > 0;
    }

    /**
     * {@return whether this device can run stage 2's compiled shaders at all}: the task
     * workgroup (32 lanes, 644-byte payload) and the mesh workgroup (64 invocations,
     * 256-vertex / 128-primitive output) must each fit inside the reported limits. A
     * device failing any term keeps the indirect path; the check is deliberately about
     * the shaders as compiled, not about a frame's size — per-frame counts are
     * {@link #stage2TaskGroupsFit(int)} and the task shader's own amplification is
     * bounded by the directory it reads.
     */
    public boolean stage2Capable() {
        return queried()
                && this.maxTaskWorkGroupInvocations >= STAGE2_TASK_WORKGROUP_SIZE
                && this.maxTaskWorkGroupSizeX >= STAGE2_TASK_WORKGROUP_SIZE
                && this.maxTaskPayloadSize >= STAGE2_TASK_PAYLOAD_BYTES
                && this.maxMeshWorkGroupInvocations >= STAGE2_MESH_WORKGROUP_INVOCATIONS
                && this.maxMeshOutputVertices >= STAGE2_MESH_OUTPUT_VERTICES
                && this.maxMeshOutputPrimitives >= STAGE2_MESH_OUTPUT_PRIMITIVES;
    }

    /**
     * {@return whether one stage-2 draw of {@code taskGroups} task workgroups is inside
     * both task limits} (the per-dimension x cap and the total cap). Replaces the old
     * hard-coded 4,194,304 clamp, which was one fixture's measured total presented as a
     * universal constant.
     */
    public boolean stage2TaskGroupsFit(int taskGroups) {
        return taskGroups > 0
                && taskGroups <= this.maxTaskWorkGroupCountX
                && taskGroups <= this.maxTaskWorkGroupTotalCount;
    }

    /**
     * {@return whether this device can run stage 1's compiled mesh shader} (6 vertices /
     * 2 primitives out, 1 invocation): every mesh-capable device in the item-652 table
     * passes, but the answer is still read from the device, not assumed.
     */
    public boolean stage1Capable() {
        return queried()
                && this.maxMeshOutputVertices >= STAGE1_MESH_OUTPUT_VERTICES
                && this.maxMeshOutputPrimitives >= STAGE1_MESH_OUTPUT_PRIMITIVES
                && this.maxMeshWorkGroupTotalCount > 0;
    }

    /**
     * {@return whether one stage-1 direct draw of {@code quads} mesh workgroups (one per
     * quad, all in the x dimension) is inside both mesh limits}. Stage 1's caller falls
     * back to the indirect chain for the batch when this is false — the pre-existing
     * refusal form — instead of issuing a dispatch whose group counts exceed the
     * device limits (undefined behaviour per the vkCmdDrawMeshTasksEXT valid-usage
     * rules for groupCountX and the total amplified count).
     */
    public boolean stage1DispatchFits(long quads) {
        return quads > 0L
                && quads <= (long) this.maxMeshWorkGroupCountX
                && quads <= (long) this.maxMeshWorkGroupTotalCount;
    }

    @Override
    public String toString() {
        return "taskTotal=" + this.maxTaskWorkGroupTotalCount
                + " taskCountX=" + this.maxTaskWorkGroupCountX
                + " taskInvocations=" + this.maxTaskWorkGroupInvocations
                + " taskSizeX=" + this.maxTaskWorkGroupSizeX
                + " taskPayload=" + this.maxTaskPayloadSize
                + " meshTotal=" + this.maxMeshWorkGroupTotalCount
                + " meshCountX=" + this.maxMeshWorkGroupCountX
                + " meshInvocations=" + this.maxMeshWorkGroupInvocations
                + " meshOutVertices=" + this.maxMeshOutputVertices
                + " meshOutPrimitives=" + this.maxMeshOutputPrimitives;
    }
}
