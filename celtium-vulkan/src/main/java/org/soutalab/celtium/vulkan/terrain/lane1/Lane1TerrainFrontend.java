package org.soutalab.celtium.vulkan.terrain.lane1;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.frame.VulkanCommandStream;
import org.soutalab.celtium.core.shader.AttachmentBlendSpec;
import org.soutalab.celtium.core.shader.AttachmentFormatKey;
import org.soutalab.celtium.core.shader.BlendFactor;
import org.soutalab.celtium.core.shader.BlendOp;
import org.soutalab.celtium.core.shader.BlendStateSpec;
import org.soutalab.celtium.core.shader.DescriptorBindingPlan;
import org.soutalab.celtium.core.shader.ShaderStage;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.VulkanHostBuffer;
import org.soutalab.celtium.vulkan.gl.ModShaderCompiler;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Lane 1 terrain frontend inside the window backend (S2-b): owns the resident section store,
 * the slab spaces, the descriptor sets and the built-in axis/general programs, and records Lane 1
 * draws into the frame's render pass through {@link TerrainDrawStage}. The backend's frame shell
 * (render pass, swapchain borrow, immediate flush ordering) stays in {@code VulkanRenderer}; this
 * class is only the terrain lane's HAL: it builds pipelines from {@link TerrainStageProgram}s and
 * decides nothing else.
 * <p>
 * Culling is the CPU frustum walk inside {@code TerrainDrawStage} (the K3 fallback state); the
 * GPU cull / Hi-Z wiring is S3-b's seat. Fog rides a per-kind uniform block (binding 8) written at
 * {@link #prepareFrame} from the backend's immediate fog state, so the built-in fragment matches
 * the vanilla fog the GL path applied.
 */
public final class Lane1TerrainFrontend {

    // Sized from the Gate G1 flight scene's measured demand (2,269 sections resident):
    // axis 32.6 MB (~14 KB/section), general 67.1 MB and FULL at 64 MB (~30 KB/section -
    // the general quad is 64 B against the axis quad's 16 B, and un-packed geometry
    // (leaves, cutout, non-cube) lands there). General gets the headroom; axis keeps 2x.
    private static final long AXIS_SLAB_BYTES = 96L * 1024L * 1024L;
    private static final long GENERAL_SLAB_BYTES = 192L * 1024L * 1024L;
    private static final long AUX_SLAB_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_SECTIONS = 32768;
    private static final long DESC_BYTES = (long) MAX_SECTIONS * 48L;
    /** DrawPush: mat4 (64) + vec4 cameraPos (64) + ivec4 gridOrigin (80) + lightCount/pads (96). */
    private static final int PUSH_BYTES = 112;
    /** FogBlock: vec4 fogColor (0) + vec3 camera (16) + start (28) + end (32) + density (36) + mode (40) + cutoff (44). */
    private static final int FOG_BYTES = 64;
    /** Texture-cell table capacity: one uvec2 (cellU, cellV) per sprite texture index. */
    private static final int MAX_TEXTURE_CELLS = 8192;
    private static final long TEXTURE_CELL_BYTES = (long) MAX_TEXTURE_CELLS * 8L;
    /** Per-slot indirect-draw staging capacity: 16 B VkDrawIndirectCommand records. */
    private static final int MAX_INDIRECT_COMMANDS = 16384;
    private static final long INDIRECT_BYTES = (long) MAX_INDIRECT_COMMANDS * 16L;
    private static final int FRAME_SLOTS = 3;
    private static final int KIND_COUNT = 3;

    private final VulkanContext context;
    private final VulkanSlabSpace axisSpace;
    private final VulkanSlabSpace generalSpace;
    private final VulkanSlabSpace auxSpace;
    private TerrainSectionStore store;
    private TerrainWorld world;
    private TerrainDrawStage drawStage;

    private final VulkanHostBuffer descBuffer;
    private final VulkanHostBuffer lightBuffer;
    private final VulkanHostBuffer maskBuffer;
    private final VulkanHostBuffer textureCellBuffer;
    private final VulkanHostBuffer[] indirectBuffers = new VulkanHostBuffer[FRAME_SLOTS];
    private final int[] indirectCursor = new int[FRAME_SLOTS];
    private long indirectFrame = -1L;
    private final VulkanHostBuffer[] fogBuffers = new VulkanHostBuffer[KIND_COUNT];

    private final long setLayout;
    private final long pipelineLayout;
    private final long descriptorPool;
    /** [slot][kind][stream 0=axis, 1=general] */
    private final long[][][] sets = new long[FRAME_SLOTS][KIND_COUNT][2];
    private final long atlasSampler;
    private final long lightmapSampler;
    private final long[][] boundViews = new long[FRAME_SLOTS][2];

    private final TerrainStageProgram[] axisPrograms = new TerrainStageProgram[KIND_COUNT];
    private final TerrainStageProgram[] generalPrograms = new TerrainStageProgram[KIND_COUNT];
    private final Map<Long, Long> shaderModules = new HashMap<>();
    private final Map<PipelineKey, Long> pipelines = new HashMap<>();

    private TerrainSectionStore descStore;
    private long descGeneration = -1L;

    // Per-frame state, written by prepareFrame and consumed by recordPass / push constants.
    private final float[] viewProj = new float[16];
    private double camX;
    private double camY;
    private double camZ;
    private int gridOriginX;
    private int gridOriginY;
    private int gridOriginZ;
    private int lightCount;
    private float atlasCellsAcross = 64.0f;
    private int textureCellCount;
    private long frameId;
    private int refusedUploads;

    private record PipelineKey(long renderPass, int kindOrdinal, boolean general) {
    }

    public Lane1TerrainFrontend(VulkanContext context) {
        this.context = context;
        this.axisSpace = new VulkanSlabSpace(context, AXIS_SLAB_BYTES);
        this.generalSpace = new VulkanSlabSpace(context, GENERAL_SLAB_BYTES);
        this.auxSpace = new VulkanSlabSpace(context, AUX_SLAB_BYTES);
        this.store = new TerrainSectionStore(axisSpace, generalSpace, auxSpace);
        this.world = new TerrainWorld(store);
        this.descBuffer = new VulkanHostBuffer(context, DESC_BYTES, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        this.textureCellBuffer = new VulkanHostBuffer(context, TEXTURE_CELL_BYTES,
                VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            this.indirectBuffers[slot] = new VulkanHostBuffer(context, INDIRECT_BYTES,
                    VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
        }
        this.lightBuffer = new VulkanHostBuffer(context, LightGridBuilder.UBO_BYTES,
                VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        this.maskBuffer = new VulkanHostBuffer(context, LightGridBuilder.CLUSTER_MASK_BYTES,
                VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        for (int kind = 0; kind < KIND_COUNT; kind++) {
            this.fogBuffers[kind] = new VulkanHostBuffer(context, FOG_BYTES,
                    VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
        }
        this.setLayout = createSetLayout();
        this.pipelineLayout = createPipelineLayout();
        this.descriptorPool = createDescriptorPool();
        allocateSets();
        this.atlasSampler = createSampler(VK10.VK_FILTER_NEAREST, true);
        this.lightmapSampler = createSampler(VK10.VK_FILTER_LINEAR, false);
        buildPrograms();
        rebuildDrawStage();
        writeBufferDescriptors();
    }

    private void rebuildDrawStage() {
        TerrainCullStage cullStage = new TerrainCullStage(world);
        this.drawStage = new TerrainDrawStage(world, cullStage, this::sinkDraw);
    }

    // ------------------------------------------------------------------
    // World / section lifecycle (backend entry points)
    // ------------------------------------------------------------------

    /** Drops every resident section and rebuilds the store over the same slab spaces. */
    public synchronized void clearWorld() {
        this.store = new TerrainSectionStore(axisSpace, generalSpace, auxSpace);
        this.world = new TerrainWorld(store);
        rebuildDrawStage();
    }

    public synchronized void uploadSection(int x, int y, int z, SectionGeometry geometry, long timeline) {
        if (store.sectionCount() >= MAX_SECTIONS && !store.contains(x, y, z)) {
            refusedUploads++;
            return;
        }
        try {
            store.upload(x, y, z, geometry, timeline);
        } catch (IllegalStateException cannotFit) {
            // The store's contract is to throw when no region can fit the geometry; the
            // frontend's contract is a refusal the driver can retry once space frees.
            refusedUploads++;
        }
    }

    public synchronized void removeSection(int x, int y, int z, long timeline) {
        store.remove(x, y, z, timeline);
    }

    public synchronized boolean hasSection(int x, int y, int z) {
        return store.contains(x, y, z);
    }

    public synchronized int sectionCount() {
        return store.sectionCount();
    }

    public synchronized void collectCompleted() {
        store.collectCompleted(VulkanContext.completedEpoch());
    }

    /**
     * Replaces the texture-cell table the general stream's shaders read (binding 3): one
     * {@code (cellU, cellV)} pair per texture index, unpacked from the driver's
     * {@code cellU | (cellV << 8)} packing. Indices past the table resolve to cell 0 in the shader.
     */
    public synchronized void setTextureCellTable(int[] packedCells) {
        int count = Math.min(packedCells.length, MAX_TEXTURE_CELLS);
        MemorySegment segment = MemorySegment.ofAddress(textureCellBuffer.mappedAddress())
                .reinterpret(TEXTURE_CELL_BYTES);
        for (int i = 0; i < count; i++) {
            segment.set(ValueLayout.JAVA_INT, (long) i * 8L, packedCells[i] & 0xFF);
            segment.set(ValueLayout.JAVA_INT, (long) i * 8L + 4L, (packedCells[i] >> 8) & 0xFF);
        }
        this.textureCellCount = count;
    }


    public int refusedUploads() {
        return refusedUploads;
    }

    public String diagnosticsSummary() {
        return drawStage.diagnosticsSummary()
                + " sections=" + store.sectionCount()
                + " axisUsed=" + store.axisUsedBytes()
                + " generalUsed=" + store.generalUsedBytes()
                + " auxUsed=" + store.auxUsedBytes()
                + " refusedUploads=" + refusedUploads
                + " atlasCells=" + atlasCellsAcross
                + " texCells=" + textureCellCount;
