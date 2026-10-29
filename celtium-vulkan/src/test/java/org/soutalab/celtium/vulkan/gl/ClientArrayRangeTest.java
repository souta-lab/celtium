package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

/** Windows regression: a positioned BufferBuilder still publishes its allocation base. */
class ClientArrayRangeTest {
    @Test void nonzeroBufferPositionNeverShiftsThePublishedAllocation() {
        ByteBuffer data = MemoryUtil.memAlloc(256);
        try {
            long expected = MemoryUtil.memAddress(data, 0);
            data.position(20);
            assertEquals(expected, ClientArrayRange.base(data));
            assertEquals(expected + 20, MemoryUtil.memAddress(data),
                    "original regression: position-sensitive memAddress was published as the start");
            assertTrue(ClientArrayRange.covers(ClientArrayRange.base(data), data.capacity(), expected, 96));
            assertTrue(ClientArrayRange.covers(ClientArrayRange.base(data), data.capacity(), expected + 240, 16));
            assertFalse(ClientArrayRange.covers(ClientArrayRange.base(data), data.capacity(), expected - 20, 96));
            assertFalse(ClientArrayRange.covers(ClientArrayRange.base(data), data.capacity(), expected + 240, 17));
            assertFalse(ClientArrayRange.covers(ClientArrayRange.base(data), data.capacity(), Long.MAX_VALUE, 96));
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
