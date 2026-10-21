package org.soutalab.celtium.vulkan;

/**
 * The game's window, as the two loaders this renderer can meet present it.
 * <p>
 * Cleanroom 0.7 replaced GLFW with SDL 3: there is no {@code org.lwjgl.glfw} on the runtime classpath at all, and a
 * renderer that calls it throws {@code NoClassDefFoundError} the first time a frame is presented (measured: the glyph
 * capture's flush died on it while the swap chain reported zero presentations). Cleanroom exposes its window through
 * {@code com.cleanroommc.client.sdl.SDL.window()}, which is loader API rather than a maven artifact, so it is reached
 * reflectively here and the type is resolved once.
 * <p>
 * The fallback is the GLFW accessor the 0.6 line uses. Keeping both behind one class means the surface, the framebuffer
 * size and the event pump each ask one question - "what window is the game drawing into" - instead of every call site
 * deciding which loader it is on.
 */
public final class CeltiumWindow {

    /** The Cleanroom SDL entry point, or null when this build runs on a loader that does not have it. */
    /** The loader's SDL class, or null when this build runs on GLFW. Static calls are invoked on null. */
    private static final Class<?> SDL_CLASS = resolveSdl();

    private static final java.lang.reflect.Method SDL_WINDOW;
    private static final java.lang.reflect.Method WINDOW_HANDLE;
    private static final java.lang.reflect.Method WINDOW_NATIVE;
    private static final java.lang.reflect.Method NATIVE_COMPOSITOR;
    private static final java.lang.reflect.Method NATIVE_HANDLE;
    private static final java.lang.reflect.Method WINDOW_PIXEL_WIDTH;
    private static final java.lang.reflect.Method WINDOW_PIXEL_HEIGHT;
    private static final java.lang.reflect.Method WINDOW_CONSUME_RESIZED;
    private static final java.lang.reflect.Method COMPOSITOR_NAME;

    static {
        java.lang.reflect.Method window = null;
        java.lang.reflect.Method handle = null;
        java.lang.reflect.Method nativeWindow = null;
        java.lang.reflect.Method compositor = null;
        java.lang.reflect.Method nativeHandle = null;
        java.lang.reflect.Method pixelWidth = null;
        java.lang.reflect.Method pixelHeight = null;
        java.lang.reflect.Method consumeResized = null;
        java.lang.reflect.Method compositorName = null;
        if (SDL_CLASS != null) {
            // Resolved one at a time: only the window accessor and its pointer are required, and a loader whose window
            // class spells an optional accessor differently must not send the whole renderer back to GLFW. Each miss is
            // named once, because a silent fallback is how the first attempt produced a NoClassDefFoundError for GLFW
            // on every offered batch while the log said the window had been found.
            java.util.List<String> missing = new java.util.ArrayList<String>();
            window = method(SDL_CLASS, "window", missing);
            if (window != null) {
                Class<?> windowType = window.getReturnType();
                handle = method(windowType, "handle", missing);
                nativeWindow = method(windowType, "nativeWindow", missing);
                pixelWidth = method(windowType, "pixelWidth", missing);
                pixelHeight = method(windowType, "pixelHeight", missing);
                consumeResized = method(windowType, "consumeResized", missing);
                if (nativeWindow != null) {
                    Class<?> nativeType = nativeWindow.getReturnType();
                    nativeHandle = method(nativeType, "handle", missing);
                    compositor = method(nativeType, "compositor", missing);
                    if (compositor != null) {
                        compositorName = method(compositor.getReturnType(), "name", missing);
                    }
                }
                if (handle == null) {
                    // Without the pointer there is no surface; treating SDL as present would be a half-wired path.
                    window = null;
                }
            }
            System.out.println("[Celtium] CELTIUM_WINDOW shape resolved=" + (window != null)
                    + (missing.isEmpty() ? "" : " missing=" + missing));
        }
        SDL_WINDOW = window;
        WINDOW_HANDLE = handle;
        WINDOW_NATIVE = nativeWindow;
        NATIVE_COMPOSITOR = compositor;
        NATIVE_HANDLE = nativeHandle;
        WINDOW_PIXEL_WIDTH = pixelWidth;
        WINDOW_PIXEL_HEIGHT = pixelHeight;
        WINDOW_CONSUME_RESIZED = consumeResized;
        COMPOSITOR_NAME = compositorName;
    }

