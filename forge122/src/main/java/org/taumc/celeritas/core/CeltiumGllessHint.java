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

    /**
     * Writes {@code count} fresh non-zero names into the caller's buffer, for the array forms of the generators
     * ({@code glGenTextures(int n, IntBuffer)}, {@code glGenBuffers}, {@code glGenFramebuffers} and the rest).
     * <p>
     * The array forms differ from the single-id ones in a way that makes them safe to answer here: the count is an
     * argument, so the number of bytes the caller allocated is known from the signature, unlike the query functions
     * where only the caller knows. A no-op would leave the buffer as it was - for LWJGL's own wrappers that is a
     * zeroed stack buffer, so every caller would receive texture, buffer and framebuffer name 0, which is exactly the
     * "no object" answer that makes correct callers fail (measured on {@code glGenLists} in the 268-mod pack).
     */
    public static void glGenNames(int count, long out) {
        if (out == 0L || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            org.lwjgl.system.MemoryUtil.memPutInt(out + (long) i * 4L, glGenName());
        }
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
        // ActiveRenderInfo unprojects through a four-component viewport. The native replacement must use
        // the same live rectangles/bindings as the callback path, not write one zero into that array.
        if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$getIntegers(pname, params)) {
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
            case 0x0B33: // GL_LIST_INDEX - the display list glNewList opened, 0 when none is
                // This is the answer the model capture reads to decide whether a quad belongs to a list. The
                // capture refuses a quad whose list is 0, and a model compiles its display list exactly once
                // (ModelRenderer.render only calls compileDisplayList while `compiled` is false, and the quads
                // only run inside it), so answering 0 here costs that model its only chance to be captured -
                // which is what `captured=0` in the leg reports was.
                value = listIndex;
                break;
            case 0x0B30: // GL_LIST_MODE - GL_COMPILE (0x1300) or GL_COMPILE_AND_EXECUTE (0x1301); 0 when none is open
                value = listIndex == 0 ? 0 : listMode;
                break;
            default:
                value = 0;
                break;
        }
        org.lwjgl.system.MemoryUtil.memPutInt(params, value);
    }

    /**
     * The display list {@code glNewList} opened, or 0 when none is.
     *
     * <p>Tracked here rather than in {@code CeltiumGlStub} because this class is the layer the transformer actually
     * delegates to: the stub's provider carries only the capability query (the function census from the leg shows
     * four {@code glGetIntegerv} and two {@code glGetError} and nothing else), while every other native goes through
     * {@code CeltiumGllessNativeTransformer} and lands in the methods here.
     */
    private static volatile int listIndex;
    /** The mode {@link #listIndex} was opened with; only {@code GL_LIST_MODE} reads it. */
    private static volatile int listMode;

    /** {@return the list being compiled, for the capture that has to file a quad under it}. */
    public static int listIndex() {
        return listIndex;
    }

    /**
     * Opens a display list, as {@code glNewList(int list, int mode)} does.
     *
     * <p>A name of zero is not a list - the spec makes {@code glNewList(0)} GL_INVALID_VALUE - and recording under it
     * would file quads where no {@code glCallList} can reach them.
     */
    public static void glNewList(int list, int mode) {
        noteDisplayListCall();
        listIndex = list;
        listMode = mode;
    }

    /** Closes the open display list, as {@code glEndList()} does. */
    public static void glEndList() {
        noteDisplayListCall();
        END_LIST_CALLS.incrementAndGet();
        listIndex = 0;
        listMode = 0;
    }

    /**
     * Arrival counts for the three display-list calls.
     *
     * <p>The stub's {@code CELTIUM_FN_CENSUS} cannot answer whether these reach the transformer: it counts the
     * stub's own trampoline, which by design carries only the capability query (four {@code glGetIntegerv} and two
     * {@code glGetError} on every leg so far), while the display-list calls travel the transformer's path. Without
     * a counter here a silent run cannot be told apart from a transformer that never saw the class - and that is
     * exactly the open question, because a coremod's transformer only applies to classes loaded after it is
     * registered, and {@code org.lwjgl.opengl.GL11} is loaded very early.
     */
    private static final java.util.concurrent.atomic.AtomicLong NEW_LIST_CALLS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong END_LIST_CALLS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong CALL_LIST_CALLS =
            new java.util.concurrent.atomic.AtomicLong();

    /** {@return the display-list arrival counts, one line, for the stub's exit hook to print}. */
    public static String displayListCensus() {
        return "glNewList=" + NEW_LIST_CALLS.get() + " glEndList=" + END_LIST_CALLS.get()
                + " glCallList=" + CALL_LIST_CALLS.get() + " listIndex=" + listIndex
                + " lastCalled=" + lastCalledList;
    }

    /** {@return whether any display-list call reached the transformer}. */
    public static boolean sawDisplayListCall() {
        return NEW_LIST_CALLS.get() > 0L || END_LIST_CALLS.get() > 0L || CALL_LIST_CALLS.get() > 0L;
    }

    /** {@return whether a display list is currently open}. */
    public static boolean isCompilingList() {
        return listIndex != 0;
    }

    /**
     * Executes a display list, as {@code glCallList(int list)} does.
     *
     * <p>Recorded rather than drawn here: the geometry of a compiled list was captured while it was compiled
     * ({@code VulkanModelQuadCapture} files a quad under {@link #listIndex}), so what a replay has to do is hand
     * that captured geometry to the renderer again. The name is kept so the renderer can ask which list is being
     * drawn, and {@link #lastCalledList} is what the capture reads for that.
     */
    public static void glCallList(int list) {
        noteDisplayListCall();
        CALL_LIST_CALLS.incrementAndGet();
        lastCalledList = list;
    }

    /** Registers the exit line once, on the first arrival. A run that never reaches these calls stays silent. */
    private static void noteDisplayListCall() {
        if (hooked) {
            return;
        }
        synchronized (CeltiumGllessHint.class) {
            if (hooked) {
                return;
            }
            hooked = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // FileDescriptor.out, not System.out: Forge routes System.out into Log4j and Log4j's own shutdown
                // hook stops the appenders, so a line printed that way appears only when this hook wins the race.
                // Measured on the stub's census: one run in three. FileDescriptor.out is the file the process was
                // started with and stays writable through teardown.
                try (java.io.PrintStream out = new java.io.PrintStream(
                        new java.io.FileOutputStream(java.io.FileDescriptor.out), false)) {
                    out.println("CELTIUM_HINT_DISPLAY_LISTS " + displayListCensus());
                } catch (Throwable ignored) {
                    // A census that cannot be printed must not take the process down on its way out.
                }
            }, "celtium-hint-census"));
        }
    }

    private static volatile boolean hooked;

    /** The last list {@code glCallList} named, 0 before any call. */
    private static volatile int lastCalledList;

    /** {@return the list {@code glCallList} last named}. */
    public static int lastCalledList() {
        return lastCalledList;
    }

    /** Keeps the viewport query and the draw grouping key consistent after resize and offscreen passes. */
    public static void glViewport(int x, int y, int width, int height) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$viewport(x, y, width, height);
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend != null) {
            backend.viewport(x, y, width, height);
        }
    }

    /** A raw GL clear is also a draw-order boundary, even when no GlStateManager wrapper called it. */
    public static void glClear(int mask) {
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend != null) {
            backend.flushBeforeClear(mask);
        }
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$clear(mask);
    }

    public static void glClearColor(float red, float green, float blue, float alpha) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$clearColor(red, green, blue, alpha);
    }

    public static void glClearDepth(double depth) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$clearDepth(depth);
    }

    /** Returns the live mirrored state with the component count implied by pname, not one scalar zero. */
    public static void glGetFloatv(int pname, long params) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$getFloats(pname, params, false);
    }

    /** Double-precision output follows the same state/query contract as the float entry point. */
    public static void glGetDoublev(int pname, long params) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$getFloats(pname, params, true);
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
    /** Fence tokens for the GL-less path; 0 means "no object" to every caller, as with the names. */
    private static final java.util.concurrent.atomic.AtomicLong NEXT_FENCE =
            new java.util.concurrent.atomic.AtomicLong(1);

    /**
     * Returns a fence token for {@code glFenceSync}, which cannot exist without a GL context.
     * <p>
     * Measured 2026-10-15 (leg {@code gv-cohort-fix129}, the first run past the splash and the atlas): the client
     * reached the world and then died in {@code Minecraft.postRender} with
     * {@code RuntimeException: Failed to create fence object} from
     * {@code RenderAheadManager.endFrame(RenderAheadManager.java:41)}, because the generic native fallback answers the
     * type's zero and that class treats zero as failure. The renderer has no GL fences to hand out, so this returns a
     * token that cannot be read as failure.
     * <p>
     * The serialisation that {@code RenderAheadManager} wants is not lost with it. Its fence exists to stop the CPU
     * running ahead of the GPU and to keep a persistently-mapped staging buffer from being overwritten before the
     * frame using it has finished. Under this renderer that bound is the frame-slot fence:
     * {@code VulkanTerrainGpuBackend} acquires a slot per frame and, when the slot's previous submission is still in
     * flight, waits on that slot's own {@code vkWaitForFences} before it may reset and re-record it (the slot-reuse
     * wait, "OPT-08's retirement rule"), with a fixed number of slots. So at most {@code FRAME_SLOTS} submissions can
     * be in flight, which is the same bound the GL fence pair enforced.
     */
    public static long glFenceSyncId(int condition, int flags) {
        return NEXT_FENCE.getAndIncrement();
    }

    /**
     * Answers {@code glClientWaitSync} with {@code GL_ALREADY_SIGNALED}.
     * <p>
     * There is no GL fence to wait on, and waiting on a token that will never signal would hang the client thread. The
     * wait the caller wants has already happened on the renderer's side - see {@link #glFenceSyncId} for why the
     * frame-slot fence is that wait - so the honest answer is that this fence is already satisfied.
     */
    public static int glClientWaitSyncStatus(long sync, int flags, long timeout) {
        return 0x911A; // GL_ALREADY_SIGNALED
    }

    /**
     * Answers {@code glGetTexLevelParameteriv} for the two queries a caller divides by.
     * <p>
     * Measured 2026-10-15 (leg {@code gv-cohort-fix152}, the first run whose world load returned): the client got past
     * the join and died with {@code ArithmeticException: / by zero} at
     * {@code net.minecraftforge.client.CloudRenderer.render(CloudRenderer.java:326)} - {@code offU = offU % texW} -
     * reached through {@code FMLClientHandler.renderClouds} and {@code RenderGlobal.func_180447_b}. Forge fills those
     * two fields from {@code glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH/HEIGHT)} (CloudRenderer.java
     * 479-480), and the generic zero-writer answered zero, which is not a usable divisor.
     * <p>
     * The value is Forge's own: the same class defines {@code PX_SIZE = 1 / 256F} and its cloud texture is 256 square,
     * so 256 is what that query is asked about rather than a guess. Everything else keeps the zero answer, because for
     * the rest of the queries a caller reads a number it does not divide by, and inventing values for those would be
     * the kind of lying the stub's comments elsewhere refuse.
     */
    /**
     * {@return the answer the int-returning form of the texture level query must give}.
     * <p>
     * LWJGL's {@code glGetTexLevelParameteri} returns the value directly and goes through its own native, so the
     * buffer-writing fix below never saw it. Measured 2026-10-15 (leg gv-cohort-lm1): the light map mirror was never
     * established - {@code lightmapSuppliedOnDemand=0} with {@code lightmapSupplyFailure=[none]} - because
     * {@code textureSize()} asked this way, got zero for both dimensions, and skipped the upload silently. The
     * lightmapped batches were then refused 553 times in a run that presented 563 frames.
     * <p>
     * The value is the same 256 the buffer form answers, for the same reason: the light map and Forge's cloud
     * texture are 256 square, and the caller divides by these numbers rather than merely reading them.
     */
    public static int glGetTexLevelParameteri(int target, int level, int pname) {
        return (pname == 0x1000 || pname == 0x1001) ? 256 : 0; // GL_TEXTURE_WIDTH / GL_TEXTURE_HEIGHT
    }
    public static void glGetTexLevelParameteriv(int target, int level, int pname, long params) {
        int value = (pname == 0x1000 || pname == 0x1001) ? 256 : 0; // GL_TEXTURE_WIDTH / GL_TEXTURE_HEIGHT
        if (params == 0L) {
            return;
        }
        org.lwjgl.system.MemoryUtil.memPutInt(params, value);
    }

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
