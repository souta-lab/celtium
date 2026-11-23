package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;

/**
 * Item 677, stage 3: the pipeline factory and recording orchestrator that turn stage 2's
 * input side ({@link VulkanMeshTaskDraw}, {@link SectionDirectory},
 * {@code terrain_section.task}) into an actual issuance path.
 * <p>
 * Stage 2 stopped at the caller: {@link VulkanMeshTaskDraw#tryRecord} records
 * {@code vkCmdDrawMeshTasksEXT} only when handed a stage-2 pipeline and a layout leading
 * with its task set, and nothing in the tree built one. This class builds it, per terrain
 * pass pipeline, as a three-set layout:
 * <ul>
 *   <li>set 0 — the task-private set (directory / table / remap / mask), layout owned by
 *   {@link VulkanMeshTaskDraw#taskSetLayout()};</li>
 *   <li>set 1 — the pass' own descriptor set, carried across the pipeline switch exactly
 *   as stage 1 carried set 0: the layout names the pass pipeline's own set-layout object
 *   ({@link VulkanTerrainPipeline#descriptorSetLayout()}), so the caller's existing pass
 *   set binds unchanged at index 1;</li>
 *   <li>set 2 — this class' mesh-input set: binding 0 the shared vertex arena, binding 1
 *   the index buffer the pass' entries read, binding 2 a per-slot uniform block holding
 *   the pass' two matrices. The matrices travel in a buffer, not push constants, because
 *   the pipeline's push block belongs to the task stage (its 144-byte culling block sits
 *   at offset 0; the layout's only push range is the task range).</li>
 * </ul>
 * The pipeline's stages are {@code terrain_section.task} + {@code terrain_section.mesh} +
 * the pass' own {@code terrain_solid.frag} (same specialization as stage 1's sibling);
 * the fixed-function state mirrors the pass pipeline's through the same
 * {@code celtium$mesh*} accessors stage 1 uses. There is no vertex input and no input
 * assembly, as a task/mesh pipeline has neither.
 * <p>
 * {@link #recordPassHead} is the whole issuance path in one call, in recording order:
 * create-or-fetch the pipeline, write the slot's matrix block, take the slot's next ring
 * set and write it, bind sets 1 and 2 under the stage-2 layout, then hand pipeline and
 * layout to {@link VulkanMeshTaskDraw#tryRecord}, which uploads directory/remap/mask,
 * binds set 0 and the pipeline, pushes the task block and records the single draw call.
 * (Binding sets 1/2 before the pipeline bind is deliberate: descriptor binds are layout
 * state, and the pipeline {@code tryRecord} binds names this same layout, so the binds
 * survive it.) Every refusal is the task draw's counted {@link
 * VulkanMeshTaskDraw.GateReason}: a pass this factory cannot serve — a non-COMPACT vertex
 * module, a broken pipeline, or a split-budget override the mesh shader's compile-time
 * budget cannot match — is handed to {@code tryRecord} with null handles so the refusal
 * lands in the same census as NO_PIPELINE instead of becoming a second, silent path.
 * <p>
 * Scope, deliberately narrow (stage 1's shape, one stage up): the SOLID pass only (the
 * pass pipeline whose vertex module is {@link SpirvShaders#SOLID_VERTEX}), and only when
 * {@link VulkanMeshTaskDraw#available(VulkanContext)} holds. With the stage-2 flag off or
 * a device without the mesh/task pair (HD 620 / UHD 630 are the standing examples), no
 * object of this class is ever created and the terrain path is byte-identical to today.
 * The one hook this class still needs from the backend — a per-frame section-granular
 * table plus the pass' arena buffers at the pass head — is the merge phase's wiring job
 * (the section-granular table itself is E10-b preparation work); everything downstream of
 * that hook is implemented here.
 */
public final class VulkanMeshStage2Pipeline {

    /**
     * The mesh shader's compile-time split budget ({@code QUADS_PER_WORKGROUP} in
     * {@code terrain_section.mesh}: 64 quads x 4 unique vertices = 256 vertices, 128
     * primitives). The task stage takes its budget from push, so a
     * {@code celtium.vulkan.meshTerrainStage2.quadsPerMeshWorkgroup} override would split
     * the section differently on the two stages; creation is refused while they disagree.
     */
    public static final int SHADER_QUADS_PER_WORKGROUP = 64;

