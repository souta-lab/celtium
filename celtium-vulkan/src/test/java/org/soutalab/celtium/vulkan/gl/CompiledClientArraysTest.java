package org.soutalab.celtium.vulkan.gl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the adapter's real compile dispatcher; no GL context and no claim about rendered pixels. */
class CompiledClientArraysTest {
    @Test void legacyTrianglesOwnTheirPackedVerticesInsteadOfTheReusedCallerBuffer() throws Exception {
        var ctor = CeltiumGlStub.class.getDeclaredConstructor(); ctor.setAccessible(true);
        CeltiumGlStub stub = ctor.newInstance();
        Method dispatch = CeltiumGlStub.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        dispatch.setAccessible(true);
        Field idsField = CeltiumGlStub.class.getDeclaredField("IDS"); idsField.setAccessible(true);
        Map<?, ?> ids = (Map<?, ?>) idsField.get(null);
        Field installed = CeltiumGlStub.class.getDeclaredField("installed"); installed.setAccessible(true);
        Object previous = installed.get(null); installed.set(null, stub);
        ByteBuffer source = MemoryUtil.memAlloc(3 * 32);
        try {
            for (int i = 0; i < 3; i++) {
                source.putFloat(i * 32, i + 1); source.putFloat(i * 32 + 12, i + 0.25f);
                source.putInt(i * 32 + 20, 0x11223344); // unused colour must not become the normal
                source.putInt(i * 32 + 24, 0x007f0000);
            }
            invoke(dispatch, stub, ids, "glBindBuffer", 0x8892, 0);
            CeltiumGlStub.compatibilityNewList(400, 0x1300);
            invoke(dispatch, stub, ids, "glEnableClientState", 0x8074);
            invoke(dispatch, stub, ids, "glEnableClientState", 0x8078);
            invoke(dispatch, stub, ids, "glEnableClientState", 0x8075);
            invoke(dispatch, stub, ids, "glVertexPointer", 3, 0x1406, 32, MemoryUtil.memAddress(source));
            CeltiumGlStub.celtium$drawArrays(4, 0, 3);
            CeltiumGlStub.compatibilityEndList();
            Field listsField = CeltiumGlStub.class.getDeclaredField("displayLists"); listsField.setAccessible(true);
            List<?> list = (List<?>) ((Map<?, ?>) listsField.get(stub)).get(400);
            assertEquals(1, list.size(), "client state executes during compile; only the captured draw is stored");
            long[] command = (long[]) ((Object[]) list.get(0))[1];
            assertEquals(24, command[1]); assertEquals(9, command[2]);
            assertEquals(3, command[3]); assertEquals(4, command[4]);
            long captured = NativeGeometryBuffer.find((int) command[0]).address();
            MemoryUtil.memSet(MemoryUtil.memAddress(source), 0, source.capacity());
            for (int i = 0; i < 3; i++) {
                assertEquals(i + 1, MemoryUtil.memGetFloat(captured + i * 24L));
                assertEquals(i + 0.25f, MemoryUtil.memGetFloat(captured + i * 24L + 12));
                assertEquals(0x007f0000, MemoryUtil.memGetInt(captured + i * 24L + 20));
            }
            // The lit twin: the same vertices in ITEM order (pos, colour, UV, normal), opaque white without a colour
            // array, so the lit pipeline can shade them by their normals.
            long litId = command[5];
            long lit = NativeGeometryBuffer.find((int) litId).address();
            for (int i = 0; i < 3; i++) {
                assertEquals(i + 1, MemoryUtil.memGetFloat(lit + i * 28L));
                assertEquals(-1, MemoryUtil.memGetInt(lit + i * 28L + 12));
                assertEquals(i + 0.25f, MemoryUtil.memGetFloat(lit + i * 28L + 16));
                assertEquals(0x007f0000, MemoryUtil.memGetInt(lit + i * 28L + 24));
            }
            invoke(dispatch, stub, ids, "glDeleteLists", 400, 1);
            assertNull(NativeGeometryBuffer.find((int) command[0]), "deletion releases the retained resource");
            assertNull(NativeGeometryBuffer.find((int) litId), "deletion releases the lit twin as well");
        } finally {
            invoke(dispatch, stub, ids, "glDeleteLists", 400, 1);
            MemoryUtil.memFree(source);
            installed.set(null, previous);
        }
    }

    @Test void robotTriangleFanWithNonzeroFirstIsCompiledWithCorrectVertices() throws Exception {
        var ctor = CeltiumGlStub.class.getDeclaredConstructor(); ctor.setAccessible(true);
        CeltiumGlStub stub = ctor.newInstance();
        Method dispatch = CeltiumGlStub.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        dispatch.setAccessible(true);
        Field idsField = CeltiumGlStub.class.getDeclaredField("IDS"); idsField.setAccessible(true);
        Map<?, ?> ids = (Map<?, ?>) idsField.get(null);
        Field installed = CeltiumGlStub.class.getDeclaredField("installed"); installed.setAccessible(true);
        Object previous = installed.get(null); installed.set(null, stub);
        ByteBuffer source = MemoryUtil.memAlloc(5 * 32);
        try {
            for (int i = 0; i < 5; i++) {
                source.putFloat(i * 32, 100 + i);
                source.putFloat(i * 32 + 12, i + 0.5f);
                source.putInt(i * 32 + 24, 0x007f0000);
            }
            invoke(dispatch, stub, ids, "glBindBuffer", 0x8892, 0);
            CeltiumGlStub.compatibilityNewList(401, 0x1300);
            invoke(dispatch, stub, ids, "glEnableClientState", 0x8074);
            invoke(dispatch, stub, ids, "glEnableClientState", 0x8078);
            invoke(dispatch, stub, ids, "glEnableClientState", 0x8075);
            invoke(dispatch, stub, ids, "glVertexPointer", 3, 0x1406, 32, MemoryUtil.memAddress(source));
            CeltiumGlStub.celtium$drawArrays(6, 1, 4);
            CeltiumGlStub.compatibilityEndList();
            Field listsField = CeltiumGlStub.class.getDeclaredField("displayLists");
            listsField.setAccessible(true);
            List<?> list = (List<?>) ((Map<?, ?>) listsField.get(stub)).get(401);
            assertEquals(1, list.size(), "GL_TRIANGLE_FAN must reach retained geometry");
            long[] command = (long[]) ((Object[]) list.get(0))[1];
            assertEquals(4, command[3]); assertEquals(6, command[4]);
            long captured = NativeGeometryBuffer.find((int) command[0]).address();
            for (int i = 0; i < 4; i++) {
                assertEquals(101f + i, MemoryUtil.memGetFloat(captured + 24L * i));
            }
        } finally {
            invoke(dispatch, stub, ids, "glDeleteLists", 401, 1);
            MemoryUtil.memFree(source);
            installed.set(null, previous);
        }
    }

    private static void invoke(Method dispatch, CeltiumGlStub stub, Map<?, ?> ids, String name, long... args) throws Exception {
        long[] c = new long[16]; System.arraycopy(args, 0, c, 0, args.length);
        dispatch.invoke(stub, ids.get(name), c);
    }
}
