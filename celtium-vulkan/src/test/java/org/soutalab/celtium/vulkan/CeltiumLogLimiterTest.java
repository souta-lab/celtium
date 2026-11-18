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
    void crumbDumpLinesAreNeverLimited() {
        // Item 414: of 128 crumbs only the first 16 reached latest.log, because the dump prints one CELTIUM_CRUMB
        // line per ring entry through the same stream the limiter bounds. The whole dump - header, crumbs and the
        // footer that shares the header's tag - must pass however far past the limit it goes.
        for (int i = 0; i < CeltiumLogLimiter.LIMIT + 128; i++) {
            assertTrue(CeltiumLogLimiter.admit("[Celtium] CELTIUM_CRUMB #" + i + " window target=0x1 groups=2"));
            assertTrue(CeltiumLogLimiter.admit("[Celtium] CELTIUM_CRASH_BREADCRUMBS trigger=x count=128"));
        }
        assertTrue(CeltiumLogLimiter.isUnlimited("CELTIUM_CRUMB"));
        assertTrue(CeltiumLogLimiter.isUnlimited("CELTIUM_CRASH_BREADCRUMBS"));
        assertFalse(CeltiumLogLimiter.isUnlimited("CELTIUM_CRUMB_EXTRA"));
        assertFalse(CeltiumLogLimiter.isUnlimited(null));
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
