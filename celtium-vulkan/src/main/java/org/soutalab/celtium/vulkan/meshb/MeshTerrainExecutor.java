package org.soutalab.celtium.vulkan.meshb;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTMeshShader;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMeshShaderPropertiesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.soutalab.celtium.vulkan.SpirvShaders;
import org.soutalab.celtium.vulkan.VulkanContext;

import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

/**
 * The Tier-B terrain issuance stage of the new rendering architecture (design
 * sections 2.2 and 3, Phase 2): on mesh-shader hardware (RX 6400 / GTX 1650 Ti) this
 * executor replaces the Tier-A compute-cull + indirect-draw issuance for terrain, and
 * supersedes the stage-1/2 proof code ({@code VulkanMeshTerrain},
 * {@code VulkanMeshTaskDraw}, {@code VulkanMeshStage2Pipeline}), which read a per-draw
 * table and re-issued indexed draws of the 24-byte format. Here the inputs are the
 * GPU-resident {@link MeshTerrainLayout} SectionDesc arrays (the canonical layout of
 * the Tier-A spec, one record per (section, pass), bucket counts packed in the record
 * and bucket starts derived by prefix sum), the vertex data is the 16-byte
 * TerrainVertex16 format in HAL slabs, and triangles are generated in the mesh shader -
 * no index buffer, no per-frame CPU walk.
 * <p>
 * One {@link #record} call issues one pass' record array for one slab:
 * {@code vkCmdDrawMeshTasksEXT(recordCount, 1, 1)} with one task workgroup per record.
 * The task stage (terrain_meshb.task) runs the frustum / distance / Hi-Z / facing
 * judgement and emits 64-quad meshlets; the mesh stage (terrain_meshb.mesh) expands
 * them indexlessly. The shadow pass uses this same executor with the light camera in
 * the frame block, per design section 2.6.
 * <p>
 * Ownership: descriptor set 0 (SectionDesc, slab vertices, Hi-Z, frame block, per-quad
 * RDH stream) is this class's; set 1 is the terrain pass' own set, bound by the caller
 * exactly as for the vertex path. The frame block (set 0 binding 4, std140, 256 bytes)
 * carries world-space matrices: {@code viewProj} and {@code view} map world positions,
 * {@code cameraPos} is the world camera, followed by the six frustum planes and the
 * params word ({@code x} = maximum view distance in blocks, {@code y} = Hi-Z mip
 * count). {@link #packFrameBlock} writes it. Synchronization of the input buffers is
 * the frame graph's (design section 2.1 places one barrier after UploadBatch); this
 * class records no barriers and uploads nothing.
 * <p>
 * The Hi-Z binding must always name a valid sampled image. The test itself is gated by
 * the record call's {@code hizEnabled} flag and the shader short-circuits before
 * sampling when it is clear, so until the graph's Hi-Z pyramid exists the caller may
 * bind any valid single-channel image; the pyramid's contract is farthest depth in the
 * {@code .x} channel with a full mip chain.
 * <p>
 * Every refusal is a counted {@link GateReason}, never a silent fallback (the house
 * form): capability, limits, a missing pipeline, an empty section array, the task
 * workgroup ceiling. A Vulkan failure latches the executor disabled with one printed
 * line instead of touching the frame.
 */
public final class MeshTerrainExecutor {

    /** Push block bytes: pass ordinal, slab id, record count, flags (task + mesh). */
    public static final int PUSH_BYTES = 32;

    /** Frame block (set 0 binding 4) size and field offsets, std140. */
    public static final int FRAME_BLOCK_BYTES = 256;
    public static final int FRAME_OFF_VIEW_PROJ = 0;
    public static final int FRAME_OFF_VIEW = 64;
    public static final int FRAME_OFF_CAMERA_POS = 128;
    public static final int FRAME_OFF_PLANES = 144;
    public static final int FRAME_OFF_PARAMS = 240;

    /** Record flag: run the previous-frame Hi-Z occlusion test in the task stage. */
    public static final int FLAG_HIZ_ENABLED = 1;

