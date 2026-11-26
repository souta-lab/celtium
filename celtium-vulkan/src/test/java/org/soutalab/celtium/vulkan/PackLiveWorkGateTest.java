package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.terrain.AsyncSectionMesher;
import org.soutalab.celtium.vulkan.terrain.TerrainSectionStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 6 stage-2 entry condition (v) (2026-11-26): the pack route's entry
 * gates must judge pack work by pack residency, not by legacy presence. The
 * pass entry's early return and the pack route's handoff shortcut both read
 * legacy-side presence (the regions' storage sets, the render lists'
 * geometry), which cannot see the pack space; today that is a faithful
 * proxy because pack sections keep their legacy upload unconditionally, and
 * it stops being one the moment the legacy route is removed (stage 2). The
 * gates therefore also consult the pack executor's live-work peek. The host
 * and the store are pure CPU state, so the peek's behaviour is exercised
 * directly; the device-bound wiring (the two gate conditions, the executor
 * predicate, the vanilla peek's unchanged shape) is pinned as source
 * contracts, in the style of {@link TierALiveWorkGateTest}.
 */
class PackLiveWorkGateTest {

    private static TerrainRenderPass pass(String name) {
        return TerrainRenderPass.builder()
                .name(name)
                .vertexType(ChunkMeshFormats.TERRAIN_A16)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    private static BuiltSectionMeshParts parts(int quads, int stride) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * stride);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        if (quads > 0) {
            ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quads * 4));
        }
        int[] rdh = null;
        if (stride == TerrainVertex16.STRIDE) {
            rdh = new int[quads];
            for (int i = 0; i < quads; i++) {
                rdh[i] = 100 + i;
            }
        }
        return new BuiltSectionMeshParts(vertices, null, null, ranges, rdh);
    }

    // ----------------------------------------------------------------------------------
    // The peek's behaviour (pure CPU state)
    // ----------------------------------------------------------------------------------

    @Test
    void aPassWithNoPackExecutorHasNoPackLiveWork() {
        TerrainEngineAHost host = new TerrainEngineAHost();
        TerrainRenderPass pass = pass("never-offered");
        assertFalse(host.hasPackLiveWork(pass),
                "no pack engine exists for the pass, so neither gate may open on pack work");
        assertNull(host.packEngineIfPresent(pass),
                "the peek must not create the executor it tests for");
    }

    @Test
    void packResidencyIsSeenPerPass() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var pack = store.register(mesher.build(2, 0, 0, 1, parts(3, 44), 0, 44), 2);
        assertNotNull(pack);
        assertTrue(pack.pack(), "the foreign-stride registration is a pack record");
        assertTrue(store.hasPackRecords(1), "the owning pass sees its pack residency");
        assertFalse(store.hasPackRecords(0), "pack residency is per pass, not store-wide");
        assertFalse(store.hasPackRecords(2), "a pass with no pack records reads empty");
    }

    /** Legacy-side presence alone must not read as pack residency: the gates open on pack work only. */
    @Test
    void vanillaResidencyIsNotPackResidency() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var vanilla = store.register(mesher.build(1, 0, 0, 1, parts(2, TerrainVertex16.STRIDE), 0), 1);
        assertNotNull(vanilla);
        assertFalse(vanilla.pack(), "the 16-byte registration is a vanilla record");
        assertFalse(store.hasPackRecords(1),
                "a vanilla record for the same pass is not pack residency");
        assertFalse(store.hasPackRecords(0), "no pack records exist at all");
    }

    @Test
    void packResidencyEndsWithTheRecord() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var pack = store.register(mesher.build(2, 0, 0, 1, parts(3, 44), 0, 44), 2);
        assertNotNull(pack);
        assertTrue(store.hasPackRecords(1));
        store.unregister(new TerrainSectionStore.Key(2, 0, 0, 1), 0);
        assertFalse(store.hasPackRecords(1),
                "once the record is gone the pass no longer holds the entry open");
    }

    // ----------------------------------------------------------------------------------
    // The wiring (source contracts)
    // ----------------------------------------------------------------------------------

    /** The entry refuses a pass only when no route — legacy, vanilla Tier-A, or pack — has anything for it. */
    @Test
    void thePassEntryConsultsPackLiveWorkBeforeRefusing() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertTrue(source.contains(
                        "if (!renderLists.hasPass(pass) && !celtium$tierAHasLiveWork(pass)"
                                + " && !celtium$packHasLiveWork(pass)) {"),
                "the hasPass early return also requires the pack executor to hold no live work for the pass");
    }

    /** The handoff shortcut's "nothing to draw" judgment must see the pack space too. */
    @Test
    void theHandoffShortcutConsultsPackLiveWork() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertTrue(source.contains(
                        "if (handoffSource != 0L && !celtium$renderListsHaveGeometry(renderLists, pass)\n"
                                + "                        && !celtium$packHasLiveWork(pass)) {"),
                "the shortcut returns 'handled, no draw' only when the pack executor also has no live work");
    }

    /** The host's answer is a peek at the existing pack executor, never a creation. */
    @Test
    void theHostPackPeekNeverCreatesAnEngine() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java"));
        int start = source.indexOf("boolean hasPackLiveWork(TerrainRenderPass pass) {");
        assertTrue(start >= 0, "the host exposes the pack live-work peek");
        int end = source.indexOf("\n    }", start);
        String body = source.substring(start, end);
        assertTrue(body.contains("this.packEngines.get(pass)"), "the peek reads the existing pack executor");
        assertTrue(body.contains("engine.hasLiveWork()"), "the peek defers to the executor's own state");
        assertFalse(body.contains("packEngineFor("), "the peek must not create the executor it tests for");
    }

    /** Live pack work is this pass' resident pack records or a pending deferred queue, and a disabled executor has none. */
    @Test
    void thePackExecutorPredicateReadsRecordsDeferredAndDisabled() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainPackEngine.java"));
        int start = source.indexOf("boolean hasLiveWork() {");
        assertTrue(start >= 0, "the pack executor exposes its live-work predicate");
        int end = source.indexOf("\n    }", start);
        String body = source.substring(start, end);
        assertTrue(body.contains("!this.disabled"), "a disabled executor has no live work");
        assertTrue(body.contains("this.store.hasPackRecords(this.passOrdinal)"),
                "resident pack records for this pass are live work");
        assertTrue(body.contains("!this.deferred.isEmpty()"),
                "deferred registrations are live work: prepareFrame is their only way in");
    }

    /**
     * The backend's pack peek carries no stride guard: a pack pass is
     * foreign-format by definition, so the vanilla guard would make the peek
     * permanently false. The pack map is the discriminator instead.
     */
    @Test
    void theBackendPackPeekHasNoStrideGuard() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int start = source.indexOf("private boolean celtium$packHasLiveWork(TerrainRenderPass pass) {");
        assertTrue(start >= 0, "the backend exposes the pack live-work test");
        int end = source.indexOf("\n    }", start);
        String body = source.substring(start, end);
        assertTrue(body.contains("host.hasPackLiveWork(pass)"), "the test defers to the host's pack peek");
        assertFalse(body.contains("STRIDE"), "no stride guard: pack passes are never 16-byte");
        assertFalse(body.contains("terrainAFailed"),
                "the vanilla head's failure flag does not close the pack route's gates");
    }

    /** The vanilla half of the entry judgment is unchanged: its peek keeps the stride guard and the host consult. */
    @Test
    void theVanillaPeekKeepsItsShape() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int start = source.indexOf("private boolean celtium$tierAHasLiveWork(TerrainRenderPass pass) {");
        assertTrue(start >= 0, "the backend still exposes the vanilla live-work test");
        int end = source.indexOf("\n    }", start);
        String body = source.substring(start, end);
        assertTrue(body.contains("this.terrainAFailed"), "the vanilla test still stands down on a failed head");
        assertTrue(body.contains("TerrainVertex16.STRIDE"),
                "the vanilla test still turns non-16-byte passes away");
        assertTrue(body.contains("host.hasLiveWork(pass)"), "the vanilla test still defers to the host peek");
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
