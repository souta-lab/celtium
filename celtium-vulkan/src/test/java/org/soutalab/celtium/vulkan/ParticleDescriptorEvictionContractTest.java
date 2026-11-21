package org.soutalab.celtium.vulkan;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The particle family's descriptor-set eviction, pinned at the pure decisions it turns on and at the wiring
 * that connects them (audit-vulkan-core D2's remainder: the block, item and textured families closed in
 * 6251010fb and 9d4df7c64, this family was missed, and the interaction audit found the miss).
 * <p>
 * The pipeline is device-bound, so the policy is pinned the way the sibling families' D2 fixes are: the
 * ring's eldest decision and the retired store's pairing are exercised directly, composed the way
 * {@code celtium$evictEldestDescriptorSets} composes them, and the pipeline's own wiring - eviction before
 * allocation, the slot drop, the generation guard on the completion-gated free - is pinned as a source
 * contract, the shape {@code LandedFixGuardContractTest} established for device-bound mechanisms. The
 * behavioural half therefore does not execute the pipeline's loop; the source half is what fails if the loop
 * itself is removed or reverted.
 */
class ParticleDescriptorEvictionContractTest {

    private static final String PIPELINE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticlePipeline.java";

    // ---- the ring's decision ---------------------------------------------------------------------------

    @Test
    @DisplayName("the eviction ring serves keys eldest first, and a full ring records nothing further")
    void ringServesEldestFirstAndDropsNotesWhenFull() {
        VulkanImmediateParticlePipeline.EvictionRing ring =
                new VulkanImmediateParticlePipeline.EvictionRing(4);
        ring.note(11L);
        ring.note(22L);
        ring.note(33L);
        assertEquals(11L, ring.eldest(), "the eldest recorded key is served first");
        assertEquals(22L, ring.eldest());
        ring.note(44L);
        ring.note(55L);
        ring.note(66L);
        assertEquals(4, ring.size(), "the ring is now full");
        ring.note(77L);
        assertEquals(4, ring.size(), "a full ring leaves the key unevictable rather than overwriting the eldest");
        assertEquals(33L, ring.eldest());
        assertEquals(44L, ring.eldest());
        assertEquals(55L, ring.eldest());
        assertEquals(66L, ring.eldest(), "77 was never recorded, so it can never be served");
        assertFalse(ring.hasEldest(), "a drained ring has no eldest");
        ring.note(88L);
        ring.clear();
        assertEquals(0, ring.size(), "clear drops every recorded key");
        assertFalse(ring.hasEldest());
    }

    // ---- the policy, composed the way the pipeline composes it ------------------------------------------

    /**
     * The pipeline's eviction loop over the components it composes, at a small limit: while the cache is at
     * its limit and the ring has an eldest, the eldest key's set leaves the cache for the retired store.
     */
    private static void evictEldest(Long2LongOpenHashMap cache,
                                    VulkanImmediateParticlePipeline.EvictionRing ring,
                                    RetiredDescriptorSets retired, long pool, int limit) {
        while (cache.size() >= limit && ring.hasEldest()) {
            long eldest = ring.eldest();
            long removed = cache.remove(eldest);
            if (removed != 0L) {
                retired.add(pool, removed);
            }
        }
    }

    @Test
    @DisplayName("at the cache limit the eldest sets retire and the live sets stay cached")
    void evictionAtTheLimitRetiresOnlyTheEldest() {
        Long2LongOpenHashMap cache = new Long2LongOpenHashMap();
        VulkanImmediateParticlePipeline.EvictionRing ring =
                new VulkanImmediateParticlePipeline.EvictionRing(8);
        RetiredDescriptorSets retired = new RetiredDescriptorSets();
        long pool = 4242L;
        int limit = 4;
        // Pipeline order on a miss: evict first, then allocate, cache and record the key.
        for (long key = 1L; key <= 6L; key++) {
            evictEldest(cache, ring, retired, pool, limit);
            cache.put(key, 100L + key);
            ring.note(key);
        }
        assertEquals(4, cache.size(), "the cache is held at its limit");
        assertEquals(103L, cache.get(3L), "a live set is still served from the cache");
        assertEquals(106L, cache.get(6L), "the newest set is still served from the cache");
        assertEquals(0L, cache.get(1L), "the eldest key is no longer cached");
        Map<Long, long[]> byPool = retired.drainGrouped();
        assertArrayEquals(new long[] {101L, 102L}, byPool.get(pool),
                "exactly the two eldest sets were retired, against their own pool, in order");
        assertEquals(1, byPool.size(), "no other pool was handed a set");
    }