    /** Bytes in the per-slot matrix block: the pass' projection and model-view matrices. */
    private static final int FRAME_BYTES = 128;
    private static final int MATRIX_FLOATS = 32;
    /** Ring sets per frame slot; one per stage-2 pass recording, never rewritten in-frame. */
    private static final int SETS_PER_SLOT = 32;

    private static boolean budgetMismatchReported;
    private static int failureReports;

    /** A created stage-2 sibling of one terrain pipeline: its layout and pipeline handle. */
    public static final class Stage2Pipeline {
        public final long layout;
        public final long pipeline;

        Stage2Pipeline(long layout, long pipeline) {
            this.layout = layout;
            this.pipeline = pipeline;
        }
    }

    private final VulkanContext context;
    private final VulkanMeshTaskDraw taskDraw;
    private long meshSetLayout;
    private long descriptorPool;
    private final VulkanHostBuffer[] frameBuffers =
            new VulkanHostBuffer[FrameSubmissionScheduler.FRAME_SLOTS];
    /** Ring sets as [slot][ring index]; allocated whole when the resources are created. */
    private final long[][] ringSets =
            new long[FrameSubmissionScheduler.FRAME_SLOTS][SETS_PER_SLOT];
    private final int[] ringCursor = new int[FrameSubmissionScheduler.FRAME_SLOTS];
    private final boolean[] slotExhausted = new boolean[FrameSubmissionScheduler.FRAME_SLOTS];
    private final Map<VulkanTerrainPipeline, Stage2Pipeline> pipelines =
            new IdentityHashMap<VulkanTerrainPipeline, Stage2Pipeline>();
    private final Set<VulkanTerrainPipeline> brokenPipelines =
            Collections.newSetFromMap(new IdentityHashMap<VulkanTerrainPipeline, Boolean>());
    private boolean resourcesCreated;
    private boolean released;

