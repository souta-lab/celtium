package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.ImageSlot;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.NamedTargetSpec;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.NamedTargetState;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetClearPolicy;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetKind;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetSizing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedRenderTargetLedgerTest {

    @Test
    void zeroNamedTargetsPreservesExistingChainPairBehavior() {
        RenderTargetRegistry registry = new RenderTargetRegistry();
        PublishedFrameTextures published = new PublishedFrameTextures();
        NamedRenderTargetAllocator allocator = new NamedRenderTargetAllocator(null, registry, published);

        allocator.ensureAllocated(1280, 720);
        assertEquals(0, registry.namedTargetCount());
        assertTrue(registry.namedTargets().isEmpty());
        assertEquals(0, published.publishedNamedCount());
        assertEquals(0, published.publishedCount());

        long fboA = RenderTargetRegistry.rawFramebufferIdentity(10);
        long fboB = RenderTargetRegistry.rawFramebufferIdentity(11);
        assertEquals(ImageSlot.CHAIN_PAIR, registry.noteBind(fboA, false, "a"));
        assertEquals(ImageSlot.CHAIN_PAIR, registry.noteBind(fboB, false, "b"));
        assertTrue(registry.sharedSlots().startsWith("chain-pair="));
    }

    @Test
    void rejectsDuplicateNamesAndInvalidSpecs() {
        RenderTargetRegistry registry = new RenderTargetRegistry();
        NamedTargetSpec color = NamedTargetSpec.color(
                "scene_color", TargetFormatClass.RGBA16_FLOAT, TargetSizing.scaled(1.0f), TargetClearPolicy.CLEAR_EACH_FRAME);
        registry.declareNamedTarget(color);

        assertThrows(IllegalArgumentException.class, () -> registry.declareNamedTarget(color));
        assertThrows(IllegalArgumentException.class, () -> NamedTargetSpec.color(
                " ", TargetFormatClass.RGBA8_UNORM, TargetSizing.scaled(1.0f), TargetClearPolicy.DONT_CARE));
        assertThrows(IllegalArgumentException.class, () -> NamedTargetSpec.color(
                "bad_depth_fmt", TargetFormatClass.DEPTH32_FLOAT, TargetSizing.scaled(1.0f), TargetClearPolicy.DONT_CARE));
        assertThrows(IllegalArgumentException.class, () -> NamedTargetSpec.depth(
                "bad_color_fmt", TargetFormatClass.RGBA8_UNORM, TargetSizing.scaled(1.0f), TargetClearPolicy.CLEAR_EACH_FRAME));
        assertThrows(IllegalArgumentException.class, () -> NamedTargetSpec.shadowDepth(
                "bad_shadow_fmt", TargetFormatClass.RGBA16_FLOAT, TargetSizing.fixed(1024, 1024), TargetClearPolicy.CLEAR_EACH_FRAME));
        assertThrows(IllegalArgumentException.class, () -> TargetSizing.scaled(0.0f));
        assertThrows(IllegalArgumentException.class, () -> TargetSizing.scaled(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> TargetSizing.fixed(0, 512));
        assertThrows(IllegalArgumentException.class, () -> new NamedTargetSpec(
                "bad_clear_depth", TargetKind.DEPTH, TargetFormatClass.DEPTH32_FLOAT,
                TargetSizing.scaled(1.0f), TargetClearPolicy.CLEAR_EACH_FRAME, 0f, 0f, 0f, 0f, 1.5f));
    }

    @Test
    void declaresShadowAndScaledTargetsAndPublishesWithoutTouchingGlNames() {
        RenderTargetRegistry registry = new RenderTargetRegistry();
        PublishedFrameTextures published = new PublishedFrameTextures();

        NamedTargetSpec halfColor = NamedTargetSpec.color(
                "half_color", TargetFormatClass.R11G11B10_FLOAT, TargetSizing.scaled(0.5f),
                TargetClearPolicy.CLEAR_ON_CREATE, 0.1f, 0.2f, 0.3f, 1.0f);
        NamedTargetSpec shadowMap = NamedTargetSpec.shadowDepth(
                "shadow_map", TargetFormatClass.DEPTH32_FLOAT, TargetSizing.fixed(2048, 2048),
                TargetClearPolicy.CLEAR_EACH_FRAME);

        registry.declareNamedTarget(halfColor);
        registry.declareNamedTarget(shadowMap);

        assertEquals(2, registry.namedTargetCount());
        assertEquals(640, halfColor.sizing().resolveWidth(1280));
        assertEquals(360, halfColor.sizing().resolveHeight(720));
        assertEquals(2048, shadowMap.sizing().resolveWidth(1280));
        assertEquals(2048, shadowMap.sizing().resolveHeight(720));
        assertTrue(shadowMap.kind().isDepth());
        assertTrue(shadowMap.kind().usesComparisonSampler());
        assertEquals(TargetClearPolicy.CLEAR_ON_CREATE, halfColor.clearPolicy());
        assertEquals(TargetClearPolicy.CLEAR_EACH_FRAME, shadowMap.clearPolicy());

        long versionBefore = published.publishedVersion();
        NamedTargetState colorState = registry.noteNamedTargetAllocated("half_color", 101L, 201L, 301L, 640, 360, 0);
        NamedTargetState shadowState = registry.noteNamedTargetAllocated("shadow_map", 102L, 202L, 302L, 2048, 2048, 0);
        assertEquals(1L, colorState.generation());
        assertEquals(1L, shadowState.generation());

        published.syncNamedTargets(registry);
        assertTrue(published.publishedVersion() > versionBefore);
        assertEquals(2, published.publishedNamedCount());
        assertEquals(0, published.publishedCount());

        PublishedFrameTextures.NamedTargetTexture pubColor = published.publishedNamed("half_color");
        assertNotNull(pubColor);
        assertEquals(640, pubColor.width());
        assertEquals(360, pubColor.height());
        assertFalse(pubColor.depth());
        assertFalse(pubColor.comparisonSampler());

        PublishedFrameTextures.NamedTargetTexture pubShadow = published.publishedNamed("shadow_map");
        assertNotNull(pubShadow);
        assertEquals(2048, pubShadow.width());
        assertEquals(2048, pubShadow.height());
        assertTrue(pubShadow.depth());
        assertTrue(pubShadow.comparisonSampler());
        assertEquals(TargetFormatClass.DEPTH32_FLOAT.vkFormat(), pubShadow.vkFormat());

        registry.forgetNamedTargetAllocation("half_color");
        published.syncNamedTargets(registry);
        assertNull(published.publishedNamed("half_color"));
        assertEquals(1, published.publishedNamedCount());
    }
}
