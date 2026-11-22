package org.embeddedt.embeddium.impl.render.chunk.data;

import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

/**
 * ±Y level shelves (QUEUE item 671, optim-plan C1): within the POS_Y and NEG_Y buckets of an unsorted section mesh,
 * quads are grouped by the Y level of their first vertex, so the batch assembler can drop the levels a camera
 * cannot see — the levels below the camera out of the down-facing bucket, and the levels above it out of the
 * up-facing bucket — by moving a run's start or end instead of drawing the whole bucket.
 * <p>
 * The technique is ported from VulkanMod-Next (VLOD-ZDOV; reuse permitted 2026-11-21), whose
 * {@code VertexLayout.copyGrouped} groups quads the same way and whose terrain renderer trims with the same
 * cumulative table. The semantics below were read from that source, not from a summary:
 * <ul>
 *     <li>A quad's level is {@code floor(firstVertexY)} in section-local coordinates. Levels 0..15 are inside the
 *     section; anything else is "outside" and is <em>never</em> trimmed.</li>
 *     <li>Down-facing (NEG_Y) quads are ordered by ascending level with the outside quads last, so the levels below
 *     the camera form a prefix that a run start can skip.</li>
 *     <li>Up-facing (POS_Y) quads are ordered with the outside quads first and then ascending level, so the levels
 *     above the camera form a suffix that a run end can cut.</li>
 *     <li>With {@code level = floor(camY - sectionMinY)}, {@code below = clamp(level, 0, 16)} and
 *     {@code above = clamp(level + 1, 0, 16)}: the camera's own level is kept in both buckets (the trim is
 *     conservative by one level), and the clamps make the same formulas hold for cameras outside the section.</li>
 * </ul>
 * Everything here is behind {@code -Dceltium.terrain.yLevelShelves} (default off). With the flag off, no sort
 * happens, the COMPACT row layout keeps its current stride, and the assembler never consults a shelf: behaviour
 * is byte-equivalent to the code before this class existed.
 * <p>
 * This class is deliberately pure (no native memory, no LWJGL) so the sort, the cumulative table and the trim
 * arithmetic can be pinned by CPU tests; the storage layout and the assembler read and write through it.
 */
public final class YLevelShelves {
    /** Master switch, read once like the other {@code celtium.*} terrain flags. */
    public static final boolean ENABLED = Boolean.getBoolean("celtium.terrain.yLevelShelves");

    /** Levels inside one section: 0..15. */
    public static final int LEVELS = 16;

    /** Cumulative entries per bucket: one per level boundary, 0..16. */
    public static final int CUM_ENTRIES = LEVELS + 1;

    /** Shelf ints per COMPACT row: the NEG_Y cumulative table followed by the POS_Y one. */
    public static final int SHELF_INTS = 2 * CUM_ENTRIES;

    /** Sort key for quads whose level is outside the section. They are never trimmed. */
    public static final int OUTSIDE_KEY = LEVELS;

    /** Vertices per quad; buckets are quad-aligned by construction (see ChunkBuildBuffers). */
    public static final int VERTICES_PER_QUAD = 4;

    private YLevelShelves() {
    }

    /**
     * {@return the level of a quad whose first vertex has section-local Y {@code y}}
     */
    public static int levelOf(float y) {
        return (int) Math.floor(y);
    }

    /**
     * {@return the sort key of a level: the level itself inside the section, {@link #OUTSIDE_KEY} outside it}
     */
    public static int keyOf(int level) {
        return level >= 0 && level < LEVELS ? level : OUTSIDE_KEY;
    }

    /**
     * {@return how many NEG_Y prefix boundaries the camera at {@code level} (relative to the section) passes:
     * levels strictly below this are trimmed from the down-facing bucket}
     */
    public static int below(int level) {
        return clampToLevels(level);
    }

    /**
     * {@return the POS_Y suffix boundary for the camera at {@code level}: levels at or above this are trimmed
     * from the up-facing bucket. The {@code + 1} keeps the camera's own level.}
     */
    public static int above(int level) {
        return clampToLevels(level + 1);
    }

    private static int clampToLevels(int value) {
        return value < 0 ? 0 : Math.min(value, LEVELS);
    }

