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
 * protocol. Since option C (OVERHAUL-BUDGET-MESH-STARVATION-DIAGNOSIS-2026-11-27) the mesh side judges
 * against its own {@link MeshUploadBudget}, charged at issue time; the judgment half is checked against
 * the real account through the backend's static judgment functions, and the wiring half is pinned on
 * the sources, because the producer and the drain live on the terrain backend, which needs a device
 * to instantiate.
 */
class UploadBudgetLayer2Test {

    private static final long CAP = UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES;

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
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        assertFalse(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 600L));
        assertEquals(0L, account.deferredCount());
        assertEquals(0L, account.deferredOutstandingBytes());
    }

    /** A burst that would take the window over the budget is deferred and accounted. */
    @Test
    void burstOverBudgetDefersAndAccounts() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        account.noteIssued(7L, 800L);
        assertTrue(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 300L));
        assertEquals(300L, account.deferredOutstandingBytes());
        assertEquals(300L, account.deferredTotalBytes());
        assertEquals(1L, account.deferredCount());
    }

    /** When the deferred cap refuses, the arrival is issued now and the refusal is the counted fact. */
    @Test
    void capRefusalForcesAnImmediateIssue() {
        MeshUploadBudget account = new MeshUploadBudget(100L, 50L);
        account.noteIssued(7L, 90L);
        assertFalse(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 60L));
        assertEquals(1L, account.deferredCapForce());
        assertEquals(0L, account.deferredOutstandingBytes());
        assertEquals(0L, account.deferredCount());
    }

    /** An unlimited budget (the property's 0) never defers, whatever arrives. */
    @Test
    void unlimitedBudgetNeverDefers() {
        MeshUploadBudget account = new MeshUploadBudget(0L, CAP);
        assertFalse(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 1L << 30));
        assertEquals(0L, account.deferredCount());
    }

    /**
     * The option-C behavior the shared ledger could not produce: a burst issued now charges the
     * window at arrival time, so a second burst in the same frame - small enough to pass on its
     * own - is deferred on the first burst's charge.
     */
    @Test
    void arrivalChargingMakesALaterBurstInTheSameFrameDefer() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        assertFalse(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 600L),
                "the first burst fits and is issued now");
        account.noteIssued(7L, 600L);
        assertTrue(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 500L),
                "the second burst is judged against the first burst's charge");
        assertEquals(500L, account.deferredOutstandingBytes());
    }

    // ------------------------------------------------------------------ drain judgment

    /** The head entry issues while the window has room for it, and waits once the room is gone. */
    @Test
    void drainHeadFollowsTheWindowsRemainingRoom() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        account.noteIssued(7L, 800L);
        assertTrue(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(account, 7L, 800L, 0L, 200L));
        assertFalse(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(account, 7L, 800L, 0L, 201L));
    }

    /**
     * The starvation escape: an entry heavier than the whole budget, facing a window that has carried
     * nothing, is issued once - otherwise it would wait on a window only its own issue could fill.
     * The escape closes as soon as anything has flowed, in the window or in this drain.
     */
    @Test
    void starvationEscapeIssuesAnOversizedHeadOnce() {
        MeshUploadBudget account = new MeshUploadBudget(100L, CAP);
        assertTrue(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(account, 9L, 0L, 0L, 250L),
                "an empty window issues its oversized head");
        assertFalse(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(account, 9L, 0L, 250L, 250L),
                "the escape fires once per drain, not once per entry");
        assertFalse(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(account, 9L, 1L, 0L, 250L),
                "a window that has carried anything is not starving");
    }

    /** The full round trip: defer under one frame, drain under the next, the account returns to 0. */
    @Test
    void deferThenDrainRoundTripSettlesTheAccount() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        account.noteIssued(7L, 800L);
        assertTrue(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 300L));
        assertEquals(300L, account.deferredOutstandingBytes());
        // The next frame's window has been charged nothing yet, so the entry fits it outright.
        assertTrue(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(
                account, 8L, account.windowBytes(8L), 0L, 300L));
        account.noteIssued(8L, 300L);
        account.noteDeferredIssued(300L);
        assertEquals(0L, account.deferredOutstandingBytes());
        assertEquals(300L, account.deferredTotalBytes(), "the cumulative totals survive the round trip");
        assertEquals(300L, account.windowBytes(8L), "the drain's issue is charged to the new window");
    }

    /**
     * The drain charges its own issues as it goes: after the first head is issued, the second head
     * is judged against the charged window and waits, instead of both spending the same room.
     */
    @Test
    void drainChargesItsIssuesAgainstLaterHeads() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        account.noteIssued(7L, 800L);
        assertTrue(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 300L));
        assertTrue(VulkanTerrainGpuBackend.celtium$tryDeferBurstArrival(account, 7L, 800L));
        // Frame 8: the first head fits the fresh window and is issued; its charge lands at once.
        assertTrue(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(
                account, 8L, account.windowBytes(8L), 0L, 300L));
        account.noteIssued(8L, 300L);
        account.noteDeferredIssued(300L);
        assertFalse(VulkanTerrainGpuBackend.celtium$deferredEntryIssuable(
                account, 8L, account.windowBytes(8L), 300L, 800L),
                "the second head waits: the first issue already spent 300 of the window");
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

    /** (b) The drain supplies the key, asks the mesh account, charges its issues and settles them. */
    @Test
    void drainAndProducerAreWiredToTheMeshAccount() throws IOException {
        String backend = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertTrue(backend.contains("this.context.celtium$supplyUploadBudgetFrameKey(this.frameId)"),
                "the drain is the frame's definition point for the key");
        assertTrue(backend.contains("celtium$tryDeferBurstArrival(account, frameKey, weight)"),
                "the producer judgment asks the mesh account with the batch weight");
        assertTrue(backend.contains("account.noteIssued(frameKey, weight)"),
                "a burst issued now is charged to the mesh window at arrival time");
        assertTrue(backend.contains("celtium$deferredEntryIssuable(account, frameKey, account.windowBytes(frameKey),"),
                "the drain judges its head entry against the mesh window's own charges");
        assertTrue(backend.contains("account.noteIssued(frameKey, entry.bytes)"),
                "a drained entry's issue is charged to the mesh window");
        assertTrue(backend.contains("account.noteDeferredIssued(entry.bytes)"),
                "an issued entry leaves the backlog's account");
        assertTrue(backend.contains("celtium$meshUploadBudget().noteDeferredIssued(droppedBytes)"),
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

    /** (d) The summary carries the mesh side's own deferred counters, not a subtraction. */
    @Test
    void meshCountersAreInTheSummary() throws IOException {
        String diagnostics = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java"));
        assertTrue(diagnostics.contains("meshUploadBudgetBytes="),
                "the summary names the resolved mesh budget");
        assertTrue(diagnostics.contains("meshUploadDeferredBytes="),
                "the summary carries the mesh side's deferred bytes");
        assertTrue(diagnostics.contains("meshUploadDeferredCount="),
                "the summary carries the mesh side's deferred count");
        assertTrue(diagnostics.contains("meshUploadDeferredCapForce="),
                "the summary carries the mesh side's cap refusals");
        String backend = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertTrue(backend.contains("celtium$meshUploadBudgetOrNull()"),
                "the diagnostics read the account without resolving it");
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
