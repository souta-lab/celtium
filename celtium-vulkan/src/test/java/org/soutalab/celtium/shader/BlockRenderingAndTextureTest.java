package org.soutalab.celtium.shader;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.shader.block_rendering.BlockRenderingSettings;
import org.soutalab.celtium.shader.shaderpack.IdMap;
import org.soutalab.celtium.shader.shaderpack.materialmap.NamespacedId;
import org.soutalab.celtium.shader.texture.PBRType;
import org.soutalab.celtium.shader.texture.format.TextureFormat;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockRenderingAndTextureTest {

    @Test
    void packsMcEntityShortAndResolvesDefaultUnmapped() {
        BlockRenderingSettings settings = BlockRenderingSettings.fromIdMap(
                IdMap.EMPTY, 1.0f, false, true, true);
        NamespacedId stone = new NamespacedId("minecraft", "stone");
        assertEquals(BlockRenderingSettings.UNMAPPED_ID, settings.resolveBlockId(stone, 0));
        // ((-1 + 1) << 1) | 0 == 0
        assertEquals((short) 0, settings.packMcEntityShort(stone, 0, false));
        // ((-1 + 1) << 1) | 1 == 1 (fluid flag)
        assertEquals((short) 1, settings.packMcEntityShort(stone, 0, true));
        assertNull(settings.resolvePassOverride(stone));
        assertTrue(settings.writeStainedGlassDepth());
    }

    @Test
    void pbrTypeSuffixesAndLabPbrDefinesWorkWithoutMinecraftOrGlsm() {
        assertEquals("textures/blocks/stone_n.png", PBRType.NORMAL.appendToFileLocation("textures/blocks/stone.png"));
        assertEquals("textures/blocks/stone_s.png", PBRType.SPECULAR.appendToFileLocation("textures/blocks/stone.png"));
        assertEquals(PBRType.NORMAL, PBRType.fromFileLocation("textures/blocks/stone_n.png"));
        assertEquals(PBRType.SPECULAR, PBRType.fromFileLocation("textures/blocks/stone_s.png"));
        assertNull(PBRType.fromFileLocation("textures/blocks/stone.png"));

        TextureFormat lab = TextureFormat.Registry.INSTANCE.create("lab-pbr", "1.3");
        assertNotNull(lab);
        assertEquals(List.of("MC_TEXTURE_FORMAT_LAB_PBR", "MC_TEXTURE_FORMAT_LAB_PBR_1_3"), lab.getDefines());
        assertTrue(lab.canInterpolateValues(PBRType.NORMAL));
        assertFalse(lab.canInterpolateValues(PBRType.SPECULAR));
        assertNotNull(lab.getMipmapGenerator(PBRType.SPECULAR));
    }
}
