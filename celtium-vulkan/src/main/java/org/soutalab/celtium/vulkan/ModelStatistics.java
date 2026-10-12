package org.soutalab.celtium.vulkan;

/**
 * ModelStatistics - the model domain's counters and its fragments of the verification line.
 *
 * <p>R2 (docs/refactoring-plan.md 6.1). The line's field names and order are a contract with the fixture and
 * every leg record (scripts/cpu-contract/verify_line_baseline.txt), so this class does not choose an order: it
 * exposes one appendRunN per contiguous run of its fields and the backend calls them in the existing order. The
 * domains are interleaved in the line, so a single fragment() per class would reorder it.
 *
 * <p>Fields are package-private only for the wiring pass; the writers move in with the domain (R3a/R3b/R4/R5/R6)
 * and the visibility narrows then. Expressions that read another class or a helper (VulkanContext, CeltiumGlStub,
 * TextureCopyService, VulkanModelQuadCapture, the *Summary helpers) are left as calls and are listed in the
 * class javadoc of the run that uses them - they are the wiring's decisions, not this class's fields.
 *
 * <p>Runs, in the line's order:
 * <li>run 0 (backend L8554-L8554): modelDraws</li>
 * <li>run 1 (backend L8556-L8580): modelDrawRefusals modelLastRefusal modelMeshLists modelMeshQuads modelMeshFailure modelStoredFloats modelFirstVertex modelMatrixPair modelLists modelListQuads modelQuads modelQuadVertices modelQuadExtent displayListOrigins displayListReplaysPassOpen displayListReplaysPassClosed displayListOriginCancels displayListCancelled displayListReplaysOpen displayListReplaysClosed displayListReplays displayListDistinct displayListTop</li>
 */
public final class ModelStatistics {

    /** backend field modelDraws (declared L?). */
    long modelDraws;
    /** backend field modelDrawRefusals (declared L?). */
    long modelDrawRefusals;
    /** backend field modelLastRefusal (declared L?). */
    long modelLastRefusal;
    /** backend field modelMeshQuads (declared L?). */
    long modelMeshQuads;
    /** backend field modelMeshFailure (declared L?). */
    long modelMeshFailure;

    /** Run 0, reproducing backend L8554-L8554: modelDraws. */
    public void appendRun0(StringBuilder out) {
        out.append(" modelDraws=");
        out.append(this.modelDraws);
    }

    /** Run 1, reproducing backend L8556-L8580: modelDrawRefusals modelLastRefusal modelMeshLists modelMeshQuads modelMeshFailure modelStoredFloats modelFirstVertex modelMatrixPair modelLists modelListQuads modelQuads modelQuadVertices modelQuadExtent displayListOrigins displayListReplaysPassOpen displayListReplaysPassClosed displayListOriginCancels displayListCancelled displayListReplaysOpen displayListReplaysClosed displayListReplays displayListDistinct displayListTop. */
    public void appendRun1(StringBuilder out) {
        out.append(" modelDrawRefusals=");
        out.append(this.modelDrawRefusals);
        out.append(" modelLastRefusal=");
        out.append(this.modelLastRefusal);
        out.append(" modelMeshLists=");
        out.append((this.modelMeshes == null ? -1 : this.modelMeshes.size()));
        out.append(" modelMeshQuads=");
        out.append(this.modelMeshQuads);
        out.append(" modelMeshFailure=");
        out.append(this.modelMeshFailure);
        out.append(" modelStoredFloats=");
        out.append(org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedFloats());
        out.append(" modelFirstVertex=");
        out.append(org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredVertex());
        out.append(" modelMatrixPair=");
        out.append(org.soutalab.celtium.vulkan.VulkanModelQuadCapture.matrixComparison());
        out.append(" modelLists=");
        out.append(org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lists());
        out.append(" modelListQuads=");
        out.append(org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listSummary());
        out.append(" modelQuads=");
        out.append(org.soutalab.celtium.vulkan.VulkanModelQuadCapture.quads());
        out.append(" modelQuadVertices=");
        out.append(org.soutalab.celtium.vulkan.VulkanModelQuadCapture.vertices());
        out.append(" modelQuadExtent=");
        out.append(org.soutalab.celtium.vulkan.VulkanModelQuadCapture.summary());
        out.append(" displayListOrigins=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.origins());
        out.append(" displayListReplaysPassOpen=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassOpen());
        out.append(" displayListReplaysPassClosed=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassClosed());
        out.append(" displayListOriginCancels=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originCancels());
        out.append(" displayListCancelled=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.cancelled());
        out.append(" displayListReplaysOpen=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameOpen());
        out.append(" displayListReplaysClosed=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameClosed());
        out.append(" displayListReplays=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.total());
        out.append(" displayListDistinct=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.distinctLists());
        out.append(" displayListTop=");
        out.append(org.soutalab.celtium.vulkan.VulkanDisplayListCensus.summary());
    }

}
