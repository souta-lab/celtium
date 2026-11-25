package org.soutalab.celtium.vulkan;

import java.util.IdentityHashMap;
import java.util.Map;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.soutalab.celtium.vulkan.terrain.AsyncSectionMesher;
import org.soutalab.celtium.vulkan.terrain.TerrainResidencyGate;
import org.soutalab.celtium.vulkan.terrain.TerrainSectionStore;
import org.soutalab.celtium.vulkan.terrain.TerrainUploadBudget;

/**
 * Owns the Tier-A terrain state for one backend: the shared
 * {@link TerrainSectionStore}, the upload budget, and one
 * {@link TerrainEngineA} executor per render pass. Pass ordinals are assigned by
 * identity in first-use order and are the same ordinals the SectionDesc records
 * carry, so registration and culling always agree on them.
 * <p>
 * Deferred arena releases are collected against the host's own generation counter
 * with a two-generation lag — an interim completion rule that approximates the
 * frames-in-flight depth until the frame timeline (design section 2.3) lands and
 * drives collection with real GPU completion values.
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
        TerrainEngineA engine = this.engines.get(pass);
        if (engine == null) {
            engine = new TerrainEngineA(context, ordinalFor(pass),
                    "pass-" + ordinalFor(pass), this.store, this.budget, this.residencyGate);
            this.engines.put(pass, engine);
        }
        return engine;
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
                    indices, this.generation);
        }
    }

    /** Removes every pass' record of one section, deferred against the generation lag. */
    void unregisterSection(int sectionX, int sectionY, int sectionZ) {
        for (int ordinal : this.ordinals.values()) {
            TerrainSectionStore.Key key = new TerrainSectionStore.Key(sectionX, sectionY, sectionZ, ordinal);
            this.store.unregister(key, this.generation);
            this.residencyGate.forget(key);
        }
        this.store.collectReleased(Math.max(0, this.generation - 2));
    }

    void delete(VulkanContext context) {
        for (TerrainEngineA engine : this.engines.values()) {
            engine.delete();
        }
        this.engines.clear();
    }
}
