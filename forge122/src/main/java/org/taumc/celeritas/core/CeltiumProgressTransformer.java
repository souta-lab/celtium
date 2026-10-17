package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Drives the GL-less loading overlay from inside {@code ProgressManager}, by rewriting it before it is defined.
 * <p>
 * A mixin cannot reach this class: mod discovery calls {@code ProgressManager.push} seconds before mixin
 * configurations are prepared (measured: discovery at ~01:07:05 against the 9.837 s prepareConfigs pass), so the
 * target is already defined by the time any mixin could register - the same race {@link CeltiumGllessSplashTransformer}
 * documents for {@code SplashProgress}. This transformer runs in the coremod's chain and applies whenever the class
 * is first defined, which is what the race leaves open.
 * <p>
 * The calls are the two entry points {@link org.taumc.celeritas.impl.gui.CeltiumLoadingProgress} owns: bar
 * transitions draw immediately; steps are throttled inside the helper. The draws happen on the loader thread, which
 * is the same thread {@code Minecraft.drawSplashScreen} already drew and presented on, so no GL state crosses
 * threads - the reason Forge needed a second context is the one thing this design removes.
 */
public class CeltiumProgressTransformer implements IClassTransformer {
    private static final String MANAGER = "net.minecraftforge.fml.common.ProgressManager";
    private static final String BAR = "net.minecraftforge.fml.common.ProgressManager$ProgressBar";
    private static final int ASM_API = 9 << 16;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        boolean isManager = MANAGER.equals(name) || MANAGER.equals(transformedName);
        boolean isBar = BAR.equals(name) || BAR.equals(transformedName);
        if (!isManager && !isBar) {
            return basicClass;
        }
        if ("false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"))) {
            return basicClass;
        }
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        final String target = isManager ? MANAGER : BAR;
        reader.accept(new org.objectweb.asm.ClassVisitor(ASM_API, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor target1 = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                boolean instrument = isManager
                        ? ("push".equals(methodName) || "pop".equals(methodName))
                        : "step".equals(methodName);
                if (!instrument) {
                    return target1;
                }
                // Every return path gets the draw call through the throttle: measured (leg e2e-hurt7), drawing
                // unthrottled on each push/pop cost tens of milliseconds per call across thousands of registry
                // bars and the load never finished. The helper decides; the injected call is one INVOKESTATIC.
                return new MethodVisitor(ASM_API, target1) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "org/taumc/celeritas/impl/gui/CeltiumLoadingProgress",
                                    "drawIfDue", "()V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        System.out.println("[Celtium] CELTIUM_GLLESS instrumented " + target
                + " (the loading overlay's progress pulses; the splash thread it replaces cannot exist under GL-less)");
        return writer.toByteArray();
    }
}
