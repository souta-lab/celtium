package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.lwjgl.vulkan.VK10;
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

        PendingMesh(int sectionIndex, TerrainRenderPass pass, BuiltSectionMeshParts parts,
                    ChunkSortOutput.SortedMesh sorted, int[] sectionFirstColor) {
            this.sectionIndex = sectionIndex;
            this.pass = pass;
            this.parts = parts;
            this.sorted = sorted;
            this.sectionFirstColor = sectionFirstColor;
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
    }

    /** Binds a 32-bit index buffer, for the same reason the vertex binding takes the command buffer. */
    void bindIndexBuffer(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, VulkanBuffer buffer) {
        org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer(commandBuffer, buffer.buffer(), 0L,
                org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32);
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

    private VulkanBuffer indirectBuffer;
    private long indirectGrowths;
    private String lastTerrainIndirectFallback;

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
        int needed = size * INDIRECT_COMMAND_BYTES;
        if (!this.ensureIndirectBuffer(context, needed)) {
            this.terrainIndirectFallbacks++;
            this.lastTerrainIndirectFallback = "the indirect packet buffer could not be created";
            return false;
        }
        this.bindIndexBuffer(commandBuffer, regionIndex ? resources.indexArena.buffer()
                : sharedIndexBuffers.get(primitiveType));
        ByteBuffer packets = stack.calloc(needed);
        for (int i = 0; i < size; i++) {
            long byteOffset = batch.indexByteOffset(i);
            packets.putInt(i * INDIRECT_COMMAND_BYTES, batch.elementCount(i));
            packets.putInt(i * INDIRECT_COMMAND_BYTES + 4, 1);
            packets.putInt(i * INDIRECT_COMMAND_BYTES + 8, regionIndex ? (int) (byteOffset / 4) : 0);
            packets.putInt(i * INDIRECT_COMMAND_BYTES + 12, batch.baseVertex(i));
            // firstInstance stays 0: drawIndirectFirstInstance is enabled only when the device reported it, and this
            // path never needs anything but 0.
            packets.putInt(i * INDIRECT_COMMAND_BYTES + 16, 0);
        }
        if (TERRAIN_INDIRECT_COMPARE) {
            // The same enumeration the direct loop would perform, folded into its own counters rather than issued.
            for (int i = 0; i < size; i++) {
                long byteOffset = batch.indexByteOffset(i);
                long directFirstIndex = regionIndex ? (byteOffset / 4) : 0L;
                this.terrainDirectEquivalentDraws++;
                this.terrainDirectEquivalentChecksum = commandHash(this.terrainDirectEquivalentChecksum,
                        batch.elementCount(i), directFirstIndex, batch.baseVertex(i));
                this.terrainIndirectCommandDraws++;
                this.terrainIndirectCommandChecksum = commandHash(this.terrainIndirectCommandChecksum,
                        batch.elementCount(i), directFirstIndex, batch.baseVertex(i));
            }
        }
        long address = MemoryUtil.memAddress(packets);
        int first = 0;
        while (first < size) {
            int count = (int) Math.min(maxDraws, (long) (size - first));
            if (count != size - first) {
                this.terrainIndirectClampEvents++;
            }
            long offsetBytes = (long) first * INDIRECT_COMMAND_BYTES;
            this.indirectBuffer.write(offsetBytes, address + offsetBytes, count * INDIRECT_COMMAND_BYTES);
            vkCmdDrawIndexedIndirect(commandBuffer, this.indirectBuffer.buffer(), offsetBytes, count,
                    INDIRECT_COMMAND_BYTES);
            this.terrainIndirectCalls++;
            this.terrainIndirectDraws += count;
            this.statistics.addLastDrawCommands(count);
            first += count;
        }
        return true;
    }

    boolean ensureIndirectBuffer(VulkanContext context, int bytes) {
        try {
            if (this.indirectBuffer != null && this.indirectBuffer.size() >= bytes) {
                return true;
            }
            if (this.indirectBuffer != null) {
                this.indirectBuffer.delete();
                this.indirectBuffer = null;
            }
            long size = Math.max(bytes, 64L * 1024L);
            this.indirectBuffer = new VulkanBuffer(context, size,
                    org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT);
            this.indirectGrowths++;
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.lastTerrainIndirectFallback = "indirect buffer: " + describe(error);
            return false;
        }
    }

}
