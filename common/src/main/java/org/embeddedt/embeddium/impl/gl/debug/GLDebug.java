package org.embeddedt.embeddium.impl.gl.debug;

import java.io.PrintStream;

/**
 * Minimal GL-free binary-compatibility shell retained for Demonica Tier A bytecode references.
 * Celtium uses Vulkan validation layers rather than OpenGL KHR_debug callbacks.
 */
public final class GLDebug {
    private GLDebug() {
    }

    public static int setupDebugMessageCallback() {
        return 0;
    }

    public static int setupDebugMessageCallback(PrintStream stream) {
        return 0;
    }

    public static int disableDebugMessages() {
        return 1;
    }

    public static void reloadDebugState() {
    }

    public static void nameObject(int id, int object, String name) {
    }

    public static void pushGroup(int id, String name) {
    }

    public static void popGroup() {
    }
}
