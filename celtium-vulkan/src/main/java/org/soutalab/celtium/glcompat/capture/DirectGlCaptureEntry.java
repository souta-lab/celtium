package org.soutalab.celtium.glcompat.capture;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.glcompat.DisplayListManager;
import org.soutalab.celtium.glcompat.DirectGlDispatcher;
import org.soutalab.celtium.glcompat.GLSMAttribStack;
import org.soutalab.celtium.glcompat.GLSMBlend;
import org.soutalab.celtium.glcompat.GLSMBuffers;
import org.soutalab.celtium.glcompat.GLSMDepthStencilAlpha;
import org.soutalab.celtium.glcompat.GLSMDraws;
import org.soutalab.celtium.glcompat.GLSMEnable;
import org.soutalab.celtium.glcompat.GLSMFog;
import org.soutalab.celtium.glcompat.GLSMFramebuffer;
import org.soutalab.celtium.glcompat.GLSMMatrices;
import org.soutalab.celtium.glcompat.GLSMPrograms;
import org.soutalab.celtium.glcompat.GLSMQueries;
import org.soutalab.celtium.glcompat.GLSMRasterizer;
import org.soutalab.celtium.glcompat.GLSMTexEnvGen;
import org.soutalab.celtium.glcompat.GLSMTextures;
import org.soutalab.celtium.glcompat.GLSMVertexInput;
import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.vulkan.BackendCallCounters;
import org.soutalab.celtium.vulkan.GlsmNameLedger;
import org.soutalab.celtium.vulkan.NativeRasterState;
import org.soutalab.celtium.vulkan.PixelUnpackState;
import org.soutalab.celtium.vulkan.lane2.Lane2StateMirror;

/**
 * The capture entrance: the one class the loading plugins install as LWJGL's GL provider
 * (S3-b E4-b, Neon E4 door ruling §3.2).
 *
 * <p>The entrance holds exactly five duties and no state of its own beyond them:
 * <ol>
 * <li>the LWJGL3 ICD surface - {@link #install()} / {@link #installFirstCapabilities()} and the
 * {@link DispatchThreadGate} every dispatch funnels through;</li>
 * <li>the function table absorbed from the old stub - {@link GlFn} rows, the {@code fn()}
 * factory and the {@code R_*}/{@code A_*}/{@code SEM_*} encodings {@link GlFunctionTable}
 * registers in - with thin distribution from the dispatch to the seats of the eight-family
 * table (the {@code GLSM*} converter classes, {@code GLStateManager},
 * {@code DisplayListManager} and the seat A / seat B / HAL records);</li>
 * <li>the thin {@code glGet*} composite router: each pname is answered by the seat that owns
 * the record, never by a bulk zero;</li>
 * <li>the minimal font-window gate ({@link #beginFontWindow()} / {@link #endFontWindow()});</li>
 * <li>the static drawing-entry predicate {@link #isDrawingEntryPointKey(String)} the bytecode
 * transformer consults.</li>
 * </ol>
 *
 * <p>Until E4-b2 this surface lived inside the {@code GlsmFrontend} monolith in
 * {@code vulkan.gl}; the monolith keeps its own copy of the dispatch for its tests until E4-c
 * retires it, but no production caller reaches it any more: the plugins install this class,
 * and the table below is the single registration both sides read.
 */
public final class DirectGlCaptureEntry implements FunctionProvider {

    // Return types (the table's R_* encoding; GlFunctionTable.A_* carries the argument side).
    public static final byte R_VOID = 0;
    public static final byte R_INT = 1;
    public static final byte R_LONG = 2;
    public static final byte R_PTR = 3;

    // Semantics ids - what the dispatch does with the call. The enum keeps the table lines short.
    public static final int SEM_NOOP = 0;            // record only - state the Vulkan side owns or nobody reads
    public static final int SEM_GEN = 1;             // object-name allocation: returns the next id
    public static final int SEM_GET_ERROR = 2;       // the converter's recorded error (GLStateManager)
    public static final int SEM_GET_STRING = 3;      // arg0 pname -> interned ascii
    public static final int SEM_IS_ENABLED = 4;      // arg0 cap -> recorded enable bit
    public static final int SEM_ENABLE = 5;          // arg0 cap -> set enable bit
    public static final int SEM_DISABLE = 6;         // arg0 cap -> clear enable bit
    public static final int SEM_GET_INTEGERV = 7;    // arg0 pname, arg1 out[] -> plausible ints
    public static final int SEM_GET_FLOATV = 8;      // arg0 pname, arg1 out[] -> plausible floats
    public static final int SEM_GET_BOOLEANV = 9;    // arg0 pname, arg1 out[] -> plausible bytes
    public static final int SEM_IS_OBJECT = 10;      // arg0 id -> whether the table generated it
    public static final int SEM_DELETE = 11;         // id list -> retire names (reissue is the same counter-free)
    public static final int SEM_GET_STRINGI = 12;    // pname, index -> extension string
    public static final int SEM_GET_INTEGER64V = 13; // pname, out64[] -> plausible longs
    // GAP-SEM-1 (b): SEM_ROUTE (id 14) removed - zero function-table rows ever targeted it.
    public static final int SEM_ZERO = 15;           // int return: 0 - valid-but-empty answers (uniform loc etc)
    public static final int SEM_FBO_COMPLETE = 16;   // int return: GL_FRAMEBUFFER_COMPLETE
    public static final int SEM_SIGNALED = 17;       // int return: GL_ALREADY_SIGNALED
    public static final int SEM_MAP = 18;            // ptr return: the named buffer's storage
    public static final int SEM_UNMAP = 19;          // byte return: true, buffer contents already live
    public static final int SEM_BUFFER_DATA = 20;    // (target,size,data,usage) -> size bytes of storage
    public static final int SEM_STATUS = 21;         // (obj,pname,out[]) -> successful-answer queries
    public static final int SEM_INFO_LOG = 22;       // (obj,bufSize,len*,log) -> empty string
    public static final int SEM_BIND_BUFFER = 23;    // (target,name) -> binding record for map/data
    public static final int SEM_BIND_TEXTURE = 24;   // (target,name) -> texture binding record
    public static final int SEM_ACTIVE_TEXTURE = 25; // (unit) -> texture unit selector record
    public static final int SEM_TEXIMAGE = 26;       // (target,level,internal,w,h,...) -> size record per bound
    public static final int SEM_VIEWPORT = 27;       // (x,y,w,h) -> viewport record feeding GL_VIEWPORT
    public static final int SEM_FOG_I = 71;
    public static final int SEM_FOG_F = 72;
    public static final int SEM_FOG_FV = 73;
    public static final int SEM_FOG_IV = 74;
    public static final int SEM_TEX_ENV_I = 47;      // (target,pname,param) -> texture-env int, per active unit
    public static final int SEM_TEX_ENV_F = 48;      // (target,pname,param) -> texture-env float
    public static final int SEM_TEX_ENV_FV = 49;     // (target,pname,values) -> texture-env float array
    public static final int SEM_GET_TEX_ENV_I = 50;  // (target,pname,out) -> the recorded int
    public static final int SEM_GET_TEX_ENV_FV = 51; // (target,pname,out) -> the recorded floats
    public static final int SEM_COPY_TEX_IMAGE = 52;    // copy from the bound read framebuffer into a texture level
    public static final int SEM_COPY_TEX_SUB_IMAGE = 53; // the same, into a sub-rectangle
    public static final int SEM_BLIT_FRAMEBUFFER = 78;  // framebuffer-to-framebuffer blit (the depth bit is served)
    public static final int SEM_COPY_IMAGE_SUB_DATA = 79; // (srcName,srcTarget,srcLevel,srcX,srcY,srcZ,dstName,dstTarget,dstLevel,dstX,dstY,dstZ,w,h,d) -> captured level to captured level
    public static final int SEM_TEX_PARAM_I = 54;     // (target,pname,param) -> texture parameter of the bound texture
    public static final int SEM_TEX_PARAM_IV = 55;    // (target,pname,values) -> the same, from an array
    public static final int SEM_TEX_PARAM_I_DSA = 56; // (texture,pname,param) -> the same, texture named directly
    public static final int SEM_TEX_PARAM_IV_DSA = 57; // (texture,pname,values) -> the same, from an array
    public static final int SEM_MULT_MATRIX = 58;     // (matrix) -> multiply the active matrix of the mirror
    public static final int SEM_MATRIX_MODE = 59;     // (mode) -> which stack glMultMatrix* multiplies
    public static final int SEM_BIND_FRAMEBUFFER = 28; // (target,name) -> framebuffer binding record
    public static final int SEM_CLEAR = 29;          // (mask) -> pending clear of the owned frame image
    public static final int SEM_CLEAR_COLOR = 30;    // (r,g,b,a) -> the colour glClear will use
    public static final int SEM_CLEAR_DEPTH = 31;    // (d) -> the depth glClear will use
    public static final int SEM_BUFFER_SUBDATA = 32; // (target,offset,size,data) -> into the recorded storage
    public static final int SEM_LINE_WIDTH = 33;      // (w) -> recorded raster line width
    public static final int SEM_USE_PROGRAM = 34;     // (program) -> recorded current program
    public static final int SEM_NEW_LIST = 35;        // (list,mode) -> the list being compiled
    public static final int SEM_END_LIST = 36;        // () -> compilation finished
    public static final int SEM_CALL_LIST = 37;       // (list) -> execute the recorded list
    public static final int SEM_BIND_VERTEX_ARRAY = 38; // (vao) -> recorded vertex array binding
    public static final int SEM_TEXIMAGE_PIXELS = 39; // (target,level,internal,w,h,border,fmt,type,pixels) -> extent + RGBA8
    public static final int SEM_TEXSUBIMAGE = 40;     // (target,level,x,y,w,h,fmt,type,pixels) -> patch into a captured level
    public static final int SEM_COMPRESSED_TEXIMAGE = 41; // (target,level,internal,w,h,border,size,data) -> extent only
    public static final int SEM_PIXEL_STORE = 42;     // (pname,value) -> the unpack state upload conversion needs
    public static final int SEM_VERTEX_POINTER = 43;  // (size,type,stride,offset) -> how to read the bound array buffer
    public static final int SEM_DRAW_ARRAYS = 44;     // (mode,first,count) -> one array-buffer draw, drawn natively
    public static final int SEM_COMPILED_ARRAYS = -2; // legacy compiled client arrays (the libffi path's row)
    public static final int SEM_BEGIN = 68;           // (mode) -> the site that opens an immediate-mode run
    public static final int SEM_END = 69;             // () -> the site that closes it
    public static final int SEM_READ_PIXELS = 70;     // (x,y,w,h,format,type,pixels) -> read the bound read framebuffer
    public static final int SEM_SCISSOR = 45;         // (x,y,w,h) -> the game's clip rectangle, for the group's scissor
    public static final int SEM_CULL_FACE = 60;       // arg0 = the face GL will cull
    public static final int SEM_FRONT_FACE = 61;      // arg0 = the winding GL calls front
    public static final int SEM_POLYGON_OFFSET = 62;  // (factor, units) as float bits -> the depth bias GL wants
    public static final int SEM_COLOR_MASK = 63;      // (r,g,b,a) -> which channels the draw may write (BH-10)
    public static final int SEM_BLEND_FUNC = 64;      // (src,dst) -> all four GL blend factors, as glBlendFunc does
    public static final int SEM_BLEND_FUNC_SEPARATE = 65; // (srcRgb,dstRgb,srcAlpha,dstAlpha) -> the four factors
    public static final int SEM_BLEND_EQUATION = 66;  // (mode) -> both blend equations
    public static final int SEM_BLEND_EQUATION_SEPARATE = 67; // (rgbMode, alphaMode) -> the two equations
    public static final int SEM_LIST_BASE = 75;      // (base) -> the offset glCallLists adds to every name
    public static final int SEM_CALL_LISTS = 76;     // (count, type, address) -> execute a run of display lists
    public static final int SEM_BIND_IMAGE_TEXTURE = 77; // full GL image-unit tuple -> native ledger

