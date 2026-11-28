package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aggregation behaviour of the per-level mip write census (R-104). The census is process-wide
 * static state, so every assertion here is a delta against the value read before the notes:
 * what a note adds, per level, is the contract - not the totals a whole test JVM happens to
 * accumulate.
 */
class MipLevelWriteCensusTest {

    @Test
    void noteWriteAccumulatesCountsAndBytesPerLevel() {
        long countsBefore = MipLevelWriteCensus.writeCount(2);
        long bytesBefore = MipLevelWriteCensus.writeBytes(2);
        MipLevelWriteCensus.noteWrite(2, 100L);
        MipLevelWriteCensus.noteWrite(2, 250L);
        assertEquals(countsBefore + 2, MipLevelWriteCensus.writeCount(2), "each note is one write");
        assertEquals(bytesBefore + 350L, MipLevelWriteCensus.writeBytes(2), "bytes accumulate per write");
    }

    @Test
    void noteWriteKeepsLevelsIndependent() {
        long otherCounts = MipLevelWriteCensus.writeCount(4);
        long otherBytes = MipLevelWriteCensus.writeBytes(4);
        MipLevelWriteCensus.noteWrite(3, 64L);
        assertEquals(otherCounts, MipLevelWriteCensus.writeCount(4), "a write to level 3 is not a write to level 4");
        assertEquals(otherBytes, MipLevelWriteCensus.writeBytes(4), "bytes stay with the level they were written to");
    }

    @Test
    void noteWholeImageCountsTheCopyAndEveryRegeneratedLevel() {
        long[] countsBefore = new long[11];
        long[] bytesBefore = new long[11];
        for (int level = 0; level <= 10; level++) {
            countsBefore[level] = MipLevelWriteCensus.writeCount(level);
            bytesBefore[level] = MipLevelWriteCensus.writeBytes(level);
        }
        // A 512x512 whole upload with a ten-level chain, four bytes per texel.
        MipLevelWriteCensus.noteWholeImage(512, 512, 10, 4, 512L * 512L * 4L);
        for (int level = 0; level < 10; level++) {
            assertEquals(countsBefore[level] + 1, MipLevelWriteCensus.writeCount(level),
                    "level " + level + " was written once: level 0 by the copy, the rest by the chain");
        }
        assertEquals(bytesBefore[0] + 512L * 512L * 4L, MipLevelWriteCensus.writeBytes(0),
                "level 0 accounts the copied bytes");
        assertEquals(bytesBefore[1] + 256L * 256L * 4L, MipLevelWriteCensus.writeBytes(1),
                "a regenerated level accounts its full byte size");
        assertEquals(bytesBefore[9] + 4L, MipLevelWriteCensus.writeBytes(9),
                "the chain's last level is one texel");
        assertEquals(countsBefore[10], MipLevelWriteCensus.writeCount(10),
                "a level outside the upload's chain is not counted");
        assertEquals(bytesBefore[10], MipLevelWriteCensus.writeBytes(10),
                "a level outside the upload's chain gains no bytes");
    }

    @Test
    void noteWholeImageClampsShrunkenExtentsAtOneTexel() {
        long level1Bytes = MipLevelWriteCensus.writeBytes(1);
        long level2Bytes = MipLevelWriteCensus.writeBytes(2);
        // 3x1: halving reaches zero on both axes by level 2, and the mirror's own extents clamp
        // at one texel rather than vanishing.
        MipLevelWriteCensus.noteWholeImage(3, 1, 3, 4, 12L);
        assertEquals(level1Bytes + 4L, MipLevelWriteCensus.writeBytes(1),
                "level 1 is max(1, 3>>1) x max(1, 1>>1) texels");
        assertEquals(level2Bytes + 4L, MipLevelWriteCensus.writeBytes(2),
                "level 2 clamps to a single texel instead of zero bytes");
    }

    @Test
    void writesAboveTheCapAreBucketedNotDropped() {
        long writesBefore = MipLevelWriteCensus.aboveCapWrites();
        long bytesBefore = MipLevelWriteCensus.aboveCapBytes();
        MipLevelWriteCensus.noteWrite(MipLevelWriteCensus.MAX_LEVELS, 100L);
        MipLevelWriteCensus.noteWrite(MipLevelWriteCensus.MAX_LEVELS + 15, 50L);
        assertEquals(writesBefore + 2, MipLevelWriteCensus.aboveCapWrites(),
                "a write past the table still counts, in the bucket");
        assertEquals(bytesBefore + 150L, MipLevelWriteCensus.aboveCapBytes(),
                "its bytes are bucketed with it");
        assertEquals(0L, MipLevelWriteCensus.writeCount(MipLevelWriteCensus.MAX_LEVELS),
                "the table itself has no slot past the cap to read back");
    }

    @Test
    void summaryNamesCountAndBytesPerWrittenLevelAndOmitsUnwrittenOnes() {
        MipLevelWriteCensus.noteWrite(7, 424242L);
        String expectedToken = "7:" + MipLevelWriteCensus.writeCount(7) + "/" + MipLevelWriteCensus.writeBytes(7);
        String[] tokens = MipLevelWriteCensus.summary().split(" ");
        assertTrue(Arrays.asList(tokens).contains(expectedToken),
                "the summary carries level:count/bytes for a written level: " + MipLevelWriteCensus.summary());
        if (MipLevelWriteCensus.writeCount(15) == 0L) {
            assertFalse(Arrays.stream(tokens).anyMatch(token -> token.startsWith("15:")),
                    "a level nobody wrote is omitted, so a patch-only chain reads as stopping early");
        }
    }
}
