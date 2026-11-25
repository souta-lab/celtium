package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.lwjgl.vulkan.VK10;
import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexedIndirect;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The terrain pass's own resources and the regions it has uploaded (refactoring plan R4).
 * <p>
 * This is the first half of R4: the per-region arenas, the failed-upload ledger and the region lookup are one owner,
 * so "which region has an arena", "which sections failed to build" and "which arena serves this region's indices"
 * have a single place to be answered. The ordered draw path - {@code render}, {@code uploadRegion},
 * {@code recordRenderPass} - stays in the backend until the second half, because it reads the frame's command buffer,
 * the descriptor set and the pass state, and those are the backend's.
 * <p>
 * Celeritas' CPU side is untouched: this class holds what the upload wrote, not how the mesh was built, sorted or
 * compacted. The two counters it keeps are the audit's BH-25 evidence, which used to be invisible because a failed
 * upload left its sections marked built.
 */
public final class TerrainRenderer {

    /**
     * One region's resources: the renderer's shared arenas (agera-kpi-4), plus this region's own first-vertex
     * records. The arenas used to be per-region - one vertex buffer and one index buffer per region - which is
     * what capped the indirect path at one call per region batch: vkCmdDrawIndexedIndirect reads only the one
     * bound vertex/index buffer pair, so commands from two regions could never share a call. With every region
     * allocating from the same pair of arenas, segment offsets are unique across the whole world and a pass can
     * bind the pair once and chain every region's commands into a handful of calls.
     */
    static final class RegionResources {
        /** The renderer's shared vertex arena; not owned by this region. */
        final VulkanArena vertexArena;
        final int[] sectionFirstColor = new int[RenderRegion.REGION_SIZE];
        /** The first 32 bytes of the first vertex of each section, as the upload saw them. */
        final byte[][] sectionFirstVertex = new byte[RenderRegion.REGION_SIZE][];
        /** The renderer's shared index arena once any region has indices; not owned by this region. */
        VulkanArena indexArena;

        RegionResources(VulkanArena sharedVertexArena) {
            this.vertexArena = sharedVertexArena;
        }

        int bufferCount() {
            return 1 + (this.indexArena != null ? 1 : 0);
        }

        /** The arenas are shared and outlive the region; only the region's own reference is dropped. */
        void delete() {
            this.indexArena = null;
        }
    }

    /**
     * One mesh of an upload batch. Pooled: a burst creates one per pass per rebuilt section, and the fields are
     * cleared on release so a pooled instance cannot pin a section's buffers or a world.
     */
    static final class PendingMesh {
        private static final java.util.ArrayDeque<PendingMesh> POOL = new java.util.ArrayDeque<>();
        private static final int POOL_CAP = 512;

        int sectionIndex;
        TerrainRenderPass pass;
        BuiltSectionMeshParts parts;
        ChunkSortOutput.SortedMesh sorted;
        int[] sectionFirstColor;
        /** The region resources the diagnostic head belongs to, so logFirstVertex can fill it without a lookup. */
        TerrainRenderer.RegionResources firstVertexOwner;

        /** One mesh an upload produced and has not committed yet. */
        private PendingMesh() {
        }

        static PendingMesh obtain(int sectionIndex, TerrainRenderPass pass, BuiltSectionMeshParts parts,
                                  ChunkSortOutput.SortedMesh sorted, int[] sectionFirstColor,
                                  TerrainRenderer.RegionResources firstVertexOwner) {
            PendingMesh mesh = POOL.poll();
            if (mesh == null) {
                mesh = new PendingMesh();
            }
            mesh.sectionIndex = sectionIndex;
            mesh.pass = pass;
            mesh.parts = parts;
            mesh.sorted = sorted;
            mesh.sectionFirstColor = sectionFirstColor;
            mesh.firstVertexOwner = firstVertexOwner;
            return mesh;
        }

        /** Clears every reference before the instance goes back to the pool. */
        void release() {
            this.parts = null;
            this.sorted = null;
            this.sectionFirstColor = null;
            this.firstVertexOwner = null;
            this.pass = null;
            if (POOL.size() < POOL_CAP) {
                POOL.add(this);
            }
        }
    }

    private final Map<RenderRegion, RegionResources> regionResources = new ConcurrentHashMap<RenderRegion, RegionResources>();

    /**
     * The one vertex arena every region allocates from (agera-kpi-4), created on first use. The starting
     * capacity covers a render-distance scene's live geometry in one allocation - the per-region arenas it
     * replaces started at 4 MiB each and grew through chains of reallocations during chunk load - and the
     * arena's own doubling growth still applies past it. The stride is the one current at creation, exactly
     * as a per-region arena's was; a stride change across worlds is settled by {@link #releaseAll()}.
     */
    private VulkanArena sharedVertexArena;
    /** The one index arena every region allocates from, created on first use. */
    private VulkanArena sharedIndexArena;

    private static final int SHARED_VERTEX_ARENA_INITIAL_BYTES = 64 * 1024 * 1024;
    private static final int SHARED_INDEX_ARENA_INITIAL_BYTES = 16 * 1024 * 1024;

    /** {@return the shared vertex arena, creating it on first use}. */
    VulkanArena sharedVertexArena(VulkanContext context) {
        if (this.sharedVertexArena == null) {
            this.sharedVertexArena = new VulkanArena(context, SHARED_VERTEX_ARENA_INITIAL_BYTES,
                    VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, vertexStride());
        }
        return this.sharedVertexArena;
    }

    /** {@return the shared index arena, creating it on first use}. */
    VulkanArena sharedIndexArena(VulkanContext context) {
        if (this.sharedIndexArena == null) {
            this.sharedIndexArena = new VulkanArena(context, SHARED_INDEX_ARENA_INITIAL_BYTES,
                    VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 4);
        }
        return this.sharedIndexArena;
    }

    /** {@return the shared vertex arena when it exists, or null}. */
    VulkanArena sharedVertexArenaIfPresent() {
        return this.sharedVertexArena;
    }

    /** {@return the shared index arena when it exists, or null}. */
    VulkanArena sharedIndexArenaIfPresent() {
        return this.sharedIndexArena;
    }

    /**
     * Sections whose geometry the last {@code uploadMeshes} could not put on the GPU (BH-25).
     * <p>
     * Filled when a region's upload throws and drained by {@link VulkanTerrainGpuBackend#consumeFailedUploads()} on the caller's next line, so
     * the section manager can leave exactly those sections un-committed and queued for another build. A set rather than
     * a count because the caller needs the identity, and identity is the whole point: the section must not be marked
     * built.
     */
    private final Set<RenderSection> failedUploads = Collections.newSetFromMap(new IdentityHashMap<RenderSection, Boolean>());

    /**
     * Sections a failed region upload left un-built, counted for the acceptance (BH-25). Before the fix the same
     * failure left them marked built, so this number was invisible: it was zero because nothing distinguished the two.
     */
    private int sectionsLeftUnbuilt;

    /** {@return the region's resources, creating them on first use}. */
    RegionResources resourcesFor(VulkanContext context, RenderRegion region, int vertexStride) {
        RegionResources resources = this.regionResources.get(region);
        if (resources == null) {
            resources = new RegionResources(sharedVertexArena(context));
            this.regionResources.put(region, resources);
        }
        return resources;
    }

    /** {@return the region's resources when it has any, or null - the draw path must not create an arena}. */
    RegionResources resourcesIfPresent(RenderRegion region) {
        return this.regionResources.get(region);
    }

    /** {@return the shared index arena, named on the region's resources from first use}. */
    VulkanArena indexArena(VulkanContext context, RegionResources resources) {
        if (resources.indexArena == null) {
            resources.indexArena = sharedIndexArena(context);
        }
        return resources.indexArena;
    }

    /** Records a section whose upload failed, so the next pass rebuilds it. {@return whether it was new}. */
    boolean noteFailedUpload(RenderSection section) {
        return this.failedUploads.add(section);
    }

    /** {@return whether any failed upload is waiting}. */
    boolean failedUploadsEmpty() {
        return this.failedUploads.isEmpty();
    }

    /** {@return the failed uploads, for the rebuild pass to drain}. */
    Set<RenderSection> failedUploads() {
        return this.failedUploads;
    }

    /** {@return the regions that have resources}. */
    Iterable<RegionResources> regionResources() {
        return this.regionResources.values();
    }

    /** {@return the region entries, for the paths that need the region itself}. */
    Set<Map.Entry<RenderRegion, RegionResources>> regionEntries() {
        return this.regionResources.entrySet();
    }

    /** {@return whether the section's upload failed and it must be rebuilt}. */
    boolean isFailedUpload(RenderSection section) {
        return this.failedUploads.contains(section);
    }

    /** {@return how many failed uploads are waiting to be rebuilt}. */
    int failedUploadCount() {
        return this.failedUploads.size();
    }

    /** Records how many sections a failed upload left un-built. */
    void noteSectionsLeftUnbuilt(int count) {
        this.sectionsLeftUnbuilt += count;
    }

    /** {@return how many sections failed uploads have left un-built}. */
    int sectionsLeftUnbuilt() {
        return this.sectionsLeftUnbuilt;
    }

    /** {@return how many regions have resources}. */
    int regionCount() {
        return this.regionResources.size();
    }

    /** {@return how many arenas the regions hold: the shared pair, counted once}. */
    int arenaCount() {
        return (this.sharedVertexArena != null ? 1 : 0) + (this.sharedIndexArena != null ? 1 : 0);
    }

    /** Releases every region's resources and the shared arenas. Called when the world or the renderer goes away. */
    void releaseAll() {
        for (RegionResources resources : this.regionResources.values()) {
            resources.delete();
        }
        this.regionResources.clear();
        this.failedUploads.clear();
        if (this.sharedVertexArena != null) {
            this.sharedVertexArena.delete();
            this.sharedVertexArena = null;
        }
        if (this.sharedIndexArena != null) {
            this.sharedIndexArena.delete();
            this.sharedIndexArena = null;
        }
    }

    /**
     * Binds the region's vertex buffer. The command buffer is a parameter because it is the frame's, not this class's:
     * the terrain pass records into it but does not own it (refactoring plan R4, second half).
     */
    void bindVertexBuffer(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, org.lwjgl.system.MemoryStack stack,
            RegionResources resources) {
        java.nio.LongBuffer buffers = stack.mallocLong(1);
        java.nio.LongBuffer offsets = stack.mallocLong(1);
        offsets.put(0, 0L);
        bindVertexBuffer(commandBuffer, buffers, offsets, resources);
    }

