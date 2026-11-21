package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound-target route's owed clear: consumed by the terrain pass itself, for both shapes of pair depth.
 * <p>
 * Item 384's verdict was routing/timing. A mod that binds its own framebuffer around a terrain layer (GTCEu's
 * Bloom layer) has that layer routed into the bound framebuffer's chain pair, and the game's bind-time clear of
 * the framebuffer is deferred as a pair clear whose only consumer is an immediate window's begin. While the
 * bound-target branch consumed that clear only for a pair borrowing a shared depth, a pair on its own depth
 * left the clear pending past the terrain pass: the frame's first window after the pass consumed it over the
 * mask the pass had just drawn, every frame, so the chain read an erased mask and no halo formed.
 * <p>
 * The fix has two halves, and both are pinned here. The wiring half is a source contract: the branch calls
 * {@code celtium$consumeRoutedTargetClear} unconditionally, before the pair's attach acquire, in the depth
 * route's order. The decision half is the pure rule the consume consults for its depth half: a shared depth is
 * guarded once per frame per depth key, while a pair on its own depth (key 0) is allowed - its once-per-frame
 * guard is the per-identity rule the consume has already passed, and the shared rule's "key 0 answers false"
 * must not strip a private depth's clear.
 * <p>
 * What this proves: the allowance table and the call site's shape. It does not prove a frame's mask survives to
 * the chain's sample; that needs a device and the frame-granular patch instrument of item 384's follow-up leg.
 */
class BoundTargetRoutedClearTest {

    /** A pair on its own depth: nothing is shared, so the depth half runs whatever the serials say. */
    @Test
    void aPairOnItsOwnDepthMayClearItsDepth() {
        assertTrue(VulkanTerrainGpuBackend.celtium$routedDepthClearAllowed(0L, -1L, 41L),
                "never cleared");
        assertTrue(VulkanTerrainGpuBackend.celtium$routedDepthClearAllowed(0L, 41L, 41L),
                "the shared rule's same-frame answer must not leak into the private shape: the per-identity "
                        + "rule upstream of this decision is the guard here");
        assertTrue(VulkanTerrainGpuBackend.celtium$routedDepthClearAllowed(0L, 41L, 42L),
                "an earlier frame");
    }

    /** A shared depth never cleared in this frame: the first member's clear runs. */
    @Test
    void aSharedDepthNotYetClearedThisFrameMayClear() {
        assertTrue(VulkanTerrainGpuBackend.celtium$routedDepthClearAllowed(0x2aL, -1L, 41L));
    }

    /** A shared depth already cleared this frame: a second member must not clear what the first drew. */
    @Test
    void aSharedDepthClearedThisFrameRefusesTheDepthHalf() {
        assertFalse(VulkanTerrainGpuBackend.celtium$routedDepthClearAllowed(0x2aL, 41L, 41L));
    }

    /** A shared depth cleared in an earlier frame: the guard is per frame, not permanent. */
    @Test
    void aSharedDepthClearedInAnEarlierFrameMayClearAgain() {
        assertTrue(VulkanTerrainGpuBackend.celtium$routedDepthClearAllowed(0x2aL, 41L, 42L));
    }

    /**
     * The wiring: inside the bound-target branch, the consume runs for every routed pass - not only for a
     * shared-depth member - and ahead of the attach acquire, which is the order the depth route uses.
     */
    @Test
    void theBoundTargetBranchConsumesTheClearBeforeTheAcquire() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int branch = source.indexOf("TERRAIN_TO_BOUND_TARGET && !this.targetRegistry.currentIsMain()");
        assertTrue(branch >= 0, "the bound-target branch is present");
        String rest = source.substring(branch, branch + 8000);
        int consume = rest.indexOf("celtium$consumeRoutedTargetClear(pairOwner, identity, boundDepthKey);");
        int acquire = rest.indexOf("pairOwner.recordChainPairAttachAcquire(identity, boundDepthKey, this.commandBuffer);");
        assertTrue(consume >= 0, "the branch consumes the owed clear");
        assertTrue(acquire > consume, "the consume precedes the attach acquire, the depth route's order");
        String before = rest.substring(0, consume);
        assertFalse(before.contains("if (boundDepthKey != 0L)"),
                "the consume must not be guarded on a shared depth: that guard is the shape that left a "
                        + "private-depth pair's clear pending past the pass");
    }

    /** The consume consults the routed allowance, not the shared rule directly. */
    @Test
    void theConsumeConsultsTheRoutedAllowance() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int consume = source.indexOf("private void celtium$consumeRoutedTargetClear(");
        assertTrue(consume >= 0, "the consume is declared");
        String body = source.substring(consume, consume + 4000);
        assertTrue(body.contains("celtium$routedDepthClearAllowed(depthKey,"),
                "the depth allowance goes through the routed rule, which is what admits a private depth");
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
