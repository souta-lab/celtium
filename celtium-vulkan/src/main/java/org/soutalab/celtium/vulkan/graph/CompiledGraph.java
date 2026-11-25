package org.soutalab.celtium.vulkan.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The compiled frame graph: passes in execution order, the barriers to emit before each pass,
 * and the transient lifetimes the memory side aliases by. Immutable; a graph is compiled once
 * per frame shape (the vanilla graph once at startup) and executed every frame.
 */
public final class CompiledGraph {

    /** A transient resource's first and last use, as execution-order pass indices. */
    public static final class Lifetime {
        private final int firstUse;
        private final int lastUse;

        Lifetime(int firstUse, int lastUse) {
            this.firstUse = firstUse;
            this.lastUse = lastUse;
        }

        public int firstUse() {
            return this.firstUse;
        }

        public int lastUse() {
            return this.lastUse;
        }

        boolean overlaps(Lifetime other) {
            return this.firstUse <= other.lastUse && other.firstUse <= this.lastUse;
        }
    }

    private final List<PassNode> executionOrder;
    private final List<List<GraphBarrier>> barriersBefore;
    private final Map<GraphResource, Lifetime> lifetimes;
    private final List<List<GraphResource>> aliasGroups;
    private final Map<GraphResource, Integer> aliasSlot;

    CompiledGraph(List<PassNode> executionOrder, List<List<GraphBarrier>> barriersBefore,
                  Map<GraphResource, Lifetime> lifetimes,
                  List<List<GraphResource>> aliasGroups) {
        this.executionOrder = Collections.unmodifiableList(executionOrder);
        this.barriersBefore = Collections.unmodifiableList(barriersBefore);
        this.lifetimes = Collections.unmodifiableMap(new HashMap<GraphResource, Lifetime>(lifetimes));
        this.aliasGroups = Collections.unmodifiableList(aliasGroups);
        this.aliasSlot = new HashMap<GraphResource, Integer>();
        for (int group = 0; group < aliasGroups.size(); group++) {
            for (GraphResource resource : aliasGroups.get(group)) {
                this.aliasSlot.put(resource, group);
            }
        }
    }

    public List<PassNode> executionOrder() {
        return this.executionOrder;
    }

    /** Barriers to emit immediately before the pass at {@code executionIndex} records. */
    public List<GraphBarrier> barriersBefore(int executionIndex) {
        return this.barriersBefore.get(executionIndex);
    }

    public int totalBarrierCount() {
        int total = 0;
        for (List<GraphBarrier> barriers : this.barriersBefore) {
            total += barriers.size();
        }
        return total;
    }

    /** Lifetime of a transient resource, or null for imported resources. */
    public Lifetime lifetimeOf(GraphResource resource) {
        return this.lifetimes.get(resource);
    }

    /**
     * Groups of transient resources that may share one allocation: members of a group have
     * pairwise-disjoint lifetimes and identical shape (see
     * {@link GraphResource#aliasCompatible}). Imported resources never appear.
     */
    public List<List<GraphResource>> aliasGroups() {
        return this.aliasGroups;
    }

    /** The alias group index a transient belongs to, or -1 when it is in no group. */
    public int aliasSlotOf(GraphResource resource) {
        Integer slot = this.aliasSlot.get(resource);
        return slot == null ? -1 : slot.intValue();
    }
}
