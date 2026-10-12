package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.lwjgl.vulkan.VK10;

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

}
