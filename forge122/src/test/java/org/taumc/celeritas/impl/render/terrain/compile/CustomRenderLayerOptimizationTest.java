package org.taumc.celeritas.impl.render.terrain.compile;

import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins the render-pass optimization rule in {@link VintageChunkBuildContext#selectOptimizedMaterial}:
 * vanilla cutout/translucent passes downgrade opaque sprites to SOLID, while custom mod render layers
 * (e.g. GTCEu's Bloom layer) MUST preserve their own material even for opaque sprites.
 */
final class CustomRenderLayerOptimizationTest {

    private static Material dummyMaterial(String name, boolean translucent) {
        TerrainRenderPass pass = TerrainRenderPass.builder()
                .name(name)
                .supportsFragmentDiscard(false)
                .useReverseOrder(translucent)
                .useTranslucencySorting(translucent)
                .build();
        return new Material(pass, AlphaCutoffParameter.ZERO, true);
    }

    @Test
    void customModLayerWithOpaqueSpriteIsNotDowngradedToSolid() {
        Material solid = dummyMaterial("solid", false);
        Material cutout = dummyMaterial("cutout", false);
        Material cutoutMipped = dummyMaterial("cutout_mipped", false);
        Material translucent = dummyMaterial("translucent", true);
        Material bloom = dummyMaterial("bloom", false);

        assertSame(bloom, VintageChunkBuildContext.selectOptimizedMaterial(
                bloom, SpriteTransparencyLevel.OPAQUE, solid, cutoutMipped, translucent, cutout),
                "custom mod layer (bloom) must not be downgraded to solid for opaque sprites");
        assertSame(solid, VintageChunkBuildContext.selectOptimizedMaterial(
                cutoutMipped, SpriteTransparencyLevel.OPAQUE, solid, cutoutMipped, translucent, cutout),
                "vanilla cutout_mipped still downgrades opaque sprites to solid");
        assertSame(cutoutMipped, VintageChunkBuildContext.selectOptimizedMaterial(
                translucent, SpriteTransparencyLevel.TRANSPARENT, solid, cutoutMipped, translucent, cutout),
                "vanilla translucent still downgrades non-translucent sprites to cutout_mipped");
    }
}
