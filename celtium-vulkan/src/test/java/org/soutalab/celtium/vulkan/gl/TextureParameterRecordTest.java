package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the contract of the per-texture parameter record extracted from {@code CeltiumGlStub}
 * (refactor/giant-split-1): GL's defaults for unset pnames, the recorded/absent distinction the mirror
 * relies on, the per-name version the sampler re-confirm compares, the forget that a recycled name goes
 * through, and the record cap.
 */
class TextureParameterRecordTest {

    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_TEXTURE_BORDER_COLOR = 0x1004;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_NEAREST_MIPMAP_LINEAR = 0x2702;
    private static final int GL_REPEAT = 0x2901;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;

    @Test
    void unsetPnamesAnswerGlDefaultsAndAreNotRecorded() {
        TextureParameterRecord record = new TextureParameterRecord();
        assertEquals(GL_REPEAT, record.valueOrDefault(7, GL_TEXTURE_WRAP_S));
        assertEquals(GL_REPEAT, record.valueOrDefault(7, GL_TEXTURE_WRAP_T));
        assertEquals(GL_LINEAR, record.valueOrDefault(7, GL_TEXTURE_MAG_FILTER));
        assertEquals(GL_NEAREST_MIPMAP_LINEAR, record.valueOrDefault(7, GL_TEXTURE_MIN_FILTER));
        assertEquals(0, record.valueOrDefault(7, 0x84CD));
        assertEquals(-1, record.recordedValue(7, GL_TEXTURE_WRAP_S));
        assertFalse(record.isRecorded(7, GL_TEXTURE_WRAP_S));
        assertEquals(0, record.version(7));
    }

    @Test
    void recordedValuesWinOverDefaultsAndBumpOnlyTheirOwnName() {
        TextureParameterRecord record = new TextureParameterRecord();
        record.set(7, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE, "test");
        assertEquals(GL_CLAMP_TO_EDGE, record.valueOrDefault(7, GL_TEXTURE_WRAP_S));
        assertEquals(GL_CLAMP_TO_EDGE, record.recordedValue(7, GL_TEXTURE_WRAP_S));
        assertTrue(record.isRecorded(7, GL_TEXTURE_WRAP_S));
        assertEquals(1, record.version(7));
        record.set(7, GL_TEXTURE_MAG_FILTER, GL_LINEAR, "test");
        assertEquals(2, record.version(7));
        record.set(8, GL_TEXTURE_WRAP_S, GL_REPEAT, "test");
        assertEquals(2, record.version(7));
        assertEquals(1, record.version(8));
        assertEquals(3, record.calls());
    }

    @Test
    void borderColorAndUnboundCallsAreNotRecorded() {
        TextureParameterRecord record = new TextureParameterRecord();
        record.set(7, GL_TEXTURE_BORDER_COLOR, 123, "test");
        assertFalse(record.isRecorded(7, GL_TEXTURE_BORDER_COLOR));
        assertEquals(0, record.version(7));
        record.set(0, GL_TEXTURE_WRAP_S, GL_REPEAT, "test");
        record.set(-3, GL_TEXTURE_WRAP_S, GL_REPEAT, "test");
        assertEquals(0, record.calls());
        assertTrue(record.summary().contains("unbound=2"), record.summary());
    }

    @Test
    void forgetDropsOnlyThatNamesValuesAndVersion() {
        TextureParameterRecord record = new TextureParameterRecord();
        record.set(7, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE, "test");
        record.set(8, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE, "test");
        record.forget(7);
        assertFalse(record.isRecorded(7, GL_TEXTURE_WRAP_S));
        assertEquals(0, record.version(7));
        assertEquals(GL_REPEAT, record.valueOrDefault(7, GL_TEXTURE_WRAP_S));
        assertTrue(record.isRecorded(8, GL_TEXTURE_WRAP_S));
        assertEquals(1, record.version(8));
    }

    @Test
    void versionTableGrowsPastItsInitialSize() {
        TextureParameterRecord record = new TextureParameterRecord();
        record.set(5000, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE, "test");
        assertEquals(1, record.version(5000));
        assertEquals(GL_CLAMP_TO_EDGE, record.recordedValue(5000, GL_TEXTURE_WRAP_T));
    }

    @Test
    void summaryCountsTheRecord() {
        TextureParameterRecord record = new TextureParameterRecord();
        record.set(7, GL_TEXTURE_WRAP_S, GL_REPEAT, "test");
        record.set(7, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE, "test");
        String summary = record.summary();
        assertTrue(summary.startsWith("texture params: recorded=2 textures=1"), summary);
        assertTrue(summary.contains("wrapRepeat=1 wrapClamp=1"), summary);
        assertTrue(summary.contains("calls=2"), summary);
    }

    @Test
    void pastTheCapNewKeysAreCountedNotRecordedButExistingKeysStillUpdate() {
        TextureParameterRecord record = new TextureParameterRecord();
        for (int name = 1; name <= (1 << 16); name++) {
            record.set(name, GL_TEXTURE_WRAP_S, GL_REPEAT, "test");
        }
        record.set(70000, GL_TEXTURE_WRAP_S, GL_REPEAT, "test");
        assertFalse(record.isRecorded(70000, GL_TEXTURE_WRAP_S));
        assertTrue(record.summary().contains("overflow=1"), record.summary());
        record.set(1, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE, "test");
        assertEquals(GL_CLAMP_TO_EDGE, record.recordedValue(1, GL_TEXTURE_WRAP_S));
    }
}
