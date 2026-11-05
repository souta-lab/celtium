package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/** The int-wide conversion writes the same R, G, B, A bytes the per-byte version wrote, in either buffer order. */
class ToRgbaTest {
    private static byte[] reference(int[] texels, int width, int height) {
        byte[] out = new byte[width * height * 4];
        int i = 0;
        for (int t = 0; t < width * height; t++) {
            int argb = t < texels.length ? texels[t] : 0;
            out[i++] = (byte) ((argb >> 16) & 0xFF);
            out[i++] = (byte) ((argb >> 8) & 0xFF);
            out[i++] = (byte) (argb & 0xFF);
            out[i++] = (byte) ((argb >>> 24) & 0xFF);
        }
        return out;
    }

    private static void check(ByteOrder order, boolean direct) {
        int[] texels = { 0x80FF4020, 0x00000000, 0xFFFFFFFF, 0x7F123456, 0xDEADBEEF };
        int width = 3;
        int height = 2; // six texels from five: the last converts to zero
        ByteBuffer out = (direct ? ByteBuffer.allocateDirect(width * height * 4) : ByteBuffer.allocate(width * height * 4))
                .order(order);
        assertEquals(6, VulkanTextureRegistry.toRgba(new int[][] { texels }, 0, width, height, out));
        assertEquals(out.capacity(), out.position());
        byte[] got = new byte[out.capacity()];
        out.flip();
        out.get(got);
        assertArrayEquals(reference(texels, width, height), got, order + (direct ? " direct" : " heap"));
    }

    @Test void bigEndianHeap() {
        check(ByteOrder.BIG_ENDIAN, false);
    }

    @Test void littleEndianHeap() {
        check(ByteOrder.LITTLE_ENDIAN, false);
    }

    @Test void nativeDirect() {
        check(ByteOrder.nativeOrder(), true);
    }

    @Test void missingLevelConvertsToZero() {
        ByteBuffer out = ByteBuffer.allocate(16);
        VulkanTextureRegistry.toRgba(new int[][] { null }, 0, 2, 2, out);
        assertArrayEquals(new byte[16], out.array());
    }
}
