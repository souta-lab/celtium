package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The R-15 sharing of the Tier-A resident buffers, pinned without a device.
 * <p>
 * Each per-pass engine used to keep a private copy of four buffers: the SectionDesc
 * table and the factor-base image (byte-identical duplicates of the one store image),
 * and the factor and sorted arenas (32 MiB each per pass, holding only that pass'
 * slice of the store's single cross-pass offset spaces). All four now live on the
 * host, created lazily at the same first touches, written at the store's own offsets,
 * and deleted only after every engine. Since T3 condition (2) the two images are
 * per frame slot and are written only through the host's frame snapshot: a slot's
 * snapshot is built once per recording from one atomic store capture, so a store
 * image that moves mid-recording never splits the recording's passes across two
 * images — the identity deciding that is a pure function and is driven directly
 * below (the snapshot contract itself is pinned in {@code FrameSnapshotContractTest}).
 * The device-bound halves cannot run on a
 * CPU-only instance, so the ownership shape is pinned at the source layer, the shape
 * {@code HostBufferGrowthRetentionTest} and {@code FrameTargetSlabHousingTest}
 * established.
 */
class TerrainEngineASharedBuffersTest {

    private static final String HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";
    private static final String ENGINE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java";
    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";

    @Test
    void theFourResidentBuffersAreHostFieldsNotEngineFields() throws IOException {
        String host = read(HOST);
        String engine = read(ENGINE);
        for (String field : new String[] {
                "private VulkanHostBuffer factorBuffer;",
                "private VulkanHostBuffer sortedBuffer;"}) {
            assertTrue(host.contains(field), "the host owns " + field);
            assertFalse(engine.contains(field), "no per-pass copy of " + field + " remains");
        }
        assertTrue(host.contains("private final VulkanHostBuffer[] descBuffers"),
                "the host owns the desc table, one copy per frame slot (T3 condition (2))");
        assertTrue(host.contains("private final VulkanHostBuffer[] factorBaseBuffers"),
                "the host owns the factor-base image, one copy per frame slot (T3 condition (2))");
        assertFalse(engine.contains("descBuffers"),
                "no engine names the slot image array: it reads its snapshot's handles");
        assertFalse(engine.contains("factorBaseBuffers"),
                "no engine names the slot image array: it reads its snapshot's handles");
        assertTrue(host.contains("slotUploadedGenerations"),
                "the per-slot upload generation tracking lives on the host");
        assertFalse(engine.contains("uploadedGeneration"),
                "no per-engine upload tracking remains");
    }

    @Test
    void theSnapshotLivesOnTheHostAlone() throws IOException {
        String host = read(HOST);
        String engine = read(ENGINE);
        assertTrue(host.contains(
                        "FrameSnapshot prepareSlotSnapshot(VulkanContext context, int slotIndex, long recordingEpoch)"),
                "the host carries the one snapshot builder");
        assertTrue(host.contains("this.store.captureSnapshot()"),
                "the snapshot is built from the store's one atomic capture");
        assertFalse(engine.contains("imageGeneration"),
                "no engine re-uploads the image into a private copy anymore");
        assertTrue(engine.contains(
                        "this.host.prepareSlotSnapshot(this.context, slotIndex, recordingEpoch)"),
                "the frame head reaches the snapshot through the host");
    }

    @Test
    void engineHandlesAndWritesGoThroughTheHost() throws IOException {
        String engine = read(ENGINE);
        assertTrue(engine.contains("this.currentSnapshot.descBufferHandle()"),
                "binding 6 reads the snapshot's desc table");
        assertTrue(engine.contains("this.currentSnapshot.factorBaseBufferHandle()"),
                "binding 7 reads the snapshot's factor-base image");
        assertTrue(engine.contains("return this.host.factorBuffer(this.context).buffer();"),
                "binding 8 reads the shared factor arena");
        assertTrue(engine.contains(
                "this.host.factorBuffer(this.context).mappedAddress() + registration.factor.byteOffset"),
                "factor bytes land in the shared arena at the store's offset");
        assertTrue(engine.contains(
                "this.host.sortedBuffer(this.context).mappedAddress() + registration.sorted.byteOffset"),
                "sorted bytes land in the shared arena at the store's offset");
        assertTrue(engine.contains("this.host.sortedBuffer(this.context).buffer()"),
                "the sorted draw binds the shared arena as its index buffer");
    }

    @Test
    void theHostDeletesTheSharedBuffersOnlyAfterEveryEngine() throws IOException {
        String host = read(HOST);
        String engine = read(ENGINE);
        int deleteStart = host.indexOf("boolean delete(VulkanContext context)");
        assertTrue(deleteStart >= 0, "the host's delete exists");
        String delete = host.substring(deleteStart);
        int enginesDeleted = delete.indexOf("engine.delete()");
        assertTrue(enginesDeleted >= 0, "the host deletes its engines");
        for (String bufferDelete : new String[] {
                "this.descBuffers[slot].delete()", "this.factorBaseBuffers[slot].delete()",
                "this.factorBuffer.delete()", "this.sortedBuffer.delete()",
                "geometrySlab.delete()"}) {
            assertTrue(delete.indexOf(bufferDelete) > enginesDeleted,
                    bufferDelete + " runs only after every engine is deleted");
            assertFalse(engine.contains(bufferDelete),
                    "no engine frees a shared buffer while a sibling pass still draws");
        }
    }

    @Test
    void slotUploadIsDueAtCreationAndNotWithoutAMove() {
        assertTrue(TerrainEngineAHost.slotUploadDue(false, 0L, -1L),
                "no buffer yet: the slot's first snapshot creates and uploads");
        assertFalse(TerrainEngineAHost.slotUploadDue(true, 5L, 5L),
                "the slot already holds the captured generation: nothing to upload");
        assertTrue(TerrainEngineAHost.slotUploadDue(true, 6L, 5L),
                "the capture is a generation ahead of the slot's copy: upload");
    }

    @Test
    void aSnapshotServesItsRecordingAndIsRebuiltForTheNext() {
        assertTrue(TerrainEngineAHost.snapshotCurrent(true, 3L, 7L, 3L, 7L),
                "same recording epoch, same frame stamp: the standing snapshot serves"
                        + " every pass of the recording");
        assertFalse(TerrainEngineAHost.snapshotCurrent(true, 3L, 7L, 4L, 7L),
                "the recording split and a new one began: a fresh snapshot is built,"
                        + " so no pass reads the discarded recording's image");
        assertFalse(TerrainEngineAHost.snapshotCurrent(true, 3L, 7L, 3L, 8L),
                "a new frame stamp: a fresh snapshot is built at the new frame's head");
    }

    @Test
    void theBackendStampsTheFrameOnTheHostAtThePassHead() throws IOException {
        assertTrue(read(BACKEND).contains("host.noteTierAFrameHead(this.frameId);"),
                "the pass head stamps the backend's frame id on the host");
        assertTrue(read(HOST).contains("void noteTierAFrameHead(long frameKey)"),
                "the host carries the frame stamp the snapshot identity keys on");
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
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
