package org.taumc.celeritas.impl.render.terrain;

import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.CeleritasVintage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the selection seam after the R-18 guard removal (landed with the R-17
 * candidate 2-MIN form).
 *
 * <p>The guard this class used to pin held {@code TERRAIN_A16} back while a
 * shader pack was in use, on the premise that the Tier-A stream carried no
 * Demonica auxiliary attributes. Under 2-MIN a pack's own presented format
 * registers with the Tier-A machinery as a format variant, so the premise is
 * gone: selection no longer consults the shader state at all, and the guard
 * log line is never printed. The seam itself still cannot be observed in a
 * real fixture run (with Demonica present its mixin takes over
 * {@code chooseVertexType()}), so the package-private overload remains the
 * test surface, with the shader state supplied directly.
 */
class CeleritasWorldRendererA16GuardTest {
    private boolean originalUseCompactVertexFormat;

    @BeforeEach
    void saveOption() {
        this.originalUseCompactVertexFormat =
                CeleritasVintage.options().performance.useCompactVertexFormat;
    }

    @AfterEach
    void restoreOption() {
        CeleritasVintage.options().performance.useCompactVertexFormat =
                this.originalUseCompactVertexFormat;
    }

    @Test
    void shaderPackInUseSelectsTerrainA16WhenCompactOptionIsOff() {
        CeleritasVintage.options().performance.useCompactVertexFormat = false;

        ChunkVertexType selected = CeleritasWorldRenderer.chooseVertexType(() -> true);

        assertSame(ChunkMeshFormats.TERRAIN_A16, selected);
    }

    @Test
    void shaderPackInUseSelectsTerrainA16WhenCompactOptionIsOn() {
        CeleritasVintage.options().performance.useCompactVertexFormat = true;

        ChunkVertexType selected = CeleritasWorldRenderer.chooseVertexType(() -> true);

        assertSame(ChunkMeshFormats.TERRAIN_A16, selected);
    }

    @Test
    void guardLogIsNeverPrintedForRepeatedShaderPackSelections() {
        CeleritasVintage.options().performance.useCompactVertexFormat = true;

        String output = captureStdout(() -> {
            assertSame(ChunkMeshFormats.TERRAIN_A16,
                    CeleritasWorldRenderer.chooseVertexType(() -> true));
            assertSame(ChunkMeshFormats.TERRAIN_A16,
                    CeleritasWorldRenderer.chooseVertexType(() -> true));
        });

        assertEquals(0, occurrences(output, "CELTIUM_TERRAIN_A16_GUARD"));
    }

    @Test
    void noShaderPackKeepsTerrainA16SelectionAndPrintsNoGuardLog() {
        String output = captureStdout(() ->
                assertSame(ChunkMeshFormats.TERRAIN_A16,
                        CeleritasWorldRenderer.chooseVertexType(() -> false)));

        assertFalse(output.contains("CELTIUM_TERRAIN_A16_GUARD"),
                "no guard remains to report: " + output);
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
