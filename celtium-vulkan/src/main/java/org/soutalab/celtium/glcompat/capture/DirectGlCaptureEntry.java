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
import org.soutalab.celtium.glcompat.backend.DirectGlExecutor;
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
import org.soutalab.celtium.vulkan.BufferStorageRecord;
import org.soutalab.celtium.vulkan.CaptureUploadContext;
import org.soutalab.celtium.vulkan.CeltiumBackends;
import org.soutalab.celtium.vulkan.FramebufferAttachmentTable;
import org.soutalab.celtium.vulkan.FramebufferBindings;
import org.soutalab.celtium.vulkan.FramebufferClearState;
import org.soutalab.celtium.vulkan.FramebufferViewportState;
import org.soutalab.celtium.vulkan.GlsmBufferState;
import org.soutalab.celtium.vulkan.GlsmClientArrayState;
import org.soutalab.celtium.vulkan.GlsmEnableState;
import org.soutalab.celtium.vulkan.GlsmImmediateState;
import org.soutalab.celtium.vulkan.GlsmAttribStacks;
import org.soutalab.celtium.vulkan.GlsmNameLedger;
import org.soutalab.celtium.vulkan.LegacyAttribSnapshot;
import org.soutalab.celtium.vulkan.ImmediateFogState;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;
import org.soutalab.celtium.vulkan.NativeImageBindings;
import org.soutalab.celtium.vulkan.NativeRasterState;
import org.soutalab.celtium.vulkan.NativeTextureBindings;
import org.soutalab.celtium.vulkan.PixelUnpackState;
import org.soutalab.celtium.vulkan.RenderObjectIds;
import org.soutalab.celtium.vulkan.TextureCaptureStore;
import org.soutalab.celtium.vulkan.VulkanModelQuadCapture;
import org.soutalab.celtium.vulkan.VulkanTextureRegistry;
import org.soutalab.celtium.vulkan.Lane2ImmediateRecorder;
import org.soutalab.celtium.vulkan.VulkanBackendAdapter;
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

    // The provider's own small records, ported from the monolith's dispatch fields (E4-b2): the
    // pixel pack binding (the unpack binding lives in PixelUnpackState) and the depth/alpha
    // function record the attribute stack and the hint's note doors write, whose query answers
    // fall back to them when no backend mirror exists.
    private volatile int pixelPackBuffer;
    private volatile boolean attribDepthWrites = true;
    private volatile int attribDepthFunc = 0x0201;
    private volatile int attribAlphaFunc = 0x0207;
    private volatile float attribAlphaReference;

    /** Vanilla-bracket suppression of the raw-framebuffer bind registration (see {@link #bindFramebuffer}). */
    private static int bindReportSuppression;

    // Capability constants the query router answers from (the monolith's GlStateQueries values).
    static final int TEXTURE_UNITS_PER_STAGE = 32;
    static final int TEXTURE_UNITS_COMBINED = 64;
    static final int COLOR_ATTACHMENT_SLOTS = 8;
    /** The index the enable record uses for {@code glDepthMask}, which GL has no capability for. */
    static final int GL_DEPTH_WRITEMASK_CAP = 0x0B72;

    private final GlTrampolines glTrampolines = new GlTrampolines(this::dispatch, this::legacyDispatch);
    private final DispatchThreadGate dispatchGate = new DispatchThreadGate();

    private DirectGlCaptureEntry() {
        // A new entrance is a new GL context: the registry's capture store starts empty for it.
        VulkanTextureRegistry.captureStore().clear();
        // The framebuffer bindings seat likewise starts from a fresh context's values.
        FramebufferBindings.current().reset();
        // The capture store's allocation producers act through the entrance's upload context.
        VulkanTextureRegistry.captureStore().uploadContext(CaptureUploadContext.current());
        // GL's initial GL_DEPTH_WRITEMASK is GL_TRUE. The record existed but started at zero, so a query before any
        // glDepthMask answered "writes disabled" - the opposite of the context's initial state.
        GlsmEnableState.current().set(GL_DEPTH_WRITEMASK_CAP, true);
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

    private static final java.util.concurrent.atomic.AtomicLong FONT_WINDOW_SUPPRESSED =
            new java.util.concurrent.atomic.AtomicLong();

    /** Counts one legacy begin suppressed by the open font window (DP-26). */
    public static void noteFontWindowSuppressed() {
        FONT_WINDOW_SUPPRESSED.incrementAndGet();
    }

    /** {@return how many legacy begins the font window has suppressed}. */
    public static long fontWindowSuppressed() {
        return FONT_WINDOW_SUPPRESSED.get();
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
                return sharedEnabled((int) c[0]) ? 1L : 0L;
            case SEM_ENABLE:
                setCapability((int) c[0], true);
                return 0L;
            case SEM_DISABLE:
                setCapability((int) c[0], false);
                return 0L;
            case SEM_GET_INTEGERV:
                getInts((int) c[0], c[1]);
                return 0L;
            case SEM_GET_FLOATV:
                getFloats(fn, (int) c[0], c[1]);
                return 0L;
            case SEM_GET_BOOLEANV:
                routeBooleans((int) c[0], c[1]);
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
                // The provider's framebuffer names are HAL-seat names, not converter names, so
                // the converter has no status to report for them; the shipped answer stands.
                return 0x8CD5L; // GL_FRAMEBUFFER_COMPLETE
            case SEM_SIGNALED:
                return 0x911AL; // GL_ALREADY_SIGNALED - the frame-slot fence is the wait (see the hint's fence doors)
            case SEM_MAP:
                return mapBuffer(fn, c);
            case SEM_UNMAP:
                return unmapBuffer(fn, c);
            case SEM_BUFFER_DATA:
                bufferData(fn, c);
                return 0L;
            case SEM_BUFFER_SUBDATA:
                bufferSubDataFor(bufferFor(fn, c), c[1], c[2], c[3]);
                if (c[3] != 0L && !fn.namedObject) {
                    GLSMBuffers.glBufferSubData((int) c[0], c[1], bytes(c[3], (int) c[2]));
                }
                return 0L;
            case SEM_STATUS:
                statusAnswer(fn, c);
                return 0L;
            case SEM_INFO_LOG:
                infoLogAnswer(fn, c);
                return 0L;
            case SEM_BIND_BUFFER:
                bindBuffer((int) c[0], (int) c[1]);
                GLSMBuffers.glBindBuffer((int) c[0], (int) c[1]);
                return 0L;
            case SEM_BIND_TEXTURE:
                NativeTextureBindings.current().bindTexture((int) c[0], (int) c[1]);
                if (Lane2StateMirror.current().register().activeProgramId() != 0) {
                    GLSMTextures.glBindTexture((int) c[0], (int) c[1]);
                }
                return 0L;
            case SEM_ACTIVE_TEXTURE:
                // The unit index, not the raw enum: the getter adds the GL_TEXTURE0 base back, and a stored enum made
                // every save/restore round trip add another base (measured as the 0x84C0 ladder in the game's own
                // active-unit reads), which sent every unit-keyed binding record to a key no reader looked up.
                NativeTextureBindings.current().activeUnit((int) c[0] - 0x84C0);
                if (Lane2StateMirror.current().register().activeProgramId() != 0) {
                    GLSMTextures.glActiveTexture((int) c[0]);
                }
                return 0L;
            case SEM_TEXIMAGE:
                CaptureUploadContext.current().recordExtent((int) c[0], 0, (int) c[2], (int) c[3], (int) c[4]);
                return 0L;
            case SEM_TEXIMAGE_PIXELS:
                texImagePixels(fn, c);
                return 0L;
            case SEM_TEXSUBIMAGE:
                VulkanTextureRegistry.captureStore().texSubImage(CaptureUploadContext.current(), c);
                return 0L;
            case SEM_COMPRESSED_TEXIMAGE:
                // The converter holds no compressed-image form; the old stub recorded the
                // extent only and stored no pixels either, so the call stays a counted arrival.
                return 0L;
            case SEM_PIXEL_STORE:
                if (fn.name.indexOf('f') == fn.name.length() - 1) {
                    PixelUnpackState.current().pixelStore((int) c[0], (int) f(c[1]));
                } else {
                    PixelUnpackState.current().pixelStore((int) c[0], (int) c[1]);
                }
                return 0L;
            case SEM_VIEWPORT:
                FramebufferViewportState.current().set((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case SEM_SCISSOR:
                NativeRasterState.current().noteScissorCall();
                NativeRasterState.current().scissor((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case SEM_CLEAR:
                recordClear((int) c[0]);
                return 0L;
            case SEM_CLEAR_COLOR:
                FramebufferClearState.current().clearColor(f(c[0]), f(c[1]), f(c[2]), f(c[3]));
                return 0L;
            case SEM_CLEAR_DEPTH:
                double depth = "glClearDepth".equals(fn.name) ? d(c[0]) : (double) f(c[0]);
                FramebufferClearState.current().clearDepth(Math.max(0.0D, Math.min(1.0D, depth)));
                return 0L;
            case SEM_LINE_WIDTH:
                GlsmImmediateState.current().lineWidth(f(c[0]));
                return 0L;
            case SEM_USE_PROGRAM:
                GLSMPrograms.glUseProgram((int) c[0]);
                Lane2StateMirror.current().register().setActiveProgramId((int) c[0]);
                return 0L;
            case SEM_NEW_LIST:
                GLSMDraws.glNewList((int) c[0], (int) c[1]);
                if (c[0] > 0) {
                    VulkanModelQuadCapture.listCompileStarted((int) c[0]);
                }
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
                // The client-array state's bound VAO swaps with the binding (the Hint door
                // keeps the same seat in step): the entry's attrib queries read it.
                GlsmClientArrayState.current().bindVertexArray((int) c[0]);
                GlsmClientArrayState.current().bindAttribs((int) c[0]);
                syncVertexPointerRecord();
                GLSMVertexInput.glBindVertexArray((int) c[0]);
                return 0L;
            case SEM_BIND_FRAMEBUFFER:
                bindFramebuffer((int) c[0], (int) c[1]);
                GLSMFramebuffer.glBindFramebuffer((int) c[0], (int) c[1]);
                return 0L;
            case SEM_BLIT_FRAMEBUFFER:
                noteBlitFramebuffer((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], (int) c[6], (int) c[7], (int) c[8], (int) c[9]);
                return 0L;
            case SEM_COPY_TEX_IMAGE:
                if (CaptureUploadContext.isDepthInternalFormat((int) c[2])) {
                    // Same refusal as the transformer's route: a depth copy is not read back.
                    CaptureUploadContext.current().recordExtent((int) c[0], (int) c[1], (int) c[2],
                            (int) c[5], (int) c[6]);
                    CaptureUploadContext.current().noteDepthCopySkipped();
                    return 0L;
                }
                CaptureUploadContext.current().noteTextureCopy(fn.name, (int) c[0], (int) c[1], 0, 0,
                        (int) c[3], (int) c[4], (int) c[5], (int) c[6]);
                return 0L;
            case SEM_COPY_TEX_SUB_IMAGE:
                CaptureUploadContext.current().noteTextureCopy(fn.name, (int) c[0], (int) c[1],
                        (int) c[2], (int) c[3], (int) c[4], (int) c[5], (int) c[6], (int) c[7]);
                return 0L;
            case SEM_COPY_IMAGE_SUB_DATA:
                // All 15 slots ride the dispatch (the table row declares them); the size tail
                // c[12..14] is the copy's width/height/depth - zeroing it made every copy a
                // no-op (E4-c repair; the monolith passed the slots through).
                CaptureUploadContext.current().copyImageSubData((int) c[0], (int) c[1], (int) c[2],
                        (int) c[3], (int) c[4], (int) c[5], (int) c[6], (int) c[7], (int) c[8],
                        (int) c[9], (int) c[10], (int) c[11], (int) c[12], (int) c[13], (int) c[14]);
                return 0L;
            case SEM_TEX_PARAM_I:
                CaptureUploadContext.current().setTextureParameterBound((int) c[0], (int) c[1],
                        "glTexParameterf".equals(fn.name) ? Math.round(Float.intBitsToFloat((int) c[2])) : (int) c[2],
                        paramSite());
                return 0L;
            case SEM_TEX_PARAM_IV:
                CaptureUploadContext.current().setTextureParameterBound((int) c[0], (int) c[1],
                        firstParamValue(fn, c[2]), paramSite());
                return 0L;
            case SEM_TEX_PARAM_I_DSA:
                CaptureUploadContext.current().setTextureParameter((int) c[0], (int) c[1],
                        "glTextureParameterf".equals(fn.name)
                                ? Math.round(Float.intBitsToFloat((int) c[2])) : (int) c[2],
                        paramSite());
                return 0L;
            case SEM_TEX_PARAM_IV_DSA:
                CaptureUploadContext.current().setTextureParameter((int) c[0], (int) c[1],
                        firstParamValue(fn, c[2]), paramSite());
                return 0L;
            case SEM_MULT_MATRIX:
                noteMatrixMultiplyArrival(fn);
                return 0L;
            case SEM_MATRIX_MODE: {
                final int mode = (int) c[0];
                // Direct to the Lane 2 mirror (the forwarding record that used to sit
                // between this seat and the mirror was deleted in E4-b2-tail); the fall-through to MODELVIEW for values outside
                // the three matrix modes, which the record applied on write, is kept here.
                Lane2StateMirror.current().setMatrixMode(
                        mode == 0x1700 || mode == 0x1701 || mode == 0x1702 ? mode : 0x1700);
                GLSMMatrices.glMatrixMode(mode);
                return 0L;
            }
            case SEM_CULL_FACE:
                GLSMRasterizer.glCullFace((int) c[0]);
                NativeRasterState.current().cullFace((int) c[0]);
                return 0L;
            case SEM_FRONT_FACE:
                GLSMRasterizer.glFrontFace((int) c[0]);
                NativeRasterState.current().frontFace((int) c[0]);
                return 0L;
            case SEM_POLYGON_OFFSET:
                GLSMRasterizer.glPolygonOffset(f(c[0]), f(c[1]));
                NativeRasterState.current().polygonOffset(f(c[0]), f(c[1]));
                return 0L;
            case SEM_COLOR_MASK:
                GLStateManager.glColorMask(c[0] != 0L, c[1] != 0L, c[2] != 0L, c[3] != 0L);
                // E4-c seat repair: the entry's own queries and the attrib snapshot read the
                // native raster seat (as the monolith's dispatch wrote it); the GLSM write above
                // feeds the executor. Both seats are written, as the scissor case already does.
                NativeRasterState.current().colorMask(((int) c[0] != 0 ? 0x1 : 0) | ((int) c[1] != 0 ? 0x2 : 0)
                        | ((int) c[2] != 0 ? 0x4 : 0) | ((int) c[3] != 0 ? 0x8 : 0));
                return 0L;
            case SEM_BLEND_FUNC:
                GLSMBlend.glBlendFunc((int) c[0], (int) c[1]);
                NativeRasterState.current().blendFunc((int) c[0], (int) c[1], (int) c[0], (int) c[1]);
                return 0L;
            case SEM_BLEND_FUNC_SEPARATE:
                GLSMBlend.glBlendFuncSeparate((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                NativeRasterState.current().blendFunc((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case SEM_BLEND_EQUATION:
                GLSMBlend.glBlendEquation((int) c[0]);
                NativeRasterState.current().blendEquation((int) c[0], (int) c[0]);
                return 0L;
            case SEM_BLEND_EQUATION_SEPARATE:
                GLSMBlend.glBlendEquationSeparate((int) c[0], (int) c[1]);
                NativeRasterState.current().blendEquation((int) c[0], (int) c[1]);
                return 0L;
            case SEM_BEGIN:
                GLSMDraws.glBegin((int) c[0]);
                return 0L;
            case SEM_END:
                GLSMDraws.glEnd();
                return 0L;
            case SEM_READ_PIXELS:
                serveReadPixels((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], c[6]);
                return 0L;
            case SEM_FOG_I:
                org.soutalab.celtium.vulkan.ImmediateFogState.current().parameter((int) c[0], (int) c[1]);
                return 0L;
            case SEM_FOG_F:
                org.soutalab.celtium.vulkan.ImmediateFogState.current().parameter((int) c[0], f(c[1]));
                return 0L;
            case SEM_FOG_FV:
            case SEM_FOG_IV:
                if (c[1] != 0L) {
                    org.soutalab.celtium.vulkan.ImmediateFogState fog =
                            org.soutalab.celtium.vulkan.ImmediateFogState.current();
                    boolean ints = fn.semantics == SEM_FOG_IV;
                    if ((int) c[0] == 0x0B66) {
                        // Integer fog colours are normalized signed integers, unlike the enum/scalar parameters.
                        fog.color(ints ? (float) Math.max(-1.0, MemoryUtil.memGetInt(c[1]) / 2147483647.0) : MemoryUtil.memGetFloat(c[1]),
                                ints ? (float) Math.max(-1.0, MemoryUtil.memGetInt(c[1] + 4) / 2147483647.0) : MemoryUtil.memGetFloat(c[1] + 4),
                                ints ? (float) Math.max(-1.0, MemoryUtil.memGetInt(c[1] + 8) / 2147483647.0) : MemoryUtil.memGetFloat(c[1] + 8),
                                ints ? (float) Math.max(-1.0, MemoryUtil.memGetInt(c[1] + 12) / 2147483647.0) : MemoryUtil.memGetFloat(c[1] + 12));
                    } else fog.parameter((int) c[0], ints ? MemoryUtil.memGetInt(c[1]) : MemoryUtil.memGetFloat(c[1]));
                }
                return 0L;
            case SEM_TEX_ENV_I:
                org.soutalab.celtium.vulkan.NativeTextureEnvironment.current()
                        .parameter((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case SEM_TEX_ENV_F:
                // An A_FLOAT slot carries the raw IEEE-754 bits; decode it the way the int form
                // records, so both spellings land on the same value (the monolith's BH-34 note).
                org.soutalab.celtium.vulkan.NativeTextureEnvironment.current()
                        .parameter((int) c[0], (int) c[1], Math.round(Float.intBitsToFloat((int) c[2])));
                return 0L;
            case SEM_TEX_ENV_FV:
                texEnvFloats((int) c[0], (int) c[1], c[2]);
                return 0L;
            case SEM_GET_TEX_ENV_I:
                if (c[2] != 0L) {
                    MemoryUtil.memPutInt(c[2], org.soutalab.celtium.vulkan.NativeTextureEnvironment
                            .current().activeParameter((int) c[0], (int) c[1]));
                }
                return 0L;
            case SEM_GET_TEX_ENV_FV:
                getTexEnvFloats((int) c[0], (int) c[1], c[2]);
                return 0L;
            case SEM_VERTEX_POINTER:
                vertexPointer(fn, c);
                return 0L;
            case SEM_DRAW_ARRAYS:
                GLSMDraws.glDrawArrays((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case SEM_BIND_IMAGE_TEXTURE:
                NativeImageBindings.current().bind((int) c[0], (int) c[1], (int) c[2],
                        c[3] != 0L, (int) c[4], (int) c[5], (int) c[6]);
                GLSMTextures.glBindImageTexture((int) c[0], (int) c[1], (int) c[2], c[3] != 0L,
                        (int) c[4], (int) c[5], (int) c[6]);
                return 0L;
            default:
                return 0L;
        }
    }

    // ---- seat writes the dispatch shares with the hint doors --------------------------------
    //
    // These are the monolith's door bodies with the monolith removed: each writes the seat that
    // owns the record (family table, adjudication §2), and the hint doors call the static forms
    // so a transformed call and a provider call land in the same place.

    /** The buffer binding record: the array seat, the two pixel seats, or the flat binding map. */
    private void bindBuffer(int target, int name) {
        if (target == 0x8892) { // GL_ARRAY_BUFFER
            NativeGeometryBuffer.bindId(name);
        } else if (target == 0x88EB) { // GL_PIXEL_PACK_BUFFER
            this.pixelPackBuffer = name;
            return;
        } else if (target == 0x88EC) { // GL_PIXEL_UNPACK_BUFFER
            PixelUnpackState.current().pixelUnpackBuffer(name);
            return;
        }
        GlsmBufferState.current().bind(target, name);
    }

    /** The framebuffer binding record plus the raw-bind registration with the renderer. */
    private void bindFramebuffer(int target, int name) {
        FramebufferBindings.current().bind(target, name);
        // The registration the renderer makes from a raw bind (identity, records, pair, shared-depth borrow)
        // is triggered here rather than in the hint's report: every raw bind lands in this record, including
        // the ones GLSM's state cache and the vanilla-bracket suppression kept the hint's report from ever
        // seeing for the shadow framebuffers (legs 14/15: served native-depth copies = 0). The hint suppresses
        // the trigger while a vanilla Framebuffer method runs, because those binds are the object's own and the
        // mixin reports them by object identity.
        if (target != 0x8CA8 && bindReportSuppression == 0) {
            Object backend = CeltiumBackends.current();
            Lane2ImmediateRecorder vulkan = vulkanBackend();
            if (vulkan != null) {
                vulkan.celtium$registerRawFramebuffer(name, true);
            }
        }
    }

    /** Suppresses the bind registration for the duration of one vanilla-bracketed bind. */
    public static void pushBindReportSuppression() {
        bindReportSuppression++;
    }

    /** Ends one {@link #pushBindReportSuppression}. */
    public static void popBindReportSuppression() {
        if (bindReportSuppression > 0) bindReportSuppression--;
    }

    /** Registers a raw framebuffer with the renderer without a bind (the hint's unbind-on-delete path). */
    public static void registerRawFramebuffer(int framebuffer) {
        Object backend = CeltiumBackends.current();
        Lane2ImmediateRecorder vulkan = vulkanBackend();
            if (vulkan != null) {
            vulkan.celtium$registerRawFramebuffer(framebuffer, true);
        }
    }

    /** {@return how many framebuffer binds the seat has recorded}. */
    public static long framebufferBindSerial() {
        return FramebufferBindings.current().bindSerial();
    }

    /** The bound pixel pack buffer record (the unpack half lives in {@link PixelUnpackState}). */
    public static int pixelPackBuffer() {
        DirectGlCaptureEntry entry = installed;
        return entry == null ? 0 : entry.pixelPackBuffer;
    }

    /** The clear latch: the HAL clear seat issues the mask against the bound draw framebuffer. */
    private void recordClear(int mask) {
        mask = FramebufferClearState.effectiveClearMask(mask);
        FramebufferClearState.current().issue(mask, FramebufferBindings.current().drawFramebuffer(),
                FramebufferBindings.current().drawIsMain());
        if (FramebufferBindings.current().drawIsMain()) {
            // The main framebuffer's clear can be carried by the draw queue instead of by the latch: the backend
            // attached it to the last queued group of the frame image when the ordering flush was asked for
            // (Lane2ImmediateRecorder.flushBeforeClear), and this consumes the pending mask so the latch and the
            // window acquire do not apply the same clear a second time. A mask the backend did not carry is left
            // pending, so the frame machinery still applies it at the next pass begin.
            try {
                Object backend = CeltiumBackends.current();
                Lane2ImmediateRecorder vulkan = vulkanBackend();
            if (vulkan != null) {
                    vulkan.celtium$takeCarriedMainClear(mask);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // The pending mask stays set, so the next bind's latch still carries the clear as before.
            }
        } else {
            // A non-main framebuffer's clear has no other carrier: the latch only runs on the next vanilla
            // bindFramebuffer, and a readback that binds by raw GL name never trips it - measured as a
            // framebufferClear() whose drawn quad survived through glReadPixels. Recording it at the clear call
            // keeps it between the draws before it and the draws after it, which is GL's own ordering; the
            // main-framebuffer case keeps the latch path, which the frame machinery already owns.
            try {
                Object backend = CeltiumBackends.current();
                Lane2ImmediateRecorder vulkan = vulkanBackend();
            if (vulkan != null) {
                    vulkan.celtium$gameClear(mask, FramebufferBindings.current().drawFramebuffer());
                }
            } catch (RuntimeException | LinkageError ignored) {
                // The pending mask stays set, so the next bind's latch still carries the clear as before.
            }
        }
    }

    /**
     * Records one capability enable/disable, from the dispatch or the transformer's route.
     * <p>
     * The capabilities whose state the immediate pipeline reads go to their mirrors (fog, the
     * backend's depth/alpha/lighting switches, the raster seat, the texture-unit record); every
     * capability also lands in the flat enable record the queries answer from.
     */
    public static void setCapability(int capability, boolean enabled) {
        if (capability < 0 || capability >= 0x10000) {
            return;
        }
        switch (capability) {
            case 0x0B60: // GL_FOG
                ImmediateFogState.current().enabled(enabled);
                break;
            case 0x0B71: { // GL_DEPTH_TEST
                org.soutalab.celtium.vulkan.CeltiumBackend backend = CeltiumBackends.current();
                if (backend != null) backend.setDepthTest(enabled);
                break;
            }
            case 0x0B86: { // GL_ALPHA_TEST
                org.soutalab.celtium.vulkan.CeltiumBackend backend = CeltiumBackends.current();
                if (backend != null) backend.setAlphaTestEnabled(enabled);
                break;
            }
            case 0x0B50: { // GL_LIGHTING
                org.soutalab.celtium.vulkan.CeltiumBackend backend = CeltiumBackends.current();
                if (backend != null) backend.setLightingEnabled(enabled);
                break;
            }
            case 0x0BE2: NativeRasterState.current().blendEnabled(enabled); break; // GL_BLEND
            case 0x0B44: NativeRasterState.current().cullEnabled(enabled); break; // GL_CULL_FACE
            case 0x0C11: NativeRasterState.current().noteScissorToggle();
                NativeRasterState.current().scissorTest(enabled); break; // GL_SCISSOR_TEST
            case 0x8037: NativeRasterState.current().polygonOffsetFillEnabled(enabled); break; // GL_POLYGON_OFFSET_FILL
            case 0x0DE1: NativeTextureBindings.current().enabled2d(enabled); break; // GL_TEXTURE_2D
            default: break;
        }
        GlsmEnableState.current().set(capability, enabled);
    }

    /** {@return whether a capability is enabled}, answered from the same records {@link #setCapability} writes. */
    public static boolean sharedEnabled(int cap) {
        switch (cap) {
            case 0x0B60: return ImmediateFogState.current().enabled(); // GL_FOG
            case 0x0BE2: return NativeRasterState.current().blendEnabled(); // GL_BLEND
            case 0x0B44: return NativeRasterState.current().cullEnabled(); // GL_CULL_FACE
            case 0x0C11: return NativeRasterState.current().scissorTest(); // GL_SCISSOR_TEST
            case 0x8037: return NativeRasterState.current().polygonOffsetFillEnabled(); // GL_POLYGON_OFFSET_FILL
            case 0x0DE1: { // GL_TEXTURE_2D
                NativeTextureBindings textures = NativeTextureBindings.current();
                return textures.enabled2d(textures.activeUnit());
            }
            // The mirror is the authority for these three: vanilla's GlStateManager cache can skip the native
            // glEnable/glDisable entirely, so a private enable record never sees the change - the model state
            // probe measured glIsEnabled(GL_DEPTH_TEST)=false against a captured depthTest=1.
            case 0x0B71: { // GL_DEPTH_TEST
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$depthTestEnabled() : GlsmEnableState.current().enabled(0x0B71);
            }
            case 0x0B86: { // GL_ALPHA_TEST
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$alphaTestEnabled() : GlsmEnableState.current().enabled(0x0B86);
            }
            case 0x0B50: { // GL_LIGHTING
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$lightingEnabled() : GlsmEnableState.current().enabled(0x0B50);
            }
            default: break;
        }
        return GlsmEnableState.current().enabled(cap);
    }

    /** {@return the live Vulkan backend, or null when another renderer (or none) is installed. */
    static Lane2ImmediateRecorder vulkanBackend() {
        Object backend = CeltiumBackends.current();
        return backend instanceof VulkanBackendAdapter adapter ? adapter.funnel() : null;
    }

    /** The stub's record of a raw or managed {@code glDepthMask}, for the push snapshot and the query fallback. */
    public static void noteDepthMask(boolean mask) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.attribDepthWrites = mask;
        }
        GlsmEnableState.current().set(GL_DEPTH_WRITEMASK_CAP, mask);
    }

    /** The record of a raw {@code glDepthFunc}, for the query fallback when no backend mirror exists. */
    public static void noteDepthFunc(int func) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.attribDepthFunc = func;
        }
    }

    /** The record of a raw {@code glAlphaFunc}, for the query fallback when no backend mirror exists. */
    public static void noteAlphaFunc(int func, float reference) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.attribAlphaFunc = func;
            entry.attribAlphaReference = reference;
        }
    }

    /**
     * Writes an object's info log for the transformer's LWJGL3 route: a program whose recorded
     * link failed carries that failure as its log; anything else carries the empty log.
     *
     * @return the number of bytes written, excluding any terminator
     */
    public static int writeInfoLog(int object, int maxLength, long log) {
        if (log == 0L || maxLength <= 0) {
            return 0;
        }
        String text = GLSMPrograms.glIsProgram(object)
                ? GLSMPrograms.glGetProgramInfoLog(object)
                : GLSMPrograms.glIsShader(object) ? GLSMPrograms.glGetShaderInfoLog(object) : "";
        if (text == null) {
            text = "";
        }
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, maxLength);
        for (int i = 0; i < count; i++) {
            MemoryUtil.memPutByte(log + i, bytes[i]);
        }
        return count;
    }

    /**
     * The transformer's {@code glUseProgram} route: the converter applies the program (family
     * 5's seat) and the register publishes it, which is the read the backend's refusal gate and
     * the bound-texture dual route key on.
     */
    public static void useProgram(int program) {
        DirectGlDispatcher.enterLane3();
        try {
            GLSMPrograms.glUseProgram(program);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
        Lane2StateMirror.current().register().setActiveProgramId(program);
    }

    // Texture-unit query census: one line per pname for the life of this JVM.
    private static final java.util.Set<Integer> TEXUNIT_ANSWER_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> IMGUNIT_ANSWER_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Hands one texture-unit answer to the census (one line per pname). */
    public static void noteTextureUnitAnswer(int pname, int value) {
        if (pname != 0x8872 && pname != 0x8B4C && pname != 0x8B4D && pname != 0x8F39) {
            return;
        }
        if (TEXUNIT_ANSWER_LOGGED.add(Integer.valueOf(pname))) {
            System.out.println("[Celtium] CELTIUM_TEXUNIT_ANSWER pname=0x" + Integer.toHexString(pname)
                    + " value=" + value);
        }
    }

    /** Hands one image-unit answer to the census (one line for the sole pname). */
    static void noteImageUnitAnswer(int pname, int value) {
        if (pname == 0x8F38 && IMGUNIT_ANSWER_LOGGED.add(Integer.valueOf(pname))) {
            System.out.println("[Celtium] CELTIUM_IMGUNIT_ANSWER pname=0x8f38 value=" + value);
        }
    }


    // ---- framebuffer blit / readback notes, ported from the monolith (E4-b2) -----------------
    private int blitDepthRequests;
    private int blitUnservedRequests;
    private int readPixelsEntries;
    private int readPixelsCalls;
    private int pixelBufferRefusals;
    private static int readPixelsRefusals;
    private static final java.util.Set<String> BLIT_DEPTH_SHAPES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> BLIT_UNSERVED_SHAPES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final java.util.concurrent.atomic.AtomicLong MATRIX_MULTIPLY_ARRIVALS = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong MATRIX_MULTIPLY_APPLIED = new java.util.concurrent.atomic.AtomicLong();

    private void noteUnservedBlit(int srcX0, int srcY0, int srcX1, int srcY1,
            int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        this.blitUnservedRequests++;
        String bits = (mask & 0x00004000) != 0
                ? ((mask & 0x00000400) != 0 ? "color+stencil" : "color")
                : "stencil";
        String key = bits + "/" + (srcX1 - srcX0) + "x" + (srcY1 - srcY0)
                + "->" + (dstX1 - dstX0) + "x" + (dstY1 - dstY0)
                + "/filter=0x" + Integer.toHexString(filter)
                + "/readFbo=" + FramebufferBindings.current().readFramebuffer() + "/drawFbo=" + FramebufferBindings.current().drawFramebuffer();
        if (BLIT_UNSERVED_SHAPES.size() < 8 && BLIT_UNSERVED_SHAPES.add(key)) {
            System.out.println("[Celtium] CELTIUM_BLIT_UNSERVED glBlitFramebuffer bits=" + bits
                    + " source=" + srcX0 + "," + srcY0 + " " + (srcX1 - srcX0) + "x" + (srcY1 - srcY0)
                    + " dest=" + dstX0 + "," + dstY0 + " " + (dstX1 - dstX0) + "x" + (dstY1 - dstY0)
                    + " readFbo=" + FramebufferBindings.current().readFramebuffer() + " drawFbo=" + FramebufferBindings.current().drawFramebuffer()
                    + " requests=" + this.blitUnservedRequests
                    + " site=[" + callSite(8) + "]");
        }
    }

    private void noteBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1,
            int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if ((mask & (0x00004000 | 0x00000400)) != 0) {
            noteUnservedBlit(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
        }
        if ((mask & 0x00000100) == 0) {
            return;
        }
        this.blitDepthRequests++;
        int sourceTexture = FramebufferAttachmentTable.current().depthTexture(FramebufferBindings.current().readFramebuffer());
        int destTexture = FramebufferAttachmentTable.current().depthTexture(FramebufferBindings.current().drawFramebuffer());
        int width = srcX1 - srcX0;
        int height = srcY1 - srcY0;
        String servedFrom;
        if (sourceTexture == 0 || destTexture == 0) {
            servedFrom = "refused-depth-unrecorded: readFbo=" + FramebufferBindings.current().readFramebuffer() + " drawFbo=" + FramebufferBindings.current().drawFramebuffer()
                    + " sourceTexture=" + sourceTexture + " destTexture=" + destTexture;
        } else if (width <= 0 || height <= 0 || dstX1 - dstX0 != width || dstY1 - dstY0 != height
                || filter != 0x2600) {
            servedFrom = "refused-depth-shape: src=" + width + "x" + height + " dst=" + (dstX1 - dstX0) + "x"
                    + (dstY1 - dstY0) + " filter=0x" + Integer.toHexString(filter)
                    + " (only an equal-extent NEAREST depth blit is a copy)";
        } else {
            try {
                Object backend = org.soutalab.celtium.vulkan.CeltiumBackends.current();
                servedFrom = vulkanBackend() != null
                        ? vulkanBackend().celtium$serveDepthCopy(sourceTexture, destTexture, srcX0, srcY0, width, height,
                                dstX0, dstY0)
                        : "refused-no-vulkan-backend";
            } catch (RuntimeException | LinkageError error) {
                servedFrom = "refused-serve-failed: " + error;
            }
            if ("served".equals(servedFrom)) {
                servedFrom = "native-depth";
            }
        }
        String key = width + "x" + height + "/readFbo=" + FramebufferBindings.current().readFramebuffer() + "/drawFbo=" + FramebufferBindings.current().drawFramebuffer()
                + "/" + servedFrom;
        if (BLIT_DEPTH_SHAPES.size() < 8 && BLIT_DEPTH_SHAPES.add(key)) {
            System.out.println("[Celtium] CELTIUM_BLIT_DEPTH glBlitFramebuffer"
                    + " source=" + srcX0 + "," + srcY0 + " " + width + "x" + height
                    + " dest=" + dstX0 + "," + dstY0
                    + " readFbo=" + FramebufferBindings.current().readFramebuffer() + " drawFbo=" + FramebufferBindings.current().drawFramebuffer()
                    + " servedFrom=" + servedFrom + " requests=" + this.blitDepthRequests
                    + " site=[" + callSite(8) + "]");
        }
    }

    private void serveReadPixels(int x, int y, int width, int height, int format, int type, long address) {
        if (this.readPixelsEntries < 6) {
            this.readPixelsEntries++;
            System.out.println("[Celtium] CELTIUM_READ_PIXELS_ENTRY readFbo=" + FramebufferBindings.current().readFramebuffer()
                    + " rect=" + x + "," + y + " " + width + "x" + height
                    + " format=0x" + Integer.toHexString(format) + " type=0x" + Integer.toHexString(type));
        }
        String pboRefusal = TextureCaptureStore.pixelBufferRefusal(this.pixelPackBuffer, address, "glReadPixels");
        if (pboRefusal != null) {
            if (this.pixelBufferRefusals < 8) {
                this.pixelBufferRefusals++;
                System.out.println("[Celtium] CELTIUM_PBO_REFUSED " + pboRefusal);
            }
            this.readPixelsCalls++;
            return;
        }
        try {
            Object backend = org.soutalab.celtium.vulkan.CeltiumBackends.current();
            boolean served = vulkanBackend() != null
                    && vulkanBackend().celtium$readPixels(FramebufferBindings.current().readFramebuffer(), x, y, width, height, format, type, address);
            if (!served && readPixelsRefusals < 8) {
                readPixelsRefusals++;
                System.out.println("[Celtium] CELTIUM_READ_PIXELS refused readFbo=" + FramebufferBindings.current().readFramebuffer()
                        + " rect=" + x + "," + y + " " + width + "x" + height
                        + " format=0x" + Integer.toHexString(format) + " type=0x" + Integer.toHexString(type)
                        + " backend=" + (backend == null ? "none" : backend.getClass().getSimpleName()));
            }
            this.readPixelsCalls++;
        } catch (RuntimeException | LinkageError error) {
            if (readPixelsRefusals < 8) {
                readPixelsRefusals++;
                System.out.println("[Celtium] CELTIUM_READ_PIXELS failed: " + error);
            }
        }
    }

    static String callSite(int limit) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        int taken = 0;
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            if (name.startsWith("org.soutalab.celtium") || name.startsWith("org.taumc.celeritas")
                    || name.startsWith("org.spongepowered") || name.contains("$celtium$")) {
                continue;
            }
            if (taken > 0) {
                frames.append(" <- ");
            }
            frames.append(shortSiteName(name)).append('.').append(frame.getMethodName()).append(':')
                    .append(frame.getLineNumber());
            if (++taken >= limit) {
                break;
            }
        }
        return frames.toString();
    }

    private static String shortSiteName(String className) {
        String shortened = className;
        for (String prefix : new String[] { "org.lwjgl.opengl.", "org.lwjgl.util.", "org.lwjgl.", "org.lwjglx.",
                "net.minecraftforge.", "net.minecraft." }) {
            if (shortened.startsWith(prefix)) {
                shortened = shortened.substring(prefix.length());
            }
        }
        return shortened;
    }

    /** The pointer form of the tex-env write: the colour pname reads four floats, the rest one. */
    private static void texEnvFloats(int target, int pname, long values) {
        if (values == 0L || target != 0x2300) { // GL_TEXTURE_ENV
            return;
        }
        org.soutalab.celtium.vulkan.NativeTextureEnvironment env =
                org.soutalab.celtium.vulkan.NativeTextureEnvironment.current();
        if (pname == 0x2201) { // GL_TEXTURE_ENV_COLOR
            env.color(target, pname, MemoryUtil.memGetFloat(values), MemoryUtil.memGetFloat(values + 4L),
                    MemoryUtil.memGetFloat(values + 8L), MemoryUtil.memGetFloat(values + 12L));
        } else {
            env.parameter(target, pname, Math.round(MemoryUtil.memGetFloat(values)));
        }
    }

    /** The float form of the tex-env read. */
    private static void getTexEnvFloats(int target, int pname, long out) {
        if (out == 0L || target != 0x2300) { // GL_TEXTURE_ENV
            return;
        }
        org.soutalab.celtium.vulkan.NativeTextureEnvironment env =
                org.soutalab.celtium.vulkan.NativeTextureEnvironment.current();
        if (pname == 0x2201) { // GL_TEXTURE_ENV_COLOR
            for (int i = 0; i < 4; i++) {
                MemoryUtil.memPutFloat(out + (long) i * 4L, env.colorComponent(i));
            }
        } else {
            MemoryUtil.memPutFloat(out, env.activeParameter(target, pname));
        }
    }

    /** The transformer's {@code glMapBuffer}/{@code glMapBufferRange} route; 0 is GL's NULL. */
    public static long mapBufferRoute(int target, boolean range, long offset, long length, int access) {
        DirectGlCaptureEntry entry = installed;
        return entry == null ? 0L : BufferStorageRecord.current().mapBufferFor(
                entry.boundBuffer(target), range, offset, length, access);
    }

    /** The DSA mapping form: the buffer is named, not the bound target. */
    public static long mapNamedBufferRoute(int name, boolean range, long offset, long length, int access) {
        return BufferStorageRecord.current().mapBufferFor(name, range, offset, length, access);
    }

    /** The transformer's {@code glUnmapBuffer} route. */
    public static boolean unmapBufferRoute(int target) {
        DirectGlCaptureEntry entry = installed;
        return entry != null && BufferStorageRecord.unmap(entry.boundBuffer(target));
    }

    /** The DSA unmap form. */
    public static boolean unmapNamedBufferRoute(int name) {
        return BufferStorageRecord.unmap(name);
    }

    /** The transformer's {@code glBufferData} route: mirrors the bound buffer's storage. */
    public static void bufferDataRoute(int target, long size, long data) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            BufferStorageRecord.current().bufferDataFor(entry.boundBuffer(target), size, data);
        }
    }

    /** The transformer's named buffer-parameter query: GL_BUFFER_SIZE from the record, else 0. */
    public static void getNamedBufferParameter(int name, int pname, long out, boolean wide) {
        if (out == 0L) {
            return;
        }
        long size = 0L;
        try {
            NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
            if (nativeBuffer != null) {
                size = nativeBuffer.byteSize();
            } else {
                ByteBuffer storage = GlsmBufferState.current().storage(name);
                size = storage == null ? 0L
                        : BufferStorageRecord.current().logicalSizeOf(name, storage.capacity());
            }
        } catch (RuntimeException error) {
            size = 0L;
        }
        long value = pname == 0x8764 ? size : 0L; // GL_BUFFER_SIZE
        if (wide) {
            MemoryUtil.memPutLong(out, value);
        } else {
            MemoryUtil.memPutInt(out, (int) value);
        }
    }

    /** The recorded level parameter of the texture a target resolves to (the stub's glGetTexLevelParameter answer). */
    private int texLevelParam(int target, int level, int pname) {
        int name = CaptureUploadContext.current().uploadTargetName(target);
        int[] dims = VulkanTextureRegistry.captureStore().texLevels()
                .get(CaptureUploadContext.texLevelKey(name, level));
        if (dims == null) {
            return 0;
        }
        switch (pname) {
            case 0x1000: // GL_TEXTURE_WIDTH
                return dims[0];
            case 0x1001: // GL_TEXTURE_HEIGHT
                return dims[1];
            case 0x1003: // GL_TEXTURE_INTERNAL_FORMAT
                return dims[2];
            case 0x1005: // GL_TEXTURE_BORDER
                return 0;
            // GL_TEXTURE_RED/GREEN/BLUE/ALPHA/LUMINANCE/INTENSITY_SIZE.
            case 0x805C:
            case 0x805D:
            case 0x805E:
            case 0x805F:
            case 0x8060:
            case 0x8061:
                return 8;
            default:
                return 0;
        }
    }

    /**
     * {@code glClearBufferData/SubData} and their named forms, for the zero fill only: a null
     * clear value zeroes the bytes whatever the internal format's component layout is. A
     * non-zero value stays untouched.
     */
    public static void clearBufferRoute(int targetOrName, boolean named, long offset, long size, long data) {
        DirectGlCaptureEntry entry = installed;
        if (entry == null || data != 0L) {
            return;
        }
        int name = named ? targetOrName : entry.boundBuffer(targetOrName);
        if (name == 0) {
            return;
        }
        long capacity;
        try {
            NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
            if (nativeBuffer != null) {
                capacity = nativeBuffer.byteSize();
                if (offset < 0L || offset >= capacity) {
                    return;
                }
                if (size <= 0L) {
                    size = capacity - offset;
                }
                if (BufferStorageRecord.mapRangeOffset(offset, size, capacity) < 0L) {
                    return;
                }
                byte[] zeros = new byte[(int) Math.min(size, 1 << 16)];
                long written = 0L;
                while (written < size) {
                    int chunk = (int) Math.min(size - written, zeros.length);
                    nativeBuffer.write(offset + written, java.nio.ByteBuffer.wrap(zeros, 0, chunk));
                    written += chunk;
                }
                return;
            }
        } catch (RuntimeException error) {
            return;
        }
        ByteBuffer storage = GlsmBufferState.current().storage(name);
        if (storage == null) {
            return;
        }
        capacity = BufferStorageRecord.current().logicalSizeOf(name, storage.capacity());
        if (offset < 0L || offset >= capacity) {
            return;
        }
        if (size <= 0L) {
            size = capacity - offset;
        }
        if (BufferStorageRecord.mapRangeOffset(offset, size, capacity) < 0L) {
            return;
        }
        MemoryUtil.memSet(MemoryUtil.memAddress(storage) + offset, 0, size);
    }

    /** The transformer's {@code glClear} route: the clear latch on the entry. */
    public static void clearRoute(int mask) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.recordClear(mask);
        }
    }

    /** Reads a byte range out of a buffer mirror into the caller's address (bound form). */
    public static void readBufferRoute(int target, long offset, long size, long out) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            readBuffer(entry.boundBuffer(target), offset, size, out);
        }
    }

    /** The DSA spelling of the buffer readback. */
    public static void readNamedBufferRoute(int name, long offset, long size, long out) {
        readBuffer(name, offset, size, out);
    }

    private static void readBuffer(int name, long offset, long size, long out) {
        if (name == 0 || out == 0L || offset < 0L || size <= 0L) {
            return;
        }
        ByteBuffer view;
        long capacity;
        try {
            NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
            view = nativeBuffer != null ? nativeBuffer.compatibilityView() : GlsmBufferState.current().storage(name);
            if (view == null) {
                return;
            }
            capacity = nativeBuffer != null ? view.capacity()
                    : BufferStorageRecord.current().logicalSizeOf(name, view.capacity());
        } catch (RuntimeException error) {
            return;
        }
        if (BufferStorageRecord.mapRangeOffset(offset, size, capacity) < 0L) {
            return;
        }
        MemoryUtil.memCopy(MemoryUtil.memAddress(view) + offset, out, size);
    }

    /** Copies a byte range between the two bound buffer mirrors. */
    public static void copyBufferRoute(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            copyBuffer(entry.boundBuffer(readTarget), entry.boundBuffer(writeTarget), readOffset, writeOffset, size);
        }
    }

    /** The DSA spelling of the buffer copy. */
    public static void copyNamedBufferRoute(int readBuffer, int writeBuffer, long readOffset, long writeOffset,
            long size) {
        copyBuffer(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }

    private static void copyBuffer(int readName, int writeName, long readOffset, long writeOffset, long size) {
        if (readName == 0 || writeName == 0 || readOffset < 0L || writeOffset < 0L || size <= 0L) {
            return;
        }
        ByteBuffer source;
        long readCapacity;
        try {
            NativeGeometryBuffer nativeRead = NativeGeometryBuffer.find(readName);
            source = nativeRead != null ? nativeRead.compatibilityView() : GlsmBufferState.current().storage(readName);
            if (source == null) {
                return;
            }
            readCapacity = nativeRead != null ? source.capacity()
                    : BufferStorageRecord.current().logicalSizeOf(readName, source.capacity());
        } catch (RuntimeException error) {
            return;
        }
        if (BufferStorageRecord.mapRangeOffset(readOffset, size, readCapacity) < 0L) {
            return;
        }
        ByteBuffer slice = source.duplicate();
        slice.position((int) readOffset);
        slice.limit((int) (readOffset + size));
        slice = slice.slice();
        try {
            NativeGeometryBuffer nativeWrite = NativeGeometryBuffer.find(writeName);
            if (nativeWrite != null) {
                if (BufferStorageRecord.mapRangeOffset(writeOffset, size, nativeWrite.byteSize()) >= 0L) {
                    nativeWrite.write(writeOffset, slice);
                }
                return;
            }
            ByteBuffer destination = GlsmBufferState.current().storage(writeName);
            if (destination != null && BufferStorageRecord.mapRangeOffset(writeOffset, size,
                    BufferStorageRecord.current().logicalSizeOf(writeName, destination.capacity())) >= 0L) {
                ByteBuffer out = destination.duplicate();
                out.position((int) writeOffset);
                out.put(slice);
            }
        } catch (RuntimeException ignored) {
            // A closed or mapped destination keeps its previous content, as the GL copy would have refused.
        }
    }

    /** The transformer's {@code glBufferSubData} route, against the bound buffer. */
    public static void bufferSubDataRoute(int target, long offset, long size, long data) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.bufferSubDataFor(entry.boundBuffer(target), offset, size, data);
        }
    }

    /** The DSA spelling of the sub-data mirror write. */
    public static void namedBufferSubDataRoute(int name, long offset, long size, long data) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.bufferSubDataFor(name, offset, size, data);
        }
    }

    /**
     * {@code glDelete*} array names from the transformer's route. The dispatcher's delete path
     * already knows each family; the name is normalised to the core spelling it matches.
     */
    public static void deleteObjects(String name, int count, long names) {
        DirectGlCaptureEntry entry = installed;
        if (entry == null || count <= 0 || names == 0L) {
            return;
        }
        int bounded = Math.min(count, 1 << 16);
        entry.delete(new GlFn(coreDeleteName(name), R_VOID, new byte[] {GlFunctionTable.A_INT, GlFunctionTable.A_PTR}, SEM_DELETE),
                new long[] {bounded, names});
    }

    /** {@code glDeleteProgram/glDeleteShader} from the transformer's route. */
    public static void deleteObject(String name, int object) {
        DirectGlCaptureEntry entry = installed;
        if (entry == null) {
            return;
        }
        entry.delete(new GlFn(coreDeleteName(name), R_VOID, new byte[] {GlFunctionTable.A_INT}, SEM_DELETE),
                new long[] {object});
    }

    private static String coreDeleteName(String name) {
        String core = name.startsWith("ngl") ? name.substring(1) : name;
        if (core.endsWith("EXT") || core.endsWith("ARB")) {
            core = core.substring(0, core.length() - 3);
        }
        return core;
    }

    /** {@code glDeleteLists} from the transformer's route. */
    public static void deleteListsRoute(int first, int range) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null && first > 0 && range > 0) {
            entry.deleteListsRange(first, range);
            DirectGlDispatcher.enterLane3();
            try {
                GLSMDraws.glDeleteLists(first, range);
            } finally {
                DirectGlDispatcher.exitLane3();
            }
        }
    }

    /** {@code glIsList} from the transformer's route: the one display-list id space (C3). */
    public static boolean isListRoute(int list) {
        DirectGlDispatcher.enterLane3();
        try {
            return GLSMDraws.glIsList(list);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The int-returning texture-parameter query, bound or named. */
    public static int getTextureParameterInt(int targetOrName, int pname, boolean named) {
        if (named) {
            return textureParameterValue(targetOrName, pname);
        }
        int textureName = 0;
        if (!isProxyTarget(targetOrName)) {
            int bound = NativeTextureBindings.current().boundTexture(targetOrName);
            textureName = bound < 0 ? 0 : bound;
        }
        return textureParameterValue(textureName, pname);
    }

    /** GL_PROXY_TEXTURE_1D/2D/3D/CUBE_MAP - uploads to these carry no pixels and bind to nothing. */
    public static boolean isProxyTarget(int target) {
        return target == 0x8063 || target == 0x8064 || target == 0x8070 || target == 0x851B;
    }

    /** {@code glGetTextureLevelParameteriv/i64v}: the DSA spelling of the recorded level extent. */
    public static void getTexLevelParameterNamed(int texture, int level, int pname, long out, boolean wide) {
        if (out == 0L) {
            return;
        }
        long value = 0L;
        int[] extent = texture > 0 ? VulkanTextureRegistry.captureStore().texLevels()
                .get(CaptureUploadContext.texLevelKey(texture, level)) : null;
        if (extent != null) {
            if (pname == 0x1000) {
                value = extent[0]; // GL_TEXTURE_WIDTH
            } else if (pname == 0x1001) {
                value = extent[1]; // GL_TEXTURE_HEIGHT
            } else if (pname == 0x1003) {
                value = extent[2]; // GL_TEXTURE_INTERNAL_FORMAT
            }
        }
        if (wide) {
            MemoryUtil.memPutLong(out, value);
        } else {
            MemoryUtil.memPutInt(out, (int) value);
        }
    }

    /**
     * The answer for a shader/program status query. LINK_STATUS and VALIDATE_STATUS answer
     * the executor's recorded link truth for a program it knows; an id with no recorded link
     * keeps the default true, and COMPILE_STATUS is always true: compilation here happens at
     * link, per program, so no per-shader result exists when a caller asks.
     */
    public static int shaderProgramQueryAnswer(int object, int pname) {
        if (GLSMPrograms.glIsProgram(object)) {
            return GLSMPrograms.glGetProgrami(object, pname);
        }
        if (GLSMPrograms.glIsShader(object)) {
            return GLSMPrograms.glGetShaderi(object, pname);
        }
        if (pname == 0x8B82 || pname == 0x8B83) { // GL_LINK_STATUS / GL_VALIDATE_STATUS
            return 1;
        }
        if (pname == 0x8B84) { // GL_INFO_LOG_LENGTH
            return 0;
        }
        return pname == 0x8B81 ? 1 : 0; // GL_COMPILE_STATUS
    }

    /** {@code glShaderObjectParameter*} from the transformer's route. */
    public static void shaderObjectParameter(int object, int pname, long out) {
        if (out == 0L) {
            return;
        }
        MemoryUtil.memPutInt(out, shaderProgramQueryAnswer(object, pname));
    }

    /**
     * {@code glGetFramebufferAttachmentParameteriv} (and its named form): a colour attachment
     * the game itself reported is answered with its texture, from the attachment table.
     */
    public static void framebufferAttachmentParameter(boolean named, int framebufferOrTarget, int attachment,
            int pname, long out) {
        if (out == 0L) {
            return;
        }
        MemoryUtil.memPutInt(out, framebufferAttachmentAnswer(named, framebufferOrTarget, attachment, pname));
    }

    private static int framebufferAttachmentAnswer(boolean named, int framebufferOrTarget, int attachment,
            int pname) {
        int framebuffer = named ? framebufferOrTarget
                : (framebufferOrTarget == 0x8CA8 ? FramebufferBindings.current().readFramebuffer()
                        : FramebufferBindings.current().drawFramebuffer());
        int slot = colorAttachmentSlot(attachment);
        int colorTexture = 0;
        if (framebuffer != 0 && slot >= 0) {
            colorTexture = FramebufferAttachmentTable.current().colorSlot(framebuffer, slot);
        }
        boolean colorAttached = colorTexture != 0;
        if (pname == 0x8CD0) { // GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            return colorAttached ? 0x1702 : 0; // GL_TEXTURE / GL_NONE
        }
        if (pname == 0x8CD1) { // GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            return colorAttached ? colorTexture : 0;
        }
        return 0;
    }

    /** The bound texture's recorded level-0 extent answer for a level query name, or 0. */
    public static int texLevelSize(int target, int level, int pname) {
        if (target != 0x0DE1 || level != 0) { // GL_TEXTURE_2D
            return 0;
        }
        int[] extent = VulkanTextureRegistry.captureStore()
                .textureExtent(NativeTextureBindings.current().texture2d());
        return extent == null ? 0
                : org.soutalab.celtium.vulkan.TextureLevelAnswer.answer(extent[0], extent[1], pname);
    }

    /** One recorded attribute answer, from the bound VAO's per-attribute record. */
    private static int vertexAttribAnswer(int index, int pname) {
        if (index < 0 || index >= GlsmClientArrayState.MAX_VERTEX_ATTRIBS) {
            return 0;
        }
        GlsmClientArrayState.VaoAttrib attrib = GlsmClientArrayState.current().boundAttribs()[index];
        switch (pname) {
            case 0x8622: // GL_VERTEX_ATTRIB_ARRAY_ENABLED
                return attrib.enabled || (index == 0 && (GlsmEnableState.current().enabled(0x8074)
                        || GlsmEnableState.current().enabled(0x8622))) ? 1 : 0;
            case 0x8623: // GL_VERTEX_ATTRIB_ARRAY_SIZE
                return attrib.size;
            case 0x8624: // GL_VERTEX_ATTRIB_ARRAY_STRIDE
                return attrib.stride;
            case 0x8625: // GL_VERTEX_ATTRIB_ARRAY_TYPE
                return attrib.type;
            case 0x889F: // GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING
                return attrib.buffer;
            case 0x886A: // GL_VERTEX_ATTRIB_ARRAY_NORMALIZED
                return attrib.normalized ? 1 : 0;
            case 0x88FD: // GL_VERTEX_ATTRIB_ARRAY_INTEGER
                return attrib.integer ? 1 : 0;
            default:
                return 0;
        }
    }

    /** {@code glGetVertexAttribfv/dv} from the transformer's route. */
    public static void getVertexAttribFloats(int index, int pname, long out, boolean asDouble) {
        if (out == 0L) {
            return;
        }
        int value = vertexAttribAnswer(index, pname);
        if (asDouble) {
            MemoryUtil.memPutDouble(out, value);
        } else {
            MemoryUtil.memPutFloat(out, value);
        }
    }

    /** {@code glGetVertexAttribPointerv} from the transformer's route. */
    public static void getVertexAttribPointer(int index, int pname, long out) {
        if (out == 0L) {
            return;
        }
        long value = pname == 0x8645 && index >= 0 && index < GlsmClientArrayState.MAX_VERTEX_ATTRIBS
                ? GlsmClientArrayState.current().boundAttribs()[index].offset : 0L;
        MemoryUtil.memPutLong(out, value);
    }

    /** {@code glGetVertexAttribiv} from the transformer's route. */
    public static void getVertexAttribiv(int index, int pname, long out) {
        if (out == 0L) {
            return;
        }
        MemoryUtil.memPutInt(out, vertexAttribAnswer(index, pname));
    }

    /** {@code glClearBufferfv} from the transformer's route: colour or depth, with the caller's value. */
    public static void clearBufferfvRoute(int buffer, int drawBuffer, long value) {
        if (DirectGlDispatcher.inLane3()) {
            if (value != 0L && (buffer == 0x1800 || buffer == 0x1801)) { // GL_COLOR or GL_DEPTH
                DirectGlExecutor.EXECUTOR.clearBufferfv(buffer, drawBuffer,
                        MemoryUtil.memFloatBuffer(value, buffer == 0x1800 ? 4 : 1));
            }
            return;
        }
        DirectGlCaptureEntry entry = installed;
        if (entry == null || value == 0L) {
            return;
        }
        if (buffer == 0x1800) { // GL_COLOR
            float red = MemoryUtil.memGetFloat(value);
            float green = MemoryUtil.memGetFloat(value + 4L);
            float blue = MemoryUtil.memGetFloat(value + 8L);
            float alpha = MemoryUtil.memGetFloat(value + 12L);
            FramebufferClearState clear = FramebufferClearState.current();
            float oldRed = clear.red(), oldGreen = clear.green(), oldBlue = clear.blue(), oldAlpha = clear.alpha();
            try {
                clear.clearColor(red, green, blue, alpha);
                entry.recordClear(0x4000);
            } finally {
                clear.clearColor(oldRed, oldGreen, oldBlue, oldAlpha);
            }
        } else if (buffer == 0x1801) { // GL_DEPTH
            double depth = MemoryUtil.memGetFloat(value);
            FramebufferClearState clear = FramebufferClearState.current();
            double old = clear.depth();
            try {
                clear.clearDepth(depth);
                entry.recordClear(0x0100);
            } finally {
                clear.clearDepth(old);
            }
        }
    }

    /** {@code glClearBufferiv} from the transformer's route. */
    public static void clearBufferivRoute(int buffer, int drawBuffer, long value) {
        if (DirectGlDispatcher.inLane3()) {
            if (value != 0L && buffer == 0x1802) { // GL_STENCIL
                DirectGlExecutor.EXECUTOR.clearBufferiv(buffer, drawBuffer,
                        MemoryUtil.memIntBuffer(value, 1));
            }
            return;
        }
        clearBufferfvRoute(buffer, drawBuffer, value);
    }

    /** {@code glClearBufferuiv} from the transformer's route. */
    public static void clearBufferuivRoute(int buffer, int drawBuffer, long value) {
        if (!DirectGlDispatcher.inLane3()) {
            clearBufferfvRoute(buffer, drawBuffer, value);
        }
        // Lane 3's framebuffer colors are normalized, so it has no unsigned integer color target.
    }

    /** {@code glClearBufferfi} from the transformer's route: the depth/stencil pair clear. */
    public static void clearBufferfiRoute(int buffer, int drawBuffer, float depth, int stencil) {
        if (DirectGlDispatcher.inLane3()) {
            DirectGlExecutor.EXECUTOR.clearBufferfi(buffer, drawBuffer, depth, stencil);
            return;
        }
        DirectGlCaptureEntry entry = installed;
        if (entry == null || buffer != 0x84F9) { // GL_DEPTH_STENCIL
            return;
        }
        FramebufferClearState clear = FramebufferClearState.current();
        double old = clear.depth();
        try {
            clear.clearDepth(depth);
            entry.recordClear(0x4100);
        } finally {
            clear.clearDepth(old);
        }
    }

    /** The transformer's {@code glListBase} route: the converter's single list seat. */
    public static void listBaseRoute(int base) {
        DirectGlDispatcher.enterLane3();
        try {
            GLSMDraws.glListBase(base);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glCallLists} route, shared with the dispatch's SEM_CALL_LISTS. */
    public static void callListsRoute(int count, int type, long lists) {
        DirectGlDispatcher.enterLane3();
        try {
            GLSMDraws.glCallLists(count, type, lists);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glDrawArrays} route. */
    public static void drawArraysRoute(int mode, int first, int count) {
        DirectGlDispatcher.enterLane3();
        try {
            GLSMDraws.glDrawArrays(mode, first, count);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glDrawElements} route. */
    public static void drawElementsRoute(int mode, int count, int type, long indices) {
        DirectGlDispatcher.enterLane3();
        try {
            GLSMDraws.glDrawElements(mode, count, type, indices);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glDrawElementsBaseVertex} route. */
    public static void drawElementsBaseVertexRoute(int mode, int count, int type, long indices, int baseVertex) {
        DirectGlDispatcher.enterLane3();
        try {
            GLSMDraws.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glMultiDrawArrays} route: the two native arrays read out here. */
    public static void multiDrawArraysRoute(int mode, long firsts, long counts, int drawcount) {
        if (firsts == 0L || counts == 0L || drawcount <= 0) {
            return;
        }
        int bounded = Math.min(drawcount, 1 << 16);
        int[] firstValues = new int[bounded];
        int[] countValues = new int[bounded];
        for (int i = 0; i < bounded; i++) {
            firstValues[i] = MemoryUtil.memGetInt(firsts + (long) i * 4L);
            countValues[i] = MemoryUtil.memGetInt(counts + (long) i * 4L);
        }
        DirectGlDispatcher.enterLane3();
        try {
            GLSMDraws.glMultiDrawArrays(mode, firstValues, countValues);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glMultiDrawElements} route: one indexed draw per element. */
    public static void multiDrawElementsRoute(int mode, long counts, int type, long indices, int drawcount) {
        if (counts == 0L || indices == 0L || drawcount <= 0) {
            return;
        }
        int bounded = Math.min(drawcount, 1 << 16);
        DirectGlDispatcher.enterLane3();
        try {
            for (int i = 0; i < bounded; i++) {
                int count = MemoryUtil.memGetInt(counts + (long) i * 4L);
                long pointer = MemoryUtil.memGetLong(indices + (long) i * 8L);
                GLSMDraws.glDrawElements(mode, count, type, pointer);
            }
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glDrawArraysInstanced} route. */
    public static void drawArraysInstancedRoute(int mode, int first, int count, int instanceCount) {
        DirectGlDispatcher.enterLane3();
        try {
            GLSMDraws.glDrawArraysInstanced(mode, first, count, instanceCount);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glDrawElementsInstanced} route. */
    public static void drawElementsInstancedRoute(int mode, int count, int type, long indices, int instanceCount) {
        DirectGlDispatcher.enterLane3();
        try {
            GLSMDraws.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
        } finally {
            DirectGlDispatcher.exitLane3();
        }
    }

    /** The transformer's {@code glReadPixels} route. */
    public static void readPixels(int x, int y, int width, int height, int format, int type, long address) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.serveReadPixels(x, y, width, height, format, type, address);
        }
    }

    /** The transformer's {@code glGetTexImage} route. */
    public static void getTexImage(int target, int level, int format, int type, long address) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.serveGetTexImage(target, level, format, type, address);
        }
    }

    /** The texture-parameter record's call-site label (bounded by the record's shape census). */
    static String paramSite() {
        return org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(
                org.soutalab.celtium.vulkan.TextureParameterRecord.paramShapeCount(), 24)
                ? callSite(5) : "";
    }

    /** The first element of an array-valued texture parameter: every scalar pname this record carries is one wide. */
    static int firstParamValue(GlFn fn, long values) {
        if (values == 0L) {
            return 0;
        }
        return fn.name.endsWith("fv") ? Math.round(MemoryUtil.memGetFloat(values)) : MemoryUtil.memGetInt(values);
    }

    /** The matrix-multiply arrival note: the mirror hooks apply matrices; this counts the raw arrivals. */
    private void noteMatrixMultiplyArrival(GlFn fn) {
        MATRIX_MULTIPLY_ARRIVALS.incrementAndGet();
        if (MATRIX_MULTIPLY_ARRIVALS.get() <= 4L
                && org.soutalab.celtium.vulkan.VulkanDiagnostics.enabled()) {
            System.out.println("[Celtium] CELTIUM_MULT_MATRIX arrival-only fn=" + fn.name
                    + " mode=" + Lane2StateMirror.current().matrixMode()
                    + " applied=" + MATRIX_MULTIPLY_APPLIED.get()
                    + " site=[" + callSite(8) + "]");
        }
    }

    /**
     * A {@code glGetTexImage} of the bound texture, answered from that texture's mirror by the backend.
     */
    void serveGetTexImage(int target, int level, int format, int type, long address) {
        if (target != 0x0DE1) {
            return;
        }
        long capacity = Long.MAX_VALUE;
        if (this.pixelPackBuffer > 0) {
            ByteBuffer storage = GlsmBufferState.current().storage(this.pixelPackBuffer);
            int logicalSize = storage == null ? 0
                    : BufferStorageRecord.current().logicalSizeOf(this.pixelPackBuffer, storage.capacity());
            if (storage == null || address < 0L || address >= logicalSize) {
                return;
            }
            capacity = logicalSize - address;
            address = MemoryUtil.memAddress(storage) + address;
        }
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        if (vulkan != null) {
            vulkan.celtium$getTexImage(NativeTextureBindings.current().texture2d(), level, format, type,
                    address, capacity);
        }
    }


    // ---- framebuffer attachment record, ported from the monolith (E4-b2) ---------------------
    private static int framebufferAttachmentLogs;
    private static int framebufferDepthAttachmentLogs;
    private static int framebufferLevelRefusals;
    private static int framebufferLevelRefusalLogs;

    void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        int framebuffer = target == 0x8CA8 ? FramebufferBindings.current().readFramebuffer() : FramebufferBindings.current().drawFramebuffer();
        if (framebuffer == 0) {
            return;
        }
        if (level != 0) {
            // A mip-level attachment is not recorded: the backend resolves an attachment at level 0, so
            // recording this one would publish the texture as if its level-0 pixels were the level the caller
            // named. Refused by name and counted instead of silently dropped.
            framebufferLevelRefusals++;
            if (framebufferLevelRefusalLogs < 8) {
                framebufferLevelRefusalLogs++;
                System.out.println("[Celtium] CELTIUM_RAW_FBO_LEVEL framebuffer=" + framebuffer
                        + " attachment=0x" + Integer.toHexString(attachment) + " texture=" + texture
                        + " level=" + level + " refused=level-not-recorded");
            }
            return;
        }
        // Depth attachments have their own record (see framebufferDepthTextures): they are not sampled as
        // colour, but the backend keys its shared depth storage from the texture named here.
        if (isDepthAttachment(attachment)) {
            FramebufferAttachmentTable.current().attachDepth(framebuffer, texture);
            if (texture != 0 && framebufferDepthAttachmentLogs < 8) {
                framebufferDepthAttachmentLogs++;
                System.out.println("[Celtium] CELTIUM_RAW_FBO_DEPTH framebuffer=" + framebuffer
                        + " depthTexture=" + texture
                        + " depthAttachments=" + FramebufferAttachmentTable.current().depthTextureCount());
            }
            // The depth attach completes most framebuffers' records (colour went on first), so it is a
            // registration point too: a framebuffer whose binds never reach the renderer's bind report still
            // gets its identity, pair and shared-depth borrow from here.
            Object backend = org.soutalab.celtium.vulkan.CeltiumBackends.current();
            Lane2ImmediateRecorder vulkan = vulkanBackend();
            if (vulkan != null) {
                vulkan.celtium$rawAttachmentChanged(framebuffer);
            }
            return;
        }
        // Colour attachments: every colour slot is recorded (GL_COLOR_ATTACHMENT0+i,
        // i < COLOR_ATTACHMENT_SLOTS), because a mod's render target can attach several colour textures and
        // write more than one in a pass; slot 0 additionally feeds the single-texture record above, which is
        // the one the sampling resolution has always read.
        int slot = colorAttachmentSlot(attachment);
        if (slot < 0) {
            return;
        }
        FramebufferAttachmentTable.current().attachColor(framebuffer, slot, texture);
        // After the record update, not before: the backend's registration reads the record this call just wrote
        // (it used to be notified first and so registered the previous attachment, or none).
        Object backend = org.soutalab.celtium.vulkan.CeltiumBackends.current();
        Lane2ImmediateRecorder vulkan = vulkanBackend();
            if (vulkan != null) {
            vulkan.celtium$rawAttachmentChanged(framebuffer);
        }
        if (texture == 0) return;
        if (framebufferAttachmentLogs < 8) {
            framebufferAttachmentLogs++;
            System.out.println("[Celtium] CELTIUM_RAW_FBO framebuffer=" + framebuffer + " slot=" + slot
                    + " colorTexture=" + texture
                    + " textarget=0x" + Integer.toHexString(textarget) + " level=" + level
                    + " colorAttachments=" + FramebufferAttachmentTable.current().colorTextureCount());
        }
    }

    static boolean isDepthAttachment(int attachment) {
        return attachment == 0x8D00 || attachment == 0x821A; // DEPTH_ATTACHMENT / DEPTH_STENCIL_ATTACHMENT
    }

    static int colorAttachmentSlot(int attachment) {
        int slot = (attachment & 0xFFFF) - 0x8CE0; // COLOR_ATTACHMENT0
        return slot >= 0 && slot < COLOR_ATTACHMENT_SLOTS ? slot : -1;
    }

    /** The transformer's {@code glFramebufferTexture2D} route: files under the bound framebuffer. */
    public static void framebufferTexture2DRoute(int target, int attachment, int textarget, int texture, int level) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.framebufferTexture2D(target, attachment, textarget, texture, level);
        }
    }

    /** The transformer's named-framebuffer texture route (the DSA spelling). */
    public static void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        if (framebuffer == 0 || level != 0) {
            return;
        }
        if (isDepthAttachment(attachment)) {
            FramebufferAttachmentTable.current().attachDepth(framebuffer, texture);
        } else {
            int slot = colorAttachmentSlot(attachment);
            if (slot < 0) {
                return;
            }
            FramebufferAttachmentTable.current().attachColor(framebuffer, slot, texture);
        }
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        if (vulkan != null) {
            vulkan.celtium$rawAttachmentChanged(framebuffer);
        }
    }

    /** The transformer's buffer-binding route: the same seat writes the dispatch makes. */
    public static void bindBufferRoute(int target, int name) {
        DirectGlCaptureEntry entry = installed;
        if (entry == null) {
            if (target == 0x8892) { // GL_ARRAY_BUFFER
                NativeGeometryBuffer.bindId(name);
            }
            return;
        }
        entry.bindBuffer(target, name);
        GLSMBuffers.glBindBuffer(target, name);
    }

    /** The transformer's framebuffer-binding route. */
    public static void bindFramebufferRoute(int target, int name) {
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.bindFramebuffer(target, name);
        } else {
            FramebufferBindings.current().bind(target, name);
        }
        GLSMFramebuffer.glBindFramebuffer(target, name);
    }


    // ---- attribute stacks, ported from the monolith (E4-b2) ----------------------------------
    private int attribPushes;
    private int attribPops;
    private int attribOverflow;
    private int attribUnmatchedPops;
    private int attribRestoredGroups;
    private int attribUnrestoredGroups;
    private int attribPartialGroups;
    private int lastAttribRestored;
    private int lastAttribMask;
    private int attribLightingRestores;
    private int attribLightingRestoresChanged;
    private final java.util.Map<Integer, Long> attribMasks = new java.util.HashMap<>();
    private static final int MAX_ATTRIB_MASKS = 64;
    private static final int[] ATTRIB_CAPABILITIES = {
            0x0BE2, 0x0B44, 0x0B71, 0x0BD0, 0x0B60, 0x0B50, 0x0BC0, 0x0C11, 0x8037, 0x0DE1, 0x0B57, 0x0B90, 0x0B41, 0x809D,
    };

    private boolean attribDepthWritesLive() {
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$depthWritesLive() : this.attribDepthWrites;
    }

    private int attribDepthFuncLive() {
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$depthFuncGl() : this.attribDepthFunc;
    }

    private int attribAlphaFuncLive() {
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$alphaTestFuncGl() : this.attribAlphaFunc;
    }

    private float attribAlphaReferenceLive() {
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$alphaTestReference() : this.attribAlphaReference;
    }

    private void noteAttribMask(int mask) {
        Long previous = this.attribMasks.get(Integer.valueOf(mask));
        if (previous != null) {
            this.attribMasks.put(Integer.valueOf(mask), Long.valueOf(previous.longValue() + 1L));
        } else if (this.attribMasks.size() < MAX_ATTRIB_MASKS) {
            this.attribMasks.put(Integer.valueOf(mask), Long.valueOf(1L));
        }
    }

    /** The legacy vertex-pointer record (the clientPointer(GL_VERTEX_ARRAY) live record). */
    private static GlsmClientArrayState.Pointer vertexPointerRecord() {
        return GlsmClientArrayState.current().clientPointer(0x8074, true);
    }

    public static void pushAttrib(int mask) {
        DirectGlCaptureEntry stub = installed;
        if (stub == null) {
            return;
        }
        int caps = ATTRIB_CAPABILITIES.length;
        LegacyAttribSnapshot snapshot = new LegacyAttribSnapshot(caps);
        snapshot.mask = mask;
        for (int i = 0; i < caps; i++) {
            snapshot.capabilities[i] = sharedEnabled(ATTRIB_CAPABILITIES[i]);
        }
        snapshot.red = GlsmImmediateState.current().red();
        snapshot.green = GlsmImmediateState.current().green();
        snapshot.blue = GlsmImmediateState.current().blue();
        snapshot.alpha = GlsmImmediateState.current().alpha();
        snapshot.lineWidth = GlsmImmediateState.current().lineWidth();
        snapshot.pointSize = GlsmImmediateState.current().pointSize();
        snapshot.depthWrites = stub.attribDepthWritesLive();
        snapshot.depthFunc = stub.attribDepthFuncLive();
        snapshot.alphaFunc = stub.attribAlphaFuncLive();
        snapshot.alphaReference = stub.attribAlphaReferenceLive();
        snapshot.blendSourceRgb = NativeRasterState.current().blendSrcRgb();
        snapshot.blendDestinationRgb = NativeRasterState.current().blendDstRgb();
        snapshot.blendSourceAlpha = NativeRasterState.current().blendSrcAlpha();
        snapshot.blendDestinationAlpha = NativeRasterState.current().blendDstAlpha();
        snapshot.blendEquationRgb = NativeRasterState.current().blendEquationRgb();
        snapshot.blendEquationAlpha = NativeRasterState.current().blendEquationAlpha();
        snapshot.colorMask = NativeRasterState.current().colorMask();
        snapshot.cullFace = NativeRasterState.current().cullFace();
        snapshot.frontFace = NativeRasterState.current().frontFace();
        snapshot.scissorX = NativeRasterState.current().scissorX();
        snapshot.scissorY = NativeRasterState.current().scissorY();
        snapshot.scissorWidth = NativeRasterState.current().scissorWidth();
        snapshot.scissorHeight = NativeRasterState.current().scissorHeight();
        snapshot.polygonOffsetFactor = NativeRasterState.current().polygonOffsetFactor();
        snapshot.polygonOffsetUnits = NativeRasterState.current().polygonOffsetUnits();
        snapshot.viewportX = FramebufferViewportState.current().x();
        snapshot.viewportY = FramebufferViewportState.current().y();
        snapshot.viewportWidth = FramebufferViewportState.current().width();
        snapshot.viewportHeight = FramebufferViewportState.current().height();
        snapshot.listBase = GLStateManager.getListBase();
        snapshot.activeTextureUnit = NativeTextureBindings.current().activeUnit();
        snapshot.texture2d = NativeTextureBindings.current().texture2d();
        // GL_LIGHTING_BIT's restorable part. The authority is the backend's immediate-state mirror, which is where the
        // game's GlStateManager path and the raw glLightfv route both write; this stub keeps no second copy, and
        // reading one would be the "restore a copy of a copy" shape the rest of this push avoids.
        org.soutalab.celtium.vulkan.Lane2ImmediateRecorder lighting = vulkanBackend();
        if (lighting != null) {
            System.arraycopy(lighting.celtium$lightDirections(), 0, snapshot.lightDirections, 0, 6);
            System.arraycopy(lighting.celtium$lightDiffuse(), 0, snapshot.lightDiffuse, 0, 6);
            System.arraycopy(lighting.celtium$lightAmbient(), 0, snapshot.lightAmbient, 0, 3);
            snapshot.shadeModel = lighting.celtium$shadeModel();
            snapshot.lightingCaptured = true;
        }
        // GL_FOG_BIT's restorable part. The authority is the fog owner, which the game's GlStateManager path and
        // the raw glFog route both write; it exists unconditionally, so there is no "not captured" case here of
        // the kind the lighting group needs. The enable is captured beside the parameters because GL keeps it in
        // the fog group too: a mask like AdvancedRocketry's 0xBC1 (fog group, no GL_ENABLE_BIT) still expects the
        // pop to put it back (ITEM382-SCAN section 3.1).
        org.soutalab.celtium.vulkan.ImmediateFogState fog = org.soutalab.celtium.vulkan.ImmediateFogState.current();
        snapshot.fogEnabled = fog.enabled();
        snapshot.fogMode = fog.glMode();
        snapshot.fogDensity = fog.getFogDensity();
        snapshot.fogStart = fog.getFogStart();
        snapshot.fogEnd = fog.getFogEnd();
        float[] fogColor = fog.getFogColor();
        snapshot.fogColor[0] = fogColor[0];
        snapshot.fogColor[1] = fogColor[1];
        snapshot.fogColor[2] = fogColor[2];
        snapshot.fogColor[3] = fog.colorAlpha();
        stub.attribPushes++;
        stub.noteAttribMask(mask);
        if (!GlsmAttribStacks.current().pushServer(snapshot)) {
            stub.attribOverflow++;
            return;
        }
    }

    public static void popAttrib() {
        DirectGlCaptureEntry stub = installed;
        if (stub == null) {
            return;
        }
        LegacyAttribSnapshot snapshot = GlsmAttribStacks.current().popServer();
        if (snapshot == null) {
            stub.attribUnmatchedPops++;
            return;
        }
        stub.attribPops++;
        stub.restoreAttrib(snapshot);
    }

    private void restoreAttrib(LegacyAttribSnapshot snapshot) {
        org.soutalab.celtium.vulkan.CeltiumBackend backend =
                org.soutalab.celtium.vulkan.CeltiumBackends.current();
        int restored = 0;
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.ENABLE_BIT)) {
            for (int i = 0; i < ATTRIB_CAPABILITIES.length; i++) {
                setCapability(ATTRIB_CAPABILITIES[i], snapshot.capabilities[i]);
            }
            restored |= LegacyAttribSnapshot.ENABLE_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.CURRENT_BIT)) {
            GlsmImmediateState.current().color(snapshot.red, snapshot.green, snapshot.blue, snapshot.alpha);
            // The group payload reads its colour from the backend mirror, not from these fields: restoring only the
            // stub would leave the popped draw's tint as the next vanilla group's colour.
            if (backend != null) {
                backend.setColor(GlsmImmediateState.current().red(), GlsmImmediateState.current().green(), GlsmImmediateState.current().blue(), GlsmImmediateState.current().alpha());
            }
            restored |= LegacyAttribSnapshot.CURRENT_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.LINE_BIT)) {
            GlsmImmediateState.current().lineWidth(snapshot.lineWidth);
            restored |= LegacyAttribSnapshot.LINE_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.POINT_BIT)) {
            GlsmImmediateState.current().pointSize(snapshot.pointSize);
            restored |= LegacyAttribSnapshot.POINT_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.DEPTH_BUFFER_BIT)) {
            this.attribDepthWrites = snapshot.depthWrites;
            this.attribDepthFunc = snapshot.depthFunc;
            // GL_DEPTH_WRITEMASK is answered from the enable record when no backend mirror exists, and the restore has
            // to land there too or the query keeps reporting the pushed block's value.
            GlsmEnableState.current().set(GL_DEPTH_WRITEMASK_CAP, snapshot.depthWrites);
            if (backend != null) {
                backend.setDepthMask(snapshot.depthWrites);
                backend.depthFunc(snapshot.depthFunc);
            }
            restored |= LegacyAttribSnapshot.DEPTH_BUFFER_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.COLOR_BUFFER_BIT)) {
            // GL puts the blend factors and equation, the colour write mask and the alpha test's comparison in
            // GL_COLOR_BUFFER_BIT - the *enables* are GL_ENABLE_BIT, which is why this is a separate group.
            NativeRasterState.current().blendFunc(snapshot.blendSourceRgb, snapshot.blendDestinationRgb,
                    snapshot.blendSourceAlpha, snapshot.blendDestinationAlpha);
            NativeRasterState.current().blendEquation(snapshot.blendEquationRgb, snapshot.blendEquationAlpha);
            NativeRasterState.current().colorMask(snapshot.colorMask);
            this.attribAlphaFunc = snapshot.alphaFunc;
            this.attribAlphaReference = snapshot.alphaReference;
            if (backend != null) {
                backend.setAlphaFunc(snapshot.alphaFunc, snapshot.alphaReference);
            }
            restored |= LegacyAttribSnapshot.COLOR_BUFFER_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.POLYGON_BIT)) {
            NativeRasterState.current().cullFace(snapshot.cullFace);
            NativeRasterState.current().frontFace(snapshot.frontFace);
            NativeRasterState.current().polygonOffset(snapshot.polygonOffsetFactor, snapshot.polygonOffsetUnits);
            restored |= LegacyAttribSnapshot.POLYGON_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.SCISSOR_BIT)) {
            NativeRasterState.current().scissor(snapshot.scissorX, snapshot.scissorY,
                    snapshot.scissorWidth, snapshot.scissorHeight);
            restored |= LegacyAttribSnapshot.SCISSOR_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.VIEWPORT_BIT)) {
            // Through the backend when there is one: it owns the group key's viewport term and the live sizes the
            // offers outside the full-target contract are refused against, and its own viewport writer updates the
            // packed rectangle and this stub's fields. Without a backend the packed value is still what the capture
            // reads, so it is written directly rather than left describing the popped block's rectangle.
            Lane2ImmediateRecorder vulkan = vulkanBackend();
            if (vulkan != null) {
                vulkan.viewport(snapshot.viewportX, snapshot.viewportY,
                        snapshot.viewportWidth, snapshot.viewportHeight);
            } else {
                FramebufferViewportState.current().set(snapshot.viewportX, snapshot.viewportY,
                        snapshot.viewportWidth, snapshot.viewportHeight);
            }
            restored |= LegacyAttribSnapshot.VIEWPORT_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.LIST_BIT)) {
            GLSMDraws.glListBase(snapshot.listBase);
            restored |= LegacyAttribSnapshot.LIST_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.TEXTURE_BIT)) {
            NativeTextureBindings.current().activeUnit(snapshot.activeTextureUnit);
            NativeTextureBindings.current().bindTexture2d(snapshot.texture2d);
            restored |= LegacyAttribSnapshot.TEXTURE_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.LIGHTING_BIT) && snapshot.lightingCaptured
                && vulkanBackend() != null) {
            Lane2ImmediateRecorder lighting = vulkanBackend();
            // The group is restored in part (see LegacyAttribSnapshot.PARTIAL_GROUPS): the two directions, the two
            // diffuse colours, the scene ambient and the shade model. The material and per-light ambient/specular/spot
            // terms GL also keeps in this group do not exist in this renderer's payload, so they are counted as the
            // partial gap rather than silently claimed.
            if (lighting.celtium$restoreLightState(snapshot.lightDirections, snapshot.lightDiffuse,
                    snapshot.lightAmbient, snapshot.shadeModel)) {
                this.attribLightingRestoresChanged++;
            }
            this.attribLightingRestores++;
            restored |= LegacyAttribSnapshot.LIGHTING_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.FOG_BIT)) {
            // The group is restored in part (see LegacyAttribSnapshot.PARTIAL_GROUPS): the enable, the mode, the
            // density, the start/end and the colour go back through the fog owner's own setters, so a restore
            // that changes a value takes the same batch-boundary flush a game write would. GL_FOG_INDEX has no
            // counterpart in the owner and is the counted partial gap.
            org.soutalab.celtium.vulkan.ImmediateFogState fog =
                    org.soutalab.celtium.vulkan.ImmediateFogState.current();
            fog.enabled(snapshot.fogEnabled);
            fog.parameter(0x0B65, snapshot.fogMode);
            fog.parameter(0x0B62, snapshot.fogDensity);
            fog.parameter(0x0B63, snapshot.fogStart);
            fog.parameter(0x0B64, snapshot.fogEnd);
            fog.color(snapshot.fogColor[0], snapshot.fogColor[1], snapshot.fogColor[2], snapshot.fogColor[3]);
            restored |= LegacyAttribSnapshot.FOG_BIT;
        }
        this.attribRestoredGroups += Integer.bitCount(restored);
        this.attribUnrestoredGroups += Integer.bitCount(selectsAttrib(snapshot, LegacyAttribSnapshot.UNMODELLED_GROUPS));
        this.attribPartialGroups += Integer.bitCount(selectsAttrib(snapshot, LegacyAttribSnapshot.PARTIAL_GROUPS));
        this.lastAttribRestored = restored;
        this.lastAttribMask = snapshot.mask;
    }

    /** {@return whether this pop restores {@code group}} - the mask rule, applied unconditionally. */
    private static boolean restoresAttrib(LegacyAttribSnapshot snapshot, int group) {
        return snapshot.restores(group);
    }

    /** {@return the groups a push with this mask selects that this renderer cannot put back}. */
    private static int selectsAttrib(LegacyAttribSnapshot snapshot, int groups) {
        return snapshot.mask & groups;
    }

    public static void pushClientAttrib() {
        DirectGlCaptureEntry stub = installed;
        if (stub == null) {
            return;
        }
        GlsmAttribStacks.current().pushClient(new long[] {vertexPointerRecord().size, vertexPointerRecord().type,
                vertexPointerRecord().stride, vertexPointerRecord().offset, GlsmEnableState.current().enabled(0x8074) ? 1 : 0,
                vertexPointerRecord().normalized ? 1L : 0L, vertexPointerRecord().integer ? 1L : 0L});
    }

    public static void popClientAttrib() {
        DirectGlCaptureEntry stub = installed;
        if (stub == null) {
            return;
        }
        long[] saved = GlsmAttribStacks.current().popClient();
        if (saved == null) {
            return;
        }
        vertexPointerRecord().size = (int) saved[0];
        vertexPointerRecord().type = (int) saved[1];
        vertexPointerRecord().stride = (int) saved[2];
        vertexPointerRecord().offset = saved[3];
        GlsmEnableState.current().set(0x8074, saved[4] != 0);
        vertexPointerRecord().normalized = saved.length > 5 && saved[5] != 0L;
        vertexPointerRecord().integer = saved.length > 6 && saved[6] != 0L;
        // The restored pointer is attribute 0's record too; the two views of it must not drift apart.
        GlsmClientArrayState.VaoAttrib attrib = GlsmClientArrayState.current().boundAttribs()[0];
        attrib.size = vertexPointerRecord().size;
        attrib.type = vertexPointerRecord().type;
        attrib.stride = vertexPointerRecord().stride;
        attrib.offset = vertexPointerRecord().offset;
        attrib.normalized = vertexPointerRecord().normalized;
        attrib.integer = vertexPointerRecord().integer;
        attrib.pointerSet = true;
    }

    // ---- dispatch helpers -------------------------------------------------------------------

    private static boolean isObject(String name, int id) {
        if ("glIsList".equals(name)) {
            // C3: list names live in the GLSM allocator's space, not the name ledger.
            return GLSMDraws.glIsList(id);
        }
        // The monolith's answer, unchanged: a name is an object when the shared ledger holds it
        // (or it is a live sync); a buffer name backed by a native geometry buffer counts too.
        return GlsmNameLedger.current().isLive(id) || GlsmNameLedger.current().isLiveSync(id)
                || ("glIsBuffer".equals(name) && NativeGeometryBuffer.find(id) != null);
    }

    /**
     * Name allocation, ported from the monolith's dispatch: every name comes out of the one
     * shared window ({@link RenderObjectIds}) and is entered in the name ledger, whatever the
     * object kind - the mirrors key on names across kinds, so two windows both starting at 1
     * would hand two different resources the same name. The converter allocates its own ids
     * for calls that arrive transformed; this surface serves the provider's callers.
     * Display-list names are the single exception (C3): they come out of the GLSM display-list
     * allocator, the one space {@code glIsList}/{@code glDeleteLists} also answer from.
     */
    private long generate(GlFn fn, long[] c) {
        if ("glGenLists".equals(fn.name) && fn.args.length == 1) {
            // Display-list names are the one exception to the shared window below (C3, decoders
            // ruling item 25): they live in the GLSM allocator's space - the same space
            // glIsList and glDeleteLists answer from - so generation, query and deletion can
            // never split. The allocator hands out a contiguous range, which Minecraft relies
            // on (RenderGlobal's star lists are base, base+1, base+2). The spec: a range of
            // zero (or less) generates nothing and returns 0.
            int range = (int) c[0];
            return range <= 0 ? 0 : GLSMDraws.glGenLists(range);
        }
        if (fn.ret == R_INT && fn.args.length <= 1 && lastPointerArg(fn) < 0) {
            // Scalar-return form (glCreateShader, glCreateProgram): the id is the return value.
            int range = 1;
            if ("glCreateShader".equals(fn.name) || "glCreateShaderObjectARB".equals(fn.name)) {
                // The program family has one id space, the converter's (family 5): a name from
                // the shared window would be a second space for the same object.
                int id = GLSMPrograms.glCreateShader((int) c[0]);
                GlsmNameLedger.current().addName(id);
                return id;
            }
            if ("glCreateProgram".equals(fn.name) || "glCreateProgramObjectARB".equals(fn.name)) {
                int id = GLSMPrograms.glCreateProgram();
                GlsmNameLedger.current().addName(id);
                return id;
            }
            int scalar = RenderObjectIds.reserve(range);
            for (int i = 0; i < range; i++) {
                GlsmNameLedger.current().addName(scalar + i);
            }
            return scalar;
        }
        if (fn.ret == R_LONG) {
            // Sync object (glFenceSync): ids come from the 64-bit space.
            long sync = GlsmNameLedger.current().newSync();
            GlsmNameLedger.current().addName((int) sync);
            return sync;
        }
        // Array form (glGenTextures, glCreateTextures...): the out[] is the LAST pointer arg; the count is the
        // int immediately before it.
        int outIndex = lastPointerArg(fn);
        if (outIndex < 0) {
            int scalar = RenderObjectIds.reserve(1);
            GlsmNameLedger.current().addName(scalar);
            return scalar;
        }
        long out = c[outIndex];
        if (out == 0L) {
            return 0L;
        }
        int n = countFromArgs(fn, c, outIndex);
        for (int i = 0; i < n; i++) {
            int name = RenderObjectIds.reserve(1);
            GlsmNameLedger.current().addName(name);
            MemoryUtil.memPutInt(out + (long) i * 4L, name);
        }
        return 0L;
    }

    private static int lastPointerArg(GlFn fn) {
        for (int i = fn.args.length - 1; i >= 0; i--) {
            if (fn.args[i] == GlFunctionTable.A_PTR) {
                return i;
            }
        }
        return -1;
    }

    /** The count before the out array: the last int arg before the out pointer, or 1 when there is none. */
    private static int countFromArgs(GlFn fn, long[] c, int outIndex) {
        for (int i = outIndex - 1; i >= 0; i--) {
            if (fn.args[i] == GlFunctionTable.A_INT || fn.args[i] == GlFunctionTable.A_UINT) {
                return (int) c[i];
            }
        }
        return 1;
    }

    /**
     * Deletion, ported from the monolith's dispatch: the ledger entry goes, and each kind's
     * records settle with it - texture captures through the capture context, buffer storage
     * through {@link #forgetBuffer}, framebuffers through {@link #forgetFramebuffer}, VAO and
     * renderbuffer records through the client-array state, list ranges through
     * {@link #deleteListsRange}. Program and shader names also retire in the converter
     * (family 5's seat), whose records carry the lowered forms.
     */
    private void delete(GlFn fn, long[] c) {
        boolean textures = "glDeleteTextures".equals(fn.name);
        if (fn.args.length == 2 && fn.args[0] == GlFunctionTable.A_INT && fn.args[1] == GlFunctionTable.A_PTR) {
            int count = (int) c[0];
            long names = c[1];
            if (names == 0L) {
                return;
            }
            boolean buffers = "glDeleteBuffers".equals(fn.name);
            boolean framebuffers = "glDeleteFramebuffers".equals(fn.name);
            boolean vertexArrays = "glDeleteVertexArrays".equals(fn.name);
            boolean renderbuffers = "glDeleteRenderbuffers".equals(fn.name);
            for (int i = 0; i < count; i++) {
                int name = MemoryUtil.memGetInt(names + (long) i * 4L);
                GlsmNameLedger.current().removeName(name);
                if (vertexArrays) {
                    GlsmClientArrayState.current().removeAttribs(name);
                    if (GlsmClientArrayState.current().boundVertexArray() == name) {
                        GlsmClientArrayState.current().bindVertexArray(0);
                    }
                }
                if (renderbuffers) {
                    GlsmClientArrayState.current().removeRenderbufferSize(name);
                    if (GlsmClientArrayState.current().boundRenderbuffer() == name) {
                        GlsmClientArrayState.current().bindRenderbuffer(0);
                    }
                }
                if (textures) {
                    CaptureUploadContext.current().deleteTextureRecords(name);
                }
                if (buffers) {
                    forgetBuffer(name);
                }
                if (framebuffers) {
                    forgetFramebuffer(name);
                }
            }
            return;
        }
        if ("glDeleteLists".equals(fn.name)) {
            // Display-list names are a range of their own: glDeleteLists(list, range) retires range names, and each one
            // that this provider compiled owns payload buffers that go with it (#394). The name is retired too, so
            // glIsList answers false afterwards (#395).
            deleteListsRange((int) c[0], (int) c[1]);
            return;
        }
        if (fn.args.length == 1) {
            if (fn.args[0] == GlFunctionTable.A_LONG) {
                GlsmNameLedger.current().removeSync(c[0]);
                GLSMBuffers.glDeleteSync(c[0]);
            } else {
                int name = (int) c[0];
                GlsmNameLedger.current().removeName(name);
                if ("glDeleteBuffers".equals(fn.name)) {
                    forgetBuffer(name);
                }
                if ("glDeleteProgram".equals(fn.name)) {
                    GLSMPrograms.glDeleteProgram(name);
                }
                if ("glDeleteShader".equals(fn.name)) {
                    GLSMPrograms.glDeleteShader(name);
                }
            }
        }
    }

    /**
     * Retires a buffer's storage and its bindings.
     * <p>
     * Deleting a buffer left the byte store holding its direct memory and the bindings still
     * naming it, so a later {@code glMapBuffer} answered with a pointer into the deleted
     * buffer's storage and every delete-then-create cycle kept another megabyte alive (#386).
     */
    private void forgetBuffer(int name) {
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) nativeBuffer.close();
        if (NativeGeometryBuffer.boundId() == name) NativeGeometryBuffer.unbind();
        GlsmBufferState.current().removeStorage(name);
        BufferStorageRecord.current().forget(name);
        PixelUnpackState.current().forgetFallbackStorage(name);
        if (this.pixelPackBuffer == name) {
            this.pixelPackBuffer = 0;
        }
        if (PixelUnpackState.current().pixelUnpackBuffer() == name) {
            PixelUnpackState.current().pixelUnpackBuffer(0);
        }
        GlsmBufferState.current().unbindName(name);
        GLSMBuffers.glDeleteBuffers(name);
    }

    /** Retires a framebuffer's attachment record and binding, and notifies the backend. */
    private void forgetFramebuffer(int name) {
        if (name == 0) return;
        int color = org.soutalab.celtium.vulkan.FramebufferAttachmentTable.current().colorTexture(name);
        Object backend = CeltiumBackends.current();
        Lane2ImmediateRecorder vulkan = vulkanBackend();
            if (vulkan != null) {
            vulkan.celtium$rawFramebufferDeleted(name, color);
        }
        org.soutalab.celtium.vulkan.FramebufferAttachmentTable.current().framebufferDeleted(name);
        FramebufferBindings.current().forgetName(name);
        GLSMFramebuffer.glDeleteFramebuffers(name);
    }

    /** Retires a display-list range: the model capture's lists and the names. */
    private void deleteListsRange(int first, int range) {
        VulkanModelQuadCapture.listsDeleted(first, range);
        for (int i = 0; i < range; i++) {
            GlsmNameLedger.current().removeName(first + i);
        }
        GLSMDraws.glDeleteLists(first, range);
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

    /** The buffer a target-form call names: the bound buffer, with the pixel and array seats resolved. */
    private int boundBuffer(int target) {
        switch (target) {
            case 0x8892: // GL_ARRAY_BUFFER
                return NativeGeometryBuffer.boundId();
            case 0x88EB: // GL_PIXEL_PACK_BUFFER
                return this.pixelPackBuffer;
            case 0x88EC: // GL_PIXEL_UNPACK_BUFFER
                return PixelUnpackState.current().pixelUnpackBuffer();
            default:
                return GlsmBufferState.current().bound(target);
        }
    }

    private int bufferFor(GlFn fn, long[] c) {
        return fn.namedObject ? (int) c[0] : boundBuffer((int) c[0]);
    }

    /** Writes into the storage {@code glBufferData} allocated, which is what the mirrored meshes are read from. */
    private void bufferSubDataFor(int name, long offset, long size, long data) {
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) {
            if (data != 0L && size > 0 && offset >= 0 && size <= nativeBuffer.byteSize()
                    && offset <= nativeBuffer.byteSize() - size) {
                nativeBuffer.write(offset, MemoryUtil.memByteBuffer(data, (int) size));
            }
            return;
        }
        ByteBuffer storage = GlsmBufferState.current().storage(name);
        int logicalSize = BufferStorageRecord.current().logicalSizeOf(name, storage == null ? 0 : storage.capacity());
        if (storage == null || data == 0L || BufferStorageRecord.mapRangeOffset(offset, size, logicalSize) < 0L) {
            return;
        }
        ByteBuffer source = MemoryUtil.memByteBuffer(data, (int) size);
        int position = storage.position();
        storage.position((int) offset);
        storage.put(source);
        storage.position(position);
    }

    private long mapBuffer(GlFn fn, long[] c) {
        boolean range = fn.args.length == 4;
        return BufferStorageRecord.current().mapBufferFor(bufferFor(fn, c), range,
                range ? c[1] : 0L, range ? c[2] : 0L, range ? c[3] : 0L);
    }

    private long unmapBuffer(GlFn fn, long[] c) {
        NativeGeometryBuffer mapped = NativeGeometryBuffer.find(bufferFor(fn, c));
        return mapped == null || mapped.unmap() ? 1L : 0L;
    }

    private void bufferData(GlFn fn, long[] c) {
        // The seat producer first: the byte store and logical size the mirrors read.
        BufferStorageRecord.current().bufferDataFor(bufferFor(fn, c), c[1], c[2]);
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
    }

    private void statusAnswer(GlFn fn, long[] c) {
        if (fn.name.equals("glGetFramebufferAttachmentParameteriv")
                || fn.name.equals("glGetNamedFramebufferAttachmentParameteriv")) {
            // (target-or-fbo, attachment, pname, params*) - the one status row whose out slot is c[3].
            // Answered from the attachment table, as the shipped stub answered it: a colour
            // attachment the game itself reported reads back with its texture.
            if (c[3] != 0L) {
                MemoryUtil.memPutInt(c[3], framebufferAttachmentAnswer(
                        fn.name.startsWith("glGetNamed"), (int) c[0], (int) c[1], (int) c[2]));
            }
            return;
        }
        if (fn.name.startsWith("glGetTexLevelParameter")) {
            // (target, level, pname, params*) - the one status row whose out slot is c[3] besides
            // the framebuffer-attachment one above. The generic arm below would write through
            // c[2], which here holds the pname itself. The answer is the recorded level parameter
            // of the texture the target resolves to, as the shipped stub answered it.
            if (c[3] != 0L) {
                int value = texLevelParam((int) c[0], (int) c[1], (int) c[2]);
                if (fn.name.endsWith("fv")) {
                    MemoryUtil.memPutFloat(c[3], value);
                } else {
                    MemoryUtil.memPutInt(c[3], value);
                }
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
                MemoryUtil.memPutInt(out, bufferParameterAnswer(obj, pname));
                return;
            case "glGetVertexAttribiv":
                getVertexAttribiv(obj, pname, out);
                return;
            case "glGetVertexAttribPointerv":
                getVertexAttribPointer(obj, pname, out);
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

    /**
     * The dispatch's buffer-parameter answer. GL_BUFFER_SIZE comes from the record seat
     * this dispatch's own bufferData writes ({@link BufferStorageRecord} / the native
     * buffer), as the named route already answers it: the executor's record is blind to
     * natively bound buffers, so a raw glBufferData against one answered 0 there. Other
     * parameters stay with the executor's record.
     */
    private int bufferParameterAnswer(int target, int pname) {
        if (pname != 0x8764) { // GL_BUFFER_SIZE
            return GLSMBuffers.glGetBufferParameteri(target, pname);
        }
        int name = boundBuffer(target);
        if (name == 0) {
            return 0;
        }
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) {
            return (int) nativeBuffer.byteSize();
        }
        ByteBuffer storage = GlsmBufferState.current().storage(name);
        return storage == null ? 0
                : BufferStorageRecord.current().logicalSizeOf(name, storage.capacity());
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
    // Ported from the monolith's GlStateQueries with the monolith removed: each pname is
    // answered by the seat that owns the record - the raster seat, the texture bindings, the
    // buffer state, the HAL framebuffer seats, the backend's mirrors, the Lane 2 register -
    // and no pname falls through to a bulk zero. The static forms are the hint doors' surface:
    // the same router answers the transformer's LWJGL3 query natives.

    /** {@return the scalar answer for a tracked pname}, or {@link Integer#MIN_VALUE} when untracked. */
    public static int getInteger(int pname) {
        DirectGlCaptureEntry entry = installed;
        if (entry == null) {
            return Integer.MIN_VALUE;
        }
        int value = entry.getIntegerValue(pname);
        noteTextureUnitAnswer(pname, value);
        noteImageUnitAnswer(pname, value);
        return value;
    }

    /**
     * Answers tracked integer state for LWJGL3's replaced native query. Untracked capability
     * limits are left to the caller, which can also answer them before this provider is installed.
     *
     * @return whether the pname was answered
     */
    public static boolean getIntegers(int pname, long out) {
        DirectGlCaptureEntry entry = installed;
        if (entry == null || out == 0L) {
            return false;
        }
        if (pname != 0x0BA2 && pname != 0x0C10 && entry.getIntegerValue(pname) == Integer.MIN_VALUE) {
            return false;
        }
        entry.getInts(pname, out);
        return true;
    }

    /** The same float router for LWJGL3's replaced native query. */
    public static void getFloats(int pname, long out, boolean asDouble) {
        if (out == 0L) {
            return;
        }
        DirectGlCaptureEntry entry = installed;
        if (entry != null) {
            entry.routeFloats(pname, out, asDouble);
        } else {
            putFloatOrDouble(out, 0, 0.0f, asDouble);
        }
    }

    /** {@return whether the boolean query was answered}; the caller's buffer receives one byte per component. */
    public static boolean getBooleans(int pname, long out) {
        DirectGlCaptureEntry entry = installed;
        if (entry == null || out == 0L) {
            return false;
        }
        entry.routeBooleans(pname, out);
        return true;
    }

    /** {@return the value a texture parameter query answers}: the record, or GL's initial state. */
    public static int textureParameterValue(int textureName, int pname) {
        return VulkanTextureRegistry.captureStore().parameters().valueOrDefault(textureName, pname);
    }

    /** The buffer-object query for the transformer's natives: GL_BUFFER_SIZE, everything else 0. */
    public static void getBufferParameter(String name, int target, int pname, long out) {
        if (out == 0L) {
            return;
        }
        if (pname == 0x8764) { // GL_BUFFER_SIZE - LWJGL needs this to size glMapBuffer results
            DirectGlCaptureEntry entry = installed;
            int bound = entry == null ? 0 : entry.boundBuffer(target);
            NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(bound);
            ByteBuffer storage = bound == 0 ? null : GlsmBufferState.current().storage(bound);
            int size = nativeBuffer != null ? nativeBuffer.byteSize()
                    : BufferStorageRecord.current().logicalSizeOf(bound, storage == null ? 0 : storage.capacity());
            if ("glGetBufferParameteri64v".equals(name)) {
                MemoryUtil.memPutLong(out, size);
            } else {
                MemoryUtil.memPutInt(out, size);
            }
            return;
        }
        MemoryUtil.memPutInt(out, 0);
    }

    /** The non-DSA tex-parameter query for the transformer's natives: (target, pname, out). */
    public static void getTexParameter(String name, int target, int paramPname, long out) {
        if (out == 0L) {
            return;
        }
        int textureName = 0;
        if (target != 0x8063 && target != 0x8064 && target != 0x8070 && target != 0x851B) {
            int bound = NativeTextureBindings.current().boundTexture(target);
            textureName = bound < 0 ? 0 : bound;
        }
        int value = textureParameterValue(textureName, paramPname);
        if (name.endsWith("fv")) {
            if (paramPname == 0x1004) { // GL_TEXTURE_BORDER_COLOR - four components, GL's initial zero
                for (int i = 0; i < 4; i++) {
                    MemoryUtil.memPutFloat(out + (long) i * 4L, 0.0f);
                }
                return;
            }
            MemoryUtil.memPutFloat(out, value);
        } else {
            MemoryUtil.memPutInt(out, value);
        }
    }

    /** The scalar half of {@link #getInts}; {@link Integer#MIN_VALUE} means "not a tracked scalar pname". */
    private int getIntegerValue(int pname) {
        NativeRasterState raster = NativeRasterState.current();
        NativeTextureBindings textures = NativeTextureBindings.current();
        switch (pname) {
            case 0x0BE1: // GL_BLEND_SRC
            case 0x80C9: return raster.blendSrcRgb(); // GL_BLEND_SRC_RGB
            case 0x0BE0: // GL_BLEND_DST
            case 0x80C8: return raster.blendDstRgb(); // GL_BLEND_DST_RGB
            case 0x80CB: return raster.blendSrcAlpha();
            case 0x80CA: return raster.blendDstAlpha();
            case 0x8009: return raster.blendEquationRgb();
            case 0x883D: return raster.blendEquationAlpha();
            case 0x0B45: return raster.cullFace();
            case 0x0B46: return raster.frontFace();

            case 0x8068: // GL_TEXTURE_BINDING_1D
                return textureBindingOrZero(0x0DE0);
            case 0x806A: // GL_TEXTURE_BINDING_3D
                return textureBindingOrZero(0x806F);
            case 0x8514: // GL_TEXTURE_BINDING_CUBE_MAP
                return textureBindingOrZero(0x8513);
            case 0x8CA7: // GL_RENDERBUFFER_BINDING
                return GlsmClientArrayState.current().boundRenderbuffer();
            case 0x8919: // GL_SAMPLER_BINDING - sampler objects are not modelled
                return 0;
            case 0x8A28: { // GL_UNIFORM_BUFFER_BINDING - the generic binding record
                int bound = GlsmBufferState.current().bound(0x8A11);
                return bound < 0 ? 0 : bound;
            }
            case 0x8F36: { // GL_COPY_READ_BUFFER_BINDING
                int bound = GlsmBufferState.current().bound(0x8F36);
                return bound < 0 ? 0 : bound;
            }
            case 0x8F37: { // GL_COPY_WRITE_BUFFER_BINDING
                int bound = GlsmBufferState.current().bound(0x8F37);
                return bound < 0 ? 0 : bound;
            }
            case 0x8895: // GL_ELEMENT_ARRAY_BUFFER_BINDING - the binding the indexed decode reads
                return GlsmBufferState.current().bound(0x8893);
            case 0x88ED: // GL_PIXEL_PACK_BUFFER_BINDING
                return this.pixelPackBuffer;
            case 0x88EF: // GL_PIXEL_UNPACK_BUFFER_BINDING
                return PixelUnpackState.current().pixelUnpackBuffer();
            case 0x8894: // GL_ARRAY_BUFFER_BINDING, including a native API resource's opaque identity
                return NativeGeometryBuffer.boundId();
            case 0x8069: { // GL_TEXTURE_BINDING_2D - the name bound to TEXTURE_2D on the active unit
                return textures.texture2d();
            }
            case 0x84E0: // GL_ACTIVE_TEXTURE
                return 0x84C0 + textures.activeUnit();
            case 0x8CA6: // GL_DRAW_FRAMEBUFFER_BINDING / GL_FRAMEBUFFER_BINDING
                return FramebufferBindings.current().drawFramebuffer();
            case 0x8CAA: // GL_READ_FRAMEBUFFER_BINDING
                return FramebufferBindings.current().readFramebuffer();
            case 0x8CA5: // GL_STENCIL_BACK_WRITEMASK - no stencil state is kept; GL's default makes every bit writable
                return 0xFF;
            case 0x0B33: // GL_LIST_INDEX - the display list being compiled, which the model path keys on
                return Lane2StateMirror.current().register().compilingListId();
            case 0x8B8D: // GL_CURRENT_PROGRAM
                return Lane2StateMirror.current().register().activeProgramId();
            case 0x0BC1: { // GL_ALPHA_TEST_FUNC
                // The mirror is the authority while a backend exists; without one the recorded value is, which is
                // what the attribute stack puts back and what a mod's raw glAlphaFunc reported here.
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$alphaTestFuncGl() : this.attribAlphaFunc;
            }
            case 0x0BC2: { // GL_ALPHA_TEST_REF
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                float reference = vulkan != null ? vulkan.celtium$alphaTestReference() : this.attribAlphaReference;
                return reference >= 0.5f ? 1 : 0;
            }
            case 0x0B50: return sharedEnabled(0x0B50) ? 1 : 0; // GL_LIGHTING
            case 0x0B60: return sharedEnabled(0x0B60) ? 1 : 0; // GL_FOG
            case 0x0B71: return sharedEnabled(0x0B71) ? 1 : 0; // GL_DEPTH_TEST
            case 0x0BC0: return sharedEnabled(0x0B86) ? 1 : 0; // GL_ALPHA_TEST
            case 0x0C11: return sharedEnabled(0x0C11) ? 1 : 0; // GL_SCISSOR_TEST
            // The delivered frame is RGBA8 colour with D24 depth and no stencil. Mods probe these to pick a format or
            // a shadow path; the old answer was zero for every one of them, which reads as "no colour bits at all".
            // The colour answers describe the frame, so while a non-default chain pair is the current target
            // they are stale for the target actually being drawn into - QUEUE 405 (S6) counts that mismatch
            // (targetFormatBitsAnsweredStale) rather than changing an answer mods pick formats by.
            case 0x0D52: noteBitsAnswer(0x0D52, 8); return 8; // GL_RED_BITS
            case 0x0D53: noteBitsAnswer(0x0D53, 8); return 8; // GL_GREEN_BITS
            case 0x0D54: noteBitsAnswer(0x0D54, 8); return 8; // GL_BLUE_BITS
            case 0x0D55: noteBitsAnswer(0x0D55, 8); return 8; // GL_ALPHA_BITS
            case 0x0D56: return 24; // GL_DEPTH_BITS
            case 0x0D57: return 0; // GL_STENCIL_BITS - this renderer keeps no stencil
            // Display/format probes: the window is double buffered, not stereo, and the delivered frame has no MSAA.
            case 0x0C32: return 1; // GL_DOUBLEBUFFER
            case 0x0C33: return 0; // GL_STEREO
            case 0x80A8: return 0; // GL_SAMPLE_BUFFERS
            case 0x80A9: return 0; // GL_SAMPLES
            case 0x8D57: return 0; // GL_MAX_SAMPLES
            case 0x0B40: // GL_POLYGON_MODE - the rasterizer only fills, and glPolygonMode is a recorded no-op
                return 0x1B02; // GL_FILL
            case 0x0C40: // GL_RENDER_MODE - no feedback or selection mode is ever entered
                return 0x1C00; // GL_RENDER
            case 0x0D50: // GL_SUBPIXEL_BITS - the 8-bit subpixel precision Vulkan rasterization reports
                return 8;
            case 0x0B54: // GL_SHADE_MODEL - the shaders interpolate the vertex colour, which is GL_SMOOTH
                return 0x1D01;
            case 0x0B52: // GL_LIGHT_MODEL_TWO_SIDE - the shader lights one side of every face
                return 0;
            case 0x0C01: // GL_DRAW_BUFFER - the window's back buffer, or the colour attachment when a framebuffer is bound
                return FramebufferBindings.current().drawFramebuffer() == 0 ? 0x0405 : 0x8CE0;
            case 0x0C02: // GL_READ_BUFFER
                return FramebufferBindings.current().readFramebuffer() == 0 ? 0x0405 : 0x8CE0;
            case 0x0BA0: // GL_MATRIX_MODE - the stack glMatrixMode selected, as GL reports it
                return Lane2StateMirror.current().matrixMode();
            case 0x0B74: { // GL_DEPTH_FUNC
                // Answered from the mirror: raw glDepthFunc and GlStateManager both feed it, and neither necessarily
                // touches this provider's own record. With no backend the record is the answer, the same one
                // glPushAttrib(GL_DEPTH_BUFFER_BIT) saves and glPopAttrib puts back.
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$depthFuncGl() : this.attribDepthFunc;
            }
            case 0x0D05: // GL_PACK_ALIGNMENT
                return 4;
            case 0x0CF5: // GL_UNPACK_ALIGNMENT - the recorded value the upload conversion reads rows with
                return GlsmBufferState.current().unpackAlignment();
            case 0x0CF2: // GL_UNPACK_ROW_LENGTH
                return GlsmBufferState.current().unpackRowLength();
            case 0x0CF3: // GL_UNPACK_SKIP_ROWS - the same pnames the pixelStore setter uses
                return GlsmBufferState.current().unpackSkipRows();
            case 0x0CF4: // GL_UNPACK_SKIP_PIXELS
                return GlsmBufferState.current().unpackSkipPixels();
            case 0x0D02: // GL_PACK_ROW_LENGTH - the readback paths write tightly packed rows
            case 0x0D03: // GL_PACK_SKIP_ROWS
            case 0x0D04: // GL_PACK_SKIP_PIXELS
                return 0;
            case 0x0B65: // GL_FOG_MODE
                return ImmediateFogState.current().glMode();
            case 0x0D3B: // GL_MAX_CLIENT_ATTRIB_STACK_DEPTH - the bounded stack the push/pop routes use
                return 16;
            case 0x85B5: // GL_VERTEX_ARRAY_BINDING - the bound vertex-array name is owned by GlsmClientArrayState
                return GlsmClientArrayState.current().boundVertexArray();
            case 0x8CDF: // GL_MAX_COLOR_ATTACHMENTS - the MRT ledger size: a chain target holds a colour image
                // per slot up to COLOR_ATTACHMENT_SLOTS, and a pass is built with that many attachments
                return COLOR_ATTACHMENT_SLOTS;
            case 0x8824: // GL_MAX_DRAW_BUFFERS - the same ledger
                return COLOR_ATTACHMENT_SLOTS;
            case 0x84E8: { // GL_MAX_RENDERBUFFER_SIZE
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$maxTextureSize() : 16384;
            }
            case 0x0D33: { // GL_MAX_TEXTURE_SIZE
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                return vulkan != null ? vulkan.celtium$maxTextureSize() : 16384;
            }
            case 0x8869: // GL_MAX_VERTEX_ATTRIBS
                return 16;
            case 0x8DFB: // GL_MAX_VERTEX_UNIFORM_VECTORS - a quarter of the component count
                return 1024;
            case 0x8DFD: // GL_MAX_FRAGMENT_UNIFORM_VECTORS
                return 1024;
            case 0x821B: // GL_MAJOR_VERSION
                // Kept consistent with the version string this provider advertises; a capability claim has to be
                // one story (see GlContextTerms and the monolith's measured note on the 4.4 support test).
                return GlContextTerms.current().major();
            case 0x821C: // GL_MINOR_VERSION
                return GlContextTerms.current().minor();
            case 0x9126: { // GL_CONTEXT_PROFILE_MASK
                // Answered only when the latched terms make a profile claim; the default terms predate the
                // profile concept, so the query keeps falling through to the MIN_VALUE answer.
                int mask = GlContextTerms.current().profileMask();
                return mask >= 0 ? mask : Integer.MIN_VALUE;
            }
            case 0x821D: // GL_NUM_EXTENSIONS
                return 1;
            case 0x8F38: // GL_MAX_IMAGE_UNITS - the native image binding ledger size
                return NativeImageBindings.IMAGE_UNITS_PER_PROGRAM;
            case 0x84FF: // GL_MAX_TEXTURE_MAX_ANISOTROPY - the sampler path does not filter anisotropically
                return 1;
            case 0x8872: // GL_MAX_TEXTURE_IMAGE_UNITS - the per-stage count the binding ledger backs
                return TEXTURE_UNITS_PER_STAGE;
            case 0x8B4C: // GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS - the pipeline binds vertex-stage samplers too
                return TEXTURE_UNITS_PER_STAGE;
            case 0x8F39: // GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS - per-stage x the two stages served
                return TEXTURE_UNITS_COMBINED;
            case 0x8B4D: // GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS as LWJGL2 names it (GL20's constant is 35661)
                return TEXTURE_UNITS_COMBINED;
            default:
                return Integer.MIN_VALUE;
        }
    }

    /** The texture bound to {@code target} on the active unit, or 0 when the binding record holds none. */
    private static int textureBindingOrZero(int target) {
        int bound = NativeTextureBindings.current().boundTexture(target);
        return bound < 0 ? 0 : bound;
    }

    private void getInts(int pname, long out) {
        if (out == 0L) {
            return;
        }
        int value;
        switch (pname) {
            case 0x0D3A: { // GL_MAX_VIEWPORT_DIMS - two limits
                int dimension = 16384;
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                if (vulkan != null) {
                    dimension = vulkan.celtium$maxTextureSize();
                }
                MemoryUtil.memPutInt(out, dimension);
                MemoryUtil.memPutInt(out + 4, dimension);
                return;
            }
            case 0x0BA2: // GL_VIEWPORT
                MemoryUtil.memPutInt(out, FramebufferViewportState.current().x());
                MemoryUtil.memPutInt(out + 4, FramebufferViewportState.current().y());
                MemoryUtil.memPutInt(out + 8, FramebufferViewportState.current().width());
                MemoryUtil.memPutInt(out + 12, FramebufferViewportState.current().height());
                return;
            case 0x0C23: { // GL_COLOR_WRITEMASK - four integers, R,G,B,A
                int mask = NativeRasterState.current().colorMask();
                for (int i = 0; i < 4; i++) {
                    MemoryUtil.memPutInt(out + (long) i * 4L, (mask >>> i) & 1);
                }
                return;
            }
            case 0x0C10: // GL_SCISSOR_BOX - independent of the viewport, even when scissor is disabled
                MemoryUtil.memPutInt(out, NativeRasterState.current().scissorX());
                MemoryUtil.memPutInt(out + 4, NativeRasterState.current().scissorY());
                MemoryUtil.memPutInt(out + 8, NativeRasterState.current().scissorWidth());
                MemoryUtil.memPutInt(out + 12, NativeRasterState.current().scissorHeight());
                return;
            case 0x8872: // GL_MAX_TEXTURE_IMAGE_UNITS
                value = TEXTURE_UNITS_PER_STAGE;
                break;
            case 0x8F38: // GL_MAX_IMAGE_UNITS
                value = NativeImageBindings.IMAGE_UNITS_PER_PROGRAM;
                break;
            case 0x0B33: // GL_LIST_INDEX - the list glNewList opened, 0 when none is open
                value = Lane2StateMirror.current().register().compilingListId();
                break;
            case 0x0B30: // GL_LIST_MODE - GL_COMPILE (0x1300) or GL_COMPILE_AND_EXECUTE (0x1301); 0 when none is open
                value = Lane2StateMirror.current().register().compilingListId() == 0 ? 0
                        : DisplayListManager.isCompileAndExecute() ? 0x1301 : 0x1300;
                break;
            default:
                value = getIntegerValue(pname);
                if (value == Integer.MIN_VALUE) {
                    value = 0;
                }
        }
        noteTextureUnitAnswer(pname, value);
        noteImageUnitAnswer(pname, value);
        MemoryUtil.memPutInt(out, value);
    }

    private void getLongs(int pname, long out) {
        if (out != 0L) {
            MemoryUtil.memPutLong(out, 0L);
        }
    }

    private void getFloats(GlFn fn, int pname, long out) {
        routeFloats(pname, out, "glGetDoublev".equals(fn.name));
    }

    /**
     * Answers a float query with the number of components its pname has.
     * <p>
     * One float was written for every pname before this, which left the rest of the caller's buffer uninitialised: a
     * sixteen-float matrix read produced one zero and fifteen words of whatever the allocation held, and every
     * transform built from it was garbage. Matrices read the renderer's live state mirror (identity only before a
     * backend exists); scalar and vector queries write exactly the number of components implied by their pname.
     */
    private void routeFloats(int pname, long out, boolean asDouble) {
        ImmediateFogState fog = ImmediateFogState.current();
        if (out != 0L && pname >= 0x0B62 && pname <= 0x0B66) {
            if (pname == 0x0B66) {
                float[] color = fog.getFogColor();
                for (int i = 0; i < 4; i++) putFloatOrDouble(out, i, i == 3 ? fog.colorAlpha() : color[i], asDouble);
            } else {
                float value = pname == 0x0B62 ? fog.getFogDensity() : pname == 0x0B63 ? fog.getFogStart()
                        : pname == 0x0B64 ? fog.getFogEnd() : fog.glMode();
                putFloatOrDouble(out, 0, value, asDouble);
            }
            return;
        }

        if (out == 0L) {
            return;
        }
        NativeRasterState raster = NativeRasterState.current();
        switch (pname) {
            case 0x8038: { // GL_POLYGON_OFFSET_FACTOR
                putFloatOrDouble(out, 0, raster.polygonOffsetFactor(), asDouble);
                return;
            }
            case 0x2A00: { // GL_POLYGON_OFFSET_UNITS
                putFloatOrDouble(out, 0, raster.polygonOffsetUnits(), asDouble);
                return;
            }
            case 0x0BA2: { // GL_VIEWPORT
                putFloatOrDouble(out, 0, FramebufferViewportState.current().x(), asDouble);
                putFloatOrDouble(out, 1, FramebufferViewportState.current().y(), asDouble);
                putFloatOrDouble(out, 2, FramebufferViewportState.current().width(), asDouble);
                putFloatOrDouble(out, 3, FramebufferViewportState.current().height(), asDouble);
                return;
            }
            case 0x0C10: { // GL_SCISSOR_BOX
                putFloatOrDouble(out, 0, raster.scissorX(), asDouble);
                putFloatOrDouble(out, 1, raster.scissorY(), asDouble);
                putFloatOrDouble(out, 2, raster.scissorWidth(), asDouble);
                putFloatOrDouble(out, 3, raster.scissorHeight(), asDouble);
                return;
            }
            case 0x0C23: { // GL_COLOR_WRITEMASK, as glGetFloatv reports the four channels
                int mask = raster.colorMask();
                for (int i = 0; i < 4; i++) {
                    putFloatOrDouble(out, i, ((mask >>> i) & 1), asDouble);
                }
                return;
            }
            case 0x0B72: { // GL_DEPTH_WRITEMASK, the same boolean state through the float query
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                boolean mask = vulkan != null ? vulkan.celtium$depthWritesLive() : this.attribDepthWrites;
                putFloatOrDouble(out, 0, mask ? 1.0f : 0.0f, asDouble);
                return;
            }
            case 0x0C22: { // GL_COLOR_CLEAR_VALUE
                float[] clearScratch = new float[4];
                FramebufferClearState.current().mainClearValues(clearScratch);
                for (int i = 0; i < 4; i++) {
                    putFloatOrDouble(out, i, clearScratch[i], asDouble);
                }
                return;
            }
            case 0x0B73: { // GL_DEPTH_CLEAR_VALUE
                putFloatOrDouble(out, 0, (float) FramebufferClearState.current().depth(), asDouble);
                return;
            }
            case 0x0B91: { // GL_STENCIL_CLEAR_VALUE - no stencil state is recorded, GL's default is 0
                putFloatOrDouble(out, 0, 0.0f, asDouble);
                return;
            }
            case 0x0BC1: { // GL_ALPHA_TEST_FUNC, as a float as glGetFloatv reports enums
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                float func = vulkan != null ? vulkan.celtium$alphaTestFuncGl() : (float) 0x0207;
                putFloatOrDouble(out, 0, func, asDouble);
                return;
            }
            case 0x0BC2: { // GL_ALPHA_TEST_REF
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                float reference = vulkan != null ? vulkan.celtium$alphaTestReference() : 0.0f;
                putFloatOrDouble(out, 0, reference, asDouble);
                return;
            }
            case 0x0BA6: // GL_MODELVIEW_MATRIX
            case 0x0BA7: // GL_PROJECTION_MATRIX
            case 0x0BA8: // GL_TEXTURE_MATRIX
                // The renderer's own mirror when it has one. Vanilla reads the terrain's projection and model-view
                // through exactly this call (ActiveRenderInfo.updateRenderInfo -> GlStateManager.getFloat), and it
                // builds the frustum the chunk traversal culls with from the same two reads (ClippingHelperImpl). An
                // identity here therefore does two things at once: every section but the camera's own is culled, and
                // the surviving geometry is drawn with clip coordinates equal to camera-relative world coordinates.
                float[] mirrored = mirroredMatrix(pname);
                for (int i = 0; i < 16; i++) {
                    putFloatOrDouble(out, i, mirrored != null ? mirrored[i] : (i % 5 == 0 ? 1.0f : 0.0f), asDouble);
                }
                return;
            case 0x0B00: // GL_CURRENT_COLOR
                putFloatOrDouble(out, 0, GlsmImmediateState.current().red(), asDouble);
                putFloatOrDouble(out, 1, GlsmImmediateState.current().green(), asDouble);
                putFloatOrDouble(out, 2, GlsmImmediateState.current().blue(), asDouble);
                putFloatOrDouble(out, 3, GlsmImmediateState.current().alpha(), asDouble);
                return;
            case 0x0B70: // GL_DEPTH_RANGE
                putFloatOrDouble(out, 0, 0.0f, asDouble);
                putFloatOrDouble(out, 1, 1.0f, asDouble);
                return;
            case 0x0B21: // GL_LINE_WIDTH
                putFloatOrDouble(out, 0, GlsmImmediateState.current().lineWidth(), asDouble);
                return;
            case 0x0B11: // GL_POINT_SIZE
                putFloatOrDouble(out, 0, GlsmImmediateState.current().pointSize(), asDouble);
                return;
            case 0x8005: // GL_BLEND_COLOR - the constant blend colour is never recorded; GL's initial value is black
                putFloatOrDouble(out, 0, 0.0f, asDouble);
                putFloatOrDouble(out, 1, 0.0f, asDouble);
                putFloatOrDouble(out, 2, 0.0f, asDouble);
                putFloatOrDouble(out, 3, 0.0f, asDouble);
                return;
            case 0x84FF: // GL_MAX_TEXTURE_MAX_ANISOTROPY - the float spelling of the int answer
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                return;
            case 0x0B53: { // GL_LIGHT_MODEL_AMBIENT - the ambient the shader lighting uses
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                float[] ambient = vulkan == null ? GlsmImmediateState.current().lightAmbientDefault() : vulkan.celtium$lightAmbient();
                for (int i = 0; i < 4; i++) {
                    putFloatOrDouble(out, i, ambient != null && i < ambient.length ? ambient[i] : 0.0f, asDouble);
                }
                return;
            }
            case 0x0B12: // GL_POINT_SIZE_RANGE - points are not rasterized, so the one supported size is one
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, 1.0f, asDouble);
                return;
            case 0x0B13: // GL_POINT_SIZE_GRANULARITY
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                return;
            case 0x0B23: // GL_LINE_WIDTH_GRANULARITY
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                return;
            case 0x0B22: // GL_LINE_WIDTH_RANGE - two values, not the point size
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, lineWidthRangeMax(), asDouble);
                return;
            case 0x0D3A: { // GL_MAX_VIEWPORT_DIMS
                int dimension = 16384;
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                if (vulkan != null) {
                    dimension = vulkan.celtium$maxTextureSize();
                }
                putFloatOrDouble(out, 0, dimension, asDouble);
                putFloatOrDouble(out, 1, dimension, asDouble);
                return;
            }
            case 0x846D: // GL_ALIASED_POINT_SIZE_RANGE - points are not rasterized; one supported size
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, 1.0f, asDouble);
                return;
            case 0x8129: // GL_POINT_DISTANCE_ATTENUATION - GL's initial value, in all three components
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, 0.0f, asDouble);
                putFloatOrDouble(out, 2, 0.0f, asDouble);
                return;
            case 0x846E: // GL_ALIASED_LINE_WIDTH_RANGE - the device's range, not the width currently set
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, lineWidthRangeMax(), asDouble);
                return;
            default:
                putFloatOrDouble(out, 0, 0.0f, asDouble);
        }
    }

    /**
     * The matrix the renderer's mirror holds for a GL matrix pname, or null when no mirror is published.
     * <p>
     * The provider is the only thing that can answer these reads, and answering the identity is not a neutral choice -
     * it is a culling frustum and a camera transform. The backend publishes what its own hooks observed, so the two
     * agree on what the frame's matrices are.
     */
    private static float[] mirroredMatrix(int pname) {
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        if (vulkan == null) {
            return null;
        }
        // Peeked, not copied: the caller writes the sixteen values out at once, and vanilla asks per entity.
        switch (pname) {
            case 0x0BA6:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.lane2.Lane2ImmediateState.MATRIX_MODE_MODELVIEW);
            case 0x0BA7:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.lane2.Lane2ImmediateState.MATRIX_MODE_PROJECTION);
            case 0x0BA8:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.lane2.Lane2ImmediateState.MATRIX_MODE_TEXTURE);
            default:
                return null;
        }
    }

    private static void putFloatOrDouble(long out, int index, float value, boolean asDouble) {
        if (asDouble) {
            MemoryUtil.memPutDouble(out + (long) index * 8L, value);
        } else {
            MemoryUtil.memPutFloat(out + (long) index * 4L, value);
        }
    }

    /** {@return the max of the line-width range the queries answer}: the device's limit, or 1.0 before a backend exists. */
    private static float lineWidthRangeMax() {
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$lineWidthRangeMax() : 1.0f;
    }

    /**
     * Hands one framebuffer bit-count answer to the format census (QUEUE 405, S6): the answer is this
     * provider's flat value for the delivered frame, and the backend counts it when the current target's
     * pair class carries different bits. Without a Vulkan backend there is no pair class to disagree
     * with, so nothing is counted.
     */
    private static void noteBitsAnswer(int pname, int answeredBits) {
        Lane2ImmediateRecorder vulkan = vulkanBackend();
        if (vulkan != null) {
            vulkan.celtium$noteTargetFormatBitsAnswer(pname, answeredBits);
        }
    }

    private void routeBooleans(int pname, long out) {
        if (out == 0L) {
            return;
        }
        // The query used to write a single false byte and nothing else, whatever was asked. Callers that save and restore
        // GL state read the whole vector: glColorMask(true,true,true,true) is recorded here, and the readback answered
        // [0,85,85,85] because only the first byte was written and it was false (#388).
        switch (pname) {
            case 0x0C23: { // GL_COLOR_WRITEMASK
                int mask = NativeRasterState.current().colorMask();
                for (int i = 0; i < 4; i++) {
                    MemoryUtil.memPutByte(out + i, (byte) (((mask >>> i) & 1) != 0 ? 1 : 0));
                }
                return;
            }
            case 0x0B72: { // GL_DEPTH_WRITEMASK
                // GL's default is enabled. The mirror is the authority: glDepthMask goes through the hint to the backend
                // and never reaches this provider's enable record.
                Lane2ImmediateRecorder vulkan = vulkanBackend();
                boolean mask = vulkan != null ? vulkan.celtium$depthWritesLive() : sharedEnabled(GL_DEPTH_WRITEMASK_CAP);
                MemoryUtil.memPutByte(out, (byte) (mask ? 1 : 0));
                return;
            }
            case 0x0BD0: { // GL_DITHER
                // GL's default is enabled and dithering has no effect in this renderer, so the answer stays true.
                MemoryUtil.memPutByte(out, (byte) 1);
                return;
            }
            case 0x0BE2: // GL_BLEND
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0BE2) ? 1 : 0));
                return;
            case 0x0B90: // GL_STENCIL_TEST
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0B90) ? 1 : 0));
                return;
            case 0x0B71: // GL_DEPTH_TEST
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0B71) ? 1 : 0));
                return;
            case 0x0C11: // GL_SCISSOR_TEST
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0C11) ? 1 : 0));
                return;
            case 0x8037: // GL_POLYGON_OFFSET_FILL
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x8037) ? 1 : 0));
                return;
            case 0x0B44: // GL_CULL_FACE
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0B44) ? 1 : 0));
                return;
            case 0x0B50: // GL_LIGHTING
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0B50) ? 1 : 0));
                return;
            case 0x0B60: // GL_FOG
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0B60) ? 1 : 0));
                return;
            case 0x0BC0: // GL_ALPHA_TEST
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0B86) ? 1 : 0));
                return;
            case 0x0DE1: // GL_TEXTURE_2D
                MemoryUtil.memPutByte(out, (byte) (sharedEnabled(0x0DE1) ? 1 : 0));
                return;
            default:
                // A query this provider has no record for: the spec says the value is undefined, and writing false over
                // the caller's whole destination would destroy whatever it had laid out next. One byte stays, as before.
                MemoryUtil.memPutByte(out, (byte) 0);
        }
    }

    /** The tex-image producer the dispatch lands on: the capture store files the upload (family 3, seat B). */
    private void texImagePixels(GlFn fn, long[] c) {
        VulkanTextureRegistry.captureStore().texImagePixels(CaptureUploadContext.current(), fn.name, c);
    }

    private void callLists(long[] c) {
        GLSMDraws.glCallLists((int) c[0], (int) c[1], c[2]);
    }

    /**
     * C2(b) (DECODERS ruling, ruling item 25 section 3.2): with no VBO bound, a raw client
     * pointer resolves against the owned range the game published for this draw
     * ({@link ClientArrayRange}) into a direct buffer over exactly the readable remainder
     * (native byte order, position 0), so the {@code GLSMVertexInput} ByteBuffer overload
     * stores it as the VAO's client pointer. Unregistered or out-of-range addresses resolve
     * to null and keep the VBO-offset path unchanged: the buffer is only ever built over the
     * range the game's own publication vouched for, never over unverified memory.
     */
    private static ByteBuffer resolveClientArrayPointer(long pointer) {
        if (pointer == 0L || GLStateManager.getBoundVBO() != 0) {
            return null;
        }
        final long[] range = ClientArrayRange.peekPublished();
        if (range == null || !ClientArrayRange.covers(range[0], range[1], pointer, 1)) {
            return null;
        }
        final long remaining = range[0] + range[1] - pointer;
        if (remaining <= 0L || remaining > Integer.MAX_VALUE) {
            return null;
        }
        return MemoryUtil.memByteBuffer(pointer, (int) remaining);
    }

    private void vertexPointer(GlFn fn, long[] c) {
        switch (fn.name) {
            case "glVertexPointer": {
                recordVertexPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                final ByteBuffer client = resolveClientArrayPointer(c[3]);
                if (client != null) {
                    GLSMVertexInput.glVertexPointer((int) c[0], (int) c[1], (int) c[2], client);
                } else {
                    GLSMVertexInput.glVertexPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                }
                break;
            }
            case "glNormalPointer": {
                GlsmClientArrayState.current().setClientPointer(0x8075, 3, (int) c[0], (int) c[1], c[2],
                        NativeGeometryBuffer.boundId());
                final ByteBuffer client = resolveClientArrayPointer(c[2]);
                if (client != null) {
                    GLSMVertexInput.glNormalPointer((int) c[0], (int) c[1], client);
                } else {
                    GLSMVertexInput.glNormalPointer((int) c[0], (int) c[1], c[2]);
                }
                break;
            }
            case "glColorPointer": {
                GlsmClientArrayState.current().setClientPointer(0x8076, (int) c[0], (int) c[1], (int) c[2], c[3],
                        NativeGeometryBuffer.boundId());
                final ByteBuffer client = resolveClientArrayPointer(c[3]);
                if (client != null) {
                    GLSMVertexInput.glColorPointer((int) c[0], (int) c[1], (int) c[2], client);
                } else {
                    GLSMVertexInput.glColorPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                }
                break;
            }
            case "glTexCoordPointer": {
                GlsmClientArrayState.current().setClientPointer(0x8078, (int) c[0], (int) c[1], (int) c[2], c[3],
                        NativeGeometryBuffer.boundId());
                final ByteBuffer client = resolveClientArrayPointer(c[3]);
                if (client != null) {
                    GLSMVertexInput.glTexCoordPointer((int) c[0], (int) c[1], (int) c[2], client);
                } else {
                    GLSMVertexInput.glTexCoordPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * The legacy vertex pointer's two records of one fact (the Hint door keeps both): the
     * client-pointer record the draw decode reads, and attribute 0's per-VAO record the
     * attrib queries read. Neither may drift from the other.
     */
    private static void recordVertexPointer(int size, int type, int stride, long pointer) {
        GlsmClientArrayState.Pointer vertex = GlsmClientArrayState.current().clientPointer(0x8074, true);
        vertex.size = size;
        vertex.type = type;
        vertex.stride = stride;
        vertex.offset = pointer;
        vertex.normalized = false;
        vertex.integer = false;
        GlsmClientArrayState.VaoAttrib attrib = GlsmClientArrayState.current().boundAttribs()[0];
        attrib.size = size;
        attrib.type = type;
        attrib.stride = stride;
        attrib.offset = pointer;
        attrib.normalized = false;
        attrib.integer = false;
        attrib.pointerSet = true;
        attrib.buffer = NativeGeometryBuffer.boundId();
    }

    /** Attribute 0's per-VAO record is the live legacy pointer after a VAO swap: mirror it back. */
    private static void syncVertexPointerRecord() {
        GlsmClientArrayState.VaoAttrib attrib = GlsmClientArrayState.current().boundAttribs()[0];
        GlsmClientArrayState.Pointer vertex = GlsmClientArrayState.current().clientPointer(0x8074, true);
        vertex.size = attrib.size;
        vertex.type = attrib.type;
        vertex.stride = attrib.stride;
        vertex.offset = attrib.offset;
        vertex.normalized = attrib.normalized;
        vertex.integer = attrib.integer;
    }

    /** The generic attribute pointer's per-VAO record (the Hint door writes the same fields). */
    private static void recordVertexAttribPointer(int index, int size, int type, boolean normalized,
            int stride, long pointer) {
        if (index < 0 || index >= GlsmClientArrayState.MAX_VERTEX_ATTRIBS) {
            return;
        }
        GlsmClientArrayState.VaoAttrib attrib = GlsmClientArrayState.current().boundAttribs()[index];
        attrib.size = size;
        attrib.type = type;
        attrib.stride = stride;
        attrib.offset = pointer;
        attrib.normalized = normalized;
        attrib.integer = false;
        attrib.pointerSet = true;
        attrib.buffer = NativeGeometryBuffer.boundId();
        if (index == 0) {
            syncVertexPointerRecord();
        }
    }

    /** The generic attribute enable's per-VAO record; index 0 also feeds the capability record. */
    private static void recordVertexAttribEnabled(int index, boolean enabled) {
        if (index >= 0 && index < GlsmClientArrayState.MAX_VERTEX_ATTRIBS) {
            GlsmClientArrayState.current().boundAttribs()[index].enabled = enabled;
        }
        if (index == 0) {
            setCapability(0x8622, enabled);
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
                recordVertexAttribEnabled((int) c[0], true);
                GLSMVertexInput.glEnableVertexAttribArray((int) c[0]);
                return 0L;
            case "glDisableVertexAttribArray": case "glDisableVertexAttribArrayARB":
                recordVertexAttribEnabled((int) c[0], false);
                GLSMVertexInput.glDisableVertexAttribArray((int) c[0]);
                return 0L;
            case "glVertexAttribPointer": case "glVertexAttribPointerARB": {
                recordVertexAttribPointer((int) c[0], (int) c[1], (int) c[2], c[3] != 0L, (int) c[4], c[5]);
                final ByteBuffer client = resolveClientArrayPointer(c[5]);
                if (client != null) {
                    GLSMVertexInput.glVertexAttribPointer((int) c[0], (int) c[1], (int) c[2], c[3] != 0L,
                            (int) c[4], client);
                } else {
                    GLSMVertexInput.glVertexAttribPointer((int) c[0], (int) c[1], (int) c[2], c[3] != 0L,
                            (int) c[4], c[5]);
                }
                return 0L;
            }
            case "glVertexAttribDivisor":
                GLSMVertexInput.glVertexAttribDivisor((int) c[0], (int) c[1]);
                return 0L;
            case "glTexCoordPointer": {
                GlsmClientArrayState.current().setClientPointer(0x8078, (int) c[0], (int) c[1], (int) c[2], c[3],
                        NativeGeometryBuffer.boundId());
                final ByteBuffer client = resolveClientArrayPointer(c[3]);
                if (client != null) {
                    GLSMVertexInput.glTexCoordPointer((int) c[0], (int) c[1], (int) c[2], client);
                } else {
                    GLSMVertexInput.glTexCoordPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                }
                return 0L;
            }
            case "glColorPointer": {
                GlsmClientArrayState.current().setClientPointer(0x8076, (int) c[0], (int) c[1], (int) c[2], c[3],
                        NativeGeometryBuffer.boundId());
                final ByteBuffer client = resolveClientArrayPointer(c[3]);
                if (client != null) {
                    GLSMVertexInput.glColorPointer((int) c[0], (int) c[1], (int) c[2], client);
                } else {
                    GLSMVertexInput.glColorPointer((int) c[0], (int) c[1], (int) c[2], c[3]);
                }
                return 0L;
            }
            case "glNormalPointer": {
                GlsmClientArrayState.current().setClientPointer(0x8075, 3, (int) c[0], (int) c[1], c[2],
                        NativeGeometryBuffer.boundId());
                final ByteBuffer client = resolveClientArrayPointer(c[2]);
                if (client != null) {
                    GLSMVertexInput.glNormalPointer((int) c[0], (int) c[1], client);
                } else {
                    GLSMVertexInput.glNormalPointer((int) c[0], (int) c[1], c[2]);
                }
                return 0L;
            }
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
                serveGetTexImage((int) c[0], (int) c[1], (int) c[2], (int) c[3], c[4]);
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
