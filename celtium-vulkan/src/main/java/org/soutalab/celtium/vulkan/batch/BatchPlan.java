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
 *
 * <p>Each entry also carries the texture slot of its first item and whether that item's
 * texture rides in its instance record ({@link #textureInRecord(int)}). For a record-riding
 * entry the executor ignores the slot (the texture array is bound with the entry's pipeline);
 * for a per-texture entry, entries never fuse across a texture change, so a change in this
 * value between consecutive entries is exactly where the executor rebinds the per-texture
 * descriptor set - and a record-riding entry in between rebinds too, because it leaves the
 * texture array bound where the per-texture set was.
 *
 * <p>Each entry likewise carries the state key of its first item (the scissor / draw-state
 * bucket the caller captured with the item). Entries never fuse across a state change, so a
 * change in this value between consecutive entries is exactly where the executor re-issues
 * the entry's command state (viewport, scissor, depth bias) through its sink.
 */
public final class BatchPlan {

    private final int[] pipelineKeys;
    private final int[] indexCounts;
    private final int[] instanceCounts;
    private final int[] firstIndices;
    private final int[] vertexOffsets;
    private final int[] firstInstances;
    private final int[] textureSlots;
    private final int[] stateKeys;
    private final boolean[] textureInRecord;
    private final int sourceItems;

    BatchPlan(int[] pipelineKeys, int[] indexCounts, int[] instanceCounts, int[] firstIndices,
              int[] vertexOffsets, int[] firstInstances, int[] textureSlots, int[] stateKeys,
              boolean[] textureInRecord, int sourceItems) {
        this.pipelineKeys = pipelineKeys;
        this.indexCounts = indexCounts;
        this.instanceCounts = instanceCounts;
        this.firstIndices = firstIndices;
        this.vertexOffsets = vertexOffsets;
        this.firstInstances = firstInstances;
        this.textureSlots = textureSlots;
        this.stateKeys = stateKeys;
        this.textureInRecord = textureInRecord;
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

    /** The texture slot of the entry's first item (a GL texture name on the fallback path). */
    public int textureSlot(int entry) {
        return this.textureSlots[entry];
    }

    /** The state key of the entry's first item (the caller's scissor / draw-state bucket). */
    public int stateKey(int entry) {
        return this.stateKeys[entry];
    }

    /**
     * Whether the entry's items sample through the bindless texture array, their texture
     * riding in each item's instance record: the executor binds no per-texture set for such
     * an entry, whatever {@link #textureSlot(int)} says.
     */
    public boolean textureInRecord(int entry) {
        return this.textureInRecord[entry];
    }
}
