package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin;

/**
 * Guards the window-layer GL entry points that sit outside the GL stub's package, so a call to one of them is
 * answered inside celtium instead of executing in the real GL driver.
 * <p>
 * The native transformer covers {@code org.lwjgl.opengl.*} only. The window layer's own GL calls live in two
 * other LWJGL classes and kept their real behaviour: {@code GLFW.glfwSwapBuffers} /
 * {@code GLFW.glfwMakeContextCurrent} and {@code SDLVideo.SDL_GL_SwapWindow} / {@code SDLVideo.SDL_GL_MakeCurrent}.
 * In GL-less mode none of them has a legitimate role - the window was created {@code GLFW_NO_API} for a Vulkan
 * surface and the Vulkan swapchain is the only presenter - so a call here is the window layer's own context
 * bookkeeping (measured: Cleanroom's window builder calls {@code SDL_GL_MakeCurrent} during creation), a
 * leftover of a code path that assumed a GL renderer, or a mod probing a surface celtium deliberately does
 * not provide. Item 349's
 * diagnosis (issue #423) names this the largest identified hole in the interposition: the reporter's JVM died
 * inside {@code nvoglv64.dll} in the first frame of a world join, and world transitions are exactly when
 * swap/context-current calls cluster. Which call faulted is still unknown (the native stack sits in the
 * reporter's {@code hs_err} file), so this guard claims no fix: it removes the hole, and the census in
 * {@link CeltiumGllessHint#noteWindowGlCall} names the caller in the log if anything still attempts one.
 * <p>
 * The LWJGL 3.4.3 bindings are not {@code native} methods - they are Java bodies that invoke a function address
 * through {@code org.lwjgl.system.JNI} - so the native transformer's rewrite cannot reach them and the four
 * named bodies are replaced wholesale instead, the same discipline as
 * {@link CeltiumGllessSdlSwapTransformer}: the call is answered without touching the window library at all.
 * The SDL pair reports success ({@code true}), which is the answer the GL-less surface gives everywhere else:
 * a caller told the swap or the make-current failed may abort, retry in a loop, or tear its window down, while
 * the rendering it would have presented is celtium's Vulkan path either way. Every other method of both
 * classes - window creation, input, events, the Vulkan surface entry points - is left byte-identical: GLFW and
 * SDL are the window layer celtium itself stands on, and only their GL entry points are guarded.
 */
public class CeltiumGllessWindowGlTransformer implements IClassTransformer {
    private static final String GLFW = "org.lwjgl.glfw.GLFW";
    private static final String SDL_VIDEO = "org.lwjgl.sdl.SDLVideo";
    private static final int ASM_API = 9 << 16;
    private static final String HINT = "org/taumc/celeritas/core/CeltiumGllessHint";
    private static final String NOTE = "noteWindowGlCall";
    private static final String NOTE_DESC = "(Ljava/lang/String;)V";

    /** One guarded entry point: its class, method name, descriptor, and the value the guard answers. */
    private static final class Guard {
        final String owner;
        final String name;
        final String descriptor;
        /** Local slots the descriptor's arguments occupy (a long takes two). */
        final int locals;
        /** Whether the guard answers {@code true} (the two SDL entry points) or nothing (the two GLFW ones). */
        final boolean answerTrue;

        Guard(String owner, String name, String descriptor, int locals, boolean answerTrue) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.locals = locals;
            this.answerTrue = answerTrue;
        }
    }

    private static final Guard[] GUARDS = {
            new Guard(GLFW, "glfwSwapBuffers", "(J)V", 2, false),
            new Guard(GLFW, "glfwMakeContextCurrent", "(J)V", 2, false),
            new Guard(SDL_VIDEO, "SDL_GL_SwapWindow", "(J)Z", 2, true),
            new Guard(SDL_VIDEO, "SDL_GL_MakeCurrent", "(JJ)Z", 4, true),
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String target = transformedName != null ? transformedName : name;
        boolean isGlfw = GLFW.equals(target);
        boolean isSdl = SDL_VIDEO.equals(target);
        if (!isGlfw && !isSdl) {
            return basicClass;
        }
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, 0);
            int[] guarded = new int[1];
            reader.accept(new ClassVisitor(ASM_API, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                        String[] exceptions) {
                    Guard guard = null;
                    for (Guard candidate : GUARDS) {
                        if (candidate.owner.equals(target) && candidate.name.equals(methodName)
                                && candidate.descriptor.equals(descriptor)) {
                            guard = candidate;
                            break;
                        }
                    }
                    if (guard == null) {
                        return super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    }
                    guarded[0]++;
                    // The native flag is dropped as the native transformer drops it: on a binding version where
                    // one of these is a real native, the replacement body is what makes the guard real at all.
                    MethodVisitor method = super.visitMethod(access & ~Opcodes.ACC_NATIVE, methodName, descriptor,
                            signature, exceptions);
                    method.visitCode();
                    method.visitLdcInsn(guard.name);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, HINT, NOTE, NOTE_DESC, false);
                    if (guard.answerTrue) {
                        method.visitInsn(Opcodes.ICONST_1);
                        method.visitInsn(Opcodes.IRETURN);
                    } else {
                        method.visitInsn(Opcodes.RETURN);
                    }
                    method.visitMaxs(1, guard.locals);
                    method.visitEnd();
                    return null;
                }
            }, 0);
            if (guarded[0] == 0) {
                return basicClass;
            }
            System.out.println("[Celtium] CELTIUM_GLSTUB guarded " + guarded[0] + " window-layer GL entry point(s) in "
                    + target + " (the Vulkan swapchain is the only presenter; a swap or context-current here would"
                    + " execute in the real GL driver against a GLFW_NO_API window)");
            return writer.toByteArray();
        } catch (Throwable error) {
            CeleritasVintageMixinPlugin.LOGGER.error(
                    "GL-less window-layer guard failed for {}; its GL entry points keep LWJGL's behaviour", target, error);
            return basicClass;
        }
    }
}
