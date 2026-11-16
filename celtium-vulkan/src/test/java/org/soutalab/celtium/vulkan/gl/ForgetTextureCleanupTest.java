package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code forgetTexture} / {@code forgetTextureAllocation} must still remove every recorded mip level, capture,
 * parameter, and patch backlog entry for the texture when probing only up to {@code maxRecordedTexLevel}.
 */
class ForgetTextureCleanupTest {
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;

    @Test
    void deletesAllRecordedMipLevelsCapturesAndParameters() throws Exception {
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

        ByteBuffer pixels = MemoryUtil.memAlloc(4 * 4 * 4);
        try {
            invoke(dispatch, stub, ids, "glBindTexture", GL_TEXTURE_2D, 25);
            CeltiumGlStub.recordTextureAllocation(25, 4, 16, 16);
            invoke(dispatch, stub, ids, "glTexParameteri", GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            invoke(dispatch, stub, ids, "glTexImage2D", GL_TEXTURE_2D, 5, GL_RGBA, 1, 1, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, MemoryUtil.memAddress(pixels));

            assertTrue(CeltiumGlStub.textureParameterRecorded(25, GL_TEXTURE_WRAP_S));
            assertEquals(16, CeltiumGlStub.celtium$textureExtent(25)[0]);

            // Keep a second texture intact while forgetting texture 25.
            invoke(dispatch, stub, ids, "glBindTexture", GL_TEXTURE_2D, 26);
            CeltiumGlStub.recordTextureAllocation(26, 2, 8, 8);

            CeltiumGlStub.forgetTextureAllocation(25);

            assertFalse(CeltiumGlStub.textureParameterRecorded(25, GL_TEXTURE_WRAP_S),
                    "recorded texture parameter must be cleared on delete");
            assertNull(CeltiumGlStub.celtium$textureExtent(25),
                    "level 0 extent must be cleared on delete");

            Field texLevelsField = CeltiumGlStub.class.getDeclaredField("texLevels");
            texLevelsField.setAccessible(true);
            Map<?, ?> texLevels = (Map<?, ?>) texLevelsField.get(stub);
            // Only texture 26's 3 mip levels (0, 1, 2) should remain.
            assertEquals(3, texLevels.size(), "all mip levels 0..5 of texture 25 must be cleared");

            Field texCapturesField = CeltiumGlStub.class.getDeclaredField("texCaptures");
            texCapturesField.setAccessible(true);
            Map<?, ?> texCaptures = (Map<?, ?>) texCapturesField.get(stub);
            assertEquals(3, texCaptures.size(), "all mip captures 0..5 of texture 25 must be cleared");
        } finally {
            installedField.set(null, previous);
            MemoryUtil.memFree(pixels);
        }
    }

    private static void invoke(Method dispatch, CeltiumGlStub stub, Map<?, ?> ids, String name, long... args)
            throws Exception {
        long[] c = new long[16];
        System.arraycopy(args, 0, c, 0, args.length);
        dispatch.invoke(stub, ids.get(name), c);
    }
}
