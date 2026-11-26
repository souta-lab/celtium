package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * The Tier-A section mesher's assembly step (new architecture, design section 2.4):
 * the chunk builder's worker threads already write the final 16-byte vertex format
 * directly into their thread-local off-heap buffers (the {@link TerrainVertex16}
 * encoder, selected per pass when the Tier-A engine is on), and
 * {@code ChunkBuildBuffers.createMesh} merges the per-facing buffers in
 * {@code ModelQuadFacing.VALUES} order — so the merged data is bucket-contiguous by
 * construction. This class validates that shape, counts the buckets, and produces the
 * {@link BuiltSectionA}: the geometry plus a {@link SectionDesc} draft whose slab
 * placement the {@link TerrainSectionStore} assigns at registration.
 * <p>
 * It runs on whichever worker thread built the section; it keeps no state.
 */
public final class AsyncSectionMesher {

    /**
     * One built section, ready for the store: bucket-contiguous 16-byte vertices, the
     * optional per-quad correction-factor stream, the optional sorted index buffer
     * (translucent passes), and the SectionDesc draft (slab fields unset).
     * The buffers are borrowed from the source parts: the caller keeps ownership and
     * frees them once the store has uploaded the data.
     */
    public static final class BuiltSectionA {
        public final int sectionX;
        public final int sectionY;
        public final int sectionZ;
        public final int passOrdinal;
        public final ByteBuffer vertexData;
        public final int vertexBytes;
        /** Vertex stride of the presented stream in bytes (16 for the Tier-A format). */
        public final int stride;
        /** Per-quad bilinear correction factors in bucket order, or null when absent. */
        public final int[] rdhFactors;
        /** The pass' sorted index buffer (uint32 indices), or null for unsorted passes. */
        public final NativeBuffer sortedIndices;
        public final int[] bucketCounts = new int[SectionDesc.BUCKETS];
        public final int totalQuads;
        /** Some bucket exceeded the SectionDesc uint16 count field: Tier-A ineligible. */
        public final boolean oversize;
        public final SectionDesc descDraft;

        private BuiltSectionA(int sectionX, int sectionY, int sectionZ, int passOrdinal,
                              ByteBuffer vertexData, int vertexBytes, int stride, int[] rdhFactors,
                              NativeBuffer sortedIndices, int[] bucketCounts, boolean oversize,
                              SectionDesc descDraft) {
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            this.passOrdinal = passOrdinal;
            this.vertexData = vertexData;
            this.vertexBytes = vertexBytes;
            this.stride = stride;
            this.rdhFactors = rdhFactors;
            this.sortedIndices = sortedIndices;
            System.arraycopy(bucketCounts, 0, this.bucketCounts, 0, SectionDesc.BUCKETS);
            int total = 0;
            for (int count : bucketCounts) {
                total += count;
            }
            this.totalQuads = total;
            this.oversize = oversize;
            this.descDraft = descDraft;
        }

        /**
         * A copy of this section that presents {@code vertexData} and
         * {@code sortedIndices} in place of the borrowed source buffers, with
         * the factor stream cloned: the deferred-upload queue's copy-on-defer
         * form (T3 design section 4.2). Every other field — placement draft,
         * bucket counts, oversize verdict — is this section's own; the desc
         * draft is shared, not copied, because it is a value the mesher made
         * for this section alone and only registration writes into it.
         */
        static BuiltSectionA rebuild(BuiltSectionA source, ByteBuffer vertexData,
                                     NativeBuffer sortedIndices) {
            return new BuiltSectionA(source.sectionX, source.sectionY, source.sectionZ,
                    source.passOrdinal, vertexData, source.vertexBytes, source.stride,
                    source.rdhFactors == null ? null : source.rdhFactors.clone(),
                    sortedIndices, source.bucketCounts, source.oversize, source.descDraft);
        }

        /**
         * Bytes the store reserves in the slab: the vertices followed by the per-quad
         * factor stream. The factor area is reserved even when the stream is absent
         * (it is uploaded as zeros), so the vertex shader's factor addressing —
         * derived from the desc alone — is the same for every section.
         */
        public long slabBytes() {
            if (this.stride != TerrainVertex16.STRIDE) {
                // A foreign format's stream is self-contained: no factor area
                // is reserved for it (its encoder baked every per-vertex value
                // into the stream), so the reservation is the vertices alone.
                return (long) totalQuads * 4 * this.stride;
            }
            return (long) totalQuads * 4 * TerrainVertex16.STRIDE + (long) totalQuads * 4;
        }
    }

