package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3 condition (2): the frame snapshot's identity and the engines' read
 * discipline, pinned without a device. A slot's snapshot serves exactly one
 * recording — the same recording epoch under the same frame stamp — and
 * every count a dispatch or an arming reads comes from the snapshot the
 * host built for that recording, never from the live store (INV-2a). The
 * pack executor's private desc image is gone: one per-slot image on the
 * host serves every pass (the unification). The pure identity decisions are
 * driven directly; the device-bound wiring is pinned at the source layer,
 * the shape {@code TerrainEngineASharedBuffersTest} established.
 */
class FrameSnapshotContractTest {

    private static final String HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";
    private static final String ENGINE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java";
    private static final String PACK =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainPackEngine.java";

    @Test
    void aSnapshotServesOnlyItsOwnRecording() {
        assertTrue(TerrainEngineAHost.snapshotCurrent(true, 3L, 7L, 3L, 7L),
                "same epoch, same frame stamp: the standing snapshot serves");
        assertFalse(TerrainEngineAHost.snapshotCurrent(true, 3L, 7L, 4L, 7L),
                "a new recording (the epoch advanced) builds a fresh snapshot");
        assertFalse(TerrainEngineAHost.snapshotCurrent(true, 3L, 7L, 3L, 8L),
                "a new frame stamp builds a fresh snapshot");
        assertFalse(TerrainEngineAHost.snapshotCurrent(false, 3L, 7L, 3L, 7L),
                "no snapshot yet: the first prepare of the recording builds one");
    }

    @Test
    void aSlotUploadIsDueOnlyForAMissingOrStaleBuffer() {
        assertTrue(TerrainEngineAHost.slotUploadDue(false, 0L, -1L),
                "no buffer yet: the slot's first snapshot creates and uploads");
        assertFalse(TerrainEngineAHost.slotUploadDue(true, 5L, 5L),
                "the slot already holds the captured generation: no rewrite"
                        + " (a rewrite mid-recording is what INV-2b forbids)");
        assertTrue(TerrainEngineAHost.slotUploadDue(true, 6L, 5L),
                "the capture is a generation ahead of the slot's copy: upload at the prepare");
    }

    @Test
    void bothEnginesTakeTheSnapshotFromTheHost() throws IOException {
        String host = read(HOST);
        assertTrue(host.contains(
                        "FrameSnapshot prepareSlotSnapshot(VulkanContext context, int slotIndex, long recordingEpoch)"),
                "the host builds the per-slot snapshot");
        assertTrue(host.contains("this.store.captureSnapshot()"),
                "the snapshot is one atomic store capture");
        for (String enginePath : new String[] {ENGINE, PACK}) {
            String engine = read(enginePath);
            assertTrue(engine.contains(
                            "this.host.prepareSlotSnapshot(this.context, slotIndex, recordingEpoch)"),
                    enginePath + " takes its recording's snapshot from the host");
            assertFalse(engine.contains("captureSnapshot()"),
                    enginePath + " never captures the store itself: one builder, one identity");
        }
        String vanilla = read(ENGINE);
        assertTrue(vanilla.contains("int[] slabCounts = snapshot.vanillaSlabCounts(this.passOrdinal);"),
                "the vanilla arming reads the snapshot's frozen per-pass counts (M2)");
        String pack = read(PACK);
        assertTrue(pack.contains("snapshot.packSlabCounts(this.passOrdinal)"),
                "the pack dispatch set reads the snapshot's frozen per-pass pack counts");
    }

    @Test
    void thePackEngineKeepsNoPrivateDescImage() throws IOException {
        String pack = read(PACK);
        assertFalse(pack.contains("private VulkanHostBuffer descBuffer"),
                "the pack executor's private desc copy is gone (the unification)");
        assertFalse(pack.contains("uploadDescImageIfMoved"),
                "no per-executor desc upload remains");
        assertFalse(pack.contains("uploadedGeneration"),
                "no per-executor upload tracking remains");
        assertTrue(pack.contains("this.host.descBuffer(slot.index)"),
                "the pack cull names the host's per-slot desc buffer");
    }

    @Test
    void dispatchBodiesReadNoLiveStoreCounts() throws IOException {
        String cull = methodBody(read(ENGINE), "private void dispatchSlabCull(");
        assertTrue(cull.contains("snapshot.recordCount()"),
                "the vanilla dispatch walks the snapshot's table");
        assertFalse(cull.contains("store.recordCount()"),
                "the vanilla dispatch never sizes itself from the live store");
        assertFalse(cull.contains("slabRecordCounts"),
                "the vanilla dispatch never re-reads live counts");

        String packDispatch = methodBody(read(PACK), "private void dispatchSlab(");
        assertTrue(packDispatch.contains("snapshot.recordCount()"),
                "the pack dispatch walks the snapshot's table");
        assertFalse(packDispatch.contains("store.recordCount()"),
                "the pack dispatch never sizes itself from the live store");
        assertFalse(packDispatch.contains("packSlabRecordCounts"),
                "the pack dispatch never re-reads live counts");
    }

    /** The body of the method whose declaration starts with {@code signature}, braces balanced. */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "method exists: " + signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(start, i + 1);
            }
        }
        throw new IllegalStateException("unbalanced braces after " + signature);
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
