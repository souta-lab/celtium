package org.soutalab.celtium.vulkan.batch;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

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
 * ring. Adjacent plan entries are fused only when pipeline, vertex offset and instance slot all
 * match and the index ranges are contiguous - the one case where two captures are provably a
 * single draw.
 *
 * <p>Threading: render thread only. Vertex data is quad-shaped by contract (4 vertices per
 * primitive); other primitive modes do not go through this batcher.
 */
public final class ImmediateBatcher {

    private final DrawItemStore store = new DrawItemStore();
    private StagingRing ring;
    private int nextQuad;
    private int droppedCaptures;

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
     * Captures one quad draw. {@code vertexData}'s remaining bytes must be exactly
     * {@code quadCount * 4 * bytesPerVertex}.
     *
     * @param instanceDataIndex the draw's record in the instance-data SSBO; draws that share a
     *                          record pass the same index, which is what lets {@link #endFrame()}
     *                          fuse adjacent captures of one record into a single draw
     * @param ordered true for draws whose order against other draws is observable (translucent,
     *                GUI); false for opaque draws, which may be reordered by sort key
     * @return false when the staging ring had no room - the draw is dropped and counted in
     * {@link #droppedCaptures()}, never partially captured
     */
    public boolean captureQuads(int passId, int pipelineKey, int stateKey, int layer,
                                int textureSlot, int instanceDataIndex, boolean ordered,
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
                instanceDataIndex, textureSlot, ordered ? DrawItemStore.FLAG_ORDERED : 0);
        return true;
    }

    /** Captures dropped because the ring or the shared index space was full, over all frames. */
    public int droppedCaptures() {
        return this.droppedCaptures;
    }

    /** Items captured so far this frame. */
    public int capturedItems() {
        return this.store.size();
    }

    /** Compiles the frame's captures into the indirect draw plan and ends the frame. */
    public BatchPlan endFrame() {
        int size = this.store.size();
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        final DrawItemStore items = this.store;
        // One lexicographic rank per item: (pass, ordered-after-opaque, then key or sequence).
        // Within a pass the opaque items go first in key order - forward rendering draws opaque
        // before translucent anyway - and the ordered items follow in capture sequence, which is
        // the only order their blending may observe. Cross-pass order belongs to the frame graph,
        // not to this sort.
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                long keyA = items.sortKey(a);
                long keyB = items.sortKey(b);
                int byPass = Integer.compare(DrawSortKey.passId(keyA), DrawSortKey.passId(keyB));
                if (byPass != 0) {
                    return byPass;
                }
                boolean aOrdered = (items.flags(a) & DrawItemStore.FLAG_ORDERED) != 0;
                boolean bOrdered = (items.flags(b) & DrawItemStore.FLAG_ORDERED) != 0;
                if (aOrdered != bOrdered) {
                    return aOrdered ? 1 : -1;
                }
                if (aOrdered) {
                    return Integer.compare(DrawSortKey.sequence(keyA), DrawSortKey.sequence(keyB));
                }
                return Long.compare(keyA, keyB);
            }
        });

        int[] pipelineKeys = new int[size];
        int[] indexCounts = new int[size];
        int[] instanceCounts = new int[size];
        int[] firstIndices = new int[size];
        int[] vertexOffsets = new int[size];
        int[] firstInstances = new int[size];
        int entries = 0;
        for (int n = 0; n < size; n++) {
            int item = order[n];
            int pipeline = DrawSortKey.pipelineKey(items.sortKey(item));
            int indexCount = items.quadCount(item) * SharedQuadIndex.INDICES_PER_QUAD;
            int firstIndex = items.firstQuad(item) * SharedQuadIndex.INDICES_PER_QUAD;
            int vertexOffset = items.vertexOffset(item);
            int instance = items.instanceDataIndex(item);
            if (entries > 0
                    && pipelineKeys[entries - 1] == pipeline
                    && vertexOffsets[entries - 1] == vertexOffset
                    && firstInstances[entries - 1] == instance
                    && instanceCounts[entries - 1] == 1
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
            entries++;
        }
        BatchPlan plan = new BatchPlan(
                Arrays.copyOf(pipelineKeys, entries), Arrays.copyOf(indexCounts, entries),
                Arrays.copyOf(instanceCounts, entries), Arrays.copyOf(firstIndices, entries),
                Arrays.copyOf(vertexOffsets, entries), Arrays.copyOf(firstInstances, entries),
                size);
        this.ring = null;
        return plan;
    }
}
