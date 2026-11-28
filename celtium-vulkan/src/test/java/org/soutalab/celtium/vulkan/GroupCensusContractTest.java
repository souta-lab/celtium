package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The group census's bucket boundary, pinned at the layers a CPU gate can reach.
 * <p>
 * The census classifies every drawn group into one of five families (texture, lit item, unlit item,
 * colour, other) and four blend/depth-write states, and counts it at
 * {@code family * 4 + (blends ? 2 : 0) + (depthWrites ? 1 : 0)} - an index that reaches 19. The
 * bucket array was sized 12 (the three-family era), so the first colour-family group through the
 * drain threw {@code ArrayIndexOutOfBoundsException} inside the flush, the flush was abandoned,
 * and every later frame retried into the same throw: with {@code -Dceltium.debug.groupCensus=true}
 * the immediate drain wedged permanently (stage-4 legs VS/HUD, 2026-11-27). The production path
 * never paid it - the census flag is the trigger - but a measuring instrument that breaks the
 * thing it measures is worse than none. These tests pin the array against the classification's
 * own arithmetic and the two print sites' label tables against the same five families, read as
 * source in the shape the sibling contract tests established.
 */
class GroupCensusContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
    private static final String FRAME_STATS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java";

    /** Families in the classification: texture, litItem, unlitItem, colour, other. */
    private static final int FAMILIES = 5;
    /** States per family: (blends ? 2 : 0) + (depthWrites ? 1 : 0) spans 0..3. */
    private static final int STATES = 4;

    @Test
    @DisplayName("the bucket array covers the classification's largest index")
    void bucketArrayCoversTheClassification() throws IOException {
        String backend = read(BACKEND);
        Matcher declaration = Pattern.compile("groupCensusCounts = new long\\[(\\d+)\\]").matcher(backend);
        assertTrue(declaration.find(), "the census bucket array is declared with a literal length");
        int buckets = Integer.parseInt(declaration.group(1));
        int largestIndex = (FAMILIES - 1) * STATES + (STATES - 1);
        assertEquals(FAMILIES * STATES, buckets,
                "five families x four states is the whole classification, no more and no less");
        assertTrue(largestIndex < buckets,
                "the largest classification index " + largestIndex + " stays inside the array");
    }

    @Test
    @DisplayName("the counting site keeps the family-times-four index the boundary is computed from")
    void countingSiteKeepsTheIndexForm() throws IOException {
        assertTrue(read(BACKEND).contains(
                        "this.groupCensusCounts[family * 4 + (blends ? 2 : 0) + (depthWrites ? 1 : 0)]++;"),
                "the drain counts at family * 4 + blend bit + depth-write bit");
    }

    @Test
    @DisplayName("the classification still returns the five families the buckets are sized for")
    void classificationKeepsFiveFamilies() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("censusPipeline instanceof VulkanImmediateTextureRecordsPipeline ? 0"),
                "family 0 is the texture pipeline");
        assertTrue(backend.contains("? (itemState2.lit() ? 1 : 2)"),
                "families 1 and 2 are the lit and unlit item pipelines");
        assertTrue(backend.contains(": censusPipeline instanceof VulkanImmediateColourRecordsPipeline ? 3 : 4;"),
                "family 3 is the colour pipeline and family 4 is everything else");
    }

    @Test
    @DisplayName("both print sites name all five families and derive family and state from the bucket index")
    void printSitesNameAllFiveFamilies() throws IOException {
        for (String source : new String[] {read(BACKEND), read(FRAME_STATS)}) {
            assertTrue(source.contains("{\"texture\", \"litItem\", \"unlitItem\", \"colour\", \"other\"}"),
                    "the label table names the five families the counting side can produce");
            assertTrue(source.contains("int family = i / 4;") && source.contains("int state = i % 4;"),
                    "the label derivation matches the counting side's family * 4 + state packing");
        }
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
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
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }
}
