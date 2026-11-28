package org.soutalab.celtium.glcompat.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.core.state.LightStateRegister;
import org.soutalab.celtium.glcompat.DisplayListManager;
import org.soutalab.celtium.glcompat.DirectGlDispatcher;
import org.soutalab.celtium.glcompat.GLContextState;
import org.soutalab.celtium.glcompat.GLSMDraws;
import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.glcompat.capture.ClientArrayRange;
import org.soutalab.celtium.glcompat.capture.DirectGlCaptureEntry;
import org.soutalab.celtium.glcompat.capture.GlFn;
import org.soutalab.celtium.glcompat.ffp.ShaderManager;
import org.soutalab.celtium.vulkan.lane2.Lane2StateMirror;

/**
 * E4-b2-tail T1/T2 (plan section 8.3 items 25/26): the wiring the tail slice landed, verified
 * against the production seats - not test-local substitutes.
 * <p>
 * T1: connecting a sink to the production {@link DirectGlExecutor#EXECUTOR} fires the C1
 * wiring (the FFP {@link ShaderManager} enable and the {@code directDrawer} install), and
 * the compiling-list id is published to the shared register while a list is open and
 * restored to 0 at glEndList, under both GL_COMPILE and GL_COMPILE_AND_EXECUTE (C4).
 * <p>
 * Scope note (measured on agera, 2026-11-28): geometry-bearing immediate-mode work - the
 * live glBegin/glEnd draw, the live half of GL_COMPILE_AND_EXECUTE, and recording or
 * replaying a list that carries immediate geometry - bottoms out in the tessellator's
 * vertex-format machinery, which issues raw LWJGL GL11 calls. In the game those calls are
 * intercepted by the transformer's trampoline; in this test JVM there is no transformer,
 * and touching that machinery aborts the JVM (SIGABRT, exit 134, reproduced standalone
 * three times). Those forms are therefore verified where they can be - the glprobe
 * acceptance leg, whose families #0/#3/#4/#6 are the live immediate draws and #1/#2 the
 * compiled-list draws - and this class pins the register contract (C4) on geometry-free
 * lists, whose open/close/call path is pure state machinery.
 * <p>
 * T2: the successor observable for the two draw tests E4-c retired from VaoAttribRecordTest -
 * draws assembled from a VBO-offset pointer and from a raw client-memory pointer resolved
 * through the published owned range (C2(b)) reach the sink with droppedDraws == 0.
 */
final class Lane3WiringDrawTest {

    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_COMPILE = 0x1300;
    private static final int GL_COMPILE_AND_EXECUTE = 0x1301;
    private static final int GL_VERTEX_ARRAY = 0x8074;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_UNSIGNED_SHORT = 0x1403;
    private static final int GL_STATIC_DRAW = 0x88E4;
    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static final int GL_FRAGMENT_SHADER = 0x8B30;
    private static final int GL_LINK_STATUS = 0x8B82;

    private DirectGlExecutorTest.FakeSink sink;
    private LightStateRegister register;
    private long droppedBefore;

    @BeforeEach
    void connectProductionSink() {
        this.sink = new DirectGlExecutorTest.FakeSink();
        // The production singleton: this connection is what fires the C1 wiring.
        DirectGlExecutor.EXECUTOR.setSink(this.sink);
        this.register = Lane2StateMirror.current().register();
        DirectGlDispatcher.attachRegister(this.register);
        // The drawable-resources initialisation production runs when a context binds
        // (GLStateManager.initializeDrawableResources): without it the VAO manager has no
        // current attribute array and every client-state call dereferences null.
        GLContextState ctx = GLStateManager.ctx();
        if (!ctx.vaos.isInitialized()) {
            ctx.defaultVAO = DirectGlExecutor.EXECUTOR.genVertexArrays();
            ctx.boundVAO = ctx.defaultVAO;
            ctx.vaos.init(ctx.defaultVAO);
        }
        this.droppedBefore = DirectGlExecutor.EXECUTOR.droppedDraws();
    }

