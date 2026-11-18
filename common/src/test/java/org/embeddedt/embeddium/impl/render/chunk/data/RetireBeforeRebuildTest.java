package org.embeddedt.embeddium.impl.render.chunk.data;

import org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage.BatchCacheParams;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.taumc.celeritas.lwjgl.FakeGlLWJGLService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * The retire-before-rebuild ordering at the storage the renderers actually call, run without a GL context
 * through {@link FakeGlLWJGLService} (item 398). This is the shape of upstream's
 * {@code BatchBufferRecyclingTest}: upstream drove {@code DefaultChunkRenderer.getRegionBatch} and read
 * the fake's buffer log to prove a rebuilt batch reuses the retired batch's GL buffer name. Celtium takes
 * the harness and the ordering, not the recycling - its batches own no GL buffer - so what is proved here
 * is the ordering itself (the stale batch is already deleted when the replacement is created, and the
 * replacement is stored under the same key), plus the premise of the refusal: creating and deleting a
 * batch through the fake records no GL buffer calls at all.
 * <p>
 * The storage can only be constructed in a test at all because the harness exists: its heap is allocated
 * through the LWJGL service ({@code SectionRenderDataUnsafe}), which the fake serves from Unsafe memory.
 */
class RetireBeforeRebuildTest {
    private static final BatchCacheParams PARAMS =
            new BatchCacheParams(false, BatchCacheParams.DEFAULT_VIEW);
    private static final BatchCacheParams SHADOW_PARAMS =
            new BatchCacheParams(false, BatchCacheParams.SHADOW_VIEW);

    /** A cached batch that appends its deletion to a shared event log; its payload is null. */
    private static final class RecordingBatch extends CachedBatch {
        private final List<String> events;
        private final String name;

        RecordingBatch(List<String> events, String name) {
            super(null, null, new byte[0], 0, 0, 0, 0, 0, 0, 0);
            this.events = events;
            this.name = name;
        }

        @Override
        public void delete() {
            this.events.add("delete " + this.name);
        }
    }

    private SectionRenderDataStorage storage;

    @BeforeEach
    void setUp() {
        FakeGlLWJGLService.reset();
        this.storage = new SectionRenderDataStorage(QuadPrimitiveType.TRIANGULATED, false, 1);
    }

    @AfterEach
    void tearDown() {
        this.storage.delete();
    }

    @Test
    void theTestLwjglServiceIsTheFake() {
        // Guards the harness wiring itself: if the -Dceleritas.lwjglService property or the lwjglCommon
        // test classpath entry is lost, the storage above cannot be built and every test here fails in
        // setUp - this assertion names the reason instead of leaving a class-loading stack trace.
        assertEquals("FakeGlLWJGLService", LWJGL.toString());
    }

    @Test
    void staleBatchIsDeletedBeforeTheReplacementIsCreated() {
        List<String> events = new ArrayList<>();
        RecordingBatch stale = new RecordingBatch(events, "stale");
        RecordingBatch otherView = new RecordingBatch(events, "shadow");
        this.storage.storeCachedMultiDrawBatch(PARAMS, stale);
        this.storage.storeCachedMultiDrawBatch(SHADOW_PARAMS, otherView);

        // The rebuild sequence both renderers run (DefaultChunkRenderer.getRegionBatch, and the Vulkan
        // backend inside its rebuild-allowed branch): retire, then create, then store.
        this.storage.retireCachedMultiDrawBatch(PARAMS);
        events.add("create replacement");
        RecordingBatch replacement = new RecordingBatch(events, "replacement");
        this.storage.storeCachedMultiDrawBatch(PARAMS, replacement);

        // The stale batch was deleted before the replacement existed - not inside the store, after both
        // were alive - and the other view's batch was not touched by either step.
        assertEquals(List.of("delete stale", "create replacement"), events);
        assertSame(replacement, this.storage.getCachedMultiDrawBatch(PARAMS));
        assertSame(otherView, this.storage.getCachedMultiDrawBatch(SHADOW_PARAMS));
    }

    @Test
    void retireWithoutAStoredBatchIsANoOp() {
        assertNull(this.storage.getCachedMultiDrawBatch(PARAMS));

        this.storage.retireCachedMultiDrawBatch(PARAMS);

        assertNull(this.storage.getCachedMultiDrawBatch(PARAMS));
    }

    @Test
    void aBatchCreatedThroughTheFakeOwnsNoGlBuffer() {
        // DirectMultiDrawBatch is the default batch factory's product: its commands live in CPU memory
        // served by the LWJGL service, its upload is a no-op, and deleting it frees memory only. No GL
        // buffer name is ever generated - which is why upstream's buffer recycling has nothing to recycle
        // in celtium, and only its ordering and harness were taken (item 337 audit, item 398).
        DirectMultiDrawBatch batch = new DirectMultiDrawBatch(4);
        batch.appendDrawCommand(0, 6, 0L);
        batch.mergeIntoLastCommand(6);
        assertEquals(1, batch.size());
        assertEquals(12, batch.getIndexBufferSize());
        batch.upload(null);
        batch.delete();

        assertTrue(FakeGlLWJGLService.LOG.isEmpty(),
                "no GL buffer calls expected, got: " + FakeGlLWJGLService.LOG);
    }
}
