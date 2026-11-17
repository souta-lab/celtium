package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generation safety for recyclable GL names (item 328, V-04): a deleted object's celtium-side state belongs to
 * the object, not to its name, so the name's next owner starts empty.
 * <p>
 * GL drivers recycle names immediately - the name a mod gets from {@code glGenTextures} or {@code glCreateShader}
 * is routinely one it deleted a frame earlier. Before this item, the program registry forgot a program only on
 * the forge122 transformer route and never forgot a shader's source at all, so a recycled shader name attached
 * to a new program compiled its predecessor's source; and the stub's delete left the "allocated without data"
 * classification standing for a texture name's next owner. These tests delete and recreate through both the
 * registry and the stub's own dispatch, and assert the new incarnation inherits nothing.
 */
class RecycledHandleStateTest {
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_UNSIGNED_BYTE = 0x1401;

    private static final String VERTEX =
            "attribute vec2 position; varying vec2 uv;\n"
                    + "void main() { gl_Position = vec4(position, 0.0, 1.0); uv = position; }";
    private static final String FRAGMENT =
            "uniform sampler2D tex; varying vec2 uv;\n"
                    + "void main() { gl_FragColor = texture2D(tex, uv); }";

    @BeforeEach
    void reset() {
        ModProgramRegistry.clear();
    }

    @Test
    void aDeletedShadersSourceIsNotCompiledByItsNamesNextOwner() {
        ModProgramRegistry.shaderSource(7, FRAGMENT);
        ModProgramRegistry.deleteShader(7);
        // The driver hands name 7 to a new shader object, which is attached before any source arrives.
        ModProgramRegistry.attach(9, 7);
        ModProgramRegistry.attach(9, 6);
        ModProgramRegistry.shaderSource(6, VERTEX);
        ModProgramRegistry.Program program = ModProgramRegistry.link(9);
        assertFalse(program.lowered().ok(),
                "the recycled name must start with no source, not its predecessor's");
        assertTrue(program.lowered().failure().contains("no fragment stage"),
                program.lowered().failure());
    }

    @Test
    void aShaderDeletedWhileAttachedKeepsItsProgramButNotItsNextOwner() {
        ModProgramRegistry.shaderSource(6, VERTEX);
        ModProgramRegistry.shaderSource(7, FRAGMENT);
        ModProgramRegistry.attach(1, 6);
        ModProgramRegistry.attach(1, 7);
        assertTrue(ModProgramRegistry.link(1).lowered().ok());
        // GL defers this deletion while program 1 references the shader: program 1 is untouched.
        ModProgramRegistry.deleteShader(7);
        assertTrue(ModProgramRegistry.program(1).lowered().ok());
        // But the name is spent: attached afresh, it is a new shader object with no source.
        ModProgramRegistry.attach(2, 6);
        ModProgramRegistry.attach(2, 7);
        assertFalse(ModProgramRegistry.link(2).lowered().ok(),
                "a fresh attach of a deleted name must not compile the retained source");
    }

    @Test
    void deletingTheLastReferencingProgramReleasesADeferredSource() {
        ModProgramRegistry.shaderSource(6, VERTEX);
        ModProgramRegistry.shaderSource(7, FRAGMENT);
        ModProgramRegistry.attach(1, 6);
        ModProgramRegistry.attach(1, 7);
        ModProgramRegistry.deleteShader(7);
        ModProgramRegistry.deleteProgram(1);
        assertNull(ModProgramRegistry.program(1));
        ModProgramRegistry.attach(2, 6);
        ModProgramRegistry.attach(2, 7);
        assertFalse(ModProgramRegistry.link(2).lowered().ok(),
                "once no live program references the shader, its deferred source is released");
    }

