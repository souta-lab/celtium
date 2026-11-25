package org.soutalab.celtium.vulkan.terrain;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Canonical 48-byte (12 x uint32, std430) GPU-resident section descriptor for the
 * new-architecture TerrainWorldEngine (Tier-A Compute+MDI and Tier-B Task+Mesh).
 *
 * <p>Layout follows {@code SECTION-DESC-SPEC-2026-11-25.md} word-for-word:
 * <pre>
 *   word  0 : sectionX (int32)
 *   word  1 : sectionY (int32)
 *   word  2 : sectionZ (int32)
 *   word  3 : slabId (uint32)
 *   word  4 : vertexOffset (uint32, in vertices within slab)
 *   word  5 : quadStart (uint32, in quads within slab = start of bucket 0)
 *   word  6 : bucketCount[0] (u16 low) | bucketCount[1] (u16 high)
 *   word  7 : bucketCount[2] (u16 low) | bucketCount[3] (u16 high)
 *   word  8 : bucketCount[4] (u16 low) | bucketCount[5] (u16 high)
 *   word  9 : bucketCount[6] (u16 low) | passOrdinal (u8 bits 16..23) | flags (u8 bits 24..31)
 *   word 10 : generation (uint32)
 *   word 11 : sortedIndexBase (uint32, 0xFFFFFFFF when unused)
 * </pre>
 *
 * <p>Bucket order matches {@code EnumFacing.ordinal()}:
 * {@code 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST, 6=UNASSIGNED}.
 */
public final class SectionDesc {
    public static final int STRIDE_INTS = 12;
    public static final int STRIDE_BYTES = STRIDE_INTS * Integer.BYTES;

    public static final int BUCKET_DOWN = 0;
    public static final int BUCKET_UP = 1;
    public static final int BUCKET_NORTH = 2;
    public static final int BUCKET_SOUTH = 3;
    public static final int BUCKET_WEST = 4;
    public static final int BUCKET_EAST = 5;
    public static final int BUCKET_UNASSIGNED = 6;
    public static final int BUCKET_COUNT = 7;

    /** Maximum number of VkDrawIndexedIndirectCommand entries emitted per record (one per non-empty visible bucket). */
    public static final int MAX_INDIRECT_COMMANDS_PER_RECORD = BUCKET_COUNT;

    /** Maximum quad count representable in a single uint16 bucket slot before FLAG_OVERSIZE is set. */
    public static final int MAX_BUCKET_QUADS = 0xFFFF;

    /** Sentinel value for {@code sortedIndexBase} when the section uses the shared static quad index buffer. */
    public static final int UNUSED_SORTED_INDEX_BASE = 0xFFFFFFFF;

    // Flags stored in word 9 bits [24..31] (shifted by FLAGS_SHIFT = 24).
    public static final int PASS_ORDINAL_SHIFT = 16;
    public static final int FLAGS_SHIFT = 24;

    /** Bit 0 of flags (word 9 bit 24): at least one bucket exceeded uint16; ineligible for Tier-A. */
    public static final int FLAG_OVERSIZE = 1 << 0;
    /** Bit 1 of flags (word 9 bit 25): section uses sortedIndexBase for translucent quad ordering. */
    public static final int FLAG_SORTED_INDEX = 1 << 1;
    /** Bit 2 of flags (word 9 bit 26): section contains animated sprites requiring tick atlas updates. */
    public static final int FLAG_ANIMATED = 1 << 2;

    private SectionDesc() {
    }

