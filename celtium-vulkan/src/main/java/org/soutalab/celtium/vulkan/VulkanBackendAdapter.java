package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.lane2.Lane2ImmediateState;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.soutalab.celtium.harness.HarnessConfig;
import org.soutalab.celtium.vulkan.DynamicLightService;
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
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Terrain GPU backend implemented with Vulkan.
 * <p>
 * It consumes the same meshing output, the same per-section metadata, the same culling and batch assembly and the
 * same render lists as the OpenGL backend; only the GPU resources and the draw calls differ. Renders into the
 * game's framebuffer through {@link GllessFrameTarget}, which owns the colour and depth images outright.
 * <p>
 * Runs on the client/render thread with a current GL context, like the OpenGL backend it replaces.
 */
/**
 * The thin backend adapter left by the S3-b E7 split: it owns the installed surface
 * ({@code VulkanRenderer} / {@code CeltiumBackend}), construction order and teardown, and
 * delegates - frame loop to the {@link FrameCoordinator}, immediate recording to the
 * {@link Lane2ImmediateRecorder}, present to the {@link PresentSwapchain}. No drawing or
 * recording bodies live here.
 */
public final class VulkanBackendAdapter implements VulkanRenderer, PackStageStateGuard.DepthAlphaSink {

    final BackendDiagnostics reports;
    final VulkanContext context;
    final FrameCoordinator frameCoordinator;
    final Lane2ImmediateRecorder funnel;
    final PresentSwapchain presentSwapchain;
    final CopyReadSide copyReadSide;
    final FrameStatsReport frameStats;

    public VulkanBackendAdapter(VulkanContext context) {
        this.reports = new BackendDiagnostics();
        this.frameCoordinator = new FrameCoordinator(context, this.reports);
        this.funnel = new Lane2ImmediateRecorder(context, this.frameCoordinator, this.reports);
        this.frameCoordinator.funnel = this.funnel;
        this.frameCoordinator.owner = this;
        this.presentSwapchain = new PresentSwapchain(this.frameCoordinator, this.funnel);
        this.frameCoordinator.presentSwapchain = this.presentSwapchain;
        this.funnel.presentSwapchain = this.presentSwapchain;
        this.funnel.owner = this;
        this.funnel.modelStats = new ModelStatistics(this.modelGeometry);
        this.copyReadSide = new CopyReadSide(this.frameCoordinator, this.funnel, this.presentSwapchain);
        this.copyReadSide.seatOwner = this;
        this.textureCopyService = this.copyReadSide.textureCopyService;
        this.presentSwapchain.seatOwner = this;
        this.frameStats = new FrameStatsReport(this.frameCoordinator, this.funnel);
        this.frameStats.seatOwner = this;
        this.reports.bind(this.frameCoordinator, this.funnel, this.presentSwapchain, this);

        this.context = context;
        // OPT-07 lifetime rule: a deleter asks whether an open recording could still name its resource. The backend is
        // the only place that knows - the unified frame buffer, an open window recording, or a recording in progress -
        // so it installs the probe here, before any resource can be deleted.
        context.celtium$setRecordingOpenProbe(() -> this.frameCoordinator.frameBufferOpen || this.funnel.immediateWindowBusy
                || this.frameCoordinator.commandState.recording());
        // Deferred destroys are tagged with the submission sequence and only run once a later sequence has been
        // confirmed, which is what makes tearing down a pipeline/pool used by a pending submission legal.
        context.celtium$setSequenceSources(this.frameCoordinator.scheduler::submitSequence, this.frameCoordinator.scheduler::completedSubmitSequence);
        this.uniformAlignment = Math.max(MIN_UNIFORM_ALIGNMENT,
                Integer.highestOneBit(Math.max(MIN_UNIFORM_ALIGNMENT, this.funnel.alignmentFromDevice())));
        this.frameCoordinator.createCommandResources();
        this.nativePassExecutor = new PassExecutor(this.context, this.funnel.targetRegistry,
                this.funnel.publishedTextures, this.funnel.nativePassRegistry);
        // The vanilla post executor resolves its targets through the renderer's existing
        // publications (ruling section 5.2); the coordinator holds no mirror of its own.
        this.frameCoordinator.setVanillaPostPublications(this.funnel.publishedTextures);
        this.funnel.celtium$installWindowSplitExitHook();
        this.funnel.celtium$installFontCensusExitHook();
        // The pipeline prewarm pair (Stage 5 slice S5-0): created with the backend so its
        // fallback counter exists for every diagnostic report, and registered with the context
        // so the context's teardown stops its background compiler before the device goes away.
        // The startup warm set itself is registered later, at the first world prepare
        // (celtium$ensurePipelinePrewarm), when a terrain render pass exists to build against.
        this.funnel.pipelinePrewarmCompiler = new PipelinePrewarmCompiler();
        this.funnel.pipelinePrewarm = new PipelinePrewarm(this.funnel.pipelinePrewarmCompiler);
        context.celtium$setPipelinePrewarm(this.funnel.pipelinePrewarm);
    }

    /** Alignment required for dynamic uniform buffer offsets. Tightened at construction from the device limits. */
    static final int MIN_UNIFORM_ALIGNMENT = 256;

    DynamicLightService dynamicLightService;

    final int uniformAlignment;

    /**
     * UI passes retired by a frame-format change (Stage 1 BGRA ignition): kept, not destroyed, until
     * teardown - a pass may still be named by submitted command buffers, and the variant caches take the
     * same keep-until-teardown line. Bounded by the number of frame formats (two).
     */
    final java.util.ArrayList<Long> retiredUiRenderPasses = new java.util.ArrayList<>();

    /** The frame-shaped pipeline passes for non-default classes, indexed by class ordinal (0 = unused). */
    final long[] formatClassPipelinePasses = new long[TargetFormatClass.values().length];

    VulkanGlTexture atlasTexture;


    /**
     * Creates the instance that exists from window creation, before any world renderer does.
     * <p>
     * This is the menu, the loading screens and every GUI-only frame: under the GL-less window the game draws them from
     * client-memory vertex arrays, which the stub decodes and hands to {@code CeltiumBackends} - a holder that used
     * to stay empty until a world was loaded, so those draws went to nobody and nothing presented. Measured on the
     * user's own launch: 524,288 client-memory draws decoded and every one of them refused with "no terrain backend is
     * installed". The same instance is later adopted by the world renderer
     * ({@link #celtium$adoptTerrainState}), so one renderer serves the whole process instead of one per configuration.
     */
    public static VulkanBackendAdapter createForWindow(VulkanContext context) {
        VulkanBackendAdapter backend = new VulkanBackendAdapter(context);
        backend.windowBackend = true;
        backend.windowOwned = true;
        return backend;
    }


    /** The loading screen's own surface (S3-b addendum 4-1): owns the native screen and its dump state. */
    final LoadingScreenSurface loadingSurface = new LoadingScreenSurface();


    /**
     * Presents Forge's loading screen ({@link NativeLoadingScreen}): Forge's loader supplies the progress and the
     * splash configuration, this path draws Forge's frame into its own image and presents it.
     * <p>
     * The frame is laid out at {@link LoadingScreenSurface#layoutSize} rather than at the requested size the caller passes:
     * those are the game's cached display fields, frozen at the requested size for the whole load, while the
     * swapchain this presents through already follows the window.
     *
     * @param colors background, barBorder, bar, barBackground, memoryGood, memoryWarn, memoryLow (RGB)
     * @return the size the frame was laid out and presented at, or {@code null} when nothing was presented
     */
    public int[] presentLoadingProgress(int width, int height, int[] colors, boolean showMemory, boolean rotate,
            int logoOffset, java.awt.image.BufferedImage logo, java.awt.image.BufferedImage forge,
            java.awt.image.BufferedImage font, String[] titles, String[] messages, int[] steps, int[] totals,
            int angle) {
        if (!this.windowBackend || this.frameCoordinator.gpuStalled || !ensureSwapchain()) return null;
        return this.loadingSurface.present(this.context, this.funnel.swapchain, width, height, colors, showMemory,
                rotate, logoOffset, logo, forge, font, titles, messages, steps, totals, angle);
    }

    public void finishLoadingProgress() {
        this.loadingSurface.finish(() -> this.frameCoordinator.settleGpu("loading screen finished"));
    }


    /** True while this instance exists only for the window and no world renderer has adopted it yet. */
    boolean windowBackend;

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
    boolean windowOwned;


    /** {@return whether this instance was created at window creation and no world renderer has adopted it yet}. */
    public boolean celtium$isWindowBackend() {
        return this.windowBackend;
    }


    /** {@return the ownership flags and delete count}, for the line that reports a backend being replaced. */
    public String celtium$ownershipSummary() {
        return "windowOwned=" + this.windowOwned + " windowBackend=" + this.windowBackend + " deletes=" + this.deleteCalls;
    }

    int deleteCalls;