    /** Shared by every unresolved name: the dispatch path must not allocate an empty array per unknown symbol. */
    private static final byte[] EMPTY_ARGS = new byte[0];
    private static final List<GlFn> TABLE = new ArrayList<>();
    private static final Map<String, Integer> IDS = new HashMap<>();

    /** Registers one function-table row; called only by {@link GlFunctionTable}. */
    public static GlFn fn(String name, byte ret, int semantics, byte... args) {
        GlFn fn = new GlFn(name, ret, args, semantics);
        TABLE.add(fn);
        return fn;
    }

    static {
        GlFunctionTable.registerAll();
        for (int i = 0; i < TABLE.size(); i++) {
            IDS.put(TABLE.get(i).name, Integer.valueOf(i));
        }
    }

    /** {@return the function table, in registration order} (the old frontend reads the same rows until E4-c). */
    public static List<GlFn> table() {
        return TABLE;
    }

    /** {@return the name-to-id index over {@link #table()}}. */
    public static Map<String, Integer> ids() {
        return IDS;
    }

    /** {@return the row for a function id, or {@code null} when the id is outside the table}. */
    public static GlFn row(int functionId) {
        return functionId >= 0 && functionId < TABLE.size() ? TABLE.get(functionId) : null;
    }

    // ---- duty 1: the LWJGL3 ICD surface ------------------------------------------------

    /** The installed entry - one per process; GL capabilities are process-global so this is a singleton. */
    private static volatile DirectGlCaptureEntry installed;
    private static volatile boolean capabilitiesInstalled;

    private final GlTrampolines glTrampolines = new GlTrampolines(this::dispatch, this::legacyDispatch);
    private final DispatchThreadGate dispatchGate = new DispatchThreadGate();

    private DirectGlCaptureEntry() {
    }

    /** {@return whether {@link #install()} has installed the entry}. */
    public static boolean isInstalled() {
        return installed != null;
    }

    /** {@return the installed entry, or {@code null} when none is}. */
    public static DirectGlCaptureEntry installed() {
        return installed;
    }