    /**
     * Immutable unpacked view of a 48-byte {@code SectionDesc} record.
     */
    public record Record(
            int sectionX,
            int sectionY,
            int sectionZ,
            int slabId,
            int vertexOffset,
            int quadStart,
            int bucket0Down,
            int bucket1Up,
            int bucket2North,
            int bucket3South,
            int bucket4West,
            int bucket5East,
            int bucket6Unassigned,
            int passOrdinal,
            int flags,
            int generation,
            int sortedIndexBase
    ) {
        public int bucketCount(int bucket) {
            return switch (bucket) {
                case BUCKET_DOWN -> bucket0Down;
                case BUCKET_UP -> bucket1Up;
                case BUCKET_NORTH -> bucket2North;
                case BUCKET_SOUTH -> bucket3South;
                case BUCKET_WEST -> bucket4West;
                case BUCKET_EAST -> bucket5East;
                case BUCKET_UNASSIGNED -> bucket6Unassigned;
                default -> throw new IndexOutOfBoundsException("bucket=" + bucket);
            };
        }

        public int totalQuads() {
            return bucket0Down + bucket1Up + bucket2North + bucket3South
                    + bucket4West + bucket5East + bucket6Unassigned;
        }

        public int totalVertices() {
            return totalQuads() * 4;
        }

        public int totalIndices() {
            return totalQuads() * 6;
        }

        /**
         * Returns the starting quad index within the slab for bucket {@code b}:
         * {@code quadStart + sum(counts[0..b-1])}.
         */
        public int bucketQuadOffset(int bucket) {
            if (bucket < 0 || bucket >= BUCKET_COUNT) {
                throw new IndexOutOfBoundsException("bucket=" + bucket);
            }
            int offset = quadStart;
            if (bucket > 0) offset += bucket0Down;
            if (bucket > 1) offset += bucket1Up;
            if (bucket > 2) offset += bucket2North;
            if (bucket > 3) offset += bucket3South;
            if (bucket > 4) offset += bucket4West;
            if (bucket > 5) offset += bucket5East;
            return offset;
        }

        public int bucketFirstIndex(int bucket) {
            return bucketQuadOffset(bucket) * 6;
        }

        public boolean isOversize() {
            return (flags & FLAG_OVERSIZE) != 0;
        }

        public boolean usesSortedIndex() {
            return (flags & FLAG_SORTED_INDEX) != 0;
        }

        public boolean isAnimated() {
            return (flags & FLAG_ANIMATED) != 0;
        }

        /** Packs this record into 12 consecutive ints in {@code dst} starting at {@code intOffset}. */
        public void pack(int[] dst, int intOffset) {
            SectionDesc.pack(
                    dst, intOffset,
                    sectionX, sectionY, sectionZ,
                    slabId, vertexOffset, quadStart,
                    bucket0Down, bucket1Up, bucket2North, bucket3South,
                    bucket4West, bucket5East, bucket6Unassigned,
                    passOrdinal, flags, generation, sortedIndexBase
            );
        }

        /** Packs this record into 48 bytes in {@code dst} starting at {@code byteOffset}. */
        public void pack(ByteBuffer dst, int byteOffset) {
            SectionDesc.pack(
                    dst, byteOffset,
                    sectionX, sectionY, sectionZ,
                    slabId, vertexOffset, quadStart,
                    bucket0Down, bucket1Up, bucket2North, bucket3South,
                    bucket4West, bucket5East, bucket6Unassigned,
                    passOrdinal, flags, generation, sortedIndexBase
            );
        }

        /** Packs this record into 48 bytes at native address {@code ptr}. */
        public void pack(long ptr) {
            SectionDesc.pack(
                    ptr,
                    sectionX, sectionY, sectionZ,
                    slabId, vertexOffset, quadStart,
                    bucket0Down, bucket1Up, bucket2North, bucket3South,
                    bucket4West, bucket5East, bucket6Unassigned,
                    passOrdinal, flags, generation, sortedIndexBase
            );
        }
    }

    /**
     * Reusable builder for constructing a validated {@link Record}.
     * Automatically clamps oversized buckets and sets {@link #FLAG_OVERSIZE} when any
     * bucket count exceeds {@link #MAX_BUCKET_QUADS}.
     */
    public static final class Builder {
        private int sectionX;
        private int sectionY;
        private int sectionZ;
        private int slabId;
        private int vertexOffset;
        private int quadStart;
        private final int[] buckets = new int[BUCKET_COUNT];
        private int passOrdinal;
        private int flags;
        private int generation;
        private int sortedIndexBase = UNUSED_SORTED_INDEX_BASE;

        public Builder reset() {
            this.sectionX = 0;
            this.sectionY = 0;
            this.sectionZ = 0;
            this.slabId = 0;
            this.vertexOffset = 0;
            this.quadStart = 0;
            Arrays.fill(this.buckets, 0);
            this.passOrdinal = 0;
            this.flags = 0;
            this.generation = 0;
            this.sortedIndexBase = UNUSED_SORTED_INDEX_BASE;
            return this;
        }

        public Builder section(int x, int y, int z) {
            this.sectionX = x;
            this.sectionY = y;
            this.sectionZ = z;
            return this;
        }

        public Builder allocation(int slabId, int vertexOffset, int quadStart) {
            this.slabId = slabId;
            this.vertexOffset = vertexOffset;
            this.quadStart = quadStart;
            return this;
        }

        public Builder bucketCounts(int[] counts) {
            if (counts.length != BUCKET_COUNT) {
                throw new IllegalArgumentException("Expected " + BUCKET_COUNT + " bucket counts, got " + counts.length);
            }
            System.arraycopy(counts, 0, this.buckets, 0, BUCKET_COUNT);
            return this;
        }

        public Builder bucketCount(int bucket, int count) {
            if (count < 0) {
                throw new IllegalArgumentException("Negative bucket count: " + count);
            }
            this.buckets[bucket] = count;
            return this;
        }

