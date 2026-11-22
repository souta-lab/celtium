package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 675 candidate 0, the instrument-first step: the same-JVM re-entry stall's second phase sat inside
 * {@code VulkanContext.freeMemory} during the head of {@code celtium$deleteSettled()}, after the device had
 * already been settled idle, and nothing in the log could say which segment stalled, which free was slow, or
 * how large it was. These tests pin the two instruments that separate that on the next leg: the slow-free
 * record in {@code freeMemory} and the per-segment brackets over the teardown head.
 * <p>
 * What this proves: the wiring's shape - the timing wraps the native call, the print is threshold-gated, the
 * brackets close in dependency order. It does not prove a driver stalls a free; that is the reproduction
 * leg's measurement (procedure fixed in the item 675 record).
 */
class ReentryStallInstrumentTest {

    /** freeMemory times the native call and records only a slow one, with the freed size and the slow totals. */
    @Test
    void freeMemoryRecordsOnlySlowCallsWithSizeAndTotals() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java"));
        int free = source.indexOf("public static void freeMemory(");
        assertTrue(free >= 0, "freeMemory is declared");
        String body = source.substring(free, free + 2600);
        int remove = body.indexOf("LIVE_ALLOCATIONS.remove(");
        int timed = body.indexOf("vkFreeMemory(device, memory, null);");
        int gate = body.indexOf("if (took > SLOW_FREE_MEMORY_NANOS) {");
        assertTrue(remove >= 0 && remove < timed,
                "the allocation is still untracked before the native free, as before the instrument");
        assertTrue(timed >= 0 && gate > timed, "the slow gate reads the span the native call took");
        assertTrue(body.contains("CELTIUM_FREE_MEMORY_SLOW"), "the slow record carries its marker");
        assertTrue(body.contains("\" size=\""), "the record names the freed allocation's size");
        assertTrue(body.contains("slowCalls=") && body.contains("slowTotalMillis=")
                        && body.contains("slowWorstMillis="),
                "the record carries the cumulative slow count, total and worst");
        assertTrue(source.contains("SLOW_FREE_MEMORY_NANOS = 50_000_000L"),
                "the threshold is the frame boundary's 50 ms slow threshold");
    }

    /** The teardown head is bracketed segment by segment, in the sequence's own dependency order. */
    @Test
    void deleteSettledHeadIsBracketedBySegment() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int settled = source.indexOf("private void celtium$deleteSettled() {");
        assertTrue(settled >= 0, "the release sequence is declared");
        String head = source.substring(settled, settled + 9000);
        int indirect = head.indexOf("celtium$deleteSegment(\"indirect\"");
        int drawRecords = head.indexOf("celtium$deleteSegment(\"drawRecords\"");
        int modelInstances = head.indexOf("celtium$deleteSegment(\"modelInstances\"");
        int nativePass = head.indexOf("celtium$deleteSegment(\"nativePass\"");
        int textureArray = head.indexOf("celtium$deleteSegment(\"textureArray\"");
        assertTrue(indirect >= 0 && indirect < drawRecords && drawRecords < modelInstances
                        && modelInstances < nativePass && nativePass < textureArray,
                "the head's brackets close in order: indirect, drawRecords, modelInstances, nativePass, textureArray");
        int windowExit = head.indexOf("if (this.windowOwned) {");
        int releaseWorld = head.indexOf("celtium$deleteSegment(\"releaseWorldResources\"");
        assertTrue(windowExit >= 0 && releaseWorld > windowExit,
                "the window-owned exit's world release is bracketed inside its branch");
        assertTrue(source.contains("CELTIUM_DELETE_SETTLED_SLOW"),
                "a slow segment names itself under its marker");
        assertTrue(source.contains("took > 50_000_000L"),
                "the bracket threshold is the frame boundary's 50 ms slow threshold");
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
