package org.taumc.celeritas.core;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The GL-less transformers' fail-open discipline, pinned after the forge/mixin audit found two
 * of them unguarded. Every transformer in this family answers a failure by returning the
 * original bytes and logging the error, so a broken or unexpected class can never take the
 * game down at define time: the splash and SDL-swap transformers used to let a Throwable from
 * ASM escape {@code transform()}, and the window transformer logged "installed" even when its
 * anchor never matched and nothing was injected. These tests hold the splash/SDL fail-open
 * shape on unreadable input, and the window transformer's injection count on synthetic
 * classes with and without the {@code glfwCreateWindow} anchor.
 */
class CeltiumGllessTransformerGuardsTest {

    private static final String HINT_CALL =
            "org/taumc/celeritas/core/CeltiumGllessHint.beforeWindowCreation";
    private static final String ANCHOR_CALL = "org/lwjgl/glfw/GLFW.glfwCreateWindow";

    @Test
    void splashReturnsTheOriginalBytesForUnreadableInput() {
        byte[] garbage = {0x01, 0x02, 0x03, 0x04};
        byte[] result = new CeltiumGllessSplashTransformer()
                .transform("net.minecraftforge.fml.client.SplashProgress", null, garbage);
        assertSame(garbage, result,
                "an unreadable class must come back untouched, not as an exception");
    }

    @Test
    void sdlSwapReturnsTheOriginalBytesForUnreadableInput() {
        byte[] garbage = {0x01, 0x02, 0x03, 0x04};
        byte[] result = new CeltiumGllessSdlSwapTransformer()
                .transform("com.cleanroommc.client.sdl.Window", null, garbage);
        assertSame(garbage, result,
                "an unreadable class must come back untouched, not as an exception");
    }

    @Test
    void windowInjectsTheHintBeforeTheCreateWindowAnchor() {
        byte[] result = new CeltiumGllessWindowTransformer()
                .transform("org.lwjgl.opengl.Display", null, syntheticDisplay(true));
        List<String> calls = methodCalls(result);
        assertEquals(1, Collections.frequency(calls, HINT_CALL),
                "exactly one hint call is injected for the one anchor call");
        assertTrue(calls.indexOf(HINT_CALL) < calls.indexOf(ANCHOR_CALL),
                "the hint must land immediately before glfwCreateWindow, not after it");
    }

    @Test
    void windowInjectsNothingWithoutTheAnchor() {
        byte[] result = new CeltiumGllessWindowTransformer()
                .transform("org.lwjgl.opengl.Display", null, syntheticDisplay(false));
        assertEquals(0, Collections.frequency(methodCalls(result), HINT_CALL),
                "a class with no glfwCreateWindow call gets no hint call");
    }

    /** A minimal class with one static method, optionally calling the window anchor. */
    private static byte[] syntheticDisplay(boolean withAnchor) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "test/SyntheticDisplay", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "create", "()V", null, null);
        method.visitCode();
        if (withAnchor) {
            for (int i = 0; i < 5; i++) {
                method.visitInsn(Opcodes.ICONST_0);
            }
            method.visitInsn(Opcodes.LCONST_0);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/glfw/GLFW",
                    "glfwCreateWindow", "(IIIIIJ)J", false);
            method.visitInsn(Opcodes.POP2);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(withAnchor ? 7 : 0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** Every method call in the class, as {@code owner.name}, in instruction order. */
    private static List<String> methodCalls(byte[] bytes) {
        List<String> calls = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String calledName,
                                                String calledDescriptor, boolean isInterface) {
                        calls.add(owner + "." + calledName);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }
}
