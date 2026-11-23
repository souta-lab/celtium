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
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;

/**
 * Item 652, stage 1: the proof form of the mesh-shader terrain path.
 * <p>
 * When armed ({@code -Dceltium.vulkan.meshTerrain}, default off) on a device created with
 * {@code VK_EXT_mesh_shader} enabled (stage 0, {@link VulkanContext#meshShaderEnabled()}), an
 * unsorted terrain batch that the indirect preparation accepted is drawn by a mesh-shader
 * pipeline instead of by {@code vkCmdDrawIndexedIndirect}: the batch's prepared draw records
 * are copied into this class' per-slot record buffer, and one
 * {@code vkCmdDrawMeshTasksEXT} dispatch re-issues them - one workgroup per quad, the mesh
 * shader ({@code terrain_solid.mesh}) reading the shared vertex arena and the batch's index
 * buffer as storage buffers and emitting the same triangles the indexed draw would read. The
 * fragment stage is the pass' own {@code terrain_solid.frag}; the fixed-function state mirrors
 * the pass pipeline's; set 0 is the pass' own set, carried across the pipeline switch because
 * this path's layout names the same set-0 layout object. The CPU still assembles and culls
 * exactly as before - nothing about the batch changes, only the issuing mechanism - which is
 * what makes this stage a proof of the pipeline rather than a new renderer (design section 3).
 * <p>
 * Scope, deliberately narrow (the design's stage-1 shape): the COMPACT vertex layout only
 * (the pass pipeline whose vertex module is {@link SpirvShaders#SOLID_VERTEX}), unsorted
 * passes only, single-instance records whose index counts are whole quads. Anything else
 * answers {@code false} and the caller draws the batch the ordinary way; a batch refused
 * after preparation costs nothing but the packet copy the preparation already made. With the
 * flag off, {@link #available(VulkanContext)} is false and no object of this class is ever
 * created, so the terrain record path is untouched.
 * <p>
 * Per-frame discipline mirrors the indirect path's: records live in one host buffer per
 * frame slot, appended at a cursor {@link #beginSlot(int)} resets when the slot's recording
 * begins (after its fence, so the previous submission has completed), and descriptor sets
 * come from a per-slot ring so a set is never rewritten inside the frame that uses it. A
 * frame that outruns either capacity falls back to the ordinary path for its remaining
 * batches rather than growing anything mid-frame.
 */
public final class VulkanMeshTerrain {

    /** Master switch for the proof path. Default off; with it off nothing here is allocated. */
    public static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.meshTerrain");

    /** One draw record is a {@code VkDrawIndexedIndirectCommand}: five 32-bit fields. */
    private static final int RECORD_BYTES = 20;
    /** Records one frame slot's buffer holds; a frame needing more falls back past the cap. */
    private static final int RECORD_CAPACITY = 16384;
    /** Descriptor sets in one slot's ring; one per mesh-drawn batch, never rewritten in-frame. */
    private static final int SETS_PER_SLOT = 64;
    /** The mesh push block: the vertex stage's 128 bytes of matrices plus 16 bytes of dispatch range. */
    private static final int PUSH_BYTES = 144;
    private static final int MATRIX_FLOATS = 32;

    private static boolean gateReported;
    private static int failureReports;

    /**
     * The standing gate, in the order a frame meets it: the flag, then the stage-0 capability.
     * The capability-off answer is reported once, so a leg that armed the flag on a device
     * without the extension can see the path was gated rather than silently absent.
     *
     * @param context the context the batch would record against
     * @return whether a batch may be offered to {@link #recordBatch}
     */
    public static boolean available(VulkanContext context) {
        if (!ENABLED) {
            return false;
        }
        if (context == null || !context.meshShaderEnabled()) {
            if (!gateReported) {
                gateReported = true;
                System.out.println("[Celtium] CELTIUM_MESH_TERRAIN gate=capability-off flag=on"
                        + " meshShaderEnabled=" + (context != null && context.meshShaderEnabled()));
            }
            return false;
        }
        return true;
    }

    /** A created mesh sibling of one terrain pipeline: its own layout and pipeline handle. */
    private static final class MeshPipeline {
        final long layout;
        final long pipeline;

        MeshPipeline(long layout, long pipeline) {
            this.layout = layout;
            this.pipeline = pipeline;
        }
    }

