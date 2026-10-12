package org.soutalab.celtium.vulkan;

/**
 * The model domain's fragments of the verification line (refactoring plan R2, stage 2).
 *
 * <p>Same decision as stage 1: counters whose owner exists are read from that owner. The model mesh table and its
 * quads/failure reason belong to {@link ModelGeometryCache} since R3a; the draw census is still the backend's because
 * it is static on purpose - it must survive a resource reload and a world switch while a backend instance does not.
 * The display-list census is {@link VulkanModelQuadCapture}'s, read through as it always was.
 *
 * <p>The fragments reproduce the field names and order exactly; the line calls them where those fields used to be
 * concatenated.
 */
public final class ModelStatistics {

    private final ModelGeometryCache modelGeometry;

    ModelStatistics(ModelGeometryCache modelGeometry) {
        this.modelGeometry = modelGeometry;
    }

    /** {@return run 0 of the line's model fields: modelDraws}. */
    public String run0(long modelDraws) {
        return " modelDraws=" + modelDraws;
    }

    /** {@return run 1: the draw census, the mesh table and the display-list census, in the line's order}. */
    public String run1(long modelDrawRefusals, String modelLastRefusal) {
        StringBuilder out = new StringBuilder();
        out.append(" modelDrawRefusals=").append(modelDrawRefusals);
        out.append(" modelLastRefusal=").append(modelLastRefusal);
        out.append(" modelMeshLists=").append(this.modelGeometry.modelMeshes() == null ? -1
                : this.modelGeometry.modelMeshes().size());
        return out.toString();
    }

    /** {@return run 2: the mesh quads and the first failure reason, which the line prints after two interleaved
     * expressions from other owners}. */
    public String run2() {
        StringBuilder out = new StringBuilder();
        out.append(" modelMeshQuads=").append(this.modelGeometry.modelMeshQuads());
        out.append(" modelMeshFailure=").append(this.modelGeometry.modelMeshFailure());
        return out.toString();
    }
}