    /**
     * Assembles one section from its built parts.
     *
     * @param parts parts built with the {@link TerrainVertex16} format; their vertex
     *              ranges must tile the buffer contiguously in facing order
     * @param extraFlags SectionDesc flags the parts cannot express (e.g. ANIMATED)
     * @return the assembled section, or {@code null} when the parts hold no quads
     * @throws IllegalArgumentException when the parts are not in the Tier-A shape
     *         (wrong stride, non-contiguous ranges, a factor stream that disagrees
     *         with the quad count)
     */
    public BuiltSectionA build(int sectionX, int sectionY, int sectionZ, int passOrdinal,
                               BuiltSectionMeshParts parts, int extraFlags) {
        return build(sectionX, sectionY, sectionZ, passOrdinal, parts, extraFlags,
                TerrainVertex16.STRIDE);
    }

    /**
     * The stride-general form (R-17 candidate 2-MIN): the presented vertex
     * stream is assembled byte-for-byte at whatever stride the pass' format
     * declares. Bucket contiguity and the sorted shape are format-independent
     * properties of the merged parts, so the same validation applies; only the
     * vertex size changes. A foreign format carries no RDH factor stream (its
     * encoder baked the equivalent data into the stream), and the store
     * reserves no factor area for it.
     */
    public BuiltSectionA build(int sectionX, int sectionY, int sectionZ, int passOrdinal,
                               BuiltSectionMeshParts parts, int extraFlags, int stride) {
        ByteBuffer vertices = parts.vertexBuffer().getDirectBuffer();
        int vertexBytes = parts.vertexBuffer().getLength();
        if (vertexBytes % stride != 0) {
            throw new IllegalArgumentException("vertex data is not a whole number of "
                    + stride + "-byte vertices: " + vertexBytes
                    + " bytes (the section was not built with the pass' presented format)");
        }
        int vertexCount = vertexBytes / stride;
        if (vertexCount == 0) {
            return null;
        }
        if (vertexCount % 4 != 0) {
            throw new IllegalArgumentException("vertex count is not a whole number of quads: " + vertexCount);
        }

        int[] bucketCounts = new int[SectionDesc.BUCKETS];
        boolean sorted = parts.sortState() != null || parts.indexBuffer() != null;
        if (sorted) {
            // Sorted passes merge everything into the UNASSIGNED bucket and carry their
            // own index buffer; bucket culling does not apply to them.
            bucketCounts[ModelQuadFacing.UNASSIGNED.ordinal()] = vertexCount / 4;
        } else {
            int cursor = 0;
            for (Map.Entry<ModelQuadFacing, VertexRange> entry : parts.ranges().entrySet()) {
                VertexRange range = entry.getValue();
                if (range.vertexStart() != cursor) {
                    throw new IllegalArgumentException("vertex ranges are not bucket-contiguous: facing "
                            + entry.getKey() + " starts at " + range.vertexStart() + ", expected " + cursor);
                }
                if (range.vertexCount() % 4 != 0) {
                    throw new IllegalArgumentException("bucket " + entry.getKey()
                            + " does not hold whole quads: " + range.vertexCount());
                }
                bucketCounts[entry.getKey().ordinal()] = range.vertexCount() / 4;
                cursor += range.vertexCount();
            }
            if (cursor != vertexCount) {
                throw new IllegalArgumentException("vertex ranges cover " + cursor
                        + " of " + vertexCount + " vertices");
            }
        }

        int totalQuads = vertexCount / 4;
        int[] factors = parts.rdhFactors();
        if (factors != null && factors.length != totalQuads) {
            throw new IllegalArgumentException("correction-factor stream holds " + factors.length
                    + " quads for " + totalQuads + " built quads");
        }

        boolean oversize = false;
        for (int count : bucketCounts) {
            if (count > SectionDesc.MAX_BUCKET_QUADS) {
                oversize = true;
            }
        }

        SectionDesc.Builder draft = SectionDesc.builder(sectionX, sectionY, sectionZ, passOrdinal)
                .bucketCounts(bucketCounts)
                .flags(extraFlags);
        if (sorted) {
            draft.flags(SectionDesc.FLAG_SORTED_INDEX);
        }

        return new BuiltSectionA(sectionX, sectionY, sectionZ, passOrdinal, vertices, vertexBytes,
                stride, factors, parts.indexBuffer(), bucketCounts, oversize, draft.build());
    }
}
