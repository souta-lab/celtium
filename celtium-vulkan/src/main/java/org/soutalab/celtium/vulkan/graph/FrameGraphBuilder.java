package org.soutalab.celtium.vulkan.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link CompiledGraph} from declared passes (design section 2.1: the declarative
 * render graph with automatic barrier synthesis and transient aliasing).
 *
 * <p>Compilation is three passes over the declarations:
 * <ol>
 *   <li><b>Dependencies.</b> Per resource, a write depends on the previous writer and on every
 *   reader since it (WAW/WAR); a read depends on the previous writer (RAW). The execution order
 *   is the stable topological order of those edges - declaration order wherever dependencies
 *   allow it, so a graph declared in frame order compiles to itself.</li>
 *   <li><b>Barriers.</b> Walking the execution order, consecutive uses of one resource produce a
 *   barrier when the layout changes or a read/write hazard crosses a pass boundary. First use of
 *   an image produces only its layout transition out of the initial layout.</li>
 *   <li><b>Lifetimes and aliasing.</b> Each transient gets its first/last use; transients with
 *   disjoint lifetimes and identical shape are grouped onto one alias slot.</li>
 * </ol>
 */
public final class FrameGraphBuilder {

    private final List<PassNode> passes = new ArrayList<PassNode>();
    private final Set<GraphResource> resources = new LinkedHashSet<GraphResource>();

    public FrameGraphBuilder addResource(GraphResource resource) {
        this.resources.add(resource);
        return this;
    }

    public FrameGraphBuilder addPass(PassNode pass) {
        this.passes.add(pass);
        for (ResourceAccess access : pass.accesses()) {
            this.resources.add(access.resource());
        }
        return this;
    }

    public CompiledGraph build() {
        List<PassNode> order = topologicalOrder();
        List<List<GraphBarrier>> barriers = synthesizeBarriers(order);
        Map<GraphResource, CompiledGraph.Lifetime> lifetimes = computeLifetimes(order);
        List<List<GraphResource>> aliasGroups = computeAliasGroups(lifetimes);
        return new CompiledGraph(order, barriers, lifetimes, aliasGroups);
    }

    // ---------------------------------------------------------------- dependencies

    private List<PassNode> topologicalOrder() {
        int count = this.passes.size();
        List<Set<Integer>> edges = new ArrayList<Set<Integer>>(count);
        for (int i = 0; i < count; i++) {
            edges.add(new HashSet<Integer>());
        }
        Map<GraphResource, Integer> lastWriter = new IdentityHashMap<GraphResource, Integer>();
        Map<GraphResource, List<Integer>> readersSinceWrite =
                new IdentityHashMap<GraphResource, List<Integer>>();
        for (int i = 0; i < count; i++) {
            // Combine this pass's accesses per resource: any write makes the pass a writer.
            Map<GraphResource, Boolean> writes = new IdentityHashMap<GraphResource, Boolean>();
            for (ResourceAccess access : this.passes.get(i).accesses()) {
                Boolean previous = writes.get(access.resource());
                boolean write = access.type().isWrite() || (previous != null && previous);
                writes.put(access.resource(), write);
            }
            for (Map.Entry<GraphResource, Boolean> entry : writes.entrySet()) {
                GraphResource resource = entry.getKey();
                Integer writer = lastWriter.get(resource);
                if (entry.getValue()) {
                    if (writer != null) {
                        edges.get(writer).add(i);
                    }
                    List<Integer> readers = readersSinceWrite.get(resource);
                    if (readers != null) {
                        for (Integer reader : readers) {
                            if (reader.intValue() != i) {
                                edges.get(reader).add(i);
                            }
                        }
                    }
                    lastWriter.put(resource, i);
                    readersSinceWrite.put(resource, new ArrayList<Integer>());
                } else {
                    if (writer != null) {
                        edges.get(writer).add(i);
                    }
                    List<Integer> readers = readersSinceWrite.get(resource);
                    if (readers == null) {
                        readers = new ArrayList<Integer>();
                        readersSinceWrite.put(resource, readers);
                    }
                    readers.add(i);
                }
            }
        }
        // Kahn's algorithm, always taking the lowest declaration index among ready passes so
        // the result is declaration order wherever the edges permit.
        int[] indegree = new int[count];
        for (int from = 0; from < count; from++) {
            for (Integer to : edges.get(from)) {
                indegree[to]++;
            }
        }
        List<PassNode> order = new ArrayList<PassNode>(count);
        boolean[] emitted = new boolean[count];
        for (int step = 0; step < count; step++) {
            int pick = -1;
            for (int i = 0; i < count; i++) {
                if (!emitted[i] && indegree[i] == 0) {
                    pick = i;
                    break;
                }
            }
            if (pick < 0) {
                throw new IllegalStateException("frame graph has a dependency cycle involving "
                        + remainingNames(emitted));
            }
            emitted[pick] = true;
            order.add(this.passes.get(pick));
            for (Integer to : edges.get(pick)) {
                indegree[to]--;
            }
        }
        return order;
    }

