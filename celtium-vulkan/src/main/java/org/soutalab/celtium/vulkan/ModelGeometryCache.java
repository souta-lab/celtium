package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R3a: one owner for the compiled model geometry - the display-list meshes, their compile generation, the lending rule
 * and the two teardown paths.
 * <p>
 * What moved here from {@code VulkanTerrainGpuBackend}: the {@code modelMeshes} table and the builder that fills it
 * ({@code celtium$buildModelMeshes}, around line 10630), the missing-mesh counter and its bounded log, the contract
 * failures and the build summary, the world-teardown and full-teardown release loops (around lines 9091 and 9169), and
 * the borrow the draw path performs when it looks a list's mesh up (around line 10976).
 * <p>
 * The lending rule, which is the part worth stating: a mesh in this table is <b>lent</b>, not given. A group that draws
 * it records {@code GroupStamp.meshBorrowed = true} (the backend's {@code celtium$openImmediateGroup} sets it at the
 * comment "Borrowed: the mesh belongs to the capture that lent it and is drawn every frame"), and the immediate
 * queue's retire path must not free a borrowed mesh - it only removes the group (see
 * {@code releaseRetiredImmediateGroups}, which checks that flag before {@code group.mesh.delete()}). So this class never
 * frees a mesh that a live or in-flight group may still draw: it retires superseded meshes through
 * {@link VulkanDynamicMesh#retire()} (which waits for the frames that used them) and only the two teardown paths
 * release the table itself, after the device has been settled or the world epoch has advanced.
 * <p>
 * There is deliberately no second mesh builder here: {@link #build} is the existing one, moved, and it stays the only
 * place a {@link VulkanDynamicMesh} for a display list is created.
 */
public final class ModelGeometryCache {

    /** Whether the meshes are built from the captured display lists (lab switch: off leaves GL drawing them). */
    private static final boolean MODEL_MESH_BUILD = Boolean.parseBoolean(
            System.getProperty("celtium.vulkan.modelMeshBuild", "true"));
    /** A limit on how many quads go into one mesh, so a single quad can be drawn on its own (0 = no limit). */
    private static final int MODEL_QUAD_LIMIT = Integer.getInteger("celtium.vulkan.modelQuadLimit", 0).intValue();

    /** The built meshes, keyed by display-list id, or null when they have not been built. */
    /** Reusable destination for the model-geometry read: the rebuild loop allocates nothing per list with it. */
    private final VulkanModelQuadCapture.Scratch snapshotScratch = new VulkanModelQuadCapture.Scratch();

    private LinkedHashMap<Integer, VulkanDynamicMesh> modelMeshes;
    /** The capture stamp the table was built from; {@link Long#MIN_VALUE} forces a build. */
    private long builtStamp = Long.MIN_VALUE;

    /** Display lists a replay asked for that this cache has no mesh for, and the bounded log of them. */
    private int modelMissingMeshes;
    private int modelMissingMeshLog;
    private int modelLastMissingList = Integer.MIN_VALUE;
    /** Meshes refused by the vertex/index contract, and the first reason, so a bad snapshot is named not hidden. */
    private int modelMeshContractFailures;
    /** How many build-summary lines have been printed; bounded, because the build repeats as lists appear. */
    private int modelBuildLogs;
    private int modelMeshQuads;
    private String modelMeshFailure = "none";

    /** {@return the built meshes, keyed by display-list id, or null when nothing has been built}. */
    public Map<Integer, VulkanDynamicMesh> modelMeshes() {
        return this.modelMeshes;
    }

    /** {@return how many replays asked for a list this cache has no mesh for}. */
    /**
     * Whether a missing-mesh line should be printed for this list: bounded, and only when the list differs from the
     * last one logged, so a scene with many draws of one list does not flood the log.
     */
    public boolean shouldLogMissingMesh(int listId) {
        if (this.modelMissingMeshLog >= 8 || listId == this.modelLastMissingList) {
            return false;
        }
        this.modelLastMissingList = listId;
        this.modelMissingMeshLog++;
        return true;
    }

    /** Records a model list that had no mesh to draw, which the borrow path names one by one. */
    public void noteMissingMesh() {
        this.modelMissingMeshes++;
    }

    public int modelMissingMeshes() {
        return this.modelMissingMeshes;
    }

    public int modelMeshContractFailures() {
        return this.modelMeshContractFailures;
    }

    public int modelMeshQuads() {
        return this.modelMeshQuads;
    }

    public String modelMeshFailure() {
        return this.modelMeshFailure;
    }

    /** {@return whether the build is enabled at all} - the draw path and the report both read this. */
    public static boolean buildEnabled() {
        return MODEL_MESH_BUILD;
    }

    /**
     * Builds or refreshes the table from the captured display lists, reusing every mesh whose list has not moved
     * generation and retiring the ones that have.
     * <p>
     * Moved verbatim from {@code VulkanTerrainGpuBackend.celtium$buildModelMeshes}; the only change is that the
     * context arrives as an argument instead of a field, and the summary line goes through {@link #reportBuild}.
     *
     * @return whether the table was replaced (a failed build leaves the previous table in place)
     */
    public boolean build(VulkanContext context) {
        if (!MODEL_MESH_BUILD) {
            return false;
        }
        // Nothing captured has moved since the last successful build: the table cannot have changed, and skipping here
        // also spares the two maps this method allocates per call. JFR of the shipped configuration put this method and
        // its caller at 2.1% inclusive, all of it spent re-walking lists whose generations had not moved.
        long stamp = VulkanModelQuadCapture.stamp();
        if (this.builtStamp == stamp && this.modelMeshes != null) {
            return false;
        }
        int[] ids = VulkanModelQuadCapture.listIds();
        if (ids.length == 0 && this.modelMeshes == null) {
            // Nothing captured yet and nothing built: the table stays null, which the report prints as -1 rather than as
            // a build that found no lists. An empty capture with meshes already built is not this case, because those
            // meshes' lists are gone and are retired below.
            return false;
        }
        LinkedHashMap<Integer, VulkanDynamicMesh> built = new LinkedHashMap<Integer, VulkanDynamicMesh>();
        LinkedHashMap<Integer, Integer> builtGenerations = new LinkedHashMap<Integer, Integer>();
        List<VulkanDynamicMesh> superseded = new ArrayList<VulkanDynamicMesh>();
        if (this.modelMeshes != null) {
            for (Map.Entry<Integer, VulkanDynamicMesh> entry : this.modelMeshes.entrySet()) {
                int listId = entry.getKey().intValue();
                VulkanDynamicMesh mesh = entry.getValue();
                if (mesh.matches(listId, VulkanModelQuadCapture.generationOf(listId))) {
                    // Unchanged since it was built: reused as it is, which is the point of the generation identity.
                    built.put(entry.getKey(), mesh);
                } else {
                    // Recompiled, deleted or emptied: the mesh is stale from the moment its list's generation moved and
                    // must not be drawn again. The retire is queued rather than freed here, so the frames that already
                    // used these buffers have completed before they are destroyed.
                    superseded.add(mesh);
                }
            }
        }
        VulkanDynamicMesh pending = null;
        try {
            // The mesh being built, held so a failure before it reaches the table can still release it.
            for (int id : ids) {
                // Filled in place: the arrays are read until the next list and never kept, and this loop rebuilds every list whose
                // generation moved on - which allocated 1.3 GB of float[] per run when each snapshot was a fresh copy.
                if (!VulkanModelQuadCapture.snapshotInto(id, this.snapshotScratch)) {
                    continue;
                }
                VulkanModelQuadCapture.Scratch snapshot = this.snapshotScratch;
                VulkanDynamicMesh existing = built.get(Integer.valueOf(id));
                if (existing != null && existing.matches(id, snapshot.generation)) {
                    continue;
                }
                VulkanDynamicMesh mesh = new VulkanDynamicMesh(context);
                // The mesh exists before it is in the table, so a failure while filling it must release it here: the
                // catch below can only discard what the table already holds, and this mesh is not in it yet (audit
                // ownership gap - the model builder leaked the one mesh it was in the middle of building).
                pending = mesh;
                // The ITEM layout: position, colour, texture, normal, padding - 28 bytes, the same one the dropped item
                // is drawn through. The colour is the material the fixed-function lighting evaluates, and the normal is
                // the quad's own, carried by the capture.
                mesh.begin(new VulkanDynamicMesh.Layout(28, 0, 16, 12, 24, -1));
                int quadLimit = snapshot.quads;
                if (MODEL_QUAD_LIMIT > 0 && quadLimit > MODEL_QUAD_LIMIT) {
                    quadLimit = MODEL_QUAD_LIMIT;
                }
                for (int quad = 0; quad < quadLimit; quad++) {
                    // nextVertex() belongs INSIDE the loop: it starts a new vertex and returns its index, and every
                    // put* call writes the vertex it started.
                    int base = mesh.vertexCount();
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int started = mesh.nextVertex();
                        if (vertex == 0) {
                            base = started;
                        } else if (started != base + vertex) {
                            throw new IllegalStateException("model mesh vertex contract: quad " + quad + " vertex "
                                    + vertex + " started at " + started + ", expected " + (base + vertex));
                        }
                        int source = (quad * 4 + vertex) * 3;
                        mesh.putPosition(snapshot.positions[source], snapshot.positions[source + 1],
                                snapshot.positions[source + 2]);
                        mesh.putUv(snapshot.uvs[(quad * 4 + vertex) * 2], snapshot.uvs[(quad * 4 + vertex) * 2 + 1]);
                        // White: what glColor holds when vanilla draws a model, and the material the lighting scales.
                        mesh.putColor(255, 255, 255, 255);
                        // One normal per quad, as vanilla submits it (flat shading, one normal for all four vertices).
                        mesh.putNormal(snapshot.normals[quad * 3], snapshot.normals[quad * 3 + 1],
                                snapshot.normals[quad * 3 + 2]);
                    }
                    mesh.index(base);
                    mesh.index(base + 1);
                    mesh.index(base + 2);
                    mesh.index(base);
                    mesh.index(base + 2);
                    mesh.index(base + 3);
                }
                // The contract, checked as arithmetic rather than assumed: four vertices and six indices per quad, and
                // the indices must address vertices this mesh actually holds. A mesh that fails is not drawn, and the
                // failure is scoped to THIS list rather than aborting every list.
                if (mesh.vertexCount() != quadLimit * 4 || mesh.indexCount() != quadLimit * 6) {
                    String failure = "list " + id + ": " + quadLimit + " quads produced " + mesh.vertexCount()
                            + " vertices and " + mesh.indexCount() + " indices, expected " + (quadLimit * 4)
                            + " and " + (quadLimit * 6);
                    this.modelMeshContractFailures++;
                    // The sentinel is "none", not null (audit #348): the null check never ran, so every contract
                    // failure counted but kept the report's reason at "none".
                    if ("none".equals(this.modelMeshFailure)) {
                        this.modelMeshFailure = failure;
                    }
                    // The mesh never reaches the table, so this is a partial build's leftovers: released here rather
                    // than left for the next rebuild to replace (the audit's N02 failure path).
                    mesh.discard();
                    continue;
                }
                built.put(Integer.valueOf(id), mesh);
                pending = null;
                builtGenerations.put(Integer.valueOf(id), Integer.valueOf(snapshot.generation));
            }
        } catch (RuntimeException | LinkageError error) {
            if (pending != null) {
                pending.discard();
                pending = null;
            }
            // A failure this far out is not attributable to one list, so the previous table stays in place - nothing
            // above touched it - and the meshes this attempt created are released rather than leaked.
            this.modelMeshFailure = String.valueOf(error);
            for (Map.Entry<Integer, VulkanDynamicMesh> entry : built.entrySet()) {
                VulkanDynamicMesh previous = this.modelMeshes == null ? null : this.modelMeshes.get(entry.getKey());
                if (previous != entry.getValue()) {
                    entry.getValue().discard();
                }
            }
            return false;
        }
        // Committed: the table the scene draws from is replaced in one step, every mesh in it is stamped with the
        // generation it was built from, and only then are the superseded meshes retired. Stamping after the commit is
        // what keeps a failed build from leaving a stamped mesh in a table that was never installed.
        this.modelMeshes = built;
        this.builtStamp = stamp;
        // Retired first: a list never has two meshes registered at once, so the registry's replacement counter means
        // what it says instead of counting this commit's own window.
        for (VulkanDynamicMesh mesh : superseded) {
            mesh.retire();
        }
        for (Map.Entry<Integer, Integer> entry : builtGenerations.entrySet()) {
            built.get(entry.getKey()).stamp(entry.getKey().intValue(), entry.getValue().intValue());
        }
        int quads = 0;
        for (VulkanDynamicMesh mesh : built.values()) {
            quads += mesh.indexCount() / 6;
        }
        this.modelMeshQuads = quads;
        reportBuild();
        return true;
    }

    /**
     * The borrow: the mesh for a display list, or {@code null} when this cache has none.
     * <p>
     * A non-null answer is a <b>loan</b>: the caller draws it this frame and records the loan on its group's stamp
     * ({@code meshBorrowed = true}), which is what stops the immediate queue's retire path from freeing it. A null
     * answer is counted and named once per list, because "the model is missing" and "the mesh was never captured" are
     * different findings.
     */
    public VulkanDynamicMesh borrow(int listId, int boundTextureId) {
        if (this.modelMeshes == null) {
            return null;
        }
        VulkanDynamicMesh mesh = this.modelMeshes.get(Integer.valueOf(listId));
        if (mesh == null || mesh.isEmpty()) {
            this.modelMissingMeshes++;
            if (this.modelMissingMeshLog < 8 && listId != this.modelLastMissingList) {
                this.modelLastMissingList = listId;
                this.modelMissingMeshLog++;
                System.out.println("[Celtium] CELTIUM_MODEL_MISSING list=" + listId + " texture=" + boundTextureId
                        + " (no captured mesh for this display list; the GL body runs and there is no context)");
            }
            return null;
        }
        return mesh;
    }

    /**
     * The return half of the lending rule, as a predicate the queue can ask before it frees anything: a group whose
     * stamp says the mesh was borrowed owns nothing to free, so retiring the group only removes the group.
     */
    public static boolean meshBorrowed(GroupStamp stamp) {
        return stamp != null && stamp.meshBorrowed;
    }

    /**
     * World teardown: the world's display lists are gone with it, so every mesh is released and the table is cleared.
     * Called after the world epoch advanced and after the queued groups were discarded, so nothing can draw them again.
     */
    public void releaseAfterWorldTeardown() {
        if (this.modelMeshes != null) {
            for (VulkanDynamicMesh mesh : this.modelMeshes.values()) {
                mesh.releaseAfterIdle();
            }
            this.modelMeshes.clear();
            this.builtStamp = Long.MIN_VALUE;
        }
    }

    /**
     * Full teardown: the device has been settled, so the release is immediate rather than idle-deferred, and the table
     * is cleared. This is the branch the backend's own {@code delete()} takes after {@code settleGpu} succeeded (audit
     * N02: no free without a completed wait).
     */
    public void releaseAll() {
        if (this.modelMeshes != null) {
            for (VulkanDynamicMesh mesh : this.modelMeshes.values()) {
                mesh.releaseAfterIdle();
            }
            this.modelMeshes.clear();
            this.builtStamp = Long.MIN_VALUE;
        }
    }

    /**
     * The explicit-drop entry point: retires every mesh and nulls the table, so the next {@link #build} starts from
     * nothing. Nothing calls it today, deliberately (the Lead's R3a decision): the resource-reload path is already
     * covered by the generation-driven rebuild in {@link #build} (the capture's generations move, every stale mesh is
     * superseded and retired), and there is no reload hook in the backend to call this from. It exists so a later unit
     * that needs a forced drop has one place to do it rather than reaching into the table.
     * <p>
     * It does <b>not</b> reset {@code modelMeshQuads} or {@code modelMeshFailure}: the world-teardown loop it mirrors
     * clears the table only, and a counter whose meaning changed inside an extraction would change what a leg reports.
     */
    public void invalidate() {
        if (this.modelMeshes == null) {
            return;
        }
        for (VulkanDynamicMesh mesh : this.modelMeshes.values()) {
            mesh.retire();
        }
        this.modelMeshes = null;
    }

    /** The bounded build summary, the same line the backend printed from the builder's tail. */
    private void reportBuild() {
        if (!VulkanDiagnostics.enabled() || this.modelBuildLogs >= 8) {
            return;
        }
        this.modelBuildLogs++;
        System.out.println("[Celtium] CELTIUM_MODEL_BUILD lists=" + (this.modelMeshes == null ? -1 : this.modelMeshes.size())
                + " quads=" + this.modelMeshQuads + " missingMeshes=" + this.modelMissingMeshes
                + " contractFailures=" + this.modelMeshContractFailures
                + " failure=[" + this.modelMeshFailure + "]");
    }
}