    /**
     * Whether the full (non-window) teardown in {@link #celtium$deleteSettled()} ran to its end. A second
     * {@link #delete()} after that has nothing left to release - every handle it would touch is already
     * zeroed or destroyed - and re-running the settle and the release sequence over that state is the
     * double teardown this flag exists to refuse (issue #425). The window-owned exit never sets it: that
     * path is a per-world release and is called once per world by design, and the stalled / settle-failed
     * exits do not set it either, because a later delete may still have the settle succeed.
     */
    boolean fullyDeleted;


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
     * Hands a loaded world to the window-time instance (S2-b): the dynamic light service the Lane 1
     * terrain path gathers its per-frame sources through. The device, the command resources, the frame
     * target and the swapchain are the ones already in use, so the first world frame finds them warm.
     * The presented-frame counter is restarted here for the reason the old adoption restarted it: it
     * names world frames, and the window-time instance has already presented menu frames.
     */
    public void celtium$adoptWorld(DynamicLightService dynamicLightService) {
        this.dynamicLightService = dynamicLightService;
        if (this.lane1 != null) {
            this.lane1.clearWorld();
        }
        this.lane1PreparedFrame = -1L;
        this.windowBackend = false;
        if (this.framePresentedFromVulkan != 0) {
            this.reports.record("presented-frame counter reset at adoption: was " + this.framePresentedFromVulkan
                    + " pre-world presents, the numbering starts with the world");
            this.framePresentedFromVulkan = 0;
        }
        this.reports.record("the world adopted the window-time backend: dynamicLights=" + (dynamicLightService != null));
    }


    // ------------------------------------------------------------------
    // Lane 1 terrain (S2-b): the resident frontend and its frame entry.
    // ------------------------------------------------------------------

    org.soutalab.celtium.vulkan.terrain.lane1.Lane1TerrainFrontend lane1;

    long lane1PreparedFrame = -1L;

    /** The frame whose S1 cull dispatch has been recorded (once per frame, first main layer). */
    long lane1CullDispatchedFrame = -1L;

    @Override
    public void celtium$setTextureCellTable(int[] packedCells) {
        celtium$lane1().setTextureCellTable(packedCells);
    }

    @Override
    public void celtium$setTextureRectTable(float[] packedRects) {
        celtium$lane1().setTextureRectTable(packedRects);
    }


    /** Lane 1 store/draw counters for the F3 panel and status reports; never starts the frontend. */
    public String celtium$lane1DiagnosticsSummary() {
        String base = this.lane1 == null ? "lane1 sections=0 (not started)" : this.lane1.diagnosticsSummary();
        base += " atlasW=" + (this.atlasTexture == null ? -1 : this.atlasTexture.width());
        return base + " drawCalls=" + this.lane1DrawCalls + " guardCmd=" + this.lane1GuardCmd
                + " guardStall=" + this.lane1GuardStall + " guardTarget=" + this.lane1GuardTarget
                + " guardPass=" + this.lane1GuardPass + " guardViews=" + this.lane1GuardViews
                + " recordPass=" + this.lane1RecordPasses;
    }

    long lane1DrawCalls;

    long lane1GuardCmd;

    long lane1GuardStall;

    long lane1GuardTarget;

    long lane1GuardPass;

    long lane1GuardViews;

    long lane1RecordPasses;

    org.soutalab.celtium.vulkan.terrain.lane1.Lane1TerrainFrontend celtium$lane1() {
        if (this.lane1 == null) {
            this.lane1 = new org.soutalab.celtium.vulkan.terrain.lane1.Lane1TerrainFrontend(this.context);
        }
        return this.lane1;
    }

    @Override
    public void celtium$lane1UploadSection(int x, int y, int z,
            org.soutalab.celtium.vulkan.terrain.lane1.SectionGeometry geometry) {
        celtium$lane1().uploadSection(x, y, z, geometry,
                this.frameCoordinator.scheduler == null ? 0L : this.frameCoordinator.scheduler.submitSequence());
    }

    @Override
    public void celtium$lane1UploadSectionRaw(int x, int y, int z,
            org.soutalab.celtium.vulkan.terrain.lane1.SectionGeometryRaw raw) {
        celtium$lane1().uploadSectionRaw(x, y, z, raw,
                this.frameCoordinator.scheduler == null ? 0L : this.frameCoordinator.scheduler.submitSequence());
    }

    @Override
    public void celtium$lane1RemoveSection(int x, int y, int z) {
        if (this.lane1 != null) {
            this.lane1.removeSection(x, y, z,
                    this.frameCoordinator.scheduler == null ? 0L : this.frameCoordinator.scheduler.submitSequence());
        }
    }

    @Override
    public boolean celtium$lane1HasSection(int x, int y, int z) {
        return this.lane1 != null && this.lane1.hasSection(x, y, z);
    }

    @Override
    public int celtium$lane1SectionCount() {
        return this.lane1 == null ? 0 : this.lane1.sectionCount();
    }


