package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The registry's lookup memo is direct-mapped by GL name, and every mutation of the table used to clear all of it.
 * <p>
 * The change under test is that a mutation of one name clears that name's entry only. The property that makes it sound
 * is the one asserted here: dropping one name leaves every other name's entry exactly as it was, and a name that
 * happens to share the slot is not evicted by a drop of the name that is not in it.
 * <p>
 * No device is needed: the memo is three parallel arrays and the rule is a pure function of them. The image array is
 * carried through the calls because the production rule clears it too, but its contents are not what the rule decides
 * on - the stored id is - so the assertions read the id and generation arrays.
 */
class TextureMemoInvalidationTest {

    private static int[] emptyIds() {
        int[] ids = new int[VulkanTextureRegistry.celtium$memoSlots()];
        Arrays.fill(ids, Integer.MIN_VALUE);
        return ids;
    }

    @Test
    void droppingOneNameLeavesEveryOtherEntryInPlace() {
        int slots = VulkanTextureRegistry.celtium$memoSlots();
        int[] ids = emptyIds();
        VulkanGlTexture[] textures = new VulkanGlTexture[slots];
        int[] generations = new int[slots];

        int first = 7;
        int second = 8;
        int third = 9;
        assertTrue(VulkanTextureRegistry.celtium$memoSlot(first) != VulkanTextureRegistry.celtium$memoSlot(second));
        assertTrue(VulkanTextureRegistry.celtium$memoSlot(second) != VulkanTextureRegistry.celtium$memoSlot(third));
        for (int name : new int[] {first, second, third}) {
            int slot = VulkanTextureRegistry.celtium$memoSlot(name);
            ids[slot] = name;
            generations[slot] = name * 3;
        }

        assertTrue(VulkanTextureRegistry.celtium$dropMemoEntry(ids, textures, generations, second));

        assertEquals(Integer.MIN_VALUE, ids[VulkanTextureRegistry.celtium$memoSlot(second)]);
        assertEquals(0, generations[VulkanTextureRegistry.celtium$memoSlot(second)]);
        // The other two are untouched: this is the whole point of the change.
        assertEquals(first, ids[VulkanTextureRegistry.celtium$memoSlot(first)]);
        assertEquals(first * 3, generations[VulkanTextureRegistry.celtium$memoSlot(first)]);
        assertEquals(third, ids[VulkanTextureRegistry.celtium$memoSlot(third)]);
        assertEquals(third * 3, generations[VulkanTextureRegistry.celtium$memoSlot(third)]);
    }

    @Test
    void droppingANameWhoseSlotWasTakenByAnotherNameChangesNothing() {
        int slots = VulkanTextureRegistry.celtium$memoSlots();
        int[] ids = emptyIds();
        VulkanGlTexture[] textures = new VulkanGlTexture[slots];
        int[] generations = new int[slots];

        int resident = 11;
        int colliding = resident + slots;
        assertEquals(VulkanTextureRegistry.celtium$memoSlot(resident),
                VulkanTextureRegistry.celtium$memoSlot(colliding));

        int slot = VulkanTextureRegistry.celtium$memoSlot(resident);
        ids[slot] = resident;
        generations[slot] = 5;

        // The colliding name is not the one in the slot, so dropping it must not evict the resident.
        assertFalse(VulkanTextureRegistry.celtium$dropMemoEntry(ids, textures, generations, colliding));
        assertEquals(resident, ids[slot]);
        assertEquals(5, generations[slot]);

        // And dropping the resident does clear it.
        assertTrue(VulkanTextureRegistry.celtium$dropMemoEntry(ids, textures, generations, resident));
        assertEquals(Integer.MIN_VALUE, ids[slot]);
        assertEquals(0, generations[slot]);
    }

    @Test
    void droppingAnAbsentNameIsANoOp() {
        int slots = VulkanTextureRegistry.celtium$memoSlots();
        int[] ids = emptyIds();
        VulkanGlTexture[] textures = new VulkanGlTexture[slots];
        int[] generations = new int[slots];

        assertFalse(VulkanTextureRegistry.celtium$dropMemoEntry(ids, textures, generations, 42));
        for (int index = 0; index < slots; index++) {
            assertEquals(Integer.MIN_VALUE, ids[index]);
            assertEquals(0, generations[index]);
        }
    }

    @Test
    void theSlotIsTheNamesLowBits() {
        int slots = VulkanTextureRegistry.celtium$memoSlots();
        // A power of two, or the mask in the lookup would not be a slot index.
        assertEquals(0, slots & (slots - 1));
        for (int name : new int[] {0, 1, 2, 255, 256, 257, 4096, 65535}) {
            assertEquals(name & (slots - 1), VulkanTextureRegistry.celtium$memoSlot(name));
        }
    }
}
