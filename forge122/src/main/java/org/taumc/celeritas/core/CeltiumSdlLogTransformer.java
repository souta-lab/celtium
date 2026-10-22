package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Removes the Java callback Cleanroom installs as SDL's log handler.
 * <p>
 * Measured from a core dump, not guessed: the process died on a native thread with no Java frame, and the stack was
 * libwayland's {@code wl_display_dispatch_queue_pending} called from libSDL3, wayland delivering into a libffi closure,
 * and SDL again before the fault. LWJGL implements Java callbacks over libffi, and the only such callback in that path is
 * the log handler {@code com.cleanroommc.client.sdl.SDL.installLog} registers - so SDL logging from inside its own event
 * dispatch re-entered SDL through a Java callback and crashed.
 * <p>
 * The handler is therefore turned into a no-op: SDL then keeps its default output, and no libffi closure takes part in
 * wayland's dispatch. Nothing the renderer needs is lost - it reports its own failures to the log.
 */
public class CeltiumSdlLogTransformer implements IClassTransformer {

    private static final String TARGET = "com.cleanroommc.client.sdl.SDL";
    private static final String METHOD = "installLog";
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
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            boolean[] stubbed = new boolean[1];
            reader.accept(new ClassVisitor(ASM_API, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                        String[] exceptions) {
                    if (METHOD.equals(methodName) && "()V".equals(descriptor)) {
                        stubbed[0] = true;
                        MethodVisitor sink = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                        // No delegate on purpose: every original instruction, annotation and local-variable entry is
                        // dropped, and only a bare return is written to the class writer. The earlier version kept a
                        // delegate and therefore emitted the original body as well, which produced a class the game
                        // refused (exit 255 in eight seconds).
                        return new MethodVisitor(ASM_API) {
                            @Override
                            public void visitCode() {
                                sink.visitCode();
                                sink.visitInsn(Opcodes.RETURN);
                                sink.visitMaxs(0, 0);
                            }

                            @Override
                            public void visitEnd() {
                                sink.visitEnd();
                            }
                        };
                    }
                    return super.visitMethod(access, methodName, descriptor, signature, exceptions);
                }
            }, 0);
            if (stubbed[0]) {
                System.out.println("[Celtium] CELTIUM_SDL_LOG installLog stubbed (no Java callback in wayland's dispatch)");
                return writer.toByteArray();
            }
            return basicClass;
        } catch (Throwable error) {
            System.out.println("[Celtium] CELTIUM_SDL_LOG transform failed: " + error);
            return basicClass;
        }
    }
}
