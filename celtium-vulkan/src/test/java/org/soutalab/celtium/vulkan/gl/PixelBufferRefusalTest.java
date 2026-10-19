package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code pixels} argument with a PBO bound is an offset into that buffer, not a client pointer (audit #360). Offset 0
 * would be a null dereference and offset 16 a write into the JVM's own low memory, so both paths refuse and count it.
 */
class PixelBufferRefusalTest {
    @Test
    void proceedsWhenNoPixelBufferIsBound() {
        assertNull(CeltiumGlStub.pixelBufferRefusal(0, 0x7f000000L, "glReadPixels"));
        assertNull(CeltiumGlStub.pixelBufferRefusal(0, 0L, "texture upload"));
    }

    @Test
    void refusesWithAPackBufferBound() {
        String refusal = CeltiumGlStub.pixelBufferRefusal(7, 16L, "glReadPixels");
        assertNotNull(refusal);
        assertTrue(refusal.contains("buffer=7"), refusal);
        assertTrue(refusal.contains("offset=16"), refusal);
        assertTrue(refusal.contains("not a client pointer"), refusal);
    }

    @Test
    void refusesWithAnUnpackBufferBound() {
        assertNotNull(CeltiumGlStub.pixelBufferRefusal(3, 0L, "texture upload"));
    }

    @Test
    void theOffsetIsReportedVerbatim() {
        // The diagnostic has to carry the offset: a clamped or formatted one makes the refused call unidentifiable.
        assertTrue(CeltiumGlStub.pixelBufferRefusal(1, 4096L, "op").contains("offset=4096"));
    }
}
