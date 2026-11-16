package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CeltiumGlStub#textureCaptureSummary()} must accurately report patch keys and branch counts without boxed
 * per-patch map entries.
 */
class TexturePatchCensusTest {
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_UNSIGNED_BYTE = 0x1401;

    @Test
    void reportsPatchKeysAndBranchesAcrossPasteAndLoneUploads() throws Exception {
        var ctor = CeltiumGlStub.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        CeltiumGlStub stub = ctor.newInstance();
        Method dispatch = CeltiumGlStub.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        dispatch.setAccessible(true);
        Field idsField = CeltiumGlStub.class.getDeclaredField("IDS");
        idsField.setAccessible(true);
        Map<?, ?> ids = (Map<?, ?>) idsField.get(null);
        Field installedField = CeltiumGlStub.class.getDeclaredField("installed");
        installedField.setAccessible(true);
        Object previous = installedField.get(null);
        installedField.set(null, stub);

        ByteBuffer full = MemoryUtil.memAlloc(4 * 4 * 4);
        ByteBuffer patch = MemoryUtil.memAlloc(4);
        patch.putInt(0, 0x11223344);
        try {
            // Texture 10: 4x4 full upload followed by two 1x1 sub-image pastes.
            invoke(dispatch, stub, ids, "glBindTexture", GL_TEXTURE_2D, 10);
            invoke(dispatch, stub, ids, "glTexImage2D", GL_TEXTURE_2D, 0, GL_RGBA, 4, 4, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, MemoryUtil.memAddress(full));
            invoke(dispatch, stub, ids, "glTexSubImage2D", GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA,
                    GL_UNSIGNED_BYTE, MemoryUtil.memAddress(patch));
            invoke(dispatch, stub, ids, "glTexSubImage2D", GL_TEXTURE_2D, 0, 1, 0, 1, 1, GL_RGBA,
                    GL_UNSIGNED_BYTE, MemoryUtil.memAddress(patch));

            // Texture 11: lone 1x1 sub-image without prior extent/full upload.
            invoke(dispatch, stub, ids, "glBindTexture", GL_TEXTURE_2D, 11);
            invoke(dispatch, stub, ids, "glTexSubImage2D", GL_TEXTURE_2D, 0, 2, 3, 1, 1, GL_RGBA,
                    GL_UNSIGNED_BYTE, MemoryUtil.memAddress(patch));

            String summary = CeltiumGlStub.textureCaptureSummary();
            assertTrue(summary.contains("10.0=2[4x4@0,0]"), "expected paste key census in: " + summary);
            assertTrue(summary.contains("11.0=1[1x1@2,3]"), "expected lone key census in: " + summary);
            assertTrue(summary.contains("branches={lone=1, paste=2}"), "expected branch counts in: " + summary);
        } finally {
            installedField.set(null, previous);
            MemoryUtil.memFree(full);
            MemoryUtil.memFree(patch);
        }
    }

    private static void invoke(Method dispatch, CeltiumGlStub stub, Map<?, ?> ids, String name, long... args)
            throws Exception {
        long[] c = new long[16];
        System.arraycopy(args, 0, c, 0, args.length);
        dispatch.invoke(stub, ids.get(name), c);
    }
}
