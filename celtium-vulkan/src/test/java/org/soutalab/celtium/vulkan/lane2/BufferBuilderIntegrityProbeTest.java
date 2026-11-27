package org.soutalab.celtium.vulkan.lane2;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferBuilderIntegrityProbeTest {

    /** Stands in for the vanilla BufferBuilder shape: owns the one backing buffer. */
    static class FakeBuilder {
        ByteBuffer byteBuffer;
    }

    /** A mod-style subclass that shadows the storage with a field of its own. */
    static class ShadowBuilder extends FakeBuilder {
        ByteBuffer ownBuffer;
    }

    /** A subclass that adds no storage: the inherited view stays the written buffer. */
    static class CleanBuilder extends FakeBuilder {
        int counter;
    }

    private static ByteBuffer direct(int capacity) {
        return ByteBuffer.allocateDirect(capacity);
    }

    @Test
    void shadowStorageScanFindsSubclassFieldsOnly() {
        assertTrue(BufferBuilderIntegrityProbe.hasShadowStorage(ShadowBuilder.class, FakeBuilder.class));
        assertFalse(BufferBuilderIntegrityProbe.hasShadowStorage(CleanBuilder.class, FakeBuilder.class));
        assertFalse(BufferBuilderIntegrityProbe.hasShadowStorage(FakeBuilder.class, FakeBuilder.class));
    }

    @Test
    void aSoundOfferIsZeroCopy() {
        int reasons = BufferBuilderIntegrityProbe.inspectReasons(
                false, direct(4096), 64, 32, true, 7);
        assertEquals(0, reasons);
        assertEquals(BufferBuilderIntegrityProbe.CopyPath.ZERO_COPY,
                BufferBuilderIntegrityProbe.decide(false, direct(4096), 64, 32, true, 7));
    }

    @Test
    void eachBreakageNamesItsReasonAndDiverts() {
        assertEquals(BufferBuilderIntegrityProbe.REASON_SHADOW_STORAGE,
                BufferBuilderIntegrityProbe.inspectReasons(true, direct(4096), 64, 32, true, 7));
        assertEquals(BufferBuilderIntegrityProbe.REASON_NO_BUFFER,
                BufferBuilderIntegrityProbe.inspectReasons(false, null, 64, 32, true, 7));
        assertEquals(BufferBuilderIntegrityProbe.REASON_BUFFER_NOT_DIRECT,
                BufferBuilderIntegrityProbe.inspectReasons(false, ByteBuffer.allocate(4096), 64, 32, true, 7));
        assertEquals(BufferBuilderIntegrityProbe.REASON_BUFFER_TOO_SMALL,
                BufferBuilderIntegrityProbe.inspectReasons(false, direct(1024), 64, 32, true, 7));
        assertEquals(BufferBuilderIntegrityProbe.REASON_NO_FORMAT,
                BufferBuilderIntegrityProbe.inspectReasons(false, direct(4096), 64, 32, false, 7));
        assertEquals(BufferBuilderIntegrityProbe.REASON_BAD_DRAW_MODE,
                BufferBuilderIntegrityProbe.inspectReasons(false, direct(4096), 64, 32, true, 9));
        assertEquals(BufferBuilderIntegrityProbe.CopyPath.ONE_COPY_SIMD,
                BufferBuilderIntegrityProbe.decide(true, direct(4096), 64, 32, true, 7));
    }

    @Test
    void reasonsCombine() {
        int reasons = BufferBuilderIntegrityProbe.inspectReasons(
                true, ByteBuffer.allocate(16), 64, 32, false, 42);
        assertEquals(BufferBuilderIntegrityProbe.REASON_SHADOW_STORAGE
                | BufferBuilderIntegrityProbe.REASON_BUFFER_NOT_DIRECT
                | BufferBuilderIntegrityProbe.REASON_BUFFER_TOO_SMALL
                | BufferBuilderIntegrityProbe.REASON_NO_FORMAT
                | BufferBuilderIntegrityProbe.REASON_BAD_DRAW_MODE, reasons);
    }

    @Test
    void countersRecordDecisions() {
        long zeroBefore = BufferBuilderIntegrityProbe.zeroCopyDecisions();
        long copyBefore = BufferBuilderIntegrityProbe.oneCopyDecisions();
        BufferBuilderIntegrityProbe.decide(false, direct(4096), 4, 32, true, 4);
        BufferBuilderIntegrityProbe.decide(true, direct(4096), 4, 32, true, 4);
        assertEquals(zeroBefore + 1, BufferBuilderIntegrityProbe.zeroCopyDecisions());
        assertEquals(copyBefore + 1, BufferBuilderIntegrityProbe.oneCopyDecisions());
    }
}
