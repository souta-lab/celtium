package org.taumc.celeritas.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.glcompat.capture.DirectGlCaptureEntry;
import org.soutalab.celtium.glcompat.capture.GlFn;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3 (decoders ruling, item 25 / C3): display-list names live in ONE id space.
 * {@code glGenLists} on the capture entry allocates from the GLSM display-list allocator -
 * the same space {@code glIsList} and {@code glDeleteLists} answer from - and the hint's
 * {@code glIsList} door reads that same space through the entry's route, so a list generated
 * on one surface is a list on the other, and a deleted list is gone on both.
 */
class DisplayListSingleIdSpaceTest {

    private static long call(DirectGlCaptureEntry entry, String name, long... callArgs) throws Exception {
        Method dispatch = DirectGlCaptureEntry.class.getDeclaredMethod("dispatchArgs", GlFn.class, long[].class);
        dispatch.setAccessible(true);
        Map<String, Integer> ids = DirectGlCaptureEntry.ids();
        long[] args = new long[16];
        System.arraycopy(callArgs, 0, args, 0, callArgs.length);
        return (Long) dispatch.invoke(entry, DirectGlCaptureEntry.row(ids.get(name)), args);
    }

    private static DirectGlCaptureEntry entry() throws Exception {
        var ctor = DirectGlCaptureEntry.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    @Test
    @DisplayName("gen, is and delete agree on one id space across the entry and the hint")
    void oneIdSpaceAcrossEntryAndHint() throws Exception {
        DirectGlCaptureEntry entry = entry();
        long base = call(entry, "glGenLists", 3);
        assertTrue(base > 0, "a range of three generates a base name");
        for (int i = 0; i < 3; i++) {
            int list = (int) base + i;
            assertEquals(1L, call(entry, "glIsList", list),
                    "the entry sees its own generated list " + list);
            assertTrue(CeltiumGllessHint.glIsList(list),
                    "the hint sees the same list " + list + " - one space, not two");
        }
        call(entry, "glDeleteLists", base, 3);
        for (int i = 0; i < 3; i++) {
            int list = (int) base + i;
            assertEquals(0L, call(entry, "glIsList", list),
                    "after glDeleteLists the entry no longer sees list " + list);
            assertFalse(CeltiumGllessHint.glIsList(list),
                    "and neither does the hint - the delete lands in the same space");
        }
    }

    @Test
    @DisplayName("a non-positive range generates nothing and returns 0")
    void nonPositiveRangeGeneratesNothing() throws Exception {
        DirectGlCaptureEntry entry = entry();
        assertEquals(0L, call(entry, "glGenLists", 0), "range 0 generates nothing");
        assertEquals(0L, call(entry, "glGenLists", -2), "a negative range generates nothing");
    }
}
