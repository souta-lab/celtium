package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CeltiumLogLimiterTest {
    @Test
    void tagsAreTheFirstWordOfTheRenderersLines() {
        assertEquals("CELTIUM_TEX_COPY_SERVED", CeltiumLogLimiter.tagOf("[Celtium] CELTIUM_TEX_COPY_SERVED dest=936"));
        assertEquals("uploads", CeltiumLogLimiter.tagOf("celtium uploads: offers=500"));
        assertEquals("CELTIUM_FOG", CeltiumLogLimiter.tagOf("[Celtium] CELTIUM_FOG=1"));
        assertNull(CeltiumLogLimiter.tagOf("[Server thread/INFO] Saving chunks"));
        assertNull(CeltiumLogLimiter.tagOf(null));
    }

    @Test
    void eachTagPassesItsLimitAndNoMore() {
        String line = "[Celtium] CELTIUM_LIMITER_TEST_TAG n";
        int admitted = 0;
        for (int i = 0; i < CeltiumLogLimiter.LIMIT + 50; i++) {
            if (CeltiumLogLimiter.admit(line)) {
                admitted++;
            }
        }
        assertEquals(CeltiumLogLimiter.LIMIT, admitted);
        assertTrue(CeltiumLogLimiter.admit("not the renderer's line"));
    }
}
