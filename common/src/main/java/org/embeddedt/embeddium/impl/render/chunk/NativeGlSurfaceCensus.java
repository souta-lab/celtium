package org.embeddedt.embeddium.impl.render.chunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A log-only census of the native-GL-adjacent surface a world load runs under, so a crash report self-describes
 * what could have reached the real GL driver outside celtium's stub.
 * <p>
 * Celtium's own GL surface cannot reach the driver - every LWJGL GL native is rewritten and every capability
 * address is a celtium trampoline - but that says nothing about libraries that bind GL themselves: MCEF/CEF
 * runs Chromium in-process, and any mod that loads its own GL binder brings the driver along with it. Item 349's
 * diagnosis (issue #423) could only name MCEF as a possibility, with no supporting evidence in the reporter's
 * log, because the log never said which of those libraries was loaded. This probe runs once per world load and
 * prints one line naming the loaded markers.
 * <p>
 * The probe never loads or initialises a class: it asks each reachable class loader for the class it already
 * holds ({@code ClassLoader.findLoadedClass}), including the launch class loader mods run under. A marker that
 * is merely present on the class path but never loaded is not listed - the line answers "loaded in this process",
 * not "installed". The window-layer entries ({@code glfw}, {@code sdl}) say whether the guarded GLFW/SDL
 * entry points from item 352 exist in-process at all; the binder entries ({@code mcef}, {@code jcef}) are the
 * libraries that can still call the driver directly. The line is deduplicated: it prints only when the loaded
 * set changes since the last print, so dimension changes in one session do not repeat it.
 */
public final class NativeGlSurfaceCensus {
    /** Marker class names for the libraries that can reach the real GL driver outside the stub. */
    private static final String[][] MARKERS = {
            // The Chromium embedder a pack's browser mods ride on (WebDisplays in the item-349 pack).
            {"mcef", "net.montoyo.mcef.MCEF"},
            {"mcef-cinemamod", "com.cinemamod.mcef.MCEF"},
            {"jcef", "org.cef.CefApp"},
            // The window layer itself: whether the guarded GLFW/SDL entry points are live in-process.
            {"glfw", "org.lwjgl.glfw.GLFW"},
            {"sdl", "org.lwjgl.sdl.SDLVideo"},
    };

    private static String lastPrinted;

    private NativeGlSurfaceCensus() {
    }

    /**
     * {@return the one-line census, or {@code null} when the loaded set is unchanged since the last print}.
     * <p>
     * The line names loaded markers in the {@code binders} group (libraries that bind GL themselves) and the
     * {@code windowLayer} group (the classes whose GL entry points item 352 guards), with {@code none} for an
     * empty group so an empty result is still evidence rather than a missing line.
     */
    public static synchronized String line() {
        StringBuilder binders = new StringBuilder();
        StringBuilder windowLayer = new StringBuilder();
        for (String[] marker : MARKERS) {
            boolean window = marker[0].equals("glfw") || marker[0].equals("sdl");
            StringBuilder into = window ? windowLayer : binders;
            if (isLoaded(marker[1])) {
                if (into.length() > 0) {
                    into.append(' ');
                }
                into.append(marker[0]);
            }
        }
        String rendered = "binders=[" + (binders.length() == 0 ? "none" : binders) + "]"
                + " windowLayer=[" + (windowLayer.length() == 0 ? "none" : windowLayer) + "]";
        if (rendered.equals(lastPrinted)) {
            return null;
        }
        lastPrinted = rendered;
        return rendered;
    }

    /** Whether any reachable class loader already holds {@code className}, without loading or initialising it. */
    private static boolean isLoaded(String className) {
        ClassLoader[] loaders = {
                NativeGlSurfaceCensus.class.getClassLoader(),
                ClassLoader.getSystemClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                launchClassLoader(),
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Method findLoaded = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
                findLoaded.setAccessible(true);
                if (findLoaded.invoke(loader, className) != null) {
                    return true;
                }
            } catch (Throwable ignored) {
                // A loader that cannot be asked is not evidence either way; the next loader decides.
            }
        }
        return false;
    }

    /** The launch class loader mods run under, or {@code null} outside a launchwrapper environment. */
    private static ClassLoader launchClassLoader() {
        try {
            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", false,
                    NativeGlSurfaceCensus.class.getClassLoader());
            Field field = launch.getField("classLoader");
            Object value = field.get(null);
            return value instanceof ClassLoader loader ? loader : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
