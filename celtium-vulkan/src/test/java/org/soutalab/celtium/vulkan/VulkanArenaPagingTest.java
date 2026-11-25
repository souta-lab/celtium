package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3R-2: the arena's paged growth. The behavioural contract the replacement has to keep is the old
 * growth contract minus the copy: existing segments keep their offset, their length and their bytes
 * when the arena grows. The arena cannot be constructed without a device, so the equivalence is
 * pinned at the level the implementation is built from - the page resolution
 * ({@link VulkanArena#pageIndexFor}) over bases accumulated from
 * {@link VulkanArena#pageCapacityBytes} - plus the source contracts that keep the copy path out,
 * and the per-page fragmentation readings' combination.
 */
class VulkanArenaPagingTest {

    @Test
    void anOffsetResolvesToTheLastPageWhoseBaseIsAtOrBelowIt() {
        int[] bases = {0, 100, 250};
        assertEquals(0, VulkanArena.pageIndexFor(bases, 3, 0));
        assertEquals(0, VulkanArena.pageIndexFor(bases, 3, 99));
        // Exactly at capacity: the first byte past page 0's end is page 1's first byte.
        assertEquals(1, VulkanArena.pageIndexFor(bases, 3, 100));
        assertEquals(1, VulkanArena.pageIndexFor(bases, 3, 249));
        assertEquals(2, VulkanArena.pageIndexFor(bases, 3, 250));
        assertEquals(2, VulkanArena.pageIndexFor(bases, 3, 1_000_000));
    }

    @Test
    void aSinglePageResolvesEveryOffsetToPageZero() {
        int[] bases = {0};
        assertEquals(0, VulkanArena.pageIndexFor(bases, 1, 0));
        assertEquals(0, VulkanArena.pageIndexFor(bases, 1, 67_108_863));
    }

    @Test
    void appendingPagesNeverMovesAnExistingOffsetToAnotherPage() {
        // The growth equivalence, stated on the resolution the draws use: bases are append-only,
        // so an offset's page - and with it the segment's buffer and page-local offset - is the
        // same before and after any number of page additions.
        int unit = 4096;
        int[] onePage = {0};
        int[] threePages = {0, 4096, 8192};
        for (int offset : new int[]{0, 1, 2048, 4095}) {
            assertEquals(VulkanArena.pageIndexFor(onePage, 1, offset),
                    VulkanArena.pageIndexFor(threePages, 3, offset),
                    "offset " + offset + " must stay on page 0 when pages are appended");
        }
        // And the bases themselves are the page capacities accumulated, each on the unit.
        long first = VulkanArena.pageCapacityBytes(unit, 0, 4);
        long second = VulkanArena.pageCapacityBytes(unit, 0, 4);
        assertEquals(4096L, first);
        assertEquals(first, (long) threePages[1]);
        assertEquals(first + second, (long) threePages[2]);
    }

    @Test
    void combinedFragmentationKeepsTheIdentityAcrossPages() {
        // Page A: 60 used, then a 40-byte free tail. Page B: a 30-byte hole, then 70 used.
        VulkanArena.Fragmentation a = VulkanArena.summarizeFragmentation(100,
                new int[]{0, 60}, new int[]{60, 40}, new boolean[]{false, true}, 2);
        VulkanArena.Fragmentation b = VulkanArena.summarizeFragmentation(100,
                new int[]{0, 30}, new int[]{30, 70}, new boolean[]{true, false}, 2);
        VulkanArena.Fragmentation combined = VulkanArena.Fragmentation.combine(a, b);
        assertEquals(200, combined.capacity);
        assertEquals(130L, combined.usedBytes);
        assertEquals(30L, combined.holeBytes);
        assertEquals(1, combined.holeCount);
        // The tails add (each page's end slack is growth slack of the same kind); the largest
        // free run does not - no run spans pages, so it is the larger page's run.
        assertEquals(40L, combined.tailBytes);
        assertEquals(40L, combined.largestFreeRunBytes);
        assertEquals(combined.capacity,
                combined.usedBytes + combined.holeBytes + combined.tailBytes,
                "used + holes + tail == capacity survives the combination");
    }

    @Test
    void theArenaHasNoGrowthCopyLeft() throws java.io.IOException {
        String source = java.nio.file.Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanArena.java"));
        // The one capacity call allowed to remain is the readback verify buffer's own growth;
        // a page buffer growing by capacity would be the copy path returning.
        assertTrue(source.contains("this.verifyBuffer.ensureCapacity"),
                "the readback verify buffer still grows by capacity");
        assertFalse(source.replace("this.verifyBuffer.ensureCapacity", "").contains("ensureCapacity"),
                "the verify buffer's call is the only ensureCapacity left in the arena");
        assertFalse(source.contains("private void grow("),
                "the doubling grow is deleted, not bypassed");
        assertTrue(source.contains("private Page addPage(int neededBytes)"),
                "page addition is the arena's one growth path");
        assertTrue(source.contains("public VulkanBuffer buffer()"),
                "a segment resolves its own page's buffer");
        assertTrue(source.contains("public int pageByteOffset()"),
                "a segment names its offset inside its page, which is what binds and copies use");
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