    private String remainingNames(boolean[] emitted) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < this.passes.size(); i++) {
            if (!emitted[i]) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(this.passes.get(i).name());
            }
        }
        return names.toString();
    }

    // ---------------------------------------------------------------- barriers

    private List<List<GraphBarrier>> synthesizeBarriers(List<PassNode> order) {
        Map<GraphResource, AccessType> lastAccess = new IdentityHashMap<GraphResource, AccessType>();
        Map<GraphResource, ImageLayout> currentLayout = new IdentityHashMap<GraphResource, ImageLayout>();
        for (GraphResource resource : this.resources) {
            currentLayout.put(resource, resource.isTransient()
                    ? ImageLayout.UNDEFINED : resource.initialLayout());
        }
        List<List<GraphBarrier>> result = new ArrayList<List<GraphBarrier>>(order.size());
        for (PassNode pass : order) {
            List<GraphBarrier> before = new ArrayList<GraphBarrier>();
            // First and last access per resource within this pass: the barrier (if any) must
            // cover the first use; the state carried forward is the last use.
            Map<GraphResource, AccessType> firstInPass = new IdentityHashMap<GraphResource, AccessType>();
            Map<GraphResource, AccessType> lastInPass = new IdentityHashMap<GraphResource, AccessType>();
            for (ResourceAccess access : pass.accesses()) {
                if (!firstInPass.containsKey(access.resource())) {
                    firstInPass.put(access.resource(), access.type());
                }
                lastInPass.put(access.resource(), access.type());
            }
            for (Map.Entry<GraphResource, AccessType> entry : firstInPass.entrySet()) {
                GraphResource resource = entry.getKey();
                AccessType access = entry.getValue();
                AccessType previous = lastAccess.get(resource);
                ImageLayout layout = currentLayout.get(resource);
                boolean barrier;
                if (resource.kind() == GraphResource.Kind.IMAGE) {
                    boolean layoutChange = access.layout() != null && access.layout() != layout;
                    boolean hazard = previous != null
                            && (previous.isWrite() || access.isWrite());
                    barrier = layoutChange || hazard;
                    if (barrier) {
                        before.add(new GraphBarrier(resource,
                                previous == null ? SyncBits.STAGE_TOP_OF_PIPE : previous.stageMask(),
                                access.stageMask(),
                                previous == null ? 0 : previous.accessMask(),
                                access.accessMask(),
                                layout,
                                access.layout() == null ? layout : access.layout()));
                    }
                    if (access.layout() != null) {
                        currentLayout.put(resource, access.layout());
                    }
                } else {
                    barrier = previous != null && (previous.isWrite() || access.isWrite());
                    if (barrier) {
                        before.add(new GraphBarrier(resource,
                                previous.stageMask(), access.stageMask(),
                                previous.accessMask(), access.accessMask(),
                                ImageLayout.UNDEFINED, ImageLayout.UNDEFINED));
                    }
                }
                lastAccess.put(resource, lastInPass.get(resource));
            }
            result.add(Collections.unmodifiableList(before));
        }
        return result;
    }

    // ---------------------------------------------------------------- lifetimes / aliasing

    private Map<GraphResource, CompiledGraph.Lifetime> computeLifetimes(List<PassNode> order) {
        Map<GraphResource, CompiledGraph.Lifetime> lifetimes =
                new IdentityHashMap<GraphResource, CompiledGraph.Lifetime>();
        for (int index = 0; index < order.size(); index++) {
            for (ResourceAccess access : order.get(index).accesses()) {
                GraphResource resource = access.resource();
                if (!resource.isTransient()) {
                    continue;
                }
                CompiledGraph.Lifetime previous = lifetimes.get(resource);
                if (previous == null) {
                    lifetimes.put(resource, new CompiledGraph.Lifetime(index, index));
                } else {
                    lifetimes.put(resource, new CompiledGraph.Lifetime(
                            previous.firstUse(), index));
                }
            }
        }
        return lifetimes;
    }

    private List<List<GraphResource>> computeAliasGroups(
            Map<GraphResource, CompiledGraph.Lifetime> lifetimes) {
        List<GraphResource> transients = new ArrayList<GraphResource>(lifetimes.keySet());
        // Deterministic order: by first use, then name.
        transients.sort((a, b) -> {
            int byFirst = Integer.compare(lifetimes.get(a).firstUse(), lifetimes.get(b).firstUse());
            return byFirst != 0 ? byFirst : a.name().compareTo(b.name());
        });
        List<List<GraphResource>> groups = new ArrayList<List<GraphResource>>();
        for (GraphResource resource : transients) {
            CompiledGraph.Lifetime lifetime = lifetimes.get(resource);
            List<GraphResource> home = null;
            for (List<GraphResource> group : groups) {
                boolean fits = true;
                for (GraphResource member : group) {
                    if (!member.aliasCompatible(resource)
                            || lifetimes.get(member).overlaps(lifetime)) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    home = group;
                    break;
                }
            }
            if (home == null) {
                home = new ArrayList<GraphResource>();
                groups.add(home);
            }
            home.add(resource);
        }
        List<List<GraphResource>> sealed = new ArrayList<List<GraphResource>>(groups.size());
        for (List<GraphResource> group : groups) {
            sealed.add(Collections.unmodifiableList(group));
        }
        return sealed;
    }
}
