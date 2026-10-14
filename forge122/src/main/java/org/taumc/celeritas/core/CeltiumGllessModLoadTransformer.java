package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin;

/**
 * Installs the GL-less stub's LWJGL3 capabilities on the thread that runs mod pre-initialisation.
 * <p>
 * LWJGL3 keeps them per thread, so every thread that calls GL needs its own. The mixin plugin's {@code onLoad}
 * covers {@code main}; a mod's pre-init runs on the launcher's game thread, which is a different thread - leg
 * {@code gv-e2e-268-fix128h} aborts in Thaumcraft's {@code ClientProxy.preInit} with
 * {@code Thread[#3,Client thread,7,main]} in the message, and Fugue#80's Cleanroom stack goes
 * {@code Minecraft.func_71384_a} -> {@code FMLClientHandler.beginMinecraftLoading} -> resource reload -> the mod's
 * GL call.
 * <p>
 * A mixin could not be relied on for this entry point: a mixin registered from the plugin's list is prepared at
 * 9.8 s in the 268-mod pack, and this class is a Forge class that may already be defined by then. The transformer
 * chain has no such race, so the call is placed in the bytecode instead - the same reasoning as the window hint.
 */
public class CeltiumGllessModLoadTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraftforge.fml.client.FMLClientHandler";
    private static final String METHOD = "beginMinecraftLoading";
    private static final String HINT_OWNER = "org/taumc/celeritas/core/CeltiumGllessHint";
    private static final String HINT_METHOD = "ensureThreadCapabilities";
    private static final int ASM_API = 9 << 16;

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
            reader.accept(new ClassVisitor(ASM_API, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor next = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    if (!METHOD.equals(methodName)) {
                        return next;
                    }
                    return new MethodVisitor(ASM_API, next) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HINT_OWNER, HINT_METHOD, "()V", false);
                        }
                    };
                }
            }, 0);
            CeleritasVintageMixinPlugin.LOGGER.info(
                    "CELTIUM_GLLESS mod-load transformer installed (thread capabilities before mod pre-init)");
            return writer.toByteArray();
        } catch (Throwable error) {
            CeleritasVintageMixinPlugin.LOGGER.error(
                    "GL-less mod-load transformer failed; mod pre-init runs without per-thread capabilities", error);
            return basicClass;
        }
    }
}
