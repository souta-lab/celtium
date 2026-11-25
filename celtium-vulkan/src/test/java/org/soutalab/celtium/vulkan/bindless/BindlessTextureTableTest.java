package org.soutalab.celtium.vulkan.bindless;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindlessTextureTableTest {

    @Test
    void firstRegistrationsTakeTheLowestSlots() {
        BindlessTextureTable table = new BindlessTextureTable();
        int a = table.register(100);
        int b = table.register(200);
        assertEquals(0, table.slotOf(a));
        assertEquals(1, table.slotOf(b));
        assertEquals(2, table.size());
        assertEquals(100, table.glIdAt(0));
    }

    @Test
    void registeringTheSameTextureTwiceReturnsTheSameHandle() {
        BindlessTextureTable table = new BindlessTextureTable();
        assertEquals(table.register(42), table.register(42));
        assertEquals(1, table.size());
    }

    @Test
    void releasedSlotsAreReusedWithAFreshGeneration() {
        BindlessTextureTable table = new BindlessTextureTable();
        int first = table.register(100);
        int slot = table.slotOf(first);
        table.release(100);
        assertEquals(-1, table.slotOf(first));
        int second = table.register(200);
        assertEquals(slot, table.slotOf(second));
        assertNotEquals(first, second);
        // The stale handle must not resolve to the reused slot.
        assertEquals(-1, table.slotOf(first));
        assertEquals(second, table.handleOf(200));
    }

    @Test
    void unknownHandlesAndNamesDoNotResolve() {
        BindlessTextureTable table = new BindlessTextureTable();
        assertEquals(-1, table.slotOf(BindlessTextureTable.INVALID_HANDLE));
        assertEquals(-1, table.slotOf(0));
        assertEquals(BindlessTextureTable.INVALID_HANDLE, table.handleOf(999));
        table.release(999);
        assertEquals(0, table.size());
    }

    @Test
    void fullTableRefusesNewTexturesButKeepsExistingOnes() {
        BindlessTextureTable table = new BindlessTextureTable();
        int last = BindlessTextureTable.INVALID_HANDLE;
        for (int glId = 1; glId <= BindlessTextureTable.CAPACITY; glId++) {
            last = table.register(glId);
            assertTrue(last != BindlessTextureTable.INVALID_HANDLE, "glId " + glId);
        }
        assertEquals(BindlessTextureTable.CAPACITY, table.size());
        assertEquals(BindlessTextureTable.INVALID_HANDLE,
                table.register(BindlessTextureTable.CAPACITY + 1));
        assertEquals(last, table.handleOf(BindlessTextureTable.CAPACITY));
    }
}