    @AfterEach
    void disconnect() throws Exception {
        DisplayListManager.abortIfLeaked();
        DirectGlDispatcher.attachRegister(null);
        uninstallEntry();
        ClientArrayRange.consumePublished();
        DirectGlExecutor.EXECUTOR.setSink(null);
    }

    /** The draw tests need the shader compiler, the same availability gate the executor tests use. */
    private void assumeShaderCompiler() {
        DirectGlExecutor probe = new DirectGlExecutor();
        probe.setSink(new DirectGlExecutorTest.FakeSink());
        int program = probe.createProgram();
        int vs = probe.createShader(GL_VERTEX_SHADER);
        probe.shaderSource(vs, """
                #version 120
                attribute vec2 position;
                void main() { gl_Position = vec4(position, 0.0, 1.0); }
                """);
        probe.attachShader(program, vs);
        int fs = probe.createShader(GL_FRAGMENT_SHADER);
        probe.shaderSource(fs, """
                #version 120
                uniform sampler2D tex;
                void main() { gl_FragColor = texture2D(tex, vec2(0.5)); }
                """);
        probe.attachShader(program, fs);
        probe.linkProgram(program);
        Assumptions.assumeTrue(probe.getProgrami(program, GL_LINK_STATUS) == 1,
                "shader compiler unavailable or translator refused in this JVM");
    }

    private void assertNoNewDrops() {
        assertEquals(this.droppedBefore, DirectGlExecutor.EXECUTOR.droppedDraws(),
                "no draw on the wired path may be dropped");
    }

    // ------------------------------------------------------------------ T1

    @Test
    void sinkConnectionEnablesFfpAndInstallsTheDirectDrawer() {
        assertTrue(ShaderManager.isEnabled(),
                "C1(a): connecting the production sink enables the FFP shader manager");
        assertNotNull(GLStateManager.getInitConfig(),
                "C1(b): connecting the production sink installs an init config");
        assertNotNull(GLStateManager.getInitConfig().getDirectDrawer(),
                "C1(b): the installed config carries the tessellator direct drawer");
    }

    @Test
    void compiledListPublishesAndRestoresTheCompilingId() {
        // Geometry-free on purpose (see the class scope note): the register contract and
        // the manager's open/close/call machinery are pure state and are pinned here; a
        // geometry-bearing list is verified by the glprobe acceptance leg.
        int list = GLSMDraws.glGenLists(1);
        try {
            assertEquals(0, this.register.compilingListId());
            DisplayListManager.glNewList(list, GL_COMPILE);
            assertEquals(list, this.register.compilingListId(),
                    "C4: opening the list publishes its id to the shared register");
            DisplayListManager.glEndList();
            assertEquals(0, this.register.compilingListId(),
                    "C4: closing the list restores the register to 0");
            int drawsBefore = this.sink.draws.size();
            DisplayListManager.glCallList(list);
            GLSMDraws.glListBase(list);
            GLSMDraws.glCallLists(IntBuffer.wrap(new int[] {0}));
            assertEquals(drawsBefore, this.sink.draws.size(),
                    "calling an empty list is a no-op at the sink");
            assertNoNewDrops();
        } finally {
            GLSMDraws.glDeleteLists(list, 1);
        }
    }

    @Test
    void compileAndExecutePublishesAndRestoresTheCompilingId() {
        // The live half of compile-and-execute invokes the direct drawer, which this JVM
        // cannot run (see the class scope note); the register contract is pinned here and
        // the live draw is verified by the glprobe acceptance leg.
        int list = GLSMDraws.glGenLists(1);
        try {
            DisplayListManager.glNewList(list, GL_COMPILE_AND_EXECUTE);
            assertEquals(list, this.register.compilingListId(),
                    "C4: the compile-and-execute open publishes its id too");
            DisplayListManager.glEndList();
            assertEquals(0, this.register.compilingListId(),
                    "C4: the compile-and-execute close restores the register to 0");
        } finally {
            GLSMDraws.glDeleteLists(list, 1);
        }
    }

    // ------------------------------------------------------------------ T2

