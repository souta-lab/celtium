package org.soutalab.celtium.vulkan.batch;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.soutalab.celtium.core.shader.NonTerrainPackTarget;

/**
 * Deferred capture of immediate-mode quad draws (new-architecture design section 2.2, replacing
 * the per-draw command recording of the old immediate pipelines and the CPU index expansion of
 * {@code VulkanImmediateDrawer.expandIndices}).
 *
 * <p>At capture time nothing is recorded into a command buffer: the draw's vertex bytes are
 * copied into the frame's {@link StagingRing} and a {@link DrawItemStore} entry is appended with
 * a {@link DrawSortKey}. {@link #endFrame()} compiles the frame into a {@link BatchPlan}: items
 * are ranked by (pass, opaque-before-ordered, then key or capture sequence) - opaque items
 * sorted by key so same-pipeline draws become neighbours, ordered items (translucent, GUI -
 * anything whose blending depends on submission order) after the pass's opaque items, in
 * capture sequence.
 *
 * <p>Every captured draw owns the next slice of the shared quad index space
 * ({@link SharedQuadIndex}), so an item's draw arguments are
 * {@code firstIndex = firstQuad * 6} and
 * {@code vertexOffset = ringVertexBase - firstQuad * 4}: the shared pattern's indices for quad
 * {@code q} are {@code 4q..4q+3}, and adding the offset lands them on this draw's vertices in the
 * ring. Adjacent plan entries are fused only when pipeline, state key, vertex offset and
 * instance slot all match and the index ranges are contiguous - the one case where two
 * captures are provably a single draw. The state key carries the draw-state boundary the
 * caller captured with the item (the scissor rectangle and alpha/depth state hash, S5-2
 * slicing section 3-6): a fused entry draws under one state, so a state change is a fusion
 * boundary even when everything else matches. The texture rule is per item, not per frame:
 * an item whose texture rides in its record ({@link DrawItemStore#FLAG_TEXTURE_IN_RECORD})
 * samples through the bindless texture array, so two such items may fuse across a texture
 * change; any other pairing must also match texture slots, because a fused entry that binds
 * a per-texture set can only sample the one bound texture.
 *
 * <p>Threading: render thread only. Vertex data is quad-shaped by contract (4 vertices per
 * primitive); other primitive modes do not go through this batcher.
 */
public final class ImmediateBatcher {

    private final DrawItemStore store = new DrawItemStore();
    private StagingRing ring;
    private int nextQuad;
    private int droppedCaptures;
    private int[] sortOrder;
    private int[] sortScratch;
    private long[] rankPrimary;
    private long[] rankSecondary;
    private final int[] sortBuckets = new int[256];

    /** Starts a frame on the given ring (its current slot receives this frame's copies). */
    public void beginFrame(StagingRing stagingRing) {
        this.ring = stagingRing;
        this.store.clear();
        this.nextQuad = 0;
    }

    /**
     * Captures one quad draw whose instance data is a fresh record: the capture's own index
     * becomes its instance-data index, so it can never fuse with a neighbour. Use
     * {@link #captureQuads(int, int, int, int, int, int, boolean, ByteBuffer, int, int)} when
     * several draws share one instance record.
     */
    public boolean captureQuads(int passId, int pipelineKey, int stateKey, int layer,
                                int textureSlot, boolean ordered,
                                ByteBuffer vertexData, int quadCount, int bytesPerVertex) {
        return captureQuads(passId, pipelineKey, stateKey, layer, textureSlot,
                this.store.size(), ordered, vertexData, quadCount, bytesPerVertex);
    }

    /**
     * Captures one quad draw whose texture is bound per texture at execution (the fallback
     * form); see {@link #captureQuads(int, int, int, int, int, boolean, int, boolean,
     * ByteBuffer, int, int)} for the record-riding form.
     */
    public boolean captureQuads(int passId, int pipelineKey, int stateKey, int layer,
                                int textureSlot, int instanceDataIndex, boolean ordered,
                                ByteBuffer vertexData, int quadCount, int bytesPerVertex) {
        return captureQuads(passId, pipelineKey, stateKey, layer, textureSlot, false,
                instanceDataIndex, ordered, vertexData, quadCount, bytesPerVertex);
    }

