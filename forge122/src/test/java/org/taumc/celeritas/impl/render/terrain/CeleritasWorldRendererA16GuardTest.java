package org.taumc.celeritas.impl.render.terrain;

import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.CeleritasVintage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the Stage 4 A16 pack guard at the selection seam.
 *
 * <p>The guard cannot be observed in a real fixture run from either side: with Demonica present its
 * mixin takes over {@code chooseVertexType()}, and without Demonica the Iris API supplier disappears,
 * so {@code ShaderModBridge.areShadersEnabled()} stays false. The production no-arg path therefore
 * delegates to the package-private overload tested here, with the bridge method reference as its
 * shader-state supplier.
 */
class CeleritasWorldRendererA16GuardTest {
    private boolean originalUseCompactVertexFormat;

    @BeforeEach
    void resetGuardAndSaveOption() throws Exception {
        this.originalUseCompactVertexFormat =
                CeleritasVintage.options().performance.useCompactVertexFormat;
        setGuardReported(false);
    }

    @AfterEach
    void restoreGuardAndOption() throws Exception {
        CeleritasVintage.options().performance.useCompactVertexFormat =
                this.originalUseCompactVertexFormat;
        setGuardReported(false);
    }

    @Test
    void shaderPackInUseSelectsVanillaLikeLegacyFormatWhenCompactOptionIsOff() {
        CeleritasVintage.options().performance.useCompactVertexFormat = false;

        ChunkVertexType selected = CeleritasWorldRenderer.chooseVertexType(() -> true);

        assertNotSame(ChunkMeshFormats.TERRAIN_A16, selected);
        assertSame(Boolean.getBoolean("celtium.terrain.forceCompactFormat")
                ? ChunkMeshFormats.COMPACT
                : ChunkMeshFormats.VANILLA_LIKE, selected);
    }

    @Test
    void shaderPackInUseSelectsCompactLegacyFormatWhenCompactOptionIsOn() {
        CeleritasVintage.options().performance.useCompactVertexFormat = true;

        ChunkVertexType selected = CeleritasWorldRenderer.chooseVertexType(() -> true);

        assertNotSame(ChunkMeshFormats.TERRAIN_A16, selected);
        assertSame(ChunkMeshFormats.COMPACT, selected);
    }

    @Test
    void guardLogIsPrintedOnceForRepeatedShaderPackSelections() {
        CeleritasVintage.options().performance.useCompactVertexFormat = true;

        String output = captureStdout(() -> {
            assertSame(ChunkMeshFormats.COMPACT,
                    CeleritasWorldRenderer.chooseVertexType(() -> true));
            assertSame(ChunkMeshFormats.COMPACT,
                    CeleritasWorldRenderer.chooseVertexType(() -> true));
        });

        assertEquals(1, occurrences(output, "CELTIUM_TERRAIN_A16_GUARD"));
    }

    @Test
    void noShaderPackKeepsTerrainA16SelectionAndPrintsNoGuardLog() {
        String output = captureStdout(() ->
                assertSame(ChunkMeshFormats.TERRAIN_A16,
                        CeleritasWorldRenderer.chooseVertexType(() -> false)));

        assertFalse(output.contains("CELTIUM_TERRAIN_A16_GUARD"),
                "the guard must not report or alter selection when no shader pack is in use: " + output);
    }

    private static void setGuardReported(boolean reported) throws Exception {
        Field field = CeleritasWorldRenderer.class.getDeclaredField("terrainA16GuardReported");
        field.setAccessible(true);
        field.setBoolean(null, reported);
    }

    private static String captureStdout(Runnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