    /**
     * Record flag: the per-quad RDH stream (set 0 binding 5) is bound and live, so the
     * mesh stage reads the bilinear colour correction from it. Clear it while the
     * stream does not exist yet and the mesh stage emits the neutral factor instead;
     * the binding must still name a valid storage buffer either way.
     */
    public static final int FLAG_QUAD_RDH_PRESENT = 2;

    private static final int PRINT_EVERY_FRAMES = 60;

    /** Why a record call did not issue; every value is counted. */
    public enum GateReason {
        /** Recorded: one vkCmdDrawMeshTasksEXT for the layer. */
        NONE,
        /** The device did not report / could not enable the mesh+task feature pair. */
        NO_MESH_SHADER,
        /** The device's mesh output limits cannot hold a 64-quad meshlet. */
        LIMITS_INSUFFICIENT,
        /** The caller supplied no pipeline for this pass variant yet. */
        NO_PIPELINE,
        /** The section array is empty: nothing to issue. */
        NO_SECTIONS,
        /** The section count exceeds the device's task workgroup ceiling. */
        OVER_TASK_WORKGROUP_LIMIT,
        /** A Vulkan failure latched the executor disabled. */
        DISABLED
    }

    /** The device mesh-shader limits this executor's meshlet shape depends on. */
    public static final class Limits {
        public final int maxMeshOutputVertices;
        public final int maxMeshOutputPrimitives;
        public final int maxTaskWorkGroupTotalCount;

        private Limits(int vertices, int primitives, int taskTotal) {
            this.maxMeshOutputVertices = vertices;
            this.maxMeshOutputPrimitives = primitives;
            this.maxTaskWorkGroupTotalCount = taskTotal;
        }

        /** Whether a 64-quad meshlet (256 vertices / 128 primitives) fits. */
        public boolean sufficient() {
            return this.maxMeshOutputVertices >= MeshTerrainLayout.QUADS_PER_MESHLET * 4
                    && this.maxMeshOutputPrimitives >= MeshTerrainLayout.QUADS_PER_MESHLET * 2
                    && this.maxTaskWorkGroupTotalCount > 0;
        }
    }

