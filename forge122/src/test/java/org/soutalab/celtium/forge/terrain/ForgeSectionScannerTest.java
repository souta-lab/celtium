package org.soutalab.celtium.forge.terrain;

import net.minecraft.world.chunk.NibbleArray;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scanner's raw-byte light read (S2-b-perf CPU-3): {@link ForgeSectionScanner#lightNibble}
 * must be {@link NibbleArray#get} under another name, for every cell of a storage. The
 * chunk-level branches around it (a null sky array answers 0, replicating
 * {@code Chunk.getLightFor}'s provider rule) are one-line mappings verified against the
 * decompiled sources; a full {@code Chunk} cannot be constructed without a world, so the
 * arithmetic itself - the part that can silently flip nibbles - is what is pinned here.
 */
class ForgeSectionScannerTest {

    @Test
    void rawNibbleReadMatchesNibbleArrayGetEverywhere() {
        Random random = new Random(0xCE1710L ^ 0x5EEDL);
        for (int trial = 0; trial < 4; trial++) {
            byte[] data = new byte[2048];
            random.nextBytes(data);
            NibbleArray array = new NibbleArray(data);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        assertEquals(array.get(x, y, z),
                                ForgeSectionScanner.lightNibble(data, x, y, z),
                                "cell (" + x + ", " + y + ", " + z + ")");
                    }
                }
            }
        }
    }

    @Test
    void rawNibbleReadMatchesOnStructuredPatterns() {
        // All-low, all-high and alternating nibbles: the patterns a hand-rolled reader
        // most easily swaps.
        for (byte fill : new byte[]{0x00, (byte) 0xFF, 0x0F, (byte) 0xF0, 0x55, (byte) 0xAA}) {
            byte[] data = new byte[2048];
            java.util.Arrays.fill(data, fill);
            NibbleArray array = new NibbleArray(data);
            for (int y = 0; y < 16; y += 5) {
                for (int z = 0; z < 16; z += 3) {
                    for (int x = 0; x < 16; x++) {
                        assertEquals(array.get(x, y, z),
                                ForgeSectionScanner.lightNibble(data, x, y, z));
                    }
                }
            }
        }
    }
}