        public Builder passOrdinal(int passOrdinal) {
            if ((passOrdinal & ~0xFF) != 0) {
                throw new IllegalArgumentException("passOrdinal out of uint8 range: " + passOrdinal);
            }
            this.passOrdinal = passOrdinal;
            return this;
        }

        public Builder flags(int flags) {
            this.flags = flags & 0xFF;
            return this;
        }

        public Builder animated(boolean animated) {
            if (animated) {
                this.flags |= FLAG_ANIMATED;
            } else {
                this.flags &= ~FLAG_ANIMATED;
            }
            return this;
        }

        public Builder generation(int generation) {
            this.generation = generation;
            return this;
        }

        public Builder sortedIndexBase(int sortedIndexBase) {
            this.sortedIndexBase = sortedIndexBase;
            if (sortedIndexBase != UNUSED_SORTED_INDEX_BASE) {
                this.flags |= FLAG_SORTED_INDEX;
            } else {
                this.flags &= ~FLAG_SORTED_INDEX;
            }
            return this;
        }

        public Record build() {
            int effectiveFlags = this.flags & 0xFF;
            int total = 0;
            int[] packedCounts = new int[BUCKET_COUNT];
            for (int i = 0; i < BUCKET_COUNT; i++) {
                int c = this.buckets[i];
                if (c < 0) {
                    throw new IllegalStateException("Negative bucket count at " + i + ": " + c);
                }
                if (c > MAX_BUCKET_QUADS) {
                    effectiveFlags |= FLAG_OVERSIZE;
                    packedCounts[i] = MAX_BUCKET_QUADS;
                } else {
                    packedCounts[i] = c;
                }
                total += c;
            }
            if (total == 0) {
                throw new IllegalStateException("Empty section record (totalQuads == 0) must not be emitted");
            }
            return new Record(
                    sectionX, sectionY, sectionZ,
                    slabId, vertexOffset, quadStart,
                    packedCounts[0], packedCounts[1], packedCounts[2], packedCounts[3],
                    packedCounts[4], packedCounts[5], packedCounts[6],
                    passOrdinal, effectiveFlags, generation, sortedIndexBase
            );
        }
    }

    public static int packPair(int lowU16, int highU16) {
        return (lowU16 & 0xFFFF) | ((highU16 & 0xFFFF) << 16);
    }

    public static int packWord9(int bucket6U16, int passOrdinalU8, int flagsU8) {
        return (bucket6U16 & 0xFFFF)
                | ((passOrdinalU8 & 0xFF) << PASS_ORDINAL_SHIFT)
                | ((flagsU8 & 0xFF) << FLAGS_SHIFT);
    }

    public static void pack(
            int[] dst, int intOffset,
            int sectionX, int sectionY, int sectionZ,
            int slabId, int vertexOffset, int quadStart,
            int b0, int b1, int b2, int b3, int b4, int b5, int b6,
            int passOrdinal, int flags, int generation, int sortedIndexBase
    ) {
        dst[intOffset + 0] = sectionX;
        dst[intOffset + 1] = sectionY;
        dst[intOffset + 2] = sectionZ;
        dst[intOffset + 3] = slabId;
        dst[intOffset + 4] = vertexOffset;
        dst[intOffset + 5] = quadStart;
        dst[intOffset + 6] = packPair(b0, b1);
        dst[intOffset + 7] = packPair(b2, b3);
        dst[intOffset + 8] = packPair(b4, b5);
        dst[intOffset + 9] = packWord9(b6, passOrdinal, flags);
        dst[intOffset + 10] = generation;
        dst[intOffset + 11] = sortedIndexBase;
    }

    public static void pack(
            ByteBuffer dst, int byteOffset,
            int sectionX, int sectionY, int sectionZ,
            int slabId, int vertexOffset, int quadStart,
            int b0, int b1, int b2, int b3, int b4, int b5, int b6,
            int passOrdinal, int flags, int generation, int sortedIndexBase
    ) {
        dst.putInt(byteOffset + 0, sectionX);
        dst.putInt(byteOffset + 4, sectionY);
        dst.putInt(byteOffset + 8, sectionZ);
        dst.putInt(byteOffset + 12, slabId);
        dst.putInt(byteOffset + 16, vertexOffset);
        dst.putInt(byteOffset + 20, quadStart);
        dst.putInt(byteOffset + 24, packPair(b0, b1));
        dst.putInt(byteOffset + 28, packPair(b2, b3));
        dst.putInt(byteOffset + 32, packPair(b4, b5));
        dst.putInt(byteOffset + 36, packWord9(b6, passOrdinal, flags));
        dst.putInt(byteOffset + 40, generation);
        dst.putInt(byteOffset + 44, sortedIndexBase);
    }

