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
 * The shader/program status routes of the GL-less native transformer, pinned at the point that
 * was broken: the native's first argument is the shader or program the query is about, and the
 * replacement body must forward it to the hint. The route used to load only the pname (slot 1),
 * so the hint could answer nothing but the unconditional success the stub's recorded link status
 * was meant to qualify - every known consumer of LINK_STATUS (Iris's and Embeddium's builders,
 * through LWJGL3) travels this route. These tests hold the emitted hint calls' descriptors:
 * the object-carrying forms {@code (II)I}, {@code (IIJ)V} and {@code (IIJJ)V}.
 */
class CeltiumGllessNativeTransformerShaderQueryTest {

    private static final String HINT = "org/taumc/celeritas/core/CeltiumGllessHint.";

    @Test
    void statusRoutesForwardTheObjectToTheHint() {
        byte[] input = syntheticGl20c();
        byte[] result = new CeltiumGllessNativeTransformer()
                .transform("org.lwjgl.opengl.GL20C", null, input);
        if (!asm9VisitorsSupported()) {
            // Same environment limit as the guards tests: the forge122 test runtime resolves ASM
            // to launchwrapper's asm-debug-all 5.2, whose ClassVisitor rejects the ASM9 api level
            // the transformer builds its visitors with, so it must fail open here. The emitted
            // routes are asserted strictly wherever ASM9 visitors can be built.
            assertSame(input, result,
                    "with no ASM9 support the native transformer must fail open");
            return;
        }
        List<String> calls = methodCalls(result);
        assertEquals(1, Collections.frequency(calls, HINT + "glGetShaderProgrami (II)I"),
                "nglGetShaderi must route to the object-carrying hint form");
        assertEquals(1, Collections.frequency(calls, HINT + "glGetShaderProgramiv (IIJ)V"),
                "nglGetShaderiv must route to the object-carrying hint form");
        assertEquals(1, Collections.frequency(calls, HINT + "glGetShaderInfoLog (IIJJ)V"),
                "nglGetShaderInfoLog must route to the object-carrying hint form");
    }

    /**
     * Whether this runtime's ASM accepts ASM9 visitors. The api level is the literal
     * {@code 9 << 16}, not {@code Opcodes.ASM9}: ASM 5.2's Opcodes has no such field, so
     * naming it would fail the probe with a linkage error instead of the answer.
     */
    private static boolean asm9VisitorsSupported() {
        try {
            new ClassVisitor(9 << 16, null) {
            };
            return true;
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    /** A minimal LWJGL3-shaped class holding the three status-query natives, and nothing else. */
    private static byte[] syntheticGl20c() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "org/lwjgl/opengl/GL20C", null,
                "java/lang/Object", null);
        nativeMethod(writer, "nglGetShaderi", "(II)I");
        nativeMethod(writer, "nglGetShaderiv", "(IIJ)V");
        nativeMethod(writer, "nglGetShaderInfoLog", "(IIJJ)V");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void nativeMethod(ClassWriter writer, String name, String descriptor) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
                name, descriptor, null, null);
        method.visitEnd();
    }

    /** Every method call in the class, as {@code owner.name descriptor}, in instruction order. */
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
                        calls.add(owner + "." + calledName + " " + calledDescriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }
}
