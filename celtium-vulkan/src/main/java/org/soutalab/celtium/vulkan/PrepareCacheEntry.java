package org.soutalab.celtium.vulkan;

/**
 * One prepared-material cache slot: the six inputs the answer depends on, plus the answer.
 * <p>
 * A plain mutable holder rather than a record: the table is direct-mapped and a slot is overwritten in place, so
 * the point is that the six comparisons and the value share one object instead of seven arrays. The fields are
 * package-private and the class is final so the JIT sees plain field loads with no accessor calls.
 * <p>
 * Promoted out of {@code VulkanTerrainGpuBackend} as its own top-level type: the slot is a plain value with no
 * backend state of its own, so the table's owner keeps the array and the hit predicate while the entry stands
 * alone.
 */
final class PrepareCacheEntry {
    Object pass;
    int format;
    int textureId;
    long key;
    long textureKey;
    long lightKey;
    PreparedImmediate value;
}