    /**
     * A stable counting sort of quad indices into shelf order.
     *
     * @param levels    per-quad levels, in bucket order
     * @param positiveY true for the POS_Y bucket ([outside, level 0..15]), false for NEG_Y ([level 0..15, outside])
     * @return a permutation of {@code 0..levels.length - 1}: {@code order[i]} is the source quad placed at
     * position {@code i}. Quads with equal keys keep their relative order.
     */
    public static int[] sortOrder(int[] levels, boolean positiveY) {
        int count = levels.length;
        int[] rank = new int[count];

        int[] starts = new int[CUM_ENTRIES + 1];
        for (int i = 0; i < count; i++) {
            rank[i] = rankOf(keyOf(levels[i]), positiveY);
            starts[rank[i] + 1]++;
        }
        for (int r = 0; r < CUM_ENTRIES; r++) {
            starts[r + 1] += starts[r];
        }

        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[starts[rank[i]]++] = i;
        }
        return order;
    }

    private static int rankOf(int key, boolean positiveY) {
        if (positiveY) {
            // Outside first so the suffix trim can never reach it, then ascending levels.
            return key == OUTSIDE_KEY ? 0 : key + 1;
        }
        // Ascending levels, outside last so the prefix trim can never reach it.
        return key;
    }

    /**
     * The cumulative vertex table of one bucket: {@code cum[k]} is the number of vertices in inside quads with
     * level {@code < k}, for {@code k} in 0..16. Outside quads are excluded on purpose: the assembler adds their
     * count back from the bucket total where the layout needs it (the POS_Y suffix trim).
     */
    public static int[] cumulative(int[] levels) {
        int[] counts = new int[LEVELS];
        for (int level : levels) {
            int key = keyOf(level);
            if (key != OUTSIDE_KEY) {
                counts[key]++;
            }
        }

        int[] cum = new int[CUM_ENTRIES];
        int sum = 0;
        for (int k = 0; k < LEVELS; k++) {
            cum[k] = sum * VERTICES_PER_QUAD;
            sum += counts[k];
        }
        cum[LEVELS] = sum * VERTICES_PER_QUAD;
        return cum;
    }

    /**
     * {@return the trimmed start (in vertices) of a NEG_Y bucket whose untrimmed start is {@code bucketStart},
     * given the bucket's cumulative table and the camera's level relative to the section}
     */
    public static int negTrimmedStart(int bucketStart, int[] negCum, int level) {
        return negTrimmedStart(bucketStart, negCum[below(level)]);
    }

    /**
     * The scalar core of {@link #negTrimmedStart(int, int[], int)}, for callers that read the cumulative value
     * out of native row storage instead of an array.
     */
    public static int negTrimmedStart(int bucketStart, int negCumBelow) {
        return bucketStart + negCumBelow;
    }

    /**
     * {@return the trimmed end (in vertices) of a POS_Y bucket spanning {@code [bucketStart, bucketEnd)}, given
     * the bucket's cumulative table and the camera's level relative to the section. The outside quads sit at the
     * front of the sorted bucket, so the end is the outside span plus the inside levels below
     * {@link #above(int)}.}
     */
    public static int posTrimmedEnd(int bucketStart, int bucketEnd, int[] posCum, int level) {
        return posTrimmedEnd(bucketStart, bucketEnd, posCum[LEVELS], posCum[above(level)]);
    }

    /**
     * The scalar core of {@link #posTrimmedEnd(int, int, int[], int)}: {@code posCumTotal} is the table's last
     * entry (all inside vertices), so the span before it is the never-trimmed outside quads.
     */
    public static int posTrimmedEnd(int bucketStart, int bucketEnd, int posCumTotal, int posCumAbove) {
        int outsideVertices = (bucketEnd - bucketStart) - posCumTotal;
        return bucketStart + outsideVertices + posCumAbove;
    }

    /**
     * How far the camera's integer Y can move before any section's shelf trim in a region changes, as a packed
     * half-open interval in the same format as {@code BatchAssembler.cameraValidityInterval}.
     * <p>
     * The trim of every section in the region is a clamped function of {@code cameraY - sectionMinY}; across the
     * region's section rows those functions jointly change at every integer Y inside
     * {@code [regionMinBlockY, regionMinBlockY + REGION_BLOCK_HEIGHT)} and are constant outside it, so a cached
     * batch stays valid on the whole clamped side and on exactly one integer step inside the region's span.
     *
     * @param regionMinBlockY the region's minimum block Y (its chunk Y times 16)
     */
    public static long shelfValidityIntervalY(int cameraY, int regionMinBlockY) {
        int regionMaxBlockY = regionMinBlockY + RenderRegion.REGION_BLOCK_HEIGHT;

        int min, max;
        if (cameraY < regionMinBlockY) {
            min = Integer.MIN_VALUE;
            max = regionMinBlockY;
        } else if (cameraY >= regionMaxBlockY) {
            min = regionMaxBlockY;
            max = Integer.MAX_VALUE;
        } else {
            min = cameraY;
            max = cameraY + 1;
        }

        return (min & 0xFFFFFFFFL) | ((long) max << 32);
    }
}
