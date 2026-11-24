package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link GlsmFrontend#releaseSettledTextureCaptures()} (item 387 / 361-B) must drop exactly the settled
 * copies: a full upload a reader has already taken in full. A level that was ever patched, never supplied,
 * or allocated without data keeps its copy - a patch after a release would be pasted into a fresh undefined
 * surface and zero the rest of the level, and an unsupplied copy is the only one there is.
 */
class TextureCaptureReleaseTest {
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_UNSIGNED_BYTE = 0x1401;

    @Test
    void releasesOnlySettledFullUploads() throws Exception {
        var ctor = GlsmFrontend.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        GlsmFrontend stub = ctor.newInstance();
        Method dispatch = GlsmFrontend.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        dispatch.setAccessible(true);
        Field idsField = GlsmFrontend.class.getDeclaredField("IDS");
        idsField.setAccessible(true);
        Map<?, ?> ids = (Map<?, ?>) idsField.get(null);
        Field installedField = GlsmFrontend.class.getDeclaredField("installed");
        installedField.setAccessible(true);
        Object previous = installedField.get(null);
        installedField.set(null, stub);

        ByteBuffer full = MemoryUtil.memAlloc(4 * 4 * 4);
        ByteBuffer patch = MemoryUtil.memAlloc(4);
        patch.putInt(0, 0x7F6A5B4C);
        try {
            // Texture 10: full upload, then supplied - the one settled copy.
            uploadFull(dispatch, stub, ids, 10, full);
            // Texture 11: full upload, then a patch - patched levels are never released.
            uploadFull(dispatch, stub, ids, 11, full);
            invoke(dispatch, stub, ids, "glTexSubImage2D", GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA,
                    GL_UNSIGNED_BYTE, MemoryUtil.memAddress(patch));
            // Texture 12: full upload, never supplied - the mirror never took it.
            uploadFull(dispatch, stub, ids, 12, full);
            // Texture 13: allocated with no data - an undefined surface, not game content.
            invoke(dispatch, stub, ids, "glBindTexture", GL_TEXTURE_2D, 13);
            invoke(dispatch, stub, ids, "glTexImage2D", GL_TEXTURE_2D, 0, GL_RGBA, 4, 4, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, 0L);

            assertNotNull(GlsmFrontend.textureUploadRgba8(10, 0, null));
            assertNotNull(GlsmFrontend.textureUploadRgba8(11, 0, null));
            assertNotNull(GlsmFrontend.textureUploadRgba8(13, 0, null));

            long[] released = GlsmFrontend.releaseSettledTextureCaptures();
            assertEquals(1L, released[0], "exactly the settled level is released");
            assertEquals(64L, released[1], "a 4x4 RGBA8 level is 64 bytes");

            assertNull(GlsmFrontend.textureUploadRgba8(10, 0, null), "the settled copy is gone");
            assertNotNull(GlsmFrontend.textureUploadRgba8(11, 0, null), "the patched copy stays");
            assertNotNull(GlsmFrontend.textureUploadRgba8(12, 0, null), "the unsupplied copy stays");
            assertNotNull(GlsmFrontend.textureUploadRgba8(13, 0, null), "the undefined surface stays");
        } finally {
            installedField.set(null, previous);
            MemoryUtil.memFree(full);
            MemoryUtil.memFree(patch);
        }
    }

    private static void uploadFull(Method dispatch, GlsmFrontend stub, Map<?, ?> ids, int name,
            ByteBuffer pixels) throws Exception {
        invoke(dispatch, stub, ids, "glBindTexture", GL_TEXTURE_2D, name);
        invoke(dispatch, stub, ids, "glTexImage2D", GL_TEXTURE_2D, 0, GL_RGBA, 4, 4, 0, GL_RGBA,
                GL_UNSIGNED_BYTE, MemoryUtil.memAddress(pixels));
    }

    private static void invoke(Method dispatch, GlsmFrontend stub, Map<?, ?> ids, String name, long... args)
            throws Exception {
        long[] c = new long[16];
        System.arraycopy(args, 0, c, 0, args.length);
        dispatch.invoke(stub, ids.get(name), c);
    }
}