    /**
     * Creates the factory against the stage-2 task draw. All Vulkan objects are lazy
     * (first served pass), mirroring stage 1's discipline.
     */
    public VulkanMeshStage2Pipeline(VulkanContext context, VulkanMeshTaskDraw taskDraw) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        if (taskDraw == null) {
            throw new NullPointerException("taskDraw");
        }
        this.context = context;
        this.taskDraw = taskDraw;
        System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_STAGE2_PIPELINE factory=ready"
                + " shaderQuadsPerWorkgroup=" + SHADER_QUADS_PER_WORKGROUP
                + " taskQuadsPerWorkgroup=" + VulkanMeshTaskDraw.QUADS_PER_MESH_WORKGROUP);
    }

    /** The task draw this factory serves (its set-0 layout leads every layout built here). */
    public VulkanMeshTaskDraw taskDraw() {
        return this.taskDraw;
    }

    /**
     * Resets one frame slot's ring cursor. Called from the same slot opening stage 1 uses,
     * after the slot's fence completed, so no set the previous submission still reads is
     * rewritten.
     *
     * @param slot the frame slot whose recording is beginning
     */
    public void beginSlot(int slot) {
        if (slot < 0 || slot >= this.ringCursor.length) {
            return;
        }
        this.ringCursor[slot] = 0;
        this.slotExhausted[slot] = false;
    }

    /**
     * Records one pass of one frame through the stage-2 task+mesh pipeline, or counts the
     * refusal through the task draw's gate census. The command buffer must be recording
     * inside the pass' render pass (the draw is a graphics draw; the task draw's barrier
     * and binds are recorded by this call's tail).
     *
     * @param passDescriptorSet the pass' own descriptor set for this frame (bound at set 1)
     * @param vertexArena       the shared vertex arena the pass' entries read vertices from
     * @param indexBuffer       the index buffer the pass' entries read (region arena or the
     *                          shared quad index buffer, per the entries' index-source bit)
     * @param tableBuffer       the E10 slot's table buffer handle (bound into set 0 by the
     *                          task draw and read by the mesh stage through the same set)
     * @return the task draw's gate verdict: NONE when the draw call was recorded
     */
    public VulkanMeshTaskDraw.GateReason recordPassHead(MemoryStack stack,
            VkCommandBuffer commandBuffer, int slot, VulkanTerrainPipeline terrainPipeline,
            long passDescriptorSet, VulkanBuffer vertexArena, VulkanBuffer indexBuffer,
            long tableBuffer, GpuTerrainDrawTable.Table table, float[] planes,
            float cameraX, float cameraY, float cameraZ, float maxDistance,
            int passOrdinal, int tableEntryBase, boolean budgetActive) {
        return recordPassHead(stack, commandBuffer, slot, terrainPipeline, passDescriptorSet,
                vertexArena, indexBuffer, tableBuffer, table, null, 0L, planes, cameraX, cameraY,
                cameraZ, maxDistance, passOrdinal, tableEntryBase, budgetActive);
    }

    /**
     * Same as {@link #recordPassHead} with an optional pre-built {@link SectionDirectory.Directory}
     * and generation stamp forwarded to {@link VulkanMeshTaskDraw#tryRecord}.
     */
    public VulkanMeshTaskDraw.GateReason recordPassHead(MemoryStack stack,
            VkCommandBuffer commandBuffer, int slot, VulkanTerrainPipeline terrainPipeline,
            long passDescriptorSet, VulkanBuffer vertexArena, VulkanBuffer indexBuffer,
            long tableBuffer, GpuTerrainDrawTable.Table table,
            SectionDirectory.Directory prebuiltDirectory, long directoryGeneration, float[] planes,
            float cameraX, float cameraY, float cameraZ, float maxDistance,
            int passOrdinal, int tableEntryBase, boolean budgetActive) {
        Stage2Pipeline stage2 = null;
        if (!this.released && terrainPipeline != null && slot >= 0 && slot < this.ringCursor.length
                && !this.brokenPipelines.contains(terrainPipeline)
                && SpirvShaders.SOLID_VERTEX.equals(terrainPipeline.vertexModuleName())
                && VulkanMeshTaskDraw.QUADS_PER_MESH_WORKGROUP == SHADER_QUADS_PER_WORKGROUP) {
            try {
                if (ensureResources(stack)) {
                    stage2 = this.pipelines.get(terrainPipeline);
                    if (stage2 == null) {
                        stage2 = createStage2Pipeline(stack, terrainPipeline);
                        this.pipelines.put(terrainPipeline, stage2);
                    }
                }
            } catch (RuntimeException failure) {
                this.brokenPipelines.add(terrainPipeline);
                reportFailure("stage-2 pipeline creation", failure);
                stage2 = null;
            }
        } else if (VulkanMeshTaskDraw.QUADS_PER_MESH_WORKGROUP != SHADER_QUADS_PER_WORKGROUP
                && !budgetMismatchReported) {
            budgetMismatchReported = true;
            System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_STAGE2_BUDGET_MISMATCH"
                    + " task=" + VulkanMeshTaskDraw.QUADS_PER_MESH_WORKGROUP
                    + " shader=" + SHADER_QUADS_PER_WORKGROUP
                    + " (the mesh shader's split budget is compile-time in this form;"
                    + " the property override is refused, not mismatched)");
        }
        if (stage2 == null || this.slotExhausted[slot]
                || this.ringCursor[slot] >= SETS_PER_SLOT) {
            if (stage2 != null) {
                this.slotExhausted[slot] = true;
            }
            // Null handles: the task draw counts NO_PIPELINE in its own census, so every
            // refusal of this path is visible in one place and nothing is recorded.
            return this.taskDraw.tryRecord(stack, commandBuffer, slot,
                    VK_NULL_HANDLE, VK_NULL_HANDLE, tableBuffer, table, prebuiltDirectory,
                    directoryGeneration, planes, cameraX, cameraY, cameraZ, maxDistance,
                    passOrdinal, tableEntryBase, budgetActive);
        }
        // The slot's matrix block: the pass pipeline's last pushed matrices, in the vertex
        // stage's order (projection first, then model-view), read by the mesh stage from
        // set 2 binding 2.
        VulkanHostBuffer frameBuffer = this.frameBuffers[slot];
        long frameAddress = frameBuffer.mappedAddress();
        float[] matrices = terrainPipeline.lastPushBlock();
        for (int i = 0; i < MATRIX_FLOATS; i++) {
            MemoryUtil.memPutFloat(frameAddress + (long) i * 4L, matrices[i]);
        }
        long meshSet = this.ringSets[slot][this.ringCursor[slot]];
        writeMeshSet(stack, meshSet, vertexArena, indexBuffer, frameBuffer);
        // Sets 1 and 2 first, under the stage-2 layout; tryRecord then binds the pipeline
        // (same layout), set 0, the task push block, and records the draw.
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, stage2.layout, 1,
                stack.longs(passDescriptorSet, meshSet), null);
        this.ringCursor[slot]++;
        VulkanMeshTaskDraw.GateReason verdict = this.taskDraw.tryRecord(stack, commandBuffer, slot,
                stage2.pipeline, stage2.layout, tableBuffer, table, prebuiltDirectory,
                directoryGeneration, planes, cameraX, cameraY, cameraZ, maxDistance,
                passOrdinal, tableEntryBase, budgetActive);
        if (verdict == VulkanMeshTaskDraw.GateReason.NONE) {
            // Restore the pass' own terrain pipeline so downstream pass-tail recording sees
            // the same bound pipeline state as stage 1 and the non-mesh path.
            terrainPipeline.bindPipeline(commandBuffer);
        }
        return verdict;
    }

    /** Creates the standing resources: the set-2 layout, the pool and rings, the matrix blocks. */
    private boolean ensureResources(MemoryStack stack) {
        if (this.resourcesCreated) {
            return true;
        }
        VkDevice device = this.context.device();
        org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer bindings =
                org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(3, stack);
        int meshStage = org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT;
        bindings.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1).stageFlags(meshStage);
        bindings.get(1).binding(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1).stageFlags(meshStage);
        bindings.get(2).binding(2).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1).stageFlags(meshStage);
        org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutCreate =
                org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                        .pBindings(bindings);
        LongBuffer handle = stack.mallocLong(1);
        check(org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout(device, layoutCreate, null, handle),
                "vkCreateDescriptorSetLayout(mesh stage-2)");
        this.meshSetLayout = handle.get(0);
        int slots = this.frameBuffers.length;
        int totalSets = slots * SETS_PER_SLOT;
        org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer sizes =
                org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(2, stack);
        sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(totalSets * 2);
        sizes.get(1).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(totalSets);
        org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolCreate =
                org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                        .maxSets(totalSets)
                        .pPoolSizes(sizes);
        check(org.lwjgl.vulkan.VK10.vkCreateDescriptorPool(device, poolCreate, null, handle),
                "vkCreateDescriptorPool(mesh stage-2)");
        this.descriptorPool = handle.get(0);
        LongBuffer layouts = stack.mallocLong(totalSets);
        for (int i = 0; i < totalSets; i++) {
            layouts.put(i, this.meshSetLayout);
        }
        LongBuffer sets = stack.mallocLong(totalSets);
        org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                        .descriptorPool(this.descriptorPool)
                        .pSetLayouts(layouts);
        check(org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets(device, allocate, sets),
                "vkAllocateDescriptorSets(mesh stage-2)");
        for (int slot = 0; slot < slots; slot++) {
            for (int ring = 0; ring < SETS_PER_SLOT; ring++) {
                this.ringSets[slot][ring] = sets.get(slot * SETS_PER_SLOT + ring);
            }
            this.frameBuffers[slot] = new VulkanHostBuffer(this.context, FRAME_BYTES,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
        }
        this.resourcesCreated = true;
        return true;
    }

    /** Writes one ring set: the pass' arena, its index buffer and the slot's matrix block. */
    private void writeMeshSet(MemoryStack stack, long set, VulkanBuffer vertexArena,
            VulkanBuffer indexBuffer, VulkanHostBuffer frameBuffer) {
        org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer infos =
                org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(3, stack);
        infos.get(0).buffer(vertexArena.buffer()).offset(0L).range(vertexArena.size());
        infos.get(1).buffer(indexBuffer.buffer()).offset(0L).range(indexBuffer.size());
        infos.get(2).buffer(frameBuffer.buffer()).offset(0L).range(frameBuffer.size());
        org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer writes =
                org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(3, stack);
        int[] types = {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER};
        for (int i = 0; i < 3; i++) {
            writes.get(i).sType$Default()
                    .dstSet(set)
                    .dstBinding(i)
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .descriptorType(types[i])
                    .pBufferInfo(infos.slice(i, 1));
        }
        org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets(this.context.device(), writes, null);
    }

    /**
     * Creates one pass' stage-2 sibling: the three-set layout (task set, pass set, mesh
     * set) with the task stage's 144-byte push range, and a task+mesh+fragment graphics
     * pipeline whose fixed-function state mirrors the pass pipeline's creation, field for
     * field as stage 1's sibling mirrors it.
     */
    private Stage2Pipeline createStage2Pipeline(MemoryStack stack,
            VulkanTerrainPipeline terrain) {
        VkDevice device = this.context.device();
        long layout = VK_NULL_HANDLE;
        long pipeline = VK_NULL_HANDLE;
        long taskModule = 0L;
        long meshModule = 0L;
        long fragmentModule = 0L;
        ByteBuffer taskCode = null;
        ByteBuffer meshCode = null;
        ByteBuffer fragmentCode = null;
        try {
            org.lwjgl.vulkan.VkPushConstantRange.Buffer range =
                    org.lwjgl.vulkan.VkPushConstantRange.calloc(1, stack);
            range.get(0).stageFlags(org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT)
                    .offset(0).size(VulkanMeshTaskDraw.PUSH_BYTES);
            org.lwjgl.vulkan.VkPipelineLayoutCreateInfo layoutCreate =
                    org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                            .pSetLayouts(stack.longs(this.taskDraw.taskSetLayout(),
                                    terrain.descriptorSetLayout(), this.meshSetLayout))
                            .pPushConstantRanges(range);
            LongBuffer handle = stack.mallocLong(1);
            check(org.lwjgl.vulkan.VK10.vkCreatePipelineLayout(device, layoutCreate, null, handle),
                    "vkCreatePipelineLayout(mesh stage-2)");
            layout = handle.get(0);

            taskCode = SpirvShaders.load(SpirvShaders.TERRAIN_SECTION_TASK);
            meshCode = SpirvShaders.load(SpirvShaders.TERRAIN_SECTION_MESH);
            fragmentCode = SpirvShaders.load(SpirvShaders.SOLID_FRAGMENT);
            taskModule = createShaderModule(stack, device, taskCode, "task");
            meshModule = createShaderModule(stack, device, meshCode, "mesh");
            fragmentModule = createShaderModule(stack, device, fragmentCode, "fragment");
            org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.Buffer stages =
                    org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.calloc(3, stack);
            stages.get(0).sType$Default()
                    .stage(org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT)
                    .module(taskModule)
                    .pName(stack.UTF8("main"));
            stages.get(1).sType$Default()
                    .stage(org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT)
                    .module(meshModule)
                    .pName(stack.UTF8("main"));
            org.lwjgl.vulkan.VkSpecializationMapEntry.Buffer specEntries =
                    org.lwjgl.vulkan.VkSpecializationMapEntry.calloc(1, stack);
            specEntries.get(0).constantID(0).offset(0).size(4);
            org.lwjgl.vulkan.VkSpecializationInfo specialization =
                    org.lwjgl.vulkan.VkSpecializationInfo.calloc(stack)
                            .pMapEntries(specEntries)
                            .pData(stack.malloc(4).putInt(0, terrain.celtium$meshFragmentDiscard() ? 1 : 0));
            stages.get(2).sType$Default()
                    .stage(org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragmentModule)
                    .pName(stack.UTF8("main"))
                    .pSpecializationInfo(specialization);

            org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo viewport =
                    org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo.calloc(stack)
                            .sType$Default().viewportCount(1).scissorCount(1);
            boolean culling = terrain.celtium$meshBlockFaceCulling();
            org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo rasterization =
                    org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthClampEnable(false)
                            .rasterizerDiscardEnable(false)
                            .polygonMode(org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL)
                            .frontFace(terrain.celtium$meshFrontFace())
                            .cullMode(culling
                                    ? org.lwjgl.vulkan.VK10.VK_CULL_MODE_BACK_BIT
                                    : org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE)
                            .depthBiasEnable(false)
                            .lineWidth(1.0f);
            org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo multisample =
                    org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .rasterizationSamples(org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT)
                            .sampleShadingEnable(false)
                            .alphaToCoverageEnable(false)
                            .alphaToOneEnable(false);
            boolean depth = terrain.celtium$meshDepthTestEnabled();
            boolean blended = terrain.celtium$meshBlended();
            org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo depthStencil =
                    org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(depth)
                            .depthWriteEnable(depth && !blended)
                            .depthCompareOp(org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL)
                            .depthBoundsTestEnable(false)
                            .stencilTestEnable(false)
                            .minDepthBounds(0.0f)
                            .maxDepthBounds(1.0f);
            org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                    org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachment.get(0)
                    .colorWriteMask(org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT
                            | org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT
                            | org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT
                            | org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT);
            if (blended) {
                blendAttachment.get(0)
                        .blendEnable(true)
                        .srcColorBlendFactor(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA)
                        .dstColorBlendFactor(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .colorBlendOp(org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .alphaBlendOp(org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD);
            } else {
                blendAttachment.get(0)
                        .blendEnable(false)
                        .srcColorBlendFactor(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE)
                        .dstColorBlendFactor(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO)
                        .colorBlendOp(org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO)
                        .alphaBlendOp(org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD);
            }
            org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo blend =
                    org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .logicOpEnable(false)
                            .pAttachments(blendAttachment);
            org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamic =
                    org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pDynamicStates(stack.ints(org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_VIEWPORT,
                                    org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_SCISSOR));

            org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer create =
                    org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default()
                    .pStages(stages)
                    .pViewportState(viewport)
                    .pRasterizationState(rasterization)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(blend)
                    .pDynamicState(dynamic)
                    .layout(layout)
                    .renderPass(terrain.renderPass())
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);
            check(VulkanContext.createGraphicsPipelines(device, create, handle, "terrain-mesh-stage2"),
                    "vkCreateGraphicsPipelines(mesh stage-2)");
            pipeline = handle.get(0);
            return new Stage2Pipeline(layout, pipeline);
        } catch (RuntimeException failure) {
            if (pipeline != VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyPipeline(device, pipeline, null);
            }
            if (layout != VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout(device, layout, null);
            }
            throw failure;
        } finally {
            if (fragmentModule != 0L) {
                org.lwjgl.vulkan.VK10.vkDestroyShaderModule(device, fragmentModule, null);
            }
            if (meshModule != 0L) {
                org.lwjgl.vulkan.VK10.vkDestroyShaderModule(device, meshModule, null);
            }
            if (taskModule != 0L) {
                org.lwjgl.vulkan.VK10.vkDestroyShaderModule(device, taskModule, null);
            }
            if (fragmentCode != null) {
                MemoryUtil.memFree(fragmentCode);
            }
            if (meshCode != null) {
                MemoryUtil.memFree(meshCode);
            }
            if (taskCode != null) {
                MemoryUtil.memFree(taskCode);
            }
        }
    }

    private static long createShaderModule(MemoryStack stack, VkDevice device, ByteBuffer code,
            String stage) {
        if (code == null || !code.isDirect() || code.remaining() < 4) {
            throw new IllegalStateException("SpirvShaders.load returned no direct SPIR-V byte code"
                    + " for the " + stage + " stage of the stage-2 mesh terrain shader");
        }
        org.lwjgl.vulkan.VkShaderModuleCreateInfo create =
                org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
        LongBuffer handle = stack.mallocLong(1);
        check(org.lwjgl.vulkan.VK10.vkCreateShaderModule(device, create, null, handle),
                "vkCreateShaderModule(" + stage + ")");
        return handle.get(0);
    }

    /** Destroys every object this factory created. Called with the terrain pipelines' release. */
    public void release() {
        if (this.released) {
            return;
        }
        this.released = true;
        VkDevice device = this.context.device();
        for (Stage2Pipeline stage2 : this.pipelines.values()) {
            if (stage2.pipeline != VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyPipeline(device, stage2.pipeline, null);
            }
            if (stage2.layout != VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout(device, stage2.layout, null);
            }
        }
        this.pipelines.clear();
        if (this.descriptorPool != 0L) {
            org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool(device, this.descriptorPool, null);
            this.descriptorPool = 0L;
        }
        if (this.meshSetLayout != 0L) {
            org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout(device, this.meshSetLayout, null);
            this.meshSetLayout = 0L;
        }
        for (int slot = 0; slot < this.frameBuffers.length; slot++) {
            if (this.frameBuffers[slot] != null) {
                this.frameBuffers[slot].delete();
                this.frameBuffers[slot] = null;
            }
        }
    }

    private static void reportFailure(String what, RuntimeException failure) {
        if (failureReports >= 4) {
            return;
        }
        failureReports++;
        System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_STAGE2_PIPELINE failure " + what + ": " + failure);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }
}
