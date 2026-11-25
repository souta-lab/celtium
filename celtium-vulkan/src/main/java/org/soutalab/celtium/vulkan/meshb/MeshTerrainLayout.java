package org.soutalab.celtium.vulkan.meshb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The CPU-side mirror of the terrain Tier-B data contract: the canonical SectionDesc
 * record and the TerrainVertex16 vertex, as defined by the Tier-A terrain engine's spec
 * (night-queue/SECTION-DESC-SPEC-2026-11-25.md, owner arch-terrain, ruled canonical by
 * coord-migration) and by the GLSL include {@code terrain_meshb_layout.glsl}, which the
 * Tier-B task and mesh stages include. This class is the Tier-B consumer's single swap
 * point: if the canonical definition changes, this class and the GLSL include are the
 * only consumer-side files that move, and {@code MeshTerrainLayoutTest} pins them.
 *
 * <p>SectionDesc - 48 bytes per record, twelve uint words, GPU-resident, one record per
 * (section, pass) in per-pass arrays:
 * <pre>
 *   word 0..2 : section position (i32 x, y, z, in sections)
 *   word 3    : slab id (u32)
 *   word 4    : vertex offset (u32, the section's first vertex, slab-relative, vertices)
 *   word 5    : quad start (u32, the section's first quad, slab-relative, in quads)
 *   word 6    : bucketCount[0] (low u16) | bucketCount[1] (high u16)
 *   word 7    : bucketCount[2] (low) | bucketCount[3] (high)
 *   word 8    : bucketCount[4] (low) | bucketCount[5] (high)
 *   word 9    : bucketCount[6] (low u16) | pass ordinal (u8, bits 16..23)
 *               | flags (u8, bits 24..31)
 *   word 10   : generation (u32)
 *   word 11   : sorted index base (u32, 0xFFFFFFFF when unused)
 * </pre>
 * Buckets are contiguous in bucket order inside the section's slab run, so bucket starts
 * are prefix sums of the counts. Bucket order is the producer's
 * {@code ModelQuadFacing.VALUES} order: 0 = +X, 1 = +Y, 2 = +Z, 3 = -X, 4 = -Y, 5 = -Z,
 * 6 = unassigned.
 *
 * <p>TerrainVertex16 - 16 bytes per vertex, indexless (a quad is four consecutive
 * vertices):
 * <pre>
 *   word 0 : posX 12 bits [0..11] | posY 12 bits [12..23] | posZ low 8 bits [24..31]
 *   word 1 : posZ high 4 bits [0..3] | normalId 3 bits [4..6] | material 4 bits [7..10]
 *            | lightBlock 8 bits [11..18] | lightSky 8 bits [19..26]
 *            | uvLowU 2 bits [27..28] | uvLowV 2 bits [29..30] | spare [31]
 *   word 2 : texU high 16 bits [0..15] | texV high 16 bits [16..31]
 *   word 3 : colour RGBA8
 * </pre>
 * Position keeps the COMPACT format's model space: section-relative with origin -8 and
 * range 32, quantized to 12 bits per axis (stored = round((local + 8) * 128)). UV keeps
 * the COMPACT 18-bit encoding (value = uv * 131072). Light components are lightmap texel
 * coordinates (0..255). The bilinear colour correction (a_RdhFactor) is not in this
 * format; per the spec's ruling it travels as a separate per-quad stream of one u32 per
 * quad, addressed by slab quad id.
 */
public final class MeshTerrainLayout {

    public static final int DESC_BYTES = 48;
    public static final int DESC_WORDS = 12;
    public static final int BUCKETS_PER_SECTION = 7;
    public static final int QUADS_PER_MESHLET = 64;
    public static final int VERTICES_PER_QUAD = 4;
    public static final int VERTEX_BYTES = 16;

    public static final int WORD_SECTION_X = 0;
    public static final int WORD_SECTION_Y = 1;
    public static final int WORD_SECTION_Z = 2;
    public static final int WORD_SLAB_ID = 3;
    public static final int WORD_VERTEX_OFFSET = 4;
    public static final int WORD_QUAD_START = 5;
    public static final int WORD_BUCKET_COUNT_0 = 6;
    public static final int WORD_BUCKET_COUNT_1 = 7;
    public static final int WORD_BUCKET_COUNT_2 = 8;
    public static final int WORD_PACKED_TAIL = 9;
    public static final int WORD_GENERATION = 10;
    public static final int WORD_SORTED_INDEX_BASE = 11;

    public static final int BUCKET_POS_X = 0;
    public static final int BUCKET_POS_Y = 1;
    public static final int BUCKET_POS_Z = 2;
    public static final int BUCKET_NEG_X = 3;
    public static final int BUCKET_NEG_Y = 4;
    public static final int BUCKET_NEG_Z = 5;
    public static final int BUCKET_UNASSIGNED = 6;

    /** A bucket count exceeded u16 at build time; the section stays with the legacy path. */
    public static final int FLAG_OVERSIZE = 0x01000000;
    /** word 11's sorted index base is live. */
    public static final int FLAG_SORTED_INDEX = 0x02000000;
    /** The section holds animated sprites and is tick-updated. */
    public static final int FLAG_ANIMATED = 0x04000000;

    public static final int SORTED_INDEX_UNUSED = 0xFFFFFFFF;

    private MeshTerrainLayout() {
    }

    /** The section-relative quad index at which a bucket starts (prefix sum of counts). */
    public static int bucketStartInSection(int[] bucketCounts, int bucket) {
        int start = 0;
        for (int b = 0; b < bucket; b++) {
            start += bucketCounts[b];
        }
        return start;
    }

    public static int totalQuads(int[] bucketCounts) {
        int total = 0;
        for (int count : bucketCounts) {
            total += count;
        }
        return total;
    }

    public static int meshletsForCount(int quadCount) {
        return (quadCount + QUADS_PER_MESHLET - 1) / QUADS_PER_MESHLET;
    }

    /** The mesh workgroups a record with these counts emits under a visibility mask. */
    public static int meshletsForMask(int[] bucketCounts, int bucketMask) {
        int groups = 0;
        for (int bucket = 0; bucket < BUCKETS_PER_SECTION; bucket++) {
            if ((bucketMask & (1 << bucket)) != 0) {
                groups += meshletsForCount(bucketCounts[bucket]);
            }
        }
        return groups;
    }

    /**
     * Writes one SectionDesc record. {@code bucketCounts} must hold exactly
     * {@link #BUCKETS_PER_SECTION} entries, each already within u16 (an over-count
     * section is the producer's OVERSIZE case, flagged rather than packed). {@code flags}
     * is masked to the word-9 flag byte.
     */
    public static void packDesc(ByteBuffer buffer, int byteOffset,
            int sectionX, int sectionY, int sectionZ,
            int slabId, int vertexOffset, int quadStart, int[] bucketCounts,
            int passOrdinal, int flags, int generation, int sortedIndexBase) {
        if (bucketCounts.length != BUCKETS_PER_SECTION) {
            throw new IllegalArgumentException("bucketCounts must hold "
                    + BUCKETS_PER_SECTION + " entries");
        }
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(byteOffset + WORD_SECTION_X * 4, sectionX);
        buffer.putInt(byteOffset + WORD_SECTION_Y * 4, sectionY);
        buffer.putInt(byteOffset + WORD_SECTION_Z * 4, sectionZ);
        buffer.putInt(byteOffset + WORD_SLAB_ID * 4, slabId);
        buffer.putInt(byteOffset + WORD_VERTEX_OFFSET * 4, vertexOffset);
        buffer.putInt(byteOffset + WORD_QUAD_START * 4, quadStart);
        for (int pair = 0; pair < 3; pair++) {
            buffer.putInt(byteOffset + (WORD_BUCKET_COUNT_0 + pair) * 4,
                    (bucketCounts[pair * 2] & 0xFFFF)
                            | ((bucketCounts[pair * 2 + 1] & 0xFFFF) << 16));
        }
        buffer.putInt(byteOffset + WORD_PACKED_TAIL * 4,
                (bucketCounts[6] & 0xFFFF) | ((passOrdinal & 0xFF) << 16)
                        | (flags & 0xFF000000));
        buffer.putInt(byteOffset + WORD_GENERATION * 4, generation);
        buffer.putInt(byteOffset + WORD_SORTED_INDEX_BASE * 4, sortedIndexBase);
    }

    /**
     * Writes one TerrainVertex16. {@code localX/Y/Z} are section-relative model-space
     * coordinates (origin -8, range 32 - the COMPACT format's space); {@code uvU/V} use
     * the COMPACT encoding domain (uv * 131072 in 18 bits); {@code lightBlock} and
     * {@code lightSky} are lightmap texel coordinates (0..255). Out-of-range inputs are
     * clamped or masked to their field widths.
     */
    public static void packVertex(ByteBuffer buffer, int byteOffset,
            float localX, float localY, float localZ,
            int colorRgba, float uvU, float uvV,
            int normalId, int material, int lightBlock, int lightSky) {
        int posX = quantizePosition(localX);
        int posY = quantizePosition(localY);
        int posZ = quantizePosition(localZ);
        int uvU18 = quantizeUv(uvU);
        int uvV18 = quantizeUv(uvV);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(byteOffset, posX | (posY << 12) | ((posZ & 0xFF) << 24));
        buffer.putInt(byteOffset + 4,
                ((posZ >> 8) & 0xF)
                        | ((normalId & 7) << 4)
                        | ((material & 0xF) << 7)
                        | ((lightBlock & 0xFF) << 11)
                        | ((lightSky & 0xFF) << 19)
                        | ((uvU18 & 3) << 27)
                        | ((uvV18 & 3) << 29));
        buffer.putInt(byteOffset + 8,
                ((uvU18 >> 2) & 0xFFFF) | (((uvV18 >> 2) & 0xFFFF) << 16));
        buffer.putInt(byteOffset + 12, colorRgba);
    }

    private static int quantizePosition(float local) {
        int value = Math.round((local + 8.0f) * 128.0f);
        return value < 0 ? 0 : (value > 0xFFF ? 0xFFF : value);
    }

    private static int quantizeUv(float uv) {
        int value = Math.round(uv * 131072.0f);
        return value < 0 ? 0 : (value > 0x3FFFF ? 0x3FFFF : value);
    }
}