    /**
     * Captures one quad draw. {@code vertexData}'s remaining bytes must be exactly
     * {@code quadCount * 4 * bytesPerVertex}.
     *
     * @param textureInRecord true when the draw's texture rides in its instance record (the
     *                        writer stored the bindless array index in the record): the item
     *                        samples through the texture array, fuses across texture changes
     *                        with other record-riding items, and the executor never binds a
     *                        per-texture set for its entry. False is the per-texture form.
     * @param instanceDataIndex the draw's record in the instance-data SSBO; draws that share a
     *                          record pass the same index, which is what lets {@link #endFrame()}
     *                          fuse adjacent captures of one record into a single draw
     * @param ordered true for draws whose order against other draws is observable (translucent,
     *                GUI); false for opaque draws, which may be reordered by sort key
     * @return false when the staging ring had no room - the draw is dropped and counted in
     * {@link #droppedCaptures()}, never partially captured
     */
    public boolean captureQuads(int passId, int pipelineKey, int stateKey, int layer,
                                int textureSlot, boolean textureInRecord, int instanceDataIndex,
                                boolean ordered,
                                ByteBuffer vertexData, int quadCount, int bytesPerVertex) {
        if (this.ring == null) {
            throw new IllegalStateException("beginFrame was not called");
        }
        if (quadCount < 0 || quadCount > SharedQuadIndex.MAX_QUADS - this.nextQuad) {
            this.droppedCaptures++;
            return false;
        }
        int vertexBytes = quadCount * SharedQuadIndex.VERTICES_PER_QUAD * bytesPerVertex;
        if (vertexData.remaining() != vertexBytes) {
            throw new IllegalArgumentException("vertex data holds " + vertexData.remaining()
                    + " bytes, " + quadCount + " quads at " + bytesPerVertex
                    + " bytes/vertex need " + vertexBytes);
        }
        long offset = this.ring.allocate(vertexBytes, bytesPerVertex);
        if (offset < 0) {
            this.droppedCaptures++;
            return false;
        }
        this.ring.write(offset, vertexData);
        int firstQuad = this.nextQuad;
        this.nextQuad += quadCount;
        int itemIndex = this.store.size();
        long sortKey = DrawSortKey.encode(passId, pipelineKey, stateKey, layer, itemIndex);
        int vertexOffset = (int) (offset / bytesPerVertex)
                - firstQuad * SharedQuadIndex.VERTICES_PER_QUAD;
        this.store.append(sortKey, vertexOffset, firstQuad, quadCount,
                instanceDataIndex, textureSlot,
                (ordered ? DrawItemStore.FLAG_ORDERED : 0)
                        | (textureInRecord ? DrawItemStore.FLAG_TEXTURE_IN_RECORD : 0));
        return true;
    }

    /** Captures dropped because the ring or the shared index space was full, over all frames. */
    public int droppedCaptures() {
        return this.droppedCaptures;
    }

    // -- interface 2 receptacle (S3-a item 5) --------------------------------------
    //
    // While a pack stage is active the executor draws this batcher's plan through the
    // stage's pipelines; the batcher itself only holds the target. Capture and endFrame
    // never read the field, so with no pack active the lane pays exactly nothing for the
    // receptacle - the zero-tax passthrough the plan requires - and with a pack active the
    // resolution happens once per plan entry at record time, not per captured draw.

    /** {@link #packPipeline(String)} result when no pack target is active. */
    public static final long PACK_NATIVE = -1L;

    private NonTerrainPackTarget packTarget;

    /** Sets the active pack target, or {@code null} when no pack stage is active. */
    public void setPackTarget(NonTerrainPackTarget target) {
        this.packTarget = target;
    }

    public NonTerrainPackTarget packTarget() {
        return this.packTarget;
    }

    /**
     * The pipeline a pack program resolves to for this batcher's draws: the active target's
     * handle, {@link NonTerrainPackTarget#NO_PIPELINE} when the stage does not define the
     * program (the draw is skipped), or {@link #PACK_NATIVE} when no pack is active.
     */
    public long packPipeline(String programName) {
        NonTerrainPackTarget target = this.packTarget;
        return target == null ? PACK_NATIVE : target.pipelineHandle(programName);
    }

    /** Items captured so far this frame. */
    public int capturedItems() {
        return this.store.size();
    }