    public static void pack(
            long ptr,
            int sectionX, int sectionY, int sectionZ,
            int slabId, int vertexOffset, int quadStart,
            int b0, int b1, int b2, int b3, int b4, int b5, int b6,
            int passOrdinal, int flags, int generation, int sortedIndexBase
    ) {
        MemoryUtil.memPutInt(ptr + 0, sectionX);
        MemoryUtil.memPutInt(ptr + 4, sectionY);
        MemoryUtil.memPutInt(ptr + 8, sectionZ);
        MemoryUtil.memPutInt(ptr + 12, slabId);
        MemoryUtil.memPutInt(ptr + 16, vertexOffset);
        MemoryUtil.memPutInt(ptr + 20, quadStart);
        MemoryUtil.memPutInt(ptr + 24, packPair(b0, b1));
        MemoryUtil.memPutInt(ptr + 28, packPair(b2, b3));
        MemoryUtil.memPutInt(ptr + 32, packPair(b4, b5));
        MemoryUtil.memPutInt(ptr + 36, packWord9(b6, passOrdinal, flags));
        MemoryUtil.memPutInt(ptr + 40, generation);
        MemoryUtil.memPutInt(ptr + 44, sortedIndexBase);
    }

    public static Record unpack(int[] src, int intOffset) {
        int w6 = src[intOffset + 6];
        int w7 = src[intOffset + 7];
        int w8 = src[intOffset + 8];
        int w9 = src[intOffset + 9];
        return new Record(
                src[intOffset + 0],
                src[intOffset + 1],
                src[intOffset + 2],
                src[intOffset + 3],
                src[intOffset + 4],
                src[intOffset + 5],
                w6 & 0xFFFF,
                (w6 >>> 16) & 0xFFFF,
                w7 & 0xFFFF,
                (w7 >>> 16) & 0xFFFF,
                w8 & 0xFFFF,
                (w8 >>> 16) & 0xFFFF,
                w9 & 0xFFFF,
                (w9 >>> PASS_ORDINAL_SHIFT) & 0xFF,
                (w9 >>> FLAGS_SHIFT) & 0xFF,
                src[intOffset + 10],
                src[intOffset + 11]
        );
    }

    public static Record unpack(ByteBuffer src, int byteOffset) {
        int w6 = src.getInt(byteOffset + 24);
        int w7 = src.getInt(byteOffset + 28);
        int w8 = src.getInt(byteOffset + 32);
        int w9 = src.getInt(byteOffset + 36);
        return new Record(
                src.getInt(byteOffset + 0),
                src.getInt(byteOffset + 4),
                src.getInt(byteOffset + 8),
                src.getInt(byteOffset + 12),
                src.getInt(byteOffset + 16),
                src.getInt(byteOffset + 20),
                w6 & 0xFFFF,
                (w6 >>> 16) & 0xFFFF,
                w7 & 0xFFFF,
                (w7 >>> 16) & 0xFFFF,
                w8 & 0xFFFF,
                (w8 >>> 16) & 0xFFFF,
                w9 & 0xFFFF,
                (w9 >>> PASS_ORDINAL_SHIFT) & 0xFF,
                (w9 >>> FLAGS_SHIFT) & 0xFF,
                src.getInt(byteOffset + 40),
                src.getInt(byteOffset + 44)
        );
    }

    public static Record unpack(long ptr) {
        int w6 = MemoryUtil.memGetInt(ptr + 24);
        int w7 = MemoryUtil.memGetInt(ptr + 28);
        int w8 = MemoryUtil.memGetInt(ptr + 32);
        int w9 = MemoryUtil.memGetInt(ptr + 36);
        return new Record(
                MemoryUtil.memGetInt(ptr + 0),
                MemoryUtil.memGetInt(ptr + 4),
                MemoryUtil.memGetInt(ptr + 8),
                MemoryUtil.memGetInt(ptr + 12),
                MemoryUtil.memGetInt(ptr + 16),
                MemoryUtil.memGetInt(ptr + 20),
                w6 & 0xFFFF,
                (w6 >>> 16) & 0xFFFF,
                w7 & 0xFFFF,
                (w7 >>> 16) & 0xFFFF,
                w8 & 0xFFFF,
                (w8 >>> 16) & 0xFFFF,
                w9 & 0xFFFF,
                (w9 >>> PASS_ORDINAL_SHIFT) & 0xFF,
                (w9 >>> FLAGS_SHIFT) & 0xFF,
                MemoryUtil.memGetInt(ptr + 40),
                MemoryUtil.memGetInt(ptr + 44)
        );
    }
}
