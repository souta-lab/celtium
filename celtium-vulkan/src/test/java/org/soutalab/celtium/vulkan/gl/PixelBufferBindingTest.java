package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code glBindBuffer} through the stub's own dispatcher ({@code SEM_BIND_BUFFER}) must update {@code pixelPackBuffer}
 * and {@code pixelUnpackBuffer} identically to {@code celtium$bindBuffer}, and {@code forgetBuffer} must clear both on
 * delete so deleted PBO names do not linger and break subsequent client-pointer uploads or readbacks.
 */
class PixelBufferBindingTest {
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB;
    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;
    private static final int GL_PIXEL_PACK_BUFFER_BINDING = 0x88ED;
    private static final int GL_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;
    private static final int GL_STATIC_DRAW = 0x88E4;

    private final CeltiumGlStub stub;
    private final Method dispatch;
    private final Map<?, ?> ids;

    PixelBufferBindingTest() throws Exception {
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
        long[] slots = new long[16];
        System.arraycopy(args, 0, slots, 0, args.length);
        return (Long) this.dispatch.invoke(this.stub, this.ids.get(name), slots);
    }

    @Test
    void dispatchBindAndDeleteMaintainPixelBufferBindings() throws Exception {
        ByteBuffer nameBuf = MemoryUtil.memAlloc(4);
        ByteBuffer out = MemoryUtil.memAlloc(4);
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        try {
            pixel.putInt(0, 0xFF112233);
            call("glGenBuffers", 1, MemoryUtil.memAddress(nameBuf));
            int pbo = nameBuf.getInt(0);

            call("glBindBuffer", GL_PIXEL_UNPACK_BUFFER, pbo);
            call("glGetIntegerv", GL_PIXEL_UNPACK_BUFFER_BINDING, MemoryUtil.memAddress(out));
            assertEquals(pbo, out.getInt(0), "glBindBuffer(GL_PIXEL_UNPACK_BUFFER) must update pixelUnpackBuffer");

            call("glBufferData", GL_PIXEL_UNPACK_BUFFER, 4, MemoryUtil.memAddress(pixel), GL_STATIC_DRAW);
            Method uploadToRgba8 = CeltiumGlStub.class.getDeclaredMethod(
                    "uploadToRgba8", int.class, int.class, int.class, int.class, long.class);
            uploadToRgba8.setAccessible(true);
            assertNotNull(uploadToRgba8.invoke(this.stub, 1, 1, 0x1908, 0x1401, 0L),
                    "1x1 upload from bound PBO at offset 0 must resolve storage");

            call("glBindBuffer", GL_PIXEL_PACK_BUFFER, pbo);
            call("glGetIntegerv", GL_PIXEL_PACK_BUFFER_BINDING, MemoryUtil.memAddress(out));
            assertEquals(pbo, out.getInt(0), "glBindBuffer(GL_PIXEL_PACK_BUFFER) must update pixelPackBuffer");

            call("glDeleteBuffers", 1, MemoryUtil.memAddress(nameBuf));
            call("glGetIntegerv", GL_PIXEL_UNPACK_BUFFER_BINDING, MemoryUtil.memAddress(out));
            assertEquals(0, out.getInt(0), "deleting bound unpack PBO must clear pixelUnpackBuffer");
            call("glGetIntegerv", GL_PIXEL_PACK_BUFFER_BINDING, MemoryUtil.memAddress(out));
            assertEquals(0, out.getInt(0), "deleting bound pack PBO must clear pixelPackBuffer");
        } finally {
            MemoryUtil.memFree(nameBuf);
            MemoryUtil.memFree(out);
            MemoryUtil.memFree(pixel);
        }
    }
}
