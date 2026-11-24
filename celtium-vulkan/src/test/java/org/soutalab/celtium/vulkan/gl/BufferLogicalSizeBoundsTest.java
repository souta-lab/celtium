package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code readBuffer} and {@code copyBuffer} must honour {@code bufferLogicalSize} after {@code glBufferData} shrinks a
 * buffer (reusing its larger direct allocation) and guard range sums against {@code long} overflow.
 */
class BufferLogicalSizeBoundsTest {
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_STATIC_DRAW = 0x88E4;

    private final GlsmFrontend stub;
    private final Method dispatch;
    private final Map<?, ?> ids;

    BufferLogicalSizeBoundsTest() throws Exception {
        var constructor = GlsmFrontend.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        this.stub = constructor.newInstance();
        this.dispatch = GlsmFrontend.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        this.dispatch.setAccessible(true);
        Field table = GlsmFrontend.class.getDeclaredField("IDS");
        table.setAccessible(true);
        this.ids = (Map<?, ?>) table.get(null);
    }

    private long call(String name, long... args) throws Exception {
        long[] slots = new long[16];
        System.arraycopy(args, 0, slots, 0, args.length);
        return (Long) this.dispatch.invoke(this.stub, this.ids.get(name), slots);
    }

    private int genAndBind(int size, byte fill) throws Exception {
        ByteBuffer nameBuf = MemoryUtil.memAlloc(4);
        ByteBuffer data = MemoryUtil.memAlloc(size);
        try {
            call("glGenBuffers", 1, MemoryUtil.memAddress(nameBuf));
            int name = nameBuf.getInt(0);
            for (int i = 0; i < size; i++) {
                data.put(i, fill);
            }
            call("glBindBuffer", GL_ARRAY_BUFFER, name);
            call("glBufferData", GL_ARRAY_BUFFER, size, MemoryUtil.memAddress(data), GL_STATIC_DRAW);
            return name;
        } finally {
            MemoryUtil.memFree(nameBuf);
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void readAndCopyRefuseOffsetsPastShrunkLogicalSize() throws Exception {
        int src = genAndBind(64, (byte) 0x11);
        int dst = genAndBind(64, (byte) 0x22);

        ByteBuffer small = MemoryUtil.memAlloc(16);
        ByteBuffer out = MemoryUtil.memAlloc(16);
        try {
            for (int i = 0; i < 16; i++) {
                small.put(i, (byte) 0x33);
                out.put(i, (byte) 0x7F);
            }
            // Shrink both buffers to 16 bytes; backing ByteBuffers keep 64-byte capacity for pointer stability.
            call("glBindBuffer", GL_ARRAY_BUFFER, src);
            call("glBufferData", GL_ARRAY_BUFFER, 16, MemoryUtil.memAddress(small), GL_STATIC_DRAW);
            call("glBindBuffer", GL_ARRAY_BUFFER, dst);
            call("glBufferData", GL_ARRAY_BUFFER, 16, MemoryUtil.memAddress(small), GL_STATIC_DRAW);

            this.stub.readBuffer(src, 16L, 16L, MemoryUtil.memAddress(out));
            assertEquals((byte) 0x7F, out.get(0),
                    "readBuffer must not expose stale bytes past the shrunk logical size");

            // Valid read within [0, 16) still works.
            this.stub.readBuffer(src, 0L, 16L, MemoryUtil.memAddress(out));
            assertEquals((byte) 0x33, out.get(0));

            // Writing/reading past shrunk logical size via copyBuffer must be refused.
            ByteBuffer marker = MemoryUtil.memAlloc(16);
            try {
                for (int i = 0; i < 16; i++) {
                    marker.put(i, (byte) 0x44);
                }
                call("glBindBuffer", GL_ARRAY_BUFFER, src);
                call("glBufferSubData", GL_ARRAY_BUFFER, 0L, 16L, MemoryUtil.memAddress(marker));

                // Write past dst logical size (offset 16 in 16B logical buffer, backed by 64B): must be refused.
                this.stub.copyBuffer(src, dst, 0L, 16L, 16L);
                // Read past src logical size into dst[0..16): must not overwrite dst's 0x33 bytes.
                this.stub.copyBuffer(src, dst, 16L, 0L, 16L);
                this.stub.readBuffer(dst, 0L, 16L, MemoryUtil.memAddress(out));
                assertEquals((byte) 0x33, out.get(0),
                        "copyBuffer must not read past source logical size");

                // Overflowing offset + size must be rejected without wrapping negative.
                for (int i = 0; i < 16; i++) {
                    out.put(i, (byte) 0x7F);
                }
                this.stub.readBuffer(src, 8L, Long.MAX_VALUE, MemoryUtil.memAddress(out));
                assertEquals((byte) 0x7F, out.get(0),
                        "readBuffer must reject overflowing size instead of wrapping offset + size negative");
                this.stub.copyBuffer(src, dst, 0L, 8L, Long.MAX_VALUE);
                this.stub.readBuffer(dst, 0L, 16L, MemoryUtil.memAddress(out));
                assertEquals((byte) 0x33, out.get(0),
                        "copyBuffer must reject overflowing write ranges");
            } finally {
                MemoryUtil.memFree(marker);
            }
        } finally {
            MemoryUtil.memFree(small);
            MemoryUtil.memFree(out);
        }
    }

    @Test
    void unpackBufferTextureUploadRefusesOffsetsPastShrunkLogicalSize() throws Exception {
        int pbo = genAndBind(64, (byte) 0x55);
        ByteBuffer small = MemoryUtil.memAlloc(4);
        try {
            small.putInt(0, 0x11223344);
            // Shrink PBO to 4 bytes while keeping the underlying 64-byte ByteBuffer.
            call("glBindBuffer", 0x88EC, pbo); // GL_PIXEL_UNPACK_BUFFER
            call("glBufferData", 0x88EC, 4, MemoryUtil.memAddress(small), GL_STATIC_DRAW);

            Method uploadToRgba8 = GlsmFrontend.class.getDeclaredMethod(
                    "uploadToRgba8", int.class, int.class, int.class, int.class, long.class);
            uploadToRgba8.setAccessible(true);
            Object result = uploadToRgba8.invoke(this.stub, 4, 4, 0x1908, 0x1401, 0L);
            assertNull(result,
                    "PBO upload of 4x4 RGBA8 (64B) must be refused when buffer was shrunk to 4B logical size");
        } finally {
            MemoryUtil.memFree(small);
        }
    }

    @Test
    void bufferSubDataRefusesShrunkLogicalSizeAndOverflow() throws Exception {
        int buf = genAndBind(64, (byte) 0x11);
        ByteBuffer small = MemoryUtil.memAlloc(16);
        ByteBuffer marker = MemoryUtil.memAlloc(16);
        ByteBuffer out = MemoryUtil.memAlloc(16);
        try {
            for (int i = 0; i < 16; i++) {
                small.put(i, (byte) 0x33);
                marker.put(i, (byte) 0x55);
            }
            call("glBindBuffer", GL_ARRAY_BUFFER, buf);
            call("glBufferData", GL_ARRAY_BUFFER, 16, MemoryUtil.memAddress(small), GL_STATIC_DRAW);

            // 1. Offset + size past shrunk 16B logical size (still within 64B backing) must be refused.
            call("glBufferSubData", GL_ARRAY_BUFFER, 8L, 16L, MemoryUtil.memAddress(marker));
            this.stub.readBuffer(buf, 0L, 16L, MemoryUtil.memAddress(out));
            assertEquals((byte) 0x33, out.get(8),
                    "glBufferSubData must not overwrite past shrunk logical size");

            // 2. Overflowing offset + size where (int) offset == 0 and (int) size == 8 must not wrap negative and clobber offset 0.
            call("glBufferSubData", GL_ARRAY_BUFFER, 0x7FFF_FFFF_0000_0000L, 0x0000_0001_0000_0008L,
                    MemoryUtil.memAddress(marker));
            this.stub.readBuffer(buf, 0L, 16L, MemoryUtil.memAddress(out));
            assertEquals((byte) 0x33, out.get(0),
                    "glBufferSubData must reject overflowing offset + size without clobbering offset 0");

            // 3. Overflowing offset near Long.MAX_VALUE must return cleanly instead of throwing IllegalArgumentException.
            call("glBufferSubData", GL_ARRAY_BUFFER, Long.MAX_VALUE - 4L, 16L, MemoryUtil.memAddress(marker));
            this.stub.readBuffer(buf, 0L, 16L, MemoryUtil.memAddress(out));
            assertEquals((byte) 0x33, out.get(0));
        } finally {
            MemoryUtil.memFree(small);
            MemoryUtil.memFree(marker);
            MemoryUtil.memFree(out);
        }
    }
}
