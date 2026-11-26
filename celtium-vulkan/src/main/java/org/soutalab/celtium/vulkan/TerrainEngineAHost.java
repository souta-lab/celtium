package org.soutalab.celtium.vulkan;

import java.util.IdentityHashMap;
import java.util.Map;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.hal.SlabAllocator;
import org.soutalab.celtium.vulkan.terrain.AsyncSectionMesher;
import org.soutalab.celtium.vulkan.terrain.TerrainResidencyGate;
import org.soutalab.celtium.vulkan.terrain.TerrainSectionStore;
import org.soutalab.celtium.vulkan.terrain.TerrainUploadBudget;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

/**
 * Owns the Tier-A terrain state for one backend: the shared
 * {@link TerrainSectionStore}, the upload budget, and one
 * {@link TerrainEngineA} executor per render pass. Pass ordinals are assigned by
 * identity in first-use order and are the same ordinals the SectionDesc records
 * carry, so registration and culling always agree on them.
 * <p>
 * The host also owns the pass-independent GPU resources every executor shares
 * (designer audit 2026-11-25, Stage 4 item 5-②): the geometry slab allocator —
 * one allocator for all passes keeps the terrain geometry within the shared
 * slab cap instead of multiplying it by the pass count — and the static quad
 * index buffer, whose contents depend only on
 * {@link TerrainSectionStore#MAX_QUADS_PER_SLAB}. Both are created lazily and
 * deleted here, after every engine, because the allocator's slabs are the
 * engines' slab buffers' memory.
 * <p>
 * The resident store images and arenas are shared the same way (R-15): the
 * SectionDesc table and the factor-base image are one store image every pass
 * reads identically, and the factor and sorted arenas are partial images of
 * the store's single cross-pass offset spaces, so one buffer per kind holds
 * the union at the store's own offsets. Until R-15 each engine kept a private
 * copy of all four — the two images byte-identical duplicates, the two arenas
 * 32 MiB each per pass — for no reader's benefit.
 * <p>
 * Deferred arena releases are stamped and collected in the context's slab
 * completion currency ({@link VulkanContext#celtium$slabReleaseValue()} /
 * {@link VulkanContext#celtium$slabCompletedValue()}): on a timeline device
 * collection waits for real GPU completion, off one it waits for a later
 * drain. Until Stage 2/3 landed, the stamps were the host's own generation
 * counter with a two-generation lag — an interim rule approximating the
 * frames-in-flight depth; the counter remains, but only as the registration
 * counter written into each section desc, no longer as a completion proof.
 */
final class TerrainEngineAHost {
    // Tier-A is the default terrain route (wave-2 cutover, 2026-11-25): the property
    // remains only as a bisection switch to turn it off, not a parallel route flag.
    // The A->B overhaul's Stage 4 escape flag -Dceltium.terrain.engine=legacy turns
    // it off as well (the whole engine falls back to the legacy terrain route).
    static final boolean ENABLED =
            !"legacy".equals(System.getProperty("celtium.terrain.engine"))
                    && Boolean.parseBoolean(System.getProperty("celtium.vulkan.terrainEngineA", "true"));

    private final AsyncSectionMesher mesher = new AsyncSectionMesher();
    private final TerrainSectionStore store = new TerrainSectionStore();
    private final TerrainUploadBudget budget = new TerrainUploadBudget(
            Long.getLong("celtium.vulkan.terrainUploadMiB", 4L) * 1024 * 1024);
    private final TerrainResidencyGate residencyGate = new TerrainResidencyGate();
    private final Map<TerrainRenderPass, Integer> ordinals = new IdentityHashMap<>();
    private final Map<TerrainRenderPass, TerrainEngineA> engines = new IdentityHashMap<>();
    private long generation;
    private VulkanContext context;
    private SlabAllocator geometrySlabAllocator;
    private VulkanHostBuffer sharedIndexBuffer;
    // Resident GPU images of the store (host-visible, written through the
    // mapping), shared by every pass (R-15; see the class javadoc).
    private VulkanHostBuffer descBuffer;
    private VulkanHostBuffer factorBaseBuffer;
    private long uploadedDescGeneration = -1;
    private VulkanHostBuffer factorBuffer;
    private VulkanHostBuffer sortedBuffer;

    AsyncSectionMesher mesher() {
        return this.mesher;
    }

    TerrainSectionStore store() {
        return this.store;
    }

    TerrainResidencyGate residencyGate() {
        return this.residencyGate;
    }

