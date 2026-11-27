package org.taumc.celeritas.impl.render.terrain;

import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the selection seam after the R-18 guard removal (landed with the R-17
 * candidate 2-MIN form).
 */
class CeleritasWorldRendererA16GuardTest {

    @Test
    void shaderPackInUseSelectsTerrainA16() {
        ChunkVertexType selected = CeleritasWorldRenderer.chooseVertexType(() -> true);

        assertSame(ChunkMeshFormats.TERRAIN_A16, selected);
    }

    @Test
    void guardLogIsNeverPrintedForRepeatedShaderPackSelections() {
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