    /** Zero-allocation overload reusing caller's pre-allocated 1-element vertex buffer/offset scratch buffers. */
    void bindVertexBuffer(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, java.nio.LongBuffer buffers,
            java.nio.LongBuffer offsets, RegionResources resources) {
        buffers.put(0, resources.vertexArena.buffer().buffer());
        org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers(commandBuffer, 0, buffers, offsets);
    }

    /**
     * Binds the shared vertex arena once for a whole pass (agera-kpi-4): with every region's geometry in the
     * one buffer, the per-region bind the draw loop used to record is the same handle every time, so the pass
     * binds it at its head and the chained indirect calls below read it throughout. A pass over a world with
     * no uploads has no arena and nothing to bind - and no batches to draw either.
     */
    void bindSharedVertexBuffer(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, java.nio.LongBuffer buffers,
            java.nio.LongBuffer offsets) {
        if (this.sharedVertexArena == null) {
            return;
        }
        buffers.put(0, this.sharedVertexArena.buffer().buffer());
        org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers(commandBuffer, 0, buffers, offsets);
    }


    /** The vertex and index buffers of the captured batch, taken with the range rather than at their bind sites. */

    /** Incremented each time a frame's batch is captured, so the owner can attach its own state at the same moment. */






    /** Time inside {@code vkCmdDrawIndexedIndirect} itself, against the whole pass's recording bracket. */
    private long indirectDrawNanos;
    /** Time spent writing the indirect command packets into mapped memory. */
    private long indirectPacketNanos;
    /** The whole indirect-batch call, section walk included; compare against the pass bracket. */
    private long indirectBatchNanos;
    /**
     * The setup half of the indirect-batch call (item 350's split of the batch timer): the device checks,
     * the unsorted offset walk and the packet reserve, up to the index bind. Offers that leave through an
     * early return are not accumulated here - on a device where the indirect path is usable at all they are
     * the empty and the mixed-source batches, not the steady state this split exists to attribute.
     */
    private long indirectSetupNanos;
    /** Time inside the indirect path's own {@code vkCmdBindIndexBuffer} call; split out for item 350. */
    private long indirectBindNanos;

    /** {@return nanoseconds inside the indirect-batch calls}. */
    long indirectBatchNanos() {
        return this.indirectBatchNanos;
    }

    /** {@return nanoseconds in the indirect-batch setup half (checks, offset walk, packet reserve)}. */
    long indirectSetupNanos() {
        return this.indirectSetupNanos;
    }

    /** {@return nanoseconds inside the indirect path's index-buffer binds}. */
    long indirectBindNanos() {
        return this.indirectBindNanos;
    }

    /** {@return nanoseconds spent writing the indirect packets}. */
    long indirectPacketNanos() {
        return this.indirectPacketNanos;
    }

    /** {@return nanoseconds spent inside the indirect draw calls since the last reset}. */
    long indirectDrawNanos() {
        return this.indirectDrawNanos;
    }










    /**
     * The index buffer this renderer believes is bound on the command buffer being recorded, or null
     * when that is unknown (item 350). Every terrain-path bind goes through {@link #bindIndexBuffer},
     * which maintains the belief, so within one terrain pass the belief is exact: the pass head forgets
     * it ({@link #invalidateIndexBufferBelief}, called by the backend at the head of its pass recording
     * and by {@link #beginIndirectRecording}), and the only other index binders (UI, dynamic meshes,
     * model instances) record outside the region loop - before the first terrain bind of a pass, when
     * the belief is still null, or after the last one, when it is never consulted again. The indirect
     * path consults the belief to skip a bind that would rebind the buffer already bound: on the 325
     * fixture protocol that bind was 89 of the batch timer's 172 ms, more than the draw calls
     * themselves, because most offers in a pass read the one shared index buffer.
     */
    private VulkanBuffer indexBufferBelief;

    /** Forgets which index buffer is bound; called at terrain pass heads and at recording begins. */
    void invalidateIndexBufferBelief() {
        this.indexBufferBelief = null;
    }

    /** Binds a 32-bit index buffer, for the same reason the vertex binding takes the command buffer. */
    void bindIndexBuffer(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, VulkanBuffer buffer) {
        org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer(commandBuffer, buffer.buffer(), 0L,
                org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32);
        this.indexBufferBelief = buffer;
    }


    /** The context the pipelines are built with; the backend owns it and hands it over. */
    private VulkanContext context;

    /**
     * Tier-A terrain state (new rendering architecture): the default terrain route
     * since the wave-2 cutover ({@code -Dceltium.vulkan.terrainEngineA=false} is the
     * bisection switch that turns it off). Built sections in the 16-byte format are
     * registered into the resident SectionDesc table at upload, and the backend's
     * pass loop drives the Tier-A executors
     * ({@link TerrainEngineA#prepareFrame} / {@link TerrainEngineA#recordDraws}).
     */
    private final TerrainEngineAHost terrainAHost = TerrainEngineAHost.ENABLED ? new TerrainEngineAHost() : null;
    private boolean terrainARegisterFailurePrinted;

    /** The Tier-A host, or null when Tier-A is switched off; the backend's pass loop drives it. */
    TerrainEngineAHost terrainAHost() {
        return this.terrainAHost;
    }
    /** Where a message goes: the backend's diagnostics list, so the report still sees it. */
    private java.util.function.Consumer<String> diagnostics = message -> { };

    /** Gives the class the context its pipelines are built with. */
    void setContext(VulkanContext context) {
        this.context = context;
    }

    /** Gives the class the backend's diagnostic sink. */
    void setDiagnostics(java.util.function.Consumer<String> diagnostics) {
        this.diagnostics = diagnostics;
    }

    /** The stage gate consulted before any pipeline creation (item 327); the system gate by default. */
    private StageGate stageGate = StageGate.system();
    /** Whether the stage-off refusal has been recorded for this renderer; it is recorded once, not per frame. */
    private boolean stageOffRecorded;

    /** Gives the class the stage gate to consult; for tests. */
    void setStageGate(StageGate stageGate) {
        this.stageGate = stageGate;
    }

    /** Records one diagnostic through the backend's sink. */
    void record(String message) {
        this.diagnostics.accept(message);
    }

    /** Names a render pass for a message, tolerating the absence of one. */
    static String nameOf(TerrainRenderPass pass) {
        return pass == null ? "<no render pass>" : pass.name();
    }

