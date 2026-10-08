package org.soutalab.celtium.probe;

import org.lwjgl.egl.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Isolated Linux CLI context, adapted from Celeritas' HeadlessGl benchmark utility.
 * The in-game probe uses the existing context and never calls this class.
 */
final class HeadlessGlContext implements AutoCloseable {
    private long display;
    private long context;
    private boolean initialized;
    private boolean glLoaded;

    static HeadlessGlContext create(int deviceIndex) {
        HeadlessGlContext result = new HeadlessGlContext();
        try {
            result.open(deviceIndex);
            return result;
        } catch (RuntimeException | LinkageError error) {
            result.close();
            throw error;
        }
    }

    private void open(int deviceIndex) {
        Configuration.OPENGL_EXPLICIT_INIT.set(true);
        // EGL client-extension flags have been unreliable in LWJGL. Resolve this entry point directly,
        // as the upstream benchmark does, rather than guessing a display server/session environment.
        long platformDisplay = EGL.getFunctionProvider().getFunctionAddress("eglGetPlatformDisplayEXT");
        if (deviceIndex >= 0) {
            display = deviceDisplay(deviceIndex, platformDisplay);
        } else {
            if (platformDisplay != 0L) display = JNI.invokePPP(0x31DD, EGL14.EGL_DEFAULT_DISPLAY, 0L, platformDisplay);
            if (display == EGL10.EGL_NO_DISPLAY) display = EGL10.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        }
        if (display == EGL10.EGL_NO_DISPLAY) throw failure("No EGL display");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer major = stack.mallocInt(1), minor = stack.mallocInt(1);
            if (!EGL10.eglInitialize(display, major, minor)) throw failure("eglInitialize");
            initialized = true;
            EGLCapabilities caps = EGL.createDisplayCapabilities(display, major.get(0), minor.get(0));
            if (!caps.EGL_KHR_surfaceless_context) throw failure("Missing EGL_KHR_surfaceless_context");
            if (!EGL12.eglBindAPI(EGL14.EGL_OPENGL_API)) throw failure("eglBindAPI");
            long config = chooseConfig(stack);
            context = EGL10.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                    stack.ints(EGL15.EGL_CONTEXT_MAJOR_VERSION, 4, EGL15.EGL_CONTEXT_MINOR_VERSION, 6,
                            EGL15.EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL15.EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT, EGL10.EGL_NONE));
            if (context == EGL10.EGL_NO_CONTEXT) throw failure("eglCreateContext(4.6 core)");
            if (!EGL10.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, context)) throw failure("eglMakeCurrent");
        }
        GL.create((ByteBuffer name) -> EGL10.eglGetProcAddress(name));
        glLoaded = true;
        GL.createCapabilities();
    }

    private long deviceDisplay(int deviceIndex, long platformDisplay) {
        long queryDevices = EGL.getFunctionProvider().getFunctionAddress("eglQueryDevicesEXT");
        if (queryDevices == 0L || platformDisplay == 0L) {
            throw new IllegalStateException("Explicit GPU selection requires EGL_EXT_device_enumeration and EGL_EXT_platform_device");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer devices = stack.callocPointer(16);
            IntBuffer count = stack.callocInt(1);
            if (JNI.invokePPI(devices.capacity(), devices.address(), MemoryUtil.memAddress(count), queryDevices) == EGL10.EGL_FALSE) {
                throw failure("eglQueryDevicesEXT");
            }
            if (count.get(0) > devices.capacity() || deviceIndex >= count.get(0)) {
                throw new IllegalArgumentException("EGL device index " + deviceIndex + " unavailable; enumerated " + count.get(0) + " devices");
            }
            // EGL_PLATFORM_DEVICE_EXT: selecting an explicit device must never fall back to the default GPU.
            return JNI.invokePPP(0x313F, devices.get(deviceIndex), 0L, platformDisplay);
        }
    }

    private long chooseConfig(MemoryStack stack) {
        org.lwjgl.PointerBuffer configs = stack.mallocPointer(1);
        IntBuffer count = stack.mallocInt(1);
        if (!EGL10.eglChooseConfig(display, stack.ints(EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL12.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_BIT, EGL10.EGL_NONE), configs, count) || count.get(0) == 0) {
            throw failure("eglChooseConfig");
        }
        return configs.get(0);
    }

    private static IllegalStateException failure(String operation) {
        return new IllegalStateException(operation + ": EGL error 0x" + Integer.toHexString(EGL10.eglGetError()));
    }

    @Override
    public void close() {
        if (initialized) {
            EGL10.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            if (context != EGL10.EGL_NO_CONTEXT) EGL10.eglDestroyContext(display, context);
            EGL10.eglTerminate(display);
            initialized = false;
        }
        if (glLoaded) {
            GL.setCapabilities(null);
            GL.destroy();
            glLoaded = false;
        }
    }
}