    private static java.lang.reflect.Method method(Class<?> type, String name, java.util.List<String> missing) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException absent) {
            missing.add(name);
            return null;
        }
    }

    private CeltiumWindow() {
    }

    /**
     * Finds the loader's SDL entry point through every loader that could plausibly see it.
     * <p>
     * A mod is loaded by the game's LaunchClassLoader while the loader's own classes sit above it, and which loader
     * answers {@code Class.forName} depends on the line that runs it: measured on Cleanroom 0.7, resolving through this
     * class's own loader alone came back empty, the swap chain fell through to the GLFW path and every offered batch
     * died with {@code NoClassDefFoundError: org/lwjgl/glfw/GLFW}. So the context loader and the system loader are
     * tried as well, and the outcome is logged once because "the window abstraction silently picked GLFW" is exactly
     * the kind of failure that needs a line saying so.
     */
    private static Class<?> resolveSdl() {
        String name = "com.cleanroommc.client.sdl.SDL";
        ClassLoader[] loaders = new ClassLoader[] {
                CeltiumWindow.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader(),
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> sdl = Class.forName(name, false, loader);
                System.out.println("[Celtium] CELTIUM_WINDOW sdl=" + name + " via="
                        + (loader == CeltiumWindow.class.getClassLoader() ? "mod" : "context-or-system"));
                return sdl;
            } catch (ClassNotFoundException | LinkageError absent) {
                // Try the next loader: the class may live on the other side of the loader boundary.
            }
        }
        System.out.println("[Celtium] CELTIUM_WINDOW sdl=absent (GLFW fallback)");
        return null;
    }

    /** {@return whether the loader presents through SDL rather than GLFW}. */
    public static boolean sdl() {
        return SDL_WINDOW != null;
    }

    /** {@return the SDL_Window pointer the loader owns}, or 0 when this build is on GLFW. */
    public static long sdlWindowPointer() {
        if (SDL_WINDOW == null) {
            return 0L;
        }
        try {
            Object window = SDL_WINDOW.invoke(null);
            if (window == null) {
                return 0L;
            }
            Object pointer = WINDOW_HANDLE.invoke(window);
            return pointer instanceof Long ? ((Long) pointer).longValue() : 0L;
        } catch (ReflectiveOperationException failed) {
            return 0L;
        }
    }

    /**
     * {@return the compositor SDL reports}, in the loader's own vocabulary ("X11", "WAYLAND", ...), or null when this
     * build is on GLFW. The upper-case names are matched case-insensitively by callers.
     */
    public static String sdlCompositor() {
        if (SDL_WINDOW == null) {
            return null;
        }
        try {
            Object window = SDL_WINDOW.invoke(null);
            if (window == null) {
                return null;
            }
            Object nativeWindow = WINDOW_NATIVE.invoke(window);
            if (nativeWindow == null) {
                return null;
            }
            Object compositor = NATIVE_COMPOSITOR.invoke(nativeWindow);
            Object name = compositor == null ? null : COMPOSITOR_NAME.invoke(compositor);
            return name instanceof String ? (String) name : null;
        } catch (ReflectiveOperationException failed) {
            return null;
        }
    }

    /**
     * {@return the native window handle SDL reports}, which for X11 is the window id and for Wayland the wl_surface.
     * <p>
     * The surface is created through {@code SDL_Vulkan_CreateSurface} from the SDL_Window pointer, so this is for
     * diagnostics and for the compositor's own identity rather than for surface creation.
     */
    public static long sdlNativeHandle() {
        if (SDL_WINDOW == null) {
            return 0L;
        }
        try {
            Object window = SDL_WINDOW.invoke(null);
            if (window == null) {
                return 0L;
            }
            Object nativeWindow = WINDOW_NATIVE.invoke(window);
            if (nativeWindow == null) {
                return 0L;
            }
            Object handle = NATIVE_HANDLE.invoke(nativeWindow);
            return handle instanceof Long ? ((Long) handle).longValue() : 0L;
        } catch (ReflectiveOperationException failed) {
            return 0L;
        }
    }

    /**
     * Writes the framebuffer size in pixels into {@code out} and returns whether it was available.
     * <p>
     * The drawable's pixel size, not the window's logical size: a HiDPI compositor scales one into the other, and the
     * swapchain has to match the pixels.
     */
    public static boolean sdlPixelSize(int[] out) {
        if (SDL_WINDOW == null || out == null || out.length < 2) {
            return false;
        }
        try {
            Object window = SDL_WINDOW.invoke(null);
            if (window == null) {
                return false;
            }
            Object width = WINDOW_PIXEL_WIDTH.invoke(window);
            Object height = WINDOW_PIXEL_HEIGHT.invoke(window);
            if (!(width instanceof Integer) || !(height instanceof Integer)) {
                return false;
            }
            out[0] = ((Integer) width).intValue();
            out[1] = ((Integer) height).intValue();
            return out[0] > 0 && out[1] > 0;
        } catch (ReflectiveOperationException failed) {
            return false;
        }
    }

    /** {@return whether the compositor resized the window since the last call}, consuming the flag. */
    public static boolean sdlConsumeResized() {
        if (WINDOW_CONSUME_RESIZED == null) {
            return false;
        }
        try {
            Object window = SDL_WINDOW.invoke(null);
            if (window == null) {
                return false;
            }
            Object resized = WINDOW_CONSUME_RESIZED.invoke(window);
            return resized instanceof Boolean && ((Boolean) resized).booleanValue();
        } catch (ReflectiveOperationException failed) {
            return false;
        }
    }
}
