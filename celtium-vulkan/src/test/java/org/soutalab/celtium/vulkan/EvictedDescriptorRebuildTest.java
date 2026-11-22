package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The draw-side eviction guard in {@code drawPendingImmediate}, pinned as a source contract because the
 * failure needs a device-bound allocation sequence to reproduce at runtime.
 * <p>
 * The descriptor rebuild guard keyed on {@code prepared.textureKey} cannot see a set that died with its key
 * unchanged: misses on other textures evict the entry, the set is retired, and the completion-gated free
 * (audit D2) returns it to its pool without counting a still-queued group's unrecorded use, so the flush
 * binds a freed set and the driver faults inside {@code vkCmdBindDescriptorSets} - the flush-boundary crash
 * a reporter's pack reproduces through Xaero's raw framebuffer registration. These assertions pin the three
 * pieces of the repair: the pipeline answers whether a set is still the cached one for its key from the
 * cache map itself, the draw loop computes the eviction case beside the moved-key case and admits it to the
 * same rebuild branch, and a rebuild on that path is counted separately.
 */
class EvictedDescriptorRebuildTest {

    private static String texturePipelineSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java"));
    }

    private static String backendSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
    }

    @Test
    void stillCachedIsAnsweredFromTheCacheMap() throws IOException {
        String source = texturePipelineSource();
        int method = source.indexOf("public boolean celtium$setStillCached(long set, long key)");
        assertTrue(method >= 0, "the pipeline exposes the still-cached query");
        int body = source.indexOf("return set != 0L && this.setsByTextureView.get(key) == set;", method);
        assertTrue(body > method, "the query compares the set against the cache map's entry for the key");
    }

    @Test
    void drawLoopAdmitsTheEvictedCaseToTheRebuildBranch() throws IOException {
        String source = backendSource();
        int evicted = source.indexOf("boolean evicted = !forced && currentTexture != null");
        assertTrue(evicted >= 0, "the draw loop computes the eviction case");
        int query = source.indexOf(
                "!evictionPipeline.celtium$setStillCached(prepared.descriptor, prepared.textureKey)", evicted);
        assertTrue(query > evicted, "the eviction case consults the still-cached query with the prepared set and key");
        int branch = source.indexOf(
                "if (currentTexture != null && (forced || evicted || currentTexture.key() != prepared.textureKey))",
                query);
        assertTrue(branch > query, "the eviction case is admitted to the rebuild branch beside the moved-key case");
    }

    @Test
    void anEvictedRebuildIsCountedSeparately() throws IOException {
        String source = backendSource();
        int rebuilt = source.indexOf("if (rebuilt != 0L) {");
        assertTrue(rebuilt >= 0, "the rebuild success block is present");
        int counted = source.indexOf("this.immediateStatistics.noteEvictedSetDescriptorsRebuilt();", rebuilt);
        assertTrue(counted > rebuilt, "a successful rebuild on the eviction path raises the dedicated counter");
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
