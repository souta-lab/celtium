package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.terrain.AsyncSectionMesher;
import org.soutalab.celtium.vulkan.terrain.TerrainResidencyGate;
import org.soutalab.celtium.vulkan.terrain.TerrainSectionStore;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Condition (4) of the T3 sharing design (section 5.2): the quiescence +
 * completion gate at {@link TerrainEngineAHost#delete}'s door, the paired
 * reset behind it, and the world epoch the executors are stamped with.
 * <p>
 * The host is pure CPU state, so the gate is exercised directly against an
 * {@link Unsafe}-allocated context whose submission state is set field by
 * field - the same idiom as {@link FlushPendingBeforeGlDrawTest}. The
 * invariants pinned are the review's: INV-4a (a refused delete destroys
 * nothing), INV-4b (a passed delete leaves the post-state total), INV-4c
 * (an executor of a dead epoch does not run). The settle-side ordering
 * (M4) is device-bound, so it is pinned as a source contract.
 */
class TerrainHostQuiescenceGateTest {

    private static final String BACKEND_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String HOST_SOURCE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";

    private static Unsafe unsafe() throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /**
     * A context with an empty submission stream: no probe (no recording
     * open), an empty pending list, no timeline (the fence path) with zero
     * deferred submissions outstanding, and an empty retire list. Every
     * gate input starts at "quiescent"; a test moves exactly one.
     */
    private static VulkanContext fakeContext() throws Exception {
        VulkanContext context = (VulkanContext) unsafe().allocateInstance(VulkanContext.class);
        setField(context, "pendingBatchedSubmits", new ArrayList<>());
        setField(context, "retiredSlabBuffers", new ArrayList<>());
        return context;
    }

