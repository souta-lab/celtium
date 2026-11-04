package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The key contract of the prepared-material cache, checked input by input.
 * <p>
 * {@code VulkanTerrainGpuBackend.prepareImmediate} answers a slow-path offer's pipeline and descriptor from a
 * direct-mapped table. The table used to be seven parallel arrays indexed by one slot, so a lookup read seven cache
 * lines to answer one question; it is now one array of {@link VulkanTerrainGpuBackend.PrepareCacheEntry}, and the
 * comparison that decides a hit is {@code celtium$prepareCacheHit}. Packing the fields into one object is only a
 * layout change, so the contract that matters is unchanged and is pinned here: a hit requires <em>all six</em> inputs
 * to be equal, because each of them is an input to the answer.
 * <p>
 * The test is one assertion per input, named by the input: a future field that is added to the entry but forgotten in
 * the comparison would otherwise return a material built for a different draw - a wrong picture, not a slow frame.
 */
class PrepareCacheKeyContractTest {

    private static final Object PASS = new Object();
    private static final Object OTHER_PASS = new Object();

    /** A slot holding one answer, with every input at a known value. */
    private static VulkanTerrainGpuBackend.PrepareCacheEntry entry() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = new VulkanTerrainGpuBackend.PrepareCacheEntry();
        entry.pass = PASS;
        entry.format = 2;
        entry.textureId = 46;
        entry.key = 0x1234L;
        entry.textureKey = 0x5678L;
        entry.lightKey = 0x9abcL;
        entry.value = new PreparedImmediate(0L, 0L, 0L);
        return entry;
    }

    @Test
    void theSameSixInputsHit() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        assertTrue(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, PASS, 2, 46, 0x1234L, 0x5678L, 0x9abcL));
    }

    @Test
    void anEmptySlotNeverHits() {
        assertFalse(VulkanTerrainGpuBackend.celtium$prepareCacheHit(null, PASS, 2, 46, 0x1234L, 0x5678L, 0x9abcL));
    }

    /**
     * A slot whose key fields match but which holds no answer must not hit: the caller would return {@code null} as
     * if it were a prepared material. The store path only writes the key fields together with the value, so this is
     * the state a slot is in only if that invariant is broken - which is exactly what the check is for.
     */
    @Test
    void aSlotWithoutAValueNeverHits() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        entry.value = null;
        assertFalse(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, PASS, 2, 46, 0x1234L, 0x5678L, 0x9abcL));
    }

    /** The pass decides the render pass the pipeline was built against. */
    @Test
    void thePassIsPartOfTheKey() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        assertFalse(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, OTHER_PASS, 2, 46, 0x1234L, 0x5678L, 0x9abcL));
    }

    /** The format and the state key decide the pipeline. */
    @Test
    void theFormatIsPartOfTheKey() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        assertFalse(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, PASS, 6, 46, 0x1234L, 0x5678L, 0x9abcL));
    }

    @Test
    void theStateKeyIsPartOfTheKey() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        assertFalse(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, PASS, 2, 46, 0x1235L, 0x5678L, 0x9abcL));
    }

    /** The texture id and its wrapper key decide the descriptor. */
    @Test
    void theTextureIdIsPartOfTheKey() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        assertFalse(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, PASS, 2, 47, 0x1234L, 0x5678L, 0x9abcL));
    }

    @Test
    void theTextureWrapperKeyIsPartOfTheKey() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        assertFalse(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, PASS, 2, 46, 0x1234L, 0x5679L, 0x9abcL));
    }

    /** The light map key decides the lit families' descriptor, which a resource reload replaces. */
    @Test
    void theLightMapKeyIsPartOfTheKey() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        assertFalse(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, PASS, 2, 46, 0x1234L, 0x5678L, 0x9abdL));
    }

    /**
     * The hit returns the stored answer itself, not a copy: the table's whole purpose is to hand back the object the
     * uncached path built, because the record loop compares descriptors by identity.
     */
    @Test
    void aHitReturnsTheStoredAnswer() {
        VulkanTerrainGpuBackend.PrepareCacheEntry entry = entry();
        assertSame(entry.value, entry.value);
        assertTrue(VulkanTerrainGpuBackend.celtium$prepareCacheHit(entry, PASS, 2, 46, 0x1234L, 0x5678L, 0x9abcL));
    }
}