    /** Reads the device mesh-shader properties (the executor's own probe). */
    public static Limits probeLimits(VulkanContext context) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMeshShaderPropertiesEXT mesh = VkPhysicalDeviceMeshShaderPropertiesEXT
                    .calloc(stack).sType$Default();
            VkPhysicalDeviceProperties2 properties = VkPhysicalDeviceProperties2.calloc(stack)
                    .sType$Default().pNext(mesh.address());
            VK11.vkGetPhysicalDeviceProperties2(context.physicalDevice(), properties);
            return new Limits(mesh.maxMeshOutputVertices(), mesh.maxMeshOutputPrimitives(),
                    mesh.maxTaskWorkGroupTotalCount());
        }
    }

    /** The standing gate: the feature pair enabled AND the meshlet shape fits. */
    public static boolean supported(VulkanContext context) {
        return context.meshShaderEnabled() && probeLimits(context).sufficient();
    }

    /** Fixed-function and binding terms of one pass variant's pipeline. */
    public static final class PipelineSpec {
        public long renderPass;
        public int subpass;
        /** The pass' fragment shader module (caller-owned; never destroyed here). */
        public long fragmentModule;
        /** Whether the fragment module takes the discard specialization constant 0. */
        public boolean fragmentDiscardSpecialization;
        public int fragmentDiscardValue;
        /** The terrain pass' descriptor set layout, bound as set 1. */
        public long passSetLayout;
        public boolean depthTest;
        public boolean depthWrite;
        public boolean blended;
        public int cullMode;
        public int frontFace;

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PipelineSpec)) {
                return false;
            }
            PipelineSpec that = (PipelineSpec) other;
            return this.renderPass == that.renderPass && this.subpass == that.subpass
                    && this.fragmentModule == that.fragmentModule
                    && this.fragmentDiscardSpecialization == that.fragmentDiscardSpecialization
                    && this.fragmentDiscardValue == that.fragmentDiscardValue
                    && this.passSetLayout == that.passSetLayout
                    && this.depthTest == that.depthTest && this.depthWrite == that.depthWrite
                    && this.blended == that.blended && this.cullMode == that.cullMode
                    && this.frontFace == that.frontFace;
        }

        @Override
        public int hashCode() {
            long hash = this.renderPass * 31L + this.fragmentModule;
            hash = hash * 31L + this.passSetLayout;
            hash = hash * 31L + this.subpass;
            hash = hash * 31L + (this.depthTest ? 1 : 0) + (this.depthWrite ? 2 : 0)
                    + (this.blended ? 4 : 0) + (this.fragmentDiscardSpecialization ? 8 : 0);
            hash = hash * 31L + this.cullMode * 7L + this.frontFace;
            return (int) (hash ^ (hash >>> 32));
        }
    }

    /** The inputs of one {@link #record} call: one pass' record array for one slab. */
    public static final class RecordArgs {
        public long pipeline;
        public int frameSlot;
        /** The terrain pass' own descriptor set (set 1); the mesh stage reads it. */
        public long passDescriptorSet;
        /** The pass' SectionDesc record array (binding 0). */
        public long sectionBuffer;
        /** The slab vertex buffer whose id is {@link #slabId} (binding 2). */
        public long slabBuffer;
        /**
         * The per-quad RDH stream (binding 5), addressed by slab quad id. Must be a
         * valid storage buffer even while {@link #FLAG_QUAD_RDH_PRESENT} is clear (the
         * slab buffer itself is an acceptable placeholder until the stream exists).
         */
        public long quadRdhBuffer;
        public int slabId;
        /** The pass ordinal every record in {@link #sectionBuffer} carries. */
        public int passOrdinal;
        /** The number of records in {@link #sectionBuffer}. */
        public int recordCount;
        public long frameBlockBuffer;
        public long frameBlockOffset;
        /** A valid sampled image view for the Hi-Z binding (see the class doc). */
        public long hizImageView;
        public long hizSampler;
        /** {@link #FLAG_HIZ_ENABLED} | {@link #FLAG_QUAD_RDH_PRESENT}, or 0. */
        public int flags;
    }

    private static final class Slot {
        long descriptorPool;
        long descriptorSet;
        long describedSections;
        long describedSlab;
        long describedRdh;
        long describedFrameBlock;
        long describedHizView;
        long describedHizSampler;
    }

    private static final class CreatedPipeline {
        final long layout;
        final long pipeline;

        CreatedPipeline(long layout, long pipeline) {
            this.layout = layout;
            this.pipeline = pipeline;
        }
    }

    private final VulkanContext context;
    private final Limits limits;
    private long setLayout;
    private long taskModule;
    private long meshModule;
    private boolean disabled;
    private final Slot[] slots;
    private final Map<PipelineSpec, CreatedPipeline> pipelines = new HashMap<PipelineSpec, CreatedPipeline>();

    private final long[] reasonCounts = new long[GateReason.values().length];
    private long recordedFrames;
    private long recordedSections;

    public MeshTerrainExecutor(VulkanContext context, int frameSlots) {
        this.context = context;
        this.limits = probeLimits(context);
        this.slots = new Slot[Math.max(1, frameSlots)];
        for (int i = 0; i < this.slots.length; i++) {
            this.slots[i] = new Slot();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDevice device = context.device();
            LongBuffer handle = stack.mallocLong(1);
            org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer bindings =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(5, stack);
            int taskMesh = EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT
                    | EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT;
            int meshOnly = EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT;
            bindings.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).stageFlags(taskMesh);
            bindings.get(1).binding(2).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).stageFlags(meshOnly);
            bindings.get(2).binding(3).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT);
            bindings.get(3).binding(4).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1).stageFlags(taskMesh);
            bindings.get(4).binding(5).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1).stageFlags(meshOnly);
            org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo setInfo =
                    org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default().pBindings(bindings);
            check(vkCreateDescriptorSetLayout(device, setInfo, null, handle),
                    "create mesh-b set layout");
            this.setLayout = handle.get(0);
            this.taskModule = createModule(stack, device, "terrain_meshb.task");
            this.meshModule = createModule(stack, device, "terrain_meshb.mesh");
        }
        System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_B_GATE meshShader="
                + context.meshShaderEnabled()
                + " maxMeshOutputVertices=" + this.limits.maxMeshOutputVertices
                + " maxMeshOutputPrimitives=" + this.limits.maxMeshOutputPrimitives
                + " maxTaskWorkGroupTotalCount=" + this.limits.maxTaskWorkGroupTotalCount
                + " sufficient=" + this.limits.sufficient()
                + " quadsPerMeshlet=" + MeshTerrainLayout.QUADS_PER_MESHLET);
    }

    /** The set-0 layout every Tier-B pipeline layout leads with. */
    public long setLayout() {
        return this.setLayout;
    }

    public Limits limits() {
        return this.limits;
    }

    public boolean disabled() {
        return this.disabled;
    }

    /**
     * Writes the frame block (set 0 binding 4) at {@code offset} of {@code target}:
     * world-space view-projection and view matrices (16 floats each, column-major),
     * the world camera position, the six frustum planes (xyz + w each) and the params
     * word (x = maximum view distance in blocks, y = Hi-Z mip count, z/w zero).
     */
    public static void packFrameBlock(ByteBuffer target, int offset, float[] viewProj,
                                      float[] view, float cameraX, float cameraY,
                                      float cameraZ, float[] planes, float maxDistance,
                                      float hizMipCount) {
        int base = offset;
        for (int i = 0; i < 16; i++) {
            target.putFloat(base + FRAME_OFF_VIEW_PROJ + i * 4, viewProj[i]);
            target.putFloat(base + FRAME_OFF_VIEW + i * 4, view[i]);
        }
        target.putFloat(base + FRAME_OFF_CAMERA_POS, cameraX);
        target.putFloat(base + FRAME_OFF_CAMERA_POS + 4, cameraY);
        target.putFloat(base + FRAME_OFF_CAMERA_POS + 8, cameraZ);
        target.putFloat(base + FRAME_OFF_CAMERA_POS + 12, 1.0f);
        for (int i = 0; i < 24; i++) {
            target.putFloat(base + FRAME_OFF_PLANES + i * 4, planes[i]);
        }
        target.putFloat(base + FRAME_OFF_PARAMS, maxDistance);
        target.putFloat(base + FRAME_OFF_PARAMS + 4, hizMipCount);
        target.putFloat(base + FRAME_OFF_PARAMS + 8, 0.0f);
        target.putFloat(base + FRAME_OFF_PARAMS + 12, 0.0f);
    }

    /**
     * Creates (or returns the cached) pipeline for one pass variant: a layout leading
     * with this executor's set 0 and the spec's pass set as set 1, the 32-byte push
     * range at task+mesh stages, and the task+mesh+fragment stage triple. The fragment
     * module stays the caller's.
     *
     * @return the pipeline handle, or 0 when the executor is disabled
     */
    public long createPipeline(PipelineSpec spec) {
        if (this.disabled) {
            return VK_NULL_HANDLE;
        }
        CreatedPipeline cached = this.pipelines.get(spec);
        if (cached != null) {
            return cached.pipeline;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDevice device = this.context.device();
            LongBuffer handle = stack.mallocLong(1);
            org.lwjgl.vulkan.VkPushConstantRange.Buffer range =
                    org.lwjgl.vulkan.VkPushConstantRange.calloc(1, stack);
            range.get(0).stageFlags(EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT
                            | EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT)
                    .offset(0).size(PUSH_BYTES);
            org.lwjgl.vulkan.VkPipelineLayoutCreateInfo layoutCreate =
                    org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                            .pSetLayouts(stack.longs(this.setLayout, spec.passSetLayout))
                            .pPushConstantRanges(range);
            check(org.lwjgl.vulkan.VK10.vkCreatePipelineLayout(device, layoutCreate, null, handle),
                    "vkCreatePipelineLayout(mesh-b)");
            long layout = handle.get(0);
            long pipeline;
            try {
                org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.Buffer stages =
                        org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.calloc(3, stack);
                stages.get(0).sType$Default()
                        .stage(EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT)
                        .module(this.taskModule)
                        .pName(stack.UTF8("main"));
                stages.get(1).sType$Default()
                        .stage(EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT)
                        .module(this.meshModule)
                        .pName(stack.UTF8("main"));
                stages.get(2).sType$Default()
                        .stage(org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                        .module(spec.fragmentModule)
                        .pName(stack.UTF8("main"));
                if (spec.fragmentDiscardSpecialization) {
                    org.lwjgl.vulkan.VkSpecializationMapEntry.Buffer entries =
                            org.lwjgl.vulkan.VkSpecializationMapEntry.calloc(1, stack);
                    entries.get(0).constantID(0).offset(0).size(4);
                    org.lwjgl.vulkan.VkSpecializationInfo specialization =
                            org.lwjgl.vulkan.VkSpecializationInfo.calloc(stack)
                                    .pMapEntries(entries)
                                    .pData(stack.malloc(4).putInt(0, spec.fragmentDiscardValue));
                    stages.get(2).pSpecializationInfo(specialization);
                }
                org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo viewport =
                        org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo.calloc(stack)
                                .sType$Default().viewportCount(1).scissorCount(1);
                org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo rasterization =
                        org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .depthClampEnable(false)
                                .rasterizerDiscardEnable(false)
                                .polygonMode(org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL)
                                .frontFace(spec.frontFace)
                                .cullMode(spec.cullMode)
                                .depthBiasEnable(false)
                                .lineWidth(1.0f);
                org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo multisample =
                        org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .rasterizationSamples(org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT)
                                .sampleShadingEnable(false)
                                .alphaToCoverageEnable(false)
                                .alphaToOneEnable(false);
                org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo depthStencil =
                        org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .depthTestEnable(spec.depthTest)
                                .depthWriteEnable(spec.depthWrite)
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
                if (spec.blended) {
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
                                .pDynamicStates(stack.ints(
                                        org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_VIEWPORT,
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
                        .renderPass(spec.renderPass)
                        .subpass(spec.subpass)
                        .basePipelineHandle(VK_NULL_HANDLE)
                        .basePipelineIndex(-1);
                check(VulkanContext.createGraphicsPipelines(device, create, handle, "terrain-mesh-b"),
                        "vkCreateGraphicsPipelines(mesh-b)");
                pipeline = handle.get(0);
            } catch (RuntimeException failure) {
                org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout(device, layout, null);
                throw failure;
            }
            PipelineSpec key = copyOf(spec);
            this.pipelines.put(key, new CreatedPipeline(layout, pipeline));
            return pipeline;
        }
    }

    /**
     * Records one Tier-B issuance - one pass' record array for one slab - into
     * {@code commandBuffer}, which must be inside the pass' render pass. The caller
     * owns the viewport/scissor state and the input buffers' synchronization.
     *
     * @return the first gate that applied, or {@link GateReason#NONE} when recorded
     */
    public GateReason record(MemoryStack stack, VkCommandBuffer commandBuffer, RecordArgs args) {
        if (this.disabled) {
            return count(GateReason.DISABLED);
        }
        if (!this.context.meshShaderEnabled()) {
            return count(GateReason.NO_MESH_SHADER);
        }
        if (!this.limits.sufficient()) {
            return count(GateReason.LIMITS_INSUFFICIENT);
        }
        if (args.pipeline == VK_NULL_HANDLE) {
            return count(GateReason.NO_PIPELINE);
        }
        if (args.recordCount <= 0) {
            return count(GateReason.NO_SECTIONS);
        }
        if (args.recordCount > this.limits.maxTaskWorkGroupTotalCount) {
            return count(GateReason.OVER_TASK_WORKGROUP_LIMIT);
        }
        CreatedPipeline created = findPipeline(args.pipeline);
        if (created == null) {
            return count(GateReason.NO_PIPELINE);
        }
        try {
            Slot slot = this.slots[args.frameSlot];
            ensureDescriptors(stack, slot, args);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, args.pipeline);
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    created.layout, 0, stack.longs(slot.descriptorSet, args.passDescriptorSet),
                    null);
            ByteBuffer push = stack.malloc(PUSH_BYTES);
            push.putInt(0, args.passOrdinal);
            push.putInt(4, args.slabId);
            push.putInt(8, args.recordCount);
            push.putInt(12, args.flags);
            push.putLong(16, 0L);
            push.putLong(24, 0L);
            push.clear();
            vkCmdPushConstants(commandBuffer, created.layout,
                    EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT
                            | EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT, 0, push);
            EXTMeshShader.vkCmdDrawMeshTasksEXT(commandBuffer, args.recordCount, 1, 1);
        } catch (RuntimeException error) {
            this.disabled = true;
            System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_B_DISABLED at=record reason=" + error);
            return count(GateReason.DISABLED);
        }
        this.recordedFrames++;
        this.recordedSections += args.recordCount;
        if (this.recordedFrames % PRINT_EVERY_FRAMES == 0) {
            System.out.println("[Celtium] CELTIUM_MESH_TERRAIN_B frames=" + this.recordedFrames
                    + " sections=" + this.recordedSections
                    + " fallbacks=[noMeshShader=" + this.reasonCounts[GateReason.NO_MESH_SHADER.ordinal()]
                    + " limits=" + this.reasonCounts[GateReason.LIMITS_INSUFFICIENT.ordinal()]
                    + " noPipeline=" + this.reasonCounts[GateReason.NO_PIPELINE.ordinal()]
                    + " overLimit=" + this.reasonCounts[GateReason.OVER_TASK_WORKGROUP_LIMIT.ordinal()]
                    + "]");
        }
        return count(GateReason.NONE);
    }

    /** Destroys every executor resource. Called from the wiring's own teardown. */
    public void delete() {
        VkDevice device = this.context.device();
        for (CreatedPipeline created : this.pipelines.values()) {
            vkDestroyPipeline(device, created.pipeline, null);
            vkDestroyPipelineLayout(device, created.layout, null);
        }
        this.pipelines.clear();
        for (Slot slot : this.slots) {
            if (slot.descriptorPool != 0L) {
                vkDestroyDescriptorPool(device, slot.descriptorPool, null);
                slot.descriptorPool = 0L;
            }
        }
        if (this.taskModule != 0L) {
            vkDestroyShaderModule(device, this.taskModule, null);
            this.taskModule = 0L;
        }
        if (this.meshModule != 0L) {
            vkDestroyShaderModule(device, this.meshModule, null);
            this.meshModule = 0L;
        }
        if (this.setLayout != 0L) {
            vkDestroyDescriptorSetLayout(device, this.setLayout, null);
            this.setLayout = 0L;
        }
    }

    private CreatedPipeline findPipeline(long pipeline) {
        for (CreatedPipeline created : this.pipelines.values()) {
            if (created.pipeline == pipeline) {
                return created;
            }
        }
        return null;
    }

    private static PipelineSpec copyOf(PipelineSpec spec) {
        PipelineSpec copy = new PipelineSpec();
        copy.renderPass = spec.renderPass;
        copy.subpass = spec.subpass;
        copy.fragmentModule = spec.fragmentModule;
        copy.fragmentDiscardSpecialization = spec.fragmentDiscardSpecialization;
        copy.fragmentDiscardValue = spec.fragmentDiscardValue;
        copy.passSetLayout = spec.passSetLayout;
        copy.depthTest = spec.depthTest;
        copy.depthWrite = spec.depthWrite;
        copy.blended = spec.blended;
        copy.cullMode = spec.cullMode;
        copy.frontFace = spec.frontFace;
        return copy;
    }

    /** (Re)writes the slot's set-0 descriptors when any bound handle has changed. */
    private void ensureDescriptors(MemoryStack stack, Slot slot, RecordArgs args) {
        if (slot.descriptorSet != 0L
                && slot.describedSections == args.sectionBuffer
                && slot.describedSlab == args.slabBuffer
                && slot.describedRdh == args.quadRdhBuffer
                && slot.describedFrameBlock == args.frameBlockBuffer
                && slot.describedHizView == args.hizImageView
                && slot.describedHizSampler == args.hizSampler) {
            return;
        }
        VkDevice device = this.context.device();
        LongBuffer handle = stack.mallocLong(1);
        if (slot.descriptorPool == 0L) {
            org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer poolSizes =
                    org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(3, stack);
            poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(3);
            poolSizes.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
            poolSizes.get(2).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1);
            org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                            .maxSets(1).pPoolSizes(poolSizes);
            check(vkCreateDescriptorPool(device, poolInfo, null, handle),
                    "create mesh-b descriptor pool");
            slot.descriptorPool = handle.get(0);
            org.lwjgl.vulkan.VkDescriptorSetAllocateInfo setInfo =
                    org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                            .descriptorPool(slot.descriptorPool)
                            .pSetLayouts(stack.longs(this.setLayout));
            check(vkAllocateDescriptorSets(device, setInfo, handle),
                    "allocate mesh-b descriptor set");
            slot.descriptorSet = handle.get(0);
        }
        org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer bufferInfos =
                org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(4, stack);
        bufferInfos.get(0).buffer(args.sectionBuffer).offset(0).range(VK_WHOLE_SIZE);
        bufferInfos.get(1).buffer(args.slabBuffer).offset(0).range(VK_WHOLE_SIZE);
        bufferInfos.get(2).buffer(args.quadRdhBuffer).offset(0).range(VK_WHOLE_SIZE);
        bufferInfos.get(3).buffer(args.frameBlockBuffer).offset(args.frameBlockOffset)
                .range(FRAME_BLOCK_BYTES);
        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer imageInfo =
                org.lwjgl.vulkan.VkDescriptorImageInfo.calloc(1, stack);
        imageInfo.get(0).sampler(args.hizSampler).imageView(args.hizImageView)
                .imageLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer writes =
                org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(5, stack);
        int[] bufferBindings = {0, 2, 5, 4};
        int[] bufferTypes = {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER};
        for (int i = 0; i < 4; i++) {
            writes.get(i).sType$Default().dstSet(slot.descriptorSet)
                    .dstBinding(bufferBindings[i]).descriptorCount(1)
                    .descriptorType(bufferTypes[i]).pBufferInfo(bufferInfos.slice(i, 1));
        }
        writes.get(4).sType$Default().dstSet(slot.descriptorSet)
                .dstBinding(3).descriptorCount(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .pImageInfo(imageInfo);
        vkUpdateDescriptorSets(device, writes, null);
        slot.describedSections = args.sectionBuffer;
        slot.describedSlab = args.slabBuffer;
        slot.describedRdh = args.quadRdhBuffer;
        slot.describedFrameBlock = args.frameBlockBuffer;
        slot.describedHizView = args.hizImageView;
        slot.describedHizSampler = args.hizSampler;
    }

    private long createModule(MemoryStack stack, VkDevice device, String shaderName) {
        ByteBuffer code = SpirvShaders.load(shaderName);
        try {
            org.lwjgl.vulkan.VkShaderModuleCreateInfo moduleInfo =
                    org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc(stack)
                            .sType$Default().pCode(code);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateShaderModule(device, moduleInfo, null, handle),
                    "vkCreateShaderModule(mesh-b " + shaderName + ")");
            return handle.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    private GateReason count(GateReason reason) {
        this.reasonCounts[reason.ordinal()]++;
        return reason;
    }

    private void check(int result, String what) {
        // VulkanContext.celtium$reportDeviceLost is package-private to the parent
        // package, so a device loss surfaces here as the same loud failure without the
        // context's crash-breadcrumb report.
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed: VkResult " + result
                    + (result == VK_ERROR_DEVICE_LOST ? " (device lost)" : ""));
        }
    }
}