    private static BuiltSectionMeshParts parts(int quads) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * TerrainVertex16.STRIDE);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quads * 4));
        return new BuiltSectionMeshParts(vertices, null, null, ranges, null);
    }

    private static TerrainEngineAHost hostWithOneSection() {
        TerrainEngineAHost host = new TerrainEngineAHost();
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        BuiltSectionMeshParts source = parts(2);
        assertNotNull(host.store().register(mesher.build(1, 2, 3, 0, source, 0), 1L, 1L));
        source.vertexBuffer().free();
        return host;
    }

    @Test
    void deleteRefusedWhileRecordingIsOpen() throws Exception {
        TerrainEngineAHost host = hostWithOneSection();
        VulkanContext context = fakeContext();
        context.celtium$setRecordingOpenProbe(() -> true);
        assertTrue(context.celtium$recordingOpen());

        assertFalse(host.delete(context),
                "an open recording refuses the teardown: its buffers may still be named by it");
        assertEquals(1, host.store().recordCount(), "INV-4a: nothing was destroyed");
        assertTrue(host.store().geometryAllocator().liveBytes() > 0,
                "INV-4a: the ledgers are untouched");
        assertEquals(0, host.worldEpoch(), "the epoch does not advance on a refusal");
    }

    @Test
    void deleteRefusedWhileBatchedSubmitsArePending() throws Exception {
        TerrainEngineAHost host = hostWithOneSection();
        VulkanContext context = fakeContext();
        List pending = (List) getField(context, "pendingBatchedSubmits");
        pending.add(unsafe().allocateInstance(
                Class.forName("org.soutalab.celtium.vulkan.VulkanContext$DeferredSubmit")));
        assertTrue(context.celtium$hasPendingBatchedSubmits(),
                "the queued entry is exactly what the new query reports");

        assertFalse(host.delete(context),
                "a recorded-but-unsubmitted batch refuses the teardown");
        assertEquals(1, host.store().recordCount(), "INV-4a: nothing was destroyed");
        assertEquals(0, host.worldEpoch(), "the epoch does not advance on a refusal");
    }

    @Test
    void deleteRefusedWhileASubmissionIsUnconfirmed() throws Exception {
        TerrainEngineAHost host = hostWithOneSection();
        VulkanContext context = fakeContext();
        setField(context, "deferredSubmitsOutstanding", 1);
        assertFalse(context.celtium$isSubmissionCompleted(),
                "on the fence path an outstanding deferred submission is unconfirmed work");

        assertFalse(host.delete(context),
                "an unconfirmed submission refuses the teardown");
        assertEquals(1, host.store().recordCount(), "INV-4a: nothing was destroyed");
        assertEquals(0, host.worldEpoch(), "the epoch does not advance on a refusal");
    }

    @Test
    void deleteRunsThePairedResetWhenQuiescent() throws Exception {
        TerrainEngineAHost host = hostWithOneSection();
        // Accompanying state the reset must clear with the epoch: a residency
        // expectation and a miss recorded against the old world.
        TerrainSectionStore.Key missing = new TerrainSectionStore.Key(7, 7, 7, 0);
        host.residencyGate().expect(missing);
        host.residencyGate().noteMissing(missing, TerrainResidencyGate.Reason.ARENA_FULL);
        VulkanContext context = fakeContext();
        assertTrue(context.celtium$isSubmissionCompleted());

        assertTrue(host.delete(context), "a quiescent, completed stream passes the gate");

        assertEquals(0, host.store().recordCount(), "INV-4b: no record survives the reset");
        assertEquals(0, host.store().geometryAllocator().liveBytes(), "INV-4b: every ledger is empty");
        assertEquals(0, host.store().quadSpaceAllocator().liveBytes());
        assertEquals(0, host.store().factorAllocator().liveBytes());
        assertEquals(0, host.store().sortedAllocator().liveBytes());
        assertEquals(1, host.worldEpoch(), "the epoch advanced exactly once");
        assertTrue(host.residencyGate().verify(0, host.store()).complete(),
                "the gate's expectations left with the epoch");

        // A repeated delete is a harmless no-op, not a second reset.
        assertTrue(host.delete(context), "a repeated delete still reports the epoch as torn down");
        assertEquals(1, host.worldEpoch(), "a repeated delete does not advance the epoch again");

        // The store serves the next epoch: the same section registers whole.
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        BuiltSectionMeshParts source = parts(2);
        assertNotNull(host.store().register(mesher.build(1, 2, 3, 0, source, 0), 2L, 2L));
        assertEquals(1, host.store().recordCount());
        source.vertexBuffer().free();
    }

    @Test
    void enginesOfADeadEpochRefuseToRun() throws Exception {
        TerrainEngineAHost host = new TerrainEngineAHost();
        long deadEpoch = host.worldEpoch() + 1;

        TerrainEngineA vanilla = (TerrainEngineA) unsafe().allocateInstance(TerrainEngineA.class);
        setField(vanilla, "host", host);
        setField(vanilla, "worldEpoch", deadEpoch);
        assertFalse(vanilla.prepareFrame(null, 0, null, 0, 0, 0, 0L),
                "INV-4c: a dead epoch's executor prepares nothing");

        TerrainPackEngine pack = (TerrainPackEngine) unsafe().allocateInstance(TerrainPackEngine.class);
        setField(pack, "host", host);
        setField(pack, "worldEpoch", deadEpoch);
        assertNull(pack.register(null, 0L),
                "INV-4c: a dead epoch's pack executor registers nothing - the guard fires before the build is read");
        assertFalse(pack.prepareFrame(null, 0, null, 0, 0, 0, 0L),
                "INV-4c: a dead epoch's pack executor prepares nothing");
    }

    @Test
    void settleAndTeardownOrderingIsPinned() throws IOException {
        String backend = readSource(BACKEND_SOURCE);

        // M4: inside settleGpu, the finish and the flush stand before the
        // device wait - the wait only confirms what has been submitted.
        String settle = backend.substring(backend.indexOf("private boolean settleGpu(String reason)"));
        int finish = settle.indexOf("celtium$finishFrameBuffer()");
        int flush = settle.indexOf("celtium$flushBatchedSubmits()");
        int wait = settle.indexOf("vkDeviceWaitIdle");
        assertTrue(finish >= 0, "settleGpu finishes an open frame buffer");
        assertTrue(flush > finish, "settleGpu flushes after finishing");
        assertTrue(wait > flush, "the flush stands before vkDeviceWaitIdle");

        // The world teardown composes its gate at the head, before any
        // destruction, and resets the armed frame stamp with the cache.
        String release = backend.substring(
                backend.indexOf("public void celtium$releaseWorldResources()"));
        int gate = release.indexOf("celtium$terrainQuiescent()");
        int hostDelete = release.indexOf("terrainAHost().delete(this.context)");
        int armedReset = release.indexOf("this.terrainAArmedFrame = -1L");
        assertTrue(gate >= 0, "releaseWorldResources composes the quiescence gate");
        assertTrue(hostDelete > gate, "the gate stands before the host teardown");
        assertTrue(armedReset > gate, "the armed frame stamp is reset as part of the teardown");

        // The host verifies the gate before the paired reset runs: the
        // refusal (and its log) precedes the store reset and the epoch
        // advance in delete's own body.
        String host = readSource(HOST_SOURCE);
        String delete = host.substring(host.indexOf("boolean delete(VulkanContext context)"));
        int refusal = delete.indexOf("CELTIUM_TERRAIN_A_DELETE_REFUSED");
        int reset = delete.indexOf("resetForNewEpoch()");
        int advance = delete.indexOf("this.worldEpoch++");
        assertTrue(refusal >= 0, "a refused delete logs itself");
        assertTrue(reset > refusal, "the gate stands before the paired reset");
        assertTrue(advance > reset, "the epoch advances inside the reset, after the gate");
    }

    private static String readSource(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
    }

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