    /** Names a throwable for a message, keeping its type even when it has no message. */
    static String describe(Throwable error) {
        return error == null ? "null" : error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : ": " + error.getMessage());
    }

    /** {@return the pipelines by render pass, for the paths that release them}. */
    java.util.Map<TerrainRenderPass, VulkanTerrainPipeline> pipelines() {
        return this.pipelines;
    }

    /** Releases every terrain pipeline and forgets them, the remembered creation failures included. */
    void releasePipelines() {
        for (VulkanTerrainPipeline pipeline : this.pipelines.values()) {
            if (pipeline != null) {
                pipeline.delete();
            }
        }
        this.pipelines.clear();
        this.failedPipelines.clear();
    }

    // ---------------------------------------------------------------- terrain pipelines (R4, second half)

    /** The pipelines built per terrain render pass: keyed by the pass, created on first use. */
    private final java.util.Map<TerrainRenderPass, VulkanTerrainPipeline> pipelines =
            new java.util.concurrent.ConcurrentHashMap<TerrainRenderPass, VulkanTerrainPipeline>();

    /**
     * The passes whose pipeline creation has already failed, remembered beside the map rather than in it:
     * the map is a ConcurrentHashMap and rejects the null value the failure used to be cached as - the
     * handler's own NullPointerException is what killed the leg-11 run and buried the creation failure it
     * was recording. A remembered failure answers null without retrying the creation or repeating the
     * record, which is the behaviour the null entry was reaching for.
     */
    private final java.util.Set<TerrainRenderPass> failedPipelines =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    VulkanTerrainPipeline pipelineFor(TerrainRenderPass pass) {
        if (pass == null) {
            // With no bridge there is no last pass to look up, and the map this reads rejects a null key with a
            // NullPointerException - which is what the remaining three-per-run nulls were. The callers all handle a null
            // pipeline already, so saying "no pipeline for no pass" is the whole fix.
            return null;
        }
        // The stage gate (item 327, Vulcanizator V-01): a switched-off stage never attempts creation - on a
        // hostile driver the creation call itself is the crash, so the refusal happens before any native work.
        // It is recorded once and is NOT remembered as a failure below: the stage was off, not broken.
        if (!this.stageGate.enabled(StageGate.Stage.TERRAIN_PIPELINES)) {
            if (!this.stageOffRecorded) {
                this.stageOffRecorded = true;
                this.record("terrain pipeline stage off (" + (this.stageGate.switchedOff(
                        StageGate.Stage.TERRAIN_PIPELINES)
                        ? "kill switch " + StageGate.Stage.TERRAIN_PIPELINES.switchProperty() + "=off"
                        : "quarantined") + "); passes answer null");
            }
            return null;
        }
        VulkanTerrainPipeline pipeline = this.pipelines.get(pass);
        if (pipeline != null) {
            return pipeline;
        }
        if (this.failedPipelines.contains(pass)) {
            return null;
        }
        try {
            // Back faces are culled on every terrain pass, the sorted (translucent) one included: vanilla draws the
            // translucent layer with GL_CULL_FACE enabled (EntityRenderer.renderWorldPass enables it just before), and
            // the reference renderer inherits that state. "!isSorted" is the mesher's facing-group selection, not the
            // GPU state - passed here it drew the far side of every glass, ice and slime block, a second blended layer
            // that darkened stained glass by ~20-25 levels per channel against Celeritas and whose sort order changes
            // with the view.
            pipeline = VulkanTerrainPipeline.create(this.context, pass, true);
            this.pipelines.put(pass, pipeline);
            record("pipeline for " + nameOf(pass) + " created [" + pipeline.vertexInputSummary() + "]");
            if (pipeline.limitsWarning() != null) {
                record(pipeline.limitsWarning());
            }
            return pipeline;
        } catch (RuntimeException | LinkageError error) {
            this.stageGate.noteFailure(StageGate.Stage.TERRAIN_PIPELINES, error);
            record("pipeline for " + pass.name() + " failed: " + describe(error));
            // The trace goes to stdout as well as the record: in the leg-11 run the record line reached
            // neither the console log nor the game log, so the creation failure this handler exists to
            // surface stayed unobserved while the handler's own crash (the null put this block used to end
            // with) took the game down. Once per pass - the failure is remembered below, so a pass that
            // keeps failing cannot repeat either line per frame.
            error.printStackTrace(System.out);
            this.failedPipelines.add(pass);
            return null;
        }
    }


    // ---------------------------------------------------------------- indirect batch (R4)

    private static final int INDIRECT_COMMAND_BYTES = 20;
    /** A/B constant-colour control, read once: this block is written per vertex page in the upload path. */
    private static final boolean SOLID_TERRAIN = Boolean.getBoolean("celtium.vulkan.solidTerrain");
    private static final boolean TERRAIN_INDIRECT_COMPARE =
            Boolean.getBoolean("celtium.vulkan.indirectCompare");
    /** The indirect command buffer and how often it had to grow. */
    /** The pass counters, so a fallback records its draw count where the pass does. */
    private TerrainStatistics statistics;

    /** Gives the class the pass counters it records into. */
    void setStatistics(TerrainStatistics statistics) {
        this.statistics = statistics;
    }

    private static final int INDIRECT_PAGE_BYTES = 64 * 1024;
    // Pages belong to a recording slot, not a region. Earlier regions' packets stay at distinct offsets until the
    // slot's own fence completes; all deferred uploads are submitted before any of the recorded region draws.
    // Host-visible persistently-mapped pages (HOST_COHERENT): the packet ints are written straight into the memory
    // the draw reads, so no staging snapshot, vkCmdCopyBuffer or deferred upload exists per batch. HOST_COHERENT plus
    // the vkQueueSubmit host-ordering guarantee makes the record-time writes visible to the submission - the contract
    // VulkanHostBuffer documents for exactly this data.
    private final List<List<VulkanHostBuffer>> indirectSlotPages = new ArrayList<>();
    private int indirectRecordingSlot = -1;
    private int indirectPageIndex;
    private long indirectWriteCursor;
    private long indirectBatchOffset;
    private VulkanHostBuffer indirectBuffer;
    private long indirectGrowths;
    private String lastTerrainIndirectFallback = "none";

    /** Offers whose packets came from the batch's retained encoding, and offers that skipped their
     * index bind because the buffer was already bound (item 350). */
    private long terrainIndirectPacketCacheHits;
    private long terrainIndirectBindSkips;

    /** {@return terrainIndirectPacketCacheHits}. */
    long terrainIndirectPacketCacheHits() {
        return this.terrainIndirectPacketCacheHits;
    }

    /** {@return terrainIndirectBindSkips}. */
    long terrainIndirectBindSkips() {
        return this.terrainIndirectBindSkips;
    }

    /** Batches offered to the indirect path, calls recorded, draws carried by them, and clamp events. */
    private long terrainIndirectOffers;
    private long terrainIndirectCalls;
    private long terrainIndirectDraws;
    private long terrainIndirectFallbacks;
    private long terrainIndirectClampEvents;
    private long terrainIndirectCommandDraws;
    private long terrainIndirectCommandChecksum;
    private long terrainDirectEquivalentDraws;
    private long terrainDirectEquivalentChecksum;

    /** {@return terrainIndirectOffers}. */
    long terrainIndirectOffers() {
        return this.terrainIndirectOffers;
    }

    /** {@return terrainIndirectCalls}. */
    long terrainIndirectCalls() {
        return this.terrainIndirectCalls;
    }

    /** {@return terrainIndirectDraws}. */
    long terrainIndirectDraws() {
        return this.terrainIndirectDraws;
    }

    /** {@return terrainIndirectFallbacks}. */
    long terrainIndirectFallbacks() {
        return this.terrainIndirectFallbacks;
    }

    /** {@return terrainIndirectClampEvents}. */
    long terrainIndirectClampEvents() {
        return this.terrainIndirectClampEvents;
    }

    /** {@return terrainIndirectCommandDraws}. */
    long terrainIndirectCommandDraws() {
        return this.terrainIndirectCommandDraws;
    }

    /** {@return terrainIndirectCommandChecksum}. */
    long terrainIndirectCommandChecksum() {
        return this.terrainIndirectCommandChecksum;
    }

    /** {@return terrainDirectEquivalentDraws}. */
    long terrainDirectEquivalentDraws() {
        return this.terrainDirectEquivalentDraws;
    }

    /** {@return terrainDirectEquivalentChecksum}. */
    long terrainDirectEquivalentChecksum() {
        return this.terrainDirectEquivalentChecksum;
    }

    /** Records one fallback to direct draws. */
    void noteTerrainIndirectFallback() {
        this.terrainIndirectFallbacks++;
    }

    /** Records why the indirect path fell back. */
    void setLastTerrainIndirectFallback(String reason) {
        this.lastTerrainIndirectFallback = reason;
    }

    /** {@return why the last indirect batch fell back}. */
    String lastTerrainIndirectFallback() {
        return this.lastTerrainIndirectFallback;
    }

    /** {@return how many times the indirect buffer grew}. */
    long terrainIndirectGrowths() {
        return this.indirectGrowths;
    }

    static long commandHash(long checksum, int indexCount, long firstIndex, int vertexOffset,
            int firstInstance) {
        checksum = checksum * 1099511628211L + indexCount;
        checksum = checksum * 1099511628211L + firstIndex;
        checksum = checksum * 1099511628211L + vertexOffset;
        checksum = checksum * 1099511628211L + firstInstance;
        return checksum;
    }

    boolean tryIndirectBatch(VulkanContext context, org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            org.lwjgl.system.MemoryStack stack,
            java.util.Map<org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType, VulkanBuffer> sharedIndexBuffers,
            RegionResources resources,
            VulkanMultiDrawBatch batch, boolean sortedPass,
            ChunkPrimitiveType primitiveType, int firstInstance) {
        long batchStarted = System.nanoTime();
        if (!prepareIndirectPackets(context, sharedIndexBuffers, resources, batch, sortedPass, primitiveType,
                batchStarted, firstInstance)) {
            this.indirectBatchNanos += System.nanoTime() - batchStarted;
            return false;
        }
        long afterSetup = System.nanoTime();
        if (this.preparedIndexBuffer == null || this.preparedIndexBuffer != this.indexBufferBelief) {
            this.bindIndexBuffer(commandBuffer, this.preparedIndexBuffer);
        } else {
            this.terrainIndirectBindSkips++;
        }
        this.indirectBindNanos += System.nanoTime() - afterSetup;
        long afterDraw = recordIndirectDraws(commandBuffer, this.indirectBuffer.buffer(), this.indirectBatchOffset,
                batch.size(), this.preparedMaxDraws, afterSetup);
        this.indirectBatchNanos += afterDraw - batchStarted;
        return true;
    }

    /** The index buffer the last successful {@link #prepareIndirectPackets} decided its batch reads. */
    private VulkanBuffer preparedIndexBuffer;
    /** The device draw-count limit the last successful preparation read, for the draw loop's clamping. */
    private long preparedMaxDraws;

    /**
     * The front half of an indirect offer, shared by the one-call-per-batch path above and the pass chain
     * below: the fallback checks, the packet-page reservation, and the copy of the batch's retained packets
     * into the page with this region's firstInstance stamped. On success the batch's commands sit in
     * {@link #indirectBuffer} at {@link #indirectBatchOffset} and {@link #preparedIndexBuffer} names the index
     * buffer they read; nothing has been recorded yet, so the caller decides when the bind and the draw are
     * recorded. The bind's own nanos are the caller's (the chain defers it to the flush), so the setup/packet
     * split here is measured up to the copy instead of up to the bind.
     *
     * @return false when the batch must be drawn directly, with the fallback counted and named as before
     */
    private boolean prepareIndirectPackets(VulkanContext context,
            java.util.Map<org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType, VulkanBuffer> sharedIndexBuffers,
            RegionResources resources,
            VulkanMultiDrawBatch batch, boolean sortedPass,
            ChunkPrimitiveType primitiveType, long setupStarted, int firstInstance) {
        this.terrainIndirectOffers++;
        if (!context.multiDrawIndirectEnabled()) {
            this.terrainIndirectFallbacks++;
            this.lastTerrainIndirectFallback = "the device was not created with multiDrawIndirect";
            return false;
        }
        // Unsigned, because the limit is a uint32: RADV reports 0xFFFFFFFF ("no small bound"), which reads as -1 as a
        // signed int and is not a negative draw count. Only a true 0 means "not reported", and then the direct path is
        // the honest answer.
        long maxDraws = context.maxDrawIndirectCount() & 0xFFFFFFFFL;
        if (maxDraws == 0L) {
            this.terrainIndirectFallbacks++;
            this.lastTerrainIndirectFallback = "maxDrawIndirectCount is unknown (0)";
            return false;
        }
        int size = batch.size();
        if (size <= 0) {
            return false;
        }
        // BH-02's rule, applied to the whole batch this time: one indirect call binds one index buffer, so a batch whose
        // commands do not all read the same one is drawn directly.
        boolean regionIndex = sortedPass;
        if (!regionIndex) {
            // The batch is immutable, so the walk's verdict is computed once with its retained packets
            // (item 350) instead of per offer; the fallback accounting is unchanged.
            boolean offsetsClean = batch.unsortedOffsetsClean();
            if (!offsetsClean) {
                this.terrainIndirectFallbacks++;
                this.lastTerrainIndirectFallback = "unsorted batch with a non-zero index offset (mixed sources)";
                return false;
            }
        }
        if (regionIndex && resources.indexArena == null) {
            this.terrainIndirectFallbacks++;
            this.lastTerrainIndirectFallback = "sorted batch with no region index arena";
            return false;
        }
        if (size > Integer.MAX_VALUE / INDIRECT_COMMAND_BYTES) {
            this.terrainIndirectFallbacks++;
            this.lastTerrainIndirectFallback = "the indirect packet byte count exceeds the supported range";
            return false;
        }
        int needed = size * INDIRECT_COMMAND_BYTES;
        if (!this.reserveIndirectPacketBytes(context, needed)) {
            this.terrainIndirectFallbacks++;
            return false;
        }
        long afterSetup = System.nanoTime();
        this.indirectSetupNanos += afterSetup - setupStarted;
        this.preparedIndexBuffer = regionIndex ? resources.indexArena.buffer()
                : sharedIndexBuffers.get(primitiveType);
        this.preparedMaxDraws = maxDraws;
        // Write the packets directly into the mapped page at the draw's offset: the HOST_COHERENT memory needs no
        // flush, and vkQueueSubmit's host-side ordering guarantee publishes these record-time writes to the
        // submission that carries the draw. The per-batch staging snapshot + vkCmdCopyBuffer + deferred upload this
        // replaces was the dominating cost of the indirect path (measured: recDraw ~3 ms/frame while the draw call
        // itself is ~1 vkCmd).
        long packets = this.indirectBuffer.mappedAddress() + this.indirectBatchOffset;
        // The batch's retained encoding, copied in one native copy (item 350): the packets are encoded
        // once per batch instead of once per offer; the compare path further down reads them back from
        // this page exactly as it did a per-offer encoding's.
        MemoryUtil.memCopy(batch.retainedPacketsAddress(regionIndex), packets, (long) needed);
        this.terrainIndirectPacketCacheHits++;
        // Stamp this region's index into every copied command's firstInstance field (offset 16
        // of the 20-byte command, agera-kpi-3): the vertex shader reads its region offset from
        // the binding-5 array at gl_InstanceIndex, which equals firstInstance for these
        // single-instance draws. The batch's retained encoding keeps its zeros - only the page
        // copy is stamped, one int per command over bytes the copy just touched.
        for (int i = 0; i < size; i++) {
            MemoryUtil.memPutInt(packets + (long) i * INDIRECT_COMMAND_BYTES + 16, firstInstance);
        }
        long afterPacket = System.nanoTime();
        this.indirectPacketNanos += afterPacket - afterSetup;
        if (TERRAIN_INDIRECT_COMPARE) {
            // Compare the encoded CPU packet with the direct enumeration, not the same CPU values twice. This is
            // an encoder check, not a GPU readback or proof of the packet's resource lifetime.
            for (int i = 0; i < size; i++) {
                long byteOffset = batch.indexByteOffset(i);
                long directFirstIndex = regionIndex ? (byteOffset / 4) : 0L;
                long packet = packets + (long) i * INDIRECT_COMMAND_BYTES;
                this.terrainDirectEquivalentDraws++;
                this.terrainDirectEquivalentChecksum = commandHash(this.terrainDirectEquivalentChecksum,
                        batch.elementCount(i), directFirstIndex, batch.baseVertex(i), firstInstance);
                this.terrainIndirectCommandDraws++;
                this.terrainIndirectCommandChecksum = commandHash(this.terrainIndirectCommandChecksum,
                        MemoryUtil.memGetInt(packet),
                        Integer.toUnsignedLong(MemoryUtil.memGetInt(packet + 8)),
                        MemoryUtil.memGetInt(packet + 12),
                        MemoryUtil.memGetInt(packet + 16));
            }
        }
        return true;
    }

    /**
     * Records the draw calls for {@code size} prepared commands starting at {@code startOffsetBytes} in
     * {@code pageBuffer}, clamped to the device limit exactly as the one-call-per-batch path always did.
     *
     * @return the timestamp after the last call
     */
    private long recordIndirectDraws(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, long pageBuffer,
            long startOffsetBytes, int size, long maxDraws, long startedAt) {
        int first = 0;
        long drawStarted = startedAt;
        long afterDraw = startedAt;
        while (first < size) {
            int count = (int) Math.min(maxDraws, (long) (size - first));
            if (count != size - first) {
                this.terrainIndirectClampEvents++;
            }
            long offsetBytes = startOffsetBytes + (long) first * INDIRECT_COMMAND_BYTES;
            vkCmdDrawIndexedIndirect(commandBuffer, pageBuffer, offsetBytes, count, INDIRECT_COMMAND_BYTES);
            afterDraw = System.nanoTime();
            this.indirectDrawNanos += afterDraw - drawStarted;
            drawStarted = afterDraw;
            this.terrainIndirectCalls++;
            this.terrainIndirectDraws += count;
            this.statistics.addLastDrawCommands(count);
            first += count;
        }
        return afterDraw;
    }

    // ---------------------------------------------------------------- pass chain (agera-kpi-4)

    /**
     * The open chain run: a maximal run of offered batches whose packets lie contiguously in one page and
     * read the same index buffer, so one vkCmdDrawIndexedIndirect can carry them all. The run is recorded
     * only at the flush, which is what makes the recorded order identical to the offer order: a batch that
     * cannot join (another index buffer, or a reservation that landed on a new page) flushes the run ahead
     * of its own commands, and a batch refused by the preparation flushes it from the caller before the
     * direct draws that replace it. The vertex buffer is the shared arena's, bound once at the pass' head,
     * so nothing between offers needs recording at all.
     */
    private boolean chainOpen;
    private VulkanHostBuffer chainPage;
    private long chainStartOffset;
    private int chainCount;
    private VulkanBuffer chainIndexBuffer;
    private long chainMaxDraws;

    /** Opens a chain for the pass being recorded. No draw state is touched until the first flush. */
    void beginTerrainChain() {
        this.chainOpen = true;
        this.chainPage = null;
        this.chainStartOffset = 0;
        this.chainCount = 0;
        this.chainIndexBuffer = null;
    }

    /**
     * Offers one batch to the pass' chain: prepares its packets exactly as {@link #tryIndirectBatch} does
     * and appends them to the open run, flushing the run first when this batch cannot extend it.
     *
     * @return false when the batch must be drawn directly (the preparation's fallback accounting applies);
     *         the caller flushes the chain before recording those direct draws, preserving order
     */
    boolean offerChainedBatch(VulkanContext context, org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            java.util.Map<org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType, VulkanBuffer> sharedIndexBuffers,
            RegionResources resources,
            VulkanMultiDrawBatch batch, boolean sortedPass,
            ChunkPrimitiveType primitiveType, int firstInstance, VulkanTerrainPipeline pipeline) {
        long batchStarted = System.nanoTime();
        if (!prepareIndirectPackets(context, sharedIndexBuffers, resources, batch, sortedPass, primitiveType,
                batchStarted, firstInstance)) {
            this.indirectBatchNanos += System.nanoTime() - batchStarted;
            return false;
        }
        boolean contiguous = this.chainOpen && this.chainCount > 0
                && this.chainPage == this.indirectBuffer
                && this.chainStartOffset + (long) this.chainCount * INDIRECT_COMMAND_BYTES == this.indirectBatchOffset
                && this.chainIndexBuffer == this.preparedIndexBuffer;
        if (this.chainCount > 0 && !contiguous) {
            flushTerrainChain(commandBuffer);
        }
        if (this.chainCount == 0) {
            this.chainPage = this.indirectBuffer;
            this.chainStartOffset = this.indirectBatchOffset;
            this.chainIndexBuffer = this.preparedIndexBuffer;
            this.chainMaxDraws = this.preparedMaxDraws;
        }
        this.chainCount += batch.size();
        this.indirectBatchNanos += System.nanoTime() - batchStarted;
        return true;
    }

    /** Records the open run, if any: binds its index buffer when the belief names another, then draws it whole. */
    void flushTerrainChain(org.lwjgl.vulkan.VkCommandBuffer commandBuffer) {
        if (this.chainCount <= 0) {
            this.chainPage = null;
            this.chainIndexBuffer = null;
            return;
        }
        long flushStarted = System.nanoTime();
        if (this.chainIndexBuffer == null || this.chainIndexBuffer != this.indexBufferBelief) {
            this.bindIndexBuffer(commandBuffer, this.chainIndexBuffer);
            this.indirectBindNanos += System.nanoTime() - flushStarted;
            flushStarted = System.nanoTime();
        }
        recordIndirectDraws(commandBuffer, this.chainPage.buffer(), this.chainStartOffset, this.chainCount,
                this.chainMaxDraws, flushStarted);
        this.chainPage = null;
        this.chainStartOffset = 0;
        this.chainCount = 0;
        this.chainIndexBuffer = null;
    }

    /** Flushes the open run and closes the pass' chain. */
    void endTerrainChain(org.lwjgl.vulkan.VkCommandBuffer commandBuffer) {
        flushTerrainChain(commandBuffer);
        this.chainOpen = false;
    }

    /** Called after this command slot's prior fence completed and its command buffer was reset successfully. */
    void beginIndirectRecording(int slot) {
        if (slot < 0 || slot >= FrameSubmissionScheduler.FRAME_SLOTS) {
            throw new IllegalArgumentException("invalid indirect recording slot: " + slot);
        }
        // A new recording starts from a reset command buffer: nothing is bound on it yet.
        this.invalidateIndexBufferBelief();
        // A chain belongs to one pass' recording; a pass that ended without its endTerrainChain (an
        // exceptional exit) must not leak its open run into this frame - the run's commands were never
        // recorded, and the new frame's chain starts empty rather than appending to a dead pass' state.
        this.chainOpen = false;
        this.chainPage = null;
        this.chainCount = 0;
        this.chainIndexBuffer = null;
        while (this.indirectSlotPages.size() <= slot) {
            this.indirectSlotPages.add(new ArrayList<>());
        }
        this.indirectRecordingSlot = slot;
        this.indirectPageIndex = 0;
        this.indirectWriteCursor = 0;
        this.indirectBatchOffset = 0;
        this.indirectBuffer = null;
    }

    private boolean reserveIndirectPacketBytes(VulkanContext context, int bytes) {
        if (this.indirectRecordingSlot < 0) {
            this.lastTerrainIndirectFallback = "no indirect recording slot owns the packets";
            return false;
        }
        try {
            List<VulkanHostBuffer> pages = this.indirectSlotPages.get(this.indirectRecordingSlot);
            int pageIndex = this.indirectPageIndex;
            VulkanHostBuffer page = this.indirectBuffer;
            long offset = this.indirectWriteCursor;
            if (page == null || offset > page.size() - bytes) {
                if (page != null) {
                    pageIndex++;
                }
                page = pageIndex < pages.size() ? pages.get(pageIndex) : null;
                if (page == null || page.size() < bytes) {
                    VulkanHostBuffer replacement = new VulkanHostBuffer(context,
                            Math.max((long) bytes, INDIRECT_PAGE_BYTES),
                            org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
                    // This page has not been used in this recording. The selected slot's previous use is fenced;
                    // unlike growing the active page, replacing it cannot destroy a handle in recorded draws.
                    if (page != null) {
                        page.delete();
                        pages.set(pageIndex, replacement);
                    } else {
                        pages.add(replacement);
                    }
                    page = replacement;
                    this.indirectGrowths++;
                }
                offset = 0;
            }
            this.indirectBuffer = page;
            this.indirectPageIndex = pageIndex;
            this.indirectBatchOffset = offset;
            this.indirectWriteCursor = offset + bytes;
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.lastTerrainIndirectFallback = "indirect buffer: " + describe(error);
            return false;
        }
    }

    /** Releases all packet pages only after the backend drained their command slots and outstanding uploads. */
    void releaseIndirectBuffers() {
        for (List<VulkanHostBuffer> pages : this.indirectSlotPages) {
            for (VulkanHostBuffer page : pages) {
                page.delete();
            }
        }
        this.indirectSlotPages.clear();
        this.indirectBuffer = null;
        this.indirectRecordingSlot = -1;
        this.indirectPageIndex = 0;
        this.indirectWriteCursor = 0;
        this.indirectBatchOffset = 0;
    }


    // ---------------------------------------------------------------- uniforms (R4)

    /** The uniform ring the pass writes its per-region blocks into, and how far it has been written. */
    /** The fog service the uniform block reads its colour and distances from. */
    private FogService fogService;

    /** Gives the class the fog service the game installed. */
    void setFogService(FogService fogService) {
        this.fogService = fogService;
    }

    /** The dynamic light service the frame's shading-time sources are gathered from (item 326). */
    private DynamicLightService dynamicLightService;

    /** Gives the class the dynamic light service the game installed. */
    void setDynamicLightService(DynamicLightService dynamicLightService) {
        this.dynamicLightService = dynamicLightService;
    }

    /**
     * One dynamic light buffer per descriptor slot, created beside the uniform ring. The contents are
     * rewritten once per frame (the slot's previous frame is complete by the slot discipline, the same
     * one the descriptor sets rely on); the handles are stable, so the pipeline's descriptor writes
     * only change when a buffer is first created.
     */
    private VulkanUniformBuffer[] dynamicLightBuffers;

    /** The scratch the gather writes through, sized by the service contract. */
    private final float[] dynamicLightScratch = new float[DynamicLightService.MAX_LIGHTS * 4];

    /** The descriptor slot the current frame's gather belongs to. */
    private int dynamicLightSlot;

    /** Item 430 discrimination poison: {@code -Dceltium.dynamicLights.poison=true} replaces the gather. */
    private static final boolean DYNAMIC_LIGHT_POISON = Boolean.getBoolean("celtium.dynamicLights.poison");
    private static boolean dynamicLightPoisonPrinted;

    /** Whether the current frame's sources have been gathered into {@link #dynamicLightBuffers}. */
    private boolean dynamicLightsGathered = true;


    /** {@return the native handle of the dynamic light buffer of a descriptor slot, or 0 before creation}. */
    long dynamicLightBufferHandle(int slot) {
        VulkanUniformBuffer[] buffers = this.dynamicLightBuffers;
        if (buffers == null) {
            return 0L;
        }
        VulkanUniformBuffer buffer = buffers[slot & (VulkanTerrainPipeline.DESCRIPTOR_SLOTS - 1)];
        return buffer == null ? 0L : buffer.buffer();
    }

    /** {@return the native handle of the region offset buffer of a descriptor slot, or 0 before creation}. */
    long regionOffsetBufferHandle(int slot) {
        VulkanHostBuffer[] buffers = this.regionOffsetBuffers;
        if (buffers == null) {
            return 0L;
        }
        VulkanHostBuffer buffer = buffers[slot & (VulkanTerrainPipeline.DESCRIPTOR_SLOTS - 1)];
        return buffer == null ? 0L : buffer.buffer();
    }

    /** {@return the size in bytes of the region offset buffer of a descriptor slot, or 0 before creation}. */
    long regionOffsetBufferSize(int slot) {
        VulkanHostBuffer[] buffers = this.regionOffsetBuffers;
        if (buffers == null) {
            return 0L;
        }
        VulkanHostBuffer buffer = buffers[slot & (VulkanTerrainPipeline.DESCRIPTOR_SLOTS - 1)];
        return buffer == null ? 0L : buffer.size();
    }

    /**
     * Gathers this frame's dynamic light sources into the current slot's buffer. Runs on the first
     * uniform write of the frame, which is the first call that knows the frame's camera. The positions
     * the service returns are camera-relative, and so is the terrain shader's worldPosition (it is
     * built from the region offset, which {@link #writeRegionOffset} writes as region origin minus
     * camera), so the shader measures distance directly in that shared frame. The header still carries the
     * camera position next to the count, as provenance. (Item 430: the shader used to subtract the
     * header camera from the already camera-relative position, counting the camera offset twice -
     * about 64 blocks of it vertical - and the raise came to zero everywhere.)
     */
    private void gatherDynamicLights(CameraTransform camera) {
        VulkanUniformBuffer[] buffers = this.dynamicLightBuffers;
        if (buffers == null) {
            return;
        }
        VulkanUniformBuffer buffer = buffers[this.dynamicLightSlot];
        if (buffer == null) {
            return;
        }
        int count = 0;
        double cameraX = (double) camera.intX + camera.fracX;
        double cameraY = (double) camera.intY + camera.fracY;
        double cameraZ = (double) camera.intZ + camera.fracZ;
        if (this.dynamicLightService != null) {
            count = this.dynamicLightService.gatherLights(this.dynamicLightScratch, cameraX, cameraY, cameraZ);
        }
        if (DYNAMIC_LIGHT_POISON) {
            // Item 430 discrimination poison: one level-15 source at the camera-relative origin and a
            // zero header camera, so the shader's raise must light the terrain around the camera if
            // the buffer -> descriptor -> shader path is alive at all, whatever the real gather wrote.
            count = 1;
            this.dynamicLightScratch[0] = 0.0F;
            this.dynamicLightScratch[1] = 0.0F;
            this.dynamicLightScratch[2] = 0.0F;
            this.dynamicLightScratch[3] = 15.0F;
            cameraX = 0.0D;
            cameraY = 0.0D;
            cameraZ = 0.0D;
            if (!dynamicLightPoisonPrinted) {
                dynamicLightPoisonPrinted = true;
                System.out.println("[Celtium] CELTIUM_DYN_POISON armed");
            }
        }
        ByteBuffer block = buffer.mapped();
        block.putFloat(0, (float) count);
        block.putFloat(4, (float) cameraX);
        block.putFloat(8, (float) cameraY);
        block.putFloat(12, (float) cameraZ);
        for (int i = 0; i < count; i++) {
            int base = 16 + i * 16;
            block.putFloat(base, this.dynamicLightScratch[i * 4]);
            block.putFloat(base + 4, this.dynamicLightScratch[i * 4 + 1]);
            block.putFloat(base + 8, this.dynamicLightScratch[i * 4 + 2]);
            block.putFloat(base + 12, this.dynamicLightScratch[i * 4 + 3]);
        }
    }

    /** {@return the slot index into the per-frame-slot uniform buffer ring}. */
    static int uniformSlotIndex(int frameSlot) {
        return frameSlot & (VulkanTerrainPipeline.DESCRIPTOR_SLOTS - 1);
    }

    /** {@return the active frame slot's uniform ring, which the pass reserves against before recording}. */
    VulkanUniformBuffer uniforms() {
        return uniforms(this.uniformSlot);
    }

    /** {@return the uniform ring belonging to {@code frameSlot}, or {@code null} before creation}. */
    VulkanUniformBuffer uniforms(int frameSlot) {
        VulkanUniformBuffer[] buffers = this.uniformBuffers;
        return buffers == null ? null : buffers[uniformSlotIndex(frameSlot)];
    }

    /** {@return the growth summary across the uniform slot ring, for diagnostics}. */
    String uniformGrowthSummary() {
        VulkanUniformBuffer[] buffers = this.uniformBuffers;
        if (buffers == null) {
            return "=none";
        }
        VulkanUniformBuffer active = buffers[this.uniformSlot];
        return active == null ? "=none" : active.growthSummary();
    }

    /** {@return the stride of one uniform block}. */
    long uniformStride() {
        return this.uniformStride;
    }

    /** Sets how many uniform blocks the ring holds, after a growth. */
    void setUniformBlocks(int blocks) {
        this.uniformBlocks = blocks;
    }

    /** {@return how many uniform blocks the ring holds}. */
    int uniformBlocks() {
        return this.uniformBlocks;
    }

    /** {@return how far the ring has been written this pass}. */
    long uniformWriteCursor() {
        return this.uniformWriteCursor;
    }

    /** {@return whether the fog line has been printed this run}. */
    boolean uniformsLogged() {
        return this.uniformsLogged;
    }

    /**
     * One host-coherent uniform ring per in-flight frame slot ({@link VulkanTerrainPipeline#DESCRIPTOR_SLOTS}),
     * matching {@link #dynamicLightBuffers} and the pipeline's per-slot descriptor sets. A single shared ring had its
     * write cursor reset to 0 on every frame begin and overwrote {@code u_RegionOffset} and fog blocks while up to
     * three earlier frames were still executing on the GPU (issue #422 (b)/(d): regions teleporting to another
     * region's coordinates such as +y200 / floating leaf canopies).
     */
    private VulkanUniformBuffer[] uniformBuffers;
    private int uniformSlot;
    private long uniformStride;
    private int uniformBlocks;
    private long uniformWriteCursor;
    private boolean uniformsLogged;

    /**
     * One host-coherent storage buffer per frame slot holding the frame's per-region offsets
     * (agera-kpi-3, descriptor binding 5): one vec4 per region draw, xyz = region origin minus
     * camera. The vertex stage reads its draw's entry through {@code gl_InstanceIndex}, whose
     * value the draw command's firstInstance carries, so the offset no longer needs a uniform
     * block - and a descriptor rebind - of its own per region. Written through the same slot
     * discipline as the uniform ring; the cursor resets with it at frame begin.
     */
    private VulkanHostBuffer[] regionOffsetBuffers;
    private int regionOffsetWriteCursor;
    /** Bytes per region offset entry: one vec4. */
    private static final int REGION_OFFSET_ENTRY_BYTES = 16;

    /** How many region draws have passed, so the fog line can be compared between legs at the same index. */
    private int fogLogDraws;
    /** The fog line repeats every this many region draws (plus the first). */
    private static final int FOG_LOG_EVERY = 200;
    private static final float MIP_BIAS_ADD =
            Float.parseFloat(System.getProperty("celtium.vulkan.mipBiasAdd", "0.0"));

    static int fogMode() {
        ImmediateFogState fog = ImmediateFogState.current();
        if (!fog.enabled()) return 0;
        return fog.glMode() == 2049 ? 1 : fog.glMode() == 2048 ? 2 : 3;
    }

    /** Per-pass fog snapshot so the region loop reads FogService once per pass rather than once per region. */
    private boolean passFogPrepared;
    private float passFogR;
    private float passFogG;
    private float passFogB;
    private float passFogA;
    private float passFogDensity;
    private float passFogStart;
    private float passFogEnd;
    private int passFogMode;
    private int passFogShape;
    private int passFragmentDiscard;

    void preparePassFog(TerrainRenderPass pass) {
        FogService service = this.fogService;
        float[] fogColor = service != null ? service.getFogColor() : null;
        this.passFogR = fogColor != null && fogColor.length > 0 ? fogColor[0] : 0.0f;
        this.passFogG = fogColor != null && fogColor.length > 1 ? fogColor[1] : 0.0f;
        this.passFogB = fogColor != null && fogColor.length > 2 ? fogColor[2] : 0.0f;
        this.passFogA = fogColor != null && fogColor.length > 3 ? fogColor[3] : 0.0f;
        this.passFogDensity = service != null ? service.getFogDensity() : 0.0f;
        this.passFogStart = service != null ? service.getFogStart() : 0.0f;
        this.passFogEnd = service != null ? service.getFogEnd() : 0.0f;
        this.passFogMode = fogMode();
        this.passFogShape = service != null ? service.getFogShapeIndex() : FogService.FOG_SHAPE_SPHERICAL;
        this.passFragmentDiscard = pass != null && pass.supportsFragmentDiscard() ? 1 : 0;
        this.passFogPrepared = true;
    }

    void clearPassFog() {
        this.passFogPrepared = false;
    }

    /**
     * Writes this pass' uniform block once, ahead of the region loop, and returns its ring offset
     * for the pass' single descriptor bind (agera-kpi-3). Everything the block carries is constant
     * across a pass - the fog values come from the snapshot {@link #preparePassFog} took, the
     * shadow lookup from the backend's per-pass arming - except the region offset, which no
     * longer travels in the block at all: {@link #writeRegionOffset} appends it to the binding-5
     * array and the draw command's firstInstance names the entry. The block's offset field is
     * still written (zeros) because the fragment stage declares the same block layout.
     * <p>
     * Before this split the block was rewritten per region and the descriptor set rebound with a
     * fresh dynamic offset for every region batch; the agera-kpi-2 census put that rebind at
     * roughly 7.5-10us per batch on ANV, the second-largest named child of terrain record.
     */
    long writePassUniforms(TerrainRenderPass pass, CameraTransform camera) {
        if (!this.dynamicLightsGathered) {
            this.dynamicLightsGathered = true;
            gatherDynamicLights(camera);
        }
        VulkanUniformBuffer activeUniforms = this.uniformBuffers[this.uniformSlot];
        long offset = this.uniformWriteCursor;
        this.uniformWriteCursor += this.uniformStride;
        if (this.uniformWriteCursor > activeUniforms.size()) {
            // This must not happen: render() reserves an upper bound before recording, and growth replaces the
            // buffer that descriptors recorded earlier in this pass already point at. Reported loudly rather than
            // growing silently, so a missed reservation shows up as a defect instead of as wrong terrain.
            record("uniform buffer grew during recording: slot=" + this.uniformSlot + " "
                    + this.uniformWriteCursor + " > " + activeUniforms.size()
                    + " (descriptors bound earlier in this pass may be stale)");
            activeUniforms.ensureCapacity(this.uniformWriteCursor);
            this.uniformBlocks = (int) (activeUniforms.size() / this.uniformStride);
        }

        float fogR, fogG, fogB, fogA, fogDensity, fogStart, fogEnd;
        int currentFogMode, fogShape, fragmentDiscard;
        if (this.passFogPrepared) {
            fogR = this.passFogR;
            fogG = this.passFogG;
            fogB = this.passFogB;
            fogA = this.passFogA;
            fogDensity = this.passFogDensity;
            fogStart = this.passFogStart;
            fogEnd = this.passFogEnd;
            currentFogMode = this.passFogMode;
            fogShape = this.passFogShape;
            fragmentDiscard = this.passFragmentDiscard;
        } else {
            FogService service = this.fogService;
            float[] fogColor = service != null ? service.getFogColor() : null;
            fogR = fogColor != null && fogColor.length > 0 ? fogColor[0] : 0.0f;
            fogG = fogColor != null && fogColor.length > 1 ? fogColor[1] : 0.0f;
            fogB = fogColor != null && fogColor.length > 2 ? fogColor[2] : 0.0f;
            fogA = fogColor != null && fogColor.length > 3 ? fogColor[3] : 0.0f;
            fogDensity = service != null ? service.getFogDensity() : 0.0f;
            fogStart = service != null ? service.getFogStart() : 0.0f;
            fogEnd = service != null ? service.getFogEnd() : 0.0f;
            currentFogMode = fogMode();
            fogShape = service != null ? service.getFogShapeIndex() : FogService.FOG_SHAPE_SPHERICAL;
            fragmentDiscard = pass.supportsFragmentDiscard() ? 1 : 0;
        }

        long addr = activeUniforms.mappedAddress() + offset;
        // The region offset field: no longer read by the vertex stage (the offset arrives through
        // the binding-5 array), still written so the block the fragment stage declares is whole.
        MemoryUtil.memPutFloat(addr + 0, 0.0f);
        MemoryUtil.memPutFloat(addr + 4, 0.0f);
        MemoryUtil.memPutFloat(addr + 8, 0.0f);
        MemoryUtil.memPutFloat(addr + 12, 0.0f);
        MemoryUtil.memPutFloat(addr + 16, fogR);
        MemoryUtil.memPutFloat(addr + 20, fogG);
        MemoryUtil.memPutFloat(addr + 24, fogB);
        MemoryUtil.memPutFloat(addr + 28, fogA);
        MemoryUtil.memPutFloat(addr + 32, fogDensity);
        MemoryUtil.memPutFloat(addr + 36, fogStart);
        MemoryUtil.memPutFloat(addr + 40, fogEnd);
        // Additive atlas LOD shift. The per-material bias already travels in the vertex, so parity needs 0 here.
        MemoryUtil.memPutFloat(addr + 44, MIP_BIAS_ADD);
        MemoryUtil.memPutInt(addr + 48, currentFogMode);
        MemoryUtil.memPutInt(addr + 52, fogShape);
        MemoryUtil.memPutInt(addr + 56, fragmentDiscard);
        // Chunk fade is a separate feature that the Vulkan path does not port yet; 0 keeps the GL default look.
        MemoryUtil.memPutInt(addr + 60, 0);
        MemoryUtil.memPutFloat(addr + 64, 0.0f);
        MemoryUtil.memPutFloat(addr + 68, 0.0f);
        MemoryUtil.memPutFloat(addr + 72, 0.0f);
        MemoryUtil.memPutFloat(addr + 76, 0.0f);
        // A/B only: a constant fragment colour that reaches the frame image says the geometry is rasterised.
        MemoryUtil.memPutInt(addr + 80, SOLID_TERRAIN ? 1 : 0);
        // The terrain shadow lookup (item 537): on only for a pass whose set carries a shadow depth image, with the
        // light-space matrix that pass' shadow terrain was drawn with. Off writes zero and the shader skips the
        // lookup, so the default build's terrain is the same picture.
        MemoryUtil.memPutInt(addr + 84, this.shadowLookupEnabled ? 1 : 0);
        MemoryUtil.memPutFloat(addr + 88, this.shadowLookupDarkness);
        if (this.shadowLookupEnabled) {
            // std140 mat4 at offset 96, column-major (JOML's mCR is column C, row R).
            org.joml.Matrix4f m = this.shadowLookupMatrix;
            long base = addr + 96;
            MemoryUtil.memPutFloat(base, m.m00());
            MemoryUtil.memPutFloat(base + 4, m.m01());
            MemoryUtil.memPutFloat(base + 8, m.m02());
            MemoryUtil.memPutFloat(base + 12, m.m03());
            MemoryUtil.memPutFloat(base + 16, m.m10());
            MemoryUtil.memPutFloat(base + 20, m.m11());
            MemoryUtil.memPutFloat(base + 24, m.m12());
            MemoryUtil.memPutFloat(base + 28, m.m13());
            MemoryUtil.memPutFloat(base + 32, m.m20());
            MemoryUtil.memPutFloat(base + 36, m.m21());
            MemoryUtil.memPutFloat(base + 40, m.m22());
            MemoryUtil.memPutFloat(base + 44, m.m23());
            MemoryUtil.memPutFloat(base + 48, m.m30());
            MemoryUtil.memPutFloat(base + 52, m.m31());
            MemoryUtil.memPutFloat(base + 56, m.m32());
            MemoryUtil.memPutFloat(base + 60, m.m33());
        }

        return offset;
    }

    /**
     * Appends this region's offset to the frame slot's binding-5 array and returns its index,
     * which the caller carries into the region's draw commands as firstInstance (agera-kpi-3).
     * The values are the identical floats the uniform block's {@code u_RegionOffset} used to
     * carry - region origin minus camera, in the same computation order - so a draw reads
     * exactly the offset it read before the delivery channel changed.
     */
    int writeRegionOffset(RenderRegion region, CameraTransform camera) {
        float regionOffsetX = (region.getOriginX() - camera.intX) - camera.fracX;
        float regionOffsetY = (region.getOriginY() - camera.intY) - camera.fracY;
        float regionOffsetZ = (region.getOriginZ() - camera.intZ) - camera.fracZ;

        // The fog line moved here from the per-region uniform write (agera-kpi-3 split): it is
        // repeated every FOG_LOG_EVERY region draws as well as on the first one, and carries the
        // draw index. It printed once, which made the two legs' single samples different frames
        // and so incomparable: the bridge leg read [0.3816, 0.4421, 0.545] and the GL-less leg
        // [0.4786, 0.5544, 0.6834] with no way to tell whether that was a difference between the
        // backends or between the frames they happened to log.
        this.fogLogDraws++;
        if (!uniformsLogged || (PeriodicLog.ENABLED && this.fogLogDraws % FOG_LOG_EVERY == 0)) {
            uniformsLogged = true;
            float fogStart = this.passFogPrepared ? this.passFogStart
                    : (this.fogService != null ? this.fogService.getFogStart() : 0.0f);
            float fogEnd = this.passFogPrepared ? this.passFogEnd
                    : (this.fogService != null ? this.fogService.getFogEnd() : 0.0f);
            float fogDensity = this.passFogPrepared ? this.passFogDensity
                    : (this.fogService != null ? this.fogService.getFogDensity() : 0.0f);
            int fogShape = this.passFogPrepared ? this.passFogShape
                    : (this.fogService != null ? this.fogService.getFogShapeIndex()
                    : FogService.FOG_SHAPE_SPHERICAL);
            int currentFogMode = this.passFogPrepared ? this.passFogMode : fogMode();
            float[] fogColor = this.fogService != null ? this.fogService.getFogColor() : null;
            // stdout, not record(): the diagnostics list is capped and this line was being dropped from it.
            System.out.println("[Celtium] CELTIUM_FOG draw=" + this.fogLogDraws + " fogColor=" + java.util.Arrays.toString(fogColor)
                    + " start=" + fogStart
                    + " end=" + fogEnd
                    + " density=" + fogDensity
                    + " shape=" + (this.fogService != null ? fogShape : -1)
                    + " glFogMode=" + currentFogMode + " cutoff=" + (this.fogService != null ? this.fogService.getFogCutoff() : 0.0f)
                    + " regionOffset=(" + regionOffsetX + "," + regionOffsetY + "," + regionOffsetZ + ")");
        }

        VulkanHostBuffer active = this.regionOffsetBuffers[this.uniformSlot];
        int index = this.regionOffsetWriteCursor++;
        if ((long) (index + 1) * REGION_OFFSET_ENTRY_BYTES > active.size()) {
            // The same missed-reservation guard as the uniform ring, in the same loud shape.
            // VulkanHostBuffer growth retires the old buffer rather than deleting it under the
            // recording, and the pipeline rewrites the descriptor when the handle changes, so
            // growing here is safe - but it still means the reservation was wrong, so it is said.
            record("region offset buffer grew during recording: slot=" + this.uniformSlot + " entry=" + index
                    + " capacity=" + (active.size() / REGION_OFFSET_ENTRY_BYTES));
            active.ensureCapacity((long) (index + 1) * REGION_OFFSET_ENTRY_BYTES);
        }
        long addr = active.mappedAddress() + (long) index * REGION_OFFSET_ENTRY_BYTES;
        MemoryUtil.memPutFloat(addr, regionOffsetX);
        MemoryUtil.memPutFloat(addr + 4, regionOffsetY);
        MemoryUtil.memPutFloat(addr + 8, regionOffsetZ);
        MemoryUtil.memPutFloat(addr + 12, 0.0f);
        return index;
    }

    /** {@return the binding-5 write cursor: the index the next {@link #writeRegionOffset} call will take}. */
    int regionOffsetWriteCursor() {
        return this.regionOffsetWriteCursor;
    }

    /** Whether the pass being recorded samples the shadow map (item 537); set by the backend before the pass. */
    private boolean shadowLookupEnabled;
    /** How much of the sky-lit light a shadowed fragment keeps lost (0 = no darkening, 1 = all sky light). */
    private float shadowLookupDarkness;
    /** The light-space matrix (Vulkan projection x model-view) the shadow terrain was drawn with this frame. */
    private final org.joml.Matrix4f shadowLookupMatrix = new org.joml.Matrix4f();

    /**
     * Arms or disarms the shadow lookup for the next pass' uniform writes. {@code lightSpace} is copied; null
     * disarms. The backend calls this before every terrain pass, so a pass never inherits the previous one's state.
     */
    void setShadowLookup(org.joml.Matrix4fc lightSpace, float darkness) {
        if (lightSpace == null) {
            this.shadowLookupEnabled = false;
            return;
        }
        this.shadowLookupEnabled = true;
        this.shadowLookupDarkness = darkness;
        this.shadowLookupMatrix.set(lightSpace);
    }

    /** Sets the stride of one uniform block, computed from the device's alignment. */
    void setUniformStride(long stride) {
        this.uniformStride = stride;
    }

    /**
     * Resets the write cursor, which the pass does before it records. The frame slot comes with the
     * reset because the frame's dynamic light gather (item 326) belongs to the slot's buffer: arming
     * it here and performing it on the first uniform write keeps it once per frame without a second
     * frame-begin call.
     */
    void resetUniformWriteCursor(int frameSlot) {
        this.uniformWriteCursor = 0;
        this.regionOffsetWriteCursor = 0;
        int slot = uniformSlotIndex(frameSlot);
        this.uniformSlot = slot;
        this.dynamicLightSlot = slot;
        this.dynamicLightsGathered = false;
    }

    /** Releases all per-slot uniform and dynamic-light rings, if they exist. */
    void releaseUniforms() {
        if (this.uniformBuffers != null) {
            for (VulkanUniformBuffer buffer : this.uniformBuffers) {
                if (buffer != null) {
                    buffer.delete();
                }
            }
            this.uniformBuffers = null;
        }
        if (this.dynamicLightBuffers != null) {
            for (VulkanUniformBuffer buffer : this.dynamicLightBuffers) {
                if (buffer != null) {
                    buffer.delete();
                }
            }
            this.dynamicLightBuffers = null;
        }
        if (this.regionOffsetBuffers != null) {
            for (VulkanHostBuffer buffer : this.regionOffsetBuffers) {
                if (buffer != null) {
                    buffer.delete();
                }
            }
            this.regionOffsetBuffers = null;
        }
    }

    /** Creates the per-slot uniform rings or grows the active frame slot's ring to hold at least {@code required} bytes. */
    void ensureUniformCapacity(VulkanContext context, int frameSlot, long required) {
        int slot = uniformSlotIndex(frameSlot);
        if (this.uniformBuffers == null) {
            this.uniformBuffers = new VulkanUniformBuffer[VulkanTerrainPipeline.DESCRIPTOR_SLOTS];
            for (int i = 0; i < this.uniformBuffers.length; i++) {
                this.uniformBuffers[i] = new VulkanUniformBuffer(context, required);
            }
        } else {
            // Grow only the active slot (fence-confirmed idle at beginRecording); other slots may still be in flight.
            this.uniformBuffers[slot].ensureCapacity(required);
        }
        this.uniformBlocks = (int) (this.uniformBuffers[slot].size() / this.uniformStride);
        if (this.dynamicLightBuffers == null) {
            this.dynamicLightBuffers = new VulkanUniformBuffer[VulkanTerrainPipeline.DESCRIPTOR_SLOTS];
            for (int i = 0; i < this.dynamicLightBuffers.length; i++) {
                this.dynamicLightBuffers[i] =
                        new VulkanUniformBuffer(context, VulkanTerrainPipeline.DYNAMIC_LIGHT_BLOCK_SIZE);
            }
        }
    }

    /**
     * Creates the per-slot region offset buffers or grows the active frame slot's buffer to hold
     * at least {@code requiredEntries} vec4 entries. Called with the uniform reservation, before
     * anything is recorded: one entry per region draw is the frame's upper bound (region count
     * times the passes a frame can have), so the write cursor never crosses capacity mid-frame.
     * Growth goes through {@link VulkanHostBuffer#ensureCapacity}, which retires the old buffer
     * instead of deleting it under a recording; the pipeline notices the new handle on the next
     * {@code bindTextures} and rewrites the slot's descriptor.
     */
    void ensureRegionOffsetCapacity(VulkanContext context, int frameSlot, int requiredEntries) {
        int slot = uniformSlotIndex(frameSlot);
        long requiredBytes = (long) requiredEntries * REGION_OFFSET_ENTRY_BYTES;
        if (this.regionOffsetBuffers == null) {
            this.regionOffsetBuffers = new VulkanHostBuffer[VulkanTerrainPipeline.DESCRIPTOR_SLOTS];
            for (int i = 0; i < this.regionOffsetBuffers.length; i++) {
                this.regionOffsetBuffers[i] = new VulkanHostBuffer(context, requiredBytes,
                        VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            }
        } else {
            this.regionOffsetBuffers[slot].ensureCapacity(requiredBytes);
        }
    }



    // ---------------------------------------------------------------- region upload (R4)

    /** Per-colour counts of the first vertices, for the diagnostics that print them. */
    // Int-keyed: the census counts one entry per rebuilt mesh, and the boxed map allocated two Integers per mesh.
    private final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap colourCounts =
            new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
    private int colourBatches;
    private int verticesLogged;

    /** {@return how many vertices the diagnostics have logged}. */
    int verticesLogged() {
        return this.verticesLogged;
    }

    /** {@return the per-colour counts}. */
    it.unimi.dsi.fastutil.ints.Int2IntMap colourCounts() {
        return this.colourCounts;
    }

    /** {@return how many colour batches were counted}. */
    int colourBatches() {
        return this.colourBatches;
    }


    /** How many section uploads print their packed vertex colours; a property so a normal run stays quiet. */
    private static final int TERRAIN_VERTEX_COLOUR_SAMPLES =
            Integer.getInteger("celtium.vulkan.vertexColourSamples", 0).intValue();

    /** {@return the colour word at a byte offset, in the little-endian order the GPU reads}. */
    static int firstVertexColor(ByteBuffer vertices, int at) {
        java.nio.ByteOrder order = vertices.order();
        try {
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return vertices.getInt(at + 12);
        } finally {
            vertices.order(order);
        }
    }

    /** Colour word of the first vertex of a mesh, in the little-endian byte order the GPU reads. */
    static int firstVertexColor(ByteBuffer vertices) {
        java.nio.ByteOrder order = vertices.order();
        try {
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return vertices.getInt(12);
        } finally {
            vertices.order(order);
        }
    }

    void logFirstVertex(java.util.List<TerrainRenderer.PendingMesh> pending) {
        if (VulkanDiagnostics.enabled()) {
            for (TerrainRenderer.PendingMesh mesh : pending) {
                if (mesh.parts != null && mesh.sectionFirstColor != null) {
                    int colour = mesh.sectionFirstColor[mesh.sectionIndex];
                    colourCounts.put(colour, colourCounts.get(colour) + 1);
                }
            }
            this.colourBatches++;
            if (this.colourBatches == 1 || this.colourBatches % 20 == 0) {
                java.util.List<it.unimi.dsi.fastutil.ints.Int2IntMap.Entry> entries =
                        new java.util.ArrayList<>(colourCounts.int2IntEntrySet());
                java.util.Collections.sort(entries, (a, b) -> b.getIntValue() - a.getIntValue());
                StringBuilder line = new StringBuilder("mesh colour census (batches=" + this.colourBatches + "):");
                for (int i = 0; i < Math.min(5, entries.size()); i++) {
                    line.append(" 0x").append(Integer.toHexString(entries.get(i).getIntKey()))
                            .append(" x").append(entries.get(i).getIntValue());
                }
                record(line.toString());
            }
        }
        if (verticesLogged >= 8) {
            return;
        }
        for (TerrainRenderer.PendingMesh mesh : pending) {
            if (mesh.parts == null) {
                continue;
            }
            int stride = mesh.pass.vertexType().getVertexFormat().getStride();
            ByteBuffer vertices = mesh.parts.vertexBuffer().getDirectBuffer();
            if (vertices.remaining() < stride) {
                continue;
            }
            vertices.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            // The 32-byte head that used to be copied for every mesh now exists only for the meshes that get logged;
            // the vertex buffer is still alive here, which is what makes that possible.
            if (mesh.sectionFirstColor != null) {
                TerrainRenderer.RegionResources owner = null;
                byte[] head = new byte[Math.min(32, vertices.capacity())];
                for (int b = 0; b < head.length; b++) {
                    head[b] = vertices.get(b);
                }
                // The array belongs to the region's resources; only fill it when the caller can still find them.
                owner = mesh.firstVertexOwner;
                if (owner != null) {
                    owner.sectionFirstVertex[mesh.sectionIndex] = head;
                }
            }
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




    /** The game's configuration, handed over by the backend; the upload reads its chunk settings. */
    private RenderPassConfiguration<?> configuration;

    /** Gives the class the configuration the upload reads. */
    void setConfiguration(RenderPassConfiguration<?> configuration) {
        this.configuration = configuration;
    }

    private int colourSamples;

    /** {@return colourSamples}. */
    int colourSamples() {
        return this.colourSamples;
    }

    private int beforeAfterLogged;

    /** {@return beforeAfterLogged}. */
    int beforeAfterLogged() {
        return this.beforeAfterLogged;
    }

    /** {@return the vertex stride the region arenas are built with}. */
    int vertexStride() {
        return this.vertexStride;
    }

    /** Sets the vertex stride, which the backend computes from the format. */
    void setVertexStride(int stride) {
        this.vertexStride = stride;
    }

    private int vertexStride;

    /** The last region upload's byte count, which the acceptance reads. */
    private long lastUploadedBytes;

    /** {@return the last region upload's byte count}. */
    long lastUploadedBytes() {
        return this.lastUploadedBytes;
    }

    /**
     * Registers one freshly built section into the Tier-A resident table. A failure
     * leaves the section on the legacy route only — the upload that called this must
     * not fail with it — and is printed once, since a shape rejection here is a build
     * bug, not a runtime condition.
     */
    private void registerTierA(ChunkBuildOutput build, TerrainRenderPass pass, BuiltSectionMeshParts mesh) {
        try {
            org.soutalab.celtium.vulkan.terrain.AsyncSectionMesher.BuiltSectionA built =
                    this.terrainAHost.mesher().build(build.render.getChunkX(), build.render.getChunkY(),
                            build.render.getChunkZ(), this.terrainAHost.ordinalFor(pass), mesh, 0);
            if (built != null) {
                this.terrainAHost.engineFor(this.context, pass)
                        .register(built, this.terrainAHost.nextGeneration());
            }
        } catch (RuntimeException error) {
            // Counted per section for the residency gate (order (i)): a build
            // exception used to be a permanent, uninstrumented Tier-A leak.
            this.terrainAHost.noteBuildException(build.render.getChunkX(), build.render.getChunkY(),
                    build.render.getChunkZ(), pass);
            if (!this.terrainARegisterFailurePrinted) {
                this.terrainARegisterFailurePrinted = true;
                System.out.println("[Celtium] CELTIUM_TERRAIN_A_REGISTER_FAILED reason=" + error);
            }
        }
    }

    private SectionRenderDataStorage storageForPass(RenderRegion region, TerrainRenderPass pass,
            java.util.Map<TerrainRenderPass, SectionRenderDataStorage> cache) {
        SectionRenderDataStorage existing = cache.get(pass);
        if (existing != null) {
            return existing;
        }
        SectionRenderDataStorage created = region.createStorage(pass, configuration);
        cache.put(pass, created);
        return created;
    }

    /** Scratch lists for {@link #uploadRegion}: rebuilt at the head of each call and never retained. */
    private final java.util.ArrayList<TerrainRenderer.PendingMesh> uploadPendingScratch = new java.util.ArrayList<>();
    private final java.util.HashMap<TerrainRenderPass, SectionRenderDataStorage> uploadStorageScratch =
            new java.util.HashMap<>();

    void uploadRegion(RenderRegion region, List<ChunkTaskOutput> outputs, Runnable graphUpdateTrigger) {
        List<TerrainRenderer.PendingMesh> pending = this.uploadPendingScratch;
        // A previous call that threw before its tail released its meshes leaves them here holding a section's parts.
        // Releasing them before the clear keeps the scratch's ownership rule: nothing in it outlives a call.
        if (!pending.isEmpty()) {
            for (TerrainRenderer.PendingMesh previous : pending) {
                previous.release();
            }
            pending.clear();
        }
        boolean needIndexBuffer = false;
        // Resolved once for the whole upload: it used to be looked up again for every mesh.
        TerrainRenderer.RegionResources meshResources = resourcesFor(this.context, region, vertexStride());

        for (ChunkTaskOutput output : outputs) {
            if (output instanceof ChunkBuildOutput) {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.buildOutputs++;
                ChunkBuildOutput build = (ChunkBuildOutput) output;
                region.removeMeshes(build.render.getSectionIndex());
                if (this.terrainAHost != null) {
                    this.terrainAHost.unregisterSection(build.render.getChunkX(), build.render.getChunkY(),
                            build.render.getChunkZ());
                }
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, BuiltSectionMeshParts> meshEntry
                        : build.meshes.reference2ReferenceEntrySet()) {
                    BuiltSectionMeshParts mesh = meshEntry.getValue();
                    if (mesh == null) {
                        continue;
                    }
                    needIndexBuffer |= mesh.indexBuffer() != null;
                    pending.add(TerrainRenderer.PendingMesh.obtain(build.render.getSectionIndex(), meshEntry.getKey(), mesh, null,
                            meshResources.sectionFirstColor, meshResources));
                    if (this.terrainAHost != null
                            && vertexStride() == org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16.STRIDE) {
                        registerTierA(build, meshEntry.getKey(), mesh);
                    } else if (this.terrainAHost != null) {
                        // Stride gate (order (i)): this section never reaches
                        // registerTierA while the stride differs — count it as
                        // a permanent leak instead of leaving it invisible.
                        this.terrainAHost.noteStrideGate(build.render.getChunkX(), build.render.getChunkY(),
                                build.render.getChunkZ(), meshEntry.getKey());
                    }
                }
            } else if (output instanceof ChunkSortOutput) {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.sortOutputs++;
                ChunkSortOutput sort = (ChunkSortOutput) output;
                needIndexBuffer = true;
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, ChunkSortOutput.SortedMesh> meshEntry
                        : sort.meshes.reference2ReferenceEntrySet()) {
                    SectionRenderDataStorage storage = region.getStorage(meshEntry.getKey());
                    if (storage != null) {
                        storage.removeIndexBuffer(sort.render.getSectionIndex());
                    }
                    if (this.terrainAHost != null && meshEntry.getValue() != null) {
                        // Wave-2 item 4: the re-sort regenerated this section's sorted
                        // indices; the resident Tier-A record must carry the new order.
                        this.terrainAHost.updateSortedSection(sort.render.getChunkX(), sort.render.getChunkY(),
                                sort.render.getChunkZ(), meshEntry.getKey(), meshEntry.getValue().indexData());
                    }
                    pending.add(TerrainRenderer.PendingMesh.obtain(sort.render.getSectionIndex(), meshEntry.getKey(), null,
                            meshEntry.getValue(), null, null));
                }
            } else {
                throw new IllegalStateException("Unexpected result type: " + output.getClass().getName());
            }
        }

        if (pending.isEmpty()) {
            return;
        }

        TerrainRenderer.RegionResources resources = meshResources;
        // One map lookup per render pass rather than one per mesh: a build output carries several meshes per section
        // and every one of them asked the region for the same storage.
        java.util.Map<TerrainRenderPass, SectionRenderDataStorage> storageByPass = this.uploadStorageScratch;
        storageByPass.clear();
        long uploaded = 0;

        for (TerrainRenderer.PendingMesh mesh : pending) {
            if (mesh.parts != null) {
                ByteBuffer vertices = mesh.parts.vertexBuffer().getDirectBuffer();
                int vertexBytes = mesh.parts.vertexBuffer().getLength();
                if (VulkanDiagnostics.enabled() && mesh.sectionFirstColor != null) {
                    mesh.sectionFirstColor[mesh.sectionIndex] = firstVertexColor(vertices);
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
                long beforeAddress = MemoryUtil.memAddress(vertices);
                VulkanArena.Segment vertexSegment = resources.vertexArena.allocate(vertexBytes);
                resources.vertexArena.write(vertexSegment.byteOffset(), MemoryUtil.memAddress(vertices), vertexBytes);
                if (beforeAfterLogged < 4) {
                    beforeAfterLogged++;
                    int beforeColour = firstVertexColor(vertices);
                    int mid = Math.max(0, (vertexBytes / 2 / 32) * 32);
                    int midColour = firstVertexColor(vertices, mid);
                    record("copy check: before=0x" + Integer.toHexString(beforeColour)
                            + " after=0x" + Integer.toHexString(firstVertexColor(vertices))
                            + " address=" + beforeAddress + " segmentOffset=" + vertexSegment.byteOffset()
                            + " bytes=" + vertexBytes + " position=" + vertices.position()
                            + " mid=0x" + Integer.toHexString(midColour));
                }
                uploaded += vertexBytes;
                org.soutalab.celtium.vulkan.DrawCounters.noteUpload(vertexBytes);

                VulkanArena.Segment indexSegment = null;
                if (mesh.parts.indexBuffer() != null) {
                    ByteBuffer indices = mesh.parts.indexBuffer().getDirectBuffer();
                    int indexBytes = mesh.parts.indexBuffer().getLength();
                    VulkanArena indicesArena = indexArena(this.context, resources);
                    indexSegment = indicesArena.allocate(indexBytes);
                    indicesArena.write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                    uploaded += indexBytes;
                    org.soutalab.celtium.vulkan.DrawCounters.noteUpload(indexBytes);
                }

                SectionRenderDataStorage storage = storageForPass(region, mesh.pass, storageByPass);
                storage.setMeshes(mesh.sectionIndex, vertexSegment, indexSegment, mesh.parts.ranges());
            } else if (mesh.sorted != null) {
                ByteBuffer indices = mesh.sorted.indexData().getDirectBuffer();
                int indexBytes = mesh.sorted.indexData().getLength();
                VulkanArena.Segment indexSegment = indexArena(this.context, resources).allocate(indexBytes);
                indexArena(this.context, resources).write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                uploaded += indexBytes;

                SectionRenderDataStorage storage = storageForPass(region, mesh.pass, storageByPass);
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
        for (TerrainRenderer.PendingMesh mesh : pending) {
            mesh.release();
        }
        pending.clear();

    }

}
