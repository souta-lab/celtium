package org.soutalab.celtium.vulkan.terrain.lane1;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
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
import org.lwjgl.vulkan.VkQueueFamilyProperties;
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
import org.soutalab.celtium.vulkan.FrameImageTarget;
import org.soutalab.celtium.vulkan.FrameSubmissionScheduler;
import org.soutalab.celtium.vulkan.GllessFrameTarget;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.VulkanHostBuffer;
import org.soutalab.celtium.vulkan.hal.SlabAllocator;
import org.soutalab.celtium.vulkan.shader.ModShaderCompiler;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
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
 * Main draws can use the GPU frustum cull. Fog rides a per-kind uniform block (binding 8) written at
 * {@link #prepareFrame} from the backend's immediate fog state, so the built-in fragment matches
 * the vanilla fog the GL path applied.
 */
public final class Lane1TerrainFrontend implements TerrainDrawStage.IndirectDrawSink {

    // Sized from the Gate G1 flight scene's measured demand (2,269 sections resident):
    // axis 32.6 MB (~14 KB/section), general 67.1 MB and FULL at 64 MB (~30 KB/section -
    // the general quad is 64 B against the axis quad's 16 B, and un-packed geometry
    // (leaves, cutout, non-cube) lands there). General gets the headroom; axis keeps 2x.
    private static final long AXIS_SLAB_BYTES = 96L * 1024L * 1024L;
    private static final long GENERAL_SLAB_BYTES = 192L * 1024L * 1024L;
    // The auxiliary slab carries the pack mc_Entity columns (2 B per axis quad) and, since
    // R-107, the unconditional per-corner light columns (8 B per quad over both quad kinds).
    // Worst-case sizing against the geometry slabs' quad ceilings: ~6M axis quads and ~2.4M
    // general quads of light (48 MB + 19 MB) plus ~12 MB of mc_Entity columns stays under
    // 96 MB with headroom.
    private static final long AUX_SLAB_BYTES = 96L * 1024L * 1024L;
    private static final int MAX_SECTIONS = 32768;
    private static final long DESC_BYTES = (long) MAX_SECTIONS * 48L;
    /** DrawPush byte offsets: mat4 0, camera section 64, grid origin 80, metadata 96, camera fraction 112. */
    private static final int PUSH_BYTES = 128;
    /** FogBlock: vec4 fogColor (0) + vec3 camera (16) + start (28) + end (32) + density (36) + mode (40) + cutoff (44). */
    private static final int FOG_BYTES = 64;
    /** Texture-cell table capacity: one uvec2 (cellU, cellV) per sprite texture index. */
    private static final int MAX_TEXTURE_CELLS = 8192;
    private static final long TEXTURE_CELL_BYTES = (long) MAX_TEXTURE_CELLS * 8L;
    /** Sprite-rectangle table capacity: one vec4 (uvMin, uvSize) per sprite texture index. */
    private static final long TEXTURE_RECT_BYTES = (long) MAX_TEXTURE_CELLS * 16L;
    /** Per-slot indirect-draw staging capacity: 16 B VkDrawIndirectCommand records. */
    private static final int MAX_INDIRECT_COMMANDS = 16384;
    private static final long INDIRECT_BYTES = (long) MAX_INDIRECT_COMMANDS * 16L;
    private static final int FRAME_SLOTS = FrameSubmissionScheduler.FRAME_SLOTS;
    private static final boolean GPU_CULL = Boolean.parseBoolean(System.getProperty("celtium.lane1.gpuCull", "true"));
    private static final boolean HIZ = Boolean.parseBoolean(System.getProperty("celtium.lane1.hiZ", "true"));
    private static final int COMMAND_STRIDE = 16;
    private static final long CULL_COMMAND_BYTES = 6L * MAX_SECTIONS * COMMAND_STRIDE;
    private static final long COUNTER_BYTES = 8L * Integer.BYTES;
    private static final int MAX_HIZ_LEVELS = 31;
    private static final int KIND_COUNT = 3;

    private final VulkanContext context;
    private final boolean computeQueue;
    private final VulkanSlabSpace axisSpace;
    private final VulkanSlabSpace generalSpace;
    private final VulkanSlabSpace auxSpace;
    private TerrainSectionStore store;
    private TerrainWorld world;
    private TerrainDrawStage drawStage;

    private final VulkanHostBuffer descBuffer;
    private final VulkanHostBuffer rangesBuffer;
    private final VulkanHostBuffer[] frameDescs = new VulkanHostBuffer[FRAME_SLOTS];
    private final VulkanHostBuffer[] frameRanges = new VulkanHostBuffer[FRAME_SLOTS];
    private final long[] uploadedDescVersions = new long[FRAME_SLOTS];
    private long descVersion;
    private int descCount;
    private final VulkanHostBuffer lightBuffer;
    private final VulkanHostBuffer maskBuffer;
    private final VulkanHostBuffer textureCellBuffer;
    private final VulkanHostBuffer textureRectBuffer;
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
    private CameraSection cameraSection;
    private FrustumPlanes frustum;
    private long indirectCommandBuffer;
    private int indirectSlot;
    private ComputeProgram cullProgram;
    private ComputeProgram hiZProgram;
    private final VulkanHostBuffer[] cullCommands = new VulkanHostBuffer[FRAME_SLOTS];
    private final VulkanHostBuffer[] cullCounters = new VulkanHostBuffer[FRAME_SLOTS];
    private final HiZImage[] cullImages = new HiZImage[FRAME_SLOTS];
    private final HiZSlot[] hiZSlots = new HiZSlot[FRAME_SLOTS];
    private HiZImage dummyDepth;
    private HiZImage pyramid;
    private long hiZSampler;
    private boolean hiZGuarded;
    private boolean hiZValid;
    private int hiZGuardCount;
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
        this.computeQueue = (GPU_CULL || HIZ) && supportsCompute(context);
        this.axisSpace = new VulkanSlabSpace(context, AXIS_SLAB_BYTES);
        this.generalSpace = new VulkanSlabSpace(context, GENERAL_SLAB_BYTES);
        this.auxSpace = new VulkanSlabSpace(context, AUX_SLAB_BYTES);
        this.store = new TerrainSectionStore(axisSpace, generalSpace, auxSpace);
        this.world = new TerrainWorld(store);
        this.descBuffer = new VulkanHostBuffer(context, DESC_BYTES, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        this.rangesBuffer = canCull() ? new VulkanHostBuffer(context, DESC_BYTES,
                VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT) : null;
        this.textureCellBuffer = new VulkanHostBuffer(context, TEXTURE_CELL_BYTES,
                VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        this.textureRectBuffer = new VulkanHostBuffer(context, TEXTURE_RECT_BYTES,
                VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            frameDescs[slot] = new VulkanHostBuffer(context, DESC_BYTES, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            if (rangesBuffer != null) {
                frameRanges[slot] = new VulkanHostBuffer(context, DESC_BYTES, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            }
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
        this.drawStage.setIndirectDrawSink(this);
    }

    // ------------------------------------------------------------------
    // World / section lifecycle (backend entry points)
    // ------------------------------------------------------------------

    /** Drops every resident section and rebuilds the store over the same slab spaces. */
    public synchronized void clearWorld() {
        this.store = new TerrainSectionStore(axisSpace, generalSpace, auxSpace);
        this.world = new TerrainWorld(store);
        rebuildDrawStage();
        cameraSection = null;
        frustum = null;
        hiZValid = false;
        if (pyramid != null) {
            pyramid.built = false;
        }
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

    public synchronized void collectCompleted(long completedSubmitWatermark) {
        store.collectCompleted(completedSubmitWatermark);
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

    /**
     * Replaces the sprite-rectangle table the general stream's vertex shader reads
     * (binding 9): one {@code (uvMin.xy, uvSize.zw)} vec4 per texture index, in the same
     * index space and count as the cell table. The general stream restores atlas UVs from
     * these real rectangles (UV ruling 5.4-2), never from a fixed cell size.
     */
    public synchronized void setTextureRectTable(float[] rects) {
        int count = Math.min(rects.length / 4, MAX_TEXTURE_CELLS);
        MemorySegment segment = MemorySegment.ofAddress(textureRectBuffer.mappedAddress())
                .reinterpret(TEXTURE_RECT_BYTES);
        for (int i = 0; i < count * 4; i++) {
            segment.set(ValueLayout.JAVA_FLOAT, (long) i * 4L, rects[i]);
        }
    }


    public int refusedUploads() {
        return refusedUploads;
    }

    /** The terrain driver the native pack frame seat records through (plan interface 1). */
    public org.soutalab.celtium.core.shader.PackTerrainDriver terrainDriver() {
        return drawStage;
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
    }

    // ------------------------------------------------------------------
    // Frame preparation + recording
    // ------------------------------------------------------------------

    /**
     * Publishes one frame's view state: the descriptor table, the dynamic-light grid and the fog
     * blocks. The Vulkan view-projection and frustum planes both operate on positions
     * relative to the supplied world origin.
     */
    public synchronized void prepareFrame(float[] viewProj16, double camX, double camY, double camZ,
                                          float[] frustumCoefficients, List<LightGridBuilder.PointLight> lights,
                                          float[] fogColorRgba, float fogStart, float fogEnd,
                                          float fogDensity, int fogMode, long frameId) {
        prepareFrame(viewProj16, new CameraPose(camX, camY, camZ, 0.0f, 0.0f),
                FrustumPlanes.of(frustumCoefficients), lights, fogColorRgba,
                fogStart, fogEnd, fogDensity, fogMode, frameId);
    }

    /** Publishes the matching transform, origin and planes from the extractor. */
    public synchronized void prepareFrame(FrustumExtractor.View view, List<LightGridBuilder.PointLight> lights,
                                          float[] fogColorRgba, float fogStart, float fogEnd,
                                          float fogDensity, int fogMode, long frameId) {
        prepareFrame(view.viewProj(), view.origin(), view.planes(), lights, fogColorRgba,
                fogStart, fogEnd, fogDensity, fogMode, frameId);
    }

    private void prepareFrame(float[] viewProj16, CameraPose origin, FrustumPlanes frustum,
                              List<LightGridBuilder.PointLight> lights, float[] fogColorRgba,
                              float fogStart, float fogEnd, float fogDensity, int fogMode, long frameId) {
        CameraSection nextCamera = CameraSection.of(origin);
        if (cameraSection == null || this.frameId != frameId) {
            hiZGuarded = cameraSection != null && (
                    Math.abs(viewProj16[5] - viewProj[5]) > 0.15f * Math.abs(viewProj[5])
                    || Math.abs((long) nextCamera.x() - cameraSection.x()) > 4
                    || Math.abs((long) nextCamera.y() - cameraSection.y()) > 4
                    || Math.abs((long) nextCamera.z() - cameraSection.z()) > 4);
            if (hiZGuarded && hiZGuardCount < Integer.MAX_VALUE) {
                hiZGuardCount++;
            }
            hiZValid = pyramid != null && pyramid.built && !hiZGuarded;
        }
        drawStage.disarmIndirect();
        System.arraycopy(viewProj16, 0, this.viewProj, 0, 16);
        this.cameraSection = nextCamera;
        this.frustum = frustum;
        this.frameId = frameId;
        world.setMainView(origin, frustum, frameId);
        double camX = origin.x();
        double camY = origin.y();
        double camZ = origin.z();
        // The descriptor block only changes when residency does; rewriting its ~140 KB
        // every frame was pure overhead in a steady view (Gate G1 profile).
        if (descStore != store || descGeneration != store.generation()) {
            MemorySegment descSegment = MemorySegment.ofAddress(descBuffer.mappedAddress())
                    .reinterpret(DESC_BYTES);
            descCount = store.writeDescriptors(descSegment, 0L);
            if (rangesBuffer != null) {
                store.writeLayerRanges(mapped(rangesBuffer, DESC_BYTES));
            }
            descVersion++;
            descStore = store;
            descGeneration = store.generation();
        }
        MemorySegment lightSegment = MemorySegment.ofAddress(lightBuffer.mappedAddress())
                .reinterpret(LightGridBuilder.UBO_BYTES);
        MemorySegment maskSegment = MemorySegment.ofAddress(maskBuffer.mappedAddress())
                .reinterpret(LightGridBuilder.CLUSTER_MASK_BYTES);
        LightGridBuilder.Result result = LightGridBuilder.build(lights, camX, camY, camZ,
                lightSegment, maskSegment);
        this.gridOriginX = result.originX();
        this.gridOriginY = result.originY();
        this.gridOriginZ = result.originZ();
        this.lightCount = result.lightCount();
        for (int kind = 0; kind < KIND_COUNT; kind++) {
            MemorySegment fog = MemorySegment.ofAddress(fogBuffers[kind].mappedAddress())
                    .reinterpret(FOG_BYTES);
            fog.set(ValueLayout.JAVA_FLOAT, 0L, fogColorRgba[0]);
            fog.set(ValueLayout.JAVA_FLOAT, 4L, fogColorRgba[1]);
            fog.set(ValueLayout.JAVA_FLOAT, 8L, fogColorRgba[2]);
            fog.set(ValueLayout.JAVA_FLOAT, 12L, fogColorRgba[3]);
            fog.set(ValueLayout.JAVA_FLOAT, 16L, (float) camX);
            fog.set(ValueLayout.JAVA_FLOAT, 20L, (float) camY);
            fog.set(ValueLayout.JAVA_FLOAT, 24L, (float) camZ);
            fog.set(ValueLayout.JAVA_FLOAT, 28L, fogStart);
            fog.set(ValueLayout.JAVA_FLOAT, 32L, fogEnd);
            fog.set(ValueLayout.JAVA_FLOAT, 36L, fogDensity);
            fog.set(ValueLayout.JAVA_INT, 40L, fogMode);
            fog.set(ValueLayout.JAVA_FLOAT, 44L, alphaCutoff(kind));
            fog.set(ValueLayout.JAVA_FLOAT, 48L, atlasCellsAcross);
        }
    }

    private static float alphaCutoff(int kindOrdinal) {
        return switch (kindOrdinal) {
            case 0 -> 0.0f;
            case 1 -> 0.1f;
            default -> 0.0001f;
        };
    }

    /**
     * Records one main-view terrain layer into the frame's already-open render pass. The pass
     * bracket belongs to the backend's shell, so the command stream handed to the draw stage has
     * no-op begin/end rendering.
     */
    public synchronized void recordPass(TerrainPassKind kind, VkCommandBuffer commandBuffer,
                                        long renderPass, int frameSlot, long atlasView, long lightmapView,
                                        float atlasCellsAcross) {
        if (atlasCellsAcross > 0.0f) {
            this.atlasCellsAcross = atlasCellsAcross;
        }
        if (this.frameId != indirectFrame) {
            java.util.Arrays.fill(indirectCursor, 0);
            indirectFrame = this.frameId;
        }
        int kindOrdinal = kind.ordinal();
        int slot = Math.floorMod(frameSlot, FRAME_SLOTS);
        uploadFrameDescriptors(slot);
        if (boundViews[slot][0] != atlasView || boundViews[slot][1] != lightmapView) {
            writeImageDescriptors(slot, atlasView, lightmapView);
            boundViews[slot][0] = atlasView;
            boundViews[slot][1] = lightmapView;
        }
        ShellStream stream = new ShellStream(commandBuffer, renderPass, slot, kindOrdinal);
        if (kind.isMain() && drawStage.indirectArmed(frameId)
                && indirectSlot == slot && indirectCommandBuffer == commandBuffer.address()) {
            drawStage.recordTerrainPassIndirect(stream, kind, axisPrograms[kindOrdinal], null);
        } else {
            drawStage.recordTerrainPass(stream, kind, axisPrograms[kindOrdinal], null);
        }
    }

    /**
     * The draw stage's sink: one indirect draw per same-stream run. The per-section
     * {@code vkCmdDraw} issuance this replaces cost more CPU than the rest of the pass combined at
     * Gate G1 (~3.5k draws per frame through the LWJGL/JNI seam); the commands are pure data, so
     * they are staged into the frame slot's host-visible indirect buffer and issued with a single
     * {@code vkCmdDrawIndirect} per run (the design's production sink shape; the GPU-side count
     * form stays S3-b's). Falls back to direct draws when the device lacks multiDrawIndirect or
     * the frame's command budget is exhausted.
     */
    private void sinkDraw(VulkanCommandStream stream, TerrainPassKind kind,
                          int[] packed, int commandCount) {
        ShellStream shell = (ShellStream) stream;
        VkCommandBuffer cmd = shell.commandBuffer();
        int slot = shell.slot();
        boolean useIndirect = context.multiDrawIndirectEnabled() && context.drawIndirectFirstInstanceEnabled();
        int index = 0;
        while (index < commandCount) {
            boolean general = packed[index * 4 + 3] != 0;
            int end = index + 1;
            while (end < commandCount && (packed[end * 4 + 3] != 0) == general) {
                end++;
            }
            shell.bindTerrainProgram(general
                    ? generalPrograms[kind.ordinal()] : axisPrograms[kind.ordinal()]);
            int count = end - index;
            if (useIndirect && indirectCursor[slot] + count <= MAX_INDIRECT_COMMANDS
                    && Integer.compareUnsigned(count, context.maxDrawIndirectCount()) <= 0) {
                int base = indirectCursor[slot];
                MemorySegment segment = MemorySegment.ofAddress(indirectBuffers[slot].mappedAddress())
                        .reinterpret(INDIRECT_BYTES);
                for (int i = 0; i < count; i++) {
                    int command = (index + i) * 4;
                    long offset = (long) (base + i) * 16L;
                    segment.set(ValueLayout.JAVA_INT, offset, packed[command]);
                    segment.set(ValueLayout.JAVA_INT, offset + 4L, 1);
                    segment.set(ValueLayout.JAVA_INT, offset + 8L, packed[command + 1] * 6);
                    segment.set(ValueLayout.JAVA_INT, offset + 12L, packed[command + 2]);
                }
                VK10.vkCmdDrawIndirect(cmd, indirectBuffers[slot].buffer(), (long) base * 16L, count, 16);
                indirectCursor[slot] = base + count;
            } else {
                for (int i = index; i < end; i++) {
                    int command = i * 4;
                    VK10.vkCmdDraw(cmd, packed[command], 1, packed[command + 1] * 6,
                            packed[command + 2]);
                }
            }
            index = end;
        }
    }

    private boolean canCull() {
        return GPU_CULL && computeQueue && context.drawIndirectCountEnabled() && context.multiDrawIndirectEnabled()
                && context.drawIndirectFirstInstanceEnabled()
                && Integer.compareUnsigned(context.maxDrawIndirectCount(), MAX_SECTIONS) >= 0;
    }

    private static boolean supportsCompute(VulkanContext context) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(context.physicalDevice(), count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.malloc(count.get(0), stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(context.physicalDevice(), count, families);
            return (families.get(context.queueFamilyIndex()).queueFlags() & VK10.VK_QUEUE_COMPUTE_BIT) != 0;
        }
    }

    private static MemorySegment mapped(VulkanHostBuffer buffer, long bytes) {
        return MemorySegment.ofAddress(buffer.mappedAddress()).reinterpret(bytes);
    }

    private void uploadFrameDescriptors(int slot) {
        if (uploadedDescVersions[slot] == descVersion) {
            return;
        }
        // prepareFrame stages the latest residency. Each slot keeps its own snapshot until
        // the scheduler has waited for that slot, including when another frame changes the store.
        long bytes = descCount * 48L;
        mapped(frameDescs[slot], bytes).copyFrom(mapped(descBuffer, bytes));
        if (rangesBuffer != null) {
            mapped(frameRanges[slot], bytes).copyFrom(mapped(rangesBuffer, bytes));
        }
        uploadedDescVersions[slot] = descVersion;
    }

    /**
     * Records the main-view cull outside rendering, before the terrain passes in this command
     * buffer. The caller has acquired the scheduler slot for recording.
     */
    public synchronized void recordCullDispatch(VkCommandBuffer commandBuffer, int frameSlot) {
        if (!canCull() || frustum == null || descStore != store || descCount == 0) {
            drawStage.disarmIndirect();
            return;
        }
        int slot = Math.floorMod(frameSlot, FRAME_SLOTS);
        if (drawStage.indirectArmed(frameId) && indirectSlot == slot
                && indirectCommandBuffer == commandBuffer.address()) {
            return;
        }
        uploadFrameDescriptors(slot);
        ensureCullResources(slot);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long commands = cullCommands[slot].buffer();
            long counters = cullCounters[slot].buffer();
            VK10.vkCmdFillBuffer(commandBuffer, counters, 0, COUNTER_BYTES, 0);
            VkBufferMemoryBarrier.Buffer reset = VkBufferMemoryBarrier.calloc(1, stack);
            bufferBarrier(reset.get(0), counters, COUNTER_BYTES, VK10.VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK10.VK_ACCESS_SHADER_READ_BIT | VK10.VK_ACCESS_SHADER_WRITE_BIT);
            VK10.vkCmdPipelineBarrier(commandBuffer, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, null, reset, null);

            HiZImage source = pyramid != null && pyramid.built ? pyramid : dummyDepth;
            source.toGeneral(commandBuffer, stack);
            if (cullImages[slot] != source) {
                long set = cullProgram.sets[slot];
                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(5, stack);
                writes.put(0, bufferWrite(stack, set, 0, frameDescs[slot].buffer(), DESC_BYTES));
                writes.put(1, bufferWrite(stack, set, 1, commands, CULL_COMMAND_BYTES));
                writes.put(2, bufferWrite(stack, set, 2, counters, COUNTER_BYTES));
                imageWrite(stack, writes.get(3), set, 3, VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                        source.fullView, hiZSampler);
                writes.put(4, bufferWrite(stack, set, 4, frameRanges[slot].buffer(), DESC_BYTES));
                VK10.vkUpdateDescriptorSets(context.device(), writes, null);
                source.retain();
                if (cullImages[slot] != null) {
                    cullImages[slot].release();
                }
                cullImages[slot] = source;
            }
            cullProgram.bind(commandBuffer, slot, stack);
            ByteBuffer push = stack.malloc(PUSH_BYTES);
            for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
                for (int component = 0; component < 4; component++) {
                    push.putFloat(frustum.coefficient(plane, component));
                }
            }
            push.putInt(cameraSection.x()).putInt(cameraSection.y()).putInt(cameraSection.z()).putInt(descCount);
            push.putFloat(cameraSection.fracX()).putFloat(cameraSection.fracY()).putFloat(cameraSection.fracZ());
            push.putInt(MAX_SECTIONS).flip();
            VK10.vkCmdPushConstants(commandBuffer, cullProgram.layout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            VK10.vkCmdDispatch(commandBuffer, (descCount + 63) / 64, 1, 1);

            VkBufferMemoryBarrier.Buffer ready = VkBufferMemoryBarrier.calloc(2, stack);
            bufferBarrier(ready.get(0), commands, CULL_COMMAND_BYTES,
                    VK10.VK_ACCESS_SHADER_WRITE_BIT, VK10.VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            bufferBarrier(ready.get(1), counters, COUNTER_BYTES,
                    VK10.VK_ACCESS_SHADER_WRITE_BIT, VK10.VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            VK10.vkCmdPipelineBarrier(commandBuffer, VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK10.VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT, 0, null, ready, null);
        }
        indirectCommandBuffer = commandBuffer.address();
        indirectSlot = slot;
        drawStage.armIndirect(frameId);
    }

    private static void bufferBarrier(VkBufferMemoryBarrier barrier, long buffer, long bytes,
                                      int srcAccess, int dstAccess) {
        barrier.sType$Default().buffer(buffer).offset(0).size(bytes)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .srcAccessMask(srcAccess).dstAccessMask(dstAccess);
    }

    @Override
    public void drawPass(VulkanCommandStream stream, TerrainPassKind kind, int layer) {
        if (!context.drawIndirectCountEnabled()) {
            return;
        }
        ShellStream shell = (ShellStream) stream;
        int slot = shell.slot();
        for (int streamIndex = 0; streamIndex < 2; streamIndex++) {
            shell.bindTerrainProgram(streamIndex == 0 ? axisPrograms[kind.ordinal()] : generalPrograms[kind.ordinal()]);
            int region = layer * 2 + streamIndex;
            VK12.vkCmdDrawIndirectCount(shell.commandBuffer(), cullCommands[slot].buffer(),
                    (long) region * MAX_SECTIONS * COMMAND_STRIDE, cullCounters[slot].buffer(),
                    (long) region * Integer.BYTES, MAX_SECTIONS, COMMAND_STRIDE);
        }
    }

    public synchronized boolean hiZValid() {
        return hiZValid;
    }

    public synchronized int hiZGuardCount() {
        return hiZGuardCount;
    }

    /**
     * Records a depth seed and its MAX-reduced mips outside rendering, after the frame's
     * depth writes. Call once per frame on the acquired scheduler slot.
     */
    public synchronized void recordHiZBuild(VkCommandBuffer commandBuffer, FrameImageTarget target, int frameSlot) {
        if (!HIZ || !computeQueue || !(target instanceof GllessFrameTarget owned)
                || owned.width() <= 0 || owned.height() <= 0 || target.depthImage() == VK10.VK_NULL_HANDLE) {
            return;
        }
        int slot = Math.floorMod(frameSlot, FRAME_SLOTS);
        int width = owned.width();
        int height = owned.height();
        int depthFormat = VulkanFormatDepth();
        ensureHiZResources(slot, owned, depthFormat);
        int depthLayout = target.depthLayout();
        int depthAspect = depthAspectMask(depthFormat);
        int compute = VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
        int allCommands = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        int shaderRead = VK10.VK_ACCESS_SHADER_READ_BIT;
        int shaderReadWrite = shaderRead | VK10.VK_ACCESS_SHADER_WRITE_BIT;
        int memoryReadWrite = VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT;
        int general = VK10.VK_IMAGE_LAYOUT_GENERAL;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // This also orders depth writes when the target already rests in GENERAL.
            imageBarrier(commandBuffer, stack, target.depthImage(), depthAspect, 1,
                    depthLayout, general, allCommands, compute, memoryReadWrite, shaderRead);
            if (pyramid.inGeneral) {
                imageBarrier(commandBuffer, stack, pyramid.image, VK10.VK_IMAGE_ASPECT_COLOR_BIT, pyramid.levels,
                        general, general, compute, compute, shaderReadWrite, shaderReadWrite);
            } else {
                pyramid.toGeneral(commandBuffer, stack);
            }
            ByteBuffer push = stack.malloc(Integer.BYTES);
            for (int level = 0; level < pyramid.levels; level++) {
                hiZProgram.bind(commandBuffer, slot * MAX_HIZ_LEVELS + level, stack);
                push.putInt(0, level);
                VK10.vkCmdPushConstants(commandBuffer, hiZProgram.layout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
                int w = Math.max(1, width >> level);
                int h = Math.max(1, height >> level);
                VK10.vkCmdDispatch(commandBuffer, (w + 7) / 8, (h + 7) / 8, 1);
                if (level + 1 < pyramid.levels) {
                    imageBarrier(commandBuffer, stack, pyramid.image, VK10.VK_IMAGE_ASPECT_COLOR_BIT, pyramid.levels,
                            general, general, compute, compute, shaderReadWrite, shaderReadWrite);
                }
            }
            imageBarrier(commandBuffer, stack, target.depthImage(), depthAspect, 1,
                    general, depthLayout, compute, allCommands, shaderRead, memoryReadWrite);
        }
        pyramid.built = true;
        hiZValid = !hiZGuarded;
    }

    private static int depthAspectMask(int format) {
        return switch (format) {
            case VK10.VK_FORMAT_D16_UNORM_S8_UINT, VK10.VK_FORMAT_D24_UNORM_S8_UINT, VK10.VK_FORMAT_D32_SFLOAT_S8_UINT ->
                    VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
            default -> VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
        };
    }

    private static void imageBarrier(VkCommandBuffer cmd, MemoryStack stack, long image, int aspect, int levels,
                                     int oldLayout, int newLayout, int srcStage, int dstStage,
                                     int srcAccess, int dstAccess) {
        VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(1, stack);
        VkImageMemoryBarrier barrier = barriers.get(0).sType$Default().image(image)
                .oldLayout(oldLayout).newLayout(newLayout).srcAccessMask(srcAccess).dstAccessMask(dstAccess)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
        barrier.subresourceRange().set(aspect, 0, levels, 0, 1);
        VK10.vkCmdPipelineBarrier(cmd, srcStage, dstStage, 0, null, null, barriers);
    }

    private void ensureCullResources(int slot) {
        int storage = VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        if (cullProgram == null) {
            ensureHiZSampler();
            int depthFormat = VulkanFormatDepth();
            if (dummyDepth == null) {
                dummyDepth = new HiZImage(1, 1, 1, depthFormat,
                        VK10.VK_IMAGE_ASPECT_DEPTH_BIT, depthAspectMask(depthFormat),
                        VK10.VK_IMAGE_USAGE_SAMPLED_BIT | VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
            }
            cullProgram = new ComputeProgram(Lane1Shaders.CULL_COMPUTE, PUSH_BYTES, FRAME_SLOTS,
                    storage, storage, storage, VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, storage);
        }
        int indirectStorage = VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
        if (cullCommands[slot] == null) {
            cullCommands[slot] = new VulkanHostBuffer(context, CULL_COMMAND_BYTES, indirectStorage);
        }
        if (cullCounters[slot] == null) {
            cullCounters[slot] = new VulkanHostBuffer(context, COUNTER_BYTES, indirectStorage);
        }
    }

    private void ensureHiZSampler() {
        if (hiZSampler == VK10.VK_NULL_HANDLE) {
            // Sampled depth formats need not support linear filtering.
            hiZSampler = createSampler(VK10.VK_FILTER_NEAREST, true, VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST);
        }
    }

    private void ensureHiZResources(int slot, GllessFrameTarget target, int depthFormat) {
        int width = target.width();
        int height = target.height();
        long depthImage = target.depthImage();
        ensureHiZSampler();
        if (hiZProgram == null) {
            hiZProgram = new ComputeProgram(Lane1Shaders.HIZ_COMPUTE, Integer.BYTES, FRAME_SLOTS * MAX_HIZ_LEVELS,
                    VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
        }
        if (pyramid == null || width != pyramid.width || height != pyramid.height) {
            // The helper counts the levels from the half-resolution reduction. Include the full-resolution seed.
            int levels = width == 1 && height == 1 ? 1 : 1 + TerrainCullStage.hiZPyramidLevels(width, height);
            HiZImage next = new HiZImage(width, height, levels, VK10.VK_FORMAT_R32_SFLOAT,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT, VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                    VK10.VK_IMAGE_USAGE_SAMPLED_BIT | VK10.VK_IMAGE_USAGE_STORAGE_BIT);
            if (pyramid != null) {
                pyramid.release();
            }
            pyramid = next;
            hiZValid = false;
        }
        HiZSlot bound = hiZSlots[slot];
        if (bound == null) {
            bound = new HiZSlot();
            hiZSlots[slot] = bound;
        }
        boolean sameDepth = bound.target == target && bound.depthImage == depthImage;
        if (sameDepth && bound.pyramid == pyramid) {
            return;
        }
        if (!sameDepth) {
            long depthView = createImageView(depthImage, depthFormat, VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1);
            VK10.vkDestroyImageView(context.device(), bound.depthView, null);
            bound.depthView = depthView;
            bound.depthImage = depthImage;
            bound.target = target;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(pyramid.levels * 2, stack);
            for (int level = 0; level < pyramid.levels; level++) {
                long set = hiZProgram.sets[slot * MAX_HIZ_LEVELS + level];
                imageWrite(stack, writes.get(level * 2), set, 0, VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                        level == 0 ? bound.depthView : pyramid.fullView, hiZSampler);
                imageWrite(stack, writes.get(level * 2 + 1), set, 1, VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                        pyramid.levelViews[level], VK10.VK_NULL_HANDLE);
            }
            VK10.vkUpdateDescriptorSets(context.device(), writes, null);
        }
        pyramid.retain();
        if (bound.pyramid != null) {
            bound.pyramid.release();
        }
        bound.pyramid = pyramid;
    }

    private static final class HiZSlot {
        FrameImageTarget target;
        long depthImage;
        long depthView;
        HiZImage pyramid;
    }

    private static void imageWrite(MemoryStack stack, VkWriteDescriptorSet write, long set, int binding,
                                   int type, long view, long sampler) {
        VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack);
        info.get(0).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL).imageView(view).sampler(sampler);
        write.sType$Default().dstSet(set).dstBinding(binding).descriptorType(type)
                .descriptorCount(1).pImageInfo(info);
    }

    private long createImageView(long image, int format, int aspect, int baseLevel, int levels) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image).format(format).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);
            info.subresourceRange().set(aspect, baseLevel, levels, 0, 1);
            LongBuffer out = stack.mallocLong(1);
            check(VK10.vkCreateImageView(context.device(), info, null, out), "create lane1 compute image view");
            return out.get(0);
        }
    }

    /** The active image and each slot's descriptor bindings hold references until replaced. */
    private final class HiZImage {
        private final int width;
        private final int height;
        private final int levels;
        private final int barrierAspect;
        private final long[] levelViews;
        private long image;
        private long fullView;
        private SlabAllocator.SlabAllocation allocation;
        private int references = 1;
        private boolean inGeneral;
        private boolean built;

        HiZImage(int width, int height, int levels, int format, int viewAspect, int barrierAspect, int usage) {
            this.width = width;
            this.height = height;
            this.levels = levels;
            this.barrierAspect = barrierAspect;
            this.levelViews = new long[(usage & VK10.VK_IMAGE_USAGE_STORAGE_BIT) != 0 ? levels : 0];
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkImageCreateInfo info = VkImageCreateInfo.calloc(stack).sType$Default()
                        .imageType(VK10.VK_IMAGE_TYPE_2D).format(format).mipLevels(levels).arrayLayers(1)
                        .samples(VK10.VK_SAMPLE_COUNT_1_BIT).tiling(VK10.VK_IMAGE_TILING_OPTIMAL).usage(usage)
                        .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                info.extent().set(width, height, 1);
                LongBuffer out = stack.mallocLong(1);
                check(VK10.vkCreateImage(context.device(), info, null, out), "create lane1 compute image");
                image = out.get(0);
                VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
                VK10.vkGetImageMemoryRequirements(context.device(), image, requirements);
                SlabAllocator allocator = context.celtium$deviceLocalImageSlabs(requirements.memoryTypeBits());
                allocation = allocator.allocate(requirements.size(), requirements.alignment());
                allocator.bindImage(image, allocation);
                fullView = createImageView(image, format, viewAspect, 0, levels);
                for (int i = 0; i < levelViews.length; i++) {
                    levelViews[i] = createImageView(image, format, viewAspect, i, 1);
                }
            } catch (RuntimeException | LinkageError error) {
                release();
                throw error;
            }
        }

        void toGeneral(VkCommandBuffer cmd, MemoryStack stack) {
            if (!inGeneral) {
                imageBarrier(cmd, stack, image, barrierAspect, levels, VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                        VK10.VK_IMAGE_LAYOUT_GENERAL, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                        VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0,
                        VK10.VK_ACCESS_SHADER_READ_BIT | VK10.VK_ACCESS_SHADER_WRITE_BIT);
                inGeneral = true;
            }
        }

        void retain() {
            references++;
        }

        void release() {
            if (--references == 0) {
                for (long view : levelViews) {
                    VK10.vkDestroyImageView(context.device(), view, null);
                }
                VK10.vkDestroyImageView(context.device(), fullView, null);
                VK10.vkDestroyImage(context.device(), image, null);
                if (allocation != null) {
                    allocation.owner().release(allocation, context.celtium$slabReleaseValue());
                }
            }
        }
    }

    private final class ComputeProgram {
        private final long[] sets;
        private long setLayout;
        private long pool;
        private long layout;
        private long pipeline;

        ComputeProgram(String source, int pushBytes, int setCount, int... types) {
            sets = new long[setCount];
            long module = shaderModule(words(ModShaderCompiler.compileCompute(source)));
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(types.length, stack);
                int[] counts = new int[VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER + 1];
                for (int i = 0; i < types.length; i++) {
                    bindings.get(i).binding(i).descriptorCount(1).descriptorType(types[i])
                            .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
                    counts[types[i]] += setCount;
                }
                VkDescriptorSetLayoutCreateInfo setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType$Default().pBindings(bindings);
                LongBuffer out = stack.mallocLong(1);
                check(VK10.vkCreateDescriptorSetLayout(context.device(), setInfo, null, out),
                        "create lane1 compute set layout");
                setLayout = out.get(0);

                VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(types.length, stack);
                int sizeCount = 0;
                for (int type = 0; type < counts.length; type++) {
                    if (counts[type] != 0) {
                        sizes.get(sizeCount++).type(type).descriptorCount(counts[type]);
                    }
                }
                sizes.limit(sizeCount);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType$Default().maxSets(setCount).pPoolSizes(sizes);
                check(VK10.vkCreateDescriptorPool(context.device(), poolInfo, null, out),
                        "create lane1 compute descriptor pool");
                pool = out.get(0);
                LongBuffer layouts = stack.mallocLong(setCount);
                for (int i = 0; i < setCount; i++) {
                    layouts.put(i, setLayout);
                }
                VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default().descriptorPool(pool).pSetLayouts(layouts);
                LongBuffer allocated = stack.mallocLong(setCount);
                check(VK10.vkAllocateDescriptorSets(context.device(), allocate, allocated),
                        "allocate lane1 compute descriptor sets");
                allocated.get(sets);

                VkPushConstantRange.Buffer push = VkPushConstantRange.calloc(1, stack);
                push.get(0).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(pushBytes);
                VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType$Default().pSetLayouts(stack.longs(setLayout)).pPushConstantRanges(push);
                check(VK10.vkCreatePipelineLayout(context.device(), layoutInfo, null, out),
                        "create lane1 compute pipeline layout");
                layout = out.get(0);
                VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0).sType$Default().layout(layout);
                pipelineInfo.get(0).stage().sType$Default().stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(module).pName(stack.UTF8("main"));
                check(VK10.vkCreateComputePipelines(context.device(), VK10.VK_NULL_HANDLE, pipelineInfo, null, out),
                        "create lane1 compute pipeline");
                pipeline = out.get(0);
            } catch (RuntimeException | LinkageError error) {
                delete();
                throw error;
            } finally {
                VK10.vkDestroyShaderModule(context.device(), module, null);
            }
        }

        void bind(VkCommandBuffer commandBuffer, int set, MemoryStack stack) {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE,
                    layout, 0, stack.longs(sets[set]), null);
        }

        void delete() {
            VK10.vkDestroyPipeline(context.device(), pipeline, null);
            VK10.vkDestroyPipelineLayout(context.device(), layout, null);
            VK10.vkDestroyDescriptorPool(context.device(), pool, null);
            VK10.vkDestroyDescriptorSetLayout(context.device(), setLayout, null);
        }
    }

    /** A command stream whose rendering bracket is a no-op: the backend's shell owns the pass. */
    private final class ShellStream implements VulkanCommandStream {
        private final VkCommandBuffer commandBuffer;
        private final long renderPass;
        private final int slot;
        private final int kindOrdinal;

        ShellStream(VkCommandBuffer commandBuffer, long renderPass, int slot, int kindOrdinal) {
            this.commandBuffer = commandBuffer;
            this.renderPass = renderPass;
            this.slot = slot;
            this.kindOrdinal = kindOrdinal;
        }

        VkCommandBuffer commandBuffer() {
            return commandBuffer;
        }

        int slot() {
            return slot;
        }

        @Override
        public long nativeCommandBuffer() {
            return commandBuffer.address();
        }

        @Override
        public void beginRendering(ActiveRenderTargets targets) {
            // The frame render pass is already open (backend shell); nothing to begin.
        }

        @Override
        public void endRendering() {
            // The backend shell ends the frame render pass after the terrain layers.
        }

        @Override
        public void bindTerrainProgram(TerrainStageProgram program) {
            boolean general = program == generalPrograms[kindOrdinal];
            long pipeline = pipelineFor(renderPass, kindOrdinal, general, program);
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                java.nio.LongBuffer set = stack.longs(sets[slot][kindOrdinal][general ? 1 : 0]);
                VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout, 0, set, null);
                ByteBuffer push = stack.malloc(PUSH_BYTES);
                writePushConstants(push);
                VK10.vkCmdPushConstants(commandBuffer, pipelineLayout,
                        VK10.VK_SHADER_STAGE_VERTEX_BIT, 0, push);
            }
        }
    }

    private void writePushConstants(ByteBuffer push) {
        FloatBuffer floats = push.asFloatBuffer();
        floats.put(viewProj, 0, 16);
        IntBuffer ints = push.asIntBuffer();
        ints.position(16);
        ints.put(cameraSection.x()).put(cameraSection.y()).put(cameraSection.z()).put(0);
        ints.put(gridOriginX).put(gridOriginY).put(gridOriginZ).put(0);
        ints.position(24);
        ints.put(lightCount);
        floats.position(25);
        floats.put(atlasCellsAcross);
        ints.position(26);
        ints.put(textureCellCount).put(0);
        floats.position(28);
        floats.put(cameraSection.fracX()).put(cameraSection.fracY()).put(cameraSection.fracZ()).put(0.0f);
        push.position(0);
    }

    // ------------------------------------------------------------------
    // Programs, pipelines, descriptors
    // ------------------------------------------------------------------

    private void buildPrograms() {
        int[] axisWords = words(ModShaderCompiler.compile(Lane1Shaders.AXIS_VERTEX, true));
        int[] generalWords = words(ModShaderCompiler.compile(Lane1Shaders.GENERAL_VERTEX, true));
        int[] fragmentWords = words(ModShaderCompiler.compile(Lane1Shaders.TERRAIN_FRAGMENT, false));
        int[] generalFragmentWords = words(ModShaderCompiler.compile(Lane1Shaders.GENERAL_FRAGMENT, false));
        AttachmentFormatKey formats = AttachmentFormatKey.of(
                new int[]{context.celtium$frameColorFormat()}, VulkanFormatDepth(), 0);
        DescriptorBindingPlan plan = bindingPlan();
        for (int kind = 0; kind < KIND_COUNT; kind++) {
            TerrainPassKind passKind = TerrainPassKind.values()[kind];
            boolean translucent = kind == 2;
            BlendStateSpec blend = BlendStateSpec.uniform(
                    translucent ? AttachmentBlendSpec.alphaBlend() : AttachmentBlendSpec.opaque(), 1);
            axisPrograms[kind] = new TerrainStageProgram(passKind,
                    SpirvModuleBundle.of(axisWords, fragmentWords), formats, blend,
                    alphaCutoff(kind), !translucent, false, plan);
            generalPrograms[kind] = new TerrainStageProgram(passKind,
                    SpirvModuleBundle.of(generalWords, generalFragmentWords), formats, blend,
                    alphaCutoff(kind), !translucent, false, plan);
        }
    }

    private static int VulkanFormatDepth() {
        return org.soutalab.celtium.vulkan.VulkanDepthFormat.chosen();
    }

    private static DescriptorBindingPlan bindingPlan() {
        EnumSet<ShaderStage> vertex = EnumSet.of(ShaderStage.VERTEX);
        EnumSet<ShaderStage> fragment = EnumSet.of(ShaderStage.FRAGMENT);
        List<DescriptorBindingPlan.Binding> bindings = new ArrayList<>();
        bindings.add(new DescriptorBindingPlan.Binding(0, DescriptorBindingPlan.Kind.STORAGE_BUFFER, vertex, 1));
        bindings.add(new DescriptorBindingPlan.Binding(1, DescriptorBindingPlan.Kind.STORAGE_BUFFER, vertex, 1));
        bindings.add(new DescriptorBindingPlan.Binding(2, DescriptorBindingPlan.Kind.STORAGE_BUFFER, vertex, 1));
        bindings.add(new DescriptorBindingPlan.Binding(3, DescriptorBindingPlan.Kind.STORAGE_BUFFER, vertex, 1));
        bindings.add(new DescriptorBindingPlan.Binding(4, DescriptorBindingPlan.Kind.STORAGE_BUFFER, vertex, 1));
        bindings.add(new DescriptorBindingPlan.Binding(5, DescriptorBindingPlan.Kind.STORAGE_BUFFER, vertex, 1));
        bindings.add(new DescriptorBindingPlan.Binding(6, DescriptorBindingPlan.Kind.COMBINED_IMAGE_SAMPLER, fragment, 1));
        bindings.add(new DescriptorBindingPlan.Binding(7, DescriptorBindingPlan.Kind.COMBINED_IMAGE_SAMPLER, fragment, 1));
        bindings.add(new DescriptorBindingPlan.Binding(8, DescriptorBindingPlan.Kind.UNIFORM_BUFFER, fragment, 1));
        bindings.add(new DescriptorBindingPlan.Binding(9, DescriptorBindingPlan.Kind.STORAGE_BUFFER, vertex, 1));
        return new DescriptorBindingPlan(bindings);
    }

    private static int[] words(byte[] spirv) {
        int[] words = new int[spirv.length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(spirv).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.asIntBuffer().get(words);
        return words;
    }

    private long shaderModule(int[] spirvWords) {
        long key = (long) spirvWords.hashCode() << 32 | spirvWords.length;
        Long cached = shaderModules.get(key);
        if (cached != null) {
            return cached;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer code = stack.malloc(spirvWords.length * 4);
            code.asIntBuffer().put(spirvWords);
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(code);
            java.nio.LongBuffer out = stack.mallocLong(1);
            check(VK10.vkCreateShaderModule(context.device(), info, null, out), "create terrain shader module");
            long module = out.get(0);
            shaderModules.put(key, module);
            return module;
        }
    }

    private long pipelineFor(long renderPass, int kindOrdinal, boolean general,
                             TerrainStageProgram program) {
        PipelineKey key = new PipelineKey(renderPass, kindOrdinal, general);
        Long cached = pipelines.get(key);
        if (cached != null) {
            return cached;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .module(shaderModule(program.spirv().vertexWords()))
                    .pName(stack.UTF8("main"));
            stages.get(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(shaderModule(program.spirv().fragmentWords()))
                    .pName(stack.UTF8("main"));
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo
                    .calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo
                    .calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo
                    .calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo
                    .calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                    // Axis stream: back-face culling is on - the vertex stage mirrors the
                    // corner U by word0 bit 20 (negativeFacing), so every packed quad winds
                    // CCW from its outward side (transmission item 14). The general stream
                    // carries arbitrary model geometry and stays double-sided.
                    .cullMode(general ? VK10.VK_CULL_MODE_NONE : VK10.VK_CULL_MODE_BACK_BIT)
                    .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo
                    .calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo
                    .calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(program.depthWrite())
                    .depthCompareOp(VK10.VK_COMPARE_OP_LESS_OR_EQUAL)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);
            AttachmentBlendSpec blendSpec = program.blendSpec().forAttachment(0);
            VkPipelineColorBlendAttachmentState.Buffer attachment =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            attachment.get(0)
                    .blendEnable(blendSpec.blendEnabled())
                    .srcColorBlendFactor(blendFactor(blendSpec.srcColorFactor()))
                    .dstColorBlendFactor(blendFactor(blendSpec.dstColorFactor()))
                    .colorBlendOp(blendOp(blendSpec.colorOp()))
                    .srcAlphaBlendFactor(blendFactor(blendSpec.srcAlphaFactor()))
                    .dstAlphaBlendFactor(blendFactor(blendSpec.dstAlphaFactor()))
                    .alphaBlendOp(blendOp(blendSpec.alphaOp()))
                    .colorWriteMask(blendSpec.colorWriteMask());
            VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo
                    .calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .pAttachments(attachment);
            IntBuffer dynamicStates = stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT,
                    VK10.VK_DYNAMIC_STATE_SCISSOR);
            org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamic =
                    org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                            .pDynamicStates(dynamicStates);
            VkGraphicsPipelineCreateInfo.Buffer createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            createInfo.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(assembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(raster)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depth)
                    .pColorBlendState(blend)
                    .pDynamicState(dynamic)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0);
            java.nio.LongBuffer out = stack.mallocLong(1);
            check(VK10.vkCreateGraphicsPipelines(context.device(), VK10.VK_NULL_HANDLE,
                    createInfo, null, out), "create lane1 terrain pipeline");
            long pipeline = out.get(0);
            pipelines.put(key, pipeline);
            return pipeline;
        }
    }

    private static int blendFactor(BlendFactor factor) {
        return switch (factor) {
            case ZERO -> VK10.VK_BLEND_FACTOR_ZERO;
            case ONE -> VK10.VK_BLEND_FACTOR_ONE;
            case SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR;
            case ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case SRC_ALPHA_SATURATE -> VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            case CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            case SRC1_COLOR -> VK10.VK_BLEND_FACTOR_SRC1_COLOR;
            case ONE_MINUS_SRC1_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC1_COLOR;
            case SRC1_ALPHA -> VK10.VK_BLEND_FACTOR_SRC1_ALPHA;
            case ONE_MINUS_SRC1_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC1_ALPHA;
        };
    }

    private static int blendOp(BlendOp op) {
        return switch (op) {
            case ADD -> VK10.VK_BLEND_OP_ADD;
            case SUBTRACT -> VK10.VK_BLEND_OP_SUBTRACT;
            case REVERSE_SUBTRACT -> VK10.VK_BLEND_OP_REVERSE_SUBTRACT;
            case MIN -> VK10.VK_BLEND_OP_MIN;
            case MAX -> VK10.VK_BLEND_OP_MAX;
        };
    }

    private long createSetLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] bindings = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            VkDescriptorSetLayoutBinding.Buffer layoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(bindings.length, stack);
            for (int i = 0; i < bindings.length; i++) {
                int binding = bindings[i];
                boolean storage = binding <= 5 || binding == 9;
                layoutBindings.get(i)
                        .binding(binding)
                        .descriptorCount(1)
                        .descriptorType(storage
                                ? VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                                : binding <= 7
                                ? VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                                : VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .stageFlags(storage
                                ? VK10.VK_SHADER_STAGE_VERTEX_BIT
                                : VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            }
            VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(layoutBindings);
            java.nio.LongBuffer out = stack.mallocLong(1);
            check(VK10.vkCreateDescriptorSetLayout(context.device(), info, null, out),
                    "create lane1 set layout");
            return out.get(0);
        }
    }

    private long createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
            range.get(0)
                    .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(PUSH_BYTES);
            VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(setLayout))
                    .pPushConstantRanges(range);
            java.nio.LongBuffer out = stack.mallocLong(1);
            check(VK10.vkCreatePipelineLayout(context.device(), info, null, out),
                    "create lane1 pipeline layout");
            return out.get(0);
        }
    }

    private long createDescriptorPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int setCount = FRAME_SLOTS * KIND_COUNT * 2;
            VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(3, stack);
            sizes.get(0).type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(setCount * 7);
            sizes.get(1).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(setCount * 2);
            sizes.get(2).type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(setCount);
            VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(setCount)
                    .pPoolSizes(sizes);
            java.nio.LongBuffer out = stack.mallocLong(1);
            check(VK10.vkCreateDescriptorPool(context.device(), info, null, out),
                    "create lane1 descriptor pool");
            return out.get(0);
        }
    }

    private void allocateSets() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int setCount = FRAME_SLOTS * KIND_COUNT * 2;
            java.nio.LongBuffer layouts = stack.mallocLong(setCount);
            for (int i = 0; i < setCount; i++) {
                layouts.put(i, setLayout);
            }
            VkDescriptorSetAllocateInfo info = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(layouts);
            java.nio.LongBuffer out = stack.mallocLong(setCount);
            check(VK10.vkAllocateDescriptorSets(context.device(), info, out), "allocate lane1 sets");
            int index = 0;
            for (int slot = 0; slot < FRAME_SLOTS; slot++) {
                for (int kind = 0; kind < KIND_COUNT; kind++) {
                    for (int stream = 0; stream < 2; stream++) {
                        sets[slot][kind][stream] = out.get(index++);
                    }
                }
            }
        }
    }

    private void writeBufferDescriptors() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            List<VkWriteDescriptorSet> writes = new ArrayList<>();
            for (int slot = 0; slot < FRAME_SLOTS; slot++) {
                for (int kind = 0; kind < KIND_COUNT; kind++) {
                    for (int stream = 0; stream < 2; stream++) {
                        long set = sets[slot][kind][stream];
                        writes.add(bufferWrite(stack, set, 0, frameDescs[slot].buffer(), DESC_BYTES));
                        writes.add(bufferWrite(stack, set, 1, stream == 0
                                ? axisSpace.bufferHandle() : generalSpace.bufferHandle(),
                                stream == 0 ? AXIS_SLAB_BYTES : GENERAL_SLAB_BYTES));
                        writes.add(bufferWrite(stack, set, 2, auxSpace.bufferHandle(), AUX_SLAB_BYTES));
                        writes.add(bufferWrite(stack, set, 3, textureCellBuffer.buffer(), TEXTURE_CELL_BYTES));
                        writes.add(bufferWrite(stack, set, 9, textureRectBuffer.buffer(), TEXTURE_RECT_BYTES));
                        writes.add(bufferWrite(stack, set, 4, lightBuffer.buffer(),
                                LightGridBuilder.UBO_BYTES));
                        writes.add(bufferWrite(stack, set, 5, maskBuffer.buffer(),
                                LightGridBuilder.CLUSTER_MASK_BYTES));
                        writes.add(bufferWrite(stack, set, 8, fogBuffers[kind].buffer(), FOG_BYTES));
                    }
                }
            }
            VkWriteDescriptorSet.Buffer buffer = VkWriteDescriptorSet.calloc(writes.size(), stack);
            for (int i = 0; i < writes.size(); i++) {
                buffer.put(i, writes.get(i));
            }
            VK10.vkUpdateDescriptorSets(context.device(), buffer, null);
        }
    }

    private VkWriteDescriptorSet bufferWrite(MemoryStack stack, long set, int binding,
                                             long bufferHandle, long range) {
        VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack);
        info.get(0).buffer(bufferHandle).offset(0L).range(range);
        VkWriteDescriptorSet write = VkWriteDescriptorSet.calloc(stack);
        write.sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(set)
                .dstBinding(binding)
                .descriptorCount(1)
                .descriptorType(binding == 8
                        ? VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
                        : VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .pBufferInfo(info);
        return write;
    }

    private void writeImageDescriptors(int slot, long atlasView, long lightmapView) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(KIND_COUNT * 2 * 2, stack);
            int index = 0;
            for (int kind = 0; kind < KIND_COUNT; kind++) {
                for (int stream = 0; stream < 2; stream++) {
                    for (int image = 0; image < 2; image++) {
                        VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack);
                        info.get(0)
                                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                                .imageView(image == 0 ? atlasView : lightmapView)
                                .sampler(image == 0 ? atlasSampler : lightmapSampler);
                        writes.get(index++)
                                .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                                .dstSet(sets[slot][kind][stream])
                                .dstBinding(6 + image)
                                .descriptorCount(1)
                                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                                .pImageInfo(info);
                    }
                }
            }
            VK10.vkUpdateDescriptorSets(context.device(), writes, null);
        }
    }

    private long createSampler(int filter, boolean mipmapped) {
        return createSampler(filter, mipmapped, mipmapped
                ? VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR : VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST);
    }

    private long createSampler(int filter, boolean mipmapped, int mipmapMode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(filter)
                    .minFilter(filter)
                    .mipmapMode(mipmapMode)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxLod(mipmapped ? 1000.0f : 0.0f);
            java.nio.LongBuffer out = stack.mallocLong(1);
            check(VK10.vkCreateSampler(context.device(), info, null, out), "create lane1 sampler");
            return out.get(0);
        }
    }

    private static void check(int result, String what) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException("Vulkan call failed (" + result + "): " + what);
        }
    }

    /** Destroys every Vulkan object this frontend owns. The device must be idle. */
    public synchronized void delete() {
        if (cullProgram != null) {
            cullProgram.delete();
        }
        if (hiZProgram != null) {
            hiZProgram.delete();
        }
        VK10.vkDestroySampler(context.device(), hiZSampler, null);
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            frameDescs[slot].delete();
            if (frameRanges[slot] != null) {
                frameRanges[slot].delete();
            }
            if (cullCommands[slot] != null) {
                cullCommands[slot].delete();
            }
            if (cullCounters[slot] != null) {
                cullCounters[slot].delete();
            }
            if (cullImages[slot] != null) {
                cullImages[slot].release();
            }
            if (hiZSlots[slot] != null) {
                VK10.vkDestroyImageView(context.device(), hiZSlots[slot].depthView, null);
                if (hiZSlots[slot].pyramid != null) {
                    hiZSlots[slot].pyramid.release();
                }
            }
        }
        if (pyramid != null) {
            pyramid.release();
        }
        if (dummyDepth != null) {
            dummyDepth.release();
        }
        if (rangesBuffer != null) {
            rangesBuffer.delete();
        }
        for (long pipeline : pipelines.values()) {
            VK10.vkDestroyPipeline(context.device(), pipeline, null);
        }
        pipelines.clear();
        for (long module : shaderModules.values()) {
            VK10.vkDestroyShaderModule(context.device(), module, null);
        }
        shaderModules.clear();
        VK10.vkDestroySampler(context.device(), atlasSampler, null);
        VK10.vkDestroySampler(context.device(), lightmapSampler, null);
        VK10.vkDestroyDescriptorPool(context.device(), descriptorPool, null);
        VK10.vkDestroyPipelineLayout(context.device(), pipelineLayout, null);
        VK10.vkDestroyDescriptorSetLayout(context.device(), setLayout, null);
        for (VulkanHostBuffer fog : fogBuffers) {
            fog.delete();
        }
        descBuffer.delete();
        textureCellBuffer.delete();
        textureRectBuffer.delete();
        for (VulkanHostBuffer indirect : indirectBuffers) {
            indirect.delete();
        }
        lightBuffer.delete();
        maskBuffer.delete();
        axisSpace.close();
        generalSpace.close();
        auxSpace.close();
    }
}
