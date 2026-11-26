package org.soutalab.celtium.vulkan;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upload budget's layer 2 (design OVERHAUL-S3-REMAINDER-DESIGN §2.2): the producer-side deferral
 * protocol wired onto the layer-1 ledger. The judgment half is checked against the real
 * {@link UploadBudgetLedger} through the backend's static judgment functions; the wiring half is
 * pinned on the sources, because the producer and the drain live on the terrain backend, which needs
 * a device to instantiate.
 */
class UploadBudgetLayer2Test {

    // ------------------------------------------------------------------ weight

    private static TerrainRenderPass pass(String name) {
        return TerrainRenderPass.builder()
                .name(name)
                .vertexType(ChunkMeshFormats.TERRAIN_A16)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    /** The weight is the real mesh bytes: vertex lengths plus index lengths where an index exists. */
    @Test
    void weightSumsVertexAndIndexBytesAcrossPasses() {
        Reference2ReferenceOpenHashMap<TerrainRenderPass, BuiltSectionMeshParts> meshes =
                new Reference2ReferenceOpenHashMap<>();
        meshes.put(pass("first"), new BuiltSectionMeshParts(new NativeBuffer(100), new NativeBuffer(40),
                null, java.util.Map.of()));
        meshes.put(pass("second"),
                new BuiltSectionMeshParts(new NativeBuffer(50), null, null, java.util.Map.of()));
        ChunkBuildOutput output = new ChunkBuildOutput(null, null, meshes, 0);
        assertEquals(190L, VulkanTerrainGpuBackend.celtium$deferredUploadWeight(output));
    }

    /** Sort outputs carry no built meshes under the weight definition, so they weigh 0. */
    @Test
    void weightOfASortOutputIsZero() {
        Reference2ReferenceOpenHashMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> meshes =
                new Reference2ReferenceOpenHashMap<>();
        meshes.put(pass("sort"), new ChunkSortOutput.SortedMesh(new NativeBuffer(64)));
        ChunkSortOutput output = new ChunkSortOutput(null, 0, meshes);
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$deferredUploadWeight(output));
    }

    // ------------------------------------------------------------------ producer judgment

