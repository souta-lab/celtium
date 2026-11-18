package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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
 * {@code getMaxTextureSize} is answered by this transformer instead (see the method body): it has no
 * {@code enabled} guard and the atlas needs its answer, and the mixin that was meant to supply it
 * ({@code core.GLAllocationMixin}, shadowed in the mixin config - see {@code CeleritasVintageMixinPlugin})
 * never applied either, so the answer is given here, where the stubbing above proves this transformer runs.
 * <p>
 * The replaced {@code start()} keeps one instruction from the original body, under a guard: the shared-context
 * hand-off, {@code shared.makeCurrent()}, executed only if a shared context exists. Another mod's required mixin
 * injection can anchor on that call inside {@code start()} (measured 2026-11-17, leg 3 of the Demonica pairing in
 * {@code SPLASH-DESIGN-2026-11-17.md}: with the call gone, the injection check failed 0/1 and
 * {@code SplashProgress} would not load at all). Under this surface a shared context is never created - the field
 * only the original body assigned stays null - so the guarded call never executes and the hand-off it marks does
 * not happen; what the replacement preserves is the call site, in the conditional form the original's sequence
 * implied (create, then make current). If a shared context somehow did exist, calling the real
 * {@code makeCurrent()} would be the correct behaviour.
 */
public class CeltiumGllessSplashTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraftforge.fml.client.SplashProgress";
    private static final String TARGET_INTERNAL = "net/minecraftforge/fml/client/SplashProgress";
    private static final String CLIENT = "net.minecraftforge.fml.client.FMLClientHandler";
    private static final int ASM_API = 9 << 16;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        boolean client = CLIENT.equals(name) || CLIENT.equals(transformedName);
        if (basicClass == null || !client && !TARGET.equals(name) && !TARGET.equals(transformedName)) {
            return basicClass;
        }
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new org.objectweb.asm.ClassVisitor(ASM_API, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if (client) {
                    if (!"processWindowMessages".equals(methodName) || !"()V".equals(descriptor)) return target;
                    return new MethodVisitor(ASM_API, target) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "org/taumc/celeritas/core/CeltiumLoadingProgress", "refresh", "()V", false);
                        }
                    };
                }
                // The maximum texture size is measured by probing GL_PROXY_TEXTURE_2D and reading it back with
                // glGetTexLevelParameteriv, which answers zero without a GL context, so Forge's own method returns
                // -1 and TextureMap builds Stitcher(-1, -1) - measured on this same leg as
                // "StitcherException: Unable to fit: immersiveengineering:blocks/metal_multiblock_crusher". The mixin
                // that answered this never reached the class either, so the answer is given here, where the stubbing
                // above proves this transformer is applied.
                if ("getMaxTextureSize".equals(methodName) && "()I".equals(descriptor)) {
                    target.visitCode();
                    target.visitLdcInsn(Integer.valueOf(16384));
                    target.visitInsn(Opcodes.IRETURN);
                    target.visitMaxs(1, 0);
                    target.visitEnd();
                    System.out.println("[Celtium] CELTIUM_GLSTUB answered SplashProgress.getMaxTextureSize with 16384"
                            + " (the proxy-texture probe cannot run without a GL context)");
                    return null;
                }
                boolean splashEntryPoint = ("start".equals(methodName) || "finish".equals(methodName))
                        && "()V".equals(descriptor);
                if (!splashEntryPoint) {
                    return target;
                }
                // Replace the shared-GL-context thread with the native loading-screen lifetime.
                target.visitCode();
                if ("start".equals(methodName)) {
                    // The original body hands the main thread a shared context here (create, then makeCurrent)
                    // before the splash thread spawns. That context cannot exist on this surface, so the
                    // hand-off is kept only as the guarded call: the field is null whenever this transformer
                    // has run, the invoke never executes, and the injection point another mod anchors on
                    // inside start() stays real (see the class javadoc). The merge point's frame is exact:
                    // both paths arrive with an empty stack and this body uses no locals.
                    Label noSharedContext = new Label();
                    target.visitFieldInsn(Opcodes.GETSTATIC, TARGET_INTERNAL, "shared",
                            "Lcom/cleanroommc/client/sdl/SharedGLContext;");
                    target.visitJumpInsn(Opcodes.IFNULL, noSharedContext);
                    target.visitFieldInsn(Opcodes.GETSTATIC, TARGET_INTERNAL, "shared",
                            "Lcom/cleanroommc/client/sdl/SharedGLContext;");
                    target.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            "com/cleanroommc/client/sdl/SharedGLContext", "makeCurrent", "()V", false);
                    target.visitLabel(noSharedContext);
                    target.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                }
                target.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "org/taumc/celeritas/core/CeltiumLoadingProgress", methodName, "()V", false);
                target.visitInsn(Opcodes.RETURN);
                target.visitMaxs("start".equals(methodName) ? 1 : 0, 0);
                target.visitEnd();
                System.out.println("[Celtium] CELTIUM_GLSTUB stubbed SplashProgress." + methodName
                        + " (the splash needs a shared GL context a GLFW_NO_API window cannot provide)");
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }
}
