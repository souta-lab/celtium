package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slab retire drain's gate (R-52 remainder (1)): a retired slab buffer may only be destroyed once the
 * completed submission sequence has passed the sequence tagged at its retirement - the submission current
 * at retirement, and the one after it (the recording that could name the buffer), must both be confirmed.
 * The behaviour tests exercise the prefix rule itself without a device; the source contracts pin the two
 * properties whose violation reintroduces the measured failure or its mirror: the drain must actually be
 * driven from the frame boundary (its only other exit hangs off {@code waitForIdle()}, which no frame
 * boundary calls, and the list starved there for over 99 seconds in the measured leg), and the fallback
 * drain serial must advance only in the full queue-idle drain, because the direct releases keyed on it
 * rely on that contract.
 */
class SlabRetireDrainGateTest {

    private static VulkanContext.RetiredSlabBuffer entry(long handle, long sequence) {
        return new VulkanContext.RetiredSlabBuffer(handle, null, sequence, 0L);
    }

    @Test
    void anEmptyListDrainsNothing() {
        List<VulkanContext.RetiredSlabBuffer> entries = new ArrayList<VulkanContext.RetiredSlabBuffer>();
        List<Long> destroyed = new ArrayList<Long>();
        int drained = VulkanContext.celtium$drainEligibleSlabPrefix(entries, 10L,
                e -> destroyed.add(Long.valueOf(e.handle)));
        assertEquals(0, drained);
        assertTrue(destroyed.isEmpty());
        assertTrue(entries.isEmpty());
    }

    @Test
    void anEntryTaggedWithTheCompletedSequenceWaitsForOneMore() {
        List<VulkanContext.RetiredSlabBuffer> entries = new ArrayList<VulkanContext.RetiredSlabBuffer>();
        entries.add(entry(1L, 5L));
        int drained = VulkanContext.celtium$drainEligibleSlabPrefix(entries, 5L, e -> {
        });
        assertEquals(0, drained, "tag == completed: the naming submission is not confirmed yet");
        assertEquals(1, entries.size());
    }

    @Test
    void anEntryBelowTheCompletedSequenceIsDestroyed() {
        List<VulkanContext.RetiredSlabBuffer> entries = new ArrayList<VulkanContext.RetiredSlabBuffer>();
        entries.add(entry(1L, 4L));
        entries.add(entry(2L, 5L));
        List<Long> destroyed = new ArrayList<Long>();
        int drained = VulkanContext.celtium$drainEligibleSlabPrefix(entries, 6L,
                e -> destroyed.add(Long.valueOf(e.handle)));
        assertEquals(2, drained);
        assertEquals(java.util.Arrays.asList(Long.valueOf(1L), Long.valueOf(2L)), destroyed);
        assertTrue(entries.isEmpty());
    }

    @Test
    void thePrefixStopsAtTheFirstIneligibleEntry() {
        List<VulkanContext.RetiredSlabBuffer> entries = new ArrayList<VulkanContext.RetiredSlabBuffer>();
        entries.add(entry(1L, 1L));
        entries.add(entry(2L, 9L));
        entries.add(entry(3L, 2L));
        List<Long> destroyed = new ArrayList<Long>();
        int drained = VulkanContext.celtium$drainEligibleSlabPrefix(entries, 3L,
                e -> destroyed.add(Long.valueOf(e.handle)));
        assertEquals(1, drained, "the drain must not scan past an ineligible head");
        assertEquals(java.util.Arrays.asList(Long.valueOf(1L)), destroyed);
        assertEquals(2, entries.size());
        assertEquals(2L, entries.get(0).handle);
    }

    @Test
    void theRetireTagClampNeverLowersATag() {
        assertEquals(7L, VulkanContext.celtium$clampRetireSequence(7L, 3L));
        assertEquals(9L, VulkanContext.celtium$clampRetireSequence(7L, 9L));
        assertEquals(0L, VulkanContext.celtium$clampRetireSequence(0L, 0L));
    }

    /** The gated drain is driven from the frame boundary, beside the retired-cleanup drain. */
    @Test
    void theGatedDrainIsDrivenFromTheFrameBoundary() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertTrue(source.contains("this.context.celtium$drainRetiredSlabBuffers();"),
                "the frame boundary drives the gated slab drain");
        assertEquals(1, countOccurrences(source, "celtium$drainRetiredSlabBuffers()"),
                "exactly one drive point: the frame boundary");
    }

    /** The fallback serial's only advance stays in the full queue-idle drain. */
    @Test
    void theDrainSerialAdvancesOnlyInTheFullDrain() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java"));
        assertEquals(1, countOccurrences(source, "this.slabReleaseSerial++"),
                "slabReleaseSerial advances only in celtium$destroyRetiredBuffers");
        int drainStart = source.indexOf("public int celtium$drainRetiredSlabBuffers()");
        assertTrue(drainStart >= 0, "the gated drain exists");
        int drainEnd = source.indexOf("public String celtium$slabDrainSummary()", drainStart);
        assertTrue(drainEnd > drainStart, "the gated drain's end is found");
        String drain = source.substring(drainStart, drainEnd);
        assertTrue(drain.contains("celtium$slabCompletedValue()"),
                "gate-passed releases are keyed on the already-completed value");
        assertTrue(!drain.contains("slabReleaseSerial"),
                "the gated drain never touches the drain serial");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