    /** Compiles the frame's captures into the indirect draw plan and ends the frame. */
    public BatchPlan endFrame() {
        int size = this.store.size();
        int[] order = orderFor(size);
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        final DrawItemStore items = this.store;
        // One lexicographic rank per item: (pass, ordered-after-opaque, then key or sequence).
        // Within a pass the opaque items go first in key order - forward rendering draws opaque
        // before translucent anyway - and the ordered items follow in capture sequence, which is
        // the only order their blending may observe. Cross-pass order belongs to the frame graph,
        // not to this sort. The rank is folded into the two radix keys (S3-a: the megabatch sort
        // is a radix sort now, same order, no comparisons).
        for (int i = 0; i < size; i++) {
            long sortKey = items.sortKey(i);
            boolean orderedItem = (items.flags(i) & DrawItemStore.FLAG_ORDERED) != 0;
            this.rankPrimary[i] = DrawKeyRadixSort.primaryKey(sortKey, orderedItem);
            this.rankSecondary[i] = DrawKeyRadixSort.secondaryKey(sortKey);
        }
        DrawKeyRadixSort.sort(order, this.rankPrimary, this.rankSecondary, size,
                this.sortScratch, this.sortBuckets);

        int[] pipelineKeys = new int[size];
        int[] indexCounts = new int[size];
        int[] instanceCounts = new int[size];
        int[] firstIndices = new int[size];
        int[] vertexOffsets = new int[size];
        int[] firstInstances = new int[size];
        int[] textureSlots = new int[size];
        int[] stateKeys = new int[size];
        boolean[] textureInRecord = new boolean[size];
        int entries = 0;
        for (int n = 0; n < size; n++) {
            int item = order[n];
            int pipeline = DrawSortKey.pipelineKey(items.sortKey(item));
            int stateKey = DrawSortKey.stateKey(items.sortKey(item));
            int indexCount = items.quadCount(item) * SharedQuadIndex.INDICES_PER_QUAD;
            int firstIndex = items.firstQuad(item) * SharedQuadIndex.INDICES_PER_QUAD;
            int vertexOffset = items.vertexOffset(item);
            int instance = items.instanceDataIndex(item);
            int textureSlot = items.textureSlot(item);
            boolean itemInRecord =
                    (items.flags(item) & DrawItemStore.FLAG_TEXTURE_IN_RECORD) != 0;
            // The texture rule, per entry: an entry spans a texture change only when its own
            // first item and the candidate both ride in their records (the entry's items all
            // share its pipeline, and the array form is a pipeline of its own, so the first
            // item's bit speaks for the whole entry); otherwise the slots must match, as a
            // per-texture entry can only sample the one bound texture.
            if (entries > 0
                    && pipelineKeys[entries - 1] == pipeline
                    && stateKeys[entries - 1] == stateKey
                    && vertexOffsets[entries - 1] == vertexOffset
                    && firstInstances[entries - 1] == instance
                    && instanceCounts[entries - 1] == 1
                    && ((textureInRecord[entries - 1] && itemInRecord)
                            || textureSlots[entries - 1] == textureSlot)
                    && firstIndices[entries - 1] + indexCounts[entries - 1] == firstIndex) {
                indexCounts[entries - 1] += indexCount;
                continue;
            }
            pipelineKeys[entries] = pipeline;
            indexCounts[entries] = indexCount;
            instanceCounts[entries] = 1;
            firstIndices[entries] = firstIndex;
            vertexOffsets[entries] = vertexOffset;
            firstInstances[entries] = instance;
            textureSlots[entries] = textureSlot;
            stateKeys[entries] = stateKey;
            textureInRecord[entries] = itemInRecord;
            entries++;
        }
        BatchPlan plan = new BatchPlan(
                Arrays.copyOf(pipelineKeys, entries), Arrays.copyOf(indexCounts, entries),
                Arrays.copyOf(instanceCounts, entries), Arrays.copyOf(firstIndices, entries),
                Arrays.copyOf(vertexOffsets, entries), Arrays.copyOf(firstInstances, entries),
                Arrays.copyOf(textureSlots, entries), Arrays.copyOf(stateKeys, entries),
                Arrays.copyOf(textureInRecord, entries),
                size);
        this.ring = null;
        return plan;
    }

    /**
     * The frame's ranking workspace, grown to the largest frame seen and kept across frames
     * (designer audit 2026-11-25, Stage 5: the previous {@code Integer[]} ranking allocated and
     * boxed every item on every frame). Only the first {@code size} entries are live in any one
     * {@link #endFrame()}; the rest is stale capacity, never read.
     */
    private int[] orderFor(int size) {
        if (this.sortOrder == null || this.sortOrder.length < size) {
            int capacity = Math.max(size,
                    this.sortOrder == null ? 1024 : this.sortOrder.length * 2);
            this.sortOrder = new int[capacity];
            this.sortScratch = new int[capacity];
            this.rankPrimary = new long[capacity];
            this.rankSecondary = new long[capacity];
        }
        return this.sortOrder;
    }
}
