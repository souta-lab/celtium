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
        assertEquals("deferred-upload texture=4339 fence=0x2a serial=7",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.DEFERRED_UPLOAD, 4339L, 0x2aL, 7L));
    }

    @Test
    void item529CodesDescribeWithTheirOwnFields() {
        // Item 529: one deferred code per fence-assignment site, each carrying the texture's instance serial,
        // and the batch record that says what shared a lost fence - its generation, serial, and composition.
        assertEquals("deferred-upload-patched-levels texture=4339 fence=0x2a serial=7",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.DEFERRED_UPLOAD_PATCHED_LEVELS, 4339L, 0x2aL, 7L));
        assertEquals("deferred-transition texture=4339 fence=0x2a serial=7",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.DEFERRED_TRANSITION, 4339L, 0x2aL, 7L));
        assertEquals("deferred-upload-level texture=4339 fence=0x2a serial=7",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.DEFERRED_UPLOAD_LEVEL, 4339L, 0x2aL, 7L));
        assertEquals("deferred-upload-image texture=4339 fence=0x2a serial=7",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.DEFERRED_UPLOAD_IMAGE, 4339L, 0x2aL, 7L));
        assertEquals("batch-submit gen=3 batch=7 fence=0x2a entries=5 frame=2 deferred=3 semaphoreSets=1",
                CrashBreadcrumbs.describe(CrashBreadcrumbs.BATCH_SUBMIT, (3L << 32) | 7L, 0x2aL,
                        CrashBreadcrumbs.packBatchComposition(5, 2, 1)));
    }

    @Test
    void windowPassLineCarriesTheBeginArguments() {
        // Item 536: the write-through line is read next to an hs_err after a native AV, so it names the same
        // three numbers the ring's WINDOW_PASS describe does, plus the sequence of the window in the session.
        assertEquals("celtium last window-pass seq=7 renderPass=0x2a framebuffer=0x10 extent=3440x1369\n",
                CrashBreadcrumbs.windowPassLine(7L, 0x2aL, 0x10L, (3440L << 32) | 1369L));
    }

    @Test
    void windowPassNotesWriteThroughTheLatestTuple(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        // Item 536: the ring is dumped only at a device loss, so a process-killing AV inside
        // vkCmdBeginRenderPass takes the fatal window's begin arguments with it. The write-through file must
        // hold the latest tuple after each change, and an unchanged tuple inside the heartbeat interval must
        // not be rewritten (the sequence number in the line would move if it were).
        java.nio.file.Path file = dir.resolve("celtium-last-window-pass.log");
        CrashBreadcrumbs.windowPassFileForTest = file;
        try {
            long extent = (1920L << 32) | 1080L;
            CrashBreadcrumbs.note3(CrashBreadcrumbs.WINDOW_PASS, 0xaaaaL, 0xbbbbL, extent);
            String first = java.nio.file.Files.readString(file);
            assertTrue(first.contains("renderPass=0xaaaa"), first);
            assertTrue(first.contains("framebuffer=0xbbbb"), first);
            assertTrue(first.contains("extent=1920x1080"), first);
            CrashBreadcrumbs.note3(CrashBreadcrumbs.WINDOW_PASS, 0xaaaaL, 0xbbbbL, extent);
            assertEquals(first, java.nio.file.Files.readString(file),
                    "an unchanged tuple inside the interval is not rewritten");
            CrashBreadcrumbs.note3(CrashBreadcrumbs.WINDOW_PASS, 0xccccL, 0xddddL, extent);
            String changed = java.nio.file.Files.readString(file);
            assertTrue(changed.contains("renderPass=0xcccc"), changed);
            assertTrue(changed.contains("framebuffer=0xdddd"), changed);
        } finally {
            CrashBreadcrumbs.windowPassFileForTest = null;
        }
    }

    @Test
    void newCodesDoNotCollideWithTheExistingOnes() {
        int[] codes = {CrashBreadcrumbs.WINDOW, CrashBreadcrumbs.TERRAIN_PASS, CrashBreadcrumbs.CHAIN_PAIR,
                CrashBreadcrumbs.TEXTURE_UPLOAD, CrashBreadcrumbs.RAW_WRITE, CrashBreadcrumbs.PRESENT,
                CrashBreadcrumbs.COALESCED_COPY, CrashBreadcrumbs.FRAMEBUFFER_BIND, CrashBreadcrumbs.WINDOW_PASS,
                CrashBreadcrumbs.FRAME_CACHE_HIT, CrashBreadcrumbs.FRAME_CACHE_MISS,
                CrashBreadcrumbs.PIPELINE_CREATE, CrashBreadcrumbs.MODEL_REPLAY, CrashBreadcrumbs.IMMEDIATE_GROUP,
                CrashBreadcrumbs.RAW_FBO, CrashBreadcrumbs.DEFERRED_UPLOAD,
                CrashBreadcrumbs.DEFERRED_UPLOAD_PATCHED_LEVELS, CrashBreadcrumbs.DEFERRED_TRANSITION,
                CrashBreadcrumbs.DEFERRED_UPLOAD_LEVEL, CrashBreadcrumbs.DEFERRED_UPLOAD_IMAGE,
                CrashBreadcrumbs.BATCH_SUBMIT};
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
        for (int code : codes) {
            assertTrue(seen.add(Integer.valueOf(code)), "duplicate breadcrumb code " + code);
        }
    }
}
