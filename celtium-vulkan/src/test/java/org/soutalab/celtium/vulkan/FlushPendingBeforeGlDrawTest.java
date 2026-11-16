package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VulkanTerrainGpuBackend#flushPendingBeforeGlDraw()} must flush pre-closed groups in
 * {@code immediateGroups} even when no mesh, geometry lease, or font batch is currently open.
 * <p>
 * Callers such as retained submissions, model replay, and framebuffer compositing close/queue groups directly into
 * {@code immediateGroups} while leaving {@code currentImmediateMesh == null} and {@code currentGeometryLease == null}.
 */
class FlushPendingBeforeGlDrawTest {

    private static VulkanTerrainGpuBackend newBackendForFlushTest() throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);
        VulkanTerrainGpuBackend backend =
                (VulkanTerrainGpuBackend) unsafe.allocateInstance(VulkanTerrainGpuBackend.class);

        setField(backend, "targetRegistry", new RenderTargetRegistry());
        setField(backend, "immediateStatistics", new ImmediateStatistics());
        setField(backend, "fontBatch", new FontBatch());
        setField(backend, "immediateGroups", new ArrayList<ImmediateGroup>());
        return backend;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = VulkanTerrainGpuBackend.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void flushesQueuedGroupEvenWhenNoOpenMeshOrLeaseExists() throws Exception {
        VulkanTerrainGpuBackend backend = newBackendForFlushTest();
        backend.targetRegistry.noteReplacementAnnounced();
        ImmediateGroup queued = ImmediateGroup.obtain(
                null, new float[16], new float[16], VulkanImmediateFormats.ITEM, 7, 11L, null, 0L);
        queued.batches = 3;
        backend.immediateGroups.add(queued);

        backend.flushPendingBeforeGlDraw();

        assertEquals(1L, backend.flushBeforeGlDrawCalls);
        assertEquals(1L, backend.flushBeforeGlDrawWithGroups,
                "flushPendingBeforeGlDraw must not return early while immediateGroups has queued work");
        assertTrue(backend.immediateGroups.isEmpty(),
                "queued immediate group must be processed rather than left stranded for menu frame boundary drop");
        assertEquals(3, backend.immediateStatistics.postAcceptDroppedGroups());
    }

    @Test
    void returnsWithoutWorkWhenNothingIsOpenAndQueueIsEmpty() throws Exception {
        VulkanTerrainGpuBackend backend = newBackendForFlushTest();
        backend.targetRegistry.noteReplacementAnnounced();

        backend.flushPendingBeforeGlDraw();

        assertEquals(1L, backend.flushBeforeGlDrawCalls);
        assertEquals(0L, backend.flushBeforeGlDrawWithGroups);
        assertEquals(0, backend.immediateStatistics.postAcceptDroppedGroups());
    }
}
