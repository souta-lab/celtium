package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wait census printed at device teardown is the only outlet for the submit-path counters in
 * {@link VulkanContext}: they are counted rather than printed at the call site, because printing from the submit
 * path itself was suspected of perturbing a run (the counter's own javadoc records this). A counter that is
 * incremented but reaches no outlet is indistinguishable from a counter that was never wired: roam-hunt-13's
 * census (2026-11-22) found {@code submitWithSemaphoresCalls} in exactly that state - incremented on every
 * semaphore submit, read nowhere - while its siblings (bootstrap waits, submit timeouts) all appeared in the
 * teardown line. This pins the semaphore-submit count into that line, in the shape the pack-audit disposition
 * established for the same family (aggregate the counted-but-unread censuses into the existing diagnostics).
 */
class WaitCensusCoverageTest {

    @Test
    void semaphoreSubmitCountReachesTheTeardownWaitCensus() throws Exception {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java"));
        int census = source.indexOf("CELTIUM_WAIT_CENSUS");
        assertTrue(census >= 0, "the teardown wait census line exists");
        String block = source.substring(census, census + 1200);
        assertTrue(block.contains("\" submitWithSemaphoresCalls=\" + submitWithSemaphoresCalls"),
                "the wait census prints submitWithSemaphoresCalls next to its sibling counters");
        assertTrue(source.contains("submitWithSemaphoresCalls++;"),
                "the counter is still incremented in submitWithSemaphores");
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
