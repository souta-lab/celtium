package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeFogOwnershipTest {
    @Test void terrainVisibilityAndQueuedImmediateDrawsShareTheOwnerWithoutSharingMutableSnapshots() {
        ImmediateFogState owner = new ImmediateFogState();
        FogService terrain = owner;
        owner.parameter(0x0B65, 9729);
        owner.parameter(0x0B63, 96);
        owner.parameter(0x0B64, 128);
        owner.color(0.3f, 0.5f, 0.7f);
        owner.enabled(true);
        float[] queued = owner.snapshot();
        assertEquals(ChunkFogMode.SMOOTH, terrain.getFogMode());
        assertEquals(96, terrain.getFogStart());
        assertEquals(128, terrain.getFogCutoff());
        assertArrayEquals(terrain.getFogColor(), new float[] {queued[4], queued[5], queued[6], queued[7]});
        assertFalse(owner.movedFrom(queued));
        owner.color(0, 0, 0);
        assertTrue(owner.movedFrom(queued));
        assertEquals(0.3f, queued[4]);
        owner.enabled(false);
        assertNull(owner.snapshot());
        assertEquals(ChunkFogMode.NONE, terrain.getFogMode());
        assertEquals(128, terrain.getFogCutoff());
    }
    @Test void pendingTextIsSubmittedUnderTheOldFogBeforeTheOwnerChanges() {
        NativeRasterState boundaries = new NativeRasterState();
        ImmediateFogState owner = new ImmediateFogState(boundaries);
        int[] calls = {0};
        boundaries.beforeChange(() -> {
            assertFalse(owner.enabled());
            assertEquals(1, owner.getFogEnd());
            calls[0]++;
        });
        owner.enabled(true);
        assertEquals(1, calls[0]);
        boundaries.beforeChange(() -> {
            assertTrue(owner.enabled());
            assertEquals(1, owner.getFogEnd());
            calls[0]++;
        });
        owner.enabled(true);
        owner.parameter(0x0B64, 128);
        assertEquals(2, calls[0]);
        boundaries.beforeChange(() -> { throw new IllegalStateException("pending text was not submitted"); });
        assertThrows(IllegalStateException.class, () -> owner.color(0.5f, 0.4f, 0.3f));
        assertArrayEquals(new float[] {0, 0, 0, 1}, owner.getFogColor());
    }

}
