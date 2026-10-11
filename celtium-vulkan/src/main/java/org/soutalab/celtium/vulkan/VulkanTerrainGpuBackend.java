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
    // Not final: the window-time instance adopts the world renderer's objects when the world arrives, and hands them
    // back when it leaves, so the same device and swapchain serve both (task-15).
    private RenderPassConfiguration<?> configuration;
    private RenderRegionManager regionManager;
    private FogService fogService;
    private int commonVertexStride;
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

    private FrameImageTarget frameTarget;
    /** Standalone frame-compatible render pass for UI-only frames - only used when no terrain pass has run. */
    private long uiRenderPass;

    private long uiRenderPass() {
        if (this.uiRenderPass == 0L) {
            this.uiRenderPass = VulkanTerrainPipeline.createFrameRenderPass(this.context);
            record("standalone UI render pass created for the no-terrain-pass frame");
        }
        return this.uiRenderPass;
    }
    private int targetWidth;
    private int targetHeight;
    private long framebuffer;
    private long framebufferRenderPass;
    /**
     * The framebuffer over the owned off-target images, and the render pass it was built for.
     * <p>
     * One per pair, cached for the same reason: compatibility is defined by the render pass the pipeline built, and
     * one framebuffer serves every pass that is compatible with it.
     */
    private long offTargetFramebuffer;
    private long offTargetFramebufferRenderPass;
    private int offTargetFramebufferCreations;
    /**
     * The framebuffer over the chain pair - the images draws for the game's own framebuffers render into, the
     * entity-outline silhouette being the measured one. It is a second framebuffer rather than a second colour view
     * on one, because the render pass declares its attachments and the two pairs are different images.
     */
    private long chainPairFramebuffer;
    private long chainPairFramebufferRenderPass;
    private int chainPairFramebufferCreations;
    /**
     * The published wrapper for the chain's adopted output: the renderer's off-target colour image under the GL name
     * the game's composite binds, plus the name and the image handle it was published for.
     * <p>
     * The pair is published from the chain's adoption, the one place that knows both the identity -&gt; GL name
     * association (reported by the game's own framebuffer bind) and the image the chain actually runs on. Publishing
     * is idempotent for the same handles, so a per-frame call returns the same wrapper and the descriptor keys the
     * immediate path cached stay valid. The image handle is kept so a pair recreated at a new size - whose wrapper
     * would name a destroyed image - is unpublished instead of transitioned.
     */
    private VulkanGlTexture publishedChainOutput;
    private int publishedChainTexture;
    private long publishedChainImage;
    /**
     * Every game colour texture name published over the chain pair's image, and the image it names.
     * <p>
     * The outline's own name has its own field above; this map is for the names published by the off-target pass -
     * the vanilla loading screen's framebuffer is the measured one - so that a pair recreated at a new size can be
     * unpublished from every name it stood for instead of only from the outline's.
     */
    private final java.util.Map<Integer, VulkanGlTexture> publishedFrameTextures =
            new java.util.HashMap<Integer, VulkanGlTexture>();
    /** The image each published frame texture name stands for, so a recreated pair can be told from the same one. */
    private final java.util.Map<Integer, Long> publishedFrameImages = new java.util.HashMap<Integer, Long>();
    private int publishedFrameTexturesCreated;
    private int publishedFrameTexturesUnpublished;
    /**
     * The colour image the render pass being begun attaches, recorded when the framebuffer is chosen.
     * <p>
     * A published name that stands for this image must NOT be moved to the sampling layout before the pass begins. The
     * pass declares {@code GENERAL} for attachment 0, so the transition to {@code SHADER_READ_ONLY_OPTIMAL} recorded
     * immediately before {@code vkCmdBeginRenderPass} left the image in a layout the pass does not declare - which is
     * exactly {@code VUID-vkCmdBeginRenderPass-initialLayout-00900}, measured as 20 messages per run and about one per
     * frame after the first seconds, on a scene with no post chain. The published names and the attachment are the same
     * image ({@code celtium$syncPublishedChainOutput} keeps only the names that stand for the attached pair image), so
     * the sampling-side transition at that site was moving the image the pass was about to attach.
     */
    private long passAttachedColorImage;
    /** How many published names the attach-side rule kept out of the sampling transition. */
    private int publishedFrameTexturesSkippedForAttach;
    /** How many published names were dropped because their image was no longer the pair's live image. */
    private int publishedFrameTexturesStalePruned;
    /** How many published names were re-pointed at the pair's new image instead of being dropped. */
    private int publishedFrameTexturesRepublished;
    /** Groups whose sampled texture wrapper had been replaced between prepare and draw, and were re-prepared. */
    private int staleGroupDescriptorsRebuilt;
    /** Groups refused because their texture wrapper had moved and no rebuild was available for their pipeline. */
    private int staleGroupRefusals;
    /**
     * The frame in which each pair's images have been cleared, so the clear happens once per frame and not per
     * window. One per pair: two pairs are two images, and a frame that cleared one of them says nothing about the
     * other - which is exactly the mistake that would make the second window of a frame erase the first's work.
     */
    private long defaultPairClearedFrame = -1L;
    private long chainPairClearedFrame = -1L;
    /** Clears recorded per pair; the attribution counters are separate and name the pair per identity. */
    private int defaultPairClears;
    private int chainPairClears;
    /**
     * glClear attribution, counted where the request is read: {@code gameClearsLatchedForFrame} were issued while the
     * game's own main framebuffer was bound (they clear the delivered image), {@code gameClearsRoutedToPairs} while
     * one of its other framebuffers was (their pair carries them, exactly as it did before this latch existed, and the
     * frame image must not see them). Both are reported by {@code takeTimingSummary}, and the first few are printed as
     * {@code CELTIUM_CLEAR_LATCH} lines with the values they carried.
     */
    private int gameClearsLatchedForFrame;
    private int gameClearsRoutedToPairs;
    private int gameClearLatchLog;
    private static final int GAME_CLEAR_LATCH_LINES =
            Integer.getInteger("celtium.clearlatch.lines", 6).intValue();
    private int offTargetPassFailures;
    private String lastOffTargetFailure = "none";

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
    /** One slot per remembered draw; 32 covers a frame's visible sections at a typical render distance. */
    private static final int READBACK_SLOTS = 32;
    private final long[] readbackOffsets = new long[READBACK_SLOTS];
    /** The region the remembered reads come from; a frame's first draws all belong to the same one in practice. */
    private RegionResources readbackFrom;
    /** The region each remembered draw belongs to; the offsets alone cannot say which arena to read. */
    /** The diagnostic copy's size: one whole block per remembered draw, and the range the guard must cover. */
    private static final int READBACK_COPY_BYTES = 64;
    /** Whether the copy for each slot was actually issued; a refused slot's block is stale, not sampled data. */
    private final boolean[] readbackValid = new boolean[READBACK_SLOTS];
    private final RegionResources[] readbackRegions = new RegionResources[READBACK_SLOTS];
    private int readbackRefusals;
    private int readbackRegionsUsed;

    private long readbackMemory;
    private long readbackMapped;
    private boolean submissionPending;
    private VulkanCommandBufferState commandState;
    private long activeImmediateTarget;
    private final java.util.List<ImmediateGroup> recordedImmediateGroups = new java.util.ArrayList<ImmediateGroup>();
    /**
     * When and against which frame-target generation a queued group was built.
     * <p>
     * The five stages - queued, recorded, submitted, completed, retired - live in separate lists and counters, but a group
     * carried no record of the frame or the target it belonged to, so a group that outlived a frame-target recreation could
     * still be drawn with a pipeline and render pass built for a target that no longer exists. The stamp is that record, and
     * drawPendingImmediate refuses to draw a group whose target generation has been superseded.
     * <p>
     * The texture generation is deliberately not part of it: a group's vertices do not become invalid when a texture is
     * re-uploaded, and dropping the draw would lose geometry. Only the target identity decides.
     */
    // GroupStamp, PreparedImmediate and the typed group they ride in live in ImmediateGroup.java - the named form of
    // the Object[] every group used to be.

    /** Bumped whenever the frame target is destroyed, so groups of the old target can be told apart. */
    private int targetGeneration;
    private int groupsStaleTargetGeneration;
    /**
     * Bumped when a world's resources are released, so a queued group from the previous world can never be drawn.
     * <p>
     * The window-owned backend outlives a world: on a quit to the title screen the world renderer detaches and
     * {@link #celtium$releaseWorldResources()} frees the model meshes that captured model groups borrow their meshes
     * from. A group still in the queue at that moment would otherwise be drawn against a released buffer - the audit's
     * B10 gap. Groups carry the epoch they were accepted in; the draw side retires a stale one instead of dereferencing
     * it, and the teardown drops the queue rather than leaving it for the next world.
     */
    private int worldEpoch;
    private int groupsStaleWorldEpoch;
    private int groupsDiscardedAtWorldTeardown;
    /** Groups retired by the one drain that has no later one: the backend's own last teardown. */
    private int groupsDiscardedAtFullTeardown;
    private long immediateRecordedBatches, immediateSubmittedBatches, immediateCompletedBatches;
    private long offTargetRecordedBatches, offTargetCompletedBatches;
    private long invalidImmediateRecords;
    private long immediateRetryCount;
    private int failImmediateSubmits;

    /** Restricted to the small validation scene, so retry is exercised after a real batch was accepted. */
    public void injectImmediateSubmitFailure(int count) {
        if (!Boolean.getBoolean("celtium.validation.commandBufferTargets") || count < 0 || count > 1)
            throw new IllegalArgumentException("submit failure injection requires the target acceptance fixture");
        this.failImmediateSubmits = count;
    }
    private int currentGroupBatches;
    private int[] currentGroupViewportValues;
    /**
     * Groups closed because the game's clip rectangle moved between two otherwise identical batches.
     * <p>
     * The capture and the flush half of this rule are already in the tree ({@code currentGroupScissor} /
     * {@code currentGroupScissorEnabled}, captured where the group opens and read by {@code celtium$setGroupViewport});
     * what was missing is the <em>boundary</em>: two batches offered under different {@code glScissor} rectangles still
     * joined one group, so the whole group was clipped by the first rectangle. This counter is the evidence that the
     * boundary fires, and it can only be non-zero on a scene that moves the clip inside a group.
     */
    private long scissorBoundarySplits;
    /** Groups whose {@code vkCmdSetScissor} was the game's captured clip intersected with the target rectangle. */
    private long scissorCapturedGroups;
    private PreparedImmediate currentPreparedImmediate;

    /** [accept, record, submit, fence-complete, pending, invalid, off-accept, off-record, off-complete]. */
    public long[] immediateExecutionCounters() {
        long pending = this.currentGroupUsed ? this.currentGroupBatches : 0;
        for (ImmediateGroup group : this.immediateGroups) pending += group.batches;
        return new long[] {this.immediateTaken, this.immediateRecordedBatches, this.immediateSubmittedBatches,
                this.immediateCompletedBatches, pending, this.invalidImmediateRecords, this.acceptedForOtherTargets,
                this.offTargetRecordedBatches, this.offTargetCompletedBatches, this.immediateRetryCount};
    }

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
        // Null means the window-time instance, which is created before any world renderer exists (task-15). Nothing in
        // this constructor needs the render pass configuration: the pipelines that do are created per terrain pass, on
        // first use, from this.configuration, which the world renderer hands over through celtium$adoptTerrainState when
        // it arrives. The stride is the one value computed here, and until a world exists there is no terrain vertex
        // format to take it from.
        this.commonVertexStride = configuration == null ? 1
                : configuration.getAllKnownRenderPasses()
                        .mapToInt(pass -> pass.vertexType().getVertexFormat().getStride())
                        .reduce(1, MathUtil::lcm);
        this.uniformAlignment = Math.max(MIN_UNIFORM_ALIGNMENT,
                Integer.highestOneBit(Math.max(MIN_UNIFORM_ALIGNMENT, this.alignmentFromDevice())));
        this.uniformStride = align(UNIFORM_BYTES, this.uniformAlignment);
        createCommandResources();
    }

    /**
     * Creates the instance that exists from window creation, before any world renderer does.
     * <p>
     * This is the menu, the loading screens and every GUI-only frame: under the GL-less window the game draws them from
     * client-memory vertex arrays, which the stub decodes and hands to {@code TerrainGpuBackends} - a holder that used
     * to stay empty until a world was loaded, so those draws went to nobody and nothing presented. Measured on the
     * user's own launch: 524,288 client-memory draws decoded and every one of them refused with "no terrain backend is
     * installed". The same instance is later adopted by the world renderer
     * ({@link #celtium$adoptTerrainState}), so one renderer serves the whole process instead of one per configuration.
     */
    public static VulkanTerrainGpuBackend createForWindow(VulkanContext context) {
        VulkanTerrainGpuBackend backend = new VulkanTerrainGpuBackend(context, null, null, null);
        backend.windowBackend = true;
        backend.windowOwned = true;
        return backend;
    }

    /** True while this instance exists only for the window and no world renderer has adopted it yet. */
    private boolean windowBackend;
    /**
     * True for the instance the window created, for the life of the process.
     * <p>
     * It never clears, unlike {@code windowBackend}: the world renderer adopts this instance and the world's teardown
     * must not destroy it. {@code RenderSectionManager.destroy()} calls {@code delete()} on every world unload
     * (RenderSectionManager.java:923, reached from SimpleWorldRenderer.unloadWorld/unloadWorld), and if that released
     * the device, the frame target and the swapchain, quitting to the title screen would blank the menu again with no
     * second chance - the creation hook is latched. So a window-owned instance releases its terrain resources and
     * detaches, and keeps everything a menu frame needs.
     */
    private boolean windowOwned;

    /** {@return whether this instance was created at window creation and no world renderer has adopted it yet}. */
    public boolean celtium$isWindowBackend() {
        return this.windowBackend;
    }

    /**
     * {@return the name of the Vulkan device this renderer draws with}, or {@code null} when there is none.
     * <p>
     * The stub answers {@code GL_RENDERER} - which F3 shows and the fixture records as {@code glRenderer} - from this,
     * because under the GL-less window the GL renderer string is the stub's own name and tells a user nothing about
     * which GPU is rendering: the user's own report of "stub gl" in F3 is exactly that doubt. {@code GL_VENDOR} stays
     * "Celtium" so a GL-less leg is still identifiable in a report, and a configuration with no native device keeps
     * the stub string as well.
     */
    public String celtium$nativeDeviceName() {
        try {
            return this.context == null ? null : this.context.deviceName();
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    /**
     * Hands the world renderer's objects to the instance that already exists, instead of a second backend being created
     * beside it. The alternative - a menu renderer running alongside the world one - would give the swapchain and the
     * frame target two owners, which is the duplication this project has paid for before.
     * <p>
     * What arrives is exactly what the world-time constructor used to be given: the render pass configuration (whose
     * stride is recomputed), the region manager and the fog service. The device, the command resources, the frame
     * target and the swapchain are the ones already in use, so the first world frame finds them warm.
     */
    public void celtium$adoptTerrainState(RenderPassConfiguration<?> configuration,
                                          RenderRegionManager regionManager,
                                          FogService fogService) {
        this.configuration = configuration;
        this.regionManager = regionManager;
        this.fogService = fogService;
        if (configuration != null) {
            this.commonVertexStride = configuration.getAllKnownRenderPasses()
                    .mapToInt(pass -> pass.vertexType().getVertexFormat().getStride())
                    .reduce(1, MathUtil::lcm);
        }
        this.windowBackend = false;
        // The presented-frame counter means world frames: it names the numbered dump files (`frame-N.png`) and the
        // fixture asserts that N is the frame the capture shows, within one. The window-time instance presents the
        // menu and the loading screens from the first tick, so without this reset N would start before the world and
        // every dump would be offset by the pre-world presents - measured as 146 against 128 world frames in
        // fog-path's legs, which is exactly the `|presentedFrame - captureWorldFrames| <= 1` assertion failing for
        // every worker. The counter is a world-frame number, so it is restarted when the world begins.
        if (this.framePresentedFromVulkan != 0) {
            record("presented-frame counter reset at adoption: was " + this.framePresentedFromVulkan
                    + " pre-world presents, the numbering starts with the world");
            this.framePresentedFromVulkan = 0;
        }
        install();
        record("the world renderer adopted the window-time backend: configuration=" + (configuration != null)
                + " regions=" + (regionManager != null) + " fog=" + (fogService != null)
                + " terrainVertexStride=" + this.commonVertexStride);
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
        // Each call builds the current snapshot rather than appending to the last one. The audit (C13.5) measured that
        // the list grew by a fixed block on every call, so a repeatedly queried renderer (F3, the report, a watchdog)
        // accumulated its own history and the strings described older states as well as the current one. The stable
        // configuration and the current counters belong in the snapshot; a history, if one is ever wanted, is a
        // separate bounded structure and not this list.
        this.diagnostics.clear();
        // takeTimingSummary() was written but never called, so every post-process investigation ran without the numbers
        // that say where the handoff time goes. Gated, because it is a per-report string rather than a per-frame cost.
        // Which depth format this device ended up with, and how: the choice is made once, from the device's own
        // answers, and a run that does not say which format it used cannot be compared with one on another GPU.
        this.diagnostics.add("depthFormat: " + org.soutalab.celtium.vulkan.VulkanDepthFormat.chosenName()
                + " (" + org.soutalab.celtium.vulkan.VulkanDepthFormat.decisionSummary() + ")");
        // Unconditional: the mirror census is not a timing and must not depend on a diagnostics switch to be visible.
        this.diagnostics.add("stateMirror: stubTransformCalls="
                + (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                        ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.stubTransformSummary() : "bridge"));
        if (Boolean.getBoolean("celtium.vulkan.handoffTimings")) {
            // The refusal counters belong beside the timings: the shutter that keeps a shader-drawn batch out of Vulkan
            // changes which batches are taken, so a run has to say how many it refused and why.
            // How many transform calls reached the stub. Not a hook-failure count on its own - the mirror hooks at
            // the head of the GlStateManager method and does not cancel the GL body - but the denominator for the
            // mirror's own application counts, which is the comparison that found the missing float translate.
            this.diagnostics.add("stateMirror: stubTransformCalls="
                    + (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                            ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.stubTransformSummary() : "bridge"));
            this.diagnostics.add("acceptance: refusedBeforeAppend=" + this.refusedBeforeAppend
                    + " fullGlUploads=" + VulkanGlTexture.fullUploadSummary()
                + " submitFaultInjections=" + VulkanSubmitFault.injections()
                + " submitRequests=" + VulkanSubmitFault.requests()
                + " textureFaultInjections=" + VulkanTextureFault.injections()
                + " textureUploadFailures=" + this.textureUploadFailures
                + " lastTextureUploadFailure=" + this.lastTextureUploadFailure
                + " descriptorFaultInjections=" + VulkanDescriptorFault.injections()
                + " descriptorRequests=" + VulkanDescriptorFault.requests()
                + " modelDraws=" + this.modelDraws
                + " texMatrixGroups=" + this.texMatrixGroups
                + " modelDrawRefusals=" + this.modelDrawRefusals
                + " stubTransformCalls=" + (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                        ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.stubTransformSummary() : "bridge")
                + " stubTransformCalls=" + (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                        ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.stubTransformSummary() : "bridge")
                + " stubTransformCalls=" + (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                        ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.stubTransformSummary() : "bridge") + " modelMissingMeshes=" + this.modelMissingMeshes
                + " modelLastRefusal=" + this.modelLastRefusal
                + " modelMeshLists=" + (this.modelMeshes == null ? -1 : this.modelMeshes.size())
                + " " + VulkanDynamicMesh.describeModel()
                + " " + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lifecycle()
                + " modelMeshQuads=" + this.modelMeshQuads
                + " modelMeshFailure=" + this.modelMeshFailure
                + " modelStoredFloats=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedFloats()
                + " modelFirstVertex=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredVertex()
                + " modelMatrixPair=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.matrixComparison()
                + " modelLists=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lists()
                + " modelListQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listSummary()
                + " modelQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.quads()
                + " modelQuadVertices=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.vertices()
                + " modelQuadExtent=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.summary()
                + " textureDeletions=" + this.glTextureDeletions
                + " atlasReestablished=" + this.atlasReestablished
                + " lightmapReestablished=" + this.lightmapReestablished
                + " texturePatchCost=" + (this.textureRegistry == null ? "-" : this.textureRegistry.patchCostSummary())
                + " displayListOrigins=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.origins()
                + " displayListReplaysPassOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassOpen()
                + " displayListReplaysPassClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassClosed()
                + " prepareDescriptorFailures=" + this.prepareDescriptorFailures
                + " lastPrepareFailure=" + this.lastPrepareFailure
                + " groupsQueued=" + this.immediateGroups.size()
                + " groupsRecorded=" + this.recordedImmediateGroups.size()
                + " groupsRetired=" + this.retiredImmediateGroups.size()
                + " groupsStaleTargetGeneration=" + this.groupsStaleTargetGeneration
                + " targetGeneration=" + this.targetGeneration
                + " groupsStaleWorldEpoch=" + this.groupsStaleWorldEpoch
                + " worldEpoch=" + this.worldEpoch
                + " groupsDiscardedAtWorldTeardown=" + this.groupsDiscardedAtWorldTeardown
                + " groupsDiscardedAtFullTeardown=" + this.groupsDiscardedAtFullTeardown
                + " displayListOriginCancels=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originCancels()
                + " displayListCancelled=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.cancelled()
                + " displayListReplaysOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameOpen()
                + " displayListReplaysClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameClosed()
                + " displayListReplays=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.total()
                + " displayListDistinct=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.distinctLists()
                + " displayListTop=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.summary()
                + " modelDraws=" + this.modelDraws
                + " modelDrawRefusals=" + this.modelDrawRefusals
                + " modelLastRefusal=" + this.modelLastRefusal
                + " modelMeshLists=" + (this.modelMeshes == null ? -1 : this.modelMeshes.size())
                + " " + VulkanDynamicMesh.describeModel()
                + " " + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lifecycle()
                + " modelMeshQuads=" + this.modelMeshQuads
                + " modelMeshFailure=" + this.modelMeshFailure
                + " modelStoredFloats=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedFloats()
                + " modelFirstVertex=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredVertex()
                + " modelMatrixPair=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.matrixComparison()
                + " modelLists=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lists()
                + " modelListQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listSummary()
                + " modelQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.quads()
                + " modelQuadVertices=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.vertices()
                + " modelQuadExtent=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.summary()
                + " displayListOrigins=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.origins()
                + " displayListReplaysPassOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassOpen()
                + " displayListReplaysPassClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassClosed()
                + " displayListOriginCancels=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originCancels()
                + " displayListCancelled=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.cancelled()
                + " displayListReplaysOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameOpen()
                + " displayListReplaysClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameClosed()
                + " displayListReplays=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.total()
                + " displayListDistinct=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.distinctLists()
                + " displayListTop=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.summary()
                + " shaderProgramRefusals=" + this.shaderProgramRefusals
                + " shaderProgramRefusedIds=" + this.shaderProgramRefusedIds
                + " shaderProgramRefusedPrograms=" + this.shaderProgramRefusedPrograms
                    + " textureRefusals=" + this.textureRefusalsBeforeAccept
                    + " accepted=" + this.frameAccepted);
        }
        if (Boolean.getBoolean("celtium.vulkan.handoffTimings") && this.frameTarget != null) {
            String timings = this.frameTarget.takeTimingSummary();
            if (timings != null) {
                this.diagnostics.add(timings);
            }
        }
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
        if (Boolean.getBoolean("celtium.vulkan.refuseTerrainPass")) {
            // Isolation switch: with the terrain pass out of the frame, whatever is still wrong in the picture
            // belongs to the immediate path, and the other way round.
            return;
        }
        if (pass == null) {
            // Something calls this with no render pass at all. Every guard below names the pass - `pass.name()` is the first
            // thing each of them touches - so a null one threw, was caught by the batch handler, and surfaced as
            // "copy failed: NullPointerException ... TerrainRenderPass.name() ... pass is null". A caller with no pass has no
            // pass to draw, so the answer is to say so rather than to dereference it.
            this.lastSkipReason = "render was called with no render pass";
            return;
        }
        if (REFERENCE_RENDER) {
            // A reference render: the mod's own hooks call this instance directly, so leaving the registry empty was not
            // enough - measured, the "reference" still drew 1641 passes and reported regions=4. Nothing is drawn here, so
            // the frame is OpenGL's.
            return;
        }
        // Everything the game drew before this pass - the sky, the void plane under the horizon, the sun and moon -
        // was recorded as immediate work and is still queued. GL's order is call order, so those draws belong to the
        // frame BEFORE the terrain: leaving them queued submits them after this pass, and the void plane then paints
        // over the terrain it is supposed to be hidden behind.
        flushPendingBeforeGlDraw();
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
        // A resource failure while preparing the uniforms must not take the game down: the pass is skipped and the
        // reason recorded, which is the same shape as the missing-atlas skip just below. An injected allocation failure
        // here used to escape into Minecraft's render loop and end the run with "Game crashed!" and no report.
        try {
            ensureUniformCapacity((this.regionResources.size() * MAX_UNIFORM_PASSES) + 1);
        } catch (RuntimeException error) {
            this.uniformPreparationFailures++;
            this.lastUniformPreparationFailure = String.valueOf(error);
            record("pass skipped: uniform preparation failed: " + error);
            this.passSkips++;
            return;
        }

        refreshTextures();
        if (this.atlasTexture == null || this.lightmapTexture == null) {
            // Without both textures the descriptor set cannot be filled; the pass is skipped and the reason is
            // recorded instead of drawing with an unbound atlas.
            this.passSkips++;
            this.lastSkipReason = pass.name() + ": atlas or lightmap not mirrored";
            record("pass " + nameOf(pass) + " skipped: the block atlas or the lightmap is not mirrored yet");
            return;
        }

        boolean useBlockFaceCulling = !pass.isSorted();
        this.lastDrawCommands = 0;
        this.lastSectionsDrawn = 0;
        this.frameIndex++;

        this.renderCalls++;
        this.lastUiPass = pass;
        this.lastTerrainPassFrame = this.frameId;

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

            }
        } catch (RuntimeException | LinkageError error) {
            this.passErrors++;
            record("render failed: " + describe(error));
        }

        if (this.gpuStalled) return;
        if (!submitted) {
            // endFrame() makes GL wait on the Vulkan->GL semaphore, and GL has no timeout. A submission must
            // therefore consume the GL->Vulkan semaphore and signal the Vulkan->GL one even when nothing was
            // recorded: an empty acquire/release round trip hands the untouched images straight back, which is what
            // the game expects. Only the first attempt is repeated - a failing recovery is reported and endFrame()
            // still runs so the frame cannot be left open.
            try {
                if (!waitForIdle()) throw new IllegalStateException("terrain recovery fence failed");
                check(vkResetCommandBuffer(this.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT),
                        "vkResetCommandBuffer(terrain recovery)");
                this.commandState.reset();
                this.immediateGroups.addAll(0, this.recordedImmediateGroups);
                this.recordedImmediateGroups.clear();
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
    private String immediateLastRefusal = "unrecorded";
    /** The checkpoint the offer path last passed, so an unnamed refusal can still say where it stopped. */
    private int offerStage;
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
    /** Split of the per-batch cost: time inside prepareImmediate (pipeline and descriptor preparation). */
    private long prepareImmediateNanos;
    private int prepareImmediateCalls;
    /** Split of the batch call outside the preflight: group (mesh) creation against the vertex append. */
    private long groupCreateNanos;
    /** CPU time of the same three inner calls, so wall minus CPU says which one blocks in a native call. */
    private long appendCpuNanos;
    private long preflightCpuNanos;
    private long groupCreateCpuNanos;
    private int groupCreateCount;
    private long appendNanos;
    private int appendCount;
    /** The GL query the custom-program shutter makes per batch, timed: it is a synchronising call on some drivers. */
    private long programQueryNanos;
    private int programQueryCount;
    /** Split of the batch call's remainder: matrices, state capture, state key, and the group's state clone. */
    private long matrixCopyNanos;
    private long captureStateNanos;
    private long stateKeyNanos;
    private long groupCloneNanos;
    private int groupCloneCount;
    /** Split of prepareImmediate: pipeline+texture lookup against the pipeline/descriptor branch. */
    private long prepLookupNanos;
    /** Which half of the lookup dominates: the terrain pipeline lookup or the texture registry lookup. */
    private long prepTerrainPipelineNanos;
    private long prepRegistryNanos;
    private long prepBranchNanos;
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
    /**
     * Whether the immediate path takes the projection and model-view matrices from the state mirror instead of reading
     * them from OpenGL. Now the default: the two were compared and agree exactly (matrixMirrorDelta proj:0.0 view:0.0)
     * and the frames are pixel-identical (mean absolute difference 0.000 against the GL-reading path), so the GL read
     * is one dependency the native path no longer needs. The property stays so a run can put the read back for
     * comparison - {@code -Dceltium.vulkan.mirroredMatrices=false}.
     */
    private static final boolean MIRRORED_MATRICES =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.mirroredMatrices", "true"));

    /** Largest absolute element difference seen between the mirrored matrices and the ones read from OpenGL. */
    private float mirroredProjectionDelta = -1.0f;
    private float mirroredModelViewDelta = -1.0f;
    private int matrixComparisons;
    private int matrixExactComparisons;
    /**
     * Multiplies that reached the mirror, from every owner: {@code GlStateManager.multMatrix} (hooked in
     * {@code GlStateManagerMixin}) and the item camera transform ({@code ForgeHooksClientMixin}). Read against the
     * stub's raw {@code glMultMatrixf} arrival census - the stub applies nothing on purpose, so arrivals without a
     * mirrored multiply are the signal that a raw call arrived with no owner.
     */
    private int mirroredMultiplies;
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
    private int outsideWorldPassAccepted;
    /** Accepted batches per vertex-format id, so the contribution of each shape is attributable (sky is id 6). */
    private final int[] acceptedByFormat = new int[VulkanImmediateFormats.COUNT];
    private boolean currentRenderTargetIsMain = true;
    /** The identity of the main framebuffer, so an unfiltered flush can mean "main" rather than "everything". */
    private long mainTargetIdentity = Long.MIN_VALUE;
    /** Off-target batches per render-target identity, so the per-target work has a size to plan against. */
    /** Off-target render targets built for the game's other framebuffers: the resource half of per-target drawing. */
    private int offTargetTargetsCreated;
    private int offTargetReadyToTake;
    private int offTargetDrawn;
    /** Batches offered for an off-target target, which is not the same as batches taken for one. */
    private int offTargetOffered;
    /** Batches accepted for an off-target target - the one that is evidence. */
    private int offTargetAccepted;
    /** Batches refused because the mirror they need has only ever received a partial write. */
    private int partialMirrorRefusals;
    /** Composites offered while the default framebuffer was bound, taken into the shared image instead of refused. */
    private int defaultFramebufferCompositesTaken;
    /** Mirrors that had only a partial write and were supplied whole by the offer path (audit A04). */
    private int partialMirrorsCompleted;
    /** Batches refused by a preflight that now runs before anything is appended (audit A08). */
    private int refusedBeforeAppend;
    /** Acceptances counted across all formats, so the table can be printed incrementally. */
    private int immediateAcceptedTotal;
    private int announcedUploads;
    /**
     * Invalidation requests that were not honoured because there is no GL texture to re-read.
     * <p>
     * Non-zero in the GL-less configuration is expected and correct; the number is here so a reader can tell "the
     * mirror was kept because there is no GL" from "the mirror was dropped and the draw had nothing".
     */
    private int textureInvalidationsKeptGlless;
    /**
     * Whether a GL-less invalidation keeps the mirror instead of marking it for a re-read that cannot happen.
     * <p>
     * The behaviour is the default; the switch exists as a measurement control, not as a way to run the renderer. The
     * question the panel's absence raised is whether format 6 draws nothing because their mirror is empty or because
     * the colourless pipeline reads the vertex buffer with the wrong layout, and both candidates moved between the
     * 12:52 run and this one. Two runs of one jar with only this flag apart separate them: with {@code false} the
     * invalidation takes the same path it took before the GL-less branch existed
     * ({@code VulkanTextureRegistry.invalidate} plus the direct atlas/light-map refresh), so a picture that still
     * shows the panel answers "the mirror path was not the missing piece" and names the pipeline as the cause.
     */
    private static final boolean GLESS_KEEPS_INVALIDATIONS =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glessKeepsInvalidations", "true"));
    private int animatedImageFills;
    private int directMirrorFromAnnouncement;
    /** Readbacks that were skipped because the announced pixels had already been applied to the sampled image. */
    private int readbackSkippedAfterAnnouncement;
    /** Why a preflight returned no descriptor, split by cause so the refusal can be acted on. */
    private int prepareDescriptorFailures;
    /** Preflights refused because no pass had run yet, and the frame range they happened in. */
    private int prepareNoPassRefusals;
    private long prepareNoPassFirstFrame = -1L;
    private long prepareNoPassLastFrame = -1L;
    private String lastPrepareFailure = "none";
    /** Cached highest line width the device can draw, or -1 before it has been asked. */
    private float maxLineWidth = -1.0F;
    /** Line batches drawn by the line pipeline, and how many were refused for their width. */
    private int lineBatchesNative;
    private int lineBatchesRefusedForWidth;
    /**
     * Line batches drawn at the 1.0 width this renderer implements when OpenGL asked for more (BH-12): the block
     * selection outline asks for 2.0 and the fishing rod's line is a line batch too, and under the GL-less window a
     * refusal draws nothing. Counted so the difference is reported rather than silent.
     */
    private int lineBatchesNarrowed;
    /** What the swapchain said about the last present: the report boolean alone does not say why it failed. */
    private String lastPresentResult = "none";
    /** Frames presented from Vulkan, and the attempts that did not reach the queue. */
    private int framePresentedFromVulkan;
    private int presentAttempts;
    private int presentFailures;
    /** Frame composites left to the present path because Vulkan had already shown the frame. */
    private int glCompositesSkipped;
    private int gameFramebufferAdopted;
    private boolean gameFramebufferIsShared;
    /** The first GL error seen after the swap and the point that saw it, or null/0. */
    private String firstGlErrorWhere;
    private int firstGlErrorCode;
    /** A completed frame's content is in the shared image and has not been presented yet. */
    private boolean lastFrameReachedQueue;
    private boolean presentedImageDumpRequested;
    /** A presented frame has not reached the game's swap point yet, and how many swaps that skipped. */
    private boolean swapPendingFromVulkan;
    private int glSwapsSkipped;
    /** Per-frame present calls that arrived, and why the last one did not present. */
    private int presentHookCalls;
    /** The path the fixture asked its presented-image dump to be written to, re-asked on a cadence. */
    private java.nio.file.Path presentedDumpPath;
    /** How many bound-pipeline/descriptor lines have been recorded; bounded so a run cannot flood. */
    private int boundHandleLogs;
    /** Chain targets adopted from the renderer's own off-target image, and the ones refused. */
    private int chainAdoptions;
    /** Input images registered before the chain preflight, and preflight refusals printed, both bounded. */
    private int chainAdoptionDeclarations;
    private int preflightRefusalsReported;
    /** Bounded: how many chain-input state lines this run has printed. */
    private int chainInputReports;
    /**
     * Diagnostic for the chain's input image: batches recorded before each chain read, and how many groups were still
     * queued for the input's identity at that moment. The two are what tell "the silhouette is in the image" apart
     * from "the model path drew it and the chain read an image it had not reached yet".
     */
    private long lastChainInputRecordedBatches = -1L;
    private int chainInputBatchLogs;
    /**
     * Lab switch for the one ordering change this task makes: register the chain's reported input image before the
     * preflight that judges it. Default on; {@code -Dceltium.vulkan.chainPreflightAdoption=false} restores the old
     * order in the same jar, which is the A/B the chain's {@code postChains} counter is read against.
     */
    private static final boolean CHAIN_PREFLIGHT_ADOPTION =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.chainPreflightAdoption", "true"));

    /**
     * Token of the device-wide wait in progress, and the token whose wait has returned. Per-call rather than one
     * shared flag: a late watchdog must be able to tell "my wait is still running" from "a later wait is running now"
     * (audit A05).
     */
    private long deviceWaitToken;
    private long deviceWaitFinishedToken = -1L;
    private final Object deviceWaitLock = new Object();
    /** The framebuffer identity of the last bind the game made, i.e. what it is rendering into now. */
    private long currentFramebufferIdentity;
    private int chainAdoptionsRefused;
    /** How many lightmap identity lines have been recorded; bounded. */
    private int lightmapIdentityLogs;
    /** How often the camera seed was skipped because there is no context to read it from. */
    private int cameraSetupSeedsSkipped;
    /** identity -> {colour texture name, texture width, texture height}, reported at each framebuffer bind. */
    private final java.util.Map<Long, long[]> framebufferColorTextures = new java.util.concurrent.ConcurrentHashMap<>();
    private int framebufferColorTextureReports;
    private int framebufferColorTextureLog;
    /** Distinct framebuffer identities the game has bound, for the bounded census the routing rule is read against. */
    private final java.util.Set<Long> framebufferBindCensus =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** How many orthographic projections the mirror has been given; the GUI's own, if it arrives at all. */
    private int orthoCalls;
    /** How many frame images the periodic dump has written; capped so a long run cannot fill the disk. */
    private int frameDumpsWritten;
    private String presentSkipReason = "never called";
    /** Textures supplied whole on their first offer, and the ones that could not be. */
    private int lazyMirrorSupplies;
    private int lazyMirrorFailures;
    private String lastLazyMirrorFailure = "none";
    /** Texture lifecycle: reallocations seen, and how often the sampled atlas/light map had to be re-established. */
    private int glTextureDeletions;
    private int atlasReestablished;
    private int lightmapReestablished;
    /** The GL texture unit the light map is bound on, passed in from the mod side; -1 until it is known. */
    private int lightmapUnit = -1;
    /**
     * The light-map coordinate pair the game last set, captured per draw from
     * {@code OpenGlHelper.setLightmapTextureCoords} (see {@code LightmapCoordsMixin}). It is per draw and not per
     * frame on purpose: it changes for every entity, TESR, held item and layer, and the group enqueued while it holds
     * belongs to that draw.
     */
    private float lightmapCoordX;
    private float lightmapCoordY;
    /** How many captures arrived and how many groups carried one into the push block, for the night measurement. */
    private int lightmapCoordCalls;
    private int lightmapCoordGroups;
    /**
     * Groups offered outside the world pass whose light-map coordinate is the full-bright pair instead of the captured
     * one. Named so the fix is visible in the same census as the capture it replaces: at zero the rule did not fire and
     * a hotbar item is still drawn with the world's light, whatever the picture looks like.
     */
    private int lightmapFullBrightGroups;
    /**
     * The ITEM family's batches split by which variant they got and by the stage they were offered in. This is the
     * instrument the hotbar measurement needed and did not have: a GUI item that arrives unlit never samples the light
     * map, so no light-map coordinate - captured or full-bright - can change its brightness, and a leg that reports
     * only the coordinate counters cannot tell that apart from "the rule did nothing".
     */
    private int itemLitGroups;
    private int itemLitGuiGroups;
    private int itemUnlitGroups;
    private int itemUnlitGuiGroups;
    /**
     * Whether the light-map texture unit is enabled right now, from {@code EntityRenderer.enableLightmap()} /
     * {@code disableLightmap()} (SRG {@code func_180436_i} / {@code func_175072_h}). Defaults to <em>enabled</em> so a
     * dropped hook leaves the captured coordinate in place - the behaviour before this rule - rather than drawing every
     * entity full-bright. The two counters below are what say the hook ran.
     */
    private boolean lightmapUnitEnabled = true;
    private int lightmapUnitEnables;
    private int lightmapUnitDisables;
    /** Light maps taken again on demand inside the reallocation window, and the last failure doing so. */
    private int lightmapSuppliedOnDemand;
    private String lastLightmapSupplyFailure = "none";
    private int animatedAnnounceDiagnostics;
    private int dynamicTextureFills;
    private int dynamicAnnounceDiagnostics;
    private String lastInvalidationOrigin = "unknown";
    /** The texture ids whose invalidation origin has been printed. */
    private final java.util.Set<Integer> invalidationOrigins = new java.util.HashSet<Integer>();
    /** Acceptances split by whether the game had blending on, which is what translucency is here. */
    private int immediateBlendedAccepted;
    private int immediateOpaqueAccepted;
    /** Set while an off-target pass records, so the shared draw path binds that pass's pipeline. */
    private int offTargetCopiedBack;
    /** One format-1 pipeline per off-target render pass: a pipeline belongs to the pass it was built for. */

    private final java.util.Map<Long, java.util.concurrent.atomic.AtomicInteger> offTargetBatchesByTarget =
            new java.util.HashMap<Long, java.util.concurrent.atomic.AtomicInteger>();
    private int offTargetRefusals;
    private int textureRefusalsBeforeAccept;
    /** Bound-texture comparisons between the state mirror and GL, and how many disagreed. */
    private int boundTextureComparisons;
    /** Announces that carried the size from the game instead of asking OpenGL for it. */
    private int announcedDynamicTextures;
    /** Whether the game has set a colour yet, i.e. whether the state mirror's colour can be trusted. */
    private boolean colorMirrorHasValue;
    /** Which source the format-7 colour came from, reported so a run says whether the mirror is really in use. */
    private int colorMirrorUses;
    private int boundTextureMismatches;

    /**
     * Checks the state mirror's bound texture against what OpenGL answers.
     * <p>
     * The two announce paths used to read {@code GL_TEXTURE_BINDING_2D} with the comment that it is "the one place in this
     * renderer where a GL query buys something a hook cannot supply" - but {@code bindTexture} is one of the calls the
     * state mirror already hooks, so the mirror can supply it. This comparison is what decides that: a run reports how
     * many samples were taken and how many disagreed, and only a zero mismatch count justifies dropping the query
     * (completion condition (b) is about not needing GL reads, not about assuming they agree).
     */
    private static final boolean COMPARE_BOUND_TEXTURE =
            Boolean.getBoolean("celtium.vulkan.compareBoundTexture");

    private void compareBoundTextureWithGl(int mirrored) {
        if (!COMPARE_BOUND_TEXTURE) {
            // Off by default: the comparison itself is a GL read, and the measurement that justified dropping the query
            // (525 samples, 0 mismatches) is recorded in the plan. Turn it on to re-check after a change to the mirror.
            return;
        }
        this.boundTextureComparisons++;
        int live = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        if (live != mirrored) {
            this.boundTextureMismatches++;
            if (this.boundTextureMismatches <= 3) {
                record("bound texture mirror mismatch: mirror=" + mirrored + " gl=" + live);
            }
        }
    }

    /** Batches refused because a custom shader program was bound (the game's post-process chain and mod shaders). */
    private int shaderProgramRefusals;
    /** Whether the entity layout's first refusal has been reported. */
    private boolean entityProgramLogged;
    /** Which programs the custom-program shutter refused, and how often - the entity path's identity. */
    private final java.util.LinkedHashMap<String, Integer> shaderProgramRefusedIds = new java.util.LinkedHashMap<>();
    /** The same refusals grouped by program alone. */
    private final java.util.LinkedHashMap<Integer, Integer> shaderProgramRefusedPrograms = new java.util.LinkedHashMap<>();
    private int postAcceptDroppedGroups;
    /** Per target: batches dropped at a frame boundary because no pass ran for that identity (BH-15). */
    private final java.util.Map<Long, Integer> unflushedTargetDroppedByTarget =
            new java.util.HashMap<Long, Integer>();

    /** Per target: groups present at flush entry and flush entries, to separate "never queued" from "not drawn". */
    private final java.util.Map<Long, Integer> flushEntryGroupsByTarget = new java.util.HashMap<Long, Integer>();
    private final java.util.Map<Long, Integer> flushEntryPassesByTarget = new java.util.HashMap<Long, Integer>();
    /** Flush entries whose target was not the bound one. Zero while the drain is reverted (BH-14/15/16). */
    private int drainedOtherTargetPasses;

    /** {@return one line per target whose groups were dropped at a frame boundary}, or "none". */
    private String unflushedTargetDropSummary() {
        if (this.unflushedTargetDroppedByTarget.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (java.util.Map.Entry<Long, Integer> entry : this.unflushedTargetDroppedByTarget.entrySet()) {
            text.append(Long.toHexString(entry.getKey().longValue())).append(":frames=").append(entry.getValue())
                    .append(' ');
        }
        return text.toString().trim();
    }

    /** {@return one line per target the drain visited}, with its entry counts. */
    private String flushEntrySummary() {
        if (this.flushEntryPassesByTarget.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (java.util.Map.Entry<Long, Integer> entry : this.flushEntryPassesByTarget.entrySet()) {
            text.append(Long.toHexString(entry.getKey().longValue())).append(":entries=").append(entry.getValue())
                    .append(",groupsAtEntry=")
                    .append(this.flushEntryGroupsByTarget.getOrDefault(entry.getKey(), Integer.valueOf(0)))
                    .append(' ');
        }
        return text.toString().trim();
    }

    /** Groups dropped because no pass could draw their target, and passes that failed (BH-15). */
    private int unflushedTargetGroupsDropped;
    private int unflushedTargetGroupsDroppedLogged;
    private int unflushedTargetPassFailures;
    private String lastUnflushedTargetFailure = "none";

    /** Bounded lines for the copy's read resolution (see celtium$ownedReadImage). */
    private static final int READ_RESOLVE_LOG_LIMIT =
            Integer.getInteger("celtium.vulkan.readResolveLog", 6).intValue();
    private int readResolveLogs;
    /** Reads that resolved through the frame's last non-main draw target rather than the bound target (panorama). */
    private int readResolvedFromLastDrawnTarget;

    /**
     * The last non-main, non-zero identity a batch was accepted for in this frame (panorama read resolution).
     * <p>
     * A copy can read GL name 0 while the frame's content was drawn into a framebuffer whose identity is not the one
     * bound at the copy: measured on the menu, the panorama's cube faces are offered for identity 572879803 (routed to
     * the chain pair) and the copy into texture 20 then reads with {@code readFbo=0} and the default pair bound, which
     * resolved to an image the faces never wrote and returned uniform white. The read has to follow the image the
     * draws actually wrote, and this is that fact.
     */
    private long lastDrawnOffTargetIdentity;
    private long lastDrawnOffTargetFrame = -1L;

    /** Set when a wait timed out: no further recording or submission happens until the renderer is rebuilt. */
    private boolean gpuStalled;
    /**
     * Window flushes that threw, and the batches they had to drop. Bounded to the frame they happened in since
     * task-60: the count and the text are what let a reader tell a refused window from a device that stopped.
     */
    private int windowFlushFailures;
    private long windowFlushFailureFrame = -1L;
    private long windowFlushDroppedBatches;
    /** Viewports issued per group; BH-13's counter, so "the rectangle was restored" is readable, not assumed. */
    private long groupViewportSets;
    /**
     * Immediate groups whose capture saw {@code GL_CULL_FACE} enabled, and the ones that saw it disabled.
     * <p>
     * BH-08's per-batch proof: before the fix every group fell into the disabled column, because nothing ever wrote
     * the mirror's cull flag. A leg that shows both columns non-empty (the HUD and the world differ) is the evidence
     * that the capability and the face now reach the pipeline key, which is stronger than "the state flows" read off
     * the source. Counted at capture, where the group's own state is taken.
     */
    private long cullEnabledGroups;
    private long cullDisabledGroups;
    /**
     * BH-07's per-batch counts, taken at capture with the group: batches with blending on, and of those the ones whose
     * alpha factor pair is not the {@code ONE / ONE_MINUS_SRC_ALPHA} pair every pipeline baked before the fix.
     * <p>
     * The second number is the one that matters. Before BH-07 it was zero <em>by construction</em> - the capture never
     * read the alpha pair - so a non-zero value in a leg is the proof that the pair now travels, and the pipeline
     * caches' size next to it is the proof that it also closes a pipeline rather than being captured and dropped.
     */
    private long blendGroups;
    private long blendAlphaNonDefaultGroups;
    /** BH-10: batches whose captured write mask was not all four channels, and the last such mask. */
    private long partialColorMaskGroups;
    private int lastPartialColorMask = -1;
    /**
     * BH-10's histogram over the captured write masks: one slot per possible mask, index = the
     * {@code VK_COLOR_COMPONENT_*} nibble (0x0 = nothing written, 0x7 = RGB without alpha, 0xF = all four).
     * <p>
     * The last-mask field could only ever name one value, and the Fancy-cloud experiment needs to know <em>which</em>
     * masks were captured: the depth-only pass is exactly {@code 0x0}, so a run whose histogram holds only
     * {@code 0x7} (the GUI hover's alpha-masked highlight) says the depth-only pass was not in the frame at all -
     * the difference between "the mask reached the pipeline and changed nothing visible" and "the group this finding
     * is about never ran". Complete rather than bounded: 16 slots, no eviction.
     */
    private final long[] colorMaskHistogram = new long[16];
    /** BH-07: batches whose captured blend equation was not ADD. */
    private long nonAddBlendEquationGroups;
    /** BH-03: batches captured with the alpha test on, with a comparison other than GREATER, and with it off. */
    private long alphaFuncGroups;
    private long nonGreaterAlphaFuncGroups;
    private long alphaTestDisabledGroups;
    /** The last comparison function captured, as the raw GL enum; 0 means the test was disabled for that batch. */
    private int lastAlphaFunc;
    /**
     * Batches a failed window flush puts back on the queue instead of throwing away.
     * <p>
     * The frame is abandoned either way, so nothing that was already recorded into it survives; but a group whose
     * recording never reached the queue has not been drawn, and clearing it here is what erased every entity and
     * held-item batch of the window - measured as {@code id4 28 accepted / 0 drawn} and {@code id9 228 / 0} with no
     * loss column in the per-format histogram to show it. Paired with {@link #windowFlushDroppedBatches}: a group is
     * either drawn, or retained here, or counted as dropped there.
     */
    private long windowFlushRetainedBatches;
    private String lastWindowFlushFailure = "none";
    /**
     * Ordering flushes that threw ({@code flushPendingBeforeGlDraw}'s catch), with their text and the batches they had
     * to drop. Bounded to the frame they happened in, for the same reason as the window-flush counters: this catch used
     * to latch the renderer for the whole session, and the user's loading screen was where it did.
     */
    private int orderingFlushFailures;
    private long orderingFlushDroppedBatches;
    private String lastOrderingFlushFailure = "none";
    /** Where the latch was set from, so a report names the origin instead of only the state (task-60). */
    private String stallOrigin = "none";
    /**
     * True from the moment a framebuffer replacement is announced until the shared images match the new size again.
     * <p>
     * A resize replaces the game framebuffer, and every ordering flush during that window waits on a device that is in
     * the middle of the replacement; that wait failing marks the device stalled permanently, since nothing clears that
     * flag. Measured: one mid-run resize froze the present path (presentedFromVulkan stuck at 355 against 785 for the same
     * duration without a resize), with the stall first at the bind and, once the bind was guarded, at the clear. The
     * window is what both have in common.
     */
    private boolean framebufferReplacing;
    /** Ordering flushes skipped at a framebuffer replacement, and the batches the first of them had to drop. */
    private int replaceBoundaryDrops;
    private int replaceBoundaryDropBatches;

    /**
     * Marks the device stalled and says where from, once.
     * <p>
     * The stall is permanent - nothing clears it, by design, because the resources it protects may still be owned by a
     * command buffer that never completed. It happened on a mid-run resize and left the present path declining with
     * "no frame target, or stalled" for the rest of the session (presentedFromVulkan frozen at 355), so which of the four
     * places set it is the fact the fix needs.
     */
    private void noteStalled() {
        if (!this.gpuStalled) {
            this.gpuStalled = true;
            StackTraceElement[] stack = new Throwable().getStackTrace();
            String where = "unknown";
            // The first frame outside this class says which caller's path reached the latch; the first frame inside
            // it says which of the ten noteStalled() sites did. Recording only the outer one hid the site - measured
            // on the loading screen's font path, where the outer frame was the font submit and the site was not the
            // window-flush catch it was first read as.
            String site = "unknown";
            for (StackTraceElement frame : stack) {
                if (frame.getClassName().startsWith("org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend")) {
                    if (site.equals("unknown") && !"noteStalled".equals(frame.getMethodName())) {
                        site = frame.getMethodName() + ':' + frame.getLineNumber();
                    }
                    continue;
                }
                where = frame.getClassName() + '.' + frame.getMethodName() + ':' + frame.getLineNumber();
                break;
            }
            this.stallOrigin = where + " via " + site;
            System.out.println("[Celtium] CELTIUM_STALL site=" + where + " latchSite=" + site
                    + " target=" + this.targetWidth + "x"
                    + this.targetHeight + " frameId=" + this.frameId + " frameTargetNull=" + (this.frameTarget == null)
                    + " presentSkipReason=[" + this.presentSkipReason + "]");
        }
        this.stalledStops++;
    }
    private int stalledStops;
    private int stalledTeardownSkips;
    /** Inject a wait failure after this many waits; 0 disables. See the comment in waitForIdle. */
    private static final int FAIL_IDLE_AFTER = Integer.getInteger("celtium.vulkan.failIdleAfter", 0).intValue();
    private int idleWaits;
    /** Windows abandoned because the device stalled; the batches they held are lost and counted. */
    private int stalledGeometryDrops;
    private int stalledGeometryDroppedBatches;
    private int injectedIdleFailures;
    /** One-shot count for the injected device-lost fence result. */
    private int injectedDeviceLosses;
    /** Caps the env-combine report so a hurt mob does not log every frame. */
    private int envCombineLogged;
    /** The texture matrix the currently open group was accepted under; {@code null} for identity. */
    private float[] currentGroupTexMatrix;
    /** Groups drawn under a non-identity texture matrix - the glint and the renderer's own transforms. */
    private int texMatrixGroups;
    private boolean blendObserved;
    private int observedBlendSrc = -1;
    private int observedBlendDst = -1;
    private int currentGroupBlendSrc = -1;
    private int currentGroupBlendDst = -1;
    private int blendFuncIgnored;
    private static final boolean LIGHT_BIND_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.lightDiagnostics");
    private int lightBindPrints;
    /** Set for reference captures: load the mod, run the fixture, install nothing, let OpenGL draw. */
    private static final boolean REFERENCE_RENDER = Boolean.getBoolean("celtium.vulkan.referenceRender");
    private int terrainPasses;
    private int immediateOnlyPasses;
    private static final boolean TEXTURE_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.textureDiagnostics");
    private final java.util.Set<Integer> diagnosedTextures = new java.util.HashSet<Integer>();
    private String blendOutlier;
    /**
     * How many 2-argument {@code glBlendFunc} calls reached the mirror, and the last pair they carried.
     * <p>
     * Before this counter existed a 2-argument call was visible only through {@link #blendOutlier}, which is set for
     * anything that is not the ordinary alpha pair - so a run in which the game called {@code blendFunc(ONE, ONE)}
     * and nothing else unusual happened reported no evidence at all, and the fact that the call never reached the
     * mirror was invisible.
     */
    private int blendFuncTwoArgCalls;
    private String lastTwoArgBlendPair = "none";
    /** How many groups opened with each observed (src,dst) pair, so one run says whether they differ. */
    private final java.util.Map<Long, Integer> blendPairs = new java.util.HashMap<Long, Integer>();
    private VulkanImmediateParticlePipeline descriptorProbe;
    private int particleDescriptorRefusals;
    private String particleDescriptorFailureReason;
    /**
     * Batches that were accepted and then dropped, by format id.
     * <p>
     * Sized from {@link VulkanImmediateFormats#COUNT}, not from the eight ids that existed when it was written: the
     * summary prints every id now, and an array that stops at 8 would have thrown on the first drop of a layout past
     * it. It has no increment site today - the drops that happen are counted in
     * {@code postAcceptDroppedGroups}, which is per-event rather than per-format - so this reads as an all-zero row
     * and says that rather than looking like a measurement of "nothing was dropped by format".
     */
    private final int[] postAcceptDroppedByFormat = new int[VulkanImmediateFormats.COUNT];
    private int passBoundaryFlushes;
    /**
     * Flushes made by the world-to-GUI ordering rule in {@link #endWorldPass()} - the counter that names the rule.
     * <p>
     * A run with pending world-pass groups at the boundary prints a non-zero value here and the groups are drawn
     * before the GUI stage's depth-writing quad; without the rule the same groups were drawn afterwards and silently
     * failed their depth test while still counting as drawn.
     */
    private int worldPassBoundaryFlushes;

    // ---- the coalesced font glyph batch (task-59) ----------------------------------------------------------------
    //
    // 1.12.2's FontRenderer issues one tessellator.draw() per character, so every glyph was one window, one quad and
    // one submitImmediateBatch call - 1,012,751 of them in the user's session, 47,246 ms inside that call, 46.6 us per
    // glyph, all of it the per-call constant (state capture, state key, registry lookup, prepareImmediate, group key,
    // group creation, mesh append) recomputed for four vertices.
    //
    // The backend owns the pending run; the font appends to it. Ordering is safe by construction because the run is
    // flushed at the ENTRY of every other submission and at every group/pass/frame boundary, so held quads are always
    // appended before anything that came after them - never after. The counters say how each run went out, because a
    // batching that silently changed ordering is exactly the class of change this project has been bitten by.
    /**
     * The batching, on by default: `-Dceltium.font.batching=false` is the one-flag control.
     * <p>
     * It is 16x cheaper per glyph (the user's session with it on: 275,503 quads in 1,014 ms = 3.7 us per quad, against
     * 59 us with it off). It used to garble text, and the mechanism was that {@code submitImmediateBatch} captures the
     * bound texture and the matrices at *submission* time: a run flushed at an intervening submission - which is what
     * happens around the menu's buttons - was drawn with the intervening draw's texture and transform, while a run
     * flushed at a boundary (the footer text, drawn last) was correct. `celtium$flushPendingFontBatch` now applies the
     * state the run was appended under and restores it afterwards, and the menu text is legible with the batching on
     * (compositor capture, jar 64923c0a: every button label, the splash line and the copyright line).
     */
    private static final boolean FONT_BATCHING = !"false".equalsIgnoreCase(
            System.getProperty("celtium.font.batching", "true"));

    /** The run's vertices, owned by this backend and grown as needed; the font's own buffer is copied into it. */
    private java.nio.ByteBuffer pendingFontVertices;
    private int pendingFontBytesUsed;
    private int pendingFontVertexCount;
    private Object pendingFontFormat;
    private int pendingFontStride;
    private int pendingFontFormatId;
    private int pendingFontDrawMode;
    /** The cheap state witnesses a run was started with: a change in any of them flushes it through the normal path. */
    private int pendingFontTexture = -1;
    private long pendingFontColorBits;
    private boolean pendingFontAlphaTest;
    private float pendingFontAlphaRef;
    private boolean pendingFontActive;
    /** The matrix pair the open run was started under; a change flushes it, as the normal path's group key does. */
    private final float[] pendingFontProjection = new float[16];
    private final float[] pendingFontModelView = new float[16];
    /** How each coalesced run was flushed: by an intervening submission, a boundary, or a state change. */
    private int fontBatchesFlushedIntervening;
    private int fontBatchesFlushedBoundary;
    private int fontBatchesFlushedStateChange;
    /** Glyph quads appended to a run and runs started, so "batched" is a quantity and not a claim. */
    private long fontQuadsAppended;
    private long fontBatchesStarted;
    private long fontAppendMillis;
    /** Diagnostic only: reading GL back per batch to compare against the mirror. Off in the normal path. */
    private static final boolean MATRIX_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.matrixDiagnostics");
    /**
     * Diagnostic only: the per-group projected NDC bounds that {@link #reportGroupBounds} computes.
     * <p>
     * That method is the instrument a "geometry drawn in the wrong place" defect wants - vertex 0 cannot tell
     * "projects on-screen but is rejected by the depth test" from "never enters an accepted group", while the bounds
     * over the whole group can - and it had no caller. It is called here behind this switch so the default output is
     * exactly what it was: with the switch off, nothing calls it and no line is printed.
     * <p>
     * {@code -Dceltium.vulkan.groupBoundsFormat=<id>} reports one layout instead of every layout (which is what one
     * wedged frame needs: 8000 groups of every format bury the six that matter), and
     * {@code -Dceltium.vulkan.groupBoundsCount=<n>} raises the six-sample cap.
     */
    private static final boolean GROUP_BOUNDS_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.groupBounds");
    private static final int GROUP_BOUNDS_FORMAT =
            Integer.getInteger("celtium.vulkan.groupBoundsFormat", -1).intValue();
    /**
     * The layouts {@link #reportGroupBounds} reports, from {@code groupBoundsFormat} as a comma-separated list.
     * <p>
     * A single id was enough while one question was being asked of one family. The sky is not one family: the sun and
     * the moon are {@code POSITION_TEX} (6) while the sky dome and the horizon plane are {@code POSITION} (7), and a
     * filter that can only name one of them hides whichever the artefact turns out to belong to - which is what
     * happened here. {@code -1} still means every layout.
     */
    private static final boolean[] GROUP_BOUNDS_FORMATS = groupBoundsFormats();

    private static boolean[] groupBoundsFormats() {
        if (GROUP_BOUNDS_FORMAT < 0) {
            return null;
        }
        boolean[] wanted = new boolean[VulkanImmediateFormats.COUNT + 1];
        for (String part : System.getProperty("celtium.vulkan.groupBoundsFormat", "").split(",")) {
            try {
                int id = Integer.parseInt(part.trim());
                if (id >= 0 && id < wanted.length) {
                    wanted[id] = true;
                }
            } catch (NumberFormatException notAnId) {
                // An unreadable entry narrows nothing; the ids that did parse still apply.
            }
        }
        boolean any = false;
        for (boolean value : wanted) {
            any |= value;
        }
        return any ? wanted : null;
    }

    private static boolean wantedGroupBoundsFormat(int formatId) {
        return GROUP_BOUNDS_FORMATS == null
                || (formatId >= 0 && formatId < GROUP_BOUNDS_FORMATS.length && GROUP_BOUNDS_FORMATS[formatId]);
    }
    private static final int GROUP_BOUNDS_LIMIT =
            Integer.getInteger("celtium.vulkan.groupBoundsCount", 200).intValue();
    /**
     * First frame {@code CELTIUM_GROUP_BOUNDS} reports, so the bounded line budget reaches the frame under test.
     * <p>
     * {@code -Dceltium.vulkan.groupBoundsMinFrame=<n>}, default 0 (no filter). Measured need: a probe with
     * {@code groupBoundsFormat=2,5,6,7,10 groupBoundsCount=400} spent all 400 lines on the loading screen and produced
     * zero world-frame samples, which is the measurement that says the cap alone cannot reach the world. The filter is
     * checked before the cap for the same reason the format filter is.
     */
    private static final long GROUP_BOUNDS_MIN_FRAME =
            Long.getLong("celtium.vulkan.groupBoundsMinFrame", 0L).longValue();
    /**
     * Diagnostic only: the rotations the mirror applied since this frame's camera boundary, oldest first.
     * <p>
     * A group's model-view is the camera base times whatever {@code GlStateManager.rotate} calls the pass made on top
     * of it, so a quad drawn in the wrong place can be a wrong base, a rotation that never arrived, one that arrived
     * twice, or one that landed on the other matrix stack. The base alone cannot tell those apart. This log, printed
     * beside the group's own model-view, is what does - and it is the measurement the sky defect needs, because the
     * only thing between the camera and the sun/moon quads is two rotations that the group's matrix should contain.
     * Bounded, so a frame that rotates a thousand models cannot grow it.
     */
    private static final int ROTATION_LOG_LIMIT = 12;
    private final java.util.ArrayDeque<String> rotationLog = new java.util.ArrayDeque<String>();
    /** The rotations in {@link #rotationLog} at the moment the current group's matrices were captured. */
    private String currentGroupRotations = "";
    /**
     * Diagnostic only: {@code CELTIUM_IMMEDIATE_ENQ} for every group instead of the first eight.
     * <p>
     * The first eight groups belong to the loading screen, so a run could not say which matrices the frame the defect
     * is in was drawn with. Off by default, and {@code -Dceltium.vulkan.enqFormat=<id>} narrows it to one layout.
     */
    private static final boolean ENQ_ALL_GROUPS =
            Boolean.getBoolean("celtium.vulkan.enqAll");
    private static final int ENQ_FORMAT =
            Integer.getInteger("celtium.vulkan.enqFormat", -1).intValue();
    /** Lines of {@code CELTIUM_DRAW_SITE}, the draw-moment companion of {@code CELTIUM_IMMEDIATE_ENQ}. */
    private static final int DRAW_SITE_LIMIT =
            Integer.getInteger("celtium.vulkan.drawSiteLines", 16).intValue();
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
    /**
     * The GLU channel: calls that reached the mirror, calls that could not, and the distinct shapes they installed.
     * <p>
     * {@code gluPerspective} is the one projection step no {@code GlStateManager} hook can carry, so these counters
     * say whether the channel is live at all: a zero with sky draws in the frame means the call never arrived here,
     * and a non-zero one with the identity still at the sky draw means it arrived and did not take. The shapes bound
     * the census line to one line per distinct projection rather than one per frame.
     */
    private int mirroredGluPerspectives;
    private int skippedGluPerspectives;
    private final java.util.Set<String> gluPerspectiveShapes = new java.util.LinkedHashSet<String>();
    private final java.util.Set<String> gluPerspectiveSkips = new java.util.LinkedHashSet<String>();
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
    private final java.util.List<ImmediateGroup> immediateGroups = new java.util.ArrayList<ImmediateGroup>();
    private VulkanDynamicMesh currentImmediateMesh;
    /**
     * The immediate path's vertex and index memory for the frame being recorded (OPT-05).
     * <p>
     * One arena, suballocated per group, instead of two {@code VkBuffer}/{@code VkDeviceMemory}/{@code map} pairs per
     * group (BH-28). Created on the first group of the first frame that needs it, so a run that never draws an
     * immediate batch pays nothing. A group that does not fit even at the arena's cap promotes itself to buffers it
     * owns rather than being dropped, and the arena's own counters say how often that happened.
     */
    private VulkanFrameArena frameArena;
    /** Groups whose arena slice could not be handed out, so they allocated their own buffers: the bounded fallback. */
    private long frameArenaFallbackGroups;
    private final float[] currentGroupProjection = new float[16];
    private final float[] currentGroupModelView = new float[16];
    private boolean currentGroupUsed;
    private int currentGroupFormatId;
    private int[] currentGroupState = new int[7];
    /**
     * The fog state at the moment the group was opened, beside {@code currentGroupState} and
     * {@code currentGroupModelView} and for the same reason.
     * <p>
     * Read at close time instead, it was the state of whatever the renderer had reached by then: {@code
     * RenderGlobal.renderSky} enables fog for the sky dome and disables it again a few statements later
     * ({@code RenderGlobal.java:1337} and {@code :1354}), so a group that was offered under fog and closed after the
     * disable came out with {@code fog == null} and the shaders' fog branch a no-op. Measured on the sky dome: the
     * band kept the dome's own flat colour (120,167,255) in both the fog-on and the fog-off leg, where vanilla ramps
     * to the fog colour (179,207,255) - i.e. the flag made no difference to the picture at all.
     */
    private float[] currentGroupFog;
    /** The clip rectangle captured with the open group, or null when glScissor had not been called. */
    private int[] currentGroupScissor;
    /** Whether the clip was enabled when the open group was created. */
    private boolean currentGroupScissorEnabled;
    /** The colour GL had current when a position-only batch was offered; null for every other format. */
    private float[] currentGroupColor;
    /**
     * The fixed-function lighting payload of the batch that opened this group, captured at acceptance (BH-05).
     * <p>
     * It used to be computed in {@code closeCurrentImmediateGroup}, which reads the light directions, diffuse, ambient
     * and the enabled-light mask from the <em>live</em> immediate state: a draw accepted before a light change was
     * therefore shaded with the light that arrived later, and only the flush timing decided which. The group's own
     * model-view was already saved at acceptance, so the payload is now built from that pair here - the same design the
     * model-mesh path already used.
     */
    private float[] currentGroupLightParams;
    /**
     * The light-map coordinate the group being built was offered with, decided at acceptance rather than at close.
     * <p>
     * The timing is the whole point: {@code endWorldPass()} clears {@code worldPassActive} <em>before</em> it flushes
     * the groups the world pass left pending (the flag at {@code :3650}, the flush at {@code :3665}), so a decision
     * read at close time would classify those world groups as GUI-stage and draw the hand and the entities
     * full-bright. Read here, at the moment the batch was offered, it is the same rule the colour, the lighting
     * payload and the fog already follow.
     */
    private float[] currentGroupLightmapCoords;
    /** {@code -Dceltium.vulkan.alphaTest=false} turns the immediate path's alpha test off, for measurements. */
    private static final boolean ALPHA_TEST_ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.alphaTest", "true"));
    /**
     * {@code -Dceltium.vulkan.positionNormalDepthTest=false} builds the POSITION_NORMAL pipeline with its depth test
     * off, for the one measurement that separates a depth rejection from a lost draw.
     */
    private static final boolean POSITION_NORMAL_DEPTH_TEST =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.positionNormalDepthTest", "true"));
    /**
     * The one-flag controls for BH-07 and BH-10. Each substitutes the value the pipeline baked before the finding was
     * fixed while leaving everything else - the capture, the key, the group - untouched, so the same build can be run
     * twice and the two pictures differ by the state and nothing else. This is the shape a picture claim about blend
     * alpha needs: with the default (correct) factors the RGB result of a draw is identical to the wrong-alpha one, so
     * only a controlled leg can show the difference at all.
     */
    private static final boolean IMMEDIATE_BLEND_ALPHA = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.immediateBlendAlpha", "true"));
    private static final boolean IMMEDIATE_BLEND_EQUATION = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.immediateBlendEquation", "true"));
    private static final boolean IMMEDIATE_COLOR_MASK = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.immediateColorMask", "true"));
    /**
     * {@code -Dceltium.vulkan.groupScissor=false} restores the pre-fix shape of the clip rule for a measurement leg:
     * the rectangle is read from the live stub record when the group is flushed, and a change of it does not close the
     * group. Default on (the fix), so the shipped behaviour is the captured rectangle plus the boundary.
     */
    private static final boolean GROUP_SCISSOR = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.groupScissor", "true"));
    /**
     * {@code -Dceltium.vulkan.alphaFuncOverride=<GL enum>} substitutes the alpha comparison function for a measurement
     * leg (BH-03). Default 0 = off, which is the shipped behaviour: the captured comparison is used. Vanilla asks for
     * GL_GREATER everywhere it calls {@code GlStateManager.alphaFunc}, so this is the only way to put a second
     * comparison in front of the same shader and see whether the selector is honoured.
     */
    private static final int ALPHA_FUNC_OVERRIDE =
            Integer.getInteger("celtium.vulkan.alphaFuncOverride", 0).intValue();
    private int currentGroupTexture;
    private long currentGroupStateKey;
    /** Pipelines per (format, blend, depth, cull) combination; the drawing state decides which one a group needs. */
    private final java.util.Map<Long, VulkanImmediatePipeline> immediatePipelineCache =
            new java.util.HashMap<Long, VulkanImmediatePipeline>();
    /** The same states, but with a line-list topology: the selection outline and other line batches. */
    private final java.util.Map<Long, VulkanImmediatePipeline> linePipelineCache =
            new java.util.HashMap<Long, VulkanImmediatePipeline>();
    /** One position-only pipeline per drawing state: twelve-byte vertices, no colour, no texture. */
    /** One pipeline per state for the entity/TESR layout, whose stride the textured pipeline cannot serve. */
    private final java.util.Map<Long, VulkanImmediateTexturePipeline> positionTexColorNormalPipelineCache =
            new java.util.HashMap<Long, VulkanImmediateTexturePipeline>();
    /** One pipeline per state for the composite batch's layout, likewise wider than the colourless pipeline's. */
    private final java.util.Map<Long, VulkanImmediateTexturePipeline> positionTexNormalPipelineCache =
            new java.util.HashMap<Long, VulkanImmediateTexturePipeline>();
    private final java.util.Map<Long, VulkanImmediateTexturePipeline> positionOnlyPipelineCache =
            new java.util.HashMap<Long, VulkanImmediateTexturePipeline>();
    /**
     * One pipeline per state for {@code POSITION_NORMAL}: sixteen-byte vertices.
     * <p>
     * It cannot share the position-only cache: the two differ in their vertex binding's stride, which is baked into the
     * pipeline, and 16 is not a width any other layout has. Its one user is the white box {@code Render.renderOffsetAABB}
     * draws for a renderer-less entity, so an empty cache here means that box is not drawn.
     */
    private final java.util.Map<Long, VulkanImmediateTexturePipeline> positionNormalPipelineCache =
            new java.util.HashMap<Long, VulkanImmediateTexturePipeline>();
    private final java.util.Map<Long, VulkanImmediateTexturePipeline> positionTexPipelineCache =
            new java.util.HashMap<Long, VulkanImmediateTexturePipeline>();
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
    /** Additive mip bias; 0.0 is GL parity. Swept to separate the chain's content from the shader's LOD. */
    private static final float MIP_BIAS_ADD =
            Float.parseFloat(System.getProperty("celtium.vulkan.mipBiasAdd", "0.0"));

    private static final String ACCEPTED_FORMATS = System.getProperty("celtium.vulkan.immediateFormats", VulkanImmediateFormats.DEFAULT_ACCEPTED);

    /**
     * {@return one counter array as {@code [1:n,2:n,...]}, for every id the array covers}.
     * <p>
     * One reader for both per-format rows in the summary, and it walks the array's own length rather than a list
     * written by hand: the hand-written list stopped at 9 while the arrays were sized from
     * {@link VulkanImmediateFormats#COUNT}, so POSITION_NORMAL's arrivals and drops were counted and then not printed.
     * That is the same shape as the counters that used to stop at 6, 7 and 8 and hide the ids above them.
     */
    private static String celtium$perFormat(int[] counts) {
        StringBuilder text = new StringBuilder();
        for (int id = 1; id < counts.length; id++) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(id).append(':').append(counts[id]);
        }
        return text.toString();
    }

    private static boolean celtium$formatAccepted(int formatId) {
        // Comma-separated ids, not digit-by-digit: the matcher walked the string's characters and compared each to the
        // format id, so an id of 10 could never be accepted - the mask would have to be the single character ':' or a
        // duplicate 1, and the first version of this line silently admitted every format-1 batch instead. Reading the
        // entries as numbers is what lets the mask grow past nine ids, which is where the layouts are now.
        String accepted = ACCEPTED_FORMATS;
        int start = 0;
        while (start <= accepted.length()) {
            int comma = accepted.indexOf(',', start);
            String entry = comma < 0 ? accepted.substring(start) : accepted.substring(start, comma);
            try {
                if (Integer.parseInt(entry.trim()) == formatId) {
                    return true;
                }
            } catch (NumberFormatException notAnId) {
                // An unreadable entry admits nothing; the ids that did parse still apply.
            }
            if (comma < 0) {
                break;
            }
            start = comma + 1;
        }
        return false;
    }

    /**
     * {@return whether the layout's pipeline declares no texture coordinate at all}, so a batch that cannot sample is
     * not refused for a texture it does not read.
     * <p>
     * Two layouts are in this class: {@code POSITION} (7), the sky dome, the stars and the horizon plane, and
     * {@code POSITION_NORMAL} (10), the white box {@code Render.renderOffsetAABB} draws for a renderer-less entity.
     * Their shaders declare no sampler and their pipelines no texture attribute, so the texture that happens to be
     * bound when they arrive is irrelevant to the picture - and the guards below use that: a missing binding or a
     * mirror that cannot be built is not a reason to refuse them, and "textured batch with no texture bound" was
     * simply false for them. Both are drawn with the 1x1 white stand-in instead, the same substitution the
     * texturing-disabled case already makes.
     */
    private static boolean celtium$layoutHasNoTexture(int formatId) {
        return formatId == VulkanImmediateFormats.POSITION
                || formatId == VulkanImmediateFormats.POSITION_NORMAL;
    }

    /**
     * On by default: the window draws each batch in the frame that produced it, so the immediate path does not lag a
     * frame, and one window per frame is what keeps the frame intact - unlimited windows were measured as a black
     * frame, one per frame measures pixel-identical to GL on both GPUs.
     */
    private static final boolean IMMEDIATE_WINDOWS_ENABLED =
            Boolean.parseBoolean(System.getProperty("celtium.vulkan.immediateWindows", "true"));
    /** Off by default: present the frame from the composite batch instead of letting GL blit it. */
    /**
     * Whether the frame's final composite is the present path's job rather than an immediate batch.
     * <p>
     * Default on. Measured: AMD 18.19/18.81 -> 15.89 ms, Intel 42.66 -> 30.86 ms, and the screen-sized (1280x720)
     * mirror readback drops from 6.2 to 0.3 ms/frame - the frame's colour is then owned by the present path instead of
     * being read back out of OpenGL. The image differs by under one 8-bit step in 0.12% of pixels (mean absolute
     * difference 0.0155, max 7/255, zero in the sky): the composite it replaces is an opaque full-screen copy
     * (col=0xffffffff), so this is rounding, not a skipped blend or a missing element.
     * <p>
     * It could only become the default after the command-buffer-target acceptance was moved off
     * {@code POSITION_TEX_COLOR}: that fixture checks which render target a batch belongs to and in what order the
     * groups are flushed, and format 2 is exactly the composite this changes. It now draws {@code POSITION_COLOR} and
     * passes with this on and off, so the property stays covered.
     */
    /**
     * Lab switch: let the frame's own GL composite be skipped once Vulkan has put the frame on the swapchain.
     * <p>
     * Off by default because the game's screenshot reads OpenGL's default framebuffer: with this on, that framebuffer keeps
     * whatever the last GL frame left there. The evidence for the frame becomes the presented image instead
     * ({@code -Dceltium.validation.dumpPresentedImage=true}), which is why this could only be attempted after that path
     * existed. The condition is a recorded fact - a frame really was presented from Vulkan - not a name or a counter.
     */
    private static final boolean SKIP_GL_COMPOSITE =
            Boolean.getBoolean("celtium.vulkan.skipGlComposite");
    private static final boolean PRESENT_FROM_COMPOSITE =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.presentFromComposite", "true"));
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
    /**
     * A real frame identity (audit S11).
     * <p>
     * The per-frame window budget used to be reset by {@code updateTimings}, which is called on every fence wait - so a
     * frame with three flushes had its budget reset three times and "sixteen windows per frame" could not be demonstrated
     * at all. The identity is incremented at the camera boundary, which fires exactly once per frame, and the counters are
     * reconciled there rather than on a flush.
     */
    private long frameId;
    private long countersFrameId = -1;
    private long flushCount;
    private long retiredFlushes;
    private int lastReportedOutOfDate = -1;
    private int lastReportedAcquireOutOfDate = -1;
    private int lastReportedDirectRefreshes = -1;
    private int orderPreservingFlushes;
    /** Handoffs that actually carried geometry (the counters above count calls, many of which return early). */
    /**
     * Wall time of the game's batch call, measured by the caller (the mixin) around {@code submitImmediateBatch}.
     * <p>
     * The per-frame budget after the terrain passes (2.2 ms) and the handoffs (2.3 ms) left ~38.5 ms unaccounted for,
     * and the {@code immediate=false} A/B says it belongs to the immediate path. This is the instrument for the part of
     * that path the game calls.
     */
    public static long batchCallNanos;
    public static int batchCallCount;
    /**
     * The single most expensive batch call, and the total of the first 200.
     * <p>
     * The per-frame mean of this counter is 2.5x higher on Intel than on AMD even though the work is the same CPU-side
     * code and the call counts per frame match, which points at the mean being inflated by a few very slow calls - the
     * JIT compiling the path on a GPU that produces half as many frames. The maximum and the opening window separate
     * that from a real per-call difference.
     */
    public static long batchCallWorstNanos;
    /**
     * The batch call measured as CPU time instead of wall time.
     * <p>
     * Every part measured inside the call adds up to ~1.1 ms/frame (AMD) and ~3.2 ms/frame (Intel), while the wall time
     * around the same call is 3.8 / 10.2 ms/frame. If the CPU time matches the parts, the difference is time the thread
     * spent descheduled - contention with the server thread, the upload thread or the driver - and not work this call
     * does itself.
     */
    public static long batchCallCpuNanos;
    /**
     * The thread MXBean, looked up once.
     * <p>
     * OPT-01: this used to be `ManagementFactory.getThreadMXBean()` on every call - twice per immediate batch for the
     * preflight split, plus once per group and once per frame - and the lookup is a synchronised platform-MBean
     * resolution, not a field read. The instrument's own cost is part of what a performance run measures, so the bean
     * and its support flag are resolved once here.
     */
    private static final java.lang.management.ThreadMXBean THREAD_MX_BEAN =
            java.lang.management.ManagementFactory.getThreadMXBean();

    /** Whether the JVM exposes per-thread CPU time here (it decides whether the split above means anything). */
    public static final boolean THREAD_CPU_SUPPORTED = THREAD_MX_BEAN.isCurrentThreadCpuTimeSupported();
    /**
     * How the render thread spent its non-running time, split by kind.
     * <p>
     * The batch call's wall time exceeds its CPU time by 2.0 ms/frame on AMD and 7.8 ms/frame on Intel. If these two
     * are near zero while that gap is large, the waiting is not Java-level contention but a native call blocking - the
     * bridge's synchronisation - which is a different fix.
     */
    private static long threadWaitedMillis() {
        if (!THREAD_CPU_SUPPORTED) {
            return -1L;
        }
        java.lang.management.ThreadInfo info = THREAD_MX_BEAN.getThreadInfo(Thread.currentThread().getId());
        return info == null ? -1L : info.getWaitedTime();
    }

    private static long threadBlockedMillis() {
        if (!THREAD_CPU_SUPPORTED) {
            return -1L;
        }
        java.lang.management.ThreadInfo info = THREAD_MX_BEAN.getThreadInfo(Thread.currentThread().getId());
        return info == null ? -1L : info.getBlockedTime();
    }

    /** The thread's CPU time right now, or -1 when unsupported. */
    /**
     * Whether the per-batch CPU split is measured at all.
     * <p>
     * OPT-01: the split costs two {@code getCurrentThreadCpuTime()} calls per immediate batch (preflight, group
     * creation, append and flush each take a start and an end), and it is instrumentation, not rendering. It is off by
     * default so a performance run measures the renderer rather than the meter; a run that wants the split turns it on
     * with {@code -Dceltium.vulkan.cpuSplit=true} and reports it as an instrumented run, which is the distinction
     * OPT-01's acceptance asks for.
     */
    public static final boolean CPU_SPLIT = Boolean.parseBoolean(
            System.getProperty("celtium.vulkan.cpuSplit", "false"));

    public static long threadCpuNanos() {
        if (!CPU_SPLIT) {
            return -1L;
        }
        return THREAD_CPU_SUPPORTED ? THREAD_MX_BEAN.getCurrentThreadCpuTime() : -1L;
    }
    public static long batchCallOpeningNanos;
    public static int batchCallOpeningCount;

    private int actualFlushes;
    private long actualFlushNanos;
    private long actualFlushWorstNanos;
    /** CPU time of the same handoffs, so wall minus CPU says how much of it is blocking in a native call. */
    private long actualFlushCpuNanos;
    private int clearOrderFlushes;
    private int compositePresentRefusals;
    private int frameAccepted;
    private int frameDrawn;
    private int lastFrameAccepted;
    private int lastFrameDrawn;
    private int immediateDeferredNextFrame;
    private int whiteTextureGlId;
    private int immediateRefusedDefaultFbo;
    private int immediatePresentedFrames;
    private int immediateTextureInvalidations;
    private int directMirrorRefreshes;
    private int atlasSourceGlId = -1;
    private int lightmapSourceGlId = -1;
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
    /**
     * How often every mirror is marked stale, in frames. Zero means never.
     * <p>
     * Now zero. The periodic invalidation existed because some textures - the light map above all - were updated through
     * paths the upload hooks could not reach, so the mirror had to be re-read from OpenGL to stay current. Those paths
     * are announced now (the DynamicTexture hook and the int[] TextureLoader hook both hand the pixels over), so the
     * only thing the periodic refresh still did was force a GL readback of the 16x16 light map on the frames where it
     * had not changed: measured 2.30 ms/frame on Intel and 0.31 on AMD, all of it native blocking, because
     * glGetTexImage waits for the GPU rather than for the bytes.
     * <p>
     * Measured with it off: Intel p50 31.18 -> 28.66/28.89 ms, AMD 16.05 -> 16.12 ms, stale 0.00 ms/frame on both, and
     * the image is identical to the default (mean absolute difference 0.0000, 5-7 pixels above one step on both GPUs).
     * The 0.0169 seen between two runs of this setting is the path-dependent batch variation measured separately, not a
     * staleness error.
     */
    private static final int MIRROR_REFRESH_FRAMES =
            Integer.getInteger("celtium.vulkan.mirrorRefreshFrames", 0).intValue();
    private int mirrorRefreshCounter;
    private int mirrorRefreshes;
    /**
     * Offered and accepted batch counts per format id.
     * <p>
     * Added to answer a specific question rather than to add telemetry: the GUI-visible difference is in the first-person
     * hand, and the first thing to know is which formats its batches carry and whether they are served by Vulkan at all.
     * Reported once, at a count a run actually crosses.
     */
    // Sized from the format table, not from the eight ids that existed when this was written: an array of 8 silently
    // truncated format 8 and 9 out of the per-format line, so batches that were offered and accepted (id8=128,
    // id9=228 in one run) never appeared in the table the evidence is read from - the histogram and the accepted
    // counters disagreed with each other and neither was wrong about what it had seen.
    private final int[] immediateOfferedByFormat = new int[VulkanImmediateFormats.COUNT];
    private final int[] immediateAcceptedByFormat = new int[VulkanImmediateFormats.COUNT];
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
    private int emptyFrameReleases;

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
    /**
     * Batches drawn, by format id - the counter that pairs with {@code acceptedByFormat}.
     * <p>
     * {@code immediateGroupsDrawn} says how many groups reached a pipeline in total; it cannot say which layout they
     * were, and a group is not the unit {@code acceptedByFormat} counts. The pair is read as
     * {@code acceptedByFormat[10]} against {@code drawnByFormat[10]}: measured on the full mask, 103 accepted against
     * 65 drawn, so the loss is real and per-batch rather than inferred from a group total.
     */
    private final int[] drawnByFormat = new int[VulkanImmediateFormats.COUNT];
    /**
     * Where the draw loop was entered from, for the bounded {@code CELTIUM_DRAW_SITE} record.
     * <p>
     * The queue counters say a group was drawn; they do not say into which pass. The three entries are the only ones
     * that exist - the ordering flush inside an open pass, an immediate-only window of its own, and the tail of a
     * terrain pass - and they differ in what the depth attachment holds when the group is tested against it, which is
     * the difference that decides whether a group drawn "successfully" can still leave no pixels.
     */
    private String immediateDrawSite = "none";
    /** Lines of {@code CELTIUM_DRAW_SITE} printed; bounded by {@code celtium.vulkan.enqAll} + {@code enqFormat}. */
    private int immediateDrawSiteLogged;
    /** Groups left in the queue because they belong to another render target (audit A32). */
    private int immediateGroupsKeptForOtherTargets;
    private int immediateGroupsOffTargetRetired;
    /** Off-target passes not begun because their target had no pending geometry. */
    private int offTargetPassesSkippedEmpty;
    /** Groups enqueued with a target other than the main one (audit A10). */
    private int groupsEnqueuedForOtherTargets;
    /** Batches accepted while a framebuffer other than the main one was bound (audit A10). */
    private int acceptedForOtherTargets;
    /** Flushes refused because no command buffer was recording (A11 exposes this path). */
    private int drawsWithoutCommandBuffer;
    private int flushWithoutBufferStacks;
    /** Refusal reasons seen for batches offered while a non-main framebuffer was bound (audit A10). */
    private final java.util.Map<String, Integer> offTargetRefusalsByReason =
            new java.util.LinkedHashMap<String, Integer>();
    /** Pending groups destined for a target that is not the main one, and where they were seen to disappear. */
    private int otherTargetGroupsAtMainFlush;
    private int otherTargetGroupsDiscarded;
    private int otherTargetGroupsFlushedByWindow;
    private int offTargetOfferDiagnostics;
    /** Groups seen at pass time and drawing passes, per target; the offers come from offTargetBatchesByTarget. */
    private final java.util.Map<Long, Integer> offTargetPassGroupsByTarget = new java.util.HashMap<Long, Integer>();
    private final java.util.Map<Long, Integer> offTargetDrawnByTarget = new java.util.HashMap<Long, Integer>();
    private int immediateFlushNoPipeline;
    private String immediateLastFlushError = "none";
    /**
     * Groups whose draws are already recorded but whose command buffer may still be in flight.
     * <p>
     * Destroying the mesh buffers immediately after recording them is a use-after-free as far as the GPU is
     * concerned: the handoff submits later in the same pass, so the buffers must outlive it. Keeping one frame's
     * worth and releasing it at the next flush is enough, because a pass only starts after the previous one signalled.
     */
    private final java.util.List<ImmediateGroup> retiredImmediateGroups = new java.util.ArrayList<ImmediateGroup>();
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

    /** BH-07: the blend equation, on the mirror the capture reads when the stub is not installed (the bridge). */
    @Override
    public void blendEquation(int rgbEquation, int alphaEquation) {
        this.immediateState.blendEquation(rgbEquation, alphaEquation);
        this.immediateStateCalls++;
    }

    /** BH-10: the colour write mask, on the same mirror and for the same reason as the equation above. */
    @Override
    public void colorMask(int mask) {
        this.immediateState.colorMask(mask);
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

    /**
     * The alpha test, which had no implementation at all.
     * <p>
     * The interface declared both halves as no-ops and the hook that feeds the comparison was named for
     * {@code alphaFunc}, so the mirrored state kept {@code alphaTestEnabled=false} and a reference of 0 for every
     * batch: {@code celtium$captureState} read {@code ALPHA_TEST_ENABLED && alphaTestEnabled() ? reference : 0.0f},
     * which is 0 whatever the game asked for. Position-only and texture-family cutout batches are the ones that read
     * it, so this is the state half of the same defect as the mis-named hooks in {@code GlStateManagerMixin}.
     */
    @Override
    public void setAlphaFunc(int glFunc, float reference) {
        this.immediateState.alphaFunc(glFunc, reference);
        this.immediateStateCalls++;
    }

    @Override
    public void setAlphaTestEnabled(boolean enabled) {
        this.immediateState.alphaTestEnabled(enabled);
        this.immediateStateCalls++;
    }

    @Override
    public void setLightingEnabled(boolean enabled) {
        this.immediateState.lightingEnabled(enabled);
        this.immediateStateCalls++;
    }

    /**
     * {@code EntityRenderer.enableLightmap()} / {@code disableLightmap()}: the light-map texture unit was enabled or
     * disabled. This is the state the full-bright rule keys on (see {@code celtium$lightmapForGroup()}), because it is
     * the state real OpenGL modulates by - the unit is on for the entity pass, the hand and the block entities, and off
     * for the whole GUI stage.
     */
    @Override
    public void setLightmapUnitEnabled(boolean enabled) {
        this.lightmapUnitEnabled = enabled;
        if (enabled) {
            this.lightmapUnitEnables++;
        } else {
            this.lightmapUnitDisables++;
        }
    }

    @Override
    public void setLightEnabled(int index, boolean enabled) {
        this.immediateState.lightEnabled(index, enabled);
        this.immediateStateCalls++;
    }

    @Override
    public void setLightPosition(int index, float x, float y, float z) {
        this.immediateState.setLightPosition(index, x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void setLightDiffuse(int index, float red, float green, float blue) {
        this.immediateState.setLightDiffuse(index, red, green, blue);
        this.immediateStateCalls++;
    }

    @Override
    public void setLightAmbient(float red, float green, float blue) {
        this.immediateState.setLightAmbient(red, green, blue);
        this.immediateStateCalls++;
    }

    @Override
    public void setShadeModel(int glMode) {
        // GL_FLAT and GL_SMOOTH are the two the mirror distinguishes; the shader path treats per-quad normals as flat.
        this.immediateState.shadeModel(glMode == 7424 ? VulkanGlState.SHADE_FLAT : VulkanGlState.SHADE_SMOOTH);
        this.immediateStateCalls++;
    }

    // ----------------------------------------------------------------------------------------------
    // Fixed-function fog, mirrored from GlStateManager by GlStateManagerFogMixin.
    //
    // The immediate path had no fog of any kind, so every batch vanilla blends into the horizon - the
    // sky dome, the horizon plane, the water surface, the clouds - arrived as its own flat colour: the
    // sky dome is two flat grids and the fog ramp across them is the whole of its gradient. The state
    // is captured per batch (celtium$fogSnapshot, taken where the other per-group state is taken)
    // rather than read at flush time, because RenderGlobal.renderSky turns fog on for the dome and off
    // again for the sun and the moon inside the same pass.
    //
    // The values travel in a push-constant block appended after the existing 128 bytes:
    //   128 vec4 u_FogColor (rgb, mix weight)   144 vec4 u_FogParams (density, start, end, selector)
    //   160/176/192 vec4 the model view's three affine rows plus its translation
    // so the fog coordinate is the true eye-space distance for camera-space geometry (the sky) and for
    // object-space geometry (entities, TESRs) alike. The block ends at 208, the size the lit ITEM
    // pipeline already requires of the device, so nothing here asks the device for more than the tree
    // already did.
    // ----------------------------------------------------------------------------------------------

    /**
     * The kill switch, so the fog can be measured as one flag apart on one jar: with this false the family is mirrored
     * and the push block is still written, but its selector says "no fog" - exactly the picture the path produced
     * before the family existed.
     */
    private static final boolean IMMEDIATE_FOG =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.immediateFog", "true"));
    /**
     * Which distance the fog coordinate uses: {@code radial} (the distance from the eye), {@code planar} (the
     * eye-space depth) or {@code auto} (whichever the game asked for with {@code GL_NV_fog_distance}).
     * <p>
     * The default is radial rather than auto because the GL-less leg cannot answer the extension query - the stub
     * returns an empty extension string, so {@code setupFog} never sets {@code GL_EYE_RADIAL_NV} there while the same
     * game code does on a driver that has the extension. Radial is also what the terrain path already uses for its own
     * fog (sodium's {@code getFragDistance} is the camera distance) and that path is accepted against the GL bridge.
     * The run's {@code CELTIUM_IMMEDIATE_FOG} line records which mode was used and what the game asked for.
     */
    private static final String IMMEDIATE_FOG_DISTANCE =
            System.getProperty("celtium.vulkan.immediateFogDistance", "radial").trim().toLowerCase();
    /** Bounded, like the other per-run diagnostics: the fog state changes a handful of times per frame. */
    private static final int IMMEDIATE_FOG_LOG_LIMIT =
            Integer.getInteger("celtium.vulkan.immediateFogLog", 8).intValue();

    private boolean fogEnabled;
    /** The GL fog mode: 9729 LINEAR, 2048 EXP, 2049 EXP2 - the values {@code GlStateManager.FogState} carries. */
    private int fogGlMode = 2048;
    private float fogDensity = 1.0f;
    private float fogStart;
    private float fogEnd = 1.0f;
    private float fogRed;
    private float fogGreen;
    private float fogBlue;
    /** Set when the game asks for {@code GL_EYE_RADIAL_NV}; the GL-less stub's empty extension list keeps it false. */
    private boolean fogRadialRequested;
    /**
     * Set the first time any fog state arrives from {@code GlStateManager}. This is what {@link #immediateFogAvailable()}
     * answers with: a mixin that did not apply leaves it false, and a caller that would refuse a fogged draw on the
     * grounds that the path has no fog must keep refusing in that case rather than draw the raw colour.
     */
    private boolean fogMirrored;
    /** Groups drawn with fog on: the number the fog line reports beside the state. */
    private int fogGroups;
    private int fogLogs;
    /** The last line this diagnostic printed, so a change is logged rather than every frame's repeat. */
    private String fogLastLogged = "";

    @Override
    public void setFogEnabled(boolean enabled) {
        this.fogEnabled = enabled;
        this.fogMirrored = true;
        this.immediateStateCalls++;
        celtium$logFogState("enable=" + enabled);
    }

    @Override
    public void setFogMode(int glMode) {
        this.fogGlMode = glMode;
        this.fogMirrored = true;
        this.immediateStateCalls++;
        celtium$logFogState("mode=" + glMode);
    }

    @Override
    public void setFogDensity(float density) {
        this.fogDensity = density;
        this.fogMirrored = true;
        this.immediateStateCalls++;
    }

    @Override
    public void setFogStart(float start) {
        this.fogStart = start;
        this.fogMirrored = true;
        this.immediateStateCalls++;
        celtium$logFogState("start=" + start);
    }

    @Override
    public void setFogEnd(float end) {
        this.fogEnd = end;
        this.fogMirrored = true;
        this.immediateStateCalls++;
        celtium$logFogState("end=" + end);
    }

    @Override
    public void setFogColor(float red, float green, float blue) {
        this.fogRed = red;
        this.fogGreen = green;
        this.fogBlue = blue;
        this.fogMirrored = true;
        this.immediateStateCalls++;
        celtium$logFogState("colour=" + red + "," + green + "," + blue);
    }

    @Override
    public void setFogRadial(boolean radial) {
        this.fogRadialRequested = radial;
        this.fogMirrored = true;
        this.immediateStateCalls++;
        celtium$logFogState("radial=" + radial);
    }

    /**
     * {@inheritDoc}
     * <p>
     * "Available" means the game's own fog state has reached this backend at least once ({@code fogMirrored}), not that
     * the feature is configured on. The difference matters to the caller: the GL-less stub's sky gate refuses a fogged
     * sky draw because the immediate path used to have no fog, and it may only stop refusing once the state the shaders
     * would use has really arrived. A mixin whose target names differed applies nothing and this stays false, so the
     * gate keeps refusing instead of letting the batch through with the field defaults.
     */
    @Override
    public boolean immediateFogAvailable() {
        return IMMEDIATE_FOG && this.fogMirrored;
    }

    /**
     * The shader's fog selector: {@code mode + 8 * shape}, both small integers so the float is exact.
     * <p>
     * The mode numbering is the terrain block's ({@code 0 none, 1 exp2, 2 exp, 3 linear}) so the two fog paths in this
     * renderer can be read side by side, and the shape numbering is {@code FogService}'s ({@code 0 spherical}, the
     * radial distance, and {@code 2 planar}). {@code 1} (cylindrical) is never produced here: the fixed pipeline has no
     * such mode, it is sodium's.
     */
    private int celtium$fogSelector() {
        if (!IMMEDIATE_FOG || !this.fogEnabled) {
            return 0;
        }
        int mode;
        if (this.fogGlMode == 9729) {
            mode = 3; // GL_LINEAR
        } else if (this.fogGlMode == 2049) {
            mode = 1; // GL_EXP2
        } else {
            mode = 2; // GL_EXP, which is also GlStateManager.FogState's default
        }
        boolean planar;
        if ("planar".equals(IMMEDIATE_FOG_DISTANCE)) {
            planar = true;
        } else if ("auto".equals(IMMEDIATE_FOG_DISTANCE)) {
            planar = !this.fogRadialRequested;
        } else {
            planar = false; // radial
        }
        return mode + (planar ? 16 : 0); // shape 2 = planar, and 2 << 3 = 16
    }

    /**
     * The per-batch fog payload: eight floats {@code {selector, density, start, end, r, g, b, weight}}, or {@code null}
     * when fog is off for this batch.
     * <p>
     * Taken where the other per-group state is taken, so the group is drawn with the fog the game had set when the batch
     * was offered - which is the whole point, since {@code renderSky} brackets the dome with enable/disable.
     */
    private float[] celtium$fogSnapshot() {
        int selector = celtium$fogSelector();
        if (selector == 0) {
            return null;
        }
        return new float[] {
                selector, this.fogDensity, this.fogStart, this.fogEnd,
                this.fogRed, this.fogGreen, this.fogBlue,
                // The GL fog blend uses the fog colour's RGB only. The weight is 1.0 in vanilla and is kept as its own
                // field so a mod's non-opaque fog colour cannot silently change the blend here.
                1.0f,
        };
    }

    /**
     * Records that a group's fog was captured at offer time while the live state at close time says something else.
     * <p>
     * This is the instrument for the defect the sky band measured: the snapshot used to be taken at close time, and
     * {@code RenderGlobal.renderSky} disables the fog a few statements after the dome draw, so a fogged group closed
     * later carried no fog and the picture was flat in both legs. The counter is bounded and named
     * {@code CELTIUM_FOG_CAPTURE} in the report; a run whose captured and live selectors agree prints nothing.
     */
    private static final int FOG_CAPTURE_LOG_LIMIT =
            Integer.getInteger("celtium.vulkan.fogCaptureLog", 4).intValue();
    private int fogCaptureDriftLogged;

    private void celtium$noteFogCaptureDrift(ImmediateGroup group) {
        if (this.fogCaptureDriftLogged >= FOG_CAPTURE_LOG_LIMIT) {
            return;
        }
        int captured = group.fog == null ? 0 : (int) group.fog[0];
        int live = celtium$fogSelector();
        boolean shadowed = group.fog != null && group.lightParams != null;
        if (captured == live && !shadowed) {
            return;
        }
        this.fogCaptureDriftLogged++;
        System.out.println("[Celtium] CELTIUM_FOG_CAPTURE frame=" + this.frameId + " fmt=" + group.formatId
                + " batches=" + group.batches + " capturedSelector=" + captured + " liveSelector=" + live
                + " lightParams=" + (group.lightParams != null)
                + " fogEnabledNow=" + this.fogEnabled + " fogMirrored=" + this.fogMirrored
                + " reason=[" + (shadowed
                        ? "the group carries the lit payload at 128..207 as well as a fog snapshot, and the lit payload"
                        + " is written last, so this group's fog block is shadowed"
                        : "the group was offered under one fog state and closed under another; the offered one is what"
                        + " the batch was drawn with") + "]");
    }

    /**
     * Writes the fog block (128..207) for one group into the shared push buffer.
     * <p>
     * The block layout, matching the fragment shaders' declaration: {@code u_FogColor} at 128 (rgb plus the blend
     * weight), {@code u_FogParams} at 144 (density, start, end, selector), and the model view's three affine rows at
     * 160/176/192 - each row's fourth component is that row's translation, so {@code eye = M * position} is three dot
     * products in the vertex shader. The selector is written even when the group has no fog, because the push buffer is
     * shared by every group in a flush and a stale selector from the previous group would fog this draw.
     */
    private void celtium$writeFogPush(ImmediateGroup group, ByteBuffer push) {
        float[] fog = group.fog;
        if (fog == null) {
            push.putFloat(144 + 12, 0.0f); // u_FogParams.w: 0 = mode NONE
            return;
        }
        float[] mv = group.modelView;
        if (mv == null || mv.length < 16) {
            push.putFloat(144 + 12, 0.0f);
            return;
        }
        push.putFloat(128, fog[4]);
        push.putFloat(132, fog[5]);
        push.putFloat(136, fog[6]);
        push.putFloat(140, fog[7]);
        push.putFloat(144, fog[1]);
        push.putFloat(148, fog[2]);
        push.putFloat(152, fog[3]);
        push.putFloat(156, fog[0]);
        // Row 0 of the affine model view plus its translation: [m00 m01 m02 tx].
        push.putFloat(160, mv[0]);
        push.putFloat(164, mv[4]);
        push.putFloat(168, mv[8]);
        push.putFloat(172, mv[12]);
        push.putFloat(176, mv[1]);
        push.putFloat(180, mv[5]);
        push.putFloat(184, mv[9]);
        push.putFloat(188, mv[13]);
        push.putFloat(192, mv[2]);
        push.putFloat(196, mv[6]);
        push.putFloat(200, mv[10]);
        push.putFloat(204, mv[14]);
        this.fogGroups++;
    }

    /** A bounded record of what the game set, so a run with no fog in it can be told from a run with black fog. */
    private void celtium$logFogState(String what) {
        if (!IMMEDIATE_FOG || this.fogLogs >= IMMEDIATE_FOG_LOG_LIMIT) {
            return;
        }
        String line = what + " enabled=" + this.fogEnabled + " glMode=" + this.fogGlMode
                + " density=" + this.fogDensity + " start=" + this.fogStart + " end=" + this.fogEnd
                + " colour=" + this.fogRed + "," + this.fogGreen + "," + this.fogBlue
                + " radialRequested=" + this.fogRadialRequested + " distance=" + IMMEDIATE_FOG_DISTANCE
                + " selector=" + celtium$fogSelector();
        if (line.equals(this.fogLastLogged)) {
            return;
        }
        this.fogLastLogged = line;
        this.fogLogs++;
        System.out.println("[Celtium] CELTIUM_IMMEDIATE_FOG frame=" + this.frameId + " " + line);
    }

    /**
     * Announces a dynamic texture's write, which arrives as a flat pixel array.
     * <p>
     * The light map's write does not pass through either hooked texture utility method, so this is the only announcement it ever
     * makes. The id and the size are asked of GL because the method that performs the write takes neither.
     */
    /**
     * Announces a whole-texture {@code int[]} upload, the {@code TextureUtil.uploadTexture} / DynamicTexture path.
     * <p>
     * The id comes from the caller, not from the bound texture: these upload methods carry the texture they write as a
     * parameter, and the bound texture at hook time is whatever the frame had bound before - which is where the light
     * map's pixels were being aimed (at a 1x1 placeholder), leaving its mirror to be read back on every update.
     */
    public void announceDynamicTexture(int glTextureId, int[] pixels, int width, int height) {
        if (pixels == null || glTextureId == 0 || width <= 0 || height <= 0 || pixels.length < width * height) {
            return;
        }
        if (this.textureRegistry == null) {
            this.textureRegistry = celtium$registry();
        }
        if (!this.immediateWindowBusy && queuedGroupUsesTexture(glTextureId)) flushPendingBeforeGlDraw();
        // One int[] holding the whole of level 0, which is the shape announceFullPixels documents: its data[0] is
        // the entire base level, not its first row. Splitting the flat array into one int[] per row made data[0] a
        // single row, so every texel past the first row fell outside the array the consumer reads and was written as
        // transparent - measured as a light map whose row 0 held the noon gradient and whose rows 1..15 were black,
        // which is the black terrain this replaces.
        this.announcedDynamicTextures++;
        if (this.textureRegistry.announceFullPixels(glTextureId, new int[][] { pixels }, width, height, 0, 0)) {
            this.animatedImageFills++;
            this.dynamicTextureFills++;
        }
    }

    /**
     * As {@link #announceDynamicTexture(int, int[], int, int)}, with the size asked of the texture itself.
     * The DynamicTexture mixin has the object and its id but not the size - so the size is read from the
     * texture directly rather than from whatever is bound.
     */
    public void announceDynamicTexture(int glTextureId, int[] pixels) {
        if (pixels == null || glTextureId == 0) {
            return;
        }
        int width = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(glTextureId, 0,
                org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
        int height = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(glTextureId, 0,
                org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) {
            return;
        }
        announceDynamicTexture(glTextureId, pixels, width, height);
        if (this.dynamicAnnounceDiagnostics < 3) {
            this.dynamicAnnounceDiagnostics++;
            System.out.println("[Celtium] CELTIUM_DYNAMIC_ANNOUNCE texture=" + glTextureId + " size=" + width + "x" + height);
        }
    }

    /**
     * {@return whether a queued group still has to draw a batch that samples this GL texture}.
     * <p>
     * A texture mutation only threatens queued work when a queued group actually references the texture: the group holds the
     * descriptor prepared for it, so replacing the image behind it would swap what those batches sample. When no group
     * references it, flushing would open a window - four full-screen blits and two depth conversions - to protect nothing.
     */
    /** {@return whether a queued batch for this target samples the published outline image}. */
    private boolean celtium$queuedForTargetUsesPublished(long target) {
        if (this.publishedChainOutput == null || this.publishedChainTexture == 0) {
            return false;
        }
        for (ImmediateGroup group : this.immediateGroups) {
            if (group.target == target && group.texture == this.publishedChainTexture) {
                return true;
            }
        }
        return false;
    }

    private boolean queuedGroupUsesTexture(int glTextureId) {
        for (ImmediateGroup group : this.immediateGroups) {
            if (group.texture == glTextureId) {
                return true;
            }
        }
        return false;
    }

    /** Called by the upload hook with the image the game is uploading, so the mirror need not read GL back. */
    public void announceTextureUpload(int glTextureId, java.awt.image.BufferedImage image) {
        if (!this.immediateWindowBusy && queuedGroupUsesTexture(glTextureId)) flushPendingBeforeGlDraw();
        if (this.textureRegistry == null) {
            this.textureRegistry = celtium$registry();
        }
        this.textureRegistry.announceImage(glTextureId, image);
        this.announcedUploads++;
    }

    /**
     * Announces a whole-texture write made through the animation path, which writes into whatever texture is bound.
     * <p>
     * The id has to be asked of GL because the method that performs the write takes no id - it is the one place in this
     * renderer where a GL query buys something a hook cannot supply.
     */
    public void announceAnimatedUpload(int[][] data, int width, int height, int x, int y) {
        int bound = this.immediateState.boundTextureName();
        compareBoundTextureWithGl(bound);
        if (!this.immediateWindowBusy && queuedGroupUsesTexture(bound)) flushPendingBeforeGlDraw();
        if (this.textureRegistry == null) {
            this.textureRegistry = celtium$registry();
        }
        // Which id the animation announces, and which id the light map has: if those differ, the light map's writes are being
        // announced against something else and its refreshes will keep reading back.
        if (this.animatedAnnounceDiagnostics < 5) {
            this.animatedAnnounceDiagnostics++;
            System.out.println("[Celtium] CELTIUM_ANIM_ANNOUNCE bound=" + bound + " region=" + width + "x" + height
                    + " at " + x + "," + y + " lightmapGlId=" + this.lightmapSourceGlId
                    + " atlasGlId=" + this.atlasSourceGlId);
        }
        if (this.textureRegistry.announceFullPixels(bound, data, width, height, x, y)) {
            this.animatedImageFills++;
        }
    }

    @Override
    public void invalidateTexture(int glTextureId) {
        this.lastInvalidationOrigin = "invalidateTexture";
        // Who invalidates what, named once per texture. Three hooks have failed to catch the light map's writes, and the
        // alternative to naming a fourth is asking the code that performs the invalidation to say where it came from.
        if (this.invalidationOrigins.size() < 12 && this.invalidationOrigins.add(Integer.valueOf(glTextureId))) {
            StringBuilder stack = new StringBuilder();
            StackTraceElement[] frames = Thread.currentThread().getStackTrace();
            for (int i = 2; i < Math.min(frames.length, 9); i++) {
                StackTraceElement frame = frames[i];
                stack.append(frame.getClassName().replaceAll(".*\\.", "")).append('.')
                        .append(frame.getMethodName()).append(" (").append(frame.getLineNumber()).append(") <- ");
            }
            System.out.println("[Celtium] CELTIUM_INVALIDATE_ORIGIN glTexture=" + glTextureId
                    + " lightmapGlId=" + this.lightmapSourceGlId + " atlasGlId=" + this.atlasSourceGlId
                    + " stack=" + stack);
        }
        // Animated sprites re-upload every frame; dropping the mirror makes the next draw re-read the GL texture.
        // The registry is created lazily, and Minecraft uploads textures - it registers them - before any world draw:
        // invalidating through a registry that does not exist yet is what made the first attempt crash during
        // "Registering texture".
        //
        // That contract assumes there is a GL texture to re-read, and in the GL-less configuration there is not: the
        // registry's image is the only source of pixels, so dropping it leaves every draw that samples the texture with
        // nothing. It was measured rather than reasoned about - the GUI panels and slot backgrounds were submitted and
        // accepted (the per-format histogram has id6 accepted, the group bounds put the JEI slot cells at the panel's
        // own NDC) and produced no pixels, because the upload hook announces the image and then invalidates it, and the
        // re-read the invalidation relies on cannot happen. Keeping the mirror is the correct behaviour there: the
        // announcement that arrives with the upload is the new content.
        // The predicate is the GL provider, not the frame target: textures are uploaded while the game registers them,
        // which is before any frame target exists, so a frame-target check would answer "GL is there" for exactly the
        // uploads that matter. isInstalled() is true from the moment the stub takes over the GL entry points.
        // The control switch is what separates the two candidate causes of a missing format-6 draw; see
        // GLESS_KEEPS_INVALIDATIONS.
        if (GLESS_KEEPS_INVALIDATIONS && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            this.textureInvalidationsKeptGlless++;
            celtium$reportGllessInvalidation(glTextureId);
            return;
        }
        if (this.textureRegistry != null) {
            this.textureRegistry.invalidate(glTextureId);
        }
        // The terrain atlas and the light map are held directly rather than through the registry, so an animation that
        // re-uploads one of them would leave this renderer drawing the old content forever (audit S16). They are refreshed
        // here, in place, which keeps their view and generation - and therefore every descriptor key - unchanged.
        if (this.atlasTexture != null && glTextureId == this.atlasSourceGlId) {
            try {
                // The announcement first: if the write that triggered this invalidation handed its pixels over, the mirror can
                // be filled from them and the readback is unnecessary - which is the whole stream this path was.
                if (this.textureRegistry != null && this.textureRegistry.canSkipReadback(glTextureId)) {
                    // The write that invalidated this mirror handed its pixels over, and they went into this image.
                    this.textureRegistry.consumeReadback(glTextureId);
                    this.directMirrorFromAnnouncement++;
                    this.readbackSkippedAfterAnnouncement++;
                } else if (this.textureRegistry != null
                        && this.textureRegistry.applyAnnouncedTo(this.atlasTexture, glTextureId)) {
                    this.directMirrorFromAnnouncement++;
                } else {
                    this.atlasTexture.uploadFromGlTexture(glTextureId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                    if (this.textureRegistry != null) {
                        this.textureRegistry.markFullyInitialized(glTextureId);
                    }
                }
                this.directMirrorRefreshes++;
            } catch (RuntimeException | LinkageError error) {
                record("atlas re-upload failed: " + describe(error));
            }
        }
        if (this.lightmapTexture != null && glTextureId == this.lightmapSourceGlId) {
            try {
                if (this.textureRegistry != null && this.textureRegistry.canSkipReadback(glTextureId)) {
                    this.textureRegistry.consumeReadback(glTextureId);
                    this.directMirrorFromAnnouncement++;
                    this.readbackSkippedAfterAnnouncement++;
                } else if (this.textureRegistry != null
                        && this.textureRegistry.applyAnnouncedTo(this.lightmapTexture, glTextureId)) {
                    this.directMirrorFromAnnouncement++;
                } else {
                    this.lightmapTexture.uploadFromGlTexture(glTextureId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                    if (this.textureRegistry != null) {
                        this.textureRegistry.markFullyInitialized(glTextureId);
                    }
                }
                this.directMirrorRefreshes++;
            } catch (RuntimeException | LinkageError error) {
                record("light map re-upload failed: " + describe(error));
            }
        }
        if (this.textureRegistry != null) {
            this.textureRegistry.origin(this.lastInvalidationOrigin);
        }
        this.immediateTextureInvalidations++;
    }

    /**
     * Establishes the whole image for a directly sampled texture - the atlas or the light map - exactly once per generation.
     * <p>
     * A full readback is the only way to get a texture whose pixels never came through an announcement, so it stays. When the
     * announced pixels are already in this image the readback is redundant and is skipped, and the skip is counted: those
     * skips are the stream that used to run once per animation frame.
     */
    private void celtium$establishAtlasImage(int glTextureId, VulkanGlTexture image, String what) {
        if (this.textureRegistry != null && this.textureRegistry.canSkipReadback(glTextureId)) {
            this.textureRegistry.consumeReadback(glTextureId);
            this.directMirrorFromAnnouncement++;
            this.readbackSkippedAfterAnnouncement++;
            return;
        }
        image.uploadFromGlTexture(glTextureId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
        if (this.textureRegistry != null) {
            this.textureRegistry.markFullyInitialized(glTextureId);
        }
    }

    /**
     * Handles a GL texture id being fully (re)allocated: a resource reload, a re-stitch, or an id that came back for
     * different content.
     * <p>
     * The mirror for the id is dropped before the new pixels are announced, so an image can never outlive the content it
     * was built from. The directly sampled textures release their reference and are re-established on the next frame from
     * the fresh image the registry creates; a descriptor is keyed by the image it samples, so it is recomputed rather
     * than reused.
     */
    public void onGlTextureReallocated(int glTextureId) {
        onGlTextureReallocated(glTextureId, -1, -1);
    }

    /**
     * As {@link #onGlTextureReallocated(int)}, with the incoming content's size so a same-size write keeps its mirror.
     * <p>
     * A full upload is only a new texture identity when the storage changed: {@code DynamicTexture.updateDynamicTexture}
     * reaches this method on every light-map refresh with the same id and the same 16x16, and forgetting the mirror each
     * time meant a new image, a new view and new descriptor keys per update - and a readback whenever the announce then
     * missed. A same-size write is a content replace instead: the entry is kept, marked stale, and the announcement that
     * follows fills it in place.
     */
    public void onGlTextureReallocated(int glTextureId, int width, int height) {
        this.glTextureDeletions++;
        boolean sameSize = width > 0 && height > 0 && this.textureRegistry != null
                && this.textureRegistry.hasEntry(glTextureId, width, height);
        if (sameSize) {
            // Marked stale, not refreshed: invalidateTexture would run the direct refresh path now, before the
            // announcement below lands, and read the texture back - the very cost this branch exists to avoid. The
            // stale mark alone keeps an unannounced write honest: the next lookup refreshes it if nothing arrives.
            this.textureRegistry.invalidate(glTextureId);
            return;
        }
        if (this.textureRegistry != null) {
            this.textureRegistry.forgetTexture(glTextureId);
        }
        if (this.atlasTexture != null && glTextureId == this.atlasSourceGlId) {
            this.atlasTexture = null;
            this.atlasSourceGlId = 0;
            this.atlasGlId = 0;
            this.atlasRefreshCountdown = 0;
            this.atlasReestablished++;
        }
        if (this.lightmapTexture != null && glTextureId == this.lightmapSourceGlId) {
            this.lightmapTexture = null;
            this.lightmapSourceGlId = 0;
            this.lightmapGlId = 0;
            this.lightmapRefreshCountdown = 0;
            this.lightmapReestablished++;
        }
    }

    /**
     * Queues an existing mesh for the immediate window instead of drawing it in a pass of its own.
     * <p>
     * The model draw used to open a command buffer, record a pass and submit it inline at {@code callList}. That made it a
     * separate submission inside the frame: it did not participate in the ordering the immediate path maintains through its
     * window, and two identical runs did not produce the same picture in the model's band (measured: the default floor in
     * that band was 32 pixels, the experiment's own floor 1283). Queueing the mesh as a group of the window means the
     * flush that precedes the next GL draw records it in the same pass as everything else, in the same order.
     * <p>
     * The mesh is borrowed: the captured model meshes live for the whole run and are drawn every frame, so the retire
     * drain must not free them.
     */
    /**
     * The 2D affine part of the texture matrix, or {@code null} when it is the identity. {@code matrixMode(5890)}
     * routes glTranslate/glRotate/glScale onto the mirror's texture stack; the item glint animates its texture this
     * way and the renderer uses the same channel for its own overlays. Only the UV transform matters, so the result
     * is {@code [a,b,c,d,tx,ty]} in column-major terms ({@code u'=a*u+c*v+tx, v'=b*u+d*v+ty}).
     */
    /**
     * {@return whether the colour the game has current differs from the one captured with {@code currentGroupColor}}.
     * <p>
     * BH-04: the group key compared the format, texture, state key, pass and matrices, but not the current colour, so
     * two colourless draws in one group were both drawn with the first one's tint. A {@code null} captured colour means
     * the group's format does not take a colour at all, and then there is nothing to compare.
     */
    private boolean celtium$colourDiffers() {
        if (this.currentGroupColor == null) {
            return true;
        }
        return this.currentGroupColor[0] != this.immediateState.colorRed()
                || this.currentGroupColor[1] != this.immediateState.colorGreen()
                || this.currentGroupColor[2] != this.immediateState.colorBlue()
                || this.currentGroupColor[3] != this.immediateState.colorAlpha();
    }

    /**
     * {@return whether the live texture affine differs from the one captured with {@code currentGroupTexMatrix}}.
     * <p>
     * BH-04's texture-matrix half: the affine is per-draw state (the item glint scrolls it, and the renderer's own
     * effects set it), and it is captured once when a group opens. A batch offered after it changed used to join the
     * open group and be drawn with the earlier transform. {@code null} means the identity, so a move to or from the
     * identity is a change like any other.
     */
    private boolean celtium$textureAffineMoved() {
        float[] now = celtium$textureAffine();
        float[] captured = this.currentGroupTexMatrix;
        if (now == null || captured == null) {
            return (now == null) != (captured == null);
        }
        return !java.util.Arrays.equals(now, captured);
    }

    /**
     * {@return whether the live fog differs from the one captured with {@code currentGroupFog}}.
     * <p>
     * BH-04's fog half, and the reason the fog was captured per group in the first place: {@code renderSky} brackets
     * the sky dome with fog enabled and the sun and moon with it disabled inside one pass, so a group that spanned
     * such a bracket drew both halves under one of the two. {@code null} is fog off, which is a state to compare.
     */
    /**
     * {@return whether the live clip rectangle is the one the open group was captured under}.
     * <p>
     * The comparison is between two <em>clips</em>, not two boxes: a rectangle is only in force when the test is
     * enabled <em>and</em> {@code glScissor} has been called, so "test off with a box recorded" and "test on with a box
     * recorded" are different states and must split the group even when the four numbers are equal. The live side is
     * read through the value readers rather than {@code scissorBox()} because this runs once per offered batch, and
     * that method builds an array.
     */
    private boolean celtium$sameScissor(boolean liveApplies) {
        boolean capturedApplies = this.currentGroupScissorEnabled && this.currentGroupScissor != null;
        if (capturedApplies != liveApplies) {
            return false;
        }
        if (!liveApplies) {
            return true;
        }
        int[] captured = this.currentGroupScissor;
        return captured[0] == org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorX()
                && captured[1] == org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorY()
                && captured[2] == org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorWidth()
                && captured[3] == org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorHeight();
    }

    private boolean celtium$fogMoved() {
        float[] now = celtium$fogSnapshot();
        float[] captured = this.currentGroupFog;
        if (now == null || captured == null) {
            return (now == null) != (captured == null);
        }
        return !java.util.Arrays.equals(now, captured);
    }

    private float[] celtium$textureAffine() {
        try {            // Unit 0's matrix explicitly: the lightmap unit's matrix is a different transform on a different stack,
            // and reading the shared stack would return whichever unit was last active.
            float[] matrix = this.immediateState.textureMatrix(0);
            if (matrix == null) {
                return null;
            }
            float a = matrix[0], b = matrix[1], c = matrix[4], d = matrix[5], tx = matrix[12], ty = matrix[13];
            if (a == 1.0f && b == 0.0f && c == 0.0f && d == 1.0f && tx == 0.0f && ty == 0.0f) {
                return null;
            }
            return new float[] {a, b, c, d, tx, ty};
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    /**
     * The lit group's push payload: five vec4 - the two light directions rotated into model space, the scene ambient,
     * and each light's diffuse - or {@code null} when lighting is off.
     * <p>
     * GL lights live in eye space while the vertex shader only has model-space normals, so each eye-space direction is
     * pre-multiplied by the inverse modelview's rotation: {@code dot(N, M^-1 * L)} equals the fixed pipeline's
     * {@code dot(M^-T * N, L)} exactly for orthogonal transforms, which is every transform vanilla's lit draws use.
     * A disabled light's diffuse is zeroed rather than the direction skipped - the shader's dot stays a constant.
     */
    private float[] celtium$lightParams(int[] state, float[] modelView) {
        if (state == null || state.length < 9 || state[8] == 0 || modelView == null) {
            return null;
        }
        // Inverse of the upper 3x3, named in row/col so the cofactors stay readable: a b c / d e f / g h i, with the
        // modelview's column-major array addressed as a=m[0], d=m[1], g=m[2], b=m[4] and so on.
        float a = modelView[0], d = modelView[1], g = modelView[2];
        float b = modelView[4], e = modelView[5], h = modelView[6];
        float c = modelView[8], f = modelView[9], i22 = modelView[10];
        float det = a * (e * i22 - f * h) - b * (d * i22 - f * g) + c * (d * h - e * g);
        float[] inv;
        if (Math.abs(det) < 1.0e-8f) {
            // A singular modelview degenerates the draw anyway; identity directions refuse nothing.
            inv = new float[] {1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
        } else {
            float invDet = 1.0f / det;
            inv = new float[] {
                    (e * i22 - f * h) * invDet, (f * g - d * i22) * invDet, (d * h - e * g) * invDet,
                    (c * h - b * i22) * invDet, (a * i22 - c * g) * invDet, (b * g - a * h) * invDet,
                    (b * f - c * e) * invDet, (c * d - a * f) * invDet, (a * e - b * d) * invDet,
            };
        }
        float[] params = new float[20];
        float[] dirs = this.immediateState.lightDirections();
        float[] diffuse = this.immediateState.lightDiffuse();
        float[] ambient = this.immediateState.lightAmbient();
        for (int i = 0; i < 2; ++i) {
            int eye = i * 3;
            float x = inv[0] * dirs[eye] + inv[3] * dirs[eye + 1] + inv[6] * dirs[eye + 2];
            float y = inv[1] * dirs[eye] + inv[4] * dirs[eye + 1] + inv[7] * dirs[eye + 2];
            float z = inv[2] * dirs[eye] + inv[5] * dirs[eye + 1] + inv[8] * dirs[eye + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 1.0e-6f) {
                params[i * 4] = x / len;
                params[i * 4 + 1] = y / len;
                params[i * 4 + 2] = z / len;
            }
            int diffuseBase = 12 + i * 4;
            if (this.immediateState.lightEnabled(i)) {
                params[diffuseBase] = diffuse[eye];
                params[diffuseBase + 1] = diffuse[eye + 1];
                params[diffuseBase + 2] = diffuse[eye + 2];
            }
        }
        params[8] = ambient[0];
        params[9] = ambient[1];
        params[10] = ambient[2];
        return params;
    }

    /**
     * The texture-environment constant the fixed pipeline would mix in on the lightmap unit, or {@code null} when the
     * environment is not set to {@code GL_INTERPOLATE}: the hurt flash and the team outline are the two vanilla
     * effects that draw the same model twice through this combine, and the constant is their colour. Asked of GL at
     * replay time because the caller sets it per render - it is not part of the captured list.
     */
    private float[] celtium$envCombineColor() {
        try {
            int active = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);
            int combine = org.lwjgl.opengl.GL11.glGetTexEnvi(org.lwjgl.opengl.GL11.GL_TEXTURE_ENV,
                    org.lwjgl.opengl.GL13.GL_COMBINE_RGB);
            float[] color = null;
            if (combine == org.lwjgl.opengl.GL13.GL_INTERPOLATE) {
                java.nio.FloatBuffer env = org.lwjgl.BufferUtils.createFloatBuffer(4);
                org.lwjgl.opengl.GL11.glGetTexEnvfv(org.lwjgl.opengl.GL11.GL_TEXTURE_ENV,
                        org.lwjgl.opengl.GL11.GL_TEXTURE_ENV_COLOR, env);
                color = new float[] { env.get(0), env.get(1), env.get(2), env.get(3) };
                if (this.envCombineLogged < 6) {
                    this.envCombineLogged++;
                    System.out.println("[Celtium] CELTIUM_ENV_COMBINE rgba="
                            + round3(color[0]) + "," + round3(color[1]) + "," + round3(color[2])
                            + "," + round3(color[3]));
                }
            }
            org.lwjgl.opengl.GL13.glActiveTexture(active);
            return color;
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    private boolean celtium$enqueueModelMesh(VulkanDynamicMesh mesh, int texture, int[] state, long key,
            float[] projection, float[] modelView, int quads, float[] combineColor) {
        if (mesh == null || mesh.isEmpty()) {
            return false;
        }
        // ITEM, because that is the format whose pipeline evaluates the fixed-function lighting: its lit variant reads
        // the vertex normal and the push block's five vec4 (the two light directions in model space, the scene ambient
        // and each light's diffuse) and multiplies the result into the vertex colour. The vertex stride is the same 28
        // bytes the check at the top of prepareImmediate compares against, so the mesh built above is what it wants.
        PreparedImmediate prepared = prepareImmediate(VulkanImmediateFormats.ITEM, texture, key, state);
        if (prepared == null || prepared.pipeline == null) {
            return false;
        }
        long target = this.currentRenderTarget;
        if (this.modelMatrixLoggedCount < 8) {
            // A handful of model enqueues, each saying which kind of projection it is drawn under. One row was not enough: the
            // residual image still showed screen-space quads, so the question is whether the projection is wrong at every
            // enqueue or only at some of them - and whether the modelview's rotation/scale rows are the ones the part was
            // compiled with. projection[11] is -1 for a GL perspective and 0 for an orthographic projection.
            this.modelMatrixLoggedCount++;
            System.out.println("[Celtium] CELTIUM_MODEL_ENQ texture=" + texture
                    // The target the group is stamped with, against the main identity: a model group whose target is not
                    // the one the window draws is kept in the queue instead of drawn (`immediateGroupsKeptForOtherTargets`),
                    // which is the shape of "accepted and never drawn" that the format histograms show for id4 and id9.
                    + " target=0x" + Long.toHexString(target) + " main=0x" + Long.toHexString(this.mainTargetIdentity)
                    + " mainBound=" + this.currentRenderTargetIsMain
                    + " projKind=" + (Math.abs(projection[11] + 1.0f) < 0.01f ? "perspective" : "ortho")
                    + " proj11=" + round3(projection[11]) + " proj10=" + round3(projection[10])
                    + " proj14=" + round3(projection[14])
                    + " mvT=" + round3(modelView[12]) + "," + round3(modelView[13]) + "," + round3(modelView[14])
                    + " mvRow0=" + round3(modelView[0]) + "," + round3(modelView[1]) + "," + round3(modelView[2])
                    + " mvScale=" + round3((float) Math.sqrt(modelView[0] * modelView[0] + modelView[1] * modelView[1]
                            + modelView[2] * modelView[2])));
            System.out.println("[Celtium] CELTIUM_MODEL_BOUNDS "
                    + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedBoundsSummary());
        }
        ImmediateGroup group = new ImmediateGroup(mesh, projection, modelView,
                VulkanImmediateFormats.ITEM, texture, key, state, target);
        group.prepared = prepared;
        group.combineColor = combineColor;
        group.texMatrix = celtium$textureAffine();
        group.lightParams = celtium$lightParams(state, modelView);
        // The same rule as the immediate path, and it is the light-map unit's enable state that decides it - not the
        // pass. The model path's GUI-stage content is the inventory's player model and any TESR item (a chest, a
        // shulker box, a banner: RenderItem.renderItem sends those through
        // getTileEntityItemStackRenderer().renderByItem, RenderItem.java:144-148), and real OpenGL draws both with the
        // unit disabled - GuiInventory.java:154-165 only *disables* the unit at the end of its draw and never enables
        // it, so nothing lights the player model by the world either. A chest in the hotbar of a dark place was drawn
        // with the cave's light because this line took the captured coordinate unconditionally.
        group.lightmapCoords = celtium$lightmapForGroup();
        group.fog = celtium$fogSnapshot();
        group.batches = quads;
        group.viewportValues = this.currentGroupViewportValues;
        // Borrowed: the mesh belongs to the capture that lent it and is drawn every frame, so the retire path
        // must not free it - which is the whole reason the stamp exists.
        group.stamp = new GroupStamp(this.frameId, this.targetGeneration, true, this.worldEpoch);
        this.immediateGroups.add(group);
        this.immediateGroupsClosed++;
        if (target != this.mainTargetIdentity) {
            // Off-target model batches are drawn in the pass that is open for their target, not carried in the queue.
            // The immediate window draws the groups of `currentRenderTarget` only (`flushImmediateWindow`: "others remain
            // owned in the queue") and runs on the main target later in the frame, so a group stamped with another
            // identity waits for a pass that belongs to it - which for the entity-outline framebuffer never comes,
            // because that framebuffer is cleared and consumed by the outline chain inside the same frame. That is the
            // offer path's own rule, written down as audit A10 ("the queue never sees a non-main group afterwards"), and
            // the model path has to follow it.
            //
            // Measured on the glow scene: 931 off-target model replays arrive from the outline pass
            // (`CELTIUM_MODEL_OFFTARGET list=125 texture=124 target=… main=… inTable=true verts=24`) - the vanilla
            // outline loop does replay the mobs' display lists - and with this flush each of them is recorded into the
            // identity the chain adopts (`…_FLUSH batches+6 flushes+1 groupsLeft=0`, against
            // `CELTIUM_OUTLINE_INPUT … identity=… texture=42`). Without it they sit in the queue: the visible frame
            // gains the outline pass's geometry (creeper green 1.017/0.971/1.003 with the flush, against 1.28/1.27/1.21
            // before it) and the outline image still holds only the item.
            // Coalesced to one flush per target change within a frame: the first off-target batch of a pass flushes the
            // batches of that pass, and the later batches of the same target/frame are left for the next change. Per
            // batch this was a submit and a wait each time (`frameIntervalP95Ms=1946`, world frames 43-63 -> 48).
            if (this.frameId != this.modelOffTargetFlushedFrame || target != this.modelOffTargetFlushedTarget) {
                this.modelOffTargetFlushedFrame = this.frameId;
                this.modelOffTargetFlushedTarget = target;
                long recordedBefore = this.immediateRecordedBatches;
                long flushesBefore = this.actualFlushes;
                this.flushPendingBeforeGlDraw();
                if (this.modelOffTargetFlushLogs < MODEL_OFFTARGET_FLUSH_LOG_LIMIT) {
                    this.modelOffTargetFlushLogs++;
                    System.out.println("[Celtium] CELTIUM_MODEL_OFFTARGET_FLUSH batches+"
                            + (this.immediateRecordedBatches - recordedBefore)
                            + " flushes+" + (this.actualFlushes - flushesBefore)
                            + " windowBusy=" + this.immediateWindowBusy
                            + " target=" + target + " groupsLeft=" + this.immediateGroups.size()
                            + " frame=" + this.frameId);
                }
            } else {
                this.modelOffTargetCoalesced++;
            }
        }
        return true;
    }

    /** Reports the animation-driven mirror refreshes on stdout: the debug list is capped and would drop them. */
    private void reportDirectMirrorRefreshes() {
        if (this.directMirrorRefreshes != this.lastReportedDirectRefreshes) {
            this.lastReportedDirectRefreshes = this.directMirrorRefreshes;
            System.out.println("[Celtium] CELTIUM_GROUPS queued=" + this.immediateGroups.size()
                    + " recorded=" + this.recordedImmediateGroups.size()
                    + " retired=" + this.retiredImmediateGroups.size()
                    + " staleTargetGeneration=" + this.groupsStaleTargetGeneration
                    + " targetGeneration=" + this.targetGeneration
                    + " staleWorldEpoch=" + this.groupsStaleWorldEpoch
                    + " worldEpoch=" + this.worldEpoch
                    + " worldTeardownDiscards=" + this.groupsDiscardedAtWorldTeardown
                    + " fullTeardownDiscards=" + this.groupsDiscardedAtFullTeardown
                    + " submitted=" + this.immediateSubmittedBatches + " completed=" + this.immediateCompletedBatches
                    + " discarded=" + this.immediateGroupsDiscarded
                    + " keptForOtherTargets=" + this.immediateGroupsKeptForOtherTargets
                    + " presentSucceeded=" + (this.swapchain != null && this.swapchain.lastPresentSucceeded())
                    + " lightmapSuppliedOnDemand=" + this.lightmapSuppliedOnDemand
                    + " lightmapSupplyFailure=[" + this.lastLightmapSupplyFailure + "]"
                    + " lineBatchesNative=" + this.lineBatchesNative
                    + " lineBatchesRefusedForWidth=" + this.lineBatchesRefusedForWidth
                    + " maxLineWidth=" + this.maxLineWidth
                    + " presentHookCalls=" + this.presentHookCalls
                    + " presentSkipReason=[" + this.presentSkipReason + "]"
                    + " presentedFromVulkan=" + this.framePresentedFromVulkan
                    + " glCompositesSkipped=" + this.glCompositesSkipped
                    + " defaultFramebufferCompositesTaken=" + this.defaultFramebufferCompositesTaken
                    + " gameFramebufferIsShared=" + this.gameFramebufferIsShared
                    + " gameFramebufferAdopted=" + this.gameFramebufferAdopted
                    + " firstGlError=[" + this.firstGlErrorWhere + ":0x"
                    + Integer.toHexString(this.firstGlErrorCode) + "]"
                    + " frameEntryGlError=[" + (this.frameTarget == null ? "no-target"
                    : String.valueOf(this.frameTarget.firstEntryGlError())) + "]"
                    + " glSwapsSkipped=" + this.glSwapsSkipped
                    // Why the present path last declined, on a line that is known to surface. The resize left the counter
                    // at a third of its usual rate with no refusal naming a size anywhere, so the reason has to be read
                    // where it is set rather than inferred from the outcome.
                    + " presentSkipReason=[" + this.presentSkipReason + "]"
                    + " presentAttempts=" + this.presentAttempts
                    + " presentFailures=" + this.presentFailures
                    + " presentResult=[" + this.lastPresentResult + "]"
                    + " lazyMirrorSupplies=" + this.lazyMirrorSupplies
                    + " lazyMirrorFailures=" + this.lazyMirrorFailures
                    + " prepareDescriptorFailures=" + this.prepareDescriptorFailures
                    + " noPassRefusals=" + this.prepareNoPassRefusals
                    + " noPassFrames=" + this.prepareNoPassFirstFrame + ".." + this.prepareNoPassLastFrame
                    + " lastPrepareFailure=[" + this.lastPrepareFailure + "]");
            System.out.println("[Celtium] CELTIUM_ANIM directMirrorRefreshes=" + this.directMirrorRefreshes
                    + " readbacksSkipped=" + this.readbackSkippedAfterAnnouncement
                    + " invalidations=" + this.immediateTextureInvalidations + " atlasGlId=" + this.atlasSourceGlId
                    + " lightmapGlId=" + this.lightmapSourceGlId
                    + " deletions=" + this.glTextureDeletions + " reestablished=" + this.atlasReestablished
                    + "/" + this.lightmapReestablished
                    + (this.textureRegistry == null ? ""
                            : " invalidationsKeptNoGl=" + this.textureInvalidationsKeptGlless
                            + " " + VulkanGlState.perUnitBindCensus()
                            + " mirrorSources=[" + this.textureRegistry.mirrorSourceSummary() + "]"
                            + " " + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.textureCaptureSummary()
                            + " cost=[" + this.textureRegistry.patchCostSummary() + "]"));
        }
    }

    @Override
    public void invalidateBoundTexture() {
        this.lastInvalidationOrigin = "invalidateBoundTexture";
        int bound = this.immediateState.boundTextureName();
        if (bound != 0) {
            invalidateTexture(bound);
        }
    }

    /**
     * Names what the GL-less invalidation did, on a gate that can actually fire there.
     * <p>
     * The texture summary line ({@code CELTIUM_ANIM}) is gated on {@code directMirrorRefreshes} changing, and this
     * branch is what stops that counter from moving: with the stub installed the line prints once at startup, when the
     * registry does not exist yet, so everything it says about mirrors - the kept-invalidation count, the fills by
     * source and the stub's own capture census - was unreachable in exactly the configuration it was written for. This
     * prints the same facts from the branch itself, bounded to the first and then every five hundredth invalidation so
     * an animated texture cannot flood the log.
     * <p>
     * {@code unconverted} in the capture census is the number that matters here: it counts uploads the stub could not
     * turn into pixels (a compressed payload, a format/type pair it does not model), and each of those is a texture
     * whose mirror has no source except the announcement. A non-zero value is the measurement that justifies keeping
     * announcements alive across a delete-and-reallocate.
     * <p>
     * The mirror economy is on the same line, because the question the GUI raised is answered by three numbers and not
     * by one: {@code cost}'s {@code glLess[fromAnnouncement, fromCapture, noContent]} says which source filled a
     * mirror and how often none could, {@code lazySupply} says how often a batch had to hand the whole image over
     * before the preflight (a mirror that never reports whole content is supplied again and again), and
     * {@code partialRefusals} is the format-5 gate that refuses exactly that state. Printed on the first and then
     * every hundredth kept invalidation, so a run that reaches the world carries them without an animated texture
     * flooding the log.
     */
    private void celtium$reportGllessInvalidation(int glTextureId) {
        int kept = this.textureInvalidationsKeptGlless;
        if (kept != 1 && kept % 100 != 0) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_GLESS_INVALIDATE kept=" + kept + " glTexture=" + glTextureId
                + " atlasGlId=" + this.atlasSourceGlId + " lightmapGlId=" + this.lightmapSourceGlId
                + " lazySupply=" + this.lazyMirrorSupplies + "/" + this.lazyMirrorFailures
                + " partialRefusals=" + this.partialMirrorRefusals + " partialCompleted=" + this.partialMirrorsCompleted
                + (this.textureRegistry == null ? " registry=none"
                        : " retainedAnnouncements=" + this.textureRegistry.retainedAnnouncements()
                        + " fills=[" + this.textureRegistry.mirrorSourceSummary() + "]"
                        + " cost=[" + this.textureRegistry.patchCostSummary() + "]")
                + " " + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.textureCaptureSummary()
                + " (no GL readback exists under the stub: a mirror is refreshed from the announced image the game"
                + " handed over, which now survives the texture's own delete-and-reallocate, and otherwise from the"
                + " upload the stub captured)");
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
        // The state mirror already keeps this colour (immediateState.color); the flag only says whether the game has ever
        // set one, which is what decides between the mirror and the GL read on the position-only path.
        this.colorMirrorHasValue = true;
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
        celtium$fontMatrixBoundary();
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
        celtium$fontMatrixBoundary();
        // The GUI's projection arrives through this call. If it stops arriving, every GUI batch is drawn with whatever
        // perspective the world pass left in the mirror - which is a huge quad where the HUD should be.
        if (this.orthoCalls < 6) {
            this.orthoCalls++;
            System.out.println("[Celtium] CELTIUM_ORTHO l=" + (float) left + " r=" + (float) right + " b=" + (float) bottom
                    + " t=" + (float) top + " n=" + (float) near + " f=" + (float) far);
        }
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
        celtium$fontMatrixBoundary();
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
        celtium$fontMatrixBoundary();
        this.matricesDirty = true;
        if (GROUP_BOUNDS_DIAGNOSTICS) {
            while (this.rotationLog.size() >= ROTATION_LOG_LIMIT) {
                this.rotationLog.poll();
            }
            this.rotationLog.add(fmt3(angle) + "@" + fmt3(x) + "," + fmt3(y) + "," + fmt3(z));
        }
        this.immediateState.rotate(angle, x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void scale(float x, float y, float z) {
        celtium$fontMatrixBoundary();
        this.matricesDirty = true;
        this.immediateState.scale(x, y, z);
        this.immediateStateCalls++;
    }

    @Override
    public void activeTextureUnit(int unit) {
        this.immediateState.activeTextureUnit(unit);
    }

    @Override
    public void depthFunc(int func) {
        this.immediateState.depthFunc(func);
    }

    @Override
    public void perspective(float fovDegrees, float aspect, float near, float far) {
        celtium$fontMatrixBoundary();
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

    /**
     * The GLU helper's whole call, mirrored as the load it is.
     * <p>
     * {@link #perspective} mirrors what {@code Project.gluPerspective} does to GL - multiply a fresh frustum in - but
     * not what the stack holds underneath it. Every call site in {@code EntityRenderer} puts
     * {@code matrixMode(GL_PROJECTION)} and {@code loadIdentity()} in front of the call, and the mirrored
     * {@code loadIdentity} does leave the mirror's projection at the identity - and then
     * {@code applyDerivedProjection} writes the pass projection over it, because the field of view is an argument to
     * this very call and its hook fires while the arguments are evaluated. So at the instant the frustum is
     * multiplied, GL is at the identity and the mirror is at the pass projection: the mirror has to reset the stack to
     * what GL holds first, or the pass projection is applied twice.
     * <p>
     * That double application is measured, not inferred. Hooking the channel without the reset ({@code f82bf4612})
     * took the visible sections from 23 to 8 on the same camera and fixture, because the culling reads this same
     * mirror through {@code ClippingHelperImpl.getInstance()} -> {@code GlStateManager.getFloat(GL_PROJECTION_MATRIX)}
     * and a squared projection culls the world away. With the reset, the camera's own call ends with the projection it
     * already had (the two are the same matrix: the same field of view, aspect, near and far), and only the calls that
     * genuinely install a different projection - the sky's ({@code farPlaneDistance * 2.0}), the clouds'
     * ({@code * 4.0}), the hand's ({@code getFOVModifier(partialTicks, false)}) - change what the mirror holds, each
     * to exactly the value GL holds.
     *
     * @return whether the call reached the mirror; {@code false} when LWJGL's own helper would have returned before
     *         touching GL, or when GL state was unavailable
     */
    @Override
    public boolean mirrorGluPerspective(float fovDegrees, float aspect, float near, float far) {
        // The shapes LWJGL 2's Project.gluPerspective returns early on (its own `if (deltaZ == 0 || sine == 0 ||
        // aspect == 0) return;`, read from lwjgl_util-2.9.4's bytecode): it touches no GL state in either, so neither
        // may the mirror. A non-finite argument is added here rather than in LWJGL: multiplying NaN into the mirrored
        // projection would poison every later draw, and no guard downstream could tell that from a real projection.
        if (aspect == 0.0f || near == far || fovDegrees % 360.0f == 0.0f
                || !Float.isFinite(fovDegrees) || !Float.isFinite(aspect)
                || !Float.isFinite(near) || !Float.isFinite(far)) {
            this.skippedGluPerspectives++;
            reportSkippedGluPerspective(fovDegrees, aspect, near, far);
            return false;
        }
        float[] before = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION);
        // The mirrored loadIdentity this call site always performs, applied to the projection stack whether or not the
        // mirror's mode still points at it: the stack whose value GL is replacing is the projection stack.
        int mode = this.immediateState.matrixMode();
        this.immediateState.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        this.immediateState.loadIdentity();
        this.immediateState.matrixMode(mode);
        this.perspective(fovDegrees, aspect, near, far);
        this.mirroredGluPerspectives++;
        reportGluPerspective(fovDegrees, aspect, near, far, before);
        return true;
    }

    /**
     * One line per distinct projection the GLU channel installs, named with the game-side call site that installed it.
     * <p>
     * This is the census that separates the explanations a wrong sky draw can have: the channel is not live (no line
     * at all), the channel is live but the call site is not the one that draws the sky, or the channel is live and
     * installed exactly what vanilla installs - which the projection itself says, since the field of view, the aspect
     * and the far plane are all printed. The value the mirror held <em>before</em> the call is printed beside it,
     * because the identity there is the defect this channel exists to remove.
     * <p>
     * Keyed on the call site rather than on the projection: the field of view moves every frame, so a value key fills
     * its bound with camera lines inside the first frames and hides the sites it was written to name - measured on the
     * first run of this hook, where eight shapes were the camera's and only two lines named anything else.
     */
    private void reportGluPerspective(float fovDegrees, float aspect, float near, float far, float[] before) {
        String site = gluPerspectiveSite();
        if (this.gluPerspectiveShapes.size() >= 12 || !this.gluPerspectiveShapes.add(site)) {
            return;
        }
        float[] after = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION);
        System.out.println("[Celtium] CELTIUM_GLUPERSPECTIVE fov=" + round3(fovDegrees) + " aspect=" + round3(aspect)
                + " near=" + round3(near) + " far=" + round3(far)
                + " after=[m00=" + round3(after[0]) + ",m11=" + round3(after[5]) + ",m22=" + round3(after[10])
                + ",m23=" + round3(after[11]) + ",m32=" + round3(after[14]) + ",m33=" + round3(after[15]) + "]"
                + " before=[m00=" + round3(before[0]) + ",m11=" + round3(before[5]) + ",m22=" + round3(before[10])
                + ",m23=" + round3(before[11]) + ",m32=" + round3(before[14]) + ",m33=" + round3(before[15]) + "]"
                + " site=[" + site + "]");
    }

    /**
     * One line per distinct call site whose projection could not be mirrored, with the reason.
     * <p>
     * A skip is where the mirror and GL part company by construction: LWJGL returns before touching GL for these
     * arguments, so GL keeps the identity the mirrored {@code loadIdentity} left - which is what the stub's draw census
     * then reports as "the mirrored projection is the identity at draw time". Naming the site and the argument is what
     * tells that (correct, and vanilla's own behaviour) apart from the channel not arriving, which is the defect.
     */
    private void reportSkippedGluPerspective(float fovDegrees, float aspect, float near, float far) {
        String reason = aspect == 0.0f ? "aspect=0 (LWJGL returns before touching GL)"
                : near == far ? "deltaZ=0 (LWJGL returns before touching GL)"
                : fovDegrees % 360.0f == 0.0f ? "fov is a multiple of 360 (sine=0)"
                : "a non-finite argument";
        String site = gluPerspectiveSite() + " " + reason;
        if (this.gluPerspectiveSkips.size() >= 8 || !this.gluPerspectiveSkips.add(site)) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_GLUPERSPECTIVE_SKIP fov=" + round3(fovDegrees) + " aspect="
                + round3(aspect) + " near=" + round3(near) + " far=" + round3(far) + " reason=[" + reason
                + "] site=[" + gluPerspectiveSite() + "]");
    }

    /** The game-side frames of the current call stack, innermost first: which call site this projection came from. */
    private static String gluPerspectiveSite() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        int taken = 0;
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            if (name.startsWith("org.soutalab.celtium") || name.startsWith("org.taumc.celeritas")
                    || name.startsWith("org.spongepowered") || name.startsWith("org.lwjgl")) {
                continue;
            }
            if (taken > 0) {
                frames.append(" <- ");
            }
            frames.append(name).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            if (++taken >= 2) {
                break;
            }
        }
        return frames.length() == 0 ? "unknown" : frames.toString();
    }

    @Override
    public void frustum(double left, double right, double bottom, double top, double near, double far) {
        celtium$fontMatrixBoundary();
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
        celtium$fontMatrixBoundary();
        this.matricesDirty = true;
        this.immediateState.multiplyMatrixBuffer(buffer);
        this.immediateStateCalls++;
        // The application side of the raw glMultMatrixf census: the stub counts arrivals and applies nothing, on
        // purpose (one operation, one owner), and this counts the multiplies that actually reached the mirror -
        // whether from GlStateManager's own multMatrix or from ForgeHooksClientMixin's item camera transform.
        this.mirroredMultiplies++;
    }

    @Override
    public void pushMatrix() {
        celtium$fontMatrixBoundary();
        this.matricesDirty = true;
        this.immediateState.pushMatrix();
        this.immediateStateCalls++;
    }

    @Override
    public void popMatrix() {
        celtium$fontMatrixBoundary();
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
        if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            // Nothing to seed from: the matrices this would read belong to a context that does not exist, and the
            // stub answers both queries with the identity. Seeding the mirror from that would replace a camera
            // transform the hooks did observe with the identity - measured, the world then draws from the origin as
            // a wedge instead of a plane. The mirror already holds what the hooked GlStateManager calls produced,
            // and the projection comes from applyDerivedProjection, so the honest answer is to leave it alone.
            this.cameraSetupSeedsSkipped++;
            return;
        }
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

    /** {@return one line per off-target render target that received batches}, for the per-target work's sizing. */
    public String offTargetSummary() {
        StringBuilder text = new StringBuilder();
        for (java.util.Map.Entry<Long, java.util.concurrent.atomic.AtomicInteger> entry : this.offTargetBatchesByTarget.entrySet()) {
            text.append(entry.getKey()).append(':').append(entry.getValue().get())
                    .append('(').append(pairOfOffTargetIdentity(entry.getKey().longValue())).append(") ");
        }
        return text.length() == 0 ? "none" : text.toString().trim();
    }

    /**
     * Which of the two owned pairs an off-target identity's draws render into. The rule itself is stated once, in
     * {@link GllessFrameTarget#pairLabelForIdentity(long)}, which owns the pairs; this is the name the backend's
     * counters use so a reported identity is never read against the wrong image.
     */
    private static String pairOfOffTargetIdentity(long identity) {
        return GllessFrameTarget.pairLabelForIdentity(identity);
    }

    /** {@return the owned pairs' own creation/clear/self-test counters, or "none" for the GL bridge}. */
    private String frameTargetPairSummary() {
        GllessFrameTarget glless = offTargetTarget();
        return glless == null ? "none" : glless.pairSummary();
    }

    @Override
    public String lastRefusal() {
        if (this.immediateLastRefusal != null && this.immediateLastRefusal.startsWith("unrecorded")) {
            // The refusal never named itself; the stage says where the batch stopped. Stages are set at the checkpoints of
            // the offer path below, so a number here is a location and not a guess.
            return this.immediateLastRefusal + " stage=" + this.offerStage;
        }
        return this.immediateLastRefusal;
    }

    public void flushBeforeClear(int mask) {
        if (this.immediateWindowBusy) return; // bridge GL state restore must not recursively flush
        flushPendingBeforeGlDraw();
        this.clearOrderFlushes++;
    }


    public void blendFunc(int sourceFactor, int destinationFactor) {
        // Only the factors Vulkan and OpenGL actually define: one run showed every particle group capturing
        // (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) - exactly the constants the family asserted - plus a single group capturing
        // (6, 7), which is not a blend factor pair at all and was enough to spoil the frame when the observed values were
        // used. A mirror that records a value the API does not define is worse than one that records nothing.
        if (!isBlendFactor(sourceFactor) || !isBlendFactor(destinationFactor)) {
            this.blendFuncIgnored++;
            return;
        }
        // The 2-argument form *is* the state. It used to be read and thrown away - observedBlendSrc/Dst were written
        // but the mirror was not - so the mirror kept whatever the last 4-argument tryBlendFuncSeparate left, and a
        // batch drawn under the 2-argument pair was enqueued and had its pipeline built with the wrong factors. That
        // is not a cosmetic difference: LayerSpiderEyes calls enableBlend(); disableAlpha(); blendFunc(ONE, ONE) for
        // the additive red eye layer, and with the mirror still holding (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) the layer
        // came out alpha-blended instead - measured as the eye's red channel matching the reference at 0.99 while
        // green and blue sat at 0.64-0.66, which is the signature of alpha-blending an additive layer.
        //
        // GL's glBlendFunc sets the RGB and alpha factors alike, which is exactly how GlStateManager.blendFunc(int,int)
        // behaves (it assigns srcFactor/dstFactor to the alpha fields too), so all four slots take the same pair.
        this.immediateState.blendFunc(sourceFactor, destinationFactor, sourceFactor, destinationFactor);
        this.immediateStateCalls++;
        this.blendFuncTwoArgCalls++;
        this.lastTwoArgBlendPair = "(" + sourceFactor + "," + destinationFactor + ")";
        this.observedBlendSrc = sourceFactor;
        this.observedBlendDst = destinationFactor;
        this.blendObserved = true;
        if (sourceFactor != 0x0302 || destinationFactor != 0x0303) {
            // Anything that is not the ordinary alpha pair, kept verbatim so the outlier can be read rather than decoded.
            this.blendOutlier = "blendFunc(" + sourceFactor + "," + destinationFactor + ") at frame " + this.framesSeen;
        }
    }

    /** The blend factors the GL and Vulkan enums define: zero, one, and the 0x0300..0x0307 range. */
    private static boolean isBlendFactor(int factor) {
        return factor == 0 || factor == 1 || (factor >= 0x0300 && factor <= 0x0307);
    }

    /**
     * Tells the next {@link #bindFramebuffer} that the framebuffer being bound has a different size from the shared images.
     * <p>
     * A resize replaces the game framebuffer, so the bind that follows is a generation change rather than a pass change.
     * Flushing the previous pass at that point waits on a device that is in the middle of that replacement, and the wait
     * failing marks the device stalled - permanently, by design, since nothing clears that flag. Measured: one mid-run
     * resize froze the present path (presentedFromVulkan stuck at 355 while the same duration without a resize reached
     * 785). There is no ordering to preserve either: the previous framebuffer's contents have already been presented.
     * <p>
     * Only the <b>main</b> framebuffer can replace the shared images. The hint used to fire for every framebuffer whose
     * size differed, and the game binds framebuffers of other sizes on purpose - the vanilla loading screen's own
     * framebuffer is 468x1018 against the window's 854x480 - so the flag was set while nothing was being replaced and
     * the next ordering flush <b>dropped the pending groups</b> instead of drawing them
     * ({@code CELTIUM_REPLACE_DROP batches=1 target=854x480 frameId=0}, measured on the splash's own composite batch at
     * startup). That drop is what kept the loading screen off the window. The caller says which framebuffer it is.
     */
    public void celtium$noteBindSize(int width, int height, boolean mainFramebuffer) {
        if (mainFramebuffer && this.frameTarget != null && !this.frameTarget.matches(width, height)) {
            this.framebufferReplacing = true;
        }
    }

    /**
     * The colour texture behind a bound framebuffer, and its size.
     * <p>
     * The identity alone says which target a batch belongs to; this says which image stands for its pixels. With no
     * OpenGL context there is nothing to ask for an attachment, so the association is made where the framebuffer is
     * bound. It is what lets a pass that reads another pass's framebuffer - the entity outline reads the silhouette
     * framebuffer - be routed to the right image, and what lets the game's composite find that image by the texture
     * name it binds.
     */
    @Override
    public void bindFramebufferColorTexture(long framebufferIdentity, int colorTexture, int width, int height) {
        if (colorTexture == 0 || width <= 0 || height <= 0) {
            this.framebufferColorTextures.remove(Long.valueOf(framebufferIdentity));
            return;
        }
        this.framebufferColorTextures.put(Long.valueOf(framebufferIdentity),
                new long[] { colorTexture, width, height });
        if (framebufferIdentity == this.mainTargetIdentity && width > 0 && height > 0) {
            // The game's main framebuffer's own size, which is what the frame target must be: the GL viewport is set per
            // framebuffer bind, so sizing the target from it can adopt an off-target's extent (the loading screen's own
            // framebuffer is the measured case) and rebuild the target - which destroys the pairs and unpublishes every
            // name published over them, leaving the composite to sample an empty mirror (the white loading screen).
            this.mainFramebufferWidth = width;
            this.mainFramebufferHeight = height;
        }
        // The last framebuffer the game bound is the one it is rendering into, which is what the overlay-vs-blit
        // question needs; the alternative - asking GL - is exactly what the GL-less stub cannot answer.
        this.currentFramebufferIdentity = framebufferIdentity;
        this.framebufferColorTextureReports++;
        if (this.framebufferColorTextureLog < 6) {
            this.framebufferColorTextureLog++;
            record("framebuffer colour texture: identity=" + framebufferIdentity + " texture=" + colorTexture
                    + " " + width + "x" + height);
        }
    }

    /** The colour texture recorded for a framebuffer identity as {texture, width, height}, or null. */
    private long[] framebufferColorTexture(long identity) {
        return this.framebufferColorTextures.get(Long.valueOf(identity));
    }

    /** The colour texture name standing for a framebuffer identity, or 0 when none was reported. */
    private int framebufferColorTextureName(long identity) {
        long[] entry = framebufferColorTexture(identity);
        return entry == null ? 0 : (int) entry[0];
    }

    public void bindFramebuffer(long targetIdentity, boolean isMain) {
        // First, while the fields below still describe the framebuffer that is bound: the game clears a framebuffer
        // between binding it and binding the next one, so this is the only moment a glClear can be attributed to the
        // image it names. See celtium$latchGameClearAtTargetChange for what the two routes mean.
        celtium$latchGameClearAtTargetChange();
        if (!this.immediateWindowBusy && targetIdentity != this.currentRenderTarget && !this.framebufferReplacing) {
            // Framebuffer.bind HEAD: the OLD framebuffer is still bound, so complete its accepted draws before switching.
            flushPendingBeforeGlDraw();
        }
        if (targetIdentity != this.currentRenderTarget && this.currentImmediateMesh != null) {
            // A pass change is an ordering boundary: whatever is pending belongs to the pass that is ending, so it is closed
            // here and drawn before the new pass's work rather than merged with it.
            closeCurrentImmediateGroup();
            this.passBoundaryFlushes++;
        }
        this.currentRenderTarget = targetIdentity;
        this.currentRenderTargetIsMain = isMain;
        if (isMain) {
            // The main pass flushes with no target filter, so it has to know which identity "main" means: without this it drew
            // every pending group, including the ones offered for another framebuffer, which is why the off-target pass found
            // an empty queue every time (246 offered, 0 at pass time).
            this.mainTargetIdentity = targetIdentity;
        }
        // One line per distinct identity the game ever binds, bounded: this is the census that says whether any
        // framebuffer other than the default one (identity 0, reported by the unbind) and the game's own main one was
        // ever bound in this run. The chain pair is fed by exactly those identities - the entity outline's target is
        // the measured one - so a run whose census has only 0 and main cannot exercise that route, and the report has
        // to say that instead of leaving a zero counter to be read as "no draws arrived".
        // The route column is the one the backend actually takes, not the pair an identity would take if it were
        // off-target: the main framebuffer's identity is non-zero, so the old "pair=" column read
        // "main=true pair=chain-pair" - a label that named an image no main-identity draw goes to. The rule and this
        // separation live in GllessFrameTarget.routeLabelForBinding.
        if (this.framebufferBindCensus.size() < 12 && this.framebufferBindCensus.add(Long.valueOf(targetIdentity))) {
            record("framebuffer bind: identity=0x" + Long.toHexString(targetIdentity) + " main=" + isMain
                    + " route=" + GllessFrameTarget.routeLabelForBinding(targetIdentity, isMain)
                    + " pair=" + pairOfOffTargetIdentity(targetIdentity)
                    + " (distinct identities so far " + this.framebufferBindCensus.size() + ")");
        }
    }

    public void viewport(int x, int y, int width, int height) {
        // No flush here. Every queued group is drawn with the current target's own viewport and scissor, so a viewport
        // change cannot strand or invalidate one; it only makes *new* offers outside the full-target contract, and those
        // are refused before acceptance, where the ordering flush already happens. Flushing here as well cost a whole
        // bridge round trip per viewport change - four full-screen blits plus two depth conversions - for nothing.
        // Packed so the comparison in the group key is one test rather than four.
        this.currentViewportKey = ((long) x << 42) ^ ((long) y << 21) ^ ((long) width << 10) ^ (long) height;
    }

    public void beginWorldPass() {
        this.worldSnapshotValid = false;
        this.worldPassActive = true;
    }

    public void endWorldPass() {
        // Pass boundary for the font batching (task-59): a held glyph run belongs to the pass it was started in, so it
        // goes out here rather than being appended to whatever the next pass submits.
        celtium$flushPendingFontBatch("boundary");
        this.worldPassActive = false;
        // The world-to-GUI ordering rule (task-14/37). A group offered inside the world pass tests against the depth
        // the terrain pass wrote, and that depth is only valid until the GUI stage draws. One of the GUI stage's quads
        // is a full-screen ortho quad with the depth test and the depth write both on at z = 0.50 (measured,
        // CELTIUM_GROUP_BOUNDS: `fmt=2 worldPass=false vertices=4 ndc x[-1.00..1.00] y[-1.00..1.00] z[0.50..0.50]
        // state=[depthTest=1 depthWrite=1]`); once it has run, every pending world group fails its LEQUAL test
        // against 0.5 while its draw is still counted. That is exactly what the POSITION_NORMAL box measured: the
        // group accepted, closed and drawn 62/62 with no pixels, and the same group in the picture (3,909 px) when it
        // was the first thing drawn after the terrain pass. GL never sees this because the world is drawn before the
        // GUI; the window path had left the group pending across two later windows.
        //
        // So the boundary is where the order is restored: whatever is still pending here was offered for this world
        // pass (or is a leftover for another target, which the window's own target filter keeps out of this pass), and
        // it is drawn now, in a window whose depth is the terrain's. Groups offered after this point belong to the GUI
        // stage and still defer exactly as before - the rule adds an ordering boundary, it does not stop deferral.
        if (!this.immediateGroups.isEmpty()) {
            int pending = this.immediateGroups.size();
            this.flushPendingBeforeGlDraw();
            if (this.immediateGroups.size() < pending) {
                // Counted only when the flush actually drew something: the ordering call also returns early for an
                // empty queue, a framebuffer replacement and a stall, and a counter that moved on those would not
                // describe the rule. Measured with the full default mask: 1 per world frame, and the box is then in
                // the picture at the isolation leg's coordinates.
                this.worldPassBoundaryFlushes++;
            }
        }
    }

    /**
     * Captures the projection and camera transform for this frame, once, at the camera boundary.
     * <p>
     * This is the per-frame snapshot the world pass draws from: the projection is the one derived from Minecraft's own
     * numbers, and the camera transform is the modelview as the camera setup left it. It replaces the per-batch
     * {@code glGetFloatv} pairs that used to be read at every matrix change, and it is the read the plan records as still
     * present - one per frame, at a boundary, rather than two per batch anywhere.
     */
    /** Reports the swapchain's out-of-date state on stdout whenever it changes: the debug list is capped. */
    private void reportOutOfDateState() {
        int outOfDate = this.swapchain != null && this.swapchain.outOfDate() ? 1 : 0;
        int acquireOutOfDate = this.swapchain != null ? this.swapchain.acquireOutOfDate() : -1;
        if (outOfDate != this.lastReportedOutOfDate || acquireOutOfDate != this.lastReportedAcquireOutOfDate) {
            this.lastReportedOutOfDate = outOfDate;
            this.lastReportedAcquireOutOfDate = acquireOutOfDate;
            System.out.println("[Celtium] CELTIUM_OOD outOfDate=" + outOfDate + " acquireOutOfDate=" + acquireOutOfDate
                    + " presentFailures=" + this.immediatePresentFailures);
        }
    }

    public void captureWorldSnapshot() {
        // Frame boundary for the font batching (task-59): a held run must not survive into the next frame.
        celtium$flushPendingFontBatch("boundary");
        // BH-15, the safe half of BH-14/15/16. The flush draws the groups of whichever target is bound when it runs
        // (`flushImmediateWindow`'s binding-driven filter), so a group accepted for a target that is never bound again
        // would sit in this queue for the rest of the session and the image it was meant for would stay empty - which
        // is what the menu panorama and the loading screen's white window are. Such a group is dropped here, at the
        // frame boundary it was accepted in, and counted per identity, rather than left queued or silently re-routed
        // into another target's pass. The drain that would have drawn them per target was reverted (e30a23cd6)
        // because retargeting the same command buffer lost the device; drawing them needs a pass of its own.
        if (!this.immediateGroups.isEmpty()) {
            int dropped = 0;
            java.util.LinkedHashSet<Long> targets = new java.util.LinkedHashSet<Long>();
            for (ImmediateGroup group : this.immediateGroups) {
                dropped += group.batches;
                targets.add(Long.valueOf(group.target));
            }
            this.unflushedTargetGroupsDropped += dropped;
            for (Long target : targets) {
                this.unflushedTargetDroppedByTarget.merge(target, Integer.valueOf(1), Integer::sum);
            }
            if (this.unflushedTargetGroupsDropped <= this.unflushedTargetGroupsDroppedLogged + 3) {
                this.unflushedTargetGroupsDroppedLogged++;
                record("frame boundary dropped " + dropped + " accepted immediate batches whose target never got a"
                        + " pass (targets=" + targets + ", total " + this.unflushedTargetGroupsDropped + ")");
            }
            this.immediateGroups.clear();
            this.pendingImmediateIndices = 0;
        }
        // Frame boundary: reconcile what the frame that just ended accepted and drew, then start a new one. Everything
        // that is per frame - the window budget, these counters - is reset here and nowhere else.
        if (this.countersFrameId >= 0) {
            this.lastFrameAccepted = this.frameAccepted;
            this.lastFrameDrawn = this.frameDrawn;
        }
        this.frameId++;
        this.countersFrameId = this.frameId;
        // OPT-05: hand the frame its arena slot, and reclaim one only where GPU completion allows it. Done here
        // because this is the frame boundary (everything per frame is reset here and nowhere else), and done after
        // the previous window's wait, so a slot whose groups are all released is normally free by now.
        if (this.frameArena != null) {
            this.frameArena.beginFrame(this.frameId, VulkanContext.completedEpoch());
        }
        this.frameAccepted = 0;
        this.frameDrawn = 0;
        reportOutOfDateState();
        reportDirectMirrorRefreshes();
        this.immediateWindowsThisFrame = 0;
        if (!this.worldPassActive) {
            return;
        }
        float[] derived = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION);
        System.arraycopy(derived, 0, this.worldSnapshotProjection, 0, 16);
        if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            // The camera transform is mirrored from the hooked GlStateManager matrix calls, so it is already here:
            // the GL read would answer zeros, and a zeroed camera transform degenerates every world draw.
            System.arraycopy(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW), 0,
                    this.worldSnapshotModelView, 0, 16);
        } else {
            java.nio.FloatBuffer modelView = org.lwjgl.system.MemoryUtil.memAllocFloat(16);
            try {
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, modelView);
                modelView.get(this.worldSnapshotModelView);
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(modelView);
            }
        }
        this.worldSnapshotValid = true;
        // Seed the mirror's base: the projection derived from Minecraft's numbers and the camera transform as the camera
        // setup left it. Everything the pass then does through the hooked matrix calls - per-model, per-entity, per-TESR
        // pushes, translates, rotates and pops - accumulates on top of this base. Copying the snapshot into every batch's
        // matrices instead would throw those local transforms away and draw every model at the camera origin.
        this.immediateState.setMatrix(VulkanGlState.MATRIX_MODE_PROJECTION, this.worldSnapshotProjection);
        this.immediateState.setMatrix(VulkanGlState.MATRIX_MODE_MODELVIEW, this.worldSnapshotModelView);
        this.snapshotCaptures++;
        if (GROUP_BOUNDS_DIAGNOSTICS) {
            // The base every group of this pass is measured against, printed once per camera boundary: with the group's
            // own model-view and the rotations that arrived in between, the whole chain from the camera to the quad is
            // in the log, and "the base is wrong" can be told from "the rotation never arrived" without a second run.
            int cleared = this.rotationLog.size();
            this.rotationLog.clear();
            this.currentGroupRotations = "";
            System.out.println("[Celtium] CELTIUM_FRAME_BASE frame=" + this.frameId
                    + " mv=" + matrixText(this.worldSnapshotModelView)
                    + " proj=" + matrixText(this.worldSnapshotProjection)
                    + " rotationsCleared=" + cleared);
        }
    }

    /**
     * Whether a particle-family batch with this texture can have its descriptor built at all.
     * <p>
     * The probe pipeline is created once and kept: the descriptor's inputs are the texture and the lightmap, not the
     * pipeline's state key, so any particle pipeline answers the same question about them.
     */
    private boolean canBuildDescriptor(int boundTexture) {
        if (this.textureRegistry == null) {
            // Created on demand rather than only in the flush. This method runs as part of the acceptance decision, which
            // happens before any flush on the first frames - and dereferencing a null registry there threw, was caught, and
            // refused the batch with "copy failed: NullPointerException". The composite's layout arrives exactly then, which
            // is how twelve batches per run came to be mapped, in range, in the mask, with the right stride, and still
            // declined.
            this.textureRegistry = celtium$registry();
        }
        VulkanGlTexture texture = this.textureRegistry.get(boundTexture);
        if (this.lightmapTexture == null) {
            ensureLightmapMirror();
        }
        if (texture == null || this.lightmapTexture == null) {
            this.immediateLastRefusal = "the descriptor needs the light map and texture " + boundTexture + " (lightmap="
                    + (this.lightmapTexture != null) + " texture=" + (texture != null) + ")";
            return false;
        }
        if (!texture.hasFullContent()) {
            // A mirror that only ever received a partial write has undefined content outside that patch. Handing the batch to GL
            // is the honest outcome until the atlas or texture is supplied whole (audit A04).
            this.immediateLastRefusal = "the mirror for texture " + boundTexture
                    + " has never received its full content";
            this.partialMirrorRefusals++;
            return false;
        }
        try {
            if (this.descriptorProbe == null) {
                // The render pass has to be the one this batch will be recorded into, chosen exactly as
                // prepareImmediate chooses it: the last pass that ran when there is one, and the standalone
                // frame-compatible pass when there is not. lastUiPass is null until the first pass of the backend
                // instance runs and pipelineFor(null) returns null, so requiring a terrain pipeline here refused
                // every particle batch offered before that first pass - 35 of them in regr4-glless, all before the
                // first terrain pass, and all of them silently: this branch recorded no reason, so the tally read
                // "unrecorded (format=5 ...) stage=10" and could not be told from the conditions that do name
                // themselves. The draw path already substitutes the standalone pass for exactly this case
                // (prepareImmediate and the flush path both use the terrain pass only when there is one), so this is
                // the draw path's own rule rather than a relaxation of the gate; the bridge leg keeps refusing
                // because prepareImmediate refuses there too.
                VulkanTerrainPipeline ui = pipelineFor(this.lastUiPass);
                long renderPass;
                if (ui != null) {
                    renderPass = ui.renderPass();
                } else if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
                    renderPass = uiRenderPass();
                } else {
                    this.immediateLastRefusal = "the particle descriptor probe needs a pass and none has run yet"
                            + " (lastUiPass=" + nameOf(this.lastUiPass) + ")";
                    return false;
                }
                this.descriptorProbe = VulkanImmediateParticlePipeline.create(this.context, renderPass,
                        VulkanColorBlendState.of(false, 0, 0,
                                org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE,
                                org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                                org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD, org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD,
                                VulkanColorBlendState.allChannels()),
                        false, false, false, org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL);
            }
            boolean ok = celtium$descriptorSet(
                    this.descriptorProbe.descriptorSetFor(texture, this.lightmapTexture), "probe") != 0L;
            this.particleDescriptorFailureReason = this.descriptorProbe.lastFailure();
            return ok;
        } catch (Throwable error) {
            // A refusal has to name itself: the gate's caller only fills in its generic sentence when nothing was
            // recorded, so a silent false here reads as "unrecorded (format=5 ...) stage=10".
            this.immediateLastRefusal = "the particle descriptor probe could not be built: " + describe(error);
            return false;
        }
    }

    /**
     * The mirrored matrices, for the GL reads the stub has to answer: vanilla reads the terrain's projection and
     * model-view through {@code glGetFloatv} ({@code ActiveRenderInfo.updateRenderInfo}), and it builds the frustum
     * the chunk traversal culls with from the same two reads ({@code ClippingHelperImpl}). {@code VulkanGlState.matrix}
     * returns a copy, so a caller cannot write into the mirror through this.
     */
    /**
     * How many immediate batches this backend has been offered, as a monotonic ordering witness.
     * <p>
     * Read by the font's glyph batching ({@code CeltiumFontGlyphCapture}), which may hold a run of glyph quads and
     * submit them in one call - but only while nothing else is submitted in between. Between two glyphs of one string
     * nothing else draws; between two strings another batch can, and a held quad appended afterwards would be drawn on
     * top of that content instead of under it. Comparing this counter against the font's own submission count is what
     * makes the batching order-safe by construction, rather than assuming a string boundary the font cannot see.
     */
    public int celtium$immediateSequence() {
        return this.immediateBatchesOffered;
    }

    /**
     * Appends one font glyph quad to the backend-owned pending run (task-59), instead of submitting it on its own.
     * <p>
     * The run is flushed before anything that came after it can be submitted - at the entry of
     * {@link #submitImmediateBatch}, at every group/pass/frame boundary, and when one of the cheap state witnesses
     * changes - so a held quad is always appended before later content, never after it. The witnesses are the values
     * that can differ between two glyphs of a string without a matrix change: the bound texture, the current colour,
     * the alpha test and its reference, the format/stride/mode. A matrix change is not batched at all: the normal path
     * captures the matrices and the next glyph starts a new run, which keeps the run's state identical to what the
     * first quad of the run would have been submitted with.
     *
     * @return whether the quad was appended; false means the caller must submit it through the normal path
     */
    public boolean celtium$fontAppend(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                      int vertexCount, int drawMode) {
        long started = System.nanoTime();
        try {
            if (!FONT_BATCHING) {
                // The one-flag control: with batching off every glyph goes back to its own submitImmediateBatch call,
                // so a crash or a picture change can be attributed to the coalescing rather than to the tree.
                return false;
            }
            if (vertexAddress == 0L || vertexCount <= 0 || vertexStride <= 0) {
                return false;
            }
            if (this.gpuStalled || this.matricesDirty || !this.currentRenderTargetIsMain) {
                // The normal path owns the capture (matrices, stall handling, target routing); a run is only started
                // from a state that has already been captured.
                return false;
            }
            int texture = this.immediateState.texture2dEnabled()
                    ? this.immediateState.boundTextureName() : whiteTextureId();
            long colourBits = celtium$fontColourBits();
            boolean alphaTest = this.immediateState.alphaTestEnabled();
            float alphaRef = this.immediateState.alphaFuncReference();
            if (this.pendingFontActive
                    && !celtium$fontRunMatches(formatId, vertexStride, drawMode, texture, colourBits, alphaTest,
                            alphaRef)) {
                celtium$flushPendingFontBatch("state-change");
            }
            if (!this.pendingFontActive) {
                this.pendingFontFormat = vertexFormat;
                this.pendingFontStride = vertexStride;
                this.pendingFontFormatId = formatId;
                this.pendingFontDrawMode = drawMode;
                this.pendingFontTexture = texture;
                this.pendingFontColorBits = colourBits;
                this.pendingFontAlphaTest = alphaTest;
                this.pendingFontAlphaRef = alphaRef;
                System.arraycopy(this.glProjection, 0, this.pendingFontProjection, 0, 16);
                System.arraycopy(this.glModelView, 0, this.pendingFontModelView, 0, 16);
                this.pendingFontBytesUsed = 0;
                this.pendingFontVertexCount = 0;
                this.pendingFontActive = true;
                this.fontBatchesStarted++;
            }
            int bytes = vertexCount * vertexStride;
            celtium$ensurePendingFontCapacity(this.pendingFontBytesUsed + bytes);
            long destination = org.lwjgl.system.MemoryUtil.memAddress(this.pendingFontVertices, 0)
                    + this.pendingFontBytesUsed;
            // Copied, not aliased: the font's buffer is refilled for the next glyph.
            org.lwjgl.system.MemoryUtil.memCopy(vertexAddress, destination, bytes);
            this.pendingFontBytesUsed += bytes;
            this.pendingFontVertexCount += vertexCount;
            this.fontQuadsAppended++;
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.immediateLastRefusal = "the font run could not take a quad: " + error;
            return false;
        } finally {
            this.fontAppendNanos += System.nanoTime() - started;
        }
    }

    private long fontAppendNanos;

    /**
     * A matrix change is a boundary for the coalesced font run (task-59). The run's quads were appended while these
     * matrices were current, and the submission path captures the matrices at submit time - so a run held across a
     * matrix change would be drawn with the transform of whatever came last (the measured symptom with the batching
     * on: garbled menu button labels, a rotated splash and block-like copyright text). Flushing before the change is
     * applied keeps every quad with the matrices it was written for.
     */
    private void celtium$fontMatrixBoundary() {
        celtium$flushPendingFontBatch("state-change");
    }



    /** The current colour as the four float bits, packed into one long: the run's colour witness. */
    private long celtium$fontColourBits() {
        // Packed as four bytes, not as float bit patterns: the first version shifted each 32-bit pattern by 48/32/16/0
        // and masked the low 16 bits, which for ordinary colours (1.0f = 0x3F800000, 0.25f, 0.0f) is zero - the
        // witness was 0 for almost every colour, so runs merged across colour changes, including the shadow pass and
        // its light pass. The quantised byte is what the vertex data carries anyway.
        return ((long) celtium$colourByte(this.immediateState.colorRed()) << 24)
                | ((long) celtium$colourByte(this.immediateState.colorGreen()) << 16)
                | ((long) celtium$colourByte(this.immediateState.colorBlue()) << 8)
                | celtium$colourByte(this.immediateState.colorAlpha());
    }

    private static int celtium$colourByte(float value) {
        int quantised = Math.round(value * 255.0f);
        return quantised < 0 ? 0 : Math.min(quantised, 255);
    }

    private boolean celtium$fontRunMatches(int formatId, int stride, int drawMode, int texture, long colourBits,
                                           boolean alphaTest, float alphaRef) {
        // The matrix pair is part of the witness, exactly as it is for the normal path's group key (see the
        // projection/modelView comparison in the append path): a run that spans a matrix change is drawn with one
        // matrix for all of its quads. Leaving it out was measured as the garbled menu - the button labels, the rotated
        // splash text and the copyright line came out as blocks while the unscaled footer text stayed legible, because
        // the scaled and rotated draws were merged into a run opened under a different transform.
        return this.pendingFontFormatId == formatId && this.pendingFontStride == stride
                && this.pendingFontDrawMode == drawMode && this.pendingFontTexture == texture
                && this.pendingFontColorBits == colourBits && this.pendingFontAlphaTest == alphaTest
                && Float.compare(this.pendingFontAlphaRef, alphaRef) == 0
                && java.util.Arrays.equals(this.pendingFontProjection, this.glProjection)
                && java.util.Arrays.equals(this.pendingFontModelView, this.glModelView);
    }

    private void celtium$ensurePendingFontCapacity(int bytes) {
        java.nio.ByteBuffer buffer = this.pendingFontVertices;
        if (buffer != null && buffer.capacity() >= bytes) {
            return;
        }
        int capacity = Math.max(4096, Integer.highestOneBit(Math.max(1, bytes)) * 2);
        java.nio.ByteBuffer grown = org.lwjgl.system.MemoryUtil.memAlloc(capacity);
        if (buffer != null) {
            org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(buffer, 0),
                    org.lwjgl.system.MemoryUtil.memAddress(grown, 0), this.pendingFontBytesUsed);
            org.lwjgl.system.MemoryUtil.memFree(buffer);
        }
        this.pendingFontVertices = grown;
    }

    /**
     * Sends the pending run out through the normal submission path, and counts how it went: an intervening submission,
     * a group/pass/frame boundary, or a state change. The run is marked inactive <em>before</em> the submission so the
     * submission's own entry flush cannot see it again.
     */
    private void celtium$flushPendingFontBatch(String how) {
        if (!this.pendingFontActive) {
            return;
        }
        Object format = this.pendingFontFormat;
        int stride = this.pendingFontStride;
        int formatId = this.pendingFontFormatId;
        int drawMode = this.pendingFontDrawMode;
        int count = this.pendingFontVertexCount;
        long address = org.lwjgl.system.MemoryUtil.memAddress(this.pendingFontVertices, 0);
        // The state the run was appended under. `submitImmediateBatch` captures the bound texture and the matrices at
        // submission time, so a run flushed at an intervening submission used to be drawn with the *intervening* draw's
        // texture and transform. That is the measured garbling: the menu's button labels, the rotated splash text and the
        // copyright line came out as blocks while runs flushed at a boundary - the footer text, drawn last with nothing
        // after it - stayed legible. The recorded state is applied around the submission and restored afterwards.
        int runTexture = this.pendingFontTexture;
        boolean runAlphaTest = this.pendingFontAlphaTest;
        float runAlphaRef = this.pendingFontAlphaRef;
        this.pendingFontActive = false;
        this.pendingFontBytesUsed = 0;
        this.pendingFontVertexCount = 0;
        if ("intervening".equals(how)) {
            this.fontBatchesFlushedIntervening++;
        } else if ("boundary".equals(how)) {
            this.fontBatchesFlushedBoundary++;
        } else {
            this.fontBatchesFlushedStateChange++;
        }
        int savedTexture = this.immediateState.boundTextureName();
        boolean savedTexture2d = this.immediateState.texture2dEnabled();
        boolean savedAlphaTest = this.immediateState.alphaTestEnabled();
        float savedAlphaRef = this.immediateState.alphaFuncReference();
        float[] savedProjection = this.glProjection.clone();
        float[] savedModelView = this.glModelView.clone();
        try {
            this.immediateState.texture2dEnabled(true);
            this.immediateState.bindTexture(runTexture);
            this.immediateState.alphaTestEnabled(runAlphaTest);
            this.immediateState.alphaFuncReference(runAlphaRef);
            System.arraycopy(this.pendingFontProjection, 0, this.glProjection, 0, 16);
            System.arraycopy(this.pendingFontModelView, 0, this.glModelView, 0, 16);
            submitImmediateBatch(format, stride, formatId, address, count, drawMode);
        } catch (RuntimeException | LinkageError error) {
            this.immediateLastRefusal = "the coalesced font run could not be submitted: " + error;
        } finally {
            this.immediateState.texture2dEnabled(savedTexture2d);
            this.immediateState.bindTexture(savedTexture);
            this.immediateState.alphaTestEnabled(savedAlphaTest);
            this.immediateState.alphaFuncReference(savedAlphaRef);
            System.arraycopy(savedProjection, 0, this.glProjection, 0, 16);
            System.arraycopy(savedModelView, 0, this.glModelView, 0, 16);
        }
    }

    /** The font batching's own census, for the report: how much was coalesced and how each run went out. */
    public String celtium$fontBatchSummary() {
        return "fontRunsStarted=" + this.fontBatchesStarted
                + " fontQuadsAppended=" + this.fontQuadsAppended
                + " flushedIntervening=" + this.fontBatchesFlushedIntervening
                + " flushedBoundary=" + this.fontBatchesFlushedBoundary
                + " flushedStateChange=" + this.fontBatchesFlushedStateChange
                + " appendMillis=" + (this.fontAppendNanos / 1000000L);
    }

    public float[] celtium$mirroredModelView() {
        return this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW);
    }

    /** The mirrored projection; see {@link #celtium$mirroredModelView()}. */
    public float[] celtium$mirroredProjection() {
        return this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION);
    }

    /** The mirrored texture matrix of the active unit, which is what the mirror's texture mode addresses. */
    public float[] celtium$mirroredTexture() {
        return this.immediateState.matrix(VulkanGlState.MATRIX_MODE_TEXTURE);
    }

    /**
     * The colour the immediate path's mirror currently holds, as {r,g,b,a}.
     * <p>
     * Formats 6, 7 and 9 carry no colour in their vertices, so the group that draws them takes this value at the
     * moment the group opens - which makes it the only thing that says what colour a position-only batch was drawn
     * in. Read by the GL-less stub's own draw census ({@code CELTIUM_DRAWARRAY_LAYOUT}), where "the sky plane is
     * drawn in the wrong colour" and "the wrong geometry was submitted" look identical in the frame.
     */
    public float[] celtium$mirroredColor() {
        return new float[] {this.immediateState.colorRed(), this.immediateState.colorGreen(),
                this.immediateState.colorBlue(), this.immediateState.colorAlpha()};
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
        // The entry flush of the font batching (task-59): anything submitted while a glyph run is held means the run
        // came before this batch, so it goes out now - before this one is appended. This is the whole ordering
        // argument; without it a held quad would land after content that vanilla drew before it.
        celtium$flushPendingFontBatch("intervening");
        int refusedFormat = Integer.getInteger("celtium.vulkan.refuseImmediateFormat", -1).intValue();
        if (refusedFormat >= 0 && formatId == refusedFormat) {
            // Isolation switch: which family a wrong part of the picture belongs to, asked by removing one family at
            // a time rather than by reading the scene.
            this.immediateLastRefusal = "isolation: format " + formatId + " refused by property";
            this.refusedBeforeAppend++;
            return false;
        }
        // Cleared at entry so a refusal always carries its own reason. It used to keep whatever the previous call left -
        // a successful batch sets "none" - so 886 format-2 refusals per run were tallied under a reason that described the
        // batch before them, and the path that refused them was never named.
        // A non-null marker rather than null: one caller dereferences the reason, and null turned a stale reason into a
    // NullPointerException in the frame. "unrecorded" says the same thing without breaking that caller.
    // The marker carries what the batch is, so an unnamed refusal still says which kind of batch it was: with 967 of them
    // per run the tally could not tell one group from another. Naming the refusing condition itself is the next step; this
    // at least splits the group so that step has one target instead of one number.
    this.immediateLastRefusal = "unrecorded (format=" + formatId + " stride=" + vertexStride + " mode=" + drawMode
            + " count=" + vertexCount + ")";
        if (REFERENCE_RENDER) {
            // Same reason as the pass entry: this method is called by the uploader hook directly, so reference mode has to
            // refuse it here as well.
            this.immediateLastRefusal = "reference render";
            this.flushPendingBeforeGlDraw();
            return false;
        }
        // Content outside the world pass - the GUI, the font, the HUD, the hand - used to be refused here, which left it
        // to OpenGL. It does not need the world snapshot: the matrices a group is built from come from the live
        // GlStateManager mirror (glProjection/glModelView), which holds the orthographic projection outside the world pass
        // exactly as it holds the world projection inside it. So it is taken, and the counter says how much of the frame
        // arrives this way.
        if (!this.worldPassActive || !this.worldSnapshotValid) {
            this.outsideWorldPassAccepted++;
        }
        if (this.gpuStalled) {
            this.immediateLastRefusal = "the GPU stalled; no further drawing until the renderer is rebuilt";
            this.flushPendingBeforeGlDraw();
            return false;
        }
        if (!this.currentRenderTargetIsMain) {
            this.offTargetOffered++;
            this.offTargetBatchesByTarget.computeIfAbsent(this.currentRenderTarget,
                    ignored -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        }
        this.immediateBatchesOffered++;
        if (formatId >= 0 && formatId < this.immediateOfferedByFormat.length) {
            this.immediateOfferedByFormat[formatId]++;
        }
        long submitStart = System.nanoTime();
        try {
            boolean taken = submitImmediateBatchTimed(vertexFormat, vertexStride, formatId, vertexAddress,
                    vertexCount, drawMode);
            if (!taken && !this.currentRenderTargetIsMain) {
                // Refused in this very call: attribute the reason now, because the offers arrive in one burst at the end of a
                // run and there is no later call to sample from.
                this.offTargetRefusalsByReason.merge(this.immediateLastRefusal == null ? "unset" : this.immediateLastRefusal,
                        Integer.valueOf(1), Integer::sum);
            }
            return taken;
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
            this.immediateLastRefusal = "the GPU stalled; no further drawing until the renderer is rebuilt";
            return false;
        }
        if (!IMMEDIATE_DRAWING_ENABLED) {
            // Comparison switch, default on: turning it off makes every immediate batch fall back to GL, which is
            // exactly the reference image the Vulkan path has to reproduce.
            this.immediateLastRefusal = "immediate drawing disabled by celtium.vulkan.immediate=false";
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
        this.offerStage = 1;
        String reason = VulkanImmediateDrawer.unsupportedReason(drawMode);
        if (reason != null && VulkanImmediateDrawer.isLineMode(drawMode)) {
            // Lines are drawn, not refused. The refusal that used to be here was right about one thing - the
            // pipelines are built with .lineWidth(1.0f), so a 2 px request would be drawn one pixel wide - and wrong
            // about the consequence: under the GL-less window the refusal is not "drawn by OpenGL", it is drawn by
            // nobody, which is why the block selection outline (glLineWidth(2.0), POSITION_COLOR) and the fishing
            // rod's line are missing from the frame. The batch is now accepted at the width this renderer implements
            // and the difference is *reported* instead of being silent: lineBatchesNarrowed counts the batches whose
            // requested width was clamped, and the first few name the requested and drawn widths.
            //
            // The format restriction (it used to require formatId == 1) is gone with it: the fishing rod's line is a
            // line batch of another format, and the line pipeline's own layout is what decides whether a batch fits.
            float askedWidth = this.celtium$requestedLineWidth();
            if (askedWidth > 1.0F + 0.01F) {
                this.lineBatchesNarrowed++;
                if (this.lineBatchesNarrowed <= 4) {
                    System.out.println("[Celtium] CELTIUM_LINE_NARROWED requested=" + askedWidth
                            + " drawn=1.0 (device maximum " + this.celtium$maxLineWidth()
                            + " with wideLines) mode=" + drawMode + " format=" + formatId
                            + " - drawn rather than refused, because a refusal draws nothing under GL-less");
                }
            }
            reason = null;
        }
        if (reason != null) {
            this.immediateLastRefusal = reason;
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
        if (formatId == 5 && this.lightCoordLogged < 4 && vertexAddress != 0 && vertexCount > 0) {
            // The raw light coordinates of a particle batch, a few times. The shader's conversion is right and the layout is
            // right, so what is left is whether the input matches what GL would use - the lightmap is at offset 24 in this
            // layout. Values near 0 would mean the batch is unlit rather than lit differently.
            this.lightCoordLogged++;
            try {
                long at = vertexAddress + 24;
                int first = Short.toUnsignedInt(org.lwjgl.system.MemoryUtil.memGetShort(at));
                int second = Short.toUnsignedInt(org.lwjgl.system.MemoryUtil.memGetShort(at + vertexStride));
                int last = Short.toUnsignedInt(org.lwjgl.system.MemoryUtil.memGetShort(
                        vertexAddress + 24 + (long) (vertexCount - 1) * vertexStride));
                float vx = org.lwjgl.system.MemoryUtil.memGetFloat(at - 24);
                float vy = org.lwjgl.system.MemoryUtil.memGetFloat(at - 24 + 4);
                float vz = org.lwjgl.system.MemoryUtil.memGetFloat(at - 24 + 8);
                System.out.println("[Celtium] CELTIUM_FMT5_POS first=(" + round3(vx) + "," + round3(vy) + "," + round3(vz)
                        + ") camera=(" + round3(this.glModelView[12]) + "," + round3(this.glModelView[13]) + ","
                        + round3(this.glModelView[14]) + ")");
                System.out.println("[Celtium] CELTIUM_LIGHTCOORD fmt=5 stride=" + vertexStride + " count=" + vertexCount
                        + " first=0x" + Integer.toHexString(first) + "(" + first + ")"
                        + " second=0x" + Integer.toHexString(second) + "(" + second + ")"
                        + " last=0x" + Integer.toHexString(last) + "(" + last + ")"
                        + " u_first=" + round3((first & 0xFF) / 16.0f) + " v_first=" + round3(((first >> 8) & 0xFF) / 16.0f)
                        + " uraw_first=" + round3(first / 256.0f));
                // Every vertex field of the first two vertices, once: whether the zero lights travel with a colour that
                // is also zero (padding never written) or a real particle that happened to be unlit.
                if (this.lightCoordLogged == 1) {
                    float tu0 = org.lwjgl.system.MemoryUtil.memGetFloat(vertexAddress + 12);
                    float tv0 = org.lwjgl.system.MemoryUtil.memGetFloat(vertexAddress + 16);
                    int c0 = org.lwjgl.system.MemoryUtil.memGetInt(vertexAddress + 20);
                    int lu0 = Short.toUnsignedInt(org.lwjgl.system.MemoryUtil.memGetShort(vertexAddress + 24));
                    int lv0 = Short.toUnsignedInt(org.lwjgl.system.MemoryUtil.memGetShort(vertexAddress + 26));
                    long v1 = vertexAddress + vertexStride;
                    float tu1 = org.lwjgl.system.MemoryUtil.memGetFloat(v1 + 12);
                    float tv1 = org.lwjgl.system.MemoryUtil.memGetFloat(v1 + 16);
                    int c1 = org.lwjgl.system.MemoryUtil.memGetInt(v1 + 20);
                    int lu1 = Short.toUnsignedInt(org.lwjgl.system.MemoryUtil.memGetShort(v1 + 24));
                    int lv1 = Short.toUnsignedInt(org.lwjgl.system.MemoryUtil.memGetShort(v1 + 26));
                    System.out.println("[Celtium] CELTIUM_FMT5_VERTEX"
                            + " v0 uv=(" + round3(tu0) + "," + round3(tv0) + ") color=0x"
                            + Integer.toHexString(c0) + " lmap=(" + lu0 + "," + lv0 + ")"
                            + " v1 uv=(" + round3(tu1) + "," + round3(tv1) + ") color=0x"
                            + Integer.toHexString(c1) + " lmap=(" + lu1 + "," + lv1 + ")");
                    StackTraceElement[] stack = new Throwable().getStackTrace();
                    StringBuilder frames = new StringBuilder();
                    for (int i = 1; i < Math.min(stack.length, 14); i++) {
                        frames.append(" <- ").append(stack[i].getClassName()).append('.')
                                .append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
                    }
                    System.out.println("[Celtium] CELTIUM_FMT5_SITE" + frames);
                }
            } catch (RuntimeException | LinkageError error) {
                this.lightCoordLogged = 4;
            }
        }
        int expectedStride = formatId == 10 ? VulkanImmediateTexturePipeline.POSITION_NORMAL_BYTES
                : formatId == 9 ? VulkanImmediateTexturePipeline.POSITION_TEX_NORMAL_BYTES
                : formatId == 8 ? VulkanImmediateTexturePipeline.POSITION_TEX_COLOR_NORMAL_BYTES
                : formatId == 7 ? VulkanImmediateTexturePipeline.POSITION_BYTES
                : formatId == 6 ? VulkanImmediateTexturePipeline.POSITION_TEX_BYTES
                : formatId == 2 ? VulkanImmediateTexturePipeline.VERTEX_BYTES
                : formatId == 3 ? VulkanImmediateBlockPipeline.VERTEX_BYTES
                : formatId == 4 ? VulkanImmediateItemPipeline.VERTEX_BYTES
                : formatId == 5 ? VulkanImmediateParticlePipeline.VERTEX_BYTES
                : VulkanImmediatePipeline.VERTEX_BYTES;
        // The property name says "present from composite", and that is what it is for - but with the check below it
        // currently presents nothing: measured compositePresentRefusals 1/frame and no present at all, because the
        // batch Minecraft composites with is drawn to the game framebuffer rather than to the screen. What the switch
        // controls in practice is which format-2 batches the present path may take; everything else falls through to
        // the immediate path. Recorded so the name is not read as evidence that a present happens.
        this.offerStage = 2;
        if (formatId == 2 && SKIP_GL_COMPOSITE && this.framePresentedFromVulkan > 0
                && this.currentRenderTargetIsMain) {
            // The frame is already on the swapchain (a present really happened for it), so the game's own composite would
            // copy the same picture into the default framebuffer a second time. Taken and drawn as nothing: this is the
            // "another path already did this" evidence the cancellation rule asks for, and it is checked per frame.
            //
            // The second half of the condition is the design fix the glow needed. Under the stub the game's composite is
            // issued with no Framebuffer bound, so the target is identity 0 rather than the main identity - and the
            // finished frame is then copied onto ITSELF from the frame image's own colour texture. That copy used to be
            // taken and drawn into the renderer's off-target pair, which is also the image the entity-outline chain
            // adopts as its input: the chain was handed a copy of the whole frame instead of the silhouette, and the
            // composite added a dimmed mirrored copy of the scene over it (measured offTargetByTarget=[0:104 ...]).
            // Skipping it here is what the swap point already does; the world pass, which also arrives with identity 0,
            // is untouched because it does not sample the frame's own colour texture.
            this.glCompositesSkipped++;
            return true;
        }
        this.offerStage = 3;
        if (formatId == 2 && PRESENT_FROM_COMPOSITE && (this.currentRenderTargetIsMain
                || celtium$samplesBoundFramebufferColor(this.immediateState.boundTextureName()))) {
            // The audit's S17: this layout is not only the screen composite. `Framebuffer.func_178038_a` uses it to blend
            // an overlay *into* a framebuffer as well, and GUI, outlines, offscreen passes and postprocessing share it -
            // so presenting on the format alone cancels GL draws that were not composites. The two conditions that
            // distinguish the screen-only case are checked here, before anything is taken: the main framebuffer must be
            // the bound target, and the viewport must be the whole of it.
            // The audit asked for the framebuffer to be checked rather than the format alone. The stricter viewport
            // equality that was tried first turned out to refuse the legitimate composite as well - the game does not set
            // a viewport equal to the whole target at that moment - and with it the present never happened at all, which
            // the fixture noticed by never completing. The target check stays, because that is what distinguishes the
            // screen from an overlay blended into a framebuffer.
            // Reached only for a batch that is a candidate composite (the condition above). The earlier form refused
            // everything that was not bound to the screen, which sent the hand, overlays and mod batches to OpenGL -
            // and GL then drew them against a depth buffer the terrain had written, which is why the depth write-back
            // was needed at all. Those batches now fall through to the acceptance mask below (which accepts format 2)
            // and are drawn by the immediate path, so the depth return can go away: measured effect 0.0000 / 5-8 px
            // against a 0.0167 / 481 px floor on both GPUs once format 2 is taken natively.
            // Present the frame's content at the moment Minecraft composites it. The stack trace identified this batch
            // as Framebuffer.func_178038_a - the game framebuffer blitted onto the screen - and taking it cancels GL's
            // blit, so the screen shows the Vulkan present instead. It runs before the acceptance mask, which keeps
            // this layout on GL by default.
            try {
                // Under the GL-less window the swap point presents the finished frame, so this mid-frame present would
                // be a second one for the same frame - and it restores the colour image to a layout the owned target
                // does not use. The bridge configuration keeps it: that is where it was measured.
                if (this.frameTarget != null && ensureSwapchain()
                        && !org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
                    this.frameSubmitReachedQueue = false;
                    this.frameTarget.beginFrameForPresent();
                    long bridgeWait = this.frameTarget.waitSemaphore();
                    long bridgeDone = this.frameTarget.doneSemaphore();
                    int sourceLayout = this.frameTarget.colorImageLayout();
                    int restoredLayout = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                    String result = this.lastPresentResult = this.swapchain.presentImage(this.frameTarget.colorImage(), sourceLayout,
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
                    } else {
                        // The frame is open and its GL-to-Vulkan semaphore has been acquired, but nothing was submitted:
                        // endFrame() makes GL wait on the Vulkan-to-GL semaphore, so without a submission that signals it
                        // the render thread deadlocks - which is exactly what a forced out-of-date acquire produced when
                        // the failure path was first made reachable (audit S18).
                        submitSignalOnly();
                        this.frameTarget.endFrame();
                        this.emptyFrameReleases++;
                    }
                    if (!this.swapchain.lastPresentSucceeded()) {
                        this.immediatePresentFailures++;
                        this.immediateLastRefusal = "the composite present did not reach the screen";
                        record("composite present did not reach the screen, GL's composite stays: " + result);
                        return false;
                    }
                    if (this.swapchain.outOfDate()) {
                        // The swapchain no longer matches the surface. Dropping it is the whole rebuild: the next
                        // present calls ensureSwapchain(), which creates one against the surface as it is by then.
                        //
                        // OPT-04 contract, stated because this delete rests on a basis other than a device-wide idle:
                        // it runs inside the present path, *after* this frame's submission completed and its images
                        // were handed to the presentation engine (the fence this path waits on before reaching here),
                        // and the only resources it frees are the swapchain's own images, semaphores and surface
                        // wrapper - none of which this frame's command buffer still references, because the blit into
                        // them is part of the submission that already signalled. So the basis is "the submission that
                        // used these completed", which is the same guarantee settleGpu() would give, obtained from the
                        // present path's own fence instead of a queue wait. A future change that moves this delete
                        // before that wait, or that frees anything the frame's command buffer can still name, breaks
                        // the contract and needs the idle wait instead.
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
                    this.immediateLastRefusal = "presenting from the composite failed";
                    record("presenting from the composite failed: " + describe(error));
                }
            }
            // Under the GL-less window there is no GL composite to replace - the mid-frame present above is the
            // bridge's, and the swap point presents the finished frame - so this branch could only ever refuse. What
            // separates the two kinds of format-2 quad is the texture it samples: the frame's own blit samples this
            // target's own colour image and stays refused, because presenting it again is exactly the duplicate the
            // swap point already avoided; anything else is an overlay blended into the frame (the entity outline's
            // composite is the case) and belongs to the acceptance mask below. The question is answered from the map
            // the game's own framebuffer binds fill, because the stub cannot answer an attachment query.
            if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                    && !celtium$samplesCurrentTargetColor(this.immediateState.boundTextureName())) {
                this.immediateLastRefusal = null;
            } else {
                return false;
            }
        }
        if (this.immediateWindowBusy) {
            this.immediateLastRefusal = "reentrant upload during bridge handoff";
            return false;
        }
        this.offerStage = 4;
        if (!supportedImmediateTarget()) {
            // The gate names the failing condition itself; overwriting it here is what hid which one refused 820
            // format-2 batches in a run.
            flushPendingBeforeGlDraw();
            return false;
        }
        this.offerStage = 5;
        if (!celtium$formatAccepted(formatId)) {
            // Attribution switch: restricting which layouts this backend accepts makes the image difference
            // measurable per format instead of as one aggregate number. Refused batches are drawn by GL as usual.
            // The name is carried beside the number: "format 9 not in celtium.vulkan.immediateFormats" does not say which
            // of the vanilla layouts the mask is holding back, and the mask is a comma-separated list of numbers.
            this.immediateLastRefusal = "format " + formatId + " ("
                    + VulkanImmediateFormats.nameOf(formatId) + ") not in celtium.vulkan.immediateFormats";
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
        this.offerStage = 6;
        if (formatId < 1 || formatId > VulkanImmediateFormats.POSITION_NORMAL) {
            // The layout is identified structurally by the Mixin (element list, not class or stride), so an unknown
            // one is refused with its size rather than drawn with a pipeline that would misread its vertices. The upper
            // bound was 5, written before id 6 existed: position-plus-texture - the sky, the clouds, the water surface -
            // passed the mapping, passed the acceptance mask, and was then refused here as an unknown layout by a range
            // check that simply predated the format. It is the highest id the formats class declares, not a literal, for
            // the same reason: the literal was the defect.
            // Id 0 is the one case where the number says nothing at all: it is what the uploader passes when no layout
            // in the table matched, so the refusal has to point at the line that carries the shape rather than repeat
            // "no such layout". Measured: 138 offers in one GL-less leg read as `id0=138/0` here while the uploader's
            // own CELTIUM_UNMAPPED line named them as PARTICLE_POSITION_TEX_COLOR_LMAP written `2,UV,Short` - two
            // layout constants said "Unsigned Short" (USHORT's name) for an element whose type is SHORT, and this
            // refusal was the end of a path that never mentioned the element list.
            this.immediateLastRefusal = formatId == 0
                    ? "no native layout for a " + vertexStride + "-byte vertex (id 0: the uploader's CELTIUM_UNMAPPED"
                    + " and CELTIUM_UNMAPPED_LAYOUT lines carry its element list, width, count and indices)"
                    : "no pipeline yet for a " + vertexStride + "-byte vertex (id " + formatId + ", no such layout)";
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
        this.offerStage = 7;
        if (vertexStride != expectedStride) {
            // Both sides named: the layout the mapping chose and the width it actually has. The pair disagreed in silence
            // for two layouts - id 9 was accepted at 24 bytes while its format is 28 - and a refusal that reads
            // "id 9 has stride 28, expected 24" is what names the constant to fix.
            this.immediateLastRefusal = "layout id " + formatId + " ("
                    + VulkanImmediateFormats.nameOf(formatId) + ") has stride " + vertexStride + ", expected "
                    + expectedStride;
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
        if (formatId == 3 || formatId == 5) {
            ensureLightmapMirror();
        }
        if ((formatId == 3 || formatId == 5) && this.lightmapTexture == null) {
            // Which of the two conditions kept the mirror from being taken is named, so this does not end as a branch
            // that never fires with no way to tell whether it is unreachable or merely rare.
            this.immediateLastRefusal = "lightmapped batch with no lightmap texture mirrored (id=" + this.lightmapGlId
                    + " unit=" + this.lightmapUnit + ")";
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
        this.offerStage = 8;
        if (formatId >= 2 && this.immediateState.boundTextureName() == 0 && !celtium$layoutHasNoTexture(formatId)) {
            this.immediateLastRefusal = "textured batch with no texture bound";
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
        if (formatId == 3 && this.lightmapTexture == null) {
            // The BLOCK family samples the lightmap, so a missing mirror means the batch would be lit wrongly.
            this.immediateLastRefusal = "block batch with no lightmap texture mirrored";
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
        int[] indices = VulkanImmediateDrawer.expandIndices(drawMode, vertexCount);
        if (indices == null) {
            this.immediateLastRefusal = "topology " + drawMode + " has no expansion";
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
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
            this.currentImmediateTexture = boundTexture;
            if (boundTexture != 0 && VulkanTextureFault.shouldFail("immediate")) {
                this.textureUploadFailures++;
                this.lastTextureUploadFailure = "immediate " + boundTexture;
                this.immediateLastRefusal = "texture upload fault";
                this.flushPendingBeforeGlDraw();
                return false;
            }
            if (!this.immediateState.texture2dEnabled() || celtium$layoutHasNoTexture(formatId)) {
                // Vanilla draws the sky with texturing switched off and the gradient in the vertex colours. Sampling
                // the texture that happens to be bound then washes that out - measured as the sky going flat, 74.7% of
                // the top third of the frame differing. A white 1x1 mirror leaves the vertex colours as they are.
                // The same substitution covers the layouts that have no texture coordinate at all (POSITION and
                // POSITION_NORMAL): their shaders declare no sampler, so any image is correct, and the white one is the
                // only one this path is guaranteed to be able to mirror.
                boundTexture = whiteTextureId();
            }
            long matrixCopyStart = System.nanoTime();
            if (MIRRORED_MATRICES) {
                System.arraycopy(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION), 0,
                        this.glProjection, 0, 16);
                System.arraycopy(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW), 0,
                        this.glModelView, 0, 16);
                this.matricesDirty = false;
            }
            this.matrixCopyNanos += System.nanoTime() - matrixCopyStart;
            long captureStart = System.nanoTime();
            int[] state = celtium$captureState();
            this.captureStateNanos += System.nanoTime() - captureStart;
            long keyStart = System.nanoTime();
            long stateKey = celtium$stateKey(formatId, state);
            if (VulkanImmediateDrawer.isLineMode(drawMode)) {
                // A bit of its own, so the preflight can pick the line pipeline without being told the mode again.
                stateKey |= 1L << 61;
            }
            this.stateKeyNanos += System.nanoTime() - keyStart;
            // Split of the 1.87 ms per batch call: the preflight (pipeline and descriptor preparation) against the rest
            // of the body (which the existing immediateSubmitNanos already times as a whole).
            long preflightStart = System.nanoTime();
            long preflightCpuStart = threadCpuNanos();
            // A texture nothing has announced yet gets its one full supply here, before the preflight that refuses when no
            // mirror exists. Entity and TESR models sample their own skins and none of the upload hooks see them, so without
            // this their offers could never be accepted however correct the rest of the path was. Once per GL id.
            org.soutalab.celtium.vulkan.VulkanGlTexture existing =
                    boundTexture == 0 ? null : celtium$registry().get(boundTexture);
            // Two cases, one supply: no mirror at all (entity and TESR models sample their own skins and no upload hook
            // sees them) and a mirror that has only ever received a partial write, whose content outside that patch is
            // undefined (audit A04). The second was missing, so a partially patched texture kept every batch going to GL -
            // measured as 801 format-5 refusals per run, all reading "the mirror for texture N has never received its
            // full content" - while the fix was the same full upload that was already written here.
            if (boundTexture != 0 && (existing == null || !existing.hasFullContent())) {
                int[] size = textureSize(boundTexture);
                if (size[0] > 0 && size[1] > 0) {
                    try {
                        org.soutalab.celtium.vulkan.VulkanGlTexture created =
                                celtium$registry().imageFor(boundTexture, size[0], size[1], true);
                        created.uploadFromGlTexture(boundTexture, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                        celtium$registry().markFullyInitialized(boundTexture);
                        this.lazyMirrorSupplies++;
                        if (existing != null) {
                            this.partialMirrorsCompleted++;
                        }
                    } catch (RuntimeException | LinkageError error) {
                        this.lazyMirrorFailures++;
                        this.lastLazyMirrorFailure = boundTexture + ": " + error;
                    }
                }
            }
            PreparedImmediate prepared = prepareImmediate(formatId, boundTexture, stateKey, state);
            long preflightCpuEnd = threadCpuNanos();
            this.prepareImmediateNanos += System.nanoTime() - preflightStart;
            if (preflightCpuStart >= 0 && preflightCpuEnd >= 0) this.preflightCpuNanos += preflightCpuEnd - preflightCpuStart;
            this.prepareImmediateCalls++;
            if (prepared == null) {
                // The reason comes from prepareImmediate, which is the one place that knows which of its conditions failed.
                this.immediateLastRefusal = "preflight: " + this.lastPrepareFailure;
                this.refusedBeforeAppend++;
                flushPendingBeforeGlDraw();
                return false;
            }
            // The pass identity joins the key here: a batch from another render target or viewport is not part of this
            // group, which is what stops two passes merging into one draw (audit S10).
            boolean samePass = this.currentGroupTarget == this.currentRenderTarget
                    && this.currentGroupViewport == this.currentViewportKey;
            if (!samePass && this.currentImmediateMesh != null) {
                this.passSplits++;
            }
            // BH-09's group rule, completed: the clip rectangle is command state, so it must not go into the
            // pipeline key - but it is per-draw state, so a batch offered under a different rectangle cannot join
            // this group either. The rectangle is compared against the one captured when the group opened, at the
            // offer, and the four value readers are used rather than scissorBox() because this runs on every batch
            // and that method builds an array.
            boolean liveScissorApplies = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                    && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorTest()
                    && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorCalled();
            boolean scissorMoved = GROUP_SCISSOR && this.currentImmediateMesh != null
                    && !celtium$sameScissor(liveScissorApplies);
            if (scissorMoved) {
                this.scissorBoundarySplits++;
            }
            if (this.currentImmediateMesh == null
                    || this.currentGroupFormatId != formatId || this.currentGroupTexture != boundTexture
                    || this.currentGroupStateKey != stateKey || !samePass
                    || scissorMoved
                    || this.currentPreparedImmediate == null || this.currentPreparedImmediate.pipeline != prepared.pipeline
                    || this.currentPreparedImmediate.descriptor != prepared.descriptor
                    || !java.util.Arrays.equals(this.currentGroupProjection, this.glProjection)
                    || !java.util.Arrays.equals(this.currentGroupModelView, this.glModelView)
                    // BH-04: the colour is part of the group's state, so a draw that arrives with a different current
                    // colour cannot join it - the colourless layouts carry it as the tint, and a batch appended after
                    // a glColor change used to be drawn with the previous colour. Compared only for the formats that
                    // take the colour from the mirror and only when the mirror has a value, which is exactly when the
                    // capture below reads it.
                    || ((formatId == 7 || formatId == 6 || formatId == 9) && this.colorMirrorHasValue
                            && this.celtium$colourDiffers())
                    // BH-04, the other two halves: the texture affine and the fog are per-draw state as well, and both
                    // are captured once when the group opens. A batch offered after either changed used to be appended
                    // to the open group and drawn with the earlier value - the glint's scrolling affine and a quad drawn
                    // under a different fog bracket are the two shapes this closes. Both comparisons are null-safe:
                    // null means "identity affine" and "fog off" respectively, and those are states to compare too.
                    || this.celtium$textureAffineMoved()
                    || this.celtium$fogMoved()) {
                // A new matrix pair means a new group: mixing them is what placed geometry off-screen.
                closeCurrentImmediateGroup();
                long groupCreateStart = System.nanoTime();
                long groupCreateCpuStart = threadCpuNanos();
                this.currentImmediateMesh = celtium$newImmediateMesh();
                long groupCreateCpuEnd = threadCpuNanos();
                this.groupCreateNanos += System.nanoTime() - groupCreateStart;
                if (groupCreateCpuStart >= 0 && groupCreateCpuEnd >= 0) this.groupCreateCpuNanos += groupCreateCpuEnd - groupCreateCpuStart;
                this.groupCreateCount++;
                // The target and viewport belong to the group being created, here, and not to whichever group happened to be
                // first: they were recorded in an `if (!currentGroupUsed)` block that the creation path never reset, so every
                // group after the first kept the first group's target - the main framebuffer - and the batches offered while a
                // second framebuffer was bound were enqueued as main-target work. That is why the off-target pass found an
                // empty queue every run (audit A10/A32). Resetting the flag keeps the older path consistent as well.
                this.currentGroupTarget = this.currentRenderTarget;
                this.currentGroupViewport = this.currentViewportKey;
                this.currentGroupUsed = false;
                this.currentGroupFog = null;
                this.currentGroupScissor = null;
                this.currentGroupScissorEnabled = false;
                this.currentGroupBatches = 0;
                this.currentGroupViewportValues = currentViewport();
                this.currentPreparedImmediate = prepared;
                System.arraycopy(this.glProjection, 0, this.currentGroupProjection, 0, 16);
                System.arraycopy(this.glModelView, 0, this.currentGroupModelView, 0, 16);
                if (GROUP_BOUNDS_DIAGNOSTICS) {
                    // Captured with the matrices, not read at close time: the log belongs to the moment the group's
                    // matrices were taken, and a later group's rotations must not be attributed to this one.
                    this.currentGroupRotations = String.join(" ", this.rotationLog);
                }
                this.currentGroupFormatId = formatId;
                this.currentGroupTexture = boundTexture;
                this.currentGroupStateKey = stateKey;
                // The group's own copy of the captured state. Without this the field stayed at its initialised
                // new int[7], closeCurrentImmediateGroup stored that zero array, and every group was drawn with blend,
                // depth test, depth write, culling and the alpha reference all off - a state-keyed pipeline cache whose
                // keys were right and whose values were empty. Cloned rather than referenced so the snapshot cannot be
                // overwritten by the next capture.
                long cloneStart = System.nanoTime();
                this.currentGroupState = state.clone();
                this.groupCloneNanos += System.nanoTime() - cloneStart;
                this.groupCloneCount++;
                // The fog this batch was offered under, taken here for the same reason the state above is: at this
                // moment the live fog state is the batch's own, and by the time the group closes the game may have
                // bracketed the batch with enable/disable (the sky dome is exactly that case).
                this.currentGroupFog = celtium$fogSnapshot();
                // BH-09/OPT-10: the clip rectangle the batch was offered under, captured here rather than read at
                // flush time. Reading it live applied the last rectangle of a scissor change to every batch in the
                // group, which is the same "state must reach the group" rule the fog and the state follow.
                int[] liveScissor = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                        ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorBox() : null;
                this.currentGroupScissor = liveScissor == null ? null : liveScissor.clone();
                this.currentGroupScissorEnabled = liveScissor != null
                        && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorTest();
                // BH-05: the lighting belongs to the batch that opened the group, so it is read from the state saved
                // two lines above while that state is still the live one. Reading it at close time instead let a later
                // light change shade vertices that had already been accepted.
                this.currentGroupLightParams = celtium$lightParams(this.currentGroupState, this.currentGroupModelView);
                // The light-map coordinate belongs to the batch as well, and it is decided here rather than at close
                // for the same reason and one more: this is the only moment at which `worldPassActive` still describes
                // the pass the batch was offered in (see currentGroupLightmapCoords).
                this.currentGroupLightmapCoords = celtium$lightmapForGroup();
                // Translucency is a state rather than a layout, so the per-format counters cannot see it. This pair can, and it
                // has to be counted here rather than where the flag is read for pipeline selection: at flush time the state
                // belongs to the group being flushed and blending is on for the whole family, which made the first version of
                // this instrument report every acceptance as blended. Here the state is the batch's own.
                // The live state rather than a slot in the captured array: at this moment - the batch being offered - the live
                // state is the batch's own. Reading state[0] here instead reported every batch as blended, which is how the
                // first attempt at this went wrong twice over: once by sampling at flush time and once by guessing the slot.
                if (this.immediateState.blendMixes()) {
                    this.immediateBlendedAccepted++;
                } else {
                    this.immediateOpaqueAccepted++;
                }
                // Position-only and colourless-textured content has no colour in its vertices: the game draws it with
                // whatever colour it had current, so that is read here, at the moment the batch is offered, and
                // carried with the group. Formats 6 and 9 (POSITION_TEX and its normal sibling) need the same tint -
                // they used to drop it, which is what a glColor-tinted sky or water quad would have lost.
                // The alpha is carried too, as the fourth element. The colourless shaders multiplied the texture by
                // the current RGB and hardcoded the fragment alpha to 1.0, so a blended draw in one of these layouts
                // came out opaque: measured on the underwater overlay (ItemRenderer.renderWaterOverlayTexture, a
                // full-screen POSITION_TEX quad drawn with SRC_ALPHA/ONE_MINUS_SRC_ALPHA and
                // GlStateManager.color(f, f, f, 0.5F) where f is the light at the eye - 0 five blocks under water),
                // the whole frame was one colour, pure black, against a dark blue underwater scene under real GL.
                // It is also what the stub's own refusal text said for another draw: "the group colour's alpha is
                // 0.046 ... the position shaders hardcode the fragment alpha to 1.0, so this translucent batch would
                // be drawn opaque". The four elements reach the shaders at offset 96 (the block's spare vec4, which
                // nothing reads today): writing the alpha is only half the fix, the two colourless vertex shaders
                // have to read it.
                if ((formatId == 7 || formatId == 6 || formatId == 9) && this.colorMirrorHasValue) {
                    // No GL query: the colour the game last set, from the same state mirror everything else uses.
                    this.colorMirrorUses++;
                    this.currentGroupColor = new float[] {this.immediateState.colorRed(),
                            this.immediateState.colorGreen(), this.immediateState.colorBlue(),
                            this.immediateState.colorAlpha()};
                } else if (formatId == 7 || formatId == 6 || formatId == 9) {
                    java.nio.FloatBuffer colour = org.lwjgl.system.MemoryUtil.memAllocFloat(4);
                    try {
                        org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_CURRENT_COLOR, colour);
                        this.currentGroupColor = new float[] {colour.get(0), colour.get(1), colour.get(2),
                                colour.get(3)};
                    } catch (RuntimeException | LinkageError error) {
                        this.currentGroupColor = new float[] {1.0f, 1.0f, 1.0f, 1.0f};
                    } finally {
                        org.lwjgl.system.MemoryUtil.memFree(colour);
                    }
                } else {
                    this.currentGroupColor = null;
                }
                // The blend factors as they were when this group opened, captured here rather than read from the global
                // observation at draw time: the global value is whatever the most recent blendFunc call anywhere left, and
                // using it gave 0.31% against these factors' 0.21-0.23%. This is the per-group snapshot the audit's S07 asks
                // for, and it lives beside the state key for the same reason the matrices do.
                this.currentGroupBlendSrc = this.blendObserved ? this.observedBlendSrc
                        : org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
                this.currentGroupBlendDst = this.blendObserved ? this.observedBlendDst
                        : org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
                Long pair = Long.valueOf((((long) this.currentGroupBlendSrc) << 20) | this.currentGroupBlendDst);
                Integer seen = this.blendPairs.get(pair);
                this.blendPairs.put(pair, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
                this.currentGroupTarget = this.currentRenderTarget;
                this.currentGroupViewport = this.currentViewportKey;
                this.currentGroupTexMatrix = celtium$textureAffine();
            }
            // Every preflight comes before the append (audit A08). It used to run after `copyVertices` had already put the
            // vertices in the pending mesh, so a refused batch was drawn by GL **and** left sitting in the Vulkan queue,
            // ready to be flushed as a second copy of the same geometry. Refusing before anything is appended needs no
            // rollback and cannot double-draw.
            if (this.textureRegistry == null) {
                // The other place the acceptance needs it: this check runs while a batch is being offered, before any flush
                // has created the registry. Fixing only the descriptor probe left this one throwing, which the instrument
                // showed by reporting the identical reason after the first fix.
                this.textureRegistry = celtium$registry();
            }
            // A batch drawn by a shader cannot be reproduced by this renderer's immediate pipeline, and taking it was
            // expensive in a way the frame showed: Minecraft's post-process chain (ShaderGroup) draws several
            // full-screen passes with real programs, each of which mirrored its source texture - measured at 898
            // readbacks, about 15 per frame, which is what turned the spectator spider scene into ~2 s per frame and
            // left the run unable to reach its capture tick on either GPU. Refused before anything is appended, so GL
            // keeps drawing those passes with the program that is actually bound.
            long programQueryStart = System.nanoTime();
            int boundProgram = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM);
            this.programQueryNanos += System.nanoTime() - programQueryStart;
            this.programQueryCount++;
            if (boundProgram != 0) {
                // 8 is the entity layout: position, texture, colour and a normal the shaders do not read.
                this.offerStage = 9;
        if (formatId == 8 && !this.entityProgramLogged) {
                    // The entity layout is refused in every scene (acceptedByFormat 8:0). Which program is bound when it
                    // is offered decides whether the custom-program shutter is what keeps entities on GL - and that is
                    // what the depth write-back depends on.
                    this.entityProgramLogged = true;
                    System.out.println("[Celtium] CELTIUM_ENTITY_REFUSED program=" + boundProgram
                            + " texture=" + boundTexture);
                    StackTraceElement[] entityStack = new Throwable().getStackTrace();
                    StringBuilder entityFrames = new StringBuilder();
                    for (int i = 1; i < Math.min(entityStack.length, 10); i++) {
                        entityFrames.append(" <- ").append(entityStack[i].getClassName()).append('.')
                                .append(entityStack[i].getMethodName()).append(':').append(entityStack[i].getLineNumber());
                    }
                    System.out.println("[Celtium] CELTIUM_ENTITY_REFUSED_SITE" + entityFrames);
                }
                this.immediateLastRefusal = "a custom shader program is bound";
                // Named once per program: the spider scene refuses 1470 batches here, and which program they belong to
                // decides whether the entity path can be supported natively at all (which is what would let the depth
                // write-back go). The id is the only handle GL offers; the count says how dominant it is.
                // The vertex layout matters as much as the program: the vanilla outline group's mask pass draws entity
                // models while the blur and composite passes are full-screen quads, so which format each refused program
                // uses says which of them a native pass has to reproduce.
                String programKey = boundProgram + "/fmt" + formatId;
                Integer seenCount = this.shaderProgramRefusedIds.get(programKey);
                this.shaderProgramRefusedIds.put(programKey,
                        Integer.valueOf(seenCount == null ? 1 : seenCount.intValue() + 1));
                Integer seenProgram = this.shaderProgramRefusedPrograms.get(Integer.valueOf(boundProgram));
                this.shaderProgramRefusedPrograms.put(Integer.valueOf(boundProgram),
                        Integer.valueOf(seenProgram == null ? 1 : seenProgram.intValue() + 1));
                if (seenCount == null) {
                    System.out.println("[Celtium] CELTIUM_SHADER_PROGRAM_REFUSED program=" + boundProgram
                            + " format=" + formatId
                            + " (first batch refused with this program bound)");
                    // The call site, once per program: the id and the format say a custom program drew coloured quads,
                    // which is not enough to know what to support. The stack names it.
                    StackTraceElement[] stack = new Throwable().getStackTrace();
                    StringBuilder frames = new StringBuilder();
                    for (int i = 1; i < Math.min(stack.length, 9); i++) {
                        frames.append(" <- ").append(stack[i].getClassName()).append('.')
                                .append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
                    }
                    System.out.println("[Celtium] CELTIUM_SHADER_PROGRAM_REFUSED_SITE" + frames);
                }
                this.immediateLastRefusal = "no shader program could be prepared for this batch";
                this.shaderProgramRefusals++;
                this.refusedBeforeAppend++;
                this.flushPendingBeforeGlDraw();
                return false;
            }
            if (this.textureRegistry.get(boundTexture) == null) {
                this.immediateLastRefusal = "the bound texture could not be mirrored";
                this.textureRefusalsBeforeAccept++;
                this.refusedBeforeAppend++;
                // Order (S10): what is already accepted is drawn before this batch goes to GL.
                this.flushPendingBeforeGlDraw();
                return false;
            }
            this.offerStage = 10;
        if (formatId == 5 && !canBuildDescriptor(boundTexture)) {
                // The particle family is the one that dropped: every post-accept drop counted so far is format 5, which is
                // the family whose descriptor needs the lightmap. Building it here, once, turns that drop into an ordinary
                // refusal - before acceptance, where GL still owns the draw. The descriptor depends on the textures rather
                // than on the pipeline's state key, so one pipeline is enough to ask the question.
                // The descriptor gate names what it was missing; overwriting it here is what kept 751 format-5
                // refusals per run reading as one unexplained sentence.
                if (this.immediateLastRefusal == null || this.immediateLastRefusal.isEmpty()) {
                    this.immediateLastRefusal = "the particle descriptor could not be built for this lightmap";
                }
                this.particleDescriptorRefusals++;
                this.refusedBeforeAppend++;
                // Order (S10): what is already accepted is drawn before this batch goes to GL.
                this.flushPendingBeforeGlDraw();
                return false;
            }

            long appendStart = System.nanoTime();
            long appendCpuStart = threadCpuNanos();
            boolean appended = this.currentImmediateMesh.copyVertices(vertexAddress, vertexStride, vertexCount, indices);
            long appendCpuEnd = threadCpuNanos();
            this.appendNanos += System.nanoTime() - appendStart;
            if (appendCpuStart >= 0 && appendCpuEnd >= 0) this.appendCpuNanos += appendCpuEnd - appendCpuStart;
            this.appendCount++;
            if (!appended) {
                this.immediateLastRefusal = "the pending batch buffer refused the vertices";
                // Order (S10): what is already accepted is drawn before this batch goes to GL.
                this.flushPendingBeforeGlDraw();
                return false;
            }
            this.currentGroupUsed = true;
            this.currentGroupBatches++;
            this.pendingImmediateIndices += indices.length;

            this.immediateTaken++;

            if (!this.currentRenderTargetIsMain) {
                // Offered for another framebuffer and accepted: the number that says whether these batches are taken at all,
                // since the queue never sees a non-main group afterwards (audit A10).
                this.acceptedForOtherTargets++;
                if (this.currentRenderTarget != 0L) {
                    this.lastDrawnOffTargetIdentity = this.currentRenderTarget;
                    this.lastDrawnOffTargetFrame = this.frameId;
                }
            }

            this.offerStage = 11;
            if (formatId >= 0 && formatId < this.acceptedByFormat.length) {

                this.acceptedByFormat[formatId]++;

            }
            this.frameAccepted++;
            if (IMMEDIATE_WINDOWS_ENABLED && this.pendingImmediateIndices >= IMMEDIATE_FLUSH_INDICES) {
                try { flushImmediateWindow(); }
                catch (RuntimeException | LinkageError error) {
                    // Bounded, not latched (task-60). This catch used to call noteStalled(), which stops the renderer
                    // for the rest of the session: measured on the loading screen's font path at frame 0, where the
                    // latch then refused 678 of 723 draws and every later composite. A latch is only right for a
                    // device that has genuinely stopped - and that path is the wait timeout, which still latches. A
                    // window flush that throws is bounded to the window it belongs to: its batches are dropped and
                    // counted, the frame is abandoned, and the next frame tries again.
                    this.windowFlushFailures++;
                    this.lastWindowFlushFailure = describeWithOrigin(error);
                    this.windowFlushFailureFrame = this.frameId;
                    // Retained, not dropped. The frame is abandoned, so a group whose recording already sits in the
                    // abandoned command buffer has to be recorded again - and the abort branch's own contract is the
                    // pattern: take the recording's counters back off, add its indices back, and put it at the front of
                    // the queue. The unrecorded remainder never left the queue and stays where it is. Clearing the
                    // whole queue here is what erased the window's entity and held-item batches; the drop was counted,
                    // but the per-format histogram has no loss column, so it read as "accepted and never drawn".
                    for (ImmediateGroup group : this.recordedImmediateGroups) {
                        int batches = group.batches;
                        this.pendingImmediateIndices += group.mesh.indexCount();
                        this.immediateRecordedBatches -= batches;
                        if (group.target != this.mainTargetIdentity) this.offTargetRecordedBatches -= batches;
                    }
                    this.immediateGroups.addAll(0, this.recordedImmediateGroups);
                    this.recordedImmediateGroups.clear();
                    int retained = 0;
                    for (ImmediateGroup group : this.immediateGroups) {
                        retained += group.batches;
                    }
                    this.windowFlushRetainedBatches += retained;
                    // The frame is left open by the failure, and the semaphores it would wait on are never consumed;
                    // abandoning it keeps the next beginFrame() from reporting an open frame instead of the failure.
                    this.frameTarget.abandonFrame("immediate window submit failure");
                    record("window flush failed at frame " + this.frameId + " (" + this.lastWindowFlushFailure
                            + "); retained " + retained + " accepted batches, none dropped, the next frame retries"
                            + " (windowFlushFailures=" + this.windowFlushFailures + ")");
                }
            }
            if (this.immediateBatchesOffered % 500 == 0 && VulkanDiagnostics.enabled()) {
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
                        + " keptForOtherTargets=" + this.immediateGroupsKeptForOtherTargets
                        + " noPipeline=" + this.immediateFlushNoPipeline
                        + " deferredNextFrame=" + this.immediateDeferredNextFrame
                        + " refusedWindowBudget=" + this.immediateRefusedWindowBudget
                        + " lastError=" + this.immediateLastFlushError);
            }
            if (formatId >= 0 && formatId < this.immediateAcceptedByFormat.length) {
                this.immediateAcceptedByFormat[formatId]++;
                if (!this.currentRenderTargetIsMain) {
                    // Accepted for a framebuffer that is not the main one: this is the count that means the content is being
                    // drawn by Vulkan into one of the game's own targets, as opposed to merely offered to it.
                    this.offTargetAccepted++;
                }
                // Printed incrementally rather than only in the end-of-run report, because a run that times out has no report and
                // still has evidence: the arrival census proved that on a partial Intel run carrying 674,500 uploads. The
                // acceptance side needed the same treatment - a count that only exists at the end cannot answer "was this taken"
                // for a run that never reached the end.
                this.immediateAcceptedTotal++;
                // Gated: one line per 500 accepted batches is a per-second line on the operator's own session, and it
                // is a synchronous stdout write on the render thread. The same numbers are in the report.
                if (this.immediateAcceptedTotal % 500 == 0 && VulkanDiagnostics.enabled()) {
                    StringBuilder accepted = new StringBuilder();
                    for (int i = 0; i < this.immediateAcceptedByFormat.length; i++) {
                        accepted.append(i).append(':').append(this.immediateAcceptedByFormat[i]).append(' ');
                    }
                    System.out.println("[Celtium] CELTIUM_ACCEPTED total=" + this.immediateAcceptedTotal
                            + " byFormat=[" + accepted.toString().trim() + "]"
                            + " blendingFactorsMix=" + this.immediateBlendedAccepted
                            + " blendingOff=" + this.immediateOpaqueAccepted
                            // With the counters, not only in the report: how many transform calls reached the stub, to be read
                            // against the mirror's own application counts rather than on its own.
                            + " stubTransformCalls=" + (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                                    ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.stubTransformSummary() : "bridge"));
                }
            }
            this.immediateLastRefusal = "none";
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.immediateLastRefusal = "copy failed: " + describe(error);
            record("immediate batch refused: " + describe(error));
            // Order (S10): what is already accepted is drawn before this batch goes to GL.
            this.flushPendingBeforeGlDraw();
            return false;
        }
    }

    /** Frees the meshes of groups whose draw was recorded at least one full pass ago. */
    private void releaseRetiredImmediateGroups() {
        for (ImmediateGroup group : this.retiredImmediateGroups) {
            if (group.stamp != null
                    && (group.stamp).meshBorrowed) {
                // The mesh is held by whoever lent it (the captured model meshes are reused every frame), so retiring
                // the group must not free it.
                continue;
            }
            group.mesh.delete();
        }
        this.retiredImmediateGroups.clear();
    }

    /**
     * The captured drawing state of a group as one bracketed field, for {@code CELTIUM_GROUP_BOUNDS}.
     * <p>
     * The array is {@code celtium$captureState}'s own layout - {@code {blend, srcFactor, dstFactor, depthTest,
     * depthWrite, cull, alphaReference, depthFunc, lighting}} - and it is printed here because the NDC bounds next to
     * it cannot tell "the geometry is not where the panel is" from "the geometry is exactly where the panel is and the
     * depth test rejected it". The second needs the state, and the state was not in any per-group line.
     */
    private static String stateText(int[] state) {
        if (state == null || state.length < 8) {
            return "[none]";
        }
        return "[blend=" + state[0] + " src=" + state[1] + " dst=" + state[2]
                // The appended slots, named here because these five numbers are what BH-07/BH-10/BH-03 moved: a reader
                // of a group line has to be able to see the alpha pair beside the RGB pair, the write mask, and the
                // comparison the batch was captured with.
                + (state.length > 14 ? " srcA=" + state[9] + " dstA=" + state[10]
                        + " mask=0x" + Integer.toHexString(state[11]) + " eq=" + state[12] + "/" + state[13]
                        + " alphaFunc=" + state[14] : " (pre-BH-07 array)")
                + " depthTest=" + state[3] + " depthWrite=" + state[4] + " cull=" + state[5]
                + " alphaRef=" + Float.intBitsToFloat(state[6]) + " depthFunc=" + state[7]
                // The other copy of the same facts, and the trap in reading it. The mirror is fed at the head of
                // every hooked GlStateManager call; the GL enable record behind glIsEnabled is fed by whatever
                // glEnable/glDisable the game issues, including any that does not go through GlStateManager at all.
                // **The two halves are read at different instants**: the state fields are the group's captured copy,
                // taken when its first batch was offered, while glBlend/glDepthTest are read here, when the group is
                // closed - which can be a later batch and even a later frame. A line whose halves disagree therefore
                // says "the state moved between the offer and the close" at least as often as it says "the mirror is
                // wrong", and only a comparison taken at the offer itself can tell those apart. Measured the hard way:
                // 146 groups printed `blend=0 ... glBlend=on`, which looked like a broken mirror; a reconcile that
                // compared the two *at the offer* found the blend bit already agreeing and left every GUI pixel
                // unchanged, so the reading was an artefact of the instrument, not a defect.
                + " glBlend=" + glEnabled(org.lwjgl.opengl.GL11.GL_BLEND)
                + " glDepthTest=" + glEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST)
                + " (gl* read at close, the state fields at the offer)" + "]";
    }

    /** The GL enable record's answer for one capability, or {@code ?} when the query itself is unavailable. */
    private static String glEnabled(int capability) {
        try {
            return org.lwjgl.opengl.GL11.glIsEnabled(capability) ? "on" : "off";
        } catch (RuntimeException | LinkageError error) {
            return "?";
        }
    }

    /**
     * Reports the projected NDC bounds of a group and the colour of its first vertex.
     * <p>
     * This is the narrower diagnostic the previous round called for: vertex 0 alone cannot tell "projects on-screen
     * but is rejected by the depth test" from "never enters an accepted group", while the bounds over the whole group
     * can. The packed colour identifies the test geometry (the fixture draws with 255, 64, 64, 200).
     */
    private void reportGroupBounds(ImmediateGroup group) {
        if (!wantedGroupBoundsFormat(group.formatId)) {
            // The format filter is checked before the cap so that a run narrowed to one layout is not spent on the
            // first six groups of every other layout.
            return;
        }
        if (this.frameId < GROUP_BOUNDS_MIN_FRAME) {
            // Same reason, one level up: the loading screen and the menu own the first frames' groups, so a bounded
            // budget has to skip them to reach the frame the question is about.
            return;
        }
        int limit = GROUP_BOUNDS_DIAGNOSTICS ? GROUP_BOUNDS_LIMIT : 6;
        if (this.immediateDiagnostics >= limit) {
            return;
        }
        this.immediateDiagnostics++;
        try {
            VulkanDynamicMesh mesh = group.mesh;
            float[] projection = group.projection;
            float[] modelView = group.modelView;
            long base = mesh.vertexAddress();
            int vertices = mesh.vertexCount();
            // The stride the mesh was built with, not a pipeline constant: an immediate group copies the caller's
            // vertices unchanged and records their own stride in the layout, and for POSITION_TEX that is 20 bytes
            // (position 12 + texture 8), not the 16 of the base immediate layout. Reading the wrong stride put every
            // vertex after the first at an offset inside another vertex's texture data: measured on the sun and moon
            // groups, it reported y ranges of 100+ NDC units for quads whose real extent is 0.9, which is how a
            // correct quad came to be described as a frame-covering wedge. The vertices, the per-vertex clip
            // positions and the model-view are all printed now, so the reading can be checked rather than believed.
            int stride = mesh.layout() != null ? mesh.layout().stride : VulkanImmediatePipeline.VERTEX_BYTES;
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
            StringBuilder perVertex = new StringBuilder();
            StringBuilder positions = new StringBuilder();
            StringBuilder uvs = new StringBuilder();
            for (int vertex = 0; vertex < vertices; vertex++) {
                long offset = base + ((long) vertex * stride);
                float x = MemoryUtil.memGetFloat(offset);
                float y = MemoryUtil.memGetFloat(offset + 4);
                float z = MemoryUtil.memGetFloat(offset + 8);
                float cx = (mvp[0] * x) + (mvp[4] * y) + (mvp[8] * z) + mvp[12];
                float cy = (mvp[1] * x) + (mvp[5] * y) + (mvp[9] * z) + mvp[13];
                float cz = (mvp[2] * x) + (mvp[6] * y) + (mvp[10] * z) + mvp[14];
                float cw = (mvp[3] * x) + (mvp[7] * y) + (mvp[11] * z) + mvp[15];
                if (vertex < 8) {
                    // The vertices themselves, beside the bounds: the bounds say where the group lands, and these say
                    // whether the geometry the group was built from is the quad the caller drew. A defect in either
                    // would move the picture, and the two are indistinguishable from the bounds alone. The same
                    // stride applies to the texture coordinates, which is what names the quad (the sun's is u=0,
                    // the moon's first vertex u=0.25 of the phase atlas).
                    positions.append(vertex > 0 ? ";" : "").append(round3(x)).append(',').append(round3(y))
                            .append(',').append(round3(z));
                    if (stride >= 20) {
                        uvs.append(vertex > 0 ? ";" : "")
                                .append(round3(MemoryUtil.memGetFloat(offset + 12))).append(',')
                                .append(round3(MemoryUtil.memGetFloat(offset + 16)));
                    }
                    perVertex.append(vertex > 0 ? ";" : "").append(round3(cx / cw)).append(',')
                            .append(round3(cy / cw)).append(',').append(round3(cz / cw)).append(',')
                            .append(round3(cw));
                }
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
            String colour = "0x" + Integer.toHexString(MemoryUtil.memGetInt(base + 12));
            // The world-pass POSITION_TEX family is the sky - the sun, the moon, the water surface - and it is the one
            // family whose model-view is the camera times two rotations the pass itself makes. For those groups the
            // line carries the whole chain: the group's own model-view, the vertices it was built from, each vertex's
            // clip-space position, and the rotations that reached the mirror since the camera boundary. Nothing else is
            // added, and with the switch off none of it is even computed.
            String chain = "";
            if ((group.formatId == 6 || group.formatId == 7) && this.worldPassActive) {
                chain = " mv=" + matrixText(modelView)
                        + " verts=[" + positions + "]"
                        + " uv=[" + uvs + "]"
                        + " clip=[" + perVertex + "]"
                        + " rot=[" + this.currentGroupRotations + "]";
            } else if (group.formatId == VulkanImmediateFormats.ITEM && !this.worldPassActive) {
                // The GUI item family, added 2026-10-11: RenderItem draws a GUI item through
                // BufferBuilder.begin(7, DefaultVertexFormats.ITEM) - id 4 - so a sprite that comes out at the wrong
                // size is either this group's model-view (its scale is exactly diag(16,-16,16) with a translation of
                // (x+8, y+8, 100+zLevel), from RenderItem.setupGuiTransform) or the ITEM layout being read at the
                // wrong stride. The model-view is printed here so the two are told apart from one line: a wrong scale
                // shows in mv[0]/mv[5], a wrong stride shows in verts=[...] while mv stays 16/-16.
                chain = " mv=" + matrixText(modelView)
                        + " verts=[" + positions + "]"
                        + " uv=[" + uvs + "]"
                        + " clip=[" + perVertex + "]"
                        + " rot=[" + this.currentGroupRotations + "]";
            }
            // On stdout as well as in the report: the report keeps only the last recorded line, and the question this
            // answers - which groups an artefact frame is made of - is a sequence, not a sample. frame/group tie the
            // line to the frame dump the PNG is of, and the projection's own columns say whether the group was
            // captured with the world perspective, an ortho, or a stale stack.
            System.out.println("[Celtium] CELTIUM_GROUP_BOUNDS frame=" + this.frameId
                    + " group=" + this.immediateGroupsClosed + " fmt=" + group.formatId
                    + " worldPass=" + this.worldPassActive + " target=" + group.target
                    + " texture=" + group.texture + " batches=" + group.batches
                    + " vertices=" + vertices + " indices=" + mesh.indexCount()
                    + " colour0=" + colour
                    // round3, not fmt3: the GUI's ortho has m00 = 2/ScaledWidth, which at the pinned pose is
                    // 0.00469 - a *correct* GUI projection and a collapsed one both print "0.00" under fmt3's two
                    // decimals, so the field could not answer the one question it exists for. The trap is the same
                    // shape as the 16-byte-stride and pointerStride=1 readings: the instrument did not read what it
                    // looked like it read. m11 = -2/ScaledHeight = -0.00833 is the second tell.
                    + " proj=[m00=" + round3(projection[0]) + " m11=" + round3(projection[5])
                    + " m22=" + round3(projection[10]) + " m32=" + round3(projection[14]) + "]"
                    // The drawing state the group was captured with, which is what decides whether a group that
                    // measures on-screen can still be rejected: a depth-tested quad drawn after the world pass is
                    // tested against the world's depth unless something cleared the depth attachment in between.
                    + " state=" + stateText(group.state)
                    // The texture matrix the group was captured with, because it is the one input that moves a
                    // correct-looking quad's UVs somewhere else: a stale affine left on unit 0 by an earlier draw
                    // transforms this group's texture coordinates, and the sample then lands outside the sprite -
                    // which the alpha test turns into "drawn, accepted, no pixels". The block-breaking overlay is the
                    // measured case (its fragments are alpha-discarded with the real atlas bound), and this field is
                    // what tells "the group carries a transform" from "it does not".
                    + " texMat=" + (group.texMatrix == null ? "none"
                            : round3(group.texMatrix[0]) + "," + round3(group.texMatrix[1]) + ","
                                    + round3(group.texMatrix[2]) + "," + round3(group.texMatrix[3]) + ","
                                    + round3(group.texMatrix[4]) + "," + round3(group.texMatrix[5]))
                    + " ndc " + bounds + chain);
            record("group " + this.immediateDiagnostics + ": vertices=" + vertices
                    + " indices=" + mesh.indexCount() + " colour0=" + colour
                    + " proj=[m00=" + projection[0] + " m11=" + projection[5] + "] ndc " + bounds);
        } catch (RuntimeException | LinkageError error) {
            record("group diagnostic failed: " + describe(error));
        }
    }

    /**
     * Snapshots the drawing state that bakes into a pipeline.
     * <p>
     * Captured with the group, like the matrices: by the time the flush runs a frame later the live state has moved on,
     * and using it would draw with a blend or depth combination the batch never asked for.
     * <p>
     * The array is
     * {@code {blend, srcColorVk, dstColorVk, depthTest, depthWrite, cull, alphaRefBits, depthFunc, lighting,
     * srcAlphaVk, dstAlphaVk, colorWriteMask, colorOpVk, alphaOpVk, alphaFuncGl}} - the first nine slots are the
     * original layout and keep their meaning, everything BH-07/BH-10/BH-03 added is appended so a reader written
     * before the change still reads what it read. The key is computed over the whole array
     * ({@link #celtium$stateKey}), which is what makes a batch that differs only in its alpha factors, its write mask
     * or its alpha comparison a different pipeline <em>and</em> a different group.
     */
    private int[] celtium$captureState() {
        boolean blend = this.immediateState.blendEnabled();
        int source = blend ? glBlendFactorToVk(this.immediateState.blendSrcRgb()) : 0;
        int destination = blend ? glBlendFactorToVk(this.immediateState.blendDstRgb()) : 0;
        int sourceAlpha = blend ? glBlendFactorToVk(this.immediateState.blendSrcAlpha()) : 0;
        int destinationAlpha = blend ? glBlendFactorToVk(this.immediateState.blendDstAlpha()) : 0;
        int colorOp = glBlendEquationToVk(this.immediateState.blendEquationRgb());
        int alphaOp = glBlendEquationToVk(this.immediateState.blendEquationAlpha());
        int writeMask = this.immediateState.colorMask();
        // Comparison switch, default on: forcing the threshold to zero turns every discard off, which isolates the
        // alpha test's effect inside one build instead of comparing across builds.
        float alphaReference = ALPHA_TEST_ENABLED && this.immediateState.alphaTestEnabled()
                ? this.immediateState.alphaFuncReference() : 0.0f;
        // BH-08: the cull state the game actually set, read from the recording the stub keeps for exactly this -
        // the capability from the same enables map glEnable/glDisable write, and the face from glCullFace. Nothing
        // else ever wrote these two fields, so the pipeline was built with culling off for every immediate batch.
        //
        // BH-07/BH-10: the same reading, for the same reason, of the state that used to stop at the game's own
        // wrapper. `GlStateManager.blendFunc(int,int)` calls `GL11.glBlendFunc` directly and both
        // `tryBlendFuncSeparate` overloads go through `OpenGlHelper.glBlendFunc` -> `GL14.glBlendFuncSeparate`, so
        // the mixin that hooks the enum overload is not the whole path - and `glColorMask` has no mixin at all. The
        // stub's record is the one place every caller of every shape lands, and it holds exactly what GL was told.
        if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            // The mirror is kept in step as well, so `describe()` and any later reader of the mirror see the same
            // state the capture took rather than the last wrapper call.
            if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendFactorsRecorded()) {
                this.immediateState.blendFunc(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendSrcRgb(),
                        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendDstRgb(),
                        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendSrcAlpha(),
                        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendDstAlpha());
            }
            if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendEquationRecorded()) {
                this.immediateState.blendEquation(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendEquationRgb(),
                        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendEquationAlpha());
            }
            if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.colorMaskRecorded()) {
                this.immediateState.colorMask(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.colorMask());
            }
            this.immediateState.blendEnabled(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendEnabled());
            this.immediateState.cullEnabled(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.cullEnabled());
            this.immediateState.cullFace(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.cullFace());
            blend = this.immediateState.blendEnabled();
            source = blend ? glBlendFactorToVk(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendSrcRgb()) : 0;
            destination = blend ? glBlendFactorToVk(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendDstRgb()) : 0;
            sourceAlpha = blend ? glBlendFactorToVk(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendSrcAlpha()) : 0;
            destinationAlpha = blend ? glBlendFactorToVk(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendDstAlpha()) : 0;
            colorOp = glBlendEquationToVk(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendEquationRgb());
            alphaOp = glBlendEquationToVk(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendEquationAlpha());
            writeMask = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.colorMask();
        }
        // The one-flag controls. Each keeps the captured state but substitutes the value the pipeline baked before the
        // finding was fixed, so a single build can be run twice and the picture compared against itself rather than
        // against another build's picture - the difference between the legs is then the state and nothing else.
        //
        // The counters below are taken from the *captured* values, before these substitutions: a control leg has to
        // report the same "the game asked for this" numbers as the fixed leg, or the one number that says the state
        // arrived at all would read zero exactly in the leg that uses it as a control.
        int capturedSourceAlpha = sourceAlpha;
        int capturedDestinationAlpha = destinationAlpha;
        int capturedWriteMask = writeMask;
        int capturedColorOp = colorOp;
        int capturedAlphaOp = alphaOp;
        if (!IMMEDIATE_BLEND_ALPHA) {
            sourceAlpha = org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
            destinationAlpha = org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        }
        if (!IMMEDIATE_BLEND_EQUATION) {
            colorOp = org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
            alphaOp = org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
        }
        if (!IMMEDIATE_COLOR_MASK) {
            writeMask = VulkanColorBlendState.allChannels();
        }
        // BH-03: the comparison function travels with the batch, next to the reference it is compared against. The
        // slot must distinguish "disabled" (0 - no GL enum is 0) from "enabled with a reference of 0", which the old
        // shader's `alphaRef > 0.0` guard could not: for GL_GREATER and a reference of 0 the discard is *not* off.
        int alphaFunction = ALPHA_TEST_ENABLED && this.immediateState.alphaTestEnabled()
                ? this.immediateState.alphaFunc() : 0;
        // The measurement switch for the comparison-function half of BH-03: vanilla's own code only ever asks for
        // GL_GREATER (read in the sources - every `GlStateManager.alphaFunc` call in the client passes 516), so a
        // non-GREATER comparison cannot be reached from the game's own state in this fixture. This substitutes the
        // comparison for the batches whose test is enabled, leaving the reference and the enable alone, so one build
        // can be run at GREATER, LESS and ALWAYS and the crops compared with each other. A batch with the test
        // disabled keeps the 0 that means "disabled" - otherwise the flag would switch the test on where the game
        // switched it off, and the leg would measure two changes at once.
        if (alphaFunction != 0 && ALPHA_FUNC_OVERRIDE != 0) {
            alphaFunction = ALPHA_FUNC_OVERRIDE;
        }
        // BH-08's per-batch proof, counted here where the group's own state is taken rather than at draw time, where
        // it would be the flush's state.
        if (this.immediateState.cullEnabled()) {
            this.cullEnabledGroups++;
        } else {
            this.cullDisabledGroups++;
        }
        // BH-07/BH-10/BH-03's own per-batch counts, at the same instant and for the same reason: a batch whose alpha
        // pair is not the pair the pipelines used to bake is the case the finding is about, and "the state reached the
        // key" is only readable if the number of batches that carried a different one is on the report.
        // Taken from the captured values (see above), so a control leg reports the same arrival numbers as the
        // fixed leg rather than zeroes that would read as "the state never arrived".
        if (blend) {
            if (capturedSourceAlpha != org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE
                    || capturedDestinationAlpha != org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA) {
                this.blendAlphaNonDefaultGroups++;
            }
            this.blendGroups++;
        }
        this.colorMaskHistogram[capturedWriteMask & 0xF]++;
        if (capturedWriteMask != VulkanColorBlendState.allChannels()) {
            this.partialColorMaskGroups++;
            this.lastPartialColorMask = capturedWriteMask;
        }
        if (capturedColorOp != org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD
                || capturedAlphaOp != org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD) {
            this.nonAddBlendEquationGroups++;
        }
        if (alphaFunction != 0) {
            this.alphaFuncGroups++;
            this.lastAlphaFunc = alphaFunction;
            if (alphaFunction != 0x0204) {
                this.nonGreaterAlphaFuncGroups++;
            }
        } else {
            this.alphaTestDisabledGroups++;
        }
        return new int[] {
                blend ? 1 : 0,
                source < 0 ? org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA : source,
                destination < 0 ? org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA : destination,
                this.immediateState.depthTestEnabled() ? 1 : 0,
                this.immediateState.depthMask() ? 1 : 0,
                this.immediateState.cullEnabled() ? 1 : 0,
                Float.floatToIntBits(alphaReference),
                // The comparison itself rather than a boolean: the glint draws at GL_EQUAL and the held item at
                // GL_ALWAYS, neither of which a fixed LESS_OR_EQUAL pipeline would pass correctly.
                this.immediateState.depthFunc(),
                // Fixed-function lighting: only the normal-carrying formats (8 and 9) can act on it, but it rides the
                // state key so a lit batch and an unlit batch never share a pipeline - or a group.
                this.immediateState.lightingEnabled() ? 1 : 0,
                // ---- appended by BH-07/BH-10/BH-03; the first nine slots above keep their meaning.
                sourceAlpha < 0 ? org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE : sourceAlpha,
                destinationAlpha < 0 ? org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA : destinationAlpha,
                writeMask,
                colorOp,
                alphaOp,
                alphaFunction,
        };
    }

    /**
     * The Vulkan blend state a captured group asks for, rebuilt from its own array.
     * <p>
     * One place owns the GL-to-Vulkan mapping for the whole immediate path, and the pipeline factories take the result
     * rather than nine loose integers that can be passed in the wrong order.
     */
    private static VulkanColorBlendState celtium$blendState(int[] state) {
        boolean blend = state[0] != 0;
        return VulkanColorBlendState.of(blend,
                state[1],
                state[2],
                state.length > 9 ? state[9] : org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE,
                state.length > 10 ? state[10] : org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                state.length > 12 ? state[12] : org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD,
                state.length > 13 ? state[13] : org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD,
                state.length > 11 ? state[11] : VulkanColorBlendState.allChannels());
    }

    /**
     * The Vulkan blend op for one OpenGL blend equation. GL's constants are not Vulkan's and not ordinals of each
     * other, so this is a translation and not a cast; an equation this renderer does not know falls back to ADD, which
     * is GL's initial state and the one every caller in the fixture actually sets.
     */
    private static int glBlendEquationToVk(int glEquation) {
        switch (glEquation) {
            case VulkanGlState.BLEND_FUNC_SUBTRACT: return org.lwjgl.vulkan.VK10.VK_BLEND_OP_SUBTRACT;
            case VulkanGlState.BLEND_FUNC_REVERSE_SUBTRACT:
                return org.lwjgl.vulkan.VK10.VK_BLEND_OP_REVERSE_SUBTRACT;
            case VulkanGlState.BLEND_MIN: return org.lwjgl.vulkan.VK10.VK_BLEND_OP_MIN;
            case VulkanGlState.BLEND_MAX: return org.lwjgl.vulkan.VK10.VK_BLEND_OP_MAX;
            case VulkanGlState.BLEND_FUNC_ADD:
            default: return org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
        }
    }

    /**
     * The Vulkan compare op for one OpenGL depth function. {@code GlStateManager.depthFunc} passes the GL constant
     * verbatim; anything outside the eight standard functions falls back to LESS_OR_EQUAL, which is what the
     * pipeline used to bake unconditionally.
     */
    private static int celtium$vkCompareOp(int glFunc) {
        switch (glFunc) {
            case 512: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_NEVER;
            case 513: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS;
            case 514: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_EQUAL;
            case 515: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
            case 516: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_GREATER;
            case 517: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_NOT_EQUAL;
            case 518: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_GREATER_OR_EQUAL;
            case 519: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_ALWAYS;
            default: return org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
        }
    }

    /**
     * The highest line width the device can draw, or 0 when it cannot draw wide lines at all.
     * <p>
     * {@code wideLines} is an optional Vulkan feature, so a renderer that has to run on a wide range of devices cannot
     * require it: the line width OpenGL was asked for is compared against this, and a batch that needs more is refused
     * with a reason and drawn by OpenGL, which is where it is correct today.
     */
    private float celtium$maxLineWidth() {
        if (this.maxLineWidth >= 0.0F) {
            return this.maxLineWidth;
        }
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
            org.lwjgl.vulkan.VkPhysicalDeviceLimits limits = properties.limits();
            // lineWidthRange is the limit; wideLines is a feature and is not queried separately because the two agree:
            // without the feature the range is [1, 1], so the width comparison below already refuses anything wider.
            this.maxLineWidth = limits.lineWidthRange(1);
        } catch (RuntimeException | LinkageError error) {
            this.maxLineWidth = 1.0F;
        }
        return this.maxLineWidth;
    }

    /** {@return whether a line batch that OpenGL asked this width for can be drawn by the line pipeline}. */
    /** {@return the line width OpenGL currently has set}, or 1.0 when it cannot be read. */
    private float celtium$requestedLineWidth() {
        try {
            java.nio.FloatBuffer width = org.lwjgl.BufferUtils.createFloatBuffer(1);
            org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_LINE_WIDTH, width);
            return width.get(0);
        } catch (RuntimeException | LinkageError ignored) {
            return 1.0F;
        }
    }

    private static String round3(float value) {
        return String.valueOf(Math.round(value * 1000.0f) / 1000.0f);
    }

    /** A column-major matrix or vector as one bracketed, comma-separated line, for the diagnostic prints. */
    private static String matrixText(float[] values) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(round3(values[i]));
        }
        return text.append(']').toString();
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

    /**
     * Draws what this renderer has already accepted, before the batch being refused is drawn by OpenGL (audit S10).
     * <p>
     * Accepted batches wait for the group to close and for a flush; a refusal returns immediately and lets the original
     * GL draw run at once, so without this the deferred batch is drawn <em>after</em> content that came later in the
     * frame. The audit describes exactly that: "small accepted batches wait for a threshold while the next unsupported
     * draw runs in GL immediately". Flushing here keeps the order; with nothing pending it does nothing.
     */
    /**
     * {@return one line of the descriptor-set census, with an entry per immediate family}.
     * <p>
     * Audit B11: this used to sum only the block and particle caches and print a single number, so the textured and
     * item families - which allocate far more sets than either - were invisible, and the number that was printed read
     * as a total for the whole immediate path. The four families now have four counters and four entries. The
     * textured family is the largest by construction: it keeps a set per distinct texture view, so its row is the one
     * that moves when a scene samples many textures.
     */
    /**
     * {@return the uploads every live region arena has performed}, the volume that makes {@code bootstrapQueueWaits}
     * readable as a cost per upload rather than a bare count (task-51).
     */
    private long celtium$arenaUploadCount() {
        long total = 0;
        for (RegionResources resources : this.regionResources.values()) {
            total += resources.vertexArena.uploadCount();
            if (resources.indexArena != null) {
                total += resources.indexArena.uploadCount();
            }
        }
        return total;
    }

    /** {@return the bytes those uploads carried}, the other half of the same cost pair (task-51). */
    private long celtium$arenaUploadedBytes() {
        long total = 0;
        for (RegionResources resources : this.regionResources.values()) {
            total += resources.vertexArena.uploadedBytes();
            if (resources.indexArena != null) {
                total += resources.indexArena.uploadedBytes();
            }
        }
        return total;
    }

    private String descriptorSetCensus() {
        long block = 0;
        for (VulkanImmediateBlockPipeline pipeline : this.blockPipelineCache.values()) {
            block += pipeline.setsCreated;
        }
        long particle = 0;
        for (VulkanImmediateParticlePipeline pipeline : this.particlePipelineCache.values()) {
            particle += pipeline.setsCreated;
        }
        long texture = VulkanImmediateTexturePipeline.DESCRIPTOR_SETS_ALLOCATED.get();
        long item = VulkanImmediateItemPipeline.DESCRIPTOR_SETS_ALLOCATED.get();
        return "[texture=" + texture + ",item=" + item + ",block=" + block + ",particle=" + particle
                + ",total=" + (texture + item + block + particle) + "]";
    }

    /** Releases a mesh if it exists; returns 1 when something was released, so the teardown can count. */
    private int releaseImmediateMesh(VulkanDynamicMesh mesh) {
        if (mesh == null) {
            return 0;
        }
        try {
            mesh.delete();
            return 1;
        } catch (RuntimeException | LinkageError error) {
            record("teardown: mesh could not be released: " + describe(error));
            return 0;
        }
    }

    /** Runs a teardown step and counts it; a failure is reported rather than fatal (audit S15). */
    private int releaseDirect(Runnable teardown) {
        if (teardown == null) {
            return 0;
        }
        try {
            teardown.run();
            return 1;
        } catch (RuntimeException | LinkageError error) {
            record("teardown step failed: " + describe(error));
            return 0;
        }
    }

    /**
     * Releases one keyed cache of untextured or line immediate pipelines and empties it.
     * <p>
     * Every entry owns a pipeline layout and, for the line family, its own pipeline; the cache is keyed by the draw
     * state and nothing else holds a reference once the held groups have been discarded, so dropping the map without
     * walking it is a leak of one object set per distinct state key. The map is cleared whatever happens to an entry,
     * so a second teardown sees an empty cache and releases nothing again.
     */
    private int releaseImmediatePipelineCache(java.util.Map<Long, VulkanImmediatePipeline> cache) {
        int count = 0;
        for (VulkanImmediatePipeline pipeline : cache.values()) {
            count += releaseDirect(pipeline == null ? null : () -> pipeline.delete());
        }
        cache.clear();
        return count;
    }

    /** Releases one keyed cache of textured immediate pipelines; see {@link #releaseImmediatePipelineCache}. */
    private int releaseTexturePipelineCache(java.util.Map<Long, VulkanImmediateTexturePipeline> cache) {
        int count = 0;
        for (VulkanImmediateTexturePipeline pipeline : cache.values()) {
            count += releaseDirect(pipeline == null ? null : () -> pipeline.delete());
        }
        cache.clear();
        return count;
    }

    /**
     * Releases every immediate pipeline the backend built against a render pass, and empties the caches.
     * <p>
     * Each of these objects names a render pass at creation: the world's terrain pass for a world frame, the standalone
     * UI pass for a menu frame. The cache key is the draw state alone and does not include it, so a cache cannot outlive
     * the world whose pass it was built for - {@link VulkanTerrainPipeline#delete()} destroys the terrain render pass,
     * and a reused entry would name it. This is called from the world-leave path and from the backend's own teardown;
     * because each map is emptied here, the second call finds nothing and releases nothing again, which is what makes
     * the release exactly-once on both paths.
     * <p>
     * The UI pipeline is not part of this: it is built against the standalone UI render pass, which the window keeps.
     */
    private int celtium$releaseImmediatePipelines() {
        int released = 0;
        for (VulkanImmediateBlockPipeline block : this.blockPipelineCache.values()) {
            released += releaseDirect(block == null ? null : () -> block.delete());
        }
        this.blockPipelineCache.clear();
        for (VulkanImmediateParticlePipeline particle : this.particlePipelineCache.values()) {
            released += releaseDirect(particle == null ? null : () -> particle.delete());
        }
        this.particlePipelineCache.clear();
        // Each entry is a pipeline layout, a descriptor set layout and one or more descriptor pools, and a pool cannot
        // be destroyed while a set allocated from it is alive, so a cache dropped without this leaks the whole family.
        released += releaseImmediatePipelineCache(this.immediatePipelineCache);
        released += releaseImmediatePipelineCache(this.linePipelineCache);
        released += releaseTexturePipelineCache(this.positionTexColorNormalPipelineCache);
        released += releaseTexturePipelineCache(this.positionTexNormalPipelineCache);
        released += releaseTexturePipelineCache(this.positionOnlyPipelineCache);
        released += releaseTexturePipelineCache(this.positionNormalPipelineCache);
        released += releaseTexturePipelineCache(this.positionTexPipelineCache);
        released += releaseTexturePipelineCache(this.texturedPipelineCache);
        for (VulkanImmediateItemPipeline item : this.itemPipelineCache.values()) {
            released += releaseDirect(item == null ? null : () -> item.delete());
        }
        this.itemPipelineCache.clear();
        released += releaseDirect(this.immediatePipeline == null ? null : () -> this.immediatePipeline.delete());
        this.immediatePipeline = null;
        released += releaseImmediateMesh(this.isolationMesh);
        this.isolationMesh = null;
        released += releaseDirect(this.descriptorProbe == null ? null : () -> this.descriptorProbe.delete());
        this.descriptorProbe = null;
        // The UI test quad's pipeline is built in drawUiTestQuad against the terrain pass, not against the standalone UI
        // pass the window keeps, and it draws into the terrain colour image - so it belongs to this set, whatever its
        // name suggests. Its mesh is created with it and is used by nothing else.
        released += releaseDirect(this.uiPipeline == null ? null : () -> this.uiPipeline.delete());
        this.uiPipeline = null;
        released += releaseImmediateMesh(this.uiMesh);
        this.uiMesh = null;
        return released;
    }

    /** The viewport key that means "the whole render target", packed the same way {@code viewport} packs it. */
    private long fullTargetViewportKey() {
        return ((long) 0 << 42) ^ ((long) 0 << 21) ^ ((long) this.targetWidth << 10) ^ (long) this.targetHeight;
    }

    private void flushPendingBeforeGlDraw() {
        if (this.immediateWindowBusy) return;
        closeCurrentImmediateGroup();
        if (this.immediateGroups.isEmpty()) return;
        if (this.framebufferReplacing) {
            // No ordering flush across a framebuffer replacement: the pending groups were offered for a framebuffer whose
            // size no longer exists, and drawing them now would need the very wait that stalls the device. They are counted
            // and dropped, which is the same rule the refusal sites follow - name the loss rather than claim a draw that
            // cannot happen.
            int dropped = 0;
            for (ImmediateGroup group : this.immediateGroups) {
                dropped += group.batches;
            }
            this.postAcceptDroppedGroups += dropped;
            this.immediateGroups.clear();
            this.replaceBoundaryDrops++;
            if (this.replaceBoundaryDropBatches == 0) {
                this.replaceBoundaryDropBatches = dropped;
                System.out.println("[Celtium] CELTIUM_REPLACE_DROP batches=" + dropped + " target=" + this.targetWidth
                        + "x" + this.targetHeight + " frameId=" + this.frameId);
            }
            return;
        }
        // Counted here, after the emptiness check: clearOrderFlushes counts *calls* (many return early), so it cannot
        // say how many handoffs a frame really makes. The wall time is what says whether a handoff is a stall.
        this.actualFlushes++;
        long flushStartedNanos = System.nanoTime();
        long flushCpuStartedNanos = threadCpuNanos();
        try {
            if (this.commandState != null && this.commandState.inRenderPass()) {
                // An ordering flush: the batch that is about to be refused is drawn by GL, so what is already
                // accepted has to reach the image first, inside the pass that is already open.
                this.immediateDrawSite = "order-flush-in-pass";
                drawPendingImmediate(Long.valueOf(this.activeImmediateTarget));
            } else {
                flushImmediateWindow();
            }
            this.orderPreservingFlushes++;
            long tookNanos = System.nanoTime() - flushStartedNanos;
            long flushCpuEndedNanos = threadCpuNanos();
            if (flushCpuStartedNanos >= 0 && flushCpuEndedNanos >= 0) {
                this.actualFlushCpuNanos += flushCpuEndedNanos - flushCpuStartedNanos;
            }
            this.actualFlushNanos += tookNanos;
            if (tookNanos > this.actualFlushWorstNanos) {
                this.actualFlushWorstNanos = tookNanos;
            }
        } catch (RuntimeException | LinkageError error) {
            // Bounded, not latched - the same rule the window-append catch follows (task-60). An exception in an ordering
            // flush is not a device that has stopped, and latching here is what the user felt: their own session latched
            // at frame 0 on the loading screen's font path (`CELTIUM_STALL site=…CeltiumFontGlyphCapture.submit:455`),
            // after which every draw was refused for the rest of the run (79 "the GPU stalled" lines) and the window
            // never left the loading dirt. The batches this flush held cannot be handed back - their OpenGL draws were
            // cancelled when they were accepted - so they are counted and dropped and the next frame tries again. The
            // latch stays reachable from the wait-timeout path, which is where a device that has genuinely stopped is
            // detected.
            this.orderingFlushFailures++;
            this.lastOrderingFlushFailure = describe(error);
            int dropped = 0;
            for (ImmediateGroup group : this.immediateGroups) {
                dropped += group.batches;
            }
            this.orderingFlushDroppedBatches += dropped;
            this.immediateGroups.clear();
            this.pendingImmediateIndices = 0;
            record("ordering flush failed at frame " + this.frameId + ", its " + dropped
                    + " accepted batches were dropped and the next frame retries (orderingFlushFailures="
                    + this.orderingFlushFailures + "): " + describe(error));
        }
    }

    /** Synchronous same-target LOAD window; no draw command is emitted outside this backend's pass. */
    private void flushImmediateWindow() {
        if (this.immediateWindowBusy) return;
        // Kept from the reverted drain (e87aa0ec1): how many of the bound target's groups are present at flush entry,
        // and how often that target was visited. The drain itself is reverted - a device-lost regression outranks it -
        // but this counter is what said the one-shot identity's groups are not in the queue at a flush at all, and it
        // stays so the next attempt starts from that fact rather than rediscovering it.
        this.flushEntryGroupsByTarget.merge(Long.valueOf(this.currentRenderTarget),
                Integer.valueOf(countGroupsForTarget(this.currentRenderTarget)), Integer::sum);
        this.flushEntryPassesByTarget.merge(Long.valueOf(this.currentRenderTarget), Integer.valueOf(1), Integer::sum);
        closeCurrentImmediateGroup();
        if (this.immediateGroups.isEmpty()) return;
        if (this.gpuStalled) {
            // A stalled device cannot draw this window, and the batches it holds were already cancelled in OpenGL when
            // they were accepted, so they cannot be handed back. Throwing here reached Minecraft's render loop and the
            // client died on the crash screen - which the idle-failure injection measured as exit 124 with no report.
            // The loss is counted and named instead, so the run ends with a message and the verdict says the terrain is
            // not verified (audit: count every drop rather than claim a draw that did not happen).
            int dropped = 0;
            for (ImmediateGroup group : this.immediateGroups) {
                dropped += group.batches;
            }
            this.stalledGeometryDrops++;
            this.stalledGeometryDroppedBatches += dropped;
            if (this.stalledGeometryDrops <= 3) {
                record("GPU stalled: dropping " + dropped + " accepted immediate batches (total "
                        + this.stalledGeometryDroppedBatches + ")");
            }
            this.immediateGroups.clear();
            this.pendingImmediateIndices = 0;
            return;
        }
        if (this.commandState.recording()) throw new IllegalStateException("nested immediate command recording");
        if (!supportedImmediateTarget()) throw new IllegalStateException("accepted target disappeared before flush");
        VulkanTerrainPipeline terrain = pipelineFor(this.lastUiPass);
        if (terrain == null && !org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            throw new IllegalStateException("accepted geometry has no render pass");
        }
        // GL-less frames can hold UI batches with no terrain pass ever run (the menu screen): the standalone
        // frame-compatible render pass records them the same way.
        long renderPassHandle = terrain != null ? terrain.renderPass() : uiRenderPass();
        if (!this.windowOrderLogged) {
            // The one fact that separates the two remaining candidates for the unoccluded model quads: if a terrain pass has
            // already run in this frame, the depth buffer is populated and the fault is the depth values; if not, the window
            // draws into an empty one and the fault is the order.
            this.windowOrderLogged = true;
            System.out.println("[Celtium] CELTIUM_WINDOW_ORDER frame=" + this.frameId + " lastTerrainPassFrame="
                    + this.lastTerrainPassFrame + " terrainRanFirst=" + (this.lastTerrainPassFrame == this.frameId)
                    + " lastUiPass=" + nameOf(this.lastUiPass) + " groups=" + this.immediateGroups.size());
        }
        if (countGroupsForTarget(this.currentRenderTarget) == 0) return; // others remain owned in the queue
        boolean offTarget = !this.currentRenderTargetIsMain;
        long identity = this.currentRenderTarget;
        this.immediateWindowBusy = true;
        // BH-26: the owner state a recording consumes lives on the GL-less frame target, while this field is the
        // FrameImageTarget interface - so the handle is resolved once here and both rollback sites use it. On the
        // bridge (real GL) the target is not a GllessFrameTarget and both calls are skipped, which is correct: there
        // is no owner state to put back.
        GllessFrameTarget gllessTarget = this.frameTarget instanceof GllessFrameTarget
                ? (GllessFrameTarget) this.frameTarget : null;
        GllessFrameTarget.RecordingSnapshot recordingSnapshot =
                gllessTarget == null ? null : gllessTarget.snapshotRecording();
        try {
            // Retry a pre-submit failure once. Never retry after vkQueueSubmit success/a fence timeout.
            for (int attempt = 0; attempt < 2; attempt++) {
                boolean opened = false;
                try {
                    this.frameSubmitReachedQueue = false;
                    this.frameTarget.beginFrame();
                    opened = true;
                    this.windowOpen = true;
                    beginCommands();
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        // The acquire barriers advance owner state that only a submission makes true - the first
                        // transition, the consumed clear, both layouts and both pairs' acquired flags. A record that is
                        // discarded before it reaches the queue must put that state back, or the re-record skips the
                        // transition and the clear it already consumed (BH-26).
                        try {
                            this.frameTarget.recordAcquireBarriers(this.commandBuffer);
                            recordImmediateOnlyPass(stack, terrain);
                            this.frameTarget.recordReleaseBarriers(this.commandBuffer);
                        } catch (RuntimeException | LinkageError recordError) {
                            if (gllessTarget != null) {
                                gllessTarget.rollbackRecording(recordingSnapshot);
                            }
                            throw recordError;
                        }
                    }
                    submitCommands();
                    if (!waitForIdle()) throw new IllegalStateException("immediate submission did not complete");
                    this.frameTarget.endFrame();
                    opened = false;
                    this.passSubmissions++;
                    this.immediateWindows++;
                    this.immediateWindowsThisFrame++;
                    if (offTarget) {
                        this.offTargetDrawnByTarget.merge(identity, 1, Integer::sum);
                        this.offTargetCopiedBack++; // completed bridge return, not an empty offscreen copy
                        if (this.textureRegistry != null) {
                            int fbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
                            int source = org.lwjgl.opengl.GL45C.glGetNamedFramebufferAttachmentParameteri(fbo,
                                    org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0,
                                    org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                            this.textureRegistry.invalidateRenderedTexture(source);
                        }
                    }
                    return;
                } catch (RuntimeException | LinkageError error) {
                    if (this.frameSubmitReachedQueue || this.submissionPending || this.gpuStalled) {
                        this.noteStalled(); // no reset/retire/GL fallback while the GPU may own these resources
                        throw error;
                    }
                    // No queue submission: reset the failed attempt and restore recorded groups before releasing GL's semaphore.
                    check(vkResetCommandBuffer(this.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT),
                            "vkResetCommandBuffer(abort)");
                    this.commandState.reset();
                    // BH-26: this recording is discarded before the queue ever saw it, and the recovery below
                    // re-records the acquire and release barriers on a fresh command buffer. Put the owner state back
                    // first, or the second recording sees the first one's consumption - the first transition, the
                    // consumed clear, the layouts - and skips the barrier the frame needs.
                    if (gllessTarget != null) {
                        gllessTarget.rollbackRecording(recordingSnapshot);
                    }
                    for (ImmediateGroup group : this.recordedImmediateGroups) {
                        int batches = group.batches;
                        this.pendingImmediateIndices += group.mesh.indexCount();
                        this.immediateRecordedBatches -= batches;
                        if (group.target != this.mainTargetIdentity) this.offTargetRecordedBatches -= batches;
                    }
                    this.immediateGroups.addAll(0, this.recordedImmediateGroups);
                    this.recordedImmediateGroups.clear();
                    if (opened) {
                        beginCommands();
                        this.frameTarget.recordAcquireBarriers(this.commandBuffer);
                        this.frameTarget.recordReleaseBarriers(this.commandBuffer);
                        submitCommands();
                        if (!waitForIdle()) throw new IllegalStateException("bridge recovery did not complete", error);
                        this.frameTarget.endFrame();
                    }
                    if (attempt == 1) {
                        // Bounded, not latched (task-60). Two failed flushes of one window are the window's problem,
                        // not evidence that the device stopped: the wait counters of the leg this was measured on
                        // read submitTimeouts=0 and a 6 ms worst bootstrap wait, and no fence timeout was recorded.
                        // The window's groups are dropped and counted, the frame is abandoned, and the next frame
                        // tries again. gpuStalled stays reachable from the wait-timeout paths, which is where a
                        // device that has genuinely stopped is detected, and stallOrigin names that origin separately.
                        this.windowFlushFailures++;
                        this.lastWindowFlushFailure = describeWithOrigin(error);
                        this.windowFlushFailureFrame = this.frameId;
                        // Retained, not dropped, and this is the site the measurement actually reaches: the abort
                        // branch above has already taken the recording's counters off and re-queued it, so everything
                        // still queued here is a group that has not been drawn. Clearing it is what erased every
                        // entity and held-item batch of the window - id4 33 accepted / 0 drawn, id9 228 / 0 - while the
                        // per-format histogram has no loss column to show it. The frame is abandoned either way, so
                        // the next attempt re-records these groups; a group is now drawn, retained or counted.
                        int retained = 0;
                        for (ImmediateGroup group : this.immediateGroups) {
                            retained += group.batches;
                        }
                        this.windowFlushRetainedBatches += retained;
                        this.frameTarget.abandonFrame("accepted geometry retained");
                        record("window flush failed twice at frame " + this.frameId + " ("
                                + this.lastWindowFlushFailure + "); retained " + retained
                                + " accepted batches, none dropped, the next frame retries (windowFlushFailures="
                                + this.windowFlushFailures + ")");
                        return;
                    }
                    this.immediateRetryCount++;
                    record("retrying unsubmitted immediate window: " + describe(error));
                } finally { this.windowOpen = false; }
            }
        } finally { this.immediateWindowBusy = false; }
    }

    /** Validate before acceptance; final default-FBO composite remains GL-owned, never inferred from format id. */
    /**
     * Takes the light map mirror again when its GL id is known but the mirror is missing.
     * <p>
     * One full supply per id, the same rule the textures follow: the mirror is dropped when its id is reallocated, and a
     * batch arriving before the next announcement had no way to take it again. Both the batch preparation and the
     * descriptor decision call this, because the decision runs first and used to refuse instead (measured: 798 format-5
     * refusals per run, with the light map supply sitting below the decision that turned the batch away).
     */
    private void ensureLightmapMirror() {
        if (this.lightmapTexture != null) {
            return;
        }
        if (this.lightmapGlId <= 0 && this.lightmapUnit > 0) {
            // The id is asked of GL on the unit the mod side named, rather than remembered: the reallocation path zeroes
            // it. The previous active unit is restored, so this is a query and not a state change.
            try {
                int previous = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
                // The mod side hands over OpenGlHelper.lightmapTexUnit, which is already the GL enum (GL_TEXTURE1 =
                // 33985), not a unit index. Adding GL_TEXTURE0 to it asked for unit 33985 - a unit that cannot exist -
                // so the light map's id was never learned and the terrain pass never opened.
                int wanted = this.lightmapUnit >= 0x84C0 ? this.lightmapUnit : 33984 + this.lightmapUnit;
                if (previous != wanted) {
                    org.lwjgl.opengl.GL13.glActiveTexture(wanted);
                }
                int bound = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
                if (previous != wanted) {
                    org.lwjgl.opengl.GL13.glActiveTexture(previous);
                }
                if (bound > 0) {
                    this.lightmapGlId = bound;
                }
            } catch (RuntimeException | LinkageError error) {
                this.lastLightmapSupplyFailure = "asking the light map unit failed: " + error;
            }
        }
        if (this.lightmapGlId > 0) {
            try {
                int[] size = textureSize(this.lightmapGlId);
                if (size[0] > 0 && size[1] > 0) {
                    org.soutalab.celtium.vulkan.VulkanGlTexture established =
                            celtium$registry().imageFor(this.lightmapGlId, size[0], size[1], false);
                    established.uploadFromGlTexture(this.lightmapGlId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                    celtium$registry().markFullyInitialized(this.lightmapGlId);
                    this.lightmapTexture = established;
                    this.lightmapReestablished++;
                    this.lightmapSuppliedOnDemand++;
                }
            } catch (RuntimeException | LinkageError error) {
                this.lastLightmapSupplyFailure = String.valueOf(error);
            }
        }
    }

    private boolean supportedImmediateTarget() {
        // Every condition names itself. One shared message covered all five and could not say which one refused, which is
        // how 870 format-2 refusals in a run stayed unnamed (measured: the stage marker said this gate, and the tally
        // still read "unrecorded").
        int fbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean sharedComposite = false;
        if (fbo == 0 && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            // No GL framebuffers exist under the stub, so every default-framebuffer draw - the world pass, menus, the
            // composite, overlays - lands in the owned image. This is that path and it must keep taking them.
            //
            // It was briefly changed to refuse them, on the theory that the finished-frame composite was being fed to
            // the entity-outline chain's off-target pair. The theory was wrong about the consequence and the change
            // froze every GL-less picture: the presented frame stopped advancing after world frame 3 while the run
            // still reported 63097 completed groups and successful presents, and the same frozen image (identical
            // sha256) appeared even with the glyph draws refused - so it was not caused by any one batch, it was the
            // world's own route into the image being refused. The glow defect is real and is not fixed here; the shared
            // off-target pair needs a fix that does not cut this path.
            this.defaultFramebufferCompositesTaken++;
            sharedComposite = true;
            this.immediateLastRefusal = "taken: the glless default-framebuffer draw goes into the owned image";
        } else if (fbo == 0) {
            // The default framebuffer is what Minecraft's own composite draws into: it blends an overlay over the frame on
            // the way to the screen. With the game rendering into the shared images that framebuffer is not where the
            // picture comes from any more, and the composite's content is the same picture - so it is taken and drawn there
            // instead, which puts the overlay into the image the next present shows. Measured: 874 such batches per run,
            // every one of them refused before this, all of them format 2, stride 24, one quad.
            int sharedTarget = this.gameFramebufferIsShared && this.frameTarget != null
                    ? this.frameTarget.adoptableFramebuffer(this.targetWidth, this.targetHeight) : 0;
            if (sharedTarget != 0) {
                this.defaultFramebufferCompositesTaken++;
                fbo = sharedTarget;
                sharedComposite = true;
                this.immediateLastRefusal = "taken: the default framebuffer composite goes into the shared image";
            } else {
                this.immediateLastRefusal = "target: the default framebuffer is bound";
                return false;
            }
        }
        int[] viewport = currentViewport();
        if (viewport[0] < 0 || viewport[1] < 0) {
            this.immediateLastRefusal = "target: the viewport origin is " + viewport[0] + "," + viewport[1];
            return false;
        }
        // The composite redirected above already proved the shared target exists and matches: asking ensureFrameTarget()
        // again would re-read the bound GL framebuffer - still the default one - and refuse on what the redirect just
        // decided. The framebuffer violation below is equally about the bound object, so it is skipped the same way.
        boolean targetReady = sharedComposite
                ? this.frameTarget != null && this.frameTarget.matches(this.targetWidth, this.targetHeight)
                : ensureFrameTarget();
        if (!targetReady) {
            this.immediateLastRefusal = "target: no frame target for " + this.targetWidth + "x" + this.targetHeight;
            return false;
        }
        // A GL viewport is a rectangle of the drawable, and any rectangle inside it is legal - the renderer applies it
        // as a Vulkan viewport and scissor rather than refusing the batch. Refusing was what kept the loading screen and
        // the main menu's panorama out of the frame: those are 256x256 draws made into the window (measured: drawFbo=0,
        // viewport 256x256, default framebuffer bound, `GuiMainMenu.renderSkybox` and Forge's splash), and every one of
        // them was answered "the viewport is 256x256, the target is 1280x720". Only a rectangle that does not fit inside
        // the target stays a refusal, with the numbers on the line: a viewport wider than the drawable is not something
        // to clip silently. (Measured and named, not fixed here: the game asks for 1281x720 on a 1280x720 target in the
        // frames before the world exists, which this rule still refuses.)
        if (viewport[2] <= 0 || viewport[3] <= 0) {
            this.immediateLastRefusal = "target: the viewport " + viewport[0] + "," + viewport[1] + " "
                    + viewport[2] + "x" + viewport[3] + " has no area";
            return false;
        }
        // A viewport larger than the target is legal in GL - it is clipped at rasterization - and it happens here: the
        // GUI-stage composite is drawn at ScaledResolution.getScaledWidth() * getScaleFactor() = 427 * 3 = 1281 columns
        // on a 1280-wide target (Minecraft.java:1007, measured by the projection worker). It is applied clipped to the
        // target rather than refused, because the alternative is a GUI stage drawn by nobody. The clip itself lives in
        // celtium$setGroupViewport, which is where the group's rectangle becomes the Vulkan viewport and scissor.

        if (!sharedComposite) {
            String violation = this.frameTarget.currentFramebufferViolation();
            if (violation != null) {
                this.immediateLastRefusal = "target: " + violation;
                return false;
            }
        }
        return true;
    }

    private PreparedImmediate prepareImmediate(int format, int textureId, long key, int[] state) {
        // Split of the 1.09 ms per prepareImmediate call: the pipeline lookup and the texture registry lookup (which
        // can reach OpenGL and mirror an image) against the pipeline/descriptor branch below.
        long prepLookupStart = System.nanoTime();
        VulkanTerrainPipeline terrain = pipelineFor(this.lastUiPass);
        // A fallback to this.terrainPipelineForFrame was tried here and measured preparePassFallbacks=0: the frame's world
        // pipeline is null at the same moments, which is what says those offers belong to the composite path's own layout
        // rule (CELTIUM_COMPOSITE_LAYOUT_REFUSED) and are not entity models being turned away. The rule was removed
        // rather than kept as an unreachable branch; only the named failure reason below is kept, because it is what
        // settled the question. Under the GL-less window the same absence means a UI-only screen, and those batches
        // must still draw: the standalone frame-compatible render pass stands in.
        this.prepTerrainPipelineNanos += System.nanoTime() - prepLookupStart;
        if (terrain == null && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            terrain = null; // keep the pass lookup result; the render pass handle below substitutes for it
        } else if (terrain == null) {
            // Every null return names its cause here, so the offer path has one reason to report instead of judging the
            // same condition again with a message that merged three different causes.
            this.prepareDescriptorFailures++;
            // Which pass was asked for, and whether there was one at all: "no pipeline for this pass" covers both "the
            // pass has not run yet in this instance" and "this pass has no pipeline", and the two need different fixes.
            this.prepareNoPassRefusals++;
            this.prepareNoPassFirstFrame = this.prepareNoPassRefusals == 1 ? this.frameId : this.prepareNoPassFirstFrame;
            this.prepareNoPassLastFrame = this.frameId;
            this.lastPrepareFailure = "format=" + format + " texture=" + textureId
                    + (this.lastUiPass == null ? " (no pass has run yet in this instance)" : " (no pipeline for this pass)");
            return null;
        }
        if (this.textureRegistry == null) this.textureRegistry = celtium$registry();
        long prepRegistryStart = System.nanoTime();
        VulkanGlTexture texture = this.textureRegistry.get(textureId);
        this.prepRegistryNanos += System.nanoTime() - prepRegistryStart;
        this.prepLookupNanos += System.nanoTime() - prepLookupStart;
        if (format != 1 && texture == null) {
            this.prepareDescriptorFailures++;
            this.lastPrepareFailure = "format=" + format + " texture=" + textureId
                    + " (no mirror for this GL id; registry=" + (this.textureRegistry == null ? "absent" : "present") + ")";
            return null;
        }
        long pass = terrain != null ? terrain.renderPass() : uiRenderPass();
        long prepBranchStart = System.nanoTime();
        Object pipeline;
        long descriptor = 0;
        if (format == 5) {
            long particleKey = key * 31L + (((long) VK_BLEND_FACTOR_SRC_ALPHA) << 20) + VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            // BH-07: the colour-blend state is the batch's own, like every other family's. This one used to assert
            // (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) for the RGB pair and the family default for alpha whatever the capture
            // said; ParticleManager itself calls `blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)`, and GL's glBlendFunc sets
            // the RGB *and* the alpha pair to that same pair - so the alpha pair the pipeline baked was the wrong one
            // for every particle batch in the scene. The depth and cull arguments are left as this family asserted
            // them: they are a different finding's subject and changing them here would move an accepted picture.
            VulkanImmediateParticlePipeline value = this.particlePipelineCache.computeIfAbsent(particleKey,
                    k -> VulkanImmediateParticlePipeline.create(this.context, pass, celtium$blendState(state),
                            true, false, false, celtium$vkCompareOp(state[7])));
            descriptor = celtium$descriptorSet(value.descriptorSetFor(texture, this.lightmapTexture), "prepare"); pipeline = value;
        } else if (format == 4) {
            // Which variant each ITEM batch gets, split by stage. The GUI stage is where the hotbar and the inventory
            // items are drawn, and whether they arrive lit decides whether the light map can darken them at all: the
            // full-bright rule at celtium$lightmapForGroup() writes a coordinate that only a lit group ever reads.
            boolean itemLit = state.length > 8 && state[8] != 0;
            if (itemLit) {
                this.itemLitGroups++;
                if (!this.worldPassActive) this.itemLitGuiGroups++;
            } else {
                this.itemUnlitGroups++;
                if (!this.worldPassActive) this.itemUnlitGuiGroups++;
            }
            VulkanImmediateItemPipeline value = this.itemPipelineCache.computeIfAbsent(key,
                    k -> VulkanImmediateItemPipeline.create(this.context, pass, celtium$blendState(state),
                            state[3] != 0, state[4] != 0, state[5] != 0, celtium$vkCompareOp(state[7]), itemLit));
            // The lit variant samples the light map on a second binding; the unlit one has only the model texture, so
            // the set is built for the layout this variant declares. A lit batch with no mirrored light map gets
            // descriptor 0 and is refused by name rather than drawn with a zero view (see the pipeline's guard).
            descriptor = celtium$descriptorSet(value.lit()
                    ? value.descriptorSetFor(texture, this.lightmapTexture)
                    : value.descriptorSetFor(texture), "prepare");
            pipeline = value;
        } else if (format == 3) {
            VulkanImmediateBlockPipeline value = this.blockPipelineCache.computeIfAbsent(key,
                    k -> VulkanImmediateBlockPipeline.create(this.context, pass, celtium$blendState(state),
                            state[3] != 0, state[4] != 0, state[5] != 0, celtium$vkCompareOp(state[7])));
            descriptor = celtium$descriptorSet(value.descriptorSetFor(texture, this.lightmapTexture), "prepare"); pipeline = value;
        } else if (format >= 2) {
            java.util.Map<Long, VulkanImmediateTexturePipeline> cache = format == 10 ? this.positionNormalPipelineCache
                    : format == 9 ? this.positionTexNormalPipelineCache
                    : format == 8 ? this.positionTexColorNormalPipelineCache : format == 7 ? this.positionOnlyPipelineCache
                    : format == 6 ? this.positionTexPipelineCache : this.texturedPipelineCache;
            VulkanImmediateTexturePipeline value = cache.get(key);
            if (value == null) {
                int compareOp = celtium$vkCompareOp(state[7]);
                // Experiment switch for the POSITION_NORMAL box: every one of its batches reaches this draw and the
                // box is absent from the full-mask picture (62 accepted, 62 drawn, no pixels), while the same group
                // drawn with format 10 alone lands exactly where GROUP_BOUNDS puts it. Turning its depth test off
                // separates "the fragments are rejected by the depth the window draws against" from "the draw does not
                // reach the image at all". Default on, so the shipped behaviour is unchanged.
                boolean positionNormalDepthTest = state[3] != 0 && POSITION_NORMAL_DEPTH_TEST;
                VulkanColorBlendState colorBlend = celtium$blendState(state);
                value = format == 10 ? VulkanImmediateTexturePipeline.createForPositionNormal(this.context, pass,
                        colorBlend, positionNormalDepthTest, state[4] != 0, state[5] != 0, compareOp)
                        : format == 9 ? VulkanImmediateTexturePipeline.createForPositionTexNormal(this.context, pass,
                        colorBlend, state[3] != 0, state[4] != 0, state[5] != 0, compareOp)
                        : format == 8 ? VulkanImmediateTexturePipeline.createForPositionTexColorNormal(this.context, pass,
                        colorBlend, state[3] != 0, state[4] != 0, state[5] != 0, compareOp)
                        : format == 7 ? VulkanImmediateTexturePipeline.createForPosition(this.context, pass,
                        colorBlend, state[3] != 0, state[4] != 0, state[5] != 0, compareOp)
                        : format == 6 ? VulkanImmediateTexturePipeline.createForPositionTex(this.context, pass,
                        colorBlend, state[3] != 0, state[4] != 0, state[5] != 0, compareOp)
                        : VulkanImmediateTexturePipeline.create(this.context, pass,
                        colorBlend, state[3] != 0, state[4] != 0, state[5] != 0, compareOp);
                cache.put(key, value);
            }
            descriptor = celtium$descriptorSet(value.descriptorSetFor(texture), "prepare"); pipeline = value;
        } else {
            boolean lineBatch = (key & (1L << 61)) != 0;
            VulkanColorBlendState colorBlend = celtium$blendState(state);
            pipeline = (lineBatch ? this.linePipelineCache : this.immediatePipelineCache).computeIfAbsent(key,
                    k -> lineBatch
                            ? VulkanImmediatePipeline.createForLines(this.context, pass, colorBlend,
                                    state[3] != 0, state[4] != 0, state[5] != 0, celtium$vkCompareOp(state[7]))
                            : VulkanImmediatePipeline.create(this.context, pass, colorBlend,
                                    state[3] != 0, state[4] != 0, state[5] != 0, celtium$vkCompareOp(state[7])));
            if (lineBatch) {
                this.lineBatchesNative++;
            }
        }
        this.prepBranchNanos += System.nanoTime() - prepBranchStart;
        if (format != 1 && descriptor == 0) {
            // "pipeline or descriptor preflight failed" merged three different causes into one line and a decision could
            // not be made from it: no mirror, a mirror that never received its full content, or a descriptor that could
            // not be built for an image that does exist. The three are told apart here because the fix differs.
            this.prepareDescriptorFailures++;
            this.lastPrepareFailure = "format=" + format + " texture=" + textureId
                    + (texture == null ? " (no mirror)" : texture.hasFullContent()
                            ? " (mirror has full content: descriptor creation failed)" : " (mirror never fully initialized)")
                    + " pipeline=" + (pipeline == null ? "null" : "present");
            return null;
        }
        return format == 1 || descriptor != 0
                ? new PreparedImmediate(pipeline, descriptor, texture == null ? 0L : texture.key())
                : null;
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
    /**
     * Records a pass for one off-target framebuffer's content and copies it back into the game's texture.
     * <p>
     * This is the interruption the structural finding called for. The body mirrors {@link #recordImmediateOnlyPass}, which
     * already does exactly this shape for the frame's pass: begin, set the viewport and scissor, draw the pending groups, end.
     */
    /**
     * Removes the pending groups that belong to one render target, leaving every other group in the queue.
     * <p>
     * The off-target pass runs before the main pass and filters to a single target. Retiring everything there - which is what
     * the code did before audit A32 - destroyed the main pass's accepted geometry: accepted, counted, and thrown away before it
     * could be drawn. Static and device-free so the CPU contract check can exercise the rule with two independent targets.
     *
     * @return how many groups were left behind for other targets
     */
    public static int retireGroupsForTarget(java.util.List<ImmediateGroup> groups, long targetIdentity) {
        return ImmediateGroupQueue.retireGroupsForTarget(groups, targetIdentity);
    }

    /** {@return whether the pending queue holds non-empty geometry for a target}, asked before a pass is begun. */
    /** {@return how many non-empty pending groups belong to a target}, counted when a pass is about to run. */
    /**
     * Whether this offer is the game copying the finished frame onto itself.
     * <p>
     * Under the GL-less window the game's composite is issued with no Framebuffer bound - the frame mixin reports that
     * as {@code bindFramebuffer(0L, false)} - so the target identity is 0 while the image being sampled is the frame
     * image's own colour texture, named by the main framebuffer's entry in the identity map. That combination is the
     * self-copy: the swap point already presents the frame, so there is nothing for it to do, and drawing it is what
     * polluted the outline chain's input image.
     * <p>
     * <b>This predicate is not used, and must not be used as a skip rule.</b> Skipping what it matches (measured
     * 2026-10-11, glow scene) made the delivered frame a single colour: under the adoption path the fbo==0 composite is
     * not a copy of the frame onto itself, it is the pass that DELIVERS the game's finished frame into the delivered
     * image, and its sampled texture is the one the game framebuffer was adopted from. Refusing it loses the world (the
     * earlier version of this mistake froze the picture), routing it into the delivered image wipes it (this one), and
     * skipping it empties it. All three shapes were measured before the rule was withdrawn.
     * <p>
     * What that means for the glow: the off-target pair cannot be freed by changing where identity 0 is drawn. The pair
     * is the game's adopted colour image, so the world, the composite and the entity silhouette all legitimately pass
     * through it, and the outline chain reading it as its input is the actual defect. The fix belongs where the chain's
     * input image is chosen: it needs an image of its own rather than the shared pair.
     * <p>
     * <b>That fourth change is the one that is in the tree (2026-10-11):</b> a second owned pair was added beside the
     * first, identity 0 keeps the pair above and every other non-main identity - the outline framebuffer's silhouette
     * is the measured one - renders into the new pair, which the chain adopts, publishes and hands back instead. No
     * rule about what identity 0 means changed, which is why the delivered frame survived the change: the routing is
     * by identity and the chain's input image, not the drawing of the composite, is what moved.
     */
    private boolean celtium$isScreenSelfComposite() {
        if (!org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled() || this.currentRenderTarget != 0L) {
            return false;
        }
        int frameColorTexture = framebufferColorTextureName(this.mainTargetIdentity);
        return frameColorTexture != 0 && this.immediateState.boundTextureName() == frameColorTexture;
    }

    /** {@return how many pending non-empty groups belong to a target other than the main one}. */
    private int pendingGroupsForOtherTargets() {
        int count = 0;
        for (ImmediateGroup group : this.immediateGroups) {
            if (group.target != this.mainTargetIdentity
                    && !group.mesh.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int countGroupsForTarget(long targetIdentity) {
        int count = 0;
        for (ImmediateGroup group : this.immediateGroups) {
            if (group.target == targetIdentity && !group.mesh.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** {@return a per-target line}: offers (the map the offer branch keeps), groups at pass time, drawing passes. */
    private String offTargetPerTarget() {
        if (this.offTargetBatchesByTarget.isEmpty() && this.offTargetPassGroupsByTarget.isEmpty()) {
            return "none";
        }
        java.util.TreeSet<Long> ids = new java.util.TreeSet<Long>();
        ids.addAll(this.offTargetBatchesByTarget.keySet());
        ids.addAll(this.offTargetPassGroupsByTarget.keySet());
        ids.addAll(this.offTargetDrawnByTarget.keySet());
        StringBuilder summary = new StringBuilder();
        for (Long idBoxed : ids) {
            java.util.concurrent.atomic.AtomicInteger offered = this.offTargetBatchesByTarget.get(idBoxed);
            // The units are named in the columns because they are not the same and reading them as if they were
            // produced a false mechanism: `offered` counts batches (one increment per accepted batch) while the pass
            // column counts groups, so "677 offered, 32 atPass" is not 645 lost groups - it is 677 batches in 32
            // groups that reached a pass. The row is evidence only when the two are read in their own units.
            summary.append(Long.toHexString(idBoxed.longValue()))
                    .append(":batchesOffered=").append(offered == null ? 0 : offered.get())
                    .append(",groupsAtPass=").append(this.offTargetPassGroupsByTarget.getOrDefault(idBoxed, Integer.valueOf(0)))
                    .append(",drawnPasses=").append(this.offTargetDrawnByTarget.getOrDefault(idBoxed, Integer.valueOf(0)))
                    .append(",pair=").append(pairOfOffTargetIdentity(idBoxed.longValue()))
                    .append(' ');
        }
        return summary.toString().trim();
    }

    private void recordImmediateOnlyPass(MemoryStack stack, VulkanTerrainPipeline terrain) {
        this.immediateOnlyPasses++;
        // Recorded for the model draw: it needs a render pass, and this is the one the frame is using. When no
        // terrain pass has run (the GL-less menu is all UI batches), the standalone frame-compatible pass stands in.
        this.terrainPipelineForFrame = terrain;
        this.frameTargetKnown = true;
        if (this.gpuStalled) {
            // The pass-level recording is closed too: with the buffer never begun, vkCmdBeginRenderPass, vkCmdSetViewport and
            // vkCmdSetScissor were still being recorded into it, which is what the steady-state injection reported.
            return;
        }
        long renderPass = terrain != null ? terrain.renderPass() : uiRenderPass();
        // Which image this pass draws into: the frame's, or one of the two owned pairs. The rule is by identity,
        // because identity is the only thing that separates the two streams that used to share one image.
        long framebuffer;
        // NOT "identity 0 is main". That was tried and it overdraws the frame: routing the default-framebuffer
        // passes into the delivered image makes the game's finished-frame composite - a copy of the image onto
        // itself under the stub - land on top of the world, and the presented frame came out a single colour
        // (presentedDistinctColours=1). Refusing those passes outright was tried before that and froze the picture,
        // because the same identity also carries the world pass. Both halves of that stream need different answers,
        // which is a design question (skip the self-composite, keep the pass) rather than a one-line routing change.
        //
        // What identity 0 keeps is the pair it has always written: it is the default framebuffer, and under the stub
        // its draws are the game's own finished-frame composites. Every OTHER non-main identity is one of the game's
        // framebuffers - the entity outline's `entityOutlineFramebuffer` is the measured one - and those get the
        // chain pair, which is the image the post chain adopts. That split is the fix for the shared pair: the chain
        // used to read an image the composite had just painted a copy of the whole scene into. It is a routing rule
        // and not a skip rule: both streams are still drawn, each into an image of its own.
        boolean chainIdentity = this.currentRenderTarget != 0L;
        GllessFrameTarget offTarget = this.currentRenderTargetIsMain ? null : offTargetTarget();
        // The image this pass attaches, so the sampling-side transition below can leave it alone. Default is the frame
        // image, which is what both the plain path and the fallback path attach.
        this.passAttachedColorImage = this.frameTarget.colorImage();
        if (offTarget != null) {
            try {
                if (chainIdentity) {
                    offTarget.ensureChainPair(this.targetWidth, this.targetHeight);
                    // CELTIUM-OUTLINE-COMPOSITE: the image this pair holds was sampled last frame by whatever
                    // composites it (the game's own outline composite binds the published name), so it can be in
                    // SHADER_READ_ONLY while this pass clears it - which declares GENERAL - and then attaches it. The
                    // hand-back is recorded before both. A pair recreated at a new size is unpublished here instead:
                    // its wrapper names a destroyed image, and a barrier on that is a validation error rather than a
                    // missing glow.
                    this.celtium$syncPublishedChainOutput(offTarget.chainPairColorImage());
                    // Every other published name gets the same hand-back here, before the clear and the attach: the
                    // sampling transition leaves the pair image in SHADER_READ_ONLY_OPTIMAL, and this pass begins with
                    // initialLayout GENERAL - which is VUID-vkCmdBeginRenderPass-initialLayout-00900 unless the image is
                    // moved back first. The two VUIDs are one lifecycle: sampled -> read-only, attached -> general.
                    celtium$transitionPublishedFrameTextures(true);
                    if (!offTarget.chainPairAcquired() || this.chainPairClearedFrame != this.frameId) {
                        // Outside the pass, which is where the clear and its first-use barrier belong, and once per
                        // frame: the game clears a framebuffer when it binds it, and clearing per window would erase
                        // what an earlier window of the same frame already drew there. The acquire state is asked of
                        // the pair as well as the frame, because a pair recreated after this frame's clear is
                        // UNDEFINED again while the frame flag still says otherwise - which is the layout error a
                        // dimension change produced.
                        if (offTarget.chainPairNeedsSelfTest()) {
                            // The one legal-use sequence of the images this pair now holds, recorded through the
                            // validation layer and reported once per creation: it is the same clear the ordinary path
                            // records, so it is the pair's first clear rather than an extra one.
                            record(offTarget.chainPairSelfTest(this.commandBuffer));
                        } else {
                            offTarget.clearChainPair(this.commandBuffer);
                        }
                        this.chainPairClearedFrame = this.frameId;
                        this.chainPairClears++;
                    }
                    framebuffer = ensureChainPairFramebuffer(renderPass);
                    this.passAttachedColorImage = offTarget.chainPairColorImage();
                    // The game's own framebuffer whose draws are landing in this pair: its colour texture name is what
                    // the game composites with, and under GL-less that texture has an extent and no pixels (the stub's
                    // glTexImage2D with a null pointer records the size and no data). Publishing the pair's image under
                    // that name is what gives the composite's batch something to sample - the same call the entity
                    // outline's adoption makes, applied here because the vanilla loading screen has no post chain to
                    // adopt anything: SplashProgress draws the Mojang logo into its own framebuffer and composites it,
                    // so with no entry in the registry the composite samples an empty mirror and the window shows a
                    // black loading screen. Nothing is created, resized, transitioned or freed here.
                    long[] reported = framebufferColorTexture(this.currentRenderTarget);
                    if (reported != null && reported[0] != 0L) {
                        celtium$publishFrameTexture((int) reported[0], offTarget.chainPairColorImage(),
                                offTarget.chainPairColorView(), this.targetWidth, this.targetHeight);
                    }
                } else {
                    offTarget.ensureOffTarget(this.targetWidth, this.targetHeight);
                    // No hand-back here: the published image is the chain pair's, and this pass attaches the default
                    // pair. Transitioning the published image is the sampling site's job, below.
                    if (!offTarget.offTargetAcquired() || this.defaultPairClearedFrame != this.frameId) {
                        offTarget.clearOffTarget(this.commandBuffer);
                        this.defaultPairClearedFrame = this.frameId;
                        this.defaultPairClears++;
                    }
                    framebuffer = ensureOffTargetFramebuffer(renderPass);
                    this.passAttachedColorImage = offTarget.offTargetColorImage();
                }
            } catch (RuntimeException | LinkageError error) {
                this.offTargetPassFailures++;
                this.lastOffTargetFailure = describe(error);
                record("off-target pass refused, drawing into the frame image instead: " + describe(error));
                framebuffer = ensureFramebuffer(renderPass);
            }
        } else {
            framebuffer = ensureFramebuffer(renderPass);
        }
        org.lwjgl.vulkan.VkRenderPassBeginInfo begin =
                org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack).sType$Default();
        begin.renderPass(renderPass);
        begin.framebuffer(framebuffer);
        org.lwjgl.vulkan.VkRect2D.Buffer area = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
        area.get(0).offset().set(0, 0);
        area.get(0).extent().set(this.targetWidth, this.targetHeight);
        begin.renderArea(area.get(0));
        if (this.publishedChainOutput != null
                && celtium$queuedForTargetUsesPublished(this.activeImmediateTarget)) {
            // The immediate pipelines declare SHADER_READ_ONLY_OPTIMAL in their descriptors while the published
            // outline image can be in GENERAL here: an off-target pass hands it back before attaching it, and a frame
            // in which the chain did not run never moves it forward. The transition belongs to the submission that
            // draws the sampling batch - recorded outside the pass, before the draw, which is what this site is.
            this.publishedChainOutput.ensureSampledLayout(this.commandBuffer);
        }
        // Every other name published over the pair's image gets the same transition at the same site. A name published
        // for a game framebuffer whose draws land in the pair (the vanilla loading screen's, measured) is sampled from a
        // pass whose descriptor declares SHADER_READ_ONLY while the pair's own layout is GENERAL: leaving it there is
        // exactly the VUID-vkCmdDraw-None-09600 the vsync leg reported on three distinct images at vkQueueSubmit. The
        // transition is recorded outside the pass, before the draw, like the outline's.
        celtium$transitionPublishedFrameTextures(false);
        vkCmdBeginRenderPass(this.commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);
        this.activeImmediateTarget = this.currentRenderTarget;
        this.commandState.beginPass(this.activeImmediateTarget);
        org.soutalab.celtium.vulkan.VulkanDisplayListCensus.setRenderPassOpen(true);
        setViewportAndScissor(stack);
        if (!this.currentRenderTargetIsMain) {
            this.offTargetPassGroupsByTarget.merge(this.currentRenderTarget, countGroupsForTarget(this.currentRenderTarget),
                    Integer::sum);
        }
        // A window of its own: its own render pass over the frame target, so the depth it tests against is whatever
        // that pass loads or the game's last clear left there.
        this.immediateDrawSite = "immediate-only-window";
        drawPendingImmediate(Long.valueOf(this.activeImmediateTarget));
        vkCmdEndRenderPass(this.commandBuffer);
        this.commandState.endPass();
        org.soutalab.celtium.vulkan.VulkanDisplayListCensus.setRenderPassOpen(false);
    }

    /**
     * {@return the mesh a new immediate group writes into}: an arena slice when one can be handed out, an owning mesh
     * when the arena's slots are all held by submissions that have not completed.
     * <p>
     * The fallback is bounded and counted rather than silent: a frame whose groups are still held (waiting for another
     * render target) can leave both slots reserved, and the right answer then is the old behaviour for that group -
     * its own buffers - not a reset of memory a later submission may still be reading, and not a dropped draw.
     */
    private VulkanDynamicMesh celtium$newImmediateMesh() {
        if (this.frameArena == null) {
            this.frameArena = new VulkanFrameArena(this.context);
        }
        VulkanDynamicMesh borrowed = this.frameArena.openMesh();
        if (borrowed != null) {
            return borrowed;
        }
        this.frameArenaFallbackGroups++;
        return new VulkanDynamicMesh(this.context);
    }

    /** Moves the open group into the flush list together with the matrices it was built with. */
    private void closeCurrentImmediateGroup() {
        // Group boundary for the font batching (task-59): a held run is closed out through the normal path here, so it
        // cannot be appended to a group that opens after it.
        celtium$flushPendingFontBatch("boundary");
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
            if (this.formatProbeLogged < 6 && (this.currentGroupFormatId == 5 || this.currentGroupFormatId == 3)) {
                // Raw GL projection against the one that is actually stored and pushed, for the two families that both use
                // a light map. The stored one has been through remapClipDepth and the pushed one through vulkanProjection,
                // so if the depth row changes shape between the two, that is where a wrong depth (or a wrong place) comes
                // from - and the raw values on the left are what GL itself uses.
                this.formatProbeLogged++;
                System.out.println("[Celtium] CELTIUM_PROJ_PROBE fmt=" + this.currentGroupFormatId
                        + " rawDepth=(" + round3(this.glProjection[2]) + "," + round3(this.glProjection[6]) + ","
                        + round3(this.glProjection[10]) + "," + round3(this.glProjection[14]) + ")"
                        + " storedDepth=(" + round3(this.currentGroupProjection[2]) + ","
                        + round3(this.currentGroupProjection[6]) + "," + round3(this.currentGroupProjection[10]) + ","
                        + round3(this.currentGroupProjection[14]) + ")"
                        + " rawW=(" + round3(this.glProjection[11]) + "," + round3(this.glProjection[15]) + ")"
                        + " mvT=(" + round3(this.currentGroupModelView[12]) + "," + round3(this.currentGroupModelView[13])
                        + "," + round3(this.currentGroupModelView[14]) + ")"
                        // The mirror's own account of its contents: a texture that only ever received patches would be sampled
                        // in the parts that never arrived, which is what a black particle sprite would look like.
                        + " atlasFull=" + (this.atlasTexture != null && this.atlasTexture.hasFullContent())
                        + " lightmapFull=" + (this.lightmapTexture != null && this.lightmapTexture.hasFullContent())
                        + " groupTexFull=" + this.groupTextureFull());
            }
            if (this.immediateGroupsClosed == 400 || this.immediateGroupsClosed == 1500
                    || this.immediateGroupsClosed == 4000 || this.immediateGroupsClosed == 8000) {
                // A late group, in full: the first eight groups belong to the loading screen and say nothing about the
                // matrices the frame itself is drawn with, which is what a picture made only of the clear colour
                // needs to be judged against.
                StringBuilder proj = new StringBuilder();
                StringBuilder mv = new StringBuilder();
                for (int i = 0; i < 16; i++) {
                    proj.append(round3(this.currentGroupProjection[i])).append(i == 15 ? "" : ",");
                    mv.append(round3(this.currentGroupModelView[i])).append(i == 15 ? "" : ",");
                }
                System.out.println("[Celtium] CELTIUM_MATRIX_DUMP group=" + this.immediateGroupsClosed
                        + " fmt=" + this.currentGroupFormatId + " batches=" + this.currentGroupBatches
                        + " vertices=" + groupVertices + " indices=" + this.currentImmediateMesh.indexCount()
                        + " worldPass=" + this.worldPassActive
                        + " target=" + this.currentGroupTarget + " viewport=" + this.currentGroupViewport
                        + "\n  proj=[" + proj + "]\n  mv=[" + mv + "]"
                        + "\n  raw proj=[" + round3(this.glProjection[0]) + "," + round3(this.glProjection[5]) + ","
                        + round3(this.glProjection[10]) + "," + round3(this.glProjection[14]) + "]");
            }
            if (this.immediateMatrixLogged < 8
                    || (ENQ_ALL_GROUPS && (ENQ_FORMAT < 0 || ENQ_FORMAT == this.currentGroupFormatId))) {
                // The immediate path's own matrices and batch identity, for the first few groups: the first one was normal,
                // so the groups that draw the dark squares have to be found among the others. With
                // -Dceltium.vulkan.enqAll the print covers every group (optionally one format), because the first eight
                // belong to the loading screen and cannot say which matrices the frame an artefact is in was drawn with.
                this.immediateMatrixLogged++;
                System.out.println("[Celtium] CELTIUM_IMMEDIATE_ENQ frame=" + this.frameId
                        + " group=" + this.immediateGroupsClosed + " proj=" + round3(projection[2]) + ","
                        + round3(projection[6]) + "," + round3(projection[10]) + "," + round3(projection[14])
                        + " mvT=" + round3(modelView[12]) + "," + round3(modelView[13]) + "," + round3(modelView[14])
                        + " fmt=" + this.currentGroupFormatId + " texture=" + this.currentGroupTexture
                        + " batches=" + this.currentGroupBatches
                        + " target=" + this.currentGroupTarget
                        + " mirrored=" + (this.textureRegistry != null && this.currentGroupTexture != 0
                                && this.textureRegistry.get(this.currentGroupTexture) != null)
                        + " worldPass=" + this.worldPassActive);
            }
            ImmediateGroup group = new ImmediateGroup(this.currentImmediateMesh, projection, modelView,
                    this.currentGroupFormatId, this.currentGroupTexture,
                    this.currentGroupStateKey, this.currentGroupState, this.currentGroupTarget);
            group.color = this.currentGroupColor;
            group.blendSrc = this.currentGroupBlendSrc;
            group.blendDst = this.currentGroupBlendDst;
            group.prepared = this.currentPreparedImmediate;
            group.batches = this.currentGroupBatches;
            group.viewportValues = this.currentGroupViewportValues;
            group.texMatrix = this.currentGroupTexMatrix;
            // BH-05: the payload captured when this group was opened, not one rebuilt from the live light state now.
            group.lightParams = this.currentGroupLightParams;
            // The coordinate captured when the group was opened, not the live one: a group the world pass left pending
            // is flushed by endWorldPass after that method has already cleared worldPassActive, so the live value here
            // would say "GUI stage" about the hand and the entities. See currentGroupLightmapCoords.
            group.lightmapCoords = this.currentGroupLightmapCoords;
            // The state captured when the group was opened, not the live state: see currentGroupFog.
            group.fog = this.currentGroupFog;
            group.scissorBox = this.currentGroupScissor;
            group.scissorEnabled = this.currentGroupScissorEnabled;
            celtium$noteFogCaptureDrift(group);
            group.stamp = new GroupStamp(this.frameId, this.targetGeneration, false, this.worldEpoch);
            this.immediateGroups.add(group);
            if (GROUP_BOUNDS_DIAGNOSTICS) {
                // Off by default: with the switch off this method keeps the "no caller" property it had, and no line
                // reaches stdout or the report, so runs on the default flags stay comparable with older artifacts.
                reportGroupBounds(group);
            }
        } else if (this.currentImmediateMesh != null) {
            this.currentImmediateMesh.delete();
        }
        this.currentImmediateMesh = null;
        this.currentGroupUsed = false;
    }

    /**
     * Ends the backend's use of the queue: the idle wait makes the recorded groups unreachable, and then everything the
     * queue still holds is retired.
     * <p>
     * The drain runs again after the move, not only before it. The groups moved here were never submitted, so they are
     * safe to free immediately, and leaving them in the retired list means the last teardown of the process - the one
     * call that has no later drain - would free their meshes never rather than late. Emptying the list in the same call
     * is what makes "released exactly once" true on the final path.
     */
    private void discardImmediateGroups() {
        if (!waitForIdle()) return;
        releaseRetiredImmediateGroups();
        int held = this.immediateGroups.size();
        for (ImmediateGroup group : this.immediateGroups) {
            this.retiredImmediateGroups.add(group);
        }
        this.immediateGroups.clear();
        this.pendingImmediateIndices = 0;
        if (this.currentImmediateMesh != null) {
            this.currentImmediateMesh.delete();
            this.currentImmediateMesh = null;
        }
        this.currentGroupUsed = false;
        this.groupsDiscardedAtFullTeardown += held;
        releaseRetiredImmediateGroups();
    }

    /**
     * {@return a machine-readable summary of what this backend actually drew}. The fixture reads it to decide
     * whether a run really rendered terrain: selecting the backend by name is not evidence that any pass reached the
     * queue, so submissions, skips and errors are counted instead.
     */
    /**
     * BH-10's capture histogram as one bracketed field, non-zero slots only.
     * <p>
     * The number that decides the Fancy-cloud question is the {@code 0x0} slot: it is the depth-only pass. A leg
     * whose histogram has {@code 0x0:0} did not draw that pass, and no crop can show the mask's effect there however
     * the flags are set.
     */
    private String colorMaskHistogramText() {
        StringBuilder text = new StringBuilder("[");
        boolean any = false;
        for (int mask = 0; mask < this.colorMaskHistogram.length; mask++) {
            if (this.colorMaskHistogram[mask] == 0L) {
                continue;
            }
            if (any) {
                text.append(' ');
            }
            text.append("0x").append(Integer.toHexString(mask)).append(':').append(this.colorMaskHistogram[mask]);
            any = true;
        }
        return text.append(']').toString();
    }

    public String verificationSummary() {
        return "celtium/vulkan-check passes=" + this.passSubmissions + " skipped=" + this.passSkips + " errors="
                + this.passErrors + " lastDraw=" + this.lastDrawCommands + " lastSections=" + this.lastSectionsDrawn
                + " skipReason=" + (this.lastSkipReason == null ? "none" : this.lastSkipReason.replace(' ', '_'))
                + " immediateState=" + this.immediateStateCalls + " immediateOffered=" + this.immediateBatchesOffered
                + " frameId=" + this.frameId + " flushCount=" + this.flushCount
                + " orderPreservingFlushes=" + this.orderPreservingFlushes
                + " actualFlushes=" + this.actualFlushes
                + " actualFlushMillis=" + (this.actualFlushNanos / 1000000L)
                + " actualFlushWorstMillis=" + (this.actualFlushWorstNanos / 1000000L)
                + " actualFlushCpuMillis=" + (this.actualFlushCpuNanos / 1000000L)
                // Task-60: the wait facts, so the JSON report names the wait instead of a reader guessing. The
                // bootstrap path's waits have no API bound at all; the frame path's fence wait has a 5 s one and is
                // the only place that sets gpuStalled. Worst is the number that says whether either is a hazard.
                + " bootstrapQueueWaits=" + VulkanContext.bootstrapQueueWaits
                + " bootstrapQueueWaitMillis=" + VulkanContext.bootstrapQueueWaitMillis
                + " bootstrapQueueWaitWorstMillis=" + VulkanContext.bootstrapQueueWaitWorstMillis
                + " bootstrapSubmitWaits=" + VulkanContext.bootstrapSubmitWaits
                + " bootstrapSubmitWaitMillis=" + VulkanContext.bootstrapSubmitWaitMillis
                + " bootstrapSubmitWaitWorstMillis=" + VulkanContext.bootstrapSubmitWaitWorstMillis
                + " " + this.context.uploadBatchSummary()
                + " " + (this.frameArena == null ? "frameArena=not-created" : this.frameArena.describe())
                + " frameArenaFallbackGroups=" + this.frameArenaFallbackGroups
                + " " + VulkanHostBuffer.allocationSummary()
                + " uniform" + (this.uniforms == null ? "=none" : this.uniforms.growthSummary())
                + " readResolvedFromLastDrawnTarget=" + this.readResolvedFromLastDrawnTarget
                + " unflushedTargetGroupsDropped=" + this.unflushedTargetGroupsDropped
                + " unflushedTargetDropTargets=[" + unflushedTargetDropSummary() + "]"
                + " drainedOtherTargetPasses=" + this.drainedOtherTargetPasses
                + " flushEntries=[" + flushEntrySummary() + "]"
                + " unflushedTargetPassFailures=" + this.unflushedTargetPassFailures
                + " lastUnflushedTargetFailure=" + (this.lastUnflushedTargetFailure == null ? "none"
                        : this.lastUnflushedTargetFailure.replace(' ', '_').substring(0,
                                Math.min(120, this.lastUnflushedTargetFailure.length())))
                + " submitTimeouts=" + VulkanContext.submitTimeouts
                + " gpuStalled=" + this.gpuStalled
                + " deviceWaitToken=" + this.deviceWaitToken
                + " stalledGeometryDrops=" + this.stalledGeometryDrops
                + " stalledGeometryDroppedBatches=" + this.stalledGeometryDroppedBatches
                + " stallOrigin=" + (this.stallOrigin == null ? "none" : this.stallOrigin.replace(' ', '_'))
                + " windowFlushFailures=" + this.windowFlushFailures
                + " windowFlushFailureFrame=" + this.windowFlushFailureFrame
                + " windowFlushDroppedBatches=" + this.windowFlushDroppedBatches
                + " groupViewportSets=" + this.groupViewportSets
                + " cullEnabledGroups=" + this.cullEnabledGroups
                + " cullDisabledGroups=" + this.cullDisabledGroups
                // BH-11 and BH-12's counters, at the same place as BH-08's. acceptance found
                // immediateDepthBiasedGroups declared and incremented with zero print sites, so "the bias was
                // applied" had no evidence in any artifact; the narrowed-line total had the same gap (only the
                // first four events printed). Both are now in the verification summary.
                + " immediateDepthBiasedGroups=" + this.immediateDepthBiasedGroups
                // The two halves that decide whether the bias can fire at all: the call reaching the stub, and the
                // enable bit. acceptance measured 0 groups with the break overlay present, so one of these is false
                // and the pair says which without another round of guessing.
                + " polygonOffsetCalls=" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetCallCount()
                + " polygonOffsetFillEnabled=" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetFillEnabled()
                + " polygonOffsetCalled=" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetCalled()
                + " lineBatchesNarrowed=" + this.lineBatchesNarrowed
                // BH-07/BH-10/BH-03's counters, at the same place as BH-08's: captured per batch, so a leg says how
                // many batches carried an alpha pair the old pipelines could not express, how many carried a write
                // mask that was not all four channels, and which alpha comparison reached the shader. Before the fix
                // the first two were zero by construction (the capture never read them) and the third did not exist.
                + " blendGroups=" + this.blendGroups
                + " blendAlphaNonDefaultGroups=" + this.blendAlphaNonDefaultGroups
                + " partialColorMaskGroups=" + this.partialColorMaskGroups
                + " lastPartialColorMask=0x" + Integer.toHexString(this.lastPartialColorMask)
                + " colorMaskHistogram=" + colorMaskHistogramText()
                + " nonAddBlendEquationGroups=" + this.nonAddBlendEquationGroups
                + " scissorBoundarySplits=" + this.scissorBoundarySplits
                + " scissorCapturedGroups=" + this.scissorCapturedGroups
                + " alphaFuncGroups=" + this.alphaFuncGroups
                + " nonGreaterAlphaFuncGroups=" + this.nonGreaterAlphaFuncGroups
                + " alphaTestDisabledGroups=" + this.alphaTestDisabledGroups
                + " lastAlphaFunc=" + this.lastAlphaFunc
                + " windowFlushRetainedBatches=" + this.windowFlushRetainedBatches
                + " lastWindowFlushFailure=" + (this.lastWindowFlushFailure == null ? "none"
                        : this.lastWindowFlushFailure.replace(' ', '_').substring(0,
                                Math.min(200, this.lastWindowFlushFailure.length())))
                // The ordering flush's own bounded catch: the site the user's loading screen latched at before it was
                // bounded, so the next leg reads whether it still happens and what it throws.
                + " orderingFlushFailures=" + this.orderingFlushFailures
                + " orderingFlushDroppedBatches=" + this.orderingFlushDroppedBatches
                + " lastOrderingFlushFailure=" + (this.lastOrderingFlushFailure == null ? "none"
                        : this.lastOrderingFlushFailure.replace(' ', '_').substring(0,
                                Math.min(200, this.lastOrderingFlushFailure.length())))
                + " batchCallCount=" + batchCallCount
                + " batchCallMillis=" + (batchCallNanos / 1000000L)
                + " batchCallWorstMillis=" + (batchCallWorstNanos / 1000000L)
                + " batchCallOpeningMillis=" + (batchCallOpeningNanos / 1000000L)
                + " batchCallOpeningCount=" + batchCallOpeningCount
                + " batchCallCpuMillis=" + (batchCallCpuNanos / 1000000L)
                + " threadCpuSupported=" + org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.THREAD_CPU_SUPPORTED
                + " threadWaitedMillis=" + threadWaitedMillis()
                + " threadBlockedMillis=" + threadBlockedMillis()
                + " threadCpuTotalMillis=" + (threadCpuNanos() / 1000000L)
                + " uniformPreparationFailures=" + this.uniformPreparationFailures
                + " lastUniformPreparationFailure=" + this.lastUniformPreparationFailure
                + " regionUploadFailures=" + this.regionUploadFailures
                + " sectionsLeftUnbuilt=" + this.sectionsLeftUnbuilt
                + " lastRegionUploadFailure=" + this.lastRegionUploadFailure
                + " innerWallCpu=[preflight " + (this.prepareImmediateNanos / 1000000L) + "/"
                + (this.preflightCpuNanos / 1000000L) + ", append " + (this.appendNanos / 1000000L) + "/"
                + (this.appendCpuNanos / 1000000L) + ", groupCreate " + (this.groupCreateNanos / 1000000L) + "/"
                + (this.groupCreateCpuNanos / 1000000L) + "]"
                + " prepareImmediateCalls=" + this.prepareImmediateCalls
                + " prepareImmediateMillis=" + (this.prepareImmediateNanos / 1000000L)
                + " groupCreateCount=" + this.groupCreateCount
                + " groupCreateMillis=" + (this.groupCreateNanos / 1000000L)
                + " appendCount=" + this.appendCount
                + " appendMillis=" + (this.appendNanos / 1000000L)
                + " programQueryCount=" + this.programQueryCount
                + " programQueryMillis=" + (this.programQueryNanos / 1000000L)
                + " matrixCopyMillis=" + (this.matrixCopyNanos / 1000000L)
                + " captureStateMillis=" + (this.captureStateNanos / 1000000L)
                + " stateKeyMillis=" + (this.stateKeyNanos / 1000000L)
                + " groupCloneCount=" + this.groupCloneCount
                + " groupCloneMillis=" + (this.groupCloneNanos / 1000000L)
                + " prepLookupMillis=" + (this.prepLookupNanos / 1000000L)
                + " prepTerrainPipelineMillis=" + (this.prepTerrainPipelineNanos / 1000000L)
                + " prepRegistryMillis=" + (this.prepRegistryNanos / 1000000L)
                + " regAnnouncedCalls=" + VulkanTextureRegistry.ANNOUNCED_CALLS
                + " regAnnouncedMillis=" + (VulkanTextureRegistry.ANNOUNCED_NANOS / 1000000L)
                + " regStaleCalls=" + VulkanTextureRegistry.STALE_CALLS
                + " regStaleMillis=" + (VulkanTextureRegistry.STALE_NANOS / 1000000L)
                + " regHitCalls=" + VulkanTextureRegistry.HIT_CALLS
                + " regAnnouncedSkips=" + VulkanTextureRegistry.ANNOUNCED_SKIPS
                + " prepBranchMillis=" + (this.prepBranchNanos / 1000000L)
                + " descriptorHits=" + VulkanImmediateTexturePipeline.DESCRIPTOR_HITS
                + " descriptorMisses=" + VulkanImmediateTexturePipeline.DESCRIPTOR_MISSES + " clearOrderFlushes=" + this.clearOrderFlushes
                + " offTargetByTarget=[" + this.offTargetSummary() + "] offTargetTargets=" + this.offTargetTargetsCreated
                + " refusedBeforeAppend=" + this.refusedBeforeAppend
                + " partialMirrorRefusals=" + this.partialMirrorRefusals
                + " partialMirrorsCompleted=" + this.partialMirrorsCompleted
                + " offTargetPassesSkippedEmpty=" + this.offTargetPassesSkippedEmpty
                + " offTargetPerTarget=[" + offTargetPerTarget() + "]"
                + " offTargetPairs=[" + frameTargetPairSummary() + "]"
                + " offTargetPairClears=[default-pair=" + this.defaultPairClears
                + ",chain-pair=" + this.chainPairClears + "]"
                + " gameClearAttribution=[latchedForFrame=" + this.gameClearsLatchedForFrame
                + ",routedToPairs=" + this.gameClearsRoutedToPairs + "]"
                + " offTargetPairFramebuffers=[default-pair=" + this.offTargetFramebufferCreations
                + ",chain-pair=" + this.chainPairFramebufferCreations + "]"
                + " otherTargetGroups=[atMainFlush=" + this.otherTargetGroupsAtMainFlush
                + ",discarded=" + this.otherTargetGroupsDiscarded
                + ",windowFlush=" + this.otherTargetGroupsFlushedByWindow
                + ",enqueuedForOtherTargets=" + this.groupsEnqueuedForOtherTargets
                + ",acceptedForOtherTargets=" + this.acceptedForOtherTargets + "]"
                + " immediateRecordedBatches=" + this.immediateRecordedBatches
                + " immediateSubmittedBatches=" + this.immediateSubmittedBatches
                + " immediateCompletedBatches=" + this.immediateCompletedBatches
                + " immediateRetries=" + this.immediateRetryCount
                + " invalidImmediateRecords=" + this.invalidImmediateRecords
                + " backendCommandBuffer=" + Long.toHexString(this.commandBuffer.address())
                + " backendCommandState=" + this.commandState.state()
                + " drawsWithoutCommandBuffer=" + this.drawsWithoutCommandBuffer
                + " offTargetRefusals=" + (this.offTargetRefusalsByReason.isEmpty()
                        ? "none" : this.offTargetRefusalsByReason.toString())
                + " offTargetOffered=" + this.offTargetOffered + " offTargetAccepted=" + this.offTargetAccepted
                + " offTargetDrawn=" + this.offTargetDrawn + " offTargetCopiedBack=" + this.offTargetCopiedBack
                + " outsideWorldPassAccepted=" + this.outsideWorldPassAccepted + " outsideWorldPassRefusals=" + this.outsideWorldPassRefusals + " compositePresentRefusals=" + this.compositePresentRefusals
                + " descriptorSets=" + this.descriptorSetCensus()
                + " inPlaceRefreshes=" + (this.textureRegistry != null ? this.textureRegistry.inPlaceRefreshes() : 0)
                + " mirroredTextures=" + (this.textureRegistry != null ? this.textureRegistry.mirrorCount() : 0)
                + " externalImages=[" + (this.textureRegistry != null
                        ? this.textureRegistry.externalSummary() : "none") + "]"
                // Condition (c) evidence: what the swapchain last presented, and whether the present was accepted. With
                // presentMarker the image is cleared to magenta (1,0,1), whose 8-bit channel mean is ~170, so a
                // presented mean near 170 says the swapchain image held this renderer's frame rather than nothing.
                + " presentSucceeded=" + (this.swapchain != null && this.swapchain.lastPresentSucceeded())
                + " presentSubmitDone=" + (this.swapchain != null && this.swapchain.lastPresentSubmitDone())
                + " depthFormat=" + org.soutalab.celtium.vulkan.VulkanDepthFormat.chosenName()
                + " depthFormatDecision=[" + org.soutalab.celtium.vulkan.VulkanDepthFormat.decisionSummary() + "]"
                // Which leg of the clear-attribution A/B this run is. On by default; the control leg sets
                // -Dceltium.vulkan.gameClearLatch=false, and a reader of one report must be able to tell which is
                // which without a log. Surfaced here and not in diagnostics(): that list is not part of the report.
                + " gameClearLatch=" + (celtium$gameClearLatchEnabled() ? "on" : "off")
                + " presentedMean=" + (this.swapchain != null ? this.swapchain.lastPresentedMean() : -1.0f)
                + " presentMarkerExpected=170.0_magenta_1_0_1"
                + " fogModeSource=" + (VulkanFogMirror.hasValue() ? "mirror" : "gl_query")
                + " boundTextureSamples=" + this.boundTextureComparisons
                + " boundTextureMismatches=" + this.boundTextureMismatches
                + " announcedDynamicTextures=" + this.announcedDynamicTextures
                + " colorMirrorUses=" + this.colorMirrorUses
                // The refusal counters belong in this summary, not in diagnostics(): diagnostics() is not surfaced in
                // the report (it is a separate list, and the report keeps ten items), while this string is the one the
                // fixture reads. shaderProgramRefusals is what the custom-program shutter took out of Vulkan.
                + " modelDraws=" + this.modelDraws
                + " texMatrixGroups=" + this.texMatrixGroups
                + " modelDrawRefusals=" + this.modelDrawRefusals
                + " modelLastRefusal=" + this.modelLastRefusal
                + " modelMeshLists=" + (this.modelMeshes == null ? -1 : this.modelMeshes.size())
                + " " + VulkanDynamicMesh.describeModel()
                + " " + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lifecycle()
                + " modelMeshQuads=" + this.modelMeshQuads
                + " modelMeshFailure=" + this.modelMeshFailure
                + " modelStoredFloats=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedFloats()
                + " modelFirstVertex=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredVertex()
                + " modelMatrixPair=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.matrixComparison()
                + " modelLists=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lists()
                + " modelListQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listSummary()
                + " modelQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.quads()
                + " modelQuadVertices=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.vertices()
                + " modelQuadExtent=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.summary()
                + " displayListOrigins=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.origins()
                + " displayListReplaysPassOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassOpen()
                + " displayListReplaysPassClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassClosed()
                + " displayListOriginCancels=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originCancels()
                + " displayListCancelled=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.cancelled()
                + " displayListReplaysOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameOpen()
                + " displayListReplaysClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameClosed()
                + " displayListReplays=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.total()
                + " displayListDistinct=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.distinctLists()
                + " displayListTop=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.summary()
                + " shaderProgramRefusals=" + this.shaderProgramRefusals
                + " acceptedBatches=" + this.frameAccepted
                + " imageFilledMirrors=" + (this.textureRegistry != null ? this.textureRegistry.imageUploads() : 0)
                + " imageUploadsSkipped="
                + (this.textureRegistry != null ? this.textureRegistry.imageUploadsSkipped() : 0)
                + " announcedUploads=" + this.announcedUploads + " animatedImageFills=" + this.animatedImageFills
                + " textureCopiesServed=" + this.textureCopiesServed
                + " textureCopiesRefused=" + this.textureCopiesRefused
                + " textureCopiesReusedInFrame=" + this.textureCopiesReusedInFrame
                + " textureCopyReadbacks=" + this.textureCopyReadbacks
                + " textureCopyReadbackMillis=" + (this.textureCopyReadbackNanos / 1000000L)
                + " lastTextureCopyRefusal=[" + this.lastTextureCopyRefusal + "]"
                + " " + (this.postChain != null ? this.postChain.status() : "postChains=0 postPasses=0")
                + " directMirrorFromAnnouncement=" + this.directMirrorFromAnnouncement
                + " dynamicTextureFills=" + this.dynamicTextureFills
                + " readbackByOrigin=" + (this.textureRegistry != null ? this.textureRegistry.readbackByOrigin() : "none")
                + " mirrorsCreatedByAnnouncement="
                + (this.textureRegistry != null ? this.textureRegistry.createdByAnnouncement() : 0)
                + " swapchainOutOfDate=" + (this.swapchain != null ? (this.swapchain.outOfDate() ? 1 : 0) : -1)
                + " acquireOutOfDate=" + (this.swapchain != null ? this.swapchain.acquireOutOfDate() : -1)
                + " retiredFlushes=" + this.retiredFlushes + " retire=" + VulkanRetireQueue.describe()
                + " lastFrameAccepted=" + this.lastFrameAccepted + " lastFrameDrawn=" + this.lastFrameDrawn
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
                + " publishedFrameTexturesCreated=" + this.publishedFrameTexturesCreated
                + " publishedFrameTexturesUnpublished=" + this.publishedFrameTexturesUnpublished
                // The attach-side rule's counter: a published name that stands for the image the pass attaches is kept
                // out of the sampling-side transition, which is the VUID-00900 fix. It must be visible, because a rule
                // that silently stops transitioning an image is indistinguishable from one that forgot to.
                + " publishedFrameTexturesSkippedForAttach=" + this.publishedFrameTexturesSkippedForAttach
                // A name whose image was recreated under it is unpublished at the point of use: a barrier on the freed
                // handle was measured as a validation error followed by a SIGSEGV inside libvulkan_radeon.
                + " publishedFrameTexturesStalePruned=" + this.publishedFrameTexturesStalePruned
                + " publishedFrameTexturesRepublished=" + this.publishedFrameTexturesRepublished
                // A group that outlived its texture wrapper: the descriptor is rebuilt from the current wrapper, or the
                // group is refused. Both are counted because "the picture lost a batch" and "the device was lost" are
                // different outcomes and only the counter says which happened.
                + " staleGroupDescriptorsRebuilt=" + this.staleGroupDescriptorsRebuilt
                + " staleGroupRefusals=" + this.staleGroupRefusals
                + " stalledTeardownSkips=" + this.stalledTeardownSkips
                + " idleWaits=" + this.idleWaits + " injectedIdleFailures=" + this.injectedIdleFailures
                // A timed-out fence wait is retried before the latch: a resize or a swapchain rebuild produces transient
                // timeouts, and latching on the first one stopped the renderer for the session (measured on the
                // world-resize leg). Both the retries and the timeouts are reported so the two are told apart.
                + " idleWaitTimeouts=" + this.idleWaitTimeouts + " idleWaitRetries=" + this.idleWaitRetries
                + " terrainPasses=" + this.terrainPasses + " immediateOnlyPasses=" + this.immediateOnlyPasses
                + " worldPassBoundaryFlushes=" + this.worldPassBoundaryFlushes
                // task-51's prerequisite: the upload path's queue drain and volume, on the line the fixture actually
                // reports. VulkanContext.bootstrapQueueWaits is incremented by beginCommands()'s vkQueueWaitIdle and
                // was printed only by CELTIUM_WAIT_CENSUS from VulkanContext.delete(), which appears in no run at all -
                // a counter that cannot be read is not a measurement. The arena totals beside it are what make the
                // cost per upload readable instead of the wait count alone.
                + " bootstrapQueueWaits=" + VulkanContext.bootstrapQueueWaits
                + " arenaUploads=" + this.celtium$arenaUploadCount() + " arenaUploadedBytes="
                + this.celtium$arenaUploadedBytes()
                + " textureDiagnostics=" + TEXTURE_DIAGNOSTICS + " diagnosed=" + this.diagnosedTextures.size()
                + " blendObserved=" + this.blendObserved + "(" + this.observedBlendSrc + "," + this.observedBlendDst + ")"
                + " blendPairs=" + this.blendPairs + " blendFuncIgnored=" + this.blendFuncIgnored
                + " blendOutlier=\"" + (this.blendOutlier == null ? "none" : this.blendOutlier) + "\""
                // The 2-argument glBlendFunc calls, which used to reach the counters but not the mirror. A run whose
                // value here is 0 and whose picture has an additive layer is a run where the call never arrived.
                + " blendFuncTwoArg=" + this.blendFuncTwoArgCalls
                + " lastTwoArgBlendPair=\"" + this.lastTwoArgBlendPair + "\""
                + " particleDescriptorFailure=\"" + this.particleDescriptorFailureReason + "\""
                + " acceptedByFormat=[" + celtium$perFormat(this.acceptedByFormat) + "]"
                + " drawnByFormat=[" + celtium$perFormat(this.drawnByFormat) + "]"
                + " droppedByFormat=[" + celtium$perFormat(this.postAcceptDroppedByFormat) + "]"
                + " passBoundaryFlushes=" + this.passBoundaryFlushes + " matrixDiagnostics=" + MATRIX_DIAGNOSTICS
                // Per-format arrival and acceptance over every id the layouts class declares, built from COUNT rather
                // than from a hand-written list: the list stopped at 9 and hid POSITION_NORMAL's own entry, which is
                // the same shape as the counters that hid ids 6..10 before. droppedByFormat stays for whichever sites
                // increment it; it is empty today, and printing every id is what says so rather than a list that
                // stops at 5.
                // The group lifecycle at the end of the run: what the queue did with what it accepted.
                // immediateFlushes is incremented beside immediateGroupsDrawn, so both count groups drawn - closed
                // minus drawn is the number that reached no pipeline, and the pending/recorded/retired sizes are what
                // is still held. immediateGroupsKeptForOtherTargets, immediateDeferredNextFrame and
                // immediateRefusedWindowBudget are printed because they name the three ways a group can be held back;
                // reading them here is what makes a zero that cannot move (no increment site) visible as such.
                + " immediateGroupsDrawn=" + this.immediateGroupsDrawn
                // The light-map census, printed here because the two counters it is read with had no reporting site at
                // all: lightmapCoordGroups counts the groups whose captured coordinate reached the push block and
                // lightmapFullBrightGroups the GUI-stage groups that were given the full-bright pair instead. A
                // hotbar leg that reports the capture without the second number cannot say which coordinate was drawn.
                + " lightmapCoordCalls=" + this.lightmapCoordCalls
                + " lightmapCoordGroups=" + this.lightmapCoordGroups
                + " lightmapFullBrightGroups=" + this.lightmapFullBrightGroups
                // The hook's own census: a zero here means the enableLightmap/disableLightmap injection never ran, and
                // the full-bright rule then degrades to the captured coordinate rather than to full-bright entities.
                + " lightmapUnitEnables=" + this.lightmapUnitEnables
                + " lightmapUnitDisables=" + this.lightmapUnitDisables
                // The ITEM variant split, so "the GUI item is bright" can be read as "it was lit and the coordinate was
                // full-bright" or as "it was never lit at all" rather than assumed from the picture.
                + " itemLitGroups=" + this.itemLitGroups
                + " itemLitGuiGroups=" + this.itemLitGuiGroups
                + " itemUnlitGroups=" + this.itemUnlitGroups
                + " itemUnlitGuiGroups=" + this.itemUnlitGuiGroups
                + " immediateGroupsDiscarded=" + this.immediateGroupsDiscarded
                + " immediateGroupsKeptForOtherTargets=" + this.immediateGroupsKeptForOtherTargets
                + " immediateDeferredNextFrame=" + this.immediateDeferredNextFrame
                + " immediateRefusedWindowBudget=" + this.immediateRefusedWindowBudget
                + " groupsStaleTargetGeneration=" + this.groupsStaleTargetGeneration
                + " groupsStaleWorldEpoch=" + this.groupsStaleWorldEpoch
                + " worldEpoch=" + this.worldEpoch
                + " groupsDiscardedAtWorldTeardown=" + this.groupsDiscardedAtWorldTeardown
                + " groupsDiscardedAtFullTeardown=" + this.groupsDiscardedAtFullTeardown
                + " immediateWindows=" + this.immediateWindows
                + " immediateWindowsThisFrame=" + this.immediateWindowsThisFrame
                + " pendingImmediateIndices=" + this.pendingImmediateIndices
                + " immediateGroupsPending=" + this.immediateGroups.size()
                + " recordedGroupsHeld=" + this.recordedImmediateGroups.size()
                + " retiredGroupsHeld=" + this.retiredImmediateGroups.size()
                + " lastTerrainPassFrame=" + this.lastTerrainPassFrame + " frameId=" + this.frameId
                + " matrixMirrorDelta=proj:" + this.mirroredProjectionDelta + ",view:" + this.mirroredModelViewDelta
                // The application side of the stub's raw glMultMatrixf arrival census. Without this print the field was
                // an instrument with no output (proj-channel found it incremented at :3102 and printed nowhere), and
                // "arrivals without a mirrored multiply" is the pair that says a raw call reached GL with no owner.
                + ",mirroredMultiplies:" + this.mirroredMultiplies
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
                // Audit B03/B04: what the descriptor pools did (pools, growths, sets, cached texture keys,
                // refusals) and the wait-outcome matrix (completions, timeouts, lost devices, unconfirmed waits),
                // in the run's own report rather than only on stdout at teardown - the teardown path is not reached
                // in every run, and "the counter is in a log line nobody read" is how a bound stays invisible.
                + " immediateDescriptors=[" + VulkanImmediateTexturePipeline.descriptorPoolSummary() + "]"
                // Task-46: the authoritative "what renders now" fields - the real driver, from the device that
                // renders. The old GL bootstrap probe also has a glRenderer/vulkanDevice pair and those describe a
                // different, earlier question (the GL identity and the interop contract); this is the live one.
                + " driverInfo=[" + VulkanContext.driverSummary() + "]"
                + " waitOutcomes=[" + VulkanContext.waitOutcomeSummary() + "]"
                + " presentMode=[" + VulkanSwapchain.presentModeSummary()
                + " rebuilds=" + this.presentModeRebuilds + "]"
                + " quarantined=[buffers=" + VulkanContext.quarantinedResourceCounts()[0]
                + " fences=" + VulkanContext.quarantinedResourceCounts()[1] + "]"
                + " matrixChannels=persp:" + this.perspectiveCalls + ",frustum:" + this.frustumCalls
                + ",gluPerspective:" + this.mirroredGluPerspectives + ",gluSkipped:"
                + this.skippedGluPerspectives + ",gluSites:" + this.gluPerspectiveShapes.size()
                + ",gluSkipSites:" + this.gluPerspectiveSkips.size()
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
            // A resource failure while a region is being uploaded must not take the game down. An injected allocation
            // failure in the vertex arena, the index arena or the staging buffer used to escape through here into
            // Minecraft's chunk loop and end the run with "Game crashed!" and no report at all (exit 255, both GPUs).
            // The region keeps whatever it already had, the counter records the loss, and the next region is tried.
            try {
                uploadRegion(entry.getKey(), entry.getValue(), graphUpdateTrigger);
            } catch (RuntimeException error) {
                this.regionUploadFailures++;
                this.lastRegionUploadFailure = String.valueOf(error);
                // BH-25: name every section of the failed region, so the caller leaves them un-committed instead of
                // marking them built. Swallowing the exception alone left the section's metadata, build token and
                // lastBuiltFrame advanced with no geometry on the GPU, and nothing rebuilds a section that reads as
                // built - the missing chunks were permanent.
                for (ChunkTaskOutput output : entry.getValue()) {
                    if (output.render != null && this.failedUploads.add(output.render)) {
                        this.sectionsLeftUnbuilt++;
                    }
                }
                record("region upload failed and was skipped: " + error + " (sections left un-built: "
                        + entry.getValue().size() + ", regionUploadFailures=" + this.regionUploadFailures + ")");
            }
        }
    }

    /**
     * {@return the sections the last {@code uploadMeshes} could not upload}, consuming the set (BH-25).
     * <p>
     * The caller reads this immediately after {@code uploadMeshes} and skips the commit for each named section, so the
     * failure leaves the section queued for another build instead of marking it built with no geometry.
     */
    @Override
    public java.util.Set<org.embeddedt.embeddium.impl.render.chunk.RenderSection> consumeFailedUploads() {
        if (this.failedUploads.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        java.util.Set<org.embeddedt.embeddium.impl.render.chunk.RenderSection> drained =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        drained.addAll(this.failedUploads);
        this.failedUploads.clear();
        return drained;
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
                if (!settleGpu("region eviction")) {
                    // Stalled: the region stays for process exit rather than being released while the GPU may still
                    // reference it. The loop's remaining entries are left alone for the same reason.
                    break;
                }
                entry.getValue().delete();
                iterator.remove();
            }
        }
    }

    @Override
    public void updateTimings() {
        // The vertex readback is read once per run, after a frame that recorded its copies: this is the frame boundary.
        // Wait until at least two draws were remembered: the first frame of a session draws almost nothing, and reading
        // then would report a single vertex and say nothing about the sections that are actually visible.
        if (VERTEX_DIAGNOSTICS && !this.readbackDone && this.readbackSlots >= READBACK_SLOTS / 2) readBackAndLog();
        // The immediate window count is per frame. The measurement that forced the limit: the first window of a frame
        // left the framebuffer at 150.46 before and after, and the second turned it to 0.0 with the GL state restored
        // unchanged, which is what blackened the captured frame.
        // A flush is not a frame: it increments the flush count and leaves the frame's budget and counters alone (S11).
        this.flushCount++;
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

    /**
     * Releases the resources that belong to the world renderer and leaves the device alive.
     * <p>
     * Every resource here is created from the world's render pass configuration or from its regions - the region
     * buffers, the per-pass pipelines, the model meshes, the shared index buffers and the terrain uniform buffer, and the
     * immediate pipelines whose caches are keyed by draw state rather than by the pass they were built for. The
     * ones the window keeps are the device and its command resources, the standalone UI render pass, the frame target
     * and the swapchain, the texture registry and its mirrors, and the readback image: those are what a menu frame
     * draws into and presents from. The uniforms and the pipelines are rebuilt on demand by the terrain path, so the
     * next world recreates them from the configuration the adoption hands over.
     * <p>
     * The world's lifetime also ends for the queue: a group accepted under this world is retired here rather than left
     * for the next draw, and the world epoch it carries is bumped so that nothing accepted before the next adoption can
     * be drawn against this world's freed buffers. That is the audit's B10 gap - the target generation says nothing
     * about a world, because the frame target survives one.
     */
    private void celtium$releaseWorldResources() {
        // The world's own lifetime ends here, and a queued group belongs to it: a captured model group's mesh is lent by
        // one of the model meshes released just below, so drawing such a group after this point reads freed geometry.
        // Undrawn groups are retired now rather than left for a draw that may never come, and the epoch bump keeps a
        // group accepted between here and the next world's adoption from being drawn under the old stamp.
        int orphans = this.immediateGroups.size();
        if (orphans > 0) {
            this.retiredImmediateGroups.addAll(this.immediateGroups);
            this.immediateGroups.clear();
            this.pendingImmediateIndices = 0;
            this.groupsDiscardedAtWorldTeardown += orphans;
            record("world teardown retired " + orphans + " queued immediate group(s) that the world owned");
        }
        this.worldEpoch++;
        if (this.modelMeshes != null) {
            for (VulkanDynamicMesh mesh : this.modelMeshes.values()) {
                mesh.releaseAfterIdle();
            }
            this.modelMeshes.clear();
        }
        int drained = VulkanRetireQueue.flush();
        if (drained > 0) {
            record("world teardown destroyed " + drained + " retired resources; the window device stays");
        }
        releaseRetiredImmediateGroups();
        for (RegionResources resources : this.regionResources.values()) {
            resources.delete();
        }
        this.regionResources.clear();
        for (VulkanTerrainPipeline pipeline : this.pipelines.values()) {
            pipeline.delete();
        }
        this.pipelines.clear();
        // The immediate caches are built against the terrain pass that was just destroyed, so they belong to this world
        // as much as the pipelines do: a state-keyed entry kept across a world change would name a dead render pass. The
        // queued groups that held references to them were retired above, so nothing here is still pointing at them.
        int immediate = celtium$releaseImmediatePipelines();
        if (immediate > 0) {
            record("world teardown released " + immediate + " immediate pipeline(s) built for the world's render pass");
        }
        for (VulkanBuffer buffer : this.sharedIndexBuffers.values()) {
            buffer.delete();
        }
        this.sharedIndexBuffers.clear();
        this.sharedIndexPrimitives.clear();
        if (this.uniforms != null) {
            this.uniforms.delete();
            this.uniforms = null;
        }
        // OPT-05: the frame arena's buffers are released here, not left for the next world. Every group that held a
        // slice has been retired above (this path runs after a wait for device idle that succeeded), so nothing the
        // queue may still read is inside them; keeping them would carry one world's immediate memory into the next.
        if (this.frameArena != null) {
            this.frameArena.delete();
            this.frameArena = null;
            this.frameArenaFallbackGroups = 0L;
        }
        record("world teardown: terrain resources released, the window device and swapchain are kept");
    }

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
        if (!settleGpu("backend delete")) {
            // Stalled: releasing the backend here would destroy objects the recorded command buffer still references.
            return;
        }
        if (this.windowOwned) {
            // The world renderer is leaving, not the window. Release what the world owns, detach the world's objects and
            // keep the device, the frame target and the swapchain: the menu and the loading screens draw through them,
            // and the next world adopts the same instance again (VulkanTerrainFactory.create checks
            // celtium$isWindowBackend()). Without this branch a quit to the title screen deletes the device and the menu
            // has nothing to draw with, which is the state task-15 exists to remove.
            celtium$releaseWorldResources();
            this.configuration = null;
            this.regionManager = null;
            this.fogService = null;
            this.windowBackend = true;
            return;
        }
        // The model meshes belong to this backend and hold buffers this device owns. settleGpu succeeded just above, so
        // the last submission has completed and nothing more will be recorded: the release is immediate and counted as
        // teardown, not as a free without a completed wait (audit N02).
        if (this.modelMeshes != null) {
            for (VulkanDynamicMesh mesh : this.modelMeshes.values()) {
                mesh.releaseAfterIdle();
            }
            this.modelMeshes.clear();
        }
        // Teardown: the device has been settled above, so anything still retired can be destroyed now.
        int drained = VulkanRetireQueue.flush();
        if (drained > 0) {
            record("teardown destroyed " + drained + " retired resources");
        }
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
        if (this.uiRenderPass != 0) {
            org.lwjgl.vulkan.VK10.vkDestroyRenderPass(this.context.device(), this.uiRenderPass, null);
            this.uiRenderPass = 0;
        }
        for (VulkanBuffer buffer : this.sharedIndexBuffers.values()) {
            buffer.delete();
        }
        this.sharedIndexBuffers.clear();
        this.sharedIndexPrimitives.clear();
        if (this.uniforms != null) {
            this.uniforms.delete();
            this.uniforms = null;
        }
        if (this.postChain != null) {
            this.postChain.delete();
            this.postChain = null;
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
            // Groups recorded against the old target may still be queued; the generation is what lets the draw refuse them
            // instead of binding a render pass that no longer exists. Both the recreate path and the teardown path bump it.
            this.targetGeneration++;
        }
        destroyCommandResources();
        // Everything the backend owns is released here, not just the terrain half (audit S15): the immediate mesh, the
        // held groups, the immediate pipeline caches, the UI pipeline and mesh, the texture registry and the swapchain.
        int released = 0;
            this.otherTargetGroupsDiscarded += pendingGroupsForOtherTargets();
        try {
            discardImmediateGroups();
            released++;
        } catch (RuntimeException | LinkageError error) {
            record("teardown: held groups could not be released: " + describe(error));
        }
        int pendingCopies = 0;

        released += releaseImmediateMesh(this.currentImmediateMesh);
        this.currentImmediateMesh = null;
        // The immediate pipeline caches, the isolation pass's pipeline and mesh, the descriptor probe and the UI test
        // quad's pipeline and mesh are one set: everything the immediate path built against a render pass. They are
        // released by the same call the world-leave path makes, so the two lifetimes cannot drift apart.
        released += celtium$releaseImmediatePipelines();
        if (this.textureRegistry != null) {
            this.textureRegistry.delete();
            this.textureRegistry = null;
            released++;
        }
        if (this.swapchain != null) {
            this.swapchain.delete();
            this.swapchain = null;
            released++;
        }
        System.out.println("[Celtium] CELTIUM_TEARDOWN released " + released
                + " backend resources (immediate mesh, groups, pipeline caches, UI, registry, swapchain)");
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
                    RegionResources owner = resourcesFor(region);
                    byte[] head = new byte[32];
                    java.nio.ByteOrder headOrder = vertices.order();
                    try {
                        vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        for (int b = 0; b < 32 && b < vertices.capacity(); b++) head[b] = vertices.get(b);
                    } finally {
                        vertices.order(headOrder);
                    }
                    owner.sectionFirstVertex[mesh.sectionIndex] = head;
                }
                if (TERRAIN_VERTEX_COLOUR_SAMPLES > 0 && colourSamples < TERRAIN_VERTEX_COLOUR_SAMPLES) {
                    colourSamples++;
                    // What the mesh builder packed, in the layout the pipeline declares (vanilla BLOCK: colour at byte 12).
                    // The frame is darker than the reference by a per-pixel factor, so the question is whether the colour
                    // arrives dark (alpha below 255 would do it: the fragment shader multiplies rgb by alpha) or arrives
                    // correct and is lost later. Mean and extremes answer that without a second build.
                    int stride = 32;
                    int count = Math.max(1, vertexBytes / stride);
                    long sumR = 0, sumG = 0, sumB = 0, sumA = 0;
                    int minA = 255, maxA = 0;
                    int step = Math.max(1, count / 64);
                    int sampled = 0;
                    java.nio.ByteOrder order = vertices.order();
                    try {
                        vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        for (int v = 0; v < count; v += step) {
                            int at = v * stride;
                            int r = vertices.get(at + 12) & 0xFF;
                            int g = vertices.get(at + 13) & 0xFF;
                            int b = vertices.get(at + 14) & 0xFF;
                            int a = vertices.get(at + 15) & 0xFF;
                            sumR += r; sumG += g; sumB += b; sumA += a; sampled++;
                            if (a < minA) minA = a;
                            if (a > maxA) maxA = a;
                        }
                    } finally {
                        vertices.order(order);
                    }
                    // The light element is two unsigned shorts at byte 24 in the vanilla BLOCK layout. Their units decide
                    // the light map divisor: byte-sized light values (0..15) belong with /16, values already shifted for a
                    // 256-texel map (0..240) belong with /256, and the two cannot both be right.
                    int light0 = 0, light1 = 0;
                    java.nio.ByteOrder lightOrder = vertices.order();
                    try {
                        vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        light0 = vertices.getShort(24) & 0xFFFF;
                        light1 = vertices.getShort(26) & 0xFFFF;
                    } finally {
                        vertices.order(lightOrder);
                    }
                    float u0 = 0.0f, v0 = 0.0f;
                    java.nio.ByteOrder uvOrder = vertices.order();
                    try {
                        vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        u0 = vertices.getFloat(16);
                        v0 = vertices.getFloat(20);
                    } finally {
                        vertices.order(uvOrder);
                    }
                    java.util.Map<Integer, Integer> histogram = new java.util.TreeMap<>();
                    java.nio.ByteOrder histOrder = vertices.order();
                    try {
                        vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        for (int v = 0; v < count; v++) {
                            int at = v * stride;
                            int packedColour = (vertices.get(at + 12) & 0xFF) | ((vertices.get(at + 13) & 0xFF) << 8)
                                    | ((vertices.get(at + 14) & 0xFF) << 16) | ((vertices.get(at + 15) & 0xFF) << 24);
                            histogram.merge(Integer.valueOf(packedColour), Integer.valueOf(1), Integer::sum);
                        }
                    } finally {
                        vertices.order(histOrder);
                    }
                    StringBuilder top = new StringBuilder();
                    int shownColours = 0;
                    for (var entry : histogram.entrySet()) {
                        if (shownColours++ >= 4) break;
                        top.append("0x").append(Integer.toHexString(entry.getKey().intValue()))
                                .append('x').append(entry.getValue()).append(' ');
                    }
                    System.out.println("[Celtium] CELTIUM_VERTEX_COLOUR pass=" + mesh.pass + " section=" + mesh.sectionIndex
                            + " first=0x" + Integer.toHexString(firstVertexColor(vertices))
                            + " meanRGBA=" + (sumR / sampled) + "," + (sumG / sampled) + "," + (sumB / sampled)
                            + "," + (sumA / sampled) + " alphaRange=" + minA + ".." + maxA
                            + " lightShort0=" + light0 + " lightShort1=" + light1
                            + " uv=" + u0 + "," + v0 + " vertices=" + count
                            + " distinctColours=" + histogram.size() + " top=" + top);
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
    /** Passes abandoned because the uniform buffer could not be prepared. */
    private int uniformPreparationFailures;
    private String lastUniformPreparationFailure = "none";
    /** Regions whose upload was abandoned because a resource could not be built. */
    private int regionUploadFailures;
    private String lastRegionUploadFailure = "none";
    /**
     * Sections whose geometry the last {@code uploadMeshes} could not put on the GPU (BH-25).
     * <p>
     * Filled when a region's upload throws and drained by {@link #consumeFailedUploads()} on the caller's next line, so
     * the section manager can leave exactly those sections un-committed and queued for another build. A set rather than
     * a count because the caller needs the identity, and identity is the whole point: the section must not be marked
     * built.
     */
    private final java.util.Set<org.embeddedt.embeddium.impl.render.chunk.RenderSection> failedUploads =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /**
     * Sections a failed region upload left un-built, counted for the acceptance (BH-25). Before the fix the same
     * failure left them marked built, so this number was invisible: it was zero because nothing distinguished the two.
     */
    private int sectionsLeftUnbuilt;
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
        if ((this.colourBatches == 1 || this.colourBatches % 20 == 0) && VulkanDiagnostics.enabled()) {
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
            record("mesh" + verticesLogged + " pass=" + nameOf(mesh.pass) + " " + line);
            return;
        }
    }

    /** Colour word of the first vertex of a mesh, in the little-endian byte order the GPU reads. */
    /** How many section uploads print their packed vertex colours; a property so a normal run stays quiet. */
    /** The vertex-level diagnostics (layout, drawn sections, GPU readback): one switch, quiet by default. */
    private static final boolean VERTEX_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.vertexDiagnostics");

    private static final int TERRAIN_VERTEX_COLOUR_SAMPLES =
            Integer.getInteger("celtium.vulkan.vertexColourSamples", 0).intValue();
    private int colourSamples;

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
        this.terrainPasses++;
        // Before anything this pass records: the model meshes are built once, from geometry captured while Minecraft
        // compiled its display lists. Guarded by a lab switch, so the default path is untouched until the draw lands.
        celtium$buildModelMeshes();
        if (this.gpuStalled) {
            // The pass-level recording is closed too: with the buffer never begun, vkCmdBeginRenderPass, vkCmdSetViewport and
            // vkCmdSetScissor were still being recorded into it, which is what the steady-state injection reported.
            return;
        }
        try {
            ensureUniformCapacity(this.regionResources.size());
        } catch (RuntimeException error) {
            this.uniformPreparationFailures++;
            this.lastUniformPreparationFailure = String.valueOf(error);
            record("pass skipped: uniform preparation failed: " + error);
            return;
        }
        // The descriptor set points at the atlas, the lightmap and the uniform buffer for this pass. It is rewritten
        // once per pass per frame, which is a few descriptor writes and removes any staleness after a re-upload.
        if (LIGHT_BIND_DIAGNOSTICS && this.lightBindPrints < 3) {
            this.lightBindPrints++;
            // Which texture the terrain shader actually samples as the light map, and how big it is. The 16x16 dump has
            // been analysed twice on the assumption that it is this one; this prints the identity instead.
            System.out.println("[Celtium] CELTIUM_LIGHT_BIND glId=" + this.lightmapGlId
                    + " view=" + Long.toHexString(this.lightmapTexture.view())
                    + " size=" + this.lightmapTexture.width() + "x" + this.lightmapTexture.height()
                    + " atlas=" + this.atlasTexture.width() + "x" + this.atlasTexture.height());
        }
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
        if (this.publishedChainOutput != null
                && celtium$queuedForTargetUsesPublished(this.activeImmediateTarget)) {
            // The immediate pipelines declare SHADER_READ_ONLY_OPTIMAL in their descriptors while the published
            // outline image can be in GENERAL here: an off-target pass hands it back before attaching it, and a frame
            // in which the chain did not run never moves it forward. The transition belongs to the submission that
            // draws the sampling batch - recorded outside the pass, before the draw, which is what this site is.
            this.publishedChainOutput.ensureSampledLayout(this.commandBuffer);
        }
        // Every other name published over the pair's image gets the same transition at the same site. A name published
        // for a game framebuffer whose draws land in the pair (the vanilla loading screen's, measured) is sampled from a
        // pass whose descriptor declares SHADER_READ_ONLY while the pair's own layout is GENERAL: leaving it there is
        // exactly the VUID-vkCmdDraw-None-09600 the vsync leg reported on three distinct images at vkQueueSubmit. The
        // transition is recorded outside the pass, before the draw, like the outline's.
        celtium$transitionPublishedFrameTextures(false);
        vkCmdBeginRenderPass(this.commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);
        this.activeImmediateTarget = this.currentRenderTarget;
        this.commandState.beginPass(this.activeImmediateTarget);
        org.soutalab.celtium.vulkan.VulkanDisplayListCensus.setRenderPassOpen(true);

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

            if (this.readbackSlots < 8) {
                // One slot per pass, so the readback covers the batch each pass actually draws rather than only the
                // first one: the visible terrain mixes passes, and a colour that is wrong in one of them has to be
                // attributable to that pass.
                this.readbackOffset = (long) batch.baseVertex(0) * this.commonVertexStride;
                this.readbackRegion = resources;
            }
            long uniformOffset = writeUniforms(region, pass, camera);
            pipeline.bindDescriptors(this.commandBuffer, uniformOffset);
            if (this.boundHandleLogs < 6 && this.atlasTexture != null) {
                // Which pipeline and descriptor set the draw will actually use, next to the views the backend handed
                // bindTextures. If the set printed here is not the one that was written, the terrain samples another
                // image - and that is invisible from the image's content, however correct the content is.
                this.boundHandleLogs++;
                record("bound draw: pass=" + nameOf(pass) + " pipeline=" + Long.toHexString(pipeline.pipelineHandle())
                        + " set=" + Long.toHexString(pipeline.descriptorSetHandle())
                        + " atlasView=" + Long.toHexString(this.atlasTexture.view())
                        + " atlasKey=" + Long.toHexString(this.atlasTexture.key())
                        + " lightmapView=" + (this.lightmapTexture == null ? "none"
                                : Long.toHexString(this.lightmapTexture.view())));
            }
            if (this.groupDepthRowsSeen < 2) {
                // The depth row of the projection each group draws with, plus the model-view translation, so a model group and
                // an immediate group can be told apart and compared: the order instrument said the terrain ran first, so the
                // depth values are what is left.
                // Which kind of group a row belongs to is not printed here: the loop's variable is not named group at this
                // level, and the first two rows came out identical anyway - both immediate groups, judging by the model-view
                // translations not matching the model path's. Labelling it is the next step, not a guess.
                String row = round3(matrices.projection().m20()) + "," + round3(matrices.projection().m21()) + ","
                        + round3(matrices.projection().m22()) + "," + round3(matrices.projection().m23())
                        + " mvT=" + round3(matrices.modelView().m30()) + "," + round3(matrices.modelView().m31()) + ","
                        + round3(matrices.modelView().m32());
                if (!row.equals(this.lastGroupDepthRow)) {
                    this.lastGroupDepthRow = row;
                    this.groupDepthRowsSeen++;
                    System.out.println("[Celtium] CELTIUM_GROUP_DEPTH row" + this.groupDepthRowsSeen + "=" + row);
                }
            }
            pipeline.pushMatrices(this.commandBuffer, vulkanProjection(matrices.projection()), matrices.modelView());

            bindVertexBuffer(stack, resources);
            if (!indexBound) {
                bindIndexBuffer(stack, this.sharedIndexBuffers.get(primitiveType));
                indexBound = true;
            }

            if (this.lastDrawCommands == 0 && command0Logged < 6) {
                command0Logged++;
                StringBuilder colors = new StringBuilder("drawn section colours:");
                byte[] sections = renderList.getSectionsWithGeometry();
                for (int i = 0; i < Math.min(4, sections.length); i++) {
                    int local = sections[i] & 0xFF;
                    colors.append(" #").append(local).append("=0x")
                            .append(Integer.toHexString(resources.sectionFirstColor[local]));
                }
                record(colors.toString());
                // stdout: the CPU-side counterpart of the GPU readback, for the sections actually being drawn.
                byte[] firstHead = resources.sectionFirstVertex[sections.length > 0 ? (sections[0] & 0xFF) : 0];
                StringBuilder head = new StringBuilder();
                if (firstHead != null) {
                    for (byte b : firstHead) head.append(String.format("%02x", b));
                }
                if (VERTEX_DIAGNOSTICS) {
                    System.out.println("[Celtium] CELTIUM_DRAWN_SECTIONS pass=" + nameOf(pass) + " " + colors
                            + " firstVertexBytes=" + head);
                }
                command0Logged++;
                record("firstDraw region=" + region.getId() + " pass=" + nameOf(pass)
                        + " stride=" + this.commonVertexStride
                        + " vertexArenaBytes=" + resources.vertexArena.deviceAllocatedMemoryL()
                        + " baseVertex=" + batch.baseVertex(0) + " elements=" + batch.elementCount(0)
                        + " indexBytes=" + batch.indexByteOffset(0)
                        + " batchSize=" + batch.size()
                        + " sharedIndexPrimitives=" + this.sharedIndexPrimitives.get(primitiveType));
            }
            // BH-02: which index buffer a command reads is decided by the pass, not by the offset value. A sorted
            // (transparent) pass writes real index positions into the region's arena, and 0 is a legal position in it -
            // the first command of the first such region lands there. The old test read `byteOffset == 0` as "the shared
            // quad index is bound" and drew that command out of the shared buffer instead, mixing the transparent
            // geometry with the unsorted quad pattern; whether it happened depended on whether a positive-offset command
            // had preceded it, which is why it was invisible most of the time. The shared quad index remains the index
            // source for every unsorted pass, whose offsets are 0 by construction.
            boolean sortedPass = pass.isSorted();
            for (int command = 0; command < batch.size(); command++) {
                long byteOffset = batch.indexByteOffset(command);
                int firstIndex;
                if (sortedPass) {
                    if (resources.indexArena == null) {
                        continue;
                    }
                    bindIndexBuffer(stack, resources.indexArena.buffer());
                    firstIndex = (int) (byteOffset / 4);
                } else if (byteOffset == 0) {
                    firstIndex = 0;
                } else if (resources.indexArena != null) {
                    bindIndexBuffer(stack, resources.indexArena.buffer());
                    firstIndex = (int) (byteOffset / 4);
                } else {
                    continue;
                }
                vkCmdDrawIndexed(this.commandBuffer, batch.elementCount(command), 1, firstIndex,
                        batch.baseVertex(command), 0);
                if (VERTEX_DIAGNOSTICS && !this.readbackDone && this.readbackSlots < READBACK_SLOTS) {
                    // Only the address is remembered here: a buffer copy is illegal inside a render pass, so the copies
                    // are issued after vkCmdEndRenderPass for every remembered draw. The region travels with the offset
                    // because a single field was overwritten by every draw, so every slot was read back from whichever
                    // region happened to be drawn last - which is why the decoded vertices could never be trusted.
                    this.readbackOffsets[this.readbackSlots] = (long) batch.baseVertex(command) * this.commonVertexStride;
                    this.readbackRegions[this.readbackSlots] = resources;
                    this.readbackSlots++;
                }
                this.lastDrawCommands++;
            }

            this.lastSectionsDrawn += renderList.getSectionsWithGeometryCount();

            if (resources.indexArena != null) {
                // The next region may use the shared index buffer again.
                bindIndexBuffer(stack, this.sharedIndexBuffers.get(primitiveType));
            }
        }

        if (this.currentImmediateMesh != null || !this.immediateGroups.isEmpty()) {
            // The terrain pass's tail: everything still pending is drawn inside this pass, after the sections, so it
            // tests against the depth the terrain of this pass wrote.
            this.immediateDrawSite = "terrain-pass-tail";
            drawPendingImmediate(Long.valueOf(this.activeImmediateTarget));
        }

        if (this.uiTestEnabled && pass.isSorted()) {
            drawUiTestQuad(primitiveType);
            // The isolation test runs with the UI quad so the frame carries both a known-good draw and the pipeline
            // under test, which is what makes the pixel result conclusive.
            drawImmediateIsolationQuad();
        }

        vkCmdEndRenderPass(this.commandBuffer);
        this.commandState.endPass();
        org.soutalab.celtium.vulkan.VulkanDisplayListCensus.setRenderPassOpen(false);
        if (timestampSlot >= 0) {
            vkCmdWriteTimestamp(this.commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, this.timestampPool,
                    (timestampSlot * 2) + 1);
            this.timestampPasses.put(timestampSlot, pass);
            this.timestampSlots++;
        }
        // A buffer copy is only legal outside a render pass instance, so every remembered draw is copied here.
        if (VERTEX_DIAGNOSTICS && !this.readbackDone) {
            for (int slot = 0; slot < this.readbackSlots; slot++) {
                RegionResources region = this.readbackRegions[slot];
                if (region == null) {
                    continue;
                }
                if (region.vertexArena == null
                        || this.readbackOffsets[slot] + READBACK_COPY_BYTES > region.vertexArena.deviceAllocatedMemoryL()) {
                    // The slot names a range the arena does not hold: copying it is the validation error this used to
                    // produce (VUID-vkCmdCopyBuffer-srcOffset-00113). The check is the size the copy actually asks for
                    // - a whole diagnostic block - not one vertex stride: a slot that has a stride's worth of bytes
                    // left but not a block's passed the old check and then read past the allocation.
                    this.readbackRefusals++;
                    this.readbackValid[slot] = false;
                    continue;
                }
                this.readbackValid[slot] = true;
                recordReadback(region, this.readbackOffsets[slot], slot);
            }
            this.readbackRegionsUsed += this.readbackSlots;
            this.readbackDone = true;
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
        return ensureFramebuffer(renderPass, this.frameTarget.colorView(), this.frameTarget.depthView());
    }

    /**
     * The framebuffer for a pass that draws into the target's own off-target images rather than the frame's.
     * <p>
     * The game's framebuffers are not the frame image: the entity outline draws its silhouette into
     * {@code entityOutlineFramebuffer} before the chain that turns it into a glow, and drawing that into the frame
     * image instead paints the silhouette flat onto the scene with the scene's depth buffer. With no OpenGL context
     * there is no such framebuffer to bind, so the renderer keeps one image of its own for these draws.
     */
    private long ensureOffTargetFramebuffer(long renderPass) {
        GllessFrameTarget glless = offTargetTarget();
        if (glless == null) {
            return 0L;
        }
        if (this.offTargetFramebuffer != 0 && this.offTargetFramebufferRenderPass == renderPass
                && glless.offTargetMatches(this.targetWidth, this.targetHeight)) {
            return this.offTargetFramebuffer;
        }
        destroyOffTargetFramebuffer();
        this.offTargetFramebuffer = ensurePairFramebuffer(renderPass, glless.offTargetColorView(),
                glless.offTargetDepthView(), "off-target");
        this.offTargetFramebufferRenderPass = renderPass;
        this.offTargetFramebufferCreations++;
        return this.offTargetFramebuffer;
    }

    /**
     * The framebuffer for a pass that draws into the chain pair: the game's own framebuffers' content, the
     * entity-outline silhouette being the measured case. Separate from the default pair's framebuffer because the
     * attachments differ; otherwise the same cache, keyed by the render pass it is compatible with.
     */
    private long ensureChainPairFramebuffer(long renderPass) {
        GllessFrameTarget glless = offTargetTarget();
        if (glless == null) {
            return 0L;
        }
        if (this.chainPairFramebuffer != 0 && this.chainPairFramebufferRenderPass == renderPass
                && glless.chainPairMatches(this.targetWidth, this.targetHeight)) {
            return this.chainPairFramebuffer;
        }
        destroyChainPairFramebuffer();
        this.chainPairFramebuffer = ensurePairFramebuffer(renderPass, glless.chainPairColorView(),
                glless.chainPairDepthView(), "chain-pair");
        this.chainPairFramebufferRenderPass = renderPass;
        this.chainPairFramebufferCreations++;
        return this.chainPairFramebuffer;
    }

    /** Builds one framebuffer over a pair's views at the frame's size. */
    private long ensurePairFramebuffer(long renderPass, long colorView, long depthView, String what) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.mallocLong(2);
            attachments.put(0, colorView);
            attachments.put(1, depthView);
            org.lwjgl.vulkan.VkFramebufferCreateInfo create = org.lwjgl.vulkan.VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .pAttachments(attachments)
                    .width(this.targetWidth)
                    .height(this.targetHeight)
                    .layers(1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateFramebuffer(this.context.device(), create, null, handle),
                    "vkCreateFramebuffer(" + what + ")");
            return handle.get(0);
        }
    }

    /** The owned off-target image pair, or null when this configuration has none (the GL bridge has real ones). */
    private GllessFrameTarget offTargetTarget() {
        return this.frameTarget instanceof GllessFrameTarget ? (GllessFrameTarget) this.frameTarget : null;
    }

    /**
     * Attributes the game's pending {@code glClear} request to the framebuffer that was bound when it was issued.
     * <p>
     * A GL clear belongs to the framebuffer bound at the moment it is called, and the game clears three of them every
     * glow frame: its own main framebuffer (this delivered image) with the fog colour
     * ({@code EntityRenderer.updateFogColor} then {@code clear(16640)} at EntityRenderer.java:1326-1327), the
     * entity-outline framebuffer (the chain pair) with transparent black
     * ({@code RenderGlobal.java:679} {@code entityOutlineFramebuffer.framebufferClear()}, whose
     * {@code Framebuffer.java:276-288} sets {@code glClearColor(this.framebufferColor)} - (0,0,0,0) - before its own
     * clear), and the default framebuffer (the default pair). Only the first is this image, and until this latch
     * existed nothing downstream could tell them apart: the stub keeps one buffer mask and one live colour, and
     * {@code GllessFrameTarget.applyGameClears} read both at the <em>next</em> frame's acquire, when the last
     * {@code glClearColor} of the previous frame is the outline framebuffer's transparent black. Every glow frame
     * therefore cleared the delivered image black, and the sky - which no pass redraws, because the sky draws are
     * gated by {@code celtium.glstub.skyFrameGate} while fog is on - kept that black. That is the black region:
     * measured at 0.18984 of the presented frame in run {@code glow688} (scene terrain + glowEntities), 0.335 in
     * {@code glow}, and 0.006 in the same terrain scene without glow entities, where no outline framebuffer is
     * cleared.
     * <p>
     * The other route needs no clear here at all: a clear addressed to the default framebuffer or to a framebuffer of
     * the game's own is a clear of the pair that stands for it, and both pairs are cleared once per frame by
     * {@code clearOffTarget} / {@code clearChainPair} with exactly those values (transparent black, depth 1). What
     * this method must not do is let that request reach the frame image, which is what it did.
     */
    /**
     * Lab switch for the clear-attribution latch, on by default, and the control the latch has to be measured against.
     * <p>
     * Off is the pre-latch behaviour: nothing consumes the stub's pending clears at a framebuffer change, so
     * {@code GllessFrameTarget.applyGameClears} reads whatever mask and live {@code glClearColor} are pending at the
     * <em>next</em> window acquire - which for the entity-outline framebuffer is its transparent black, applied to the
     * delivered image mid-frame. The latch has to be shown to change the picture, not just to compile, and this is how
     * one jar produces both legs: same build, same scene, same fixture, one property apart. The two runs and what they
     * measured are named in the task-8 report.
     * <p>
     * It is a nullable Boolean rather than a primitive so the report can say "the latch was on by default" instead of
     * reading a false as if it had been asked for.
     */
    private static final String GAME_CLEAR_LATCH_PROPERTY = "celtium.vulkan.gameClearLatch";

    /** {@return whether the clear-attribution latch is enabled for this process}, defaulting to on. */
    private static boolean celtium$gameClearLatchEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(GAME_CLEAR_LATCH_PROPERTY, "true"));
    }

    private void celtium$latchGameClearAtTargetChange() {
        if (!org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            // The bridge leg has real GL: it clears for itself, and the stub's request is not this path's to consume.
            return;
        }
        if (!celtium$gameClearLatchEnabled()) {
            // Control leg: leave the request pending, exactly as it was before this latch existed, so the live read
            // in applyGameClears makes the decision at the next acquire.
            return;
        }
        GllessFrameTarget glless = offTargetTarget();
        if (glless == null) {
            return;
        }
        int mask = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.consumePendingClears();
        if (mask == 0) {
            return;
        }
        float[] rgba = new float[4];
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearColor(rgba);
        double depth = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearDepth();
        if (!this.currentRenderTargetIsMain) {
            // The default framebuffer's or one of the game's own: its pair carries that clear already.
            this.gameClearsRoutedToPairs++;
            celtium$reportGameClearLatch("pair", mask, rgba, depth);
            return;
        }
        glless.noteGameClear(mask, rgba[0], rgba[1], rgba[2], rgba[3], depth);
        this.gameClearsLatchedForFrame++;
        celtium$reportGameClearLatch("frame", mask, rgba, depth);
    }

    /** Bounded, so a run states the attribution a few times instead of once per bind. */
    private void celtium$reportGameClearLatch(String route, int mask, float[] rgba, double depth) {
        if (this.gameClearLatchLog >= GAME_CLEAR_LATCH_LINES) {
            return;
        }
        this.gameClearLatchLog++;
        System.out.println("[Celtium] CELTIUM_CLEAR_LATCH route=" + route
                + " boundIdentity=0x" + Long.toHexString(this.currentRenderTarget)
                + " boundIsMain=" + this.currentRenderTargetIsMain
                + " mask=0x" + Integer.toHexString(mask)
                + " colour=(" + celtium$round3(rgba[0]) + "," + celtium$round3(rgba[1]) + "," + celtium$round3(rgba[2])
                + "," + celtium$round3(rgba[3]) + ") depth=" + (float) depth
                + " meaning=[" + ("frame".equals(route)
                        ? "the game cleared its own main framebuffer; the delivered image is cleared with this colour"
                        : "the game cleared another framebuffer; its owned pair carries this clear, not the frame image")
                + "]");
    }

    /** Three decimals, the precision the renderer's own state lines use. */
    private static float celtium$round3(float value) {
        return (float) (Math.round(value * 1000.0D) / 1000.0D);
    }

    private void destroyOffTargetFramebuffer() {
        if (this.offTargetFramebuffer != 0) {
            vkDestroyFramebuffer(this.context.device(), this.offTargetFramebuffer, null);
            this.offTargetFramebuffer = 0;
            this.offTargetFramebufferRenderPass = 0;
        }
    }

    private void destroyChainPairFramebuffer() {
        if (this.chainPairFramebuffer != 0) {
            vkDestroyFramebuffer(this.context.device(), this.chainPairFramebuffer, null);
            this.chainPairFramebuffer = 0;
            this.chainPairFramebufferRenderPass = 0;
        }
    }

    private long ensureFramebuffer(long renderPass, long colorView, long depthView) {
        if (this.framebuffer != 0 && this.framebufferRenderPass == renderPass) {
            return this.framebuffer;
        }
        destroyFramebuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.mallocLong(2);
            attachments.put(0, colorView);
            attachments.put(1, depthView);
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
        destroyOffTargetFramebuffer();
        destroyChainPairFramebuffer();
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
            // stdout, not record(): the diagnostics list is capped and this line was being dropped from it.
            System.out.println("[Celtium] CELTIUM_FOG fogColor=" + java.util.Arrays.toString(fogColor)
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
        // 0.0 is exact OpenGL parity. The property exists so the shader's level-of-detail can be swept against the
        // reference: the mirrored chain measures 5.60% while a shallow clamp measures 0.16%, and the question is whether
        // that difference is the chain's content or the LOD the shader asks for.
        block.putFloat(base + 44, MIP_BIAS_ADD);
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
        if (VulkanFogMirror.hasValue()) {
            // No GL query on this path: the game pushed GlStateManager.fogState (the same source the GL fog service
            // reads). The query below stays as the fallback for a caller that has not pushed yet.
            int mirrored = VulkanFogMirror.enabled() ? VulkanFogMirror.mode() : 0;
            if (mirrored == 0) {
                return 0;
            }
            if (mirrored == org.lwjgl.opengl.GL11.GL_EXP2) {
                return 1;
            }
            if (mirrored == org.lwjgl.opengl.GL11.GL_EXP) {
                return 2;
            }
            return 3;
        }
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
        if (pass == null) {
            // With no bridge there is no last pass to look up, and the map this reads rejects a null key with a
            // NullPointerException - which is what the remaining three-per-run nulls were. The callers all handle a null
            // pipeline already, so saying "no pipeline for no pass" is the whole fix.
            return null;
        }
        VulkanTerrainPipeline pipeline = this.pipelines.get(pass);
        if (pipeline != null) {
            return pipeline;
        }
        try {
            pipeline = VulkanTerrainPipeline.create(this.context, pass, !pass.isSorted());
            this.pipelines.put(pass, pipeline);
            record("pipeline for " + nameOf(pass) + " created [" + pipeline.vertexInputSummary() + "]");
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
    /** Swapchains dropped because the VSync option changed (task-56), so the rebuild is a number, not a claim. */
    private int presentModeRebuilds;

    private boolean ensureSwapchain() {
        // The VSync option changed since this swapchain was built (task-56): the present mode is a creation parameter,
        // so the only way to honour it is a new swapchain. Dropping it here routes the rebuild through exactly the
        // path an out-of-date surface takes - the acquire semaphores, the per-image render-finished semaphores and the
        // image ownership all belong to the swapchain object and are recreated with it, which is why this is safe to
        // do between frames and not mid-acquire.
        if (this.swapchain != null && this.swapchain.presentModeStale()) {
            record("the VSync option changed: dropping the swapchain so the present mode is rebuilt"
                    + " requested=" + org.soutalab.celtium.vulkan.VulkanSwapchain.vsyncRequested()
                    + " previous=[" + this.swapchain.presentModeSummary() + "]");
            this.swapchain.delete();
            this.swapchain = null;
            this.presentModeRebuilds++;
        }
        // Under the GL-less window there is no current context to ask, so the handle comes from Display - the same
        // GLFW window either way. Reflective because celtium-vulkan does not depend on the lwjglxx shim.
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            try {
                Class<?> display = Class.forName("org.lwjgl.opengl.Display");
                window = ((Long) display.getMethod("getWindow").invoke(null)).longValue();
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through: no window means no swapchain.
            }
        }
        if (window == 0L) {
            record("swapchain unavailable: no current GLFW window");
            return false;
        }
        int framebufferWidth;
        int framebufferHeight;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(window, width, height);
            framebufferWidth = width.get(0);
            framebufferHeight = height.get(0);
        }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            // A minimised window has no drawable: the swapchain stays as it is and the frame swaps through GL.
            this.presentSkipReason = "window has no drawable size";
            return false;
        }
        if (this.swapchain != null) {
            // The size the surface would give a fresh swapchain is the authority, not the driver's out-of-date report.
            // The check used to be gated on frameId, which only advances at a world frame boundary
            // (captureWorldSnapshot) - so a window that was resized while the menu was up (which is what a real launch
            // does: the window is created small and then sized to the display) kept the swapchain from its first
            // present. The present blit is bottom-anchored and never scaled, so it then showed the frame's lower left
            // clipped by the smaller swapchain: measured on the user's own session, the menu appeared shifted with its
            // right side and logo cut off, and going fullscreen did not change it. A present-time check costs one
            // surface-capabilities query per presented frame and is what makes the swapchain follow the window.
            if (!this.swapchain.matchesSurfaceExtent(framebufferWidth, framebufferHeight)) {
                record("the surface extent no longer matches the " + this.swapchain.width() + "x"
                        + this.swapchain.height() + " swapchain (window framebuffer is " + framebufferWidth
                        + "x" + framebufferHeight + "), rebuilding it");
                this.swapchain.delete();
                this.swapchain = null;
            }
            if (this.swapchain != null) {
                return true;
            }
        }
        java.util.List<String> reasons = new java.util.ArrayList<String>();
        this.swapchain = VulkanSwapchain.create(this.context, window, framebufferWidth, framebufferHeight, reasons);
        if (this.swapchain == null) {
            record("swapchain unavailable at surface and swapchain creation: " + reasons);
            // BH-19 evidence: this list is what names *why* the swapchain was refused - the present-support contract,
            // a surface usage/alpha/format refusal, or a vkCreateSwapchainKHR result. record()'s line reaches the
            // report but not the process stdout, so the leg that exercises the refusal (the synthetic
            // -Dceltium.vulkan.assumeNoPresentSupport=true contract test, or a real non-presenting family) had no
            // reason string in its artifact - only the present hook's lastSkip=[swapchain unavailable]. Printed once
            // per failed creation, where the run's own log keeps it.
            System.out.println("[Celtium] CELTIUM_SWAPCHAIN_FAILED " + reasons);
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
                this.lastPresentResult = this.swapchain.presentImage(this.frameTarget.colorImage(),
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, this.targetWidth,
                        this.targetHeight);
                record(this.lastPresentResult);
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

    /**
     * {@return whether the texture a batch samples is the colour attachment of the framebuffer bound right now}.
     * <p>
     * This is the material that tells a screen composite apart from the hand, an overlay or a mod's format-2 batch:
     * the composite reads the framebuffer it is about to be drawn over, the others do not. The audit asked for the
     * framebuffer to be checked rather than the vertex format alone (S17), and this is that check done on the texture
     * the batch actually binds.
     */
    /**
     * Whether the bound texture is the colour image of the framebuffer the game is currently rendering into.
     * <p>
     * This is what separates the frame's own blit - already presented at the swap point, so GL's copy must stay
     * cancelled - from an overlay that samples a different texture and blends into the frame, such as the entity
     * outline's composite. Asked of the map the game's own binds fill rather than of GL, because under the GL-less
     * window the attachment query is unanswerable and {@link #celtium$samplesBoundFramebufferColor(int)} is false
     * there for everything.
     */
    private boolean celtium$samplesCurrentTargetColor(int texture) {
        if (texture == 0 || this.currentFramebufferIdentity == 0L) {
            return false;
        }
        long[] reported = framebufferColorTexture(this.currentFramebufferIdentity);
        return reported != null && (int) reported[0] == texture;
    }

    private boolean celtium$samplesBoundFramebufferColor(int texture) {
        if (texture == 0) {
            return false;
        }
        int fbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        if (fbo == 0) {
            return false;
        }
        int type = org.lwjgl.opengl.GL45C.glGetNamedFramebufferAttachmentParameteri(fbo,
                org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type != org.lwjgl.opengl.GL11.GL_TEXTURE) {
            return false;
        }
        int attached = org.lwjgl.opengl.GL45C.glGetNamedFramebufferAttachmentParameteri(fbo,
                org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        return attached != 0 && attached == texture;
    }

    /**
     * Builds a vertex mesh for every captured display list whose geometry changed, and retires the meshes whose lists
     * did (audit N02).
     * <p>
     * This is the model path's first step: the quads were captured while {@code ModelRenderer} compiled its parts into
     * display lists, and a GL display list is replayed under the current matrix (measured: the same list shows a
     * different translation at compile and replay time and still draws correctly), so the geometry only needs the
     * replay-time matrix. Building the mesh here, on the render thread at a safe point, keeps the {@code callList} hook
     * itself free of allocation. Nothing is drawn yet - the counter says how many lists have a mesh ready.
     * <p>
     * The unit of change is a display list's compile generation, not the number of lists in the capture. Every mesh is
     * stamped with the generation it was built from and is reused exactly as it is while its list's generation is
     * unchanged, so one new list no longer rebuilds every mesh in the scene and the rebuild cost stops growing with the
     * list count. A mesh whose generation has moved on - a recompile, a delete, or an id that came back - is retired
     * through {@link VulkanDynamicMesh#retire()}, which hands its buffers to the renderer's retire queue: they are
     * destroyed only after a wait that succeeded, never while a command buffer recorded earlier may still name them.
     * The table is replaced only after the whole build succeeded, so a failure leaves the previous meshes in place, and
     * the meshes that attempt created are released before it returns.
     */
    private void celtium$buildModelMeshes() {
        if (!MODEL_MESH_BUILD) {
            return;
        }
        int[] ids = org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listIds();
        if (ids.length == 0 && this.modelMeshes == null) {
            // Nothing captured yet and nothing built: the table stays null, which the report prints as -1 rather than as
            // a build that found no lists. An empty capture with meshes already built is not this case, because those
            // meshes' lists are gone and are retired below.
            return;
        }
        java.util.LinkedHashMap<Integer, VulkanDynamicMesh> built =
                new java.util.LinkedHashMap<Integer, VulkanDynamicMesh>();
        java.util.LinkedHashMap<Integer, Integer> builtGenerations =
                new java.util.LinkedHashMap<Integer, Integer>();
        java.util.List<VulkanDynamicMesh> superseded = new java.util.ArrayList<VulkanDynamicMesh>();
        if (this.modelMeshes != null) {
            for (java.util.Map.Entry<Integer, VulkanDynamicMesh> entry : this.modelMeshes.entrySet()) {
                int listId = entry.getKey().intValue();
                VulkanDynamicMesh mesh = entry.getValue();
                if (mesh.matches(listId, org.soutalab.celtium.vulkan.VulkanModelQuadCapture.generationOf(listId))) {
                    // Unchanged since it was built: reused as it is, which is the point of the generation identity.
                    built.put(entry.getKey(), mesh);
                } else {
                    // Recompiled, deleted or emptied: the mesh is stale from the moment its list's generation moved and
                    // must not be drawn again. The retire is queued rather than freed here, so the frames that already
                    // used these buffers have completed before they are destroyed.
                    superseded.add(mesh);
                }
            }
        }
            VulkanDynamicMesh pending = null;
        try {
            // The mesh being built, held so a failure before it reaches the table can still release it.
            for (int id : ids) {
                org.soutalab.celtium.vulkan.VulkanModelQuadCapture.Snapshot snapshot =
                        org.soutalab.celtium.vulkan.VulkanModelQuadCapture.snapshot(id);
                if (snapshot == null) {
                    continue;
                }
                VulkanDynamicMesh existing = built.get(Integer.valueOf(id));
                if (existing != null && existing.matches(id, snapshot.generation)) {
                    continue;
                }
                VulkanDynamicMesh mesh = new VulkanDynamicMesh(this.context);
                // The mesh exists before it is in the table, so a failure while filling it must release it here: the
                // catch below can only discard what the table already holds, and this mesh is not in it yet (audit
                // ownership gap - the model builder leaked the one mesh it was in the middle of building).
                pending = mesh;
                // The ITEM layout: position, colour, texture, normal, padding - 28 bytes, the same one the dropped item
                // is drawn through. The colour is the material the fixed-function lighting evaluates (glColorMaterial
                // AMBIENT_AND_DIFFUSE, and vanilla draws a model with the current colour = white), and the normal is the
                // quad's own, carried by the capture since the first quad and discarded by every mesh before this.
                //
                // Measured, and this is why the layout changed: the native model draw was 1.2-1.6x brighter than the
                // upstream Celeritas reference on every entity family while the ITEM-drawn apple matched at 0.98. The
                // term is the fixed-function lighting, which IS in effect for entities - `RenderHelper
                // .enableStandardItemLighting()` is called immediately before `renderEntities` (EntityRenderer:1397-1402)
                // - and the twenty-byte POSITION_TEX pipeline has no lighting term at all. The light map, which was the
                // first candidate, was measured on this run and is nearly white at the scene's light level
                // (raw=(128,240) -> mapped=(0.531,0.969) -> factor 0.988), so it is not what the 1.3x was.
                mesh.begin(new VulkanDynamicMesh.Layout(28, 0, 16, 12, 24, -1));
                // A limit on how many quads go into the mesh, so a single quad can be drawn on its own: if one quad comes
                // out as a quad the mesh and draw path are sound and the capture is what is wrong, and if it comes out as
                // a triangle the fault is on this side. That is the question the stray triangles leave open.
                int quadLimit = snapshot.quads;
                if (MODEL_QUAD_LIMIT > 0 && quadLimit > MODEL_QUAD_LIMIT) {
                    quadLimit = MODEL_QUAD_LIMIT;
                }
                for (int quad = 0; quad < quadLimit; quad++) {
                    // nextVertex() belongs INSIDE the loop: it starts a new vertex and returns its index, and every
                    // put* call writes the vertex it started. Calling it once per quad and then writing four times
                    // overwrote one vertex with the quad's four corners, so a quad contributed ONE vertex while the
                    // indices still asked for base..base+3 - a count of Q instead of 4Q and indices past the end of
                    // the buffer. That is the corruption the shards and wedges in the presented frames were made of,
                    // and it is upstream of every matrix, topology and depth question the same frames raised.
                    int base = mesh.vertexCount();
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int started = mesh.nextVertex();
                        if (vertex == 0) {
                            base = started;
                        } else if (started != base + vertex) {
                            throw new IllegalStateException("model mesh vertex contract: quad " + quad + " vertex "
                                    + vertex + " started at " + started + ", expected " + (base + vertex));
                        }
                        int source = (quad * 4 + vertex) * 3;
                        mesh.putPosition(snapshot.positions[source], snapshot.positions[source + 1],
                                snapshot.positions[source + 2]);
                        mesh.putUv(snapshot.uvs[(quad * 4 + vertex) * 2], snapshot.uvs[(quad * 4 + vertex) * 2 + 1]);
                        // White: what glColor holds when vanilla draws a model, and the material the lighting scales.
                        mesh.putColor(255, 255, 255, 255);
                        // One normal per quad, as vanilla submits it (flat shading, one normal for all four vertices).
                        mesh.putNormal(snapshot.normals[quad * 3], snapshot.normals[quad * 3 + 1],
                                snapshot.normals[quad * 3 + 2]);
                    }
                    mesh.index(base);
                    mesh.index(base + 1);
                    mesh.index(base + 2);
                    mesh.index(base);
                    mesh.index(base + 2);
                    mesh.index(base + 3);
                }
                // The contract, checked as arithmetic rather than assumed: four vertices and six indices per quad, and
                // the indices must address vertices this mesh actually holds. A mesh that fails is not drawn.
                //
                // The failure is scoped to THIS list, not to the build. Throwing out of the loop aborts every list -
                // which is how one bad snapshot would remove every model in the scene at once, a far worse outcome
                // than the single missing model it is meant to protect against. The mesh is released and the list is
                // recorded as failed; the rest of the build continues, and the reason names the list so the snapshot
                // can be fixed rather than the symptom hidden.
                if (mesh.vertexCount() != quadLimit * 4 || mesh.indexCount() != quadLimit * 6) {
                    String failure = "list " + id + ": " + quadLimit + " quads produced " + mesh.vertexCount()
                            + " vertices and " + mesh.indexCount() + " indices, expected " + (quadLimit * 4)
                            + " and " + (quadLimit * 6);
                    this.modelMeshContractFailures++;
                    if (this.modelMeshFailure == null) {
                        this.modelMeshFailure = failure;
                    }
                    // The mesh never reaches the table, so this is a partial build's leftovers: released here rather
                    // than left for the next rebuild to replace (the audit's N02 failure path).
                    mesh.discard();
                    continue;
                }
                built.put(Integer.valueOf(id), mesh);
                pending = null;
                builtGenerations.put(Integer.valueOf(id), Integer.valueOf(snapshot.generation));
            }
        } catch (RuntimeException | LinkageError error) {
            if (pending != null) {
                pending.discard();
                pending = null;
            }
            // A failure this far out is not attributable to one list, so the previous table stays in place - nothing
            // above touched it - and the meshes this attempt created are released rather than leaked. Before N02 the
            // table was replaced at the end and every newly built mesh stayed alive on this path.
            this.modelMeshFailure = String.valueOf(error);
            for (java.util.Map.Entry<Integer, VulkanDynamicMesh> entry : built.entrySet()) {
                VulkanDynamicMesh previous = this.modelMeshes == null ? null : this.modelMeshes.get(entry.getKey());
                if (previous != entry.getValue()) {
                    entry.getValue().discard();
                }
            }
            return;
        }
        // Committed: the table the scene draws from is replaced in one step, every mesh in it is stamped with the
        // generation it was built from, and only then are the superseded meshes retired. Stamping after the commit is
        // what keeps a failed build from leaving a stamped mesh in a table that was never installed.
        this.modelMeshes = built;
        // Retired first: a list never has two meshes registered at once, so the registry's replacement counter means
        // what it says - a mesh replaced without being retired - instead of counting this commit's own window.
        for (VulkanDynamicMesh mesh : superseded) {
            mesh.retire();
        }

        for (java.util.Map.Entry<Integer, Integer> entry : builtGenerations.entrySet()) {
            built.get(entry.getKey()).stamp(entry.getKey().intValue(), entry.getValue().intValue());
        }
        int quads = 0;
        for (VulkanDynamicMesh mesh : built.values()) {
            quads += mesh.indexCount() / 6;
        }
        this.modelMeshQuads = quads;
        // Printed as well as reported: the report's debug strings are assembled per configuration, and the bridge's
        // report has never carried the model counters (checked across p530-tp, ref-gl, glow-bridge3 and bridge-model,
        // all absent while the GL-less reports carry them). Without a line both configurations emit, "the models did
        // not draw" and "the counters were not collected" look identical - which is exactly the confusion this line
        // exists to end. Printed per change, bounded: the first 64 changes one by one and every hundredth after.
        // The off-target aggregate, printed from the frame's build as the counters move: the off-target replays are the
        // entity-outline pass's, and whether they are enqueued or refused is the question the picture cannot answer on
        // its own ("a missing counter is not a missing draw" applies to the queue as much as to a census).
        if (this.modelOffTargetCalls != this.modelOffTargetReported) {
            this.modelOffTargetReported = this.modelOffTargetCalls;
            if ((this.modelOffTargetReports < 24 || this.modelOffTargetCalls % 64 == 0) && VulkanDiagnostics.enabled()) {
                this.modelOffTargetReports++;
                System.out.println("[Celtium] CELTIUM_MODEL_OFFTARGET_BATCHES calls=" + this.modelOffTargetCalls
                        + " drawn=" + this.modelOffTargetDraws
                        + " missingMeshes=" + this.modelMissingMeshes
                        + " refusals=" + this.modelDrawRefusals
                        + " lastRefusal=[" + this.modelLastRefusal + "]");
            }
        }
        if (builtGenerations.size() > 0 || !superseded.isEmpty()) {
            this.modelBuildLogs++;
            if ((this.modelBuildLogs <= 64 || this.modelBuildLogs % 100 == 0) && VulkanDiagnostics.enabled()) {
            System.out.println("[Celtium] CELTIUM_MODEL_BUILD lists=" + built.size() + " quads=" + quads
                    + " rebuilt=" + builtGenerations.size() + " reused=" + (built.size() - builtGenerations.size())
                    + " retired=" + superseded.size()
                    + " contractFailures=" + this.modelMeshContractFailures + " failure="
                    + (this.modelMeshFailure == null ? "none" : this.modelMeshFailure)
                    + " " + VulkanDynamicMesh.describeModel()
                    + " " + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lifecycle());
            }
        }
    }

    /** Whether the model meshes are built from the captured display lists (lab switch: off leaves GL drawing them). */
    // On by default since the staged acceptance: two scenes, both GPUs, the model draw inside the default's own
    // run-to-run floor (the spider scene's floor is 0 pixels and the effect 20 of 921600). -Dceltium.vulkan.modelMeshBuild=false
    // and -Dceltium.vulkan.modelDraw=false restore the previous behaviour.
    private static final boolean MODEL_MESH_BUILD = Boolean.parseBoolean(
            System.getProperty("celtium.vulkan.modelMeshBuild", "true"));
    /** The built meshes, keyed by display-list id, or null when they have not been built. */
    private java.util.LinkedHashMap<Integer, VulkanDynamicMesh> modelMeshes;
    /**
     * Native model draws and the reasons they were refused.
     * <p>
     * Static so the count lives as long as the cancellation counter it has to be compared with: the census is static and
     * survives a resource reload or a world switch, while a backend instance does not. With an instance field the two
     * numbers drifted apart on exactly those two scenes (measured: cancelled 1022 against draws 526), which reads like a
     * defect but was only the counters having different lifetimes.
     */
    private static int modelDraws;
    private int modelDrawRefusals;
    /** Display lists a replay asked for that this renderer has no captured mesh for, and the bounded log of them. */
    private int modelMissingMeshes;
    /** Meshes refused by the vertex/index contract, and the first reason, so a bad snapshot is named not hidden. */
    private int modelMeshContractFailures;
    /** How many build-summary lines have been printed; bounded, because the build repeats as lists appear. */
    private int modelBuildLogs;
    private int modelMissingMeshLog;
    private int modelLastMissingList = Integer.MIN_VALUE;
    private String modelLastRefusal = "none";
    /** Whether a frame target exists, so a pass can be opened. */
    private boolean frameTargetKnown;
    /** Texture uploads that were made to fail, and the last one, for the failure-path acceptance. */
    private int textureUploadFailures;
    private String lastTextureUploadFailure = "none";
    /** The terrain pipeline of the frame, for its render pass. */
    private VulkanTerrainPipeline terrainPipelineForFrame;
    /** The texture the immediate path most recently bound. */
    private int currentImmediateTexture;
    private int modelMeshQuads;
    private String modelMeshFailure = "none";
    /** The frame in which a terrain pass last ran, so a window can say whether it came first. */
    private long lastTerrainPassFrame = -1L;
    /** One-shot: the frame's order, for settling whether the window runs before the terrain. */
    private boolean windowOrderLogged;
    /** One-shots: the frame-target refusals that used to collapse into "no frame target". */
    private int nonTextureAttachmentLogged;
    private int foreignFboLogged;
    /** One-shot: the distinct projection depth rows the groups of a frame draw with. */
    private int groupDepthRowsSeen;
    /** One-shot: the matrices a model group is stored with. */
    /** How many model enqueues have printed their matrices. */
    private int modelMatrixLoggedCount;
    /** Model replays that arrived for a target other than the main one: the outline pass's, and anything else off-target. */
    private int modelOffTargetCalls;
    private int modelOffTargetDraws;
    private final java.util.Set<String> modelOffTargetLogged = new java.util.HashSet<String>();
    private int modelOffTargetLogs;
    /** The last off-target call count printed from the build, and how many such lines have printed. */
    private int modelOffTargetReported = -1;
    private int modelOffTargetReports;
    /** How many flush-outcome lines have printed for off-target model batches, and the bound on them. */
    private int modelOffTargetFlushLogs;
    private static final int MODEL_OFFTARGET_FLUSH_LOG_LIMIT = 6;
    /** The frame and target the last coalesced off-target flush covered, and how many batches it coalesced. */
    private long modelOffTargetFlushedFrame = -1L;
    private long modelOffTargetFlushedTarget = Long.MIN_VALUE;
    private int modelOffTargetCoalesced;
    /** The bound on {@link #modelOffTargetLogs}: the entity scene has one list set per off-target pass. */
    private static final int MODEL_OFFTARGET_LOG_LIMIT = 24;
    /** Display lists seen replayed under a texture other than their compile texture, so a layer prints once. */
    private final java.util.Set<String> modelLayerLogged = new java.util.HashSet<String>();
    private int modelLayerLogs;
    /** The bound on {@link #modelLayerLogs}: the entity scene has one layer (the spider's eyes) per texture. */
    private static final int MODEL_LAYER_LOG_LIMIT = 16;
    /** One-shot: the matrices an immediate group is stored with. */
    /** {@return whether the texture of the group being closed reports a complete mirror}. */
    private boolean groupTextureFull() {
        try {
            if (this.textureRegistry == null || this.currentGroupTexture == 0) {
                return false;
            }
            org.soutalab.celtium.vulkan.VulkanGlTexture texture =
                    this.textureRegistry.get(this.currentGroupTexture);
            return texture != null && texture.hasFullContent();
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    /** How many immediate groups have printed their raw and stored projections. */
    private int formatProbeLogged;
    /** How many particle batches have printed their light coordinates. */
    private int lightCoordLogged;
    /** How many immediate groups have printed their matrices. */
    private int immediateMatrixLogged;
    private String lastGroupDepthRow = "";
    /** One-shot: the state the model path is told about depth. */
    private boolean modelStateLogged;
    /** Lines of {@code CELTIUM_MODEL_MATRIX} written this run; see {@link VulkanDiagnostics}. */
    private int modelMatrixLines;
    /** The bound for that line: it is per model list per frame, so it needs a cap even in a diagnostics run. */
    private static final int MODEL_MATRIX_LINES = Integer.getInteger("celtium.vulkan.modelMatrixLines", 200);

    /**
     * Draws one captured display list natively, at the moment GL would have replayed it.
     * <p>
     * The recipe is the immediate path's own: the format-6 {@code POSITION_TEX} pipeline, the atlas and light map
     * descriptor, the combined matrix in the push constants, and a render pass opened for the duration because no pass
     * is open at {@code callList} time (measured: passOpen 0, passClosed 2003). The display list's vertices were captured
     * while {@code ModelRenderer} compiled it, and a display list is replayed under the current matrix rather than the
     * one it was compiled with - also measured - so the matrix used here is the one in effect now.
     * <p>
     * Lab switch: off, because this is a new draw path and the default must not change until a matched pair says the
     * image is the same. It was on for a while on the strength of terrain-only comparisons, which cannot see it: the
     * terrain-only scenes remove every entity before the capture. The entity scene then showed the difference - with the
     * draw on, the native path and the GL path differ on 18.49 % of the frame, and with only display list 2 taken
     * natively the difference is still 2.10 %, so the model geometry is not right yet. Keep it off until that is 0.
     */
    public boolean celtium$drawModelList(int listId, int boundTextureId) {
        // Which render target each model replay lands on. The entity-outline pass binds its own framebuffer, sets
        // renderOutlines and re-runs the same display lists through RenderLivingBase.renderModel
        // (`RenderGlobal.java:683-694`), so the mobs' geometry arrives here with a target that is not the main one, and
        // the group is stamped with it. A replay that is refused leaves the outline target empty and the glow invisible,
        // and no counter distinguishes "refused here" from "never called": this line does.
        long callTarget = this.currentRenderTarget;
        boolean offTargetCall = callTarget != this.mainTargetIdentity;
        if (offTargetCall) {
            this.modelOffTargetCalls++;
            if (this.modelOffTargetLogged.add(listId + "@" + callTarget)
                    && this.modelOffTargetLogs < MODEL_OFFTARGET_LOG_LIMIT) {
                this.modelOffTargetLogs++;
                VulkanDynamicMesh looked = this.modelMeshes == null
                        ? null : this.modelMeshes.get(Integer.valueOf(listId));
                System.out.println("[Celtium] CELTIUM_MODEL_OFFTARGET list=" + listId + " texture=" + boundTextureId
                        + " target=" + callTarget + " main=" + this.mainTargetIdentity
                        + " modelDraw=" + MODEL_DRAW + " frameTargetKnown=" + this.frameTargetKnown
                        + " meshes=" + (this.modelMeshes == null ? -1 : this.modelMeshes.size())
                        + " inTable=" + (looked != null) + " verts=" + (looked == null ? -1 : looked.vertexCount()));
            }
            if ((this.modelOffTargetCalls == 1 || this.modelOffTargetCalls % 32 == 0) && VulkanDiagnostics.enabled()) {
                System.out.println("[Celtium] CELTIUM_MODEL_OFFTARGET_BATCHES calls=" + this.modelOffTargetCalls
                        + " drawn=" + this.modelOffTargetDraws
                        + " missingMeshes=" + this.modelMissingMeshes
                        + " refusals=" + this.modelDrawRefusals
                        + " lastRefusal=[" + this.modelLastRefusal + "]");
            }
        }
        if (!MODEL_DRAW || this.modelMeshes == null || !this.frameTargetKnown) {
            return false;
        }
        if (MODEL_ONLY_LIST != 0 && listId != MODEL_ONLY_LIST) {
            return false;
        }
        VulkanDynamicMesh mesh = this.modelMeshes.get(Integer.valueOf(listId));
        if (mesh == null || mesh.isEmpty()) {
            // Named rather than silent: a display list this renderer never captured is a piece of the scene that the GL
            // body would draw and that nothing draws without a context. Counting it is what turns "the model is missing"
            // into "list N, texture M, no mesh", which is the only form in which it can be fixed.
            this.modelMissingMeshes++;
            if (this.modelMissingMeshLog < 8 && listId != this.modelLastMissingList) {
                this.modelLastMissingList = listId;
                this.modelMissingMeshLog++;
                System.out.println("[Celtium] CELTIUM_MODEL_MISSING list=" + listId + " texture=" + boundTextureId
                        + " (no captured mesh for this display list; the GL body runs and there is no context)");
            }
            return false;
        }
        try {
            // The texture the model is actually bound to, asked of GL at the moment of the replay: a player model samples
            // its own skin, not the block atlas the immediate path last used.
            int texture = boundTextureId != 0 ? boundTextureId : whiteTextureId();
            if (boundTextureId != 0 && this.textureRegistry != null
                    && this.textureRegistry.get(boundTextureId) == null) {
                this.modelDrawRefusals++;
                this.modelLastRefusal = "texture " + boundTextureId + " is not mirrored";
                return false;
            }
            int[] state = celtium$captureState();
            long key = celtium$stateKey(VulkanImmediateFormats.ITEM, state);
            // The layer passes: a display list replayed under a texture other than the one it was compiled with. The
            // spider's red eyes are one (LayerSpiderEyes rebinds the eyes texture and draws the same lists again with
            // additive blending and full-brightness light-map coordinates), and it is the one model family whose
            // measured ratio did not come to 1.0 after the lighting fix (0.99 r but 0.64-0.66 g/b). The body and the
            // layer draw the same geometry, so the difference between them can only be the state printed here: the
            // blend factors, the alpha test, the lighting flag and the resulting pipeline key.
            if (boundTextureId != 0
                    && boundTextureId != org.soutalab.celtium.vulkan.VulkanModelQuadCapture.textureForList(listId)
                    && this.modelLayerLogged.add(listId + "|" + boundTextureId)
                    && this.modelLayerLogs < MODEL_LAYER_LOG_LIMIT) {
                this.modelLayerLogs++;
                System.out.println("[Celtium] CELTIUM_MODEL_LAYER list=" + listId
                        + " compileTexture=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.textureForList(listId)
                        + " replayTexture=" + boundTextureId
                        + " blend=" + state[0] + "(" + state[1] + "," + state[2] + ")"
                        + " depthTest=" + state[3] + " depthWrite=" + state[4] + " cull=" + state[5]
                        + " alphaRef=" + round3(Float.intBitsToFloat(state[6]))
                        + " depthFunc=" + state[7] + " lighting=" + state[8] + " key=" + key);
            }
            if (!this.modelStateLogged) {
                // What the model path is told about the state it draws under, once: the one-quad image showed the quads
                // over the terrain and the sky alike, and depth is the first thing that can do that.
                this.modelStateLogged = true;
                System.out.println("[Celtium] CELTIUM_MODEL_STATE list=" + listId + " depthTest=" + state[3]
                        + " depthWrite=" + state[4] + " cull=" + state[5] + " blend=" + state[0]
                        + " glDepthTest=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST)
                        + " glDepthMask=" + org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK)
                        + " key=" + key);
            }
            // Asked of GL here rather than taken from the immediate path's fields: a display list is replayed under the
            // matrix in effect at that moment, and the projection gets the same clip-depth remap the immediate path uses.
            // The mirror, not glGetFloatv. Asking GL here was measured to give the orthographic projection the HUD last
            // installed - "proj=0.0,0.0,-1.0,-0.05" for the model group against the terrain's perspective
            // "0,0,-1.001,-1" - which is exactly what the one-quad image showed: model quads laid out in screen space.
            // The immediate path's matrices come from this mirror for the same reason.
            // The mirror for both, which is the best measured pairing. Taking the model-view from GL instead was tested and
            // made the difference worse (6.35 % -> 18.30 % of the entity frame), so the live model-view is not the entity's
            // either - both sources report the camera there. That is consistent with what the capture records: a display
            // list stores its vertices already transformed, so the parts carry their own placement and the matrix must not
            // put them somewhere else. What the remaining difference is belongs to the capture's side of the pairing, not to
            // the live state.
            float[] projectionNow = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION).clone();
            // The matrix the list was compiled under, which is the one its model-space vertices pair with (measured: a part
            // ranges over about +/-0.6 while the captured translation is the entity's placement at that moment). This is
            // what the capture has always stored and never used.
            float[] compileMatrix = org.soutalab.celtium.vulkan.VulkanModelQuadCapture.matrixForList(listId);
            // Identity was tried as an experiment - if the captured vertices carried their own placement, no model-view at all
            // would be right - and it measured worse than the mirror (8.08 % against 5.80 % of the entity frame), so the
            // camera rotation the mirror holds is needed. The mirror stays until the vertex space itself is settled.
            // Six pairings were measured: the mirror 5.80 % of the entity frame, identity 8.08, the captured compile-time
            // matrix 13.19, GL's live view 18.30, the mirror's rotation with GL's translation 18.40, GL's projection too
            // 18.49. No translation helping and a rotation helping says the vertices are already in the view space of the
            // moment they were captured in - so what has to be applied is the change of camera since then, which is exactly
            // V_now * C_compile^-1. Both halves are in hand: the mirror is V_now, the capture kept C_compile.
            float[] modelViewNow = this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW).clone();
            if (compileMatrix != null) {
                try {
                    org.joml.Matrix4f now = new org.joml.Matrix4f().set(modelViewNow);
                    org.joml.Matrix4f compileView = new org.joml.Matrix4f().set(compileMatrix);
                    org.joml.Matrix4f relative = now.mul(compileView.invert(), new org.joml.Matrix4f());
                    relative.get(modelViewNow);
                } catch (RuntimeException | LinkageError error) {
                    this.modelLastRefusal = "relative view failed: " + error;
                }
            }
            // The suspicion that the live projection had already been remapped was tested with a switch that copied it
            // through unchanged: the difference grew from 4.56 % to 6.01 % of the crop, so the remap is right and the
            // switch was removed rather than kept.
            float[] projectionRemapped = new float[16];
            remapClipDepth(projectionNow, projectionRemapped);
            // Gated and bounded: this line is per model list per frame, and on the operator's own session it was the
            // flood that pushed the game's log past 100,000 lines with nothing but -Dceltium.vulkan.glless set.
            if (VulkanDiagnostics.withinLimit(this.modelMatrixLines, MODEL_MATRIX_LINES)) {
                this.modelMatrixLines++;
                System.out.println("[Celtium] CELTIUM_MODEL_MATRIX list=" + listId
                        + " liveMVt=(" + round3(modelViewNow[12]) + "," + round3(modelViewNow[13]) + ","
                        + round3(modelViewNow[14]) + ")"
                        + " capturedMVt=(" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredMatrixTranslation()
                        + ")"
                        + " liveProj=(" + round3(projectionNow[0]) + "," + round3(projectionNow[5]) + ","
                        + round3(projectionNow[10]) + ")");
            }

            if (!celtium$enqueueModelMesh(mesh, texture, state, key, projectionRemapped, modelViewNow,
                    mesh.indexCount() / 6, celtium$envCombineColor())) {
                this.modelDrawRefusals++;
                this.modelLastRefusal = "could not queue list " + listId;
                return false;
            }
            // Queued, not drawn: the draw happens in the window's pass, and the group counters (submitted/completed) are
            // what show that the queued batch was actually drawn. The caller cancels the GL replay on this basis.
            this.modelDraws++;
            if (offTargetCall) {
                this.modelOffTargetDraws++;
            }
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.modelDrawRefusals++;
            this.modelLastRefusal = String.valueOf(error);
            return false;
        }
    }

    /**
     * Presents this frame's content from Vulkan, and says whether it reached the queue.
     * <p>
     * The frame's colour image is the one the game's own rendering and the native passes both wrote into (it is the shared
     * image the GL side draws through), so presenting it puts this frame on the swapchain without a GL swap. The sequence
     * is the one the probe already used: beginFrame, presentImage, endFrame.
     * <p>
     * Called once per frame from {@code Minecraft.updateDisplay}. A false return leaves the caller to swap through GL as
     * before, so a failure here cannot leave the window without a frame.
     */
    /** The post-process chain executor, created lazily the first frame a chain group actually renders. */
    private VulkanPostChain postChain;

    /**
     * {@return whether a collected pass list can run natively this frame}.
     * <p>
     * Same readiness the frame path needs: a live frame target, a recording-capable command buffer and an unstalled
     * device. The executor itself is not built until a chain actually arrives, which is most games' never.
     */
    public boolean celtium$postChainAvailable() {
        return this.frameTarget != null && !this.gpuStalled && this.commandBuffer != null
                && this.context != null;
    }

    /**
     * Runs one collected shader chain inside a GL-to-Vulkan handoff: GL releases the shared image, the passes draw
     * it (sampling it or the pure-Vulkan chain targets) and release it back, then GL waits and keeps going. The
     * caller binds the game framebuffer first, so the acquire sees the shared framebuffer under adoption.
     *
     * @return true when every pass was recorded and submitted; false leaves the caller's GL path to run instead
     */
    public boolean celtium$postChainRun(java.util.List<VulkanPostChain.Pass> passes) {
        if (!celtium$postChainAvailable() || passes == null || passes.isEmpty()) {
            return false;
        }
        try {
            if (this.postChain == null) {
                this.postChain = VulkanPostChain.create(this.context, this.frameTarget);
            }
        } catch (RuntimeException | LinkageError error) {
            record("post chain executor creation failed: " + describe(error));
            return false;
        }
        if (!this.postChain.supportsAll(passes)) {
            StringBuilder reason = new StringBuilder("post chain refused:");
            for (VulkanPostChain.Pass pass : passes) {
                reason.append(' ').append(pass.program).append('(').append(pass.pushBytes).append("B)");
            }
            record(reason.toString());
            return false;
        }
        // The chain's own input has to be registered *before* the preflight that judges whether that input can be
        // supplied. The preflight walks the pass list and accepts only the shared frame image, an image an earlier
        // pass of this chain writes, or one already adopted under that identity - and the adoption used to happen
        // only after it, inside the frame. The entity-outline chain is exactly the case where that ordering is
        // wrong: its first pass reads "final", the silhouette target the game filled itself, so nothing in the list
        // supplies it, and the adoption that would have was unreachable. Every frame refused before recording, with
        // the programs routed and no chain: measured under GL-less as ROUTE/PACKED for entity_outline, blur and blit
        // followed by postChains=0 postPasses=0 postTargets=0 postAdopted=0. Registering the target here is the same
        // call the frame-time path makes; it is idempotent for a matching image, and this method only declares the
        // identity when every check the frame-time path performs has already passed, so a preflight that passes here
        // is one the frame can really supply.
        boolean inputDeclared = CHAIN_PREFLIGHT_ADOPTION && declareChainInputAdoption(passes);
        String unsupplied = this.postChain.unsuppliedInput(passes);
        if (unsupplied != null) {
            // N04: the chain reads an image this renderer cannot supply - under the GL bridge that is a real GL
            // framebuffer no Vulkan image stands for. Refused before the frame opens: nothing is acquired, no
            // semaphore is signalled, nothing is recorded, so the caller's GL chain runs on an untouched frame.
            record("post chain refused before the frame opened: " + unsupplied);
            reportPreflightRefusal(unsupplied, inputDeclared);
            return false;
        }
        boolean submitted = false;
        try {
            this.frameTarget.beginFrame();
            beginCommands();
            this.frameTarget.recordAcquireBarriers(this.commandBuffer);
            adoptChainFramebuffers(passes);
            reportChainInputRecorded(passes.get(0).groupMainFbId);
            if (this.publishedChainOutput != null) {
                // The chain's adopted target is GENERAL by contract, and the off-target pass may not have run in
                // this frame (the game can render the chain from an image an earlier frame left behind).
                this.publishedChainOutput.ensureGeneralLayout(this.commandBuffer);
            }
            // The same hand-back for every other published name: the chain is about to attach the pair image, which
            // declares GENERAL, and the sampling side left it in SHADER_READ_ONLY.
            celtium$transitionPublishedFrameTextures(true);
            int chainRecording = this.postChain.recordPasses(this.commandBuffer, passes);
            if (this.publishedChainOutput != null) {
                // The chain has written the image and the game's composite samples it next, through a descriptor the
                // immediate pipelines declare as SHADER_READ_ONLY_OPTIMAL. Recorded in the chain's own submission,
                // so the layout is already right when the composite's command buffer runs - and the next frame's
                // off-target pass hands it back before it attaches the image again.
                this.publishedChainOutput.ensureSampledLayout(this.commandBuffer);
            }
            this.frameTarget.recordReleaseBarriers(this.commandBuffer);
            submitCommands();
            submitted = true;
            // The chain's effect on the picture, asked where it can still be attributed: one readback of the frame image
            // immediately after this submission, before anything later in the frame can overwrite it, and one of the
            // adopted chain pair when there is one. Off unless -Dceltium.vulkan.chainFrameDump=true. The passes go in
            // with it: a run holds several chains, and a sharp frame image after a chain whose output target is not
            // `main` is expected rather than evidence, so every dump line names the chain it took.
            celtium$dumpImagesAtChainTime(passes);
            // The queue accepted this submission, so the first-use barriers of *this* recording are fact - and only
            // this recording's: the token names it, so any other submission's success (a terrain pass, the next
            // frame, a buffer that was reset or abandoned) cannot commit these targets. N05 item 1.
            this.postChain.commitPasses(chainRecording);
        } catch (RuntimeException | LinkageError error) {
            VulkanGlTexture.abandonLayoutRecording(this.commandBuffer,
                    "post chain submission failed: " + describe(error));
            record("post chain submission failed: " + describe(error));
        } finally {
            if (submitted) {
                try {
                    this.frameTarget.endFrame();
                } catch (RuntimeException | LinkageError error) {
                    record("post chain frame end failed: " + describe(error));
                    return false;
                }
            } else {
                // GL is already holding the image or would wait on a signal that never comes: abandon rather than
                // leave the frame half-closed.
                this.frameTarget.abandonFrame("post chain submission refused");
                if (!this.submissionPending && !this.gpuStalled && this.commandState.recording()) {
                    // The recording the submission never made has to be closed: beginCommands() refuses to open a
                    // second recording on the same command buffer ("nested begin on backend command buffer"), so
                    // leaving the state at "recording" wedges every later terrain/immediate/chain recording
                    // (measured: frame count 205 -> 55, six nested-begin failures after one injected first-use
                    // failure). submitCommands() throws before vkQueueSubmit, so the buffer is not in flight and this
                    // reset touches nothing the queue owns - the same guard the terrain recovery path uses.
                    check(vkResetCommandBuffer(this.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT),
                            "vkResetCommandBuffer(post chain refusal)");
                    this.commandState.reset();
                }
            }
        }
        return submitted;
    }

    /**
     * Registers the chain's reported input image with the executor <em>before</em> the preflight that judges it.
     * <p>
     * The entity-outline chain's first pass reads {@code "final"} - the silhouette target the game filled itself -
     * so the preflight's own resolution (shared frame image, an image an earlier pass writes, or one already adopted
     * under that identity) can only accept it once that identity resolves to an image. The adoption that does that
     * used to run after the preflight, inside the frame, so it was unreachable: every glow frame was refused before
     * recording and the counter set was {@code postChains=0 postPasses=0 postTargets=0 postAdopted=0} with the
     * programs routed. This declares exactly what the frame-time path would adopt, and only after every check that
     * path performs has passed, so a preflight that accepts here is one the frame can really supply.
     *
     * @return whether the identity now resolves to the chain image (or already did)
     */
    private boolean declareChainInputAdoption(java.util.List<VulkanPostChain.Pass> passes) {
        GllessFrameTarget glless = offTargetTarget();
        if (glless == null || this.postChain == null || passes.isEmpty()) {
            return false;
        }
        VulkanPostChain.Pass first = passes.get(0);
        int identity = first.groupMainFbId;
        if (identity == 0 || first.groupMainW <= 0 || first.groupMainH <= 0
                || identity == (int) this.mainTargetIdentity) {
            return false;
        }
        if (framebufferColorTexture(identity) == null) {
            // Nothing was reported under this identity, so there is no GL name for the game's composite to bind
            // either; leaving it undeclared lets the preflight refuse with its own reason.
            return false;
        }
        if (!glless.chainPairMatches(first.groupMainW, first.groupMainH)) {
            // The frame-time path reports this refusal together with the size it asked for. Declaring it here would
            // let the preflight accept an input the frame cannot supply, which is the N04 stand-in failure.
            return false;
        }
        try {
            this.postChain.adoptTarget(identity, glless.chainPairColorImage(), glless.chainPairColorView(),
                    first.groupMainW, first.groupMainH);
            this.chainAdoptionDeclarations++;
            return true;
        } catch (RuntimeException | LinkageError error) {
            record("post chain input adoption could not be declared: " + describe(error));
            return false;
        }
    }

    /**
     * One bounded line per run: how much geometry had been recorded before this chain read its input image, and how
     * many groups were still queued for that image's identity at that moment.
     * <p>
     * This is the measurement the glow needs. The model path draws the silhouette into the chain pair and the counter
     * says it was drawn ({@code CELTIUM_MODEL_OFFTARGET_BATCHES calls=N drawn=N}); what is not visible from outside is
     * whether those batches had reached the image when pass 0 sampled it, because the silhouette's pass and the chain
     * are two submissions of the same frame. {@code deltaSinceLastChain} is the geometry recorded between two chain
     * reads - the whole silhouette of one frame if the ordering is right, a fraction of it if the rest is still
     * queued - and {@code pendingForInput} names the groups that are provably not in the image yet.
     */
    private void reportChainInputRecorded(int inputIdentity) {
        long pending = inputIdentity == 0 ? 0L : countGroupsForTarget(inputIdentity);
        if (this.chainInputBatchLogs < 8) {
            this.chainInputBatchLogs++;
            System.out.println("[Celtium] CELTIUM_CHAIN_INPUT_BATCHES frame=" + this.frameId
                    + " identity=" + inputIdentity + " pendingForInput=" + pending
                    + " recordedTotal=" + this.immediateRecordedBatches
                    + " deltaSinceLastChain="
                    + (this.lastChainInputRecordedBatches < 0 ? -1
                            : this.immediateRecordedBatches - this.lastChainInputRecordedBatches));
        }
        this.lastChainInputRecordedBatches = this.immediateRecordedBatches;
    }

    /**
     * Records the batches still queued for the target the game is drawing into, before a post chain reads that
     * target's image.
     * <p>
     * Called from {@link org.taumc.celeritas.mixin.core.ShaderGroupMixin} while the chain's input framebuffer is
     * still the current one and before the main framebuffer is bound: the model path coalesces its own flushes to one
     * per target change, so the silhouette batches that arrived after that first flush are still queued at chain time
     * and the bind that follows is the only other flush - this makes it explicit and ordered ahead of the read.
     *
     * @return how many groups were pending for the current target (0 when nothing had to be recorded)
     */
    public int celtium$flushPendingForChainInput() {
        if (this.gpuStalled || this.immediateWindowBusy) {
            return 0;
        }
        int pending = countGroupsForTarget(this.currentRenderTarget);
        if (pending == 0) {
            return 0;
        }
        flushPendingBeforeGlDraw();
        if (this.chainInputBatchLogs <= 12) {
            System.out.println("[Celtium] CELTIUM_CHAIN_INPUT_FLUSH frame=" + this.frameId
                    + " target=" + this.currentRenderTarget + " groups=" + pending
                    + " recordedTotal=" + this.immediateRecordedBatches);
        }
        return pending;
    }

    /**
     * One bounded line per run naming what the chain's input image holds at the moment the chain is about to read it.
     * <p>
     * The counters can say the chain ran ({@code postChains>0}) while the picture shows no outline, and the two are
     * not separable from the frame: either nothing was drawn into the adopted image, or the image was cleared after
     * the silhouette reached it, or the composite sampled a different name than the one the image was published
     * under. This prints the four facts that tell them apart - the identity and GL name the chain reads, whether the
     * pair was cleared in this frame, every off-target identity that received batches (with the pair each routes to),
     * and the name the image is currently published under.
     */
    private void reportChainInputState(int identity, long[] reported, GllessFrameTarget glless) {
        if (this.chainInputReports++ >= 3) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_OUTLINE_INPUT frame=" + this.frameId
                + " identity=" + identity + " texture=" + (reported == null ? -1L : reported[0])
                + " size=" + (reported == null ? "?" : reported[1] + "x" + reported[2])
                + " clearedThisFrame=" + (this.chainPairClearedFrame == this.frameId)
                + " chainPairClears=" + this.chainPairClears
                + " boundIdentity=" + this.currentFramebufferIdentity
                + " boundIsMain=" + this.currentRenderTargetIsMain
                + " offTargetByTarget=[" + offTargetSummary() + "]"
                + " publishedTexture=" + (this.publishedChainOutput != null ? this.publishedChainTexture : 0));
    }

    /**
     * One bounded line per run when the preflight refuses the chain.
     * <p>
     * The refusal was recorded but never printed, so a run whose chain stopped there had no line naming it - the
     * counters said a chain was seen ({@code postChains=0} with the programs routed) and nothing said why.
     */
    private void reportPreflightRefusal(String reason, boolean inputDeclared) {
        if (this.preflightRefusalsReported++ >= 2) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_POSTCHAIN PREFLIGHT_REFUSED inputDeclarationSucceeded=" + inputDeclared
                + " declarations=" + this.chainAdoptionDeclarations + " reason=" + reason);
    }

    /**
     * Adopts the renderer-owned chain-pair image under the framebuffer identity the game bound and drew into, so a
     * pass that reads another pass's framebuffer - the entity-outline chain reads the silhouette framebuffer - samples
     * what the game actually drew there instead of an empty stand-in the chain allocated for itself.
     * <p>
     * The identity map is filled only by the game's own framebuffer binds, so a chain-allocated target (its "swap"
     * sibling, say) is never in it and cannot be adopted by mistake. The pair must already be in the layout the chain
     * assumes, which is what the frame's first clear for that pair records; if the frame has not used the pair yet,
     * the clear happens here, and if it has, the silhouette is live and must not be cleared again.
     * <p>
     * The image adopted is the <em>chain</em> pair and not the default one: the default pair is where the game's
     * finished-frame composites land, and adopting it is what made the chain blur a per-frame copy of the whole
     * scene instead of the silhouette (audit N04). The silhouette's own draws reach the chain pair through the
     * identity routing in {@link #recordImmediateOnlyPass}, before this runs - the bind the caller performs just
     * ahead of the chain is what flushes them.
     */
    private void adoptChainFramebuffers(java.util.List<VulkanPostChain.Pass> passes) {
        GllessFrameTarget glless = offTargetTarget();
        if (glless == null || this.postChain == null || passes.isEmpty()) {
            // The GL bridge has real GL framebuffers behind these identities; there is no stand-in image to adopt.
            return;
        }
        // Only the identity the mixin reports as the chain's own first input, never "any framebuffer the game has
        // bound". That second rule was measured to over-adopt: `framebufferColorTextures` also remembers the targets a
        // chain bound while it was still rendering through GL, so the spectator group adopted all four of its own
        // targets (postTargets=0, postAdopted=4) and every blur pass read and wrote one image. A chain-created target
        // can never be reported as the chain's input, because the mixin reports what the group reads first.
        VulkanPostChain.Pass first = passes.get(0);
        if (first.groupMainFbId == 0) {
            return;
        }
        adoptChainFramebuffer(first.groupMainFbId, first.groupMainW, first.groupMainH, glless);
    }

    private void adoptChainFramebuffer(int identity, int width, int height, GllessFrameTarget glless) {
        long[] reported = identity == 0 ? null : framebufferColorTexture(identity);
        if (reported == null || width <= 0 || height <= 0 || identity == (int) this.mainTargetIdentity) {
            return;
        }
        if (!glless.chainPairMatches(width, height)) {
            // Never resize a live pair here: the silhouette it holds is what the chain is about to read. A pair that
            // does not exist at all is refused the same way rather than created empty: the chain's real input is the
            // silhouette, and an image with nothing in it would make the chain report a run whose picture is the
            // deterministic result of reading nothing - the stand-in failure the audit measured, with a counter that
            // says otherwise. Refusing leaves the caller's GL chain in charge, which is the audit's N04 rule.
            this.chainAdoptionsRefused++;
            record("post chain adoption refused: the chain image is "
                    + (glless.chainPairExists() ? "not " + width + "x" + height : "absent")
                    + " (asked for " + width + "x" + height + ")");
            return;
        }
        // Before anything in this submission clears, attaches or samples the image: the previous frame's composite
        // may have left it in SHADER_READ_ONLY, and both the clear and the chain declare GENERAL.
        this.celtium$syncPublishedChainOutput(glless.chainPairColorImage());
        if (!glless.chainPairAcquired() || this.chainPairClearedFrame != this.frameId) {
            if (glless.chainPairNeedsSelfTest()) {
                record(glless.chainPairSelfTest(this.commandBuffer));
            } else {
                glless.clearChainPair(this.commandBuffer);
            }
            this.chainPairClearedFrame = this.frameId;
            this.chainPairClears++;
        }
        this.postChain.adoptTarget(identity, glless.chainPairColorImage(), glless.chainPairColorView(),
                width, height);
        this.chainAdoptions++;
        reportChainInputState(identity, reported, glless);
        // CELTIUM-OUTLINE-COMPOSITE: the chain's output IS this image, and the game composites it by binding the
        // outline framebuffer's colour texture name - the one the bind reported for this identity. Publishing the
        // image under that name is what gives the composite's batch a texture to sample: every batch resolves its
        // bound name through the registry, and with no entry there this one is refused and the glow never reaches
        // the screen. Nothing is created, resized, transitioned or freed on the image's behalf here.
        this.publishedChainOutput = celtium$registry().publishExternalImage((int) reported[0],
                glless.chainPairColorImage(), glless.chainPairColorView(), width, height);
        this.publishedChainTexture = (int) reported[0];
        this.publishedChainImage = glless.chainPairColorImage();
    }

    /**
     * Keeps the published outline output current and hands it back to the layout its owner attaches it in.
     * <p>
     * Called from the two places the chain pair's colour image is about to be used as an attachment: the off-target
     * pass that draws the game's own framebuffer into it, which clears it and then attaches it, and the chain
     * submission, whose adopted target is GENERAL by contract. The composite is the one sampler, and its transition
     * to SHADER_READ_ONLY is recorded at the end of the chain submission; without this hand-back the next attachment
     * would begin a render pass on an image in the wrong layout. A pair recreated at a new size is unpublished
     * rather than transitioned, because its wrapper names a destroyed image.
     *
     * @param chainPairColorImage the colour image the chain pair holds now, which is what the wrapper must name
     */
    /**
     * Publishes a game framebuffer's colour texture name over the chain pair's image, and keeps it current.
     * <p>
     * One entry per GL texture name, holding the image that name currently stands for. The same name is published
     * again only when the pair has been recreated (a new size makes a new image); a recreated pair's old image is
     * destroyed, so the wrapper that named it is unpublished first - the rule {@code celtium$syncPublishedChainOutput}
     * follows for the outline's name. Called from the off-target pass that draws the game's own framebuffer into the
     * pair, which is the moment both facts are true: the identity being drawn is known, and the image its draws land in
     * is the pair.
     */
    private void celtium$publishFrameTexture(int glTextureName, long image, long view, int width, int height) {
        if (glTextureName == 0 || image == 0L || width <= 0 || height <= 0) {
            return;
        }
        VulkanGlTexture current = this.publishedFrameTextures.get(Integer.valueOf(glTextureName));
        if (current != null && this.publishedFrameImages.get(Integer.valueOf(glTextureName)) != null
                && this.publishedFrameImages.get(Integer.valueOf(glTextureName)).longValue() == image) {
            return;
        }
        if (current != null) {
            celtium$registry().unpublishExternalImage(glTextureName);
        }
        VulkanGlTexture published = celtium$registry().publishExternalImage(glTextureName, image, view, width, height);
        if (published == null) {
            return;
        }
        this.publishedFrameTextures.put(Integer.valueOf(glTextureName), published);
        this.publishedFrameImages.put(Integer.valueOf(glTextureName), Long.valueOf(image));
        this.publishedFrameTexturesCreated++;
    }

    /**
     * Moves every published frame texture to the layout its next use needs.
     * <p>
     * Two uses alternate on the same image: the pass that draws the game's framebuffer into the pair attaches it, which
     * declares GENERAL, and the pass that samples it through the published name has a descriptor declaring
     * SHADER_READ_ONLY_OPTIMAL. {@code general} picks which one is next; the sampling side is the transition that was
     * missing, and its absence is the VUID-vkCmdDraw-None-09600 the vsync leg reported at vkQueueSubmit.
     */
    private void celtium$transitionPublishedFrameTextures(boolean general) {
        if (this.publishedFrameTextures.isEmpty() || this.commandBuffer == null) {
            return;
        }
        celtium$pruneStalePublishedNames();
        if (this.publishedFrameTextures.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<Integer, VulkanGlTexture> entry : this.publishedFrameTextures.entrySet()) {
            // The sampling side must not move the image this pass is about to attach: the pass declares GENERAL for
            // attachment 0, and the transition to SHADER_READ_ONLY_OPTIMAL right before vkCmdBeginRenderPass is
            // VUID-vkCmdBeginRenderPass-initialLayout-00900. The attach-side hand-back (the `true` direction) already
            // moved it to GENERAL for this pass; leaving it there is the whole fix. A batch that samples this same
            // image inside the pass would be a feedback loop and is not a case this rule has to serve.
            if (!general && this.passAttachedColorImage != 0L) {
                Long image = this.publishedFrameImages.get(entry.getKey());
                if (image != null && image.longValue() == this.passAttachedColorImage) {
                    this.publishedFrameTexturesSkippedForAttach++;
                    if (this.publishedFrameTexturesSkippedForAttach == 1) {
                        record("published name " + entry.getKey() + " stands for the image this pass attaches (0x"
                                + Long.toHexString(this.passAttachedColorImage) + "); the sampling-side transition"
                                + " would leave it in SHADER_READ_ONLY under a pass that declares GENERAL, so it is"
                                + " skipped and the attach-side hand-back keeps it in GENERAL");
                    }
                    continue;
                }
            }
            if (general) {
                entry.getValue().ensureGeneralLayout(this.commandBuffer);
            } else {
                entry.getValue().ensureSampledLayout(this.commandBuffer);
            }
        }
    }

    /**
     * Drops every published name whose image is no longer the pair's live image.
     * <p>
     * A name is published over the renderer's own image so the game's composite can sample it by the name it binds. That
     * image is destroyed and replaced when the pair is recreated (a size change, the first real frame after the splash),
     * and the wrapper then names freed memory. Recording a barrier on it is not a validation warning: the menu leg
     * measured `vkCmdPipelineBarrier(): pImageMemoryBarriers[0].image Invalid VkImage Object 0x300000000030` and then a
     * <b>SIGSEGV inside libvulkan_radeon</b> — the driver dereferenced the freed handle. The unpublish-on-recreation rule
     * already existed ({@code celtium$syncPublishedChainOutput}), but it only ran on the chain-identity branch, so a
     * frame that samples the name before that branch runs used the stale wrapper. This is the same rule at the point of
     * use, so a recreated pair cannot be sampled through a name that still points at the old image.
     */
    private void celtium$pruneStalePublishedNames() {
        if (this.publishedFrameTextures.isEmpty()) {
            return;
        }
        GllessFrameTarget offTarget = offTargetTarget();
        if (offTarget == null) {
            // Not the GL-less frame target (the bridge configuration keeps its own target type): the published names are
            // not ours to judge here.
            return;
        }
        long live = offTarget.chainPairColorImage();
        java.util.Iterator<java.util.Map.Entry<Integer, VulkanGlTexture>> iterator =
                this.publishedFrameTextures.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<Integer, VulkanGlTexture> entry = iterator.next();
            Long image = this.publishedFrameImages.get(entry.getKey());
            // A name survives only while the pair still owns the very image it names. `live == 0` means the pair has been
            // released (teardown, or the release half of a re-creation) and then no published name can be valid: the
            // first version of this rule kept the names in that case, and the barrier landed on the freed handle - the
            // measured crash was this method's caller, `ensureSampledLayout` on image 0x300000000030, with the
            // Java frame `celtium$transitionPublishedFrameTextures` under it.
            if (image != null && live != 0L && image.longValue() == live) {
                continue;
            }
            // The name's meaning is "the game framebuffer's colour texture, whose pixels the renderer serves from its
            // pair image". When that image is recreated the name must follow it to the new one, not disappear: a draw
            // that samples an unpublished name resolves to no view, and the measured consequence was a descriptor with
            // `VkImageView 0x0` (invalid or destroyed) followed by VK_ERROR_DEVICE_LOST on a plain terrain leg. So the
            // re-publication happens here, before any barrier could be recorded on the old handle, and the wrapper is
            // replaced with one standing for the live image.
            if (live != 0L) {
                VulkanGlTexture republished = celtium$registry().publishExternalImage(entry.getKey().intValue(), live,
                        offTarget.chainPairColorView(), this.targetWidth, this.targetHeight);
                if (republished != null) {
                    entry.setValue(republished);
                    this.publishedFrameImages.put(entry.getKey(), Long.valueOf(live));
                    this.publishedFrameTexturesRepublished++;
                    continue;
                }
            }
            // No live pair image at all (teardown, or the release half of a re-creation): there is nothing valid to
            // point the name at, so it is dropped rather than left naming freed memory.
            celtium$registry().unpublishExternalImage(entry.getKey().intValue());
            this.publishedFrameImages.remove(entry.getKey());
            this.publishedFrameTexturesUnpublished++;
            this.publishedFrameTexturesStalePruned++;
            if (this.publishedFrameTexturesStalePruned == 1) {
                record("published name " + entry.getKey() + " named image 0x"
                        + (image == null ? "unknown" : Long.toHexString(image.longValue()))
                        + " which is no longer the pair's image (0x" + Long.toHexString(live)
                        + "); unpublished before any barrier could be recorded on it");
            }
            iterator.remove();
        }
    }

    private void celtium$syncPublishedChainOutput(long chainPairColorImage) {
        // A pair recreated at a new size destroys the image every published name stands for: those wrappers name freed
        // memory, so they are unpublished and dropped here - the same rule the outline's own name follows below. This
        // runs before the early return that only looks at the outline's wrapper, because the other names are published
        // even when the outline never adopted anything (the vanilla loading screen's framebuffer is the measured case).
        if (!this.publishedFrameTextures.isEmpty()) {
            GllessFrameTarget liveTarget = offTargetTarget();
            long liveImage = liveTarget == null ? 0L : liveTarget.chainPairColorImage();
            long liveView = liveTarget == null ? 0L : liveTarget.chainPairColorView();
            java.util.Iterator<java.util.Map.Entry<Integer, VulkanGlTexture>> iterator =
                    this.publishedFrameTextures.entrySet().iterator();
            while (iterator.hasNext()) {
                java.util.Map.Entry<Integer, VulkanGlTexture> entry = iterator.next();
                Long image = this.publishedFrameImages.get(entry.getKey());
                if (image != null && image.longValue() == chainPairColorImage) {
                    continue;
                }
                // The pair was recreated, and the name's meaning is "the game framebuffer's colour texture, whose pixels
                // the renderer serves from its pair image": it must follow the image to the new one. Dropping it here is
                // what made the loading phase a uniform white field - the registry resolves a name with no external image
                // through announcements and then the empty mirror, and the composite that samples it then paints the
                // empty mirror. The re-pointing is the same rule `celtium$pruneStalePublishedNames` applies at the point
                // of use, applied at the point of recreation, which is where it was measured to happen five times in one
                // leg for glTexture=22.
                if (liveImage != 0L && liveView != 0L) {
                    VulkanGlTexture republished = celtium$registry().publishExternalImage(entry.getKey().intValue(),
                            liveImage, liveView, this.targetWidth, this.targetHeight);
                    if (republished != null) {
                        entry.setValue(republished);
                        this.publishedFrameImages.put(entry.getKey(), Long.valueOf(liveImage));
                        this.publishedFrameTexturesRepublished++;
                        continue;
                    }
                }
                celtium$registry().unpublishExternalImage(entry.getKey().intValue());
                this.publishedFrameImages.remove(entry.getKey());
                this.publishedFrameTexturesUnpublished++;
                iterator.remove();
            }
        }
        if (this.publishedChainOutput == null) {
            return;
        }
        if (this.publishedChainImage != chainPairColorImage) {
            celtium$registry().unpublishExternalImage(this.publishedChainTexture);
            this.publishedChainOutput = null;
            this.publishedChainImage = 0L;
            return;
        }
        this.publishedChainOutput.ensureGeneralLayout(this.commandBuffer);
    }

    public boolean celtium$presentFrame() {
        // Counted before any condition: the entry counter is what says whether the per-frame call arrives at all, which the
        // attempt counter cannot (it is only reached after the early returns).
        this.presentHookCalls++;
        // The counters below are otherwise only printed by event-driven reports, which say what happened the first time
        // something changed rather than where the session ended up. This heartbeat is what makes the present path
        // readable at the end of a run: whether the per-frame call arrives, and if it does not present, which gate
        // returned it.
        if (this.presentHookCalls <= 3 || this.presentHookCalls % 300 == 0) {
            System.out.println("[Celtium] CELTIUM_PRESENT hookCalls=" + this.presentHookCalls
                    + " attempts=" + this.presentAttempts + " presented=" + this.framePresentedFromVulkan
                    + " failures=" + this.presentFailures + " swapsSkipped=" + this.glSwapsSkipped
                    + " waiting=" + this.lastFrameReachedQueue + " lastSkip=[" + this.presentSkipReason
                    + "] lastResult=[" + this.lastPresentResult + "]");
        }
        if (this.frameTarget == null || this.gpuStalled) {
            this.presentSkipReason = "no frame target, or stalled";
            return false;
        }
        if (!ensureSwapchain()) {
            // The swapchain is created here rather than only by the diagnostic probe: "anything that needs to present calls
            // this" is what that method's own contract says, and this is the second caller it was written for.
            this.presentSkipReason = "swapchain unavailable";
            return false;
        }
        if (!this.lastFrameReachedQueue) {
            this.presentSkipReason = "no completed frame waiting";
            // No completed frame's content is waiting in the shared image. frameSubmitReachedQueue cannot be used here:
            // it is cleared when the next frame begins, and this call happens after that.
            return false;
        }
        // Consumed: the next call presents the frame after this one.
        this.lastFrameReachedQueue = false;
        try {
            if (!ensureFrameTarget()) {
                this.presentSkipReason = "frame target not available";
                return false;
            }
            // No beginFrame/endFrame around this any more. The probe needed them because it produced the content itself,
            // but in the normal frame the content and the copyback have already happened: the shading was measured as
            // +13.4 ms per frame on Intel with them (AMD: no measurable cost), which is the price of a second copyback and
            // its waits per frame. The shared image is left in the layout GL declares, which is what is passed here.
            // VK_IMAGE_LAYOUT_GENERAL is the layout both targets leave the colour image in after a frame's release
            // barrier (VulkanFrameTarget.recordReleaseBarriers and GllessFrameTarget.recordReleaseBarriers both end
            // there), so it is the layout this present really finds. Taking it from the target's own bookkeeping was
            // tried and reverted: the bridge target records the layout its *GL* side leaves, which is a different
            // question from the one this call asks.
            // Except when ensureFrameTarget() above has just rebuilt the target - a world or dimension change does -
            // because a fresh image is UNDEFINED and no barrier for it has reached any submitted buffer yet.
            // Declaring GENERAL for it makes this blit's source barrier claim a layout the image is not in, which the
            // layer reports at vkQueueSubmit as VUID-vkCmdDraw-None-09600 (its submit-time first-layout check, whose
            // "vkCmdDraw" is a hardcoded label rather than the command). The GL-less target's own bookkeeping answers
            // this exactly - UNDEFINED while fresh, GENERAL once a submitted frame has transitioned it - so it is
            // asked only in the configuration that owns the image; the bridge's tracking answers a different question.
            int presentSourceLayout = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                    ? this.frameTarget.colorImageLayout()
                    : org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
            // The image is handed back in GENERAL whatever it was found in, because UNDEFINED is a legal oldLayout for
            // the source transition and an illegal newLayout for the hand-back: passing the found layout through as the
            // restore target produced VUID-VkImageMemoryBarrier-newLayout-01198 on a freshly rebuilt target (the
            // present's restore barrier asked for newLayout=UNDEFINED). Content is not claimed by saying GENERAL here -
            // a fresh image has none either way, and the next frame's acquire barrier is what gives it a layout it can
            // be drawn into.
            this.lastPresentResult = this.swapchain.presentImage(this.frameTarget.colorImage(),
                    presentSourceLayout, this.targetWidth, this.targetHeight, org.lwjgl.vulkan.VK10.VK_NULL_HANDLE,
                    org.lwjgl.vulkan.VK10.VK_NULL_HANDLE, org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
            this.frameTarget.noteColorImageLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
            this.presentAttempts++;
            this.presentSkipReason = "presented";
            boolean reached = this.swapchain.lastPresentSucceeded();
            if (reached) {
                this.framePresentedFromVulkan++;
                dumpFrameIfRequested(this.framePresentedFromVulkan);
                // The caller skips the game's own Display.update when this returns true, and that call is also what polls
                // the window's events, so they are polled here instead: input keeps working without the GL swap. Done in
                // this module because the GLFW classes are on its classpath and not on the game module's.
                celtium$pollEvents();
                // The game's own Display.update runs after this and would swap through GL as well. The frame that swap
                // would show is the one just presented, so it is skipped once, at the swap point itself.
                this.swapPendingFromVulkan = true;
            } else {
                this.presentFailures++;
            }
            return reached;
        } catch (RuntimeException | LinkageError error) {
            this.presentFailures++;
            this.lastPresentResult = "present threw: " + error;
            return false;
        }
    }

    /**
     * Writes the presented image out every N frames when a directory is named, which is how the window's content is
     * inspected over a run: one image at the fixture's capture cannot say whether what is on screen is a loading
     * screen, the world, or a frame that never got drawn.
     */
    private void dumpFrameIfRequested(int presented) {
        // Keep the fixture's own dump fresh as well. It asks for it once, and the write happens at the next present;
        // when the run ends before that present - which is what a capture followed by a shutdown does - the file on
        // disk is still the early one, and the picture assertions measure a frame from before the world existed.
        // Re-asking on a cadence bounds the age instead of depending on where the run happens to stop.
        if (this.presentedDumpPath != null && presented % 40 == 0) {
            this.swapchain.requestPresentedImageDump(this.presentedDumpPath);
        }
        String directory = System.getProperty("celtium.vulkan.frameDumpDir");
        if (directory == null) {
            return;
        }
        int every = Integer.getInteger("celtium.vulkan.frameDumpEvery", 60).intValue();
        if (every <= 0 || presented % every != 0) {
            return;
        }
        if (this.frameDumpsWritten >= Integer.getInteger("celtium.vulkan.frameDumpCount", 6).intValue()) {
            return;
        }
        this.frameDumpsWritten++;
        celtium$dumpPresentedImage(java.nio.file.Paths.get(directory, "frame-" + presented + ".png"));
    }

    /** Polls the window's events. The caller skips Display.update when a frame was presented, and that call polls them. */
    public void celtium$pollEvents() {
        try {
            org.lwjgl.glfw.GLFW.glfwPollEvents();
        } catch (RuntimeException | LinkageError ignored) {
            // Polling is not worth failing a frame over.
        }
    }


    /**
     * {@return whether a presented frame is still waiting for the game's swap point, consuming that fact}.
     * <p>
     * True at most once per presented frame: the caller uses it to skip the GL swap for a frame Vulkan already showed,
     * and the answer is cleared here so that a frame which was not presented still swaps through GL.
     */
    public boolean celtium$consumeSwapPendingFromVulkan() {
        boolean pending = this.swapPendingFromVulkan;
        this.swapPendingFromVulkan = false;
        if (pending) {
            this.glSwapsSkipped++;
        }
        return pending;
    }

    /**
     * {@return the mean of the last image presented to the swapchain}, or -1 when it was not read back.
     * <p>
     * Exposed so the fixture can record it at the capture: an earlier comparison of this mean against the GL screenshot's
     * was wrong because the two were taken at different moments, and a value recorded at the capture is the same frame.
     * It is only meaningful with {@code -Dceltium.vulkan.presentReadback=true}.
     */
    public double celtium$presentedImageMean() {
        return this.swapchain == null ? -1.0D : this.swapchain.lastPresentedMean();
    }

    /**
     * Writes the frame the swapchain last received to a PNG.
     * <p>
     * The GL-independent image: the game's screenshot reads OpenGL's default framebuffer, which is why the GL composite
     * cannot be removed while that is the only picture available. Written at the capture so it belongs to the same frame.
     */
    public boolean celtium$dumpPresentedImage(java.nio.file.Path path) {
        if (this.swapchain == null) {
            return false;
        }
        this.swapchain.requestPresentedImageDump(path);
        this.presentedDumpPath = path;
        this.presentedImageDumpRequested = true;
        return true;
    }

    // ---- glCopyTexImage2D / glCopyTexSubImage2D: the read side of the copy --------------------------------------
    //
    // The main menu's panorama is the measured caller (GuiMainMenu.renderSkybox -> drawPanorama -> 7x
    // rotateAndBlurSkybox): 384 quads are drawn into the default framebuffer with a 256x256 viewport and then copied
    // into a 256x256 dynamic texture with glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, 256, 256), seven times, each copy
    // followed by three blur quads. Under GL-less the stub has no framebuffer to read, so those copies were a named
    // refusal and the skybox texture kept the flat colour the game had uploaded - the menu's background.
    //
    // What the copy needs is the pixels the game's own draws put in the bound read framebuffer, and this renderer owns
    // that image: identity 0 is the GL-less default framebuffer, whose draws the off-target pass puts in the frame
    // target's default pair (see GllessFrameTarget.pairLabelForIdentity), and the game's main identity is drawn into
    // the frame image the present path blits from. Nothing else is served: a read of a framebuffer this renderer does
    // not own (a mod's own FBO) stays a refusal rather than reading whatever happens to be bound.

    /** Copy requests served from an owned image, and those refused, with the reason of the last refusal. */
    private int textureCopiesServed;
    private int textureCopiesRefused;
    /** How many served copies have printed their rectangle's statistics (bounded, for the picture question). */
    private int textureCopySamples;
    /** How many chain-time image dumps have been written (bounded; see {@code celtium$dumpImagesAtChainTime}). */
    private int chainFrameDumps;
    /**
     * The frame the served-copy set belongs to, and the destination rectangles already served in it.
     * <p>
     * The menu asks for the same rectangle seven times per frame; one readback answers all seven, and this set is what
     * makes that a decision rather than a repeated cost. Cleared whenever the frame changes.
     */
    private long textureCopyFrame = -1L;
    private final java.util.Set<Long> textureCopiesServedThisFrame = new java.util.HashSet<Long>();
    private int textureCopiesReusedInFrame;
    /** Readback submissions the copy path actually made, and the wall time they took. */
    private int textureCopyReadbacks;
    private long textureCopyReadbackNanos;
    private String lastTextureCopyRefusal = "none";

    /**
     * Serves one {@code glCopyTexImage2D}/{@code glCopyTexSubImage2D} from an image this renderer owns.
     * <p>
     * The caller is the GL-less stub, which knows the destination texture name, the level and offset the copy names,
     * and the source rectangle. The pixels come from the same route the presented-image dump uses (a transfer-source
     * copy into a host-visible buffer), but of a rectangle of the owned image rather than of the whole swapchain image
     * at the swap point.
     * <p>
     * The copy is synchronous on purpose. The game draws the faces, copies them and then draws the blur from the copy
     * in the same frame: a read that returned the previous frame's content would put the blur on the wrong pixels, and
     * the ordering is the game's, not the renderer's to defer. So the pending groups are flushed (which submits and
     * waits - the same immediate-window path an ordering flush uses) before the rectangle is read.
     * <p>
     * One readback per frame per destination rectangle. The menu asks for the same 256x256 region seven times a frame
     * (GuiMainMenu.rotateAndBlurSkybox, each pass followed by three blur quads), and seven queue waits per frame is a
     * menu that stutters: measured as the cost this method reports. The later requests in the same frame are answered
     * from the content the first one read, which is a stated simplification - vanilla's passes each see the blur the
     * pass before them drew - and the counter {@code textureCopiesReusedInFrame} says how many were answered that way,
     * so the difference is visible rather than implied.
     *
     * @param destName the GL texture name the copy writes into
     * @param level    the destination level; only level 0 is served, because the mirror's patch path is level 0
     * @param xoffset  the destination offset inside that level
     * @param yoffset  the destination offset inside that level
     * @param x        the source rectangle's left edge in the read framebuffer
     * @param y        the source rectangle's bottom edge in the read framebuffer (GL's origin)
     * @param width    the source rectangle's width, also the copied width
     * @param height   the source rectangle's height, also the copied height
     * @param readFbo  the framebuffer the game bound for reading
     * @return {@code "served"} when the pixels reached the destination mirror, otherwise a reason that names itself
     */
    public String celtium$serveTextureCopy(int destName, int level, int xoffset, int yoffset,
            int x, int y, int width, int height, int readFbo) {
        if (destName == 0 || width <= 0 || height <= 0) {
            return celtium$refuseTextureCopy("invalid arguments: dest=" + destName + " " + width + "x" + height);
        }
        if (level != 0) {
            // The mirror's patch path carries level 0 only (announceFullPixels walks data[level] from 0 and stops at the
            // first absent level), so a copy into a higher level is named rather than written to level 0 as if it were
            // the same image. No measured caller does this: the panorama copies into level 0.
            return celtium$refuseTextureCopy("level " + level + " (the mirror's patch path serves level 0)");
        }
        long image = celtium$ownedReadImage(readFbo);
        if (image == 0L) {
            return celtium$refuseTextureCopy("read framebuffer " + readFbo + " is not an image this renderer owns");
        }
        // The frame's first request for this destination rectangle pays for the readback; the rest are answered from it.
        long copyKey = (((long) destName) << 32) ^ (((long) level) << 28) ^ (((long) xoffset) << 20)
                ^ (((long) yoffset) << 12) ^ (((long) x) << 8) ^ (((long) y) << 4) ^ (((long) width) << 2)
                ^ ((long) height);
        if (this.textureCopyFrame != this.frameId) {
            this.textureCopyFrame = this.frameId;
            this.textureCopiesServedThisFrame.clear();
        }
        if (this.textureCopyFrame == this.frameId && this.textureCopiesServedThisFrame.contains(Long.valueOf(copyKey))) {
            this.textureCopiesReusedInFrame++;
            this.textureCopiesServed++;
            return "served";
        }
        // What the game drew into that image is still queued as groups: submitting them is what makes the read the
        // frame the copy is about. flushPendingBeforeGlDraw draws into an open pass if there is one, otherwise it opens
        // and completes an immediate window, which is the same ordering boundary the game's own draw calls use.
        if (!this.immediateGroups.isEmpty()) {
            flushPendingBeforeGlDraw();
        }
        if (!waitForIdle()) {
            return celtium$refuseTextureCopy("the GPU did not confirm the draws before the copy completed");
        }
        long readbackStartedNanos = System.nanoTime();
        int[] pixels = celtium$readImageRectangle(image, x, y, width, height);
        this.textureCopyReadbackNanos += System.nanoTime() - readbackStartedNanos;
        this.textureCopyReadbacks++;
        if (pixels == null) {
            return celtium$refuseTextureCopy("the rectangle read of the owned image failed");
        }
        if (this.textureRegistry == null) {
            this.textureRegistry = celtium$registry();
        }
        int[][] levels = new int[1][];
        levels[0] = pixels;
        if (this.textureCopySamples < 3) {
            // What the copy actually served, bounded to three lines: the mean and the opaque fraction of the rectangle
            // are what tell "the source image holds the accumulated panorama" from "it holds a cleared surface", which
            // the destination mirror's own census cannot (it reports the mirror's state at creation, not per copy).
            this.textureCopySamples++;
            long red = 0L;
            long green = 0L;
            long blue = 0L;
            int opaque = 0;
            for (int pixel : pixels) {
                red += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                blue += pixel & 0xFF;
                if (((pixel >>> 24) & 0xFF) >= 0x80) {
                    opaque++;
                }
            }
            int count = Math.max(1, pixels.length);
            System.out.println("[Celtium] CELTIUM_TEX_COPY_SERVED dest=" + destName + " level=" + level
                    + " destOffset=" + xoffset + "," + yoffset + " source=" + x + "," + y + " " + width + "x" + height
                    + " readFbo=" + readFbo + " mean=" + (red / count) + "," + (green / count) + "," + (blue / count)
                    + " opaque=" + opaque + "/" + pixels.length + " served=" + (this.textureCopiesServed + 1));
        }
        boolean applied = this.textureRegistry.announceFullPixels(destName, levels, width, height, xoffset, yoffset);
        if (!applied) {
            return celtium$refuseTextureCopy("the destination mirror refused the pixels: "
                    + this.textureRegistry.lastFailure());
        }
        this.textureCopiesServedThisFrame.add(Long.valueOf(copyKey));
        this.textureCopiesServed++;
        return "served";
    }

    /**
     * One-shot readback of the frame image and of the adopted chain pair immediately after the chain's submission.
     * <p>
     * The question it answers is where a post chain's effect goes: the passes demonstrably run ({@code postChains},
     * {@code postPasses}, {@code postTargets}) and the presented picture still shows no blur, so either the frame image
     * holds the chain's output at this point - and the loss is later, in the present or composite path - or it does not,
     * and the passes' writes are not landing in the image the chain's output target claims. Both answers name the next
     * owner. The readback is the same one the texture-copy path uses, of the whole image, and it is synchronous because
     * it has to see this submission and nothing after it. Off unless
     * {@code -Dceltium.vulkan.chainFrameDump=true}; the path comes from
     * {@code -Dceltium.vulkan.chainFrameDumpPath} (default {@code /tmp/celtium-chain-frame.png}) and the count from
     * {@code -Dceltium.vulkan.chainFrameDumps} (default 1).
     */
    private void celtium$dumpImagesAtChainTime(java.util.List<VulkanPostChain.Pass> passes) {
        if (!Boolean.getBoolean("celtium.vulkan.chainFrameDump")) {
            return;
        }
        if (this.chainFrameDumps >= Integer.getInteger("celtium.vulkan.chainFrameDumps", 1).intValue()) {
            return;
        }
        this.chainFrameDumps++;
        // Which chain this is: a run holds several, and the frame image is only expected to carry the output of the one
        // whose output target is `main`. The identity is the first pass's program, how many passes there are, whether
        // that first pass reads and the last one writes the frame image, and the framebuffer ids involved.
        String identity = "chain=" + this.chainFrameDumps + " passes=" + passes.size()
                + " first=" + passes.get(0).program + " last=" + passes.get(passes.size() - 1).program
                + " inIsMain=" + passes.get(0).inIsMain + " outIsMain=" + passes.get(passes.size() - 1).outIsMain
                + " outFbId=" + passes.get(passes.size() - 1).outFbId
                + " frameId=" + this.frameId;
        if (!waitForIdle()) {
            record("chain frame dump skipped: the GPU did not confirm the chain submission");
            return;
        }
        String base = System.getProperty("celtium.vulkan.chainFrameDumpPath", "/tmp/celtium-chain-frame.png");
        if (this.frameTarget != null) {
            celtium$dumpOwnedImage("frame", this.frameTarget.colorImage(), base, identity,
                    this.targetWidth, this.targetHeight);
        }
        GllessFrameTarget glless = offTargetTarget();
        if (glless != null && glless.chainPairColorImage() != 0L
                && glless.chainPairMatches(this.targetWidth, this.targetHeight)) {
            // Dumped at the frame's size: the pair is created at the size the game's framebuffer asks for, and the
            // readback's extent has to be inside the image it reads. A pair of another size is named rather than read.
            celtium$dumpOwnedImage("chain-pair", glless.chainPairColorImage(), base,
                    identity + " pairSize=" + this.targetWidth + "x" + this.targetHeight,
                    this.targetWidth, this.targetHeight);
        } else if (glless != null && glless.chainPairColorImage() != 0L) {
            System.out.println("[Celtium] CELTIUM_CHAIN_FRAME_DUMP stage=chain-pair skipped=size"
                    + " frameSize=" + this.targetWidth + "x" + this.targetHeight + " " + identity);
        }
        // Each pass's own output image, from the executor's bookkeeping (`VulkanPostChain.lastRecordedOutputs`, filled
        // as the passes are recorded and readable before `commitPasses`): the frame image alone cannot tell "pass 0
        // wrote nothing" from "the final blit never reached main", and that is the fork this line exists for. The
        // "main" entries are skipped - the frame image above is that image - and each line names its pass, program and
        // label so a chain of several passes is attributable pass by pass. Off unless
        // -Dceltium.vulkan.chainDumpPasses=true, so a normal leg pays nothing for it.
        if (Boolean.getBoolean("celtium.vulkan.chainDumpPasses") && this.postChain != null) {
            for (VulkanPostChain.RecordedOutput out : this.postChain.lastRecordedOutputs()) {
                if (out.image == 0L || "main".equals(out.label)) {
                    continue;
                }
                // The input side rides on the same line: "the pass read the right image but wrote nothing" and "the
                // pass read the wrong image" are different faults, and only the pair tells them apart. A `main` input
                // is the frame image, reported as 0 handles by the executor - the label is what names it.
                celtium$dumpOwnedImage("pass-out", out.image, base,
                        identity + " pass=" + out.passIndex + " program=" + out.program + " label=" + out.label
                                + " out=" + out.width + "x" + out.height
                                + " in=" + out.inLabel + (out.inImage == 0L ? "" : "(0x" + Long.toHexString(out.inImage)
                                        + ")") + " in=" + out.inWidth + "x" + out.inHeight,
                        out.width, out.height);
            }
        }
    }

    /**
     * Reads one owned image out and writes it, with its mean and opaque fraction on the line that names it.
     * <p>
     * Orientation, measured by proj-channel against the presented dump of the same frame: this readback's rows are
     * bottom-up relative to the presented-image dump, so comparing the two PNGs directly reports a ~98 % difference
     * that is pure orientation - {@code flipud} of one side differs on 0 of 921,600 pixels. Anyone comparing a
     * chain-time dump with a presented dump has to flip one of them first.
     */
    private void celtium$dumpOwnedImage(String stage, long image, String base, String identity, int width,
            int height) {
        if (image == 0L || width <= 0 || height <= 0) {
            return;
        }
        int[] pixels = celtium$readImageRectangle(image, 0, 0, width, height);
        if (pixels == null) {
            System.out.println("[Celtium] CELTIUM_CHAIN_FRAME_DUMP stage=" + stage + " readback=failed " + identity);
            return;
        }
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int opaque = 0;
        for (int pixel : pixels) {
            red += (pixel >> 16) & 0xFF;
            green += (pixel >> 8) & 0xFF;
            blue += pixel & 0xFF;
            if (((pixel >>> 24) & 0xFF) >= 0x80) {
                opaque++;
            }
        }
        int count = Math.max(1, pixels.length);
        java.nio.file.Path path = java.nio.file.Paths.get(stage.equals("frame") ? base
                : base.replaceFirst("\\.png$", "") + "-" + stage + ".png");
        boolean written = false;
        try {
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(width, height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, width, height, pixels, 0, width);
            if (path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }
            written = javax.imageio.ImageIO.write(out, "png", path.toFile());
        } catch (java.io.IOException | RuntimeException | LinkageError error) {
            record("chain frame dump write failed: " + describe(error));
        }
        System.out.println("[Celtium] CELTIUM_CHAIN_FRAME_DUMP stage=" + stage + " path=" + path
                + " written=" + written + " " + width + "x" + height
                + " mean=" + (red / count) + "," + (green / count) + "," + (blue / count)
                + " opaque=" + opaque + "/" + pixels.length + " " + identity);
    }

    /** {@return the image a copy's read framebuffer names, or 0 when this renderer does not own it}. */
    private long celtium$ownedReadImage(int readFbo) {
        GllessFrameTarget glless = offTargetTarget();
        // The routing decision, printed bounded: which facts the read resolved from and which image it chose. The
        // panorama copy reads GL name 0 while the cube faces were offered for a non-zero identity, and uniform white
        // is what a read of the wrong image looks like, so the inputs have to be in the log rather than inferred.
        if (this.readResolveLogs < READ_RESOLVE_LOG_LIMIT) {
            this.readResolveLogs++;
            System.out.println("[Celtium] CELTIUM_READ_RESOLVE readFbo=" + readFbo
                    + " mainIdentity=" + this.mainTargetIdentity
                    + " mainColorName=" + framebufferColorTextureName(this.mainTargetIdentity)
                    + " currentTarget=" + this.currentRenderTarget
                    + " currentIsMain=" + this.currentRenderTargetIsMain
                    + " frameImage=" + (this.frameTarget == null ? 0L : this.frameTarget.colorImage())
                    + " defaultPair=" + (glless == null ? 0L : glless.offTargetColorImage())
                    + " chainPair=" + (glless == null ? 0L : glless.chainPairColorImage()));
        }
        // The read framebuffer's GL name against the main framebuffer's own recorded GL name - not against the object
        // identity, which is a different number space and made this branch unreachable. Measured: Minecraft's main
        // framebuffer is the default one in this configuration (its bind reports GL name 0), its draws are routed to the
        // frame image, and the panorama copy reads from GL name 0 - so resolving name 0 to the default pair read an image
        // the cube faces never wrote and returned uniform white (`CELTIUM_TEX_COPY_SERVED … mean=255,255,255`).
        if (this.frameTarget != null && this.mainTargetIdentity != Long.MIN_VALUE
                && framebufferColorTextureName(this.mainTargetIdentity) == readFbo) {
            return this.frameTarget.colorImage();
        }
        // The name comparison above can only fire when the game's main framebuffer is the one whose colour texture the
        // bind census recorded, and the copy's read name is an FBO name while that census carries a texture name. The
        // routing is the fact that does not depend on either: when the main framebuffer is what is bound, its draws went
        // to the frame image, so a read from GL name 0 is a read of the frame image.
        if (readFbo == 0 && this.currentRenderTargetIsMain && this.frameTarget != null) {
            return this.frameTarget.colorImage();
        }
        if (readFbo == 0 && glless != null) {
            // GL name 0 with a target bound that is not the main framebuffer: the name is not the fact that decides
            // where the draws went - the routing is. The game's own framebuffer carries a non-zero identity and a GL
            // name the stub records as 0, and the panorama's cube faces are offered for exactly that identity, so a
            // read that resolved "name 0 and not main" to the default pair read an image the faces never wrote and
            // returned uniform white. routeLabelForBinding is the one place the routing rule lives (main -> frame
            // image, identity 0 -> default pair, every other non-main identity -> chain pair), so the read follows
            // the same rule the draws did.
            String route = GllessFrameTarget.routeLabelForBinding(this.currentRenderTarget, this.currentRenderTargetIsMain);
            if ("chain-pair".equals(route)) {
                glless.ensureOffTarget(this.targetWidth, this.targetHeight);
                return glless.chainPairColorImage();
            }
            if (this.lastDrawnOffTargetIdentity != 0L && this.lastDrawnOffTargetFrame == this.frameId) {
                // The copy reads name 0 and the default pair is bound, but this frame's non-main content was drawn
                // into this identity's pair: that image is what the copy is of. Measured on the menu, where the
                // panorama's faces go to the chain pair and the read otherwise found uniform white.
                this.readResolvedFromLastDrawnTarget++;
                glless.ensureOffTarget(this.targetWidth, this.targetHeight);
                return glless.chainPairColorImage();
            }
            glless.ensureOffTarget(this.targetWidth, this.targetHeight);
            return glless.offTargetColorImage();
        }
        return 0L;
    }

    private String celtium$refuseTextureCopy(String reason) {
        this.textureCopiesRefused++;
        this.lastTextureCopyRefusal = reason;
        return "refused-" + reason;
    }

    /**
     * {@return rectangle {@code width x height} at {@code x,y} of an owned colour image as ARGB texels}, or null.
     * <p>
     * The image is a colour attachment in {@code VK_IMAGE_LAYOUT_GENERAL} (both the off-target pairs and the frame image
     * are), and GENERAL is a legal transfer source - but the next frame attaches the image again, so the barrier pair
     * below is GENERAL to TRANSFER_SRC and back to GENERAL and the target's own layout bookkeeping stays true. The copy
     * goes through {@code VulkanContext.beginCommands}/{@code endCommands}, which waits for the queue before allocating
     * and after submitting: that is the same route the swapchain's own presented-image readback takes.
     */
    private int[] celtium$readImageRectangle(long image, int x, int y, int width, int height) {
        long buffer = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
        long memory = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long bytes = (long) width * (long) height * 4L;
            org.lwjgl.vulkan.VkBufferCreateInfo bufferInfo = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(bytes)
                    .usage(org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            if (org.lwjgl.vulkan.VK10.vkCreateBuffer(this.context.device(), bufferInfo, null, handle)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            buffer = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements =
                    org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements(this.context.device(), buffer, requirements);
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                            org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                                    | org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                            "the texture-copy readback buffer"));
            if (org.lwjgl.vulkan.VK10.vkAllocateMemory(this.context.device(), allocate, null, handle)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            memory = handle.get(0);
            org.lwjgl.vulkan.VK10.vkBindBufferMemory(this.context.device(), buffer, memory, 0);

            VkCommandBuffer commands = this.context.beginCommands();
            org.lwjgl.vulkan.VkBufferImageCopy.Buffer copy =
                    org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            copy.get(0).imageSubresource().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            copy.get(0).imageOffset().set(x, y, 0);
            copy.get(0).imageExtent().set(width, height, 1);
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer toTransfer =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            toTransfer.get(0).sType$Default()
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT);
            toTransfer.get(0).subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commands,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, toTransfer);
            org.lwjgl.vulkan.VK10.vkCmdCopyImageToBuffer(commands, image,
                    org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, copy);
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer backToGeneral =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            backToGeneral.get(0).sType$Default()
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_READ_BIT);
            backToGeneral.get(0).subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commands,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, backToGeneral);
            this.context.endCommands(commands);

            org.lwjgl.PointerBuffer mapped = stack.mallocPointer(1);
            if (org.lwjgl.vulkan.VK10.vkMapMemory(this.context.device(), memory, 0, bytes, 0, mapped)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            // The owned colour image is VK_FORMAT_R8G8B8A8_UNORM, so the bytes are R, G, B, A in that order - the ARGB
            // int the mirror's patch path expects packs R in bits 16..23.
            int[] pixels = new int[width * height];
            for (int pixel = 0; pixel < pixels.length; pixel++) {
                long at = mapped.get(0) + (long) pixel * 4L;
                int r = MemoryUtil.memGetByte(at) & 0xFF;
                int g = MemoryUtil.memGetByte(at + 1) & 0xFF;
                int b = MemoryUtil.memGetByte(at + 2) & 0xFF;
                int a = MemoryUtil.memGetByte(at + 3) & 0xFF;
                pixels[pixel] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            org.lwjgl.vulkan.VK10.vkUnmapMemory(this.context.device(), memory);
            return pixels;
        } catch (RuntimeException | LinkageError error) {
            record("texture copy readback failed: " + describe(error));
            return null;
        } finally {
            if (buffer != org.lwjgl.vulkan.VK10.VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyBuffer(this.context.device(), buffer, null);
            }
            if (memory != org.lwjgl.vulkan.VK10.VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkFreeMemory(this.context.device(), memory, null);
            }
        }
    }

    /** {@return whether a requested presented-image dump has been written}. */
    public boolean celtium$presentedImageDumpWritten() {
        return this.presentedImageDumpRequested && this.swapchain != null && this.swapchain.lastDumpWritten();
    }

    /** {@return how many frames have been presented from Vulkan so far}. */
    public int celtium$presentedFrameCount() {
        return this.framePresentedFromVulkan;
    }

    /**
     * Tells the renderer which texture unit Minecraft's light map is bound on.
     * <p>
     * The value lives in a Minecraft class, which this module deliberately does not depend on, so the mod side passes the
     * number in. It is needed because the reallocation path zeroes the light map's GL id, and a batch that arrives inside
     * that window is otherwise refused with no way to take the mirror again: with the unit known, the binding is asked of
     * GL exactly as the model draw asks for its texture.
     */
    public void celtium$setLightmapUnit(int unit) {
        this.lightmapUnit = unit;
    }

    /**
     * The light-map coordinate pair of the draw that is about to happen, captured from
     * {@code OpenGlHelper.setLightmapTextureCoords} (SRG {@code func_77475_a}) by {@code LightmapCoordsMixin}.
     * <p>
     * This is the world light level, and it exists nowhere else for the model batch: the ITEM layout's vertices carry
     * position, texture, colour and normal, the trailing normal carries the fixed-function lighting, and the light
     * level only ever travelled as a texture coordinate on unit 1. Under the GL-less window that call is a no-op, so
     * without this capture a light-map sampler would read one wrong texel for every batch - which is why the term
     * could not be added to the shader alone.
     */
    @Override
    public void setLightmapCoords(float x, float y) {
        this.lightmapCoordX = x;
        this.lightmapCoordY = y;
        this.lightmapCoordCalls++;
        this.immediateStateCalls++;
    }

    /** {@return whether a light-map coordinate has been captured since the renderer came up}. */
    private boolean lightmapCoordKnown() {
        return this.lightmapCoordCalls > 0;
    }

    /** The light-map coordinate pair the last capture stored, as {x, y}. */
    private float[] lightmapCoords() {
        return new float[] {this.lightmapCoordX, this.lightmapCoordY};
    }

    /**
     * The full-bright light-map coordinate, the pair {@code GuiContainer.java:107} sets before it draws its items:
     * {@code OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F)}. The light map's
     * texel there is its brightest, which is what "GUI items are drawn full-bright" is in a renderer whose lit layouts
     * always sample the light map.
     */
    private static final float[] FULL_BRIGHT_LIGHTMAP = {240.0F, 240.0F};

    /**
     * The light-map coordinate a group offered now must be drawn with: the captured world light where the light-map
     * texture unit was **enabled**, the full-bright pair where it was disabled.
     * <p>
     * The key is the unit's own enable state, not the pass, because that is what real OpenGL modulates by. The unit is
     * enabled in exactly three brackets - {@code RenderGlobal.renderEntities} ({@code RenderGlobal.java:1166}/{@code :1205}),
     * the first-person hand ({@code EntityRenderer.java:825}/{@code :827}, inside {@code renderWorldPass}) and the
     * block-entity section ({@code EntityRenderer.java:1434}/{@code :1403}) - and disabled everywhere else, including
     * the whole GUI stage: {@code GuiIngame.renderHotbar} never sets the coordinate while
     * {@code GuiContainer.java:107} sets {@code (240,240)} for its own items. This renderer's lit layouts always sample
     * the light map, so a draw made with the unit disabled has to be given the full-bright texel, or the coordinate the
     * world last set (the player's own light, {@code ItemRenderer.setLightmap()}) reaches the hotbar and a GUI item in a
     * dark place is drawn with the cave's light.
     * <p>
     * The state is a hook on {@code EntityRenderer.enableLightmap}/{@code disableLightmap} (SRG
     * {@code func_180436_i}/{@code func_175072_h}, resolved from the SRG table rather than guessed) and it defaults to
     * <em>enabled</em>: a hook that never fires leaves every group on the captured coordinate, which is the behaviour
     * this renderer had before the rule, so a silently dropped injection degrades to the status quo rather than to a
     * frame of full-bright entities. {@code lightmapUnitEnables}/{@code lightmapUnitDisables} in the census say whether
     * the hook ran at all.
     * <p>
     * The model path uses this too. Its GUI-stage content is the inventory's player model and any TESR item - a chest,
     * a shulker box, a banner, which {@code RenderItem.renderItem} sends through
     * {@code getTileEntityItemStackRenderer().renderByItem} ({@code RenderItem.java:144-148}) - and both are drawn with
     * the unit disabled, so both are full-bright under real GL. The inventory's player model is the reason this is
     * checked rather than assumed: {@code GuiInventory.java:154-165} only <em>disables</em> the unit at the end of its
     * draw, it never enables it, so nothing lights that model by the world either.
     */
    /**
     * {@code -Dceltium.vulkan.lightmapFullBrightGui=false} turns the rule off, for measurements: the same jar, the same
     * scene and the same tick then draw a GUI item with the world's light, which is the "before" half of the pair
     * without rebuilding a jar that differs in anything else.
     */
    private static final boolean LIGHTMAP_FULL_BRIGHT_GUI = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.lightmapFullBrightGui", "true"));

    private float[] celtium$lightmapForGroup() {
        if (!this.lightmapUnitEnabled && LIGHTMAP_FULL_BRIGHT_GUI) {
            this.lightmapFullBrightGroups++;
            return FULL_BRIGHT_LIGHTMAP;
        }
        return lightmapCoordKnown() ? lightmapCoords() : null;
    }

    /**
     * Points the game's main framebuffer at the shared images so the frame is produced in Vulkan-owned memory.
     * <p>
     * On by default since both GPUs accepted it with the same picture: AMD 0.001 mean absolute difference and 0.00 % of
     * pixels over 8 against the GL path in the same scene, Intel 0.026 and 0.02 %, both PASSED with no GL error and
     * nothing left refused for a reason of its own. Set {@code -Dceltium.vulkan.adoptGameFramebuffer=false} to go back to
     * the per-frame copy into OpenGL's framebuffer, which is what the migration still supports but no longer uses.
     */
    private static final boolean ADOPT_GAME_FRAMEBUFFER =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.adoptGameFramebuffer", "true"));

    /**
     * Prepares the frame target for whatever framebuffer is bound right now and answers whether it is ready.
     * <p>
     * The adoption runs after the game has bound its framebuffer, not before: {@code ensureFrameTarget} reads the size of
     * the bound colour texture, and before the bind that is still the previous framebuffer. Doing it in that order made a
     * resized window keep the old shared images - measured: after a mid-run resize the game bound a 1024x640 framebuffer
     * while the shared images were 1280x720, {@code adoptableFramebuffer} answered 0, and no present came from Vulkan
     * again (265 presents against 785 in the same duration without a resize).
     */
    public boolean celtium$ensureTargetForBoundFramebuffer() {
        boolean ready = ensureFrameTarget();
        if (ready) {
            // The shared images match the framebuffer that is bound now, so the replacement window is over and ordering
            // flushes are meaningful again.
            this.framebufferReplacing = false;
        }
        return ready;
    }

    /**
     * Ensures the frame target, falling back to the framebuffer object's own size when the bound attachment cannot be
     * measured.
     * <p>
     * The bind-returns hook has the framebuffer object, whose {@code framebufferWidth}/{@code framebufferHeight} stay
     * correct even when the colour attachment is a shared image that was released with a previous backend instance -
     * reading that attachment gives 0x0, and without a size no target can be built, which is the deadlock the Nether
     * dimension transfer left behind (every pass skipped on "no frame target"). The size hint is only used for the
     * game's main framebuffer, whose logical size is the same size the target must cover anyway.
     */
    public boolean celtium$ensureTargetForBoundFramebuffer(int widthHint, int heightHint) {
        boolean ready = ensureFrameTarget();
        if (!ready && widthHint > 0 && heightHint > 0) {
            ready = ensureFrameTarget(widthHint, heightHint);
        }
        if (ready) {
            this.framebufferReplacing = false;
        }
        return ready;
    }

    public int celtium$adoptableFramebuffer(int width, int height) {
        if (!ADOPT_GAME_FRAMEBUFFER || this.frameTarget == null) {
            return 0;
        }
        return this.frameTarget.adoptableFramebuffer(width, height);
    }

    public int celtium$adoptedColorTexture() {
        return this.frameTarget == null ? 0 : this.frameTarget.sharedColorTexture();
    }

    public int celtium$adoptedDepthTexture() {
        return this.frameTarget == null ? 0 : this.frameTarget.sharedDepthTexture();
    }

    public void celtium$noteFramebufferAdopted() {
        this.gameFramebufferAdopted++;
        this.gameFramebufferIsShared = true;
        if (this.frameTarget != null) {
            this.frameTarget.noteGameFramebufferAdopted();
        }
    }

    public boolean celtium$isSharedFramebuffer(int framebufferObject) {
        return ADOPT_GAME_FRAMEBUFFER && this.frameTarget != null
                && this.frameTarget.isSharedFramebuffer(framebufferObject);
    }

    /**
     * Records the first GL error seen at a named point after the swap, and where it was seen.
     * <p>
     * Reading a GL error clears it, so only the first probe that finds one can speak: it names the window the error was
     * produced in. Probes after it will read zero whatever happens, which is why the point is recorded with the code.
     */
    public void celtium$noteGlErrorAt(String where, int error) {
        if (error != 0 && this.firstGlErrorWhere == null) {
            this.firstGlErrorWhere = where;
            this.firstGlErrorCode = error;
        }
    }

    /** {@return whether the native model draw experiment is enabled}. */
    public static boolean modelDrawEnabled() {
        return MODEL_DRAW;
    }

    /** Draws at most this many quads per list, for isolating a single quad (0 = all). */
    private static final int MODEL_QUAD_LIMIT = Integer.getInteger("celtium.vulkan.modelQuadLimit", 0).intValue();
    /** Draws only this display list, for isolating one list (0 = all). */
    private static final int MODEL_ONLY_LIST = Integer.getInteger("celtium.vulkan.modelDrawOnlyList", 0).intValue();

    /**
     * Whether captured display lists are drawn natively.
     * <p>
     * On by default since the staged acceptance: the entity scene (frozen creeper/zombie/spider/enderman in the view
     * cone, captureEntities keeping the live frame) measures the native draw at 0.35 % on AMD and 0.16 % on Intel, at
     * or below the GL replay baseline the same scene takes (1.15 % on AMD) - the texture-ownership work that fixed the
     * light map's delivery is what made the models light correctly. A list whose capture or build fails returns false
     * and the GL replay is not cancelled, so the fallback is the replay that used to run anyway.
     * {@code -Dceltium.vulkan.modelDraw=false} restores the previous behaviour.
     */
    private static final boolean MODEL_DRAW = Boolean.parseBoolean(
            System.getProperty("celtium.vulkan.modelDraw", "true"));
    /** Forces the fence wait to report a timeout, so the post-submit failure path can be exercised (lab switch). */
    private static final boolean FENCE_WAIT_FAULT = Boolean.getBoolean("celtium.vulkan.failFenceWait");
    /**
     * Makes the fence wait report {@code VK_ERROR_DEVICE_LOST} instead of its real result - the case of a submission
     * that was queued and then lost with the device, which the pre-submit fault cannot produce. Lab switch.
     */
    private static final boolean DEVICE_LOST_FAULT = Boolean.getBoolean("celtium.vulkan.failDeviceLost");

    /**
     * Runs a descriptor-set request through the fault switch, so the refusal path can be exercised on a real GPU.
     * <p>
     * The value the pipelines return is what the callers already test against zero, so an injected failure is
     * indistinguishable from a driver refusal - which is exactly the case this is meant to cover.
     */
    private static long celtium$descriptorSet(long descriptorSet, String site) {
        if (VulkanDescriptorFault.shouldFail(site)) {
            return 0L;
        }
        return descriptorSet;
    }

    /**
     * The one texture registry.
     * <p>
     * It was created inline at five call sites, which is five chances for two registries to exist and for the image the
     * backend samples to differ from the image the announcements patch - the split this change removes. Creation stays lazy
     * because the first texture registration happens before any world draw.
     */
    private VulkanTextureRegistry celtium$registry() {
        if (this.textureRegistry == null) {
            this.textureRegistry = new VulkanTextureRegistry(this.context);
        }
        return this.textureRegistry;
    }

    private boolean ensureFrameTarget() {
        if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            // The window's own framebuffer size is what the frame target must match: it is the drawable the swapchain
            // presents, and it does not move when the game binds one of its own framebuffers. Sizing from the GL viewport
            // instead adopted whatever framebuffer was bound last - the splash's own FBO during startup - so the target
            // (and with it the pairs and every name published over them) was created at the wrong size and then rebuilt
            // when the main framebuffer arrived, which threw the splash's pixels away and left the composite sampling an
            // empty image: the white loading screen. The main framebuffer's reported size is the second choice, the
            // viewport the last.
            int[] window = windowFramebufferSize();
            if (window[0] > 0 && window[1] > 0) {
                return ensureFrameTarget(window[0], window[1]);
            }
            int[] drawable = currentViewport();
            int width = this.mainFramebufferWidth > 0 ? this.mainFramebufferWidth : drawable[2];
            int height = this.mainFramebufferHeight > 0 ? this.mainFramebufferHeight : drawable[3];
            return ensureFrameTarget(width, height);
        }
        int fbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        if (fbo == 0) return false;
        int type = org.lwjgl.opengl.GL45C.glGetNamedFramebufferAttachmentParameteri(fbo,
                org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type != org.lwjgl.opengl.GL11.GL_TEXTURE) {
            if (this.nonTextureAttachmentLogged < 6) {
                this.nonTextureAttachmentLogged++;
                // First seen as a steady "no frame target" refusal for format-2 quads; the reason this gate fails has to
                // name the framebuffer and its attachment, which only the dedicated line surfaces.
                System.out.println("[Celtium] CELTIUM_TARGET_REFUSAL fbo=" + fbo + " attachmentType=" + type
                        + " target=" + this.targetWidth + "x" + this.targetHeight);
            }
            return false;
        }
        int texture = org.lwjgl.opengl.GL45C.glGetNamedFramebufferAttachmentParameteri(fbo,
                org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        int w = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(texture, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
        int h = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(texture, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
        if (w != this.targetWidth || h != this.targetHeight) {
            // A non-shared framebuffer - a mod's or the post-chain's - reads a different size than the game target.
            if (this.foreignFboLogged < 8) {
                this.foreignFboLogged++;
                System.out.println("[Celtium] CELTIUM_TARGET_FOREIGN fbo=" + fbo + " attachment=" + texture
                        + " " + w + "x" + h + " target=" + this.targetWidth + "x" + this.targetHeight);
            }
        }
        if (w <= 0 || h <= 0) {
            if (this.nonTextureAttachmentLogged < 6) {
                this.nonTextureAttachmentLogged++;
                System.out.println("[Celtium] CELTIUM_TARGET_REFUSAL fbo=" + fbo + " attachment=" + texture
                        + " size " + w + "x" + h + " (dead or incomplete)");
            }
            return false;
        }
        return ensureFrameTarget(w, h);
    }

    /**
     * Ensures the frame target exists for the given size, without asking the bound framebuffer how big it is.
     * <p>
     * The callers that read the attachment are left on {@link #ensureFrameTarget()}; this overload is for the adoption
     * path, where the framebuffer object's own width and height are the authoritative size. The bound colour
     * attachment can be a shared image that died together with an earlier backend instance - a world change recreates
     * the renderer and this backend but not the framebuffer - which reads back as 0x0 and would otherwise leave the
     * target, and with it every draw, unrecoverable.
     */
    private boolean ensureFrameTarget(int width, int height) {
        if (this.frameTarget != null && this.frameTarget.matches(width, height)) {
            return true;
        }
        // The GPU must be finished with the old target before any of it is released: a resize recreates the game's
        // framebuffer, and the validation layer caught the first attempt doing exactly this -
        // "vkDestroyFramebuffer(): can't be called on VkFramebuffer ... that is currently in use by VkCommandBuffer".
        // The same rule as delete() and region eviction, applied to the path a resize takes.
        if (!settleGpu("frame target recreation")) {
            // Fail closed: without a settled GPU the old target cannot be released, so no new one is built either.
            return false;
        }
        destroyFramebuffer();
        if (this.frameTarget != null) {
            this.frameTarget.delete();
            this.frameTarget = null;
            // Groups recorded against the old target may still be queued; the generation is what lets the draw refuse them
            // instead of binding a render pass that no longer exists. Both the recreate path and the teardown path bump it.
            this.targetGeneration++;
        }
        try {
            if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
                // With the stub answering GL there is no GL side to share images with: the frame lands in an
                // owned target whose semaphores are signalled and consumed by Vulkan submissions alone.
                this.frameTarget = GllessFrameTarget.create(this.context, width, height);
            } else {
                this.frameTarget = VulkanFrameTarget.create(this.context.instance(), this.context.physicalDevice(),
                        this.context.device(), this.context.queue(), this.context.queueFamilyIndex(), width, height);
            }
            if (this.gameFramebufferIsShared) {
                this.frameTarget.noteGameFramebufferAdopted();
            }
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

    /**
     * {@return the GLFW window's framebuffer size}, or {@code {0, 0}} when the window is unknown or has no drawable.
     * <p>
     * The window handle is the current context under a real GL configuration and comes from {@code Display} under the
     * GL-less window, where there is no context to ask (reflective, because celtium-vulkan does not depend on the
     * lwjglxx shim). This is the size the frame target must have: it is the drawable the swapchain presents.
     */
    private static int[] windowFramebufferSize() {
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            try {
                Class<?> display = Class.forName("org.lwjgl.opengl.Display");
                window = ((Long) display.getMethod("getWindow").invoke(null)).longValue();
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // No window handle from here; the caller falls back to the framebuffer or the viewport.
            }
        }
        if (window == 0L) {
            return new int[] {0, 0};
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(window, width, height);
            return new int[] {width.get(0), height.get(0)};
        }
    }

    private void refreshTextures() {
        int atlasId = boundTextureId(0);
        int lightmapId = boundTextureId(1);
        // CELTIUM_TEXTURE_DIAG: off by default. The atlas is the texture whose mip state matters, and this is the
        // only method that ever mirrors it, so the question is asked here - with the binding read rather than set, so
        // nothing the game is drawing can be disturbed.
        if (TEXTURE_DIAGNOSTICS && atlasId != 0 && this.diagnosedTextures.add(Integer.valueOf(atlasId))) {
            int levels = 0;
            while (levels < 16 && org.lwjgl.opengl.GL11.glGetTexLevelParameteri(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, levels, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH) > 0) {
                levels++;
            }
            System.out.println("[Celtium] CELTIUM_TEXTURE_DIAG atlas=" + atlasId + " levels=" + levels
                    + " maxLevel=" + org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x813D)
                    + " minLod=" + org.lwjgl.opengl.GL11.glGetTexParameterf(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x813A)
                    + " maxLod=" + org.lwjgl.opengl.GL11.glGetTexParameterf(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x813B)
                    + " minFilter=" + org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x2801)
                    + " magFilter=" + org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x2800)
                    + " atlasSize=" + org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH)
                    + "x" + org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT));
        }


        if (atlasId != 0 && (this.atlasTexture == null || atlasId != this.atlasGlId || this.atlasRefreshCountdown <= 0)) {
            try {
                if (this.atlasTexture == null) {
                    int[] size = textureSize(atlasId);
                    if (size[0] <= 0 || size[1] <= 0) {
                        // No storage to mirror yet. Creating an image of an unknown size is what produced a 1x1
                        // mirror that the upload then had to replace, so the frame is skipped and the question is
                        // asked again when the atlas exists.
                        this.atlasRefreshCountdown = 0;
                        return;
                    }
                    // The registry owns the image; the backend samples that same object, so an announced patch and the
                    // descriptor look at one image instead of two.
                    this.atlasTexture = celtium$registry().imageFor(atlasId, size[0], size[1], true);
                }
                this.celtium$establishAtlasImage(atlasId, this.atlasTexture, "atlas");
                this.atlasSourceGlId = atlasId;
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
                    this.lightmapTexture = celtium$registry().imageFor(lightmapId, size[0], size[1], false);
                }
                if (this.lightmapIdentityLogs < 4) {
                    this.lightmapIdentityLogs++;
                    org.soutalab.celtium.vulkan.VulkanGlTexture registryEntry =
                            celtium$registry().get(lightmapId);
                    record("lightmap identity: glId=" + lightmapId
                            + " boundView=" + Long.toHexString(this.lightmapTexture.view())
                            + " boundFull=" + this.lightmapTexture.hasFullContent()
                            + " registryView=" + (registryEntry == null ? "none"
                                    : Long.toHexString(registryEntry.view()))
                            + " registryFull=" + (registryEntry == null ? "none"
                                    : Boolean.toString(registryEntry.hasFullContent()))
                            + " same=" + (registryEntry == this.lightmapTexture));
                }
                // Back on the announcement path, which is the light map's real source: the black it produced was the
                // announced array's shape (one int[] per row where the consumer reads one int[] for the whole of level
                // 0), not this call. With the producer fixed, an announcement-only run measures the same band as the
                // readback did - 102.4/63.9/58.5 - so the readback detour is gone and the image has one owner again.
                this.celtium$establishAtlasImage(lightmapId, this.lightmapTexture, "lightmap");
                this.lightmapSourceGlId = lightmapId;
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

    /**
     * The size of a GL texture's level 0, asked through the binding rather than through direct state access.
     * <p>
     * {@code glGetTextureLevelParameteri} needs GL 4.5 or {@code ARB_direct_state_access}, and when the context does
     * not have it the query answers 0 - which the {@code Math.max(1, ...)} here turned into a 1x1 image. The mirror
     * was then created at 1x1 and the upload had to replace it, so every descriptor built in between pointed at an
     * image that no longer existed: the atlas sampled as black while the registry's current image held the real
     * pixels. The bound form works wherever a texture can be bound at all, and under the GL-less stub it answers from
     * the extents the stub recorded. The binding is restored, so the game sees no state change.
     */
    private static int[] textureSize(int texture) {
        if (texture == 0) {
            return new int[] { 0, 0 };
        }
        int previous = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
        try {
            if (previous != texture) {
                org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, texture);
            }
            int width = org.lwjgl.opengl.GL11C.glGetTexLevelParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL11C.GL_TEXTURE_WIDTH);
            int height = org.lwjgl.opengl.GL11C.glGetTexLevelParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL11C.GL_TEXTURE_HEIGHT);
            return new int[] { width, height };
        } finally {
            if (previous != texture) {
                org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, previous);
            }
        }
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
            this.commandState = new VulkanCommandBufferState(this.commandBuffer.address());

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

            if (REFERENCE_RENDER) {
                // A reference render: the mod loads, the bootstrap completes and the fixture can query the device, but the
                // backend never registers - so no pass, no capture and no mixin takes anything, and OpenGL draws the whole
                // frame. Returning here instead of skipping only the registration also skipped the command registration
                // and made the fixture fail with "Vulkan bootstrap was expected but the command is missing".
                record("reference render: the Vulkan backend is not installed, so the frame is OpenGL");
            } else {
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.install(this);
            }

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
        if (this.commandState.recording()) throw new IllegalStateException("nested begin on backend command buffer");
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
                this.noteStalled();
                this.stalledStops++;
                record("gpu stalled: the previous submission did not complete; drawing is refused from here");
                throw new IllegalStateException("pending backend command buffer did not complete");
            }
            this.submissionPending = false;
            // The device is genuinely idle here - the wait above succeeded - so this is the one place where retired
            // textures can be destroyed without racing a command buffer that still names them (audit S12).
            // The device is idle, so any off-target copy recorded for the previous frame has certainly completed: this is
            // where its pixels go into the game's texture.
            int drained = VulkanRetireQueue.flush();
            if (drained > 0) {
                this.retiredFlushes += drained;
            }
            // The previous frame's timestamps are complete now, which is the only moment they can be read reliably.
            updateTimings();
        }
        check(vkResetCommandBuffer(this.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT), "vkResetCommandBuffer");
        this.commandState.reset();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(this.commandBuffer, begin), "vkBeginCommandBuffer");
            this.commandState.begin();
            // N05(2): bind every foreign-image transition recorded into this buffer to this recording. It becomes
            // fact only when the submission that carries it is accepted; opening a recording on a handle that still
            // has an uncommitted one abandons that one first, which is exactly the reset/abandon case.
            VulkanGlTexture.beginLayoutRecording(this.commandBuffer);
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
        // 208, not 128: the pipeline's push range now covers the fog block the fragment stage reads at 128..207, and a
        // push shorter than the layout's range would leave those bytes undefined for this draw.
        ByteBuffer push = MemoryUtil.memAlloc(208);
        try {
            // Identity matrices: with z = 0 the vertex is already inside Vulkan's 0..1 depth range, so no clip-depth
            // remap is wanted here.
            for (int i = 0; i < 16; i++) {
                push.putFloat(i * 4, (i % 5 == 0) ? 1.0f : 0.0f);
            }
            // The model view's three affine columns, which is what the shader reads at 64/80/96, and a zero alpha
            // reference at 112 so the isolation draw is unaffected by whatever the last state left there.
            push.putFloat(64, 1.0f);
            push.putFloat(80, 1.0f);
            push.putFloat(96, 1.0f);
            push.putFloat(112, 0.0f);
            // Fog off for the isolation quad: its machinery is what is under test, not the world's fog. The selector is
            // the only field the shaders branch on, and the rest of the block is left at this buffer's own zeros.
            push.putFloat(156, 0.0f);
            this.immediatePipeline.draw(this.commandBuffer, this.isolationMesh, push);
            this.immediateIsolationDraws++;
        } finally {
            MemoryUtil.memFree(push);
        }
    }

    private void drawPendingImmediate() {
        drawPendingImmediate(null);
    }

    /**
     * Draws the pending groups, optionally only those belonging to one render target.
     * <p>
     * The filter is what makes an off-target pass possible: a Vulkan render pass cannot be begun inside another, so those
     * groups are drawn in their own pass and copied back, and they must not also be drawn in the frame's pass.
     */
    private void drawPendingImmediate(Long onlyTarget) {
        long target = onlyTarget == null ? this.mainTargetIdentity : onlyTarget.longValue();
        try { this.commandState.requireDraw(target); }
        catch (IllegalStateException error) { this.invalidImmediateRecords++; throw error; }
        closeCurrentImmediateGroup();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 216, not 208: the lit ITEM pipeline's block is five lighting vec4 (128..207) *plus* the light-map
            // coordinate pair at 208..215, and every other pipeline clamps to its own declared range when it pushes.
            // The buffer was 208 while the light-coordinate write below targets 208 and 212, so drawing any group that
            // carried light-map coordinates - the model and item batches, exactly - threw IndexOutOfBoundsException out
            // of the window flush; the catch then cleared the whole immediate queue, which is the measured reason
            // id4/id9 were accepted and never drawn (windowFlushFailures=64, windowFlushDroppedBatches=9680,
            // drawnByFormat id4=0 id9=0).
            ByteBuffer push = stack.calloc(216);
            Iterator<ImmediateGroup> iterator = this.immediateGroups.iterator();
            while (iterator.hasNext()) {
                ImmediateGroup group = iterator.next();
                if (group.target != target) {
                    // Kept for its own pass, and now counted: this is the site the counter's name describes, and
                    // without an increment here a "kept" group and a "dropped" one both read as zero. It counts
                    // keep-events, like the pass-boundary counter counts boundary events, so a group held across
                    // three flushes raises it three times.
                    this.immediateGroupsKeptForOtherTargets++;
                    continue;
                }
                if (group.stamp != null) {
                    GroupStamp stamp = group.stamp;
                    if (stamp.targetGeneration != this.targetGeneration) {
                        // Recorded against a frame target that has since been destroyed: its pipeline and render pass are
                        // gone, so drawing it would bind the wrong thing. Retired without drawing and counted; the mesh is
                        // freed by the next completion drain, which is safe because this group was never submitted.
                        iterator.remove();
                        this.retiredImmediateGroups.add(group);
                        this.groupsStaleTargetGeneration++;
                        this.immediateGroupsDiscarded++;
                        continue;
                    }
                    if (stamp.worldEpoch != this.worldEpoch) {
                        // Accepted under a world that has since been torn down. A captured model group's mesh is owned by
                        // the model mesh it was lent from, and the world teardown has already released those, so drawing
                        // this would read freed geometry. The target-generation stamp cannot see that: a frame target
                        // survives a world change. Retired without drawing and counted; the mesh is freed by the next
                        // completion drain, which is safe because the group was never submitted.
                        iterator.remove();
                        this.retiredImmediateGroups.add(group);
                        this.groupsStaleWorldEpoch++;
                        this.immediateGroupsDiscarded++;
                        continue;
                    }
                }
                VulkanDynamicMesh mesh = group.mesh;
                if (mesh.isEmpty()) throw new IllegalStateException("accepted empty mesh");
                PreparedImmediate prepared = group.prepared;
                // A group can outlive the texture it was prepared with: the wrapper is retired when the renderer replaces
                // the image (measured: glTexture=22 unpublished at frame 0, moments after the group was enqueued), and the
                // descriptor then names a destroyed view. Drawing it is not a missing batch but the
                // `u_Texture ... imageView 0x0` -> VK_ERROR_DEVICE_LOST chain, so the sampled wrapper is re-resolved here
                // and the descriptor rebuilt when its key (view + generation) has moved.
                if (prepared.textureKey != 0L && group.texture != 0) {
                    VulkanGlTexture currentTexture = celtium$registry().get(group.texture);
                    if (currentTexture != null && currentTexture.key() != prepared.textureKey) {
                        // The rebuild has to know which family the group belongs to. The textured family needs only its
                        // own texture; a lit ITEM group - the model path's batch - declares a second binding for the
                        // light map, so rebuilding it with the one-texture call would produce a set the pipeline cannot
                        // sample and a descriptor-validity error. That is why the model groups were refused here while
                        // every other family was rebuilt: measured as modelDraws=1485 with drawnByFormat[4]=0, every
                        // entity and held-item group retired at this line in the frame it was enqueued. The lit variant
                        // is rebuilt with the same light map the prepare used.
                        long rebuilt;
                        if (prepared.pipeline instanceof VulkanImmediateTexturePipeline texturePipeline) {
                            rebuilt = texturePipeline.descriptorSetFor(currentTexture);
                        } else if (prepared.pipeline instanceof VulkanImmediateItemPipeline itemPipeline) {
                            rebuilt = itemPipeline.lit()
                                    ? itemPipeline.descriptorSetFor(currentTexture, this.lightmapTexture)
                                    : itemPipeline.descriptorSetFor(currentTexture);
                        } else {
                            rebuilt = 0L;
                        }
                        if (rebuilt != 0L) {
                            prepared = new PreparedImmediate(prepared.pipeline, rebuilt, currentTexture.key());
                            this.staleGroupDescriptorsRebuilt++;
                        } else {
                            // No rebuild available (a lit item group needs its light map as well): refusing by name is
                            // still better than binding a descriptor whose view has been destroyed.
                            this.staleGroupRefusals++;
                            if (this.staleGroupRefusals <= 4) {
                                record("group for GL texture " + group.texture + " was prepared with key 0x"
                                        + Long.toHexString(prepared.textureKey) + " but the wrapper is now key 0x"
                                        + Long.toHexString(currentTexture.key())
                                        + "; the group is refused rather than drawn with a destroyed view");
                            }
                            iterator.remove();
                            this.retiredImmediateGroups.add(group);
                            this.immediateGroupsDiscarded++;
                            continue;
                        }
                    }
                }
                pushCombinedMatrixInto(group.projection, group.modelView, push, 0);
                // The push buffer is shared by every group in this flush, so the texture-matrix slots must be
                // rewritten per group rather than only when a matrix is present: a stale affine would otherwise leak
                // into the next group.
                float[] texMat = group.texMatrix;
                if (texMat != null) {
                    this.texMatrixGroups++;
                    push.putFloat(64, texMat[0]);
                    push.putFloat(68, texMat[1]);
                    push.putFloat(72, texMat[2]);
                    push.putFloat(76, texMat[3]);
                    push.putFloat(80, texMat[4]);
                    push.putFloat(84, texMat[5]);
                    push.putFloat(88, 1.0f);
                } else {
                    push.putFloat(88, 0.0f);
                }
                int[] state = group.state;
                if (group.combineColor != null) {
                    // The fixed pipeline's texture-env interpolate, encoded into the block's spare values: a negative
                    // alpha reference marks the combine (alpha tests use non-negative references only) and the RGB
                    // slots carry the constant colour; the fragment shader mixes instead of discarding.
                    push.putFloat(112, -group.combineColor[3]);
                    push.putFloat(116, group.combineColor[0]);
                    push.putFloat(120, group.combineColor[1]);
                    push.putFloat(124, group.combineColor[2]);
                } else {
                    push.putFloat(112, Float.intBitsToFloat(state[6]));
                    float[] color = group.color;
                    push.putFloat(116, color == null ? 1 : color[0]);
                    push.putFloat(120, color == null ? 1 : color[1]);
                    push.putFloat(124, color == null ? 1 : color[2]);
                    // The current colour's alpha, in the block's spare vec4 at 96. Written for every group rather
                    // than only the colourless ones, for the same reason as the texture matrix above: the push buffer
                    // is shared by the whole flush, so a stale value would leak into the next group. Nothing reads
                    // 96 yet - the two colourless vertex shaders have to be changed to use it - but the value has to
                    // be there for that to be a one-line change on their side.
                    push.putFloat(96, color == null || color.length < 4 ? 1.0f : color[3]);
                }
                // BH-03: the alpha comparison function, in the spare slot at offset 100. The GL enum travels as a
                // float, and 0 means "the test is disabled" - no GL enum is 0, so a reference of 0 stays
                // distinguishable from a disabled test, which the shader's old "alphaRef > 0.0" guard conflated (it
                // implemented GL_GREATER alone).
                //
                // Read from the group's own captured state (slot 14), not from the live mirror: the push buffer is
                // shared by the whole flush, and a comparison the game changed after this batch was accepted must not
                // reach it - the same rule the matrices, the alpha reference and the colour above already follow.
                // Written outside the combine branch as well, because the buffer is shared: a group that follows a
                // combine group must not inherit the combine's compare value even though the combine's own branch
                // never reads it (a negative alpha reference selects the mix path before the comparison).
                push.putFloat(100, state.length > 14 ? state[14] : 0);
                // The lit extension: model-space directions, ambient and the per-light diffuses. Written for every
                // group that carries them rather than guarded per pipeline - only the lit layout reads past 128, and
                // a lit group always arrives with its params attached.
                float[] light = group.lightParams;
                if (light != null) {
                    for (int i = 0; i < 20; ++i) {
                        push.putFloat(128 + i * 4, light[i]);
                    }
                    // The world light level, at 208 in the lit layout: the pair the game set for this entity, which the
                    // lit vertex shader samples against the light map. Only a lit group's block is read past 207, so
                    // this write is guarded by the same `light != null` that makes the block lit.
                    float[] coords = group.lightmapCoords;
                    if (coords != null) {
                        push.putFloat(208, coords[0]);
                        push.putFloat(212, coords[1]);
                        this.lightmapCoordGroups++;
                    }
                }
                // The fog block, which lives in the same 128..207 region as the lighting payload: a lit group's block
                // is the lighting and must not be overwritten, so the fog is written only for groups that do not carry
                // one. Everything that can arrive here with fog on is camera-space or object-space world geometry;
                // the selector is 0 for a group offered while the game had fog off, so the shaders' fog branch is a
                // no-op there. The ITEM pipeline's lit layout is the one family whose shaders do not declare this
                // block at all (see the report: lit items and entities are the unfogged part of the immediate path).
                if (light == null) {
                    celtium$writeFogPush(group, push);
                }
                // The batch's own viewport, applied as the drawable sub-rect GL would have used. Without this the
                // pipeline's baked-in viewport (set once per pass by setViewportAndScissor) would draw a 256x256 GUI
                // batch across the whole target - wrong pixels, which is why the batch used to be refused instead.
                celtium$setGroupViewport(stack, group);
                // BH-11: GL's polygon offset, applied as this group's depth bias. It is command state - one
                // pipeline serves every offset the game sets, which is why the pipelines declare
                // VK_DYNAMIC_STATE_DEPTH_BIAS and depthBiasEnable. The two names are swapped between the APIs: GL's
                // units is Vulkan's constant factor and GL's factor is Vulkan's slope factor, with the sign carrying
                // across unchanged (smaller is nearer in both). Block entities and the break overlay are the callers:
                // with the offset dropped, the TESR and the block face it sits on z-fight. Issued only when the game
                // has actually called glPolygonOffset, so a scene that never uses it pays one branch.
                boolean offsetEnabled = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                        && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetFillEnabled()
                        && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetCalled();
                float constantFactor = offsetEnabled
                        ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetUnits() : 0.0F;
                float slopeFactor = offsetEnabled
                        ? org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetFactor() : 0.0F;
                // Issued for every group, not only when the game has called glPolygonOffset: the pipelines declare
                // depthBiasEnable statically, so a draw whose command buffer never saw vkCmdSetDepthBias is a
                // validation error (VUID-07834, measured) - and under GL-less an unwritten dynamic state is also
                // whatever the last draw left. Zeros are the state GL has when the offset is off.
                org.lwjgl.vulkan.VK10.vkCmdSetDepthBias(this.commandBuffer, constantFactor, 0.0F, slopeFactor);
                if (offsetEnabled) {
                    this.immediateDepthBiasedGroups++;
                }
                Object pipeline = prepared.pipeline;
                if (pipeline instanceof VulkanImmediateTexturePipeline value) value.draw(this.commandBuffer, mesh, push, prepared.descriptor);
                else if (pipeline instanceof VulkanImmediateItemPipeline value) value.draw(this.commandBuffer, mesh, push, prepared.descriptor);
                else if (pipeline instanceof VulkanImmediateBlockPipeline value) value.draw(this.commandBuffer, mesh, push, prepared.descriptor);
                else if (pipeline instanceof VulkanImmediateParticlePipeline value) value.draw(this.commandBuffer, mesh, push, prepared.descriptor);
                else ((VulkanImmediatePipeline) pipeline).draw(this.commandBuffer, mesh, push);
                int batches = group.batches;
                this.immediateRecordedBatches += batches;
                if (target != this.mainTargetIdentity) this.offTargetRecordedBatches += batches;
                this.immediateGroupsDrawn++;
                if (group.target != this.mainTargetIdentity) {
                    // offTargetDrawn was declared and printed but never incremented - a counter that can only read
                    // zero, which is the inverse of "a missing counter is not a missing draw": here it invited
                    // "nothing off-target was drawn" from a number that could never move. It counts the groups whose
                    // target is not the main identity as they are drawn, which is what its name says.
                    this.offTargetDrawn++;
                }
                this.immediateFlushes++;
                this.frameDrawn++;
                if (group.formatId >= 0 && group.formatId < this.drawnByFormat.length) {
                    // Batches, not groups: acceptedByFormat counts batches, so counting groups here would compare a
                    // group against a batch and read as "38,123 accepted, 1,358 drawn" for formats whose batches merge
                    // by the hundred. The unit is stated once and the two rows are subtracted by whoever reads them.
                    this.drawnByFormat[group.formatId] += batches;
                }
                if (ENQ_ALL_GROUPS && (ENQ_FORMAT < 0 || ENQ_FORMAT == group.formatId)
                        && this.immediateDrawSiteLogged < DRAW_SITE_LIMIT) {
                    // Where this group was drawn, not only that it was: the three sites test against different depth
                    // contents, so "drawn" and "in the picture" are different questions and this is the line that
                    // tells them apart. Gated by the existing enq switches and bounded, so a normal run is unchanged.
                    this.immediateDrawSiteLogged++;
                    System.out.println("[Celtium] CELTIUM_DRAW_SITE #" + this.immediateDrawSiteLogged
                            + " frame=" + this.frameId + " fmt=" + group.formatId
                            + " site=" + this.immediateDrawSite
                            + " target=" + group.target + " onMain=" + (group.target == this.mainTargetIdentity)
                            + " passOpen=" + (this.commandState != null && this.commandState.inRenderPass())
                            + " vertices=" + mesh.vertexCount() + " indices=" + mesh.indexCount()
                            + " batches=" + batches + " pending=" + this.immediateGroups.size()
                            + " windows=" + this.immediateWindows + " windowsThisFrame=" + this.immediateWindowsThisFrame
                            + " terrainRanThisFrame=" + (this.lastTerrainPassFrame == this.frameId)
                            + " groupFrame=" + (group.stamp == null ? -1L : group.stamp.frameId)
                            + " groupGeneration=" + (group.stamp == null ? -1 : group.stamp.targetGeneration)
                            + " borrowed=" + (group.stamp != null && group.stamp.meshBorrowed));
                }
                iterator.remove();
                this.pendingImmediateIndices -= mesh.indexCount();
                this.recordedImmediateGroups.add(group); // until submit success; not freed here
            }
            // Back to the pass's own rectangle: the groups above may have narrowed it, and whatever is drawn after this
            // flush - terrain, another pass, the final blit - expects the target's viewport, not the last GUI batch's.
            if (this.groupViewportNarrowed) {
                this.groupViewportNarrowed = false;
                setViewportAndScissor(stack);
            }
        }
    }

    /** Set when a group in the current flush asked for a sub-rectangle, so the pass viewport is restored once. */
    private boolean groupViewportNarrowed;

    /**
     * Applies one group's GL viewport as a Vulkan viewport and scissor.
     * <p>
     * GL's viewport origin is the bottom-left of the drawable and Vulkan's is the top-left, and this renderer keeps the
     * Vulkan convention unchanged (positive height, see {@code vulkanProjection()}), so the y is flipped here: a GL
     * rectangle (x, y_bottom, w, h) becomes the Vulkan rectangle (x, targetHeight - y_bottom - h, w, h).
     * <p>
     * A group whose rectangle is the whole target does nothing - the pass already set exactly that - and the flag is
     * set only when a narrow rectangle is applied, so the common case costs nothing and the restore happens once per
     * flush rather than per group.
     */
    private void celtium$setGroupViewport(MemoryStack stack, ImmediateGroup group) {
        int[] viewport = group.viewportValues;
        if (viewport == null || viewport.length < 4) {
            return;
        }
        // Clipped to the target: GL clips a viewport wider or taller than the drawable at rasterization, and the GUI
        // stage really does ask for 1281 columns on a 1280 target. The intersection is what Vulkan is given.
        int x0 = Math.max(0, viewport[0]);
        int yBottom0 = Math.max(0, viewport[1]);
        int x1 = Math.min(this.targetWidth, viewport[0] + viewport[2]);
        int yBottom1 = Math.min(this.targetHeight, viewport[1] + viewport[3]);
        // BH-13: the viewport is dynamic command state, so it is set for *every* group - and this is exactly where
        // the old code went wrong twice. It returned early when the group's rectangle was the full target, on the
        // assumption that "the pass already set exactly this rectangle": true at the pass's first group, false after
        // any narrower group has run, which left the narrow rectangle in place for everything that followed. It also
        // returned without setting anything when the rectangle was entirely off the drawable, so that group inherited
        // whichever rectangle the previous one had set instead of drawing nothing. The rules Vulkan actually needs:
        // a full rectangle is re-issued (it is one command), and an empty intersection becomes a zero-extent viewport,
        // which is legal and rasterizes nothing.
        boolean empty = x1 <= x0 || yBottom1 <= yBottom0;
        int width = empty ? 0 : x1 - x0;
        int height = empty ? 0 : yBottom1 - yBottom0;
        int y = empty ? 0 : this.targetHeight - yBottom1;
        if (empty) {
            // Nothing of the rectangle is on the drawable; GL would draw nothing either. Counted so an entirely
            // off-target viewport is visible rather than silently empty.
            this.immediateOffTargetViewports++;
        }
        org.lwjgl.vulkan.VkViewport.Buffer vkViewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
        vkViewport.get(0).x((float) x0).y((float) y)
                .width((float) width).height((float) height)
                .minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(this.commandBuffer, 0, vkViewport);
        this.groupViewportSets++;
        // BH-09: the game's own clip rectangle. Celeritas' UI calls GL11.glScissor directly (VintageDrawContext),
        // which the stub now records instead of dropping, and this is the one place a group's rectangle becomes the
        // Vulkan scissor - so the clip is applied here as the intersection of the target rectangle and the game's box,
        // with GL's bottom-left origin flipped to Vulkan's top-left. An empty intersection becomes a zero extent,
        // which is what clips everything. The scissor is command state, not pipeline state: it belongs in this
        // rectangle and deliberately not in the group key.
        int scissorX = x0;
        int scissorY = y;
        int scissorWidth = width;
        int scissorHeight = height;
        // The group's own captured clip, not the live record: reading it live applied whatever rectangle the game
        // had set last to every batch in the group, so a group spanning a scissor change was clipped by the wrong box
        // (OPT-10's "state must reach the group", the same rule the fog and the light params follow).
        int[] clip = group.scissorBox;
        boolean clipEnabled = group.scissorEnabled;
        if (!GROUP_SCISSOR) {
            // The measurement control: the pre-fix shape read the live record here, so a group that spanned a
            // rectangle change was clipped by whatever the game had set by the time the group was flushed.
            clip = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorBox();
            clipEnabled = clip != null && org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorTest();
        }
        if (clip != null && clipEnabled) {
            // The group carried a clip of its own: counted separately from the intersection below, so a run says
            // whether the rectangle reached the group at all before it says how the intersection came out.
            this.scissorCapturedGroups++;
            int clipX0 = clip[0];
            int clipY0 = this.targetHeight - (clip[1] + clip[3]);
            int clipX1 = clipX0 + clip[2];
            int clipY1 = clipY0 + clip[3];
            int intersectX0 = Math.max(scissorX, clipX0);
            int intersectY0 = Math.max(scissorY, clipY0);
            int intersectX1 = Math.min(scissorX + scissorWidth, clipX1);
            int intersectY1 = Math.min(scissorY + scissorHeight, clipY1);
            scissorX = intersectX0;
            scissorY = intersectY0;
            scissorWidth = Math.max(0, intersectX1 - intersectX0);
            scissorHeight = Math.max(0, intersectY1 - intersectY0);
            this.immediateScissoredGroups++;
        }
        org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
        scissor.get(0).offset().set(scissorX, scissorY);
        scissor.get(0).extent().set(scissorWidth, scissorHeight);
        vkCmdSetScissor(this.commandBuffer, 0, scissor);
        this.groupViewportNarrowed = true;
        this.immediateNarrowViewports++;
        if (this.immediateNarrowViewports <= 3) {
            System.out.println("[Celtium] CELTIUM_GROUP_VIEWPORT gl=" + viewport[0] + "," + viewport[1] + " "
                    + viewport[2] + "x" + viewport[3] + " clipped=" + x0 + "," + y + " " + width + "x" + height
                    + " target=" + this.targetWidth + "x" + this.targetHeight);
        }
    }

    /** Groups drawn with a viewport narrower than the target: the counter that says the sub-rect path is live. */
    private long immediateNarrowViewports;
    /** Groups whose Vulkan scissor was the intersection of the target with the game's own glScissor box (BH-09). */
    private long immediateScissoredGroups;
    /** Groups drawn with GL's polygon offset applied as a Vulkan depth bias (BH-11). */
    private long immediateDepthBiasedGroups;
    /** Groups whose clipped viewport is empty: an off-drawable rectangle, which GL would also draw nothing for. */
    private long immediateOffTargetViewports;


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
            // Minecraft's overlay/FBO setup uses the mirrored GlStateManager ortho/model-view stacks. These batches ARE
            // accepted: returning without capturing them left the previous world's perspective here and clipped real GUI
            // geometry. Snapshot the local pass matrices, never borrow another pass's camera.
            System.arraycopy(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_PROJECTION), 0, this.glProjection, 0, 16);
            System.arraycopy(this.immediateState.matrix(VulkanGlState.MATRIX_MODE_MODELVIEW), 0, this.glModelView, 0, 16);
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

    /**
     * Writes the first three columns of an affine model-view matrix (48 bytes); the shader rebuilds the fourth, which is
     * (0, 0, 0, 1) by construction. This is what keeps the immediate push block inside Vulkan's guaranteed 128 bytes
     * (audit S19).
     */
    /**
     * Writes {@code projection * modelView} into the push constants.
     * <p>
     * Both are column-major 4x4 arrays in OpenGL's order, so this is the ordinary column-major multiply. The shader needs one
     * multiply afterwards, and the model view's translation - which the three-column form dropped - is carried by the product.
     */
    private static void pushCombinedMatrixInto(float[] projection, float[] modelView, ByteBuffer push, int base) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += projection[k * 4 + row] * modelView[column * 4 + k];
                }
                push.putFloat(base + column * 16 + row * 4, sum);
            }
        }
    }

    private static void pushMatrixColumnsInto(float[] matrix, ByteBuffer target, int offset) {
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 4; row++) {
                target.putFloat(offset + (column * 4 + row) * 4, matrix[column * 4 + row]);
            }
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

    /**
     * Submits nothing but the semaphore pairing: it waits on the frame's GL-to-Vulkan semaphore and signals the
     * Vulkan-to-GL one, so a frame that was begun can be ended even when the present never happened. Without this the
     * GL wait inside {@code endFrame} has nothing to wait for and blocks the render thread for good (audit S18).
     */
    private void submitSignalOnly() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default();
            LongBuffer waitSemaphores = stack.mallocLong(1);
            waitSemaphores.put(0, this.frameTarget.waitSemaphore());
            IntBuffer waitStages = stack.mallocInt(1);
            waitStages.put(0, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            LongBuffer signalSemaphores = stack.mallocLong(1);
            signalSemaphores.put(0, this.frameTarget.doneSemaphore());
            submit.waitSemaphoreCount(waitSemaphores.remaining());
            submit.pWaitSemaphores(waitSemaphores).pWaitDstStageMask(waitStages).pSignalSemaphores(signalSemaphores);
            // No command buffers: the submission exists only to release the frame's synchronization.
            if (VulkanSubmitFault.shouldFail("signal-only")) {
                // The failure is raised exactly where a driver refusal would be, before the queue has seen anything, so
                // the code's own pre-submit rules apply: what is already recorded stays recorded and nothing in flight is
                // touched. That is the property this switch exists to check.
                check(org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST, "vkQueueSubmit (injected)");
            }
                        check(vkQueueSubmit(this.context.queue(), submit, VK_NULL_HANDLE), "vkQueueSubmit (signal only)");
            this.frameSubmitReachedQueue = true;
            this.lastFrameReachedQueue = true;
            if (this.frameTarget instanceof GllessFrameTarget gllessTarget) {
                // The signal-only submission waited on the wait semaphore and signalled the done semaphore: the pairing is
                // complete for this frame, so nothing is outstanding to consume.
                gllessTarget.noteWaitConsumed();
            }
        }
    }

    private void submitCommands() {
        submitCommands(true);
    }

    /**
     * {@code withFrameSemaphores} false submits the same command buffer without the GL↔Vulkan semaphore pair: the
     * seeded post-chain path synchronizes with the fence instead, and the frame's semaphores belong to whoever
     * opened the frame.
     */
    private void submitCommands(boolean withFrameSemaphores) {
        if (this.gpuStalled) {
            // Nothing to end: the buffer was never begun. This is the last of the recording entries the steady-state injection
            // reported, after the pass-level ones.
            return;
        }
        // OPT-07: every copy the frame recorded goes out as one submission before this one, so the vertices, indices
        // and uniform bytes this command buffer reads are in place. It is a no-op when nothing is pending, and it is
        // the frame's own ordering statement: a copy no longer needs a submission and a wait of its own.
        this.context.flushUploads();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(vkEndCommandBuffer(this.commandBuffer), "vkEndCommandBuffer");
            this.commandState.end();
            check(vkResetFences(this.context.device(), this.fence), "vkResetFences");
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default();
            LongBuffer waitSemaphores = null;
            IntBuffer waitStages = null;
            LongBuffer signalSemaphores = null;
            if (withFrameSemaphores) {
                waitSemaphores = stack.mallocLong(1);
                waitSemaphores.put(0, this.frameTarget.waitSemaphore());
                waitStages = stack.mallocInt(1);
                waitStages.put(0, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
                signalSemaphores = stack.mallocLong(1);
                signalSemaphores.put(0, this.frameTarget.doneSemaphore());
            }
            org.lwjgl.PointerBuffer commandBuffers = stack.mallocPointer(1);
            commandBuffers.put(0, this.commandBuffer.address());
            // LWJGL derives the semaphore counts from the buffers, so only the pointers are set here.
            // waitSemaphoreCount must be set explicitly: LWJGL's pWaitSemaphores(LongBuffer) writes only the
            // pointer, unlike pSignalSemaphores which also sets its count. Leaving it to calloc's zero meant this
            // submission waited on nothing at all - which is what the validation layer was reporting all along.
            if (withFrameSemaphores) {
                submit.waitSemaphoreCount(waitSemaphores.remaining());
                submit.pWaitSemaphores(waitSemaphores).pWaitDstStageMask(waitStages)
                        .pSignalSemaphores(signalSemaphores);
            }
            submit.pCommandBuffers(commandBuffers);
            if (this.immediateWindowBusy && !this.recordedImmediateGroups.isEmpty() && failImmediateSubmits > 0) {
                failImmediateSubmits--;
                throw new IllegalStateException("injected immediate submit failure before queue ownership");
            }
            if (FAIL_SUBMITS_REMAINING > 0) {
                // Test-only injection, so the fail-closed paths can be exercised instead of merely compiled: the
                // submission is treated as refused by the driver, which is the case the audit's E asks to be shown to
                // end in bounded time rather than in a wait for a signal that never comes.
                FAIL_SUBMITS_REMAINING--;
                record("injected submit failure (" + FAIL_SUBMITS_REMAINING + " left): this submission is not sent");
                throw new IllegalStateException("injected submit failure before queue ownership");
            }
            if (VulkanSubmitFault.shouldFail("fenced")) {
                // The failure is raised exactly where a driver refusal would be, before the queue has seen anything, so
                // the code's own pre-submit rules apply: what is already recorded stays recorded and nothing in flight is
                // touched. That is the property this switch exists to check.
                check(org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST, "vkQueueSubmit (injected)");
            }
                        check(vkQueueSubmit(this.context.queue(), submit, this.fence), "vkQueueSubmit");
            // OPT-05: the arena slot that holds this frame's immediate geometry is now in flight, and may not be reset
            // until a wait confirms the submission completed. Recorded here rather than at the wait, because a submit
            // that never reaches the queue must not hold a slot, and an abandon after a successful submit must.
            if (this.frameArena != null) {
                this.frameArena.submitted(this.frameId);
            }
            // The queue accepted this submission, so the layouts the recording declared into it are now the truth
            // every wrapper of those images shares. A refused submit throws before this line, leaving the recording
            // pending for the next beginCommands() to abandon - and the next ensureGeneralLayout/ensureSampledLayout
            // to re-record.
            VulkanGlTexture.commitLayoutRecording(this.commandBuffer);
            this.submissionPending = true;
            this.commandState.submitted();
            for (ImmediateGroup group : this.recordedImmediateGroups) {
                this.immediateSubmittedBatches += group.batches;
                this.retiredImmediateGroups.add(group);
            }
            this.recordedImmediateGroups.clear();
            this.frameSubmitReachedQueue = true;
            this.lastFrameReachedQueue = true;
            if (withFrameSemaphores && this.frameTarget instanceof GllessFrameTarget gllessTarget) {
                // This submission waited on the frame's wait semaphore, so the target must not consume it again when the
                // frame is abandoned (fog-path's measured VUID-00067 chain: an abandoned frame left the signal
                // outstanding and the next beginFrame signalled the same binary semaphore twice).
                gllessTarget.noteWaitConsumed();
            }
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
     * <b>This wait cannot be bounded by the API.</b> {@code vkDeviceWaitIdle} takes no timeout, and it is the honest
     * call here because submissions from several command buffers and from the bridge's handoffs are outstanding at
     * once. The comment used to claim the frame waits' timeout applied to it, which was not true of the call it
     * describes - and a doc that promises a bound the code does not have is worse than no bound, because it stops
     * anyone adding one.
     * <p>
     * What is done instead: a watchdog names the stall. If the call has not returned after the same timeout the frame
     * waits use, one bounded line is recorded and {@code gpuStalled} is set, so a device that stopped making progress
     * leaves evidence and every later path that must not reset, re-record or free in-use resources already refuses on
     * that flag. The call itself still returns when the driver returns; nothing here can interrupt it.
     * <p>
     * The callers are the three non-per-frame paths - region eviction, backend deletion and frame-target recreation -
     * so a stall here costs a teardown, not a frame.
     */
    private boolean settleGpu(String reason) {
        celtium$watchDeviceWait(reason);
        if (this.gpuStalled) {
            // A stalled GPU means the last command buffer may never have been begun and never submitted, so its recorded
            // references are still live. Destroying the frame target or its framebuffers here is what validation reported as
            // "in use by VkCommandBuffer": on this path the resources are left for process exit instead.
            // It reports rather than throws: the exception reached Minecraft's render loop and killed the client on the
            // crash screen, which the idle-failure injection measured as exit 255 with no report at all.
            this.stalledTeardownSkips++;
            record("not releasing resources after a stall: " + reason);
            return false;
        }
        long start = System.nanoTime();
        int result = org.lwjgl.vulkan.VK10.vkDeviceWaitIdle(this.context.device());
        synchronized (this.deviceWaitLock) {
            // This call's wait returned: answer its own token so a late watchdog for it cannot call it a stall.
            this.deviceWaitFinishedToken = this.deviceWaitToken;
        }
        if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
            this.noteStalled();
            throw new IllegalStateException("settleGpu(" + reason + "): vkDeviceWaitIdle returned " + result);
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (millis > 200L) {
            record("settleGpu(" + reason + ") waited " + millis + "ms for the device");
        }
        return true;
    }

    /**
     * Starts the watchdog that names a {@code vkDeviceWaitIdle} which never returns.
     * <p>
     * A daemon thread rather than an interrupt: the call cannot be interrupted, so the only useful thing a watchdog can
     * do is leave evidence and set the flag the failure paths already key on. It is created per call and exits after
     * firing once, so a normal run costs one short-lived thread per teardown.
     */
    private void celtium$watchDeviceWait(String reason) {
        // Per-call token, not a shared boolean (audit A05). With one flag, a watchdog from an earlier wait that fires
        // late reads the flag a NEWER wait has already cleared and declares a stall for a wait that has only just
        // started - a false stall, and a false stall is expensive here because gpuStalled makes every later path
        // refuse to release resources. Each call takes the next token and only the watchdog holding the current one
        // may report; the wait's completion sets the token to "finished", so a late watchdog finds its own call
        // already answered.
        long token = ++this.deviceWaitToken;
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(IDLE_TIMEOUT_NANOS / 1_000_000L);
            } catch (InterruptedException interrupted) {
                return;
            }
            synchronized (this.deviceWaitLock) {
                if (this.deviceWaitToken != token || this.deviceWaitFinishedToken == token) {
                    // Either a later wait has taken over (this watchdog is stale) or this call's wait already
                    // returned. Neither is a stall, and saying so is the whole point of the token.
                    return;
                }
                this.deviceWaitFinishedToken = token;
                this.gpuStalled = true;
                record("settleGpu(" + reason + "): vkDeviceWaitIdle has not returned after "
                        + (IDLE_TIMEOUT_NANOS / 1_000_000_000L) + "s (wait token " + token
                        + "); the device has stopped making progress. No recorded work is reset, re-recorded or"
                        + " freed from here on.");
            }
        }, "celtium-device-wait-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private boolean waitForIdle() {
        // A documented injection for the timeout path, which no ordinary run reaches: the audit asks for a safe fake
        // result here and a real latency test to be kept apart. It fires after a configured number of waits, not on the
        // first: failing the first wait stalls before the renderer exists, which is a start-up question rather than the
        // steady-state one this audit item describes, and the earlier run showed exactly that as a start-up abort.
        if (FAIL_IDLE_AFTER > 0 && ++this.idleWaits >= FAIL_IDLE_AFTER) {
            this.injectedIdleFailures++;
            this.noteStalled();
            // Nothing was submitted, so the frame's semaphores are never consumed. Leaving the frame open made the
            // caller's endFrame() wait in the GL write-back for a Vulkan signal that cannot come: the injected idle
            // failure then hung the client (exit 124, report missing) instead of ending the run with a message.
            this.frameTarget.abandonFrame("injected idle failure");
            return false;
        }
        if (this.fence == 0 || !this.submissionPending) {
            return true;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer fences = stack.mallocLong(1);
            fences.put(0, this.fence);
            int result = vkWaitForFences(this.context.device(), fences, true, IDLE_TIMEOUT_NANOS);
            if (FENCE_WAIT_FAULT) {
                // A wait that times out after the submission was accepted: the command buffer may still be in flight, so
                // nothing it recorded may be reset, re-recorded or destroyed. This is the second half of the rule the
                // submit fault covers, and it had no way to run until now.
                result = org.lwjgl.vulkan.VK10.VK_TIMEOUT;
                System.out.println("[Celtium] CELTIUM_FENCE_TIMEOUT injected");
            }
            if (DEVICE_LOST_FAULT && result == org.lwjgl.vulkan.VK10.VK_SUCCESS && this.injectedDeviceLosses == 0) {
                // One lost device, once: reporting the loss after a real submission is the post-submit case the
                // pre-submit vkQueueSubmit fault cannot produce - the queue took the command buffer and the answer
                // is the device is gone.
                this.injectedDeviceLosses++;
                result = org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
                System.out.println("[Celtium] CELTIUM_DEVICE_LOST injected");
            }
            if (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                // A timed-out fence wait is not by itself a dead device: a swapchain rebuild, a resize or a driver that
                // is busy with a long submission all produce one, and latching on the first timeout stops the renderer for
                // the rest of the session - measured on the world-resize leg, where `stalledStops=1` after the resize
                // left `presentedImageFramesAtCapture=0` and no picture. The wait is retried a bounded number of times
                // (per wait, not per session) before the latch, and both are counted.
                this.idleWaitTimeouts++;
                int retries = 0;
                while (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT && retries < IDLE_WAIT_RETRIES) {
                    retries++;
                    this.idleWaitRetries++;
                    record("waitForIdle: the fence did not signal within "
                            + (IDLE_TIMEOUT_NANOS / 1_000_000_000L) + "s; retry " + retries + " of "
                            + IDLE_WAIT_RETRIES + " before treating the device as stalled (timeouts="
                            + this.idleWaitTimeouts + ")");
                    result = vkWaitForFences(this.context.device(), fences, true, IDLE_TIMEOUT_NANOS);
                }
            }
            if (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                record("waitForIdle: the fence did not signal within " + (IDLE_TIMEOUT_NANOS / 1_000_000_000L)
                        + "s after " + IDLE_WAIT_RETRIES + " retries, so the last submission is not completing;"
                        + " not draining the queue");
                this.noteStalled();
                this.frameTarget.abandonFrame("fence wait timed out");
                return false;
            }
            if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                // Any other failure - device lost above all - means the frame's semaphores will never signal either, so
                // the frame is abandoned for the same reason the timeout branch abandons it: leaving it open would hang
                // the GL write-back waiting for a signal that cannot come.
                record("waitForIdle: vkWaitForFences returned " + result);
                this.noteStalled();
                this.frameTarget.abandonFrame("fence wait failed: " + result);
                return false;
            }
            this.submissionPending = false;
            // OPT-04: a confirmed completion is the only basis resource reuse may rest on. The arena's slots are
            // released here, before the retired groups below drop their slices, so a slot whose last group is freed in
            // this same call is reclaimable at the next beginFrame.
            VulkanContext.noteCompletionConfirmed(false);
            if (this.frameArena != null) {
                this.frameArena.completed(VulkanContext.completedEpoch());
            }
            this.commandState.completed();
            for (ImmediateGroup group : this.retiredImmediateGroups) {
                int batches = group.batches;
                this.immediateCompletedBatches += batches;
                if (group.target != this.mainTargetIdentity) this.offTargetCompletedBatches += batches;
            }
            releaseRetiredImmediateGroups();
            if (this.immediateGroups.isEmpty() && this.currentImmediateMesh == null) VulkanRetireQueue.flush();
        }
        return true;
    }

    /** How long a wait for the GPU will be tolerated before the run ends rather than hanging. */
    private static final long IDLE_TIMEOUT_NANOS = 5_000_000_000L;
    /**
     * How many times a timed-out fence wait is retried before the renderer latches. A resize or a swapchain rebuild
     * produces transient timeouts; a device that has genuinely stopped produces one on every attempt. Counted as
     * {@code idleWaitTimeouts}/{@code idleWaitRetries} so the report says which of the two happened.
     */
    private static final int IDLE_WAIT_RETRIES = 3;
    private int idleWaitTimeouts;
    private int idleWaitRetries;
    /** The game's main framebuffer's own size, reported on its bind; 0 until it has been reported. */
    private int mainFramebufferWidth;
    private int mainFramebufferHeight;

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
                    .size(READBACK_SLOTS * 64L).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
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
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanTerrainGpuBackend:5047")) {
                throw new IllegalStateException("injected allocation failure: VulkanTerrainGpuBackend:5047");
            }
            check(vkAllocateMemory(this.context.device(), allocate, null, handle), "vkAllocateMemory(readback)");
            this.readbackMemory = handle.get(0);
            check(vkBindBufferMemory(this.context.device(), this.readbackBuffer, this.readbackMemory, 0),
                    "vkBindBufferMemory(readback)");
            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(this.context.device(), this.readbackMemory, 0, READBACK_SLOTS * 64L, 0, pointer), "vkMapMemory(readback)");
            this.readbackMapped = pointer.get(0);
        }
    }

    private void recordReadback(RegionResources resources, long sourceOffset, int slot) {
        ensureReadback();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkBufferCopy.Buffer region = org.lwjgl.vulkan.VkBufferCopy.calloc(1, stack);
            region.get(0).srcOffset(sourceOffset).dstOffset((long) slot * READBACK_COPY_BYTES)
                    .size(READBACK_COPY_BYTES);
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
        if (!waitForIdle()) throw new IllegalStateException("readback fence did not complete");
        ByteBuffer view = MemoryUtil.memByteBuffer(this.readbackMapped,
                Math.max(READBACK_COPY_BYTES, this.readbackSlots * READBACK_COPY_BYTES));
        view.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        StringBuilder line = new StringBuilder("gpu draws:");
        int colourCounts = 0;
        java.util.Map<Integer, Integer> histogram = new java.util.TreeMap<>();
        for (int slot = 0; slot < this.readbackSlots; slot++) {
            if (!this.readbackValid[slot]) {
                // The copy for this slot was refused, so the block holds whatever was there before it. Decoding it
                // would put a stale colour into the histogram and into the diagnostic this exists to be.
                continue;
            }
            int base = slot * READBACK_COPY_BYTES;
            int colour = view.getInt(base + 12);
            histogram.merge(Integer.valueOf(colour), Integer.valueOf(1), Integer::sum);
            if (slot < 10) {
                line.append(" [").append(slot).append("] colour=0x").append(Integer.toHexString(colour))
                        .append(" light=0x").append(Integer.toHexString(view.getInt(base + 24)))
                        .append(" pos=(").append(fmt(view.getFloat(base))).append(',')
                        .append(fmt(view.getFloat(base + 4))).append(',')
                        .append(fmt(view.getFloat(base + 8))).append(')');
            }
        }
        colourCounts = histogram.size();
        line.append(" distinctColours=").append(colourCounts).append(" histogram=");
        int shown = 0;
        for (var e : histogram.entrySet()) {
            if (shown++ >= 8) break;
            line.append('0').append(Integer.toHexString(e.getKey().intValue())).append('x').append(e.getValue()).append(' ');
        }
        record(line.toString());
        // stdout as well as the list: this is the only place that shows what the GPU holds rather than what the CPU
        // wrote, and the diagnostics list is not printed by every run.
        if (VERTEX_DIAGNOSTICS) {
            System.out.println("[Celtium] CELTIUM_GPU_VERTEX " + line);
        }
    }

    /** Short float formatting for the readback line. */
    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", Float.valueOf(value));
    }

    private void record(String diagnostic) {
        this.diagnostics.add(diagnostic);
    }

    /**
     * Names a render pass for a message, tolerating the absence of one.
     * <p>
     * Three null pointers per run came from these message sites in the no-bridge configuration, where a caller with no pass
     * still reaches the logging. A message that throws is worse than no message: it turns a diagnostic into a crash.
     */
    private static String nameOf(TerrainRenderPass pass) {
        return pass == null ? "<no render pass>" : pass.name();
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }

    /**
     * {@link #describe} plus where it was thrown, for the one failure path whose cause is invisible without it.
     * <p>
     * The immediate window's flush catches this: measured on the entity leg, {@code windowFlushFailures=1} at frame 69
     * with 15,113 batches dropped - every format 4 and 9 group of the window, which is the entity and held-item path -
     * and {@code lastWindowFlushFailure} read only {@code IndexOutOfBoundsException: null}, because a message-less
     * exception prints as "null". The class alone cannot name the array, so the first frames of the stack travel with
     * it. Bounded to five frames: this runs once per failure.
     */
    private static String describeWithOrigin(Throwable error) {
        StringBuilder line = new StringBuilder(describe(error));
        StackTraceElement[] frames = error.getStackTrace();
        for (int i = 0; i < frames.length && i < 5; i++) {
            line.append(i == 0 ? " at " : " <- ").append(frames[i].getClassName()).append('.')
                    .append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber());
        }
        return line.toString();
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
        /** The first 32 bytes of the first vertex of each section, as the upload saw them. */
        final byte[][] sectionFirstVertex = new byte[RenderRegion.REGION_SIZE][];
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
