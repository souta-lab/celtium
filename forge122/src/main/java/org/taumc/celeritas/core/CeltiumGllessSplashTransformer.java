package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Makes Forge's splash screen a no-op under the GL-less surface, by rewriting the class before it is defined.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-cohort-fix128y}): a mixin on {@code SplashProgress} did not stop it -
 * {@code start()} still reached its shared-context attempt and the game died at 10.3 s with
 * {@code SplashProgress$4: org.lwjgl.LWJGLException: Couldn't create shared context hidden window}. Forge's own
 * classes are not reached the way the game's are, so the mixin never applied. This transformer runs in the coremod's
 * chain, which is the mechanism that already reaches the classes this project needs.
 * <p>
 * The splash thread creates a shared GL context, which a {@code GLFW_NO_API} window cannot provide, and Forge's own
 * reports recommend {@code enabled=false} in {@code config/splash.properties} - this is that, one step earlier.
 * {@code getMaxTextureSize} is left alone: it has no {@code enabled} guard and the atlas needs its answer, which the
 * mixin still supplies.
 */
public class CeltiumGllessSplashTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraftforge.fml.client.SplashProgress";
    private static final int ASM_API = 9 << 16;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET.equals(name) && !TARGET.equals(transformedName)) {
            return basicClass;
        }
        if ("false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"))) {
            return basicClass;
        }
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new org.objectweb.asm.ClassVisitor(ASM_API, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                boolean splashEntryPoint = ("start".equals(methodName) || "finish".equals(methodName))
                        && "()V".equals(descriptor);
                if (!splashEntryPoint) {
                    return target;
                }
                // An empty body in place of the original: nothing of the splash runs, so there is no thread to start
                // and none to check the state of later.
                target.visitCode();
                target.visitInsn(Opcodes.RETURN);
                target.visitMaxs(0, 0);
                target.visitEnd();
                System.out.println("[Celtium] CELTIUM_GLLESS stubbed SplashProgress." + methodName
                        + " (the splash needs a shared GL context a GLFW_NO_API window cannot provide)");
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }
}
