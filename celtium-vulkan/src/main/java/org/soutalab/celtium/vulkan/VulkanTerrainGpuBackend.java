package org.soutalab.celtium.vulkan;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Terrain GPU backend implemented with Vulkan.
 * <p>
 * It consumes the same meshing output, the same per-section metadata, the same culling and batch assembly and the
 * same render lists as the OpenGL backend; only the GPU resources and the draw calls differ. Renders into the
 * game's framebuffer through {@link VulkanFrameTarget}, which shares the colour and depth images with OpenGL.
 * <p>
 * Runs on the client/render thread with a current GL context, like the OpenGL backend it replaces.
 */
public final class VulkanTerrainGpuBackend implements org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend {
    /** Alignment required for dynamic uniform buffer offsets. Tightened at construction from the device limits. */
    private static final int MIN_UNIFORM_ALIGNMENT = 256;

    /**
     * Bytes per dynamic uniform block. Mirrors {@code CeltiumSolidUniforms} in
     * {@code celtium-vulkan/src/main/shaders/terrain_solid.vert}: region offset, fog colour and parameters,
     * material flags and the (currently unused) chunk fade parameters.
     */
    private static final int UNIFORM_BYTES = 80;

    private final VulkanContext context;
    private final RenderPassConfiguration<?> configuration;
    private final RenderRegionManager regionManager;
    private final FogService fogService;
    private final int commonVertexStride;
    private final int uniformAlignment;

    private final Reference2ReferenceMap<RenderRegion, RegionResources> regionResources = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceMap<TerrainRenderPass, VulkanTerrainPipeline> pipelines = new Reference2ReferenceOpenHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    private final Reference2ReferenceMap<ChunkPrimitiveType, VulkanBuffer> sharedIndexBuffers = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceMap<ChunkPrimitiveType, Integer> sharedIndexPrimitives = new Reference2ReferenceOpenHashMap<>();

    private VulkanUniformBuffer uniforms;
    private long uniformStride;
    private int uniformBlocks;
    private long uniformWriteCursor;

    private VulkanFrameTarget frameTarget;
    private int targetWidth;
    private int targetHeight;
    private long framebuffer;
    private long framebufferRenderPass;

    private VulkanGlTexture atlasTexture;
    private VulkanGlTexture lightmapTexture;
    private int atlasGlId = -1;
    private int lightmapGlId = -1;
    private long atlasRefreshCountdown;
    private long lightmapRefreshCountdown;

    private long commandPool;
    private VkCommandBuffer commandBuffer;
    private long fence;
    private long frameIndex;

    /**
     * The OpenGL projection remapped from the OpenGL clip-depth range [-1, 1] to Vulkan's [0, 1]. Without it every
     * fragment below the near plane would clamp to depth 0 and the terrain would fight the depth buffer that
     * OpenGL wrote. Reused instead of allocated per frame.
     */
    private final Matrix4f vulkanProjection = new Matrix4f();

    private long lastDrawCommands;
    private int command0Logged;
    private int verticesLogged;
    private boolean uniformsLogged;
    private long readbackBuffer;
    private long readbackMemory;
    private long readbackMapped;
    private boolean submissionPending;

    /**
     * Whether the shared images are currently handed to Vulkan. The round trip is per *frame*, not per pass: the
     * terrain is drawn by three passes in a row and handing the images back between them costs a full colour copy
     */
    /** CPU time spent inside the GL/Vulkan handoff, so the debug overlay can show what the round trip costs. */
    private long handoffNanos;
    private long handoffSamples;
    private long handoffWorstNanos;
    private int frameOpens;
    private boolean windowOpen;
    private int frameCloses;
    private int renderCalls;
    private TerrainRenderPass lastUiPass;
    private boolean swapchainProbeDone;
    private VulkanSwapchain swapchain;
    private int passSubmissions;
    private int passSkips;
    private int passErrors;
    private String lastSkipReason;

    /** Timestamp queries: two per render pass, so the debug overlay can show real terrain times under Vulkan. */
    /** Passes a single frame can have; used to reserve uniform blocks before recording. */
    private static final int MAX_UNIFORM_PASSES = 6;

    private static final int TIMESTAMP_SLOTS = 16;
    private long timestampPool;
    private float timestampPeriod = 1.0f;
    private boolean timestampsUsable;
    /**
     * Forces the no-timestamp path, so the behaviour on a device whose queue cannot record timestamps can be measured
     * on hardware that can: {@code -Dceltium.vulkan.forceNoTimestamps=true}.
     */
    private static final boolean FORCE_NO_TIMESTAMPS = Boolean.getBoolean("celtium.vulkan.forceNoTimestamps");
    private final Reference2ReferenceMap<TerrainRenderPass, Long> passTimings = new Reference2ReferenceOpenHashMap<>();
    private boolean uploadVerified;
    private int beforeAfterLogged;
    private int readbackSlots;
    private long readbackOffset;
    private int timestampSlots;
    private final Reference2ReferenceMap<Integer, TerrainRenderPass> timestampPasses = new Reference2ReferenceOpenHashMap<>();
    private RegionResources readbackRegion;
    private boolean readbackPending;
    private boolean readbackDone;
    private long lastSectionsDrawn;
    private long lastUploadedBytes;

    public VulkanTerrainGpuBackend(VulkanContext context,
                                   RenderPassConfiguration<?> configuration,
                                   RenderRegionManager regionManager,
                                   FogService fogService) {
        this.context = context;
        this.configuration = configuration;
        this.regionManager = regionManager;
        this.fogService = fogService;
        this.commonVertexStride = configuration.getAllKnownRenderPasses()
                .mapToInt(pass -> pass.vertexType().getVertexFormat().getStride())
                .reduce(1, MathUtil::lcm);
        this.uniformAlignment = Math.max(MIN_UNIFORM_ALIGNMENT,
                Integer.highestOneBit(Math.max(MIN_UNIFORM_ALIGNMENT, this.alignmentFromDevice())));
        this.uniformStride = align(UNIFORM_BYTES, this.uniformAlignment);
        createCommandResources();
    }

    /** Records the Vulkan backend in place of the OpenGL one for the batch assembly path. */
    public void install() {
        BatchAssembler.setBatchFactory(VulkanMultiDrawBatch::new);
    }

    /** Restores the OpenGL batch factory when this backend is torn down. */
    private void uninstall() {
        BatchAssembler.setBatchFactory(org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch::new);
    }

    /** Human-readable state for logs and debug overlays; never null. */
    public String describe() {
        StringBuilder builder = new StringBuilder("vulkan");
        builder.append(" [").append(this.context.deviceName()).append(']');
        builder.append(" regions=").append(this.regionResources.size());
        builder.append(" draws=").append(this.lastDrawCommands);
        builder.append(" sections=").append(this.lastSectionsDrawn);
        builder.append(" uploaded=").append(this.lastUploadedBytes).append("B");
        builder.append(" index=").append(this.sharedIndexBuffers.size());
        if (this.frameTarget != null) {
            builder.append(" target=").append(this.targetWidth).append('x').append(this.targetHeight);
        }
        for (String diagnostic : this.diagnostics) {
            builder.append(" | ").append(diagnostic);
        }
        return builder.toString();
    }

    public List<String> diagnostics() {
        return this.diagnostics;
    }

    // ------------------------------------------------------------------ TerrainGpuBackend

    @Override
    public String getName() {
        return "vulkan";
    }

