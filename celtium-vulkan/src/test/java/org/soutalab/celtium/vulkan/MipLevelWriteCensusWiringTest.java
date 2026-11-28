package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source contracts for the mip level write census (R-104): every upload path that records the
 * per-mirror provenance also feeds the census, with the byte count that path actually knows,
 * and the verification summary splices the census in unconditionally - not behind the texture
 * diagnostics flag, which is what left the acceptance leg unable to read it. The mechanism
 * lives inside device-bound methods, so - like {@link TextureUploadWiringContractTest} - these
 * pins are by source shape.
 */
class MipLevelWriteCensusWiringTest {

    private static final String TEXTURE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java";
    private static final String DIAGNOSTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static String window(String text, String anchor, int length) {
        int start = text.indexOf(anchor);
        assertTrue(start >= 0, "anchor is present: " + anchor);
        return text.substring(start, Math.min(text.length(), start + length));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    void thePatchPathNotesEachSuppliedLevelWithItsStagedBytes() throws IOException {
        String texture = read(TEXTURE);
        assertTrue(texture.contains("long[] levelBytes = new long[depth];"),
                "the patch path keeps the per-level byte counts beside the staging offsets");
        assertTrue(texture.contains("levelBytes[level] = bytes;"),
                "each level's staged byte count is captured where the offsets are packed");
        String record = window(texture, "recordPatchWrite(depth, producerDepth", 1200);
        assertTrue(record.contains("MipLevelWriteCensus.noteWrite(level, levelBytes[level])"),
                "the census is fed at the same seat that records the patch provenance");
    }

    @Test
    void wholeImageSeatsNoteTheCopyAndTheRegeneratedChain() throws IOException {
        String texture = read(TEXTURE);
        assertEquals(2, occurrences(texture,
                        "MipLevelWriteCensus.noteWholeImage(pixelWidth, pixelHeight, this.mipLevels,"
                                + " BYTES_PER_PIXEL, bytes)"),
                "both whole-image seats (the direct copy and the service route) note the census");
    }

    @Test
    void singleLevelSeatsNoteExactlyThatLevel() throws IOException {
        String texture = read(TEXTURE);
        assertEquals(2, occurrences(texture, "MipLevelWriteCensus.noteWrite(level, bytes)"),
                "both single-level seats (the direct copy and the service route) note the census");
    }

    @Test
    void theSummaryLineCarriesTheCensusUnconditionally() throws IOException {
        String diagnostics = read(DIAGNOSTICS);
        String fragment = window(diagnostics, "private String textureUploadSummary()", 1600);
        assertTrue(fragment.contains("textureMipLevelWrites=["),
                "the census is spliced into the unconditional texture fragment");
        assertTrue(fragment.contains("MipLevelWriteCensus.summary()"),
                "the fragment reads the census aggregate itself");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
