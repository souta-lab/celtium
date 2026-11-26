package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3 stage 1 (empty-copy removal, 2026-11-26): a vanilla executor must not
 * materialize a device slab buffer for a slab its own pass holds no records
 * in. The store-wide slab counts armed every slab any pass had records in;
 * the cull shader filters by (pass, slab), so such a slab produced zero
 * counts and zero draws for this pass, yet the draw bind lazily created its
 * 64 MiB device buffer — an empty copy. The executor arms from its own pass'
 * counts (whose behaviour is pinned in {@code TerrainSectionStoreTest});
 * since T3 condition (2) those counts are the frame snapshot's frozen
 * per-pass counts (M2 — a single combined array would regress this
 * isolation), read at the same seat. The device side needs a Vulkan
 * context, so the wiring is pinned here as source contracts, in the style
 * of {@link PackLiveWorkGateTest}.
 */
class TerrainEmptySlabCopyContractTest {

    private static final String ENGINE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java";

    @Test
    void prepareFrameArmsFromPerPassCounts() throws IOException {
        String source = readSource(ENGINE);
        assertTrue(source.contains(
                        "int[] slabCounts = snapshot.vanillaSlabCounts(this.passOrdinal);"),
                "prepareFrame arms from the snapshot's frozen counts for this pass alone (M2)");
        assertFalse(source.contains("this.store.slabRecordCounts"),
                "no live store count arms a pass: the store-wide form counts other passes'"
                        + " records, and even the live per-pass form would split the recording"
                        + " across generations");
    }

    @Test
    void slabBuffersMaterializeOnlyAtUploadAndDrawBind() throws IOException {
        String source = readSource(ENGINE);
        assertTrue(source.contains("SlabBuffer slab = slabBuffer(registration.geometry.slabId);"),
                "upload materializes the slab its registration just landed in (never an empty copy)");
        assertTrue(source.contains("SlabBuffer slab = slabBuffer(s);"),
                "the draw bind materializes armed slabs");
        int sites = source.split("slabBuffer\\(", -1).length - 1;
        assertEquals(3, sites,
                "slabBuffer( appears at the definition and exactly those two call sites;"
                        + " a new materialization site must be added here consciously");
    }

    @Test
    void drawsBindOnlyArmedSlabs() throws IOException {
        String source = readSource(ENGINE);
        assertTrue(source.contains("if (!slot.armed[s]) {"),
                "recordDraws binds only armed slabs, so an unarmed slab's buffer is never created");
    }

    /**
     * Reads a main source file, normalised to LF: the pinned shapes span
     * lines, and a CRLF checkout (the Windows default) must pin the same
     * shapes as an LF one.
     */
    private static String readSource(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
