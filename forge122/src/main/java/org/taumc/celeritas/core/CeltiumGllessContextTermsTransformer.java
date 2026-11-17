package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin;

/**
 * Latches the GL context terms from the creation request in force, immediately after Cleanroom
 * creates the window inside {@code Minecraft.init}.
 * <p>
 * The request lives in {@code ForgeEarlyConfig}'s fields, and it only says what the created context
 * was asked to be around creation: a requester that rewrites the fields for its own context
 * (Demonica's startup mixin, which asks for a core profile this way) restores the user's values at
 * {@code createDisplay} HEAD, after the window exists. So the latch has to sit at the one call that
 * creates the window - {@code com.cleanroommc.client.sdl.SDL.createWindow()} inside
 * {@code Minecraft.init}, the same anchor that requester itself uses - and it has to be a transformer
 * rather than a mixin for the reason {@link CeltiumGllessWindowTransformer} records: a mixin can lose
 * the registration race against classes defined during tweak initialisation, while this chain is
 * registered when the coremod loads.
 * <p>
 * The latch call goes <i>after</i> the createWindow invoke, not before it. Measured on leg
 * {@code demonica-leg3} (2026-11-17): with the call before the invoke, it executed before the
 * requester's own mixin handler at the same anchor (the transformer chain's insertion landed ahead
 * of the mixin's), read the user's standing values (4.6, compatibility) and latched the floor terms,
 * while the requester's log line one second later showed the core request it had written. After the
 * invoke, the requester's write has happened and its restore (at {@code createDisplay} HEAD) has
 * not, so the fields hold exactly the request the window was created under. The inserted call is a
 * no-argument static that leaves the returned {@code Window$Builder} untouched on the stack.
 * <p>
 * The injection matches the call site by owner, name and descriptor, in any method of the class and
 * with any invoke opcode: production Minecraft methods carry SRG names, so matching a method name
 * would silently miss, and the class has exactly one such call site. The latch itself is in
 * {@link CeltiumGllessHint#noteContextRequest()} so this class never needs the target's classes; a
 * failure here returns the original bytes and the context terms stay at their default floor.
 * <p>
 * The visitor API is a literal for the reason {@link CeltiumGllessWindowTransformer} records: this
 * module compiles against an ASM whose {@code Opcodes} predates the ASM9 constant, while the game's
 * ASM (9.10.1) is what has to read the transformed class.
 */
public class CeltiumGllessContextTermsTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraft.client.Minecraft";
    private static final int ASM_API = 9 << 16;
    private static final String SDL = "com/cleanroommc/client/sdl/SDL";
    private static final String CREATE_WINDOW = "createWindow";
    private static final String CREATE_WINDOW_DESC = "()Lcom/cleanroommc/client/sdl/Window$Builder;";
    private static final String HINT_OWNER = "org/taumc/celeritas/core/CeltiumGllessHint";
    private static final String HINT_METHOD = "noteContextRequest";

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
            boolean[] injected = {false};
            reader.accept(new ClassVisitor(ASM_API, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor next = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    return new MethodVisitor(ASM_API, next) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String calledName, String calledDescriptor,
                                                    boolean isInterface) {
                            super.visitMethodInsn(opcode, owner, calledName, calledDescriptor, isInterface);
                            if (SDL.equals(owner) && CREATE_WINDOW.equals(calledName)
                                    && CREATE_WINDOW_DESC.equals(calledDescriptor)) {
                                // After the invoke, not before it: see the class doc for the measured
                                // ordering against the requester's own mixin handler at this anchor.
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HINT_OWNER, HINT_METHOD, "()V", false);
                                injected[0] = true;
                            }
                        }
                    };
                }
            }, 0);
            CeleritasVintageMixinPlugin.LOGGER.info(
                    "CELTIUM_GL_CONTEXT_TERMS transformer installed on {} (latch injected after SDL.createWindow: {})",
                    target, Boolean.valueOf(injected[0]));
            return writer.toByteArray();
        } catch (Throwable error) {
            CeleritasVintageMixinPlugin.LOGGER.error(
                    "GL context terms transformer failed; the context terms stay at their default floor", error);
            return basicClass;
        }
    }
}