    @Test
    void vboOffsetPointerDrawReachesTheSink() throws Exception {
        assumeShaderCompiler();
        installEntry();
        int vbo = DirectGlExecutor.EXECUTOR.genBuffers();
        DirectGlExecutor.EXECUTOR.bindBuffer(GL_ARRAY_BUFFER, vbo);
        ByteBuffer vertices = ByteBuffer.allocateDirect(3 * 3 * 4).order(ByteOrder.nativeOrder());
        vertices.asFloatBuffer().put(new float[] {-1f, -1f, 0f, 1f, -1f, 0f, 0f, 1f, 0f});
        DirectGlExecutor.EXECUTOR.bufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        DirectGlExecutor.EXECUTOR.bindBuffer(GL_ARRAY_BUFFER, 0);
        try {
            call("glBindBuffer", GL_ARRAY_BUFFER, vbo);
            GLStateManager.glEnableClientState(GL_VERTEX_ARRAY);
            // Offset form: with a VBO bound, C2(b) stays out and the VAO records the offset.
            call("glVertexPointer", 3, GL_FLOAT, 0, 0L);
            int drawsBefore = this.sink.draws.size();
            GLSMDraws.glDrawArrays(GL_TRIANGLES, 0, 3);
            assertEquals(drawsBefore + 1, this.sink.draws.size(),
                    "the VBO-offset draw reaches the sink");
            assertNoNewDrops();
        } finally {
            GLStateManager.glDisableClientState(GL_VERTEX_ARRAY);
            call("glBindBuffer", GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void publishedClientArrayDrawReachesTheSink() throws Exception {
        assumeShaderCompiler();
        installEntry();
        ByteBuffer vertices = ByteBuffer.allocateDirect(3 * 3 * 4).order(ByteOrder.nativeOrder());
        vertices.asFloatBuffer().put(new float[] {-1f, -1f, 0f, 1f, -1f, 0f, 0f, 1f, 0f});
        long address = MemoryUtil.memAddress(vertices);
        // The game's publication for this draw (the mixin's seat in production): without it,
        // or without C2(b), the raw address below never becomes a readable client pointer.
        ClientArrayRange.publish(address, 3 * 3 * 4);
        try {
            GLStateManager.glEnableClientState(GL_VERTEX_ARRAY);
            call("glVertexPointer", 3, GL_FLOAT, 0, address);
            int drawsBefore = this.sink.draws.size();
            GLSMDraws.glDrawArrays(GL_TRIANGLES, 0, 3);
            assertEquals(drawsBefore + 1, this.sink.draws.size(),
                    "the client-array draw reaches the sink through the published range");
            ByteBuffer indices = ByteBuffer.allocateDirect(3 * 2).order(ByteOrder.nativeOrder());
            indices.asShortBuffer().put(new short[] {0, 1, 2});
            GLSMDraws.glDrawElements(GL_TRIANGLES, 3, GL_UNSIGNED_SHORT, indices);
            assertEquals(drawsBefore + 2, this.sink.draws.size(),
                    "the indexed form over the same client array reaches the sink too");
            assertNoNewDrops();
        } finally {
            GLStateManager.glDisableClientState(GL_VERTEX_ARRAY);
        }
    }

    // ------------------------------------------------------- entry driving

    private static void installEntry() throws Exception {
        var ctor = DirectGlCaptureEntry.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        DirectGlCaptureEntry stub = ctor.newInstance();
        Field installed = DirectGlCaptureEntry.class.getDeclaredField("installed");
        installed.setAccessible(true);
        installed.set(null, stub);
    }

    private static void uninstallEntry() throws Exception {
        Field installed = DirectGlCaptureEntry.class.getDeclaredField("installed");
        installed.setAccessible(true);
        installed.set(null, null);
    }

    private static void call(String name, long... callArgs) throws Exception {
        var dispatch = DirectGlCaptureEntry.class.getDeclaredMethod("dispatchArgs", GlFn.class, long[].class);
        dispatch.setAccessible(true);
        long[] args = new long[16];
        System.arraycopy(callArgs, 0, args, 0, callArgs.length);
        dispatch.invoke(DirectGlCaptureEntry.installed(),
                DirectGlCaptureEntry.row(DirectGlCaptureEntry.ids().get(name)), args);
    }
}
