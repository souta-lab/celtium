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

    /** One region's arenas: the vertex arena always, the index arena once the region has indices. */
    static final class RegionResources {
        final VulkanArena vertexArena;
        final int[] sectionFirstColor = new int[RenderRegion.REGION_SIZE];
        /** The first 32 bytes of the first vertex of each section, as the upload saw them. */
        final byte[][] sectionFirstVertex = new byte[RenderRegion.REGION_SIZE][];
        VulkanArena indexArena;

        RegionResources(VulkanContext context, int vertexStride) {
            this.vertexArena = new VulkanArena(context, 512 * 1024, VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, vertexStride);
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

    /** One mesh an upload produced and has not committed yet. */
    static final class PendingMesh {
        final int sectionIndex;
        final TerrainRenderPass pass;
        final BuiltSectionMeshParts parts;
        final ChunkSortOutput.SortedMesh sorted;
        final int[] sectionFirstColor;
        /** The region resources the diagnostic head belongs to, so logFirstVertex can fill it without a lookup. */
        final TerrainRenderer.RegionResources firstVertexOwner;

        PendingMesh(int sectionIndex, TerrainRenderPass pass, BuiltSectionMeshParts parts,
                    ChunkSortOutput.SortedMesh sorted, int[] sectionFirstColor,
                    TerrainRenderer.RegionResources firstVertexOwner) {
            this.sectionIndex = sectionIndex;
            this.pass = pass;
            this.parts = parts;
            this.sorted = sorted;
            this.sectionFirstColor = sectionFirstColor;
            this.firstVertexOwner = firstVertexOwner;
        }
    }

    private final Map<RenderRegion, RegionResources> regionResources = new ConcurrentHashMap<RenderRegion, RegionResources>();

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
            resources = new RegionResources(context, vertexStride);
            this.regionResources.put(region, resources);
        }
        return resources;
    }

    /** {@return the region's resources when it has any, or null - the draw path must not create an arena}. */
    RegionResources resourcesIfPresent(RenderRegion region) {
        return this.regionResources.get(region);
    }

    /** {@return the region's index arena, creating it on first use}. */
    VulkanArena indexArena(VulkanContext context, RegionResources resources) {
        if (resources.indexArena == null) {
            resources.indexArena = new VulkanArena(context, 64 * 1024, VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 4);
        }
        return resources.indexArena;
    }

    /** Records a section whose upload failed, so the next pass rebuilds it. {@return whether it was new}. */
    boolean noteFailedUpload(RenderSection section) {
        return this.failedUploads.add(section);
    }

    /** {@return whether any failed upload is waiting}. */
    boolean isEmpty() {
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

    /** {@return how many arenas the regions hold}. */
    int arenaCount() {
        int total = 0;
        for (RegionResources resources : this.regionResources.values()) {
            total += resources.bufferCount();
        }
        return total;
    }

    /** Releases every region's arenas. Called when the world or the renderer goes away. */
    void releaseAll() {
        for (RegionResources resources : this.regionResources.values()) {
            resources.delete();
        }
        this.regionResources.clear();
        this.failedUploads.clear();
    }

    /**
     * Binds the region's vertex buffer. The command buffer is a parameter because it is the frame's, not this class's:
     * the terrain pass records into it but does not own it (refactoring plan R4, second half).
     */
    void bindVertexBuffer(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, org.lwjgl.system.MemoryStack stack,
            RegionResources resources) {
        java.nio.LongBuffer buffers = stack.mallocLong(1);
        buffers.put(0, resources.vertexArena.buffer().buffer());
        java.nio.LongBuffer offsets = stack.mallocLong(1);
        offsets.put(0, 0L);
        org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers(commandBuffer, 0, buffers, offsets);
        this.shadowVertexBuffer = resources.vertexArena.buffer().buffer();
    }

    /** Geometry the shadow pass may draw, captured from the pass's own bindings and indirect range. */
    private long shadowVertexBuffer;
    private long shadowIndexBuffer;
    private long shadowIndirectBuffer;
    private long shadowIndirectOffset;
    private int shadowIndirectCount;

    /** {@return the vertex buffer the terrain pass bound}, for the shadow pass. */
    public long shadowVertexBuffer() {
        return this.shadowVertexBuffer;
    }

    /** {@return the index buffer the terrain pass bound}. */
    public long shadowIndexBuffer() {
        return this.shadowIndexBuffer;
    }

    /** {@return the indirect command buffer and range the terrain pass issued}. */
    public long shadowIndirectBuffer() {
        return this.shadowIndirectBuffer;
    }

    public long shadowIndirectOffset() {
        return this.shadowIndirectOffset;
    }

    public int shadowIndirectCount() {
        return this.shadowIndirectCount;
    }

    /** Clears the capture, so each frame's shadow draw is the geometry of that frame. */
    public void resetShadowCapture() {
        this.shadowIndirectCount = 0;
        this.shadowIndirectBuffer = 0L;
    }

    /** Binds a 32-bit index buffer, for the same reason the vertex binding takes the command buffer. */
    void bindIndexBuffer(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, VulkanBuffer buffer) {
        org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer(commandBuffer, buffer.buffer(), 0L,
                org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32);
        this.shadowIndexBuffer = buffer.buffer();
    }


    /** The context the pipelines are built with; the backend owns it and hands it over. */
    private VulkanContext context;
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

    /** Releases every terrain pipeline and forgets them. */
    void releasePipelines() {
        for (VulkanTerrainPipeline pipeline : this.pipelines.values()) {
            if (pipeline != null) {
                pipeline.delete();
            }
        }
        this.pipelines.clear();
    }

    // ---------------------------------------------------------------- terrain pipelines (R4, second half)

    /** The pipelines built per terrain render pass: keyed by the pass, created on first use. */
    private final java.util.Map<TerrainRenderPass, VulkanTerrainPipeline> pipelines =
            new java.util.concurrent.ConcurrentHashMap<TerrainRenderPass, VulkanTerrainPipeline>();

    VulkanTerrainPipeline pipelineFor(TerrainRenderPass pass) {
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


    // ---------------------------------------------------------------- indirect batch (R4)

    private static final int INDIRECT_COMMAND_BYTES = 20;
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

    static long commandHash(long checksum, int indexCount, long firstIndex, int vertexOffset) {
        checksum = checksum * 1099511628211L + indexCount;
        checksum = checksum * 1099511628211L + firstIndex;
        checksum = checksum * 1099511628211L + vertexOffset;
        return checksum;
    }

    boolean tryIndirectBatch(VulkanContext context, org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            org.lwjgl.system.MemoryStack stack,
            java.util.Map<org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType, VulkanBuffer> sharedIndexBuffers,
            RegionResources resources,
            VulkanMultiDrawBatch batch, boolean sortedPass,
            ChunkPrimitiveType primitiveType) {
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
            for (int i = 0; i < size; i++) {
                if (batch.indexByteOffset(i) != 0) {
                    this.terrainIndirectFallbacks++;
                    this.lastTerrainIndirectFallback = "unsorted batch with a non-zero index offset (mixed sources)";
                    return false;
                }
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
        this.bindIndexBuffer(commandBuffer, regionIndex ? resources.indexArena.buffer()
                : sharedIndexBuffers.get(primitiveType));
        // Write the packets directly into the mapped page at the draw's offset: the HOST_COHERENT memory needs no
        // flush, and vkQueueSubmit's host-side ordering guarantee publishes these record-time writes to the
        // submission that carries the draw. The per-batch staging snapshot + vkCmdCopyBuffer + deferred upload this
        // replaces was the dominating cost of the indirect path (measured: recDraw ~3 ms/frame while the draw call
        // itself is ~1 vkCmd).
        long packets = this.indirectBuffer.mappedAddress() + this.indirectBatchOffset;
        for (int i = 0; i < size; i++) {
            long byteOffset = batch.indexByteOffset(i);
            long packet = packets + (long) i * INDIRECT_COMMAND_BYTES;
            MemoryUtil.memPutInt(packet, batch.elementCount(i));
            MemoryUtil.memPutInt(packet + 4, 1);
            MemoryUtil.memPutInt(packet + 8, regionIndex ? (int) (byteOffset / 4) : 0);
            MemoryUtil.memPutInt(packet + 12, batch.baseVertex(i));
            // firstInstance stays 0: drawIndirectFirstInstance is enabled only when the device reported it, and this
            // path never needs anything but 0.
            MemoryUtil.memPutInt(packet + 16, 0);
        }
        if (TERRAIN_INDIRECT_COMPARE) {
            // Compare the encoded CPU packet with the direct enumeration, not the same CPU values twice. This is
            // an encoder check, not a GPU readback or proof of the packet's resource lifetime.
            for (int i = 0; i < size; i++) {
                long byteOffset = batch.indexByteOffset(i);
                long directFirstIndex = regionIndex ? (byteOffset / 4) : 0L;
                long packet = packets + (long) i * INDIRECT_COMMAND_BYTES;
                this.terrainDirectEquivalentDraws++;
                this.terrainDirectEquivalentChecksum = commandHash(this.terrainDirectEquivalentChecksum,
                        batch.elementCount(i), directFirstIndex, batch.baseVertex(i));
                this.terrainIndirectCommandDraws++;
                this.terrainIndirectCommandChecksum = commandHash(this.terrainIndirectCommandChecksum,
                        MemoryUtil.memGetInt(packet),
                        Integer.toUnsignedLong(MemoryUtil.memGetInt(packet + 8)),
                        MemoryUtil.memGetInt(packet + 12));
            }
        }
        int first = 0;
        while (first < size) {
            int count = (int) Math.min(maxDraws, (long) (size - first));
            if (count != size - first) {
                this.terrainIndirectClampEvents++;
            }
            long offsetBytes = this.indirectBatchOffset + (long) first * INDIRECT_COMMAND_BYTES;
            vkCmdDrawIndexedIndirect(commandBuffer, this.indirectBuffer.buffer(), offsetBytes, count,
                    INDIRECT_COMMAND_BYTES);
            // Captured for the shadow pass: one batch's commands, offset and count together. Accumulating the counts while
            // keeping the first offset made the range run past the buffer (VUID-vkCmdDrawIndexedIndirect-drawCount-00540
            // with drawCount 3246 at offset 800 in leg shadowTerrain5), so only the first batch of the frame is kept.
            if (this.shadowIndirectCount == 0) {
                this.shadowIndirectBuffer = this.indirectBuffer.buffer();
                this.shadowIndirectOffset = offsetBytes;
                this.shadowIndirectCount = count;
            }
            this.terrainIndirectCalls++;
            this.terrainIndirectDraws += count;
            this.statistics.addLastDrawCommands(count);
            first += count;
        }
        return true;
    }

    /** Called after this command slot's prior fence completed and its command buffer was reset successfully. */
    void beginIndirectRecording(int slot) {
        if (slot < 0 || slot >= FrameSubmissionScheduler.FRAME_SLOTS) {
            throw new IllegalArgumentException("invalid indirect recording slot: " + slot);
        }
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

    /** {@return the uniform ring, which the pass reserves against before recording}. */
    VulkanUniformBuffer uniforms() {
        return this.uniforms;
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

    private VulkanUniformBuffer uniforms;
    private long uniformStride;
    private int uniformBlocks;
    private long uniformWriteCursor;
    private boolean uniformsLogged;

    /** How many region draws have passed, so the fog line can be compared between legs at the same index. */
    private int fogLogDraws;
    /** The fog line repeats every this many region draws (plus the first). */
    private static final int FOG_LOG_EVERY = 200;
    private static final float MIP_BIAS_ADD =
            Float.parseFloat(System.getProperty("celtium.vulkan.mipBiasAdd", "0.0"));

    static int fogMode() {
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

    long writeUniforms(RenderRegion region, TerrainRenderPass pass, CameraTransform camera) {
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
        // The line is repeated every FOG_LOG_EVERY region draws as well as on the first one, and carries the draw
        // index. It printed once, which made the two legs' single samples different frames and so incomparable: the
        // bridge leg read [0.3816, 0.4421, 0.545] and the GL-less leg [0.4786, 0.5544, 0.6834] with no way to tell
        // whether that was a difference between the backends or between the frames they happened to log.
        this.fogLogDraws++;
        if (!uniformsLogged || this.fogLogDraws % FOG_LOG_EVERY == 0) {
            uniformsLogged = true;
            // stdout, not record(): the diagnostics list is capped and this line was being dropped from it.
            System.out.println("[Celtium] CELTIUM_FOG draw=" + this.fogLogDraws + " fogColor=" + java.util.Arrays.toString(fogColor)
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
        // A/B only: a constant fragment colour that reaches the frame image says the geometry is rasterised and the
        // loss is in the fragment maths; a frame that stays black says nothing is being rasterised at all.
        block.putInt(base + 80, Boolean.getBoolean("celtium.vulkan.solidTerrain") ? 1 : 0);
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

    /** Sets the stride of one uniform block, computed from the device's alignment. */
    void setUniformStride(long stride) {
        this.uniformStride = stride;
    }

    /** Resets the write cursor, which the pass does before it records. */
    void resetUniformWriteCursor() {
        this.uniformWriteCursor = 0;
    }

    /** Releases the uniform ring, if it exists. */
    void releaseUniforms() {
        if (this.uniforms != null) {
            this.uniforms.delete();
            this.uniforms = null;
        }
    }

    /** Creates or grows the uniform ring so it holds at least the required bytes. */
    void ensureUniformCapacity(VulkanContext context, long required) {
        if (this.uniforms == null) {
            this.uniforms = new VulkanUniformBuffer(context, required);
        } else {
            this.uniforms.ensureCapacity(required);
        }
        this.uniformBlocks = (int) (this.uniforms.size() / this.uniformStride);
    }



    // ---------------------------------------------------------------- region upload (R4)

    /** Per-colour counts of the first vertices, for the diagnostics that print them. */
    private final java.util.HashMap<Integer, Integer> colourCounts = new java.util.HashMap<Integer, Integer>();
    private int colourBatches;
    private int verticesLogged;

    /** {@return how many vertices the diagnostics have logged}. */
    int verticesLogged() {
        return this.verticesLogged;
    }

    /** {@return the per-colour counts}. */
    java.util.Map<Integer, Integer> colourCounts() {
        return this.colourCounts;
    }

    /** {@return how many colour batches were counted}. */
    int colourBatches() {
        return this.colourBatches;
    }


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
        for (TerrainRenderer.PendingMesh mesh : pending) {
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
        for (TerrainRenderer.PendingMesh mesh : pending) {
            if (mesh.parts != null && mesh.sectionFirstColor != null) {
                // Counted for every mesh, not just the first few, so a colour that only some sections carry shows up.
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

    void uploadRegion(RenderRegion region, List<ChunkTaskOutput> outputs, Runnable graphUpdateTrigger) {
        List<TerrainRenderer.PendingMesh> pending = new ArrayList<>();
        boolean needIndexBuffer = false;
        // Resolved once for the whole upload: it used to be looked up again for every mesh.
        TerrainRenderer.RegionResources meshResources = resourcesFor(this.context, region, vertexStride());

        for (ChunkTaskOutput output : outputs) {
            if (output instanceof ChunkBuildOutput) {
                org.embeddedt.embeddium.impl.render.chunk.BuildFlowCounters.buildOutputs++;
                ChunkBuildOutput build = (ChunkBuildOutput) output;
                region.removeMeshes(build.render.getSectionIndex());
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, BuiltSectionMeshParts> meshEntry
                        : build.meshes.reference2ReferenceEntrySet()) {
                    BuiltSectionMeshParts mesh = meshEntry.getValue();
                    if (mesh == null) {
                        continue;
                    }
                    needIndexBuffer |= mesh.indexBuffer() != null;
                    pending.add(new TerrainRenderer.PendingMesh(build.render.getSectionIndex(), meshEntry.getKey(), mesh, null,
                            meshResources.sectionFirstColor, meshResources));
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
                    pending.add(new TerrainRenderer.PendingMesh(sort.render.getSectionIndex(), meshEntry.getKey(), null,
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
        java.util.Map<TerrainRenderPass, SectionRenderDataStorage> storageByPass = new java.util.HashMap<>();
        long uploaded = 0;

        for (TerrainRenderer.PendingMesh mesh : pending) {
            if (mesh.parts != null) {
                ByteBuffer vertices = mesh.parts.vertexBuffer().getDirectBuffer();
                int vertexBytes = mesh.parts.vertexBuffer().getLength();
                if (mesh.sectionFirstColor != null) {
                    // One int per mesh: it feeds the colour census in logFirstVertex. The 32-byte head that used to be
                    // copied here (with an order save/restore and a new byte[32] per mesh) only ever fed a bounded
                    // diagnostic line; it is now built there, for the few meshes that are logged, from the same vertex
                    // buffer - which is still alive at that point.
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

                VulkanArena.Segment indexSegment = null;
                if (mesh.parts.indexBuffer() != null) {
                    ByteBuffer indices = mesh.parts.indexBuffer().getDirectBuffer();
                    int indexBytes = mesh.parts.indexBuffer().getLength();
                    VulkanArena indicesArena = indexArena(this.context, resources);
                    indexSegment = indicesArena.allocate(indexBytes);
                    indicesArena.write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                    uploaded += indexBytes;
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

    }

}
