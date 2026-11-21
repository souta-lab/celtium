package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Retired descriptor sets paired with the pool each was allocated from, in retirement order.
 * <p>
 * Two parallel lists rather than a map: a set handle is unique only within its pool, so the pair is the
 * identity. {@link #drainGrouped()} is the hand-off to the completion-gated free - one
 * {@code vkFreeDescriptorSets} call per pool. The block and item immediate pipelines share this store; the
 * textured pipeline carries its own copy of the same shape (audit D2 was fixed per family, and each family's
 * census counts its own retirements).
 */
final class RetiredDescriptorSets {
    private final ArrayList<Long> pools = new ArrayList<Long>();
    private final ArrayList<Long> sets = new ArrayList<Long>();

    void add(long pool, long set) {
        this.pools.add(Long.valueOf(pool));
        this.sets.add(Long.valueOf(set));
    }

    int size() {
        return this.sets.size();
    }

    void clear() {
        this.pools.clear();
        this.sets.clear();
    }

    /** Removes every pair and groups the set handles by pool, in first-seen pool order. */
    Map<Long, long[]> drainGrouped() {
        Map<Long, ArrayList<Long>> grouped = new LinkedHashMap<Long, ArrayList<Long>>();
        for (int i = 0; i < this.sets.size(); i++) {
            ArrayList<Long> bucket = grouped.get(this.pools.get(i));
            if (bucket == null) {
                bucket = new ArrayList<Long>();
                grouped.put(this.pools.get(i), bucket);
            }
            bucket.add(this.sets.get(i));
        }
        this.pools.clear();
        this.sets.clear();
        Map<Long, long[]> drained = new LinkedHashMap<Long, long[]>();
        for (Map.Entry<Long, ArrayList<Long>> entry : grouped.entrySet()) {
            long[] handles = new long[entry.getValue().size()];
            for (int i = 0; i < handles.length; i++) {
                handles[i] = entry.getValue().get(i).longValue();
            }
            drained.put(entry.getKey(), handles);
        }
        return drained;
    }
}
