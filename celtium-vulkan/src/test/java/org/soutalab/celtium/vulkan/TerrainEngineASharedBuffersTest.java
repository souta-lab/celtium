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
 * and deleted only after every engine. The device-bound halves cannot run on a
 * CPU-only instance, so the ownership shape is pinned at the source layer, the shape
 * {@code HostBufferGrowthRetentionTest} and {@code FrameTargetSlabHousingTest}
 * established.
 */
class TerrainEngineASharedBuffersTest {

    private static final String HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";
    private static final String ENGINE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java";

    @Test
    void theFourResidentBuffersAreHostFieldsNotEngineFields() throws IOException {
        String host = read(HOST);
        String engine = read(ENGINE);
        for (String field : new String[] {
                "private VulkanHostBuffer descBuffer;",
                "private VulkanHostBuffer factorBaseBuffer;",
                "private VulkanHostBuffer factorBuffer;",
                "private VulkanHostBuffer sortedBuffer;"}) {
            assertTrue(host.contains(field), "the host owns " + field);
            assertFalse(engine.contains(field), "no per-pass copy of " + field + " remains");
        }
        assertTrue(host.contains("private long uploadedDescGeneration"),
                "the one upload's generation tracking lives on the host");
        assertFalse(engine.contains("uploadedGeneration"),
                "no per-engine upload tracking remains");
    }

    @Test
    void theDescUploadLivesOnTheHostAlone() throws IOException {
        String host = read(HOST);
        String engine = read(ENGINE);
        assertTrue(host.contains("void uploadDescImageIfMoved(VulkanContext context)"),
                "the host carries the one desc/factor-base upload");
        assertTrue(host.contains("this.store.imageGeneration()"),
                "the upload keys on the store's image generation");
        assertFalse(engine.contains("imageGeneration"),
                "no engine re-uploads the image into a private copy anymore");
        assertTrue(engine.contains("this.host.uploadDescImageIfMoved(this.context)"),
                "the frame head reaches the shared upload through the host");
    }

    @Test
    void engineHandlesAndWritesGoThroughTheHost() throws IOException {
        String engine = read(ENGINE);
        assertTrue(engine.contains("return this.host.descBufferHandle(this.context);"),
                "binding 6 reads the shared desc table");
        assertTrue(engine.contains("return this.host.factorBaseBufferHandle(this.context);"),
                "binding 7 reads the shared factor-base image");
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
        int deleteStart = host.indexOf("void delete(VulkanContext context)");
        assertTrue(deleteStart >= 0, "the host's delete exists");
        String delete = host.substring(deleteStart);
        int enginesDeleted = delete.indexOf("engine.delete()");
        assertTrue(enginesDeleted >= 0, "the host deletes its engines");
        for (String bufferDelete : new String[] {
                "this.descBuffer.delete()", "this.factorBaseBuffer.delete()",
                "this.factorBuffer.delete()", "this.sortedBuffer.delete()"}) {
            assertTrue(delete.indexOf(bufferDelete) > enginesDeleted,
                    bufferDelete + " runs only after every engine is deleted");
            assertFalse(engine.contains(bufferDelete),
                    "no engine frees a shared buffer while a sibling pass still draws");
        }
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
