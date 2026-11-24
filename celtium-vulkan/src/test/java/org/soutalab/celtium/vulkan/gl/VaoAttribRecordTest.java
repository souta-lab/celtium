package org.soutalab.celtium.vulkan.gl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The VAO-carried full-screen quad (Demonica's {@code FullScreenQuadRenderer}): three generic attributes set once
 * against a VBO inside a VAO, the array buffer unbound again, and the draw issued frames later with only the VAO
 * rebound. Before the per-attribute records this decoded from the last pointer set (attribute 2's) and from
 * enables another draw had left behind, and was refused on every frame. No GL context and no claim about rendered
 * pixels: the assertions are the recorded state and the decode's own report line.
 */
class VaoAttribRecordTest {
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_STATIC_DRAW = 0x88E4;
    private static final int GL_TRIANGLE_STRIP = 5;
    private static final int ENABLED = 0x8622;
    private static final int SIZE = 0x8623;
    private static final int STRIDE = 0x8624;
    private static final int BUFFER_BINDING = 0x889F;
    private static final int POINTER = 0x8645;

    private static GlsmFrontend install() throws Exception {
        var ctor = GlsmFrontend.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        GlsmFrontend stub = ctor.newInstance();
        Field installed = GlsmFrontend.class.getDeclaredField("installed");
        installed.setAccessible(true);
        installed.set(null, stub);
        // The routes rewiring moved the legacy vertex-pointer tuple and the capability record
        // into singleton owners (GlsmClientArrayState / GlsmEnableState), where they outlive a
        // stub instance. A fresh stub used to start from GL's initial values; restore that here
        // so one test's setup cannot describe the next test's draw.
        GlsmClientArrayState.Pointer vertexPointer = GlsmClientArrayState.current().clientPointer(0x8074, true);
        vertexPointer.size = 4;
        vertexPointer.type = GL_FLOAT;
        vertexPointer.stride = 0;
        vertexPointer.offset = 0L;
        vertexPointer.buffer = 0;
        vertexPointer.enabled = false;
        vertexPointer.normalized = false;
        vertexPointer.integer = false;
        GlsmEnableState.current().set(0x8074, false); // GL_VERTEX_ARRAY
        GlsmEnableState.current().set(0x8075, false); // GL_NORMAL_ARRAY
        GlsmEnableState.current().set(0x8076, false); // GL_COLOR_ARRAY
        GlsmEnableState.current().set(0x8078, false); // GL_TEXTURE_COORD_ARRAY
        GlsmEnableState.current().set(ENABLED, false); // the attribute-0 enable mirror
        // The per-attribute records are VAO state in the same singleton owner (M-5), so they outlive a stub
        // instance too - and the resets above do not touch them. The quad setup leaves VAO 77 bound with
        // attribute 0 enabled; a draw that is supposed to be unrecorded then decodes under the quad's enable
        // and refuses on the legacy pointer's size instead of by name (the main-promotion full gate's one
        // failure, 2026-11-25). Bind the default VAO and restore its records to GL's initial values, so the
        // unrecorded-draw premise is actually established.
        GlsmFrontend.celtium$bindVertexArray(0);
        for (GlsmClientArrayState.VaoAttrib attrib : GlsmClientArrayState.current().attribsFor(0)) {
            attrib.enabled = false;
            attrib.pointerSet = false;
            attrib.normalized = false;
            attrib.integer = false;
            attrib.size = 4;
            attrib.type = GL_FLOAT;
            attrib.stride = 0;
            attrib.offset = 0L;
            attrib.buffer = 0;
        }
        return stub;
    }

    private static void uninstall() throws Exception {
        Field installed = GlsmFrontend.class.getDeclaredField("installed");
        installed.setAccessible(true);
        installed.set(null, null);
        GlsmFrontend.celtium$bindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private static void setCurrentProgram(GlsmFrontend stub, int program) {
        // The current program is owned by GlsmShaderLedger (GAP-PROG-1); SEM_USE_PROGRAM writes it there.
        GlsmShaderLedger.current().useProgram(program);
    }

    private static int attribIv(int index, int pname) {
        ByteBuffer out = MemoryUtil.memAlloc(4);
        try {
            GlsmFrontend.celtium$getVertexAttribiv(index, pname, MemoryUtil.memAddress(out));
            return out.getInt(0);
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    private static long attribPointer(int index) {
        ByteBuffer out = MemoryUtil.memAlloc(8);
        try {
            GlsmFrontend.celtium$getVertexAttribPointer(index, POINTER, MemoryUtil.memAddress(out));
            return out.getLong(0);
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    /** The quad's own setup sequence, through the same statics the transformer's hint calls. */
    private static void setUpQuad(ByteBuffer vertices) {
        GlsmFrontend.celtium$bindVertexArray(77);
        GlsmFrontend.celtium$bindBuffer(GL_ARRAY_BUFFER, 78);
        GlsmFrontend.celtium$bufferData(GL_ARRAY_BUFFER, 80L, MemoryUtil.memAddress(vertices), GL_STATIC_DRAW);
        GlsmFrontend.celtium$vertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0L);
        GlsmFrontend.celtium$vertexAttribEnabled(0, true);
        GlsmFrontend.celtium$vertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12L);
        GlsmFrontend.celtium$vertexAttribEnabled(1, true);
        GlsmFrontend.celtium$vertexAttribPointer(2, 2, GL_FLOAT, false, 20, 12L);
        GlsmFrontend.celtium$vertexAttribEnabled(2, true);
        GlsmFrontend.celtium$bindBuffer(GL_ARRAY_BUFFER, 0);
        GlsmFrontend.celtium$bindVertexArray(0);
    }

    private static ByteBuffer quadVertices() {
        ByteBuffer vertices = MemoryUtil.memAlloc(80);
        float[] values = {1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < values.length; i++) {
            vertices.putFloat(i * 4, values[i]);
        }
        return vertices;
    }

    private static String drawLine(int program, Runnable draw) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            draw.run();
        } finally {
            System.setOut(original);
        }
        String text = captured.toString(StandardCharsets.UTF_8);
        for (String line : text.split("\n")) {
            if (line.contains("CELTIUM_PROGRAM_DRAW program=" + program + " ")) {
                return line;
            }
        }
        return "";
    }

    @Test
    void quadSetupIsRecordedPerAttributeAndSurvivesRebind() throws Exception {
        GlsmFrontend stub = install();
        ByteBuffer vertices = quadVertices();
        try {
            setUpQuad(vertices);
            // The default VAO was never given these attributes: its records stay at the defaults.
            assertEquals(0, attribIv(1, ENABLED), "attribute 1 of VAO 0 must not inherit the quad's enable");
            GlsmFrontend.celtium$bindVertexArray(77);
            assertEquals(1, attribIv(0, ENABLED));
            assertEquals(1, attribIv(1, ENABLED));
            assertEquals(1, attribIv(2, ENABLED));
            assertEquals(3, attribIv(0, SIZE));
            assertEquals(20, attribIv(0, STRIDE));
            assertEquals(20, attribIv(2, STRIDE));
            assertEquals(78, attribIv(0, BUFFER_BINDING), "the pointer's buffer is VAO state, not the live binding");
            assertEquals(12L, attribPointer(1));
            assertEquals(12L, attribPointer(2));
            // A legacy pointer set on VAO 0 must not leak into the quad's VAO, nor the quad's into VAO 0.
            GlsmFrontend.celtium$bindVertexArray(0);
            GlsmFrontend.celtium$vertexPointer(3, GL_FLOAT, 12, 0L);
            GlsmFrontend.celtium$bindVertexArray(77);
            assertEquals(20, attribIv(0, STRIDE), "VAO 77 keeps its own stride across the swap");
            GlsmFrontend.celtium$bindVertexArray(0);
            assertEquals(12, attribIv(0, STRIDE), "VAO 0 keeps the legacy pointer set on it");
        } finally {
            MemoryUtil.memFree(vertices);
            uninstall();
        }
    }

    @Test
    void quadDrawDecodesToPositionTexFromTheRecordedBuffer() throws Exception {
        GlsmFrontend stub = install();
        ByteBuffer vertices = quadVertices();
        try {
            setUpQuad(vertices);
            GlsmFrontend.celtium$bindVertexArray(77);
            setCurrentProgram(stub, 999);
            String line = drawLine(999, () -> GlsmFrontend.celtium$drawArrays(GL_TRIANGLE_STRIP, 0, 4));
            assertTrue(line.contains("format=6"), "the stride-20 quad must decode as POSITION_TEX: " + line);
            assertTrue(line.contains("reason=null"),
                    "the decode must accept the draw (a backend refusal later is a different line): " + line);
        } finally {
            MemoryUtil.memFree(vertices);
            uninstall();
        }
    }

    @Test
    void drawWithoutAnyEnabledAttributeKeepsTheNamedRefusal() throws Exception {
        GlsmFrontend stub = install();
        try {
            setCurrentProgram(stub, 998);
            String line = drawLine(998, () -> GlsmFrontend.celtium$drawArrays(GL_TRIANGLE_STRIP, 0, 4));
            assertTrue(line.contains("attribute 0 is not enabled"),
                    "an unrecorded draw must still refuse by name: " + line);
        } finally {
            uninstall();
        }
    }
}
