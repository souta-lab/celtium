package org.soutalab.celtium.vulkan.batch;

/**
 * The megabatch ordering of Lane 2 (design V040-FINAL-PLAN section 4.2, stage S3-a item 2):
 * a stable LSD radix sort over a two-part rank key per draw item, replacing the comparison
 * merge sort the batcher used before. No allocation per frame (caller-owned workspaces), and
 * the ordering is exactly the one the merge sort produced: (pass, opaque before ordered
 * within the pass, then the sort key for opaque items and the capture sequence for ordered
 * ones).
 *
 * <p>The rank does not fit one long - pass, the ordered bit, pipeline, state, layer and the
 * 24-bit sequence add up to 65 bits - so it is split where the comparison splits:
 * {@link #primaryKey} carries (pass, ordered, pipeline, state, layer) and
 * {@link #secondaryKey} carries the sequence. An ordered item's primary key zeroes the
 * pipeline/state/layer fields, because its rank ignores them (capture order is its whole
 * order within the pass). Sorting stably by the secondary key first and then by the primary
 * key gives the lexicographic rank order. Both keys are unsigned ranks: they are compared by
 * their bits, never as signed longs.
 */
public final class DrawKeyRadixSort {

    private DrawKeyRadixSort() {
    }

    /** The primary rank key one item sorts by; {@code sortKey} is its {@link DrawSortKey}. */
    public static long primaryKey(long sortKey, boolean ordered) {
        long packed = sortKey ^ Long.MIN_VALUE;
        long pass = packed & 0xFF00_0000_0000_0000L;
        if (ordered) {
            return pass | (1L << 55);
        }
        long pipeline = (packed >>> 40) & 0xFFFFL;
        long state = (packed >>> 32) & 0xFFL;
        long layer = (packed >>> 24) & 0xFFL;
        return pass | (pipeline << 39) | (state << 31) | (layer << 23);
    }

    /** The secondary rank key: the item's capture sequence (the sort key's low field). */
    public static long secondaryKey(long sortKey) {
        return (sortKey ^ Long.MIN_VALUE) & 0xFF_FFFFL;
    }

    /**
     * Sorts {@code order[0..size)} (item indices) ascending by (primary, secondary) rank.
     * The key arrays are indexed by item and are not modified. {@code scratchOrder} is a
     * workspace of at least {@code size} entries and {@code buckets} one of 256.
     */
    public static void sort(int[] order, long[] primaryKeys, long[] secondaryKeys, int size,
                            int[] scratchOrder, int[] buckets) {
        // The sequence fits in 24 bits, so three passes settle the secondary key; the
        // primary takes the full eight. Both counts are even/odd mixes, so each stage
        // tracks where its result landed and copies back when it ends in the scratch.
        int[] src = countingPasses(order, scratchOrder, secondaryKeys, size, 3, buckets);
        if (src != order) {
            System.arraycopy(src, 0, order, 0, size);
        }
        src = countingPasses(order, scratchOrder, primaryKeys, size, 8, buckets);
        if (src != order) {
            System.arraycopy(src, 0, order, 0, size);
        }
    }

    /**
     * Runs {@code passes} LSD counting passes over {@code keys[item]} starting from
     * {@code order} and ping-ponging with {@code scratch}; returns the array holding the
     * result (either {@code order} or {@code scratch}).
     */
    private static int[] countingPasses(int[] order, int[] scratch, long[] keys, int size,
                                        int passes, int[] buckets) {
        int[] src = order;
        int[] dst = scratch;
        for (int pass = 0; pass < passes; pass++) {
            int shift = pass * 8;
            java.util.Arrays.fill(buckets, 0);
            for (int i = 0; i < size; i++) {
                buckets[(int) ((keys[src[i]] >>> shift) & 0xFF)]++;
            }
            int sum = 0;
            for (int b = 0; b < 256; b++) {
                int count = buckets[b];
                buckets[b] = sum;
                sum += count;
            }
            for (int i = 0; i < size; i++) {
                int item = src[i];
                int bucket = (int) ((keys[item] >>> shift) & 0xFF);
                dst[buckets[bucket]++] = item;
            }
            int[] swap = src;
            src = dst;
            dst = swap;
        }
        return src;
    }
}