    /** A burst that fits the window is issued now; nothing is accounted as deferred. */
    @Test
    void burstWithinBudgetIssuesNow() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(1000L, UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES);
        assertFalse(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(ledger, 7L, 600L));
        assertEquals(0L, ledger.deferredCount());
        assertEquals(0L, ledger.deferredOutstandingBytes());
    }

    /** A burst that would take the window over the budget is deferred and accounted. */
    @Test
    void burstOverBudgetDefersAndAccounts() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(1000L, UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES);
        ledger.noteFlushed(7L, 800L);
        assertTrue(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(ledger, 7L, 300L));
        assertEquals(300L, ledger.deferredOutstandingBytes());
        assertEquals(300L, ledger.deferredTotalBytes());
        assertEquals(1L, ledger.deferredCount());
    }

    /** When the deferred cap refuses, the arrival is issued now and the refusal is the counted fact. */
    @Test
    void capRefusalForcesAnImmediateIssue() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(100L, 50L);
        ledger.noteFlushed(7L, 90L);
        assertFalse(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(ledger, 7L, 60L));
        assertEquals(1L, ledger.deferredCapForce());
        assertEquals(0L, ledger.deferredOutstandingBytes());
        assertEquals(0L, ledger.deferredCount());
    }

    /** An unlimited budget (the property's 0) never defers, whatever arrives. */
    @Test
    void unlimitedBudgetNeverDefers() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(0L, UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES);
        assertFalse(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(ledger, 7L, 1L << 30));
        assertEquals(0L, ledger.deferredCount());
    }

    // ------------------------------------------------------------------ drain judgment

    /** The head entry issues while the window has room for it, and waits once the room is gone. */
    @Test
    void drainHeadFollowsTheWindowsRemainingRoom() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(1000L, UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES);
        ledger.noteFlushed(7L, 800L);
        assertTrue(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(ledger, 7L, 800L, 0L, 200L));
        assertFalse(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(ledger, 7L, 800L, 0L, 201L));
    }

    /**
     * The starvation escape: an entry heavier than the whole budget, facing a window that has carried
     * nothing, is issued once - otherwise it would wait on a window only its own issue could fill.
     * The escape closes as soon as anything has flowed, in the window or in this drain.
     */
    @Test
    void starvationEscapeIssuesAnOversizedHeadOnce() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(100L, UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES);
        assertTrue(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(ledger, 9L, 0L, 0L, 250L),
                "an empty window issues its oversized head");
        assertFalse(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(ledger, 9L, 0L, 250L, 250L),
                "the escape fires once per drain, not once per entry");
        assertFalse(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(ledger, 9L, 1L, 0L, 250L),
                "a window that has carried anything is not starving");
    }

    /** The full round trip: defer under one frame, drain under the next, the account returns to 0. */
    @Test
    void deferThenDrainRoundTripSettlesTheAccount() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(1000L, UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES);
        ledger.noteFlushed(7L, 800L);
        assertTrue(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(ledger, 7L, 300L));
        assertEquals(300L, ledger.deferredOutstandingBytes());
        // The next frame's window has carried nothing yet, so its flushed bytes read as 0 and the
        // entry fits the fresh window outright.
        assertTrue(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(ledger, 8L, 0L, 0L, 300L));
        ledger.noteDeferredIssued(300L);
        assertEquals(0L, ledger.deferredOutstandingBytes());
        assertEquals(300L, ledger.deferredTotalBytes(), "the cumulative totals survive the round trip");
    }

    // ------------------------------------------------------------------ wiring pins

    /** (a) The window key is the supplied frame key; the layer-1 derivation remains only as fallback. */
    @Test
    void windowKeyPrefersTheSuppliedFrameKey() throws IOException {
        String context = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java"));
        assertTrue(context.contains("void celtium$supplyUploadBudgetFrameKey(long frameKey)"),
                "the supply entry point exists");
        assertTrue(context.contains("if (this.uploadBudgetFrameKeySupplied)"),
                "the window key prefers the supplied key");
        assertTrue(context.contains("this.frameTimeline.lastAllocatedValue()"),
                "the layer-1 derivation remains as the pre-supply fallback");
        String coordinator = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java"));
        assertEquals(2, countOccurrences(coordinator, "celtium$supplyUploadBudgetFrameKey(this.host.frameId())"),
                "both of the frame's flush points supply the key");
    }

    /** (b) The drain supplies the key, asks the judgment, and settles each issued entry's account. */
    @Test
    void drainAndProducerAreWiredToTheLedger() throws IOException {
        String backend = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertTrue(backend.contains("this.context.celtium$supplyUploadBudgetFrameKey(this.frameId)"),
                "the drain is the frame's definition point for the key");
        assertTrue(backend.contains("celtium$tryDeferBurstArrival(ledger, this.context.celtium$uploadBudgetWindowKey(), weight)"),
                "the producer judgment asks the ledger with the batch weight");
        assertTrue(backend.contains("celtium$deferredEntryIssuable(ledger, frameKey, windowFlushedBytes, issuedBytes, entry.bytes)"),
                "the drain judges its head entry");
        assertTrue(backend.contains("ledger.noteDeferredIssued(entry.bytes)"),
                "an issued entry leaves the backlog's account");
        assertTrue(backend.contains("this.context.celtium$uploadBudget().noteDeferredIssued(droppedBytes)"),
                "a backlog dropped at world teardown settles its account");
    }

    /** (c) The count property is gone; the byte property is the one canonical budget. */
    @Test
    void theCountPropertyIsGone() throws IOException {
        String backend = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertFalse(backend.contains("Integer.getInteger(\"celtium.vulkan.uploadBudget\""),
                "no code reads the count property any more");
        assertFalse(backend.contains("UPLOAD_BUDGET"),
                "the count cap's field and its gates are gone");
        assertTrue(backend.contains("Integer.getInteger(\"celtium.vulkan.uploadBurstThreshold\", 32)"),
                "the burst threshold remains as the arrival-shape gate");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
        return direct;
    }
}
