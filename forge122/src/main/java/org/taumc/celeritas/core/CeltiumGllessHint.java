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


    /**
     * Whether the GL-less surface is in use, read from the same property the rest of the mod uses.
     * <p>
     * The mixins that answer calls the GL surface cannot need to be inert on a hybrid run, where a real GL context
     * exists and its answers are the right ones.
     */
    public static boolean isGllessClaimed() {
        return !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"));
    }

    /** The next object name this renderer hands out; starts at 1 because 0 means "none" to every caller. */
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_NAME =
            new java.util.concurrent.atomic.AtomicInteger(1);

    /**
     * A fresh, non-zero object name for the {@code glGen*} and {@code glCreate*} families.
     * <p>
     * Measured 2026-10-15 (leg {@code gv-e2e-268-fix128w}, the 268-mod pack): after the atlas was built the pack died
     * with {@code java.lang.IllegalStateException: glGenLists returned an ID of 0 for a count of 1, GL error (0): No
     * error code reported}, which is vanilla's own check in {@code GLAllocation.generateDisplayLists} - it treats a
     * zero id as failure and reads the error code to explain it, and this renderer reports no error, so the message
     * ends up saying nothing. The same shape applies to every generator: the id is the caller's handle, 0 means "no
     * object", and a replacement that answers the type's zero makes correct callers fail.
     * <p>
     * The ids are opaque here - this renderer keeps its own resources and ignores display lists, textures and buffers
     * created through the GL surface - so all that is required is that they are non-zero and distinct.
     */
    public static int glGenName() {
        return NEXT_NAME.getAndIncrement();
    }

    /** The maximum texture size this renderer claims, in one place for every surface that has to answer it. */
    public static int claimedMaxTextureSize() {
        return 16384;
    }

    /** The GL-less renderer's name, for GL_RENDERER. */
    private static final String IDENTITY = "Celtium (GL-less Vulkan)";

    /**
     * One NUL-terminated off-heap copy per queried name, kept so the addresses stay valid. {@code glGetString} can be
     * called every frame, so allocating per call would leak.
     */
    private static final java.util.Map<Integer, java.nio.ByteBuffer> STRINGS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Long> STRING_ADDRESSES = new java.util.HashMap<>();

    /**
     * The address of the NUL-terminated string for a {@code glGetString} name, for LWJGL3's pointer-returning
     * {@code nglGetString}.
     * <p>
     * Measured 2026-10-15, in two steps. The first version emitted {@code MemoryUtil.memASCII(CharSequence)} expecting
     * a {@code long}, and LWJGL 3.4.1's overload returns a {@code ByteBuffer} - every leg died at 8-15 s with
     * {@code NoSuchMethodError}. The second returned one identity string for every name, and the cohort leg then died
     * at 24.8 s with {@code java.lang.RuntimeException: Failed to parse the OpenGL version.} - thrown by Cleanroom's
     * own Kirino patch ({@code com.cleanroommc.kirino.KirinoClientCore}), which parses {@code glGetString(GL_VERSION)}
     * and cannot parse a renderer name.
     * <p>
     * So the answers are per name and deliberately the same strings {@code CeltiumGlStub} gives the LWJGL2 surface -
     * {@code "3.0.0 Celtium"} for GL_VERSION (the version whose gates are all backed, per the stub's own note),
     * {@code ""} for GL_EXTENSIONS (this renderer interprets no extension), {@code "1.20"} for the shading language -
     * because the two GL surfaces have to agree: one of them being parseable and the other not is a crash, not a
     * difference of opinion.
     */
    public static synchronized long glStringPointer(int pname) {
        Long cached = STRING_ADDRESSES.get(Integer.valueOf(pname));
        if (cached != null) {
            return cached.longValue();
        }
        String value;
        switch (pname) {
            case 0x1F00: // GL_VENDOR
                value = "Celtium";
                break;
            case 0x1F01: // GL_RENDERER
                value = IDENTITY;
                break;
            case 0x1F02: // GL_VERSION - must stay parseable: Cleanroom's Kirino patch reads it
                value = "3.0.0 Celtium";
                break;
            case 0x1F03: // GL_EXTENSIONS
                value = "";
                break;
            case 0x8B8C: // GL_SHADING_LANGUAGE_VERSION
            case 0x821B: // GL_SHADING_LANGUAGE_VERSION_ARB - the same question, the ARB spelling
                value = "1.20";
                break;
            default:
                value = "";
                break;
        }
        java.nio.ByteBuffer bytes = org.lwjgl.system.MemoryUtil.memASCII(value);
        long address = org.lwjgl.system.MemoryUtil.memAddress(bytes);
        STRINGS.put(Integer.valueOf(pname), bytes);
        STRING_ADDRESSES.put(Integer.valueOf(pname), Long.valueOf(address));
        return address;
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

    /**
     * Answers the shader and program status queries the game validates, writing into the caller's buffer.
     * <p>
     * The same family as {@link #glGetIntegerv}: the caller reads the buffer, and the value decides whether the game
     * treats a shader as usable. Vanilla's {@code RenderGlobal.makeEntityOutlineShader} and {@code ShaderGroup} check
     * {@code GL_COMPILE_STATUS} and {@code GL_LINK_STATUS} and log or throw when they are false, so answering zero
     * would turn every shader into a failed one. This renderer takes the shaders' source and draws with its own
     * pipeline, so the honest answer to "did this compile" is yes - and {@code GL_INFO_LOG_LENGTH} is 0 because there
     * is no log to read.
     */
    public static void glGetShaderProgramiv(int pname, long params) {
        if (params == 0L) {
            return;
        }
        int value;
        switch (pname) {
            case 0x8B81: // GL_COMPILE_STATUS
            case 0x8B82: // GL_LINK_STATUS
            case 0x8B83: // GL_VALIDATE_STATUS
                value = 1; // GL_TRUE
                break;
            case 0x8B84: // GL_INFO_LOG_LENGTH
            case 0x8B86: // GL_ACTIVE_UNIFORMS
            case 0x8B87: // GL_ACTIVE_UNIFORM_MAX_LENGTH
            case 0x8B89: // GL_ACTIVE_ATTRIBUTES
            case 0x8B8A: // GL_ACTIVE_ATTRIBUTE_MAX_LENGTH
                value = 0;
                break;
            case 0x8B85: // GL_ATTACHED_SHADERS
                value = 1;
                break;
            default:
                value = 0;
                break;
        }
        org.lwjgl.system.MemoryUtil.memPutInt(params, value);
    }

    /** {@return the integer answer for the single-value shader and program queries} */
    public static int glGetShaderProgrami(int pname) {
        switch (pname) {
            case 0x8B81: // GL_COMPILE_STATUS
            case 0x8B82: // GL_LINK_STATUS
            case 0x8B83: // GL_VALIDATE_STATUS
                return 1;
            case 0x8B85: // GL_ATTACHED_SHADERS
                return 1;
            default:
                return 0;
        }
    }

    /**
     * Writes zeros for the single-value "get" functions whose result the caller reads out of its own buffer.
     * <p>
     * The general rule this belongs to, stated by gpu-verifier for this project and by headlessmc for its own GL
     * instrumentation: a replacement that returns the type's zero is fine for a function whose result nobody checks,
     * and wrong for every query the caller validates - and a query that writes into the caller's buffer is worse than
     * wrong, because a no-op leaves <b>uninitialised memory</b> there. The sizes are per function because the number of
     * bytes the caller allocated is not in the signature: writing more than the caller asked for would corrupt the
     * heap, so only the functions whose element count is exactly one are handled here.
     */
    public static void glGetZero(long params, int bytes) {
        if (params == 0L) {
            return;
        }
        for (int i = 0; i < bytes; i++) {
            org.lwjgl.system.MemoryUtil.memPutByte(params + i, (byte) 0);
        }
    }

    /** {@return the element size, in bytes, of a single-value get function, or 0 when it is not one} */
    public static int glGetZeroSize(String name) {
        switch (name) {
            case "nglGetBooleanv":
            case "nglGetBooleanIndexedv":
                return 1;
            case "nglGetIntegerv":
            case "nglGetIntegeri_v":
            case "nglGetTexLevelParameteriv":
            case "nglGetTexParameteriv":
            case "nglGetTexParameterIiv":
            case "nglGetTexParameterIuiv":
            case "nglGetBufferParameteriv":
            case "nglGetBufferParameteri64v":
            case "nglGetVertexAttribiv":
            case "nglGetVertexAttribIiv":
            case "nglGetVertexAttribIuiv":
            case "nglGetQueryObjectiv":
            case "nglGetQueryObjectuiv":
            case "nglGetQueryObjecti64v":
            case "nglGetQueryObjectui64v":
            case "nglGetSynciv":
            case "nglGetFramebufferAttachmentParameteriv":
            case "nglGetRenderbufferParameteriv":
            case "nglGetSamplerParameteriv":
            case "nglGetSamplerParameterIiv":
            case "nglGetSamplerParameterIuiv":
            case "nglGetMultisamplefv":
            case "nglGetShaderiv":
            case "nglGetProgramiv":
                return 4;
            case "nglGetInteger64v":
            case "nglGetInteger64i_v":
            case "nglGetBufferParameteri64v64":
                return 8;
            case "nglGetFloatv":
            case "nglGetTexParameterfv":
            case "nglGetBufferParameterfv":
            case "nglGetVertexAttribfv":
            case "nglGetSamplerParameterfv":
            case "nglGetFramebufferAttachmentParameterfv":
            case "nglGetRenderbufferParameterfv":
                return 4;
            case "nglGetDoublev":
            case "nglGetVertexAttribdv":
                return 8;
            default:
                return 0;
        }
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
