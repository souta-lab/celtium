import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * CPU contract check for the texture-patch and render-target queue rules (audit A02, A03, A32).
 *
 * <p>This runs without a GPU, a device, or the game. It exercises the two device-free helpers the renderer uses:
 * {@code VulkanTextureRegistry.toRgba} - which reads the animation method's {@code int[mipLevel][flat texel]} array - and
 * {@code ImmediateGroupQueue.retireGroupsForTarget} - which decides which pending groups one pass may retire.
 *
 * <p>What it can and cannot prove: the byte-level addressing, the channel and row order, the region arithmetic, and the
 * queue rule are all exercised here. The Vulkan barrier and layout handling that a real patch performs are not - those are
 * covered by the validation-clean runs, and this check does not stand in for them.
 *
 * <p>Run: javac -cp <celtium-vulkan classes> -d /tmp/contract scripts/cpu-contract/TextureContractCheck.java
 *         java -cp <celtium-vulkan classes>:/tmp/contract TextureContractCheck
 */
public final class TextureContractCheck {

    private static int failures;

    public static void main(String[] args) {
        checkFlatTexelContract();
        checkTwoIndependentPatchesPreserveTheRest();
        checkTwoIndependentTargets();
        checkUploadSliceContract();
        checkPerLevelPatchConversion();
        System.out.println(failures == 0 ? "CELTIUM_CPU_CONTRACT PASS" : "CELTIUM_CPU_CONTRACT FAIL failures=" + failures);
        if (failures != 0) {
            System.exit(1);
        }
    }

    /**
     * N08: one slice for the hash and the copy, validated against the declared extent.
     *
     * <p>The rule {@code VulkanGlTexture.requiredUploadBytes} enforces is that a caller must supply
     * {@code width * height * 4} readable bytes <em>from the buffer's current position</em>, because that is where the
     * native address the copy takes starts and where the hash now starts too. The regression this fails on: hashing
     * from absolute index 0 while the copy starts at the position (two different slices), or trusting the caller for
     * the length and reading past a short buffer.
     */
    private static void checkUploadSliceContract() {
        // An exact-size direct buffer from position 0 is accepted and reports the byte count the copy will use.
        ByteBuffer exact = direct(16);
        expect("N08 exact-size bytes", 16L,
                org.soutalab.celtium.vulkan.VulkanGlTexture.requiredUploadBytes("test", 2, 2, exact));

        // A non-zero position is the case the audit found: four payload bytes sit at offset 4, the buffer has room for
        // them from there, and the position and limit must not be touched by the check.
        ByteBuffer offset = direct(32);
        offset.position(4);
        offset.limit(20);
        int positionBefore = offset.position();
        int limitBefore = offset.limit();
        expect("N08 offset slice bytes", 16L,
                org.soutalab.celtium.vulkan.VulkanGlTexture.requiredUploadBytes("test", 2, 2, offset));
        expect("N08 the caller's position is unchanged", positionBefore, offset.position());
        expect("N08 the caller's limit is unchanged", limitBefore, offset.limit());

        // A short buffer is refused by name rather than read past: 15 readable bytes cannot hold 16.
        ByteBuffer shortBuffer = direct(15);
        expectRefused("N08 a short buffer is refused", "test", 2, 2, shortBuffer);
        // ... and the check counts the readable bytes, not the capacity: a 32-byte buffer with a limit of 8 is short.
        ByteBuffer limited = direct(32);
        limited.limit(8);
        expectRefused("N08 a buffer whose limit is short is refused", "test", 2, 2, limited);
        // A position that leaves too little is refused as well, even though the buffer is large.
        ByteBuffer latePosition = direct(32);
        latePosition.position(20);
        expectRefused("N08 a late position with too little remaining is refused", "test", 2, 2, latePosition);
        // Extents that are not positive, or whose byte count overflows, are refused before any arithmetic is trusted.
        expectRefused("N08 a zero width is refused", "test", 0, 4, direct(16));
        expectRefused("N08 a negative height is refused", "test", 4, -1, direct(16));
        expectRefused("N08 an extent above the int range is refused", "test", 1 << 15, 1 << 15, direct(16));
        expectRefused("N08 a null buffer is refused", "test", 2, 2, null);
        // A heap buffer has no native address to copy from, which is what the GL-less capture path relies on being
        // refused rather than silently mis-copied.
        expectRefused("N08 a heap buffer is refused", "test", 2, 2, ByteBuffer.allocate(16));

        checkHashSliceContract();
    }

