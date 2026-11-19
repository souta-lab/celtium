package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 433: the arena fragmentation instrument (D6's missing meter).
 * <p>
 * The D6 record left the 40/13 MiB allocation-versus-use headroom as a monitoring item because no meter
 * separated the free space into holes stranded between live segments and the tail at the end of the
 * capacity. The instrument's core is a pure aggregation over a segment-list snapshot
 * ({@code VulkanArena.summarizeFragmentation}), because the arena itself cannot be constructed without a
 * device; these tests pin the split on synthetic snapshots shaped like the lists the arena produces
 * (offset order, contiguous, free ranges merged), the identity every reading must satisfy, and the census
 * line's format. The wiring - the arena walking its own list and the line riding the upload census - is
 * pinned as a source contract, and the first measured values are a fixture leg's to record.
 */
class VulkanArenaFragmentationTest {

    private static VulkanArena.Fragmentation summarize(int capacity, int[] offsets, int[] lengths,
            boolean[] free) {
        VulkanArena.Fragmentation reading =
                VulkanArena.summarizeFragmentation(capacity, offsets, lengths, free);
        assertEquals(capacity, reading.usedBytes + reading.holeBytes + reading.tailBytes,
                "every reading satisfies used + holes + tail == capacity");
        return reading;
    }

    @Test
    void noFreeSpaceAtAll() {
        VulkanArena.Fragmentation reading = summarize(100,
                new int[]{0, 60}, new int[]{60, 40}, new boolean[]{false, false});

        assertEquals(100L, reading.usedBytes);
        assertEquals(0, reading.holeCount);
        assertEquals(0L, reading.holeBytes);
        assertEquals(0L, reading.largestHoleBytes);
        assertEquals(0L, reading.tailBytes);
    }

    @Test
    void tailOnlyWhenTheLastRangeIsFreeAndReachesTheEnd() {
        VulkanArena.Fragmentation reading = summarize(100,
                new int[]{0, 60}, new int[]{60, 40}, new boolean[]{false, true});

        assertEquals(60L, reading.usedBytes);
        assertEquals(0, reading.holeCount);
        assertEquals(0L, reading.holeBytes);
        assertEquals(40L, reading.tailBytes, "the free range at the end is growth slack, not a hole");
    }

    @Test
    void holesOnlyWhenUsedSpaceReachesTheEnd() {
        VulkanArena.Fragmentation reading = summarize(100,
                new int[]{0, 30, 50}, new int[]{30, 20, 50}, new boolean[]{false, true, false});

        assertEquals(80L, reading.usedBytes);
        assertEquals(1, reading.holeCount);
        assertEquals(20L, reading.holeBytes);
        assertEquals(20L, reading.largestHoleBytes);
        assertEquals(0L, reading.tailBytes);
    }

    @Test
    void holesAndTailTogetherWithTheLargestHoleNamed() {
        VulkanArena.Fragmentation reading = summarize(100,
                new int[]{0, 10, 20, 40, 50, 80},
                new int[]{10, 10, 20, 10, 30, 20},
                new boolean[]{false, true, false, true, false, true});

        assertEquals(60L, reading.usedBytes);
        assertEquals(2, reading.holeCount);
        assertEquals(20L, reading.holeBytes);
        assertEquals(10L, reading.largestHoleBytes);
        assertEquals(20L, reading.tailBytes);
    }

    @Test
    void theLargestHoleIsTheMaximumNotTheFirst() {
        VulkanArena.Fragmentation reading = summarize(100,
                new int[]{0, 10, 35, 45, 70},
                new int[]{10, 25, 10, 25, 30},
                new boolean[]{false, true, false, true, false});

        assertEquals(2, reading.holeCount);
        assertEquals(50L, reading.holeBytes);
        assertEquals(25L, reading.largestHoleBytes);
        assertEquals(0L, reading.tailBytes);
    }

    @Test
    void aPristineArenaIsAllTail() {
        // A fresh arena is one free range over the whole capacity: nothing is stranded, everything is tail.
        VulkanArena.Fragmentation reading = summarize(1048576,
                new int[]{0}, new int[]{1048576}, new boolean[]{true});

        assertEquals(0L, reading.usedBytes);
        assertEquals(0, reading.holeCount);
        assertEquals(1048576L, reading.tailBytes);
    }

    @Test
    void justGrownShapeKeepsTheAppendedSlackInTheTail() {
        // Growth appends a free tail behind the live ranges; a hole left by an earlier free stays a hole.
        VulkanArena.Fragmentation reading = summarize(1048576,
                new int[]{0, 100000, 200000, 524288},
                new int[]{100000, 100000, 324288, 524288},
                new boolean[]{false, true, false, true});

        assertEquals(424288L, reading.usedBytes);
        assertEquals(1, reading.holeCount);
        assertEquals(100000L, reading.holeBytes);
        assertEquals(524288L, reading.tailBytes,
                "the appended half is tail: growth slack, not fragmentation");
    }

    @Test
    void aFreeLastRangeThatStopsShortOfTheEndIsAHole() {
        // The tail is defined by reaching the capacity's end; a snapshot whose last range is free but
        // stops short (not a shape the arena produces - its list always covers the capacity) counts that
        // range as a hole rather than inventing tail bytes beyond it.
        VulkanArena.Fragmentation reading = VulkanArena.summarizeFragmentation(100,
                new int[]{0, 60}, new int[]{60, 30}, new boolean[]{false, true});

        assertEquals(1, reading.holeCount);
        assertEquals(30L, reading.holeBytes);
        assertEquals(0L, reading.tailBytes);
    }

    @Test
    void anEmptySnapshotReadsAsAllZero() {
        VulkanArena.Fragmentation reading =
                VulkanArena.summarizeFragmentation(0, new int[0], new int[0], new boolean[0]);

        assertEquals(0L, reading.usedBytes);
        assertEquals(0, reading.holeCount);
        assertEquals(0L, reading.tailBytes);
    }

    @Test
    void raggedSnapshotArraysAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> VulkanArena.summarizeFragmentation(100,
                new int[]{0}, new int[]{10, 20}, new boolean[]{true}),
                "a snapshot whose arrays disagree names no ranges and must not be aggregated");
    }

    @Test
    void theCensusLineNamesEveryField() {
        VulkanArena.Fragmentation reading = summarize(100,
                new int[]{0, 10, 20, 40, 50, 80},
                new int[]{10, 10, 20, 10, 30, 20},
                new boolean[]{false, true, false, true, false, true});

        assertEquals("CELTIUM_ARENA_FRAGMENTATION capacity=100 used=60 holes=2 holeBytes=20 "
                + "largestHole=10 tailBytes=20", reading.line());
    }

    @Test
    void theCensusReportRidesTheUploadCensus() throws java.io.IOException {
        String source = java.nio.file.Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java"));
        int census = source.indexOf("private void reportUploadCensus()");
        assertTrue(census >= 0, "reportUploadCensus is declared");
        String body = source.substring(census, census + 2500);
        assertTrue(body.contains("CELTIUM_ARENA_UPLOAD"), "the upload line is the census this rides");
        assertTrue(body.contains("fragmentationLine()"),
                "the same report prints the fragmentation line, under the same census gate");
        int snapshot = source.indexOf("public Fragmentation fragmentation()");
        assertTrue(snapshot >= 0, "the arena exposes its current fragmentation");
        assertTrue(source.substring(snapshot, snapshot + 1600).contains("summarizeFragmentation("),
                "the arena's reading is the pure aggregation over its walked segment list");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static java.nio.file.Path findRepoFile(String repoRelative) {
        java.nio.file.Path direct = java.nio.file.Paths.get(repoRelative);
        if (java.nio.file.Files.exists(direct)) {
            return direct;
        }
        java.nio.file.Path fromModule = java.nio.file.Paths.get("..").resolve(repoRelative);
        if (java.nio.file.Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + java.nio.file.Paths.get("").toAbsolutePath());
    }
}
