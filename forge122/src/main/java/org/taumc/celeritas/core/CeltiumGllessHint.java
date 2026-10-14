package org.taumc.celeritas.core;

/**
 * The GL-less window hint, in its own class so the transformer can call it without loading the target's classes.
 * <p>
 * Measured 2026-10-15: the mixin that used to do this cannot be relied on. In the 268-mod E2E pack RandomPatches'
 * tweak initialisation defines {@code org.lwjgl.opengl.Display} at 8.975 s (leg {@code gv-e2e-268-xlog},
 * {@code -Xlog:class+load=info}), while Mixin prepares its configurations - both the JSON lists and the plugin's
 * {@code getMixins()} list - in one deferred pass that runs at 9.837 s. A mixin registered after its target class
 * is defined can never apply, so the stub was never installed and the window was created with a real GL 4.6
 * context.
 * <p>
 * The transformer chain has no such race: it is registered when the coremod loads, before any of this. The call
 * below is placed immediately before {@code GLFW.glfwCreateWindow} in {@code Display.create}, which is after
 * {@code glfwInit} (GLFW only permits the init-related calls before it, and {@code glfwInit} resets window hints to
 * their defaults) and before the window exists. It is idempotent and does nothing when GL-less is opted out.
 */
public final class CeltiumGllessHint {
    private static boolean applied;

    private CeltiumGllessHint() {
    }


    /** The GL-less identity this project answers GL_RENDERER and friends with. */
    private static final String IDENTITY = "Celtium (GL-less Vulkan)";

    /** The off-heap bytes behind {@link #glStringPointer()}; held so the address stays valid. */
    private static java.nio.ByteBuffer identityBytes;
    private static long identityPointer;

    /**
     * The address of the NUL-terminated identity string, for LWJGL3's pointer-returning {@code nglGetString}.
     * <p>
     * Measured 2026-10-15: the first version of this emitted {@code MemoryUtil.memASCII(CharSequence)} expecting a
     * {@code long}, and LWJGL 3.4.1's overload returns a {@code ByteBuffer} - so every leg died at 8-15 s with
     * {@code NoSuchMethodError: 'long org.lwjgl.system.MemoryUtil.memASCII(java.lang.CharSequence)'}. The pointer is
     * {@code memAddress(memASCII(...))}, computed once and kept: {@code glGetString} can be called every frame, and
     * allocating off-heap memory per call would leak.
     */
    public static synchronized long glStringPointer() {
        if (identityPointer == 0L) {
            identityBytes = org.lwjgl.system.MemoryUtil.memASCII(IDENTITY);
            identityPointer = org.lwjgl.system.MemoryUtil.memAddress(identityBytes);
        }
        return identityPointer;
    }

    /**
     * Answers the integer queries the game itself validates, writing into the caller's buffer.
     * <p>
     * Measured 2026-10-15 (legs {@code gv-cohort-fix128s} / {@code gv-e2e-268-fix128s}): a replacement that returns
     * the type's zero for everything makes Minecraft throw during startup - {@code glCheckFramebufferStatus}
     * returned "unknown status:0" from {@code Framebuffer.checkFramebufferComplete}, called from
     * {@code Minecraft.init}. gpu-verifier's rule is the general form of it: <b>a zero-returning replacement breaks
     * every query whose result the caller checks</b>, and {@code glGetIntegerv} has eleven call sites inside
     * Minecraft (GL_MAX_TEXTURE_SIZE and friends, where 0 causes a different failure further on).
     * <p>
     * Writing the buffer rather than leaving it alone matters: LWJGL's {@code glGetIntegerv(int, IntBuffer)} is Java
     * and calls this native with the buffer's address, so the caller reads whatever is at that address. The values
     * below are the ones this renderer can honestly claim - a large but finite texture limit, the texture-unit and
     * attribute counts a GL 3.0 profile guarantees - and anything unknown is written as 0 rather than left as
     * uninitialised memory.
     */
    public static void glGetIntegerv(int pname, long params) {
        if (params == 0L) {
            return;
        }
        int value;
        switch (pname) {
            case 0x0D33: // GL_MAX_TEXTURE_SIZE
                value = 16384;
                break;
            case 0x8872: // GL_MAX_TEXTURE_IMAGE_UNITS
                value = 16;
                break;
            case 0x8B4D: // GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS
                value = 32;
                break;
            case 0x8869: // GL_MAX_VERTEX_ATTRIBS
                value = 16;
                break;
            case 0x80E9: // GL_MAX_VERTEX_UNIFORM_COMPONENTS
            case 0x8B4A: // GL_MAX_FRAGMENT_UNIFORM_COMPONENTS
                value = 4096;
                break;
            case 0x0D3B: // GL_MAX_VIEWPORT_DIMS
                org.lwjgl.system.MemoryUtil.memPutInt(params, 16384);
                org.lwjgl.system.MemoryUtil.memPutInt(params + 4L, 16384);
                return;
            case 0x821D: // GL_NUM_EXTENSIONS
                value = 0;
                break;
            default:
                value = 0;
                break;
        }
        org.lwjgl.system.MemoryUtil.memPutInt(params, value);
    }

    /** Called from the injected instruction; never throws into the window creation path. */
    public static void beforeWindowCreation() {
        if (applied || "false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"))) {
            return;
        }
        applied = true;
        try {
            Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwWindowHint", int.class, int.class)
                    .invoke(null, Integer.valueOf(0x00022001), Integer.valueOf(0));
            org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.info(
                    "CELTIUM_GLLESS window hinted GLFW_NO_API before glfwCreateWindow (from the coremod transformer)");
        } catch (Throwable error) {
            // A failed hint must not stop the window: the run is then a hybrid, which the leg's own counters show.
            org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.warn(
                    "CELTIUM_GLLESS window hint failed in the transformer path: {}", error.toString());
        }
    }
}
