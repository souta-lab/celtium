package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Makes Cleanroom's SDL window stop swapping a GL surface the GL-less renderer never owns.
 * <p>
 * {@code org.lwjgl.opengl.Display.update()} calls {@code WindowBridge.swapBuffers()}, which ends in
 * {@code com.cleanroommc.client.sdl.Window.swapBuffers()} - SDL's own window-surface swap. Under the GL-less renderer
 * there is no GL context, so that call has nothing to present; and it presents on the same wl_surface the Vulkan
 * swapchain targets. Measured on the isolated sway compositor, entering fullscreen with both presenters live is
 * followed by {@code wp_linux_drm_syncobj_surface_v1 error 4: Buffer attached but no acquire point set} and the
 * compositor closes the connection, which ends the session as a clean exit. One presenter per surface: the native
 * Vulkan path presents, and this call becomes a no-op. It is left active when the GL-less path is off, because there
 * SDL is the only presenter.
 */
public class CeltiumGllessSdlSwapTransformer implements IClassTransformer {
    private static final String TARGET = "com.cleanroommc.client.sdl.Window";
    private static final int ASM_API = 9 << 16;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET.equals(name) && !TARGET.equals(transformedName)) {
            return basicClass;
        }
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new org.objectweb.asm.ClassVisitor(ASM_API, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if (!"swapBuffers".equals(methodName) || !"()Lcom/cleanroommc/client/sdl/Window;".equals(descriptor)) {
                    return target;
                }
                // Returns the window the way the original did (it answers itself), without touching SDL.
                target.visitCode();
                target.visitVarInsn(Opcodes.ALOAD, 0);
                target.visitInsn(Opcodes.ARETURN);
                target.visitMaxs(1, 1);
                target.visitEnd();
                System.out.println("[Celtium] CELTIUM_GLSTUB stubbed Window.swapBuffers (the Vulkan swapchain is the"
                        + " only presenter on this wl_surface; SDL's own swap would attach a second buffer)");
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }
}
