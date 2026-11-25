package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedQuadIndexTest {

    @Test
    void patternForTwoQuads() {
        assertArrayEquals(new int[]{0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7},
                SharedQuadIndex.generate(2));
    }

    @Test
    void countsAndSizes() {
        assertEquals(0, SharedQuadIndex.indexCountForQuads(0));
        assertEquals(6, SharedQuadIndex.indexCountForQuads(1));
        assertEquals(SharedQuadIndex.MAX_QUADS * 6,
                SharedQuadIndex.indexCountForQuads(SharedQuadIndex.MAX_QUADS));
        assertEquals(SharedQuadIndex.MAX_QUADS * 6 * 4,
                SharedQuadIndex.byteSizeForQuads(SharedQuadIndex.MAX_QUADS));
    }

    @Test
    void outOfRangeQuadCountsThrow() {
        assertThrows(IllegalArgumentException.class, () -> SharedQuadIndex.generate(-1));
        assertThrows(IllegalArgumentException.class,
                () -> SharedQuadIndex.generate(SharedQuadIndex.MAX_QUADS + 1));
    }

    @Test
    void byteBufferFillMatchesArrayPattern() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(SharedQuadIndex.byteSizeForQuads(3));
        SharedQuadIndex.fill(buffer, 3);
        buffer.flip();
        int[] expected = SharedQuadIndex.generate(3);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], buffer.getInt(i * 4), "index " + i);
        }
    }

    @Test
    void byteBufferFillRejectsASmallBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(8);
        assertThrows(IllegalArgumentException.class, () -> SharedQuadIndex.fill(buffer, 2));
    }
}