    /**
     * Installs this provider as the process's GL function source; call before
     * {@code GL.createCapabilities()}. LWJGL only lets {@code GL.create} supply the function
     * source when the library's default load was deferred, so this also asserts
     * {@link Configuration#OPENGL_EXPLICIT_INIT} - the window-creation path must set it before
     * anything touches the GL class. (Moved from the old stub's {@code install()} at E4-b2; the
     * census hook keeps the two lines the entry itself owns - unknown symbols and the fn census -
     * while the old stub's own censuses stay with its records until E4-c.)
     */
    public static synchronized DirectGlCaptureEntry install() {
        if (installed == null) {
            installed = new DirectGlCaptureEntry();
            Configuration.OPENGL_EXPLICIT_INIT.set(true);
            GL.create(installed);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                celtium$printAtExit("CELTIUM_UNKNOWN_SYMBOLS " + unknownSymbolSummary());
                if (FN_CENSUS) {
                    celtium$printAtExit("CELTIUM_FN_CENSUS " + fnCensusSummary());
                }
            }, "celtium-capture-entry-census"));
        }
        return installed;
    }

    /**
     * Builds LWJGL3's {@code GLCapabilities} from this entry and installs it; must be called
     * <b>before</b> anything else creates one. The extension set carries every {@code GL_*} name
     * the capabilities class declares plus the version pseudo-extensions up to the version this
     * entry advertises (3.0) - an empty set leaves the generated classes' addresses at zero and
     * aborts the JVM on first use (measured 2026-10-15 against the old stub, whose identity
     * answers this entry preserves). Failure is logged and never fatal.
     */
    public static synchronized void installFirstCapabilities() {
        if (installed == null || capabilitiesInstalled) {
            return;
        }
        try {
            Class<?> capabilitiesClass = Class.forName("org.lwjgl.opengl.GLCapabilities");
            java.util.Set<String> extensions = new java.util.HashSet<>();
            java.util.Set<String> advertisedVersions = new java.util.HashSet<>(java.util.Arrays.asList(
                    "OpenGL11", "OpenGL12", "OpenGL13", "OpenGL14", "OpenGL15",
                    "OpenGL20", "OpenGL21", "OpenGL30"));
            for (java.lang.reflect.Field field : capabilitiesClass.getFields()) {
                String name = field.getName();
                if (name.startsWith("GL_") || advertisedVersions.contains(name)) {
                    extensions.add(name);
                }
            }
            java.lang.reflect.Constructor<?> constructor = capabilitiesClass.getDeclaredConstructor(
                    FunctionProvider.class, java.util.Set.class, boolean.class,
                    java.util.function.IntFunction.class);
            constructor.setAccessible(true);
            Object capabilities = constructor.newInstance(installed, extensions, Boolean.FALSE,
                    (java.util.function.IntFunction<org.lwjgl.PointerBuffer>) org.lwjgl.BufferUtils::createPointerBuffer);
            GL.setCapabilities((org.lwjgl.opengl.GLCapabilities) capabilities);
            capabilitiesInstalled = true;
            celtium$printAtExit("CELTIUM_GLSTUB LWJGL3 capabilities installed first from the capture entry: extensions="
                    + extensions.size() + " thread=" + Thread.currentThread().getName());
        } catch (Throwable error) {
            celtium$printAtExit("CELTIUM_GLSTUB first capabilities install failed: " + error);
        }
    }

    /** Writes one line to the process's own stdout (FileDescriptor.out survives Forge's Log4j shutdown race). */
    static void celtium$printAtExit(String line) {
        try (java.io.PrintStream out = new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out), true)) {
            out.println(line);
        } catch (Throwable ignored) {
            // Not fatal: the exit census is a diagnostic, never a gate.
        }
    }

    // ---- FunctionProvider ---------------------------------------------------------------

    @Override
    public long getFunctionAddress(ByteBuffer nameBuffer) {
        String name = StandardCharsets.UTF_8.decode(nameBuffer.duplicate()).toString();
        int nul = name.indexOf(0);
        if (nul >= 0) {
            name = name.substring(0, nul);
        }
        return addressFor(name);
    }

    @Override
    public long getFunctionAddress(CharSequence name) {
        return addressFor(String.valueOf(name));
    }

    private long addressFor(String name) {
        // Every name gets a trampoline - the names the table knows get their semantics, the rest
        // get a generic no-op. Returning 0 would tell LWJGL capability detection the whole
        // version or extension block is absent, leaving the corresponding pointers empty.
        Integer idValue = IDS.get(name);
        int id = idValue == null ? -1 : idValue.intValue();
        if (id < 0) {
            celtium$noteUnknownSymbol(name);
        }
        byte[] args = id >= 0 ? TABLE.get(id).args : EMPTY_ARGS;
        return this.glTrampolines.addressFor(name, id, args);
    }

    // ---- unknown symbols + fn census ------------------------------------------------------

    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> UNKNOWN_SYMBOLS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong UNKNOWN_SYMBOL_TOTAL =
            new java.util.concurrent.atomic.AtomicLong();

    private static void celtium$noteUnknownSymbol(String name) {
        UNKNOWN_SYMBOL_TOTAL.incrementAndGet();
        java.util.concurrent.atomic.AtomicLong counter = UNKNOWN_SYMBOLS.get(name);
        if (counter == null) {
            java.util.concurrent.atomic.AtomicLong fresh = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong raced = UNKNOWN_SYMBOLS.putIfAbsent(name, fresh);
            counter = raced == null ? fresh : raced;
        }
        counter.incrementAndGet();
    }

    /** {@return the unknown-symbol census: total arrivals and the most frequent names}. */
    public static String unknownSymbolSummary() {
        long total = UNKNOWN_SYMBOL_TOTAL.get();
        if (total == 0L) {
            return "unknown=0";
        }
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> entries =
                new java.util.ArrayList<>(UNKNOWN_SYMBOLS.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder line = new StringBuilder("unknown=").append(total).append(" top=[");
        for (int i = 0; i < entries.size() && i < 12; i++) {
            if (i > 0) {
                line.append(' ');
            }
            line.append(entries.get(i).getKey()).append('=').append(entries.get(i).getValue().get());
        }
        return line.append(']').toString();
    }

    private static final boolean FN_CENSUS = Boolean.getBoolean("celtium.debug.fnCensus");

    private static String fnCensusSummary() {
        java.util.List<GlFn> rows = new java.util.ArrayList<>(TABLE);
        rows.sort((a, b) -> Long.compare(b.calls, a.calls));
        StringBuilder line = new StringBuilder("fn=[");
        int named = 0;
        for (GlFn fn : rows) {
            if (fn.calls <= 0L) {
                continue;
            }
            if (named > 0) {
                line.append(' ');
            }
            line.append(fn.name).append('=').append(fn.calls);
            if (++named >= 24) {
                break;
            }
        }
        return line.append(']').toString();
    }

    // ---- duty 4: the font-window gate ------------------------------------------------------

    private static volatile boolean fontWindowActive;
    private static final java.util.concurrent.atomic.AtomicLong FONT_WINDOW_OPENS =
            new java.util.concurrent.atomic.AtomicLong();

    /** Opens the font window: glyph draws until {@link #endFontWindow()} belong to the font capture. */
    public static void beginFontWindow() {
        fontWindowActive = true;
        FONT_WINDOW_OPENS.incrementAndGet();
    }

    /** Closes the font window opened by {@link #beginFontWindow()}. */
    public static void endFontWindow() {
        fontWindowActive = false;
    }

    /** {@return whether a font window is open}. */
    public static boolean isFontWindowActive() {
        return fontWindowActive;
    }

    // ---- duty 5: the drawing-entry predicate ------------------------------------------------

    /** The drawing entry points the surface census reports on (the old stub's set, moved unchanged). */
    private static final java.util.Set<String> DRAWING_ENTRY_NAMES = java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(java.util.Arrays.asList((
                    "glDrawElements glDrawRangeElements glDrawElementsInstanced glDrawArraysInstanced"
                            + " glMultiDrawArrays glMultiDrawElements glDrawElementsBaseVertex"
                            + " glDrawArraysIndirect glMultiDrawArraysIndirect glMultiDrawElementsIndirect"
                            + " glDrawElementsIndirect glArrayElement glRects glBitmap glDrawPixels glCopyPixels")
                    .split(" "))));

    /**
     * {@return whether {@code key} names a drawing entry point, with or without a descriptor}.
     * The transformer's unrouted census holds JVM keys ({@code glDrawElements(IIIJ)V}); the
     * descriptor is stripped here, once, so the join between the two censuses cannot silently
     * read "none" forever.
     */
    public static boolean isDrawingEntryPointKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        int paren = key.indexOf('(');
        String bare = paren > 0 ? key.substring(0, paren) : key;
        return DRAWING_ENTRY_NAMES.contains(bare);
    }

    // ---- duty 2: the dispatch -------------------------------------------------------------
    //
    // Every trampoline arrival funnels through the DispatchThreadGate (the state the routes
    // write is confined to the dispatching thread; a foreign arrival that overlaps a dispatch in
    // flight is refused and counted, sequential foreign arrivals are served), then routes by the
    // row's semantics to the eight-family destinations: the GLSM* converter classes and
    // GLStateManager inside a DirectGlDispatcher Lane 3 excursion, DisplayListManager for lists,
    // and the seat records (NativeRasterState, PixelUnpackState, GlsmNameLedger) for what they
    // own. Handlers never throw across the boundary: a failure is counted by name and answered
    // with zero, because both upcall entries end in native code that cannot receive a Java
    // exception (audit-gl-deep G1).

    private static final java.util.concurrent.atomic.AtomicLong DISPATCH_FAILURES =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> DISPATCH_FAILURES_BY_NAME =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger DISPATCH_FAILURE_LOGS =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicLong FOREIGN_DISPATCH_REFUSALS =
            new java.util.concurrent.atomic.AtomicLong();

    private static void celtium$noteDispatchFailure(String name, Throwable failure) {
        long total = DISPATCH_FAILURES.incrementAndGet();
        java.util.concurrent.atomic.AtomicLong counter = DISPATCH_FAILURES_BY_NAME.get(name);
        if (counter == null) {
            java.util.concurrent.atomic.AtomicLong fresh = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong raced = DISPATCH_FAILURES_BY_NAME.putIfAbsent(name, fresh);
            counter = raced == null ? fresh : raced;
        }
        long forName = counter.incrementAndGet();
        if (DISPATCH_FAILURE_LOGS.incrementAndGet() <= 8) {
            System.out.println("[Celtium] CELTIUM_GL_DISPATCH_FAILURE fn=" + name + " count=" + forName
                    + " total=" + total + " error=" + failure.getClass().getName() + ": " + failure.getMessage()
                    + " (the call is answered with zero; a Java exception must not cross the upcall boundary)");
            failure.printStackTrace(System.err);
        }
    }

    /** {@return the dispatch failures so far: the total and the worst offenders by name}. */
    public static String dispatchFailureSummary() {
        long total = DISPATCH_FAILURES.get();
        if (total == 0L) {
            return "dispatchFailures=0";
        }
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> entries =
                new java.util.ArrayList<>(DISPATCH_FAILURES_BY_NAME.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder line = new StringBuilder("dispatchFailures=").append(total).append(" top=[");
        for (int i = 0; i < entries.size() && i < 8; i++) {
            if (i > 0) {
                line.append(' ');
            }
            line.append(entries.get(i).getKey()).append('=').append(entries.get(i).getValue().get());
        }
        return line.append(']').toString();
    }

    private final long[] dispatchScratch = new long[GlTrampolines.SLOTS];
    private final java.util.concurrent.atomic.AtomicReference<Thread> dispatchScratchOwner =
            new java.util.concurrent.atomic.AtomicReference<>();

    private long[] dispatchArgsScratch() {
        Thread calling = Thread.currentThread();
        if (calling == this.dispatchScratchOwner.get()) {
            return this.dispatchScratch;
        }
        if (this.dispatchScratchOwner.compareAndSet(null, calling)) {
            return this.dispatchScratch;
        }
        if (calling == this.dispatchScratchOwner.get()) {
            return this.dispatchScratch;
        }
        return new long[GlTrampolines.SLOTS];
    }

    private long dispatch(int functionId, long c0, long c1, long c2, long c3, long c4, long c5, long c6,
            long c7, long c8, long c9, long c10, long c11) {
        long callBegin = BackendCallCounters.noteCallBegin();
        try {
            if (functionId < 0) {
                return 0L;
            }
            GlFn fn = TABLE.get(functionId);
            if (FN_CENSUS) {
                fn.calls++;
            }
            long[] c = dispatchArgsScratch();
            c[0] = c0; c[1] = c1; c[2] = c2; c[3] = c3; c[4] = c4; c[5] = c5;
            c[6] = c6; c[7] = c7; c[8] = c8; c[9] = c9; c[10] = c10; c[11] = c11;
            return dispatchArgs(fn, c);
        } catch (Throwable failure) {
            celtium$noteDispatchFailure(celtium$fnNameForId(functionId), failure);
            return 0L;
        } finally {
            BackendCallCounters.noteCallEnd(callBegin);
        }
    }

    private void legacyDispatch(int functionId, long result, long args) {
        long value;
        long callBegin = BackendCallCounters.noteCallBegin();
        try {
            value = legacyDispatchBody(functionId, args);
        } catch (Throwable failure) {
            celtium$noteDispatchFailure(celtium$fnNameForId(functionId), failure);
            value = 0L;
        } finally {
            BackendCallCounters.noteCallEnd(callBegin);
        }
        APIUtil.apiClosureRetP(result, value);
    }

    private long legacyDispatchBody(int functionId, long args) {
        if (functionId < 0) {
            return 0L;
        }
        GlFn fn = TABLE.get(functionId);
        if (FN_CENSUS) {
            fn.calls++;
        }
        long[] c = dispatchArgsScratch();
        for (int i = 0; i < fn.args.length && i < GlTrampolines.SLOTS; i++) {
            long slot = MemoryUtil.memGetAddress(args + (long) i * org.lwjgl.system.Pointer.POINTER_SIZE);
            switch (fn.args[i]) {
                case GlFunctionTable.A_INT:
                case GlFunctionTable.A_UINT:
                    c[i] = MemoryUtil.memGetInt(slot);
                    break;
                case GlFunctionTable.A_PTR:
                    c[i] = MemoryUtil.memGetAddress(slot);
                    break;
                case GlFunctionTable.A_LONG:
                    c[i] = MemoryUtil.memGetLong(slot);
                    break;
                case GlFunctionTable.A_FLOAT:
                    c[i] = Float.floatToRawIntBits(MemoryUtil.memGetFloat(slot)) & 0xFFFFFFFFL;
                    break;
                case GlFunctionTable.A_DOUBLE:
                    c[i] = Double.doubleToRawLongBits(MemoryUtil.memGetDouble(slot));
                    break;
                default:
                    break;
            }
        }
        return dispatchArgs(fn, c);
    }

    private static String celtium$fnNameForId(int functionId) {
        GlFn fn = row(functionId);
        return fn == null ? "id=" + functionId : fn.name;
    }

    private long dispatchArgs(GlFn fn, long[] c) {
        Thread self = Thread.currentThread();
        if (this.dispatchGate.enter(self) == DispatchThreadGate.REFUSED) {
            FOREIGN_DISPATCH_REFUSALS.incrementAndGet();
            return 0L;
        }
        try {
            DirectGlDispatcher.enterLane3();
            try {
                return dispatchSemantics(fn, c);
            } finally {
                DirectGlDispatcher.exitLane3();
            }
        } catch (Throwable failure) {
            celtium$noteDispatchFailure(fn == null ? "<unresolved>" : fn.name, failure);
            return 0L;
        } finally {
            this.dispatchGate.exit(self);
        }
    }

    private static float f(long slot) {
        return Float.intBitsToFloat((int) slot);
    }

    private static double d(long slot) {
        return Double.longBitsToDouble(slot);
    }

    private static ByteBuffer bytes(long ptr, int size) {
        return ptr == 0L || size <= 0 ? null : MemoryUtil.memByteBuffer(ptr, size);
    }

    private static String cString(long ptr) {
        return ptr == 0L ? "" : MemoryUtil.memUTF8(ptr);
    }

    /** Bytes one tex image of this shape carries, honouring the recorded unpack row length. */
    private static int texBytes(int format, int type, int width, int height) {
        int comps;
        switch (format) {
            case 0x1903: case 0x1906: case 0x1909: case 0x1902: case 0x8D94: comps = 1; break;
            case 0x190A: case 0x8227: case 0x84F9: comps = 2; break;
            case 0x1907: comps = 3; break;
            default: comps = 4; break;
        }
        int typeSize;
        switch (type) {
            case 0x1403: case 0x1402: case 0x140B: typeSize = 2; break;
            case 0x1405: case 0x1404: case 0x1406: typeSize = 4; break;
            case 0x1407: typeSize = 8; break;
            default: typeSize = 1; break;
        }
        int row = Math.max(width, PixelUnpackState.current().unpackRowLength());
        long size = (long) comps * typeSize * ((long) row * (height - 1) + width);
        return (int) Math.max(1L, Math.min(size, Integer.MAX_VALUE - 8));
    }

    private long dispatchSemantics(GlFn fn, long[] c) {
        switch (fn.semantics) {
            case SEM_NOOP:
                return routeNoopByName(fn, c);
            case SEM_GET_ERROR:
                return GLStateManager.glGetError();
            case SEM_GET_STRING:
                return GlsmStringState.current().intern(GLStateManager.glGetString((int) c[0]));
            case SEM_GET_STRINGI:
                return GlsmStringState.current().intern(GLStateManager.glGetStringi((int) c[0], (int) c[1]));
            case SEM_IS_ENABLED:
                return c.length > 1 && fn.args.length > 1
                        ? (GLSMEnable.glIsEnabledi((int) c[0], (int) c[1]) ? 1L : 0L)
                        : (GLSMEnable.glIsEnabled((int) c[0]) ? 1L : 0L);
            case SEM_ENABLE:
                GLSMEnable.glEnable((int) c[0]);
                return 0L;
            case SEM_DISABLE:
                GLSMEnable.glDisable((int) c[0]);
                return 0L;
            case SEM_GET_INTEGERV:
                getInts((int) c[0], c[1]);
                return 0L;
            case SEM_GET_FLOATV:
                getFloats((int) c[0], c[1]);
                return 0L;
            case SEM_GET_BOOLEANV:
                getBooleans((int) c[0], c[1]);
                return 0L;
            case SEM_GET_INTEGER64V:
                getLongs((int) c[0], c[1]);
                return 0L;
            case SEM_IS_OBJECT:
                return isObject(fn.name, (int) c[0]) ? 1L : 0L;
            case SEM_GEN:
                return generate(fn, c);
            case SEM_DELETE:
                delete(fn, c);
                return 0L;
            case SEM_ZERO:
                return zeroAnswer(fn, c);
            case SEM_FBO_COMPLETE:
                return GLSMFramebuffer.glCheckFramebufferStatus((int) c[0]);
            case SEM_SIGNALED:
                return GLSMBuffers.glClientWaitSync(c[0], (int) c[1], c[2]);
            case SEM_MAP:
                return mapBuffer(fn, c);
            case SEM_UNMAP:
                return unmapBuffer(fn, c);
            case SEM_BUFFER_DATA:
                bufferData(fn, c);
                return 0L;
            case SEM_BUFFER_SUBDATA:
                if (c[3] != 0L) {
                    GLSMBuffers.glBufferSubData((int) c[0], c[1], bytes(c[3], (int) c[2]));
                    filePboBytes((int) c[0], c[1], c[3], (int) c[2]);
                }
                return 0L;
            case SEM_STATUS:
                statusAnswer(fn, c);
                return 0L;
            case SEM_INFO_LOG:
                infoLogAnswer(fn, c);
                return 0L;
            case SEM_BIND_BUFFER:
                GLSMBuffers.glBindBuffer((int) c[0], (int) c[1]);
                if (c[0] == 0x88EBL) {
                    PixelUnpackState.current().pixelUnpackBuffer((int) c[1]);
                }
                return 0L;
            case SEM_BIND_TEXTURE:
                GLSMTextures.glBindTexture((int) c[0], (int) c[1]);
                return 0L;
            case SEM_ACTIVE_TEXTURE:
                GLSMTextures.glActiveTexture((int) c[0]);
                return 0L;
            case SEM_TEXIMAGE:
            case SEM_TEXIMAGE_PIXELS:
                texImage(fn, c);
                return 0L;
            case SEM_TEXSUBIMAGE:
                texSubImage(fn, c);
                return 0L;
            case SEM_COMPRESSED_TEXIMAGE:
                // The converter holds no compressed-image form; the old stub recorded the
                // extent only and stored no pixels either, so the call stays a counted arrival.
                return 0L;
            case SEM_PIXEL_STORE:
                if (fn.name.indexOf('f') == fn.name.length() - 1) {
                    GLSMTextures.glPixelStoref((int) c[0], f(c[1]));
                    PixelUnpackState.current().pixelStore((int) c[0], (int) f(c[1]));
                } else {
                    GLSMTextures.glPixelStorei((int) c[0], (int) c[1]);
                    PixelUnpackState.current().pixelStore((int) c[0], (int) c[1]);
                }
                return 0L;
            case SEM_VIEWPORT:
                GLSMRasterizer.glViewport((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case SEM_SCISSOR:
                GLSMRasterizer.glScissor((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case SEM_CLEAR:
                GLSMFramebuffer.glClear((int) c[0]);
                return 0L;
            case SEM_CLEAR_COLOR:
                GLStateManager.glClearColor(f(c[0]), f(c[1]), f(c[2]), f(c[3]));
                return 0L;
            case SEM_CLEAR_DEPTH:
                if ("glClearDepth".equals(fn.name)) {
                    GLSMDepthStencilAlpha.glClearDepth(d(c[0]));
                } else {
                    GLSMDepthStencilAlpha.glClearDepth((double) f(c[0]));
                }
                return 0L;
            case SEM_LINE_WIDTH:
                GLSMRasterizer.glLineWidth(f(c[0]));
                return 0L;
            case SEM_USE_PROGRAM:
                GLSMPrograms.glUseProgram((int) c[0]);
                return 0L;
            case SEM_NEW_LIST:
                GLSMDraws.glNewList((int) c[0], (int) c[1]);
                return 0L;
            case SEM_END_LIST:
                GLSMDraws.glEndList();
                return 0L;
            case SEM_CALL_LIST:
                GLSMDraws.glCallList((int) c[0]);
                return 0L;
            case SEM_CALL_LISTS:
                callLists(c);
                return 0L;
            case SEM_LIST_BASE:
                GLSMDraws.glListBase((int) c[0]);
                return 0L;
            case SEM_BIND_VERTEX_ARRAY:
                GLSMVertexInput.glBindVertexArray((int) c[0]);
                return 0L;
            case SEM_BIND_FRAMEBUFFER:
                GLSMFramebuffer.glBindFramebuffer((int) c[0], (int) c[1]);
                return 0L;
            case SEM_BLIT_FRAMEBUFFER:
                GLSMFramebuffer.glBlitFramebuffer((int) c[0], (int) c[1], (int) c[2], (int) c[3],
                        (int) c[4], (int) c[5], (int) c[6], (int) c[7], (int) c[8], (int) c[9]);
                return 0L;
            case SEM_COPY_TEX_IMAGE:
                GLSMTextures.glCopyTexImage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], (int) c[6], (int) c[7]);
                return 0L;
            case SEM_COPY_TEX_SUB_IMAGE:
                GLSMTextures.glCopyTexSubImage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3],
                        (int) c[4], (int) c[5], (int) c[6], (int) c[7]);
                return 0L;
            case SEM_COPY_IMAGE_SUB_DATA:
                GLSMTextures.glCopyImageSubData((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], (int) c[6], (int) c[7], (int) c[8], (int) c[9], (int) c[10], (int) c[11],
                        (int) 0, (int) 0, (int) 0);
                return 0L;
            case SEM_TEX_PARAM_I:
                GLSMTextures.glTexParameteri((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case SEM_TEX_PARAM_IV:
                if (fn.name.indexOf('f') >= 0) {
                    GLSMTextures.glTexParameter((int) c[0], (int) c[1],
                            MemoryUtil.memFloatBuffer(c[2], 4));
                } else {
                    GLSMTextures.glTexParameter((int) c[0], (int) c[1],
                            MemoryUtil.memIntBuffer(c[2], 4));
                }
                return 0L;
            case SEM_TEX_PARAM_I_DSA:
                GLStateManager.glTextureParameteri((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case SEM_TEX_PARAM_IV_DSA:
                if (c[2] != 0L) {
                    int[] params = new int[4];
                    for (int i = 0; i < 4; i++) {
                        params[i] = MemoryUtil.memGetInt(c[2] + (long) i * 4L);
                    }
                    GLStateManager.glTextureParameteriv((int) c[0], (int) c[1], params);
                }
                return 0L;
            case SEM_MULT_MATRIX:
                if (fn.name.endsWith("f")) {
                    GLSMMatrices.glMultMatrix(MemoryUtil.memFloatBuffer(c[0], 16));
                } else {
                    GLSMMatrices.glMultMatrix(MemoryUtil.memDoubleBuffer(c[0], 16));
                }
                return 0L;
            case SEM_MATRIX_MODE:
                GLSMMatrices.glMatrixMode((int) c[0]);
                return 0L;
            case SEM_CULL_FACE:
                GLSMRasterizer.glCullFace((int) c[0]);
                return 0L;
            case SEM_FRONT_FACE:
                GLSMRasterizer.glFrontFace((int) c[0]);
                return 0L;
            case SEM_POLYGON_OFFSET:
                GLSMRasterizer.glPolygonOffset(f(c[0]), f(c[1]));
                return 0L;
            case SEM_COLOR_MASK:
                GLStateManager.glColorMask(c[0] != 0L, c[1] != 0L, c[2] != 0L, c[3] != 0L);
                return 0L;
            case SEM_BLEND_FUNC:
                GLSMBlend.glBlendFunc((int) c[0], (int) c[1]);
                return 0L;
            case SEM_BLEND_FUNC_SEPARATE:
                GLSMBlend.glBlendFuncSeparate((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case SEM_BLEND_EQUATION:
                GLSMBlend.glBlendEquation((int) c[0]);
                return 0L;
            case SEM_BLEND_EQUATION_SEPARATE:
                GLSMBlend.glBlendEquationSeparate((int) c[0], (int) c[1]);
                return 0L;
            case SEM_BEGIN:
                GLSMDraws.glBegin((int) c[0]);
                return 0L;
            case SEM_END:
                GLSMDraws.glEnd();
                return 0L;
            case SEM_READ_PIXELS:
                readPixels(c);
                return 0L;
            case SEM_FOG_I:
                GLSMFog.glFogi((int) c[0], (int) c[1]);
                return 0L;
            case SEM_FOG_F:
                GLSMFog.glFogf((int) c[0], f(c[1]));
                return 0L;
            case SEM_FOG_FV:
                GLSMFog.glFog((int) c[0], MemoryUtil.memFloatBuffer(c[1], 4));
                return 0L;
            case SEM_FOG_IV:
                GLSMFog.glFog((int) c[0], MemoryUtil.memIntBuffer(c[1], 4));
                return 0L;
            case SEM_TEX_ENV_I:
                GLSMTexEnvGen.glTexEnvi((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case SEM_TEX_ENV_F:
                GLSMTexEnvGen.glTexEnvf((int) c[0], (int) c[1], f(c[2]));
                return 0L;
            case SEM_TEX_ENV_FV:
                GLSMTexEnvGen.glTexEnv((int) c[0], (int) c[1], MemoryUtil.memFloatBuffer(c[2], 4));
                return 0L;
            case SEM_GET_TEX_ENV_I:
                if (c[2] != 0L) {
                    MemoryUtil.memPutInt(c[2], GLSMTexEnvGen.glGetTexEnvi((int) c[0], (int) c[1]));
                }
                return 0L;
            case SEM_GET_TEX_ENV_FV:
                if (c[2] != 0L) {
                    GLSMTexEnvGen.glGetTexEnv((int) c[0], (int) c[1], MemoryUtil.memFloatBuffer(c[2], 4));
                }
                return 0L;
            case SEM_VERTEX_POINTER:
                vertexPointer(fn, c);
                return 0L;
            case SEM_DRAW_ARRAYS:
                GLSMDraws.glDrawArrays((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case SEM_BIND_IMAGE_TEXTURE:
                GLSMTextures.glBindImageTexture((int) c[0], (int) c[1], (int) c[2], c[3] != 0L,
                        (int) c[4], (int) c[5], (int) c[6]);
                return 0L;
            default:
                return 0L;
        }
    }

    // ---- dispatch helpers -------------------------------------------------------------------

    private static boolean isObject(String name, int id) {
        switch (name) {
            case "glIsProgram": case "glIsProgramARB": return GLSMPrograms.glIsProgram(id);
            case "glIsShader": case "glIsShaderARB": return GLSMPrograms.glIsShader(id);
            case "glIsFramebuffer": return GLSMFramebuffer.glIsFramebuffer(id);
            case "glIsRenderbuffer": return GLSMFramebuffer.glIsRenderbuffer(id);
            case "glIsQuery": case "glIsQueryARB": return GLSMQueries.glIsQuery(id);
            default: return GlsmNameLedger.current().isLive(id);
        }
    }

    private long generate(GlFn fn, long[] c) {
        String name = fn.name;
        if (name.equals("glCreateShader") || name.equals("glCreateShaderObjectARB")) {
            int id = GLSMPrograms.glCreateShader((int) c[0]);
            GlsmNameLedger.current().addName(id);
            return id;
        }
        if (name.equals("glCreateProgram") || name.equals("glCreateProgramObjectARB")) {
            int id = GLSMPrograms.glCreateProgram();
            GlsmNameLedger.current().addName(id);
            return id;
        }
        if (name.equals("glGenLists")) {
            return GLSMDraws.glGenLists((int) c[0]);
        }
        if (name.equals("glGenSync") || name.equals("glFenceSync")) {
            return GLSMBuffers.glFenceSync((int) c[0], (int) c[1]);
        }
        if (fn.args.length < 2) {
            return 0L;
        }
        int n = (int) c[0];
        long out = c[1];
        if (out == 0L || n <= 0) {
            return 0L;
        }
        for (int i = 0; i < n; i++) {
            int id;
            switch (name) {
                case "glGenTextures": case "glGenTexturesARB": id = GLSMTextures.glGenTextures(); break;
                case "glGenBuffers": case "glGenBuffersARB": id = GLSMBuffers.glGenBuffers(); break;
                case "glGenFramebuffers": case "glGenFramebuffersEXT": case "glGenFramebuffersARB":
                    id = GLSMFramebuffer.glGenFramebuffers(); break;
                case "glGenRenderbuffers": case "glGenRenderbuffersEXT": case "glGenRenderbuffersARB":
                    id = GLSMFramebuffer.glGenRenderbuffers(); break;
                case "glGenVertexArrays": case "glGenVertexArraysARB": id = GLSMVertexInput.glGenVertexArrays(); break;
                case "glGenQueries": case "glGenQueriesARB": id = GLSMQueries.glGenQueries(); break;
                case "glGenSamplers": id = GLSMTextures.glGenSamplers(); break;
                default: id = org.soutalab.celtium.vulkan.RenderObjectIds.reserve(1); break;
            }
            GlsmNameLedger.current().addName(id);
            MemoryUtil.memPutInt(out + (long) i * 4L, id);
        }
        return 0L;
    }

    private void delete(GlFn fn, long[] c) {
        String name = fn.name;
        switch (name) {
            case "glDeleteShader": case "glDeleteObjectARB":
                GLSMPrograms.glDeleteShader((int) c[0]);
                GlsmNameLedger.current().removeName((int) c[0]);
                return;
            case "glDeleteProgram":
                GLSMPrograms.glDeleteProgram((int) c[0]);
                GlsmNameLedger.current().removeName((int) c[0]);
                return;
            case "glDeleteSync":
                GLSMBuffers.glDeleteSync(c[0]);
                return;
            case "glDeleteLists":
                GLSMDraws.glDeleteLists((int) c[0], (int) c[1]);
                return;
            default:
                break;
        }
        int n = (int) c[0];
        long ptr = c[1];
        if (ptr == 0L || n <= 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            int id = MemoryUtil.memGetInt(ptr + (long) i * 4L);
            switch (name) {
                case "glDeleteTextures": case "glDeleteTexturesARB":
                    GLSMTextures.glDeleteTextures(id); break;
                case "glDeleteBuffers": case "glDeleteBuffersARB":
                    GLSMBuffers.glDeleteBuffers(id);
                    PixelUnpackState.current().forgetFallbackStorage(id);
                    break;
                case "glDeleteFramebuffers": case "glDeleteFramebuffersEXT": case "glDeleteFramebuffersARB":
                    GLSMFramebuffer.glDeleteFramebuffers(id); break;
                case "glDeleteRenderbuffers": case "glDeleteRenderbuffersEXT": case "glDeleteRenderbuffersARB":
                    GLSMFramebuffer.glDeleteRenderbuffers(id); break;
                case "glDeleteVertexArrays": case "glDeleteVertexArraysARB":
                    GLSMVertexInput.glDeleteVertexArrays(id); break;
                case "glDeleteQueries": case "glDeleteQueriesARB":
                    GLSMQueries.glDeleteQueries(id); break;
                case "glDeleteSamplers":
                    GLSMTextures.glDeleteSamplers(id); break;
                default:
                    break;
            }
            GlsmNameLedger.current().removeName(id);
        }
    }

    private long zeroAnswer(GlFn fn, long[] c) {
        switch (fn.name) {
            case "glRenderMode": return GLSMDraws.glRenderMode((int) c[0]);
            case "glGetUniformLocation": case "glGetUniformLocationARB":
                return GLSMPrograms.glGetUniformLocation((int) c[0], cString(c[1]));
            case "glGetAttribLocation":
                return GLSMPrograms.glGetAttribLocation((int) c[0], cString(c[1]));
            default: return 0L;
        }
    }

    private long mapBuffer(GlFn fn, long[] c) {
        ByteBuffer mapped;
        switch (fn.name) {
            case "glMapBuffer": case "glMapBufferARB":
                mapped = GLSMBuffers.glMapBuffer((int) c[0], (int) c[1]);
                break;
            case "glMapBufferRange":
                mapped = GLSMBuffers.glMapBufferRange((int) c[0], c[1], c[2], (int) c[3]);
                break;
            default:
                return 0L;
        }
        return mapped == null ? 0L : MemoryUtil.memAddress(mapped);
    }

    private long unmapBuffer(GlFn fn, long[] c) {
        if (fn.name.startsWith("glUnmapNamedBuffer")) {
            return 1L;
        }
        return GLSMBuffers.glUnmapBuffer((int) c[0]) ? 1L : 0L;
    }

    private void bufferData(GlFn fn, long[] c) {
        int target = (int) c[0];
        long size = c[1];
        long data = c[2];
        int usage = (int) c[3];
        if (fn.namedObject) {
            if (data != 0L) {
                GLSMBuffers.glNamedBufferData(target, bytes(data, (int) Math.min(size, Integer.MAX_VALUE - 8)), usage);
            } else {
                GLSMBuffers.glNamedBufferData(target, size, usage);
            }
        } else {
            GLSMBuffers.nglBufferData(target, size, data, usage);
        }
        if (target == 0x88EB && data != 0L && size > 0L) {
            filePboBytes(target, 0L, data, (int) Math.min(size, Integer.MAX_VALUE - 8));
        }
    }

    /** Files a copy of bytes written into the bound pixel unpack buffer, for the capture store's reads. */
    private static void filePboBytes(int target, long offset, long data, int size) {
        if (target != 0x88EB || data == 0L || size <= 0) {
            return;
        }
        PixelUnpackState state = PixelUnpackState.current();
        int bound = state.pixelUnpackBuffer();
        if (bound == 0) {
            return;
        }
        ByteBuffer existing = state.storage(bound);
        int needed = (int) offset + size;
        ByteBuffer copy;
        if (existing != null && existing.capacity() >= needed) {
            copy = existing;
        } else {
            copy = ByteBuffer.allocate(Math.max(needed, 1));
            if (existing != null) {
                ByteBuffer view = existing.duplicate();
                view.position(0);
                view.limit(Math.min(existing.capacity(), copy.capacity()));
                copy.put(view);
            }
        }
        ByteBuffer source = MemoryUtil.memByteBuffer(data, size);
        ByteBuffer targetView = copy.duplicate();
        targetView.position((int) offset);
        targetView.limit(needed);
        targetView.put(source);
        state.fileFallbackStorage(bound, copy);
    }

    private void statusAnswer(GlFn fn, long[] c) {
        if (fn.name.equals("glGetFramebufferAttachmentParameteriv")) {
            // (target, attachment, pname, params*) - the one status row whose out slot is c[3].
            if (c[3] != 0L) {
                MemoryUtil.memPutInt(c[3], GLSMFramebuffer.glGetFramebufferAttachmentParameteri(
                        (int) c[0], (int) c[1], (int) c[2]));
            }
            return;
        }
        long out = c[2];
        if (out == 0L) {
            return;
        }
        int obj = (int) c[0];
        int pname = (int) c[1];
        switch (fn.name) {
            case "glGetProgramiv":
                MemoryUtil.memPutInt(out, GLSMPrograms.glGetProgrami(obj, pname));
                return;
            case "glGetShaderiv":
                MemoryUtil.memPutInt(out, GLSMPrograms.glGetShaderi(obj, pname));
                return;
            case "glGetObjectParameterivARB": case "glGetObjectParameteriARB":
                GLSMPrograms.glGetObjectParameterARB(obj, pname, MemoryUtil.memIntBuffer(out, 1));
                return;
            case "glGetBufferParameteriv": case "glGetBufferParameterivARB":
                MemoryUtil.memPutInt(out, GLSMBuffers.glGetBufferParameteri(obj, pname));
                return;
            case "glGetTexParameteriv":
                MemoryUtil.memPutInt(out, GLSMTextures.glGetTexParameteri(obj, pname));
                return;
            case "glGetTexParameterfv":
                MemoryUtil.memPutFloat(out, GLSMTextures.glGetTexParameterf(obj, pname));
                return;
            case "glGetQueryObjectiv":
                MemoryUtil.memPutInt(out, GLSMQueries.glGetQueryObjecti(obj, pname));
                return;
            case "glGetActiveUniform":
                GLSMPrograms.glGetActiveUniform(obj, (int) c[1],
                        c[3] == 0L ? null : MemoryUtil.memIntBuffer(c[3], 1),
                        c[4] == 0L ? null : MemoryUtil.memIntBuffer(c[4], 1),
                        c[5] == 0L ? null : MemoryUtil.memIntBuffer(c[5], 1),
                        c[6] == 0L ? null : MemoryUtil.memByteBuffer(c[6], (int) c[2]));
                return;
            case "glGetActiveAttrib":
                GLSMPrograms.glGetActiveAttrib(obj, (int) c[1],
                        c[3] == 0L ? null : MemoryUtil.memIntBuffer(c[3], 1),
                        c[4] == 0L ? null : MemoryUtil.memIntBuffer(c[4], 1),
                        c[5] == 0L ? null : MemoryUtil.memIntBuffer(c[5], 1),
                        c[6] == 0L ? null : MemoryUtil.memByteBuffer(c[6], (int) c[2]));
                return;
            case "glGetAttachedShaders": case "glGetAttachedObjectsARB":
                GLSMPrograms.glGetAttachedShaders(obj,
                        c[2] == 0L ? null : MemoryUtil.memIntBuffer(c[2], 1),
                        c[3] == 0L ? null : MemoryUtil.memIntBuffer(c[3], 64));
                return;
            default:
                MemoryUtil.memPutInt(out, 0);
        }
    }

    private void infoLogAnswer(GlFn fn, long[] c) {
        String log;
        switch (fn.name) {
            case "glGetShaderInfoLog":
                log = GLSMPrograms.glGetShaderInfoLog((int) c[0]);
                break;
            case "glGetProgramInfoLog":
                log = GLSMPrograms.glGetProgramInfoLog((int) c[0]);
                break;
            case "glGetShaderSource": case "glGetShaderSourceARB":
                log = GLSMPrograms.glGetShaderSource((int) c[0]);
                break;
            default:
                log = GLSMPrograms.glGetProgramInfoLog((int) c[0]);
                break;
        }
        writeLog(c[1], c[2], c[3], log == null ? "" : log);
    }

    /** Writes an info log in GL shape: ASCII bytes, a NUL when room remains, and the byte count (sans NUL). */
    private static void writeLog(long bufSize, long lengthPtr, long logPtr, String log) {
        int capacity = (int) bufSize;
        byte[] ascii = log.getBytes(StandardCharsets.US_ASCII);
        int written = Math.min(ascii.length, Math.max(0, capacity - 1));
        if (logPtr != 0L && capacity > 0) {
            ByteBuffer target = MemoryUtil.memByteBuffer(logPtr, capacity);
            target.put(ascii, 0, written);
            if (written < capacity) {
                target.put((byte) 0);
            }
        }
        if (lengthPtr != 0L) {
            MemoryUtil.memPutInt(lengthPtr, written);
        }
    }

    // ---- duty 3: the glGet* composite router --------------------------------------------------
    //
    // Each pname is answered by the seat that owns the record: the current program from the
    // Lane 2 register (the same read point 6 of the adjudication fixed for the monolith's
    // boundProgram gate), the scissor box from the raster seat, the bound texture from the
    // converter's texture state, and everything else from GLStateManager, which holds the
    // shared fixed-function state. No pname falls through to a bulk zero.

    private void getInts(int pname, long out) {
        if (out == 0L) {
            return;
        }
        if (pname == 0x8B8D /* GL_CURRENT_PROGRAM */) {
            MemoryUtil.memPutInt(out, Lane2StateMirror.current().register().activeProgramId());
            return;
        }
        if (pname == 0x0C10 /* GL_SCISSOR_BOX */) {
            int[] box = NativeRasterState.current().scissorBox();
            for (int i = 0; i < 4; i++) {
                MemoryUtil.memPutInt(out + (long) i * 4L, box[i]);
            }
            return;
        }
        if (pname == 0x8069 /* GL_TEXTURE_BINDING_2D */) {
            MemoryUtil.memPutInt(out, GLSMTextures.getBoundTextureForServerState());
            return;
        }
        GLStateManager.glGetInteger(pname, MemoryUtil.memIntBuffer(out, 16));
    }

    private void getFloats(int pname, long out) {
        if (out == 0L) {
            return;
        }
        GLStateManager.glGetFloat(pname, MemoryUtil.memFloatBuffer(out, 16));
    }

    private void getBooleans(int pname, long out) {
        if (out == 0L) {
            return;
        }
        if (pname == 0x0C11 /* GL_SCISSOR_TEST */) {
            MemoryUtil.memPutByte(out, (byte) (NativeRasterState.current().scissorTest() ? 1 : 0));
            return;
        }
        GLStateManager.glGetBoolean(pname, MemoryUtil.memByteBuffer(out, 16));
    }

    private void getLongs(int pname, long out) {
        if (out == 0L) {
            return;
        }
        java.nio.IntBuffer ints = java.nio.IntBuffer.allocate(4);
        GLStateManager.glGetInteger(pname, ints);
        for (int i = 0; i < 4; i++) {
            MemoryUtil.memPutLong(out + (long) i * 8L, ints.get(i));
        }
    }

    private void texImage(GlFn fn, long[] c) {
        String name = fn.name;
        if (name.equals("glTexStorage2D")) {
            GLSMTextures.glTexStorage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4]);
            return;
        }
        if (name.equals("glTexImage2D") || name.equals("glTextureImage2DEXT")) {
            int format = (int) c[6];
            int type = (int) c[7];
            long pixels = c[8];
            if (pixels == 0L) {
                GLSMTextures.glTexImage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], format, type, 0L);
            } else if (PixelUnpackState.current().pixelUnpackBuffer() != 0) {
                GLSMTextures.glTexImage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], format, type, pixels);
            } else {
                GLSMTextures.glTexImage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], format, type, bytes(pixels, texBytes(format, type, (int) c[3], (int) c[4])));
            }
            return;
        }
        // glTexImage1D/3D and the remaining storage forms keep the old stub's extent-only
        // answer: the call is counted by the fn census and no pixels are read.
    }

    private void texSubImage(GlFn fn, long[] c) {
        if (!fn.name.equals("glTexSubImage2D")) {
            return;
        }
        int format = (int) c[6];
        int type = (int) c[7];
        long pixels = c[8];
        if (pixels == 0L) {
            GLSMTextures.glTexSubImage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                    (int) c[5], format, type, 0L);
        } else if (PixelUnpackState.current().pixelUnpackBuffer() != 0) {
            GLSMTextures.glTexSubImage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                    (int) c[5], format, type, pixels);
        } else {
            GLSMTextures.glTexSubImage2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                    (int) c[5], format, type, bytes(pixels, texBytes(format, type, (int) c[4], (int) c[5])));
        }
    }

    private void readPixels(long[] c) {
        int width = (int) c[2];
        int height = (int) c[3];
        int format = (int) c[4];
        int type = (int) c[5];
        long pixels = c[6];
        if (pixels == 0L) {
            return;
        }
        if (pixels < 0x100000L) {
            // A small value is a pixel-pack buffer offset, not an address.
            GLSMFramebuffer.glReadPixels((int) c[0], (int) c[1], width, height, format, type, pixels);
        } else {
            GLSMFramebuffer.glReadPixels((int) c[0], (int) c[1], width, height, format, type,
                    bytes(pixels, texBytes(format, type, width, height)));
        }
    }

    private void callLists(long[] c) {
        int n = (int) c[0];
        int type = (int) c[1];
        long ptr = c[2];
        if (ptr == 0L || n <= 0) {
            return;
        }
        switch (type) {
            case 0x1403: /* GL_UNSIGNED_SHORT */
                GLSMDraws.glCallLists(MemoryUtil.memShortBuffer(ptr, n));
                break;
            case 0x1401: /* GL_UNSIGNED_BYTE */
                GLSMDraws.glCallLists(MemoryUtil.memByteBuffer(ptr, n));
                break;
            default:
                GLSMDraws.glCallLists(MemoryUtil.memIntBuffer(ptr, n));
                break;
        }
    }

    private void vertexPointer(GlFn fn, long[] c) {
        switch (fn.name) {
            case "glVertexPointer":
                GLSMVertexInput.glVertexPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                break;
            case "glNormalPointer":
                GLSMVertexInput.glNormalPointer((int) c[0], (int) c[1], c[2]);
                break;
            case "glColorPointer":
                GLSMVertexInput.glColorPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                break;
            case "glTexCoordPointer":
                GLSMVertexInput.glTexCoordPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                break;
            default:
                break;
        }
    }

    // ---- the SEM_NOOP name router -------------------------------------------------------------
    //
    // The table's no-op block registers its rows with a rough all-pointer signature, because the
    // old stub discarded the arguments. The values that survive that marshalling are the integer
    // and pointer arguments (floats and doubles travel in other registers and never reach the
    // slots), so this router sends exactly the integer/pointer/no-argument names to their
    // converter destinations - draws, program lifecycle, attrib pointers, FBO composition, the
    // attrib stack, sampler and buffer bindings - and leaves the float-carrying names as counted
    // arrivals, which is the answer the old stub gave them too. Transformed callers are not
    // affected either way: they reach the same GLSM* destinations through the hint doors with
    // exact values.

    private long routeNoopByName(GlFn fn, long[] c) {
        switch (fn.name) {
            // draws (integer + pointer arguments)
            case "glDrawElements":
                GLSMDraws.glDrawElements((int) c[0], (int) c[1], (int) c[2], c[3]);
                return 0L;
            case "glDrawRangeElements":
                GLSMDraws.glDrawRangeElements((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5]);
                return 0L;
            case "glDrawElementsInstanced":
                GLSMDraws.glDrawElementsInstanced((int) c[0], (int) c[1], (int) c[2], c[3], (int) c[4]);
                return 0L;
            case "glDrawArraysInstanced":
                GLSMDraws.glDrawArraysInstanced((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case "glDrawElementsBaseVertex":
                GLSMDraws.glDrawElementsBaseVertex((int) c[0], (int) c[1], (int) c[2], c[3], (int) c[4]);
                return 0L;
            case "glDrawRangeElementsBaseVertex":
                GLSMDraws.glDrawRangeElementsBaseVertex((int) c[0], (int) c[1], (int) c[2], (int) c[3],
                        (int) c[4], c[5], (int) c[6]);
                return 0L;
            case "glMultiDrawArrays": {
                int drawcount = (int) c[3];
                if (c[1] != 0L && c[2] != 0L && drawcount > 0) {
                    int[] firsts = new int[drawcount];
                    int[] counts = new int[drawcount];
                    for (int i = 0; i < drawcount; i++) {
                        firsts[i] = MemoryUtil.memGetInt(c[1] + (long) i * 4L);
                        counts[i] = MemoryUtil.memGetInt(c[2] + (long) i * 4L);
                    }
                    GLSMDraws.glMultiDrawArrays((int) c[0], firsts, counts);
                }
                return 0L;
            }
            case "glMultiDrawElementsBaseVertex":
                GLSMDraws.glMultiDrawElementsBaseVertex((int) c[0], c[1], (int) c[2], c[3], (int) c[4], c[5]);
                return 0L;
            case "glPrimitiveRestartIndex":
                GLSMDraws.glPrimitiveRestartIndex((int) c[0]);
                return 0L;
            case "glFlush":
                GLSMDraws.glFlush();
                return 0L;
            case "glFinish":
                GLSMDraws.glFinish();
                return 0L;
            // program lifecycle and uniforms with integer/pointer payloads
            case "glAttachShader": case "glAttachObjectARB":
                GLSMPrograms.glAttachShader((int) c[0], (int) c[1]);
                return 0L;
            case "glDetachShader": case "glDetachObjectARB":
                GLSMPrograms.glDetachShader((int) c[0], (int) c[1]);
                return 0L;
            case "glCompileShader": case "glCompileShaderARB":
                GLSMPrograms.glCompileShader((int) c[0]);
                return 0L;
            case "glLinkProgram": case "glLinkProgramARB":
                GLSMPrograms.glLinkProgram((int) c[0]);
                return 0L;
            case "glValidateProgram": case "glValidateProgramARB":
                GLSMPrograms.glValidateProgram((int) c[0]);
                return 0L;
            case "glShaderSource": case "glShaderSourceARB":
                GLSMPrograms.nglShaderSource((int) c[0], (int) c[1], c[2], c[3]);
                return 0L;
            case "glUniform1i": case "glUniform1iARB":
                GLSMPrograms.glUniform1i((int) c[0], (int) c[1]);
                return 0L;
            case "glUniform2i": case "glUniform2iARB":
                GLSMPrograms.glUniform2i((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case "glUniform3i":
                GLSMPrograms.glUniform3i((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case "glUniformMatrix3fv": case "glUniformMatrix3fvARB":
                if (c[3] != 0L) {
                    GLSMPrograms.glUniformMatrix3((int) c[0], c[2] != 0L,
                            MemoryUtil.memFloatBuffer(c[3], (int) c[1] * 9));
                }
                return 0L;
            case "glUniformMatrix4fv": case "glUniformMatrix4fvARB":
                if (c[3] != 0L) {
                    GLSMPrograms.glUniformMatrix4((int) c[0], c[2] != 0L,
                            MemoryUtil.memFloatBuffer(c[3], (int) c[1] * 16));
                }
                return 0L;
            case "glBindAttribLocation":
                GLSMPrograms.glBindAttribLocation((int) c[0], (int) c[1], cString(c[2]));
                return 0L;
            case "glBindFragDataLocation":
                GLSMPrograms.glBindFragDataLocation((int) c[0], (int) c[1], cString(c[2]));
                return 0L;
            // vertex input
            case "glEnableVertexAttribArray": case "glEnableVertexAttribArrayARB":
                GLSMVertexInput.glEnableVertexAttribArray((int) c[0]);
                return 0L;
            case "glDisableVertexAttribArray": case "glDisableVertexAttribArrayARB":
                GLSMVertexInput.glDisableVertexAttribArray((int) c[0]);
                return 0L;
            case "glVertexAttribPointer": case "glVertexAttribPointerARB":
                GLSMVertexInput.glVertexAttribPointer((int) c[0], (int) c[1], (int) c[2], c[3] != 0L,
                        (int) c[4], c[5]);
                return 0L;
            case "glVertexAttribDivisor":
                GLSMVertexInput.glVertexAttribDivisor((int) c[0], (int) c[1]);
                return 0L;
            case "glTexCoordPointer":
                GLSMVertexInput.glTexCoordPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                return 0L;
            case "glColorPointer":
                GLSMVertexInput.glColorPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                return 0L;
            case "glNormalPointer":
                GLSMVertexInput.glNormalPointer((int) c[0], (int) c[1], c[2]);
                return 0L;
            case "glEdgeFlagPointer":
                GLSMVertexInput.glEdgeFlagPointer((int) c[0], c[1]);
                return 0L;
            case "glSecondaryColorPointer":
                GLSMVertexInput.glSecondaryColorPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                return 0L;
            case "glFogCoordPointer":
                GLSMVertexInput.glFogCoordPointer((int) c[0], (int) c[1], c[2]);
                return 0L;
            // buffers
            case "glBindBufferBase":
                GLSMBuffers.glBindBufferBase((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case "glBindBufferRange":
                GLSMBuffers.glBindBufferRange((int) c[0], (int) c[1], (int) c[2], c[3], c[4]);
                return 0L;
            case "glCopyBufferSubData":
                GLSMBuffers.glCopyBufferSubData((int) c[0], (int) c[1], c[2], c[3], c[4]);
                return 0L;
            case "glGetBufferSubData":
                if (c[3] != 0L) {
                    GLSMBuffers.glGetBufferSubData((int) c[0], c[1], bytes(c[3], (int) c[2]));
                }
                return 0L;
            case "glFlushMappedBufferRange":
                GLSMBuffers.glFlushMappedBufferRange((int) c[0], c[1], c[2]);
                return 0L;
            case "glWaitSync":
                GLSMBuffers.glWaitSync(c[0], (int) c[1], c[2]);
                return 0L;
            // framebuffers and renderbuffers
            case "glBindRenderbuffer": case "glBindRenderbufferEXT": case "glBindRenderbufferARB":
                GLSMFramebuffer.glBindRenderbuffer((int) c[0], (int) c[1]);
                return 0L;
            case "glFramebufferTexture2D": case "glFramebufferTexture2DEXT": case "glFramebufferTexture2DARB":
                GLSMFramebuffer.glFramebufferTexture2D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4]);
                return 0L;
            case "glFramebufferTexture1D":
                GLSMFramebuffer.glFramebufferTexture1D((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4]);
                return 0L;
            case "glFramebufferTexture3D":
                GLSMFramebuffer.glFramebufferTexture3D((int) c[0], (int) c[1], (int) c[2], (int) c[3],
                        (int) c[4], (int) c[5]);
                return 0L;
            case "glFramebufferTextureLayer":
                GLSMFramebuffer.glFramebufferTextureLayer((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4]);
                return 0L;
            case "glFramebufferTexture":
                GLSMFramebuffer.glFramebufferTexture2D((int) c[0], (int) c[1], 0x0DE1 /* GL_TEXTURE_2D */,
                        (int) c[2], (int) c[3]);
                return 0L;
            case "glFramebufferRenderbuffer": case "glFramebufferRenderbufferEXT": case "glFramebufferRenderbufferARB":
                GLSMFramebuffer.glFramebufferRenderbuffer((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case "glRenderbufferStorage": case "glRenderbufferStorageEXT": case "glRenderbufferStorageARB":
                GLSMFramebuffer.glRenderbufferStorage((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case "glRenderbufferStorageMultisample":
                GLSMFramebuffer.glRenderbufferStorageMultisample((int) c[0], (int) c[1], (int) c[2],
                        (int) c[3], (int) c[4]);
                return 0L;
            case "glDrawBuffer":
                GLSMFramebuffer.glDrawBuffer((int) c[0]);
                return 0L;
            case "glDrawBuffers":
                if (c[1] != 0L) {
                    GLSMFramebuffer.glDrawBuffers(MemoryUtil.memIntBuffer(c[1], (int) c[0]));
                }
                return 0L;
            case "glReadBuffer":
                GLSMFramebuffer.glReadBuffer((int) c[0]);
                return 0L;
            // textures
            case "glGenerateMipmap": case "glGenerateMipmapEXT": case "glGenerateMipmapARB":
                GLSMTextures.glGenerateMipmap((int) c[0]);
                return 0L;
            case "glBindSampler":
                GLSMTextures.glBindSampler((int) c[0], (int) c[1]);
                return 0L;
            case "glBindTextureUnit":
                GLSMTextures.glBindTextureUnit((int) c[0], (int) c[1]);
                return 0L;
            case "glGetTexImage":
                if (c[4] != 0L) {
                    int w = GLSMTextures.glGetTexLevelParameteri((int) c[0], (int) c[1], 0x1000);
                    int h = GLSMTextures.glGetTexLevelParameteri((int) c[0], (int) c[1], 0x1001);
                    GLSMTextures.glGetTexImage((int) c[0], (int) c[1], (int) c[2], (int) c[3],
                            bytes(c[4], texBytes((int) c[2], (int) c[3], Math.max(w, 1), Math.max(h, 1))));
                }
                return 0L;
            // fixed-function state with integer payloads
            case "glDepthMask":
                GLSMDepthStencilAlpha.glDepthMask(c[0] != 0L);
                return 0L;
            case "glDepthFunc":
                GLSMDepthStencilAlpha.glDepthFunc((int) c[0]);
                return 0L;
            case "glStencilFunc":
                GLSMDepthStencilAlpha.glStencilFunc((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case "glStencilFuncSeparate":
                GLSMDepthStencilAlpha.glStencilFuncSeparate((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case "glStencilOp":
                GLSMDepthStencilAlpha.glStencilOp((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case "glStencilOpSeparate":
                GLSMDepthStencilAlpha.glStencilOpSeparate((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case "glStencilMask":
                GLSMDepthStencilAlpha.glStencilMask((int) c[0]);
                return 0L;
            case "glStencilMaskSeparate":
                GLSMDepthStencilAlpha.glStencilMaskSeparate((int) c[0], (int) c[1]);
                return 0L;
            case "glLogicOp":
                GLSMBlend.glLogicOp((int) c[0]);
                return 0L;
            case "glPolygonMode":
                GLSMRasterizer.glPolygonMode((int) c[0], (int) c[1]);
                return 0L;
            case "glHint":
                GLSMRasterizer.glHint((int) c[0], (int) c[1]);
                return 0L;
            case "glShadeModel":
                GLSMRasterizer.glShadeModel((int) c[0]);
                return 0L;
            case "glClipPlane":
                if (c[1] != 0L) {
                    GLSMRasterizer.glClipPlane((int) c[0], MemoryUtil.memDoubleBuffer(c[1], 4));
                }
                return 0L;
            case "glLoadIdentity":
                GLSMMatrices.glLoadIdentity();
                return 0L;
            case "glPushMatrix":
                GLSMMatrices.glPushMatrix();
                return 0L;
            case "glPopMatrix":
                GLSMMatrices.glPopMatrix();
                return 0L;
            case "glPushAttrib":
                GLSMAttribStack.glPushAttrib((int) c[0]);
                return 0L;
            case "glPopAttrib":
                GLSMAttribStack.glPopAttrib();
                return 0L;
            case "glPushClientAttrib":
                GLSMAttribStack.glPushClientAttrib((int) c[0]);
                return 0L;
            case "glPopClientAttrib":
                GLSMAttribStack.glPopClientAttrib();
                return 0L;
            // queries
            case "glBeginQuery": case "glBeginQueryARB":
                GLSMQueries.glBeginQuery((int) c[0], (int) c[1]);
                return 0L;
            case "glEndQuery": case "glEndQueryARB":
                GLSMQueries.glEndQuery((int) c[0]);
                return 0L;
            default:
                // Counted arrival (the fn census carries it when enabled): float-carrying
                // immediate/state names, indexed queries (the converter holds no indexed form;
                // the old stub answered them as no-ops too) and the selection/feedback legacy
                // the old stub also answered with silence.
                return 0L;
        }
    }
}
