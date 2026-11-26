package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source contracts for the two halves of collect-before-grow that a device-less test cannot exercise
 * directly (BO convergence lever 2): the gated drain collects its own releases at its tail, so the
 * ranges it frees re-enter the ledgers in the same frame instead of at the next frame boundary's
 * head-of-drain collect; and the owning allocator wires both ledgers' growth points to the context's
 * slab completed value - the same currency the collect-on-allocate retry uses. A different or weaker
 * currency at the growth point would either stall reuse or, worse, collect ranges the GPU has not
 * finished with. The ledgers' growth-point behaviour itself is covered by
 * {@code SlabCollectBeforeGrowTest}.
 */
class SlabCollectBeforeGrowContractTest {

    /** The gated drain collects the releases it stacked, at its tail, gated on having freed anything. */
    @Test
    void theGatedDrainCollectsItsOwnReleasesAtItsTail() throws IOException {
        String source = readSource(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java"));
        int drainStart = source.indexOf("public int celtium$drainRetiredSlabBuffers()");
        assertTrue(drainStart >= 0, "the gated drain exists");
        int drainEnd = source.indexOf("public String celtium$slabDrainSummary()", drainStart);
        assertTrue(drainEnd > drainStart, "the gated drain's end is found");
        String drain = source.substring(drainStart, drainEnd);
        int prefixDrain = drain.indexOf("celtium$drainEligibleSlabPrefix");
        assertTrue(prefixDrain >= 0, "the drain runs the gated prefix rule");
        int tailCollect = drain.lastIndexOf("celtium$collectSlabReleases()");
        assertTrue(tailCollect > prefixDrain,
                "a collect runs after the prefix drain: the drain's own releases are collected at its tail");
        assertTrue(drain.substring(prefixDrain, tailCollect).contains("freed > 0"),
                "the tail collect is gated on the drain having freed something");
    }

    /** Both pools' growth points are wired to the context's slab completed value by the allocator. */
    @Test
    void theAllocatorWiresBothPoolsToTheSlabCompletedValue() throws IOException {
        String source = readSource(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/SlabAllocator.java"));
        assertTrue(source.contains(
                        "this.pool.setCompletedValueSource(() -> this.context.celtium$slabCompletedValue());"),
                "the ordinary pool's growth point collects against the slab completed value");
        assertTrue(source.contains(
                        "this.fixedPool.setCompletedValueSource(() -> this.context.celtium$slabCompletedValue());"),
                "the fixed pool's growth point collects against the slab completed value");
    }

    /** Source reads are line-ending normalised, so the contracts hold on a CRLF checkout as well. */
    private static String readSource(Path file) throws IOException {
        return Files.readString(file).replace("\r\n", "\n");
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
