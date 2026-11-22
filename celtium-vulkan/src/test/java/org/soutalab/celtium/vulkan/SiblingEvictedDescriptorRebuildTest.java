package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sibling families' half of the draw-side eviction guard, pinned as a source contract beside
 * {@link EvictedDescriptorRebuildTest} for the same reason: the failure needs a device-bound allocation
 * sequence to reproduce at runtime.
 * <p>
 * The item, block and particle pipelines keep the textured family's cache shape - a bounded
 * {@code setsByTextureView} map, eldest eviction on a miss, retirement into the completion-gated free that
 * counts recorded uses only - but the landed guard asked the question only of
 * {@code VulkanImmediateTexturePipeline}, so a queued sibling group whose set was evicted with its texture
 * key unchanged still bound the freed set. These assertions pin the repair's pieces: each sibling pipeline
 * answers the still-cached query from its own cache map, the draw loop admits the sibling case to the same
 * {@code evicted} term (with the pair key those caches are keyed by, recomputed from the wrappers current
 * at the draw), and the rebuild branch can actually rebuild a block or particle group instead of falling
 * through to the refusal it used for every non-texture family.
 */
class SiblingEvictedDescriptorRebuildTest {

    private static String source(String file) throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/" + file));
    }

    private static String backendSource() throws IOException {
        return source("VulkanTerrainGpuBackend.java");
    }

    @Test
    void eachSiblingPipelineAnswersStillCachedFromItsOwnCacheMap() throws IOException {
        String[] pipelines = {
                "VulkanImmediateItemPipeline.java",
                "VulkanImmediateBlockPipeline.java",
                "VulkanImmediateParticlePipeline.java",
        };
        for (String pipeline : pipelines) {
            String source = source(pipeline);
            int method = source.indexOf("public boolean celtium$setStillCached(long set, long key)");
            assertTrue(method >= 0, pipeline + " exposes the still-cached query");
            int body = source.indexOf("return set != 0L && this.setsByTextureView.get(key) == set;", method);
            assertTrue(body > method,
                    pipeline + " answers the query by comparing the set against its own cache map's entry");
        }
    }

    @Test
    void drawLoopAdmitsTheSiblingCaseToTheSameEvictedTerm() throws IOException {
        String source = backendSource();
        int evicted = source.indexOf("boolean evicted = !forced && currentTexture != null");
        assertTrue(evicted >= 0, "the draw loop computes the eviction case");
        int textureQuery = source.indexOf(
                "!evictionPipeline.celtium$setStillCached(prepared.descriptor, prepared.textureKey)", evicted);
        assertTrue(textureQuery > evicted, "the textured family's query is part of the eviction case");
        int siblingQuery = source.indexOf("celtium$siblingSetEvicted(prepared, currentTexture)", textureQuery);
        assertTrue(siblingQuery > textureQuery,
                "the sibling families' case is admitted to the same evicted term");
        int branch = source.indexOf(
                "if (currentTexture != null && (forced || evicted || currentTexture.key() != prepared.textureKey))",
                siblingQuery);
        assertTrue(branch > siblingQuery, "the eviction case reaches the rebuild branch beside the moved-key case");
    }

    @Test
    void siblingQueryUsesEachFamilysCacheKey() throws IOException {
        String source = backendSource();
        int helper = source.indexOf(
                "private boolean celtium$siblingSetEvicted(PreparedImmediate prepared, VulkanGlTexture currentTexture)");
        assertTrue(helper >= 0, "the sibling eviction query exists");
        int pairKey = source.indexOf("(currentTexture.key() * 31L) + lightmapKey", helper);
        assertTrue(pairKey > helper,
                "the pair key the sibling caches are keyed by is recomputed from the current wrappers");
        int item = source.indexOf("itemPipeline.celtium$setStillCached(prepared.descriptor, cacheKey)", helper);
        assertTrue(item > helper, "the item family is asked with its variant's key (pair when lit, plain when not)");
        int block = source.indexOf("blockPipeline.celtium$setStillCached(prepared.descriptor,", helper);
        assertTrue(block > helper, "the block family is asked with its pair key");
        int particle = source.indexOf("particlePipeline.celtium$setStillCached(prepared.descriptor,", helper);
        assertTrue(particle > helper, "the particle family is asked with its pair key");
    }

    @Test
    void rebuildBranchCanRebuildBlockAndParticleGroups() throws IOException {
        String source = backendSource();
        int dispatch = source.indexOf("} else if (prepared.pipeline instanceof VulkanImmediateItemPipeline itemPipeline) {");
        assertTrue(dispatch >= 0, "the rebuild dispatch is present");
        int block = source.indexOf("blockPipeline.descriptorSetFor(currentTexture, this.lightmapTexture)", dispatch);
        assertTrue(block > dispatch, "a block group is rebuilt with its texture and the light map");
        int particle = source.indexOf("particlePipeline.descriptorSetFor(currentTexture, this.lightmapTexture)", dispatch);
        assertTrue(particle > dispatch, "a particle group is rebuilt with its texture and the light map");
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
