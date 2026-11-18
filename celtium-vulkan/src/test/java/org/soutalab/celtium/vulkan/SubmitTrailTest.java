package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubmitTrailTest {
    @Test
    void describesTheNewestSubmissionsOldestFirst() {
        SubmitTrail.clear();
        assertEquals("lastSubmits=[none]", SubmitTrail.describe());
        SubmitTrail.note(SubmitTrail.FRAME, 10708L, 2L);
        SubmitTrail.note(SubmitTrail.DEFERRED, 0x2aL, 0L);
        assertEquals("lastSubmits=[frame seq=10708 slot=2 deferred fence=0x2a mask=0]", SubmitTrail.describe());
    }

    @Test
    void theRingKeepsOnlyTheNewestEight() {
        SubmitTrail.clear();
        for (int i = 1; i <= 10; i++) {
            SubmitTrail.note(SubmitTrail.FRAME, i, 0L);
        }
        String described = SubmitTrail.describe();
        assertFalse(described.contains("seq=1 "), described);
        assertFalse(described.contains("seq=2 "), described);
        assertTrue(described.contains("seq=3 "), described);
        assertTrue(described.contains("seq=10"), described);
    }
}
