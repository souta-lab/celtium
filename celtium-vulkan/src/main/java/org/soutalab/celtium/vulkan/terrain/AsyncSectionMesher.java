package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Off-heap direct-write section mesher producing 7 face-direction contiguous buckets of
 * {@link TerrainVertex16} (16 B/vertex) with zero Java heap per-quad allocation
 * ({@code CELTIUM-NEW-ARCH-2026-11-25.md} §2.4, {@code SECTION-DESC-SPEC-2026-11-25.md} Step 2).
 *
 * <p>Worker threads each own a reusable {@link Scratch} instance (via {@link #threadLocalScratch()})
 * containing 7 direct off-heap bucket buffers. Quads from existing Forge/Celeritas model callbacks
 * are encoded directly into {@link TerrainVertex16} bytes without intermediate Java-heap arrays or
 * main-thread repacking. {@link Scratch#finish} compacts the 7 buckets contiguously in
 * {@code EnumFacing} order ({@code DOWN..EAST, UNASSIGNED}) into a single off-heap
 * {@link BuiltSection16} ready for {@link TerrainUploadBudget}.
 */
public final class AsyncSectionMesher {
    public static final int QUAD_VERTICES = 4;
    public static final int QUAD_BYTES = QUAD_VERTICES * TerrainVertex16.STRIDE; // 64 bytes/quad
    public static final int INITIAL_BUCKET_QUADS = 128;

    private static final ThreadLocal<Scratch> THREAD_LOCAL = ThreadLocal.withInitial(Scratch::new);

    private AsyncSectionMesher() {
    }

    public static Scratch threadLocalScratch() {
        return THREAD_LOCAL.get();
    }

    /**
     * Completed off-heap terrain section mesh in final GPU {@link TerrainVertex16} layout
     * (7 contiguous face-direction buckets). Owns its native allocation until {@link #close()}.
     */
    public static final class BuiltSection16 implements AutoCloseable {
        private final int sectionX;
        private final int sectionY;
        private final int sectionZ;
        private final int passOrdinal;
        private final int generation;
        private final boolean animated;
        private final int[] bucketQuadCounts;
        private final int totalQuads;
        private final int byteSize;
        private final ByteBuffer vertexData;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        BuiltSection16(
                int sectionX, int sectionY, int sectionZ,
                int passOrdinal, int generation, boolean animated,
                int[] bucketQuadCounts, int totalQuads,
                ByteBuffer vertexData, int byteSize
        ) {
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            this.passOrdinal = passOrdinal;
            this.generation = generation;
            this.animated = animated;
            this.bucketQuadCounts = bucketQuadCounts;
            this.totalQuads = totalQuads;
            this.vertexData = vertexData;
            this.byteSize = byteSize;
        }

        public int sectionX() {
            return sectionX;
        }

        public int sectionY() {
            return sectionY;
        }

        public int sectionZ() {
            return sectionZ;
        }

        public int passOrdinal() {
            return passOrdinal;
        }

        public int generation() {
            return generation;
        }

        public boolean animated() {
            return animated;
        }

        public int totalQuads() {
            return totalQuads;
        }

        public int totalVertices() {
            return totalQuads * QUAD_VERTICES;
        }

        public int byteSize() {
            return byteSize;
        }

        public int bucketQuadCount(int bucket) {
            return bucketQuadCounts[bucket];
        }

        public int[] bucketQuadCountsCopy() {
            return Arrays.copyOf(bucketQuadCounts, bucketQuadCounts.length);
        }

        public boolean isClosed() {
            return closed.get();
        }

        /**
         * Returns a read-only native-order slice of the compacted 16 B vertex bytes.
         * Throws {@link IllegalStateException} if already released (guards against deferred-upload UAF).
         */
        public ByteBuffer vertexDataView() {
            if (closed.get()) {
                throw new IllegalStateException("BuiltSection16 already closed");
            }
            return vertexData.slice().order(ByteOrder.nativeOrder()).limit(byteSize);
        }

        public long vertexDataAddress() {
            if (closed.get()) {
                throw new IllegalStateException("BuiltSection16 already closed");
            }
            return MemoryUtil.memAddress(vertexData);
        }

        /**
         * Builds a {@link SectionDesc.Record} binding this mesh to a GPU slab allocation.
         */
        public SectionDesc.Record toSectionDesc(int slabId, int vertexOffset, int quadStart, int sortedIndexBase) {
            return new SectionDesc.Builder()
                    .section(sectionX, sectionY, sectionZ)
                    .allocation(slabId, vertexOffset, quadStart)
                    .bucketCounts(bucketQuadCounts)
                    .passOrdinal(passOrdinal)
                    .animated(animated)
                    .generation(generation)
                    .sortedIndexBase(sortedIndexBase)
                    .build();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                MemoryUtil.memFree(vertexData);
            }
        }
    }

    /**
     * Thread-local off-heap scratch builder holding 7 dynamically growing native bucket buffers.
     * Call {@link #reset()} before meshing a section+pass, {@link #emitQuad} for each quad, and
     * {@link #finish} to compact into an owned {@link BuiltSection16} (or {@code null} if empty).
     */
    public static final class Scratch implements AutoCloseable {
        private final ByteBuffer[] bucketBuffers = new ByteBuffer[SectionDesc.BUCKET_COUNT];
        private final int[] bucketCapacitiesQuads = new int[SectionDesc.BUCKET_COUNT];
        private final int[] bucketQuadCounts = new int[SectionDesc.BUCKET_COUNT];
        private boolean animated;
        private boolean freed;

        public Scratch() {
            for (int i = 0; i < SectionDesc.BUCKET_COUNT; i++) {
                bucketCapacitiesQuads[i] = INITIAL_BUCKET_QUADS;
                bucketBuffers[i] = MemoryUtil.memAlloc(INITIAL_BUCKET_QUADS * QUAD_BYTES)
                        .order(ByteOrder.nativeOrder());
            }
        }

        public void reset() {
            checkOpen();
            Arrays.fill(bucketQuadCounts, 0);
            animated = false;
        }

        public void markAnimated() {
            this.animated = true;
        }

        public int totalQuads() {
            int sum = 0;
            for (int c : bucketQuadCounts) {
                sum += c;
            }
            return sum;
        }

        public int bucketQuadCount(int bucket) {
            return bucketQuadCounts[bucket];
        }

        /**
         * Encodes a 4-vertex quad directly into the specified face-direction bucket's off-heap buffer.
         */
        public void emitQuad(
                int bucket,
                int materialBits,
                ChunkVertexEncoder.Vertex[] quad4,
                int normalId
        ) {
            checkOpen();
            if (bucket < 0 || bucket >= SectionDesc.BUCKET_COUNT) {
                throw new IndexOutOfBoundsException("bucket=" + bucket);
            }
            if (quad4.length < QUAD_VERTICES) {
                throw new IllegalArgumentException("Expected at least 4 vertices, got " + quad4.length);
            }
            int quadIndex = bucketQuadCounts[bucket];
            ensureBucketCapacity(bucket, quadIndex + 1);
            long basePtr = MemoryUtil.memAddress(bucketBuffers[bucket]) + ((long) quadIndex * QUAD_BYTES);

            for (int v = 0; v < QUAD_VERTICES; v++) {
                ChunkVertexEncoder.Vertex vertex = quad4[v];
                TerrainVertex16.encodeToAddress(
                        basePtr + ((long) v * TerrainVertex16.STRIDE),
                        vertex.x, vertex.y, vertex.z,
                        normalId,
                        materialBits,
                        vertex.light,
                        vertex.u, vertex.v,
                        vertex.color
                );
            }
            bucketQuadCounts[bucket] = quadIndex + 1;
        }

        /**
         * Compacts all non-empty buckets contiguously in {@code 0..6} order into a newly allocated
         * off-heap {@link BuiltSection16}. Returns {@code null} if no quads were emitted (empty
         * section records must not be emitted per {@code SECTION-DESC-SPEC-2026-11-25.md}).
         */
        public BuiltSection16 finish(
                int sectionX, int sectionY, int sectionZ,
                int passOrdinal, int generation
        ) {
            checkOpen();
            int total = totalQuads();
            if (total == 0) {
                return null;
            }
            int totalBytes = total * QUAD_BYTES;
            ByteBuffer compacted = MemoryUtil.memAlloc(totalBytes).order(ByteOrder.nativeOrder());
            long dstPtr = MemoryUtil.memAddress(compacted);
            long dstOffset = 0L;
            int[] countsCopy = new int[SectionDesc.BUCKET_COUNT];

            for (int b = 0; b < SectionDesc.BUCKET_COUNT; b++) {
                int count = bucketQuadCounts[b];
                countsCopy[b] = count;
                if (count > 0) {
                    long bytes = (long) count * QUAD_BYTES;
                    MemoryUtil.memCopy(MemoryUtil.memAddress(bucketBuffers[b]), dstPtr + dstOffset, bytes);
                    dstOffset += bytes;
                }
            }
            return new BuiltSection16(
                    sectionX, sectionY, sectionZ,
                    passOrdinal, generation, animated,
                    countsCopy, total, compacted, totalBytes
            );
        }

        private void ensureBucketCapacity(int bucket, int requiredQuads) {
            int cap = bucketCapacitiesQuads[bucket];
            if (requiredQuads <= cap) {
                return;
            }
            int newCap = Math.max(cap * 2, requiredQuads);
            ByteBuffer resized = MemoryUtil.memRealloc(bucketBuffers[bucket], newCap * QUAD_BYTES)
                    .order(ByteOrder.nativeOrder());
            bucketBuffers[bucket] = resized;
            bucketCapacitiesQuads[bucket] = newCap;
        }

        private void checkOpen() {
            if (freed) {
                throw new IllegalStateException("Scratch already freed");
            }
        }

        @Override
        public void close() {
            if (!freed) {
                freed = true;
                for (int i = 0; i < SectionDesc.BUCKET_COUNT; i++) {
                    if (bucketBuffers[i] != null) {
                        MemoryUtil.memFree(bucketBuffers[i]);
                        bucketBuffers[i] = null;
                    }
                }
            }
        }
    }
}