    /**
     * One terrain layer through the Lane 1 path, in the frame shell the retired pass used: the
     * pre-terrain flush first (the sky and the void plane are queued immediate work that must land
     * before the terrain), then the frame render pass over the frame target with the layer's draws
     * inside it. The pass loads colour and depth, so the layers compose in vanilla's order.
     */
    @Override
    public void celtium$lane1DrawLayer(int layerOrdinal, float[] projection, float[] modelView,
                                       double cameraX, double cameraY, double cameraZ) {
        // S2 of the fixed template: a terrain layer records into the frame's main stage.
        this.frameCoordinator.markStage(FrameCoordinator.Stage.MAIN_RENDER);
        this.lane1DrawCalls++;
        if (this.frameCoordinator.commandBuffer == null) {
            this.lane1GuardCmd++;
            return;
        }
        if (this.frameCoordinator.gpuStalled) {
            this.lane1GuardStall++;
            return;
        }
        if (this.funnel.frameTarget == null || this.frameCoordinator.commandState == null) {
            this.lane1GuardTarget++;
            return;
        }
        if (this.frameCoordinator.commandState.inRenderPass()) {
            this.lane1GuardPass++;
            return;
        }
        org.soutalab.celtium.vulkan.terrain.lane1.Lane1TerrainFrontend frontend = celtium$lane1();
        if (this.lane1PreparedFrame != this.frameCoordinator.frameId) {
            // The retired terrain pass refreshed the atlas/lightmap mirrors once per frame
            // before drawing; the lane1 entry inherits that seat, or the mirrors never
            // establish and every layer is skipped at the views guard below.
            refreshTextures();
            celtium$lane1PrepareFrame(frontend, projection, modelView, cameraX, cameraY, cameraZ);
            this.lane1PreparedFrame = this.frameCoordinator.frameId;
        }
        if (this.atlasTexture == null || this.funnel.lightmapTexture == null
                || this.atlasTexture.view() == 0L || this.funnel.lightmapTexture.view() == 0L) {
            this.lane1GuardViews++;
            return;
        }
        if (this.lane1CullDispatchedFrame != this.frameCoordinator.frameId) {
            // S1 of the fixed template records here, once per frame, ahead of the first main
            // layer pass: the GPU cull dispatch fills the indirect regions the layer passes
            // consume (the CPU visible-set scan is the fallback the frontend keeps when the
            // dispatch does not arm). Bracketed by the stage's G0 timestamp pair (G2-30).
            this.lane1CullDispatchedFrame = this.frameCoordinator.frameId;
            int cullSlot = this.funnel.celtium$beginNamedPassTimestamp();
            try {
                frontend.recordCullDispatch(this.frameCoordinator.commandBuffer, this.frameCoordinator.frameSlot);
            } finally {
                this.funnel.celtium$closeNamedPassTimestamp(cullSlot, "S1_cull_lightgrid");
            }
        }
        this.funnel.celtium$preflushBeforeTerrain();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.funnel.frameTarget.recordAcquireBarriers(this.frameCoordinator.commandBuffer);
            long renderPass = this.funnel.uiRenderPass();
            long framebuffer = this.funnel.ensureFramebuffer(renderPass);
            org.lwjgl.vulkan.VkRenderPassBeginInfo begin =
                    this.funnel.celtium$terrainBeginInfo(renderPass, framebuffer, this.funnel.targetWidth, this.funnel.targetHeight);
            org.soutalab.celtium.core.shader.TerrainPassKind kind =
                    org.soutalab.celtium.core.shader.TerrainPassKind.values()[layerOrdinal];
            int passSlot = this.funnel.celtium$beginNamedPassTimestamp();
            vkCmdBeginRenderPass(this.frameCoordinator.commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);
            this.frameCoordinator.commandState.beginPass(this.funnel.activeImmediateTarget);
            try {
                this.funnel.setViewportAndScissor(stack, this.funnel.targetWidth, this.funnel.targetHeight);
                this.lane1RecordPasses++;
                // G2-30's named Lane 1 CPU bracket: the regular rewiring of the existing
                // terrainRecord slot onto the seated recordPass call.
                FramePassTimings.begin("terrainRecord");
                try {
                    frontend.recordPass(
                            kind,
                            this.frameCoordinator.commandBuffer, renderPass, this.frameCoordinator.frameSlot,
                            this.atlasTexture.view(), this.funnel.lightmapTexture.view(),
                            this.atlasTexture.width() / 16.0f);
                } finally {
                    FramePassTimings.end("terrainRecord");
                }
            } finally {
                vkCmdEndRenderPass(this.frameCoordinator.commandBuffer);
                this.frameCoordinator.commandState.endPass();
                this.funnel.celtium$closeNamedPassTimestamp(passSlot, Lane2ImmediateRecorder.celtium$lane1PassTimingName(kind));
            }
        }
    }


    /**
     * Publishes one frame's Lane 1 view state: the combined matrix for the frustum planes
     * (camera-relative space), the translation-free view-projection the vertex shader applies after subtracting the
     * camera position itself, the dynamic lights gathered through the adopted service, and the
     * immediate path's fog state.
     */
    void celtium$lane1PrepareFrame(
            org.soutalab.celtium.vulkan.terrain.lane1.Lane1TerrainFrontend frontend,
            float[] projection, float[] modelView, double cameraX, double cameraY, double cameraZ) {
        // S1 of the fixed template: this publication is the frame's cull + light-grid
        // state (frustum, view, dynamic-light grid) the terrain stages consume.
        this.frameCoordinator.markStage(FrameCoordinator.Stage.CULL_LIGHTGRID);
        // The shadow JOML's buffer read goes through Unsafe and needs a direct buffer; the
        // driver's matrices arrive as heap float[], so they are set element-wise instead
        // (column-major, the same order the array carries). FloatBuffer.wrap on a heap
        // array segfaulted in MemUtilUnsafe the first time this seat was reached.
        Matrix4f proj = new Matrix4f().set(
                projection[0], projection[1], projection[2], projection[3],
                projection[4], projection[5], projection[6], projection[7],
                projection[8], projection[9], projection[10], projection[11],
                projection[12], projection[13], projection[14], projection[15]);
        Matrix4f mv = new Matrix4f().set(
                modelView[0], modelView[1], modelView[2], modelView[3],
                modelView[4], modelView[5], modelView[6], modelView[7],
                modelView[8], modelView[9], modelView[10], modelView[11],
                modelView[12], modelView[13], modelView[14], modelView[15]);
        // The frustum and the shader transform are both origin-relative: the extractor
        // combines the Vulkan projection with the translation-free model-view (the
        // model-view operating on camera-relative positions), which is the same product
        // the vertex stage applies after the section relativization. The plane spec is
        // the Gribb-Hartmann set this seat used to hand-roll (near = row 2 under the
        // Vulkan depth convention); the extractor additionally falls back to permissive
        // planes for a non-finite or singular transform instead of publishing garbage.
        Matrix4f mvZero = new Matrix4f(mv);
        mvZero.m30(0.0f);
        mvZero.m31(0.0f);
        mvZero.m32(0.0f);
        float[] vulkanProj = new float[16];
        this.funnel.vulkanProjection(proj).get(vulkanProj);
        float[] mvZeroValues = new float[16];
        mvZero.get(mvZeroValues);
        org.soutalab.celtium.vulkan.terrain.lane1.FrustumExtractor.View frameView =
                org.soutalab.celtium.vulkan.terrain.lane1.FrustumExtractor.extract(
                        new org.soutalab.celtium.core.frame.CameraPose(cameraX, cameraY, cameraZ, 0.0f, 0.0f),
                        vulkanProj, mvZeroValues,
                        org.soutalab.celtium.vulkan.terrain.lane1.FrustumExtractor.DepthConvention
                                .VULKAN_ZERO_TO_ONE);
        java.util.List<org.soutalab.celtium.vulkan.terrain.lane1.LightGridBuilder.PointLight> lights =
                new java.util.ArrayList<>();
        DynamicLightService service = this.dynamicLightService;
        if (service != null) {
            float[] gathered = new float[4 * DynamicLightService.MAX_LIGHTS];
            int count = service.gatherLights(gathered, cameraX, cameraY, cameraZ);
            for (int i = 0; i < count; i++) {
                float level = gathered[i * 4 + 3];
                if (level <= 0.0f) {
                    continue;
                }
                float strength = level / 15.0f;
                lights.add(new org.soutalab.celtium.vulkan.terrain.lane1.LightGridBuilder.PointLight(
                        cameraX + gathered[i * 4], cameraY + gathered[i * 4 + 1],
                        cameraZ + gathered[i * 4 + 2], level + 2.0f,
                        1.0f, 0.92f, 0.78f, strength * strength));
            }
        }
        int fogMode = 0;
        if (this.funnel.fog.enabled()) {
            fogMode = switch (this.funnel.fog.glMode()) {
                case 0x2600 -> 1;
                case 0x0800 -> 2;
                case 0x0801 -> 3;
                default -> 1;
            };
        }
        frontend.prepareFrame(frameView, lights,
                this.funnel.fog.getFogColor(), this.funnel.fog.getFogStart(), this.funnel.fog.getFogEnd(),
                this.funnel.fog.getFogDensity(), fogMode, this.frameCoordinator.frameId);
        frontend.collectCompleted();
    }

    public List<String> diagnostics() {
        return this.reports.diagnostics();
    }


    // ------------------------------------------------------------------ CeltiumBackend

    @Override
    public String getName() {
        return "vulkan";
    }


    /** The post-process chain bridge: runs a collected shader chain natively, adopts its framebuffers and keeps the textures it publishes to the game in the right layout. */
    /** Delegating door (S3-b E6): the implementation lives in {@link PresentSwapchain}. */
    void celtium$publishFrameTexture(int glTextureName, long image, long view, int width, int height) {
        this.presentSwapchain.celtium$publishFrameTexture(glTextureName, image, view, width, height);
    }


    /** Delegating door (S3-b E6): the implementation lives in {@link PresentSwapchain}. */
    void celtium$publishFrameDepthTexture(int glTextureName, long image, long view, int width, int height,
            int declaredLayout, long depthKey) {
        this.presentSwapchain.celtium$publishFrameDepthTexture(glTextureName, image, view, width, height, declaredLayout, depthKey);
    }


    /** Delegating door (S3-b E6): the implementation lives in {@link PresentSwapchain}. */
    void celtium$forgetPublishedTarget(long identity) {
        this.presentSwapchain.celtium$forgetPublishedTarget(identity);
    }






    /** Delegating door (S3-b E6): the implementation lives in {@link PresentSwapchain}. */
    void celtium$transitionPublishedFrameTextures(boolean general) {
        this.presentSwapchain.celtium$transitionPublishedFrameTextures(general);
    }


    /** Delegating door (S3-b E6): the implementation lives in {@link PresentSwapchain}. */
    void celtium$transitionPublishedFrameTextures(boolean general, long attachColor, long attachDepth) {
        this.presentSwapchain.celtium$transitionPublishedFrameTextures(general, attachColor, attachDepth);
    }


    /** Delegating door (S3-b E6): the implementation lives in {@link PresentSwapchain}. */
    void celtium$transitionPublishedFrameTextures(boolean general, long attachColor, long attachDepth,
            long[] attachImages) {
        this.presentSwapchain.celtium$transitionPublishedFrameTextures(general, attachColor, attachDepth, attachImages);
    }



    /** Delegating door (S3-b E6): the implementation lives in {@link PresentSwapchain}. */
    void celtium$syncPublishedChainOutput(long identity, long chainPairColorImage) {
        this.presentSwapchain.celtium$syncPublishedChainOutput(identity, chainPairColorImage);
    }


    /**
     * The frame coordinator whose entrances the ShaderGroupMixin boundary calls (ruling
     * section 5.2): the vanilla post chain runs through {@code runVanillaPostChain}, never
     * through a delegator on this class.
     */
    public FrameCoordinator celtium$frameCoordinator() {
        return this.frameCoordinator;
    }


    /**
     * Counts a chain-input flush failure (DP-32). The mixin's catch at the chain boundary used to
     * swallow these whole: a flush that keeps failing leaves the pending geometry unsubmitted at
     * exactly the moment the chain reads the frame, and the run showed a chain reading stale content
     * with no record anywhere. The count itself lives on the frame coordinator (the verify line's
     * seat since the ruling's step 4); this seat keeps the bounded print. Counted always, printed
     * for the first few.
     */
    public void celtium$noteChainInputFlushFailure(Throwable error) {
        this.frameCoordinator.noteChainInputFlushFailure();
        long failures = this.frameCoordinator.chainInputFlushFailures();
        if (failures <= 3) {
            System.out.println("[Celtium] CELTIUM_CHAIN_INPUT_FLUSH_FAILED failures="
                    + failures + " error=" + Lane2ImmediateRecorder.describe(error));
        }
    }

    public int celtium$flushPendingForChainInput() {
        // M-6 wired (FL-1 F-6): the chain-input boundary records into the single flush census
        // at this entry.
        // The chain input's flush orders the pending work before the chain reads it; it is an
        // ordering flush, not a draw boundary (the window census' stamp).
        this.funnel.pendingWindowTrigger = FramePassTimings.WINDOW_TRIGGER_ORDER;
        // The body the PostChainBridge used to run for this entry, seated here with the bridge's
        // retirement (ruling step 5): the model path coalesces its own flushes to one per target
        // change, so the batches that arrived after that first flush are still queued at chain
        // time - this records them against the current target before the chain reads it.
        if (this.frameCoordinator.gpuStalled || this.funnel.immediateWindowBusy) {
            return 0;
        }
        int pending = this.funnel.countGroupsForTarget(this.funnel.targetRegistry.currentIdentity());
        if (pending == 0) {
            return 0;
        }
        this.funnel.celtium$flushPendingForGlDrawCause(GlDrawWindowCensus.CAUSE_REPLAY_OFFTARGET);
        if (this.chainInputBatchLogs <= 12) {
            System.out.println("[Celtium] CELTIUM_CHAIN_INPUT_FLUSH frame=" + this.frameCoordinator.frameId
                    + " target=" + this.funnel.targetRegistry.currentIdentity() + " groups=" + pending
                    + " recordedTotal=" + this.funnel.immediateStatistics.immediateRecordedBatches());
        }
        return pending;
    }

    public void compositeFramebuffer(long identity, boolean main, int texture, int sourceWidth, int sourceHeight,
            int textureWidth, int textureHeight, int width, int height, boolean opaque) {
        this.funnel.framebufferCompositor.submit(identity, main, texture, sourceWidth, sourceHeight,
                textureWidth, textureHeight, width, height, opaque);
    }

    public boolean celtium$drawModelList(int listId, int boundTextureId) {
        return this.funnel.modelReplay.celtium$drawModelList(listId, boundTextureId);
    }



    /**
     * {@return a machine-readable summary of what this backend actually drew}. The fixture reads it to decide
     * whether a run really rendered terrain: selecting the backend by name is not evidence that any pass reached the
     * queue, so submissions, skips and errors are counted instead.
     */
    public String verificationSummary() {
        return this.reports.verificationSummary();
    }

    /** Frames presented from Vulkan, and the attempts that did not reach the queue. */
    int framePresentedFromVulkan;

    /**
     * Exceptions the swap point's two catches swallowed (drawpath audit DP-14). Static, because the
     * catches sit in the mixin and the second of them guards the lookup of the current backend itself,
     * so no instance is known at the seat. A present that threw every frame used to be
     * indistinguishable from a present that was never attempted - the window froze, or fell back to
     * the GL swap, with no record anywhere - because both catches discarded the exception without a
     * count or a line. Printed on the verification summary as {@code swapPointExceptions} and reported
     * to stderr on the cadence {@link #celtium$swapPointExceptionReported(int)} pins.
     */
    public static int swapPointExceptions;


    /**
     * Counts and reports an exception the swap point swallowed (DP-14). The catches keep their
     * behaviour - the frame is still shown and the GL swap still runs - only the silence is removed.
     * The report goes to stderr rather than the bounded {@code [Celtium]} log, whose per-tag line
     * limit is exactly what a per-frame failure would exhaust first.
     */
    public static void celtium$noteSwapPointException(String seat, Throwable error) {
        swapPointExceptions++;
        if (celtium$swapPointExceptionReported(swapPointExceptions)) {
            System.err.println("[Celtium] CELTIUM_SWAPPOINT_EXCEPTION seat=" + seat
                    + " count=" + swapPointExceptions + " error=" + error);
        }
    }


    /**
     * {@return whether the swap-point exception with this count is reported}: the first three, then
     * every 300th - the CELTIUM_PRESENT heartbeat's cadence, so a failure that starts mid-run is named
     * when it starts and its persistence stays visible without a line per frame. Pure, so the cadence
     * the production path takes is the one a test can pin.
     */
    static boolean celtium$swapPointExceptionReported(int count) {
        return count <= 3 || count % 300 == 0;
    }

    int chainInputBatchLogs;


    /** The model mesh table, its builder and the lending rule (refactoring plan R3a). */
    final ModelGeometryCache modelGeometry = new ModelGeometryCache();

    final PassExecutor nativePassExecutor;

    /** Pending groups destined for a target that is not the main one, and where they were seen to disappear. */
    int otherTargetGroupsDiscarded;


    /** The viewport key that means "the whole render target", packed the same way {@code viewport} packs it. */


    /**
     * Ends and defers the frame's unified command buffer - the frame's single submission, carrying every recording
     * the frame made. The coordinator clears {@link #frameBufferOpen} itself; the boundary then flushes the batch,
     * which is the ONE vkQueueSubmit the frame pays.
     */
    public void celtium$finishFrameBuffer() {
        if (!this.frameCoordinator.frameBufferOpen) {
            return;
        }
        // The recording ends here: whatever records next is a new recording,
        // so Tier-A streams armed into this one stop being issuable (INV-2c).
        this.funnel.recordingEpoch++;
        GpuPassTimingReport.printIfDue(this.frameCoordinator.frameId, this.frameCoordinator.passTimings, this.frameCoordinator.packPassTimings,
                this.frameCoordinator.immediatePassGpuNanos, this.frameCoordinator.frameGpuNanos,
                this.frameCoordinator.frameHeadNanos, this.frameCoordinator.frameTailNanos, this.frameCoordinator.windowGpuCount, this.frameCoordinator.windowGpuMainNanos,
                this.frameCoordinator.windowGpuOffNanos, this.frameCoordinator.windowGpuMaxNanos, this.frameCoordinator.gpuMarkTimeline);
        this.funnel.celtium$reportFlushReasons(false);
        this.funnel.celtium$reportWindowSplitIfDue();
        DrawCounters.reportIfDue(this.frameCoordinator.frameId);
        BackendCallCounters.reportIfDue(this.frameCoordinator.frameId);
        // The native pack frame seat closes with the frame buffer it was opened on -
        // in a finally, so an exception escaping the coordinator's finish cannot leave
        // the seat holding this frame's command buffer into the next frame (D7 seat
        // ruling: endNativeFrame on every exit path, exception escape included).
        try {
            this.frameCoordinator.celtium$finishFrameBuffer();
        } finally {
            org.soutalab.celtium.vulkan.pack.ShaderPackRuntime.endNativeFrame();
        }
    }


    /**
     * Prints the counter summary once per trigger class, naming what triggered it.
     * <p>
     * It is reachable from the F3 debug list, but that list is only built when the Celeritas section of the overlay
     * is drawn - a leg with the overlay requested printed every counter as absent rather than zero, which is the
     * confusion this whole summary exists to prevent. World teardown is the second trigger, and the frame counter is
     * the third because a fixture that exits without tearing the world down never reaches the second.
     * <p>
     * The bounded triggers ({@code frame 120}, {@code present 120}) are <em>snapshots</em> so a leg that dies early
     * still leaves a counter set to read, and they do not suppress the teardown print: the two are different readings
     * and an acceptance reads the <em>last</em> {@code CELTIUM_VERIFY} line in the log. Measured (round 166): reading
     * the {@code present 120} snapshot as the verdict is how {@code scissorCalls=0} was read as "the GUI never
     * clipped" for three rounds, while in the same run the video options screen opened its list ~90 presents after
     * that snapshot and did clip - the picture shows the rows cut at the forced viewport and the scrollbar drawn.
     */
    /**
     * Prints the counter summary now, for a fixture that is about to end the run.
     * <p>
     * The other triggers cannot be relied on for the final reading: {@code frame 120} and {@code present 120} are
     * bounded snapshots a slow leg never reaches (a 640x480 video-settings leg of round 166 reached 86 presents and
     * printed nothing at all), world teardown is skipped by a fixture that exits with its world still loaded, and a
     * JVM shutdown hook's output lands after the log has been closed - measured, the hook printed into nothing.
     * The fixture is the one component that knows the run is ending while the log is still alive, so it asks for the
     * line through the backend call it already uses for reflection. An acceptance reads the LAST line.
     *
     * @return always {@code true}, so a reflective caller can tell "printed" from "no backend"
     */
    public boolean printCounterSummaryNow(String trigger) {
        this.funnel.celtium$printVerificationSummary(trigger, false);
        // The per-frame split's session summary, for the same reason the counter summary is printed here: world
        // teardown is skipped by a fixture that exits with its world still loaded, and the teardowns that do run
        // happen before the measured world has drawn a frame - measured on leg r50c, where both teardown summaries
        // read frames=0 while the periodic lines in the same log reached frames=960. The fixture is the component
        // that knows the run is ending while the log is alive, so the last summary is the one it asks for.
        FrameBudgetTimings.printSummary();
        // The HUD census' final line, printed with the other summaries for the same reason: its
        // periodic lines stop at the frame bound, and a leg that ends before the bound still has a
        // census to read.
        if (HudCensus.enabled()) {
            System.out.println("[Celtium] " + HudCensus.summary());
        }
        return true;
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
    /**
     * The quiescence half of the terrain teardown gate (T3 design section
     * 5.2): nothing is being recorded, nothing recorded is still queued
     * unsubmitted, and every submission is confirmed complete. Destruction
     * below - the terrain host's paired reset above all - is sound only
     * under all three.
     */
    boolean celtium$terrainQuiescent() {
        return !this.context.celtium$recordingOpen()
                && !this.context.celtium$hasPendingBatchedSubmits()
                && this.context.celtium$isSubmissionCompleted();
    }

    @Override
    public void celtium$releaseWorldResources() {
        // The gate at the door (T3 design section 5.2, review M4): this
        // teardown destroys what in-flight work may still name, so it first
        // composes quiescence + completion from the context. The caller's own
        // settle normally leaves the stream closed already; when it does not,
        // one settle is attempted here - settleGpu finishes any open frame
        // buffer and flushes the queued batches before it waits (M4), and
        // settleDeferredSubmits then observes the fences the wait confirmed,
        // which is what the completion half reads on a fence-path device.
        // Still not quiescent after that, nothing destructive runs: the
        // per-world arming state is detached and the method returns, leaving
        // the resources for the device teardown that follows - a leak on a
        // stalled device, never a destroy under the GPU.
        if (!celtium$terrainQuiescent()) {
            // Only a settle that actually confirmed the device idle earns the
            // fence observation; on a stalled or lost device the refusal
            // below is the whole answer, exactly as in delete()'s own head.
            if (this.frameCoordinator.settleGpu("world teardown")) {
                this.context.celtium$settleDeferredSubmits();
            }
        }
        if (!celtium$terrainQuiescent()) {
            this.reports.record("world teardown refused: the submission stream is not quiescent (recordingOpen="
                    + this.context.celtium$recordingOpen()
                    + " pendingBatchedSubmits=" + this.context.celtium$hasPendingBatchedSubmits()
                    + " submissionCompleted=" + this.context.celtium$isSubmissionCompleted()
                    + "); world resources are detached, not destroyed");
            return;
        }
        // The counter summary is printed here as well as through the F3 debug list, because that list is only built when
        // the Celeritas section of the overlay is drawn - a leg with F3 requested but no such line printed every counter
        // as absent, which reads exactly like a counter that never fired. World teardown happens at least once per leg
        // (the fixture leaves the world before exiting), so this makes the summary reachable without depending on the
        // overlay, and prints once so a leg that tears down twice does not repeat it.
        this.funnel.celtium$printVerificationSummary("world teardown", false);
        // The per-frame split's session summary: printed here rather than at device teardown because the log is
        // already closed by then, and this is the last point a leg is guaranteed to reach while it is alive.
        FrameBudgetTimings.printSummary();
        // The font run's refusal/retention/draw-state census, for the same reason and at the same point. It is also
        // appended to the glyph capture's shutdown summary, but a shutdown hook's stdout is not in the log: measured on
        // leg r39a-gate, where CELTIUM_FONT_GLYPH_SUMMARY appears in no file of the run at all. World teardown is the
        // last point whose output the log actually holds.
        this.funnel.celtium$printFontRunCensus();
        // The window's fixed cost, in microseconds per window, when -Dceltium.vulkan.windowSplit asked for it. Printed
        // at the same point and for the same reason as the census above: world teardown is the last point whose output
        // the log actually holds.
        this.funnel.celtium$printWindowSplit();
        // Item 312 P0: the last HUD frame's census and the summary, at the same last-live point.
        this.funnel.celtium$flushHudP0Frame();
        this.funnel.celtium$printHudP0Summary();
        // The world's own lifetime ends here, and a queued group belongs to it: a captured model group's mesh is lent by
        // one of the model meshes released just below, so drawing such a group after this point reads freed geometry.
        // Undrawn groups are retired now rather than left for a draw that may never come, and the epoch bump keeps a
        // group accepted between here and the next world's adoption from being drawn under the old stamp.
        int orphans = this.funnel.immediateGroups.size();
        if (orphans > 0) {
            this.frameCoordinator.retiredImmediateGroups.addAll(this.funnel.immediateGroups);
            this.funnel.groupQueueMutation++;
            this.funnel.immediateGroups.clear();
            this.funnel.pendingImmediateIndices = 0;
            this.funnel.immediateStatistics.addGroupsDiscardedAtWorldTeardown(orphans);
            this.reports.record("world teardown retired " + orphans + " queued immediate group(s) that the world owned");
        }
        this.funnel.worldEpoch++;
        this.modelGeometry.releaseAfterWorldTeardown();
        // The off-target dedupe set keys on this world's framebuffer identities, which die with the world: cleared
        // here so the set holds one world's pairs rather than every world's (audit F2).
        this.modelOffTargetLogged.clear();
        int drained = VulkanRetireQueue.flushFor(this.context.device().address());
        if (drained > 0) {
            this.reports.record("world teardown destroyed " + drained + " retired resources; the window device stays");
        }
        this.funnel.releaseRetiredImmediateGroups();
        // The Lane 1 terrain residency is world-owned: the frontend's world release runs at this
        // same seat (S2-b), dropping every resident section while the device and swapchain stay.
        this.funnel.celtium$releaseLane1World();
        this.funnel.recordingEpoch = 0L;
        // The immediate caches are built against the terrain pass that was just destroyed, so they belong to this world
        // as much as the pipelines do: a state-keyed entry kept across a world change would name a dead render pass. The
        // queued groups that held references to them were retired above, so nothing here is still pointing at them.
        int immediate = this.funnel.celtium$releaseImmediatePipelines();
        if (immediate > 0) {
            this.reports.record("world teardown released " + immediate + " immediate pipeline(s) built for the world's render pass");
        }
        // Every render pass those pipelines owned is destroyed by now, and the frame framebuffers are keyed by those
        // passes: a driver is free to hand the same handle value to the next world's pass, and the cache would then
        // answer with a framebuffer built for a pass that no longer exists (item 372, hole 2). The UI pass survives a
        // world change, but its entry goes with the rest - re-creating one command on the next world's first window is
        // cheaper than a rule that has to know which keys are still live. Retired, not destroyed: a recording can still
        // name these handles.
        if (this.funnel.framebufferCache != null) {
            int frameFramebuffers = this.funnel.framebufferCache.retireFrameFramebuffers();
            if (frameFramebuffers > 0) {
                this.reports.record("world teardown retired " + frameFramebuffers
                        + " frame-target framebuffer(s) keyed by the destroyed render passes");
            }
        }
        // OPT-05: the frame arena's buffers are released here, not left for the next world. Every group that held a
        // slice has been retired above (this path runs after a wait for device idle that succeeded), so nothing the
        // queue may still read is inside them; keeping them would carry one world's immediate memory into the next.
        if (this.frameCoordinator.frameArena != null) {
            this.frameCoordinator.frameArena.delete();
            this.frameCoordinator.frameArena = null;
            this.frameCoordinator.submissionStatistics.setFrameArenaFallbackGroups(0L);
        }
        this.reports.record("world teardown: terrain resources released, the window device and swapchain are kept");
    }


    /**
     * Takes this backend out of the current-backend registration and restores the GL batch factory, each only
     * while what is installed is still this backend's own (BH10-13): when a second backend exists (the factory's
     * own scenario, observed with modded RenderGlobals), the newer one is registered by the time the older one is
     * deleted, and clearing unconditionally would erase the live backend's registration and leave the mixins with
     * no Vulkan backend at all. uninstall() applies the same identity rule to the batch factory.
     * <p>
     * Every delete() exit that retires this instance runs this, including the stalled / settle-failed early
     * returns (BH10-07): those paths deliberately leave the GPU resources for process exit, but the registration
     * is plain Java state, and leaving it pointing at a backend its owner believes deleted keeps the mixins
     * drawing into a stopped GPU backend until the factory happens to install a successor. The windowOwned exit
     * is the one exception: that instance stays alive as the window backend, so its registration must stay.
     */
    void celtium$detachRegistration() {
        if (org.soutalab.celtium.vulkan.CeltiumBackends.current() == this) {
            org.soutalab.celtium.vulkan.CeltiumBackends.install(null);
        }
    }

    public void delete() {
        this.presentSwapchain.freeCachedWindowBegin();
        if (this.funnel.cachedTerrainBegin != null) {
            this.funnel.cachedTerrainBegin.free();
            this.funnel.cachedTerrainBegin = null;
        }
        if (this.funnel.cachedTerrainBeginArea != null) {
            this.funnel.cachedTerrainBeginArea.free();
            this.funnel.cachedTerrainBeginArea = null;
        }
        if (this.funnel.groupViewportScratch != null) {
            this.funnel.groupViewportScratch.free();
            this.funnel.groupViewportScratch = null;
        }
        if (this.funnel.groupScissorScratch != null) {
            this.funnel.groupScissorScratch.free();
            this.funnel.groupScissorScratch = null;
        }
        if (this.fullyDeleted) {
            // The full teardown already ran to its end once: nothing here is left to release, and running
            // the settle again would wait on a device its owner may since have deleted. The registration
            // detach is idempotent, so repeating it is the whole of a second delete (issue #425).
            celtium$detachRegistration();
            return;
        }
        if (this.frameCoordinator.gpuStalled) {
            // A stalled GPU means the last command buffer may never have been begun and never submitted, so its recorded
            // references are still live. Destroying the frame target or its framebuffers here is what validation reported as
            // "in use by VkCommandBuffer": on this path the resources are left for process exit instead.
            this.frameCoordinator.submissionStatistics.noteStalledTeardownSkips();
            celtium$detachRegistration();
            return;
        }
        // Nothing may be released while a submission is still reading it: the audit reproduced
        // vkDestroyBuffer/-Pipeline/-DescriptorPool/-RenderPass/-Framebuffer while a command buffer was in use, on the
        // reload and world-leave paths. Ending the queue's work first is the difference between freeing a resource and
        // pulling it out from under the GPU.
        if (!this.frameCoordinator.settleGpu("backend delete")) {
            // Stalled: releasing the backend here would destroy objects the recorded command buffer still references.
            celtium$detachRegistration();
            return;
        }
        try {
            celtium$deleteSettled();
        } catch (RuntimeException | LinkageError failure) {
            // One failing release must not strand the rest or the registration (issue #425): before this
            // catch, a throw out of the sequence skipped every later release AND the detach, leaving the
            // current-backend registration pointing at a half-deleted backend the mixins kept drawing
            // into (the state BH10-07 names). The failure is recorded, the registration is detached so
            // nothing draws into this backend again, and what the sequence did not reach is left for the
            // process teardown - the same disposition as the stalled and settle-failed exits above. The
            // window-owned exit inside the sequence is a return, not a throw, and is unaffected.
            this.reports.record("teardown: the release sequence stopped early (" + Lane2ImmediateRecorder.describe(failure)
                    + "); the resources it did not reach are left for the process teardown");
            celtium$detachRegistration();
        }
    }


    /**
     * Closes one bracketed segment of {@link #celtium$deleteSettled()} and {@return the instant the next
     * segment starts}. A segment slower than 50 ms - the frame boundary's slow threshold, the
     * {@code CELTIUM_BOUNDARY_SLOW} line's - prints one {@code CELTIUM_DELETE_SETTLED_SLOW} line naming it;
     * a fast segment prints nothing and costs one {@code nanoTime} read. Item 675's desk separation placed a
     * same-JVM re-entry stall somewhere in this sequence's head (the one thread-dump sample sat in
     * {@code drawRecords}' {@code vkFreeMemory}), but two dumps 66 s apart cannot say whether one segment
     * stalled or several slow frees accumulated - these brackets are what separates the two on the next leg,
     * segment by segment, without waiting for a dump.
     */
    static long celtium$deleteSegment(String segment, long segmentStartedNanos) {
        long now = System.nanoTime();
        long took = now - segmentStartedNanos;
        if (took > 50_000_000L) {
            System.out.println("[Celtium] CELTIUM_DELETE_SETTLED_SLOW segment=" + segment
                    + " millis=" + (took / 1_000_000L));
        }
        return now;
    }


    /**
     * The release sequence {@link #delete()} runs once the device has settled: every resource this backend
     * owns, in dependency order. Split from {@code delete()} so the caller can hold the whole sequence to
     * one contract - a failure anywhere in it is recorded and ends the teardown, it does not propagate -
     * while the window-owned exit in the middle stays a plain return. Reaching the end of this method is
     * what {@link #fullyDeleted} records; the window-owned return deliberately does not reach it.
     * <p>
     * The head of the sequence - the indirect pipelines and commands, {@code drawRecords},
     * {@code modelInstances}, the native pass executor, the texture array, and the window-owned exit's
     * {@code celtium$releaseWorldResources()} - is bracketed by {@link #celtium$deleteSegment}: a slow
     * segment names itself in the log instead of hiding inside the gap before
     * {@code CELTIUM_BACKEND_DELETE} prints.
     */
    void celtium$deleteSettled() {
        long segmentStarted = System.nanoTime();
        this.funnel.recordsPipelines.celtium$releaseAll();
        segmentStarted = celtium$deleteSegment("indirect", segmentStarted);
        if (this.funnel.drawRecords != null) {
            this.funnel.drawRecords.delete();
            this.funnel.drawRecords = null;
        }
        segmentStarted = celtium$deleteSegment("drawRecords", segmentStarted);
        if (this.funnel.modelInstances != null) {
            this.funnel.modelInstances.delete();
            this.funnel.modelInstances = null;
        }
        this.funnel.openModelRunGroup = null;
        segmentStarted = celtium$deleteSegment("modelInstances", segmentStarted);
        this.nativePassExecutor.delete();
        celtium$deleteSegment("nativePass", segmentStarted);
        this.deleteCalls++;
        System.out.println("[Celtium] CELTIUM_BACKEND_DELETE backend@" + Integer.toHexString(System.identityHashCode(this))
                + " " + celtium$ownershipSummary());
        if (this.windowOwned) {
            // The world renderer is leaving, not the window. Release what the world owns, detach the world's objects and
            // keep the device, the frame target and the swapchain: the menu and the loading screens draw through them,
            // and the next world adopts the same instance again (VulkanTerrainFactory.create checks
            // celtium$isWindowBackend()). Without this branch a quit to the title screen deletes the device and the menu
            // has nothing to draw with, which is the state task-15 exists to remove.
            long worldStarted = System.nanoTime();
            celtium$releaseWorldResources();
            celtium$deleteSegment("releaseWorldResources", worldStarted);
            this.windowBackend = true;
            return;
        }
        this.loadingSurface.delete();
        // The model meshes belong to this backend and hold buffers this device owns. settleGpu succeeded just above, so
        // the last submission has completed and nothing more will be recorded: the release is immediate and counted as
        // teardown, not as a free without a completed wait (audit N02).
        this.modelGeometry.releaseAll();
        // Teardown: the device has been settled above, so anything still retired can be destroyed now.
        int drained = VulkanRetireQueue.flushFor(this.context.device().address());
        if (drained > 0) {
            this.reports.record("teardown destroyed " + drained + " retired resources");
        }
        this.textureCopyService.delete();
        this.funnel.releaseRetiredImmediateGroups();
        celtium$detachRegistration();
        if (this.lane1 != null) {
            this.lane1.delete();
            this.lane1 = null;
        }
        if (this.funnel.uiRenderPass != 0) {
            org.lwjgl.vulkan.VK10.vkDestroyRenderPass(this.context.device(), this.funnel.uiRenderPass, null);
            this.funnel.uiRenderPass = 0;
        }
        for (long retiredPass : this.retiredUiRenderPasses) {
            if (retiredPass != 0) {
                org.lwjgl.vulkan.VK10.vkDestroyRenderPass(this.context.device(), retiredPass, null);
            }
        }
        this.retiredUiRenderPasses.clear();
        if (this.funnel.chainPairRenderPass != 0) {
            org.lwjgl.vulkan.VK10.vkDestroyRenderPass(this.context.device(), this.funnel.chainPairRenderPass, null);
            this.funnel.chainPairRenderPass = 0;
        }
        for (int count = 2; count < this.funnel.chainPairRenderPassesByCount.length; count++) {
            if (this.funnel.chainPairRenderPassesByCount[count] != 0) {
                org.lwjgl.vulkan.VK10.vkDestroyRenderPass(this.context.device(),
                        this.funnel.chainPairRenderPassesByCount[count], null);
                this.funnel.chainPairRenderPassesByCount[count] = 0;
            }
        }
        for (long variantPass : this.funnel.chainPairVariantPasses.values()) {
            if (variantPass != 0) {
                org.lwjgl.vulkan.VK10.vkDestroyRenderPass(this.context.device(), variantPass, null);
            }
        }
        this.funnel.chainPairVariantPasses.clear();
        for (long framePass : this.funnel.frameVariantPasses.values()) {
            if (framePass != 0) {
                org.lwjgl.vulkan.VK10.vkDestroyRenderPass(this.context.device(), framePass, null);
            }
        }
        this.funnel.frameVariantPasses.clear();
        this.funnel.chainPairVariantColorCounts.clear();
        for (int slot = 0; slot < this.formatClassPipelinePasses.length; slot++) {
            if (this.formatClassPipelinePasses[slot] != 0) {
                org.lwjgl.vulkan.VK10.vkDestroyRenderPass(this.context.device(),
                        this.formatClassPipelinePasses[slot], null);
                this.formatClassPipelinePasses[slot] = 0;
            }
        }
        if (this.atlasTexture != null) {
            this.atlasTexture.delete();
            this.atlasTexture = null;
        }
        if (this.funnel.lightmapTexture != null) {
            this.funnel.lightmapTexture.delete();
            this.funnel.lightmapTexture = null;
        }
        if (this.frameCoordinator.readbackMapped != 0) {
            vkUnmapMemory(this.context.device(), this.frameCoordinator.readbackMemory);
            this.frameCoordinator.readbackMapped = 0;
        }
        if (this.frameCoordinator.readbackBuffer != 0) {
            vkDestroyBuffer(this.context.device(), this.frameCoordinator.readbackBuffer, null);
            this.frameCoordinator.readbackBuffer = 0;
        }
        if (this.frameCoordinator.readbackMemory != 0) {
            VulkanContext.freeMemory(this.context.device(), this.frameCoordinator.readbackMemory);
            this.frameCoordinator.readbackMemory = 0;
        }
        this.funnel.destroyFramebuffer();
        if (this.funnel.frameTarget != null) {
            this.funnel.frameTarget.delete();
            this.funnel.frameTarget = null;
            // Groups recorded against the old target may still be queued; the generation is what lets the draw refuse them
            // instead of binding a render pass that no longer exists. Both the recreate path and the teardown path bump it.
            this.funnel.targetRegistry.noteFrameTargetReplaced();
        }
        this.frameCoordinator.destroyCommandResources();
        // Everything the backend owns is released here, not just the terrain half (audit S15): the immediate mesh, the
        // held groups, the immediate pipeline caches, the UI pipeline and mesh, the texture registry and the swapchain.
        int released = 0;
            this.otherTargetGroupsDiscarded += this.funnel.pendingGroupsForOtherTargets();
        try {
            this.funnel.discardImmediateGroups();
            released++;
        } catch (RuntimeException | LinkageError error) {
            this.reports.record("teardown: held groups could not be released: " + Lane2ImmediateRecorder.describe(error));
        }
        int pendingCopies = 0;
        if (this.funnel.retainedGeometry != null) {
            this.funnel.retainedGeometry.close();
            this.funnel.retainedGeometry = null;
        }

        released += this.funnel.releaseImmediateMesh(this.funnel.currentImmediateMesh);
        this.funnel.currentImmediateMesh = null;
        // The immediate pipeline caches, the isolation pass's pipeline and mesh, the descriptor probe and the UI test
        // quad's pipeline and mesh are one set: everything the immediate path built against a render pass. They are
        // released by the same call the world-leave path makes, so the two lifetimes cannot drift apart.
        released += this.funnel.celtium$releaseImmediatePipelines();
        if (this.funnel.textureRegistry != null) {
            this.funnel.textureRegistry.delete();
            this.funnel.textureRegistry = null;
            this.funnel.whiteTextureGlId = 0;
            released++;
        }
        if (this.funnel.swapchain != null) {
            this.funnel.swapchain.delete();
            this.funnel.swapchain = null;
            released++;
        }
        // The teardown above queues as it goes: the texture registry's textures, the frame target and the
        // swapchain hand their handles to the retire queue instead of destroying them in place, and they do so
        // after this sequence's own drain near its head. The device was settled at the head of delete() and is
        // still alive here, so this device's share can be destroyed now. Without this last drain those entries
        // outlive the device: nothing guarantees VulkanContext.delete() runs after this method, and a successor
        // backend's frame-path drain would otherwise execute them against a destroyed device (L06-01/BH10-09).
        int drainedLate = VulkanRetireQueue.flushFor(this.context.device().address());
        if (drainedLate > 0) {
            this.reports.record("teardown destroyed " + drainedLate + " resource(s) retired during the teardown itself");
        }
        System.out.println("[Celtium] CELTIUM_TEARDOWN released " + released
                + " backend resources (immediate mesh, groups, pipeline caches, UI, registry, swapchain)");
        this.fullyDeleted = true;
    }

    boolean ensureSwapchain() {
        return this.presentSwapchain.ensureSwapchain();
    }

    public String celtium$swapchainCreationFailure() {
        return this.presentSwapchain.celtium$swapchainCreationFailure();
    }

    void runSwapchainProbe() {
        this.presentSwapchain.runSwapchainProbe();
    }

    org.lwjgl.vulkan.VkRenderPassBeginInfo celtium$windowBeginInfo(long renderPass, long framebuffer,
                                                                          int width, int height) {
        return this.presentSwapchain.celtium$windowBeginInfo(renderPass, framebuffer, width, height);
    }

    public boolean celtium$presentFrame() {
        return this.presentSwapchain.celtium$presentFrame();
    }

    public void celtium$pollEvents() {
        this.presentSwapchain.celtium$pollEvents();
    }

    public boolean celtium$consumeSwapPendingFromVulkan() {
        return this.presentSwapchain.celtium$consumeSwapPendingFromVulkan();
    }

    public double celtium$presentedImageMean() {
        return this.presentSwapchain.celtium$presentedImageMean();
    }

    public boolean celtium$dumpPresentedImage(java.nio.file.Path path) {
        return this.presentSwapchain.celtium$dumpPresentedImage(path);
    }

    final java.util.Set<String> modelOffTargetLogged = new java.util.HashSet<String>();


    /**
     * The texture-copy path's service, built by {@link #copyReadSide}. Kept as a field under this name
     * because the frame summary, the diagnostics and the teardown read it here - one of them reflectively.
     */
    final TextureCopyService textureCopyService;

    void celtium$dumpOwnedImage(String stage, long image, String base, String identity, int width,
            int height) {
        this.copyReadSide.celtium$dumpOwnedImage(stage, image, base, identity, width, height);
    }

    int[] celtium$readImageRectangle(long image, int x, int y, int width, int height) {
        return this.copyReadSide.celtium$readImageRectangle(image, x, y, width, height);
    }

    public boolean celtium$presentedImageDumpWritten() {
        return this.copyReadSide.celtium$presentedImageDumpWritten();
    }

    public int celtium$presentedFrameCount() {
        return this.copyReadSide.celtium$presentedFrameCount();
    }

    public int celtium$presentSkips() {
        return this.copyReadSide.celtium$presentSkips();
    }

    public int celtium$presentAttempts() {
        return this.copyReadSide.celtium$presentAttempts();
    }

    public int celtium$presentHookCalls() {
        return this.copyReadSide.celtium$presentHookCalls();
    }

    public int celtium$presentedDumpWrittenFrameFor(String fileName) {
        return this.copyReadSide.celtium$presentedDumpWrittenFrameFor(fileName);
    }

    public void celtium$setLightmapUnit(int unit) {
        this.copyReadSide.celtium$setLightmapUnit(unit);
    }

    @Override
    public void setLightmapCoords(float x, float y) {
        this.copyReadSide.setLightmapCoords(x, y);
    }

    float[] lightmapCoords() {
        return this.copyReadSide.lightmapCoords();
    }

    float[] celtium$lightmapForGroup() {
        return this.copyReadSide.celtium$lightmapForGroup();
    }

    public boolean celtium$ensureTargetForBoundFramebuffer() {
        return this.copyReadSide.celtium$ensureTargetForBoundFramebuffer();
    }

    public boolean celtium$ensureTargetForBoundFramebuffer(int widthHint, int heightHint) {
        return this.copyReadSide.celtium$ensureTargetForBoundFramebuffer(widthHint, heightHint);
    }

    public int celtium$adoptableFramebuffer(int width, int height) {
        return this.copyReadSide.celtium$adoptableFramebuffer(width, height);
    }

    public int celtium$adoptedColorTexture() {
        return this.copyReadSide.celtium$adoptedColorTexture();
    }

    public int celtium$adoptedDepthTexture() {
        return this.copyReadSide.celtium$adoptedDepthTexture();
    }

    public void celtium$noteFramebufferAdopted() {
        this.copyReadSide.celtium$noteFramebufferAdopted();
    }

    public boolean celtium$isSharedFramebuffer(int framebufferObject) {
        return this.copyReadSide.celtium$isSharedFramebuffer(framebufferObject);
    }

    public void celtium$noteGlErrorAt(String where, int error) {
        this.copyReadSide.celtium$noteGlErrorAt(where, error);
    }

    VulkanTextureRegistry celtium$registry() {
        return this.copyReadSide.celtium$registry();
    }

    public String celtium$textureCensus(int glTextureId) {
        return this.copyReadSide.celtium$textureCensus(glTextureId);
    }

    public String celtium$immediateMatrixState() {
        return this.copyReadSide.celtium$immediateMatrixState();
    }

    boolean ensureFrameTarget() {
        return this.copyReadSide.ensureFrameTarget();
    }

    boolean ensureFrameTarget(int width, int height) {
        return this.copyReadSide.ensureFrameTarget(width, height);
    }

    void refreshTextures() {
        this.copyReadSide.refreshTextures();
    }

    /** {@return the Lane 2 recording funnel (the seat the direct-GL capture entry records through). */
    public Lane2ImmediateRecorder funnel() {
        return this.funnel;
    }

    public void celtium$noteFramebufferHintFailure(String seat, Throwable error) {
        this.funnel.celtium$noteFramebufferHintFailure(seat, error);
    }

    public void celtium$setFontPreFlush(Runnable hook) {
        this.funnel.celtium$setFontPreFlush(hook);
    }

    public void celtium$setFontCaptureWork(java.util.function.BooleanSupplier probe) {
        this.funnel.celtium$setFontCaptureWork(probe);
    }

    public void celtium$setFontCaptureSummary(java.util.function.Supplier<String> supplier) {
        this.funnel.celtium$setFontCaptureSummary(supplier);
    }

    @Override
    public void setBlendEnabled(boolean enabled) {
        this.funnel.setBlendEnabled(enabled);
    }

    @Override
    public void setBlendFunc(int sourceRgb, int destRgb, int sourceAlpha, int destAlpha) {
        this.funnel.setBlendFunc(sourceRgb, destRgb, sourceAlpha, destAlpha);
    }

    @Override
    public void blendEquation(int rgbEquation, int alphaEquation) {
        this.funnel.blendEquation(rgbEquation, alphaEquation);
    }

    @Override
    public void colorMask(int mask) {
        this.funnel.colorMask(mask);
    }

    public float[] celtium$lightDirections() {
        return this.funnel.celtium$lightDirections();
    }

    public float[] celtium$lightDiffuse() {
        return this.funnel.celtium$lightDiffuse();
    }

    public boolean celtium$depthWritesLive() {
        return this.funnel.celtium$depthWritesLive();
    }

    @Override
    public void setDepthMask(boolean mask) {
        this.funnel.setDepthMask(mask);
    }

    @Override
    public void setDepthTest(boolean enabled) {
        this.funnel.setDepthTest(enabled);
    }

    @Override
    public void setAlphaFunc(int glFunc, float reference) {
        this.funnel.setAlphaFunc(glFunc, reference);
    }

    @Override
    public void setAlphaTestEnabled(boolean enabled) {
        this.funnel.setAlphaTestEnabled(enabled);
    }

    @Override
    public void setLightingEnabled(boolean enabled) {
        this.funnel.setLightingEnabled(enabled);
    }

    @Override
    public void setLightmapUnitEnabled(boolean enabled) {
        this.funnel.setLightmapUnitEnabled(enabled);
    }

    @Override
    public void setLightEnabled(int index, boolean enabled) {
        this.funnel.setLightEnabled(index, enabled);
    }

    @Override
    public void setLightPosition(int index, float x, float y, float z) {
        this.funnel.setLightPosition(index, x, y, z);
    }

    @Override
    public void setLightDiffuse(int index, float red, float green, float blue) {
        this.funnel.setLightDiffuse(index, red, green, blue);
    }

    @Override
    public void setLightAmbient(float red, float green, float blue) {
        this.funnel.setLightAmbient(red, green, blue);
    }

    @Override
    public void setShadeModel(int glMode) {
        this.funnel.setShadeModel(glMode);
    }

    @Override
    public void setFogEnabled(boolean enabled) {
        this.funnel.setFogEnabled(enabled);
    }

    @Override
    public void setFogMode(int glMode) {
        this.funnel.setFogMode(glMode);
    }

    @Override
    public void setFogDensity(float density) {
        this.funnel.setFogDensity(density);
    }

    @Override
    public void setFogStart(float start) {
        this.funnel.setFogStart(start);
    }

    @Override
    public void setFogEnd(float end) {
        this.funnel.setFogEnd(end);
    }

    @Override
    public void setFogColor(float red, float green, float blue) {
        this.funnel.setFogColor(red, green, blue);
    }

    @Override
    public void setFogRadial(boolean radial) {
        this.funnel.setFogRadial(radial);
    }

    @Override
    public boolean immediateFogAvailable() {
        return this.funnel.immediateFogAvailable();
    }

    public void announceDynamicTexture(int glTextureId, int[] pixels, int width, int height) {
        this.funnel.announceDynamicTexture(glTextureId, pixels, width, height);
    }

    public void announceDynamicTexture(int glTextureId, int[] pixels, int width, int height, boolean fromSplash) {
        this.funnel.announceDynamicTexture(glTextureId, pixels, width, height, fromSplash);
    }

    @Override
    public void announceTextureUpload(int glTextureId, java.awt.image.BufferedImage image) {
        this.funnel.announceTextureUpload(glTextureId, image);
    }

    @Override
    public void beginAnimationUploads(int glTextureId) {
        this.funnel.beginAnimationUploads(glTextureId);
    }

    @Override
    public void endAnimationUploads() {
        this.funnel.endAnimationUploads();
    }

    @Override
    public void announceAnimatedUpload(int[][] data, int width, int height, int x, int y) {
        this.funnel.announceAnimatedUpload(data, width, height, x, y);
    }

    @Override
    public void invalidateTexture(int glTextureId) {
        this.funnel.invalidateTexture(glTextureId);
    }

    public void onGlTextureReallocated(int glTextureId) {
        this.funnel.onGlTextureReallocated(glTextureId);
    }

    public void onGlTextureReallocated(int glTextureId, int width, int height) {
        this.funnel.onGlTextureReallocated(glTextureId, width, height);
    }

    @Override
    public void invalidateBoundTexture() {
        this.funnel.invalidateBoundTexture();
    }

    @Override
    public void setTexture2dEnabled(boolean enabled) {
        this.funnel.setTexture2dEnabled(enabled);
    }

    @Override
    public void bindTexture(int glTextureId) {
        this.funnel.bindTexture(glTextureId);
    }

    @Override
    public boolean skipUnchangedBind(int glTextureId) {
        return this.funnel.skipUnchangedBind(glTextureId);
    }

    @Override
    public void setColor(float red, float green, float blue, float alpha) {
        this.funnel.setColor(red, green, blue, alpha);
    }

    @Override
    public void matrixMode(int glMode) {
        this.funnel.matrixMode(glMode);
    }

    @Override
    public void loadIdentity() {
        this.funnel.loadIdentity();
    }

    @Override
    public void ortho(double left, double right, double bottom, double top, double near, double far) {
        this.funnel.ortho(left, right, bottom, top, near, far);
    }

    @Override
    public void translate(double x, double y, double z) {
        this.funnel.translate(x, y, z);
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        this.funnel.rotate(angle, x, y, z);
    }

    @Override
    public void scale(float x, float y, float z) {
        this.funnel.scale(x, y, z);
    }

    @Override
    public void activeTextureUnit(int unit) {
        this.funnel.activeTextureUnit(unit);
    }

    @Override
    public void depthFunc(int func) {
        this.funnel.depthFunc(func);
    }

    @Override
    public void perspective(float fovDegrees, float aspect, float near, float far) {
        this.funnel.perspective(fovDegrees, aspect, near, far);
    }

    @Override
    public boolean mirrorGluPerspective(float fovDegrees, float aspect, float near, float far) {
        return this.funnel.mirrorGluPerspective(fovDegrees, aspect, near, far);
    }

    @Override
    public void frustum(double left, double right, double bottom, double top, double near, double far) {
        this.funnel.frustum(left, right, bottom, top, near, far);
    }

    @Override
    public void multiplyMatrix(java.nio.FloatBuffer buffer) {
        this.funnel.multiplyMatrix(buffer);
    }

    @Override
    public void pushMatrix() {
        this.funnel.pushMatrix();
    }

    @Override
    public void popMatrix() {
        this.funnel.popMatrix();
    }

    @Override
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
        this.funnel.seedMirrorFromCameraSetup();
    }

    public boolean celtium$isGpuStalled() {
        return this.frameCoordinator.celtium$isGpuStalled();
    }

    @Override
    public String lastRefusal() {
        return this.funnel.lastRefusal();
    }

    @Override
    public void flushBeforeClear(int mask) {
        this.funnel.flushBeforeClear(mask);
    }

    @Override
    public void blendFunc(int sourceFactor, int destinationFactor) {
        this.funnel.blendFunc(sourceFactor, destinationFactor);
    }

    public void celtium$noteBindSize(int width, int height, boolean mainFramebuffer) {
        this.funnel.celtium$noteBindSize(width, height, mainFramebuffer);
    }

    public void celtium$noteGameFramebuffer(long identity, int colorTexture, int depthTexture) {
        this.funnel.celtium$noteGameFramebuffer(identity, colorTexture, depthTexture);
    }

    public void celtium$forgetDeletedFramebuffer(long identity) {
        this.funnel.celtium$forgetDeletedFramebuffer(identity);
    }

    public void celtium$rawAttachmentTextureDeleted(int framebuffer) {
        this.funnel.celtium$rawAttachmentTextureDeleted(framebuffer);
    }

    public void celtium$rawFramebufferDeleted(int framebuffer, int colorTexture) {
        this.funnel.celtium$rawFramebufferDeleted(framebuffer, colorTexture);
    }

    public void celtium$rawTextureDeleted(int texture) {
        this.funnel.celtium$rawTextureDeleted(texture);
    }

    @Override
    public void bindFramebufferColorTexture(long framebufferIdentity, int colorTexture, int width, int height) {
        this.funnel.bindFramebufferColorTexture(framebufferIdentity, colorTexture, width, height);
    }

    public void celtium$noteFramebufferName(long identity, int framebufferObject) {
        this.funnel.celtium$noteFramebufferName(identity, framebufferObject);
    }

    @Override
    public void bindFramebuffer(long targetIdentity, boolean isMain) {
        this.funnel.bindFramebuffer(targetIdentity, isMain);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        this.funnel.viewport(x, y, width, height);
    }

    public void celtium$frameBoundary() {
        this.funnel.celtium$frameBoundary();
    }

    @Override
    public void beginWorldPass() {
        this.funnel.beginWorldPass();
    }

    @Override
    public void endWorldPass() {
        this.funnel.endWorldPass();
    }

    @Override
    public void captureWorldSnapshot() {
        this.funnel.captureWorldSnapshot();
    }

    public boolean celtium$fontAppend(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
            int vertexCount, int drawMode) {
        return this.funnel.celtium$fontAppend(vertexFormat, vertexStride, formatId, vertexAddress, vertexCount, drawMode);
    }

    public String celtium$fontBakeSummary() {
        return this.funnel.celtium$fontBakeSummary();
    }

    public String celtium$fontBatchSummary() {
        return this.funnel.celtium$fontBatchSummary();
    }

    public String celtium$fontRetentionSummary() {
        return this.funnel.celtium$fontRetentionSummary();
    }

    public String celtium$fontStateSummary() {
        return this.funnel.celtium$fontStateSummary();
    }

    public float[] celtium$mirroredModelView() {
        return this.funnel.celtium$mirroredModelView();
    }

    public float[] celtium$mirroredProjection() {
        return this.funnel.celtium$mirroredProjection();
    }

    @Override
    public void applyDerivedProjection(float fovDegrees, float aspect, float near, float far) {
        this.funnel.applyDerivedProjection(fovDegrees, aspect, near, far);
    }

    @Override
    public boolean submitImmediateBatch(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                        int vertexCount, int drawMode) {
        return this.funnel.submitImmediateBatch(vertexFormat, vertexStride, formatId, vertexAddress, vertexCount, drawMode);
    }

    @Override
    public boolean fixedFunctionLightingEnabled() {
        return this.funnel.fixedFunctionLightingEnabled();
    }

    @Override
    public boolean acceptsRetainedGeometry() {
        return this.funnel.acceptsRetainedGeometry();
    }

    @Override
    public boolean submitRetainedGeometry(Object geometry, int drawMode) {
        return this.funnel.submitRetainedGeometry(geometry, drawMode);
    }

    @Override
    public boolean texture2dEnabled() {
        return this.funnel.texture2dEnabled();
    }

    public void celtium$flushPendingAtFrameBoundary() {
        this.frameCoordinator.celtium$flushPendingAtFrameBoundary();
    }

    @Override
    public String getTransferQueueDebugString() {
        return this.funnel.getTransferQueueDebugString();
    }

    public String celtium$serveTextureCopy(int destName, int level, int xoffset, int yoffset,
            int x, int y, int width, int height, int readFbo) {
        return this.funnel.celtium$serveTextureCopy(destName, level, xoffset, yoffset, x, y, width, height, readFbo);
    }

    public String celtium$serveDepthCopy(int sourceDepthTexture, int destDepthTexture,
            int x, int y, int width, int height, int xoffset, int yoffset) {
        return this.funnel.celtium$serveDepthCopy(sourceDepthTexture, destDepthTexture, x, y, width, height, xoffset, yoffset);
    }

    public boolean celtium$readPixels(int readFbo, int x, int y, int width, int height,
            int format, int type, long address) {
        return this.funnel.celtium$readPixels(readFbo, x, y, width, height, format, type, address);
    }

    public boolean celtium$getTexImage(int textureName, int level, int format, int type, long address) {
        return this.funnel.celtium$getTexImage(textureName, level, format, type, address);
    }

    public boolean celtium$getTexImage(int textureName, int level, int format, int type, long address,
            long capacity) {
        return this.funnel.celtium$getTexImage(textureName, level, format, type, address, capacity);
    }

    @Override
    public void updateTimings() {
        this.frameCoordinator.updateTimings();
    }

}
