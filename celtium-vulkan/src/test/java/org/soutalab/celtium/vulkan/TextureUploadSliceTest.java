package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit N08: one slice for the hash and the copy, validated against the declared extent.
 * <p>
 * The rule {@link VulkanGlTexture#requiredUploadBytes} enforces is that a caller must supply
 * {@code width * height * 4} readable bytes <em>from the buffer's current position</em>, because that is where the
 * native address the copy takes starts and where the hash now starts too. The regression this fails on: hashing
 * from absolute index 0 while the copy starts at the position (two different slices), or trusting the caller for
 * the length and reading past a short buffer. {@link VulkanGlTexture#hashUploadSlice} is the hash half: it must
 * describe the same slice the copy takes, or a payload whose earlier bytes did not change hashes the same as the
 * old content and the upload is skipped as a duplicate.
 * <p>
 * This is the JUnit form of {@code scripts/cpu-contract/TextureContractCheck.java}'s upload sections (QUEUE 640);
 * the check's {@code toRgba} sections are covered by {@link ToRgbaTest} and its queue section by
 * {@link ImmediateGroupQueueTest}, so the standalone check retired when this landed. It runs without a GPU or a
 * device: both helpers are pure buffer arithmetic.
 */
class TextureUploadSliceTest {

    /** A direct buffer holding {@code bytes} readable bytes from position 0. */
    private static ByteBuffer direct(int bytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes);
        buffer.limit(bytes);
        return buffer;
    }

    /** A direct buffer holding {@code prefix} bytes then {@code payload}, positioned at the payload. */
    private static ByteBuffer sliced(int prefix, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(prefix + payload.length);
        for (int i = 0; i < prefix; i++) {
            buffer.put(i, (byte) (0x40 + i));
        }
        for (int i = 0; i < payload.length; i++) {
            buffer.put(prefix + i, payload[i]);
        }
        buffer.position(prefix);
        buffer.limit(prefix + payload.length);
        return buffer;
    }

    /** Expects the slice check to refuse the arguments, with the declared extent named in the message. */
    private static void expectRefused(String what, int width, int height, ByteBuffer pixels) {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> VulkanGlTexture.requiredUploadBytes("test", width, height, pixels), what);
        assertTrue(refusal.getMessage() != null && refusal.getMessage().contains("test"),
                what + ": the refusal does not name what was checked (" + refusal.getMessage() + ")");
    }

    @Test
    void anExactSizeBufferIsAccepted() {
        assertEquals(16L, VulkanGlTexture.requiredUploadBytes("test", 2, 2, direct(16)),
                "an exact-size direct buffer from position 0 reports the byte count the copy will use");
    }

    @Test
    void aNonZeroPositionIsMeasuredFromThePosition() {
        // The case the audit found: four payload bytes sit at offset 4, the buffer has room for them from there,
        // and the position and limit must not be touched by the check.
        ByteBuffer offset = direct(32);
        offset.position(4);
        offset.limit(20);
        int positionBefore = offset.position();
        int limitBefore = offset.limit();
        assertEquals(16L, VulkanGlTexture.requiredUploadBytes("test", 2, 2, offset));
        assertEquals(positionBefore, offset.position(), "the caller's position is unchanged");
        assertEquals(limitBefore, offset.limit(), "the caller's limit is unchanged");
    }

    @Test
    void shortBuffersAreRefusedByName() {
        // 15 readable bytes cannot hold 16: refused by name rather than read past.
        expectRefused("a short buffer is refused", 2, 2, direct(15));
        // The check counts the readable bytes, not the capacity: a 32-byte buffer with a limit of 8 is short.
        ByteBuffer limited = direct(32);
        limited.limit(8);
        expectRefused("a buffer whose limit is short is refused", 2, 2, limited);
        // A position that leaves too little is refused as well, even though the buffer is large.
        ByteBuffer latePosition = direct(32);
        latePosition.position(20);
        expectRefused("a late position with too little remaining is refused", 2, 2, latePosition);
    }

    @Test
    void unusableExtentsAndBuffersAreRefused() {
        // Extents that are not positive, or whose byte count overflows, are refused before any arithmetic is trusted.
        expectRefused("a zero width is refused", 0, 4, direct(16));
        expectRefused("a negative height is refused", 4, -1, direct(16));
        expectRefused("an extent above the int range is refused", 1 << 15, 1 << 15, direct(16));
        expectRefused("a null buffer is refused", 2, 2, null);
        // A heap buffer has no native address to copy from, which is what the GL-less capture path relies on being
        // refused rather than silently mis-copied.
        expectRefused("a heap buffer is refused", 2, 2, ByteBuffer.allocate(16));
    }

    @Test
    void theHashDescribesTheSliceTheCopyTakes() {
        // Payload P1 at offset 4, with four bytes of prefix in front of it. The prefix is the same in every buffer,
        // so only the payload decides the hash of the slice the copy reads.
        ByteBuffer first = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        ByteBuffer same = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        // A payload that differs only in its LAST byte must hash differently, which is exactly the byte the old
        // window (absolute 0 for min(remaining/height, width*4)*height bytes) could miss.
        ByteBuffer changedTail = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 99});
        long hashFirst = VulkanGlTexture.hashUploadSlice(first, 16);
        assertEquals(hashFirst, VulkanGlTexture.hashUploadSlice(same, 16), "the same slice hashes the same");
        assertNotEquals(hashFirst, VulkanGlTexture.hashUploadSlice(changedTail, 16),
                "a changed tail byte changes the hash of the slice");
        // A different prefix in front of the same payload must not change the hash of the slice: the copy reads from
        // the position, so the hash must ignore what precedes it.
        ByteBuffer otherPrefix = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        otherPrefix.put(0, (byte) 0x77);
        assertEquals(hashFirst, VulkanGlTexture.hashUploadSlice(otherPrefix, 16),
                "the prefix before the position does not enter the slice hash");
    }

    @Test
    void aHashPastTheRemainingBytesIsRefused() {
        ByteBuffer first = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        assertThrows(IllegalArgumentException.class, () -> VulkanGlTexture.hashUploadSlice(first, 17),
                "hashing more than the buffer can read is refused by name rather than read past the end");
    }

    /**
     * The counterfactual: the pre-fix window really did miss the byte the fix reads.
     * <p>
     * Asserting that the new hash changes is only half the claim. The other half is that the old expression was
     * wrong in the way the audit says - a check that would have passed under the old code proves nothing about the
     * fix. The old window was {@code min(remaining / height, width * 4) * height} bytes from absolute index 0; this
     * computes both hashes over the same two buffers and requires that the old one does <em>not</em> see the changed
     * tail while the new one does. If a future edit reinstates the absolute-index window, this test fails first.
     */
    @Test
    void theLegacyWindowWouldMissTheTail() {
        ByteBuffer first = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        ByteBuffer changedTail = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 99});
        // The audit's own numbers for this case: a 4x4 upload from a buffer positioned at 4. The old window is
        // min(remaining/4, 4*4)*4 = 16 bytes from index 0, i.e. the four prefix bytes plus the first twelve payload
        // bytes - the last payload byte, the one that changed, is outside it.
        int height = 4;
        int width = 4;
        int remaining = first.remaining();
        int legacyWindow = Math.min(remaining / Math.max(1, height), width * 4) * height;
        assertEquals(16, legacyWindow, "the legacy window is the 16 bytes the audit describes");
        assertEquals(legacyHash(first, legacyWindow), legacyHash(changedTail, legacyWindow),
                "the legacy window could not see the changed tail byte (the bug the fix removes)");
        assertNotEquals(VulkanGlTexture.hashUploadSlice(first, 16), VulkanGlTexture.hashUploadSlice(changedTail, 16),
                "the slice hash sees the byte the legacy window missed");
    }

    /** The pre-fix hash, kept here as the counterfactual rather than as production code. */
    private static long legacyHash(ByteBuffer buffer, int bytes) {
        long hash = 1469598103934665603L;
        for (int i = 0; i < bytes; i++) {
            hash = (hash ^ (buffer.get(i) & 0xFF)) * 1099511628211L;
        }
        return hash;
    }
}
