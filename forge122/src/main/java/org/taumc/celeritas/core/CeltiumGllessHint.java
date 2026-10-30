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
    /** {@return whether this host carries the SDL3 LWJGL binding}, which only the 0.7 generation does. */
    private static boolean sdl3Present() {
        try {
            Class.forName("org.lwjgl.sdl.SDL", false, CeltiumGllessHint.class.getClassLoader());
            return true;
        } catch (Throwable absent) {
            return false;
        }
    }

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
        return glGenNames(1);
    }

    /**
     * {@return the first of {@code range} contiguous names}, from the one window the stub owns.
     *
     * <p>The names the transformer hands out and the names the stub hands out have to come from the same counter: they
     * are keys into the same mirrors, and two windows both starting at 1 means two different resources can share a name.
     * Before provider installation, reserve from the same native identity allocator to avoid an early-name collision.
     */
    public static int glGenNames(int range) {
        int reserved = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$reserveNames(range);
        return reserved >= 0 ? reserved : org.soutalab.celtium.vulkan.RenderObjectIds.reserve(range);
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
        nativeNewList(list, mode);
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.compatibilityNewList(list, mode);
    }

    /** Native vanilla compilation tracks its own geometry; it does not use the Mod GL compiler. */
    public static void nativeNewList(int list, int mode) {
        noteDisplayListCall();
        NEW_LIST_CALLS.incrementAndGet();
        listIndex = list;
        listMode = mode;
        // Published to the stub as well, because that is where the model capture reads the open list from
        // (CeltiumGlStub.compilingList). This class is the layer the transformer delegates to, so without this the
        // stub's field stays 0 and every quad is filed under no list at all - measured on leg gv-cohort-dl9.
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$noteListOpened(list, mode);
    }

    /** Closes the open display list, as {@code glEndList()} does. */
    public static void glEndList() {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.compatibilityEndList();
        nativeEndList();
    }

    public static void nativeEndList() {
        noteDisplayListCall();
        END_LIST_CALLS.incrementAndGet();
        listIndex = 0;
        listMode = 0;
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$noteListClosed();
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
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.compatibilityCallList(list);
    }

    /**
     * The texture-env writes, routed to the stub's per-unit state. {@code RenderLivingBase.setBrightness} writes the
     * hurt overlay through {@code GlStateManager.glTexEnv*}, which under lwjglx delegates to the real
     * {@code org.lwjgl.opengl.GL11} natives - the ones this transformer replaces. Without these the write dies in
     * the replaced body: measured on legs {@code plain-hurt3}/{@code plain-hurt4}, where the hurt zombie rendered
     * untinted and the stub recorded zero COMBINE writes.
     */
    public static void glTexEnvi(int target, int pname, int param) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glTexEnvi(target, pname, param);
    }

    /** {@code nglTexEnvf}: the float form of the same write. */
    public static void glTexEnvf(int target, int pname, float param) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glTexEnvf(target, pname, param);
    }

    /** {@code nglTexEnviv}/{@code nglTexEnvfv}: the pointer forms of the same write. */
    public static void glTexEnvPtr(int target, int pname, long values) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glTexEnvPtr(target, pname, values);
    }

    /** {@code nglGetTexEnvi(II)I}: the int-returning form of the same read. */
    public static int glGetTexEnvI(int target, int pname) {
        return org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glGetTexEnvI(target, pname);
    }

    /** {@code nglGetTexEnvi}/{@code nglGetTexEnviv}: the int read-back, through the out pointer. */
    public static void glGetTexEnvInt(int target, int pname, long out) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glGetTexEnvInt(target, pname, out);
    }

    /** {@code nglGetTexEnvfv}: the float read-back, four floats through the out pointer. */
    public static void glGetTexEnvFloats(int target, int pname, long out) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glGetTexEnvFloats(target, pname, out);
    }

    /**
     * Calls of each native the transformer rewrote, when {@code -Dceltium.glless.census=true} made it count them.
     *
     * <p>The question this answers is "which vanilla or mod drawing still goes nowhere", and no existing counter could:
     * the stub's no-op census only sees the calls that reach the stub's trampoline, and under GL-less the transformer
     * rewrites the natives first (measured on leg gv-cohort-cloud1: the log carries no CELTIUM_NOOP_CENSUS line at all).
     * The cloud and sky paths are the measured example - Forge's CloudRenderer draws its VBO through
     * {@code VertexBuffer.drawArrays -> glDrawArrays}, and the sky dome, the stars and the horizon plane take the same
     * route - so this names them and their neighbours instead of leaving the hole to be found one call site at a time.
     */
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> REWRITTEN_CALLS =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();

    /** Records one call of a native the transformer rewrote. Emitted only when the census switch is on. */
    public static void noteRewritten(String name) {
        java.util.concurrent.atomic.AtomicLong count = REWRITTEN_CALLS.get(name);
        if (count == null) {
            count = REWRITTEN_CALLS.computeIfAbsent(name, key -> new java.util.concurrent.atomic.AtomicLong());
        }
        count.incrementAndGet();
        noteDisplayListCall();
        // The matrix natives that are NOT routed are invisible in the stub: they reach emitDefaultReturn and the mirror
        // never sees them. The stride-48 draw's refusal named "the mirrored projection is the identity", so the call
        // that should have set it has to be named. Three lines, only under the census, only for matrix-family names.
        if ((name.contains("Matrix") || name.contains("Frustum") || name.contains("Ortho"))
                && MATRIX_NATIVE_NAMES.size() < 8 && MATRIX_NATIVE_NAMES.add(name)) {
            StackTraceElement[] trace = new Throwable().getStackTrace();
            StringBuilder callers = new StringBuilder();
            for (int i = 2; i < Math.min(trace.length, 10); i++) {
                if (i > 2) callers.append(" <- ");
                callers.append(trace[i].getClassName()).append('.').append(trace[i].getMethodName());
            }
            System.out.println("[Celtium] CELTIUM_GLLESS_MATRIX_NATIVE name=" + name + " callers=[" + callers + "]");
        }
    }

    /** First calls of each matrix-family native, for the bounded {@link #noteRewritten} report. */
    private static final java.util.Set<String> MATRIX_NATIVE_NAMES =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    /** {@return the rewritten-native census, largest first, for the exit line}. */
    public static String rewriteCensus() {
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> entries =
                new java.util.ArrayList<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>>(
                        REWRITTEN_CALLS.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < entries.size() && i < 40; i++) {
            if (i > 0) {
                line.append(' ');
            }
            line.append(entries.get(i).getKey()).append('=').append(entries.get(i).getValue().get());
        }
        return "names=" + entries.size() + " [" + line + "]";
    }

    /** Counts per unrouted native id; sized to the transformer's tracking bound. */
    private static final int[] UNROUTED_COUNTS = new int[4096];
    private static final java.util.concurrent.atomic.AtomicInteger UNROUTED_TOTAL =
            new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean surfaceReported;

    /** Calls after which the surface report prints once during the run, so a log always carries it. */
    static final int SURFACE_REPORT_CALLS = 20000;

    /** {@return the per-id call counts of natives that no route answered}, for the surface report. */
    public static int[] unroutedCounts() {
        return UNROUTED_COUNTS;
    }

    /**
     * Notes one call to a native the transformer's zero-writer answered, so the renderer's silent answers are counted
     * per name rather than inferred from a picture that is missing something.
     */
    public static void noteUnrouted(int id) {
        if (id < 0 || id >= UNROUTED_COUNTS.length) {
            return;
        }
        UNROUTED_COUNTS[id]++;
        UNROUTED_TOTAL.incrementAndGet();
        if (!surfaceReported && UNROUTED_TOTAL.get() >= SURFACE_REPORT_CALLS) {
            surfaceReported = true;
            org.taumc.celeritas.core.CeltiumGllessNativeTransformer.printSurfaceReport("in-run");
        }
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
                    if (!REWRITTEN_CALLS.isEmpty()) {
                        out.println("CELTIUM_TRANSFORM_CENSUS " + rewriteCensus());
                    }
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

    /**
     * Records a texture parameter, as {@code glTexParameteri(target, pname, value)} does.
     *
     * <p>Vanilla sets the filters this way ({@code TextureUtil}'s own {@code GlStateManager.glTexParameteri} calls), and
     * the registry's sampler asks the stub for what was recorded before it falls back to inferring the filter from the
     * upload's shape. Nothing was recorded under GL-less - the calls were rewritten to a no-op - so a magnified item
     * texture was sampled LINEAR and the item looked blurred. The stub resolves which texture the parameter belongs to.
     */
    public static void glTexParameteri(int target, int pname, int value) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$texParameteri(target, pname, value);
    }

    /**
     * Serves a texture copy, as {@code glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height)} does.
     *
     * <p>The main menu's panorama is built this way: {@code GuiMainMenu.renderSkybox} unbinds the framebuffer, draws its
     * cube faces, then copies the result into a 256x256 texture five times to blur it. The stub's copy case is what
     * turns that into a mirror patch through the frame target, and the transformer used to drop the call before it
     * arrived - the panorama texture was then rejected as having no game pixels.
     */
    public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width,
            int height) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$copyTexSubImage2D(target, level, xoffset, yoffset, x, y,
                width, height);
    }

    /** Serves a copy, as {@code glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border)} does. */
    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$copyTexImage2D(target, level, internalFormat, x, y, width,
                height);
    }

    /**
     * The transformer's form: it hands every one of the GL entry point's eight arguments over, so this is the overload
     * its {@code (IIIIIIII)V} call resolves to. Only the 7-argument one existed, and the first mod to copy through
     * {@code GL11.glCopyTexImage2D} died with NoSuchMethodError (The Betweenlands' depth-buffer blit, in ATM3). The
     * border must be 0 in GL; nothing else is defined, so it carries no information here.
     */
    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height,
            int border) {
        glCopyTexImage2D(target, level, internalFormat, x, y, width, height);
    }

    /**
     * The client-array/VBO draw family, as {@code VertexBuffer} issues it.
     *
     * <p>{@code bindBuffer -> glEnableClientState/glVertexPointer -> glDrawArrays} is how the sky dome, the stars, the
     * horizon plane and Forge's {@code CloudRenderer} draw; the stub decodes that call shape, and the transformer used
     * to drop the calls before it. The cloud leg's census names the size of the hole: {@code glDrawArrays=6220},
     * {@code nglVertexPointer=5332}, {@code glEnableClientState=7117}, {@code glBindBuffer=12474}.
     */
    public static void glDrawArrays(int mode, int first, int count) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$drawArrays(mode, first, count);
    }

    /** Binds an array buffer, as {@code glBindBuffer(target, name)} does. */
    public static void glBindBuffer(int target, int name) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$bindBuffer(target, name);
    }

    /**
     * Binds a texture, as {@code glBindTexture(target, name)} does. The call used to be dropped whole by the no-op
     * trampoline, so a mod's raw {@code GL11.glBindTexture} left no record anywhere: the upload that followed was
     * filed under whatever texture the game last bound, and the name was later refused "no mirror".
     */
    public static void glBindTexture(int target, int texture) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glBindTexture(target, texture);
    }

    /** Selects the active texture unit, as {@code glActiveTexture(unit)} does. */
    public static void glActiveTexture(int unit) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glActiveTexture(unit);
    }

    /** Deletes texture names, as {@code glDeleteTextures(count, namesPointer)} does. */
    public static void glDeleteTextures(int count, long names) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glDeleteTextures(count, names);
    }

    /** A deleted bound draw FBO becomes the default framebuffer before the stub forgets it. */
    public static void glDeleteFramebuffers(int count, long names) {
        if (names == 0L) return;
        for (int i = 0; i < count; i++) {
            int name = org.lwjgl.system.MemoryUtil.memGetInt(names + (long) i * 4L);
            if (name != 0 && name == rawDrawFramebuffer) {
                if (vanillaFramebufferDepth == 0) reportRawFramebuffer(0);
                rawDrawFramebuffer = 0;
                break;
            }
        }
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$glDeleteFramebuffers(count, names);
    }

    /** Enables a client array, as {@code glEnableClientState(capability)} does. */
    public static void glEnableClientState(int capability) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$clientState(capability, true);
    }

    /** Disables a client array, as {@code glDisableClientState(capability)} does. */
    public static void glDisableClientState(int capability) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$clientState(capability, false);
    }

    /** Sets the client vertex-array pointer, as {@code glVertexPointer(size, type, stride, pointer)} does. */
    public static void glVertexPointer(int size, int type, int stride, long pointer) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$vertexPointer(size, type, stride, pointer);
    }

    /** Sets the split texture-coordinate pointer, as {@code glTexCoordPointer(size, type, stride, pointer)} does. */
    public static void glTexCoordPointer(int size, int type, int stride, long pointer) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$texCoordPointer(size, type, stride, pointer);
    }

    /** Sets the split colour pointer, as {@code glColorPointer(size, type, stride, pointer)} does. */
    public static void glColorPointer(int size, int type, int stride, long pointer) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$colorPointer(size, type, stride, pointer);
    }

    /** Sets the split normal pointer, as {@code glNormalPointer(type, stride, pointer)} does. */
    public static void glNormalPointer(int type, int stride, long pointer) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$normalPointer(type, stride, pointer);
    }

    /**
     * The texture upload family: every texture's pixels arrive through these two, and under GL-less they were dropped.
     * The measured case is the 268-mod E2E pack's block atlas (GL 353): the stub captured no upload, the mirror never
     * became full, the terrain pass refused its batches, and the frame's lower half was drawn by nobody.
     */
    public static void glTexImage2D(int target, int level, int internal, int width, int height, int border, int format,
            int type, long pixels) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$texImage2D(target, level, internal, width, height, border,
                format, type, pixels);
    }

    /** Sub-image writes, the other half of the same family. */
    public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height,
            int format, int type, long pixels) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$texSubImage2D(target, level, xOffset, yOffset, width,
                height, format, type, pixels);
    }

    /**
     * A raw framebuffer's colour attachment. The vanilla path is already linked by {@code FramebufferMixin}; a mod
     * that creates its own framebuffer with raw GL has no such object, so without this route nothing knows which image
     * its draws went into and every batch sampling it is refused - the 268-mod E2E pack measures 62 such batches per
     * run for texture 506.
     */
    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$framebufferTexture2D(target, attachment, textarget,
                texture, level);
        if (vanillaFramebufferDepth == 0 && target != GL_READ_FRAMEBUFFER) {
            reportRawFramebuffer(rawDrawFramebuffer);
        }
    }

    /**
     * Raised by FramebufferMixin while a vanilla {@code Framebuffer} method runs: those already report their binds to
     * the renderer with the object's own identity, and the raw bind they make inside must not report a second one.
     */
    public static int vanillaFramebufferDepth;

    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;

    /** The draw framebuffer the last raw bind named; 0 is the default framebuffer. */
    private static int rawDrawFramebuffer;

    /**
     * Tells the renderer which target a raw framebuffer bind makes current, and which colour texture stands for it.
     * <p>
     * A mod framebuffer whose class overrides the vanilla bind with raw GL - Xaero's Minimap's ImprovedFramebuffer -
     * reached only the stub's binding record, so the renderer kept drawing into the game's image: the minimap's clear
     * blacked out the world, its tile draws landed on the screen at the framebuffer's viewport (the "wrong position"
     * the user reported), and the HUD quad that samples its texture found nothing.
     */
    private static void reportRawFramebuffer(int framebuffer) {
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (!(current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend backend)) {
            return;
        }
        if (framebuffer == 0) {
            backend.bindFramebuffer(0L, false);
            return;
        }
        long identity = org.soutalab.celtium.vulkan.RenderTargetRegistry.rawFramebufferIdentity(framebuffer);
        backend.bindFramebuffer(identity, false);
        int color = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$framebufferColorTexture(framebuffer);
        int[] extent = color == 0 ? null : org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$textureExtent(color);
        if (extent != null && extent[0] > 0 && extent[1] > 0) {
            backend.bindFramebufferColorTexture(identity, color, extent[0], extent[1]);
        }
    }

    /** Which framebuffer is bound; the attachment record above is filed under it. */
    public static void glBindFramebuffer(int target, int framebuffer) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$bindFramebuffer(target, framebuffer);
        if (target != GL_READ_FRAMEBUFFER) {
            rawDrawFramebuffer = framebuffer;
            if (vanillaFramebufferDepth == 0) {
                reportRawFramebuffer(framebuffer);
            }
        }
    }

    /**
     * A framebuffer-pixel read, as {@code glReadPixels(x, y, width, height, format, type, pixels)} does.
     * <p>
     * Without the route every caller's buffer stayed zeroed - a no-op that reads as transparent black rather than a
     * failure, which is what ScreenShotHelper's screenshots and the fixture's format-2 readback measured.
     */
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$readPixels(x, y, width, height, format, type, pixels);
    }

    /**
     * {@code glGetTexImage(target, level, format, type, pixels)}, the read the game's own screenshot uses.
     * <p>
     * Measured: ScreenShotHelper takes this branch whenever framebuffers are enabled, so a GL-less run's screenshot
     * never touched the {@code glReadPixels} route and its buffer stayed zeroed - a black PNG.
     */
    public static void glGetTexImage(int target, int level, int format, int type, long pixels) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$getTexImage(target, level, format, type, pixels);
    }

    /**
     * The raw matrix call a vanilla GlStateManager method is about to make, set by its mixin hook (which has already
     * mirrored it); 0 when none. The raw route skips exactly one call of that kind and clears it, so nothing can
     * accumulate - a depth counter here grew without bound because some hooked methods never reached their RETURN.
     */
    public static int vanillaMatrixCall;
    public static final int MATRIX_PUSH = 1, MATRIX_POP = 2, MATRIX_MODE = 3, MATRIX_IDENTITY = 4, MATRIX_ORTHO = 5,
            MATRIX_TRANSLATE = 6, MATRIX_SCALE = 7, MATRIX_ROTATE = 8;

    /**
     * {@return the backend to mirror a raw GL11 matrix call into}, or null when the call is the one a vanilla
     * GlStateManager method just mirrored itself, or there is no backend.
     * <p>
     * Mods that call {@code GL11.glPushMatrix/glPopMatrix/glRotatef} themselves - Xaero's Minimap pushes and pops the
     * projection that way around its framebuffer pass - reached no mirror at all: the natives were no-ops, so the HUD
     * after the minimap kept the minimap's projection and drew in the wrong place.
     */
    private static org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend rawMatrix(int kind) {
        // The one-shot flag, read cheaply. Reading the caller frame here instead was measured at 29.2 ms of warm frame
        // against 4.3 ms with the flag: glPushMatrix/glPopMatrix/glTranslate run per model and per entity, and a stack
        // walk on each of them costs more than the whole frame. The flag's stale case (a GlStateManager method whose
        // native call never arrives) is real but rare, and it can be cleared by the mixin's own return hook rather than
        // paid for on every matrix call.
        if (vanillaMatrixCall != 0) {
            boolean vanilla = vanillaMatrixCall == kind;
            vanillaMatrixCall = 0;
            if (vanilla) {
                return null;
            }
        }
        return org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
    }

    public static void glPushMatrix() {
        var backend = rawMatrix(MATRIX_PUSH);
        if (backend != null) backend.pushMatrix();
    }

    public static void glPopMatrix() {
        var backend = rawMatrix(MATRIX_POP);
        if (backend != null) backend.popMatrix();
    }

    private static final boolean HINT_MATRIX_TRACE = Boolean.getBoolean("celtium.vulkan.matrixTrace");
    private static int hintMultiplyDumps;
    /**
     * {@code GL_MATRIX_MODE}, kept here as well as in the mirror.
     * <p>
     * GL's matrix mode is state of the context, not of this renderer's mirror object, and the mirror is per backend
     * instance: a mod that sets PROJECTION and then multiplies across a point where the backend was replaced (the
     * mod-reload path does exactly that) had its multiply land on the new backend's default MODELVIEW while the mode
     * switch stayed on the old one. The measured symptom was a projection multiply arriving with m00=0.03 (an ortho)
     * and the backend recording mode=MODELVIEW. Re-applying this value before a matrix operation is a few counter
     * increments and makes the stack independent of instance lifetime.
     */
    private static volatile int lastMatrixMode = 5888;

    /** Mirrors {@link CeltiumGllessNativeTransformer#MOD_MATRIX_ROUTES}; see there for why it is off by default. */
    private static final boolean MOD_MATRIX_ROUTES = Boolean.getBoolean("celtium.glless.modMatrixRoutes");
    private static int hintMatrixDumps;

    public static void glMatrixMode(int mode) {
        lastMatrixMode = mode;
        var backend = rawMatrix(MATRIX_MODE);
        if (backend != null) backend.matrixMode(mode);
        if (HINT_MATRIX_TRACE && hintMatrixDumps < 8) {
            // Whether the route is reached at all: the backend-side trace showed no projection multiply, and this says
            // whether the mode switch that should precede it ever arrived.
            hintMatrixDumps++;
            StringBuilder callers = new StringBuilder();
            StackTraceElement[] trace = new Throwable().getStackTrace();
            for (int i = 2; i < Math.min(trace.length, 9); i++) {
                if (i > 2) callers.append(" <- ");
                callers.append(trace[i].getClassName()).append('.').append(trace[i].getMethodName());
                if (callers.length() > 170) break;
            }
            System.out.println("[Celtium] CELTIUM_HINT_MATRIXMODE mode=" + mode + " applied=" + (backend != null)
                    + " callers=[" + callers + "]");
        }
    }

    /**
     * The raw {@code glMultMatrixf} native, from mod code.
     * <p>
     * Not routed for a long time, and the note above still applies to vanilla: the camera reaches glMultMatrix through
     * {@code Project.gluPerspective}, which {@code GluProjectionMixin} already mirrors, and routing it too applied the
     * projection twice. But the same refusal left every mod that builds its own matrices without a mirror:
     * cam72cam's {@code RenderContext.multMatrix} (Immersive Railroading's item models) could not set a projection, and
     * its stride-48 draw was refused with "the mirrored projection is the identity at draw time". The caller decides:
     * a call from {@code net.minecraft*} is skipped, anything else is applied to the current matrix stack.
     */
    public static void glMultMatrixf(long pointer) {
        if (!MOD_MATRIX_ROUTES || pointer == 0L || !modMatrixCaller()) {
            return;
        }
        if (HINT_MATRIX_TRACE && hintMultiplyDumps < 12) {
            hintMultiplyDumps++;
            StringBuilder callers = new StringBuilder();
            StackTraceElement[] trace = new Throwable().getStackTrace();
            for (int i = 2; i < Math.min(trace.length, 9); i++) {
                if (i > 2) callers.append(" <- ");
                callers.append(trace[i].getClassName()).append('.').append(trace[i].getMethodName());
                if (callers.length() > 150) break;
            }
            System.out.println("[Celtium] CELTIUM_HINT_MULTMATRIX m00="
                    + org.lwjgl.system.MemoryUtil.memGetFloat(pointer) + " m11="
                    + org.lwjgl.system.MemoryUtil.memGetFloat(pointer + 20L) + " callers=[" + callers + "]");
        }
        // No one-shot flag here: the stack already says whether this call is vanilla's (whose mirror ran) or a mod's.
        // The flag was a second gate, and a stale set - a GlStateManager method whose native call never arrived - then
        // skipped the next mod multiply, which is how the projection multiply went missing while its matrix-mode switch
        // was applied.
        vanillaMatrixCall = 0;
        var backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend != null) {
            backend.matrixMode(lastMatrixMode);
            backend.multiplyMatrix(org.lwjgl.system.MemoryUtil.memFloatBuffer(pointer, 16));
        }
    }

    /** The {@code glMultMatrixd} native, narrowed to the float route. */
    public static void glMultMatrixd(long pointer) {
        if (!MOD_MATRIX_ROUTES || pointer == 0L || !modMatrixCaller()) {
            return;
        }
        vanillaMatrixCall = 0;
        var backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend != null) {
            backend.matrixMode(lastMatrixMode);
            java.nio.FloatBuffer buffer = org.lwjgl.system.MemoryUtil.memAllocFloat(16);
            try {
                for (int i = 0; i < 16; i++) {
                    buffer.put(i, (float) org.lwjgl.system.MemoryUtil.memGetDouble(pointer + (long) i * 8L));
                }
                backend.multiplyMatrix(buffer);
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(buffer);
            }
        }
    }

    /**
     * {@return whether the caller of the native is mod code rather than vanilla}, read from the first frame that is
     * neither the JVM, LWJGL, nor this project. The distinction is the whole safety argument of the matrix routes: the
     * vanilla paths are mirrored by mixins and must not be applied twice, while a mod's own matrices have no other
     * mirror at all.
     */
    private static boolean modMatrixCaller() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        for (int i = 2; i < Math.min(trace.length, 8); i++) {
            String type = trace[i].getClassName();
            if (type.startsWith("java.") || type.startsWith("org.lwjgl.") || type.startsWith("org.soutalab.")
                    || type.startsWith("org.taumc.")) {
                continue;
            }
            return !(type.startsWith("net.minecraft.") || type.startsWith("net.minecraftforge."));
        }
        return false;
    }

    public static void glLoadIdentity() {
        var backend = rawMatrix(MATRIX_IDENTITY);
        if (backend != null) backend.loadIdentity();
    }

    public static void glOrtho(double left, double right, double bottom, double top, double near, double far) {
        var backend = rawMatrix(MATRIX_ORTHO);
        if (backend != null) backend.ortho(left, right, bottom, top, near, far);
    }

    public static void glTranslated(double x, double y, double z) {
        var backend = rawMatrix(MATRIX_TRANSLATE);
        if (backend != null) backend.translate(x, y, z);
    }

    public static void glScaled(double x, double y, double z) {
        var backend = rawMatrix(MATRIX_SCALE);
        if (backend != null) backend.scale((float) x, (float) y, (float) z);
    }

    public static void glRotated(double angle, double x, double y, double z) {
        var backend = rawMatrix(MATRIX_ROTATE);
        if (backend != null) backend.rotate((float) angle, (float) x, (float) y, (float) z);
    }

    /**
     * A raw {@code GL11.glColor*} call: the same two destinations {@code GlStateManager.color}'s hook feeds, the font's
     * current colour and the backend's colour mirror.
     * <p>
     * The natives were no-ops, so a Mod that set its colour through GL11 itself drew with whatever colour vanilla last
     * set: GVCLib's exhaust sets {@code glColor4f(1, 1, 1, throttle * 0.03)} and drew opaque - a black and green sheet
     * across the screen where OpenGL draws a faint plume (E2E leg a4-bounds-b, group fmt=9 texture=233). A vanilla
     * {@code GlStateManager.color} that reaches here after its hook only sets the same value again, which is harmless:
     * unlike a matrix operation, a colour does not accumulate.
     */
    public static void glColor4f(float red, float green, float blue, float alpha) {
        org.taumc.celeritas.impl.font.CeltiumFontGlyphCapture.currentColor(red, green, blue, alpha);
        // The legacy immediate capture keeps its own current colour: glBegin geometry carries the colour at vertex
        // time, and the mirror's colour is state the batch path reads later.
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$legacyColor(red, green, blue, alpha);
        var backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend != null) backend.setColor(red, green, blue, alpha);
    }

    // ---- legacy immediate mode, routed by the transformer ----
    public static void glLegacyBegin(int mode) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$legacyBegin(mode);
    }

    public static void glLegacyEnd() {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$legacyEnd();
    }

    public static void glLegacyVertex3f(float x, float y, float z) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$legacyVertex3f(x, y, z);
    }

    public static void glLegacyTexCoord2f(float u, float v) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$legacyTexCoord2f(u, v);
    }

    public static void glLegacyTexCoord2d(double u, double v) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$legacyTexCoord2d(u, v);
    }

    /** {@code glColor4ub}: the bytes are unsigned and map to [0, 1], as GL does. */
    public static void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        glColor4f((red & 0xFF) / 255.0F, (green & 0xFF) / 255.0F, (blue & 0xFF) / 255.0F, (alpha & 0xFF) / 255.0F);
    }

    /** A raw {@code GL11.glDepthMask}, for the same reason as {@link #glColor4f}: canopies and overlays set it raw. */
    public static void glDepthMask(boolean mask) {
        var backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend != null) backend.setDepthMask(mask);
    }

    /** Maps the bound buffer's storage, as {@code glMapBuffer}/{@code glMapBufferRange} do; 0 is GL's NULL. */
    public static long glMapBuffer(int target, boolean range, long offset, long length, int access) {
        return org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$mapBuffer(target, range, offset, length, access);
    }

    /** Unmaps the bound buffer, as {@code glUnmapBuffer} does. */
    public static boolean glUnmapBuffer(int target) {
        return org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$unmapBuffer(target);
    }

    /** Mirrors array-buffer storage, as {@code glBufferData(target, size, data, usage)} does. */
    public static void glBufferData(int target, long size, long data, int usage) {
        org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$bufferData(target, size, data, usage);
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

    /**
     * {@return a uniform location for a NUL-terminated name}: never -1, and the same for the same name, so vanilla's
     * {@code ShaderManager} keeps the uniform. Read straight from the native string; called at shader setup and once
     * per sampler per {@code useShader}.
     */
    public static int glUniformLocation(long name) {
        if (name == 0L) {
            return -1;
        }
        int hash = 0;
        for (long p = name; ; p++) {
            byte b = org.lwjgl.system.MemoryUtil.memGetByte(p);
            if (b == 0) {
                break;
            }
            hash = 31 * hash + b;
        }
        return hash & 0x3FFFFFFF;
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
        if (!sdl3Present()) {
            // Refusing here beats letting the window be created without a client API and dying later in
            // GL.createCapabilities with "There is no OpenGL context current in the current thread", which names
            // neither the version nor this mod. Measured on the 0.6.13 cohort fixture (LWJGL 3.4.1, GLFW, lwjglx).
            String message = "[Celtium] this build requires CleanroomMC 0.7.11-alpha (SDL3, LWJGL 3.4.3); the host has no "
                    + "org.lwjgl.sdl.SDL, so it is the GLFW/lwjglx generation, where a GL-less window cannot exist. "
                    + "Use a 0.7.x instance or a build made for the older generation.";
            org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
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
