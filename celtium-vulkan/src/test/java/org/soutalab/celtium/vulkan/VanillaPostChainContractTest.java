package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass.RGBA8_UNORM;

/**
 * The ruling section 5.4 contract for the vanilla post receiver: the entity-outline chain is a
 * bundled (category A) target whose input is adopted at both ends, a refusal on a category-A chain
 * is counted with its reason and never silently falls back, and a non-bundled (category B) group
 * moves no loss counter at all. The mixin boundary itself is pinned by source contract, in the
 * shape the repo's other boundary contracts use: the mixin calls the coordinator's entrances, not
 * a monolith delegator, and the retired VulkanPostChain shape is gone from it.
 */
class VanillaPostChainContractTest {
    @Test
    void outlineUsesAdoptedImageAtBothEndsAndResolvesScratchInPassOrder() {
        var outline = target(1);
        var next = new AtomicLong(2);
        var specs = List.of(pass("entity_outline", 10, 11), pass("blur", 11, 12), pass("blit", 12, 10));
        var resolved = VanillaPostExecutor.resolvePasses(specs, new HashMap<>(Map.of(10, outline)),
                (width, height) -> fail("outline must never use the game image"), false,
                (width, height) -> target(next.getAndIncrement()));
        assertSame(outline, resolved.getFirst().inputs()[0]);
        assertSame(resolved.get(0).output(), resolved.get(1).inputs()[0]);
        assertSame(resolved.get(1).output(), resolved.get(2).inputs()[0]);
        assertSame(outline, resolved.getLast().output());
    }

    @Test
    void unsuppliedInputRefusesBeforeAllocatingScratch() {
        var specs = List.of(pass("entity_outline", 10, 11));
        var refusal = assertThrows(VanillaPostExecutor.Unsupported.class,
                () -> VanillaPostExecutor.resolvePasses(specs, new HashMap<>(), (width, height) -> fail(), false,
                        (width, height) -> fail()));
        assertEquals("unsupplied framebuffer input 10", refusal.getMessage());
    }

    @Test
    void nonBundledGroupsDoNotIncrementVanillaLosses() {
        var coordinator = new FrameCoordinator(null, null);
        var specs = List.of(pass("modded_post", 10, 11));
        assertFalse(FrameCoordinator.isBundledVanillaPostChain(specs));
        assertEquals(FrameCoordinator.VanillaPostOutcome.REFUSED_UNSUPPORTED, coordinator.runVanillaPostChain(specs));
        assertEquals(0, coordinator.postChainUnsupportedRefusals());
    }

    @Test
    void theEntityOutlineChainIsABundledCategoryATarget() {
        var specs = List.of(pass("entity_outline", 10, 11), pass("blur", 11, 12), pass("blit", 12, 10));
        assertTrue(FrameCoordinator.isBundledVanillaPostChain(specs),
                "the outline group's three programs are all in the bundled set");
    }

    @Test
    void aBundledChainRefusedAtPreflightIsCountedWithItsReason() {
        var coordinator = new FrameCoordinator(null, null);
        var specs = List.of(pass("entity_outline", 10, 11), pass("blur", 11, 12), pass("blit", 12, 10));
        assertEquals(FrameCoordinator.VanillaPostOutcome.REFUSED_UNSUPPORTED,
                coordinator.runVanillaPostChain(specs),
                "no recording frame: the chain is refused, never silently left to GL");
        assertEquals(1, coordinator.postChainUnsupportedRefusals());
        assertNotNull(coordinator.lastVanillaPostRefusalReason(),
                "the refusal carries the reason the boundary prints");
    }

    @Test
    void aBoundaryRefusalIsCountedLikeAPreflightRefusal() {
        var coordinator = new FrameCoordinator(null, null);
        assertEquals(FrameCoordinator.VanillaPostOutcome.REFUSED_UNSUPPORTED,
                coordinator.refuseVanillaPostChain("pass-not-collected"));
        assertEquals(1, coordinator.postChainUnsupportedRefusals());
        assertEquals("pass-not-collected", coordinator.lastVanillaPostRefusalReason());
        coordinator.refuseVanillaPostChain("input-without-texture");
        assertEquals(2, coordinator.postChainUnsupportedRefusals());
        assertEquals("input-without-texture", coordinator.lastVanillaPostRefusalReason());
    }

    @Test
    void theMixinCallsTheCoordinatorEntrancesNotTheRetiredChain() throws IOException {
        String mixin = Files.readString(findRepoFile(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/ShaderGroupMixin.java"));
        assertTrue(mixin.contains("coordinator.runVanillaPostChain(passes)"),
                "the chain runs through the frame coordinator's entrance (ruling 5.2)");
        assertTrue(mixin.contains("FrameCoordinator.isBundledVanillaPostChain(passes)"),
                "the category B gate is the bundled-set check, decided before any run");
        assertTrue(mixin.contains("coordinator.refuseVanillaPostChain("),
                "boundary refusals are counted through the coordinator");
        assertFalse(mixin.contains("VulkanPostChain"),
                "the retired chain shape is gone from the mixin");
        assertFalse(mixin.contains("celtium$postChainRun"),
                "no monolith delegator remains on the chain path");
        assertFalse(mixin.contains("celtium$postChainAvailable"),
                "the old availability gate is gone; preflight refusal is the coordinator's");
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

    private static VanillaPostExecutor.Target target(long image) {
        return new VanillaPostExecutor.Target(image, image, 16, 16, RGBA8_UNORM, VK_IMAGE_LAYOUT_GENERAL);
    }

    private static VanillaPostPassSpec pass(String program, int input, int output) {
        return new VanillaPostPassSpec(program, input, 16, 16, output, 16, 16, false, false,
                new int[0], new int[0], new int[0], new float[21], 84,
                new String[] {"ProjMat", "InSize", "OutSize", "Time"}, new int[] {0, 64, 72, 80},
                20, 10, 16, 16);
    }
}