    /**
     * Counts one section the renderer's stride gate kept out of Tier-A
     * (order (i)): the region was not built with the TerrainVertex16 format,
     * so {@code registerTierA} was never called for it — a permanent leak the
     * engine-side counters cannot see.
     */
    void noteStrideGate(int sectionX, int sectionY, int sectionZ, TerrainRenderPass pass) {
        this.residencyGate.noteMissing(new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinalFor(pass)),
                TerrainResidencyGate.Reason.STRIDE_GATE);
    }

    /** Counts one section whose Tier-A build threw before registration (order (i)). */
    void noteBuildException(int sectionX, int sectionY, int sectionZ, TerrainRenderPass pass) {
        this.residencyGate.noteMissing(new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinalFor(pass)),
                TerrainResidencyGate.Reason.BUILD_EXCEPTION);
    }

    long nextGeneration() {
        return ++this.generation;
    }

    int ordinalFor(TerrainRenderPass pass) {
        Integer ordinal = this.ordinals.get(pass);
        if (ordinal == null) {
            ordinal = this.ordinals.size();
            this.ordinals.put(pass, ordinal);
        }
        return ordinal;
    }

    TerrainEngineA engineFor(VulkanContext context, TerrainRenderPass pass) {
        this.context = context;
        TerrainEngineA engine = this.engines.get(pass);
        if (engine == null) {
            engine = new TerrainEngineA(context, ordinalFor(pass),
                    "pass-" + ordinalFor(pass), this.store, this.budget, this.residencyGate, this);
            this.engines.put(pass, engine);
        }
        return engine;
    }

    /**
     * The one geometry slab allocator shared by every pass' executor, created
     * lazily from the first slab buffer's own memory-type choice. Every pass'
     * slab buffers carry the same usage over the shared store's slab size, so
     * the first buffer's memory type is every buffer's; a later call's
     * arguments are the same values by construction and are not re-read.
     */
    SlabAllocator geometrySlabAllocator(VulkanContext context, int memoryType, long slabBytes) {
        if (this.geometrySlabAllocator == null) {
            this.geometrySlabAllocator = new SlabAllocator(context, "terrainGeometry", memoryType,
                    slabBytes, org.soutalab.celtium.vulkan.terrain.TerrainSlabAllocator.DEFAULT_MAX_SLABS, true);
        }
        return this.geometrySlabAllocator;
    }

    /**
     * The static quad index buffer shared by every pass: its contents are the
     * fixed quad triangulation for {@link TerrainSectionStore#MAX_QUADS_PER_SLAB}
     * quads and depend on nothing a pass owns. Written once, at creation.
     */
    VulkanHostBuffer sharedIndexBuffer(VulkanContext context) {
        if (this.sharedIndexBuffer == null) {
            int quads = TerrainSectionStore.MAX_QUADS_PER_SLAB;
            VulkanHostBuffer buffer = new VulkanHostBuffer(context, (long) quads * 6 * 4,
                    VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            long address = buffer.mappedAddress();
            for (int q = 0; q < quads; q++) {
                long at = address + (long) q * 6 * 4;
                int baseVertex = q * 4;
                MemoryUtil.memPutInt(at, baseVertex);
                MemoryUtil.memPutInt(at + 4, baseVertex + 1);
                MemoryUtil.memPutInt(at + 8, baseVertex + 2);
                MemoryUtil.memPutInt(at + 12, baseVertex);
                MemoryUtil.memPutInt(at + 16, baseVertex + 2);
                MemoryUtil.memPutInt(at + 20, baseVertex + 3);
            }
            this.sharedIndexBuffer = buffer;
        }
        return this.sharedIndexBuffer;
    }

    /**
     * The shared SectionDesc table itself, for the cull compute's descriptor
     * bookkeeping, or {@code null} before the first upload. Callers reach this
     * only after an upload call in the same frame head, the engine's previous
     * precondition for its private copy.
     */
    VulkanHostBuffer descBuffer() {
        return this.descBuffer;
    }

    /** Raw VkBuffer of the shared desc table (graphics binding 6), uploading the store image first if it moved. */
    long descBufferHandle(VulkanContext context) {
        uploadDescImageIfMoved(context);
        return this.descBuffer == null ? 0L : this.descBuffer.buffer();
    }

    /** Raw VkBuffer of the shared factor-base image (graphics binding 7), uploading first if it moved. */
    long factorBaseBufferHandle(VulkanContext context) {
        uploadDescImageIfMoved(context);
        return this.factorBaseBuffer == null ? 0L : this.factorBaseBuffer.buffer();
    }

    /**
     * Re-uploads the store's desc and factor-base images into the shared buffers
     * when the store's image generation has moved. The store packs every pass'
     * records into the one image, so one upload serves every pass; before the
     * sharing, each engine re-uploaded the identical image into its own copy,
     * once per pass per generation move.
     */
    void uploadDescImageIfMoved(VulkanContext context) {
        long generation = this.store.imageGeneration();
        if (generation == this.uploadedDescGeneration && this.descBuffer != null) {
            return;
        }
        int[] descImage = this.store.descImage();
        int[] factorBaseImage = this.store.factorBaseImage();
        long descBytes = Math.max(48L, (long) descImage.length * 4L);
        if (this.descBuffer == null) {
            this.descBuffer = new VulkanHostBuffer(context, descBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            this.factorBaseBuffer = new VulkanHostBuffer(context, Math.max(4L, (long) factorBaseImage.length * 4L),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            // These two buffers are named by Tier-A descriptor sets (bindings 6 and 7) that are only
            // rewritten at a pass head, under conditions, for the current frame slot - so a set can keep
            // naming a grown-away handle long after the growth. Retiring the old handle at growth let the
            // context's retired-buffer drain destroy a buffer a live set still named (VUID-08114,
            // diagnosed 2026-11-26). Both buffers are small (sections x 48B / x 4B) and growth is
            // geometric, so keeping every superseded buffer until delete() costs a bounded, negligible
            // amount of slab space; delete() below destroys them all at host teardown.
            this.descBuffer.retainSupersededBuffers();
            this.factorBaseBuffer.retainSupersededBuffers();
        } else {
            this.descBuffer.ensureCapacity(descBytes);
            this.factorBaseBuffer.ensureCapacity(Math.max(4L, (long) factorBaseImage.length * 4L));
        }
        long descAddress = this.descBuffer.mappedAddress();
        for (int i = 0; i < descImage.length; i++) {
            MemoryUtil.memPutInt(descAddress + (long) i * 4L, descImage[i]);
        }
        long factorAddress = this.factorBaseBuffer.mappedAddress();
        for (int i = 0; i < factorBaseImage.length; i++) {
            MemoryUtil.memPutInt(factorAddress + (long) i * 4L, factorBaseImage[i]);
        }
        this.uploadedDescGeneration = generation;
    }

    /**
     * The factor arena shared by every pass (graphics binding 8), created lazily
     * at its first touch, as the per-engine copies were. The store's one factor
     * allocator manages the whole arena offset space across passes and writes
     * never collide, so the shared buffer holds every pass' bytes at the same
     * offsets the private images held them.
     */
    VulkanHostBuffer factorBuffer(VulkanContext context) {
        if (this.factorBuffer == null) {
            this.factorBuffer = new VulkanHostBuffer(context, 32L * 1024 * 1024,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        return this.factorBuffer;
    }

    /** The sorted-index arena shared by every pass, the factor arena's twin. */
    VulkanHostBuffer sortedBuffer(VulkanContext context) {
        if (this.sortedBuffer == null) {
            this.sortedBuffer = new VulkanHostBuffer(context,
                    this.store.sortedAllocator().slabBytes(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        }
        return this.sortedBuffer;
    }

    /**
     * The value deferred store releases are stamped with: the context's slab
     * release value once an engine has been created against one. Before that
     * no section can be registered, so the generation counter the interim
     * rule used is a safe stand-in.
     */
    private long releaseStamp() {
        return this.context != null ? this.context.celtium$slabReleaseValue() : this.generation;
    }

    /**
     * The value store collection may proceed up to: the context's slab
     * completed value, or — before any engine exists, when nothing is
     * registered — the interim rule's two-generation lag.
     */
    private long completedStamp() {
        return this.context != null ? this.context.celtium$slabCompletedValue()
                : Math.max(0, this.generation - 2);
    }

    /** Re-uploads one pass' sorted indices for a section after a translucent re-sort. */
    void updateSortedSection(int sectionX, int sectionY, int sectionZ, TerrainRenderPass pass,
                             org.embeddedt.embeddium.impl.common.util.NativeBuffer indices) {
        Integer ordinal = this.ordinals.get(pass);
        if (ordinal == null) {
            return;
        }
        TerrainEngineA engine = this.engines.get(pass);
        if (engine != null) {
            engine.updateSorted(new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinal),
                    indices, releaseStamp());
        }
    }

    /** Removes every pass' record of one section, deferred against the completion currency. */
    void unregisterSection(int sectionX, int sectionY, int sectionZ) {
        for (int ordinal : this.ordinals.values()) {
            TerrainSectionStore.Key key = new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinal);
            this.store.unregister(key, releaseStamp());
            this.residencyGate.forget(key);
        }
        this.store.collectReleased(completedStamp());
    }

    void delete(VulkanContext context) {
        for (TerrainEngineA engine : this.engines.values()) {
            engine.delete();
        }
        this.engines.clear();
        // The shared resident buffers (R-15) are read by every pass' draws and
        // cull dispatches until the engines are gone, so they go here too, with
        // the other shared resources, after every engine.
        if (this.descBuffer != null) {
            this.descBuffer.delete();
            this.descBuffer = null;
        }
        if (this.factorBaseBuffer != null) {
            this.factorBaseBuffer.delete();
            this.factorBaseBuffer = null;
        }
        if (this.factorBuffer != null) {
            this.factorBuffer.delete();
            this.factorBuffer = null;
        }
        if (this.sortedBuffer != null) {
            this.sortedBuffer.delete();
            this.sortedBuffer = null;
        }
        // After every engine: the shared allocator's slabs are the engines' slab
        // buffers' memory, so they are freed last (its delete is teardown,
        // freeing live slabs outright), and the shared index buffer is read by
        // every pass' draws until the engines are gone.
        if (this.sharedIndexBuffer != null) {
            this.sharedIndexBuffer.delete();
            this.sharedIndexBuffer = null;
        }
        if (this.geometrySlabAllocator != null) {
            this.geometrySlabAllocator.delete();
            this.geometrySlabAllocator = null;
        }
    }
}
