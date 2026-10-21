package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeGeometryBufferTest {
    @Test
    void uploadOwnsExactlyTheRemainingBytesWithoutChangingTheSourceCursor() {
        ByteBuffer source = ByteBuffer.allocate(32);
        for (int i = 0; i < 32; i++) source.put(i, (byte) i);
        source.position(4).limit(28);
        try (NativeGeometryBuffer buffer = new NativeGeometryBuffer()) {
            buffer.upload(source, 12, VulkanImmediateFormats.POSITION);
            assertEquals(4, source.position());
            assertEquals(28, source.limit());
            assertEquals(2, buffer.vertexCount());
            assertEquals(12, buffer.stride());
            assertEquals(VulkanImmediateFormats.POSITION, buffer.formatId());
            assertEquals(4, MemoryUtil.memGetByte(buffer.address()));
            assertEquals(27, MemoryUtil.memGetByte(buffer.address() + 23));
            source.put(4, (byte) 99);
            assertEquals(4, MemoryUtil.memGetByte(buffer.address()));
            assertTrue(buffer.compatibilityView().isReadOnly());
        }
    }

    @Test
    void invalidUploadDoesNotRetireThePreviousGeneration() {
        try (NativeGeometryBuffer buffer = new NativeGeometryBuffer()) {
            buffer.upload(ByteBuffer.allocate(24), 12, 7);
            List<String> invalidated = new ArrayList<>();
            buffer.onInvalidate(this, () -> invalidated.add("old"));
            long old = buffer.address();
            assertThrows(IllegalArgumentException.class, () -> buffer.upload(ByteBuffer.allocate(23), 12, 7));
            assertEquals(old, buffer.address());
            assertTrue(invalidated.isEmpty());
            assertEquals(2, buffer.vertexCount());
        }
    }

    @Test
    void uploadInvalidatesEveryConsumerExactlyOnceAndEmptyDataReplacesOldGeometry() {
        try (NativeGeometryBuffer buffer = new NativeGeometryBuffer()) {
            buffer.upload(ByteBuffer.allocate(24), 12, 7);
            List<String> invalidated = new ArrayList<>();
            Object deviceA = new Object();
            buffer.onInvalidate(deviceA, () -> invalidated.add("obsolete callback"));
            buffer.onInvalidate(deviceA, () -> invalidated.add("device A"));
            buffer.onInvalidate(new Object(), () -> invalidated.add("device B"));
            buffer.upload(ByteBuffer.allocate(0), 12, 7);
            assertEquals(2, invalidated.size());
            assertTrue(invalidated.containsAll(List.of("device A", "device B")));
            assertEquals(0, buffer.address());
            assertEquals(0, buffer.vertexCount());
            buffer.upload(ByteBuffer.allocate(12), 12, 7);
            assertEquals(2, invalidated.size());
        }
    }

    @Test
    void backendDetachDoesNotDestroyTheCpuDataNeededByAReplacementDevice() {
        try (NativeGeometryBuffer buffer = new NativeGeometryBuffer()) {
            buffer.upload(ByteBuffer.allocate(12), 12, 7);
            Object device = new Object();
            buffer.onInvalidate(device, () -> fail("detached backend must not be called"));
            buffer.detach(device);
            assertNotEquals(0, buffer.address());
            assertEquals(1, buffer.vertexCount());
        }
    }

    @Test
    void replacementOrDeletionRetiresOwnersButNotAcceptedDrawLeases() {
        NativeGeometryBuffer buffer = new NativeGeometryBuffer();
        List<String> destroyed = new ArrayList<>();
        LeasedResource<String> old = new LeasedResource<>("old GPU generation", destroyed::add);
        LeasedResource.Lease<String> draw = old.acquire();
        buffer.upload(ByteBuffer.allocate(12), 12, 7);
        buffer.onInvalidate(this, old::close);
        buffer.upload(ByteBuffer.allocate(24), 12, 7);
        buffer.close();
        assertTrue(destroyed.isEmpty());
        assertEquals("old GPU generation", draw.value());
        draw.close();
        assertEquals(List.of("old GPU generation"), destroyed);
    }

    @Test
    void invalidationFailureStillVisitsEveryConsumerAndDoesNotReplaceCpuStorage() {
        try (NativeGeometryBuffer buffer = new NativeGeometryBuffer()) {
            buffer.upload(ByteBuffer.allocate(12), 12, 7);
            long old = buffer.address();
            List<String> visited = new ArrayList<>();
            buffer.onInvalidate(this, () -> { throw new IllegalStateException("device cleanup failed"); });
            buffer.onInvalidate(new Object(), () -> visited.add("other device released"));
            assertThrows(IllegalStateException.class, () -> buffer.upload(ByteBuffer.allocate(24), 12, 7));
            assertEquals(old, buffer.address());
            assertEquals(1, buffer.vertexCount());
            assertEquals(List.of("other device released"), visited);
        }
    }

    @Test
    void closingAnOldBufferDoesNotUnbindAnotherAndDeletedDataCannotBeReused() {
        NativeGeometryBuffer first = new NativeGeometryBuffer();
        NativeGeometryBuffer second = new NativeGeometryBuffer();
        try {
            first.bind();
            assertSame(first, NativeGeometryBuffer.bound());
            second.bind();
            first.close();
            assertSame(second, NativeGeometryBuffer.bound());
            first.close();
            assertThrows(IllegalStateException.class, first::bind);
            assertThrows(IllegalStateException.class, first::address);
            assertThrows(IllegalStateException.class, () -> first.upload(ByteBuffer.allocate(12), 12, 7));
            second.close();
            assertNull(NativeGeometryBuffer.bound());
        } finally {
            first.close();
            second.close();
        }
    }
}
