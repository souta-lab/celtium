package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure rules of QUEUE 395 (D18-B, post chain format selection), exercised without a device: which class
 * a pass writes in, how pipelines are keyed per class, and when a target must be rebuilt. The device halves
 * (render pass variants, the usable() feature check, allocation sizes) are gated on PC and in the leg; what
 * is pinned here is every decision that does not need a device, because a wrong decision here is silent in
 * game - the chain would run in the wrong format and only the pixels would say so.
 */
class PostChainFormatSelectionTest {

    // A pass that writes the group's main framebuffer writes the shared frame image: always the default
    // class, whatever the chain runs in (QUEUE 404 rule R1, carried into the chain).
    @Test
    void aPassWritingMainStaysInTheDefaultClass() {
        assertEquals(TargetFormatClass.RGBA8_UNORM,
                VulkanPostChain.celtium$outFormatClass(true, TargetFormatClass.RGBA16_FLOAT));
        assertEquals(TargetFormatClass.RGBA8_UNORM,
                VulkanPostChain.celtium$outFormatClass(true, TargetFormatClass.RGBA32_FLOAT));
        assertEquals(TargetFormatClass.RGBA8_UNORM,
                VulkanPostChain.celtium$outFormatClass(true, TargetFormatClass.RGBA8_UNORM));
    }

    // A pass that writes a chain target writes in the chain's class; an unresolved class is the default.
    @Test
    void aPassWritingAChainTargetWritesInTheChainClass() {
        assertEquals(TargetFormatClass.RGBA16_FLOAT,
                VulkanPostChain.celtium$outFormatClass(false, TargetFormatClass.RGBA16_FLOAT));
        assertEquals(TargetFormatClass.R11G11B10_FLOAT,
                VulkanPostChain.celtium$outFormatClass(false, TargetFormatClass.R11G11B10_FLOAT));
        assertEquals(TargetFormatClass.RGBA8_UNORM,
                VulkanPostChain.celtium$outFormatClass(false, TargetFormatClass.RGBA8_UNORM));
        assertEquals(TargetFormatClass.RGBA8_UNORM,
                VulkanPostChain.celtium$outFormatClass(false, null),
                "no class resolved: the chain runs as every chain ran before selection existed");
    }

    // The default class keeps the program's own name as its pipeline key, so every cache entry an 8-bit
    // chain built keeps its meaning; any other class separates, because a pipeline belongs to one pass.
    @Test
    void thePipelineKeyFoldsTheDefaultClassAndSeparatesTheRest() {
        assertEquals("entity_outline",
                VulkanPostChain.celtium$programKey("entity_outline", TargetFormatClass.RGBA8_UNORM));
        assertEquals("entity_outline",
                VulkanPostChain.celtium$programKey("entity_outline", null));
        assertNotEquals(VulkanPostChain.celtium$programKey("blur", TargetFormatClass.RGBA8_UNORM),
                VulkanPostChain.celtium$programKey("blur", TargetFormatClass.RGBA16_FLOAT));
        assertNotEquals(VulkanPostChain.celtium$programKey("blur", TargetFormatClass.RGBA16_FLOAT),
                VulkanPostChain.celtium$programKey("blur", TargetFormatClass.RGBA32_FLOAT));
        assertEquals(VulkanPostChain.celtium$programKey("blur", TargetFormatClass.RGBA16_FLOAT),
                VulkanPostChain.celtium$programKey("blur", TargetFormatClass.RGBA16_FLOAT),
                "the same class resolves to the same pipeline");
    }

    // Stage 1 BGRA ignition: on a BGRA frame, a pass writing main writes in the frame's class, and the
    // bare pipeline key belongs to the frame's class - an RGBA8 chain target on that frame is the
    // variant, not the default.
    @Test
    void onABgraFrameTheFrameClassIsTheDefault() {
        assertEquals(TargetFormatClass.BGRA8_UNORM,
                VulkanPostChain.celtium$outFormatClass(true, TargetFormatClass.RGBA16_FLOAT,
                        TargetFormatClass.BGRA8_UNORM),
                "writing main on a BGRA frame writes BGRA");
        assertEquals(TargetFormatClass.BGRA8_UNORM,
                VulkanPostChain.celtium$outFormatClass(false, null, TargetFormatClass.BGRA8_UNORM),
                "an unresolved chain on a BGRA frame runs in the frame's class");
        assertEquals(TargetFormatClass.RGBA16_FLOAT,
                VulkanPostChain.celtium$outFormatClass(false, TargetFormatClass.RGBA16_FLOAT,
                        TargetFormatClass.BGRA8_UNORM),
                "a resolved chain class is still the chain's own");
        assertEquals("blur",
                VulkanPostChain.celtium$programKey("blur", TargetFormatClass.BGRA8_UNORM,
                        TargetFormatClass.BGRA8_UNORM),
                "the frame's class keeps the bare key on its own frame");
        assertNotEquals(VulkanPostChain.celtium$programKey("blur", TargetFormatClass.BGRA8_UNORM,
                        TargetFormatClass.BGRA8_UNORM),
                VulkanPostChain.celtium$programKey("blur", TargetFormatClass.RGBA8_UNORM,
                        TargetFormatClass.BGRA8_UNORM),
                "RGBA8 on a BGRA frame is a variant and must not share the bare key");
    }

    // A target is rebuilt when its size changes (the rule that predates selection) or when the class the
    // pass asks for differs from the class the image was created in - an image's format cannot change.
    @Test
    void aTargetIsRebuiltOnASizeOrClassChange() {
        assertFalse(VulkanPostChain.celtium$targetRebuildNeeded(true,
                TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA8_UNORM));
        assertFalse(VulkanPostChain.celtium$targetRebuildNeeded(true,
                TargetFormatClass.RGBA16_FLOAT, TargetFormatClass.RGBA16_FLOAT));
        assertTrue(VulkanPostChain.celtium$targetRebuildNeeded(false,
                TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA8_UNORM),
                "a size change alone still rebuilds");
        assertTrue(VulkanPostChain.celtium$targetRebuildNeeded(true,
                TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA16_FLOAT),
                "a class change at the same size rebuilds: the format is fixed at creation");
        assertTrue(VulkanPostChain.celtium$targetRebuildNeeded(true,
                TargetFormatClass.RGBA16_FLOAT, TargetFormatClass.RGBA8_UNORM),
                "and so does falling back to the default class");
    }
}
