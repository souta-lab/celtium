package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The window half of the GL-less path, staged behind {@code celtium.vulkan.glless} while the rest of the migration
 * (texture routing, residual call sites) is built out.
 * <p>
 * Cleanroom remaps {@code org.lwjgl.opengl.Display} to {@code org.lwjglx.opengl.Display}, and that implementation
 * creates a GLFW window, binds a GL context to it and loads GL capabilities. Two surgical changes turn it into a
 * Vulkan-only window while leaving all of lwjglxx's bookkeeping - display mode, input callbacks, resize, title -
 * untouched:
 * <ol>
 *   <li>At method head, {@code CeltiumGlStub.install()} sets {@code OPENGL_EXPLICIT_INIT} and installs the stub
 *   function provider through {@code GL.create(provider)}. The {@code GL.createCapabilities()} lwjglxx calls later
 *   in the same method then resolves the whole function surface against the stub trampolines - no redirect needed.</li>
 *   <li>{@code GLFW_CLIENT_API = GLFW_NO_API} is hinted right before {@code glfwCreateWindow} and
 *   {@code glfwMakeContextCurrent} is skipped, so the window exists without any context.</li>
 * </ol>
 * GLFW lives under {@code org.lwjgl.glfw} at runtime, but this module compiles against the LWJGL 2 surface - the
 * calls go through reflection.
 */
@Mixin(targets = "org.lwjgl.opengl.Display")
public class LwjglxDisplayMixin {

    private static boolean celtium$gllessEnabled() {
        return Boolean.getBoolean("celtium.vulkan.glless");
    }

    private static void celtium$glfwHint(int hint, int value) {
        try {
            Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwWindowHint", int.class, int.class)
                    .invoke(null, Integer.valueOf(hint), Integer.valueOf(value));
        } catch (ReflectiveOperationException | LinkageError error) {
            System.out.println("[Celtium] CELTIUM_GLLESS hint failed: " + error);
        }
    }

    @Inject(method = "create", at = @At("HEAD"), require = 0)
    private static void celtium$installStubProvider(CallbackInfo ci) {
        if (celtium$gllessEnabled()) {
            org.soutalab.celtium.vulkan.gl.CeltiumGlStub.install();
            System.out.println("[Celtium] CELTIUM_GLLESS stub GL provider installed");
        }
    }

    @Inject(method = "create", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"), require = 0)
    private static void celtium$hintNoApi(CallbackInfo ci) {
        if (celtium$gllessEnabled()) {
            // GLFW_CLIENT_API 0x00022001, GLFW_NO_API 0 - the window is created without a context API.
            celtium$glfwHint(0x00022001, 0);
            System.out.println("[Celtium] CELTIUM_GLLESS window hinted GLFW_NO_API");
        }
    }

    @Redirect(method = "create", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V"), require = 0)
    private static void celtium$skipMakeContextCurrent(long window) {
        if (celtium$gllessEnabled()) {
            // With GLFW_NO_API there is no context to make current; the window handle stays valid for Vulkan.
            return;
        }
        try {
            Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwMakeContextCurrent", long.class)
                    .invoke(null, Long.valueOf(window));
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new RuntimeException("glfwMakeContextCurrent failed", error);
        }
    }
}