    private final VulkanContext context;
    private long setLayout;
    private long descriptorPool;
    private final VulkanHostBuffer[] recordBuffers =
            new VulkanHostBuffer[FrameSubmissionScheduler.FRAME_SLOTS];
    /** Ring sets as [slot][ring index]; allocated whole when the resources are created. */
    private final long[][] ringSets =
            new long[FrameSubmissionScheduler.FRAME_SLOTS][SETS_PER_SLOT];
    private final int[] recordCursor = new int[FrameSubmissionScheduler.FRAME_SLOTS];
    private final int[] ringCursor = new int[FrameSubmissionScheduler.FRAME_SLOTS];
    private final boolean[] slotExhausted = new boolean[FrameSubmissionScheduler.FRAME_SLOTS];
    private final Map<VulkanTerrainPipeline, MeshPipeline> pipelines =
            new IdentityHashMap<VulkanTerrainPipeline, MeshPipeline>();
    private final Set<VulkanTerrainPipeline> brokenPipelines =
            Collections.newSetFromMap(new IdentityHashMap<VulkanTerrainPipeline, Boolean>());
    private boolean resourcesCreated;
    private boolean released;
    private boolean firstUseReported;
    private long meshBatches;
    private long meshDraws;
    private long meshQuads;

    /** Creates the path against one context; all Vulkan objects are lazy (first mesh batch). */
    public VulkanMeshTerrain(VulkanContext context) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        this.context = context;
    }

    /**
     * Resets one frame slot's cursors. Called from the indirect path's own slot opening,
     * which runs after the slot's fence completed and its command buffer was reset - the
     * point at which the slot's previous submission is known to be done with these buffers.
     *
     * @param slot the frame slot whose recording is beginning
     */
    public void beginSlot(int slot) {
        if (slot < 0 || slot >= this.recordCursor.length) {
            return;
        }
        this.recordCursor[slot] = 0;
        this.ringCursor[slot] = 0;
        this.slotExhausted[slot] = false;
    }

    /** {@return how many batches this path has drawn}. */
    public long meshBatches() {
        return this.meshBatches;
    }

    /** {@return how many draw records this path has carried}. */
    public long meshDraws() {
        return this.meshDraws;
    }

    /** {@return how many quads this path has dispatched}. */
    public long meshQuads() {
        return this.meshQuads;
    }

    /**
     * Records one prepared batch through the mesh pipeline, or refuses it.
     * <p>
     * The caller (the indirect chain's offer) has already flushed its open run, so the pass'
     * recorded order is preserved: this batch's triangles are recorded here, in place, and
     * the caller records nothing more for it. The terrain pipeline is re-bound before
     * returning, so the caller's belief about the bound pipeline - which the direct draws
     * and later chain flushes rely on - is exactly what it was on entry.
     *
     * @param stack           the recording stack
     * @param commandBuffer   the command buffer being recorded, inside the pass' render pass
     * @param terrainPipeline the pass' pipeline (its set 0, render pass and state are mirrored)
     * @param vertexArena     the shared vertex arena buffer the batch's vertices live in
     * @param indexBuffer     the index buffer the preparation decided this batch reads
     * @param recordsAddress  host address of the batch's prepared records (the indirect page)
     * @param recordCount     how many records the batch carries
     * @param slot            the frame slot being recorded
     * @return true when the batch was drawn here; false when the caller must draw it ordinarily
     */
    public boolean recordBatch(MemoryStack stack, VkCommandBuffer commandBuffer,
            VulkanTerrainPipeline terrainPipeline, VulkanBuffer vertexArena,
            VulkanBuffer indexBuffer, long recordsAddress, int recordCount, int slot) {
        if (this.released || terrainPipeline == null || vertexArena == null || indexBuffer == null
                || recordCount <= 0 || slot < 0 || slot >= this.recordCursor.length
                || this.brokenPipelines.contains(terrainPipeline)) {
            return false;
        }
        // Eligibility, beyond the caller's unsorted gate: the mesh shader unpacks the COMPACT
        // vertex, so only a pass whose vertex module is that layout's own may take this path.
        if (!SpirvShaders.SOLID_VERTEX.equals(terrainPipeline.vertexModuleName())) {
            return false;
        }
        // The records must be whole single-instance quads: the shader emits six corners per
        // quad and has no instance dimension to spend a multi-instance record on.
        long quads = 0L;
        for (int i = 0; i < recordCount; i++) {
            long record = recordsAddress + (long) i * RECORD_BYTES;
            int indexCount = MemoryUtil.memGetInt(record);
            int instanceCount = MemoryUtil.memGetInt(record + 4);
            if (indexCount <= 0 || (indexCount % 6) != 0 || instanceCount != 1) {
                return false;
            }
            quads += indexCount / 6;
        }
        if (quads <= 0L || quads > Integer.MAX_VALUE) {
            return false;
        }
        if (this.slotExhausted[slot]
                || this.recordCursor[slot] + recordCount > RECORD_CAPACITY
                || this.ringCursor[slot] >= SETS_PER_SLOT) {
            this.slotExhausted[slot] = true;
            return false;
        }
        MeshPipeline mesh;
        try {
            if (!ensureResources(stack)) {
                return false;
            }
            mesh = this.pipelines.get(terrainPipeline);
            if (mesh == null) {
                mesh = createMeshPipeline(stack, terrainPipeline);
                this.pipelines.put(terrainPipeline, mesh);
            }
        } catch (RuntimeException failure) {
            this.brokenPipelines.add(terrainPipeline);
            reportFailure("mesh pipeline creation", failure);
            return false;
        }
        int firstRecord = this.recordCursor[slot];
        VulkanHostBuffer recordBuffer = this.recordBuffers[slot];
        MemoryUtil.memCopy(recordsAddress,
                recordBuffer.mappedAddress() + (long) firstRecord * RECORD_BYTES,
                (long) recordCount * RECORD_BYTES);
        long set = this.ringSets[slot][this.ringCursor[slot]];
        writeSet(stack, set, vertexArena, indexBuffer, recordBuffer);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, mesh.pipeline);
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, mesh.layout, 1,
                stack.longs(set), null);
        ByteBuffer push = stack.malloc(PUSH_BYTES);
        float[] matrices = terrainPipeline.lastPushBlock();
        for (int i = 0; i < MATRIX_FLOATS; i++) {
            push.putFloat(i * 4, matrices[i]);
        }
        push.putInt(128, firstRecord);
        push.putInt(132, recordCount);
        push.putInt(136, 0);
        push.putInt(140, 0);
        vkCmdPushConstants(commandBuffer, mesh.layout,
                org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT, 0, push);
        org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksEXT(commandBuffer, (int) quads, 1, 1);
        // Restore the pass' own pipeline: everything the caller records after this batch -
        // direct draws, chain flushes - assumes it is the bound one.
        terrainPipeline.bindPipeline(commandBuffer);
        this.recordCursor[slot] += recordCount;
        this.ringCursor[slot]++;
        this.meshBatches++;
        this.meshDraws += recordCount;
        this.meshQuads += quads;
        if (!this.firstUseReported) {
            this.firstUseReported = true;
            System.out.println("[Celtium] CELTIUM_MESH_TERRAIN active pass="
                    + terrainPipeline.vertexModuleName() + " batches=" + this.meshBatches
                    + " draws=" + this.meshDraws + " quads=" + this.meshQuads);
        }
        return true;
    }

    /** Creates the standing resources: the set-1 layout, the pool and rings, the record buffers. */
    private boolean ensureResources(MemoryStack stack) {
        if (this.resourcesCreated) {
            return true;
        }
        VkDevice device = this.context.device();
        org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer bindings =
                org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(3, stack);
        for (int i = 0; i < 3; i++) {
            bindings.get(i).binding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT);
        }
        org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutCreate =
                org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                        .pBindings(bindings);
        LongBuffer handle = stack.mallocLong(1);
        check(org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout(device, layoutCreate, null, handle),
                "vkCreateDescriptorSetLayout(mesh)");
        this.setLayout = handle.get(0);
        int slots = this.recordBuffers.length;
        int totalSets = slots * SETS_PER_SLOT;
        org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer sizes =
                org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(1, stack);
        sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(totalSets * 3);
        org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolCreate =
                org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                        .maxSets(totalSets)
                        .pPoolSizes(sizes);
        check(org.lwjgl.vulkan.VK10.vkCreateDescriptorPool(device, poolCreate, null, handle),
                "vkCreateDescriptorPool(mesh)");
        this.descriptorPool = handle.get(0);
        LongBuffer layouts = stack.mallocLong(totalSets);
        for (int i = 0; i < totalSets; i++) {
            layouts.put(i, this.setLayout);
        }
        LongBuffer sets = stack.mallocLong(totalSets);
        org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocate =
                org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                        .descriptorPool(this.descriptorPool)
                        .pSetLayouts(layouts);
        check(org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets(device, allocate, sets),
                "vkAllocateDescriptorSets(mesh)");
        for (int slot = 0; slot < slots; slot++) {
            for (int ring = 0; ring < SETS_PER_SLOT; ring++) {
                this.ringSets[slot][ring] = sets.get(slot * SETS_PER_SLOT + ring);
            }
            this.recordBuffers[slot] = new VulkanHostBuffer(this.context,
                    (long) RECORD_CAPACITY * RECORD_BYTES, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        this.resourcesCreated = true;
        return true;
    }

    /** Writes one ring set: the batch's arena, index buffer and the slot's record buffer. */
    private void writeSet(MemoryStack stack, long set, VulkanBuffer vertexArena,
            VulkanBuffer indexBuffer, VulkanHostBuffer recordBuffer) {
        org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer infos =
                org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(3, stack);
        infos.get(0).buffer(vertexArena.buffer()).offset(0L).range(vertexArena.size());
        infos.get(1).buffer(indexBuffer.buffer()).offset(0L).range(indexBuffer.size());
        infos.get(2).buffer(recordBuffer.buffer()).offset(0L).range(recordBuffer.size());
        org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer writes =
                org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(3, stack);
        for (int i = 0; i < 3; i++) {
            writes.get(i).sType$Default()
                    .dstSet(set)
                    .dstBinding(i)
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(infos.slice(i, 1));
        }
        org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets(this.context.device(), writes, null);
    }

    /**
     * Creates one pass' mesh sibling: a layout pairing the pass' own set 0 with this path's
     * set 1, and a graphics pipeline whose only stage difference from the pass' own is the
     * mesh stage in place of the vertex stage - no vertex input, no input assembly (a mesh
     * pipeline has neither), the same fragment module with the same specialization, and the
     * fixed-function state mirrored field for field from the pass pipeline's creation.
     */
    private MeshPipeline createMeshPipeline(MemoryStack stack, VulkanTerrainPipeline terrain) {
        VkDevice device = this.context.device();
        long layout = VK_NULL_HANDLE;
        long pipeline = VK_NULL_HANDLE;
        long meshModule = 0L;
        long fragmentModule = 0L;
        ByteBuffer meshCode = null;
        ByteBuffer fragmentCode = null;
        try {
            org.lwjgl.vulkan.VkPushConstantRange.Buffer range =
                    org.lwjgl.vulkan.VkPushConstantRange.calloc(1, stack);
            range.get(0).stageFlags(org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT)
                    .offset(0).size(PUSH_BYTES);
            org.lwjgl.vulkan.VkPipelineLayoutCreateInfo layoutCreate =
                    org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                            .pSetLayouts(stack.longs(terrain.descriptorSetLayout(), this.setLayout))
                            .pPushConstantRanges(range);
            LongBuffer handle = stack.mallocLong(1);
            check(org.lwjgl.vulkan.VK10.vkCreatePipelineLayout(device, layoutCreate, null, handle),
                    "vkCreatePipelineLayout(mesh)");
            layout = handle.get(0);

            meshCode = SpirvShaders.load(SpirvShaders.SOLID_MESH);
            fragmentCode = SpirvShaders.load(SpirvShaders.SOLID_FRAGMENT);
            meshModule = createShaderModule(stack, device, meshCode, "mesh");
            fragmentModule = createShaderModule(stack, device, fragmentCode, "fragment");
            org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.Buffer stages =
                    org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default()
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
            stages.get(1).sType$Default()
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
            check(VulkanContext.createGraphicsPipelines(device, create, handle, "terrain-mesh"),
                    "vkCreateGraphicsPipelines(mesh)");
            pipeline = handle.get(0);
            return new MeshPipeline(layout, pipeline);
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
            if (fragmentCode != null) {
                MemoryUtil.memFree(fragmentCode);
            }
            if (meshCode != null) {
                MemoryUtil.memFree(meshCode);
            }
        }
    }

    private static long createShaderModule(MemoryStack stack, VkDevice device, ByteBuffer code,
            String stage) {
        if (code == null || !code.isDirect() || code.remaining() < 4) {
            throw new IllegalStateException("SpirvShaders.load returned no direct SPIR-V byte code"
                    + " for the " + stage + " stage of the mesh terrain shader");
        }
        org.lwjgl.vulkan.VkShaderModuleCreateInfo create =
                org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
        LongBuffer handle = stack.mallocLong(1);
        check(org.lwjgl.vulkan.VK10.vkCreateShaderModule(device, create, null, handle),
                "vkCreateShaderModule(" + stage + ")");
        return handle.get(0);
    }

    /** Destroys every object this path created. Called with the terrain pipelines' release. */
    public void release() {
        if (this.released) {
            return;
        }
        this.released = true;
        VkDevice device = this.context.device();
        for (MeshPipeline mesh : this.pipelines.values()) {
            if (mesh.pipeline != VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyPipeline(device, mesh.pipeline, null);
            }
            if (mesh.layout != VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout(device, mesh.layout, null);
            }
        }
        this.pipelines.clear();
        if (this.descriptorPool != 0L) {
            org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool(device, this.descriptorPool, null);
            this.descriptorPool = 0L;
        }
        if (this.setLayout != 0L) {
            org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout(device, this.setLayout, null);
            this.setLayout = 0L;
        }
        for (int slot = 0; slot < this.recordBuffers.length; slot++) {
            if (this.recordBuffers[slot] != null) {
                this.recordBuffers[slot].delete();
                this.recordBuffers[slot] = null;
            }
        }
    }

    private static void reportFailure(String what, RuntimeException failure) {
        if (failureReports >= 4) {
            return;
        }
        failureReports++;
        System.out.println("[Celtium] CELTIUM_MESH_TERRAIN failure " + what + ": " + failure);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }
}