    @Test
    void deletingAProgramKeepsItsLiveShadersSources() {
        // The documented asymmetry: a shader that was never deleted survives its program and can be reused.
        ModProgramRegistry.shaderSource(6, VERTEX);
        ModProgramRegistry.shaderSource(8, FRAGMENT);
        ModProgramRegistry.attach(3, 6);
        ModProgramRegistry.attach(3, 8);
        ModProgramRegistry.deleteProgram(3);
        ModProgramRegistry.attach(4, 6);
        ModProgramRegistry.attach(4, 8);
        assertTrue(ModProgramRegistry.link(4).lowered().ok(),
                "an undeleted shader's source is the shader's own, across programs");
    }

    @Test
    void aFreshSourceRearmsADeletedName() {
        ModProgramRegistry.shaderSource(6, VERTEX);
        ModProgramRegistry.shaderSource(7, FRAGMENT);
        ModProgramRegistry.attach(1, 6);
        ModProgramRegistry.attach(1, 7);
        ModProgramRegistry.deleteShader(7);
        // The recycled name gets its own source before its new program links: that source is what compiles.
        ModProgramRegistry.shaderSource(7, FRAGMENT);
        ModProgramRegistry.attach(2, 6);
        ModProgramRegistry.attach(2, 7);
        assertTrue(ModProgramRegistry.link(2).lowered().ok());
    }

    @Test
    void stubDeleteDispatchReachesTheProgramRegistry() throws Exception {
        StubHandle handle = StubHandle.install();
        try {
            ModProgramRegistry.shaderSource(6, VERTEX);
            ModProgramRegistry.shaderSource(7, FRAGMENT);
            ModProgramRegistry.attach(1, 6);
            ModProgramRegistry.attach(1, 7);
            assertTrue(ModProgramRegistry.link(1).lowered().ok());

            handle.invoke("glDeleteProgram", 1);
            assertNull(ModProgramRegistry.program(1),
                    "glDeleteProgram through the stub dispatch must forget the program");
            handle.invoke("glDeleteShader", 7);
            ModProgramRegistry.attach(2, 6);
            ModProgramRegistry.attach(2, 7);
            assertFalse(ModProgramRegistry.link(2).lowered().ok(),
                    "glDeleteShader through the stub dispatch must forget the source");
        } finally {
            handle.restore();
        }
    }

    @Test
    void deletingATextureForgetsItsNullAllocationClassification() throws Exception {
        StubHandle handle = StubHandle.install();
        try {
            handle.invoke("glBindTexture", GL_TEXTURE_2D, 25);
            // A NULL data pointer: storage reserved, no pixels - the render-target idiom.
            handle.invoke("glTexImage2D", GL_TEXTURE_2D, 0, GL_RGBA, 4, 4, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
            assertTrue(CeltiumGlStub.textureAllocatedWithoutData(25),
                    "the null allocation is classified while the texture lives");
            CeltiumGlStub.forgetTextureAllocation(25);
            assertFalse(CeltiumGlStub.textureAllocatedWithoutData(25),
                    "the classification belongs to the deleted object, not to its name's next owner");
        } finally {
            handle.restore();
        }
    }

    /** The stub, installed reflectively the way {@code ForgetTextureCleanupTest} drives it. */
    private static final class StubHandle {
        private final CeltiumGlStub stub;
        private final Method dispatch;
        private final Map<?, ?> ids;
        private final Field installedField;
        private final Object previous;

        private StubHandle(CeltiumGlStub stub, Method dispatch, Map<?, ?> ids, Field installedField,
                Object previous) {
            this.stub = stub;
            this.dispatch = dispatch;
            this.ids = ids;
            this.installedField = installedField;
            this.previous = previous;
        }

        static StubHandle install() throws Exception {
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
            return new StubHandle(stub, dispatch, ids, installedField, previous);
        }

        void invoke(String name, long... args) throws Exception {
            long[] c = new long[16];
            System.arraycopy(args, 0, c, 0, args.length);
            this.dispatch.invoke(this.stub, this.ids.get(name), c);
        }

        void restore() throws Exception {
            this.installedField.set(null, this.previous);
        }
    }
}
