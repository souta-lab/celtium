package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrashBreadcrumbsTest {
    @Test
    void newCodesDescribeWithTheirOwnFields() {
        // Item 415: the codes item 414's window needed - a first model replay, the immediate groups queued in
        // the same seconds, the raw framebuffers a mod created, and the deferred upload a later wait was
        // charged to - plus the pipeline generations the identity line also prints.
        assertEquals("pipeline-create generation=45 pipeline=0x2a renderPass=0x10",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.PIPELINE_CREATE, 45L, 0x2aL, 0x10L));
        assertEquals("model-replay list=28083 texture=28082 key=0x10",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.MODEL_REPLAY, 28083L, 28082L, 0x10L));
        assertEquals("immediate-group format=9 pipeline=0x2a target=0x10",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.IMMEDIATE_GROUP, 9L, 0x2aL, 0x10L));
        assertEquals("raw-fbo identity=0x10 color=28072 depth=28074",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.RAW_FBO, 0x10L, 28072L, 28074L));
        assertEquals("deferred-upload texture=4339 fence=0x2a",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.DEFERRED_UPLOAD, 4339L, 0x2aL, 0L));
    }

    @Test
    void newCodesDoNotCollideWithTheExistingOnes() {
        int[] codes = {CrashBreadcrumbs.WINDOW, CrashBreadcrumbs.TERRAIN_PASS, CrashBreadcrumbs.CHAIN_PAIR,
                CrashBreadcrumbs.TEXTURE_UPLOAD, CrashBreadcrumbs.RAW_WRITE, CrashBreadcrumbs.PRESENT,
                CrashBreadcrumbs.COALESCED_COPY, CrashBreadcrumbs.FRAMEBUFFER_BIND, CrashBreadcrumbs.WINDOW_PASS,
                CrashBreadcrumbs.FRAME_CACHE_HIT, CrashBreadcrumbs.FRAME_CACHE_MISS,
                CrashBreadcrumbs.PIPELINE_CREATE, CrashBreadcrumbs.MODEL_REPLAY, CrashBreadcrumbs.IMMEDIATE_GROUP,
                CrashBreadcrumbs.RAW_FBO, CrashBreadcrumbs.DEFERRED_UPLOAD};
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
        for (int code : codes) {
            assertTrue(seen.add(Integer.valueOf(code)), "duplicate breadcrumb code " + code);
        }
    }
}
