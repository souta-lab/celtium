package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record-reading shader pairs stage 6 adds (slice S6-b), pinned at the two facts the
 * migration turns on: the pairs exist as compiled modules (the build's glslang step compiled
 * them and the loader resolves them), and they are dedicated pairs - a texgen or combine
 * group drawn through the base textured family's pair would be drawn with the wrong shader,
 * which is the failure the old twin refusal existed to prevent.
 */
class RecordsShaderPairsTest {

    /** SPIR-V's magic number, little-endian as the loader returns it. */
    private static final int SPIRV_MAGIC = 0x07230203;

    private static void assertLoads(String shader) {
        ByteBuffer code = SpirvShaders.load(shader).order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(code.remaining() >= 4, shader + " compiled to nothing");
        assertEquals(SPIRV_MAGIC, code.getInt(0), shader + " is not a SPIR-V module");
    }

    @Test
    @DisplayName("the block family's record-reading pair loads")
    void blockRecordsPairLoads() {
        assertLoads(SpirvShaders.IMMEDIATE_BLOCK_RECORDS_VERTEX);
        assertLoads(SpirvShaders.IMMEDIATE_BLOCK_RECORDS_FRAGMENT);
    }

    @Test
    @DisplayName("the texgen family's record-reading pair loads")
    void texgenRecordsPairLoads() {
        assertLoads(SpirvShaders.IMMEDIATE_TEXGEN_RECORDS_VERTEX);
        assertLoads(SpirvShaders.IMMEDIATE_TEXGEN_RECORDS_FRAGMENT);
    }

    @Test
    @DisplayName("the combine family's record-reading pair loads")
    void combine4RecordsPairLoads() {
        assertLoads(SpirvShaders.IMMEDIATE_COMBINE4_RECORDS_VERTEX);
        assertLoads(SpirvShaders.IMMEDIATE_COMBINE4_RECORDS_FRAGMENT);
    }

    @Test
    @DisplayName("the texgen and combine pairs are dedicated, not the base family's pair")
    void texgenAndCombinePairsAreDedicated() {
        assertNotEquals(SpirvShaders.IMMEDIATE_TEX_COLOR_RECORDS_VERTEX,
                SpirvShaders.IMMEDIATE_TEXGEN_RECORDS_VERTEX);
        assertNotEquals(SpirvShaders.IMMEDIATE_TEX_COLOR_RECORDS_FRAGMENT,
                SpirvShaders.IMMEDIATE_TEXGEN_RECORDS_FRAGMENT);
        assertNotEquals(SpirvShaders.IMMEDIATE_TEX_COLOR_RECORDS_VERTEX,
                SpirvShaders.IMMEDIATE_COMBINE4_RECORDS_VERTEX);
        assertNotEquals(SpirvShaders.IMMEDIATE_TEX_COLOR_RECORDS_FRAGMENT,
                SpirvShaders.IMMEDIATE_COMBINE4_RECORDS_FRAGMENT);
        assertNotEquals(SpirvShaders.IMMEDIATE_TEXGEN_RECORDS_VERTEX,
                SpirvShaders.IMMEDIATE_COMBINE4_RECORDS_VERTEX);
    }
}
