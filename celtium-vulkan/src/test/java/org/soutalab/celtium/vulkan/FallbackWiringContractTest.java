package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contracts for the Stage 2 fallback-counter wiring (residuals R-09): the timeline-wait and
 * ring-reservation decision points count their attempts and verdicts in {@link FallbackPaths}.
 * Both decision points live inside device-bound methods - a slot wait that blocks on a real
 * timeline semaphore, a ring reservation against a real {@code VkBuffer} - so they cannot be
 * driven on a CPU-only instance; the wiring is pinned by source contracts in the shape
 * {@code LandedFixGuardContractTest} established, and the two new standard path names are
 * pinned behaviourally through the registry itself.
 */
class FallbackWiringContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String FRAME_ARENA =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java";

    @BeforeEach
    void clearRegistry() {
        FallbackPaths.clearForTests();
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }

    private static int count(String text, String needle) {
        int found = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    @Test
    void stage2PathNamesAreTheRegisteredStandardNames() {
        assertEquals("sync.timeline", FallbackPaths.SYNC_TIMELINE);
        assertEquals("ring.alloc", FallbackPaths.RING_ALLOC);

        FallbackPaths.Handle timeline = FallbackPaths.register(FallbackPaths.SYNC_TIMELINE);
        timeline.attempt();
        timeline.fast();
        FallbackPaths.Handle ring = FallbackPaths.register(FallbackPaths.RING_ALLOC);
        ring.attempt();
        ring.fallback();

        assertEquals(1L, timeline.attempts());
        assertEquals(1L, timeline.fastHits());
        assertEquals(0L, timeline.fallbacks());
        assertEquals(1L, ring.attempts());
        assertEquals(0L, ring.fastHits());
        assertEquals(1L, ring.fallbacks());

        String summary = FallbackPaths.summary();
        assertTrue(summary.contains("sync.timeline:attempts=1,fast=1,fallback=0,fastRate=1.000"),
                summary);
        assertTrue(summary.contains("ring.alloc:attempts=1,fast=0,fallback=1,fastRate=0.000"),
                summary);
    }

    /**
     * The slot wait is the one place the timeline can fail over to a fence wait (the only
     * {@code awaitValue} caller in the tree). The attempt must be counted only once the method
     * is committed to waiting - after the nothing-in-flight early return - and the attempt must
     * end in exactly one verdict: fast inside the timeline branch, fallback where the fence
     * wait takes over.
     */
    @Test
    void slotWaitCountsOneAttemptAndExactlyOneVerdict() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("FallbackPaths.register(FallbackPaths.SYNC_TIMELINE)"),
                "the backend holds the sync.timeline handle");

        int start = backend.indexOf("private boolean waitForSlot(int slot)");
        assertTrue(start >= 0, "waitForSlot is declared");
        int end = backend.length();
        for (String next : new String[] {"\n    private ", "\n    public ", "\n    protected "}) {
            int candidate = backend.indexOf(next, start + 1);
            if (candidate >= 0 && candidate < end) {
                end = candidate;
            }
        }
        String method = backend.substring(start, end);

        int earlyReturn = method.indexOf("return true;");
        int attempt = method.indexOf("TIMELINE_WAIT_PATH.attempt()");
        int await = method.indexOf("awaitValue(");
        int fast = method.indexOf("TIMELINE_WAIT_PATH.fast()");
        int fallback = method.indexOf("TIMELINE_WAIT_PATH.fallback()");
        assertEquals(1, count(method, "TIMELINE_WAIT_PATH.attempt()"), method);
        assertEquals(1, count(method, "TIMELINE_WAIT_PATH.fast()"), method);
        assertEquals(1, count(method, "TIMELINE_WAIT_PATH.fallback()"), method);
        assertTrue(earlyReturn >= 0 && attempt > earlyReturn,
                "the attempt is counted after the nothing-in-flight early return");
        assertTrue(attempt < await && await < fast && fast < fallback,
                "attempt, timeline wait, fast verdict and fence-wait verdict stay in that order");
    }

    /**
     * A ring region reserves its vertex and index slices lazily, one {@code alloc} per stream;
     * a {@code -1} answer is the ring's exhaustion signal and the mesh promotes to buffers it
     * owns. Both reservation sites must count an attempt and verdict the reservation on the
     * sign of the returned offset.
     */
    @Test
    void ringReservationsCountAttemptsAndVerdictsAtBothSites() throws IOException {
        String arena = read(FRAME_ARENA);
        assertTrue(arena.contains("FallbackPaths.register(FallbackPaths.RING_ALLOC)"),
                "the frame arena holds the ring.alloc handle");

        int start = arena.indexOf("class RingRegion");
        assertTrue(start >= 0, "RingRegion is declared");
        int end = arena.indexOf("private static final class Slot", start);
        assertTrue(end > start, "RingRegion ends before the arena Slot class");
        String region = arena.substring(start, end);

        assertEquals(2, count(region, "this.ring.alloc(this.ringSlot"),
                "exactly the vertex and index reservation sites allocate from the ring");
        assertEquals(2, count(region, "RING_ALLOC_PATH.attempt()"), region);
        assertEquals(2, count(region, "RING_ALLOC_PATH.verdict(offset >= 0L)"), region);

        int firstAttempt = region.indexOf("RING_ALLOC_PATH.attempt()");
        int firstAlloc = region.indexOf("this.ring.alloc(this.ringSlot");
        int firstVerdict = region.indexOf("RING_ALLOC_PATH.verdict(offset >= 0L)");
        int secondAttempt = region.indexOf("RING_ALLOC_PATH.attempt()", firstAttempt + 1);
        int secondAlloc = region.indexOf("this.ring.alloc(this.ringSlot", firstAlloc + 1);
        int secondVerdict = region.indexOf("RING_ALLOC_PATH.verdict(offset >= 0L)", firstVerdict + 1);
        assertTrue(firstAttempt < firstAlloc && firstAlloc < firstVerdict
                        && firstVerdict < secondAttempt && secondAttempt < secondAlloc
                        && secondAlloc < secondVerdict,
                "each reservation is counted as attempt, alloc, verdict in that order");
    }
}
