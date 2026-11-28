package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass.RGBA8_UNORM;

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
        var coordinator = new FrameCoordinator(null);
        var specs = List.of(pass("modded_post", 10, 11));
        assertFalse(FrameCoordinator.isBundledVanillaPostChain(specs));
        assertEquals(FrameCoordinator.VanillaPostOutcome.REFUSED_UNSUPPORTED, coordinator.runVanillaPostChain(specs));
        assertEquals(0, coordinator.postChainUnsupportedRefusals());
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