    /**
     * N08, the hash half: the duplicate-detection hash must describe the same slice the copy takes.
     *
     * <p>The regression this fails on is the one the audit describes: hashing from absolute index 0 while the copy
     * starts at the buffer's position. With a non-zero position the hash window then ends before the payload does, so
     * a payload whose earlier bytes did not change hashes the same as the old content and the upload is skipped - the
     * changed texels never reach the image.
     */
    private static void checkHashSliceContract() {
        // Payload P1 at offset 4, with four bytes of prefix in front of it. The prefix is the same in every buffer, so
        // only the payload decides the hash of the slice the copy reads.
        ByteBuffer first = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        // The same payload: the hash must not depend on what precedes the position when that prefix is equal...
        ByteBuffer same = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        // ... and a payload that differs only in its LAST byte must hash differently, which is exactly the byte the
        // old window (absolute 0 for min(remaining/height, width*4)*height bytes) could miss.
        ByteBuffer changedTail = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 99});
        long hashFirst = org.soutalab.celtium.vulkan.VulkanGlTexture.hashUploadSlice(first, 16);
        expect("N08 the same slice hashes the same", true,
                hashFirst == org.soutalab.celtium.vulkan.VulkanGlTexture.hashUploadSlice(same, 16));
        expect("N08 a changed tail byte changes the hash of the slice", true,
                hashFirst != org.soutalab.celtium.vulkan.VulkanGlTexture.hashUploadSlice(changedTail, 16));
        // A different prefix in front of the same payload must not change the hash of the slice: the copy reads from
        // the position, so the hash must ignore what precedes it.
        ByteBuffer otherPrefix = sliced(4, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        ((ByteBuffer) otherPrefix).put(0, (byte) 0x77);
        expect("N08 the prefix before the position does not enter the slice hash", true,
                hashFirst == org.soutalab.celtium.vulkan.VulkanGlTexture.hashUploadSlice(otherPrefix, 16));
        // Hashing more than the buffer can read is refused by name rather than read past the end.
        try {
            org.soutalab.celtium.vulkan.VulkanGlTexture.hashUploadSlice(first, 17);
            failures++;
            System.out.println("  FAIL N08 a slice hash past the remaining bytes was accepted");
        } catch (IllegalArgumentException expected) {
            // The reason names the shortfall; nothing was read.
        }
        checkLegacyHashWindowWouldMissTheTail(first, changedTail);
    }

    /**
     * N08, the counterfactual: the pre-fix window really did miss the byte the fix reads.
     *
     * <p>Asserting that the new hash changes is only half the claim. The other half is that the old expression was
     * wrong in the way the audit says - a check that would have passed under the old code proves nothing about the
     * fix. The old window was {@code min(remaining / height, width * 4) * height} bytes from absolute index 0; this
     * computes both hashes over the same two buffers and requires that the old one does <em>not</em> see the changed
     * tail while the new one does. If a future edit reinstates the absolute-index window, this check fails first.
     */
    private static void checkLegacyHashWindowWouldMissTheTail(ByteBuffer first, ByteBuffer changedTail) {
        // The audit's own numbers for this case: a 4x4 upload from a buffer positioned at 4. The old window is
        // min(remaining/4, 4*4)*4 = 16 bytes from index 0, i.e. the four prefix bytes plus the first twelve payload
        // bytes - the last payload byte, the one that changed, is outside it.
        int height = 4;
        int width = 4;
        int remaining = first.remaining();
        int legacyWindow = Math.min(remaining / Math.max(1, height), width * 4) * height;
        expect("N08 the legacy window is the 16 bytes the audit describes", 16, legacyWindow);
        long legacyFirst = legacyHash(first, legacyWindow);
        long legacyChanged = legacyHash(changedTail, legacyWindow);
        expect("N08 the legacy window could not see the changed tail byte (the bug the fix removes)", true,
                legacyFirst == legacyChanged);
        // ... and the slice hash this renderer now uses does see it, over the same two buffers.
        expect("N08 the slice hash sees the byte the legacy window missed", true,
                org.soutalab.celtium.vulkan.VulkanGlTexture.hashUploadSlice(first, 16)
                        != org.soutalab.celtium.vulkan.VulkanGlTexture.hashUploadSlice(changedTail, 16));
    }

    /** The pre-fix hash, kept here as the counterfactual rather than as production code. */
    private static long legacyHash(ByteBuffer buffer, int bytes) {
        long hash = 1469598103934665603L;
        for (int i = 0; i < bytes; i++) {
            hash = (hash ^ (buffer.get(i) & 0xFF)) * 1099511628211L;
        }
        return hash;
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

    /**
     * N07: the per-level conversion reads {@code data[level]}, so an announcement's levels above 0 carry their own
     * pixels instead of level 0's (or zeroes). The regression this fails on: applying level 0's texels to every level,
     * or converting {@code data[0]} whatever level was asked for.
     */
    private static void checkPerLevelPatchConversion() {
        int[][] data = {
                {0x11223344, 0x55667788, 0x99AABBCC, 0xDDEEFF11},
                {0x0A0B0C0D, 0x1A1B1C1D, 0x2A2B2C2D, 0x3A3B3C3D},
        };
        ByteBuffer level0 = ByteBuffer.allocate(16);
        ByteBuffer level1 = ByteBuffer.allocate(16);
        org.soutalab.celtium.vulkan.VulkanTextureRegistry.toRgba(data, 2, 2, level0);
        org.soutalab.celtium.vulkan.VulkanTextureRegistry.toRgba(data, 1, 2, 2, level1);
        expect("N07 level 1 first texel red", 0x0B, level1.get(0) & 0xFF);
        expect("N07 level 1 first texel alpha", 0x0A, level1.get(3) & 0xFF);
        expect("N07 level 1 last texel red", 0x3B, level1.get(12) & 0xFF);
        expect("N07 level 1 differs from level 0", true, (level1.get(0) & 0xFF) != (level0.get(0) & 0xFF));
        // A level the producer did not supply converts to zeroes, which the caller refuses before it reaches the
        // image: it must not silently reuse level 0.
        ByteBuffer absent = ByteBuffer.allocate(16);
        org.soutalab.celtium.vulkan.VulkanTextureRegistry.toRgba(data, 5, 2, 2, absent);
        expect("N07 an absent level converts to zeroes, not to level 0", 0, absent.get(0) & 0xFF);
    }

    /**
     * Allocates a direct buffer of {@code bytes} whose readable range starts at position 0.
     *
     * <p>{@code ByteBuffer.allocateDirect} rather than {@code MemoryUtil.memAlloc} on purpose: this check is meant to
     * run with nothing but the renderer's classes on the classpath, and touching {@code MemoryUtil} would make it need
     * the LWJGL natives for a question that is about buffer slicing, not about native memory.
     */
    private static ByteBuffer direct(int bytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes);
        buffer.limit(bytes);
        return buffer;
    }

    /** Expects the slice check to refuse the arguments, with the declared extent named in the message. */
    private static void expectRefused(String what, String label, int width, int height, ByteBuffer pixels) {
        try {
            org.soutalab.celtium.vulkan.VulkanGlTexture.requiredUploadBytes(label, width, height, pixels);
            failures++;
            System.out.println("  FAIL " + what + ": the check accepted the buffer");
        } catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null || !expected.getMessage().contains(label)) {
                failures++;
                System.out.println("  FAIL " + what + ": the refusal does not name what was checked ("
                        + expected.getMessage() + ")");
            }
        }
    }

    /** A02: the outer index is the mip level and the inner one is a flat texel offset, not a row. */
    private static void checkFlatTexelContract() {
        int[][] data = {{11, 12, 21, 22}, {99}};
        ByteBuffer out = ByteBuffer.allocate(4 * 4);
        int written = org.soutalab.celtium.vulkan.VulkanTextureRegistry.toRgba(data, 2, 2, out);
        expect("A02 texels written for a 2x2 patch", 4, written);
        // Row-major within the level: 11 12 / 21 22, converted from ARGB to RGBA.
        int[] expected = {11, 12, 21, 22};
        for (int i = 0; i < 4; i++) {
            int argb = expected[i];
            expect("A02 texel " + i + " red", (argb >> 16) & 0xFF, out.get(i * 4) & 0xFF);
            expect("A02 texel " + i + " green", (argb >> 8) & 0xFF, out.get(i * 4 + 1) & 0xFF);
            expect("A02 texel " + i + " blue", argb & 0xFF, out.get(i * 4 + 2) & 0xFF);
            expect("A02 texel " + i + " alpha", (argb >>> 24) & 0xFF, out.get(i * 4 + 3) & 0xFF);
        }
        // Reading data[row] as a row would have produced 11,12,99,0 - the failure the audit described.
        expect("A02 the row-array reading would differ", true, (out.get(8) & 0xFF) != 99);
    }

    /** A03: two patches far apart, and every byte outside them unchanged. */
    private static void checkTwoIndependentPatchesPreserveTheRest() {
        int width = 8;
        int height = 4;
        byte[] image = new byte[width * height * 4];
        java.util.Arrays.fill(image, (byte) 0x5A);

        // Patch A: 2x2 at (1,2), an asymmetric pattern.
        int[][] patchA = {{0x11223344, 0x55667788, 0x99AABBCC, 0xDDEEFF11}};
        applyPatch(image, width, patchA, 2, 2, 1, 2);
        // Patch B: 3x1 at (5,0), a different colour pattern.
        int[][] patchB = {{0x0A0B0C0D, 0x1A1B1C1D, 0x2A2B2C2D}};
        applyPatch(image, width, patchB, 3, 1, 5, 0);

        int touched = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean inA = x >= 1 && x < 3 && y >= 2 && y < 4;
                boolean inB = x >= 5 && x < 8 && y == 0;
                if (!inA && !inB) {
                    for (int c = 0; c < 4; c++) {
                        expect("A03 untouched byte at " + x + "," + y + " channel " + c,
                                0x5A, image[(y * width + x) * 4 + c] & 0xFF);
                    }
                } else {
                    touched++;
                }
            }
        }
        expect("A03 touched texels", 4 + 3, touched);
        // The two patches landed where they were asked to, with the right channel order.
        expect("A03 patch A first texel alpha", 0x11, image[((2 * width) + 1) * 4 + 3] & 0xFF);
        expect("A03 patch A first texel red", 0x22, image[((2 * width) + 1) * 4] & 0xFF);
        // 0x2A2B2C2D is alpha=2A, red=2B, green=2C, blue=2D: the red byte of the last texel is 0x2B.
        expect("A03 patch B last texel red", 0x2B, image[(0 * width + 7) * 4] & 0xFF);
    }

    /** A32: one pass retires its own target's groups and leaves the rest. */
    private static void checkTwoIndependentTargets() {
        long targetA = 0xAAAAL;
        long targetB = 0xBBBBL;
        List<org.soutalab.celtium.vulkan.ImmediateGroup> groups = new ArrayList<>();
        groups.add(group(targetA));
        groups.add(group(targetB));
        groups.add(group(targetA));
        groups.add(group(targetB));
        groups.add(null); // a group the queue cannot attribute must be kept, not retired

        int kept = org.soutalab.celtium.vulkan.ImmediateGroupQueue.retireGroupsForTarget(groups, targetA);
        expect("A32 groups kept after retiring A", 3, kept);
        expect("A32 groups left in the queue", 3, groups.size());
        for (org.soutalab.celtium.vulkan.ImmediateGroup group : groups) {
            if (group != null) {
                expect("A32 only B remains", targetB, group.target);
            }
        }
        int keptB = org.soutalab.celtium.vulkan.ImmediateGroupQueue.retireGroupsForTarget(groups, targetB);
        expect("A32 groups kept after retiring B", 1, keptB);
        expect("A32 the unattributable group survives", 1, groups.size());
    }

    private static void applyPatch(byte[] image, int imageWidth, int[][] data, int w, int h, int x, int y) {
        ByteBuffer rgba = ByteBuffer.allocate(w * h * 4);
        org.soutalab.celtium.vulkan.VulkanTextureRegistry.toRgba(data, w, h, rgba);
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int destination = ((y + row) * imageWidth + (x + col)) * 4;
                int source = (row * w + col) * 4;
                for (int c = 0; c < 4; c++) {
                    image[destination + c] = rgba.get(source + c);
                }
            }
        }
    }

    private static org.soutalab.celtium.vulkan.ImmediateGroup group(long target) {
        return new org.soutalab.celtium.vulkan.ImmediateGroup(null, null, null, 0, 0, 0L, null, target);
    }

    private static void expect(String what, long expected, long actual) {
        if (expected != actual) {
            failures++;
            System.out.println("  FAIL " + what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void expect(String what, boolean expected, boolean actual) {
        if (expected != actual) {
            failures++;
            System.out.println("  FAIL " + what + ": expected " + expected + " but was " + actual);
        }
    }
}