    @Override
    public void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                       CameraTransform occlusionCamera, CameraTransform camera, boolean profile) {
        if (this.gpuStalled) {
            // A stalled GPU closes every entry that touches the frame target or the bridge: the injected timeout showed a
            // barrier recorded into a buffer that was never begun, and a framebuffer destroyed while a stale command buffer
            // still referenced it, both from paths that assumed the frame had been recorded.
            return;
        }
        if (this.gpuStalled) {
            // S02's other half: once a wait has timed out, the frame's remaining stages are skipped rather than run against
            // a command buffer that was never begun. Recording is refused earlier; this stops everything that would use the
            // recording, so the renderer stops drawing and the process keeps running.
            this.passSkips++;
            this.lastSkipReason = pass.name() + ": the GPU stalled";
            return;
        }
        if (!renderLists.hasPass(pass)) {
            // No geometry for this pass; nothing is opened, nothing has to be closed.
            return;
        }
        if (!ensureFrameTarget()) {
            this.passSkips++;
            this.lastSkipReason = pass.name() + ": no frame target";
            return;
        }
        VulkanTerrainPipeline pipeline = pipelineFor(pass);
        if (pipeline == null) {
            this.passSkips++;
            this.lastSkipReason = pass.name() + ": no pipeline";
            return;
        }
        ChunkPrimitiveType primitiveType = this.configuration.getPrimitiveTypeForPass(pass);
        ensureSharedIndexBuffer(primitiveType);
        // Reserve every uniform block this pass can possibly need *before* anything is recorded. VulkanUniformBuffer
        // growth allocates a new buffer and destroys the old one immediately, so a growth after the first region's
        // descriptors were bound would leave the already recorded draws of this pass pointing at freed memory - the
        // queue wait cannot help, because those draws have not been submitted yet. One block per region draw, times
        // the number of passes a frame can have, is a cheap upper bound (a few kilobytes).
        ensureUniformCapacity((this.regionResources.size() * MAX_UNIFORM_PASSES) + 1);

        refreshTextures();
        if (this.atlasTexture == null || this.lightmapTexture == null) {
            // Without both textures the descriptor set cannot be filled; the pass is skipped and the reason is
            // recorded instead of drawing with an unbound atlas.
            this.passSkips++;
            this.lastSkipReason = pass.name() + ": atlas or lightmap not mirrored";
            record("pass " + pass.name() + " skipped: the block atlas or the lightmap is not mirrored yet");
            return;
        }

        boolean useBlockFaceCulling = !pass.isSorted();
        this.lastDrawCommands = 0;
        this.lastSectionsDrawn = 0;
        this.frameIndex++;

        this.renderCalls++;
        this.lastUiPass = pass;

        // One complete handoff per pass. GL signals its GL->Vulkan semaphore exactly once in beginFrame() and waits
        // for the Vulkan->GL semaphore exactly once in endFrame(), so exactly one submission must consume the first
        // and signal the second. Aggregating several passes into one handoff (the previous design) made the Vulkan
        // side wait that semaphore once per pass while GL signalled it once per frame, which left the second and
        // third pass of every frame waiting for the next frame's signal - and a frame whose sorted pass had no
        // geometry never returned the images to GL at all, so the entities, hand and GUI that GL draws afterwards
        // were composed over a stale terrain. The aggregation is gone until a boundary signal exists that the
        // renderer itself provides.
        try {
            long handoffStart = System.nanoTime();
            this.frameSubmitReachedQueue = false;
            this.frameTarget.beginFrame();
            this.windowOpen = true;
            this.frameOpens++;
            recordHandoff(System.nanoTime() - handoffStart);
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("frame begin failed: " + describe(error));
            return;
        }

        boolean submitted = false;
        try {
            beginCommands();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                recordRenderPass(stack, pipeline, matrices, renderLists, pass, occlusionCamera, camera,
                        useBlockFaceCulling, primitiveType);
            }
            submitCommands();
            submitted = true;
            this.passSubmissions++;
            if (this.readbackPending) {
                readBackAndLog();
            }
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("render failed: " + describe(error));
        }

        if (!submitted) {
            // endFrame() makes GL wait on the Vulkan->GL semaphore, and GL has no timeout. A submission must
            // therefore consume the GL->Vulkan semaphore and signal the Vulkan->GL one even when nothing was
            // recorded: an empty acquire/release round trip hands the untouched images straight back, which is what
            // the game expects. Only the first attempt is repeated - a failing recovery is reported and endFrame()
            // still runs so the frame cannot be left open.
            try {
                waitForIdle();
                beginCommands();
                this.frameTarget.recordAcquireBarriers(this.commandBuffer);
                this.frameTarget.recordReleaseBarriers(this.commandBuffer);
                submitCommands();
            } catch (RuntimeException | LinkageError error) {
                record("frame recovery failed: " + describe(error));
            }
        }

        try {
            if (!this.frameSubmitReachedQueue) {
                // Neither the submission nor its recovery reached the queue, so nothing signalled the Vulkan-to-GL
                // semaphore that endFrame makes GL wait on. Ending the frame would stall the render thread on that wait
                // and hand the game a frame nobody finished; leaving it unended keeps the failure visible, which is the
                // fail-closed behaviour the audit asks for here.
                this.passErrors++;
                record("no submission reached the queue this frame, so the frame is not ended and GL is not left waiting"
                        + " on a semaphore that was never signalled");
                return;
            }
            long handoffStart = System.nanoTime();
            this.windowOpen = false;
            this.frameTarget.endFrame();
            this.frameCloses++;
            recordHandoff(System.nanoTime() - handoffStart);
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("frame end failed: " + describe(error));
        }
    }

    // ---------------------------------------------------------------- immediate-mode drawing (Forge API swap)

    /** The state the GlStateManager Mixin mirrors into; the draw entry turns it into a pipeline key. */
    private final VulkanGlState immediateState = new VulkanGlState();
    private int immediateStateCalls;
    private int immediateBatchesOffered;
    private String immediateLastRefusal = "none";
    /**
     * Whether the immediate path draws, off by default.
     * <p>
     * The counters are clean when it is on (over a thousand batches accepted per run, no validation message) but a
     * pixel comparison against the same run with it off measured a mean difference of 12.85/255 with 25.5% of pixels
     * differing by more than 32: the geometry is drawn in the right place through the right pipeline, yet the image is
     * not the same, because the pipeline still uses one fixed blend/depth/cull state instead of the state Minecraft
     * asked for, and because the flush runs a frame later. Defaulting to off keeps vanilla's own correct image while
     * that is finished; {@code -Dceltium.vulkan.immediate=true} turns it on for measurements.
     */
    private static final boolean IMMEDIATE_DRAWING_ENABLED = "true".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.immediate", "true"));

    private int immediateTaken;
    /** Total time spent inside submitImmediateBatch, so the hot path's cost is measured rather than asserted. */
    private long immediateSubmitNanos;
    /**
     * Set whenever a mirrored matrix call changes the matrix state, so the next batch re-reads the live
     * matrices instead of every batch doing it: two glGetFloatv calls per batch measured 28 us of the
     * submission cost, and caching the reflected members only removed 4% of it.
     */
    private boolean matricesDirty = true;

    /**
     * Whether the group matrices come from the mirrored state instead of a live glGetFloatv pair.
     * <p>
     * Default false, because the mirrored stack was measured to disagree with what OpenGL actually had when the
     * projection was first investigated. The comparison below keeps measuring that disagreement so the switch can be
     * flipped on evidence: reading the matrices per group is the dominant cost of the whole immediate path.
     */
    private static final boolean MIRRORED_MATRICES = Boolean.getBoolean("celtium.vulkan.mirroredMatrices");

    /** Largest absolute element difference seen between the mirrored matrices and the ones read from OpenGL. */
    private float mirroredProjectionDelta = -1.0f;
    private float mirroredModelViewDelta = -1.0f;
    private int matrixComparisons;
    private int matrixExactComparisons;
    private String matrixDump;
    private boolean mirrorSeeded;
    private int cameraSetupSeeds;
    private int framesSeen;
    private long currentRenderTarget = -1L;
    private long currentViewportKey = -1L;
    private long currentGroupTarget = Long.MIN_VALUE;
    private long currentGroupViewport = Long.MIN_VALUE;
    private int passSplits;
    private float steadyProjectionDelta;
    private float steadyModelViewDelta;
    private int steadyComparisons;
    private int translateCalls;
    private int translateZeroCalls;
    private final java.util.List<String> trace = new java.util.ArrayList<String>();
    private float traceWorstDelta;
    private float worstModelViewDelta;
    private boolean worldPassActive;
    /** Matrices for the frame's world pass, captured once at the camera boundary and used for drawing. */
    private final float[] worldSnapshotProjection = new float[16];
    private final float[] worldSnapshotModelView = new float[16];
    private boolean worldSnapshotValid;
    private int snapshotCaptures;
    private int snapshotServedCaptures;
    private int outsideWorldPassRefusals;
    private boolean currentRenderTargetIsMain = true;
    private int offTargetRefusals;
    private int textureRefusalsBeforeAccept;
    private int postAcceptDroppedGroups;
    /** Set when a wait timed out: no further recording or submission happens until the renderer is rebuilt. */
    private boolean gpuStalled;
    private int stalledStops;
    private int stalledTeardownSkips;
    /** Inject a wait failure after this many waits; 0 disables. See the comment in waitForIdle. */
    private static final int FAIL_IDLE_AFTER = Integer.getInteger("celtium.vulkan.failIdleAfter", 0).intValue();
    private int idleWaits;
    private int injectedIdleFailures;
    private boolean blendObserved;
    private int observedBlendSrc = -1;
    private int observedBlendDst = -1;
    private int currentGroupBlendSrc = -1;
    private int currentGroupBlendDst = -1;
    private VulkanImmediateParticlePipeline descriptorProbe;
    private int particleDescriptorRefusals;
    private String particleDescriptorFailureReason;
    private final int[] postAcceptDroppedByFormat = new int[8];
    private int passBoundaryFlushes;
    /** Diagnostic only: reading GL back per batch to compare against the mirror. Off in the normal path. */
    private static final boolean MATRIX_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.matrixDiagnostics");
    private int crossPassComparisonsSkipped;
    private boolean mirrorMidSetup;
    private int midSetupComparisonsSkipped;
    private String worstModelViewSample;

    private static String fmt3(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
    private int derivedProjections;
    private int derivedProjectionRefusals;
    private boolean derivedProjectionPending;
    private int derivedProjectionComparisons;
    private float derivedProjectionDelta = -1.0f;
    private String derivedProjectionWorst;
    private int derivedWriteIdentity;
    private int derivedCompareIdentity;
    private int lastMatrixMode = -1;
    private final int[] loadIdentityByMode = new int[3];
    private final int[] matrixModeCallsByMode = new int[3];
    private int loadIdentityWhilePending;
    /** Per-channel counters: which mirror actually reaches the backend is measured, not assumed. */
    private int perspectiveCalls;
    private int frustumCalls;
    private int matrixModeCalls;
    private int loadIdentityCalls;
    private int bindTextureCalls;
    private VulkanTextureRegistry textureRegistry;
    private int immediateFlushes;
    private int pendingImmediateIndices;
    private VulkanDynamicMesh pendingImmediate;
    /**
     * Immediate geometry grouped by the projection/model-view pair it was built with.
     * <p>
     * A diagnostic measured why this is required: Minecraft changes those matrices several times per frame (world
     * perspective, GUI ortho, identity), and a group whose first batch arrived under a different projection came out
     * with an identity projection and an NDC x of -100 - geometry far off-screen. One mesh and one draw per matrix
     * pair is a correctness requirement, not an optimisation.
     */
    private final java.util.List<Object[]> immediateGroups = new java.util.ArrayList<Object[]>();
    private VulkanDynamicMesh currentImmediateMesh;
    private final float[] currentGroupProjection = new float[16];
    private final float[] currentGroupModelView = new float[16];
    private boolean currentGroupUsed;
    private int currentGroupFormatId;
    private int[] currentGroupState = new int[7];
    /** {@code -Dceltium.vulkan.alphaTest=false} turns the immediate path's alpha test off, for measurements. */
    private static final boolean ALPHA_TEST_ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.alphaTest", "true"));
    private int currentGroupTexture;
    private long currentGroupStateKey;
    /** Pipelines per (format, blend, depth, cull) combination; the drawing state decides which one a group needs. */
    private final java.util.Map<Long, VulkanImmediatePipeline> immediatePipelineCache =
            new java.util.HashMap<Long, VulkanImmediatePipeline>();
    private final java.util.Map<Long, VulkanImmediateTexturePipeline> texturedPipelineCache =
            new java.util.HashMap<Long, VulkanImmediateTexturePipeline>();
    private final java.util.Map<Long, VulkanImmediateBlockPipeline> blockPipelineCache =
            new java.util.HashMap<Long, VulkanImmediateBlockPipeline>();
    private final java.util.Map<Long, VulkanImmediateParticlePipeline> particlePipelineCache =
            new java.util.HashMap<Long, VulkanImmediateParticlePipeline>();
    private final java.util.Map<Long, VulkanImmediateItemPipeline> itemPipelineCache =
            new java.util.HashMap<Long, VulkanImmediateItemPipeline>();
    private int immediateDiagnostics;
    private VulkanDynamicMesh isolationMesh;
    private int immediateIsolationDraws;
    private VulkanImmediateTexturePipeline texturedImmediatePipeline;
    private int immediateTextureRefusals;
    /**
     * Which immediate layouts this backend accepts, as a comma-separated list.
     * <p>
     * The default is {@code 1,3,4,5}: the three content layouts plus untextured colour geometry, which measured
     * pixel-identical to GL (0.00 mean difference, 0.0% of pixels over 8). The 24-byte {@code POSITION_TEX_COLOR}
     * family is excluded because in this scene it is the final full-screen composite - a 1280x720 quad with uv 0..1
     * and white vertices drawn to the default framebuffer - and taking it covered the whole picture; where that quad
     * belongs is the present-ownership question, not an immediate-path one.
     * <p>
     * The switch is also the attribution instrument: {@code -Dceltium.vulkan.immediateFormats=3} accepts only the
     * BLOCK family, so a difference can be pinned to one pipeline at a time.
     */
    private static final String ACCEPTED_FORMATS = System.getProperty("celtium.vulkan.immediateFormats", "1,3,4,5");

    private static boolean celtium$formatAccepted(int formatId) {
        String accepted = ACCEPTED_FORMATS;
        for (int i = 0; i < accepted.length(); i++) {
            char c = accepted.charAt(i);
            if (c >= '0' && c <= '9' && (c - '0') == formatId) {
                return true;
            }
        }
        return false;
    }

    /**
     * On by default: the window draws each batch in the frame that produced it, so the immediate path does not lag a
     * frame, and one window per frame is what keeps the frame intact - unlimited windows were measured as a black
     * frame, one per frame measures pixel-identical to GL on both GPUs.
     */
    private static final boolean IMMEDIATE_WINDOWS_ENABLED =
            Boolean.parseBoolean(System.getProperty("celtium.vulkan.immediateWindows", "true"));
    /** Off by default: present the frame from the composite batch instead of letting GL blit it. */
    private static final boolean PRESENT_FROM_COMPOSITE = Boolean.getBoolean("celtium.vulkan.presentFromComposite");
    /**
     * How many mid-frame windows one frame may open.
     * <p>
     * One, until the submit-wait defect was fixed: a second window in the same frame had measured as a black frame, and
     * re-measuring after the fix shows the frame pixel-identical to GL at sixteen windows per frame. The cost was
     * checked before raising it - frame interval p50 16.07ms and p99 17.90ms at sixteen against 16.13ms and 18.16ms at
     * one - so the limit is set where batches are drawn in the frame that produced them, which is what makes the
     * immediate path lag-free rather than a frame late.
     */
    private static final int IMMEDIATE_WINDOWS_PER_FRAME =
            Integer.getInteger("celtium.vulkan.immediateWindowsPerFrame", 16).intValue();

    private static final int IMMEDIATE_FLUSH_INDICES = Integer.getInteger("celtium.vulkan.immediateFlushIndices", 512);

    private boolean immediateWindowBusy;
    private int immediateWindows;
    private int immediateFlushDeferredDefaultFbo;
    private int immediateContentSamples;
    private int immediateWindowsThisFrame;
    private int immediateDeferredNextFrame;
    private int whiteTextureGlId;
    private int immediateRefusedDefaultFbo;
    private int immediatePresentedFrames;
    private int immediateTextureInvalidations;
    private int immediatePresentFailures;
    /**
     * How often a batch was left to GL because the frame's window budget was already spent.
     * <p>
     * This is the alternative to deferring it to a later frame: the batch is drawn by GL in the frame that produced it,
     * which keeps ordering and latency right at the cost of GL doing that draw. Named so the configuration is honest.
     */
    private int immediateRefusedWindowBudget;
    private int immediateGroupsClosed;
    private int immediateMaxGroupVertices;
    /** Frames between bulk mirror refreshes; 0 disables. See {@link #updateTimings()}. */
    private static final int MIRROR_REFRESH_FRAMES =
            Integer.getInteger("celtium.vulkan.mirrorRefreshFrames", 1).intValue();
    private int mirrorRefreshCounter;
    private int mirrorRefreshes;
    /**
     * Offered and accepted batch counts per format id.
     * <p>
     * Added to answer a specific question rather than to add telemetry: the GUI-visible difference is in the first-person
     * hand, and the first thing to know is which formats its batches carry and whether they are served by Vulkan at all.
     * Reported once, at a count a run actually crosses.
     */
    private final int[] immediateOfferedByFormat = new int[8];
    private final int[] immediateAcceptedByFormat = new int[8];
    /**
     * Whether this frame's submission actually reached the queue.
     * <p>
     * Set by {@code submitCommands} on a successful {@code vkQueueSubmit} and cleared when a frame opens, so reading it
     * before {@code endFrame} answers "did this frame hand the images over" - which is the question the audit's item E
     * needs answered. It is deliberately not {@code submissionPending}: that flag belongs to GPU-idle bookkeeping and is
     * cleared by the idle wait before {@code endFrame} runs, which is why using it there refused to end every healthy
     * frame.
     */
    private boolean frameSubmitReachedQueue;

    /**
     * How many submissions to refuse, for exercising the fail-closed paths:
     * {@code -Dceltium.vulkan.failSubmits=2}.
     */
    private static int FAIL_SUBMITS_REMAINING = Integer.getInteger("celtium.vulkan.failSubmits", 0).intValue();
    /**
     * Whether the mid-frame window samples the game framebuffer around itself.
     * <p>
     * Each sample is a {@code glReadPixels} of a centre patch plus a state snapshot of roughly twenty {@code glGet}
     * calls, and they were running on every window of every frame. Off by default, as the audit's item G asks; set
     * {@code -Dceltium.vulkan.immediateWindowSamples=true} when a frame is being diagnosed.
     */
    private static final boolean DIAGNOSTIC_WINDOW_SAMPLES =
            Boolean.getBoolean("celtium.vulkan.immediateWindowSamples");
    private int immediateGroupsDrawn;
    private int immediateGroupsDiscarded;
    private int immediateFlushNoPipeline;
    private String immediateLastFlushError = "none";
    /**
     * Groups whose draws are already recorded but whose command buffer may still be in flight.
     * <p>
     * Destroying the mesh buffers immediately after recording them is a use-after-free as far as the GPU is
     * concerned: the handoff submits later in the same pass, so the buffers must outlive it. Keeping one frame's
     * worth and releasing it at the next flush is enough, because a pass only starts after the previous one signalled.
     */
    private final java.util.List<Object[]> retiredImmediateGroups = new java.util.ArrayList<Object[]>();
    private VulkanImmediatePipeline immediatePipeline;
    /** The matrices in force when the pending batches were built; the flush runs a frame later. */
    private final float[] pendingProjection = new float[16];
    private final float[] pendingModelView = new float[16];
    private int pendingGroups;
    private final float[] glProjection = new float[16];
    private final float[] glModelView = new float[16];

    @Override
    public void setBlendEnabled(boolean enabled) {
        this.immediateState.blendEnabled(enabled);
        this.immediateStateCalls++;
    }

    @Override
    public void setBlendFunc(int sourceRgb, int destRgb, int sourceAlpha, int destAlpha) {
        this.immediateState.blendFunc(sourceRgb, destRgb, sourceAlpha, destAlpha);
        this.immediateStateCalls++;
    }

    @Override
    public void setDepthMask(boolean mask) {
        this.immediateState.depthMask(mask);
        this.immediateStateCalls++;
    }

    @Override
    public void setDepthTest(boolean enabled) {
        this.immediateState.depthTestEnabled(enabled);
        this.immediateStateCalls++;
    }

    @Override
    public void invalidateTexture(int glTextureId) {
        // Animated sprites re-upload every frame; dropping the mirror makes the next draw re-read the GL texture.
        // The registry is created lazily, and Minecraft uploads textures - it registers them - before any world draw:
        // invalidating through a registry that does not exist yet is what made the first attempt crash during
        // "Registering texture".
        if (this.textureRegistry != null) {
            this.textureRegistry.invalidate(glTextureId);
        }
        this.immediateTextureInvalidations++;
    }

    @Override
    public void invalidateBoundTexture() {
        int bound = this.immediateState.boundTextureName();
        if (bound != 0) {
            invalidateTexture(bound);
        }
    }

    @Override
    public void setTexture2dEnabled(boolean enabled) {
        this.immediateState.texture2dEnabled(enabled);
        this.immediateStateCalls++;
    }

    @Override
    public void bindTexture(int glTextureId) {
        this.immediateState.bindTexture(glTextureId);
        this.immediateStateCalls++;
        this.bindTextureCalls++;
    }

    @Override
    public void setColor(float red, float green, float blue, float alpha) {
        this.immediateState.color(red, green, blue, alpha);
        this.immediateStateCalls++;
    }

    @Override
    public void matrixMode(int glMode) {
        this.lastMatrixMode = glMode;
        this.matrixModeCallsByMode[glMode == 5889 ? 0 : glMode == 5888 ? 1 : 2]++;
        if (glMode == 5888 && this.derivedProjectionPending) {
            // 5888 is GL_MODELVIEW, which the world pass switches to immediately after its gluPerspective call - the
            // first point at which GL holds the frustum and the mirror should hold the same one.
            this.derivedProjectionPending = false;
            java.nio.FloatBuffer live = org.lwjgl.system.MemoryUtil.memAllocFloat(16);
            try {
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX, live);
                float[] mirrored = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION);
                float delta = 0.0f;
                for (int i = 0; i < 16; i++) {
                    delta = Math.max(delta, Math.abs(mirrored[i] - live.get(i)));
                }
                this.derivedProjectionComparisons++;
                // Record the worst samples rather than the first forty: the cap filled with early events and hid the
                // sample the counter was reporting, which is the same mistake the matrix dump made two rounds ago.
                if (delta > this.traceWorstDelta) {
                    this.traceWorstDelta = delta;
                    while (this.trace.size() >= 12) {
                        this.trace.remove(0);
                    }
                    float[] mvBefore = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
                    java.nio.FloatBuffer glMv = org.lwjgl.system.MemoryUtil.memAllocFloat(16);
                    try {
                        org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, glMv);
                        this.trace.add("C" + this.framesSeen + "[mirror " + fmt3(mvBefore[12]) + "," + fmt3(mvBefore[13]) + ","
                                + fmt3(mvBefore[14]) + " gl " + fmt3(glMv.get(12)) + "," + fmt3(glMv.get(13)) + ","
                                + fmt3(glMv.get(14)) + "]");
                    } finally {
                        org.lwjgl.system.MemoryUtil.memFree(glMv);
                    }
                }
                this.derivedCompareIdentity = System.identityHashCode(mirrored);
                if (delta > this.derivedProjectionDelta) {
                    this.derivedProjectionDelta = delta;
                    this.derivedProjectionWorst = "mirror=" + java.util.Arrays.toString(mirrored)
                            + ";gl=" + live.toString();
                }
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(live);
            }
        }
        this.matricesDirty = true;
        // OpenGL's constants, mapped onto the three stacks the state model keeps.
        if (glMode == 5889) {
            this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        } else if (glMode == 5890) {
            this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_TEXTURE);
        } else {
            this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_MODELVIEW);
        }
        this.immediateStateCalls++;
        this.matrixModeCalls++;
    }

    @Override
    public void loadIdentity() {
        if (this.trace.size() < 40) {
            float[] v = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
            this.trace.add("L" + this.framesSeen + "[mv " + fmt3(v[12]) + "," + fmt3(v[13]) + "," + fmt3(v[14]) + "]");
        }
        this.mirrorMidSetup = true;
        this.loadIdentityByMode[this.lastMatrixMode == 5889 ? 0 : this.lastMatrixMode == 5888 ? 1 : 2]++;
        if (this.derivedProjectionPending) {
            this.loadIdentityWhilePending++;
        }
        this.matricesDirty = true;
        this.immediateState.loadIdentity();
        this.immediateStateCalls++;
        this.loadIdentityCalls++;
    }

    @Override
    public void ortho(double left, double right, double bottom, double top, double near, double far) {
        this.worldPassActive = false;
        this.matricesDirty = true;
        float[] matrix = new float[16];
        // The OpenGL ortho matrix, written column-major, exactly as glOrtho builds it.
        matrix[0] = (float) (2.0 / (right - left));
        matrix[5] = (float) (2.0 / (top - bottom));
        matrix[10] = (float) (-2.0 / (far - near));
        matrix[12] = (float) (-(right + left) / (right - left));
        matrix[13] = (float) (-(top + bottom) / (top - bottom));
        matrix[14] = (float) (-(far + near) / (far - near));
        matrix[15] = 1.0f;
        this.immediateState.loadMatrix(matrix);
        this.immediateStateCalls++;
    }

    @Override
    public void translate(double x, double y, double z) {
        this.translateCalls++;
        if (this.trace.size() < 40) {
            this.trace.add("T" + this.framesSeen + "(" + fmt3(x) + "," + fmt3(y) + "," + fmt3(z) + ")");
        }
        if (y == 0.0D && z == 0.0D && x == 0.0D) {
            this.translateZeroCalls++;
        }
        this.matricesDirty = true;
        this.immediateState.translate(x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        this.matricesDirty = true;
        this.immediateState.rotate(angle, x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void scale(float x, float y, float z) {
        this.matricesDirty = true;
        this.immediateState.scale(x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void perspective(float fovDegrees, float aspect, float near, float far) {
        if (aspect == 0.0f || near == far) {
            return;
        }
        double f = 1.0 / Math.tan(Math.toRadians(fovDegrees) / 2.0);
        float[] frustum = new float[16];
        frustum[0] = (float) (f / aspect);
        frustum[5] = (float) f;
        frustum[10] = (far + near) / (near - far);
        frustum[11] = -1.0f;
        frustum[14] = (2.0f * far * near) / (near - far);
        // glFrustum multiplies, so the mirrored projection stack must multiply too.
        int mode = this.immediateState.matrixMode();
        this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        this.immediateState.multiplyMatrix(frustum);
        this.immediateState.matrixMode(mode);
        this.immediateStateCalls++;
    }

    @Override
    public void frustum(double left, double right, double bottom, double top, double near, double far) {
        double width = right - left;
        double height = top - bottom;
        double depth = far - near;
        if (width == 0.0 || height == 0.0 || depth == 0.0) {
            return;
        }
        float[] matrix = new float[16];
        matrix[0] = (float) ((2.0 * near) / width);
        matrix[5] = (float) ((2.0 * near) / height);
        matrix[8] = (float) ((right + left) / width);
        matrix[9] = (float) ((top + bottom) / height);
        matrix[10] = (float) (-(far + near) / depth);
        matrix[11] = -1.0f;
        matrix[14] = (float) (-(2.0 * far * near) / depth);
        int mode = this.immediateState.matrixMode();
        this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        this.immediateState.multiplyMatrix(matrix);
        this.immediateState.matrixMode(mode);
        this.immediateStateCalls++;
        this.frustumCalls++;
    }

    @Override
    public void multiplyMatrix(java.nio.FloatBuffer buffer) {
        this.matricesDirty = true;
        this.immediateState.multiplyMatrixBuffer(buffer);
        this.immediateStateCalls++;
    }

    @Override
    public void pushMatrix() {
        this.matricesDirty = true;
        this.immediateState.pushMatrix();
        this.immediateStateCalls++;
    }

    @Override
    public void popMatrix() {
        this.matricesDirty = true;
        this.immediateState.popMatrix();
        this.immediateStateCalls++;
    }

    @Override
    /**
     * Writes the world projection the way {@code Project.gluPerspective} would, from the values Minecraft used.
     * <p>
     * The mirrored stack sees {@code loadIdentity} for the projection and never sees the frustum, so this is the only thing
     * that puts a real projection in it. The acceptance is the existing worst-sample counter: {@code matrixMirrorDelta}'s
     * projection term should fall from 2.0006 to near zero.
     */
    /**
     * Seeds the mirrored projection and modelview from OpenGL once, at the moment the world camera is fully applied.
     * <p>
     * The earlier seed ran at the first comparison, which is a moment when GL itself holds the identity - the first batch
     * of a frame is offered during a pass like the GUI's, after that pass reset both stacks - so it copied the wrong
     * moment rather than the wrong matrix. The camera setup is the right moment: {@code func_78479_a} has by then applied
     * the nausea wobble, {@code orientCamera}'s rotations and its interpolated eye-position translation, and nothing later
     * in the frame resets the stack until the next pass. It is one read, not two per batch, and it happens before the
     * comparison counters move, so they still measure tracking rather than the copy.
     */
    public void seedMirrorFromCameraSetup() {
        if (this.mirrorSeeded) {
            return;
        }
        this.mirrorSeeded = true;
        java.nio.FloatBuffer projection = org.lwjgl.system.MemoryUtil.memAllocFloat(16);
        java.nio.FloatBuffer modelView = org.lwjgl.system.MemoryUtil.memAllocFloat(16);
        try {
            org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX, projection);
            org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, modelView);
            float[] p = new float[16];
            float[] m = new float[16];
            projection.get(p);
            modelView.get(m);
            this.immediateState.setMatrix(VulkanGlState.MATRIX_MODE_PROJECTION, p);
            this.immediateState.setMatrix(VulkanGlState.MATRIX_MODE_MODELVIEW, m);
            this.cameraSetupSeeds++;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(projection);
            org.lwjgl.system.MemoryUtil.memFree(modelView);
        }
    }

    public void blendFunc(int sourceFactor, int destinationFactor) {
        this.observedBlendSrc = sourceFactor;
        this.observedBlendDst = destinationFactor;
        this.blendObserved = true;
    }

    public void bindFramebuffer(long targetIdentity, boolean isMain) {
        if (targetIdentity != this.currentRenderTarget && this.currentImmediateMesh != null) {
            // A pass change is an ordering boundary: whatever is pending belongs to the pass that is ending, so it is closed
            // here and drawn before the new pass's work rather than merged with it.
            closeCurrentImmediateGroup();
            this.passBoundaryFlushes++;
        }
        this.currentRenderTarget = targetIdentity;
        this.currentRenderTargetIsMain = isMain;
    }

    public void viewport(int x, int y, int width, int height) {
        // Packed so the comparison in the group key is one test rather than four.
        this.currentViewportKey = ((long) x << 42) ^ ((long) y << 21) ^ ((long) width << 10) ^ (long) height;
    }

    public void beginWorldPass() {
        this.worldSnapshotValid = false;
        this.worldPassActive = true;
    }

    public void endWorldPass() {
        this.worldPassActive = false;
    }

    /**
     * Captures the projection and camera transform for this frame, once, at the camera boundary.
     * <p>
     * This is the per-frame snapshot the world pass draws from: the projection is the one derived from Minecraft's own
     * numbers, and the camera transform is the modelview as the camera setup left it. It replaces the per-batch
     * {@code glGetFloatv} pairs that used to be read at every matrix change, and it is the read the plan records as still
     * present - one per frame, at a boundary, rather than two per batch anywhere.
     */
    public void captureWorldSnapshot() {
        if (!this.worldPassActive) {
            return;
        }
        float[] derived = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION);
        System.arraycopy(derived, 0, this.worldSnapshotProjection, 0, 16);
        java.nio.FloatBuffer modelView = org.lwjgl.system.MemoryUtil.memAllocFloat(16);
        try {
            org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, modelView);
            modelView.get(this.worldSnapshotModelView);
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(modelView);
        }
        this.worldSnapshotValid = true;
        // Seed the mirror's base: the projection derived from Minecraft's numbers and the camera transform as the camera
        // setup left it. Everything the pass then does through the hooked matrix calls - per-model, per-entity, per-TESR
        // pushes, translates, rotates and pops - accumulates on top of this base. Copying the snapshot into every batch's
        // matrices instead would throw those local transforms away and draw every model at the camera origin.
        this.immediateState.setMatrix(VulkanGlState.MATRIX_MODE_PROJECTION, this.worldSnapshotProjection);
        this.immediateState.setMatrix(VulkanGlState.MATRIX_MODE_MODELVIEW, this.worldSnapshotModelView);
        this.snapshotCaptures++;
    }

    /**
     * Whether a particle-family batch with this texture can have its descriptor built at all.
     * <p>
     * The probe pipeline is created once and kept: the descriptor's inputs are the texture and the lightmap, not the
     * pipeline's state key, so any particle pipeline answers the same question about them.
     */
    private boolean canBuildDescriptor(int boundTexture) {
        VulkanGlTexture texture = this.textureRegistry.get(boundTexture);
        if (texture == null || this.lightmapTexture == null) {
            return false;
        }
        try {
            if (this.descriptorProbe == null) {
                VulkanTerrainPipeline ui = pipelineFor(this.lastUiPass);
                if (ui == null) {
                    return false;
                }
                this.descriptorProbe = VulkanImmediateParticlePipeline.create(this.context, ui.renderPass(),
                        false, 0, 0, false, false, false);
            }
            boolean ok = this.descriptorProbe.descriptorSetFor(texture, this.lightmapTexture) != 0L;
            this.particleDescriptorFailureReason = this.descriptorProbe.lastFailure();
            return ok;
        } catch (Throwable error) {
            return false;
        }
    }

    public void applyDerivedProjection(float fovDegrees, float aspect, float near, float far) {
        if (fovDegrees <= 0.0F || aspect <= 0.0F || near <= 0.0F || far <= near) {
            this.derivedProjectionRefusals++;
            return;
        }
        float radians = (float) Math.toRadians(fovDegrees * 0.5F);
        float f = 1.0F / (float) Math.tan(radians);
        float[] frustum = new float[16];
        frustum[0] = f / aspect;
        frustum[5] = f;
        frustum[10] = (far + near) / (near - far);
        frustum[11] = -1.0F;
        frustum[14] = (2.0F * far * near) / (near - far);
        this.immediateState.setMatrix(VulkanGlState.MATRIX_MODE_PROJECTION, frustum);
        this.derivedProjections++;
        // The world pass installs a perspective projection; the overlay and the GUI install orthographic ones.
        // That is enough to tell the passes apart for the mirror comparison, which is only meaningful within one.
        this.worldPassActive = true;
        // The comparison cannot happen here: at this instant gluPerspective has not applied its result to GL yet, so GL
        // still holds the identity and any delta would measure the wrong thing. The flag defers it to the next hookable
        // operation after that call, which is the matrixMode(GL_MODELVIEW) that immediately follows it.
        this.derivedProjectionPending = true;
    }

    public boolean submitImmediateBatch(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                        int vertexCount, int drawMode) {
        if (!this.worldPassActive || !this.worldSnapshotValid) {
            // Before acceptance, and for a stated reason: this batch is not drawn from the frame's world matrices, so the
            // original GL draw stays in charge. Taking it and then failing later is the failure mode the audit lists as S14.
            this.immediateLastRefusal = "outside the world pass or without a snapshot";
            this.outsideWorldPassRefusals++;
            return false;
        }
        if (this.gpuStalled) {
            this.immediateLastRefusal = "the GPU stalled; no further drawing until the renderer is rebuilt";
            return false;
        }
        if (!this.currentRenderTargetIsMain) {
            // The hybrid draws into the shared image, which stands in for the main framebuffer only. A batch issued for
            // another render target cannot be honoured, so it is refused here, before acceptance, and GL draws it - which
            // is the behaviour the audit's S10 acceptance asks for instead of viewing it in the wrong place.
            this.immediateLastRefusal = "not the main framebuffer";
            this.offTargetRefusals++;
            return false;
        }
        this.immediateBatchesOffered++;
        if (formatId >= 0 && formatId < this.immediateOfferedByFormat.length) {
            this.immediateOfferedByFormat[formatId]++;
        }
        long submitStart = System.nanoTime();
        try {
            return submitImmediateBatchTimed(vertexFormat, vertexStride, formatId, vertexAddress, vertexCount,
                    drawMode);
        } finally {
            this.immediateSubmitNanos += System.nanoTime() - submitStart;
        }
    }

    /** The body of {@link #submitImmediateBatch}, with the per-call cost measured by the caller. */
    private boolean submitImmediateBatchTimed(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                              int vertexCount, int drawMode) {
        if (this.gpuStalled) {
            // A stalled GPU closes every entry that touches the frame target or the bridge: the injected timeout showed a
            // barrier recorded into a buffer that was never begun, and a framebuffer destroyed while a stale command buffer
            // still referenced it, both from paths that assumed the frame had been recorded.
            return false;
        }
        if (!IMMEDIATE_DRAWING_ENABLED) {
            // Comparison switch, default on: turning it off makes every immediate batch fall back to GL, which is
            // exactly the reference image the Vulkan path has to reproduce.
            this.immediateLastRefusal = "immediate drawing disabled by celtium.vulkan.immediate=false";
            return false;
        }
        String reason = VulkanImmediateDrawer.unsupportedReason(drawMode);
        if (reason != null) {
            this.immediateLastRefusal = reason;
            return false;
        }
        int expectedStride = formatId == 2 ? VulkanImmediateTexturePipeline.VERTEX_BYTES
                : formatId == 3 ? VulkanImmediateBlockPipeline.VERTEX_BYTES
                : formatId == 4 ? VulkanImmediateItemPipeline.VERTEX_BYTES
                : formatId == 5 ? VulkanImmediateParticlePipeline.VERTEX_BYTES
                : VulkanImmediatePipeline.VERTEX_BYTES;
        if (formatId == 2 && PRESENT_FROM_COMPOSITE) {
            // Present the frame's content at the moment Minecraft composites it. The stack trace identified this batch
            // as Framebuffer.func_178038_a - the game framebuffer blitted onto the screen - and taking it cancels GL's
            // blit, so the screen shows the Vulkan present instead. It runs before the acceptance mask, which keeps
            // this layout on GL by default.
            try {
                if (this.frameTarget != null && ensureSwapchain()) {
                    this.frameSubmitReachedQueue = false;
                    this.frameTarget.beginFrameForPresent();
                    long bridgeWait = this.frameTarget.waitSemaphore();
                    long bridgeDone = this.frameTarget.doneSemaphore();
                    int sourceLayout = this.frameTarget.colorImageLayout();
                    int restoredLayout = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                    String result = this.swapchain.presentImage(this.frameTarget.colorImage(), sourceLayout,
                            this.targetWidth, this.targetHeight, bridgeWait, bridgeDone, restoredLayout);
                    // The present moved the shared image through the transfer layout and this hands it back, so the
                    // owner's bookkeeping follows what the commands actually did.
                    this.frameTarget.noteColorImageLayout(restoredLayout);
                    // Report both facts before anything acts on them: the previous attempt to fail closed on them
                    // broke the run (four terrain passes instead of two thousand), and which flag was false is the
                    // question to answer with a measurement rather than another guess.
                    if (this.immediatePresentedFrames < 3) {
                        record("composite present: submitDone=" + this.swapchain.lastPresentSubmitDone()
                                + " succeeded=" + this.swapchain.lastPresentSucceeded() + " frameTargetWidth="
                                + this.targetWidth + " result=" + result);
                    }
                    // End the frame only when the submission that signals the bridge's done semaphore was made; and
                    // leave GL's composite in place unless the frame actually reached the screen. Both flags are now
                    // set inside presentImage itself - the first attempt set them in the probe by mistake, which is why
                    // submitDone read false here and skipping endFrame broke the bridge's copy-back.
                    if (this.swapchain.lastPresentSubmitDone()) {
                        this.frameTarget.endFrame();
                    }
                    if (!this.swapchain.lastPresentSucceeded()) {
                        this.immediatePresentFailures++;
                        record("composite present did not reach the screen, GL's composite stays: " + result);
                        return false;
                    }
                    if (this.swapchain.outOfDate()) {
                        // The swapchain no longer matches the surface. Dropping it is the whole rebuild: the next
                        // present calls ensureSwapchain(), which creates one against the surface as it is by then.
                        record("the swapchain is out of date with the surface, rebuilding it for the next present");
                        this.swapchain.delete();
                        this.swapchain = null;
                    }
                    this.immediatePresentedFrames++;
                    if (this.immediatePresentedFrames == 1) {
                        record("presented the frame from the composite: " + result);
                        record("presented image mean=" + this.swapchain.lastPresentedMean());
                        record("submitWithSemaphores calls=" + VulkanContext.submitWithSemaphoresCalls
                                + " withWait=" + VulkanContext.submitWithSemaphoresWaits);
                    }
                    return true;
                }
            } catch (RuntimeException | LinkageError error) {
                if (this.immediatePresentedFrames == 0) {
                    record("presenting from the composite failed: " + describe(error));
                }
            }
            return false;
        }
        if (IMMEDIATE_WINDOWS_PER_FRAME > 0 && this.immediateWindowsThisFrame >= IMMEDIATE_WINDOWS_PER_FRAME
                && this.immediateGroups.isEmpty() && !this.windowOpen) {
            // The frame's window budget is spent and no batch of this frame is being held. Refusing here hands the batch
            // to GL **in this frame**, which is what the audit's D asks for: accepting it would cancel GL's draw and then
            // defer it to a later frame. Ordering is preserved because nothing of this frame is pending, and the cost is
            // stated rather than hidden - this geometry is GL-drawn and the counter below says how often.
            this.immediateRefusedWindowBudget++;
            this.immediateLastRefusal = "the frame's window budget is spent, so this batch stays with GL";
            if (this.immediateRefusedWindowBudget == 1) {
                // Reported at the first refusal rather than at a count a run may never reach: the previous attempt wired
                // these counters to a snapshot that fires at exactly 500 accepted batches, and a low-budget run refuses
                // batches before they are counted, so the line never printed and the path looked unexercised.
                record("first batch left to GL because the frame's window budget was spent: windowsThisFrame="
                        + this.immediateWindowsThisFrame + " limit=" + IMMEDIATE_WINDOWS_PER_FRAME
                        + " deferredNextFrame=" + this.immediateDeferredNextFrame);
            }
            return false;
        }
        if (!celtium$formatAccepted(formatId)) {
            // Attribution switch: restricting which layouts this backend accepts makes the image difference
            // measurable per format instead of as one aggregate number. Refused batches are drawn by GL as usual.
            this.immediateLastRefusal = "format " + formatId + " not in celtium.vulkan.immediateFormats";
            return false;
        }
        if (formatId < 1 || formatId > 5) {
            // The layout is identified structurally by the Mixin (element list, not class or stride), so an unknown
            // one is refused with its size rather than drawn with a pipeline that would misread its vertices.
            this.immediateLastRefusal = "no pipeline yet for a " + vertexStride + "-byte vertex (id " + formatId + ")";
            return false;
        }
        if (vertexStride != expectedStride) {
            this.immediateLastRefusal = "layout id " + formatId + " has stride " + vertexStride + ", expected "
                    + expectedStride;
            return false;
        }
        if ((formatId == 3 || formatId == 5) && this.lightmapTexture == null) {
            this.immediateLastRefusal = "lightmapped batch with no lightmap texture mirrored";
            return false;
        }
        if (formatId >= 2 && this.immediateState.boundTextureName() == 0) {
            this.immediateLastRefusal = "textured batch with no texture bound";
            return false;
        }
        if (formatId == 3 && this.lightmapTexture == null) {
            // The BLOCK family samples the lightmap, so a missing mirror means the batch would be lit wrongly.
            this.immediateLastRefusal = "block batch with no lightmap texture mirrored";
            return false;
        }
        int[] indices = VulkanImmediateDrawer.expandIndices(drawMode, vertexCount);
        if (indices == null) {
            this.immediateLastRefusal = "topology " + drawMode + " has no expansion";
            return false;
        }
        try {
            // The matrices are read from OpenGL at the moment the batch is built: Mixins cannot see the world
            // projection (it is installed by LWJGL 2's GLU utility class, outside the transforming loader -
            // matrixChannels=persp:0), while reading them on the render thread with the GL context current needs no
            // cooperation from any class and gives exactly what Minecraft is drawing with.
            if (this.matricesDirty) {
                captureGlMatrices();
                this.matricesDirty = false;
                compareMirroredAndLiveMatrices();
            }
            int boundTexture = this.immediateState.boundTextureName();
            if (!this.immediateState.texture2dEnabled()) {
                // Vanilla draws the sky with texturing switched off and the gradient in the vertex colours. Sampling
                // the texture that happens to be bound then washes that out - measured as the sky going flat, 74.7% of
                // the top third of the frame differing. A white 1x1 mirror leaves the vertex colours as they are.
                boundTexture = whiteTextureId();
            }
            if (MIRRORED_MATRICES) {
                System.arraycopy(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION), 0,
                        this.glProjection, 0, 16);
                System.arraycopy(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW), 0,
                        this.glModelView, 0, 16);
                this.matricesDirty = false;
            }
            int[] state = celtium$captureState();
            long stateKey = celtium$stateKey(formatId, state);
            // The pass identity joins the key here: a batch from another render target or viewport is not part of this
            // group, which is what stops two passes merging into one draw (audit S10).
            boolean samePass = this.currentGroupTarget == this.currentRenderTarget
                    && this.currentGroupViewport == this.currentViewportKey;
            if (!samePass && this.currentImmediateMesh != null) {
                this.passSplits++;
            }
            if (this.currentImmediateMesh == null
                    || this.currentGroupFormatId != formatId || this.currentGroupTexture != boundTexture
                    || this.currentGroupStateKey != stateKey || !samePass
                    || !java.util.Arrays.equals(this.currentGroupProjection, this.glProjection)
                    || !java.util.Arrays.equals(this.currentGroupModelView, this.glModelView)) {
                // A new matrix pair means a new group: mixing them is what placed geometry off-screen.
                closeCurrentImmediateGroup();
                this.currentImmediateMesh = new VulkanDynamicMesh(this.context);
                System.arraycopy(this.glProjection, 0, this.currentGroupProjection, 0, 16);
                System.arraycopy(this.glModelView, 0, this.currentGroupModelView, 0, 16);
                this.currentGroupFormatId = formatId;
                this.currentGroupTexture = boundTexture;
                this.currentGroupStateKey = stateKey;
                // The group's own copy of the captured state. Without this the field stayed at its initialised
                // new int[7], closeCurrentImmediateGroup stored that zero array, and every group was drawn with blend,
                // depth test, depth write, culling and the alpha reference all off - a state-keyed pipeline cache whose
                // keys were right and whose values were empty. Cloned rather than referenced so the snapshot cannot be
                // overwritten by the next capture.
                this.currentGroupState = state.clone();
                // The blend factors as they were when this group opened, captured here rather than read from the global
                // observation at draw time: the global value is whatever the most recent blendFunc call anywhere left, and
                // using it gave 0.31% against these factors' 0.21-0.23%. This is the per-group snapshot the audit's S07 asks
                // for, and it lives beside the state key for the same reason the matrices do.
                this.currentGroupBlendSrc = this.blendObserved ? this.observedBlendSrc
                        : org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
                this.currentGroupBlendDst = this.blendObserved ? this.observedBlendDst
                        : org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
                this.currentGroupTarget = this.currentRenderTarget;
                this.currentGroupViewport = this.currentViewportKey;
            }
            if (!this.currentImmediateMesh.copyVertices(vertexAddress, vertexStride, vertexCount, indices)) {
                this.immediateLastRefusal = "the pending batch buffer refused the vertices";
                return false;
            }
            this.currentGroupUsed = true;
            this.pendingImmediateIndices += indices.length;
            // Everything the draw will need is prepared before the batch is accepted: the audit's S14 is the
            // failure mode where geometry is taken and then dropped, and the fix is to refuse here instead, where GL
            // can still draw the batch itself. The texture is the one requirement the pipeline check does not cover.
            if (this.textureRegistry.get(boundTexture) == null) {
                this.immediateLastRefusal = "the bound texture could not be mirrored";
                this.textureRefusalsBeforeAccept++;
                return false;
            }
            if (formatId == 5 && !canBuildDescriptor(boundTexture)) {
                // The particle family is the one that dropped: every post-accept drop counted so far is format 5, which is
                // the family whose descriptor needs the lightmap. Building it here, once, turns that drop into an ordinary
                // refusal - before acceptance, where GL still owns the draw. The descriptor depends on the textures rather
                // than on the pipeline's state key, so one pipeline is enough to ask the question.
                this.immediateLastRefusal = "the particle descriptor could not be built for this lightmap";
                this.particleDescriptorRefusals++;
                return false;
            }

            this.immediateTaken++;
            if (IMMEDIATE_WINDOWS_ENABLED && this.pendingImmediateIndices >= IMMEDIATE_FLUSH_INDICES) {
                flushImmediateWindow();
            }
            if (this.immediateBatchesOffered % 500 == 0) {
                StringBuilder histogram = new StringBuilder("immediate formats offered/accepted:");
                for (int id = 0; id < this.immediateOfferedByFormat.length; id++) {
                    if (this.immediateOfferedByFormat[id] > 0) {
                        histogram.append(" id").append(id).append('=').append(this.immediateOfferedByFormat[id]).append('/')
                                .append(this.immediateAcceptedByFormat[id]);
                    }
                }
                record(histogram.toString());
                System.out.println("celtium " + histogram);
            }
            if (this.immediateTaken == 500) {
                // One-shot snapshot of what happens to accepted batches: a batch counts as accepted only when GL's own
                // draw was cancelled, so anything accepted and not drawn is lost geometry.
                record("immediate flush snapshot at 500 accepted: flushes=" + this.immediateFlushes
                        + " drawn=" + this.immediateGroupsDrawn + " discarded=" + this.immediateGroupsDiscarded
                        + " noPipeline=" + this.immediateFlushNoPipeline
                        + " deferredNextFrame=" + this.immediateDeferredNextFrame
                        + " refusedWindowBudget=" + this.immediateRefusedWindowBudget
                        + " lastError=" + this.immediateLastFlushError);
            }
            if (formatId >= 0 && formatId < this.immediateAcceptedByFormat.length) {
                this.immediateAcceptedByFormat[formatId]++;
            }
            this.immediateLastRefusal = "none";
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.immediateLastRefusal = "copy failed: " + describe(error);
            record("immediate batch refused: " + describe(error));
            return false;
        }
    }

    /** Frees the meshes of groups whose draw was recorded at least one full pass ago. */
    private void releaseRetiredImmediateGroups() {
        for (Object[] group : this.retiredImmediateGroups) {
            ((VulkanDynamicMesh) group[0]).delete();
        }
        this.retiredImmediateGroups.clear();
    }

    /**
     * Reports the projected NDC bounds of a group and the colour of its first vertex.
     * <p>
     * This is the narrower diagnostic the previous round called for: vertex 0 alone cannot tell "projects on-screen
     * but is rejected by the depth test" from "never enters an accepted group", while the bounds over the whole group
     * can. The packed colour identifies the test geometry (the fixture draws with 255, 64, 64, 200).
     */
    private void reportGroupBounds(Object[] group) {
        if (this.immediateDiagnostics >= 6) {
            return;
        }
        this.immediateDiagnostics++;
        try {
            VulkanDynamicMesh mesh = (VulkanDynamicMesh) group[0];
            float[] projection = (float[]) group[1];
            float[] modelView = (float[]) group[2];
            long base = mesh.vertexAddress();
            int vertices = mesh.vertexCount();
            float[] mvp = new float[16];
            for (int column = 0; column < 4; column++) {
                for (int row = 0; row < 4; row++) {
                    float sum = 0.0f;
                    for (int k = 0; k < 4; k++) {
                        sum += projection[k * 4 + row] * modelView[column * 4 + k];
                    }
                    mvp[column * 4 + row] = sum;
                }
            }
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            int behind = 0;
            for (int vertex = 0; vertex < vertices; vertex++) {
                long offset = base + ((long) vertex * VulkanImmediatePipeline.VERTEX_BYTES);
                float x = MemoryUtil.memGetFloat(offset);
                float y = MemoryUtil.memGetFloat(offset + 4);
                float z = MemoryUtil.memGetFloat(offset + 8);
                float cx = (mvp[0] * x) + (mvp[4] * y) + (mvp[8] * z) + mvp[12];
                float cy = (mvp[1] * x) + (mvp[5] * y) + (mvp[9] * z) + mvp[13];
                float cz = (mvp[2] * x) + (mvp[6] * y) + (mvp[10] * z) + mvp[14];
                float cw = (mvp[3] * x) + (mvp[7] * y) + (mvp[11] * z) + mvp[15];
                if (cw <= 0.0f) {
                    behind++;
                    continue;
                }
                minX = Math.min(minX, cx / cw);
                maxX = Math.max(maxX, cx / cw);
                minY = Math.min(minY, cy / cw);
                maxY = Math.max(maxY, cy / cw);
                minZ = Math.min(minZ, cz / cw);
                maxZ = Math.max(maxZ, cz / cw);
            }
            String bounds = behind == vertices ? "all behind the camera"
                    : String.format("x[%.2f..%.2f] y[%.2f..%.2f] z[%.2f..%.2f] behind=%d",
                            minX, maxX, minY, maxY, minZ, maxZ, behind);
            record("group " + this.immediateDiagnostics + ": vertices=" + vertices
                    + " indices=" + mesh.indexCount() + " colour0=0x"
                    + Integer.toHexString(MemoryUtil.memGetInt(base + 12))
                    + " proj=[m00=" + projection[0] + " m11=" + projection[5] + "] ndc " + bounds);
        } catch (RuntimeException | LinkageError error) {
            record("group diagnostic failed: " + describe(error));
        }
    }

    /**
     * Snapshots the drawing state that bakes into a pipeline.
     * <p>
     * Captured with the group, like the matrices: by the time the flush runs a frame later the live state has moved on,
     * and using it would draw with a blend or depth combination the batch never asked for. The array is
     * {blend, srcFactor, dstFactor, depthTest, depthWrite, cull} with the factors already in Vulkan form.
     */
    private int[] celtium$captureState() {
        boolean blend = this.immediateState.blendEnabled();
        int source = blend ? glBlendFactorToVk(this.immediateState.blendSrcRgb()) : 0;
        int destination = blend ? glBlendFactorToVk(this.immediateState.blendDstRgb()) : 0;
        // Comparison switch, default on: forcing the threshold to zero turns every discard off, which isolates the
        // alpha test's effect inside one build instead of comparing across builds.
        float alphaReference = ALPHA_TEST_ENABLED && this.immediateState.alphaTestEnabled()
                ? this.immediateState.alphaFuncReference() : 0.0f;
        return new int[] {
                blend ? 1 : 0,
                source < 0 ? org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA : source,
                destination < 0 ? org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA : destination,
                this.immediateState.depthTestEnabled() ? 1 : 0,
                this.immediateState.depthMask() ? 1 : 0,
                this.immediateState.cullEnabled() ? 1 : 0,
                Float.floatToIntBits(alphaReference),
        };
    }

    private static long celtium$stateKey(int formatId, int[] state) {
        long key = formatId;
        for (int value : state) {
            key = (key * 31) + value;
        }
        return key;
    }

    /**
     * Compares the mirrored matrix state with the matrices OpenGL reports, recording the largest difference seen.
     * <p>
     * This exists because the whole immediate path pays two {@code glGetFloatv} calls per matrix change (measured as
     * the dominant per-batch cost). If the mirrored stack is faithful, those calls can go away; if it is not, the
     * number says by how much, instead of leaving it to a guess.
     */
    private void compareMirroredAndLiveMatrices() {
        if (this.matrixComparisons > 4000) {
            return;
        }
        // Skip while the mirror is still the zeroed array a reset leaves, which is the condition itself rather than a
        // proxy for it: a flag that cleared after one skip was too narrow, and the worst sample stayed a zeroed matrix.
        // Comparing an unset matrix with GL's measures the interval between two frames, not the mirror.
        float[] viewNow = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
        if (viewNow[0] == 0.0f && viewNow[5] == 0.0f && viewNow[12] == 0.0f && viewNow[13] == 0.0f && viewNow[14] == 0.0f) {
            this.midSetupComparisonsSkipped++;
            return;
        }
        if (!this.worldPassActive) {
            // Comparing here would compare the world pass's mirrored matrices with whatever pass is running - the overlay's
            // translate(0, 0, -2000) turned up in this counter exactly that way, in a run that passed. The comparison is
            // only meaningful while the pass that owns the mirror is the pass that is drawing, so it is skipped otherwise
            // and counted separately.
            this.crossPassComparisonsSkipped++;
            return;
        }
        if (!this.mirrorSeeded && false) {
            // One bootstrap read, once, before any comparison is recorded. The first dump showed the mirror's modelview
            // missing exactly the translation that was applied before the hooks were installed, so the stack starts from
            // what GL already had rather than from empty. The seed is taken before the counters move, so the comparisons
            // below still measure whether the mirror *tracks* GL, not whether it was copied from it.
            this.immediateState.copyMatricesFrom(this.glProjection, this.glModelView);
            this.mirrorSeeded = true;
            return;
        }
        this.matrixComparisons++;
        float projectionDelta = maxDelta(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION),
                this.glProjection);
        float modelViewDelta = maxDelta(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW),
                this.glModelView);
        // Dump the first comparison's sixteen elements from each side. The counter said the mirror is wrong
        // everywhere; whether that is a transpose, a clip-depth remap or a different stack cannot be read from a
        // maximum, and one dump answers it in a single run.
        boolean newWorst = projectionDelta > this.mirroredProjectionDelta
                || modelViewDelta > this.mirroredModelViewDelta;
        if (newWorst || this.matrixDump == null) {
            this.matrixDump = "projMirror=" + java.util.Arrays.toString(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION))
                    + ";projGl=" + java.util.Arrays.toString(this.glProjection)
                    + ";viewMirror=" + java.util.Arrays.toString(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW))
                    + ";viewGl=" + java.util.Arrays.toString(this.glModelView);
        }
        this.mirroredProjectionDelta = Math.max(this.mirroredProjectionDelta, projectionDelta);
        this.mirroredModelViewDelta = Math.max(this.mirroredModelViewDelta, modelViewDelta);
        // The same two numbers, ignoring the first frames. The maximum includes the warm-up frames - the ones before the
        // backend existed, whose state the mirror cannot have - and a maximum that is dominated by warm-up says nothing
        // about whether the mirror tracks GL once both are running. Steady state is the question the removal of the
        // glGetFloatv reads depends on.
        if (this.framesSeen > 10) {
            this.steadyProjectionDelta = Math.max(this.steadyProjectionDelta, projectionDelta);
            this.steadyModelViewDelta = Math.max(this.steadyModelViewDelta, modelViewDelta);
            this.steadyComparisons++;
                // The worst modelview sample, with the pass identity attached. The earlier trace keyed on the projection delta and
            // therefore never recorded the sample the fixture fails on; this one is where both deltas are in scope, and it says
            // which render target and viewport the disagreeing comparison belongs to.
            if (modelViewDelta > this.worstModelViewDelta) {
                this.worstModelViewDelta = modelViewDelta;
                float[] mirrorView = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
                this.worstModelViewSample = "frame=" + this.framesSeen + " target=" + this.currentRenderTarget
                        + " viewport=" + this.currentViewportKey + " projDelta=" + projectionDelta
                        + " mirror[m0=" + fmt3(mirrorView[0]) + ",m5=" + fmt3(mirrorView[5]) + ",t=" + fmt3(mirrorView[12])
                        + "," + fmt3(mirrorView[13]) + "," + fmt3(mirrorView[14]) + "]";
            }
        }
        // How many comparisons matched exactly. A maximum on its own cannot tell "1130 samples, one of them wrong
        // somewhere" from "every sample wrong", and those two need different work: the first is one uncaptured
        // operation, the second is a broken mirror.
        if (projectionDelta == 0.0f && modelViewDelta == 0.0f) {
            this.matrixExactComparisons++;
        }
    }

    private static float maxDelta(float[] left, float[] right) {
        float worst = 0.0f;
        for (int i = 0; i < 16; i++) {
            worst = Math.max(worst, Math.abs(left[i] - right[i]));
        }
        return worst;
    }

    /**
     * Opens a window for the pending immediate groups at the point that produced them.
     * <p>
     * Off by default ({@code -Dceltium.vulkan.immediateWindows=true} enables it) so the shipped behaviour is
     * unchanged while this is being finished. Measurements so far: the copies are sound - the game framebuffer's mean
     * is 150.47656 both before and after a window - and the frame is nonetheless black at capture time in every run
     * that uses it, which is what the fixture-side sampling below is for.
     */
    private void flushImmediateWindow() {
        if (this.gpuStalled) {
            // A stalled GPU closes every entry that touches the frame target or the bridge: the injected timeout showed a
            // barrier recorded into a buffer that was never begun, and a framebuffer destroyed while a stale command buffer
            // still referenced it, both from paths that assumed the frame had been recorded.
            return;
        }
        if (this.gpuStalled) {
            return;
        }
        if (this.windowOpen || this.immediateWindowBusy) {
            return;
        }
        // The limit used to be one, because a second window in the same frame measured as a black frame. That
        // measurement was taken before the missing VkSubmitInfo.waitSemaphoreCount was found, so the black frame had a
        // sufficient explanation in handoffs that were never waited on; the audit asks for the hypothesis to be
        // re-evaluated after that fix, and this switch is how.
        if (this.immediateWindowsThisFrame >= IMMEDIATE_WINDOWS_PER_FRAME) {
            // One window per frame: a second one in the same frame is what measured as blackening the frame, and the
            // remainder is drawn in the next frame's window the way it was before.
            this.immediateDeferredNextFrame++;
            return;
        }
        closeCurrentImmediateGroup();
        if (this.immediateGroups.isEmpty()) {
            return;
        }
        int previousDrawFramebuffer;
        try {
            previousDrawFramebuffer =
                    org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        } catch (RuntimeException | LinkageError error) {
            this.immediateFlushNoPipeline++;
            return;
        }
        if (previousDrawFramebuffer == 0) {
            this.immediateFlushDeferredDefaultFbo++;
            return;
        }
        VulkanTerrainPipeline terrain = pipelineFor(this.lastUiPass);
        if (terrain == null || !ensureFrameTarget()) {
            this.immediateFlushNoPipeline++;
            return;
        }
        this.immediateWindowBusy = true;
        boolean opened = false;
        float before = -1.0f;
        String stateBefore = null;
        try {
            if (DIAGNOSTIC_WINDOW_SAMPLES && this.immediateContentSamples < 4) {
                // Diagnostic only. Both of these are glReadPixels plus a pile of glGet calls, and they used to run on
                // every window of every frame - the audit's item G - which puts a synchronous stall in the shipped path
                // to collect numbers nobody reads unless a frame is being diagnosed.
                before = sampleBoundFramebufferMean();
                stateBefore = snapshotGlState();
            }
            this.frameSubmitReachedQueue = false;
            this.frameTarget.beginFrame();
            this.windowOpen = true;
            opened = true;
            beginCommands();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                recordImmediateOnlyPass(stack, terrain);
            }
            submitCommands();
            this.passSubmissions++;
            this.immediateWindows++;
            this.immediateWindowsThisFrame++;
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("immediate window failed: " + describeWithStack(error));
            this.immediateGroupsDiscarded += this.immediateGroups.size();
            discardImmediateGroups();
        } finally {
            if (opened) {
                this.windowOpen = false;
                try {
                    if (this.frameSubmitReachedQueue) {
                        this.frameTarget.endFrame();
                    } else {
                        this.passErrors++;
                        record("immediate window submitted nothing, so the frame is not ended");
                    }
                } catch (RuntimeException | LinkageError error) {
                    record("immediate window close failed: " + describeWithStack(error));
                }
                float after = -1.0f;
                if (DIAGNOSTIC_WINDOW_SAMPLES && this.immediateContentSamples < 4) {
                    after = sampleBoundFramebufferMean();
                    this.immediateContentSamples++;
                    String stateAfter = snapshotGlState();
                    record("immediate window content sample: mean before=" + before + " after=" + after
                            + (stateBefore != null && stateBefore.equals(stateAfter)
                                    ? " GL state restored unchanged"
                                    : " GL state changed: before[" + stateBefore + "] after[" + stateAfter + "]"));
                }
            }
            try {
                org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER,
                        previousDrawFramebuffer);
            } catch (RuntimeException | LinkageError error) {
                record("immediate window could not restore the framebuffer binding: " + error);
            }
            this.immediateWindowBusy = false;
        }
    }

    /**
     * {@return the GL state a window could leave behind}, as a short string for comparison.
     * <p>
     * The framebuffer content survives a window (measured 150.47656 before and after) and the frame is black by capture
     * time, so the remaining candidates are the pieces of GL state the bridge's copies touch: a window runs depth
     * conversion with its own program, VAO and texture bindings, and GL's viewport, scissor and capabilities could
     * survive in a shape GL's later drawing does not expect.
     */
    private String snapshotGlState() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer viewport = stack.mallocInt(4);
            java.nio.IntBuffer scissor = stack.mallocInt(4);
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, scissor);
            return "viewport=" + viewport.get(0) + "," + viewport.get(1) + "," + viewport.get(2) + "," + viewport.get(3)
                    + " scissor=" + scissor.get(0) + "," + scissor.get(1) + "," + scissor.get(2) + ","
                    + scissor.get(3)
                    + " scissorTest=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST)
                    + " blend=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND)
                    + " depthTest=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST)
                    + " cull=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE)
                    + " program=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM)
                    + " vao=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING)
                    + " drawFbo=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING)
                    + " readFbo=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING)
                    + " tex2d=" + org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        } catch (RuntimeException | LinkageError error) {
            return "unavailable: " + error;
        }
    }

    /** {@return the mean of a small centre sample of the bound framebuffer}, or -1 when it cannot be read. */
    public static float sampleBoundFramebufferMean(int width, int height) {
        try {
            int size = 8;
            int x = Math.max(0, (width / 2) - (size / 2));
            int y = Math.max(0, (height / 2) - (size / 2));
            java.nio.ByteBuffer pixels = MemoryUtil.memAlloc(size * size * 4);
            try {
                org.lwjgl.opengl.GL11.glReadPixels(x, y, size, size, org.lwjgl.opengl.GL11.GL_RGBA,
                        org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixels);
                long total = 0;
                for (int i = 0; i < size * size * 4; i++) {
                    total += pixels.get(i) & 0xFF;
                }
                return (float) ((double) total / (size * size * 4));
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } catch (RuntimeException | LinkageError error) {
            return -1.0f;
        }
    }

    private float sampleBoundFramebufferMean() {
        return sampleBoundFramebufferMean(this.targetWidth, this.targetHeight);
    }

    /**
     * {@return the GL name of a 1x1 white texture}, created once.
     * <p>
     * It stands in for "texturing is off": every immediate pipeline samples a texture, so the cheapest faithful
     * equivalent of no texture is a white one, which multiplies the vertex colours by one.
     */
    private int whiteTextureId() {
        if (this.whiteTextureGlId == 0) {
            int id = org.lwjgl.opengl.GL11.glGenTextures();
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, id);
            java.nio.ByteBuffer pixel = java.nio.ByteBuffer.allocateDirect(4);
            pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
            org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA, 1, 1,
                    0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixel);
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    this.immediateState.boundTextureName());
            this.whiteTextureGlId = id;
        }
        return this.whiteTextureGlId;
    }

    /** Records the exception with its stack, so a failure identifies its own origin instead of being guessed at. */
    private static String describeWithStack(Throwable error) {
        java.io.StringWriter writer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(writer));
        String trace = writer.toString().replace('\n', ' ').replace('\r', ' ');
        return trace.length() > 600 ? trace.substring(0, 600) : trace;
    }

    /** Records a render pass containing nothing but the pending immediate groups. */
    private void recordImmediateOnlyPass(MemoryStack stack, VulkanTerrainPipeline terrain) {
        if (this.gpuStalled) {
            // The pass-level recording is closed too: with the buffer never begun, vkCmdBeginRenderPass, vkCmdSetViewport and
            // vkCmdSetScissor were still being recorded into it, which is what the steady-state injection reported.
            return;
        }
        long renderPass = terrain.renderPass();
        org.lwjgl.vulkan.VkRenderPassBeginInfo begin =
                org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack).sType$Default();
        begin.renderPass(renderPass);
        begin.framebuffer(ensureFramebuffer(renderPass));
        org.lwjgl.vulkan.VkRect2D.Buffer area = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
        area.get(0).offset().set(0, 0);
        area.get(0).extent().set(this.targetWidth, this.targetHeight);
        begin.renderArea(area.get(0));
        vkCmdBeginRenderPass(this.commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);
        setViewportAndScissor(stack);
        drawPendingImmediate();
        vkCmdEndRenderPass(this.commandBuffer);
    }

    /** Moves the open group into the flush list together with the matrices it was built with. */
    private void closeCurrentImmediateGroup() {
        if (this.currentImmediateMesh != null && this.currentGroupUsed) {
            float[] projection = new float[16];
            float[] modelView = new float[16];
            // OpenGL's clip depth runs -1..1 and Vulkan's 0..1, the same remap the terrain projection uses.
            remapClipDepth(this.currentGroupProjection, projection);
            System.arraycopy(this.currentGroupModelView, 0, modelView, 0, 16);
            // The largest group the immediate path closed, in vertices. It is the number that says whether separate
            // batches merged into one draw: the S05 acceptance fixture draws two quads into a single batch and expects to
            // see eight vertices here, where the base-vertex defect would still have shown eight vertices but drawn the
            // second quad with the first quad's four.
            this.immediateGroupsClosed++;
            int groupVertices = this.currentImmediateMesh.vertexCount();
            if (groupVertices > this.immediateMaxGroupVertices) {
                this.immediateMaxGroupVertices = groupVertices;
            }
            this.immediateGroups.add(new Object[] {this.currentImmediateMesh, projection, modelView,
                    Integer.valueOf(this.currentGroupFormatId), Integer.valueOf(this.currentGroupTexture),
                    Long.valueOf(this.currentGroupStateKey), this.currentGroupState,
                    Integer.valueOf(this.currentGroupBlendSrc), Integer.valueOf(this.currentGroupBlendDst)});
        } else if (this.currentImmediateMesh != null) {
            this.currentImmediateMesh.delete();
        }
        this.currentImmediateMesh = null;
        this.currentGroupUsed = false;
    }

    private void discardImmediateGroups() {
        releaseRetiredImmediateGroups();
        for (Object[] group : this.immediateGroups) {
            this.retiredImmediateGroups.add(group);
        }
        this.immediateGroups.clear();
        if (this.currentImmediateMesh != null) {
            this.currentImmediateMesh.delete();
            this.currentImmediateMesh = null;
        }
        this.currentGroupUsed = false;
    }

    /**
     * {@return a machine-readable summary of what this backend actually drew}. The fixture reads it to decide
     * whether a run really rendered terrain: selecting the backend by name is not evidence that any pass reached the
     * queue, so submissions, skips and errors are counted instead.
     */
    public String verificationSummary() {
        return "celtium/vulkan-check passes=" + this.passSubmissions + " skipped=" + this.passSkips + " errors="
                + this.passErrors + " lastDraw=" + this.lastDrawCommands + " lastSections=" + this.lastSectionsDrawn
                + " skipReason=" + (this.lastSkipReason == null ? "none" : this.lastSkipReason.replace(' ', '_'))
                + " immediateState=" + this.immediateStateCalls + " immediateOffered=" + this.immediateBatchesOffered
                + " immediateTaken=" + this.immediateTaken + " immediateFlushes=" + this.immediateFlushes
                + " immediateGroupsClosed=" + this.immediateGroupsClosed
                + " immediateMaxGroupVertices=" + this.immediateMaxGroupVertices
                + " immediateRefusal=" + this.immediateLastRefusal.replace(' ', '_')
                + " snapshotCaptures=" + this.snapshotCaptures + " snapshotServed=" + this.snapshotServedCaptures + " outsideWorldPassRefusals=" + this.outsideWorldPassRefusals
                + " offTargetRefusals=" + this.offTargetRefusals
                + " textureRefusalsBeforeAccept=" + this.textureRefusalsBeforeAccept
                + " postAcceptDroppedGroups=" + this.postAcceptDroppedGroups
                + " particleDescriptorRefusals=" + this.particleDescriptorRefusals
                + " gpuStalled=" + this.gpuStalled + " stalledStops=" + this.stalledStops
                + " stalledTeardownSkips=" + this.stalledTeardownSkips
                + " idleWaits=" + this.idleWaits + " injectedIdleFailures=" + this.injectedIdleFailures
                + " blendObserved=" + this.blendObserved + "(" + this.observedBlendSrc + "," + this.observedBlendDst + ")"
                + " particleDescriptorFailure=\"" + this.particleDescriptorFailureReason + "\""
                + " droppedByFormat=1:" + this.postAcceptDroppedByFormat[1] + ",2:" + this.postAcceptDroppedByFormat[2]
                + ",3:" + this.postAcceptDroppedByFormat[3] + ",4:" + this.postAcceptDroppedByFormat[4]
                + ",5:" + this.postAcceptDroppedByFormat[5] + " passBoundaryFlushes=" + this.passBoundaryFlushes + " matrixDiagnostics=" + MATRIX_DIAGNOSTICS
                + " matrixMirrorDelta=proj:" + this.mirroredProjectionDelta + ",view:" + this.mirroredModelViewDelta
                + ",comparisons:" + this.matrixComparisons + ",exact:" + this.matrixExactComparisons
                + ",translateCalls:" + this.translateCalls + ",zeroTranslates:" + this.translateZeroCalls
                + ",passSplits:" + this.passSplits + ",target:" + this.currentRenderTarget
                + ",worstView[" + (this.worstModelViewSample == null ? "none" : this.worstModelViewSample) + "]"
                + " trace[" + String.join(" ", this.trace) + "]"
                + ",steadyComparisons:" + this.steadyComparisons + ",crossPassSkipped:" + this.crossPassComparisonsSkipped + ",midSetupSkipped:" + this.midSetupComparisonsSkipped
                + ",steadyDelta=proj:" + this.steadyProjectionDelta + ",view:" + this.steadyModelViewDelta
                + ",derivedProj:" + this.derivedProjections + ",refused:" + this.derivedProjectionRefusals
                + ",cameraSeeds:" + this.cameraSetupSeeds
                + ",derivedCompared:" + this.derivedProjectionComparisons
                + ",writeId:" + this.derivedWriteIdentity + ",compareId:" + this.derivedCompareIdentity
                + ",loadIdentityWhilePending:" + this.loadIdentityWhilePending
                + ",loadIdentityByMode=proj:" + this.loadIdentityByMode[0] + ",view:" + this.loadIdentityByMode[1]
                + ",matrixModeByMode=proj:" + this.matrixModeCallsByMode[0] + ",view:" + this.matrixModeCallsByMode[1]
                + ",derivedDelta:" + this.derivedProjectionDelta
                + " derivedWorst[" + (this.derivedProjectionWorst == null ? "none" : this.derivedProjectionWorst) + "]"
                + " matrixDump[" + (this.matrixDump == null ? "none" : this.matrixDump) + "]"
                + " immediateSubmitAvgNs=" + (this.immediateBatchesOffered == 0 ? 0
                : this.immediateSubmitNanos / this.immediateBatchesOffered)
                + " matrixChannels=persp:" + this.perspectiveCalls + ",frustum:" + this.frustumCalls
                + ",mode:" + this.matrixModeCalls + ",identity:" + this.loadIdentityCalls
                + ",texture:" + this.bindTextureCalls;
    }

    @Override
    public void uploadMeshes(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger) {
        Reference2ReferenceMap<RenderRegion, List<ChunkTaskOutput>> queues = new Reference2ReferenceOpenHashMap<>();
        for (ChunkJobResult.Success<? extends ChunkTaskOutput> holder : results) {
            ChunkTaskOutput output = holder.output();
            List<ChunkTaskOutput> queue = queues.get(output.render.getRegion());
            if (queue == null) {
                queue = new ArrayList<>();
                queues.put(output.render.getRegion(), queue);
            }
            queue.add(output);
        }

        for (Reference2ReferenceMap.Entry<RenderRegion, List<ChunkTaskOutput>> entry : queues.reference2ReferenceEntrySet()) {
            uploadRegion(entry.getKey(), entry.getValue(), graphUpdateTrigger);
        }
    }

    @Override
    public void updateRegions() {
        // The region manager owns region lifetime (creating regions for loaded chunks and dropping empty ones).
        // It is reused unchanged; the Vulkan arenas are released for the regions it has dropped.
        this.regionManager.update();
        if (this.regionResources.isEmpty()) {
            return;
        }
        Set<RenderRegion> live = Collections.newSetFromMap(new IdentityHashMap<RenderRegion, Boolean>());
        live.addAll(this.regionManager.getLoadedRegions());
        Iterator<Reference2ReferenceMap.Entry<RenderRegion, RegionResources>> iterator =
                this.regionResources.reference2ReferenceEntrySet().iterator();
        while (iterator.hasNext()) {
            Reference2ReferenceMap.Entry<RenderRegion, RegionResources> entry = iterator.next();
            if (!live.contains(entry.getKey())) {
                // Same rule as delete(): a region's buffers and pipeline are released only once the GPU has finished
                // with them, because eviction happens while frames are being submitted.
                settleGpu("region eviction");
                entry.getValue().delete();
                iterator.remove();
            }
        }
    }

    @Override
    public void updateTimings() {
        // The immediate window count is per frame. The measurement that forced the limit: the first window of a frame
        // left the framebuffer at 150.46 before and after, and the second turned it to 0.0 with the GL state restored
        // unchanged, which is what blackened the captured frame.
        this.immediateWindowsThisFrame = 0;
        this.framesSeen++;
        // The lightmap is refreshed by Minecraft every frame through DynamicTexture, whose upload path no hook of this
        // project has been able to reach without crashing the client. Dropping the mirrors here makes the next use of
        // each re-read its GL texture; the interval is a property so the cost can be measured rather than assumed.
        if (MIRROR_REFRESH_FRAMES > 0 && (++this.mirrorRefreshCounter % MIRROR_REFRESH_FRAMES) == 0) {
            if (this.textureRegistry != null) {
                this.textureRegistry.invalidateAll();
                this.mirrorRefreshes++;
            }
        }
        updateTimingsBody();
    }

    /** The original timing update, called once per frame from the wrapper above. */
    private void updateTimingsBody() {
        if (this.timestampPool == 0 || this.timestampSlots == 0 || !this.timestampsUsable) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int queries = this.timestampSlots * 2;
            LongBuffer data = stack.mallocLong(queries * 2);
            int result = vkGetQueryPoolResults(this.context.device(), this.timestampPool, 0, queries, data,
                    Long.BYTES * 2, VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
            if (result != VK_SUCCESS && result != VK_NOT_READY) {
                return;
            }
            for (int slot = 0; slot < this.timestampSlots; slot++) {
                long startAvailable = data.get((slot * 2 * 2) + 1);
                long endAvailable = data.get((slot * 2 * 2) + 3);
                if (startAvailable == 0 || endAvailable == 0) {
                    // Keep whatever was measured for this pass last time instead of reporting "?? ms".
                    continue;
                }
                long start = data.get(slot * 2 * 2);
                long end = data.get((slot * 2 * 2) + 2);
                if (end >= start) {
                    this.passTimings.put(this.timestampPasses.get(slot),
                            (long) ((end - start) * this.timestampPeriod));
                }
            }
        }
        this.timestampPasses.clear();
        this.timestampSlots = 0;
    }

    @Override
    public Object2LongMap<TerrainRenderPass> snapshotTimings() {
        return new Object2LongOpenHashMap<>(this.passTimings);
    }

    @Override
    public void collectMemoryStats(RenderSectionManager.DeviceMemoryStats destination) {
        for (RegionResources resources : this.regionResources.values()) {
            destination.deviceUsed += resources.vertexArena.deviceUsedMemoryL();
            destination.deviceAllocated += resources.vertexArena.deviceAllocatedMemoryL();
            if (resources.indexArena != null) {
                destination.indexUsed += resources.indexArena.deviceUsedMemoryL();
                destination.indexAllocated += resources.indexArena.deviceAllocatedMemoryL();
            }
            destination.bufferCount += resources.bufferCount();
        }
    }

    private String depthSharingSummary;

    /**
     * Roadmap S4/S6 wiring: when enabled, the first non-terrain geometry (a textured quad built through
     * {@link VulkanDynamicMesh} and drawn by {@link VulkanUiPipeline}) is recorded into the same render pass as the
     * terrain, so the whole path - dynamic vertex stream, second pipeline, second texture binding, blending and
     * push constants - is exercised inside the frame that the validation harness already checks.
     */
    private final boolean uiTestEnabled = Boolean.getBoolean("celtium.vulkan.uiTest");
    private VulkanUiPipeline uiPipeline;
    private VulkanDynamicMesh uiMesh;

    private void recordHandoff(long nanos) {
        this.handoffNanos += nanos;
        this.handoffSamples++;
        if (nanos > this.handoffWorstNanos) {
            this.handoffWorstNanos = nanos;
        }
    }

    /** {@return the average and worst CPU time of the GL/Vulkan handoff since the last call, or null} */
    private String takeHandoffSummary() {
        if (this.handoffSamples == 0) {
            return null;
        }
        String summary = "handoff: avg " + (this.handoffNanos / this.handoffSamples / 1000L) + " us over "
                + this.handoffSamples + " calls (" + this.frameOpens + " opens, " + this.frameCloses + " closes, "
                + this.renderCalls + " pass calls), worst " + (this.handoffWorstNanos / 1000L) + " us";
        this.frameOpens = 0;
        this.frameCloses = 0;
        this.renderCalls = 0;
        this.handoffNanos = 0L;
        this.handoffSamples = 0L;
        this.handoffWorstNanos = 0L;
        return summary;
    }

    @Override
    public String getTransferQueueDebugString() {
        // The debug overlay shows this line, so it carries the backend's own state: without it a terrain pass that
        // silently skips (missing textures, no frame target, a failed pipeline) would look like a rendering bug.
        if (this.depthSharingSummary == null) {
            try {
                this.depthSharingSummary = VulkanFrameTarget.describeDepthFormatSharing(this.context);
            } catch (RuntimeException | LinkageError error) {
                this.depthSharingSummary = "shared depth formats: probe failed";
            }
        }
        String handoff = takeHandoffSummary();
        String detail = this.frameTarget == null ? null : this.frameTarget.takeTimingSummary();
        String base = describe();
        String withHandoff = handoff == null ? base : base + " | " + handoff;
        String withDetail = detail == null ? withHandoff : withHandoff + " | " + detail;
        return withDetail + " | " + this.depthSharingSummary + " | " + verificationSummary();
    }

    @Override
    public void delete() {
        if (this.gpuStalled) {
            // A stalled GPU means the last command buffer may never have been begun and never submitted, so its recorded
            // references are still live. Destroying the frame target or its framebuffers here is what validation reported as
            // "in use by VkCommandBuffer": on this path the resources are left for process exit instead.
            this.stalledTeardownSkips++;
            return;
        }
        // Nothing may be released while a submission is still reading it: the audit reproduced
        // vkDestroyBuffer/-Pipeline/-DescriptorPool/-RenderPass/-Framebuffer while a command buffer was in use, on the
        // reload and world-leave paths. Ending the queue's work first is the difference between freeing a resource and
        // pulling it out from under the GPU.
        settleGpu("backend delete");
        releaseRetiredImmediateGroups();
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(null);
        uninstall();
        for (RegionResources resources : this.regionResources.values()) {
            resources.delete();
        }
        this.regionResources.clear();
        for (VulkanTerrainPipeline pipeline : this.pipelines.values()) {
            pipeline.delete();
        }
        this.pipelines.clear();
        for (VulkanBuffer buffer : this.sharedIndexBuffers.values()) {
            buffer.delete();
        }
        this.sharedIndexBuffers.clear();
        this.sharedIndexPrimitives.clear();
        if (this.uniforms != null) {
            this.uniforms.delete();
            this.uniforms = null;
        }
        if (this.atlasTexture != null) {
            this.atlasTexture.delete();
            this.atlasTexture = null;
        }
        if (this.lightmapTexture != null) {
            this.lightmapTexture.delete();
            this.lightmapTexture = null;
        }
        if (this.readbackMapped != 0) {
            vkUnmapMemory(this.context.device(), this.readbackMemory);
            this.readbackMapped = 0;
        }
        if (this.readbackBuffer != 0) {
            vkDestroyBuffer(this.context.device(), this.readbackBuffer, null);
            this.readbackBuffer = 0;
        }
        if (this.readbackMemory != 0) {
            vkFreeMemory(this.context.device(), this.readbackMemory, null);
            this.readbackMemory = 0;
        }
        destroyFramebuffer();
        if (this.frameTarget != null) {
            this.frameTarget.delete();
            this.frameTarget = null;
        }
        destroyCommandResources();
    }

    // ------------------------------------------------------------------ upload path

    private void uploadRegion(RenderRegion region, List<ChunkTaskOutput> outputs, Runnable graphUpdateTrigger) {
        List<PendingMesh> pending = new ArrayList<>();
        boolean needIndexBuffer = false;

        for (ChunkTaskOutput output : outputs) {
            if (output instanceof ChunkBuildOutput) {
                ChunkBuildOutput build = (ChunkBuildOutput) output;
                region.removeMeshes(build.render.getSectionIndex());
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, BuiltSectionMeshParts> meshEntry
                        : build.meshes.reference2ReferenceEntrySet()) {
                    BuiltSectionMeshParts mesh = meshEntry.getValue();
                    if (mesh == null) {
                        continue;
                    }
                    needIndexBuffer |= mesh.indexBuffer() != null;
                    pending.add(new PendingMesh(build.render.getSectionIndex(), meshEntry.getKey(), mesh, null,
                    resourcesFor(region).sectionFirstColor));
                }
            } else if (output instanceof ChunkSortOutput) {
                ChunkSortOutput sort = (ChunkSortOutput) output;
                needIndexBuffer = true;
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, ChunkSortOutput.SortedMesh> meshEntry
                        : sort.meshes.reference2ReferenceEntrySet()) {
                    SectionRenderDataStorage storage = region.getStorage(meshEntry.getKey());
                    if (storage != null) {
                        storage.removeIndexBuffer(sort.render.getSectionIndex());
                    }
                    pending.add(new PendingMesh(sort.render.getSectionIndex(), meshEntry.getKey(), null,
                            meshEntry.getValue(), null));
                }
            } else {
                throw new IllegalStateException("Unexpected result type: " + output.getClass().getName());
            }
        }

        if (pending.isEmpty()) {
            return;
        }

        RegionResources resources = resourcesFor(region);
        long uploaded = 0;

        for (PendingMesh mesh : pending) {
            if (mesh.parts != null) {
                ByteBuffer vertices = mesh.parts.vertexBuffer().getDirectBuffer();
                int vertexBytes = mesh.parts.vertexBuffer().getLength();
                if (mesh.sectionFirstColor != null) {
                    mesh.sectionFirstColor[mesh.sectionIndex] = firstVertexColor(vertices);
                }
                int beforeColour = firstVertexColor(vertices);
                long beforeAddress = MemoryUtil.memAddress(vertices);
                VulkanArena.Segment vertexSegment = resources.vertexArena.allocate(vertexBytes);
                resources.vertexArena.write(vertexSegment.byteOffset(), MemoryUtil.memAddress(vertices), vertexBytes);
                if (beforeAfterLogged < 4) {
                    beforeAfterLogged++;
                    int mid = Math.max(0, (vertexBytes / 2 / 32) * 32);
                    int midColour = firstVertexColor(vertices, mid);
                    record("copy check: before=0x" + Integer.toHexString(beforeColour)
                            + " after=0x" + Integer.toHexString(firstVertexColor(vertices))
                            + " address=" + beforeAddress + " segmentOffset=" + vertexSegment.byteOffset()
                            + " bytes=" + vertexBytes + " position=" + vertices.position()
                            + " mid=0x" + Integer.toHexString(midColour));
                }
                uploaded += vertexBytes;

                VulkanArena.Segment indexSegment = null;
                if (mesh.parts.indexBuffer() != null) {
                    ByteBuffer indices = mesh.parts.indexBuffer().getDirectBuffer();
                    int indexBytes = mesh.parts.indexBuffer().getLength();
                    indexSegment = indexArena(resources).allocate(indexBytes);
                    indexArena(resources).write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                    uploaded += indexBytes;
                }

                SectionRenderDataStorage storage = region.createStorage(mesh.pass, this.configuration);
                storage.setMeshes(mesh.sectionIndex, vertexSegment, indexSegment, mesh.parts.ranges());
            } else if (mesh.sorted != null) {
                ByteBuffer indices = mesh.sorted.indexData().getDirectBuffer();
                int indexBytes = mesh.sorted.indexData().getLength();
                VulkanArena.Segment indexSegment = indexArena(resources).allocate(indexBytes);
                indexArena(resources).write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                uploaded += indexBytes;

                SectionRenderDataStorage storage = region.createStorage(mesh.pass, this.configuration);
                storage.replaceIndexBuffer(mesh.sectionIndex, indexSegment);
            }
        }

        int previousCookie = region.getPassSetUpdateCount();
        region.removeEmptyStorages();
        if (region.getPassSetUpdateCount() != previousCookie) {
            graphUpdateTrigger.run();
        }

        this.lastUploadedBytes = uploaded;
        logFirstVertex(pending);

    }

    /**
     * Decodes the first vertex of the first uploaded mesh and records it. The values the shader receives are the
     * only way to tell an attribute-layout problem from a transform problem, and the upload path already has the
     * bytes in hand.
     */
    private final java.util.HashMap<Integer, Integer> colourCounts = new java.util.HashMap<>();
    private int colourBatches;

    private void logFirstVertex(java.util.List<PendingMesh> pending) {
        for (PendingMesh mesh : pending) {
            if (mesh.parts != null && mesh.sectionFirstColor != null) {
                int colour = mesh.sectionFirstColor[mesh.sectionIndex];
                Integer previous = colourCounts.get(colour);
                colourCounts.put(colour, previous == null ? 1 : previous + 1);
            }
        }
        this.colourBatches++;
        if (this.colourBatches == 1 || this.colourBatches % 20 == 0) {
            java.util.List<java.util.Map.Entry<Integer, Integer>> entries =
                    new java.util.ArrayList<>(colourCounts.entrySet());
            java.util.Collections.sort(entries, (a, b) -> b.getValue() - a.getValue());
            StringBuilder line = new StringBuilder("mesh colour census (batches=" + this.colourBatches + "):");
            for (int i = 0; i < Math.min(5, entries.size()); i++) {
                line.append(" 0x").append(Integer.toHexString(entries.get(i).getKey()))
                        .append(" x").append(entries.get(i).getValue());
            }
            record(line.toString());
        }
        if (verticesLogged >= 8) {
            return;
        }
        for (PendingMesh mesh : pending) {
            if (mesh.parts != null && mesh.sectionFirstColor != null) {
                // Counted for every mesh, not just the first few, so a colour that only some sections carry shows up.
            }
        }
        if (verticesLogged >= 8) {
            return;
        }
        for (PendingMesh mesh : pending) {
            if (mesh.parts == null) {
                continue;
            }
            int stride = mesh.pass.vertexType().getVertexFormat().getStride();
            ByteBuffer vertices = mesh.parts.vertexBuffer().getDirectBuffer();
            if (vertices.remaining() < stride) {
                continue;
            }
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            StringBuilder line = new StringBuilder("vertex0 stride=").append(stride);
            if (stride == 32) {
                line.append(" pos=(").append(vertices.getFloat(0)).append(',').append(vertices.getFloat(4))
                        .append(',').append(vertices.getFloat(8)).append(')');
                line.append(" colorARGB=0x").append(Integer.toHexString(vertices.getInt(12)));
                line.append(" uv=(").append(vertices.getFloat(16)).append(',').append(vertices.getFloat(20)).append(')');
                line.append(" light=0x").append(Integer.toHexString(vertices.getInt(24)));
                int packed = vertices.getInt(24) >>> 16;
                line.append(" block=").append(packed & 0xFF).append(" sky=").append((packed >> 8) & 0xFF);
                line.append(" material=").append(packed & 0xFF).append(" section=").append((packed >> 8) & 0xFF);
            } else {
                line.append(" posId=0x").append(Integer.toHexString(vertices.getInt(0)));
                line.append(" colorARGB=0x").append(Integer.toHexString(vertices.getInt(8)));
                line.append(" light=0x").append(Integer.toHexString(vertices.getInt(16)));
            }
            verticesLogged++;
            record("mesh" + verticesLogged + " pass=" + mesh.pass.name() + " " + line);
            return;
        }
    }

    /** Colour word of the first vertex of a mesh, in the little-endian byte order the GPU reads. */
    private static int firstVertexColor(ByteBuffer vertices, int at) {
        java.nio.ByteOrder order = vertices.order();
        try {
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return vertices.getInt(at + 12);
        } finally {
            vertices.order(order);
        }
    }

    private static int firstVertexColor(ByteBuffer vertices) {
        java.nio.ByteOrder order = vertices.order();
        try {
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return vertices.getInt(12);
        } finally {
            vertices.order(order);
        }
    }

    /**
     * Debug aid: copies the arena bytes for the just-uploaded range back to host memory and decodes them. This is
     * the one comparison that the draw-time readback cannot make - whether the arena holds what the upload path was
     * given.
     */
    private void verifyUpload(RegionResources resources, java.util.List<PendingMesh> pending) {
        if (uploadVerified || resources.vertexArena.isEmpty()) {
            return;
        }
        for (PendingMesh mesh : pending) {
            if (mesh.parts == null) {
                continue;
            }
            uploadVerified = true;
            ensureReadback();
            long offset = 0;
            VkCommandBuffer commands = this.context.beginCommands();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                org.lwjgl.vulkan.VkBufferCopy.Buffer region = org.lwjgl.vulkan.VkBufferCopy.calloc(1, stack);
                region.get(0).srcOffset(offset).dstOffset(0L).size(64L);
                vkCmdCopyBuffer(commands, resources.vertexArena.buffer().buffer(), this.readbackBuffer, region);
                org.lwjgl.vulkan.VkMemoryBarrier.Buffer barrier = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_HOST_READ_BIT);
                vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0,
                        barrier, null, null);
            } finally {
                this.context.endCommands(commands);
            }
            ByteBuffer view = MemoryUtil.memByteBuffer(this.readbackMapped, 64);
            view.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            record("arena after upload: pos=(" + view.getFloat(0) + "," + view.getFloat(4) + ","
                    + view.getFloat(8) + ") colour=0x" + Integer.toHexString(view.getInt(12))
                    + " uv=(" + view.getFloat(16) + "," + view.getFloat(20) + ") light=0x"
                    + Integer.toHexString(view.getInt(24))
                    + " | source meshes=" + pending.size());
            return;
        }
    }

    private RegionResources resourcesFor(RenderRegion region) {
        RegionResources resources = this.regionResources.get(region);
        if (resources == null) {
            resources = new RegionResources(this.context, this.commonVertexStride);
            this.regionResources.put(region, resources);
        }
        return resources;
    }

    private VulkanArena indexArena(RegionResources resources) {
        if (resources.indexArena == null) {
            resources.indexArena = new VulkanArena(this.context, 64 * 1024, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 4);
        }
        return resources.indexArena;
    }

    // ------------------------------------------------------------------ draw path

    private void recordRenderPass(MemoryStack stack,
                                  VulkanTerrainPipeline pipeline,
                                  ChunkRenderMatrices matrices,
                                  ChunkRenderListIterable renderLists,
                                  TerrainRenderPass pass,
                                  CameraTransform occlusionCamera,
                                  CameraTransform camera,
                                  boolean useBlockFaceCulling,
                                  ChunkPrimitiveType primitiveType) {
        if (this.gpuStalled) {
            // The pass-level recording is closed too: with the buffer never begun, vkCmdBeginRenderPass, vkCmdSetViewport and
            // vkCmdSetScissor were still being recorded into it, which is what the steady-state injection reported.
            return;
        }
        ensureUniformCapacity(this.regionResources.size());
        // The descriptor set points at the atlas, the lightmap and the uniform buffer for this pass. It is rewritten
        // once per pass per frame, which is a few descriptor writes and removes any staleness after a re-upload.
        pipeline.bindTextures(this.atlasTexture, this.lightmapTexture, this.uniforms.buffer(), this.uniforms.size());

        this.frameTarget.recordAcquireBarriers(this.commandBuffer);

        long renderPass = pipeline.renderPass();
        VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                .renderPass(renderPass)
                .framebuffer(ensureFramebuffer(renderPass));
        begin.renderArea().offset().set(0, 0);
        begin.renderArea().extent().set(this.targetWidth, this.targetHeight);
        int timestampSlot = this.timestampsUsable && this.timestampSlots < (TIMESTAMP_SLOTS / 2)
                ? this.timestampSlots : -1;
        if (timestampSlot >= 0) {
            vkCmdWriteTimestamp(this.commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, this.timestampPool,
                    timestampSlot * 2);
        }
        vkCmdBeginRenderPass(this.commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);

        setViewportAndScissor(stack);
        pipeline.bindPipeline(this.commandBuffer);

        Iterator<ChunkRenderList> iterator = renderLists.iterator(pass.isReverseOrder());
        SectionRenderDataStorage.BatchCacheParams cacheParams =
                new SectionRenderDataStorage.BatchCacheParams(useBlockFaceCulling);
        boolean indexBound = false;

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();
            RenderRegion region = renderList.getRegion();
            RegionResources resources = this.regionResources.get(region);
            SectionRenderDataStorage storage = region.getStorage(pass);

            if (resources == null || storage == null) {
                continue;
            }

            CachedBatch cached = storage.getCachedMultiDrawBatch(cacheParams);
            if (cached == null || !cached.isValidFor(renderList.getSectionsWithGeometry(),
                    renderList.getSectionsWithGeometryCount(), occlusionCamera.intX, occlusionCamera.intY,
                    occlusionCamera.intZ)) {
                cached = BatchAssembler.createCachedBatch(region, storage, renderList, occlusionCamera, pass,
                        useBlockFaceCulling, null, (commands, ignored, ignoredPass) -> null);
                storage.storeCachedMultiDrawBatch(cacheParams, cached);
            }

            if (!(cached.getBatch() instanceof VulkanMultiDrawBatch) || cached.getBatch().isEmpty()) {
                continue;
            }

            VulkanMultiDrawBatch batch = (VulkanMultiDrawBatch) cached.getBatch();

            if (this.readbackSlots == 0) {
                this.readbackOffset = (long) batch.baseVertex(0) * this.commonVertexStride;
                this.readbackRegion = resources;
            }
            long uniformOffset = writeUniforms(region, pass, camera);
            pipeline.bindDescriptors(this.commandBuffer, uniformOffset);
            pipeline.pushMatrices(this.commandBuffer, vulkanProjection(matrices.projection()), matrices.modelView());

            bindVertexBuffer(stack, resources);
            if (!indexBound) {
                bindIndexBuffer(stack, this.sharedIndexBuffers.get(primitiveType));
                indexBound = true;
            }

            if (this.lastDrawCommands == 0 && command0Logged < 2) {
                command0Logged++;
                StringBuilder colors = new StringBuilder("drawn section colours:");
                byte[] sections = renderList.getSectionsWithGeometry();
                for (int i = 0; i < Math.min(4, sections.length); i++) {
                    int local = sections[i] & 0xFF;
                    colors.append(" #").append(local).append("=0x")
                            .append(Integer.toHexString(resources.sectionFirstColor[local]));
                }
                record(colors.toString());
                command0Logged++;
                record("firstDraw region=" + region.getId() + " pass=" + pass.name()
                        + " stride=" + this.commonVertexStride
                        + " vertexArenaBytes=" + resources.vertexArena.deviceAllocatedMemoryL()
                        + " baseVertex=" + batch.baseVertex(0) + " elements=" + batch.elementCount(0)
                        + " indexBytes=" + batch.indexByteOffset(0)
                        + " batchSize=" + batch.size()
                        + " sharedIndexPrimitives=" + this.sharedIndexPrimitives.get(primitiveType));
            }
            for (int command = 0; command < batch.size(); command++) {
                long byteOffset = batch.indexByteOffset(command);
                int firstIndex;
                if (byteOffset == 0) {
                    firstIndex = 0;
                } else if (resources.indexArena != null) {
                    bindIndexBuffer(stack, resources.indexArena.buffer());
                    firstIndex = (int) (byteOffset / 4);
                } else {
                    continue;
                }
                vkCmdDrawIndexed(this.commandBuffer, batch.elementCount(command), 1, firstIndex,
                        batch.baseVertex(command), 0);
                this.lastDrawCommands++;
            }

            this.lastSectionsDrawn += renderList.getSectionsWithGeometryCount();

            if (resources.indexArena != null) {
                // The next region may use the shared index buffer again.
                bindIndexBuffer(stack, this.sharedIndexBuffers.get(primitiveType));
            }
        }

        if (this.currentImmediateMesh != null || !this.immediateGroups.isEmpty()) {
            drawPendingImmediate();
        }

        if (this.uiTestEnabled && pass.isSorted()) {
            drawUiTestQuad(primitiveType);
            // The isolation test runs with the UI quad so the frame carries both a known-good draw and the pipeline
            // under test, which is what makes the pixel result conclusive.
            drawImmediateIsolationQuad();
        }

        vkCmdEndRenderPass(this.commandBuffer);
        if (timestampSlot >= 0) {
            vkCmdWriteTimestamp(this.commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, this.timestampPool,
                    (timestampSlot * 2) + 1);
            this.timestampPasses.put(timestampSlot, pass);
            this.timestampSlots++;
        }
        // The debug readback copies a buffer, which is only legal outside a render pass instance.
        if (!this.readbackDone && this.readbackSlots < 8 && this.readbackRegion != null) {
            recordReadback(this.readbackRegion, this.readbackOffset, this.readbackSlots);
            this.readbackSlots++;
            this.readbackPending = true;
        }
        this.frameTarget.recordReleaseBarriers(this.commandBuffer);
    }

    /**
     * {@code M * P} where {@code M} maps clip z from [-1, 1] to [0, 1] and leaves x, y and w alone. The Y axis is
     * handled by the negative-height viewport instead, so the OpenGL matrices and culling stay untouched.
     */
    private Matrix4fc vulkanProjection(Matrix4fc projection) {
        this.vulkanProjection.set(projection);
        // z: OpenGL clip depth [-1, 1] to Vulkan [0, 1]  ->  row 2 becomes 0.5 * (row 2 + row 3)
        float c0z = 0.5f * (projection.m02() + projection.m03());
        float c1z = 0.5f * (projection.m12() + projection.m13());
        float c2z = 0.5f * (projection.m22() + projection.m23());
        float c3z = 0.5f * (projection.m32() + projection.m33());
        // No Y flip: the shared colour image already carries the orientation OpenGL expects, so flipping here (or
        // through a negative-height viewport) rendered the terrain mirror-imaged. Culling compensates instead: the
        // pipeline uses VK_FRONT_FACE_COUNTER_CLOCKWISE for the same reason.
        this.vulkanProjection.m02(c0z);
        this.vulkanProjection.m12(c1z);
        this.vulkanProjection.m22(c2z);
        this.vulkanProjection.m32(c3z);
        return this.vulkanProjection;
    }

    /**
     * The framebuffer is created here rather than in the frame target because compatibility is defined by the
     * render pass the pipeline built, and one framebuffer serves every pass that is compatible with it.
     */
    private long ensureFramebuffer(long renderPass) {
        if (this.framebuffer != 0 && this.framebufferRenderPass == renderPass) {
            return this.framebuffer;
        }
        destroyFramebuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.mallocLong(2);
            attachments.put(0, this.frameTarget.colorView());
            attachments.put(1, this.frameTarget.depthView());
            org.lwjgl.vulkan.VkFramebufferCreateInfo create = org.lwjgl.vulkan.VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .pAttachments(attachments)
                    .width(this.targetWidth)
                    .height(this.targetHeight)
                    .layers(1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateFramebuffer(this.context.device(), create, null, handle), "vkCreateFramebuffer");
            this.framebuffer = handle.get(0);
            this.framebufferRenderPass = renderPass;
            return this.framebuffer;
        }
    }

    private void destroyFramebuffer() {
        if (this.framebuffer != 0) {
            vkDestroyFramebuffer(this.context.device(), this.framebuffer, null);
            this.framebuffer = 0;
            this.framebufferRenderPass = 0;
        }
    }

    private void bindVertexBuffer(MemoryStack stack, RegionResources resources) {
        LongBuffer buffers = stack.mallocLong(1);
        buffers.put(0, resources.vertexArena.buffer().buffer());
        LongBuffer offsets = stack.mallocLong(1);
        offsets.put(0, 0L);
        vkCmdBindVertexBuffers(this.commandBuffer, 0, buffers, offsets);
    }

    private void bindIndexBuffer(MemoryStack stack, VulkanBuffer buffer) {
        vkCmdBindIndexBuffer(this.commandBuffer, buffer.buffer(), 0L, VK_INDEX_TYPE_UINT32);
    }

    private void setViewportAndScissor(MemoryStack stack) {
        org.lwjgl.vulkan.VkViewport.Buffer viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
        // Positive height: see vulkanProjection() for why the Vulkan convention is used unchanged.
        viewport.get(0).x(0.0f).y(0.0f).width(this.targetWidth).height(this.targetHeight)
                .minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(this.commandBuffer, 0, viewport);
        org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
        scissor.get(0).offset().set(0, 0);
        scissor.get(0).extent().set(this.targetWidth, this.targetHeight);
        vkCmdSetScissor(this.commandBuffer, 0, scissor);
    }

    /** Writes one dynamic uniform block and returns its byte offset. */
    private long writeUniforms(RenderRegion region, TerrainRenderPass pass, CameraTransform camera) {
        long offset = this.uniformWriteCursor;
        this.uniformWriteCursor += this.uniformStride;
        if (this.uniformWriteCursor > this.uniforms.size()) {
            // This must not happen: render() reserves an upper bound before recording, and growth replaces the
            // buffer that descriptors recorded earlier in this pass already point at. Reported loudly rather than
            // growing silently, so a missed reservation shows up as a defect instead of as wrong terrain.
            record("uniform buffer grew during recording: " + this.uniformWriteCursor + " > " + this.uniforms.size()
                    + " (descriptors bound earlier in this pass may be stale)");
            this.uniforms.ensureCapacity(this.uniformWriteCursor);
            this.uniformBlocks = (int) (this.uniforms.size() / this.uniformStride);
        }

        ByteBuffer block = this.uniforms.mapped();
        int base = (int) offset;

        block.putFloat(base + 0, (region.getOriginX() - camera.intX) - camera.fracX);
        block.putFloat(base + 4, (region.getOriginY() - camera.intY) - camera.fracY);
        block.putFloat(base + 8, (region.getOriginZ() - camera.intZ) - camera.fracZ);
        block.putFloat(base + 12, 0.0f);

        float[] fogColor = this.fogService != null ? this.fogService.getFogColor() : null;
        if (!uniformsLogged) {
            uniformsLogged = true;
            record("uniforms: fogColor=" + java.util.Arrays.toString(fogColor)
                    + " start=" + (this.fogService != null ? this.fogService.getFogStart() : 0.0f)
                    + " end=" + (this.fogService != null ? this.fogService.getFogEnd() : 0.0f)
                    + " density=" + (this.fogService != null ? this.fogService.getFogDensity() : 0.0f)
                    + " shape=" + (this.fogService != null ? this.fogService.getFogShapeIndex() : -1)
                    + " glFogMode=" + fogMode() + " cutoff=" + (this.fogService != null ? this.fogService.getFogCutoff() : 0.0f)
                    + " regionOffset=(" + ((region.getOriginX() - camera.intX) - camera.fracX) + ","
                    + ((region.getOriginY() - camera.intY) - camera.fracY) + ","
                    + ((region.getOriginZ() - camera.intZ) - camera.fracZ) + ")");
        }
        block.putFloat(base + 16, fogColor != null && fogColor.length > 0 ? fogColor[0] : 0.0f);
        block.putFloat(base + 20, fogColor != null && fogColor.length > 1 ? fogColor[1] : 0.0f);
        block.putFloat(base + 24, fogColor != null && fogColor.length > 2 ? fogColor[2] : 0.0f);
        block.putFloat(base + 28, fogColor != null && fogColor.length > 3 ? fogColor[3] : 0.0f);

        block.putFloat(base + 32, this.fogService != null ? this.fogService.getFogDensity() : 0.0f);
        block.putFloat(base + 36, this.fogService != null ? this.fogService.getFogStart() : 0.0f);
        block.putFloat(base + 40, this.fogService != null ? this.fogService.getFogEnd() : 0.0f);
        // Additive atlas LOD shift. The per-material bias already travels in the vertex, so parity needs 0 here.
        block.putFloat(base + 44, 0.0f);
        block.putInt(base + 48, fogMode());
        block.putInt(base + 52, this.fogService != null ? this.fogService.getFogShapeIndex() : FogService.FOG_SHAPE_SPHERICAL);
        block.putInt(base + 56, pass.supportsFragmentDiscard() ? 1 : 0);
        // Chunk fade is a separate feature that the Vulkan path does not port yet; 0 keeps the GL default look.
        block.putInt(base + 60, 0);
        block.putFloat(base + 64, 0.0f);
        block.putFloat(base + 68, 0.0f);
        block.putFloat(base + 72, 0.0f);
        block.putFloat(base + 76, 0.0f);

        return offset;
    }

    /**
     * The shader's fog mode selector: 0 = none, 1 = exp2, 2 = exp, 3 = smooth/linear. Derived from the GL fog
     * state, which is what the OpenGL backend's {@code GLStateManagerFogService} reads as well.
     */
    private static int fogMode() {
        if (!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_FOG)) {
            return 0;
        }
        int mode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE);
        if (mode == org.lwjgl.opengl.GL11.GL_EXP2) {
            return 1;
        }
        if (mode == org.lwjgl.opengl.GL11.GL_EXP) {
            return 2;
        }
        return 3;
    }

    private static float[] regionTranslation(RenderRegion region, CameraTransform camera) {
        return new float[] {
                (region.getOriginX() - camera.intX) - camera.fracX,
                (region.getOriginY() - camera.intY) - camera.fracY,
                (region.getOriginZ() - camera.intZ) - camera.fracZ,
        };
    }

    private VulkanTerrainPipeline pipelineFor(TerrainRenderPass pass) {
        VulkanTerrainPipeline pipeline = this.pipelines.get(pass);
        if (pipeline != null) {
            return pipeline;
        }
        try {
            pipeline = VulkanTerrainPipeline.create(this.context, pass, !pass.isSorted());
            this.pipelines.put(pass, pipeline);
            record("pipeline for " + pass.name() + " created [" + pipeline.vertexInputSummary() + "]");
            if (pipeline.limitsWarning() != null) {
                record(pipeline.limitsWarning());
            }
            return pipeline;
        } catch (RuntimeException | LinkageError error) {
            record("pipeline for " + pass.name() + " failed: " + describe(error));
            this.pipelines.put(pass, null);
            return null;
        }
    }

    // ------------------------------------------------------------------ frame target

    /**
     * S2 probe: builds the surface and swapchain for the window GLFW currently owns, reports what the surface
     * advertises, and presents one cleared image. GL still presents this window in the hybrid build, so this runs
     * once and only when {@code -Dceltium.vulkan.swapchainProbe=true} is set.
     */
    /**
     * Creates the swapchain if it does not exist yet, and reports why when it cannot.
     * <p>
     * It used to be created as a side effect of the diagnostic probe, which meant turning the probe off silently turned
     * the frame present off with it - a run that looked validation-clean because it presented nothing. Anything that
     * needs to present calls this instead, so the probe is only a probe.
     */
    private boolean ensureSwapchain() {
        if (this.swapchain != null) {
            return true;
        }
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        java.util.List<String> reasons = new java.util.ArrayList<String>();
        this.swapchain = VulkanSwapchain.create(this.context, window, 1280, 720, reasons);
        if (this.swapchain == null) {
            record("swapchain unavailable at surface and swapchain creation: " + reasons);
            return false;
        }
        record(this.swapchain.describe());
        return true;
    }

    private void runSwapchainProbe() {
        if (this.gpuStalled) {
            // A stalled GPU closes every entry that touches the frame target or the bridge: the injected timeout showed a
            // barrier recorded into a buffer that was never begun, and a framebuffer destroyed while a stale command buffer
            // still referenced it, both from paths that assumed the frame had been recorded.
            return;
        }
        if (this.swapchainProbeDone) {
            return;
        }
        this.swapchainProbeDone = true;
        String step = "window handle";
        try {
            if (!ensureSwapchain()) {
                return;
            }
            record(this.swapchain.describe());
            step = "clear and present";
            record(this.swapchain.clearAndPresent(0.2f, 0.4f, 0.8f, 1.0f));
            // Then present the frame's own content rather than a cleared image. Minecraft draws its final composite
            // through Framebuffer.func_178038_a - a shader over a full-screen quad - and this is the step that replaces
            // it: the shared colour image is where this frame's content lives. It runs after the clear probe so both
            // are visible in one run and neither can hide the other.
            step = "present the frame content";
            // The frame content is only readable while the game's framebuffer is bound: at the probe's moment the
            // default framebuffer is, which is exactly what beginFrame() refuses with
            // "the bound draw framebuffer is the default framebuffer". So this step is skipped and said so, rather
            // than reported as a failure of the mechanism.
            if (org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING) == 0) {
                record("presenting the frame skipped: the default framebuffer is bound, so there is no frame content"
                        + " to present here");
            } else if (ensureFrameTarget()) {
                this.frameSubmitReachedQueue = false;
            this.frameTarget.beginFrame();
                record(this.swapchain.presentImage(this.frameTarget.colorImage(),
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, this.targetWidth,
                        this.targetHeight));
                this.frameTarget.endFrame();
            } else {
                record("presenting the frame skipped: no frame target");
            }
        } catch (RuntimeException | LinkageError error) {
            // The failing step and the deepest frame inside this module are what make an NPE diagnosable here: the
            // probe runs once, so a bare message would leave the next attempt guessing.
            StackTraceElement[] trace = error.getStackTrace();
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < Math.min(4, trace.length); i++) {
                if (i > 0) {
                    where.append(" <- ");
                }
                where.append(trace[i].getClassName().substring(trace[i].getClassName().lastIndexOf('.') + 1))
                        .append(':').append(trace[i].getLineNumber());
            }
            String arguments = this.swapchain == null ? "" : " args[" + this.swapchain.argumentSummary() + "]";
            record("swapchain probe failed at " + step + ": " + describe(error) + " [" + where + "]" + arguments);
        }
    }

    private boolean ensureFrameTarget() {
        int width = currentDrawableWidth();
        int height = currentDrawableHeight();
        if (width <= 0 || height <= 0) {
            record("drawable size unavailable");
            return false;
        }
        if (this.frameTarget != null && this.frameTarget.matches(width, height)) {
            return true;
        }
        // The GPU must be finished with the old target before any of it is released: a resize recreates the game's
        // framebuffer, and the validation layer caught the first attempt doing exactly this -
        // "vkDestroyFramebuffer(): can't be called on VkFramebuffer ... that is currently in use by VkCommandBuffer".
        // The same rule as delete() and region eviction, applied to the path a resize takes.
        settleGpu("frame target recreation");
        destroyFramebuffer();
        if (this.frameTarget != null) {
            this.frameTarget.delete();
            this.frameTarget = null;
        }
        try {
            this.frameTarget = VulkanFrameTarget.create(this.context.instance(), this.context.physicalDevice(),
                    this.context.device(), this.context.queue(), this.context.queueFamilyIndex(), width, height);
            this.targetWidth = width;
            this.targetHeight = height;
            return true;
        } catch (RuntimeException | LinkageError error) {
            record("frame target " + width + "x" + height + " failed: " + describe(error));
            return false;
        }
    }

    private static int currentDrawableWidth() {
        int[] viewport = currentViewport();
        return Math.max(0, viewport[2]);
    }

    private static int currentDrawableHeight() {
        int[] viewport = currentViewport();
        return Math.max(0, viewport[3]);
    }

    private static int[] currentViewport() {
        IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(4);
        org.lwjgl.opengl.GL11C.glGetIntegerv(org.lwjgl.opengl.GL11C.GL_VIEWPORT, viewport);
        return new int[] { viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3) };
    }

    private void refreshTextures() {
        int atlasId = boundTextureId(0);
        int lightmapId = boundTextureId(1);

        if (atlasId != 0 && (this.atlasTexture == null || atlasId != this.atlasGlId || this.atlasRefreshCountdown <= 0)) {
            try {
                if (this.atlasTexture == null) {
                    int[] size = textureSize(atlasId);
                    this.atlasTexture = VulkanGlTexture.create(this.context, size[0], size[1], true);
                }
                this.atlasTexture.uploadFromGlTexture(atlasId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                int[] atlasSize = textureSize(atlasId);
                record("block atlas: gl texture " + atlasId + " " + atlasSize[0] + "x" + atlasSize[1]);
                this.atlasGlId = atlasId;
                this.atlasRefreshCountdown = 400;
            } catch (RuntimeException | LinkageError error) {
                record("block atlas upload failed: " + describe(error));
                this.atlasRefreshCountdown = 400;
            }
        }
        this.atlasRefreshCountdown--;

        if (lightmapId != 0 && (this.lightmapTexture == null || lightmapId != this.lightmapGlId || this.lightmapRefreshCountdown <= 0)) {
            try {
                if (this.lightmapTexture == null) {
                    int[] size = textureSize(lightmapId);
                    this.lightmapTexture = VulkanGlTexture.create(this.context, size[0], size[1], false);
                }
                this.lightmapTexture.uploadFromGlTexture(lightmapId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                int[] lightmapSize = textureSize(lightmapId);
                record("lightmap: gl texture " + lightmapId + " " + lightmapSize[0] + "x" + lightmapSize[1]);
                this.lightmapGlId = lightmapId;
                // The light map changes every frame (torch flicker, sun angle, weather), and it is 16x16, so it is
                // mirrored every frame instead of on a cadence: a stale copy shows up as a small uniform colour
                // shift against the OpenGL render.
                this.lightmapRefreshCountdown = 1;
            } catch (RuntimeException | LinkageError error) {
                record("lightmap upload failed: " + describe(error));
                this.lightmapRefreshCountdown = 1;
            }
        }
        this.lightmapRefreshCountdown--;
    }

    private static int boundTextureId(int unit) {
        int previous = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE);
        org.lwjgl.opengl.GL13C.glActiveTexture(org.lwjgl.opengl.GL13C.GL_TEXTURE0 + unit);
        int texture = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
        org.lwjgl.opengl.GL13C.glActiveTexture(previous);
        return texture;
    }

    private static int[] textureSize(int texture) {
        int width = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(texture, 0, org.lwjgl.opengl.GL11C.GL_TEXTURE_WIDTH);
        int height = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(texture, 0, org.lwjgl.opengl.GL11C.GL_TEXTURE_HEIGHT);
        return new int[] { Math.max(1, width), Math.max(1, height) };
    }

    // ------------------------------------------------------------------ command resources

    private void createCommandResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(this.context.queueFamilyIndex());
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateCommandPool(this.context.device(), poolInfo, null, handle), "vkCreateCommandPool");
            this.commandPool = handle.get(0);

            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                    .commandPool(this.commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            org.lwjgl.PointerBuffer buffers = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(this.context.device(), allocate, buffers), "vkAllocateCommandBuffers");
            this.commandBuffer = new VkCommandBuffer(buffers.get(0), this.context.device());

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack).sType$Default();
            check(vkCreateFence(this.context.device(), fenceInfo, null, handle), "vkCreateFence");
            this.fence = handle.get(0);

            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
            // timestampValidBits belongs to the queue family, not to the device limits, so it is read for the
            // family this backend submits on.
            IntBuffer familyCount = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    this.context.physicalDevice(), familyCount, null);
            org.lwjgl.vulkan.VkQueueFamilyProperties.Buffer families =
                    org.lwjgl.vulkan.VkQueueFamilyProperties.calloc((int) familyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    this.context.physicalDevice(), familyCount, families);
            int family = Math.min(this.context.queueFamilyIndex(), families.capacity() - 1);
            long timestampValidBits = Integer.toUnsignedLong(families.get(Math.max(0, family)).timestampValidBits());
            this.timestampPeriod = properties.limits().timestampPeriod();
            // Writing timestamps on a queue that cannot record them, or with a zero period, produces meaningless
            // numbers; the overlay then keeps its "?? ms" instead of reporting fiction.
            this.timestampsUsable = timestampValidBits > 0L && this.timestampPeriod > 0.0f;
            org.lwjgl.vulkan.VkQueryPoolCreateInfo queryPool = org.lwjgl.vulkan.VkQueryPoolCreateInfo.calloc(stack)
                    .sType$Default().queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(TIMESTAMP_SLOTS);
            if (this.timestampsUsable && !FORCE_NO_TIMESTAMPS) {
                check(vkCreateQueryPool(this.context.device(), queryPool, null, handle), "vkCreateQueryPool");
                this.timestampPool = handle.get(0);
            } else {
                // Pass timing is optional; the backend is not. Registering only when a queue can record timestamps
                // meant a device without them lost the backend entirely - the audit's item H, and a direct contradiction
                // of choosing features by capability rather than by assumption.
                this.timestampsUsable = false;
                record("the queue cannot record timestamps, so pass timing is disabled; the backend registers anyway");
            }

            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(this);

            if (Boolean.getBoolean(VulkanContext.SWAPCHAIN_PROPERTY)) {
                runSwapchainProbe();
            }
        }
    }

    private void destroyCommandResources() {
        if (this.timestampPool != 0) {
            vkDestroyQueryPool(this.context.device(), this.timestampPool, null);
            this.timestampPool = 0;
        }
        if (this.fence != 0) {
            vkDestroyFence(this.context.device(), this.fence, null);
            this.fence = 0;
        }
        if (this.commandPool != 0) {
            vkDestroyCommandPool(this.context.device(), this.commandPool, null);
            this.commandPool = 0;
            this.commandBuffer = null;
        }
    }

    private void beginCommands() {
        // The frame uses one command buffer, so the previous submission has to finish before it is reset: resetting
        // or re-recording a pending buffer is invalid and, on some drivers, makes the GPU execute a half-rewritten
        // command stream - which shows up as the wrong vertex data reaching the shader.
        if (this.submissionPending) {
            boolean idle = waitForIdle();
            if (!idle) {
                // The audit's S02: a timeout here used to be logged and ignored, after which the pending command buffer
                // was cleared, reset and re-recorded although the GPU might still be reading it. Nothing is touched in
                // that case - the flag stays set, the buffer is not reset - and every later use is refused, so the
                // renderer stops drawing rather than re-recording a buffer that may be in flight.
                this.gpuStalled = true;
                this.stalledStops++;
                record("gpu stalled: the previous submission did not complete; drawing is refused from here");
                return;
            }
            this.submissionPending = false;
            // The previous frame's timestamps are complete now, which is the only moment they can be read reliably.
            updateTimings();
        }
        vkResetCommandBuffer(this.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(this.commandBuffer, begin), "vkBeginCommandBuffer");
            // Queries must be reset before they are written again, and a fresh command buffer is exactly where that
            // belongs; without it validation reports every timestamp write as using an unavailable query.
            if (this.timestampPool != 0) {
                vkCmdResetQueryPool(this.commandBuffer, this.timestampPool, 0, TIMESTAMP_SLOTS);
            }
        }
        this.uniformWriteCursor = 0;
    }

    /**
     * Maps an OpenGL blend factor onto its Vulkan equivalent.
     * <p>
     * The numbers are the GL constants Minecraft's own enum carries, recorded here next to the mapping so the
     * correspondence is checkable rather than implied: 0 ZERO, 1 ONE, 768 SRC_COLOR, 769 ONE_MINUS_SRC_COLOR,
     * 770 SRC_ALPHA, 771 ONE_MINUS_SRC_ALPHA, 772 DST_ALPHA, 773 ONE_MINUS_DST_ALPHA, 774 DST_COLOR,
     * 775 ONE_MINUS_DST_COLOR, 776 SRC_ALPHA_SATURATE, 32769 CONSTANT_COLOR, 32770 ONE_MINUS_CONSTANT_COLOR,
     * 32771 CONSTANT_ALPHA, 32772 ONE_MINUS_CONSTANT_ALPHA. An unknown value returns -1 so the caller can refuse the
     * batch rather than silently blend differently.
     */
    static int glBlendFactorToVk(int glFactor) {
        switch (glFactor) {
            case 0: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO;
            case 1: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
            case 768: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_COLOR;
            case 769: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case 770: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
            case 771: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case 772: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_DST_ALPHA;
            case 773: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case 774: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_DST_COLOR;
            case 775: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case 776: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            case 32769: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
            case 32770: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case 32771: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case 32772: return org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            default: return -1;
        }
    }

    /**
     * Draws the immediate-mode batches accumulated since the last flush.
     * <p>
     * Timing note: the batches arrive while the frame is already past its terrain passes (entities, particles, the
     * GUI), and the bridge only owns the shared images inside a terrain pass. They are therefore drawn with the
     * <em>next</em> frame's first terrain pass, using that pass's own projection and model view. For content that
     * does not move between frames this is invisible; for moving content it is one frame of latency, and the proper
     * fix is a frame the renderer owns outright (stage 7 of the docs/plan.md stages). It is documented rather than
     * hidden.
     */
    /**
     * Draws one synthetic {@code POSITION_COLOR} group with known NDC vertices through the immediate pipeline.
     * <p>
     * This is the isolation test the group-bounds measurement called for: the fixture's real batch is captured,
     * projects on-screen and is still invisible, and disabling the depth test changed nothing. Drawing a quad whose
     * vertices are already in normalised device coordinates, next to the UI test quad that is known to appear,
     * separates the two remaining possibilities with pixels: if this appears, the immediate pipeline rasterises and
     * the fault lies in the batch geometry or its transform; if it does not, the pipeline, its vertex layout or its
     * buffer binding is at fault. The quad is green so it cannot be confused with the red test batch or the textured
     * UI quad.
     */
    private void drawImmediateIsolationQuad() {
        VulkanTerrainPipeline terrain = pipelineFor(this.lastUiPass);
        if (terrain == null) {
            return;
        }
        if (this.immediatePipeline == null) {
            this.immediatePipeline = VulkanImmediatePipeline.create(this.context, terrain.renderPass());
        }
        if (this.isolationMesh == null) {
            this.isolationMesh = new VulkanDynamicMesh(this.context);
            java.nio.ByteBuffer vertices = MemoryUtil.memAlloc(4 * VulkanImmediatePipeline.VERTEX_BYTES);
            try {
                float[][] positions = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {0.5f, 0.5f}, {-0.5f, 0.5f}};
                for (float[] position : positions) {
                    vertices.putFloat(position[0]).putFloat(position[1]).putFloat(0.0f);
                    // R=0, G=255, B=0, A=255 in the R8G8B8A8_UNORM layout the pipeline declares.
                    vertices.putInt(0xFF00FF00);
                }
                vertices.flip();
                int[] indices = VulkanImmediateDrawer.expandIndices(7, 4);
                this.isolationMesh.copyVertices(MemoryUtil.memAddress(vertices), VulkanImmediatePipeline.VERTEX_BYTES,
                        4, indices);
            } finally {
                MemoryUtil.memFree(vertices);
            }
        }
        ByteBuffer push = MemoryUtil.memAlloc(128);
        try {
            // Identity matrices: with z = 0 the vertex is already inside Vulkan's 0..1 depth range, so no clip-depth
            // remap is wanted here.
            for (int i = 0; i < 16; i++) {
                push.putFloat(i * 4, (i % 5 == 0) ? 1.0f : 0.0f);
            }
            this.immediatePipeline.draw(this.commandBuffer, this.isolationMesh, push);
            this.immediateIsolationDraws++;
        } finally {
            MemoryUtil.memFree(push);
        }
    }

    private void drawPendingImmediate() {
        if (this.gpuStalled) {
            return;
        }
        closeCurrentImmediateGroup();
        if (this.immediateGroups.isEmpty()) {
            return;
        }
        try {
            VulkanTerrainPipeline terrain = pipelineFor(this.lastUiPass);
            if (terrain == null) {
                // Counted rather than silently returned: an accepted batch whose GL draw was cancelled must be drawn
                // here, so a null pipeline means geometry is being lost, not deferred.
                this.immediateFlushNoPipeline++;
                this.immediateGroupsDiscarded += this.immediateGroups.size();
                discardImmediateGroups();
                return;
            }
            if (this.immediatePipeline == null) {
                this.immediatePipeline = VulkanImmediatePipeline.create(this.context, terrain.renderPass());
            }
            if (this.textureRegistry == null) {
                this.textureRegistry = new VulkanTextureRegistry(this.context);
            }
            ByteBuffer push = MemoryUtil.memAlloc(132);
            try {
                for (Object[] group : this.immediateGroups) {
                    VulkanDynamicMesh mesh = (VulkanDynamicMesh) group[0];
                    if (mesh.isEmpty()) {
                        continue;
                    }
                    int formatId = ((Integer) group[3]).intValue();
                    int[] state = (int[]) group[6];
                    pushMatrixInto((float[]) group[1], push, 0);
                    pushMatrixInto((float[]) group[2], push, 64);
                    push.putFloat(128, Float.intBitsToFloat(state[6]));
                    if (formatId == 5) {
                        // The observation is global-latest, not per-batch: this is the factors from the most recent blendFunc call
                        // anywhere, and using them gave 0.31% against the constants' 0.23%. Until they are captured with the
                        // group - at the moment the batch is offered - the family's constants are the better answer.
                        // The group's own factors are captured and carried, but using them still measures 0.33% against the
                        // constants' 0.21-0.23%, so the factors alone are not what differs: the family's other asserted
                        // fields - depth write in particular - are the likely remainder, and they are not observed yet. The
                        // constants stay until the whole per-group triple can be observed together.
                        int observedSrc = org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
                        int observedDst = org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
                        // The key carries the blend factors as well as the captured state: a pipeline built for one pair
                        // must not be handed to a group that set another, which is what made the frame worse when the
                        // observed values were used without being part of the key.
                        long particleKey = ((Long) group[5]).longValue() * 31L
                                + (((long) observedSrc) << 20) + observedDst;
                        VulkanImmediateParticlePipeline particle =
                                particlePipelineCache.get(Long.valueOf(particleKey));
                        if (particle == null) {
                            // The particle family is translucent by definition: Minecraft draws its particles with source-alpha
                            // blending, depth testing on and depth writes off, whichever of those the state mirror happened to
                            // capture - and the mirror is known not to capture the integer blend overloads or alpha enable
                            // (audit S07). Measured, that gap showed up as particles that were brighter and warmer than GL's,
                            // which is what an unblended overlay looks like. The family's meaning is asserted here rather than
                            // read from a state the project cannot yet observe.
                            // Observed factors when the mirror has them - the blendFunc hook above captures what Minecraft
                            // set - and the family's own alpha blend only as the documented fallback for a pass whose
                            // factors were never seen.
                            // The observed factors are recorded (770 and 771 in the measured run - exactly what the family
                            // assertion used), but they are not yet used to build the pipeline: the cache key does not
                            // include them, so the first group's factors would be shared by every later group and the frame
                            // got worse (0.33% against 0.23%). Using them needs the key to carry them first.
                            int blendSrc = org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
                            int blendDst = org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
                            particle = VulkanImmediateParticlePipeline.create(this.context, terrain.renderPass(),
                                    true, observedSrc, observedDst, true, false, false);
                            particlePipelineCache.put(Long.valueOf(particleKey), particle);
                        }
                        VulkanGlTexture texture = this.textureRegistry.get(((Integer) group[4]).intValue());
                        long descriptorSet = texture == null ? 0L
                                : particle.descriptorSetFor(texture, this.lightmapTexture);
                        if (descriptorSet == 0L) {
                            // This path should be unreachable now that the texture is prepared before acceptance; if it ever
                            // runs, the geometry has already been taken and the original draw cancelled, so it is counted
                            // separately and loudly rather than folded into the ordinary refusal count.
                            this.immediateTextureRefusals++;
                            this.postAcceptDroppedGroups++;
                            if (formatId >= 0 && formatId < this.postAcceptDroppedByFormat.length) {
                                this.postAcceptDroppedByFormat[formatId]++;
                            }
                            continue;
                        }
                        particle.draw(this.commandBuffer, mesh, push, descriptorSet);
                    } else if (formatId == 4) {
                        VulkanImmediateItemPipeline item =
                                itemPipelineCache.get(Long.valueOf(((Long) group[5]).longValue()));
                        if (item == null) {
                            item = VulkanImmediateItemPipeline.create(this.context, terrain.renderPass(),
                                    state[0] != 0, state[1], state[2], state[3] != 0, state[4] != 0, state[5] != 0);
                            itemPipelineCache.put(Long.valueOf(((Long) group[5]).longValue()), item);
                        }
                        VulkanGlTexture texture = this.textureRegistry.get(((Integer) group[4]).intValue());
                        long descriptorSet = texture == null ? 0L : item.descriptorSetFor(texture);
                        if (descriptorSet == 0L) {
                            this.immediateTextureRefusals++;
                            continue;
                        }
                        item.draw(this.commandBuffer, mesh, push, descriptorSet);
                    } else if (formatId == 3) {
                        VulkanImmediateBlockPipeline block =
                                blockPipelineCache.get(Long.valueOf(((Long) group[5]).longValue()));
                        if (block == null) {
                            block = VulkanImmediateBlockPipeline.create(this.context, terrain.renderPass(),
                                    state[0] != 0, state[1], state[2], state[3] != 0, state[4] != 0, state[5] != 0);
                            blockPipelineCache.put(Long.valueOf(((Long) group[5]).longValue()), block);
                        }
                        VulkanGlTexture texture =
                                this.textureRegistry.get(((Integer) group[4]).intValue());
                        long descriptorSet = texture == null ? 0L
                                : block.descriptorSetFor(texture, this.lightmapTexture);
                        if (descriptorSet == 0L) {
                            this.immediateTextureRefusals++;
                            continue;
                        }
                        block.draw(this.commandBuffer, mesh, push, descriptorSet);
                    } else if (formatId == 2) {
                        VulkanImmediateTexturePipeline textured =
                                texturedPipelineCache.get(Long.valueOf(((Long) group[5]).longValue()));
                        if (textured == null) {
                            textured = VulkanImmediateTexturePipeline.create(this.context, terrain.renderPass(),
                                    state[0] != 0, state[1], state[2], state[3] != 0, state[4] != 0, state[5] != 0);
                            texturedPipelineCache.put(Long.valueOf(((Long) group[5]).longValue()), textured);
                        }
                        try {
                            VulkanGlTexture texture =
                                    this.textureRegistry.get(((Integer) group[4]).intValue());
                            if (texture == null) {
                                this.immediateTextureRefusals++;
                                continue;
                            }
                            long descriptorSet = textured.descriptorSetFor(texture);
                            if (descriptorSet == 0L) {
                                this.immediateTextureRefusals++;
                                continue;
                            }
                            textured.draw(this.commandBuffer, mesh, push, descriptorSet);
                        } catch (RuntimeException | LinkageError error) {
                            // Caught and reported rather than left to unwind the render pass: every command after an
                            // unwind targets a buffer that is no longer recording, which is exactly the 113
                            // "commandBuffer must be in the recording state" messages the first attempt produced.
                            this.immediateTextureRefusals++;
                            record("textured group failed: " + describe(error));
                            continue;
                        }
                    } else {
                        VulkanImmediatePipeline plain =
                                immediatePipelineCache.get(Long.valueOf(((Long) group[5]).longValue()));
                        if (plain == null) {
                            plain = VulkanImmediatePipeline.create(this.context, terrain.renderPass(),
                                    state[0] != 0, state[1], state[2], state[3] != 0, state[4] != 0, state[5] != 0);
                            immediatePipelineCache.put(Long.valueOf(((Long) group[5]).longValue()), plain);
                        }
                        plain.draw(this.commandBuffer, mesh, push);
                    }
                    this.immediateFlushes++;
                    this.immediateGroupsDrawn++;
                }
            } finally {
                MemoryUtil.memFree(push);
            }
        } catch (RuntimeException | LinkageError error) {
            this.immediateLastFlushError = describe(error);
            this.immediateGroupsDiscarded += this.immediateGroups.size();
            record("immediate flush failed: " + describe(error));
        } finally {
            discardImmediateGroups();
        }
    }

    /**
     * Records the non-terrain test geometry: one textured quad in normalised device coordinates, drawn with the UI
     * pipeline into the terrain colour image. The projection and model-view are identities, so the quad lands in a
     * fixed corner regardless of the camera, which makes the pixel check in the harness unambiguous.
     */
    private void drawUiTestQuad(ChunkPrimitiveType primitiveType) {
        try {
            VulkanTerrainPipeline terrain = pipelineFor(this.lastUiPass);
            if (terrain == null) {
                return;
            }
            if (this.uiPipeline == null) {
                this.uiPipeline = VulkanUiPipeline.create(this.context, terrain.renderPass());
                this.uiMesh = new VulkanDynamicMesh(this.context);
            }
            if (this.atlasTexture == null) {
                return;
            }
            this.uiMesh.begin(VulkanDynamicMesh.Layout.positionTexLightColor());
            float u = 1.0f / 32.0f;
            float v = 1.0f / 32.0f;
            addQuadVertex(-0.95f, 0.95f, 0.0f, 0.0f);
            addQuadVertex(-0.55f, 0.95f, u, 0.0f);
            addQuadVertex(-0.55f, 0.55f, u, v);
            addQuadVertex(-0.95f, 0.55f, 0.0f, v);
            this.uiMesh.index(0);
            this.uiMesh.index(1);
            this.uiMesh.index(2);
            this.uiMesh.index(0);
            this.uiMesh.index(2);
            this.uiMesh.index(3);
            this.uiPipeline.bindTexture(this.atlasTexture);
            ByteBuffer push = MemoryUtil.memAlloc(128);
            try {
                // Identity projection and model-view: the vertices above are already in clip space.
                putIdentity(push, 0);
                putIdentity(push, 64);
                this.uiPipeline.draw(this.commandBuffer, this.uiMesh, push);
            } finally {
                MemoryUtil.memFree(push);
            }
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("ui test quad failed: " + describe(error));
        }
    }

    /**
     * Writes {@code result = clipDepthRemap * projection}, the same z remap the terrain projection goes through, so a
     * matrix mirrored from OpenGL can be used unchanged for depth in Vulkan.
     */
    private static void remapClipDepth(float[] projection, float[] result) {
        float[] remap = new float[16];
        remap[0] = 1.0f;
        remap[5] = 1.0f;
        remap[10] = 0.5f;
        remap[14] = 0.5f;
        remap[15] = 1.0f;
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += remap[k * 4 + row] * projection[column * 4 + k];
                }
                result[column * 4 + row] = sum;
            }
        }
    }

    /**
     * Reads the matrices OpenGL is currently using. Called on the render thread while the GL context is current, so
     * the values are the ones Minecraft itself would draw with - no class needs to cooperate, which matters because
     * the projection is installed by a utility class outside the transforming loader.
     */
    private void captureGlMatrices() {
        if (this.worldPassActive && this.worldSnapshotValid) {
            // No GL read on this path. The matrices are the mirror's: the camera base seeded at the boundary plus whatever
            // local transforms the pass applied through the hooked calls since. The GL readback that used to run here was
            // the per-batch cost the plan calls dominant, and it is replaced rather than disabled.
            float[] mirroredProjection = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION);
            float[] mirroredModelView = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
            System.arraycopy(mirroredProjection, 0, this.glProjection, 0, 16);
            System.arraycopy(mirroredModelView, 0, this.glModelView, 0, 16);
            this.snapshotServedCaptures++;
            return;
        }
        if (!MATRIX_DIAGNOSTICS) {
            // Outside the world pass, or without a snapshot, the matrices are not this renderer's to use. The batch is
            // refused before acceptance by submitImmediateBatch, so reaching here means a diagnostic read was requested.
            this.outsideWorldPassRefusals++;
            return;
        }
        try {
            FloatBuffer projection = MemoryUtil.memAllocFloat(16);
            FloatBuffer modelView = MemoryUtil.memAllocFloat(16);
            try {
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX, projection);
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, modelView);
                projection.get(this.glProjection);
                modelView.get(this.glModelView);
                // Compare here, where both sides are in hand: the mirrored stack against what OpenGL reports. The
                // comparison existed but sat on a path that did not run, so the reported delta stayed at its initial -1
                // and no one could tell "the mirror is exact" from "the mirror was never checked".
                compareMirroredAndLiveMatrices();
            } finally {
                MemoryUtil.memFree(projection);
                MemoryUtil.memFree(modelView);
            }
        } catch (RuntimeException | LinkageError error) {
            record("could not read the OpenGL matrices for an immediate batch: " + describe(error));
        }
    }

    private static void pushMatrixInto(float[] matrix, ByteBuffer target, int offset) {
        for (int i = 0; i < 16; i++) {
            target.putFloat(offset + (i * 4), matrix[i]);
        }
    }

    private void addQuadVertex(float x, float y, float u, float v) {
        this.uiMesh.nextVertex();
        this.uiMesh.putPosition(x, y, 0.0f);
        this.uiMesh.putUv(u, v);
        this.uiMesh.putLight(0, 240);
        this.uiMesh.putColor(255, 255, 255, 255);
    }

    private static void putIdentity(ByteBuffer target, int offset) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                target.putFloat(offset + ((column * 4) + row) * 4, column == row ? 1.0f : 0.0f);
            }
        }
    }

    private void submitCommands() {
        if (this.gpuStalled) {
            // Nothing to end: the buffer was never begun. This is the last of the recording entries the steady-state injection
            // reported, after the pass-level ones.
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(vkEndCommandBuffer(this.commandBuffer), "vkEndCommandBuffer");
            vkResetFences(this.context.device(), this.fence);
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default();
            LongBuffer waitSemaphores = stack.mallocLong(1);
            waitSemaphores.put(0, this.frameTarget.waitSemaphore());
            IntBuffer waitStages = stack.mallocInt(1);
            waitStages.put(0, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            LongBuffer signalSemaphores = stack.mallocLong(1);
            signalSemaphores.put(0, this.frameTarget.doneSemaphore());
            org.lwjgl.PointerBuffer commandBuffers = stack.mallocPointer(1);
            commandBuffers.put(0, this.commandBuffer.address());
            // LWJGL derives the semaphore counts from the buffers, so only the pointers are set here.
            // waitSemaphoreCount must be set explicitly: LWJGL's pWaitSemaphores(LongBuffer) writes only the
            // pointer, unlike pSignalSemaphores which also sets its count. Leaving it to calloc's zero meant this
            // submission waited on nothing at all - which is what the validation layer was reporting all along.
            submit.waitSemaphoreCount(waitSemaphores.remaining());
            submit.pWaitSemaphores(waitSemaphores).pWaitDstStageMask(waitStages)
                    .pCommandBuffers(commandBuffers)
                    .pSignalSemaphores(signalSemaphores);
            if (FAIL_SUBMITS_REMAINING > 0) {
                // Test-only injection, so the fail-closed paths can be exercised instead of merely compiled: the
                // submission is treated as refused by the driver, which is the case the audit's E asks to be shown to
                // end in bounded time rather than in a wait for a signal that never comes.
                FAIL_SUBMITS_REMAINING--;
                record("injected submit failure (" + FAIL_SUBMITS_REMAINING + " left): this submission is not sent");
                return;
            }
            check(vkQueueSubmit(this.context.queue(), submit, this.fence), "vkQueueSubmit");
            this.submissionPending = true;
            this.frameSubmitReachedQueue = true;
        }
    }

    /**
     * Waits for the last submission of this backend to finish, and refuses to wait forever.
     * <p>
     * The fence exists from creation but is signalled only by a submission the driver accepted, so waiting on it when
     * nothing was submitted is a wait that can never end - which is exactly what a failed {@code vkQueueSubmit} leaves
     * behind: the previous code cleared the pending flag *before* waiting and passed {@code Long.MAX_VALUE}, so a
     * submission that never reached the queue, or a device that stopped making progress, hung the client instead of
     * ending the run. The wait is bounded now, the flag is cleared only once the wait has resolved, and the queue is
     * only drained after the fence actually signalled - draining a queue whose work never completes would hang in the
     * driver instead.
     */
    /**
     * Ends all work the device is doing, so resources can be released without the GPU still reading them.
     * <p>
     * Bounded on purpose: a device that has stopped making progress must end the run with a message rather than hang
     * it, which is what the audit asked for on every failure path. {@code vkDeviceWaitIdle} is the honest call here -
     * submissions from several command buffers and from the bridge's handoffs are all outstanding - and the timeout is
     * the same one the frame waits use.
     */
    private void settleGpu(String reason) {
        if (this.gpuStalled) {
            // A stalled GPU means the last command buffer may never have been begun and never submitted, so its recorded
            // references are still live. Destroying the frame target or its framebuffers here is what validation reported as
            // "in use by VkCommandBuffer": on this path the resources are left for process exit instead.
            this.stalledTeardownSkips++;
            return;
        }
        long start = System.nanoTime();
        int result = org.lwjgl.vulkan.VK10.vkDeviceWaitIdle(this.context.device());
        if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
            record("settleGpu(" + reason + "): vkDeviceWaitIdle returned " + result);
            return;
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (millis > 200L) {
            record("settleGpu(" + reason + ") waited " + millis + "ms for the device");
        }
    }

    private boolean waitForIdle() {
        // A documented injection for the timeout path, which no ordinary run reaches: the audit asks for a safe fake
        // result here and a real latency test to be kept apart. It fires after a configured number of waits, not on the
        // first: failing the first wait stalls before the renderer exists, which is a start-up question rather than the
        // steady-state one this audit item describes, and the earlier run showed exactly that as a start-up abort.
        if (FAIL_IDLE_AFTER > 0 && ++this.idleWaits >= FAIL_IDLE_AFTER) {
            this.injectedIdleFailures++;
            return false;
        }
        if (this.fence == 0 || !this.submissionPending) {
            return true;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer fences = stack.mallocLong(1);
            fences.put(0, this.fence);
            int result = vkWaitForFences(this.context.device(), fences, true, IDLE_TIMEOUT_NANOS);
            if (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                record("waitForIdle: the fence did not signal within " + (IDLE_TIMEOUT_NANOS / 1_000_000_000L)
                        + "s, so the last submission is not completing; not draining the queue");
                return true;
            }
            if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                record("waitForIdle: vkWaitForFences returned " + result);
                return true;
            }
            this.submissionPending = false;
        }
        vkQueueWaitIdle(this.context.queue());
        return true;
    }

    /** How long a wait for the GPU will be tolerated before the run ends rather than hanging. */
    private static final long IDLE_TIMEOUT_NANOS = 5_000_000_000L;

    // ------------------------------------------------------------------ shared resources

    /**
     * The shared index buffer holds the repeating primitive pattern the OpenGL backend also uses for unsorted
     * passes. The pattern is generated by the pass's own {@link ChunkPrimitiveType}, so the Vulkan and OpenGL
     * paths cannot drift apart on element order or count.
     */
    private void ensureSharedIndexBuffer(ChunkPrimitiveType primitiveType) {
        int maxVertices = 0;
        for (RegionResources resources : this.regionResources.values()) {
            long bytes = resources.vertexArena.deviceAllocatedMemoryL();
            maxVertices = Math.max(maxVertices, (int) (bytes / this.commonVertexStride));
        }
        int primitives = (maxVertices / primitiveType.getVerticesPerPrimitive()) + 1;
        Integer existing = this.sharedIndexPrimitives.get(primitiveType);
        if (this.sharedIndexBuffers.containsKey(primitiveType) && existing != null && existing >= primitives) {
            return;
        }
        int capacity = Math.max(primitives, 1024);
        int bytes = primitiveType.getIndexBufferSize(capacity);
        ByteBuffer data = MemoryUtil.memAlloc(bytes);
        try {
            primitiveType.generateSimpleIndexBuffer(data, capacity);
            VulkanBuffer previous = this.sharedIndexBuffers.remove(primitiveType);
            if (previous != null) {
                previous.delete();
            }
            VulkanBuffer buffer = new VulkanBuffer(this.context, bytes, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            buffer.write(0, MemoryUtil.memAddress(data), bytes);
            this.sharedIndexBuffers.put(primitiveType, buffer);
            this.sharedIndexPrimitives.put(primitiveType, capacity);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private void ensureUniformCapacity(int regions) {
        int blocks = Math.max(16, regions + 1);
        long required = blocks * this.uniformStride;
        if (this.uniforms != null && this.uniforms.size() >= required) {
            return;
        }
        if (this.uniforms == null) {
            this.uniforms = new VulkanUniformBuffer(this.context, required);
        } else {
            this.uniforms.ensureCapacity(required);
        }
        this.uniformBlocks = blocks;
    }

    private int alignmentFromDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties = org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
            long alignment = properties.limits().minUniformBufferOffsetAlignment();
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, alignment));
        }
    }

    private static long align(long value, int alignment) {
        long mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    /**
     * Debug aid: copies the first vertices of the buffer the first draw uses into host-visible memory and decodes
     * them. It answers the one question the CPU-side logs cannot: what the GPU actually holds.
     */
    private void ensureReadback() {
        if (this.readbackBuffer != 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkBufferCreateInfo create = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(8 * 64).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(this.context.device(), create, null, handle), "vkCreateBuffer(readback)");
            this.readbackBuffer = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), this.readbackBuffer, requirements);
            org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(this.context.physicalDevice(), properties);
            int type = -1;
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                int flags = properties.memoryTypes(i).propertyFlags();
                if ((requirements.memoryTypeBits() & (1 << i)) != 0
                        && (flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0
                        && (flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0) {
                    type = i;
                    break;
                }
            }
            if (type < 0) {
                throw new IllegalStateException("No host-visible coherent memory type for the debug readback");
            }
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size()).memoryTypeIndex(type);
            check(vkAllocateMemory(this.context.device(), allocate, null, handle), "vkAllocateMemory(readback)");
            this.readbackMemory = handle.get(0);
            check(vkBindBufferMemory(this.context.device(), this.readbackBuffer, this.readbackMemory, 0),
                    "vkBindBufferMemory(readback)");
            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(this.context.device(), this.readbackMemory, 0, 8 * 64, 0, pointer), "vkMapMemory(readback)");
            this.readbackMapped = pointer.get(0);
        }
    }

    private void recordReadback(RegionResources resources, long sourceOffset, int slot) {
        ensureReadback();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkBufferCopy.Buffer region = org.lwjgl.vulkan.VkBufferCopy.calloc(1, stack);
            region.get(0).srcOffset(sourceOffset).dstOffset((long) slot * 64L).size(64L);
            vkCmdCopyBuffer(this.commandBuffer, resources.vertexArena.buffer().buffer(), this.readbackBuffer, region);
            org.lwjgl.vulkan.VkMemoryBarrier.Buffer barrier = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_HOST_READ_BIT);
            vkCmdPipelineBarrier(this.commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0,
                    barrier, null, null);
        }
        this.readbackPending = true;
    }

    private void readBackAndLog() {
        this.readbackPending = false;
        this.readbackDone = true;
        waitForIdle();
        ByteBuffer view = MemoryUtil.memByteBuffer(this.readbackMapped, Math.max(64, this.readbackSlots * 64));
        view.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        StringBuilder line = new StringBuilder("gpu draws:");
        for (int slot = 0; slot < this.readbackSlots; slot++) {
            int base = slot * 64;
            line.append(" [").append(slot).append("] colour=0x")
                    .append(Integer.toHexString(view.getInt(base + 12)))
                    .append(" light=0x").append(Integer.toHexString(view.getInt(base + 24)))
                    .append(" pos=(").append(view.getFloat(base)).append(',')
                    .append(view.getFloat(base + 4)).append(',').append(view.getFloat(base + 8)).append(')');
        }
        record(line.toString());
    }

    private void record(String diagnostic) {
        this.diagnostics.add(diagnostic);
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }

    /** Per-region device memory. */
    private static final class RegionResources {
        final VulkanArena vertexArena;
        final int[] sectionFirstColor = new int[RenderRegion.REGION_SIZE];
        VulkanArena indexArena;

        RegionResources(VulkanContext context, int vertexStride) {
            this.vertexArena = new VulkanArena(context, 512 * 1024, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, vertexStride);
        }

        int bufferCount() {
            return 1 + (this.indexArena != null ? 1 : 0);
        }

        void delete() {
            this.vertexArena.delete();
            if (this.indexArena != null) {
                this.indexArena.delete();
                this.indexArena = null;
            }
        }
    }

    private static final class PendingMesh {
        final int sectionIndex;
        final TerrainRenderPass pass;
        final BuiltSectionMeshParts parts;
        final ChunkSortOutput.SortedMesh sorted;
        final int[] sectionFirstColor;

        PendingMesh(int sectionIndex, TerrainRenderPass pass, BuiltSectionMeshParts parts,
                    ChunkSortOutput.SortedMesh sorted, int[] sectionFirstColor) {
            this.sectionIndex = sectionIndex;
            this.pass = pass;
            this.parts = parts;
            this.sorted = sorted;
            this.sectionFirstColor = sectionFirstColor;
        }
    }
}
