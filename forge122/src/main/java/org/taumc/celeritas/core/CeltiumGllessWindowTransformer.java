package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin;

/**
 * Sets {@code GLFW_CLIENT_API = GLFW_NO_API} immediately before the window is created, from the transformer chain.
 * <p>
 * The mixin that used to do this ({@code core.LwjglxDisplayMixin}) can lose a race it cannot see: in the 268-mod E2E
 * pack RandomPatches' tweak initialisation defines {@code org.lwjgl.opengl.Display} at 8.975 s, while Mixin prepares
 * its configurations at 9.837 s (leg {@code gv-e2e-268-xlog}), so the mixin was never applied, the GL-less stub was
 * never installed and the window got a real GL 4.6 context. This transformer is registered when the coremod loads,
 * long before either, so the hint is placed by position in the bytecode instead of by registration order.
 * <p>
 * The injection point is the {@code INVOKESTATIC org/lwjgl/glfw/GLFW.glfwCreateWindow} call inside
 * {@code Display.create}: the call immediately before it is what creates the window, so the hint lands after
 * {@code glfwInit} (GLFW permits only the init-related calls before it, and {@code glfwInit} resets window hints)
 * and before the window exists. The hint itself is in {@link CeltiumGllessHint} so this class never needs the
 * target's classes; a failure here returns the original bytes and the run continues as a hybrid. The visitor
 * API is ASM5 because that is what the game's own ASM provides - the 5-argument visitMethodInsn needs ASM5, and
 * ASM9 is not on this compile classpath.
 */
public class CeltiumGllessWindowTransformer implements IClassTransformer {
    private static final String TARGET = "org.lwjgl.opengl.Display";
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String CREATE_WINDOW = "glfwCreateWindow";
    private static final String HINT_OWNER = "org/taumc/celeritas/core/CeltiumGllessHint";
    private static final String HINT_METHOD = "beforeWindowCreation";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String target = transformedName != null ? transformedName : name;
        if (!TARGET.equals(target)) {
            return basicClass;
        }
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor next = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM5, next) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String calledName, String calledDescriptor,
                                                    boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC && GLFW.equals(owner) && CREATE_WINDOW.equals(calledName)) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HINT_OWNER, HINT_METHOD, "()V", false);
                            }
                            super.visitMethodInsn(opcode, owner, calledName, calledDescriptor, isInterface);
                        }
                    };
                }
            }, 0);
            CeleritasVintageMixinPlugin.LOGGER.info(
                    "CELTIUM_GLLESS Display transformer installed (hint injected before glfwCreateWindow)");
            return writer.toByteArray();
        } catch (Throwable error) {
            CeleritasVintageMixinPlugin.LOGGER.error(
                    "GL-less window hint transformer failed; the window is created as the platform would", error);
            return basicClass;
        }
    }
}
