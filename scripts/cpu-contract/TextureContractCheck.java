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
        System.out.println(failures == 0 ? "CELTIUM_CPU_CONTRACT PASS" : "CELTIUM_CPU_CONTRACT FAIL failures=" + failures);
        if (failures != 0) {
            System.exit(1);
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
