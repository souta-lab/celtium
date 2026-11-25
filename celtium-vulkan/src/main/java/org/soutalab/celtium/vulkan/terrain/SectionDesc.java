package org.soutalab.celtium.vulkan.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One resident terrain section record of the new rendering architecture: 48 bytes,
 * twelve uint32 words, std430, laid out by {@code SECTION-DESC-SPEC-2026-11-25.md}.
 * A record is per (section, render pass); a pass with zero quads in the section gets
 * no record at all.
 *
 * <pre>
 * word  0: sectionX (int32, world section coordinate)
 * word  1: sectionY
 * word  2: sectionZ
 * word  3: slabId (uint32)
 * word  4: vertexOffset (uint32, in vertices, from the slab's start)
 * word  5: quadStart (uint32, in quads, from the slab's start; bucket 0 begins here)
 * word  6: bucketCount[0] (uint16 low) | bucketCount[1] (uint16 high)
 * word  7: bucketCount[2] | bucketCount[3]
 * word  8: bucketCount[4] | bucketCount[5]
 * word  9: bucketCount[6] (low) | passOrdinal (uint8, bits 16..23) | flags (uint8, bits 24..31)
 * word 10: generation (uint32, bumped on every content update)
 * word 11: sortedIndexBase (uint32, in indices into the sorted-index arena; SORTED_INDEX_NONE when unused)
 * </pre>
 *
 * Buckets are stored contiguously in {@code ModelQuadFacing.VALUES} order
 * (0=+X, 1=+Y, 2=+Z, 3=-X, 4=-Y, 5=-Z, 6=UNASSIGNED); bucket {@code b} starts at
 * {@code quadStart + sum(counts[0..b-1])}. The section AABB is derived from the section
 * coordinates times 16, exactly like the E10 section box.
 */
public final class SectionDesc {
    public static final int BYTES = 48;
    public static final int WORDS = 12;
    public static final int BUCKETS = 7;

    /** Largest bucket quad count the uint16 fields hold. */
    public static final int MAX_BUCKET_QUADS = 0xFFFF;

    /** word 11 value when the record carries no sorted index range. */
    public static final long SORTED_INDEX_NONE = 0xFFFFFFFFL;

    /** Some bucket count exceeded uint16; the section is Tier-A ineligible (legacy route). */
    public static final int FLAG_OVERSIZE = 1;
    /** The record's indices live in the sorted-index arena at {@code sortedIndexBase}. */
    public static final int FLAG_SORTED_INDEX = 2;
    /** The section holds animated sprites and is a per-tick update target. */
    public static final int FLAG_ANIMATED = 4;

    public int sectionX;
    public int sectionY;
    public int sectionZ;
    public long slabId;
    public long vertexOffset;
    public long quadStart;
    public final int[] bucketCounts = new int[BUCKETS];
    public int passOrdinal;
    public int flags;
    public long generation;
    public long sortedIndexBase = SORTED_INDEX_NONE;

    public int totalQuads() {
        int total = 0;
        for (int count : this.bucketCounts) {
            total += count;
        }
        return total;
    }

    public int totalVertices() {
        return totalQuads() * 4;
    }

    public int totalIndices() {
        return totalQuads() * 6;
    }

    /** First quad of bucket {@code b}, relative to the section's own quad 0. */
    public int bucketStartQuad(int bucket) {
        int start = 0;
        for (int i = 0; i < bucket; i++) {
            start += this.bucketCounts[i];
        }
        return start;
    }

    public boolean oversize() {
        return (this.flags & FLAG_OVERSIZE) != 0;
    }

    public boolean sorted() {
        return (this.flags & FLAG_SORTED_INDEX) != 0;
    }

    /** Writes the 12 words into {@code out} at {@code wordOffset}, unsigned values as raw bits. */
    public void pack(int[] out, int wordOffset) {
        out[wordOffset + 0] = this.sectionX;
        out[wordOffset + 1] = this.sectionY;
        out[wordOffset + 2] = this.sectionZ;
        out[wordOffset + 3] = (int) this.slabId;
        out[wordOffset + 4] = (int) this.vertexOffset;
        out[wordOffset + 5] = (int) this.quadStart;
        out[wordOffset + 6] = packCounts(0);
        out[wordOffset + 7] = packCounts(2);
        out[wordOffset + 8] = packCounts(4);
        out[wordOffset + 9] = (this.bucketCounts[6] & 0xFFFF)
                | ((this.passOrdinal & 0xFF) << 16)
                | ((this.flags & 0xFF) << 24);
        out[wordOffset + 10] = (int) this.generation;
        out[wordOffset + 11] = (int) this.sortedIndexBase;
    }

    private int packCounts(int first) {
        return (this.bucketCounts[first] & 0xFFFF) | ((this.bucketCounts[first + 1] & 0xFFFF) << 16);
    }

    /** Writes the record little-endian into {@code out} at its current position. */
    public void pack(ByteBuffer out) {
        ByteOrder previous = out.order();
        out.order(ByteOrder.LITTLE_ENDIAN);
        try {
            out.putInt(this.sectionX);
            out.putInt(this.sectionY);
            out.putInt(this.sectionZ);
            out.putInt((int) this.slabId);
            out.putInt((int) this.vertexOffset);
            out.putInt((int) this.quadStart);
            out.putInt(packCounts(0));
            out.putInt(packCounts(2));
            out.putInt(packCounts(4));
            out.putInt((this.bucketCounts[6] & 0xFFFF)
                    | ((this.passOrdinal & 0xFF) << 16)
                    | ((this.flags & 0xFF) << 24));
            out.putInt((int) this.generation);
            out.putInt((int) this.sortedIndexBase);
        } finally {
            out.order(previous);
        }
    }

    public static SectionDesc unpack(int[] in, int wordOffset) {
        SectionDesc desc = new SectionDesc();
        desc.sectionX = in[wordOffset + 0];
        desc.sectionY = in[wordOffset + 1];
        desc.sectionZ = in[wordOffset + 2];
        desc.slabId = Integer.toUnsignedLong(in[wordOffset + 3]);
        desc.vertexOffset = Integer.toUnsignedLong(in[wordOffset + 4]);
        desc.quadStart = Integer.toUnsignedLong(in[wordOffset + 5]);
        unpackCounts(desc, 0, in[wordOffset + 6]);
        unpackCounts(desc, 2, in[wordOffset + 7]);
        unpackCounts(desc, 4, in[wordOffset + 8]);
        int word9 = in[wordOffset + 9];
        desc.bucketCounts[6] = word9 & 0xFFFF;
        desc.passOrdinal = (word9 >>> 16) & 0xFF;
        desc.flags = (word9 >>> 24) & 0xFF;
        desc.generation = Integer.toUnsignedLong(in[wordOffset + 10]);
        desc.sortedIndexBase = Integer.toUnsignedLong(in[wordOffset + 11]);
        return desc;
    }

    private static void unpackCounts(SectionDesc desc, int first, int word) {
        desc.bucketCounts[first] = word & 0xFFFF;
        desc.bucketCounts[first + 1] = (word >>> 16) & 0xFFFF;
    }

    public static Builder builder(int sectionX, int sectionY, int sectionZ, int passOrdinal) {
        return new Builder(sectionX, sectionY, sectionZ, passOrdinal);
    }

    public static final class Builder {
        private final SectionDesc desc = new SectionDesc();

        private Builder(int sectionX, int sectionY, int sectionZ, int passOrdinal) {
            this.desc.sectionX = sectionX;
            this.desc.sectionY = sectionY;
            this.desc.sectionZ = sectionZ;
            this.desc.passOrdinal = passOrdinal;
        }

        public Builder slab(long slabId, long vertexOffset, long quadStart) {
            this.desc.slabId = slabId;
            this.desc.vertexOffset = vertexOffset;
            this.desc.quadStart = quadStart;
            return this;
        }

        /**
         * Sets one bucket's quad count. A count above {@link #MAX_BUCKET_QUADS} cannot be
         * represented: it saturates and raises {@link #FLAG_OVERSIZE}, which marks the
         * record Tier-A ineligible; the caller keeps the true count from the mesher.
         */
        public Builder bucketCount(int bucket, int quads) {
            if (quads > MAX_BUCKET_QUADS) {
                this.desc.flags |= FLAG_OVERSIZE;
                quads = MAX_BUCKET_QUADS;
            }
            this.desc.bucketCounts[bucket] = quads;
            return this;
        }

        public Builder bucketCounts(int[] quads) {
            for (int i = 0; i < BUCKETS; i++) {
                bucketCount(i, quads[i]);
            }
            return this;
        }

        public Builder flags(int flags) {
            this.desc.flags |= flags;
            return this;
        }

        public Builder generation(long generation) {
            this.desc.generation = generation;
            return this;
        }

        public Builder sortedIndexBase(long base) {
            this.desc.sortedIndexBase = base;
            if (base != SORTED_INDEX_NONE) {
                this.desc.flags |= FLAG_SORTED_INDEX;
            }
            return this;
        }

        public SectionDesc build() {
            return this.desc;
        }
    }
}
