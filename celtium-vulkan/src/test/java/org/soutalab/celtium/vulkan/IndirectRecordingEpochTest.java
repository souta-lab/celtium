package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3 condition (2), INV-2c: indirect streams are issuable only into the
 * recording that produced them. The backend counts recordings in a
 * monotonic epoch that advances at every recording boundary — a finish, a
 * fresh begin, and every reset or unwind that discards the standing
 * recording (the mid-frame split shapes: recovery, abort, abandoned
 * window, abandoned standalone clear) — and both Tier-A executors stamp
 * their slots with the arming recording's epoch and refuse draws under any
 * other. The gate decision is a pure function and is driven directly; the
 * boundary sites are pinned at the source layer.
 */
class IndirectRecordingEpochTest {

    private static final String ENGINE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java";
    private static final String PACK =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainPackEngine.java";
    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";

    @Test
    void drawsIssueOnlyIntoTheRecordingThatArmedThem() {
        assertTrue(TerrainEngineAHost.drawEpochCurrent(5L, 5L),
                "armed and drawn in the same recording: issue");
        assertFalse(TerrainEngineAHost.drawEpochCurrent(5L, 6L),
                "the recording split after the arming: the old streams must not issue");
        assertFalse(TerrainEngineAHost.drawEpochCurrent(6L, 5L),
                "an epoch from a later recording never matches an earlier draw either");
        assertFalse(TerrainEngineAHost.drawEpochCurrent(-1L, 0L),
                "a slot that never armed (the -1 stamp) issues nothing");
    }

    @Test
    void theEpochAdvancesAtEveryRecordingBoundary() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("private long recordingEpoch;"),
                "the backend owns the recording epoch");
        int advances = backend.split("this\\.recordingEpoch\\+\\+;", -1).length - 1;
        assertEquals(7, advances,
                "the epoch advances at the finish, the fresh begin, the recovery / abort /"
                        + " abandoned-window resets and the two abandoned-clear unwinds;"
                        + " a new recording boundary must advance it too, consciously");
        assertTrue(backend.contains("this.terrainAArmedEpoch != this.recordingEpoch"),
                "the pass head's armed cache is keyed by the epoch, so a split re-prepares"
                        + " instead of answering with the discarded recording's arming");
    }

    @Test
    void bothEnginesStampAndGateOnTheEpoch() throws IOException {
        for (String enginePath : new String[] {ENGINE, PACK}) {
            String engine = read(enginePath);
            assertTrue(engine.contains("slot.armedRecordingEpoch = recordingEpoch;"),
                    enginePath + " stamps the slot with the arming recording's epoch");
            assertTrue(engine.contains(
                            "TerrainEngineAHost.drawEpochCurrent(slot.armedRecordingEpoch, recordingEpoch)"),
                    enginePath + " refuses draws its slot's streams were not armed for");
            assertTrue(engine.contains("long recordingEpoch)"),
                    enginePath + " takes the recording epoch at prepare and draw time");
        }
        String backend = read(BACKEND);
        assertTrue(backend.contains("occlusionCamera.x, occlusionCamera.y, occlusionCamera.z, this.recordingEpoch);"),
                "the vanilla pass head hands the epoch to prepareFrame");
        assertTrue(backend.contains("tierAEngine.recordDraws(this.commandBuffer, this.frameSlot, this.recordingEpoch);"),
                "the vanilla draw site hands the epoch to recordDraws");
        assertTrue(backend.contains("packEngine.recordDraws(this.commandBuffer, this.frameSlot, this.recordingEpoch);"),
                "the pack draw site hands the epoch to recordDraws");
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
