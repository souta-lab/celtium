package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The indirect run path's half of the draw-side eviction guard, pinned as a source contract beside
 * {@link SiblingEvictedDescriptorRebuildTest} for the same reason: the failure needs a device-bound
 * allocation sequence to reproduce at runtime.
 * <p>
 * A run holds the descriptor its first group was prepared with from the enqueue to the flush, and the
 * per-group guard cannot cover it: the run's use is still unrecorded while later groups of the same
 * flush are processed, and a later group's rebuild on the same pipeline instance can evict exactly
 * that set into the completion-gated free, which counts recorded uses only (item 699 - the indirect
 * shape of the flush-boundary crash). These assertions pin the repair's pieces: the run records the
 * texture name and the cache key its set was prepared under, the flush re-asks the cache before any
 * draw binds the held set, an evicted set is rebuilt for the run's own texture (with the light map
 * for the lit item family), and a run whose rebuild is unavailable is dropped rather than drawn.
 */
class IndirectRunEvictedDescriptorTest {

    private static String runsSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java"));
    }

    @Test
    void runRecordsTheTextureAndCacheKeyItsSetWasPreparedUnder() throws IOException {
        String source = runsSource();
        int start = source.indexOf("this.indirectRunTextureSet = prepared.descriptor;");
        assertTrue(start >= 0, "the run records its first group's descriptor at the open");
        int textureId = source.indexOf("this.indirectRunTextureId = group.texture;", start);
        assertTrue(textureId > start, "the run records the GL texture name its groups sampled");
        int pairKey = source.indexOf("(prepared.textureKey * 31L) + (b.lightmapTexture == null ? 0L : b.lightmapTexture.key())",
                textureId);
        assertTrue(pairKey > textureId,
                "the lit item family's cache key is the pair key recomputed from the current wrappers");
        int plainKey = source.indexOf(": prepared.textureKey;", pairKey);
        assertTrue(plainKey > pairKey, "the textured family's cache key is the texture key itself");
    }

    @Test
    void flushRevalidatesTheHeldSetBeforeAnyDrawBindsIt() throws IOException {
        String source = runsSource();
        int flush = source.indexOf("void celtium$flushIndirectRun(String reason)");
        assertTrue(flush >= 0, "the run flush exists");
        int guard = source.indexOf("if (!celtium$runDescriptorUsable())", flush);
        assertTrue(guard > flush, "the flush re-asks the cache before drawing the run");
        int singleton = source.indexOf("if (this.indirectRunCommands == 1 && this.indirectRunOriginalPipeline != null)",
                guard);
        assertTrue(singleton > guard,
                "the revalidation precedes the singleton fallback draw, which binds the held set directly");
        int indirect = source.indexOf("this.indirectRunPipeline.drawIndirectRange", guard);
        assertTrue(indirect > guard, "the revalidation precedes the indirect draw, which binds the held set");
    }

    @Test
    void revalidationAsksEachDescriptorFamilyAndRebuildsOrDrops() throws IOException {
        String source = runsSource();
        int method = source.indexOf("boolean celtium$runDescriptorUsable()");
        assertTrue(method >= 0, "the revalidation query exists");
        int colourBypass = source.indexOf("if (!textured && !litItem)", method);
        assertTrue(colourBypass > method,
                "families that hold no descriptor (the colour family) are never asked");
        int stillCached = source.indexOf("celtium$setStillCached(this.indirectRunTextureSet, this.indirectRunDescriptorKey)",
                colourBypass);
        assertTrue(stillCached > colourBypass, "the cache map is asked whether the held set is still its entry");
        int resolve = source.indexOf("b.celtium$registry().get(this.indirectRunTextureId)", stillCached);
        assertTrue(resolve > stillCached, "an evicted set is rebuilt from the run's texture as resolved at the flush");
        int texturedRebuild = source.indexOf("descriptorSetFor(current)", resolve);
        assertTrue(texturedRebuild > resolve, "the textured family is rebuilt from its texture alone");
        int itemRebuild = source.indexOf("descriptorSetFor(current, b.lightmapTexture)", resolve);
        assertTrue(itemRebuild > resolve, "the lit item family is rebuilt with the current light map");
        int drop = source.indexOf("this.indirectRunDescriptorDrops++;", itemRebuild);
        assertTrue(drop > itemRebuild, "a run with no rebuild available is counted as dropped");
        int refuse = source.indexOf("return false;", drop);
        assertTrue(refuse > drop, "a run with no rebuild available is refused rather than drawn with a retired set");
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
