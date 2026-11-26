package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registration gate's variant eligibility (R-17 candidate 2-MIN): only a
 * presented type celtium does not itself define is a pack variant; the three
 * built-in types keep their existing routes.
 */
class PackVariantEligibilityTest {

    /** A stand-in for a shader pack's own vertex type (Demonica's shape). */
    private static final ChunkVertexType FOREIGN = new ChunkVertexType() {
        @Override
        public float getPositionScale() {
            return 1.0f;
        }

        @Override
        public float getPositionOffset() {
            return 0.0f;
        }

        @Override
        public float getTextureScale() {
            return 1.0f;
        }

        @Override
        public GlVertexFormat getVertexFormat() {
            return null;
        }

        @Override
        public ChunkVertexEncoder createEncoder() {
            return null;
        }
    };

    @Test
    void builtInTypesAreNotVariants() {
        assertFalse(TerrainRenderer.packVariantEligible(ChunkMeshFormats.TERRAIN_A16));
        assertFalse(TerrainRenderer.packVariantEligible(ChunkMeshFormats.VANILLA_LIKE));
        assertFalse(TerrainRenderer.packVariantEligible(ChunkMeshFormats.COMPACT));
    }

    @Test
    void aForeignPresentedTypeIsAVariant() {
        assertTrue(TerrainRenderer.packVariantEligible(FOREIGN));
    }
}
