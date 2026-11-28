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
 * The rule that decides whether a published colour name's image has gone stale under it, and the wiring
 * that acts on the verdict (blackness fix B).
 * <p>
 * What the decision table proves is {@code VulkanTerrainGpuBackend.celtium$staleFramePublication}: a name published
 * over the frame target's own colour image (no chain-pair owner, no depth record - the two records that
 * give a publication another liveness source) is stale exactly when the image it names is no longer
 * either live colour image the target holds. The measured failure this pins is the GTCEu bloom legs'
 * black world: the main name was published once over a borrowed swapchain image, the borrow rotated to
 * the next swapchain image (and, on capture frames, bypassed to the owned image), and every resolution
 * kept returning the wrapper over the single already-presented image, which the bloom chain then sampled
 * and wrote back over the world.
 * <p>
 * The acting sites live inside device-bound methods - the immediate prepare and the
 * published-texture transition walk - so their wiring is pinned by source
 * contracts in the shape {@code FallbackWiringContractTest} established: each site must consult the rule
 * (or, for the walk, carry the frame image in its prune trigger), exactly once. (The third site, the
 * retired ModPrograms sampler resolution, consulted the same rule until the prepare path's retirement
 * in ruling step 5; the rule itself was seated in the backend with it.)
 */
class StaleFramePublicationTest {

    private static final long FRAME = 0x1000L;
    private static final long OFF_TARGET = 0x2000L;
    private static final long OLD_BORROW = 0x3000L;
    private static final long PAIR_OWNER = 0x5000L;
    private static final long DEPTH_KEY = 0x4000L;

    // --- the decision table ---

    @Test
    void publishedOverTheLiveFrameImageIsNotStale() {
        assertFalse(VulkanTerrainGpuBackend.celtium$staleFramePublication(FRAME, FRAME, OFF_TARGET,
                Long.MIN_VALUE, 0L));
    }

    @Test
    void publishedOverTheLiveOffTargetImageIsNotStale() {
        assertFalse(VulkanTerrainGpuBackend.celtium$staleFramePublication(OFF_TARGET, FRAME, OFF_TARGET,
                Long.MIN_VALUE, 0L));
    }

    @Test
    void publishedOverAnAlreadyPresentedBorrowIsStale() {
        assertTrue(VulkanTerrainGpuBackend.celtium$staleFramePublication(OLD_BORROW, FRAME, OFF_TARGET,
                Long.MIN_VALUE, 0L),
                "the borrow rotated: the name still stands for the previous swapchain image");
    }

    @Test
    void publishedOverABorrowTheBypassReplacedIsStale() {
        assertTrue(VulkanTerrainGpuBackend.celtium$staleFramePublication(OLD_BORROW, FRAME, FRAME,
                Long.MIN_VALUE, 0L),
                "the bypass swapped in the owned image, which is also the off-target image here");
    }

    @Test
    void anUnpublishedNameIsNotStale() {
        assertFalse(VulkanTerrainGpuBackend.celtium$staleFramePublication(0L, FRAME, OFF_TARGET,
                Long.MIN_VALUE, 0L),
                "no publication is the unpublished rule's case, not this one's");
    }

    @Test
    void aChainPairPublicationHasAnotherLivenessSource() {
        assertFalse(VulkanTerrainGpuBackend.celtium$staleFramePublication(OLD_BORROW, FRAME, OFF_TARGET,
                PAIR_OWNER, 0L),
                "a pair-owned name follows its pair's generation, not the frame image");
    }

    @Test
    void aDepthPublicationHasAnotherLivenessSource() {
        assertFalse(VulkanTerrainGpuBackend.celtium$staleFramePublication(OLD_BORROW, FRAME, OFF_TARGET,
                Long.MIN_VALUE, DEPTH_KEY),
                "a depth name follows the shared depth store's key, not the frame image");
    }

    // --- the wiring contracts ---

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }

    private static int count(String text, String needle) {
        int found = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    @Test
    void theRuleIsSeatedInTheBackendExactlyOnce() throws IOException {
        String source = read(BACKEND);
        assertEquals(1, count(source, "static boolean celtium$staleFramePublication("),
                "one definition, seated in the backend since the ModPrograms retirement");
    }

    @Test
    void immediatePrepareConsultsTheRuleOnce() throws IOException {
        String source = read(BACKEND);
        assertEquals(1, count(source, "celtium$staleFramePublication(publishedImage,"));
        assertTrue(source.contains("|| staleFramePublication))"),
                "a stale verdict must reach the prepare's re-publish branch");
    }

    @Test
    void transitionWalkCarriesTheFrameImageInItsPruneTrigger() throws IOException {
        // The walk's seat moved from the retired PostChainBridge into the backend itself
        // (ruling step 5); the prune trigger's shape is unchanged.
        String source = read(BACKEND);
        assertEquals(3, count(source, "publishedCacheFrameImage"),
                "the field, the trigger comparison and the memo update");
        assertTrue(source.contains("frameColorImage != this.publishedCacheFrameImage"),
                "a frame-image change alone must re-run the prune and rebuild the cache");
    }
}
