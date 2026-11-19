package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit F1 (ITEM425 reload-lifetime audit): the mesh memo is a second borrow path into the table's meshes, and a
 * teardown release must close it too. Before the fix, {@code releaseAfterWorldTeardown} and {@code releaseAll}
 * released every mesh and cleared the table but left the memo populated, so the next world's first replay - which
 * arrives before the first terrain pass, hence before any build - borrowed a released mesh whose buffers were
 * already destroyed. A released mesh keeps its vertex/index counts ({@code delete()} does not zero them), so the
 * replay's empty check did not refuse it.
 */
class ModelGeometryCacheTeardownTest {

    @Test void worldTeardownReleaseIsNotBorrowableAgain() throws Exception {
        VulkanDynamicMesh mesh = mesh();
        ModelGeometryCache cache = cacheWith(7, mesh);
        assertSame(mesh, cache.meshFor(7), "the lookup primes the memo before the teardown");

        cache.releaseAfterWorldTeardown();

        assertTrue(mesh.releasedAfterIdle(), "the teardown really released the mesh");
        assertNull(cache.meshFor(7),
                "a released mesh must not come back through the memo after world teardown");
    }

    @Test void fullTeardownReleaseIsNotBorrowableAgain() throws Exception {
        VulkanDynamicMesh mesh = mesh();
        ModelGeometryCache cache = cacheWith(7, mesh);
        assertSame(mesh, cache.meshFor(7), "the lookup primes the memo before the teardown");

        cache.releaseAll();

        assertTrue(mesh.releasedAfterIdle(), "the full teardown really released the mesh");
        assertNull(cache.meshFor(7),
                "a released mesh must not come back through the memo after full teardown");
    }

    @Test void slottedMemoEntriesAreDroppedToo() throws Exception {
        VulkanDynamicMesh first = mesh();
        VulkanDynamicMesh second = mesh();
        ModelGeometryCache cache = cacheWith(7, first);
        addToTable(cache, 9, second);
        assertSame(first, cache.meshFor(7));
        assertSame(second, cache.meshFor(9));
        assertSame(first, cache.meshFor(7), "the 64-slot memo answers for the earlier list");

        cache.releaseAfterWorldTeardown();

        assertNull(cache.meshFor(7), "the slotted entry for the first list must not survive the teardown");
        assertNull(cache.meshFor(9), "the slotted entry for the second list must not survive the teardown");
    }

    /** A built mesh's shape: counts set, buffers owned elsewhere. {@code delete()} leaves the counts standing. */
    private static VulkanDynamicMesh mesh() throws Exception {
        VulkanDynamicMesh mesh = (VulkanDynamicMesh) unsafe().allocateInstance(VulkanDynamicMesh.class);
        setField(VulkanDynamicMesh.class, mesh, "indexCount", Integer.valueOf(6));
        setField(VulkanDynamicMesh.class, mesh, "vertexCount", Integer.valueOf(4));
        return mesh;
    }

    private static ModelGeometryCache cacheWith(int listId, VulkanDynamicMesh mesh) throws Exception {
        ModelGeometryCache cache = new ModelGeometryCache();
        it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<VulkanDynamicMesh> table =
                new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<>();
        table.put(listId, mesh);
        setField(ModelGeometryCache.class, cache, "modelMeshes", table);
        return cache;
    }

    @SuppressWarnings("unchecked")
    private static void addToTable(ModelGeometryCache cache, int listId, VulkanDynamicMesh mesh) throws Exception {
        Field field = ModelGeometryCache.class.getDeclaredField("modelMeshes");
        field.setAccessible(true);
        it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<VulkanDynamicMesh> table =
                (it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<VulkanDynamicMesh>) field.get(cache);
        table.put(listId, mesh);
    }

    private static Unsafe unsafe() throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