    @Test
    @DisplayName("a ring entry whose set already left the cache retires nothing, and the loop moves past it")
    void aRingEntryWhoseSetIsGoneRetiresNothing() {
        Long2LongOpenHashMap cache = new Long2LongOpenHashMap();
        VulkanImmediateParticlePipeline.EvictionRing ring =
                new VulkanImmediateParticlePipeline.EvictionRing(8);
        RetiredDescriptorSets retired = new RetiredDescriptorSets();
        cache.put(1L, 101L);
        ring.note(1L);
        cache.put(2L, 102L);
        ring.note(2L);
        cache.remove(1L);
        evictEldest(cache, ring, retired, 4242L, 1);
        Map<Long, long[]> byPool = retired.drainGrouped();
        assertArrayEquals(new long[] {102L}, byPool.get(4242L),
                "the absent entry handed nothing to the free; the present eldest still retired");
    }

    // ---- the pipeline's wiring, as a source contract -----------------------------------------------------

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }

    private static String body(String source, String from, String to) {
        int start = source.indexOf(from);
        assertTrue(start >= 0, from + " is declared");
        int end = source.indexOf(to, start);
        assertTrue(end > start, to + " follows " + from);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("the pool lets a retired set return individually")
    void thePoolCarriesTheFreeFlag() throws IOException {
        assertTrue(read(PIPELINE).contains("VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT"),
                "without FREE a retired set can never be handed back and the pool only fills");
    }

    @Test
    @DisplayName("a cache miss evicts before it allocates, and a freshly cached key is recorded")
    void aMissEvictsBeforeAllocating() throws IOException {
        String lookup = body(read(PIPELINE), "public long descriptorSetFor(", "private long lastDescriptorKey");
        int evict = lookup.indexOf("celtium$evictEldestDescriptorSets()");
        int allocate = lookup.indexOf("vkAllocateDescriptorSets");
        assertTrue(evict >= 0, "the miss path evicts");
        assertTrue(allocate > evict, "eviction runs before the allocation it makes room for");
        int put = lookup.indexOf("this.setsByTextureView.put(descriptorKey, set)");
        int note = lookup.indexOf("celtium$noteCachedKey(descriptorKey)");
        assertTrue(put >= 0 && note > put, "a freshly cached key joins the eviction order");
    }

    @Test
    @DisplayName("eviction retires against the pool and drops the slot that names the evicted set")
    void evictionRetiresAgainstThePoolAndDropsTheSlot() throws IOException {
        String evict = body(read(PIPELINE), "private void celtium$evictEldestDescriptorSets()",
                "private void celtium$handOffRetiredSets()");
        assertTrue(evict.contains("DESCRIPTOR_CACHE_LIMIT"), "eviction is bounded by the cache limit");
        assertTrue(evict.contains("this.retiredSets.add(this.descriptorPool, removed)"),
                "an evicted set is retired against the pool it was allocated from");
        assertTrue(evict.contains("removed == this.lastDescriptorSet"),
                "the slot cache is checked against the evicted set");
        assertTrue(evict.contains("this.lastDescriptorKey = -1L"),
                "a slot naming an evicted set is dropped with it, not served on its way back to the pool");
    }

    @Test
    @DisplayName("the retired free is completion-gated and drops itself after delete")
    void theFreeIsCompletionGatedAndGenerationGuarded() throws IOException {
        String handOff = body(read(PIPELINE), "private void celtium$handOffRetiredSets()",
                "public String descriptorSetCensus()");
        assertTrue(handOff.contains("celtium$retireCleanup(\"particle.descriptorFree\""),
                "the free waits for GPU completion (OPT-04), it does not run at eviction time");
        int guard = handOff.indexOf("generation != this.descriptorFreeGeneration");
        int free = handOff.indexOf("vkFreeDescriptorSets");
        assertTrue(guard >= 0 && free > guard,
                "a batch queued before delete() drops itself instead of freeing into a destroyed pool");
        String delete = body(read(PIPELINE), "public void delete()", "private void createPipelineLayout()");
        assertTrue(delete.contains("this.descriptorFreeGeneration++"),
                "delete() is what advances the generation the guard reads");
        assertTrue(delete.contains("this.retiredSets.clear()"),
                "sets still awaiting their free die with the pool, they are not handed off twice");
        assertTrue(delete.contains("this.insertionOrder.clear()"), "the eviction order dies with the cache");
    }

    @Test
    @DisplayName("the census reports live, retired, awaiting and freed in the sibling shape")
    void theCensusReportsTheFourCounts() throws IOException {
        String census = body(read(PIPELINE), "public String descriptorSetCensus()",
                "static final class EvictionRing");
        assertTrue(census.contains("descriptorSetsLive="), "live sets are counted");
        assertTrue(census.contains("descriptorSetsRetired="), "retirements are counted");
        assertTrue(census.contains("descriptorSetsAwaitingCompletion="), "the retired backlog is counted");
        assertTrue(census.contains("descriptorSetsFreed="), "completed frees are counted");
    }
}
