package org.soutalab.celtium.vulkan.batch;

/**
 * The compiled output of one frame of deferred captures: the argument array for a single
 * {@code vkCmdDrawIndexedIndirect} over the shared quad index buffer, in
 * {@code VkDrawIndexedIndirectCommand} field order (indexCount, instanceCount, firstIndex,
 * vertexOffset, firstInstance), plus the pipeline key each entry belongs to so the executor can
 * bind a pipeline whenever the key changes between consecutive entries.
 *
 * <p>Entries come from {@link ImmediateBatcher#endFrame()}: opaque items sorted by
 * {@link DrawSortKey}, ordered items in capture sequence, and adjacent entries fused when they
 * describe one contiguous, identically-stated run (see the batcher). Arrays are sized to the
 * entry count exactly; the plan is a per-frame value, not reused.
 */
public final class BatchPlan {

    private final int[] pipelineKeys;
    private final int[] indexCounts;
    private final int[] instanceCounts;
    private final int[] firstIndices;
    private final int[] vertexOffsets;
    private final int[] firstInstances;
    private final int sourceItems;

    BatchPlan(int[] pipelineKeys, int[] indexCounts, int[] instanceCounts, int[] firstIndices,
              int[] vertexOffsets, int[] firstInstances, int sourceItems) {
        this.pipelineKeys = pipelineKeys;
        this.indexCounts = indexCounts;
        this.instanceCounts = instanceCounts;
        this.firstIndices = firstIndices;
        this.vertexOffsets = vertexOffsets;
        this.firstInstances = firstInstances;
        this.sourceItems = sourceItems;
    }

    public int entryCount() {
        return this.pipelineKeys.length;
    }

    /** Captured items this plan was compiled from (before fusion). */
    public int sourceItems() {
        return this.sourceItems;
    }

    public int pipelineKey(int entry) {
        return this.pipelineKeys[entry];
    }

    public int indexCount(int entry) {
        return this.indexCounts[entry];
    }

    public int instanceCount(int entry) {
        return this.instanceCounts[entry];
    }

    public int firstIndex(int entry) {
        return this.firstIndices[entry];
    }

    public int vertexOffset(int entry) {
        return this.vertexOffsets[entry];
    }

    public int firstInstance(int entry) {
        return this.firstInstances[entry];
    }
}
