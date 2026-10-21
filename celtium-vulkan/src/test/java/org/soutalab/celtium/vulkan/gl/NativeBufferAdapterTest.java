package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Calls the real mod adapter's dispatcher, without creating a GL context or a Vulkan device. */
class NativeBufferAdapterTest {
    private final CeltiumGlStub stub;
    private final Method dispatch;
    private final Map<?, ?> ids;

    NativeBufferAdapterTest() throws Exception {
        var constructor = CeltiumGlStub.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        this.stub = constructor.newInstance();
        this.dispatch = CeltiumGlStub.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        this.dispatch.setAccessible(true);
        Field table = CeltiumGlStub.class.getDeclaredField("IDS");
        table.setAccessible(true);
        this.ids = (Map<?, ?>) table.get(null);
    }

    private long call(String name, long... args) throws Exception {
        assertTrue(this.ids.containsKey(name), "test must exercise a real adapter entry: " + name);
        long[] slots = new long[16];
        System.arraycopy(args, 0, slots, 0, args.length);
        return (Long) this.dispatch.invoke(this.stub, this.ids.get(name), slots);
    }

    @Test
    void nativeAndRawBindingsHaveOneSaveRestoreStateWithoutNativeCallingTheStub() throws Exception {
        ByteBuffer out = MemoryUtil.memAlloc(16);
        try (NativeGeometryBuffer buffer = new NativeGeometryBuffer()) {
            call("glBindBuffer", 0x8892, 123456);
            buffer.bind();
            call("glGetIntegerv", 0x8894, MemoryUtil.memAddress(out));
            assertEquals(buffer.id(), out.getInt(0));
            NativeGeometryBuffer.unbind();
            call("glGetIntegerv", 0x8894, MemoryUtil.memAddress(out));
            assertEquals(0, out.getInt(0), "must not restore the stale raw binding");
            call("glBindBuffer", 0x8892, buffer.id());
            assertSame(buffer, NativeGeometryBuffer.bound());
            call("glBindBuffer", 0x8892, 0);
            assertNull(NativeGeometryBuffer.bound());
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    @Test
    void rawDataUpdatesAndTransientMappingsInvalidateTheNativeGeneration() throws Exception {
        ByteBuffer source = MemoryUtil.memAlloc(48);
        ByteBuffer out = MemoryUtil.memAlloc(16);
        try (NativeGeometryBuffer buffer = new NativeGeometryBuffer()) {
            source.putInt(0, 17);
            buffer.upload(source.duplicate().limit(24), 12, 7);
            buffer.bind();
            // Raw glBufferData changes storage, not the VertexBuffer API's count field.
            call("glBufferData", 0x8892, 48, MemoryUtil.memAddress(source), 0x88E4);
            assertEquals(2, buffer.vertexCount());
            call("glGetBufferParameteriv", 0x8892, 0x8764, MemoryUtil.memAddress(out));
            assertEquals(48, out.getInt(0));
            source.putInt(0, 23);
            call("glBufferSubData", 0x8892, 4, 4, MemoryUtil.memAddress(source));
            assertEquals(23, MemoryUtil.memGetInt(buffer.address() + 4));
            long mapped = call("glMapBufferRange", 0x8892, 4, 8, 0x2);
            assertNotEquals(0, mapped);
            MemoryUtil.memPutInt(mapped, 31);
            assertThrows(IllegalStateException.class, buffer::address);
            assertEquals(1, call("glUnmapBuffer", 0x8892));
            assertEquals(31, MemoryUtil.memGetInt(buffer.address() + 4));
            assertEquals(0, call("glMapBufferRange", 0x8892, -1, 8, 0x2));
            assertEquals(0, call("glMapBufferRange", 0x8892, 0, 8, 0x42), "persistent mapping is not advertised");
        } finally {
            MemoryUtil.memFree(source);
            MemoryUtil.memFree(out);
        }
    }

    @Test
    void namesAreCollisionFreeAndRawDeletionRetiresTheNativeResource() throws Exception {
        ByteBuffer out = MemoryUtil.memAlloc(16);
        try (NativeGeometryBuffer buffer = new NativeGeometryBuffer()) {
            call("glGenBuffers", 1, MemoryUtil.memAddress(out));
            assertNotEquals(buffer.id(), out.getInt(0));
            assertEquals(1, call("glIsBuffer", buffer.id()));
            buffer.bind();
            out.putInt(0, buffer.id());
            call("glDeleteBuffers", 1, MemoryUtil.memAddress(out));
            assertNull(NativeGeometryBuffer.find(buffer.id()));
            assertEquals(0, NativeGeometryBuffer.boundId());
            assertEquals(0, call("glIsBuffer", buffer.id()));
            assertThrows(IllegalStateException.class, buffer::address);
        } finally {
            MemoryUtil.memFree(out);
        }
    }
}
