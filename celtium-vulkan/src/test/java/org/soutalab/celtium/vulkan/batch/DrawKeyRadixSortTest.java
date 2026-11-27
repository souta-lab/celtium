package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawKeyRadixSortTest {

    /** The pre-S3-a merge sort's rank, reimplemented as the reference order. */
    private static int referenceRank(DrawItemStore items, int a, int b) {
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

    @Test
    void radixOrderMatchesTheReferenceRankOnRandomFrames() {
        Random random = new Random(0xCE17_1A2B_3C4DL);
        for (int trial = 0; trial < 40; trial++) {
            int size = 1 + random.nextInt(300);
            DrawItemStore items = new DrawItemStore(size);
            long[] primary = new long[size];
            long[] secondary = new long[size];
            for (int i = 0; i < size; i++) {
                int pass = random.nextInt(6);
                int pipeline = random.nextInt(40);
                int state = random.nextInt(4);
                int layer = random.nextInt(3);
                boolean ordered = random.nextInt(4) == 0;
                long key = DrawSortKey.encode(pass, pipeline, state, layer, i);
                items.append(key, 0, 0, 1, 0, 0, ordered ? DrawItemStore.FLAG_ORDERED : 0);
                primary[i] = DrawKeyRadixSort.primaryKey(key, ordered);
                secondary[i] = DrawKeyRadixSort.secondaryKey(key);
            }
            Integer[] reference = new Integer[size];
            int[] order = new int[size];
            for (int i = 0; i < size; i++) {
                reference[i] = i;
                order[i] = i;
            }
            // The reference rank is a total order (the sequence field is the item index),
            // so the comparison sort's stability is not what is being compared - the
            // order itself is.
            Arrays.sort(reference, (a, b) -> referenceRank(items, a, b));
            DrawKeyRadixSort.sort(order, primary, secondary, size,
                    new int[size], new int[256]);
            int[] expected = new int[size];
            for (int i = 0; i < size; i++) {
                expected[i] = reference[i];
            }
            assertArrayEquals(expected, order, "trial " + trial + " size " + size);
        }
    }

    @Test
    void orderedPrimaryKeyIgnoresPipelineStateAndLayer() {
        long key = DrawSortKey.encode(2, 0x1234, 0x56, 7, 99);
        long orderedPrimary = DrawKeyRadixSort.primaryKey(key, true);
        long otherOrdered = DrawKeyRadixSort.primaryKey(
                DrawSortKey.encode(2, 0x7FFF, 0xFF, 0xFF, 3), true);
        assertEquals(orderedPrimary, otherOrdered);
        assertEquals(99, DrawKeyRadixSort.secondaryKey(key));
    }

    @Test
    void opaquePrimaryKeyKeepsPipelineStateLayerOrder() {
        long low = DrawKeyRadixSort.primaryKey(DrawSortKey.encode(1, 10, 0, 0, 0), false);
        long byPipeline = DrawKeyRadixSort.primaryKey(DrawSortKey.encode(1, 11, 0, 0, 0), false);
        long byState = DrawKeyRadixSort.primaryKey(DrawSortKey.encode(1, 10, 1, 0, 0), false);
        long byLayer = DrawKeyRadixSort.primaryKey(DrawSortKey.encode(1, 10, 0, 1, 0), false);
        assertTrue(Long.compareUnsigned(low, byPipeline) < 0);
        assertTrue(Long.compareUnsigned(low, byState) < 0);
        assertTrue(Long.compareUnsigned(low, byLayer) < 0);
        assertTrue(Long.compareUnsigned(byState, byPipeline) < 0);
        assertTrue(Long.compareUnsigned(byLayer, byState) < 0);
    }
}
